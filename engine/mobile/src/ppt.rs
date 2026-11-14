//! PowerPoint 97-2003 presentations (.ppt), which DeckCraft doesn't read. Artefact reads the binary
//! file itself ([MS-PPT] records in the `PowerPoint Document` stream, [MS-ODRAW] drawings inside
//! them) and sets what it finds in a DeckCraft presentation of the file's slide size, so a .ppt
//! opens, draws, searches and exports like any deck:
//!
//! * each slide's drawing: its text boxes and placeholders where the slide puts them, with the
//!   size, weight, slant, colour and alignment their first run states, and its pictures (JPEG, PNG
//!   and bitmaps from the `Pictures` stream);
//! * the outline ([MS-PPT] `SlideListWithText`), which holds the text of title and body
//!   placeholders, and is all there is for a slide whose drawing can't be read.
//!
//! Not carried over: autoshapes, charts, tables, backgrounds and master designs, metafile
//! pictures, animations. A slide therefore reads as its words and pictures on a plain ground.

use std::collections::HashMap;
use std::io::Read;
use std::sync::Arc;

use deckcraft_model::style::PictureFill;
use deckcraft_model::text::{Align, Anchor, TextBody};
use deckcraft_model::{ColorRef, LayoutType, Paragraph, PhType, Presentation, Rgba, Run, RunProps, Shape, ShapeId, ShapeKind, Xfrm};

/// The most records read and the deepest nesting followed (a hostile file can claim anything).
const MAX_RECORDS: usize = 500_000;
const MAX_DEPTH: usize = 16;
/// The most slides made, shapes kept on one slide, and picture bytes carried in all.
const MAX_SLIDES: usize = 2_000;
const MAX_SHAPES: usize = 2_000;
const MAX_PICTURE_BYTES: usize = 256 << 20;

const DOCUMENT_ATOM: u16 = 0x03E9;
const SLIDE: u16 = 0x03EE;
const SLIDE_LIST_WITH_TEXT: u16 = 0x0FF0;
const SLIDE_PERSIST_ATOM: u16 = 0x03F3;
const PERSIST_DIRECTORY_ATOM: u16 = 0x1772;
const DRAWING: u16 = 0x040C;
const DRAWING_GROUP: u16 = 0x040B;
const TEXT_HEADER_ATOM: u16 = 0x0F9F;
const TEXT_CHARS_ATOM: u16 = 0x0FA0;
const TEXT_BYTES_ATOM: u16 = 0x0FA8;
const STYLE_TEXT_PROP_ATOM: u16 = 0x0FA1;
const OUTLINE_TEXT_REF_ATOM: u16 = 0x0F9E;
const PLACEHOLDER_ATOM: u16 = 0x0BC3;
// [MS-ODRAW]
const ART_DGG: u16 = 0xF000;
const ART_BSTORE: u16 = 0xF001;
const ART_DG: u16 = 0xF002;
const ART_SPGR: u16 = 0xF003;
const ART_SP: u16 = 0xF004;
const ART_BSE: u16 = 0xF007;
const ART_FSPGR: u16 = 0xF009;
const ART_FSP: u16 = 0xF00A;
const ART_FOPT: u16 = 0xF00B;
const ART_TEXTBOX: u16 = 0xF00D;
const ART_CHILD_ANCHOR: u16 = 0xF00F;
const ART_CLIENT_ANCHOR: u16 = 0xF010;
const ART_CLIENT_DATA: u16 = 0xF011;

/// Text kinds ([MS-PPT] `TextTypeEnum`).
const TEXT_TITLE: u32 = 0;
const TEXT_BODY: u32 = 1;
const TEXT_NOTES: u32 = 2;
const TEXT_CENTER_BODY: u32 = 5;
const TEXT_CENTER_TITLE: u32 = 6;

/// One slide's text in the outline: its title, the rest (body, subtitle, other text) in order, and
/// every text as stored (the drawing's placeholders refer to them by position).
#[derive(Default, Debug, PartialEq)]
struct SlideText {
    title: String,
    body: Vec<String>,
    /// Where the slide's own record is, through the persist directory.
    persist: u32,
    texts: Vec<(u32, String)>,
}

#[derive(Default)]
struct Outline {
    /// The slide size in master units (576 to the inch).
    size: Option<(i32, i32)>,
    slides: Vec<SlideText>,
}

/// One record's header: its type, whether it holds other records, and its body.
pub(crate) struct Record<'a> {
    pub(crate) kind: u16,
    pub(crate) instance: u16,
    pub(crate) container: bool,
    pub(crate) body: &'a [u8],
}

pub(crate) fn records(data: &[u8]) -> impl Iterator<Item = Record<'_>> {
    let mut at = 0usize;
    std::iter::from_fn(move || {
        let (r, next) = record_at(data, at)?;
        at = next;
        Some(r)
    })
}

/// The record starting at `at`, and where the next one starts.
pub(crate) fn record_at(data: &[u8], at: usize) -> Option<(Record<'_>, usize)> {
    let head = data.get(at..at.checked_add(8)?)?;
    let ver_inst = u16::from_le_bytes([head[0], head[1]]);
    let kind = u16::from_le_bytes([head[2], head[3]]);
    let len = u32::from_le_bytes([head[4], head[5], head[6], head[7]]) as usize;
    let start = at + 8;
    let end = start.checked_add(len)?.min(data.len());
    Some((Record { kind, instance: ver_inst >> 4, container: ver_inst & 0xF == 0xF, body: data.get(start..end)? }, start.checked_add(len)?))
}

pub(crate) fn u16_at(b: &[u8], at: usize) -> Option<u16> {
    b.get(at..at.checked_add(2)?).map(|v| u16::from_le_bytes([v[0], v[1]]))
}

pub(crate) fn u32_at(b: &[u8], at: usize) -> Option<u32> {
    b.get(at..at.checked_add(4)?).map(|v| u32::from_le_bytes([v[0], v[1], v[2], v[3]]))
}

fn i32_at(b: &[u8], at: usize) -> Option<i32> {
    u32_at(b, at).map(|v| v as i32)
}

/// The text of a text atom: UTF-16 units, or bytes (Windows-1252 for the common range; the rest as
/// Latin-1). A vertical tab is a line break inside a paragraph; paragraphs end in CR.
fn atom_text(r: &Record<'_>) -> String {
    let text = if r.kind == TEXT_CHARS_ATOM {
        let units: Vec<u16> = r.body.as_chunks::<2>().0.iter().map(|c| u16::from_le_bytes(*c)).collect();
        String::from_utf16_lossy(&units)
    } else {
        r.body.iter().map(|&b| char::from(b)).collect()
    };
    text.replace('\u{b}', "\n")
}

/// The text of a slide list: a new slide at each persist atom, each text after the header saying
/// what kind of text it is.
fn read_slide_list(body: &[u8], out: &mut Outline, budget: &mut usize) {
    let mut kind = TEXT_BODY;
    for r in records(body) {
        if *budget == 0 || out.slides.len() > MAX_SLIDES {
            return;
        }
        *budget -= 1;
        match r.kind {
            SLIDE_PERSIST_ATOM => out.slides.push(SlideText { persist: u32_at(r.body, 0).unwrap_or(0), ..SlideText::default() }),
            TEXT_HEADER_ATOM => kind = u32_at(r.body, 0).unwrap_or(TEXT_BODY),
            TEXT_CHARS_ATOM | TEXT_BYTES_ATOM => {
                let text = atom_text(&r);
                let Some(slide) = out.slides.last_mut() else { continue };
                slide.texts.push((kind, text.clone()));
                match kind {
                    TEXT_TITLE | TEXT_CENTER_TITLE => {
                        if !slide.title.is_empty() {
                            slide.title.push(' ');
                        }
                        slide.title.push_str(text.trim_end_matches('\r'));
                    }
                    // notes are not slide text
                    TEXT_NOTES => {}
                    _ => slide.body.extend(text.split('\r').map(str::to_string)),
                }
            }
            _ => {}
        }
    }
}

fn walk(data: &[u8], depth: usize, out: &mut Outline, budget: &mut usize) {
    for r in records(data) {
        if *budget == 0 {
            return;
        }
        *budget -= 1;
        match r.kind {
            DOCUMENT_ATOM if out.size.is_none() => {
                if let (Some(w), Some(h)) = (i32_at(r.body, 0), i32_at(r.body, 4)) {
                    out.size = Some((w, h));
                }
            }
            // instance 0: the slides (1 is the masters, 2 the notes)
            SLIDE_LIST_WITH_TEXT if r.instance == 0 => read_slide_list(r.body, out, budget),
            // a slide's own record holds its drawing, not the outline
            SLIDE => {}
            _ if r.container && depth < MAX_DEPTH => walk(r.body, depth + 1, out, budget),
            _ => {}
        }
    }
}

/// The streams of the .ppt file `bytes`: the record stream, and the pictures (empty when none).
fn streams(bytes: &[u8]) -> Result<(Vec<u8>, Vec<u8>), String> {
    let mut cfb = cfb::CompoundFile::open(std::io::Cursor::new(bytes)).map_err(|e| format!("not a PowerPoint 97-2003 file: {e}"))?;
    let mut data = Vec::new();
    {
        let mut stream = cfb.open_stream("PowerPoint Document").map_err(|_| "the file has no PowerPoint Document stream".to_string())?;
        // the stream is read in full but never past 512 MB
        stream.by_ref().take(512 << 20).read_to_end(&mut data).map_err(|e| e.to_string())?;
    }
    let mut pictures = Vec::new();
    if let Ok(mut stream) = cfb.open_stream("Pictures") {
        // unreadable pictures leave the slides their text
        if stream.by_ref().take(MAX_PICTURE_BYTES as u64).read_to_end(&mut pictures).is_err() {
            pictures.clear();
        }
    }
    Ok((data, pictures))
}

/// The outline of the record stream `data`.
fn outline_of(data: &[u8]) -> Outline {
    let mut out = Outline::default();
    let mut budget = MAX_RECORDS;
    walk(data, 0, &mut out, &mut budget);
    out
}

#[cfg(test)]
fn outline(bytes: &[u8]) -> Result<Outline, String> {
    Ok(outline_of(&streams(bytes)?.0))
}

// ---------------------------------------------------------------- a slide's drawing

/// A rectangle as left, top, right, bottom.
type Edges = [f64; 4];

/// A group's coordinates to the slide's (master units): `x' = ox + x * sx`.
#[derive(Clone, Copy)]
struct Map {
    ox: f64,
    oy: f64,
    sx: f64,
    sy: f64,
}

impl Map {
    const SLIDE: Map = Map { ox: 0.0, oy: 0.0, sx: 1.0, sy: 1.0 };

    fn rect(&self, r: Edges) -> Edges {
        [self.ox + r[0] * self.sx, self.oy + r[1] * self.sy, self.ox + r[2] * self.sx, self.oy + r[3] * self.sy]
    }
}

/// What a text's first paragraph and first run state about its looks (the rest is the master's).
#[derive(Default, Debug, Clone, PartialEq)]
struct TextLook {
    size: Option<f64>,
    bold: Option<bool>,
    italic: Option<bool>,
    color: Option<[u8; 3]>,
    /// 0 left, 1 centre, 2 right, 3 justified.
    align: Option<u16>,
}

#[derive(Debug, Clone, PartialEq)]
struct ShapeText {
    kind: u32,
    text: String,
    look: TextLook,
}

/// A shape as its record states it.
#[derive(Default)]
struct RawShape {
    flags: u32,
    /// Its place on the slide (client anchor), in a group (child anchor), and, for a group's own
    /// shape, the coordinates its children are stated in.
    client: Option<Edges>,
    child: Option<Edges>,
    group: Option<Edges>,
    /// The picture it shows: an index into the pictures, from 1.
    picture: Option<u32>,
    rotation: f64,
    text: Option<ShapeText>,
}

/// A shape worth drawing: a text or a picture, in points on the slide.
#[derive(Debug, PartialEq)]
struct Drawn {
    /// x, y, width, height.
    rect: [f64; 4],
    rotation: f64,
    text: Option<ShapeText>,
    picture: Option<u32>,
}

/// Paragraph and character formatting of a text ([MS-PPT] `StyleTextPropAtom`): the first
/// paragraph's alignment and the first run's size, weight, slant and colour. `units` is the text's
/// length in UTF-16 units.
fn text_look(body: &[u8], units: usize) -> TextLook {
    let mut look = TextLook::default();
    let mut at = 0usize;
    // paragraph runs cover the text and its end mark
    let mut covered = 0usize;
    let mut first = true;
    while covered < units + 1 {
        let (Some(count), Some(mask)) = (u32_at(body, at), u32_at(body, at + 6)) else { return look };
        at += 10;
        covered += count.max(1) as usize;
        // a field of `len` bytes is there when its bit of the mask is set
        let field = |at: &mut usize, bit: u32, len: usize| -> Option<usize> {
            if mask & bit == 0 {
                return None;
            }
            let here = *at;
            *at += len;
            Some(here)
        };
        field(&mut at, 0x000F, 2);
        field(&mut at, 0x0080, 2);
        field(&mut at, 0x0010, 2);
        field(&mut at, 0x0040, 2);
        field(&mut at, 0x0020, 4);
        let align = field(&mut at, 0x0800, 2);
        field(&mut at, 0x1000, 2);
        field(&mut at, 0x2000, 2);
        field(&mut at, 0x4000, 2);
        field(&mut at, 0x0100, 2);
        field(&mut at, 0x0400, 2);
        field(&mut at, 0x8000, 2);
        if mask & 0x0010_0000 != 0 {
            let Some(tabs) = u16_at(body, at) else { return look };
            at += 2 + usize::from(tabs) * 4;
        }
        field(&mut at, 0x0001_0000, 2);
        field(&mut at, 0x000E_0000, 2);
        field(&mut at, 0x0020_0000, 2);
        if first {
            look.align = align.and_then(|a| u16_at(body, a));
            first = false;
        }
        if at > body.len() {
            return look;
        }
    }
    // the first character run
    let (Some(_count), Some(mask)) = (u32_at(body, at), u32_at(body, at + 4)) else { return look };
    at += 8;
    if mask & 0xFFFF != 0 {
        if let Some(style) = u16_at(body, at) {
            if mask & 0x1 != 0 {
                look.bold = Some(style & 0x1 != 0);
            }
            if mask & 0x2 != 0 {
                look.italic = Some(style & 0x2 != 0);
            }
        }
        at += 2;
    }
    for bit in [0x0001_0000u32, 0x0020_0000, 0x0040_0000, 0x0080_0000] {
        if mask & bit != 0 {
            at += 2;
        }
    }
    if mask & 0x0002_0000 != 0 {
        look.size = u16_at(body, at).map(f64::from).filter(|s| (1.0..=4000.0).contains(s));
        at += 2;
    }
    if mask & 0x0004_0000 != 0 {
        // red, green, blue, then what kind of colour: 0xFE is a colour of its own, the rest a scheme slot
        if let Some(c) = body.get(at..at + 4).filter(|c| c[3] == 0xFE) {
            look.color = Some([c[0], c[1], c[2]]);
        }
    }
    look
}

/// The text a shape holds: its own, or the outline's (a placeholder's text lives there).
fn read_textbox(body: &[u8], outline: &[(u32, String)]) -> Option<ShapeText> {
    let mut kind = 4u32;
    let mut found: Option<ShapeText> = None;
    for r in records(body) {
        match r.kind {
            TEXT_HEADER_ATOM => kind = u32_at(r.body, 0).unwrap_or(4),
            TEXT_CHARS_ATOM | TEXT_BYTES_ATOM => found = Some(ShapeText { kind, text: atom_text(&r), look: TextLook::default() }),
            STYLE_TEXT_PROP_ATOM => {
                if let Some(t) = &mut found {
                    t.look = text_look(r.body, t.text.encode_utf16().count());
                }
            }
            OUTLINE_TEXT_REF_ATOM => {
                let index = u32_at(r.body, 0).unwrap_or(u32::MAX) as usize;
                if let Some((k, text)) = outline.get(index) {
                    found = Some(ShapeText { kind: *k, text: text.clone(), look: TextLook::default() });
                }
            }
            _ => {}
        }
    }
    found.filter(|t| t.kind != TEXT_NOTES)
}

fn read_shape(body: &[u8], outline: &[(u32, String)]) -> RawShape {
    let mut s = RawShape::default();
    let edges32 = |b: &[u8]| Some([f64::from(i32_at(b, 0)?), f64::from(i32_at(b, 4)?), f64::from(i32_at(b, 8)?), f64::from(i32_at(b, 12)?)]);
    for r in records(body) {
        match r.kind {
            ART_FSP => s.flags = u32_at(r.body, 4).unwrap_or(0),
            ART_FSPGR => s.group = edges32(r.body),
            ART_CHILD_ANCHOR => s.child = edges32(r.body),
            // PowerPoint states top, left, right, bottom: as 16-bit values, or 32-bit ones
            ART_CLIENT_ANCHOR => {
                let v: Option<[f64; 4]> = if r.body.len() >= 16 {
                    edges32(r.body)
                } else {
                    let h = |at: usize| u16_at(r.body, at).map(|v| f64::from(v as i16));
                    (|| Some([h(0)?, h(2)?, h(4)?, h(6)?]))()
                };
                s.client = v.map(|[top, left, right, bottom]| [left, top, right, bottom]);
            }
            ART_FOPT => {
                for i in 0..usize::from(r.instance) {
                    let (Some(id), Some(value)) = (u16_at(r.body, i * 6), u32_at(r.body, i * 6 + 2)) else { break };
                    match id & 0x3FFF {
                        // rotation, in 16.16 fixed degrees
                        0x0004 => s.rotation = f64::from(value as i32) / 65536.0,
                        // the picture to show
                        0x0104 => s.picture = Some(value).filter(|v| *v > 0),
                        _ => {}
                    }
                }
            }
            ART_TEXTBOX => s.text = read_textbox(r.body, outline),
            // a placeholder says so in its client data; nothing more is needed of it
            ART_CLIENT_DATA | PLACEHOLDER_ATOM => {}
            _ => {}
        }
    }
    s
}

/// The shapes of a group of the drawing, groups within it followed, placed on the slide.
fn read_group(body: &[u8], parent: Map, depth: usize, outline: &[(u32, String)], out: &mut Vec<Drawn>, budget: &mut usize) {
    let mut map = parent;
    let mut first = true;
    for r in records(body) {
        if *budget == 0 || out.len() >= MAX_SHAPES {
            return;
        }
        *budget -= 1;
        match r.kind {
            ART_SP => {
                let s = read_shape(r.body, outline);
                let was_first = std::mem::replace(&mut first, false);
                if was_first && (s.group.is_some() || s.flags & 0x5 != 0) {
                    // the group's own shape: where the group sits, and the space its children are in
                    let anchor = s.client.or(s.child.map(|c| parent.rect(c)));
                    if let (Some(g), Some(a)) = (s.group, anchor) {
                        let (gw, gh) = (g[2] - g[0], g[3] - g[1]);
                        if gw.abs() > 0.0 && gh.abs() > 0.0 {
                            let (sx, sy) = ((a[2] - a[0]) / gw, (a[3] - a[1]) / gh);
                            map = Map { ox: a[0] - g[0] * sx, oy: a[1] - g[1] * sy, sx, sy };
                        }
                    }
                    continue;
                }
                // deleted, or the slide's background
                if s.flags & 0x8 != 0 || s.flags & 0x400 != 0 {
                    continue;
                }
                let Some(e) = s.client.or(s.child.map(|c| map.rect(c))) else { continue };
                let text = s.text.filter(|t| !t.text.trim().is_empty());
                if text.is_none() && s.picture.is_none() {
                    continue;
                }
                // master units are 576 to the inch: points are an eighth of them
                let rect = [e[0].min(e[2]) / 8.0, e[1].min(e[3]) / 8.0, (e[2] - e[0]).abs() / 8.0, (e[3] - e[1]).abs() / 8.0];
                if !rect.iter().all(|v| v.is_finite()) || rect[2] <= 0.0 || rect[3] <= 0.0 {
                    continue;
                }
                out.push(Drawn { rect, rotation: s.rotation, text, picture: s.picture });
            }
            ART_SPGR if depth < MAX_DEPTH => read_group(r.body, map, depth + 1, outline, out, budget),
            _ => {}
        }
    }
}

/// The texts and pictures of the slide record `body` (a `SlideContainer`).
fn read_drawing(body: &[u8], outline: &[(u32, String)], budget: &mut usize) -> Vec<Drawn> {
    let mut out = Vec::new();
    for r in records(body) {
        if r.kind != DRAWING {
            continue;
        }
        for dg in records(r.body).filter(|x| x.kind == ART_DG) {
            for g in records(dg.body).filter(|x| x.kind == ART_SPGR) {
                read_group(g.body, Map::SLIDE, 0, outline, &mut out, budget);
            }
        }
    }
    out
}

/// Where each persisted record is in the stream ([MS-PPT] `PersistDirectoryAtom`): later edits are
/// appended, so a later directory's entry replaces an earlier one's.
fn persist_offsets(data: &[u8]) -> HashMap<u32, u32> {
    let mut map = HashMap::new();
    for r in records(data).take(MAX_RECORDS) {
        if r.kind != PERSIST_DIRECTORY_ATOM {
            continue;
        }
        let mut at = 0usize;
        while let Some(head) = u32_at(r.body, at) {
            let (id, n) = (head & 0x000F_FFFF, head >> 20);
            at += 4;
            for i in 0..n {
                let Some(offset) = u32_at(r.body, at) else { break };
                map.insert(id + i, offset);
                at += 4;
            }
        }
    }
    map
}

/// A picture of the file: its bytes and their type.
pub(crate) struct Picture {
    pub(crate) data: Vec<u8>,
    pub(crate) mime: &'static str,
    pub(crate) ext: &'static str,
}

/// The picture a blip record holds: a JPEG or PNG as it is, a bitmap given the file header it
/// lacks. Metafiles (EMF, WMF, PICT) are left out.
fn blip_picture(r: &Record<'_>) -> Option<Picture> {
    // one 16-byte id, or two when the instance's low bit is set, then a tag byte
    let skip = if r.instance & 1 == 1 { 33 } else { 17 };
    match r.kind {
        0xF01D | 0xF02A => {
            let at = r.body.windows(3).take(64).position(|w| w == [0xFF, 0xD8, 0xFF])?;
            Some(Picture { data: r.body.get(at..)?.to_vec(), mime: "image/jpeg", ext: "jpg" })
        }
        0xF01E => {
            let at = r.body.windows(4).take(64).position(|w| w == [0x89, b'P', b'N', b'G'])?;
            Some(Picture { data: r.body.get(at..)?.to_vec(), mime: "image/png", ext: "png" })
        }
        0xF01F => {
            let dib = r.body.get(skip..)?;
            let header = u32_at(dib, 0)? as usize;
            let bits = u16_at(dib, 14)?;
            let used = u32_at(dib, 32).unwrap_or(0) as usize;
            let palette = if bits <= 8 { (if used == 0 { 1usize << bits } else { used.min(256) }) * 4 } else { 0 };
            let offset = 14usize.checked_add(header)?.checked_add(palette)?;
            let mut data = Vec::with_capacity(dib.len() + 14);
            data.extend(b"BM");
            data.extend(((dib.len() + 14) as u32).to_le_bytes());
            data.extend([0u8; 4]);
            data.extend((offset as u32).to_le_bytes());
            data.extend(dib);
            Some(Picture { data, mime: "image/bmp", ext: "bmp" })
        }
        _ => None,
    }
}

/// The file's pictures in the order shapes number them ([MS-ODRAW] `OfficeArtBStoreContainer`),
/// `None` for one that can't be shown.
fn read_pictures(data: &[u8], pictures: &[u8]) -> Vec<Option<Picture>> {
    fn find<'a>(data: &'a [u8], path: &[u16]) -> Option<&'a [u8]> {
        let (first, rest) = path.split_first()?;
        for r in records(data).take(MAX_RECORDS) {
            if r.kind == *first {
                return if rest.is_empty() { Some(r.body) } else { find(r.body, rest) };
            }
        }
        None
    }
    // Document ▸ drawing group ▸ OfficeArt's group container ▸ its picture store
    let Some(store) = find(data, &[0x03E8, DRAWING_GROUP, ART_DGG, ART_BSTORE]) else { return Vec::new() };
    store_pictures(store, pictures)
}

/// The pictures of an OfficeArt picture store (`OfficeArtBStoreContainer`'s contents), in the
/// order shapes number them; a picture not held in its entry is read from `pictures` (a
/// presentation's Pictures stream). `None` for one that can't be shown.
pub(crate) fn store_pictures(store: &[u8], pictures: &[u8]) -> Vec<Option<Picture>> {
    let mut out = Vec::new();
    let mut total = 0usize;
    for r in records(store).take(10_000) {
        let mut picture = None;
        if r.kind == ART_BSE {
            let name = usize::from(r.body.get(33).copied().unwrap_or(0));
            // held in the entry itself, else at its offset in the Pictures stream
            let embedded = r.body.get(36 + name..).and_then(|b| record_at(b, 0)).map(|(b, _)| b);
            let delayed = u32_at(r.body, 28).filter(|o| *o != u32::MAX).and_then(|o| record_at(pictures, o as usize)).map(|(b, _)| b);
            picture = embedded.as_ref().and_then(blip_picture).or_else(|| delayed.as_ref().and_then(blip_picture));
        } else if (0xF018..=0xF117).contains(&r.kind) {
            picture = blip_picture(&r);
        }
        if let Some(p) = &picture {
            total += p.data.len();
            if total > MAX_PICTURE_BYTES {
                picture = None;
            }
        }
        out.push(picture);
    }
    out
}

// ---------------------------------------------------------------- the presentation

/// A text box for `t` in `rect` (points), in the looks the file states or its kind's usual ones.
fn text_shape(p: &mut Presentation, rect: [f64; 4], rotation: f64, t: &ShapeText) -> Option<Shape> {
    let title = matches!(t.kind, TEXT_TITLE | TEXT_CENTER_TITLE);
    let lines: Vec<&str> = t.text.trim_end_matches('\r').split('\r').collect();
    // the master's size for the kind when the run states none (PowerPoint 97-2003's defaults)
    let mut size = t.look.size.unwrap_or(match t.kind {
        TEXT_TITLE | TEXT_CENTER_TITLE => 40.0,
        TEXT_BODY | TEXT_CENTER_BODY | 7 | 8 => 26.0,
        _ => 18.0,
    });
    // kept inside its box: about how tall the text sets at this size, shrunk to fit if taller
    let width = (rect[2] - 14.4).max(size);
    let rows: f64 = lines.iter().map(|l| ((l.chars().count() as f64 * size * 0.5) / width).ceil().max(1.0)).sum();
    let tall = rows * size * 1.2;
    let room = (rect[3] - 7.2).max(size);
    if tall > room {
        size = (size * (room / tall).sqrt()).max(size * 0.45).max(6.0);
    }
    // a light colour is for a dark design, which isn't carried over: on the plain ground it would vanish
    let color = t.look.color.filter(|[r, g, b]| 0.2126 * f64::from(*r) + 0.7152 * f64::from(*g) + 0.0722 * f64::from(*b) < 200.0);
    let props = RunProps {
        size: Some((size * 2.0).round() / 2.0),
        bold: t.look.bold.or(title.then_some(true)),
        italic: t.look.italic,
        fill: color.map(|[r, g, b]| deckcraft_model::Fill::Solid { color: ColorRef::rgb(Rgba::rgb(r, g, b)) }),
        ..RunProps::default()
    };
    let align = match t.look.align {
        Some(1) => Some(Align::Center),
        Some(2) => Some(Align::Right),
        Some(3) => Some(Align::Justify),
        Some(_) => Some(Align::Left),
        None => matches!(t.kind, TEXT_CENTER_BODY | TEXT_CENTER_TITLE).then_some(Align::Center),
    };
    let mut body = TextBody::default();
    body.body.wrap = Some(true);
    body.body.anchor = Some(if title { Anchor::Middle } else { Anchor::Top });
    body.paragraphs = lines
        .iter()
        .map(|l| {
            let mut para = Paragraph { runs: if l.is_empty() { vec![] } else { vec![Run::with(*l, props.clone())] }, ..Paragraph::default() };
            para.props.align = align;
            para.end_props = props.clone();
            para
        })
        .collect();
    Some(Shape {
        id: ShapeId(p.alloc_id()?),
        name: if title { "Title".into() } else { "Text".into() },
        xfrm: Some(Xfrm { x: rect[0], y: rect[1], w: rect[2], h: rect[3], rot: rotation, ..Xfrm::default() }),
        text: Some(body),
        text_box: true,
        ..Shape::default()
    })
}

/// A DeckCraft presentation holding the slides of the .ppt file `bytes`: each slide's text boxes
/// and pictures where the file puts them, or, for a slide whose drawing holds none, its outline
/// text on a Title and Content slide.
pub(crate) fn import(bytes: &[u8]) -> Result<Presentation, String> {
    let (data, picture_stream) = streams(bytes)?;
    let o = outline_of(&data);
    let offsets = persist_offsets(&data);
    let pictures = read_pictures(&data, &picture_stream);
    // master units are 576 to the inch: points are an eighth of them
    let (w, h) = o.size.filter(|(w, h)| *w > 0 && *h > 0).map_or((720.0, 540.0), |(w, h)| (f64::from(w) / 8.0, f64::from(h) / 8.0));
    let mut p = deckcraft_model::defaults::blank_presentation(deckcraft_model::Size::new(w, h), Default::default(), false);
    let content = deckcraft_model::defaults::layout_of_kind(&p, LayoutType::TitleAndContent);
    let title_only = deckcraft_model::defaults::layout_of_kind(&p, LayoutType::TitleOnly).or(content);
    let blank = deckcraft_model::defaults::layout_of_kind(&p, LayoutType::Blank).or(title_only);
    let mut budget = MAX_RECORDS;
    // a file with no outline at all still has its slides' records, in the order they were written
    let mut slides = o.slides;
    if slides.is_empty() {
        slides = records(&data).filter(|r| r.kind == SLIDE).take(MAX_SLIDES).map(|_| SlideText::default()).collect();
    }
    let loose: Vec<&[u8]> = records(&data).filter(|r| r.kind == SLIDE).map(|r| r.body).collect();
    let by_directory = slides.iter().any(|s| offsets.contains_key(&s.persist));
    let mut media: HashMap<u32, deckcraft_model::MediaId> = HashMap::new();
    for (i, s) in slides.into_iter().take(MAX_SLIDES).enumerate() {
        let record = if by_directory {
            offsets.get(&s.persist).and_then(|o| record_at(&data, *o as usize)).map(|(r, _)| r).filter(|r| r.kind == SLIDE).map(|r| r.body)
        } else {
            loose.get(i).copied()
        };
        let drawn = record.map(|body| read_drawing(body, &s.texts, &mut budget)).unwrap_or_default();
        let shows = |d: &Drawn| d.text.is_some() || d.picture.and_then(|n| pictures.get(n as usize - 1)).is_some_and(Option::is_some);
        if drawn.iter().any(shows) {
            let Some(layout) = blank else { break };
            let Some(mut slide) = deckcraft_model::defaults::new_slide(&mut p, layout) else { break };
            // only what the file drew: the layout's own empty placeholders would show their prompts
            slide.shapes.clear();
            for d in &drawn {
                if let Some(n) = d.picture {
                    let id = match media.get(&n) {
                        Some(id) => Some(*id),
                        None => pictures.get(n as usize - 1).and_then(Option::as_ref).and_then(|pic| {
                            let id = p.add_media(&format!("image{n}.{}", pic.ext), pic.mime, pic.data.clone())?;
                            media.insert(n, id);
                            Some(id)
                        }),
                    };
                    if let (Some(media), Some(id)) = (id, p.alloc_id()) {
                        slide.shapes.push(Shape {
                            id: ShapeId(id),
                            name: "Picture".into(),
                            xfrm: Some(Xfrm { x: d.rect[0], y: d.rect[1], w: d.rect[2], h: d.rect[3], rot: d.rotation, ..Xfrm::default() }),
                            kind: ShapeKind::Picture { fill: PictureFill { media, ..PictureFill::default() } },
                            ..Shape::default()
                        });
                    }
                }
                if let Some(t) = &d.text
                    && let Some(shape) = text_shape(&mut p, d.rect, d.rotation, t)
                {
                    slide.shapes.push(shape);
                }
            }
            p.slides.push(Arc::new(slide));
            continue;
        }
        // nothing drawn could be read: the outline's text, as a Title and Content slide
        let body: Vec<String> = s.body.into_iter().filter(|l| !l.trim().is_empty()).collect();
        let Some(layout) = (if body.is_empty() { title_only } else { content }) else { break };
        let Some(mut slide) = deckcraft_model::defaults::new_slide(&mut p, layout) else { break };
        for shape in &mut slide.shapes {
            let Some(ph) = &shape.ph else { continue };
            match ph.kind {
                PhType::Title | PhType::CtrTitle => shape.text = Some(TextBody::from_text(&s.title)),
                PhType::Body | PhType::Obj | PhType::SubTitle => shape.text = Some(TextBody::from_text(&body.join("\n"))),
                _ => {}
            }
        }
        p.slides.push(Arc::new(slide));
    }
    if p.slides.is_empty() {
        return Err("no slides were found in this PowerPoint 97-2003 file".into());
    }
    Ok(p)
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;

    fn record(kind: u16, instance: u16, container: bool, body: &[u8]) -> Vec<u8> {
        let ver = if container { 0xF } else { 0 };
        let mut out = ((instance << 4) | ver).to_le_bytes().to_vec();
        out.extend(kind.to_le_bytes());
        out.extend((body.len() as u32).to_le_bytes());
        out.extend(body);
        out
    }

    fn header(t: u32) -> Vec<u8> {
        record(TEXT_HEADER_ATOM, 0, false, &t.to_le_bytes())
    }

    fn chars(s: &str) -> Vec<u8> {
        record(TEXT_CHARS_ATOM, 0, false, &s.encode_utf16().flat_map(u16::to_le_bytes).collect::<Vec<u8>>())
    }

    fn file(stream: &[u8], pictures: Option<&[u8]>) -> Vec<u8> {
        use std::io::Write;
        let mut cfb = cfb::CompoundFile::create(std::io::Cursor::new(Vec::new())).unwrap();
        cfb.create_stream("PowerPoint Document").unwrap().write_all(stream).unwrap();
        if let Some(p) = pictures {
            cfb.create_stream("Pictures").unwrap().write_all(p).unwrap();
        }
        cfb.into_inner().into_inner()
    }

    fn document(list: &[u8], extra: &[u8]) -> Vec<u8> {
        let mut atom = 5760i32.to_le_bytes().to_vec();
        atom.extend(4320i32.to_le_bytes());
        atom.extend([0u8; 32]);
        let mut doc = record(DOCUMENT_ATOM, 0, false, &atom);
        doc.extend(extra);
        doc.extend(record(SLIDE_LIST_WITH_TEXT, 0, true, list));
        record(0x03E8, 0, true, &doc)
    }

    /// A .ppt file of two slides: a title with a subtitle, and a title with two bullets.
    pub(crate) fn sample_ppt() -> Vec<u8> {
        let bytes = |s: &str| record(TEXT_BYTES_ATOM, 0, false, s.as_bytes());
        let mut list = Vec::new();
        list.extend(record(SLIDE_PERSIST_ATOM, 0, false, &[0; 20]));
        list.extend(header(6));
        list.extend(chars("Quarterly review"));
        list.extend(header(5));
        list.extend(bytes("Sales and plans"));
        list.extend(record(SLIDE_PERSIST_ATOM, 0, false, &[0; 20]));
        list.extend(header(0));
        list.extend(chars("Highlights"));
        list.extend(header(1));
        list.extend(chars("Revenue up 36 %\rTwo new markets"));
        file(&document(&list, &[]), None)
    }

    #[test]
    fn reads_the_slides_text_and_size() {
        let o = outline(&sample_ppt()).unwrap();
        assert_eq!(o.size, Some((5760, 4320)));
        assert_eq!(o.slides.len(), 2);
        assert_eq!(o.slides[0].title, "Quarterly review");
        assert_eq!(o.slides[0].body, ["Sales and plans"]);
        assert_eq!(o.slides[1].body, ["Revenue up 36 %", "Two new markets"]);
        let p = import(&sample_ppt()).unwrap();
        assert_eq!(p.slides.len(), 2);
        assert_eq!((p.slide_size.width, p.slide_size.height), (720.0, 540.0));
    }

    #[test]
    fn a_damaged_record_stream_ends_quietly() {
        let mut o = Outline::default();
        let mut budget = MAX_RECORDS;
        // a container claiming more bytes than there are
        walk(&[0x0F, 0, 0xE8, 0x03, 0xFF, 0xFF, 0xFF, 0x7F, 1, 2], 0, &mut o, &mut budget);
        assert!(o.slides.is_empty());
        assert!(import(b"not a compound file").is_err());
        assert_eq!(text_look(&[1, 2, 3], 40), TextLook::default());
    }

    /// A shape record: flags, where it sits (16-bit top, left, right, bottom), and what it holds.
    fn shape(flags: u32, anchor: Option<[i16; 4]>, more: &[u8]) -> Vec<u8> {
        let mut fsp = 1u32.to_le_bytes().to_vec();
        fsp.extend(flags.to_le_bytes());
        let mut body = record(ART_FSP, 202, false, &fsp);
        if let Some(a) = anchor {
            body.extend(record(ART_CLIENT_ANCHOR, 0, false, &a.iter().flat_map(|v| v.to_le_bytes()).collect::<Vec<u8>>()));
        }
        body.extend(more);
        record(ART_SP, 0, true, &body)
    }

    /// A slide record whose drawing holds `shapes` under the drawing's top group.
    fn slide(shapes: &[u8]) -> Vec<u8> {
        let mut group = shape(0x5, None, &record(ART_FSPGR, 1, false, &[0u8; 16]));
        group.extend(shapes);
        let dg = record(ART_DG, 0, true, &record(ART_SPGR, 0, true, &group));
        record(SLIDE, 0, true, &record(DRAWING, 0, true, &dg))
    }

    /// The file issue #102 describes: a slide whose words sit in a free text box, and a picture,
    /// neither of which is in the outline. It used to open as a blank white page.
    #[test]
    fn a_slide_of_free_text_boxes_and_pictures_is_not_blank() {
        // a text box: 24 pt bold, centred
        let text = "Free text";
        let mut style = ((text.len() + 1) as u32).to_le_bytes().to_vec(); // one paragraph run
        style.extend(0u16.to_le_bytes());
        style.extend(0x0800u32.to_le_bytes());
        style.extend(1u16.to_le_bytes()); // centred
        style.extend(((text.len() + 1) as u32).to_le_bytes()); // one character run
        style.extend((0x0002_0001u32).to_le_bytes()); // bold stated, size stated
        style.extend(1u16.to_le_bytes()); // bold
        style.extend(24u16.to_le_bytes());
        let mut textbox = header(4);
        textbox.extend(chars(text));
        textbox.extend(record(STYLE_TEXT_PROP_ATOM, 0, false, &style));
        let mut shapes = shape(0xA00, Some([800, 1600, 4000, 1600]), &record(ART_TEXTBOX, 0, true, &textbox));
        // a picture frame showing the first picture
        let mut fopt = 0x4104u16.to_le_bytes().to_vec();
        fopt.extend(1u32.to_le_bytes());
        shapes.extend(shape(0xA00, Some([2000, 800, 2400, 3200]), &record(ART_FOPT, 1, false, &fopt)));
        // a title placeholder whose text is the outline's first
        let by_ref = record(OUTLINE_TEXT_REF_ATOM, 0, false, &0u32.to_le_bytes());
        shapes.extend(shape(0xA00, Some([100, 400, 5000, 700]), &record(ART_TEXTBOX, 0, true, &by_ref)));

        // the picture: a 1 × 1 PNG in the Pictures stream, listed in the picture store
        let png = lightcraft_codecs::encode_png(
            &lightcraft_codecs::EncodeImage::rgba8(&lightcraft_raster::Rgba8 { width: 1, height: 1, data: vec![[200, 40, 40, 255]] }),
            &lightcraft_codecs::EncodeMeta::default(),
        )
        .unwrap();
        let mut blip = vec![0u8; 17];
        blip.extend(&png);
        let pictures = record(0xF01E, 0x6E0, false, &blip);
        let mut bse = vec![0u8; 36];
        bse[28..32].copy_from_slice(&0u32.to_le_bytes());
        let store = record(ART_BSTORE, 1, true, &record(ART_BSE, 6, false, &bse));
        let group = record(DRAWING_GROUP, 0, true, &record(ART_DGG, 0, true, &store));

        // one slide in the outline (only its title), whose record the persist directory finds
        let mut persist = 7u32.to_le_bytes().to_vec();
        persist.extend([0u8; 16]);
        let mut list = record(SLIDE_PERSIST_ATOM, 0, false, &persist);
        list.extend(header(0));
        list.extend(chars("From the outline"));
        let mut stream = document(&list, &group);
        let at = stream.len() as u32;
        stream.extend(slide(&shapes));
        let mut directory = ((1u32 << 20) | 7).to_le_bytes().to_vec();
        directory.extend(at.to_le_bytes());
        stream.extend(record(PERSIST_DIRECTORY_ATOM, 0, false, &directory));

        let p = import(&file(&stream, Some(&pictures))).unwrap();
        assert_eq!(p.slides.len(), 1);
        let shapes = &p.slides[0].shapes;
        let texts: Vec<String> = shapes.iter().filter_map(|s| s.text.as_ref()).map(TextBody::text).collect();
        assert_eq!(texts, ["Free text", "From the outline"]);
        let free = shapes.iter().find(|s| s.text.as_ref().is_some_and(|t| t.text() == "Free text")).unwrap();
        // 16-bit top, left, right, bottom in master units, an eighth of which is a point
        assert_eq!(free.xfrm.map(|x| (x.x, x.y, x.w, x.h)), Some((200.0, 100.0, 300.0, 100.0)));
        let run = &free.text.as_ref().unwrap().paragraphs[0];
        assert_eq!((run.runs[0].props.size, run.runs[0].props.bold, run.props.align), (Some(24.0), Some(true), Some(Align::Center)));
        assert_eq!(shapes.iter().filter(|s| matches!(s.kind, ShapeKind::Picture { .. })).count(), 1);
        assert_eq!(p.media.len(), 1);
        // and it draws: not a white page
        let img = deckcraft_render::render_slide(&p, 0, &deckcraft_render::RenderOpts { scale: 0.5, ..Default::default() });
        assert!(img.pixels.as_chunks::<4>().0.iter().any(|px| px[0] < 200 || px[1] < 200 || px[2] < 200), "the slide drew blank");
    }
}

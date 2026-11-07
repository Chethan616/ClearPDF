//! PowerPoint 97-2003 presentations (.ppt), which DeckCraft doesn't read: their slides' text, read
//! from the binary file's outline ([MS-PPT] `SlideListWithText`), set in a DeckCraft presentation
//! of the file's slide size with Title and Content slides, so they open, draw, search and export
//! like any deck. Pictures, shapes and the original layout are not carried over.

use std::io::Read;
use std::sync::Arc;

use deckcraft_model::text::TextBody;
use deckcraft_model::{LayoutType, PhType, Presentation};

/// The most records read and the deepest nesting followed (a hostile file can claim anything).
const MAX_RECORDS: usize = 500_000;
const MAX_DEPTH: usize = 16;
/// The most slides made.
const MAX_SLIDES: usize = 2_000;

const DOCUMENT_ATOM: u16 = 0x03E9;
const SLIDE_LIST_WITH_TEXT: u16 = 0x0FF0;
const SLIDE_PERSIST_ATOM: u16 = 0x03F3;
const TEXT_HEADER_ATOM: u16 = 0x0F9F;
const TEXT_CHARS_ATOM: u16 = 0x0FA0;
const TEXT_BYTES_ATOM: u16 = 0x0FA8;

/// One slide's text: its title, and the rest (body, subtitle, other text) in order.
#[derive(Default, Debug, PartialEq)]
struct SlideText {
    title: String,
    body: Vec<String>,
}

#[derive(Default)]
struct Outline {
    /// The slide size in master units (576 to the inch).
    size: Option<(i32, i32)>,
    slides: Vec<SlideText>,
}

/// One record's header: its type, whether it holds other records, and its body.
struct Record<'a> {
    kind: u16,
    instance: u16,
    container: bool,
    body: &'a [u8],
}

fn records(data: &[u8]) -> impl Iterator<Item = Record<'_>> {
    let mut at = 0usize;
    std::iter::from_fn(move || {
        let head = data.get(at..at.checked_add(8)?)?;
        let ver_inst = u16::from_le_bytes([head[0], head[1]]);
        let kind = u16::from_le_bytes([head[2], head[3]]);
        let len = u32::from_le_bytes([head[4], head[5], head[6], head[7]]) as usize;
        let start = at + 8;
        let end = start.checked_add(len)?.min(data.len());
        at = start.checked_add(len)?;
        Some(Record { kind, instance: ver_inst >> 4, container: ver_inst & 0xF == 0xF, body: data.get(start..end)? })
    })
}

/// The text of a slide list: a new slide at each persist atom, each text after the header saying
/// what kind of text it is.
fn read_slide_list(body: &[u8], out: &mut Outline, budget: &mut usize) {
    let mut kind = 1u32;
    for r in records(body) {
        if *budget == 0 || out.slides.len() > MAX_SLIDES {
            return;
        }
        *budget -= 1;
        match r.kind {
            SLIDE_PERSIST_ATOM => out.slides.push(SlideText::default()),
            TEXT_HEADER_ATOM => kind = r.body.get(..4).map_or(1, |b| u32::from_le_bytes([b[0], b[1], b[2], b[3]])),
            TEXT_CHARS_ATOM | TEXT_BYTES_ATOM => {
                let text = if r.kind == TEXT_CHARS_ATOM {
                    let units: Vec<u16> = r.body.as_chunks::<2>().0.iter().map(|c| u16::from_le_bytes(*c)).collect();
                    String::from_utf16_lossy(&units)
                } else {
                    // Windows-1252 for the common range; the rest as Latin-1
                    r.body.iter().map(|&b| char::from(b)).collect()
                };
                // paragraphs end in CR; a vertical tab is a line break inside one
                let text = text.replace('\u{b}', "\n");
                let Some(slide) = out.slides.last_mut() else { continue };
                match kind {
                    // title, centred title
                    0 | 6 => {
                        if !slide.title.is_empty() {
                            slide.title.push(' ');
                        }
                        slide.title.push_str(text.trim_end_matches('\r'));
                    }
                    // notes are not slide text
                    2 => {}
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
                if let Some(b) = r.body.get(..8) {
                    out.size = Some((i32::from_le_bytes([b[0], b[1], b[2], b[3]]), i32::from_le_bytes([b[4], b[5], b[6], b[7]])));
                }
            }
            // instance 0: the slides (1 is the masters, 2 the notes)
            SLIDE_LIST_WITH_TEXT if r.instance == 0 => read_slide_list(r.body, out, budget),
            _ if r.container && depth < MAX_DEPTH => walk(r.body, depth + 1, out, budget),
            _ => {}
        }
    }
}

/// The outline of the .ppt file `bytes`.
fn outline(bytes: &[u8]) -> Result<Outline, String> {
    let mut cfb = cfb::CompoundFile::open(std::io::Cursor::new(bytes)).map_err(|e| format!("not a PowerPoint 97-2003 file: {e}"))?;
    let mut stream = cfb.open_stream("PowerPoint Document").map_err(|_| "the file has no PowerPoint Document stream".to_string())?;
    let mut data = Vec::new();
    // the stream is read in full but never past 512 MB
    stream.by_ref().take(512 << 20).read_to_end(&mut data).map_err(|e| e.to_string())?;
    let mut out = Outline::default();
    let mut budget = MAX_RECORDS;
    walk(&data, 0, &mut out, &mut budget);
    Ok(out)
}

/// A DeckCraft presentation holding the slides' text of the .ppt file `bytes`.
pub(crate) fn import(bytes: &[u8]) -> Result<Presentation, String> {
    let o = outline(bytes)?;
    // master units are 576 to the inch: points are an eighth of them
    let (w, h) = o.size.filter(|(w, h)| *w > 0 && *h > 0).map_or((720.0, 540.0), |(w, h)| (f64::from(w) / 8.0, f64::from(h) / 8.0));
    let mut p = deckcraft_model::defaults::blank_presentation(deckcraft_model::Size::new(w, h), Default::default(), false);
    let content = deckcraft_model::defaults::layout_of_kind(&p, LayoutType::TitleAndContent);
    let title_only = deckcraft_model::defaults::layout_of_kind(&p, LayoutType::TitleOnly).or(content);
    for s in o.slides.into_iter().take(MAX_SLIDES) {
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

    /// A .ppt file of two slides: a title with a subtitle, and a title with two bullets.
    pub(crate) fn sample_ppt() -> Vec<u8> {
        let header = |t: u32| record(TEXT_HEADER_ATOM, 0, false, &t.to_le_bytes());
        let chars = |s: &str| record(TEXT_CHARS_ATOM, 0, false, &s.encode_utf16().flat_map(u16::to_le_bytes).collect::<Vec<u8>>());
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
        let mut atom = 5760i32.to_le_bytes().to_vec();
        atom.extend(4320i32.to_le_bytes());
        atom.extend([0u8; 32]);
        let mut doc = record(DOCUMENT_ATOM, 0, false, &atom);
        doc.extend(record(SLIDE_LIST_WITH_TEXT, 0, true, &list));
        let stream = record(0x03E8, 0, true, &doc);
        let mut cfb = cfb::CompoundFile::create(std::io::Cursor::new(Vec::new())).unwrap();
        {
            use std::io::Write;
            let mut s = cfb.create_stream("PowerPoint Document").unwrap();
            s.write_all(&stream).unwrap();
        }
        cfb.into_inner().into_inner()
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
    }
}

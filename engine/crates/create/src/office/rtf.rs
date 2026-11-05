//! Rich Text Format (RTF 1.x) to flowing pages: paragraphs, character formatting, tabs, line
//! and page breaks, Unicode, simple tables and PNG/JPEG pictures. Destinations PdfCraft doesn't
//! draw (font and colour tables aside, which it reads; style sheets, document info, headers,
//! footnotes, fields' instructions, objects) are skipped.

use super::draw::{BLACK, Face, Family, Rgb, Writer, family_of};
use super::flow::{self, Align, Block, Cell, Inline, LineRule, PageSetup, Para, Row, Style, Table};

/// Deepest group nesting followed; deeper groups are skipped.
const MAX_GROUPS: usize = 256;
/// Largest picture read (hex digits decode to half as many bytes).
const MAX_PICTURE: usize = 32 << 20;
const TWIP: f64 = 1.0 / 20.0;

#[derive(Clone, Debug)]
struct CharState {
    bold: bool,
    italic: bool,
    underline: bool,
    strike: bool,
    size: f64,
    font: i64,
    colour: usize,
    highlight: usize,
    vert: i8,
    caps: bool,
    hidden: bool,
}

#[derive(Clone, Debug)]
struct ParaState {
    align: Align,
    left: f64,
    right: f64,
    first: f64,
    before: f64,
    after: f64,
    line: LineRule,
    in_table: bool,
    page_break_before: bool,
}

impl Default for ParaState {
    fn default() -> Self {
        ParaState {
            align: Align::Left,
            left: 0.0,
            right: 0.0,
            first: 0.0,
            before: 0.0,
            after: 0.0,
            line: LineRule::Auto(1.0),
            in_table: false,
            page_break_before: false,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum Dest {
    Text,
    Skip,
    FontTable,
    ColourTable,
    Picture,
    FieldInstruction,
}

#[derive(Clone, Debug)]
struct Group {
    chars: CharState,
    para: ParaState,
    dest: Dest,
    uc: usize,
}

struct Parser {
    groups: Vec<Group>,
    fonts: Vec<(i64, Family)>,
    font_entry: Option<(i64, Family, String)>,
    colours: Vec<Option<Rgb>>,
    colour_entry: (u8, u8, u8, bool),
    default_font: i64,
    encoding: &'static encoding_rs::Encoding,
    /// Unicode fallback characters still to skip after `\uN`.
    skip: usize,
    setup: PageSetup,
    blocks: Vec<Block>,
    inlines: Vec<Inline>,
    text: String,
    text_style: Option<Style>,
    // Tables.
    cell_edges: Vec<f64>,
    row_borders: bool,
    row_cells: Vec<Vec<Block>>,
    cell_blocks: Vec<Block>,
    table: Option<Table>,
    // Pictures.
    pict_hex: Vec<u8>,
    pict_kind: Option<&'static str>,
    pict_goal: (f64, f64),
    pict_scale: (f64, f64),
    writer_images: Vec<(usize, f64, f64)>,
    picture_count: usize,
}

impl Parser {
    fn top(&self) -> Option<&Group> {
        self.groups.last()
    }
    fn top_mut(&mut self) -> Option<&mut Group> {
        self.groups.last_mut()
    }
    fn dest(&self) -> Dest {
        self.top().map_or(Dest::Skip, |g| g.dest)
    }

    fn style(&self) -> Style {
        let Some(g) = self.top() else { return Style::default() };
        let c = &g.chars;
        let family = self.fonts.iter().find(|(n, _)| *n == c.font).map_or(Family::Serif, |(_, f)| *f);
        let mut size = c.size;
        let mut shift = 0.0;
        match c.vert {
            1 => {
                shift = size * 0.33;
                size *= 0.65;
            }
            -1 => {
                shift = -size * 0.14;
                size *= 0.65;
            }
            _ => {}
        }
        Style {
            face: Face { family, bold: c.bold, italic: c.italic },
            size,
            color: self.colours.get(c.colour).copied().flatten().unwrap_or(BLACK),
            underline: c.underline,
            strike: c.strike,
            highlight: if c.highlight > 0 { self.colours.get(c.highlight).copied().flatten() } else { None },
            shift,
        }
    }

    fn push_char(&mut self, ch: char) {
        if self.skip > 0 {
            self.skip -= 1;
            return;
        }
        match self.dest() {
            Dest::Text => {}
            Dest::FontTable => {
                if let Some((_, _, name)) = self.font_entry.as_mut() {
                    if ch == ';' {
                        self.finish_font();
                    } else {
                        name.push(ch);
                    }
                }
                return;
            }
            Dest::ColourTable => {
                if ch == ';' {
                    let (r, g, b, set) = self.colour_entry;
                    self.colours.push(set.then_some(Rgb(r, g, b)));
                    self.colour_entry = (0, 0, 0, false);
                }
                return;
            }
            _ => return,
        }
        if self.top().is_some_and(|g| g.chars.hidden) {
            return;
        }
        let ch = if self.top().is_some_and(|g| g.chars.caps) { ch.to_uppercase().next().unwrap_or(ch) } else { ch };
        let style = self.style();
        if self.text_style.as_ref() != Some(&style) {
            self.flush_text();
            self.text_style = Some(style);
        }
        self.text.push(ch);
    }

    fn flush_text(&mut self) {
        if !self.text.is_empty()
            && let Some(s) = self.text_style.clone()
        {
            self.inlines.push(Inline::Text(std::mem::take(&mut self.text), s));
        }
        self.text.clear();
    }

    fn inline(&mut self, i: Inline) {
        if self.dest() != Dest::Text || self.top().is_some_and(|g| g.chars.hidden) {
            return;
        }
        self.flush_text();
        self.inlines.push(i);
    }

    fn finish_font(&mut self) {
        if let Some((n, fam, name)) = self.font_entry.take() {
            let fam = if name.trim().is_empty() { fam } else { family_of(name.trim()).max_with(fam) };
            self.fonts.push((n, fam));
        }
    }

    fn paragraph(&mut self) {
        self.flush_text();
        let Some(g) = self.top() else { return };
        let ps = g.para.clone();
        let size = self.style().size;
        let para = Para {
            inlines: std::mem::take(&mut self.inlines),
            align: ps.align,
            left: ps.left,
            right: ps.right,
            first: ps.first,
            before: ps.before,
            after: ps.after,
            line: ps.line,
            page_break_before: ps.page_break_before,
            mark_size: size,
            ..Default::default()
        };
        if ps.in_table {
            self.cell_blocks.push(Block::Para(para));
        } else {
            self.end_table();
            self.blocks.push(Block::Para(para));
        }
    }

    fn end_table(&mut self) {
        if let Some(t) = self.table.take() {
            self.blocks.push(Block::Table(t));
        }
    }

    fn cell(&mut self) {
        self.flush_text();
        if !self.inlines.is_empty() || self.cell_blocks.is_empty() {
            if let Some(g) = self.top_mut() {
                g.para.in_table = true;
            }
            self.paragraph();
        }
        let blocks = std::mem::take(&mut self.cell_blocks);
        self.row_cells.push(blocks);
    }

    fn row(&mut self) {
        self.flush_text();
        if !self.inlines.is_empty() || !self.cell_blocks.is_empty() {
            self.cell();
        }
        let cells = std::mem::take(&mut self.row_cells);
        if cells.is_empty() {
            return;
        }
        // Column widths from the cell edges (\cellx), shared by the table's rows.
        let mut widths = Vec::new();
        let mut last = 0.0;
        for e in &self.cell_edges {
            widths.push((e - last).max(6.0));
            last = *e;
        }
        let line = self.row_borders.then_some((BLACK, 0.5));
        let row =
            Row { cells: cells.into_iter().map(|blocks| Cell { blocks, borders: [line; 4], ..Default::default() }).collect(), ..Default::default() };
        let table = self.table.get_or_insert_with(Table::default);
        if table.cols.is_empty() {
            table.cols = widths;
        }
        table.rows.push(row);
    }

    fn finish_picture(&mut self, writer: &mut Writer) {
        let hex = std::mem::take(&mut self.pict_hex);
        let Some(kind) = self.pict_kind.take() else { return };
        let bytes: Vec<u8> = hex.as_chunks::<2>().0.iter().filter_map(|[a, b]| Some((hex_val(*a)? << 4) | hex_val(*b)?)).collect();
        self.picture_count += 1;
        let key = format!("rtf-picture-{}-{kind}", self.picture_count);
        if let Some((id, natural)) = writer.image(&key, &bytes) {
            let (gw, gh) = self.pict_goal;
            let (mut w, mut h) = if gw > 0.0 && gh > 0.0 { (gw, gh) } else { natural };
            w *= self.pict_scale.0;
            h *= self.pict_scale.1;
            self.writer_images.push((id, w, h));
            // The picture's group is closing; add it to the enclosing text.
            self.flush_text();
            self.inlines.push(Inline::Image { id, w: w.clamp(1.0, 2000.0), h: h.clamp(1.0, 2000.0) });
        }
    }
}

trait MaxWith {
    fn max_with(self, other: Family) -> Family;
}

impl MaxWith for Family {
    /// A font's name decides, unless it says nothing (a sans default) and the font table's
    /// family class says serif or mono.
    fn max_with(self, other: Family) -> Family {
        if self == Family::Sans && other != Family::Sans { other } else { self }
    }
}

fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

const SKIPPED: [&str; 34] = [
    "stylesheet",
    "info",
    "header",
    "headerl",
    "headerr",
    "headerf",
    "footer",
    "footerl",
    "footerr",
    "footerf",
    "footnote",
    "annotation",
    "object",
    "listtable",
    "listoverridetable",
    "revtbl",
    "rsidtbl",
    "generator",
    "themedata",
    "colorschememapping",
    "latentstyles",
    "datastore",
    "xmlnstbl",
    "mmathPr",
    "pgdsctbl",
    "filetbl",
    "comment",
    "atnid",
    "atnauthor",
    "bkmkstart",
    "bkmkend",
    "xe",
    "tc",
    "nonshppict",
];

pub(crate) fn convert(bytes: &[u8], writer: &mut Writer) -> Result<(), String> {
    let mut p = Parser {
        groups: Vec::new(),
        fonts: Vec::new(),
        font_entry: None,
        colours: Vec::new(),
        colour_entry: (0, 0, 0, false),
        default_font: 0,
        encoding: encoding_rs::WINDOWS_1252,
        skip: 0,
        setup: PageSetup::default(),
        blocks: Vec::new(),
        inlines: Vec::new(),
        text: String::new(),
        text_style: None,
        cell_edges: Vec::new(),
        row_borders: false,
        row_cells: Vec::new(),
        cell_blocks: Vec::new(),
        table: None,
        pict_hex: Vec::new(),
        pict_kind: None,
        pict_goal: (0.0, 0.0),
        pict_scale: (1.0, 1.0),
        writer_images: Vec::new(),
        picture_count: 0,
    };
    let base = Group {
        chars: CharState {
            bold: false,
            italic: false,
            underline: false,
            strike: false,
            size: 12.0,
            font: 0,
            colour: 0,
            highlight: 0,
            vert: 0,
            caps: false,
            hidden: false,
        },
        para: ParaState::default(),
        dest: Dest::Text,
        uc: 1,
    };
    let mut i = 0usize;
    let mut depth_overflow = 0usize;
    let mut started = false;
    // Bytes of text in the document's code page, decoded together.
    let mut pending: Vec<u8> = Vec::new();
    let flush_bytes = |p: &mut Parser, pending: &mut Vec<u8>| {
        if pending.is_empty() {
            return;
        }
        let text = p.encoding.decode_without_bom_handling(pending).0.into_owned();
        pending.clear();
        for ch in text.chars() {
            p.push_char(ch);
        }
    };
    while let Some(&b) = bytes.get(i) {
        match b {
            b'{' => {
                flush_bytes(&mut p, &mut pending);
                i += 1;
                if depth_overflow > 0 || p.groups.len() >= MAX_GROUPS {
                    depth_overflow += 1;
                    continue;
                }
                let g = p.groups.last().cloned().unwrap_or_else(|| base.clone());
                p.groups.push(g);
                started = true;
            }
            b'}' => {
                flush_bytes(&mut p, &mut pending);
                i += 1;
                if depth_overflow > 0 {
                    depth_overflow -= 1;
                    continue;
                }
                let closing = p.dest();
                if closing == Dest::FontTable {
                    p.finish_font();
                }
                let parent_dest = p.groups.len().checked_sub(2).and_then(|k| p.groups.get(k)).map(|g| g.dest);
                if closing == Dest::Picture && parent_dest != Some(Dest::Picture) {
                    p.groups.pop();
                    p.finish_picture(writer);
                    continue;
                }
                p.groups.pop();
                if p.groups.is_empty() && started {
                    break;
                }
            }
            b'\\' => {
                flush_bytes(&mut p, &mut pending);
                i += 1;
                let Some(&c) = bytes.get(i) else { break };
                if depth_overflow > 0 {
                    i += 1;
                    continue;
                }
                if c.is_ascii_alphabetic() {
                    let start = i;
                    while bytes.get(i).is_some_and(|b| b.is_ascii_alphabetic()) && i - start < 32 {
                        i += 1;
                    }
                    let word = std::str::from_utf8(bytes.get(start..i).unwrap_or_default()).unwrap_or_default().to_string();
                    let mut param: Option<i64> = None;
                    let neg = bytes.get(i) == Some(&b'-');
                    if neg {
                        i += 1;
                    }
                    let ds = i;
                    while bytes.get(i).is_some_and(|b| b.is_ascii_digit()) && i - ds < 10 {
                        i += 1;
                    }
                    if i > ds {
                        let v: i64 = std::str::from_utf8(bytes.get(ds..i).unwrap_or_default()).ok().and_then(|s| s.parse().ok()).unwrap_or(0);
                        param = Some(if neg { -v } else { v });
                    }
                    if bytes.get(i) == Some(&b' ') {
                        i += 1;
                    }
                    if word == "bin" {
                        // Binary data follows: skip it.
                        let n = usize::try_from(param.unwrap_or(0).max(0)).unwrap_or(0);
                        i = i.saturating_add(n);
                        continue;
                    }
                    control(&mut p, &word, param, writer);
                } else {
                    i += 1;
                    match c {
                        b'\'' => {
                            let v = bytes.get(i..i + 2).and_then(|h| Some((hex_val(*h.first()?)? << 4) | hex_val(*h.get(1)?)?));
                            i += 2;
                            if let Some(v) = v {
                                if p.skip > 0 {
                                    p.skip -= 1;
                                } else {
                                    pending.push(v);
                                    flush_bytes(&mut p, &mut pending);
                                }
                            }
                        }
                        b'*' => {
                            // An optional destination: skipped unless it is one we read.
                            if let Some(g) = p.top_mut() {
                                g.dest = Dest::Skip;
                            }
                        }
                        b'~' => p.push_char('\u{A0}'),
                        b'_' => p.push_char('-'),
                        b'-' => {}
                        b'\\' | b'{' | b'}' => p.push_char(c as char),
                        b'\n' | b'\r' => p.paragraph(),
                        b'\t' => p.inline(Inline::Tab(p.style())),
                        _ => {}
                    }
                }
            }
            b'\r' | b'\n' => i += 1,
            _ => {
                i += 1;
                if depth_overflow > 0 {
                    continue;
                }
                match p.dest() {
                    Dest::Picture => {
                        if b.is_ascii_hexdigit() && p.pict_hex.len() < MAX_PICTURE * 2 {
                            p.pict_hex.push(b);
                        }
                    }
                    Dest::Text | Dest::FontTable | Dest::ColourTable => {
                        if b == b'\t' {
                            flush_bytes(&mut p, &mut pending);
                            p.inline(Inline::Tab(p.style()));
                        } else if p.skip > 0 {
                            p.skip -= 1;
                        } else {
                            pending.push(b);
                        }
                    }
                    _ => {}
                }
            }
        }
    }
    flush_bytes(&mut p, &mut pending);
    if p.groups.is_empty() {
        p.groups.push(base);
    }
    p.flush_text();
    if !p.inlines.is_empty() {
        p.paragraph();
    }
    if !p.row_cells.is_empty() {
        p.row();
    }
    p.end_table();
    let _ = p.default_font;
    let mut pages = flow::Pages::new();
    let items = flow::layout_blocks(&p.blocks, p.setup.content_width(), 0);
    let max = super::draw::MAX_PAGES;
    flow::paginate(items, &p.setup, &mut pages, max + 1);
    if pages.len() > max {
        return Err(format!("the converted document would have more than {max} pages"));
    }
    for (w, h, ops) in pages {
        writer.page(w, h, &ops).map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn control(p: &mut Parser, word: &str, param: Option<i64>, writer: &mut Writer) {
    let _ = writer;
    let on = param.is_none_or(|v| v != 0);
    let twips = |v: Option<i64>| v.unwrap_or(0).clamp(-1_000_000, 1_000_000) as f64 * TWIP;
    let dest = p.dest();
    // Destinations.
    match word {
        "fonttbl" => {
            if let Some(g) = p.top_mut() {
                g.dest = Dest::FontTable;
            }
            return;
        }
        "colortbl" => {
            if let Some(g) = p.top_mut() {
                g.dest = Dest::ColourTable;
            }
            return;
        }
        "pict" => {
            if let Some(g) = p.top_mut() {
                g.dest = if dest == Dest::Text { Dest::Picture } else { Dest::Skip };
            }
            p.pict_hex.clear();
            p.pict_kind = None;
            p.pict_goal = (0.0, 0.0);
            p.pict_scale = (1.0, 1.0);
            return;
        }
        "fldinst" => {
            if let Some(g) = p.top_mut() {
                g.dest = Dest::FieldInstruction;
            }
            return;
        }
        "fldrslt" => {
            // Field results show; keep the enclosing destination (unless skipped).
            if dest == Dest::Skip
                && let Some(g) = p.top_mut()
            {
                g.dest = Dest::Text;
            }
            return;
        }
        "shppict" => {
            if let Some(g) = p.top_mut() {
                g.dest = Dest::Text;
            }
            return;
        }
        w if SKIPPED.contains(&w) => {
            if let Some(g) = p.top_mut() {
                g.dest = Dest::Skip;
            }
            return;
        }
        _ => {}
    }
    match dest {
        Dest::FontTable => {
            match word {
                "f" => {
                    p.finish_font();
                    p.font_entry = Some((param.unwrap_or(0), Family::Sans, String::new()));
                }
                "froman" => {
                    if let Some(e) = p.font_entry.as_mut() {
                        e.1 = Family::Serif;
                    }
                }
                "fmodern" => {
                    if let Some(e) = p.font_entry.as_mut() {
                        e.1 = Family::Mono;
                    }
                }
                "fswiss" | "fnil" | "fscript" | "fdecor" => {}
                _ => {}
            }
            return;
        }
        Dest::ColourTable => {
            let v = param.unwrap_or(0).clamp(0, 255) as u8;
            match word {
                "red" => p.colour_entry = (v, p.colour_entry.1, p.colour_entry.2, true),
                "green" => p.colour_entry = (p.colour_entry.0, v, p.colour_entry.2, true),
                "blue" => p.colour_entry = (p.colour_entry.0, p.colour_entry.1, v, true),
                _ => {}
            }
            return;
        }
        Dest::Picture => {
            match word {
                "pngblip" => p.pict_kind = Some("png"),
                "jpegblip" => p.pict_kind = Some("jpeg"),
                "picwgoal" => p.pict_goal.0 = twips(param).abs(),
                "pichgoal" => p.pict_goal.1 = twips(param).abs(),
                "picscalex" => p.pict_scale.0 = (param.unwrap_or(100).clamp(1, 1000) as f64) / 100.0,
                "picscaley" => p.pict_scale.1 = (param.unwrap_or(100).clamp(1, 1000) as f64) / 100.0,
                _ => {}
            }
            return;
        }
        Dest::Skip | Dest::FieldInstruction => return,
        Dest::Text => {}
    }
    match word {
        "ansicpg" => {
            let cp = param.unwrap_or(1252);
            if let Some(e) = u16::try_from(cp).ok().and_then(codepage) {
                p.encoding = e;
            }
        }
        "mac" => p.encoding = encoding_rs::MACINTOSH,
        "deff" => p.default_font = param.unwrap_or(0),
        "paperw" => p.setup.w = twips(param).clamp(72.0, 14_400.0),
        "paperh" => p.setup.h = twips(param).clamp(72.0, 14_400.0),
        "margl" => p.setup.left = twips(param).clamp(0.0, 1000.0),
        "margr" => p.setup.right = twips(param).clamp(0.0, 1000.0),
        "margt" => p.setup.top = twips(param).clamp(0.0, 1000.0),
        "margb" => p.setup.bottom = twips(param).clamp(0.0, 1000.0),
        "par" => p.paragraph(),
        "sect" => p.paragraph(),
        "line" => p.inline(Inline::Break),
        "page" => {
            p.flush_text();
            if !p.inlines.is_empty() {
                p.paragraph();
            }
            p.end_table();
            p.blocks.push(Block::PageBreak);
        }
        "tab" => {
            let s = p.style();
            p.inline(Inline::Tab(s));
        }
        "u" => {
            let v = param.unwrap_or(0);
            let v = if v < 0 { v + 65_536 } else { v };
            let ch = u32::try_from(v).ok().and_then(char::from_u32).unwrap_or('?');
            p.push_char(ch);
            p.skip = p.top().map_or(1, |g| g.uc);
        }
        "uc" => {
            let n = param.unwrap_or(1).clamp(0, 8) as usize;
            if let Some(g) = p.top_mut() {
                g.uc = n;
            }
        }
        "emdash" => p.push_char('\u{2014}'),
        "endash" => p.push_char('\u{2013}'),
        "bullet" => p.push_char('\u{2022}'),
        "lquote" => p.push_char('\u{2018}'),
        "rquote" => p.push_char('\u{2019}'),
        "ldblquote" => p.push_char('\u{201C}'),
        "rdblquote" => p.push_char('\u{201D}'),
        "emspace" | "enspace" | "qmspace" => p.push_char(' '),
        // Paragraph properties.
        "pard" => {
            if let Some(g) = p.top_mut() {
                g.para = ParaState::default();
            }
        }
        "ql" | "qc" | "qr" | "qj" | "qd" => {
            if let Some(g) = p.top_mut() {
                g.para.align = match word {
                    "qc" => Align::Center,
                    "qr" => Align::Right,
                    "qj" | "qd" => Align::Justify,
                    _ => Align::Left,
                };
            }
        }
        "li" => set_para(p, |s| s.left = twips(param)),
        "ri" => set_para(p, |s| s.right = twips(param)),
        "fi" => set_para(p, |s| s.first = twips(param)),
        "sb" => set_para(p, |s| s.before = twips(param).max(0.0)),
        "sa" => set_para(p, |s| s.after = twips(param).max(0.0)),
        "sl" => {
            let v = param.unwrap_or(0);
            set_para(p, |s| {
                s.line = if v == 0 {
                    LineRule::Auto(1.0)
                } else if v < 0 {
                    LineRule::Exact((v as f64 * TWIP).abs())
                } else {
                    LineRule::AtLeast(v as f64 * TWIP)
                }
            });
        }
        "slmult" => {
            if param == Some(1) {
                set_para(p, |s| {
                    if let LineRule::AtLeast(v) | LineRule::Exact(v) = s.line {
                        s.line = LineRule::Auto(v / 12.0);
                    }
                });
            }
        }
        "intbl" => set_para(p, |s| s.in_table = true),
        "pagebb" => set_para(p, |s| s.page_break_before = on),
        // Tables.
        "trowd" => {
            p.cell_edges.clear();
            p.row_borders = false;
        }
        "cellx" => {
            let e = twips(param).max(0.0);
            if p.cell_edges.len() < 64 {
                p.cell_edges.push(e);
            }
        }
        "clbrdrt" | "clbrdrb" | "clbrdrl" | "clbrdrr" | "trbrdrt" | "trbrdrb" | "trbrdrl" | "trbrdrr" | "trbrdrh" | "trbrdrv" => p.row_borders = true,
        "cell" => p.cell(),
        "row" => p.row(),
        // Character properties.
        "plain" => {
            let font = p.default_font;
            if let Some(g) = p.top_mut() {
                g.chars = CharState {
                    bold: false,
                    italic: false,
                    underline: false,
                    strike: false,
                    size: 12.0,
                    font,
                    colour: 0,
                    highlight: 0,
                    vert: 0,
                    caps: false,
                    hidden: false,
                };
            }
        }
        "b" => set_char(p, |c| c.bold = on),
        "i" => set_char(p, |c| c.italic = on),
        "ul" | "uld" | "uldb" | "ulw" | "ulth" | "uldash" => set_char(p, |c| c.underline = on),
        "ulnone" => set_char(p, |c| c.underline = false),
        "strike" | "striked" => set_char(p, |c| c.strike = on),
        "caps" | "scaps" => set_char(p, |c| c.caps = on),
        "v" => set_char(p, |c| c.hidden = on),
        "fs" => set_char(p, |c| c.size = (param.unwrap_or(24).clamp(2, 3000) as f64) / 2.0),
        "f" => set_char(p, |c| c.font = param.unwrap_or(0)),
        "cf" => set_char(p, |c| c.colour = param.unwrap_or(0).clamp(0, 10_000) as usize),
        "highlight" | "cb" => set_char(p, |c| c.highlight = param.unwrap_or(0).clamp(0, 10_000) as usize),
        "super" => set_char(p, |c| c.vert = 1),
        "sub" => set_char(p, |c| c.vert = -1),
        "nosupersub" => set_char(p, |c| c.vert = 0),
        _ => {}
    }
}

fn set_para(p: &mut Parser, f: impl FnOnce(&mut ParaState)) {
    if let Some(g) = p.top_mut() {
        f(&mut g.para);
    }
}

fn set_char(p: &mut Parser, f: impl FnOnce(&mut CharState)) {
    if let Some(g) = p.top_mut() {
        f(&mut g.chars);
    }
}

fn codepage(cp: u16) -> Option<&'static encoding_rs::Encoding> {
    Some(match cp {
        1250 => encoding_rs::WINDOWS_1250,
        1251 => encoding_rs::WINDOWS_1251,
        1252 => encoding_rs::WINDOWS_1252,
        1253 => encoding_rs::WINDOWS_1253,
        1254 => encoding_rs::WINDOWS_1254,
        1255 => encoding_rs::WINDOWS_1255,
        1256 => encoding_rs::WINDOWS_1256,
        1257 => encoding_rs::WINDOWS_1257,
        1258 => encoding_rs::WINDOWS_1258,
        874 => encoding_rs::WINDOWS_874,
        932 => encoding_rs::SHIFT_JIS,
        936 => encoding_rs::GBK,
        949 => encoding_rs::EUC_KR,
        950 => encoding_rs::BIG5,
        10000 => encoding_rs::MACINTOSH,
        65001 => encoding_rs::UTF_8,
        _ => return None,
    })
}

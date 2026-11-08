//! Word documents (.docx, ECMA-376 Part 1 §17 WordprocessingML) to flowing pages.

use std::collections::HashMap;

use roxmltree::Node;

use super::Package;
use super::draw::{BLACK, Face, Rgb, Writer, family_of};
use super::flow::{self, Align, Block, Borders, Cell, Inline, LineRule, PageSetup, Para, Row, Style, TabAlign, TabStop, Table, VAlign};
use super::xml::{attr, child, children, elements, num, on_off, parse_str, path, relationships};

/// How deep body content (tables in cells, text boxes, content controls) may nest.
const MAX_DEPTH: usize = 24;
/// Longest `basedOn` chain followed.
const MAX_STYLE_CHAIN: usize = 32;

const TWIP: f64 = 1.0 / 20.0;
const EMU: f64 = 1.0 / 12700.0;

/// A page-number field in a header or footer, replaced on each page.
pub(crate) const PAGE_MARK: char = '\u{E000}';
/// A page-count field (NUMPAGES), replaced once every page is laid out.
pub(crate) const PAGES_MARK: char = '\u{E001}';

#[derive(Clone, Debug, Default)]
struct RunProps {
    bold: Option<bool>,
    italic: Option<bool>,
    underline: Option<bool>,
    strike: Option<bool>,
    size: Option<f64>,
    color: Option<Rgb>,
    font: Option<String>,
    theme_font: Option<bool>, // Some(true) major, Some(false) minor
    caps: Option<bool>,
    vert: Option<i8>,
    highlight: Option<Option<Rgb>>,
    shade: Option<Option<Rgb>>,
    vanish: Option<bool>,
}

impl RunProps {
    fn merge(&mut self, o: &RunProps) {
        macro_rules! take {
            ($($f:ident),*) => { $( if o.$f.is_some() { self.$f = o.$f.clone(); } )* };
        }
        take!(bold, italic, underline, strike, size, color, caps, vert, highlight, shade, vanish);
        if o.font.is_some() || o.theme_font.is_some() {
            self.font = o.font.clone();
            self.theme_font = o.theme_font;
        }
    }
}

#[derive(Clone, Debug, Default)]
struct ParaProps {
    jc: Option<Align>,
    before: Option<f64>,
    after: Option<f64>,
    line: Option<LineRule>,
    left: Option<f64>,
    right: Option<f64>,
    /// First-line offset (negative for hanging).
    first: Option<f64>,
    page_break_before: Option<bool>,
    num: Option<(String, usize)>,
    shading: Option<Option<Rgb>>,
    /// Tab stops: position, kind (`left`, `right`, `center`, `decimal`, `clear` …) and leader.
    tabs: Option<Vec<(f64, String, Option<char>)>>,
    contextual: Option<bool>,
    border_bottom: Option<Option<(Rgb, f64)>>,
}

impl ParaProps {
    fn merge(&mut self, o: &ParaProps) {
        macro_rules! take {
            ($($f:ident),*) => { $( if o.$f.is_some() { self.$f = o.$f.clone(); } )* };
        }
        take!(jc, before, after, line, left, right, first, page_break_before, num, shading, contextual, border_bottom);
        if let Some(t) = &o.tabs {
            // Tab stops accumulate down the chain; `clear` stops remove inherited ones.
            let mut all = self.tabs.take().unwrap_or_default();
            for (pos, kind, leader) in t {
                all.retain(|(p, _, _)| (p - pos).abs() > 0.5);
                if kind != "clear" {
                    all.push((*pos, kind.clone(), *leader));
                }
            }
            self.tabs = Some(all);
        }
    }
}

fn twips(n: Node<'_, '_>, a: &str) -> Option<f64> {
    num(n, a).map(|v| v * TWIP)
}

fn colour_attr(n: Node<'_, '_>, a: &str) -> Option<Option<Rgb>> {
    attr(n, a).map(|v| if v.eq_ignore_ascii_case("auto") { None } else { Rgb::hex(v) })
}

fn highlight_colour(name: &str) -> Option<Rgb> {
    Some(match name {
        "yellow" => Rgb(255, 255, 0),
        "green" => Rgb(0, 255, 0),
        "cyan" => Rgb(0, 255, 255),
        "magenta" => Rgb(255, 0, 255),
        "blue" => Rgb(0, 0, 255),
        "red" => Rgb(255, 0, 0),
        "darkBlue" => Rgb(0, 0, 139),
        "darkCyan" => Rgb(0, 139, 139),
        "darkGreen" => Rgb(0, 100, 0),
        "darkMagenta" => Rgb(139, 0, 139),
        "darkRed" => Rgb(139, 0, 0),
        "darkYellow" => Rgb(128, 128, 0),
        "darkGray" => Rgb(169, 169, 169),
        "lightGray" => Rgb(211, 211, 211),
        "black" => BLACK,
        "white" => Rgb(255, 255, 255),
        _ => return None,
    })
}

fn parse_rpr(n: Option<Node<'_, '_>>) -> RunProps {
    let mut r = RunProps::default();
    let Some(n) = n else { return r };
    for c in elements(n) {
        match c.tag_name().name() {
            "b" => r.bold = Some(on_off(c)),
            "i" => r.italic = Some(on_off(c)),
            "u" => r.underline = Some(attr(c, "val") != Some("none")),
            "strike" | "dstrike" => r.strike = Some(on_off(c)),
            "sz" => r.size = num(c, "val").map(|v| (v / 2.0).clamp(1.0, 1638.0)),
            "color" => {
                if let Some(c) = colour_attr(c, "val") {
                    r.color = Some(c.unwrap_or(BLACK));
                }
            }
            "rFonts" => {
                if let Some(f) = attr(c, "ascii").or_else(|| attr(c, "hAnsi")) {
                    r.font = Some(f.to_string());
                    r.theme_font = None;
                } else if let Some(t) = attr(c, "asciiTheme").or_else(|| attr(c, "hAnsiTheme")) {
                    r.font = None;
                    r.theme_font = Some(t.starts_with("major"));
                }
            }
            "caps" | "smallCaps" => r.caps = Some(on_off(c)),
            "vertAlign" => {
                r.vert = Some(match attr(c, "val") {
                    Some("superscript") => 1,
                    Some("subscript") => -1,
                    _ => 0,
                })
            }
            "highlight" => r.highlight = Some(attr(c, "val").and_then(highlight_colour)),
            "shd" => r.shade = colour_attr(c, "fill"),
            "vanish" | "specVanish" => r.vanish = Some(on_off(c)),
            _ => {}
        }
    }
    r
}

fn border(n: Node<'_, '_>) -> Option<(Rgb, f64)> {
    let val = attr(n, "val").unwrap_or("single");
    if matches!(val, "nil" | "none") {
        return None;
    }
    let colour = colour_attr(n, "color").flatten().unwrap_or(BLACK);
    let width = num(n, "sz").map_or(0.5, |v| (v / 8.0).clamp(0.25, 6.0));
    let width = if matches!(val, "double" | "thick") { width.max(1.0) } else { width };
    Some((colour, width))
}

fn parse_ppr(n: Option<Node<'_, '_>>) -> ParaProps {
    let mut p = ParaProps::default();
    let Some(n) = n else { return p };
    for c in elements(n) {
        match c.tag_name().name() {
            "jc" => {
                p.jc = Some(match attr(c, "val") {
                    Some("center") => Align::Center,
                    Some("right" | "end") => Align::Right,
                    Some("both" | "distribute" | "lowKashida" | "mediumKashida" | "highKashida" | "thaiDistribute") => Align::Justify,
                    _ => Align::Left,
                })
            }
            "spacing" => {
                if attr(c, "beforeAutospacing").is_some_and(|v| v == "1" || v == "true") {
                    p.before = Some(14.0);
                } else if let Some(v) = twips(c, "before") {
                    p.before = Some(v.max(0.0));
                }
                if attr(c, "afterAutospacing").is_some_and(|v| v == "1" || v == "true") {
                    p.after = Some(14.0);
                } else if let Some(v) = twips(c, "after") {
                    p.after = Some(v.max(0.0));
                }
                if let Some(v) = num(c, "line") {
                    p.line = Some(match attr(c, "lineRule") {
                        Some("exact") => LineRule::Exact((v * TWIP).abs()),
                        Some("atLeast") => LineRule::AtLeast(v * TWIP),
                        _ => LineRule::Auto(v / 240.0),
                    });
                }
            }
            "ind" => {
                if let Some(v) = twips(c, "left").or_else(|| twips(c, "start")) {
                    p.left = Some(v);
                }
                if let Some(v) = twips(c, "right").or_else(|| twips(c, "end")) {
                    p.right = Some(v);
                }
                if let Some(v) = twips(c, "hanging") {
                    p.first = Some(-v);
                } else if let Some(v) = twips(c, "firstLine") {
                    p.first = Some(v);
                }
            }
            "pageBreakBefore" => p.page_break_before = Some(on_off(c)),
            "numPr" => {
                let id = child(c, "numId").and_then(|n| attr(n, "val")).map(str::to_string);
                let lvl = child(c, "ilvl").and_then(|n| num(n, "val")).map_or(0, |v| v.clamp(0.0, 8.0) as usize);
                if let Some(id) = id {
                    p.num = Some((id, lvl));
                }
            }
            "shd" => p.shading = colour_attr(c, "fill"),
            "tabs" => {
                let mut t = Vec::new();
                for tab in children(c, "tab") {
                    if let Some(pos) = twips(tab, "pos") {
                        let leader = match attr(tab, "leader") {
                            Some("dot") => Some('.'),
                            Some("hyphen") => Some('-'),
                            Some("underscore" | "heavy") => Some('_'),
                            Some("middleDot") => Some('\u{B7}'),
                            _ => None,
                        };
                        t.push((pos, attr(tab, "val").unwrap_or("left").to_string(), leader));
                    }
                }
                p.tabs = Some(t);
            }
            "contextualSpacing" => p.contextual = Some(on_off(c)),
            "pBdr" => {
                if let Some(b) = child(c, "bottom") {
                    p.border_bottom = Some(border(b));
                }
            }
            _ => {}
        }
    }
    p
}

/// Cell margins: left, top, right, bottom (each optional, to inherit).
type Margins = (Option<f64>, Option<f64>, Option<f64>, Option<f64>);

#[derive(Clone, Debug, Default)]
struct StyleDef {
    name: String,
    based_on: Option<String>,
    ppr: ParaProps,
    rpr: RunProps,
    /// Table styles: borders (top, left, bottom, right, insideH, insideV) and cell margins.
    tbl_borders: Option<[Option<(Rgb, f64)>; 6]>,
    tbl_margins: Option<Margins>,
    /// Table styles: the first row's fill, bold and colour (`tblStylePr type="firstRow"`) and
    /// banded rows' fill.
    first_row: Option<(Option<Rgb>, RunProps)>,
    band1: Option<Rgb>,
}

#[derive(Default)]
struct Styles {
    map: HashMap<String, StyleDef>,
    doc_rpr: RunProps,
    doc_ppr: ParaProps,
    default_para: Option<String>,
    default_table: Option<String>,
}

fn table_borders(n: Node<'_, '_>) -> [Option<(Rgb, f64)>; 6] {
    let side = |names: &[&str]| names.iter().find_map(|s| child(n, s)).and_then(border);
    [side(&["top"]), side(&["left", "start"]), side(&["bottom"]), side(&["right", "end"]), side(&["insideH"]), side(&["insideV"])]
}

fn cell_margins(n: Node<'_, '_>) -> Margins {
    let m = |names: &[&str]| names.iter().find_map(|s| child(n, s)).and_then(|c| twips(c, "w"));
    (m(&["left", "start"]), m(&["top"]), m(&["right", "end"]), m(&["bottom"]))
}

impl Styles {
    fn parse(text: Option<&str>) -> Styles {
        let mut s = Styles::default();
        let Some(doc) = text.and_then(parse_str) else { return s };
        let root = doc.root_element();
        if let Some(dd) = child(root, "docDefaults") {
            s.doc_rpr = parse_rpr(path(dd, &["rPrDefault", "rPr"]));
            s.doc_ppr = parse_ppr(path(dd, &["pPrDefault", "pPr"]));
        }
        for st in children(root, "style") {
            let Some(id) = attr(st, "styleId") else { continue };
            let kind = attr(st, "type").unwrap_or("paragraph").to_string();
            let default = attr(st, "default").is_some_and(|v| v == "1" || v == "true");
            if default && kind == "paragraph" {
                s.default_para = Some(id.to_string());
            }
            if default && kind == "table" {
                s.default_table = Some(id.to_string());
            }
            let tbl_pr = child(st, "tblPr");
            let mut def = StyleDef {
                name: child(st, "name").and_then(|n| attr(n, "val")).unwrap_or_default().to_string(),
                based_on: child(st, "basedOn").and_then(|n| attr(n, "val")).map(str::to_string),
                ppr: parse_ppr(child(st, "pPr")),
                rpr: parse_rpr(child(st, "rPr")),
                tbl_borders: tbl_pr.and_then(|t| child(t, "tblBorders")).map(table_borders),
                tbl_margins: tbl_pr.and_then(|t| child(t, "tblCellMar")).map(cell_margins),
                ..Default::default()
            };
            for cond in children(st, "tblStylePr") {
                let fill = path(cond, &["tcPr", "shd"]).and_then(|n| colour_attr(n, "fill")).flatten();
                match attr(cond, "type") {
                    Some("firstRow") => def.first_row = Some((fill, parse_rpr(child(cond, "rPr")))),
                    Some("band1Horz") => def.band1 = fill,
                    _ => {}
                }
            }
            s.map.insert(id.to_string(), def);
        }
        s
    }

    /// The style and its ancestors, root first.
    fn chain(&self, id: &str) -> Vec<&StyleDef> {
        let mut out = Vec::new();
        let mut seen: Vec<&str> = Vec::new();
        let mut cur = Some(id);
        while let Some(i) = cur {
            if seen.contains(&i) || seen.len() >= MAX_STYLE_CHAIN {
                break;
            }
            seen.push(i);
            let Some(def) = self.map.get(i) else { break };
            out.push(def);
            cur = def.based_on.as_deref();
        }
        out.reverse();
        out
    }

    /// Paragraph and run properties of paragraph style `id`, from the document defaults down.
    fn para(&self, id: Option<&str>) -> (ParaProps, RunProps) {
        let mut p = self.doc_ppr.clone();
        let mut r = self.doc_rpr.clone();
        let Some(id) = id else { return (p, r) };
        let chain = self.chain(id);
        let mut style_r = RunProps::default();
        let mut style_p = ParaProps::default();
        for d in &chain {
            style_p.merge(&d.ppr);
            style_r.merge(&d.rpr);
        }
        // Headings look like headings even when styles.xml doesn't say how.
        let name = chain.last().map(|d| d.name.as_str()).unwrap_or_default();
        if let Some(level) = heading_level(id, name) {
            let (size, italic) = match level {
                0 => (26.0, false),
                1 => (16.0, false),
                2 => (13.0, false),
                3 => (12.0, false),
                4 => (11.0, true),
                5 => (11.0, false),
                10 => (15.0, true),
                _ => (11.0, true),
            };
            if style_r.size.is_none() {
                style_r.size = Some(size);
                if style_r.bold.is_none() && level != 0 && level != 10 {
                    style_r.bold = Some(true);
                }
                if style_r.italic.is_none() && italic {
                    style_r.italic = Some(true);
                }
            }
            if style_p.before.is_none() && (1..=9).contains(&level) {
                style_p.before = Some(if level == 1 { 12.0 } else { 6.0 });
            }
            if style_p.after.is_none() {
                style_p.after = Some(if level == 0 { 6.0 } else { 3.0 });
            }
        }
        p.merge(&style_p);
        r.merge(&style_r);
        (p, r)
    }

    /// Run properties of character style `id` (its chain only).
    fn character(&self, id: &str) -> RunProps {
        let mut r = RunProps::default();
        for d in self.chain(id) {
            r.merge(&d.rpr);
        }
        r
    }
}

/// 1–9 for "Heading 1".."Heading 9", 0 for Title, 10 for Subtitle.
fn heading_level(id: &str, name: &str) -> Option<u8> {
    for s in [name, id] {
        let l = s.to_ascii_lowercase().replace(' ', "");
        if l == "title" {
            return Some(0);
        }
        if l == "subtitle" {
            return Some(10);
        }
        if let Some(n) = l.strip_prefix("heading")
            && let Ok(v) = n.parse::<u8>()
            && (1..=9).contains(&v)
        {
            return Some(v);
        }
    }
    None
}

// ── numbering ───────────────────────────────────────────────────────────────────────────────

#[derive(Clone, Debug, Default)]
struct Level {
    fmt: String,
    text: String,
    start: i64,
    ppr: ParaProps,
    rpr: RunProps,
    symbol_font: bool,
}

/// A list instance: its abstract list, start overrides by level and level overrides.
type NumDef = (String, HashMap<usize, i64>, HashMap<usize, Level>);

#[derive(Default)]
struct Numbering {
    abstracts: HashMap<String, Vec<Level>>,
    /// numId → (abstractNumId, start overrides by level, level overrides).
    nums: HashMap<String, NumDef>,
    counters: HashMap<String, [i64; 9]>,
}

fn parse_level(l: Node<'_, '_>) -> Level {
    let rpr = parse_rpr(child(l, "rPr"));
    let font = child(l, "rPr").and_then(|r| child(r, "rFonts")).and_then(|f| attr(f, "ascii").or_else(|| attr(f, "hAnsi"))).unwrap_or_default();
    let fl = font.to_ascii_lowercase();
    Level {
        fmt: child(l, "numFmt").and_then(|n| attr(n, "val")).unwrap_or("decimal").to_string(),
        text: child(l, "lvlText").and_then(|n| attr(n, "val")).unwrap_or_default().to_string(),
        start: child(l, "start").and_then(|n| num(n, "val")).map_or(1, |v| v.clamp(-1e9, 1e9) as i64),
        ppr: parse_ppr(child(l, "pPr")),
        rpr,
        symbol_font: fl.contains("symbol") || fl.contains("wingdings") || fl.contains("webdings"),
    }
}

impl Numbering {
    fn parse(text: Option<&str>) -> Numbering {
        let mut n = Numbering::default();
        let Some(doc) = text.and_then(parse_str) else { return n };
        let root = doc.root_element();
        for a in children(root, "abstractNum") {
            let Some(id) = attr(a, "abstractNumId") else { continue };
            let mut levels = vec![Level::default(); 9];
            for l in children(a, "lvl") {
                let i = num(l, "ilvl").map_or(0, |v| v.clamp(0.0, 8.0) as usize);
                if let Some(slot) = levels.get_mut(i) {
                    *slot = parse_level(l);
                }
            }
            n.abstracts.insert(id.to_string(), levels);
        }
        for m in children(root, "num") {
            let Some(id) = attr(m, "numId") else { continue };
            let Some(abs) = child(m, "abstractNumId").and_then(|a| attr(a, "val")) else { continue };
            let mut starts = HashMap::new();
            let mut levels = HashMap::new();
            for o in children(m, "lvlOverride") {
                let i = num(o, "ilvl").map_or(0, |v| v.clamp(0.0, 8.0) as usize);
                if let Some(s) = child(o, "startOverride").and_then(|s| num(s, "val")) {
                    starts.insert(i, s.clamp(-1e9, 1e9) as i64);
                }
                if let Some(l) = child(o, "lvl") {
                    levels.insert(i, parse_level(l));
                }
            }
            n.nums.insert(id.to_string(), (abs.to_string(), starts, levels));
        }
        n
    }

    fn level(&self, id: &str, ilvl: usize) -> Option<Level> {
        let (abs, _, over) = self.nums.get(id)?;
        if let Some(l) = over.get(&ilvl) {
            return Some(l.clone());
        }
        self.abstracts.get(abs)?.get(ilvl).cloned()
    }

    /// Advance the counter of list `id` at `ilvl` and return its label.
    fn label(&mut self, id: &str, ilvl: usize) -> Option<(String, Level)> {
        let level = self.level(id, ilvl)?;
        let starts: Vec<i64> = (0..9).map(|i| self.start(id, i)).collect();
        let counters = self.counters.entry(id.to_string()).or_insert_with(|| {
            let mut c = [0i64; 9];
            for (i, slot) in c.iter_mut().enumerate() {
                *slot = starts.get(i).copied().unwrap_or(1) - 1;
            }
            c
        });
        if let Some(c) = counters.get_mut(ilvl) {
            *c = c.saturating_add(1);
        }
        for (i, c) in counters.iter_mut().enumerate().skip(ilvl + 1) {
            *c = starts.get(i).copied().unwrap_or(1) - 1;
        }
        let values = *counters;
        if level.fmt == "none" {
            return Some((String::new(), level));
        }
        if level.fmt == "bullet" {
            let ch = level.text.chars().next().unwrap_or('\u{2022}');
            let bullet = if level.symbol_font {
                match ch {
                    'o' => "o".to_string(),
                    _ => "\u{2022}".to_string(),
                }
            } else {
                ch.to_string()
            };
            return Some((bullet, level));
        }
        let mut out = String::new();
        let mut chars = level.text.chars().peekable();
        while let Some(c) = chars.next() {
            if c == '%'
                && let Some(d) = chars.peek().and_then(|d| d.to_digit(10))
            {
                chars.next();
                let i = (d as usize).saturating_sub(1);
                let fmt = if i == ilvl { level.fmt.clone() } else { self.level(id, i).map(|l| l.fmt).unwrap_or_else(|| "decimal".into()) };
                out.push_str(&format_number(values.get(i).copied().unwrap_or(1), &fmt));
            } else {
                out.push(c);
            }
        }
        Some((out, level))
    }

    fn start(&self, id: &str, ilvl: usize) -> i64 {
        if let Some((_, starts, _)) = self.nums.get(id)
            && let Some(s) = starts.get(&ilvl)
        {
            return *s;
        }
        self.level(id, ilvl).map_or(1, |l| l.start)
    }
}

pub(crate) fn roman(mut n: i64) -> String {
    if !(1..=3999).contains(&n) {
        return n.to_string();
    }
    let table = [
        (1000, "m"),
        (900, "cm"),
        (500, "d"),
        (400, "cd"),
        (100, "c"),
        (90, "xc"),
        (50, "l"),
        (40, "xl"),
        (10, "x"),
        (9, "ix"),
        (5, "v"),
        (4, "iv"),
        (1, "i"),
    ];
    let mut s = String::new();
    for (v, r) in table {
        while n >= v {
            s.push_str(r);
            n -= v;
        }
    }
    s
}

fn letters(n: i64) -> String {
    if n < 1 {
        return n.to_string();
    }
    // Word repeats the letter: a … z, aa … zz, aaa …
    let n = n - 1;
    let ch = (b'a' + (n % 26) as u8) as char;
    std::iter::repeat_n(ch, (n / 26 + 1).min(10) as usize).collect()
}

pub(crate) fn format_number(n: i64, fmt: &str) -> String {
    match fmt {
        "lowerLetter" => letters(n),
        "upperLetter" => letters(n).to_uppercase(),
        "lowerRoman" => roman(n),
        "upperRoman" => roman(n).to_uppercase(),
        "decimalZero" => format!("{n:02}"),
        _ => n.to_string(),
    }
}

// ── the document ────────────────────────────────────────────────────────────────────────────

struct Theme {
    major: String,
    minor: String,
}

fn parse_theme(text: Option<&str>) -> Theme {
    let mut t = Theme { major: "Calibri Light".into(), minor: "Calibri".into() };
    if let Some(doc) = text.and_then(parse_str)
        && let Some(fs) = doc.descendants().find(|n| n.tag_name().name() == "fontScheme")
    {
        if let Some(f) = path(fs, &["majorFont", "latin"]).and_then(|n| attr(n, "typeface")).filter(|s| !s.is_empty()) {
            t.major = f.to_string();
        }
        if let Some(f) = path(fs, &["minorFont", "latin"]).and_then(|n| attr(n, "typeface")).filter(|s| !s.is_empty()) {
            t.minor = f.to_string();
        }
    }
    t
}

/// Inline content collected from a paragraph's runs; a page break splits a paragraph.
enum Piece {
    Inline(Inline),
    PageBreak,
}

struct Converter<'p, 'a, 'w> {
    pkg: &'p Package<'a>,
    part: String,
    rels: Vec<(String, String, String)>,
    styles: Styles,
    numbering: Numbering,
    theme: Theme,
    writer: &'w mut Writer,
    /// Width of the text column, for images wider than the page.
    column: f64,
    /// Blocks found inside runs (text boxes), added after the paragraph.
    pending: Vec<Block>,
    in_header: bool,
    /// A PAGE field in a header: 1 once its code is seen, 2 while skipping its cached result.
    page_field: u8,
    /// Footnote and endnote references met in the text: (endnote?, note id, number shown).
    note_refs: Vec<(bool, String, usize)>,
    /// The number of the note being laid out (for its `footnoteRef` mark).
    current_note: Option<usize>,
}

pub(crate) fn convert(pkg: &Package<'_>, main: &str, writer: &mut Writer) -> Result<(), String> {
    let text = pkg.xml(main)?.ok_or_else(|| pkg.damaged("no document part"))?;
    let doc = parse_str(&text).ok_or_else(|| pkg.damaged("its document XML can't be read"))?;
    let rels = relationships(&pkg.zip, main);
    let part_of = |suffix: &str| rels.iter().find(|(_, t, _)| t.ends_with(suffix)).map(|(_, _, p)| p.clone());
    let styles_text = match part_of("/styles") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let numbering_text = match part_of("/numbering") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let theme_text = match part_of("/theme") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let mut c = Converter {
        pkg,
        part: main.to_string(),
        styles: Styles::parse(styles_text.as_deref()),
        numbering: Numbering::parse(numbering_text.as_deref()),
        theme: parse_theme(theme_text.as_deref()),
        rels: rels.clone(),
        writer,
        column: 468.0,
        pending: Vec::new(),
        in_header: false,
        page_field: 0,
        note_refs: Vec::new(),
        current_note: None,
    };
    let body = child(doc.root_element(), "body").ok_or_else(|| pkg.damaged("the document has no body"))?;
    // Sections: content up to a paragraph carrying `w:sectPr` uses that section's page.
    let mut sections: Vec<(Vec<Block>, Option<Node<'_, '_>>)> = Vec::new();
    let mut blocks = Vec::new();
    let body_sect = child(body, "sectPr");
    c.column = c.setup(body_sect).content_width();
    for n in elements(body) {
        let name = n.tag_name().name();
        if name == "sectPr" {
            continue;
        }
        c.block(n, &mut blocks, 0);
        if name == "p"
            && let Some(sp) = path(n, &["pPr", "sectPr"])
        {
            sections.push((std::mem::take(&mut blocks), Some(sp)));
        }
    }
    // Footnotes and endnotes follow the text, under a short rule, numbered as referenced.
    let refs = std::mem::take(&mut c.note_refs);
    if !refs.is_empty() {
        let width = c.column;
        blocks.push(Block::Para(Para {
            right: width * 0.67,
            before: 12.0,
            after: 4.0,
            mark_size: 2.0,
            border_bottom: Some((BLACK, 0.5)),
            ..Default::default()
        }));
        for (endnotes, suffix, tag) in [(false, "/footnotes", "footnote"), (true, "/endnotes", "endnote")] {
            let Some(part) = part_of(suffix) else { continue };
            let Some(text) = pkg.xml(&part)? else { continue };
            let Some(notes) = parse_str(&text) else { continue };
            for (_, id, n) in refs.iter().filter(|(e, _, _)| *e == endnotes) {
                let Some(note) = children(notes.root_element(), tag).find(|f| attr(*f, "id") == Some(id.as_str())) else { continue };
                c.current_note = Some(*n);
                c.with_part(&part, |c| {
                    for el in elements(note) {
                        c.block(el, &mut blocks, 1);
                    }
                });
            }
        }
        c.current_note = None;
    }
    sections.push((blocks, body_sect));
    let mut pages = flow::Pages::new();
    let max = super::draw::MAX_PAGES;
    let mut previous: Option<PageSetup> = None;
    for (blocks, sect) in sections {
        let mut setup = c.setup(sect);
        // A section without header or footer references keeps the previous section's.
        if let Some(prev) = &previous {
            let has = |kind: &str| sect.is_some_and(|s| children(s, kind).next().is_some());
            if !has("headerReference") {
                setup.header = prev.header.clone();
                setup.first_header = prev.first_header.clone();
                setup.top = setup.top.max(prev.top);
            }
            if !has("footerReference") {
                setup.footer = prev.footer.clone();
                setup.first_footer = prev.first_footer.clone();
                setup.bottom = setup.bottom.max(prev.bottom);
            }
        }
        c.column = setup.content_width();
        let items = flow::layout_blocks(&blocks, setup.content_width(), 0);
        let first = pages.len();
        flow::paginate(items, &setup, &mut pages, max + 1);
        // Page numbers in headers and footers.
        for (i, (_, _, ops)) in pages.iter_mut().enumerate().skip(first) {
            number_pages(ops, i + 1);
        }
        if pages.len() > max {
            return Err(format!("the converted document would have more than {max} pages"));
        }
        previous = Some(setup);
    }
    let total = pages.len();
    for (_, _, ops) in &mut pages {
        for op in ops.iter_mut() {
            if let super::draw::Op::Text { text, .. } = op
                && text.contains(PAGES_MARK)
            {
                *text = text.replace(PAGES_MARK, &total.to_string());
            }
        }
    }
    for (w, h, ops) in pages {
        c.writer.page(w, h, &ops).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// The placeholder for a PAGE or NUMPAGES field code, which change from page to page.
fn page_field_mark(instr: &str) -> Option<char> {
    match instr.split_whitespace().next().map(str::to_ascii_uppercase).as_deref() {
        Some("PAGE") => Some(PAGE_MARK),
        Some("NUMPAGES" | "SECTIONPAGES") => Some(PAGES_MARK),
        _ => None,
    }
}

fn number_pages(ops: &mut [super::draw::Op], page: usize) {
    for op in ops {
        if let super::draw::Op::Text { text, .. } = op
            && text.contains(PAGE_MARK)
        {
            *text = text.replace(PAGE_MARK, &page.to_string());
        }
    }
}

impl Converter<'_, '_, '_> {
    fn rel_target(&self, id: &str) -> Option<String> {
        self.rels.iter().find(|(i, _, _)| i == id).map(|(_, _, p)| p.clone())
    }

    fn setup(&mut self, sect: Option<Node<'_, '_>>) -> PageSetup {
        let mut s = PageSetup::default();
        let Some(sect) = sect else { return s };
        if let Some(sz) = child(sect, "pgSz") {
            let w = twips(sz, "w").filter(|v| *v >= 72.0 && *v <= 14_400.0);
            let h = twips(sz, "h").filter(|v| *v >= 72.0 && *v <= 14_400.0);
            if let (Some(w), Some(h)) = (w, h) {
                s.w = w;
                s.h = h;
            }
        }
        let (mut header_dist, mut footer_dist) = (36.0, 36.0);
        if let Some(m) = child(sect, "pgMar") {
            let get = |a: &str, d: f64| twips(m, a).map_or(d, |v| v.abs().min(s.h / 2.5));
            s.top = get("top", 72.0);
            s.bottom = get("bottom", 72.0);
            s.left = twips(m, "left").map_or(72.0, |v| v.abs().min(s.w / 2.5));
            s.right = twips(m, "right").map_or(72.0, |v| v.abs().min(s.w / 2.5));
            header_dist = get("header", 36.0);
            footer_dist = get("footer", 36.0);
        }
        let width = s.content_width();
        // A different first page (`titlePg`): its own header and footer, or none.
        let title_page = child(sect, "titlePg").is_some_and(on_off);
        for (kind, is_header, dist) in [("headerReference", true, header_dist), ("footerReference", false, footer_dist)] {
            let default = self.header_footer(sect, kind, "default", width).map(|b| (b, dist));
            let first = title_page.then(|| self.header_footer(sect, kind, "first", width).map(|b| (b, dist)));
            // A header taller than the top margin pushes the body down, as in Word.
            let tallest = [default.as_ref(), first.as_ref().and_then(Option::as_ref)]
                .into_iter()
                .flatten()
                .map(|(b, _)| b.iter().map(|x| x.h).sum::<f64>())
                .fold(0.0, f64::max);
            if tallest > 0.0 {
                if is_header {
                    s.top = s.top.max(dist + tallest + 4.0).min(s.h / 2.5);
                } else {
                    s.bottom = s.bottom.max(dist + tallest + 4.0).min(s.h / 2.5);
                }
            }
            if is_header {
                s.header = default;
                s.first_header = first;
            } else {
                s.footer = default;
                s.first_footer = first;
            }
        }
        s
    }

    /// The laid-out header or footer (`kind`: headerReference or footerReference) of type `ty`.
    fn header_footer(&mut self, sect: Node<'_, '_>, kind: &str, ty: &str, width: f64) -> Option<Vec<flow::VBox>> {
        let reference = children(sect, kind).find(|r| attr(*r, "type").unwrap_or("default") == ty)?;
        let part = self.rel_target(attr(reference, "id")?)?;
        let text = self.pkg.xml(&part).ok()??;
        let doc = parse_str(&text)?;
        let blocks = self.with_part(&part, |c| {
            c.in_header = true;
            let mut blocks = Vec::new();
            for n in elements(doc.root_element()) {
                c.block(n, &mut blocks, 1);
            }
            c.in_header = false;
            c.page_field = 0;
            blocks
        });
        let boxes = flow::header_boxes(&blocks, width);
        (!boxes.is_empty()).then_some(boxes)
    }

    /// Run `f` with relationships resolved against `part` (a header, footer or other part).
    fn with_part<T>(&mut self, part: &str, f: impl FnOnce(&mut Self) -> T) -> T {
        let rels = relationships(&self.pkg.zip, part);
        let old_rels = std::mem::replace(&mut self.rels, rels);
        let old_part = std::mem::replace(&mut self.part, part.to_string());
        let out = f(self);
        self.rels = old_rels;
        self.part = old_part;
        out
    }

    /// One body-level element.
    fn block(&mut self, n: Node<'_, '_>, out: &mut Vec<Block>, depth: usize) {
        if depth > MAX_DEPTH {
            return;
        }
        match n.tag_name().name() {
            "p" => self.paragraph(n, out, depth),
            "tbl" => {
                if let Some(t) = self.table(n, depth) {
                    out.push(Block::Table(t));
                }
            }
            "sdt" => {
                if let Some(content) = child(n, "sdtContent") {
                    for c in elements(content) {
                        self.block(c, out, depth + 1);
                    }
                }
            }
            "customXml" | "ins" | "moveTo" | "smartTag" | "sdtContent" => {
                for c in elements(n) {
                    self.block(c, out, depth + 1);
                }
            }
            "AlternateContent" => {
                if let Some(branch) = child(n, "Choice").or_else(|| child(n, "Fallback")) {
                    for c in elements(branch) {
                        self.block(c, out, depth + 1);
                    }
                }
            }
            _ => {}
        }
    }

    fn style_of(&self, rpr: &RunProps) -> Style {
        let font = match (&rpr.font, rpr.theme_font) {
            (Some(f), _) => f.as_str(),
            (None, Some(true)) => self.theme.major.as_str(),
            _ => self.theme.minor.as_str(),
        };
        let mut size = rpr.size.unwrap_or(11.0);
        let mut shift = 0.0;
        match rpr.vert {
            Some(1) => {
                shift = size * 0.33;
                size *= 0.65;
            }
            Some(-1) => {
                shift = -size * 0.14;
                size *= 0.65;
            }
            _ => {}
        }
        Style {
            face: Face { family: family_of(font), bold: rpr.bold.unwrap_or(false), italic: rpr.italic.unwrap_or(false) },
            size,
            color: rpr.color.unwrap_or(BLACK),
            underline: rpr.underline.unwrap_or(false),
            strike: rpr.strike.unwrap_or(false),
            highlight: rpr.highlight.flatten().or(rpr.shade.flatten()),
            shift,
        }
    }

    fn paragraph(&mut self, p: Node<'_, '_>, out: &mut Vec<Block>, depth: usize) {
        let ppr_node = child(p, "pPr");
        let style_id =
            ppr_node.and_then(|n| child(n, "pStyle")).and_then(|n| attr(n, "val")).map(str::to_string).or_else(|| self.styles.default_para.clone());
        let (mut pp, para_rpr) = self.styles.para(style_id.as_deref());
        let direct = parse_ppr(ppr_node);
        let num = direct.num.clone().or_else(|| pp.num.clone());
        let mut label = None;
        if let Some((id, ilvl)) = num.filter(|(id, _)| id != "0")
            && let Some((text, level)) = self.numbering.label(&id, ilvl)
        {
            pp.merge(&level.ppr);
            let mut lr = para_rpr.clone();
            lr.merge(&level.rpr);
            // Bullets are drawn in the text face (the symbol fonts aren't available).
            if level.symbol_font {
                lr.font = para_rpr.font.clone();
                lr.theme_font = para_rpr.theme_font;
            }
            lr.underline = Some(false);
            if !text.is_empty() {
                label = Some((text, self.style_of(&lr)));
            }
        }
        pp.merge(&direct);
        // The paragraph mark's run properties size an empty paragraph.
        let mut mark = para_rpr.clone();
        mark.merge(&parse_rpr(ppr_node.and_then(|n| child(n, "rPr"))));
        let mark_size = self.style_of(&mark).size;
        let mut pieces = Vec::new();
        self.runs(p, &para_rpr, &mut pieces, depth);
        // Split at page breaks.
        let mut segments: Vec<Vec<Inline>> = vec![Vec::new()];
        for piece in pieces {
            match piece {
                Piece::Inline(i) => {
                    if let Some(s) = segments.last_mut() {
                        s.push(i);
                    }
                }
                Piece::PageBreak => segments.push(Vec::new()),
            }
        }
        let tabs: Vec<TabStop> = pp
            .tabs
            .as_ref()
            .map(|t| {
                t.iter()
                    .filter(|(_, kind, _)| kind != "bar")
                    .map(|(pos, kind, leader)| TabStop {
                        pos: *pos,
                        align: match kind.as_str() {
                            "right" | "end" | "decimal" => TabAlign::Right,
                            "center" => TabAlign::Center,
                            _ => TabAlign::Left,
                        },
                        leader: *leader,
                    })
                    .collect()
            })
            .unwrap_or_default();
        let base = Para {
            inlines: Vec::new(),
            align: pp.jc.unwrap_or(Align::Left),
            left: pp.left.unwrap_or(0.0),
            right: pp.right.unwrap_or(0.0),
            first: pp.first.unwrap_or(0.0),
            before: pp.before.unwrap_or(0.0),
            after: pp.after.unwrap_or(0.0),
            line: pp.line.unwrap_or(LineRule::Auto(1.0)),
            page_break_before: pp.page_break_before.unwrap_or(false),
            label: None,
            shading: pp.shading.flatten(),
            tabs,
            border_bottom: pp.border_bottom.flatten(),
            style_id: style_id.unwrap_or_default(),
            contextual: pp.contextual.unwrap_or(false),
            mark_size,
        };
        let count = segments.len();
        for (i, inlines) in segments.into_iter().enumerate() {
            if i > 0 {
                out.push(Block::PageBreak);
                // Nothing after the break: the rest of the paragraph is empty.
                if inlines.is_empty() && i + 1 == count {
                    continue;
                }
            }
            let mut para = base.clone();
            para.inlines = inlines;
            if i == 0 {
                para.label = label.clone();
            } else {
                para.page_break_before = false;
                para.before = 0.0;
                para.first = 0.0;
            }
            if i + 1 < count {
                para.after = 0.0;
            }
            out.push(Block::Para(para));
        }
        out.append(&mut self.pending);
    }

    fn runs(&mut self, n: Node<'_, '_>, para_rpr: &RunProps, out: &mut Vec<Piece>, depth: usize) {
        if depth > MAX_DEPTH {
            return;
        }
        for c in elements(n) {
            match c.tag_name().name() {
                "r" => self.run(c, para_rpr, out, depth),
                "hyperlink" | "smartTag" | "ins" | "moveTo" | "customXml" | "bdo" | "dir" | "sdtContent" => self.runs(c, para_rpr, out, depth + 1),
                "fldSimple" => {
                    let mark = page_field_mark(attr(c, "instr").unwrap_or_default());
                    if let Some(mark) = mark.filter(|_| self.in_header) {
                        let style = self.style_of(para_rpr);
                        let rstyle = c
                            .descendants()
                            .find(|d| d.tag_name().name() == "r")
                            .map(|r| self.run_props(r, para_rpr))
                            .map_or(style, |rp| self.style_of(&rp));
                        out.push(Piece::Inline(Inline::Text(mark.to_string(), rstyle)));
                    } else {
                        self.runs(c, para_rpr, out, depth + 1);
                    }
                }
                "sdt" => {
                    if let Some(content) = child(c, "sdtContent") {
                        self.runs(content, para_rpr, out, depth + 1);
                    }
                }
                "AlternateContent" => {
                    if let Some(branch) = child(c, "Choice").or_else(|| child(c, "Fallback")) {
                        self.runs(branch, para_rpr, out, depth + 1);
                    }
                }
                "oMath" | "oMathPara" => {
                    let text: String = c.descendants().filter(|d| d.tag_name().name() == "t").filter_map(|d| d.text()).collect();
                    out.push(Piece::Inline(Inline::Text(text, self.style_of(para_rpr))));
                }
                _ => {}
            }
        }
    }

    fn run_props(&self, r: Node<'_, '_>, para_rpr: &RunProps) -> RunProps {
        let rpr_node = child(r, "rPr");
        let mut rp = para_rpr.clone();
        if let Some(id) = rpr_node.and_then(|n| child(n, "rStyle")).and_then(|n| attr(n, "val")) {
            rp.merge(&self.styles.character(id));
        }
        rp.merge(&parse_rpr(rpr_node));
        rp
    }

    fn run(&mut self, r: Node<'_, '_>, para_rpr: &RunProps, out: &mut Vec<Piece>, depth: usize) {
        let rp = self.run_props(r, para_rpr);
        if rp.vanish == Some(true) {
            return;
        }
        let style = self.style_of(&rp);
        let caps = rp.caps == Some(true);
        for c in elements(r) {
            match c.tag_name().name() {
                "t" => {
                    if self.page_field == 2 {
                        continue;
                    }
                    let t = c.text().unwrap_or_default();
                    let t = if caps { t.to_uppercase() } else { t.to_string() };
                    out.push(Piece::Inline(Inline::Text(t, style.clone())));
                }
                "tab" | "ptab" => out.push(Piece::Inline(Inline::Tab(style.clone()))),
                "br" => match attr(c, "type") {
                    Some("page") => out.push(Piece::PageBreak),
                    Some("column") => out.push(Piece::Inline(Inline::Break)),
                    _ => out.push(Piece::Inline(Inline::Break)),
                },
                "cr" => out.push(Piece::Inline(Inline::Break)),
                "noBreakHyphen" => out.push(Piece::Inline(Inline::Text("-".into(), style.clone()))),
                "sym" => {
                    let ch = attr(c, "char").and_then(|h| u32::from_str_radix(h, 16).ok()).and_then(char::from_u32).unwrap_or('\u{2022}');
                    out.push(Piece::Inline(Inline::Text(ch.to_string(), style.clone())));
                }
                "instrText" => {
                    if let Some(mark) = page_field_mark(c.text().unwrap_or_default()).filter(|_| self.in_header) {
                        out.push(Piece::Inline(Inline::Text(mark.to_string(), style.clone())));
                        self.page_field = 1;
                    }
                }
                "fldChar" => match attr(c, "fldCharType") {
                    Some("separate") if self.page_field == 1 => self.page_field = 2,
                    Some("end") => self.page_field = 0,
                    _ => {}
                },
                "footnoteReference" | "endnoteReference" => {
                    if let Some(id) = attr(c, "id") {
                        let n = self.note_refs.len() + 1;
                        self.note_refs.push((c.tag_name().name() == "endnoteReference", id.to_string(), n));
                        let mut sup = rp.clone();
                        sup.vert = Some(1);
                        out.push(Piece::Inline(Inline::Text(n.to_string(), self.style_of(&sup))));
                    }
                }
                "footnoteRef" | "endnoteRef" => {
                    if let Some(n) = self.current_note {
                        let mut sup = rp.clone();
                        sup.vert = Some(1);
                        out.push(Piece::Inline(Inline::Text(format!("{n} "), self.style_of(&sup))));
                    }
                }
                "drawing" => self.drawing(c, out, depth),
                "pict" | "object" => self.vml(c, out, depth),
                "AlternateContent" => {
                    if let Some(branch) = child(c, "Choice").or_else(|| child(c, "Fallback")) {
                        let mut tmp = Vec::new();
                        for b in elements(branch) {
                            match b.tag_name().name() {
                                "drawing" => self.drawing(b, &mut tmp, depth),
                                "pict" | "object" => self.vml(b, &mut tmp, depth),
                                _ => {}
                            }
                        }
                        out.extend(tmp);
                    }
                }
                _ => {}
            }
        }
    }

    fn image(&mut self, rid: &str, w: f64, h: f64) -> Option<Inline> {
        let part = self.rel_target(rid)?;
        let bytes = self.pkg.bytes(&part)?;
        let (id, natural) = self.writer.image(&part, &bytes)?;
        let (mut w, mut h) = if w > 0.0 && h > 0.0 && w.is_finite() && h.is_finite() { (w, h) } else { natural };
        // Keep within the page.
        let max_w = self.column.max(36.0) * 1.5;
        if w > max_w {
            h *= max_w / w;
            w = max_w;
        }
        if h > 1200.0 {
            w *= 1200.0 / h;
            h = 1200.0;
        }
        Some(Inline::Image { id, w: w.max(1.0), h: h.max(1.0) })
    }

    fn drawing(&mut self, d: Node<'_, '_>, out: &mut Vec<Piece>, depth: usize) {
        for holder in elements(d) {
            let extent = child(holder, "extent");
            let w = extent.and_then(|e| num(e, "cx")).map_or(0.0, |v| v * EMU);
            let h = extent.and_then(|e| num(e, "cy")).map_or(0.0, |v| v * EMU);
            // Pictures: the first blip; a text box: its paragraphs, after this one.
            if let Some(blip) = holder.descendants().find(|n| n.tag_name().name() == "blip")
                && let Some(rid) = attr(blip, "embed")
            {
                if let Some(img) = self.image(rid, w, h) {
                    out.push(Piece::Inline(img));
                }
                continue;
            }
            if let Some(tb) = holder.descendants().find(|n| n.tag_name().name() == "txbxContent") {
                let mut blocks = Vec::new();
                for n in elements(tb) {
                    self.block(n, &mut blocks, depth + 1);
                }
                self.pending.extend(blocks);
            }
        }
    }

    fn vml(&mut self, v: Node<'_, '_>, out: &mut Vec<Piece>, depth: usize) {
        if let Some(img) = v.descendants().find(|n| n.tag_name().name() == "imagedata")
            && let Some(rid) = attr(img, "id")
        {
            // Size from the shape's CSS style (`width:120pt;height:80pt`).
            let style = v.descendants().find_map(|n| if n.tag_name().name() == "shape" { attr(n, "style") } else { None }).unwrap_or_default();
            let dim = |key: &str| {
                style.split(';').find_map(|kv| {
                    let (k, val) = kv.split_once(':')?;
                    if k.trim() != key {
                        return None;
                    }
                    let val = val.trim();
                    let n = super::xml::parse_num(val)?;
                    Some(if val.ends_with("in") {
                        n * 72.0
                    } else if val.ends_with("cm") {
                        n * 72.0 / 2.54
                    } else if val.ends_with("mm") {
                        n * 72.0 / 25.4
                    } else if val.ends_with("px") {
                        n * 0.75
                    } else {
                        n
                    })
                })
            };
            if let Some(i) = self.image(rid, dim("width").unwrap_or(0.0), dim("height").unwrap_or(0.0)) {
                out.push(Piece::Inline(i));
            }
            return;
        }
        if let Some(tb) = v.descendants().find(|n| n.tag_name().name() == "txbxContent") {
            let mut blocks = Vec::new();
            for n in elements(tb) {
                self.block(n, &mut blocks, depth + 1);
            }
            self.pending.extend(blocks);
        }
    }

    fn table(&mut self, tbl: Node<'_, '_>, depth: usize) -> Option<Table> {
        let pr = child(tbl, "tblPr");
        let style_id = pr.and_then(|p| child(p, "tblStyle")).and_then(|s| attr(s, "val")).map(str::to_string);
        let style_known = style_id.as_deref().is_some_and(|id| self.styles.map.contains_key(id));
        let chain_id = style_id.clone().or_else(|| self.styles.default_table.clone());
        let mut borders: Option<[Option<(Rgb, f64)>; 6]> = None;
        let mut margins = (Some(5.4), Some(0.0), Some(5.4), Some(0.0));
        let mut first_row: Option<(Option<Rgb>, RunProps)> = None;
        let mut band1 = None;
        if let Some(id) = &chain_id {
            for d in self.styles.chain(id) {
                if let Some(b) = d.tbl_borders {
                    borders = Some(b);
                }
                if let Some(m) = d.tbl_margins {
                    margins = (m.0.or(margins.0), m.1.or(margins.1), m.2.or(margins.2), m.3.or(margins.3));
                }
                if d.first_row.is_some() {
                    first_row = d.first_row.clone();
                }
                if d.band1.is_some() {
                    band1 = d.band1;
                }
            }
        }
        if style_id.is_some() && !style_known {
            // A table style this file doesn't define (a built-in grid): single borders.
            let single = Some((BLACK, 0.5));
            borders = Some([single; 6]);
        }
        if let Some(b) = pr.and_then(|p| child(p, "tblBorders")) {
            let direct = table_borders(b);
            let mut all = borders.unwrap_or([None; 6]);
            for (i, side) in ["top", "left", "bottom", "right", "insideH", "insideV"].iter().enumerate() {
                let present = child(b, side).is_some() || (i == 1 && child(b, "start").is_some()) || (i == 3 && child(b, "end").is_some());
                if present && let (Some(slot), Some(v)) = (all.get_mut(i), direct.get(i)) {
                    *slot = *v;
                }
            }
            borders = Some(all);
        }
        if let Some(m) = pr.and_then(|p| child(p, "tblCellMar")).map(cell_margins) {
            margins = (m.0.or(margins.0), m.1.or(margins.1), m.2.or(margins.2), m.3.or(margins.3));
        }
        // tblLook: which conditional formats apply (first row and banded rows by default).
        let look = pr.and_then(|p| child(p, "tblLook"));
        let look_first = look.is_none_or(|l| {
            attr(l, "firstRow").map_or_else(
                || attr(l, "val").and_then(|v| u32::from_str_radix(v, 16).ok()).is_none_or(|v| v & 0x20 != 0),
                |v| v == "1" || v == "true",
            )
        });
        let look_band = look.is_none_or(|l| {
            attr(l, "noHBand").map_or_else(
                || attr(l, "val").and_then(|v| u32::from_str_radix(v, 16).ok()).is_none_or(|v| v & 0x200 == 0),
                |v| v != "1" && v != "true",
            )
        });
        let indent = pr.and_then(|p| child(p, "tblInd")).and_then(|i| twips(i, "w")).unwrap_or(0.0);
        let align = match pr.and_then(|p| child(p, "jc")).and_then(|j| attr(j, "val")) {
            Some("center") => Align::Center,
            Some("right" | "end") => Align::Right,
            _ => Align::Left,
        };
        let cols: Vec<f64> =
            child(tbl, "tblGrid").map(|g| children(g, "gridCol").map(|c| twips(c, "w").unwrap_or(0.0)).collect()).unwrap_or_default();
        let mut trs: Vec<Node<'_, '_>> = Vec::new();
        collect_rows(tbl, &mut trs, 0);
        let nrows = trs.len();
        let ncols = cols.len();
        let border_sides = borders.unwrap_or([None; 6]);
        let mut rows = Vec::with_capacity(nrows);
        for (ri, tr) in trs.iter().enumerate() {
            let trpr = child(*tr, "trPr");
            let height = trpr.and_then(|t| child(t, "trHeight"));
            let (h, exact) = match height {
                Some(hn) => (twips(hn, "val").unwrap_or(0.0).abs(), attr(hn, "hRule") == Some("exact")),
                None => (0.0, false),
            };
            let mut row = Row { cells: Vec::new(), height: h, exact };
            let mut grid = 0usize;
            if let Some(gb) = trpr.and_then(|t| child(t, "gridBefore")).and_then(|g| num(g, "val")) {
                let span = gb.clamp(0.0, 64.0) as usize;
                if span > 0 {
                    row.cells.push(Cell { span, ..Default::default() });
                    grid += span;
                }
            }
            let mut tcs = Vec::new();
            collect_cells(*tr, &mut tcs, 0);
            let total_spans: usize = tcs.iter().map(|tc| span_of(*tc)).sum::<usize>() + grid;
            let width_cols = ncols.max(total_spans);
            for tc in tcs {
                let tcpr = child(tc, "tcPr");
                let span = span_of(tc);
                let vmerge = tcpr.and_then(|p| child(p, "vMerge"));
                let merge_continue = vmerge.is_some_and(|v| attr(v, "val").is_none_or(|x| x == "continue"));
                let mut blocks = Vec::new();
                if !merge_continue {
                    for n in elements(tc) {
                        if n.tag_name().name() != "tcPr" {
                            self.block(n, &mut blocks, depth + 1);
                        }
                    }
                }
                let mut fill = tcpr.and_then(|p| child(p, "shd")).and_then(|s| colour_attr(s, "fill")).flatten();
                let is_first = ri == 0 && look_first;
                if fill.is_none() {
                    if is_first {
                        fill = first_row.as_ref().and_then(|(f, _)| *f);
                    } else if look_band && band1.is_some() {
                        let body_index = if look_first && first_row.is_some() { ri.saturating_sub(1) } else { ri };
                        if body_index % 2 == 0 {
                            fill = band1;
                        }
                    }
                }
                if is_first && let Some((_, rp)) = &first_row {
                    apply_run_props(&mut blocks, rp, self);
                }
                let last_col = grid + span >= width_cols;
                let mut b: Borders = [
                    if ri == 0 { border_sides[0] } else { border_sides[4] },
                    if last_col { border_sides[3] } else { border_sides[5] },
                    if ri + 1 == nrows { border_sides[2] } else { border_sides[4] },
                    if grid == 0 { border_sides[1] } else { border_sides[5] },
                ];
                if let Some(tb) = tcpr.and_then(|p| child(p, "tcBorders")) {
                    for (i, names) in [&["top"][..], &["right", "end"][..], &["bottom"][..], &["left", "start"][..]].iter().enumerate() {
                        if let Some(n) = names.iter().find_map(|s| child(tb, s))
                            && let Some(slot) = b.get_mut(i)
                        {
                            *slot = border(n);
                        }
                    }
                }
                let mut m = margins;
                if let Some(cm) = tcpr.and_then(|p| child(p, "tcMar")).map(cell_margins) {
                    m = (cm.0.or(m.0), cm.1.or(m.1), cm.2.or(m.2), cm.3.or(m.3));
                }
                let valign = match tcpr.and_then(|p| child(p, "vAlign")).and_then(|v| attr(v, "val")) {
                    Some("center") => VAlign::Center,
                    Some("bottom") => VAlign::Bottom,
                    _ => VAlign::Top,
                };
                row.cells.push(Cell {
                    span,
                    blocks,
                    fill,
                    borders: b,
                    merge_continue,
                    margins: (m.0.unwrap_or(5.4), m.1.unwrap_or(0.0), m.2.unwrap_or(5.4), m.3.unwrap_or(0.0)),
                    valign,
                });
                grid += span;
            }
            rows.push(row);
        }
        if rows.is_empty() {
            return None;
        }
        Some(Table { cols, rows, indent, align })
    }
}

/// Make the runs of a table's first row bold and coloured as its style says.
fn apply_run_props(blocks: &mut [Block], rp: &RunProps, c: &Converter<'_, '_, '_>) {
    for b in blocks {
        if let Block::Para(p) = b {
            for i in &mut p.inlines {
                if let Inline::Text(_, s) = i {
                    if let Some(bold) = rp.bold {
                        s.face.bold = bold;
                    }
                    if let Some(col) = rp.color {
                        s.color = col;
                    }
                    if let Some(sz) = rp.size {
                        s.size = sz;
                    }
                    if rp.font.is_some() || rp.theme_font.is_some() {
                        s.face.family = c.style_of(rp).face.family;
                    }
                }
            }
        }
    }
}

fn span_of(tc: Node<'_, '_>) -> usize {
    child(tc, "tcPr").and_then(|p| child(p, "gridSpan")).and_then(|g| num(g, "val")).map_or(1, |v| v.clamp(1.0, 64.0) as usize)
}

fn collect_rows<'a, 'i>(n: Node<'a, 'i>, out: &mut Vec<Node<'a, 'i>>, depth: usize) {
    if depth > 8 {
        return;
    }
    for c in elements(n) {
        match c.tag_name().name() {
            "tr" => out.push(c),
            "sdt" | "sdtContent" | "customXml" | "ins" => collect_rows(c, out, depth + 1),
            _ => {}
        }
    }
}

fn collect_cells<'a, 'i>(n: Node<'a, 'i>, out: &mut Vec<Node<'a, 'i>>, depth: usize) {
    if depth > 8 {
        return;
    }
    for c in elements(n) {
        match c.tag_name().name() {
            "tc" => out.push(c),
            "sdt" | "sdtContent" | "customXml" | "ins" => collect_cells(c, out, depth + 1),
            _ => {}
        }
    }
}

//! Drawing for converted Office files: the standard-14 faces and their exact widths, colours,
//! drawing operations in top-down page coordinates, and the writer that turns pages of
//! operations into a PDF.
//!
//! Text is set in the standard fonts (Helvetica, Times, Courier and their bold and italic faces)
//! in WinAnsiEncoding, so only Western European characters can be drawn: any other character
//! is shown as `?`. Documents keep their layout, but text in other scripts is not readable.

use std::sync::OnceLock;

use hayro_interpret::font::StandardFont;
use pdfcraft_cos::{Dict, Document, ObjRef, Object};

use crate::{CreateError, add_page};

/// Most pages one converted file may produce.
pub(crate) const MAX_PAGES: usize = 5_000;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum Family {
    #[default]
    Sans,
    Serif,
    Mono,
}

/// A standard-14 face.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Face {
    pub family: Family,
    pub bold: bool,
    pub italic: bool,
}

impl Face {
    fn index(self) -> usize {
        let f = match self.family {
            Family::Sans => 0,
            Family::Serif => 4,
            Family::Mono => 8,
        };
        f + usize::from(self.bold) * 2 + usize::from(self.italic)
    }

    fn of_index(i: usize) -> Face {
        let family = match i / 4 {
            0 => Family::Sans,
            1 => Family::Serif,
            _ => Family::Mono,
        };
        Face { family, bold: i & 2 != 0, italic: i & 1 != 0 }
    }

    fn standard(self) -> StandardFont {
        use StandardFont as S;
        match (self.family, self.bold, self.italic) {
            (Family::Sans, false, false) => S::Helvetica,
            (Family::Sans, true, false) => S::HelveticaBold,
            (Family::Sans, false, true) => S::HelveticaOblique,
            (Family::Sans, true, true) => S::HelveticaBoldOblique,
            (Family::Serif, false, false) => S::TimesRoman,
            (Family::Serif, true, false) => S::TimesBold,
            (Family::Serif, false, true) => S::TimesItalic,
            (Family::Serif, true, true) => S::TimesBoldItalic,
            (Family::Mono, false, false) => S::Courier,
            (Family::Mono, true, false) => S::CourierBold,
            (Family::Mono, false, true) => S::CourierOblique,
            (Family::Mono, true, true) => S::CourierBoldOblique,
        }
    }

    fn base_font(self) -> &'static str {
        match (self.family, self.bold, self.italic) {
            (Family::Sans, false, false) => "Helvetica",
            (Family::Sans, true, false) => "Helvetica-Bold",
            (Family::Sans, false, true) => "Helvetica-Oblique",
            (Family::Sans, true, true) => "Helvetica-BoldOblique",
            (Family::Serif, false, false) => "Times-Roman",
            (Family::Serif, true, false) => "Times-Bold",
            (Family::Serif, false, true) => "Times-Italic",
            (Family::Serif, true, true) => "Times-BoldItalic",
            (Family::Mono, false, false) => "Courier",
            (Family::Mono, true, false) => "Courier-Bold",
            (Family::Mono, false, true) => "Courier-Oblique",
            (Family::Mono, true, true) => "Courier-BoldOblique",
        }
    }
}

/// The standard family closest to a document's font family name: serif faces become Times,
/// monospaced ones Courier, everything else Helvetica.
pub(crate) fn family_of(name: &str) -> Family {
    let n = name.to_ascii_lowercase();
    const MONO: [&str; 12] = [
        "courier",
        "consolas",
        "mono",
        "menlo",
        "monaco",
        "lucida console",
        "andale",
        "fixedsys",
        "terminal",
        "inconsolata",
        "source code",
        "fira code",
    ];
    const SERIF: [&str; 22] = [
        "times",
        "georgia",
        "cambria",
        "garamond",
        "book antiqua",
        "palatino",
        "serif",
        "minion",
        "baskerville",
        "bodoni",
        "century",
        "constantia",
        "didot",
        "caslon",
        "bookman",
        "rockwell",
        "liberation serif",
        "dejavu serif",
        "noto serif",
        "charter",
        "perpetua",
        "goudy",
    ];
    if MONO.iter().any(|m| n.contains(m)) {
        Family::Mono
    } else if SERIF.iter().any(|s| n.contains(s)) && !n.contains("sans") {
        Family::Serif
    } else {
        Family::Sans
    }
}

/// Glyph widths (1/1000 em) of every WinAnsi code in each of the 12 faces.
fn widths() -> &'static [[f32; 256]; 12] {
    static W: OnceLock<[[f32; 256]; 12]> = OnceLock::new();
    W.get_or_init(|| {
        let mut w = [[0f32; 256]; 12];
        for (i, row) in w.iter_mut().enumerate() {
            let font = Face::of_index(i).standard();
            let fallback = font.win_ansi_width(b'n').unwrap_or(556.0);
            for (code, slot) in row.iter_mut().enumerate() {
                *slot = u8::try_from(code).ok().and_then(|c| font.win_ansi_width(c)).unwrap_or(fallback);
            }
        }
        w
    })
}

/// The character drawn for `c`: itself when WinAnsiEncoding has it, a close stand-in for a few
/// common ones (symbol-font bullets, minus, non-breaking hyphen), else `?`.
pub(crate) fn drawable(c: char) -> char {
    if pdfcraft_fonts::win_ansi_byte(c).is_some() {
        return c;
    }
    match c {
        '\t' => ' ',
        '\u{2212}' | '\u{2011}' | '\u{2010}' => '-',
        '\u{2007}' | '\u{2009}' | '\u{200A}' | '\u{202F}' | '\u{2002}' | '\u{2003}' => ' ',
        '\u{25AA}' | '\u{25CF}' | '\u{25E6}' | '\u{2219}' | '\u{25A0}' | '\u{25A1}' | '\u{25C6}' | '\u{27A2}' | '\u{2713}' | '\u{2714}' => '\u{2022}',
        // A page-number placeholder, replaced on each page.
        '\u{E000}' | '\u{E001}' => c,
        // Bullets from Symbol and Wingdings fonts (private use area).
        '\u{F000}'..='\u{F0FF}' => '\u{2022}',
        _ => '?',
    }
}

/// `s` with every character replaced by [`drawable`]; zero-width characters are dropped.
pub(crate) fn drawable_text(s: &str) -> String {
    s.chars().filter(|c| !matches!(c, '\u{200B}' | '\u{200C}' | '\u{200D}' | '\u{FEFF}' | '\u{00AD}')).map(drawable).collect()
}

/// Advance of `s` (already [`drawable_text`]) at `size` points in `face`.
pub(crate) fn text_width(face: Face, size: f64, s: &str) -> f64 {
    let row = widths().get(face.index());
    let units: f64 = s
        .chars()
        .map(|c| {
            let code = if matches!(c, '\u{E000}' | '\u{E001}') { b'0' } else { pdfcraft_fonts::win_ansi_byte(c).unwrap_or(b'?') };
            row.and_then(|r| r.get(usize::from(code))).copied().unwrap_or(500.0) as f64
        })
        .sum();
    units * size / 1000.0
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Rgb(pub u8, pub u8, pub u8);

pub(crate) const BLACK: Rgb = Rgb(0, 0, 0);
pub(crate) const WHITE: Rgb = Rgb(255, 255, 255);

impl Rgb {
    /// `RRGGBB` or `AARRGGBB` hex (an alpha byte is ignored).
    pub(crate) fn hex(s: &str) -> Option<Rgb> {
        let s = s.trim().trim_start_matches('#');
        let s = if s.len() == 8 { s.get(2..)? } else { s };
        if s.len() != 6 {
            return None;
        }
        let v = u32::from_str_radix(s, 16).ok()?;
        Some(Rgb((v >> 16) as u8, (v >> 8) as u8, v as u8))
    }

    /// Lighten (`t` > 0) or darken (`t` < 0) by a tint fraction.
    pub(crate) fn tint(self, t: f64) -> Rgb {
        if !t.is_finite() {
            return self;
        }
        let t = t.clamp(-1.0, 1.0);
        let f = |c: u8| {
            let c = f64::from(c);
            let v = if t >= 0.0 { c + (255.0 - c) * t } else { c * (1.0 + t) };
            v.round().clamp(0.0, 255.0) as u8
        };
        Rgb(f(self.0), f(self.1), f(self.2))
    }

    /// Scale luminance by `m` and add `o` (DrawingML `lumMod`/`lumOff`), in HSL.
    pub(crate) fn lum(self, m: f64, o: f64) -> Rgb {
        let (h, s, l) = self.hsl();
        let l = (l * m + o).clamp(0.0, 1.0);
        Rgb::from_hsl(h, s, if l.is_finite() { l } else { 0.0 })
    }

    fn hsl(self) -> (f64, f64, f64) {
        let (r, g, b) = (f64::from(self.0) / 255.0, f64::from(self.1) / 255.0, f64::from(self.2) / 255.0);
        let (max, min) = (r.max(g).max(b), r.min(g).min(b));
        let l = (max + min) / 2.0;
        if max - min < 1e-9 {
            return (0.0, 0.0, l);
        }
        let d = max - min;
        let s = if l > 0.5 { d / (2.0 - max - min) } else { d / (max + min) };
        let h = if max == r {
            (g - b) / d + if g < b { 6.0 } else { 0.0 }
        } else if max == g {
            (b - r) / d + 2.0
        } else {
            (r - g) / d + 4.0
        };
        (h / 6.0, s, l)
    }

    fn from_hsl(h: f64, s: f64, l: f64) -> Rgb {
        let to = |v: f64| (v * 255.0).round().clamp(0.0, 255.0) as u8;
        if s <= 0.0 {
            return Rgb(to(l), to(l), to(l));
        }
        let q = if l < 0.5 { l * (1.0 + s) } else { l + s - l * s };
        let p = 2.0 * l - q;
        let hue = |mut t: f64| {
            if t < 0.0 {
                t += 1.0;
            }
            if t > 1.0 {
                t -= 1.0;
            }
            if t < 1.0 / 6.0 {
                p + (q - p) * 6.0 * t
            } else if t < 0.5 {
                q
            } else if t < 2.0 / 3.0 {
                p + (q - p) * (2.0 / 3.0 - t) * 6.0
            } else {
                p
            }
        };
        Rgb(to(hue(h + 1.0 / 3.0)), to(hue(h)), to(hue(h - 1.0 / 3.0)))
    }
}

/// Path segments, in top-down page coordinates.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Seg {
    Move(f64, f64),
    Line(f64, f64),
    Curve(f64, f64, f64, f64, f64, f64),
    Close,
}

/// A drawing operation in top-down page coordinates (y grows downwards); text `y` is the
/// baseline.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Op {
    Text {
        x: f64,
        y: f64,
        face: Face,
        size: f64,
        color: Rgb,
        text: String,
        word_spacing: f64,
    },
    Rect {
        x: f64,
        y: f64,
        w: f64,
        h: f64,
        fill: Option<Rgb>,
        stroke: Option<(Rgb, f64)>,
    },
    Line {
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        color: Rgb,
        width: f64,
    },
    Path {
        segs: Vec<Seg>,
        fill: Option<Rgb>,
        stroke: Option<(Rgb, f64)>,
    },
    Image {
        id: usize,
        x: f64,
        y: f64,
        w: f64,
        h: f64,
    },
    /// Save the graphics state and clip to a rectangle, until the matching [`Op::Restore`].
    Clip {
        x: f64,
        y: f64,
        w: f64,
        h: f64,
    },
    /// Save the graphics state and rotate what follows `deg` degrees clockwise about
    /// `(cx, cy)`, until the matching [`Op::Restore`].
    Rotate {
        cx: f64,
        cy: f64,
        deg: f64,
    },
    Restore,
}

impl Op {
    /// Move by `(dx, dy)`.
    pub(crate) fn offset(&mut self, dx: f64, dy: f64) {
        match self {
            Op::Rotate { cx: x, cy: y, .. } | Op::Text { x, y, .. } | Op::Rect { x, y, .. } | Op::Image { x, y, .. } | Op::Clip { x, y, .. } => {
                *x += dx;
                *y += dy;
            }
            Op::Line { x1, y1, x2, y2, .. } => {
                *x1 += dx;
                *x2 += dx;
                *y1 += dy;
                *y2 += dy;
            }
            Op::Path { segs, .. } => {
                for s in segs {
                    match s {
                        Seg::Move(x, y) | Seg::Line(x, y) => {
                            *x += dx;
                            *y += dy;
                        }
                        Seg::Curve(a, b, c, d, e, f) => {
                            *a += dx;
                            *c += dx;
                            *e += dx;
                            *b += dy;
                            *d += dy;
                            *f += dy;
                        }
                        Seg::Close => {}
                    }
                }
            }
            Op::Restore => {}
        }
    }
}

/// Moves every op in `ops` by `(dx, dy)` and appends them to `out`.
pub(crate) fn place(out: &mut Vec<Op>, ops: &[Op], dx: f64, dy: f64) {
    out.extend(ops.iter().cloned().map(|mut o| {
        o.offset(dx, dy);
        o
    }));
}

/// A number for a content stream: finite, at most three decimals, no trailing zeros.
fn n(v: f64) -> String {
    let v = if v.is_finite() { v.clamp(-1e6, 1e6) } else { 0.0 };
    let s = format!("{v:.3}");
    let s = s.trim_end_matches('0').trim_end_matches('.');
    if s == "-0" || s.is_empty() { "0".into() } else { s.to_string() }
}

fn colour(c: Rgb) -> String {
    format!("{} {} {}", n(f64::from(c.0) / 255.0), n(f64::from(c.1) / 255.0), n(f64::from(c.2) / 255.0))
}

/// An embedded image: its object and natural size in points.
type Embedded = (ObjRef, (f64, f64));

/// Collects pages of operations into a PDF.
pub(crate) struct Writer {
    doc: Document,
    fonts: [Option<ObjRef>; 12],
    /// Embedded images: (cache key, the object and natural size, or `None` when it couldn't be read).
    images: Vec<(String, Option<Embedded>)>,
    pages: usize,
}

impl Writer {
    pub(crate) fn new() -> Self {
        Self { doc: Document::new_empty(), fonts: [None; 12], images: Vec::new(), pages: 0 }
    }

    /// Embed the image `bytes` (PNG, JPEG, GIF, BMP, TIFF, JPEG 2000), once per `key`. Returns
    /// its id for [`Op::Image`] and its natural size in points, or `None` when it can't be read
    /// (other formats, such as EMF or SVG, are left out of the page).
    pub(crate) fn image(&mut self, key: &str, bytes: &[u8]) -> Option<(usize, (f64, f64))> {
        if let Some(i) = self.images.iter().position(|(k, _)| k == key) {
            return self.images.get(i).and_then(|(_, v)| v.map(|(_, s)| (i, s)));
        }
        let made = crate::image_xobject(&mut self.doc, key, bytes).ok();
        self.images.push((key.to_string(), made));
        made.map(|(_, s)| (self.images.len() - 1, s))
    }

    pub(crate) fn page_count(&self) -> usize {
        self.pages
    }

    /// Add a page of `w × h` points drawn by `ops`.
    pub(crate) fn page(&mut self, w: f64, h: f64, ops: &[Op]) -> Result<(), CreateError> {
        if self.pages >= MAX_PAGES {
            return Err(CreateError::Invalid(format!("the converted document would have more than {MAX_PAGES} pages")));
        }
        let clamp = |v: f64| if v.is_finite() { v.clamp(36.0, crate::MAX_SIDE) } else { 612.0 };
        let (w, h) = (clamp(w), clamp(h));
        let mut c = Vec::new();
        let mut fonts_used = [false; 12];
        let mut images_used = Vec::new();
        let mut depth = 0usize;
        let mut spacing = 0.0f64;
        // `q` … `Q` restores the word spacing too.
        let mut saved: Vec<f64> = Vec::new();
        for op in ops {
            match op {
                Op::Text { x, y, face, size, color, text, word_spacing } => {
                    if text.is_empty() || !(size.is_finite() && *size > 0.0) {
                        continue;
                    }
                    let i = face.index();
                    if let Some(slot) = fonts_used.get_mut(i) {
                        *slot = true;
                    }
                    // Word spacing is text state, which outlives BT … ET: set it whenever it changes.
                    let ws = if word_spacing.is_finite() { *word_spacing } else { 0.0 };
                    let tw = if (ws - spacing).abs() > 0.001 {
                        spacing = ws;
                        format!("{} Tw ", n(ws))
                    } else {
                        String::new()
                    };
                    c.extend_from_slice(format!("BT /F{i} {} Tf {} rg {tw}{} {} Td ", n(*size), colour(*color), n(*x), n(h - y)).as_bytes());
                    c.extend(pdfcraft_fonts::literal(&pdfcraft_fonts::win_ansi(text)));
                    c.extend_from_slice(b" Tj ET\n");
                }
                Op::Rect { x, y, w: rw, h: rh, fill, stroke } => {
                    if fill.is_none() && stroke.is_none() {
                        continue;
                    }
                    let mut s = String::from("q ");
                    if let Some(f) = fill {
                        s.push_str(&format!("{} rg ", colour(*f)));
                    }
                    if let Some((sc, sw)) = stroke {
                        s.push_str(&format!("{} RG {} w ", colour(*sc), n(*sw)));
                    }
                    s.push_str(&format!("{} {} {} {} re ", n(*x), n(h - y - rh), n(*rw), n(*rh)));
                    s.push_str(match (fill.is_some(), stroke.is_some()) {
                        (true, true) => "B Q\n",
                        (true, false) => "f Q\n",
                        _ => "S Q\n",
                    });
                    c.extend_from_slice(s.as_bytes());
                }
                Op::Line { x1, y1, x2, y2, color, width } => {
                    c.extend_from_slice(
                        format!("q {} RG {} w 0 J {} {} m {} {} l S Q\n", colour(*color), n(*width), n(*x1), n(h - y1), n(*x2), n(h - y2)).as_bytes(),
                    );
                }
                Op::Path { segs, fill, stroke } => {
                    if (fill.is_none() && stroke.is_none()) || segs.is_empty() {
                        continue;
                    }
                    let mut s = String::from("q ");
                    if let Some(f) = fill {
                        s.push_str(&format!("{} rg ", colour(*f)));
                    }
                    if let Some((sc, sw)) = stroke {
                        s.push_str(&format!("{} RG {} w ", colour(*sc), n(*sw)));
                    }
                    for seg in segs {
                        match seg {
                            Seg::Move(x, y) => s.push_str(&format!("{} {} m ", n(*x), n(h - y))),
                            Seg::Line(x, y) => s.push_str(&format!("{} {} l ", n(*x), n(h - y))),
                            Seg::Curve(a, b, cc, d, e, f) => {
                                s.push_str(&format!("{} {} {} {} {} {} c ", n(*a), n(h - b), n(*cc), n(h - d), n(*e), n(h - f)))
                            }
                            Seg::Close => s.push_str("h "),
                        }
                    }
                    s.push_str(match (fill.is_some(), stroke.is_some()) {
                        (true, true) => "B Q\n",
                        (true, false) => "f Q\n",
                        _ => "S Q\n",
                    });
                    c.extend_from_slice(s.as_bytes());
                }
                Op::Image { id, x, y, w: iw, h: ih } => {
                    if !images_used.contains(id) {
                        images_used.push(*id);
                    }
                    c.extend_from_slice(format!("q {} 0 0 {} {} {} cm /Im{id} Do Q\n", n(*iw), n(*ih), n(*x), n(h - y - ih)).as_bytes());
                }
                Op::Clip { x, y, w: cw, h: ch } => {
                    depth += 1;
                    saved.push(spacing);
                    c.extend_from_slice(format!("q {} {} {} {} re W n\n", n(*x), n(h - y - ch), n(*cw), n(*ch)).as_bytes());
                }
                Op::Rotate { cx, cy, deg } => {
                    depth += 1;
                    saved.push(spacing);
                    // Clockwise on the page is a negative angle in PDF's y-up space.
                    let phi = if deg.is_finite() { -deg.to_radians() } else { 0.0 };
                    let (s, co) = phi.sin_cos();
                    let (px, py) = (*cx, h - cy);
                    let e = px - co * px + s * py;
                    let f = py - s * px - co * py;
                    c.extend_from_slice(format!("q {} {} {} {} {} {} cm\n", n(co), n(s), n(-s), n(co), n(e), n(f)).as_bytes());
                }
                Op::Restore => {
                    if depth > 0 {
                        depth -= 1;
                        spacing = saved.pop().unwrap_or(0.0);
                        c.extend_from_slice(b"Q\n");
                    }
                }
            }
        }
        for _ in 0..depth {
            c.extend_from_slice(b"Q\n");
        }
        let mut res = Dict::new();
        let mut font_dict = Dict::new();
        for (i, used) in fonts_used.iter().enumerate() {
            if !used {
                continue;
            }
            let r = match self.fonts.get(i).copied().flatten() {
                Some(r) => r,
                None => {
                    let mut f = Dict::new();
                    f.set(b"Type".to_vec(), Object::name("Font"));
                    f.set(b"Subtype".to_vec(), Object::name("Type1"));
                    f.set(b"BaseFont".to_vec(), Object::name(Face::of_index(i).base_font()));
                    f.set(b"Encoding".to_vec(), Object::name("WinAnsiEncoding"));
                    let r = self.doc.add(f);
                    if let Some(slot) = self.fonts.get_mut(i) {
                        *slot = Some(r);
                    }
                    r
                }
            };
            font_dict.set(format!("F{i}").into_bytes(), Object::Ref(r));
        }
        if !font_dict.is_empty() {
            res.set(b"Font".to_vec(), Object::Dict(font_dict));
        }
        let mut xo = Dict::new();
        for id in images_used {
            if let Some((_, Some((r, _)))) = self.images.get(id) {
                xo.set(format!("Im{id}").into_bytes(), Object::Ref(*r));
            }
        }
        if !xo.is_empty() {
            res.set(b"XObject".to_vec(), Object::Dict(xo));
        }
        add_page(&mut self.doc, w, h, res, Some(c))?;
        self.pages += 1;
        Ok(())
    }

    pub(crate) fn finish(mut self, title: &str) -> Result<Document, CreateError> {
        if self.pages == 0 {
            // An empty document still converts: one blank page, as an empty file prints.
            self.page(crate::LETTER.0, crate::LETTER.1, &[])?;
        }
        crate::set_title(&mut self.doc, title);
        Ok(self.doc)
    }
}

/// A rounded rectangle as path segments.
pub(crate) fn round_rect(x: f64, y: f64, w: f64, h: f64, r: f64) -> Vec<Seg> {
    let r = r.min(w / 2.0).min(h / 2.0).max(0.0);
    let k = r * 0.5523;
    vec![
        Seg::Move(x + r, y),
        Seg::Line(x + w - r, y),
        Seg::Curve(x + w - r + k, y, x + w, y + r - k, x + w, y + r),
        Seg::Line(x + w, y + h - r),
        Seg::Curve(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h),
        Seg::Line(x + r, y + h),
        Seg::Curve(x + r - k, y + h, x, y + h - r + k, x, y + h - r),
        Seg::Line(x, y + r),
        Seg::Curve(x, y + r - k, x + r - k, y, x + r, y),
        Seg::Close,
    ]
}

/// An ellipse inscribed in a rectangle.
pub(crate) fn ellipse(x: f64, y: f64, w: f64, h: f64) -> Vec<Seg> {
    let (rx, ry) = (w / 2.0, h / 2.0);
    let (cx, cy) = (x + rx, y + ry);
    let (kx, ky) = (rx * 0.5523, ry * 0.5523);
    vec![
        Seg::Move(cx + rx, cy),
        Seg::Curve(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
        Seg::Curve(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
        Seg::Curve(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
        Seg::Curve(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
        Seg::Close,
    ]
}

/// A polygon through `pts`.
pub(crate) fn polygon(pts: &[(f64, f64)]) -> Vec<Seg> {
    let mut v: Vec<Seg> = pts.iter().enumerate().map(|(i, &(x, y))| if i == 0 { Seg::Move(x, y) } else { Seg::Line(x, y) }).collect();
    v.push(Seg::Close);
    v
}

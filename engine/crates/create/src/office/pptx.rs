//! PowerPoint presentations (.pptx, ECMA-376 Part 1 §19 PresentationML and §20 DrawingML) to
//! one page per slide.

use std::collections::HashMap;

use roxmltree::{Document, Node};

use super::Package;
use super::draw::{BLACK, Face, Op, Rgb, Seg, WHITE, Writer, ellipse, family_of, polygon, round_rect};
use super::flow::{self, Align, Inline, LineRule, Para, Style, VItem};
use super::xml::{attr, child, children, elements, num, parse_str, path, relationships};

const EMU: f64 = 1.0 / 12700.0;
/// How deep group shapes may nest.
const MAX_GROUP_DEPTH: usize = 16;

// ── theme and colours ───────────────────────────────────────────────────────────────────────

#[derive(Clone)]
struct Theme {
    colours: HashMap<String, Rgb>,
    major: String,
    minor: String,
}

fn parse_theme(text: Option<&str>) -> Theme {
    let mut colours = HashMap::new();
    for (k, v) in [
        ("dk1", BLACK),
        ("lt1", WHITE),
        ("dk2", Rgb(0x44, 0x54, 0x6A)),
        ("lt2", Rgb(0xE7, 0xE6, 0xE6)),
        ("accent1", Rgb(0x44, 0x72, 0xC4)),
        ("accent2", Rgb(0xED, 0x7D, 0x31)),
        ("accent3", Rgb(0xA5, 0xA5, 0xA5)),
        ("accent4", Rgb(0xFF, 0xC0, 0x00)),
        ("accent5", Rgb(0x5B, 0x9B, 0xD5)),
        ("accent6", Rgb(0x70, 0xAD, 0x47)),
        ("hlink", Rgb(0x05, 0x63, 0xC1)),
        ("folHlink", Rgb(0x95, 0x4F, 0x72)),
    ] {
        colours.insert(k.to_string(), v);
    }
    let mut t = Theme { colours, major: "Calibri Light".into(), minor: "Calibri".into() };
    let Some(doc) = text.and_then(parse_str) else { return t };
    if let Some(scheme) = doc.descendants().find(|n| n.tag_name().name() == "clrScheme") {
        for c in elements(scheme) {
            let rgb = elements(c).next().and_then(|v| match v.tag_name().name() {
                "srgbClr" => attr(v, "val").and_then(Rgb::hex),
                "sysClr" => attr(v, "lastClr").and_then(Rgb::hex),
                _ => None,
            });
            if let Some(rgb) = rgb {
                t.colours.insert(c.tag_name().name().to_string(), rgb);
            }
        }
    }
    if let Some(fs) = doc.descendants().find(|n| n.tag_name().name() == "fontScheme") {
        if let Some(f) = path(fs, &["majorFont", "latin"]).and_then(|n| attr(n, "typeface")).filter(|s| !s.is_empty()) {
            t.major = f.to_string();
        }
        if let Some(f) = path(fs, &["minorFont", "latin"]).and_then(|n| attr(n, "typeface")).filter(|s| !s.is_empty()) {
            t.minor = f.to_string();
        }
    }
    t
}

/// Colour context: the theme, the master's colour map, and the placeholder colour (`phClr`)
/// from a style reference.
#[derive(Clone)]
struct Colours<'t> {
    theme: &'t Theme,
    map: HashMap<String, String>,
}

impl Colours<'_> {
    /// The colour of a colour element (`srgbClr`, `schemeClr`, …) with its modifiers.
    fn of(&self, c: Node<'_, '_>, ph: Option<Rgb>) -> Option<Rgb> {
        let base = match c.tag_name().name() {
            "srgbClr" => attr(c, "val").and_then(Rgb::hex),
            "sysClr" => attr(c, "lastClr").and_then(Rgb::hex).or(Some(if attr(c, "val") == Some("window") { WHITE } else { BLACK })),
            "schemeClr" => {
                let v = attr(c, "val")?;
                if v == "phClr" {
                    ph.or(Some(BLACK))
                } else {
                    let mapped = self.map.get(v).map(String::as_str).unwrap_or(match v {
                        "bg1" => "lt1",
                        "tx1" => "dk1",
                        "bg2" => "lt2",
                        "tx2" => "dk2",
                        other => other,
                    });
                    self.theme.colours.get(mapped).copied()
                }
            }
            "prstClr" => Some(match attr(c, "val").unwrap_or_default() {
                "white" => WHITE,
                "red" => Rgb(255, 0, 0),
                "green" => Rgb(0, 128, 0),
                "blue" => Rgb(0, 0, 255),
                "yellow" => Rgb(255, 255, 0),
                "gray" | "grey" => Rgb(128, 128, 128),
                _ => BLACK,
            }),
            "scrgbClr" => {
                let p = |a: &str| (num(c, a).unwrap_or(0.0) / 100_000.0).clamp(0.0, 1.0);
                let f = |v: f64| (v.powf(1.0 / 2.2) * 255.0).round() as u8;
                Some(Rgb(f(p("r")), f(p("g")), f(p("b"))))
            }
            _ => None,
        }?;
        let mut out = base;
        let mut lum_mod = None;
        let mut lum_off = 0.0;
        for m in elements(c) {
            let v = num(m, "val").unwrap_or(100_000.0) / 100_000.0;
            match m.tag_name().name() {
                "lumMod" => lum_mod = Some(v),
                "lumOff" => lum_off = v,
                "tint" => out = out.tint(1.0 - v.clamp(0.0, 1.0)),
                "shade" => out = out.tint(-(1.0 - v.clamp(0.0, 1.0))),
                _ => {}
            }
        }
        if lum_mod.is_some() || lum_off != 0.0 {
            out = out.lum(lum_mod.unwrap_or(1.0), lum_off);
        }
        Some(out)
    }

    /// The colour inside a fill-like element (`solidFill`, `fgClr`, a style reference …).
    fn inside(&self, n: Node<'_, '_>, ph: Option<Rgb>) -> Option<Rgb> {
        elements(n).find_map(|c| self.of(c, ph))
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum Fill {
    None,
    Solid(Rgb),
}

/// The fill an element (`spPr`, `bgPr`, `tcPr`) sets, if it sets one.
fn fill_of(n: Node<'_, '_>, col: &Colours<'_>, ph: Option<Rgb>) -> Option<Fill> {
    for c in elements(n) {
        match c.tag_name().name() {
            "noFill" => return Some(Fill::None),
            "solidFill" => return Some(col.inside(c, ph).map_or(Fill::None, Fill::Solid)),
            "gradFill" => {
                // The middle stop stands for the gradient.
                let stops: Vec<Rgb> = c.descendants().filter(|g| g.tag_name().name() == "gs").filter_map(|g| col.inside(g, ph)).collect();
                return Some(stops.get(stops.len() / 2).copied().map_or(Fill::None, Fill::Solid));
            }
            "pattFill" => return Some(child(c, "fgClr").and_then(|f| col.inside(f, ph)).map_or(Fill::None, Fill::Solid)),
            _ => {}
        }
    }
    None
}

// ── text properties ─────────────────────────────────────────────────────────────────────────

#[derive(Clone, Debug, Default)]
struct RProps {
    size: Option<f64>,
    bold: Option<bool>,
    italic: Option<bool>,
    underline: Option<bool>,
    strike: Option<bool>,
    colour: Option<Rgb>,
    font: Option<String>,
    baseline: Option<f64>,
    caps: Option<bool>,
}

impl RProps {
    fn merge(&mut self, o: &RProps) {
        macro_rules! take {
            ($($f:ident),*) => { $( if o.$f.is_some() { self.$f = o.$f.clone(); } )* };
        }
        take!(size, bold, italic, underline, strike, colour, font, baseline, caps);
    }
}

#[derive(Clone, Debug)]
enum Bullet {
    None,
    Char(String),
    Auto(String, i64),
}

#[derive(Clone, Copy, Debug)]
enum Spacing {
    Pct(f64),
    Pts(f64),
}

#[derive(Clone, Debug, Default)]
struct PProps {
    align: Option<Align>,
    mar_l: Option<f64>,
    indent: Option<f64>,
    bullet: Option<Bullet>,
    bullet_colour: Option<Rgb>,
    bullet_size: Option<f64>,
    /// The bullet is in a symbol font (Wingdings, Symbol), which isn't available: drawn as a bullet.
    bullet_symbol: Option<bool>,
    line: Option<Spacing>,
    before: Option<Spacing>,
    after: Option<Spacing>,
    def: RProps,
}

impl PProps {
    fn merge(&mut self, o: &PProps) {
        macro_rules! take {
            ($($f:ident),*) => { $( if o.$f.is_some() { self.$f = o.$f.clone(); } )* };
        }
        take!(align, mar_l, indent, bullet, bullet_colour, bullet_size, bullet_symbol, line, before, after);
        self.def.merge(&o.def);
    }
}

type Levels = Vec<PProps>;

fn spacing(n: Option<Node<'_, '_>>) -> Option<Spacing> {
    let n = n?;
    if let Some(p) = child(n, "spcPct").and_then(|p| num(p, "val")) {
        return Some(Spacing::Pct((p / 100_000.0).clamp(0.0, 10.0)));
    }
    child(n, "spcPts").and_then(|p| num(p, "val")).map(|v| Spacing::Pts((v / 100.0).clamp(0.0, 1000.0)))
}

fn parse_rpr(n: Option<Node<'_, '_>>, col: &Colours<'_>, theme: &Theme) -> RProps {
    let mut r = RProps::default();
    let Some(n) = n else { return r };
    r.size = num(n, "sz").map(|v| (v / 100.0).clamp(1.0, 4000.0));
    r.bold = attr(n, "b").map(|v| v == "1" || v == "true");
    r.italic = attr(n, "i").map(|v| v == "1" || v == "true");
    r.underline = attr(n, "u").map(|v| v != "none");
    r.strike = attr(n, "strike").map(|v| v != "noStrike");
    r.baseline = num(n, "baseline").map(|v| v / 100_000.0);
    r.caps = attr(n, "cap").map(|v| v == "all");
    if let Some(f) = child(n, "solidFill") {
        r.colour = col.inside(f, None);
    }
    if let Some(l) = child(n, "latin").and_then(|l| attr(l, "typeface")) {
        r.font = Some(match l {
            "+mj-lt" => theme.major.clone(),
            "+mn-lt" => theme.minor.clone(),
            other => other.to_string(),
        });
    }
    r
}

fn parse_ppr(n: Option<Node<'_, '_>>, col: &Colours<'_>, theme: &Theme) -> PProps {
    let mut p = PProps::default();
    let Some(n) = n else { return p };
    p.align = attr(n, "algn").map(|a| match a {
        "ctr" => Align::Center,
        "r" => Align::Right,
        "just" | "dist" | "justLow" | "thaiDist" => Align::Justify,
        _ => Align::Left,
    });
    p.mar_l = num(n, "marL").map(|v| v * EMU);
    p.indent = num(n, "indent").map(|v| v * EMU);
    p.line = spacing(child(n, "lnSpc"));
    p.before = spacing(child(n, "spcBef"));
    p.after = spacing(child(n, "spcAft"));
    for c in elements(n) {
        match c.tag_name().name() {
            "buNone" => p.bullet = Some(Bullet::None),
            "buChar" => p.bullet = Some(Bullet::Char(attr(c, "char").unwrap_or("\u{2022}").to_string())),
            "buAutoNum" => {
                p.bullet = Some(Bullet::Auto(
                    attr(c, "type").unwrap_or("arabicPeriod").to_string(),
                    num(c, "startAt").map_or(1, |v| v.clamp(-1e6, 1e6) as i64),
                ))
            }
            "buClr" => p.bullet_colour = col.inside(c, None),
            "buFont" => {
                let f = attr(c, "typeface").unwrap_or_default().to_ascii_lowercase();
                p.bullet_symbol = Some(f.contains("wingdings") || f.contains("symbol") || f.contains("webdings"));
            }
            "buSzPct" => p.bullet_size = num(c, "val").map(|v| (v / 100_000.0).clamp(0.1, 4.0)),
            "defRPr" => p.def = parse_rpr(Some(c), col, theme),
            _ => {}
        }
    }
    p
}

/// `lvl1pPr` … `lvl9pPr` of a list style (`lstStyle`, `titleStyle`, …).
fn parse_levels(n: Option<Node<'_, '_>>, col: &Colours<'_>, theme: &Theme) -> Levels {
    let mut out = vec![PProps::default(); 9];
    let Some(n) = n else { return out };
    for (i, slot) in out.iter_mut().enumerate() {
        if let Some(l) = child(n, &format!("lvl{}pPr", i + 1)) {
            *slot = parse_ppr(Some(l), col, theme);
        }
    }
    if let Some(d) = child(n, "defPPr") {
        let def = parse_ppr(Some(d), col, theme);
        for slot in &mut out {
            let mut merged = def.clone();
            merged.merge(slot);
            *slot = merged;
        }
    }
    out
}

#[derive(Clone, Copy, Debug)]
struct BodyPr {
    insets: (f64, f64, f64, f64),
    anchor: Anchor,
    wrap: bool,
    font_scale: f64,
    line_reduction: f64,
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum Anchor {
    Top,
    Middle,
    Bottom,
}

#[derive(Clone, Copy, Debug, Default)]
struct BodyOverrides {
    l: Option<f64>,
    t: Option<f64>,
    r: Option<f64>,
    b: Option<f64>,
    anchor: Option<Anchor>,
    wrap: Option<bool>,
    font_scale: Option<f64>,
    line_reduction: Option<f64>,
}

fn parse_body(n: Option<Node<'_, '_>>) -> BodyOverrides {
    let mut b = BodyOverrides::default();
    let Some(n) = n else { return b };
    b.l = num(n, "lIns").map(|v| v * EMU);
    b.t = num(n, "tIns").map(|v| v * EMU);
    b.r = num(n, "rIns").map(|v| v * EMU);
    b.b = num(n, "bIns").map(|v| v * EMU);
    b.anchor = attr(n, "anchor").map(|a| match a {
        "ctr" => Anchor::Middle,
        "b" => Anchor::Bottom,
        _ => Anchor::Top,
    });
    b.wrap = attr(n, "wrap").map(|w| w != "none");
    if let Some(a) = child(n, "normAutofit") {
        b.font_scale = Some(num(a, "fontScale").map_or(1.0, |v| (v / 100_000.0).clamp(0.1, 1.0)));
        b.line_reduction = Some(num(a, "lnSpcReduction").map_or(0.0, |v| (v / 100_000.0).clamp(0.0, 0.9)));
    }
    b
}

impl BodyOverrides {
    fn over(self, o: BodyOverrides) -> BodyOverrides {
        BodyOverrides {
            l: o.l.or(self.l),
            t: o.t.or(self.t),
            r: o.r.or(self.r),
            b: o.b.or(self.b),
            anchor: o.anchor.or(self.anchor),
            wrap: o.wrap.or(self.wrap),
            font_scale: o.font_scale.or(self.font_scale),
            line_reduction: o.line_reduction.or(self.line_reduction),
        }
    }

    fn resolve(self) -> BodyPr {
        BodyPr {
            insets: (self.l.unwrap_or(7.2), self.t.unwrap_or(3.6), self.r.unwrap_or(7.2), self.b.unwrap_or(3.6)),
            anchor: self.anchor.unwrap_or(Anchor::Top),
            wrap: self.wrap.unwrap_or(true),
            font_scale: self.font_scale.unwrap_or(1.0),
            line_reduction: self.line_reduction.unwrap_or(0.0),
        }
    }
}

// ── geometry ────────────────────────────────────────────────────────────────────────────────

/// Maps shape coordinates (EMU, possibly a group's child space) to page points.
#[derive(Clone, Copy, Debug)]
struct Xf {
    sx: f64,
    sy: f64,
    tx: f64,
    ty: f64,
}

impl Xf {
    fn pt(&self, x: f64, y: f64) -> (f64, f64) {
        (x * self.sx + self.tx, y * self.sy + self.ty)
    }
}

#[derive(Clone, Copy, Debug)]
struct Frame {
    x: f64,
    y: f64,
    w: f64,
    h: f64,
    flip_h: bool,
    flip_v: bool,
}

fn xfrm_of(n: Option<Node<'_, '_>>) -> Option<(f64, f64, f64, f64, bool, bool)> {
    let n = n?;
    let off = child(n, "off")?;
    let ext = child(n, "ext")?;
    let v = (num(off, "x")?, num(off, "y")?, num(ext, "cx")?, num(ext, "cy")?);
    Some((v.0, v.1, v.2.max(0.0), v.3.max(0.0), attr(n, "flipH") == Some("1"), attr(n, "flipV") == Some("1")))
}

/// The clockwise rotation of an `xfrm`, in degrees (0 when absent or unreadable).
fn rotation(n: Option<Node<'_, '_>>) -> f64 {
    n.and_then(|x| num(x, "rot")).map_or(0.0, |r| (r / 60_000.0) % 360.0)
}

fn frame(xf: &Xf, v: (f64, f64, f64, f64, bool, bool)) -> Frame {
    let (x, y) = xf.pt(v.0, v.1);
    let (x2, y2) = xf.pt(v.0 + v.2, v.1 + v.3);
    Frame { x: x.min(x2), y: y.min(y2), w: (x2 - x).abs(), h: (y2 - y).abs(), flip_h: v.4, flip_v: v.5 }
}

/// Path segments of a preset shape in its frame (a few common ones; the rest as rectangles).
fn preset(name: &str, f: &Frame, adj: Option<f64>) -> Vec<Seg> {
    let Frame { x, y, w, h, .. } = *f;
    let m = w.min(h);
    match name {
        "ellipse" | "flowChartConnector" => ellipse(x, y, w, h),
        "roundRect" | "flowChartAlternateProcess" => round_rect(x, y, w, h, m * adj.unwrap_or(0.16667)),
        "triangle" | "flowChartExtract" => polygon(&[(x + w * adj.unwrap_or(0.5), y), (x + w, y + h), (x, y + h)]),
        "rtTriangle" => polygon(&[(x, y), (x + w, y + h), (x, y + h)]),
        "diamond" | "flowChartDecision" => polygon(&[(x + w / 2.0, y), (x + w, y + h / 2.0), (x + w / 2.0, y + h), (x, y + h / 2.0)]),
        "parallelogram" | "flowChartInputOutput" => {
            let d = m * adj.unwrap_or(0.25);
            polygon(&[(x + d, y), (x + w, y), (x + w - d, y + h), (x, y + h)])
        }
        "trapezoid" => {
            let d = m * adj.unwrap_or(0.25);
            polygon(&[(x + d, y), (x + w - d, y), (x + w, y + h), (x, y + h)])
        }
        "hexagon" => {
            let d = m * adj.unwrap_or(0.25);
            polygon(&[(x + d, y), (x + w - d, y), (x + w, y + h / 2.0), (x + w - d, y + h), (x + d, y + h), (x, y + h / 2.0)])
        }
        "octagon" => {
            let d = m * adj.unwrap_or(0.29289);
            polygon(&[(x + d, y), (x + w - d, y), (x + w, y + d), (x + w, y + h - d), (x + w - d, y + h), (x + d, y + h), (x, y + h - d), (x, y + d)])
        }
        "pentagon" | "homePlate" => {
            if name == "homePlate" {
                let d = m * adj.unwrap_or(0.5);
                polygon(&[(x, y), (x + w - d, y), (x + w, y + h / 2.0), (x + w - d, y + h), (x, y + h)])
            } else {
                polygon(&[(x + w / 2.0, y), (x + w, y + h * 0.38), (x + w * 0.81, y + h), (x + w * 0.19, y + h), (x, y + h * 0.38)])
            }
        }
        "chevron" => {
            let d = m * adj.unwrap_or(0.5);
            polygon(&[(x, y), (x + w - d, y), (x + w, y + h / 2.0), (x + w - d, y + h), (x, y + h), (x + d, y + h / 2.0)])
        }
        "rightArrow" => {
            let (a1, a2) = (0.5, adj.unwrap_or(0.5));
            let head = (m * a2).min(w);
            let t = h * a1 / 2.0;
            polygon(&[
                (x, y + h / 2.0 - t),
                (x + w - head, y + h / 2.0 - t),
                (x + w - head, y),
                (x + w, y + h / 2.0),
                (x + w - head, y + h),
                (x + w - head, y + h / 2.0 + t),
                (x, y + h / 2.0 + t),
            ])
        }
        "leftArrow" => {
            let head = (m * adj.unwrap_or(0.5)).min(w);
            let t = h / 4.0;
            polygon(&[
                (x + w, y + h / 2.0 - t),
                (x + head, y + h / 2.0 - t),
                (x + head, y),
                (x, y + h / 2.0),
                (x + head, y + h),
                (x + head, y + h / 2.0 + t),
                (x + w, y + h / 2.0 + t),
            ])
        }
        "upArrow" => {
            let head = (m * adj.unwrap_or(0.5)).min(h);
            let t = w / 4.0;
            polygon(&[
                (x + w / 2.0 - t, y + h),
                (x + w / 2.0 - t, y + head),
                (x, y + head),
                (x + w / 2.0, y),
                (x + w, y + head),
                (x + w / 2.0 + t, y + head),
                (x + w / 2.0 + t, y + h),
            ])
        }
        "downArrow" => {
            let head = (m * adj.unwrap_or(0.5)).min(h);
            let t = w / 4.0;
            polygon(&[
                (x + w / 2.0 - t, y),
                (x + w / 2.0 - t, y + h - head),
                (x, y + h - head),
                (x + w / 2.0, y + h),
                (x + w, y + h - head),
                (x + w / 2.0 + t, y + h - head),
                (x + w / 2.0 + t, y),
            ])
        }
        "plus" | "mathPlus" => {
            let d = m * adj.unwrap_or(0.25);
            polygon(&[
                (x + d, y),
                (x + w - d, y),
                (x + w - d, y + d),
                (x + w, y + d),
                (x + w, y + h - d),
                (x + w - d, y + h - d),
                (x + w - d, y + h),
                (x + d, y + h),
                (x + d, y + h - d),
                (x, y + h - d),
                (x, y + d),
                (x + d, y + d),
            ])
        }
        "line" | "straightConnector1" => {
            let (x1, x2) = if f.flip_h { (x + w, x) } else { (x, x + w) };
            let (y1, y2) = if f.flip_v { (y + h, y) } else { (y, y + h) };
            vec![Seg::Move(x1, y1), Seg::Line(x2, y2)]
        }
        _ => polygon(&[(x, y), (x + w, y), (x + w, y + h), (x, y + h)]),
    }
}

/// A custom geometry's paths, scaled into its frame.
fn custom(geom: Node<'_, '_>, f: &Frame) -> Vec<Seg> {
    let mut out = Vec::new();
    let Some(list) = child(geom, "pathLst") else { return out };
    for p in children(list, "path").take(64) {
        let pw = num(p, "w").filter(|v| *v > 0.0);
        let ph = num(p, "h").filter(|v| *v > 0.0);
        let (kx, ky) = (pw.map_or(EMU, |pw| f.w / pw), ph.map_or(EMU, |ph| f.h / ph));
        let pt = |n: Node<'_, '_>| -> Option<(f64, f64)> { Some((f.x + num(n, "x")? * kx, f.y + num(n, "y")? * ky)) };
        let mut cur = (f.x, f.y);
        for cmd in elements(p).take(10_000) {
            let pts: Vec<(f64, f64)> = children(cmd, "pt").filter_map(pt).collect();
            match cmd.tag_name().name() {
                "moveTo" => {
                    if let Some(&(x, y)) = pts.first() {
                        out.push(Seg::Move(x, y));
                        cur = (x, y);
                    }
                }
                "lnTo" => {
                    if let Some(&(x, y)) = pts.first() {
                        out.push(Seg::Line(x, y));
                        cur = (x, y);
                    }
                }
                "cubicBezTo" => {
                    if let (Some(a), Some(b), Some(c)) = (pts.first(), pts.get(1), pts.get(2)) {
                        out.push(Seg::Curve(a.0, a.1, b.0, b.1, c.0, c.1));
                        cur = *c;
                    }
                }
                "quadBezTo" => {
                    if let (Some(a), Some(b)) = (pts.first(), pts.get(1)) {
                        let c1 = (cur.0 + 2.0 / 3.0 * (a.0 - cur.0), cur.1 + 2.0 / 3.0 * (a.1 - cur.1));
                        let c2 = (b.0 + 2.0 / 3.0 * (a.0 - b.0), b.1 + 2.0 / 3.0 * (a.1 - b.1));
                        out.push(Seg::Curve(c1.0, c1.1, c2.0, c2.1, b.0, b.1));
                        cur = *b;
                    }
                }
                "close" => out.push(Seg::Close),
                _ => {}
            }
        }
    }
    if out.first().is_some_and(|s| !matches!(s, Seg::Move(..))) {
        out.insert(0, Seg::Move(f.x, f.y));
    }
    out
}

// ── slides ──────────────────────────────────────────────────────────────────────────────────

/// One of slide, layout or master: its XML and relationships.
struct Part<'d, 'i> {
    root: Node<'d, 'i>,
    rels: Vec<(String, String, String)>,
}

impl Part<'_, '_> {
    fn target(&self, id: &str) -> Option<&str> {
        self.rels.iter().find(|(i, _, _)| i == id).map(|(_, _, p)| p.as_str())
    }
    fn tree(&self) -> Option<Node<'_, '_>> {
        path(self.root, &["cSld", "spTree"])
    }
}

/// A placeholder's type and index.
fn placeholder(sp: Node<'_, '_>) -> Option<(String, Option<String>)> {
    let nv = elements(sp).find(|n| n.tag_name().name().starts_with("nv"))?;
    let ph = path(nv, &["nvPr", "ph"])?;
    Some((attr(ph, "type").unwrap_or("body").to_string(), attr(ph, "idx").map(str::to_string)))
}

/// Normalised placeholder family for matching across slide, layout and master.
fn ph_family(t: &str) -> &str {
    match t {
        "ctrTitle" | "title" => "title",
        "subTitle" | "body" | "obj" => "body",
        other => other,
    }
}

/// The shape on `part` matching placeholder `(ty, idx)`.
fn find_placeholder<'d, 'i>(part: &Part<'d, 'i>, ty: &str, idx: Option<&str>, by_type_only: bool) -> Option<Node<'d, 'i>> {
    let tree = path(part.root, &["cSld", "spTree"])?;
    let shapes: Vec<Node<'d, 'i>> = tree.descendants().filter(|n| matches!(n.tag_name().name(), "sp" | "pic")).collect();
    if !by_type_only
        && let Some(idx) = idx
        && let Some(s) = shapes.iter().find(|s| placeholder(**s).is_some_and(|(_, i)| i.as_deref() == Some(idx)))
    {
        return Some(*s);
    }
    let fam = ph_family(ty);
    shapes
        .iter()
        .find(|s| placeholder(**s).is_some_and(|(t, _)| t == ty))
        .or_else(|| shapes.iter().find(|s| placeholder(**s).is_some_and(|(t, _)| ph_family(&t) == fam)))
        .copied()
}

struct Deck<'p, 'a> {
    pkg: &'p Package<'a>,
    theme: Theme,
    default_text: Levels,
    texts: HashMap<String, String>,
}

impl Deck<'_, '_> {
    fn text(&mut self, part: &str) -> Result<Option<String>, String> {
        if let Some(t) = self.texts.get(part) {
            return Ok(Some(t.clone()));
        }
        let t = self.pkg.xml(part)?;
        if let Some(t) = &t {
            // Masters and layouts are shared by many slides; keep them.
            if self.texts.len() < 64 {
                self.texts.insert(part.to_string(), t.clone());
            }
        }
        Ok(t)
    }
}

pub(crate) fn convert(pkg: &Package<'_>, main: &str, writer: &mut Writer) -> Result<(), String> {
    let text = pkg.xml(main)?.ok_or_else(|| pkg.damaged("no presentation part"))?;
    let doc = parse_str(&text).ok_or_else(|| pkg.damaged("its presentation XML can't be read"))?;
    let root = doc.root_element();
    let rels = relationships(&pkg.zip, main);
    let (sw, sh) = child(root, "sldSz")
        .and_then(|s| Some((num(s, "cx")? * EMU, num(s, "cy")? * EMU)))
        .filter(|(w, h)| *w >= 36.0 && *h >= 36.0 && *w <= 14_400.0 && *h <= 14_400.0)
        .unwrap_or((720.0, 405.0));
    let theme_part = rels.iter().find(|(_, t, _)| t.ends_with("/theme")).map(|(_, _, p)| p.clone());
    let mut deck = Deck { pkg, theme: parse_theme(None), default_text: vec![PProps::default(); 9], texts: HashMap::new() };
    if let Some(p) = theme_part {
        let t = pkg.xml(&p)?;
        deck.theme = parse_theme(t.as_deref());
    }
    let ids: Vec<String> = child(root, "sldIdLst")
        .map(|l| children(l, "sldId").filter_map(|s| attr(s, "id").filter(|_| true).and(attr_rid(s))).collect())
        .unwrap_or_default();
    let mut number = 0usize;
    for rid in ids {
        let Some(part) = rels.iter().find(|(i, _, _)| *i == rid).map(|(_, _, p)| p.clone()) else { continue };
        let Some(slide_text) = deck.text(&part)? else { continue };
        let Some(slide_doc) = parse_str(&slide_text) else { continue };
        if attr(slide_doc.root_element(), "show").is_some_and(|v| v == "0" || v == "false") {
            continue;
        }
        number += 1;
        let ops = render_slide(&mut deck, writer, &doc, &part, &slide_doc, (sw, sh), number)?;
        writer.page(sw, sh, &ops).map_err(|e| e.to_string())?;
    }
    if number == 0 {
        writer.page(sw, sh, &[]).map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn attr_rid(s: Node<'_, '_>) -> Option<String> {
    // `r:id` (the relationship), not the numeric `id`.
    s.attributes().find(|a| a.name() == "id" && a.namespace().is_some_and(|ns| ns.contains("relationships"))).map(|a| a.value().to_string())
}

fn render_slide(
    deck: &mut Deck<'_, '_>,
    writer: &mut Writer,
    pres: &Document<'_>,
    part: &str,
    slide: &Document<'_>,
    (sw, sh): (f64, f64),
    number: usize,
) -> Result<Vec<Op>, String> {
    let slide_rels = relationships(&deck.pkg.zip, part);
    let layout_part = slide_rels.iter().find(|(_, t, _)| t.ends_with("/slideLayout")).map(|(_, _, p)| p.clone());
    let layout_text = match &layout_part {
        Some(p) => deck.text(p)?,
        None => None,
    };
    let layout_doc = layout_text.as_deref().and_then(parse_str);
    let layout_rels = layout_part.as_deref().map(|p| relationships(&deck.pkg.zip, p)).unwrap_or_default();
    let master_part = layout_rels.iter().find(|(_, t, _)| t.ends_with("/slideMaster")).map(|(_, _, p)| p.clone());
    let master_text = match &master_part {
        Some(p) => deck.text(p)?,
        None => None,
    };
    let master_doc = master_text.as_deref().and_then(parse_str);
    let master_rels = master_part.as_deref().map(|p| relationships(&deck.pkg.zip, p)).unwrap_or_default();
    // The master's own theme, when it has one.
    if let Some(tp) = master_rels.iter().find(|(_, t, _)| t.ends_with("/theme")).map(|(_, _, p)| p.clone())
        && let Some(t) = deck.text(&tp)?
    {
        deck.theme = parse_theme(Some(&t));
    }
    let theme = deck.theme.clone();
    let mut map: HashMap<String, String> = HashMap::new();
    if let Some(m) = master_doc.as_ref().and_then(|d| child(d.root_element(), "clrMap")) {
        for a in m.attributes() {
            map.insert(a.name().to_string(), a.value().to_string());
        }
    }
    for d in [layout_doc.as_ref(), Some(slide)].into_iter().flatten() {
        if let Some(o) = path(d.root_element(), &["clrMapOvr", "overrideClrMapping"]) {
            for a in o.attributes() {
                map.insert(a.name().to_string(), a.value().to_string());
            }
        }
    }
    let col = Colours { theme: &theme, map };
    deck.default_text = parse_levels(child(pres.root_element(), "defaultTextStyle"), &col, &theme);
    let slide_part = Part { root: slide.root_element(), rels: slide_rels };
    let layout = layout_doc.as_ref().map(|d| Part { root: d.root_element(), rels: layout_rels.clone() });
    let master = master_doc.as_ref().map(|d| Part { root: d.root_element(), rels: master_rels.clone() });
    let tx_styles = master.as_ref().and_then(|m| child(m.root, "txStyles"));
    let styles = TxStyles {
        title: parse_levels(tx_styles.and_then(|t| child(t, "titleStyle")), &col, &theme),
        body: parse_levels(tx_styles.and_then(|t| child(t, "bodyStyle")), &col, &theme),
        other: parse_levels(tx_styles.and_then(|t| child(t, "otherStyle")), &col, &theme),
    };
    let mut r =
        Renderer { deck, col: &col, theme: &theme, styles, layout: layout.as_ref(), master: master.as_ref(), number, writer, ops: Vec::new() };
    // Background: the slide's, else the layout's, else the master's; white by default.
    let mut bg_done = false;
    for p in [Some(&slide_part), layout.as_ref(), master.as_ref()].into_iter().flatten() {
        if let Some(bg) = path(p.root, &["cSld", "bg"]) {
            r.background(bg, p, sw, sh);
            bg_done = true;
            break;
        }
    }
    if !bg_done {
        r.ops.push(Op::Rect { x: 0.0, y: 0.0, w: sw, h: sh, fill: Some(WHITE), stroke: None });
    }
    let show = |p: &Part<'_, '_>| attr(p.root, "showMasterSp").is_none_or(|v| v != "0" && v != "false");
    let base = Xf { sx: EMU, sy: EMU, tx: 0.0, ty: 0.0 };
    if show(&slide_part)
        && layout.as_ref().is_none_or(show)
        && let Some(m) = master.as_ref()
        && let Some(tree) = m.tree()
    {
        r.tree(tree, m, Source::Master, base, 0);
    }
    if show(&slide_part)
        && let Some(l) = layout.as_ref()
        && let Some(tree) = l.tree()
    {
        r.tree(tree, l, Source::Layout, base, 0);
    }
    if let Some(tree) = slide_part.tree() {
        r.tree(tree, &slide_part, Source::Slide, base, 0);
    }
    Ok(r.ops)
}

struct TxStyles {
    title: Levels,
    body: Levels,
    other: Levels,
}

#[derive(Clone, Copy, PartialEq)]
enum Source {
    Slide,
    Layout,
    Master,
}

struct Renderer<'r, 'p, 'a, 'd, 'i> {
    deck: &'r mut Deck<'p, 'a>,
    col: &'r Colours<'r>,
    theme: &'r Theme,
    styles: TxStyles,
    layout: Option<&'r Part<'d, 'i>>,
    master: Option<&'r Part<'d, 'i>>,
    number: usize,
    writer: &'r mut Writer,
    ops: Vec<Op>,
}

impl<'d, 'i> Renderer<'_, '_, '_, 'd, 'i> {
    fn image(&mut self, part: &Part<'_, '_>, rid: &str) -> Option<usize> {
        let target = part.target(rid)?.to_string();
        let bytes = self.deck.pkg.bytes(&target)?;
        self.writer.image(&target, &bytes).map(|(id, _)| id)
    }

    fn background(&mut self, bg: Node<'_, '_>, part: &Part<'_, '_>, sw: f64, sh: f64) {
        if let Some(pr) = child(bg, "bgPr") {
            if let Some(blip) = path(pr, &["blipFill", "blip"]).and_then(|b| attr(b, "embed"))
                && let Some(id) = self.image(part, blip)
            {
                self.ops.push(Op::Rect { x: 0.0, y: 0.0, w: sw, h: sh, fill: Some(WHITE), stroke: None });
                self.ops.push(Op::Image { id, x: 0.0, y: 0.0, w: sw, h: sh });
                return;
            }
            let fill = match fill_of(pr, self.col, None) {
                Some(Fill::Solid(c)) => c,
                _ => WHITE,
            };
            self.ops.push(Op::Rect { x: 0.0, y: 0.0, w: sw, h: sh, fill: Some(fill), stroke: None });
        } else if let Some(r) = child(bg, "bgRef") {
            let c = self.col.inside(r, None).unwrap_or(WHITE);
            self.ops.push(Op::Rect { x: 0.0, y: 0.0, w: sw, h: sh, fill: Some(c), stroke: None });
        } else {
            self.ops.push(Op::Rect { x: 0.0, y: 0.0, w: sw, h: sh, fill: Some(WHITE), stroke: None });
        }
    }

    fn tree(&mut self, tree: Node<'_, '_>, part: &Part<'_, '_>, src: Source, xf: Xf, depth: usize) {
        if depth > MAX_GROUP_DEPTH {
            return;
        }
        for n in elements(tree) {
            match n.tag_name().name() {
                "sp" | "cxnSp" => self.shape(n, part, src, xf),
                "pic" => self.picture(n, part, src, xf),
                "grpSp" => {
                    let Some(x) = path(n, &["grpSpPr", "xfrm"]) else {
                        self.tree(n, part, src, xf, depth + 1);
                        continue;
                    };
                    let g = |a: &str, b: &str| child(x, a).and_then(|c| num(c, b));
                    let (Some(ox), Some(oy), Some(ex), Some(ey)) = (g("off", "x"), g("off", "y"), g("ext", "cx"), g("ext", "cy")) else {
                        self.tree(n, part, src, xf, depth + 1);
                        continue;
                    };
                    let (cx, cy) = (g("chOff", "x").unwrap_or(ox), g("chOff", "y").unwrap_or(oy));
                    let (cex, cey) =
                        (g("chExt", "cx").filter(|v| *v > 0.0).unwrap_or(ex.max(1.0)), g("chExt", "cy").filter(|v| *v > 0.0).unwrap_or(ey.max(1.0)));
                    let (kx, ky) = (ex / cex, ey / cey);
                    let (kx, ky) = (if kx.is_finite() { kx } else { 1.0 }, if ky.is_finite() { ky } else { 1.0 });
                    let inner = Xf { sx: xf.sx * kx, sy: xf.sy * ky, tx: xf.tx + xf.sx * (ox - cx * kx), ty: xf.ty + xf.sy * (oy - cy * ky) };
                    self.rotated(Some(x), xf, |r| r.tree(n, part, src, inner, depth + 1));
                }
                "graphicFrame" => self.graphic_frame(n, part, xf),
                "AlternateContent" => {
                    if let Some(branch) = child(n, "Fallback").or_else(|| child(n, "Choice")) {
                        self.tree(branch, part, src, xf, depth + 1);
                    }
                }
                _ => {}
            }
        }
    }

    /// Inherited placeholder shapes: the layout's and the master's.
    fn inherited(&self, ph: &(String, Option<String>), src: Source) -> (Option<Node<'d, 'i>>, Option<Node<'d, 'i>>) {
        let (ty, idx) = ph;
        let layout = if src == Source::Slide { self.layout.and_then(|l| find_placeholder(l, ty, idx.as_deref(), false)) } else { None };
        let master_type = layout.and_then(placeholder).map(|(t, _)| t).unwrap_or_else(|| ty.clone());
        let master = if src == Source::Master { None } else { self.master.and_then(|m| find_placeholder(m, &master_type, None, true)) };
        (layout, master)
    }

    /// A shape, rotated as its `xfrm` says.
    fn shape(&mut self, sp: Node<'_, '_>, part: &Part<'_, '_>, src: Source, xf: Xf) {
        let x = path(sp, &["spPr", "xfrm"]);
        self.rotated(x, xf, |r| r.shape_inner(sp, part, src, xf));
    }

    /// Run `draw` rotated about the centre of `xfrm` when it has a rotation.
    fn rotated(&mut self, x: Option<Node<'_, '_>>, xf: Xf, draw: impl FnOnce(&mut Self)) {
        let rot = rotation(x);
        match xfrm_of(x).map(|v| frame(&xf, v)) {
            Some(f) if rot.abs() > 0.01 => {
                self.ops.push(Op::Rotate { cx: f.x + f.w / 2.0, cy: f.y + f.h / 2.0, deg: rot });
                draw(self);
                self.ops.push(Op::Restore);
            }
            _ => draw(self),
        }
    }

    fn shape_inner(&mut self, sp: Node<'_, '_>, _part: &Part<'_, '_>, src: Source, xf: Xf) {
        let ph = placeholder(sp);
        // Placeholders on layouts and masters are prompts, not content.
        if ph.is_some() && src != Source::Slide {
            return;
        }
        let (lay, mas) = ph.as_ref().map(|p| self.inherited(p, src)).unwrap_or((None, None));
        let sppr = child(sp, "spPr");
        let geom_xfrm = xfrm_of(sppr.and_then(|s| child(s, "xfrm")))
            .or_else(|| xfrm_of(lay.and_then(|l| path(l, &["spPr", "xfrm"]))))
            .or_else(|| xfrm_of(mas.and_then(|m| path(m, &["spPr", "xfrm"]))));
        let Some(v) = geom_xfrm else { return };
        let f = frame(&xf, v);
        let style = child(sp, "style");
        // Fill and outline.
        let fill = sppr
            .and_then(|s| fill_of(s, self.col, None))
            .or_else(|| {
                style.and_then(|st| child(st, "fillRef")).and_then(|r| {
                    let idx = num(r, "idx").unwrap_or(0.0);
                    (idx >= 1.0).then(|| self.col.inside(r, None).map_or(Fill::None, Fill::Solid))
                })
            })
            .unwrap_or(Fill::None);
        let ln = sppr.and_then(|s| child(s, "ln"));
        let line_colour = match ln {
            Some(l) if child(l, "noFill").is_some() => None,
            Some(l) => child(l, "solidFill").and_then(|f| self.col.inside(f, None)).or_else(|| {
                style.and_then(|st| child(st, "lnRef")).filter(|r| num(*r, "idx").unwrap_or(0.0) >= 1.0).and_then(|r| self.col.inside(r, None))
            }),
            None => style.and_then(|st| child(st, "lnRef")).filter(|r| num(*r, "idx").unwrap_or(0.0) >= 1.0).and_then(|r| self.col.inside(r, None)),
        };
        let line_w = ln.and_then(|l| num(l, "w")).map_or(0.75, |w| (w * EMU).clamp(0.1, 50.0));
        let stroke = line_colour.map(|c| (c, line_w));
        let geom = sppr.and_then(|s| child(s, "prstGeom"));
        let is_connector = sp.tag_name().name() == "cxnSp";
        let segs = if let Some(cg) = sppr.and_then(|s| child(s, "custGeom")) {
            custom(cg, &f)
        } else {
            let name = geom.and_then(|g| attr(g, "prst")).unwrap_or(if is_connector { "line" } else { "rect" });
            let adj = geom
                .and_then(|g| child(g, "avLst"))
                .and_then(|a| child(a, "gd"))
                .and_then(|gd| attr(gd, "fmla"))
                .and_then(|f| f.strip_prefix("val "))
                .and_then(|v| v.trim().parse::<f64>().ok())
                .map(|v| v / 100_000.0);
            preset(name, &f, adj)
        };
        let fill_rgb = match fill {
            Fill::Solid(c) => Some(c),
            Fill::None => None,
        };
        let open = is_connector || geom.and_then(|g| attr(g, "prst")).is_some_and(|p| p == "line" || p.contains("Connector"));
        if fill_rgb.is_some() || stroke.is_some() {
            self.ops.push(Op::Path { segs, fill: if open { None } else { fill_rgb }, stroke });
        }
        // Text.
        let Some(body) = child(sp, "txBody") else { return };
        let font_ref = style.and_then(|st| child(st, "fontRef")).and_then(|r| self.col.inside(r, None));
        let body_pr = [mas, lay]
            .iter()
            .flatten()
            .fold(BodyOverrides::default(), |acc, n| acc.over(parse_body(path(*n, &["txBody", "bodyPr"]))))
            .over(parse_body(child(body, "bodyPr")))
            .resolve();
        // Level styles, most general first.
        let ty = ph.as_ref().map(|p| p.0.as_str());
        let base_levels: &Levels = match ty.map(ph_family) {
            Some("title") => &self.styles.title,
            Some("body") => &self.styles.body,
            Some(_) => &self.styles.other,
            None => &self.deck.default_text,
        };
        let mut chain: Vec<Levels> = vec![self.deck.default_text.clone(), base_levels.clone()];
        // A shape style's font colour (white text on accent-filled shapes) comes before the
        // shape's own list style and runs.
        if let Some(c) = font_ref {
            chain.push(coloured_levels(c, false));
        }
        for n in [mas, lay].into_iter().flatten() {
            chain.push(parse_levels(path(n, &["txBody", "lstStyle"]), self.col, self.theme));
        }
        chain.push(parse_levels(child(body, "lstStyle"), self.col, self.theme));
        let default_colour = font_ref;
        self.text_body(body, &chain, body_pr, &f, default_colour);
    }

    fn text_body(&mut self, body: Node<'_, '_>, chain: &[Levels], bp: BodyPr, f: &Frame, default_colour: Option<Rgb>) {
        let (l, t, r, b) = bp.insets;
        let inner_w = (f.w - l - r).max(4.0);
        let mut items: Vec<VItem> = Vec::new();
        let mut autonum: HashMap<usize, i64> = HashMap::new();
        let mut first = true;
        let mut natural_widest: f64 = 0.0;
        let mut paras = Vec::new();
        for p in children(body, "p") {
            let ppr_node = child(p, "pPr");
            let lvl = ppr_node.and_then(|n| num(n, "lvl")).map_or(0, |v| v.clamp(0.0, 8.0) as usize);
            let mut pp = PProps::default();
            for levels in chain {
                if let Some(x) = levels.get(lvl) {
                    pp.merge(x);
                }
            }
            pp.merge(&parse_ppr(ppr_node, self.col, self.theme));
            let mut inlines = Vec::new();
            let mut first_size = None;
            for c in elements(p) {
                let name = c.tag_name().name();
                if !matches!(name, "r" | "fld" | "br") {
                    continue;
                }
                let mut rp = pp.def.clone();
                rp.merge(&parse_rpr(child(c, "rPr"), self.col, self.theme));
                let style = self.style(&rp, default_colour, bp.font_scale);
                first_size.get_or_insert(style.size);
                match name {
                    "br" => inlines.push(Inline::Break),
                    _ => {
                        let mut text = child(c, "t").and_then(|t| t.text()).unwrap_or_default().to_string();
                        if name == "fld" && attr(c, "type").is_some_and(|t| t == "slidenum") {
                            text = self.number.to_string();
                        }
                        if rp.caps == Some(true) {
                            text = text.to_uppercase();
                        }
                        inlines.push(Inline::Text(text, style));
                    }
                }
            }
            let mut end = pp.def.clone();
            end.merge(&parse_rpr(child(p, "endParaRPr"), self.col, self.theme));
            let end_style = self.style(&end, default_colour, bp.font_scale);
            let size = first_size.unwrap_or(end_style.size);
            let has_text = inlines.iter().any(|i| matches!(i, Inline::Text(t, _) if !t.trim().is_empty()));
            let label = match (&pp.bullet, has_text) {
                (Some(Bullet::Char(ch)), true) => {
                    let mut s =
                        inlines.iter().find_map(|i| if let Inline::Text(_, s) = i { Some(s.clone()) } else { None }).unwrap_or(end_style.clone());
                    s.size *= pp.bullet_size.unwrap_or(1.0);
                    s.underline = false;
                    s.strike = false;
                    if let Some(c) = pp.bullet_colour {
                        s.color = c;
                    }
                    let ch = ch.chars().next().map_or('\u{2022}', |c| {
                        if pp.bullet_symbol == Some(true) || ('\u{F000}'..='\u{F0FF}').contains(&c) { '\u{2022}' } else { c }
                    });
                    Some((ch.to_string(), s))
                }
                (Some(Bullet::Auto(scheme, start)), true) => {
                    let n = autonum.entry(lvl).or_insert(*start - 1);
                    *n += 1;
                    let s = inlines.iter().find_map(|i| if let Inline::Text(_, s) = i { Some(s.clone()) } else { None }).unwrap_or(end_style.clone());
                    Some((auto_label(scheme, *n), s))
                }
                _ => {
                    autonum.remove(&lvl);
                    None
                }
            };
            // Deeper levels restart their numbering.
            autonum.retain(|k, _| *k <= lvl);
            let line_mult = match pp.line {
                Some(Spacing::Pct(p)) => LineRule::Auto((p * (1.0 - bp.line_reduction)).max(0.3)),
                Some(Spacing::Pts(v)) => LineRule::Exact(v * bp.font_scale),
                None => LineRule::Auto((1.0 - bp.line_reduction).max(0.3)),
            };
            let sp = |s: Option<Spacing>| match s {
                Some(Spacing::Pts(v)) => v * bp.font_scale,
                Some(Spacing::Pct(p)) => p * size * 1.2,
                None => 0.0,
            };
            let mar = pp.mar_l.unwrap_or(0.0);
            let para = Para {
                inlines,
                align: pp.align.unwrap_or(Align::Left),
                left: mar,
                right: 0.0,
                first: pp.indent.unwrap_or(0.0),
                before: if first { 0.0 } else { sp(pp.before) },
                after: sp(pp.after),
                line: line_mult,
                label,
                mark_size: end_style.size,
                ..Default::default()
            };
            first = false;
            if !bp.wrap {
                let w: f64 = para
                    .inlines
                    .iter()
                    .map(|i| if let Inline::Text(t, s) = i { super::draw::text_width(s.face, s.size, &super::draw::drawable_text(t)) } else { 0.0 })
                    .sum::<f64>()
                    + mar
                    + 2.0;
                natural_widest = natural_widest.max(w);
            }
            paras.push(para);
        }
        let width = if bp.wrap { inner_w } else { inner_w.max(natural_widest) };
        let x0 = if !bp.wrap && width > inner_w { f.x + l - (width - inner_w) * self.overflow_shift(&paras) } else { f.x + l };
        for p in &paras {
            items.extend(flow::layout_para(p, width));
        }
        let total: f64 = items
            .iter()
            .map(|i| match i {
                VItem::Box(b) => b.h,
                VItem::Gap(g) => *g,
                _ => 0.0,
            })
            .sum();
        let inner_h = f.h - t - b;
        let mut y = f.y
            + t
            + match bp.anchor {
                Anchor::Top => 0.0,
                Anchor::Middle => (inner_h - total) / 2.0,
                Anchor::Bottom => inner_h - total,
            };
        for it in items {
            match it {
                VItem::Box(bx) => {
                    super::draw::place(&mut self.ops, &bx.ops, x0, y);
                    y += bx.h;
                }
                VItem::Gap(g) => y += g,
                _ => {}
            }
        }
    }

    /// For unwrapped text wider than its box: how much of the overflow goes left (centred text
    /// spills both ways, right-aligned text to the left).
    fn overflow_shift(&self, paras: &[Para]) -> f64 {
        match paras.first().map(|p| p.align) {
            Some(Align::Center) => 0.5,
            Some(Align::Right) => 1.0,
            _ => 0.0,
        }
    }

    fn style(&self, rp: &RProps, default_colour: Option<Rgb>, scale: f64) -> Style {
        let font = rp.font.clone().unwrap_or_else(|| self.theme.minor.clone());
        let mut size = rp.size.unwrap_or(18.0) * scale;
        let mut shift = 0.0;
        if let Some(b) = rp.baseline.filter(|b| b.abs() > 0.01) {
            shift = size * b;
            size *= 0.67;
        }
        Style {
            face: Face { family: family_of(&font), bold: rp.bold.unwrap_or(false), italic: rp.italic.unwrap_or(false) },
            size: size.max(1.0),
            color: rp.colour.or(default_colour).unwrap_or(BLACK),
            underline: rp.underline.unwrap_or(false),
            strike: rp.strike.unwrap_or(false),
            highlight: None,
            shift,
        }
    }

    fn picture(&mut self, pic: Node<'_, '_>, part: &Part<'_, '_>, src: Source, xf: Xf) {
        let x = path(pic, &["spPr", "xfrm"]);
        self.rotated(x, xf, |r| r.picture_inner(pic, part, src, xf));
    }

    fn picture_inner(&mut self, pic: Node<'_, '_>, part: &Part<'_, '_>, src: Source, xf: Xf) {
        let ph = placeholder(pic);
        if ph.is_some() && src != Source::Slide {
            return;
        }
        let (lay, mas) = ph.as_ref().map(|p| self.inherited(p, src)).unwrap_or((None, None));
        let v = xfrm_of(path(pic, &["spPr", "xfrm"]))
            .or_else(|| xfrm_of(lay.and_then(|l| path(l, &["spPr", "xfrm"]))))
            .or_else(|| xfrm_of(mas.and_then(|m| path(m, &["spPr", "xfrm"]))));
        let Some(v) = v else { return };
        let f = frame(&xf, v);
        let Some(fill) = child(pic, "blipFill") else { return };
        let Some(rid) = child(fill, "blip").and_then(|b| attr(b, "embed")) else { return };
        let Some(id) = self.image(part, rid) else { return };
        // Cropping (`srcRect`, in thousandths of a percent of the picture).
        let crop = child(fill, "srcRect");
        let c = |a: &str| crop.and_then(|s| num(s, a)).map_or(0.0, |v| (v / 100_000.0).clamp(-10.0, 0.99));
        let (cl, ct, cr, cb) = (c("l"), c("t"), c("r"), c("b"));
        let (fw, fh) = (1.0 - cl - cr, 1.0 - ct - cb);
        if fw <= 0.01 || fh <= 0.01 {
            return;
        }
        let (iw, ih) = (f.w / fw, f.h / fh);
        self.ops.push(Op::Clip { x: f.x, y: f.y, w: f.w, h: f.h });
        self.ops.push(Op::Image { id, x: f.x - cl * iw, y: f.y - ct * ih, w: iw, h: ih });
        self.ops.push(Op::Restore);
        if let Some(ln) = path(pic, &["spPr", "ln"])
            && let Some(c) = child(ln, "solidFill").and_then(|s| self.col.inside(s, None))
        {
            let w = num(ln, "w").map_or(0.75, |w| (w * EMU).clamp(0.1, 50.0));
            self.ops.push(Op::Rect { x: f.x, y: f.y, w: f.w, h: f.h, fill: None, stroke: Some((c, w)) });
        }
    }

    fn graphic_frame(&mut self, gf: Node<'_, '_>, _part: &Part<'_, '_>, xf: Xf) {
        let Some(v) = xfrm_of(child(gf, "xfrm")) else { return };
        let f = frame(&xf, v);
        let Some(tbl) = path(gf, &["graphic", "graphicData", "tbl"]) else { return };
        let tpr = child(tbl, "tblPr");
        let styled = tpr.and_then(|t| child(t, "tableStyleId")).is_some();
        let first_row = tpr.and_then(|t| attr(t, "firstRow")).is_some_and(|v| v == "1");
        let band = tpr.and_then(|t| attr(t, "bandRow")).is_some_and(|v| v == "1");
        let accent = self.theme.colours.get("accent1").copied().unwrap_or(Rgb(0x44, 0x72, 0xC4));
        let cols: Vec<f64> = child(tbl, "tblGrid")
            .map(|g| children(g, "gridCol").map(|c| num(c, "w").unwrap_or(0.0) * EMU * xf.sx / EMU).collect())
            .unwrap_or_default();
        if cols.is_empty() {
            return;
        }
        let mut starts = vec![0.0];
        for w in &cols {
            let last = starts.last().copied().unwrap_or(0.0);
            starts.push(last + w);
        }
        let mut y = f.y;
        let rows: Vec<Node<'_, '_>> = children(tbl, "tr").collect();
        for (ri, tr) in rows.iter().enumerate() {
            let min_h = num(*tr, "h").map_or(0.0, |h| h * EMU * xf.sy / EMU);
            // Lay out every cell first to find the row height.
            let mut cells = Vec::new();
            let mut g = 0usize;
            for tc in children(*tr, "tc") {
                let span = num(tc, "gridSpan").map_or(1, |v| v.clamp(1.0, 64.0) as usize);
                let merged = attr(tc, "hMerge").is_some_and(|v| v == "1") || attr(tc, "vMerge").is_some_and(|v| v == "1");
                let (Some(&a), Some(&b)) = (starts.get(g.min(cols.len())), starts.get((g + span).min(cols.len()))) else { break };
                g += span;
                if merged {
                    continue;
                }
                cells.push((tc, a, b - a));
            }
            let mut laid = Vec::new();
            let mut row_h: f64 = min_h;
            for (tc, x, w) in &cells {
                let pr = child(*tc, "tcPr");
                let m = |a: &str, d: f64| pr.and_then(|p| num(p, a)).map_or(d, |v| v * EMU);
                let (ml, mr, mt, mb) = (m("marL", 7.2), m("marR", 7.2), m("marT", 3.6), m("marB", 3.6));
                let header = styled && first_row && ri == 0;
                let mut default_colour = None;
                let mut fill = pr.and_then(|p| fill_of(p, self.col, None));
                if fill.is_none() && styled {
                    fill = Some(Fill::Solid(if header {
                        default_colour = Some(WHITE);
                        accent
                    } else {
                        let body_i = if first_row { ri.saturating_sub(1) } else { ri };
                        if band && body_i % 2 == 0 { accent.tint(0.6) } else { accent.tint(0.8) }
                    }));
                } else if header {
                    default_colour = Some(WHITE);
                }
                let mut sub = Vec::new();
                std::mem::swap(&mut sub, &mut self.ops);
                let frame = Frame { x: x + f.x, y: 0.0, w: *w, h: 0.0, flip_h: false, flip_v: false };
                let bp = BodyPr { insets: (ml, mt, mr, mb), anchor: Anchor::Top, wrap: true, font_scale: 1.0, line_reduction: 0.0 };
                let mut levels = vec![self.deck.default_text.clone()];
                if header {
                    levels.push(coloured_levels(default_colour.unwrap_or(WHITE), true));
                }
                if let Some(body) = child(*tc, "txBody") {
                    self.text_body(body, &levels, bp, &frame, default_colour);
                }
                std::mem::swap(&mut sub, &mut self.ops);
                // Content height: the lowest text baseline plus a descent, and the margins.
                let bottom =
                    sub.iter().filter_map(|o| if let Op::Text { y, size, .. } = o { Some(y + size * 0.3) } else { None }).fold(0.0, f64::max);
                let h = if bottom > 0.0 { bottom + mb } else { mt + mb + 14.0 };
                row_h = row_h.max(h);
                let anchor = match pr.and_then(|p| attr(p, "anchor")) {
                    Some("ctr") => Anchor::Middle,
                    Some("b") => Anchor::Bottom,
                    _ => Anchor::Top,
                };
                laid.push((*x, *w, fill, sub, h, anchor, pr));
            }
            for (x, w, fill, sub, h, anchor, pr) in laid {
                let cx = f.x + x;
                if let Some(Fill::Solid(c)) = fill {
                    self.ops.push(Op::Rect { x: cx, y, w, h: row_h, fill: Some(c), stroke: None });
                }
                let dy = y + match anchor {
                    Anchor::Top => 0.0,
                    Anchor::Middle => (row_h - h) / 2.0,
                    Anchor::Bottom => row_h - h,
                };
                for mut op in sub {
                    op.offset(0.0, dy);
                    self.ops.push(op);
                }
                // Cell borders: explicit lines, else white rules for styled tables.
                let mut drew = false;
                for (side, (x1, y1, x2, y2)) in [
                    ("lnL", (cx, y, cx, y + row_h)),
                    ("lnR", (cx + w, y, cx + w, y + row_h)),
                    ("lnT", (cx, y, cx + w, y)),
                    ("lnB", (cx, y + row_h, cx + w, y + row_h)),
                ] {
                    if let Some(ln) = pr.and_then(|p| child(p, side))
                        && child(ln, "noFill").is_none()
                        && let Some(c) = child(ln, "solidFill").and_then(|s| self.col.inside(s, None))
                    {
                        let wd = num(ln, "w").map_or(0.75, |v| (v * EMU).clamp(0.1, 20.0));
                        self.ops.push(Op::Line { x1, y1, x2, y2, color: c, width: wd });
                        drew = true;
                    }
                }
                if !drew && styled {
                    self.ops.push(Op::Rect { x: cx, y, w, h: row_h, fill: None, stroke: Some((WHITE, 1.0)) });
                }
            }
            y += row_h;
        }
    }
}

/// List-style levels that only set the text colour (and bold).
fn coloured_levels(c: Rgb, bold: bool) -> Levels {
    let mut levels = vec![PProps::default(); 9];
    for l in &mut levels {
        l.def.colour = Some(c);
        if bold {
            l.def.bold = Some(true);
        }
    }
    levels
}

fn auto_label(scheme: &str, n: i64) -> String {
    let (body, wrap) = if let Some(b) = scheme.strip_suffix("ParenBoth") {
        (b, ("(", ")"))
    } else if let Some(b) = scheme.strip_suffix("ParenR") {
        (b, ("", ")"))
    } else if let Some(b) = scheme.strip_suffix("Period") {
        (b, ("", "."))
    } else if let Some(b) = scheme.strip_suffix("Plain") {
        (b, ("", ""))
    } else {
        (scheme, ("", "."))
    };
    let v = match body {
        "alphaLc" => super::docx::format_number(n, "lowerLetter"),
        "alphaUc" => super::docx::format_number(n, "upperLetter"),
        "romanLc" => super::docx::roman(n),
        "romanUc" => super::docx::roman(n).to_uppercase(),
        _ => n.to_string(),
    };
    format!("{}{v}{}", wrap.0, wrap.1)
}

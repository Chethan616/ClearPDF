//! Excel workbooks (.xlsx, ECMA-376 Part 1 §18 SpreadsheetML) to grids on pages.

use std::collections::HashMap;

use roxmltree::Node;

use super::Package;
use super::draw::{BLACK, Face, Op, Rgb, WHITE, Writer, drawable_text, family_of, text_width};
use super::xml::{attr, child, children, elements, num, on_off, parse_str, path, relationships};

/// Most cells read from one sheet.
const MAX_CELLS: usize = 1_000_000;
/// Largest used range drawn (rows, columns).
const MAX_ROWS: u32 = 1_048_576;
const MAX_COLS: u32 = 16_384;

#[derive(Clone, Debug, Default)]
struct FontDef {
    bold: bool,
    italic: bool,
    underline: bool,
    strike: bool,
    size: f64,
    color: Option<Rgb>,
    name: String,
}

#[derive(Clone, Copy, Debug, Default, PartialEq)]
enum HAlign {
    #[default]
    General,
    Left,
    Center,
    Right,
}

#[derive(Clone, Copy, Debug, Default, PartialEq)]
enum VAlign {
    #[default]
    Bottom,
    Center,
    Top,
}

#[derive(Clone, Debug, Default)]
struct Xf {
    num_fmt: u32,
    font: usize,
    fill: usize,
    border: usize,
    h: HAlign,
    v: VAlign,
    wrap: bool,
    indent: f64,
}

type Side = Option<(Rgb, f64)>;

#[derive(Default)]
struct StyleSheet {
    num_fmts: HashMap<u32, String>,
    fonts: Vec<FontDef>,
    fills: Vec<Option<Rgb>>,
    borders: Vec<[Side; 4]>,
    xfs: Vec<Xf>,
}

/// The default legacy palette (ECMA-376 Part 1 §18.8.27), for `indexed` colours.
const PALETTE: [u32; 64] = [
    0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00,
    0xFF00FF, 0x00FFFF, 0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080, 0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF,
    0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF, 0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF, 0x00CCFF, 0xCCFFFF,
    0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99, 0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
    0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333,
];

/// Theme colours in SpreadsheetML index order (lt1, dk1, lt2, dk2, accent1–6, hlink, folHlink).
fn theme_colours(text: Option<&str>) -> Vec<Rgb> {
    let mut out = vec![
        WHITE,
        BLACK,
        Rgb(0xE7, 0xE6, 0xE6),
        Rgb(0x44, 0x54, 0x6A),
        Rgb(0x44, 0x72, 0xC4),
        Rgb(0xED, 0x7D, 0x31),
        Rgb(0xA5, 0xA5, 0xA5),
        Rgb(0xFF, 0xC0, 0x00),
        Rgb(0x5B, 0x9B, 0xD5),
        Rgb(0x70, 0xAD, 0x47),
        Rgb(0x05, 0x63, 0xC1),
        Rgb(0x95, 0x4F, 0x72),
    ];
    let Some(doc) = text.and_then(parse_str) else { return out };
    let Some(scheme) = doc.descendants().find(|n| n.tag_name().name() == "clrScheme") else { return out };
    let order = ["lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink"];
    for (i, name) in order.iter().enumerate() {
        if let Some(c) = child(scheme, name).and_then(|n| elements(n).next()) {
            let rgb = match c.tag_name().name() {
                "srgbClr" => attr(c, "val").and_then(Rgb::hex),
                "sysClr" => attr(c, "lastClr").and_then(Rgb::hex),
                _ => None,
            };
            if let (Some(rgb), Some(slot)) = (rgb, out.get_mut(i)) {
                *slot = rgb;
            }
        }
    }
    out
}

fn colour(n: Node<'_, '_>, theme: &[Rgb]) -> Option<Rgb> {
    let base = if let Some(rgb) = attr(n, "rgb") {
        Rgb::hex(rgb)
    } else if let Some(t) = num(n, "theme") {
        theme.get(t.clamp(0.0, 64.0) as usize).copied()
    } else if let Some(i) = num(n, "indexed") {
        let i = i.clamp(0.0, 200.0) as usize;
        if i == 64 || i == 65 {
            return None; // system foreground/background: the defaults.
        }
        PALETTE.get(i).map(|v| Rgb((v >> 16) as u8, (v >> 8) as u8, *v as u8))
    } else {
        None
    }?;
    Some(match num(n, "tint") {
        Some(t) if t != 0.0 => base.tint(t),
        _ => base,
    })
}

impl StyleSheet {
    fn parse(text: Option<&str>, theme: &[Rgb]) -> StyleSheet {
        let mut s = StyleSheet::default();
        let Some(doc) = text.and_then(parse_str) else { return s };
        let root = doc.root_element();
        if let Some(nf) = child(root, "numFmts") {
            for f in children(nf, "numFmt") {
                if let (Some(id), Some(code)) = (num(f, "numFmtId"), attr(f, "formatCode")) {
                    s.num_fmts.insert(id.clamp(0.0, 1e6) as u32, code.to_string());
                }
            }
        }
        if let Some(fonts) = child(root, "fonts") {
            for f in children(fonts, "font") {
                let mut d = FontDef { size: 11.0, ..Default::default() };
                for c in elements(f) {
                    match c.tag_name().name() {
                        "b" => d.bold = on_off(c),
                        "i" => d.italic = on_off(c),
                        "u" => d.underline = attr(c, "val") != Some("none"),
                        "strike" => d.strike = on_off(c),
                        "sz" => d.size = num(c, "val").map_or(11.0, |v| v.clamp(1.0, 409.0)),
                        "color" => d.color = colour(c, theme),
                        "name" => d.name = attr(c, "val").unwrap_or_default().to_string(),
                        _ => {}
                    }
                }
                s.fonts.push(d);
            }
        }
        if let Some(fills) = child(root, "fills") {
            for f in children(fills, "fill") {
                let fill = child(f, "patternFill").and_then(|p| {
                    let ty = attr(p, "patternType").unwrap_or("none");
                    if ty == "none" {
                        return None;
                    }
                    let fg = child(p, "fgColor").and_then(|c| colour(c, theme));
                    if ty == "solid" { fg } else { fg.map(|c| c.tint(0.5)) }
                });
                // Gradient fills: their first stop.
                let fill = fill.or_else(|| {
                    child(f, "gradientFill").and_then(|g| g.descendants().find(|n| n.tag_name().name() == "color")).and_then(|c| colour(c, theme))
                });
                s.fills.push(fill);
            }
        }
        if let Some(borders) = child(root, "borders") {
            for b in children(borders, "border") {
                let side = |names: &[&str]| -> Side {
                    let n = names.iter().find_map(|s| child(b, s))?;
                    let style = attr(n, "style")?;
                    let width = match style {
                        "none" => return None,
                        "medium" | "mediumDashed" | "mediumDashDot" | "mediumDashDotDot" | "slantDashDot" => 1.0,
                        "thick" | "double" => 1.5,
                        "hair" => 0.25,
                        _ => 0.5,
                    };
                    Some((child(n, "color").and_then(|c| colour(c, theme)).unwrap_or(BLACK), width))
                };
                s.borders.push([side(&["top"]), side(&["right", "end"]), side(&["bottom"]), side(&["left", "start"])]);
            }
        }
        if let Some(xfs) = child(root, "cellXfs") {
            for x in children(xfs, "xf") {
                let idx = |a: &str| num(x, a).map_or(0, |v| v.clamp(0.0, 1e6) as usize);
                let al = child(x, "alignment");
                s.xfs.push(Xf {
                    num_fmt: num(x, "numFmtId").map_or(0, |v| v.clamp(0.0, 1e6) as u32),
                    font: idx("fontId"),
                    fill: idx("fillId"),
                    border: idx("borderId"),
                    h: match al.and_then(|a| attr(a, "horizontal")) {
                        Some("left") => HAlign::Left,
                        Some("center" | "centerContinuous") => HAlign::Center,
                        Some("right") => HAlign::Right,
                        _ => HAlign::General,
                    },
                    v: match al.and_then(|a| attr(a, "vertical")) {
                        Some("center") => VAlign::Center,
                        Some("top") => VAlign::Top,
                        _ => VAlign::Bottom,
                    },
                    wrap: al.and_then(|a| attr(a, "wrapText")).is_some_and(|v| v == "1" || v == "true"),
                    indent: al.and_then(|a| num(a, "indent")).map_or(0.0, |v| v.clamp(0.0, 50.0) * 9.0),
                });
            }
        }
        s
    }
}

// ── values and number formats ───────────────────────────────────────────────────────────────

#[derive(Clone, Debug)]
enum Value {
    Num(f64),
    Text(String),
    Bool(bool),
    Error(String),
}

/// A date serial number as (year, month, day, seconds into the day).
fn date_parts(serial: f64, date1904: bool) -> Option<(i64, u32, u32, f64)> {
    if !serial.is_finite() || !(0.0..3_000_000.0).contains(&serial) {
        return None;
    }
    let days = serial.floor() as i64;
    let secs = ((serial - serial.floor()) * 86_400.0).round();
    let (days, secs) = if secs >= 86_400.0 { (days + 1, 0.0) } else { (days, secs) };
    // 1904 system: day 0 is 1904-01-01. 1900 system: day 1 is 1900-01-01, and Excel keeps
    // Lotus's 1900-02-29 (serial 60), so later serials are one day ahead.
    let unix = if date1904 {
        days - 24_107
    } else if days == 60 {
        return Some((1900, 2, 29, secs));
    } else {
        (if days > 60 { days - 1 } else { days }) - 25_568
    };
    let (y, m, d) = civil(unix);
    Some((y, m, d, secs))
}

/// Days since 1970-01-01 → (year, month, day). (H. Hinnant's algorithm.)
fn civil(z: i64) -> (i64, u32, u32) {
    let z = z + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let m = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    (if m <= 2 { y + 1 } else { y }, m, d)
}

const MONTHS: [&str; 12] = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
const DAYS: [&str; 7] = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

fn builtin_format(id: u32) -> Option<&'static str> {
    Some(match id {
        0 => "General",
        1 => "0",
        2 => "0.00",
        3 => "#,##0",
        4 => "#,##0.00",
        5 => "#,##0 ;(#,##0)",
        6 => "#,##0 ;[Red](#,##0)",
        7 => "#,##0.00;(#,##0.00)",
        8 => "#,##0.00;[Red](#,##0.00)",
        9 => "0%",
        10 => "0.00%",
        11 => "0.00E+00",
        12 => "# ?/?",
        13 => "# ??/??",
        14 => "yyyy-mm-dd",
        15 => "d-mmm-yy",
        16 => "d-mmm",
        17 => "mmm-yy",
        18 => "h:mm AM/PM",
        19 => "h:mm:ss AM/PM",
        20 => "h:mm",
        21 => "h:mm:ss",
        22 => "yyyy-mm-dd h:mm",
        37 => "#,##0 ;(#,##0)",
        38 => "#,##0 ;[Red](#,##0)",
        39 => "#,##0.00;(#,##0.00)",
        40 => "#,##0.00;[Red](#,##0.00)",
        45 => "mm:ss",
        46 => "[h]:mm:ss",
        47 => "mm:ss.0",
        48 => "##0.0E+0",
        49 => "@",
        _ => return None,
    })
}

/// "General": integers as they are, other numbers with up to 10 significant digits.
fn general(v: f64) -> String {
    general_with(v, 10)
}

/// "General" with at most `sig` significant digits (Excel shortens General numbers to fit their
/// column).
fn general_with(v: f64, sig: i32) -> String {
    let sig = sig.clamp(1, 15);
    if !v.is_finite() {
        return "#NUM!".into();
    }
    let a = v.abs();
    if v == v.trunc() && a < 10f64.powi(sig + 1) {
        return format!("{}", v as i64);
    }
    if !(1e-9..1e11).contains(&a) || (a >= 1.0 && a.log10().floor() as i32 + 1 > sig) {
        let s = format!("{v:.*E}", (sig - 5).clamp(0, 5) as usize);
        // 1.23450E3 → 1.2345E+03
        if let Some((m, e)) = s.split_once('E') {
            let m = if m.contains('.') { m.trim_end_matches('0').trim_end_matches('.') } else { m };
            let e: i32 = e.parse().unwrap_or(0);
            return format!("{m}E{}{:02}", if e < 0 { '-' } else { '+' }, e.abs());
        }
        return s;
    }
    let digits = sig - (a.log10().floor() as i32 + 1);
    let s = format!("{:.*}", digits.clamp(0, 10) as usize, v);
    if s.contains('.') { s.trim_end_matches('0').trim_end_matches('.').to_string() } else { s }
}

fn group_thousands(int: &str) -> String {
    let mut out = String::new();
    let n = int.len();
    for (i, c) in int.chars().enumerate() {
        if i > 0 && (n - i).is_multiple_of(3) {
            out.push(',');
        }
        out.push(c);
    }
    out
}

/// Format `v` with a number format code: the common numeric patterns (decimals, thousands,
/// percent, scientific, literal text and currency), and dates and times. Anything else falls
/// back to General.
fn format_value(v: f64, code: &str, date1904: bool) -> String {
    if code.eq_ignore_ascii_case("general") || code.is_empty() {
        return general(v);
    }
    // Sections: positive;negative;zero;text.
    let sections: Vec<&str> = split_sections(code);
    let (section, negative_section) = if v < 0.0 && sections.len() >= 2 {
        (sections.get(1).copied().unwrap_or(code), true)
    } else if v == 0.0 && sections.len() >= 3 {
        (sections.get(2).copied().unwrap_or(code), false)
    } else {
        (sections.first().copied().unwrap_or(code), false)
    };
    // Strip colours, conditions and locale tags, keeping a currency symbol from [$€-407].
    let mut clean = String::new();
    let mut literal = false;
    let mut chars = section.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '"' => {
                literal = !literal;
                clean.push(c);
            }
            '[' if !literal => {
                let mut tag = String::new();
                for d in chars.by_ref() {
                    if d == ']' {
                        break;
                    }
                    tag.push(d);
                }
                if let Some(cur) = tag.strip_prefix('$') {
                    let sym = cur.split('-').next().unwrap_or_default();
                    if !sym.is_empty() {
                        clean.push('"');
                        clean.push_str(sym);
                        clean.push('"');
                    }
                } else if tag.chars().all(|c| matches!(c, 'h' | 'H' | 'm' | 'M' | 's' | 'S')) {
                    clean.push_str(&tag.to_ascii_lowercase());
                }
            }
            '_' if !literal => {
                chars.next();
                clean.push(' ');
            }
            '*' if !literal => {
                chars.next();
            }
            '\\' if !literal => {
                if let Some(d) = chars.next() {
                    clean.push('"');
                    clean.push(d);
                    clean.push('"');
                }
            }
            _ => clean.push(c),
        }
    }
    let unquoted: String = {
        let mut s = String::new();
        let mut q = false;
        for c in clean.chars() {
            if c == '"' {
                q = !q;
            } else if !q {
                s.push(c);
            }
        }
        s
    };
    let lower = unquoted.to_ascii_lowercase();
    if lower.contains('y')
        || lower.contains('d')
        || lower.contains('h')
        || lower.contains('s')
        || (lower.contains('m') && !lower.contains('0') && !lower.contains('#'))
    {
        return format_date(v, &clean, date1904).unwrap_or_else(|| general(v));
    }
    if unquoted.trim() == "@" {
        return general(v);
    }
    let v = if negative_section { v.abs() } else { v };
    format_number(v, &clean)
}

fn split_sections(code: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let mut start = 0;
    let mut q = false;
    for (i, c) in code.char_indices() {
        match c {
            '"' => q = !q,
            ';' if !q => {
                out.push(code.get(start..i).unwrap_or_default());
                start = i + 1;
            }
            _ => {}
        }
    }
    out.push(code.get(start..).unwrap_or_default());
    out
}

fn format_number(v: f64, code: &str) -> String {
    // Find the numeric placeholder region.
    let mut prefix = String::new();
    let mut suffix = String::new();
    let mut pattern = String::new();
    let mut q = false;
    let mut seen_digits = false;
    for c in code.chars() {
        if c == '"' {
            q = !q;
            continue;
        }
        let placeholder =
            !q && matches!(c, '0' | '#' | '?' | '.' | ',' | 'E' | 'e' | '+' | '-') && (seen_digits || matches!(c, '0' | '#' | '?' | '.'));
        let placeholder = placeholder && !(matches!(c, '+' | '-') && !pattern.ends_with(['E', 'e']));
        if placeholder {
            seen_digits = true;
            if !suffix.is_empty() {
                // Text between placeholders: keep it after.
                pattern.push_str(&suffix);
                suffix.clear();
            }
            pattern.push(c);
        } else if seen_digits {
            suffix.push(c);
        } else {
            prefix.push(c);
        }
    }
    if pattern.is_empty() {
        return format!("{prefix}{suffix}");
    }
    let percent = prefix.contains('%') || suffix.contains('%');
    let mut v = if percent { v * 100.0 } else { v };
    // Trailing commas scale by thousands.
    let mut pat = pattern.as_str();
    while let Some(p) = pat.strip_suffix(',') {
        v /= 1000.0;
        pat = p;
    }
    if let Some(e) = pat.find(['E', 'e']) {
        let mant = pat.get(..e).unwrap_or_default();
        let decimals = mant.split_once('.').map_or(0, |(_, d)| d.chars().filter(|c| matches!(c, '0' | '#' | '?')).count());
        let s = format!("{:.*E}", decimals.min(15), v);
        if let Some((m, ex)) = s.split_once('E') {
            let ex: i32 = ex.parse().unwrap_or(0);
            return format!("{prefix}{m}E{}{:02}{suffix}", if ex < 0 { '-' } else { '+' }, ex.abs());
        }
        return format!("{prefix}{s}{suffix}");
    }
    let (int_pat, dec_pat) = pat.split_once('.').unwrap_or((pat, ""));
    let min_dec = dec_pat.chars().filter(|c| *c == '0').count();
    let max_dec = dec_pat.chars().filter(|c| matches!(c, '0' | '#' | '?')).count().min(15);
    let thousands = int_pat.contains(',');
    let min_int = int_pat.chars().filter(|c| *c == '0').count();
    let neg = v < 0.0;
    let s = format!("{:.*}", max_dec, v.abs());
    let (int, mut dec) = match s.split_once('.') {
        Some((i, d)) => (i.to_string(), d.to_string()),
        None => (s.clone(), String::new()),
    };
    while dec.len() > min_dec && dec.ends_with('0') {
        dec.pop();
    }
    let mut int = if int == "0" && min_int == 0 { String::new() } else { int };
    while int.len() < min_int {
        int.insert(0, '0');
    }
    if thousands {
        int = group_thousands(&int);
    }
    let body = if dec.is_empty() { int } else { format!("{int}.{dec}") };
    let sign = if neg && body.chars().any(|c| c.is_ascii_digit() && c != '0') { "-" } else { "" };
    format!("{sign}{prefix}{body}{suffix}")
}

fn format_date(v: f64, code: &str, date1904: bool) -> Option<String> {
    let (y, mo, d, secs) = date_parts(v, date1904)?;
    let elapsed_hours = (v * 24.0).floor();
    let (h, mi, s) = ((secs / 3600.0).floor() as u32, ((secs % 3600.0) / 60.0).floor() as u32, (secs % 60.0) as u32);
    let ampm = code.to_ascii_lowercase().contains("am/pm") || code.to_ascii_lowercase().contains("a/p");
    let weekday = {
        // Sakamoto's method on the civil date (0 = Sunday).
        let t = [0i64, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
        let yy = if mo < 3 { y - 1 } else { y };
        let k = t.get((mo as usize).saturating_sub(1)).copied().unwrap_or(0);
        let w = (yy + yy.div_euclid(4) - yy.div_euclid(100) + yy.div_euclid(400) + k + i64::from(d)).rem_euclid(7);
        DAYS.get(w as usize).copied().unwrap_or("")
    };
    let mut out = String::new();
    let chars: Vec<char> = code.chars().collect();
    let mut i = 0;
    let mut last_was_hour = false;
    let mut q = false;
    while let Some(&c) = chars.get(i) {
        if c == '"' {
            q = !q;
            i += 1;
            continue;
        }
        if q {
            out.push(c);
            i += 1;
            continue;
        }
        let lc = c.to_ascii_lowercase();
        let run = chars.iter().skip(i).take_while(|x| x.to_ascii_lowercase() == lc).count();
        match lc {
            'y' => {
                if run <= 2 {
                    out.push_str(&format!("{:02}", y.rem_euclid(100)));
                } else {
                    out.push_str(&y.to_string());
                }
            }
            'm' => {
                // Minutes after an hour or before seconds.
                let next_is_s = chars.iter().skip(i + run).find(|x| x.is_ascii_alphabetic()).is_some_and(|x| x.eq_ignore_ascii_case(&'s'));
                if (last_was_hour || next_is_s) && run <= 2 {
                    out.push_str(&if run == 2 { format!("{mi:02}") } else { mi.to_string() });
                } else {
                    let name = MONTHS.get((mo as usize).saturating_sub(1)).copied().unwrap_or("");
                    match run {
                        1 => out.push_str(&mo.to_string()),
                        2 => out.push_str(&format!("{mo:02}")),
                        3 => out.push_str(name.get(..3).unwrap_or(name)),
                        5 => out.push_str(name.get(..1).unwrap_or(name)),
                        _ => out.push_str(name),
                    }
                }
            }
            'd' => match run {
                1 => out.push_str(&d.to_string()),
                2 => out.push_str(&format!("{d:02}")),
                3 => out.push_str(weekday.get(..3).unwrap_or(weekday)),
                _ => out.push_str(weekday),
            },
            'h' => {
                let hh = if ampm {
                    match h % 12 {
                        0 => 12,
                        x => x,
                    }
                } else {
                    h
                };
                out.push_str(&if run >= 2 { format!("{hh:02}") } else { hh.to_string() });
            }
            's' => out.push_str(&if run >= 2 { format!("{s:02}") } else { s.to_string() }),
            'a' if code.get(i..).is_some() => {
                let rest: String = chars.iter().skip(i).take(5).collect::<String>().to_ascii_lowercase();
                if rest.starts_with("am/pm") {
                    out.push_str(if h < 12 { "AM" } else { "PM" });
                    i += 5;
                    continue;
                } else if rest.starts_with("a/p") {
                    out.push_str(if h < 12 { "A" } else { "P" });
                    i += 3;
                    continue;
                }
                out.push(c);
            }
            _ => {
                if c.is_ascii_alphabetic() || c == '[' || c == ']' {
                    // [h] elapsed hours.
                    if c == '[' && chars.get(i + 1).is_some_and(|x| x.eq_ignore_ascii_case(&'h')) {
                        out.push_str(&(elapsed_hours as i64).to_string());
                        i += chars.iter().skip(i).take_while(|x| **x != ']').count() + 1;
                        last_was_hour = true;
                        continue;
                    }
                    if c == '0' || c == '.' {
                        out.push(c);
                    }
                } else {
                    out.push(c);
                }
                i += 1;
                continue;
            }
        }
        last_was_hour = lc == 'h';
        i += run;
    }
    Some(out)
}

// ── sheets ──────────────────────────────────────────────────────────────────────────────────

/// "B12" → (row 12, column 2), 1-based.
fn cell_ref(r: &str) -> Option<(u32, u32)> {
    let r = r.trim().trim_start_matches('$');
    let split = r.find(|c: char| c.is_ascii_digit())?;
    let (letters, digits) = r.split_at(split);
    let mut col: u32 = 0;
    for c in letters.trim_end_matches('$').chars() {
        if !c.is_ascii_alphabetic() {
            return None;
        }
        col = col.checked_mul(26)?.checked_add(u32::from(c.to_ascii_uppercase() as u8 - b'A' + 1))?;
    }
    let row: u32 = digits.parse().ok()?;
    ((1..=MAX_COLS).contains(&col) && (1..=MAX_ROWS).contains(&row)).then_some((row, col))
}

struct CellData {
    value: Option<Value>,
    style: usize,
}

struct Sheet {
    cells: HashMap<(u32, u32), CellData>,
    col_widths: HashMap<u32, f64>,
    hidden_cols: Vec<(u32, u32)>,
    row_heights: HashMap<u32, f64>,
    hidden_rows: std::collections::HashSet<u32>,
    default_width: f64,
    default_height: f64,
    merges: Vec<(u32, u32, u32, u32)>,
    landscape: Option<bool>,
    margins: Option<(f64, f64, f64, f64)>,
    show_grid: bool,
    /// The relationship id of the sheet's drawing (its pictures).
    drawing: Option<String>,
}

/// A picture on a sheet: its top-left cell (1-based) and offset into it, and either its
/// bottom-right cell and offset or its size, in points.
struct SheetImage {
    from: (u32, u32, f64, f64),
    to: Option<(u32, u32, f64, f64)>,
    size: (f64, f64),
    id: usize,
}

/// A drawing anchor marker (`xdr:from`, `xdr:to`): row, column (1-based) and offsets in points.
fn marker(n: Node<'_, '_>) -> Option<(u32, u32, f64, f64)> {
    let v = |name: &str| child(n, name).and_then(|c| c.text()).and_then(super::xml::parse_num);
    let col = v("col")?.clamp(0.0, f64::from(MAX_COLS - 1)) as u32 + 1;
    let row = v("row")?.clamp(0.0, f64::from(MAX_ROWS - 1)) as u32 + 1;
    let emu = |x: Option<f64>| x.map_or(0.0, |e| (e / 12_700.0).clamp(0.0, 5_000.0));
    Some((row, col, emu(v("colOff")), emu(v("rowOff"))))
}

/// The pictures of a sheet's drawing part, embedded in `writer`. Charts and shapes are left out.
fn sheet_images(pkg: &Package<'_>, sheet_part: &str, rid: Option<&str>, writer: &mut Writer) -> Vec<SheetImage> {
    let mut out = Vec::new();
    let Some(rid) = rid else { return out };
    let rels = relationships(&pkg.zip, sheet_part);
    let Some(target) = rels.iter().find(|(i, _, _)| i == rid).map(|(_, _, p)| p.clone()) else { return out };
    let Ok(Some(text)) = pkg.xml(&target) else { return out };
    let Some(doc) = parse_str(&text) else { return out };
    let drawing_rels = relationships(&pkg.zip, &target);
    for anchor in elements(doc.root_element()).filter(|a| matches!(a.tag_name().name(), "twoCellAnchor" | "oneCellAnchor")).take(1_000) {
        let Some(from) = child(anchor, "from").and_then(marker) else { continue };
        let Some(pic) = anchor.descendants().find(|n| n.tag_name().name() == "pic") else { continue };
        let Some(embed) = pic.descendants().find(|n| n.tag_name().name() == "blip").and_then(|b| attr(b, "embed")) else { continue };
        let Some(media) = drawing_rels.iter().find(|(i, _, _)| i == embed).map(|(_, _, p)| p.clone()) else { continue };
        let Some(bytes) = pkg.bytes(&media) else { continue };
        let Some((id, natural)) = writer.image(&media, &bytes) else { continue };
        let ext = child(anchor, "ext")
            .or_else(|| pic.descendants().find(|n| n.tag_name().name() == "ext" && n.parent().is_some_and(|p| p.tag_name().name() == "xfrm")))
            .and_then(|e| Some((num(e, "cx")? / 12_700.0, num(e, "cy")? / 12_700.0)))
            .filter(|(w, h)| *w > 0.0 && *h > 0.0 && *w < 14_400.0 && *h < 14_400.0)
            .unwrap_or(natural);
        let to = if anchor.tag_name().name() == "twoCellAnchor" { child(anchor, "to").and_then(marker) } else { None };
        out.push(SheetImage { from, to, size: ext, id });
    }
    out
}

/// Column width in characters (of the default font's digit) → points.
fn width_points(chars: f64) -> f64 {
    if !chars.is_finite() || chars <= 0.0 {
        return 0.0;
    }
    (chars.min(255.0) * 7.0 + 5.0).floor() * 0.75
}

fn shared_strings(text: Option<&str>) -> Vec<String> {
    let Some(doc) = text.and_then(parse_str) else { return Vec::new() };
    children(doc.root_element(), "si").map(rich_text).collect()
}

/// The text of a string item: `t`, or its rich runs' `t`s (phonetic runs left out).
fn rich_text(si: Node<'_, '_>) -> String {
    let mut s = String::new();
    for c in elements(si) {
        match c.tag_name().name() {
            "t" => s.push_str(c.text().unwrap_or_default()),
            "r" => {
                if let Some(t) = child(c, "t") {
                    s.push_str(t.text().unwrap_or_default());
                }
            }
            _ => {}
        }
    }
    s
}

fn parse_sheet(text: &str, strings: &[String]) -> Option<Sheet> {
    let doc = parse_str(text)?;
    let root = doc.root_element();
    let fmt = child(root, "sheetFormatPr");
    let default_width = fmt
        .and_then(|f| num(f, "defaultColWidth"))
        .map(|w| (w * 7.0).floor() * 0.75)
        .or_else(|| fmt.and_then(|f| num(f, "baseColWidth")).map(|b| width_points(b + 0.71)))
        .unwrap_or(48.0);
    let default_height = fmt.and_then(|f| num(f, "defaultRowHeight")).filter(|h| *h > 0.0 && *h < 410.0).unwrap_or(15.0);
    let mut sheet = Sheet {
        cells: HashMap::new(),
        col_widths: HashMap::new(),
        hidden_cols: Vec::new(),
        row_heights: HashMap::new(),
        hidden_rows: Default::default(),
        default_width: default_width.clamp(1.0, 2000.0),
        default_height,
        merges: Vec::new(),
        landscape: None,
        margins: None,
        show_grid: true,
        drawing: None,
    };
    if let Some(cols) = child(root, "cols") {
        for c in children(cols, "col") {
            let (Some(min), Some(max)) = (num(c, "min"), num(c, "max")) else { continue };
            let (min, max) = (min.clamp(1.0, MAX_COLS as f64) as u32, max.clamp(1.0, MAX_COLS as f64) as u32);
            if attr(c, "hidden").is_some_and(|v| v == "1" || v == "true") {
                sheet.hidden_cols.push((min, max));
                continue;
            }
            if let Some(w) = num(c, "width") {
                // Ranges to the last column (max=16384) are common: only keep explicit widths
                // for a bounded number of columns; the rest use the default.
                for col in min..=max.min(min.saturating_add(1024)) {
                    sheet.col_widths.insert(col, width_points(w));
                }
            }
        }
    }
    if let Some(data) = child(root, "sheetData") {
        let mut count = 0usize;
        let mut next_row = 1u32;
        for row in children(data, "row") {
            let r = num(row, "r").map_or(next_row, |v| v.clamp(1.0, MAX_ROWS as f64) as u32);
            next_row = r.saturating_add(1);
            if attr(row, "hidden").is_some_and(|v| v == "1" || v == "true") {
                sheet.hidden_rows.insert(r);
            }
            if let Some(ht) = num(row, "ht").filter(|h| *h >= 0.0 && *h < 410.0) {
                sheet.row_heights.insert(r, ht);
            }
            let row_style = num(row, "s").filter(|_| attr(row, "customFormat").is_some_and(|v| v == "1")).map(|v| v.clamp(0.0, 1e6) as usize);
            let mut next_col = 1u32;
            for c in children(row, "c") {
                count += 1;
                if count > MAX_CELLS {
                    break;
                }
                let (rr, cc) = attr(c, "r").and_then(cell_ref).unwrap_or((r, next_col));
                next_col = cc.saturating_add(1);
                let style = num(c, "s").map(|v| v.clamp(0.0, 1e6) as usize).or(row_style).unwrap_or(0);
                let v = child(c, "v").and_then(|v| v.text());
                let value = match attr(c, "t").unwrap_or("n") {
                    "s" => v.and_then(|v| v.trim().parse::<usize>().ok()).and_then(|i| strings.get(i)).map(|s| Value::Text(s.clone())),
                    "inlineStr" => child(c, "is").map(rich_text).map(Value::Text),
                    "str" => v.map(|s| Value::Text(s.to_string())),
                    "b" => v.map(|s| Value::Bool(s.trim() == "1")),
                    "e" => v.map(|s| Value::Error(s.to_string())),
                    "d" => v.map(|s| Value::Text(s.to_string())),
                    _ => v.and_then(|s| s.trim().parse::<f64>().ok()).filter(|f| f.is_finite()).map(Value::Num),
                };
                sheet.cells.insert((rr, cc), CellData { value, style });
            }
            if count > MAX_CELLS {
                break;
            }
        }
    }
    if let Some(m) = child(root, "mergeCells") {
        for mc in children(m, "mergeCell").take(10_000) {
            if let Some((a, b)) = attr(mc, "ref").and_then(|r| r.split_once(':'))
                && let (Some((r1, c1)), Some((r2, c2))) = (cell_ref(a), cell_ref(b))
            {
                sheet.merges.push((r1.min(r2), c1.min(c2), r1.max(r2), c1.max(c2)));
            }
        }
    }
    if let Some(ps) = child(root, "pageSetup") {
        sheet.landscape = attr(ps, "orientation").map(|o| o == "landscape");
    }
    if let Some(pm) = child(root, "pageMargins") {
        let m = |a: &str, d: f64| num(pm, a).filter(|v| (0.0..5.0).contains(v)).map_or(d, |v| v * 72.0);
        sheet.margins = Some((m("left", 50.4), m("top", 54.0), m("right", 50.4), m("bottom", 54.0)));
    }
    if let Some(view) = path(root, &["sheetViews", "sheetView"]) {
        sheet.show_grid = attr(view, "showGridLines").is_none_or(|v| v != "0" && v != "false");
    }
    sheet.drawing = child(root, "drawing").and_then(|d| attr(d, "id")).map(str::to_string);
    Some(sheet)
}

pub(crate) fn convert(pkg: &Package<'_>, main: &str, writer: &mut Writer) -> Result<(), String> {
    let text = pkg.xml(main)?.ok_or_else(|| pkg.damaged("no workbook part"))?;
    let doc = parse_str(&text).ok_or_else(|| pkg.damaged("its workbook XML can't be read"))?;
    let root = doc.root_element();
    let rels = relationships(&pkg.zip, main);
    let part_of = |suffix: &str| rels.iter().find(|(_, t, _)| t.ends_with(suffix)).map(|(_, _, p)| p.clone());
    let date1904 = child(root, "workbookPr").and_then(|w| attr(w, "date1904")).is_some_and(|v| v == "1" || v == "true");
    let theme_text = match part_of("/theme") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let theme = theme_colours(theme_text.as_deref());
    let styles_text = match part_of("/styles") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let styles = StyleSheet::parse(styles_text.as_deref(), &theme);
    let strings_text = match part_of("/sharedStrings") {
        Some(p) => pkg.xml(&p)?,
        None => None,
    };
    let strings = shared_strings(strings_text.as_deref());
    let sheets = child(root, "sheets").ok_or_else(|| pkg.damaged("the workbook lists no sheets"))?;
    let start = writer.page_count();
    for s in children(sheets, "sheet") {
        if attr(s, "state").is_some_and(|v| v == "hidden" || v == "veryHidden") {
            continue;
        }
        let Some(part) = attr(s, "id").and_then(|id| rels.iter().find(|(i, _, _)| i == id)).map(|(_, _, p)| p.clone()) else { continue };
        let Some(text) = pkg.xml(&part)? else { continue };
        let Some(sheet) = parse_sheet(&text, &strings) else { continue };
        let images = sheet_images(pkg, &part, sheet.drawing.as_deref(), writer);
        draw_sheet(&sheet, &styles, &images, date1904, writer)?;
    }
    if writer.page_count() == start {
        // A workbook with nothing to print still gives a page.
        writer.page(612.0, 792.0, &[]).map_err(|e| e.to_string())?;
    }
    Ok(())
}

struct Laid {
    text: String,
    font: FontDef,
    align: HAlign,
    valign: VAlign,
    wrap: bool,
    indent: f64,
    numeric: bool,
    /// The value, when it is shown in the General format (shortened to fit its column).
    general: Option<f64>,
}

fn draw_sheet(sheet: &Sheet, styles: &StyleSheet, images: &[SheetImage], date1904: bool, writer: &mut Writer) -> Result<(), String> {
    // The used range: cells with a value, and merged areas.
    let mut range: Option<(u32, u32, u32, u32)> = None;
    let mut grow = |r: u32, c: u32| {
        range = Some(match range {
            None => (r, c, r, c),
            Some((r1, c1, r2, c2)) => (r1.min(r), c1.min(c), r2.max(r), c2.max(c)),
        });
    };
    for (&(r, c), cell) in &sheet.cells {
        let filled = styles.xfs.get(cell.style).is_some_and(|x| styles.fills.get(x.fill).copied().flatten().is_some() || x.border > 0);
        if cell.value.as_ref().is_some_and(|v| !matches!(v, Value::Text(t) if t.is_empty())) || filled {
            grow(r, c);
        }
    }
    for &(r1, c1, r2, c2) in &sheet.merges {
        if sheet.cells.get(&(r1, c1)).is_some_and(|c| c.value.is_some()) {
            grow(r1, c1);
            grow(r2, c2);
        }
    }
    for img in images {
        grow(img.from.0, img.from.1);
        if let Some(to) = img.to {
            grow(to.0, to.1);
        }
    }
    let Some((r1, c1, r2, mut c2)) = range else { return Ok(()) };
    let width_of = |c: u32| sheet.col_widths.get(&c).copied().unwrap_or(sheet.default_width);
    // Text running past the last used column (a note, a title) widens the printed range, as
    // long as the cells it runs over are empty.
    for (&(r, c), cell) in &sheet.cells {
        let Some(Value::Text(t)) = &cell.value else { continue };
        let xf = styles.xfs.get(cell.style).cloned().unwrap_or_default();
        if xf.wrap
            || !matches!(xf.h, HAlign::General | HAlign::Left)
            || sheet.merges.iter().any(|&(a, b, cc, d)| r >= a && r <= cc && c >= b && c <= d)
        {
            continue;
        }
        let font = styles.fonts.get(xf.font).cloned().unwrap_or(FontDef { size: 11.0, ..Default::default() });
        let face = Face { family: family_of(&font.name), bold: font.bold, italic: font.italic };
        let mut need = t.lines().map(|l| text_width(face, font.size, &drawable_text(l))).fold(0.0, f64::max) + 4.0 + xf.indent;
        let mut col = c;
        while need > width_of(col) && col < c.saturating_add(64) && col < MAX_COLS {
            need -= width_of(col);
            col += 1;
            if sheet.cells.get(&(r, col)).is_some_and(|n| n.value.is_some()) {
                col -= 1;
                break;
            }
        }
        c2 = c2.max(col);
    }
    // Only from A1 when the data starts near it, as Excel prints.
    let (r1, c1) = (if r1 <= 3 { 1 } else { r1 }, if c1 <= 3 { 1 } else { c1 });
    let hidden_col = |c: u32| sheet.hidden_cols.iter().any(|&(a, b)| c >= a && c <= b);
    let cols: Vec<(u32, f64)> = (c1..=c2)
        .filter(|c| !hidden_col(*c))
        .map(|c| (c, sheet.col_widths.get(&c).copied().unwrap_or(sheet.default_width)))
        .filter(|(_, w)| *w > 0.0)
        .collect();
    let rows: Vec<(u32, f64)> = (r1..=r2)
        .filter(|r| !sheet.hidden_rows.contains(r))
        .map(|r| (r, sheet.row_heights.get(&r).copied().unwrap_or(sheet.default_height)))
        .filter(|(_, h)| *h > 0.0)
        .collect();
    if cols.is_empty() || rows.is_empty() {
        return Ok(());
    }
    let total_w: f64 = cols.iter().map(|c| c.1).sum();
    let (ml, mt, mr, mb) = sheet.margins.unwrap_or((50.4, 54.0, 50.4, 54.0));
    let (pw, ph) = (612.0, 792.0);
    let avail_p = pw - ml - mr;
    let avail_l = ph - ml - mr;
    // Portrait when the sheet fits (or nearly fits, scaled); landscape for wider sheets.
    let (landscape, scale) = match sheet.landscape {
        Some(true) => (true, (avail_l / total_w).clamp(0.5, 1.0)),
        Some(false) if total_w * 0.5 <= avail_p => (false, (avail_p / total_w).clamp(0.5, 1.0)),
        _ => {
            if total_w <= avail_p {
                (false, 1.0)
            } else if total_w * 0.85 <= avail_p {
                (false, avail_p / total_w)
            } else {
                (true, (avail_l / total_w).clamp(0.5, 1.0))
            }
        }
    };
    let (page_w, page_h) = if landscape { (ph, pw) } else { (pw, ph) };
    let area_w = (page_w - ml - mr) / scale;
    let area_h = (page_h - mt - mb) / scale;
    // Column and row chunks that fit a page.
    let chunks = |items: &[(u32, f64)], limit: f64| -> Vec<(usize, usize)> {
        let mut out = Vec::new();
        let mut start = 0;
        let mut acc = 0.0;
        for (i, (_, size)) in items.iter().enumerate() {
            if acc + size > limit + 0.01 && i > start {
                out.push((start, i));
                start = i;
                acc = 0.0;
            }
            acc += size;
        }
        out.push((start, items.len()));
        out
    };
    let col_chunks = chunks(&cols, area_w);
    let row_chunks = chunks(&rows, area_h);
    // Cell contents, formatted once.
    let laid = |cell: &CellData| -> Option<Laid> {
        let value = cell.value.as_ref()?;
        let xf = styles.xfs.get(cell.style).cloned().unwrap_or_default();
        let font = styles.fonts.get(xf.font).cloned().unwrap_or(FontDef { size: 11.0, ..Default::default() });
        let mut general = None;
        let (text, numeric) = match value {
            Value::Num(v) => {
                let code = styles.num_fmts.get(&xf.num_fmt).map(String::as_str).or_else(|| builtin_format(xf.num_fmt)).unwrap_or("General");
                if code.eq_ignore_ascii_case("general") {
                    general = Some(*v);
                }
                (format_value(*v, code, date1904), true)
            }
            Value::Text(t) => (t.clone(), false),
            Value::Bool(b) => ((if *b { "TRUE" } else { "FALSE" }).to_string(), false),
            Value::Error(e) => (e.clone(), false),
        };
        let align = match (xf.h, value) {
            (HAlign::General, Value::Num(_)) => HAlign::Right,
            (HAlign::General, Value::Bool(_) | Value::Error(_)) => HAlign::Center,
            (HAlign::General, _) => HAlign::Left,
            (h, _) => h,
        };
        Some(Laid { text: drawable_text(&text), font, align, valign: xf.v, wrap: xf.wrap, indent: xf.indent, numeric, general })
    };
    let merge_of = |r: u32, c: u32| sheet.merges.iter().find(|&&(a, b, cc, d)| r >= a && r <= cc && c >= b && c <= d).copied();
    for &(cs, ce) in &col_chunks {
        for &(rs, re) in &row_chunks {
            let (Some(pc), Some(pr)) = (cols.get(cs..ce), rows.get(rs..re)) else { continue };
            let mut xs = HashMap::new();
            let mut x = 0.0;
            for (c, w) in pc {
                xs.insert(*c, (x, *w));
                x += w;
            }
            let mut ys = HashMap::new();
            let mut y = 0.0;
            for (r, h) in pr {
                ys.insert(*r, (y, *h));
                y += h;
            }
            let (grid_w, grid_h) = (x, y);
            // Positions of any column/row in the whole sheet relative to this page (for merges
            // reaching outside it).
            let col_x = |c: u32| -> f64 {
                let first = pc.first().map_or(0, |f| f.0);
                if c >= first {
                    cols.iter().filter(|(cc, _)| *cc >= first && *cc < c).map(|(_, w)| w).sum()
                } else {
                    -cols.iter().filter(|(cc, _)| *cc >= c && *cc < first).map(|(_, w)| w).sum::<f64>()
                }
            };
            let row_y = |r: u32| -> f64 {
                let first = pr.first().map_or(0, |f| f.0);
                if r >= first {
                    rows.iter().filter(|(rr, _)| *rr >= first && *rr < r).map(|(_, h)| h).sum()
                } else {
                    -rows.iter().filter(|(rr, _)| *rr >= r && *rr < first).map(|(_, h)| h).sum::<f64>()
                }
            };
            let mut ops: Vec<Op> = Vec::new();
            // Fills.
            for (r, _) in pr {
                for (c, _) in pc {
                    let Some(cell) = sheet.cells.get(&(*r, *c)) else { continue };
                    let Some(fill) = styles.xfs.get(cell.style).and_then(|x| styles.fills.get(x.fill)).copied().flatten() else { continue };
                    let (Some(&(cx, cw)), Some(&(ry, rh))) = (xs.get(c), ys.get(r)) else { continue };
                    let (cx, ry, cw, rh) = match merge_of(*r, *c) {
                        Some((a, b, cc, d)) if (a, b) == (*r, *c) => (col_x(b), row_y(a), col_x(d + 1) - col_x(b), row_y(cc + 1) - row_y(a)),
                        Some(_) => continue,
                        None => (cx, ry, cw, rh),
                    };
                    ops.push(Op::Rect { x: cx, y: ry, w: cw, h: rh, fill: Some(fill), stroke: None });
                }
            }
            // Light grid lines, broken by merged areas.
            if sheet.show_grid {
                let grey = Rgb(0xD0, 0xD0, 0xD0);
                let mut x = 0.0;
                for (i, (c, w)) in pc.iter().enumerate() {
                    if i == 0 {
                        ops.push(Op::Line { x1: 0.0, y1: 0.0, x2: 0.0, y2: grid_h, color: grey, width: 0.25 });
                    }
                    x += w;
                    for (r, _) in pr {
                        let Some(&(ry, rh)) = ys.get(r) else { continue };
                        let inside = merge_of(*r, *c).is_some_and(|(_, _, _, d)| d > *c);
                        if !inside {
                            ops.push(Op::Line { x1: x, y1: ry, x2: x, y2: ry + rh, color: grey, width: 0.25 });
                        }
                    }
                }
                let mut y = 0.0;
                for (i, (r, h)) in pr.iter().enumerate() {
                    if i == 0 {
                        ops.push(Op::Line { x1: 0.0, y1: 0.0, x2: grid_w, y2: 0.0, color: grey, width: 0.25 });
                    }
                    y += h;
                    for (c, _) in pc {
                        let Some(&(cx, cw)) = xs.get(c) else { continue };
                        let inside = merge_of(*r, *c).is_some_and(|(_, _, cc, _)| cc > *r);
                        if !inside {
                            ops.push(Op::Line { x1: cx, y1: y, x2: cx + cw, y2: y, color: grey, width: 0.25 });
                        }
                    }
                }
            }
            // Text.
            for (r, _) in pr {
                for (ci, (c, _)) in pc.iter().enumerate() {
                    let Some(cell) = sheet.cells.get(&(*r, *c)) else { continue };
                    let merge = merge_of(*r, *c);
                    if merge.is_some_and(|(a, b, _, _)| (a, b) != (*r, *c)) {
                        continue;
                    }
                    let Some(mut l) = laid(cell) else { continue };
                    if l.text.is_empty() {
                        continue;
                    }
                    let (Some(&(mut cx, mut cw)), Some(&(mut ry, mut rh))) = (xs.get(c), ys.get(r)) else { continue };
                    if let Some((a, b, cc, d)) = merge {
                        cx = col_x(b);
                        cw = col_x(d + 1) - col_x(b);
                        ry = row_y(a);
                        rh = row_y(cc + 1) - row_y(a);
                    }
                    let face = Face { family: family_of(&l.font.name), bold: l.font.bold, italic: l.font.italic };
                    let mut size = l.font.size;
                    // General numbers lose decimals (then switch to scientific) to fit.
                    if let Some(v) = l.general.filter(|_| !l.wrap && merge.is_none()) {
                        let room = cw - 4.0;
                        let mut sig = 10;
                        while sig > 1 && text_width(face, size, &l.text) > room * 1.1 {
                            sig -= 1;
                            l.text = general_with(v, sig);
                        }
                    }
                    // The standard fonts are a little wider than Excel's defaults: a number that
                    // fits its column in Excel is set slightly smaller rather than shown as ###.
                    if l.numeric && !l.wrap && merge.is_none() {
                        let natural = text_width(face, size, &l.text);
                        let room = cw - 4.0;
                        if natural > room && room > 0.0 && natural <= room * 1.3 {
                            size *= room / natural;
                        }
                    }
                    let color = l.font.color.unwrap_or(BLACK);
                    let pad = 2.0;
                    let lines: Vec<String> = if l.wrap {
                        pdfcraft_fonts::wrap_with(&l.text, size, (cw - 2.0 * pad - l.indent).max(4.0), |s, sz| text_width(face, sz, s))
                    } else {
                        l.text.split('\n').map(str::to_string).collect()
                    };
                    let line_h = size * 1.2;
                    let block_h = line_h * lines.len() as f64;
                    let top = match l.valign {
                        VAlign::Top => ry + 1.0,
                        VAlign::Center => ry + (rh - block_h) / 2.0,
                        VAlign::Bottom => ry + rh - block_h - 1.0,
                    };
                    // Text left-aligned without wrapping runs over empty cells to its right.
                    let mut clip_w = cw;
                    let widest = lines.iter().map(|s| text_width(face, size, s)).fold(0.0, f64::max);
                    if !l.wrap && merge.is_none() && widest + 2.0 * pad > cw + 0.01 {
                        if l.numeric {
                            // Numbers too wide for their column show as ### in Excel.
                            let hashes = "#".repeat(((cw - 2.0 * pad) / text_width(face, size, "#")).floor().clamp(1.0, 255.0) as usize);
                            ops.push(Op::Clip { x: cx, y: ry, w: cw, h: rh });
                            ops.push(Op::Text { x: cx + pad, y: top + size * 0.95, face, size, color, text: hashes, word_spacing: 0.0 });
                            ops.push(Op::Restore);
                            continue;
                        }
                        if l.align == HAlign::Left {
                            for (nc, nw) in pc.iter().skip(ci + 1) {
                                if sheet
                                    .cells
                                    .get(&(*r, *nc))
                                    .is_some_and(|c| c.value.as_ref().is_some_and(|v| !matches!(v, Value::Text(t) if t.is_empty())))
                                    || merge_of(*r, *nc).is_some()
                                {
                                    break;
                                }
                                clip_w += nw;
                                if widest + 2.0 * pad <= clip_w {
                                    break;
                                }
                            }
                        }
                    }
                    ops.push(Op::Clip { x: cx, y: ry, w: clip_w, h: rh });
                    for (i, line) in lines.iter().enumerate() {
                        let tw = text_width(face, size, line);
                        let tx = match l.align {
                            HAlign::Center => cx + (cw - tw) / 2.0,
                            HAlign::Right => cx + cw - pad - tw - l.indent,
                            _ => cx + pad + l.indent,
                        };
                        let base = top + line_h * i as f64 + size * 0.95;
                        if l.font.underline {
                            ops.push(Op::Line {
                                x1: tx,
                                y1: base + size * 0.12,
                                x2: tx + tw,
                                y2: base + size * 0.12,
                                color,
                                width: (size * 0.06).max(0.4),
                            });
                        }
                        if l.font.strike {
                            ops.push(Op::Line {
                                x1: tx,
                                y1: base - size * 0.28,
                                x2: tx + tw,
                                y2: base - size * 0.28,
                                color,
                                width: (size * 0.06).max(0.4),
                            });
                        }
                        ops.push(Op::Text { x: tx, y: base, face, size, color, text: line.clone(), word_spacing: 0.0 });
                    }
                    ops.push(Op::Restore);
                }
            }
            // Cell borders.
            for (r, _) in pr {
                for (c, _) in pc {
                    let Some(cell) = sheet.cells.get(&(*r, *c)) else { continue };
                    let Some(sides) = styles.xfs.get(cell.style).and_then(|x| styles.borders.get(x.border)) else { continue };
                    let (Some(&(cx, cw)), Some(&(ry, rh))) = (xs.get(c), ys.get(r)) else { continue };
                    let [top, right, bottom, left] = sides;
                    if let Some((col, w)) = top {
                        ops.push(Op::Line { x1: cx, y1: ry, x2: cx + cw, y2: ry, color: *col, width: *w });
                    }
                    if let Some((col, w)) = bottom {
                        ops.push(Op::Line { x1: cx, y1: ry + rh, x2: cx + cw, y2: ry + rh, color: *col, width: *w });
                    }
                    if let Some((col, w)) = left {
                        ops.push(Op::Line { x1: cx, y1: ry, x2: cx, y2: ry + rh, color: *col, width: *w });
                    }
                    if let Some((col, w)) = right {
                        ops.push(Op::Line { x1: cx + cw, y1: ry, x2: cx + cw, y2: ry + rh, color: *col, width: *w });
                    }
                }
            }
            // Pictures, over the cells.
            for img in images {
                let (r, c, dx, dy) = img.from;
                let (x, y) = (col_x(c) + dx, row_y(r) + dy);
                let (w, h) = match img.to {
                    Some((r2, c2, dx2, dy2)) => ((col_x(c2) + dx2 - x).max(1.0), (row_y(r2) + dy2 - y).max(1.0)),
                    None => img.size,
                };
                if x < grid_w && y < grid_h && x + w > 0.0 && y + h > 0.0 {
                    ops.push(Op::Image { id: img.id, x, y, w, h });
                }
            }
            // Scale and place on the page, clipped to the grid.
            let mut page_ops = vec![Op::Clip { x: ml, y: mt, w: grid_w * scale + 1.0, h: grid_h * scale + 1.0 }];
            for mut op in ops {
                scale_op(&mut op, scale);
                op.offset(ml, mt);
                page_ops.push(op);
            }
            page_ops.push(Op::Restore);
            writer.page(page_w, page_h, &page_ops).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn scale_op(op: &mut Op, k: f64) {
    match op {
        Op::Text { x, y, size, word_spacing, .. } => {
            *x *= k;
            *y *= k;
            *size *= k;
            *word_spacing *= k;
        }
        Op::Rect { x, y, w, h, stroke, .. } => {
            *x *= k;
            *y *= k;
            *w *= k;
            *h *= k;
            if let Some((_, sw)) = stroke {
                *sw *= k;
            }
        }
        Op::Clip { x, y, w, h } | Op::Image { x, y, w, h, .. } => {
            *x *= k;
            *y *= k;
            *w *= k;
            *h *= k;
        }
        Op::Line { x1, y1, x2, y2, width, .. } => {
            *x1 *= k;
            *y1 *= k;
            *x2 *= k;
            *y2 *= k;
            *width *= k.max(0.5);
        }
        Op::Rotate { cx, cy, .. } => {
            *cx *= k;
            *cy *= k;
        }
        Op::Path { .. } | Op::Restore => {}
    }
}

#[cfg(test)]
pub(super) fn test_format(v: f64, code: &str) -> String {
    format_value(v, code, false)
}

#[cfg(test)]
pub(super) fn test_general(v: f64, sig: i32) -> String {
    general_with(v, sig)
}

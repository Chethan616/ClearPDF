//! Export a PDF ▸ Excel workbook (.xlsx, SpreadsheetML).
//!
//! One worksheet per page. Detected tables become cell grids (spanning cells merged); every other
//! paragraph is one cell in column A on its own row, in reading order. Plain numbers are written
//! as numbers, keeping their decimals and thousands separators as a number format.

use std::collections::HashMap;

use crate::{Item, Page, Table, Zip, core_xml, esc, items, rgb8};

/// Excel's grid: rows and columns per sheet.
const MAX_ROWS: u32 = 1_048_576;
const MAX_COLS: u32 = 16_384;
/// The most text a cell holds (UTF-16 units).
const MAX_CELL_TEXT: usize = 32_767;
/// More significant digits than this lose precision as a double: keep them as text (account
/// numbers, long references).
const MAX_DIGITS: usize = 15;
/// Approximate width of one character of the default font (Calibri 11, 7 px at 96 dpi), in
/// points: Excel column widths count these.
const CHAR_PT: f64 = 5.25;

const MAIN_NS: &str = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
const REL_NS: &str = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
const XML_DECL: &str = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

/// A plain number: optional sign, digits (optionally grouped by `,` in threes), optional decimal
/// part. Returns the value as written for `<v>` (sign kept only when negative, separators
/// dropped), whether it had thousands separators, and how many decimals it had. Leading zeros
/// ("02139") stay text: a number would drop them.
pub(crate) fn plain_number(s: &str) -> Option<(String, bool, usize)> {
    let s = s.trim();
    let (neg, rest) = match s.as_bytes().first()? {
        b'-' => (true, s.get(1..)?),
        b'+' => (false, s.get(1..)?),
        _ => (false, s),
    };
    let (int, frac) = match rest.split_once('.') {
        Some((i, f)) => (i, Some(f)),
        None => (rest, None),
    };
    if int.is_empty() || frac.is_some_and(|f| f.is_empty() || !f.bytes().all(|b| b.is_ascii_digit())) {
        return None;
    }
    let grouped = int.contains(',');
    if grouped {
        let mut groups = int.split(',');
        let first = groups.next()?;
        if first.is_empty() || first.len() > 3 || !first.bytes().all(|b| b.is_ascii_digit()) {
            return None;
        }
        if !groups.all(|g| g.len() == 3 && g.bytes().all(|b| b.is_ascii_digit())) {
            return None;
        }
    } else if !int.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    let digits: String = int.chars().filter(|c| *c != ',').collect();
    if digits.len() > 1 && digits.starts_with('0') {
        return None;
    }
    let decimals = frac.map_or(0, str::len);
    if digits.len().saturating_add(decimals) > MAX_DIGITS {
        return None;
    }
    let mut v = String::new();
    if neg {
        v.push('-');
    }
    v.push_str(&digits);
    if let Some(f) = frac {
        v.push('.');
        v.push_str(f);
    }
    // The grammar above is a valid xsd:double; parsing confirms it is finite.
    v.parse::<f64>().ok().filter(|x| x.is_finite())?;
    Some((v, grouped, decimals))
}

/// Text with characters XML forbids dropped, cut to what a cell holds (on a char boundary).
fn cell_text(s: &str) -> String {
    let mut out = String::new();
    let mut units = 0usize;
    for c in s.trim().chars().filter(|c| crate::allowed(*c)) {
        units = units.saturating_add(c.len_utf16());
        if units > MAX_CELL_TEXT {
            break;
        }
        out.push(c);
    }
    out
}

/// A column's letters: 0 → A, 25 → Z, 26 → AA.
pub(crate) fn col_name(mut c: u32) -> String {
    let mut s = Vec::new();
    loop {
        s.push(b'A' + (c % 26) as u8);
        if c < 26 {
            break;
        }
        c = c / 26 - 1;
    }
    s.reverse();
    String::from_utf8(s).unwrap_or_default()
}

/// Run formatting of a cell.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
struct Font {
    bold: bool,
    italic: bool,
    half_points: i64,
    color: [u8; 3],
}

impl Font {
    fn new(size: f64, bold: bool, italic: bool, color: [f64; 3]) -> Font {
        // Clamped to Excel's 1..=409 pt before the cast, so it cannot truncate.
        let half_points = if size.is_finite() { (size * 2.0).round().clamp(2.0, 818.0) as i64 } else { 22 };
        Font { bold, italic, half_points, color: rgb8(color) }
    }
}

const DEFAULT_FONT: Font = Font { bold: false, italic: false, half_points: 22, color: [0, 0, 0] };

/// Fonts, number formats and cell formats, each written once.
struct Styles {
    fonts: Vec<Font>,
    /// Custom number formats (id, code); ids start at 164.
    num_fmts: Vec<(u32, String)>,
    /// (font index, number format id); index 0 is the default.
    xfs: Vec<(usize, u32)>,
}

impl Styles {
    fn new() -> Styles {
        Styles { fonts: vec![DEFAULT_FONT], num_fmts: Vec::new(), xfs: vec![(0, 0)] }
    }

    fn font(&mut self, f: Font) -> usize {
        match self.fonts.iter().position(|e| *e == f) {
            Some(i) => i,
            None => {
                self.fonts.push(f);
                self.fonts.len() - 1
            }
        }
    }

    /// The number format for a number written with these separators and decimals.
    fn num_fmt(&mut self, grouped: bool, decimals: usize) -> u32 {
        match (grouped, decimals) {
            (false, 0) => 0,
            (true, 0) => 3,
            (false, 2) => 2,
            (true, 2) => 4,
            (g, d) => {
                let code = format!("{}0.{}", if g { "#,##" } else { "" }, "0".repeat(d.min(MAX_DIGITS)));
                if let Some((id, _)) = self.num_fmts.iter().find(|(_, c)| *c == code) {
                    return *id;
                }
                let id = 164u32.saturating_add(self.num_fmts.len() as u32);
                self.num_fmts.push((id, code));
                id
            }
        }
    }

    fn xf(&mut self, font: Font, num_fmt: u32) -> usize {
        let key = (self.font(font), num_fmt);
        match self.xfs.iter().position(|e| *e == key) {
            Some(i) => i,
            None => {
                self.xfs.push(key);
                self.xfs.len() - 1
            }
        }
    }

    fn xml(&self) -> String {
        let mut s = format!("{XML_DECL}<styleSheet xmlns=\"{MAIN_NS}\">");
        if !self.num_fmts.is_empty() {
            s.push_str(&format!("<numFmts count=\"{}\">", self.num_fmts.len()));
            for (id, code) in &self.num_fmts {
                s.push_str(&format!("<numFmt numFmtId=\"{id}\" formatCode=\"{}\"/>", esc(code)));
            }
            s.push_str("</numFmts>");
        }
        s.push_str(&format!("<fonts count=\"{}\">", self.fonts.len()));
        for f in &self.fonts {
            s.push_str("<font>");
            if f.bold {
                s.push_str("<b/>");
            }
            if f.italic {
                s.push_str("<i/>");
            }
            let [r, g, b] = f.color;
            let pt = f.half_points / 2;
            let size = if f.half_points % 2 == 0 { pt.to_string() } else { format!("{pt}.5") };
            s.push_str(&format!("<sz val=\"{size}\"/><color rgb=\"FF{r:02X}{g:02X}{b:02X}\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"));
        }
        s.push_str(
            "</fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>\
<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>",
        );
        s.push_str(&format!("<cellXfs count=\"{}\">", self.xfs.len()));
        for (font, num_fmt) in &self.xfs {
            let mut apply = String::new();
            if *font != 0 {
                apply.push_str(" applyFont=\"1\"");
            }
            if *num_fmt != 0 {
                apply.push_str(" applyNumberFormat=\"1\"");
            }
            s.push_str(&format!("<xf numFmtId=\"{num_fmt}\" fontId=\"{font}\" fillId=\"0\" borderId=\"0\" xfId=\"0\"{apply}/>"));
        }
        s.push_str("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
        s
    }
}

/// The shared strings table: each distinct text once.
#[derive(Default)]
struct Strings {
    index: HashMap<String, usize>,
    list: Vec<String>,
    count: usize,
}

impl Strings {
    fn add(&mut self, s: String) -> usize {
        self.count = self.count.saturating_add(1);
        if let Some(i) = self.index.get(&s) {
            return *i;
        }
        let i = self.list.len();
        self.index.insert(s.clone(), i);
        self.list.push(s);
        i
    }

    fn xml(&self) -> String {
        let mut s = format!("{XML_DECL}<sst xmlns=\"{MAIN_NS}\" count=\"{}\" uniqueCount=\"{}\">", self.count, self.list.len());
        for t in &self.list {
            s.push_str(&format!("<si><t xml:space=\"preserve\">{}</t></si>", esc(t)));
        }
        s.push_str("</sst>");
        s
    }
}

/// One worksheet being filled row by row.
struct Sheet {
    data: String,
    merges: Vec<String>,
    /// Next free row (0-based).
    row: u32,
    /// Per column: width from the PDF's table geometry, and the longest number (in characters).
    geom: Vec<f64>,
    text_len: Vec<usize>,
    full: bool,
}

impl Sheet {
    fn new() -> Sheet {
        Sheet { data: String::new(), merges: Vec::new(), row: 0, geom: Vec::new(), text_len: Vec::new(), full: false }
    }

    fn skip_row(&mut self) {
        self.row = self.row.saturating_add(1);
    }

    fn note_width(v: &mut Vec<f64>, col: usize, w: f64) {
        if col >= MAX_COLS as usize || !w.is_finite() {
            return;
        }
        if v.len() <= col {
            v.resize(col + 1, 0.0);
        }
        if let Some(e) = v.get_mut(col) {
            *e = e.max(w);
        }
    }

    /// Write one row of cells: (column, text, font, span). Empty texts leave holes.
    fn put_row(&mut self, cells: &[(u32, &str, Font, usize)], styles: &mut Styles, strings: &mut Strings) {
        if self.row >= MAX_ROWS {
            self.full = true;
            return;
        }
        let r = self.row + 1;
        let mut xml = String::new();
        for &(col, text, font, span) in cells {
            if col >= MAX_COLS {
                break;
            }
            let text = cell_text(text);
            if span > 1 {
                let extra = (span.min(MAX_COLS as usize) as u32).saturating_sub(1);
                let last = col.saturating_add(extra).min(MAX_COLS - 1);
                if last > col {
                    self.merges.push(format!("{}{r}:{}{r}", col_name(col), col_name(last)));
                }
            }
            if text.is_empty() {
                continue;
            }
            let reference = format!("{}{r}", col_name(col));
            match plain_number(&text) {
                Some((v, grouped, decimals)) => {
                    let fmt = styles.num_fmt(grouped, decimals);
                    let s = styles.xf(font, fmt);
                    xml.push_str(&format!("<c r=\"{reference}\"{}><v>{v}</v></c>", style_attr(s)));
                    if span <= 1 {
                        let lens = &mut self.text_len;
                        let len = text.chars().count();
                        if lens.len() <= col as usize {
                            lens.resize(col as usize + 1, 0);
                        }
                        if let Some(e) = lens.get_mut(col as usize) {
                            *e = (*e).max(len);
                        }
                    }
                }
                None => {
                    let s = styles.xf(font, 0);
                    let i = strings.add(text);
                    xml.push_str(&format!("<c r=\"{reference}\"{} t=\"s\"><v>{i}</v></c>", style_attr(s)));
                }
            }
        }
        if !xml.is_empty() {
            self.data.push_str(&format!("<row r=\"{r}\">{xml}</row>"));
        }
        self.row = self.row.saturating_add(1);
    }

    fn table(&mut self, t: &Table, styles: &mut Styles, strings: &mut Strings) {
        let mut edges = t.cols.clone();
        edges.push(t.rect[2]);
        for (i, w) in edges.windows(2).enumerate() {
            if let [a, b] = w {
                Self::note_width(&mut self.geom, i, (b - a) / CHAR_PT);
            }
        }
        for row in &t.rows {
            let mut cells = Vec::with_capacity(row.len());
            let mut col = 0u32;
            for c in row {
                cells.push((col, c.text.as_str(), Font::new(c.size, c.bold, c.italic, c.color), c.span.max(1)));
                col = col.saturating_add(c.span.max(1).min(MAX_COLS as usize) as u32);
            }
            self.put_row(&cells, styles, strings);
        }
    }

    fn xml(&self) -> String {
        let mut s = format!("{XML_DECL}<worksheet xmlns=\"{MAIN_NS}\" xmlns:r=\"{REL_NS}\">");
        let n = self.geom.len().max(self.text_len.len());
        let mut cols = String::new();
        for i in 0..n {
            let geom = self.geom.get(i).copied().unwrap_or(0.0);
            let len = self.text_len.get(i).copied().unwrap_or(0) as f64;
            // Table columns follow the page; numbers must fit (Excel shows ### otherwise).
            let w = geom.max(if len > 0.0 { len + 2.0 } else { 0.0 });
            if w > 0.0 {
                let w = (w + 1.0).clamp(4.0, 80.0);
                cols.push_str(&format!("<col min=\"{0}\" max=\"{0}\" width=\"{w:.2}\" customWidth=\"1\"/>", i + 1));
            }
        }
        if !cols.is_empty() {
            s.push_str(&format!("<cols>{cols}</cols>"));
        }
        if self.data.is_empty() {
            s.push_str("<sheetData/>");
        } else {
            s.push_str(&format!("<sheetData>{}</sheetData>", self.data));
        }
        if !self.merges.is_empty() {
            s.push_str(&format!("<mergeCells count=\"{}\">", self.merges.len()));
            for m in &self.merges {
                s.push_str(&format!("<mergeCell ref=\"{m}\"/>"));
            }
            s.push_str("</mergeCells>");
        }
        s.push_str("</worksheet>");
        s
    }
}

fn style_attr(s: usize) -> String {
    if s == 0 { String::new() } else { format!(" s=\"{s}\"") }
}

/// An Excel workbook (.xlsx): one worksheet per page, named "Page 1", "Page 2", …
pub fn xlsx(pages: &[Page], title: &str) -> Vec<u8> {
    let mut styles = Styles::new();
    let mut strings = Strings::default();
    let mut sheets = Vec::new();
    for page in pages {
        let mut sheet = Sheet::new();
        // Whether anything is on the sheet yet, and whether it was a table (a blank row
        // separates tables from the text around them).
        let mut last: Option<bool> = None;
        for it in items(std::slice::from_ref(page)) {
            if sheet.full {
                break;
            }
            match it {
                Item::Para(b, _) => {
                    if last == Some(true) {
                        sheet.skip_row();
                    }
                    sheet.put_row(&[(0, &b.text, Font::new(b.size, b.bold, b.italic, b.color), 1)], &mut styles, &mut strings);
                    last = Some(false);
                }
                Item::Table(t) => {
                    if last.is_some() {
                        sheet.skip_row();
                    }
                    sheet.table(&t, &mut styles, &mut strings);
                    last = Some(true);
                }
                Item::Img(_) | Item::PageBreak => {}
            }
        }
        sheets.push(sheet);
    }
    // A workbook needs at least one sheet.
    if sheets.is_empty() {
        sheets.push(Sheet::new());
    }

    let mut types = format!(
        "{XML_DECL}<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\
<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>\
<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\
<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\
<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>\
<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
    );
    let mut workbook = format!("{XML_DECL}<workbook xmlns=\"{MAIN_NS}\" xmlns:r=\"{REL_NS}\"><bookViews><workbookView/></bookViews><sheets>");
    let mut wb_rels = format!("{XML_DECL}<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
    for i in 1..=sheets.len() {
        types.push_str(&format!(
            "<Override PartName=\"/xl/worksheets/sheet{i}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        ));
        // "Page N" stays well within Excel's 31-character sheet names.
        workbook.push_str(&format!("<sheet name=\"Page {i}\" sheetId=\"{i}\" r:id=\"rId{i}\"/>"));
        wb_rels.push_str(&format!("<Relationship Id=\"rId{i}\" Type=\"{REL_NS}/worksheet\" Target=\"worksheets/sheet{i}.xml\"/>"));
    }
    types.push_str("</Types>");
    workbook.push_str("</sheets></workbook>");
    wb_rels.push_str(&format!(
        "<Relationship Id=\"rIdStyles\" Type=\"{REL_NS}/styles\" Target=\"styles.xml\"/>\
<Relationship Id=\"rIdStrings\" Type=\"{REL_NS}/sharedStrings\" Target=\"sharedStrings.xml\"/></Relationships>"
    ));
    let rels = format!(
        "{XML_DECL}<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\
<Relationship Id=\"rId1\" Type=\"{REL_NS}/officeDocument\" Target=\"xl/workbook.xml\"/>\
<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>"
    );

    let mut z = Zip::default();
    z.add("[Content_Types].xml", types.as_bytes(), true);
    z.add("_rels/.rels", rels.as_bytes(), true);
    z.add("docProps/core.xml", core_xml(title).as_bytes(), true);
    z.add("xl/workbook.xml", workbook.as_bytes(), true);
    z.add("xl/_rels/workbook.xml.rels", wb_rels.as_bytes(), true);
    z.add("xl/styles.xml", styles.xml().as_bytes(), true);
    z.add("xl/sharedStrings.xml", strings.xml().as_bytes(), true);
    for (i, sheet) in sheets.iter().enumerate() {
        z.add(&format!("xl/worksheets/sheet{}.xml", i + 1), sheet.xml().as_bytes(), true);
    }
    z.finish()
}

use std::io::Write;
use std::sync::Arc;

use pdfcraft_cos::{Dict, Document, Object, SaveOptions, write_full};

use super::*;

// ── fixtures ────────────────────────────────────────────────────────────────────────────────

fn crc32(bytes: &[u8]) -> u32 {
    let mut c = 0xFFFF_FFFFu32;
    for &b in bytes {
        c ^= u32::from(b);
        for _ in 0..8 {
            c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
        }
    }
    !c
}

/// A zip package of `entries` (deflated when `deflate`).
pub(super) fn zip_of(entries: &[(&str, &[u8])], deflate: bool) -> Vec<u8> {
    let mut out = Vec::new();
    let mut central = Vec::new();
    for (name, data) in entries {
        let offset = out.len() as u32;
        let (method, body) = if deflate {
            let mut e = flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::default());
            e.write_all(data).unwrap();
            (8u16, e.finish().unwrap())
        } else {
            (0u16, data.to_vec())
        };
        let crc = crc32(data);
        out.extend_from_slice(&0x0403_4b50u32.to_le_bytes());
        out.extend_from_slice(&[20, 0, 0, 0]);
        out.extend_from_slice(&method.to_le_bytes());
        out.extend_from_slice(&[0, 0, 0, 0]);
        out.extend_from_slice(&crc.to_le_bytes());
        out.extend_from_slice(&(body.len() as u32).to_le_bytes());
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(&(name.len() as u16).to_le_bytes());
        out.extend_from_slice(&[0, 0]);
        out.extend_from_slice(name.as_bytes());
        out.extend_from_slice(&body);
        central.extend_from_slice(&0x0201_4b50u32.to_le_bytes());
        central.extend_from_slice(&[20, 0, 20, 0, 0, 0]);
        central.extend_from_slice(&method.to_le_bytes());
        central.extend_from_slice(&[0, 0, 0, 0]);
        central.extend_from_slice(&crc.to_le_bytes());
        central.extend_from_slice(&(body.len() as u32).to_le_bytes());
        central.extend_from_slice(&(data.len() as u32).to_le_bytes());
        central.extend_from_slice(&(name.len() as u16).to_le_bytes());
        central.extend_from_slice(&[0; 12]);
        central.extend_from_slice(&offset.to_le_bytes());
        central.extend_from_slice(name.as_bytes());
    }
    let dir_offset = out.len() as u32;
    out.extend_from_slice(&central);
    out.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
    out.extend_from_slice(&[0, 0, 0, 0]);
    out.extend_from_slice(&(entries.len() as u16).to_le_bytes());
    out.extend_from_slice(&(entries.len() as u16).to_le_bytes());
    out.extend_from_slice(&(central.len() as u32).to_le_bytes());
    out.extend_from_slice(&dir_offset.to_le_bytes());
    out.extend_from_slice(&[0, 0]);
    out
}

/// A small PNG of `w × h` pixels in one colour.
pub(super) fn png(w: u32, h: u32, rgb: [u8; 3]) -> Vec<u8> {
    let mut out = Vec::new();
    {
        let mut enc = png::Encoder::new(&mut out, w, h);
        enc.set_color(png::ColorType::Rgb);
        enc.set_depth(png::BitDepth::Eight);
        let mut wr = enc.write_header().unwrap();
        wr.write_image_data(&rgb.repeat((w * h) as usize)).unwrap();
    }
    out
}

const W: &str = r#"xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture""#;

/// A .docx whose body is `body` (WordprocessingML), with optional styles and numbering parts
/// and a picture `media/image1.png` as relationship rIdImg.
pub(super) fn docx(body: &str, styles: Option<&str>, numbering: Option<&str>) -> Vec<u8> {
    docx_with(body, styles, numbering, &[])
}

/// [`docx`] with header or footer parts: (relationship id, kind `header`/`footer`, body XML).
pub(super) fn docx_with(body: &str, styles: Option<&str>, numbering: Option<&str>, parts: &[(&str, &str, &str)]) -> Vec<u8> {
    let document = format!(r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}><w:body>{body}</w:body></w:document>"#);
    let rels = r#"<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"#;
    let extra: String = parts
        .iter()
        .map(|(id, kind, _)| {
            format!(
                r#"<Relationship Id="{id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/{kind}" Target="{id}.xml"/>"#
            )
        })
        .collect();
    let doc_rels = format!(
        r#"<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdS" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rIdN" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/><Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>{extra}</Relationships>"#
    );
    let part_xml: Vec<(String, String)> = parts
        .iter()
        .map(|(id, kind, xml)| {
            let root = match *kind {
                "header" => "hdr",
                "footer" => "ftr",
                other => other,
            };
            (format!("word/{id}.xml"), format!(r#"<?xml version="1.0"?><w:{root} {W}>{xml}</w:{root}>"#))
        })
        .collect();
    let styles = styles.map(|s| format!(r#"<?xml version="1.0" encoding="UTF-8"?><w:styles {W}>{s}</w:styles>"#));
    let numbering = numbering.map(|n| format!(r#"<?xml version="1.0" encoding="UTF-8"?><w:numbering {W}>{n}</w:numbering>"#));
    let image = png(40, 20, [200, 30, 30]);
    let mut entries: Vec<(&str, &[u8])> =
        vec![("_rels/.rels", rels.as_bytes()), ("word/document.xml", document.as_bytes()), ("word/_rels/document.xml.rels", doc_rels.as_bytes())];
    if let Some(s) = &styles {
        entries.push(("word/styles.xml", s.as_bytes()));
    }
    if let Some(n) = &numbering {
        entries.push(("word/numbering.xml", n.as_bytes()));
    }
    entries.push(("word/media/image1.png", &image));
    for (name, xml) in &part_xml {
        entries.push((name, xml.as_bytes()));
    }
    zip_of(&entries, true)
}

pub(super) fn para(text: &str) -> String {
    format!("<w:p><w:r><w:t xml:space=\"preserve\">{text}</w:t></w:r></w:p>")
}

// ── checking the output ─────────────────────────────────────────────────────────────────────

pub(super) fn reopen(doc: &Document) -> Document {
    let bytes = write_full(doc, &SaveOptions::default()).unwrap();
    hayro_syntax::Pdf::new(bytes.clone()).expect("parses");
    dump(&bytes);
    Document::open(Arc::new(bytes)).unwrap()
}

/// With `PDFCRAFT_OFFICE_DUMP` set to a folder, every converted test document is written there
/// (for looking at the result).
fn dump(bytes: &[u8]) {
    if let Ok(dir) = std::env::var("PDFCRAFT_OFFICE_DUMP") {
        let name = std::thread::current().name().unwrap_or("doc").replace("::", "_");
        let mut path = std::path::PathBuf::from(dir).join(format!("{name}.pdf"));
        let mut n = 2;
        while path.exists() {
            path = path.with_file_name(format!("{name}-{n}.pdf"));
            n += 1;
        }
        std::fs::write(path, bytes).unwrap();
    }
}

pub(super) fn pages(doc: &Document) -> Vec<Dict> {
    let pages = doc.get(doc.root().unwrap()).as_dict().unwrap().reference(b"Pages").unwrap();
    let kids = doc.get(pages).as_dict().unwrap().get(b"Kids").unwrap().as_array().unwrap().clone();
    kids.iter().map(|k| doc.resolve(k).as_dict().cloned().unwrap()).collect()
}

pub(super) fn media(d: &Dict) -> (f64, f64) {
    let m: Vec<f64> = d.get(b"MediaBox").unwrap().as_array().unwrap().iter().map(|o| o.as_f64().unwrap()).collect();
    (m[2], m[3])
}

pub(super) fn content(doc: &Document, page: &Dict) -> String {
    match &*doc.resolve(page.get(b"Contents").unwrap()) {
        // Latin-1, so each WinAnsi byte stays one character.
        Object::Stream(s) => s.decoded().unwrap().iter().map(|&b| b as char).collect(),
        _ => panic!("no content stream"),
    }
}

/// The strings a page shows, in drawing order, joined by spaces.
pub(super) fn text(doc: &Document, page: &Dict) -> String {
    let b: Vec<char> = content(doc, page).chars().collect();
    let mut out = Vec::new();
    let mut i = 0;
    while i < b.len() {
        if b[i] == '(' {
            let mut s = String::new();
            i += 1;
            while i < b.len() && b[i] != ')' {
                if b[i] == '\\' && i + 1 < b.len() {
                    i += 1;
                }
                s.push(b[i]);
                i += 1;
            }
            out.push(s);
        }
        i += 1;
    }
    out.join(" ")
}

fn convert(name: &str, bytes: &[u8]) -> Document {
    reopen(&from_office(name, bytes).unwrap())
}

fn err(name: &str, bytes: &[u8]) -> String {
    match from_office(name, bytes) {
        Err(CreateError::Office(n, m)) => {
            assert_eq!(n, name);
            m
        }
        other => panic!("expected an Office error, got {other:?}"),
    }
}

/// Text is measured with the standard-14 metrics (vendored hayro-interpret patch 51), so lines
/// break where the viewer's glyphs actually end.
#[test]
fn standard_font_widths_come_from_the_metrics() {
    use super::draw::{Face, Family, text_width};
    let face = |family, bold, italic| Face { family, bold, italic };
    assert_eq!(text_width(face(Family::Sans, false, false), 1000.0, "i"), 222.0);
    assert_eq!(text_width(face(Family::Sans, true, false), 1000.0, "i"), 278.0);
    assert_eq!(text_width(face(Family::Serif, false, false), 1000.0, "i"), 278.0);
    assert_eq!(text_width(face(Family::Serif, false, true), 1000.0, "W"), 833.0);
    assert_eq!(text_width(face(Family::Mono, true, true), 1000.0, "iW"), 1200.0);
    assert_eq!(text_width(face(Family::Sans, false, false), 1000.0, "\u{20AC}"), 556.0, "the euro sign by its WinAnsi code");
    assert_eq!(super::draw::family_of("Georgia"), Family::Serif);
    assert_eq!(super::draw::family_of("Consolas"), Family::Mono);
    assert_eq!(super::draw::family_of("Calibri"), Family::Sans);
    assert_eq!(super::draw::family_of("Noto Sans Serif-ish"), Family::Sans);
}

// ── Word ────────────────────────────────────────────────────────────────────────────────────

const STYLES: &str = r#"<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="0"/></w:pPr><w:rPr><w:rFonts w:ascii="Cambria" w:hAnsi="Cambria"/><w:color w:val="2F5496"/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/></w:style>
<w:style w:type="character" w:styleId="Strong"><w:name w:val="Strong"/><w:rPr><w:b/></w:rPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr></w:style>"#;

const NUMBERING: &str = r#"<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#xF0B7;"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr><w:rPr><w:rFonts w:ascii="Symbol" w:hAnsi="Symbol"/></w:rPr></w:lvl></w:abstractNum>
<w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/><w:pPr><w:ind w:left="1440" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>"#;

fn item(num: u32, lvl: u32, text: &str) -> String {
    format!(r#"<w:p><w:pPr><w:numPr><w:ilvl w:val="{lvl}"/><w:numId w:val="{num}"/></w:numPr></w:pPr><w:r><w:t>{text}</w:t></w:r></w:p>"#)
}

fn word_sample() -> Vec<u8> {
    let mut body = String::new();
    body.push_str(r#"<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Quarterly report</w:t></w:r></w:p>"#);
    body.push_str(r#"<w:p><w:r><w:t xml:space="preserve">Sales were </w:t></w:r><w:r><w:rPr><w:rStyle w:val="Strong"/></w:rPr><w:t>strong</w:t></w:r><w:r><w:t xml:space="preserve"> and </w:t></w:r><w:r><w:rPr><w:i/><w:color w:val="C00000"/><w:sz w:val="28"/></w:rPr><w:t>growing</w:t></w:r><w:r><w:t>.</w:t></w:r></w:p>"#);
    body.push_str(
        r#"<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:u w:val="single"/></w:rPr><w:t>Centred and underlined</w:t></w:r></w:p>"#,
    );
    body.push_str(&item(1, 0, "First bullet"));
    body.push_str(&item(1, 0, "Second bullet"));
    body.push_str(&item(2, 0, "Step one"));
    body.push_str(&item(2, 1, "Detail a"));
    body.push_str(&item(2, 1, "Detail b"));
    body.push_str(&item(2, 0, "Step two"));
    body.push_str(r#"<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/></w:tblPr><w:tblGrid><w:gridCol w:w="3000"/><w:gridCol w:w="2000"/><w:gridCol w:w="2000"/></w:tblGrid>
<w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9E2F3"/></w:tcPr><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Region</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Q1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Q2</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>North, with a long name that wraps inside its cell</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>120</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>135</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>Spanning two columns</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>255</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"#);
    body.push_str(r#"<w:p><w:r><w:drawing><wp:inline><wp:extent cx="1270000" cy="635000"/><a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rIdImg"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"#);
    body.push_str(r#"<w:p><w:r><w:t>Before the break</w:t></w:r><w:r><w:br w:type="page"/></w:r><w:r><w:t>After the break</w:t></w:r></w:p>"#);
    body.push_str(r#"<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Second heading</w:t></w:r></w:p>"#);
    body.push_str(&para("The end."));
    body.push_str(r#"<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708"/></w:sectPr>"#);
    docx(&body, Some(STYLES), Some(NUMBERING))
}

#[test]
fn word_headings_runs_lists_tables_images_and_page_breaks() {
    let doc = convert("report.docx", &word_sample());
    let p = pages(&doc);
    assert_eq!(p.len(), 2, "a page break starts a second page");
    let (w, h) = media(&p[0]);
    assert!((w - 595.3).abs() < 0.5 && (h - 841.9).abs() < 0.5, "A4 from w:pgSz: {w}×{h}");
    let first = text(&doc, &p[0]);
    let order = [
        "Quarterly report",
        "Sales were",
        "strong",
        "and",
        "growing",
        "Centred and underlined",
        "First bullet",
        "1.",
        "Step one",
        "a)",
        "Detail a",
        "b)",
        "Detail b",
        "2.",
        "Step two",
        "Region",
        "Q1",
        "North, with a long",
        "Spanning two columns",
        "255",
        "Before the break",
    ];
    let mut at = 0;
    for s in order {
        let found = first[at..].find(s).unwrap_or_else(|| panic!("{s:?} missing after {at} in {first}"));
        at += found + s.len();
    }
    assert!(first.contains("\u{95}"), "a bullet: {first}");
    assert!(!first.contains("After the break"));
    let second = text(&doc, &p[1]);
    assert!(second.starts_with("After the break") && second.contains("Second heading"), "{second}");
    let c = content(&doc, &p[0]);
    // Heading 1: Cambria → Times at 16 pt in its colour; Strong is bold; the run in 14 pt italic.
    assert!(c.contains("/F4 16 Tf 0.184 0.329 0.588 rg"), "{c}");
    assert!(c.contains("/F2 11 Tf"), "bold Helvetica for the Strong run");
    assert!(c.contains("/F1 14 Tf 0.753 0 0 rg"), "italic red 14 pt");
    assert!(c.contains("re f"), "cell shading");
    assert!(c.contains("/Im0 Do"), "the picture");
    // The picture is 100 × 50 pt (from wp:extent).
    assert!(c.contains("q 100 0 0 50 "), "{c}");
}

#[test]
fn word_without_styles_still_has_headings_and_letter_pages() {
    let body = format!(
        r#"<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Plain heading</w:t></w:r></w:p>{}<w:tbl><w:tr><w:tc><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>b</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"#,
        para("Body text")
    );
    let doc = convert("plain.docx", &docx(&body, None, None));
    let p = pages(&doc);
    assert_eq!(p.len(), 1);
    assert_eq!(media(&p[0]), (612.0, 792.0), "US Letter by default");
    let c = content(&doc, &p[0]);
    assert!(c.contains("/F2 16 Tf") && c.contains("(Plain heading) Tj"), "Heading 1 is bold and larger without styles.xml: {c}");
    assert!(c.contains("/F0 11 Tf") && c.contains("(Body text) Tj"));
    assert!(c.contains("(a) Tj") && c.contains("(b) Tj"), "a table without a grid still shows its cells");
}

#[test]
fn word_headers_footers_page_numbers_and_tab_leaders() {
    let header = para("Running head");
    let first = para("Cover header");
    let footer = r#"<w:p><w:r><w:t xml:space="preserve">Page </w:t></w:r><w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple><w:r><w:t xml:space="preserve"> of </w:t></w:r><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> NUMPAGES </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>9</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>"#;
    // A table-of-contents line: a right tab stop at the margin with a dot leader.
    let toc = r#"<w:p><w:pPr><w:tabs><w:tab w:val="right" w:leader="dot" w:pos="9360"/></w:tabs></w:pPr><w:r><w:t>Introduction</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>7</w:t></w:r></w:p>"#;
    let mut body = String::from(toc);
    body.push_str(r#"<w:p><w:r><w:br w:type="page"/></w:r></w:p>"#);
    body.push_str(&para("Second page body"));
    body.push_str(r#"<w:sectPr><w:headerReference w:type="default" r:id="rIdH"/><w:headerReference w:type="first" r:id="rIdH1"/><w:footerReference w:type="default" r:id="rIdF"/><w:titlePg/><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720"/></w:sectPr>"#);
    let bytes = docx_with(&body, None, None, &[("rIdH", "header", &header), ("rIdH1", "header", &first), ("rIdF", "footer", footer)]);
    let doc = convert("toc.docx", &bytes);
    let p = pages(&doc);
    assert_eq!(p.len(), 2);
    let one = text(&doc, &p[0]);
    let two = text(&doc, &p[1]);
    assert!(one.contains("Cover header") && !one.contains("Running head"), "the first page has its own header: {one}");
    assert!(two.contains("Running head") && !two.contains("Cover header"), "{two}");
    assert!(!one.contains("Page"), "a different first page without a first-page footer has none: {one}");
    assert!(two.contains("Page 2 of 2"), "page numbers replace the cached field results: {two}");
    assert!(!two.contains('9'));
    // The leader dots run up to the page number, which ends at the right margin (540 pt).
    let c = content(&doc, &p[0]);
    assert!(c.contains("(......"), "dot leader: {c}");
    let seven = c.lines().find(|l| l.contains("(7) Tj")).unwrap();
    let x: f64 = seven.split(" Td").next().unwrap().split_whitespace().rev().nth(1).unwrap().parse().unwrap();
    let w = super::draw::text_width(super::draw::Face::default(), 11.0, "7");
    assert!((x + w - 540.0).abs() < 0.5, "right tab: {seven}");
}

#[test]
fn word_footnotes_follow_the_text() {
    let body = r#"<w:p><w:r><w:t>A claim</w:t></w:r><w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteReference w:id="2"/></w:r><w:r><w:t xml:space="preserve"> and more text.</w:t></w:r></w:p>"#;
    let notes = r#"<w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote><w:footnote w:id="2"><w:p><w:r><w:footnoteRef/></w:r><w:r><w:t>The source of the claim.</w:t></w:r></w:p></w:footnote>"#;
    let doc = convert("notes.docx", &docx_with(body, None, None, &[("rIdFN", "footnotes", notes)]));
    let t = text(&doc, &pages(&doc)[0]);
    let claim = t.find("A claim").unwrap();
    let source = t.find("The source of the claim.").unwrap_or_else(|| panic!("{t}"));
    assert!(claim < source && t[claim..source].matches('1').count() == 2, "the reference and the note are numbered 1: {t}");
}

#[test]
fn word_long_documents_paginate_and_split_tall_rows() {
    let mut body = String::new();
    for i in 0..120 {
        body.push_str(&para(&format!("Paragraph number {i} with enough words to fill part of a line.")));
    }
    // A table row taller than a page splits across pages instead of being lost.
    let cell: String = (0..80).map(|i| para(&format!("cell line {i}"))).collect();
    body.push_str(&format!("<w:tbl><w:tblGrid><w:gridCol w:w=\"4000\"/></w:tblGrid><w:tr><w:tc>{cell}</w:tc></w:tr></w:tbl>"));
    let doc = convert("long.docx", &docx(&body, None, None));
    let p = pages(&doc);
    assert!(p.len() >= 4, "{} pages", p.len());
    let all: String = p.iter().map(|pg| text(&doc, pg)).collect::<Vec<_>>().join(" ");
    for i in [0, 59, 119] {
        assert!(all.contains(&format!("Paragraph number {i} ")), "paragraph {i}");
    }
    assert!(all.contains("cell line 0") && all.contains("cell line 79"), "every line of the tall row is kept");
}

// ── Excel ───────────────────────────────────────────────────────────────────────────────────

const S: &str = r#"xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships""#;

fn xlsx(sheets: &[(&str, &str, bool)], shared: &[&str]) -> Vec<u8> {
    let mut wb_sheets = String::new();
    let mut wb_rels = String::new();
    let mut parts = Vec::new();
    for (i, (name, data, hidden)) in sheets.iter().enumerate() {
        let n = i + 1;
        let state = if *hidden { r#" state="hidden""# } else { "" };
        wb_sheets.push_str(&format!(r#"<sheet name="{name}" sheetId="{n}"{state} r:id="rId{n}"/>"#));
        wb_rels.push_str(&format!(r#"<Relationship Id="rId{n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet{n}.xml"/>"#));
        parts.push((format!("xl/worksheets/sheet{n}.xml"), format!(r#"<?xml version="1.0"?><worksheet {S}>{data}</worksheet>"#)));
    }
    wb_rels.push_str(r#"<Relationship Id="rIdS" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rIdT" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>"#);
    let workbook = format!(r#"<?xml version="1.0"?><workbook {S}><sheets>{wb_sheets}</sheets></workbook>"#);
    let rels = format!(
        r#"<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">{wb_rels}</Relationships>"#
    );
    let sst: String = shared.iter().map(|s| if s.starts_with('<') { format!("<si>{s}</si>") } else { format!("<si><t>{s}</t></si>") }).collect();
    let sst = format!(r#"<?xml version="1.0"?><sst {S} count="{0}" uniqueCount="{0}">{sst}</sst>"#, shared.len());
    let styles = format!(
        r#"<?xml version="1.0"?><styleSheet {S}><numFmts count="1"><numFmt numFmtId="164" formatCode="0.0%"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><color rgb="FFC00000"/><name val="Cambria"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill></fills>
<borders count="2"><border/><border><bottom style="thin"><color rgb="FF000000"/></bottom></border></borders>
<cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0"><alignment horizontal="center"/></xf><xf numFmtId="4" fontId="0" fillId="2" borderId="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0"/><xf numFmtId="14" fontId="0" fillId="0" borderId="0"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0"/></cellXfs></styleSheet>"#
    );
    let root_rels = r#"<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"#;
    let mut entries: Vec<(&str, &[u8])> = vec![
        ("_rels/.rels", root_rels.as_bytes()),
        ("xl/workbook.xml", workbook.as_bytes()),
        ("xl/_rels/workbook.xml.rels", rels.as_bytes()),
        ("xl/styles.xml", styles.as_bytes()),
        ("xl/sharedStrings.xml", sst.as_bytes()),
    ];
    for (name, data) in &parts {
        entries.push((name, data.as_bytes()));
    }
    zip_of(&entries, true)
}

#[test]
fn excel_sheets_strings_numbers_merges_and_hidden_sheets() {
    let first = r#"<cols><col min="1" max="1" width="20" customWidth="1"/></cols><sheetData>
<row r="1"><c r="A1" t="s" s="1"><v>0</v></c></row>
<row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2" s="2"><v>1234.5</v></c><c r="C2" s="3"><v>0.125</v></c></row>
<row r="3"><c r="A3" t="inlineStr"><is><t>Inline</t></is></c><c r="B3" s="4"><v>45292</v></c><c r="C3" t="b"><v>1</v></c></row>
<row r="4"><c r="A4" t="str"><f>A3</f><v>Formula text</v></c><c r="B4" s="5"><v>9876543</v></c><c r="C4"><v>3.14159</v></c></row>
</sheetData><mergeCells count="1"><mergeCell ref="A1:C1"/></mergeCells>"#;
    let second = r#"<sheetData><row r="1"><c r="A1" t="s"><v>3</v></c></row></sheetData>"#;
    let hidden = r#"<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Secret</t></is></c></row></sheetData>"#;
    let bytes = xlsx(
        &[("Summary", first, false), ("Hidden", hidden, true), ("Notes", second, false)],
        &["Annual title", "Revenue", "unused", "<r><t>Rich </t></r><r><rPr><b/></rPr><t>runs</t></r>"],
    );
    let doc = convert("book.xlsx", &bytes);
    let p = pages(&doc);
    assert_eq!(p.len(), 2, "one page per visible sheet");
    let t = text(&doc, &p[0]);
    for s in ["Annual title", "Revenue", "1,234.50", "12.5%", "Inline", "2024-01-01", "TRUE", "Formula text", "9,876,543", "3.14159"] {
        assert!(t.contains(s), "{s:?} in {t}");
    }
    assert!(!t.contains("Secret"));
    let c = content(&doc, &p[0]);
    assert!(c.contains("1 1 0 rg"), "yellow fill: {c}");
    assert!(c.contains("/F6 14 Tf 0.753 0 0 rg"), "bold red 14 pt Cambria → Times-Bold");
    assert!(text(&doc, &p[1]).contains("Rich runs"));
}

#[test]
fn excel_pictures_are_placed_by_their_cell_anchors() {
    let root_rels = rels(&[("rId1", "officeDocument", "xl/workbook.xml")]);
    let workbook = format!(r#"<?xml version="1.0"?><workbook {S}><sheets><sheet name="Logo" sheetId="1" r:id="rId1"/></sheets></workbook>"#);
    let wb_rels = rels(&[("rId1", "worksheet", "worksheets/sheet1.xml")]);
    let sheet = format!(
        r#"<?xml version="1.0"?><worksheet {S}><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Invoice</t></is></c></row></sheetData><drawing r:id="rIdD"/></worksheet>"#
    );
    let sheet_rels = rels(&[("rIdD", "drawing", "../drawings/drawing1.xml")]);
    // The picture spans B2 (offset 6.35 pt right) to D4: columns of 48 pt, rows of 15 pt.
    let drawing = r#"<?xml version="1.0"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><xdr:twoCellAnchor><xdr:from><xdr:col>1</xdr:col><xdr:colOff>80645</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>3</xdr:col><xdr:colOff>80645</xdr:colOff><xdr:row>3</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="Logo"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rIdI"/></xdr:blipFill><xdr:spPr/></xdr:pic><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"#;
    let drawing_rels = rels(&[("rIdI", "image", "../media/image1.png")]);
    let image = png(8, 4, [0, 90, 200]);
    let entries: Vec<(&str, &[u8])> = vec![
        ("_rels/.rels", root_rels.as_bytes()),
        ("xl/workbook.xml", workbook.as_bytes()),
        ("xl/_rels/workbook.xml.rels", wb_rels.as_bytes()),
        ("xl/worksheets/sheet1.xml", sheet.as_bytes()),
        ("xl/worksheets/_rels/sheet1.xml.rels", sheet_rels.as_bytes()),
        ("xl/drawings/drawing1.xml", drawing.as_bytes()),
        ("xl/drawings/_rels/drawing1.xml.rels", drawing_rels.as_bytes()),
        ("xl/media/image1.png", &image),
    ];
    let doc = convert("logo.xlsx", &zip_of(&entries, true));
    let p = pages(&doc);
    let c = content(&doc, &p[0]);
    // Page margins 50.4 / 54 pt; the picture at (48 + 6.35, 15) in the grid, 96 × 30 pt.
    let (_, h) = media(&p[0]);
    let expected = format!("q 96 0 0 30 {} {} cm /Im", 50.4 + 54.35, h - 54.0 - 15.0 - 30.0);
    assert!(c.contains(&expected), "{expected} in {c}");
    assert!(text(&doc, &p[0]).contains("Invoice"));
}

#[test]
fn excel_number_formats() {
    use super::xlsx::test_format as f;
    assert_eq!(f(1234.5, "General"), "1234.5");
    assert_eq!(f(0.1 + 0.2, "General"), "0.3");
    assert_eq!(f(1234.5, "#,##0.00"), "1,234.50");
    assert_eq!(f(-1234.5, "#,##0.00;(#,##0.00)"), "(1,234.50)");
    assert_eq!(f(0.256, "0%"), "26%");
    assert_eq!(f(12.0, "\"$\"#,##0.00"), "$12.00");
    assert_eq!(f(12.0, "[$€-407] #,##0.00"), "€ 12.00");
    assert_eq!(f(1234.0, "0.00E+00"), "1.23E+03");
    assert_eq!(f(45292.75, "yyyy-mm-dd hh:mm"), "2024-01-01 18:00");
    assert_eq!(f(45292.0, "d mmmm yyyy"), "1 January 2024");
    assert_eq!(f(45292.0, "dddd"), "Monday");
    assert_eq!(f(0.5, "h:mm AM/PM"), "12:00 PM");
    assert_eq!(f(60.0, "yyyy-mm-dd"), "1900-02-29", "Excel's 1900 leap day");
    assert_eq!(f(61.0, "yyyy-mm-dd"), "1900-03-01");
    // General numbers shorten to fit a narrow column.
    use super::xlsx::test_general as g;
    assert_eq!(g(1.0 / 7.0, 10), "0.1428571429");
    assert_eq!(g(1.0 / 7.0, 5), "0.14286");
    assert_eq!(g(714.285714, 4), "714.3");
    assert_eq!(g(123_456_789.0, 4), "1E+08");
    assert_eq!(g(-2.6, 1), "-3");
    for v in [f64::NAN, f64::INFINITY, -1e308, 1e-300] {
        let _ = g(v, 3);
        let _ = (f(v, "0.00"), f(v, "yyyy-mm-dd"), f(v, "General"), f(v, "0.00E+00"));
    }
}

#[test]
fn excel_wide_sheets_turn_landscape_and_paginate_across() {
    let mut data = String::from("<sheetData>");
    for r in 1..=3 {
        data.push_str(&format!("<row r=\"{r}\">"));
        for c in 0..40u32 {
            let col = if c < 26 { ((b'A' + c as u8) as char).to_string() } else { format!("A{}", (b'A' + (c - 26) as u8) as char) };
            data.push_str(&format!("<c r=\"{col}{r}\"><v>{}</v></c>", c * 10 + r));
        }
        data.push_str("</row>");
    }
    data.push_str("</sheetData>");
    let doc = convert("wide.xlsx", &xlsx(&[("Wide", &data, false)], &[]));
    let p = pages(&doc);
    assert!(p.len() >= 2, "40 columns don't fit one page: {}", p.len());
    let (w, h) = media(&p[0]);
    assert!(w > h, "landscape");
    let all: String = p.iter().map(|pg| text(&doc, pg)).collect::<Vec<_>>().join(" ");
    assert!(all.contains("393"), "the last column is printed");
}

// ── PowerPoint ──────────────────────────────────────────────────────────────────────────────

const P: &str = r#"xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main""#;

fn rels(items: &[(&str, &str, &str)]) -> String {
    let body: String = items
        .iter()
        .map(|(id, ty, target)| {
            format!(r#"<Relationship Id="{id}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/{ty}" Target="{target}"/>"#)
        })
        .collect();
    format!(r#"<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">{body}</Relationships>"#)
}

fn pptx(slides: &[&str]) -> Vec<u8> {
    let ids: String = (0..slides.len()).map(|i| format!(r#"<p:sldId id="{}" r:id="rIdS{}"/>"#, 256 + i, i + 1)).collect();
    let presentation =
        format!(r#"<?xml version="1.0"?><p:presentation {P}><p:sldIdLst>{ids}</p:sldIdLst><p:sldSz cx="9144000" cy="6858000"/></p:presentation>"#);
    let mut pres_rels: Vec<(String, String, String)> =
        (0..slides.len()).map(|i| (format!("rIdS{}", i + 1), "slide".to_string(), format!("slides/slide{}.xml", i + 1))).collect();
    pres_rels.push(("rIdM".into(), "slideMaster".into(), "slideMasters/slideMaster1.xml".into()));
    let pres_rels = rels(&pres_rels.iter().map(|(a, b, c)| (a.as_str(), b.as_str(), c.as_str())).collect::<Vec<_>>());
    let master = format!(
        r#"<?xml version="1.0"?><p:sldMaster {P}><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="F0F0FF"/></a:solidFill></p:bgPr></p:bg><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="8229600" cy="1143000"/></a:xfrm></p:spPr><p:txBody><a:bodyPr anchor="ctr"/><a:p><a:r><a:t>Master title prompt</a:t></a:r></a:p></p:txBody></p:sp>
<p:sp><p:nvSpPr><p:cNvPr id="3" name="Band"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="6400800"/><a:ext cx="9144000" cy="457200"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="003366"/></a:solidFill></p:spPr></p:sp>
<p:sp><p:nvSpPr><p:cNvPr id="4" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="8229600" cy="4525963"/></a:xfrm></p:spPr><p:txBody><a:bodyPr/><a:p><a:r><a:t>Body prompt</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
<p:txStyles><p:titleStyle><a:lvl1pPr algn="ctr"><a:defRPr sz="4400"><a:solidFill><a:schemeClr val="tx1"/></a:solidFill></a:defRPr></a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr marL="342900" indent="-342900"><a:buChar char="•"/><a:defRPr sz="3200"/></a:lvl1pPr></p:bodyStyle><p:otherStyle/></p:txStyles></p:sldMaster>"#
    );
    let layout = format!(
        r#"<?xml version="1.0"?><p:sldLayout {P}><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="914400" y="914400"/><a:ext cx="7315200" cy="1371600"/></a:xfrm></p:spPr><p:txBody><a:bodyPr/><a:p><a:r><a:t>Layout prompt</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld></p:sldLayout>"#
    );
    let mut owned: Vec<(String, Vec<u8>)> = vec![
        ("_rels/.rels".into(), rels(&[("rId1", "officeDocument", "ppt/presentation.xml")]).into_bytes()),
        ("ppt/presentation.xml".into(), presentation.into_bytes()),
        ("ppt/_rels/presentation.xml.rels".into(), pres_rels.into_bytes()),
        ("ppt/slideMasters/slideMaster1.xml".into(), master.into_bytes()),
        ("ppt/slideLayouts/slideLayout1.xml".into(), layout.into_bytes()),
        ("ppt/slideLayouts/_rels/slideLayout1.xml.rels".into(), rels(&[("rId1", "slideMaster", "../slideMasters/slideMaster1.xml")]).into_bytes()),
        ("ppt/media/image1.png".into(), png(30, 20, [20, 160, 40])),
    ];
    for (i, s) in slides.iter().enumerate() {
        owned.push((
            format!("ppt/slides/slide{}.xml", i + 1),
            format!(r#"<?xml version="1.0"?><p:sld {P}><p:cSld><p:spTree>{s}</p:spTree></p:cSld></p:sld>"#).into_bytes(),
        ));
        owned.push((
            format!("ppt/slides/_rels/slide{}.xml.rels", i + 1),
            rels(&[("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), ("rId2", "image", "../media/image1.png")]).into_bytes(),
        ));
    }
    let entries: Vec<(&str, &[u8])> = owned.iter().map(|(n, d)| (n.as_str(), d.as_slice())).collect();
    zip_of(&entries, true)
}

const TITLE_SLIDE: &str = r#"<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:t>Hello Slide</a:t></a:r></a:p></p:txBody></p:sp>
<p:pic><p:nvPicPr><p:cNvPr id="4" name="Picture"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId2"/></p:blipFill><p:spPr><a:xfrm><a:off x="1270000" y="3810000"/><a:ext cx="2540000" cy="1270000"/></a:xfrm></p:spPr></p:pic>
<p:sp><p:nvSpPr><p:cNvPr id="5" name="Box"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="5080000" y="3810000"/><a:ext cx="2540000" cy="1270000"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="FF8000"/></a:solidFill><a:ln w="25400"><a:solidFill><a:srgbClr val="000000"/></a:solidFill></a:ln></p:spPr><p:txBody><a:bodyPr anchor="ctr"/><a:p><a:pPr algn="ctr"/><a:r><a:rPr sz="2000" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Orange</a:t></a:r></a:p></p:txBody></p:sp>
<p:grpSp><p:nvGrpSpPr><p:cNvPr id="6" name="Group"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1270000" cy="1270000"/><a:chOff x="0" y="0"/><a:chExt cx="2540000" cy="2540000"/></a:xfrm></p:grpSpPr>
<p:sp><p:nvSpPr><p:cNvPr id="7" name="Inner"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="1270000" y="1270000"/><a:ext cx="1270000" cy="1270000"/></a:xfrm><a:prstGeom prst="ellipse"/><a:solidFill><a:srgbClr val="0000FF"/></a:solidFill></p:spPr></p:sp></p:grpSp>"#;

#[test]
fn powerpoint_slides_placeholders_pictures_shapes_and_groups() {
    let second = r#"<p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:t>Second slide</a:t></a:r></a:p></p:txBody></p:sp>"#;
    let doc = convert("deck.pptx", &pptx(&[TITLE_SLIDE, second]));
    let p = pages(&doc);
    assert_eq!(p.len(), 2, "one page per slide");
    assert_eq!(media(&p[0]), (720.0, 540.0), "the slide size");
    let t = text(&doc, &p[0]);
    assert!(t.contains("Hello Slide") && t.contains("Orange"), "{t}");
    assert!(!t.contains("prompt"), "layout and master placeholders aren't drawn: {t}");
    let c = content(&doc, &p[0]);
    // The title takes the layout's frame (72, 72, 576 × 108) and the master's centred 44 pt style.
    let line = c.lines().find(|l| l.contains("(Hello Slide)")).unwrap();
    assert!(line.contains("/F0 44 Tf"), "{line}");
    let x: f64 = line.split(" Td").next().unwrap().split_whitespace().rev().nth(1).unwrap().parse().unwrap();
    let w = super::draw::text_width(super::draw::Face::default(), 44.0, "Hello Slide");
    assert!((x - (72.0 + 7.2 + (576.0 - 14.4 - w) / 2.0)).abs() < 1.0, "centred in the layout's frame: x = {x}");
    assert!(c.contains("0.941 0.941 1 rg"), "the master's background");
    assert!(c.contains("0 0.2 0.4 rg"), "the master's band shape");
    assert!(c.contains("q 200 0 0 100 100 "), "picture at 100,300 sized 200 × 100: {c}");
    assert!(c.contains("1 0.502 0 rg 0 0 0 RG 2 w"), "orange box with a 2 pt outline");
    // The group maps its child space at half scale: the ellipse spans (50, 50)–(100, 100).
    assert!(c.contains("0 0 1 rg") && c.contains("100 465 m"), "group transform: {c}");
    assert!(text(&doc, &p[1]).contains("Second slide"));
}

#[test]
fn powerpoint_rotated_shapes_turn_about_their_centre() {
    let slide = r#"<p:sp><p:nvSpPr><p:cNvPr id="2" name="Turned"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm rot="5400000"><a:off x="1270000" y="1270000"/><a:ext cx="1270000" cy="635000"/></a:xfrm><a:prstGeom prst="rect"/><a:solidFill><a:srgbClr val="00FF00"/></a:solidFill></p:spPr><p:txBody><a:bodyPr/><a:p><a:r><a:t>Sideways</a:t></a:r></a:p></p:txBody></p:sp>"#;
    let doc = convert("turned.pptx", &pptx(&[slide]));
    let c = content(&doc, &pages(&doc)[0]);
    // 90° clockwise about the centre (150, 125) top-down, (150, 415) in PDF space.
    assert!(c.contains("q 0 -1 1 0 -265 565 cm"), "{c}");
    let rotate = c.find(" cm").unwrap();
    assert!(c[rotate..].contains("(Sideways) Tj") && c[rotate..].contains("0 1 0 rg"), "the shape and its text turn together");
}

#[test]
fn powerpoint_tables_and_bullets() {
    let slide = r#"<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:t>First point</a:t></a:r></a:p><a:p><a:r><a:t>Second point</a:t></a:r></a:p></p:txBody></p:sp>
<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="Table"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="457200" y="4000000"/><a:ext cx="6096000" cy="1000000"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tblPr firstRow="1" bandRow="1"><a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId></a:tblPr><a:tblGrid><a:gridCol w="3048000"/><a:gridCol w="3048000"/></a:tblGrid>
<a:tr h="370840"><a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>Name</a:t></a:r></a:p></a:txBody></a:tc><a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>Value</a:t></a:r></a:p></a:txBody></a:tc></a:tr>
<a:tr h="370840"><a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>Alpha</a:t></a:r></a:p></a:txBody></a:tc><a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>1</a:t></a:r></a:p></a:txBody></a:tc></a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>
<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="5" name="Chart"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId9"/></a:graphicData></a:graphic></p:graphicFrame>"#;
    let doc = convert("table.pptx", &pptx(&[slide]));
    let p = pages(&doc);
    let t = text(&doc, &p[0]);
    let order = ["\u{95}", "First point", "\u{95}", "Second point", "Name", "Value", "Alpha", "1"];
    let mut at = 0;
    for s in order {
        at += t[at..].find(s).unwrap_or_else(|| panic!("{s:?} in {t}")) + s.len();
    }
    let c = content(&doc, &p[0]);
    assert!(c.contains("/F0 32 Tf"), "body text at the master's 32 pt, placed by the master's body frame");
    assert!(c.contains("/F2 18 Tf 1 1 1 rg"), "white bold header cells: {c}");
}

// ── RTF ─────────────────────────────────────────────────────────────────────────────────────

#[test]
fn rtf_paragraphs_formatting_unicode_tables_and_page_breaks() {
    let rtf = br"{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0\froman Times New Roman;}{\f1\fswiss Arial;}{\f2\fmodern Courier New;}}{\colortbl;\red255\green0\blue0;}{\*\generator Test;}{\info{\title Hidden title}}
\paperw12240\paperh15840\margl1440\margr1440\margt1440\margb1440
\f0\fs24 Hello {\b bold} and {\i italic} \cf1 red\cf0  text.\par
\pard\qc\f1\fs32 Centred heading\par
\pard\f2\fs20 Mono\tab tabbed caf\'e9 EURO euro\par
\trowd\cellx3000\cellx6000\intbl A1\cell B1\cell\row
\pard\page Second page\par}";
    // The \u escape for the euro sign, written out of band so no tool turns it into a character.
    let rtf = String::from_utf8_lossy(rtf).replace("EURO", &format!("{}u{}?", char::from(92), 8364));
    let doc = convert("letter.rtf", rtf.as_bytes());
    let p = pages(&doc);
    assert_eq!(p.len(), 2);
    assert_eq!(media(&p[0]), (612.0, 792.0));
    let t = text(&doc, &p[0]);
    for s in ["Hello", "bold", "italic", "red", "Centred heading", "Mono", "tabbed caf\u{e9}", "\u{80}", "euro", "A1", "B1"] {
        assert!(t.contains(s), "{s:?} in {t}");
    }
    assert!(!t.contains("Hidden title") && !t.contains("Test") && !t.contains("Arial"), "{t}");
    let c = content(&doc, &p[0]);
    assert!(c.contains("/F4 12 Tf") && c.contains("/F6 12 Tf") && c.contains("/F5 12 Tf"), "Times, bold and italic: {c}");
    assert!(c.contains("1 0 0 rg"), "red");
    assert!(c.contains("/F0 16 Tf"), "Arial → Helvetica 16 pt");
    assert!(c.contains("/F8 10 Tf"), "Courier 10 pt");
    assert!(text(&doc, &p[1]).contains("Second page"));
}

// ── detection and errors ────────────────────────────────────────────────────────────────────

#[test]
fn zip_reader_reads_stored_and_deflated_entries_and_resolves_targets() {
    use super::zip::{Zip, resolve};
    for deflate in [false, true] {
        let bytes = zip_of(&[("word/media/image 1.png", b"one"), ("Word/Document.xml", b"<x/>")], deflate);
        let z = Zip::open(&bytes).unwrap();
        assert_eq!(z.read("word/media/image%201.png").unwrap().as_deref(), Some(&b"one"[..]), "a percent-encoded target");
        assert_eq!(z.read("/word/document.xml").unwrap().as_deref(), Some(&b"<x/>"[..]), "part names ignore case and a leading slash");
        assert_eq!(z.read("word/missing.xml").unwrap(), None);
    }
    assert_eq!(resolve("word/document.xml", "media/a.png").as_deref(), Some("word/media/a.png"));
    assert_eq!(resolve("ppt/slides/slide1.xml", "../media/a.png").as_deref(), Some("ppt/media/a.png"));
    assert_eq!(resolve("xl/workbook.xml", "/xl/worksheets/sheet1.xml").as_deref(), Some("xl/worksheets/sheet1.xml"));
    assert_eq!(resolve("a/b.xml", "../../../../x").as_deref(), Some("x"), "climbing past the root stays at the root");
    assert_eq!(resolve("word/document.xml", "https://example.com/x.png"), None);
    assert!(Zip::open(b"").is_err() && Zip::open(b"PK\x05\x06").is_err());
}

#[test]
fn office_files_are_detected_by_content() {
    let word = word_sample();
    assert_eq!(office_kind("x.bin", &word), Some(OfficeKind::Word), "by content, whatever the name");
    assert_eq!(crate::source_kind("report.docx", &word), Some(crate::SourceKind::Office));
    assert_eq!(office_kind("b.xlsx", &xlsx(&[("S", "<sheetData/>", false)], &[])), Some(OfficeKind::Excel));
    assert_eq!(office_kind("d", &pptx(&[TITLE_SLIDE])), Some(OfficeKind::PowerPoint));
    assert_eq!(office_kind("n.txt", br"{\rtf1 hi}"), Some(OfficeKind::Rtf));
    assert_eq!(office_kind("old.doc", &[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0]), Some(OfficeKind::Legacy));
    assert_eq!(office_kind("broken.docx", b"PK\x03\x04"), Some(OfficeKind::Word), "a damaged package is still a Word file by name");
    assert_eq!(office_kind("archive.zip", &zip_of(&[("a.txt", b"hello")], false)), None, "an ordinary zip isn't an Office file");
    assert_eq!(office_kind("notes.txt", b"plain"), None);
    for ext in ["docx", "xlsx", "pptx", "rtf"] {
        assert!(crate::CONVERTIBLE.contains(&ext));
    }
}

#[test]
fn damaged_and_unsupported_files_give_errors_not_panics() {
    // Legacy binary formats.
    let ole = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 1, 2, 3];
    assert!(err("old.doc", &ole).contains("save it as .docx"));
    assert!(err("old.xls", &ole).contains("save it as .xlsx"));
    assert!(err("old.ppt", &ole).contains("save it as .pptx"));
    // A truncated package.
    let word = word_sample();
    let m = err("cut.docx", &word[..word.len() / 2]);
    assert!(m.contains("Word file is damaged"), "{m}");
    // Not well-formed XML.
    let m = err("bad.docx", &docx("<w:p><w:r><w:t>unclosed", None, None));
    assert!(m.contains("damaged"), "{m}");
    // A package without its main part.
    assert!(err("empty.docx", b"PK\x03\x04").contains("damaged"));
    // A zip bomb: an entry that inflates past the cap.
    let zeros = vec![0u8; (super::zip::MAX_ENTRY_BYTES + 1024) as usize];
    let bomb = zip_of(&[("word/document.xml", &zeros)], true);
    assert!(bomb.len() < 1 << 20, "the bomb is small: {}", bomb.len());
    let m = err("bomb.docx", &bomb);
    assert!(m.contains("too large"), "{m}");
    // Too many entries.
    let names: Vec<String> = (0..super::zip::MAX_ENTRIES + 1).map(|i| format!("f{i}")).collect();
    let many = zip_of(&names.iter().map(|n| (n.as_str(), &b""[..])).collect::<Vec<_>>(), false);
    assert!(err("many.docx", &many).contains("too large"));
    // ZIP64 packages are refused by name.
    let mut z64 = zip_of(&[("word/document.xml", b"<x/>")], false);
    let n = z64.len();
    z64[n - 12..n - 10].copy_from_slice(&[0xFF, 0xFF]);
    z64[n - 14..n - 12].copy_from_slice(&[0xFF, 0xFF]);
    assert!(err("big.docx", &z64).contains("ZIP64"));
    // Not an Office file at all.
    assert!(matches!(from_office("x.bin", b"hello"), Err(CreateError::Office(..))));
}

#[test]
fn hostile_structures_are_bounded() {
    // XML nested deeper than the parser can take on a thread's stack is refused before parsing
    // (it overflowed the stack and aborted the process).
    let nested = "<w:sdt><w:sdtContent>".repeat(5000) + &para("deep") + &"</w:sdtContent></w:sdt>".repeat(5000);
    assert!(err("deep.docx", &docx(&nested, None, None)).contains("damaged"));
    assert!(super::xml::parse_str(&("<a>".repeat(100_000) + &"</a>".repeat(100_000))).is_none());
    assert!(super::xml::parse_str(&format!("<r>{}</r>", "<a/>".repeat(100_000))).is_some(), "self-closing tags don't nest");
    // Nesting within the parser's bound still stops at the converters' own limits: content
    // controls, a basedOn cycle and tables nested in tables.
    let nested = "<w:sdt><w:sdtContent>".repeat(40) + &para("deep") + &"</w:sdtContent></w:sdt>".repeat(40);
    let styles = r#"<w:style w:type="paragraph" w:styleId="A"><w:basedOn w:val="B"/></w:style><w:style w:type="paragraph" w:styleId="B"><w:basedOn w:val="A"/></w:style>"#;
    let cycle = r#"<w:p><w:pPr><w:pStyle w:val="A"/></w:pPr><w:r><w:t>cycle</w:t></w:r></w:p>"#;
    let tables = "<w:tbl><w:tr><w:tc>".repeat(60) + &para("inner") + &"</w:tc></w:tr></w:tbl>".repeat(60);
    let doc = convert("hostile.docx", &docx(&format!("{nested}{cycle}{tables}"), Some(styles), None));
    let all: String = pages(&doc).iter().map(|p| text(&doc, p)).collect();
    assert!(all.contains("cycle"));
    // Groups nested deeper than the limit.
    let deep_group = "<p:grpSp><p:grpSpPr/>".repeat(100) + &"</p:grpSp>".repeat(100);
    convert("groups.pptx", &pptx(&[&deep_group]));
    // Hostile numbers.
    let weird = r#"<w:sectPr><w:pgSz w:w="NaN" w:h="-5"/><w:pgMar w:top="99999999" w:left="-1e308"/></w:sectPr>"#;
    convert("weird.docx", &docx(&(para("x") + weird), None, None));
    let sheet = r#"<cols><col min="1" max="16384" width="1e308"/></cols><sheetData><row r="99999999"><c r="XFD1048576"><v>1e308</v></c><c r="ZZZZ1"><v>1</v></c></row></sheetData><mergeCells><mergeCell ref="A1:XFD1048576"/></mergeCells>"#;
    let _ = from_office("weird.xlsx", &xlsx(&[("S", sheet, false)], &[]));
    convert("weird.rtf", b"{\\rtf1 {{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{\\u-99999999 \\fs-5 \\bin999999999 x");
}

#[test]
fn mutated_files_never_panic() {
    let samples = [
        ("m.docx", word_sample()),
        ("m.pptx", pptx(&[TITLE_SLIDE])),
        ("m.xlsx", xlsx(&[("S", "<sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData>", false)], &["s"])),
    ];
    for (name, bytes) in samples {
        let mut seed = 0x2545_F491u32;
        for i in 0..300 {
            let mut m = bytes.clone();
            for _ in 0..4 {
                seed ^= seed << 13;
                seed ^= seed >> 17;
                seed ^= seed << 5;
                let at = seed as usize % m.len();
                m[at] = (seed >> 8) as u8;
            }
            if i % 10 == 0 {
                m.truncate(seed as usize % m.len());
            }
            let _ = from_office(name, &m);
        }
    }
    let rtf: &[u8] = br"{\rtf1\ansi{\fonttbl{\f0 Arial;}}\f0 Hello {\b world}\par\trowd\cellx100\intbl a\cell\row{\pict\pngblip 89504e47}}";
    for cut in 0..rtf.len() {
        let _ = from_office("m.rtf", &rtf[..cut]);
    }
}

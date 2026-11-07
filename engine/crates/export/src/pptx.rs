//! Export a PDF ▸ PowerPoint presentation (.pptx, PresentationML).
//!
//! One slide per page, sized like the first page. Each paragraph becomes a text box, each image
//! a picture and each detected table a real table, placed where they sit on the page (pages of
//! another size are scaled to fit the slide and centred).

use crate::{Block, Image, Page, Table, Zip, core_xml, esc, rgb8, rotated_size, tables};

const XML_DECL: &str = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
const NS: &str = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" \
xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" \
xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"";
const REL_NS: &str = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
const RELS_OPEN: &str = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">";

/// EMU per point.
const EMU: f64 = 12_700.0;
/// PowerPoint's slide sides: 1 in to 56 in, in points.
const MIN_SIDE: f64 = 72.0;
const MAX_SIDE: f64 = 4032.0;
/// The slide size when there is no page to take it from (10 × 7.5 in).
const DEFAULT_SIZE: [f64; 2] = [720.0, 540.0];
/// Bound on positions and sizes written (points): a few slide widths either way.
const MAX_COORD: f64 = 4.0 * MAX_SIDE;

/// Points to EMU, with non-finite values as 0 and everything within ±`MAX_COORD`.
fn emu(pt: f64) -> i64 {
    // Clamped before the cast, so it cannot truncate.
    if pt.is_finite() { (pt.clamp(-MAX_COORD, MAX_COORD) * EMU).round() as i64 } else { 0 }
}

/// A length in EMU, at least `min_pt`.
fn emu_len(pt: f64, min_pt: f64) -> i64 {
    emu(if pt.is_finite() { pt.max(min_pt) } else { min_pt })
}

/// A slide side for a page side, within PowerPoint's limits.
fn side(pt: f64, default: f64) -> f64 {
    if pt.is_finite() && pt > 0.0 { pt.clamp(MIN_SIDE, MAX_SIDE) } else { default }
}

/// A clockwise rotation in 60000ths of a degree for a counterclockwise angle in degrees.
fn rot(ccw: f64) -> i64 {
    if !ccw.is_finite() {
        return 0;
    }
    let d = (-ccw).rem_euclid(360.0);
    // Within 0..360 after rem_euclid, so the cast cannot truncate.
    ((d * 60_000.0).round() as i64).clamp(0, 21_599_999)
}

/// Font size in hundredths of a point, within DrawingML's 1..=4000 pt.
fn font_size(size: f64, k: f64) -> i64 {
    let v = size * k;
    if v.is_finite() { (v * 100.0).round().clamp(100.0, 400_000.0) as i64 } else { 1100 }
}

/// Maps the page (points, y up) onto the slide (points, y down).
struct Frame {
    k: f64,
    ox: f64,
    oy: f64,
    page_w: f64,
    page_h: f64,
}

impl Frame {
    fn new(page: &Page, slide: [f64; 2]) -> Frame {
        let pw = if page.width.is_finite() && page.width > 0.0 { page.width } else { slide[0] };
        let ph = if page.height.is_finite() && page.height > 0.0 { page.height } else { slide[1] };
        let k = (slide[0] / pw).min(slide[1] / ph);
        let k = if k.is_finite() && k > 0.0 { k } else { 1.0 };
        Frame { k, ox: (slide[0] - pw * k) / 2.0, oy: (slide[1] - ph * k) / 2.0, page_w: pw, page_h: ph }
    }

    fn x(&self, x: f64) -> f64 {
        self.ox + x * self.k
    }

    /// Slide y (down) for a page y (up).
    fn y(&self, y: f64) -> f64 {
        self.oy + (self.page_h - y) * self.k
    }
}

fn xfrm(tag: &str, x: f64, y: f64, w: f64, h: f64, rotation: i64) -> String {
    let rot = if rotation != 0 { format!(" rot=\"{rotation}\"") } else { String::new() };
    format!("<{tag}{rot}><a:off x=\"{}\" y=\"{}\"/><a:ext cx=\"{}\" cy=\"{}\"/></{tag}>", emu(x), emu(y), emu_len(w, 1.0), emu_len(h, 1.0))
}

/// A standard family close in width to the PDF's font, which the reader's machine is unlikely
/// to have: serif faces set as Times New Roman, monospaced ones as Courier New, the rest as
/// Arial (all three have metric-compatible open substitutes). `None` when the name is unknown.
pub(crate) fn typeface(font: &str) -> Option<&'static str> {
    let name = font.to_ascii_lowercase();
    if name.trim().is_empty() {
        return None;
    }
    let any = |words: &[&str]| words.iter().any(|w| name.contains(w));
    Some(if any(&["mono", "courier", "consol", "menlo", "code"]) {
        "Courier New"
    } else if !any(&["sans", "grotesk", "gothic"])
        && any(&[
            "serif",
            "times",
            "roman",
            "garamond",
            "playfair",
            "georgia",
            "baskerville",
            "caslon",
            "bodoni",
            "didot",
            "palatino",
            "cambria",
            "minion",
            "merriweather",
            "lora",
            "crimson",
            "stix",
            "mincho",
            "song",
            "ming",
            "book",
        ])
    {
        "Times New Roman"
    } else {
        "Arial"
    })
}

/// A run: size, bold/italic, colour and typeface.
fn run(text: &str, size: f64, bold: bool, italic: bool, color: [f64; 3], k: f64, font: &str) -> String {
    let [r, g, b] = rgb8(color);
    let face = typeface(font).map_or(String::new(), |f| format!("<a:latin typeface=\"{f}\"/>"));
    format!(
        "<a:r><a:rPr lang=\"en-US\" sz=\"{}\" b=\"{}\" i=\"{}\" dirty=\"0\"><a:solidFill><a:srgbClr val=\"{r:02X}{g:02X}{b:02X}\"/></a:solidFill>{face}</a:rPr><a:t>{}</a:t></a:r>",
        font_size(size, k),
        u8::from(bold),
        u8::from(italic),
        esc(text)
    )
}

/// Paragraphs for a text: one per line.
fn paras(text: &str, size: f64, bold: bool, italic: bool, color: [f64; 3], k: f64, font: &str) -> String {
    text.split('\n').map(|line| format!("<a:p>{}</a:p>", run(line.trim_end_matches('\r'), size, bold, italic, color, k, font))).collect()
}

/// Rows a paragraph keeps as separate lines; longer paragraphs wrap within their box (a
/// substituted font sets their lines at another width).
const MAX_KEPT_ROWS: usize = 3;

fn text_box(id: usize, b: &Block, f: &Frame) -> String {
    let (w, h) = rotated_size(b, MAX_COORD);
    let (cx, cy) = ((b.rect[0] + b.rect[2]) / 2.0, (b.rect[1] + b.rect[3]) / 2.0);
    // The substituted font may set a little wider than the PDF's: give the line some room so it
    // doesn't wrap. Unrotated boxes keep their left edge; rotated ones their centre.
    let w = w * 1.06 + 2.0;
    // One line of the PDF stays one line, however wide the substituted font sets it; paragraphs
    // wrap within their box.
    // A short paragraph whose rows are known keeps them, each a line; a longer one wraps. Both
    // are spaced as on the page.
    let rows = b.lines.len() > 1 && b.lines.len() <= MAX_KEPT_ROWS;
    let one_line = rows || (!b.text.contains('\n') && (b.rotation.abs() > 0.1 || (b.rect[3] - b.rect[1]) < 1.55 * b.size));
    let wrap = if one_line { "none" } else { "square" };
    let (x, y) = if b.rotation.abs() <= 0.1 { (f.x(b.rect[0]), f.y(b.rect[3])) } else { (f.x(cx) - w * f.k / 2.0, f.y(cy) - h * f.k / 2.0) };
    let spacing = b.leading * f.k * 100.0;
    // Exact spacing in hundredths of a point, within DrawingML's 0..=1584 pt.
    let spacing = if spacing.is_finite() && spacing > 0.0 {
        format!("<a:pPr><a:lnSpc><a:spcPts val=\"{}\"/></a:lnSpc></a:pPr>", spacing.round().clamp(1.0, 158_400.0) as i64)
    } else {
        String::new()
    };
    let lines: Vec<&str> = if rows { b.lines.iter().map(String::as_str).collect() } else { b.text.split('\n').collect() };
    let body: String = lines
        .iter()
        .map(|line| format!("<a:p>{spacing}{}</a:p>", run(line.trim_end_matches('\r'), b.size, b.bold, b.italic, b.color, f.k, &b.font)))
        .collect();
    format!(
        "<p:sp><p:nvSpPr><p:cNvPr id=\"{id}\" name=\"TextBox {id}\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>\
<p:spPr>{}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>\
<p:txBody><a:bodyPr wrap=\"{wrap}\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" rtlCol=\"0\" anchor=\"t\"><a:noAutofit/></a:bodyPr><a:lstStyle/>{}</p:txBody></p:sp>",
        xfrm("a:xfrm", x, y, w * f.k, h * f.k, rot(b.rotation)),
        body
    )
}

fn picture(id: usize, rel: &str, im: &Image, f: &Frame) -> String {
    let w = if im.size[0].is_finite() && im.size[0] > 0.0 { im.size[0] } else { im.rect[2] - im.rect[0] };
    let h = if im.size[1].is_finite() && im.size[1] > 0.0 { im.size[1] } else { im.rect[3] - im.rect[1] };
    let (cx, cy) = ((im.rect[0] + im.rect[2]) / 2.0, (im.rect[1] + im.rect[3]) / 2.0);
    let (x, y) = (f.x(cx) - w * f.k / 2.0, f.y(cy) - h * f.k / 2.0);
    format!(
        "<p:pic><p:nvPicPr><p:cNvPr id=\"{id}\" name=\"Picture {id}\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>\
<p:blipFill><a:blip r:embed=\"{rel}\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>\
<p:spPr>{}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>",
        xfrm("a:xfrm", x, y, w * f.k, h * f.k, rot(im.rotation))
    )
}

/// A cell without borders or fill, its text flush with the column edge.
const CELL_PR: &str = "<a:tcPr marL=\"0\" marR=\"0\" marT=\"0\" marB=\"0\"><a:lnL w=\"0\"><a:noFill/></a:lnL><a:lnR w=\"0\"><a:noFill/></a:lnR>\
<a:lnT w=\"0\"><a:noFill/></a:lnT><a:lnB w=\"0\"><a:noFill/></a:lnB><a:noFill/></a:tcPr>";

fn table(id: usize, t: &Table, f: &Frame) -> String {
    let mut edges = t.cols.clone();
    edges.push(t.rect[2]);
    let widths: Vec<i64> = edges.windows(2).map(|w| if let [a, b] = w { emu_len((b - a) * f.k, 1.0) } else { 0 }).collect();
    let ncols = widths.len();
    let nrows = t.rows.len().max(1);
    let uniform = (t.rect[3] - t.rect[1]) / nrows as f64;
    // Each row runs from its top edge to the next row's (the last to the table's bottom), so
    // the rows sit where they do on the page; unknown or out-of-order edges space rows evenly.
    let heights: Vec<i64> = (0..t.rows.len())
        .map(|i| {
            let top = t.row_tops.get(i).copied();
            let next = if i + 1 < t.rows.len() { t.row_tops.get(i + 1).copied() } else { Some(t.rect[1]) };
            let h = match (top, next) {
                (Some(a), Some(b)) if (a - b).is_finite() && a - b > 0.0 => a - b,
                _ => uniform,
            };
            emu_len(h * f.k, 1.0)
        })
        .collect();
    let mut s = String::new();
    for (row, row_h) in t.rows.iter().zip(&heights) {
        s.push_str(&format!("<a:tr h=\"{row_h}\">"));
        let mut used = 0usize;
        for c in row {
            if used >= ncols {
                break;
            }
            let span = c.span.max(1).min(ncols - used);
            let grid_span = if span > 1 { format!(" gridSpan=\"{span}\"") } else { String::new() };
            let body = if c.text.is_empty() { "<a:p/>".to_string() } else { paras(&c.text, c.size, c.bold, c.italic, c.color, f.k, &c.font) };
            s.push_str(&format!("<a:tc{grid_span}><a:txBody><a:bodyPr/><a:lstStyle/>{body}</a:txBody>{CELL_PR}</a:tc>"));
            // Every row has one cell per grid column; the columns a cell spans are merged into it.
            for _ in 1..span {
                s.push_str(&format!("<a:tc hMerge=\"1\"><a:txBody><a:bodyPr/><a:lstStyle/><a:p/></a:txBody>{CELL_PR}</a:tc>"));
            }
            used += span;
        }
        for _ in used..ncols {
            s.push_str(&format!("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p/></a:txBody>{CELL_PR}</a:tc>"));
        }
        s.push_str("</a:tr>");
    }
    let total_w: i64 = widths.iter().fold(0i64, |a, w| a.saturating_add(*w));
    let total_h = heights.iter().fold(0i64, |a, h| a.saturating_add(*h)).max(1);
    let grid: String = widths.iter().map(|w| format!("<a:gridCol w=\"{w}\"/>")).collect();
    format!(
        "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"{id}\" name=\"Table {id}\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>\
<p:xfrm><a:off x=\"{}\" y=\"{}\"/><a:ext cx=\"{total_w}\" cy=\"{total_h}\"/></p:xfrm>\
<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblPr/><a:tblGrid>{grid}</a:tblGrid>{s}</a:tbl></a:graphicData></a:graphic></p:graphicFrame>",
        emu(f.x(t.rect[0])),
        emu(f.y(t.rect[3]))
    )
}

const GROUP: &str = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\
<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>";

/// One slide's XML, and the images it uses (relationship id, media file name, image).
fn slide<'a>(page: &'a Page, size: [f64; 2], media_count: &mut usize) -> (String, Vec<(String, String, &'a Image)>) {
    let f = Frame::new(page, size);
    let mut shapes = String::new();
    let mut media = Vec::new();
    let mut id = 1usize;
    // Artwork at the back, then tables, then text on top. A rendered background already holds
    // the page's pictures (and its vector art), so they aren't placed again.
    let background = page.artwork.as_ref().filter(|im| !im.bytes.is_empty());
    if let Some(im) = background {
        id = id.saturating_add(1);
        *media_count = media_count.saturating_add(1);
        let rel = "rIdImg1".to_string();
        let full = Image { rect: [0.0, 0.0, f.page_w, f.page_h], size: [f.page_w, f.page_h], rotation: 0.0, ..im.clone() };
        shapes.push_str(&picture(id, &rel, &full, &f));
        media.push((rel, format!("image{media_count}.{}", im.ext), im));
    }
    for im in page.images.iter().filter(|im| background.is_none() && !im.bytes.is_empty()) {
        id = id.saturating_add(1);
        *media_count = media_count.saturating_add(1);
        let rel = format!("rIdImg{}", media.len() + 1);
        shapes.push_str(&picture(id, &rel, im, &f));
        media.push((rel, format!("image{media_count}.{}", im.ext), im));
    }
    let (found, consumed) = tables(&page.blocks);
    for t in &found {
        id = id.saturating_add(1);
        shapes.push_str(&table(id, t, &f));
    }
    for (i, b) in page.blocks.iter().enumerate() {
        if consumed.get(i).copied().unwrap_or(false) || b.text.trim().is_empty() {
            continue;
        }
        id = id.saturating_add(1);
        shapes.push_str(&text_box(id, b, &f));
    }
    let xml = format!(
        "{XML_DECL}<p:sld {NS}><p:cSld><p:spTree>{GROUP}{shapes}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    );
    (xml, media)
}

/// Default run properties for a text style level, from the theme's fonts.
fn level_style(size: i64, font: &str) -> String {
    format!(
        "<a:lvl1pPr><a:defRPr sz=\"{size}\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+{font}-lt\"/><a:ea typeface=\"+{font}-ea\"/><a:cs typeface=\"+{font}-cs\"/></a:defRPr></a:lvl1pPr>"
    )
}

fn theme() -> String {
    let colors = [
        ("dk1", "000000"),
        ("lt1", "FFFFFF"),
        ("dk2", "1F2A44"),
        ("lt2", "E8E8E8"),
        ("accent1", "2F6DB5"),
        ("accent2", "D9822B"),
        ("accent3", "7A8C99"),
        ("accent4", "E0B43A"),
        ("accent5", "4A9BD1"),
        ("accent6", "5C9E4A"),
        ("hlink", "1A5FB4"),
        ("folHlink", "8A4FB0"),
    ];
    let clr: String = colors.iter().map(|(n, v)| format!("<a:{n}><a:srgbClr val=\"{v}\"/></a:{n}>")).collect();
    let fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>";
    let fills = fill.repeat(3);
    let lines: String = [6350, 12700, 19050].iter().map(|w| format!("<a:ln w=\"{w}\">{fill}</a:ln>")).collect();
    let effects = "<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3);
    let font = |name: &str| format!("<a:{name}><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:{name}>");
    format!(
        "{XML_DECL}<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"PdfCraft\"><a:themeElements>\
<a:clrScheme name=\"PdfCraft\">{clr}</a:clrScheme><a:fontScheme name=\"PdfCraft\">{}{}</a:fontScheme>\
<a:fmtScheme name=\"PdfCraft\"><a:fillStyleLst>{fills}</a:fillStyleLst><a:lnStyleLst>{lines}</a:lnStyleLst>\
<a:effectStyleLst>{effects}</a:effectStyleLst><a:bgFillStyleLst>{fills}</a:bgFillStyleLst></a:fmtScheme>\
</a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>",
        font("majorFont"),
        font("minorFont")
    )
}

/// A PowerPoint presentation (.pptx): one slide per page, with text boxes, pictures and tables
/// where they sit on the page.
pub fn pptx(pages: &[Page], title: &str) -> Vec<u8> {
    let size = match pages.first() {
        Some(p) => [side(p.width, DEFAULT_SIZE[0]), side(p.height, DEFAULT_SIZE[1])],
        None => DEFAULT_SIZE,
    };
    let (cx, cy) = (emu(size[0]), emu(size[1]));
    let mut media_count = 0usize;
    let slides: Vec<_> = pages.iter().map(|p| slide(p, size, &mut media_count)).collect();

    let mut types = format!(
        "{XML_DECL}<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\
<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>\
<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>\
<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>\
<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>\
<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>\
<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>\
<Override PartName=\"/ppt/presProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presProps+xml\"/>\
<Override PartName=\"/ppt/viewProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml\"/>\
<Override PartName=\"/ppt/tableStyles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml\"/>\
<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
    );
    let mut ids = String::new();
    let mut pres_rels = format!(
        "{XML_DECL}{RELS_OPEN}<Relationship Id=\"rIdMaster\" Type=\"{REL_NS}/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>\
<Relationship Id=\"rIdTheme\" Type=\"{REL_NS}/theme\" Target=\"theme/theme1.xml\"/>\
<Relationship Id=\"rIdPresProps\" Type=\"{REL_NS}/presProps\" Target=\"presProps.xml\"/>\
<Relationship Id=\"rIdViewProps\" Type=\"{REL_NS}/viewProps\" Target=\"viewProps.xml\"/>\
<Relationship Id=\"rIdTableStyles\" Type=\"{REL_NS}/tableStyles\" Target=\"tableStyles.xml\"/>"
    );
    for i in 1..=slides.len() {
        types.push_str(&format!(
            "<Override PartName=\"/ppt/slides/slide{i}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
        ));
        // Slide ids start at 256 and stay below 2^31.
        let id = 255u64.saturating_add(i as u64).min(2_147_483_647);
        ids.push_str(&format!("<p:sldId id=\"{id}\" r:id=\"rIdSlide{i}\"/>"));
        pres_rels.push_str(&format!("<Relationship Id=\"rIdSlide{i}\" Type=\"{REL_NS}/slide\" Target=\"slides/slide{i}.xml\"/>"));
    }
    types.push_str("</Types>");
    pres_rels.push_str("</Relationships>");
    let id_list = if ids.is_empty() { String::new() } else { format!("<p:sldIdLst>{ids}</p:sldIdLst>") };
    let presentation = format!(
        "{XML_DECL}<p:presentation {NS} saveSubsetFonts=\"1\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster\"/></p:sldMasterIdLst>\
{id_list}<p:sldSz cx=\"{cx}\" cy=\"{cy}\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/><p:defaultTextStyle>{}</p:defaultTextStyle></p:presentation>",
        level_style(1800, "mn")
    );
    let master = format!(
        "{XML_DECL}<p:sldMaster {NS}><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>{GROUP}</p:spTree></p:cSld>\
<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>\
<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>\
<p:txStyles><p:titleStyle>{}</p:titleStyle><p:bodyStyle>{}</p:bodyStyle><p:otherStyle>{}</p:otherStyle></p:txStyles></p:sldMaster>",
        level_style(4400, "mj"),
        level_style(2800, "mn"),
        level_style(1800, "mn")
    );
    let master_rels = format!(
        "{XML_DECL}{RELS_OPEN}<Relationship Id=\"rId1\" Type=\"{REL_NS}/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>\
<Relationship Id=\"rId2\" Type=\"{REL_NS}/theme\" Target=\"../theme/theme1.xml\"/></Relationships>"
    );
    let layout = format!(
        "{XML_DECL}<p:sldLayout {NS} type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>{GROUP}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
    );
    let layout_rels = format!(
        "{XML_DECL}{RELS_OPEN}<Relationship Id=\"rId1\" Type=\"{REL_NS}/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>"
    );
    let pres_props = format!("{XML_DECL}<p:presentationPr {NS}/>");
    let view_props = format!("{XML_DECL}<p:viewPr {NS}/>");
    let table_styles = format!(
        "{XML_DECL}<a:tblStyleLst xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" def=\"{{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}}\"/>"
    );
    let rels = format!(
        "{XML_DECL}{RELS_OPEN}<Relationship Id=\"rId1\" Type=\"{REL_NS}/officeDocument\" Target=\"ppt/presentation.xml\"/>\
<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>"
    );

    let mut z = Zip::default();
    z.add("[Content_Types].xml", types.as_bytes(), true);
    z.add("_rels/.rels", rels.as_bytes(), true);
    z.add("docProps/core.xml", core_xml(title).as_bytes(), true);
    z.add("ppt/presentation.xml", presentation.as_bytes(), true);
    z.add("ppt/_rels/presentation.xml.rels", pres_rels.as_bytes(), true);
    z.add("ppt/presProps.xml", pres_props.as_bytes(), true);
    z.add("ppt/viewProps.xml", view_props.as_bytes(), true);
    z.add("ppt/tableStyles.xml", table_styles.as_bytes(), true);
    z.add("ppt/theme/theme1.xml", theme().as_bytes(), true);
    z.add("ppt/slideMasters/slideMaster1.xml", master.as_bytes(), true);
    z.add("ppt/slideMasters/_rels/slideMaster1.xml.rels", master_rels.as_bytes(), true);
    z.add("ppt/slideLayouts/slideLayout1.xml", layout.as_bytes(), true);
    z.add("ppt/slideLayouts/_rels/slideLayout1.xml.rels", layout_rels.as_bytes(), true);
    for (i, (xml, media)) in slides.iter().enumerate() {
        let n = i + 1;
        z.add(&format!("ppt/slides/slide{n}.xml"), xml.as_bytes(), true);
        let mut srels = format!(
            "{XML_DECL}{RELS_OPEN}<Relationship Id=\"rIdLayout\" Type=\"{REL_NS}/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
        );
        for (rel, name, _) in media {
            srels.push_str(&format!("<Relationship Id=\"{rel}\" Type=\"{REL_NS}/image\" Target=\"../media/{name}\"/>"));
        }
        srels.push_str("</Relationships>");
        z.add(&format!("ppt/slides/_rels/slide{n}.xml.rels"), srels.as_bytes(), true);
        for (_, name, im) in media {
            z.add(&format!("ppt/media/{name}"), &im.bytes, false);
        }
    }
    z.finish()
}

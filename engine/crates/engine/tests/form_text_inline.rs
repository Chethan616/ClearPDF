//! Edit text on pages drawn inside form XObjects (as Chrome draws every page): inlining the
//! forms changes no pixels, and an edit there lands in place and undoes. Synthetic PDFs only;
//! the opt-in check runs on a PDF the tester supplies and never becomes a fixture.

use std::sync::Arc;

use pdfcraft_cos::{Document, SaveOptions};
use pdfcraft_engine::{BlockStyle, Edit, Session};
use pdfcraft_render::{PageRenderer, RenderRequest};

fn pdf(objs: &[String]) -> Arc<Vec<u8>> {
    let mut out = b"%PDF-1.7\n".to_vec();
    let mut offsets = Vec::new();
    for (i, o) in objs.iter().enumerate() {
        offsets.push(out.len());
        out.extend_from_slice(format!("{} 0 obj\n{o}\nendobj\n", i + 1).as_bytes());
    }
    let xref = out.len();
    out.extend_from_slice(format!("xref\n0 {}\n0000000000 65535 f \n", objs.len() + 1).as_bytes());
    for o in offsets {
        out.extend_from_slice(format!("{o:010} 00000 n \n").as_bytes());
    }
    out.extend_from_slice(format!("trailer\n<< /Size {} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n", objs.len() + 1).as_bytes());
    Arc::new(out)
}

fn stream(dict: &str, data: &str) -> String {
    format!("<< {dict} /Length {} >>\nstream\n{data}\nendstream", data.len())
}

/// A page the way Chrome writes one, with the things inlining must keep: a flipped page CTM, an
/// isolated group with an opaque background, a clip by the form's box, a gradient pattern, a
/// nested text form with a /Matrix, an artwork form with a blend mode, colliding font and state
/// names, transparency and marked content.
fn chrome_like() -> Arc<Vec<u8>> {
    let page = stream(
        "",
        "0.5 0 0 -0.5 0 400 cm q 1 1 1 rg 0 0 600 800 re f Q q 0 0 600 800 re W n /G0 gs /X0 Do Q BT /F1 20 Tf 1 0 0 -1 40 760 Tm (Page own) Tj ET",
    );
    let form = stream(
        "/Type /XObject /Subtype /Form /BBox [0 0 560 780] /Group << /Type /Group /S /Transparency /I true >> /StructParents 0 \
         /Resources << /Font << /F1 9 0 R /F2 10 0 R >> /ExtGState << /G0 11 0 R /G1 12 0 R >> /Pattern << /P0 13 0 R >> /XObject << /In 6 0 R /Art 7 0 R >> >>",
        "q 0.9 0.95 1 rg /G0 gs /NonStruct <</MCID 0>> BDC 0 0 560 780 re f Q EMC \
         q /Pattern cs /P0 scn 20 20 300 120 re f Q \
         q 1 0 0 1 300 400 cm /G1 gs /Art Do Q \
         q 0 0 1 rg 500 700 200 200 re f Q \
         /P <</MCID 1>> BDC BT /F1 28 Tf 1 0 0 -1 40 80 Tm (Heading in a form) Tj ET EMC \
         BT /F2 16 Tf 0.2 0.2 0.2 rg 1 0 0 -1 40 140 Tm (Body text set in Times inside the page form.) Tj ET \
         q 1 0 0 1 0 300 cm /In Do Q",
    );
    let nested = stream(
        "/Type /XObject /Subtype /Form /BBox [0 0 400 200] /Matrix [1.5 0 0 1.5 20 0] /Resources << /Font << /F1 10 0 R >> >>",
        "0.8 0 0 rg 0 0 100 30 re f BT /F1 12 Tf 0 0 0 rg 1 0 0 -1 10 60 Tm (Nested caption) Tj ET",
    );
    let art = stream(
        "/Type /XObject /Subtype /Form /BBox [0 0 200 200] /Group << /S /Transparency /I true >> /Resources << /ExtGState << /G0 8 0 R >> >>",
        "/G0 gs 1 0.5 0 rg 0 0 150 150 re f 0 0.5 1 rg 50 50 150 150 re f",
    );
    pdf(&[
        "<< /Type /Catalog /Pages 2 0 R >>".into(),
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".into(),
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << /Font << /F1 14 0 R >> /ExtGState << /G0 15 0 R >> /XObject << /X0 5 0 R >> >> /Contents 4 0 R >>".into(),
        page,
        form,
        nested,
        art,
        "<< /ca 0.7 >>".into(),
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".into(),
        "<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>".into(),
        "<< /ca 1 /BM /Normal >>".into(),
        "<< /ca 0.9 /BM /Screen >>".into(),
        "<< /PatternType 2 /Matrix [1 0 0 1 20 0] /Shading << /ShadingType 2 /ColorSpace /DeviceRGB /Coords [0 0 300 0] /Function << /FunctionType 2 /Domain [0 1] /C0 [1 0.8 0] /C1 [0 0.4 0.8] /N 1 >> >> >>".into(),
        "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>".into(),
        "<< /ca 1 >>".into(),
    ])
}

fn pixels(bytes: Arc<Vec<u8>>, page: usize) -> pdfcraft_render::RenderedPage {
    let mut renderer = PageRenderer::new(bytes, Default::default());
    let p = renderer.render(RenderRequest { page, scale: 2.0, ..Default::default() });
    assert!(p.error.is_none(), "{:?}", p.error);
    p
}

/// The largest channel difference between two renders, and how many pixels differ at all.
fn diff(a: &pdfcraft_render::RenderedPage, b: &pdfcraft_render::RenderedPage) -> (u8, usize) {
    assert_eq!((a.width, a.height), (b.width, b.height));
    let (mut most, mut count) = (0u8, 0usize);
    for (p, q) in a.rgba.as_chunks::<4>().0.iter().zip(b.rgba.as_chunks::<4>().0) {
        let d = p.iter().zip(q).map(|(x, y)| x.abs_diff(*y)).max().unwrap_or(0);
        most = most.max(d);
        count += usize::from(d > 0);
    }
    (most, count)
}

fn inlined(bytes: &Arc<Vec<u8>>, page: usize) -> Option<Arc<Vec<u8>>> {
    let mut doc = Document::open(bytes.clone()).unwrap();
    pdfcraft_edit::inline_text_forms(&mut doc, page).unwrap().then(|| Arc::new(pdfcraft_cos::write_full(&doc, &SaveOptions::default()).unwrap()))
}

#[test]
fn inlining_a_chrome_like_page_changes_no_pixels() {
    let original = chrome_like();
    let after = inlined(&original, 0).expect("the page form draws text");
    let (most, count) = diff(&pixels(original, 0), &pixels(after.clone(), 0));
    assert!(most <= 2 && count < 50, "inlining changed {count} pixels by up to {most}");
    // Nothing is left to inline.
    assert!(inlined(&after, 0).is_none());
}

#[test]
fn editing_text_in_a_page_form_lands_in_place_and_undoes() {
    let mut s = Session::new();
    let id = s.open("chrome.pdf", None, chrome_like(), None).unwrap();
    let original = pixels(s.get(id).unwrap().display.clone(), 0);
    let blocks = s.get(id).unwrap().text_blocks(0);
    let texts: Vec<&str> = blocks.iter().map(|b| b.text.as_str()).collect();
    assert_eq!(texts, ["Heading in a form", "Body text set in Times inside the page form.", "Nested caption", "Page own"]);
    let heading = blocks[0].clone();
    let source = s.get(id).unwrap().text_lines(0)[heading.lines[0]].clone();
    s.apply(id, Edit::EditTextBlock { page: 0, block: 0, text: "Edited heading".into(), style: BlockStyle::default() }).unwrap();
    let edited = s.get(id).unwrap();
    let lines = edited.text_lines(0);
    let line = lines.iter().find(|l| l.text == "Edited heading").expect("the edit is listed");
    assert_eq!((line.baseline(), line.base_font.as_str()), (source.baseline(), "Helvetica"));
    assert!((line.size - source.size).abs() < 1e-6);
    assert_eq!(edited.text_blocks(0).len(), 4);
    // Only the heading's pixels changed.
    let after = pixels(edited.display.clone(), 0);
    let (_, count) = diff(&original, &after);
    assert!(count > 0);
    let [x0, y0, x1, y1] = heading.rect;
    let (w, scale) = (after.width as usize, 2.0);
    for (i, (p, q)) in original.rgba.as_chunks::<4>().0.iter().zip(after.rgba.as_chunks::<4>().0).enumerate() {
        if p != q {
            // Device pixels (top-left origin) back to user space (300×400 page, scale 2).
            let (x, y) = ((i % w) as f64 / scale, 400.0 - (i / w) as f64 / scale);
            assert!(
                x >= x0 - 2.0 && x <= x1.max(x0 + 160.0) + 2.0 && y >= y0 - 2.0 && y <= y1 + 2.0,
                "pixel {x},{y} outside {:?} changed",
                heading.rect
            );
        }
    }
    s.undo(id).unwrap();
    assert_eq!(pixels(s.get(id).unwrap().display.clone(), 0).rgba, original.rgba);
    assert_eq!(s.get(id).unwrap().text_blocks(0)[0].text, "Heading in a form");
    s.redo(id).unwrap();
    assert_eq!(s.get(id).unwrap().text_blocks(0)[0].text, "Edited heading");
    let saved = s.save_bytes(id).unwrap();
    let re = s.open("saved.pdf", None, saved, None).unwrap();
    assert_eq!(s.get(re).unwrap().text_blocks(0)[0].text, "Edited heading");
}

/// Opt-in: every page of a PDF the tester supplies (for example one printed by Chrome or Edge)
/// renders the same once its forms are inlined, and its first paragraph can be edited and undone.
#[test]
#[ignore = "requires PDFCRAFT_FORM_TEXT_PDF pointing at a local PDF"]
fn supplied_pdf_inlines_without_visible_change_and_edits() {
    let path = std::env::var("PDFCRAFT_FORM_TEXT_PDF").expect("supply a local PDF path");
    let original = Arc::new(std::fs::read(&path).unwrap());
    let pages = Document::open(original.clone()).map(|d| pdfcraft_model::pages(&d).len()).unwrap();
    let mut inlined_pages = 0;
    for page in 0..pages {
        let Some(after) = inlined(&original, page) else { continue };
        inlined_pages += 1;
        let (most, count) = diff(&pixels(original.clone(), page), &pixels(after, page));
        eprintln!("page {}: inlined, {count} pixels differ (most {most})", page + 1);
        // Gradients re-anchored through a premultiplied pattern matrix may round one level apart.
        assert!(most <= 2 || (most <= 8 && count < 200), "page {}: {count} pixels differ by up to {most}", page + 1);
    }
    eprintln!("{inlined_pages} of {pages} pages had text in forms");
    let mut s = Session::new();
    let id = s.open("supplied.pdf", None, original, None).unwrap();
    for page in 0..pages {
        let blocks = s.get(id).unwrap().text_blocks(page);
        let Some(first) = blocks.first() else { continue };
        let before = first.text.clone();
        s.apply(id, Edit::EditTextBlock { page, block: 0, text: "PdfCraft edit".into(), style: BlockStyle::default() }).unwrap();
        let edited = s.get(id).unwrap().text_blocks(page);
        assert!(edited.iter().any(|b| b.text == "PdfCraft edit"), "page {}: {:?}", page + 1, edited.iter().map(|b| &b.text).collect::<Vec<_>>());
        // Every other paragraph is still there.
        for b in blocks.iter().skip(1) {
            assert!(edited.iter().any(|e| e.text == b.text), "page {}: lost {:?}", page + 1, b.text);
        }
        s.undo(id).unwrap();
        assert_eq!(s.get(id).unwrap().text_blocks(page)[0].text, before, "page {}", page + 1);
        eprintln!("page {}: edited and undid {before:?} ({} paragraphs)", page + 1, blocks.len());
    }
}

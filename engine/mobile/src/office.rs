//! Office documents beside the PDFs: Word documents ([`crate::word`], WordCraft's engine) and
//! PowerPoint presentations ([`crate::slides`], DeckCraft's).
//!
//! Each lives in the same [`MobileSession`](crate::MobileSession) as the PDFs, under a document id
//! of its own ([`OFFICE_IDS`] and up), so the viewer's page, thumbnail, search, selection and Read
//! Aloud paths work unchanged: the session sends each call for such an id to the document's
//! [`OfficeDoc`]. Their text layers are built from the engines' positioned glyphs into the PDF
//! engine's [`PageText`], whose search, word and line logic is then shared.

use std::sync::Arc;

use pdfcraft_render::{PageText, TextGlyph};

use crate::{DocumentInfo, LinkInfo, MobileError, RenderedImage, TileRect};

/// The first document id given to an office document: far above the PDF engine's ids (which count
/// up from 1), so the two never meet.
pub(crate) const OFFICE_IDS: u64 = 1 << 40;

/// The largest file opened (the PDF side has the same 2 GB bound in its reader).
const MAX_FILE: u64 = 2 << 30;

/// An open office document, as the session uses it: pages (Word's pages, a deck's slides) drawn and
/// read like a PDF's.
pub(crate) trait OfficeDoc: Send + Sync {
    /// The document as the viewer's title, menus and properties show it, under id `id`.
    fn info(&self, id: u64) -> DocumentInfo;
    /// Page `page` at `scale` pixels per point: all of it, or `tile` of it (pixels at that scale).
    fn render_page(&self, page: u32, scale: f32, tile: Option<TileRect>) -> Result<RenderedImage, MobileError>;
    /// Page `page`'s text layer.
    fn page_text(&self, page: u32) -> Result<Arc<PageText>, MobileError>;
    /// The web and mail links on page `page`.
    fn links(&self, page: u32) -> Result<Vec<LinkInfo>, MobileError>;
    /// Drop what can be built again (the system is short of memory).
    fn release_memory(&self);

    /// The document written as `format` (`pdf`, or one its engine writes: see [`formats`]).
    fn export(&self, format: &str) -> Result<Vec<u8>, MobileError>;

    /// A workbook, whose sheets the app draws as a grid of its own.
    fn as_sheets(&self) -> Option<&crate::sheets::SheetBook> {
        None
    }

    /// Page 1 drawn to fit `max_side` pixels, for file lists.
    fn thumbnail(&self, max_side: u32) -> Result<RenderedImage, MobileError> {
        let page = self.info(0).pages.into_iter().next().ok_or_else(|| MobileError::Open("the document has no pages".into()))?;
        let scale = (max_side.clamp(16, 2048) as f32 / page.width.max(page.height).max(1.0)).max(0.01);
        self.render_page(0, scale, None)
    }
}

/// What kind of office document a file is, and the extension its engine reads it by.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum OfficeFile {
    Word(&'static str),
    Slides,
    Sheets(&'static str),
}

const WORD_EXTENSIONS: &[&str] = &["docx", "docm", "dotx", "dotm", "doc", "dot", "odt", "ott", "rtf"];
const SLIDE_EXTENSIONS: &[&str] = &["pptx", "pptm", "ppsx", "ppsm", "potx", "potm"];
const SHEET_EXTENSIONS: &[&str] = &["xlsx", "xlsm", "xltx", "xltm", "xlsb", "ods", "csv", "tsv"];

/// What the bytes of a file are, as far as office documents go, or `None` when it is not one (a
/// PDF, or anything else). An encrypted package is a compound file whatever the application: its
/// name decides.
pub(crate) fn sniff(bytes: &[u8], path: &str) -> Option<OfficeFile> {
    let head = bytes.get(..bytes.len().min(1024)).unwrap_or_default();
    if head.windows(5).any(|w| w == b"%PDF-") {
        return None;
    }
    let ext = std::path::Path::new(path).extension().and_then(|e| e.to_str()).map(str::to_ascii_lowercase);
    let word_named = ext.as_deref().and_then(|e| WORD_EXTENSIONS.iter().find(|w| **w == e).copied());
    let slides_named = ext.as_deref().is_some_and(|e| SLIDE_EXTENSIONS.contains(&e));
    let sheets_named = ext.as_deref().and_then(|e| SHEET_EXTENSIONS.iter().find(|w| **w == e).copied());
    let contains = |needle: &[u8]| bytes.windows(needle.len()).any(|w| w == needle);
    if bytes.starts_with(b"PK\x03\x04") {
        // a zip package: its part names are plain text in the local headers and the directory
        if contains(b"word/document.xml") {
            return Some(OfficeFile::Word(match word_named {
                Some(e @ ("docm" | "dotx" | "dotm")) => e,
                _ => "docx",
            }));
        }
        if contains(b"ppt/presentation.xml") {
            return Some(OfficeFile::Slides);
        }
        if contains(b"xl/workbook.xml") {
            return Some(OfficeFile::Sheets(match sheets_named {
                Some(e @ ("xlsm" | "xltx" | "xltm")) => e,
                _ => "xlsx",
            }));
        }
        if contains(b"xl/workbook.bin") {
            return Some(OfficeFile::Sheets("xlsb"));
        }
        if contains(b"application/vnd.oasis.opendocument.spreadsheet") {
            return Some(OfficeFile::Sheets("ods"));
        }
        if contains(b"application/vnd.oasis.opendocument.text") {
            return Some(OfficeFile::Word(if word_named == Some("ott") { "ott" } else { "odt" }));
        }
        return None;
    }
    if bytes.starts_with(&[0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1]) {
        // a compound file: a Word 97-2003 document, or a password-protected package
        let utf16 = |s: &str| s.encode_utf16().flat_map(u16::to_le_bytes).collect::<Vec<u8>>();
        if contains(&utf16("WordDocument")) {
            return Some(OfficeFile::Word(if word_named == Some("dot") { "dot" } else { "doc" }));
        }
        if contains(&utf16("EncryptedPackage")) {
            if slides_named {
                return Some(OfficeFile::Slides);
            }
            if let Some(e @ ("xlsx" | "xlsm" | "xltx" | "xltm" | "xlsb")) = sheets_named {
                return Some(OfficeFile::Sheets(e));
            }
            return word_named.filter(|e| matches!(*e, "docx" | "docm" | "dotx" | "dotm")).map(OfficeFile::Word);
        }
        return None;
    }
    if head.starts_with(b"{\\rtf") {
        return Some(OfficeFile::Word("rtf"));
    }
    // plain text is known by its name alone: tables, and prose the Word engine reads
    if head.contains(&0) {
        return None;
    }
    if let Some(e @ ("csv" | "tsv")) = sheets_named {
        return Some(OfficeFile::Sheets(e));
    }
    match ext.as_deref() {
        Some("txt") => Some(OfficeFile::Word("txt")),
        Some("md" | "markdown") => Some(OfficeFile::Word("md")),
        _ => None,
    }
}

/// Read the file at `path` within the size bound.
pub(crate) fn read(path: &str) -> Result<Vec<u8>, MobileError> {
    let size = std::fs::metadata(path).map_err(|e| MobileError::Open(format!("{path}: {e}")))?.len();
    if size > MAX_FILE {
        return Err(MobileError::Open(format!("{path}: the file is larger than 2 GB")));
    }
    std::fs::read(path).map_err(|e| MobileError::Open(format!("{path}: {e}")))
}

/// The office document at `path`, if it is one, with its bytes. Only the first bytes are read of a
/// file that can't be one (a PDF).
pub(crate) fn file_at(path: &str) -> Result<Option<(OfficeFile, Vec<u8>)>, MobileError> {
    use std::io::Read;
    let mut head = [0u8; 8];
    let n = std::fs::File::open(path).and_then(|mut f| f.read(&mut head)).map_err(|e| MobileError::Open(format!("{path}: {e}")))?;
    let head = head.get(..n).unwrap_or_default();
    let ext = std::path::Path::new(path).extension().and_then(|e| e.to_str()).unwrap_or_default();
    let plain = ["csv", "tsv", "txt", "md", "markdown"].iter().any(|e| ext.eq_ignore_ascii_case(e));
    if !(plain || head.starts_with(b"PK\x03\x04") || head.starts_with(&[0xD0, 0xCF, 0x11, 0xE0]) || head.starts_with(b"{\\rtf")) {
        return Ok(None);
    }
    let bytes = read(path)?;
    Ok(sniff(&bytes, path).map(|kind| (kind, bytes)))
}

/// Open the office document `kind` from `bytes` (read from `path`).
pub(crate) fn open(
    path: &str,
    kind: OfficeFile,
    bytes: &[u8],
    password: Option<&str>,
    locale: Option<&str>,
) -> Result<Arc<dyn OfficeDoc>, MobileError> {
    Ok(match kind {
        OfficeFile::Word(ext) => Arc::new(crate::word::WordDocument::open(path, ext, bytes, password, locale)?),
        OfficeFile::Slides => Arc::new(crate::slides::SlideDeck::open(path, bytes)?),
        OfficeFile::Sheets(ext) => Arc::new(crate::sheets::SheetBook::open(path, ext, bytes, password)?),
    })
}

/// The formats an office document of `kind` can be written as, PDF first.
pub(crate) fn formats(kind: crate::DocumentKind) -> &'static [&'static str] {
    match kind {
        crate::DocumentKind::Word => &["pdf", "docx", "odt", "rtf", "html", "md", "txt"],
        crate::DocumentKind::Slides => &["pdf", "pptx"],
        crate::DocumentKind::Sheets => &["pdf", "xlsx", "csv"],
        crate::DocumentKind::Pdf => &[],
    }
}

/// A refusal of `format` for a document of `kind`.
pub(crate) fn unknown_format(kind: crate::DocumentKind, format: &str) -> MobileError {
    MobileError::Command(format!("a {kind:?} document can't be written as {format}: it can be {}", formats(kind).join(", ")))
}

/// The file name of `path`.
pub(crate) fn file_name(path: &str) -> String {
    std::path::Path::new(path).file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_else(|| path.to_string())
}

/// How far above its baseline a glyph's box reaches, and how far below, in ems: the usual ascent
/// and descent of text faces (the box is for highlighting and hit testing, not drawing).
pub(crate) const ASCENT: f32 = 0.8;
pub(crate) const DESCENT: f32 = 0.22;

/// A text glyph showing `text` whose em box runs from `x0` to `x1` along a baseline at `baseline`
/// at `size`, in its own space, which `to_page` maps to page points (a shape's rotation, say).
pub(crate) fn text_glyph(text: String, x0: f64, x1: f64, baseline: f64, size: f64, to_page: kurbo::Affine) -> TextGlyph {
    let (top, bottom) = (baseline - size * f64::from(ASCENT), baseline + size * f64::from(DESCENT));
    let p = |x: f64, y: f64| {
        let q = to_page * kurbo::Point::new(x, y);
        [q.x as f32, q.y as f32]
    };
    // the edge towards the next line (the bottom) first, in the writing direction, then the top back
    let quad = [p(x0, bottom), p(x1, bottom), p(x1, top), p(x0, top)];
    let xs = quad.map(|c| c[0]);
    let ys = quad.map(|c| c[1]);
    let min = |v: [f32; 4]| v.into_iter().fold(f32::INFINITY, f32::min);
    let max = |v: [f32; 4]| v.into_iter().fold(f32::NEG_INFINITY, f32::max);
    let [a, b] = [quad[0], quad[1]];
    let (dx, dy) = (b[0] - a[0], b[1] - a[1]);
    let len = (dx * dx + dy * dy).sqrt();
    let direction = if len > 0.0 { [dx / len, dy / len] } else { [1.0, 0.0] };
    TextGlyph { text, rect: [min(xs), min(ys), max(xs), max(ys)], quad, direction }
}

/// Whether a link goes to the web or to a mail address (the only links the viewer opens).
pub(crate) fn is_web_link(uri: &str) -> bool {
    let lower = uri.trim_start().to_ascii_lowercase();
    lower.starts_with("http://") || lower.starts_with("https://") || lower.starts_with("mailto:")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn office_documents_are_told_from_pdfs_by_their_bytes() {
        assert_eq!(sniff(b"%PDF-1.7\n", "/a/Report.docx"), None);
        assert_eq!(sniff(b"PK\x03\x04....word/document.xml....", "/a/Report.pdf"), Some(OfficeFile::Word("docx")));
        assert_eq!(sniff(b"PK\x03\x04....word/document.xml....", "/a/Letter.dotx"), Some(OfficeFile::Word("dotx")));
        assert_eq!(sniff(b"PK\x03\x04mimetypeapplication/vnd.oasis.opendocument.text", "/a/x"), Some(OfficeFile::Word("odt")));
        assert_eq!(sniff(b"PK\x03\x04....ppt/presentation.xml....", "/a/Deck.pdf"), Some(OfficeFile::Slides));
        assert_eq!(sniff(b"PK\x03\x04xl/workbook.xml", "/a/Book.pdf"), Some(OfficeFile::Sheets("xlsx")));
        assert_eq!(sniff(b"a,b\n1,2\n", "/a/t.csv"), Some(OfficeFile::Sheets("csv")));
        assert_eq!(sniff(b"{\\rtf1\\ansi hello}", "/a/x.txt"), Some(OfficeFile::Word("rtf")));
        assert_eq!(sniff(b"plain words", "/a/notes.docx"), None);
        assert_eq!(sniff(b"plain words", "/a/notes.txt"), Some(OfficeFile::Word("txt")));
        assert_eq!(sniff(b"# Title", "/a/Read me.md"), Some(OfficeFile::Word("md")));
    }

    #[test]
    fn an_encrypted_package_is_what_its_name_says() {
        let mut cfb = vec![0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];
        cfb.extend("EncryptedPackage".encode_utf16().flat_map(u16::to_le_bytes));
        assert_eq!(sniff(&cfb, "/a/Locked.docx"), Some(OfficeFile::Word("docx")));
        assert_eq!(sniff(&cfb, "/a/Locked.pptx"), Some(OfficeFile::Slides));
        assert_eq!(sniff(&cfb, "/a/Locked.xlsx"), Some(OfficeFile::Sheets("xlsx")));
        assert_eq!(sniff(&cfb, "/a/Locked.bin"), None);
    }

    #[test]
    fn a_glyph_box_follows_its_transform() {
        let g = text_glyph("a".into(), 0.0, 10.0, 20.0, 10.0, kurbo::Affine::IDENTITY);
        assert_eq!(g.rect, [0.0, 12.0, 10.0, 22.2]);
        assert_eq!(g.direction, [1.0, 0.0]);
        // turned a quarter clockwise, the text runs down the page
        let g = text_glyph("a".into(), 0.0, 10.0, 0.0, 10.0, kurbo::Affine::rotate(std::f64::consts::FRAC_PI_2));
        assert!((g.direction[1] - 1.0).abs() < 1e-5 && g.direction[0].abs() < 1e-5, "{:?}", g.direction);
    }
}

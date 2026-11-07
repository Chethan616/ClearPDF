//! Word documents (.docx and the other word-processor formats) through storytold/wordcraft's
//! engine: opened, laid out into pages, drawn and read the way the viewer reads a PDF (see
//! [`crate::office`]).

use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use kurbo::Affine;
use pdfcraft_render::{PageText, TextGlyph};

use wordcraft_engine::Session;
use wordcraft_engine::doc::Document;
use wordcraft_engine::layout::display::{DisplayOptions, Draw, page_display};
use wordcraft_engine::layout::{DocLayout, Page};
use wordcraft_engine::render::RenderOptions;

use crate::office::{ASCENT, DESCENT, OfficeDoc, is_web_link, text_glyph};
use crate::{DocumentInfo, DocumentKind, LinkInfo, MobileError, PageInfo, RenderedImage, TileRect};

/// One open Word document: the engine's session (document, layout, history) and its text layers.
pub(crate) struct WordDocument {
    state: Mutex<WordState>,
    /// One slot per page of the current layout, built when first asked for.
    texts: Mutex<(u64, Vec<Option<Arc<PageText>>>)>,
}

struct WordState {
    session: Session,
    name: String,
    path: String,
    file_size: u64,
    encrypted: bool,
    /// Bumped by every change to the document: the app's page caches key on it.
    generation: u64,
}

/// A failed open, with the password cases told apart so the app can ask for one.
fn open_error(e: String) -> MobileError {
    if wordcraft_engine::io::needs_password(&e) {
        MobileError::NeedsPassword
    } else if wordcraft_engine::io::wrong_password(&e) {
        MobileError::WrongPassword
    } else {
        MobileError::Open(e)
    }
}

/// How the viewer draws a page: as printed, headers and footers at full strength.
fn render_options() -> RenderOptions {
    RenderOptions { display: display_options(), ..RenderOptions::default() }
}

fn display_options() -> DisplayOptions {
    DisplayOptions { dim_header: false, dim_body: false, ..DisplayOptions::default() }
}

impl WordDocument {
    /// Open the Word document at absolute path `path` (`ext` is what [`crate::office::sniff`] found),
    /// with `password` if it needs one. `locale` (a BCP 47 tag) picks the paper of formats that
    /// give none.
    pub(crate) fn open(path: &str, ext: &str, bytes: &[u8], password: Option<&str>, locale: Option<&str>) -> Result<WordDocument, MobileError> {
        let name = crate::office::file_name(path);
        let region = wordcraft_engine::Region::from_locale_vars(|_| None, || locale.map(str::to_string));
        // wordcraft picks its reader by the name's extension
        let stem = std::path::Path::new(&name).file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
        let (doc, encrypted) = wordcraft_engine::io::open_bytes_in(&format!("{stem}.{ext}"), bytes, password, &region).map_err(open_error)?;
        let mut session = Session::new(doc);
        session.system_region = region;
        // a reader, not an editor: no spelling and grammar squiggles under the words
        session.view.proofing = false;
        session.path = Some(path.into());
        if encrypted {
            session.password = password.map(wordcraft_engine::io::Password::new);
        }
        let state = WordState { session, name, path: path.to_string(), file_size: bytes.len() as u64, encrypted, generation: 0 };
        Ok(WordDocument { state: Mutex::new(state), texts: Mutex::new((0, Vec::new())) })
    }

    fn lock(&self) -> MutexGuard<'_, WordState> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// The document and its current layout, to draw or read without holding the lock (the
    /// document's blocks are shared, so the copy is cheap).
    fn snapshot(&self) -> (Document, Arc<DocLayout>, u64) {
        let mut s = self.lock();
        let layout = s.session.layout();
        (s.session.doc.clone(), layout, s.generation)
    }
}

impl OfficeDoc for WordDocument {
    fn info(&self, id: u64) -> DocumentInfo {
        let (doc, layout, generation) = self.snapshot();
        let s = self.lock();
        let some = |v: &str| (!v.trim().is_empty()).then(|| v.to_string());
        DocumentInfo {
            id,
            kind: DocumentKind::Word,
            name: s.name.clone(),
            path: Some(s.path.clone()),
            pages: layout.pages.iter().map(|p| PageInfo { width: p.w, height: p.h, label: p.number.to_string(), rotation: 0 }).collect(),
            title: some(&doc.core.title),
            author: some(&doc.core.creator),
            subject: some(&doc.core.subject),
            keywords: some(&doc.core.keywords),
            creator: None,
            producer: None,
            pdf_version: String::new(),
            file_size: s.file_size,
            encrypted: s.encrypted,
            tagged: false,
            read_only_reason: Some("Word documents open for reading in this version".into()),
            allows_printing: true,
            allows_copying: true,
            allows_annotation: false,
            allows_form_filling: false,
            allows_assembly: false,
            dirty: s.session.dirty,
            undo: None,
            redo: None,
            fields: 0,
            has_outline: false,
            attachments: 0,
            signed: false,
            comments_hidden: false,
            generation,
        }
    }

    fn render_page(&self, page: u32, scale: f32, tile: Option<TileRect>) -> Result<RenderedImage, MobileError> {
        let (doc, layout, _) = self.snapshot();
        let p = page_of(&layout, page)?;
        let opts = render_options();
        let out = match tile {
            None => wordcraft_engine::render::render_page(&doc, p, scale, &opts),
            Some(t) => {
                let view = Affine::translate((-f64::from(t.x), -f64::from(t.y))) * Affine::scale(f64::from(scale));
                wordcraft_engine::render::render_region(&doc, p, t.width.max(1), t.height.max(1), view, &opts)
            }
        };
        Ok(RenderedImage { width: out.width, height: out.height, rgba: out.pixels })
    }

    /// Built once per document version.
    fn page_text(&self, page: u32) -> Result<Arc<PageText>, MobileError> {
        let (doc, layout, generation) = self.snapshot();
        let p = page_of(&layout, page)?;
        let mut texts = self.texts.lock().unwrap_or_else(PoisonError::into_inner);
        if texts.0 != generation || texts.1.len() != layout.pages.len() {
            *texts = (generation, vec![None; layout.pages.len()]);
        }
        if let Some(Some(t)) = texts.1.get(page as usize) {
            return Ok(t.clone());
        }
        let mut glyphs = Vec::new();
        collect_glyphs(&page_display(&doc, p, &display_options()), &mut glyphs);
        let text = Arc::new(pdfcraft_render::text::layout(glyphs));
        if let Some(slot) = texts.1.get_mut(page as usize) {
            *slot = Some(text.clone());
        }
        Ok(text)
    }

    /// One rectangle per run of linked text.
    fn links(&self, page: u32) -> Result<Vec<LinkInfo>, MobileError> {
        let (doc, layout, _) = self.snapshot();
        let p = page_of(&layout, page)?;
        let mut out = Vec::new();
        collect_links(&page_display(&doc, p, &display_options()), &mut out);
        Ok(out)
    }

    /// The text layers go; they are built again when needed.
    fn release_memory(&self) {
        *self.texts.lock().unwrap_or_else(PoisonError::into_inner) = (0, Vec::new());
    }
}

fn page_of(layout: &DocLayout, page: u32) -> Result<&Page, MobileError> {
    let n = layout.pages.len();
    layout
        .pages
        .get(page as usize)
        .ok_or_else(|| MobileError::Render(format!("page {} is out of range: the document has {n} pages", u64::from(page) + 1)))
}

/// The glyphs of `draws` (and of the groups in them) as text glyphs, in page points. Glyphs that
/// show only white space are left out: the text layer puts word spaces back from the gaps.
/// Known limit: text in turned or rotated boxes is left out of the text layer.
fn collect_glyphs(draws: &[Draw], out: &mut Vec<TextGlyph>) {
    for d in draws {
        match d {
            Draw::Glyphs { size, glyphs, text, ranges, .. } => run_glyphs(*size, glyphs, text, ranges, out),
            Draw::Figure { draws, .. } => collect_glyphs(draws, out),
            Draw::Clip { items, .. } => collect_glyphs(items, out),
            _ => {}
        }
    }
}

/// The text each glyph of a run shows: from its byte range, else one character per glyph when
/// the counts agree, else the whole text on the first glyph.
fn glyph_texts(glyphs: usize, text: &str, ranges: &[std::ops::Range<usize>]) -> Vec<String> {
    if ranges.len() == glyphs {
        let mut prev: Option<&std::ops::Range<usize>> = None;
        return ranges
            .iter()
            .map(|r| {
                // the second glyph of a cluster (a mark on a letter) shows nothing more
                let same = prev.is_some_and(|p| p == r);
                prev = Some(r);
                if same { String::new() } else { text.get(r.clone()).unwrap_or_default().to_string() }
            })
            .collect();
    }
    let chars: Vec<char> = text.chars().collect();
    if chars.len() == glyphs {
        return chars.into_iter().map(String::from).collect();
    }
    (0..glyphs).map(|i| if i == 0 { text.to_string() } else { String::new() }).collect()
}

fn run_glyphs(size: f32, glyphs: &[(u32, f32, f32)], text: &str, ranges: &[std::ops::Range<usize>], out: &mut Vec<TextGlyph>) {
    if glyphs.is_empty() || !(size.is_finite() && size > 0.0) {
        return;
    }
    let texts = glyph_texts(glyphs.len(), text, ranges);
    // the last glyph's advance isn't in the run: the run's average, else half an em
    let average = match (glyphs.first(), glyphs.last()) {
        (Some(a), Some(b)) if glyphs.len() > 1 && b.1 > a.1 => (b.1 - a.1) / (glyphs.len() - 1) as f32,
        _ => size * 0.5,
    };
    for (i, ((_, x, baseline), t)) in glyphs.iter().zip(texts).enumerate() {
        if t.trim().is_empty() {
            continue;
        }
        let next = glyphs.get(i + 1).map(|g| g.1).filter(|n| *n > *x);
        let x1 = next.unwrap_or(x + average.min(size));
        out.push(text_glyph(t, f64::from(*x), f64::from(x1), f64::from(*baseline), f64::from(size), Affine::IDENTITY));
    }
}

/// The web and mail links in `draws`: each linked glyph run's box.
fn collect_links(draws: &[Draw], out: &mut Vec<LinkInfo>) {
    for d in draws {
        match d {
            Draw::Glyphs { size, glyphs, link: Some(uri), .. } if is_web_link(uri) => {
                let (Some(first), Some(last)) = (glyphs.first(), glyphs.last()) else { continue };
                let x1 = last.1 + size * 0.5;
                let top = glyphs.iter().map(|g| g.2).fold(f32::INFINITY, f32::min) - size * ASCENT;
                let bottom = glyphs.iter().map(|g| g.2).fold(f32::NEG_INFINITY, f32::max) + size * DESCENT;
                // a link set in several runs: one rectangle per run, all going to the same place
                out.push(LinkInfo { rect: vec![first.1, top, x1, bottom], uri: Some(uri.clone()), page: None, top: None });
            }
            Draw::Figure { draws, .. } => collect_links(draws, out),
            Draw::Clip { items, .. } => collect_links(items, out),
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn glyph_texts_follow_the_byte_ranges() {
        assert_eq!(glyph_texts(3, "abc", &[0..1, 1..2, 2..3]), ["a", "b", "c"]);
        // a ligature: two glyphs for one cluster keep its text once
        assert_eq!(glyph_texts(2, "fi", &[0..2, 0..2]), ["fi", ""]);
        assert_eq!(glyph_texts(2, "ab", &[]), ["a", "b"]);
        assert_eq!(glyph_texts(2, "abc", &[]), ["abc", ""]);
    }

    #[test]
    fn spaces_are_left_to_the_gaps() {
        let mut out = Vec::new();
        run_glyphs(10.0, &[(1, 0.0, 20.0), (2, 5.0, 20.0), (3, 10.0, 20.0)], "a b", &[0..1, 1..2, 2..3], &mut out);
        let texts: Vec<&str> = out.iter().map(|g| g.text.as_str()).collect();
        assert_eq!(texts, ["a", "b"]);
        let a = out.first().map(|g| g.rect).unwrap_or_default();
        let want = [0.0, 12.0, 5.0, 22.2];
        assert!(a.iter().zip(want).all(|(g, w)| (g - w).abs() < 1e-4), "{a:?}");
    }
}

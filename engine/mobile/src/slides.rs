//! PowerPoint presentations (.pptx) through storytold/deckcraft's reader and slide renderer: each
//! slide is a page of the viewer (see [`crate::office`]). Viewing needs no deckcraft engine.

use std::sync::{Arc, Mutex, PoisonError};

use deckcraft_model::Presentation;
use deckcraft_model::text::Action;
use deckcraft_render::RenderOpts;
use pdfcraft_render::PageText;

use crate::office::{OfficeDoc, is_web_link, text_glyph};
use crate::{DocumentInfo, DocumentKind, LinkInfo, MobileError, PageInfo, RenderedImage, TileRect};

/// One open presentation and its slides' text layers.
pub(crate) struct SlideDeck {
    pres: Arc<Presentation>,
    name: String,
    path: String,
    file_size: u64,
    /// Opened with a password.
    encrypted: bool,
    texts: Mutex<Vec<Option<Arc<PageText>>>>,
}

/// Worker threads a slide renders on (effects that blur need some).
const THREADS: u16 = 2;

/// Gives the slides' font database the faces Artefact bundles (the Word engine's: Source Sans 3,
/// Source Serif 4, Inter, JetBrains Mono), once. A phone has no Calibri, Arial or Times New Roman,
/// nor the desktop stand-ins DeckCraft looks for, so without these every run of text fell through
/// to its last-resort face and a deck's fonts all looked replaced; with them each family gets a
/// stand-in of its own kind (see `substitutes` in vendor/deckcraft-fonts).
pub(crate) fn ensure_fonts() {
    static ONCE: std::sync::Once = std::sync::Once::new();
    ONCE.call_once(|| {
        let db = deckcraft_fonts::FontDb::global();
        for bytes in wordcraft_fonts::bundled() {
            db.add_font(bytes.to_vec());
        }
    });
}

impl SlideDeck {
    /// A password-protected presentation is an ordinary package inside an encrypted compound file
    /// ([MS-OFFCRYPTO], the same wrapping Word and Excel use): it is decrypted with `password`
    /// (Agile and Standard encryption), then read like any other.
    pub(crate) fn open(path: &str, bytes: &[u8], password: Option<&str>) -> Result<SlideDeck, MobileError> {
        if deckcraft_pptx::is_encrypted(bytes) {
            use gridcraft_xlsx::DecryptError;
            // a scheme no password opens (RC4, rights management) is said at once, before asking
            if let Err(e @ DecryptError::Unsupported(_)) = gridcraft_xlsx::check_encryption(bytes) {
                return Err(MobileError::Open(format!("this presentation can't be opened: {e}")));
            }
            let Some(password) = password else { return Err(MobileError::NeedsPassword) };
            let package = gridcraft_xlsx::decrypt(bytes, password).map_err(|e| match e {
                DecryptError::WrongPassword => MobileError::WrongPassword,
                other => MobileError::Open(format!("this presentation can't be opened: {other}")),
            })?;
            let pres = deckcraft_pptx::import(&package).map_err(|e| MobileError::Open(e.to_string()))?;
            let mut deck = Self::of(path, pres, bytes.len());
            deck.encrypted = true;
            return Ok(deck);
        }
        let pres = deckcraft_pptx::import(bytes).map_err(|e| MobileError::Open(e.to_string()))?;
        Ok(Self::of(path, pres, bytes.len()))
    }

    /// A PowerPoint 97-2003 presentation (.ppt): its slides' text in a deck of its size.
    pub(crate) fn open_ppt(path: &str, bytes: &[u8]) -> Result<SlideDeck, MobileError> {
        let pres = crate::ppt::import(bytes).map_err(MobileError::Open)?;
        Ok(Self::of(path, pres, bytes.len()))
    }

    fn of(path: &str, pres: Presentation, size: usize) -> SlideDeck {
        ensure_fonts();
        let n = pres.slides.len();
        SlideDeck {
            pres: Arc::new(pres),
            name: crate::office::file_name(path),
            path: path.to_string(),
            file_size: size as u64,
            encrypted: false,
            texts: Mutex::new(vec![None; n]),
        }
    }

    fn slide(&self, page: u32) -> Result<usize, MobileError> {
        let n = self.pres.slides.len();
        let i = page as usize;
        if i < n {
            Ok(i)
        } else {
            Err(MobileError::Render(format!("slide {} is out of range: the presentation has {n} slides", u64::from(page) + 1)))
        }
    }
}

impl OfficeDoc for SlideDeck {
    fn info(&self, id: u64) -> DocumentInfo {
        let p = &self.pres;
        let (w, h) = (p.slide_size.width as f32, p.slide_size.height as f32);
        let some = |v: &str| (!v.trim().is_empty()).then(|| v.to_string());
        DocumentInfo {
            id,
            kind: DocumentKind::Slides,
            name: self.name.clone(),
            path: Some(self.path.clone()),
            pages: (0..p.slides.len()).map(|i| PageInfo { width: w, height: h, label: (i + 1).to_string(), rotation: 0 }).collect(),
            title: some(&p.props.title),
            author: some(&p.props.author),
            subject: some(&p.props.subject),
            keywords: some(&p.props.keywords),
            creator: None,
            producer: None,
            pdf_version: String::new(),
            file_size: self.file_size,
            encrypted: self.encrypted,
            tagged: false,
            read_only_reason: Some("Presentations open for viewing in this version".into()),
            allows_printing: true,
            allows_copying: true,
            allows_annotation: false,
            allows_form_filling: false,
            allows_assembly: false,
            dirty: false,
            undo: None,
            redo: None,
            fields: 0,
            has_outline: false,
            attachments: 0,
            signed: false,
            comments_hidden: false,
            generation: 0,
        }
    }

    fn render_page(&self, page: u32, scale: f32, tile: Option<TileRect>) -> Result<RenderedImage, MobileError> {
        let i = self.slide(page)?;
        let mut opts = RenderOpts { scale: f64::from(scale), threads: THREADS, ..RenderOpts::default() };
        if let Some(t) = tile {
            opts.offset = (-f64::from(t.x), -f64::from(t.y));
            opts.size = Some((t.width.max(1), t.height.max(1)));
        }
        let img = deckcraft_render::render_slide(&self.pres, i, &opts);
        if img.width == 0 || img.height == 0 {
            return Err(MobileError::Render(format!("slide {} could not be drawn", i + 1)));
        }
        Ok(RenderedImage { width: img.width, height: img.height, rgba: img.pixels })
    }

    fn page_text(&self, page: u32) -> Result<Arc<PageText>, MobileError> {
        let i = self.slide(page)?;
        let mut texts = self.texts.lock().unwrap_or_else(PoisonError::into_inner);
        if let Some(Some(t)) = texts.get(i) {
            return Ok(t.clone());
        }
        let mut glyphs = Vec::new();
        for pt in deckcraft_render::place_slide(&self.pres, i).texts {
            for run in &pt.layout.runs {
                if run.size <= 0.0 {
                    continue;
                }
                for (gi, (gid, x, y)) in run.glyphs.iter().enumerate() {
                    // bullets draw no paragraph character
                    let Some((_, ch)) = run.cells.get(gi) else { continue };
                    if ch.is_whitespace() {
                        continue;
                    }
                    let next = run.glyphs.get(gi + 1).map(|g| g.1).filter(|n| n > x);
                    let x1 = next.unwrap_or(x + run.face.advance(*gid) * run.size / run.face.upem.max(1.0));
                    glyphs.push(text_glyph(ch.to_string(), *x, x1, *y, run.size, pt.transform));
                }
            }
        }
        let text = Arc::new(pdfcraft_render::text::layout(glyphs));
        if let Some(slot) = texts.get_mut(i) {
            *slot = Some(text.clone());
        }
        Ok(text)
    }

    /// The web and mail links of slide `page`, and its links to other slides (a slide of the deck,
    /// the next, the previous, the first or the last), which the viewer follows as page jumps.
    fn links(&self, page: u32) -> Result<Vec<LinkInfo>, MobileError> {
        let i = self.slide(page)?;
        let last = self.pres.slides.len().saturating_sub(1);
        Ok(deckcraft_render::place_slide(&self.pres, i)
            .links
            .into_iter()
            .filter_map(|l| {
                let r = l.rect;
                let rect = vec![r.x0 as f32, r.y0 as f32, r.x1 as f32, r.y1 as f32];
                let jump = |to: usize| Some(LinkInfo { rect: rect.clone(), uri: None, page: Some(to as u32), top: None });
                match &l.link.action {
                    Action::Url { url } if is_web_link(url) => Some(LinkInfo { rect, uri: Some(url.clone()), page: None, top: None }),
                    Action::Slide { slide } => self.pres.slide_index(*slide).and_then(jump),
                    Action::NextSlide if i < last => jump(i + 1),
                    Action::PreviousSlide if i > 0 => jump(i - 1),
                    Action::FirstSlide => jump(0),
                    Action::LastSlide => jump(last),
                    _ => None,
                }
            })
            .collect())
    }

    fn export(&self, format: &str) -> Result<Vec<u8>, MobileError> {
        match format {
            "pdf" => deckcraft_pdf::export(&self.pres, &deckcraft_pdf::PdfOptions::default()).map_err(|e| MobileError::Command(e.to_string())),
            "pptx" => deckcraft_pptx::export(&self.pres).map_err(|e| MobileError::Command(e.to_string())),
            _ => Err(crate::office::unknown_format(DocumentKind::Slides, format)),
        }
    }

    fn release_memory(&self) {
        let mut texts = self.texts.lock().unwrap_or_else(PoisonError::into_inner);
        texts.iter_mut().for_each(|t| *t = None);
    }
}

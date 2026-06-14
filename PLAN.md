# ClearPDF — Roadmap

A living plan for turning ClearPDF into a full, Adobe-class **PDF editor** that stays
**100% offline & privacy-first**. No cloud, no accounts, no LLM/network AI — on-device
Google ML Kit is used **only** for the document scanner and on-device OCR text selection.

Legend: ✅ done · 🚧 in progress · ⏳ planned · 💡 idea / stretch

---

## Phase 0 — Foundation & correctness ✅ (June 2026)

- ✅ Thread-safe `PdfRenderer` engine (serialized page access — fixed crash/corruption race)
- ✅ Page rotation + page-size APIs in the engine
- ✅ Lossless **merge / split / extract** via PdfBox-Android (no more rasterized, blurry output)
- ✅ Lossless **page editor** core (reorder / rotate / delete)
- ✅ **Conversions** core: images → PDF (lossless embed), PDF → text extraction
- ✅ Viewer **zoom/pan rewrite** — focal-point pinch, correct pan clamping, gesture layer
      decoupled from the transform; annotations & OCR now align at any zoom
- ✅ New Tools: **Organize Pages**, **Images → PDF**, **Extract Text**

---

## Phase 1 — Make the editor feel complete ⏳

- ⏳ **Drag-to-reorder** pages in the Organizer (replace up/down buttons; long-press + drag)
- ⏳ **Insert pages**: append/merge another PDF or images *into* an existing document
- ⏳ **Duplicate page** + **add blank page** in the Organizer
- ⏳ **Undo/redo stack** shared across annotation + page edits
- ⏳ **Save-in-place vs Save-as-copy** choice (overwrite original when it's a writable file)
- ⏳ **Page thumbnails strip** inside the viewer for fast navigation + jump-to-page
- ⏳ **Continuous (vertical) scroll** reading mode in addition to paged mode
- ⏳ Viewer **reflow guard**: remember last page & zoom per document

## Phase 2 — Annotation depth (Adobe-style) ⏳

- ⏳ **Text box / typewriter** tool (add real typed text, font size & color)
- ⏳ **Signature** tool: draw once, save signatures, stamp onto pages
- ⏳ **Sticky notes / comments** with a comments panel
- ⏳ **Stamps** (Approved, Draft, Confidential, date stamp)
- ⏳ **Eraser** + per-object select/move/delete (currently annotations are append-only)
- ⏳ **Ink smoothing** for the pen (Catmull-Rom) and pressure-width if available
- ⏳ Persist annotations as **real PDF annotations** (via PdfBox) instead of flattening only,
      so they stay editable after save (export-flattened remains an option)

## Phase 3 — Conversions & document ops ⏳

- ⏳ **PDF → images** (export pages as PNG/JPG to gallery, quality + DPI options)
- ⏳ **PDF → long image** (stitch pages into one tall PNG for sharing)
- ⏳ **Text → PDF** screen (paste/type text, choose page size & margins — engine already supports)
- ⏳ **Page numbers / Bates numbering** stamp
- ⏳ **Watermark** (text or image, opacity, tiling, diagonal)
- ⏳ **Header/footer** templates
- ⏳ **Crop pages** (per-page or apply-to-all crop box)
- ⏳ **Rotate-all** quick action

## Phase 4 — Security & sharing ⏳

- ⏳ **Password protect / encrypt** + **remove password** (PdfBox `StandardProtectionPolicy`)
- ⏳ **Flatten** (bake annotations/forms) for safe sharing
- ⏳ **Redaction** tool (truly remove content under a box, not just cover it)
- ⏳ **Share sheet** integration everywhere + "Open with ClearPDF" as default PDF handler
- ⏳ **Print** support (Android print framework)
- ⏳ **Metadata editor** (title/author/keywords) and metadata stripping for privacy

## Phase 5 — Scanning & OCR polish ⏳

- ⏳ Audit/repair the scanner flow on-device (filters, multi-page, save reliability)
- ⏳ Route scanner output through the lossless `PdfConverter` (consistent file sizes)
- ⏳ **Searchable scans**: run ML Kit OCR over scans and embed an invisible text layer
      (makes scanned PDFs selectable/searchable — fully on-device)
- ⏳ In-viewer **find/search** (uses the OCR/text layer)
- ⏳ Auto-filter suggestion per page (B&W for text, color for photos)

## Phase 6 — Forms (stretch) 💡

- 💡 **Fill AcroForm fields** (text, checkbox, radio, dropdown) and save
- 💡 Detect flat forms and let users add fillable fields

## Phase 7 — Library, UX & quality ⏳

- ⏳ **File library / recents** with folders, rename, delete, favorites, sort & search
- ⏳ **Bookmarks / outline** viewer (read the PDF's table of contents; add custom bookmarks)
- ⏳ **Tablet / landscape** two-pane layout
- ⏳ **Per-document dark mode** (invert page colors for night reading)
- ⏳ **Haptics & motion** polish consistent with the liquid-glass language
- ⏳ **Accessibility**: TalkBack labels, larger-text support, contrast pass
- ⏳ **Localization** scaffolding (currently `en` only)

## Phase 8 — Engineering health ⏳

- ⏳ Move from `PdfServiceLocator` to a small DI setup (Hilt or manual graph)
- ⏳ Centralize the duplicated `createOutputUri` / save logic into one repository
- ⏳ Unit tests for `pdf-core` (merge/split/editor/converter) on JVM where possible
- ⏳ Instrumented tests for the viewer gesture math
- ⏳ CI (GitHub Actions): build + lint on PR
- ⏳ Crash/ANR hardening: large-file rendering, OOM guards, RGB_565 thumbnails
- ⏳ CONTRIBUTING.md, issue/PR templates, screenshots refresh

---

### Guiding principles
1. **Offline & private** — no data leaves the device. Ever.
2. **Lossless by default** — preserve text/vectors; only rasterize when a feature requires it.
3. **One design language** — every new screen uses the liquid-glass components.
4. **Editor, not just viewer** — every common Adobe action should have an offline equivalent.

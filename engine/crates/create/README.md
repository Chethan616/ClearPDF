# pdfcraft-create

Layer L4. Create a PDF (Acrobat's Create a PDF tool), execution plan M10.2:

- `blank(width, height, pages)`: an empty document;
- `from_images(&[(name, bytes)])`: one page per image, sized from the image's resolution
  (PNG `pHYs`, JPEG JFIF density; 72 dpi when absent). JPEG data is embedded as is
  (`/DCTDecode`, grey, RGB or Adobe-inverted CMYK); PNG is decoded and stored with Flate,
  with transparency as a soft mask. An ICC profile in the file (JPEG `APP2 ICC_PROFILE`, PNG
  `iCCP`) that matches the image's colour space is kept as an `/ICCBased` colour space with
  the device space as `/Alternate`; images with the same profile share one profile object.
  JPEG metadata scanning skips marker fill bytes and reads application segments after the
  frame header, stopping at the first scan or end marker. Broken or mismatched ICC profiles
  still fall back to the device colour space;
- `from_images_with_resolution(images, ImageResolution::Dpi(dpi))`: override the page
  resolution (1–1200 dpi), without resampling or recompressing image pixels. Use 72 dpi
  for one point per pixel, or `ImageResolution::Embedded` for the same behavior as
  `from_images`. The largest page side is still capped at 14,400 points;
- `from_text(text, …)`: plain text set in Helvetica, wrapped and paginated. Text Helvetica
  can't draw is refused, naming the line and the character, never set as `?`.
- `from_text_escaped(text, …)`: the same for reports PdfCraft generates from documents
  (comment summaries, compare reports): such characters are written as `[U+XXXX]` and the first
  line says so (`ESCAPED_NOTE`).
- `from_office(name, bytes)`: Word (.docx), Excel (.xlsx), PowerPoint (.pptx) and RTF files,
  converted on the device (`src/office/`); `office_kind` tells them apart by content (a zip with
  a Word, Excel or PowerPoint main part, an RTF header) and `source_kind` returns
  `SourceKind::Office` for them. Legacy binary .doc/.xls/.ppt files (OLE2) are recognised and
  refused with advice to save them as .docx/.xlsx/.pptx.
  - Word: sections (page size, margins), styles with `basedOn` chains and document defaults
    (headings look like headings even without styles.xml), run and paragraph properties, tabs,
    line and page breaks, bullets and numbering, tables (grid widths, spans, vertical merges,
    shading, borders, table-style first row and banding; rows split across pages when taller
    than one), inline and VML pictures, text boxes (after their paragraph), default headers
    and footers with page numbers.
  - Excel: each visible sheet in order, used range only; shared, rich and inline strings,
    numbers with built-in and custom formats (dates, times, percentages, currency, scientific),
    booleans, cached formula results; column widths, row heights, merged cells, fonts, fills,
    borders, alignment, wrapping and text running over empty neighbours; light grid lines;
    wide sheets are scaled down (to 50%) and turned landscape, then paginated down and across.
  - PowerPoint: a page per visible slide at the slide size; placeholders inherit position,
    body properties and list styles from the layout and master; master and layout decorations
    and backgrounds; text with bullets, numbering, spacing and autofit scaling; preset and
    custom geometry with fills and outlines, shape-style colours; pictures (cropped); groups;
    tables (the default table style is approximated). Charts and SmartArt are left out.
  - RTF: font table (family), colour table, character and paragraph formatting, tabs, Unicode
    (`\u`), code pages, simple tables, PNG/JPEG pictures and page breaks.

  Text is set in the standard 14 fonts (Helvetica, Times, Courier by the document's family)
  in WinAnsiEncoding, measured with their real widths; characters outside Western European
  scripts are drawn as `?`. Everything read is untrusted: the zip reader caps entry count
  (10 000), inflated size per entry (64 MiB, measured while inflating) and in total
  (256 MiB), refuses ZIP64 and encryption; XML parts are size-, depth- and node-capped;
  recursion is bounded and output is capped at 5 000 pages.

Every function returns a `pdfcraft_cos::Document`; the caller writes it.

Image export and Save Image As apply each lossless image's Decode pair before converting its color components.
Indexed Decode values select palette entries after rounding and clipping. Color-key masks compare the original samples.
Missing, null or malformed Decode arrays keep the default mapping.
Soft-mask Decode intervals map samples to opacity before the mask is resized, including reversed ranges and indirect entries.
The existing 16-bit path still uses each sample's high byte.
JPEG image Decode handling remains limited.
ICC transforms and calibrated color conversion remain outside this export path.

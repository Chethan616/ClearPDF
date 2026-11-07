//! Excel workbooks (.xlsx, .xlsb, .ods, .csv) through storytold/gridcraft's engine. A workbook is
//! not pages: the app draws its sheets as a grid of its own from what [`SheetBook`] serves (the
//! cells of a window with their looks, column widths and row heights, merges, frozen panes). Its
//! sheets still count as the document's pages, for titles, Recent and thumbnails.

use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use gridcraft_engine::core::CellRef;
use gridcraft_engine::model::{BorderLine, BorderStyle, Color, HAlign, PatternType, Sheet, VAlign, Visibility, Workbook};
use pdfcraft_render::PageText;

use crate::office::OfficeDoc;
use crate::{DocumentInfo, DocumentKind, LinkInfo, MobileError, PageInfo, RenderedImage, TileRect};

/// Points per pixel at 96 dpi: gridcraft keeps column widths and row heights in such pixels.
const PT: f32 = 0.75;

/// The most cells one window asks for (a screen holds a few hundred).
const MAX_WINDOW: u64 = 20_000;

/// A sheet as the grid lays it out.
#[derive(Debug, Clone, uniffi::Record)]
pub struct SheetInfo {
    pub name: String,
    /// The used area: rows and columns from A1 to its far corner.
    pub rows: u32,
    pub cols: u32,
    /// Frozen rows at the top and columns at the left (0: none).
    pub frozen_rows: u32,
    pub frozen_cols: u32,
    pub gridlines: bool,
    pub hidden: bool,
    /// Its tab's colour, `0xRRGGBB`.
    pub tab_color: Option<u32>,
}

/// Column widths and row heights of a window, in points (0: hidden).
#[derive(Debug, Clone, uniffi::Record)]
pub struct SheetAxes {
    pub col_widths: Vec<f32>,
    pub row_heights: Vec<f32>,
}

/// A border of a cell: its weight (1 thin, 2 medium, 3 thick; 0 none) and colour.
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct CellEdge {
    pub weight: u8,
    pub color: u32,
}

/// A cell as the grid draws it: the text it shows, in its looks. Colours are `0xRRGGBB`.
#[derive(Debug, Clone, uniffi::Record)]
pub struct CellView {
    pub row: u32,
    pub col: u32,
    pub text: String,
    pub fill: Option<u32>,
    pub ink: u32,
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strike: bool,
    /// Font size in points.
    pub size: f32,
    /// `l`, `c` or `r` (General: numbers right, text left, as Excel shows them).
    pub align: String,
    /// `t`, `c` or `b`.
    pub valign: String,
    pub wrap: bool,
    pub left: CellEdge,
    pub top: CellEdge,
    pub right: CellEdge,
    pub bottom: CellEdge,
}

/// Merged cells, rows `row0..=row1`, columns `col0..=col1`.
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct CellSpan {
    pub row0: u32,
    pub col0: u32,
    pub row1: u32,
    pub col1: u32,
}

/// One open workbook, held by GridCraft's session (its formulas computed, its edits undoable).
pub(crate) struct SheetBook {
    session: Mutex<gridcraft_engine::Session>,
    name: String,
    path: String,
    file_size: u64,
    encrypted: bool,
}

fn open_error(e: String) -> MobileError {
    if e.contains(gridcraft_engine::io::PASSWORD_REQUIRED) {
        MobileError::NeedsPassword
    } else if e.contains(gridcraft_engine::io::WRONG_PASSWORD) {
        MobileError::WrongPassword
    } else {
        MobileError::Open(e)
    }
}

fn rgb(c: Option<[u8; 3]>) -> Option<u32> {
    c.map(|[r, g, b]| (u32::from(r) << 16) | (u32::from(g) << 8) | u32::from(b))
}

impl SheetBook {
    pub(crate) fn open(path: &str, ext: &str, bytes: &[u8], password: Option<&str>) -> Result<SheetBook, MobileError> {
        let name = crate::office::file_name(path);
        let stem = std::path::Path::new(&name).file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
        let encrypted = gridcraft_xlsx::sniff(bytes) == gridcraft_xlsx::Format::Encrypted;
        let (wb, _warnings) =
            gridcraft_engine::io::open_bytes_with_password(&format!("{stem}.{ext}"), bytes, password).map_err(|e| open_error(e.to_string()))?;
        // the desktop app's document: computed on open (values the file left stale are brought up to
        // date), with its selection and undo history
        let mut session = gridcraft_engine::Session::new();
        let doc = gridcraft_engine::DocState::new(wb, Some(path.to_string()), stem, gridcraft_engine::Trust::FromFile);
        let i = session.add_document(doc);
        session.set_active(i);
        Ok(SheetBook { session: Mutex::new(session), name, path: path.to_string(), file_size: bytes.len() as u64, encrypted })
    }

    fn lock(&self) -> MutexGuard<'_, gridcraft_engine::Session> {
        self.session.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// The workbook as it is now (shared: reading it never holds the session).
    fn wb(&self) -> Arc<Workbook> {
        self.lock().active().map(|d| d.wb.clone()).unwrap_or_default()
    }

    /// Run GridCraft command `command` with `params` on the cells of `area` on sheet `sheet` (they
    /// are selected first, as a tap selects them in the app): the Home tab's formatting, `cell.set`,
    /// inserting and deleting rows and columns, `edit.undo` and `edit.redo`, and the rest.
    pub(crate) fn command(&self, sheet: u32, area: CellSpan, command: &str, params: serde_json::Value) -> Result<serde_json::Value, MobileError> {
        let name = self.sheet(sheet)?.name.clone();
        let range = gridcraft_engine::core::RangeRef::new(CellRef::new(area.row0, area.col0), CellRef::new(area.row1, area.col1));
        let mut s = self.lock();
        let err = |e: gridcraft_engine::EngineError| MobileError::Command(e.to_string());
        s.execute("sheet.activate", serde_json::json!({ "sheet": name })).map_err(err)?;
        s.execute("selection.set", serde_json::json!({ "range": range.a1(), "active": range.start.a1() })).map_err(err)?;
        s.execute(command, params).map_err(err)
    }

    /// What the formula bar shows for a cell: its formula (`=SUM(A1:A3)`), else its value as typed.
    pub(crate) fn input(&self, sheet: u32, row: u32, col: u32) -> Result<String, MobileError> {
        let wb = self.wb();
        let s = self.sheet_in(&wb, sheet)?;
        let c = CellRef::new(row, col);
        if let Some(f) = s.cell(c).and_then(|x| x.formula.as_ref()) {
            return Ok(format!("={}", f.text));
        }
        Ok(gridcraft_engine::display::value_text(&wb, s, c))
    }

    /// Write the workbook to `path` in the format its name says (.xlsx, .xls, .ods, .csv, ...): a save.
    pub(crate) fn save_to(&self, path: &str) -> Result<(), MobileError> {
        let mut s = self.lock();
        let d = s.active_mut().ok_or_else(|| MobileError::Command("no workbook".into()))?;
        let bytes = gridcraft_engine::io::save_bytes(&d.wb, path).map_err(|e| MobileError::Command(e.to_string()))?;
        std::fs::write(path, bytes).map_err(|e| MobileError::Command(format!("{path}: {e}")))?;
        d.saved = d.wb.clone();
        Ok(())
    }

    fn sheet(&self, index: u32) -> Result<Arc<Sheet>, MobileError> {
        let wb = self.wb();
        self.sheet_in(&wb, index)?;
        wb.sheets.get(index as usize).cloned().ok_or(MobileError::NoDocument(0))
    }

    fn sheet_in<'a>(&self, wb: &'a Workbook, index: u32) -> Result<&'a Sheet, MobileError> {
        let n = wb.sheets.len();
        wb.sheets
            .get(index as usize)
            .map(|s| &**s)
            .ok_or_else(|| MobileError::Command(format!("sheet {} is out of range: the workbook has {n} sheets", u64::from(index) + 1)))
    }

    /// The sheets in tab order.
    pub(crate) fn sheets(&self) -> Vec<SheetInfo> {
        let wb = self.wb();
        wb.sheets
            .iter()
            .map(|s| {
                let (rows, cols) = s.used_range().map_or((0, 0), |r| (r.end.row.saturating_add(1), r.end.col.saturating_add(1)));
                SheetInfo {
                    name: s.name.clone(),
                    rows,
                    cols,
                    frozen_rows: s.freeze.map_or(0, |f| f.0),
                    frozen_cols: s.freeze.map_or(0, |f| f.1),
                    gridlines: s.show_gridlines,
                    hidden: s.visibility != Visibility::Visible,
                    tab_color: s.tab_color.and_then(|c| rgb(c.resolve(&wb.theme))),
                }
            })
            .collect()
    }

    /// Widths of columns `col0..col0+cols` and heights of rows `row0..row0+rows` of sheet `sheet`.
    pub(crate) fn axes(&self, sheet: u32, row0: u32, rows: u32, col0: u32, cols: u32) -> Result<SheetAxes, MobileError> {
        let wb = self.wb();
        let s = self.sheet_in(&wb, sheet)?;
        let cols = cols.min(16_384);
        let rows = rows.min(1_048_576).min(100_000);
        Ok(SheetAxes {
            col_widths: (col0..col0.saturating_add(cols)).map(|c| s.col_width(c) * PT).collect(),
            row_heights: (row0..row0.saturating_add(rows)).map(|r| s.row_height(r) * PT).collect(),
        })
    }

    /// The cells of the window that show something (text, a fill or a border).
    pub(crate) fn cells(&self, sheet: u32, row0: u32, rows: u32, col0: u32, cols: u32) -> Result<Vec<CellView>, MobileError> {
        let book = self.wb();
        let s = self.sheet_in(&book, sheet)?;
        if u64::from(rows) * u64::from(cols) > MAX_WINDOW {
            return Err(MobileError::Command(format!("a window of {rows} × {cols} cells is too large")));
        }
        let wb = &*book;
        let theme = &wb.theme;
        let edge = |b: &BorderLine| CellEdge {
            weight: match b.style {
                BorderStyle::None => 0,
                BorderStyle::Medium
                | BorderStyle::MediumDashed
                | BorderStyle::MediumDashDot
                | BorderStyle::MediumDashDotDot
                | BorderStyle::Double => 2,
                BorderStyle::Thick => 3,
                _ => 1,
            },
            color: rgb(b.color.resolve(theme)).unwrap_or(0),
        };
        let mut out = Vec::new();
        for row in row0..row0.saturating_add(rows) {
            for col in col0..col0.saturating_add(cols) {
                let c = CellRef::new(row, col);
                let style = wb.styles.get(s.style_id(c));
                let text = gridcraft_engine::display::cell_text(wb, s, c);
                let fill = match style.fill.pattern {
                    PatternType::None => None,
                    PatternType::Solid => rgb(style.fill.fg.resolve(theme)),
                    _ => rgb(style.fill.fg.resolve(theme)).or_else(|| rgb(style.fill.bg.resolve(theme))),
                };
                let b = &style.borders;
                if text.is_empty() && fill.is_none() && [&b.left, &b.top, &b.right, &b.bottom].iter().all(|l| l.is_none()) {
                    continue;
                }
                let number = matches!(s.value(c), gridcraft_engine::core::Value::Number(_));
                let align = match style.align.h {
                    HAlign::Center | HAlign::CenterAcross => "c",
                    HAlign::Right => "r",
                    HAlign::General if number => "r",
                    _ => "l",
                };
                let valign = match style.align.v {
                    VAlign::Top => "t",
                    VAlign::Center => "c",
                    _ => "b",
                };
                let f = &style.font;
                out.push(CellView {
                    row,
                    col,
                    text,
                    fill,
                    ink: if matches!(f.color, Color::Auto) { 0 } else { rgb(f.color.resolve(theme)).unwrap_or(0) },
                    bold: f.bold,
                    italic: f.italic,
                    underline: f.underline != Default::default(),
                    strike: f.strike,
                    size: if f.size > 0.0 { f.size } else { 11.0 },
                    align: align.into(),
                    valign: valign.into(),
                    wrap: style.align.wrap,
                    left: edge(&b.left),
                    top: edge(&b.top),
                    right: edge(&b.right),
                    bottom: edge(&b.bottom),
                });
            }
        }
        Ok(out)
    }

    /// The merged areas of sheet `sheet`.
    pub(crate) fn merges(&self, sheet: u32) -> Result<Vec<CellSpan>, MobileError> {
        let wb = self.wb();
        Ok(self
            .sheet_in(&wb, sheet)?
            .merges
            .iter()
            .map(|m| CellSpan { row0: m.start.row, col0: m.start.col, row1: m.end.row, col1: m.end.col })
            .collect())
    }

    /// The cells of sheet `sheet` whose shown text holds `query` (at most `limit`), in reading order.
    pub(crate) fn find(&self, sheet: u32, query: &str, case_sensitive: bool, limit: u32) -> Result<Vec<CellSpan>, MobileError> {
        let wb = self.wb();
        let s = self.sheet_in(&wb, sheet)?;
        let q = if case_sensitive { query.to_string() } else { query.to_lowercase() };
        if q.trim().is_empty() {
            return Ok(Vec::new());
        }
        let Some(used) = s.used_range() else { return Ok(Vec::new()) };
        let mut out = Vec::new();
        for row in 0..=used.end.row {
            for col in 0..=used.end.col {
                let t = gridcraft_engine::display::cell_text(&wb, s, CellRef::new(row, col));
                let hit = if case_sensitive { t.contains(&q) } else { t.to_lowercase().contains(&q) };
                if hit {
                    out.push(CellSpan { row0: row, col0: col, row1: row, col1: col });
                    if out.len() >= limit as usize {
                        return Ok(out);
                    }
                }
            }
        }
        Ok(out)
    }

    /// A sheet's used size in points (at least a screenful of the default grid).
    fn extent(s: &Sheet) -> (f32, f32) {
        let (rows, cols) = s.used_range().map_or((20, 8), |r| (r.end.row.saturating_add(1).max(20), r.end.col.saturating_add(1).max(8)));
        let w: f32 = (0..cols.min(200)).map(|c| s.col_width(c) * PT).sum();
        let h: f32 = (0..rows.min(2_000)).map(|r| s.row_height(r) * PT).sum();
        (w.max(1.0), h.max(1.0))
    }
}

impl OfficeDoc for SheetBook {
    fn info(&self, id: u64) -> DocumentInfo {
        let (p, dirty, generation, undo, redo) = {
            let s = self.lock();
            let d = s.active();
            (
                d.map(|d| d.wb.clone()).unwrap_or_default(),
                d.is_some_and(|d| d.is_dirty()),
                d.map_or(0, |d| d.revision),
                d.and_then(|d| d.undo.last()).map(|h| h.label.clone()),
                d.and_then(|d| d.redo.last()).map(|h| h.label.clone()),
            )
        };
        DocumentInfo {
            id,
            kind: DocumentKind::Sheets,
            name: self.name.clone(),
            path: Some(self.path.clone()),
            pages: p
                .sheets
                .iter()
                .map(|s| {
                    let (w, h) = Self::extent(s);
                    PageInfo { width: w, height: h, label: s.name.clone(), rotation: 0 }
                })
                .collect(),
            title: None,
            author: None,
            subject: None,
            keywords: None,
            creator: None,
            producer: None,
            pdf_version: String::new(),
            file_size: self.file_size,
            encrypted: self.encrypted,
            tagged: false,
            read_only_reason: None,
            allows_printing: true,
            allows_copying: true,
            allows_annotation: false,
            allows_form_filling: false,
            allows_assembly: false,
            dirty,
            undo,
            redo,
            fields: 0,
            has_outline: false,
            attachments: 0,
            signed: false,
            comments_hidden: false,
            generation,
        }
    }

    /// A plain picture of the sheet's top-left: cell fills and grid lines (thumbnails; the app draws
    /// the real grid itself).
    fn render_page(&self, page: u32, scale: f32, tile: Option<TileRect>) -> Result<RenderedImage, MobileError> {
        let s = self.sheet(page)?;
        let s = &*s;
        let (w, h) = Self::extent(s);
        let scale = scale.clamp(0.01, 4.0);
        let (full_w, full_h) = (((w * scale).ceil() as u32).clamp(1, 2048), ((h * scale).ceil() as u32).clamp(1, 2048));
        let t = tile.unwrap_or(TileRect { x: 0, y: 0, width: full_w, height: full_h });
        let (tw, th) = (t.width.clamp(1, 2048), t.height.clamp(1, 2048));
        let mut rgba = vec![255u8; (tw as usize) * (th as usize) * 4];
        let mut put = |x: i64, y: i64, c: u32| {
            let (x, y) = (x - i64::from(t.x), y - i64::from(t.y));
            if x < 0 || y < 0 || x >= i64::from(tw) || y >= i64::from(th) {
                return;
            }
            let i = ((y as usize) * (tw as usize) + x as usize) * 4;
            if let Some(px) = rgba.get_mut(i..i + 4) {
                px.copy_from_slice(&[(c >> 16) as u8, (c >> 8) as u8, c as u8, 255]);
            }
        };
        let (rows, cols) = s.used_range().map_or((20, 8), |r| (r.end.row.saturating_add(1).clamp(20, 200), r.end.col.saturating_add(1).clamp(8, 60)));
        let col_x: Vec<f32> = std::iter::once(0.0)
            .chain((0..cols).scan(0.0, |a, c| {
                *a += s.col_width(c) * PT;
                Some(*a)
            }))
            .collect();
        let row_y: Vec<f32> = std::iter::once(0.0)
            .chain((0..rows).scan(0.0, |a, r| {
                *a += s.row_height(r) * PT;
                Some(*a)
            }))
            .collect();
        for cell in self.cells(page, 0, rows, 0, cols)? {
            let (Some(x0), Some(x1), Some(y0), Some(y1)) =
                (col_x.get(cell.col as usize), col_x.get(cell.col as usize + 1), row_y.get(cell.row as usize), row_y.get(cell.row as usize + 1))
            else {
                continue;
            };
            // a cell with text gets a grey bar for its words, a filled one its fill
            let fill = cell.fill.or((!cell.text.is_empty()).then_some(0x00C8_CDD6));
            let Some(color) = fill else { continue };
            let inset = if cell.fill.is_some() { 0.0 } else { (y1 - y0) * 0.3 };
            for y in ((y0 + inset) * scale) as i64..((y1 - inset) * scale) as i64 {
                for x in (x0 * scale) as i64..(x1 * scale) as i64 {
                    put(x, y, color);
                }
            }
        }
        if s.show_gridlines {
            let line = 0x00E2_E5EA;
            for x in &col_x {
                for y in 0..full_h {
                    put((x * scale) as i64, i64::from(y), line);
                }
            }
            for y in &row_y {
                for x in 0..full_w {
                    put(i64::from(x), (y * scale) as i64, line);
                }
            }
        }
        Ok(RenderedImage { width: tw, height: th, rgba })
    }

    fn page_text(&self, _page: u32) -> Result<Arc<PageText>, MobileError> {
        // the grid has its own search ([`SheetBook::find`])
        Ok(Arc::new(PageText::default()))
    }

    fn links(&self, _page: u32) -> Result<Vec<LinkInfo>, MobileError> {
        Ok(Vec::new())
    }

    fn release_memory(&self) {}

    fn export(&self, format: &str) -> Result<Vec<u8>, MobileError> {
        match format {
            "pdf" => {
                // the engine's own export: each sheet's page setup, every visible sheet
                let out =
                    self.lock().execute("file.exportPdf", serde_json::json!({ "sheets": "all" })).map_err(|e| MobileError::Command(e.to_string()))?;
                let b64 = out.get("base64").and_then(serde_json::Value::as_str).unwrap_or_default();
                gridcraft_engine::io::base64_decode(b64).ok_or_else(|| MobileError::Command("the workbook could not be written as PDF".into()))
            }
            "xlsx" | "csv" => {
                gridcraft_engine::io::save_bytes(&self.wb(), &format!("workbook.{format}")).map_err(|e| MobileError::Command(e.to_string()))
            }
            _ => Err(crate::office::unknown_format(DocumentKind::Sheets, format)),
        }
    }

    fn as_sheets(&self) -> Option<&SheetBook> {
        Some(self)
    }

    fn save(&self, path: &str) -> Result<(), MobileError> {
        self.save_to(path)
    }
}

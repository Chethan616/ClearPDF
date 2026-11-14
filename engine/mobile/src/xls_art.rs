//! The pictures and charts of a legacy Excel 97-2003 `.xls` workbook, which GridCraft's BIFF8
//! reader leaves out. Read from Microsoft's public specifications: [MS-XLS] (the Workbook
//! stream: `BoundSheet8`, `ExternSheet`, `MsoDrawingGroup`, `MsoDrawing`, `Obj` and the chart
//! substreams) and [MS-ODRAW] (the OfficeArt drawing records inside them).
//!
//! A sheet's drawing is one OfficeArt record tree cut into its `MsoDrawing` records; every shape
//! in it that carries client data is matched, in order, with the sheet's `Obj` records, which say
//! what the shape is (a picture, a chart, …). A picture's bytes are in the workbook's picture
//! store; a chart is the chart substream that follows its `Obj`, and becomes a GridCraft chart
//! whose series point at the same cells, so GridCraft draws it from the workbook's own values.

use std::io::Read;
use std::sync::Arc;

use gridcraft_engine::core::CellRef;
use gridcraft_engine::model::{Anchor, AnchorMode, Chart, ChartKind, Image, LegendPos, Series, Sheet, Workbook};

use crate::ppt::{Picture, records, store_pictures, u16_at, u32_at};

/// Records read from the Workbook stream at most.
const MAX_RECORDS: usize = 2_000_000;
/// Pictures and charts taken from one workbook at most.
const MAX_OBJECTS: usize = 400;

const BOF: u16 = 0x0809;
const EOF: u16 = 0x000A;
const CONTINUE: u16 = 0x003C;
const BOUND_SHEET: u16 = 0x0085;
const EXTERN_SHEET: u16 = 0x0017;
const DRAWING_GROUP: u16 = 0x00EB;
const DRAWING: u16 = 0x00EC;
const OBJ: u16 = 0x005D;

const ART_DGG: u16 = 0xF000;
const ART_BSTORE: u16 = 0xF001;
const ART_SPGR: u16 = 0xF003;
const ART_SP: u16 = 0xF004;
const ART_FOPT: u16 = 0xF00B;
const ART_CLIENT_ANCHOR: u16 = 0xF010;
const ART_CLIENT_DATA: u16 = 0xF011;

/// One BIFF record: its type and body.
struct Biff<'a> {
    kind: u16,
    body: &'a [u8],
}

fn biff_at(data: &[u8], at: usize) -> Option<(Biff<'_>, usize)> {
    let kind = u16_at(data, at)?;
    let len = usize::from(u16_at(data, at + 2)?);
    let end = (at + 4).checked_add(len)?;
    Some((Biff { kind, body: data.get(at + 4..end.min(data.len()))? }, end))
}

/// A sheet as `BoundSheet8` lists it: where its substream starts, its type and its name.
struct Bound {
    at: usize,
    worksheet: bool,
    name: String,
}

/// An `XLUnicodeString`-like run of `count` characters at `at` (one byte each, or two when
/// `wide`), and where it ends.
fn chars(b: &[u8], at: usize, count: usize, wide: bool) -> (String, usize) {
    if wide {
        let units: Vec<u16> = (0..count).filter_map(|i| u16_at(b, at + i * 2)).collect();
        (String::from_utf16_lossy(&units), at + count * 2)
    } else {
        let s = b.get(at..(at + count).min(b.len())).unwrap_or_default().iter().map(|&c| char::from(c)).collect();
        (s, at + count)
    }
}

/// A `ShortXLUnicodeString` at `at`: a byte count, a flags byte, then the characters.
fn short_string(b: &[u8], at: usize) -> String {
    let n = usize::from(b.get(at).copied().unwrap_or(0));
    let wide = b.get(at + 1).copied().unwrap_or(0) & 1 == 1;
    chars(b, at + 2, n, wide).0
}

fn col_name(mut c: u32) -> String {
    let mut s = Vec::new();
    loop {
        s.push(b'A' + (c % 26) as u8);
        if c < 26 {
            break;
        }
        c = c / 26 - 1;
    }
    s.reverse();
    String::from_utf8_lossy(&s).into_owned()
}

/// What the workbook-wide records say: its sheets, its external sheet list and its picture store.
struct Globals {
    bounds: Vec<Bound>,
    /// `ExternSheet`'s entries: the first sheet each points at (an index into `bounds`).
    xti: Vec<i16>,
    pictures: Vec<Option<Picture>>,
}

fn globals(data: &[u8]) -> Globals {
    let mut g = Globals { bounds: Vec::new(), xti: Vec::new(), pictures: Vec::new() };
    let mut group = Vec::new();
    let mut in_group = false;
    let mut at = 0usize;
    for _ in 0..MAX_RECORDS {
        let Some((r, next)) = biff_at(data, at) else { break };
        at = next;
        match r.kind {
            EOF => break,
            BOUND_SHEET => {
                let pos = u32_at(r.body, 0).unwrap_or(0) as usize;
                let dt = r.body.get(5).copied().unwrap_or(0);
                g.bounds.push(Bound { at: pos, worksheet: dt == 0, name: short_string(r.body, 6) });
            }
            EXTERN_SHEET => {
                let n = usize::from(u16_at(r.body, 0).unwrap_or(0));
                g.xti = (0..n).map(|i| u16_at(r.body, 2 + i * 6 + 2).unwrap_or(0) as i16).collect();
            }
            DRAWING_GROUP => {
                group.extend_from_slice(r.body);
                in_group = true;
                continue;
            }
            CONTINUE if in_group => {
                group.extend_from_slice(r.body);
                continue;
            }
            _ => {}
        }
        in_group = false;
    }
    // the drawing group ▸ its picture store
    let store = records(&group).find(|r| r.kind == ART_DGG).and_then(|dgg| records(dgg.body).find(|r| r.kind == ART_BSTORE).map(|s| s.body.to_vec()));
    if let Some(store) = store {
        g.pictures = store_pictures(&store, &[]);
    }
    g
}

/// A shape of a sheet's drawing that has an `Obj`: where it is anchored ([MS-XLS]
/// `OfficeArtClientAnchorSheet`: columns and rows, with offsets in 1/1024 of a column and 1/256
/// of a row) and the picture it shows (1-based in the store), if any.
struct Shape {
    anchor: Option<[u16; 8]>,
    picture: Option<u32>,
}

fn shapes(drawing: &[u8], out: &mut Vec<Shape>, depth: usize) {
    if depth > 12 {
        return;
    }
    for r in records(drawing).take(100_000) {
        match r.kind {
            ART_SP => {
                let mut shape = Shape { anchor: None, picture: None };
                let mut client = false;
                for c in records(r.body) {
                    match c.kind {
                        ART_CLIENT_ANCHOR => {
                            let mut a = [0u16; 8];
                            for (i, v) in a.iter_mut().enumerate() {
                                *v = u16_at(c.body, 2 + i * 2).unwrap_or(0);
                            }
                            shape.anchor = Some(a);
                        }
                        ART_FOPT => {
                            // properties: 6 bytes each, the picture is `pib` (0x0104)
                            let n = usize::from(c.instance);
                            for i in 0..n {
                                let id = u16_at(c.body, i * 6).unwrap_or(0);
                                if id & 0x3FFF == 0x0104 {
                                    shape.picture = u32_at(c.body, i * 6 + 2);
                                }
                            }
                        }
                        ART_CLIENT_DATA => client = true,
                        _ => {}
                    }
                }
                if client {
                    out.push(shape);
                }
            }
            ART_SPGR => shapes(r.body, out, depth + 1),
            _ if r.container => shapes(r.body, out, depth + 1),
            _ => {}
        }
    }
}

/// What a chart substream holds that a GridCraft chart needs.
#[derive(Default)]
struct ChartBits {
    kind: Option<ChartKind>,
    series: Vec<SeriesBits>,
    title: Option<String>,
    x_title: Option<String>,
    y_title: Option<String>,
    legend: Option<LegendPos>,
    hole: u8,
    angle: u16,
}

#[derive(Default)]
struct SeriesBits {
    name: Option<String>,
    values: Option<String>,
    categories: Option<String>,
}

/// A chart reference (`PtgRef3d` or `PtgArea3d`, the first of a list) as `'Sheet'!$A$1:$B$2`, or a
/// literal string (`PtgStr`) quoted.
fn chart_ref(rgce: &[u8], g: &Globals) -> Option<String> {
    let ptg = *rgce.first()?;
    let sheet = |ixti: u16| -> Option<String> {
        let tab = *g.xti.get(usize::from(ixti))?;
        let name = &g.bounds.get(usize::try_from(tab).ok()?)?.name;
        Some(format!("'{}'", name.replace('\'', "''")))
    };
    match ptg {
        0x3A | 0x5A | 0x7A => {
            let s = sheet(u16_at(rgce, 1)?)?;
            let row = u32::from(u16_at(rgce, 3)?);
            let col = u32::from(u16_at(rgce, 5)? & 0x3FFF);
            Some(format!("{s}!${}${}", col_name(col), row + 1))
        }
        0x3B | 0x5B | 0x7B => {
            let s = sheet(u16_at(rgce, 1)?)?;
            let (r0, r1) = (u32::from(u16_at(rgce, 3)?), u32::from(u16_at(rgce, 5)?));
            let (c0, c1) = (u32::from(u16_at(rgce, 7)? & 0x3FFF), u32::from(u16_at(rgce, 9)? & 0x3FFF));
            Some(format!("{s}!${}${}:${}${}", col_name(c0), r0 + 1, col_name(c1), r1 + 1))
        }
        0x17 => {
            let n = usize::from(*rgce.get(1)?);
            let wide = rgce.get(2).copied().unwrap_or(0) & 1 == 1;
            Some(format!("\"{}\"", chars(rgce, 3, n, wide).0.replace('"', "\"\"")))
        }
        _ => None,
    }
}

/// The chart substream starting after its `BOF` at `at`: what it charts, and where it ends.
fn chart_bits(data: &[u8], mut at: usize, g: &Globals) -> (ChartBits, usize) {
    let mut c = ChartBits::default();
    let mut depth = 0usize;
    let mut series_depth: Option<usize> = None;
    let mut text_depth: Option<usize> = None;
    let mut text: Option<String> = None;
    let mut link = 0u16;
    for _ in 0..MAX_RECORDS {
        let Some((r, next)) = biff_at(data, at) else { break };
        at = next;
        let b = r.body;
        match r.kind {
            EOF => break,
            // a chart substream inside this one (not expected) is skipped whole
            BOF => {
                let (_, end) = chart_bits(data, at, g);
                at = end;
            }
            0x1033 => depth += 1,
            0x1034 => {
                depth = depth.saturating_sub(1);
                if series_depth == Some(depth) {
                    series_depth = None;
                }
                if text_depth == Some(depth) {
                    let t = text.take().filter(|t| !t.trim().is_empty());
                    match link {
                        1 => c.title = c.title.take().or(t),
                        2 => c.y_title = c.y_title.take().or(t),
                        3 => c.x_title = c.x_title.take().or(t),
                        _ => {}
                    }
                    text_depth = None;
                    link = 0;
                }
            }
            // Series
            0x1003 => {
                c.series.push(SeriesBits::default());
                series_depth = Some(depth);
            }
            // BRAI: where a series' name, values or categories come from
            0x1051 if series_depth.is_some() => {
                let id = b.first().copied().unwrap_or(9);
                let rt = b.get(1).copied().unwrap_or(0);
                let cce = usize::from(u16_at(b, 6).unwrap_or(0));
                let found = if rt == 2 { b.get(8..8 + cce).and_then(|rgce| chart_ref(rgce, g)) } else { None };
                if let (Some(s), Some(f)) = (c.series.last_mut(), found) {
                    match id {
                        0 => s.name = Some(f),
                        1 => s.values = Some(f),
                        2 => s.categories = Some(f),
                        _ => {}
                    }
                }
            }
            // SeriesText: a series' own name, or a title's words
            0x100D => {
                let words = short_string(b, 2);
                if text_depth.is_some() {
                    text = Some(words);
                } else if series_depth.is_some()
                    && let Some(s) = c.series.last_mut()
                    && s.name.is_none()
                {
                    s.name = Some(format!("\"{}\"", words.replace('"', "\"\"")));
                }
            }
            // Text, and the ObjectLink that says what it labels
            0x1025 => {
                text_depth = Some(depth);
                text = None;
                link = 0;
            }
            0x1027 if text_depth.is_some() => link = u16_at(b, 0).unwrap_or(0),
            0x1015 => {
                c.legend = Some(match b.get(16).copied().unwrap_or(3) {
                    0 => LegendPos::Bottom,
                    2 => LegendPos::Top,
                    4 => LegendPos::Left,
                    _ => LegendPos::Right,
                });
            }
            // the chart's kind: the first chart group's
            0x1017 if c.kind.is_none() => {
                let f = u16_at(b, 4).unwrap_or(0);
                let (bar, stacked, full) = (f & 1 == 1, f & 2 == 2, f & 4 == 4);
                c.kind = Some(match (bar, stacked, full) {
                    (true, true, true) => ChartKind::BarStacked100,
                    (true, true, false) => ChartKind::BarStacked,
                    (true, false, _) => ChartKind::BarClustered,
                    (false, true, true) => ChartKind::ColumnStacked100,
                    (false, true, false) => ChartKind::ColumnStacked,
                    (false, false, _) => ChartKind::ColumnClustered,
                });
            }
            0x1018 if c.kind.is_none() => {
                let f = u16_at(b, 0).unwrap_or(0);
                c.kind = Some(if f & 1 == 1 { ChartKind::LineStacked } else { ChartKind::LineMarkers });
            }
            0x1019 if c.kind.is_none() => {
                c.angle = u16_at(b, 0).unwrap_or(0) % 360;
                let hole = u16_at(b, 2).unwrap_or(0);
                c.hole = hole.min(90) as u8;
                c.kind = Some(if hole > 0 { ChartKind::Doughnut } else { ChartKind::Pie });
            }
            0x101A if c.kind.is_none() => {
                let f = u16_at(b, 0).unwrap_or(0);
                c.kind = Some(match (f & 1 == 1, f & 2 == 2) {
                    (true, true) => ChartKind::AreaStacked100,
                    (true, false) => ChartKind::AreaStacked,
                    _ => ChartKind::Area,
                });
            }
            0x101B if c.kind.is_none() => {
                let f = u16_at(b, 4).unwrap_or(0);
                c.kind = Some(if f & 1 == 1 { ChartKind::Bubble } else { ChartKind::Scatter });
            }
            0x103E | 0x1040 if c.kind.is_none() => c.kind = Some(ChartKind::Radar),
            _ => {}
        }
    }
    (c, at)
}

/// A sheet's drawing, its `Obj` records (object type, and the chart substream after a chart's).
fn sheet_objects(data: &[u8], start: usize, g: &Globals) -> (Vec<u8>, Vec<(u16, Option<ChartBits>)>) {
    let mut drawing = Vec::new();
    let mut objects: Vec<(u16, Option<ChartBits>)> = Vec::new();
    let mut at = start;
    let mut first = true;
    let mut in_drawing = false;
    for _ in 0..MAX_RECORDS {
        let Some((r, next)) = biff_at(data, at) else { break };
        at = next;
        match r.kind {
            BOF if first => first = false,
            BOF => {
                // a chart's own substream follows its Obj
                let chart = u16_at(r.body, 2) == Some(0x0020);
                let (bits, end) = chart_bits(data, at, g);
                at = end;
                if chart && let Some(last) = objects.last_mut().filter(|o| o.0 == 5 && o.1.is_none()) {
                    last.1 = Some(bits);
                }
                in_drawing = false;
                continue;
            }
            EOF => break,
            DRAWING => {
                drawing.extend_from_slice(r.body);
                in_drawing = true;
                continue;
            }
            CONTINUE if in_drawing => {
                drawing.extend_from_slice(r.body);
                continue;
            }
            // Obj: its first part (ftCmo) gives the object's type
            OBJ => objects.push((u16_at(r.body, 4).unwrap_or(0), None)),
            _ => {}
        }
        in_drawing = false;
    }
    (drawing, objects)
}

/// Where an OfficeArt anchor puts a shape on `sheet`, in GridCraft's terms (points from its cell).
fn anchor_on(sheet: &Sheet, a: [u16; 8]) -> Option<Anchor> {
    let [c0, dx0, r0, dy0, c1, dx1, r1, dy1] = a.map(u32::from);
    let x = |c: u32, d: u32| sheet.col_left(c) as f32 + sheet.col_width(c) * (d.min(1024) as f32 / 1024.0);
    let y = |r: u32, d: u32| sheet.row_top(r) as f32 + sheet.row_height(r) * (d.min(256) as f32 / 256.0);
    let (left, top, right, bottom) = (x(c0, dx0), y(r0, dy0), x(c1, dx1), y(r1, dy1));
    if !(right > left && bottom > top) {
        return None;
    }
    Some(Anchor {
        cell: CellRef { row: r0, col: c0 },
        dx: left - sheet.col_left(c0) as f32,
        dy: top - sheet.row_top(r0) as f32,
        width: right - left,
        height: bottom - top,
        mode: AnchorMode::default(),
    })
}

/// Adds the pictures and charts of the `.xls` file `bytes` to `wb` (read from it by GridCraft).
/// Anything unreadable is left out: the cells are what matter.
pub(crate) fn import(bytes: &[u8], wb: &mut Workbook) {
    let Ok(mut cfb) = cfb::CompoundFile::open(std::io::Cursor::new(bytes)) else { return };
    let mut data = Vec::new();
    {
        let Ok(mut stream) = cfb.open_stream("Workbook").or_else(|_| cfb.open_stream("Book")) else { return };
        if stream.by_ref().take(512 << 20).read_to_end(&mut data).is_err() {
            return;
        }
    }
    let g = globals(&data);
    let mut taken = 0usize;
    let mut next_id = 10_000u32;
    for bound in g.bounds.iter().filter(|b| b.worksheet) {
        let Some(index) = wb.sheets.iter().position(|s| s.name == bound.name) else { continue };
        let (drawing, objects) = sheet_objects(&data, bound.at, &g);
        if objects.is_empty() {
            continue;
        }
        let mut found = Vec::new();
        shapes(&drawing, &mut found, 0);
        let sheet = Arc::make_mut(&mut wb.sheets[index]);
        let mut charts = Vec::new();
        let mut images = Vec::new();
        for (shape, (kind, chart)) in found.into_iter().zip(objects) {
            if taken >= MAX_OBJECTS {
                break;
            }
            let Some(anchor) = shape.anchor.and_then(|a| anchor_on(sheet, a)) else { continue };
            match (kind, chart) {
                // a picture
                (8, _) => {
                    let Some(p) = shape.picture.and_then(|i| g.pictures.get((i as usize).checked_sub(1)?)?.as_ref()) else { continue };
                    next_id += 1;
                    taken += 1;
                    images.push(Image { id: next_id, anchor, data: Arc::new(p.data.clone()), mime: p.mime.to_string(), alt: String::new() });
                }
                // a chart
                (5, Some(bits)) => {
                    let series: Vec<Series> = bits
                        .series
                        .into_iter()
                        .filter_map(|s| Some(Series { name: s.name, categories: s.categories, values: s.values?, ..Default::default() }))
                        .collect();
                    if series.is_empty() {
                        continue;
                    }
                    next_id += 1;
                    taken += 1;
                    let kind = bits.kind.unwrap_or_default();
                    let round = matches!(kind, ChartKind::Pie | ChartKind::Doughnut);
                    charts.push(Chart {
                        id: next_id,
                        kind,
                        anchor,
                        title: bits.title,
                        series,
                        legend: bits.legend.unwrap_or(LegendPos::None),
                        data_labels: false,
                        data_table: None,
                        gridlines: !round,
                        style: 2,
                        hole_size: if bits.hole > 0 { bits.hole.max(10) } else { 50 },
                        first_slice_angle: bits.angle,
                        x_title: bits.x_title,
                        y_title: bits.y_title,
                        source: None,
                        by_rows: false,
                        pivot: None,
                    });
                }
                _ => {}
            }
        }
        sheet.images.extend(images);
        sheet.charts.extend(charts);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn column_names_go_past_z() {
        assert_eq!(col_name(0), "A");
        assert_eq!(col_name(25), "Z");
        assert_eq!(col_name(26), "AA");
        assert_eq!(col_name(701), "ZZ");
        assert_eq!(col_name(702), "AAA");
    }

    #[test]
    fn a_chart_reference_names_its_sheet_and_cells() {
        let g = Globals {
            bounds: vec![Bound { at: 0, worksheet: true, name: "Sales Q1".into() }, Bound { at: 0, worksheet: true, name: "It's".into() }],
            xti: vec![0, 1],
            pictures: Vec::new(),
        };
        // PtgArea3d: ixti 0, rows 1..=4, column B
        let area = [0x3B, 0, 0, 1, 0, 4, 0, 1, 0xC0, 1, 0xC0];
        assert_eq!(chart_ref(&area, &g).as_deref(), Some("'Sales Q1'!$B$2:$B$5"));
        // PtgRef3d: ixti 1, row 0, column A
        let one = [0x3A, 1, 0, 0, 0, 0, 0xC0];
        assert_eq!(chart_ref(&one, &g).as_deref(), Some("'It''s'!$A$1"));
        // PtgStr
        let s = [0x17, 3, 0, b'T', b'o', b'p'];
        assert_eq!(chart_ref(&s, &g).as_deref(), Some("\"Top\""));
    }
}

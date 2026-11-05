//! Flowing text layout shared by Word and RTF documents: paragraphs of styled runs broken into
//! lines with real glyph widths, tables with column widths, and pagination.

use super::draw::{BLACK, Face, Op, Rgb, drawable_text, place, text_width};

/// How deep tables may nest inside table cells before inner ones are laid out as plain text.
const MAX_TABLE_DEPTH: usize = 6;
/// Most lines one paragraph may produce (a guard against pathological input).
const MAX_LINES: usize = 200_000;

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Style {
    pub face: Face,
    pub size: f64,
    pub color: Rgb,
    pub underline: bool,
    pub strike: bool,
    pub highlight: Option<Rgb>,
    /// Baseline raise in points (superscript > 0, subscript < 0).
    pub shift: f64,
}

impl Default for Style {
    fn default() -> Self {
        Style { face: Face::default(), size: 11.0, color: BLACK, underline: false, strike: false, highlight: None, shift: 0.0 }
    }
}

#[derive(Clone, Debug)]
pub(crate) enum Inline {
    Text(String, Style),
    Tab(Style),
    Break,
    /// An embedded image (writer id) of `w × h` points.
    Image {
        id: usize,
        w: f64,
        h: f64,
    },
}

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) enum Align {
    #[default]
    Left,
    Center,
    Right,
    Justify,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum LineRule {
    /// A multiple of single spacing.
    Auto(f64),
    Exact(f64),
    AtLeast(f64),
}

/// How text lines up at a tab stop.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) enum TabAlign {
    #[default]
    Left,
    Center,
    /// Right (decimal stops are treated as right stops).
    Right,
}

/// A tab stop, from the left edge of the text column, with an optional leader character
/// (dots in a table of contents).
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) struct TabStop {
    pub pos: f64,
    pub align: TabAlign,
    pub leader: Option<char>,
}

#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub(crate) enum VAlign {
    #[default]
    Top,
    Center,
    Bottom,
}

#[derive(Clone, Debug)]
pub(crate) struct Para {
    pub inlines: Vec<Inline>,
    pub align: Align,
    pub left: f64,
    pub right: f64,
    /// First-line offset from `left` (negative: a hanging indent).
    pub first: f64,
    pub before: f64,
    pub after: f64,
    pub line: LineRule,
    pub page_break_before: bool,
    /// A list label (bullet or number) set at the first-line indent.
    pub label: Option<(String, Style)>,
    pub shading: Option<Rgb>,
    /// Tab stops, from the left edge of the text column.
    pub tabs: Vec<TabStop>,
    pub border_bottom: Option<(Rgb, f64)>,
    /// Style id, for contextual spacing (no space between paragraphs of the same style).
    pub style_id: String,
    pub contextual: bool,
    /// Size of the paragraph mark: the height of an empty paragraph.
    pub mark_size: f64,
}

impl Default for Para {
    fn default() -> Self {
        Para {
            inlines: Vec::new(),
            align: Align::Left,
            left: 0.0,
            right: 0.0,
            first: 0.0,
            before: 0.0,
            after: 0.0,
            line: LineRule::Auto(1.0),
            page_break_before: false,
            label: None,
            shading: None,
            tabs: Vec::new(),
            border_bottom: None,
            style_id: String::new(),
            contextual: false,
            mark_size: 11.0,
        }
    }
}

#[derive(Clone, Debug)]
pub(crate) enum Block {
    Para(Para),
    Table(Table),
    PageBreak,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct Table {
    /// Grid column widths in points.
    pub cols: Vec<f64>,
    pub rows: Vec<Row>,
    pub indent: f64,
    pub align: Align,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct Row {
    pub cells: Vec<Cell>,
    /// Minimum (or, when `exact`, fixed) height.
    pub height: f64,
    pub exact: bool,
}

/// Border sides: top, right, bottom, left.
pub(crate) type Borders = [Option<(Rgb, f64)>; 4];

#[derive(Clone, Debug)]
pub(crate) struct Cell {
    pub span: usize,
    pub blocks: Vec<Block>,
    pub fill: Option<Rgb>,
    pub borders: Borders,
    /// A vertically merged cell continuing the one above (drawn without content or top border).
    pub merge_continue: bool,
    /// Margins: left, top, right, bottom.
    pub margins: (f64, f64, f64, f64),
    pub valign: VAlign,
}

impl Default for Cell {
    fn default() -> Self {
        Cell {
            span: 1,
            blocks: Vec::new(),
            fill: None,
            borders: [None; 4],
            merge_continue: false,
            margins: (5.4, 0.0, 5.4, 0.0),
            valign: VAlign::Top,
        }
    }
}

/// A laid-out box: its height and operations relative to its top-left corner.
#[derive(Clone, Debug, Default)]
pub(crate) struct VBox {
    pub h: f64,
    pub ops: Vec<Op>,
}

#[derive(Clone, Debug)]
pub(crate) struct CellLayout {
    x: f64,
    w: f64,
    fill: Option<Rgb>,
    borders: Borders,
    lines: Vec<VBox>,
    pad_top: f64,
    pad_bottom: f64,
    valign: VAlign,
}

#[derive(Clone, Debug)]
pub(crate) struct RowLayout {
    cells: Vec<CellLayout>,
    min_h: f64,
    exact: bool,
}

impl RowLayout {
    fn height(&self) -> f64 {
        if self.exact && self.min_h > 0.0 {
            return self.min_h;
        }
        let content = self.cells.iter().map(|c| c.pad_top + c.lines.iter().map(|l| l.h).sum::<f64>() + c.pad_bottom).fold(0.0, f64::max);
        content.max(self.min_h)
    }

    /// Draw the whole row at `(dx, dy)`, `h` tall.
    fn draw(&self, out: &mut Vec<Op>, dx: f64, dy: f64, h: f64) {
        for c in &self.cells {
            if let Some(f) = c.fill {
                out.push(Op::Rect { x: dx + c.x, y: dy, w: c.w, h, fill: Some(f), stroke: None });
            }
        }
        for c in &self.cells {
            let content: f64 = c.lines.iter().map(|l| l.h).sum();
            let free = (h - c.pad_top - c.pad_bottom - content).max(0.0);
            let mut y = dy
                + c.pad_top
                + match c.valign {
                    VAlign::Top => 0.0,
                    VAlign::Center => free / 2.0,
                    VAlign::Bottom => free,
                };
            if self.exact {
                out.push(Op::Clip { x: dx + c.x, y: dy, w: c.w, h });
            }
            for l in &c.lines {
                place(out, &l.ops, dx + c.x, y);
                y += l.h;
            }
            if self.exact {
                out.push(Op::Restore);
            }
        }
        for c in &self.cells {
            borders(out, dx + c.x, dy, c.w, h, &c.borders);
        }
    }
}

fn borders(out: &mut Vec<Op>, x: f64, y: f64, w: f64, h: f64, b: &Borders) {
    let [top, right, bottom, left] = b;
    if let Some((c, wd)) = top {
        out.push(Op::Line { x1: x, y1: y, x2: x + w, y2: y, color: *c, width: *wd });
    }
    if let Some((c, wd)) = bottom {
        out.push(Op::Line { x1: x, y1: y + h, x2: x + w, y2: y + h, color: *c, width: *wd });
    }
    if let Some((c, wd)) = left {
        out.push(Op::Line { x1: x, y1: y, x2: x, y2: y + h, color: *c, width: *wd });
    }
    if let Some((c, wd)) = right {
        out.push(Op::Line { x1: x + w, y1: y, x2: x + w, y2: y + h, color: *c, width: *wd });
    }
}

/// Laid-out content, top to bottom.
#[derive(Clone, Debug)]
pub(crate) enum VItem {
    Box(VBox),
    /// Vertical space, dropped at the top of a page.
    Gap(f64),
    PageBreak,
    Row(RowLayout),
}

// ── line breaking ───────────────────────────────────────────────────────────────────────────

#[derive(Clone, Debug)]
enum Piece {
    Word { text: String, style: Style, w: f64 },
    Space { text: String, style: Style, w: f64 },
    Tab { style: Style, leader: Option<char> },
    Break,
    Image { id: usize, w: f64, h: f64 },
}

fn pieces(inlines: &[Inline]) -> Vec<Piece> {
    let mut out = Vec::new();
    for inline in inlines {
        match inline {
            Inline::Text(t, style) => {
                let t = drawable_text(t);
                let mut word = String::new();
                let mut spaces = String::new();
                let flush_word = |word: &mut String, out: &mut Vec<Piece>| {
                    if !word.is_empty() {
                        let w = text_width(style.face, style.size, word);
                        out.push(Piece::Word { text: std::mem::take(word), style: style.clone(), w });
                    }
                };
                let flush_space = |spaces: &mut String, out: &mut Vec<Piece>| {
                    if !spaces.is_empty() {
                        let w = text_width(style.face, style.size, spaces);
                        out.push(Piece::Space { text: std::mem::take(spaces), style: style.clone(), w });
                    }
                };
                for c in t.chars() {
                    if c == ' ' {
                        flush_word(&mut word, &mut out);
                        spaces.push(c);
                    } else if c == '\n' {
                        flush_word(&mut word, &mut out);
                        flush_space(&mut spaces, &mut out);
                        out.push(Piece::Break);
                    } else {
                        flush_space(&mut spaces, &mut out);
                        word.push(c);
                    }
                }
                flush_word(&mut word, &mut out);
                flush_space(&mut spaces, &mut out);
            }
            Inline::Tab(style) => out.push(Piece::Tab { style: style.clone(), leader: None }),
            Inline::Break => out.push(Piece::Break),
            Inline::Image { id, w, h } => out.push(Piece::Image { id: *id, w: *w, h: *h }),
        }
    }
    out
}

/// A piece placed on a line.
#[derive(Clone, Debug)]
struct Placed {
    x: f64,
    piece: Piece,
    w: f64,
}

#[derive(Clone, Debug, Default)]
struct Line {
    items: Vec<Placed>,
    start: f64,
    end: f64,
    explicit: bool,
}

impl Line {
    fn content_end(&self) -> f64 {
        self.items.iter().rev().find(|p| !matches!(p.piece, Piece::Space { .. })).map_or(self.start, |p| p.x + p.w)
    }
    fn has_content(&self) -> bool {
        self.items.iter().any(|p| !matches!(p.piece, Piece::Space { .. }))
    }
}

const ASCENT: f64 = 0.92;
const DESCENT: f64 = 0.25;

/// Lay out a paragraph `width` points wide into line boxes (with its spacing as gaps).
pub(crate) fn layout_para(p: &Para, width: f64) -> Vec<VItem> {
    let mut out = Vec::new();
    if p.page_break_before {
        out.push(VItem::PageBreak);
    }
    if p.before > 0.0 {
        out.push(VItem::Gap(p.before));
    }
    let width = if width.is_finite() { width.max(10.0) } else { 10.0 };
    let left = p.left.clamp(-width, width);
    let right_edge = (width - p.right.clamp(-width, width)).max(left + 10.0);
    let first_x = (left + p.first).max(-width).min(right_edge - 10.0);
    let mut lines: Vec<Line> = Vec::new();
    let mut cur = Line { start: first_x, end: right_edge, ..Default::default() };
    let mut label_ops: Option<(f64, String, Style)> = None;
    if let Some((label, style)) = &p.label {
        let label = drawable_text(label);
        let lw = text_width(style.face, style.size, &label);
        let gap = text_width(style.face, style.size, " ");
        label_ops = Some((first_x, label, style.clone()));
        let after_label = first_x + lw;
        cur.start = if after_label + gap * 0.5 <= left { left } else { next_tab(after_label + gap * 0.5, &p.tabs, left).pos };
    }
    let mut x = cur.start;
    let items = pieces(&p.inlines);
    let mut i = 0usize;
    let full = |l: &Line| l.end - l.start;
    while i < items.len() {
        if lines.len() > MAX_LINES {
            break;
        }
        let Some(piece) = items.get(i) else { break };
        match piece {
            Piece::Break => {
                cur.explicit = true;
                let next = Line { start: left, end: right_edge, ..Default::default() };
                lines.push(std::mem::replace(&mut cur, next));
                x = left;
                i += 1;
            }
            Piece::Space { w, .. } => {
                // Spaces at the start of a wrapped line are dropped.
                if cur.has_content() || lines.is_empty() || lines.last().is_some_and(|l| l.explicit) {
                    cur.items.push(Placed { x, piece: piece.clone(), w: *w });
                    x += w;
                }
                i += 1;
            }
            Piece::Tab { style, .. } => {
                let stop = next_tab(x + 0.01, &p.tabs, left);
                // Right and centred stops line up the text that follows, up to the next tab.
                let segment: f64 = items
                    .iter()
                    .skip(i + 1)
                    .take_while(|p| !matches!(p, Piece::Tab { .. } | Piece::Break))
                    .map(|p| piece_width(p, right_edge - left))
                    .sum();
                let target = match stop.align {
                    TabAlign::Left => stop.pos,
                    TabAlign::Center => stop.pos - segment / 2.0,
                    TabAlign::Right => stop.pos - segment,
                };
                let space = text_width(style.face, style.size, " ");
                let w = if stop.pos <= cur.end + 0.5 { (target - x).max(if stop.align == TabAlign::Left { 0.0 } else { space }) } else { space };
                cur.items.push(Placed { x, piece: Piece::Tab { style: style.clone(), leader: stop.leader }, w });
                x += w;
                i += 1;
            }
            Piece::Word { .. } | Piece::Image { .. } => {
                // A group: consecutive words and images with no space between them.
                let mut j = i;
                let mut gw = 0.0;
                while let Some(p) = items.get(j) {
                    match p {
                        Piece::Word { w, .. } => gw += w,
                        Piece::Image { w, .. } => gw += w.min(right_edge - left),
                        _ => break,
                    }
                    j += 1;
                }
                if x + gw <= cur.end + 0.01 || !cur.has_content() && gw <= full(&cur) + 0.01 {
                    for p in items.get(i..j).unwrap_or_default() {
                        let w = piece_width(p, right_edge - left);
                        cur.items.push(Placed { x, piece: scaled(p, right_edge - left), w });
                        x += w;
                    }
                    i = j;
                } else if cur.has_content() {
                    let next = Line { start: left, end: right_edge, ..Default::default() };
                    lines.push(std::mem::replace(&mut cur, next));
                    x = left;
                } else {
                    // Wider than a whole line: break it by character.
                    for p in items.get(i..j).unwrap_or_default() {
                        match p {
                            Piece::Word { text, style, .. } => {
                                for ch in text.chars() {
                                    let s = ch.to_string();
                                    let w = text_width(style.face, style.size, &s);
                                    if x + w > cur.end && cur.has_content() {
                                        let next = Line { start: left, end: right_edge, ..Default::default() };
                                        lines.push(std::mem::replace(&mut cur, next));
                                        x = left;
                                    }
                                    cur.items.push(Placed { x, piece: Piece::Word { text: s, style: style.clone(), w }, w });
                                    x += w;
                                }
                            }
                            other => {
                                let w = piece_width(other, right_edge - left);
                                if x + w > cur.end && cur.has_content() {
                                    let next = Line { start: left, end: right_edge, ..Default::default() };
                                    lines.push(std::mem::replace(&mut cur, next));
                                    x = left;
                                }
                                cur.items.push(Placed { x, piece: scaled(other, right_edge - left), w });
                                x += w;
                            }
                        }
                    }
                    i = j;
                }
            }
        }
    }
    lines.push(cur);
    let count = lines.len();
    for (n, line) in lines.into_iter().enumerate() {
        let last = n + 1 == count;
        let label = if n == 0 { label_ops.take() } else { None };
        out.push(VItem::Box(render_line(p, line, last, label, left, right_edge)));
    }
    if let Some((c, w)) = p.border_bottom {
        out.push(VItem::Box(VBox {
            h: 3.0 + w,
            ops: vec![Op::Line { x1: left, y1: 1.5 + w / 2.0, x2: right_edge, y2: 1.5 + w / 2.0, color: c, width: w }],
        }));
    }
    if p.after > 0.0 {
        out.push(VItem::Gap(p.after));
    }
    out
}

fn piece_width(p: &Piece, max: f64) -> f64 {
    match p {
        Piece::Word { w, .. } | Piece::Space { w, .. } => *w,
        Piece::Image { w, .. } => w.min(max),
        _ => 0.0,
    }
}

/// An image wider than the column, scaled down to fit it.
fn scaled(p: &Piece, max: f64) -> Piece {
    match p {
        Piece::Image { id, w, h } if *w > max && *w > 0.0 => Piece::Image { id: *id, w: max, h: h * max / w },
        other => other.clone(),
    }
}

/// The next tab stop after `x`: an explicit stop, the hanging indent, else every half inch.
fn next_tab(x: f64, tabs: &[TabStop], left: f64) -> TabStop {
    let mut best = None::<TabStop>;
    for t in tabs {
        if t.pos > x && best.is_none_or(|b| t.pos < b.pos) {
            best = Some(*t);
        }
    }
    if left > x && best.is_none_or(|b| left < b.pos) {
        best = Some(TabStop { pos: left, ..Default::default() });
    }
    best.unwrap_or_else(|| TabStop { pos: ((x / 36.0).floor() + 1.0) * 36.0, ..Default::default() })
}

fn render_line(p: &Para, line: Line, last: bool, label: Option<(f64, String, Style)>, left: f64, right_edge: f64) -> VBox {
    let mut asc: f64 = 0.0;
    let mut desc: f64 = 0.0;
    let mut has_text = false;
    for it in &line.items {
        match &it.piece {
            Piece::Word { style, .. } | Piece::Space { style, .. } | Piece::Tab { style, .. } => {
                asc = asc.max(style.size * ASCENT + style.shift.max(0.0));
                desc = desc.max(style.size * DESCENT - style.shift.min(0.0));
                has_text = true;
            }
            Piece::Image { h, .. } => asc = asc.max(*h),
            Piece::Break => {}
        }
    }
    if let Some((_, _, s)) = &label {
        asc = asc.max(s.size * ASCENT);
        desc = desc.max(s.size * DESCENT);
        has_text = true;
    }
    if !has_text {
        // Images alone still sit on a text line's descent; an empty line has the mark's height.
        desc = desc.max(p.mark_size * DESCENT);
        if asc == 0.0 {
            asc = p.mark_size * ASCENT;
        }
    }
    let natural = asc + desc;
    let h = match p.line {
        LineRule::Auto(m) => {
            let m = if m.is_finite() { m.clamp(0.25, 10.0) } else { 1.0 };
            // Extra spacing applies to text; an image line keeps at least its own height.
            if has_text { (natural * m).max(asc + desc * m.min(1.0)) } else { natural }
        }
        LineRule::Exact(v) => v.clamp(1.0, 2000.0),
        LineRule::AtLeast(v) => natural.max(v.clamp(0.0, 2000.0)),
    };
    let base = h - desc;
    // Alignment and justification.
    let content_end = line.content_end();
    let slack = (line.end - content_end).max(0.0);
    let justify = p.align == Align::Justify && !last && !line.explicit;
    let spaces: usize = if justify {
        let last_content = line.items.iter().rposition(|p| !matches!(p.piece, Piece::Space { .. })).unwrap_or(0);
        line.items.iter().take(last_content).map(|p| if let Piece::Space { text, .. } = &p.piece { text.chars().count() } else { 0 }).sum()
    } else {
        0
    };
    let (shift, per_space) = match p.align {
        Align::Center => (slack / 2.0, 0.0),
        Align::Right => (slack, 0.0),
        Align::Justify if justify && spaces > 0 => (0.0, slack / spaces as f64),
        _ => (0.0, 0.0),
    };
    let mut ops = Vec::new();
    let mut under = Vec::new();
    if let Some(fill) = p.shading {
        under.push(Op::Rect { x: left, y: 0.0, w: right_edge - left, h, fill: Some(fill), stroke: None });
    }
    if let Some((lx, text, style)) = label {
        ops.push(Op::Text { x: lx, y: base, face: style.face, size: style.size, color: style.color, text, word_spacing: 0.0 });
    }
    // Runs of text in one style become one text operation; spaces stretch with Tw when justified.
    let mut extra = 0.0;
    let mut run: Option<(f64, String, Style, f64)> = None;
    let flush = |run: &mut Option<(f64, String, Style, f64)>, ops: &mut Vec<Op>, under: &mut Vec<Op>, end_x: f64| {
        if let Some((x, text, style, _)) = run.take() {
            let trimmed = text.trim_end();
            if trimmed.is_empty() {
                return;
            }
            let y = base - style.shift;
            if let Some(hl) = style.highlight {
                under.push(Op::Rect {
                    x,
                    y: y - style.size * ASCENT,
                    w: end_x - x,
                    h: style.size * (ASCENT + DESCENT),
                    fill: Some(hl),
                    stroke: None,
                });
            }
            let ul_end = end_x;
            if style.underline {
                let lw = (style.size * 0.06).max(0.4);
                ops.push(Op::Line { x1: x, y1: y + style.size * 0.12, x2: ul_end, y2: y + style.size * 0.12, color: style.color, width: lw });
            }
            if style.strike {
                let lw = (style.size * 0.06).max(0.4);
                ops.push(Op::Line { x1: x, y1: y - style.size * 0.28, x2: ul_end, y2: y - style.size * 0.28, color: style.color, width: lw });
            }
            ops.push(Op::Text { x, y, face: style.face, size: style.size, color: style.color, text: trimmed.to_string(), word_spacing: per_space });
        }
    };
    let mut run_end = 0.0;
    let last_content = line.items.iter().rposition(|p| !matches!(p.piece, Piece::Space { .. }));
    for (k, it) in line.items.iter().enumerate() {
        if last_content.is_some_and(|lc| k > lc) {
            break;
        }
        let x = it.x + shift + extra;
        match &it.piece {
            Piece::Word { text, style, .. } | Piece::Space { text, style, .. } => {
                let same = run.as_ref().is_some_and(|(_, _, s, _)| s == style);
                if !same {
                    flush(&mut run, &mut ops, &mut under, run_end);
                    if matches!(it.piece, Piece::Space { .. }) && !style.underline && !style.strike && style.highlight.is_none() {
                        // A space alone never starts a run.
                        if let Piece::Space { text, .. } = &it.piece {
                            extra += per_space * text.chars().count() as f64;
                        }
                        run_end = x + it.w;
                        continue;
                    }
                    run = Some((x, String::new(), style.clone(), 0.0));
                }
                if let Some((_, t, _, _)) = run.as_mut() {
                    t.push_str(text);
                }
                if let Piece::Space { text, .. } = &it.piece {
                    extra += per_space * text.chars().count() as f64;
                }
                run_end = it.x + shift + extra + it.w;
            }
            Piece::Tab { style, leader } => {
                flush(&mut run, &mut ops, &mut under, run_end);
                if let Some(ch) = leader {
                    let dot = ch.to_string();
                    let cw = text_width(style.face, style.size, &dot);
                    if cw > 0.0 && it.w > cw * 2.0 {
                        let n = ((it.w - cw) / cw).floor().clamp(0.0, 2000.0) as usize;
                        let text = dot.repeat(n);
                        let tw = cw * n as f64;
                        ops.push(Op::Text {
                            x: x + it.w - tw - cw * 0.5,
                            y: base - style.shift,
                            face: style.face,
                            size: style.size,
                            color: style.color,
                            text,
                            word_spacing: 0.0,
                        });
                    }
                }
                if style.underline {
                    let y = base + style.size * 0.12;
                    ops.push(Op::Line { x1: x, y1: y, x2: x + it.w, y2: y, color: style.color, width: (style.size * 0.06).max(0.4) });
                }
                run_end = x + it.w;
            }
            Piece::Image { id, w, h } => {
                flush(&mut run, &mut ops, &mut under, run_end);
                ops.push(Op::Image { id: *id, x, y: base - h, w: *w, h: *h });
                run_end = x + it.w;
            }
            Piece::Break => {}
        }
    }
    flush(&mut run, &mut ops, &mut under, run_end);
    under.extend(ops);
    VBox { h, ops: under }
}

// ── blocks and tables ───────────────────────────────────────────────────────────────────────

/// Lay out blocks `width` points wide.
pub(crate) fn layout_blocks(blocks: &[Block], width: f64, depth: usize) -> Vec<VItem> {
    let mut out = Vec::new();
    let mut prev: Option<&Para> = None;
    for (i, b) in blocks.iter().enumerate() {
        match b {
            Block::Para(p) => {
                // Contextual spacing: no space between paragraphs of the same style.
                let next_same = matches!(blocks.get(i + 1), Some(Block::Para(n)) if n.style_id == p.style_id && n.contextual && p.contextual);
                let prev_same = prev.is_some_and(|q| q.style_id == p.style_id && q.contextual && p.contextual);
                if next_same || prev_same {
                    let mut q = p.clone();
                    if prev_same {
                        q.before = 0.0;
                    }
                    if next_same {
                        q.after = 0.0;
                    }
                    out.extend(layout_para(&q, width));
                } else {
                    out.extend(layout_para(p, width));
                }
                prev = Some(p);
            }
            Block::Table(t) => {
                out.extend(layout_table(t, width, depth));
                prev = None;
            }
            Block::PageBreak => {
                out.push(VItem::PageBreak);
                prev = None;
            }
        }
    }
    out
}

/// Flatten laid-out items into boxes (for table cells): gaps become empty boxes, nested rows
/// are drawn whole, and page breaks are ignored.
fn boxes(items: Vec<VItem>) -> Vec<VBox> {
    let mut out = Vec::new();
    for it in items {
        match it {
            VItem::Box(b) => out.push(b),
            VItem::Gap(h) => out.push(VBox { h, ops: Vec::new() }),
            VItem::PageBreak => {}
            VItem::Row(r) => {
                let h = r.height();
                let mut ops = Vec::new();
                r.draw(&mut ops, 0.0, 0.0, h);
                out.push(VBox { h, ops });
            }
        }
    }
    // Space after the last paragraph in a cell still counts in Word; trailing gaps stay.
    out
}

pub(crate) fn layout_table(t: &Table, width: f64, depth: usize) -> Vec<VItem> {
    if depth >= MAX_TABLE_DEPTH {
        // Too deeply nested: the cells' text, one paragraph after another.
        let mut out = Vec::new();
        for row in &t.rows {
            for cell in &row.cells {
                out.extend(layout_blocks(&cell.blocks, width, depth));
            }
        }
        return out;
    }
    let ncols = t.rows.iter().map(|r| r.cells.iter().map(|c| c.span.max(1)).sum::<usize>()).max().unwrap_or(0).max(t.cols.len());
    if ncols == 0 {
        return Vec::new();
    }
    let mut cols: Vec<f64> = (0..ncols).map(|i| t.cols.get(i).copied().filter(|w| w.is_finite() && *w > 0.0).unwrap_or(0.0)).collect();
    let known: f64 = cols.iter().sum();
    let unknown = cols.iter().filter(|w| **w <= 0.0).count();
    if unknown > 0 {
        let each = ((width - known).max(0.0) / unknown as f64).max(width / ncols as f64 * 0.25).max(12.0);
        for w in cols.iter_mut().filter(|w| **w <= 0.0) {
            *w = each;
        }
    }
    let total: f64 = cols.iter().sum();
    let avail = (width - t.indent.max(0.0)).max(width * 0.5);
    if total > avail && total > 0.0 {
        let k = avail / total;
        for w in &mut cols {
            *w *= k;
        }
    }
    let total: f64 = cols.iter().sum();
    let x0 = match t.align {
        Align::Center => ((width - total) / 2.0).max(0.0),
        Align::Right => (width - total).max(0.0),
        _ => t.indent.clamp(-width, width),
    };
    let mut starts = Vec::with_capacity(ncols + 1);
    let mut acc = 0.0;
    for w in &cols {
        starts.push(acc);
        acc += w;
    }
    starts.push(acc);
    let mut out = Vec::new();
    for (ri, row) in t.rows.iter().enumerate() {
        let mut cells = Vec::new();
        let mut g = 0usize;
        for cell in &row.cells {
            let span = cell.span.max(1);
            let (Some(&a), Some(&b)) = (starts.get(g.min(ncols)), starts.get((g + span).min(ncols))) else { break };
            let w = (b - a).max(1.0);
            let (ml, mt, mr, mb) = cell.margins;
            let inner = (w - ml - mr).max(4.0);
            let lines = if cell.merge_continue { Vec::new() } else { boxes(layout_blocks(&cell.blocks, inner, depth + 1)) };
            let lines = lines
                .into_iter()
                .map(|mut l| {
                    for op in &mut l.ops {
                        op.offset(ml, 0.0);
                    }
                    l
                })
                .collect();
            let mut borders = cell.borders;
            if cell.merge_continue {
                borders[0] = None;
            }
            // The cell below continues this one: no line between them.
            let below_continues = t.rows.get(ri + 1).is_some_and(|next| {
                let mut ng = 0usize;
                next.cells.iter().any(|c| {
                    let hit = ng == g && c.merge_continue;
                    ng += c.span.max(1);
                    hit
                })
            });
            if below_continues {
                borders[2] = None;
            }
            cells.push(CellLayout { x: x0 + a, w, fill: cell.fill, borders, lines, pad_top: mt, pad_bottom: mb, valign: cell.valign });
            g += span;
        }
        out.push(VItem::Row(RowLayout { cells, min_h: row.height.max(0.0), exact: row.exact }));
    }
    out
}

// ── pagination ──────────────────────────────────────────────────────────────────────────────

/// The page a flow is set on.
#[derive(Clone, Debug)]
pub(crate) struct PageSetup {
    pub w: f64,
    pub h: f64,
    pub top: f64,
    pub bottom: f64,
    pub left: f64,
    pub right: f64,
    /// Drawn at the top (header) and bottom (footer) of every page: boxes, and the distance
    /// from the page edge.
    pub header: Option<(Vec<VBox>, f64)>,
    pub footer: Option<(Vec<VBox>, f64)>,
    /// A different header and footer on the section's first page (`Some(None)`: none there).
    pub first_header: Option<Option<(Vec<VBox>, f64)>>,
    pub first_footer: Option<Option<(Vec<VBox>, f64)>>,
}

impl Default for PageSetup {
    fn default() -> Self {
        PageSetup {
            w: 612.0,
            h: 792.0,
            top: 72.0,
            bottom: 72.0,
            left: 72.0,
            right: 72.0,
            header: None,
            footer: None,
            first_header: None,
            first_footer: None,
        }
    }
}

impl PageSetup {
    pub(crate) fn content_width(&self) -> f64 {
        (self.w - self.left - self.right).max(36.0)
    }
}

/// Header and footer boxes laid out from blocks.
pub(crate) fn header_boxes(blocks: &[Block], width: f64) -> Vec<VBox> {
    boxes(layout_blocks(blocks, width, 0))
}

/// Pages of operations: (width, height, ops).
pub(crate) type Pages = Vec<(f64, f64, Vec<Op>)>;

/// Set `items` on pages of `setup`, appending them to `pages`. Returns early once `max_pages`
/// pages exist (the caller reports it).
pub(crate) fn paginate(items: Vec<VItem>, setup: &PageSetup, pages: &mut Pages, max_pages: usize) {
    let top = setup.top.clamp(0.0, setup.h * 0.45);
    let bottom = (setup.h - setup.bottom.clamp(0.0, setup.h * 0.45)).max(top + 36.0);
    let left = setup.left.clamp(0.0, setup.w * 0.45);
    let full = bottom - top;
    let mut ops: Vec<Op> = Vec::new();
    let mut y = top;
    let mut fresh = true;
    let start = pages.len();
    let new_page = |ops: &mut Vec<Op>, pages: &mut Pages| {
        let mut page_ops = std::mem::take(ops);
        decorate(&mut page_ops, setup, left, pages.len() == start);
        pages.push((setup.w, setup.h, page_ops));
    };
    for it in items {
        if pages.len() >= max_pages {
            return;
        }
        match it {
            VItem::Gap(g) => {
                if !fresh {
                    y += g.max(0.0);
                }
            }
            VItem::PageBreak => {
                new_page(&mut ops, pages);
                y = top;
                fresh = true;
            }
            VItem::Box(b) => {
                if y + b.h > bottom + 0.01 && !fresh {
                    new_page(&mut ops, pages);
                    y = top;
                }
                place(&mut ops, &b.ops, left, y);
                y += b.h;
                fresh = false;
            }
            VItem::Row(r) => {
                let h = r.height();
                if y + h <= bottom + 0.01 {
                    r.draw(&mut ops, left, y, h);
                    y += h;
                    fresh = false;
                    continue;
                }
                if !fresh && h <= full {
                    new_page(&mut ops, pages);
                    y = top;
                    r.draw(&mut ops, left, y, h);
                    y += h;
                    fresh = false;
                    continue;
                }
                // Taller than the space left on a fresh page: split it line by line.
                let mut next: Vec<usize> = vec![0; r.cells.len()];
                let mut guard = 0usize;
                loop {
                    guard += 1;
                    if guard > max_pages || pages.len() >= max_pages {
                        return;
                    }
                    let avail = bottom - y;
                    let mut seg_h: f64 = 0.0;
                    let mut taken: Vec<(usize, usize)> = Vec::with_capacity(r.cells.len());
                    for (ci, c) in r.cells.iter().enumerate() {
                        let start = next.get(ci).copied().unwrap_or(0);
                        let mut used = c.pad_top;
                        let mut end = start;
                        while let Some(l) = c.lines.get(end) {
                            if used + l.h + c.pad_bottom > avail && (end > start || !fresh) {
                                break;
                            }
                            used += l.h;
                            end += 1;
                        }
                        seg_h = seg_h.max(used + c.pad_bottom);
                        taken.push((start, end));
                    }
                    let done = r.cells.iter().zip(&taken).all(|(c, (_, end))| *end >= c.lines.len());
                    let seg_h = if done { seg_h.max((r.min_h - 0.0).min(avail)) } else { avail.max(seg_h) };
                    let progressed = taken.iter().any(|(s, e)| e > s);
                    if !progressed && !done && fresh {
                        // Nothing fits even on an empty page; give up on the rest of this row.
                        break;
                    }
                    for c in &r.cells {
                        if let Some(f) = c.fill {
                            ops.push(Op::Rect { x: left + c.x, y, w: c.w, h: seg_h, fill: Some(f), stroke: None });
                        }
                    }
                    for (c, (s, e)) in r.cells.iter().zip(&taken) {
                        let mut ly = y + c.pad_top;
                        for l in c.lines.get(*s..*e).unwrap_or_default() {
                            place(&mut ops, &l.ops, left + c.x, ly);
                            ly += l.h;
                        }
                    }
                    for c in &r.cells {
                        borders(&mut ops, left + c.x, y, c.w, seg_h, &c.borders);
                    }
                    for (slot, (_, e)) in next.iter_mut().zip(&taken) {
                        *slot = *e;
                    }
                    if done {
                        y += seg_h;
                        fresh = false;
                        break;
                    }
                    new_page(&mut ops, pages);
                    y = top;
                    fresh = true;
                }
            }
        }
    }
    if !ops.is_empty() || !fresh || pages.is_empty() {
        new_page(&mut ops, pages);
    }
}

fn decorate(ops: &mut Vec<Op>, setup: &PageSetup, left: f64, first: bool) {
    let header = match (&setup.first_header, first) {
        (Some(h), true) => h,
        _ => &setup.header,
    };
    let footer = match (&setup.first_footer, first) {
        (Some(f), true) => f,
        _ => &setup.footer,
    };
    if let Some((boxes, dist)) = header {
        let mut y = dist.clamp(0.0, setup.h / 2.0);
        for b in boxes {
            place(ops, &b.ops, left, y);
            y += b.h;
        }
    }
    if let Some((boxes, dist)) = footer {
        let total: f64 = boxes.iter().map(|b| b.h).sum();
        let mut y = setup.h - dist.clamp(0.0, setup.h / 2.0) - total;
        for b in boxes {
            place(ops, &b.ops, left, y);
            y += b.h;
        }
    }
}

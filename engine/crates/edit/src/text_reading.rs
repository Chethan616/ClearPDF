//! The text of a page as a reader sees it (Export to Word, Excel, PowerPoint, HTML and RTF).
//!
//! Editing works on runs in content order, but some producers draw one line as many runs: web
//! browsers print each glyph with its own positioning and switch between several subsets of a
//! font within a word, so consecutive runs rarely share a font. Reading therefore rebuilds
//! upright lines from the glyphs themselves: glyphs on one baseline are sorted left to right and
//! split only at wide gaps, whatever font, subset or colour drew them. Each line takes the style
//! most of its characters are set in. Other text (rotated or skewed) keeps the run-based lines.

use super::*;

/// The most glyphs one page is rebuilt from; runs past it keep their run-based lines.
const MAX_PAGE_GLYPHS: usize = 1_000_000;

/// A run of text and what reading needs to know about it beyond the run itself.
pub(super) struct Run {
    pub(super) shown: Shown,
    pub(super) stream: usize,
    /// Position in reading the page's content, across forms.
    pub(super) order: usize,
    pub(super) color: [f64; 3],
    /// Drawn with no visible fill: alpha 0, or rendering mode 3 or 7.
    pub(super) invisible: bool,
}

/// One glyph of an upright run, in user space.
struct Glyph {
    text: String,
    x0: f64,
    x1: f64,
    base: f64,
    size: f64,
    run: usize,
}

/// Where a form XObject paints a gradient (pattern or shading), and its average colour.
pub(super) type Gradient = ([f64; 4], [f64; 3]);

/// A run's colour as a reader sees it: a gradient pattern's average, else its fill.
pub(super) fn run_colour(doc: &Document, resources: &Dict, fill: &[Op]) -> [f64; 3] {
    pattern_color(doc, resources, fill).unwrap_or_else(|| fill_color(fill))
}

/// Whether a run paints nothing a reader can see: invisible or clip-only text (rendering modes
/// 3 and 7), or text filled or stroked at zero opacity.
pub(super) fn invisible(s: &Shown) -> bool {
    let gs = &s.state.gs;
    match s.state.render_mode {
        0 | 4 => gs.fill_alpha <= 0.0,
        1 | 5 => gs.stroke_alpha <= 0.0,
        2 | 6 => gs.fill_alpha <= 0.0 && gs.stroke_alpha <= 0.0,
        3 | 7 => true,
        _ => false,
    }
}

/// Whether `area` covers most of `rect`.
fn covers(area: [f64; 4], rect: [f64; 4]) -> bool {
    let w = area[2].min(rect[2]) - area[0].max(rect[0]);
    let h = area[3].min(rect[3]) - area[1].max(rect[1]);
    let own = (rect[2] - rect[0]) * (rect[3] - rect[1]);
    w > 0.0 && h > 0.0 && own > 0.0 && w * h >= 0.5 * own
}

/// Whether a run is upright, left-to-right text: its rendering matrix only scales (y may be
/// flipped by the text matrix and back by the CTM, as browsers print it).
fn upright(s: &Shown) -> bool {
    let m = s.tm.then(&s.state.ctm).0;
    m[0] > 0.0 && m[3] > 0.0 && m[1].abs() <= m[0] * 1e-3 && m[2].abs() <= m[3] * 1e-3
}

/// Lines from runs: upright runs rebuilt from their glyphs, the others joined as runs. Text drawn
/// invisibly over a gradient a form paints (how browsers print gradient-filled text: the visible
/// copy is a masked gradient) reads in the gradient's colour.
pub(super) fn lines_of(mut runs: Vec<Run>, gradients: &[Gradient]) -> Vec<TextLine> {
    for run in runs.iter_mut().filter(|r| r.invisible) {
        if let Some((_, c)) = gradients.iter().find(|(area, _)| covers(*area, run.shown.rect)) {
            run.color = *c;
        }
    }
    let mut glyphs: Vec<Glyph> = Vec::new();
    // Run-based lines, and the order key of the run that started each.
    let mut run_lines: Vec<TextLine> = Vec::new();
    let mut keys: Vec<usize> = Vec::new();
    let mut last = None;
    let mut last_stream = None;
    for (i, run) in runs.iter().enumerate() {
        let s = &run.shown;
        // Text from another stream or form starts a new line, as when each was read alone.
        if last_stream.replace(run.stream).is_some_and(|prev| prev != run.stream) {
            last = None;
        }
        if upright(s) && glyphs.len().saturating_add(s.glyphs.len()) <= MAX_PAGE_GLYPHS {
            for (text, x0, x1) in &s.glyphs {
                if x0.is_finite() && x1.is_finite() && s.baseline.is_finite() {
                    glyphs.push(Glyph { text: text.clone(), x0: x0.min(*x1), x1: x0.max(*x1), base: s.baseline, size: s.size.max(0.1), run: i });
                }
            }
            // A run-based line doesn't continue across upright text.
            last = None;
        } else {
            let before = run_lines.len();
            add_shown(&mut run_lines, &mut last, s.clone(), run.stream);
            if run_lines.len() > before {
                keys.push(run.order);
                if let Some(l) = run_lines.last_mut() {
                    l.color = run.color;
                }
            }
        }
    }
    let mut keyed: Vec<(usize, TextLine)> = keys.into_iter().zip(run_lines).collect();
    keyed.extend(glyph_lines(&runs, glyphs));
    // Content order, as the paragraph grouping expects.
    keyed.sort_by_key(|(k, _)| *k);
    let mut lines: Vec<TextLine> = keyed.into_iter().map(|(_, l)| l).collect();
    lines.retain(|l| !l.text.trim().is_empty());
    lines
}

/// A font name without its subset tag ("ABCDEF+Inter-Bold" → "Inter-Bold").
fn face_name(base_font: &str) -> &str {
    match base_font.split_once('+') {
        Some((tag, rest)) if tag.len() == 6 && tag.bytes().all(|b| b.is_ascii_uppercase()) => rest,
        _ => base_font,
    }
}

/// Lines rebuilt from upright glyphs, each with the order key of its first run.
fn glyph_lines(runs: &[Run], mut glyphs: Vec<Glyph>) -> Vec<(usize, TextLine)> {
    // Top to bottom by baseline, then left to right.
    glyphs.sort_by(|a, b| b.base.total_cmp(&a.base).then(a.x0.total_cmp(&b.x0)));
    let mut rows: Vec<Vec<Glyph>> = Vec::new();
    for g in glyphs {
        let joins = rows.last().and_then(|r| r.first()).is_some_and(|first| (first.base - g.base).abs() <= 0.3 * first.size.min(g.size));
        match rows.last_mut() {
            Some(row) if joins => row.push(g),
            _ => rows.push(vec![g]),
        }
    }
    let mut out = Vec::new();
    for mut row in rows {
        row.sort_by(|a, b| a.x0.total_cmp(&b.x0));
        let row = dedupe(runs, row);
        let mut segment: Vec<Glyph> = Vec::new();
        for g in row {
            let split = segment.iter().rev().find(|p| !p.text.trim().is_empty()).or(segment.last()).is_some_and(|p| {
                let (small, big) = (p.size.min(g.size), p.size.max(g.size));
                let scope = |i: usize| runs.get(i).and_then(|r| r.shown.scope);
                g.x0 - p.x1 > 1.5 * big || big > 1.6 * small || scope(p.run) != scope(g.run)
            });
            if split {
                out.extend(segment_line(runs, std::mem::take(&mut segment)));
            }
            segment.push(g);
        }
        out.extend(segment_line(runs, segment));
    }
    out
}

/// Drop glyphs drawn twice in the same place (a shadow, a fake bold, an invisible copy over a
/// visible one), keeping a visible copy.
fn dedupe(runs: &[Run], row: Vec<Glyph>) -> Vec<Glyph> {
    let invisible = |g: &Glyph| runs.get(g.run).is_some_and(|r| r.invisible);
    let mut out: Vec<Glyph> = Vec::with_capacity(row.len());
    for g in row {
        // Sorted by x, so a twin is among the last few glyphs kept.
        let near = out.len().saturating_sub(8);
        let twin =
            out.iter().enumerate().skip(near).rev().find(|(_, p)| p.text == g.text && (p.x0 - g.x0).abs() < 0.2 * p.size.min(g.size)).map(|(i, _)| i);
        match twin {
            Some(i) => {
                if let Some(kept) = out.get_mut(i)
                    && invisible(kept)
                    && !invisible(&g)
                {
                    *kept = g;
                }
            }
            None => out.push(g),
        }
    }
    out
}

/// One line from a left-to-right run of glyphs on one baseline.
fn segment_line(runs: &[Run], glyphs: Vec<Glyph>) -> Option<(usize, TextLine)> {
    let first = glyphs.first()?;
    // Gaps between letters: letter-spaced text has wide ones everywhere, so a word break is a
    // gap clearly wider than the usual one.
    let mut gaps: Vec<f64> = glyphs.windows(2).filter_map(|w| if let [a, b] = w { Some(b.x0 - a.x1) } else { None }).collect();
    gaps.sort_by(|a, b| a.total_cmp(b));
    let usual = gaps.get(gaps.len() / 2).copied().unwrap_or(0.0).max(0.0);
    let mut text = String::new();
    let mut pending_space = false;
    let mut prev: Option<&Glyph> = None;
    for g in &glyphs {
        if g.text.trim().is_empty() {
            pending_space = true;
            prev = Some(g);
            continue;
        }
        if let Some(p) = prev
            && (pending_space || g.x0 - p.x1 > usual + 0.2 * p.size.min(g.size))
            && !text.is_empty()
            && !text.ends_with(' ')
        {
            text.push(' ');
        }
        pending_space = false;
        text.push_str(&g.text);
        prev = Some(g);
    }
    let text = crate::bidi::to_logical(text.trim());
    if text.is_empty() {
        return None;
    }
    // The style most of the characters are set in.
    let mut weight: Vec<(usize, usize)> = Vec::new();
    for g in glyphs.iter().filter(|g| !g.text.trim().is_empty()) {
        let n = g.text.chars().count();
        match weight.iter_mut().find(|e| e.0 == g.run) {
            Some(e) => e.1 += n,
            None => weight.push((g.run, n)),
        }
    }
    // Style by run: runs of the same face, size and colour pool their characters.
    let key =
        |r: &Run| (face_name(&r.shown.base_font).to_owned(), (r.shown.size * 100.0).round() as i64, rgb_key(r.color), r.shown.bold, r.shown.italic);
    // (style, characters, a run in that style)
    let mut pooled: Vec<(StyleKey, usize, usize)> = Vec::new();
    for (run, n) in &weight {
        let Some(r) = runs.get(*run) else { continue };
        let k = key(r);
        match pooled.iter_mut().find(|e| e.0 == k) {
            Some(e) => e.1 += n,
            None => pooled.push((k, *n, *run)),
        }
    }
    // Ties go to the style the line starts in.
    let most = pooled.iter().map(|e| e.1).max().unwrap_or(0);
    let style_run = pooled.iter().find(|e| e.1 == most).map_or(first.run, |e| e.2);
    let style = runs.get(style_run)?;
    let origin_run = runs.get(first.run)?;
    let mut members: Vec<usize> = glyphs.iter().map(|g| g.run).collect();
    members.sort_unstable();
    members.dedup();
    let (mut y0, mut y1) = (f64::MAX, f64::MIN);
    for r in members.iter().filter_map(|i| runs.get(*i)) {
        y0 = y0.min(r.shown.rect[1]);
        y1 = y1.max(r.shown.rect[3]);
    }
    let x0 = glyphs.iter().map(|g| g.x0).fold(f64::MAX, f64::min);
    let x1 = glyphs.iter().map(|g| g.x1).fold(f64::MIN, f64::max);
    let mut ops: Vec<usize> = Vec::new();
    for r in members.iter().filter_map(|i| runs.get(*i)) {
        ops.push(r.shown.op);
        ops.extend(r.shown.extra_ops.iter().copied());
    }
    ops.sort_unstable();
    ops.dedup();
    let order = members.iter().filter_map(|i| runs.get(*i)).map(|r| r.order).min().unwrap_or(0);
    let stream = members.iter().filter_map(|i| runs.get(*i)).map(|r| r.stream).min().unwrap_or(0);
    let face = face_name(&style.shown.base_font).to_owned();
    let mut base = Vec::new();
    add_shown(&mut base, &mut None, origin_run.shown.clone(), stream);
    let mut line = base.pop()?;
    let rect = [x0, y0, x1, y1];
    line.text = text;
    line.rect = rect;
    line.frame = rect;
    line.ink = vec![rect];
    // Subsets of one face are one font to a reader.
    line.font = face.clone();
    line.base_font = face;
    line.size = style.shown.size;
    line.bold = style.shown.bold;
    line.italic = style.shown.italic;
    line.color = style.color;
    line.decodable = members.iter().filter_map(|i| runs.get(*i)).all(|r| r.shown.decodable);
    line.face = style.shown.face.clone();
    line.stream = stream;
    line.ops = ops;
    line.origin.baseline = first.base;
    line.origin.x = x0;
    Some((order, line))
}

/// Face, size (hundredths of a point), colour (8-bit), bold, italic.
type StyleKey = (String, i64, [i64; 3], bool, bool);

fn rgb_key(c: [f64; 3]) -> [i64; 3] {
    c.map(|v| if v.is_finite() { (v.clamp(0.0, 1.0) * 255.0).round() as i64 } else { 0 })
}

/// The colour of a pattern fill (`/Pattern cs /P1 scn`): the average of a shading pattern's
/// colour stops. `None` for other fills, tiling patterns and shadings without stops.
fn pattern_color(doc: &Document, resources: &Dict, fill: &[Op]) -> Option<[f64; 3]> {
    let op = fill.iter().rev().find(|op| op.is("scn"))?;
    let name = match op.operands.last()? {
        Object::Name(n) => n.clone(),
        _ => return None,
    };
    let patterns = resources.get(b"Pattern").and_then(|p| doc.dict(p))?;
    let pattern = patterns.get(&name).and_then(|p| pattern_dict(doc, p))?;
    if pattern.get(b"PatternType").and_then(|t| doc.resolve(t).as_int()) != Some(2) {
        return None;
    }
    shading_color(doc, pattern.get(b"Shading")?)
}

/// A pattern's dictionary (patterns may be streams).
fn pattern_dict(doc: &Document, o: &Object) -> Option<Dict> {
    match &*doc.resolve(o) {
        Object::Dict(d) => Some(d.clone()),
        Object::Stream(s) => Some(s.dict.clone()),
        _ => None,
    }
}

/// The average of a shading's colour stops, as RGB.
fn shading_color(doc: &Document, shading: &Object) -> Option<[f64; 3]> {
    let shading = pattern_dict(doc, shading)?;
    let components = match shading.get(b"ColorSpace").map(|c| doc.resolve(c)) {
        Some(cs) => match &*cs {
            Object::Name(n) => match n.as_slice() {
                b"DeviceGray" | b"G" | b"CalGray" => 1,
                b"DeviceCMYK" | b"CMYK" => 4,
                _ => 3,
            },
            Object::Array(a) => match a.first() {
                Some(Object::Name(n)) if n.as_slice() == b"ICCBased" => a
                    .get(1)
                    .and_then(|s| match &*doc.resolve(s) {
                        Object::Stream(s) => s.dict.get(b"N").and_then(|n| doc.resolve(n).as_int()),
                        _ => None,
                    })
                    .map_or(3, |n| n.clamp(1, 4) as usize),
                Some(Object::Name(n)) if n.as_slice() == b"CalGray" => 1,
                _ => 3,
            },
            _ => 3,
        },
        None => 3,
    };
    let mut stops: Vec<Vec<f64>> = Vec::new();
    collect_stops(doc, shading.get(b"Function")?, &mut stops, 0);
    let stops: Vec<Vec<f64>> = stops.into_iter().filter(|s| s.len() == components && s.iter().all(|v| v.is_finite())).collect();
    if stops.is_empty() {
        return None;
    }
    let n = stops.len() as f64;
    let mean: Vec<f64> = (0..components).map(|i| stops.iter().filter_map(|s| s.get(i)).sum::<f64>() / n).collect();
    let v = |i: usize| mean.get(i).copied().unwrap_or(0.0).clamp(0.0, 1.0);
    Some(match components {
        1 => [v(0), v(0), v(0)],
        4 => [(1.0 - v(0)) * (1.0 - v(3)), (1.0 - v(1)) * (1.0 - v(3)), (1.0 - v(2)) * (1.0 - v(3))],
        _ => [v(0), v(1), v(2)],
    })
}

/// The colour stops of an exponential function (C0, C1) or of a stitching function's parts.
fn collect_stops(doc: &Document, f: &Object, out: &mut Vec<Vec<f64>>, depth: usize) {
    if depth > 4 || out.len() > 64 {
        return;
    }
    let Some(d) = pattern_dict(doc, f) else { return };
    let nums = |key: &[u8], default: f64| -> Vec<f64> {
        match d.get(key).map(|o| doc.resolve(o)) {
            Some(o) => o.as_array().map(|a| a.iter().filter_map(|x| doc.resolve(x).as_f64()).collect()).unwrap_or_default(),
            None => vec![default],
        }
    };
    match d.get(b"FunctionType").and_then(|t| doc.resolve(t).as_int()) {
        Some(2) => {
            out.push(nums(b"C0", 0.0));
            out.push(nums(b"C1", 1.0));
        }
        Some(3) => {
            if let Some(parts) = d.get(b"Functions").map(|o| doc.resolve(o)).and_then(|o| o.as_array().cloned()) {
                for part in parts.iter().take(16) {
                    collect_stops(doc, part, out, depth + 1);
                }
            }
        }
        _ => {}
    }
}

/// Where a form paints a gradient, from the patterns it fills with and the shadings it draws.
pub(super) fn form_paint(doc: &Document, form: &FormCall, ops: &[Op]) -> Option<Gradient> {
    let shadings = form.resources.get(b"Shading").and_then(|s| doc.dict(s));
    let mut colors = Vec::new();
    for (i, op) in ops.iter().enumerate().take(100_000) {
        if op.is("scn") {
            let fill: Vec<Op> = ops.get(i.saturating_sub(1)..=i).map(|s| s.to_vec()).unwrap_or_default();
            if let Some(c) = pattern_color(doc, &form.resources, &fill) {
                colors.push(c);
            }
        } else if op.is("sh")
            && let Some(c) = op.name(0).and_then(|n| shadings.as_ref()?.get(n).cloned()).and_then(|s| shading_color(doc, &s))
        {
            colors.push(c);
        }
        if colors.len() >= 8 {
            break;
        }
    }
    let first = *colors.first()?;
    let bbox = form.obj.and_then(|r| match &*doc.get(r) {
        Object::Stream(s) => s.dict.get(b"BBox").map(|b| doc.resolve(b)).and_then(|b| {
            let v: Vec<f64> = b.as_array()?.iter().filter_map(|x| doc.resolve(x).as_f64()).collect();
            <[f64; 4]>::try_from(v).ok().filter(|v| v.iter().all(|x| x.is_finite()))
        }),
        _ => None,
    })?;
    let corners =
        [form.ctm.apply(bbox[0], bbox[1]), form.ctm.apply(bbox[2], bbox[1]), form.ctm.apply(bbox[0], bbox[3]), form.ctm.apply(bbox[2], bbox[3])];
    let area = corners.iter().fold([f64::MAX, f64::MAX, f64::MIN, f64::MIN], |b, p| [b[0].min(p.0), b[1].min(p.1), b[2].max(p.0), b[3].max(p.1)]);
    area.iter().all(|v| v.is_finite()).then_some((area, first))
}

/// Remove every glyph a document draws in page content and form XObjects (soft-mask groups
/// included), keeping all other painting and every graphics-state change, so the pages render as
/// their artwork alone (a background behind exported text). Annotation appearances keep their
/// text: export doesn't read it. Work on a copy: this is not an edit to keep.
pub fn remove_all_text(doc: &mut Document) {
    let shows = |op: &Op| matches!(op.op.as_slice(), b"Tj" | b"TJ" | b"'" | b"\"");
    // Text that clips (rendering modes 4–7) shapes the artwork drawn through it: keep it.
    let strip = |data: &[u8]| -> Option<Vec<u8>> {
        let ops = parse(data).ops;
        let mut mode = 0i64;
        let mut saved: Vec<i64> = Vec::new();
        let mut keep = Vec::with_capacity(ops.len());
        let mut changed = false;
        for op in ops {
            match op.op.as_slice() {
                b"q" if saved.len() < 4096 => saved.push(mode),
                b"Q" => mode = saved.pop().unwrap_or(mode),
                b"Tr" => mode = op.operands.first().and_then(Object::as_int).unwrap_or(0),
                _ => {}
            }
            if shows(&op) && !(4..=7).contains(&mode) {
                changed = true;
                continue;
            }
            keep.push(op);
        }
        changed.then(|| serialize_ops(&keep))
    };
    // Appearance streams of annotations, which keep their text.
    let mut appearances = std::collections::HashSet::new();
    for page in pdfcraft_model::pages(doc) {
        let annots = page.dict.get(b"Annots").map(|a| doc.resolve(a)).and_then(|a| a.as_array().cloned()).unwrap_or_default();
        for a in annots.iter().take(10_000) {
            let Some(ap) = doc.dict(a).and_then(|a| a.get(b"AP").and_then(|ap| doc.dict(ap))) else { continue };
            for (_, entry) in ap.iter() {
                if let Some(r) = entry.as_ref() {
                    appearances.insert(r.num);
                }
                if let Some(states) = doc.dict(entry) {
                    appearances.extend(states.iter().filter_map(|(_, s)| s.as_ref()).map(|r| r.num));
                }
            }
        }
    }
    for num in doc.object_numbers() {
        if appearances.contains(&num) {
            continue;
        }
        let Ok(obj) = doc.try_get(num) else { continue };
        let Object::Stream(s) = &*obj else { continue };
        if s.dict.name(b"Subtype") != Some(b"Form") {
            continue;
        }
        let Some(data) = s.decoded().ok().and_then(|d| strip(&d)) else { continue };
        let mut dict = s.dict.clone();
        dict.remove(b"Length");
        let r = pdfcraft_cos::ObjRef::new(num, doc.generation(num));
        doc.set(r, Stream::flate(dict, &data));
    }
    for page in pdfcraft_model::pages(doc) {
        let Ok(streams) = content_streams(doc, &page.dict) else { continue };
        let joined: Vec<u8> = streams.iter().flat_map(|(_, d)| d.iter().copied().chain(std::iter::once(b'\n'))).collect();
        let Some(data) = strip(&joined) else { continue };
        let contents = doc.add(Stream::flate(Dict::new(), &data));
        let _ = doc.update_dict(page.obj, |d| d.set(b"Contents".to_vec(), Object::Ref(contents)));
    }
}

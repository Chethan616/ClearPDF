//! Edit text that a page draws inside form XObjects.
//!
//! Chrome (Skia) and other producers put a whole page in one form XObject: the page's own content
//! is little more than `/X0 Do`. Edit text can only rewrite the page's own content streams, so
//! before this module such a page had no editable paragraphs. Here a page's text-bearing forms
//! are *inlined*: each `Do` of a form that draws text becomes
//! `q <form /Matrix> cm <BBox> re W n <form content> Q`, which is what drawing the form does
//! (ISO 32000-2 §8.10.1), with the form's resource names renamed where they collide with the
//! page's.
//!
//! [`plan`] is pure and deterministic. Edit text reads every page through [`view`] (the plan
//! applied to a copy), so the paragraph indexes it lists already include the forms' text; every
//! rewrite applies the same plan to the document first ([`edit_inlined`]), so those indexes
//! stay valid. A page without text-bearing forms has no plan and is never touched. Applying a
//! plan changes no pixels: forms whose meaning inlining would change are left alone (knockout
//! groups, groups composited with transparency, optional content, …), patterns used inside a
//! form are copied with their matrix moved into the page's space (§8.7.3.1), and the form
//! object itself stays in the file (other pages may draw it).

use std::borrow::Cow;
use std::collections::{HashMap, HashSet};
use std::ops::Range;

use pdfcraft_content::{Matrix, Op, Pieces, parse, write_op};
use pdfcraft_cos::{Dict, Document, ObjRef, Object, Stream};

use crate::EditError;

/// Resource categories content operators name (§7.8.3). Index 4 is `Pattern`, whose entries are
/// copied per form space instead of merged.
const CATEGORIES: [&[u8]; 7] = [b"Font", b"XObject", b"ExtGState", b"ColorSpace", b"Pattern", b"Shading", b"Properties"];
const FONT: usize = 0;
const XOBJECT: usize = 1;
const EXTGSTATE: usize = 2;
const COLORSPACE: usize = 3;
const PATTERN: usize = 4;
const SHADING: usize = 5;
const PROPERTIES: usize = 6;

/// How deep text-bearing forms are inlined inside each other.
const MAX_DEPTH: usize = 8;
/// The most decoded bytes one form may have to be inlined.
const MAX_FORM_BYTES: usize = 16 << 20;
/// The most bytes a page's inlined content may add; past it nothing on the page is inlined.
const MAX_TOTAL_BYTES: usize = 48 << 20;
/// The most resource entries one form may bring.
const MAX_ENTRIES: usize = 4096;
/// The most Type 3 glyph procedures a plan reads to check they borrow no resources.
const MAX_GLYPHS: usize = 20_000;

/// What applying a plan writes.
pub(crate) struct Plan {
    page: ObjRef,
    /// The page's content streams as they are, and each one's new bytes (unchanged ones equal).
    streams: Vec<(Object, Vec<u8>)>,
    contents: Vec<Vec<u8>>,
    /// The page's resources with the forms' merged in (patterns still to add).
    resources: Dict,
    /// Pattern copies, by resource name, to add as new objects.
    patterns: Vec<(Vec<u8>, Object)>,
    /// `/StructParents` the page takes over from an inlined form, whose marked content it now holds.
    struct_parents: Option<i64>,
    /// The forms whose content now lies in the page's.
    forms: Vec<ObjRef>,
}

/// Why a form wasn't inlined. `Kept`: a property of the form and the state it is drawn in (the
/// same on every plan), so it simply stays a `Do`. `Abort`: a limit was reached (depth, a cycle)
/// or a form inside it had to be inlined and couldn't, so the form it is in isn't inlined either.
#[derive(Debug)]
enum Fail {
    Kept,
    Abort,
}

/// The graphics state the planner tracks: what decides whether a group may be inlined.
#[derive(Clone)]
struct State {
    ctm: Matrix,
    fill_alpha: f64,
    stroke_alpha: f64,
    normal_blend: bool,
    soft_mask: bool,
}

impl State {
    fn new() -> Self {
        State { ctm: Matrix::IDENTITY, fill_alpha: 1.0, stroke_alpha: 1.0, normal_blend: true, soft_mask: false }
    }

    fn opaque(&self) -> bool {
        self.fill_alpha == 1.0 && self.stroke_alpha == 1.0 && self.normal_blend && !self.soft_mask
    }

    fn set(&mut self, doc: &Document, gs: &Dict) {
        if let Some(v) = gs.get(b"ca").and_then(|v| doc.resolve(v).as_f64()) {
            self.fill_alpha = v;
        }
        if let Some(v) = gs.get(b"CA").and_then(|v| doc.resolve(v).as_f64()) {
            self.stroke_alpha = v;
        }
        if let Some(bm) = gs.get(b"BM") {
            self.normal_blend = normal_blend(doc, bm);
        }
        if let Some(mask) = gs.get(b"SMask") {
            self.soft_mask = doc.resolve(mask).as_name() != Some(b"None");
        }
    }
}

fn normal_blend(doc: &Document, bm: &Object) -> bool {
    let bm = doc.resolve(bm);
    let name = match &*bm {
        Object::Array(a) => a.first().and_then(Object::as_name).map(<[u8]>::to_vec),
        other => other.as_name().map(<[u8]>::to_vec),
    };
    matches!(name.as_deref(), Some(b"Normal" | b"Compatible"))
}

/// (category, name in a form's resources) → its name in the merged dictionary, where it changed.
type Renames = HashMap<(usize, Vec<u8>), Vec<u8>>;

/// The resources a content stream's names refer to, and where they went in the merged dictionary.
#[derive(Clone)]
struct Scope {
    res: Dict,
    /// `res`'s category dictionaries, resolved (indexes as [`CATEGORIES`]).
    dicts: Vec<Dict>,
    renames: Renames,
    /// The content's coordinate space in the page's default space (pattern space, §8.7.2).
    space: Matrix,
    /// The page's own content: its names are never rewritten.
    page: bool,
}

/// The planner's state that a failed attempt to inline a form must undo.
#[derive(Clone)]
struct Snapshot {
    cats: Vec<Dict>,
    changed: [bool; 7],
    taken: HashSet<(usize, Vec<u8>)>,
    patterns: usize,
    pattern_names: HashMap<(Vec<u8>, [u64; 6]), Vec<u8>>,
    missing: HashMap<(usize, Vec<u8>), Vec<u8>>,
    adopted: Option<i64>,
    forms: usize,
    bytes: usize,
}

struct Planner<'a> {
    doc: &'a Document,
    /// The merged dictionary of each category (the page's, resolved, plus what forms brought).
    cats: Vec<Dict>,
    changed: [bool; 7],
    /// Names in use or referred to (by the page or an inlined form), which a new name must avoid.
    taken: HashSet<(usize, Vec<u8>)>,
    patterns: Vec<(Vec<u8>, Object)>,
    /// (pattern identity, form space) → the copy's name.
    pattern_names: HashMap<(Vec<u8>, [u64; 6]), Vec<u8>>,
    /// Names a form uses that its resources lack → a name that stays missing.
    missing: HashMap<(usize, Vec<u8>), Vec<u8>>,
    page_struct_parents: Option<i64>,
    adopted: Option<i64>,
    forms: Vec<ObjRef>,
    /// Decoded form bytes read and output bytes written.
    bytes: usize,
    /// Forms visited looking for text (see [`crate::text::MAX_FORM_VISITS`]).
    visits: usize,
    /// Type 3 glyph procedures read ([`MAX_GLYPHS`]).
    glyphs: usize,
    /// Whether anything has been painted on the page so far, in drawing order (inlined forms
    /// included): until then the page is the transparent backdrop an isolated group starts with.
    painted: bool,
    /// A budget ran out: plan nothing for the page, so the outcome never depends on where it stopped.
    exhausted: bool,
    text_memo: HashMap<ObjRef, bool>,
}

fn resolved_dict(doc: &Document, o: Option<&Object>) -> Option<Dict> {
    o.and_then(|o| doc.resolve(o).as_dict().cloned())
}

fn is_text_op(op: &Op) -> bool {
    matches!(op.op.as_slice(), b"Tj" | b"TJ" | b"'" | b"\"")
}

/// The resource names an operator refers to: (operand index, category). Inline images are
/// handled separately ([`Planner::rename_inline_image`]).
fn name_refs(op: &Op) -> Vec<(usize, usize)> {
    let named = |i: usize| op.operands.get(i).and_then(Object::as_name).is_some();
    match op.op.as_slice() {
        b"Tf" if named(0) => vec![(0, FONT)],
        b"Do" if named(0) => vec![(0, XOBJECT)],
        b"gs" if named(0) => vec![(0, EXTGSTATE)],
        b"cs" | b"CS" if op.name(0).is_some_and(|n| !is_colour_family(n)) => vec![(0, COLORSPACE)],
        b"sh" if named(0) => vec![(0, SHADING)],
        b"scn" | b"SCN" => match op.operands.len().checked_sub(1) {
            Some(last) if named(last) => vec![(last, PATTERN)],
            _ => Vec::new(),
        },
        b"BDC" | b"DP" if named(1) => vec![(1, PROPERTIES)],
        _ => Vec::new(),
    }
}

/// Colour space family names that are never resources (§8.6.3).
fn is_colour_family(name: &[u8]) -> bool {
    matches!(name, b"DeviceGray" | b"DeviceRGB" | b"DeviceCMYK" | b"Pattern")
}

/// Inline-image colour space names that are never resources (§8.9.7, Table 91 abbreviations).
fn is_inline_family(name: &[u8]) -> bool {
    matches!(name, b"G" | b"RGB" | b"CMYK" | b"I" | b"Indexed" | b"DeviceGray" | b"DeviceRGB" | b"DeviceCMYK" | b"Pattern")
}

fn matrix_of(doc: &Document, o: Option<&Object>) -> Option<Matrix> {
    let Some(o) = o else { return Some(Matrix::IDENTITY) };
    let a = doc.resolve(o);
    let v: Vec<f64> = a.as_array()?.iter().map(|x| doc.resolve(x).as_f64()).collect::<Option<_>>()?;
    <[f64; 6]>::try_from(v).ok().filter(|v| v.iter().all(|x| x.is_finite() && x.abs() < 1e12)).map(Matrix)
}

fn bbox_of(doc: &Document, o: Option<&Object>) -> Option<[f64; 4]> {
    let a = doc.resolve(o?);
    let v: Vec<f64> = a.as_array()?.iter().map(|x| doc.resolve(x).as_f64()).collect::<Option<_>>()?;
    let [x0, y0, x1, y1] = <[f64; 4]>::try_from(v).ok()?;
    let b = [x0.min(x1), y0.min(y1), x0.max(x1), y0.max(y1)];
    b.iter().all(|x| x.is_finite() && x.abs() < 1e12).then_some(b)
}

/// The resolved form XObject `name` in `res`: its object (when indirect) and stream.
fn form_named(doc: &Document, res: &Dict, name: &[u8]) -> Option<(Option<ObjRef>, Stream)> {
    let xobjects = resolved_dict(doc, res.get(b"XObject"))?;
    let o = xobjects.get(name)?;
    let Object::Stream(s) = &*doc.resolve(o) else { return None };
    (s.dict.name(b"Subtype") == Some(b"Form")).then(|| (o.as_ref(), s.clone()))
}

/// The identity of a resource value: its object number, or its serialized bytes when direct.
fn identity(o: &Object) -> Vec<u8> {
    match o {
        Object::Ref(r) => format!("R{} {}", r.num, r.generation).into_bytes(),
        other => {
            let mut out = b"D".to_vec();
            pdfcraft_cos::serialize(other, &mut out);
            out
        }
    }
}

/// q/Q, BT/ET and marked content stay balanced inside the form (a stray `Q` there would pop the
/// page's state once inlined). Returns how many `q` are left open, which inlining closes.
fn balance(ops: &[Op]) -> Option<usize> {
    let (mut q, mut marked, mut text) = (0usize, 0usize, false);
    for op in ops {
        match op.op.as_slice() {
            b"q" => q = q.saturating_add(1),
            b"Q" => q = q.checked_sub(1)?,
            b"BMC" | b"BDC" => marked = marked.saturating_add(1),
            b"EMC" => marked = marked.checked_sub(1)?,
            b"BT" if !text => text = true,
            b"ET" if text => text = false,
            b"BT" | b"ET" => return None,
            _ => {}
        }
    }
    (marked == 0 && !text).then_some(q)
}

impl<'a> Planner<'a> {
    fn snapshot(&self) -> Snapshot {
        Snapshot {
            cats: self.cats.clone(),
            changed: self.changed,
            taken: self.taken.clone(),
            patterns: self.patterns.len(),
            pattern_names: self.pattern_names.clone(),
            missing: self.missing.clone(),
            adopted: self.adopted,
            forms: self.forms.len(),
            bytes: self.bytes,
        }
    }

    fn restore(&mut self, s: Snapshot) {
        self.cats = s.cats;
        self.changed = s.changed;
        self.taken = s.taken;
        self.patterns.truncate(s.patterns);
        self.pattern_names = s.pattern_names;
        self.missing = s.missing;
        self.adopted = s.adopted;
        self.forms.truncate(s.forms);
        self.bytes = s.bytes;
    }

    fn cat(&self, c: usize) -> Option<&Dict> {
        self.cats.get(c)
    }

    /// A name for category `c` that is neither in the merged dictionary nor referred to: `base`,
    /// `tag` and a number.
    fn fresh(&mut self, c: usize, base: &[u8], tag: &str) -> Vec<u8> {
        let mut k = 1usize;
        loop {
            let mut name = base.to_vec();
            name.extend_from_slice(format!("{tag}{k}").as_bytes());
            if !self.cat(c).is_some_and(|d| d.contains(&name)) && !self.taken.contains(&(c, name.clone())) {
                self.taken.insert((c, name.clone()));
                return name;
            }
            k = k.saturating_add(1);
        }
    }

    /// Whether `stream` (drawn with `res` when it has no resources of its own) shows text,
    /// directly or in a form it draws.
    fn draws_text(&mut self, r: Option<ObjRef>, stream: &Stream, res: &Dict, path: &mut Vec<ObjRef>) -> bool {
        let own = resolved_dict(self.doc, stream.dict.get(b"Resources"));
        if let (Some(r), true) = (r, own.is_some())
            && let Some(known) = self.text_memo.get(&r)
        {
            return *known;
        }
        if path.len() >= crate::text::MAX_FORM_DEPTH || r.is_some_and(|r| path.contains(&r)) {
            return false;
        }
        if self.visits >= crate::text::MAX_FORM_VISITS {
            self.exhausted = true;
            return false;
        }
        self.visits = self.visits.saturating_add(1);
        let Ok(data) = stream.decoded_within(MAX_FORM_BYTES) else { return false };
        let ops = parse(&data).ops;
        let res = own.as_ref().unwrap_or(res);
        let mut found = ops.iter().any(is_text_op);
        if !found {
            if let Some(r) = r {
                path.push(r);
            }
            for op in ops.iter().filter(|op| op.is("Do")) {
                let Some((nested, s)) = op.name(0).and_then(|n| form_named(self.doc, res, n)) else { continue };
                if self.draws_text(nested, &s, res, path) {
                    found = true;
                    break;
                }
            }
            if r.is_some() {
                path.pop();
            }
        }
        if let (Some(r), true) = (r, own.is_some()) {
            self.text_memo.insert(r, found);
        }
        found
    }

    /// Whether anything in `res` (or a form it draws) blends other than normally.
    fn blends(&mut self, res: &Dict, depth: usize) -> bool {
        if depth > 4 || self.visits >= crate::text::MAX_FORM_VISITS {
            self.exhausted |= self.visits >= crate::text::MAX_FORM_VISITS;
            return true;
        }
        self.visits = self.visits.saturating_add(1);
        let states = resolved_dict(self.doc, res.get(b"ExtGState")).unwrap_or_default();
        if states
            .iter()
            .take(MAX_ENTRIES)
            .any(|(_, gs)| resolved_dict(self.doc, Some(gs)).and_then(|d| d.get(b"BM").cloned()).is_some_and(|bm| !normal_blend(self.doc, &bm)))
        {
            return true;
        }
        let forms: Vec<Dict> = resolved_dict(self.doc, res.get(b"XObject"))
            .unwrap_or_default()
            .iter()
            .take(MAX_ENTRIES)
            .filter_map(|(_, x)| match &*self.doc.resolve(x) {
                Object::Stream(s) if s.dict.name(b"Subtype") == Some(b"Form") => resolved_dict(self.doc, s.dict.get(b"Resources")),
                _ => None,
            })
            .collect();
        forms.iter().any(|r| self.blends(r, depth + 1))
    }

    /// Whether drawing a transparency group form inline looks the same as compositing the group
    /// (§11.4.7): never for a knockout group, or one composited with transparency, a soft mask or a
    /// blend mode. An isolated group differs only where its content blends with what is behind
    /// it, which can't happen when nothing in it blends other than normally, when nothing has been
    /// painted on the page yet (the page group starts as transparent as the form group does,
    /// §11.4.7), or when it starts by covering its whole box with opaque paint (Chrome's page
    /// background).
    fn group_inlines(&mut self, form: &Dict, bbox: [f64; 4], ops: &[Op], res: &Dict, state: &State) -> bool {
        let Some(group) = resolved_dict(self.doc, form.get(b"Group")) else { return true };
        if group.name(b"S") != Some(b"Transparency") {
            return true;
        }
        let flag = |k: &[u8]| group.get(k).is_some_and(|v| matches!(&*self.doc.resolve(v), Object::Bool(true)));
        if flag(b"K") || !state.opaque() {
            return false;
        }
        !flag(b"I") || !self.painted || self.covers(bbox, ops, res) || !self.blends(res, 0)
    }

    /// Whether `ops` begin by filling a rectangle that covers `bbox` with opaque paint, before
    /// anything else is painted or clipped.
    fn covers(&self, bbox: [f64; 4], ops: &[Op], res: &Dict) -> bool {
        let mut st = State::new();
        let mut stack = Vec::new();
        let mut rect: Option<[f64; 4]> = None;
        let mut pattern = false;
        for op in ops.iter().take(64) {
            match op.op.as_slice() {
                b"q" => stack.push((st.clone(), pattern)),
                b"Q" => {
                    if let Some((s, p)) = stack.pop() {
                        (st, pattern) = (s, p);
                    }
                }
                b"cm" => match op.nums::<6>() {
                    Some(m) => st.ctm = Matrix(m).then(&st.ctm),
                    None => return false,
                },
                b"gs" => {
                    let Some(gs) =
                        op.name(0).and_then(|n| resolved_dict(self.doc, res.get(b"ExtGState")).and_then(|d| resolved_dict(self.doc, d.get(n))))
                    else {
                        return false;
                    };
                    st.set(self.doc, &gs);
                }
                b"g" | b"rg" | b"k" | b"sc" => pattern = false,
                b"cs" => pattern = op.name(0).is_none_or(|n| !matches!(n, b"DeviceGray" | b"DeviceRGB" | b"DeviceCMYK")),
                b"scn" => pattern = pattern || op.operands.last().and_then(Object::as_name).is_some(),
                b"G" | b"RG" | b"K" | b"SC" | b"SCN" | b"CS" | b"w" | b"J" | b"j" | b"M" | b"d" | b"ri" | b"i" | b"BMC" | b"BDC" | b"EMC" => {}
                b"re" if rect.is_none() => {
                    let Some([x, y, w, h]) = op.nums::<4>() else { return false };
                    rect = Some([x.min(x + w), y.min(y + h), x.max(x + w), y.max(y + h)]);
                }
                b"f" | b"F" | b"f*" | b"B" | b"B*" => {
                    let [a, b, c, d, _, _] = st.ctm.0;
                    let axis = (b == 0.0 && c == 0.0) || (a == 0.0 && d == 0.0);
                    let Some(r) = rect.filter(|_| axis && !pattern && st.fill_alpha == 1.0 && st.normal_blend && !st.soft_mask) else {
                        return false;
                    };
                    let r = st.ctm.bbox(r);
                    return r[0] <= bbox[0] + 0.5 && r[1] <= bbox[1] + 0.5 && r[2] >= bbox[2] - 0.5 && r[3] >= bbox[3] - 0.5;
                }
                b"n" => rect = None,
                _ => return false,
            }
        }
        false
    }

    /// Merge a form's own resources into the page's, renaming what collides.
    fn merge(&mut self, res: &Dict) -> Result<Renames, Fail> {
        let sources: Vec<Dict> = CATEGORIES.iter().map(|c| resolved_dict(self.doc, res.get(c)).unwrap_or_default()).collect();
        if sources.iter().map(Dict::len).sum::<usize>() > MAX_ENTRIES {
            return Err(Fail::Kept);
        }
        // Default colour spaces recolour device colours in the stream whose resources hold them
        // (§8.6.5.6): the form's and the page's must agree.
        for name in [&b"DefaultGray"[..], b"DefaultRGB", b"DefaultCMYK"] {
            let form = sources.get(COLORSPACE).and_then(|d| d.get(name));
            let page = self.cat(COLORSPACE).and_then(|d| d.get(name));
            if form != page {
                return Err(Fail::Kept);
            }
        }
        // A Type 3 font without resources borrows those of the stream it is used in, whose names
        // may change here (§9.6.4). Chrome's Type 3 glyphs are bare paths, which borrow nothing.
        let fonts: Vec<Dict> =
            sources.get(FONT).map(|d| d.iter().filter_map(|(_, f)| resolved_dict(self.doc, Some(f))).collect()).unwrap_or_default();
        for font in fonts {
            if font.name(b"Subtype") == Some(b"Type3") && !font.contains(b"Resources") && self.glyphs_borrow(&font) {
                return Err(Fail::Kept);
            }
        }
        let mut renames = HashMap::new();
        for (c, source) in sources.into_iter().enumerate() {
            if c == PATTERN {
                continue;
            }
            for (name, value) in source.iter() {
                let existing = self.cat(c).and_then(|d| d.get(name)).cloned();
                match existing {
                    Some(v) if v == *value => {}
                    None if !self.taken.contains(&(c, name.clone())) => {
                        if let Some(d) = self.cats.get_mut(c) {
                            d.set(name.clone(), value.clone());
                        }
                        self.taken.insert((c, name.clone()));
                        if let Some(flag) = self.changed.get_mut(c) {
                            *flag = true;
                        }
                    }
                    _ => {
                        let new = self.fresh(c, name, "_f");
                        if let Some(d) = self.cats.get_mut(c) {
                            d.set(new.clone(), value.clone());
                        }
                        if let Some(flag) = self.changed.get_mut(c) {
                            *flag = true;
                        }
                        renames.insert((c, name.clone()), new);
                    }
                }
            }
        }
        Ok(renames)
    }

    /// Whether any glyph procedure of Type 3 `font` names a resource (or can't be read within the
    /// page's glyph budget, which counts as yes).
    fn glyphs_borrow(&mut self, font: &Dict) -> bool {
        let procs = resolved_dict(self.doc, font.get(b"CharProcs")).unwrap_or_default();
        for (_, proc) in procs.iter() {
            if self.glyphs >= MAX_GLYPHS {
                // Where the budget ran out depends on what was read before; plan nothing.
                self.exhausted = true;
                return true;
            }
            self.glyphs = self.glyphs.saturating_add(1);
            let Object::Stream(s) = &*self.doc.resolve(proc) else { continue };
            let Ok(data) = s.decoded_within(1 << 20) else { return true };
            if parse(&data).ops.iter().any(|op| op.inline.is_some() || !name_refs(op).is_empty() || op.is("Do")) {
                return true;
            }
        }
        false
    }

    /// The merged name for `name` of category `c` in `scope`.
    fn map_name(&mut self, scope: &Scope, c: usize, name: &[u8]) -> Vec<u8> {
        if c == PATTERN {
            return self.map_pattern(scope, name);
        }
        if let Some(n) = scope.renames.get(&(c, name.to_vec())) {
            return n.clone();
        }
        if scope.dicts.get(c).is_some_and(|d| d.contains(name)) {
            return name.to_vec();
        }
        // Missing from the form's resources: it must stay missing on the page.
        if !self.cat(c).is_some_and(|d| d.contains(name)) {
            self.taken.insert((c, name.to_vec()));
            return name.to_vec();
        }
        if let Some(n) = self.missing.get(&(c, name.to_vec())) {
            return n.clone();
        }
        let n = self.fresh(c, name, "_missing");
        self.missing.insert((c, name.to_vec()), n.clone());
        n
    }

    /// A pattern a form paints with maps to the form's space (§8.7.2); on the page it needs a copy
    /// whose matrix includes the form's matrix and the CTM at the `Do`.
    fn map_pattern(&mut self, scope: &Scope, name: &[u8]) -> Vec<u8> {
        let Some(value) = scope.dicts.get(PATTERN).and_then(|d| d.get(name)).cloned() else {
            if !self.cat(PATTERN).is_some_and(|d| d.contains(name)) {
                self.taken.insert((PATTERN, name.to_vec()));
                return name.to_vec();
            }
            if let Some(n) = self.missing.get(&(PATTERN, name.to_vec())) {
                return n.clone();
            }
            let n = self.fresh(PATTERN, name, "_missing");
            self.missing.insert((PATTERN, name.to_vec()), n.clone());
            return n;
        };
        let key = (identity(&value), scope.space.0.map(f64::to_bits));
        if let Some(n) = self.pattern_names.get(&key) {
            return n.clone();
        }
        let copy = match &*self.doc.resolve(&value) {
            Object::Stream(s) => {
                let mut s = s.clone();
                let m = matrix_of(self.doc, s.dict.get(b"Matrix")).unwrap_or(Matrix::IDENTITY).then(&scope.space);
                s.dict.set(b"Matrix".to_vec(), Object::Array(m.0.map(Object::Real).to_vec()));
                Object::Stream(s)
            }
            Object::Dict(d) => {
                let mut d = d.clone();
                let m = matrix_of(self.doc, d.get(b"Matrix")).unwrap_or(Matrix::IDENTITY).then(&scope.space);
                d.set(b"Matrix".to_vec(), Object::Array(m.0.map(Object::Real).to_vec()));
                Object::Dict(d)
            }
            // Not a pattern: painting with it fails either way; keep the reference as it is.
            _ => value,
        };
        let n = self.fresh(PATTERN, name, "_p");
        self.patterns.push((n.clone(), copy));
        self.pattern_names.insert(key, n.clone());
        n
    }

    /// `op` with its resource names mapped, serialized, when any changed.
    fn rename(&mut self, op: &Op, scope: &Scope) -> Option<Vec<u8>> {
        if op.inline.is_some() {
            return self.rename_inline_image(op, scope);
        }
        let mut changed = None;
        for (i, c) in name_refs(op) {
            let Some(name) = op.operands.get(i).and_then(Object::as_name) else { continue };
            let new = self.map_name(scope, c, name);
            if new != name {
                let o = changed.get_or_insert_with(|| op.clone());
                if let Some(slot) = o.operands.get_mut(i) {
                    *slot = Object::Name(new);
                }
            }
        }
        changed.map(|o| {
            let mut out = Vec::new();
            write_op(&o, &mut out);
            out
        })
    }

    /// An inline image names a colour space resource in `/CS` (or as an indexed space's base).
    fn rename_inline_image(&mut self, op: &Op, scope: &Scope) -> Option<Vec<u8>> {
        let (dict, data) = op.inline.as_ref()?;
        let key: &[u8] = if dict.contains(b"CS") { b"CS" } else { b"ColorSpace" };
        let new = match dict.get(key)? {
            Object::Name(n) if !is_inline_family(n) => {
                let m = self.map_name(scope, COLORSPACE, n);
                (m != *n).then_some(Object::Name(m))
            }
            Object::Array(a) => match a.get(1).and_then(Object::as_name) {
                Some(base) if !is_inline_family(base) => {
                    let m = self.map_name(scope, COLORSPACE, base);
                    (m != base).then(|| {
                        let mut a = a.clone();
                        if let Some(slot) = a.get_mut(1) {
                            *slot = Object::Name(m);
                        }
                        Object::Array(a)
                    })
                }
                _ => None,
            },
            _ => None,
        }?;
        let mut dict = dict.clone();
        dict.set(key.to_vec(), new);
        let mut out = Vec::new();
        write_op(&Op { op: b"BI".to_vec(), operands: Vec::new(), inline: Some((dict, data.clone())), span: 0..0 }, &mut out);
        Some(out)
    }

    /// Walk a content stream, inlining the text-bearing forms it draws and (in a form's content)
    /// renaming resource names. `eligible` says which operators may be replaced at all.
    fn walk(
        &mut self,
        ops: &[Op],
        scope: &Scope,
        mut st: State,
        path: &mut Vec<ObjRef>,
        eligible: &dyn Fn(usize) -> bool,
        edits: &mut Vec<(Range<usize>, Vec<u8>)>,
    ) -> Result<(), Fail> {
        let mut stack: Vec<State> = Vec::new();
        let mut in_text = false;
        for (i, op) in ops.iter().enumerate() {
            match op.op.as_slice() {
                b"q" if stack.len() < 4096 => stack.push(st.clone()),
                b"Q" => {
                    if let Some(s) = stack.pop() {
                        st = s;
                    }
                }
                b"cm" => {
                    if let Some(m) = op.nums::<6>() {
                        st.ctm = Matrix(m).then(&st.ctm);
                    }
                }
                b"gs" => {
                    if let Some(gs) = op.name(0).and_then(|n| scope.dicts.get(EXTGSTATE).and_then(|d| resolved_dict(self.doc, d.get(n)))) {
                        st.set(self.doc, &gs);
                    }
                }
                b"BT" => in_text = true,
                b"ET" => in_text = false,
                b"Do" if !in_text && eligible(i) => {
                    if let Some((r, stream)) = op.name(0).and_then(|n| form_named(self.doc, &scope.res, n)) {
                        // A form without resources uses its parent's, whose names change here.
                        let must = !scope.page && !stream.dict.contains(b"Resources");
                        if must || self.draws_text(r, &stream, &scope.res, path) {
                            let snapshot = scope.page.then(|| self.snapshot());
                            match self.inline(r, &stream, scope, &st, path) {
                                Ok(bytes) => {
                                    edits.push((op.span.clone(), bytes));
                                    self.painted = true;
                                    continue;
                                }
                                Err(fail) => {
                                    if let Some(s) = snapshot {
                                        self.restore(s);
                                    } else if must || matches!(fail, Fail::Abort) {
                                        return Err(Fail::Abort);
                                    }
                                }
                            }
                        }
                    }
                }
                _ => {}
            }
            if matches!(
                op.op.as_slice(),
                b"f" | b"F" | b"f*" | b"B" | b"B*" | b"b" | b"b*" | b"S" | b"s" | b"sh" | b"Do" | b"BI" | b"Tj" | b"TJ" | b"'" | b"\""
            ) {
                self.painted = true;
            }
            if !scope.page
                && let Some(bytes) = self.rename(op, scope)
            {
                edits.push((op.span.clone(), bytes));
            }
        }
        Ok(())
    }

    /// The content that draws form `stream` in place of its `Do`, drawn in `parent` with state `st`.
    fn inline(&mut self, r: Option<ObjRef>, stream: &Stream, parent: &Scope, st: &State, path: &mut Vec<ObjRef>) -> Result<Vec<u8>, Fail> {
        if path.len() >= MAX_DEPTH || r.is_some_and(|r| path.contains(&r)) {
            return Err(Fail::Abort);
        }
        let dict = &stream.dict;
        // Optional content, reference XObjects and forms that are a structure element's content
        // item mean something as a whole.
        if dict.contains(b"OC") || dict.contains(b"Ref") || dict.contains(b"StructParent") {
            return Err(Fail::Kept);
        }
        let matrix = matrix_of(self.doc, dict.get(b"Matrix")).ok_or(Fail::Kept)?;
        let bbox = bbox_of(self.doc, dict.get(b"BBox")).ok_or(Fail::Kept)?;
        let data = stream.decoded_within(MAX_FORM_BYTES).map_err(|_| Fail::Kept)?;
        let parsed = parse(&data);
        // Bytes the parser couldn't read (or operands without an operator) would join the
        // page's next operator once inlined.
        if parsed.skipped > 0 {
            return Err(Fail::Kept);
        }
        let open = balance(&parsed.ops).ok_or(Fail::Kept)?;
        let own = resolved_dict(self.doc, dict.get(b"Resources"));
        let res = own.clone().unwrap_or_else(|| parent.res.clone());
        if !self.group_inlines(dict, bbox, &parsed.ops, &res, st) {
            return Err(Fail::Kept);
        }
        // Marked content in a form refers to the structure tree through the form's
        // /StructParents (§14.7.5.4); the page can take that over when it has none of its own.
        let struct_parents = dict.get(b"StructParents").and_then(|v| self.doc.resolve(v).as_int());
        if let Some(k) = struct_parents
            && self.page_struct_parents.or(self.adopted).is_some_and(|p| p != k)
        {
            return Err(Fail::Kept);
        }
        // Everything above refuses before the plan changes (as does `merge`); after it only a
        // limit can stop the form.
        let space = matrix.then(&st.ctm);
        let scope = match own {
            Some(own) => {
                let renames = self.merge(&own)?;
                let dicts = CATEGORIES.iter().map(|c| resolved_dict(self.doc, own.get(c)).unwrap_or_default()).collect();
                Scope { res: own, dicts, renames, space, page: false }
            }
            None => Scope { res: parent.res.clone(), dicts: parent.dicts.clone(), renames: parent.renames.clone(), space, page: false },
        };
        if struct_parents.is_some() && self.page_struct_parents.is_none() && self.adopted.is_none() {
            self.adopted = struct_parents;
        }
        self.bytes = self.bytes.saturating_add(data.len());
        if self.bytes > MAX_TOTAL_BYTES {
            self.exhausted = true;
            return Err(Fail::Abort);
        }
        let mut edits = Vec::new();
        if let Some(r) = r {
            path.push(r);
        }
        let walked = self.walk(&parsed.ops, &scope, State { ctm: space, ..st.clone() }, path, &|_| true, &mut edits);
        if r.is_some() {
            path.pop();
        }
        walked?;
        let body = Pieces::join(&[data.as_slice()]).splice(edits).into_iter().next().unwrap_or_default();
        let mut out = b"\nq\n".to_vec();
        write_op(&Op::new("cm", matrix.0.map(Object::Real).to_vec()), &mut out);
        let [x0, y0, x1, y1] = bbox;
        write_op(&Op::new("re", [x0, y0, x1 - x0, y1 - y0].map(Object::Real).to_vec()), &mut out);
        out.extend_from_slice(b"W n\n");
        out.extend_from_slice(&body);
        out.push(b'\n');
        for _ in 0..open {
            out.extend_from_slice(b"Q\n");
        }
        out.extend_from_slice(b"Q\n");
        self.bytes = self.bytes.saturating_add(out.len());
        if self.bytes > MAX_TOTAL_BYTES {
            self.exhausted = true;
            return Err(Fail::Abort);
        }
        if let Some(r) = r {
            self.forms.push(r);
        }
        Ok(out)
    }
}

/// The page's text-bearing forms inlined, or `None` when it has none (or none can be).
pub(crate) fn plan(doc: &Document, page: usize) -> Option<Plan> {
    let p = pdfcraft_model::pages(doc).into_iter().nth(page)?;
    // A page past its decode budget is left as it is (reading it refuses it anyway).
    let streams = crate::text::content_streams(doc, &p.dict).ok()?;
    // Most pages draw no XObject at all.
    if !streams.iter().any(|(_, d)| d.windows(2).any(|w| w == b"Do")) {
        return None;
    }
    let res = resolved_dict(doc, p.dict.get(b"Resources")).unwrap_or_default();
    // Streams PdfCraft added (items, headers and footers) keep their own structure.
    let eligible_streams: Vec<bool> =
        streams.iter().map(|(o, _)| !matches!(&*doc.resolve(o), Object::Stream(s) if s.dict.contains(b"PCMark"))).collect();
    let pieces = Pieces::join(&streams.iter().map(|(_, d)| d.as_slice()).collect::<Vec<_>>());
    let ops = pieces.parse().ops;
    let mut planner = Planner {
        doc,
        cats: CATEGORIES.iter().map(|c| resolved_dict(doc, res.get(c)).unwrap_or_default()).collect(),
        changed: [false; 7],
        taken: HashSet::new(),
        patterns: Vec::new(),
        pattern_names: HashMap::new(),
        missing: HashMap::new(),
        page_struct_parents: p.dict.get(b"StructParents").and_then(|v| doc.resolve(v).as_int()),
        adopted: None,
        forms: Vec::new(),
        bytes: 0,
        visits: 0,
        glyphs: 0,
        painted: false,
        exhausted: false,
        text_memo: HashMap::new(),
    };
    // Names the page refers to are taken even when its resources lack them, so a form's
    // resource can't start answering to one.
    for op in &ops {
        for (i, c) in name_refs(op) {
            if let Some(name) = op.operands.get(i).and_then(Object::as_name) {
                planner.taken.insert((c, name.to_vec()));
            }
        }
    }
    let dicts = planner.cats.clone();
    let scope = Scope { res: res.clone(), dicts, renames: HashMap::new(), space: Matrix::IDENTITY, page: true };
    // A `Do` split across two streams, or in a stream PdfCraft added, stays as it is.
    let eligible = |i: usize| {
        ops.get(i).is_some_and(|op| {
            let (a, b) = pieces.pieces_of(op);
            a == b && eligible_streams.get(a).copied().unwrap_or(false)
        })
    };
    let mut edits = Vec::new();
    let mut path = Vec::new();
    planner.walk(&ops, &scope, State::new(), &mut path, &eligible, &mut edits).ok()?;
    if edits.is_empty() || planner.exhausted {
        return None;
    }
    let contents = pieces.splice(edits);
    let mut resources = res;
    for (c, dict) in planner.cats.into_iter().enumerate() {
        if (planner.changed.get(c).copied().unwrap_or(false) || (c == PATTERN && !planner.patterns.is_empty()))
            && let Some(name) = CATEGORIES.get(c)
        {
            resources.set(name.to_vec(), Object::Dict(dict));
        }
    }
    Some(Plan { page: p.obj, streams, contents, resources, patterns: planner.patterns, struct_parents: planner.adopted, forms: planner.forms })
}

/// Write `plan` into `doc`. `compress` writes Flate streams and points the structure tree at the
/// page (for the document itself); a [`view`] skips both.
fn apply(doc: &mut Document, plan: Plan, compress: bool) -> Result<(), EditError> {
    let mut resources = plan.resources;
    if !plan.patterns.is_empty() {
        let mut patterns = resolved_dict(doc, resources.get(b"Pattern")).unwrap_or_default();
        for (name, object) in plan.patterns {
            let r = doc.add(object);
            patterns.set(name, Object::Ref(r));
        }
        resources.set(b"Pattern".to_vec(), Object::Dict(patterns));
    }
    let contents = if compress {
        crate::text::rewritten_contents(doc, &plan.streams, plan.contents)
    } else {
        let mut list = Vec::with_capacity(plan.streams.len());
        for ((obj, old), data) in plan.streams.iter().zip(plan.contents) {
            if *old == data {
                list.push(obj.clone());
                continue;
            }
            let mut dict = match &*doc.resolve(obj) {
                Object::Stream(s) => s.dict.clone(),
                _ => Dict::new(),
            };
            for key in [&b"Length"[..], b"Filter", b"DecodeParms"] {
                dict.remove(key);
            }
            list.push(Object::Ref(doc.add(Object::Stream(Stream::from_raw(dict, data)))));
        }
        match <[Object; 1]>::try_from(list) {
            Ok([one]) => one,
            Err(many) => Object::Array(many),
        }
    };
    doc.update_dict(plan.page, |d| {
        d.set(b"Contents".to_vec(), contents);
        d.set(b"Resources".to_vec(), Object::Dict(resources));
        if let Some(k) = plan.struct_parents {
            d.set(b"StructParents".to_vec(), Object::Int(k));
        }
    })?;
    if compress && plan.struct_parents.is_some() {
        retarget_structure(doc, plan.page, &plan.forms.into_iter().collect())?;
    }
    Ok(())
}

/// The document Edit text reads page `page` from: `doc` itself, or a copy with the page's
/// text-bearing forms inlined.
pub(crate) fn view(doc: &Document, page: usize) -> Result<Cow<'_, Document>, EditError> {
    let Some(plan) = plan(doc, page) else { return Ok(Cow::Borrowed(doc)) };
    let mut copy = doc.clone();
    apply(&mut copy, plan, false)?;
    Ok(Cow::Owned(copy))
}

/// Run a rewrite that uses [`view`] indexes: on the document with the page's forms inlined first,
/// committed only when the rewrite succeeds.
pub(crate) fn edit_inlined<T>(doc: &mut Document, page: usize, f: impl FnOnce(&mut Document) -> Result<T, EditError>) -> Result<T, EditError> {
    let Some(plan) = plan(doc, page) else { return f(doc) };
    let mut work = doc.clone();
    apply(&mut work, plan, true)?;
    let out = f(&mut work)?;
    *doc = work;
    Ok(out)
}

/// Inline the text-bearing forms page `page` (0-based) draws into its own content, so their text
/// can be edited; rendering is unchanged. Edit text does this itself before a rewrite; returns
/// whether anything was inlined.
pub fn inline_text_forms(doc: &mut Document, page: usize) -> Result<bool, EditError> {
    match plan(doc, page) {
        Some(plan) => apply(doc, plan, true).map(|()| true),
        None => Ok(false),
    }
}

/// Marked-content references into an inlined form (`/Stm`) now point at the page's content,
/// which holds that marked content under the `/StructParents` it took over (§14.7.5.2).
fn retarget_structure(doc: &mut Document, page: ObjRef, forms: &HashSet<ObjRef>) -> Result<(), EditError> {
    /// The most structure objects visited.
    const MAX_NODES: usize = 1_000_000;
    let Some(root) = doc.root().map(|r| doc.get(r)).and_then(|c| c.as_dict().and_then(|c| c.get(b"StructTreeRoot").cloned())) else {
        return Ok(());
    };
    // (object, the page it inherits) for every indirect structure object to look at.
    let mut todo: Vec<(Object, Option<ObjRef>)> = vec![(root, None)];
    let mut seen = HashSet::new();
    let mut updates: Vec<(ObjRef, Dict)> = Vec::new();
    // A K entry (or an array of them) with its MCRs fixed, or None when nothing changed.
    fn fix(
        o: &Object,
        pg: Option<ObjRef>,
        page: ObjRef,
        forms: &HashSet<ObjRef>,
        todo: &mut Vec<(Object, Option<ObjRef>)>,
        depth: usize,
    ) -> Option<Object> {
        match o {
            Object::Ref(_) => {
                todo.push((o.clone(), pg));
                None
            }
            Object::Array(a) if depth < 32 => {
                let fixed: Vec<Option<Object>> = a.iter().map(|x| fix(x, pg, page, forms, todo, depth + 1)).collect();
                fixed.iter().any(Option::is_some).then(|| Object::Array(a.iter().zip(fixed).map(|(x, f)| f.unwrap_or_else(|| x.clone())).collect()))
            }
            Object::Dict(d) if depth < 32 => fix_dict(d, pg, page, forms, todo, depth).map(Object::Dict),
            _ => None,
        }
    }
    fn fix_dict(
        d: &Dict,
        pg: Option<ObjRef>,
        page: ObjRef,
        forms: &HashSet<ObjRef>,
        todo: &mut Vec<(Object, Option<ObjRef>)>,
        depth: usize,
    ) -> Option<Dict> {
        let pg = d.reference(b"Pg").or(pg);
        if d.name(b"Type") == Some(b"MCR") {
            let into_form = d.reference(b"Stm").is_some_and(|s| forms.contains(&s));
            return (into_form && pg == Some(page)).then(|| {
                let mut d = d.clone();
                d.remove(b"Stm");
                d.remove(b"StmOwn");
                d.set(b"Pg".to_vec(), Object::Ref(page));
                d
            });
        }
        let k = d.get(b"K")?;
        let fixed = fix(k, pg, page, forms, todo, depth + 1)?;
        let mut d = d.clone();
        d.set(b"K".to_vec(), fixed);
        Some(d)
    }
    while let Some((o, pg)) = todo.pop() {
        if seen.len() >= MAX_NODES {
            break;
        }
        let Some(r) = o.as_ref() else { continue };
        if !seen.insert(r) {
            continue;
        }
        let Some(d) = doc.get(r).as_dict().cloned() else { continue };
        if let Some(fixed) = fix_dict(&d, pg, page, forms, &mut todo, 0) {
            updates.push((r, fixed));
        }
    }
    for (r, d) in updates {
        doc.update_dict(r, |old| *old = d)?;
    }
    Ok(())
}

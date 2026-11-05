//! XML helpers for Office parts: parsing with size and node caps, and lookups by local name
//! (the prefixes Office files use vary, the local names don't).

use roxmltree::{Document, Node, ParsingOptions};

/// Largest XML part parsed (bytes).
pub(crate) const MAX_XML_BYTES: usize = 48 << 20;
/// Most nodes one XML part may have.
const MAX_NODES: u32 = 4_000_000;

/// The text of an XML part, ready for [`parse_str`]: UTF-8 (a BOM dropped), or UTF-16 with a
/// byte order mark. `None` when it is larger than [`MAX_XML_BYTES`].
pub(crate) fn text_of(bytes: &[u8]) -> Option<String> {
    if bytes.len() > MAX_XML_BYTES {
        return None;
    }
    if let Some((encoding, bom)) = encoding_rs::Encoding::for_bom(bytes) {
        return Some(encoding.decode_without_bom_handling(bytes.get(bom..).unwrap_or_default()).0.into_owned());
    }
    Some(String::from_utf8_lossy(bytes).into_owned())
}

/// Deepest element nesting parsed. roxmltree descends recursively, so a few thousand nested
/// elements would exhaust the thread's stack (an abort no guard can catch): the depth is checked
/// before parsing. Real Office parts stay far below this.
const MAX_XML_DEPTH: usize = 256;

/// Whether `text` nests elements deeper than `limit`, skipping comments, CDATA sections,
/// processing instructions, declarations and quoted attribute values. Self-closing tags don't stay
/// open. A stray `<` only makes it over-count, which errs towards refusing the part.
fn nests_deeper_than(text: &str, limit: usize) -> bool {
    let b = text.as_bytes();
    let skip_to = |from: usize, end: &[u8]| -> usize {
        let mut p = from;
        while p < b.len() {
            if b.get(p..).is_some_and(|r| r.starts_with(end)) {
                return p + end.len();
            }
            p += 1;
        }
        b.len()
    };
    let mut i = 0usize;
    let mut depth = 0usize;
    while let Some(&c) = b.get(i) {
        if c != b'<' {
            i += 1;
            continue;
        }
        let rest = b.get(i..).unwrap_or_default();
        if rest.starts_with(b"<!--") {
            i = skip_to(i + 4, b"-->");
        } else if rest.starts_with(b"<![CDATA[") {
            i = skip_to(i + 9, b"]]>");
        } else if rest.starts_with(b"<?") {
            i = skip_to(i + 2, b"?>");
        } else if rest.starts_with(b"<!") {
            i = skip_to(i + 2, b">");
        } else if rest.starts_with(b"</") {
            depth = depth.saturating_sub(1);
            i = skip_to(i + 2, b">");
        } else {
            depth += 1;
            if depth > limit {
                return true;
            }
            i += 1;
            let (mut quote, mut last) = (0u8, 0u8);
            while let Some(&t) = b.get(i) {
                i += 1;
                if quote != 0 {
                    if t == quote {
                        quote = 0;
                    }
                } else if t == b'"' || t == b'\'' {
                    quote = t;
                } else if t == b'>' {
                    break;
                } else if !t.is_ascii_whitespace() {
                    last = t;
                }
            }
            if last == b'/' {
                depth = depth.saturating_sub(1);
            }
        }
    }
    false
}

/// Parse XML text with the node and depth caps; DTDs are refused (no entity expansion).
pub(crate) fn parse_str(text: &str) -> Option<Document<'_>> {
    if nests_deeper_than(text, MAX_XML_DEPTH) {
        return None;
    }
    let opts = ParsingOptions { allow_dtd: false, nodes_limit: MAX_NODES };
    Document::parse_with_options(text, opts).ok()
}

/// The first child element of `n` named `name` (local name).
pub(crate) fn child<'a, 'i>(n: Node<'a, 'i>, name: &str) -> Option<Node<'a, 'i>> {
    n.children().find(|c| c.is_element() && c.tag_name().name() == name)
}

/// Child elements of `n` named `name`.
pub(crate) fn children<'a, 'i: 'a>(n: Node<'a, 'i>, name: &'a str) -> impl Iterator<Item = Node<'a, 'i>> + 'a {
    n.children().filter(move |c| c.is_element() && c.tag_name().name() == name)
}

/// Child elements of `n`.
pub(crate) fn elements<'a, 'i: 'a>(n: Node<'a, 'i>) -> impl Iterator<Item = Node<'a, 'i>> + 'a {
    n.children().filter(Node::is_element)
}

/// The element reached from `n` through child names `path`.
pub(crate) fn path<'a, 'i>(n: Node<'a, 'i>, path: &[&str]) -> Option<Node<'a, 'i>> {
    path.iter().try_fold(n, |n, name| child(n, name))
}

/// Attribute `name` of `n` by local name (any namespace, or none).
pub(crate) fn attr<'a>(n: Node<'a, '_>, name: &str) -> Option<&'a str> {
    n.attributes().find(|a| a.name() == name).map(|a| a.value())
}

/// A numeric attribute; `None` when missing, unparsable or not finite.
pub(crate) fn num(n: Node<'_, '_>, name: &str) -> Option<f64> {
    attr(n, name).and_then(parse_num)
}

/// A number as Office writes them; a trailing unit (`pt`, `%`) is dropped. Not finite → `None`.
pub(crate) fn parse_num(s: &str) -> Option<f64> {
    let s = s.trim();
    let end = s.find(|c: char| !(c.is_ascii_digit() || matches!(c, '.' | '-' | '+' | 'e' | 'E'))).unwrap_or(s.len());
    let v: f64 = s.get(..end)?.parse().ok()?;
    v.is_finite().then_some(v)
}

/// An OOXML on/off value (`w:b`, `w:val="0"`, `b="1"`); a missing `val` means on.
pub(crate) fn on_off(n: Node<'_, '_>) -> bool {
    !matches!(attr(n, "val"), Some("0" | "false" | "off" | "none"))
}

/// Package relationships part for `part` (`word/document.xml` → `word/_rels/document.xml.rels`).
pub(crate) fn rels_path(part: &str) -> String {
    match part.rsplit_once('/') {
        Some((dir, file)) => format!("{dir}/_rels/{file}.rels"),
        None => format!("_rels/{part}.rels"),
    }
}

/// The relationships of a part: (id, type, resolved target part).
pub(crate) fn relationships(zip: &super::zip::Zip<'_>, part: &str) -> Vec<(String, String, String)> {
    let Ok(Some(bytes)) = zip.read(&rels_path(part)) else { return Vec::new() };
    let Some(text) = text_of(&bytes) else { return Vec::new() };
    let Some(doc) = parse_str(&text) else { return Vec::new() };
    let mut out = Vec::new();
    for r in doc.root_element().children().filter(|c| c.is_element() && c.tag_name().name() == "Relationship") {
        if attr(r, "TargetMode") == Some("External") {
            continue;
        }
        if let (Some(id), Some(target)) = (attr(r, "Id"), attr(r, "Target"))
            && let Some(resolved) = super::zip::resolve(part, target)
        {
            out.push((id.to_string(), attr(r, "Type").unwrap_or_default().to_string(), resolved));
        }
    }
    out
}

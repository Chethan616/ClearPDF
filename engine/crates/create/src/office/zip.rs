//! A small, defensive reader for the zip packages Office files are (ECMA-376 Part 2, Open
//! Packaging Conventions; APPNOTE.TXT for the container). It reads the central directory, and
//! inflates stored or deflated entries on demand. Every size in the file is untrusted: entry
//! count, per-entry and total inflated bytes are capped, and the caps are measured while
//! inflating rather than taken from the headers. ZIP64 and encrypted entries are refused.

use std::io::Read;

/// Most entries a package may list.
pub(crate) const MAX_ENTRIES: usize = 10_000;
/// Most bytes one entry may inflate to.
pub(crate) const MAX_ENTRY_BYTES: u64 = 64 << 20;
/// Most bytes all the entries read from one package may inflate to.
pub(crate) const MAX_TOTAL_BYTES: u64 = 256 << 20;

#[derive(Debug, PartialEq)]
pub(crate) enum ZipError {
    /// Not a zip file, or a damaged one.
    Damaged(&'static str),
    /// A feature this reader doesn't handle (ZIP64, encryption, another compression method).
    Unsupported(&'static str),
    /// An entry, or the package, inflates past the caps.
    TooLarge,
}

#[derive(Clone, Debug)]
struct Entry {
    name: String,
    method: u16,
    compressed: u64,
    local_offset: u64,
}

pub(crate) struct Zip<'a> {
    bytes: &'a [u8],
    entries: Vec<Entry>,
    /// Bytes inflated so far (shared budget of `MAX_TOTAL_BYTES`).
    used: std::cell::Cell<u64>,
}

fn le16(b: &[u8], at: usize) -> Option<u16> {
    let s = b.get(at..at.checked_add(2)?)?;
    Some(u16::from_le_bytes([*s.first()?, *s.get(1)?]))
}

fn le32(b: &[u8], at: usize) -> Option<u32> {
    let s = b.get(at..at.checked_add(4)?)?;
    Some(u32::from_le_bytes([*s.first()?, *s.get(1)?, *s.get(2)?, *s.get(3)?]))
}

/// Whether `bytes` start like a zip file (a local header, or the end record of an empty one).
pub(crate) fn looks_like_zip(bytes: &[u8]) -> bool {
    bytes.starts_with(b"PK\x03\x04") || bytes.starts_with(b"PK\x05\x06")
}

impl<'a> Zip<'a> {
    pub(crate) fn open(bytes: &'a [u8]) -> Result<Self, ZipError> {
        const EOCD: u32 = 0x0605_4b50;
        // The end of central directory record sits in the last 22 + 65 535 bytes (its comment).
        let min = bytes.len().saturating_sub(22 + 0xFFFF);
        let mut eocd = None;
        let mut i = bytes.len().saturating_sub(22);
        loop {
            if le32(bytes, i) == Some(EOCD) {
                eocd = Some(i);
                break;
            }
            if i <= min || i == 0 {
                break;
            }
            i -= 1;
        }
        let eocd = eocd.ok_or(ZipError::Damaged("no zip directory"))?;
        let count = le16(bytes, eocd + 10).ok_or(ZipError::Damaged("truncated zip directory"))?;
        let size = le32(bytes, eocd + 12).ok_or(ZipError::Damaged("truncated zip directory"))?;
        let offset = le32(bytes, eocd + 16).ok_or(ZipError::Damaged("truncated zip directory"))?;
        if count == 0xFFFF || size == 0xFFFF_FFFF || offset == 0xFFFF_FFFF {
            return Err(ZipError::Unsupported("ZIP64 packages"));
        }
        let count = usize::from(count);
        if count > MAX_ENTRIES {
            return Err(ZipError::TooLarge);
        }
        let start = usize::try_from(offset).map_err(|_| ZipError::Damaged("bad zip directory offset"))?;
        let dir_end = start.checked_add(size as usize).ok_or(ZipError::Damaged("bad zip directory size"))?;
        let dir = bytes.get(start..dir_end.min(eocd)).ok_or(ZipError::Damaged("the zip directory is outside the file"))?;
        let mut entries = Vec::with_capacity(count);
        let mut at = 0usize;
        while entries.len() < count {
            if le32(dir, at) != Some(0x0201_4b50) {
                return Err(ZipError::Damaged("a damaged zip directory entry"));
            }
            let flags = le16(dir, at + 8).ok_or(ZipError::Damaged("truncated zip directory entry"))?;
            let method = le16(dir, at + 10).ok_or(ZipError::Damaged("truncated zip directory entry"))?;
            let compressed = le32(dir, at + 20).ok_or(ZipError::Damaged("truncated zip directory entry"))?;
            let uncompressed = le32(dir, at + 24).ok_or(ZipError::Damaged("truncated zip directory entry"))?;
            let name_len = usize::from(le16(dir, at + 28).ok_or(ZipError::Damaged("truncated zip directory entry"))?);
            let extra_len = usize::from(le16(dir, at + 30).ok_or(ZipError::Damaged("truncated zip directory entry"))?);
            let comment_len = usize::from(le16(dir, at + 32).ok_or(ZipError::Damaged("truncated zip directory entry"))?);
            let local = le32(dir, at + 42).ok_or(ZipError::Damaged("truncated zip directory entry"))?;
            if compressed == 0xFFFF_FFFF || uncompressed == 0xFFFF_FFFF || local == 0xFFFF_FFFF {
                return Err(ZipError::Unsupported("ZIP64 packages"));
            }
            if flags & 1 != 0 {
                return Err(ZipError::Unsupported("encrypted packages"));
            }
            let name_at = at + 46;
            let raw = dir.get(name_at..name_at + name_len).ok_or(ZipError::Damaged("truncated zip entry name"))?;
            // Part names are ASCII in practice (OPC percent-encodes the rest); keep anything else
            // readable rather than refuse it.
            let name = String::from_utf8_lossy(raw).replace('\\', "/");
            entries.push(Entry { name, method, compressed: u64::from(compressed), local_offset: u64::from(local) });
            at = name_at + name_len + extra_len + comment_len;
        }
        Ok(Self { bytes, entries, used: std::cell::Cell::new(0) })
    }

    /// Whether the package has an entry named `name` (ASCII case-insensitive, as OPC part names
    /// are, and without a leading `/`).
    pub(crate) fn has(&self, name: &str) -> bool {
        self.find(name).is_some()
    }

    fn find(&self, name: &str) -> Option<&Entry> {
        let name = name.trim_start_matches('/');
        self.entries.iter().find(|e| e.name.eq_ignore_ascii_case(name)).or_else(|| {
            // Relationship targets are URIs: `image%201.png` names the item `image 1.png`.
            let decoded = percent_decode(name)?;
            self.entries.iter().find(|e| e.name.eq_ignore_ascii_case(&decoded))
        })
    }

    /// The inflated bytes of entry `name`; `Ok(None)` when there is no such entry.
    pub(crate) fn read(&self, name: &str) -> Result<Option<Vec<u8>>, ZipError> {
        let Some(e) = self.find(name) else { return Ok(None) };
        let local = usize::try_from(e.local_offset).map_err(|_| ZipError::Damaged("bad entry offset"))?;
        if le32(self.bytes, local) != Some(0x0403_4b50) {
            return Err(ZipError::Damaged("a damaged zip entry header"));
        }
        let name_len = usize::from(le16(self.bytes, local + 26).ok_or(ZipError::Damaged("truncated zip entry header"))?);
        let extra_len = usize::from(le16(self.bytes, local + 28).ok_or(ZipError::Damaged("truncated zip entry header"))?);
        let data_at = local.checked_add(30 + name_len + extra_len).ok_or(ZipError::Damaged("bad entry offset"))?;
        let len = usize::try_from(e.compressed).map_err(|_| ZipError::Damaged("bad entry size"))?;
        let data = self
            .bytes
            .get(data_at..data_at.checked_add(len).ok_or(ZipError::Damaged("bad entry size"))?)
            .ok_or(ZipError::Damaged("a truncated zip entry"))?;
        let budget = MAX_ENTRY_BYTES.min(MAX_TOTAL_BYTES.saturating_sub(self.used.get()));
        let out = match e.method {
            0 => {
                if data.len() as u64 > budget {
                    return Err(ZipError::TooLarge);
                }
                data.to_vec()
            }
            8 => {
                // Read one byte past the budget: getting it means the entry is too large.
                let mut out = Vec::new();
                let mut reader = flate2::read::DeflateDecoder::new(data).take(budget.saturating_add(1));
                reader.read_to_end(&mut out).map_err(|_| ZipError::Damaged("a damaged compressed entry"))?;
                if out.len() as u64 > budget {
                    return Err(ZipError::TooLarge);
                }
                out
            }
            _ => return Err(ZipError::Unsupported("this compression method")),
        };
        self.used.set(self.used.get().saturating_add(out.len() as u64));
        Ok(Some(out))
    }
}

/// `%XX` escapes decoded (as UTF-8); `None` when there are none or the result isn't text.
fn percent_decode(s: &str) -> Option<String> {
    if !s.contains('%') {
        return None;
    }
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while let Some(&c) = b.get(i) {
        let hex = |at: usize| b.get(at).and_then(|h| (*h as char).to_digit(16));
        match (c, hex(i + 1), hex(i + 2)) {
            (b'%', Some(hi), Some(lo)) => {
                out.push((hi * 16 + lo) as u8);
                i += 3;
            }
            _ => {
                out.push(c);
                i += 1;
            }
        }
    }
    String::from_utf8(out).ok()
}

/// Resolve a relationship target against the part that owns it (`word/document.xml` +
/// `media/image1.png` → `word/media/image1.png`). Absolute targets (`/xl/media/x.png`) start
/// from the package root; `..` steps up. External targets (URLs) give `None`.
pub(crate) fn resolve(owner: &str, target: &str) -> Option<String> {
    if target.contains("://") || target.starts_with("mailto:") {
        return None;
    }
    let mut parts: Vec<&str> = if target.starts_with('/') {
        Vec::new()
    } else {
        let mut p: Vec<&str> = owner.split('/').collect();
        p.pop();
        p
    };
    for seg in target.split('/') {
        match seg {
            "" | "." => {}
            ".." => {
                parts.pop();
            }
            s => parts.push(s),
        }
    }
    Some(parts.join("/"))
}

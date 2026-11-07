//! Office files to PDF, converted on the device: Word (.docx), Excel (.xlsx), PowerPoint
//! (.pptx) and Rich Text Format (.rtf).
//!
//! Each format is read from its own XML (ECMA-376 Office Open XML; the RTF 1.9 specification)
//! and laid out by PdfCraft: no Office application is involved. The output aims to look like
//! the document for ordinary files, not to be a complete Office renderer. Text is set in the
//! standard PDF fonts (Helvetica, Times and Courier, chosen by the document's font family) in
//! WinAnsiEncoding: characters outside Western European scripts are shown as `?`.
//!
//! Everything read is untrusted: the zip reader caps entries and inflated sizes, XML parts are
//! size- and node-capped, recursion is bounded and the page count is limited.

mod docx;
mod draw;
mod flow;
mod pptx;
mod rtf;
mod xlsx;
mod xml;
pub(crate) mod zip;

use pdfcraft_cos::Document;

use crate::CreateError;
use zip::{Zip, ZipError};

/// An Office file format Create converts.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum OfficeKind {
    Word,
    Excel,
    PowerPoint,
    Rtf,
    /// A legacy binary file (Word 97–2003 .doc, Excel .xls, PowerPoint .ppt): refused with a
    /// message saying to save it in the current format.
    Legacy,
}

impl OfficeKind {
    fn label(self) -> &'static str {
        match self {
            OfficeKind::Word => "Word",
            OfficeKind::Excel => "Excel",
            OfficeKind::PowerPoint => "PowerPoint",
            OfficeKind::Rtf => "RTF",
            OfficeKind::Legacy => "Office",
        }
    }
}

/// The OLE2 compound file signature of legacy binary Office files.
const OLE2: [u8; 8] = [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1];

fn extension(name: &str) -> String {
    name.rsplit_once('.').map(|(_, e)| e.to_ascii_lowercase()).unwrap_or_default()
}

/// The main part of an Office package and its kind, from the package relationships (or the
/// usual part names).
fn main_part(zip: &Zip<'_>) -> Option<(OfficeKind, String)> {
    let kind_of = |part: &str| {
        let p = part.to_ascii_lowercase();
        if p.starts_with("word/") {
            Some(OfficeKind::Word)
        } else if p.starts_with("xl/") {
            Some(OfficeKind::Excel)
        } else if p.starts_with("ppt/") {
            Some(OfficeKind::PowerPoint)
        } else {
            None
        }
    };
    for (_, ty, target) in xml::relationships(zip, "") {
        if ty.ends_with("/officeDocument")
            && zip.has(&target)
            && let Some(k) = kind_of(&target)
        {
            return Some((k, target));
        }
    }
    for (part, kind) in
        [("word/document.xml", OfficeKind::Word), ("xl/workbook.xml", OfficeKind::Excel), ("ppt/presentation.xml", OfficeKind::PowerPoint)]
    {
        if zip.has(part) {
            return Some((kind, part.to_string()));
        }
    }
    None
}

/// What Office format `bytes` (named `name`) is, by content first: a zip package with a Word,
/// Excel or PowerPoint main part, an RTF header, or an OLE2 compound file. A zip that can't be
/// read is still recognised by its extension (converting it then explains it is damaged).
pub fn office_kind(name: &str, bytes: &[u8]) -> Option<OfficeKind> {
    if bytes.starts_with(&OLE2) {
        return Some(OfficeKind::Legacy);
    }
    if bytes.starts_with(b"{\\rtf") {
        return Some(OfficeKind::Rtf);
    }
    let ext = extension(name);
    if zip::looks_like_zip(bytes) {
        if let Ok(z) = Zip::open(bytes)
            && let Some((kind, _)) = main_part(&z)
        {
            return Some(kind);
        }
        return match ext.as_str() {
            "docx" | "docm" | "dotx" | "dotm" => Some(OfficeKind::Word),
            "xlsx" | "xlsm" | "xltx" | "xltm" => Some(OfficeKind::Excel),
            "pptx" | "pptm" | "ppsx" | "ppsm" | "potx" | "potm" => Some(OfficeKind::PowerPoint),
            _ => None,
        };
    }
    if ext == "rtf" && bytes.trim_ascii_start().starts_with(b"{\\rtf") {
        return Some(OfficeKind::Rtf);
    }
    None
}

fn damaged(kind: OfficeKind, why: &str) -> String {
    format!("this {} file is damaged ({why}); open it in its app and save it again", kind.label())
}

fn zip_message(kind: OfficeKind, e: ZipError) -> String {
    match e {
        ZipError::Damaged(why) => damaged(kind, why),
        ZipError::Unsupported(what) => format!("this {} file uses {what}, which can't be converted yet", kind.label()),
        ZipError::TooLarge => format!("this {} file is too large to convert (its contents expand past the size limit)", kind.label()),
    }
}

/// An open package plus what is needed to report errors about it.
pub(crate) struct Package<'a> {
    pub zip: Zip<'a>,
    pub kind: OfficeKind,
}

impl Package<'_> {
    /// The text of XML part `part`; `Ok(None)` when the package has no such part.
    pub(crate) fn xml(&self, part: &str) -> Result<Option<String>, String> {
        match self.zip.read(part) {
            Ok(Some(bytes)) => xml::text_of(&bytes).map(Some).ok_or_else(|| zip_message(self.kind, ZipError::TooLarge)),
            Ok(None) => Ok(None),
            Err(e) => Err(zip_message(self.kind, e)),
        }
    }

    /// The bytes of a part (an image); `None` when missing or unreadable.
    pub(crate) fn bytes(&self, part: &str) -> Option<Vec<u8>> {
        self.zip.read(part).ok().flatten()
    }

    pub(crate) fn damaged(&self, why: &str) -> String {
        damaged(self.kind, why)
    }
}

/// Convert an Office file to PDF. `name` gives the title (its stem) and, for legacy files, the
/// advice in the error.
pub fn from_office(name: &str, bytes: &[u8]) -> Result<Document, CreateError> {
    let title = name.rsplit_once('.').map_or(name, |(s, _)| s);
    let fail = |m: String| CreateError::Office(name.to_string(), m);
    let kind = office_kind(name, bytes).ok_or_else(|| fail("this isn't a Word, Excel, PowerPoint or RTF file".into()))?;
    let mut w = draw::Writer::new();
    match kind {
        OfficeKind::Legacy => {
            let advice = match extension(name).as_str() {
                "doc" | "dot" => "legacy Word .doc files aren't supported; open it in Word and save it as .docx",
                "xls" | "xlt" => "legacy Excel .xls files aren't supported; open it in Excel and save it as .xlsx",
                "ppt" | "pot" | "pps" => "legacy PowerPoint .ppt files aren't supported; open it in PowerPoint and save it as .pptx",
                _ => "legacy Office files (.doc, .xls, .ppt) aren't supported; save it as .docx, .xlsx or .pptx",
            };
            return Err(fail(advice.into()));
        }
        OfficeKind::Rtf => rtf::convert(bytes, &mut w).map_err(fail)?,
        OfficeKind::Word | OfficeKind::Excel | OfficeKind::PowerPoint => {
            let zip = Zip::open(bytes).map_err(|e| fail(zip_message(kind, e)))?;
            let (found, main) = main_part(&zip).ok_or_else(|| {
                fail(damaged(
                    kind,
                    match kind {
                        OfficeKind::Word => "no word/document.xml",
                        OfficeKind::Excel => "no xl/workbook.xml",
                        _ => "no ppt/presentation.xml",
                    },
                ))
            })?;
            let pkg = Package { zip, kind: found };
            match found {
                OfficeKind::Word => docx::convert(&pkg, &main, &mut w),
                OfficeKind::Excel => xlsx::convert(&pkg, &main, &mut w),
                _ => pptx::convert(&pkg, &main, &mut w),
            }
            .map_err(fail)?;
        }
    }
    w.finish(title)
}

#[cfg(test)]
mod tests;

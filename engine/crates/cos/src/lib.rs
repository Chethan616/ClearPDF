//! pdfcraft-cos — the PDF object layer (L1, standalone with `pdfcraft-filters`).
//!
//! Parses the COS object graph lazily and tolerantly, keeps edits in a copy-on-write overlay
//! (cheap snapshots for undo), and writes documents back either incrementally (original bytes
//! untouched) or as a full, garbage-collected rewrite. The PDF object graph *is* PdfCraft's
//! document model (plan/architecture.md §5, ADR-0010).
//!
//! Done: every cross-reference form (tables, streams, hybrid `/XRefStm`), object streams, repair
//! by scanning, encryption on load and save (R2–R6, crypt filters, `/EFF`), incremental writing,
//! and full saves that pack objects into compressed object streams with a cross-reference stream.
//! Not yet: streaming very large files within a memory budget (`ByteSource`,
//! `core.lazy-loading`), and linearized output (`core.linearization`, M11). See the README.

#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

mod budget;
mod bytes;
mod document;
mod object;
pub mod page_labels;
mod parser;
mod security;
mod writer;

pub use budget::{DecodeBudget, MAX_PAGE_DECODED, MAX_PAGE_FORMS};
pub use bytes::Bytes;
pub use document::{Document, ReadState, Revision, SourceIdentity, XrefEntry};
pub use object::{Dict, MAX_DECODED, Name, ObjRef, Object, PdfString, Stream};
pub use parser::{Lexer, is_whitespace, parse_indirect};
pub use pdfcraft_crypt::{
    Algorithm, Auth, Method as CryptMethod, NewEncryption, Permissions, SecurityHandler, effective_password, pdfdoc_byte, pdfdoc_char,
};
pub use writer::{SaveOptions, pdf_date, serialize, write_full, write_incremental};

#[derive(Debug, thiserror::Error, Clone, PartialEq)]
pub enum CosError {
    #[error("not a PDF file (no %PDF- header)")]
    NotPdf,
    #[error("syntax error at byte {offset}: {detail}")]
    Syntax { offset: usize, detail: String },
    #[error("object {0} is missing")]
    MissingObject(u32),
    #[error("object {0} is not a dictionary")]
    NotADictionary(ObjRef),
    #[error("stream data could not be decoded: {0}")]
    Filter(String),
    #[error("the document is protected by a password")]
    NeedsPassword,
    #[error("the password is incorrect")]
    WrongPassword,
    #[error("unsupported security: {0}")]
    Security(String),
    #[error("internal lock poisoned")]
    Poisoned,
    #[error("this document was opened read-only with a decode limit and cannot be saved")]
    ReadOnlyLimit,
}

impl CosError {
    /// Whether stream data was refused only because it decodes to more than the reader's cap
    /// (a resource guard, as for a decompression bomb), not because it is damaged. The filter
    /// error's text is what [`CosError::Filter`] carries.
    pub fn is_decode_limit(&self) -> bool {
        matches!(self, CosError::Filter(m) if m.starts_with("decoded output exceeds the limit"))
    }
}

#[cfg(test)]
mod decode_limit_tests {
    use super::CosError;

    #[test]
    fn a_decode_cap_is_told_from_damage() {
        assert!(CosError::Filter(pdfcraft_filters::FilterError::LimitExceeded(8).to_string()).is_decode_limit());
        assert!(!CosError::Filter("invalid deflate data".into()).is_decode_limit());
        assert!(!CosError::MissingObject(3).is_decode_limit());
    }
}

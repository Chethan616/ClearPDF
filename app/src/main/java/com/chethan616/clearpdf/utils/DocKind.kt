package com.chethan616.clearpdf.utils

/**
 * The original document family, derived from a file name's extension. The app converts non-PDF
 * documents to a PDF for viewing (which loses the original type), but the original display name is
 * preserved (e.g. "budget.xlsx") — so the extension is a reliable way to recover the kind and show
 * the right editor tools / route to the right viewer.
 */
enum class DocKind { Pdf, Word, Excel, Ppt, Image, Other }

/** Classify a file by its name/extension. Null/unknown → [DocKind.Other]. */
fun docKindOf(fileName: String?): DocKind {
    val n = (fileName ?: "").lowercase().trim()
    return when {
        n.endsWith(".pdf") -> DocKind.Pdf
        n.endsWith(".docx") || n.endsWith(".doc") || n.endsWith(".odt") || n.endsWith(".rtf") -> DocKind.Word
        n.endsWith(".xlsx") || n.endsWith(".xls") -> DocKind.Excel
        n.endsWith(".pptx") || n.endsWith(".ppt") -> DocKind.Ppt
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".heic") -> DocKind.Image
        else -> DocKind.Other
    }
}

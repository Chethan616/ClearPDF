package com.kyant.pdfcore.model

import android.net.Uri

/**
 * Represents a loaded PDF document throughout the app.
 *
 * @property uri        Content URI or file URI pointing to the PDF.
 * @property name       Human-readable file name (e.g. "Report.pdf").
 * @property pageCount  Total number of pages, or -1 if unknown.
 * @property sizeBytes  File size in bytes, or -1 if unknown.
 */
data class PdfDocument(
    val uri: Uri,
    val name: String,
    val pageCount: Int = -1,
    val sizeBytes: Long = -1
)

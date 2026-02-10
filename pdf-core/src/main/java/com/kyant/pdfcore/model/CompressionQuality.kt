package com.kyant.pdfcore.model

/**
 * Quality level used when compressing a PDF.
 */
enum class CompressionQuality {
    /** Smallest file size, lowest quality. */
    LOW,
    /** Balanced file size and quality. */
    MEDIUM,
    /** Best quality, moderate file size reduction. */
    HIGH
}

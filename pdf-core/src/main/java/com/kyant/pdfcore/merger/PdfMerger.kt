package com.kyant.pdfcore.merger

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument

/**
 * Merges two or more PDF files into a single output document.
 *
 * Implementations may use iText, PDFBox-Android, or a native solution.
 */
interface PdfMerger {

    /**
     * Merge a list of PDF [sources] into one document written to [outputUri].
     *
     * @param context    Application context for content resolution.
     * @param sources    Ordered list of PDFs to merge.
     * @param outputUri  Destination URI for the merged file.
     * @return A [PdfDocument] describing the merged output.
     */
    fun merge(
        context: Context,
        sources: List<PdfDocument>,
        outputUri: Uri
    ): PdfDocument

    /**
     * Merge only selected pages from each source.
     *
     * @param context    Application context.
     * @param sources    List of pairs: (document, page-indices to include).
     * @param outputUri  Destination URI.
     * @return A [PdfDocument] describing the merged output.
     */
    fun mergePages(
        context: Context,
        sources: List<Pair<PdfDocument, List<Int>>>,
        outputUri: Uri
    ): PdfDocument
}

/**
 * Default stub implementation.  Every method throws [NotImplementedError].
 */
class PdfMergerImpl : PdfMerger {

    override fun merge(
        context: Context,
        sources: List<PdfDocument>,
        outputUri: Uri
    ): PdfDocument {
        TODO("Merge full documents — iterate sources, copy all pages to outputUri")
    }

    override fun mergePages(
        context: Context,
        sources: List<Pair<PdfDocument, List<Int>>>,
        outputUri: Uri
    ): PdfDocument {
        TODO("Merge selected pages — iterate sources, copy specified page indices")
    }
}

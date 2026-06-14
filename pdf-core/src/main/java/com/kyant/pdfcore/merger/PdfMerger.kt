package com.kyant.pdfcore.merger

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.kyant.pdfcore.model.PdfDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * Merges two or more PDF files into a single output document.
 */
interface PdfMerger {

    /** Merge a list of PDF [sources] (whole documents) into one file at [outputUri]. */
    fun merge(context: Context, sources: List<PdfDocument>, outputUri: Uri): PdfDocument

    /** Merge only selected pages from each source. */
    fun mergePages(context: Context, sources: List<Pair<PdfDocument, List<Int>>>, outputUri: Uri): PdfDocument
}

/**
 * Lossless implementation using PdfBox — pages are copied with their original text and
 * vector content intact (no rasterisation, no quality loss, output stays small).
 */
class PdfMergerImpl : PdfMerger {

    override fun merge(context: Context, sources: List<PdfDocument>, outputUri: Uri): PdfDocument {
        PdfBox.ensureInitialized(context)
        if (sources.isEmpty()) throw IllegalArgumentException("No PDFs to merge")

        val merger = PDFMergerUtility()
        val outputStream = context.contentResolver.openOutputStream(outputUri)
            ?: throw IllegalStateException("Cannot write merged PDF output")

        var totalPages = 0
        outputStream.use { out ->
            merger.destinationStream = out
            for (src in sources) {
                val input = context.contentResolver.openInputStream(src.uri)
                    ?: throw IllegalStateException("Cannot read ${src.name}")
                merger.addSource(input)
                totalPages += countPages(context, src.uri)
            }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        }

        return PdfDocument(uri = outputUri, name = "Merged.pdf", pageCount = totalPages)
    }

    override fun mergePages(
        context: Context,
        sources: List<Pair<PdfDocument, List<Int>>>,
        outputUri: Uri
    ): PdfDocument {
        PdfBox.ensureInitialized(context)

        val output = PDDocument()
        var written = 0
        try {
            for ((src, pages) in sources) {
                val input = context.contentResolver.openInputStream(src.uri)
                    ?: throw IllegalStateException("Cannot read ${src.name}")
                input.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        for (i in pages) {
                            if (i in 0 until doc.numberOfPages) {
                                output.importPage(doc.getPage(i))
                                written++
                            }
                        }
                    }
                }
            }
            val out = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot write merged PDF output")
            out.use { output.save(it) }
        } finally {
            output.close()
        }

        return PdfDocument(uri = outputUri, name = "Merged.pdf", pageCount = written)
    }

    /** Cheap page count via the native renderer (no full PdfBox parse). */
    private fun countPages(context: Context, uri: Uri): Int = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { it.pageCount }
        } ?: 0
    } catch (_: Exception) {
        0
    }
}

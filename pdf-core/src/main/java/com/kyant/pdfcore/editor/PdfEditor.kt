package com.kyant.pdfcore.editor

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.kyant.pdfcore.model.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * Lossless page-level editing: reorder, delete, and rotate pages while preserving the
 * original text & vector content. Powers the in-app page organiser.
 */
interface PdfEditor {

    /**
     * Produce a new document from [source] whose pages are exactly [order] (a list of
     * original 0-based page indices). Omitting an index deletes that page; repeating it
     * duplicates it; any other ordering reorders. Each entry may carry an extra clockwise
     * rotation via [rotationByOriginalIndex] (degrees, added to the page's existing
     * rotation).
     */
    fun applyPageEdits(
        context: Context,
        source: PdfDocument,
        order: List<Int>,
        rotationByOriginalIndex: Map<Int, Int> = emptyMap(),
        outputUri: Uri
    ): PdfDocument
}

class PdfEditorImpl : PdfEditor {

    override fun applyPageEdits(
        context: Context,
        source: PdfDocument,
        order: List<Int>,
        rotationByOriginalIndex: Map<Int, Int>,
        outputUri: Uri
    ): PdfDocument {
        PdfBox.ensureInitialized(context)
        if (order.isEmpty()) throw IllegalArgumentException("Result would have no pages")

        val output = PDDocument()
        var written = 0
        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IllegalArgumentException("Cannot open source")
            input.use { stream ->
                PDDocument.load(stream).use { doc ->
                    for (orig in order) {
                        if (orig !in 0 until doc.numberOfPages) continue
                        val srcPage = doc.getPage(orig)
                        val imported = output.importPage(srcPage)
                        val extra = rotationByOriginalIndex[orig] ?: 0
                        if (extra != 0) {
                            imported.rotation = (((srcPage.rotation + extra) % 360) + 360) % 360
                        }
                        written++
                    }
                }
            }
            if (written == 0) throw IllegalArgumentException("Result would have no pages")
            val out = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot write edited PDF output")
            out.use { output.save(it) }
        } finally {
            output.close()
        }
        return PdfDocument(uri = outputUri, name = "Edited.pdf", pageCount = written)
    }
}

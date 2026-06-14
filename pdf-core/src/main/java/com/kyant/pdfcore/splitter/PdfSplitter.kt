package com.kyant.pdfcore.splitter

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.kyant.pdfcore.model.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * Splits a PDF into multiple documents (lossless — original page content is preserved).
 */
interface PdfSplitter {

    /** Split a PDF into one single-page PDF per page, written into the cache dir. */
    fun splitAll(context: Context, source: PdfDocument, outputDir: Uri): List<PdfDocument>

    /** Extract specific [pages] from a PDF into a new document at [outputUri]. */
    fun extractPages(context: Context, source: PdfDocument, pages: List<Int>, outputUri: Uri): PdfDocument
}

class PdfSplitterImpl : PdfSplitter {

    override fun splitAll(context: Context, source: PdfDocument, outputDir: Uri): List<PdfDocument> {
        PdfBox.ensureInitialized(context)
        val results = mutableListOf<PdfDocument>()
        val baseName = source.name.removeSuffix(".pdf").ifBlank { "Document" }
        val splitDir = File(context.cacheDir, "split").apply { mkdirs() }

        val input = context.contentResolver.openInputStream(source.uri)
            ?: throw IllegalArgumentException("Cannot open source")
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                for (i in 0 until doc.numberOfPages) {
                    PDDocument().use { single ->
                        single.importPage(doc.getPage(i))
                        val fileName = "${baseName}_page${i + 1}.pdf"
                        val outFile = File(splitDir, fileName)
                        outFile.outputStream().use { single.save(it) }
                        results.add(PdfDocument(uri = Uri.fromFile(outFile), name = fileName, pageCount = 1))
                    }
                }
            }
        }
        return results
    }

    override fun extractPages(
        context: Context,
        source: PdfDocument,
        pages: List<Int>,
        outputUri: Uri
    ): PdfDocument {
        PdfBox.ensureInitialized(context)
        var written = 0
        val output = PDDocument()
        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IllegalArgumentException("Cannot open source")
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
            val out = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot write split PDF output")
            out.use { output.save(it) }
        } finally {
            output.close()
        }
        return PdfDocument(uri = outputUri, name = "Split.pdf", pageCount = written)
    }
}

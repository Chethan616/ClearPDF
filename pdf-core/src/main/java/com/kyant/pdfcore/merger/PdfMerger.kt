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
 * Real implementation using Android's PdfRenderer + PdfDocument APIs.
 */
class PdfMergerImpl : PdfMerger {

    override fun merge(
        context: Context,
        sources: List<PdfDocument>,
        outputUri: Uri
    ): PdfDocument {
        val outDoc = android.graphics.pdf.PdfDocument()
        var globalPage = 0

        for (src in sources) {
            val fd = context.contentResolver.openFileDescriptor(src.uri, "r") ?: continue
            val renderer = android.graphics.pdf.PdfRenderer(fd)

            for (i in 0 until renderer.pageCount) {
                val srcPage = renderer.openPage(i)
                val w = srcPage.width
                val h = srcPage.height
                val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                srcPage.close()

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, globalPage++).create()
                val page = outDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                outDoc.finishPage(page)
                bitmap.recycle()
            }
            renderer.close()
            fd.close()
        }

        context.contentResolver.openOutputStream(outputUri)?.use { outDoc.writeTo(it) }
        outDoc.close()

        return PdfDocument(uri = outputUri, name = "Merged.pdf", pageCount = globalPage)
    }

    override fun mergePages(
        context: Context,
        sources: List<Pair<PdfDocument, List<Int>>>,
        outputUri: Uri
    ): PdfDocument {
        val outDoc = android.graphics.pdf.PdfDocument()
        var globalPage = 0

        for ((src, pages) in sources) {
            val fd = context.contentResolver.openFileDescriptor(src.uri, "r") ?: continue
            val renderer = android.graphics.pdf.PdfRenderer(fd)

            for (i in pages) {
                if (i < 0 || i >= renderer.pageCount) continue
                val srcPage = renderer.openPage(i)
                val w = srcPage.width
                val h = srcPage.height
                val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                srcPage.close()

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, globalPage++).create()
                val page = outDoc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                outDoc.finishPage(page)
                bitmap.recycle()
            }
            renderer.close()
            fd.close()
        }

        context.contentResolver.openOutputStream(outputUri)?.use { outDoc.writeTo(it) }
        outDoc.close()

        return PdfDocument(uri = outputUri, name = "Merged.pdf", pageCount = globalPage)
    }
}

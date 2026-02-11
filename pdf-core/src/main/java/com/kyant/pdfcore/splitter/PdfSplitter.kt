package com.kyant.pdfcore.splitter

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument

/**
 * Splits a PDF into multiple documents.
 */
interface PdfSplitter {
    /**
     * Split a PDF into individual page PDFs.
     */
    fun splitAll(context: Context, source: PdfDocument, outputDir: Uri): List<PdfDocument>

    /**
     * Extract specific pages from a PDF into a new document.
     */
    fun extractPages(context: Context, source: PdfDocument, pages: List<Int>, outputUri: Uri): PdfDocument
}

class PdfSplitterImpl : PdfSplitter {

    override fun splitAll(context: Context, source: PdfDocument, outputDir: Uri): List<PdfDocument> {
        // Split into individual pages - each saved via content resolver
        val results = mutableListOf<PdfDocument>()
        val fd = context.contentResolver.openFileDescriptor(source.uri, "r") ?: return results
        val renderer = android.graphics.pdf.PdfRenderer(fd)

        for (i in 0 until renderer.pageCount) {
            val srcPage = renderer.openPage(i)
            val w = srcPage.width
            val h = srcPage.height
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            srcPage.close()

            val outDoc = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, 0).create()
            val page = outDoc.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            outDoc.finishPage(page)
            bitmap.recycle()

            // Write to cache and record
            val baseName = source.name.removeSuffix(".pdf")
            val fileName = "${baseName}_page${i + 1}.pdf"
            val outFile = java.io.File(context.cacheDir, fileName)
            outFile.outputStream().use { outDoc.writeTo(it) }
            outDoc.close()

            results.add(PdfDocument(uri = Uri.fromFile(outFile), name = fileName, pageCount = 1))
        }
        renderer.close()
        fd.close()
        return results
    }

    override fun extractPages(context: Context, source: PdfDocument, pages: List<Int>, outputUri: Uri): PdfDocument {
        val fd = context.contentResolver.openFileDescriptor(source.uri, "r")
            ?: throw IllegalArgumentException("Cannot open source")
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        val outDoc = android.graphics.pdf.PdfDocument()

        pages.forEachIndexed { index, pageIdx ->
            if (pageIdx < 0 || pageIdx >= renderer.pageCount) return@forEachIndexed
            val srcPage = renderer.openPage(pageIdx)
            val w = srcPage.width
            val h = srcPage.height
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            srcPage.close()

            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, index).create()
            val page = outDoc.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            outDoc.finishPage(page)
            bitmap.recycle()
        }
        renderer.close()
        fd.close()

        context.contentResolver.openOutputStream(outputUri)?.use { outDoc.writeTo(it) }
        outDoc.close()

        return PdfDocument(uri = outputUri, name = "Split.pdf", pageCount = pages.size)
    }
}

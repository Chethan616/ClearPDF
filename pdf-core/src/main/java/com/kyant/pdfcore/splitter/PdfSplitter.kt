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
        context.contentResolver.openFileDescriptor(source.uri, "r")?.use { fd ->
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            var reusableBitmap: android.graphics.Bitmap? = null
            try {
                for (i in 0 until renderer.pageCount) {
                    val srcPage = renderer.openPage(i)
                    try {
                        val w = srcPage.width
                        val h = srcPage.height
                        val bitmap = obtainReusableBitmap(reusableBitmap, w, h)
                        if (bitmap !== reusableBitmap) {
                            reusableBitmap?.recycle()
                            reusableBitmap = bitmap
                        }
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                        val outDoc = android.graphics.pdf.PdfDocument()
                        try {
                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, 0).create()
                            val page = outDoc.startPage(pageInfo)
                            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            outDoc.finishPage(page)

                            // Write to cache and record
                            val baseName = source.name.removeSuffix(".pdf")
                            val fileName = "${baseName}_page${i + 1}.pdf"
                            val outFile = java.io.File(context.cacheDir, fileName)
                            outFile.outputStream().use {
                                outDoc.writeTo(it)
                                it.flush()
                            }
                            results.add(PdfDocument(uri = Uri.fromFile(outFile), name = fileName, pageCount = 1))
                        } finally {
                            outDoc.close()
                        }
                    } finally {
                        srcPage.close()
                    }
                }
            } finally {
                reusableBitmap?.recycle()
                renderer.close()
            }
        } ?: return results

        return results
    }

    override fun extractPages(context: Context, source: PdfDocument, pages: List<Int>, outputUri: Uri): PdfDocument {
        val outDoc = android.graphics.pdf.PdfDocument()
        var writtenPages = 0

        try {
            context.contentResolver.openFileDescriptor(source.uri, "r")?.use { fd ->
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                var reusableBitmap: android.graphics.Bitmap? = null
                try {
                    for (pageIdx in pages) {
                        if (pageIdx < 0 || pageIdx >= renderer.pageCount) continue
                        val srcPage = renderer.openPage(pageIdx)
                        try {
                            val w = srcPage.width
                            val h = srcPage.height
                            val bitmap = obtainReusableBitmap(reusableBitmap, w, h)
                            if (bitmap !== reusableBitmap) {
                                reusableBitmap?.recycle()
                                reusableBitmap = bitmap
                            }
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            srcPage.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, writtenPages).create()
                            val page = outDoc.startPage(pageInfo)
                            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            outDoc.finishPage(page)
                            writtenPages += 1
                        } finally {
                            srcPage.close()
                        }
                    }
                } finally {
                    reusableBitmap?.recycle()
                    renderer.close()
                }
            } ?: throw IllegalArgumentException("Cannot open source")

            val splitOutput = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot write split PDF output")
            splitOutput.use {
                outDoc.writeTo(it)
                it.flush()
            }
        } finally {
            outDoc.close()
        }

        return PdfDocument(uri = outputUri, name = "Split.pdf", pageCount = writtenPages)
    }

    private fun obtainReusableBitmap(
        existing: android.graphics.Bitmap?,
        width: Int,
        height: Int
    ): android.graphics.Bitmap {
        if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
            return existing
        }
        return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    }
}

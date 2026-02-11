package com.kyant.pdfcore.creator

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument

/**
 * Creates new PDF documents from text, images, or blank pages.
 *
 * Implementations will use Android's PdfDocument API or a third-party library.
 */
interface PdfCreator {

    /**
     * Create a blank PDF with the given number of pages.
     *
     * @param context    Application context.
     * @param pageCount  Number of blank pages to create.
     * @param outputUri  Destination URI for the new file.
     * @return A [PdfDocument] describing the created file.
     */
    fun createBlank(
        context: Context,
        pageCount: Int,
        outputUri: Uri
    ): PdfDocument

    /**
     * Create a PDF from a list of images (one image per page).
     *
     * @param context    Application context.
     * @param images     Ordered list of bitmaps to convert to pages.
     * @param outputUri  Destination URI.
     * @return A [PdfDocument] describing the created file.
     */
    fun createFromImages(
        context: Context,
        images: List<Bitmap>,
        outputUri: Uri
    ): PdfDocument

    /**
     * Create a single-page PDF containing the given [text].
     *
     * @param context    Application context.
     * @param text       Text content to render.
     * @param outputUri  Destination URI.
     * @return A [PdfDocument] describing the created file.
     */
    fun createFromText(
        context: Context,
        text: String,
        outputUri: Uri
    ): PdfDocument
}

/**
 * Default stub implementation.  Every method throws [NotImplementedError].
 */
class PdfCreatorImpl : PdfCreator {

    override fun createBlank(
        context: Context,
        pageCount: Int,
        outputUri: Uri
    ): PdfDocument {
        val doc = android.graphics.pdf.PdfDocument()
        for (i in 0 until pageCount) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, i).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawColor(android.graphics.Color.WHITE)
            doc.finishPage(page)
        }
        context.contentResolver.openOutputStream(outputUri)?.use { doc.writeTo(it) }
        doc.close()
        return PdfDocument(uri = outputUri, name = "Blank.pdf", pageCount = pageCount)
    }

    override fun createFromImages(
        context: Context,
        images: List<Bitmap>,
        outputUri: Uri
    ): PdfDocument {
        val doc = android.graphics.pdf.PdfDocument()
        images.forEachIndexed { index, bitmap ->
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index).create()
            val page = doc.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            doc.finishPage(page)
        }
        context.contentResolver.openOutputStream(outputUri)?.use { doc.writeTo(it) }
        doc.close()
        return PdfDocument(uri = outputUri, name = "Created.pdf", pageCount = images.size)
    }

    override fun createFromText(
        context: Context,
        text: String,
        outputUri: Uri
    ): PdfDocument {
        val doc = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 72f
        val textPaint = android.text.TextPaint().apply {
            textSize = 14f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }
        val usableWidth = (pageWidth - margin * 2).toInt()

        val lines = text.split("\n")
        var yPos = margin
        var pageIndex = 0
        var currentPage: android.graphics.pdf.PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null

        fun startNewPage() {
            currentPage?.let { doc.finishPage(it) }
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex++).create()
            currentPage = doc.startPage(pageInfo)
            canvas = currentPage!!.canvas
            canvas!!.drawColor(android.graphics.Color.WHITE)
            yPos = margin
        }
        startNewPage()

        for (line in lines) {
            val sl = android.text.StaticLayout.Builder.obtain(line, 0, line.length, textPaint, usableWidth).build()
            if (yPos + sl.height > pageHeight - margin) startNewPage()
            canvas!!.save()
            canvas!!.translate(margin, yPos)
            sl.draw(canvas!!)
            canvas!!.restore()
            yPos += sl.height + 4f
        }
        currentPage?.let { doc.finishPage(it) }

        context.contentResolver.openOutputStream(outputUri)?.use { doc.writeTo(it) }
        doc.close()
        return PdfDocument(uri = outputUri, name = "Created.pdf", pageCount = pageIndex)
    }
}

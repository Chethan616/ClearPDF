package com.kyant.pdfcore.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.kyant.pdfcore.model.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Conversions between PDF and other formats.
 */
interface PdfConverter {

    /**
     * Build a PDF from a list of image [imageUris], one image per page.
     *
     * @param fitToA4 when true each page is A4 and the image is centred/scaled to fit;
     *                when false each page matches its image's pixel dimensions.
     * @param jpegQuality 0..100 quality for the embedded JPEGs.
     */
    fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputUri: Uri,
        fitToA4: Boolean = true,
        jpegQuality: Int = 85
    ): PdfDocument

    /** Extract all text from [source] (empty string for scanned/imaged PDFs without a text layer). */
    fun extractText(context: Context, source: PdfDocument): String
}

class PdfConverterImpl : PdfConverter {

    override fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputUri: Uri,
        fitToA4: Boolean,
        jpegQuality: Int
    ): PdfDocument {
        PdfBox.ensureInitialized(context)
        if (imageUris.isEmpty()) throw IllegalArgumentException("No images to convert")

        val doc = PDDocument()
        var pages = 0
        try {
            for (uri in imageUris) {
                val bitmap = decodeBitmap(context, uri) ?: continue
                try {
                    val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQuality / 100f)
                    val page = if (fitToA4) {
                        PDPage(PDRectangle.A4)
                    } else {
                        PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                    }
                    doc.addPage(page)

                    PDPageContentStream(doc, page).use { content ->
                        val box = page.mediaBox
                        if (fitToA4) {
                            val scale = minOf(box.width / bitmap.width, box.height / bitmap.height)
                            val w = bitmap.width * scale
                            val h = bitmap.height * scale
                            val x = (box.width - w) / 2f
                            val y = (box.height - h) / 2f
                            content.drawImage(pdImage, x, y, w, h)
                        } else {
                            content.drawImage(pdImage, 0f, 0f, box.width, box.height)
                        }
                    }
                    pages++
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            if (pages == 0) throw IllegalStateException("No readable images")
            val out = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot write PDF output")
            out.use { doc.save(it) }
        } finally {
            doc.close()
        }
        return PdfDocument(uri = outputUri, name = "Converted.pdf", pageCount = pages)
    }

    override fun extractText(context: Context, source: PdfDocument): String {
        PdfBox.ensureInitialized(context)
        val input = context.contentResolver.openInputStream(source.uri)
            ?: throw IllegalArgumentException("Cannot open source")
        return input.use { stream ->
            PDDocument.load(stream).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }
}

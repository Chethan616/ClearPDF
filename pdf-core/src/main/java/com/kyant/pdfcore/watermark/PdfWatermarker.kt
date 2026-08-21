package com.kyant.pdfcore.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.min

/**
 * Stamps a repeated text watermark onto every page of a PDF via PDFBox. The source URI is
 * never modified; the result is written to [destinationUri]. All work runs on the caller's
 * worker thread (same contract as the other pdf-core services).
 *
 * Original implementation for ClearPDF — modeled on the app's existing text-stamp export path
 * (PDExtendedGraphicsState alpha + PDType1Font.showText), adapted to a page-centered watermark.
 */
object PdfWatermarker {

    /**
     * @param text      the watermark string (blank is a no-op that still copies the file).
     * @param opacity   0..1 fill alpha for the text.
     * @param diagonal  true = 45° watermark centered on the page; false = horizontal centered.
     * @param colorArgb watermark colour (default a neutral grey).
     */
    fun apply(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        text: String,
        opacity: Float,
        diagonal: Boolean,
        colorArgb: Int = 0xFF8A8A8A.toInt()
    ) {
        PdfBox.ensureInitialized(context)
        val sanitized = sanitize(text)
        val c = Color.valueOf(colorArgb)
        val alpha = opacity.coerceIn(0.05f, 1f)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                if (sanitized.isNotBlank()) {
                    val font = PDType1Font.HELVETICA_BOLD
                    for (i in 0 until doc.numberOfPages) {
                        val page = doc.getPage(i)
                        val boxRect = page.cropBox ?: page.mediaBox ?: continue
                        val w = boxRect.width
                        val h = boxRect.height
                        val originX = boxRect.lowerLeftX
                        val originY = boxRect.lowerLeftY
                        // Scale font to the page's short edge so the watermark reads on any size.
                        val fontSize = (min(w, h) * 0.11f).coerceIn(18f, 120f)
                        val textWidth = font.getStringWidth(sanitized) / 1000f * fontSize
                        val cx = originX + w / 2f
                        val cy = originY + h / 2f

                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.saveGraphicsState()
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            if (diagonal) {
                                // Rotate the text frame 45° about the page centre, then shift so the
                                // string's midpoint sits on the centre.
                                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy))
                                cs.newLineAtOffset(-textWidth / 2f, -fontSize / 2.6f)
                            } else {
                                cs.newLineAtOffset(cx - textWidth / 2f, cy - fontSize / 2.6f)
                            }
                            runCatching { cs.showText(sanitized) }
                            cs.endText()
                            cs.restoreGraphicsState()
                        }
                    }
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
    }

    /**
     * Stamp an [bitmap] image watermark (e.g. a logo) centered on every page.
     *
     * @param opacity  0..1 image alpha.
     * @param diagonal true = rotate the image 45° about the page centre.
     * @param widthFraction the watermark width as a fraction of the page width.
     */
    fun applyImage(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        bitmap: Bitmap,
        opacity: Float,
        diagonal: Boolean,
        widthFraction: Float = 0.45f
    ) {
        PdfBox.ensureInitialized(context)
        val alpha = opacity.coerceIn(0.05f, 1f)
        val ratio = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val image = LosslessFactory.createFromImage(doc, bitmap)
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox ?: continue
                    val w = box.width
                    val h = box.height
                    val cx = box.lowerLeftX + w / 2f
                    val cy = box.lowerLeftY + h / 2f
                    val drawW = w * widthFraction.coerceIn(0.1f, 1f)
                    val drawH = drawW * ratio

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.saveGraphicsState()
                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = alpha
                            strokingAlphaConstant = alpha
                        }
                        cs.setGraphicsStateParameters(gs)
                        if (diagonal) {
                            cs.transform(Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy))
                            cs.drawImage(image, -drawW / 2f, -drawH / 2f, drawW, drawH)
                        } else {
                            cs.drawImage(image, cx - drawW / 2f, cy - drawH / 2f, drawW, drawH)
                        }
                        cs.restoreGraphicsState()
                    }
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
    }

    /** PDFBox's WinAnsi encoding rejects unsupported glyphs; keep to Latin-1. */
    private fun sanitize(text: String): String =
        buildString { text.forEach { ch -> append(if (ch.code in 32..255) ch else '?') } }
}

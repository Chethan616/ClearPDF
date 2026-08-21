package com.kyant.pdfcore.pagenumber

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlin.math.min

/**
 * Stamps a page number onto the bottom margin of every page via PDFBox. The source URI is
 * never modified; the result is written to [destinationUri]. Runs on the caller's worker
 * thread (same contract as the other pdf-core services). Original ClearPDF code, built on
 * the same text-stamp path as PdfWatermarker.
 */
object PdfPageNumberer {

    enum class Position { CENTER, RIGHT }

    /**
     * @param position     bottom-center or bottom-right.
     * @param includeTotal true renders "3 / 12"; false renders "3".
     * @param startAt      the number printed on the first page (default 1).
     */
    fun apply(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        position: Position,
        includeTotal: Boolean,
        startAt: Int = 1,
        colorArgb: Int = 0xFF444444.toInt()
    ) {
        PdfBox.ensureInitialized(context)
        val c = Color.valueOf(colorArgb)
        val font = PDType1Font.HELVETICA

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox ?: continue
                    val w = box.width
                    val h = box.height
                    val originX = box.lowerLeftX
                    val originY = box.lowerLeftY
                    val fontSize = (min(w, h) * 0.018f).coerceIn(9f, 14f)
                    val margin = fontSize * 2.2f
                    val text = if (includeTotal) "${startAt + i} / ${startAt + total - 1}" else "${startAt + i}"
                    val textWidth = font.getStringWidth(text) / 1000f * fontSize
                    val x = when (position) {
                        Position.CENTER -> originX + w / 2f - textWidth / 2f
                        Position.RIGHT  -> originX + w - margin - textWidth
                    }
                    val y = originY + margin - fontSize / 2f

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                        cs.setFont(font, fontSize)
                        cs.newLineAtOffset(x, y)
                        runCatching { cs.showText(text) }
                        cs.endText()
                    }
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
    }
}

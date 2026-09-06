package com.kyant.pdfcore.searchable

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix

/**
 * A recognized word to stamp as invisible, searchable text. Coordinates are page-normalized
 * (0..1), top-left origin — the same convention [com.kyant.pdfcore.text.PdfTextBlock] and
 * `com.kyant.ocrcore.OcrWord` already use.
 */
data class InvisibleWord(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Bakes OCR results into a PDF as an invisible (`Tr 3`) text layer, so the output is
 * selectable/searchable/copyable in ANY PDF reader, not just ClearPDF — the same technique
 * tools like OCRmyPDF use. Pure PDFBox; the caller supplies already-recognized words (see
 * `com.kyant.ocrcore.OcrService`), so this object carries no OCR dependency of its own and
 * pdf-core stays focused on PDF I/O.
 */
object PdfSearchableStamper {

    /** The source is never modified; the result (original content + invisible text) is written to [destinationUri]. */
    fun stamp(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        wordsByPage: Map<Int, List<InvisibleWord>>
    ) {
        PdfBox.ensureInitialized(context)
        val font = PDType1Font.HELVETICA

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                for ((pageIndex, words) in wordsByPage) {
                    if (words.isEmpty() || pageIndex !in 0 until doc.numberOfPages) continue
                    val page = doc.getPage(pageIndex)
                    val box = page.cropBox ?: page.mediaBox ?: continue
                    val w = box.width
                    val h = box.height
                    val originX = box.lowerLeftX
                    val originY = box.lowerLeftY
                    if (w <= 0f || h <= 0f) continue

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        words.forEach word@{ word ->
                            val sanitized = sanitize(word.text)
                            if (sanitized.isBlank()) return@word
                            val wordWpt = ((word.right - word.left) * w).coerceAtLeast(0.01f)
                            val wordHpt = ((word.bottom - word.top) * h).coerceAtLeast(0.01f)
                            val fontSize = wordHpt.coerceIn(3f, 400f)
                            val rawWidth = font.getStringWidth(sanitized) / 1000f * fontSize
                            // Horizontally scale the glyphs to match the OCR box's measured width, so a
                            // reader's own "highlight the match" rectangle lines up with the scanned text.
                            val scaleX = if (rawWidth > 0.01f) (wordWpt / rawWidth).coerceIn(0.05f, 20f) else 1f
                            val x = originX + word.left * w
                            // Normalized `top`/`bottom` are measured from the page's visual top; PDF
                            // text-space is bottom-up, so flip and anchor at the word's baseline-ish bottom.
                            val y = originY + h - word.bottom * h

                            runCatching {
                                cs.beginText()
                                cs.setRenderingMode(RenderingMode.NEITHER)
                                cs.setFont(font, fontSize)
                                cs.setTextMatrix(Matrix(scaleX, 0f, 0f, 1f, x, y))
                                cs.showText(sanitized)
                                cs.endText()
                            }
                        }
                    }
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
    }

    /** PDFBox's WinAnsi encoding rejects unsupported glyphs; keep to Latin-1 (mirrors PdfWatermarker). */
    private fun sanitize(text: String): String =
        buildString { text.forEach { ch -> append(if (ch.code in 32..255) ch else ' ') } }.trim()
}

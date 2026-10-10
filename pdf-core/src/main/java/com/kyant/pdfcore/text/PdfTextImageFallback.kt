package com.kyant.pdfcore.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix

/**
 * The text editor's last resort, kept apart from the PDF logic because it is the only Android-only
 * part: scripts no available PDF font can draw (or that need shaping, which PDF text output doesn't
 * do: Devanagari, Arabic…) are laid out by Android with full shaping and font fallback, and placed
 * on the baseline as an image.
 */
internal object PdfTextImageFallback {

    /** Draws [text] on [trm]'s baseline; returns its advance in [trm]'s units (0 if nothing drawn). */
    fun draw(
        doc: PDDocument, cs: PDPageContentStream, text: String, trm: Matrix, rgb: Int,
        bold: Boolean, italic: Boolean, serif: Boolean, mono: Boolean
    ): Float {
        val pt = kotlin.math.hypot(trm.getValue(1, 0), trm.getValue(1, 1))
        if (pt <= 0f) return 0f
        val scale = 4f // px per pt: sharp at print zoom
        val family = when { mono -> Typeface.MONOSPACE; serif -> Typeface.SERIF; else -> Typeface.SANS_SERIF }
        val style = when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = pt * scale
            color = 0xFF000000.toInt() or rgb
            typeface = Typeface.create(family, style)
        }
        val fm = paint.fontMetrics
        val w = paint.measureText(text).coerceAtLeast(1f)
        val h = (fm.descent - fm.ascent).coerceAtLeast(1f)
        val bmp = Bitmap.createBitmap(w.toInt() + 2, h.toInt() + 2, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 1f, -fm.ascent + 1f, paint)
        val image = runCatching { LosslessFactory.createFromImage(doc, bmp) }.getOrNull() ?: return 0f
        bmp.recycle()
        val x = trm.translateX
        val y = trm.translateY - (fm.descent / scale)
        cs.drawImage(image, x, y, (w + 2f) / scale, (h + 2f) / scale)
        val unit = kotlin.math.hypot(trm.getValue(0, 0), trm.getValue(0, 1))
        return if (unit > 0f) (w / scale) / unit else 0f
    }
}

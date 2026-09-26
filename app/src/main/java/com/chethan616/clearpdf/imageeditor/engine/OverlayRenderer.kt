package com.chethan616.clearpdf.imageeditor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Text overlays and watermarks. Watermark placement (nine anchors + tiled, alpha, rotation,
 * padding) follows ImageToolbox's feature/watermarking (Apache-2.0), implemented natively here
 * instead of through the androidwm library.
 */
object OverlayRenderer {

    fun typeface(font: TextFont, bold: Boolean, italic: Boolean): Typeface {
        val family = when (font) {
            TextFont.Sans -> Typeface.SANS_SERIF
            TextFont.Serif -> Typeface.SERIF
            TextFont.Mono -> Typeface.MONOSPACE
            TextFont.Condensed -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            TextFont.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(family, style)
    }

    fun drawText(base: Bitmap, t: TextLayer): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        drawTextOn(Canvas(out), out.width, out.height, t)
        return out
    }

    /** Also used by the live preview overlay so on-screen and exported text match. */
    fun drawTextOn(canvas: Canvas, w: Int, h: Int, t: TextLayer) {
        if (t.text.isBlank()) return
        val short = min(w, h).toFloat()
        val size = (t.size * short).coerceAtLeast(4f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            typeface = typeface(t.font, t.bold, t.italic)
            color = t.color
            alpha = (android.graphics.Color.alpha(t.color) * t.alpha).toInt()
            textAlign = Paint.Align.CENTER
        }
        val lines = t.text.split('\n')
        val fm = fill.fontMetrics
        val lineH = fm.descent - fm.ascent
        val totalH = lineH * lines.size
        val maxW = lines.maxOf { fill.measureText(it) }
        val cx = t.center.x * w
        val cy = t.center.y * h
        canvas.save()
        canvas.rotate(t.rotation, cx, cy)
        if (t.backgroundColor != 0) {
            val pad = size * 0.3f
            val r = RectF(cx - maxW / 2 - pad, cy - totalH / 2 - pad * 0.6f, cx + maxW / 2 + pad, cy + totalH / 2 + pad * 0.6f)
            canvas.drawRoundRect(r, pad, pad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.backgroundColor
                alpha = (android.graphics.Color.alpha(t.backgroundColor) * t.alpha).toInt()
            })
        }
        val stroke = if (t.outlineColor != 0) Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * t.outlineWidth
            strokeJoin = Paint.Join.ROUND
            color = t.outlineColor
            alpha = (android.graphics.Color.alpha(t.outlineColor) * t.alpha).toInt()
        } else null
        var baseline = cy - totalH / 2 - fm.ascent
        for (line in lines) {
            stroke?.let { canvas.drawText(line, cx, baseline, it) }
            canvas.drawText(line, cx, baseline, fill)
            baseline += lineH
        }
        canvas.restore()
    }

    fun drawWatermark(base: Bitmap, p: WatermarkParams): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val short = min(w, h).toFloat()
        val canvas = Canvas(out)
        val stamp = stampBitmap(p, short) ?: return out
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = (p.alpha * 255).toInt() }
        val margin = p.spacing * short
        if (p.position == WatermarkPosition.Tiled) {
            val stepX = stamp.width + margin * 2
            val stepY = stamp.height + margin * 2
            canvas.save()
            canvas.rotate(p.rotation, w / 2f, h / 2f)
            val diag = kotlin.math.hypot(w.toFloat(), h.toFloat())
            var y = (h - diag) / 2f
            var row = 0
            while (y < (h + diag) / 2f) {
                var x = (w - diag) / 2f + if (row % 2 == 1) stepX / 2f else 0f
                while (x < (w + diag) / 2f) {
                    canvas.drawBitmap(stamp, x, y, paint)
                    x += stepX
                }
                y += stepY
                row++
            }
            canvas.restore()
        } else {
            val sw = stamp.width.toFloat()
            val sh = stamp.height.toFloat()
            val x = when (p.position) {
                WatermarkPosition.TopLeft, WatermarkPosition.CenterLeft, WatermarkPosition.BottomLeft -> margin
                WatermarkPosition.TopRight, WatermarkPosition.CenterRight, WatermarkPosition.BottomRight -> w - sw - margin
                else -> (w - sw) / 2f
            }
            val y = when (p.position) {
                WatermarkPosition.TopLeft, WatermarkPosition.TopCenter, WatermarkPosition.TopRight -> margin
                WatermarkPosition.BottomLeft, WatermarkPosition.BottomCenter, WatermarkPosition.BottomRight -> h - sh - margin
                else -> (h - sh) / 2f
            }
            canvas.save()
            canvas.rotate(p.rotation, x + sw / 2, y + sh / 2)
            canvas.drawBitmap(stamp, x, y, paint)
            canvas.restore()
        }
        stamp.recycle()
        return out
    }

    private fun stampBitmap(p: WatermarkParams, short: Float): Bitmap? {
        p.image?.let { img ->
            val tw = max(1, (p.size * 4f * short).toInt())
            val th = max(1, (img.height * tw.toFloat() / img.width).toInt())
            return Bitmap.createScaledBitmap(img, tw, th, true)
        }
        if (p.text.isBlank()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (p.size * short).coerceAtLeast(6f)
            color = p.color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(textSize / 14f, 0f, textSize / 24f, 0x66000000)
        }
        val fm = paint.fontMetrics
        val tw = max(1, (paint.measureText(p.text) + paint.textSize * 0.3f).toInt())
        val th = max(1, (fm.descent - fm.ascent + paint.textSize * 0.2f).toInt())
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(p.text, paint.textSize * 0.15f, -fm.ascent + paint.textSize * 0.1f, paint)
        return bmp
    }
}

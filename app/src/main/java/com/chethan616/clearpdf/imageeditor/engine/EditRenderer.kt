package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.chethan616.clearpdf.imageeditor.engine.draw.ShapePaths
import com.chethan616.clearpdf.imageeditor.engine.draw.StrokeRenderer
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.crop.CropAgent
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.RectCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.freecorners.FreeCrop
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Applies one [EditOp] to a bitmap. Every function returns a **new** bitmap and never recycles
 * its input — the caller (render cache / exporter) owns lifetimes.
 *
 * @param resolutionScale ratio of the bitmap being rendered to the full-resolution source
 *        (1 for export, < 1 for the preview). Only [ResizeOp] needs it.
 */
class EditRenderer(private val context: Context) {

    private val cropAgent = CropAgent()
    private val density = Density(context)

    fun apply(src: Bitmap, op: EditOp, resolutionScale: Float): Bitmap = when (op) {
        is TransformOp -> transform(src, op)
        is CropOp -> crop(src, op)
        is PerspectiveOp -> FreeCrop.crop(src, op.corners.map { Offset(it.x * src.width, it.y * src.height) })
        is AdjustOp -> AdjustRenderer.apply(context, src, op.values).let { if (it === src) src.copy(Bitmap.Config.ARGB_8888, true) else it }
        is FilterOp -> FilterPreset.apply(context, src, op.preset, op.intensity)
        is DrawOp -> StrokeRenderer.render(src, op.strokes)
        is TextOp -> OverlayRenderer.drawText(src, op.layer)
        is WatermarkOp -> OverlayRenderer.drawWatermark(src, op.params)
        is BackgroundOp -> background(src, op)
        is ResizeOp -> {
            val w = max(1, (op.width * resolutionScale).roundToInt())
            val h = max(1, (op.height * resolutionScale).roundToInt())
            Bitmap.createScaledBitmap(src, w, h, true).let { if (it === src) src.copy(Bitmap.Config.ARGB_8888, true) else it }
        }
    }

    fun transform(src: Bitmap, op: TransformOp): Bitmap {
        val turns = Math.floorMod(op.quarterTurns, 4)
        val swap = turns % 2 == 1
        val outW = if (swap) src.height else src.width
        val outH = if (swap) src.width else src.height
        val theta = Math.toRadians(op.straighten.toDouble())
        val c = abs(cos(theta)).toFloat()
        val s = abs(sin(theta)).toFloat()
        // Zoom so the rotated image still covers the whole frame (no empty corners).
        val zoom = if (op.straighten == 0f) 1f else max((outW * c + outH * s) / outW, (outW * s + outH * c) / outH)
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postScale(if (op.flipH) -1f else 1f, if (op.flipV) -1f else 1f)
            postRotate(turns * 90f + op.straighten)
            postScale(zoom, zoom)
            postTranslate(outW / 2f, outH / 2f)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun crop(src: Bitmap, op: CropOp): Bitmap {
        val l = (op.rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val t = (op.rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val r = (op.rect.right * src.width).roundToInt().coerceIn(l + 1, src.width)
        val b = (op.rect.bottom * src.height).roundToInt().coerceIn(t + 1, src.height)
        val out = Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(l, t, r, b), Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        val outline = op.outline ?: return out
        if (outline is RectCropShape) return out
        // Shape masks (circle, rounded, polygon, heart…) via the ported cropper's CropAgent.
        val masked = cropAgent.crop(
            imageBitmap = out.asImageBitmap(),
            cropRect = androidx.compose.ui.geometry.Rect(0f, 0f, out.width.toFloat(), out.height.toFloat()),
            cropOutline = outline,
            layoutDirection = LayoutDirection.Ltr,
            density = density
        ).asAndroidBitmap()
        if (masked !== out) out.recycle()
        return masked
    }

    private fun background(src: Bitmap, op: BackgroundOp): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        if (op.autoMask != null) {
            mc.drawBitmap(op.autoMask, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.BLACK })
        } else {
            mc.drawColor(android.graphics.Color.BLACK)
        }
        val short = minOf(w, h).toFloat()
        for (s in op.strokes) {
            val pts = s.points.map { android.graphics.PointF(it.x * w, it.y * h) }
            if (pts.isEmpty()) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (s.width * short).coerceAtLeast(1f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                if (s.restore) color = android.graphics.Color.BLACK
                else xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            mc.drawPath(ShapePaths.smooth(pts, false), paint)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawBitmap(src, 0f, 0f, null)
        oc.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        mask.recycle()
        return out
    }
}

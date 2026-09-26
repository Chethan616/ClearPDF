package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGammaFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVibranceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tonal adjustments. The slider range is symmetric around [default] where that makes sense.
 * Colour-matrix adjustments are applied on the CPU in one pass; the rest go through GPUImage
 * (selection of filters follows ImageToolbox's feature/filters/data/model — Apache-2.0).
 */
enum class Adjust(val min: Float, val max: Float, val default: Float, val gpu: Boolean) {
    Brightness(-100f, 100f, 0f, false),
    Contrast(-100f, 100f, 0f, false),
    Saturation(-100f, 100f, 0f, false),
    Exposure(-100f, 100f, 0f, false),
    Highlights(-100f, 100f, 0f, false),
    Shadows(-100f, 100f, 0f, false),
    Temperature(-100f, 100f, 0f, true),
    Tint(-100f, 100f, 0f, true),
    Vibrance(-100f, 100f, 0f, true),
    Hue(-180f, 180f, 0f, false),
    Gamma(-100f, 100f, 0f, true),
    Sharpen(0f, 100f, 0f, true),
    Vignette(0f, 100f, 0f, true);
}

object AdjustRenderer {

    fun colorMatrix(values: Map<Adjust, Float>): ColorMatrix? {
        fun v(a: Adjust) = values[a] ?: a.default
        val b = v(Adjust.Brightness)
        val c = v(Adjust.Contrast)
        val s = v(Adjust.Saturation)
        val e = v(Adjust.Exposure)
        val h = v(Adjust.Hue)
        if (b == 0f && c == 0f && s == 0f && e == 0f && h == 0f) return null
        val m = ColorMatrix()
        if (e != 0f) {
            val k = 2f.pow(e / 50f) // ±2 stops
            m.postConcat(scale(k))
        }
        if (s != 0f) m.postConcat(ColorMatrix().apply { setSaturation(1f + s / 100f) })
        if (h != 0f) m.postConcat(hueMatrix(h))
        if (c != 0f) {
            val k = if (c > 0) 1f + c / 50f else 1f + c / 125f
            val t = (1f - k) * 127.5f
            m.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        }
        if (b != 0f) {
            val t = b * 1.1f
            m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, t, 0f, 1f, 0f, 0f, t, 0f, 0f, 1f, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        }
        return m
    }

    fun gpuFilters(values: Map<Adjust, Float>): List<GPUImageFilter> {
        fun v(a: Adjust) = values[a] ?: a.default
        val out = mutableListOf<GPUImageFilter>()
        val temp = v(Adjust.Temperature)
        val tint = v(Adjust.Tint)
        if (temp != 0f || tint != 0f) out += GPUImageWhiteBalanceFilter(5000f + temp * 20f, tint)
        val vib = v(Adjust.Vibrance)
        if (vib != 0f) out += GPUImageVibranceFilter(vib / 50f)
        val g = v(Adjust.Gamma)
        if (g != 0f) out += GPUImageGammaFilter(if (g > 0) 1f - g / 200f else 1f - g / 50f)
        val sp = v(Adjust.Sharpen)
        if (sp != 0f) out += GPUImageSharpenFilter(sp / 25f)
        val vg = v(Adjust.Vignette)
        if (vg != 0f) out += GPUImageVignetteFilter(
            PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.75f - vg / 250f, 1f
        )
        return out
    }

    fun apply(context: Context, src: Bitmap, values: Map<Adjust, Float>): Bitmap {
        var cur = src
        colorMatrix(values)?.let { cur = applyMatrix(cur, it) }
        val hi = values[Adjust.Highlights] ?: 0f
        val sh = values[Adjust.Shadows] ?: 0f
        if (hi != 0f || sh != 0f) {
            val next = applyToneLut(cur, toneLut(sh / 100f, hi / 100f))
            if (cur !== src) cur.recycle()
            cur = next
        }
        val gpu = gpuFilters(values)
        if (gpu.isNotEmpty()) {
            val filtered = GpuRunner.apply(context, cur, if (gpu.size == 1) gpu[0] else GPUImageFilterGroup(gpu))
            if (filtered != null) {
                if (cur !== src) cur.recycle()
                cur = filtered
            }
        }
        return cur
    }

    fun applyMatrix(src: Bitmap, m: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    /** Shadow / highlight tone curve: lifts or crushes the low / high end independently. */
    private fun toneLut(shadows: Float, highlights: Float): IntArray = IntArray(256) { i ->
        val x = i / 255f
        val y = x + shadows * 2.2f * x * (1 - x) * (1 - x) + highlights * 2.2f * x * x * (1 - x)
        (y.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    private fun applyToneLut(src: Bitmap, lut: IntArray): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                row[x] = (p and -0x1000000) or (lut[p shr 16 and 0xFF] shl 16) or
                    (lut[p shr 8 and 0xFF] shl 8) or lut[p and 0xFF]
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        return out
    }

    private fun scale(k: Float) = ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, 1f, 0f))

    /** Luminance-preserving hue rotation. */
    fun hueMatrix(degrees: Float): ColorMatrix {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        return ColorMatrix(floatArrayOf(
            lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
            lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
            lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}

/**
 * Runs a GPUImage filter off-screen. GPUImage creates its own EGL context on the calling thread,
 * so callers must stay on the editor's single render thread. Returns null if GL fails (e.g. the
 * bitmap exceeds GL_MAX_TEXTURE_SIZE) so callers can fall back gracefully.
 */
object GpuRunner {
    fun apply(context: Context, src: Bitmap, filter: GPUImageFilter): Bitmap? = runCatching {
        val input = if (src.config == Bitmap.Config.ARGB_8888 && !src.isRecycled) src
        else src.copy(Bitmap.Config.ARGB_8888, false)
        val gpu = GPUImage(context.applicationContext)
        gpu.setFilter(filter)
        val out = gpu.getBitmapWithFilterApplied(input, false)
        gpu.deleteImage()
        if (input !== src) input.recycle()
        out
    }.getOrNull()
}

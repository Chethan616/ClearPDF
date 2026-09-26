package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.Paint
import jp.co.cyberagent.android.gpuimage.filter.GPUImageCrosshatchFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageEmbossFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHalftoneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageKuwaharaFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImagePosterizeFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSketchFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToonFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import android.graphics.PointF

/**
 * One-tap looks. Most are colour matrices (cheap, CPU); the artistic ones run through GPUImage.
 * [intensity] blends the look with its input: matrices are interpolated towards identity, GPU
 * looks are alpha-composited over the input.
 */
enum class FilterPreset(val label: String) {
    Vivid("Vivid"),
    Warm("Warm"),
    Cool("Cool"),
    Fade("Fade"),
    Mono("Mono"),
    Noir("Noir"),
    Sepia("Sepia"),
    Vintage("Vintage"),
    Chrome("Chrome"),
    Dramatic("Dramatic"),
    Golden("Golden"),
    Teal("Teal & Orange"),
    Polaroid("Polaroid"),
    Lomo("Lomo"),
    Invert("Invert"),
    Posterize("Posterize"),
    Sketch("Sketch"),
    Toon("Toon"),
    Emboss("Emboss"),
    Crosshatch("Crosshatch"),
    Halftone("Halftone"),
    OilPaint("Oil paint");

    fun matrix(): ColorMatrix? = when (this) {
        Vivid -> ColorMatrix().apply { setSaturation(1.45f) }.then(contrast(1.08f))
        Warm -> m(1.10f, 0f, 0f, 0f, 14f, 0f, 1.02f, 0f, 0f, 6f, 0f, 0f, 0.88f, 0f, -4f)
        Cool -> m(0.92f, 0f, 0f, 0f, -4f, 0f, 1.0f, 0f, 0f, 2f, 0f, 0f, 1.12f, 0f, 14f)
        Fade -> contrast(0.78f).then(ColorMatrix().apply { setSaturation(0.8f) }).then(offset(18f))
        Mono -> ColorMatrix().apply { setSaturation(0f) }
        Noir -> ColorMatrix().apply { setSaturation(0f) }.then(contrast(1.45f))
        Sepia -> ColorMatrix().apply { setSaturation(0f) }
            .then(m(1.07f, 0f, 0f, 0f, 38f, 0f, 0.95f, 0f, 0f, 18f, 0f, 0f, 0.78f, 0f, -6f))
        Vintage -> m(0.9f, 0.1f, 0.05f, 0f, 20f, 0.05f, 0.85f, 0.05f, 0f, 14f, 0.05f, 0.1f, 0.7f, 0f, 10f)
            .then(contrast(0.9f))
        Chrome -> contrast(1.2f).then(ColorMatrix().apply { setSaturation(1.15f) })
            .then(m(1f, 0f, 0f, 0f, -6f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 1.06f, 0f, 6f))
        Dramatic -> contrast(1.35f).then(ColorMatrix().apply { setSaturation(0.75f) }).then(offset(-10f))
        Golden -> m(1.12f, 0.05f, 0f, 0f, 16f, 0.02f, 1.05f, 0f, 0f, 10f, 0f, 0f, 0.8f, 0f, -10f)
            .then(ColorMatrix().apply { setSaturation(1.15f) })
        Teal -> m(1.1f, 0f, 0f, 0f, 8f, 0f, 1.0f, 0.05f, 0f, 0f, 0f, 0.12f, 1.05f, 0f, 10f)
            .then(contrast(1.12f))
        Polaroid -> m(1.438f, -0.062f, -0.062f, 0f, 0f, -0.122f, 1.378f, -0.122f, 0f, 0f, -0.016f, -0.016f, 1.483f, 0f, 0f)
            .then(offset(-6f)).then(ColorMatrix().apply { setSaturation(0.9f) })
        Lomo -> ColorMatrix().apply { setSaturation(1.3f) }.then(contrast(1.25f))
        Invert -> m(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f)
        else -> null
    }

    fun gpu(): GPUImageFilter? = when (this) {
        Lomo -> GPUImageVignetteFilter(PointF(0.5f, 0.5f), floatArrayOf(0f, 0f, 0f), 0.35f, 0.85f)
        Posterize -> GPUImagePosterizeFilter(6)
        Sketch -> GPUImageSketchFilter()
        Toon -> GPUImageToonFilter(0.2f, 10f)
        Emboss -> GPUImageEmbossFilter(1.5f)
        Crosshatch -> GPUImageCrosshatchFilter(0.02f, 0.003f)
        Halftone -> GPUImageHalftoneFilter(0.012f)
        OilPaint -> GPUImageKuwaharaFilter(4)
        else -> null
    }

    companion object {
        fun apply(context: Context, src: Bitmap, preset: FilterPreset, intensity: Float): Bitmap {
            val k = intensity.coerceIn(0f, 1f)
            var cur = src
            preset.matrix()?.let { cur = AdjustRenderer.applyMatrix(cur, lerp(it, k)) }
            preset.gpu()?.let { f ->
                val filtered = GpuRunner.apply(context, cur, f) ?: return cur
                val blended = if (k >= 0.999f) filtered else blend(cur, filtered, k).also { filtered.recycle() }
                if (cur !== src) cur.recycle()
                cur = blended
            }
            return if (cur === src) src.copy(Bitmap.Config.ARGB_8888, true) else cur
        }

        /** Draws [top] over [bottom] with [alpha]; returns a new bitmap. */
        fun blend(bottom: Bitmap, top: Bitmap, alpha: Float): Bitmap {
            val out = bottom.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(out).drawBitmap(
                top, null, android.graphics.Rect(0, 0, out.width, out.height),
                Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt() }
            )
            return out
        }

        private fun lerp(target: ColorMatrix, k: Float): ColorMatrix {
            if (k >= 0.999f) return target
            val id = ColorMatrix().array
            val t = target.array
            return ColorMatrix(FloatArray(20) { i -> id[i] + (t[i] - id[i]) * k })
        }
    }
}

private fun m(vararg rgb: Float): ColorMatrix {
    // 15 values (3 rows) → append identity alpha row.
    return ColorMatrix(rgb + floatArrayOf(0f, 0f, 0f, 1f, 0f))
}

private fun contrast(k: Float): ColorMatrix {
    val t = (1f - k) * 127.5f
    return ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f))
}

private fun offset(t: Float) =
    ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, t, 0f, 1f, 0f, 0f, t, 0f, 0f, 1f, 0f, t, 0f, 0f, 0f, 1f, 0f))

/** Returns this followed by [next] (mutates and returns this). */
private fun ColorMatrix.then(next: ColorMatrix): ColorMatrix = apply { postConcat(next) }


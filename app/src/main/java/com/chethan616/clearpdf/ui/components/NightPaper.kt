package com.chethan616.clearpdf.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Dark reader mode for documents ("night"), after Gander (MIT): the page's colours are turned over
 * with invert + a 180° hue turn, so white paper goes black and black ink white while a blue heading
 * stays blue (a plain RGB invert made it orange). Pictures are judged one by one: a scan or a
 * figure on white "looks like paper" and turns over with the page; a photograph is left exactly as
 * it was printed.
 */
object NightPaper {

    /** invert(1) hue-rotate(180deg) — Gander's VW_NIGHT_MATRIX, offsets in 0..255. */
    private val matrix = floatArrayOf(
        0.574f, -1.430f, -0.144f, 0f, 255f,
        -0.426f, -0.430f, -0.144f, 0f, 255f,
        -0.426f, -1.430f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )

    val filter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix))

    /** One colour as night shows it. */
    fun color(c: Color): Color {
        val r = c.red; val g = c.green; val b = c.blue
        fun ch(i: Int) = (matrix[i] * r + matrix[i + 1] * g + matrix[i + 2] * b + matrix[i + 4] / 255f).coerceIn(0f, 1f)
        return Color(ch(0), ch(5), ch(10), c.alpha)
    }

    fun argb(argb: Int): Int {
        val c = color(Color(argb))
        return android.graphics.Color.argb(
            (c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
        )
    }

    private const val ProbeWidth = 64
    private const val ProbeSaturation = 0.15f
    private const val ProbePaper = 0.25f

    /**
     * Whether [region] (normalised 0..1, null for the whole bitmap) of [bitmap] is paper — mostly
     * pale and nearly colourless, like a scan, a chart or a diagram — rather than a photograph.
     * pdf.js's looksLikePaper thresholds, measured over white (what shows through transparency).
     */
    fun looksLikePaper(bitmap: Bitmap, region: Rect? = null): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return true
        val l = ((region?.left ?: 0f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val t = ((region?.top ?: 0f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val r = ((region?.right ?: 1f) * bitmap.width).toInt().coerceIn(l + 1, bitmap.width)
        val b = ((region?.bottom ?: 1f) * bitmap.height).toInt().coerceIn(t + 1, bitmap.height)
        val w = r - l
        val h = b - t
        val cols = minOf(ProbeWidth, w).coerceAtLeast(1)
        val rows = (cols * h / w).coerceIn(1, ProbeWidth * 4)
        var pale = 0
        var sat = 0f
        var n = 0
        for (yi in 0 until rows) {
            val y = t + ((yi + 0.5f) * h / rows).toInt().coerceIn(0, h - 1)
            for (xi in 0 until cols) {
                val x = l + ((xi + 0.5f) * w / cols).toInt().coerceIn(0, w - 1)
                val p = bitmap.getPixel(x, y)
                val a = (p ushr 24) / 255f
                // Over white.
                val pr = ((p shr 16 and 0xFF) * a + 255 * (1 - a))
                val pg = ((p shr 8 and 0xFF) * a + 255 * (1 - a))
                val pb = ((p and 0xFF) * a + 255 * (1 - a))
                val mx = maxOf(pr, pg, pb)
                val mn = minOf(pr, pg, pb)
                if (mn > 204f) pale++
                if (mx > 0f) sat += (mx - mn) / mx
                n++
            }
        }
        if (n == 0) return true
        return sat / n < ProbeSaturation && pale.toFloat() / n > ProbePaper
    }
}

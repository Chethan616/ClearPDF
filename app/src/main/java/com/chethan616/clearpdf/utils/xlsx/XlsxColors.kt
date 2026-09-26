package com.chethan616.clearpdf.utils.xlsx

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A SpreadsheetML colour as the file states it — `rgb`, `theme` (+ `tint`), `indexed`, or `auto` —
 * kept in that form so a colour the user picked from the theme grid is written back as a theme
 * reference (and follows a theme change in Excel) rather than being flattened to a literal.
 */
data class ColorSpec(
    val rgb: Int? = null,
    val theme: Int = -1,
    val indexed: Int = -1,
    val tint: Double = 0.0,
    val auto: Boolean = false
) {
    /** ARGB, or null for "automatic" (the renderer picks the theme's ink). */
    fun resolve(themeColors: IntArray, indexedColors: IntArray?): Int? {
        val base: Int = when {
            auto -> return null
            rgb != null -> rgb
            theme >= 0 -> themeColors.getOrNull(theme) ?: return null
            indexed >= 0 -> {
                if (indexed == 64 || indexed == 65) return null   // system foreground/background
                indexedColors?.getOrNull(indexed) ?: XlsxColors.indexed(indexed) ?: return null
            }
            else -> return null
        }
        val opaque = if ((base ushr 24) == 0) base or 0xFF000000.toInt() else base
        return if (tint != 0.0) XlsxColors.applyTint(opaque, tint) else opaque
    }

    /** `<{tag} …/>` — the attribute form Excel writes. */
    fun toXml(tag: String): String = buildString {
        append('<').append(tag)
        when {
            auto -> append(" auto=\"1\"")
            rgb != null -> append(" rgb=\"").append(XlsxColors.hex8(rgb)).append('"')
            theme >= 0 -> append(" theme=\"").append(theme).append('"')
            indexed >= 0 -> append(" indexed=\"").append(indexed).append('"')
        }
        if (tint != 0.0 && !auto) append(" tint=\"").append(tint.toString()).append('"')
        append("/>")
    }

    companion object {
        fun ofArgb(argb: Int) = ColorSpec(rgb = argb or 0xFF000000.toInt())

        /** From a `<color>`/`<fgColor>` element's attributes. */
        fun fromAttrs(a: Map<String, String>): ColorSpec? {
            val tint = a["tint"]?.toDoubleOrNull() ?: 0.0
            return when {
                a["rgb"] != null -> XlsxColors.parseHex(a["rgb"]!!)?.let { ColorSpec(rgb = it, tint = tint) }
                a["theme"] != null -> a["theme"]!!.toIntOrNull()?.let { ColorSpec(theme = it, tint = tint) }
                a["indexed"] != null -> a["indexed"]!!.toIntOrNull()?.let { ColorSpec(indexed = it, tint = tint) }
                a["auto"] == "1" || a["auto"] == "true" -> ColorSpec(auto = true)
                else -> null
            }
        }
    }
}

object XlsxColors {

    /**
     * Office 2013–2022 default theme, in *theme-index* order (lt1, dk1, lt2, dk2, accent1–6, hlink,
     * folHlink). Used when a workbook has no theme part.
     */
    val DefaultTheme: IntArray = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE7E6E6.toInt(), 0xFF44546A.toInt(),
        0xFF4472C4.toInt(), 0xFFED7D31.toInt(), 0xFFA5A5A5.toInt(), 0xFFFFC000.toInt(),
        0xFF5B9BD5.toInt(), 0xFF70AD47.toInt(), 0xFF0563C1.toInt(), 0xFF954F72.toInt()
    )

    /** The legacy 64-colour palette `indexed="n"` points into (0–7 repeat 8–15). */
    private val IndexedPalette = intArrayOf(
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
        0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
        0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333
    )

    fun indexed(i: Int): Int? = IndexedPalette.getOrNull(i)?.let { it or 0xFF000000.toInt() }

    fun parseHex(s: String): Int? {
        val h = s.trim().removePrefix("#")
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            8 -> v.toInt()
            6 -> (v or 0xFF000000L).toInt()
            else -> null
        }
    }

    fun hex8(argb: Int): String = String.format(java.util.Locale.ROOT, "%08X", argb)
    fun hex6(argb: Int): String = String.format(java.util.Locale.ROOT, "%06X", argb and 0xFFFFFF)

    /**
     * Excel's tint: in HLS, `tint < 0` darkens (`L·(1+tint)`), `tint > 0` lightens
     * (`L·(1−tint) + tint`). Hue and saturation stay; alpha is preserved.
     */
    fun applyTint(argb: Int, tint: Double): Int {
        if (tint == 0.0) return argb
        val hls = rgbToHls(argb)
        val l = if (tint < 0) hls[2] * (1.0 + tint) else hls[2] * (1.0 - tint) + tint
        return (argb and 0xFF000000.toInt()) or (hlsToRgb(hls[0], l.coerceIn(0.0, 1.0), hls[1]) and 0xFFFFFF)
    }

    /** [h (0–1), s, l] for an ARGB int. */
    fun rgbToHls(argb: Int): DoubleArray {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        if (max == min) return doubleArrayOf(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
            g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        return doubleArrayOf(h, s, l)
    }

    fun hlsToRgb(h: Double, l: Double, s: Double): Int {
        if (s == 0.0) {
            val v = (l * 255).roundToInt().coerceIn(0, 255)
            return 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(tIn: Double): Double {
            var t = tIn
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        val r = (hue(h + 1.0 / 3) * 255).roundToInt().coerceIn(0, 255)
        val g = (hue(h) * 255).roundToInt().coerceIn(0, 255)
        val b = (hue(h - 1.0 / 3) * 255).roundToInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    /** Perceived luminance 0–1, for picking legible ink over a fill. */
    fun luminance(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** The tints Excel's colour grid shows under each theme swatch (rows 2–6). */
    fun themeGridTints(baseArgb: Int): DoubleArray {
        val l = rgbToHls(baseArgb)[2]
        return when {
            l < 0.02 -> doubleArrayOf(0.4999, 0.3499, 0.2499, 0.1499, 0.0499)       // black
            l > 0.98 -> doubleArrayOf(-0.0499, -0.1499, -0.2499, -0.3499, -0.4999)  // white
            l < 0.2 -> doubleArrayOf(0.8999, 0.7499, 0.4999, 0.2499, 0.0999)
            l > 0.8 -> doubleArrayOf(-0.0999, -0.2499, -0.4999, -0.7499, -0.8999)
            else -> doubleArrayOf(0.7999, 0.5999, 0.3999, -0.2499, -0.4999)
        }
    }

    internal fun near(a: Int, b: Int, tol: Int = 1): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tol &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tol &&
            abs((a and 0xFF) - (b and 0xFF)) <= tol
}

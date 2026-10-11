package com.chethan616.clearpdf.utils.xlsx

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.chethan616.clearpdf.R

/**
 * Carlito, the free font with Calibri's exact metrics (SIL OFL). Workbooks are laid out in Calibri
 * (or Aptos), and Excel's column widths are measured in its characters; drawn in the wider system
 * sans, text overflowed its cells, wrapped onto extra lines and was cut off at merge edges (#71).
 */
object XlsxFonts {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null

    fun init(context: Context) {
        if (regular != null) return
        regular = runCatching { ResourcesCompat.getFont(context.applicationContext, R.font.carlito_regular) }.getOrNull()
        bold = runCatching { ResourcesCompat.getFont(context.applicationContext, R.font.carlito_bold) }.getOrNull()
    }

    private val calibriLike = setOf("calibri", "calibri light", "carlito", "aptos", "aptos narrow", "aptos display", "aptos light")

    /** Carlito in [style] for a Calibri-family font name (null when it isn't one, or not loaded). */
    fun forName(name: String?, style: Int): Typeface? {
        val n = name?.lowercase()?.trim() ?: return null
        if (n !in calibriLike) return null
        val base = (if (style and Typeface.BOLD != 0) bold else regular) ?: return null
        return if (style and Typeface.ITALIC != 0) Typeface.create(base, Typeface.ITALIC) else base
    }
}

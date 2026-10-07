package com.chethan616.clearpdf.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Small, shared palette for the glass surfaces. Keeping these values in one
 * place makes the visual hierarchy consistent across every screen and avoids
 * scattering near-duplicate colors through composables.
 */
object LiquidGlassColors {
    val LightText = Color(0xFF111216)
    val LightSecondary = Color(0xFF5E6068)
    val DarkText = Color(0xFFF7F7FA)
    val DarkSecondary = Color(0xFFB5B6BF)

    // Apple-style system accents, tuned for translucent surfaces.
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Purple = Color(0xFFBF5AF2)
    val Teal = Color(0xFF64D2FF)
    val Indigo = Color(0xFF5E5CE6)
    val Orange = Color(0xFFFF9F0A)

    fun text(isDark: Boolean): Color = if (isDark) DarkText else LightText
    fun secondary(isDark: Boolean): Color = if (isDark) DarkSecondary else LightSecondary

    /**
     * Status ink for text sitting directly on glass. The bright system green / red read fine on a
     * dark panel but wash out on a light one (the old `#B9F6CA` success line was invisible in light
     * mode), so each theme gets its own shade.
     */
    fun success(isDark: Boolean): Color = if (isDark) Color(0xFF4CD964) else Color(0xFF1B7F37)
    fun danger(isDark: Boolean): Color = if (isDark) Color(0xFFFF6961) else Color(0xFFC62828)
    fun warning(isDark: Boolean): Color = if (isDark) Color(0xFFFFB340) else Color(0xFFA15C00)

    /** A quiet wash for unselected chips and secondary pills — the title pill's own surface. */
    fun neutralSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFF1E1E1E).copy(0.35f) else Color(0xFFFAFAFA).copy(0.35f)
}

/**
 * One accent per screen. A tool's grid tile, its intro icon and every vivid "Get it" button on its
 * screen use the same colour, so opening a tool reads as stepping into that tile.
 *
 * Every value is chosen to carry a **white** label on [com.chethan616.clearpdf.ui.components.LiquidButton]'s
 * tinted capsule (which lays the tint over a 42% white base). That rules out the bright system
 * greens, teals, oranges and ambers as-is — white on `#30D158` or `#64D2FF` is under 2:1 — so those
 * hues appear here as their deeper, still-saturated shades.
 */
object ToolAccents {
    val Open = Color(0xFF0A84FF)
    val Scan = Color(0xFF1E9E4A)

    // Organize
    val Merge = Color(0xFFFF3B30)
    val Split = Color(0xFFAF52DE)
    val Organize = Color(0xFF0E9AB5)
    val ExtractPages = Color(0xFF00897B)

    // Convert
    val ImagesToPdf = Color(0xFF5E5CE6)
    val PdfToImages = Color(0xFF1597D3)
    val ExtractText = Color(0xFF00838F)
    val Create = Color(0xFFEE6A00)
    val HtmlToPdf = Color(0xFFEE6A00)

    // Edit
    val Watermark = Color(0xFFD81B60)
    val PageNumbers = Color(0xFF3949AB)
    val FillForm = Color(0xFF00695C)
    val ImageTools = Color(0xFFF4511E)

    // Optimize & secure
    val Compress = Color(0xFF1E9E4A)
    val Flatten = Color(0xFF8D6E50)
    val Encrypt = Color(0xFF5E5CE6)
    val Decrypt = Color(0xFFAF52DE)

    // App chrome
    val Settings = Color(0xFF0A84FF)
    val Star = Color(0xFFD97706)
    val Destructive = Color(0xFFFF3B30)
}

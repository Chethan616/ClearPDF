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
}

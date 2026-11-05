package com.chethan616.clearpdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.Inter
import com.chethan616.clearpdf.ui.ThemeChoice

/** The candy accents: one per tool, the same hue in both themes. */
object Candy {
    val Tomato = Color(0xFFFF5A3C)
    val Tangerine = Color(0xFFFF9A1F)
    val Lemon = Color(0xFFFFC83A)
    val Lime = Color(0xFF8CCB2E)
    val Mint = Color(0xFF17C08F)
    val Sky = Color(0xFF2E86FF)
    val Grape = Color(0xFF8A4DFF)
    val Bubblegum = Color(0xFFFF4DA6)

    val All = listOf(Tomato, Tangerine, Lemon, Lime, Mint, Sky, Grape, Bubblegum)
}

@Immutable
data class ClearColors(
    val dark: Boolean,
    /** Behind everything, under the aurora. */
    val canvas: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    /** The wash on chrome glass (dock, bars, buttons). */
    val glass: Color,
    /** The heavier frost menus, sheets and dialogs use so their text stays legible. */
    val frost: Color,
    /** The aurora's blobs: colour and strength. */
    val aurora: List<Pair<Color, Float>>,
    /** Behind document pages. */
    val desk: Color,
)

private val InkLight = Color(0xFF17141F)
private val InkDark = Color(0xFFF7F3FF)

val LightClear = ClearColors(
    dark = false,
    canvas = Color(0xFFF6F1EA),
    ink = InkLight,
    inkSoft = InkLight.copy(alpha = 0.64f),
    inkFaint = InkLight.copy(alpha = 0.40f),
    line = InkLight.copy(alpha = 0.08f),
    glass = Color.White.copy(alpha = 0.40f),
    frost = Color(0xFFFBF8F4).copy(alpha = 0.78f),
    aurora = listOf(
        Candy.Tangerine to 0.30f,
        Candy.Grape to 0.24f,
        Candy.Sky to 0.20f,
        Candy.Bubblegum to 0.18f,
        Candy.Lemon to 0.26f,
    ),
    desk = Color(0xFFE6E2DC),
)

val DarkClear = ClearColors(
    dark = true,
    canvas = Color(0xFF0E0C13),
    ink = InkDark,
    inkSoft = InkDark.copy(alpha = 0.66f),
    inkFaint = InkDark.copy(alpha = 0.42f),
    line = InkDark.copy(alpha = 0.10f),
    glass = Color(0xFF2A2634).copy(alpha = 0.42f),
    frost = Color(0xFF1C1924).copy(alpha = 0.82f),
    aurora = listOf(
        Candy.Tangerine to 0.30f,
        Candy.Grape to 0.42f,
        Candy.Sky to 0.30f,
        Candy.Bubblegum to 0.26f,
        Candy.Mint to 0.18f,
    ),
    desk = Color(0xFF111015),
)

val LocalClearColors = staticCompositionLocalOf { LightClear }

/** The colours in effect. */
val clear: ClearColors @Composable get() = LocalClearColors.current

/** Bricolage Grotesque at one point of its weight, width and optical-size axes. */
private fun bricolage(weight: Int, width: Float, opticalSize: Float) = FontFamily(
    Font(
        R.font.bricolage,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun display(family: FontFamily, size: TextUnit, line: TextUnit, tracking: TextUnit) =
    TextStyle(fontFamily = family, fontSize = size, lineHeight = line, letterSpacing = tracking, lineHeightStyle = Trim)

/**
 * The type scale. Bricolage Grotesque (condensed, heavy, large optical size) carries the voice: heroes,
 * titles, tile labels and numbers. Inter carries reading: body, labels and captions.
 */
object ClearType {
    val Hero = display(bricolage(800, 84f, 96f), 52.sp, 54.sp, (-0.9).sp)
    val Display = display(bricolage(760, 86f, 60f), 40.sp, 42.sp, (-0.6).sp)
    val Headline = display(bricolage(720, 90f, 36f), 27.sp, 31.sp, (-0.3).sp)
    val Title = display(bricolage(660, 92f, 24f), 19.sp, 23.sp, (-0.2).sp)
    val Tile = display(bricolage(720, 86f, 20f), 18.sp, 20.sp, (-0.2).sp)
    val Number = display(bricolage(740, 80f, 48f), 22.sp, 24.sp, 0.sp).copy(fontFeatureSettings = "tnum")

    val Eyebrow = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun ClearTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    CompositionLocalProvider(LocalClearColors provides if (dark) DarkClear else LightClear, content = content)
}

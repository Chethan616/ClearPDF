package com.chethan616.clearpdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.ThemeChoice

/**
 * The accents: Apple's system hues, one per tool, the same in both themes. Icons, toggles, the
 * vivid buttons. Surfaces use the softer [CardTone]s instead.
 */
object Candy {
    val Tomato = Color(0xFFFF5B4F)
    val Tangerine = Color(0xFFFF9F0A)
    val Lemon = Color(0xFFFFC21A)
    val Lime = Color(0xFF34C759)
    val Mint = Color(0xFF00BFB3)
    val Sky = Color(0xFF0A84FF)
    val Grape = Color(0xFF5E5CE6)
    val Bubblegum = Color(0xFFFF4F8B)

    /** The chosen chip's pastel lemon. */
    val Chip = Color(0xFFEEF27E)
    /** A note's title blue. */
    val InkBlue = Color(0xFF2C6DB5)

    val All = listOf(Tomato, Tangerine, Lemon, Lime, Mint, Sky, Grape, Bubblegum)
}

/** A pastel card's paint: a soft vertical gradient, the ink on it and its own accent (titles, serif words). */
@Immutable
data class CardTone(val top: Color, val bottom: Color, val ink: Color, val accent: Color)

/** The card colours, by role. */
enum class Tone { Sage, Paper, Lavender, Butter, Orchid, Blush, Sky, Peach }

@Immutable
data class ClearColors(
    val dark: Boolean,
    /** Behind everything: the wallpaper's gradient, top to bottom. */
    val wall: List<Color>,
    /** Soft light pooled on the wallpaper: colour and strength. */
    val blooms: List<Pair<Color, Float>>,
    /** Big titles straight on the wallpaper. */
    val hero: Color,
    /** The glow behind [hero] titles. */
    val heroGlow: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    /** The wash on chrome glass (dock, bars, buttons, chips). */
    val glass: Color,
    /** The rim of an outlined glass chip. */
    val rim: Color,
    /** The heavier frost menus, sheets and dialogs use so their text stays legible. */
    val frost: Color,
    /** Behind document pages. */
    val desk: Color,
    /** The dark glass of the floating create capsule. */
    val capsule: Color,
    val tones: Map<Tone, CardTone>,
) {
    /** The canvas colour (the wallpaper's middle), for places that need one flat colour. */
    val canvas: Color get() = wall[wall.size / 2]

    fun tone(t: Tone): CardTone = tones.getValue(t)
}

private val InkLight = Color(0xFF172033)
private val InkDark = Color(0xFFEEF1FF)

private val LightTones = mapOf(
    Tone.Sage to CardTone(Color(0xFF8ED0C1), Color(0xFFB2DFD6), Color(0xFF12302B), Color(0xFF1B6E60)),
    Tone.Paper to CardTone(Color(0xFFFFFFFF).copy(alpha = 0.88f), Color(0xFFF1F5FC).copy(alpha = 0.82f), InkLight, Candy.InkBlue),
    Tone.Lavender to CardTone(Color(0xFF9FA5EA), Color(0xFFBABDF5), Color.White, Color.White),
    Tone.Butter to CardTone(Color(0xFFEAE5BC), Color(0xFFF4F1DA), Color(0xFF3A3420), Color(0xFFB0862C)),
    Tone.Orchid to CardTone(Color(0xFFE9C1EC), Color(0xFFD8AEE7), Color(0xFF3B1E3F), Color(0xFFC0258A)),
    Tone.Blush to CardTone(Color(0xFFF5AFBF), Color(0xFFF7C3CF), Color(0xFF4A1824), Color(0xFFD23B5F)),
    Tone.Sky to CardTone(Color(0xFFA8D0F5), Color(0xFFC9E3FA), Color(0xFF13304F), Color(0xFF1C6FD1)),
    Tone.Peach to CardTone(Color(0xFFF8C6A3), Color(0xFFFBDCC5), Color(0xFF4A2512), Color(0xFFD9541C)),
)

/**
 * At night the cards become jewels: saturated, deep, lit from above, with pale ink on them. (The
 * pastels merely darkened turned muddy against the midnight wallpaper.)
 */
private val DarkTones = mapOf(
    Tone.Sage to CardTone(Color(0xFF22877A), Color(0xFF15605A), Color(0xFFEAFBF6), Color(0xFF7CE3CF)),
    Tone.Paper to CardTone(Color(0xFFCBD5FF).copy(alpha = 0.16f), Color(0xFFCBD5FF).copy(alpha = 0.09f), InkDark, Color(0xFF9DB8FF)),
    Tone.Lavender to CardTone(Color(0xFF5B4FD6), Color(0xFF3F34A6), Color.White, Color(0xFFD9D4FF)),
    Tone.Butter to CardTone(Color(0xFFB38A1F), Color(0xFF7C5C12), Color(0xFFFFF6DA), Color(0xFFFFD66B)),
    Tone.Orchid to CardTone(Color(0xFF9A3FB5), Color(0xFF66257F), Color(0xFFFBE9FF), Color(0xFFF0A6FF)),
    Tone.Blush to CardTone(Color(0xFFC7415F), Color(0xFF8F2644), Color(0xFFFFE9EE), Color(0xFFFF9DB5)),
    Tone.Sky to CardTone(Color(0xFF2D79D6), Color(0xFF1A4FA0), Color(0xFFE8F3FF), Color(0xFF9CCBFF)),
    Tone.Peach to CardTone(Color(0xFFD2723B), Color(0xFF9C4A22), Color(0xFFFFF0E4), Color(0xFFFFBE91)),
)

val LightClear = ClearColors(
    dark = false,
    wall = listOf(Color(0xFF9AB4DC), Color(0xFFB3C5E8), Color(0xFFA8BEE3)),
    blooms = listOf(
        Color(0xFFCDF2F3) to 0.62f,
        Color(0xFFD9C3F4) to 0.58f,
        Color(0xFFF7BEDD) to 0.36f,
        Color(0xFF8AA4D7) to 0.42f,
        Color(0xFFFFFFFF) to 0.20f,
    ),
    hero = Color.White,
    heroGlow = Color(0xFF3A527E).copy(alpha = 0.22f),
    ink = InkLight,
    inkSoft = InkLight.copy(alpha = 0.64f),
    inkFaint = InkLight.copy(alpha = 0.40f),
    line = InkLight.copy(alpha = 0.08f),
    glass = Color.White.copy(alpha = 0.26f),
    rim = Color.White.copy(alpha = 0.70f),
    frost = Color(0xFFF3F6FC).copy(alpha = 0.82f),
    desk = Color(0xFFDCE3EE),
    capsule = Color(0xFF26324A).copy(alpha = 0.34f),
    tones = LightTones,
)

val DarkClear = ClearColors(
    dark = true,
    // Midnight Aurora: deep indigo with cobalt, violet and a breath of magenta pooled in it
    wall = listOf(Color(0xFF0A0B1E), Color(0xFF14123A), Color(0xFF0B1030)),
    blooms = listOf(
        Color(0xFF1E6FD9) to 0.46f,
        Color(0xFF7C3AED) to 0.52f,
        Color(0xFFD62C86) to 0.26f,
        Color(0xFF3B4FD8) to 0.42f,
        Color(0xFFA5B4FF) to 0.07f,
    ),
    hero = Color.White,
    heroGlow = Color(0xFF05061A).copy(alpha = 0.45f),
    ink = InkDark,
    inkSoft = InkDark.copy(alpha = 0.68f),
    inkFaint = InkDark.copy(alpha = 0.44f),
    line = InkDark.copy(alpha = 0.10f),
    glass = Color(0xFFC9D2FF).copy(alpha = 0.11f),
    rim = Color.White.copy(alpha = 0.26f),
    frost = Color(0xFF1A1B3C).copy(alpha = 0.90f),
    desk = Color(0xFF090A18),
    capsule = Color(0xFF0B0C22).copy(alpha = 0.46f),
    tones = DarkTones,
)

val LocalClearColors = staticCompositionLocalOf { LightClear }

/** The colours in effect. */
val clear: ClearColors @Composable get() = LocalClearColors.current

/** Plus Jakarta Sans (OFL) at one weight of its variable axis. */
private fun jakarta(weight: Int, italic: Boolean = false) = FontFamily(
    Font(
        if (italic) R.font.jakarta_italic else R.font.jakarta,
        FontWeight(weight),
        if (italic) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

/** The whole Jakarta family, every weight the text styles ask for. */
val Jakarta: FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).flatMap { w ->
        listOf(
            Font(R.font.jakarta, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(R.font.jakarta_italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    },
)

/** Doto (OFL): the dot-matrix face of to-do lists. */
val Doto: FontFamily = FontFamily(
    Font(R.font.doto, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.Setting("ROND", 60f))),
)

/** DM Serif Display (OFL): the editorial word on a collection's card ("Files", "Pdf"). */
val Serif: FontFamily = FontFamily(
    Font(R.font.dm_serif, FontWeight.Normal),
    Font(R.font.dm_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Fraunces (OFL): a soft serif. [soft] rounds its terminals (0..100); [wonk] 1 leans some letters (quirky), 0 keeps them upright. */
private fun fraunces(weight: Int, soft: Float, size: TextUnit, wonk: Float = 0f) = FontFamily(
    Font(
        R.font.fraunces, FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight), FontVariation.opticalSizing(size),
            FontVariation.Setting("SOFT", soft), FontVariation.Setting("WONK", wonk),
        ),
    ),
)

/** Bricolage Grotesque (OFL): a grotesque with a width axis, from a tall condensed 75 to the normal 100. */
private fun bricolage(weight: Int, width: Float, size: TextUnit) = FontFamily(
    Font(
        R.font.bricolage, FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight), FontVariation.width(width), FontVariation.opticalSizing(size),
        ),
    ),
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun display(family: FontFamily, size: TextUnit, line: TextUnit, tracking: TextUnit) =
    TextStyle(fontFamily = family, fontSize = size, lineHeight = line, letterSpacing = tracking, lineHeightStyle = Trim)

/**
 * The type scale, all Plus Jakarta Sans: heavy and tight for heroes and titles (Apple's display
 * sizes, a touch rounder), plain for reading. [Serif] and [Doto] are the playful accents on cards.
 */
object ClearType {
    val Hero = display(jakarta(700), 54.sp, 56.sp, (-1.4).sp)
    val Display = display(jakarta(600), 42.sp, 46.sp, (-1.0).sp)
    val Headline = display(jakarta(700), 26.sp, 30.sp, (-0.5).sp)
    val Title = display(jakarta(700), 18.sp, 22.sp, (-0.2).sp)
    val Tile = display(jakarta(700), 17.sp, 20.sp, (-0.2).sp)
    val Number = display(jakarta(700), 22.sp, 24.sp, 0.sp).copy(fontFeatureSettings = "tnum")

    /** A note card's title: bold, tight, in the note's blue. */
    val CardTitle = display(jakarta(800), 27.sp, 29.sp, (-0.6).sp)
    /** A collection card's word ("Files"). */
    val SerifWord = display(Serif, 52.sp, 52.sp, (-1.2).sp)
    /** A list card's title. */
    val DotTitle = display(Doto, 30.sp, 32.sp, 0.sp)

    // The desk's big title, one face per shelf, each in the voice of what it holds: a clean bold
    // grotesque for everything, an upright soft serif for notes, dots for lists, an editorial serif
    // for PDFs, a thin italic for pictures, a light wide grotesque for voices.
    // All six share one size and one line height, so switching chips never moves anything below the
    // title; the desk shrinks that one size (for every title alike) only if a translation is too wide.
    val DeskTitleSize = 44.sp
    val DeskTitleLine = 50.sp
    val DeskAll = display(bricolage(700, 100f, 96.sp), DeskTitleSize, DeskTitleLine, (-1.4).sp)
    val DeskNotes = display(fraunces(600, 50f, 72.sp), DeskTitleSize, DeskTitleLine, (-0.8).sp)
    val DeskTodo = display(Doto, DeskTitleSize, DeskTitleLine, 0.sp)
    val DeskPdfs = display(Serif, DeskTitleSize, DeskTitleLine, (-0.8).sp)
    val DeskImages = display(jakarta(300, italic = true), DeskTitleSize, DeskTitleLine, (-1.4).sp).copy(fontStyle = FontStyle.Italic)
    val DeskAudio = display(bricolage(300, 100f, 72.sp), DeskTitleSize, DeskTitleLine, (-1.0).sp)

    /** The Tools screen's title: Fraunces at its softest and quirkiest, a face no other screen uses. */
    val ToolsTitle = display(fraunces(700, 100f, 72.sp, wonk = 1f), DeskTitleSize, DeskTitleLine, (-0.8).sp)
    /** A tool tile's name, set like the desk's "Files" and "Pdf" words. */
    val ToolWord = display(Serif, 31.sp, 33.sp, (-0.6).sp)

    val Eyebrow = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)
    val Body = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Label = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 15.sp)
}

/** A hero title's soft glow, so white type reads on the pale sky. */
fun TextStyle.glowing(c: ClearColors): TextStyle = copy(shadow = Shadow(c.heroGlow, Offset(0f, 2f), 22f))

@Composable
fun ClearTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    CompositionLocalProvider(LocalClearColors provides if (dark) DarkClear else LightClear, content = content)
}

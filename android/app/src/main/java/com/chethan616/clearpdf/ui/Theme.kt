package com.chethan616.clearpdf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The app's reading face: Plus Jakarta Sans, the same as the glass screens (the old name stays for the viewer's code). */
val Inter: FontFamily get() = com.chethan616.clearpdf.ui.theme.Jakarta

/** The colours the screens use beyond Material's roles (measured from the reference app's screens). */
@Immutable
data class PdfColors(
    val dark: Boolean,
    /** The app's surfaces: screens, bars and sheets. */
    val surface: Color,
    /** Slightly raised: cards, the search field, selected rows. */
    val surfaceRaised: Color,
    /** A pressed or selected control's background (the active tool, an open picker's button). */
    val pressed: Color,
    /** The viewer's desk around and between the pages. */
    val desk: Color,
    val pageEdge: Color,
    val text: Color,
    val textSecondary: Color,
    /** Dates and other quiet text (the comment list's dates). */
    val textTertiary: Color,
    /** Bar and sheet icons. */
    val icon: Color,
    val divider: Color,
    /** A sheet's drag handle. */
    val handle: Color,
    /** Selection, the active tool, checked rows. */
    val accent: Color,
    val onAccent: Color,
    /** Filled buttons (primary actions). */
    val button: Color,
    /** The PDF badge and destructive actions. */
    val red: Color,
    val scrim: Color,
    /** Hint toasts over the page. */
    val toast: Color,
    val onToast: Color,
    /** Labels of the viewer's bars (the reference's primary label colour). */
    val label: Color,
    /** The corner triangle of a tool with sub-tools (the same in both themes). */
    val cornerMark: Color = Color(0xFF4B4B4B),
    /** The active tool's icon and the Done check (the reference's darker blue on its bars). */
    val toolAccent: Color,
    /** The comment panel's "N of M" and its reply box's outline and hint. */
    val navigatorText: Color,
    val replyBorder: Color,
    /** Home, Files, Tools and Search: the reference's home background. */
    val home: Color,
    /** The card behind each page of Pages. */
    val pageCard: Color,
    /** Pop-up menus over the page (the text selection's). */
    val menu: Color,
    /** The band behind the navigation buttons in the viewer and its screens and sheets. */
    val navBand: Color,
    /** The Read aloud switch's track, off and on. */
    val switchOff: Color,
    val switchOn: Color,
)

val LocalPdfColors = staticCompositionLocalOf<PdfColors> { error("no PdfcraftTheme") }

/** The colours in effect. */
val pdfColors: PdfColors @Composable get() = LocalPdfColors.current

/** How the app chooses light or dark (Preferences ▸ Theme). */
enum class ThemeChoice { System, Light, Dark }

/**
 * The viewer's and tool screens' roles in Artefact's palette: cool grouped surfaces (Apple's),
 * the theme's ink, the system accents. (Inside [com.chethan616.clearpdf.ui.theme.ClearTheme], so it follows the same theme.)
 */
private fun pdfColorsFrom(cc: com.chethan616.clearpdf.ui.theme.ClearColors): PdfColors {
    val candy = com.chethan616.clearpdf.ui.theme.Candy
    val dark = cc.dark
    val surface = if (dark) Color(0xFF121B20) else Color(0xFFF5F7FB)
    val raised = if (dark) Color(0xFF1B262C) else Color(0xFFE9EDF4)
    return PdfColors(
        dark = dark,
        surface = surface,
        surfaceRaised = raised,
        pressed = if (dark) Color(0xFF24323A) else Color(0xFFDDE3EC),
        desk = cc.desk,
        pageEdge = if (dark) Color.Transparent else Color(0x1F000000),
        text = cc.ink,
        textSecondary = cc.inkSoft,
        textTertiary = cc.inkFaint,
        icon = cc.ink,
        divider = cc.line,
        handle = cc.inkFaint,
        accent = candy.Sky,
        onAccent = Color.White,
        button = candy.Sky,
        red = candy.Tomato,
        scrim = if (dark) Color(0x99000000) else Color(0x55000000),
        toast = if (dark) Color(0xFF24323A) else Color(0xFF26324A),
        onToast = Color.White,
        label = cc.ink,
        toolAccent = candy.Sky,
        navBand = surface,
        menu = surface,
        pageCard = raised,
        home = cc.canvas,
        navigatorText = cc.ink,
        replyBorder = cc.inkSoft,
        switchOff = raised,
        switchOn = candy.Lime,
    )
}

@Composable
fun PdfcraftTheme(choice: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val c = pdfColorsFrom(com.chethan616.clearpdf.ui.theme.LocalClearColors.current)
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, surface = c.surface, onSurface = c.text, background = c.surface, onBackground = c.text,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary, outline = c.divider, error = c.red, surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised, surfaceContainerLow = c.surface)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, surface = c.surface, onSurface = c.text, background = c.surface, onBackground = c.text,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary, outline = c.divider, error = c.red, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceRaised, surfaceContainerLow = c.surface)
    }
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Inter),
        headlineMedium = base.headlineMedium.copy(fontFamily = Inter),
        headlineSmall = base.headlineSmall.copy(fontFamily = Inter),
        titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    )
    CompositionLocalProvider(LocalPdfColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

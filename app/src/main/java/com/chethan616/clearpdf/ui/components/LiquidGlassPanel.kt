package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/** Dark, mostly-opaque glass base for PDF-viewer chrome so white text stays readable
 *  over bright pages, while the lens/blur/highlight refraction is preserved. */
val ViewerChromeGlass: Color = Color(0xFF12151C).copy(alpha = 0.62f)

@Composable
fun Modifier.liquidGlassPanel(
    backdrop: Backdrop,
    uiSensor: UISensor,
    // When set, overrides the theme-based tint. Used by the PDF viewer chrome, which
    // renders white text over a backdrop that may be a bright page — it needs a dark,
    // mostly-opaque base so text stays readable while the glass refraction is kept.
    containerColorOverride: Color? = null
): Modifier {
    val isDarkMode = LocalIsDarkMode.current
    val isLightTheme = !isDarkMode
    // The title chips' tint (GlassTitlePill: 0.35), so a panel and the chip above it read as one
    // material rather than a card under a lens.
    val containerColor = containerColorOverride
        ?: if (isLightTheme) Color(0xFFFAFAFA).copy(0.35f) else Color(0xFF1E1E1E).copy(0.35f)
    // Flat wallpaper: same pixels, none of the blur/lens/offscreen work — see FlatBackdrop.
    val flat = flatColorOf(backdrop)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(28f.dp) },
        // Heavier frost than a chip on purpose: panels carry paragraphs of text, which a 2 dp blur
        // over a busy custom wallpaper would leave unreadable.
        effects = if (flat != null) ({}) else ({
            vibrancy()
            blur(8f.dp.toPx())
            lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true)
        }),
        // The chips' bright specular rim (falloff 1, LiquidButton's default) instead of the old narrow
        // falloff-2 sheen plus a dark 3 dp inner shadow — that inset edge was what made panels read as
        // tinted cards next to the glassy title chips. Still turns with the device tilt.
        highlight = { Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle)) },
        shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.1f)) },
        onDrawBackdrop = if (flat != null) ({ _ -> drawFlatVibrantBackdrop(flat) }) else ({ it() }),
        onDrawSurface = { drawRect(containerColor) }
    )
}

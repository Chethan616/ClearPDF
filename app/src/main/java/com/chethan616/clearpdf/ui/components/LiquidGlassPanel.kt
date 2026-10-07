package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.theme.LocalIsScrolling
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
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
    val containerColor = containerColorOverride
        ?: if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF1E1E1E).copy(0.4f)
    // A flatter, more opaque stand-in for the scrolling window: cheap to draw, and dense enough
    // that it doesn't flash a visibly different material for the ~100ms it's on screen.
    val scrollingColor = containerColor.copy(alpha = (containerColor.alpha * 2.25f).coerceAtMost(0.92f))
    // A plain (non-deferred) read, not LocalIsScrolling.current used inside a draw-phase lambda:
    // this recomposes on scroll start/stop (rare) so it can branch the modifier chain itself and
    // skip drawBackdrop's own backdrop CAPTURE while scrolling, not just its vibrancy/blur/lens
    // shaders. A screen with many panels (Settings has ~10) was still paying for that capture on
    // every one, every scroll frame, even with the shaders skipped -- see LiquidButton's own note,
    // where the same upgrade was needed for its many small per-chip/pill instances.
    val isScrolling = LocalIsScrolling.current()
    return this.then(
        if (isScrolling) {
            Modifier.clip(RoundedRectangle(28f.dp)).drawBehind { drawRect(scrollingColor) }
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(28f.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f)) },
                shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.1f)) },
                innerShadow = { InnerShadow(radius = 3f.dp, alpha = 0.3f) },
                onDrawSurface = { drawRect(containerColor) }
            )
        }
    )
}

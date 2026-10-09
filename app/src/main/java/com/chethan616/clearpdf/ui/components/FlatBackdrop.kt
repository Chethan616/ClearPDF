package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kyant.backdrop.Backdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * The app's wallpaper layer when it is a single solid colour (Settings → Background off, the
 * default), so glass sampling it can skip work that provably changes no pixels.
 *
 * Every `liquidGlassPanel` / `LiquidButton` / `LiquidIconButton` runs `vibrancy → blur → lens` over
 * what it samples. Over a uniform colour, blur (clamped edges) returns that colour, and the lens
 * shader only *displaces* sample coordinates (`content.eval(refractedCoord)`, no shading) — so it
 * returns that colour too. The visible result is exactly `vibrancy(colour)`, everywhere. Yet each
 * surface was still recording the wallpaper into an offscreen layer and running a blur pass plus an
 * AGSL shader on it every frame, and re-recording on every scroll step because the sample position
 * is coordinate-dependent. Settings alone has ~25 such surfaces.
 *
 * The fast path draws `vibrancy(colour)` directly. Highlight, shadows, inner shadow, shape clip and
 * surface tint are untouched, and only surfaces whose backdrop *is* this exact wallpaper object take
 * it — anything sampling live content (headers, nav bar, dialogs, viewer chrome) keeps the full
 * pipeline.
 */
@Immutable
class FlatBackdrop(val backdrop: Backdrop, val color: Color)

val LocalFlatBackdrop = staticCompositionLocalOf<FlatBackdrop?> { null }

/** The flat wallpaper colour if [backdrop] is that flat wallpaper, else null (use the full glass). */
@Composable
fun flatColorOf(backdrop: Backdrop): Color? {
    val flat = LocalFlatBackdrop.current ?: return null
    return if (flat.backdrop === backdrop) flat.color else null
}

/**
 * Compose twin of the backdrop library's `vibrancy()` filter (`colorControls(saturation = 1.5f)`):
 * the same 4x5 matrix, so drawing a colour through it rounds exactly as the RenderEffect would.
 */
val VibrancyColorFilter: ColorFilter = run {
    val saturation = 1.5f
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r + saturation, g, b, 0f, 0f,
                r, g + saturation, b, 0f, 0f,
                r, g, b + saturation, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/** What `vibrancy → blur → lens` produces over a flat [color]: drawn straight, no layer, no shader. */
/**
 * The title chips' glass (LiquidButton's recipe: vibrancy, 2 dp frost, 12x24 lens rim, specular
 * highlight) as a surface for non-button content such as list rows. Honours the flat-wallpaper fast
 * path. No drop shadow: rows sit inside a panel and are clipped.
 */
@androidx.compose.runtime.Composable
fun Modifier.chipGlassSurface(
    backdrop: Backdrop,
    shape: () -> androidx.compose.ui.graphics.Shape,
    surface: Color
): Modifier {
    val flat = flatColorOf(backdrop)
    val style = GlassSettings.style
    return drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = if (flat != null) ({}) else ({
            glassEffects(style, 2f.dp.toPx(), 12f.dp.toPx(), 24f.dp.toPx())
        }),
        highlight = { glassHighlight(style) },
        shadow = null,
        onDrawBackdrop = if (flat != null) ({ _ -> drawFlatVibrantBackdrop(flat) }) else ({ it() }),
        onDrawSurface = { drawRect(surface.glassTint(style)) }
    )
}

/** The colour stack (user's vibrancy/brightness) over a flat [color]; blur and lens are no-ops there. */
fun DrawScope.drawFlatVibrantBackdrop(color: Color) {
    drawRect(color, colorFilter = glassColorFilter(GlassSettings.style))
}

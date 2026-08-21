package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * The PDF viewer top bar's glass, as a modifier for surfaces that are not buttons.
 *
 * This is [LiquidIconButton] and [LiquidButton]'s effect stack verbatim — `vibrancy`, a 2 dp blur and
 * a 12x24 lens, with `highlight`/`shadow` left at `drawBackdrop`'s defaults and no inner shadow —
 * lifted off their hardcoded circle and capsule so a panel can wear the same material. Those two
 * components are read-only, hence the copy rather than a shared helper.
 *
 * Deliberately lighter than [liquidGlassPanel], which runs an 8 dp blur, a 20x40 depth lens, a
 * gravity-angled highlight and an inner shadow. That heavier stack is still right for dialogs: they
 * sit over a scrim and need an edge of their own. Chrome sits directly on the document and should read
 * as one material with the buttons floating on top of it.
 *
 * Takes an explicit [color] rather than resolving the theme, so it is not `@Composable` and each
 * viewer passes the same `chromeGlass` its buttons already use.
 */
/**
 * The corner curve [viewerGlass] paints by default.
 *
 * Hoisted out of the default argument because anything that *clips* to a viewerGlass surface has to
 * use the same curve — a clip and a paint that disagree by even a couple of dp reads as a chipped
 * edge. See [carouselEdges].
 */
val ViewerGlassShape: Shape = RoundedRectangle(28f.dp)

fun Modifier.viewerGlass(
    backdrop: Backdrop,
    color: Color,
    shape: () -> Shape = { ViewerGlassShape },
    // Off for surfaces that must NOT cast a drop shadow — e.g. the onboarding page-1 book, whose
    // shadow otherwise snapped in the moment the assembled book reached full opacity.
    withShadow: Boolean = true,
    // Panels sometimes host animated/scrolling controls that intentionally float beyond the glass
    // silhouette. The default preserves the original safe behavior for the rest of the app.
    clipContent: Boolean = true
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(2f.dp.toPx())
        lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    shadow = if (withShadow) ({ com.kyant.backdrop.shadow.Shadow.Default }) else null,
    clipContent = clipContent,
    onDrawSurface = {
        // When content is allowed to overflow, draw the tint as the glass outline instead of a
        // rectangular wash. This keeps the material rounded while its controls remain un-clipped.
        drawOutline(shape().createOutline(size, layoutDirection, this), color)
    }
)

/**
 * Soft, scroll-aware edge mask for a horizontally scrolling strip that rides on a [viewerGlass]
 * surface.
 *
 * **The bug this exists to kill.** `Modifier.horizontalScroll` clips to its node's *rectangular*
 * bounds, and [viewerGlass] only ever *draws* its rounded shape — `drawBackdrop` paints, it does not
 * clip. A scrolling row of chips on glass was therefore chopped by a straight vertical line sitting
 * inside the capsule's own curve: the one thing a floating glass control must never look like. The
 * mask below is a draw-time fade only — it deliberately does not hard-clip to the glass shape — so
 * controls remain visually above the surface while they are being dragged.
 *
 * Apply it **inside** the glass and **outside** the scroll, with the padding moved to the far end:
 *
 * ```
 * .viewerGlass(backdrop, glass)
 * .carouselEdges(state)
 * .horizontalScroll(state)
 * .padding(horizontal = 12.dp, vertical = 10.dp)
 * ```
 *
 * Two things about that order matter. The glass is drawn by the modifier to the *left*, so it is
 * outside this layer and the capsule keeps its full opacity at the edges — only the chips fade.
 * And padding after the scroll is *content* padding that travels with the content, so the first and
 * last chips rest with the same inset as the gaps between them yet can still reach the true edge
 * mid-scroll. Before the scroll it insets the viewport, which is what put the straight cut 12 dp in
 * from the curve.
 *
 * The fade reads [state] inside the draw lambda, so scrolling invalidates draw only and never
 * recomposes. It is absent at rest on the left, retires as you reach the right end, and is short
 * ([fade] defaults to 18 dp) — a hint that there is more to the side, not a vignette.
 */
fun Modifier.carouselEdges(
    state: ScrollState,
    shape: Shape = ViewerGlassShape,
    fade: Dp = 18.dp
): Modifier = this
    // Keep the edge fade isolated. `Offscreen` is not decoration: it is what makes the `DstIn` below
    // mask this strip rather than punch a hole through everything already on the canvas. `clip`
    // stays false on purpose; the glass panel owns the surface shape, while this scrolling content
    // remains a floating layer that is not chopped by the panel's corners.
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        clip = false
    }
    .drawWithContent {
        drawContent()
        val w = size.width
        val fadePx = fade.toPx()
        if (w <= fadePx * 2f) return@drawWithContent

        val max = state.maxValue
        // `maxValue` is Int.MAX_VALUE until the strip has been measured. Treated as "nothing to
        // scroll", otherwise a row that fits flashes a trailing fade on its first frame.
        val scrollable = max in 1..<Int.MAX_VALUE
        val lead = if (scrollable) (state.value / fadePx).coerceIn(0f, 1f) else 0f
        val trail = if (scrollable) ((max - state.value) / fadePx).coerceIn(0f, 1f) else 0f
        if (lead == 0f && trail == 0f) return@drawWithContent

        val edge = fadePx / w
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 1f - lead),
                edge to Color.Black,
                1f - edge to Color.Black,
                1f to Color.Black.copy(alpha = 1f - trail)
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * The neutral chrome tint the theme-driven viewers pair with [viewerGlass], so their panels and their
 * buttons resolve the same colour from one place instead of each screen writing the literal.
 *
 * The PDF viewer does *not* use this: its tint is chosen from the current page's luminance rather than
 * the app theme, because its chrome floats over the document itself.
 */
fun viewerChromeGlass(isDark: Boolean): Color =
    if (isDark) Color(0xFF20242C).copy(0.7f) else Color.White.copy(0.55f)

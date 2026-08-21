package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

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
    withShadow: Boolean = true
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(2f.dp.toPx())
        lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    shadow = if (withShadow) ({ com.kyant.backdrop.shadow.Shadow.Default }) else null,
    onDrawSurface = { drawRect(color) }
)

/**
 * Scroll metrics for a carousel whose interactive children must be able to paint beyond the
 * scrolling viewport. Unlike [ScrollState], this state is intentionally independent of
 * `horizontalScroll`: that modifier installs a clipping layer around its children, which turns a
 * pressed liquid chip into a flat-cut shape at the edge of the viewport.
 */
@Stable
class ViewerCarouselState internal constructor() {
    var value by mutableFloatStateOf(0f)
        private set

    var maxValue by mutableIntStateOf(0)
        private set

    internal fun updateMaxValue(newMaxValue: Int) {
        maxValue = newMaxValue.coerceAtLeast(0)
        if (value > maxValue) value = maxValue.toFloat()
    }

    internal fun consumeScrollDelta(delta: Float): Float {
        val previous = value
        value = (value + delta).coerceIn(0f, maxValue.toFloat())
        return value - previous
    }
}

@Composable
fun rememberViewerCarouselState(): ViewerCarouselState = remember { ViewerCarouselState() }

/**
 * A horizontally scrolling strip with an overflow-safe interactive layer.
 *
 * `scrollable` handles touch, mouse, accessibility and fling without imposing the child clipping
 * used by `horizontalScroll`. The small layout below measures the strip unbounded, updates the
 * scroll range, and translates the actual buttons itself. The parent therefore remains free to
 * draw the full liquid-button shape while the carousel keeps the same edge fade.
 */
@Composable
fun ViewerOverflowCarousel(
    modifier: Modifier = Modifier,
    state: ViewerCarouselState = rememberViewerCarouselState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val scrollableState = rememberScrollableState { delta -> state.consumeScrollDelta(delta) }
    Layout(
        modifier = modifier
            .carouselEdges(state, clipContent = false)
            .scrollable(scrollableState, Orientation.Horizontal),
        content = content
    ) { measurables, constraints ->
        val start = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
        val end = contentPadding.calculateEndPadding(layoutDirection).roundToPx()
        val left = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) start else end
        val right = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) end else start
        val top = contentPadding.calculateTopPadding().roundToPx()
        val bottom = contentPadding.calculateBottomPadding().roundToPx()
        val spacing = itemSpacing.roundToPx()
        val childConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = 0,
            maxHeight = (constraints.maxHeight - top - bottom).coerceAtLeast(0)
        )
        val placeables = measurables.map { it.measure(childConstraints) }
        val childrenWidth = placeables.sumOf { it.width }
        val gapsWidth = spacing * (placeables.size - 1).coerceAtLeast(0)
        val contentWidth = left + childrenWidth + gapsWidth + right
        val width = if (constraints.maxWidth == Constraints.Infinity) contentWidth else constraints.maxWidth
        val contentHeight = placeables.maxOfOrNull { it.height } ?: 0
        val height = (top + contentHeight + bottom).coerceIn(constraints.minHeight, constraints.maxHeight)
        state.updateMaxValue((contentWidth - width).coerceAtLeast(0))

        layout(width, height) {
            var x = left - state.value.roundToInt()
            placeables.forEach { placeable ->
                val y = top + ((height - top - bottom - placeable.height) / 2).coerceAtLeast(0)
                placeable.placeRelative(x, y)
                x += placeable.width + spacing
            }
        }
    }
}

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
    fade: Dp = 18.dp,
    // Keep the fade mask, but allow a caller with its own overflow-safe layer to opt out of
    // clipping the interactive children to the viewport's rounded outline.
    clipContent: Boolean = true
): Modifier = carouselEdges(state.value, state.maxValue, shape, fade, clipContent)

fun Modifier.carouselEdges(
    state: ViewerCarouselState,
    shape: Shape = ViewerGlassShape,
    fade: Dp = 18.dp,
    clipContent: Boolean = true
): Modifier = carouselEdges(state.value.roundToInt(), state.maxValue, shape, fade, clipContent)

private fun Modifier.carouselEdges(
    value: Int,
    max: Int,
    shape: Shape,
    fade: Dp,
    clipContent: Boolean
): Modifier = this
    // One layer does both jobs. `Offscreen` is not decoration: it is what makes the `DstIn` below
    // mask this strip rather than punch a hole through everything already on the canvas.
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        clip = clipContent
        this.shape = shape
    }
    .drawWithContent {
        drawContent()
        val w = size.width
        val fadePx = fade.toPx()
        if (w <= fadePx * 2f) return@drawWithContent

        // `maxValue` is Int.MAX_VALUE until the strip has been measured. Treated as "nothing to
        // scroll", otherwise a row that fits flashes a trailing fade on its first frame.
        val scrollable = max in 1..<Int.MAX_VALUE
        val lead = if (scrollable) (value / fadePx).coerceIn(0f, 1f) else 0f
        val trail = if (scrollable) ((max - value) / fadePx).coerceIn(0f, 1f) else 0f
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

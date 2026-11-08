package com.chethan616.clearpdf.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.drawBackdrop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser

/** The ink drawn on a glass control: white on a candy tint, else the theme's ink. */
val LocalGlassInk = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/**
 * The pressable glass both buttons share: refracts [LocalBackdrop], swells, leans, blooms and jiggles,
 * ticks once. A long-press only arms [onLongClick]; it runs when the finger lifts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.glassControl(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    tint: Color,
    surface: Color,
    blur: Dp,
    label: String?,
    fill: (() -> Color)? = null,
    tap: Feel? = Feel.Tap,
    bloom: Boolean = true,
    tintAt: (() -> Color)? = null,
    candyBase: Boolean = true,
): Modifier {
    val backdrop = LocalBackdrop.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val currentHaptics by rememberUpdatedState(haptics)
    val hasLongClick by rememberUpdatedState(onLongClick != null)
    val highlight = remember(scope, bloom) {
        InteractiveHighlight(scope, longPressExpand = bloom, onLongPressExpand = {
            if (!hasLongClick) currentHaptics?.play(Feel.Bloom)
        })
    }
    // an overlay opening (often by this very press) settles the press at once, so the scene under the
    // overlay stops changing and isn't redrawn under it frame after frame
    val overlays = LocalGlassOverlayHost.current
    if (overlays != null) LaunchedEffect(overlays, highlight) {
        androidx.compose.runtime.snapshotFlow { overlays.entries.isNotEmpty() }.collect { if (it) highlight.settle() }
    }
    val source = remember { MutableInteractionSource() }
    val armed = remember { booleanArrayOf(false) }
    val currentLongClick by rememberUpdatedState(onLongClick)
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Release -> if (armed[0]) { armed[0] = false; currentLongClick?.invoke() }
                is PressInteraction.Cancel -> armed[0] = false
            }
        }
    }
    val style = GlassSettings.style
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            shadow = null,
            effects = { glassEffects(style, blur.toPx(), 12f.dp.toPx(), 24f.dp.toPx()) },
            highlight = { glassHighlight(style) },
            layerBlock = { liquidPressTransform(highlight) },
            onDrawSurface = {
                // tintAt is read here, while drawing, so a colour that follows a pager recomposes nothing
                val t = tintAt?.invoke() ?: tint
                if (t.isSpecified) {
                    // a soft white base first, so the candy comes out vivid over dark and light alike
                    if (candyBase) drawRect(Color.White.copy(alpha = 0.42f))
                    drawRect(t, blendMode = BlendMode.Hue)
                    drawRect(t.copy(alpha = if (candyBase) 0.8f else 0.75f))
                }
                if (surface.isSpecified) drawRect(surface.glassTint(style))
                // read here, while drawing, so a colour animating in changes pixels and recomposes nothing
                val f = fill?.invoke()
                if (f != null && f.alpha > 0f) drawRect(f)
            },
        )
        .combinedClickable(
            interactionSource = source,
            indication = null,
            role = Role.Button,
            onClick = { if (tap != null && highlight.expandProgress < 0.5f) haptics?.play(tap); onClick() },
            onLongClick = if (onLongClick != null) ({ armed[0] = true; haptics?.play(Feel.Bloom) }) else null,
        )
        .then(highlight.modifier)
        .then(highlight.gestureModifier)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/** How long Back's glass plays before its screen goes: the press shows, then it springs back. */
const val BackBeatMillis = 300L

/**
 * [action] a beat after the tap, so a glass button's press and spring-back are seen before its screen
 * goes (Back). A second tap in the beat does nothing.
 */
@Composable
fun rememberBeat(action: () -> Unit, millis: Long = BackBeatMillis): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(action)
    val going = remember { booleanArrayOf(false) }
    return remember(scope, millis) {
        {
            if (!going[0]) {
                going[0] = true
                scope.launch {
                    delay(millis)
                    going[0] = false
                    latest()
                }
            }
        }
    }
}

/**
 * A glass bar or capsule that holds buttons of its own (a toolbar, a group of bar buttons): the glass
 * of every glass control, swelling, leaning and jiggling under the finger by Settings ▸ Motion as iOS
 * 26's bars do, while the buttons inside take the taps. Its tint and blur are the glass style's
 * (Settings ▸ liquid glass), so it shows what's under it, the page in the viewer.
 */
@Composable
fun Modifier.glassBar(shape: Shape = ChromeCapsule, surface: Color = Color.Unspecified, blur: Dp = 2.dp): Modifier {
    val backdrop = LocalBackdrop.current
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val style = GlassSettings.style
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            shadow = null,
            effects = { glassEffects(style, blur.toPx(), 12f.dp.toPx(), 24f.dp.toPx()) },
            highlight = { glassHighlight(style) },
            layerBlock = { liquidPressTransform(highlight) },
            onDrawSurface = { if (surface.isSpecified) drawRect(surface.glassTint(style)) },
        )
        .then(highlight.modifier)
        .then(highlight.gestureModifier)
}

/**
 * A glass capsule. With [tint] it's the vivid candy button (white ink); without, a neutral pill.
 */
@Composable
fun GlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    height: Dp = 48.dp,
    blur: Dp = 2.dp,
    horizontalPadding: Dp = 16.dp,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    tintAt: (() -> Color)? = null,
    tap: Feel? = Feel.Tap,
    content: @Composable RowScope.() -> Unit,
) {
    val candy = tint.isSpecified || tintAt != null
    val probe = if (candy) InkProbe(Modifier, Color.White) else rememberGlassInk(clear.ink)
    CompositionLocalProvider(LocalGlassInk provides probe.color) {
        Row(
            modifier
                .then(probe.modifier)
                .glassControl(ChromeCapsule, onClick, onLongClick, tint, if (tintAt != null) Color.Unspecified else surface, blur, label, tap = tap, bloom = true, tintAt = tintAt)
                .height(height)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * A small glass capsule that holds anything (a filter, a count, a checklist row, a fan item): the very
 * glass of [GlassPill], Back and the menu buttons, so it swells, leans and blooms the same way.
 * [fill] is an optional colour over the glass (a chosen chip's lemon, a ticked row), read while
 * drawing so it can animate for free. [tap] is the haptic on a tap; null leaves it to the caller.
 * [bloom] false keeps a long press from growing a control that has no long-press job.
 */
@Composable
fun GlassChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    // natural glass, as the header circles and the old app's chips: no wash of its own
    surface: Color = Color.Unspecified,
    fill: (() -> Color)? = null,
    blur: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    tap: Feel? = Feel.Tap,
    bloom: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    ink: Color = clear.ink,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGlassInk provides ink) {
        Row(
            modifier
                .glassControl(Capsule, onClick, onLongClick, Color.Unspecified, surface, blur, label, fill, tap, bloom)
                .height(height)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * The old app's capsule, continuous corners and all, for chrome that stays put (pills, bars): a path
 * clip costs nothing there. Chips and cards that scroll use the circular [Capsule].
 */
val ChromeCapsule = com.kyant.shapes.Capsule

/** The old app's header circle: 40 dp of natural glass (no wash), so every header reads the same. */
val HeaderButtonSize = 40.dp

/** The round glass button of a screen's header (Back, More, the desk's menu). */
@Composable
fun HeaderGlassButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = HeaderButtonSize, content: @Composable () -> Unit) {
    GlassCircle(onClick = onClick, modifier = modifier, size = size, label = label, ink = clear.ink) { content() }
}

/** Material's rounded arrow_back_ios_new, the old app's Back glyph. */
val BackArrow: ImageVector by lazy {
    ImageVector.Builder("BackArrow", 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(
            "M16.62,2.99c-0.49,-0.49 -1.28,-0.49 -1.77,0L6.54,11.3c-0.39,0.39 -0.39,1.02 0,1.41l8.31,8.31c0.49,0.49 1.28,0.49 1.77,0 0.49,-0.49 0.49,-1.28 0,-1.77L9.38,12l7.25,-7.25c0.48,-0.48 0.48,-1.28 -0.01,-1.76z"
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Back, as the old app's: a 40 dp glass circle, a 16 dp arrow, leaving a beat after the press. */
@Composable
fun GlassBackButton(onBack: () -> Unit, modifier: Modifier = Modifier, label: String? = null, immediate: Boolean = false) {
    val beat = rememberBeat(onBack)
    GlassCircle(
        onClick = if (immediate) onBack else beat,
        modifier = modifier,
        size = HeaderButtonSize,
        label = label ?: androidx.compose.ui.res.stringResource(com.chethan616.clearpdf.R.string.common_back),
    ) {
        GlassIcon(BackArrow, null, Modifier.size(16.dp))
    }
}

/**
 * An icon button inside a glass bar or capsule: no glass of its own (glass on glass reads as two
 * buttons stacked), just the glyph in the bar's ink and a soft press.
 */
@Composable
fun BarGlyphButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).pressable(onClick, enabled = enabled, pressedScale = 0.88f).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A round glass button, usually an icon. */
@Composable
fun GlassCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = HeaderButtonSize,
    tint: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    ink: Color = Color.Unspecified,
    tap: Feel? = Feel.Tap,
    content: @Composable () -> Unit,
) {
    // over a document the glyph takes dark or white ink from the page around it, as the old app's
    val probe = if (tint.isSpecified) InkProbe(Modifier, Color.White) else rememberGlassInk(if (ink.isSpecified) ink else clear.ink)
    CompositionLocalProvider(LocalGlassInk provides probe.color) {
        Box(
            modifier.then(probe.modifier).size(size).glassControl(CircleShape, onClick, onLongClick, tint, surface, 2.dp, label, tap = tap, bloom = true, candyBase = false),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

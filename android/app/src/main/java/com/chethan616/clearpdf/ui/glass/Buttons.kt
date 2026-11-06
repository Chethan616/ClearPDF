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
import com.kyant.shapes.Capsule
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape

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
    val source = remember { MutableInteractionSource() }
    val armed = remember { booleanArrayOf(false) }
    val currentLongClick by rememberUpdatedState(onLongClick)
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> currentHaptics?.play(Feel.Press)
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
                    drawRect(Color.White.copy(alpha = 0.42f))
                    drawRect(t, blendMode = BlendMode.Hue)
                    drawRect(t.copy(alpha = 0.82f))
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
fun Modifier.glassBar(shape: Shape = Capsule, surface: Color = clear.glass, blur: Dp = 6.dp): Modifier {
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
    surface: Color = if (tint.isSpecified) Color.Unspecified else clear.glass,
    height: Dp = 48.dp,
    blur: Dp = 2.dp,
    horizontalPadding: Dp = 18.dp,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    tintAt: (() -> Color)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val ink = if (tint.isSpecified || tintAt != null) Color.White else clear.ink
    CompositionLocalProvider(LocalGlassInk provides ink) {
        Row(
            modifier
                .glassControl(Capsule, onClick, onLongClick, tint, if (tintAt != null) Color.Unspecified else surface, blur, label, bloom = onLongClick != null, tintAt = tintAt)
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
    surface: Color = clear.glass,
    fill: (() -> Color)? = null,
    blur: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    tap: Feel? = Feel.Tap,
    bloom: Boolean = false,
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
 * The round glass button of a screen's header (Back, More, the desk's menu): 54 dp of the same
 * plain glass every glass button wears, ink in [GlassIcon]s white on the sky. Never ringed or
 * dark-filled: one glass, one lit rim.
 */
@Composable
fun HeaderGlassButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = 54.dp, content: @Composable () -> Unit) {
    GlassCircle(onClick = onClick, modifier = modifier, size = size, label = label) {
        CompositionLocalProvider(LocalGlassInk provides clear.hero) { content() }
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
    size: Dp = 44.dp,
    tint: Color = Color.Unspecified,
    surface: Color = if (tint.isSpecified) Color.Unspecified else clear.glass,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    val ink = if (tint.isSpecified) Color.White else clear.ink
    CompositionLocalProvider(LocalGlassInk provides ink) {
        Box(
            modifier.size(size).glassControl(CircleShape, onClick, onLongClick, tint, surface, 2.dp, label, bloom = onLongClick != null),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

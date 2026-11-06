package com.chethan616.clearpdf.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.drawBackdrop
import com.kyant.shapes.RoundedRectangle

/**
 * A still glass surface (bars, the dock's body, panels, sheets): refracts [LocalBackdrop] through
 * [shape], washed with [wash]. No shadow, ever.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    wash: Color = clear.glass,
    blur: Dp = 8.dp,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 24.dp,
): Modifier {
    val backdrop = LocalBackdrop.current
    val style = GlassSettings.style
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        shadow = null,
        effects = { glassEffects(style, blur.toPx(), lensHeight.toPx(), lensAmount.toPx()) },
        highlight = { glassHighlight(style) },
        onDrawSurface = { if (wash.isSpecified) drawRect(wash.glassTint(style)) },
    )
}

/**
 * A glass card whose children refract the card itself: its surface is a sibling layer under [content],
 * so a glass button inside reads as part of the same frosted sheet, not a hole punched through it.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(28.dp),
    wash: Color = clear.glass,
    blur: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val below = LocalBackdrop.current
    val layer = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(below, layer)
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(layer).glass(shape, wash, blur))
        CompositionLocalProvider(LocalBackdrop provides combined) { Column(content = content) }
    }
}

/**
 * A painted surface (a desk card) that glass can sit on: [paint] is drawn as a sibling layer under
 * [content], so a [GlassChip] inside refracts the card's own colour instead of the wallpaper behind it.
 * (A glass control can't refract the layer it is drawn in, hence two layers.)
 */
@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    paint: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val below = LocalBackdrop.current
    val layer = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(below, layer)
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(layer), content = paint)
        CompositionLocalProvider(LocalBackdrop provides combined) { content() }
    }
}

/**
 * The wash panel scrolling lists use: the glass's tint and its lit rim without the backdrop pass.
 * Over the aurora's soft gradients a blur changes almost nothing, but re-sampling every panel on
 * every scrolled frame costs a lot; floating chrome keeps the real glass.
 */
@Composable
fun Modifier.wash(shape: Shape = RoundedRectangle(28.dp), tint: Color = clear.glass): Modifier {
    val dark = clear.dark
    val rimTop = Color.White.copy(alpha = if (dark) 0.16f else 0.70f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.03f else 0.18f)
    return clip(shape)
        .background(tint.glassTint(GlassSettings.style))
        .border(1.dp, Brush.verticalGradient(listOf(rimTop, rimBottom)), shape)
}

/** The [wash] of a panel (a file card, a group of rows), with its 20 dp continuous corners. */
@Composable
fun washPanel(): Modifier = Modifier.wash(RoundedRectangle(20.dp))

/** A [wash] panel holding rows. */
@Composable
fun WashPanel(modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(28.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.wash(shape), content = content)
}

/** A glass panel holding rows: 28 dp continuous corners. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = RoundedRectangle(28.dp), wash: Color = clear.glass, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(shape, wash), content = content)
}

/**
 * A flat pressable (a row, a tile, a card): sinks under the finger and eases back (no bounce: that
 * is the glass buttons'), one haptic per tap. No ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = Fluid.PressedScale,
    role: Role = Role.Button,
    enabled: Boolean = true,
    feel: Feel = Feel.Tap,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val jelly = rememberJellyPress(source, pressedScale)
    val haptics = LocalHaptics.current
    return graphicsLayer {
        scaleX = jelly.scale
        scaleY = jelly.scale
    }.combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = { haptics?.play(feel); onClick() },
        onLongClick = onLongClick?.let { l -> { haptics?.play(Feel.Bloom); jelly.rest(); l() } },
    )
}

/** An icon in the ink of the glass it sits on. */
@Composable
fun GlassIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val ink = when {
        tint.isSpecified -> tint
        LocalGlassInk.current.isSpecified -> LocalGlassInk.current
        else -> clear.ink
    }
    Icon(icon, contentDescription, modifier, tint = ink)
}

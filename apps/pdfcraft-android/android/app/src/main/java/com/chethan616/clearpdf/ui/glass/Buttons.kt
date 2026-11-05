package com.chethan616.clearpdf.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
): Modifier {
    val backdrop = LocalBackdrop.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val hasLongClick by rememberUpdatedState(onLongClick != null)
    val highlight = remember(scope) {
        InteractiveHighlight(scope, longPressExpand = true, onLongPressExpand = {
            if (!hasLongClick) currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        })
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
                if (tint.isSpecified) {
                    // a soft white base first, so the candy comes out vivid over dark and light alike
                    drawRect(Color.White.copy(alpha = 0.42f))
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.82f))
                }
                if (surface.isSpecified) drawRect(surface.glassTint(style))
            },
        )
        .combinedClickable(
            interactionSource = source,
            indication = null,
            role = Role.Button,
            onClick = { if (highlight.expandProgress < 0.5f) haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() },
            onLongClick = if (onLongClick != null) ({ armed[0] = true; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }) else null,
        )
        .then(highlight.modifier)
        .then(highlight.gestureModifier)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
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
    content: @Composable RowScope.() -> Unit,
) {
    val ink = if (tint.isSpecified) Color.White else clear.ink
    CompositionLocalProvider(LocalGlassInk provides ink) {
        Row(
            modifier
                .glassControl(Capsule, onClick, onLongClick, tint, surface, blur, label)
                .height(height)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
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
            modifier.size(size).glassControl(CircleShape, onClick, onLongClick, tint, surface, 2.dp, label),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

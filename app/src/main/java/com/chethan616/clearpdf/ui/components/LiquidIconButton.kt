package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.utils.InteractiveHighlight
import com.chethan616.clearpdf.ui.utils.liquidPressTransform
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.shape.CircleShape

/**
 * Circular variant of LiquidButton, optimized for icons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    // combinedClickable already buzzes when its own onLongClick fires; only add ours when there is none.
    val hasOwnLongClick by rememberUpdatedState(onLongClick != null)
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            longPressExpand = true,
            onLongPressExpand = {
                if (!hasOwnLongClick) currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
    }
    // Flat wallpaper: same pixels, none of the blur/lens/offscreen work — see FlatBackdrop.
    val flat = flatColorOf(backdrop)
    val style = GlassSettings.style
    // A long-press only ARMS its action; it runs when the finger lifts (a cancelled press drops it),
    // so holding Back never yanks the screen away mid-press.
    val pressSource = remember { MutableInteractionSource() }
    val longArmed = remember { booleanArrayOf(false) }
    val currentLongClick by rememberUpdatedState(onLongClick)
    LaunchedEffect(pressSource) {
        pressSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Release -> if (longArmed[0]) { longArmed[0] = false; currentLongClick?.invoke() }
                is PressInteraction.Cancel -> longArmed[0] = false
            }
        }
    }

    Box(
        modifier
            .size(40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                shadow = null,
                effects = if (flat != null) ({}) else ({
                    glassEffects(style, 2f.dp.toPx(), 12f.dp.toPx(), 24f.dp.toPx())
                }),
                highlight = { glassHighlight(style) },
                onDrawBackdrop = if (flat != null) ({ _ -> drawFlatVibrantBackdrop(flat) }) else ({ it() }),
                layerBlock = if (isInteractive) {
                    { liquidPressTransform(interactiveHighlight) }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor.glassTint(style))
                    }
                }
            )
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(
                        interactionSource = pressSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() },
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = { longArmed[0] = true }
                    )
                else
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = { if (interactiveHighlight.expandProgress < 0.5f) haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }
                    )
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

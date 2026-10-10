package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.liquidPressGlow

/**
 * The app's list-row tap: springs down a touch under the finger, the chips' press light blooms from
 * the touch point, one light haptic — instead of a Material ripple, which reads as Android-stock
 * next to liquid glass. Apply in place of `clickable` on menu / panel rows.
 */
@Composable
fun Modifier.liquidRowClick(
    enabled: Boolean = true,
    corner: Dp = 14.dp,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val jelly = rememberJellyPress(interaction, pressedScale = 0.97f)
    return this
        .graphicsLayer { scaleX = jelly.scale; scaleY = jelly.scale }
        .clip(RoundedCornerShape(corner))
        .liquidPressGlow(interaction, onLight = !LocalIsDarkMode.current)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

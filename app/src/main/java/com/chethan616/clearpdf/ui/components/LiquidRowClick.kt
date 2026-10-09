package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, GlassMotion.press(), label = "rowPress")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(corner))
        .liquidPressGlow(interaction, onLight = !LocalIsDarkMode.current)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

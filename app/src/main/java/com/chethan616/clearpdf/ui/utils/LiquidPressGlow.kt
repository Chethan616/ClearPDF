package com.chethan616.clearpdf.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.collectLatest

/**
 * The glass chips' press light — a soft wash plus a radial glow blooming from the touch point — for
 * surfaces that are not [com.chethan616.clearpdf.ui.components.LiquidButton]s, such as list rows.
 *
 * Driven by [interactionSource]'s press interactions rather than raw pointer input, so it inherits
 * the clickable's scroll tap-delay: starting a scroll on a row never flashes it. A quick tap still
 * gets a visible pulse (it rises to half strength before fading), like an iOS cell highlight.
 *
 * Draws under the content. Over a light surface the glow is a gentle darkening (a white Plus glow is
 * invisible on white); over a dark one it is light, as on the chips.
 */
@Composable
fun Modifier.liquidPressGlow(interactionSource: InteractionSource, onLight: Boolean): Modifier {
    val progress = remember { Animatable(0f, 0.001f) }
    // Not state: read in draw only, alongside `progress`, which already invalidates the draw.
    val origin = remember { arrayOf(Offset.Unspecified) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    origin[0] = interaction.pressPosition
                    progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (interaction is PressInteraction.Release && progress.value < 0.5f) {
                        progress.animateTo(0.5f, tween(70))
                    }
                    progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 260f))
                }
            }
        }
    }
    return drawWithContent {
        val p = progress.value
        if (p > 0f) {
            val c = origin[0].takeIf { it.isSpecified } ?: center
            val radius = size.minDimension * 1.6f
            if (onLight) {
                drawRect(Color.Black.copy(alpha = 0.035f * p))
                drawCircle(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.06f * p), Color.Transparent), c, radius),
                    radius = radius,
                    center = c
                )
            } else {
                drawRect(Color.White.copy(alpha = 0.06f * p), blendMode = BlendMode.Plus)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f * p), Color.Transparent), c, radius),
                    radius = radius,
                    center = c,
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }
}

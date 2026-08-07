package com.chethan616.clearpdf.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R

/** A lightweight, WhatsApp-style doodle layer built from the provided SVG art. */
@Composable
fun DoodleBackdrop(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val ink = if (isDarkMode) Color(0xFF9AC9FF) else Color(0xFF35658F)
    val base = if (isDarkMode) Color(0xFF101418) else Color(0xFFF3F7FB)
    val transition = rememberInfiniteTransition(label = "doodle-drift")
    val drift by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "doodle-drift"
    )

    Box(modifier.background(base)) {
        Doodle(
            ink = ink,
            alpha = if (isDarkMode) 0.08f else 0.055f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-28).dp)
                .size(246.dp)
                .graphicsLayer {
                    translationX = drift
                    rotationZ = -8f
                }
        )
        Doodle(
            ink = ink,
            alpha = if (isDarkMode) 0.065f else 0.045f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-82).dp, y = 54.dp)
                .size(232.dp)
                .graphicsLayer {
                    translationX = -drift * 0.7f
                    rotationZ = 14f
                }
        )
        Doodle(
            ink = ink,
            alpha = if (isDarkMode) 0.045f else 0.03f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 108.dp, y = 10.dp)
                .size(180.dp)
                .graphicsLayer {
                    translationY = drift * 0.45f
                    rotationZ = 22f
                }
        )
    }
}

@Composable
private fun Doodle(ink: Color, alpha: Float, modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.clearpdf_doodle_cat),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(ink),
        modifier = modifier.alpha(alpha)
    )
}

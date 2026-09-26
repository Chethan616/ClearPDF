package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * Capsule progress bar. `progress == null` renders an indeterminate sweeping shimmer; otherwise the
 * fill animates (critically-damped spring) to [progress] in 0..1. With a [backdrop] the track is a
 * frosted glass capsule; without one it is a translucent tinted track.
 */
@Composable
fun GlassProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    color: Color = LiquidGlassColors.Blue,
    height: Dp = 6.dp
) {
    val isDark = LocalIsDarkMode.current
    val trackColor = if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.08f)
    val animated by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.001f),
        label = "glassProgress"
    )
    val infinite = rememberInfiniteTransition(label = "glassProgressIndeterminate")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    val base = modifier
        .fillMaxWidth()
        .height(height)
        .semantics {
            progressBarRangeInfo = if (progress == null) ProgressBarRangeInfo.Indeterminate
            else ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        }
    val withTrack = if (backdrop != null) {
        base.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(trackColor) }
        ).clip(Capsule)
    } else base.clip(Capsule)

    Canvas(withTrack) {
        val r = CornerRadius(size.height / 2f)
        if (backdrop == null) drawRoundRect(trackColor, cornerRadius = r)
        if (progress == null) {
            // Indeterminate: a 40%-wide soft-edged segment sweeping across, eased at both ends.
            val segW = size.width * 0.4f
            val x = -segW + (size.width + segW) * sweep
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to color.copy(alpha = 0f),
                    0.3f to color,
                    0.7f to color,
                    1f to color.copy(alpha = 0f),
                    startX = x,
                    endX = x + segW
                ),
                topLeft = Offset(x, 0f),
                size = Size(segW, size.height),
                cornerRadius = r
            )
        } else {
            val w = size.width * animated
            if (w > 0f) {
                drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                // Moving specular shimmer on the filled part.
                val sx = -size.width * 0.3f + size.width * 1.6f * sweep
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.35f),
                        1f to Color.White.copy(alpha = 0f),
                        startX = sx,
                        endX = sx + size.width * 0.3f
                    ),
                    size = Size(w.coerceAtLeast(size.height), size.height),
                    cornerRadius = r
                )
            }
        }
    }
}

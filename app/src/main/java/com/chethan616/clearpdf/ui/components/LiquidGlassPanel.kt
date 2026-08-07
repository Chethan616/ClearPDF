package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

@Composable
fun Modifier.liquidGlassPanel(
    backdrop: Backdrop,
    uiSensor: UISensor
): Modifier {
    val isDarkMode = LocalIsDarkMode.current
    val isLightTheme = !isDarkMode
    // Match the stronger, dimensional glass language used by the Tools cards.
    val containerColor = if (isLightTheme) Color.White.copy(0.34f) else Color(0xFF111216).copy(0.46f)
    val rimColor = if (isLightTheme) Color.White.copy(0.52f) else Color.White.copy(0.16f)
    val surfaceHighlight = remember(isLightTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isLightTheme) 0.14f else 0.08f),
                Color.Transparent
            )
        )
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(28f.dp) },
        effects = {
            vibrancy()
            blur(6f.dp.toPx())
            lens(18f.dp.toPx(), 34f.dp.toPx(), depthEffect = true)
        },
        highlight = { Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f)) },
         shadow = { Shadow(radius = 7f.dp, color = Color.Black.copy(alpha = 0.10f)) },
         innerShadow = { InnerShadow(radius = 3f.dp, alpha = 0.32f) },
        onDrawSurface = {
            drawRect(containerColor)
            drawRect(surfaceHighlight)
            drawRoundRect(
                color = rimColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f.dp.toPx()),
                style = Stroke(width = 1f.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    )
}

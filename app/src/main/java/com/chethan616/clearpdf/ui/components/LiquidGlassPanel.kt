package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.45f) else Color(0xFF141414).copy(0.55f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(28f.dp) },
        effects = {
            vibrancy()
            blur(8f.dp.toPx())
            lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true)
        },
        highlight = { Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f)) },
        shadow = { Shadow(radius = 10f.dp, color = Color.Black.copy(alpha = 0.16f)) },
        innerShadow = { InnerShadow(radius = 6f.dp, alpha = 0.55f) },
        onDrawSurface = {
            drawRect(containerColor)
            val vignette = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(0.22f)),
                center = center,
                radius = maxOf(size.width, size.height) * 0.75f
            )
            drawRect(vignette)
        }
    )
}

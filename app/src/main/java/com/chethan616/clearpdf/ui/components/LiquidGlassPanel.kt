package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF1E1E1E).copy(0.4f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(28f.dp) },
        effects = {
            vibrancy()
            blur(8f.dp.toPx())
            lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true)
        },
        highlight = { Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f)) },
        shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.1f)) },
        innerShadow = { InnerShadow(radius = 3f.dp, alpha = 0.3f) },
        onDrawSurface = { drawRect(containerColor) }
    )
}

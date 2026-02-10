package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kyant.shapes.Capsule

@Composable
fun LiquidGlassTopBar(
    title: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isLightTheme = !isSystemInDarkTheme()
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.35f) else Color(0xFF1E1E1E).copy(0.35f)
    val titleColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                highlight = {
                    Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))
                },
                shadow = { Shadow(radius = 6f.dp, color = Color.Black.copy(alpha = 0.08f)) },
                innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.25f) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = titleColor, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

package com.chethan616.clearpdf.ui.components

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
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
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
import com.kyant.shapes.Capsule

@Composable
fun LiquidGlassTopBar(
    title: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLightTheme = !isDarkMode
    val containerColor = if (isLightTheme) Color.White.copy(0.34f) else Color(0xFF111216).copy(0.42f)
    val titleColor = LiquidGlassColors.text(!isLightTheme)

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = {
                    Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))
                },
                shadow = { Shadow(radius = 6f.dp, color = Color.Black.copy(alpha = 0.08f)) },
                innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.25f) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = titleColor, fontSize = titleFontSize, fontWeight = fontWeight),
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

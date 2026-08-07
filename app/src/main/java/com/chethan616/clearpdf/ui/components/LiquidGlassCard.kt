package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.InteractiveHighlight
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidGlassCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    backdrop: Backdrop,
    uiSensor: UISensor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val container = if (isLight) Color.White.copy(0.34f) else Color(0xFF111216).copy(0.46f)
    val titleColor = LiquidGlassColors.text(!isLight)
    val subtitleColor = LiquidGlassColors.secondary(!isLight)

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24f.dp) },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(18f.dp.toPx(), 34f.dp.toPx(), depthEffect = true)
                },
                highlight = {
                    Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))
                },
                shadow = { Shadow(radius = 7f.dp, color = Color.Black.copy(alpha = 0.10f)) },
                innerShadow = { InnerShadow(radius = 3f.dp, alpha = 0.32f) },
                layerBlock = {
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                    val maxOffset = size.minDimension
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(0.04f * offset.x / maxOffset)
                    translationY = maxOffset * tanh(0.04f * offset.y / maxOffset)
                    val maxDragScale = 3f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension)
                    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension)
                },
                 onDrawSurface = { drawRect(container) }
             )
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .height(160.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TextStyle(titleColor, 16.sp, FontWeight.Bold))
            BasicText(subtitle, style = TextStyle(subtitleColor, 12.sp))
        }
    }
}

@Composable
fun LiquidGlassErrorCard(
    message: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val errorRed = Color(0xFFEF5350)
    val errorBg = if (isLight) Color(0xFFFFEBEE).copy(0.6f) else Color(0xFF2C1C1C).copy(0.6f)
    val textColor = if (isLight) Color(0xFFC62828) else Color(0xFFFF8A80)

    Row(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(errorRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(20.dp), errorRed)
        }
        BasicText(
            message,
            style = TextStyle(textColor, 13.sp, FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            LiquidIconButton(
                onClick = onDismiss,
                backdrop = backdrop,
                tint = errorRed,
                modifier = Modifier.size(30.dp)
            ) {
                CloseCrossIcon(Modifier.size(12.dp), Color.White)
            }
        }
    }
}

package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * Horizontally scrollable liquid-glass capsule dock for editor tools. Content scrolls inside the
 * glass; the leading/trailing edges fade out only while there is more content in that direction.
 * Use [GlassToolButton] for the items.
 */
@Composable
fun GlassToolbar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val containerColor = if (isDark) Color(0xFF1E1E1E).copy(0.42f) else Color(0xFFFAFAFA).copy(0.42f)
    val scroll = rememberScrollState()

    Box(
        modifier
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.10f)) },
                innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.25f) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .clip(Capsule),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 24.dp.toPx()
                    val w = size.width
                    if (w <= 0f) return@drawWithContent
                    val startA = if (scroll.canScrollBackward) 0f else 1f
                    val endA = if (scroll.canScrollForward) 0f else 1f
                    if (startA < 1f || endA < 1f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = startA),
                                (fade / w).coerceAtMost(0.5f) to Color.Black,
                                (1f - fade / w).coerceAtLeast(0.5f) to Color.Black,
                                1f to Color.Black.copy(alpha = endA)
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Tool button for [GlassToolbar]: icon with optional caption, >=48dp target, accent-tinted pill when
 * [selected], press-scale spring and a light haptic tick.
 */
@Composable
fun GlassToolButton(
    icon: ImageVector,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LiquidGlassColors.Blue
) {
    val isDark = LocalIsDarkMode.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) GlassMotion.PressedScale else 1f,
        GlassMotion.press(),
        label = "toolPress"
    )
    val fg by animateColorAsState(
        if (selected) accent else LiquidGlassColors.text(isDark),
        GlassMotion.settle(),
        label = "toolFg"
    )
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = if (isDark) 0.24f else 0.16f) else Color.Transparent,
        GlassMotion.settle(),
        label = "toolBg"
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        if (label != null) {
            BasicText(
                label,
                style = TextStyle(
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

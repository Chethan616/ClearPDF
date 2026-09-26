package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Liquid-glass alert dialog (catalog DialogContent recipe) rendered **in-hierarchy** so it can
 * refract [backdrop]. Place it as the last child of a full-screen Box whose content records into
 * [backdrop] (a platform Dialog window could not sample the app's backdrop layer).
 *
 * Entrance: scrim fades in, panel scales 0.88 -> 1 with a spring while the lens strength ramps with
 * the same progress (ControlCenter-style progressive lens). Back press and scrim tap call [onDismiss].
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    title: String?,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f, visibilityThreshold = 0.001f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.001f))
            composed = false
        }
    }
    if (!composed) return

    BackHandler(enabled = visible) { onDismiss() }

    val isLight = !LocalIsDarkMode.current
    val containerColor = if (isLight) Color(0xFFFAFAFA).copy(0.6f) else Color(0xFF121212).copy(0.4f)
    val dimColor = if (isLight) Color(0xFF29293A).copy(0.23f) else Color(0xFF121212).copy(0.56f)
    val contentColor = LiquidGlassColors.text(!isLight)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.value.fastCoerceIn(0f, 1f)
                drawRect(dimColor.copy(alpha = dimColor.alpha * p))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible && dismissOnScrimTap,
                onClick = onDismiss
            )
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progress.value
                    val s = 0.88f + 0.12f * p
                    scaleX = s
                    scaleY = s
                    alpha = p.fastCoerceIn(0f, 1f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        val p = progress.value.fastCoerceIn(0f, 1f)
                        colorControls(
                            brightness = if (isLight) 0.2f else 0f,
                            saturation = 1.5f
                        )
                        blur(if (isLight) 16f.dp.toPx() else 8f.dp.toPx())
                        lens(24f.dp.toPx() * p, 48f.dp.toPx() * p, depthEffect = true, chromaticAberration = true)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 24f.dp, color = Color.Black.copy(alpha = 0.12f)) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                // Swallow taps so they don't reach the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .semantics { if (title != null) paneTitle = title }
        ) {
            if (title != null) {
                BasicText(
                    title,
                    Modifier.padding(28f.dp, 24f.dp, 28f.dp, 8f.dp),
                    style = TextStyle(contentColor, 22f.sp, FontWeight.SemiBold)
                )
            } else {
                Box(Modifier.height(16.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24f.dp, vertical = 8f.dp)) {
                content()
            }
            Row(
                Modifier
                    .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/**
 * Capsule action for [GlassDialog]'s `actions` slot (catalog style): secondary = faint glass fill,
 * primary = solid accent, destructive = red text.
 */
@Composable
fun RowScope.GlassDialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    val haptics = LocalHapticFeedback.current
    val accent = if (destructive) LiquidGlassColors.Red else LiquidGlassColors.Blue
    val bg = when {
        primary -> accent
        isDark -> Color.White.copy(0.10f)
        else -> Color.Black.copy(0.06f)
    }
    val fg = when {
        primary -> Color.White
        destructive -> LiquidGlassColors.Red
        else -> LiquidGlassColors.text(isDark)
    }
    Row(
        modifier
            .weight(1f)
            .height(48.dp)
            .clip(Capsule)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(text, style = TextStyle(fg, 16.sp, if (primary) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
    }
}

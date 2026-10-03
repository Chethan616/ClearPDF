package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle

/** One row of a [GlassActionMenu]: an accent-coloured icon badge, a label and an optional subtitle. */
data class GlassActionItem(
    val icon: ImageVector,
    val label: String,
    val accent: Color,
    val subtitle: String? = null,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * iOS-style context menu: ONE frosted glass panel that grows out of its anchor corner (the button
 * that opened it), with accent-badged rows and hairline separators. Rows are plain content on the
 * panel — not glass buttons stacked on glass — so they can't pick up the page behind the menu and go
 * illegible. Ink follows the app theme.
 *
 * Place it as the last child of a full-screen Box. [alignment] is the corner it docks to and grows
 * from; [contentPadding] positions it above the control that opened it. Tap outside or Back
 * dismisses; picking a row dismisses first, then runs the action.
 */
@Composable
fun GlassActionMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    items: List<GlassActionItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
    alignment: Alignment = Alignment.BottomEnd,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 520f, visibilityThreshold = 0.001f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.001f))
            composed = false
        }
    }
    if (!composed) return
    BackHandler(enabled = visible) { onDismiss() }

    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val soft = LiquidGlassColors.secondary(isDark)
    val haptics = LocalHapticFeedback.current
    val pivot = when (alignment) {
        Alignment.BottomStart -> TransformOrigin(0f, 1f)
        Alignment.TopEnd -> TransformOrigin(1f, 0f)
        Alignment.TopStart -> TransformOrigin(0f, 0f)
        Alignment.BottomCenter -> TransformOrigin(0.5f, 1f)
        else -> TransformOrigin(1f, 1f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.value.fastCoerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = (if (isDark) 0.30f else 0.14f) * p))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible,
                onClick = onDismiss
            )
            .padding(contentPadding)
    ) {
        Column(
            modifier
                .align(alignment)
                .widthIn(min = 236.dp, max = 300.dp)
                .graphicsLayer {
                    val p = progress.value
                    val s = lerp(0.62f, 1f, p)
                    scaleX = s
                    scaleY = s
                    alpha = p.fastCoerceIn(0f, 1f)
                    transformOrigin = pivot
                }
                .viewerGlass(backdrop, glassDialogSurface(isDark), shape = { RoundedRectangle(28.dp) })
                // Swallow taps so they don't fall through to the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(vertical = 6.dp)
        ) {
            if (title != null) {
                BasicText(
                    title,
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
                    style = TextStyle(soft, 13.sp, FontWeight.SemiBold),
                    maxLines = 1
                )
            }
            items.forEachIndexed { i, item ->
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(start = 62.dp, end = 14.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(ink.copy(alpha = 0.12f))
                    )
                }
                // Rows cascade in a beat after the panel, iOS-menu style. Draw-time only.
                val rowIn by animateFloatAsState(
                    if (visible) 1f else 0f,
                    spring(dampingRatio = 0.86f, stiffness = 420f - i * 40f),
                    label = "menuRow$i"
                )
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .graphicsLayer {
                            alpha = rowIn
                            translationY = (1f - rowIn) * 10.dp.toPx()
                        }
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .drawBehind { if (pressed) drawRect(ink.copy(alpha = 0.08f)) }
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (item.active) item.accent else item.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, null, Modifier.size(19.dp), if (item.active) Color.White else item.accent)
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            item.label,
                            style = TextStyle(ink, 16.sp, FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.subtitle != null) {
                            BasicText(
                                item.subtitle,
                                style = TextStyle(soft, 12.5.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (item.active) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(item.accent))
                    }
                }
            }
            Box(Modifier.width(1.dp).height(0.dp))
        }
    }
}

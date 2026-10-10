package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS-style segmented control on a liquid-glass capsule track, with a sliding glass thumb.
 *
 * Gesture handling is done in a single pointer loop so a tap and a drag each fire [onSelect]
 * exactly once (the LiquidToggle double-fire came from stacking `clickable` on top of a drag
 * detector). [onSelect] is only called when the index actually changes.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    height: androidx.compose.ui.unit.Dp = 44.dp,
    enabled: Boolean = true
) {
    val count = options.size.coerceAtLeast(1)
    val isDark = LocalIsDarkMode.current
    val textColor = LiquidGlassColors.text(isDark)
    val secondary = LiquidGlassColors.secondary(isDark)
    val trackColor = if (isDark) Color(0xFF1E1E1E).copy(0.40f) else Color(0xFFFAFAFA).copy(0.40f)
    val thumbColor = if (isDark) Color.White.copy(0.18f) else Color.White.copy(0.85f)
    val haptics = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)

    // Thumb position in "segment index" units (0f..count-1).
    val position = remember { Animatable(selectedIndex.toFloat().fastCoerceIn(0f, (count - 1).toFloat())) }
    val press = remember { Animatable(0f) }
    val stretch = rememberLiquidStretchState(stretchFactor = 0.35f, minScale = 0.8f, maxScale = 1.25f)
    var segPx by remember { mutableFloatStateOf(1f) }
    var hoverIndex by remember { mutableStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex, count) {
        hoverIndex = selectedIndex
        position.animateTo(
            selectedIndex.toFloat().fastCoerceIn(0f, (count - 1).toFloat()),
            spring(dampingRatio = 0.72f, stiffness = 380f)
        ) { stretch.onPositionSample(value * segPx) }
    }

    BoxWithConstraints(
        modifier
            .height(height)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(10f.dp.toPx(), 20f.dp.toPx())
                },
                shadow = null,
                onDrawSurface = { drawRect(trackColor) }
            )
            .semantics { role = Role.RadioButton }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(count, isLtr) {
                    val segW = size.width.toFloat() / count
                    segPx = segW
                    fun indexAt(x: Float): Int {
                        val lx = if (isLtr) x else size.width - x
                        return (lx / segW).toInt().coerceIn(0, count - 1)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startIndex = indexAt(down.position.x)
                        val onThumb = startIndex == currentSelected
                        var dragged = false
                        var totalDx = 0f
                        var cancelled = false
                        val slop = viewConfiguration.touchSlop
                        scope.launch { press.animateTo(1f, spring(1f, 800f)) }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragged && change.isConsumed) { cancelled = true; break }
                            val dx = change.positionChange().x
                            totalDx += dx
                            if (!dragged && abs(totalDx) > slop) {
                                if (onThumb) dragged = true else { cancelled = true; break }
                            }
                            if (dragged) {
                                change.consume()
                                val lx = if (isLtr) change.position.x else size.width - change.position.x
                                val p = (lx / segW - 0.5f).fastCoerceIn(0f, (count - 1).toFloat())
                                scope.launch { position.snapTo(p); stretch.onPositionSample(p * segPx) }
                                val hi = p.roundToInt()
                                if (hi != hoverIndex) {
                                    hoverIndex = hi
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                        }
                        scope.launch { press.animateTo(0f, spring(0.6f, 400f)) }
                        val target = if (dragged) hoverIndex else startIndex
                        if (!cancelled && target != currentSelected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            currentOnSelect(target)
                        } else {
                            // Snap the thumb back if a drag ended on the same segment.
                            hoverIndex = currentSelected
                            scope.launch {
                                position.animateTo(currentSelected.toFloat(), spring(0.72f, 380f)) {
                                    stretch.onPositionSample(value * segPx)
                                }
                            }
                        }
                    }
                }
            )
    ) {
        val segWidth = maxWidth / count
        val density = LocalDensity.current
        val inset = 3.dp
        // Sliding thumb
        Box(
            Modifier
                .padding(inset)
                .width(segWidth - inset * 2)
                .fillMaxHeight()
                .graphicsLayer {
                    val segPx = with(density) { segWidth.toPx() }
                    translationX = (if (isLtr) 1f else -1f) * position.value * segPx
                }
                .liquidStretch(stretch, pressProgress = { press.value })
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule },
                    effects = {
                        val p = press.value
                        blur(2f.dp.toPx() * (1f - p))
                        lens(6f.dp.toPx() + 4f.dp.toPx() * p, 12f.dp.toPx() + 8f.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.6f + 0.4f * press.value) },
                    shadow = null,
                    onDrawSurface = { drawRect(thumbColor.copy(alpha = thumbColor.alpha * (1f - 0.5f * press.value))) }
                )
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.Start) {
            options.forEachIndexed { i, label ->
                val sel = i == hoverIndex
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp)
                        .semantics { selected = sel },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = icons?.getOrNull(i)
                    val tint = if (sel) textColor else secondary
                    if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    if (label.isNotEmpty()) {
                        BasicText(
                            label,
                            style = TextStyle(
                                color = tint,
                                fontSize = 14.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

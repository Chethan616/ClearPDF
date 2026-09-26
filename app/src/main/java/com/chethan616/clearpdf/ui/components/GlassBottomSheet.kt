package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
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
import kotlinx.coroutines.launch

/**
 * Floating liquid-glass bottom sheet (iOS 26 style: inset from the screen edges, fully rounded).
 * Rendered in-hierarchy so it can refract [backdrop] — place it as the last child of a full-screen Box.
 *
 * Drag anywhere on the sheet (or its handle) to pull it down; releasing past 30% of its height or
 * with a downward fling > 1200 px/s dismisses, otherwise it springs back. Upward drags rubber-band.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var composed by remember { mutableStateOf(visible) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    // 0 = fully shown, 1 = fully hidden (fraction of the sheet height).
    val hidden = remember { Animatable(1f) }
    // Extra px offset during a drag (can go negative for rubber-band).
    val dragOffset = remember { Animatable(0f) }
    val enterSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = 0.001f)

    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            dragOffset.snapTo(0f)
            hidden.animateTo(0f, enterSpec)
        } else {
            hidden.animateTo(1f, spring(dampingRatio = 1f, stiffness = 600f, visibilityThreshold = 0.001f))
            dragOffset.snapTo(0f)
            composed = false
        }
    }
    if (!composed) return

    BackHandler(enabled = visible) { currentOnDismiss() }

    val isLight = !LocalIsDarkMode.current
    val containerColor = if (isLight) Color(0xFFFAFAFA).copy(0.6f) else Color(0xFF121212).copy(0.45f)
    val dimColor = if (isLight) Color(0xFF29293A).copy(0.23f) else Color(0xFF000000).copy(0.5f)
    val handleColor = if (isLight) Color.Black.copy(0.22f) else Color.White.copy(0.30f)

    val dragState = rememberDraggableState { delta ->
        val cur = dragOffset.value
        val next = if (cur + delta < 0f) {
            // Rubber-band upward drags.
            cur + delta * 0.25f
        } else cur + delta
        scope.launch { dragOffset.snapTo(next.coerceAtLeast(-with(density) { 40.dp.toPx() })) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val shownFraction = if (sheetHeight > 0f) {
                    1f - hidden.value - dragOffset.value.coerceAtLeast(0f) / sheetHeight
                } else 1f - hidden.value
                drawRect(dimColor.copy(alpha = dimColor.alpha * shownFraction.fastCoerceIn(0f, 1f)))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible,
                onClick = { currentOnDismiss() }
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .graphicsLayer {
                    val travel = sheetHeight + 48.dp.toPx()
                    translationY = hidden.value * travel + dragOffset.value
                    // Stretch slightly when rubber-banding upward.
                    val up = (-dragOffset.value).coerceAtLeast(0f)
                    if (up > 0f && size.height > 0f) {
                        scaleY = 1f + up / size.height * 0.5f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(36f.dp) },
                    effects = {
                        colorControls(brightness = if (isLight) 0.2f else 0f, saturation = 1.5f)
                        blur(if (isLight) 16f.dp.toPx() else 10f.dp.toPx())
                        lens(20f.dp.toPx(), 40f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 24f.dp, color = Color.Black.copy(alpha = 0.14f)) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val threshold = sheetHeight * 0.30f
                        if (dragOffset.value > threshold || velocity > 1200f) {
                            // Continue the fling out, then report dismissal.
                            val travel = sheetHeight + with(density) { 48.dp.toPx() }
                            val startFraction = (dragOffset.value / travel).fastCoerceIn(0f, 1f)
                            dragOffset.snapTo(0f)
                            hidden.snapTo(startFraction)
                            currentOnDismiss()
                        } else {
                            dragOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f), initialVelocity = velocity)
                        }
                    }
                )
        ) {
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(Capsule)
                        .background(handleColor)
                )
            }
            content()
            Box(Modifier.height(12.dp))
        }
    }
}

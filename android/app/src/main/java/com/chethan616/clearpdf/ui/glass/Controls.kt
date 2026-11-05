package com.chethan616.clearpdf.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

/** The thumb both controls share: an opaque white capsule at rest, a refracting lens while held. */
@Composable
private fun Modifier.thumb(drag: DampedDragAnimation, track: com.kyant.backdrop.backdrops.LayerBackdrop, lensHeight: Float, lensAmount: Float, trackScale: Float): Modifier {
    val backdrop = LocalBackdrop.current
    return drawBackdrop(
        backdrop = rememberCombinedBackdrop(
            backdrop,
            rememberBackdrop(track) { draw ->
                val p = drag.pressProgress
                scale(lerp(2f / 3f, trackScale, p), lerp(0f, trackScale, p)) { draw() }
            },
        ),
        shape = { Capsule },
        effects = {
            val p = drag.pressProgress
            if (p > 0f) blur(8f.dp.toPx() * (1f - p))
            lens(lensHeight.dp.toPx() * p, lensAmount.dp.toPx() * p, chromaticAberration = true)
        },
        highlight = {
            val p = drag.pressProgress
            if (p <= 0f) null else Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.5f, blurRadius = Highlight.Ambient.blurRadius / 1.5f, alpha = p)
        },
        shadow = null,
        innerShadow = {
            val p = drag.pressProgress
            if (p <= 0f) null else InnerShadow(radius = 4f.dp * p, alpha = p)
        },
        // covered by the white surface at rest: don't record or blur what nobody can see
        onDrawBackdrop = { draw -> if (drag.pressProgress > 0f) draw() },
        layerBlock = {
            scaleX = drag.scaleX
            scaleY = drag.scaleY
        },
        onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - drag.pressProgress)) },
    )
}

/** A glass switch: drag or tap, the thumb turns to glass under the finger. */
@Composable
fun GlassToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, accent: Color = Candy.Mint) {
    val c = clear
    val track = if (c.dark) Color(0xFF787880).copy(0.36f) else Color(0xFF787878).copy(0.2f)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val haptics = LocalHapticFeedback.current
    val dragWidth = with(density) { 20f.dp.toPx() }
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onChange)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val drag = remember(scope) {
        DampedDragAnimation(
            scope, fraction, 0f..1f, 0.001f, 1f, 1.5f,
            onDragStarted = {},
            onDragStopped = {
                fraction = if (didDrag) (if (targetValue >= 0.5f) 1f else 0f) else (if (current) 0f else 1f)
                didDrag = false
                if ((fraction == 1f) != current) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    change(fraction == 1f)
                }
            },
            onDrag = { _, d ->
                if (!didDrag) didDrag = d.x != 0f
                val delta = d.x / dragWidth
                fraction = (if (isLtr) fraction + delta else fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(drag) { snapshotFlow { fraction }.collectLatest { drag.updateValue(it) } }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            drag.animateToValue(target)
        }
    }
    val trackLayer = rememberLayerBackdrop()
    val stretch = rememberLiquidStretchState(stretchFactor = 0.4f, minScale = 0.85f, maxScale = 1.2f)
    LaunchedEffect(drag, stretch) { snapshotFlow { drag.value }.collect { stretch.onPositionSample(it * dragWidth) } }
    Box(
        modifier.clip(Capsule).then(drag.modifier).semantics {
            role = Role.Switch
            toggleableState = ToggleableState(current)
            onClick { change(!current); true }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.layerBackdrop(trackLayer).clip(Capsule).drawBehind { drawRect(lerp(track, accent, drag.value)) }.size(64.dp, 28.dp))
        Box(
            Modifier
                .graphicsLayer {
                    val pad = 2f.dp.toPx()
                    translationX = if (isLtr) lerp(pad, pad + dragWidth, drag.value) else lerp(-pad, -(pad + dragWidth), drag.value)
                }
                .liquidStretch(stretch)
                .thumb(drag, trackLayer, 5f, 10f, 0.75f)
                .size(40.dp, 24.dp),
        )
    }
}

/** A glass slider: the candy fill follows the thumb, which turns to glass while held. */
@Composable
fun GlassSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    accent: Color = Candy.Sky,
) {
    val c = clear
    val track = if (c.dark) Color(0xFF787880).copy(0.36f) else Color(0xFF787878).copy(0.2f)
    val trackLayer = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        val current = value.coerceIn(range.start, range.endInclusive)
        val change by rememberUpdatedState(onChange)
        var dragging by remember { mutableStateOf(false) }
        val drag = remember(scope, isLtr, width, range.start, range.endInclusive) {
            DampedDragAnimation(
                scope, current, range, 0.001f, 1f, 1.5f,
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false },
                onDrag = { _, d ->
                    val delta = (range.endInclusive - range.start) * (d.x / width.toFloat())
                    if (delta != 0f) {
                        val next = (if (isLtr) targetValue + delta else targetValue - delta).coerceIn(range.start, range.endInclusive)
                        updateValue(next)
                        change(next)
                    }
                },
            )
        }
        val stretch = rememberLiquidStretchState(stretchFactor = 0.4f, minScale = 0.85f, maxScale = 1.2f)
        LaunchedEffect(drag, stretch, width) { snapshotFlow { drag.progress }.collect { stretch.onPositionSample(it * width) } }
        LaunchedEffect(current, dragging, drag) {
            if (!dragging && abs(drag.targetValue - current) > 0.001f) drag.updateValue(current)
        }
        Box(Modifier.layerBackdrop(trackLayer)) {
            Box(
                Modifier.clip(Capsule).background(track)
                    .pointerInput(width, isLtr, range.start, range.endInclusive) {
                        detectTapGestures { p ->
                            val delta = (range.endInclusive - range.start) * (p.x / width.toFloat())
                            val target = (if (isLtr) range.start + delta else range.endInclusive - delta).coerceIn(range)
                            drag.animateToValue(target)
                            change(target)
                        }
                    }
                    .height(6.dp).fillMaxWidth(),
            )
            Box(
                Modifier.clip(Capsule).background(accent).height(6.dp).layout { m, cons ->
                    val p = m.measure(cons)
                    layout((cons.maxWidth * drag.progress).fastRoundToInt(), p.height) { p.place(0, 0) }
                },
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + width * drag.progress).fastCoerceIn(-size.width / 4f, width - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(drag.modifier)
                .liquidStretch(stretch)
                .thumb(drag, trackLayer, 10f, 14f, 1f)
                .size(40.dp, 24.dp),
        )
    }
}

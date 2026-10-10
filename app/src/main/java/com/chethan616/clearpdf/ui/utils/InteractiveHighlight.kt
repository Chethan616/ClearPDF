package com.chethan616.clearpdf.ui.utils

import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MinPressMillis = 90L
// Initial velocities for [InteractiveHighlight.wobble]'s spring: 34 peaks near 1, 10 near 0.3.
private const val WobbleKick = 34f
private const val PressKick = 10f

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /**
     * Holding a finger still past the platform long-press timeout "blooms" the control: [expandProgress]
     * springs to 1 (the owner grows a little further on top of the press scale and glows brighter)
     * and [onLongPressExpand] fires once — the owner's haptic. Off by default: tab bars and cards
     * own their own long-press meaning.
     */
    private val longPressExpand: Boolean = false,
    private val onLongPressExpand: (() -> Unit)? = null
) {
    // iOS 27 press: the swell lands fast with a small overshoot, the release springs back past rest
    // once, and [wobble] adds one out-of-phase squash on top (see liquidPressTransform).
    private val pressInSpec = spring(0.66f, 620f, 0.001f)
    private val pressOutSpec = spring(0.5f, 380f, 0.001f)
    private val positionAnimationSpec = spring(0.52f, 360f, Offset.VisibilityThreshold)
    private val wobbleSpec = spring(0.42f, 420f, 0.001f)
    // Bloom in on a soft, slightly slower spring; let go on a livelier one that undershoots rest
    // a touch before settling — the "boing" that makes the release feel physical.
    private val expandInSpec = spring(dampingRatio = 0.52f, stiffness = 340f, visibilityThreshold = 0.001f)
    private val expandOutSpec = spring(dampingRatio = 0.46f, stiffness = 420f, visibilityThreshold = 0.001f)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private val expandAnimation = Animatable(0f, 0.001f)
    private val wobbleAnimation = Animatable(0f, 0.001f)

    private var startPosition = Offset.Zero
    private var positionUpdateJob: Job? = null
    private var longPressJob: Job? = null
    private var releaseJob: Job? = null
    private var downAt = 0L
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition
    /** 0 at rest, 1 once a long-press has bloomed; briefly dips below 0 on the release bounce. */
    val expandProgress: Float get() = expandAnimation.value
    /** Release jiggle, peaking near ±1 and decaying in ~400 ms. Positive = wider and shorter. */
    val wobble: Float get() = wobbleAnimation.value

    // Built on first press, not at construction. Every LiquidButton / LiquidIconButton / glass card
    // owns one of these, and `RuntimeShader(...)` compiles its AGSL on the calling thread — so a
    // screen of buttons used to pay one shader compile per button during composition (and a list
    // paid it again for every row it scrolled in), for a highlight most buttons never show.
    private val shader: RuntimeShader? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }
    }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            // A bloomed long-press lifts the glow a little further, so the control reads as "picked up".
            val bloom = expandAnimation.value.fastCoerceIn(0f, 1f)
            if (progress > 0f) {
                val shader = this@InteractiveHighlight.shader
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    drawRect(Color.White.copy(0.08f * progress + 0.05f * bloom), blendMode = BlendMode.Plus)
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.15f * progress + 0.10f * bloom).toArgb())
                        setFloatUniform("radius", size.minDimension * (1.5f + 0.5f * bloom))
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
                } else {
                    drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
                }
            }
            drawContent()
        }

    private fun release() {
        positionUpdateJob?.cancel()
        longPressJob?.cancel()
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            // A quick tap still shows the whole swell before it lets go.
            val held = SystemClock.uptimeMillis() - downAt
            if (held < MinPressMillis) delay(MinPressMillis - held)
            val kick = WobbleKick * pressProgressAnimation.value.fastCoerceIn(0f, 1.1f)
            launch { wobbleAnimation.animateTo(0f, wobbleSpec, initialVelocity = kick) }
            launch { pressProgressAnimation.animateTo(0f, pressOutSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
            if (expandAnimation.value != 0f || expandAnimation.targetValue != 0f) {
                launch { expandAnimation.animateTo(0f, expandOutSpec) }
            }
        }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            val longPressMs = viewConfiguration.longPressTimeoutMillis
            val slop = viewConfiguration.touchSlop
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    downAt = SystemClock.uptimeMillis()
                    positionUpdateJob?.cancel()
                    longPressJob?.cancel()
                    releaseJob?.cancel()
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressInSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                        // A small taller-first nudge as the glass swells under the finger.
                        launch { wobbleAnimation.animateTo(0f, wobbleSpec, initialVelocity = -PressKick) }
                    }
                    if (longPressExpand) {
                        // Same timeout the platform uses for long-click, so a button that also has an
                        // onLongClick blooms exactly as its own long-press fires.
                        longPressJob = animationScope.launch {
                            delay(longPressMs)
                            onLongPressExpand?.invoke()
                            expandAnimation.animateTo(1f, expandInSpec)
                        }
                    }
                },
                onDragEnd = { release() },
                onDragCancel = { release() }
            ) { change, _ ->
                // A finger that wanders before the bloom is a drag/scroll, not a hold.
                if (longPressJob?.isActive == true && expandAnimation.targetValue == 0f &&
                    (change.position - startPosition).getDistance() > slop
                ) {
                    longPressJob?.cancel()
                }
                // Pointer events can arrive faster than a frame. Keep only the
                // newest position instead of queuing one coroutine per sample.
                positionUpdateJob?.cancel()
                positionUpdateJob = animationScope.launch {
                    positionAnimation.snapTo(change.position)
                }
            }
        }
}

/**
 * The Liquid Glass press transform shared by LiquidButton and LiquidIconButton (call it from
 * `drawBackdrop`'s `layerBlock`): swells ~4 dp under the finger, leans and stretches toward a
 * dragging finger, blooms on long-press (≤ ~14 dp, ≤ 12 %), and jiggles once on release with X and
 * Y squashing out of phase (~2.5 dp at the first swing, capped for tiny controls).
 */
fun GraphicsLayerScope.liquidPressTransform(h: InteractiveHighlight) {
    val width = size.width
    val height = size.height

    val progress = h.pressProgress
    val bloom = h.expandProgress * (14f.dp.toPx() / size.maxDimension).fastCoerceAtMost(0.12f)
    val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress) + bloom

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = h.offset
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

    val maxDragScale = 4f.dp.toPx() / height
    val offsetAngle = atan2(offset.y, offset.x)
    val w = h.wobble
    val wobbleX = w * (2.5f.dp.toPx() / width).fastCoerceAtMost(0.045f)
    val wobbleY = w * (2.5f.dp.toPx() / height).fastCoerceAtMost(0.045f)
    scaleX = scale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f) +
        wobbleX
    scaleY = scale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f) -
        wobbleY
}

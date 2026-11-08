package com.chethan616.clearpdf.ui.glass

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Watches a press and its drag without consuming them, so the control under it still gets the click. */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = drag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(pointerId: PointerId, onDrag: (PointerInputChange) -> Unit): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val other = event.changes.fastFirstOrNull { it.pressed } ?: return change
            pointer = other.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}

// Initial velocities of the release jiggle: 34 peaks near 1, 10 near 0.3.
private const val WobbleKick = 34f
private const val PressKick = 10f

/**
 * The glass press: a swell under the finger, a lean toward a dragging finger, a one-swing jiggle on
 * release, a light that follows the touch, and (with [longPressExpand]) a bloom after a long hold.
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    private val longPressExpand: Boolean = false,
    private val onLongPressExpand: (() -> Unit)? = null,
) {
    private val pressInSpec get() = spring(MotionSettings.damping(0.66f), MotionSettings.stiffness(620f), 0.001f)
    private val pressOutSpec get() = spring(MotionSettings.damping(0.5f), MotionSettings.stiffness(380f), 0.001f)
    private val positionSpec get() = spring(MotionSettings.damping(0.52f), MotionSettings.stiffness(360f), Offset.VisibilityThreshold)
    private val wobbleSpec get() = spring(MotionSettings.damping(0.42f), MotionSettings.stiffness(420f), 0.001f)
    private val expandInSpec get() = spring(MotionSettings.damping(0.52f), MotionSettings.stiffness(340f), 0.001f)
    private val expandOutSpec get() = spring(MotionSettings.damping(0.46f), MotionSettings.stiffness(420f), 0.001f)
    // The light never bounces: it rises with the finger and fades once it lifts, while the shape jiggles.
    private val glowInSpec get() = spring(1f, MotionSettings.stiffness(900f), 0.001f)
    private val glowOutSpec get() = spring(1f, MotionSettings.stiffness(380f), 0.001f)

    private val pressAnim = Animatable(0f, 0.001f)
    private val positionAnim = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private val expandAnim = Animatable(0f, 0.001f)
    private val wobbleAnim = Animatable(0f, 0.001f)
    private val glowAnim = Animatable(0f, 0.001f)
    private val bloomGlowAnim = Animatable(0f, 0.001f)

    private var startPosition = Offset.Zero
    private var positionJob: Job? = null
    private var longPressJob: Job? = null
    private var releaseJob: Job? = null
    private var downAt = 0L

    val pressProgress: Float get() = pressAnim.value
    val offset: Offset get() = positionAnim.value - startPosition
    val expandProgress: Float get() = expandAnim.value
    val wobble: Float get() = wobbleAnim.value

    // Built on first press: RuntimeShader compiles on the calling thread, and most buttons are never pressed.
    // Typed Shader so nothing names RuntimeShader below Android 13.
    private val shader: Shader? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PressGlow.create() else null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = glowAnim.value.fastCoerceIn(0f, 1f)
        val bloom = bloomGlowAnim.value.fastCoerceIn(0f, 1f)
        if (progress > 0f || bloom > 0f) {
            val s = shader
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && s != null) {
                drawRect(Color.White.copy(0.08f * progress + 0.05f * bloom), blendMode = BlendMode.Plus)
                val p = position(size, positionAnim.value)
                PressGlow.update(
                    s, size, Color.White.copy(0.15f * progress + 0.10f * bloom).toArgb(), size.minDimension * (1.5f + 0.5f * bloom),
                    Offset(p.x.fastCoerceIn(0f, size.width), p.y.fastCoerceIn(0f, size.height)),
                )
                drawRect(ShaderBrush(s), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object PressGlow {
        // one compiled shader for every button: each press sets its uniforms and draws in the same UI-thread
        // step, and a paint snapshots them, so sharing costs nothing and saves a compile at each button's first press
        private var shared: Shader? = null

        fun create(): Shader = shared ?: RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}""",
        ).also { shared = it }

        fun update(shader: Shader, size: Size, color: Int, radius: Float, position: Offset) {
            (shader as RuntimeShader).apply {
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", color)
                setFloatUniform("radius", radius)
                setFloatUniform("position", position.x, position.y)
            }
        }
    }

    private fun release() {
        positionJob?.cancel()
        longPressJob?.cancel()
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            val held = SystemClock.uptimeMillis() - downAt
            if (held < GlassMotion.MinPressMillis) delay(GlassMotion.MinPressMillis - held)
            val kick = WobbleKick * MotionSettings.style.wobble.fastCoerceIn(0f, 3f) * pressAnim.value.fastCoerceIn(0f, 1.1f)
            launch { wobbleAnim.animateTo(0f, wobbleSpec, initialVelocity = kick) }
            launch { pressAnim.animateTo(0f, pressOutSpec) }
            launch { glowAnim.animateTo(0f, glowOutSpec) }
            launch { positionAnim.animateTo(startPosition, positionSpec) }
            if (expandAnim.value != 0f || expandAnim.targetValue != 0f) {
                launch { expandAnim.animateTo(0f, expandOutSpec) }
                launch { bloomGlowAnim.animateTo(0f, glowOutSpec) }
            }
        }
    }

    /**
     * Settles at once (critically damped, ~150 ms) instead of ringing on: a press that opened a menu or
     * sheet would otherwise keep the whole scene under it redrawing, and re-blurring, for a second.
     */
    fun settle() {
        if (pressAnim.value == 0f && wobbleAnim.value == 0f && glowAnim.value == 0f && expandAnim.value == 0f) return
        releaseJob?.cancel()
        longPressJob?.cancel()
        val quick = spring<Float>(1f, 1400f, 0.004f)
        releaseJob = animationScope.launch {
            launch { wobbleAnim.animateTo(0f, quick) }
            launch { pressAnim.animateTo(0f, quick) }
            launch { glowAnim.animateTo(0f, quick) }
            launch { expandAnim.animateTo(0f, quick) }
            launch { bloomGlowAnim.animateTo(0f, quick) }
            launch { positionAnim.snapTo(startPosition) }
        }
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                downAt = SystemClock.uptimeMillis()
                positionJob?.cancel()
                longPressJob?.cancel()
                releaseJob?.cancel()
                animationScope.launch {
                    launch { pressAnim.animateTo(1f, pressInSpec) }
                    launch { glowAnim.animateTo(1f, glowInSpec) }
                    launch { positionAnim.snapTo(startPosition) }
                    launch { wobbleAnim.animateTo(0f, wobbleSpec, initialVelocity = -PressKick) }
                }
                if (longPressExpand) {
                    longPressJob = animationScope.launch {
                        delay(longPressMs)
                        onLongPressExpand?.invoke()
                        launch { bloomGlowAnim.animateTo(1f, glowInSpec) }
                        expandAnim.animateTo(1f, expandInSpec)
                    }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            if (longPressJob?.isActive == true && expandAnim.targetValue == 0f && (change.position - startPosition).getDistance() > slop) {
                longPressJob?.cancel()
            }
            positionJob?.cancel()
            positionJob = animationScope.launch { positionAnim.snapTo(change.position) }
        }
    }
}

/**
 * The glass press as a transform (from a drawBackdrop layerBlock): swells ~4 dp, leans toward a
 * dragging finger, blooms on a long hold (≤ ~14 dp), and jiggles once on release, X and Y out of phase.
 */
fun GraphicsLayerScope.liquidPressTransform(h: InteractiveHighlight) {
    val width = size.width
    val height = size.height
    if (!(width > 0f && height > 0f)) return
    val motion = MotionSettings.style
    val progress = h.pressProgress
    val bloomAmount = motion.bloom.fastCoerceIn(0f, 2.5f)
    val bloom = h.expandProgress * (14f.dp.toPx() * bloomAmount / size.maxDimension).fastCoerceAtMost(0.12f * bloomAmount)
    val scale = lerp(1f, 1f + 4f.dp.toPx() * motion.press.fastCoerceIn(0f, 2.5f) / height, progress) + bloom
    val maxOffset = size.minDimension
    val stretch = motion.stretch.fastCoerceIn(0f, 2.5f)
    val k = 0.05f * stretch
    val offset = h.offset
    translationX = maxOffset * tanh(k * offset.x / maxOffset)
    translationY = maxOffset * tanh(k * offset.y / maxOffset)
    val maxDragScale = 4f.dp.toPx() * stretch / height
    val angle = atan2(offset.y, offset.x)
    val w = h.wobble
    val wobbleX = w * (2.5f.dp.toPx() / width).fastCoerceAtMost(0.045f)
    val wobbleY = w * (2.5f.dp.toPx() / height).fastCoerceAtMost(0.045f)
    scaleX = scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f) + wobbleX
    scaleY = scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f) - wobbleY
}

/**
 * The press of flat pressables (rows, tiles, cards, chips): sinks under the finger and comes back
 * softly, never past rest (the jelly belongs to the glass buttons alone), and holds a quick tap long
 * enough to be seen. Read [scale] inside graphicsLayer.
 */
@Stable
class JellyPress internal constructor(private val scope: CoroutineScope, private val pressedScale: Float) {
    private val anim = Animatable(1f, 0.0005f)
    private var downAt = 0L
    private var releaseJob: Job? = null

    val scale: Float get() = anim.value
    /** 0 at rest, 1 fully pressed. */
    val progress: Float get() = ((1f - anim.value) / (1f - pressedScale)).fastCoerceIn(0f, 1f)

    fun press() {
        releaseJob?.cancel()
        downAt = SystemClock.uptimeMillis()
        scope.launch { anim.animateTo(pressedScale, Fluid.pressIn()) }
    }

    fun release() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            val held = SystemClock.uptimeMillis() - downAt
            if (held < GlassMotion.MinPressMillis) delay(GlassMotion.MinPressMillis - held)
            anim.animateTo(1f, Fluid.pressOut())
        }
    }

    /**
     * Back to rest at once: a long press opens a sheet over this, and springing back under the sheet
     * would redraw the whole still scene on every frame of it.
     */
    fun rest() {
        releaseJob?.cancel()
        scope.launch { anim.snapTo(1f) }
    }
}

@Composable
fun rememberJellyPress(interaction: InteractionSource, pressedScale: Float = Fluid.PressedScale): JellyPress {
    val scope = rememberCoroutineScope()
    val jelly = remember(scope, pressedScale) { JellyPress(scope, pressedScale) }
    LaunchedEffect(interaction, jelly) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> jelly.press()
                is PressInteraction.Release, is PressInteraction.Cancel -> jelly.release()
            }
        }
    }
    return jelly
}

/**
 * Velocity squash-and-stretch, after LiquidTracker in AndroidLiquidGlassView by Donny Yale (QmDeve,
 * MIT): stretches along the direction of travel and squashes across it, then relaxes.
 */
@Stable
class LiquidStretchState internal constructor(
    private val scope: CoroutineScope,
    private val stretchFactor: Float,
    private val minScale: Float,
    private val maxScale: Float,
    private val settleDelayMs: Long,
) {
    private val spec get() = spring<Float>(MotionSettings.damping(0.35f), MotionSettings.stiffness(180f), 0.0005f)
    private val sx = Animatable(1f, 0.0005f)
    private val sy = Animatable(1f, 0.0005f)
    private var lastX = Float.NaN
    private var lastY = 0f
    private var lastT = 0L
    private var settleJob: Job? = null

    val scaleX: Float get() = sx.value
    val scaleY: Float get() = sy.value

    fun onPositionSample(x: Float, y: Float = 0f) {
        val t = SystemClock.uptimeMillis()
        if (lastX.isNaN()) {
            lastX = x; lastY = y; lastT = t
            return
        }
        val dt = t - lastT
        if (dt <= 0L) return
        if (dt > 100L) {
            lastX = x; lastY = y; lastT = t
            return
        }
        onVelocity((x - lastX) / dt, (y - lastY) / dt)
        lastX = x; lastY = y; lastT = t
    }

    fun onVelocity(vx: Float, vy: Float) {
        val ax = abs(vx)
        val ay = abs(vy)
        val k = stretchFactor * MotionSettings.style.stretch
        val (tx, ty) = if (ax >= ay) (1f + ax * k) to (1f - ax * k * 0.5f)
        else (1f - ay * k * 0.5f) to (1f + ay * k)
        animateTo(tx.coerceIn(minScale, maxScale), ty.coerceIn(minScale, maxScale))
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(settleDelayMs)
            settle()
        }
    }

    fun settle() {
        lastX = Float.NaN
        animateTo(1f, 1f)
    }

    private fun animateTo(x: Float, y: Float) {
        scope.launch { sx.animateTo(x, spec) }
        scope.launch { sy.animateTo(y, spec) }
    }
}

@Composable
fun rememberLiquidStretchState(stretchFactor: Float = 0.5f, minScale: Float = 0.6f, maxScale: Float = 1.4f, settleDelayMs: Long = 200L): LiquidStretchState {
    val scope = rememberCoroutineScope()
    return remember(scope, stretchFactor, minScale, maxScale, settleDelayMs) { LiquidStretchState(scope, stretchFactor, minScale, maxScale, settleDelayMs) }
}

fun Modifier.liquidStretch(state: LiquidStretchState): Modifier = graphicsLayer {
    scaleX = state.scaleX
    scaleY = state.scaleY
}

/**
 * A value a finger drags (a thumb, a tab blob): it follows on a stiff spring, the thumb swells while
 * held, and its velocity is tracked so it can stretch along its travel.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    private val visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocitySpec get() = spring(MotionSettings.damping(0.5f), MotionSettings.stiffness(300f), visibilityThreshold * 10f)
    private val pressSpec = spring(1f, 1000f, 0.001f)
    private val scaleXSpec get() = spring(MotionSettings.damping(0.6f), MotionSettings.stiffness(250f), 0.001f)
    private val scaleYSpec get() = spring(MotionSettings.damping(0.7f), MotionSettings.stiffness(250f), 0.001f)

    private val valueAnim = Animatable(initialValue, visibilityThreshold)
    private val velocityAnim = Animatable(0f, 5f)
    private val pressAnim = Animatable(0f, 0.001f)
    private val scaleXAnim = Animatable(initialScale, 0.001f)
    private val scaleYAnim = Animatable(initialScale, 0.001f)
    private val mutex = MutatorMutex()
    private val tracker = VelocityTracker()

    val value: Float get() = valueAnim.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnim.targetValue
    val pressProgress: Float get() = pressAnim.value
    val scaleX: Float get() = scaleXAnim.value
    val scaleY: Float get() = scaleYAnim.value
    val velocity: Float get() = velocityAnim.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down -> onDragStarted(down.position); press() },
            onDragEnd = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press() {
        tracker.resetTracking()
        animationScope.launch {
            launch { pressAnim.animateTo(1f, pressSpec) }
            launch { scaleXAnim.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnim.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnim.value }.filter { abs(it - valueAnim.targetValue) < threshold }.first()
            }
            launch { pressAnim.animateTo(0f, pressSpec) }
            launch { scaleXAnim.animateTo(initialScale, scaleXSpec) }
            launch { scaleYAnim.animateTo(initialScale, scaleYSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch { valueAnim.animateTo(target, valueSpec) { updateVelocity() } }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnim.animateTo(target, valueSpec) }
                if (velocity != 0f) launch { velocityAnim.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }

    private fun updateVelocity() {
        tracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val v = tracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnim.animateTo(v, velocitySpec) }
    }
}

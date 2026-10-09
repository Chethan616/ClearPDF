package com.chethan616.clearpdf.ui.utils

import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    // Bloom in on a soft, slightly slower spring; let go on a livelier one that undershoots rest
    // a touch before settling — the "boing" that makes the release feel physical.
    private val expandInSpec = spring(dampingRatio = 0.52f, stiffness = 340f, visibilityThreshold = 0.001f)
    private val expandOutSpec = spring(dampingRatio = 0.46f, stiffness = 420f, visibilityThreshold = 0.001f)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private val expandAnimation = Animatable(0f, 0.001f)

    private var startPosition = Offset.Zero
    private var positionUpdateJob: Job? = null
    private var longPressJob: Job? = null
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition
    /** 0 at rest, 1 once a long-press has bloomed; briefly dips below 0 on the release bounce. */
    val expandProgress: Float get() = expandAnimation.value

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
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
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
                    positionUpdateJob?.cancel()
                    longPressJob?.cancel()
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
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

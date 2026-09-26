/*
 * Velocity squash-and-stretch ported from AndroidLiquidGlassView's LiquidTracker
 * (https://github.com/QmDeve/AndroidLiquidGlassView, MIT License, Copyright (c) 2025-2026 Donny Yale).
 * Re-implemented for Compose; see THIRD_PARTY_NOTICES.md.
 */

package com.chethan616.clearpdf.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Holds the spring-driven scale of a "liquid" element that stretches along its direction of travel
 * and squashes across it, proportional to velocity (px/ms):
 *   along  = 1 + |v| * stretchFactor
 *   across = 1 - |v| * stretchFactor * 0.5
 * clamped to [minScale]..[maxScale], animated with a soft spring (stiffness 180, damping 0.35) and
 * relaxed back to 1 after [settleDelayMs] without movement.
 *
 * Feed it either raw positions ([onPositionSample], velocity is derived) or velocities ([onVelocity]).
 */
@Stable
class LiquidStretchState internal constructor(
    private val scope: CoroutineScope,
    private val stretchFactor: Float,
    private val minScale: Float,
    private val maxScale: Float,
    private val settleDelayMs: Long
) {
    private val spec = spring<Float>(dampingRatio = 0.35f, stiffness = 180f, visibilityThreshold = 0.0005f)
    private val sx = Animatable(1f, 0.0005f)
    private val sy = Animatable(1f, 0.0005f)
    private var lastX = Float.NaN
    private var lastY = 0f
    private var lastT = 0L
    private var settleJob: Job? = null

    val scaleX: Float get() = sx.value
    val scaleY: Float get() = sy.value

    /** Report the element's current position in px. Samples within the same millisecond are merged. */
    fun onPositionSample(x: Float, y: Float = 0f) {
        val t = SystemClock.uptimeMillis()
        if (lastX.isNaN()) {
            lastX = x; lastY = y; lastT = t
            return
        }
        val dt = t - lastT
        if (dt <= 0L) return
        if (dt > 100L) {
            // Stale previous sample — restart tracking instead of reporting a bogus slow velocity.
            lastX = x; lastY = y; lastT = t
            return
        }
        onVelocity((x - lastX) / dt, (y - lastY) / dt)
        lastX = x; lastY = y; lastT = t
    }

    /** Report a velocity in px/ms. */
    fun onVelocity(vx: Float, vy: Float) {
        val ax = abs(vx)
        val ay = abs(vy)
        val tx: Float
        val ty: Float
        if (ax >= ay) {
            tx = 1f + ax * stretchFactor
            ty = 1f - ax * stretchFactor * 0.5f
        } else {
            tx = 1f - ay * stretchFactor * 0.5f
            ty = 1f + ay * stretchFactor
        }
        animateTo(tx.coerceIn(minScale, maxScale), ty.coerceIn(minScale, maxScale))
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(settleDelayMs)
            settle()
        }
    }

    /** Relax back to the rest shape. */
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
fun rememberLiquidStretchState(
    stretchFactor: Float = 0.5f,
    minScale: Float = 0.6f,
    maxScale: Float = 1.4f,
    settleDelayMs: Long = 200L
): LiquidStretchState {
    val scope = rememberCoroutineScope()
    return remember(scope, stretchFactor, minScale, maxScale, settleDelayMs) {
        LiquidStretchState(scope, stretchFactor, minScale, maxScale, settleDelayMs)
    }
}

/**
 * Applies [state]'s squash-and-stretch plus a slight press swell ([pressedScale] at pressProgress 1)
 * as a draw-time transform (no re-layout).
 */
fun Modifier.liquidStretch(
    state: LiquidStretchState,
    pressProgress: () -> Float = { 0f },
    pressedScale: Float = 1.02f
): Modifier = this.graphicsLayer {
    val p = 1f + (pressedScale - 1f) * pressProgress()
    scaleX = state.scaleX * p
    scaleY = state.scaleY * p
}

/**
 * Self-contained variant: observes (without consuming) the pointer moving over this element and
 * stretches it along the drag velocity, like LiquidTracker.applyMovement.
 */
@Composable
fun Modifier.liquidStretchOnDrag(
    stretchFactor: Float = 0.5f,
    minScale: Float = 0.6f,
    maxScale: Float = 1.4f
): Modifier {
    val state = rememberLiquidStretchState(stretchFactor, minScale, maxScale)
    return this
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.onPositionSample(down.position.x, down.position.y)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    state.onPositionSample(change.position.x, change.position.y)
                }
                state.settle()
            }
        }
        .liquidStretch(state)
}

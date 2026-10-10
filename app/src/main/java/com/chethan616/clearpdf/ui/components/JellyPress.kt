package com.chethan616.clearpdf.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The press "jelly" for flat pressables (rows, chips, toolbar items): dips on [GlassMotion.pressIn],
 * springs back on [GlassMotion.pressOut] with one small overshoot, and holds a quick tap for
 * [GlassMotion.MinPressMillis] so it is never swallowed. In a scrolling list Compose reports a quick
 * tap's Press and Release together, which with a plain `animateFloatAsState` showed no dip at all.
 *
 * Read [scale] inside `graphicsLayer {}` so the animation stays in the draw phase.
 */
@Stable
class JellyPress internal constructor(
    private val scope: CoroutineScope,
    private val pressedScale: Float
) {
    private val anim = Animatable(1f, 0.0005f)
    private var downAt = 0L
    private var releaseJob: Job? = null

    val scale: Float get() = anim.value

    fun press() {
        releaseJob?.cancel()
        downAt = SystemClock.uptimeMillis()
        scope.launch { anim.animateTo(pressedScale, GlassMotion.pressIn()) }
    }

    fun release() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            val held = SystemClock.uptimeMillis() - downAt
            if (held < GlassMotion.MinPressMillis) delay(GlassMotion.MinPressMillis - held)
            anim.animateTo(1f, GlassMotion.pressOut())
        }
    }
}

/** Jelly driven by an [InteractionSource] (the one passed to `clickable`). */
@Composable
fun rememberJellyPress(
    interaction: InteractionSource,
    pressedScale: Float = GlassMotion.PressedScale
): JellyPress {
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

/** Jelly driven by a pressed flag, for controls with their own gesture detector. */
@Composable
fun rememberJellyPress(
    pressed: Boolean,
    pressedScale: Float = GlassMotion.PressedScale
): JellyPress {
    val scope = rememberCoroutineScope()
    val jelly = remember(scope, pressedScale) { JellyPress(scope, pressedScale) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(pressed) {
        if (first[0]) { first[0] = false; if (!pressed) return@LaunchedEffect }
        if (pressed) jelly.press() else jelly.release()
    }
    return jelly
}

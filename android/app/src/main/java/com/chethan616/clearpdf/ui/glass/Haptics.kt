package com.chethan616.clearpdf.ui.glass

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_SPIN
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * What a moment feels like, in Material 3 Expressive's haptic vocabulary: crisp ticks for
 * selection, a firm click for a press, a rising swell when something opens or springs up, a fall
 * when it folds away, a spin for a playful twirl, a thud for a landing, confirm and reject.
 */
enum class Feel { Tap, Press, Tick, ToggleOn, ToggleOff, Confirm, Reject, Rise, Fall, Spin, Thud, Bloom }

/**
 * Plays [Feel]s with the vibrator's composition primitives (Android 11+, when the phone has them:
 * they are what make haptics feel rich rather than buzzy), else the view's haptic constants. Played
 * as touch feedback, so the phone's own touch-vibration strength applies. Honours Settings ▸
 * Haptic feedback, and drops a second feel within a few milliseconds of the last.
 */
@Stable
class Haptics internal constructor(context: Context, private val view: View) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val rich: Boolean = Build.VERSION.SDK_INT >= 30 && supports(PRIMITIVE_CLICK, PRIMITIVE_TICK, PRIMITIVE_QUICK_RISE, PRIMITIVE_QUICK_FALL)
    private val spin: Boolean = rich && supports(PRIMITIVE_SPIN)
    private val thud: Boolean = rich && supports(PRIMITIVE_THUD)
    private var last = 0L

    private fun supports(vararg primitives: Int): Boolean {
        val v = vibrator ?: return false
        if (Build.VERSION.SDK_INT < 30 || !v.hasVibrator()) return false
        return runCatching { v.areAllPrimitivesSupported(*primitives) }.getOrDefault(false)
    }

    fun play(feel: Feel) {
        if (!GlassSettings.haptics) return
        val now = SystemClock.uptimeMillis()
        if (now - last < 35L && feel != Feel.Confirm && feel != Feel.Reject) return
        last = now
        if (rich && Build.VERSION.SDK_INT >= 30 && vibrate(compose(feel))) return
        view.performHapticFeedback(fallback(feel))
    }

    /** A run of light ticks rising in strength, one per item, [gapMs] apart: a fan of things spilling out. */
    fun cascade(count: Int, gapMs: Int = 28) {
        if (!GlassSettings.haptics || count <= 0) return
        if (rich && Build.VERSION.SDK_INT >= 30) {
            val c = VibrationEffect.startComposition()
            repeat(count) { i -> c.addPrimitive(PRIMITIVE_TICK, 0.35f + 0.4f * (i + 1) / count, if (i == 0) 0 else gapMs) }
            if (vibrate(c.compose())) return
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun vibrate(effect: VibrationEffect): Boolean {
        val v = vibrator ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            else v.vibrate(effect)
        }.isSuccess
    }

    @RequiresApi(30)
    private fun compose(feel: Feel): VibrationEffect {
        val c = VibrationEffect.startComposition()
        when (feel) {
            Feel.Tap -> c.addPrimitive(PRIMITIVE_CLICK, 0.55f)
            Feel.Press -> c.addPrimitive(PRIMITIVE_TICK, 0.45f)
            Feel.Tick -> c.addPrimitive(PRIMITIVE_TICK, 0.8f)
            Feel.ToggleOn -> c.addPrimitive(PRIMITIVE_CLICK, 0.8f).addPrimitive(PRIMITIVE_TICK, 0.5f, 40)
            Feel.ToggleOff -> c.addPrimitive(PRIMITIVE_TICK, 0.7f)
            Feel.Confirm -> c.addPrimitive(PRIMITIVE_CLICK, 0.6f).addPrimitive(PRIMITIVE_CLICK, 1f, 70)
            Feel.Reject -> c.addPrimitive(PRIMITIVE_QUICK_FALL, 0.7f).addPrimitive(PRIMITIVE_CLICK, 0.6f, 30).addPrimitive(PRIMITIVE_CLICK, 0.6f, 60)
            Feel.Rise -> c.addPrimitive(PRIMITIVE_QUICK_RISE, 0.55f).addPrimitive(PRIMITIVE_CLICK, 0.7f, 10)
            Feel.Fall -> c.addPrimitive(PRIMITIVE_QUICK_FALL, 0.5f)
            Feel.Spin -> if (spin) {
                c.addPrimitive(PRIMITIVE_SPIN, 0.7f).addPrimitive(PRIMITIVE_CLICK, 0.6f, 40)
            } else {
                c.addPrimitive(PRIMITIVE_QUICK_RISE, 0.5f).addPrimitive(PRIMITIVE_QUICK_FALL, 0.5f).addPrimitive(PRIMITIVE_CLICK, 0.6f, 20)
            }
            Feel.Thud -> if (thud) c.addPrimitive(PRIMITIVE_THUD, 0.7f) else c.addPrimitive(PRIMITIVE_CLICK, 1f)
            Feel.Bloom -> c.addPrimitive(PRIMITIVE_QUICK_RISE, 0.8f).addPrimitive(PRIMITIVE_CLICK, 1f, 20)
        }
        return c.compose()
    }

    private fun fallback(feel: Feel): Int = when (feel) {
        Feel.Tap -> HapticFeedbackConstants.CONTEXT_CLICK
        Feel.Press -> HapticFeedbackConstants.VIRTUAL_KEY
        Feel.Tick -> HapticFeedbackConstants.CLOCK_TICK
        Feel.ToggleOn -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONTEXT_CLICK
        Feel.ToggleOff -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        Feel.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        Feel.Reject -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Feel.Rise, Feel.Bloom -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS
        Feel.Fall -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK
        Feel.Spin, Feel.Thud -> HapticFeedbackConstants.LONG_PRESS
    }
}

/** The app's haptics. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

/** One tap is one tick: anything within [windowMs] of the last haptic is dropped. Honours Settings. */
class ThrottledHaptics(private val delegate: HapticFeedback, private val windowMs: Long = 90L) : HapticFeedback {
    private var last = 0L
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!GlassSettings.haptics) return
        val now = SystemClock.uptimeMillis()
        if (now - last < windowMs) return
        last = now
        delegate.performHapticFeedback(hapticFeedbackType)
    }
}

@Composable
fun ProvideGlassHaptics(content: @Composable () -> Unit) {
    val system = LocalHapticFeedback.current
    val throttled = remember(system) { ThrottledHaptics(system) }
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) { Haptics(context, view) }
    CompositionLocalProvider(LocalHapticFeedback provides throttled, LocalHaptics provides haptics, content = content)
}

/** Play [feel] with the app's haptics (nothing outside [ProvideGlassHaptics]). */
@Composable
fun rememberFeel(): (Feel) -> Unit {
    val h = LocalHaptics.current
    return remember(h) { { f: Feel -> h?.play(f) } }
}

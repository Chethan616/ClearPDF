package com.chethan616.clearpdf.ui.glass

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
    CompositionLocalProvider(LocalHapticFeedback provides throttled, content = content)
}

package com.chethan616.clearpdf.ui.utils

import android.os.SystemClock
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * App-wide haptic de-duplication. Glass buttons tick on every tap, and many callers also buzz in
 * their own onClick; anything within [windowMs] of the last haptic is dropped, so one tap is always
 * exactly one pulse — never a double buzz.
 */
class ThrottledHaptics(private val delegate: HapticFeedback, private val windowMs: Long = 90L) : HapticFeedback {
    private var last = 0L
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        // Settings -> Personalization -> Haptic feedback.
        if (!com.chethan616.clearpdf.ui.components.GlassSettings.hapticsEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - last < windowMs) return
        last = now
        delegate.performHapticFeedback(hapticFeedbackType)
    }
}

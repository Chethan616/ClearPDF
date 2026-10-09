package com.chethan616.clearpdf.ui.utils

import android.os.SystemClock
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Where on screen a document was opened from, so its viewer grows out of that spot — and shrinks
 * back into it on Back — instead of zooming from the screen's centre: the iOS "zoom from the
 * thumbnail" cue that ties the page you get to the row you tapped.
 *
 * The tapped element calls [set] right before navigating; the viewer routes' transitions read it.
 * Anything that opens a viewer without setting it (a tool's output, a share intent, the picker) gets
 * the centre, because a pending origin expires after a moment.
 */
object DocumentOpenOrigin {
    private const val VALID_MS = 1500L

    private var pending: TransformOrigin? = null
    private var pendingAt = 0L

    /** The origin the current viewer opened from; Back shrinks toward it. */
    var current: TransformOrigin = TransformOrigin.Center
        private set

    /** [pivotX]/[pivotY] are fractions (0..1) of the window, e.g. the tapped row's centre. */
    fun set(pivotX: Float, pivotY: Float) {
        pending = TransformOrigin(pivotX.coerceIn(0f, 1f), pivotY.coerceIn(0f, 1f))
        pendingAt = SystemClock.uptimeMillis()
    }

    /** For a viewer route's enter transition. Idempotent within one navigation. */
    fun takeForEnter(): TransformOrigin {
        val fresh = pending?.takeIf { SystemClock.uptimeMillis() - pendingAt < VALID_MS }
        current = fresh ?: TransformOrigin.Center
        return current
    }
}

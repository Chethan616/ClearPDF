package com.chethan616.clearpdf.ui.glass

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How springy the glass moves: multipliers on the designed physics, so [Normal] is the design exactly.
 * [bounce] 0.5 is the design (higher overshoots more); [speed] scales stiffness; [press] how far a
 * control swells or dips under the finger; [wobble] the release jiggle; [stretch] the lean toward a
 * dragging finger; [bloom] how much a long-pressed control grows.
 */
@Immutable
data class MotionStyle(
    val bounce: Float = 0.5f,
    val speed: Float = 1f,
    val press: Float = 1f,
    val wobble: Float = 1f,
    val stretch: Float = 1f,
    val bloom: Float = 1f,
) {
    companion object {
        val Normal = MotionStyle()
        val Calm = MotionStyle(bounce = 0.2f, speed = 1.45f, press = 0.6f, wobble = 0f, stretch = 0.5f, bloom = 0.5f)
        val Bouncy = MotionStyle(bounce = 0.75f, speed = 0.92f, press = 1.3f, wobble = 1.6f, stretch = 1.2f, bloom = 1.2f)
        val Jelly = MotionStyle(bounce = 0.95f, speed = 0.82f, press = 1.6f, wobble = 2.2f, stretch = 1.5f, bloom = 1.5f)
        val Presets = listOf(Calm, Normal, Bouncy, Jelly)
        val Default = Bouncy
    }
}

object MotionSettings {
    private const val PREFS = "glass"

    var style: MotionStyle by mutableStateOf(MotionStyle.Default)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = MotionStyle.Default
        style = MotionStyle(
            bounce = p.getFloat("motion_bounce", d.bounce),
            speed = p.getFloat("motion_speed", d.speed),
            press = p.getFloat("motion_press", d.press),
            wobble = p.getFloat("motion_wobble", d.wobble),
            stretch = p.getFloat("motion_stretch", d.stretch),
            bloom = p.getFloat("motion_bloom", d.bloom),
        )
    }

    fun update(context: Context, s: MotionStyle) {
        style = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("motion_bounce", s.bounce)
            .putFloat("motion_speed", s.speed)
            .putFloat("motion_press", s.press)
            .putFloat("motion_wobble", s.wobble)
            .putFloat("motion_stretch", s.stretch)
            .putFloat("motion_bloom", s.bloom)
            .apply()
    }

    /** A designed damping ratio at the user's bounce: 1.6x calmer at 0, 0.4x springier at 1. */
    fun damping(designed: Float): Float = (designed * (1f + (0.5f - style.bounce) * 1.2f)).coerceIn(0.12f, 0.98f)

    fun stiffness(designed: Float): Float = designed * style.speed.coerceIn(0.4f, 2.5f)
}

/**
 * The shared springs. Bounce belongs on draw-time properties (scale, translation, rotation), never on
 * layout or alpha: a bouncing size re-runs the glass blur every overshoot frame, a bouncing alpha flickers.
 */
object GlassMotion {
    /** Springs open: capsules, chevrons, thumbs. ~8 % overshoot. */
    fun <T> morph(): SpringSpec<T> = spring(MotionSettings.damping(0.58f), MotionSettings.stiffness(420f))

    /** Small things landing: icons, beads, toasts. */
    fun <T> pop(): SpringSpec<T> = spring(MotionSettings.damping(0.45f), MotionSettings.stiffness(500f))

    /** Alpha and layout. Never bounces. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** Finger down: lands in ~80 ms, barely overshoots. */
    fun <T> pressIn(): SpringSpec<T> = spring(MotionSettings.damping(0.72f), MotionSettings.stiffness(900f))

    /** Finger up: one small swing past rest. */
    fun <T> pressOut(): SpringSpec<T> = spring(MotionSettings.damping(0.46f), MotionSettings.stiffness(420f))

    /** Menus unfolding from their anchor: width first ... */
    fun <T> unfold(): SpringSpec<T> = spring(MotionSettings.damping(0.68f), MotionSettings.stiffness(420f))

    /** ... then the height a beat behind, like a drop of gel. */
    fun <T> unfoldLag(): SpringSpec<T> = spring(MotionSettings.damping(0.6f), MotionSettings.stiffness(340f))

    /** Folding away: fast and critically damped. Dismissals never bounce. */
    fun <T> fold(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = MotionSettings.stiffness(900f))

    /** Entrances of whole blocks: a soft rise with a little overshoot. */
    fun <T> rise(): SpringSpec<T> = spring(MotionSettings.damping(0.62f), MotionSettings.stiffness(220f))

    val PressedScale: Float get() = 1f - 0.06f * MotionSettings.style.press.coerceIn(0f, 2.5f)

    /** A tap shorter than this still plays the whole press. */
    const val MinPressMillis = 90L

    /** A tapped back button waits this long so its press is seen first. */
    const val BackTapDelayMillis = 280L
}

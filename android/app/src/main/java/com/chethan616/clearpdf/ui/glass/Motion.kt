package com.chethan616.clearpdf.ui.glass

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

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

/** A cubic-bezier easing curve from (0, 0) to (1, 1) through two handles, as CSS and Figma write it. */
@Immutable
data class EaseCurve(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val easing: Easing get() = CubicBezierEasing(x1, y1, x2, y2)

    companion object {
        /** UIKit's drawer curve: fast out, a long gentle landing (the app's own). */
        val Drawer = EaseCurve(0.32f, 0.72f, 0f, 1f)
        val Standard = EaseCurve(0.4f, 0f, 0.2f, 1f)
        val Emphasized = EaseCurve(0.05f, 0.7f, 0.1f, 1f)
        val EaseInOut = EaseCurve(0.42f, 0f, 0.58f, 1f)
        val Linear = EaseCurve(0f, 0f, 1f, 1f)
        val Presets = listOf(Drawer, Standard, Emphasized, EaseInOut, Linear)
    }
}

object MotionSettings {
    private const val PREFS = "glass"

    var style: MotionStyle by mutableStateOf(MotionStyle.Default)
        private set

    /** The curve screens, sheets and fades travel on (Settings ▸ Liquid glass & motion). */
    var curve: EaseCurve by mutableStateOf(EaseCurve.Drawer)
        private set

    /** Multiplies every tween's time: 1 is the design. */
    var duration: Float by mutableStateOf(1f)
        private set

    private var cachedCurve: EaseCurve? = null
    private var cachedEasing: Easing = EaseCurve.Drawer.easing

    val easing: Easing
        get() {
            val c = curve
            if (c != cachedCurve) {
                cachedCurve = c
                cachedEasing = c.easing
            }
            return cachedEasing
        }

    fun updateCurve(context: Context, c: EaseCurve, save: Boolean = true) {
        curve = c
        if (save) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("motion_curve", "${c.x1},${c.y1},${c.x2},${c.y2}").apply()
    }

    fun updateDuration(context: Context, d: Float) {
        duration = d
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("motion_duration", d).apply()
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString("motion_curve", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let {
            curve = EaseCurve(it[0], it[1], it[2], it[3])
        }
        duration = p.getFloat("motion_duration", 1f)
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
 * Motion for everything that is not a liquid glass control: screens, sheets, dialogs, menus, cards,
 * chips, toasts. Apple's way by default: a quick start and a long, soft landing, never past the
 * target. Its curve and pace are the user's (Settings ▸ Liquid glass & motion ▸ Screen transitions).
 */
object Fluid {
    /** The curve for pushes and sheets: UIKit's "drawer" curve unless the user drew another. */
    val Ease: Easing get() = MotionSettings.easing

    /** For small things fading or sliding a short way. */
    val Soft: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    private fun ms(designed: Int): Int = (designed * MotionSettings.duration.coerceIn(0.3f, 3f)).roundToInt()

    /** A screen pushed in. */
    fun <T> push(): TweenSpec<T> = tween(ms(420), easing = Ease)

    /** A screen popped (Back): a touch quicker than the push. */
    fun <T> pop(): TweenSpec<T> = tween(ms(360), easing = Ease)

    /** A sheet, a dialog, a document rising. */
    fun <T> rise(): TweenSpec<T> = tween(ms(400), easing = Ease)

    /** Anything going away. */
    fun <T> dismiss(): TweenSpec<T> = tween(ms(260), easing = Ease)

    /** A sheet leaving: it eases away, then gathers speed off the edge, rather than sinking slowly out of sight. */
    fun <T> sheetLeave(): TweenSpec<T> = tween(ms(240), easing = Leave)

    private val Leave: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.5f)

    /** Small changes: a title swapping, a chip lighting, an icon turning. */
    fun <T> quick(): TweenSpec<T> = tween(ms(260), easing = Ease)

    /** Fades. */
    fun <T> fade(): TweenSpec<T> = tween(ms(200), easing = Soft)

    /** Values that follow something (an interrupted drag, a moving target): no overshoot. */
    fun <T> follow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 520f)

    /** A finger on a flat surface (cards, rows): it sinks fast ... */
    fun <T> pressIn(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1800f)

    /** ... and comes back softly, never past rest. */
    fun <T> pressOut(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 420f)

    /** How far a flat pressable sinks. */
    const val PressedScale = 0.97f
}

/**
 * The liquid glass's motion: springs tuned by Settings ▸ Motion (menus, dialogs, toasts, the + fan,
 * the recorder pill, the press feel in `Press.kt`). Overshoot belongs on draw-time properties
 * (scale, translation, rotation), never on alpha (it flickers); layout values may swing only if
 * clamped. Sheets keep one well-damped spring of their own whatever the setting (`Overlays.kt`).
 */
object GlassMotion {
    /** Alpha and colour. Never bounces. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** A menu unfolding: its width leads and settles with a soft overshoot (the one every entrance is judged against). */
    fun <T> unfold(): SpringSpec<T> = spring(MotionSettings.damping(0.68f), MotionSettings.stiffness(420f))

    /** The menu's height, a beat behind its width. */
    fun <T> unfoldLag(): SpringSpec<T> = spring(MotionSettings.damping(0.6f), MotionSettings.stiffness(340f))

    /** Springs open: capsules, chevrons, thumbs, dialogs, toasts. */
    fun <T> morph(): SpringSpec<T> = spring(MotionSettings.damping(0.58f), MotionSettings.stiffness(420f))

    /** A small thing popping into place: fan chips, the + glyph. */
    fun <T> pop(): SpringSpec<T> = spring(MotionSettings.damping(0.45f), MotionSettings.stiffness(500f))

    /** The recorder pill growing: slow and soft, with a floor on damping so Jelly cannot ring on. */
    fun <T> shape(): SpringSpec<T> = spring(maxOf(MotionSettings.damping(0.6f), ShapeMinDamping), MotionSettings.stiffness(105f))

    /** The recorder pill shrinking back. */
    fun <T> shapeBack(): SpringSpec<T> = spring(maxOf(MotionSettings.damping(0.62f), ShapeMinDamping), MotionSettings.stiffness(125f))

    private const val ShapeMinDamping = 0.46f

    /** A tap shorter than this still plays the whole press. */
    const val MinPressMillis = 90L
}

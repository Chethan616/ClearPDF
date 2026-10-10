package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The app's shared motion vocabulary, factored out of `ShareMorphButton` so the search bars, title
 * pills and pressable tiles all move with the same physics instead of each screen inventing its own
 * spring.
 *
 * The split matters: **bounce belongs on draw-time properties** (scale, translation, rotation) and
 * **not on layout** (height, width). A bouncing layout property re-measures on every overshoot frame,
 * which forces `drawBackdrop` to re-run its blur and lens at a new size. So [morph] is used for
 * scale, [settle] for anything that changes real size, and [fade] for alpha — a bouncing alpha just
 * reads as a flicker.
 *
 * Press physics (iOS 26/27 Liquid Glass): the finger lands on [pressIn] (fast, barely overshoots, so
 * the press reads instantly), lifts on [pressOut] (springs once past rest, then settles), and glass
 * controls add a [wobble] (X and Y squash out of phase, one visible swing). A tap shorter than
 * [MinPressMillis] still shows the full press before it springs back.
 */
object GlassMotion {

    /** Underdamped: overshoots ~8% and settles. The capsule/bar "springs open" feel. */
    fun <T> morph(): SpringSpec<T> = spring(dampingRatio = 0.58f, stiffness = 420f)

    /** Snappier and bouncier — for small elements that should pop, like an icon landing. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.45f, stiffness = 500f)

    /** Critically damped. For alpha, and for layout properties where overshoot costs a re-measure. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** Alias of [settle], named for intent at call sites that animate opacity. */
    fun <T> fade(): SpringSpec<T> = settle()

    /** Press-down and bounce-back for value-driven presses (prefer [rememberJellyPress]). */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 700f)

    /** Finger down: quick and nearly critically damped, so the press lands in ~80 ms. */
    fun <T> pressIn(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 900f)

    /** Finger up: springs back past rest once (~2 % of the dip) and settles. */
    fun <T> pressOut(): SpringSpec<T> = spring(dampingRatio = 0.46f, stiffness = 420f)

    /** Release jiggle for glass: one visible out-of-phase squash, the second swing is ~20 %. */
    fun <T> wobble(): SpringSpec<T> = spring(dampingRatio = 0.42f, stiffness = 420f)

    /** Menus and popovers unfolding from their anchor: ~5 % overshoot, settled in ~350 ms. */
    fun <T> unfold(): SpringSpec<T> = spring(dampingRatio = 0.68f, stiffness = 420f)

    /** The trailing axis of an unfold: a touch softer, so the panel lands like a drop of gel. */
    fun <T> unfoldLag(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 340f)

    /** Folding away: fast and critically damped. Dismissals never bounce. */
    fun <T> fold(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 900f)

    /** Uniform pressed scale for tiles, rows and morphing buttons. */
    const val PressedScale = 0.94f

    /** A tap shorter than this still shows the whole press before releasing. */
    const val MinPressMillis = 90L
}

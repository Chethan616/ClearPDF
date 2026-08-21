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

    /** Press-down and bounce-back, matching LiquidButton's deformation. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)

    /** Uniform pressed scale for tiles, rows and morphing buttons. */
    const val PressedScale = 0.94f
}

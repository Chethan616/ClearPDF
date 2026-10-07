package com.chethan616.clearpdf.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal for app-wide dark mode state.
 * Allows all composables to access the current theme without prop drilling.
 */
val LocalIsDarkMode = compositionLocalOf { false }

/**
 * Opt-in "Reduce glass motion" (Settings → Appearance, #53). `false` by default — full glass. When
 * `true`, [com.chethan616.clearpdf.ui.utils.rememberUISensor] freezes the gravity-tracked specular
 * highlight that every `liquidGlassPanel` reads, instead of threading an explicit parameter through
 * every one of its call sites across the app.
 */
val LocalReducedGlassMotion = compositionLocalOf { false }

/**
 * Deferred read of "is my nearest scroll container actively moving" (`ScrollState`/`LazyListState`'s
 * own `isScrollInProgress`, hoisted by the screen and provided here). `liquidGlassPanel` reads this
 * lambda from its DRAW phase -- same convention as `UISensor.gravityAngle` -- so toggling it only
 * invalidates that panel's draw, not a recomposition of the whole screen.
 *
 * Profiled on Tools: 4 simultaneous glass panels each re-running vibrancy+blur+lens every scroll
 * frame cost ~32% janky frames with the UI thread (not the GPU) as the bottleneck. Screens that
 * provide this skip those three shader passes while scrolling and fall back to a flat tinted fill,
 * restoring full glass the instant the scroll settles. `staticCompositionLocalOf` because providing
 * it (per screen, once) is far rarer than reading it (every panel, every frame).
 */
val LocalIsScrolling = staticCompositionLocalOf<() -> Boolean> { { false } }

package com.chethan616.clearpdf.ui.theme

import androidx.compose.runtime.compositionLocalOf

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

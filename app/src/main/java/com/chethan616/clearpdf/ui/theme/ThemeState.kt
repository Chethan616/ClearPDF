package com.chethan616.clearpdf.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for app-wide dark mode state.
 * Allows all composables to access the current theme without prop drilling.
 */
val LocalIsDarkMode = compositionLocalOf { false }

/*
 * Adapted from AndroidLiquidGlass by kyant0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * Licensed under the Apache License, Version 2.0.
 */

package com.kyant.backdrop.highlight

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Highlight(
    val width: Dp = 0.5f.dp,
    val blurRadius: Dp = width / 2f,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val style: HighlightStyle = HighlightStyle.Default
) {

    companion object {

        @Stable
        val Default: Highlight = Highlight()

        @Stable
        val Ambient: Highlight = Highlight(style = HighlightStyle.Ambient)

        @Stable
        val Plain: Highlight = Highlight(style = HighlightStyle.Plain)
    }
}

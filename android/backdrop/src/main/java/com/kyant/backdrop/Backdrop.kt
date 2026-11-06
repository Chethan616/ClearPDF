/*
 * Adapted from AndroidLiquidGlass by kyant0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * Licensed under the Apache License, Version 2.0.
 */

package com.kyant.backdrop

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

interface Backdrop {

    val isCoordinatesDependent: Boolean

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )

    /**
     * Where [coordinates] sit relative to what this backdrop draws, as a key: equal keys draw the same
     * pixels, so a glass node that moved but kept its key (a chip on a card being scrolled with the
     * card) needn't re-blur. [UnknownPosition] when it can't tell: always redraw.
     */
    fun positionKey(coordinates: LayoutCoordinates): Long = UnknownPosition
}

const val UnknownPosition: Long = Long.MIN_VALUE

/** Two keys as one, unknown if either is. */
fun mixPositionKeys(a: Long, b: Long): Long =
    if (a == UnknownPosition || b == UnknownPosition) UnknownPosition else a * 31 + b

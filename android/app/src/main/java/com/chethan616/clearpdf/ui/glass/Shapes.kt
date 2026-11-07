package com.chethan616.clearpdf.ui.glass

import androidx.compose.ui.unit.Dp
import com.kyant.shapes.RoundedCornerStyle

// Circular corners, not kyant's default continuous ones: a continuous corner is a path, and a path
// clip that moves (a card or chip scrolling, a sheet sliding) makes Skia rasterize a fresh software
// mask and upload it on every frame. A circular one is a rounded rect the GPU clips by itself.

/** A capsule whose ends are half circles. */
val Capsule = com.kyant.shapes.Capsule(RoundedCornerStyle.Circular)

/** A rounded rectangle with circular corners of [radius]. */
fun RoundedRectangle(radius: Dp) = com.kyant.shapes.RoundedRectangle(radius, RoundedCornerStyle.Circular)

/**
 * A rounded rectangle with Apple's continuous ("squircle") corners of [radius]. Only for content
 * clipped inside a layer of its own that moves by its layer's transform (a card in a
 * `graphicsLayer`): there the mask is recorded once. Never on content that scrolls or resizes.
 */
fun Squircle(radius: Dp) = com.kyant.shapes.RoundedRectangle(radius, RoundedCornerStyle.Continuous)

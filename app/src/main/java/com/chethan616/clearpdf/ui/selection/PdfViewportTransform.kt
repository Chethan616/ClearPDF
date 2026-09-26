package com.chethan616.clearpdf.ui.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * The PDF viewer's document-level zoom/pan, as a pure value: the ONE place page coordinates are
 * mapped to screen coordinates and back.
 *
 * The page column sits in a `graphicsLayer` with `scaleX = scaleY = scale`,
 * `translationX = offsetX`, `translationY = 0` and `transformOrigin = (0.5, 0)` — horizontally
 * centred, top-anchored. Vertical position comes from the LazyColumn scroll, which lives INSIDE the
 * layer, so it is already folded into a page's [pageOrigin] (its top-left in the layer's own,
 * unscaled coordinates).
 *
 * "Screen" here means the viewer container's coordinates (the overlay that hosts the handles and the
 * toolbar), which shares its origin with the un-transformed layer.
 */
data class PdfViewportTransform(
    val containerWidth: Float,
    val scale: Float,
    val offsetX: Float
) {
    private val cx get() = containerWidth / 2f
    private val s get() = if (scale == 0f) 1f else scale

    fun layerToScreen(p: Offset): Offset =
        Offset((p.x - cx) * s + cx + offsetX, p.y * s)

    fun screenToLayer(p: Offset): Offset =
        Offset((p.x - cx - offsetX) / s + cx, p.y / s)

    /** A point in a page's local, unscaled coordinates → screen. */
    fun pageToScreen(pagePoint: Offset, pageOrigin: Offset): Offset =
        layerToScreen(pagePoint + pageOrigin)

    /** A screen point → that page's local, unscaled coordinates. */
    fun screenToPage(screenPoint: Offset, pageOrigin: Offset): Offset =
        screenToLayer(screenPoint) - pageOrigin

    fun pageRectToScreen(r: Rect, pageOrigin: Offset): Rect {
        val tl = pageToScreen(r.topLeft, pageOrigin)
        val br = pageToScreen(r.bottomRight, pageOrigin)
        return Rect(tl, br)
    }

    /** Screen length of [pageLength] page pixels. */
    fun toScreenLength(pageLength: Float): Float = pageLength * s
}

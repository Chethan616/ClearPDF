/*
 * Adapted from AndroidLiquidGlass by kyant0.
 * Source: https://github.com/Kyant0/AndroidLiquidGlass
 * Licensed under the Apache License, Version 2.0.
 */

package com.kyant.backdrop.highlight

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.RuntimeShaderCacheImpl
import com.kyant.backdrop.ShapeProvider
import com.kyant.backdrop.clipOutline
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var prevStyle: HighlightStyle? = null

    // what the layer was last recorded for: the rim only changes with these, not with the node's
    // position, so a glass node that moves (a fan chip, a swaying card) doesn't re-record its rim
    private var recordedSize = IntSize.Zero
    private var recordedDensity = 0f
    private var recordedWidth = Float.NaN
    private var recordedBlur = Float.NaN
    private var recordedStyle: HighlightStyle? = null
    private var recordedShape: Shape? = null
    private var recordedLayoutDirection: LayoutDirection? = null

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize =
                IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            val shape = shapeProvider.shape
            val widthPx = highlight.width.toPx()
            val blurPx = highlight.blurRadius.toPx()
            val unchanged = safeSize == recordedSize &&
                density.density == recordedDensity &&
                widthPx == recordedWidth &&
                blurPx == recordedBlur &&
                highlight.style == recordedStyle &&
                shape == recordedShape &&
                layoutDirection == recordedLayoutDirection

            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = highlight.style.blendMode
            if (!unchanged) {
                configurePaint(highlight)
                highlightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
                recordedSize = safeSize
                recordedDensity = density.density
                recordedWidth = widthPx
                recordedBlur = blurPx
                recordedStyle = highlight.style
                recordedShape = shape
                recordedLayoutDirection = layoutDirection
            }

            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        prevStyle = null
        recordedStyle = null
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth =
            ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        val blurRadius = highlight.blurRadius.toPx()
        paint.asFrameworkPaint().maskFilter =
            if (blurRadius > 0f) {
                BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paint.shader = with(highlight.style) {
                createShader(
                    shape = shapeProvider.shape,
                    runtimeShaderCache = runtimeShaderCache
                )
            }
        }
    }
}

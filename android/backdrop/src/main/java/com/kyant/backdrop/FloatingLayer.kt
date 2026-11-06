package com.kyant.backdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toIntSize

/**
 * Moves, scales and fades the content like `graphicsLayer`, but only at draw time: the content is laid
 * out where it comes to rest, so the glass inside keeps its position. The content is recorded once
 * into an offscreen texture; while [block]'s values animate only the texture's transform changes, so
 * the glass inside is not re-blurred on any frame of the motion. Touches land where the content rests.
 * A [GraphicsLayerScope.shape] with [GraphicsLayerScope.clip] clips the texture, animated as cheaply.
 */
fun Modifier.floatingLayer(block: GraphicsLayerScope.() -> Unit): Modifier =
    this then FloatingLayerElement(block)

private class FloatingLayerElement(
    val block: GraphicsLayerScope.() -> Unit
) : ModifierNodeElement<FloatingLayerNode>() {

    override fun create(): FloatingLayerNode = FloatingLayerNode(block)

    override fun update(node: FloatingLayerNode) {
        node.block = block
        node.applyTransform()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "floatingLayer"
        properties["block"] = block
    }

    override fun equals(other: Any?): Boolean = other is FloatingLayerElement && other.block == block

    override fun hashCode(): Int = block.hashCode()
}

private class FloatingLayerNode(
    var block: GraphicsLayerScope.() -> Unit
) : DrawModifierNode, ObserverModifierNode, Modifier.Node() {

    private var layer: GraphicsLayer? = null
    private val scope = InverseLayerScope()
    private var size = Size.Unspecified

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun onObservedReadsChanged() {
        applyTransform()
        // the texture's properties changed, not its content: a new frame, without re-recording anything
        requireView().invalidate()
    }

    fun applyTransform() {
        val layer = layer ?: return
        if (!isAttached || size == Size.Unspecified) return
        val d = requireDensity()
        observeReads {
            scope.reset()
            scope.size = size
            scope.density = d.density
            scope.fontScale = d.fontScale
            scope.block()
        }
        layer.alpha = scope.alpha
        layer.scaleX = scope.scaleX
        layer.scaleY = scope.scaleY
        layer.translationX = scope.translationX
        layer.translationY = scope.translationY
        layer.rotationZ = scope.rotationZ
        val origin = scope.transformOrigin
        layer.pivotOffset = Offset(origin.pivotFractionX * size.width, origin.pivotFractionY * size.height)
        // an animated clip is only an outline on the texture: nothing inside is redrawn for it
        if (scope.clip) {
            when (val o = scope.shape.createOutline(size, requireLayoutDirection(), d)) {
                is Outline.Rectangle -> layer.setRectOutline(o.rect.topLeft, o.rect.size)
                is Outline.Rounded -> {
                    val r = o.roundRect
                    layer.setRoundRectOutline(Offset(r.left, r.top), Size(r.width, r.height), r.topLeftCornerRadius.x)
                }
                is Outline.Generic -> layer.setPathOutline(o.path)
            }
        }
        layer.clip = scope.clip
    }

    override fun ContentDrawScope.draw() {
        val layer = layer
        if (layer == null) {
            drawContent()
            return
        }
        if (this.size != this@FloatingLayerNode.size) {
            this@FloatingLayerNode.size = this.size
            applyTransform()
        }
        layer.record(this.size.toIntSize()) { this@draw.drawContent() }
        drawLayer(layer)
    }
}

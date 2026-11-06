package com.kyant.backdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo

/**
 * Marks everything below as "in flight" while [moving] is true. Glass nodes under it keep drawing
 * but only re-record their backdrop about ten times a second instead of on every position change
 * of every frame, and once more when the motion stops, so a page of flying glass cards stays cheap.
 */
fun Modifier.throttleBackdrops(moving: () -> Boolean): Modifier =
    this then ThrottleBackdropsElement(moving)

private const val ThrottleKey = "com.kyant.backdrop.ThrottleBackdrops"

private class ThrottleBackdropsElement(
    val moving: () -> Boolean
) : ModifierNodeElement<ThrottleBackdropsNode>() {

    override fun create(): ThrottleBackdropsNode = ThrottleBackdropsNode(moving)

    override fun update(node: ThrottleBackdropsNode) {
        node.moving = moving
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "throttleBackdrops"
        properties["moving"] = moving
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThrottleBackdropsElement) return false
        return moving == other.moving
    }

    override fun hashCode(): Int = moving.hashCode()
}

internal class ThrottleBackdropsNode(
    var moving: () -> Boolean
) : Modifier.Node(), TraversableNode {

    override val traverseKey: Any get() = ThrottleKey
}

internal fun Modifier.Node.findBackdropThrottle(): ThrottleBackdropsNode? =
    findNearestAncestor(ThrottleKey) as? ThrottleBackdropsNode

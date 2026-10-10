package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A colour swatch in the liquid-glass family — a vivid tinted bead with a specular top-left glint, a
 * light-to-dark rim and inner depth, springing down under the finger and up when selected — drawn
 * entirely in one `drawBehind` (no backdrop sampling), so a palette of 80 of them stays free to show
 * and scroll. Use real [LiquidIconButton] beads only for short rows (under ~10).
 */
@Composable
fun GlassBead(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.84f
            selected -> 1.1f
            else -> 1f
        },
        spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "beadScale"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = size.minDimension / 2f
                val c = center
                // Body: the "Get it" vivid recipe — a soft white base under the tint.
                drawCircle(Color.White.copy(alpha = 0.35f), r, c)
                drawCircle(if (color.alpha < 1f) color else color.copy(alpha = 0.92f), r, c)
                // Inner depth toward the bottom.
                drawCircle(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), startY = c.y, endY = c.y + r),
                    r, c
                )
                // Specular glint, top-left.
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(c.x - r * 0.35f, c.y - r * 0.45f),
                        radius = r * 0.8f
                    ),
                    r, c
                )
                // Rim: bright on top, faint underneath — the lens edge.
                val w = 1.4.dp.toPx()
                drawCircle(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.12f))),
                    r - w / 2f, c, style = Stroke(w)
                )
                if (selected) drawCircle(Color.White, r - 1.6.dp.toPx(), c, style = Stroke(2.2.dp.toPx()))
            }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                null,
                Modifier.size(13.dp),
                if (color.luminance() > 0.6f) Color.Black else Color.White
            )
        }
    }
}

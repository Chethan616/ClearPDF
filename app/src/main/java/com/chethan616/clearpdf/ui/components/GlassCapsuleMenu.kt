package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** One circular action inside a [GlassCapsuleMenu]. */
data class GlassMenuAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit
)

private val ActionSize = 40.dp

/** How much of the morph each successive circle is delayed by, as a fraction of `progress`. */
private const val StaggerFraction = 0.07f

/**
 * A contextual menu shaped like the rest of the design language: **one** glass capsule with flat,
 * tinted circles inside it — not a cluster of free-floating glass buttons.
 *
 * Two deliberate choices, both for the same reason:
 *
 * - The capsule is a single [drawBackdrop] surface, so opening the menu costs one blur+lens pass
 *   instead of one per action. The circles are flat [background]s, which are free.
 * - **The capsule only fades.** A glass surface samples the backdrop under the region it currently
 *   covers, so scaling or translating it re-runs blur+lens every frame. The morph therefore lives on
 *   the flat circles, which can scale and rise as much as they like.
 *
 * [progress] (0..1) is owned by the caller, so the menu can be anchored and driven by whatever
 * gesture opened it. The per-circle stagger is derived from it — no extra animation state.
 */
@Composable
fun GlassCapsuleMenu(
    actions: List<GlassMenuAction>,
    backdrop: Backdrop,
    uiSensor: UISensor,
    progress: Float,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color.Unspecified
) {
    val container = if (surfaceColor == Color.Unspecified) {
        Color.White.copy(0.10f)
    } else {
        surfaceColor
    }

    Row(
        modifier
            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                highlight = {
                    Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))
                },
                shadow = { Shadow(radius = 10f.dp, color = Color.Black.copy(alpha = 0.14f)) },
                innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.25f) },
                onDrawSurface = { drawRect(container) }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEachIndexed { index, action ->
            // Cascade left-to-right by re-mapping the shared progress rather than starting a spring
            // per circle — `spring()` has no delay parameter, and one clock keeps them in lockstep.
            val head = index * StaggerFraction
            val span = (1f - head).coerceAtLeast(0.001f)
            val local = ((progress - head) / span).coerceIn(0f, 1f)
            CapsuleAction(action, local)
        }
    }
}

@Composable
private fun CapsuleAction(action: GlassMenuAction, local: Float) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.90f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "capsuleActionPress"
    )

    Box(
        Modifier
            .size(ActionSize)
            .graphicsLayer {
                val s = lerp(0.4f, 1f, local) * press
                scaleX = s
                scaleY = s
                alpha = local
                translationY = lerp(14f, 0f, local) * density
            }
            .clip(CircleShape)
            .background(action.tint.copy(0.85f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = action.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(action.icon, action.label, Modifier.size(19.dp), Color.White)
    }
}

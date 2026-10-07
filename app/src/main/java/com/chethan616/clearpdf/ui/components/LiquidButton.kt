package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.LocalIsScrolling
import com.chethan616.clearpdf.ui.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

private const val CLOSE_CROSS_SVG_PATH =
    "M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z"

@Composable
fun CloseCrossIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    val vector = remember(tint) {
        ImageVector.Builder(
            name = "CloseCross",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).addGroup(
            name = "group",
            translationY = 960f
        ).addPath(
            pathData = PathParser().parsePathString(CLOSE_CROSS_SVG_PATH).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(tint)
        ).build()
    }
    Icon(vector, contentDescription = stringResource(R.string.close), modifier = modifier, tint = tint)
}

/**
 * Catalog-style LiquidButton — matches the "Pick an Image" button exactly.
 * Interactive press-deformation, lens refraction, blur, vibrancy, specular highlight.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    // Frost behind the pill. 2 dp is the catalog look over the wallpaper; a button that sits on
    // top of another glass panel (dialog actions) passes that panel's blur so it reads as the same
    // frosted sheet with a lensed rim instead of a sharp window punched through it.
    blurRadius: Dp = 2.dp,
    horizontalContentPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    // A plain (non-deferred) read: this recomposes LiquidButton on scroll start/stop, which is rare
    // -- not on every frame -- so it's cheap, and it is the only way to skip drawBackdrop's own
    // backdrop CAPTURE, not just its vibrancy/blur/lens shaders. A screen like Settings has a few
    // dozen of these (every chip, pill, action button); reading this from inside `effects` instead
    // (as liquidGlassPanel does, where panel counts are much lower) still paid for that capture on
    // every one of them every scroll frame and left Settings at ~75-80% jank despite the shader skip.
    val isScrolling = LocalIsScrolling.current()

    Row(
        modifier
            .then(
                if (isScrolling) {
                    Modifier.clip(Capsule).drawBehind {
                        if (tint.isSpecified) {
                            drawRect(Color.White.copy(alpha = 0.42f))
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.8f))
                        }
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    }
                } else {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule },
                        effects = {
                            vibrancy()
                            blur(blurRadius.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        },
                        layerBlock = if (isInteractive) {
                            {
                                val width = size.width
                                val height = size.height

                                val progress = interactiveHighlight.pressProgress
                                val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                                val maxOffset = size.minDimension
                                val initialDerivative = 0.05f
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                                translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                                val maxDragScale = 4f.dp.toPx() / size.height
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX =
                                    scale +
                                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                            (width / height).fastCoerceAtMost(1f)
                                scaleY =
                                    scale +
                                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                            (height / width).fastCoerceAtMost(1f)
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            if (tint.isSpecified) {
                                // "Get it" look everywhere: a tinted pill only read vivid over light content
                                // (the tint composited onto white); over the dark wallpaper the same 75% tint
                                // went murky. A soft white base first makes the colour come out bright and
                                // saturated on any backdrop while the lens rim still refracts.
                                drawRect(Color.White.copy(alpha = 0.42f))
                                drawRect(tint, blendMode = BlendMode.Hue)
                                drawRect(tint.copy(alpha = 0.8f))
                            }
                            if (surfaceColor.isSpecified) {
                                drawRect(surfaceColor)
                            }
                        }
                    )
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(48f.dp)
            .padding(horizontal = horizontalContentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

package com.chethan616.clearpdf.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.utils.InteractiveHighlight
import com.chethan616.clearpdf.ui.utils.liquidPressTransform
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
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
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            longPressExpand = true,
            onLongPressExpand = { currentHaptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        )
    }
    // Flat wallpaper: same pixels, none of the blur/lens/offscreen work — see FlatBackdrop.
    val flat = flatColorOf(backdrop)
    val style = GlassSettings.style

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule },
                shadow = null,
                effects = if (flat != null) ({}) else ({
                    glassEffects(style, blurRadius.toPx(), 12f.dp.toPx(), 24f.dp.toPx())
                }),
                highlight = { glassHighlight(style) },
                onDrawBackdrop = if (flat != null) ({ _ -> drawFlatVibrantBackdrop(flat) }) else ({ it() }),
                layerBlock = if (isInteractive) {
                    { liquidPressTransform(interactiveHighlight) }
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
                        drawRect(surfaceColor.glassTint(style))
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = { if (interactiveHighlight.expandProgress < 0.5f) haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }
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

package com.chethan616.clearpdf.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

/**
 * The PDF viewer top bar's glass, as a modifier for surfaces that are not buttons.
 *
 * This is [LiquidIconButton] and [LiquidButton]'s effect stack verbatim — `vibrancy`, a 2 dp blur and
 * a 12x24 lens, with `highlight`/`shadow` left at `drawBackdrop`'s defaults and no inner shadow —
 * lifted off their hardcoded circle and capsule so a panel can wear the same material. Those two
 * components are read-only, hence the copy rather than a shared helper.
 *
 * Deliberately lighter than [liquidGlassPanel], which runs an 8 dp blur, a 20x40 depth lens, a
 * gravity-angled highlight and an inner shadow. That heavier stack is still right for dialogs: they
 * sit over a scrim and need an edge of their own. Chrome sits directly on the document and should read
 * as one material with the buttons floating on top of it.
 *
 * Takes an explicit [color] rather than resolving the theme, so it is not `@Composable` and each
 * viewer passes the same `chromeGlass` its buttons already use.
 */
fun Modifier.viewerGlass(
    backdrop: Backdrop,
    color: Color,
    shape: () -> Shape = { RoundedRectangle(28f.dp) }
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(2f.dp.toPx())
        lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    onDrawSurface = { drawRect(color) }
)

/**
 * The neutral chrome tint the theme-driven viewers pair with [viewerGlass], so their panels and their
 * buttons resolve the same colour from one place instead of each screen writing the literal.
 *
 * The PDF viewer does *not* use this: its tint is chosen from the current page's luminance rather than
 * the app theme, because its chrome floats over the document itself.
 */
fun viewerChromeGlass(isDark: Boolean): Color =
    if (isDark) Color(0xFF20242C).copy(0.7f) else Color.White.copy(0.55f)

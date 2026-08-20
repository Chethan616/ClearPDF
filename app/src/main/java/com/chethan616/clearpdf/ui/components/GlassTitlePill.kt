package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop

/**
 * Drop-in replacement for a `LiquidGlassTopBar` sitting in a header [Row]: it takes the remaining
 * width, centres a [GlassTitlePill] in it, and adds a 40 dp balancer so the pill is optically
 * centred against the leading back button. Every tool screen's header is this shape.
 */
@Composable
fun RowScope.GlassScreenTitle(
    title: String,
    backdrop: Backdrop,
    fontFamily: FontFamily? = null
) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        GlassTitlePill(text = title, backdrop = backdrop, fontFamily = fontFamily)
    }
    Spacer(Modifier.size(40.dp))
}

/**
 * The title pill used across the app — the *same* widget as the PDF viewer's "Page 1 / 12" and the
 * spreadsheet viewer's "Sheet 1 / 3" chip (`PdfViewerScreen.kt:778`): a [LiquidButton] capsule at its
 * default 48 dp height and 16 dp horizontal padding, with 13 sp SemiBold label. Only the text differs.
 *
 * It keeps [LiquidButton]'s interactive press deformation, so the pill squashes and refracts under a
 * finger exactly like the viewer's does, and it springs in on first composition with a soft overshoot.
 *
 * The surface defaults to the theme's glass tint rather than the viewer's fixed dark chrome, so the
 * pill stays legible on Home and Tools in light mode.
 */
@Composable
fun GlassTitlePill(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    surfaceColor: Color = Color.Unspecified,
    /** Only the typeface may vary — size and weight stay at the viewer's 13 sp SemiBold. */
    fontFamily: FontFamily? = null,
    animateIn: Boolean = true
) {
    val isDarkMode = LocalIsDarkMode.current
    val resolvedSurface = if (surfaceColor.isSpecified) {
        surfaceColor
    } else {
        if (isDarkMode) Color(0xFF1E1E1E).copy(0.35f) else Color(0xFFFAFAFA).copy(0.35f)
    }

    var shown by remember { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        if (shown) 1f else 0.9f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "titlePillScale"
    )
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "titlePillAlpha"
    )

    LiquidButton(
        onClick = onClick ?: {},
        backdrop = backdrop,
        surfaceColor = resolvedSurface,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = LiquidGlassColors.text(isDarkMode),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = fontFamily
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

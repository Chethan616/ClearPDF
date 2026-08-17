package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Shared glass design-system primitives (Item 4). These are thin wrappers over the
 * existing LiquidButton / LiquidIconButton / liquidGlassPanel so every screen shares
 * the same spacing, corner radii, typography and tint — instead of re-deriving them.
 * The document/page surface intentionally stays solid + readable; only floating UI
 * uses glass.
 */
object GlassDimens {
    val ScreenPadding = 16.dp
    val SectionGap    = 16.dp
    val SectionRadius = 24.dp
    val InnerRadius   = 16.dp
    val SectionPadding = 20.dp
    val TitleSize: TextUnit = 16.sp
}

/** Solid (non-refractive) glass section card. Used for settings-style grouped rows
 *  where a translucent panel would hurt text legibility. */
fun Modifier.glassSection(isLight: Boolean, radius: Dp = GlassDimens.SectionRadius): Modifier {
    val container = if (isLight) Color.White.copy(0.68f) else Color(0xFF161820).copy(0.72f)
    val borderCol = if (isLight) Color.White.copy(0.80f) else Color.White.copy(0.12f)
    return this
        .clip(RoundedCornerShape(radius))
        .background(container)
        .border(1.dp, borderCol, RoundedCornerShape(radius))
}

/** Standard section header: M3 rounded icon tile + title, with an optional trailing slot. */
@Composable
fun GlassSectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    titleColor: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(iconTint.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), iconTint)
        }
        BasicText(
            title,
            style = TextStyle(titleColor, GlassDimens.TitleSize, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Small tinted value pill (e.g. a "72%" quality badge). */
@Composable
fun GlassChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        BasicText(text, style = TextStyle(color, 13.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * Standard tool-screen shell: a floating back button + glass top bar with the shared
 * staggered fade+rise entrance, and a scrollable content column. Replaces the ~45 lines
 * of identical animation/header boilerplate every tool screen used to hand-roll.
 */
@Composable
fun ToolScaffold(
    title: String,
    backdrop: LayerBackdrop,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleFontSize: TextUnit = 18.sp,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = !LocalIsDarkMode.current
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val uiSensor = rememberUISensor()
    val density = LocalDensity.current.density

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val topSpec = tween<Float>(durationMillis = 500, easing = FastOutSlowInEasing)
    val bodySpec = tween<Float>(durationMillis = 600, delayMillis = 100, easing = FastOutSlowInEasing)
    val topAlpha by animateFloatAsState(if (isVisible) 1f else 0f, topSpec, label = "toolTopA")
    val topY     by animateFloatAsState(if (isVisible) 0f else 16f, topSpec, label = "toolTopY")
    val bodyAlpha by animateFloatAsState(if (isVisible) 1f else 0f, bodySpec, label = "toolBodyA")
    val bodyY     by animateFloatAsState(if (isVisible) 0f else 24f, bodySpec, label = "toolBodyY")

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(GlassDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(GlassDimens.SectionGap)
    ) {
        Row(
            Modifier.graphicsLayer { alpha = topAlpha; translationY = topY * density },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidIconButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
            }
            LiquidGlassTopBar(
                title = title,
                backdrop = backdrop,
                uiSensor = uiSensor,
                modifier = Modifier.weight(1f),
                titleFontSize = titleFontSize
            )
            headerTrailing?.invoke(this)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = bodyAlpha; translationY = bodyY * density }
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GlassDimens.SectionGap),
            content = content
        )
    }
}

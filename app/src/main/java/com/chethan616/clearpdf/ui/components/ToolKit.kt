package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop

/*
 * The tool-screen kit: the handful of building blocks every tool screen (merge, split, compress,
 * watermark…) was re-deriving by hand, each slightly differently — icon sizes from 52 to 72 dp,
 * subtitles centred on some screens and not others, error text in four different reds, "disabled"
 * buttons that went white-on-white, and option chips whose unselected state painted a 42% white base
 * under a white label. Everything here is composed from the existing glass primitives
 * (liquidGlassPanel, LiquidButton), so it is the same material, just used one way.
 *
 * Rules the kit encodes:
 *  - Primary action = vivid tinted LiquidButton in the screen's accent with a white label.
 *  - Secondary action = clear glass pill (the title pill's own surface) with ink-coloured label.
 *  - Labels never clip: they are the capsule's last child and ellipsize on one line.
 *  - Things that appear after a choice (a picked file, a result) reveal with [GlassReveal].
 */

/** Height opens critically damped (an overshooting height re-measures every glass panel below). */
private val RevealEnter: EnterTransition =
    expandVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) +
        fadeIn(spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow))

/** Fade first (fast), then fold the space away — the glass is invisible while its slot moves. */
private val RevealExit: ExitTransition =
    fadeOut(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) +
        shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top)

/**
 * Interruptible spring reveal for a block that appears in a tool column (options after a file is
 * picked, a result card). Replaces the hard `if (…) { … }` pop. Springs carry velocity, so toggling
 * mid-way reverses from where it is instead of restarting.
 */
@Composable
fun ColumnScope.GlassReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = RevealEnter,
        exit = RevealExit
    ) {
        content()
    }
}

/** Ink colours every tool screen uses — resolved once here instead of per screen. */
object ToolInk {
    @Composable fun text(): Color = LiquidGlassColors.text(LocalIsDarkMode.current)
    @Composable fun secondary(): Color = LiquidGlassColors.secondary(LocalIsDarkMode.current)
}

/** The rounded accent tile an icon sits in — same treatment as the Tools grid tiles. */
@Composable
fun AccentIconTile(icon: ImageVector, accent: Color, size: Int = 60, iconSize: Int = 30) {
    val isDark = LocalIsDarkMode.current
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(accent.copy(alpha = if (isDark) 0.24f else 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(iconSize.dp), accent)
    }
}

/**
 * The intro card at the top of every tool: accent icon tile, title, centred description, then the
 * primary action(s) in [actions].
 */
@Composable
fun ToolHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AccentIconTile(icon, accent)
        Spacer(Modifier.height(14.dp))
        BasicText(
            title,
            style = TextStyle(ToolInk.text(), 20.sp, FontWeight.SemiBold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            subtitle,
            style = TextStyle(ToolInk.secondary(), 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        )
        if (actions != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = actions
            )
        }
    }
}

/**
 * The selected file, as one compact glass row: accent document tile, name, one line of detail.
 */
@Composable
fun ToolFileCard(
    name: String,
    detail: String?,
    accent: Color,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.PictureAsPdf,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccentIconTile(icon, accent, size = 42, iconSize = 22)
        Column(Modifier.weight(1f)) {
            BasicText(
                name,
                style = TextStyle(ToolInk.text(), 15.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!detail.isNullOrBlank()) {
                BasicText(
                    detail,
                    style = TextStyle(ToolInk.secondary(), 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Primary action: the vivid "Get it" pill in the screen's [accent], white label. [busy] swaps the
 * icon for a spinner; a disabled button keeps its colour at reduced opacity rather than turning
 * into a pale pill under a white label.
 */
@Composable
fun ToolPrimaryButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true
) {
    LiquidButton(
        onClick = { if (enabled && !busy) onClick() },
        backdrop = backdrop,
        tint = accent,
        isInteractive = enabled && !busy,
        modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
            icon != null -> Icon(icon, null, Modifier.size(18.dp), Color.White)
        }
        BasicText(
            text,
            style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold),
            maxLines = 1,
            // Last child of the capsule's Row, so it is measured against whatever width is left
            // and ellipsizes instead of clipping (long pt / es labels). No weight: these pills
            // also live in horizontally scrolling rows, where a weight would collapse to zero.
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Secondary action: clear glass in the title pill's own surface, ink label, optional accent icon.
 */
@Composable
fun ToolSecondaryButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        surfaceColor = LiquidGlassColors.neutralSurface(isDark),
        isInteractive = enabled,
        modifier = modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), if (iconTint == Color.Unspecified) ink else iconTint)
        }
        BasicText(
            text,
            style = TextStyle(ink, 15.sp, FontWeight.Medium),
            maxLines = 1,
            // Last child of the capsule's Row, so it is measured against whatever width is left
            // and ellipsizes instead of clipping (long pt / es labels). No weight: these pills
            // also live in horizontally scrolling rows, where a weight would collapse to zero.
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * One option in a small set (format, position, mode). Selected = vivid accent pill with a white
 * label; unselected = clear glass with an ink label. Same look as the Settings appearance picker.
 */
@Composable
fun GlassChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val isDark = LocalIsDarkMode.current
    val fg = if (selected) Color.White else LiquidGlassColors.text(isDark)
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = if (selected) accent else Color.Unspecified,
        surfaceColor = if (selected) Color.Unspecified else LiquidGlassColors.neutralSurface(isDark),
        horizontalContentPadding = 10.dp,
        modifier = modifier
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), fg)
        BasicText(
            label,
            style = TextStyle(fg, 14.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1,
            // Last child of the capsule's Row, so it is measured against whatever width is left
            // and ellipsizes instead of clipping (long pt / es labels). No weight: these pills
            // also live in horizontally scrolling rows, where a weight would collapse to zero.
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Inline result / error message on glass, with the theme-correct status ink and an optional
 * trailing action (e.g. "Open"). One component instead of the per-screen red/green text panels.
 */
@Composable
fun ToolStatusCard(
    message: String,
    isError: Boolean,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val isDark = LocalIsDarkMode.current
    val tone = if (isError) LiquidGlassColors.danger(isDark) else LiquidGlassColors.success(isDark)
    Row(
        modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = if (isDark) 0.22f else 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                null,
                Modifier.size(19.dp),
                tone
            )
        }
        BasicText(
            message,
            style = TextStyle(LiquidGlassColors.text(isDark), 13.sp, FontWeight.Medium, lineHeight = 18.sp),
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
        if (onDismiss != null) {
            LiquidIconButton(
                onClick = onDismiss,
                backdrop = backdrop,
                modifier = Modifier.size(32.dp)
            ) {
                CloseCrossIcon(Modifier.size(12.dp), LiquidGlassColors.text(isDark))
            }
        }
    }
}

/** The recessed surface behind text inputs on glass (password, page range, watermark text…). */
@Composable
fun Modifier.glassFieldSurface(): Modifier {
    val isDark = LocalIsDarkMode.current
    return this
        .clip(RoundedCornerShape(14.dp))
        .background(if (isDark) Color.White.copy(0.09f) else Color.Black.copy(0.05f))
        .padding(horizontal = 14.dp, vertical = 13.dp)
}

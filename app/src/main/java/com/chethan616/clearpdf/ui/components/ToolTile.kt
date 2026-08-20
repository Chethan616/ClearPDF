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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode

/** Uniform height for the square-ish grid tiles, sized to fit two lines of Portuguese. */
val ToolTileHeight = 120.dp

/**
 * Press feedback shared by every tile: the surface eases down under the finger and springs back on
 * release, with no grey ripple. Same feel as the recents rows on Home.
 */
@Composable
private fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        // Slightly under-damped and a touch stiffer than the rest of the app: release kicks back
        // with a visible bounce. Scoped to the tool tiles on purpose — everything else stays damped.
        spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "toolTilePress"
    )
    return scale
}

/**
 * A flat tool tile. Deliberately **not** a glass surface: it renders inside a single
 * `liquidGlassPanel`, which supplies the refraction for the whole section. Giving each of the 17
 * tools its own blur+lens pass is what made the Tools screen stutter.
 */
@Composable
fun ToolTile(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = !LocalIsDarkMode.current
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    Column(
        modifier
            .height(ToolTileHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = if (isLight) 0.10f else 0.15f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        ToolIconTile(icon, accent, title)
        Spacer(Modifier.weight(1f))
        BasicText(
            title,
            style = TextStyle(
                color = LiquidGlassColors.text(!isLight),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            subtitle,
            style = TextStyle(color = LiquidGlassColors.secondary(!isLight), fontSize = 11.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Full-width variant used for the single most-reached-for action (Open PDF), which sits above the
 * categorised sections rather than inside one.
 */
@Composable
fun ToolTileWide(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = !LocalIsDarkMode.current
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = if (isLight) 0.12f else 0.18f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolIconTile(icon, accent, title)
        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(
                    color = LiquidGlassColors.text(!isLight),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                subtitle,
                style = TextStyle(color = LiquidGlassColors.secondary(!isLight), fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            Modifier.size(20.dp),
            LiquidGlassColors.secondary(!isLight).copy(0.6f)
        )
    }
}

@Composable
private fun ToolIconTile(icon: ImageVector, accent: Color, contentDescription: String) {
    val isLight = !LocalIsDarkMode.current
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = if (isLight) 0.18f else 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, Modifier.size(23.dp), accent)
    }
}

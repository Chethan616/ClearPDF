package com.chethan616.clearpdf.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.clear

/**
 * A screen of its own (a tool, the pages, a signature) standing on the app's wallpaper, as a glass
 * layer: the glass Back, pills and panels on it refract it and follow Settings ▸ Liquid glass & motion.
 */
@Composable
fun AuroraScreen(mood: AuroraMood = AuroraMoods.Tools, content: @Composable () -> Unit) {
    GlassHost(Modifier.fillMaxSize(), paint = { Aurora(mood) }) { content() }
}

/**
 * A worded action (Cancel, Save, Sign, Trust): a glass pill, candy when [primary], faint while it
 * can't be used. It presses and springs as Settings ▸ Liquid glass & motion says.
 */
@Composable
fun GlassTextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, accent: Color = Candy.Sky) {
    val c = clear
    GlassPill(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        tint = if (primary && enabled) accent else Color.Unspecified,
        height = 42.dp,
        horizontalPadding = 16.dp,
        label = label,
    ) {
        androidx.compose.material3.Text(
            label, style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, maxLines = 1,
            color = when {
                primary && enabled -> Color.White
                enabled -> c.ink
                else -> c.inkFaint
            },
        )
    }
}

/** A round choice: a ring that springs into a candy disc with a white dot when chosen. */
@Composable
fun GlassRadio(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = Candy.Sky) {
    val c = clear
    val on by animateFloatAsState(if (selected) 1f else 0f, GlassMotion.morph(), label = "radio")
    Box(modifier.size(36.dp).pressable(onClick, pressedScale = 0.85f, role = Role.RadioButton), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).border(2.dp, c.ink.copy(alpha = 0.4f * (1f - on.coerceIn(0f, 1f))), CircleShape))
        Box(
            Modifier.size(24.dp).graphicsLayer { scaleX = on.coerceAtLeast(0f); scaleY = on.coerceAtLeast(0f) }.clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White)) }
    }
}

/** A round tick for picking things (pages, files): a faint ring, springing into a mint disc with a check. */
@Composable
fun GlassTick(selected: Boolean, modifier: Modifier = Modifier, accent: Color = Candy.Mint) {
    val c = clear
    val on by animateFloatAsState(if (selected) 1f else 0f, GlassMotion.morph(), label = "tick")
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)).border(2.dp, c.ink.copy(alpha = 0.35f), CircleShape))
        Box(
            Modifier.size(22.dp).graphicsLayer { scaleX = on.coerceAtLeast(0f); scaleY = on.coerceAtLeast(0f) }.clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(com.chethan616.clearpdf.ui.LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * Frosted menu material for floating text menus (the selection toolbar's overflow): the chip's lens
 * rim, frosted harder so rows of text stay legible over whatever they float on.
 */
fun Modifier.glassMenu(
    backdrop: Backdrop,
    dark: Boolean,
    shape: () -> Shape = { RoundedRectangle(24.dp) },
    /** Surface opacity; raise it for menus that float over vivid content (context menus). */
    surfaceAlpha: Float = 0.62f
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        glassEffects(GlassSettings.style, 10f.dp.toPx(), 14f.dp.toPx(), 28f.dp.toPx())
    },
    highlight = { glassHighlight(GlassSettings.style) },
    shadow = null,
    onDrawSurface = {
        // Menus carry text: the user's Tint can make them denser, never clearer than legible.
        val a = maxOf(surfaceAlpha, (surfaceAlpha * GlassSettings.style.tint)).coerceAtMost(1f)
        drawRect(if (dark) Color(0xFF1E1E1E).copy(a) else Color(0xFFFAFAFA).copy(a))
    }
)

/** One choice in a [LiquidGlassDropdown]. [supporting] is an optional second line (e.g. a native name). */
@Immutable
data class GlassDropdownOption<T>(
    val value: T,
    val label: String,
    val supporting: String? = null,
    val icon: ImageVector? = null
)

/**
 * The share sheet's liquid-glass dropdown from the Aug 22 build, 1:1: a glass pill trigger (value +
 * chevron that turns on the morph spring) and a liquid-glass panel menu that drops in under it on a
 * `pop()` spring — alpha, a 14 dp drop and a small scaleY grow, all draw-time so the menu's glass never
 * re-blurs while it springs.
 *
 * The menu is drawn in the app's [GlassOverlayLayer] so it overlays the screen instead of growing the
 * panel the trigger sits in (resizing a glass panel re-blurs it every frame). It samples the same
 * [backdrop] as the trigger, exactly as the original sampled its dialog's.
 */
@Composable
fun <T> LiquidGlassDropdown(
    options: List<GlassDropdownOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    accent: Color = LiquidGlassColors.Blue,
    /** Trigger + menu surface (the original's `field`). Defaults to a light theme wash. */
    triggerSurface: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified
) {
    val isDark = LocalIsDarkMode.current
    val fg = if (contentColor.isSpecified) contentColor else LiquidGlassColors.text(isDark)
    val fgSoft = fg.copy(alpha = 0.62f)
    val field = if (triggerSurface.isSpecified) triggerSurface
    else if (isDark) Color(0xFF111318).copy(0.74f) else Color.White.copy(0.55f)
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val current = options.firstOrNull { it.value == selected } ?: options.firstOrNull()
    val chevron by animateFloatAsState(if (open) 180f else 0f, GlassMotion.morph(), label = "dropChevron")

    BackHandler(enabled = open) { open = false }

    LiquidButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            open = !open
        },
        backdrop = backdrop,
        surfaceColor = field,
        modifier = modifier.onGloballyPositioned { anchor = it.boundsInWindow() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp), accent)
                BasicText(
                    current?.label.orEmpty(),
                    style = TextStyle(fg, 14.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                null,
                Modifier.size(20.dp).graphicsLayer { rotationZ = chevron },
                fgSoft
            )
        }
    }

    GlassOverlay {
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize()) {
            // Tap-outside catcher, only while open; swallows the touch so nothing underneath reacts.
            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                open = false
                            }
                        }
                )
            }
            val local = windowToLocal(anchor.topLeft)
            val menuOffset = IntOffset(
                local.x.roundToInt(),
                (local.y + anchor.height).roundToInt() + with(density) { 6.dp.roundToPx() }
            )
            val menuWidth = with(density) { anchor.width.toDp() }
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(110)),
                exit = fadeOut(tween(90)),
                modifier = Modifier.align(Alignment.TopStart).offset { menuOffset }
            ) {
                val uiSensor = rememberUISensor()
                val reveal by transition.animateFloat(
                    transitionSpec = {
                        if (targetState == EnterExitState.Visible) GlassMotion.pop() else GlassMotion.settle()
                    },
                    label = "dropMenuReveal"
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                Column(
                    Modifier
                        .width(menuWidth)
                        .graphicsLayer {
                            alpha = reveal.coerceIn(0f, 1f)
                            translationY = (1f - reveal) * (-14.dp.toPx())
                            scaleY = 0.9f + 0.1f * reveal
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            // Per-draw alpha: an offscreen layer would clip the panel's shadow
                            // during the fade and snap it in at the end (the flicker).
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .liquidGlassPanel(backdrop, uiSensor, field)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEach { opt ->
                        val isSelected = opt.value == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    open = false
                                    if (opt.value != selected) onSelect(opt.value)
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    opt.label,
                                    style = TextStyle(fg, 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (opt.supporting != null) {
                                    BasicText(opt.supporting, style = TextStyle(fgSoft, 11.sp), maxLines = 1)
                                }
                            }
                            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), fg)
                        }
                    }
                }
            }
        }
    }
}

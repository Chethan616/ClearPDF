package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The floating-menu material, shared by every glass menu (this dropdown, the text-selection toolbar's
 * overflow): the chip's vibrancy + lens rim, but frosted harder (10 dp blur, 62% tint) so rows of text
 * stay legible over whatever they float on, with a soft deep shadow to lift it off the content.
 */
fun Modifier.glassMenu(
    backdrop: Backdrop,
    dark: Boolean,
    shape: () -> Shape = { RoundedRectangle(24.dp) }
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(10f.dp.toPx())
        lens(14f.dp.toPx(), 28f.dp.toPx())
    },
    highlight = { Highlight.Default },
    shadow = { Shadow(radius = 28.dp, color = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)) },
    onDrawSurface = { drawRect(if (dark) Color(0xFF1E1E1E).copy(0.62f) else Color(0xFFFAFAFA).copy(0.62f)) }
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
 * The share sheet's bouncy dropdown (commit 248ab19), generalised: a chip-glass trigger showing the
 * current value with a chevron that turns on the morph spring, and a frosted liquid-glass menu that
 * springs open out of it.
 *
 * The menu lives in the app's [GlassOverlayLayer], not under the trigger, so (a) opening it never
 * grows the panel the trigger sits in — resizing a glass panel re-blurs it every frame, which is the
 * original "expand lag" this pattern was invented to avoid — and (b) it refracts the real screen
 * beneath it rather than the wallpaper.
 *
 * Motion: the menu unfolds from the trigger (scale + a short slide, overshooting on a soft spring) and
 * its rows land one after another. Rows support iOS-style press-and-slide: put a finger down, slide
 * across the options (a light tick on each), lift to choose. The trigger's value rolls to the new
 * choice. Back, or a touch anywhere outside, closes it.
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
    /** Trigger surface; defaults to the neutral chip wash used by unselected [GlassChoiceChip]s. */
    triggerSurface: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified
) {
    val isDark = LocalIsDarkMode.current
    val ink = if (contentColor.isSpecified) contentColor else LiquidGlassColors.text(isDark)
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    // Window-space bounds of the trigger, read by the menu at layout time only.
    val anchor = remember { mutableStateOf(Rect.Zero) }
    val current = options.firstOrNull { it.value == selected } ?: options.firstOrNull()
    val chevron by animateFloatAsState(if (open) 180f else 0f, GlassMotion.morph(), label = "dropdownChevron")

    BackHandler(enabled = open) { open = false }

    LiquidButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            open = !open
        },
        backdrop = backdrop,
        surfaceColor = if (triggerSurface.isSpecified) triggerSurface else LiquidGlassColors.neutralSurface(isDark),
        modifier = modifier
            .onGloballyPositioned { anchor.value = it.boundsInWindow() }
            .semantics { role = Role.DropdownList }
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp), accent)
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(GlassMotion.fade()) + slideInVertically(GlassMotion.morph()) { it / 2 }) togetherWith
                    (fadeOut(GlassMotion.fade()) + slideOutVertically(GlassMotion.settle()) { -it / 2 })
            },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "dropdownValue"
        ) { opt ->
            BasicText(
                opt?.label.orEmpty(),
                style = TextStyle(ink, 15.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            null,
            Modifier.size(20.dp).graphicsLayer { rotationZ = chevron },
            ink.copy(alpha = 0.6f)
        )
    }

    GlassOverlay {
        DropdownMenu(
            open = open,
            anchor = { anchor.value },
            options = options,
            selected = selected,
            accent = accent,
            onPick = { value ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                open = false
                if (value != selected) onSelect(value)
            },
            onHover = { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun <T> GlassOverlayScope.DropdownMenu(
    open: Boolean,
    anchor: () -> Rect,
    options: List<GlassDropdownOption<T>>,
    selected: T,
    accent: Color,
    onPick: (T) -> Unit,
    onHover: () -> Unit,
    onDismiss: () -> Unit
) {
    // Open on a soft, underdamped spring (a little overshoot = the "unfold"); close critically damped
    // and fast, so dismissal never wobbles.
    val transition = updateTransition(open, label = "dropdownMenu")
    val reveal by transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.62f, stiffness = 460f)
            else spring(dampingRatio = 1f, stiffness = 900f)
        },
        label = "dropdownReveal"
    ) { if (it) 1f else 0f }
    // Idle cost ~zero: nothing composes while fully closed.
    if (!open && transition.currentState == transition.targetState && reveal <= 0f) return

    val density = LocalDensity.current
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val inkSoft = LiquidGlassColors.secondary(isDark)
    val topSafe = WindowInsets.statusBars.getTop(density) + with(density) { 8.dp.roundToPx() }
    val bottomSafe = WindowInsets.navigationBars.getBottom(density) + with(density) { 8.dp.roundToPx() }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentPick by rememberUpdatedState(onPick)
    val currentHover by rememberUpdatedState(onHover)

    var hovered by remember { mutableIntStateOf(-1) }
    // Row extents in window space, written at layout and read only by the gesture.
    val rowTops = remember(options.size) { FloatArray(options.size) }
    val rowBottoms = remember(options.size) { FloatArray(options.size) }
    val menuOrigin = remember { arrayOf(Offset.Zero) }
    val menuWidth = remember { floatArrayOf(0f) }
    fun indexAt(windowY: Float): Int {
        for (i in options.indices) if (windowY >= rowTops[i] && windowY < rowBottoms[i]) return i
        return -1
    }

    Box(Modifier.fillMaxSize()) {
        if (open) {
            // A touch anywhere outside the menu dismisses it, and is swallowed so nothing underneath
            // (a scroll, a button) reacts to the same touch.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            currentDismiss()
                        }
                    }
            )
        }

        Layout(
            content = {
                Column(
                    Modifier
                        .glassMenu(backdrop, dark = isDark)
                        .onGloballyPositioned {
                            menuOrigin[0] = it.positionInWindow()
                            menuWidth[0] = it.size.width.toFloat()
                        }
                        .pointerInput(options) {
                            // Press-and-slide selection: the row under the finger highlights as it
                            // moves (a tick per row); lifting on a row picks it.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                fun hit(p: Offset): Int =
                                    if (p.x < 0f || p.x > menuWidth[0]) -1 else indexAt(menuOrigin[0].y + p.y)
                                hovered = hit(down.position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        change.consume()
                                        val i = hit(change.position)
                                        if (i >= 0) currentPick(options[i].value)
                                        break
                                    }
                                    change.consume()
                                    val i = hit(change.position)
                                    if (i != hovered) {
                                        if (i >= 0) currentHover()
                                        hovered = i
                                    }
                                }
                                hovered = -1
                            }
                        }
                        .padding(vertical = 6.dp)
                ) {
                    options.forEachIndexed { i, opt ->
                        DropdownRow(
                            option = opt,
                            index = i,
                            isSelected = opt.value == selected,
                            isHovered = hovered == i,
                            ink = ink,
                            inkSoft = inkSoft,
                            accent = accent,
                            onBounds = { top, bottom -> rowTops[i] = top; rowBottoms[i] = bottom },
                            onPickA11y = { currentPick(opt.value) }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val margin = 12.dp.roundToPx()
            val gap = 8.dp.roundToPx()
            val layerW = constraints.maxWidth
            val layerH = constraints.maxHeight
            val a = anchor()
            val aTopLeft = windowToLocal(a.topLeft)
            // Match the trigger's width (never narrower than a comfortable menu, never wider than the
            // screen) — the dropdown reads as the trigger opening, not a separate popup.
            val maxW = (layerW - 2 * margin).coerceAtLeast(0)
            val w = a.width.roundToInt().coerceAtLeast(200.dp.roundToPx()).coerceAtMost(maxW)
            val placeable = measurables.first().measure(
                Constraints.fixedWidth(w).copy(maxHeight = (layerH - topSafe - bottomSafe).coerceAtLeast(0))
            )
            val h = placeable.height
            val belowY = aTopLeft.y + a.height + gap
            val fitsBelow = belowY + h <= layerH - bottomSafe
            val y = if (fitsBelow) belowY else (aTopLeft.y - gap - h).coerceAtLeast(topSafe.toFloat())
            val x = aTopLeft.x.coerceIn(margin.toFloat(), (layerW - margin - w).toFloat().coerceAtLeast(margin.toFloat()))
            val pivotX = if (w > 0) ((aTopLeft.x + a.width / 2f - x) / w).coerceIn(0f, 1f) else 0.5f
            val slide = 10.dp.toPx() * if (fitsBelow) -1f else 1f
            layout(layerW, layerH) {
                placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                    val r = reveal
                    alpha = r.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * r
                    scaleY = 0.8f + 0.2f * r
                    translationY = (1f - r) * slide
                    transformOrigin = TransformOrigin(pivotX, if (fitsBelow) 0f else 1f)
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownRow(
    option: GlassDropdownOption<T>,
    index: Int,
    isSelected: Boolean,
    isHovered: Boolean,
    ink: Color,
    inkSoft: Color,
    accent: Color,
    onBounds: (top: Float, bottom: Float) -> Unit,
    onPickA11y: () -> Unit
) {
    // Rows land one after another (~24 ms apart) as the glass unfolds, like an iOS menu.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(24L + 24L * index.coerceAtMost(8))
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f))
    }
    val press by animateFloatAsState(if (isHovered) 0.97f else 1f, GlassMotion.press(), label = "dropRowPress")
    val wash by animateFloatAsState(if (isHovered) 1f else 0f, GlassMotion.fade(), label = "dropRowWash")
    val slidePx = with(LocalDensity.current) { 6.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
                val b = c.boundsInWindow()
                onBounds(b.top, b.bottom)
            }
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * slidePx
                scaleX = press
                scaleY = press
            }
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind { if (wash > 0f) drawRect(ink.copy(alpha = 0.08f * wash)) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = isSelected
                onClick(label = option.label) { onPickA11y(); true }
            }
            .padding(horizontal = 12.dp, vertical = if (option.supporting != null) 9.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), accent)
        }
        if (option.icon != null) Icon(option.icon, null, Modifier.size(18.dp), if (isSelected) accent else ink)
        Column(Modifier.weight(1f)) {
            BasicText(
                option.label,
                style = TextStyle(ink, 15.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (option.supporting != null) {
                Spacer(Modifier.size(1.dp))
                BasicText(
                    option.supporting,
                    style = TextStyle(inkSoft, 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

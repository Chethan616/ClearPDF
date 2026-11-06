package com.chethan616.clearpdf.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One choice of [GlassTabs]: an icon, a label, or both, and the candy it lights up in. */
data class GlassTab(val label: String, val icon: ImageVector? = null, val accent: Color)

/**
 * A glass capsule of choices with a jelly blob over the chosen one. Drag the blob or tap a choice;
 * the blob is a lens, and under it the choices are drawn in the chosen tab's candy. The dock and the
 * segmented controls are both this.
 */
@Composable
fun GlassTabs(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    textStyle: TextStyle = ClearType.Label,
) {
    val c = clear
    val backdrop = LocalBackdrop.current
    val haptics = LocalHaptics.current
    val count = tabs.size
    val container = c.glass
    val inner = (height - 8.dp).coerceAtLeast(0.dp)
    val accent by animateColorAsState(tabs.getOrNull(selected)?.accent ?: c.ink, GlassMotion.settle(), label = "tab accent")
    val tabsLayer = rememberLayerBackdrop()
    val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / count }
        val offsetAnim = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val f = (offsetAnim.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * f.sign * EaseOut.transform(abs(f)) }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()
        var current by remember { mutableIntStateOf(selected) }
        val drag = remember(scope, count) {
            DampedDragAnimation(
                scope, selected.toFloat(), 0f..(count - 1).toFloat(), 0.001f, 1f, 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    current = target
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnim.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, d ->
                    updateValue((targetValue + d.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (count - 1).toFloat()))
                    scope.launch { offsetAnim.snapTo(offsetAnim.value + d.x) }
                },
            )
        }
        // follow the caller from the first frame (the bar may be rebuilt mid-change), report only our own changes
        LaunchedEffect(selected) { current = selected }
        LaunchedEffect(drag) {
            snapshotFlow { current }.collectLatest { i ->
                if (drag.targetValue.fastRoundToInt() != i) drag.animateToValue(i.toFloat())
                if (i != selectedNow) {
                    haptics?.play(Feel.Tick)
                    select(i)
                }
            }
        }
        val highlight = remember(scope) {
            InteractiveHighlight(scope, position = { size, _ ->
                Offset(
                    if (isLtr) (drag.value + 0.5f) * tabWidth + panelOffset else size.width - (drag.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            })
        }
        val pressScale = { lerp(1f, 1.18f, drag.pressProgress) }

        @Composable
        fun RowScope.Cells(tinted: Boolean) {
            tabs.forEachIndexed { i, t ->
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .then(if (tinted) Modifier else Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { current = i })
                        .semantics { if (!tinted) { this.selected = i == current; contentDescription = t.label; role = Role.Tab } }
                        .graphicsLayer { val s = pressScale(); scaleX = s; scaleY = s },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val ink = c.ink
                    if (t.icon != null) Icon(t.icon, null, tint = ink, modifier = Modifier.size(if (t.label.isEmpty()) 24.dp else 22.dp))
                    if (t.icon != null && t.label.isNotEmpty()) Spacer(Modifier.height(3.dp))
                    if (t.label.isNotEmpty()) Text(t.label, style = textStyle, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule },
                    shadow = null,
                    effects = { glassEffects(GlassSettings.style, 4f.dp.toPx(), 24f.dp.toPx(), 24f.dp.toPx()) },
                    highlight = { glassHighlight(GlassSettings.style) },
                    layerBlock = {
                        val s = if (size.width <= 0f) 1f else lerp(1f, 1f + 16f.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { drawRect(container.glassTint(GlassSettings.style)) },
                )
                .then(highlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Cells(tinted = false) }

        // the same choices in the chosen candy, recorded (not shown) for the blob's lens to reveal
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsLayer)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule },
                    shadow = null,
                    effects = {
                        val p = drag.pressProgress
                        val style = GlassSettings.style
                        vibrancy()
                        blur(8f.dp.toPx() * style.blur)
                        lens(24f.dp.toPx() * p * style.refractionHeight, 24f.dp.toPx() * p * style.refractionAmount)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress * GlassSettings.style.highlight.coerceIn(0f, 1f)) },
                    onDrawSurface = { drawRect(container) },
                )
                .height(inner)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
            verticalAlignment = Alignment.CenterVertically,
        ) { Cells(tinted = true) }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) drag.value * tabWidth + panelOffset else size.width - (drag.value + 1f) * tabWidth + panelOffset
                }
                .then(highlight.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsLayer),
                    shape = { Capsule },
                    effects = {
                        val p = drag.pressProgress
                        val style = GlassSettings.style
                        lens(10f.dp.toPx() * p * style.refractionHeight, 14f.dp.toPx() * p * style.refractionAmount, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress * GlassSettings.style.highlight.coerceIn(0f, 1f)) },
                    shadow = null,
                    innerShadow = {
                        val p = drag.pressProgress
                        InnerShadow(radius = 8f.dp * p, alpha = p)
                    },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val v = drag.velocity / 10f
                        scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val p = drag.pressProgress
                        drawRect(if (c.dark) Color.White.copy(0.08f) else Color.White.copy(0.14f), alpha = 1f - p)
                        drawRect(accent.copy(alpha = if (c.dark) 0.20f else 0.12f), alpha = 1f - p)
                        drawRect(Color.White.copy(alpha = 0.04f * p))
                    },
                )
                .height(inner)
                .fillMaxWidth(1f / count),
        )
    }
}

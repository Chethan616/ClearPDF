package com.chethan616.clearpdf.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.drawBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A round glass button that morphs into its menu, as the old app's share button morphs into its
 * capsule: the glass itself grows from the circle at [anchor] (its window bounds) into the card, its
 * rim and lens following the shape the whole way, on one glass spring (Settings ▸ Motion, damped
 * enough that a resizing glass doesn't ring on); the [glyph] cross-fades into the rows on a fade that
 * never bounces, and the rows' icons pop in with it. Dismissed, it shrinks back into the circle; an
 * item chosen fades it where it is. Keep the real button hidden from the tap until [onGone].
 */
@Composable
fun GlassMorphMenu(
    open: Boolean,
    anchor: Rect,
    onDismiss: () -> Unit,
    onGone: () -> Unit,
    items: List<GlassMenuItem>,
    width: Dp = 236.dp,
    glyph: @Composable () -> Unit,
) {
    val c = clear
    GlassOverlay {
        BackHandler(enabled = open, onBack = onDismiss)
        val host = LocalGlassOverlayHost.current ?: return@GlassOverlay
        val density = LocalDensity.current
        // 0 = the button, 1 = the menu: the glass's width and height, in layout only
        val grow = remember { Animatable(0f) }
        // the glyph out and the rows in, on the same beat as the glass but never past either end
        val shown = remember { Animatable(0f) }
        val pop = remember { Animatable(0f) }
        val fade = remember { Animatable(1f) }
        var chosen by remember { mutableStateOf(false) }
        val gone by rememberUpdatedState(onGone)
        LaunchedEffect(open) {
            coroutineScope {
                when {
                    open -> {
                        launch { shown.animateTo(1f, ContentIn) }
                        launch { pop.animateTo(1f, GlassMotion.pop()) }
                        grow.animateTo(1f, morphSpring())
                    }
                    chosen -> fade.animateTo(0f, Fluid.fade())
                    else -> {
                        launch { shown.animateTo(0f, ContentOut) }
                        launch { pop.animateTo(0f, ContentOut) }
                        grow.animateTo(0f, morphSpring())
                    }
                }
            }
            if (!open) gone()
        }
        if (open) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        val local = Rect(host.windowToLocal(anchor.topLeft), host.windowToLocal(anchor.bottomRight))
        val right = local.center.x > host.size.width / 2f
        val corner = if (right) Alignment.TopEnd else Alignment.TopStart
        val button = with(density) { local.height.toDp() }
        // half the button's height: a circle at the button's size, the menu's corners at full size
        val shape = RoundedRectangle(button / 2)
        val style = GlassSettings.style
        val backdrop = LocalBackdrop.current
        var fullHeight by remember { mutableFloatStateOf(0f) }
        Layout(
            content = {
                Box(
                    Modifier
                        .graphicsLayer { alpha = fade.value }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            shadow = null,
                            effects = { glassEffects(style, 4f.dp.toPx(), 12f.dp.toPx(), 24f.dp.toPx()) },
                            highlight = { glassHighlight(style) },
                            onDrawSurface = {
                                // the button's glass, frosting over as it becomes a menu so the rows read
                                drawRect(c.glass.glassTint(style))
                                drawRect(c.frost.glassTint(style), alpha = shown.value.coerceIn(0f, 1f) * 0.9f)
                            },
                        )
                        .clip(shape),
                ) {
                    Column(
                        Modifier
                            .wrapContentSize(corner, unbounded = true)
                            .width(width)
                            .onSizeChanged { fullHeight = it.height.toFloat() }
                            .graphicsLayer { alpha = ((shown.value - 0.3f) / 0.7f).coerceIn(0f, 1f) },
                    ) {
                        Spacer(Modifier.height(6.dp))
                        items.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .pressable({ if (open) { chosen = true; onDismiss(); item.onClick() } })
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.label, style = ClearType.BodyStrong, color = if (item.destructive) Candy.Tomato else c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.icon != null) {
                                    Icon(
                                        item.icon, null,
                                        tint = if (item.destructive) Candy.Tomato else c.inkSoft,
                                        modifier = Modifier.size(20.dp).graphicsLayer {
                                            val s = pop.value.coerceAtLeast(0f)
                                            scaleX = s
                                            scaleY = s
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Box(
                        Modifier.align(corner).size(button).graphicsLayer { alpha = (1f - shown.value * 2.2f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.Center,
                    ) { glyph() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val fullW = width.toPx()
            val fullH = if (fullHeight > 0f) fullHeight else local.height
            val g = grow.value
            // past full size by what the spring throws; never smaller than most of the button
            val w = lerp(local.width, fullW, g).coerceAtLeast(local.width * 0.8f)
            val h = lerp(local.height, fullH, g).coerceAtLeast(local.height * 0.8f)
            val card = measurables.first().measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = if (right) local.right - w else local.left
                // grows down from the button, or up when there is no room below
                val y = if (local.top + fullH < constraints.maxHeight) local.top else local.bottom - h
                card.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/** The glass growing or shrinking: the motion setting's spring, with a floor on damping (a resizing glass re-blurs every frame). */
private fun morphSpring(): SpringSpec<Float> =
    spring(maxOf(MotionSettings.damping(0.75f), 0.62f), MotionSettings.stiffness(480f), visibilityThreshold = 0.002f)

private val ContentIn = spring<Float>(dampingRatio = 1f, stiffness = 380f)
private val ContentOut = spring<Float>(dampingRatio = 1f, stiffness = 900f)

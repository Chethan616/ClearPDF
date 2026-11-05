package com.chethan616.clearpdf.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * What a glass surface refracts: everything drawn beneath it. Provided by the layer below, never
 * passed by hand, so no surface can sample the wallpaper when something else is actually behind it.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * Draws [content] (recording it), then [above] over it. Glass in [above] refracts the content and
 * everything beneath this layer; glass inside [content] refracts only what is beneath the layer.
 *
 * Use it wherever glass floats over something: the app over its wallpaper, the dock over the
 * screens, a viewer's bars over its pages, a glass chip over a picture.
 */
@Composable
fun GlassLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    above: @Composable BoxScope.() -> Unit,
) {
    val below = LocalBackdrop.current
    val layer = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(below, layer)
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(layer), content = content)
        CompositionLocalProvider(LocalBackdrop provides combined) { Box(Modifier.matchParentSize(), content = above) }
    }
}

/** A mood of the aurora: where its blobs sit, as fractions of the screen, and how big they are. */
data class AuroraMood(val blobs: List<Offset>, val radius: Float = 0.75f)

object AuroraMoods {
    val Desk = AuroraMood(listOf(Offset(0.05f, 0.08f), Offset(0.95f, 0.02f), Offset(0.0f, 0.62f), Offset(1.0f, 0.78f), Offset(0.5f, 1.05f)))
    val Files = AuroraMood(listOf(Offset(0.9f, 0.1f), Offset(0.1f, 0.0f), Offset(1.0f, 0.55f), Offset(0.0f, 0.9f), Offset(0.45f, 0.4f)), 0.7f)
    val Tools = AuroraMood(listOf(Offset(0.5f, -0.05f), Offset(0.0f, 0.3f), Offset(0.95f, 0.45f), Offset(0.15f, 1.0f), Offset(0.9f, 1.0f)))
    val Settings = AuroraMood(listOf(Offset(0.0f, 0.0f), Offset(1.0f, 0.15f), Offset(0.2f, 0.75f), Offset(0.85f, 1.0f), Offset(0.6f, 0.45f)), 0.65f)
}

private val OffsetPairConverter = TwoWayConverter<Pair<Offset, Offset>, AnimationVector4D>(
    { (a, b) -> AnimationVector4D(a.x, a.y, b.x, b.y) },
    { v -> Offset(v.v1, v.v2) to Offset(v.v3, v.v4) },
)

/**
 * The wallpaper: the canvas colour under soft candy blobs. Static while idle (no infinite animation);
 * a new [mood] glides the blobs to new places on one spring, so each tab has its own light.
 */
@Composable
fun Aurora(mood: AuroraMood, modifier: Modifier = Modifier) {
    val colors = clear
    val anims = remember { List(5) { i -> Animatable(mood.blobs[i % mood.blobs.size] to Offset(mood.radius, 0f), OffsetPairConverter) } }
    LaunchedEffect(mood) {
        mood.blobs.forEachIndexed { i, p ->
            val target = p to Offset(mood.radius, 0f)
            if (anims[i].targetValue != target) launch { anims[i].animateTo(target, GlassMotion.rise()) }
        }
    }
    Box(
        modifier.fillMaxSize().drawBehind {
            drawRect(colors.canvas)
            val r = size.maxDimension
            colors.aurora.forEachIndexed { i, (c, strength) ->
                val (p, rad) = anims.getOrNull(i)?.value ?: return@forEachIndexed
                if (!(r * rad.x > 1f)) return@forEachIndexed
                val center = Offset(p.x * size.width, p.y * size.height)
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = strength), c.copy(alpha = strength * 0.4f), Color.Transparent), center, r * rad.x),
                    radius = r * rad.x, center = center,
                )
            }
        },
    )
}

/**
 * An in-window portal for floating glass (menus, sheets, dialogs, toasts). Glass can't refract the
 * layer it is drawn in, so anything that floats is composed here, above every screen and the dock,
 * where [LocalBackdrop] is the whole live screen.
 */
@Stable
class GlassOverlayHost {
    internal val entries = mutableStateListOf<OverlayEntry>()
    internal var originInWindow by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
        internal set

    internal fun register(entry: OverlayEntry) {
        entries.removeAll { it.key === entry.key }
        entries.add(entry)
    }

    internal fun unregister(key: Any) {
        entries.removeAll { it.key === key }
    }

    /** A window point in the overlay's space. Read at layout or draw time. */
    fun windowToLocal(point: Offset): Offset = point - originInWindow
}

internal class OverlayEntry(val key: Any, val content: @Composable BoxScope.() -> Unit)

val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

@Composable
fun GlassOverlayLayer(host: GlassOverlayHost, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().onGloballyPositioned {
            host.originInWindow = it.positionInWindow()
            host.size = it.size
        },
    ) {
        host.entries.forEach { entry -> key(entry.key) { entry.content(this) } }
    }
}

/** Shows [content] in the overlay for as long as the caller is composed. Draw nothing while idle. */
@Composable
fun GlassOverlay(content: @Composable BoxScope.() -> Unit) {
    val host = LocalGlassOverlayHost.current ?: return
    val latest by rememberUpdatedState(content)
    val key = remember { Any() }
    DisposableEffect(host, key) {
        host.register(OverlayEntry(key) { latest() })
        onDispose { host.unregister(key) }
    }
}

/**
 * The app's glass stack, bottom to top: the [wallpaper]; the [screens]; the [chrome] that floats over
 * them (the dock); then the overlay portal over all three. Provide [LocalGlassOverlayHost] above it
 * so overlays composed outside the stage (app-level dialogs and sheets) land in the same portal.
 */
@Composable
fun GlassStage(
    wallpaper: @Composable BoxScope.() -> Unit,
    screens: @Composable BoxScope.() -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
) {
    val provided = LocalGlassOverlayHost.current
    val host = provided ?: remember { GlassOverlayHost() }
    val wall = rememberLayerBackdrop()
    CompositionLocalProvider(LocalGlassOverlayHost provides host) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(wall), content = wallpaper)
            CompositionLocalProvider(LocalBackdrop provides wall) {
                GlassLayer(Modifier.fillMaxSize(), content = screens) {
                    GlassLayer(Modifier.fillMaxSize(), content = chrome) {
                        GlassOverlayLayer(host)
                    }
                }
            }
        }
    }
}

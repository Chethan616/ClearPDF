package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop

/**
 * An in-window portal for floating glass: dropdown menus, contextual action bars.
 *
 * Why it exists: glass can only refract a layer it is *not* part of. A menu opened from inside a
 * screen's scrolling content lives inside the layer that content records into, so it could only ever
 * sample the wallpaper — over the default flat background that reads as a grey card, not glass. The
 * floating tab bar solves the same problem by being a sibling of that layer at the app root; this
 * gives any component, however deep, the same position. Content registered here is composed in
 * [GlassOverlayLayer], drawn above every screen (and the tab bar), and handed the app's live-screen
 * backdrop, so a menu really bends whatever is under it.
 *
 * Entries take window coordinates (e.g. `boundsInWindow()` of a trigger) and convert with
 * [GlassOverlayScope.windowToLocal].
 */
@Stable
class GlassOverlayHost {
    internal val entries = mutableStateListOf<OverlayEntry>()
    internal var originInWindow by mutableStateOf(Offset.Zero)
    internal var layerSize by mutableStateOf(IntSize.Zero)

    internal fun register(entry: OverlayEntry) {
        entries.removeAll { it.key === entry.key }
        entries.add(entry)
    }

    internal fun unregister(key: Any) {
        entries.removeAll { it.key === key }
    }
}

internal class OverlayEntry(val key: Any, val content: @Composable GlassOverlayScope.() -> Unit)

@Stable
class GlassOverlayScope internal constructor(
    /** The live screen (wallpaper + everything drawn by the app) — what overlay glass refracts. */
    val backdrop: Backdrop,
    private val host: GlassOverlayHost
) {
    /** A window-space point in this layer's local space. Read in layout/draw, not composition. */
    fun windowToLocal(point: Offset): Offset = point - host.originInWindow

    /** The overlay layer's size in px — the area floating content may occupy. */
    val layerSize: IntSize get() = host.layerSize
}

val LocalGlassOverlayHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/**
 * Renders everything registered with [host]. Place it as a late child of the app's root Box, OUTSIDE
 * the layer that records into [backdrop] (the same rule as the tab bar), so overlay glass can sample
 * the whole screen without sampling itself.
 */
@Composable
fun GlassOverlayLayer(host: GlassOverlayHost, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val scope = remember(host, backdrop) { GlassOverlayScope(backdrop, host) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                host.originInWindow = it.positionInWindow()
                host.layerSize = it.size
            }
    ) {
        host.entries.forEach { entry ->
            key(entry.key) { entry.content(scope) }
        }
    }
}

/**
 * Hosts [content] in the app's [GlassOverlayLayer] for as long as the calling composable is in
 * composition. No-op where no host is provided. [content] should draw nothing while it has nothing to
 * show — it stays registered, so the cost of an idle entry must be ~zero.
 */
@Composable
fun GlassOverlay(content: @Composable GlassOverlayScope.() -> Unit) {
    val host = LocalGlassOverlayHost.current ?: return
    val latest by rememberUpdatedState(content)
    val key = remember { Any() }
    DisposableEffect(host, key) {
        host.register(OverlayEntry(key) { latest(this) })
        onDispose { host.unregister(key) }
    }
}

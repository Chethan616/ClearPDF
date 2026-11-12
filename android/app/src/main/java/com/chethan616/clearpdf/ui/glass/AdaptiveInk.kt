package com.chethan616.clearpdf.ui.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Per-control ink for the document viewers. Each icon reads what is really on screen around it
 * and picks dark or white ink for itself, so two buttons in the same bar can differ (one over a
 * white page, one over the dark desk beside it, a photo, book mode's letterbox).
 *
 * How: while at least one control asks, the window is copied, scaled down, a few times a second,
 * and only after it actually drew something new. A control samples the corners of its own box,
 * which sit outside its round or capsule glass, so its own ink never feeds back into the reading.
 * Before Android 8 (no window PixelCopy) every control keeps the ink its screen gives it.
 */
@Stable
class ScreenInkSampler internal constructor() {
    internal var frame by mutableStateOf<InkFrame?>(null)
    internal var users by mutableIntStateOf(0)

    @RequiresApi(Build.VERSION_CODES.O)
    internal suspend fun run(window: Window, view: View) {
        val dirty = AtomicBoolean(true)
        val listener = ViewTreeObserver.OnDrawListener { dirty.set(true) }
        view.viewTreeObserver.addOnDrawListener(listener)
        val handler = Handler(Looper.getMainLooper())
        var bitmap: Bitmap? = null
        try {
            while (true) {
                if (users == 0) {
                    snapshotFlow { users > 0 }.first { it }
                    dirty.set(true)
                }
                val decor = window.decorView
                val w = decor.width
                val h = decor.height
                if (w > 0 && h > 0 && dirty.getAndSet(false)) {
                    val bw = (w / SCALE_DOWN).coerceAtLeast(1)
                    val bh = (h / SCALE_DOWN).coerceAtLeast(1)
                    val target = bitmap?.takeIf { it.width == bw && it.height == bh && !it.isRecycled }
                        ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bitmap = it }
                    val ok = runCatching {
                        suspendCancellableCoroutine { cont ->
                            PixelCopy.request(window, target, { result -> if (cont.isActive) cont.resume(result) }, handler)
                        } == PixelCopy.SUCCESS
                    }.getOrDefault(false)
                    if (ok) {
                        val px = IntArray(bw * bh)
                        target.getPixels(px, 0, bw, 0, 0, bw, bh)
                        frame = InkFrame(px, bw, bh, bw / w.toFloat(), bh / h.toFloat())
                    }
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        } finally {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
            bitmap?.recycle()
        }
    }

    private companion object {
        const val SCALE_DOWN = 6
        const val SAMPLE_INTERVAL_MS = 140L
    }
}

/** A scaled-down copy of the window; coordinates in and out are window pixels. */
internal class InkFrame(
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int,
    private val scaleX: Float,
    private val scaleY: Float
) {
    private fun luminanceAt(x: Float, y: Float): Double {
        val px = (x * scaleX).toInt().coerceIn(0, width - 1)
        val py = (y * scaleY).toInt().coerceIn(0, height - 1)
        val c = pixels[py * width + px]
        return (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
    }

    /**
     * Luminance just outside [box], [gap] pixels clear of it on every side and corner: what the
     * glass sits on, with none of the glass itself in the reading. (Read at its own rim, a control
     * saw its own light: it brightened, read brighter, brightened again, and so breathed on a page
     * that never changed.)
     */
    fun outsideLuminance(box: Rect, gap: Float): Float {
        val cx = box.center.x
        val cy = box.center.y
        val l = box.left - gap
        val t = box.top - gap
        val r = box.right + gap
        val b = box.bottom + gap
        val sum = luminanceAt(l, cy) + luminanceAt(r, cy) + luminanceAt(cx, t) + luminanceAt(cx, b) +
            luminanceAt(l, t) + luminanceAt(r, t) + luminanceAt(l, b) + luminanceAt(r, b)
        return (sum / 8.0).toFloat()
    }

    /** The whole window's luminance, from a spread of its pixels. */
    fun average(): Float {
        var sum = 0.0
        var n = 0
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
            n++
            i += 37
        }
        return if (n == 0) 0.5f else (sum / n).toFloat()
    }

    /**
     * Luminance around [box]: just inside its four corners (outside the rounded glass), plus, at
     * double weight, just outside its left and right edges level with its centre, where the icon
     * itself sits. A control straddling a page edge so follows what is beside its glyph.
     */
    fun cornerLuminance(box: Rect, inset: Float): Float {
        val d = inset.coerceAtMost(minOf(box.width, box.height) * 0.14f)
        val corners = luminanceAt(box.left + d, box.top + d) + luminanceAt(box.right - d, box.top + d) +
            luminanceAt(box.left + d, box.bottom - d) + luminanceAt(box.right - d, box.bottom - d)
        val cy = box.center.y
        val sides = luminanceAt(box.left - d * 0.6f, cy) + luminanceAt(box.right + d * 0.6f, cy)
        return ((corners + 2 * sides) / 8.0).toFloat()
    }
}

val LocalScreenInk = staticCompositionLocalOf<ScreenInkSampler?> { null }

/**
 * Runs one [ScreenInkSampler] for this window (Android 8+); the controls under it read from it. One
 * already running above is used as it is (the app runs one for every screen while Adaptive luminance
 * is on; the viewers always ask for one). [enabled] false provides none, without changing what is composed.
 */
@Composable
fun ProvideScreenInk(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        content()
        return
    }
    val above = LocalScreenInk.current
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    val sampler = remember { ScreenInkSampler() }
    val runs = enabled && above == null && window != null
    if (runs && window != null) LaunchedEffect(window, view) { sampler.run(window, view) }
    CompositionLocalProvider(LocalScreenInk provides (above ?: if (runs) sampler else null), content = content)
}

/**
 * How light the screen is around one glass control, from the window's sampler: what the control's
 * glass adapts to while Adaptive luminance is on. One copy of the window serves every control, so a
 * screenful of glass buttons costs no more than one.
 */
@Stable
class SurroundLuminance internal constructor(initial: Float) {
    internal val anim = androidx.compose.animation.core.Animatable(initial)
    internal val box = arrayOfNulls<Rect>(1)

    /** 0 (dark around) … 1 (light around). Read while drawing. */
    val value: Float get() = anim.value

    /** Put on the control (first, so it measures the resting box). */
    val modifier: Modifier = Modifier.onGloballyPositioned { box[0] = it.boundsInWindow() }
}

/** The luminance around the control being composed, or null while Adaptive luminance is off. */
@Composable
fun rememberSurroundLuminance(): SurroundLuminance? {
    val sampler = LocalScreenInk.current
    if (!GlassSettings.adaptive || sampler == null) return null
    val dark = com.chethan616.clearpdf.ui.theme.clear.dark
    val state = remember { SurroundLuminance(sampler.frame?.average() ?: if (dark) 0.14f else 0.86f) }
    val gap = with(LocalDensity.current) { 7.dp.toPx() }
    val overlays = LocalGlassOverlayHost.current
    DisposableEffect(sampler) {
        sampler.users++
        onDispose { sampler.users-- }
    }
    LaunchedEffect(sampler) {
        var first = true
        snapshotFlow { sampler.frame }.collect { frame ->
            val b = state.box[0]
            if (frame == null || b == null || b.width <= 0f || b.height <= 0f) return@collect
            // a menu or sheet over the screen is not the screen: the glass under it holds as it was
            if (!first && overlays != null && overlays.entries.isNotEmpty()) return@collect
            val lum = frame.outsideLuminance(b, gap)
            if (first) {
                state.anim.snapTo(lum)
                first = false
            } else if (kotlin.math.abs(lum - state.anim.targetValue) > 0.1f) {
                // only a real change of what it is over moves it: a neighbour's glint does not
                launch { state.anim.animateTo(lum, tween(360)) }
            }
        }
    }
    return state
}

/** One control's ink: put [modifier] on the control (first, so it measures the resting box). */
class InkProbe(val modifier: Modifier, val color: Color) {
    val soft: Color get() = color.copy(alpha = 0.62f)
}

/** Ink over a light background. */
val InkOnLight = Color(0xFF15171C)

/**
 * Ink for one control, from the pixels around it. [fallback] is the ink the screen would use for
 * the whole bar: it applies until the first reading and on Android 7 and older, and when the
 * reading agrees with it, it is kept as is (so a screen's own dark ink stays its exact colour).
 */
@Composable
fun rememberAdaptiveInk(fallback: Color): InkProbe {
    val sampler = LocalScreenInk.current
    val fallbackOnLight = fallback.luminance() < 0.5f
    var onLight by remember { mutableStateOf(fallbackOnLight) }
    var measured by remember { mutableStateOf(false) }
    val box = remember { arrayOfNulls<Rect>(1) }
    val currentFallback by rememberUpdatedState(fallbackOnLight)
    val inset = with(LocalDensity.current) { 5.dp.toPx() }
    val overlays = LocalGlassOverlayHost.current
    if (sampler != null) {
        DisposableEffect(sampler) {
            sampler.users++
            onDispose { sampler.users-- }
        }
        LaunchedEffect(sampler) {
            snapshotFlow { sampler.frame }.collect { frame ->
                val b = box[0]
                if (frame == null || b == null || b.width <= 0f || b.height <= 0f) return@collect
                // a menu lying over a button is not what the button stands on: its ink holds
                if (measured && overlays != null && overlays.entries.isNotEmpty()) return@collect
                val lum = frame.cornerLuminance(b, inset)
                // Hysteresis, so a control over a mid-grey picture doesn't flicker.
                onLight = when {
                    !measured -> lum > 0.60f
                    lum > 0.62f -> true
                    lum < 0.58f -> false
                    else -> onLight
                }
                measured = true
            }
        }
    }
    val useLight = if (sampler != null && measured) onLight else currentFallback
    val target = when {
        useLight == fallbackOnLight -> fallback
        useLight -> InkOnLight
        else -> Color.White
    }
    val color by animateColorAsState(target, tween(200), label = "adaptiveInk")
    val modifier = remember { Modifier.onGloballyPositioned { box[0] = it.boundsInWindow() } }
    return InkProbe(modifier, color)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * [rememberAdaptiveInk] where a [ScreenInkSampler] runs (the viewer), else [fallback] as it is: the
 * app's own screens keep their theme ink, glass over a document reads its ink from the page.
 */
@Composable
fun rememberGlassInk(fallback: Color): InkProbe =
    if (LocalScreenInk.current != null) rememberAdaptiveInk(fallback) else remember(fallback) { InkProbe(Modifier, fallback) }

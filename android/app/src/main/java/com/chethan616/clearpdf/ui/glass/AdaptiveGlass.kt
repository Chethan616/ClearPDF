package com.chethan616.clearpdf.ui.glass

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sign

/**
 * Adaptive luminance: a glass surface that reads how light or dark the things behind it are and
 * answers them, as AndroidLiquidGlass's "Adaptive luminance glass" does (Kyant0, Apache-2.0; the
 * library our backdrop module is built on). Over a bright page the glass turns milky and bright and
 * takes dark ink; over a dark one it deepens and takes white ink. So a menu reads the same over a
 * white PDF, a black slide or the wallpaper, whatever the app's theme.
 *
 * How: the surface records the backdrop it draws a second time, very small, into [layer]; a few times
 * a second that record is read back and averaged into one luminance, which glides to its new value.
 * The glass's effects read [value] while drawing, so nothing recomposes as it moves; only [onLight]
 * (which ink) is composition state, and it flips with hysteresis so mid-grey never flickers.
 */
@Stable
class GlassLuminance internal constructor(initial: Float, internal val layer: GraphicsLayer) {
    private val anim = Animatable(initial)
    private var recorded = false

    /** 0 (black behind) … 1 (white behind). Read while drawing. */
    val value: Float get() = anim.value

    /** Whether the glass sits over something light, so its ink should be dark. */
    var onLight by mutableStateOf(initial > 0.5f)
        private set

    /** True once the backdrop has really been read (until then [value] is the theme's guess). */
    var measured by mutableStateOf(false)
        private set

    /** Draws the backdrop, and a small copy of it into [layer] for the reading. */
    internal val recorder: DrawScope.(DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
        drawBackdrop()
        val w = (size.width / DOWN).toInt().coerceAtLeast(1)
        val h = (size.height / DOWN).toInt().coerceAtLeast(1)
        layer.record(IntSize(w, h)) {
            scale(1f / DOWN, 1f / DOWN, pivot = Offset.Zero) { drawBackdrop() }
        }
        recorded = true
    }

    internal suspend fun run() {
        val px = IntArray(SAMPLES * SAMPLES)
        var waited = 0
        while (true) {
            if (!recorded) {
                // nothing drawn to read yet: don't hold the surface back for ever
                if (!measured && ++waited > 12) measured = true
                delay(16)
                continue
            }
            val lum = withContext(Dispatchers.Default) {
                runCatching {
                    val full = layer.toImageBitmap().asAndroidBitmap()
                    // a hardware bitmap can't be read: a small software copy can
                    val small = Bitmap.createScaledBitmap(full, SAMPLES, SAMPLES, true).copy(Bitmap.Config.ARGB_8888, false)
                    small.getPixels(px, 0, SAMPLES, 0, 0, SAMPLES, SAMPLES)
                    var sum = 0.0
                    var n = 0
                    for (c in px) {
                        // what was not drawn (outside every layer) says nothing
                        if ((c ushr 24) < 8) continue
                        sum += (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
                        n++
                    }
                    if (n == 0) null else (sum / n).toFloat()
                }.getOrNull()
            }
            if (lum == null && !measured && ++waited > 12) measured = true
            if (lum != null) {
                onLight = when {
                    !measured -> lum > 0.5f
                    lum > 0.56f -> true
                    lum < 0.44f -> false
                    else -> onLight
                }
                if (!measured) {
                    // The first reading is taken as it is, at once. The surface is held unseen until
                    // it is in (see [MenuCard]), so a menu opened over a black page is dark from its
                    // first visible frame: it used to open in the theme's guess (white in the light
                    // theme) and then swing to black.
                    anim.snapTo(lum)
                    measured = true
                } else if (kotlin.math.abs(lum - anim.targetValue) > 0.07f) {
                    // only a real change behind it moves it (a small one, read again and again, made it breathe)
                    anim.animateTo(lum, tween(420))
                }
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private companion object {
        const val DOWN = 8f
        const val SAMPLES = 6
        const val SAMPLE_INTERVAL_MS = 220L
    }
}

/**
 * A luminance reading for one glass surface, or null when [enabled] is false. Pass it to [glass] (or
 * [GlassCard]) and take the ink from [adaptiveInk].
 */
@Composable
fun rememberGlassLuminance(enabled: Boolean = GlassSettings.adaptive): GlassLuminance? {
    if (!enabled) return null
    val layer = rememberGraphicsLayer()
    val dark = clear.dark
    // the first guess: what the screen as a whole looks like now (the window's sampler, running
    // while Adaptive luminance is on), else the theme
    val screen = LocalScreenInk.current
    val state = remember(layer) { GlassLuminance(screen?.frame?.average() ?: if (dark) 0.14f else 0.86f, layer) }
    LaunchedEffect(state) { state.run() }
    return state
}

/**
 * The ink of the menu or popover being composed (unspecified outside one): its rows and glyphs take
 * it, so they follow the glass when it adapts to what is behind it.
 */
val LocalMenuInk = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/** Dark ink over light glass, white over dark, gliding between them; [fallback] when there is no reading. */
@Composable
fun adaptiveInk(luminance: GlassLuminance?, fallback: Color): Color {
    if (luminance == null) return fallback
    val target = if (luminance.onLight) InkOnLight else Color.White
    val ink by animateColorAsState(target, tween(220), label = "adaptiveGlassInk")
    return ink
}

/** [luminance] as -1 (dark) … 1 (light), eased so mid-greys stay near neutral. */
private fun signed(luminance: Float): Float = (luminance * 2f - 1f).let { sign(it) * it * it }

/**
 * The adaptive effect stack (after Kyant0's demo): brighter and flatter over light, deeper over dark,
 * the frost growing with the light (a bright busy page needs more of it than a dark one), then the
 * user's lens.
 */
fun BackdropEffectScope.adaptiveGlassEffects(
    style: GlassStyle,
    luminance: Float,
    blurPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
) {
    val l = signed(luminance.coerceIn(0f, 1f))
    colorControls(
        brightness = (if (l > 0f) lerp(0.08f, 0.42f, l) else lerp(0.08f, -0.2f, -l)) + style.brightness,
        contrast = if (l > 0f) lerp(1f, 0.25f, l) else 1f,
        saturation = style.saturation,
    )
    blur(blurPx * style.blur * (if (l > 0f) lerp(1f, 1.5f, l) else lerp(1f, 0.6f, -l)))
    lens(
        refractionHeightPx * style.refractionHeight,
        refractionAmountPx * style.refractionAmount,
        depthEffect = style.depthEffect,
        chromaticAberration = style.chromaticAberration,
    )
}

/**
 * The user's glass with its filters set aside: while Adaptive luminance is on, frost, colour,
 * brightness and tint are the luminance's to set, so the presets' values for them are not applied
 * (the lens and rim light, which are shape, stay).
 */
val GlassStyle.adaptiveBase: GlassStyle get() = copy(blur = 1f, saturation = 1.5f, brightness = 0f, tint = 1f)

/** The thin wash over adaptive glass: milk over light, smoke over dark. */
fun adaptiveWash(luminance: Float): Color {
    val l = signed(luminance.coerceIn(0f, 1f))
    return if (l >= 0f) Color.White.copy(alpha = lerp(0.10f, 0.30f, l)) else Color.Black.copy(alpha = lerp(0.10f, 0.34f, -l))
}

package com.chethan616.clearpdf.ui.components

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/**
 * The user's liquid-glass tuning — the same knobs kyant's Glass Playground exposes (blur, refraction
 * height + amount, depth, chromatic aberration, corner radius) plus the colour stack (vibrancy,
 * brightness, tint) and the specular rim. Every value is a MULTIPLIER on each component's own design
 * value (except saturation/brightness, which are absolute), so a panel keeps its heavier frost
 * relative to a chip whatever the user picks. 1 = the designed look.
 */
@Immutable
data class GlassStyle(
    val blur: Float = 1f,
    val refractionHeight: Float = 1f,
    val refractionAmount: Float = 1f,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
    val saturation: Float = 1.5f,
    val brightness: Float = 0f,
    val tint: Float = 1f,
    val highlight: Float = 1f,
    val corners: Float = 1f
) {
    companion object {
        val Default = GlassStyle()
        /** Heavier frost + denser tint: text stays legible over white PDFs. */
        val Readable = GlassStyle(blur = 2.4f, tint = 1.8f, refractionAmount = 0.7f, saturation = 1.3f)
        /** Clear, strongly bent glass with a prismatic edge — the showpiece. */
        val Crystal = GlassStyle(blur = 0.3f, refractionHeight = 1.5f, refractionAmount = 1.6f, depthEffect = true, chromaticAberration = true, tint = 0.6f, saturation = 1.7f)
        /** No blur or lens passes at all — the cheapest glass, for slower phones. */
        val Performance = GlassStyle(blur = 0f, refractionHeight = 0f, refractionAmount = 0f, tint = 1.4f)
    }
}

/**
 * App-wide glass settings, held in snapshot state so a change re-draws every glass surface live, and
 * persisted. Also hosts the haptics switch (same "how the UI feels" family).
 */
object GlassSettings {
    private const val PREFS = "ui_prefs"

    var style: GlassStyle by mutableStateOf(GlassStyle.Default)
    var hapticsEnabled: Boolean by mutableStateOf(true)

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = GlassStyle.Default
        style = GlassStyle(
            blur = p.getFloat("blur", d.blur),
            refractionHeight = p.getFloat("refractionHeight", d.refractionHeight),
            refractionAmount = p.getFloat("refractionAmount", d.refractionAmount),
            depthEffect = p.getBoolean("depthEffect", d.depthEffect),
            chromaticAberration = p.getBoolean("chromaticAberration", d.chromaticAberration),
            saturation = p.getFloat("saturation", d.saturation),
            brightness = p.getFloat("brightness", d.brightness),
            tint = p.getFloat("tint", d.tint),
            highlight = p.getFloat("highlight", d.highlight),
            corners = p.getFloat("corners", d.corners)
        )
        hapticsEnabled = p.getBoolean("haptics", true)
    }

    fun update(context: Context, s: GlassStyle) {
        style = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("blur", s.blur)
            .putFloat("refractionHeight", s.refractionHeight)
            .putFloat("refractionAmount", s.refractionAmount)
            .putBoolean("depthEffect", s.depthEffect)
            .putBoolean("chromaticAberration", s.chromaticAberration)
            .putFloat("saturation", s.saturation)
            .putFloat("brightness", s.brightness)
            .putFloat("tint", s.tint)
            .putFloat("highlight", s.highlight)
            .putFloat("corners", s.corners)
            .apply()
    }

    fun setHaptics(context: Context, enabled: Boolean) {
        hapticsEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("haptics", enabled).apply()
    }
}

/**
 * The shared glass effect stack, scaled by [style]: colour (vibrancy or the user's saturation /
 * brightness), frost, then the lens. Components pass their DESIGNED pixel values.
 */
fun BackdropEffectScope.glassEffects(
    style: GlassStyle,
    blurPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    depthEffect: Boolean = false
) {
    if (style.saturation == 1.5f && style.brightness == 0f) vibrancy()
    else colorControls(brightness = style.brightness, saturation = style.saturation)
    blur(blurPx * style.blur)
    lens(
        refractionHeightPx * style.refractionHeight,
        refractionAmountPx * style.refractionAmount,
        depthEffect = depthEffect || style.depthEffect,
        chromaticAberration = style.chromaticAberration
    )
}

/** A component's surface wash, scaled by the user's tint. */
fun Color.glassTint(style: GlassStyle): Color =
    if (style.tint == 1f) this else copy(alpha = (alpha * style.tint).coerceIn(0f, 1f))

/** The default specular rim at the user's strength. */
fun glassHighlight(style: GlassStyle, base: Highlight = Highlight.Default): Highlight? =
    if (style.highlight <= 0f) null else base.copy(alpha = style.highlight.coerceIn(0f, 1f))

/** As [glassHighlight] for the tilt-following rim panels use. */
fun glassHighlight(style: GlassStyle, angle: Float): Highlight? =
    glassHighlight(style, Highlight(style = HighlightStyle.Default(angle = angle)))

/** The colour stack alone, for the flat-wallpaper fast path (see FlatBackdrop). */
internal fun glassColorFilter(style: GlassStyle): ColorFilter {
    if (style.saturation == 1.5f && style.brightness == 0f) return VibrancyColorFilter
    val s = style.saturation
    val r = 0.213f * (1f - s)
    val g = 0.715f * (1f - s)
    val b = 0.072f * (1f - s)
    val o = style.brightness * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r + s, g, b, 0f, o,
                r, g + s, b, 0f, o,
                r, g, b + s, 0f, o,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

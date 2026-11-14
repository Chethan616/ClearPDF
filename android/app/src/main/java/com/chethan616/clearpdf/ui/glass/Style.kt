package com.chethan616.clearpdf.ui.glass

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * The user's glass tuning. Each value multiplies a component's designed value (saturation and
 * brightness are absolute), so a sheet keeps its heavier frost relative to a button. 1 = the design.
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
) {
    companion object {
        val Default = GlassStyle()
        /** Heavier frost and wash: text stays legible over busy pages. */
        val Frosted = GlassStyle(blur = 2.4f, tint = 1.8f, refractionAmount = 0.7f, saturation = 1.3f)
        /**
         * A menu's own frost, independent of the user's glass preset (a Lite or Crystal menu would not
         * read). Lighter than it was (blur ×3 hid everything behind): what is under the menu now shows
         * through as soft shapes, as a sheet of frosted glass lets it.
         */
        val MenuFrost = GlassStyle(blur = 1.5f, refractionHeight = 0f, refractionAmount = 0f, tint = 1f, saturation = 1.2f, brightness = 0.08f)

        /**
         * The same in the dark theme: what is behind is dimmed a little, so white ink still reads when
         * a dark menu opens over a white page.
         */
        val MenuFrostDark = MenuFrost.copy(brightness = -0.3f)
        /** Clear, strongly bent glass with a prismatic rim. */
        val Crystal = GlassStyle(blur = 0.3f, refractionHeight = 1.5f, refractionAmount = 1.6f, depthEffect = true, chromaticAberration = true, tint = 0.6f, saturation = 1.7f)
        /** No blur or lens at all, for slower phones. */
        val Lite = GlassStyle(blur = 0f, refractionHeight = 0f, refractionAmount = 0f, tint = 1.4f)
        val Presets = listOf(Default, Frosted, Crystal, Lite)
    }
}

/**
 * What the app stands on: Artefact's soft aurora (the default, as the old ClearPDF started on its
 * own plain background), the old ClearPDF's liquid blobs, or a picture of the user's own.
 */
enum class Wallpaper { Aurora, Liquid, Custom }

object GlassSettings {
    private const val PREFS = "glass"
    private const val CUSTOM_FILE = "wallpaper_custom.jpg"

    var wallpaper: Wallpaper by mutableStateOf(Wallpaper.Aurora)

    /** The user's own picture, decoded once (null until one is chosen). */
    var customWallpaper: androidx.compose.ui.graphics.ImageBitmap? by mutableStateOf(null)
        private set

    fun updateWallpaper(context: Context, w: Wallpaper) {
        wallpaper = w
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("wallpaper", w.name).apply()
    }

    /**
     * Copies a picture from the gallery into the app (upright, at most 1600 px on its long side, so it
     * stays after the picker's permission lapses) and makes it the wallpaper. Off the main thread.
     */
    suspend fun setCustomWallpaper(context: Context, uri: android.net.Uri): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, 1600f / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            java.io.File(context.filesDir, CUSTOM_FILE).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            val image = bitmap.asImageBitmapCompat()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                customWallpaper = image
                updateWallpaper(context, Wallpaper.Custom)
            }
            true
        }.getOrDefault(false)
    }

    private fun android.graphics.Bitmap.asImageBitmapCompat() = asImageBitmap()

    private fun loadCustom(context: Context) {
        val f = java.io.File(context.filesDir, CUSTOM_FILE)
        customWallpaper = if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmapCompat() else null
    }

    var style: GlassStyle by mutableStateOf(GlassStyle.Default)
    var haptics: Boolean by mutableStateOf(true)

    /**
     * Adaptive luminance (Settings ▸ Liquid glass): menus, popovers and toasts read how light what is
     * behind them is, and turn light with dark ink or dark with white ink to suit (see [GlassLuminance]).
     */
    var adaptive: Boolean by mutableStateOf(false)
        private set

    fun updateAdaptive(context: Context, on: Boolean) {
        adaptive = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("adaptive", on).apply()
    }

    fun load(context: Context) {
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
        )
        haptics = p.getBoolean("haptics", true)
        adaptive = p.getBoolean("adaptive", false)
        wallpaper = runCatching { Wallpaper.valueOf(p.getString("wallpaper", null) ?: "Aurora") }.getOrDefault(Wallpaper.Aurora)
        if (wallpaper == Wallpaper.Custom) loadCustom(context)
        if (wallpaper == Wallpaper.Custom && customWallpaper == null) wallpaper = Wallpaper.Aurora
        MotionSettings.load(context)
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
            .apply()
    }

    fun updateHaptics(context: Context, on: Boolean) {
        haptics = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("haptics", on).apply()
    }
}

/** The one effect stack every glass surface uses: colour (vibrancy), frost, then the lens. Pass designed px. */
fun BackdropEffectScope.glassEffects(
    style: GlassStyle,
    blurPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    depthEffect: Boolean = false,
) {
    if (style.saturation == 1.5f && style.brightness == 0f) vibrancy()
    else colorControls(brightness = style.brightness, saturation = style.saturation)
    blur(blurPx * style.blur)
    lens(
        refractionHeightPx * style.refractionHeight,
        refractionAmountPx * style.refractionAmount,
        depthEffect = depthEffect || style.depthEffect,
        chromaticAberration = style.chromaticAberration,
    )
}

/** A surface wash at the user's tint. */
fun Color.glassTint(style: GlassStyle): Color =
    if (style.tint == 1f) this else copy(alpha = (alpha * style.tint).coerceIn(0f, 1f))

/** The specular rim at the user's strength. */
fun glassHighlight(style: GlassStyle, base: Highlight = Highlight.Default): Highlight? =
    if (style.highlight <= 0f) null else base.copy(alpha = style.highlight.coerceIn(0f, 1f))

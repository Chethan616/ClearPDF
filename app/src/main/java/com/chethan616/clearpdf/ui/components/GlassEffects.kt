package com.chethan616.clearpdf.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GlassEdge { Top, Bottom }

private const val ProgressiveBlurShader = """
    uniform shader content;
    uniform float2 size;
    uniform float fromBottom;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;

    half4 main(float2 coord) {
        float y = fromBottom > 0.5 ? size.y - coord.y : coord.y;
        float a = smoothstep(size.y, size.y * 0.5, y);
        return mix(content.eval(coord) * a, tint * a, tintIntensity);
    }"""

/**
 * Alpha-masked progressive blur (catalog ProgressiveBlurContent): the backdrop behind this element is
 * blurred and tinted, fully opaque on the [edge] side and fading to nothing across the far half.
 * Size the element yourself (e.g. `fillMaxWidth().height(statusBar + 72.dp)` behind a top bar).
 *
 * Needs API 33 (RuntimeShader). Below that it falls back to a plain tint gradient, since an unmasked
 * blur would leave a hard edge.
 */
@Composable
fun Modifier.progressiveBlurEdge(
    backdrop: Backdrop,
    edge: GlassEdge = GlassEdge.Top,
    blurRadius: Dp = 4.dp,
    tint: Color = if (LocalIsDarkMode.current) Color(0xFF101114) else Color.White,
    tintIntensity: Float = 0.6f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.drawBehind {
            val stops = arrayOf(0f to tint.copy(alpha = tintIntensity), 0.5f to tint.copy(alpha = tintIntensity), 1f to Color.Transparent)
            drawRect(
                if (edge == GlassEdge.Top) Brush.verticalGradient(*stops)
                else Brush.verticalGradient(*stops, startY = size.height, endY = 0f)
            )
        }
    }
    return this.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            blur(blurRadius.toPx())
            runtimeShaderEffect("ClearPdfProgressiveBlur", ProgressiveBlurShader, "content") {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("fromBottom", if (edge == GlassEdge.Bottom) 1f else 0f)
                setColorUniform("tint", tint.toArgb())
                setFloatUniform("tintIntensity", tintIntensity)
            }
        }
    )
}

/**
 * Adaptive-luminance content colour (catalog AdaptiveLuminanceGlassContent). Pass [backdrop] (the
 * wrapped one) to your glass element's `drawBackdrop` instead of the original: every time it draws,
 * the backdrop pixels under the glass are recorded and periodically averaged (5x5 downsample).
 * [contentColor] eases to black over bright content and white over dark; [luminance] is 0..1.
 */
@Stable
class AdaptiveGlassContent internal constructor(
    val backdrop: Backdrop,
    private val colorAnim: Animatable<Color, *>,
    private val lumAnim: Animatable<Float, *>
) {
    val contentColor: Color get() = colorAnim.value
    val luminance: Float get() = lumAnim.value
}

@Composable
fun rememberAdaptiveGlassContentColor(
    backdrop: Backdrop,
    sampleIntervalMs: Long = 500L
): AdaptiveGlassContent {
    val isDark = LocalIsDarkMode.current
    val layer: GraphicsLayer = rememberGraphicsLayer()
    val colorAnim = remember(isDark) { Animatable(if (isDark) Color.White else Color.Black) }
    val lumAnim = remember(isDark) { Animatable(if (isDark) 0f else 1f) }
    val onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) -> Unit =
        remember(layer) {
            { drawBackdrop ->
                drawBackdrop()
                layer.record { drawBackdrop() }
            }
        }
    val wrapped = rememberBackdrop(backdrop, onDraw)
    LaunchedEffect(layer, colorAnim, lumAnim) {
        val buffer = IntArray(25)
        while (isActive) {
            delay(sampleIntervalMs)
            val avg = try {
                if (layer.size.width <= 0 || layer.size.height <= 0) continue
                val src = layer.toImageBitmap().asAndroidBitmap()
                val isHw = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE
                val soft = if (isHw) src.copy(Bitmap.Config.ARGB_8888, false) else src
                val thumb = Bitmap.createScaledBitmap(soft, 5, 5, true)
                thumb.getPixels(buffer, 0, 5, 0, 0, 5, 5)
                if (thumb !== soft) thumb.recycle()
                if (soft !== src) soft.recycle()
                buffer.sumOf { argb ->
                    val r = (argb shr 16 and 0xFF) / 255.0
                    val g = (argb shr 8 and 0xFF) / 255.0
                    val b = (argb and 0xFF) / 255.0
                    0.2126 * r + 0.7152 * g + 0.0722 * b
                }.toFloat() / buffer.size
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                continue
            }
            launch { colorAnim.animateTo(if (avg > 0.5f) Color.Black else Color.White, tween(600)) }
            launch { lumAnim.animateTo(avg, tween(600)) }
        }
    }
    return remember(wrapped, colorAnim, lumAnim) { AdaptiveGlassContent(wrapped, colorAnim, lumAnim) }
}

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



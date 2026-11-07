package com.chethan616.clearpdf.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.clear

/**
 * The one loader: Material 3 Expressive's morphing indicator (its shapes turn into one another)
 * inside a glass circle that grows in. Every wait in the app shows this.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassLoader(modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = Candy.Sky, label: String? = null) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Fluid.quick()) }
    Box(
        modifier
            .graphicsLayer {
                val s = 0.8f + 0.2f * appear.value
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .size(size)
            .glass(CircleShape, clear.frost, blur = 12.dp)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.size(size * 0.78f), color = color, polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons)
    }
}

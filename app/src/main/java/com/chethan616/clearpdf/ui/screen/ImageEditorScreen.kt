package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel.ImgFilter
import com.kyant.backdrop.backdrops.LayerBackdrop

/** Real image editor: rotate, colour filters, brightness/contrast — applied live and saved to the
 *  gallery at full resolution. Operates on the original image, not a converted PDF. */
@Composable
fun ImageEditorScreen(
    backdrop: LayerBackdrop,
    viewModel: ImageEditorViewModel,
    onBack: () -> Unit,
    onOpenPdf: (android.net.Uri) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val accent = Color(0xFF8E5AF2)
    val uiSensor = rememberUISensor()

    state.savedMessage?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); viewModel.dismissMessage() }
    }

    GlassScreenScaffold(
        backdrop = backdrop,
        contentHorizontalPadding = 12.dp,
        headerHorizontalPadding = 12.dp,
        headerGap = 10.dp,
        header = { headerBackdrop ->
            GlassScreenHeaderRow(
                title = state.fileName.ifBlank { stringResource(R.string.image_editor_title) },
                backdrop = headerBackdrop,
                onBack = onBack
            )
        }
    ) { contentPadding ->
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
            }
            state.error != null || state.preview == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText(state.error ?: "Couldn't open this image.", style = TextStyle(sub, 14.sp))
            }
            else -> {
                val bmp = state.preview!!
                val matrix = remember(state.filter, state.brightness, state.contrast) {
                    ColorMatrix(ImageEditorViewModel.colorMatrixFor(state.filter, state.brightness, state.contrast))
                }
                // Live preview
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF15181E) else Color(0xFFEDEEF0)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(matrix),
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }

                // Controls panel
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().liquidGlassPanel(backdrop, uiSensor).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rotate + reset
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidIconButton(onClick = { viewModel.rotate(-90) }, backdrop = backdrop, surfaceColor = accent.copy(0.16f), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.RotateLeft, stringResource(R.string.image_rotate_left), Modifier.size(20.dp), accent)
                        }
                        LiquidIconButton(onClick = { viewModel.rotate(90) }, backdrop = backdrop, surfaceColor = accent.copy(0.16f), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.RotateRight, stringResource(R.string.image_rotate_right), Modifier.size(20.dp), accent)
                        }
                        Box(Modifier.weight(1f))
                        LiquidIconButton(onClick = { viewModel.reset() }, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Restore, stringResource(R.string.image_reset_edits), Modifier.size(20.dp), sub)
                        }
                    }

                    // Filter chips
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImgFilter.entries.forEach { f ->
                            val selected = state.filter == f
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) accent.copy(0.9f) else (if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f)))
                                    .clickable { viewModel.setFilter(f) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                BasicText(filterLabel(f), style = TextStyle(if (selected) Color.White else text, 12.sp, FontWeight.Medium))
                            }
                        }
                    }

                    // Brightness
                    AdjustSlider(stringResource(R.string.image_brightness), state.brightness, -100f..100f, sub, text, accent) { viewModel.setBrightness(it) }
                    // Contrast
                    AdjustSlider(stringResource(R.string.image_contrast), state.contrast, 0.5f..2f, sub, text, accent) { viewModel.setContrast(it) }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidButton(onClick = { viewModel.exportToPdf(context) { uri -> uri?.let(onOpenPdf) } }, backdrop = backdrop, surfaceColor = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f), modifier = Modifier.weight(1f)) {
                            BasicText(stringResource(R.string.image_export_pdf), style = TextStyle(text, 14.sp, FontWeight.Medium), modifier = Modifier.padding(vertical = 6.dp))
                        }
                        LiquidButton(onClick = { viewModel.saveToGallery(context) }, backdrop = backdrop, tint = accent, modifier = Modifier.weight(1.3f)) {
                            BasicText(
                                state.savedMessage ?: stringResource(R.string.image_save_gallery),
                                style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AdjustSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    sub: Color, text: Color, accent: Color, onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = TextStyle(sub, 12.sp, FontWeight.Medium))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = sub.copy(0.3f)),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun filterLabel(f: ImgFilter): String = stringResource(
    when (f) {
        ImgFilter.None -> R.string.image_filter_none
        ImgFilter.Mono -> R.string.image_filter_mono
        ImgFilter.Sepia -> R.string.image_filter_sepia
        ImgFilter.Vivid -> R.string.image_filter_vivid
        ImgFilter.Cool -> R.string.image_filter_cool
        ImgFilter.Warm -> R.string.image_filter_warm
    }
)

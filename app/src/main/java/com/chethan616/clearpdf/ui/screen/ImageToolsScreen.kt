package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassChip
import com.chethan616.clearpdf.ui.components.GlassSectionHeader
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ImageToolsViewModel
import com.kyant.pdfcore.raster.PdfRasterizer.ImageFormat
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

private val ImageAccent = Color(0xFFF4511E)

@Composable
fun ImageToolsScreen(
    backdrop: LayerBackdrop,
    viewModel: ImageToolsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = LocalIsDarkMode.current
    val isLight = !isDark
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (state.errorMessage != null) { delay(3500); viewModel.clearFeedback() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onSelectImage(context, uri) }

    ToolScaffold(
        title = stringResource(R.string.tool_image_tools),
        backdrop = backdrop,
        onBack = onBack
    ) {
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.Image, null, Modifier.size(56.dp), ImageAccent)
            BasicText(stringResource(R.string.tool_image_tools), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(stringResource(R.string.tool_image_tools_sub), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
            LiquidButton(onClick = { imagePicker.launch("image/*") }, backdrop = backdrop, tint = ImageAccent) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.image_tools_pick), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        if (state.sourceUri != null) {
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassSectionHeader(title = stringResource(R.string.image_tools_options), icon = Icons.Rounded.Tune, iconTint = ImageAccent, titleColor = text)
                BasicText(
                    "${state.sourceName}  ·  ${state.srcWidth}×${state.srcHeight}  ·  ${state.srcSizeBytes / 1024} KB",
                    style = TextStyle(sub, 13.sp), maxLines = 1
                )

                // Format
                BasicText(stringResource(R.string.image_tools_format), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormatOption("JPG", state.format == ImageFormat.JPEG, backdrop, isLight, { viewModel.onFormatChange(ImageFormat.JPEG) }, Modifier.weight(1f))
                    FormatOption("PNG", state.format == ImageFormat.PNG, backdrop, isLight, { viewModel.onFormatChange(ImageFormat.PNG) }, Modifier.weight(1f))
                    FormatOption("WebP", state.format == ImageFormat.WEBP, backdrop, isLight, { viewModel.onFormatChange(ImageFormat.WEBP) }, Modifier.weight(1f))
                }

                // Quality (lossy formats only)
                if (state.format != ImageFormat.PNG) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(stringResource(R.string.image_tools_quality), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                        GlassChip("${state.quality}%", ImageAccent)
                    }
                    LiquidSlider(
                        value = { state.quality.toFloat() },
                        onValueChange = { viewModel.onQualityChange(it.toInt()) },
                        valueRange = 30f..100f, visibilityThreshold = 0.5f,
                        backdrop = backdrop, modifier = Modifier.fillMaxWidth()
                    )
                }

                // Resize
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(stringResource(R.string.image_tools_resize), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                    GlassChip("${state.scalePercent}%", ImageAccent)
                }
                LiquidSlider(
                    value = { state.scalePercent.toFloat() },
                    onValueChange = { viewModel.onScaleChange(it.toInt()) },
                    valueRange = 10f..100f, visibilityThreshold = 0.5f,
                    backdrop = backdrop, modifier = Modifier.fillMaxWidth()
                )

                BasicText(stringResource(R.string.image_tools_note), style = TextStyle(sub.copy(0.8f), 11.sp))
            }

            LiquidButton(
                onClick = { if (!state.isProcessing) viewModel.process(context) },
                backdrop = backdrop, tint = ImageAccent, modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    if (state.isProcessing) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), Color.White)
                    BasicText(
                        stringResource(if (state.isProcessing) R.string.image_tools_processing else R.string.image_tools_process),
                        style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium), maxLines = 1
                    )
                }
            }

            state.result?.let {
                Column(
                    Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.resultMessage?.let { m -> BasicText(m, style = TextStyle(text, 14.sp, fontWeight = FontWeight.Medium)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidButton(onClick = { viewModel.saveToGallery(context) }, backdrop = backdrop, tint = ImageAccent, modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp), Color.White)
                                BasicText(stringResource(R.string.image_tools_save), style = TextStyle(Color.White, 14.sp, FontWeight.Medium), maxLines = 1)
                            }
                        }
                        LiquidButton(onClick = { viewModel.share(context) }, backdrop = backdrop, tint = Color(0xFF1976D2), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(16.dp), Color.White)
                                BasicText(stringResource(R.string.image_tools_share), style = TextStyle(Color.White, 14.sp, FontWeight.Medium), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            LiquidGlassErrorCard(message = state.errorMessage!!, backdrop = backdrop, uiSensor = uiSensor, onDismiss = { viewModel.clearFeedback() })
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FormatOption(
    label: String,
    selected: Boolean,
    backdrop: LayerBackdrop,
    isLight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) Color.White else (if (isLight) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = if (selected) ImageAccent else Color.Transparent,
        surfaceColor = if (selected) ImageAccent.copy(0.18f) else (if (isLight) Color.White.copy(0.70f) else Color.White.copy(0.10f)),
        modifier = modifier
    ) {
        BasicText(
            label,
            style = TextStyle(contentColor, 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

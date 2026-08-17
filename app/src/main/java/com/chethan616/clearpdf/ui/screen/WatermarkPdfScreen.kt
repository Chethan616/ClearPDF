package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.WatermarkMode
import com.chethan616.clearpdf.ui.viewmodel.WatermarkPdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

private val WatermarkAccent = Color(0xFFAD1457)

@Composable
fun WatermarkPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: WatermarkPdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = LocalIsDarkMode.current
    val isLight = !isDark
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (state.resultMessage != null || state.errorMessage != null) {
            delay(3500); viewModel.clearFeedback()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onSelectFile(context, uri) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onPickImage(context, uri) }

    ToolScaffold(
        title = stringResource(R.string.tool_watermark),
        backdrop = backdrop,
        onBack = onBack
    ) {
        // Intro / pick card
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.BrandingWatermark, null, Modifier.size(56.dp), WatermarkAccent)
            BasicText(stringResource(R.string.tool_watermark), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(stringResource(R.string.tool_watermark_sub), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
            LiquidButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }, backdrop = backdrop, tint = WatermarkAccent) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        if (state.sourceUri != null) {
            // File name
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicText(state.sourceName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }

            // Options card
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassSectionHeader(
                    title = stringResource(R.string.watermark_text_label),
                    icon = Icons.Rounded.BrandingWatermark,
                    iconTint = WatermarkAccent,
                    titleColor = text
                )

                // Mode: Text / Image
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WmModeOption(stringResource(R.string.watermark_mode_text), state.mode == WatermarkMode.TEXT, backdrop, isLight, { viewModel.onModeChange(WatermarkMode.TEXT) }, Modifier.weight(1f))
                    WmModeOption(stringResource(R.string.watermark_mode_image), state.mode == WatermarkMode.IMAGE, backdrop, isLight, { viewModel.onModeChange(WatermarkMode.IMAGE) }, Modifier.weight(1f))
                }

                if (state.mode == WatermarkMode.TEXT) {
                    BasicTextField(
                        value = state.text,
                        onValueChange = viewModel::onTextChange,
                        singleLine = true,
                        textStyle = TextStyle(text, 15.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            if (state.text.isEmpty()) BasicText(stringResource(R.string.watermark_text_hint), style = TextStyle(sub, 14.sp))
                            inner()
                        }
                    )
                } else {
                    LiquidButton(onClick = { imagePicker.launch("image/*") }, backdrop = backdrop, tint = WatermarkAccent, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Rounded.Image, null, Modifier.size(18.dp), Color.White)
                            BasicText(stringResource(R.string.watermark_pick_image), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    if (state.imageName.isNotEmpty()) BasicText(state.imageName, style = TextStyle(sub, 12.sp), maxLines = 1)
                }

                // Opacity
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(stringResource(R.string.watermark_opacity), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                    GlassChip("${(state.opacity * 100).toInt()}%", WatermarkAccent)
                }
                LiquidSlider(
                    value = { state.opacity },
                    onValueChange = viewModel::onOpacityChange,
                    valueRange = 0.05f..1f,
                    visibilityThreshold = 0.005f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )

                // Diagonal toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicText(stringResource(R.string.watermark_diagonal), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                    LiquidToggle(selected = { state.diagonal }, onSelect = viewModel::onDiagonalChange, backdrop = backdrop)
                }
            }

            // Apply
            LiquidButton(
                onClick = { if (!state.isProcessing) viewModel.apply(context) },
                backdrop = backdrop, tint = WatermarkAccent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.BrandingWatermark, null, Modifier.size(18.dp), Color.White)
                    }
                    BasicText(
                        stringResource(if (state.isProcessing) R.string.watermark_applying else R.string.watermark_apply),
                        style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }

        if (state.errorMessage != null) {
            LiquidGlassErrorCard(
                message = state.errorMessage!!,
                backdrop = backdrop,
                uiSensor = uiSensor,
                onDismiss = { viewModel.clearFeedback() }
            )
        }

        state.lastOutputUri?.let { outUri ->
            LiquidButton(
                onClick = { onViewOutput(outUri) },
                backdrop = backdrop, tint = Color(0xFF1976D2),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(stringResource(R.string.viewer_open_pdf), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold), modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun WmModeOption(
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
        tint = if (selected) WatermarkAccent else Color.Transparent,
        surfaceColor = if (selected) WatermarkAccent.copy(0.18f) else (if (isLight) Color.White.copy(0.70f) else Color.White.copy(0.10f)),
        modifier = modifier
    ) {
        BasicText(
            label,
            style = TextStyle(contentColor, 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

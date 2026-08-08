package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CompressPdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

@Composable
fun CompressPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CompressPdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF388E3C)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (!state.resultMessage.isNullOrBlank() || !state.errorMessage.isNullOrBlank()) {
            delay(3000)
            viewModel.clearFeedback()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onSelectFile(context, uri)
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "compressTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "compressTopBarOffsetY"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "compressContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "compressContentOffsetY"
    )

    Box(Modifier.fillMaxSize()) {
        // ── Scrollable Content (scrolls underneath floating nav bar) ──
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                }
                .verticalScroll(rememberScrollState())
                .padding(top = 76.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Rounded.Compress, null, Modifier.size(56.dp), accent)
                BasicText("Reduce File Size", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                BasicText("Compress your PDF while preserving quality", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

                LiquidButton(
                    onClick = { filePicker.launch(arrayOf("application/pdf")) },
                    backdrop = backdrop, tint = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                        BasicText("Select PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }

            if (state.sourceFileName.isNotEmpty()) {
                // File info
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText(state.sourceFileName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                    BasicText("Size: ${state.originalSizeBytes / 1024} KB", style = TextStyle(sub, 13.sp))
                }

                // Un-nested slider control for maximum performance (no glass card lag)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.HighQuality, null, Modifier.size(22.dp), accent)
                            BasicText("Compression Quality", style = TextStyle(text, 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            BasicText(
                                "${(state.qualitySlider * 100).toInt()}%",
                                style = TextStyle(accent, 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    LiquidSlider(
                        value = { state.qualitySlider },
                        onValueChange = { v ->
                            viewModel.onQualitySliderChanged(v)
                        },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.005f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText("Smaller file", style = TextStyle(sub, 12.sp))
                        BasicText("Better quality", style = TextStyle(sub, 12.sp))
                    }

                    if (state.estimatedSizeBytes > 0) {
                        val currentKb = state.originalSizeBytes / 1024
                        val estimateKb = state.estimatedSizeBytes / 1024
                        BasicText(
                            "Estimated output  $currentKb KB  ->  about $estimateKb KB",
                            style = TextStyle(sub, 12.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }

                // Compress button
                LiquidButton(
                    onClick = { viewModel.onCompress(context) },
                    backdrop = backdrop, tint = accent,
                    isInteractive = !state.isCompressing
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isCompressing) {
                            CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Compress, null, Modifier.size(18.dp), Color.White)
                        }
                        BasicText(
                            if (state.isCompressing) "Compressing..." else "Compress Now",
                            style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard(
                    message = state.errorMessage!!,
                    backdrop = backdrop,
                    uiSensor = uiSensor,
                    onDismiss = { viewModel.clearFeedback() }
                )
            }

            state.lastOutputUri?.let { outputUri ->
                LiquidButton(
                    onClick = { onViewOutput(outputUri) },
                    backdrop = backdrop,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicText("View PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.SemiBold))
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // ── Floating Liquid Glass Top Bar ──
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    alpha = topBarAlpha
                    translationY = topBarOffsetY * density
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "Compress PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }
    }
}

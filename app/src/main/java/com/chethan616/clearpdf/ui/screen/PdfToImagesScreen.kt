package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PdfToImagesViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.pdfcore.raster.PdfRasterizer
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToImagesScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfToImagesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = LocalIsDarkMode.current
    val isLight = !isDark
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF00ACC1)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val density = LocalDensity.current.density

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (!state.resultMessage.isNullOrBlank() || !state.errorMessage.isNullOrBlank()) {
            delay(3200); viewModel.clearFeedback()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSelectFile(context, uri)
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val topAlpha by animateFloatAsState(if (isVisible) 1f else 0f, tween(500, easing = FastOutSlowInEasing), label = "t")
    val topY by animateFloatAsState(if (isVisible) 0f else 16f, tween(500, easing = FastOutSlowInEasing), label = "ty")
    val bodyAlpha by animateFloatAsState(if (isVisible) 1f else 0f, tween(600, 100, FastOutSlowInEasing), label = "b")
    val bodyY by animateFloatAsState(if (isVisible) 0f else 24f, tween(600, 100, FastOutSlowInEasing), label = "by")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.graphicsLayer { alpha = topAlpha; translationY = topY * density },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidIconButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, stringResourceSafe(), Modifier.size(16.dp), text)
            }
            LiquidGlassTopBar(title = androidx.compose.ui.res.stringResource(R.string.tool_pdf_to_images), backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().graphicsLayer { alpha = bodyAlpha; translationY = bodyY * density },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Rounded.Image, null, Modifier.size(52.dp), accent)
                BasicText(androidx.compose.ui.res.stringResource(R.string.tool_pdf_to_images), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                BasicText(androidx.compose.ui.res.stringResource(R.string.tool_pdf_to_images_desc), style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center))

                LiquidButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }, backdrop = backdrop, tint = accent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                        BasicText(androidx.compose.ui.res.stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }

            if (state.sourceName.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(state.sourceName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    BasicText(androidx.compose.ui.res.stringResource(R.string.create_pages, state.pageCount), style = TextStyle(sub, 13.sp))
                }

                // Format selector
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(androidx.compose.ui.res.stringResource(R.string.pdf_to_images_format), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PdfRasterizer.ImageFormat.entries.forEach { fmt ->
                            val selected = state.format == fmt
                            LiquidButton(
                                onClick = { viewModel.onFormatChange(fmt) },
                                backdrop = backdrop,
                                tint = if (selected) accent else Color.Transparent,
                                surfaceColor = if (selected) accent else Color.White.copy(0.08f),
                                modifier = Modifier.weight(1f)
                            ) {
                                BasicText(fmt.extension.uppercase(), style = TextStyle(if (selected) Color.White else text, 13.sp, FontWeight.Medium))
                            }
                        }
                    }
                }

                val runLabel = if (state.isProcessing)
                    androidx.compose.ui.res.stringResource(R.string.pdf_to_images_working, (state.progress * 100).toInt())
                else androidx.compose.ui.res.stringResource(R.string.pdf_to_images_convert)

                LiquidButton(onClick = { viewModel.run(context) }, backdrop = backdrop, tint = accent, isInteractive = !state.isProcessing, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isProcessing) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Image, null, Modifier.size(18.dp), Color.White)
                        BasicText(runLabel, style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }

            if (state.resultPages.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(androidx.compose.ui.res.stringResource(R.string.pdf_to_images_preview), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.resultPages.take(12).forEach { page ->
                            val thumb = remember(page.file.path) {
                                runCatching {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                    BitmapFactory.decodeFile(page.file.path, opts)
                                }.getOrNull()
                            }
                            Box(
                                Modifier.size(width = 64.dp, height = 84.dp).clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, sub.copy(0.3f), RoundedCornerShape(8.dp))
                                    .background(if (isLight) Color(0xFFF5F5F5) else Color(0xFF333333))
                            ) {
                                if (thumb != null) {
                                    Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                        .background(Color.Black.copy(0.45f)).padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText("${page.pageIndex + 1}", style = TextStyle(Color.White, 11.sp, FontWeight.Bold))
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidButton(onClick = { viewModel.saveToGallery(context) }, backdrop = backdrop, tint = Color(0xFF43A047), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp), Color.White)
                                BasicText(androidx.compose.ui.res.stringResource(R.string.pdf_to_images_save), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                            }
                        }
                        LiquidButton(onClick = { viewModel.shareAll(context) }, backdrop = backdrop, surfaceColor = Color.White.copy(0.1f), modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp), Color.White)
                                BasicText(androidx.compose.ui.res.stringResource(R.string.share), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                    BasicText(it, style = TextStyle(Color(0xFFD32F2F), 14.sp))
                }
            }
            state.resultMessage?.let {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                    BasicText(it, style = TextStyle(Color(0xFF388E3C), 14.sp))
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun stringResourceSafe(): String = androidx.compose.ui.res.stringResource(R.string.back)

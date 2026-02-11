package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun PdfViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF1976D2)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openPdf(context, it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "PDF Viewer", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        if (state.document == null) {
            // Pick file state
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(56.dp), accent)
                    BasicText("Open a PDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                    BasicText("Select a PDF file from your device to view it", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

                    LiquidButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        backdrop = backdrop, tint = accent
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                            BasicText("Pick a PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }

                if (state.errorMessage != null) {
                    BasicText(state.errorMessage!!, style = TextStyle(Color(0xFFD32F2F), 14.sp))
                }
                Spacer(Modifier.height(80.dp))
            }
        } else {
            // File info
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicText(state.fileName, style = TextStyle(text, 16.sp, fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BasicText("${state.pageCount} pages", style = TextStyle(sub, 13.sp))
                    if (state.sizeBytes > 0) {
                        val sizeStr = if (state.sizeBytes > 1_048_576)
                            "%.1f MB".format(state.sizeBytes / 1_048_576f)
                        else "${state.sizeBytes / 1024} KB"
                        BasicText(sizeStr, style = TextStyle(sub, 13.sp))
                    }
                }
                BasicText(
                    "Page ${state.currentPage + 1} of ${state.pageCount}",
                    style = TextStyle(accent, 13.sp, fontWeight = FontWeight.Medium)
                )
            }

            // Page viewer
            if (state.pageCount > 0) {
                val pagerState = rememberPagerState(initialPage = 0) { state.pageCount }

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.onPageChanged(pagerState.currentPage)
                    viewModel.renderPage(context, pagerState.currentPage)
                    // Preload adjacent pages
                    if (pagerState.currentPage + 1 < state.pageCount)
                        viewModel.renderPage(context, pagerState.currentPage + 1)
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) { page ->
                    val bitmap = state.pageBitmaps.getOrNull(page)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${page + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (isLight) Color(0xFFF5F5F5) else Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }

            // Open another file
            LiquidButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                backdrop = backdrop,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(16.dp), text)
                    BasicText("Open Another", style = TextStyle(text, 14.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

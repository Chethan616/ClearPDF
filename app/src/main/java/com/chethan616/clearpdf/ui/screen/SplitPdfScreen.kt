package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import com.chethan616.clearpdf.ui.viewmodel.SplitPdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: SplitPdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF7B1FA2)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val selectedPages = remember { mutableStateListOf<Int>() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedPages.clear()
            viewModel.onSelectFile(context, uri)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "Split PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.CallSplit, null, Modifier.size(56.dp), accent)
            BasicText("Extract Pages", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Split a PDF into separate pages or extract specific pages", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

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
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(state.sourceFileName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                BasicText("${state.pageCount} pages", style = TextStyle(sub, 13.sp))

                // Split All button
                LiquidButton(
                    onClick = { viewModel.onSplitAll(context) },
                    backdrop = backdrop, tint = accent,
                    isInteractive = !state.isSplitting
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isSplitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.CallSplit, null, Modifier.size(18.dp), Color.White)
                        }
                        BasicText(
                            if (state.isSplitting) "Splitting..." else "Split All Pages",
                            style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            // Page selection for extract — page thumbnails
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicText("Select Pages to Extract", style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                    Icon(
                        Icons.Rounded.SelectAll, "Select All",
                        Modifier
                            .size(22.dp)
                            .clickable {
                                if (selectedPages.size == state.pageCount) selectedPages.clear()
                                else {
                                    selectedPages.clear()
                                    selectedPages.addAll(0 until state.pageCount)
                                }
                            },
                        accent
                    )
                }

                // Page grid with thumbnails
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until state.pageCount) {
                        val isSelected = i in selectedPages
                        val thumb = state.pageThumbnails.getOrNull(i)
                        Box(
                            Modifier
                                .size(width = 64.dp, height = 84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) accent else sub.copy(0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isSelected) selectedPages.remove(i) else selectedPages.add(i)
                                }
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = "Page ${i + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(if (isLight) Color(0xFFF5F5F5) else Color(0xFF333333))
                                )
                            }

                            // Page number badge
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) accent.copy(alpha = 0.85f)
                                        else Color.Black.copy(alpha = 0.45f)
                                    )
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    "${i + 1}",
                                    style = TextStyle(Color.White, 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }

                            // Checkmark for selected
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Selected pages preview & extract button
            if (selectedPages.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText(
                        "${selectedPages.size} page${if (selectedPages.size != 1) "s" else ""} selected",
                        style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium)
                    )

                    // Horizontal preview strip of selected pages
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(selectedPages.sorted()) { pageIdx ->
                            val thumb = state.pageThumbnails.getOrNull(pageIdx)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 72.dp, height = 96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, accent, RoundedCornerShape(8.dp))
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Page ${pageIdx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(accent.copy(0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText("${pageIdx + 1}", style = TextStyle(accent, 16.sp, fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                                BasicText(
                                    "Page ${pageIdx + 1}",
                                    style = TextStyle(sub, 11.sp, fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    LiquidButton(
                        onClick = { viewModel.onExtractPages(context, selectedPages.sorted().toList()) },
                        backdrop = backdrop, tint = accent,
                        isInteractive = !state.isSplitting
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state.isSplitting) {
                                CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ContentCut, null, Modifier.size(18.dp), Color.White)
                            }
                            BasicText(
                                if (state.isSplitting) "Extracting..." else "Extract ${selectedPages.size} Pages",
                                style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        if (state.errorMessage != null) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                BasicText(state.errorMessage!!, style = TextStyle(Color(0xFFD32F2F), 14.sp))
            }
        }

        if (!state.resultMessage.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                BasicText(state.resultMessage!!, style = TextStyle(Color(0xFF388E3C), 14.sp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

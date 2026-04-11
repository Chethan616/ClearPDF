package com.chethan616.clearpdf.ui.screen

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
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
import com.chethan616.clearpdf.ui.viewmodel.SplitMode
import com.chethan616.clearpdf.ui.viewmodel.SplitPdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: SplitPdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF7B1FA2)
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
        if (uri != null) {
            viewModel.onSelectFile(context, uri)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.CallSplit, null, Modifier.size(52.dp), accent)
            BasicText("Extract or split pages", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Pick one mode, then run one clear primary action.", style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center))

            LiquidButton(
                onClick = { filePicker.launch(arrayOf("application/pdf")) },
                backdrop = backdrop,
                tint = accent
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicText(state.sourceFileName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.SemiBold))
                BasicText("${state.pageCount} pages", style = TextStyle(sub, 13.sp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiquidButton(
                    onClick = { viewModel.onModeChanged(SplitMode.EXTRACT_SELECTED) },
                    backdrop = backdrop,
                    tint = if (state.mode == SplitMode.EXTRACT_SELECTED) accent else Color.Transparent,
                    surfaceColor = if (state.mode == SplitMode.EXTRACT_SELECTED) accent else Color.White.copy(0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        "Extract Selected",
                        style = TextStyle(if (state.mode == SplitMode.EXTRACT_SELECTED) Color.White else text, 13.sp, FontWeight.Medium)
                    )
                }

                LiquidButton(
                    onClick = { viewModel.onModeChanged(SplitMode.SPLIT_ALL) },
                    backdrop = backdrop,
                    tint = if (state.mode == SplitMode.SPLIT_ALL) accent else Color.Transparent,
                    surfaceColor = if (state.mode == SplitMode.SPLIT_ALL) accent else Color.White.copy(0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        "Split All",
                        style = TextStyle(if (state.mode == SplitMode.SPLIT_ALL) Color.White else text, 13.sp, FontWeight.Medium)
                    )
                }
            }

            if (state.mode == SplitMode.EXTRACT_SELECTED) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicText(
                        "${state.selectedPages.size} page${if (state.selectedPages.size == 1) "" else "s"} selected",
                        style = TextStyle(text, 14.sp, FontWeight.Medium)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickSelectChip(label = "All", onClick = { viewModel.onSelectAllPages() }, text = text, accent = accent)
                        QuickSelectChip(label = "Odd", onClick = { viewModel.onSelectOddPages() }, text = text, accent = accent)
                        QuickSelectChip(label = "Even", onClick = { viewModel.onSelectEvenPages() }, text = text, accent = accent)
                        QuickSelectChip(label = "Clear", onClick = { viewModel.onClearSelection() }, text = text, accent = accent)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 0 until state.pageCount) {
                            val isSelected = i in state.selectedPages
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
                                    .clickable { viewModel.onToggleSelectedPage(i) }
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
                                    BasicText("${i + 1}", style = TextStyle(Color.White, 11.sp, fontWeight = FontWeight.Bold))
                                }

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
            } else {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                    BasicText(
                        "This will generate one PDF per page.",
                        style = TextStyle(sub, 13.sp)
                    )
                }
            }

            val actionLabel = when (state.mode) {
                SplitMode.SPLIT_ALL -> if (state.isSplitting) "Splitting..." else "Split All Pages"
                SplitMode.EXTRACT_SELECTED -> if (state.isSplitting) "Extracting..." else "Extract Selected Pages"
            }

            LiquidButton(
                onClick = { viewModel.onRunPrimaryAction(context) },
                backdrop = backdrop,
                tint = accent,
                isInteractive = !state.isSplitting
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.isSplitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.mode == SplitMode.SPLIT_ALL) Icons.AutoMirrored.Rounded.CallSplit else Icons.Rounded.ContentCut,
                            null,
                            Modifier.size(18.dp),
                            Color.White
                        )
                    }
                    BasicText(actionLabel, style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
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

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun QuickSelectChip(
    label: String,
    onClick: () -> Unit,
    text: Color,
    accent: Color
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        BasicText(label, style = TextStyle(text, 12.sp, FontWeight.Medium))
    }
}

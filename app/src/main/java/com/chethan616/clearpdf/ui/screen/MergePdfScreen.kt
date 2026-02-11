package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.chethan616.clearpdf.ui.viewmodel.MergePdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun MergePdfScreen(
    backdrop: LayerBackdrop,
    viewModel: MergePdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFFD32F2F)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFiles(context, uris)
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
            LiquidGlassTopBar(title = "Merge PDFs", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(56.dp), accent)
            BasicText("Combine Multiple PDFs", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Select two or more PDF files to merge into one", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

            LiquidButton(
                onClick = { filePicker.launch(arrayOf("application/pdf")) },
                backdrop = backdrop, tint = accent
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddCircleOutline, null, Modifier.size(18.dp), Color.White)
                    BasicText("Add Files", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        if (state.selectedFiles.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BasicText("${state.selectedFiles.size} files selected", style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                state.selectedFiles.forEachIndexed { index, file ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp), accent.copy(0.7f))
                        BasicText(file, style = TextStyle(sub, 14.sp), modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.Close, "Remove",
                            Modifier
                                .size(18.dp)
                                .clickable { viewModel.onRemoveFile(index) },
                            sub
                        )
                    }
                }
            }

            LiquidButton(
                onClick = { viewModel.onMerge(context) },
                backdrop = backdrop, tint = accent,
                isInteractive = !state.isMerging
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.isMerging) {
                        CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.MergeType, null, Modifier.size(18.dp), Color.White)
                    }
                    BasicText(
                        if (state.isMerging) "Merging..." else "Merge Now",
                        style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium)
                    )
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

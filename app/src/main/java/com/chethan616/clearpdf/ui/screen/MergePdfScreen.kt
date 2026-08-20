package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

import android.net.Uri
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.automirrored.rounded.MergeType
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.MergePdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun MergePdfScreen(
    backdrop: LayerBackdrop,
    viewModel: MergePdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFFD32F2F)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val canMerge = state.selectedUris.size >= 2 && !state.isMerging

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (!state.resultMessage.isNullOrBlank() || !state.errorMessage.isNullOrBlank()) {
            delay(3000)
            viewModel.clearFeedback()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFiles(context, uris)
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mergeTopBarAlpha"
    )
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mergeContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "mergeContentOffsetY"
    )

    GlassScreenScaffold(
        backdrop = backdrop,
        header = { headerBackdrop ->
            // Fade only — the header is glass, and translating glass re-runs its blur+lens.
            GlassScreenHeaderRow(
                title = stringResource(R.string.tool_merge),
                backdrop = headerBackdrop,
                onBack = onBack,
                modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
            )
        }
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
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
            Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(56.dp), accent)
            BasicText(stringResource(R.string.tool_merge), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(stringResource(R.string.tool_merge_description), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

            LiquidButton(
                onClick = { filePicker.launch(arrayOf("application/pdf")) },
                backdrop = backdrop, tint = accent
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddCircleOutline, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.add_files), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
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
                BasicText(stringResource(R.string.files_selected, state.selectedFiles.size), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                BasicText(stringResource(R.string.reorder_files_hint), style = TextStyle(sub, 12.sp))
                state.selectedFiles.forEachIndexed { index, file ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp), accent.copy(0.7f))
                        BasicText(file, style = TextStyle(sub, 14.sp), modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.ArrowUpward, "Move up",
                            Modifier
                                .size(18.dp)
                                .clickable(enabled = index > 0) { viewModel.moveFile(index, index - 1) },
                            if (index > 0) accent else sub.copy(0.45f)
                        )
                        Icon(
                            Icons.Rounded.ArrowDownward, "Move down",
                            Modifier
                                .size(18.dp)
                                .clickable(enabled = index < state.selectedFiles.lastIndex) { viewModel.moveFile(index, index + 1) },
                            if (index < state.selectedFiles.lastIndex) accent else sub.copy(0.45f)
                        )
                        LiquidIconButton(
                            onClick = { viewModel.onRemoveFile(index) },
                            backdrop = backdrop,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(28.dp)
                        ) {
                            CloseCrossIcon(Modifier.size(12.dp), Color.White)
                        }
                    }
                }
            }

            if (state.selectedFiles.size < 2) {
                Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                    BasicText(stringResource(R.string.select_two_pdfs), style = TextStyle(Color(0xFFD32F2F), 13.sp))
                }
            }

            LiquidButton(
                onClick = { if (canMerge) viewModel.onMerge(context) },
                backdrop = backdrop,
                tint = if (canMerge) accent else accent.copy(0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    if (state.isMerging) {
                        CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.MergeType, null, Modifier.size(18.dp), Color.White.copy(if (canMerge) 1f else 0.6f))
                    }
                    BasicText(
                        if (state.isMerging) stringResource(R.string.merging) else stringResource(R.string.merge_now),
                        style = TextStyle(Color.White.copy(if (canMerge) 1f else 0.6f), 15.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1
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

        state.lastOutputUri?.let { outputUri ->
            LiquidButton(
                onClick = { onViewOutput(outputUri) },
                backdrop = backdrop,
                tint = Color(0xFF1976D2),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(stringResource(R.string.viewer_open_pdf), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(80.dp))
        }
    }
}

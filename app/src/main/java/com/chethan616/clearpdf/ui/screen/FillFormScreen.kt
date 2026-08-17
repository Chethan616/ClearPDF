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
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassSectionHeader
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.FillFormViewModel
import com.kyant.pdfcore.form.PdfFormService
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

private val FormAccent = Color(0xFF00695C)

@Composable
fun FillFormScreen(
    backdrop: LayerBackdrop,
    viewModel: FillFormViewModel,
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
        if (state.resultMessage != null || state.errorMessage != null) { delay(3500); viewModel.clearFeedback() }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onSelectFile(context, uri) }

    ToolScaffold(
        title = stringResource(R.string.tool_fill_form),
        backdrop = backdrop,
        onBack = onBack
    ) {
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.EditNote, null, Modifier.size(56.dp), FormAccent)
            BasicText(stringResource(R.string.tool_fill_form), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(stringResource(R.string.tool_fill_form_sub), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
            LiquidButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }, backdrop = backdrop, tint = FormAccent) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        if (state.fields.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassSectionHeader(
                    title = stringResource(R.string.fill_form_fields),
                    icon = Icons.Rounded.EditNote, iconTint = FormAccent, titleColor = text,
                    trailing = { BasicText("${state.fields.size}", style = TextStyle(sub, 13.sp)) }
                )
                state.fields.forEach { field ->
                    when (field.type) {
                        PdfFormService.FieldType.CHECKBOX -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                BasicText(field.name, style = TextStyle(text, 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                LiquidToggle(
                                    selected = { field.value == "true" },
                                    onSelect = { viewModel.onFieldChange(field.name, if (it) "true" else "false") },
                                    backdrop = backdrop
                                )
                            }
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BasicText(field.name, style = TextStyle(sub, 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicTextField(
                                    value = field.value,
                                    onValueChange = { viewModel.onFieldChange(field.name, it) },
                                    singleLine = true,
                                    textStyle = TextStyle(text, 15.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                                if (field.options.isNotEmpty()) {
                                    BasicText(field.options.joinToString(" · "), style = TextStyle(sub.copy(0.8f), 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        BasicText(stringResource(R.string.fill_form_flatten), style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))
                        BasicText(stringResource(R.string.fill_form_flatten_desc), style = TextStyle(sub, 12.sp))
                    }
                    LiquidToggle(selected = { state.flatten }, onSelect = viewModel::onFlattenChange, backdrop = backdrop)
                }
            }

            LiquidButton(
                onClick = { if (!state.isProcessing) viewModel.save(context) },
                backdrop = backdrop, tint = FormAccent, modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    if (state.isProcessing) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp), Color.White)
                    BasicText(
                        stringResource(if (state.isProcessing) R.string.fill_form_working else R.string.fill_form_action),
                        style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium), maxLines = 1
                    )
                }
            }
        }

        if (state.errorMessage != null) {
            LiquidGlassErrorCard(message = state.errorMessage!!, backdrop = backdrop, uiSensor = uiSensor, onDismiss = { viewModel.clearFeedback() })
        }

        state.lastOutputUri?.let { outUri ->
            LiquidButton(onClick = { onViewOutput(outUri) }, backdrop = backdrop, tint = Color(0xFF1976D2), modifier = Modifier.fillMaxWidth()) {
                BasicText(stringResource(R.string.viewer_open_pdf), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold), modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassSectionHeader
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.HtmlToPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.WebToPdfMode
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

private val HtmlAccent = Color(0xFFE65100)

@Composable
fun HtmlToPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: HtmlToPdfViewModel,
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
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onLoadFile(context, uri) }

    ToolScaffold(
        title = stringResource(R.string.tool_html_to_pdf),
        backdrop = backdrop,
        onBack = onBack
    ) {
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassSectionHeader(title = stringResource(R.string.tool_html_to_pdf), icon = Icons.Rounded.Code, iconTint = HtmlAccent, titleColor = text)

            // Mode: Web URL / HTML
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeOption(stringResource(R.string.html_to_pdf_mode_web), state.mode == WebToPdfMode.URL, backdrop, isLight, { viewModel.onModeChange(WebToPdfMode.URL) }, Modifier.weight(1f))
                ModeOption(stringResource(R.string.html_to_pdf_mode_html), state.mode == WebToPdfMode.HTML, backdrop, isLight, { viewModel.onModeChange(WebToPdfMode.HTML) }, Modifier.weight(1f))
            }

            if (state.mode == WebToPdfMode.URL) {
                BasicText(stringResource(R.string.html_to_pdf_url_desc), style = TextStyle(sub, 13.sp, textAlign = TextAlign.Start))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(18.dp), sub)
                    BasicTextField(
                        value = state.url,
                        onValueChange = viewModel::onUrlChange,
                        singleLine = true,
                        textStyle = TextStyle(text, 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (state.url.isEmpty()) BasicText(stringResource(R.string.html_to_pdf_url_hint), style = TextStyle(sub, 15.sp))
                            inner()
                        }
                    )
                }
            } else {
                BasicText(stringResource(R.string.html_to_pdf_desc), style = TextStyle(sub, 13.sp, textAlign = TextAlign.Start))
                LiquidButton(onClick = { filePicker.launch("text/html") }, backdrop = backdrop, tint = HtmlAccent, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp), Color.White)
                        BasicText(stringResource(R.string.html_to_pdf_load_file), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
                if (state.sourceName.isNotEmpty()) BasicText(state.sourceName, style = TextStyle(sub, 12.sp), maxLines = 1)
                BasicTextField(
                    value = state.html,
                    onValueChange = viewModel::onHtmlChange,
                    textStyle = TextStyle(text, 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .padding(12.dp),
                    decorationBox = { inner ->
                        if (state.html.isEmpty()) BasicText(stringResource(R.string.html_to_pdf_hint), style = TextStyle(sub, 13.sp))
                        inner()
                    }
                )
            }
        }

        LiquidButton(
            onClick = { if (!state.isProcessing) viewModel.convert(context) },
            backdrop = backdrop, tint = HtmlAccent, modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                if (state.isProcessing) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp), Color.White)
                BasicText(
                    stringResource(if (state.isProcessing) R.string.html_to_pdf_working else R.string.html_to_pdf_action),
                    style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium), maxLines = 1
                )
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

@Composable
private fun ModeOption(
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
        tint = if (selected) HtmlAccent else Color.Transparent,
        surfaceColor = if (selected) HtmlAccent.copy(0.18f) else (if (isLight) Color.White.copy(0.70f) else Color.White.copy(0.10f)),
        modifier = modifier
    ) {
        BasicText(
            label,
            style = TextStyle(contentColor, 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

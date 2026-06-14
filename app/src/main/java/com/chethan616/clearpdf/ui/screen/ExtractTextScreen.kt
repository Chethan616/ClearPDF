package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ExtractTextViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun ExtractTextScreen(
    backdrop: LayerBackdrop,
    viewModel: ExtractTextViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiSensor = rememberUISensor()
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = Color(0xFF00838F)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSelectFile(context, it) }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar("Extract Text", backdrop, uiSensor, Modifier.weight(1f), titleFontSize = 18.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = { picker.launch(arrayOf("application/pdf")) }, backdrop = backdrop, tint = accent) {
                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                BasicText("Pick a PDF", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
            }
            if (state.text.isNotEmpty()) {
                LiquidButton(onClick = { clipboard.setText(AnnotatedString(state.text)) }, backdrop = backdrop) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), text)
                    BasicText("Copy", style = TextStyle(text, 14.sp, FontWeight.Medium))
                }
                LiquidButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share text"))
                }, backdrop = backdrop) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(16.dp), text)
                    BasicText("Share", style = TextStyle(text, 14.sp, FontWeight.Medium))
                }
            }
        }

        state.sourceFileName.takeIf { it.isNotBlank() }?.let {
            BasicText(it, style = TextStyle(sub, 12.sp))
        }
        state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFFFB300), 12.sp)) }

        Box(Modifier.fillMaxWidth().weight(1f).liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
            when {
                state.isExtracting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }
                state.text.isNotEmpty() -> SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BasicText(state.text, style = TextStyle(text, 14.sp))
                }
                state.hasResult -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    BasicText("No text layer in this PDF.", style = TextStyle(sub, 13.sp))
                }
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    BasicText("Pick a PDF to pull its text.", style = TextStyle(sub, 13.sp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

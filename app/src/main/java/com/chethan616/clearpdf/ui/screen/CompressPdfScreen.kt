package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CompressPdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun CompressPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CompressPdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isLight = !isSystemInDarkTheme()
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF388E3C)
    val uiSensor = rememberUISensor()
    var quality by remember { mutableFloatStateOf(0.5f) }

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
            LiquidGlassTopBar(title = "Compress PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.Compress, null, Modifier.size(56.dp), accent)
            BasicText("Reduce File Size", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Compress your PDF while preserving quality", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))

            LiquidButton(onClick = { }, backdrop = backdrop, tint = accent) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText("Select PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.HighQuality, null, Modifier.size(22.dp), accent)
                BasicText("Quality", style = TextStyle(text, 16.sp, fontWeight = FontWeight.Medium))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Low", style = TextStyle(sub, 13.sp))
                BasicText("${(quality * 100).toInt()}%", style = TextStyle(text, 13.sp, fontWeight = FontWeight.SemiBold))
                BasicText("High", style = TextStyle(sub, 13.sp))
            }
            LiquidSlider(
                value = { quality },
                onValueChange = { quality = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.005f,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.sourceFileName.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BasicText(state.sourceFileName, style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium))

                LiquidButton(onClick = { viewModel.onCompress() }, backdrop = backdrop, tint = accent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Compress, null, Modifier.size(18.dp), Color.White)
                        BasicText("Compress", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        if (!state.resultMessage.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                BasicText(state.resultMessage!!, style = TextStyle(sub, 14.sp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

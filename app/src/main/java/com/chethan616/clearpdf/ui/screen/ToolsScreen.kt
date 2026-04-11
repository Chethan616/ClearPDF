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
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidGlassCard
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun ToolsScreen(
    backdrop: LayerBackdrop,
    onNavigateToOpenPdf: () -> Unit,
    onNavigateToMergePdf: () -> Unit,
    onNavigateToSplitPdf: () -> Unit,
    onNavigateToCompressPdf: () -> Unit,
    onNavigateToCreatePdf: () -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val sub = if (isLight) Color(0xFF666666) else Color(0xFFAAAAAA)
    val uiSensor = rememberUISensor()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiquidGlassTopBar(title = "Tools", backdrop = backdrop, uiSensor = uiSensor)

        BasicText(
            "Every tool you need to work with PDFs",
            style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidGlassCard(
                title = "Open PDF", subtitle = "View & read",
                accentColor = Color(0xFF1976D2), backdrop = backdrop, uiSensor = uiSensor,
                onClick = onNavigateToOpenPdf, modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.FileOpen, null, Modifier.size(26.dp), Color(0xFF1976D2))
            }
            LiquidGlassCard(
                title = "Merge PDFs", subtitle = "Combine files",
                accentColor = Color(0xFFD32F2F), backdrop = backdrop, uiSensor = uiSensor,
                onClick = onNavigateToMergePdf, modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(26.dp), Color(0xFFD32F2F))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidGlassCard(
                title = "Split PDF", subtitle = "Extract pages",
                accentColor = Color(0xFF7B1FA2), backdrop = backdrop, uiSensor = uiSensor,
                onClick = onNavigateToSplitPdf, modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Rounded.CallSplit, null, Modifier.size(26.dp), Color(0xFF7B1FA2))
            }
            LiquidGlassCard(
                title = "Compress", subtitle = "Reduce size",
                accentColor = Color(0xFF388E3C), backdrop = backdrop, uiSensor = uiSensor,
                onClick = onNavigateToCompressPdf, modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Compress, null, Modifier.size(26.dp), Color(0xFF388E3C))
            }
        }

        LiquidGlassCard(
            title = "Create PDF", subtitle = "From scratch, images, or text",
            accentColor = Color(0xFFE65100), backdrop = backdrop, uiSensor = uiSensor,
            onClick = onNavigateToCreatePdf, modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(26.dp), Color(0xFFE65100))
        }

        Spacer(Modifier.height(80.dp))
    }
}

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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassCard
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun HomeScreen(
    backdrop: LayerBackdrop,
    onNavigateToOpenPdf: () -> Unit,
    onPickWallpaper: () -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF666666) else Color(0xFFAAAAAA)
    val accent = Color(0xFF0088FF)
    val uiSensor = rememberUISensor()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiquidGlassTopBar(title = "ClearPDF", backdrop = backdrop, uiSensor = uiSensor)

        // Welcome card
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Description, contentDescription = null,
                tint = accent, modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                "Welcome to ClearPDF",
                style = TextStyle(text, 22.sp, FontWeight.Bold, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Your all-in-one PDF toolkit.\nOpen, merge, split, compress, and create PDFs.",
                style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(onClick = onNavigateToOpenPdf, backdrop = backdrop, tint = accent) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp), Color.White)
                        BasicText("Open PDF", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                    }
                }
                LiquidButton(
                    onClick = onPickWallpaper, backdrop = backdrop,
                    surfaceColor = if (isLight) Color.White.copy(0.3f) else Color.White.copy(0.1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Wallpaper, null, Modifier.size(18.dp), text)
                        BasicText("Wallpaper", style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                }
            }
        }

        // Recent files
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(20.dp)
        ) {
            BasicText("Recent Files", style = TextStyle(text, 16.sp, FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            listOf("Annual Report 2025.pdf", "Contract Draft.pdf", "Invoice_0042.pdf").forEach { file ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.PictureAsPdf, null,
                        Modifier.size(28.dp), Color(0xFFE53935)
                    )
                    Column {
                        BasicText(file, style = TextStyle(text, 14.sp, FontWeight.Medium))
                        BasicText("Opened yesterday", style = TextStyle(sub, 11.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

package com.chethan616.clearpdf.ui.screen

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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    backdrop: LayerBackdrop,
    onNavigateToOpenPdf: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF666666) else Color(0xFFAAAAAA)
    val accent = Color(0xFF0088FF)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    val recentsState = remember { mutableStateOf(RecentFilesManager.getRecents(context)) }

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
                "Your all-in-one PDF toolkit.\nScan, open, merge, split, compress, and create PDFs.",
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
                    onClick = onNavigateToScan, backdrop = backdrop,
                    tint = Color(0xFF4CAF50)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Scanner, null, Modifier.size(18.dp), Color.White)
                        BasicText("Scan", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText("Recent Files", style = TextStyle(text, 16.sp, FontWeight.Bold))
                if (recentsState.value.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.DeleteOutline, "Clear recents",
                        Modifier
                            .size(20.dp)
                            .clickable {
                                RecentFilesManager.clearRecents(context)
                                recentsState.value = emptyList()
                            },
                        sub
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (recentsState.value.isEmpty()) {
                BasicText("No recent files", style = TextStyle(sub, 14.sp))
            } else {
                recentsState.value.forEach { recent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToOpenPdf() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PictureAsPdf, null,
                            Modifier.size(28.dp), Color(0xFFE53935)
                        )
                        Column(Modifier.weight(1f)) {
                            BasicText(recent.name, style = TextStyle(text, 14.sp, FontWeight.Medium))
                            val timeStr = formatTimestamp(recent.timestamp)
                            val sizeStr = if (recent.sizeBytes > 0) " · ${recent.sizeBytes / 1024} KB" else ""
                            val pageStr = if (recent.pageCount > 0) " · ${recent.pageCount} pages" else ""
                            BasicText("$timeStr$sizeStr$pageStr", style = TextStyle(sub, 11.sp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

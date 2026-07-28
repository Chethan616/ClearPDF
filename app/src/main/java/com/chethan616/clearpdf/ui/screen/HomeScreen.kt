package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onNavigateToScan: () -> Unit,
    onRecentFileSelected: (Uri) -> Unit
) {
    val homeRecentLimit = 5
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF666666) else Color(0xFFAAAAAA)
    val accent = Color(0xFF0088FF)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    var recents by remember { mutableStateOf(RecentFilesManager.getRecents(context)) }
    var showAllRecents by remember { mutableStateOf(false) }
    var selectedRecent by remember { mutableStateOf<com.chethan616.clearpdf.data.repository.RecentFile?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recents = RecentFilesManager.getRecents(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
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
                if (recents.isNotEmpty()) {
                    if (recents.size > homeRecentLimit) {
                        BasicText(
                            if (showAllRecents) "Show less" else "See all (${recents.size})",
                            style = TextStyle(accent, 12.sp, FontWeight.SemiBold),
                            modifier = Modifier.clickable { showAllRecents = !showAllRecents }
                        )
                    }
                    Icon(
                        Icons.Rounded.DeleteOutline, "Clear recents",
                        Modifier
                            .size(20.dp)
                            .clickable {
                                RecentFilesManager.clearRecents(context)
                                recents = emptyList()
                                showAllRecents = false
                            },
                        sub
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (recents.isEmpty()) {
                BasicText("Your opened and exported PDFs will show up here.", style = TextStyle(sub, 14.sp))
            } else {
                val visibleRecents = if (showAllRecents) recents else recents.take(homeRecentLimit)
                visibleRecents.forEach { recent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isLight) Color.White.copy(0.18f) else Color.White.copy(0.06f))
                            .combinedClickable(
                                onClick = { onRecentFileSelected(recent.uri) },
                                onLongClick = { selectedRecent = recent }
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE53935).copy(alpha = if (isLight) 0.14f else 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(24.dp), Color(0xFFE53935))
                        }
                        Column(Modifier.weight(1f)) {
                            BasicText(recent.name, style = TextStyle(text, 14.sp, FontWeight.Medium))
                            val timeStr = formatTimestamp(recent.timestamp)
                            val sizeStr = if (recent.sizeBytes > 0) " · ${recent.sizeBytes / 1024} KB" else ""
                            val pageStr = if (recent.pageCount > 0) " · ${recent.pageCount} pages" else ""
                            BasicText("$timeStr$sizeStr$pageStr", style = TextStyle(sub, 11.sp))
                        }
                        BasicText("PDF", style = TextStyle(accent, 10.sp, FontWeight.Bold))
                    }
                }
                if (!showAllRecents && recents.size > homeRecentLimit) {
                    BasicText(
                        "Hold a file for quick actions",
                        style = TextStyle(sub, 11.sp),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(80.dp))
        }

        selectedRecent?.let { recent ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable { selectedRecent = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .liquidGlassPanel(backdrop, uiSensor)
                        .clickable { /* Consume taps inside the action sheet. */ }
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText("Quick actions", style = TextStyle(text, 18.sp, FontWeight.Bold))
                    BasicText(
                        recent.name,
                        style = TextStyle(sub, 13.sp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LiquidButton(
                        onClick = {
                            selectedRecent = null
                            onRecentFileSelected(recent.uri)
                        },
                        backdrop = backdrop,
                        tint = accent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicText("Open PDF", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                    }
                    LiquidButton(
                        onClick = {
                            RecentFilesManager.removeRecent(context, recent.uri)
                            recents = recents.filterNot { it.uriString == recent.uri.toString() }
                            selectedRecent = null
                        },
                        backdrop = backdrop,
                        surfaceColor = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicText("Remove from Recents", style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                    BasicText(
                        "Cancel",
                        style = TextStyle(accent, 13.sp, FontWeight.SemiBold, textAlign = TextAlign.Center),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRecent = null }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
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

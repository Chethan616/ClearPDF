package com.chethan616.clearpdf.ui.screen

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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {}
) {
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val label = if (isLight) Color(0xFF444444) else Color(0xFFCCCCCC)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    var autoCompress by remember { mutableStateOf(true) }
    var keepOriginal by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(false) }
    var defaultQuality by remember { mutableFloatStateOf(0.7f) }
    var saveUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable permission so we can write there later
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val displayPath = uri.lastPathSegment?.replace("primary:", "") ?: uri.toString()
            SaveLocationManager.setSaveLocation(context, uri, displayPath)
            saveUri = uri
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .graphicsLayer { }
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiquidGlassTopBar(title = "Settings", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.fillMaxWidth())

        // ── Appearance ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText("Appearance", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))

            SettingsToggleRow(
                icon = Icons.Rounded.DarkMode,
                title = "Dark Mode",
                desc = "Use dark colour scheme",
                checked = isDarkMode,
                onCheckedChange = onDarkModeChanged,
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )
        }

        // ── Save Location ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText("Save Location", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(22.dp), label)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        if (saveUri != null) "Custom Location" else "Default (Downloads)",
                        style = TextStyle(label, 15.sp, fontWeight = FontWeight.Medium)
                    )
                    if (saveUri != null) {
                        val path = saveUri!!.lastPathSegment?.replace("primary:", "") ?: saveUri.toString()
                        BasicText(path, style = TextStyle(sub, 12.sp))
                    } else {
                        BasicText("PDFs saved to Downloads folder", style = TextStyle(sub, 12.sp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton(
                    onClick = { folderPicker.launch(null) },
                    backdrop = backdrop, tint = Color(0xFF1976D2)
                ) {
                    BasicText("Choose Folder", style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Medium))
                }
                if (saveUri != null) {
                    LiquidButton(
                        onClick = {
                            SaveLocationManager.clearSaveLocation(context)
                            saveUri = null
                        },
                        backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)
                    ) {
                        BasicText("Reset", style = TextStyle(text, 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        // ── File Handling ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText("File Handling", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))

            SettingsToggleRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "Auto-Compress",
                desc = "Compress PDFs automatically on import",
                checked = autoCompress,
                onCheckedChange = { autoCompress = it },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

            SettingsToggleRow(
                icon = Icons.Rounded.FileCopy,
                title = "Keep Original",
                desc = "Preserve original file after editing",
                checked = keepOriginal,
                onCheckedChange = { keepOriginal = it },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

            SettingsToggleRow(
                icon = Icons.Rounded.Notifications,
                title = "Notifications",
                desc = "Show notification when tasks complete",
                checked = notifications,
                onCheckedChange = { notifications = it },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )
        }

        // ── Default Quality ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.HighQuality, null, Modifier.size(22.dp), Color(0xFF1976D2))
                BasicText("Default Quality", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Low", style = TextStyle(sub, 13.sp))
                BasicText("${(defaultQuality * 100).toInt()}%", style = TextStyle(text, 13.sp, fontWeight = FontWeight.SemiBold))
                BasicText("High", style = TextStyle(sub, 13.sp))
            }
            LiquidSlider(
                value = { defaultQuality },
                onValueChange = { defaultQuality = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.005f,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── About ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Info, null, Modifier.size(32.dp), sub)
            BasicText("ClearPDF", style = TextStyle(text, 18.sp, fontWeight = FontWeight.Bold))
            BasicText("Version 1.0.0", style = TextStyle(sub, 13.sp))
            BasicText(
                "A beautiful liquid glass PDF editor",
                style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center)
            )
        }

        // ── Open Source Licenses ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText("Open Source Licenses", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Backdrop (Liquid Glass UI)", style = TextStyle(label, 14.sp, fontWeight = FontWeight.Medium))
                BasicText("by Kyant", style = TextStyle(sub, 12.sp))
                BasicText("Licensed under Apache License 2.0", style = TextStyle(sub, 12.sp))
                BasicText("https://github.com/Kyant0/AndroidLiquidGlass", style = TextStyle(Color(0xFF0088FF), 12.sp))
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Apache License 2.0", style = TextStyle(label, 13.sp, fontWeight = FontWeight.Medium))
                BasicText(
                    "Licensed under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0",
                    style = TextStyle(sub, 11.sp, lineHeight = 16.sp)
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: LayerBackdrop,
    labelColor: Color,
    subColor: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), labelColor)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(labelColor, 15.sp, fontWeight = FontWeight.Medium))
            BasicText(desc, style = TextStyle(subColor, 12.sp))
        }
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop
        )
    }
}

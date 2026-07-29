package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    themeMode: Int = 0,
    onThemeModeChanged: (Int) -> Unit = {}
) {
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val label = if (isLight) Color(0xFF444444) else Color(0xFFCCCCCC)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val openRepo = remember(context) {
        { openExternalLink(context, GitHubStarPromptManager.REPO_URL) }
    }

    var autoCompress by remember { mutableStateOf(AppSettingsManager.getAutoCompress(context)) }
    var keepOriginal by remember { mutableStateOf(AppSettingsManager.getKeepOriginal(context)) }
    var notifications by remember { mutableStateOf(AppSettingsManager.getNotifications(context)) }
    var defaultQuality by remember { mutableFloatStateOf(AppSettingsManager.getDefaultQuality(context)) }

    // Debounce quality slider persistence to prevent lag
    LaunchedEffect(defaultQuality) {
        delay(300L)
        AppSettingsManager.setDefaultQuality(context, defaultQuality)
    }

    var saveUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable permission so we can write there later
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
                // Some providers may reject persistable flags; fallback still stores chosen URI.
            }
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiquidGlassTopBar(title = "Settings", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.fillMaxWidth())

        // ── Theme Mode Selector ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), label)
                BasicText("Appearance", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            // Segmented pill selector
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                data class ThemeOption(val idx: Int, val label: String, val icon: ImageVector)
                val options = listOf(
                    ThemeOption(0, "System", Icons.Rounded.PhoneAndroid),
                    ThemeOption(1, "Light", Icons.Rounded.LightMode),
                    ThemeOption(2, "Dark", Icons.Rounded.DarkMode)
                )
                options.forEach { option ->
                    val isSelected = themeMode == option.idx
                    val pillColor = when {
                        isSelected && option.idx == 2 -> Color(0xFF5C6BC0)
                        isSelected && option.idx == 1 -> Color(0xFFFFA726)
                        isSelected -> Color(0xFF0088FF)
                        else -> Color.Transparent
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) pillColor.copy(0.18f) else Color.Transparent)
                            .clickable { onThemeModeChanged(option.idx) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            option.icon, null,
                            Modifier.size(16.dp),
                            if (isSelected) pillColor else sub
                        )
                        Spacer(Modifier.width(6.dp))
                        BasicText(
                            option.label,
                            style = TextStyle(
                                if (isSelected) pillColor else sub,
                                12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            BasicText(
                when (themeMode) {
                    1 -> "Always use light theme"
                    2 -> "Always use dark theme"
                    else -> "Follows your device's system settings"
                },
                style = TextStyle(sub, 12.sp)
            )
        }

        // ── Save Location ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(22.dp), label)
                BasicText("Save Location", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLight) Color.Black.copy(0.03f) else Color.White.copy(0.05f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(Color(0xFF1976D2).copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp), Color(0xFF1976D2))
                }
                Column(Modifier.weight(1f)) {
                    BasicText(
                        if (saveUri != null) "Custom Location" else "Default (Downloads)",
                        style = TextStyle(label, 14.sp, fontWeight = FontWeight.Medium)
                    )
                    if (saveUri != null) {
                        val path = saveUri!!.lastPathSegment?.replace("primary:", "") ?: saveUri.toString()
                        BasicText(path, style = TextStyle(sub, 11.sp))
                    } else {
                        BasicText("PDFs saved to Downloads folder", style = TextStyle(sub, 11.sp))
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(22.dp), label)
                BasicText("File Handling", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            SettingsToggleRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "Auto-Compress",
                desc = "Compress PDFs automatically on import",
                checked = autoCompress,
                onCheckedChange = { autoCompress = it; AppSettingsManager.setAutoCompress(context, it) },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

            // Separator
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp)
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
            )

            SettingsToggleRow(
                icon = Icons.Rounded.FileCopy,
                title = "Keep Original",
                desc = "Preserve original file after editing",
                checked = keepOriginal,
                onCheckedChange = { keepOriginal = it; AppSettingsManager.setKeepOriginal(context, it) },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp)
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
            )

            SettingsToggleRow(
                icon = Icons.Rounded.Notifications,
                title = "Notifications",
                desc = "Show notification when tasks complete",
                checked = notifications,
                onCheckedChange = { notifications = it; AppSettingsManager.setNotifications(context, it) },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )
        }

        // ── Default Quality ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.HighQuality, null, Modifier.size(22.dp), Color(0xFF1976D2))
                BasicText("Default Quality", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            // Quality presets as segmented glass pills
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                data class QualityPreset(val target: Float, val title: String, val desc: String, val color: Color)
                val qualities = listOf(
                    QualityPreset(0.15f, "Low", "Smallest", Color(0xFF4CAF50)),
                    QualityPreset(0.5f, "Medium", "Balanced", Color(0xFF2196F3)),
                    QualityPreset(0.85f, "High", "Best", Color(0xFF9C27B0))
                )
                qualities.forEach { preset ->
                    val isSelected = when {
                        preset.target < 0.33f -> defaultQuality < 0.33f
                        preset.target < 0.66f -> defaultQuality in 0.33f..0.66f
                        else -> defaultQuality > 0.66f
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) preset.color.copy(0.15f) else Color.Transparent)
                            .clickable { defaultQuality = preset.target }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        BasicText(
                            preset.title,
                            style = TextStyle(
                                if (isSelected) preset.color else sub,
                                13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                        BasicText(
                            preset.desc,
                            style = TextStyle(
                                if (isSelected) preset.color.copy(0.7f) else sub.copy(0.6f),
                                10.sp
                            )
                        )
                    }
                }
            }

            // Fine-tune slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("Fine-tune", style = TextStyle(sub, 12.sp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1976D2).copy(0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        BasicText(
                            "${(defaultQuality * 100).toInt()}%",
                            style = TextStyle(Color(0xFF1976D2), 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                LiquidSlider(
                    value = { defaultQuality },
                    onValueChange = {
                        val snapped = ((it * 100f).toInt() / 100f).coerceIn(0f, 1f)
                        if (snapped != defaultQuality) {
                            defaultQuality = snapped
                        }
                    },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.005f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("Smaller file", style = TextStyle(sub.copy(0.6f), 11.sp))
                    BasicText("Better quality", style = TextStyle(sub.copy(0.6f), 11.sp))
                }
            }
        }

        // ── About & Open Source ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(Color(0xFF0088FF).copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Info, null, Modifier.size(28.dp), Color(0xFF0088FF))
            }
            BasicText("ClearPDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.Bold))
            BasicText("Version 1.0.0", style = TextStyle(sub, 13.sp))
            BasicText(
                "Made by Chethan616 with ❤",
                style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center)
            )

            Spacer(Modifier.height(4.dp))

            // Star CTA
            LiquidButton(
                onClick = openRepo,
                backdrop = backdrop,
                tint = Color(0xFFFFC107),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(18.dp), Color.White)
                    BasicText("Star on GitHub", style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }

            BasicText(
                "ClearPDF is open source",
                style = TextStyle(sub.copy(0.7f), 11.sp, textAlign = TextAlign.Center)
            )
        }

        // ── Licenses ──
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Code, null, Modifier.size(22.dp), label)
                BasicText("Open Source Licenses", style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            LicenseItem(
                name = "AndroidLiquidGlass",
                author = "Kyant",
                license = "Apache License 2.0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass",
                labelColor = label,
                subColor = sub
            )

            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
            )

            BasicText(
                "Licensed under the Apache License, Version 2.0.\nYou may obtain a copy at apache.org/licenses/LICENSE-2.0",
                style = TextStyle(sub.copy(0.7f), 11.sp, lineHeight = 16.sp)
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(labelColor.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), labelColor)
        }
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

@Composable
private fun LicenseItem(
    name: String,
    author: String,
    license: String,
    url: String,
    labelColor: Color,
    subColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(name, style = TextStyle(labelColor, 14.sp, fontWeight = FontWeight.Medium))
            BasicText("by $author", style = TextStyle(subColor, 12.sp))
        }
        BasicText(license, style = TextStyle(subColor, 11.sp))
        BasicText(url, style = TextStyle(Color(0xFF0088FF), 11.sp))
    }
}

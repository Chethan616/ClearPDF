package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    themeMode: Int = 0,
    onThemeModeChanged: (Int) -> Unit = {},
    showWallpaper: Boolean = true,
    onShowWallpaperChanged: (Boolean) -> Unit = {},
    hasCustomWallpaper: Boolean = false,
    onCustomWallpaperChanged: (String?) -> Unit = {},
    selectedLocale: String = "en",
    onLocaleChanged: (String) -> Unit = {}
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

    // Pick a custom background image from the gallery (persistable so it survives restarts).
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            AppSettingsManager.setCustomWallpaper(context, uri.toString())
            onCustomWallpaperChanged(uri.toString())
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 18f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsTopBarOffsetY"
    )

    val panel1Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 60, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel1Alpha"
    )
    val panel1OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 20f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 60, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel1OffsetY"
    )

    val panel2Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 620, delayMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel2Alpha"
    )
    val panel2OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 620, delayMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel2OffsetY"
    )

    val panel3Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 640, delayMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel3Alpha"
    )
    val panel3OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 28f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 640, delayMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel3OffsetY"
    )

    val panel4Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 660, delayMillis = 240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel4Alpha"
    )
    val panel4OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 32f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 660, delayMillis = 240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel4OffsetY"
    )

    val panel5Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 680, delayMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel5Alpha"
    )
    val panel5OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 36f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 680, delayMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel5OffsetY"
    )

    val panel6Alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700, delayMillis = 360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel6Alpha"
    )
    val panel6OffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 40f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700, delayMillis = 360, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanel6OffsetY"
    )

    GlassScreenScaffold(
        backdrop = backdrop,
        header = { headerBackdrop ->
            // No back button here, so the pill centres against the full width. Fade only — the pill
            // is glass, and translating glass re-runs its blur+lens.
            GlassScreenHeaderRow(
                title = stringResource(R.string.settings_title),
                backdrop = headerBackdrop,
                onBack = null,
                modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
            )
        }
    ) { contentPadding ->
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Theme Mode Selector ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel1Alpha
                    translationY = panel1OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), label)
                BasicText(stringResource(R.string.settings_appearance), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            // Liquid-glass refracted segmented control
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                data class ThemeOption(val idx: Int, val label: String, val icon: ImageVector, val activeColor: Color)
                val options = listOf(
                    ThemeOption(0, stringResource(R.string.settings_theme_auto), Icons.Rounded.PhoneAndroid, Color(0xFF0088FF)),
                    ThemeOption(1, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode, Color(0xFFFFA726)),
                    ThemeOption(2, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode, Color(0xFF7C4DFF))
                )
                options.forEach { option ->
                    val isSelected = themeMode == option.idx
                    val cc = if (isSelected) Color.White else (if (isLight) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
                    LiquidButton(
                        onClick = { onThemeModeChanged(option.idx) },
                        backdrop = backdrop,
                        tint = if (isSelected) option.activeColor else Color.Unspecified,
                        surfaceColor = if (isSelected) Color.Unspecified else (if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.10f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                        ) {
                            Icon(option.icon, null, Modifier.size(16.dp), cc)
                            BasicText(
                                option.label,
                                style = TextStyle(cc, 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            BasicText(
                when (themeMode) {
                    1 -> stringResource(R.string.settings_theme_light_desc)
                    2 -> stringResource(R.string.settings_theme_dark_desc)
                    else -> stringResource(R.string.settings_theme_auto_desc)
                },
                style = TextStyle(sub, 12.sp)
            )
        }

        // ── Language ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel1Alpha
                    translationY = panel1OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Language, null, Modifier.size(22.dp), label)
                BasicText(stringResource(R.string.settings_language), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                data class LangOption(val code: String, val label: String)
                val langs = listOf(
                    LangOption("en", stringResource(R.string.language_english)),
                    LangOption("pt-BR", stringResource(R.string.language_portuguese))
                )
                val accent = Color(0xFF0088FF)
                langs.forEach { opt ->
                    val isSelected = selectedLocale == opt.code
                    val cc = if (isSelected) Color.White else (if (isLight) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
                    LiquidButton(
                        onClick = { onLocaleChanged(opt.code) },
                        backdrop = backdrop,
                        tint = if (isSelected) accent else Color.Unspecified,
                        surfaceColor = if (isSelected) Color.Unspecified else (if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.10f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicText(
                            opt.label,
                            style = TextStyle(cc, 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ── Save Location ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel2Alpha
                    translationY = panel2OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(22.dp), Color(0xFF1976D2))
                BasicText(stringResource(R.string.settings_save_location), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isLight) Color.Black.copy(0.03f) else Color.White.copy(0.05f))
                    .border(1.dp, if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1976D2).copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp), Color(0xFF1976D2))
                }
                Column(Modifier.weight(1f)) {
                    BasicText(
                        if (saveUri != null) stringResource(R.string.settings_custom_directory) else stringResource(R.string.settings_default_directory),
                        style = TextStyle(label, 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                    val path = if (saveUri != null) {
                        saveUri!!.lastPathSegment?.replace("primary:", "") ?: saveUri.toString()
                    } else stringResource(R.string.settings_default_path)
                    BasicText(path, style = TextStyle(sub, 12.sp))
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidButton(
                    onClick = { folderPicker.launch(null) },
                    backdrop = backdrop,
                    tint = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(stringResource(R.string.settings_change_folder), style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.SemiBold))
                }
                if (saveUri != null) {
                    LiquidButton(
                        onClick = {
                            SaveLocationManager.clearSaveLocation(context)
                            saveUri = null
                        },
                        backdrop = backdrop,
                        surfaceColor = Color.White.copy(0.08f)
                    ) {
                        BasicText(stringResource(R.string.settings_reset), style = TextStyle(text, 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        // ── File Handling ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel3Alpha
                    translationY = panel3OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Description, null, Modifier.size(22.dp), label)
                BasicText(stringResource(R.string.settings_file_handling), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            SettingsToggleRow(
                icon = Icons.Rounded.Compress,
                title = stringResource(R.string.settings_auto_compress),
                desc = stringResource(R.string.settings_auto_compress_desc),
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
                title = stringResource(R.string.settings_keep_original),
                desc = stringResource(R.string.settings_keep_original_desc),
                checked = keepOriginal,
                onCheckedChange = { keepOriginal = it; AppSettingsManager.setKeepOriginal(context, it) },
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

        }

        // ── Default Quality ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel4Alpha
                    translationY = panel4OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.HighQuality, null, Modifier.size(22.dp), Color(0xFF1976D2))
                    BasicText(stringResource(R.string.settings_compression_quality), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1976D2).copy(0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    BasicText(
                        "${(defaultQuality * 100).toInt()}%",
                        style = TextStyle(Color(0xFF1976D2), 13.sp, fontWeight = FontWeight.Bold)
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
                BasicText(stringResource(R.string.settings_smaller_size), style = TextStyle(sub.copy(0.7f), 11.sp))
                BasicText(stringResource(R.string.settings_higher_quality), style = TextStyle(sub.copy(0.7f), 11.sp))
            }
        }

        // ── Personalization ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel4Alpha
                    translationY = panel4OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Wallpaper, null, Modifier.size(22.dp), label)
                BasicText(stringResource(R.string.settings_personalization), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
            }

            SettingsToggleRow(
                icon = Icons.Rounded.Wallpaper,
                title = stringResource(R.string.settings_background),
                desc = stringResource(R.string.settings_background_desc),
                checked = showWallpaper,
                onCheckedChange = onShowWallpaperChanged,
                backdrop = backdrop,
                labelColor = label,
                subColor = sub
            )

            // When the background is on, let the user pick a custom image + reset to default.
            if (showWallpaper) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, start = 46.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidButton(
                        onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                        backdrop = backdrop,
                        tint = Color(0xFF0088FF),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp), Color.White)
                            BasicText(stringResource(R.string.settings_bg_gallery), style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        }
                    }
                    LiquidIconButton(
                        onClick = { AppSettingsManager.clearCustomWallpaper(context); onCustomWallpaperChanged(null) },
                        backdrop = backdrop,
                        surfaceColor = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.10f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.settings_reset), Modifier.size(18.dp), if (hasCustomWallpaper) label else label.copy(0.4f))
                    }
                }
            }
        }

        // ── About & Open Source ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel5Alpha
                    translationY = panel5OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(24.dp),
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
            BasicText(stringResource(R.string.settings_version), style = TextStyle(sub, 13.sp))
            BasicText(
                stringResource(R.string.settings_made_by),
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
                    BasicText(stringResource(R.string.settings_star_github), style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }

            BasicText(
                stringResource(R.string.settings_open_source),
                style = TextStyle(sub.copy(0.7f), 11.sp, textAlign = TextAlign.Center)
            )
        }

        // ── Licenses ──
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panel6Alpha
                    translationY = panel6OffsetY * density
                }
                .liquidGlassSection(isLight)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Code, null, Modifier.size(22.dp), label)
                BasicText(stringResource(R.string.settings_licenses), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
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

            LicenseItem(
                name = "Pdf_Tools",
                author = "Karna14314",
                license = "PDF viewer zoom/pan reference",
                url = "https://github.com/Karna14314/Pdf_Tools",
                labelColor = label,
                subColor = sub
            )

            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
            )

            BasicText(
                stringResource(R.string.settings_license_notice),
                style = TextStyle(sub.copy(0.7f), 11.sp, lineHeight = 16.sp)
            )
        }

        // Clear the floating bottom navigation bar + system nav inset.
        Spacer(Modifier.height(120.dp))
    }
    }
}

private fun Modifier.liquidGlassSection(isLight: Boolean): Modifier {
    val containerColor = if (isLight) Color.White.copy(0.68f) else Color(0xFF161820).copy(0.72f)
    val borderColor = if (isLight) Color.White.copy(0.80f) else Color.White.copy(0.12f)
    return this
        .clip(RoundedCornerShape(24.dp))
        .background(containerColor)
        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
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
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
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
            BasicText(stringResource(R.string.settings_license_author, author), style = TextStyle(subColor, 12.sp))
        }
        BasicText(license, style = TextStyle(subColor, 11.sp))
        BasicText(url, style = TextStyle(Color(0xFF0088FF), 11.sp))
    }
}

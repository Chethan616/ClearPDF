package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.UISensor
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

/**
 * Settings — a premium, spacious "inset grouped" layout on real Liquid Glass. Each section is
 * a small uppercase label above a single translucent, backdrop-blurred glass container holding
 * compact native-style rows (colored icon tile · title/description · right-aligned control),
 * separated by inset hairlines. The wallpaper reads through the glass; nothing is a solid slab.
 */

// System accent palette for the icon tiles.
private val SysBlue   = Color(0xFF0A84FF)
private val SysGreen  = Color(0xFF32D74B)
private val SysIndigo = Color(0xFF5E5CE6)
private val SysOrange = Color(0xFFFF9F0A)
private val SysPurple = Color(0xFFBF5AF2)
private val SysTeal   = Color(0xFF64D2FF)
private val SysYellow = Color(0xFFFFD60A)
private val SysGray   = Color(0xFF8E8E93)

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    themeMode: Int = 0,
    onThemeModeChanged: (Int) -> Unit = {},
    showWallpaper: Boolean = true,
    onShowWallpaperChanged: (Boolean) -> Unit = {},
    selectedLocale: String = "en",
    onLocaleChanged: (String) -> Unit = {}
) {
    val isLight = !isDarkMode
    val label     = if (isLight) Color(0xFF0B0B0F) else Color(0xFFF7F7FA)
    val secondary = if (isLight) Color(0xFF3C3C43).copy(0.62f) else Color(0xFFEBEBF5).copy(0.62f)
    val tertiary  = if (isLight) Color(0xFF3C3C43).copy(0.42f) else Color(0xFFEBEBF5).copy(0.40f)
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val uiSensor = rememberUISensor()

    var autoCompress   by remember { mutableStateOf(AppSettingsManager.getAutoCompress(context)) }
    var keepOriginal   by remember { mutableStateOf(AppSettingsManager.getKeepOriginal(context)) }
    var defaultQuality by remember { mutableFloatStateOf(AppSettingsManager.getDefaultQuality(context)) }
    LaunchedEffect(defaultQuality) {
        delay(300L); AppSettingsManager.setDefaultQuality(context, defaultQuality)
    }

    var saveUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            val displayPath = uri.lastPathSegment?.replace("primary:", "") ?: uri.toString()
            SaveLocationManager.setSaveLocation(context, uri, displayPath)
            saveUri = uri
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val fade by animateFloatAsState(if (isVisible) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "settingsFade")
    val rise by animateFloatAsState(if (isVisible) 0f else 14f, tween(420, easing = FastOutSlowInEasing), label = "settingsRise")

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { alpha = fade; translationY = rise * density }
    ) {
        // Large navigation title — strong but not space-hungry.
        BasicText(
            stringResource(R.string.settings_title),
            style = TextStyle(label, 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp)
        )

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_appearance), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>({
                SegmentedRow(
                    options = listOf(
                        stringResource(R.string.settings_theme_auto),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark)
                    ),
                    selectedIndex = themeMode, isLight = isLight, label = label, onSelect = onThemeModeChanged
                )
            })
        }

        // ── Language ────────────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_language), backdrop, uiSensor, secondary, isLight) {
            val codes = listOf("en", "pt-BR")
            listOf<@Composable () -> Unit>({
                SegmentedRow(
                    options = listOf(stringResource(R.string.language_english), stringResource(R.string.language_portuguese)),
                    selectedIndex = codes.indexOf(selectedLocale).coerceAtLeast(0),
                    isLight = isLight, label = label, onSelect = { onLocaleChanged(codes[it]) }
                )
            })
        }

        // ── Storage ─────────────────────────────────────────────────────────
        val isDefault = saveUri == null
        val customPath = saveUri?.let { it.lastPathSegment?.replace("primary:", "") ?: it.toString() }
        SettingsGroup(stringResource(R.string.settings_save_location), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        icon = Icons.Rounded.Download, iconTint = SysBlue,
                        title = stringResource(R.string.settings_save_downloads),
                        subtitle = stringResource(R.string.settings_default_path),
                        label = label, secondary = secondary,
                        trailing = { if (isDefault) SelectedCheck() },
                        onClick = { if (!isDefault) { SaveLocationManager.clearSaveLocation(context); saveUri = null } }
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Rounded.FolderOpen, iconTint = SysOrange,
                        title = stringResource(R.string.settings_save_custom),
                        subtitle = if (!isDefault) customPath else null,
                        label = label, secondary = secondary,
                        trailing = { if (!isDefault) SelectedCheck() else Chevron(tertiary) },
                        onClick = { folderPicker.launch(null) }
                    )
                }
            )
        }

        // ── File Processing ─────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_file_handling), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>(
                {
                    ToggleRow(
                        icon = Icons.Rounded.Compress, iconTint = SysGreen,
                        title = stringResource(R.string.settings_auto_compress),
                        subtitle = stringResource(R.string.settings_auto_compress_desc),
                        checked = autoCompress, label = label, secondary = secondary, backdrop = backdrop,
                        onCheckedChange = { autoCompress = it; AppSettingsManager.setAutoCompress(context, it) }
                    )
                },
                {
                    ToggleRow(
                        icon = Icons.Rounded.FileCopy, iconTint = SysTeal,
                        title = stringResource(R.string.settings_keep_original),
                        subtitle = stringResource(R.string.settings_keep_original_desc),
                        checked = keepOriginal, label = label, secondary = secondary, backdrop = backdrop,
                        onCheckedChange = { keepOriginal = it; AppSettingsManager.setKeepOriginal(context, it) }
                    )
                },
                {
                    QualityRow(defaultQuality, label, secondary, tertiary, backdrop) {
                        val snapped = ((it * 100f).toInt() / 100f).coerceIn(0f, 1f)
                        if (snapped != defaultQuality) defaultQuality = snapped
                    }
                }
            )
        }

        // ── Personalization ─────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_personalization), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>({
                ToggleRow(
                    icon = Icons.Rounded.Wallpaper, iconTint = SysPurple,
                    title = stringResource(R.string.settings_background),
                    subtitle = stringResource(R.string.settings_background_desc),
                    checked = showWallpaper, label = label, secondary = secondary, backdrop = backdrop,
                    onCheckedChange = onShowWallpaperChanged
                )
            })
        }

        // ── About ───────────────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_about), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        icon = Icons.Rounded.Star, iconTint = SysYellow,
                        title = stringResource(R.string.settings_star_github),
                        label = label, secondary = secondary,
                        trailing = { Chevron(tertiary) },
                        onClick = { openExternalLink(context, GitHubStarPromptManager.REPO_URL) }
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Rounded.Shield, iconTint = SysGreen,
                        title = stringResource(R.string.settings_privacy_policy),
                        label = label, secondary = secondary,
                        trailing = { Chevron(tertiary) },
                        onClick = { openExternalLink(context, "https://github.com/Chethan616/ClearPDF/blob/main/PRIVACY.md") }
                    )
                }
            )
        }

        // ── Open Source ─────────────────────────────────────────────────────
        SettingsGroup(stringResource(R.string.settings_licenses), backdrop, uiSensor, secondary, isLight) {
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        icon = Icons.Rounded.Code, iconTint = SysGray,
                        title = "AndroidLiquidGlass",
                        subtitle = stringResource(R.string.settings_license_author, "Kyant") + " · Apache 2.0",
                        label = label, secondary = secondary,
                        trailing = { Chevron(tertiary) },
                        onClick = { openExternalLink(context, "https://github.com/Kyant0/AndroidLiquidGlass") }
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Rounded.Code, iconTint = SysGray,
                        title = "Pdf_Tools",
                        subtitle = stringResource(R.string.settings_license_author, "Karna14314"),
                        label = label, secondary = secondary,
                        trailing = { Chevron(tertiary) },
                        onClick = { openExternalLink(context, "https://github.com/Karna14314/Pdf_Tools") }
                    )
                }
            )
        }

        // ── Version footer ──────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            BasicText("${stringResource(R.string.settings_version_label)} ${appVersion()}", style = TextStyle(secondary, 13.sp, fontWeight = FontWeight.Medium))
            BasicText(stringResource(R.string.settings_made_by), style = TextStyle(tertiary, 12.sp, textAlign = TextAlign.Center))
        }

        Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp))
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

/** A section: a small uppercase label above one real-glass container of compact rows. */
@Composable
private fun SettingsGroup(
    header: String,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    secondary: Color,
    isLight: Boolean,
    rows: () -> List<@Composable () -> Unit>
) {
    val separator = if (isLight) Color(0xFF3C3C43).copy(0.14f) else Color(0xFFFFFFFF).copy(0.10f)
    val list = rows()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicText(
            header.uppercase(),
            style = TextStyle(secondary, 12.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp)
        )
        // Outer Box carries the glass + soft shadow (from liquidGlassPanel); the inner Column
        // clips rows/ripples/separators to the rounded shape without clipping the drop shadow.
        Box(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))) {
                list.forEachIndexed { i, row ->
                    if (i > 0) Box(Modifier.padding(start = 54.dp, end = 14.dp).fillMaxWidth().height(0.6.dp).background(separator))
                    row()
                }
            }
        }
    }
}

/** Compact native-style row: leading icon tile · title (+ optional description) · trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector?,
    iconTint: Color,
    title: String,
    label: Color,
    secondary: Color,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) IconTile(icon, iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(title, style = TextStyle(label, 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = TextStyle(secondary, 12.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** A row whose trailing control is the Liquid Glass switch. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    label: Color,
    secondary: Color,
    backdrop: LayerBackdrop,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRow(
        icon = icon, iconTint = iconTint, title = title, subtitle = subtitle,
        label = label, secondary = secondary,
        trailing = { LiquidToggle(selected = { checked }, onSelect = onCheckedChange, backdrop = backdrop) },
        onClick = { onCheckedChange(!checked) }
    )
}

/** Compression-quality control cell: icon · title · % on one line, slider + range labels below. */
@Composable
private fun QualityRow(
    quality: Float,
    label: Color,
    secondary: Color,
    tertiary: Color,
    backdrop: LayerBackdrop,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Rounded.HighQuality, SysIndigo)
            BasicText(stringResource(R.string.settings_compression_quality), style = TextStyle(label, 16.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            BasicText("${(quality * 100).toInt()}%", style = TextStyle(label, 15.sp, fontWeight = FontWeight.SemiBold))
        }
        LiquidSlider(
            value = { quality }, onValueChange = onChange,
            valueRange = 0f..1f, visibilityThreshold = 0.005f,
            backdrop = backdrop, modifier = Modifier.fillMaxWidth().padding(start = 41.dp)
        )
        Row(Modifier.fillMaxWidth().padding(start = 41.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(stringResource(R.string.settings_smaller_size), style = TextStyle(tertiary, 11.sp))
            BasicText(stringResource(R.string.settings_higher_quality), style = TextStyle(tertiary, 11.sp))
        }
    }
}

/** Rounded-square colored icon tile (SF-Symbol style). */
@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(17.dp), Color.White)
    }
}

@Composable
private fun Chevron(color: Color) = Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), color)

@Composable
private fun SelectedCheck() = Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), SysBlue)

/** Compact glass segmented control with a refined selected pill. */
@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    isLight: Boolean,
    label: Color,
    onSelect: (Int) -> Unit
) {
    val track = if (isLight) Color(0xFF767680).copy(0.14f) else Color(0xFF767680).copy(0.28f)
    val pill = if (isLight) Color.White.copy(0.92f) else Color(0xFF6E6E73)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val selected = i == selectedIndex
            val fade by animateFloatAsState(if (selected) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "seg$i")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pill.copy(alpha = pill.alpha * fade))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    opt,
                    style = TextStyle(label.copy(alpha = if (selected) 1f else 0.72f), 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
}

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

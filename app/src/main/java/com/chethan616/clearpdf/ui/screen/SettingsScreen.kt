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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.glassSection
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

/**
 * Settings — rebuilt from scratch in the iOS-Settings "inset grouped" idiom (Apple HIG):
 * a large navigation title, grouped rounded cards with uppercase section headers and gray
 * footnotes, colored SF-Symbol-style icon tiles, inset hairline separators, and native-feeling
 * segmented controls / switches. The card surface uses the app's shared glass so it reads well
 * over both the wallpaper and the flat background (see the Background toggle at the bottom).
 */

// iOS system accent palette for the icon tiles.
private val SysBlue   = Color(0xFF007AFF)
private val SysGreen  = Color(0xFF34C759)
private val SysIndigo = Color(0xFF5856D6)
private val SysOrange = Color(0xFFFF9500)
private val SysPurple = Color(0xFFAF52DE)
private val SysRed    = Color(0xFFFF3B30)
private val SysTeal   = Color(0xFF5AC8FA)
private val SysYellow = Color(0xFFFFCC00)
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
    // Apple label colors.
    val label       = if (isLight) Color(0xFF000000) else Color(0xFFFFFFFF)
    val secondary   = if (isLight) Color(0xFF3C3C43).copy(0.60f) else Color(0xFFEBEBF5).copy(0.60f)
    val tertiary    = if (isLight) Color(0xFF3C3C43).copy(0.45f) else Color(0xFFEBEBF5).copy(0.45f)
    val context = LocalContext.current
    val density = LocalDensity.current.density

    var autoCompress   by remember { mutableStateOf(AppSettingsManager.getAutoCompress(context)) }
    var keepOriginal   by remember { mutableStateOf(AppSettingsManager.getKeepOriginal(context)) }
    var notifications  by remember { mutableStateOf(AppSettingsManager.getNotifications(context)) }
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
    val fade by animateFloatAsState(
        if (isVisible) 1f else 0f,
        tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "settingsFade"
    )
    val rise by animateFloatAsState(
        if (isVisible) 0f else 14f,
        tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "settingsRise"
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { alpha = fade; translationY = rise * density }
    ) {
        // Large navigation title.
        BasicText(
            stringResource(R.string.settings_title),
            style = TextStyle(label, 34.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp)
        )

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsGroup(header = stringResource(R.string.settings_appearance), isLight = isLight, secondary = secondary) {
            listOf<@Composable () -> Unit>(
                {
                    SegmentedRow(
                        options = listOf(
                            stringResource(R.string.settings_theme_auto),
                            stringResource(R.string.settings_theme_light),
                            stringResource(R.string.settings_theme_dark)
                        ),
                        selectedIndex = themeMode,
                        isLight = isLight,
                        label = label,
                        onSelect = onThemeModeChanged
                    )
                }
            )
        }

        // ── Language ────────────────────────────────────────────────────────
        SettingsGroup(header = stringResource(R.string.settings_language), isLight = isLight, secondary = secondary) {
            listOf<@Composable () -> Unit>(
                {
                    val codes = listOf("en", "pt-BR")
                    SegmentedRow(
                        options = listOf(
                            stringResource(R.string.language_english),
                            stringResource(R.string.language_portuguese)
                        ),
                        selectedIndex = codes.indexOf(selectedLocale).coerceAtLeast(0),
                        isLight = isLight,
                        label = label,
                        onSelect = { onLocaleChanged(codes[it]) }
                    )
                }
            )
        }

        // ── Files ───────────────────────────────────────────────────────────
        SettingsGroup(
            header = stringResource(R.string.settings_save_location),
            isLight = isLight, secondary = secondary,
            footer = if (saveUri != null)
                (saveUri!!.lastPathSegment?.replace("primary:", "") ?: saveUri.toString())
            else stringResource(R.string.settings_default_path)
        ) {
            listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        icon = Icons.Rounded.Download, iconTint = SysBlue,
                        title = stringResource(R.string.settings_save_downloads),
                        label = label, secondary = secondary,
                        trailing = { if (saveUri == null) SelectedCheck() },
                        onClick = { if (saveUri != null) { SaveLocationManager.clearSaveLocation(context); saveUri = null } }
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Rounded.FolderOpen, iconTint = SysOrange,
                        title = stringResource(R.string.settings_save_custom),
                        label = label, secondary = secondary,
                        trailing = { if (saveUri != null) SelectedCheck() else Chevron(tertiary) },
                        onClick = { folderPicker.launch(null) }
                    )
                }
            )
        }

        SettingsGroup(header = stringResource(R.string.settings_file_handling), isLight = isLight, secondary = secondary) {
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
                    ToggleRow(
                        icon = Icons.Rounded.Notifications, iconTint = SysRed,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_desc),
                        checked = notifications, label = label, secondary = secondary, backdrop = backdrop,
                        onCheckedChange = { notifications = it; AppSettingsManager.setNotifications(context, it) }
                    )
                },
                {
                    // Quality slider cell (label + value on top, slider below — iOS control-cell style).
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(Icons.Rounded.HighQuality, SysIndigo)
                            BasicText(stringResource(R.string.settings_compression_quality), style = TextStyle(label, 17.sp), modifier = Modifier.weight(1f))
                            BasicText("${(defaultQuality * 100).toInt()}%", style = TextStyle(secondary, 16.sp))
                        }
                        LiquidSlider(
                            value = { defaultQuality },
                            onValueChange = {
                                val snapped = ((it * 100f).toInt() / 100f).coerceIn(0f, 1f)
                                if (snapped != defaultQuality) defaultQuality = snapped
                            },
                            valueRange = 0f..1f, visibilityThreshold = 0.005f,
                            backdrop = backdrop, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        // ── Personalization (Background toggle — user experiment) ────────────
        SettingsGroup(
            header = stringResource(R.string.settings_personalization),
            isLight = isLight, secondary = secondary
        ) {
            listOf<@Composable () -> Unit>(
                {
                    ToggleRow(
                        icon = Icons.Rounded.Wallpaper, iconTint = SysPurple,
                        title = stringResource(R.string.settings_background),
                        subtitle = stringResource(R.string.settings_background_desc),
                        checked = showWallpaper, label = label, secondary = secondary, backdrop = backdrop,
                        onCheckedChange = onShowWallpaperChanged
                    )
                }
            )
        }

        // ── About ───────────────────────────────────────────────────────────
        SettingsGroup(
            header = stringResource(R.string.settings_about),
            isLight = isLight, secondary = secondary,
            footer = stringResource(R.string.settings_made_by)
        ) {
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
                },
                {
                    SettingsRow(
                        icon = null, iconTint = SysGray,
                        title = stringResource(R.string.settings_version_label),
                        label = label, secondary = secondary,
                        trailing = { BasicText(appVersion(), style = TextStyle(secondary, 16.sp)) }
                    )
                }
            )
        }

        // ── Licenses ────────────────────────────────────────────────────────
        SettingsGroup(
            header = stringResource(R.string.settings_licenses),
            isLight = isLight, secondary = secondary,
            footer = stringResource(R.string.settings_license_notice)
        ) {
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

        Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp))
    }
}

// ── Apple-HIG building blocks ────────────────────────────────────────────────

/** A grouped, inset card with an uppercase header and optional gray footnote. */
@Composable
private fun SettingsGroup(
    header: String?,
    isLight: Boolean,
    secondary: Color,
    footer: String? = null,
    rows: () -> List<@Composable () -> Unit>
) {
    val separator = if (isLight) Color(0xFF3C3C43).copy(0.18f) else Color(0xFF545458).copy(0.40f)
    val list = rows()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            BasicText(
                header.uppercase(),
                style = TextStyle(secondary, 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 7.dp)
            )
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Column(Modifier.fillMaxWidth().glassSection(isLight, radius = 16.dp)) {
            list.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.7.dp).background(separator))
                row()
            }
        }
        if (footer != null) {
            BasicText(
                footer,
                style = TextStyle(secondary, 12.sp, lineHeight = 16.sp),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp)
            )
        }
    }
}

/** Standard tappable row: leading icon tile, title (+ optional subtitle), trailing control. */
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
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) IconTile(icon, iconTint)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(label, 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = TextStyle(secondary, 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** A row whose trailing control is an iOS switch. */
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

/** The iconic iOS Settings rounded-square colored icon tile. */
@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(18.dp), Color.White)
    }
}

@Composable
private fun Chevron(color: Color) {
    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), color)
}

@Composable
private fun SelectedCheck() {
    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), SysBlue)
}

/** iOS UISegmentedControl-style control, sized as a full-width grouped cell. */
@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    isLight: Boolean,
    label: Color,
    onSelect: (Int) -> Unit
) {
    val trackColor = if (isLight) Color(0xFF767680).copy(0.12f) else Color(0xFF767680).copy(0.24f)
    val selectedPill = if (isLight) Color.White else Color(0xFF636366)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(trackColor)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) selectedPill else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    opt,
                    style = TextStyle(
                        label, 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
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
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
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

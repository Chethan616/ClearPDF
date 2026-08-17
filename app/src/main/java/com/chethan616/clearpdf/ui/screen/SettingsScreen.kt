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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

// ── Shared design tokens (Item 4 spirit, scoped to Settings) ────────────────
// Centralised so every section shares the same rhythm; no more per-block magic
// numbers drifting apart.
private val SectionRadius   = 24.dp
private val InnerRadius     = 16.dp
private val SectionGap      = 16.dp
private val SectionPadding  = 20.dp
private val TitleSize       = 16.sp
private val AccentBlue      = Color(0xFF0088FF)
private val AccentGreen     = Color(0xFF00C853)

@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    themeMode: Int = 0,
    onThemeModeChanged: (Int) -> Unit = {},
    selectedLocale: String = "en",
    onLocaleChanged: (String) -> Unit = {}
) {
    val isLight = !isDarkMode
    val text  = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub   = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val label = if (isLight) Color(0xFF444444) else Color(0xFFCCCCCC)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val openRepo = remember(context) {
        { openExternalLink(context, GitHubStarPromptManager.REPO_URL) }
    }

    // ── Backing state (unchanged persistence calls) ─────────────────────────
    var autoCompress   by remember { mutableStateOf(AppSettingsManager.getAutoCompress(context)) }
    var keepOriginal   by remember { mutableStateOf(AppSettingsManager.getKeepOriginal(context)) }
    var notifications  by remember { mutableStateOf(AppSettingsManager.getNotifications(context)) }
    var defaultQuality by remember { mutableFloatStateOf(AppSettingsManager.getDefaultQuality(context)) }

    // Debounce quality slider persistence to prevent lag.
    LaunchedEffect(defaultQuality) {
        delay(300L)
        AppSettingsManager.setDefaultQuality(context, defaultQuality)
    }

    var saveUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
                // Some providers reject persistable flags; fallback still stores the chosen URI.
            }
            val displayPath = uri.lastPathSegment?.replace("primary:", "") ?: uri.toString()
            SaveLocationManager.setSaveLocation(context, uri, displayPath)
            saveUri = uri
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SectionGap)
    ) {
        Box(reveal(0, isVisible, density)) {
            LiquidGlassTopBar(
                title = stringResource(R.string.settings_title),
                backdrop = backdrop,
                uiSensor = uiSensor,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_appearance),
            icon = Icons.Rounded.Tune,
            iconTint = AccentBlue,
            isLight = isLight,
            titleColor = text,
            modifier = reveal(1, isVisible, density)
        ) {
            SegmentedSelector(
                options = listOf(
                    SegOption("0", stringResource(R.string.settings_theme_auto),  Icons.Rounded.PhoneAndroid, AccentBlue),
                    SegOption("1", stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode,    Color(0xFFFFA726)),
                    SegOption("2", stringResource(R.string.settings_theme_dark),  Icons.Rounded.DarkMode,     Color(0xFF7C4DFF))
                ),
                selectedId = themeMode.toString(),
                isLight = isLight,
                backdrop = backdrop,
                onSelect = { onThemeModeChanged(it.toInt()) }
            )
            BasicText(
                when (themeMode) {
                    1 -> stringResource(R.string.settings_theme_light_desc)
                    2 -> stringResource(R.string.settings_theme_dark_desc)
                    else -> stringResource(R.string.settings_theme_auto_desc)
                },
                style = TextStyle(sub, 12.sp)
            )
        }

        // ── Language ────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_language),
            icon = Icons.Rounded.Language,
            iconTint = AccentBlue,
            isLight = isLight,
            titleColor = text,
            modifier = reveal(2, isVisible, density)
        ) {
            SegmentedSelector(
                options = listOf(
                    SegOption("en",    stringResource(R.string.language_english),    null, AccentBlue),
                    SegOption("pt-BR", stringResource(R.string.language_portuguese), null, AccentBlue)
                ),
                selectedId = selectedLocale,
                isLight = isLight,
                backdrop = backdrop,
                onSelect = onLocaleChanged
            )
        }

        // ── Files: save location ────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_save_location),
            icon = Icons.Rounded.FolderOpen,
            iconTint = AccentBlue,
            isLight = isLight,
            titleColor = text,
            modifier = reveal(3, isVisible, density)
        ) {
            val isDefault = saveUri == null
            SegmentedSelector(
                options = listOf(
                    SegOption("default", stringResource(R.string.settings_save_downloads), Icons.Rounded.Download,      AccentBlue),
                    SegOption("custom",  stringResource(R.string.settings_save_custom),    Icons.Rounded.FolderSpecial, AccentGreen)
                ),
                selectedId = if (isDefault) "default" else "custom",
                isLight = isLight,
                backdrop = backdrop,
                onSelect = { id ->
                    if (id == "default") {
                        if (!isDefault) { SaveLocationManager.clearSaveLocation(context); saveUri = null }
                    } else {
                        folderPicker.launch(null)
                    }
                }
            )

            // Path details card.
            val accent = if (saveUri != null) AccentGreen else AccentBlue
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(InnerRadius))
                    .background(if (isLight) Color.Black.copy(0.03f) else Color.White.copy(0.05f))
                    .border(1.dp, if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f), RoundedCornerShape(InnerRadius))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(accent.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (saveUri != null) Icons.Rounded.FolderSpecial else Icons.Rounded.Download,
                        null, Modifier.size(19.dp), accent
                    )
                }
                Column(Modifier.weight(1f)) {
                    BasicText(
                        if (saveUri != null) stringResource(R.string.settings_custom_directory)
                        else stringResource(R.string.settings_default_directory),
                        style = TextStyle(label, 13.sp, fontWeight = FontWeight.SemiBold)
                    )
                    val path = if (saveUri != null)
                        saveUri!!.lastPathSegment?.replace("primary:", "") ?: saveUri.toString()
                    else stringResource(R.string.settings_default_path)
                    BasicText(path, style = TextStyle(sub, 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ── Files: handling toggles ─────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_file_handling),
            icon = Icons.Rounded.AutoFixHigh,
            iconTint = Color(0xFF1976D2),
            isLight = isLight,
            titleColor = text,
            contentSpacing = 2.dp,
            modifier = reveal(4, isVisible, density)
        ) {
            SettingsToggleRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = stringResource(R.string.settings_auto_compress),
                desc = stringResource(R.string.settings_auto_compress_desc),
                checked = autoCompress,
                onCheckedChange = { autoCompress = it; AppSettingsManager.setAutoCompress(context, it) },
                backdrop = backdrop, labelColor = label, subColor = sub
            )
            SettingsDivider(isLight)
            SettingsToggleRow(
                icon = Icons.Rounded.FileCopy,
                title = stringResource(R.string.settings_keep_original),
                desc = stringResource(R.string.settings_keep_original_desc),
                checked = keepOriginal,
                onCheckedChange = { keepOriginal = it; AppSettingsManager.setKeepOriginal(context, it) },
                backdrop = backdrop, labelColor = label, subColor = sub
            )
            SettingsDivider(isLight)
            SettingsToggleRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.settings_notifications),
                desc = stringResource(R.string.settings_notifications_desc),
                checked = notifications,
                onCheckedChange = { notifications = it; AppSettingsManager.setNotifications(context, it) },
                backdrop = backdrop, labelColor = label, subColor = sub
            )
        }

        // ── Files: compression quality ──────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_compression_quality),
            icon = Icons.Rounded.HighQuality,
            iconTint = Color(0xFF1976D2),
            isLight = isLight,
            titleColor = text,
            modifier = reveal(5, isVisible, density),
            trailing = {
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
        ) {
            LiquidSlider(
                value = { defaultQuality },
                onValueChange = {
                    val snapped = ((it * 100f).toInt() / 100f).coerceIn(0f, 1f)
                    if (snapped != defaultQuality) defaultQuality = snapped
                },
                valueRange = 0f..1f,
                visibilityThreshold = 0.005f,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(stringResource(R.string.settings_smaller_size),  style = TextStyle(sub.copy(0.7f), 11.sp))
                BasicText(stringResource(R.string.settings_higher_quality), style = TextStyle(sub.copy(0.7f), 11.sp))
            }
        }

        // ── About ───────────────────────────────────────────────────────────
        Column(
            reveal(6, isVisible, density)
                .fillMaxWidth()
                .liquidGlassSection(isLight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(AccentBlue.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Info, null, Modifier.size(28.dp), AccentBlue)
            }
            BasicText("ClearPDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.Bold))
            BasicText(stringResource(R.string.settings_version), style = TextStyle(sub, 13.sp))
            BasicText(stringResource(R.string.settings_made_by), style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center))

            Spacer(Modifier.height(4.dp))

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

        // ── Licenses ────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_licenses),
            icon = Icons.Rounded.Code,
            iconTint = label,
            isLight = isLight,
            titleColor = text,
            contentSpacing = 12.dp,
            modifier = reveal(7, isVisible, density)
        ) {
            LicenseItem(
                name = "AndroidLiquidGlass", author = "Kyant", license = "Apache License 2.0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass",
                labelColor = label, subColor = sub,
                onOpen = { openExternalLink(context, "https://github.com/Kyant0/AndroidLiquidGlass") }
            )
            LicenseItem(
                name = "Pdf_Tools", author = "Karna14314", license = "PDF viewer zoom/pan reference",
                url = "https://github.com/Karna14314/Pdf_Tools",
                labelColor = label, subColor = sub,
                onOpen = { openExternalLink(context, "https://github.com/Karna14314/Pdf_Tools") }
            )
            SettingsDivider(isLight)
            BasicText(
                stringResource(R.string.settings_license_notice),
                style = TextStyle(sub.copy(0.7f), 11.sp, lineHeight = 16.sp)
            )
        }

        // Dynamic bottom spacer: tab bar + actual nav-bar inset + breathing room.
        Spacer(Modifier.height(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp
        ))
    }
}

// ── Reusable primitives ─────────────────────────────────────────────────────

/** Staggered fade + rise entrance, indexed so each section leads the next. */
@Composable
private fun reveal(index: Int, visible: Boolean, density: Float): Modifier {
    val spec = tween<Float>(durationMillis = 560 + index * 22, delayMillis = index * 60, easing = FastOutSlowInEasing)
    val alpha by animateFloatAsState(if (visible) 1f else 0f, spec, label = "revealA$index")
    val dy    by animateFloatAsState(if (visible) 0f else 18f + index * 4f, spec, label = "revealY$index")
    return Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * density }
}

/** A glass card with an M3 rounded-icon-tile header and an optional trailing slot. */
@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isLight: Boolean,
    titleColor: Color,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = 14.dp,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlassSection(isLight)
            .padding(SectionPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(iconTint.copy(0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), iconTint)
            }
            BasicText(
                title,
                style = TextStyle(titleColor, TitleSize, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        content()
    }
}

/** Equal-weight segmented control shared by theme / language / save-location. */
private data class SegOption(val id: String, val label: String, val icon: ImageVector?, val activeColor: Color)

@Composable
private fun SegmentedSelector(
    options: List<SegOption>,
    selectedId: String,
    isLight: Boolean,
    backdrop: LayerBackdrop,
    onSelect: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val isSelected = selectedId == option.id
            val contentColor = if (isSelected) Color.White else (if (isLight) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
            LiquidButton(
                onClick = { onSelect(option.id) },
                backdrop = backdrop,
                tint = if (isSelected) option.activeColor else Color.Transparent,
                surfaceColor = if (isSelected) option.activeColor.copy(0.18f)
                               else (if (isLight) Color.White.copy(0.70f) else Color.White.copy(0.10f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                ) {
                    if (option.icon != null) {
                        Icon(option.icon, null, Modifier.size(16.dp), contentColor)
                    }
                    BasicText(
                        option.label,
                        style = TextStyle(
                            contentColor, 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider(isLight: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp)
            .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f))
    )
}

private fun Modifier.liquidGlassSection(isLight: Boolean): Modifier {
    val containerColor = if (isLight) Color.White.copy(0.68f) else Color(0xFF161820).copy(0.72f)
    val borderColor = if (isLight) Color.White.copy(0.80f) else Color.White.copy(0.12f)
    return this
        .clip(RoundedCornerShape(SectionRadius))
        .background(containerColor)
        .border(1.dp, borderColor, RoundedCornerShape(SectionRadius))
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
            Modifier.size(34.dp).clip(CircleShape).background(labelColor.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), labelColor)
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(labelColor, 15.sp, fontWeight = FontWeight.Medium))
            BasicText(desc, style = TextStyle(subColor, 12.sp))
        }
        LiquidToggle(selected = { checked }, onSelect = onCheckedChange, backdrop = backdrop)
    }
}

@Composable
private fun LicenseItem(
    name: String,
    author: String,
    license: String,
    url: String,
    labelColor: Color,
    subColor: Color,
    onOpen: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(name, style = TextStyle(labelColor, 14.sp, fontWeight = FontWeight.Medium))
            BasicText(stringResource(R.string.settings_license_author, author), style = TextStyle(subColor, 12.sp))
        }
        BasicText(license, style = TextStyle(subColor, 11.sp))
        BasicText(url, style = TextStyle(AccentBlue, 11.sp))
    }
}

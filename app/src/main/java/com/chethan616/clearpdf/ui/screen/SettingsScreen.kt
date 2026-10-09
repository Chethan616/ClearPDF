package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.chethan616.clearpdf.office.OfficeEngine
import com.chethan616.clearpdf.ui.components.AccentIconTile
import com.chethan616.clearpdf.ui.components.GlassChoiceChip
import com.chethan616.clearpdf.ui.components.GlassDialog
import com.chethan616.clearpdf.ui.components.GlassDialogAction
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.ToolInk
import com.chethan616.clearpdf.ui.components.ToolPrimaryButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.rememberScreenBackdrop
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.ToolAccents
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings, rebuilt on the same kit the tool screens and Home use: [liquidGlassPanel] sections
 * (sampling the *sensor*, so their own highlight follows device tilt like every other panel
 * instead of the old bespoke [legacySectionBorder]-style flat card), [GlassChoiceChip] for every
 * picker instead of three hand-rolled [LiquidButton]s per picker, and ONE staggered [updateTransition]
 * entrance instead of twelve independent `animateFloatAsState` pairs. Every setting the old screen
 * had is still here and still does the same thing — this only changes how it looks and how many
 * animation clocks it costs to show it.
 */
@Composable
fun SettingsScreen(
    backdrop: LayerBackdrop,
    isDarkMode: Boolean = false,
    onDarkModeChanged: (Boolean) -> Unit = {},
    themeMode: Int = 0,
    onThemeModeChanged: (Int) -> Unit = {},
    showWallpaper: Boolean = true,
    onShowWallpaperChanged: (Boolean) -> Unit = {},
    reduceGlassMotion: Boolean = false,
    onReduceGlassMotionChanged: (Boolean) -> Unit = {},
    hasCustomWallpaper: Boolean = false,
    onCustomWallpaperChanged: (String?) -> Unit = {},
    selectedLocale: String = "en",
    onLocaleChanged: (String) -> Unit = {},
    onReplayOnboarding: () -> Unit = {}
) {
    val isLight = !isDarkMode
    val ink = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val context = LocalContext.current
    val uiSensor = rememberUISensor()
    val settingsScope = rememberCoroutineScope()
    val openRepo = remember(context) { { openExternalLink(context, GitHubStarPromptManager.REPO_URL) } }

    var autoCompress by remember { mutableStateOf(AppSettingsManager.getAutoCompress(context)) }
    var keepOriginal by remember { mutableStateOf(AppSettingsManager.getKeepOriginal(context)) }
    var rememberRecentFiles by remember { mutableStateOf(AppSettingsManager.getRememberRecentFiles(context)) }
    var keepLocalCopies by remember { mutableStateOf(AppSettingsManager.getKeepLocalCopies(context)) }
    var defaultQuality by remember { mutableFloatStateOf(AppSettingsManager.getDefaultQuality(context)) }
    LaunchedEffect(defaultQuality) {
        delay(300L)
        AppSettingsManager.setDefaultQuality(context, defaultQuality)
    }

    var saveUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }
    // Non-null while the "Delete Office engine?" dialog is up; holds the size it will free.
    var officeEngineDeleteSize by remember { mutableStateOf<Long?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val displayPath = uri.lastPathSegment?.replace("primary:", "") ?: uri.toString()
            SaveLocationManager.setSaveLocation(context, uri, displayPath)
            saveUri = uri
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            AppSettingsManager.setCustomWallpaper(context, uri.toString())
            onCustomWallpaperChanged(uri.toString())
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    // One transition for every section's entrance, staggered by index — the same device ToolsScreen
    // uses, in place of the old screen's twelve separate `animateFloatAsState` pairs (six panels,
    // each with its own alpha *and* offsetY Animatable, all running concurrently from first frame).
    val entrance = updateTransition(isVisible, label = "settingsEntrance")

    val screenBackdrop = rememberScreenBackdrop(backdrop)
    Box(Modifier.fillMaxSize()) {
        GlassScreenScaffold(
            backdrop = backdrop,
            screenBackdrop = screenBackdrop,
            contentBottomPadding = 84.dp,
            header = { headerBackdrop ->
                GlassScreenHeaderRow(
                    title = stringResource(R.string.settings_title),
                    backdrop = headerBackdrop,
                    onBack = null,
                    modifier = entrance.sectionFade(0)
                )
            }
        ) { contentPadding ->
            val scrollState = rememberScrollState()
            val officeEngineRequester = remember { BringIntoViewRequester() }
            LaunchedEffect(Unit) {
                if (OfficeEngine.focusSettingsSection.value) {
                    OfficeEngine.focusSettingsSection.value = false
                    delay(350L)
                    officeEngineRequester.bringIntoView()
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Appearance ──
                SettingsSection(backdrop, uiSensor, entrance.sectionFade(1)) {
                    SettingsSectionHeader(Icons.Rounded.Tune, stringResource(R.string.settings_appearance), ink)
                    data class ThemeOption(val idx: Int, val label: String, val icon: ImageVector, val accent: Color)
                    val options = listOf(
                        ThemeOption(0, stringResource(R.string.settings_theme_auto), Icons.Rounded.PhoneAndroid, LiquidGlassColors.Blue),
                        ThemeOption(1, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode, LiquidGlassColors.Orange),
                        ThemeOption(2, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode, LiquidGlassColors.Purple)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEach { option ->
                            GlassChoiceChip(
                                label = option.label,
                                selected = themeMode == option.idx,
                                onClick = { onThemeModeChanged(option.idx) },
                                backdrop = backdrop,
                                accent = option.accent,
                                icon = option.icon,
                                modifier = Modifier.weight(1f)
                            )
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
                    SettingsDivider(isLight)
                    SettingsToggleRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.settings_reduce_glass_motion),
                        desc = stringResource(R.string.settings_reduce_glass_motion_desc),
                        checked = reduceGlassMotion,
                        onCheckedChange = onReduceGlassMotionChanged,
                        backdrop = backdrop
                    )
                }

                // ── Language ──
                SettingsSection(backdrop, uiSensor, entrance.sectionFade(2)) {
                    SettingsSectionHeader(Icons.Rounded.Language, stringResource(R.string.settings_language), ink)
                    data class LangOption(val code: String, val label: String)
                    val langs = listOf(
                        LangOption("en", stringResource(R.string.language_english)),
                        LangOption("pt-BR", stringResource(R.string.language_portuguese)),
                        LangOption("es", stringResource(R.string.language_spanish)),
                        LangOption("it", stringResource(R.string.language_italian)),
                        LangOption("ru", stringResource(R.string.language_russian))
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        langs.forEach { opt ->
                            GlassChoiceChip(
                                label = opt.label,
                                selected = selectedLocale == opt.code,
                                onClick = { onLocaleChanged(opt.code) },
                                backdrop = backdrop,
                                accent = LiquidGlassColors.Blue
                            )
                        }
                    }
                }

                // ── Save Location + File Handling + Default Quality ── one panel: three related
                // "how documents are handled" topics, each keeping its own sub-header, so scrolling
                // past this part of the screen pays for one glass surface instead of three.
                SettingsSection(backdrop, uiSensor, entrance.sectionFade(3), gap = 4.dp) {
                    SettingsSectionHeader(Icons.Rounded.FolderOpen, stringResource(R.string.settings_save_location), LiquidGlassColors.Blue, bottomPad = 8.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidGlassColors.neutralSurface(isDarkMode))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AccentIconTile(Icons.Rounded.FolderOpen, LiquidGlassColors.Blue, size = 40, iconSize = 20)
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                if (saveUri != null) stringResource(R.string.settings_custom_directory) else stringResource(R.string.settings_default_directory),
                                style = TextStyle(ink, 14.sp, FontWeight.SemiBold)
                            )
                            val path = saveUri?.let { it.lastPathSegment?.replace("primary:", "") ?: it.toString() }
                                ?: stringResource(R.string.settings_default_path)
                            BasicText(path, style = TextStyle(sub, 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolPrimaryButton(
                            text = stringResource(R.string.settings_change_folder),
                            onClick = { folderPicker.launch(null) },
                            backdrop = backdrop,
                            accent = LiquidGlassColors.Teal,
                            modifier = Modifier.weight(1f)
                        )
                        if (saveUri != null) {
                            ToolPrimaryButton(
                                text = stringResource(R.string.settings_reset),
                                onClick = { SaveLocationManager.clearSaveLocation(context); saveUri = null },
                                backdrop = backdrop,
                                accent = LiquidGlassColors.Orange
                            )
                        }
                    }

                    SettingsDivider(isLight)
                    SettingsSectionHeader(Icons.Rounded.Description, stringResource(R.string.settings_file_handling), ink, bottomPad = 8.dp)
                    SettingsToggleRow(
                        icon = Icons.Rounded.Compress,
                        title = stringResource(R.string.settings_auto_compress),
                        desc = stringResource(R.string.settings_auto_compress_desc),
                        checked = autoCompress,
                        onCheckedChange = { autoCompress = it; AppSettingsManager.setAutoCompress(context, it) },
                        backdrop = backdrop
                    )
                    SettingsDivider(isLight)
                    SettingsToggleRow(
                        icon = Icons.Rounded.FileCopy,
                        title = stringResource(R.string.settings_keep_original),
                        desc = stringResource(R.string.settings_keep_original_desc),
                        checked = keepOriginal,
                        onCheckedChange = { keepOriginal = it; AppSettingsManager.setKeepOriginal(context, it) },
                        backdrop = backdrop
                    )
                    SettingsDivider(isLight)
                    SettingsToggleRow(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.settings_remember_recent_files),
                        desc = stringResource(R.string.settings_remember_recent_files_desc),
                        checked = rememberRecentFiles,
                        onCheckedChange = { rememberRecentFiles = it; AppSettingsManager.setRememberRecentFiles(context, it) },
                        backdrop = backdrop
                    )
                    SettingsDivider(isLight)
                    SettingsToggleRow(
                        icon = Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.settings_keep_local_copies),
                        desc = stringResource(R.string.settings_keep_local_copies_desc),
                        checked = keepLocalCopies,
                        onCheckedChange = {
                            keepLocalCopies = it
                            settingsScope.launch(Dispatchers.IO) { AppSettingsManager.setKeepLocalCopies(context, it) }
                        },
                        backdrop = backdrop
                    )

                    SettingsDivider(isLight)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SettingsSectionHeader(Icons.Rounded.HighQuality, stringResource(R.string.settings_compression_quality), LiquidGlassColors.Blue, bottomPad = 0.dp)
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(LiquidGlassColors.Blue.copy(0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            BasicText("${(defaultQuality * 100).toInt()}%", style = TextStyle(LiquidGlassColors.Blue, 13.sp, FontWeight.Bold))
                        }
                    }
                    LiquidSlider(
                        value = { defaultQuality },
                        onValueChange = { defaultQuality = ((it * 100f).toInt() / 100f).coerceIn(0f, 1f) },
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
                SettingsSection(backdrop, uiSensor, entrance.sectionFade(4), gap = 4.dp) {
                    SettingsSectionHeader(Icons.Rounded.Wallpaper, stringResource(R.string.settings_personalization), ink, bottomPad = 8.dp)
                    SettingsToggleRow(
                        icon = Icons.Rounded.Wallpaper,
                        title = stringResource(R.string.settings_background),
                        desc = stringResource(R.string.settings_background_desc),
                        checked = showWallpaper,
                        onCheckedChange = onShowWallpaperChanged,
                        backdrop = backdrop
                    )
                    if (showWallpaper) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp, start = 46.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolPrimaryButton(
                                text = stringResource(R.string.settings_bg_gallery),
                                onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                                backdrop = backdrop,
                                accent = LiquidGlassColors.Indigo,
                                icon = Icons.Rounded.PhotoLibrary,
                                modifier = Modifier.weight(1f)
                            )
                            LiquidIconButton(
                                onClick = { AppSettingsManager.clearCustomWallpaper(context); onCustomWallpaperChanged(null) },
                                backdrop = backdrop,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, stringResource(R.string.settings_reset), Modifier.size(18.dp), if (hasCustomWallpaper) ink else ink.copy(0.4f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ToolPrimaryButton(
                        text = stringResource(R.string.settings_replay_onboarding),
                        onClick = onReplayOnboarding,
                        backdrop = backdrop,
                        accent = LiquidGlassColors.Purple,
                        icon = Icons.Rounded.AutoAwesome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── About & Open Source + Licenses ── one panel: the credits list is part of "about
                // the app", not a topic of its own, and folding it in here removes the single
                // heaviest remaining glass surface (the longest scroll content on the screen).
                SettingsSection(backdrop, uiSensor, entrance.sectionFade(5), padding = 24.dp, horizontalAlignment = Alignment.CenterHorizontally) {
                    AccentIconTile(Icons.Rounded.Info, LiquidGlassColors.Blue, size = 56, iconSize = 28)
                    BasicText("ClearPDF", style = TextStyle(ink, 20.sp, FontWeight.Bold))
                    BasicText(stringResource(R.string.settings_version), style = TextStyle(sub, 13.sp))
                    BasicText(stringResource(R.string.settings_made_by), style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center))
                    Spacer(Modifier.height(4.dp))
                    ToolPrimaryButton(
                        text = stringResource(R.string.settings_star_github),
                        onClick = openRepo,
                        backdrop = backdrop,
                        accent = ToolAccents.Star,
                        icon = Icons.Rounded.Star,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BasicText(stringResource(R.string.settings_open_source), style = TextStyle(sub.copy(0.7f), 11.sp, textAlign = TextAlign.Center))

                    SettingsDivider(isLight)
                    // Left-aligned sub-column: the parent's CenterHorizontally is for the hero block
                    // above, not this list.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingsSectionHeader(Icons.Rounded.Code, stringResource(R.string.settings_licenses), ink)
                        OpenSourceCredits.forEachIndexed { index, credit ->
                            if (index > 0) SettingsDivider(isLight)
                            LicenseItem(credit.name, credit.author, credit.license, credit.url, ink, sub)
                        }
                        SettingsDivider(isLight)
                        BasicText(stringResource(R.string.settings_license_notice), style = TextStyle(sub.copy(0.7f), 11.sp, lineHeight = 16.sp))
                    }
                }

                // ── Office engine (optional, powered by LibreOffice) ──
                OfficeEngineSettingsSection(
                    backdrop = backdrop,
                    isLight = isLight,
                    textColor = ink,
                    labelColor = ink,
                    subColor = sub,
                    onRequestDelete = { officeEngineDeleteSize = it },
                    modifier = Modifier
                        .bringIntoViewRequester(officeEngineRequester)
                        .fillMaxWidth()
                        .then(entrance.sectionFade(6))
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp)
                )
            }
        }
    }
    // Outside the scaffold's captured layer so it refracts the live screen (wallpaper + content).
    GlassDialog(
        visible = officeEngineDeleteSize != null,
        onDismiss = { officeEngineDeleteSize = null },
        backdrop = screenBackdrop.glass,
        title = stringResource(R.string.office_engine_delete_title),
        actions = {
            GlassDialogAction(text = stringResource(R.string.office_engine_cancel), onClick = { officeEngineDeleteSize = null })
            GlassDialogAction(
                text = stringResource(R.string.office_engine_delete),
                onClick = { officeEngineDeleteSize = null; OfficeEngine.installer(context).uninstall() },
                primary = true,
                destructive = true
            )
        }
    ) {
        BasicText(
            stringResource(R.string.office_engine_delete_message, android.text.format.Formatter.formatShortFileSize(context, officeEngineDeleteSize ?: 0L)),
            style = TextStyle(ink, 14.sp, lineHeight = 20.sp)
        )
    }
}

// With panels now merged down to 7 sections (was 9), the cascade is shorter on its own, but the
// per-step/duration values were still the original, slower ones -- the last section didn't finish
// appearing until ~725ms after landing on the screen, which on top of the nav transition itself
// read as "the screen is still loading" rather than a snappy tab switch.
private const val SettingsStaggerStepMs = 20

/** Fade-only entrance for a glass section — translating a `liquidGlassPanel` re-samples its backdrop
 *  every frame of the slide, which is the jank this replaces (see ToolsScreen's own note). */
@Composable
private fun Transition<Boolean>.sectionFade(index: Int): Modifier {
    val fadeAlpha by animateFloat(
        transitionSpec = { tween(durationMillis = 200, delayMillis = SettingsStaggerStepMs * index, easing = FastOutSlowInEasing) },
        label = "settingsFade$index"
    ) { if (it) 1f else 0f }
    return Modifier.alpha(fadeAlpha)
}

/** One glass panel, built from [liquidGlassPanel] exactly like every tool screen's own sections. */
@Composable
private fun SettingsSection(
    backdrop: Backdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    entranceModifier: Modifier,
    padding: androidx.compose.ui.unit.Dp = 20.dp,
    gap: androidx.compose.ui.unit.Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(entranceModifier)
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String, tint: Color, bottomPad: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(
        Modifier.padding(bottom = bottomPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint)
        BasicText(title, style = TextStyle(ToolInk.text(), 17.sp, FontWeight.SemiBold))
    }
}

@Composable
private fun SettingsDivider(isLight: Boolean) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f)))
}

private fun openExternalLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

/** A row's accent icon badge + title/description + [LiquidToggle] — the same icon-tile language
 *  [AccentIconTile] uses, just sized for a list row instead of a hero card. */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop
) {
    val ink = ToolInk.text()
    val sub = ToolInk.secondary()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccentIconTile(icon, LiquidGlassColors.Blue, size = 34, iconSize = 17)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(ink, 15.sp, FontWeight.Medium))
            BasicText(desc, style = TextStyle(sub, 12.sp))
        }
        LiquidToggle(selected = { checked }, onSelect = onCheckedChange, backdrop = backdrop)
    }
}

private class Credit(val name: String, val author: String, val license: String, val url: String)

/**
 * Ordered roughly by how much of the app each one carries.
 *
 * Note what is *not* here: the .xlsx reader and the PowerPoint slide renderer are written in this
 * repo against the published OOXML layout, not borrowed. That was a size decision — the libraries
 * that read Office formats properly on Android (POI's OOXML half plus XmlBeans at ~17 MB, or
 * OpenDocument.core's 100 MB AAR) are far more than this app can carry for a viewer. POI appears
 * below only for the *legacy* binary .doc/.xls/.ppt formats, which are not XML and cannot be read
 * this way. Word layout is the one place a library won on merit: docx-preview delegates to the
 * browser engine the phone already has, so it costs ~48 KB rather than tens of megabytes.
 */
private val OpenSourceCredits = listOf(
    Credit("AndroidLiquidGlass", "Kyant", "Apache License 2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
    Credit("PdfBox-Android", "Tom Roush", "Apache License 2.0 — PDF text, forms and annotation export", "https://github.com/TomRoush/PdfBox-Android"),
    Credit("Apache POI", "The Apache Software Foundation", "Apache License 2.0 — legacy .doc / .xls / .ppt reading", "https://poi.apache.org"),
    Credit("ML Kit Text Recognition", "Google", "Apache License 2.0 — bundled on-device OCR, no network", "https://developers.google.com/ml-kit/vision/text-recognition"),
    Credit("Tesseract4Android", "Adaptech s.r.o.", "Apache License 2.0 — offline OCR fallback", "https://github.com/adaptech-cz/Tesseract4Android"),
    Credit("Tesseract OCR", "Google / Tesseract contributors", "Apache License 2.0", "https://github.com/tesseract-ocr/tesseract"),
    Credit("Leptonica", "Dan Bloomberg", "BSD 2-Clause — image processing behind Tesseract", "https://github.com/DanBloomberg/leptonica"),
    Credit("docx-preview", "Volodymyr Baydalka", "Apache License 2.0 — Word document layout", "https://github.com/VolodymyrBaydalka/docxjs"),
    Credit("JSZip", "Stuart Knightley", "MIT License (used under MIT of its MIT/GPLv3 dual licence)", "https://github.com/Stuk/jszip"),
    Credit("ImageToolbox", "T8RIN (Malik Mukhametzyanov)", "Apache License 2.0 — image editor cropper, perspective crop and draw engine (adapted)", "https://github.com/T8RIN/ImageToolbox"),
    Credit("GPUImage for Android", "CyberAgent, Inc.", "Apache License 2.0 — image editor adjustments and filters", "https://github.com/cats-oss/android-gpuimage"),
    Credit("ML Kit Subject Segmentation", "Google", "Google APIs Terms — optional background removal via Play services", "https://developers.google.com/ml-kit/vision/subject-segmentation"),
    Credit("LibreOffice", "The Document Foundation", "Mozilla Public License 2.0 — optional Office engine (downloaded on request, not bundled)", "https://www.libreoffice.org/about-us/licenses/"),
    Credit("Pdf_Tools", "Karna14314", "PDF viewer zoom/pan reference", "https://github.com/Karna14314/Pdf_Tools")
)

@Composable
private fun LicenseItem(name: String, author: String, license: String, url: String, labelColor: Color, subColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(name, style = TextStyle(labelColor, 14.sp, FontWeight.Medium))
            BasicText(stringResource(R.string.settings_license_author, author), style = TextStyle(subColor, 12.sp))
        }
        BasicText(license, style = TextStyle(subColor, 11.sp))
        val context = LocalContext.current
        BasicText(
            url,
            style = TextStyle(LiquidGlassColors.Blue, 11.sp),
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openExternalLink(context, url) }
        )
    }
}

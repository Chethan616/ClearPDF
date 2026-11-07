package com.chethan616.clearpdf.ui.desk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import com.chethan616.clearpdf.ui.ThemeChoice
import com.chethan616.clearpdf.ui.glass.GlassCard
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassRow
import com.chethan616.clearpdf.ui.glass.GlassSettings
import com.chethan616.clearpdf.ui.glass.GlassStyle
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.GlassTextField
import com.chethan616.clearpdf.ui.glass.GlassToggle
import com.chethan616.clearpdf.ui.glass.MotionSettings
import com.chethan616.clearpdf.ui.glass.MotionStyle
import com.chethan616.clearpdf.ui.glass.WashPanel
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.glass.RoundedRectangle

/** Artefact's repository: help, issues and source. */
const val CLEARPDF_REPO = "https://github.com/Chethan616/ClearPDF"

/** Settings, and its Licences page. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var page by remember { mutableStateOf("root") }
    BackHandler { if (page == "root") vm.settingsOpen = false else page = "root" }
    AnimatedContent(
        page,
        transitionSpec = {
            val deeper = targetState != "root"
            val dir = if (deeper) 1 else -1
            (androidx.compose.animation.slideInHorizontally(com.chethan616.clearpdf.ui.glass.Fluid.push()) { dir * it / 4 } +
                androidx.compose.animation.fadeIn(com.chethan616.clearpdf.ui.glass.Fluid.quick())) togetherWith
                (androidx.compose.animation.slideOutHorizontally(com.chethan616.clearpdf.ui.glass.Fluid.push()) { -dir * it / 10 } +
                    androidx.compose.animation.fadeOut(com.chethan616.clearpdf.ui.glass.Fluid.fade()))
        },
        label = "settings page",
    ) { p ->
        when (p) {
            "licences" -> LicencesPage { page = "root" }
            "glass" -> GlassMotionPage { page = "root" }
            else -> SettingsRoot(vm, openLicences = { page = "licences" }, openGlass = { page = "glass" })
        }
    }
}

@Composable
private fun SettingsRoot(vm: AppViewModel, openLicences: () -> Unit, openGlass: () -> Unit) {
    val c = clear
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var leaving by remember { mutableStateOf<String?>(null) }
    var authorDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "title") {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(null, stringResource(R.string.home_settings), onBack = { vm.settingsOpen = false })
        }

        // what people change most comes first: how the app looks and feels
        item(key = "look") { Section(stringResource(R.string.settings_look)) }
        item(key = "lookpanel") {
            val custom = stringResource(R.string.settings_custom)
            val glassName = GlassStyle.Presets.indexOf(GlassSettings.style).let { if (it < 0) custom else glassPresetNames()[it] }
            val motionName = MotionStyle.Presets.indexOf(MotionSettings.style).let { if (it < 0) custom else motionPresetNames()[it] }
            Panel {
                ThemePicker(vm.theme) { vm.setThemeChoice(it) }
                SettingRow(LucideIcons.Sparkles, Candy.Grape, stringResource(R.string.settings_glass_motion), openGlass, value = "$glassName · $motionName", chevron = true)
                ToggleRow(LucideIcons.Smartphone, Candy.Bubblegum, stringResource(R.string.settings_haptics), null, GlassSettings.haptics) { GlassSettings.updateHaptics(context, it) }
            }
        }

        item(key = "personal") { Section(stringResource(R.string.settings_personalization)) }
        item(key = "personalpanel") { Panel { WallpaperPicker(context) } }

        item(key = "reading") { Section(stringResource(R.string.settings_reading)) }
        item(key = "readingpanel") {
            Panel {
                ToggleRow(LucideIcons.PanelsTopLeft, Candy.Sky, stringResource(R.string.settings_quick_preview), stringResource(R.string.settings_quick_preview_body), vm.quickPreview, first = true) { vm.updateQuickPreview(it) }
                ToggleRow(LucideIcons.Sun, Candy.Tangerine, stringResource(R.string.home_keep_screen_on), stringResource(R.string.home_keep_screen_on_body), vm.keepScreenOn) { vm.updateKeepScreenOn(it) }
                ToggleRow(LucideIcons.Smartphone, Candy.Sky, stringResource(R.string.home_status_bar_in_documents), stringResource(R.string.home_status_bar_in_documents_body), vm.statusBarInDocuments) { vm.updateStatusBarInDocuments(it) }
                ToggleRow(LucideIcons.Hash, Candy.Mint, stringResource(R.string.home_search_count), stringResource(R.string.home_search_count_body), vm.searchCount) { vm.updateSearchCount(it) }
                ToggleRow(LucideIcons.SquareTerminal, Candy.Grape, stringResource(R.string.home_keyboard_shortcuts), stringResource(R.string.home_keyboard_shortcuts_body), vm.keyboardShortcuts) { vm.updateKeyboardShortcuts(it) }
            }
        }

        item(key = "forms") { Section(stringResource(R.string.settings_forms)) }
        item(key = "formspanel") {
            Panel {
                SettingRow(
                    LucideIcons.UserRound, Candy.Sky, stringResource(R.string.home_author_title), { authorDialog = true },
                    value = vm.toolPrefs.author.ifBlank { stringResource(R.string.home_author_hint) }, chevron = true, first = true,
                )
                ToggleRow(LucideIcons.TextCursorInput, Candy.Tangerine, stringResource(R.string.home_offer_suggestions), null, vm.formSuggestions) { vm.updateFormSuggestions(it) }
                SettingRow(LucideIcons.BrushCleaning, Candy.Tomato, stringResource(R.string.home_clear_suggestions), { vm.clearFormSuggestions() })
            }
        }

        item(key = "about") { Section(stringResource(R.string.settings_about)) }
        item(key = "aboutpanel") {
            Panel {
                SettingRow(LucideIcons.Compass, Candy.Sky, stringResource(R.string.settings_show_welcome), { vm.showWelcomeAgain() }, first = true)
                SettingRow(LucideIcons.MessageSquare, Candy.Tomato, stringResource(R.string.home_report_problem), { leaving = "$CLEARPDF_REPO/issues" }, chevron = true)
                SettingRow(LucideIcons.CodeXml, Candy.Grape, stringResource(R.string.home_source_code), { uri.openUri(CLEARPDF_REPO) }, chevron = true)
                SettingRow(LucideIcons.ScrollText, Candy.Mint, stringResource(R.string.licences_row), openLicences, chevron = true)
            }
        }
        item(key = "credits") { AppFooter(vm) }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
    leaving?.let { url ->
        GlassDialog(
            onDismiss = { leaving = null }, title = stringResource(R.string.home_leaving_title), icon = LucideIcons.ExternalLink, accent = Candy.Sky,
            confirm = stringResource(R.string.home_continue), onConfirm = { leaving = null; uri.openUri(url) }, dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.home_leaving_body), style = ClearType.Body, color = c.inkSoft) }
    }
    if (authorDialog) {
        var name by remember { mutableStateOf(vm.toolPrefs.author) }
        GlassDialog(
            onDismiss = { authorDialog = false }, title = stringResource(R.string.home_author_title), icon = LucideIcons.UserRound, accent = Candy.Sky,
            confirm = stringResource(R.string.common_ok), onConfirm = { vm.toolPrefs.author = name.trim(); authorDialog = false; vm.prefsChanged++ },
            dismiss = stringResource(R.string.common_cancel),
        ) { GlassTextField(name, { name = it }, stringResource(R.string.common_your_name)) }
    }
}

/** The settings' foot: the mark, name and version (a long press copies it), the credits and notice. */
@Composable
private fun AppFooter(vm: AppViewModel) {
    val c = clear
    val clipboard = LocalClipboardManager.current
    val version = stringResource(R.string.home_version, vm.appVersion, com.chethan616.clearpdf.core.engineVersion())
    val copied = stringResource(R.string.home_version_copied)
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp)
            .pointerInput(version) { detectTapGestures(onLongPress = { clipboard.setText(AnnotatedString(version)); vm.tell(copied) }) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.artefact_logo), null, Modifier.size(44.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_name), style = ClearType.BodyStrong, color = c.ink)
        Text(version, style = ClearType.Caption, color = c.inkSoft)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.settings_credits_body), style = ClearType.Caption, color = c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_legal_notice), style = ClearType.Caption, color = c.inkFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A group's name, small and quiet over its panel (as iOS's grouped settings), "Custom" when tuned by hand. */
@Composable
internal fun Section(title: String, custom: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = ClearType.Eyebrow, color = clear.inkSoft, modifier = Modifier.weight(1f))
        if (custom) Text(stringResource(R.string.settings_custom), style = ClearType.Label, color = clear.inkSoft)
    }
}

/** A setting chosen by a segmented capsule: its name above, the glass capsule below. */
@Composable
private fun Choice(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label.uppercase(), style = ClearType.Eyebrow, color = clear.inkSoft, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        content()
    }
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    WashPanel(Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(), shape = RoundedRectangle(22.dp)) {
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The theme as three little desks to pick from: the light one, the dark one, and System split
 * corner to corner between them. The chosen one is ringed and ticked, and lifts a touch.
 */
@Composable
private fun ThemePicker(current: ThemeChoice, onPick: (ThemeChoice) -> Unit) {
    val c = clear
    val choices = listOf(
        ThemeChoice.System to stringResource(R.string.settings_theme_system),
        ThemeChoice.Light to stringResource(R.string.settings_theme_light),
        ThemeChoice.Dark to stringResource(R.string.settings_theme_dark),
    )
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.forEach { (choice, label) ->
            val on = choice == current
            val lift by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, com.chethan616.clearpdf.ui.glass.GlassMotion.pop(), label = "theme")
            Column(
                Modifier.weight(1f).pressable({ onPick(choice) }, pressedScale = 0.94f).semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.78f)
                        .graphicsLayer { val s = 0.96f + 0.04f * lift; scaleX = s; scaleY = s }
                        .border(if (on) 2.5.dp else 1.dp, if (on) Candy.Sky else c.ink.copy(alpha = 0.12f), RoundedRectangle(18.dp))
                        .padding(4.dp)
                        .clip(RoundedRectangle(14.dp))
                        .drawBehind { drawThemeDesk(choice) },
                ) {
                    if (on) Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Candy.Sky),
                        contentAlignment = Alignment.Center,
                    ) { Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(label, style = if (on) ClearType.BodyStrong else ClearType.Body, color = if (on) c.ink else c.inkSoft, maxLines = 1)
            }
        }
    }
}

/**
 * The wallpaper: Aurora (Artefact's own), Liquid (the old ClearPDF's) and a picture from the gallery,
 * each a thumbnail, the chosen one ringed and ticked like the theme's. Custom picks a new picture
 * every time it is tapped.
 */
@Composable
private fun WallpaperPicker(context: android.content.Context) {
    val c = clear
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val current = GlassSettings.wallpaper
    val custom = GlassSettings.customWallpaper
    val failed = stringResource(R.string.settings_wallpaper_failed)
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (!GlassSettings.setCustomWallpaper(context, uri)) android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val choices = listOf(
        com.chethan616.clearpdf.ui.glass.Wallpaper.Aurora to stringResource(R.string.settings_wallpaper_aurora),
        com.chethan616.clearpdf.ui.glass.Wallpaper.Liquid to stringResource(R.string.settings_wallpaper_liquid),
        com.chethan616.clearpdf.ui.glass.Wallpaper.Custom to stringResource(R.string.settings_wallpaper_custom),
    )
    Text(stringResource(R.string.settings_wallpaper), style = ClearType.Label, color = c.inkSoft, modifier = Modifier.padding(start = 18.dp, top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { (w, label) ->
            val on = w == current
            val tap = {
                if (w == com.chethan616.clearpdf.ui.glass.Wallpaper.Custom) {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else GlassSettings.updateWallpaper(context, w)
            }
            Column(Modifier.weight(1f).pressable(tap, pressedScale = 0.94f).semantics { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.62f)
                        .border(if (on) 2.5.dp else 1.dp, if (on) Candy.Sky else c.ink.copy(alpha = 0.12f), RoundedRectangle(18.dp))
                        .padding(4.dp).clip(RoundedRectangle(14.dp)),
                ) {
                    when (w) {
                        com.chethan616.clearpdf.ui.glass.Wallpaper.Liquid ->
                            Image(painterResource(if (c.dark) R.drawable.wallpaper_liquid_dark else R.drawable.wallpaper_liquid_light), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        com.chethan616.clearpdf.ui.glass.Wallpaper.Custom ->
                            if (custom != null) Image(custom, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else Box(Modifier.fillMaxSize().background(c.ink.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                                Icon(LucideIcons.ImagePlus, null, tint = c.inkSoft, modifier = Modifier.size(26.dp))
                            }
                        else -> Box(Modifier.fillMaxSize().drawBehind {
                            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(if (c.dark) listOf(Color(0xFF231A3D), Color(0xFF15122A)) else listOf(Color(0xFFDCD8F6), Color(0xFFC9C4EE))))
                            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFB36BFF).copy(alpha = 0.55f), Color.Transparent), androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f), size.width * 0.7f), size.width * 0.7f, androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f))
                            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF5AA9FF).copy(alpha = 0.45f), Color.Transparent), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.85f), size.width * 0.7f), size.width * 0.7f, androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.85f))
                        })
                    }
                    if (on) Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(Candy.Sky),
                        contentAlignment = Alignment.Center,
                    ) { Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                    else if (w == com.chethan616.clearpdf.ui.glass.Wallpaper.Custom && custom != null) Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(LucideIcons.Pencil, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(label, style = if (on) ClearType.BodyStrong else ClearType.Body, color = if (on) c.ink else c.inkSoft, maxLines = 1)
            }
        }
    }
}

/** A tiny desk in a theme's colours: its sky, a bloom, two cards and the dock. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThemeDesk(choice: ThemeChoice) {
    fun desk(dark: Boolean) {
        val sky = if (dark) listOf(Color(0xFF231A3D), Color(0xFF15122A)) else listOf(Color(0xFFDCD8F6), Color(0xFFC9C4EE))
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(sky))
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf((if (dark) Color(0xFF7B4DDB) else Color(0xFFF2B8E4)).copy(alpha = 0.7f), Color.Transparent), androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.15f), size.width * 0.8f),
            size.width * 0.8f, androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.15f),
        )
        val ink = if (dark) Color.White else Color(0xFF1C1733)
        val w = size.width
        val h = size.height
        drawRoundRect(ink.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.06f), androidx.compose.ui.geometry.CornerRadius(h * 0.03f))
        val card1 = if (dark) Color(0xFF3B6E5A) else Color(0xFFBFE8D2)
        val card2 = if (dark) Color(0xFF4A3F86) else Color(0xFFFFFFFF)
        drawRoundRect(card1, androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.27f), androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.4f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
        drawRoundRect(card2, androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.27f), androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.3f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
        drawRoundRect(ink.copy(alpha = if (dark) 0.22f else 0.12f), androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.8f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.1f), androidx.compose.ui.geometry.CornerRadius(h * 0.05f))
        drawCircle(Color.White, h * 0.035f, androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.85f))
    }
    when (choice) {
        ThemeChoice.Light -> desk(false)
        ThemeChoice.Dark -> desk(true)
        else -> {
            desk(false)
            val half = androidx.compose.ui.graphics.Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            clipPath(half) { desk(true) }
        }
    }
}

/** A setting's icon: a small solid tile with a white glyph, as grouped settings draw them. */
@Composable
private fun SettingIcon(icon: ImageVector, accent: Color) {
    Box(Modifier.size(30.dp).clip(RoundedRectangle(8.dp)).background(accent), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

/** A hairline between rows of a panel, starting where the words do. */
private fun Modifier.rowDivider(show: Boolean, color: Color): Modifier = if (!show) this else drawBehind {
    val x = 60.dp.toPx()
    drawRect(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(size.width - x, 0.6.dp.toPx()))
}

/** A row of a settings panel: icon, name, then a value and a chevron when it opens something. */
@Composable
internal fun SettingRow(icon: ImageVector, accent: Color, label: String, onClick: () -> Unit, value: String? = null, chevron: Boolean = false, first: Boolean = false) {
    val c = clear
    Row(
        Modifier.fillMaxWidth().rowDivider(!first, c.ink.copy(alpha = 0.08f)).pressable(onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, accent)
        Spacer(Modifier.width(14.dp))
        // the value under the name, as a subtitle, so neither is ever cut short
        Column(Modifier.weight(1f)) {
            Text(label, style = ClearType.Body, color = c.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (value != null) Text(value, style = ClearType.Caption, color = c.inkSoft, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (chevron) Icon(LucideIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.padding(start = 4.dp).size(16.dp))
    }
}

/** A switch row: icon, the setting's name and (smaller) what it does, and a glass toggle. */
@Composable
internal fun ToggleRow(icon: ImageVector, accent: Color, label: String, body: String?, checked: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    val c = clear
    Row(
        Modifier.fillMaxWidth().rowDivider(!first, c.ink.copy(alpha = 0.08f)).pressable({ onChange(!checked) }).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = ClearType.Body, color = c.ink)
            if (body != null) Text(body, style = ClearType.Caption, color = c.inkSoft, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        GlassToggle(checked, onChange, accent = Candy.Mint)
    }
}

/**
 * One open-source work Artefact is built from: its name, what it does in the app, its licence and
 * authors, where it lives, and the licence text the app carries for it (in `assets/licences`), if any.
 */
private class Credit(val name: String, val role: String, val terms: String, val url: String, val text: String? = null)

/** A group of credits under a heading. */
private class CreditGroup(val title: Int, val credits: List<Credit>)

/**
 * Open-source licences: what Artefact is built from, in groups (the document engines first), each
 * entry its role, licence and authors; a tap opens its licence text, the arrow its source.
 */
@Composable
private fun LicencesPage(onBack: () -> Unit) {
    val c = clear
    val context = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val mitApache = "MIT OR Apache-2.0"
    val groups = listOf(
        CreditGroup(R.string.licences_engines, listOf(
            Credit("PdfCraft", stringResource(R.string.licences_role_pdf), "$mitApache · storytold, the ArtCraft Team and contributors", "https://github.com/storytold/pdfcraft"),
            Credit("WordCraft", stringResource(R.string.licences_role_word), "$mitApache · storytold and contributors", "https://github.com/storytold/wordcraft"),
            Credit("DeckCraft", stringResource(R.string.licences_role_slides), "$mitApache · storytold and contributors", "https://github.com/storytold/deckcraft"),
            Credit("GridCraft", stringResource(R.string.licences_role_sheets), "$mitApache · storytold and contributors", "https://github.com/storytold/gridcraft"),
            Credit("LightCraft", stringResource(R.string.licences_role_photos), "$mitApache · storytold and contributors", "https://github.com/storytold/lightcraft"),
            Credit("Ocrs", stringResource(R.string.licences_role_ocr), "$mitApache · Robert Knight; models CC-BY-SA-4.0", "https://github.com/robertknight/ocrs"),
        )),
        CreditGroup(R.string.licences_interface, listOf(
            Credit("PdfCraft for Android", stringResource(R.string.licences_role_app), "$mitApache · the ArtCraft Team", "https://github.com/artcraft-android/pdfcraft-android"),
            Credit("AndroidLiquidGlass", stringResource(R.string.licences_role_glass), "Apache-2.0 · Kyant0", "https://github.com/Kyant0/AndroidLiquidGlass"),
            Credit("AndroidLiquidGlassView", stringResource(R.string.licences_role_tracker), "MIT · Donny Yale (QmDeve)", "https://github.com/QmDeve/AndroidLiquidGlassView"),
            Credit("Jetpack Compose, AndroidX, Kotlin coroutines", stringResource(R.string.licences_role_toolkit), "Apache-2.0 · Google, JetBrains", "https://developer.android.com/jetpack"),
            Credit("UniFFI", stringResource(R.string.licences_role_bridge), "MPL-2.0 · Mozilla", "https://github.com/mozilla/uniffi-rs"),
            Credit("JNA", stringResource(R.string.licences_role_bridge), "Apache-2.0 · the JNA authors", "https://github.com/java-native-access/jna"),
        )),
        CreditGroup(R.string.licences_type, listOf(
            Credit("Plus Jakarta Sans", stringResource(R.string.licences_role_ui_font), "OFL-1.1 · The Plus Jakarta Sans Project Authors", "https://github.com/tokotype/PlusJakartaSans", "OFL-PlusJakartaSans.txt"),
            Credit("Bricolage Grotesque", stringResource(R.string.licences_role_titles), "OFL-1.1 · The Bricolage Grotesque Project Authors", "https://github.com/ateliertriay/bricolage", "OFL-BricolageGrotesque.txt"),
            Credit("Fraunces", stringResource(R.string.licences_role_titles), "OFL-1.1 · The Fraunces Project Authors", "https://github.com/undercasetype/Fraunces", "OFL-Fraunces.txt"),
            Credit("DM Serif Display", stringResource(R.string.licences_role_titles), "OFL-1.1 · Colophon Foundry", "https://github.com/googlefonts/dm-fonts", "OFL-DMSerifDisplay.txt"),
            Credit("Doto", stringResource(R.string.licences_role_lists), "OFL-1.1 · The Doto Project Authors", "https://github.com/oliverlalan/Doto", "OFL-Doto.txt"),
            Credit("Dancing Script", stringResource(R.string.licences_role_signatures), "OFL-1.1 · The Dancing Script Project Authors", "https://github.com/googlefonts/DancingScript", "OFL-DancingScript.txt"),
            Credit("Source Sans 3, Source Serif 4", stringResource(R.string.licences_role_doc_fonts), "OFL-1.1 · Adobe", "https://github.com/adobe-fonts", "OFL-SourceSans3.txt"),
            Credit("Inter, JetBrains Mono", stringResource(R.string.licences_role_doc_fonts), "OFL-1.1 · The Inter and JetBrains Mono Project Authors", "https://github.com/rsms/inter"),
            Credit("Ubuntu Light", stringResource(R.string.licences_role_slide_font), "Ubuntu Font Licence 1.0 · Canonical Ltd., Dalton Maag", "https://design.ubuntu.com/font"),
        )),
        CreditGroup(R.string.licences_art, listOf(
            Credit("Lucide", stringResource(R.string.licences_role_icons), "ISC · Lucide Contributors", "https://lucide.dev", "LICENSE-lucide.txt"),
            Credit("bot-avatars", stringResource(R.string.licences_role_mascots), "MIT · Jakub Antalik", "https://github.com/jakubantalik"),
        )),
        CreditGroup(R.string.licences_data, listOf(
            Credit("PDFium font data", stringResource(R.string.licences_role_pdf_fonts), "BSD-3-Clause · Foxit Software Inc. and the PDFium Authors", "https://pdfium.googlesource.com/pdfium", "LICENSE-foxit.txt"),
            Credit("Adobe CMaps", stringResource(R.string.licences_role_cmaps), "BSD-3-Clause · Adobe", "https://github.com/adobe-type-tools/cmap-resources", "LICENSE-adobe-cmaps.txt"),
        )),
        CreditGroup(R.string.licences_platform, listOf(
            Credit("Rust and its crates", stringResource(R.string.licences_role_rust), stringResource(R.string.root_licence_rust_terms), "https://www.rust-lang.org"),
        )),
    )
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(null, stringResource(R.string.home_licences), onBack = onBack)
            Text(
                stringResource(R.string.licences_intro), style = ClearType.Body, color = c.inkSoft,
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 10.dp),
            )
        }
        for (g in groups) {
            item(key = "h${g.title}") { Section(stringResource(g.title)) }
            item(key = "p${g.title}") {
                Panel {
                    g.credits.forEachIndexed { i, k ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.5.dp).background(c.ink.copy(alpha = 0.08f)))
                        val expanded = open == k.name
                        Column(Modifier.fillMaxWidth().pressable({ open = if (expanded) null else k.name }).padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(k.name, style = ClearType.BodyStrong, color = c.ink)
                                    Text(k.role, style = ClearType.Caption, color = c.inkSoft)
                                }
                                Box(Modifier.size(36.dp).pressable({ uri.openUri(k.url) }), contentAlignment = Alignment.Center) {
                                    Icon(LucideIcons.ArrowUpRight, stringResource(R.string.licences_source, k.name), tint = c.inkSoft, modifier = Modifier.size(18.dp))
                                }
                            }
                            Text(k.terms, style = ClearType.Caption.copy(fontSize = 11.5.sp), color = c.inkFaint, modifier = Modifier.padding(top = 2.dp))
                            if (expanded) {
                                val text = remember(k.text) {
                                    k.text?.let { f -> runCatching { context.assets.open("licences/$f").bufferedReader().readText() }.getOrNull() }
                                }
                                Text(
                                    text ?: stringResource(R.string.licences_text_at_source),
                                    style = ClearType.Caption.copy(fontSize = 11.sp, lineHeight = 15.sp), color = c.inkSoft,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

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
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
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
import com.kyant.shapes.RoundedRectangle

/** Artefact's repository: help, issues and source. */
const val CLEARPDF_REPO = "https://github.com/Chethan616/ClearPDF"

/** Settings, and its Licences page. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var page by remember { mutableStateOf("root") }
    BackHandler { if (page == "root") vm.settingsOpen = false else page = "root" }
    when (page) {
        "licences" -> LicencesPage { page = "root" }
        else -> SettingsRoot(vm) { page = "licences" }
    }
}

@Composable
private fun SettingsRoot(vm: AppViewModel, openLicences: () -> Unit) {
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
        item(key = "hero") { AppCard(vm) }

        item(key = "look") { Section(stringResource(R.string.settings_look)) }
        item(key = "theme") {
            Choice(stringResource(R.string.settings_theme)) {
                val options = listOf(ThemeChoice.System, ThemeChoice.Light, ThemeChoice.Dark)
                GlassTabs(
                    listOf(
                        GlassTab(stringResource(R.string.settings_theme_system), LucideIcons.Contrast, Candy.Grape),
                        GlassTab(stringResource(R.string.settings_theme_light), LucideIcons.Sun, Candy.Tangerine),
                        GlassTab(stringResource(R.string.settings_theme_dark), LucideIcons.Moon, Candy.Sky),
                    ),
                    selected = options.indexOf(vm.theme).coerceAtLeast(0), onSelect = { vm.setThemeChoice(options[it]) },
                    modifier = Modifier.fillMaxWidth(), height = 64.dp,
                )
            }
        }
        item(key = "glass") {
            Choice(stringResource(R.string.settings_glass)) {
                val presets = GlassStyle.Presets
                GlassTabs(
                    listOf(
                        GlassTab(stringResource(R.string.settings_glass_clear), accent = Candy.Sky),
                        GlassTab(stringResource(R.string.settings_glass_frosted), accent = Candy.Mint),
                        GlassTab(stringResource(R.string.settings_glass_crystal), accent = Candy.Grape),
                        GlassTab(stringResource(R.string.settings_glass_lite), accent = Candy.Tangerine),
                    ),
                    selected = presets.indexOf(GlassSettings.style).coerceAtLeast(0), onSelect = { GlassSettings.update(context, presets[it]) },
                    modifier = Modifier.fillMaxWidth(), height = 48.dp,
                )
            }
        }
        item(key = "motion") {
            Choice(stringResource(R.string.settings_motion)) {
                val presets = MotionStyle.Presets
                GlassTabs(
                    listOf(
                        GlassTab(stringResource(R.string.settings_motion_calm), accent = Candy.Mint),
                        GlassTab(stringResource(R.string.settings_motion_normal), accent = Candy.Sky),
                        GlassTab(stringResource(R.string.settings_motion_bouncy), accent = Candy.Bubblegum),
                        GlassTab(stringResource(R.string.settings_motion_jelly), accent = Candy.Tomato),
                    ),
                    selected = presets.indexOf(MotionSettings.style).coerceAtLeast(0), onSelect = { MotionSettings.update(context, presets[it]) },
                    modifier = Modifier.fillMaxWidth(), height = 48.dp,
                )
            }
        }
        item(key = "feel") {
            Panel {
                ToggleRow(LucideIcons.Sparkles, Candy.Bubblegum, stringResource(R.string.settings_haptics), null, GlassSettings.haptics) { GlassSettings.updateHaptics(context, it) }
                GlassRow(stringResource(R.string.settings_show_welcome), { vm.showWelcomeAgain() }, icon = LucideIcons.Compass, accent = Candy.Sky)
            }
        }

        item(key = "reading") { Section(stringResource(R.string.settings_reading)) }
        item(key = "readingpanel") {
            Panel {
                ToggleRow(LucideIcons.PanelsTopLeft, Candy.Sky, stringResource(R.string.settings_quick_preview), stringResource(R.string.settings_quick_preview_body), vm.quickPreview) { vm.updateQuickPreview(it) }
                ToggleRow(LucideIcons.Sun, Candy.Lemon, stringResource(R.string.home_keep_screen_on), stringResource(R.string.home_keep_screen_on_body), vm.keepScreenOn) { vm.updateKeepScreenOn(it) }
                ToggleRow(LucideIcons.Smartphone, Candy.Sky, stringResource(R.string.home_status_bar_in_documents), stringResource(R.string.home_status_bar_in_documents_body), vm.statusBarInDocuments) { vm.updateStatusBarInDocuments(it) }
                ToggleRow(LucideIcons.Hash, Candy.Mint, stringResource(R.string.home_search_count), stringResource(R.string.home_search_count_body), vm.searchCount) { vm.updateSearchCount(it) }
                ToggleRow(LucideIcons.SquareTerminal, Candy.Grape, stringResource(R.string.home_keyboard_shortcuts), stringResource(R.string.home_keyboard_shortcuts_body), vm.keyboardShortcuts) { vm.updateKeyboardShortcuts(it) }
            }
        }

        item(key = "forms") { Section(stringResource(R.string.settings_forms)) }
        item(key = "formspanel") {
            Panel {
                ToggleRow(LucideIcons.TextCursorInput, Candy.Tangerine, stringResource(R.string.home_offer_suggestions), null, vm.formSuggestions) { vm.updateFormSuggestions(it) }
                GlassRow(stringResource(R.string.home_clear_suggestions), { vm.clearFormSuggestions() }, icon = LucideIcons.BrushCleaning, accent = Candy.Tomato)
                GlassRow(
                    stringResource(R.string.home_author_title), { authorDialog = true }, icon = LucideIcons.UserRound, accent = Candy.Sky,
                    value = vm.toolPrefs.author.ifBlank { stringResource(R.string.home_author_hint) },
                )
            }
        }

        item(key = "about") { Section(stringResource(R.string.settings_about)) }
        item(key = "aboutpanel") {
            Panel {
                GlassRow(stringResource(R.string.home_report_problem), { leaving = "$CLEARPDF_REPO/issues" }, icon = LucideIcons.MessageSquare, accent = Candy.Tomato)
                GlassRow(stringResource(R.string.home_source_code), { uri.openUri(CLEARPDF_REPO) }, icon = LucideIcons.CodeXml, accent = Candy.Grape)
                GlassRow(stringResource(R.string.home_engine), { uri.openUri("https://github.com/storytold/pdfcraft") }, icon = LucideIcons.Wrench, accent = Candy.Tangerine)
                GlassRow(stringResource(R.string.home_licences), openLicences, icon = LucideIcons.ScrollText, accent = Candy.Mint)
            }
        }
        item(key = "credits") {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(), shape = RoundedRectangle(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.settings_credits_title), style = ClearType.Title, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.settings_credits_body), style = ClearType.Body, color = c.inkSoft)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_legal_notice), style = ClearType.Caption, color = c.inkFaint)
                }
            }
        }
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

/** The app's own card: its icon, its name, the version (long-press copies it). */
@Composable
private fun AppCard(vm: AppViewModel) {
    val c = clear
    val clipboard = LocalClipboardManager.current
    val version = stringResource(R.string.home_version, vm.appVersion, com.chethan616.clearpdf.core.engineVersion())
    val copied = stringResource(R.string.home_version_copied)
    GlassCard(
        Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth()
            .pointerInput(version) { detectTapGestures(onLongPress = { clipboard.setText(AnnotatedString(version)); vm.tell(copied) }) },
        shape = RoundedRectangle(30.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.artefact_logo), null, Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.app_name), style = ClearType.Headline, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(version, style = ClearType.Caption, color = c.inkSoft)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = ClearType.Headline, color = clear.ink, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp))
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
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    WashPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(6.dp))
    }
}

/** A switch row: a candy blob, the setting's name and what it does, and a glass toggle. */
@Composable
private fun ToggleRow(icon: ImageVector, accent: Color, label: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = clear
    Row(
        Modifier.fillMaxWidth().pressable({ onChange(!checked) }).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedRectangle(11.dp)).background(accent.copy(alpha = if (c.dark) 0.26f else 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = ClearType.BodyStrong, color = c.ink)
            if (body != null) Text(body, style = ClearType.Caption, color = c.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        GlassToggle(checked, onChange, accent = accent)
    }
}

/** One entry of the Licences page: what it is, and its licence and authors. */
private class Credit(val name: String, val terms: String)

/** The open-source licences of everything Artefact is built from, then their full texts. */
@Composable
private fun LicencesPage(onBack: () -> Unit) {
    val c = clear
    val context = LocalContext.current
    val texts = remember {
        listOf("LICENSE-lucide.txt", "OFL-PlusJakartaSans.txt", "OFL-Doto.txt", "OFL-DMSerifDisplay.txt", "OFL-DancingScript.txt", "LICENSE-foxit.txt", "LICENSE-adobe-cmaps.txt").mapNotNull { name ->
            runCatching { context.assets.open("licences/$name").bufferedReader().readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }
    val credits = listOf(
        Credit(stringResource(R.string.root_licence_pdfcraft), stringResource(R.string.root_licence_pdfcraft_terms)),
        Credit("AndroidLiquidGlass (backdrop)", "Apache-2.0, Kyant0"),
        Credit("AndroidLiquidGlassView (LiquidTracker)", "MIT, Donny Yale (QmDeve)"),
        Credit("UniFFI", "MPL-2.0, Mozilla"),
        Credit("JNA", "Apache-2.0"),
        Credit("AndroidX, Jetpack Compose, Kotlin coroutines", "Apache-2.0"),
        Credit(stringResource(R.string.root_licence_lucide), "ISC, Lucide Contributors"),
        Credit("Plus Jakarta Sans", "SIL Open Font License 1.1, The Plus Jakarta Sans Project Authors"),
        Credit("Doto", "SIL Open Font License 1.1, The Doto Project Authors"),
        Credit("DM Serif Display", "SIL Open Font License 1.1, Colophon Foundry"),
        Credit("bot-avatars (the mascots)", "MIT, Jakub Antalik"),
        Credit(stringResource(R.string.root_licence_dancing_script), "SIL Open Font License 1.1, The Dancing Script Project Authors"),
        Credit(stringResource(R.string.root_licence_foxit), "BSD-3-Clause, Foxit Software Inc. and the PDFium Authors"),
        Credit(stringResource(R.string.root_licence_cmaps), "BSD-3-Clause, Adobe"),
        Credit(stringResource(R.string.root_licence_ocrs), stringResource(R.string.root_licence_ocrs_terms)),
        Credit(stringResource(R.string.root_licence_rust), stringResource(R.string.root_licence_rust_terms)),
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(null, stringResource(R.string.home_licences), onBack = onBack)
            Spacer(Modifier.height(16.dp))
        }
        item {
            Panel {
                credits.forEach { k ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(k.name, style = ClearType.BodyStrong, color = c.ink)
                        Text(k.terms, style = ClearType.Caption, color = c.inkSoft)
                    }
                }
            }
        }
        items(texts.size) { i ->
            WashPanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                Text(texts[i], style = ClearType.Caption, color = c.inkSoft, modifier = Modifier.padding(18.dp))
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

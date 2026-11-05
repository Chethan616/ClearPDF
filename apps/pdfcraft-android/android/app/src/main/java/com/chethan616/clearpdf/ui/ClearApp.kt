package com.chethan616.clearpdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.desk.DeskScreen
import com.chethan616.clearpdf.ui.desk.FileActionsSheet
import com.chethan616.clearpdf.ui.desk.FilesScreen
import com.chethan616.clearpdf.ui.desk.SettingsScreen
import com.chethan616.clearpdf.ui.desk.ToolCatalog
import com.chethan616.clearpdf.ui.desk.ToolsScreen
import com.chethan616.clearpdf.ui.glass.Aurora
import com.chethan616.clearpdf.ui.glass.AuroraMoods
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassOverlay
import com.chethan616.clearpdf.ui.glass.GlassOverlayHost
import com.chethan616.clearpdf.ui.glass.LocalGlassOverlayHost
import com.chethan616.clearpdf.ui.glass.GlassSheet
import com.chethan616.clearpdf.ui.glass.GlassStage
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.GlassTextField
import com.chethan616.clearpdf.ui.glass.GlassToast
import com.chethan616.clearpdf.ui.glass.ProvideGlassHaptics
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.HomeTab
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearTheme
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.CertSignScreen
import com.chethan616.clearpdf.ui.viewer.CreateIdScreen
import com.chethan616.clearpdf.ui.viewer.IdPasswordDialog
import com.chethan616.clearpdf.ui.viewer.SignFieldChoiceDialog
import com.chethan616.clearpdf.ui.viewer.SignaturePadScreen
import com.chethan616.clearpdf.ui.viewer.ViewerScreen
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay

/** The whole app: the glass stage with the tabs (or a document, or Settings), the dock, and every dialog. */
@Composable
fun ClearApp(vm: AppViewModel, onBrowse: () -> Unit, onPickSignatureImage: () -> Unit) {
    val overlays = remember { GlassOverlayHost() }
    ClearTheme(vm.theme) {
        PdfcraftTheme(vm.theme) {
            ProvideGlassHaptics {
                CompositionLocalProvider(LocalGlassOverlayHost provides overlays) { Shell(vm, onBrowse, onPickSignatureImage) }
            }
        }
    }
}

@Composable
private fun Shell(vm: AppViewModel, onBrowse: () -> Unit, onPickSignatureImage: () -> Unit) {
    val c = clear
    val doc = vm.document
    var toast by remember { mutableStateOf<Message?>(null) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        for (m in vm.messages) {
            toast = m
            delay(if (m.actionLabel != null) 6_000 else 3_200)
            toast = null
            delay(220)
        }
    }
    // outside a document the bar icons follow the app's theme (the viewer sets its own)
    val view = LocalView.current
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        if (doc == null) {
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !c.dark
            bars.isAppearanceLightNavigationBars = !c.dark
        }
    }
    BackHandler(enabled = doc != null && vm.toolScreen == null) { vm.closeDocument() }
    BackHandler(enabled = doc == null && !vm.settingsOpen && vm.homeTab != HomeTab.Home && vm.toolScreen == null) { vm.homeTab = HomeTab.Home }

    val onTabs = doc == null && !vm.settingsOpen
    val dockVisible = onTabs && vm.toolScreen == null && vm.signaturePad == null && vm.certSign == null && !vm.idCreate && vm.selectedFiles.isEmpty()
    val mood = when {
        vm.settingsOpen -> AuroraMoods.Settings
        vm.homeTab == HomeTab.Files -> AuroraMoods.Files
        vm.homeTab == HomeTab.Tools -> AuroraMoods.Tools
        else -> AuroraMoods.Desk
    }

    GlassStage(
        wallpaper = { Aurora(mood) },
        screens = {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                when {
                    doc != null -> Viewer(vm, doc)
                    vm.settingsOpen -> Entrance("settings", fromSide = true) { SettingsScreen(vm) }
                    else -> Entrance(vm.homeTab) {
                        when (vm.homeTab) {
                            HomeTab.Home -> DeskScreen(vm, onBrowse)
                            HomeTab.Files -> FilesScreen(vm, onBrowse)
                            HomeTab.Tools -> ToolsScreen(vm)
                        }
                    }
                }
                vm.toolScreen?.let { ToolScreenHost(vm, it) }
                vm.signaturePad?.let { SignaturePadScreen(vm, it, onPickSignatureImage) }
                vm.certSign?.let { CertSignScreen(vm, it) }
                if (vm.idCreate) CreateIdScreen(vm)
            }
        },
        chrome = {
            if (dockVisible) Dock(vm, creating) { creating = !creating }
            toast?.let { m ->
                Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (dockVisible) 96.dp else 20.dp),
                ) { key(m) { GlassToast(m.text, m.actionLabel, { m.action?.invoke(); toast = null }) } }
            }
        },
    )

    if (creating) CreateSheet(vm, onBrowse) { creating = false }
    if (doc == null) vm.fileSheet?.let { (r, recent) -> FileActionsSheet(vm, r, recent) { vm.fileSheet = null } }
    if (vm.opening) Spinner()
    Dialogs(vm)
}

/** The document viewer, laid out as it expects (sideways it keeps clear of the camera and buttons). */
@Composable
private fun Viewer(vm: AppViewModel, doc: OpenDocument) {
    val fullWidth = vm.statusBarInDocuments
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(
                if (fullWidth) WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal) else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ).then(if (fullWidth) Modifier else Modifier.background(pdfColors.surface)),
        ) { ViewerScreen(vm, doc) }
    }
}

/**
 * A screen arriving: it rises a little and fades in on a soft spring ([fromSide]: slides in from
 * the end, for pushed pages). Only the arriving screen is composed.
 */
@Composable
private fun Entrance(key: Any, fromSide: Boolean = false, content: @Composable () -> Unit) {
    key(key) {
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) { p.animateTo(1f, GlassMotion.rise()) }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val v = p.value
                if (fromSide) translationX = (1f - v) * 48.dp.toPx() else translationY = (1f - v) * 22.dp.toPx()
                alpha = v.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        ) { content() }
    }
}

/** The floating dock: Desk, Files and Tools on a glass capsule, and the candy + that creates. */
@Composable
private fun BoxScope.Dock(vm: AppViewModel, creating: Boolean, onCreate: () -> Unit) {
    val turn by animateFloatAsState(if (creating) 45f else 0f, GlassMotion.morph(), label = "plus")
    Row(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabs = listOf(HomeTab.Home, HomeTab.Files, HomeTab.Tools)
        GlassTabs(
            tabs = listOf(
                GlassTab(stringResource(R.string.dock_desk), LucideIcons.House, Candy.Tomato),
                GlassTab(stringResource(R.string.dock_files), LucideIcons.Folder, Candy.Sky),
                GlassTab(stringResource(R.string.dock_tools), LucideIcons.LayoutGrid, Candy.Grape),
            ),
            selected = tabs.indexOf(vm.homeTab).coerceAtLeast(0),
            onSelect = { vm.homeTab = tabs[it] },
            modifier = Modifier.width(252.dp),
            height = 64.dp,
        )
        GlassCircle(onClick = onCreate, size = 64.dp, tint = Candy.Tomato, label = stringResource(R.string.dock_create)) {
            GlassIcon(LucideIcons.Plus, null, Modifier.size(30.dp).graphicsLayer { rotationZ = turn })
        }
    }
}

/** "Start something": the ways in, as a grid of candy tiles. */
@Composable
private fun CreateSheet(vm: AppViewModel, onBrowse: () -> Unit, onDismiss: () -> Unit) {
    fun go(f: () -> Unit) {
        onDismiss()
        f()
    }
    GlassSheet(onDismiss, stringResource(R.string.create_title), stringResource(R.string.create_subtitle)) {
        val items = listOf(
            Triple(LucideIcons.FolderOpen, stringResource(R.string.home_open_file), Candy.Sky) to onBrowse,
            Triple(ToolCatalog.Scan.icon, stringResource(ToolCatalog.Scan.label), ToolCatalog.Scan.accent) to { ToolCatalog.Scan.go(vm) },
            Triple(ToolCatalog.Photos.icon, stringResource(ToolCatalog.Photos.label), ToolCatalog.Photos.accent) to { ToolCatalog.Photos.go(vm) },
            Triple(ToolCatalog.Combine.icon, stringResource(ToolCatalog.Combine.label), Candy.Grape) to { ToolCatalog.Combine.go(vm) },
            Triple(ToolCatalog.Create.icon, stringResource(ToolCatalog.Create.label), Candy.Tangerine) to { ToolCatalog.Create.go(vm) },
            Triple(ToolCatalog.Edit.icon, stringResource(ToolCatalog.Edit.label), ToolCatalog.Edit.accent) to { ToolCatalog.Edit.go(vm) },
        )
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (spec, action) ->
                        val (icon, label, accent) = spec
                        CreateTile(icon, label, accent, Modifier.weight(1f)) { go(action) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTile(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(112.dp).pressable(onClick, pressedScale = 0.92f).clip(RoundedRectangle(24.dp)).background(accent).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(label, style = ClearType.Label, color = Color.White, maxLines = 2)
    }
}

/** A candy spinner over everything while a document opens. */
@Composable
private fun Spinner() {
    GlassOverlay {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(44.dp), color = Candy.Tomato, strokeWidth = 4.dp)
        }
    }
}

/** Everything the view model can ask: passwords, read-only files, links, form alerts, waits. */
@Composable
private fun Dialogs(vm: AppViewModel) {
    val c = clear
    vm.passwordRequest?.let { PasswordDialog(vm, it) }
    vm.idImport?.let { IdPasswordDialog(vm, it) }
    vm.signFieldChoice?.let { (d, w) -> SignFieldChoiceDialog(vm, d, w) }
    if (vm.authorPrompt) {
        var name by remember { mutableStateOf("") }
        GlassDialog(
            onDismiss = { vm.authorPrompt = false }, title = stringResource(R.string.home_author_title), icon = LucideIcons.UserRound, accent = Candy.Sky,
            confirm = stringResource(R.string.common_save), onConfirm = { vm.toolPrefs.author = name.trim(); vm.authorPrompt = false; vm.prefsChanged++ },
            dismiss = stringResource(R.string.root_skip),
        ) {
            Text(stringResource(R.string.root_author_prompt_body), style = ClearType.Body, color = c.inkSoft)
            Spacer(Modifier.height(14.dp))
            GlassTextField(name, { name = it }, stringResource(R.string.common_your_name))
        }
    }
    vm.readOnlyPrompt?.let { d ->
        GlassDialog(
            onDismiss = { vm.answerReadOnly(null) }, title = stringResource(R.string.root_read_only_title), icon = LucideIcons.Lock, accent = Candy.Tangerine,
            confirm = stringResource(R.string.common_save_copy), onConfirm = { vm.answerReadOnly(true) },
            dismiss = stringResource(R.string.root_discard), onDismissButton = { vm.answerReadOnly(false) },
        ) { Text(stringResource(R.string.root_read_only_body, d.name), style = ClearType.Body, color = c.inkSoft) }
    }
    vm.linkPrompt?.let { url ->
        val shown = runCatching { android.net.Uri.parse(url).let { u -> "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}" } }.getOrDefault(url)
        GlassDialog(
            onDismiss = { vm.answerLink(false) }, title = stringResource(R.string.root_open_link_title), icon = LucideIcons.Link, accent = Candy.Sky,
            confirm = stringResource(R.string.common_open), onConfirm = { vm.answerLink(true) }, dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.root_open_link_body, shown), style = ClearType.Body, color = c.inkSoft) }
    }
    vm.scriptAlerts.firstOrNull()?.let { message ->
        GlassDialog(onDismiss = vm::dismissScriptAlert, title = null, icon = LucideIcons.Info, accent = Candy.Grape, confirm = stringResource(R.string.common_ok), onConfirm = vm::dismissScriptAlert) {
            Text(message, style = ClearType.Body, color = c.ink)
        }
    }
    vm.confirmDeleteAllBookmarks?.let { d ->
        GlassDialog(
            onDismiss = { vm.confirmDeleteAllBookmarks = null }, title = stringResource(R.string.root_delete_bookmarks_title), icon = LucideIcons.BookmarkMinus, destructive = true,
            confirm = stringResource(R.string.root_delete_all), onConfirm = { vm.confirmDeleteAllBookmarks = null; vm.deleteAllBookmarks(d) }, dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.root_delete_bookmarks_body), style = ClearType.Body, color = c.inkSoft) }
    }
    vm.readingModeBlocked?.let {
        GlassDialog(
            onDismiss = { vm.readingModeBlocked = null }, title = stringResource(R.string.root_reading_mode_title), icon = LucideIcons.BookOpenText, accent = Candy.Sky,
            confirm = stringResource(R.string.common_ok), onConfirm = { vm.readingModeBlocked = null },
        ) { Text(stringResource(R.string.root_reading_mode_body), style = ClearType.Body, color = c.inkSoft) }
    }
    vm.busy?.let { message ->
        GlassDialog(onDismiss = {}, title = null, dismissible = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(30.dp), color = Candy.Tomato, strokeWidth = 3.5.dp)
                Spacer(Modifier.width(18.dp))
                Text(message, style = ClearType.BodyStrong, color = c.ink, textAlign = TextAlign.Start)
            }
        }
    }
}

/** "Enter password": the file's name in italics, a masked field; a wrong one turns it tomato. */
@Composable
private fun PasswordDialog(vm: AppViewModel, req: PasswordRequest) {
    val c = clear
    var pw by remember(req) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(req) { runCatching { focus.requestFocus() } }
    val body = stringResource(R.string.root_password_body)
    GlassDialog(
        onDismiss = { vm.answerPassword(null) }, title = stringResource(R.string.root_enter_password), icon = LucideIcons.LockOpen, accent = Candy.Grape,
        confirm = stringResource(R.string.common_ok), onConfirm = { vm.answerPassword(pw) }, dismiss = stringResource(R.string.common_cancel),
    ) {
        Text(
            buildAnnotatedString {
                val at = body.indexOf("%1\$s")
                if (at < 0) append(body) else {
                    append(body.substring(0, at))
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(req.file.name) }
                    append(body.substring(at + "%1\$s".length))
                }
            },
            style = ClearType.Body, color = c.inkSoft,
        )
        Spacer(Modifier.height(14.dp))
        GlassTextField(
            pw, { pw = it }, stringResource(R.string.common_password), password = true, error = req.wrong && pw.isEmpty(), focus = focus,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (pw.isNotEmpty()) vm.answerPassword(pw) }),
        )
    }
}

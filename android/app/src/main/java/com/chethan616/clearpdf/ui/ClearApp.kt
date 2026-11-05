package com.chethan616.clearpdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
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
import com.chethan616.clearpdf.ui.glass.GlassLoader
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.GlassTextField
import com.chethan616.clearpdf.ui.glass.GlassToast
import com.chethan616.clearpdf.ui.glass.ProvideGlassHaptics
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.CreateDock
import com.chethan616.clearpdf.ui.home.DeskState
import com.chethan616.clearpdf.ui.home.HomeScreen
import com.chethan616.clearpdf.ui.home.HomeTab
import com.chethan616.clearpdf.ui.home.LocalHomeVm
import com.chethan616.clearpdf.ui.notes.NoteEditor
import com.chethan616.clearpdf.ui.onboarding.Onboarding
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
                CompositionLocalProvider(LocalGlassOverlayHost provides overlays, LocalHomeVm provides vm) { Shell(vm, onBrowse, onPickSignatureImage) }
            }
        }
    }
}

@Composable
private fun Shell(vm: AppViewModel, onBrowse: () -> Unit, onPickSignatureImage: () -> Unit) {
    val c = clear
    val doc = vm.document
    var toast by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
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
    BackHandler(enabled = doc == null && vm.onboarded && !vm.settingsOpen && vm.openNote == null && vm.homeTab != HomeTab.Home && vm.toolScreen == null) { vm.homeTab = HomeTab.Home }

    val route = when {
        !vm.onboarded -> "welcome"
        vm.settingsOpen -> "settings"
        vm.openNote != null -> "note:" + vm.openNote
        else -> vm.homeTab.name
    }
    val onDesk = doc == null && route == HomeTab.Home.name && vm.toolScreen == null && vm.signaturePad == null && vm.certSign == null && !vm.idCreate
    val desk = remember { DeskState(scope) }
    val mood = when {
        route == "welcome" -> AuroraMoods.Welcome
        vm.settingsOpen -> AuroraMoods.Settings
        vm.openNote != null -> AuroraMoods.Note
        vm.homeTab == HomeTab.Files -> AuroraMoods.Files
        vm.homeTab == HomeTab.Tools -> AuroraMoods.Tools
        else -> AuroraMoods.Desk
    }

    GlassStage(
        wallpaper = { Aurora(mood) },
        screens = {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                AnimatedContent(route, transitionSpec = { push(depth(initialState), depth(targetState)) }, label = "route") { r ->
                    when {
                        r == "welcome" -> Onboarding(vm)
                        r == "settings" -> SettingsScreen(vm)
                        r.startsWith("note:") -> NoteEditor(vm, r.removePrefix("note:"))
                        r == HomeTab.Files.name -> FilesScreen(vm, onBrowse)
                        r == HomeTab.Tools.name -> ToolsScreen(vm)
                        else -> HomeScreen(vm, desk)
                    }
                }
                vm.toolScreen?.let { ToolScreenHost(vm, it) }
                vm.signaturePad?.let { SignaturePadScreen(vm, it, onPickSignatureImage) }
                vm.certSign?.let { CertSignScreen(vm, it) }
                if (vm.idCreate) CreateIdScreen(vm)
            }
            // the document opens over the desk, zooming up from it
            AnimatedVisibility(
                doc != null,
                enter = fadeIn(GlassMotion.settle()) + scaleIn(GlassMotion.morph(), initialScale = 0.9f),
                exit = fadeOut(GlassMotion.fold()) + scaleOut(GlassMotion.fold(), targetScale = 0.94f),
            ) {
                val last = remember { arrayOfNulls<OpenDocument>(1) }
                if (doc != null) last[0] = doc
                last[0]?.let { Viewer(vm, it) }
            }
        },
        chrome = {
            if (onDesk) CreateDock(vm, desk, onBrowse)
            toast?.let { m ->
                Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (onDesk) 100.dp else 20.dp),
                ) { key(m) { GlassToast(m.text, m.actionLabel, { m.action?.invoke(); toast = null }) } }
            }
        },
    )

    if (doc == null) vm.fileSheet?.let { (r, recent) -> FileActionsSheet(vm, r, recent) { vm.fileSheet = null } }
    if (vm.opening) Spinner()
    Dialogs(vm)
}

/** The document viewer, laid out as it expects (sideways it keeps clear of the camera and buttons). */
@Composable
private fun Viewer(vm: AppViewModel, doc: OpenDocument) {
    val fullWidth = vm.statusBarInDocuments
    Box(Modifier.fillMaxSize().background(pdfColors.desk)) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(
                if (fullWidth) WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal) else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ).then(if (fullWidth) Modifier else Modifier.background(pdfColors.surface)),
        ) { ViewerScreen(vm, doc) }
    }
}

/** The wait while a document opens: the expressive loader in its glass circle. */
@Composable
private fun Spinner() {
    GlassOverlay {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GlassLoader(size = 76.dp) }
    }
}

/** How deep a route is: deeper ones push in from the end, going back slides the other way. */
private fun depth(route: String): Int = when {
    route == "welcome" || route == HomeTab.Home.name -> 0
    route.startsWith("note:") -> 2
    route == "settings" -> 2
    else -> 1
}

/** A push (or a pop): the new screen slides and grows in on a spring while the old one shrinks back. */
private fun push(from: Int, to: Int): ContentTransform {
    val forward = to >= from
    val dir = if (forward) 1 else -1
    return (slideInHorizontally(GlassMotion.morph()) { it * dir / 3 } + fadeIn(GlassMotion.settle()) + scaleIn(GlassMotion.morph(), initialScale = if (forward) 0.96f else 1.04f)) togetherWith
        (slideOutHorizontally(GlassMotion.fold()) { -it * dir / 5 } + fadeOut(GlassMotion.fold()) + scaleOut(GlassMotion.fold(), targetScale = if (forward) 0.94f else 1.04f))
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
                GlassLoader(size = 48.dp)
                Spacer(Modifier.width(16.dp))
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

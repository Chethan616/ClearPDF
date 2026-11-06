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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import com.chethan616.clearpdf.ui.glass.Fluid
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
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
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
    var dockIntoNote by remember { mutableStateOf(false) }
    var dockFromNote by remember { mutableStateOf(false) }
    val mood = when {
        route == "welcome" -> AuroraMoods.Welcome
        vm.settingsOpen -> AuroraMoods.Settings
        vm.openNote != null -> AuroraMoods.Note
        vm.homeTab == HomeTab.Files -> AuroraMoods.Files
        vm.homeTab == HomeTab.Tools -> AuroraMoods.Tools
        else -> AuroraMoods.Desk
    }

    val reveal = remember { RevealState() }
    // under an open document nothing of the desk shows: skip drawing it (and its glass) entirely
    val underDoc = Modifier.drawWithContent { if (!reveal.covering) drawContent() }
    GlassStage(
        wallpaper = { Aurora(mood, underDoc) },
        screens = {
            Box(Modifier.fillMaxSize().then(underDoc).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                RouteHost(route) { r ->
                    when {
                        r == "welcome" -> Onboarding(vm)
                        r == "settings" -> SettingsScreen(vm)
                        r.startsWith("note:") -> NoteEditor(vm, r.removePrefix("note:"))
                        r == HomeTab.Files.name -> FilesScreen(vm, onBrowse)
                        r == HomeTab.Tools.name -> ToolsScreen(vm)
                        else -> HomeScreen(vm, desk)
                    }
                }
            }
            // the document rises over the desk (translation and a cheap fade: no scaling, no layer)
            Reveal(doc, reveal) { Viewer(vm, it) }
            // a tool's own screen opens over whatever it was started from: the desk or a document
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                vm.toolScreen?.let { ToolScreenHost(vm, it) }
                vm.signaturePad?.let { SignaturePadScreen(vm, it, onPickSignatureImage) }
                vm.certSign?.let { CertSignScreen(vm, it) }
                if (vm.idCreate) CreateIdScreen(vm)
            }
        },
        chrome = {
            // a note opened from the desk: the dock stays a moment and becomes the note's toolbar; back
            // on the desk, the note's toolbar becomes the dock again
            val lastRoute = remember { arrayOf(route) }
            if (lastRoute[0] != route) {
                val intoNote = lastRoute[0] == HomeTab.Home.name && route.startsWith("note:") && doc == null
                val fromNote = lastRoute[0].startsWith("note:") && route == HomeTab.Home.name && doc == null
                if (intoNote != dockIntoNote) dockIntoNote = intoNote
                if (fromNote != dockFromNote) dockFromNote = fromNote
                if (vm.noteBarHidden != (intoNote || fromNote)) vm.noteBarHidden = intoNote || fromNote
                lastRoute[0] = route
            }
            if (onDesk || dockIntoNote) {
                CreateDock(vm, desk, onBrowse, intoNote = !onDesk, fromNote = onDesk && dockFromNote) {
                    dockIntoNote = false
                    dockFromNote = false
                    vm.noteBarHidden = false
                }
            }
            toast?.let { m ->
                Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (onDesk) 100.dp else 20.dp),
                ) { key(m) { GlassToast(m.text, m.actionLabel, { m.action?.invoke(); toast = null }) } }
            }
        },
    )

    if (doc == null) vm.fileSheet?.let { (r, recent) -> FileActionsSheet(vm, r, recent) { vm.fileSheet = null } }
    // the loader only for an open that takes a while: a quick one shouldn't flash a spinner first
    var slowOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vm.opening) {
        slowOpen = false
        if (vm.opening) {
            kotlinx.coroutines.delay(350)
            slowOpen = true
        }
    }
    if (vm.opening && slowOpen) Spinner()
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
        ) { com.chethan616.clearpdf.ui.glass.ProvideScreenInk { ViewerScreen(vm, doc) } }
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

/**
 * The screens, one route at a time, moved the way iOS pushes: on one eased curve (quick to start,
 * long and soft to land, never past its place; no spring, and Settings ▸ Motion doesn't touch it).
 * The new screen glides in from the side as the old one drifts the other way and fades out ahead
 * of it; Back mirrors it, a touch quicker. Both stay one composition throughout (keyed, so the old
 * screen is moved, not rebuilt), each route keeps its state, and the fades modulate alpha per draw
 * instead of rendering whole screens offscreen. The new screen is composed at alpha 0, so a slow
 * first frame is never seen.
 */
@Composable
private fun RouteHost(route: String, content: @Composable (String) -> Unit) {
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var shown by remember { mutableStateOf(route) }
    var leaving by remember { mutableStateOf<String?>(null) }
    var forward by remember { mutableStateOf(true) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(route) {
        if (route == shown) return@LaunchedEffect
        forward = depth(route) >= depth(shown)
        leaving = shown
        shown = route
        p.snapTo(0f)
        p.animateTo(1f, if (forward) Fluid.push() else Fluid.pop())
        leaving = null
    }
    // The desk stays composed (unseen, untouchable) under the screens pushed over it, so coming back to
    // it rebuilds nothing: no cards, thumbnails or glass appearing a moment after the screen has.
    val desk = HomeTab.Home.name
    var deskSeen by remember { mutableStateOf(route == desk) }
    if (shown == desk) deskSeen = true
    val routes = buildList {
        if (deskSeen && desk != leaving && desk != shown) add(desk)
        leaving?.let(::add)
        add(shown)
    }
    Box(Modifier.fillMaxSize()) {
        for (r in routes) {
            key(r) {
                val isNew = r == shown
                val hidden = r != shown && r != leaving
                Box(
                    Modifier.fillMaxSize().then(if (hidden) BlockTouches else Modifier).graphicsLayer {
                        if (hidden) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val v = p.value
                        val dir = if (forward) 1f else -1f
                        if (isNew) {
                            translationX = dir * size.width * 0.24f * (1f - v)
                            alpha = (v * 1.25f).coerceIn(0f, 1f)
                        } else {
                            translationX = -dir * size.width * 0.10f * v
                            alpha = (1f - v * 1.6f).coerceIn(0f, 1f)
                        }
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                ) { holder.SaveableStateProvider(r) { content(r) } }
            }
        }
    }
}

/** Takes every touch before anything inside sees it (a screen kept alive but not shown). */
private val BlockTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** Where the last touch went down, in window pixels (MainActivity records it). */
object LastTouch {
    @JvmField var x = Float.NaN
    @JvmField var y = Float.NaN
}

/** How far a document has risen over the desk: 0 gone, 1 covering it. */
@androidx.compose.runtime.Stable
private class RevealState {
    val progress = Animatable(0f)

    /** Fully over the desk: what's beneath needn't be drawn at all. */
    val covering by androidx.compose.runtime.derivedStateOf { progress.value >= 1f }
}

/**
 * [value] rising into view while it is set, on the eased curve a sheet rises on, and falling away
 * (quicker) once it's null, still drawn as it was.
 */
@Composable
private fun <T : Any> Reveal(value: T?, state: RevealState, content: @Composable (T) -> Unit) {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    val p = state.progress
    // the spot it grows out of: where the finger was when it was opened, kept for the way back
    val from = remember { floatArrayOf(Float.NaN, Float.NaN) }
    LaunchedEffect(value != null) {
        if (value != null) {
            from[0] = LastTouch.x
            from[1] = LastTouch.y
            // a soft spring that never overshoots: the page lands, it doesn't bounce
            p.animateTo(1f, androidx.compose.animation.core.spring(1f, com.chethan616.clearpdf.ui.glass.MotionSettings.stiffness(260f), 0.001f))
        } else {
            p.animateTo(0f, Fluid.dismiss())
        }
        if (value == null) last[0] = null
    }
    // read through a derived state: the content recomposes when it's gone, not on every frame
    val gone by remember { androidx.compose.runtime.derivedStateOf { p.value <= 0f } }
    @Suppress("UNCHECKED_CAST")
    val shown = last[0] as T? ?: return
    if (gone && value == null) return
    val origin = remember { arrayOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin[0] = it.positionInWindow() }
            .graphicsLayer {
                val v = p.value.coerceIn(0f, 1f)
                // zooms out of the touch, iOS's way of opening from what was tapped; with no touch
                // (opened from elsewhere) from the middle
                val fx = if (from[0].isNaN() || size.width <= 0f) 0.5f else ((from[0] - origin[0].x) / size.width).coerceIn(0f, 1f)
                val fy = if (from[1].isNaN() || size.height <= 0f) 0.5f else ((from[1] - origin[0].y) / size.height).coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(fx, fy)
                val s = 0.3f + 0.7f * v
                scaleX = s
                scaleY = s
                alpha = (v * 2.5f).coerceIn(0f, 1f)
                // corners round while it's small, square once it fills the screen (a rounded rect:
                // the GPU clips it for nothing)
                shape = androidx.compose.foundation.shape.RoundedCornerShape(((1f - v) * 44f).dp)
                clip = v < 1f
            },
    ) { content(shown) }
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

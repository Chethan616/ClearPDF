package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.ui.components.DecryptingAnimation
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.ViewerChromeGlass
import com.chethan616.clearpdf.ui.components.LiquidSaveSheet
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.viewerChromeGlass
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit,
    // True when the caller (recents / external open / a tool's output) already handed us a document
    // to load. In that case the viewer must NOT flash its "Open a PDF" picker while the pages render —
    // it shows a loading curtain that the real document fades in behind. See [ViewerLoadingCurtain].
    pendingLoad: Boolean = false
) {
    val state         by viewModel.uiState.collectAsState()
    val isDarkMode     = LocalIsDarkMode.current
    val isLight        = !isDarkMode
    val text           = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub            = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent         = Color(0xFF1976D2)
    val uiSensor       = rememberUISensor()
    val context        = LocalContext.current
    val clipboard      = LocalClipboardManager.current
    val focusManager   = LocalFocusManager.current
    val activity       = context as? Activity
    val view           = LocalView.current
    val scope          = rememberCoroutineScope()

    // ── Local UI state ─────────────────────────────────────────────────────
    var controlsVisible     by rememberSaveable { mutableStateOf(true) }
    var controlsPinned      by rememberSaveable { mutableStateOf(false) }
    // True while the bottom "Editor Tools" panel is expanded — keeps the chrome from auto-hiding
    // so the user can browse tools without it disappearing.
    var editorToolsOpen     by rememberSaveable { mutableStateOf(false) }
    // True for as long as the share capsule is held morphed open. Not saveable on purpose: a
    // configuration change cancels the pointer gesture, so a persisted `true` would pin the chrome
    // open forever with no finger left to release it.
    var shareHolding        by remember { mutableStateOf(false) }
    var lastInteractionAtMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    // Adobe-style single-axis reading: the viewer is vertical-only. Horizontal
    // panning is still allowed inside a zoomed page (handled in PdfContinuousPage).
    val scrollOrientation = ScrollOrientation.Vertical
    var showPageJumpDialog  by rememberSaveable { mutableStateOf(false) }
    var activeTool          by rememberSaveable { mutableStateOf(PdfEditTool.None) }
    var currentColorLong    by rememberSaveable { mutableLongStateOf(0xFF00BCD4L) }
    val currentColor         = Color(currentColorLong)
    var currentStrokeWidth  by rememberSaveable { mutableFloatStateOf(6f) }
    var activeImageId       by remember { mutableStateOf<Long?>(null) }
    var showSaveDialog      by rememberSaveable { mutableStateOf(false) }
    var showShareDialog     by remember { mutableStateOf(false) }
    var showFindBar         by rememberSaveable { mutableStateOf(false) }
    var findQuery           by rememberSaveable { mutableStateOf("") }
    val findFocusRequester   = remember { FocusRequester() }
    var passwordText        by rememberSaveable { mutableStateOf("") }
    val passwordFocusRequester = remember { FocusRequester() }
    var showSignaturePad    by remember { mutableStateOf(false) }
    var showZoomHud         by remember { mutableStateOf(false) }
    // Annotation (text box / sticky note) editing
    var editingAnnoId       by remember { mutableStateOf<Long?>(null) }
    var editingAnnoPage     by remember { mutableStateOf(0) }
    var editingAnnoIsNote   by remember { mutableStateOf(false) }
    var annotationDraft     by remember { mutableStateOf("") }
    var editingAnnoColor    by remember { mutableStateOf(Color(0xFF1976D2)) }
    // Shape (rect / oval / line / arrow / stroke) editing — identified by page + list index.
    var editingShapePage    by remember { mutableStateOf<Int?>(null) }
    var editingShapeIndex   by remember { mutableStateOf(-1) }
    // Generic markup selection (move + resize of shapes / text / notes).
    var selectedAnnoPage    by remember { mutableStateOf<Int?>(null) }
    var selectedAnnoIndex   by remember { mutableStateOf(-1) }
    val density              = LocalDensity.current

    // ── Zoom / pan state ───────────────────────────────────────────────────
    // Ported from Pdf_Tools (Karna14314): document-level zoom/pan is plain float
    // state updated *synchronously* inside the gesture loop. The earlier
    // Animatable approach launched a coroutine (snapTo) per pointer event, which
    // raced on single-page docs and made pinch/pan stutter — see settings credit.
    var scale   by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(scale) {
        if (scale > 1.05f) { showZoomHud = true; delay(900); showZoomHud = false }
        else showZoomHud = false
    }

    // ── Page annotation state ──────────────────────────────────────────────
    val annotationsByPage = remember { mutableStateMapOf<Int, MutableList<PdfMarkup>>() }
    val pageCanvasSizes   = remember { mutableStateMapOf<Int, Size>() }
    val pageBitmapSizes   = remember { mutableStateMapOf<Int, Size>() }

    fun getPageMarks(page: Int): MutableList<PdfMarkup> =
        annotationsByPage.getOrPut(page) { mutableStateListOf() }

    fun selectedTextRanges(page: Int): List<OcrTextRange> =
        state.selectedOcrRangesByPage[page].orEmpty()

    // Undo history: the page each added markup landed on, newest last. Undo pops the most recent
    // entry and removes THAT page's last mark, so "undo" means the last thing the user actually
    // did. The old behaviour removed the last mark of `listState.firstVisibleItemIndex`, which is
    // the first *partially* visible page — usually a sliver of the previous page while you draw on
    // the one filling the screen, so undo silently no-op'd on an empty list. The same trap is
    // already documented for image placement at `activeImageLoc`.
    val undoStack = remember { mutableStateListOf<Int>() }

    // Declared here (rather than lower) so the image/signature launchers below can place
    // annotations onto whichever page is under the viewport centre.
    val listState = rememberLazyListState()

    // iOS-style momentum for the continuous page scroll: a lower-friction exponential decay
    // glides longer and settles smoothly (vs the stiffer platform spline), so even 2–3 page
    // docs feel continuous instead of stopping abruptly. Only the fling curve changes — the
    // scroll mechanics and the default stretch overscroll (rubber-band at the edges) are intact.
    val iosPageDecay = remember { androidx.compose.animation.core.exponentialDecay<Float>(frictionMultiplier = 0.55f) }
    val pageFling = remember(iosPageDecay) {
        object : androidx.compose.foundation.gestures.FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                if (kotlin.math.abs(initialVelocity) <= 1f) return initialVelocity
                var lastValue = 0f
                var velocityLeft = initialVelocity
                androidx.compose.animation.core.AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                    .animateDecay(iosPageDecay) {
                        val delta = value - lastValue
                        val consumed = scrollBy(delta)
                        lastValue = value
                        velocityLeft = this.velocity
                        // Hit an edge / content end → stop so overscroll can take over.
                        if (kotlin.math.abs(delta - consumed) > 0.5f) cancelAnimation()
                    }
                return velocityLeft
            }
        }
    }

    // The page + on-screen position where a newly placed image/signature should land: the
    // page occupying the vertical centre of the viewport, at the point the user is looking
    // at (so a sign never silently lands off-screen on page 1). Returns a null offset when
    // the page isn't measured yet → callers fall back to that page's frame centre.
    fun viewportPlacementTarget(): Pair<Int, Offset?> {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return state.currentPage to null
        val centerY = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val item = visible.firstOrNull { centerY >= it.offset && centerY < it.offset + it.size }
            ?: visible.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centerY) }!!
        val page = item.index
        val cs = pageCanvasSizes[page] ?: return page to null
        if (cs.width < 50f || cs.height < 50f) return page to null
        val topPadPx = with(density) { 6.dp.toPx() }
        val localY = (centerY - item.offset - topPadPx).coerceIn(0f, cs.height)
        val bs = pageBitmapSizes[page] ?: cs
        val frame = fitBitmapRect(cs, bs.width, bs.height)
        return page to Offset(frame.center.x, localY)
    }

    /** Record that [page] just gained a markup, so undo can find it again. */
    fun recordEdit(page: Int) { undoStack.add(page) }

    /**
     * Remove the most recently added markup, wherever it lives. Entries can go stale — the eraser
     * and the shape editor delete marks without touching the stack — so pop past any page that has
     * since been emptied. If the history is exhausted (or was never populated) fall back to the page
     * under the viewport centre, which is the page the user is looking at.
     */
    fun undoLastEdit() {
        while (undoStack.isNotEmpty()) {
            val page = undoStack.removeAt(undoStack.lastIndex)
            val marks = getPageMarks(page)
            if (marks.isNotEmpty()) { marks.removeAt(marks.lastIndex); return }
        }
        val marks = getPageMarks(viewportPlacementTarget().first)
        if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex)
    }

    /** Clear every markup on the page under the viewport centre — the one the user can see. */
    fun clearVisiblePage() {
        val page = viewportPlacementTarget().first
        getPageMarks(page).clear()
        undoStack.removeAll { it == page }
    }

    // Add a new image/signature centred at the viewport-target on the correct page, sized
    // to the page and clamped fully on-page. Shared by the image picker and signature pad.
    fun placeImageMarkup(bmp: Bitmap, isSignature: Boolean) {
        val (page, target) = viewportPlacementTarget()
        val marks = getPageMarks(page)
        val rawCs = pageCanvasSizes[page]
        val cs = if (rawCs != null && rawCs.width > 50f && rawCs.height > 50f) rawCs else Size(1000f, 1400f)
        val bs = pageBitmapSizes[page] ?: cs
        val frame = fitBitmapRect(cs, bs.width, bs.height)
        val maxW = (if (frame.width > 50f) frame.width else cs.width) * (if (isSignature) 0.45f else 0.5f)
        val ratio = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
        val w = maxW.coerceAtLeast(100f)
        val h = w * ratio
        val fallbackCenter = if (frame.width > 50f) frame.center else Offset(cs.width / 2f, cs.height / 2f)
        val center = target ?: fallbackCenter
        val cx = center.x.coerceIn(w / 2f, (cs.width - w / 2f).coerceAtLeast(w / 2f))
        val cy = center.y.coerceIn(h / 2f, (cs.height - h / 2f).coerceAtLeast(h / 2f))
        val id = System.nanoTime()
        marks.add(PdfMarkup.ImageMarkup(id, bmp, Offset(cx - w / 2f, cy - h / 2f), Offset(cx + w / 2f, cy + h / 2f), isSignature))
        recordEdit(page)
        activeImageId = id; activeTool = PdfEditTool.Image; controlsVisible = true
    }

    // Locate the currently selected image across ALL pages (it may live on a page other
    // than firstVisibleItemIndex, since placement targets the viewport-centre page).
    fun activeImageLoc(): Triple<Int, Int, PdfMarkup.ImageMarkup>? {
        val id = activeImageId ?: return null
        annotationsByPage.forEach { (pg, m) ->
            val i = m.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == id }
            if (i >= 0) return Triple(pg, i, m[i] as PdfMarkup.ImageMarkup)
        }
        return null
    }

    // ── Launchers ─────────────────────────────────────────────────────────
    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openPdf(context, it) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        // If an image is currently selected, replace its bitmap in place; otherwise place a
        // new one on the page under the viewport (never silently on page 1).
        val existingPage = annotationsByPage.entries.firstOrNull { (_, m) -> m.any { it is PdfMarkup.ImageMarkup && it.id == activeImageId } }?.key
        val existingIdx = existingPage?.let { getPageMarks(it).indexOfLast { m -> m is PdfMarkup.ImageMarkup && m.id == activeImageId } } ?: -1
        if (existingPage != null && existingIdx >= 0) {
            val marks = getPageMarks(existingPage)
            val existing = marks[existingIdx] as PdfMarkup.ImageMarkup
            val ratio = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
            val currentW = kotlin.math.abs(existing.end.x - existing.start.x).coerceAtLeast(20f)
            marks[existingIdx] = existing.copy(bitmap = bmp, end = Offset(existing.end.x, existing.start.y + currentW * ratio), isSignature = false)
        } else {
            placeImageMarkup(bmp, isSignature = false)
        }
    }

    // ── Immersive mode ────────────────────────────────────────────────────
    val immersiveActive = state.document != null && !controlsVisible
    DisposableEffect(activity, view, immersiveActive) {
        val ctrl = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersiveActive) ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        else ctrl?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // ── Document lifecycle effects ─────────────────────────────────────────
    LaunchedEffect(state.document?.uri) {
        controlsVisible = true; controlsPinned = false
        lastInteractionAtMs = System.currentTimeMillis()
        scale = 1f; offsetX = 0f
        activeTool = PdfEditTool.None
        selectedAnnoPage = null; selectedAnnoIndex = -1
        annotationsByPage.clear(); undoStack.clear(); pageCanvasSizes.clear(); pageBitmapSizes.clear()
        viewModel.clearOcrSelection(state.currentPage); viewModel.clearExportFeedback()
        showFindBar = false; findQuery = ""; viewModel.clearSearch()
    }

    LaunchedEffect(state.document, controlsVisible, controlsPinned, activeTool, editorToolsOpen, shareHolding, lastInteractionAtMs) {
        if (state.document != null && controlsVisible && !controlsPinned && scale <= 1.01f && activeTool == PdfEditTool.None && !editorToolsOpen && !shareHolding) {
            val snap = lastInteractionAtMs
            // Comfortable auto-hide window; any interaction bumps lastInteractionAtMs and
            // restarts this. It never fires while a tool is active (activeTool != None), while
            // the Editor Tools panel is open, or while the share capsule is held — a long-press
            // produces no pointer events for the viewer to see, so without that last guard the
            // chrome hid itself out from under the finger mid-gesture.
            delay(5000)
            if (controlsVisible && !controlsPinned && scale <= 1.01f && activeTool == PdfEditTool.None && !editorToolsOpen && !shareHolding && snap == lastInteractionAtMs)
                controlsVisible = false
        }
    }

    LaunchedEffect(state.passwordUri) {
        if (state.passwordUri != null) { passwordText = ""; delay(120); passwordFocusRequester.requestFocus() }
    }

    BackHandler(enabled = state.document != null) {
        if (!controlsVisible) controlsVisible = true else onBack()
    }

    // ── No-document state ─────────────────────────────────────────────────
    if (state.document == null) {
        val askingPassword = state.passwordRequired && state.passwordUri != null
        // A document is on its way in (recents / external / tool output) and nothing has gone wrong
        // yet: show the loading curtain instead of the picker so the user never sees the "Open a PDF"
        // card flash before their file appears. The identical curtain is held over the loaded viewer
        // below until the first page is rendered, so the swap between these two branches is seamless.
        val openingHandedDoc = !askingPassword && state.errorMessage == null && (state.isLoading || pendingLoad)
        if (openingHandedDoc) {
            Box(Modifier.fillMaxSize()) {
                ViewerLoadingCurtain(isLight = isLight)
                // While a password PDF is actually being unlocked, play the padlock "decrypting"
                // animation over the fill (styled after the onboarding page-5 demos). Plain opening
                // fills (a normal load) show nothing extra — a lock would be misleading there.
                if (state.decrypting) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DecryptingAnimation(backdrop = backdrop)
                    }
                }
            }
            return
        }
        // The password prompt is an OVERLAY on this Box, not a third row inside the Column below.
        //
        // It used to be a sibling of the "Open a PDF" card in a `spacedBy(16.dp)` Column whose middle
        // section carried `weight(1f)`. So the moment the prompt and the keyboard were both up, the
        // weighted section was squeezed and the card was chopped mid-sentence — the whole point of a
        // password dialog is that the thing behind it stays intact.
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Same trio as the loaded viewer's chrome: back circle · centered title pill · balancer.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // No `surfaceColor`, like Home's — this circle used to paint a white 8% wash that
                    // nothing else in the app does.
                    LiquidIconButton(onClick = onBack, backdrop = backdrop) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(stringResource(R.string.viewer_title), backdrop)
                    }
                    Spacer(Modifier.size(40.dp))
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Column(
                        Modifier.fillMaxWidth().viewerGlass(backdrop, viewerChromeGlass(isDarkMode)).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(56.dp), accent)
                        BasicText(stringResource(R.string.viewer_open_pdf), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                        BasicText(stringResource(R.string.viewer_select_file), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
                        LiquidButton(onClick = { pdfPickerLauncher.launch(arrayOf("*/*")) }, backdrop = backdrop, tint = accent) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                                BasicText(stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                    state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFD32F2F), 14.sp)) }
                }
            }

            // ── Password prompt (in-window, so the glass samples the real backdrop) ──
            // `askingPassword` is computed at the top of this branch.

            // Dimming scrim. Deliberately NOT tap-to-dismiss, unlike LiquidPageJumpPopup: there is
            // nothing behind this to go back to — dismissing would leave a locked document and an
            // empty screen. Back already exits the viewer.
            AnimatedVisibility(
                visible = askingPassword,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
            }

            AnimatedVisibility(
                visible = askingPassword,
                // LiquidPageJumpPopup's entrance, verbatim, so the app has one dialog motion.
                enter = fadeIn(tween(220)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.Center).imePadding()
            ) {
                Column(
                    Modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.86f)
                        // The heavy stack, not `viewerGlass`: this is a dialog over a scrim and needs
                        // an edge of its own. No `containerColorOverride` — there is no page to sample
                        // in this branch, so the theme tint is the right one.
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BasicText(stringResource(R.string.pdf_password_title), style = TextStyle(text, 17.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        if (state.passwordAttemptFailed) stringResource(R.string.pdf_password_wrong) else stringResource(R.string.pdf_password_subtitle),
                        style = TextStyle(sub, 12.sp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = passwordText,
                            onValueChange = { passwordText = it },
                            singleLine = true,
                            textStyle = TextStyle(text, 15.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { state.passwordUri?.let { viewModel.openPdf(context, it, passwordText) } }),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f))
                                .padding(horizontal = 12.dp, vertical = 11.dp).focusRequester(passwordFocusRequester),
                            decorationBox = { inner ->
                                if (passwordText.isEmpty()) BasicText(stringResource(R.string.pdf_password_hint), style = TextStyle(sub, 14.sp))
                                inner()
                            }
                        )
                        LiquidButton(onClick = { state.passwordUri?.let { viewModel.openPdf(context, it, passwordText) } }, backdrop = backdrop, tint = accent) {
                            BasicText(stringResource(R.string.pdf_password_unlock), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        return
    }

    // ── PDF viewer (continuous vertical scroll, Adobe-style) ─────────────────
    val safePageCount = state.pageCount.coerceAtLeast(1)
    val viewerScope   = rememberCoroutineScope()
    val currentPageIndex = listState.firstVisibleItemIndex.coerceIn(0, safePageCount - 1)

    // Live backdrop that captures the ACTUAL rendered page column (dark bg + PDF
    // pages), so the glass chrome reflects real content instead of the static
    // wallpaper PNG. The chrome is drawn as siblings on top of the captured Box, so
    // there is no feedback loop.
    val contentBackdrop = rememberLayerBackdrop()

    // ── Adaptive chrome contrast ────────────────────────────────────────────
    // The chrome glass floats over the page, which may be a bright white note or a
    // dark scan. Sample the current page's average luminance and flip the whole
    // chrome palette (glass surface + foreground) so text/icons are always legible:
    // dark ink on a light page, white ink on a dark page.
    //
    // Single-page documents used to be pinned to the dark branch. That was over-broad and is the
    // reason the chrome looked stuck in dark mode: a converted .docx is usually one page, so *every*
    // Word document and every one-page PDF got dark glass no matter the page or the theme.
    //
    // The reason for the pin was real, though. A lone page is centred (`Arrangement.Center` on the
    // LazyColumn below), so the chrome can float over the near-black canvas rather than over the
    // page — and at 66% the light surface set little value of its own, leaving buttons refracting
    // black while their icons took the *dark* foreground chosen from the white page. The fix is to
    // make the light surface actually opaque enough to stand on its own (78%, below) instead of
    // throwing away a correct luminance reading. Dark ink on that holds up over the black band and
    // over the page alike.
    val currentPageBitmap = state.pageBitmaps.getOrNull(currentPageIndex)
    // Whole-page luminance still drives the DIALOG panels (chromePanel / panelFg): those sit over a
    // scrim on an opaque surface, not over the live document, so a single global reading is right.
    // The floating BARS no longer use this — they sample the content directly behind each bar
    // (topFg / bottomFg, computed once containerHeightPx is known, further down). That is what makes
    // the icons dynamic per-region, kills the "doesn't flip until you scroll deep into page 3" lag,
    // and tracks the zoomed region on a single page. The old single-page white-pin is gone with it —
    // band sampling reads the dark letterbox as dark (→ white ink) on its own.
    val isLightChrome = remember(currentPageBitmap) {
        currentPageBitmap?.let { averageLuminance(it) > 0.60f } ?: false
    }
    // Home's tint, verbatim — the same expression GlassTitlePill and GlassSearchPill resolve — but
    // picked off the *page's* luminance instead of the theme, so a white scan in dark mode still gets
    // the light surface.
    //
    // This used to be 0.78/0.62, roughly twice Home's weight, and it was painted onto controls Home
    // leaves entirely clear: `LiquidIconButton` with no `surfaceColor` draws nothing at all in
    // `onDrawSurface`, so Home's circles are pure refraction. Over a white page a 62% #12151C slab
    // composites to about #6C6C6C, which is why the chrome read as grey blobs sitting *on* the
    // document rather than as glass floating over it. The effect stack was never the difference —
    // `LiquidIconButton` and `viewerGlass` both run vibrancy + blur 2 + lens 12x24 — only the tint was.
    val chromeGlass  = if (isLightChrome) Color(0xFFFAFAFA).copy(0.35f) else Color(0xFF1E1E1E).copy(0.35f)
    // Dialogs keep the old, heavier tint. They sit over a 45% black scrim and carry dense content, and
    // the app's own rule is that the heavy stack stays reserved for them (see ViewerGlass's KDoc).
    val chromePanel  = if (isLightChrome) Color(0xFFEFF1F4).copy(0.78f) else ViewerChromeGlass
    // The dialogs' ink deliberately does *not* take the single-page pin above. Their surface is
    // `chromePanel`, which is opaque enough to be its own background, so it is the panel the text has
    // to contrast with — not the canvas. Pinning these to white alongside the floating chrome would
    // put white text on a 78% white panel every time a one-page document happened to be light.
    val panelFg      = if (isLightChrome) Color(0xFF15171C) else Color.White
    val panelFgSoft  = if (isLightChrome) Color(0xFF15171C).copy(0.62f) else Color.White.copy(0.62f)
    val chromeField  = if (isLightChrome) Color.Black.copy(0.10f) else Color.White.copy(0.10f)

    // The top visible page drives text extraction + bitmap caching.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> viewModel.onPageChanged(idx.coerceIn(0, safePageCount - 1)) }
    }

    // Scroll to the page holding the active find match.
    LaunchedEffect(state.currentMatchIndex, state.findMatches) {
        val idx = state.currentMatchIndex
        val matches = state.findMatches
        if (idx >= 0 && matches.isNotEmpty()) {
            viewerScope.launch { listState.animateScrollToItem(matches[idx].pageIndex) }
        }
    }

    val drawingToolActive = activeTool in setOf(
        PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect,
        PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow
    )
    val zoomHudText = "${(scale * 100 + 0.5f).toInt()}%"

    fun scrollToPage(target: Int) {
        viewerScope.launch { listState.animateScrollToItem(target.coerceIn(0, safePageCount - 1)) }
        lastInteractionAtMs = System.currentTimeMillis()
    }

    // Smooth, critically-damped zoom — NO overshoot/bounce (premium, Apple-style ease).
    val animateZoomPan: suspend (Float, Offset) -> Unit = { targetZoom, targetPan ->
        val spec = androidx.compose.animation.core.spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow
        )
        coroutineScope {
            launch { androidx.compose.animation.core.animate(scale, targetZoom, animationSpec = spec) { v, _ -> scale = v } }
            launch { androidx.compose.animation.core.animate(offsetX, targetPan.x, animationSpec = spec) { v, _ -> offsetX = v } }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLight) Color(0xFF0A0E14).copy(0.85f) else Color(0xFF020305).copy(0.88f))
    ) {
        val renderWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(720)

        var containerHeightPx by remember { mutableStateOf(0) }

        // ── Adaptive control ink, sampled per bar (Apple-style) ──────────────────────────────
        // Each bar reads the luminance of the content *directly behind it*, so on a page that is a
        // dark image up top and a white canvas below, the top bar goes white while the bottom toolbar
        // goes dark — and both update the instant that content scrolls or zooms under them, not when
        // the page becomes the first fully-visible item. Bands are in screen px; the content Box is
        // scaled about a top origin with translationY = 0, so a screen Y maps to the LazyColumn's own
        // coordinate as screenY / scale (see [bandLuminance]).
        val topBandBottomPx = with(density) { 96.dp.toPx() }
        val bottomBandDepthPx = with(density) { 140.dp.toPx() }
        var topBarLight by remember { mutableStateOf(false) }
        var bottomBarLight by remember { mutableStateOf(false) }
        // Hysteresis: flip to light at 0.62, back to dark at 0.58, so a page hovering near the
        // midpoint doesn't strobe while scrolling. The colour crossfade below smooths the flip
        // itself. The sampling runs in a coroutine off `snapshotFlow`, so scrolling recomputes the
        // luminance every frame but only an actual light/dark flip ever recomposes the chrome.
        LaunchedEffect(containerHeightPx) {
            snapshotFlow {
                bandLuminance(listState.layoutInfo, state.pageBitmaps, scale, 0f, topBandBottomPx)
            }.collect { lum ->
                if (lum > 0.62f) topBarLight = true else if (lum < 0.58f) topBarLight = false
            }
        }
        LaunchedEffect(containerHeightPx) {
            snapshotFlow {
                val h = containerHeightPx.toFloat()
                if (h <= 0f) 0f
                else bandLuminance(listState.layoutInfo, state.pageBitmaps, scale, h - bottomBandDepthPx, h)
            }.collect { lum ->
                if (lum > 0.62f) bottomBarLight = true else if (lum < 0.58f) bottomBarLight = false
            }
        }
        val topFg by animateColorAsState(if (topBarLight) Color(0xFF15171C) else Color.White, tween(200), label = "topBarInk")
        val bottomFg by animateColorAsState(if (bottomBarLight) Color(0xFF15171C) else Color.White, tween(200), label = "bottomBarInk")
        val topFgSoft = topFg.copy(alpha = 0.62f)
        val bottomFgSoft = bottomFg.copy(alpha = 0.62f)

        // The find bar is the one control that does NOT sit at the screen edge: with the keyboard up
        // it floats at the top of the IME, so the bottom-of-screen band (behind the keyboard) is the
        // wrong reading for it. Sample the band directly above the IME instead, so its text and its
        // prev/next/close icons adapt to the page content actually behind the search bar. When the
        // keyboard is down the IME inset is 0 and this collapses to the same bottom band.
        val imeInset = WindowInsets.ime
        var findBarLight by remember { mutableStateOf(false) }
        LaunchedEffect(containerHeightPx) {
            snapshotFlow {
                val h = containerHeightPx.toFloat()
                if (h <= 0f) 0f
                else {
                    val bottom = (h - imeInset.getBottom(density)).coerceAtLeast(bottomBandDepthPx)
                    bandLuminance(listState.layoutInfo, state.pageBitmaps, scale, bottom - bottomBandDepthPx, bottom)
                }
            }.collect { lum ->
                if (lum > 0.62f) findBarLight = true else if (lum < 0.58f) findBarLight = false
            }
        }
        val findFg by animateColorAsState(if (findBarLight) Color(0xFF15171C) else Color.White, tween(200), label = "findBarInk")
        val findFgSoft = findFg.copy(alpha = 0.62f)

        // ── Continuous vertical page column with document-level zoom/pan ─────
        // layerBackdrop + background live INSIDE this Box so the captured layer holds
        // the dark base + PDF pages; the glass chrome samples it (real reflections).
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
                .background(if (isLight) Color(0xFF0A0E14).copy(0.85f) else Color(0xFF020305).copy(0.88f))
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerHeightPx = it.height }
                    .then(
                        // Keep pinch zoom available in Select Text mode. Other editing tools own
                        // the page gesture surface because their strokes/shapes need the drag.
                        if (activeTool != PdfEditTool.None && activeTool != PdfEditTool.SelectText) Modifier
                        else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange  = event.calculatePan()
                                    val zoomed = scale > 1.001f
                                    if (zoomChange != 1f || zoomed) {
                                        val cw = size.width.toFloat()
                                        val oldScale = scale
                                        val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                                        // Real zoom ratio AFTER clamping — drives the focal correction.
                                        val effZoom = if (oldScale != 0f) newScale / oldScale else 1f
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val center = cw / 2f
                                        val maxOffsetX = ((cw * newScale - cw) / 2f).coerceAtLeast(0f)
                                        // Synchronous state writes — no per-event coroutine (Pdf_Tools parity).
                                        scale = newScale
                                        if (newScale > 1f) {
                                            // FOCAL zoom: shift so the pinch centroid stays under the fingers
                                            // (layer origin is top-CENTER on X), THEN apply the finger pan.
                                            // Without this the page scaled around the centre, so zoom landed
                                            // "beside" where you pinched.
                                            val focalDx = if (centroid.isSpecified) (1f - effZoom) * (centroid.x - center - offsetX) else 0f
                                            offsetX = (offsetX + focalDx + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            // Vertical is LazyColumn scroll in UNSCALED px (layer origin is TOP
                                            // on Y): the focal term keeps the centroid's row put, the pan term
                                            // tracks the finger (divided by scale for 1:1 at higher zoom).
                                            val focalDy = if (centroid.isSpecified) centroid.y * (1f / oldScale - 1f / newScale) else 0f
                                            val totalDy = focalDy + (-panChange.y / newScale)
                                            if (totalDy != 0f) listState.dispatchRawDelta(totalDy)
                                        } else {
                                            offsetX = 0f
                                        }
                                        lastInteractionAtMs = System.currentTimeMillis()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                    )
                    .pointerInput(activeTool) {
                        detectTapGestures(
                            onTap = { if (activeTool == PdfEditTool.None) controlsVisible = !controlsVisible; lastInteractionAtMs = System.currentTimeMillis() },
                            onDoubleTap = { tap ->
                                if (activeTool != PdfEditTool.None) return@detectTapGestures
                                val cw = size.width.toFloat()
                                val s0 = scale
                                val target = if (s0 > 1.5f) 1f else 2.5f
                                val maxOffsetX = ((cw * target - cw) / 2f).coerceAtLeast(0f)
                                // Horizontal focal: keep the tapped X under the finger
                                // (graphicsLayer origin is top-CENTER, so translationX compensates).
                                val focalX = if (target > 1f) ((cw / 2f - tap.x) * (target - 1f)).coerceIn(-maxOffsetX, maxOffsetX) else 0f
                                // Vertical focal: the layer is TOP-anchored, so keep the tapped Y
                                // under the finger by scrolling the list Δ = tap.y*(1/s0 - 1/target).
                                val scrollDelta = tap.y * (1f / s0 - 1f / target)
                                val focalSpec = androidx.compose.animation.core.spring<Float>(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness    = Spring.StiffnessMediumLow
                                )
                                viewerScope.launch { animateZoomPan(target, Offset(focalX, 0f)) }
                                viewerScope.launch { listState.animateScrollBy(scrollDelta, focalSpec) }
                                lastInteractionAtMs = System.currentTimeMillis()
                            }
                        )
                    }
            ) {
                val extraBottomPadding = if (scale > 1f && containerHeightPx > 0)
                    with(LocalDensity.current) { (containerHeightPx * ((scale - 1f) / scale)).toDp() } else 0.dp

                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offsetX; translationY = 0f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        flingBehavior = pageFling,
                        userScrollEnabled = activeTool == PdfEditTool.None && scale <= 1.01f,
                        modifier = Modifier.fillMaxSize(),
                        // Center the block when it's shorter than the viewport (e.g. a
                        // single page) so it sits neatly centered instead of pinned to
                        // the top with a dark blank half below. No effect once content
                        // overflows (multi-page / zoomed), where it scrolls normally.
                        verticalArrangement = Arrangement.Center,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 4.dp + extraBottomPadding)
                    ) {
                        items(count = safePageCount, key = { it }, contentType = { "pdfPage" }) { page ->
                            // Re-request whenever this page has no bitmap (covers cache eviction
                            // while the item stays composed in the lazy list).
                            val needsRender = state.pageBitmaps.getOrNull(page) == null
                            LaunchedEffect(page, renderWidthPx, needsRender) {
                                if (needsRender) viewModel.renderPage(context, page, renderWidthPx)
                            }
                            PdfContinuousPage(
                                page               = page,
                                bitmap             = state.pageBitmaps.getOrNull(page),
                                marks              = getPageMarks(page),
                                ocrBlocks          = state.ocrBlocksByPage[page].orEmpty(),
                                selectedOcrIds     = state.selectedOcrBlockIdsByPage[page].orEmpty(),
                                selectedOcrRanges  = state.selectedOcrRangesByPage[page].orEmpty(),
                                findMatches        = state.findMatches,
                                currentMatchIndex  = state.currentMatchIndex,
                                showFindBar        = showFindBar,
                                activeTool         = activeTool,
                                currentColor       = currentColor,
                                currentStrokeWidth = currentStrokeWidth,
                                activeImageId      = activeImageId,
                                pageCanvasSizes    = pageCanvasSizes,
                                pageBitmapSizes    = pageBitmapSizes,
                                onInteraction      = { lastInteractionAtMs = System.currentTimeMillis() },
                                onMarkAdded        = { recordEdit(page) },
                                onToggleControls   = { controlsVisible = !controlsVisible },
                                onShowControls     = { controlsVisible = true },
                                onActiveToolChanged     = { activeTool = it },
                                onActiveImageIdChanged  = { activeImageId = it },
                                onClearOcrSelection     = { viewModel.clearOcrSelection(page) },
                                // Replace (not append): a drag defines the whole selection live, so as
                                // the finger shrinks the range the deselected words must drop out too.
                                onSelectOcrRange        = { viewModel.selectOcrRanges(page, it, append = false) },
                                onPlaceText             = { pt ->
                                    val id = System.nanoTime()
                                    getPageMarks(page).add(PdfMarkup.TextBoxMarkup(id, pt, "", currentColor, 40f))
                                    recordEdit(page)
                                    editingAnnoId = id; editingAnnoPage = page; editingAnnoIsNote = false; annotationDraft = ""
                                    activeTool = PdfEditTool.None; controlsVisible = true
                                },
                                onPlaceNote             = { pt ->
                                    val id = System.nanoTime()
                                    getPageMarks(page).add(PdfMarkup.NoteMarkup(id, pt, "", Color(0xFFFFC107)))
                                    recordEdit(page)
                                    editingAnnoId = id; editingAnnoPage = page; editingAnnoIsNote = true; annotationDraft = ""
                                    activeTool = PdfEditTool.None; controlsVisible = true
                                },
                                onEditAnnotation        = { id ->
                                    val m = getPageMarks(page).firstOrNull {
                                        (it is PdfMarkup.TextBoxMarkup && it.id == id) || (it is PdfMarkup.NoteMarkup && it.id == id)
                                    }
                                    editingAnnoId = id; editingAnnoPage = page
                                    editingAnnoIsNote = m is PdfMarkup.NoteMarkup
                                    annotationDraft = when (m) {
                                        is PdfMarkup.TextBoxMarkup -> m.text
                                        is PdfMarkup.NoteMarkup    -> m.text
                                        else -> ""
                                    }
                                    editingAnnoColor = when (m) {
                                        is PdfMarkup.TextBoxMarkup -> m.color
                                        is PdfMarkup.NoteMarkup    -> m.color
                                        else -> Color(0xFF1976D2)
                                    }
                                    controlsVisible = true
                                },
                                onEditShape             = { idx ->
                                    editingShapePage = page; editingShapeIndex = idx; controlsVisible = true
                                },
                                selectedMarkupIndex     = if (page == selectedAnnoPage) selectedAnnoIndex else -1,
                                onSelectMarkup          = { idx ->
                                    if (idx < 0) { selectedAnnoPage = null; selectedAnnoIndex = -1 }
                                    else { selectedAnnoPage = page; selectedAnnoIndex = idx }
                                },
                                onDeleteMarkup          = { idx ->
                                    val m = getPageMarks(page); if (idx in m.indices) m.removeAt(idx)
                                    selectedAnnoPage = null; selectedAnnoIndex = -1
                                },
                                onCopySelection = {
                                    viewModel.getSelectedOcrText(page).takeIf { it.isNotBlank() }
                                        ?.let { clipboard.setText(AnnotatedString(it)) }
                                    lastInteractionAtMs = System.currentTimeMillis()
                                },
                                onHighlightSelection = {
                                    val m = getPageMarks(page)
                                    selectedTextRanges(page).forEach { range ->
                                        // Recolor semantics: drop any highlight already covering this exact
                                        // range so a re-tap replaces the colour instead of stacking layers.
                                        m.removeAll { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == range.blockId && it.start == range.start && it.end == range.end }
                                        m.add(PdfMarkup.TextBlockHighlightMarkup(range.blockId, Color(currentColorLong), 0.38f, range.start, range.end))
                                    }
                                    // Keep the selection live so the pill immediately offers Recolor / Delete —
                                    // this is the fix for "after highlighting there's no delete option".
                                    lastInteractionAtMs = System.currentTimeMillis()
                                },
                                onSelectAll = {
                                    val ids = state.ocrBlocksByPage[page].orEmpty().map { it.id }.toSet()
                                    if (ids.isNotEmpty()) viewModel.selectOcrBlocks(page, ids, append = false)
                                    lastInteractionAtMs = System.currentTimeMillis()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Page scrubber (doubles as the fading scroll indicator) ─────────
        // Always present on multi-page docs so fast scrubbing is one drag away,
        // independent of the auto-hiding chrome. It brightens the moment the list
        // scrolls/flings and gently fades back to a slim idle state when at rest,
        // so it reads as an iOS-style scroll indicator without a second element.
        // Held while the rail itself is being dragged. Dragging it does not scroll the list (that
        // only happens on release), so without this the idle fade would run mid-drag and take the
        // thumbnail preview down to 40% opacity — which reads as the preview "half disappearing".
        var scrubberDragging by remember { mutableStateOf(false) }
        val scrubberBright = listState.isScrollInProgress || scrubberDragging
        val scrubberAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (scrubberBright) 1f else 0.4f,
            animationSpec = tween(durationMillis = if (scrubberBright) 120 else 600),
            label = "scrubberFade"
        )
        AnimatedVisibility(
            visible  = safePageCount > 1 && scale <= 1.01f,
            enter    = fadeIn(androidx.compose.animation.core.spring(stiffness = Spring.StiffnessMedium)) + scaleIn(initialScale = 0.92f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit     = fadeOut(androidx.compose.animation.core.spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = scrubberAlpha }.navigationBarsPadding().statusBarsPadding().padding(end = 8.dp)
        ) {
            PageScrubber(
                currentPage      = currentPageIndex,
                pageCount        = safePageCount,
                pageBitmaps      = state.pageBitmaps,
                backdrop         = contentBackdrop,
                uiSensor         = uiSensor,
                onPageChange     = { page -> scrollToPage(page) },
                onPageScrubbing  = { page -> viewModel.renderPage(context, page, 400); lastInteractionAtMs = System.currentTimeMillis() },
                onDraggingChange = { scrubberDragging = it },
                isScrolling      = listState.isScrollInProgress
            )
        }

        // ── Top + bottom controls overlay ──────────────────────────────────
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)
        ) {
            // Top bar
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = fadeIn(tween(200)) + slideInVertically { -it / 2 },
                exit     = fadeOut(tween(150)) + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                // Home and Tools' header trio, verbatim: back circle · centred [GlassTitlePill] ·
                // search circle, 10 dp apart. The pill is the same widget carrying "ClearPDF" on
                // Home rather than a look-alike `LiquidButton`, so the two screens can't drift; only
                // the palette differs, because the viewer picks its chrome from the *page's*
                // luminance instead of the theme.
                //
                // Page navigation lives in the right-edge scrubber + scroll, so no oversized
                // prev/next controls cover the document.
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    // No `surfaceColor`, exactly as Home calls it: the circle paints nothing of its own
                    // and is pure refraction. Only the icon's colour adapts to the page.
                    LiquidIconButton(onClick = onBack, backdrop = contentBackdrop) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), topFg)
                    }
                    // A weighted Box rather than two weighted spacers: the back circle and the
                    // search circle are the same 40 dp, so this centres the pill on the row exactly
                    // the way Home's header does, and a long "Page 100 / 1000" grows symmetrically.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(
                            text = stringResource(R.string.viewer_page_of, currentPageIndex + 1, safePageCount),
                            backdrop = contentBackdrop,
                            onClick = { showPageJumpDialog = true },
                            // Transparent, not omitted: omitting falls back to the theme's 0.35 tint,
                            // and this pill sits between two circles that paint nothing at all, so any
                            // tint at all makes it read as a slab bolted between two pieces of glass.
                            // `drawRect(Color.Transparent)` is the same no-op `LiquidIconButton`
                            // performs when it is given no `surfaceColor`.
                            surfaceColor = Color.Transparent,
                            contentColor = topFg,
                            // The enclosing AnimatedVisibility already slides and fades the whole bar
                            // in every time the chrome returns; the pill's own spring would stack on
                            // top of that and read as a stutter. Same call the find bar makes.
                            animateIn = false
                        )
                    }
                    LiquidIconButton(
                        onClick = {
                            showFindBar = !showFindBar
                            if (showFindBar) viewModel.triggerOcrForAllPages(context)
                            else { focusManager.clearFocus(); viewModel.clearSearch(); findQuery = "" }
                        },
                        backdrop = contentBackdrop
                    ) {
                        Icon(Icons.Rounded.Search, stringResource(R.string.viewer_find), Modifier.size(20.dp), topFg)
                    }
                }
            }

            // Bottom toolbar
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = fadeIn(tween(200)) + slideInVertically { it / 2 },
                exit     = fadeOut(tween(150)) + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val activeItem = activeImageLoc()?.third

                PdfViewerBottomToolbar(
                    activeTool         = activeTool,
                    drawingToolActive  = drawingToolActive,
                    showFindBar        = showFindBar,
                    showSignaturePad   = showSignaturePad,
                    activeImageId      = activeImageId,
                    currentColor       = currentColor,
                    currentColorLong   = currentColorLong,
                    currentStrokeWidth = currentStrokeWidth,
                    zoomScale          = scale,
                    hasEdits           = annotationsByPage.values.any { it.isNotEmpty() },
                    isExporting        = state.isExporting,
                    exportError        = state.exportError,
                    exportMessage      = state.exportMessage,
                    lastExportedUri    = state.lastExportedUri,
                    selectedTextCount  = selectedTextRanges(currentPageIndex).size,
                    currentSelectedIds = state.selectedOcrBlockIdsByPage[currentPageIndex].orEmpty(),
                    activeIsSignature  = activeItem?.isSignature == true,
                    // Undo is history-driven, not page-driven. Two earlier attempts keyed it to a
                    // page index — first `state.currentPage` (async, lagged behind the scroll), then
                    // `listState.firstVisibleItemIndex` (the first *partially* visible page, which
                    // while you draw is usually a sliver of the PREVIOUS page). Both removed from the
                    // wrong, usually empty, list. There is no page index that reliably means "what
                    // the user just did", so the viewer records each addition instead.
                    canUndo            = undoStack.isNotEmpty() || annotationsByPage.any { it.value.isNotEmpty() },
                    onUndo             = { undoLastEdit(); lastInteractionAtMs = System.currentTimeMillis() },
                    onClearPage        = { clearVisiblePage(); lastInteractionAtMs = System.currentTimeMillis() },
                    onSetActiveTool    = { activeTool = it; if (it == PdfEditTool.None) activeImageId = null; selectedAnnoPage = null; selectedAnnoIndex = -1; viewModel.clearOcrSelection(currentPageIndex) },
                    onToggleFindBar    = {
                        showFindBar = !showFindBar
                        if (showFindBar) viewModel.triggerOcrForAllPages(context)
                        else { focusManager.clearFocus(); viewModel.clearSearch(); findQuery = "" }
                    },
                    onShowSignaturePad = { showSignaturePad = true },
                    onPickImage        = { activeImageId = null; imagePickerLauncher.launch("image/*") },
                    onResetZoom        = { scope.launch { animateZoomPan(1f, Offset.Zero) }
                                          lastInteractionAtMs = System.currentTimeMillis() },
                    onShowSaveDialog   = { showSaveDialog = true },
                    onImageDone        = { activeImageId = null; activeTool = PdfEditTool.None },
                    onReplaceImage     = {
                        if (activeItem?.isSignature == true) showSignaturePad = true
                        else imagePickerLauncher.launch("image/*")
                    },
                    onDeleteImage      = {
                        activeImageLoc()?.let { (pg, idx, _) -> getPageMarks(pg).removeAt(idx) }
                        activeImageId = null; activeTool = PdfEditTool.None
                    },
                    onSelectAllText    = {
                        val ids = state.ocrBlocksByPage[currentPageIndex].orEmpty().map { it.id }.toSet()
                        if (ids.isNotEmpty()) viewModel.selectOcrBlocks(currentPageIndex, ids, false)
                    },
                    onCopyText         = {
                        viewModel.getSelectedOcrText(currentPageIndex).takeIf { it.isNotBlank() }
                            ?.let { clipboard.setText(AnnotatedString(it)) }
                    },
                    onHighlightSelected = {
                        val m = getPageMarks(currentPageIndex)
                        selectedTextRanges(currentPageIndex).forEach { range ->
                            if (!m.any { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == range.blockId && it.start == range.start && it.end == range.end })
                                m.add(PdfMarkup.TextBlockHighlightMarkup(range.blockId, Color(currentColorLong), 0.38f, range.start, range.end))
                        }
                    },
                    onUnderlineSelected = {
                        val m = getPageMarks(currentPageIndex)
                        selectedTextRanges(currentPageIndex).forEach { range ->
                            if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == range.blockId && it.start == range.start && it.end == range.end && !it.strikeThrough })
                                m.add(PdfMarkup.TextBlockLineMarkup(range.blockId, Color(currentColorLong), 3f, 1f, false, range.start, range.end))
                        }
                    },
                    onStrikeSelected    = {
                        val m = getPageMarks(currentPageIndex)
                        selectedTextRanges(currentPageIndex).forEach { range ->
                            if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == range.blockId && it.start == range.start && it.end == range.end && it.strikeThrough })
                                m.add(PdfMarkup.TextBlockLineMarkup(range.blockId, Color(currentColorLong), 3f, 1f, true, range.start, range.end))
                        }
                    },
                    onClearTextSelection = { viewModel.clearOcrSelection(currentPageIndex) },
                    onSetColorLong   = { currentColorLong = it },
                    onSetStrokeWidth = { currentStrokeWidth = it },
                    onDismissExportFeedback = { viewModel.clearExportFeedback() },
                    onOpenExportedFile = { state.lastExportedUri?.let { viewModel.openPdf(context, it) } },
                    onOpenAnotherPdf   = { pdfPickerLauncher.launch(arrayOf("*/*")) },
                    onEditorOpenChanged = { editorToolsOpen = it },
                    // Bumping the timestamp on RELEASE is half the fix: otherwise the hold ends and
                    // the effect resumes a window that is already most of the way expired, so the
                    // chrome blinks out a moment after the finger lifts.
                    onShareHoldChanged = { shareHolding = it; lastInteractionAtMs = System.currentTimeMillis() },
                    // Share now opens the export chooser instead of firing a PDF straight out — a
                    // .docx used to always leave as the converted PDF, silently. The dialog lets the
                    // user pick the original file vs a PDF (and encrypt the PDF). See ExportShareDialog.
                    onShareDocument    = { showShareDialog = true },
                    onRecolorSignature = { cl ->
                        activeImageLoc()?.let { (pg, idx, sig) ->
                            if (sig.isSignature) getPageMarks(pg)[idx] = sig.copy(bitmap = recolorSignatureBitmap(sig.bitmap, Color(cl).toArgb()))
                        }
                    },
                    backdrop = contentBackdrop,
                    uiSensor = uiSensor,
                    fg       = bottomFg,
                    fgSoft   = bottomFgSoft,
                    glass    = chromeGlass,
                    chip     = chromeField,
                    docKind  = com.chethan616.clearpdf.utils.docKindOf(state.fileName)
                )
            }
        }

        // ── Find bar ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showFindBar,
            enter    = fadeIn(tween(220)) + expandVertically(tween(280), Alignment.Bottom) + scaleIn(initialScale = 0.96f),
            exit     = fadeOut(tween(150)) + shrinkVertically(tween(180), Alignment.Bottom) + scaleOut(targetScale = 0.96f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PdfSearchBar(
                query             = findQuery,
                matchCount        = state.findMatches.size,
                currentMatchIndex = state.currentMatchIndex,
                focusRequester    = findFocusRequester,
                backdrop          = contentBackdrop,
                uiSensor          = uiSensor,
                fg                = findFg,
                fgSoft            = findFgSoft,
                surface           = chromeGlass,
                onQueryChange     = { q -> findQuery = q; viewModel.searchText(q) },
                onPrevMatch       = { viewModel.prevMatch(); lastInteractionAtMs = System.currentTimeMillis() },
                onNextMatch       = { viewModel.nextMatch(); lastInteractionAtMs = System.currentTimeMillis() },
                onClose           = { showFindBar = false; focusManager.clearFocus(); findQuery = ""; viewModel.clearSearch() }
            )
        }

        // ── Page-jump popup (in-window, so it samples the liquid-glass backdrop) ──
        LiquidPageJumpPopup(
            visible      = showPageJumpDialog,
            currentPage  = currentPageIndex,
            pageCount    = safePageCount,
            backdrop     = contentBackdrop,
            uiSensor     = uiSensor,
            fg           = panelFg,
            fgSoft       = panelFgSoft,
            surface      = chromePanel,
            field        = chromeField,
            onDismiss    = { showPageJumpDialog = false },
            onJumpToPage = { targetPage -> showPageJumpDialog = false; scrollToPage(targetPage) }
        )

        // ── Text / note editor (in-window, samples the real page backdrop) ──
        editingAnnoId?.let { annoId ->
            fun matches(m: PdfMarkup) =
                (m is PdfMarkup.TextBoxMarkup && m.id == annoId) || (m is PdfMarkup.NoteMarkup && m.id == annoId)
            AnnotationEditorDialog(
                isNote = editingAnnoIsNote,
                initialText = annotationDraft,
                initialColor = editingAnnoColor,
                backdrop = contentBackdrop,
                uiSensor = uiSensor,
                fg = panelFg,
                fgSoft = panelFgSoft,
                surface = chromePanel,
                field = chromeField,
                onDismiss = {
                    getPageMarks(editingAnnoPage).removeAll { m -> matches(m) &&
                        ((m is PdfMarkup.TextBoxMarkup && m.text.isBlank()) || (m is PdfMarkup.NoteMarkup && m.text.isBlank())) }
                    editingAnnoId = null
                },
                onDelete = {
                    getPageMarks(editingAnnoPage).removeAll { matches(it) }
                    editingAnnoId = null
                },
                onSave = { newText, newColor ->
                    val list = getPageMarks(editingAnnoPage)
                    val idx = list.indexOfFirst { matches(it) }
                    if (idx >= 0) {
                        if (newText.isBlank() && list[idx] is PdfMarkup.TextBoxMarkup) list.removeAt(idx)
                        else list[idx] = when (val m = list[idx]) {
                            is PdfMarkup.TextBoxMarkup -> m.copy(text = newText, color = newColor)
                            is PdfMarkup.NoteMarkup    -> m.copy(text = newText, color = newColor)
                            else -> m
                        }
                    }
                    editingAnnoId = null
                }
            )
        }

        // ── Shape editor (recolour / delete a placed rect / oval / line / arrow / stroke) ──
        editingShapePage?.let { shapePage ->
            val list = getPageMarks(shapePage)
            val shape = list.getOrNull(editingShapeIndex)
            if (shape != null && shape.isShape()) {
                ShapeEditorPopup(
                    initialColor = shape.shapeColor(),
                    backdrop = contentBackdrop,
                    uiSensor = uiSensor,
                    fg = panelFg,
                    fgSoft = panelFgSoft,
                    surface = chromePanel,
                    field = chromeField,
                    onColorChange = { c ->
                        val cur = list.getOrNull(editingShapeIndex)
                        if (cur != null && cur.isShape()) list[editingShapeIndex] = cur.recolored(c)
                        lastInteractionAtMs = System.currentTimeMillis()
                    },
                    onDelete = {
                        if (editingShapeIndex in list.indices) list.removeAt(editingShapeIndex)
                        editingShapePage = null; editingShapeIndex = -1
                    },
                    onDismiss = { editingShapePage = null; editingShapeIndex = -1 }
                )
            } else {
                editingShapePage = null; editingShapeIndex = -1
            }
        }

        // ── Save Document — REAL in-window glass (samples the live page) ──
        LiquidSaveSheet(
            visible         = showSaveDialog,
            initialFileName = state.document?.name?.substringBeforeLast('.')?.let { "${it}_Edited" } ?: "Document",
            backdrop        = contentBackdrop,
            uiSensor        = uiSensor,
            fg              = panelFg,
            fgSoft          = panelFgSoft,
            surface         = chromePanel,
            field           = chromeField,
            onDismiss       = { showSaveDialog = false },
            onSave          = { fileName, overrideUri ->
                showSaveDialog = false
                val overlays = buildExportOverlays(annotationsByPage, state.ocrBlocksByPage, pageCanvasSizes, pageBitmapSizes)
                if (overlays.isNotEmpty()) viewModel.exportEditedPdf(context, overlays, fileName, overrideUri)
            }
        )

        // ── Share / export chooser ──
        // Original vs PDF (with optional encryption). `originalExt` is null for a plain PDF, which
        // collapses the dialog to just the encrypt toggle.
        val shareExt = state.fileName.substringAfterLast('.', "").uppercase()
        ExportShareDialog(
            visible     = showShareDialog,
            originalExt = if (shareExt.isNotBlank() && shareExt != "PDF") shareExt else null,
            backdrop    = contentBackdrop,
            uiSensor    = uiSensor,
            fg          = panelFg,
            fgSoft      = panelFgSoft,
            surface     = chromePanel,
            field       = chromeField,
            onDismiss   = { showShareDialog = false },
            onShare     = { format, encrypt, password ->
                showShareDialog = false
                viewerScope.launch {
                    // All file work off the main thread: copy/encrypt, then hand a FileProvider uri to
                    // the chooser. Everything lands in cacheDir/shared, which the app FileProvider
                    // serves (file_paths.xml cache-path "/").
                    val payload = withContext(Dispatchers.IO) {
                        runCatching {
                            fun wrap(u: android.net.Uri): android.net.Uri =
                                if (u.scheme == "file")
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", java.io.File(u.path!!)
                                    )
                                else u
                            val shareDir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                            when (format) {
                                ShareFormat.ORIGINAL -> state.originalUri?.let { orig ->
                                    // Mirror the original into our own storage so the target app can
                                    // actually read it — a SAF uri from another provider can't be
                                    // re-granted to a third app.
                                    val safeName = state.fileName.ifBlank { "document.$shareExt" }
                                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                    val out = java.io.File(shareDir, "${System.currentTimeMillis()}_$safeName")
                                    context.contentResolver.openInputStream(orig)?.use { input ->
                                        out.outputStream().use { input.copyTo(it) }
                                    } ?: return@runCatching null
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.provider", out
                                    ) to (context.contentResolver.getType(orig) ?: "application/octet-stream")
                                }
                                ShareFormat.PDF -> state.document?.uri?.let { pdf ->
                                    if (encrypt && password.isNotBlank()) {
                                        val out = java.io.File(shareDir, "protected_${System.currentTimeMillis()}.pdf")
                                        val outUri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.provider", out
                                        )
                                        com.kyant.pdfcore.security.PdfSecurityService.encryptToUri(context, pdf, outUri, password)
                                        outUri to "application/pdf"
                                    } else {
                                        wrap(pdf) to "application/pdf"
                                    }
                                }
                            }
                        }.getOrNull()
                    }
                    if (payload != null) {
                        val (shareUri, mime) = payload
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(send, null)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }
        )

        // ── Opening curtain ────────────────────────────────────────────────
        // The very same dark curtain the no-document branch shows while a handed-in file loads, now
        // held over the freshly-loaded viewer until page 1 has actually rendered — so the reader is
        // never flashed a blank white page card. Because both branches paint the identical opaque
        // fill and spinner, the hand-off between them is invisible; only when the first bitmap arrives
        // does this fade away, letting the real document appear underneath. That is what makes tapping
        // a recent read as "wait a beat, then the PDF fades in" instead of a hard cut to an empty page.
        val firstPageRendered = state.pageBitmaps.getOrNull(0) != null
        var revealDocument by remember { mutableStateOf(false) }
        LaunchedEffect(firstPageRendered) {
            if (firstPageRendered && !revealDocument) {
                delay(140)          // let the page paint a frame before we lift the curtain
                revealDocument = true
            }
        }
        AnimatedVisibility(
            visible  = !revealDocument,
            exit     = fadeOut(tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            ViewerLoadingCurtain(isLight = isLight)
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (showSignaturePad) {
        SignaturePadDialog(
            backdrop = backdrop,
            onDismiss = { showSignaturePad = false },
            onSignatureCaptured = { bmp ->
                showSignaturePad = false
                runCatching {
                    val safeBmp = when {
                        bmp.isRecycled -> null
                        bmp.config == Bitmap.Config.HARDWARE || !bmp.isMutable -> bmp.copy(Bitmap.Config.ARGB_8888, true)
                        else -> bmp
                    }
                    if (safeBmp != null && safeBmp.width > 0 && safeBmp.height > 0) {
                        placeImageMarkup(safeBmp, isSignature = true)
                    }
                }
            }
        )
    }

}

/**
 * A plain opaque fill shown while a handed-in document loads — no spinner, no indicator, nothing to
 * look at. It exists only so that (a) the "Open a PDF" picker never flashes before the pages arrive,
 * and (b) the loaded viewer can fade in from behind it. It's identical in the no-document branch and
 * the loaded overlay, so the branch swap is seamless; when the first page is ready the overlay simply
 * fades out (see the AnimatedVisibility that hosts it) to reveal the PDF. That fade is the whole
 * "opening" animation — deliberately just a dissolve.
 */
@Composable
private fun ViewerLoadingCurtain(isLight: Boolean) {
    val bg = if (isLight) Color(0xFF0A0E14) else Color(0xFF05070B)
    Box(Modifier.fillMaxSize().background(bg))
}

/**
 * Cheap average perceptual luminance (0..1) of a page bitmap, sampled at a tiny
 * resolution. Drives the viewer's adaptive chrome contrast (dark ink on light pages,
 * white ink on dark pages).
 */
/**
 * Perceptual luminance of a vertical slice `[fTop, fBottom]` (fractions of height) of [bitmap],
 * sampled on a sparse 8x5 grid across the central 80% of the width. Deliberately uses `getPixel`
 * on the live bitmap rather than `createScaledBitmap` so it is cheap enough to run per scroll frame.
 * Central-width only: the far edges are usually margins that don't sit under a control.
 */
private fun regionLuminance(bitmap: Bitmap, fTop: Float, fBottom: Float): Float = runCatching {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return@runCatching 0f
    val y0 = (fTop.coerceIn(0f, 1f) * h).toInt().coerceIn(0, h - 1)
    val y1 = (fBottom.coerceIn(0f, 1f) * h).toInt().coerceIn(y0 + 1, h)
    val x0 = (w * 0.1f).toInt().coerceIn(0, w - 1)
    val x1 = (w * 0.9f).toInt().coerceIn(x0 + 1, w)
    val cols = 8
    val rows = 5
    var sum = 0.0
    var n = 0
    for (r in 0 until rows) {
        val py = (y0 + (y1 - y0) * (r + 0.5f) / rows).toInt().coerceIn(0, h - 1)
        for (c in 0 until cols) {
            val px = (x0 + (x1 - x0) * (c + 0.5f) / cols).toInt().coerceIn(0, w - 1)
            val p = bitmap.getPixel(px, py)
            val rr = ((p shr 16) and 0xFF) / 255.0
            val gg = ((p shr 8) and 0xFF) / 255.0
            val bb = (p and 0xFF) / 255.0
            sum += 0.299 * rr + 0.587 * gg + 0.114 * bb
            n++
        }
    }
    if (n == 0) 0f else (sum / n).toFloat()
}.getOrDefault(0f)

/**
 * Average luminance of the document content behind a screen-space horizontal band
 * `[screenTopPx, screenBottomPx]`, i.e. behind one of the floating bars.
 *
 * The content Box scales about a top origin with `translationY = 0`, and the LazyColumn's
 * `visibleItemsInfo.offset` is already in list-local (unscaled, viewport-relative, centring-folded)
 * coordinates — so a screen Y maps to list-local as `screenY / scale`. Each visible page item that
 * intersects the band contributes its overlapping slice, weighted by how much of the band it covers.
 * Returns 0 (→ dark → white ink) when the band sits over the empty letterbox with no page under it,
 * which is exactly what a single unzoomed page's centred layout should read as behind its bars.
 */
private fun bandLuminance(
    layoutInfo: LazyListLayoutInfo,
    pageBitmaps: List<Bitmap?>,
    scale: Float,
    screenTopPx: Float,
    screenBottomPx: Float
): Float {
    val s = scale.coerceAtLeast(0.01f)
    val lTop = screenTopPx / s
    val lBottom = screenBottomPx / s
    var lum = 0.0
    var weight = 0.0
    for (item in layoutInfo.visibleItemsInfo) {
        if (item.size <= 0) continue
        val io = item.offset.toFloat()
        val ib = io + item.size.toFloat()
        val interTop = maxOf(io, lTop)
        val interBottom = minOf(ib, lBottom)
        val cover = interBottom - interTop
        if (cover <= 0f) continue
        val bmp = pageBitmaps.getOrNull(item.index) ?: continue
        val fTop = (interTop - io) / item.size
        val fBottom = (interBottom - io) / item.size
        lum += regionLuminance(bmp, fTop, fBottom).toDouble() * cover
        weight += cover.toDouble()
    }
    return if (weight <= 0.0) 0f else (lum / weight).toFloat()
}

private fun averageLuminance(bitmap: Bitmap): Float = runCatching {
    val w = 12
    val h = 16
    val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    if (scaled != bitmap) scaled.recycle()
    var sum = 0.0
    for (p in pixels) {
        val r = ((p shr 16) and 0xFF) / 255.0
        val g = ((p shr 8) and 0xFF) / 255.0
        val b = (p and 0xFF) / 255.0
        sum += 0.299 * r + 0.587 * g + 0.114 * b
    }
    (sum / pixels.size).toFloat()
}.getOrDefault(0f)

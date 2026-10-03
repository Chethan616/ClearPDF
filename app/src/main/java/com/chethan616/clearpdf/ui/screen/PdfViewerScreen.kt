package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.gestures.scrollBy
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.PdfPageBookmarksManager
import com.chethan616.clearpdf.ui.components.UnsavedChangesDialog
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
import com.chethan616.clearpdf.ui.selection.PdfCopiedToast
import com.chethan616.clearpdf.ui.selection.PdfSelectionActions
import com.chethan616.clearpdf.ui.selection.PdfSelectionHandles
import com.chethan616.clearpdf.ui.selection.PdfSelectionToolbar
import com.chethan616.clearpdf.ui.selection.PdfTextSelectionState
import com.chethan616.clearpdf.ui.selection.PdfViewportTransform
import com.chethan616.clearpdf.ui.selection.TextPos
import com.chethan616.clearpdf.ui.selection.pdfTextSelectionGestures
import com.chethan616.clearpdf.ui.selection.rememberSelectionAutoScroller
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun PdfViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit,
    // True when the caller (recents / external open / a tool's output) already handed us a document
    // to load. In that case the viewer must NOT flash its "Open a PDF" picker while the pages render —
    // it shows a loading curtain that the real document fades in behind. See [ViewerLoadingCurtain].
    pendingLoad: Boolean = false,
    // "Get it" on the Office engine hint: open Settings at the engine section to show progress.
    onOpenOfficeEngineSettings: () -> Unit = {}
) {
    val state         by viewModel.uiState.collectAsState()
    val isDarkMode     = LocalIsDarkMode.current
    val isLight        = !isDarkMode
    val text           = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub            = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent         = Color(0xFF1976D2)
    val uiSensor       = rememberUISensor()
    val context        = LocalContext.current
    val bookmarkUri    = state.originalUri ?: state.document?.uri
    val clipboard      = LocalClipboardManager.current
    val focusManager   = LocalFocusManager.current
    val activity       = context as? Activity
    val view           = LocalView.current
    val scope          = rememberCoroutineScope()

    // ── Local UI state ─────────────────────────────────────────────────────
    var controlsVisible     by rememberSaveable { mutableStateOf(true) }
    var controlsPinned      by rememberSaveable { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(false) }
    var autoScrollSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var viewerResumed by remember { mutableStateOf(true) }
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
    var bookmarkedPages by remember { mutableStateOf<List<Int>>(emptyList()) }
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
    var selectedMarkupGroupPage by remember { mutableStateOf<Int?>(null) }
    var selectedMarkupGroup by remember { mutableStateOf<Set<Int>>(emptySet()) }
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

    // ── Unsaved-edit tracking ──────────────────────────────────────────────
    // Markups live here (not in the ViewModel), so "dirty" is: the current markups differ from what
    // was last exported. PdfMarkup variants are data classes, so a structural compare of a per-page
    // copy is exact — drawing, erasing, moving, retyping a note all flip it, undoing back to the
    // saved state clears it. Covers PDFs and Office documents alike (both render through here).
    fun markupSnapshot(): Map<Int, List<PdfMarkup>> =
        annotationsByPage.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
    var savedMarkups        by remember { mutableStateOf<Map<Int, List<PdfMarkup>>>(emptyMap()) }
    var pendingSaveMarkups  by remember { mutableStateOf<Map<Int, List<PdfMarkup>>?>(null) }
    var exitAfterSave       by remember { mutableStateOf(false) }
    var showUnsavedDialog   by remember { mutableStateOf(false) }
    val hasUnsavedEdits     by remember { derivedStateOf { markupSnapshot() != savedMarkups } }

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

    LaunchedEffect(bookmarkUri) {
        bookmarkedPages = bookmarkUri?.let { PdfPageBookmarksManager.get(context, it) }.orEmpty()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewerResumed = true
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewerResumed = false
                autoScroll = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(autoScroll, autoScrollSpeed, state.document?.uri, activeTool, scale, viewerResumed) {
        if (!autoScroll || state.document == null || !viewerResumed || activeTool != PdfEditTool.None || scale > 1.01f) {
            if (autoScroll && (!viewerResumed || activeTool != PdfEditTool.None || scale > 1.01f || state.document == null)) autoScroll = false
            return@LaunchedEffect
        }
        var previousFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (previousFrame == 0L) {
                previousFrame = frame
                continue
            }
            val elapsedSeconds = ((frame - previousFrame).coerceAtMost(50_000_000L)) / 1_000_000_000f
            previousFrame = frame
            val pixels = with(density) { 36.dp.toPx() * autoScrollSpeed * elapsedSeconds }
            if (listState.scrollBy(pixels) <= 0f) {
                autoScroll = false
                break
            }
        }
    }

    // ── Text selection (long-press, native-style handles, glass toolbar) ─────────────────────
    val textSelection = remember { PdfTextSelectionState() }
    textSelection.blocksProvider = { p -> viewModel.uiState.value.ocrBlocksByPage[p].orEmpty() }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    textSelection.transformProvider = { PdfViewportTransform(containerWidthPx.toFloat(), scale, offsetX) }
    val selectionAutoScroller = rememberSelectionAutoScroller(listState, textSelection)
    selectionAutoScroller.panBy = { dx ->
        val cw = containerWidthPx.toFloat()
        val maxOffsetX = ((cw * scale - cw) / 2f).coerceAtLeast(0f)
        val next = (offsetX + dx).coerceIn(-maxOffsetX, maxOffsetX)
        val applied = next - offsetX
        offsetX = next
        applied
    }
    var copiedTick by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

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
        autoScroll = false
        controlsVisible = true; controlsPinned = false
        lastInteractionAtMs = System.currentTimeMillis()
        scale = 1f; offsetX = 0f
        activeTool = PdfEditTool.None
        selectedAnnoPage = null; selectedAnnoIndex = -1
        selectedMarkupGroupPage = null; selectedMarkupGroup = emptySet()
        annotationsByPage.clear(); undoStack.clear(); pageCanvasSizes.clear(); pageBitmapSizes.clear()
        savedMarkups = emptyMap(); pendingSaveMarkups = null; exitAfterSave = false; showUnsavedDialog = false
        textSelection.resetDocument(); viewModel.clearExportFeedback()
        showFindBar = false; findQuery = ""; viewModel.clearSearch()
    }

    LaunchedEffect(state.document, controlsVisible, controlsPinned, activeTool, selectedMarkupGroup.size, editorToolsOpen, shareHolding, lastInteractionAtMs) {
        if (state.document != null && controlsVisible && !controlsPinned && !autoScroll && scale <= 1.01f && activeTool == PdfEditTool.None && selectedMarkupGroup.size <= 1 && !editorToolsOpen && !shareHolding) {
            val snap = lastInteractionAtMs
            // Comfortable auto-hide window; any interaction bumps lastInteractionAtMs and
            // restarts this. It never fires while a tool is active (activeTool != None), while
            // the Editor Tools panel is open, or while the share capsule is held — a long-press
            // produces no pointer events for the viewer to see, so without that last guard the
            // chrome hid itself out from under the finger mid-gesture.
            delay(5000)
            if (controlsVisible && !controlsPinned && !autoScroll && scale <= 1.01f && activeTool == PdfEditTool.None && selectedMarkupGroup.size <= 1 && !editorToolsOpen && !shareHolding && snap == lastInteractionAtMs)
                controlsVisible = false
        }
    }

    LaunchedEffect(state.passwordUri) {
        if (state.passwordUri != null) { passwordText = ""; delay(120); passwordFocusRequester.requestFocus() }
    }

    /** Leave the viewer — via the "Save changes?" card when there are unexported markups. */
    fun requestExit() {
        if (state.document != null && hasUnsavedEdits) { controlsVisible = true; showUnsavedDialog = true }
        else onBack()
    }

    // An export started from the unsaved-changes card (or any Save) settles here: success marks the
    // exported markups as saved and, if the card asked for it, leaves; failure keeps the user here.
    LaunchedEffect(state.exportMessage, state.exportError) {
        val pending = pendingSaveMarkups ?: return@LaunchedEffect
        if (state.exportMessage != null) {
            savedMarkups = pending; pendingSaveMarkups = null
            if (exitAfterSave) { exitAfterSave = false; onBack() }
        } else if (state.exportError != null) {
            pendingSaveMarkups = null; exitAfterSave = false
        }
    }

    BackHandler(enabled = state.document != null) {
        if (!controlsVisible) controlsVisible = true else requestExit()
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
    LaunchedEffect(state.document?.uri) {
        if (state.document != null) {
            val targetPage = state.currentPage.coerceIn(0, safePageCount - 1)
            if (listState.firstVisibleItemIndex != targetPage) listState.scrollToItem(targetPage)
        }
    }
    val currentPageIndex = listState.firstVisibleItemIndex.coerceIn(0, safePageCount - 1)
    // Back clears an active text selection first (registered after the viewer's own BackHandler, so
    // it takes precedence), exactly like a TextView's selection mode.
    val selectionActive by remember { derivedStateOf { textSelection.hasSelection } }
    BackHandler(enabled = selectionActive) { textSelection.clear() }
    // Any editing tool owns the page gestures; a selection must not linger underneath it.
    LaunchedEffect(activeTool) { if (activeTool != PdfEditTool.None) textSelection.clear() }

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

    // The top visible page drives text extraction, the chrome-tint sampling, and (via
    // `selectionPageIndex`/toolbar targets) which page an action applies to — all of that needs to
    // track the scroll position immediately, every tick.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> viewModel.onPageChanged(idx.coerceIn(0, safePageCount - 1)) }
    }
    // Bitmap-cache eviction is a separate, DEBOUNCED signal. It used to run on every single one of
    // those ticks: during a fast scroll or fling through a many-page document (a converted .pptx,
    // scrolled through quickly, was the easiest way to see it) the "current page" advances every
    // few milliseconds, and evicting far-from-current bitmaps on every tick recycled pages that
    // were still transiting through the viewport a frame later — the freshly-evicted page then had
    // to re-render from scratch before it could be shown again, which is what "flickering"/
    // "buffering" while scrolling actually was: real re-render work, triggered far more often than
    // the eviction needed to happen. Coalescing rapid ticks into one sweep after they stop still
    // bounds memory (nothing pins pages forever, it just evicts once per settled position instead
    // of once per pixel) while never evicting a page mid-transit.
    LaunchedEffect(listState, state.originalUri, safePageCount) {
        snapshotFlow { listState.firstVisibleItemIndex.coerceIn(0, safePageCount - 1) }
            .distinctUntilChanged()
            .debounce(180)
            .collect { idx ->
                viewModel.trimBitmapCache(idx)
                state.originalUri?.let { uri ->
                    RecentFilesManager.updateProgress(context, uri, idx, safePageCount)
                }
            }
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
        PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow, PdfEditTool.Lasso
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

        // Re-render the focused page after zoom settles. Scaling a screen-sized bitmap makes PDF
        // text and fine diagrams soft; a bounded higher-resolution page keeps pinch zoom readable
        // without triggering a native render for every pointer frame.
        LaunchedEffect(state.document?.uri, currentPageIndex, renderWidthPx) {
            snapshotFlow { scale }
                .distinctUntilChanged()
                .debounce(220)
                .collect { zoom ->
                    if (zoom < 1.18f) return@collect
                    val desired = (renderWidthPx * zoom.coerceAtMost(2.5f))
                        .roundToInt()
                        .coerceAtMost(3072)
                        .coerceAtLeast(renderWidthPx)
                    if (desired > renderWidthPx) viewModel.renderPage(context, currentPageIndex, desired)
                }
        }

        // Warms the pages just ahead of (and one behind) wherever scrolling currently is, so a page's
        // render has a head start instead of only beginning once it scrolls into view — undebounced
        // is fine here since `prefetchAround`/`renderPage` are no-ops for a page already rendered or
        // already in flight.
        LaunchedEffect(currentPageIndex, renderWidthPx) {
            viewModel.prefetchAround(context, currentPageIndex, renderWidthPx)
        }

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
                    .onSizeChanged { containerHeightPx = it.height; containerWidthPx = it.width }
                    // Long-press → select word. First in the chain so, once it fires, it can consume
                    // in the Initial pass ahead of the zoom/tap handlers and the page list.
                    .pdfTextSelectionGestures(
                        state = textSelection,
                        autoScroller = selectionAutoScroller,
                        haptics = haptics,
                        enabled = { activeTool == PdfEditTool.None },
                        onSelectionStarted = { autoScroll = false; lastInteractionAtMs = System.currentTimeMillis() }
                    )
                    .then(
                        // Keep pinch zoom available in Select Text mode. Other editing tools own
                        // the page gesture surface because their strokes/shapes need the drag.
                        if (activeTool != PdfEditTool.None) Modifier
                        else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                autoScroll = false
                                do {
                                    val event = awaitPointerEvent()
                                    // A selection drag owns the finger: no one-finger pan underneath it.
                                    if (textSelection.gestureActive) continue
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
                            onTap = {
                                // Tap outside clears a text selection (and does nothing else), like a
                                // TextView; otherwise it toggles the reading chrome.
                                if (textSelection.hasSelection) textSelection.clear()
                                else if (activeTool == PdfEditTool.None) controlsVisible = !controlsVisible
                                lastInteractionAtMs = System.currentTimeMillis()
                            },
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
                    }.onGloballyPositioned { textSelection.registerLayer(it) }
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
                                selectedMarkupIndices   = if (page == selectedMarkupGroupPage) selectedMarkupGroup else emptySet(),
                                onSelectMarkup          = { idx ->
                                    if (idx < 0) {
                                        selectedAnnoPage = null; selectedAnnoIndex = -1
                                        selectedMarkupGroupPage = null; selectedMarkupGroup = emptySet()
                                    }
                                    else {
                                        selectedAnnoPage = page; selectedAnnoIndex = idx
                                        selectedMarkupGroupPage = null; selectedMarkupGroup = emptySet()
                                        textSelection.clear()
                                    }
                                },
                                onSelectMarkups        = { indices ->
                                    selectedAnnoPage = null; selectedAnnoIndex = -1
                                    selectedMarkupGroupPage = page.takeIf { indices.size > 1 }
                                    selectedMarkupGroup = indices
                                },
                                onMoveMarkups           = { indices, delta ->
                                    val pageMarks = getPageMarks(page)
                                    indices.sorted().forEach { idx ->
                                        pageMarks.getOrNull(idx)?.let { markup ->
                                            pageMarks[idx] = markup.translated(delta)
                                        }
                                    }
                                },
                                onDeleteMarkup          = { idx ->
                                    val m = getPageMarks(page); if (idx in m.indices) m.removeAt(idx)
                                    selectedAnnoPage = null; selectedAnnoIndex = -1
                                    selectedMarkupGroupPage = null; selectedMarkupGroup = emptySet()
                                },
                                textSelection = textSelection
                            )
                        }
                    }
                }
            }
        }

        // ── Text-selection handles + magnifier (outside the zoom layer: fixed on-screen size) ──
        PdfSelectionHandles(
            state = textSelection,
            listState = listState,
            autoScroller = selectionAutoScroller,
            pageBitmaps = state.pageBitmaps
        )

        AnimatedVisibility(
            visible = controlsVisible && state.document != null && safePageCount > 1 && scale <= 1.01f && activeTool == PdfEditTool.None,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)
        ) {
            Row(
                Modifier
                    .viewerGlass(contentBackdrop, chromeGlass, shape = { com.kyant.shapes.Capsule })
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LiquidIconButton(
                    onClick = {
                        autoScroll = !autoScroll
                        controlsVisible = true
                        lastInteractionAtMs = System.currentTimeMillis()
                    },
                    backdrop = contentBackdrop,
                    tint = if (autoScroll) accent else Color.Unspecified,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        if (autoScroll) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (autoScroll) R.string.viewer_auto_scroll_pause else R.string.viewer_auto_scroll_start),
                        Modifier.size(20.dp),
                        if (autoScroll) Color.White else bottomFg
                    )
                }
                BasicText(
                    text = "${autoScrollSpeed}×",
                    modifier = Modifier
                        .semantics { contentDescription = context.getString(R.string.viewer_auto_scroll_speed) }
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            autoScrollSpeed = when (autoScrollSpeed) {
                                0.75f -> 1f
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 0.75f
                            }
                            lastInteractionAtMs = System.currentTimeMillis()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = TextStyle(bottomFg, 13.sp, FontWeight.SemiBold)
                )
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
                    LiquidIconButton(onClick = { requestExit() }, backdrop = contentBackdrop) {
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

            // Brief confirmation that the optional Office engine rendered this file, so installing
            // it has a visible effect (before, nothing told the user which renderer was used).
            var showEngineChip by remember(state.renderedByOfficeEngine, state.document) {
                mutableStateOf(state.renderedByOfficeEngine)
            }
            LaunchedEffect(showEngineChip) {
                if (showEngineChip) { kotlinx.coroutines.delay(2800L); showEngineChip = false }
            }
            AnimatedVisibility(
                visible  = showEngineChip,
                enter    = fadeIn(spring(stiffness = 300f)),
                exit     = fadeOut(tween(220)),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp)
            ) {
                Row(
                    Modifier
                        .viewerGlass(contentBackdrop, viewerChromeGlass(isDarkMode), shape = { com.kyant.shapes.Capsule })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), LiquidGlassColors.Teal)
                    BasicText(
                        stringResource(R.string.office_engine_rendered),
                        style = TextStyle(topFg, 13.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            // One-time Office engine suggestion after a built-in Office render. Glass fades only
            // (translating glass re-runs its blur + lens).
            AnimatedVisibility(
                visible  = state.showOfficeEngineHint && controlsVisible,
                enter    = fadeIn(spring(stiffness = 300f)),
                exit     = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
            ) {
                OfficeEngineHintCard(
                    backdrop  = contentBackdrop,
                    isDark    = isDarkMode,
                    fg        = bottomFg,
                    onGet     = {
                        com.chethan616.clearpdf.office.OfficeEngine.installer(context).install()
                        viewModel.dismissOfficeEngineHint(context)
                        com.chethan616.clearpdf.office.OfficeEngine.focusSettingsSection.value = true
                        onOpenOfficeEngineSettings()
                    },
                    onDismiss = { viewModel.dismissOfficeEngineHint(context) }
                )
            }

            AnimatedVisibility(
                visible = controlsVisible && selectedMarkupGroup.size > 1,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.96f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
            ) {
                LiquidButton(
                    onClick = {
                        selectedMarkupGroup = emptySet()
                        selectedMarkupGroupPage = null
                        lastInteractionAtMs = System.currentTimeMillis()
                    },
                    backdrop = contentBackdrop,
                    surfaceColor = chromeField
                ) {
                    BasicText(
                        stringResource(R.string.viewer_lasso_selected_count, selectedMarkupGroup.size) +
                            " · " + stringResource(R.string.viewer_lasso_done),
                        style = TextStyle(bottomFg, 13.sp, fontWeight = FontWeight.SemiBold)
                    )
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
                    onSetActiveTool    = {
                        activeTool = it
                        if (it == PdfEditTool.None) activeImageId = null
                        selectedAnnoPage = null; selectedAnnoIndex = -1
                        if (it != PdfEditTool.None) { selectedMarkupGroupPage = null; selectedMarkupGroup = emptySet() }
                        textSelection.clear()
                    },
                    onToggleFindBar    = {
                        showFindBar = !showFindBar
                        if (showFindBar) viewModel.triggerOcrForAllPages(context)
                        else { focusManager.clearFocus(); viewModel.clearSearch(); findQuery = "" }
                    },
                    onShowSignaturePad = { showSignaturePad = true },
                    onPickImage        = { activeImageId = null; imagePickerLauncher.launch("image/*") },
                    onZoomStep         = { step ->
                        val startScale = scale
                        val targetScale = (startScale + step).coerceIn(1f, 5f)
                        if (targetScale != startScale) {
                            // Keep the point under the viewport centre steady while the glass
                            // zoom rail changes scale, just like a focal pinch or double tap.
                            val focusY = containerHeightPx / 2f
                            val scrollDelta = focusY * (1f / startScale - 1f / targetScale)
                            val focalSpec = androidx.compose.animation.core.spring<Float>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            viewerScope.launch { animateZoomPan(targetScale, Offset(offsetX, 0f)) }
                            viewerScope.launch { listState.animateScrollBy(scrollDelta, focalSpec) }
                        }
                        lastInteractionAtMs = System.currentTimeMillis()
                    },
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

        // ── Floating liquid-glass selection toolbar (above the chrome, like the platform's) ──
        suspend fun selectionTextLoaded(): String {
            val span = textSelection.pageSpan() ?: return ""
            val missing = span.any { it !in viewModel.uiState.value.ocrBlocksByPage }
            if (missing) {
                viewModel.triggerOcrForAllPages(context)
                withTimeoutOrNull(10_000) {
                    viewModel.uiState.first { st -> span.all { it in st.ocrBlocksByPage } }
                }
            }
            return textSelection.selectedText()
        }
        fun startSafely(intent: Intent) {
            runCatching {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
        fun applyToSelection(block: (page: Int, marks: MutableList<PdfMarkup>, range: OcrTextRange) -> Boolean) {
            textSelection.rangesByPage().forEach { (page, ranges) ->
                val marks = getPageMarks(page)
                var changed = false
                ranges.forEach { r -> if (block(page, marks, r)) changed = true }
                if (changed) recordEdit(page)
            }
            lastInteractionAtMs = System.currentTimeMillis()
        }
        val selectionActions = PdfSelectionActions(
            onCopy = {
                viewerScope.launch {
                    val t = selectionTextLoaded()
                    if (t.isNotBlank()) {
                        clipboard.setText(AnnotatedString(t))
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        copiedTick++
                    }
                    textSelection.clear()
                }
            },
            onSelectAll = {
                // The whole document. Pages whose text is not extracted yet join the highlight as
                // they load (the end caret is clamped per page).
                viewModel.triggerOcrForAllPages(context)
                textSelection.select(TextPos(0, 0), TextPos(safePageCount - 1, Int.MAX_VALUE))
            },
            onShare = {
                viewerScope.launch {
                    val t = selectionTextLoaded()
                    if (t.isNotBlank()) {
                        startSafely(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null
                        ))
                    }
                    textSelection.clear()
                }
            },
            onHighlight = { colorLong ->
                currentColorLong = colorLong
                applyToSelection { _, m, r ->
                    // Re-highlighting replaces (recolours) highlights inside the range instead of stacking.
                    m.removeAll { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == r.blockId && it.start >= r.start && it.end <= r.end }
                    m.add(PdfMarkup.TextBlockHighlightMarkup(r.blockId, Color(colorLong), 0.38f, r.start, r.end))
                    true
                }
                textSelection.clear()
            },
            onUnderline = {
                applyToSelection { _, m, r ->
                    if (m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == r.blockId && it.start == r.start && it.end == r.end && !it.strikeThrough }) false
                    else m.add(PdfMarkup.TextBlockLineMarkup(r.blockId, Color(currentColorLong), 3f, 1f, false, r.start, r.end))
                }
                textSelection.clear()
            },
            onStrike = {
                applyToSelection { _, m, r ->
                    if (m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == r.blockId && it.start == r.start && it.end == r.end && it.strikeThrough }) false
                    else m.add(PdfMarkup.TextBlockLineMarkup(r.blockId, Color(currentColorLong), 3f, 1f, true, r.start, r.end))
                }
                textSelection.clear()
            },
            onRemoveHighlight = {
                textSelection.rangesByPage().forEach { (page, ranges) ->
                    getPageMarks(page).removeAll { m ->
                        m is PdfMarkup.TextBlockHighlightMarkup && ranges.any { r -> r.blockId == m.blockId && m.start < r.end && m.end > r.start }
                    }
                }
                textSelection.clear()
            },
            onSearch = {
                val q = textSelection.selectedText().replace(Regex("\\s+"), " ").trim().take(80)
                textSelection.clear()
                if (q.isNotEmpty()) {
                    showFindBar = true
                    findQuery = q
                    viewModel.triggerOcrForAllPages(context)
                    viewModel.searchText(q)
                }
            },
            onProcessText = { component ->
                viewerScope.launch {
                    val t = selectionTextLoaded()
                    if (t.isNotBlank()) {
                        startSafely(
                            Intent(Intent.ACTION_PROCESS_TEXT)
                                .setComponent(component)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_PROCESS_TEXT, t)
                                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        )
                    }
                }
            }
        )
        // A lambda, evaluated inside the toolbar: reading the selection here would recompose this
        // whole screen on every handle-drag step.
        val hasHighlightOverlap: () -> Boolean = {
            textSelection.hasSelection && textSelection.rangesByPage().any { (page, ranges) ->
                annotationsByPage[page].orEmpty().any { m ->
                    m is PdfMarkup.TextBlockHighlightMarkup && ranges.any { r -> r.blockId == m.blockId && m.start < r.end && m.end > r.start }
                }
            }
        }
        PdfSelectionToolbar(
            state = textSelection,
            listState = listState,
            backdrop = contentBackdrop,
            actions = selectionActions,
            highlightColor = currentColor,
            hasHighlightOverlap = hasHighlightOverlap
        )
        PdfCopiedToast(
            trigger = copiedTick,
            backdrop = contentBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 112.dp)
        )

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
            bookmarkedPages = bookmarkedPages,
            onDismiss    = { showPageJumpDialog = false },
            onJumpToPage = { targetPage -> showPageJumpDialog = false; scrollToPage(targetPage) },
            onToggleCurrentBookmark = {
                bookmarkUri?.let { uri -> bookmarkedPages = PdfPageBookmarksManager.toggle(context, uri, currentPageIndex) }
            },
            onRemoveBookmark = { page ->
                bookmarkUri?.let { uri -> bookmarkedPages = PdfPageBookmarksManager.toggle(context, uri, page) }
            }
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
            if (shape != null && shape.isRecolorable()) {
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
                        if (cur != null && cur.isRecolorable()) list[editingShapeIndex] = cur.recolored(c)
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
            onDismiss       = { showSaveDialog = false; exitAfterSave = false },
            onSave          = { fileName, overrideUri ->
                showSaveDialog = false
                val snapshot = markupSnapshot()
                val overlays = buildExportOverlays(annotationsByPage, state.ocrBlocksByPage, pageCanvasSizes, pageBitmapSizes)
                if (overlays.isNotEmpty()) {
                    pendingSaveMarkups = snapshot
                    viewModel.exportEditedPdf(context, overlays, fileName, overrideUri)
                } else {
                    // Nothing exportable (e.g. only empty text boxes) — nothing to lose either.
                    savedMarkups = snapshot
                    if (exitAfterSave) { exitAfterSave = false; onBack() }
                }
            }
        )

        // ── Unsaved changes — same card as the spreadsheet, refracting the live page ──
        // Save routes through the normal Save sheet (file name / location), then leaves once the
        // export succeeds (see the exportMessage effect above).
        UnsavedChangesDialog(
            visible   = showUnsavedDialog,
            onDiscard = { showUnsavedDialog = false; onBack() },
            onCancel  = { showUnsavedDialog = false },
            onSave    = { showUnsavedDialog = false; exitAfterSave = true; showSaveDialog = true },
            backdrop  = contentBackdrop
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

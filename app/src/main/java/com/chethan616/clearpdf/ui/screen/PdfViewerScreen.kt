package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.ViewerChromeGlass
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit
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
    var lastInteractionAtMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    // Adobe-style single-axis reading: the viewer is vertical-only. Horizontal
    // panning is still allowed inside a zoomed page (handled in PdfPageCanvas).
    val scrollOrientation = ScrollOrientation.Vertical
    var showPageJumpDialog  by rememberSaveable { mutableStateOf(false) }
    var activeTool          by rememberSaveable { mutableStateOf(PdfEditTool.None) }
    var currentColorLong    by rememberSaveable { mutableLongStateOf(0xFF00BCD4L) }
    val currentColor         = Color(currentColorLong)
    var currentStrokeWidth  by rememberSaveable { mutableFloatStateOf(6f) }
    var activeImageId       by remember { mutableStateOf<Long?>(null) }
    var showSaveDialog      by rememberSaveable { mutableStateOf(false) }
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
        val page = state.currentPage
        val marks = getPageMarks(page)
        val existingIdx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
        if (existingIdx >= 0) {
            val existing = marks[existingIdx] as PdfMarkup.ImageMarkup
            val ratio = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
            val currentW = kotlin.math.abs(existing.end.x - existing.start.x).coerceAtLeast(20f)
            marks[existingIdx] = existing.copy(bitmap = bmp, end = Offset(existing.end.x, existing.start.y + currentW * ratio), isSignature = false)
        } else {
            val cs = pageCanvasSizes[page] ?: Size(bmp.width.toFloat(), bmp.height.toFloat())
            val frame = fitBitmapRect(cs, (pageBitmapSizes[page]?.width ?: cs.width), (pageBitmapSizes[page]?.height ?: cs.height))
            val maxW = (if (frame.width > 0f) frame.width else cs.width) * 0.5f
            val ratio = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
            val w = maxW.coerceAtLeast(1f)
            val center = if (frame.width > 0f) frame.center else Offset(cs.width / 2f, cs.height / 2f)
            val id = System.nanoTime()
            marks.add(PdfMarkup.ImageMarkup(id, bmp, Offset(center.x - w / 2f, center.y - w * ratio / 2f), Offset(center.x + w / 2f, center.y + w * ratio / 2f), false))
            activeImageId = id
            activeTool = PdfEditTool.Image
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
        annotationsByPage.clear(); pageCanvasSizes.clear(); pageBitmapSizes.clear()
        viewModel.clearOcrSelection(state.currentPage); viewModel.clearExportFeedback()
        showFindBar = false; findQuery = ""; viewModel.clearSearch()
    }

    LaunchedEffect(state.document, controlsVisible, controlsPinned, activeTool, lastInteractionAtMs) {
        if (state.document != null && controlsVisible && !controlsPinned && scale <= 1.01f && activeTool == PdfEditTool.None) {
            val snap = lastInteractionAtMs
            delay(3500)
            if (controlsVisible && !controlsPinned && scale <= 1.01f && activeTool == PdfEditTool.None && snap == lastInteractionAtMs)
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
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                    Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(18.dp), text)
                }
                LiquidGlassTopBar(stringResource(R.string.viewer_title), backdrop, uiSensor, Modifier.weight(1f))
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
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
            AnimatedVisibility(
                visible = state.passwordRequired && state.passwordUri != null,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit  = fadeOut() + scaleOut(targetScale = 0.98f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).imePadding()
            ) {
                Column(
                    Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp),
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
    val listState     = rememberLazyListState()
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
    val currentPageBitmap = state.pageBitmaps.getOrNull(currentPageIndex)
    val isLightChrome = remember(currentPageBitmap) {
        currentPageBitmap?.let { averageLuminance(it) > 0.60f } ?: false
    }
    val chromeFg     = if (isLightChrome) Color(0xFF15171C) else Color.White
    val chromeFgSoft = if (isLightChrome) Color(0xFF15171C).copy(0.62f) else Color.White.copy(0.62f)
    val chromeGlass  = if (isLightChrome) Color(0xFFEFF1F4).copy(0.66f) else ViewerChromeGlass
    val chromeField  = if (isLightChrome) Color.Black.copy(0.06f) else Color.White.copy(0.10f)

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
                        // Zoom/pan only in reading mode; tool gestures own the page while editing.
                        if (activeTool != PdfEditTool.None) Modifier
                        else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange  = event.calculatePan()
                                    val zoomed = scale > 1.001f
                                    if (zoomChange != 1f || zoomed) {
                                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                        val cw = size.width.toFloat()
                                        val maxOffsetX = ((cw * newScale - cw) / 2f).coerceAtLeast(0f)
                                        // Synchronous state writes — no per-event coroutine (Pdf_Tools parity).
                                        scale = newScale
                                        if (newScale > 1f) {
                                            offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            // Vertical pan is LazyColumn scroll, which lives in UNSCALED
                                            // content px — divide by scale so content tracks the finger 1:1
                                            // instead of racing ahead at higher zoom.
                                            if (panChange.y != 0f) listState.dispatchRawDelta(-panChange.y / newScale)
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
                            onTap = { controlsVisible = !controlsVisible; lastInteractionAtMs = System.currentTimeMillis() },
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
                        userScrollEnabled = activeTool == PdfEditTool.None && scale <= 1.01f,
                        modifier = Modifier.fillMaxSize(),
                        // Center the block when it's shorter than the viewport (e.g. a
                        // single page) so it sits neatly centered instead of pinned to
                        // the top with a dark blank half below. No effect once content
                        // overflows (multi-page / zoomed), where it scrolls normally.
                        verticalArrangement = Arrangement.Center,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 4.dp + extraBottomPadding)
                    ) {
                        items(count = safePageCount, key = { it }) { page ->
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
                                onToggleControls   = { controlsVisible = !controlsVisible },
                                onShowControls     = { controlsVisible = true },
                                onActiveToolChanged     = { activeTool = it },
                                onActiveImageIdChanged  = { activeImageId = it },
                                onToggleOcrSelection    = { viewModel.toggleOcrSelection(page, it) },
                                onClearOcrSelection     = { viewModel.clearOcrSelection(page) },
                                onSelectLine            = { viewModel.selectLine(page, it) },
                                onSelectParagraph       = { viewModel.selectParagraph(page, it) },
                                onSelectOcrRange        = { viewModel.selectOcrBlocks(page, it, append = true) },
                                onPlaceText             = { pt ->
                                    val id = System.nanoTime()
                                    getPageMarks(page).add(PdfMarkup.TextBoxMarkup(id, pt, "", currentColor, 40f))
                                    editingAnnoId = id; editingAnnoPage = page; editingAnnoIsNote = false; annotationDraft = ""
                                    activeTool = PdfEditTool.None; controlsVisible = true
                                },
                                onPlaceNote             = { pt ->
                                    val id = System.nanoTime()
                                    getPageMarks(page).add(PdfMarkup.NoteMarkup(id, pt, "", Color(0xFFFFC107)))
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
                                    controlsVisible = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Page scrubber ─────────────────────────────────────────────────
        // Always present on multi-page docs so fast scrubbing is one drag away,
        // independent of the auto-hiding chrome. The rail itself stays slim when idle.
        AnimatedVisibility(
            visible  = safePageCount > 1 && scale <= 1.01f,
            enter    = fadeIn(androidx.compose.animation.core.spring(stiffness = Spring.StiffnessMedium)) + scaleIn(initialScale = 0.92f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit     = fadeOut(androidx.compose.animation.core.spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterEnd).navigationBarsPadding().statusBarsPadding().padding(end = 8.dp)
        ) {
            PageScrubber(
                currentPage     = currentPageIndex,
                pageCount       = safePageCount,
                pageBitmaps     = state.pageBitmaps,
                backdrop        = contentBackdrop,
                uiSensor        = uiSensor,
                onPageChange    = { page -> scrollToPage(page) },
                onPageScrubbing = { page -> viewModel.renderPage(context, page, 400); lastInteractionAtMs = System.currentTimeMillis() }
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
                // Compact, balanced top toolbar: back · centered page pill · search.
                // Page navigation lives in the right-edge scrubber + scroll, so no
                // oversized prev/next controls cover the document.
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    LiquidIconButton(onClick = onBack, backdrop = contentBackdrop, surfaceColor = chromeGlass) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), chromeFg)
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidButton(
                        onClick      = { showPageJumpDialog = true },
                        backdrop     = contentBackdrop,
                        surfaceColor = chromeGlass
                    ) {
                        BasicText(
                            stringResource(R.string.viewer_page_of, state.currentPage + 1, safePageCount),
                            style = TextStyle(chromeFg, 13.sp, FontWeight.SemiBold)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidIconButton(
                        onClick = {
                            showFindBar = !showFindBar
                            if (showFindBar) viewModel.triggerOcrForAllPages(context)
                            else { focusManager.clearFocus(); viewModel.clearSearch(); findQuery = "" }
                        },
                        backdrop = contentBackdrop,
                        surfaceColor = chromeGlass
                    ) {
                        Icon(Icons.Rounded.Search, stringResource(R.string.viewer_find), Modifier.size(20.dp), chromeFg)
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
                val activeItem = getPageMarks(state.currentPage)
                    .firstOrNull { it is PdfMarkup.ImageMarkup && it.id == activeImageId } as? PdfMarkup.ImageMarkup

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
                    selectedTextCount  = state.selectedOcrBlockIdsByPage[state.currentPage]?.size ?: 0,
                    currentSelectedIds = state.selectedOcrBlockIdsByPage[state.currentPage].orEmpty(),
                    activeIsSignature  = activeItem?.isSignature == true,
                    currentPageMarks   = getPageMarks(state.currentPage),
                    onSetActiveTool    = { activeTool = it; if (it == PdfEditTool.None) activeImageId = null; viewModel.clearOcrSelection(state.currentPage) },
                    onToggleFindBar    = {
                        showFindBar = !showFindBar
                        if (showFindBar) viewModel.triggerOcrForAllPages(context)
                        else { focusManager.clearFocus(); viewModel.clearSearch(); findQuery = "" }
                    },
                    onShowSignaturePad = { showSignaturePad = true },
                    onPickImage        = { imagePickerLauncher.launch("image/*") },
                    onResetZoom        = { scope.launch { animateZoomPan(1f, Offset.Zero) }
                                          lastInteractionAtMs = System.currentTimeMillis() },
                    onShowSaveDialog   = { showSaveDialog = true },
                    onImageDone        = { activeImageId = null; activeTool = PdfEditTool.None },
                    onReplaceImage     = {
                        if (activeItem?.isSignature == true) showSignaturePad = true
                        else imagePickerLauncher.launch("image/*")
                    },
                    onDeleteImage      = {
                        val m = getPageMarks(state.currentPage)
                        val idx = m.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                        if (idx >= 0) m.removeAt(idx)
                        activeImageId = null; activeTool = PdfEditTool.None
                    },
                    onSelectAllText    = {
                        val ids = state.ocrBlocksByPage[state.currentPage].orEmpty().map { it.id }.toSet()
                        if (ids.isNotEmpty()) viewModel.selectOcrBlocks(state.currentPage, ids, false)
                    },
                    onCopyText         = {
                        viewModel.getSelectedOcrText(state.currentPage).takeIf { it.isNotBlank() }
                            ?.let { clipboard.setText(AnnotatedString(it)) }
                    },
                    onHighlightSelected = {
                        val m = getPageMarks(state.currentPage)
                        state.selectedOcrBlockIdsByPage[state.currentPage].orEmpty().forEach { id ->
                            if (!m.any { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == id })
                                m.add(PdfMarkup.TextBlockHighlightMarkup(id, Color(currentColorLong), 0.30f))
                        }
                    },
                    onUnderlineSelected = {
                        val m = getPageMarks(state.currentPage)
                        state.selectedOcrBlockIdsByPage[state.currentPage].orEmpty().forEach { id ->
                            if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == id && !it.strikeThrough })
                                m.add(PdfMarkup.TextBlockLineMarkup(id, Color(currentColorLong), 3f, 1f, false))
                        }
                    },
                    onStrikeSelected    = {
                        val m = getPageMarks(state.currentPage)
                        state.selectedOcrBlockIdsByPage[state.currentPage].orEmpty().forEach { id ->
                            if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == id && it.strikeThrough })
                                m.add(PdfMarkup.TextBlockLineMarkup(id, Color(currentColorLong), 3f, 1f, true))
                        }
                    },
                    onClearTextSelection = { viewModel.clearOcrSelection(state.currentPage) },
                    onSetColorLong   = { currentColorLong = it },
                    onSetStrokeWidth = { currentStrokeWidth = it },
                    onDismissExportFeedback = { viewModel.clearExportFeedback() },
                    onOpenExportedFile = { state.lastExportedUri?.let { viewModel.openPdf(context, it) } },
                    onOpenAnotherPdf   = { pdfPickerLauncher.launch(arrayOf("*/*")) },
                    onRecolorSignature = { cl ->
                        val m = getPageMarks(state.currentPage)
                        val idx = m.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId && it.isSignature }
                        val sig = m.getOrNull(idx) as? PdfMarkup.ImageMarkup
                        if (sig != null) m[idx] = sig.copy(bitmap = recolorSignatureBitmap(sig.bitmap, Color(cl).toArgb()))
                    },
                    backdrop = contentBackdrop,
                    uiSensor = uiSensor,
                    fg       = chromeFg,
                    fgSoft   = chromeFgSoft,
                    glass    = chromeGlass,
                    chip     = chromeField
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
                fg                = chromeFg,
                fgSoft            = chromeFgSoft,
                surface           = chromeGlass,
                field             = chromeField,
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
            fg           = chromeFg,
            fgSoft       = chromeFgSoft,
            surface      = chromeGlass,
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
                backdrop = contentBackdrop,
                uiSensor = uiSensor,
                fg = chromeFg,
                fgSoft = chromeFgSoft,
                surface = chromeGlass,
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
                onSave = { newText ->
                    val list = getPageMarks(editingAnnoPage)
                    val idx = list.indexOfFirst { matches(it) }
                    if (idx >= 0) {
                        if (newText.isBlank()) list.removeAt(idx)
                        else list[idx] = when (val m = list[idx]) {
                            is PdfMarkup.TextBoxMarkup -> m.copy(text = newText)
                            is PdfMarkup.NoteMarkup    -> m.copy(text = newText)
                            else -> m
                        }
                    }
                    editingAnnoId = null
                }
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showSaveDialog) {
        LiquidSaveDialog(
            initialFileName = state.document?.name?.substringBeforeLast('.')?.let { "${it}_Edited" } ?: "Document",
            backdrop        = backdrop,
            uiSensor        = uiSensor,
            onDismiss       = { showSaveDialog = false },
            onSave          = { fileName, overrideUri ->
                showSaveDialog = false
                val overlays = buildExportOverlays(annotationsByPage, state.ocrBlocksByPage, pageCanvasSizes, pageBitmapSizes)
                if (overlays.isNotEmpty()) viewModel.exportEditedPdf(context, overlays, fileName, overrideUri)
            }
        )
    }

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
                        val page  = state.currentPage
                        val marks = getPageMarks(page)
                        val existingIdx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                        if (existingIdx >= 0 && (marks[existingIdx] as PdfMarkup.ImageMarkup).isSignature) {
                            val existing = marks[existingIdx] as PdfMarkup.ImageMarkup
                            val ratio = safeBmp.height.toFloat() / safeBmp.width.toFloat().coerceAtLeast(1f)
                            val currentW = kotlin.math.abs(existing.end.x - existing.start.x).coerceAtLeast(20f)
                            marks[existingIdx] = existing.copy(bitmap = safeBmp, end = Offset(existing.end.x, existing.start.y + currentW * ratio), isSignature = true)
                        } else {
                            val rawCs = pageCanvasSizes[page]
                            val cs = if (rawCs != null && rawCs.width > 50f && rawCs.height > 50f) rawCs else Size(1000f, 1400f)
                            val bs = pageBitmapSizes[page] ?: cs
                            val frame = fitBitmapRect(cs, bs.width, bs.height)
                            val maxW = (if (frame.width > 50f) frame.width else cs.width) * 0.45f
                            val ratio = safeBmp.height.toFloat() / safeBmp.width.toFloat().coerceAtLeast(1f)
                            val w = maxW.coerceAtLeast(100f)
                            val center = if (frame.width > 50f) frame.center else Offset(cs.width / 2f, cs.height / 2f)
                            val id = System.nanoTime()
                            marks.add(PdfMarkup.ImageMarkup(id, safeBmp, Offset(center.x - w / 2f, center.y - w * ratio / 2f), Offset(center.x + w / 2f, center.y + w * ratio / 2f), true))
                            activeImageId = id; activeTool = PdfEditTool.Image
                        }
                    }
                }
            }
        )
    }

}

/**
 * Cheap average perceptual luminance (0..1) of a page bitmap, sampled at a tiny
 * resolution. Drives the viewer's adaptive chrome contrast (dark ink on light pages,
 * white ink on dark pages).
 */
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

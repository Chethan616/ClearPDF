package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import androidx.compose.material.icons.rounded.Close
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.viewmodel.ExportOverlay
import com.chethan616.clearpdf.ui.viewmodel.NormalizedPoint
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Zoom / gesture constants
// ─────────────────────────────────────────────────────────────────────────────
private const val MIN_ZOOM             = 1.0f   // never zoom below fit-to-page
private const val MAX_ZOOM             = 8.0f
private const val ZOOM_SLOP            = 0.01f  // centroid-size fraction before zoom commits
private const val PAN_SLOP_PX          = 8f     // px movement before direction is decided
private const val SWIPE_ANGLE_DEG      = 38f    // angle from horizontal that counts as "swipe to page"
private const val SNAP_BACK_THRESHOLD  = 1.08f  // if zoom released below this, spring back to 1×
private const val FLING_FRICTION       = 0.92f  // per-frame velocity decay for pan fling
private const val FLING_MIN_VELOCITY   = 50f    // px/s below which fling stops
private const val DOUBLE_TAP_ZOOM_1    = 2.5f   // first double-tap zoom level
private const val DOUBLE_TAP_ZOOM_2    = 4.5f   // second double-tap zoom level

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
    val activity       = context as? Activity
    val view           = LocalView.current
    val scope          = rememberCoroutineScope()

    var controlsVisible     by rememberSaveable { mutableStateOf(true) }
    var controlsPinned      by rememberSaveable { mutableStateOf(false) }
    var lastInteractionAtMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var scrollOrientation   by rememberSaveable { mutableStateOf(if (com.chethan616.clearpdf.data.repository.AppSettingsManager.getScrollOrientation(context) == 1) ScrollOrientation.Horizontal else ScrollOrientation.Vertical) }
    var showPageJumpDialog  by rememberSaveable { mutableStateOf(false) }

    // ── Zoom / pan – backed by Animatable for smooth transitions ─────────────
    val zoomAnim = remember { Animatable(1f) }
    val panXAnim = remember { Animatable(0f) }
    val panYAnim = remember { Animatable(0f) }

    // Convenience read-only aliases (Animatable.value is State-backed → recomposition)
    // NOTE: These are read at each recomposition; lambdas that need live values
    // must read zoomAnim.value / panXAnim.value directly (see gesture handlers).
    val zoomScale: Float  = zoomAnim.value
    val panOffset: Offset = Offset(panXAnim.value, panYAnim.value)

    // Zoom HUD
    var showZoomHud by remember { mutableStateOf(false) }
    val zoomHudText  = "${(zoomAnim.value * 100 + 0.5f).toInt()}%"

    LaunchedEffect(zoomAnim.value) {
        if (zoomAnim.value > 1.05f) {
            showZoomHud = true
            delay(900)
            showZoomHud = false
        } else {
            showZoomHud = false
        }
    }

    var activeTool         by rememberSaveable { mutableStateOf(PdfEditTool.None) }
    var currentColorLong   by rememberSaveable { mutableLongStateOf(0xFF00BCD4L) }
    val currentColor        = Color(currentColorLong)
    var currentStrokeWidth by rememberSaveable { mutableFloatStateOf(6f) }
    var activeImageId      by remember { mutableStateOf<Long?>(null) }
    var showSaveDialog     by rememberSaveable { mutableStateOf(false) }

    val annotationsByPage  = remember { mutableStateMapOf<Int, MutableList<PdfMarkup>>() }
    val pageCanvasSizes    = remember { mutableStateMapOf<Int, Size>() }
    val pageBitmapSizes    = remember { mutableStateMapOf<Int, Size>() }

    fun getPageMarks(page: Int): MutableList<PdfMarkup> =
        annotationsByPage.getOrPut(page) { mutableStateListOf() }

    // Picks an image from the device and stamps it onto the current page, centred.
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        val page = state.currentPage
        val cs = pageCanvasSizes[page] ?: Size(bmp.width.toFloat(), bmp.height.toFloat())
        val frame = fitBitmapRect(cs, (pageBitmapSizes[page]?.width ?: cs.width), (pageBitmapSizes[page]?.height ?: cs.height))
        val maxW = (if (frame.width > 0f) frame.width else cs.width) * 0.5f
        val ratio = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
        val w = maxW.coerceAtLeast(1f)
        val h = w * ratio
        val center = if (frame.width > 0f) frame.center else Offset(cs.width / 2f, cs.height / 2f)
        val id = System.nanoTime()
        getPageMarks(page).add(
            PdfMarkup.ImageMarkup(
                id = id,
                bitmap = bmp,
                start = Offset(center.x - w / 2f, center.y - h / 2f),
                end = Offset(center.x + w / 2f, center.y + h / 2f)
            )
        )
        activeImageId = id
        activeTool = PdfEditTool.Image
    }

    // Helper: animate zoom + pan together
    suspend fun animateZoomPan(
        targetZoom: Float,
        targetPan: Offset,
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float    = Spring.StiffnessMedium
    ) {
        val spec = spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness)
        coroutineScope {
            launch { zoomAnim.animateTo(targetZoom, spec) }
            launch { panXAnim.animateTo(targetPan.x, spec) }
            launch { panYAnim.animateTo(targetPan.y, spec) }
        }
    }

    // Helper: snap zoom + pan instantly (during active gestures)
    suspend fun snapZoomPan(targetZoom: Float, targetPan: Offset) {
        coroutineScope {
            launch { zoomAnim.snapTo(targetZoom) }
            launch { panXAnim.snapTo(targetPan.x) }
            launch { panYAnim.snapTo(targetPan.y) }
        }
    }

    val immersiveActive = state.document != null && !controlsVisible

    DisposableEffect(activity, view, immersiveActive) {
        val ctrl = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersiveActive) ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        else ctrl?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(state.document?.uri) {
        controlsVisible = true
        controlsPinned  = false
        lastInteractionAtMs = System.currentTimeMillis()
        zoomAnim.snapTo(1f); panXAnim.snapTo(0f); panYAnim.snapTo(0f)
        activeTool = PdfEditTool.None
        annotationsByPage.clear(); pageCanvasSizes.clear(); pageBitmapSizes.clear()
        viewModel.clearOcrSelection(state.currentPage)
        viewModel.clearExportFeedback()
    }

    LaunchedEffect(state.document, controlsVisible, controlsPinned, activeTool, lastInteractionAtMs) {
        if (state.document != null && controlsVisible && !controlsPinned
            && zoomAnim.value <= 1.01f && activeTool == PdfEditTool.None
        ) {
            val snap = lastInteractionAtMs
            delay(3500)
            if (controlsVisible && !controlsPinned && zoomAnim.value <= 1.01f
                && activeTool == PdfEditTool.None && snap == lastInteractionAtMs)
                controlsVisible = false
        }
    }

    BackHandler(enabled = state.document != null) {
        if (!controlsVisible) controlsVisible = true else onBack()
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openPdf(context, it) }
    }

    // ── Empty state ───────────────────────────────────────────────────────────
    if (state.document == null) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                    Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
                }
                LiquidGlassTopBar("PDF Viewer", backdrop, uiSensor, Modifier.weight(1f))
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
                    BasicText("Open a PDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        "Select a PDF file from your device to view it",
                        style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                    )
                    LiquidButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        backdrop = backdrop, tint = accent
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                            BasicText("Pick a PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
                state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFD32F2F), 14.sp)) }
            }
        }
        return
    }

    // ── Pager ─────────────────────────────────────────────────────────────────
    val safePageCount = state.pageCount.coerceAtLeast(1)
    val pagerState    = rememberPagerState(initialPage = 0) { safePageCount }
    val pagerScope    = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        // Reset zoom/pan with a smooth snap when page changes
        zoomAnim.snapTo(1f); panXAnim.snapTo(0f); panYAnim.snapTo(0f)
        viewModel.onPageChanged(pagerState.currentPage)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLight) Color(0xFF0E1218).copy(0.92f) else Color(0xFF050608).copy(0.92f))
    ) {
        val renderWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(720)

        val drawingToolActive = activeTool in setOf(
            PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect,
            PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow
        )

        // Pager owns swipe only at 1× zoom and no active editing tool
        val pagerScrollEnabled = activeTool == PdfEditTool.None && zoomScale <= 1.02f

        LaunchedEffect(pagerState.currentPage, renderWidthPx) {
            viewModel.renderPage(context, pagerState.currentPage, renderWidthPx)
            if (pagerState.currentPage - 1 >= 0)
                viewModel.renderPage(context, pagerState.currentPage - 1, renderWidthPx)
            if (pagerState.currentPage + 1 < safePageCount)
                viewModel.renderPage(context, pagerState.currentPage + 1, renderWidthPx)
        }

        val pageContent: @Composable (Int) -> Unit = pageContent@ { page ->





            val bitmap         = state.pageBitmaps.getOrNull(page)
            val marks          = getPageMarks(page)
            val ocrBlocks      = state.ocrBlocksByPage[page].orEmpty()
            val selectedOcrIds = state.selectedOcrBlockIdsByPage[page].orEmpty()

            var draftPoints    by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
            var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
            var draftRectEnd   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
            var selDragStart   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
            var selDragEnd     by remember(page, activeTool) { mutableStateOf<Offset?>(null) }

            if (bitmap == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0E1218)), Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }
                return@pageContent
            }

            pageBitmapSizes[page] = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { sz ->
                        pageCanvasSizes[page] = Size(sz.width.toFloat(), sz.height.toFloat())
                    }
            ) {
                // ── Gesture layer (UNTRANSFORMED) ─────────────────────────────
                // Gestures must live on a node that is NOT scaled by graphicsLayer,
                // otherwise pointer deltas arrive in the zoomed coordinate space and
                // desync from the screen-space pan/focal math. The graphicsLayer is
                // applied to an inner wrapper around the page content instead.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // ─────────────────────────────────────────────────────
                        // UNIFIED GESTURE HANDLER — Adobe-style focal-point zoom
                        //
                        // Algorithm:
                        //   2 fingers   → always ours; focal-point pinch-zoom
                        //   1 finger 1× horizontal → yield to pager
                        //   1 finger 1× vertical   → ignore (doc scroll)
                        //   1 finger zoomed-in      → pan + fling on lift
                        // ─────────────────────────────────────────────────────
                        .pointerInput(page, activeTool, scrollOrientation) {
                            if (activeTool != PdfEditTool.None) return@pointerInput

                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)

                                val velocityTracker = VelocityTracker()
                                var panAccum        = Offset.Zero
                                var determined      = false
                                var isOurGesture    = false

                                loop@ while (true) {
                                    val event   = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.isEmpty()) {
                                        // ── Finger(s) lifted ─────────────────
                                        // 1. Snap back to 1× if just below snap threshold
                                        val currentZoom = zoomAnim.value
                                        if (currentZoom < SNAP_BACK_THRESHOLD && currentZoom > 0.95f) {
                                            scope.launch {
                                                animateZoomPan(
                                                    targetZoom   = 1f,
                                                    targetPan    = Offset.Zero,
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness    = Spring.StiffnessMedium
                                                )
                                            }
                                        }
                                        // 2. Fling pan (only when zoomed in)
                                        else if (isOurGesture && currentZoom > 1.02f) {
                                            val vel     = velocityTracker.calculateVelocity()
                                            val cs      = pageCanvasSizes[page] ?: Size.Zero
                                            val bs      = pageBitmapSizes[page]  ?: Size.Zero
                                            scope.launch {
                                                var vx = vel.x * 0.016f   // convert px/s → px/frame @60fps
                                                var vy = vel.y * 0.016f
                                                while (vx * vx + vy * vy > FLING_MIN_VELOCITY * 0.016f * FLING_MIN_VELOCITY * 0.016f) {
                                                    val newPan = clampPanOffset(
                                                        Offset(panXAnim.value + vx, panYAnim.value + vy),
                                                        zoomAnim.value, cs, bs
                                                    )
                                                    snapZoomPan(zoomAnim.value, newPan)
                                                    vx *= FLING_FRICTION
                                                    vy *= FLING_FRICTION
                                                    delay(16L)
                                                }
                                            }
                                        }
                                        break@loop
                                    }

                                    // ── Two-finger pinch zoom ─────────────────
                                    if (pressed.size >= 2) {
                                        if (!determined) { determined = true; isOurGesture = true }

                                        val zoomDelta  = event.calculateZoom()
                                        val panDelta   = event.calculatePan()
                                        val centroid   = event.calculateCentroid(useCurrent = false)
                                        val centSize   = event.calculateCentroidSize(useCurrent = false)
                                        val cs         = pageCanvasSizes[page] ?: Size.Zero
                                        val bs         = pageBitmapSizes[page]  ?: Size.Zero
                                        val boxCenter  = Offset(cs.width / 2f, cs.height / 2f)

                                        if (centSize > 0f && abs(zoomDelta - 1f) > ZOOM_SLOP) {
                                            val oldScale = zoomAnim.value
                                            // Slightly accelerate zoom-in, resist zoom-out (natural feel)
                                            val boosted   = if (zoomDelta > 1f)
                                                zoomDelta.toDouble().pow(1.18).toFloat().coerceIn(1f, 1.30f)
                                            else
                                                zoomDelta.coerceIn(0.78f, 1f)
                                            val newScale  = (oldScale * boosted).coerceIn(MIN_ZOOM, MAX_ZOOM)

                                            // ── Focal-point pan formula ───────────────────────────────
                                            // Keeps the content beneath the pinch centroid stationary.
                                            //
                                            //   graphicsLayer scales around the box centre (boxCenter).
                                            //   A pixel at local coord L maps to screen:
                                            //     screen = (L - boxCenter) * scale + boxCenter + pan
                                            //
                                            //   Setting screen = centroid before & after zoom:
                                            //     newPan = (centroid - boxCenter) * (1 - newScale/oldScale)
                                            //              + oldPan * (newScale/oldScale)
                                            //
                                            // Then we also incorporate the two-finger pan delta.
                                            // ─────────────────────────────────────────────────────────
                                            val ratio      = newScale / oldScale
                                            val currentPan = Offset(panXAnim.value, panYAnim.value)
                                            val focalPan   = (centroid - boxCenter) * (1f - ratio) + currentPan * ratio
                                            val clampedPan = clampPanOffset(focalPan + panDelta * ratio, newScale, cs, bs)

                                            scope.launch { snapZoomPan(newScale, clampedPan) }
                                            lastInteractionAtMs = System.currentTimeMillis()
                                        } else if (pressed.size >= 2 && panDelta != Offset.Zero) {
                                            // Pure two-finger pan (fingers moving together without pinching)
                                            val cs2 = pageCanvasSizes[page] ?: Size.Zero
                                            val bs2 = pageBitmapSizes[page]  ?: Size.Zero
                                            val newPan = clampPanOffset(
                                                Offset(panXAnim.value, panYAnim.value) + panDelta,
                                                zoomAnim.value, cs2, bs2
                                            )
                                            scope.launch { snapZoomPan(zoomAnim.value, newPan) }
                                            lastInteractionAtMs = System.currentTimeMillis()
                                        }

                                        event.changes.forEach { it.consume() }
                                        continue@loop
                                    }

                                    // ── One-finger ───────────────────────────
                                    val change = pressed.first()
                                    val delta  = change.position - change.previousPosition

                                    velocityTracker.addPosition(
                                        change.uptimeMillis, change.position
                                    )

                                    if (!determined) {
                                        panAccum += delta
                                        val dist = panAccum.getDistance()
                                        if (dist > PAN_SLOP_PX) {
                                            determined = true
                                            val angleDeg = Math.toDegrees(
                                                abs(atan2(panAccum.y.toDouble(), panAccum.x.toDouble()))
                                            ).toFloat()
                                            val isHoriz = angleDeg <= SWIPE_ANGLE_DEG
                                                    || angleDeg >= (180f - SWIPE_ANGLE_DEG)

                                            val isSwipeForPager = if (scrollOrientation == ScrollOrientation.Horizontal) isHoriz else !isHoriz

                                            if (isSwipeForPager && zoomAnim.value <= 1.02f) {
                                                // Horizontal swipe at 1× → yield to pager
                                                isOurGesture = false
                                                break@loop
                                            }
                                            isOurGesture = true
                                        }
                                    } else if (isOurGesture && zoomAnim.value > 1.02f) {
                                        // Pan while zoomed in
                                        val cs = pageCanvasSizes[page] ?: Size.Zero
                                        val bs = pageBitmapSizes[page]  ?: Size.Zero
                                        val newPan = clampPanOffset(
                                            Offset(panXAnim.value + delta.x, panYAnim.value + delta.y),
                                            zoomAnim.value, cs, bs
                                        )
                                        scope.launch { snapZoomPan(zoomAnim.value, newPan) }
                                        change.consume()
                                        lastInteractionAtMs = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        // ─────────────────────────────────────────────────────
                        // Tap / double-tap
                        // ─────────────────────────────────────────────────────
                        .pointerInput(page, activeTool) {
                            detectTapGestures(
                                onTap = {
                                    // A single tap is the deliberate immersive-mode toggle:
                                    // hide the chrome, then bring it back on the next tap.
                                    controlsVisible = !controlsVisible
                                    lastInteractionAtMs = System.currentTimeMillis()
                                },
                                onDoubleTap = { tap ->
                                    if (activeTool != PdfEditTool.None) return@detectTapGestures
                                    lastInteractionAtMs = System.currentTimeMillis()

                                    val cs        = pageCanvasSizes[page] ?: return@detectTapGestures
                                    val bs        = pageBitmapSizes[page]  ?: return@detectTapGestures
                                    val boxCenter = Offset(cs.width / 2f, cs.height / 2f)

                                    val currentZoom = zoomAnim.value
                                    // Three-stage cycle: 1× → 2.5× → 4.5× → 1×
                                    val targetZoom = when {
                                        currentZoom < 1.5f -> DOUBLE_TAP_ZOOM_1
                                        currentZoom < 3.5f -> DOUBLE_TAP_ZOOM_2
                                        else               -> 1f
                                    }

                                    val targetPan = if (targetZoom <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        // Zoom toward the tap point (same focal formula)
                                        val ratio      = targetZoom / currentZoom
                                        val currentPan = Offset(panXAnim.value, panYAnim.value)
                                        val focalPan   = (tap - boxCenter) * (1f - ratio) + currentPan * ratio
                                        clampPanOffset(focalPan, targetZoom, cs, bs)
                                    }

                                    scope.launch {
                                        animateZoomPan(
                                            targetZoom   = targetZoom,
                                            targetPan    = targetPan,
                                            dampingRatio = if (targetZoom < currentZoom)
                                                               Spring.DampingRatioMediumBouncy
                                                           else Spring.DampingRatioLowBouncy,
                                            stiffness    = Spring.StiffnessMediumLow
                                        )
                                    }
                                }
                            )
                        }
                ) {
                  // ── Transformed content layer (zoom + pan applied here) ──────
                  Box(
                      modifier = Modifier
                          .fillMaxSize()
                          .graphicsLayer(
                              scaleX       = zoomScale,
                              scaleY       = zoomScale,
                              translationX = panOffset.x,
                              translationY = panOffset.y
                          )
                  ) {
                    Image(
                        bitmap          = bitmap.asImageBitmap(),
                        contentDescription = "Page ${page + 1}",
                        contentScale    = ContentScale.Fit,
                        modifier        = Modifier.fillMaxSize()
                    )

                    // ── Annotation canvas ─────────────────────────────────────
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val ir = fitBitmapRect(size, bitmap.width.toFloat(), bitmap.height.toFloat())

                        selectedOcrIds.forEach { id ->
                            ocrBlocks.firstOrNull { it.id == id }?.let { b ->
                                val r = ocrBlockToRect(b, ir)
                                drawRect(Color(0xFFFFEB3B).copy(0.30f), r.topLeft, r.size)
                            }
                        }
                        if (activeTool == PdfEditTool.SelectText) {
                            ocrBlocks.forEach { b ->
                                val r = ocrBlockToRect(b, ir)
                                drawRect(Color.White.copy(0.18f), r.topLeft, r.size, style = Stroke(1f))
                            }
                        }

                        marks.forEach { markup ->
                            when (markup) {
                                is PdfMarkup.StrokeMarkup -> {
                                    if (markup.points.size > 1) {
                                        val path = smoothPath(markup.points)
                                        drawPath(
                                            path, markup.color.copy(markup.alpha),
                                            style = Stroke(markup.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                }
                                is PdfMarkup.RectMarkup -> {
                                    val r = Rect(
                                        min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y),
                                        max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y)
                                    )
                                    if (markup.filled) drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size)
                                    else drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size, style = Stroke(3f))
                                }
                                is PdfMarkup.OvalMarkup -> {
                                    val r = Rect(
                                        min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y),
                                        max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y)
                                    )
                                    if (markup.filled) drawOval(markup.color.copy(markup.alpha), r.topLeft, r.size)
                                    else drawOval(markup.color.copy(markup.alpha), r.topLeft, r.size, style = Stroke(3f))
                                }
                                is PdfMarkup.LineMarkup -> {
                                    if (markup.arrowHead)
                                        drawArrow(markup.start, markup.end, markup.color.copy(markup.alpha), markup.width)
                                    else
                                        drawLine(markup.color.copy(markup.alpha), markup.start, markup.end, markup.width)
                                }
                                is PdfMarkup.TextBlockHighlightMarkup -> {
                                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                                        val r = ocrBlockToRect(b, ir)
                                        drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size)
                                    }
                                }
                                is PdfMarkup.TextBlockLineMarkup -> {
                                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                                        val r = ocrBlockToRect(b, ir)
                                        val y = if (markup.strikeThrough) r.center.y else r.bottom - r.height * 0.10f
                                        drawLine(markup.color.copy(markup.alpha), Offset(r.left, y), Offset(r.right, y), markup.width)
                                    }
                                }
                                is PdfMarkup.ImageMarkup -> {
                                    val r = Rect(
                                        min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y),
                                        max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y)
                                    )
                                    drawImage(
                                        image     = markup.bitmap.asImageBitmap(),
                                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                        srcSize   = androidx.compose.ui.unit.IntSize(markup.bitmap.width, markup.bitmap.height),
                                        dstOffset = androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()),
                                        dstSize   = androidx.compose.ui.unit.IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1))
                                    )
                                    if (activeTool == PdfEditTool.Image && markup.id == activeImageId) {
                                        drawRect(Color(0xFF1976D2), r.topLeft, r.size, style = Stroke(2f))
                                        // bottom-right resize handle
                                        drawCircle(Color(0xFF1976D2), 14f, Offset(r.right, r.bottom))
                                        drawCircle(Color.White, 7f, Offset(r.right, r.bottom))
                                    }
                                }
                            }
                        }

                        if (draftPoints.size > 1) {
                            val path = smoothPath(draftPoints)
                            val isHl = activeTool == PdfEditTool.Highlight
                            drawPath(
                                path,
                                currentColor.copy(if (isHl) 0.32f else 0.95f),
                                style = Stroke(
                                    if (isHl) currentStrokeWidth * 3.5f else currentStrokeWidth,
                                    cap = StrokeCap.Round, join = StrokeJoin.Round
                                )
                            )
                        }

                        if (draftRectStart != null && draftRectEnd != null) {
                            val s = draftRectStart!!; val e = draftRectEnd!!
                            val pr = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            when (activeTool) {
                                PdfEditTool.Rect      -> drawRect(Color(0xFF42A5F5), pr.topLeft, pr.size, style = Stroke(3f))
                                PdfEditTool.Ellipse   -> drawOval(Color(0xFF26A69A), pr.topLeft, pr.size, style = Stroke(3f))
                                PdfEditTool.Line      -> drawLine(Color(0xFF66BB6A), s, e, 4f)
                                PdfEditTool.Arrow     -> drawArrow(s, e, Color(0xFFEF5350), 4f)
                                else -> Unit
                            }
                        }

                        if (selDragStart != null && selDragEnd != null && activeTool == PdfEditTool.SelectText) {
                            val s = selDragStart!!; val e = selDragEnd!!
                            val r = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            drawRect(Color(0xFFAB47BC).copy(0.20f), r.topLeft, r.size)
                            drawRect(Color(0xFFAB47BC), r.topLeft, r.size, style = Stroke(2f))
                        }
                    }
                  } // transformed content layer
                } // gesture Box

                // ── Drawing overlay ───────────────────────────────────────────
                if (drawingToolActive) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(page, activeTool) {
                            fun toContent(o: Offset): Offset {
                                val cs = pageCanvasSizes[page] ?: Size.Zero
                                val bc = Offset(cs.width / 2f, cs.height / 2f)
                                return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                            }
                            val freehand = activeTool == PdfEditTool.Draw || activeTool == PdfEditTool.Highlight
                            detectDragGestures(
                                onDragStart = { start ->
                                    lastInteractionAtMs = System.currentTimeMillis()
                                    val p = toContent(start)
                                    if (freehand) draftPoints = listOf(p)
                                    else { draftRectStart = p; draftRectEnd = p }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    lastInteractionAtMs = System.currentTimeMillis()
                                    val p = toContent(change.position)
                                    if (freehand) draftPoints = draftPoints + p
                                    else draftRectEnd = p
                                },
                                onDragCancel = { draftPoints = emptyList(); draftRectStart = null; draftRectEnd = null },
                                onDragEnd = {
                                    when (activeTool) {
                                        PdfEditTool.Draw      -> if (draftPoints.size > 1) marks.add(PdfMarkup.StrokeMarkup(draftPoints, currentColor, currentStrokeWidth, 0.95f))
                                        PdfEditTool.Highlight -> if (draftPoints.size > 1) marks.add(PdfMarkup.StrokeMarkup(draftPoints, currentColor, currentStrokeWidth * 3.5f, 0.32f))
                                        PdfEditTool.Rect      -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.RectMarkup(s, e, currentColor, 1f, false)) } }
                                        PdfEditTool.Ellipse   -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.OvalMarkup(s, e, currentColor, 1f, false)) } }
                                        PdfEditTool.Line      -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.LineMarkup(s, e, currentColor, currentStrokeWidth, 1f, false)) } }
                                        PdfEditTool.Arrow     -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.LineMarkup(s, e, currentColor, currentStrokeWidth, 1f, true)) } }
                                        else -> Unit
                                    }
                                    draftPoints = emptyList(); draftRectStart = null; draftRectEnd = null
                                }
                            )
                        }
                    )
                }

                // ── Eraser overlay (tap / drag to remove marks) ────────────────
                if (activeTool == PdfEditTool.Eraser) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(page) {
                            fun toContent(o: Offset): Offset {
                                val cs = pageCanvasSizes[page] ?: Size.Zero
                                val bc = Offset(cs.width / 2f, cs.height / 2f)
                                return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                            }
                            fun eraseAt(o: Offset) {
                                val p = toContent(o)
                                val idx = marks.indexOfLast { it.hitTest(p) }
                                if (idx >= 0) marks.removeAt(idx)
                            }
                            detectTapGestures { eraseAt(it); lastInteractionAtMs = System.currentTimeMillis() }
                        }.pointerInput(page) {
                            fun toContent(o: Offset): Offset {
                                val cs = pageCanvasSizes[page] ?: Size.Zero
                                val bc = Offset(cs.width / 2f, cs.height / 2f)
                                return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                            }
                            detectDragGestures(onDrag = { change, _ ->
                                change.consume()
                                val p = toContent(change.position)
                                val idx = marks.indexOfLast { it.hitTest(p) }
                                if (idx >= 0) marks.removeAt(idx)
                                lastInteractionAtMs = System.currentTimeMillis()
                            })
                        }
                    )
                }

                // ── Image move / resize overlay ────────────────────────────────
                if (activeTool == PdfEditTool.Image && activeImageId != null) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(page, activeImageId) {
                            fun toContent(o: Offset): Offset {
                                val cs = pageCanvasSizes[page] ?: Size.Zero
                                val bc = Offset(cs.width / 2f, cs.height / 2f)
                                return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                            }
                            var resizing = false
                            detectDragGestures(
                                onDragStart = { start ->
                                    val p = toContent(start)
                                    val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                                    val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup
                                    resizing = img != null && (p - img.end).getDistance() <= 36f
                                    lastInteractionAtMs = System.currentTimeMillis()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                                    val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup ?: return@detectDragGestures
                                    val d = dragAmount / zoomAnim.value
                                    marks[idx] = if (resizing) {
                                        val newEnd = Offset(
                                            (img.end.x + d.x).coerceAtLeast(img.start.x + 24f),
                                            (img.end.y + d.y).coerceAtLeast(img.start.y + 24f)
                                        )
                                        img.copy(end = newEnd)
                                    } else {
                                        img.copy(start = img.start + d, end = img.end + d)
                                    }
                                    lastInteractionAtMs = System.currentTimeMillis()
                                }
                            )
                        }
                    )
                }

                // ── OCR selection overlay ──────────────────────────────────────
                if (activeTool == PdfEditTool.SelectText) {
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(page, ocrBlocks) {
                                fun toContent(o: Offset): Offset {
                                    val cs = pageCanvasSizes[page] ?: Size.Zero
                                    val bc = Offset(cs.width / 2f, cs.height / 2f)
                                    return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                                }
                                detectTapGestures { tap ->
                                    lastInteractionAtMs = System.currentTimeMillis()
                                    val cs    = pageCanvasSizes[page] ?: return@detectTapGestures
                                    val bs    = pageBitmapSizes[page]  ?: return@detectTapGestures
                                    val frame = fitBitmapRect(cs, bs.width, bs.height)
                                    val hit   = hitTestOcrBlock(ocrBlocks, toContent(tap), frame)
                                    if (hit != null) viewModel.toggleOcrSelection(page, hit.id)
                                    else viewModel.clearOcrSelection(page)
                                }
                            }
                            .pointerInput(page, ocrBlocks) {
                                fun toContent(o: Offset): Offset {
                                    val cs = pageCanvasSizes[page] ?: Size.Zero
                                    val bc = Offset(cs.width / 2f, cs.height / 2f)
                                    return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { s ->
                                        lastInteractionAtMs = System.currentTimeMillis()
                                        val p = toContent(s)
                                        selDragStart = p; selDragEnd = p
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        lastInteractionAtMs = System.currentTimeMillis()
                                        selDragEnd = toContent(change.position)
                                    },
                                    onDragCancel = { selDragStart = null; selDragEnd = null },
                                    onDragEnd = {
                                        val s = selDragStart; val e = selDragEnd
                                        selDragStart = null; selDragEnd = null
                                        if (s == null || e == null || ocrBlocks.isEmpty()) return@detectDragGesturesAfterLongPress
                                        val cs    = pageCanvasSizes[page] ?: return@detectDragGesturesAfterLongPress
                                        val bs    = pageBitmapSizes[page]  ?: return@detectDragGesturesAfterLongPress
                                        val frame = fitBitmapRect(cs, bs.width, bs.height)
                                        val dist  = kotlin.math.hypot((e.x - s.x).toDouble(), (e.y - s.y).toDouble())
                                        if (dist < 12.0) {
                                            hitTestOcrBlock(ocrBlocks, s, frame)
                                                ?.let { viewModel.toggleOcrSelection(page, it.id) }
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        val marquee = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                                        viewModel.selectOcrBlocks(
                                            page,
                                            ocrBlocks.filter { intersects(marquee, ocrBlockToRect(it, frame)) }
                                                     .map { it.id }.toSet(),
                                            append = true
                                        )
                                    }
                                )
                            }
                    )
                }

                // ── Zoom level HUD ────────────────────────────────────────────
                AnimatedVisibility(
                    visible  = showZoomHud,
                    enter    = fadeIn(tween(120)),
                    exit     = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            zoomHudText,
                            style = TextStyle(
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

            } // outer sizing Box
        }

        if (scrollOrientation == ScrollOrientation.Vertical) {
            VerticalPager(
                state          = pagerState,
                userScrollEnabled = pagerScrollEnabled,
                modifier       = Modifier.fillMaxSize(),
                pageContent    = { page -> pageContent(page) }
            )
        } else {
            HorizontalPager(
                state          = pagerState,
                userScrollEnabled = pagerScrollEnabled,
                modifier       = Modifier.fillMaxSize(),
                pageContent    = { page -> pageContent(page) }
            )
        }

        // ── Controls overlay ──────────────────────────────────────────────────
        // ── Page Scrubber Minimap (Tied to Controls Visibility) ───────────────
        AnimatedVisibility(
            visible  = controlsVisible && safePageCount > 1,
            enter    = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                       scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit     = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                       scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(end = 8.dp)
        ) {
            PageScrubber(
                currentPage   = pagerState.currentPage,
                pageCount     = safePageCount,
                pageBitmaps   = state.pageBitmaps,
                backdrop      = backdrop,
                uiSensor      = uiSensor,
                onPageChange  = { page ->
                    pagerScope.launch { pagerState.animateScrollToPage(page) }
                    lastInteractionAtMs = System.currentTimeMillis()
                },
                onPageScrubbing = { page ->
                    viewModel.renderPage(context, page, 400)
                    lastInteractionAtMs = System.currentTimeMillis()
                }
            )
        }

        // ── Controls overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = controlsVisible,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Top bar ───────────────────────────────────────────────────
                Row(
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.Black.copy(0.35f)) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), Color.White)
                    }
                    LiquidGlassTopBar(
                        title         = "Page ${state.currentPage + 1} / ${state.pageCount}",
                        backdrop      = backdrop,
                        uiSensor      = uiSensor,
                        modifier      = Modifier
                            .weight(1f)
                            .clickable { showPageJumpDialog = true },
                        titleFontSize = 12.sp,
                        fontWeight    = FontWeight.Medium
                    )

                    LiquidButton(onClick = {
                        val t = (pagerState.currentPage - 1).coerceAtLeast(0)
                        if (t != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(t) }
                        lastInteractionAtMs = System.currentTimeMillis()
                    }, backdrop = backdrop) { BasicText("Prev", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                    LiquidButton(onClick = {
                        val t = (pagerState.currentPage + 1).coerceAtMost(safePageCount - 1)
                        if (t != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(t) }
                        lastInteractionAtMs = System.currentTimeMillis()
                    }, backdrop = backdrop) { BasicText("Next", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                }

                // ── Bottom controls ───────────────────────────────────────────
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selectedTextCount  = state.selectedOcrBlockIdsByPage[state.currentPage]?.size ?: 0
                    val currentSelectedIds = state.selectedOcrBlockIdsByPage[state.currentPage].orEmpty()
                    val hasEdits           = annotationsByPage.values.any { it.isNotEmpty() }

                    // Primary toolbar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlassPanel(backdrop, uiSensor)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        LiquidButton(
                            onClick  = { activeTool = if (drawingToolActive) PdfEditTool.None else PdfEditTool.Draw },
                            backdrop = backdrop,
                            tint     = if (drawingToolActive) Color(0xFF00BCD4) else Color.Transparent
                        ) { BasicText("Draw Tools", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                        LiquidButton(
                            onClick  = { activeTool = if (activeTool == PdfEditTool.SelectText) PdfEditTool.None else PdfEditTool.SelectText },
                            backdrop = backdrop,
                            tint     = if (activeTool == PdfEditTool.SelectText) Color(0xFFAB47BC) else Color.Transparent
                        ) {
                            BasicText(
                                if (selectedTextCount > 0) "OCR ($selectedTextCount)" else "Select Text",
                                style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                            )
                        }

                        LiquidButton(
                            onClick  = { imagePickerLauncher.launch("image/*") },
                            backdrop = backdrop,
                            tint     = if (activeTool == PdfEditTool.Image) Color(0xFF1976D2) else Color.Transparent
                        ) { BasicText("Add Image", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                        LiquidButton(
                            onClick  = { activeTool = if (activeTool == PdfEditTool.Eraser) PdfEditTool.None else PdfEditTool.Eraser },
                            backdrop = backdrop,
                            tint     = if (activeTool == PdfEditTool.Eraser) Color(0xFFEF5350) else Color.Transparent
                        ) { BasicText("Eraser", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                        if (drawingToolActive || activeTool == PdfEditTool.SelectText ||
                            activeTool == PdfEditTool.Image || activeTool == PdfEditTool.Eraser) {
                            LiquidIconButton(
                                onClick = {
                                    activeTool = PdfEditTool.None
                                    activeImageId = null
                                    viewModel.clearOcrSelection(state.currentPage)
                                },
                                backdrop = backdrop,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(32.dp)
                            ) {
                                CloseCrossIcon(Modifier.size(14.dp), Color.White)
                            }
                        }

                        // Animated reset zoom button
                        if (zoomScale > 1.01f) {
                            LiquidButton(
                                onClick = {
                                    scope.launch {
                                        animateZoomPan(
                                            targetZoom   = 1f,
                                            targetPan    = Offset.Zero,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness    = Spring.StiffnessMedium
                                        )
                                    }
                                    lastInteractionAtMs = System.currentTimeMillis()
                                },
                                backdrop = backdrop
                            ) {
                                BasicText(
                                    "Reset Zoom  ${(zoomScale * 100 + 0.5f).toInt()}%",
                                    style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                                )
                            }
                        }

                        if (hasEdits && !state.isExporting) {
                            LiquidButton(
                                onClick  = { showSaveDialog = true },
                                backdrop = backdrop,
                                tint     = Color(0xFF1976D2)
                            ) { BasicText("Save Edits", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                        }
                    }

                    // Integrated Edit Panel (Drawing / OCR / Image)
                    val showDrawTools = drawingToolActive
                    val showOcrTools = activeTool == PdfEditTool.SelectText || selectedTextCount > 0
                    val showImageTools = activeTool == PdfEditTool.Image && activeImageId != null

                    AnimatedVisibility(visible = showDrawTools || showOcrTools || showImageTools) {
                        Column(
                            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Row: Actions + Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showDrawTools) {
                                        listOf(
                                            PdfEditTool.Draw      to "Pen",
                                            PdfEditTool.Highlight to "Highlight",
                                            PdfEditTool.Rect      to "Rect",
                                            PdfEditTool.Ellipse   to "Oval",
                                            PdfEditTool.Line      to "Line",
                                            PdfEditTool.Arrow     to "Arrow"
                                        ).forEach { (tool, label) ->
                                            LiquidButton(
                                                onClick       = { activeTool = tool },
                                                backdrop      = backdrop,
                                                surfaceColor  = if (activeTool == tool)
                                                                    currentColor.copy(0.2f)
                                                                else Color.White.copy(0.08f)
                                            ) { BasicText(label, style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                        }
                                        LiquidButton(
                                            onClick  = {
                                                getPageMarks(state.currentPage).let { if (it.isNotEmpty()) it.removeAt(it.lastIndex) }
                                            },
                                            backdrop = backdrop
                                        ) { BasicText("Undo", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                        LiquidButton(
                                            onClick  = { getPageMarks(state.currentPage).clear() },
                                            backdrop = backdrop
                                        ) { BasicText("Clear", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                    } else if (showOcrTools) {
                                        LiquidButton(onClick = {
                                            val ids = state.ocrBlocksByPage[state.currentPage].orEmpty().map { it.id }.toSet()
                                            if (ids.isNotEmpty()) viewModel.selectOcrBlocks(state.currentPage, ids, false)
                                        }, backdrop = backdrop) { BasicText("Select All", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                                        LiquidButton(
                                            onClick = {
                                                viewModel.getSelectedOcrText(state.currentPage)
                                                    .takeIf { it.isNotBlank() }
                                                    ?.let { clipboard.setText(AnnotatedString(it)) }
                                            },
                                            backdrop = backdrop, tint = Color(0xFF7E57C2)
                                        ) { BasicText("Copy", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                                        LiquidButton(
                                            onClick = {
                                                val m = getPageMarks(state.currentPage)
                                                currentSelectedIds.forEach { id ->
                                                    if (!m.any { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == id })
                                                        m.add(PdfMarkup.TextBlockHighlightMarkup(id, Color(currentColorLong), 0.30f))
                                                }
                                            },
                                            backdrop = backdrop, tint = Color(0xFFFFB300)
                                        ) { BasicText("Highlight", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                                        LiquidButton(
                                            onClick = {
                                                val m = getPageMarks(state.currentPage)
                                                currentSelectedIds.forEach { id ->
                                                    if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == id && !it.strikeThrough })
                                                        m.add(PdfMarkup.TextBlockLineMarkup(id, Color(currentColorLong), 3f, 1f, false))
                                                }
                                            },
                                            backdrop = backdrop, tint = Color(0xFF4CAF50)
                                        ) { BasicText("Underline", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                                        LiquidButton(
                                            onClick = {
                                                val m = getPageMarks(state.currentPage)
                                                currentSelectedIds.forEach { id ->
                                                    if (!m.any { it is PdfMarkup.TextBlockLineMarkup && it.blockId == id && it.strikeThrough })
                                                        m.add(PdfMarkup.TextBlockLineMarkup(id, Color(currentColorLong), 3f, 1f, true))
                                                }
                                            },
                                            backdrop = backdrop, tint = Color(0xFFEF5350)
                                        ) { BasicText("Strikeout", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                                        LiquidButton(
                                            onClick  = { viewModel.clearOcrSelection(state.currentPage) },
                                            backdrop = backdrop
                                        ) { BasicText("Clear Sel", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                    } else if (showImageTools) {
                                        BasicText(
                                            "Drag to move · drag corner to resize",
                                            style = TextStyle(Color.White.copy(0.75f), 12.sp)
                                        )
                                        LiquidButton(
                                            onClick = { imagePickerLauncher.launch("image/*") },
                                            backdrop = backdrop
                                        ) { BasicText("Replace", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                        LiquidButton(
                                            onClick = {
                                                val m = getPageMarks(state.currentPage)
                                                val idx = m.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                                                if (idx >= 0) m.removeAt(idx)
                                                activeImageId = null
                                                activeTool = PdfEditTool.None
                                            },
                                            backdrop = backdrop, tint = Color(0xFFEF5350)
                                        ) { BasicText("Remove", style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                    }
                                }
                            }

                            // Thickness selector (draw tools only)
                            if (showDrawTools) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText("Size", style = TextStyle(Color.White.copy(0.7f), 12.sp))
                                    listOf("S" to 3f, "M" to 6f, "L" to 11f, "XL" to 18f).forEach { (label, w) ->
                                        val sel = currentStrokeWidth == w
                                        LiquidButton(
                                            onClick = { currentStrokeWidth = w },
                                            backdrop = backdrop,
                                            surfaceColor = if (sel) currentColor.copy(0.30f) else Color.White.copy(0.08f)
                                        ) { BasicText(label, style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }
                                    }
                                }
                            }

                            // Bottom Row: Color Picker
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                listOf(
                                    0xFF00BCD4L, 0xFFFFB300L, 0xFF4CAF50L, 0xFFEF5350L,
                                    0xFF42A5F5L, 0xFFAB47BCL, 0xFF26A69AL, 0xFFE0E0E0L
                                ).forEach { cl ->
                                    val sel = currentColorLong == cl
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(cl))
                                            .clickable { currentColorLong = cl }
                                            .border(if (sel) 2.dp else 0.dp,
                                                    if (sel) Color.White else Color.Transparent,
                                                    CircleShape)
                                    ) {
                                        if (sel) Box(
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Export feedback
                    if (state.exportError != null || state.exportMessage != null || state.isExporting) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassPanel(backdrop, uiSensor)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            when {
                                state.isExporting -> BasicText(
                                    "Saving edited PDF…",
                                    style = TextStyle(Color.White.copy(0.82f), 12.sp)
                                )
                                state.exportError != null -> {
                                    BasicText(state.exportError!!, style = TextStyle(Color(0xFFFF8A80), 12.sp))
                                    LiquidButton(
                                        onClick  = { viewModel.clearExportFeedback() },
                                        backdrop = backdrop
                                    ) { BasicText("Dismiss", style = TextStyle(Color.White, 11.sp, FontWeight.Medium)) }
                                }
                                state.exportMessage != null -> {
                                    BasicText(state.exportMessage!!, style = TextStyle(Color(0xFFB9F6CA), 12.sp))
                                    state.lastExportedUri?.let { uri ->
                                        LiquidButton(
                                            onClick  = { viewModel.openPdf(context, uri) },
                                            backdrop = backdrop,
                                            tint     = Color(0xFF1976D2)
                                        ) { BasicText("Open", style = TextStyle(Color.White, 11.sp, FontWeight.Medium)) }
                                    }
                                }
                            }
                        }
                    }

                    LiquidButton(
                        onClick   = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        backdrop  = backdrop,
                        tint      = accent,
                        modifier  = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(16.dp), Color.White)
                            BasicText("Open Another PDF", style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        } // AnimatedVisibility controls
    } // BoxWithConstraints

    if (showSaveDialog) {
        LiquidSaveDialog(
            initialFileName = state.document?.name
                ?.substringBeforeLast('.')
                ?.let { "${it}_Edited" } ?: "Document",
            backdrop        = backdrop,
            uiSensor        = uiSensor,
            onDismiss       = { showSaveDialog = false },
            onSave          = { fileName, overrideUri ->
                showSaveDialog = false
                val overlays = buildExportOverlays(
                    annotationsByPage, state.ocrBlocksByPage, pageCanvasSizes, pageBitmapSizes
                )
                if (overlays.isNotEmpty())
                    viewModel.exportEditedPdf(context, overlays, fileName, overrideUri)
            }
        )
    }

    if (showPageJumpDialog) {
        LiquidPageJumpDialog(
            currentPage = pagerState.currentPage,
            pageCount = safePageCount,
            backdrop = backdrop,
            uiSensor = uiSensor,
            onDismiss = { showPageJumpDialog = false },
            onJumpToPage = { targetPage ->
                showPageJumpDialog = false
                pagerScope.launch { pagerState.animateScrollToPage(targetPage) }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Enums & sealed classes
// ─────────────────────────────────────────────────────────────────────────────

private enum class ScrollOrientation { Vertical, Horizontal }

private enum class PdfEditTool { None, Draw, Highlight, Rect, Ellipse, Line, Arrow, SelectText, Image, Eraser }

/**
 * Floating vertical liquid-glass page scrubber minimap with Live PDF Page Preview Window.
 * Features auto-hiding track, physics-based 120Hz spring motion, and a bouncy jelly glass preview window.
 */
@Composable
private fun PageScrubber(
    currentPage: Int,
    pageCount: Int,
    pageBitmaps: List<android.graphics.Bitmap?>,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    onPageChange: (Int) -> Unit,
    onPageScrubbing: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode = LocalIsDarkMode.current
    val accent = Color(0xFF0088FF)

    var isDragging by remember { mutableStateOf(false) }
    var isHoveredOrActive by remember { mutableStateOf(false) }
    var dragPage by remember { mutableIntStateOf(currentPage) }
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide controller
    LaunchedEffect(currentPage) {
        if (!isDragging) {
            dragPage = currentPage
            isHoveredOrActive = true
            lastTouchTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(lastTouchTime, isDragging) {
        if (!isDragging) {
            delay(2200)
            isHoveredOrActive = false
        }
    }

    LaunchedEffect(dragPage, isDragging) {
        if (isDragging) {
            onPageScrubbing(dragPage)
        }
    }

    val targetFraction = (currentPage.toFloat() / (pageCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) (dragPage.toFloat() / (pageCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f) else targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "thumbFraction"
    )

    val scrubberHeightDp = 280.dp

    // Track width expansion on drag
    val trackWidthDp by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isDragging) 12.dp else 6.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "trackWidth"
    )

    Box(
        modifier = modifier.padding(end = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Scrubber Touch & Track Area
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(scrubberHeightDp)
                .pointerInput(pageCount) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            isHoveredOrActive = true
                            val totalPx = size.height.toFloat()
                            val frac = (offset.y / totalPx).coerceIn(0f, 1f)
                            val target = (frac * (pageCount - 1)).roundToInt()
                            dragPage = target
                            onPageScrubbing(target)
                            onPageChange(target)
                        },
                        onDragEnd = {
                            isDragging = false
                            lastTouchTime = System.currentTimeMillis()
                            onPageChange(dragPage)
                        },
                        onDragCancel = {
                            isDragging = false
                            lastTouchTime = System.currentTimeMillis()
                        },
                        onDrag = { change, _ ->
                            isHoveredOrActive = true
                            val totalPx = size.height.toFloat()
                            val frac = (change.position.y / totalPx).coerceIn(0f, 1f)
                            val target = (frac * (pageCount - 1)).roundToInt()
                            if (target != dragPage) {
                                dragPage = target
                                onPageScrubbing(target)
                                onPageChange(target)
                            }
                        }
                    )
                }
                .pointerInput(pageCount) {
                    detectTapGestures { offset ->
                        isHoveredOrActive = true
                        lastTouchTime = System.currentTimeMillis()
                        val totalPx = size.height.toFloat()
                        val frac = (offset.y / totalPx).coerceIn(0f, 1f)
                        val target = (frac * (pageCount - 1)).roundToInt()
                        dragPage = target
                        onPageScrubbing(target)
                        onPageChange(target)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Thin Glass Pillar Track
            Box(
                modifier = Modifier
                    .width(trackWidthDp)
                    .height(scrubberHeightDp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.08f)),
                contentAlignment = Alignment.TopCenter
            ) {
                // Glow Thumb Pill Handle
                Box(
                    Modifier
                        .padding(top = (scrubberHeightDp * animatedFraction - 16.dp).coerceIn(0.dp, scrubberHeightDp - 32.dp))
                        .size(width = if (isDragging) 16.dp else 10.dp, height = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDragging) accent else Color.White.copy(alpha = 0.95f))
                        .border(
                            width = 1.dp,
                            color = if (isDragging) Color.White.copy(0.6f) else Color.Black.copy(0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
        }

        // Animated Floating Live Page Minimap Window
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                   scaleOut(targetScale = 0.9f)
        ) {
            val previewBitmap = pageBitmaps.getOrNull(dragPage)
            val yOffsetDp = (scrubberHeightDp * animatedFraction - scrubberHeightDp / 2).coerceIn(-115.dp, 115.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-46).dp, y = yOffsetDp)
                    .width(145.dp)
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Liquid Glass Header Pill
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(0.22f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    BasicText(
                        "${dragPage + 1} / $pageCount",
                        style = TextStyle(Color.White, 12.sp, FontWeight.Bold)
                    )
                }

                // Mini PDF Page Bitmap Paper Card
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkMode) Color(0xFF1E1E1E) else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isDarkMode) Color.White.copy(0.12f) else Color.Black.copy(0.08f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null && !previewBitmap.isRecycled) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Page ${dragPage + 1} Minimap Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                accent,
                                strokeWidth = 2.5.dp
                            )
                            BasicText(
                                "Rendering...",
                                style = TextStyle(
                                    if (isDarkMode) Color.LightGray else Color.DarkGray,
                                    11.sp,
                                    FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidPageJumpDialog(
    currentPage: Int,
    pageCount: Int,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var targetPage by remember { mutableIntStateOf(currentPage) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.FormatListNumbered, null, Modifier.size(36.dp), Color(0xFF1976D2))
            BasicText(
                "Jump to Page",
                style = TextStyle(Color.White, 18.sp, FontWeight.SemiBold)
            )
            BasicText(
                "Page ${targetPage + 1} of $pageCount",
                style = TextStyle(Color.White.copy(0.7f), 14.sp, FontWeight.Medium)
            )

            if (pageCount > 1) {
                com.chethan616.clearpdf.ui.components.LiquidSlider(
                    value = { targetPage.toFloat() },
                    onValueChange = { targetPage = it.toInt().coerceIn(0, pageCount - 1) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    visibilityThreshold = 0.5f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(-5, -1, 1, 5).forEach { step ->
                    LiquidButton(
                        onClick = {
                            targetPage = (targetPage + step).coerceIn(0, pageCount - 1)
                        },
                        backdrop = backdrop
                    ) {
                        BasicText(
                            if (step > 0) "+$step" else "$step",
                            style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                LiquidButton(onClick = onDismiss, backdrop = backdrop) {
                    BasicText("Cancel", style = TextStyle(Color.White.copy(0.7f), 13.sp, FontWeight.Medium))
                }
                LiquidButton(
                    onClick = { onJumpToPage(targetPage) },
                    backdrop = backdrop,
                    tint = Color(0xFF1976D2)
                ) {
                    BasicText("Go to Page", style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

private sealed class PdfMarkup {
    data class StrokeMarkup(val points: List<Offset>, val color: Color, val width: Float, val alpha: Float) : PdfMarkup()
    data class RectMarkup(val start: Offset, val end: Offset, val color: Color, val alpha: Float, val filled: Boolean) : PdfMarkup()
    data class OvalMarkup(val start: Offset, val end: Offset, val color: Color, val alpha: Float, val filled: Boolean) : PdfMarkup()
    data class LineMarkup(val start: Offset, val end: Offset, val color: Color, val width: Float, val alpha: Float, val arrowHead: Boolean) : PdfMarkup()
    data class TextBlockHighlightMarkup(val blockId: String, val color: Color, val alpha: Float) : PdfMarkup()
    data class TextBlockLineMarkup(val blockId: String, val color: Color, val width: Float, val alpha: Float, val strikeThrough: Boolean) : PdfMarkup()
    data class ImageMarkup(val id: Long, val bitmap: android.graphics.Bitmap, val start: Offset, val end: Offset) : PdfMarkup()
}

/** A markup is "hittable" for the eraser if a point falls on/near it. */
private fun PdfMarkup.hitTest(p: Offset): Boolean = when (this) {
    is PdfMarkup.StrokeMarkup -> points.any { (it - p).getDistance() <= (width / 2f + 14f) }
    is PdfMarkup.RectMarkup -> nearRectEdgeOrInside(p, start, end, filled)
    is PdfMarkup.OvalMarkup -> nearRectEdgeOrInside(p, start, end, filled)
    is PdfMarkup.LineMarkup -> distanceToSegment(p, start, end) <= (width / 2f + 14f)
    is PdfMarkup.ImageMarkup -> {
        val r = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
        p.x in r.left..r.right && p.y in r.top..r.bottom
    }
    else -> false
}

private fun nearRectEdgeOrInside(p: Offset, a: Offset, b: Offset, filled: Boolean): Boolean {
    val r = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    if (filled) return p.x in r.left..r.right && p.y in r.top..r.bottom
    val pad = 14f
    val inOuter = p.x in (r.left - pad)..(r.right + pad) && p.y in (r.top - pad)..(r.bottom + pad)
    val inInner = p.x in (r.left + pad)..(r.right - pad) && p.y in (r.top + pad)..(r.bottom - pad)
    return inOuter && !inInner
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lenSq = ab.x * ab.x + ab.y * ab.y
    if (lenSq <= 0.0001f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lenSq).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometry helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a smooth path from raw points using quadratic midpoint smoothing. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size < 3) {
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        return path
    }
    for (i in 1 until points.size - 1) {
        val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
        path.quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun fitBitmapRect(container: Size, bitmapWidth: Float, bitmapHeight: Float): Rect {
    if (bitmapWidth <= 0f || bitmapHeight <= 0f || container.width <= 0f || container.height <= 0f)
        return Rect.Zero
    val scale = min(container.width / bitmapWidth, container.height / bitmapHeight)
    val dw = bitmapWidth * scale;  val dh = bitmapHeight * scale
    val l  = (container.width  - dw) / 2f
    val t  = (container.height - dh) / 2f
    return Rect(l, t, l + dw, t + dh)
}

/**
 * Clamps [pan] so the scaled content always covers (or is centred in) the container.
 *
 * With graphicsLayer the scale pivot is the box centre, so the allowed pan range is:
 *   max horizontal travel = max(0, (scaledWidth  - containerWidth)  / 2)
 *   max vertical   travel = max(0, (scaledHeight - containerHeight) / 2)
 */
private fun clampPanOffset(pan: Offset, scale: Float, containerSize: Size, bitmapSize: Size): Offset {
    if (scale <= 1.01f || containerSize.width <= 0f || containerSize.height <= 0f
        || bitmapSize.width <= 0f || bitmapSize.height <= 0f) return Offset.Zero

    val base = fitBitmapRect(containerSize, bitmapSize.width, bitmapSize.height)
    val mx   = ((base.width  * scale - containerSize.width)  / 2f).coerceAtLeast(0f)
    val my   = ((base.height * scale - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my))
}

private fun ocrBlockToRect(block: OcrTextBlock, imageRect: Rect): Rect = Rect(
    imageRect.left + block.left   * imageRect.width,
    imageRect.top  + block.top    * imageRect.height,
    imageRect.left + block.right  * imageRect.width,
    imageRect.top  + block.bottom * imageRect.height
)

private fun hitTestOcrBlock(blocks: List<OcrTextBlock>, tap: Offset, imageRect: Rect): OcrTextBlock? =
    blocks.firstOrNull { b ->
        val r = ocrBlockToRect(b, imageRect)
        tap.x in r.left..r.right && tap.y in r.top..r.bottom
    }

private fun intersects(a: Rect, b: Rect) =
    a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

private fun buildExportOverlays(
    annotationsByPage: Map<Int, List<PdfMarkup>>,
    ocrBlocksByPage:   Map<Int, List<OcrTextBlock>>,
    canvasSizesByPage: Map<Int, Size>,
    bitmapSizesByPage: Map<Int, Size>
): Map<Int, List<ExportOverlay>> {
    val output = mutableMapOf<Int, List<ExportOverlay>>()
    annotationsByPage.forEach { (page, marks) ->
        val cs = canvasSizesByPage[page] ?: return@forEach
        val bs = bitmapSizesByPage[page]  ?: return@forEach
        val ir = fitBitmapRect(cs, bs.width, bs.height)
        if (ir.width <= 0f || ir.height <= 0f) return@forEach
        val strokeBase = min(ir.width, ir.height).coerceAtLeast(1f)
        val ocrBlocks  = ocrBlocksByPage[page].orEmpty()
        val overlays   = mutableListOf<ExportOverlay>()
        marks.forEach { markup ->
            when (markup) {
                is PdfMarkup.StrokeMarkup -> {
                    val pts = markup.points.mapNotNull { screenToNormalized(it, ir) }
                    if (pts.size > 1)
                        overlays.add(ExportOverlay.Stroke(pts, markup.color.toArgb(),
                            (markup.width / strokeBase).coerceAtLeast(0.0008f), markup.alpha))
                }
                is PdfMarkup.RectMarkup -> {
                    val s = screenToNormalized(markup.start, ir)
                    val e = screenToNormalized(markup.end,   ir)
                    if (s != null && e != null)
                        overlays.add(ExportOverlay.RectShape(s, e, markup.color.toArgb(), markup.alpha, markup.filled))
                }
                is PdfMarkup.OvalMarkup -> {
                    val s = screenToNormalized(markup.start, ir)
                    val e = screenToNormalized(markup.end,   ir)
                    if (s != null && e != null)
                        overlays.add(ExportOverlay.OvalShape(s, e, markup.color.toArgb(), markup.alpha, markup.filled))
                }
                is PdfMarkup.LineMarkup -> {
                    val s = screenToNormalized(markup.start, ir)
                    val e = screenToNormalized(markup.end,   ir)
                    if (s != null && e != null)
                        overlays.add(ExportOverlay.LineShape(s, e, markup.color.toArgb(),
                            (markup.width / strokeBase).coerceAtLeast(0.0008f), markup.alpha, markup.arrowHead))
                }
                is PdfMarkup.TextBlockHighlightMarkup -> {
                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        overlays.add(ExportOverlay.RectShape(
                            NormalizedPoint(b.left, b.top), NormalizedPoint(b.right, b.bottom),
                            markup.color.toArgb(), markup.alpha, true
                        ))
                    }
                }
                is PdfMarkup.TextBlockLineMarkup -> {
                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val y = if (markup.strikeThrough) (b.top + b.bottom) / 2f
                                else b.bottom - (b.bottom - b.top) * 0.10f
                        overlays.add(ExportOverlay.LineShape(
                            NormalizedPoint(b.left, y), NormalizedPoint(b.right, y),
                            markup.color.toArgb(),
                            (markup.width / strokeBase).coerceAtLeast(0.0012f),
                            markup.alpha, false
                        ))
                    }
                }
                is PdfMarkup.ImageMarkup -> {
                    val s = screenToNormalized(markup.start, ir)
                    val e = screenToNormalized(markup.end,   ir)
                    if (s != null && e != null)
                        overlays.add(ExportOverlay.ImageStamp(markup.bitmap, s, e))
                }
            }
        }
        if (overlays.isNotEmpty()) output[page] = overlays
    }
    return output
}

/**
 * Maps a screen-space pointer position into the un-zoomed content coordinate space.
 *
 * The content layer is transformed by graphicsLayer as:
 *   screen = (P - center) * scale + center + pan
 * so the inverse used here is:
 *   P = (screen - center - pan) / scale + center
 *
 * This keeps annotations/OCR hit-testing aligned with the page at any zoom level.
 */
private fun screenToContent(screen: Offset, scale: Float, pan: Offset, center: Offset): Offset =
    if (scale <= 0f) screen else (screen - center - pan) / scale + center

private fun screenToNormalized(point: Offset, imageRect: Rect): NormalizedPoint? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    return NormalizedPoint(
        ((point.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((point.y - imageRect.top)  / imageRect.height).coerceIn(0f, 1f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Draw helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(
    start: Offset, end: Offset, color: Color, strokeWidth: Float
) {
    drawLine(color, start, end, strokeWidth)
    val angle   = atan2(end.y - start.y, end.x - start.x)
    val headLen = 20f
    val theta   = (28f * PI / 180f).toFloat()
    drawLine(color, end,
        Offset(end.x - (headLen * cos(angle - theta)).toFloat(), end.y - (headLen * sin(angle - theta)).toFloat()),
        strokeWidth)
    drawLine(color, end,
        Offset(end.x - (headLen * cos(angle + theta)).toFloat(), end.y - (headLen * sin(angle + theta)).toFloat()),
        strokeWidth)
}

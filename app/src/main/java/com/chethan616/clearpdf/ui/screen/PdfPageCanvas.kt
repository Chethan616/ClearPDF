package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.viewmodel.FindMatch
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val MIN_ZOOM             = 1.0f
private const val MAX_ZOOM             = 8.0f
private const val ZOOM_SLOP            = 0.01f
private const val PAN_SLOP_PX          = 8f
private const val SWIPE_ANGLE_DEG      = 38f
private const val SNAP_BACK_THRESHOLD  = 1.08f
private const val FLING_FRICTION       = 0.92f
private const val FLING_MIN_VELOCITY   = 50f
private const val DOUBLE_TAP_ZOOM_1    = 2.5f
private const val DOUBLE_TAP_ZOOM_2    = 4.5f

@Composable
internal fun PdfPageCanvas(
    page: Int,
    bitmap: Bitmap,
    marks: MutableList<PdfMarkup>,
    ocrBlocks: List<OcrTextBlock>,
    selectedOcrIds: Set<String>,
    findMatches: List<FindMatch>,
    currentMatchIndex: Int,
    showFindBar: Boolean,
    activeTool: PdfEditTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    zoomAnim: Animatable<Float, AnimationVector1D>,
    panXAnim: Animatable<Float, AnimationVector1D>,
    panYAnim: Animatable<Float, AnimationVector1D>,
    scrollOrientation: ScrollOrientation,
    activeImageId: Long?,
    pageCanvasSizes: SnapshotStateMap<Int, Size>,
    pageBitmapSizes: SnapshotStateMap<Int, Size>,
    onInteraction: () -> Unit,
    onToggleControls: () -> Unit,
    onShowControls: () -> Unit,
    onActiveToolChanged: (PdfEditTool) -> Unit,
    onActiveImageIdChanged: (Long?) -> Unit,
    onToggleOcrSelection: (String) -> Unit,
    onClearOcrSelection: () -> Unit,
    onSelectLine: (String) -> Unit,
    onSelectParagraph: (String) -> Unit,
    onSelectOcrRange: (Set<String>) -> Unit,
    onPlaceText: (Offset) -> Unit,
    onPlaceNote: (Offset) -> Unit,
    onEditAnnotation: (Long) -> Unit,
    showZoomHud: Boolean,
    zoomHudText: String
) {
    val scope = rememberCoroutineScope()

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

    suspend fun snapZoomPan(targetZoom: Float, targetPan: Offset) {
        coroutineScope {
            launch { zoomAnim.snapTo(targetZoom) }
            launch { panXAnim.snapTo(targetPan.x) }
            launch { panYAnim.snapTo(targetPan.y) }
        }
    }

    val zoomScale: Float  = zoomAnim.value
    val panOffset: Offset = Offset(panXAnim.value, panYAnim.value)

    pageBitmapSizes[page] = Size(bitmap.width.toFloat(), bitmap.height.toFloat())

    val drawingToolActive = activeTool in setOf(
        PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect,
        PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow
    )

    var draftPoints    by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var draftRectEnd   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragStart   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragEnd     by remember(page, activeTool) { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                pageCanvasSizes[page] = Size(sz.width.toFloat(), sz.height.toFloat())
            }
    ) {
        // ── Pan / zoom / tap gesture handler ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                } else if (isOurGesture && currentZoom > 1.02f) {
                                    val vel = velocityTracker.calculateVelocity()
                                    val cs  = pageCanvasSizes[page] ?: Size.Zero
                                    val bs  = pageBitmapSizes[page]  ?: Size.Zero
                                    scope.launch {
                                        var vx = vel.x * 0.016f
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
                                    val boosted   = if (zoomDelta > 1f)
                                        zoomDelta.toDouble().pow(1.18).toFloat().coerceIn(1f, 1.30f)
                                    else
                                        zoomDelta.coerceIn(0.78f, 1f)
                                    val newScale  = (oldScale * boosted).coerceIn(MIN_ZOOM, MAX_ZOOM)

                                    val ratio      = newScale / oldScale
                                    val currentPan = Offset(panXAnim.value, panYAnim.value)
                                    val focalPan   = (centroid - boxCenter) * (1f - ratio) + currentPan * ratio
                                    val clampedPan = clampPanOffset(focalPan + panDelta * ratio, newScale, cs, bs)

                                    scope.launch { snapZoomPan(newScale, clampedPan) }
                                    onInteraction()
                                } else if (pressed.size >= 2 && panDelta != Offset.Zero) {
                                    val cs2 = pageCanvasSizes[page] ?: Size.Zero
                                    val bs2 = pageBitmapSizes[page]  ?: Size.Zero
                                    val newPan = clampPanOffset(
                                        Offset(panXAnim.value + panDelta.x, panYAnim.value + panDelta.y),
                                        zoomAnim.value, cs2, bs2
                                    )
                                    scope.launch { snapZoomPan(zoomAnim.value, newPan) }
                                    onInteraction()
                                }

                                event.changes.forEach { it.consume() }
                                continue@loop
                            }

                            val change = pressed.first()
                            val delta  = change.position - change.previousPosition

                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (!determined) {
                                panAccum += delta
                                val dist = panAccum.getDistance()
                                if (dist > PAN_SLOP_PX) {
                                    determined = true
                                    val angleDeg = Math.toDegrees(
                                        abs(atan2(panAccum.y.toDouble(), panAccum.x.toDouble()))
                                    ).toFloat()
                                    val isHoriz = angleDeg <= SWIPE_ANGLE_DEG || angleDeg >= (180f - SWIPE_ANGLE_DEG)
                                    val isSwipeForPager = if (scrollOrientation == ScrollOrientation.Horizontal) isHoriz else !isHoriz

                                    if (isSwipeForPager && zoomAnim.value <= 1.02f) {
                                        isOurGesture = false
                                        break@loop
                                    }
                                    isOurGesture = true
                                }
                            } else if (isOurGesture && zoomAnim.value > 1.02f) {
                                val cs = pageCanvasSizes[page] ?: Size.Zero
                                val bs = pageBitmapSizes[page]  ?: Size.Zero
                                val newPan = clampPanOffset(
                                    Offset(panXAnim.value + delta.x, panYAnim.value + delta.y),
                                    zoomAnim.value, cs, bs
                                )
                                scope.launch { snapZoomPan(zoomAnim.value, newPan) }
                                change.consume()
                                onInteraction()
                            }
                        }
                    }
                }
                .pointerInput(page, activeTool) {
                    detectTapGestures(
                        onTap = { tap ->
                            val cs = pageCanvasSizes[page] ?: Size.Zero
                            val bc = Offset(cs.width / 2f, cs.height / 2f)
                            val p  = screenToContent(tap, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                            when {
                                activeTool == PdfEditTool.Text -> onPlaceText(p)
                                activeTool == PdfEditTool.Note -> onPlaceNote(p)
                                activeTool == PdfEditTool.Image && activeImageId != null -> {
                                    val tappedMark = marks.lastOrNull { it is PdfMarkup.ImageMarkup && it.hitTest(p) } as? PdfMarkup.ImageMarkup
                                    if (tappedMark != null) {
                                        onActiveImageIdChanged(tappedMark.id)
                                        onActiveToolChanged(PdfEditTool.Image)
                                        onShowControls()
                                    } else {
                                        onActiveImageIdChanged(null)
                                        onActiveToolChanged(PdfEditTool.None)
                                        onToggleControls()
                                    }
                                }
                                activeTool == PdfEditTool.None -> {
                                    val tappedImg  = marks.lastOrNull { it is PdfMarkup.ImageMarkup && it.hitTest(p) } as? PdfMarkup.ImageMarkup
                                    val tappedAnno = marks.lastOrNull { (it is PdfMarkup.TextBoxMarkup || it is PdfMarkup.NoteMarkup) && it.hitTest(p) }
                                    when {
                                        tappedImg != null -> {
                                            onActiveImageIdChanged(tappedImg.id)
                                            onActiveToolChanged(PdfEditTool.Image)
                                            onShowControls()
                                        }
                                        tappedAnno is PdfMarkup.TextBoxMarkup -> onEditAnnotation(tappedAnno.id)
                                        tappedAnno is PdfMarkup.NoteMarkup     -> onEditAnnotation(tappedAnno.id)
                                        else -> onToggleControls()
                                    }
                                }
                                else -> onToggleControls()
                            }
                            onInteraction()
                        },
                        onDoubleTap = { tap ->
                            if (activeTool != PdfEditTool.None) return@detectTapGestures
                            onInteraction()

                            val cs        = pageCanvasSizes[page] ?: return@detectTapGestures
                            val bs        = pageBitmapSizes[page]  ?: return@detectTapGestures
                            val boxCenter = Offset(cs.width / 2f, cs.height / 2f)

                            val currentZoom = zoomAnim.value
                            val targetZoom = when {
                                currentZoom < 1.5f -> DOUBLE_TAP_ZOOM_1
                                currentZoom < 3.5f -> DOUBLE_TAP_ZOOM_2
                                else               -> 1f
                            }

                            val targetPan = if (targetZoom <= 1.01f) {
                                Offset.Zero
                            } else {
                                val ratio      = targetZoom / currentZoom
                                val currentPan = Offset(panXAnim.value, panYAnim.value)
                                val focalPan   = (tap - boxCenter) * (1f - ratio) + currentPan * ratio
                                clampPanOffset(focalPan, targetZoom, cs, bs)
                            }

                            scope.launch {
                                animateZoomPan(
                                    targetZoom   = targetZoom,
                                    targetPan    = targetPan,
                                    dampingRatio = if (targetZoom < currentZoom) Spring.DampingRatioMediumBouncy else Spring.DampingRatioLowBouncy,
                                    stiffness    = Spring.StiffnessMediumLow
                                )
                            }
                        }
                    )
                }
        ) {
            // ── Bitmap + annotation canvas ───────────────────────────────────
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
                    contentDescription = null,
                    contentScale    = ContentScale.Fit,
                    modifier        = Modifier.fillMaxSize()
                )

                val cs = pageCanvasSizes[page] ?: Size.Zero
                val bs = pageBitmapSizes[page]  ?: Size.Zero

                Canvas(Modifier.fillMaxSize()) {
                    val ir = fitBitmapRect(cs, bs.width, bs.height)

                    // Draw OCR selection highlights
                    selectedOcrIds.forEach { id ->
                        ocrBlocks.firstOrNull { it.id == id }?.let { b ->
                            val r = ocrBlockToRect(b, ir)
                            drawRect(Color(0xFF42A5F5).copy(0.28f), r.topLeft, r.size)
                            drawRect(Color(0xFF1976D2), r.topLeft, r.size, style = Stroke(1.5f))
                        }
                    }

                    // Draw saved marks
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
                                val r = Rect(min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y), max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y))
                                if (markup.filled) drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size)
                                else drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size, style = Stroke(3f))
                            }
                            is PdfMarkup.OvalMarkup -> {
                                val r = Rect(min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y), max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y))
                                if (markup.filled) drawOval(markup.color.copy(markup.alpha), r.topLeft, r.size)
                                else drawOval(markup.color.copy(markup.alpha), r.topLeft, r.size, style = Stroke(3f))
                            }
                            is PdfMarkup.LineMarkup -> {
                                if (markup.arrowHead) drawArrow(markup.start, markup.end, markup.color.copy(markup.alpha), markup.width)
                                else drawLine(markup.color.copy(markup.alpha), markup.start, markup.end, markup.width)
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
                                val r = Rect(min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y), max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y))
                                if (!markup.bitmap.isRecycled && markup.bitmap.width > 0 && markup.bitmap.height > 0) {
                                    runCatching {
                                        drawImage(
                                            image     = markup.bitmap.asImageBitmap(),
                                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                            srcSize   = androidx.compose.ui.unit.IntSize(markup.bitmap.width, markup.bitmap.height),
                                            dstOffset = androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()),
                                            dstSize   = androidx.compose.ui.unit.IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1))
                                        )
                                    }
                                }
                                if (activeTool == PdfEditTool.Image && markup.id == activeImageId) {
                                    drawRect(Color(0xFF1976D2), r.topLeft, r.size, style = Stroke(2f))
                                    drawCircle(Color(0xFF1976D2), 14f, Offset(r.right, r.bottom))
                                    drawCircle(Color.White, 7f, Offset(r.right, r.bottom))
                                }
                            }
                            is PdfMarkup.TextBoxMarkup -> {
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = markup.color.toArgb()
                                    textSize = markup.fontSize
                                }
                                val linesT = if (markup.text.isEmpty()) listOf("") else markup.text.split("\n")
                                drawIntoCanvas { canvas ->
                                    var yy = markup.position.y + markup.fontSize
                                    linesT.forEach { ln ->
                                        canvas.nativeCanvas.drawText(ln, markup.position.x, yy, paint)
                                        yy += markup.fontSize * 1.2f
                                    }
                                }
                                if (markup.text.isEmpty()) {
                                    drawRect(
                                        Color(0xFF1976D2).copy(0.5f),
                                        Offset(markup.position.x - 4f, markup.position.y - 4f),
                                        Size(markup.fontSize * 5f, markup.fontSize * 1.4f),
                                        style = Stroke(2f)
                                    )
                                }
                            }
                            is PdfMarkup.NoteMarkup -> {
                                val sz = 30f
                                val tl = markup.anchor
                                drawRoundRect(markup.color, tl, Size(sz, sz), CornerRadius(6f, 6f))
                                val fold = Path().apply {
                                    moveTo(tl.x + sz * 0.62f, tl.y)
                                    lineTo(tl.x + sz, tl.y + sz * 0.38f)
                                    lineTo(tl.x + sz * 0.62f, tl.y + sz * 0.38f)
                                    close()
                                }
                                drawPath(fold, Color.White.copy(0.55f))
                                val lc = Color.White.copy(0.75f)
                                drawLine(lc, Offset(tl.x + 6f, tl.y + sz * 0.56f), Offset(tl.x + sz - 6f, tl.y + sz * 0.56f), 2f)
                                drawLine(lc, Offset(tl.x + 6f, tl.y + sz * 0.74f), Offset(tl.x + sz - 9f, tl.y + sz * 0.74f), 2f)
                            }
                        }
                    }

                    // Draw in-progress draft
                    if (draftPoints.size > 1) {
                        val path = smoothPath(draftPoints)
                        val isHl = activeTool == PdfEditTool.Highlight
                        drawPath(
                            path,
                            currentColor.copy(if (isHl) 0.32f else 0.95f),
                            style = Stroke(if (isHl) currentStrokeWidth * 3.5f else currentStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    if (draftRectStart != null && draftRectEnd != null) {
                        val s = draftRectStart!!; val e = draftRectEnd!!
                        val pr = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                        when (activeTool) {
                            PdfEditTool.Rect    -> drawRect(Color(0xFF42A5F5), pr.topLeft, pr.size, style = Stroke(3f))
                            PdfEditTool.Ellipse -> drawOval(Color(0xFF26A69A), pr.topLeft, pr.size, style = Stroke(3f))
                            PdfEditTool.Line    -> drawLine(Color(0xFF66BB6A), s, e, 4f)
                            PdfEditTool.Arrow   -> drawArrow(s, e, Color(0xFFEF5350), 4f)
                            else -> Unit
                        }
                    }

                    // Drag-select rubber band
                    if (selDragStart != null && selDragEnd != null && activeTool == PdfEditTool.SelectText) {
                        val s = selDragStart!!; val e = selDragEnd!!
                        val r = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                        drawRect(Color(0xFFAB47BC).copy(0.20f), r.topLeft, r.size)
                        drawRect(Color(0xFFAB47BC), r.topLeft, r.size, style = Stroke(2f))
                    }

                    // Find highlights
                    if (showFindBar && findMatches.isNotEmpty()) {
                        val pageMatches = findMatches.filter { it.pageIndex == page }
                        if (cs != Size.Zero && bs != Size.Zero && ocrBlocks.isNotEmpty()) {
                            val frame = fitBitmapRect(cs, bs.width, bs.height)
                            pageMatches.forEach { match ->
                                val block = ocrBlocks.firstOrNull { it.id == match.blockId }
                                if (block != null) {
                                    val r = Rect(
                                        frame.left + block.left * frame.width,
                                        frame.top + block.top * frame.height,
                                        frame.left + block.right * frame.width,
                                        frame.top + block.bottom * frame.height
                                    )
                                    val isCurrent = (findMatches.getOrNull(currentMatchIndex) == match)
                                    if (isCurrent) {
                                        drawRect(Color(0xFFFF9800).copy(0.60f), r.topLeft, r.size)
                                        drawRect(Color(0xFFE65100), r.topLeft, r.size, style = Stroke(3.5f))
                                    } else {
                                        drawRect(Color(0xFFFFEB3B).copy(0.40f), r.topLeft, r.size)
                                        drawRect(Color(0xFFFBC02D), r.topLeft, r.size, style = Stroke(1.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Drawing gesture layer ────────────────────────────────────────────
        if (drawingToolActive) {
            Box(
                Modifier.fillMaxSize().pointerInput(page, activeTool) {
                    fun toContent(o: Offset): Offset {
                        val canvasSize = pageCanvasSizes[page] ?: Size.Zero
                        val bc = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                    }
                    val freehand = activeTool == PdfEditTool.Draw || activeTool == PdfEditTool.Highlight
                    detectDragGestures(
                        onDragStart = { start ->
                            onInteraction()
                            val p = toContent(start)
                            if (freehand) draftPoints = listOf(p)
                            else { draftRectStart = p; draftRectEnd = p }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onInteraction()
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

        // ── Eraser layer ─────────────────────────────────────────────────────
        if (activeTool == PdfEditTool.Eraser) {
            Box(
                Modifier.fillMaxSize().pointerInput(page) {
                    fun toContent(o: Offset): Offset {
                        val cs = pageCanvasSizes[page] ?: Size.Zero
                        val bc = Offset(cs.width / 2f, cs.height / 2f)
                        return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                    }
                    detectTapGestures {
                        val p = toContent(it)
                        val idx = marks.indexOfLast { m -> m.hitTest(p) }
                        if (idx >= 0) marks.removeAt(idx)
                        onInteraction()
                    }
                }.pointerInput(page) {
                    fun toContent(o: Offset): Offset {
                        val cs = pageCanvasSizes[page] ?: Size.Zero
                        val bc = Offset(cs.width / 2f, cs.height / 2f)
                        return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                    }
                    detectDragGestures(onDrag = { change, _ ->
                        change.consume()
                        val p = toContent(change.position)
                        val idx = marks.indexOfLast { m -> m.hitTest(p) }
                        if (idx >= 0) marks.removeAt(idx)
                        onInteraction()
                    })
                }
            )
        }

        // ── Image drag / resize layer ─────────────────────────────────────────
        if (activeTool == PdfEditTool.Image && activeImageId != null) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(page, activeImageId) {
                        fun toContent(o: Offset): Offset {
                            val cs = pageCanvasSizes[page] ?: Size.Zero
                            val bc = Offset(cs.width / 2f, cs.height / 2f)
                            return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                        }
                        detectTapGestures { tapPos ->
                            val p = toContent(tapPos)
                            val tappedMark = marks.lastOrNull { it is PdfMarkup.ImageMarkup && it.hitTest(p) } as? PdfMarkup.ImageMarkup
                            if (tappedMark != null) {
                                onActiveImageIdChanged(tappedMark.id)
                                onActiveToolChanged(PdfEditTool.Image)
                                onShowControls()
                            } else {
                                onActiveImageIdChanged(null)
                                onActiveToolChanged(PdfEditTool.None)
                                onToggleControls()
                            }
                        }
                    }
                    .pointerInput(page, activeImageId) {
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
                                onInteraction()
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
                                onInteraction()
                            }
                        )
                    }
            )
        }

        // ── Text-selection layer ──────────────────────────────────────────────
        if (activeTool == PdfEditTool.SelectText) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(page, ocrBlocks) {
                        fun toContent(o: Offset): Offset {
                            val cs = pageCanvasSizes[page] ?: Size.Zero
                            val bc = Offset(cs.width / 2f, cs.height / 2f)
                            return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                        }
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                onInteraction()
                                val cs    = pageCanvasSizes[page] ?: return@detectTapGestures
                                val bs    = pageBitmapSizes[page]  ?: return@detectTapGestures
                                val frame = fitBitmapRect(cs, bs.width, bs.height)
                                val hit   = hitTestOcrBlock(ocrBlocks, toContent(tap), frame)
                                if (hit != null) onSelectLine(hit.id)
                            },
                            onLongPress = { tap ->
                                onInteraction()
                                val cs    = pageCanvasSizes[page] ?: return@detectTapGestures
                                val bs    = pageBitmapSizes[page]  ?: return@detectTapGestures
                                val frame = fitBitmapRect(cs, bs.width, bs.height)
                                val hit   = hitTestOcrBlock(ocrBlocks, toContent(tap), frame)
                                if (hit != null) onSelectParagraph(hit.id)
                            },
                            onTap = { tap ->
                                onInteraction()
                                val cs    = pageCanvasSizes[page] ?: return@detectTapGestures
                                val bs    = pageBitmapSizes[page]  ?: return@detectTapGestures
                                val frame = fitBitmapRect(cs, bs.width, bs.height)
                                val hit   = hitTestOcrBlock(ocrBlocks, toContent(tap), frame)
                                if (hit != null) onToggleOcrSelection(hit.id) else onClearOcrSelection()
                            }
                        )
                    }
                    .pointerInput(page, ocrBlocks) {
                        fun toContent(o: Offset): Offset {
                            val cs = pageCanvasSizes[page] ?: Size.Zero
                            val bc = Offset(cs.width / 2f, cs.height / 2f)
                            return screenToContent(o, zoomAnim.value, Offset(panXAnim.value, panYAnim.value), bc)
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { s ->
                                onInteraction()
                                val p = toContent(s)
                                selDragStart = p; selDragEnd = p
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onInteraction()
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
                                    hitTestOcrBlock(ocrBlocks, s, frame)?.let { onToggleOcrSelection(it.id) }
                                    return@detectDragGesturesAfterLongPress
                                }
                                val marquee = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                                onSelectOcrRange(
                                    ocrBlocks
                                        .filter { intersects(marquee, ocrBlockToRect(it, frame)) }
                                        .map { it.id }
                                        .toSet()
                                )
                            }
                        )
                    }
            )
        }

        // ── Zoom HUD ──────────────────────────────────────────────────────────
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
                    style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

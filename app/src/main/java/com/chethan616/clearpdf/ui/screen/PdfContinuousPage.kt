package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.ui.viewmodel.FindMatch
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import kotlin.math.max
import kotlin.math.min

/**
 * A single page inside the continuous (Adobe-style) vertical viewer.
 *
 * Zoom and pan are owned by the parent container (a [graphicsLayer] wrapping the whole
 * page column), so all pointer coordinates arrive already in this page's local, unscaled
 * space. Because the rendered bitmap fills the item width, local space == content space
 * and no manual zoom/pan projection is needed here — only tool gestures live on the page.
 */
@Composable
internal fun PdfContinuousPage(
    page: Int,
    bitmap: Bitmap?,
    marks: MutableList<PdfMarkup>,
    ocrBlocks: List<OcrTextBlock>,
    selectedOcrIds: Set<String>,
    findMatches: List<FindMatch>,
    currentMatchIndex: Int,
    showFindBar: Boolean,
    activeTool: PdfEditTool,
    currentColor: Color,
    currentStrokeWidth: Float,
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
    onEditAnnotation: (Long) -> Unit
) {
    var draftPoints    by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var draftRectEnd   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragStart   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragEnd     by remember(page, activeTool) { mutableStateOf<Offset?>(null) }

    // Page layout (rebuilt on the Pdf_Tools model): the image is drawn at its TRUE
    // aspect via ContentScale.FillWidth, so the box height follows the bitmap. There
    // is no forced-aspect placeholder jump, and single / landscape pages lay out
    // correctly. Every overlay uses matchParentSize() so its coordinate frame is
    // exactly the image frame (0,0 → box size).
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(if (bitmap == null) Modifier.aspectRatio(1f / 1.414f) else Modifier)
            .background(Color(0xFF15181E))
            .onSizeChanged { sz ->
                pageCanvasSizes[page] = Size(sz.width.toFloat(), sz.height.toFloat())
                if (bitmap != null) pageBitmapSizes[page] = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
            }
    ) {
        if (bitmap == null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1976D2), strokeWidth = 2.dp)
            }
            return@Box
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )

        // The image fills the box width and the box height follows it, so the content
        // frame is the full box.
        Canvas(Modifier.matchParentSize()) {
            val frame = Rect(0f, 0f, size.width, size.height)

            selectedOcrIds.forEach { id ->
                ocrBlocks.firstOrNull { it.id == id }?.let { b ->
                    val r = ocrBlockToRect(b, frame)
                    drawRect(Color(0xFF42A5F5).copy(0.28f), r.topLeft, r.size)
                    drawRect(Color(0xFF1976D2), r.topLeft, r.size, style = Stroke(1.5f))
                }
            }

            marks.forEach { markup ->
                when (markup) {
                    is PdfMarkup.StrokeMarkup -> if (markup.points.size > 1) {
                        drawPath(smoothPath(markup.points), markup.color.copy(markup.alpha),
                            style = Stroke(markup.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
                    is PdfMarkup.LineMarkup ->
                        if (markup.arrowHead) drawArrow(markup.start, markup.end, markup.color.copy(markup.alpha), markup.width)
                        else drawLine(markup.color.copy(markup.alpha), markup.start, markup.end, markup.width)
                    is PdfMarkup.TextBlockHighlightMarkup -> ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val r = ocrBlockToRect(b, frame); drawRect(markup.color.copy(markup.alpha), r.topLeft, r.size)
                    }
                    is PdfMarkup.TextBlockLineMarkup -> ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val r = ocrBlockToRect(b, frame)
                        val y = if (markup.strikeThrough) r.center.y else r.bottom - r.height * 0.10f
                        drawLine(markup.color.copy(markup.alpha), Offset(r.left, y), Offset(r.right, y), markup.width)
                    }
                    is PdfMarkup.ImageMarkup -> {
                        val r = Rect(min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y), max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y))
                        if (!markup.bitmap.isRecycled && markup.bitmap.width > 0) runCatching {
                            drawImage(
                                image = markup.bitmap.asImageBitmap(),
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = androidx.compose.ui.unit.IntSize(markup.bitmap.width, markup.bitmap.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1))
                            )
                        }
                        if (activeTool == PdfEditTool.Image && markup.id == activeImageId) {
                            drawRect(Color(0xFF1976D2), r.topLeft, r.size, style = Stroke(2f))
                            drawCircle(Color(0xFF1976D2), 14f, Offset(r.right, r.bottom))
                            drawCircle(Color.White, 7f, Offset(r.right, r.bottom))
                        }
                    }
                    is PdfMarkup.TextBoxMarkup -> {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = markup.color.toArgb(); textSize = markup.fontSize
                        }
                        val linesT = if (markup.text.isEmpty()) listOf("") else markup.text.split("\n")
                        drawIntoCanvas { c ->
                            var yy = markup.position.y + markup.fontSize
                            linesT.forEach { ln -> c.nativeCanvas.drawText(ln, markup.position.x, yy, paint); yy += markup.fontSize * 1.2f }
                        }
                        if (markup.text.isEmpty()) drawRect(Color(0xFF1976D2).copy(0.5f),
                            Offset(markup.position.x - 4f, markup.position.y - 4f), Size(markup.fontSize * 5f, markup.fontSize * 1.4f), style = Stroke(2f))
                    }
                    is PdfMarkup.NoteMarkup -> {
                        val sz = 30f; val tl = markup.anchor
                        drawRoundRect(markup.color, tl, Size(sz, sz), CornerRadius(6f, 6f))
                        val fold = Path().apply { moveTo(tl.x + sz * 0.62f, tl.y); lineTo(tl.x + sz, tl.y + sz * 0.38f); lineTo(tl.x + sz * 0.62f, tl.y + sz * 0.38f); close() }
                        drawPath(fold, Color.White.copy(0.55f))
                        val lc = Color.White.copy(0.75f)
                        drawLine(lc, Offset(tl.x + 6f, tl.y + sz * 0.56f), Offset(tl.x + sz - 6f, tl.y + sz * 0.56f), 2f)
                        drawLine(lc, Offset(tl.x + 6f, tl.y + sz * 0.74f), Offset(tl.x + sz - 9f, tl.y + sz * 0.74f), 2f)
                    }
                }
            }

            // In-progress drafts
            if (draftPoints.size > 1) {
                val isHl = activeTool == PdfEditTool.Highlight
                drawPath(smoothPath(draftPoints), currentColor.copy(if (isHl) 0.32f else 0.95f),
                    style = Stroke(if (isHl) currentStrokeWidth * 3.5f else currentStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
            if (selDragStart != null && selDragEnd != null && activeTool == PdfEditTool.SelectText) {
                val s = selDragStart!!; val e = selDragEnd!!
                val r = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                drawRect(Color(0xFFAB47BC).copy(0.20f), r.topLeft, r.size)
                drawRect(Color(0xFFAB47BC), r.topLeft, r.size, style = Stroke(2f))
            }

            if (showFindBar && findMatches.isNotEmpty()) {
                findMatches.filter { it.pageIndex == page }.forEach { match ->
                    ocrBlocks.firstOrNull { it.id == match.blockId }?.let { block ->
                        val r = ocrBlockToRect(block, frame)
                        if (findMatches.getOrNull(currentMatchIndex) == match) {
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

        // ── Tool gesture layers (local coordinates == content coordinates) ──────
        val drawingToolActive = activeTool in setOf(
            PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect, PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow
        )

        // Reading mode: a plain tap on a placed image/signature re-selects it (shows its
        // recolour/replace/delete controls). Only consumes the tap when it actually hits
        // an image — otherwise the tap falls through to the container (toggle chrome / zoom).
        if (activeTool == PdfEditTool.None) {
            Box(Modifier.matchParentSize().pointerInput(page, marks.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = marks.lastOrNull { it is PdfMarkup.ImageMarkup && it.hitTest(down.position) } as? PdfMarkup.ImageMarkup
                    if (hit != null) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            onActiveImageIdChanged(hit.id)
                            onActiveToolChanged(PdfEditTool.Image)
                            onShowControls()
                            onInteraction()
                        }
                    }
                }
            })
        }

        if (drawingToolActive) {
            Box(Modifier.matchParentSize().pointerInput(page, activeTool) {
                val freehand = activeTool == PdfEditTool.Draw || activeTool == PdfEditTool.Highlight
                detectDragGestures(
                    onDragStart = { p -> onInteraction(); if (freehand) draftPoints = listOf(p) else { draftRectStart = p; draftRectEnd = p } },
                    onDrag = { ch, _ -> ch.consume(); if (freehand) draftPoints = draftPoints + ch.position else draftRectEnd = ch.position },
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
            })
        }

        if (activeTool == PdfEditTool.Eraser) {
            Box(Modifier.matchParentSize().pointerInput(page) {
                detectTapGestures { p -> val idx = marks.indexOfLast { it.hitTest(p) }; if (idx >= 0) marks.removeAt(idx); onInteraction() }
            }.pointerInput(page) {
                detectDragGestures(onDrag = { ch, _ -> ch.consume(); val idx = marks.indexOfLast { it.hitTest(ch.position) }; if (idx >= 0) marks.removeAt(idx); onInteraction() })
            })
        }

        if (activeTool == PdfEditTool.Text || activeTool == PdfEditTool.Note) {
            Box(Modifier.matchParentSize().pointerInput(page, activeTool) {
                detectTapGestures { p -> if (activeTool == PdfEditTool.Text) onPlaceText(p) else onPlaceNote(p); onInteraction() }
            })
        }

        if (activeTool == PdfEditTool.Image && activeImageId != null) {
            Box(Modifier.matchParentSize()
                .pointerInput(page, activeImageId) {
                    detectTapGestures { p ->
                        val hit = marks.lastOrNull { it is PdfMarkup.ImageMarkup && it.hitTest(p) } as? PdfMarkup.ImageMarkup
                        if (hit != null) { onActiveImageIdChanged(hit.id); onShowControls() }
                        else { onActiveImageIdChanged(null); onActiveToolChanged(PdfEditTool.None); onToggleControls() }
                    }
                }
                .pointerInput(page, activeImageId) {
                    var resizing = false
                    detectDragGestures(
                        onDragStart = { p ->
                            val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                            val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup
                            resizing = img != null && (p - img.end).getDistance() <= 40f; onInteraction()
                        },
                        onDrag = { ch, drag ->
                            ch.consume()
                            val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                            val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup ?: return@detectDragGestures
                            marks[idx] = if (resizing) img.copy(end = Offset((img.end.x + drag.x).coerceAtLeast(img.start.x + 24f), (img.end.y + drag.y).coerceAtLeast(img.start.y + 24f)))
                            else img.copy(start = img.start + drag, end = img.end + drag)
                            onInteraction()
                        }
                    )
                }
            )
        }

        if (activeTool == PdfEditTool.SelectText) {
            Box(Modifier.matchParentSize()
                .pointerInput(page, ocrBlocks) {
                    val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    detectTapGestures(
                        onDoubleTap = { p -> hitTestOcrBlock(ocrBlocks, p, frame)?.let { onSelectLine(it.id) }; onInteraction() },
                        onLongPress = { p -> hitTestOcrBlock(ocrBlocks, p, frame)?.let { onSelectParagraph(it.id) }; onInteraction() },
                        onTap = { p -> val h = hitTestOcrBlock(ocrBlocks, p, frame); if (h != null) onToggleOcrSelection(h.id) else onClearOcrSelection(); onInteraction() }
                    )
                }
                .pointerInput(page, ocrBlocks) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p -> selDragStart = p; selDragEnd = p; onInteraction() },
                        onDrag = { ch, _ -> ch.consume(); selDragEnd = ch.position; onInteraction() },
                        onDragCancel = { selDragStart = null; selDragEnd = null },
                        onDragEnd = {
                            val s = selDragStart; val e = selDragEnd; selDragStart = null; selDragEnd = null
                            if (s == null || e == null || ocrBlocks.isEmpty()) return@detectDragGesturesAfterLongPress
                            val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                            val marquee = Rect(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
                            onSelectOcrRange(ocrBlocks.filter { intersects(marquee, ocrBlockToRect(it, frame)) }.map { it.id }.toSet())
                        }
                    )
                }
            )
        }

    }
}

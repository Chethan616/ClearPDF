package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.chethan616.clearpdf.ui.viewmodel.FindMatch
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
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
    selectedOcrRanges: List<OcrTextRange>,
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
    /** A stroke/shape just landed on this page. Feeds the viewer's undo history. */
    onMarkAdded: () -> Unit = {},
    onToggleControls: () -> Unit,
    onShowControls: () -> Unit,
    onActiveToolChanged: (PdfEditTool) -> Unit,
    onActiveImageIdChanged: (Long?) -> Unit,
    onClearOcrSelection: () -> Unit,
    onSelectOcrRange: (List<OcrTextRange>) -> Unit,
    onPlaceText: (Offset) -> Unit,
    onPlaceNote: (Offset) -> Unit,
    onEditAnnotation: (Long) -> Unit,
    onEditShape: (Int) -> Unit = {},
    // Generic markup selection (shapes / text / notes) for move + resize.
    selectedMarkupIndex: Int = -1,
    onSelectMarkup: (Int) -> Unit = {},
    onDeleteMarkup: (Int) -> Unit = {},
    onCopySelection: () -> Unit = {},
    onHighlightSelection: () -> Unit = {}
) {
    var draftPoints    by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var draftRectEnd   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragStart   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var selDragEnd     by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    val selectionHandleDiameterPx = with(LocalDensity.current) { 32.dp.toPx() }
    val selectionHandleHitRadiusPx = with(LocalDensity.current) { 30.dp.toPx() }

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

            selectedOcrRanges.forEach { range ->
                ocrBlocks.firstOrNull { it.id == range.blockId }?.let { b ->
                    val r = expandedTextHighlightRect(ocrTextRangeToRect(b, range, frame), verticalScale = 1.35f)
                    // Match the reference's soft cyan fill: no border, no font changes, and
                    // enough vertical air for ascenders/descenders without covering other lines.
                    val radius = (r.height * 0.14f).coerceIn(2f, 5f)
                    drawRoundRect(Color(0xFF9ADBF0).copy(0.82f), r.topLeft, r.size, CornerRadius(radius, radius))
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
                        val range = OcrTextRange(markup.blockId, markup.start, markup.end)
                        val r = expandedTextHighlightRect(ocrTextRangeToRect(b, range, frame))
                        drawRoundRect(markup.color.copy(markup.alpha), r.topLeft, r.size, CornerRadius((r.height * 0.14f).coerceIn(2f, 5f), (r.height * 0.14f).coerceIn(2f, 5f)))
                    }
                    is PdfMarkup.TextBlockLineMarkup -> ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val r = ocrTextRangeToRect(b, OcrTextRange(markup.blockId, markup.start, markup.end), frame)
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
                            val accent = Color(0xFF0A84FF)
                            // Rounded selection frame.
                            drawRoundRect(accent, r.topLeft, r.size, CornerRadius(10f, 10f), style = Stroke(2.5f))
                            // Passive corner dots (visual anchors).
                            listOf(r.topLeft, Offset(r.right, r.top), Offset(r.left, r.bottom)).forEach { c ->
                                drawCircle(Color.White, 8f, c)
                                drawCircle(accent, 8f, c, style = Stroke(2f))
                            }
                            // Prominent bottom-right RESIZE handle with a diagonal glyph.
                            val br = Offset(r.right, r.bottom)
                            drawCircle(Color.White, 22f, br)
                            drawCircle(accent, 22f, br, style = Stroke(3f))
                            drawLine(accent, Offset(br.x - 7f, br.y + 1f), Offset(br.x + 1f, br.y - 7f), 3f)
                            drawLine(accent, Offset(br.x - 1f, br.y + 7f), Offset(br.x + 7f, br.y - 1f), 3f)
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

            // Selection frame + handles for the selected shape / text / note.
            if (activeTool == PdfEditTool.None) {
                marks.getOrNull(selectedMarkupIndex)?.takeIf { it.isTransformable() }?.let { selM ->
                    selM.movableBounds()?.let { b ->
                        val accent = Color(0xFF0A84FF)
                        val fr = Rect(b.left - 6f, b.top - 6f, b.right + 6f, b.bottom + 6f)
                        drawRoundRect(accent, fr.topLeft, fr.size, CornerRadius(10f, 10f), style = Stroke(2.5f))
                        // Passive anchor dots.
                        listOf(fr.topLeft, Offset(fr.right, fr.top), Offset(fr.left, fr.bottom)).forEach { c ->
                            drawCircle(Color.White, 7f, c); drawCircle(accent, 7f, c, style = Stroke(2f))
                        }
                        // Bottom-right resize handle (hidden for fixed-size notes).
                        if (selM.isResizable()) {
                            val br = Offset(fr.right, fr.bottom)
                            drawCircle(Color.White, 20f, br); drawCircle(accent, 20f, br, style = Stroke(3f))
                            drawLine(accent, Offset(br.x - 6f, br.y + 1f), Offset(br.x + 1f, br.y - 6f), 3f)
                            drawLine(accent, Offset(br.x - 1f, br.y + 6f), Offset(br.x + 6f, br.y - 1f), 3f)
                        }
                    }
                }
            }

            if (activeTool == PdfEditTool.SelectText) {
                ocrSelectionHandleAnchors(ocrBlocks, selectedOcrRanges, frame)?.let { (start, end) ->
                    drawTextSelectionHandle(start, selectionHandleDiameterPx)
                    drawTextSelectionHandle(end, selectionHandleDiameterPx)
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
            if (showFindBar && findMatches.isNotEmpty()) {
                val activeMatch = findMatches.getOrNull(currentMatchIndex)
                findMatches.filter { it.pageIndex == page }.forEach { match ->
                    // Highlight the exact matched WORD (normalized rect), not the whole line.
                    val r = Rect(
                        frame.left + match.left * frame.width,
                        frame.top + match.top * frame.height,
                        frame.left + match.right * frame.width,
                        frame.top + match.bottom * frame.height
                    )
                    // The rect carries the word's EXACT bounds (per-glyph width × glyph box height).
                    // Extend it a touch VERTICALLY so ascenders (the dot on "i") and descenders (the
                    // tail on "p"/"g") fall INSIDE the selection — the OCR/text box hugs the x-height,
                    // so without this those strokes poke out above/below the fill. A hair of horizontal
                    // padding keeps it snug across font sizes.
                    val padX = (r.height * 0.05f).coerceIn(0.75f, 3f)
                    val padTop = r.height * 0.13f
                    val padBottom = r.height * 0.11f
                    val hl = Rect(r.left - padX, r.top - padTop, r.right + padX, r.bottom + padBottom)
                    val cr = (hl.height * 0.14f).coerceIn(2f, 5f)
                    // A TRUE text-selection overlay: one uniform blue fill covering the whole glyph
                    // area (drawn over the page, so the glyphs read through it as a darker shape —
                    // Google-Docs / Acrobat style), not a faint tint sitting behind the ink.
                    if (match == activeMatch) {
                        val base = Color(0xFF3B82F6)
                        drawRoundRect(base.copy(0.52f), hl.topLeft, hl.size, CornerRadius(cr, cr))
                        // Focused-result border (unchanged style) so the current hit stands out.
                        drawRoundRect(base.copy(0.9f), hl.topLeft, hl.size, CornerRadius(cr, cr), style = Stroke(1.25f))
                    } else {
                        // Secondary results: same uniform coverage, lower emphasis, no border.
                        drawRoundRect(Color(0xFF60A5FA).copy(0.42f), hl.topLeft, hl.size, CornerRadius(cr, cr))
                    }
                }
            }
        }

        // ── Tool gesture layers (local coordinates == content coordinates) ──────
        val drawingToolActive = activeTool in setOf(
            PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect, PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow
        )

        // Reading mode: a plain tap on a placed markup selects it. Images jump to their
        // dedicated toolbar (replace/recolour/delete); shapes/text/notes get a selection
        // frame with move + resize handles and a small Edit/Delete bar. A tap that misses
        // every markup deselects and falls through to the container (chrome toggle / zoom).
        if (activeTool == PdfEditTool.None) {
            Box(Modifier.matchParentSize().pointerInput(page, marks.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // If a markup is already selected, let its transform layer handle touches
                    // inside its frame (don't steal them here).
                    val sel = marks.getOrNull(selectedMarkupIndex)?.takeIf { it.isTransformable() }
                    val selBounds = sel?.movableBounds()
                    if (selBounds != null && selBounds.inflate(30f).contains(down.position)) return@awaitEachGesture

                    val idx = marks.indexOfLast { it.hitTest(down.position) }
                    if (idx >= 0) {
                        val hit = marks[idx]
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            when (hit) {
                                is PdfMarkup.ImageMarkup -> {
                                    onSelectMarkup(-1)
                                    onActiveImageIdChanged(hit.id)
                                    onActiveToolChanged(PdfEditTool.Image)
                                }
                                is PdfMarkup.TextBoxMarkup,
                                is PdfMarkup.NoteMarkup,
                                is PdfMarkup.RectMarkup,
                                is PdfMarkup.OvalMarkup,
                                is PdfMarkup.LineMarkup,
                                is PdfMarkup.StrokeMarkup -> onSelectMarkup(idx)
                                else -> Unit
                            }
                            onShowControls()
                            onInteraction()
                        }
                    } else {
                        // Missed everything → clear any selection (tap propagates to container).
                        if (selectedMarkupIndex >= 0) onSelectMarkup(-1)
                    }
                }
            })
        }

        // ── Transform layer: move + resize the selected shape / text / note ──────
        val selForXf = marks.getOrNull(selectedMarkupIndex)?.takeIf { activeTool == PdfEditTool.None && it.isTransformable() }
        val selXfBounds = selForXf?.movableBounds()
        if (selForXf != null && selXfBounds != null) {
            val density2 = LocalDensity.current
            val pad = 30f
            val boxL = selXfBounds.left - pad
            val boxT = selXfBounds.top - pad
            val boxW = selXfBounds.width + pad * 2
            val boxH = selXfBounds.height + pad * 2
            Box(
                Modifier
                    .offset { IntOffset(boxL.roundToInt(), boxT.roundToInt()) }
                    .size(with(density2) { boxW.toDp() }, with(density2) { boxH.toDp() })
                    .pointerInput(page, selectedMarkupIndex) {
                        var mode = 0 // 1 = move, 2 = resize
                        detectDragGestures(
                            onDragStart = { local ->
                                val cur = marks.getOrNull(selectedMarkupIndex)
                                val bb = cur?.movableBounds()
                                // Convert the box-local touch back to page space.
                                val pPage = Offset(local.x + boxL, local.y + boxT)
                                mode = if (bb != null && cur.isResizable() && (pPage - bb.bottomRight).getDistance() <= 60f) 2 else 1
                                onInteraction()
                            },
                            onDrag = { ch, drag ->
                                if (mode == 0) return@detectDragGestures
                                ch.consume()
                                val cur = marks.getOrNull(selectedMarkupIndex) ?: return@detectDragGestures
                                val bb = cur.movableBounds() ?: return@detectDragGestures
                                marks[selectedMarkupIndex] = if (mode == 2) cur.resizedBy(drag, bb) else cur.translated(drag)
                                onInteraction()
                            },
                            onDragEnd = { mode = 0 },
                            onDragCancel = { mode = 0 }
                        )
                    }
            )
        }

        if (drawingToolActive) {
            // NOTE: currentColor/currentStrokeWidth are part of the key so the gesture
            // detector restarts and re-captures them whenever they change. Without this
            // the onDragEnd closure keeps the color/width captured when the tool was first
            // selected — so changing color mid-tool wouldn't apply until you switched tools.
            Box(Modifier.matchParentSize().pointerInput(page, activeTool, currentColor, currentStrokeWidth) {
                val freehand = activeTool == PdfEditTool.Draw || activeTool == PdfEditTool.Highlight
                detectDragGestures(
                    onDragStart = { p -> onInteraction(); if (freehand) draftPoints = listOf(p) else { draftRectStart = p; draftRectEnd = p } },
                    onDrag = { ch, _ -> ch.consume(); if (freehand) draftPoints = draftPoints + ch.position else draftRectEnd = ch.position },
                    onDragCancel = { draftPoints = emptyList(); draftRectStart = null; draftRectEnd = null },
                    onDragEnd = {
                        val before = marks.size
                        when (activeTool) {
                            PdfEditTool.Draw      -> if (draftPoints.size > 1) marks.add(PdfMarkup.StrokeMarkup(draftPoints, currentColor, currentStrokeWidth, 0.95f))
                            PdfEditTool.Highlight -> if (draftPoints.size > 1) marks.add(PdfMarkup.StrokeMarkup(draftPoints, currentColor, currentStrokeWidth * 3.5f, 0.32f))
                            PdfEditTool.Rect      -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.RectMarkup(s, e, currentColor, 1f, false)) } }
                            PdfEditTool.Ellipse   -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.OvalMarkup(s, e, currentColor, 1f, false)) } }
                            PdfEditTool.Line      -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.LineMarkup(s, e, currentColor, currentStrokeWidth, 1f, false)) } }
                            PdfEditTool.Arrow     -> draftRectStart?.let { s -> draftRectEnd?.let { e -> marks.add(PdfMarkup.LineMarkup(s, e, currentColor, currentStrokeWidth, 1f, true)) } }
                            else -> Unit
                        }
                        // Every branch above is conditional (a tap with <2 points adds nothing), so
                        // compare sizes rather than assuming the drag produced a mark.
                        if (marks.size > before) onMarkAdded()
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
                            // Generous grab radius around the bottom-right handle (Apple-style
                            // touch target much larger than the visual handle).
                            resizing = img != null && (p - img.end).getDistance() <= 64f; onInteraction()
                        },
                        onDrag = { ch, drag ->
                            ch.consume()
                            val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                            val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup ?: return@detectDragGestures
                            // Clamp to the page bounds so the image can never be dragged past the
                            // page edge (where it would be clipped and hidden behind the next page).
                            val pw = size.width.toFloat(); val ph = size.height.toFloat()
                            marks[idx] = if (resizing) {
                                img.copy(end = Offset(
                                    (img.end.x + drag.x).coerceIn(img.start.x + 24f, pw),
                                    (img.end.y + drag.y).coerceIn(img.start.y + 24f, ph)
                                ))
                            } else {
                                val iw = img.end.x - img.start.x; val ih = img.end.y - img.start.y
                                val nx = (img.start.x + drag.x).coerceIn(0f, (pw - iw).coerceAtLeast(0f))
                                val ny = (img.start.y + drag.y).coerceIn(0f, (ph - ih).coerceAtLeast(0f))
                                img.copy(start = Offset(nx, ny), end = Offset(nx + iw, ny + ih))
                            }
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
                        // A tap or double-tap lands on one word. Long-press follows the platform
                        // text-selection convention: select that word and reveal the contextual
                        // Copy / Highlight actions above it.
                        onDoubleTap = { p -> hitTestOcrWord(ocrBlocks, p, frame)?.let { onSelectOcrRange(listOf(it)) }; onInteraction() },
                        onLongPress = { p -> hitTestOcrWord(ocrBlocks, p, frame)?.let { onSelectOcrRange(listOf(it)) }; onInteraction() },
                        onTap = { p -> hitTestOcrWord(ocrBlocks, p, frame)?.let { onSelectOcrRange(listOf(it)) } ?: onClearOcrSelection(); onInteraction() }
                    )
                }
                // Direct drag (no long-press wait): sweep a contiguous run of words in reading order
                // and update the range live. The page stays in text-selection mode, while the parent
                // viewer still receives two-finger pinch events for zoom.
                .pointerInput(page, ocrBlocks, selectedOcrRanges, selectionHandleHitRadiusPx) {
                    fun updateRange() {
                        val s = selDragStart; val e = selDragEnd
                        if (s == null || e == null || ocrBlocks.isEmpty()) return
                        val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                        onSelectOcrRange(ocrWordRangesBetween(ocrBlocks, frame, s, e))
                    }

                    // Do not use detectDragGestures here: it commits to a one-finger drag before
                    // the second finger of a pinch arrives. That is why zooming used to leave a
                    // text selection behind. We wait for touch slop, then only consume while the
                    // gesture remains single-touch; a second finger cancels selection immediately
                    // and leaves the event stream available to the viewer's pinch detector.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                        val handles = ocrSelectionHandleAnchors(ocrBlocks, selectedOcrRanges, frame)
                        val handleMode = when {
                            handles == null -> 0
                            (down.position - handles.first).getDistance() <= selectionHandleHitRadiusPx -> 1
                            (down.position - handles.second).getDistance() <= selectionHandleHitRadiusPx -> 2
                            else -> 0
                        }
                        val fixedHandlePoint = when (handleMode) {
                            1 -> handles?.second
                            2 -> handles?.first
                            else -> null
                        }
                        val pointerId = down.id
                        var selecting = false
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed > 1) {
                                if (!multiTouch) {
                                    multiTouch = true
                                    if (selecting) onClearOcrSelection()
                                }
                                selDragStart = null
                                selDragEnd = null
                            } else if (!multiTouch) {
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change != null) {
                                    if (!selecting && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        selecting = true
                                        selDragStart = fixedHandlePoint ?: down.position
                                        selDragEnd = change.position
                                        updateRange()
                                        onInteraction()
                                    } else if (selecting) {
                                        change.consume()
                                        selDragStart = fixedHandlePoint ?: down.position
                                        selDragEnd = change.position
                                        updateRange()
                                        onInteraction()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        selDragStart = null
                        selDragEnd = null
                    }
                }
            )
        }

        // ── Contextual selection bubble (Copy / Highlight), smart-positioned ──────
        val csz = pageCanvasSizes[page]
        if (activeTool == PdfEditTool.SelectText && selectedOcrIds.isNotEmpty() && csz != null && csz.width > 0f) {
            val frame = Rect(0f, 0f, csz.width, csz.height)
            val selRects = selectedOcrRanges.mapNotNull { range ->
                ocrBlocks.firstOrNull { it.id == range.blockId }?.let { ocrTextRangeToRect(it, range, frame) }
            }.ifEmpty {
                selectedOcrIds.mapNotNull { id -> ocrBlocks.firstOrNull { it.id == id }?.let { ocrBlockToRect(it, frame) } }
            }
            if (selRects.isNotEmpty()) {
                val density = LocalDensity.current
                val selLeft = selRects.minOf { it.left }
                val selTop = selRects.minOf { it.top }
                val selRight = selRects.maxOf { it.right }
                val selBottom = selRects.maxOf { it.bottom }
                // Is any selected word already carrying a highlight? If so the pill gains a Remove
                // action — this is the fix for "tap a highlight again and there's no delete, only
                // copy/highlight". The selection and the highlight live in two different models (OCR
                // selection vs. the page's markup list), so we cross-reference by block id.
                val selectionHasHighlight = selectedOcrIds.any { id ->
                    marks.any { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId == id }
                }
                val gapPx = with(density) { 8.dp.toPx() }
                val bubbleHpx = with(density) { 44.dp.toPx() }
                val bubbleWpx = with(density) { (if (selectionHasHighlight) 226.dp else 150.dp).toPx() }
                // Flip below the selection when it's too close to the page top.
                val placeBelow = selTop < bubbleHpx + gapPx
                val by = (if (placeBelow) selBottom + gapPx else selTop - bubbleHpx - gapPx).coerceIn(0f, (csz.height - bubbleHpx).coerceAtLeast(0f))
                val bx = ((selLeft + selRight) / 2f - bubbleWpx / 2f).coerceIn(0f, (csz.width - bubbleWpx).coerceAtLeast(0f))

                Row(
                    Modifier
                        .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF1C1F26).copy(0.97f))
                        .border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        "Copy",
                        style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onCopySelection(); onClearOcrSelection() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                    Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.14f)))
                    BasicText(
                        if (selectionHasHighlight) "Recolor" else "Highlight",
                        style = TextStyle(Color(0xFFFFCC33), 13.sp, FontWeight.SemiBold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onHighlightSelection() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                    if (selectionHasHighlight) {
                        Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.14f)))
                        BasicText(
                            "Delete",
                            style = TextStyle(Color(0xFFFF6B6B), 13.sp, FontWeight.SemiBold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    // Drop every highlight sitting on a selected word, then clear the
                                    // selection so the pill dismisses.
                                    marks.removeAll { it is PdfMarkup.TextBlockHighlightMarkup && it.blockId in selectedOcrIds }
                                    onClearOcrSelection()
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        // ── Contextual Edit / Delete bar for the selected shape / text / note ──────
        if (activeTool == PdfEditTool.None && csz != null && csz.width > 0f) {
            marks.getOrNull(selectedMarkupIndex)?.takeIf { it.isTransformable() }?.let { selM ->
                selM.movableBounds()?.let { b ->
                    val density = LocalDensity.current
                    val gapPx = with(density) { 12.dp.toPx() }
                    val barHpx = with(density) { 44.dp.toPx() }
                    val barWpx = with(density) { 132.dp.toPx() }
                    val placeBelow = b.top < barHpx + gapPx
                    val by = (if (placeBelow) b.bottom + gapPx else b.top - barHpx - gapPx)
                        .coerceIn(0f, (csz.height - barHpx).coerceAtLeast(0f))
                    val bx = ((b.left + b.right) / 2f - barWpx / 2f)
                        .coerceIn(0f, (csz.width - barWpx).coerceAtLeast(0f))

                    Row(
                        Modifier
                            .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF1C1F26).copy(0.97f))
                            .border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            "Edit",
                            style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    when (selM) {
                                        is PdfMarkup.TextBoxMarkup -> onEditAnnotation(selM.id)
                                        is PdfMarkup.NoteMarkup    -> onEditAnnotation(selM.id)
                                        else -> onEditShape(selectedMarkupIndex)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                        Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(0.14f)))
                        BasicText(
                            "Delete",
                            style = TextStyle(Color(0xFFFF6B6B), 13.sp, FontWeight.SemiBold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onDeleteMarkup(selectedMarkupIndex) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

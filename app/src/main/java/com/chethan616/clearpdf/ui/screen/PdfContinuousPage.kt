package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Edit
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.selection.PdfTextSelectionState
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.chethan616.clearpdf.ui.viewmodel.FindMatch
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.components.liquidStretchOnDrag
import androidx.compose.ui.zIndex
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.launch
import com.kyant.backdrop.backdrops.LayerBackdrop
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
    backdrop: LayerBackdrop,
    bitmap: Bitmap?,
    darkPageAppearance: Boolean = false,
    marks: MutableList<PdfMarkup>,
    ocrBlocks: List<OcrTextBlock>,
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
    onPlaceText: (Offset) -> Unit,
    onPlaceNote: (Offset) -> Unit,
    onEditAnnotation: (Long) -> Unit,
    onEditShape: (Int) -> Unit = {},
    // Generic markup selection (shapes / text / notes) for move + resize.
    selectedMarkupIndex: Int = -1,
    selectedMarkupIndices: Set<Int> = emptySet(),
    onSelectMarkup: (Int) -> Unit = {},
    onSelectMarkups: (Set<Int>) -> Unit = {},
    onMoveMarkups: (Set<Int>, Offset) -> Unit = { _, _ -> },
    onDeleteMarkup: (Int) -> Unit = {},
    /** The viewer's text selection: this page draws its slice of the highlight and registers its coordinates. */
    textSelection: PdfTextSelectionState,
    /** Where this page publishes its contextual Edit / Delete bar; the viewer draws it. */
    markupBar: MarkupBarHost,
    /** Moves an image dragged off this page onto [toPage]; false when there is no such page. */
    onMoveImageToPage: (fromPage: Int, img: PdfMarkup.ImageMarkup, toPage: Int) -> Boolean = { _, _, _ -> false }
) {
    var imageDragging by remember { mutableStateOf(false) }
    var draftPoints    by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var draftRectEnd   by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
    var draftLasso by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    val selectionColors = LocalTextSelectionColors.current
    val latestSelectedMarkupIndex by rememberUpdatedState(selectedMarkupIndex)
    val latestSelectedMarkupIndices by rememberUpdatedState(selectedMarkupIndices)
    val latestOcrBlocks by rememberUpdatedState(ocrBlocks)
    DisposableEffect(page, textSelection) { onDispose { textSelection.unregisterPage(page, null) } }
    // Accessibility: expose the page's extracted text to TalkBack, plus a "select page text" action.
    val selectPageLabel = stringResource(R.string.selection_select_page_text)
    val pageText = remember(ocrBlocks) { textSelection.layout(page)?.text.orEmpty() }
    val pageTextSemantics = if (pageText.isEmpty()) Modifier else Modifier.semantics {
        text = AnnotatedString(pageText)
        customActions = listOf(CustomAccessibilityAction(selectPageLabel) { textSelection.selectPages(page, page); true })
    }

    // Page layout (rebuilt on the Pdf_Tools model): the image is drawn at its TRUE
    // aspect via ContentScale.FillWidth, so the box height follows the bitmap. There
    // is no forced-aspect placeholder jump, and single / landscape pages lay out
    // correctly. Every overlay uses matchParentSize() so its coordinate frame is
    // exactly the image frame (0,0 → box size).
    // Height to reserve while this page has no bitmap — either it has not rendered yet, or it
    // rendered once and was evicted from the cache while staying composed.
    //
    // This used to be hardcoded to A4 portrait. On a landscape document (a converted .pptx is 16:9)
    // that reserved a box ~2.5x taller than the page, and the box collapsed the instant the bitmap
    // arrived. On the last page that collapse is a feedback loop: the list is centred
    // (`Arrangement.Center`), so the shrink shifts content, which can push the page out of the
    // viewport, which disposes it, which restores the tall placeholder, which shifts it back in —
    // visible as the last page flickering. Reserving the page's real aspect removes the size change
    // entirely, so there is nothing left to oscillate.
    //
    // `pageBitmapSizes` survives eviction (it is only cleared when the document changes), so a page
    // that has ever rendered knows its own shape; anything else borrows the first page that does,
    // since documents are near enough uniform. Only the very first page of a fresh document falls
    // through to the A4 guess.
    val placeholderAspect = if (bitmap != null) null else {
        (pageBitmapSizes[page] ?: pageBitmapSizes.values.firstOrNull { it.width > 0f && it.height > 0f })
            ?.takeIf { it.width > 0f && it.height > 0f }
            ?.let { it.width / it.height }
    }

    Box(
        Modifier
            // While an image is dragged across a page boundary, this page draws above its
            // neighbours so the image isn't hidden behind the next page.
            .zIndex(if (imageDragging) 1f else 0f)
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(if (bitmap == null) Modifier.aspectRatio(placeholderAspect ?: (1f / 1.414f)) else Modifier)
            .background(Color(0xFF15181E))
            // Page-local coordinates for the text selection's screen mapping (see PdfViewportTransform).
            .onGloballyPositioned { textSelection.registerPage(page, it) }
            .then(pageTextSemantics)
            .onSizeChanged { sz ->
                pageCanvasSizes[page] = Size(sz.width.toFloat(), sz.height.toFloat())
                if (bitmap != null) pageBitmapSizes[page] = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
            }
    ) {
        if (bitmap == null) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LiquidGlassColors.Blue, strokeWidth = 2.dp)
            }
            return@Box
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
            colorFilter = if (darkPageAppearance) {
                remember {
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
            } else null
        )

        // The image fills the box width and the box height follows it, so the content
        // frame is the full box.
        Canvas(Modifier.matchParentSize()) {
            val frame = Rect(0f, 0f, size.width, size.height)

            // Text selection highlight — the platform selection colour, one band per line fragment.
            // Drawn inside the zoom layer so it scales with the page, exactly like the glyphs.
            textSelection.pageRange(page)?.let { (from, to) ->
                textSelection.layout(page)?.selectionRects(from, to, size)?.forEach { r ->
                    drawRect(selectionColors.backgroundColor, r.topLeft, r.size)
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
                        val y = markup.textMarkupLineY(r) ?: return@let
                        drawLine(
                            color = markup.color.copy(markup.alpha),
                            start = Offset(r.left, y),
                            end = Offset(r.right, y),
                            strokeWidth = markup.width.coerceIn(2f, 4f),
                            cap = StrokeCap.Round
                        )
                    }
                    is PdfMarkup.TextEditMarkup -> {
                        // Preview of an in-place edit: the old line hidden under the page's own
                        // paper colour, the new text in the line's size, baseline and closest face.
                        // Dark reader inverts the page, so the preview inverts with it.
                        fun c(x: Color) = if (darkPageAppearance) Color(1f - x.red, 1f - x.green, 1f - x.blue, x.alpha) else x
                        val pad = markup.fontSize * 0.08f
                        drawRect(
                            c(markup.background),
                            Offset(markup.rect.left - pad, markup.rect.top - pad),
                            Size(markup.rect.width + pad * 2f, markup.rect.height + pad * 2f)
                        )
                        drawIntoCanvas { cv ->
                            val p = markup.previewPaint().apply { color = c(markup.color).toArgb() }
                            cv.nativeCanvas.drawText(markup.text, markup.rect.left, markup.baseline, p)
                        }
                    }
                    is PdfMarkup.ImageMarkup -> {
                        val r = Rect(min(markup.start.x, markup.end.x), min(markup.start.y, markup.end.y), max(markup.start.x, markup.end.x), max(markup.start.y, markup.end.y))
                        if (!markup.bitmap.isRecycled && markup.bitmap.width > 0) runCatching {
                            drawImage(
                                image = markup.bitmap.asImageBitmap(),
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = androidx.compose.ui.unit.IntSize(markup.bitmap.width, markup.bitmap.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1)),
                                // Dark reader inverts the page; a signature is ink on paper, so it
                                // inverts with it (dark ink -> light) instead of vanishing on black.
                                colorFilter = if (darkPageAppearance && markup.isSignature) InvertColorFilter else null
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
                        if (markup.text.isEmpty()) drawRect(LiquidGlassColors.Blue.copy(0.5f),
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

            if (activeTool == PdfEditTool.Lasso && draftLasso.size > 1) {
                val lassoPath = Path().apply {
                    moveTo(draftLasso.first().x, draftLasso.first().y)
                    draftLasso.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(lassoPath, Color(0xFF0A84FF).copy(alpha = 0.18f))
                drawPath(lassoPath, Color(0xFF0A84FF), style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            if (activeTool == PdfEditTool.None && selectedMarkupIndices.size > 1) {
                val groupBounds = selectedMarkupIndices.mapNotNull { marks.getOrNull(it)?.movableBounds() }
                    .reduceOrNull { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
                groupBounds?.let { b ->
                    drawRoundRect(Color(0xFF0A84FF), Offset(b.left - 8f, b.top - 8f),
                        Size(b.width + 16f, b.height + 16f), CornerRadius(12f, 12f), style = Stroke(3f))
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

                // OCR markups are anchored to extracted text rather than freely movable
                // geometry, so they get their own subtle focus ring instead of the generic
                // resize frame. The precise range remains visible and the action bubble below
                // supplies the delete affordance.
                marks.getOrNull(selectedMarkupIndex)
                    ?.takeIf { it is PdfMarkup.TextBlockHighlightMarkup || it is PdfMarkup.TextBlockLineMarkup }
                    ?.textMarkupRangeRect(ocrBlocks, frame)
                    ?.let { r ->
                        val focus = Color(0xFF0A84FF).copy(0.9f)
                        val bounds = if (marks[selectedMarkupIndex] is PdfMarkup.TextBlockLineMarkup) {
                            val y = marks[selectedMarkupIndex].textMarkupLineY(r) ?: r.bottom
                            Rect(r.left - 5f, y - 5f, r.right + 5f, y + 5f)
                        } else {
                            r.inflate(3f)
                        }
                        drawRoundRect(
                            focus,
                            bounds.topLeft,
                            bounds.size,
                            CornerRadius(6f, 6f),
                            style = Stroke(2f)
                        )
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
                    val currentSelected = latestSelectedMarkupIndex
                    val currentGroup = latestSelectedMarkupIndices
                    val currentBlocks = latestOcrBlocks
                    val sel = marks.getOrNull(currentSelected)?.takeIf { it.isTransformable() }
                    val selBounds = sel?.movableBounds()
                    if (selBounds != null && selBounds.inflate(30f).contains(down.position)) return@awaitEachGesture

                    val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    val idx = marks.indexOfLast { it.hitTest(down.position, currentBlocks, frame) }
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
                                is PdfMarkup.StrokeMarkup,
                                is PdfMarkup.TextBlockHighlightMarkup,
                                is PdfMarkup.TextBlockLineMarkup,
                                is PdfMarkup.TextEditMarkup -> onSelectMarkup(idx)
                                else -> Unit
                            }
                            onShowControls()
                            onInteraction()
                        }
                    } else {
                        // Missed everything → clear any selection (tap propagates to container).
                        if (currentSelected >= 0) onSelectMarkup(-1)
                        else if (currentGroup.isNotEmpty()) onSelectMarkups(emptySet())
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
            // The hit box is FROZEN for the whole gesture. It used to follow the markup every frame,
            // so each drag delta (measured in the box's own, moving coordinates) was skewed by the
            // box's own movement — the text "swam" under the finger and resizes overshot (#48).
            // The pointer stays captured by the box even when the finger leaves it, so a frozen
            // box loses nothing.
            var frozen by remember(page, selectedMarkupIndex) { mutableStateOf<Rect?>(null) }
            val hitBounds = frozen ?: selXfBounds
            val boxL = hitBounds.left - pad
            val boxT = hitBounds.top - pad
            val boxW = hitBounds.width + pad * 2
            val boxH = hitBounds.height + pad * 2
            Box(
                Modifier
                    .offset { IntOffset(boxL.roundToInt(), boxT.roundToInt()) }
                    .size(with(density2) { boxW.toDp() }, with(density2) { boxH.toDp() })
                    .pointerInput(page, selectedMarkupIndex) {
                        var mode = 0 // 1 = move, 2 = resize
                        // Everything is computed from the gesture's START state plus the TOTAL drag,
                        // never accumulated frame-to-frame, so the result can't drift.
                        var startMarkup: PdfMarkup? = null
                        var startBounds: Rect? = null
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { local ->
                                val cur = marks.getOrNull(selectedMarkupIndex)
                                val bb = cur?.movableBounds()
                                startMarkup = cur
                                startBounds = bb
                                frozen = bb
                                total = Offset.Zero
                                val pPage = Offset(local.x + (bb?.left ?: 0f) - pad, local.y + (bb?.top ?: 0f) - pad)
                                mode = if (bb != null && cur.isResizable() && (pPage - bb.bottomRight).getDistance() <= 64f) 2 else 1
                                onInteraction()
                            },
                            onDrag = { ch, drag ->
                                if (mode == 0) return@detectDragGestures
                                ch.consume()
                                val m0 = startMarkup ?: return@detectDragGestures
                                val b0 = startBounds ?: return@detectDragGestures
                                total += drag
                                marks[selectedMarkupIndex] = if (mode == 2) m0.resizedFrom(b0, total) else m0.translated(total)
                                onInteraction()
                            },
                            onDragEnd = { mode = 0; frozen = null },
                            onDragCancel = { mode = 0; frozen = null }
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
                detectTapGestures {
                    p ->
                    val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    val idx = marks.indexOfLast { it.hitTest(p, ocrBlocks, frame) }
                    if (idx >= 0) marks.removeAt(idx)
                    onInteraction()
                }
            }.pointerInput(page) {
                detectDragGestures(onDrag = { ch, _ ->
                    ch.consume()
                    val frame = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    val idx = marks.indexOfLast { it.hitTest(ch.position, ocrBlocks, frame) }
                    if (idx >= 0) marks.removeAt(idx)
                    onInteraction()
                })
            })
        }

        if (activeTool == PdfEditTool.Lasso) {
            Box(Modifier.matchParentSize().pointerInput(page, activeTool, marks.size) {
                detectDragGestures(
                    onDragStart = { start -> onInteraction(); draftLasso = listOf(start) },
                    onDrag = { change, _ ->
                        change.consume()
                        draftLasso = draftLasso + change.position
                    },
                    onDragCancel = { draftLasso = emptyList() },
                    onDragEnd = {
                        val selection = marks.indices.filter { marks[it].intersectsLasso(draftLasso) }.toSet()
                        draftLasso = emptyList()
                        onSelectMarkup(-1)
                        onSelectMarkups(selection)
                        onActiveToolChanged(PdfEditTool.None)
                        onShowControls()
                        onInteraction()
                    }
                )
            })
        }

        if (activeTool == PdfEditTool.None && selectedMarkupIndices.size > 1) {
            val bounds = selectedMarkupIndices.mapNotNull { marks.getOrNull(it)?.movableBounds() }
                .reduceOrNull { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }
            bounds?.let { group ->
                val density2 = LocalDensity.current
                Box(
                    Modifier.offset { IntOffset(group.left.roundToInt(), group.top.roundToInt()) }
                        .size(with(density2) { group.width.coerceAtLeast(1f).toDp() }, with(density2) { group.height.coerceAtLeast(1f).toDp() })
                        .pointerInput(page, selectedMarkupIndices) {
                            detectDragGestures(onDragStart = { onInteraction() }, onDrag = { change, drag ->
                                change.consume()
                                onMoveMarkups(selectedMarkupIndices, drag)
                                onInteraction()
                            })
                        }
                )
            }
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
                    // Ends a move: an image whose centre left the page hops to the neighbouring page
                    // (signatures can be carried down to the page they belong on); otherwise it is
                    // settled back inside this page.
                    fun settle() {
                        imageDragging = false
                        val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                        val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup ?: return
                        val ph = size.height.toFloat()
                        val cy = (img.start.y + img.end.y) / 2f
                        val target = when { cy > ph -> page + 1; cy < 0f -> page - 1; else -> null }
                        if (target != null && onMoveImageToPage(page, img, target)) return
                        val ih = img.end.y - img.start.y
                        val ny = img.start.y.coerceIn(0f, (ph - ih).coerceAtLeast(0f))
                        marks[idx] = img.copy(start = Offset(img.start.x, ny), end = Offset(img.end.x, ny + ih))
                    }
                    detectDragGestures(
                        onDragStart = { p ->
                            val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                            val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup
                            // Generous grab radius around the bottom-right handle (Apple-style
                            // touch target much larger than the visual handle).
                            resizing = img != null && (p - img.end).getDistance() <= 64f; onInteraction()
                            imageDragging = !resizing
                        },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onDrag = { ch, drag ->
                            ch.consume()
                            val idx = marks.indexOfLast { it is PdfMarkup.ImageMarkup && it.id == activeImageId }
                            val img = marks.getOrNull(idx) as? PdfMarkup.ImageMarkup ?: return@detectDragGestures
                            val pw = size.width.toFloat(); val ph = size.height.toFloat()
                            marks[idx] = if (resizing) {
                                img.copy(end = Offset(
                                    (img.end.x + drag.x).coerceIn(img.start.x + 24f, pw),
                                    (img.end.y + drag.y).coerceIn(img.start.y + 24f, ph)
                                ))
                            } else {
                                // Horizontal stays on the page; vertical may cross into the next or
                                // previous page (this page draws on top while dragging).
                                val iw = img.end.x - img.start.x; val ih = img.end.y - img.start.y
                                val nx = (img.start.x + drag.x).coerceIn(0f, (pw - iw).coerceAtLeast(0f))
                                val ny = (img.start.y + drag.y).coerceIn(-ih, ph)
                                img.copy(start = Offset(nx, ny), end = Offset(nx + iw, ny + ih))
                            }
                            onInteraction()
                        }
                    )
                }
            )
        }

        val csz = pageCanvasSizes[page]

        // ── Contextual Edit / Delete bar for the selected shape / text / note ──────
        // Published to the viewer, which draws it outside the content layer (see MarkupBarHost).
        if (activeTool == PdfEditTool.None && csz != null && csz.width > 0f) {
            marks.getOrNull(selectedMarkupIndex)?.takeIf { it.isTransformable() }?.let { selM ->
                selM.movableBounds()?.let { b ->
                    PublishMarkupBar(
                        host = markupBar,
                        page = page,
                        token = selectedMarkupIndex,
                        anchor = b,
                        onEdit = {
                            when (selM) {
                                is PdfMarkup.TextBoxMarkup -> onEditAnnotation(selM.id)
                                is PdfMarkup.NoteMarkup    -> onEditAnnotation(selM.id)
                                else -> onEditShape(selectedMarkupIndex)
                            }
                        },
                        onDelete = { onDeleteMarkup(selectedMarkupIndex) },
                        onDismiss = { onSelectMarkup(-1) }
                    )
                }
            }

            // An edited line of the page's own text: Edit reopens the editor, Delete restores the
            // original line.
            (marks.getOrNull(selectedMarkupIndex) as? PdfMarkup.TextEditMarkup)?.let { te ->
                PublishMarkupBar(
                    host = markupBar,
                    page = page,
                    token = selectedMarkupIndex,
                    anchor = te.rect,
                    onEdit = { onEditAnnotation(te.id) },
                    onDelete = { onDeleteMarkup(selectedMarkupIndex) },
                    onDismiss = { onSelectMarkup(-1) }
                )
            }

            // Text highlights, underlines, and strike-throughs are precise OCR ranges, not
            // transformable shapes. When one is tapped, expose the same clear destructive action
            // used by the other professional markup tools.
            marks.getOrNull(selectedMarkupIndex)
                ?.takeIf { it is PdfMarkup.TextBlockHighlightMarkup || it is PdfMarkup.TextBlockLineMarkup }
                ?.textMarkupRangeRect(ocrBlocks, Rect(0f, 0f, csz.width, csz.height))
                ?.let { rangeRect ->
                    val selected = marks[selectedMarkupIndex]
                    val anchorRect = if (selected is PdfMarkup.TextBlockLineMarkup) {
                        val y = selected.textMarkupLineY(rangeRect) ?: rangeRect.bottom
                        Rect(rangeRect.left, y - 4f, rangeRect.right, y + 4f)
                    } else rangeRect
                    PublishMarkupBar(
                        host = markupBar,
                        page = page,
                        token = selectedMarkupIndex,
                        anchor = anchorRect,
                        onEdit = { onEditShape(selectedMarkupIndex) },
                        onDelete = { onDeleteMarkup(selectedMarkupIndex) },
                        onDismiss = { onSelectMarkup(-1) }
                    )
                }
        }
    }
}

private val InvertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private val MarkupBarWidth = 236.dp
private val MarkupBarHeight = 48.dp

/**
 * Hand-off for the contextual Edit / Delete bar. The page owning the selected markup publishes where
 * it is ([PublishMarkupBar]); the viewer draws the bar ([PdfMarkupBarLayer]) as a sibling of its
 * content layer, beside the text-selection toolbar.
 *
 * Why: drawn inside the page, the bar was inside the very layer the viewer's glass records, so it
 * could only sample the wallpaper — with the default flat background that is one grey colour, which
 * is why it read as a flat pill instead of glass. Outside, it refracts the live page like the title
 * chips refract the home screen. It also stops scaling with zoom: a floating control keeps its size.
 */
@Stable
internal class MarkupBarHost {
    internal var owner by mutableStateOf<Any?>(null)
    internal var token by mutableIntStateOf(-1)
    internal var page by mutableIntStateOf(-1)
    /** The markup's bounds in the owning page's px (its layout coordinates). Read at layout only. */
    internal var anchor by mutableStateOf(Rect.Zero)
    internal var onEdit: () -> Unit = {}
    internal var onDelete: () -> Unit = {}
    internal var onDismiss: () -> Unit = {}
}

@Composable
private fun PublishMarkupBar(
    host: MarkupBarHost,
    page: Int,
    token: Int,
    anchor: Rect,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val key = remember { Any() }
    // Plain fields for the callbacks (read at click time); state for what the bar lays out from.
    // Same-value writes are no-ops, so an idle recomposition invalidates nothing.
    SideEffect {
        host.onEdit = onEdit
        host.onDelete = onDelete
        host.onDismiss = onDismiss
        host.page = page
        host.token = token
        host.anchor = anchor
        host.owner = key
    }
    DisposableEffect(host) { onDispose { if (host.owner === key) host.owner = null } }
}

/**
 * Viewer-level Edit / Delete / dismiss capsule for the markup a page published to [host], in the
 * title-chip glass ([com.chethan616.clearpdf.ui.components.chipGlass]) with ink picked from the page
 * behind it. Fills the viewer; only the capsule takes touches.
 *
 * Placement follows the platform's floating toolbar: centred above the markup, below it when there is
 * no room, clamped clear of the screen edges and system bars; it follows the markup through scroll,
 * zoom and drag (geometry is read at layout, so none of that recomposes).
 *
 * Motion: springs in with a real overshoot on scale from the markup's side while alpha stays
 * critically damped; selecting another markup gives it a small "boop" in place; it settles away
 * without a wobble.
 */
@Composable
internal fun PdfMarkupBarLayer(
    host: MarkupBarHost,
    textSelection: PdfTextSelectionState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    backdrop: Backdrop,
    luminanceAt: (top: Float, bottom: Float) -> Float,
    modifier: Modifier = Modifier,
    /** Anything that changes what the page looks like (dark reader): re-picks the ink live. */
    appearanceKey: Any? = null
) {
    val owner = host.owner
    val shown = owner != null
    val density = LocalDensity.current
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val scale = remember { androidx.compose.animation.core.Animatable(0.72f) }
    var onLight by remember { mutableStateOf(true) }
    val currentLuminance by rememberUpdatedState(luminanceAt)
    // Side of the markup the bar sits on; written at layout, reset to "above" per new selection.
    val placedAbove = remember { booleanArrayOf(true) }
    LaunchedEffect(owner, host.token, appearanceKey) {
        val origin = textSelection.pageOrigin(host.page) ?: return@LaunchedEffect
        if (owner == null) return@LaunchedEffect
        val r = textSelection.transform.pageRectToScreen(host.anchor, origin)
        val pad = with(density) { 72.dp.toPx() }
        onLight = currentLuminance(r.top - pad, r.bottom + pad) > 0.6f
    }
    LaunchedEffect(owner, host.token) {
        placedAbove[0] = true
        if (owner == null) {
            launch { scale.animateTo(0.9f, com.chethan616.clearpdf.ui.components.GlassMotion.settle()) }
            alpha.animateTo(0f, com.chethan616.clearpdf.ui.components.GlassMotion.settle())
            return@LaunchedEffect
        }
        // Ink from the page around the markup (the bar may land above or below it).
        val origin = textSelection.pageOrigin(host.page)
        if (origin != null) {
            val r = textSelection.transform.pageRectToScreen(host.anchor, origin)
            val pad = with(density) { 72.dp.toPx() }
            onLight = currentLuminance(r.top - pad, r.bottom + pad) > 0.6f
        }
        // First appearance pops from 0.72; a new selection while visible gives a smaller boop.
        if (alpha.value > 0.5f) scale.snapTo(0.9f) else scale.snapTo(0.72f)
        launch { alpha.animateTo(1f, com.chethan616.clearpdf.ui.components.GlassMotion.fade()) }
        scale.animateTo(1f, com.chethan616.clearpdf.ui.components.GlassMotion.pop())
    }
    if (!shown && alpha.value <= 0.01f) return

    val glass by androidx.compose.animation.animateColorAsState(
        com.chethan616.clearpdf.ui.components.chipGlass(onLight),
        com.chethan616.clearpdf.ui.components.GlassMotion.fade(),
        label = "markupBarGlass"
    )
    val ink by androidx.compose.animation.animateColorAsState(
        com.chethan616.clearpdf.ui.components.chipInk(onLight),
        com.chethan616.clearpdf.ui.components.GlassMotion.fade(),
        label = "markupBarInk"
    )
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density)
    val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)

    androidx.compose.ui.layout.Layout(
        content = {
            MarkupActionBar(
                backdrop = backdrop,
                glass = glass,
                ink = ink,
                onEdit = { host.onEdit() },
                onDelete = { host.onDelete() },
                onDismiss = { host.onDismiss() }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { measurables, constraints ->
        val bar = measurables.first().measure(androidx.compose.ui.unit.Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Subscribe the placement (not composition) to scroll and zoom.
            listState.firstVisibleItemIndex; listState.firstVisibleItemScrollOffset
            val t = textSelection.transform
            val origin = textSelection.pageOrigin(host.page) ?: return@layout
            val r = t.pageRectToScreen(host.anchor, origin)
            val margin = 12.dp.toPx()
            val gap = 12.dp.toPx()
            val topSafe = statusTop + margin
            val bottomSafe = constraints.maxHeight - navBottom - margin
            val aboveY = r.top - gap - bar.height
            // Decide the side once per appearance-ish: only flip when the preferred side truly
            // stops fitting, so a markup dragged near the edge doesn't make the bar hop.
            val fitsAbove = aboveY >= topSafe
            val fitsBelow = r.bottom + gap + bar.height <= bottomSafe
            val above = if (placedAbove[0]) fitsAbove || !fitsBelow else !fitsBelow && fitsAbove
            placedAbove[0] = above
            val y = (if (above) aboveY else r.bottom + gap)
                .coerceIn(topSafe, (bottomSafe - bar.height).coerceAtLeast(topSafe))
            val x = (r.center.x - bar.width / 2f)
                .coerceIn(margin, (constraints.maxWidth - bar.width - margin).coerceAtLeast(margin))
            val pivotX = if (bar.width > 0) ((r.center.x - x) / bar.width).coerceIn(0f, 1f) else 0.5f
            bar.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                this.alpha = alpha.value.coerceIn(0f, 1f)
                scaleX = scale.value; scaleY = scale.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, if (above) 1f else 0f)
            }
        }
    }
}

/**
 * The Edit / Delete / dismiss capsule itself, in the title-chip glass: [glass] tint over the lensed
 * live page, [ink] chosen for the page behind it. Dismissal also works by tapping anywhere else in the
 * viewer or pressing Back.
 */
@Composable
private fun MarkupActionBar(
    backdrop: Backdrop,
    glass: Color,
    ink: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val red = com.chethan616.clearpdf.ui.theme.LiquidGlassColors.Red
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier
            .width(MarkupBarWidth)
            .height(MarkupBarHeight)
            .viewerGlass(backdrop, glass, shape = { com.kyant.shapes.Capsule })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarkupBarItem(Icons.Rounded.Edit, stringResource(R.string.edit), ink, Modifier.weight(1f)) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
            onEdit()
        }
        Box(Modifier.width(1.dp).height(22.dp).background(ink.copy(0.12f)))
        MarkupBarItem(Icons.Rounded.Delete, stringResource(R.string.delete), red, Modifier.weight(1f)) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onDelete()
        }
        Box(Modifier.width(1.dp).height(22.dp).background(ink.copy(0.12f)))
        MarkupBarItem(Icons.Rounded.Close, stringResource(R.string.done), ink.copy(0.85f), Modifier.width(44.dp), iconOnly = true) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
            onDismiss()
        }
    }
}

/** One pill of [MarkupActionBar]: presses down and springs back, like [LiquidButton]'s deformation —
 *  the old plain `clickable` had no press feedback at all, the one thing that most read as "not a
 *  real button" next to ShareMorph and the rest of the toolbar. */
@Composable
private fun MarkupBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val jelly = com.chethan616.clearpdf.ui.components.rememberJellyPress(interaction)
    // A soft tinted well under the finger, like the selection toolbar's items.
    val wash by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.12f else 0f,
        com.chethan616.clearpdf.ui.components.GlassMotion.fade(),
        label = "markupItemWash"
    )
    Row(
        modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            // Draw-time only: sliding a finger across squashes the pill along the drag, like a drop
            // of the glass it sits on.
            .liquidStretchOnDrag(stretchFactor = 0.18f, minScale = 0.88f, maxScale = 1.12f)
            .graphicsLayer { scaleX = jelly.scale; scaleY = jelly.scale }
            .clip(com.kyant.shapes.Capsule)
            .drawBehind { if (wash > 0f) drawRect(color.copy(alpha = wash)) }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (iconOnly) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, if (iconOnly) label else null, Modifier.size(17.dp), color)
        if (!iconOnly) BasicText(label, style = TextStyle(color, 14.sp, FontWeight.SemiBold), maxLines = 1)
    }
}

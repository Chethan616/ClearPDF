package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import com.chethan616.clearpdf.ui.viewmodel.ExportOverlay
import com.chethan616.clearpdf.ui.viewmodel.FindMatch
import com.chethan616.clearpdf.ui.viewmodel.NormalizedPoint
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ── Viewer local enums ────────────────────────────────────────────────────────

internal enum class ScrollOrientation { Vertical, Horizontal }

internal enum class PdfEditTool { None, Draw, Highlight, Rect, Ellipse, Line, Arrow, Lasso, Image, Eraser, Text, Note }

internal enum class ViewerToolbarMode { Main, Drawing, Selection, Image, Eraser, Search, Signature }

// ── PdfMarkup — on-canvas annotation model ────────────────────────────────────

internal sealed class PdfMarkup {
    data class StrokeMarkup(
        val points: List<Offset>,
        val color: Color,
        val width: Float,
        val alpha: Float = 1f
    ) : PdfMarkup()

    data class RectMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val alpha: Float = 1f,
        val filled: Boolean = false
    ) : PdfMarkup()

    data class OvalMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val alpha: Float = 1f,
        val filled: Boolean = false
    ) : PdfMarkup()

    data class LineMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val width: Float = 3f,
        val alpha: Float = 1f,
        val arrowHead: Boolean = false
    ) : PdfMarkup()

    data class TextBlockHighlightMarkup(
        val blockId: String,
        val color: Color,
        val alpha: Float = 0.30f,
        val start: Int = 0,
        val end: Int = -1
    ) : PdfMarkup()

    data class TextBlockLineMarkup(
        val blockId: String,
        val color: Color,
        val width: Float = 3f,
        val alpha: Float = 1f,
        val strikeThrough: Boolean = false,
        val start: Int = 0,
        val end: Int = -1
    ) : PdfMarkup()

    data class ImageMarkup(
        val id: Long,
        val bitmap: Bitmap,
        val start: Offset,
        val end: Offset,
        val isSignature: Boolean = false
    ) : PdfMarkup()

    /** Inserted text box. [position] is content-space top-left; [fontSize] is content px. */
    data class TextBoxMarkup(
        val id: Long,
        val position: Offset,
        val text: String,
        val color: Color,
        val fontSize: Float = 40f
    ) : PdfMarkup()

    /** Sticky note. [anchor] is the content-space top-left of the icon. */
    data class NoteMarkup(
        val id: Long,
        val anchor: Offset,
        val text: String,
        val color: Color
    ) : PdfMarkup()

    fun hitTest(p: Offset): Boolean = when (this) {
        is StrokeMarkup -> points.any { (it - p).getDistance() <= width.coerceAtLeast(16f) }
        is RectMarkup -> {
            val r = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
            r.contains(p)
        }
        is OvalMarkup -> {
            val r = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
            r.contains(p)
        }
        is LineMarkup -> {
            val d = distToSegment(p, start, end)
            d <= width.coerceAtLeast(16f)
        }
        // OCR-anchored markups are hit-tested with the page's OCR geometry. Keep the
        // geometry-free overload conservative so callers that do not have that geometry
        // cannot accidentally select a whole page-sized annotation.
        is TextBlockHighlightMarkup -> false
        is TextBlockLineMarkup      -> false
        is ImageMarkup -> {
            val r = Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
            r.contains(p)
        }
        is TextBoxMarkup -> {
            val lines = if (text.isEmpty()) 1 else text.split("\n").size
            val w = (text.split("\n").maxOfOrNull { it.length } ?: 1).coerceAtLeast(1) * fontSize * 0.6f
            val r = Rect(position.x - 6f, position.y - 6f, position.x + w + 6f, position.y + fontSize * 1.2f * lines + 6f)
            r.contains(p)
        }
        is NoteMarkup -> {
            val r = Rect(anchor.x - 8f, anchor.y - 8f, anchor.x + 40f, anchor.y + 40f)
            r.contains(p)
        }
    }
}

/** Recolourable free-form shapes (as opposed to images / OCR-anchored / text markups). */
internal fun PdfMarkup.isShape(): Boolean = this is PdfMarkup.StrokeMarkup ||
    this is PdfMarkup.RectMarkup || this is PdfMarkup.OvalMarkup || this is PdfMarkup.LineMarkup

/** Anything [ShapeEditorPopup] can recolour — shapes plus OCR-anchored highlight/underline/strike. */
internal fun PdfMarkup.isRecolorable(): Boolean = isShape() ||
    this is PdfMarkup.TextBlockHighlightMarkup || this is PdfMarkup.TextBlockLineMarkup

/** Markups that support the generic select → move / resize transform (everything the user
 *  places freely, except images which have their own dedicated toolbar path). */
internal fun PdfMarkup.isTransformable(): Boolean = this is PdfMarkup.StrokeMarkup ||
    this is PdfMarkup.RectMarkup || this is PdfMarkup.OvalMarkup || this is PdfMarkup.LineMarkup ||
    this is PdfMarkup.TextBoxMarkup || this is PdfMarkup.NoteMarkup

/** Whether a bottom-right resize handle applies (notes are a fixed-size icon → move only). */
internal fun PdfMarkup.isResizable(): Boolean = isTransformable() && this !is PdfMarkup.NoteMarkup

/** Content-space bounding box used for the selection frame + hit-testing during transform. */
internal fun PdfMarkup.movableBounds(): Rect? = when (this) {
    is PdfMarkup.StrokeMarkup -> {
        if (points.isEmpty()) null else {
            var l = points[0].x; var t = points[0].y; var r = l; var b = t
            points.forEach { l = min(l, it.x); t = min(t, it.y); r = max(r, it.x); b = max(b, it.y) }
            Rect(l, t, max(r, l + 1f), max(b, t + 1f))
        }
    }
    is PdfMarkup.RectMarkup -> Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    is PdfMarkup.OvalMarkup -> Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    is PdfMarkup.LineMarkup -> Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x) + 1f, max(start.y, end.y) + 1f)
    is PdfMarkup.ImageMarkup -> Rect(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
    is PdfMarkup.TextBoxMarkup -> {
        val lines = if (text.isEmpty()) 1 else text.split("\n").size
        val w = ((text.split("\n").maxOfOrNull { it.length } ?: 1).coerceAtLeast(3)) * fontSize * 0.6f
        Rect(position.x, position.y, position.x + w, position.y + fontSize * 1.2f * lines)
    }
    is PdfMarkup.NoteMarkup -> Rect(anchor.x, anchor.y, anchor.x + 30f, anchor.y + 30f)
    else -> null
}

/** Returns true when any part of a lasso polygon covers the markup's visible bounds. */
internal fun PdfMarkup.intersectsLasso(points: List<Offset>): Boolean {
    if (points.size < 3) return false
    if (this is PdfMarkup.StrokeMarkup) {
        if (this.points.any { it.isInsidePolygon(points) }) return true
        if (this.points.zipWithNext().any { (a, b) -> polygonEdges(points).any { (c, d) -> segmentsIntersect(a, b, c, d) } }) return true
    }
    val bounds = movableBounds() ?: return false
    val corners = listOf(bounds.topLeft, Offset(bounds.right, bounds.top), bounds.bottomRight, Offset(bounds.left, bounds.bottom))
    return corners.any { it.isInsidePolygon(points) } ||
        points.any { bounds.contains(it) } ||
        polygonEdges(points).any { (a, b) ->
            val edges = listOf(
                bounds.topLeft to Offset(bounds.right, bounds.top),
                Offset(bounds.right, bounds.top) to bounds.bottomRight,
                bounds.bottomRight to Offset(bounds.left, bounds.bottom),
                Offset(bounds.left, bounds.bottom) to bounds.topLeft
            )
            edges.any { (c, d) -> segmentsIntersect(a, b, c, d) }
        } ||
        bounds.contains(Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size))
}

private fun polygonEdges(points: List<Offset>): List<Pair<Offset, Offset>> =
    points.indices.map { i -> points[i] to points[(i + 1) % points.size] }

private fun segmentsIntersect(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    fun cross(p: Offset, q: Offset, r: Offset) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    fun within(v: Float, p: Float, q: Float) = v >= min(p, q) && v <= max(p, q)
    fun onSegment(p: Offset, q: Offset, r: Offset) =
        within(q.x, p.x, r.x) && within(q.y, p.y, r.y)

    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    if (((abC > 0f && abD < 0f) || (abC < 0f && abD > 0f)) &&
        ((cdA > 0f && cdB < 0f) || (cdA < 0f && cdB > 0f))) return true
    return (abC == 0f && onSegment(a, c, b)) || (abD == 0f && onSegment(a, d, b)) ||
        (cdA == 0f && onSegment(c, a, d)) || (cdB == 0f && onSegment(c, b, d))
}

private fun Offset.isInsidePolygon(polygon: List<Offset>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / ((b.y - a.y).takeIf { it != 0f } ?: 0.0001f) + a.x) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/** Translate a markup by [d] (move). */
internal fun PdfMarkup.translated(d: Offset): PdfMarkup = when (this) {
    is PdfMarkup.StrokeMarkup  -> copy(points = points.map { it + d })
    is PdfMarkup.RectMarkup    -> copy(start = start + d, end = end + d)
    is PdfMarkup.OvalMarkup    -> copy(start = start + d, end = end + d)
    is PdfMarkup.LineMarkup    -> copy(start = start + d, end = end + d)
    is PdfMarkup.ImageMarkup   -> copy(start = start + d, end = end + d)
    is PdfMarkup.TextBoxMarkup -> copy(position = position + d)
    is PdfMarkup.NoteMarkup    -> copy(anchor = anchor + d)
    else -> this
}

/** Resize a markup by dragging its bottom-right handle [drag], given its current [bounds]. */
internal fun PdfMarkup.resizedBy(drag: Offset, bounds: Rect): PdfMarkup = when (this) {
    is PdfMarkup.RectMarkup -> copy(
        start = bounds.topLeft,
        end = Offset((bounds.right + drag.x).coerceAtLeast(bounds.left + 8f), (bounds.bottom + drag.y).coerceAtLeast(bounds.top + 8f))
    )
    is PdfMarkup.OvalMarkup -> copy(
        start = bounds.topLeft,
        end = Offset((bounds.right + drag.x).coerceAtLeast(bounds.left + 8f), (bounds.bottom + drag.y).coerceAtLeast(bounds.top + 8f))
    )
    is PdfMarkup.LineMarkup -> {
        // Move whichever endpoint sits nearer the bottom-right handle.
        val br = bounds.bottomRight
        if ((end - br).getDistance() <= (start - br).getDistance()) copy(end = end + drag) else copy(start = start + drag)
    }
    is PdfMarkup.StrokeMarkup -> {
        // Uniform scale about the top-left so the stroke keeps its shape.
        val pivot = bounds.topLeft
        val fx = ((bounds.width + drag.x) / bounds.width.coerceAtLeast(1f)).coerceIn(0.2f, 8f)
        val fy = ((bounds.height + drag.y) / bounds.height.coerceAtLeast(1f)).coerceIn(0.2f, 8f)
        val f = (fx + fy) / 2f
        copy(points = points.map { pivot + (it - pivot) * f })
    }
    is PdfMarkup.TextBoxMarkup -> {
        val factor = ((bounds.height + drag.y) / bounds.height.coerceAtLeast(1f)).coerceIn(0.3f, 6f)
        copy(fontSize = (fontSize * factor).coerceIn(10f, 400f))
    }
    else -> this
}

internal fun PdfMarkup.shapeColor(): Color = when (this) {
    is PdfMarkup.StrokeMarkup -> color
    is PdfMarkup.RectMarkup   -> color
    is PdfMarkup.OvalMarkup   -> color
    is PdfMarkup.LineMarkup   -> color
    is PdfMarkup.TextBlockHighlightMarkup -> color
    is PdfMarkup.TextBlockLineMarkup      -> color
    else -> Color(0xFF1976D2)
}

internal fun PdfMarkup.recolored(c: Color): PdfMarkup = when (this) {
    is PdfMarkup.StrokeMarkup -> copy(color = c)
    is PdfMarkup.RectMarkup   -> copy(color = c)
    is PdfMarkup.OvalMarkup   -> copy(color = c)
    is PdfMarkup.LineMarkup   -> copy(color = c)
    is PdfMarkup.TextBlockHighlightMarkup -> copy(color = c)
    is PdfMarkup.TextBlockLineMarkup      -> copy(color = c)
    else -> this
}

// ── Geometry helpers ──────────────────────────────────────────────────────────

internal fun fitBitmapRect(canvasSize: Size, bitmapW: Float, bitmapH: Float): Rect {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f || bitmapW <= 0f || bitmapH <= 0f)
        return Rect(0f, 0f, canvasSize.width, canvasSize.height)

    val canvasRatio = canvasSize.width / canvasSize.height
    val bitmapRatio = bitmapW / bitmapH

    val (w, h) = if (canvasRatio > bitmapRatio) {
        val h1 = canvasSize.height
        val w1 = h1 * bitmapRatio
        Pair(w1, h1)
    } else {
        val w1 = canvasSize.width
        val h1 = w1 / bitmapRatio
        Pair(w1, h1)
    }
    val left = (canvasSize.width - w) / 2f
    val top  = (canvasSize.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

internal fun screenToContent(
    screen: Offset,
    zoomScale: Float,
    panOffset: Offset,
    boxCenter: Offset
): Offset {
    val unpanned = screen - panOffset
    val rel      = unpanned - boxCenter
    return (rel / zoomScale) + boxCenter
}

internal fun clampPanOffset(
    pan: Offset,
    scale: Float,
    canvasSize: Size,
    bitmapSize: Size
): Offset {
    if (scale <= 1.01f || canvasSize.width <= 0f || canvasSize.height <= 0f) return Offset.Zero
    val frame = fitBitmapRect(canvasSize, bitmapSize.width, bitmapSize.height)
    val maxPanX = (frame.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxPanY = (frame.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(
        pan.x.coerceIn(-maxPanX, maxPanX),
        pan.y.coerceIn(-maxPanY, maxPanY)
    )
}

internal fun ocrBlockToRect(block: OcrTextBlock, frame: Rect): Rect = Rect(
    frame.left + block.left * frame.width,
    frame.top  + block.top  * frame.height,
    frame.left + block.right * frame.width,
    frame.top  + block.bottom * frame.height
)

internal fun ocrTextRangeToRect(block: OcrTextBlock, range: OcrTextRange, frame: Rect): Rect {
    val start = range.start.coerceIn(0, block.text.length)
    val end = (if (range.end < 0) block.text.length else range.end)
        .coerceIn(start, block.text.length)
    if (start >= end || block.charLefts.size < end || block.charRights.size < end) {
        return ocrBlockToRect(block, frame)
    }
    return Rect(
        frame.left + block.charLefts[start] * frame.width,
        frame.top + block.top * frame.height,
        frame.left + block.charRights[end - 1] * frame.width,
        frame.top + block.bottom * frame.height
    )
}

/** Exact page-space bounds for an OCR-anchored markup. */
internal fun PdfMarkup.textMarkupRangeRect(
    blocks: List<OcrTextBlock>,
    frame: Rect
): Rect? = when (this) {
    is PdfMarkup.TextBlockHighlightMarkup -> blocks.firstOrNull { it.id == blockId }?.let { block ->
        expandedTextHighlightRect(
            ocrTextRangeToRect(block, OcrTextRange(blockId, start, end), frame)
        )
    }
    is PdfMarkup.TextBlockLineMarkup -> blocks.firstOrNull { it.id == blockId }?.let { block ->
        ocrTextRangeToRect(block, OcrTextRange(blockId, start, end), frame)
    }
    else -> null
}

/** Baseline-aware y-position used by both the on-screen renderer and PDF export. */
internal fun PdfMarkup.textMarkupLineY(rangeRect: Rect): Float? = when (this) {
    is PdfMarkup.TextBlockLineMarkup -> if (strikeThrough) {
        rangeRect.center.y
    } else {
        // PdfTextService exposes the glyph baseline as `bottom`. A small gap keeps the
        // underline below descenders instead of cutting through the glyphs, as the old
        // `bottom - 10%` placement did.
        rangeRect.bottom + (rangeRect.height * 0.08f).coerceIn(1.5f, 6f)
    }
    else -> null
}

/** A forgiving touch target around a precise OCR markup, matching professional PDF tools. */
internal fun PdfMarkup.textMarkupHitBounds(
    blocks: List<OcrTextBlock>,
    frame: Rect
): Rect? {
    val rangeRect = textMarkupRangeRect(blocks, frame) ?: return null
    return when (this) {
        is PdfMarkup.TextBlockHighlightMarkup -> rangeRect.inflate(8f)
        is PdfMarkup.TextBlockLineMarkup -> {
            val y = textMarkupLineY(rangeRect) ?: return null
            val verticalTouch = max(14f, width * 3f)
            Rect(
                rangeRect.left - 12f,
                y - verticalTouch,
                rangeRect.right + 12f,
                y + verticalTouch
            )
        }
        else -> null
    }
}

/** Hit-testing overload for OCR markups; other annotations retain their existing behavior. */
internal fun PdfMarkup.hitTest(
    point: Offset,
    blocks: List<OcrTextBlock>,
    frame: Rect
): Boolean = when (this) {
    is PdfMarkup.TextBlockHighlightMarkup,
    is PdfMarkup.TextBlockLineMarkup -> textMarkupHitBounds(blocks, frame)?.contains(point) == true
    else -> hitTest(point)
}

internal fun expandedTextHighlightRect(rect: Rect, verticalScale: Float = 1f): Rect {
    val padX = (rect.height * 0.05f).coerceIn(0.75f, 3f)
    val padTop = rect.height * 0.13f * verticalScale
    val padBottom = rect.height * 0.11f * verticalScale
    return Rect(rect.left - padX, rect.top - padTop, rect.right + padX, rect.bottom + padBottom)
}

internal fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b - a).getDistanceSq()
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    return (p - proj).getDistance()
}

internal fun Offset.getDistanceSq(): Float = x * x + y * y

internal fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    if (pts.size == 2) {
        path.lineTo(pts[1].x, pts[1].y)
        return path
    }
    for (i in 1 until pts.size - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        path.quadraticTo(p0.x, p0.y, midX, midY)
    }
    path.lineTo(pts.last().x, pts.last().y)
    return path
}

internal fun DrawScope.drawArrow(
    start: Offset, end: Offset, color: Color, width: Float
) {
    drawLine(color, start, end, width, cap = StrokeCap.Round)
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLen = (width * 3.5f).coerceAtLeast(18f)
    val angle1 = angle + PI - (PI / 6)
    val angle2 = angle + PI + (PI / 6)
    val p1 = Offset((end.x + arrowLen * cos(angle1)).toFloat(), (end.y + arrowLen * sin(angle1)).toFloat())
    val p2 = Offset((end.x + arrowLen * cos(angle2)).toFloat(), (end.y + arrowLen * sin(angle2)).toFloat())
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

internal fun buildExportOverlays(
    annotationsByPage: Map<Int, List<PdfMarkup>>,
    ocrBlocksByPage: Map<Int, List<OcrTextBlock>>,
    pageCanvasSizes: Map<Int, Size>,
    pageBitmapSizes: Map<Int, Size>
): Map<Int, List<ExportOverlay>> {
    val map = mutableMapOf<Int, List<ExportOverlay>>()

    annotationsByPage.forEach { (page, markups) ->
        if (markups.isEmpty()) return@forEach
        val cs = pageCanvasSizes[page] ?: return@forEach
        val bs = pageBitmapSizes[page]  ?: cs
        if (cs.width <= 0f || cs.height <= 0f || bs.width <= 0f || bs.height <= 0f) return@forEach

        val frame = fitBitmapRect(cs, bs.width, bs.height)

        fun normPoint(p: Offset): NormalizedPoint = NormalizedPoint(
            x = ((p.x - frame.left) / frame.width).coerceIn(0f, 1f),
            y = ((p.y - frame.top) / frame.height).coerceIn(0f, 1f)
        )

        fun normDist(px: Float): Float = px / frame.width.coerceAtLeast(1f)

        val ocrBlocks = ocrBlocksByPage[page].orEmpty()
        val list = mutableListOf<ExportOverlay>()

        markups.forEach { markup ->
            when (markup) {
                is PdfMarkup.StrokeMarkup -> {
                    if (markup.points.size > 1) {
                        list.add(
                            ExportOverlay.Stroke(
                                points = markup.points.map { normPoint(it) },
                                colorArgb = markup.color.toArgb(),
                                widthNorm = normDist(markup.width),
                                alpha = markup.alpha
                            )
                        )
                    }
                }
                is PdfMarkup.RectMarkup -> {
                    list.add(
                        ExportOverlay.RectShape(
                            start = normPoint(markup.start),
                            end = normPoint(markup.end),
                            colorArgb = markup.color.toArgb(),
                            alpha = markup.alpha,
                            filled = markup.filled
                        )
                    )
                }
                is PdfMarkup.OvalMarkup -> {
                    list.add(
                        ExportOverlay.OvalShape(
                            start = normPoint(markup.start),
                            end = normPoint(markup.end),
                            colorArgb = markup.color.toArgb(),
                            alpha = markup.alpha,
                            filled = markup.filled
                        )
                    )
                }
                is PdfMarkup.LineMarkup -> {
                    list.add(
                        ExportOverlay.LineShape(
                            start = normPoint(markup.start),
                            end = normPoint(markup.end),
                            colorArgb = markup.color.toArgb(),
                            widthNorm = normDist(markup.width),
                            alpha = markup.alpha,
                            arrowHead = markup.arrowHead
                        )
                    )
                }
                is PdfMarkup.TextBlockHighlightMarkup -> {
                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val range = OcrTextRange(markup.blockId, markup.start, markup.end)
                        val r = ocrTextRangeToRect(b, range, Rect(0f, 0f, 1f, 1f))
                        list.add(
                            ExportOverlay.RectShape(
                                start = NormalizedPoint(r.left, r.top),
                                end = NormalizedPoint(r.right, r.bottom),
                                colorArgb = markup.color.toArgb(),
                                alpha = markup.alpha,
                                filled = true
                            )
                        )
                    }
                }
                is PdfMarkup.TextBlockLineMarkup -> {
                    ocrBlocks.firstOrNull { it.id == markup.blockId }?.let { b ->
                        val range = OcrTextRange(markup.blockId, markup.start, markup.end)
                        val r = ocrTextRangeToRect(b, range, Rect(0f, 0f, 1f, 1f))
                        val y = markup.textMarkupLineY(r) ?: return@let
                        list.add(
                            ExportOverlay.LineShape(
                                start = NormalizedPoint(r.left, y),
                                end = NormalizedPoint(r.right, y),
                                colorArgb = markup.color.toArgb(),
                                widthNorm = normDist(markup.width),
                                alpha = markup.alpha,
                                arrowHead = false
                            )
                        )
                    }
                }
                is PdfMarkup.ImageMarkup -> {
                    if (!markup.bitmap.isRecycled) {
                        list.add(
                            ExportOverlay.ImageStamp(
                                bitmap = markup.bitmap,
                                start = normPoint(markup.start),
                                end = normPoint(markup.end)
                            )
                        )
                    }
                }
                is PdfMarkup.TextBoxMarkup -> {
                    if (markup.text.isNotBlank()) {
                        list.add(
                            ExportOverlay.TextStamp(
                                position = normPoint(markup.position),
                                text = markup.text,
                                colorArgb = markup.color.toArgb(),
                                fontSizeNorm = (markup.fontSize / frame.height.coerceAtLeast(1f))
                            )
                        )
                    }
                }
                is PdfMarkup.NoteMarkup -> {
                    list.add(
                        ExportOverlay.NoteStamp(
                            position = normPoint(markup.anchor),
                            text = markup.text,
                            colorArgb = markup.color.toArgb()
                        )
                    )
                }
            }
        }

        if (list.isNotEmpty()) map[page] = list
    }

    return map
}

internal fun recolorSignatureBitmap(source: Bitmap, colorArgb: Int): Bitmap {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return source

    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    val rgb = colorArgb and 0x00FFFFFF
    for (index in pixels.indices) {
        val alpha = android.graphics.Color.alpha(pixels[index])
        pixels[index] = if (alpha == 0) 0 else (alpha shl 24) or rgb
    }
    result.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return result
}

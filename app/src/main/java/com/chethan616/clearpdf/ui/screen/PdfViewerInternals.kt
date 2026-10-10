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
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
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
        val end: Int = -1,
        // One highlight gesture over a multi-line / multi-sentence selection produces one of
        // these PER LINE (the text layout breaks the selection at line boundaries), so a single
        // "highlight this paragraph" tap created several sibling markups sharing no link back to
        // each other. Tapping any one of them to delete it only ever removed that one line, which
        // read as "deletes a word, not the group I selected" for anything spanning more than one
        // line. All siblings created by the same [applyToSelection] call share this id (0L = none,
        // only ever produced by code predating this field); deleting one deletes the whole id.
        val groupId: Long = 0L
    ) : PdfMarkup()

    data class TextBlockLineMarkup(
        val blockId: String,
        val color: Color,
        val width: Float = 3f,
        val alpha: Float = 1f,
        val strikeThrough: Boolean = false,
        val start: Int = 0,
        val end: Int = -1,
        /** See [TextBlockHighlightMarkup.groupId]. */
        val groupId: Long = 0L
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

    /**
     * An in-place edit of one line of the page's OWN text (not a sticker): on screen the old line is
     * covered by [background] and [text] drawn in the closest face at the line's real size and
     * baseline; on export the old glyphs are removed from the PDF and the new text is written in the
     * original font (PdfTextEditor). Geometry is content px like the other markups.
     */
    data class TextEditMarkup(
        val id: Long,
        val blockId: String,
        val rect: Rect,
        val baseline: Float,
        val fontSize: Float,
        val original: String,
        val text: String,
        val color: Color,
        val background: Color,
        val bold: Boolean,
        val italic: Boolean,
        val serif: Boolean,
        val mono: Boolean,
        /** The original line's per-character x bounds (normalized), so only what changed is rewritten. */
        val charLefts: FloatArray = FloatArray(0),
        val charRights: FloatArray = FloatArray(0)
    ) : PdfMarkup()

    /**
     * A value filled into one of the PDF's own form fields (#68), kept on the page of the box it
     * was filled in. Not drawn as a markup: the page draws the value inside the field itself.
     */
    data class FormValueMarkup(
        val fieldName: String,
        val value: String
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
            val r = Rect(position.x - 6f, position.y - 6f, position.x + measuredTextWidth() + 6f, position.y + fontSize * 1.2f * lines + 6f)
            r.contains(p)
        }
        is NoteMarkup -> {
            val r = Rect(anchor.x - 8f, anchor.y - 8f, anchor.x + 40f, anchor.y + 40f)
            r.contains(p)
        }
        is TextEditMarkup -> rect.inflate(8f).contains(p)
        is FormValueMarkup -> false
    }
}

/** The paint an in-place text edit previews with: the line's real size and closest typeface. */
internal fun PdfMarkup.TextEditMarkup.previewPaint(): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize
        val family = when {
            mono -> android.graphics.Typeface.MONOSPACE
            serif -> android.graphics.Typeface.SERIF
            else -> android.graphics.Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> android.graphics.Typeface.BOLD_ITALIC
            bold -> android.graphics.Typeface.BOLD
            italic -> android.graphics.Typeface.ITALIC
            else -> android.graphics.Typeface.NORMAL
        }
        typeface = android.graphics.Typeface.create(family, style)
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

/**
 * Real glyph-measured width of a [PdfMarkup.TextBoxMarkup]'s widest line at its current font size,
 * instead of the old `charCount * fontSize * 0.6` guess. That guess was reused by [hitTest] and
 * [movableBounds] to place BOTH the selection outline and the bottom-right resize handle — an
 * estimate that drifts further from the real rendered glyphs as [PdfMarkup.TextBoxMarkup.fontSize]
 * grows (narrow vs. wide character mixes, non-Latin scripts), so a resize drag's handle hit test
 * raced ahead of or behind the finger and the selection frame visibly swam away from the text being
 * resized — reported as "the text moves while resizing" (#48).
 */
private fun PdfMarkup.TextBoxMarkup.measuredTextWidth(): Float {
    val paint = cachedTextBoxPaint
    paint.textSize = fontSize
    val lines = if (text.isEmpty()) listOf("") else text.split("\n")
    return (lines.maxOfOrNull { paint.measureText(it) } ?: 0f).coerceAtLeast(fontSize * 1.2f)
}

/** One reusable [android.graphics.Paint] for text measurement — avoids allocating one per call. */
private val cachedTextBoxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

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
        Rect(position.x, position.y, position.x + measuredTextWidth(), position.y + fontSize * 1.2f * lines)
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
        // Half-sensitivity: at a typical ~48px line height, an ordinary 15-20px per-event drag
        // used to read as a 30-40% font-size jump in one frame — "cumbersome" (#48). A fast swipe
        // still reaches the same range over a few frames; it just no longer overshoots on the first one.
        val dampedDrag = drag.y * 0.5f
        val factor = ((bounds.height + dampedDrag) / bounds.height.coerceAtLeast(1f)).coerceIn(0.3f, 6f)
        copy(fontSize = (fontSize * factor).coerceIn(10f, 400f))
    }
    else -> this
}

/**
 * Absolute resize: this markup as it was when the gesture began ([startBounds]), with its
 * bottom-right corner moved by the gesture's TOTAL drag. The top-left stays put, so the markup
 * never moves while it is resized.
 *
 * Text scales uniformly by the drag projected onto the box diagonal: its width and height both
 * scale with the font size, so the handle stays exactly under the finger along the diagonal and
 * sideways jitter is ignored (#48: "the text moves while resizing").
 */
internal fun PdfMarkup.resizedFrom(startBounds: Rect, total: Offset): PdfMarkup = when (this) {
    is PdfMarkup.TextBoxMarkup -> {
        val d = startBounds.bottomRight - startBounds.topLeft
        val t = d + total
        val len2 = (d.x * d.x + d.y * d.y).coerceAtLeast(1f)
        val f = ((t.x * d.x + t.y * d.y) / len2).coerceIn(0.15f, 10f)
        copy(fontSize = (fontSize * f).coerceIn(8f, 480f))
    }
    else -> resizedBy(total, startBounds)
}

internal fun PdfMarkup.shapeColor(): Color = when (this) {
    is PdfMarkup.StrokeMarkup -> color
    is PdfMarkup.RectMarkup   -> color
    is PdfMarkup.OvalMarkup   -> color
    is PdfMarkup.LineMarkup   -> color
    is PdfMarkup.TextBlockHighlightMarkup -> color
    is PdfMarkup.TextBlockLineMarkup      -> color
    else -> LiquidGlassColors.Blue
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
    if (start >= end) return ocrBlockToRect(block, frame)

    // Character geometry is normally exact. Some PDFs and OCR engines return partial glyph
    // metadata (for example a ligature or malformed ToUnicode map); falling back to the whole
    // line made a one-word highlight paint the entire sentence. Interpolate only the missing
    // character edges across the block so the markup remains range-sized in that case.
    fun charLeft(index: Int): Float = block.charLefts.getOrNull(index)
        ?.takeIf(Float::isFinite)
        ?: (block.left + (block.right - block.left) * index / block.text.length.coerceAtLeast(1))
    fun charRight(index: Int): Float = block.charRights.getOrNull(index)
        ?.takeIf(Float::isFinite)
        ?: (block.left + (block.right - block.left) * (index + 1) / block.text.length.coerceAtLeast(1))

    val left = charLeft(start).coerceIn(block.left, block.right)
    val right = charRight(end - 1).coerceIn(block.left, block.right).coerceAtLeast(left)
    return Rect(
        frame.left + left * frame.width,
        frame.top + block.top * frame.height,
        frame.left + right * frame.width,
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
    // Line boxes now span the font's real ascent..descent (see PdfTextService), so only a hair of
    // breathing room is added. The old 13%/11% padding compensated for boxes that stopped at the
    // baseline and made adjacent lines' highlights overlap once the geometry was right.
    val padTop = rect.height * 0.03f * verticalScale
    val padBottom = rect.height * 0.03f * verticalScale
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
                is PdfMarkup.TextEditMarkup -> {
                    markup.toTextReplace(frame.width, frame.height)?.let { list.add(it) }
                }
                is PdfMarkup.FormValueMarkup -> list.add(ExportOverlay.FormValue(markup.fieldName, markup.value))
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

/** What the in-place text editor is editing: a line of [page]'s own text, maybe already edited. */
internal data class TextEditTarget(
    val page: Int,
    val block: com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock?,
    val markupId: Long?,
    /** The selected characters of the ORIGINAL line, end exclusive; null edits the whole line. */
    val selection: IntRange? = null
)

/**
 * A new in-place edit of [block] (a digital-text line with [style]) at the page's current canvas
 * size, with [text] as its replacement. The cover colour for the on-screen preview is sampled from
 * the rendered page around the line, so coloured paper or a tinted table cell stays coloured.
 */
internal fun textEditMarkupFor(
    block: com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock,
    style: com.kyant.pdfcore.text.PdfTextStyle,
    canvas: androidx.compose.ui.geometry.Size,
    bitmap: Bitmap?,
    text: String
): PdfMarkup.TextEditMarkup {
    val w = canvas.width
    val h = canvas.height
    return PdfMarkup.TextEditMarkup(
        id = System.nanoTime(),
        blockId = block.id,
        rect = Rect(block.left * w, block.top * h, block.right * w, block.bottom * h),
        baseline = style.baselineNorm * h,
        fontSize = style.emNorm * h,
        original = block.text,
        text = text,
        color = Color(0xFF000000.toInt() or (style.rgb and 0xFFFFFF)),
        background = samplePaper(bitmap, block),
        bold = style.bold,
        italic = style.italic,
        serif = style.serif,
        mono = style.mono,
        charLefts = block.charLefts,
        charRights = block.charRights
    )
}

/** Where [current] differs from [original]: (start, end in original, end in current), end exclusive. */
internal fun textDiff(original: String, current: String): Triple<Int, Int, Int> {
    val max = minOf(original.length, current.length)
    var p = 0
    while (p < max && original[p] == current[p]) p++
    var s = 0
    while (s < max - p && original[original.length - 1 - s] == current[current.length - 1 - s]) s++
    return Triple(p, original.length - s, current.length - s)
}

/**
 * The part of [current] (a line's text after earlier edits) that a selection of
 * [selStart] until [selEnd] in [original] covers. A selection touching an earlier change grows to
 * include all of it, so the editor never shows half of a previous edit.
 */
internal fun selectionInCurrent(original: String, current: String, selStart: Int, selEnd: Int): IntRange {
    val (p, oEnd, cEnd) = textDiff(original, current)
    val delta = current.length - original.length
    val r = when {
        selEnd <= p -> selStart until selEnd
        selStart >= oEnd -> (selStart + delta) until (selEnd + delta)
        else -> minOf(selStart, p) until maxOf(if (selEnd > oEnd) selEnd + delta else cEnd, cEnd)
    }
    val a = r.first.coerceIn(0, current.length)
    val b = (r.last + 1).coerceIn(a, current.length)
    return a until b
}

/**
 * The save-path form of an in-place edit: only the words that changed are rewritten (the rest of
 * the line keeps its original glyphs, moved by the change in width), falling back to the whole
 * line when there is no per-character geometry. Null when nothing changed.
 */
internal fun PdfMarkup.TextEditMarkup.toTextReplace(frameW: Float, frameH: Float): ExportOverlay.TextReplace? {
    if (text == original) return null
    val w = frameW.coerceAtLeast(1f)
    val h = frameH.coerceAtLeast(1f)
    val lineL = rect.left / w
    val lineT = rect.top / h
    val lineR = rect.right / w
    val lineB = rect.bottom / h
    val bg = background.toArgb()
    val whole = ExportOverlay.TextReplace(lineL, lineT, lineR, lineB, text, bg)
    val n = original.length
    if (n == 0 || charLefts.size != n || charRights.size != n) return whole

    val (p, oEnd, cEnd) = textDiff(original, text)
    // Grow to whole words: every edit box then holds real glyphs to anchor on, and unchanged
    // letters of a touched word are re-drawn from their own bytes, identical to before.
    var a = p
    var oe = oEnd
    var ce = cEnd
    while (a > 0 && !original[a - 1].isWhitespace()) a--
    while (oe < n && !original[oe].isWhitespace()) { oe++; ce++ }
    if (original.substring(a, oe).isBlank()) {
        // Only spaces changed (or a pure insertion between words): anchor on a neighbouring word.
        if (a > 0) {
            while (a > 0 && original[a - 1].isWhitespace()) a--
            while (a > 0 && !original[a - 1].isWhitespace()) a--
        } else {
            while (oe < n && original[oe].isWhitespace()) { oe++; ce++ }
            while (oe < n && !original[oe].isWhitespace()) { oe++; ce++ }
        }
    }
    if (a >= oe || ce < a || ce > text.length) return whole
    if (a == 0 && oe == n) return whole
    return ExportOverlay.TextReplace(
        left = charLefts[a],
        top = lineT,
        right = charRights[oe - 1],
        bottom = lineB,
        text = text.substring(a, ce),
        backgroundArgb = bg,
        lineLeft = lineL,
        lineTop = lineT,
        lineRight = lineR,
        lineBottom = lineB
    )
}

/**
 * An exact preview of a page's text edits: [bitmap] is the edited page rendered by the PDF engine
 * over the normalized horizontal [band] around the line, valid while the markup's text is [text].
 */
internal class TextEditPatch(val text: String, val band: android.graphics.RectF, val bitmap: Bitmap)

/** The band an edited line's preview covers: full page width, the line plus room for ascenders. */
internal fun PdfMarkup.TextEditMarkup.previewBand(canvasH: Float): android.graphics.RectF {
    val h = canvasH.coerceAtLeast(1f)
    val pad = rect.height * 0.45f
    return android.graphics.RectF(
        0f,
        ((rect.top - pad) / h).coerceIn(0f, 1f),
        1f,
        ((rect.bottom + pad) / h).coerceIn(0f, 1f)
    )
}

/** The colour most samples just outside the line agree on — the paper behind it. */
private fun samplePaper(bitmap: Bitmap?, b: com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock): Color {
    if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return Color.White
    val bw = bitmap.width
    val bh = bitmap.height
    val dx = 0.006f
    val dy = 0.004f
    val cx = (b.left + b.right) / 2f
    val points = listOf(
        b.left - dx to b.top, b.left - dx to b.bottom, b.right + dx to b.top, b.right + dx to b.bottom,
        cx to b.top - dy, cx to b.bottom + dy, b.left to b.top - dy, b.right to b.bottom + dy
    )
    val samples = points.mapNotNull { (x, y) ->
        val px = (x * bw).toInt()
        val py = (y * bh).toInt()
        if (px in 0 until bw && py in 0 until bh) runCatching { bitmap.getPixel(px, py) }.getOrNull() else null
    }
    if (samples.isEmpty()) return Color.White
    val paper = samples.groupBy { it and 0xF0F0F0 }.maxByOrNull { it.value.size }!!.value.first()
    return Color(paper or 0xFF000000.toInt())
}

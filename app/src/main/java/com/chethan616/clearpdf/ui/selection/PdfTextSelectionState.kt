package com.chethan616.clearpdf.ui.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange

/** A caret position in the document: between characters [offset]-1 and [offset] of [page]'s flat text. */
data class TextPos(val page: Int, val offset: Int) : Comparable<TextPos> {
    override fun compareTo(other: TextPos): Int =
        if (page != other.page) page.compareTo(other.page) else offset.compareTo(other.offset)
}

enum class SelectionHandle { Start, End }

/** A handle's attachment point in screen space: the caret's x and its line's top/bottom. */
data class ScreenCaret(val x: Float, val lineTop: Float, val lineBottom: Float) {
    val lineHeight: Float get() = lineBottom - lineTop
    val lineCenterY: Float get() = (lineTop + lineBottom) / 2f
}

/**
 * The PDF viewer's text selection: an anchor/focus pair of [TextPos], possibly spanning pages.
 *
 * Owned by the viewer screen (not the ViewModel — it is pure UI state, like a TextField's selection).
 * The screen refreshes the environment every composition: [blocksProvider], [transform]; pages
 * register their [LayoutCoordinates] so page-local geometry can be mapped to the screen through the
 * single pure [PdfViewportTransform].
 *
 * Every mutable field that UI reads is snapshot state, so highlight/handles/toolbar redraw when it
 * changes; gestures hold on to this object (a stable reference) and never key on its values —
 * restarting a `pointerInput` mid-drag is exactly the bug that used to kill handle drags.
 */
@Stable
class PdfTextSelectionState {

    var anchor by mutableStateOf<TextPos?>(null)
        private set
    var focus by mutableStateOf<TextPos?>(null)
        private set

    /** Which handle a finger is dragging, or null. */
    var draggingHandle by mutableStateOf<SelectionHandle?>(null)
        internal set

    /** True for the whole of a long-press→drag or handle drag (hides the toolbar, blocks panning). */
    var gestureActive by mutableStateOf(false)
        internal set

    /** Screen point the platform magnifier should enlarge; Unspecified hides it. */
    var magnifierCenter by mutableStateOf(Offset.Unspecified)
        internal set

    /**
     * Current zoom/pan. A provider rather than a stored value so every reader (draw, layout, gesture)
     * reads the viewer's live `scale`/`offsetX` snapshot state directly and is invalidated by it.
     */
    var transformProvider: () -> PdfViewportTransform = { PdfViewportTransform(0f, 1f, 0f) }
    val transform: PdfViewportTransform get() = transformProvider()

    var blocksProvider: (Int) -> List<OcrTextBlock> = { emptyList() }

    private val pageCoords = HashMap<Int, LayoutCoordinates>()
    private var layerCoords: LayoutCoordinates? = null
    private val layoutCache = HashMap<Int, Pair<List<OcrTextBlock>, PdfTextLayout>>()

    val hasSelection: Boolean
        get() {
            val a = anchor ?: return false
            val f = focus ?: return false
            return a != f
        }

    val start: TextPos? get() { val a = anchor ?: return null; val f = focus ?: return null; return minOf(a, f) }
    val end: TextPos? get() { val a = anchor ?: return null; val f = focus ?: return null; return maxOf(a, f) }

    // ── Environment ─────────────────────────────────────────────────────────────────────────

    fun layout(page: Int): PdfTextLayout? {
        val blocks = blocksProvider(page)
        if (blocks.isEmpty()) return null
        layoutCache[page]?.let { (b, l) -> if (b === blocks) return l }
        return PdfTextLayout(blocks).also { layoutCache[page] = blocks to it }
    }

    fun registerPage(page: Int, coords: LayoutCoordinates) { pageCoords[page] = coords }
    fun unregisterPage(page: Int, coords: LayoutCoordinates?) {
        if (coords == null || pageCoords[page] === coords) pageCoords.remove(page)
    }
    fun registerLayer(coords: LayoutCoordinates) { layerCoords = coords }

    fun resetDocument() {
        clear()
        layoutCache.clear()
        pageCoords.clear()
    }

    /** Top-left of [page] in the zoom layer's own (unscaled) coordinates, if it is laid out. */
    fun pageOrigin(page: Int): Offset? {
        val layer = layerCoords ?: return null
        val pc = pageCoords[page] ?: return null
        if (!layer.isAttached || !pc.isAttached) return null
        return runCatching { layer.localPositionOf(pc, Offset.Zero) }.getOrNull()
    }

    fun pageSize(page: Int): Size? = pageCoords[page]?.takeIf { it.isAttached }?.size?.toSize()

    /** The laid-out page under (or vertically nearest to) screen point [p]. */
    fun pageAtScreen(p: Offset): Int? {
        val lp = transform.screenToLayer(p)
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        for (page in pageCoords.keys.toList()) {
            val o = pageOrigin(page) ?: continue
            val s = pageSize(page) ?: continue
            val d = when {
                lp.y < o.y -> o.y - lp.y
                lp.y > o.y + s.height -> lp.y - (o.y + s.height)
                else -> 0f
            }
            if (d < bestD) { bestD = d; best = page }
        }
        return best
    }

    // ── Selection mutation ──────────────────────────────────────────────────────────────────

    fun select(anchor: TextPos, focus: TextPos) {
        this.anchor = anchor
        this.focus = focus
    }

    fun clear() {
        anchor = null; focus = null
        draggingHandle = null; gestureActive = false
        magnifierCenter = Offset.Unspecified
    }

    /** Select all the text of pages [first]..[last] whose text is loaded. */
    fun selectPages(first: Int, last: Int) {
        var a: TextPos? = null
        var f: TextPos? = null
        for (p in first..last) {
            val l = layout(p) ?: continue
            if (a == null) a = TextPos(p, 0)
            f = TextPos(p, l.length)
        }
        if (a != null && f != null && a != f) select(a, f)
    }

    // ── Queries ─────────────────────────────────────────────────────────────────────────────

    /** The selected flat range on [page], or null. */
    fun pageRange(page: Int): Pair<Int, Int>? {
        val s = start ?: return null
        val e = end ?: return null
        if (s == e || page < s.page || page > e.page) return null
        val l = layout(page) ?: return null
        val from = if (page == s.page) s.offset.coerceIn(0, l.length) else 0
        val to = if (page == e.page) e.offset.coerceIn(0, l.length) else l.length
        return if (to > from) from to to else null
    }

    /** The selection as annotation-ready (blockId, start, end) ranges, per page. */
    fun rangesByPage(): Map<Int, List<OcrTextRange>> {
        val s = start ?: return emptyMap()
        val e = end ?: return emptyMap()
        val out = LinkedHashMap<Int, List<OcrTextRange>>()
        for (p in s.page..e.page) {
            val (from, to) = pageRange(p) ?: continue
            val r = layout(p)?.rangesFor(from, to).orEmpty()
            if (r.isNotEmpty()) out[p] = r
        }
        return out
    }

    /** Pages the selection spans. */
    fun pageSpan(): IntRange? {
        val s = start ?: return null
        val e = end ?: return null
        return s.page..e.page
    }

    /** Plain text of the selection: lines joined by newlines, pages by a blank line. */
    fun selectedText(): String {
        val s = start ?: return ""
        val e = end ?: return ""
        val parts = ArrayList<String>()
        for (p in s.page..e.page) {
            val (from, to) = pageRange(p) ?: continue
            layout(p)?.substring(from, to)?.let { if (it.isNotBlank()) parts.add(it) }
        }
        return parts.joinToString("\n\n")
    }

    /** Screen caret geometry for a selection end, or null when that page is not laid out. */
    fun handleCaret(handle: SelectionHandle): ScreenCaret? {
        val pos = (if (handle == SelectionHandle.Start) start else end) ?: return null
        return caretOnScreen(pos, handle == SelectionHandle.Start)
    }

    fun caretOnScreen(pos: TextPos, isStart: Boolean): ScreenCaret? {
        val l = layout(pos.page) ?: return null
        val origin = pageOrigin(pos.page) ?: return null
        val size = pageSize(pos.page) ?: return null
        val g = l.caretGeometry(pos.offset, isStart, size) ?: return null
        val t = transform
        val top = t.pageToScreen(Offset(g.x, g.lineTop), origin)
        val bottom = t.pageToScreen(Offset(g.x, g.lineBottom), origin)
        return ScreenCaret(bottom.x, top.y, bottom.y)
    }

    /** Selection highlight rectangles in screen space, for pages currently laid out. */
    fun selectionScreenRects(): List<Rect> {
        val span = pageSpan() ?: return emptyList()
        val out = ArrayList<Rect>()
        for (p in span) {
            val (from, to) = pageRange(p) ?: continue
            val origin = pageOrigin(p) ?: continue
            val size = pageSize(p) ?: continue
            layout(p)?.selectionRects(from, to, size)?.forEach { out.add(transform.pageRectToScreen(it, origin)) }
        }
        return out
    }

    /** Caret nearest a screen point, on the page under it. */
    fun caretAtScreen(p: Offset): TextPos? {
        val page = pageAtScreen(p) ?: return null
        val l = layout(page) ?: return nearestTextPageCaret(page, p)
        val origin = pageOrigin(page) ?: return null
        val size = pageSize(page) ?: return null
        return TextPos(page, l.caretAt(transform.screenToPage(p, origin), size))
    }

    /** A page with no text: snap to the end of the previous text page or start of the next. */
    private fun nearestTextPageCaret(page: Int, p: Offset): TextPos? {
        val origin = pageOrigin(page) ?: return null
        val size = pageSize(page) ?: return null
        val local = transform.screenToPage(p, origin)
        val before = local.y < size.height / 2f
        val cur = focus ?: return null
        return if (before) {
            (page - 1 downTo maxOf(0, page - 3)).firstNotNullOfOrNull { q -> layout(q)?.let { TextPos(q, it.length) } }
        } else {
            (page + 1..page + 3).firstNotNullOfOrNull { q -> layout(q)?.let { TextPos(q, 0) } }
        } ?: cur
    }

    // ── Gesture logic ───────────────────────────────────────────────────────────────────────

    private var pressWordPage = -1
    private var pressWord: IntRange = IntRange.EMPTY

    /**
     * Long-press: select the word under [p]. Returns false (and changes nothing) when the press is
     * not on text.
     */
    fun selectWordAt(p: Offset, slopPx: Float): Boolean {
        val page = pageAtScreen(p) ?: return false
        val l = layout(page) ?: return false
        val origin = pageOrigin(page) ?: return false
        val size = pageSize(page) ?: return false
        val local = transform.screenToPage(p, origin)
        if (local.y < -slopPx || local.y > size.height + slopPx) return false
        val c = l.charAt(local, size, slopPx / transform.scale.coerceAtLeast(0.01f)) ?: return false
        val w = l.wordAt(c)
        if (w.isEmpty()) return false
        pressWordPage = page
        pressWord = w
        select(TextPos(page, w.first), TextPos(page, w.last + 1))
        return true
    }

    /** Long-press then drag (finger still down): extend word by word, like TextView. */
    fun extendWordSelection(p: Offset): Boolean {
        if (pressWordPage < 0) return false
        val raw = caretAtScreen(p) ?: return false
        val l = layout(raw.page) ?: return false
        val w0s = TextPos(pressWordPage, pressWord.first)
        val w0e = TextPos(pressWordPage, pressWord.last + 1)
        val before = anchor to focus
        if (raw >= w0e) {
            val c = (raw.offset - 1).coerceAtLeast(0)
            val w = l.wordAt(c)
            select(w0s, TextPos(raw.page, maxOf(w.last + 1, raw.offset).coerceAtMost(l.length)))
        } else if (raw <= w0s) {
            val w = l.wordAt(raw.offset.coerceAtMost(l.length - 1))
            select(w0e, TextPos(raw.page, minOf(w.first, raw.offset)))
        } else {
            select(w0s, w0e)
        }
        return before != (anchor to focus)
    }

    /** Pin the opposite end as the anchor so the grabbed end becomes the focus. */
    fun beginHandleDrag(handle: SelectionHandle) {
        val s = start ?: return
        val e = end ?: return
        if (handle == SelectionHandle.Start) select(e, s) else select(s, e)
        draggingHandle = handle
        gestureActive = true
    }

    /**
     * Move the focus to the caret nearest [linePoint] (a screen point on the dragged caret's line).
     * TextView granularity: expanding snaps to whole words once past a word's middle; shrinking is
     * character-precise. Never collapses to an empty selection. Returns true if the focus moved.
     */
    fun dragFocusTo(linePoint: Offset): Boolean {
        val a = anchor ?: return false
        val f = focus ?: return false
        val raw = caretAtScreen(linePoint) ?: return false
        val l = layout(raw.page) ?: return false
        val forward = f > a
        val expanding = if (forward) raw > f else raw < f
        var target = raw
        if (expanding && l.length > 0) {
            if (forward) {
                val w = l.wordAt((raw.offset - 1).coerceIn(0, l.length - 1))
                if (raw.offset > w.first && raw.offset < w.last + 1) {
                    val mid = w.first + (w.last + 1 - w.first) / 2f
                    target = TextPos(raw.page, if (raw.offset >= mid) w.last + 1 else w.first)
                }
            } else {
                val w = l.wordAt(raw.offset.coerceIn(0, l.length - 1))
                if (raw.offset > w.first && raw.offset < w.last + 1) {
                    val mid = w.first + (w.last + 1 - w.first) / 2f
                    target = TextPos(raw.page, if (raw.offset <= mid) w.first else w.last + 1)
                }
            }
        }
        if (target == a) return false
        if (target == f) return false
        focus = target
        return true
    }

    fun endGesture() {
        draggingHandle = null
        gestureActive = false
        magnifierCenter = Offset.Unspecified
        pressWordPage = -1
        // A drag can leave focus before anchor; that's fine — start/end are always normalised.
    }
}

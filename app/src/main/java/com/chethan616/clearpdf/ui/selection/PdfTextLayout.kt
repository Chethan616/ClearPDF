package com.chethan616.clearpdf.ui.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One page's extracted text, flattened into a single string in reading order so a selection is just
 * a pair of caret offsets.
 *
 * Blocks (PdfBox line segments, or OCR lines for scanned pages) are ordered with a small XY-cut so a
 * two-column page reads left column then right column instead of interleaving lines. Consecutive
 * blocks are joined by one `'\n'` in [text]; a caret offset `o` is "between characters" (0..length).
 *
 * All geometry in and out is in the page's local, unscaled pixel space for a page of [Size] `size`;
 * the blocks themselves carry coordinates normalised to 0..1.
 */
class PdfTextLayout(blocks: List<OcrTextBlock>) {

    val blocks: List<OcrTextBlock> = readingOrder(blocks.filter { it.text.isNotEmpty() })

    /** Flat offset at which each block's first character sits. */
    private val starts: IntArray
    val text: String

    init {
        val sb = StringBuilder()
        starts = IntArray(this.blocks.size)
        this.blocks.forEachIndexed { i, b ->
            if (i > 0) sb.append('\n')
            starts[i] = sb.length
            sb.append(b.text)
        }
        text = sb.toString()
    }

    val length: Int get() = text.length
    val isEmpty: Boolean get() = blocks.isEmpty()

    private val words: BreakIterator by lazy {
        BreakIterator.getWordInstance().also { it.setText(text) }
    }

    // ── Offset ↔ block ────────────────────────────────────────────────────────────────────────

    /** Index of the block containing flat offset [o] (a separator belongs to the block before it). */
    fun blockIndexOf(o: Int): Int {
        if (blocks.isEmpty()) return -1
        var lo = 0; var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= o) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun blockStart(i: Int): Int = starts[i]
    fun blockEnd(i: Int): Int = starts[i] + blocks[i].text.length

    /** The per-block ranges covering flat [start, end). Separators contribute nothing. */
    fun rangesFor(start: Int, end: Int): List<OcrTextRange> {
        if (blocks.isEmpty() || end <= start) return emptyList()
        val out = ArrayList<OcrTextRange>()
        val first = blockIndexOf(start)
        val last = blockIndexOf((end - 1).coerceAtLeast(0))
        for (i in first..last) {
            val s = (start - starts[i]).coerceIn(0, blocks[i].text.length)
            val e = (end - starts[i]).coerceIn(0, blocks[i].text.length)
            if (e > s) out.add(OcrTextRange(blocks[i].id, s, e))
        }
        return out
    }

    fun substring(start: Int, end: Int): String =
        text.substring(start.coerceIn(0, length), end.coerceIn(start.coerceIn(0, length), length))

    // ── Geometry ──────────────────────────────────────────────────────────────────────────────

    fun blockRect(i: Int, size: Size): Rect {
        val b = blocks[i]
        return Rect(b.left * size.width, b.top * size.height, b.right * size.width, b.bottom * size.height)
    }

    private fun charLeft(b: OcrTextBlock, c: Int, size: Size): Float =
        (if (c < b.charLefts.size) b.charLefts[c]
        else b.left + (b.right - b.left) * c / b.text.length.coerceAtLeast(1)) * size.width

    private fun charRight(b: OcrTextBlock, c: Int, size: Size): Float =
        (if (c < b.charRights.size) b.charRights[c]
        else b.left + (b.right - b.left) * (c + 1) / b.text.length.coerceAtLeast(1)) * size.width

    /**
     * Highlight rectangles (one per block fragment) for flat [start, end), expanded vertically so
     * ascenders and descenders sit inside the band like a platform text selection.
     */
    fun selectionRects(start: Int, end: Int, size: Size): List<Rect> {
        if (blocks.isEmpty() || end <= start) return emptyList()
        val out = ArrayList<Rect>()
        val first = blockIndexOf(start)
        val last = blockIndexOf((end - 1).coerceAtLeast(0))
        for (i in first..last) {
            val b = blocks[i]
            val s = (start - starts[i]).coerceIn(0, b.text.length)
            val e = (end - starts[i]).coerceIn(0, b.text.length)
            if (e <= s) continue
            val r = blockRect(i, size)
            out.add(expandLine(Rect(charLeft(b, s, size), r.top, charRight(b, e - 1, size), r.bottom)))
        }
        return out
    }

    /**
     * Where a selection handle attaches for caret [o]. The START handle sits at the left edge of the
     * character at [o]; the END handle at the right edge of the character before [o] — so a caret
     * sitting on a line break attaches to the line the user sees it on.
     */
    fun caretGeometry(o: Int, isStart: Boolean, size: Size): CaretGeometry? {
        if (blocks.isEmpty()) return null
        val oc = o.coerceIn(0, length)
        val bi: Int
        val x: Float
        if (isStart) {
            var i = blockIndexOf(oc)
            var c = oc - starts[i]
            if (c >= blocks[i].text.length && i + 1 < blocks.size) { i++; c = 0 }
            c = c.coerceIn(0, blocks[i].text.length - 1)
            bi = i; x = charLeft(blocks[i], c, size)
        } else {
            val i = blockIndexOf((oc - 1).coerceAtLeast(0))
            val c = (oc - starts[i] - 1).coerceIn(0, blocks[i].text.length - 1)
            bi = i; x = charRight(blocks[i], c, size)
        }
        val line = expandLine(blockRect(bi, size))
        return CaretGeometry(x, line.top, line.bottom, bi)
    }

    // ── Hit testing ───────────────────────────────────────────────────────────────────────────

    /**
     * The block a point belongs to: among blocks whose (expanded) line band contains the point's y,
     * the horizontally nearest; otherwise the vertically nearest block. Null for an empty page.
     */
    private fun blockAt(p: Offset, size: Size): Int {
        if (blocks.isEmpty()) return -1
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (i in blocks.indices) {
            val r = expandLine(blockRect(i, size))
            val dy = when {
                p.y < r.top -> r.top - p.y
                p.y > r.bottom -> p.y - r.bottom
                else -> 0f
            }
            val dx = when {
                p.x < r.left -> r.left - p.x
                p.x > r.right -> p.x - r.right
                else -> 0f
            }
            // Vertical distance dominates: lines are picked by y first (like TextView), then the
            // nearest column/segment on that line by x.
            val score = dy * 1000f + dx
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    /** Caret offset nearest [p]: before a character if [p] is left of its centre, after otherwise. */
    fun caretAt(p: Offset, size: Size): Int {
        val i = blockAt(p, size)
        if (i < 0) return 0
        val b = blocks[i]
        var c = 0
        while (c < b.text.length) {
            val mid = (charLeft(b, c, size) + charRight(b, c, size)) / 2f
            if (p.x < mid) break
            c++
        }
        return starts[i] + c
    }

    /**
     * The character offset under [p], or null when the press is not on (or right next to) any text —
     * a long-press on a margin must not select some far-away word.
     */
    fun charAt(p: Offset, size: Size, slopPx: Float): Int? {
        val i = blockAt(p, size)
        if (i < 0) return null
        val b = blocks[i]
        val r = expandLine(blockRect(i, size))
        val tol = max(slopPx, r.height * 0.5f)
        if (!r.inflate(tol).contains(p)) return null
        var best = 0
        var bestD = Float.MAX_VALUE
        for (c in b.text.indices) {
            val l = charLeft(b, c, size); val rr = charRight(b, c, size)
            val d = if (p.x < l) l - p.x else if (p.x > rr) p.x - rr else 0f
            if (d < bestD) { bestD = d; best = c }
            if (d == 0f) break
        }
        return starts[i] + best
    }

    /**
     * The word around character offset [o]: [start, end). A press on whitespace or punctuation
     * selects the nearest word on the same block, falling back to that single character.
     */
    fun wordAt(o: Int): IntRange {
        if (length == 0) return 0 until 0
        val oc = o.coerceIn(0, length - 1)
        fun wordAround(x: Int): IntRange? {
            if (x !in 0 until length || !text[x].isLetterOrDigit()) return null
            val it = words
            val s = if (it.isBoundary(x)) x else it.preceding(x)
            val e = it.following(x)
            return if (s == BreakIterator.DONE || e == BreakIterator.DONE) null else s until e
        }
        wordAround(oc)?.let { return it }
        val bi = blockIndexOf(oc)
        val bs = starts[bi]; val be = blockEnd(bi)
        for (d in 1..40) {
            if (oc - d >= bs) wordAround(oc - d)?.let { return it }
            if (oc + d < be) wordAround(oc + d)?.let { return it }
        }
        return oc until oc + 1
    }

    data class CaretGeometry(val x: Float, val lineTop: Float, val lineBottom: Float, val blockIndex: Int) {
        val lineHeight: Float get() = lineBottom - lineTop
    }

    companion object {
        /**
         * PdfBox reports each glyph box from roughly cap height down to the BASELINE, so a raw block
         * rect clips descenders and accents. Pad it to a full typographic line, the band TextView
         * paints.
         */
        fun expandLine(r: Rect): Rect {
            val h = r.height.coerceAtLeast(1f)
            return Rect(r.left, r.top - h * 0.26f, r.right, r.bottom + h * 0.24f)
        }

        /**
         * Recursive XY-cut: prefer a clean vertical gutter (columns read top-to-bottom, left then
         * right), then the single widest horizontal gap (e.g. a full-width title above columns),
         * else plain top-to-bottom, left-to-right line order.
         */
        internal fun readingOrder(blocks: List<OcrTextBlock>): List<OcrTextBlock> {
            if (blocks.size <= 1) return blocks
            verticalCut(blocks)?.let { (l, r) -> return readingOrder(l) + readingOrder(r) }
            horizontalCut(blocks)?.let { (t, b) -> return readingOrder(t) + readingOrder(b) }
            return lineOrder(blocks)
        }

        private fun verticalCut(blocks: List<OcrTextBlock>): Pair<List<OcrTextBlock>, List<OcrTextBlock>>? {
            val sorted = blocks.sortedBy { it.left }
            var maxRight = sorted.first().right
            var bestAt = -1
            var bestGap = 0.015f
            for (i in 1 until sorted.size) {
                val gap = sorted[i].left - maxRight
                if (gap > bestGap) { bestGap = gap; bestAt = i }
                maxRight = max(maxRight, sorted[i].right)
            }
            if (bestAt < 0) return null
            val left = sorted.subList(0, bestAt)
            val right = sorted.subList(bestAt, sorted.size)
            // Only a real column split: several reasonably wide lines on each side. Narrow table
            // cells stay in row order, where "label value" reads correctly.
            fun columnLike(side: List<OcrTextBlock>): Boolean {
                if (side.size < 2) return false
                val widths = side.map { it.right - it.left }.sorted()
                return widths[widths.size / 2] >= 0.15f
            }
            if (!columnLike(left) || !columnLike(right)) return null
            return left to right
        }

        private fun horizontalCut(blocks: List<OcrTextBlock>): Pair<List<OcrTextBlock>, List<OcrTextBlock>>? {
            val sorted = blocks.sortedBy { it.top }
            var maxBottom = sorted.first().bottom
            var bestAt = -1
            var bestGap = 0f
            for (i in 1 until sorted.size) {
                val gap = sorted[i].top - maxBottom
                if (gap > bestGap) { bestGap = gap; bestAt = i }
                maxBottom = max(maxBottom, sorted[i].bottom)
            }
            if (bestAt < 0) return null
            val top = sorted.subList(0, bestAt)
            val bottom = sorted.subList(bestAt, sorted.size)
            // Only worth recursing if one side can then be column-split; otherwise line order is
            // already correct and cheaper.
            if (verticalCut(top) == null && verticalCut(bottom) == null) return null
            return top to bottom
        }

        private fun lineOrder(blocks: List<OcrTextBlock>): List<OcrTextBlock> {
            val sorted = blocks.sortedBy { it.top }
            val lines = mutableListOf<MutableList<OcrTextBlock>>()
            for (b in sorted) {
                val line = lines.lastOrNull()
                val ref = line?.first()
                if (ref != null) {
                    val overlap = min(ref.bottom, b.bottom) - max(ref.top, b.top)
                    val h = min(ref.bottom - ref.top, b.bottom - b.top)
                    if (overlap > h * 0.5f || abs(ref.bottom - b.bottom) < h * 0.3f) { line.add(b); continue }
                }
                lines.add(mutableListOf(b))
            }
            return lines.flatMap { l -> l.sortedBy { it.left } }
        }
    }
}

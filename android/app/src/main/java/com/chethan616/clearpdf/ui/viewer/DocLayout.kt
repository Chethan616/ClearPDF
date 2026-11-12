package com.chethan616.clearpdf.ui.viewer

import kotlin.math.max
import kotlin.math.min

/** A page's displayed size in PDF points. */
data class PageSize(val width: Float, val height: Float)

/** A rectangle in layout pixels (the document at zoom 1). */
data class LRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** How pages follow each other. */
enum class PageMode {
    /** One column, scrolled vertically; every page as wide as the widest fits the screen. */
    Continuous,

    /** One page at a time, side by side, each fitted to the screen; swipes go page to page. */
    SinglePage,
}

/**
 * Where each page is at zoom 1, in layout pixels, for a viewport of [viewW] × [viewH] pixels.
 * Pure geometry, so it can be tested off the device.
 *
 * Continuous: the pages stack in one column with [gap] between them, scaled together so the
 * widest fills the width less [margin] on each side; [padTop] and [padBottom] keep the first and
 * last pages clear of the bars. SinglePage: each page is fitted into the viewport (less the
 * margins and paddings) and centred in a slot one viewport wide.
 */
class DocLayout(
    val pages: List<PageSize>,
    val mode: PageMode,
    val viewW: Float,
    val viewH: Float,
    val gap: Float,
    val margin: Float,
    val padTop: Float,
    val padBottom: Float,
    /** Continuous mode: how many pages sit side by side in a row (1, or 2 like an open book). */
    val columns: Int = 1,
) {
    val rects: List<LRect>
    val width: Float
    val height: Float

    /** Continuous mode: where each page's row ends (rows of two pages may differ in height). */
    private val rowBottom: FloatArray

    init {
        val out = ArrayList<LRect>(pages.size)
        when (mode) {
            PageMode.Continuous -> {
                val widest = pages.maxOfOrNull { it.width }?.coerceAtLeast(1f) ?: 1f
                val cols = columns.coerceIn(1, 2)
                // side by side, the pages share the width (less the gap between them)
                val scale = max(1f, (viewW - 2 * margin - (cols - 1) * gap) / cols) / widest
                val bottoms = FloatArray(pages.size)
                var y = padTop
                var i = 0
                while (i < pages.size) {
                    val row = pages.subList(i, min(i + cols, pages.size))
                    val rowW = row.sumOf { (it.width.coerceAtLeast(1f) * scale).toDouble() }.toFloat() + (row.size - 1) * gap
                    val rowH = row.maxOf { it.height.coerceAtLeast(1f) * scale }
                    var x = (viewW - rowW) / 2f
                    for ((k, p) in row.withIndex()) {
                        val w = p.width.coerceAtLeast(1f) * scale
                        val h = p.height.coerceAtLeast(1f) * scale
                        out.add(LRect(x, y, x + w, y + h))
                        bottoms[i + k] = y + rowH
                        x += w + gap
                    }
                    y += rowH + gap
                    i += row.size
                }
                rowBottom = bottoms
                width = viewW
                height = if (pages.isEmpty()) viewH else y - gap + padBottom
            }
            PageMode.SinglePage -> {
                val availW = max(1f, viewW - 2 * margin)
                val availH = max(1f, viewH - padTop - padBottom - 2 * margin)
                // A phone turned on its side: an upright page fitted whole would be a narrow strip
                // in the middle of the screen, too small to read. There the page takes the screen's
                // width, as other readers show it, and is read by scrolling down it; a swipe
                // sideways still turns the page.
                val sideways = viewW > viewH * 1.15f
                var tallest = viewH
                pages.forEachIndexed { i, p ->
                    val byWidth = availW / p.width.coerceAtLeast(1f)
                    val whole = min(byWidth, availH / p.height.coerceAtLeast(1f))
                    val s = if (sideways) byWidth else whole
                    val w = p.width.coerceAtLeast(1f) * s
                    val h = p.height.coerceAtLeast(1f) * s
                    val x = i * viewW + (viewW - w) / 2f
                    val y = padTop + margin + max(0f, (availH - h) / 2f)
                    out.add(LRect(x, y, x + w, y + h))
                    tallest = max(tallest, y + h + margin + padBottom)
                }
                rowBottom = FloatArray(0)
                width = max(viewW, pages.size * viewW)
                height = tallest
            }
        }
        rects = out
    }

    /** Pixels per PDF point of page [i] at zoom 1. */
    fun scaleOf(i: Int): Float = rects[i].width / pages[i].width.coerceAtLeast(1f)

    /** The pages that overlap layout rows [top]..[bottom] (and columns [left]..[right]). */
    fun visible(left: Float, top: Float, right: Float, bottom: Float): IntRange {
        if (rects.isEmpty()) return IntRange.EMPTY
        return when (mode) {
            PageMode.Continuous -> {
                val first = firstAtOrBelow(top)
                var last = first
                while (last + 1 < rects.size && rects[last + 1].top <= bottom) last++
                first..last
            }
            PageMode.SinglePage -> {
                val first = (left / viewW).toInt().coerceIn(0, rects.lastIndex)
                val last = (right / viewW).toInt().coerceIn(0, rects.lastIndex)
                first..last
            }
        }
    }

    /** The first page whose bottom is at or below layout row [y] (binary search). */
    private fun firstAtOrBelow(y: Float): Int {
        var lo = 0
        var hi = rects.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (rowBottom[mid] + gap < y) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The page under layout point ([x], [y]), if any. */
    fun pageAt(x: Float, y: Float): Int? {
        val range = visible(x, y, x, y)
        return range.firstOrNull { rects[it].contains(x, y) }
    }

    /** The page nearest layout point ([x], [y]): the one under it, else the closest. */
    fun nearestPage(x: Float, y: Float): Int {
        if (rects.isEmpty()) return 0
        return when (mode) {
            PageMode.Continuous -> {
                var i = firstAtOrBelow(y)
                // side by side: the page of the row that [x] is nearest
                while (i + 1 < rects.size && rects[i + 1].top == rects[i].top && x > (rects[i].right + rects[i + 1].left) / 2f) i++
                val r = rects[i]
                when {
                    // in the gap below page i: whichever edge is closer
                    y > r.bottom && i + 1 < rects.size -> if (rects[i + 1].top - y < y - r.bottom) i + 1 else i
                    y < r.top && i > 0 -> if (r.top - y > y - rects[i - 1].bottom) i - 1 else i
                    else -> i
                }
            }
            PageMode.SinglePage -> (x / viewW).toInt().coerceIn(0, rects.lastIndex)
        }
    }
}

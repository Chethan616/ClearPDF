package com.chethan616.clearpdf.utils.xlsx

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.chethan616.clearpdf.utils.ExcelCellFormat

/**
 * Geometry of a sheet in Excel pixels (96 dpi, 100% zoom): column left edges and row top edges as
 * prefix sums, hidden lines at zero size. Built once per sheet version; everything that maps a touch
 * or a scroll offset to a cell goes through here.
 */
class SheetLayout(val sheet: XlsxSheet, extraRows: Int = 40, extraCols: Int = 8) {
    val nRows: Int = maxOf(sheet.maxRow + 1 + extraRows, 60, sheet.frozenRows + 1).coerceAtMost(XlsxReader.MaxRows + extraRows)
    val nCols: Int = maxOf(sheet.maxCol + 1 + extraCols, 12, sheet.frozenCols + 1).coerceAtMost(XlsxReader.MaxColumns)
    val colX = FloatArray(nCols + 1)
    val rowY = FloatArray(nRows + 1)
    /** Rows the scrolling list shows (not frozen, not hidden), in order. */
    val scrollRows: IntArray
    /**
     * Each column's default `xf` (its `<col style>`), so an empty cell's fill is an array read on
     * the draw path instead of a boxed `TreeMap` lookup per cell per frame.
     */
    val colStyle = IntArray(nCols)
    /**
     * The merges crossing each row. Before, every row draw filtered the sheet's whole merge list —
     * per row, per frame of a horizontal pan — which on a heavily merged report was the single
     * largest cost in the grid.
     */
    private val rowMerges: Array<List<CellRange>?> = arrayOfNulls(nRows)

    init {
        for (c in 0 until nCols) colX[c + 1] = colX[c] + colWidthPx(sheet.colWidthChars(c))
        for (r in 0 until nRows) rowY[r + 1] = rowY[r] + sheet.rowHeightPt(r) * 4f / 3f
        // An IntArray filled in place: the old `(a until b).filter { }.toIntArray()` boxed every row
        // index of a 20 000-row sheet on the main thread each time the layout was rebuilt (every edit).
        val rows = IntArray((nRows - sheet.frozenRows).coerceAtLeast(0))
        var n = 0
        for (r in sheet.frozenRows until nRows) if (!sheet.isRowHidden(r)) rows[n++] = r
        scrollRows = if (n == rows.size) rows else rows.copyOf(n)
        for ((c, info) in sheet.cols) if (c in 0 until nCols && info.style >= 0) colStyle[c] = info.style
        for (m in sheet.merges) {
            val last = minOf(m.r2, nRows - 1)
            for (r in maxOf(m.r1, 0)..last) {
                @Suppress("UNCHECKED_CAST")
                val list = (rowMerges[r] as ArrayList<CellRange>?) ?: ArrayList<CellRange>(2).also { rowMerges[r] = it }
                list.add(m)
            }
        }
    }

    fun colW(c: Int) = colX[c + 1] - colX[c]
    fun rowH(r: Int) = rowY[r + 1] - rowY[r]
    val totalWidth get() = colX[nCols]
    val frozenWidth get() = colX[sheet.frozenCols.coerceAtMost(nCols)]
    val frozenHeight get() = rowY[sheet.frozenRows.coerceAtMost(nRows)]

    /** Merges that cross row [r]; empty for almost every row. */
    fun mergesAt(r: Int): List<CellRange> = rowMerges.getOrNull(r) ?: emptyList()

    /** Column at Excel-px x (clamped), skipping zero-width columns to the right. */
    fun colAt(x: Float): Int {
        var lo = 0
        var hi = nCols - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (colX[mid] <= x) lo = mid else hi = mid - 1
        }
        var c = lo
        while (c < nCols - 1 && colW(c) <= 0f) c++
        return c
    }

    fun rowAt(y: Float): Int {
        var lo = 0
        var hi = nRows - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (rowY[mid] <= y) lo = mid else hi = mid - 1
        }
        var r = lo
        while (r < nRows - 1 && rowH(r) <= 0f) r++
        return r
    }

    /** Index of [row] in [scrollRows] (or the nearest following one). */
    fun itemIndexOf(row: Int): Int {
        val i = java.util.Arrays.binarySearch(scrollRows, row)
        return if (i >= 0) i else (-i - 1).coerceAtMost(scrollRows.size - 1).coerceAtLeast(0)
    }

    companion object {
        /** Excel's character-width → pixel rule for the default 11pt Calibri (max digit width 7). */
        fun colWidthPx(chars: Float): Float = if (chars <= 0f) 0f else (chars * 7f + 5f).coerceAtLeast(4f)
        fun charsFromPx(px: Float): Float = ((px - 5f) / 7f).coerceAtLeast(0.5f)
    }
}

/** Colours the painter needs from the app theme. */
class PaintTheme(
    val isDark: Boolean,
    val surface: Int,
    val ink: Int,
    val gridLine: Int
)

/**
 * Paints cells with `android.graphics` — shared by the on-screen grid (one draw call per row, no
 * per-cell composables) and the PDF export, so the two can never disagree about how a cell looks.
 *
 * **Why everything about a cell is cached in [CellInfo].** A horizontal pan redraws every visible
 * row on every frame, so this runs for ~500 cells per frame. It used to re-derive each cell's facts
 * on each of those frames: `raw.toDoubleOrNull()` (a regex screen in the Kotlin stdlib) up to three
 * times per cell, `ExcelCellFormat.colorFor` (which compiles a `Regex` whenever the code has a
 * `[...]` section), a lower-cased copy of the font name, a fresh `FontMetrics`, and two
 * `measureText` calls. None of that can change while the cell doesn't — a [CellData] is immutable,
 * the style table only ever appends and the theme is fixed per painter — so it is computed once per
 * cell, keyed by identity. An edit replaces the edited cell's [CellData] instance, which is a cache
 * miss by construction, so nothing has to be cleared after an edit either (clearing used to
 * re-format and re-measure every visible cell on the frame after each commit).
 */
class XlsxPainter(private val wb: XlsxWorkbook, val theme: PaintTheme) {

    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fm = Paint.FontMetrics()
    private val dash = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    private val typefaces = HashMap<Int, Typeface>()

    /** Everything the draw path needs about one cell, derived once. */
    private class CellInfo(
        val text: String,
        val style: ResolvedStyle,
        val numeric: Boolean,
        /** Effective horizontal alignment ("general" resolved by value type). */
        val align: String,
        val ink: Int,
        val rich: RichText?,
        val multiline: Boolean,
        /** A date-formatted number: shows `####` when it doesn't fit, like Excel. */
        val isDate: Boolean,
        val typeface: Typeface
    ) {
        /** Text size [measuredWidth] was taken at; a zoom change is the only thing that moves it. */
        var measuredAt = -1f
        var measuredWidth = 0f
    }

    private val infoCache = java.util.IdentityHashMap<CellData, CellInfo>()
    private val layoutCache = object : LinkedHashMap<LayoutKey, StaticLayout>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, StaticLayout>?) = size > 300
    }

    private data class LayoutKey(val cell: CellData, val width: Int, val scale: Float, val wrap: Boolean)

    fun invalidate() { infoCache.clear(); layoutCache.clear() }

    /** What [cell] shows. Main thread only — the cache is not synchronised. */
    fun display(cell: CellData): String = info(cell).text

    private fun info(cell: CellData): CellInfo {
        infoCache[cell]?.let { return it }
        if (infoCache.size > 40_000) infoCache.clear()
        val st = wb.styles.resolve(cell.style)
        val text = wb.display(cell)
        val numeric = cell.type == "" && cell.raw.isNotEmpty() && cell.raw.toDoubleOrNull() != null
        val align = when (st.hAlign) {
            "general" -> when {
                cell.type == "b" || cell.type == "e" -> "center"
                numeric -> "right"
                else -> "left"
            }
            "centerContinuous", "distributed" -> "center"
            "fill", "justify" -> "left"
            else -> st.hAlign
        }
        return CellInfo(
            text = text,
            style = st,
            numeric = numeric,
            align = align,
            ink = inkFor(st, st.fillArgb, cell),
            rich = wb.textOf(cell)?.takeIf { it.isRich },
            multiline = text.indexOf('\n') >= 0,
            isDate = numeric && ExcelCellFormat.isDateCode(st.numFmtCode),
            typeface = typeface(st)
        ).also { infoCache[cell] = it }
    }

    private fun typeface(st: ResolvedStyle): Typeface {
        val fam = when (st.fontName?.lowercase()) {
            null -> 0
            "times new roman", "cambria", "georgia", "garamond", "book antiqua", "palatino linotype" -> 1
            "courier new", "consolas", "lucida console", "courier" -> 2
            else -> 0
        }
        val style = (if (st.bold) Typeface.BOLD else 0) or (if (st.italic) Typeface.ITALIC else 0)
        val key = fam * 4 + style
        return typefaces.getOrPut(key) {
            Typeface.create(
                when (fam) { 1 -> Typeface.SERIF; 2 -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }, style
            )
        }
    }

    /** Ink for a cell: explicit colour, number-format colour, or the theme's, kept legible. */
    private fun inkFor(st: ResolvedStyle, fill: Int?, cell: CellData?): Int {
        val fmtColor = if (cell != null && cell.type == "") ExcelCellFormat.colorFor(cell.raw, st.numFmtCode) else null
        val explicit = fmtColor ?: st.fontArgb
        val bg = fill ?: theme.surface
        if (explicit == null) {
            return if (fill != null) (if (XlsxColors.luminance(fill) < 0.45) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()) else theme.ink
        }
        // Dark mode: "black" text straight on the dark sheet would vanish — that's Excel's
        // automatic/dk1 ink, which on this surface means the theme's light ink.
        val contrast = kotlin.math.abs(XlsxColors.luminance(explicit) - XlsxColors.luminance(bg))
        if (fill == null && theme.isDark && contrast < 0.3) return theme.ink
        return explicit
    }

    /**
     * Draw one row. The canvas origin is the top-left of the cell area (right of the row gutter),
     * in screen pixels; [scale] is screen px per Excel px; [scrollX] is the horizontal scroll of the
     * non-frozen columns, in screen px; [width] the visible cell-area width.
     *
     * [drawsMerge] says whether this row is the one responsible for painting a merge that spans it
     * (the top-most row of the merge currently on screen); other rows in the merge leave it blank.
     */
    fun drawRow(
        canvas: Canvas,
        layout: SheetLayout,
        r: Int,
        scale: Float,
        scrollX: Float,
        width: Float,
        freeze: Boolean = true,
        drawsMerge: (CellRange) -> Boolean
    ) {
        val sheet = layout.sheet
        val h = layout.rowH(r) * scale
        if (h <= 0f) return
        val frozenW = if (freeze) layout.frozenWidth * scale else 0f
        val fc = if (freeze) sheet.frozenCols.coerceAtMost(layout.nCols) else 0
        val rowMerges = layout.mergesAt(r)

        // Scrolled region first, then the frozen columns on top of it.
        drawRegion(canvas, layout, r, h, scale, rowMerges, drawsMerge,
            firstCol = fc, lastCol = layout.nCols - 1,
            originX = frozenW - (layout.colX[fc] * scale) - scrollX,
            clipL = frozenW, clipR = width)
        if (fc > 0) {
            drawRegion(canvas, layout, r, h, scale, rowMerges, drawsMerge,
                firstCol = 0, lastCol = fc - 1, originX = 0f, clipL = 0f, clipR = frozenW)
        }
    }

    private fun inMerge(rowMerges: List<CellRange>, r: Int, c: Int): Boolean {
        if (rowMerges.isEmpty()) return false
        for (i in rowMerges.indices) if (rowMerges[i].contains(r, c)) return true
        return false
    }

    private fun drawRegion(
        canvas: Canvas, layout: SheetLayout, r: Int, h: Float, scale: Float,
        rowMerges: List<CellRange>, drawsMerge: (CellRange) -> Boolean,
        firstCol: Int, lastCol: Int, originX: Float, clipL: Float, clipR: Float
    ) {
        if (clipR <= clipL || lastCol < firstCol) return
        val sheet = layout.sheet
        val row = sheet.rows[r]
        val cells = row?.cells?.takeIf { it.isNotEmpty() }
        val rowStyle = row?.style?.takeIf { it >= 0 }
        val c0 = maxOf(firstCol, layout.colAt((clipL - originX) / scale))
        var c1 = c0
        while (c1 < lastCol && originX + layout.colX[c1 + 1] * scale < clipR) c1++
        canvas.save()
        canvas.clipRect(clipL, -100000f, clipR, h + 100000f)

        // Pass 1: fills + grid lines.
        linePaint.color = theme.gridLine; linePaint.strokeWidth = 1f; linePaint.pathEffect = null
        for (c in c0..c1) {
            val w = layout.colW(c) * scale
            if (w <= 0f) continue
            if (inMerge(rowMerges, r, c)) continue
            val x = originX + layout.colX[c] * scale
            val cell = cells?.get(c)
            val fill = if (cell != null) info(cell).style.fillArgb
            else wb.styles.resolve(rowStyle ?: layout.colStyle[c]).fillArgb
            if (fill != null) {
                fillPaint.color = fill
                canvas.drawRect(x, 0f, x + w, h, fillPaint)
            } else if (sheet.showGridLines) {
                canvas.drawLine(x + w - 0.5f, 0f, x + w - 0.5f, h, linePaint)
                canvas.drawLine(x, h - 0.5f, x + w, h - 0.5f, linePaint)
            }
        }
        if (cells != null) {
            // Pass 2: borders.
            for ((c, cell) in cells.subMap(c0, true, c1, true)) {
                if (inMerge(rowMerges, r, c)) continue
                val w = layout.colW(c) * scale
                if (w <= 0f) continue
                drawBorders(canvas, info(cell).style, originX + layout.colX[c] * scale, 0f, w, h, scale)
            }
            // Pass 3: text, with overflow into empty neighbours.
            val lo = maxOf(firstCol, cells.floorKey(c0)?.let { maxOf(it, c0 - 12) } ?: c0)
            for ((c, cell) in cells.subMap(lo, true, minOf(lastCol, c1 + 12), true)) {
                if (inMerge(rowMerges, r, c)) continue
                val w = layout.colW(c) * scale
                if (w <= 0f) continue
                val ci = info(cell)
                if (ci.text.isEmpty()) continue
                val st = ci.style
                var left = originX + layout.colX[c] * scale
                var right = left + w
                val cellLeft = left
                val cellRight = right
                val align = ci.align
                if (!st.wrap && !ci.numeric && cell.type != "b") {
                    // Excel spills unwrapped text into empty neighbours on the side it grows toward.
                    if (align == "left" || align == "center") {
                        var n = c + 1
                        while (n <= lastCol && n - c < 24 && cells[n]?.let { info(it).text.isEmpty() } != false &&
                            !inMerge(rowMerges, r, n)) {
                            right += layout.colW(n) * scale; n++
                        }
                    }
                    if (align == "right" || align == "center") {
                        var n = c - 1
                        while (n >= firstCol && c - n < 24 && cells[n]?.let { info(it).text.isEmpty() } != false &&
                            !inMerge(rowMerges, r, n)) {
                            left -= layout.colW(n) * scale; n--
                        }
                    }
                }
                drawText(canvas, cell, ci, cellLeft, cellRight, left, right, 0f, h, scale)
            }
        }
        // Pass 4: merges this row is responsible for.
        for (i in rowMerges.indices) {
            val m = rowMerges[i]
            if (m.c2 < c0 || m.c1 > c1 || !drawsMerge(m)) continue
            val x0 = originX + layout.colX[m.c1] * scale
            val x1 = originX + layout.colX[(m.c2 + 1).coerceAtMost(layout.nCols)] * scale
            val y0 = (layout.rowY[m.r1] - layout.rowY[r]) * scale
            val y1 = (layout.rowY[(m.r2 + 1).coerceAtMost(layout.nRows)] - layout.rowY[r]) * scale
            val cell = sheet.cell(m.r1, m.c1)
            val ci = cell?.let { info(it) }
            val st = ci?.style ?: wb.styles.resolve(
                sheet.rows[m.r1]?.style?.takeIf { it >= 0 } ?: layout.colStyle.getOrElse(m.c1) { 0 }
            )
            fillPaint.color = st.fillArgb ?: theme.surface
            canvas.drawRect(x0, y0, x1, y1, fillPaint)
            if (st.fillArgb == null && sheet.showGridLines) {
                linePaint.color = theme.gridLine; linePaint.strokeWidth = 1f; linePaint.pathEffect = null
                canvas.drawRect(x0 + 0.5f, y0 + 0.5f, x1 - 0.5f, y1 - 0.5f, linePaint)
            }
            // Outer borders come from the edge cells of the merge.
            val tl = st
            val br = wb.styles.resolve(sheet.cell(m.r2, m.c2)?.style ?: cell?.style ?: 0)
            drawEdge(canvas, tl.left, x0, y0, x0, y1, scale)
            drawEdge(canvas, tl.top, x0, y0, x1, y0, scale)
            drawEdge(canvas, br.right ?: tl.right, x1, y0, x1, y1, scale)
            drawEdge(canvas, br.bottom ?: tl.bottom, x0, y1, x1, y1, scale)
            if (cell != null && ci != null && ci.text.isNotEmpty()) {
                drawText(canvas, cell, ci, x0, x1, x0, x1, y0, y1, scale)
            }
        }
        canvas.restore()
    }

    private fun drawBorders(canvas: Canvas, st: ResolvedStyle, x: Float, y: Float, w: Float, h: Float, scale: Float) {
        if (st.left == null && st.right == null && st.top == null && st.bottom == null) return
        drawEdge(canvas, st.left, x, y, x, y + h, scale)
        drawEdge(canvas, st.right, x + w, y, x + w, y + h, scale)
        drawEdge(canvas, st.top, x, y, x + w, y, scale)
        drawEdge(canvas, st.bottom, x, y + h, x + w, y + h, scale)
    }

    private fun drawEdge(canvas: Canvas, e: EdgeStyle?, x0: Float, y0: Float, x1: Float, y1: Float, scale: Float) {
        e ?: return
        var color = e.argb ?: (if (theme.isDark) 0xFFD0D0D6.toInt() else 0xFF000000.toInt())
        if (theme.isDark && e.argb != null && XlsxColors.luminance(color) < 0.15) color = 0xFFB0B0B8.toInt()
        linePaint.color = color
        linePaint.pathEffect = if (e.dashed) dash else null
        val sw = (e.weight * scale.coerceAtLeast(0.75f)).coerceAtLeast(1f)
        if (e.double) {
            linePaint.strokeWidth = (sw / 3f).coerceAtLeast(1f)
            val off = sw / 2f
            if (x0 == x1) {
                canvas.drawLine(x0 - off, y0, x1 - off, y1, linePaint)
                canvas.drawLine(x0 + off, y0, x1 + off, y1, linePaint)
            } else {
                canvas.drawLine(x0, y0 - off, x1, y1 - off, linePaint)
                canvas.drawLine(x0, y0 + off, x1, y1 + off, linePaint)
            }
        } else {
            linePaint.strokeWidth = sw
            canvas.drawLine(x0, y0, x1, y1, linePaint)
        }
        linePaint.pathEffect = null
    }

    private fun configurePaint(ci: CellInfo, scale: Float) {
        val st = ci.style
        textPaint.typeface = ci.typeface
        textPaint.textSize = (st.sizePt * 4f / 3f * scale).coerceAtLeast(1f)
        textPaint.color = ci.ink
        textPaint.isUnderlineText = st.underline
        textPaint.isStrikeThruText = st.strike
        textPaint.isFakeBoldText = false
    }

    private fun drawText(
        canvas: Canvas, cell: CellData, ci: CellInfo,
        cellLeft: Float, cellRight: Float, spanLeft: Float, spanRight: Float,
        top: Float, bottom: Float, scale: Float
    ) {
        val st = ci.style
        configurePaint(ci, scale)
        val pad = 3f * scale
        val indent = st.indent * 9f * scale
        val align = ci.align
        val cellW = cellRight - cellLeft
        canvas.save()
        canvas.clipRect(spanLeft, top, spanRight, bottom)
        if (st.wrap || ci.rich != null || ci.multiline) {
            val avail = if (st.wrap) (cellW - 2 * pad - indent).toInt().coerceAtLeast(1) else 100_000
            val layout = staticLayout(cell, ci, avail, scale, align)
            val lw = if (st.wrap) avail.toFloat() else (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            val x = when (align) {
                "right" -> if (st.wrap) cellLeft + pad else cellRight - pad - lw - indent
                "center" -> if (st.wrap) cellLeft + pad else (cellLeft + cellRight - lw) / 2f
                else -> cellLeft + pad + indent
            }
            val lh = layout.height.toFloat()
            val y = when (st.vAlign) {
                "top" -> top + pad / 2
                "center", "justify", "distributed" -> (top + bottom - lh) / 2f
                else -> bottom - pad / 2 - lh
            }
            canvas.translate(x, y)
            layout.draw(canvas)
        } else {
            textPaint.getFontMetrics(fm)
            val textH = fm.descent - fm.ascent
            val baseline = when (st.vAlign) {
                "top" -> top + pad / 2 - fm.ascent
                "center", "justify", "distributed" -> (top + bottom - textH) / 2f - fm.ascent
                else -> bottom - pad / 2 - fm.descent
            }
            var shown = ci.text
            if (ci.measuredAt != textPaint.textSize) {
                ci.measuredWidth = textPaint.measureText(shown)
                ci.measuredAt = textPaint.textSize
            }
            var w2 = ci.measuredWidth
            if (ci.isDate && w2 > cellW - 2 * pad) {
                // A date that doesn't fit shows ##### in Excel.
                shown = "#".repeat(((cellW - 2 * pad) / textPaint.measureText("#")).toInt().coerceAtLeast(1))
                w2 = textPaint.measureText(shown)
            }
            val x = when (align) {
                "right" -> spanRight.coerceAtMost(cellRight) - pad - w2 - indent
                "center" -> (cellLeft + cellRight - w2) / 2f
                else -> cellLeft + pad + indent
            }
            if (st.rotation != 0 && st.rotation != 255) {
                val deg = if (st.rotation <= 90) -st.rotation.toFloat() else (st.rotation - 90).toFloat()
                canvas.rotate(deg, (cellLeft + cellRight) / 2f, (top + bottom) / 2f)
                canvas.drawText(shown, (cellLeft + cellRight - w2) / 2f, (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
            } else {
                canvas.drawText(shown, x, baseline, textPaint)
            }
        }
        canvas.restore()
    }

    private fun staticLayout(cell: CellData, ci: CellInfo, width: Int, scale: Float, align: String): StaticLayout {
        val st = ci.style
        val key = LayoutKey(cell, width, scale, st.wrap)
        layoutCache[key]?.let { return it }
        val text = ci.text
        val rich = ci.rich
        val tp = TextPaint(textPaint)
        val cs: CharSequence = if (rich?.runs == null) text else SpannableString(text).also { sp ->
            for (run in rich.runs) {
                val s = run.start.coerceIn(0, text.length)
                val e = run.end.coerceIn(s, text.length)
                if (e <= s) continue
                val f = FontFacts.of(run.props ?: continue, wb.styles)
                val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                val style = (if (f.bold) Typeface.BOLD else 0) or (if (f.italic) Typeface.ITALIC else 0)
                if (style != 0) sp.setSpan(StyleSpan(style), s, e, flags)
                if (f.underline) sp.setSpan(UnderlineSpan(), s, e, flags)
                if (f.strike) sp.setSpan(StrikethroughSpan(), s, e, flags)
                f.argb?.let { c ->
                    val legible = if (theme.isDark && st.fillArgb == null && XlsxColors.luminance(c) < 0.3) ci.ink else c
                    sp.setSpan(ForegroundColorSpan(legible), s, e, flags)
                }
                if (run.props.child("sz") != null) sp.setSpan(AbsoluteSizeSpan((f.size * 4f / 3f * scale).toInt().coerceAtLeast(1)), s, e, flags)
            }
        }
        val alignment = when {
            !st.wrap -> Layout.Alignment.ALIGN_NORMAL
            align == "right" -> Layout.Alignment.ALIGN_OPPOSITE
            align == "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val layout = StaticLayout.Builder.obtain(cs, 0, cs.length, tp, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .setMaxLines(if (st.wrap) 200 else 50)
            .build()
        layoutCache[key] = layout
        return layout
    }
}

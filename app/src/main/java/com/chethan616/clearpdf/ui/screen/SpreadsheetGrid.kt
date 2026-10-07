package com.chethan616.clearpdf.ui.screen

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.utils.xlsx.CellRange
import com.chethan616.clearpdf.utils.xlsx.SheetLayout
import com.chethan616.clearpdf.utils.xlsx.ValidationKind
import com.chethan616.clearpdf.utils.xlsx.XlsxPainter
import com.chethan616.clearpdf.utils.xlsx.XlsxRefs
import com.chethan616.clearpdf.utils.xlsx.XlsxSheet
import kotlin.math.abs
import kotlin.math.roundToInt

/** The selected range plus the cell typing goes into. */
data class GridSelection(val anchorR: Int, val anchorC: Int, val range: CellRange) {
    val isWholeColumn get() = range.r1 == 0 && range.r2 == XlsxRefs.MaxRow
    val isWholeRow get() = range.c1 == 0 && range.c2 == XlsxRefs.MaxCol

    companion object {
        fun cell(r: Int, c: Int, sheet: XlsxSheet): GridSelection =
            GridSelection(r, c, sheet.mergeAt(r, c) ?: CellRange(r, c, r, c))
    }
}

/**
 * Colours for the grid chrome (headers, selection), derived from the app theme. The ARGB forms are
 * resolved once here: the gutter and header paint with `android.graphics`, and converting (or
 * `copy(alpha)`-ing) a Compose colour per row per frame was pure allocation on the draw path.
 */
class GridColors(
    val surface: Color,
    val headerBand: Color,
    val headerText: Color,
    val headerDivider: Color,
    val accent: Color
) {
    val headerBandArgb = headerBand.toArgb()
    val headerTextArgb = headerText.toArgb()
    val headerDividerArgb = headerDivider.toArgb()
    val accentArgb = accent.toArgb()
    /** Header / gutter band behind a selected column or row. */
    val selBandArgb = accent.copy(alpha = 0.14f).toArgb()
    /** …and behind a whole selected column or row. */
    val wholeSelBandArgb = accent.copy(alpha = 0.30f).toArgb()
}

/**
 * The spreadsheet grid.
 *
 * Every row is a single draw call into [XlsxPainter] — no per-cell composables — inside a
 * `LazyColumn` so vertical scrolling keeps the stacking fling and the row scrubber. Horizontal
 * scrolling is a plain scroll offset read only in the draw phase, so panning sideways never
 * recomposes or re-lays-out a row. Frozen rows sit above the list and frozen columns are painted in
 * place by the painter; the column-letter header and the row-number gutter are sticky.
 *
 * Zoom follows the same rule: it lives in [zoom] and is read only in layout (row heights), draw
 * and gesture code, through [Geo]. A pinch used to write a `var zoom` the whole viewer read during
 * composition, so every pointer event of a pinch recomposed the entire screen and every visible row;
 * now it re-measures the rows and redraws, nothing more.
 */
@Composable
internal fun SpreadsheetGrid(
    sheet: XlsxSheet,
    layout: SheetLayout,
    painter: XlsxPainter,
    version: Int,
    zoom: MutableFloatState,
    listState: LazyListState,
    scrollX: MutableFloatState,
    selection: GridSelection?,
    matches: List<Pair<Int, Int>>,
    currentMatch: Pair<Int, Int>?,
    colors: GridColors,
    editMode: Boolean,
    flingBehavior: androidx.compose.foundation.gestures.FlingBehavior,
    onSelect: (GridSelection) -> Unit,
    onTapSelected: (Int, Int) -> Unit,
    onDropdown: (r: Int, c: Int, anchorInRoot: Rect) -> Unit,
    onColumnResize: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val headerH = with(density) { 28.dp.toPx() }
    val gutterW = with(density) { ((layout.nRows.toString().length * 7 + 22).dp).toPx() }
    val fc = sheet.frozenCols.coerceAtMost(layout.nCols)
    val handleR = with(density) { 7.dp.toPx() }
    val chevronW = with(density) { 22.dp.toPx() }

    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTapSelected by rememberUpdatedState(onTapSelected)
    val currentOnDropdown by rememberUpdatedState(onDropdown)
    val currentOnResize by rememberUpdatedState(onColumnResize)
    val currentEdit by rememberUpdatedState(editMode)
    // Geometry for the current zoom. A derived state, so it is only rebuilt when the zoom or the
    // layout really changes — the old `rememberUpdatedState(Geo(...))` handed every row a new object
    // on every recomposition of this function (a selection change, a keystroke in the formula bar),
    // which invalidated the draw of every visible row even when nothing about them had moved.
    val geo: State<Geo> = remember(layout, density, gutterW, headerH, fc, zoom) {
        derivedStateOf { Geo.of(layout, density.density * zoom.floatValue, fc, gutterW, headerH) }
    }

    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    /** Column being resized and its live width in Excel px. */
    var resize by remember { mutableStateOf<Pair<Int, Float>?>(null) }

    val headerPaint = remember(density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 12.dp.toPx() }
        }
    }
    // Baseline offset of the header labels, measured once (fake-bold doesn't move the metrics).
    val headerTextDy = remember(headerPaint) { headerPaint.fontMetrics.let { -(it.ascent + it.descent) / 2f } }
    val bandPaint = remember { Paint() }
    // Row numbers and column letters, built once per label instead of once per row per frame.
    val labels = remember(layout) { GridLabels(layout.nRows, layout.nCols) }

    // Horizontal pan: an offset in px, clamped to the scrollable width. Width and offset are read
    // only in callbacks and draw lambdas, never in composition.
    val viewportW = remember { mutableFloatStateOf(0f) }
    fun maxScroll(g: Geo): Float =
        ((g.layout.totalWidth - g.layout.frozenWidth) * g.scale - (viewportW.floatValue - g.gutterW - g.frozenW)).coerceAtLeast(0f)
    LaunchedEffect(geo) {
        snapshotFlow { maxScroll(geo.value) }.collect { max ->
            if (scrollX.floatValue > max) scrollX.floatValue = max
        }
    }
    val hState = rememberScrollableState { delta ->
        val old = scrollX.floatValue
        val next = (old - delta).coerceIn(0f, maxScroll(geo.value))
        scrollX.floatValue = next
        old - next
    }

    fun rowRect(g: Geo, r: Int): Pair<Float, Float>? {
        if (r < sheet.frozenRows) return g.frozenTop(r) to g.frozenTop(r + 1)
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull() ?: return null
        // Exact for a row on screen. Item heights are whole pixels, so deriving a row's position by
        // summing the sheet's fractional heights drifted ~0.5 px per row and put the selection
        // frame visibly off its cell a screen further down.
        for (item in visible) if (item.key == r) return g.frozenH + item.offset to g.frozenH + item.offset + item.size
        val firstRow = g.layout.scrollRows.getOrNull(first.index) ?: return null
        val top = g.frozenH + first.offset + (g.layout.rowY[r.coerceAtMost(g.layout.nRows)] - g.layout.rowY[firstRow]) * g.scale
        val bottom = top + (g.layout.rowY[(r + 1).coerceAtMost(g.layout.nRows)] - g.layout.rowY[r.coerceAtMost(g.layout.nRows)]) * g.scale
        return top to bottom
    }

    fun colLeft(g: Geo, c: Int): Float {
        val cc = c.coerceIn(0, g.layout.nCols)
        return if (cc < g.fc) g.layout.colX[cc] * g.scale
        else g.frozenW + (g.layout.colX[cc] - g.layout.colX[g.fc]) * g.scale - scrollX.floatValue
    }

    /** Grid-body point → (row, col); col −1 = row gutter. Null below the last row. */
    fun hit(g: Geo, p: Offset): Pair<Int, Int>? {
        val r = if (p.y < g.frozenH) {
            var fr = 0
            while (fr < sheet.frozenRows - 1 && g.frozenTop(fr + 1) <= p.y) fr++
            fr
        } else {
            val y = p.y - g.frozenH
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
            g.layout.scrollRows.getOrNull(item.index) ?: return null
        }
        val cx = p.x - g.gutterW
        if (cx < 0f) return r to -1
        val c = if (cx < g.frozenW) g.layout.colAt(cx / g.scale)
        else g.layout.colAt(g.layout.colX[g.fc] + (cx - g.frozenW + scrollX.floatValue) / g.scale)
        return r to c
    }

    fun selectionRect(g: Geo, s: GridSelection): Rect? {
        val r2 = s.range.r2.coerceAtMost(g.layout.nRows - 1)
        val c2 = s.range.c2.coerceAtMost(g.layout.nCols - 1)
        val top = rowRect(g, s.range.r1)?.first ?: return null
        val bottom = rowRect(g, r2)?.second ?: return null
        val left = g.gutterW + colLeft(g, s.range.c1)
        val right = g.gutterW + colLeft(g, c2 + 1)
        return Rect(left, top, right, bottom)
    }

    fun chevronRect(g: Geo, s: GridSelection): Rect? {
        if (!currentEdit) return null
        if (!s.range.isSingle && sheet.mergeAt(s.anchorR, s.anchorC) != s.range) return null
        val v = sheet.validationAt(s.anchorR, s.anchorC) ?: return null
        if (v.kind != ValidationKind.LIST || v.showDropDown) return null
        val rect = selectionRect(g, s) ?: return null
        val h = minOf(rect.height, chevronW)
        return Rect(rect.right + 2f, rect.bottom - h, rect.right + 2f + chevronW, rect.bottom)
    }

    Column(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { viewportW.floatValue = it.size.width.toFloat(); gridOrigin = it.positionInRoot() }
            .scrollable(hState, Orientation.Horizontal)
            .pointerInput(zoom) {
                // Pinch to zoom; single-finger gestures pass through to scrolling.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.count { it.pressed } >= 2) {
                            val z = e.calculateZoom()
                            if (z != 1f) {
                                zoom.floatValue = (zoom.floatValue * z).coerceIn(0.5f, 2.5f)
                                e.changes.forEach { it.consume() }
                            }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        // ── Column header ──────────────────────────────────────────────────────
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    // Column resize: drag a header divider (edit mode).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!currentEdit) return@awaitEachGesture
                        val g = geo.value
                        val slop = 12.dp.toPx()
                        var col = -1
                        for (c in 0 until g.layout.nCols) {
                            val edge = g.gutterW + colLeft(g, c + 1)
                            if (edge > size.width + slop) break
                            if (abs(down.position.x - edge) < slop && g.layout.colW(c) > 0f) { col = c; break }
                        }
                        if (col < 0) return@awaitEachGesture
                        down.consume()
                        var w = g.layout.colW(col)
                        resize = col to w
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            w = (w + ch.positionChange().x / g.scale).coerceIn(12f, 1200f)
                            resize = col to w
                            ch.consume()
                        }
                        resize = null
                        currentOnResize(col, SheetLayout.charsFromPx(w))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val g = geo.value
                        val cx = p.x - g.gutterW
                        if (cx < 0f) return@detectTapGestures
                        val c = if (cx < g.frozenW) g.layout.colAt(cx / g.scale)
                        else g.layout.colAt(g.layout.colX[g.fc] + (cx - g.frozenW + scrollX.floatValue) / g.scale)
                        currentOnSelect(GridSelection(0, c, CellRange(0, c, XlsxRefs.MaxRow, c)))
                    }
                }
                .drawBehind {
                    val g = geo.value
                    val sel = currentSelection
                    drawRect(colors.headerBand)
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.clipRect(g.gutterW, 0f, size.width, size.height)
                        val baseline = size.height / 2f + headerTextDy
                        fun drawCol(c: Int) {
                            val w = g.layout.colW(c) * g.scale
                            if (w <= 0f) return
                            val x = g.gutterW + colLeft(g, c)
                            if (x > size.width || x + w < g.gutterW) return
                            val inSel = sel != null && c in sel.range.c1..sel.range.c2
                            if (inSel) {
                                bandPaint.color = if (sel!!.isWholeColumn) colors.wholeSelBandArgb else colors.selBandArgb
                                nc.drawRect(x, 0f, x + w, size.height, bandPaint)
                            }
                            headerPaint.color = if (inSel) colors.accentArgb else colors.headerTextArgb
                            headerPaint.isFakeBoldText = inSel
                            nc.drawText(labels.col(c), x + w / 2f, baseline, headerPaint)
                            bandPaint.color = colors.headerDividerArgb
                            nc.drawRect(x + w - 1f, size.height * 0.22f, x + w, size.height * 0.78f, bandPaint)
                        }
                        // Scrolling columns, then frozen ones over them.
                        val startC = g.layout.colAt(g.layout.colX[g.fc] + scrollX.floatValue / g.scale)
                        var c = maxOf(g.fc, startC)
                        nc.save()
                        nc.clipRect(g.gutterW + g.frozenW, 0f, size.width, size.height)
                        while (c < g.layout.nCols && g.gutterW + colLeft(g, c) < size.width) { drawCol(c); c++ }
                        nc.restore()
                        for (fcI in 0 until g.fc) drawCol(fcI)
                        nc.restore()
                    }
                    drawLine(colors.headerDivider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                    drawLine(colors.headerDivider, Offset(g.gutterW - 0.5f, 0f), Offset(g.gutterW - 0.5f, size.height), 1f)
                    if (g.frozenW > 0f) drawLine(colors.headerDivider, Offset(g.gutterW + g.frozenW, 0f), Offset(g.gutterW + g.frozenW, size.height), 2f)
                    resize?.let { (rc, w) ->
                        val x = g.gutterW + colLeft(g, rc) + w * g.scale
                        drawLine(colors.accent, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    }
                }
        )

        // ── Body ───────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .pointerInput(Unit) {
                    // Range handles: grabbed before the list sees the drag.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val sel = currentSelection ?: return@awaitEachGesture
                        val g = geo.value
                        val rect = selectionRect(g, sel) ?: return@awaitEachGesture
                        val grab = handleR * 3.2f
                        val tl = (down.position - rect.topLeft).getDistance() < grab
                        val br = (down.position - rect.bottomRight).getDistance() < grab
                        if (!tl && !br) return@awaitEachGesture
                        down.consume()
                        val fixedR = if (br) sel.range.r1 else sel.range.r2
                        val fixedC = if (br) sel.range.c1 else sel.range.c2
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            val h = hit(g, ch.position) ?: continue
                            val c = h.second.coerceAtLeast(0)
                            var range = CellRange.of(fixedR, fixedC, h.first, c)
                            // Grow to cover any merge the range cuts through.
                            for (m in sheet.merges) if (m.r1 <= range.r2 && m.r2 >= range.r1 && m.c1 <= range.c2 && m.c2 >= range.c1) {
                                range = CellRange(minOf(range.r1, m.r1), minOf(range.c1, m.c1), maxOf(range.r2, m.r2), maxOf(range.c2, m.c2))
                            }
                            currentOnSelect(sel.copy(range = range))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val g = geo.value
                        val sel = currentSelection
                        if (sel != null) {
                            chevronRect(g, sel)?.let { cr ->
                                if (cr.inflate(8.dp.toPx()).contains(p)) {
                                    val rect = selectionRect(g, sel) ?: cr
                                    currentOnDropdown(sel.anchorR, sel.anchorC, rect.translate(gridOrigin + Offset(0f, g.headerH)))
                                    return@detectTapGestures
                                }
                            }
                        }
                        val (r, c) = hit(g, p) ?: return@detectTapGestures
                        if (c < 0) {
                            currentOnSelect(GridSelection(r, 0, CellRange(r, 0, r, XlsxRefs.MaxCol)))
                            return@detectTapGestures
                        }
                        val next = GridSelection.cell(r, c, sheet)
                        if (sel != null && sel.range == next.range && sel.anchorR == next.anchorR && sel.anchorC == next.anchorC) {
                            currentOnTapSelected(next.anchorR, next.anchorC)
                        } else currentOnSelect(next)
                    }
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                // Frozen rows: pinned above the scrolling list.
                for (r in 0 until sheet.frozenRows.coerceAtMost(layout.nRows)) {
                    if (layout.rowH(r) <= 0f) continue
                    Spacer(
                        Modifier.fillMaxWidth().rowHeight(layout, r, geo)
                            .drawBehind { drawGridRow(r, painter, layout, geo.value, scrollX.floatValue, colors, headerPaint, headerTextDy, bandPaint, labels, currentSelection) { m -> m.r1 == r } }
                    )
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    flingBehavior = flingBehavior
                ) {
                    items(count = layout.scrollRows.size, key = { layout.scrollRows[it] }) { i ->
                        val r = layout.scrollRows[i]
                        Spacer(
                            Modifier.fillMaxWidth().rowHeight(layout, r, geo)
                                .drawBehind {
                                    @Suppress("UNUSED_EXPRESSION") version
                                    drawGridRow(r, painter, layout, geo.value, scrollX.floatValue, colors, headerPaint, headerTextDy, bandPaint, labels, currentSelection) { m ->
                                        val firstRow = layout.scrollRows.getOrElse(listState.firstVisibleItemIndex) { 0 }
                                        r == maxOf(m.r1, firstRow, sheet.frozenRows)
                                    }
                                }
                        )
                    }
                }
            }
            if (layout.frozenHeight > 0f) {
                Spacer(Modifier.fillMaxSize().drawBehind {
                    val fh = geo.value.frozenH
                    drawLine(colors.headerDivider, Offset(0f, fh), Offset(size.width, fh), 2f)
                })
            }
            if (layout.frozenWidth > 0f) {
                Spacer(Modifier.fillMaxSize().drawBehind {
                    val g = geo.value
                    drawLine(colors.headerDivider, Offset(g.gutterW + g.frozenW, 0f), Offset(g.gutterW + g.frozenW, size.height), 2f)
                })
            }

            // ── Overlay: search hits, selection, handles, dropdown chevron ───────
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val g = geo.value
                    @Suppress("UNUSED_EXPRESSION") version
                    clipRect(g.gutterW) {
                        if (matches.isNotEmpty()) {
                            fun paint(m: Pair<Int, Int>) {
                                val rect = selectionRect(g, GridSelection.cell(m.first, m.second, sheet)) ?: return
                                drawRect(colors.accent.copy(alpha = if (m == currentMatch) 0.45f else 0.18f), rect.topLeft, rect.size)
                            }
                            // Matches come out of the scan sorted by row, so only the slice that is on
                            // screen is visited — not all (up to 5 000) on every scroll frame.
                            var i = 0
                            while (i < matches.size && matches[i].first < sheet.frozenRows) paint(matches[i++])
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val firstRow = (visible.firstOrNull()?.key as? Int) ?: 0
                            val lastRow = (visible.lastOrNull()?.key as? Int) ?: layout.nRows
                            var j = lowerBoundByRow(matches, maxOf(firstRow, sheet.frozenRows))
                            while (j < matches.size && matches[j].first <= lastRow) paint(matches[j++])
                        }
                        val sel = currentSelection ?: return@clipRect
                        val rect = selectionRect(g, sel) ?: return@clipRect
                        if (!sel.range.isSingle && sheet.mergeAt(sel.anchorR, sel.anchorC) != sel.range) {
                            drawRect(colors.accent.copy(alpha = 0.10f), rect.topLeft, rect.size)
                        }
                        drawRect(colors.accent, rect.topLeft, rect.size, style = Stroke(2.dp.toPx()))
                        if (currentEdit || !sel.range.isSingle) {
                            for (pt in listOf(rect.topLeft, rect.bottomRight)) {
                                drawCircle(Color.White, handleR + 1.5.dp.toPx(), pt)
                                drawCircle(colors.accent, handleR, pt)
                            }
                        }
                        chevronRect(g, sel)?.let { cr ->
                            drawRoundRect(
                                colors.surface, cr.topLeft, cr.size,
                                androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                            )
                            drawRoundRect(
                                colors.headerDivider, cr.topLeft, cr.size,
                                androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(1f)
                            )
                            val cx = cr.center.x
                            val cy = cr.center.y
                            val s = 4.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(cx - s, cy - s / 2); lineTo(cx, cy + s / 2); lineTo(cx + s, cy - s / 2)
                            }
                            drawPath(path, colors.accent, style = Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                    resize?.let { (rc, w) ->
                        val x = g.gutterW + colLeft(g, rc) + w * g.scale
                        drawLine(colors.accent, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                    }
                }
            )
        }
    }
}

/** First index in [matches] (sorted by row) whose row is ≥ [row]. */
private fun lowerBoundByRow(matches: List<Pair<Int, Int>>, row: Int): Int {
    var lo = 0
    var hi = matches.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (matches[mid].first < row) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * A row's height at the current zoom, read in the *measure* pass. A zoom change re-measures the
 * rows; it never recomposes them.
 */
private fun Modifier.rowHeight(sheetLayout: SheetLayout, r: Int, geo: State<Geo>): Modifier =
    layout { measurable, constraints ->
        val h = (sheetLayout.rowH(r) * geo.value.scale).roundToInt().coerceAtLeast(0)
        val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(p.width, h) { p.place(0, 0) }
    }

/** Lazily built row-number and column-letter strings. */
internal class GridLabels(nRows: Int, nCols: Int) {
    private val rows = arrayOfNulls<String>(nRows + 1)
    private val cols = arrayOfNulls<String>(nCols + 1)
    fun row(r: Int): String = rows.getOrNull(r)?: (r + 1).toString().also { if (r in rows.indices) rows[r] = it }
    fun col(c: Int): String = cols.getOrNull(c) ?: XlsxRefs.colLetter(c).also { if (c in cols.indices) cols[c] = it }
}

/** Geometry for one zoom level, captured so gesture handlers never see a stale layout. */
internal class Geo(
    val layout: SheetLayout,
    val scale: Float,
    val frozenW: Float,
    /** Height of the frozen band as laid out: the sum of the rows' whole-pixel heights. */
    val frozenH: Float,
    val fc: Int,
    val gutterW: Float,
    val headerH: Float,
    private val frozenTops: FloatArray
) {
    /** Top of frozen row [r] (or the band's bottom for `r == frozenRows`), in laid-out pixels. */
    fun frozenTop(r: Int): Float = frozenTops[r.coerceIn(0, frozenTops.size - 1)]

    companion object {
        fun of(layout: SheetLayout, scale: Float, fc: Int, gutterW: Float, headerH: Float): Geo {
            val fr = layout.sheet.frozenRows.coerceAtMost(layout.nRows)
            val tops = FloatArray(fr + 1)
            for (r in 0 until fr) tops[r + 1] = tops[r] + (layout.rowH(r) * scale).roundToInt().coerceAtLeast(0)
            return Geo(layout, scale, layout.frozenWidth * scale, tops[fr], fc, gutterW, headerH, tops)
        }
    }
}

private inline fun DrawScope.clipRect(left: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.clipRect(left, 0f, size.width, size.height)
    block()
    drawContext.canvas.restore()
}

/** Gutter (row number) + the row's cells, via the shared painter. */
private fun DrawScope.drawGridRow(
    r: Int,
    painter: XlsxPainter,
    layout: SheetLayout,
    g: Geo,
    scrollX: Float,
    colors: GridColors,
    headerPaint: Paint,
    headerTextDy: Float,
    bandPaint: Paint,
    labels: GridLabels,
    sel: GridSelection?,
    drawsMerge: (CellRange) -> Boolean
) {
    val h = size.height
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        nc.save()
        nc.translate(g.gutterW, 0f)
        nc.clipRect(0f, -100_000f, size.width - g.gutterW, h + 100_000f)
        painter.drawRow(nc, layout, r, g.scale, scrollX, size.width - g.gutterW, drawsMerge = drawsMerge)
        nc.restore()

        // Sticky row-number gutter, drawn last so nothing paints over it.
        val inSel = sel != null && r in sel.range.r1..sel.range.r2
        bandPaint.color = colors.headerBandArgb
        nc.drawRect(0f, 0f, g.gutterW, h, bandPaint)
        if (inSel) {
            bandPaint.color = if (sel!!.isWholeRow) colors.wholeSelBandArgb else colors.selBandArgb
            nc.drawRect(0f, 0f, g.gutterW, h, bandPaint)
        }
        headerPaint.color = if (inSel) colors.accentArgb else colors.headerTextArgb
        headerPaint.isFakeBoldText = inSel
        if (h > headerPaint.textSize * 0.8f) nc.drawText(labels.row(r), g.gutterW / 2f, h / 2f + headerTextDy, headerPaint)
        bandPaint.color = colors.headerDividerArgb
        nc.drawRect(g.gutterW - 1f, 0f, g.gutterW, h, bandPaint)
        nc.drawRect(g.gutterW * 0.25f, h - 1f, g.gutterW * 0.75f, h, bandPaint)
    }
}

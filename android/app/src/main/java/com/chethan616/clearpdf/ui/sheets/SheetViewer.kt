package com.chethan616.clearpdf.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.CellSpan
import com.chethan616.clearpdf.core.CellView
import com.chethan616.clearpdf.core.SheetInfo
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.glass.GlassChip
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassLoader
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min

/** Rows and columns fetched together from the engine. */
private const val BLOCK_ROWS = 50
private const val BLOCK_COLS = 20

/** How many rows and columns past the used area the grid still shows (room to look around). */
private const val SPARE_ROWS = 40
private const val SPARE_COLS = 8

/** The heading strips: column letters on top, row numbers at the left (points). */
private const val HEAD_H = 20f
private const val HEAD_W = 36f

/** One sheet's layout: where every row and column starts (points from A1), its merges and freeze. */
private class SheetLayout(val info: SheetInfo, val colX: FloatArray, val rowY: FloatArray, val merges: List<CellSpan>) {
    val cols get() = colX.size - 1
    val rows get() = rowY.size - 1

    /** The first index whose start is past [v] in [edges], less one: the line [v] falls in. */
    fun indexAt(edges: FloatArray, v: Float): Int {
        var lo = 0
        var hi = edges.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (edges[mid] <= v) lo = mid else hi = mid - 1
        }
        return lo.coerceAtMost(edges.size - 2).coerceAtLeast(0)
    }
}

/** "A", "B", …, "Z", "AA", … */
private fun columnName(c: Int): String {
    var n = c + 1
    val sb = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        sb.insert(0, 'A' + r)
        n = (n - 1) / 26
    }
    return sb.toString()
}

private fun rgb(c: UInt): Color = Color(0xFF000000.toInt() or c.toInt())

/**
 * A workbook: its sheets as a grid that pans and zooms under glass bars, the heading strips and
 * the frozen panes held in place, a sheet chosen from the tabs at the bottom.
 */
@Composable
fun SheetViewer(vm: AppViewModel, doc: OpenDocument) {
    val eng = vm.engine ?: return
    val c = clear
    var sheets by remember { mutableStateOf<List<SheetInfo>>(emptyList()) }
    var current by remember { mutableIntStateOf(0) }
    var layout by remember { mutableStateOf<SheetLayout?>(null) }
    // blocks of cells by (sheet, block row, block column), filled in as they come into view
    val blocks = remember { mutableStateMapOf<Triple<Int, Int, Int>, List<CellView>>() }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // the grid starts under the top row of glass (it runs on beneath it, for the glass to refract)
    val topPx = with(density) { WindowInsets.statusBars.getTop(this) + 64.dp.toPx() }

    LaunchedEffect(doc.id) {
        sheets = runCatching { eng.command { sheets(doc.id) } }.getOrDefault(emptyList())
        current = sheets.indexOfFirst { !it.hidden }.coerceAtLeast(0)
    }
    LaunchedEffect(sheets, current) {
        val info = sheets.getOrNull(current) ?: return@LaunchedEffect
        layout = null
        pan = Offset.Zero
        val rows = min(info.rows.toInt() + SPARE_ROWS, 20_000)
        val cols = min(info.cols.toInt() + SPARE_COLS, 2_000)
        val l = runCatching {
            eng.command {
                val axes = sheetAxes(doc.id, current.toUInt(), 0u, rows.toUInt(), 0u, cols.toUInt())
                val colX = FloatArray(cols + 1).also { a -> axes.colWidths.forEachIndexed { i, w -> a[i + 1] = a[i] + w } }
                val rowY = FloatArray(rows + 1).also { a -> axes.rowHeights.forEachIndexed { i, h -> a[i + 1] = a[i] + h } }
                SheetLayout(info, colX, rowY, sheetMerges(doc.id, current.toUInt()))
            }
        }.getOrNull()
        layout = l
    }

    val backdrop = rememberLayerBackdrop()
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val sheetIndex = current
    Box(Modifier.fillMaxSize()) {
        // the grid, recorded as the bars' backdrop: their glass refracts the cells
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val l = layout
            if (l == null) {
                Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) { GlassLoader(size = 64.dp) }
            } else {
                val px = density.density
                // the blocks in view, asked for as the grid moves
                LaunchedEffect(l) {
                    snapshotFlow { Triple(pan, scale, l) }
                        .distinctUntilChanged()
                        .collect { (p, s, lay) ->
                            val k = px * s
                            val x0 = (-p.x / k).coerceAtLeast(0f)
                            val y0 = (-p.y / k).coerceAtLeast(0f)
                            val c0 = lay.indexAt(lay.colX, x0) / BLOCK_COLS
                            val r0 = lay.indexAt(lay.rowY, y0) / BLOCK_ROWS
                            for (br in r0..r0 + 1) for (bc in c0..c0 + 1) {
                                val key = Triple(sheetIndex, br, bc)
                                if (key in blocks || br * BLOCK_ROWS >= lay.rows || bc * BLOCK_COLS >= lay.cols) continue
                                blocks[key] = emptyList()
                                blocks[key] = runCatching {
                                    eng.command { sheetCells(doc.id, sheetIndex.toUInt(), (br * BLOCK_ROWS).toUInt(), BLOCK_ROWS.toUInt(), (bc * BLOCK_COLS).toUInt(), BLOCK_COLS.toUInt()) }
                                }.getOrDefault(emptyList())
                            }
                        }
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(l) {
                            detectTransformGestures { centroid, move, zoom, _ ->
                                val s = (scale * zoom).coerceIn(0.4f, 4f)
                                val f = s / scale
                                val k = px * s
                                val maxX = (l.colX.last() * k - size.width / 2f).coerceAtLeast(0f)
                                val maxY = (l.rowY.last() * k - size.height / 2f).coerceAtLeast(0f)
                                // zoom about the fingers, then follow them
                                val p = (pan - centroid) * f + centroid + move
                                pan = Offset(p.x.coerceIn(-maxX, 0f), p.y.coerceIn(-maxY, 0f))
                                scale = s
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1.2f) 1f else 2f; pan = Offset.Zero }) },
                ) {
                    drawRect(Color.White)
                    translate(top = topPx) { drawSheet(l, blocks, sheetIndex, px * scale, pan, measurer, c.ink) }
                }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // the top row: back, the workbook's title and the sheet shown, share
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = { vm.closeDocument() }, label = stringResource(R.string.common_back)) { GlassIcon(LucideIcons.ChevronLeft, null, Modifier.size(22.dp)) }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f).glassBar().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(doc.title, style = ClearType.BodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        sheets.getOrNull(current)?.let { Text(it.name, style = ClearType.Caption, color = c.inkSoft, maxLines = 1) }
                    }
                    Spacer(Modifier.size(10.dp))
                    GlassCircle(onClick = { vm.share(doc) }, label = stringResource(R.string.common_share)) { GlassIcon(LucideIcons.Share2, null, Modifier.size(20.dp)) }
                }
            }
            // the sheet tabs
            val visible = sheets.withIndex().filter { !it.value.hidden }
            if (visible.size > 1) Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((i, s) in visible) {
                        val chosen = i == current
                        GlassChip(
                            onClick = { current = i },
                            fill = if (chosen) ({ com.chethan616.clearpdf.ui.theme.Candy.Lemon }) else s.tabColor?.let { t -> { rgb(t).copy(alpha = 0.35f) } },
                            ink = if (chosen) Color(0xFF172033) else c.ink,
                        ) { Text(s.name, style = ClearType.Body.copy(fontSize = 14.sp), maxLines = 1) }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * The sheet at [k] pixels per point, moved by [pan]: the body, then the frozen rows and columns
 * held still on their axis, then the heading strips, each clipped to its own region.
 */
private fun DrawScope.drawSheet(
    l: SheetLayout,
    blocks: Map<Triple<Int, Int, Int>, List<CellView>>,
    sheet: Int,
    k: Float,
    pan: Offset,
    measurer: TextMeasurer,
    ink: Color,
) {
    val head = Offset(HEAD_W * k.coerceAtMost(density * 1.5f), HEAD_H * k.coerceAtMost(density * 1.5f))
    val fr = min(l.info.frozenRows.toInt(), l.rows)
    val fc = min(l.info.frozenCols.toInt(), l.cols)
    val frozenW = l.colX[fc] * k
    val frozenH = l.rowY[fr] * k
    val body = Rect(head.x + frozenW, head.y + frozenH, size.width, size.height)
    // a region of the sheet: rows/columns from the given starts, drawn from [origin]
    fun region(clip: Rect, origin: Offset, rowsFrom: Int, rowsTo: Int, colsFrom: Int, colsTo: Int, scrollX: Boolean, scrollY: Boolean) {
        if (clip.width <= 0f || clip.height <= 0f) return
        val ox = origin.x + (if (scrollX) pan.x else 0f)
        val oy = origin.y + (if (scrollY) pan.y else 0f)
        // the lines in view
        val c0 = max(colsFrom, l.indexAt(l.colX, (clip.left - ox) / k))
        val c1 = min(colsTo, l.indexAt(l.colX, (clip.right - ox) / k) + 1)
        val r0 = max(rowsFrom, l.indexAt(l.rowY, (clip.top - oy) / k))
        val r1 = min(rowsTo, l.indexAt(l.rowY, (clip.bottom - oy) / k) + 1)
        if (c0 >= c1 || r0 >= r1) return
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val cell = { r: Int, c: Int -> Rect(ox + l.colX[c] * k, oy + l.rowY[r] * k, ox + l.colX[c + 1] * k, oy + l.rowY[r + 1] * k) }
            // grid lines
            if (l.info.gridlines) {
                val line = Color(0xFFE1E4EA)
                for (c in c0..c1) drawLine(line, Offset(ox + l.colX[c] * k, clip.top), Offset(ox + l.colX[c] * k, clip.bottom), 1f)
                for (r in r0..r1) drawLine(line, Offset(clip.left, oy + l.rowY[r] * k), Offset(clip.right, oy + l.rowY[r] * k), 1f)
            }
            // the cells of the blocks in view: fills, then borders, then text
            val shown = ArrayList<CellView>()
            for (br in r0 / BLOCK_ROWS..(r1 - 1) / BLOCK_ROWS) for (bc in c0 / BLOCK_COLS..(c1 - 1) / BLOCK_COLS) {
                blocks[Triple(sheet, br, bc)]?.forEach { v -> if (v.row.toInt() in r0 until r1 && v.col.toInt() in c0 until c1) shown += v }
            }
            fun spanOf(v: CellView): Rect {
                val m = l.merges.firstOrNull { it.row0 == v.row && it.col0 == v.col }
                return if (m == null) cell(v.row.toInt(), v.col.toInt())
                else cell(m.row0.toInt(), m.col0.toInt()).let { a ->
                    val b = cell(min(m.row1.toInt(), l.rows - 1), min(m.col1.toInt(), l.cols - 1))
                    Rect(a.left, a.top, b.right, b.bottom)
                }
            }
            for (v in shown) v.fill?.let { drawRect(rgb(it), spanOf(v).topLeft, spanOf(v).size) }
            for (v in shown) {
                val r = spanOf(v)
                fun edge(e: com.chethan616.clearpdf.core.CellEdge, a: Offset, b: Offset) {
                    if (e.weight > 0u) drawLine(rgb(e.color), a, b, e.weight.toFloat() * density * 0.6f)
                }
                edge(v.left, r.topLeft, r.bottomLeft)
                edge(v.top, r.topLeft, r.topRight)
                edge(v.right, r.topRight, r.bottomRight)
                edge(v.bottom, r.bottomLeft, r.bottomRight)
            }
            for (v in shown) {
                if (v.text.isEmpty()) continue
                val r = spanOf(v)
                val pad = 2f * k
                val maxW = (r.width - 2 * pad).toInt()
                if (maxW <= 0) continue
                val style = TextStyle(
                    color = rgb(v.ink),
                    fontSize = (v.size * k / density / fontScale).sp,
                    fontWeight = if (v.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (v.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = when {
                        v.underline && v.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        v.underline -> TextDecoration.Underline
                        v.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                )
                val t = measurer.measure(
                    v.text, style,
                    overflow = TextOverflow.Clip,
                    softWrap = v.wrap, maxLines = if (v.wrap) Int.MAX_VALUE else 1,
                    constraints = Constraints(maxWidth = if (v.wrap) maxW else Constraints.Infinity, maxHeight = max(1, r.height.toInt())),
                )
                val w = t.size.width.toFloat()
                val x = when (v.align) {
                    "c" -> r.left + (r.width - w) / 2f
                    "r" -> r.right - pad - w
                    else -> r.left + pad
                }
                val y = when (v.valign) {
                    "t" -> r.top + pad / 2
                    "c" -> r.top + (r.height - t.size.height) / 2f
                    else -> r.bottom - pad / 2 - t.size.height
                }
                // text stays within its cell (a neighbour's text would overlap it otherwise)
                clipRect(r.left, r.top, r.right, r.bottom) { drawText(t, topLeft = Offset(x, y)) }
            }
        }
    }
    // body, then the frozen strips, then the corner where both are frozen
    region(body, head, fr, l.rows, fc, l.cols, scrollX = true, scrollY = true)
    region(Rect(head.x + frozenW, head.y, size.width, head.y + frozenH), head, 0, fr, fc, l.cols, scrollX = true, scrollY = false)
    region(Rect(head.x, head.y + frozenH, head.x + frozenW, size.height), head, fr, l.rows, 0, fc, scrollX = false, scrollY = true)
    region(Rect(head.x, head.y, head.x + frozenW, head.y + frozenH), head, 0, fr, 0, fc, scrollX = false, scrollY = false)
    if (fr > 0) drawLine(Color(0xFF9AA3B2), Offset(head.x, head.y + frozenH), Offset(size.width, head.y + frozenH), density)
    if (fc > 0) drawLine(Color(0xFF9AA3B2), Offset(head.x + frozenW, head.y), Offset(head.x + frozenW, size.height), density)
    // the heading strips
    val strip = Color(0xFFF2F4F8)
    val label = TextStyle(color = ink.copy(alpha = 0.6f), fontSize = (11 * min(k / density, 1.5f) / fontScale).sp)
    drawRect(strip, Offset(0f, 0f), Size(size.width, head.y))
    drawRect(strip, Offset(0f, 0f), Size(head.x, size.height))
    val lineC = Color(0xFFD5D9E0)
    clipRect(head.x, 0f, size.width, head.y) {
        for (c in 0 until l.cols) {
            val x0 = head.x + l.colX[c] * k + (if (c >= fc) pan.x else 0f)
            val x1 = head.x + l.colX[c + 1] * k + (if (c >= fc) pan.x else 0f)
            if (x1 < head.x + (if (c >= fc) frozenW else 0f) || x0 > size.width || x1 - x0 < 1f) continue
            drawLine(lineC, Offset(x1, 0f), Offset(x1, head.y), 1f)
            val t = measurer.measure(columnName(c), label)
            if (t.size.width < x1 - x0) drawText(t, topLeft = Offset((x0 + x1 - t.size.width) / 2f, (head.y - t.size.height) / 2f))
        }
    }
    clipRect(0f, head.y, head.x, size.height) {
        for (r in 0 until l.rows) {
            val y0 = head.y + l.rowY[r] * k + (if (r >= fr) pan.y else 0f)
            val y1 = head.y + l.rowY[r + 1] * k + (if (r >= fr) pan.y else 0f)
            if (y1 < head.y + (if (r >= fr) frozenH else 0f) || y0 > size.height || y1 - y0 < 1f) continue
            drawLine(lineC, Offset(0f, y1), Offset(head.x, y1), 1f)
            val t = measurer.measure((r + 1).toString(), label)
            if (t.size.height < y1 - y0 + 2) drawText(t, topLeft = Offset((head.x - t.size.width) / 2f, (y0 + y1 - t.size.height) / 2f))
        }
    }
    drawLine(lineC, Offset(0f, head.y), Offset(size.width, head.y), 1f)
    drawLine(lineC, Offset(head.x, 0f), Offset(head.x, size.height), 1f)
}

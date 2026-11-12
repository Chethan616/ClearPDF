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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
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
import com.chethan616.clearpdf.ui.glass.ChromeCapsule
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import kotlinx.coroutines.delay
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.engine.userMessage
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassTextField
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.Capsule
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.pdfColors
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.launch
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

/** One row-and-column pair as a map key. */
private fun cellKey(row: Int, col: Int): Long = (row.toLong() shl 24) or col.toLong()

/**
 * One sheet's layout: where every row and column starts (points from A1), its merges and freeze.
 * A cell's merge is looked up in a map (a form can hold hundreds of merges, and every cell drawn
 * asks for its own on every frame); only the few very large ones are searched.
 */
internal class SheetLayout(val info: SheetInfo, val colX: FloatArray, val rowY: FloatArray, val merges: List<CellSpan>) {
    val cols get() = colX.size - 1
    val rows get() = rowY.size - 1

    private val mergeOf = HashMap<Long, CellSpan>()
    private val bigMerges = ArrayList<CellSpan>()

    init {
        for (m in merges) {
            val h = (m.row1 - m.row0).toLong() + 1
            val w = (m.col1 - m.col0).toLong() + 1
            if (h * w > 4096 || mergeOf.size > 400_000) bigMerges += m
            else for (r in m.row0.toInt()..m.row1.toInt()) for (c in m.col0.toInt()..m.col1.toInt()) mergeOf[cellKey(r, c)] = m
        }
    }

    /** The merged area [row], [col] is part of, if it is. */
    fun spanAt(row: Int, col: Int): CellSpan? =
        mergeOf[cellKey(row, col)] ?: bigMerges.firstOrNull { row.toUInt() in it.row0..it.row1 && col.toUInt() in it.col0..it.col1 }

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
 * A cell made ready to draw, once, when its block arrives: the area its fill and text take (its
 * merge, or itself), which of its own edges show (inside a merge only those on the merge's rim),
 * and its colours. [covered] cells lie inside a merge but are not its first cell: they draw no fill
 * or text of their own.
 */
internal class DrawCell(val v: CellView, val row: Int, val col: Int, val r0: Int, val c0: Int, val r1: Int, val c1: Int, val covered: Boolean, val edges: Int) {
    val fill: Color? = v.fill?.let(::rgb)
    val merged: Boolean get() = r0 != r1 || c0 != c1

    companion object {
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 4
        const val BOTTOM = 8
    }
}

/** A picture floating over the sheet, decoded. */
internal class SheetImage(val x: Float, val y: Float, val width: Float, val height: Float, val image: androidx.compose.ui.graphics.ImageBitmap)

/**
 * One workbook's grid as the screen holds it: the sheets, the one shown, its layout and the cells
 * fetched so far (in blocks), the zoom and scroll, the chosen cell and the search hits.
 */
@androidx.compose.runtime.Stable
class SheetGridState internal constructor(private val eng: Engine, private val docId: ULong) {
    var sheets by mutableStateOf<List<SheetInfo>>(emptyList())
        internal set
    var current by mutableIntStateOf(0)
    internal var layout by mutableStateOf<SheetLayout?>(null)
    internal val blocks = mutableStateMapOf<Triple<Int, Int, Int>, List<DrawCell>>()
    /** The cells holding text, of the blocks fetched: a long text may run on over its empty neighbours, not over these. */
    internal val texted = HashSet<Long>()
    internal var pictures by mutableStateOf<List<SheetImage>>(emptyList())
    var scale by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    /** The chosen cell, if one is. */
    var selected by mutableStateOf<CellSpan?>(null)
    var hits by mutableStateOf<List<CellSpan>>(emptyList())
    var hit by mutableIntStateOf(0)
    /** Bumped when the workbook changed: the layout and cells are fetched again. */
    var version by mutableIntStateOf(0)

    internal suspend fun loadSheets() {
        sheets = runCatching { eng.command { sheets(docId) } }.getOrDefault(emptyList())
        if (sheets.getOrNull(current)?.hidden != false) current = sheets.indexOfFirst { !it.hidden }.coerceAtLeast(0)
    }

    /** Show sheet [index]: the view goes back to A1, nothing chosen, nothing found. */
    fun show(index: Int) {
        if (index == current) return
        layout = null
        selected = null
        hits = emptyList()
        pan = Offset.Zero
        pictures = emptyList()
        current = index
    }

    internal suspend fun loadLayout() {
        val info = sheets.getOrNull(current) ?: return
        val sheet = current
        val rows = min(info.rows.toInt() + SPARE_ROWS, 20_000)
        val cols = min(info.cols.toInt() + SPARE_COLS, 2_000)
        val loaded = runCatching {
            eng.command {
                val axes = sheetAxes(docId, sheet.toUInt(), 0u, rows.toUInt(), 0u, cols.toUInt())
                val colX = FloatArray(cols + 1).also { a -> axes.colWidths.forEachIndexed { i, w -> a[i + 1] = a[i] + w } }
                val rowY = FloatArray(rows + 1).also { a -> axes.rowHeights.forEachIndexed { i, h -> a[i + 1] = a[i] + h } }
                SheetLayout(info, colX, rowY, sheetMerges(docId, sheet.toUInt()))
            }
        }.getOrNull()
        // a sheet chosen meanwhile wins
        if (sheet != current) return
        // the cells were made ready against the old layout's merges: they are fetched again
        blocks.clear()
        texted.clear()
        layout = loaded
        pictures = runCatching {
            eng.command {
                sheetPictures(docId, sheet.toUInt()).mapNotNull { p ->
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(p.data, 0, p.data.size, bounds)
                    // a photo is drawn a few hundred pixels wide: no need for all of it
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(p.data, 0, p.data.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    bitmap?.let { SheetImage(p.x, p.y, p.width, p.height, it.asImageBitmap()) }
                }
            }
        }.getOrDefault(emptyList()).takeIf { sheet == current }.orEmpty()
    }

    internal suspend fun loadBlock(br: Int, bc: Int) {
        val l = layout ?: return
        val sheet = current
        val key = Triple(sheet, br, bc)
        if (key in blocks) return
        blocks[key] = emptyList()
        val cells = runCatching {
            eng.command {
                sheetCells(docId, sheet.toUInt(), (br * BLOCK_ROWS).toUInt(), BLOCK_ROWS.toUInt(), (bc * BLOCK_COLS).toUInt(), BLOCK_COLS.toUInt()).mapNotNull { v ->
                    val row = v.row.toInt()
                    val col = v.col.toInt()
                    if (row >= l.rows || col >= l.cols) return@mapNotNull null
                    val m = l.spanAt(row, col)
                    if (m == null) DrawCell(v, row, col, row, col, row, col, false, DrawCell.LEFT or DrawCell.TOP or DrawCell.RIGHT or DrawCell.BOTTOM)
                    else {
                        val r0 = m.row0.toInt()
                        val c0 = m.col0.toInt()
                        val r1 = min(m.row1.toInt(), l.rows - 1)
                        val c1 = min(m.col1.toInt(), l.cols - 1)
                        // inside a merge a cell keeps only the borders on the merge's rim
                        val edges = (if (col == c0) DrawCell.LEFT else 0) or (if (row == r0) DrawCell.TOP else 0) or
                            (if (col == c1) DrawCell.RIGHT else 0) or (if (row == r1) DrawCell.BOTTOM else 0)
                        DrawCell(v, row, col, r0, c0, r1, c1, covered = row != r0 || col != c0, edges = edges)
                    }
                }
            }
        }.getOrDefault(emptyList())
        if (layout !== l || sheet != current) return
        for (c in cells) if (c.v.text.isNotEmpty() && !c.covered) texted += cellKey(c.row, c.col)
        blocks[key] = cells
    }

    /**
     * After an edit: the layout and cells are fetched again (sizes and values may all have moved).
     * What is shown stays until the new layout is in, so the grid doesn't blink empty on every edit.
     */
    fun changed() {
        version++
    }
}

@Composable
fun rememberSheetGrid(eng: Engine, docId: ULong): SheetGridState {
    val state = remember(docId) { SheetGridState(eng, docId) }
    LaunchedEffect(state, state.version) { state.loadSheets() }
    LaunchedEffect(state, state.sheets, state.current, state.version) {
        if (state.sheets.isEmpty()) return@LaunchedEffect
        state.loadLayout()
    }
    return state
}

/**
 * Text laid out once per cell and kept: at the size it has at zoom 1, drawn scaled, so neither a
 * scroll nor a pinch measures anything again (measuring every cell in view on every frame was what
 * made a full sheet lag). The least recently drawn go first when it is full.
 */
internal class SheetTextCache(private val measurer: TextMeasurer) {
    private class Entry(val text: String, val style: Int, val width: Int, val layout: androidx.compose.ui.text.TextLayoutResult)

    private val cells = object : LinkedHashMap<Long, Entry>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?): Boolean = size > 6000
    }
    private val labels = HashMap<String, androidx.compose.ui.text.TextLayoutResult>()

    /** [c]'s text set in [style], wrapped at [wrapWidth] pixels (0: one line). */
    fun cell(c: DrawCell, style: TextStyle, styleKey: Int, wrapWidth: Int): androidx.compose.ui.text.TextLayoutResult {
        val key = cellKey(c.row, c.col)
        cells[key]?.let { if (it.text == c.v.text && it.style == styleKey && it.width == wrapWidth) return it.layout }
        val wrap = wrapWidth > 0
        val layout = measurer.measure(
            c.v.text, style,
            overflow = TextOverflow.Clip,
            softWrap = wrap, maxLines = if (wrap) Int.MAX_VALUE else 1,
            constraints = Constraints(maxWidth = if (wrap) wrapWidth else Constraints.Infinity),
        )
        cells[key] = Entry(c.v.text, styleKey, wrapWidth, layout)
        return layout
    }

    /** A heading's letters or number. */
    fun label(text: String, style: TextStyle): androidx.compose.ui.text.TextLayoutResult {
        if (labels.size > 4000) labels.clear()
        return labels.getOrPut(text) { measurer.measure(text, style) }
    }

    fun clear() {
        cells.clear()
        labels.clear()
    }
}

/**
 * The grid itself: pans and pinch-zooms, a tap chooses a cell ([onCell]), the heading strips and
 * frozen panes held in place. [top] is the room the bars over it take (pixels): the grid runs on
 * beneath them for their glass to refract.
 */
@Composable
fun SheetGrid(
    state: SheetGridState,
    modifier: Modifier = Modifier,
    top: Float = 0f,
    onCell: ((CellSpan?) -> Unit)? = null,
) {
    val c = pdfColors
    val sheetSurface = if (c.dark) Color(0xFF18192F) else Color.White
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val l = state.layout
    Box(modifier.fillMaxSize().background(c.surface)) {
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { top.toDp() } + 40.dp), contentAlignment = Alignment.TopCenter) { GlassLoader(size = 64.dp) }
            return@Box
        }
        val px = density.density
        val texts = remember(l, measurer, c.dark, density.fontScale) { SheetTextCache(measurer) }
        // the blocks in view, asked for as the grid moves
        LaunchedEffect(l) {
            snapshotFlow { Triple(state.pan, state.scale, l) }.distinctUntilChanged().collect { (p, s, lay) ->
                val k = px * s
                val c0 = lay.indexAt(lay.colX, (-p.x / k).coerceAtLeast(0f)) / BLOCK_COLS
                val r0 = lay.indexAt(lay.rowY, (-p.y / k).coerceAtLeast(0f)) / BLOCK_ROWS
                // zoomed out, a screen holds more than two blocks each way
                val across = if (s < 0.75f) 2 else 1
                for (br in r0..r0 + across) for (bc in c0..c0 + across) {
                    if (br * BLOCK_ROWS < lay.rows && bc * BLOCK_COLS < lay.cols) state.loadBlock(br, bc)
                }
            }
        }
        // a hit brought into view: its cell near the top left, under the heading strips
        LaunchedEffect(state.hit, state.hits) {
            val h = state.hits.getOrNull(state.hit) ?: return@LaunchedEffect
            val k = px * state.scale
            val r = h.row0.toInt().coerceIn(0, l.rows - 1)
            val col = h.col0.toInt().coerceIn(0, l.cols - 1)
            val fr = l.info.frozenRows.toInt()
            val fc = l.info.frozenCols.toInt()
            val x = if (col < fc) state.pan.x else -(l.colX[col] - l.colX[minOf(fc, l.cols)]) * k + 24 * px
            val y = if (r < fr) state.pan.y else -(l.rowY[r] - l.rowY[minOf(fr, l.rows)]) * k + 24 * px
            state.pan = Offset(x.coerceAtMost(0f), y.coerceAtMost(0f))
            state.selected = h
        }
        // The bars over the grid grow and shrink (the formula bar comes with a chosen cell, the search
        // field with Find), moving where the grid starts. The gestures below live as long as the
        // layout does, so they read the room as it is now, not as it was when they began: a tap used
        // to be measured against the old room, and chose a cell a few rows off the one touched.
        val room = androidx.compose.runtime.rememberUpdatedState(top)
        val tapped = androidx.compose.runtime.rememberUpdatedState(onCell)
        // ... and the cells stay where they were on the screen while the bars change over them
        val lastTop = remember(l) { floatArrayOf(top) }
        androidx.compose.runtime.SideEffect {
            val d = top - lastTop[0]
            if (d != 0f) {
                lastTop[0] = top
                state.pan = Offset(state.pan.x, (state.pan.y - d).coerceAtMost(0f))
            }
        }
        // the cell under a point of the canvas (null: a heading strip)
        fun cellAt(p: Offset): CellSpan? {
            val k = px * state.scale
            val head = Offset(HEAD_W * k.coerceAtMost(px * 1.5f), HEAD_H * k.coerceAtMost(px * 1.5f))
            val y = p.y - room.value
            if (p.x < head.x || y < head.y) return null
            val fr = minOf(l.info.frozenRows.toInt(), l.rows)
            val fc = minOf(l.info.frozenCols.toInt(), l.cols)
            val gx = p.x - head.x
            val gy = y - head.y
            val col = if (gx < l.colX[fc] * k) l.indexAt(l.colX, gx / k) else l.indexAt(l.colX, (gx - state.pan.x) / k)
            val row = if (gy < l.rowY[fr] * k) l.indexAt(l.rowY, gy / k) else l.indexAt(l.rowY, (gy - state.pan.y) / k)
            return l.spanAt(row, col) ?: CellSpan(row.toUInt(), col.toUInt(), row.toUInt(), col.toUInt())
        }
        val flinger = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        Canvas(
            Modifier
                .fillMaxSize()
                .panZoomFling(state, l, px, flinger)
                .pointerInput(l) {
                    // no double tap is waited for: a tap chooses at once (a second tap on the chosen
                    // cell edits it), where waiting made every tap land a third of a second late
                    detectTapGestures(
                        onPress = { flinger[0]?.cancel() },
                        onTap = { p -> tapped.value?.invoke(cellAt(p)) },
                    )
                },
        ) {
            translate(top = top) {
                drawSheet(l, state, px * state.scale, texts, sheetSurface, c.text, c.dark)
            }
        }
    }
}

/**
 * A workbook in the app: its grid under liquid glass. The top row has Back, the title and sheet,
 * Find and More (Export as PDF, Save as, Print, Share). A tap chooses a cell: the formula bar shows
 * what it holds and takes a new value (a double tap edits at once), and the dock formats it with
 * GridCraft's Home commands. The sheet tabs ride above the dock.
 */
@Composable
fun SheetViewer(vm: AppViewModel, doc: OpenDocument) {
    val eng = vm.engine ?: return
    val c = clear
    // The sheet is themed with the app; floating controls sample its actual pixels for contrast.
    val chrome = rememberGlassInk(c.ink)
    val grid = rememberSheetGrid(eng, doc.id)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val feel = com.chethan616.clearpdf.ui.glass.rememberFeel()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var menuAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var fillMenuAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val topPx = with(density) { WindowInsets.statusBars.getTop(this) + 64.dp.toPx() } +
        with(density) { ((if (searching) 56.dp else 0.dp) + (if (grid.selected != null) 56.dp else 0.dp)).toPx() }

    // the chosen cell's contents, for the formula bar
    LaunchedEffect(grid.selected, grid.version) {
        val s = grid.selected ?: return@LaunchedEffect
        input = runCatching { eng.command { sheetInput(doc.id, grid.current.toUInt(), s.row0, s.col0) } }.getOrDefault("")
    }
    // live debounced search across the active sheet
    LaunchedEffect(query, grid.current, searching) {
        if (!searching || query.isBlank()) {
            grid.hits = emptyList()
            grid.hit = 0
        } else {
            delay(180)
            val q = query.trim()
            if (q.isNotEmpty()) {
                grid.hits = runCatching { eng.command { sheetFind(doc.id, grid.current.toUInt(), q, false, 1000u) } }.getOrDefault(emptyList())
                grid.hit = 0
            }
        }
    }
    fun after() {
        grid.changed()
        scope.launch { vm.refresh(doc) }
    }
    fun run(command: String, params: Map<String, Any?> = emptyMap()) {
        val area = grid.selected ?: CellSpan(0u, 0u, 0u, 0u)
        scope.launch {
            runCatching { eng.command { sheetCommand(doc.id, grid.current.toUInt(), area, command, Engine.toJson(params).toString()) } }
                .onSuccess { after() }
                .onFailure { vm.tell(it.userMessage) }
        }
    }
    fun commit() {
        val s = grid.selected ?: return
        editing = false
        scope.launch {
            runCatching { eng.command { sheetSetCell(doc.id, grid.current.toUInt(), s.row0, s.col0, input) } }
                .onSuccess { after() }
                .onFailure { vm.tell(it.userMessage) }
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            SheetGrid(
                grid, top = topPx,
                onCell = { cell ->
                    // a tap chooses the cell; a tap on the chosen cell opens it for typing
                    if (cell != null && cell == grid.selected) editing = true
                    else {
                        if (cell != null) feel(com.chethan616.clearpdf.ui.glass.Feel.Tick)
                        grid.selected = cell
                        editing = false
                    }
                },
            )
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassInk provides chrome.color) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = { vm.closeDocument() }, label = stringResource(R.string.common_back)) { GlassIcon(LucideIcons.ChevronLeft, null, Modifier.size(22.dp)) }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f).then(chrome.modifier).glassBar().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(doc.title, style = ClearType.BodyStrong, color = LocalGlassInk.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        grid.sheets.getOrNull(grid.current)?.let { Text(it.name + if (doc.info.dirty) " · " + stringResource(R.string.sheet_edited) else "", style = ClearType.Caption, color = LocalGlassInk.current.copy(alpha = 0.62f), maxLines = 1) }
                    }
                    Spacer(Modifier.size(10.dp))
                    GlassCircle(
                        onClick = {
                            searching = !searching
                            if (!searching) {
                                grid.hits = emptyList()
                                query = ""
                            }
                        },
                        label = stringResource(R.string.viewer_find_in_document),
                        surface = if (searching) Candy.Sky.copy(alpha = 0.22f) else Color.Unspecified,
                    ) {
                        GlassIcon(if (searching) LucideIcons.X else LucideIcons.Search, null, Modifier.size(20.dp), tint = if (searching) Candy.Sky else LocalGlassInk.current)
                    }
                    Spacer(Modifier.size(10.dp))
                    var moreRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                    GlassCircle(
                        onClick = { menuAt = moreRect }, label = stringResource(R.string.common_more_options),
                        modifier = Modifier.onGloballyPositioned { moreRect = it.boundsInWindow() },
                    ) { GlassIcon(LucideIcons.MoreHorizontal, null, Modifier.size(20.dp)) }
                }
                if (searching) {
                    val searchFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    val findLabel = stringResource(R.string.viewer_find_in_document)
                    val clearLabel = stringResource(R.string.common_clear)
                    val qTrim = query.trim()
                    val notFound = qTrim.isNotEmpty() && grid.hits.isEmpty()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .glassBar(
                                    shape = ChromeCapsule,
                                    surface = if (notFound) Candy.Tomato.copy(alpha = 0.15f) else Color.Unspecified,
                                )
                                .padding(start = 14.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                LucideIcons.Search,
                                null,
                                tint = if (notFound) Candy.Tomato else LocalGlassInk.current.copy(alpha = 0.62f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = ClearType.Body.copy(color = if (notFound) Candy.Tomato else LocalGlassInk.current),
                                cursorBrush = SolidColor(if (notFound) Candy.Tomato else Candy.Sky),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    val q = query.trim()
                                    scope.launch {
                                        grid.hits = if (q.isEmpty()) emptyList() else runCatching {
                                            eng.command { sheetFind(doc.id, grid.current.toUInt(), q, false, 1000u) }
                                        }.getOrDefault(emptyList())
                                        grid.hit = 0
                                    }
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocus)
                                    .semantics {
                                        contentDescription = findLabel
                                    },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) {
                                            Text(
                                                findLabel,
                                                style = ClearType.Body,
                                                color = LocalGlassInk.current.copy(alpha = 0.42f),
                                                maxLines = 1,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (notFound) {
                                Text(
                                    stringResource(R.string.sheet_not_found),
                                    style = ClearType.Label.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                    color = Candy.Tomato,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                                )
                            } else if (grid.hits.isNotEmpty()) {
                                Text(
                                    "${grid.hit + 1}/${grid.hits.size}",
                                    style = ClearType.Label.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                    color = LocalGlassInk.current.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                                )
                            }
                            if (query.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            query = ""
                                            grid.hits = emptyList()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        LucideIcons.X,
                                        clearLabel,
                                        tint = if (notFound) Candy.Tomato else LocalGlassInk.current.copy(alpha = 0.62f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                        if (grid.hits.size > 1) {
                            GlassCircle(
                                onClick = { grid.hit = (grid.hit - 1 + grid.hits.size) % grid.hits.size },
                                size = 44.dp,
                                label = stringResource(R.string.viewer_find_previous),
                            ) {
                                GlassIcon(LucideIcons.ChevronUp, null, Modifier.size(18.dp))
                            }
                            GlassCircle(
                                onClick = { grid.hit = (grid.hit + 1) % grid.hits.size },
                                size = 44.dp,
                                label = stringResource(R.string.viewer_find_next),
                            ) {
                                GlassIcon(LucideIcons.ChevronDown, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (searching) Spacer(Modifier.height(8.dp))
                grid.selected?.let { cell ->
                    // the formula bar: the cell's name, what it holds, and a field to change it
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(48.dp)
                            .glass(Capsule, wash = Color.White.copy(alpha = 0.12f), blur = 14.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(4.dp).glassBar(surface = Candy.Sky.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(columnName(cell.col0.toInt()) + (cell.row0.toInt() + 1), style = ClearType.Label, color = LocalGlassInk.current)
                        }
                        if (editing) {
                            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            GlassTextField(
                                input, { input = it }, "", Modifier.weight(1f).padding(horizontal = 6.dp), focus = focus,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commit() }),
                            )
                            Box(Modifier.size(40.dp).pressable({ commit() }), contentAlignment = Alignment.Center) { GlassIcon(LucideIcons.Check, stringResource(R.string.common_done), Modifier.size(20.dp), tint = Candy.Sky) }
                        } else {
                            Text(
                                input.ifEmpty { stringResource(R.string.sheet_tap_to_type) }, style = ClearType.Body, color = if (input.isEmpty()) LocalGlassInk.current.copy(alpha = 0.42f) else LocalGlassInk.current,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).pressable({ editing = true }),
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).imePadding()) {
                // the sheets: glass tabs for a few, else a pill with the sheet's name that lists them all
                val visible = grid.sheets.withIndex().filter { !it.value.hidden }
                if (visible.size in 2..4) {
                    GlassTabs(
                        tabs = visible.map { (_, sh) -> GlassTab(sh.name, accent = sh.tabColor?.let { rgb(it) } ?: Candy.Sky) },
                        selected = visible.indexOfFirst { it.index == grid.current }.coerceAtLeast(0),
                        onSelect = { i -> visible.getOrNull(i)?.let { grid.show(it.index); editing = false } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                        height = 44.dp,
                    )
                } else if (visible.size > 4) {
                    var sheetsAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                    var pillRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        GlassPill(
                            onClick = { sheetsAt = pillRect }, height = 40.dp, horizontalPadding = 16.dp,
                            modifier = Modifier.onGloballyPositioned { pillRect = it.boundsInWindow() },
                        ) {
                            GlassIcon(LucideIcons.Sheet, null, Modifier.size(17.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(grid.sheets.getOrNull(grid.current)?.name.orEmpty(), style = ClearType.Label.copy(fontSize = 14.sp), color = LocalGlassInk.current, maxLines = 1)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.sheet_of, visible.indexOfFirst { it.index == grid.current } + 1, visible.size), style = ClearType.Caption, color = LocalGlassInk.current.copy(alpha = 0.62f))
                            GlassIcon(LucideIcons.ChevronDown, null, Modifier.size(16.dp))
                        }
                    }
                    sheetsAt?.let { at ->
                        GlassMenu(at, { sheetsAt = null }, visible.map { (i, sh) ->
                            GlassMenuItem(sh.name, if (i == grid.current) LucideIcons.Check else null) { grid.show(i); editing = false }
                        })
                    }
                }
                // the dock: the chosen cell's formatting, else the workbook's tools
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val cell = grid.selected
                    Row(
                        Modifier.weight(1f).fillMaxHeight().glassBar().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (cell == null) {
                            DockKey(LucideIcons.FilePdf, stringResource(R.string.office_export_pdf)) { vm.exportOffice(doc, "pdf") }
                            var saveRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                            DockKey(LucideIcons.SaveAs, stringResource(R.string.office_save_as), modifier = Modifier.onGloballyPositioned { saveRect = it.boundsInWindow() }) { menuAt = saveRect }
                            DockKey(LucideIcons.Printer, stringResource(R.string.sheet_print)) { vm.print(doc) }
                            DockKey(LucideIcons.Share2, stringResource(R.string.common_share)) { vm.share(doc) }
                            DockKey(LucideIcons.Undo2, stringResource(R.string.word_undo), doc.info.undo != null) { run("edit.undo") }
                            DockKey(LucideIcons.Redo2, stringResource(R.string.word_redo), doc.info.redo != null) { run("edit.redo") }
                        } else {
                            DockKey(LucideIcons.Undo2, stringResource(R.string.word_undo), doc.info.undo != null) { run("edit.undo") }
                            DockKey(LucideIcons.Redo2, stringResource(R.string.word_redo), doc.info.redo != null) { run("edit.redo") }
                            DockKey(LucideIcons.Bold, stringResource(R.string.word_bold)) { run("home.bold") }
                            DockKey(LucideIcons.Italic, stringResource(R.string.word_italic)) { run("home.italic") }
                            var fillRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                            DockKey(LucideIcons.PaintBucket, stringResource(R.string.sheet_fill), modifier = Modifier.onGloballyPositioned { fillRect = it.boundsInWindow() }) { fillMenuAt = fillRect }
                            DockKey(LucideIcons.AlignLeft, stringResource(R.string.word_align_left)) { run("home.alignLeft") }
                            DockKey(LucideIcons.AlignCenter, stringResource(R.string.word_align_center)) { run("home.alignCenter") }
                            DockKey(LucideIcons.AlignRight, stringResource(R.string.word_align_right)) { run("home.alignRight") }
                            DockKey(LucideIcons.WrapText, stringResource(R.string.sheet_wrap)) { run("home.wrapText") }
                            DockKey(LucideIcons.Percent, stringResource(R.string.sheet_percent)) { run("home.percent") }
                            DockKey(LucideIcons.Hash, stringResource(R.string.sheet_comma)) { run("home.comma") }
                            DockKey(LucideIcons.RowsAdd, stringResource(R.string.sheet_insert_row)) { run("home.insertRows") }
                            DockKey(LucideIcons.ColumnsAdd, stringResource(R.string.sheet_insert_column)) { run("home.insertColumns") }
                            DockKey(LucideIcons.Trash2, stringResource(R.string.sheet_clear)) { run("edit.clearContents") }
                        }
                    }
                    if (cell != null) GlassCircle(onClick = { grid.selected = null; editing = false }, size = 48.dp, tint = Candy.Sky, label = stringResource(R.string.common_done)) {
                        GlassIcon(LucideIcons.Check, null, Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    menuAt?.let { at ->
        val formats = remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { formats.value = runCatching { eng.command { exportFormats(doc.id) } }.getOrDefault(emptyList()) }
        GlassMenu(at, { menuAt = null }, buildList {
            add(GlassMenuItem(stringResource(R.string.office_export_pdf), LucideIcons.FilePdf) { vm.exportOffice(doc, "pdf") })
            for (f in formats.value.filter { it != "pdf" }) add(GlassMenuItem(stringResource(R.string.office_save_as_format, f.uppercase()), LucideIcons.SaveAs) { vm.exportOffice(doc, f) })
            add(GlassMenuItem(stringResource(R.string.sheet_print), LucideIcons.Printer) { vm.print(doc) })
            add(GlassMenuItem(stringResource(R.string.common_share), LucideIcons.Share2) { vm.share(doc) })
        })
    }
    fillMenuAt?.let { at ->
        val fills = listOf(R.string.sheet_fill_yellow to "#FFF2A8", R.string.sheet_fill_green to "#C9F2D0", R.string.sheet_fill_blue to "#CFE3FF", R.string.sheet_fill_pink to "#FFD3E0", R.string.sheet_fill_none to "none")
        GlassMenu(at, { fillMenuAt = null }, fills.map { (label, hex) -> GlassMenuItem(stringResource(label), LucideIcons.PaintBucket) { run("home.fillColor", mapOf("color" to hex)) } })
    }
}

/** One glyph of a dock, in the glass's ink. */
@Composable
private fun DockKey(icon: ImageVector, label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(40.dp).pressable({ if (enabled) onClick() }), contentAlignment = Alignment.Center) {
        GlassIcon(icon, label, Modifier.size(21.dp).graphicsLayer { alpha = if (enabled) 1f else 0.35f })
    }
}

/**
 * The sheet at [k] pixels per point: the body, then the frozen rows and columns held still on their
 * axis, then the heading strips, each clipped to its own region. In each region, in Excel's order:
 * grid lines, merged areas cleared of them, fills and data bars, borders, pictures, the chosen cell
 * and the search hit, then the text.
 */
private fun DrawScope.drawSheet(
    l: SheetLayout,
    state: SheetGridState,
    k: Float,
    texts: SheetTextCache,
    sheetSurface: Color,
    ink: Color,
    dark: Boolean,
) {
    val pan = state.pan
    val sheet = state.current
    val blocks = state.blocks
    val found = state.hits.getOrNull(state.hit)
    val selected = state.selected
    drawRect(sheetSurface)
    val head = Offset(HEAD_W * k.coerceAtMost(density * 1.5f), HEAD_H * k.coerceAtMost(density * 1.5f))
    val fr = min(l.info.frozenRows.toInt(), l.rows)
    val fc = min(l.info.frozenCols.toInt(), l.cols)
    val frozenW = l.colX[fc] * k
    val frozenH = l.rowY[fr] * k
    val body = Rect(head.x + frozenW, head.y + frozenH, size.width, size.height)
    // text is laid out at zoom 1 and drawn scaled
    val zoom = k / density
    val accent = Color(0xFF0A84FF)
    val gridLine = if (dark) Color(0xFF343650) else Color(0xFFE1E4EA)
    val dashes = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f * k, 2f * k))
    val dots = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1f * k, 1.2f * k))
    val shown = ArrayList<DrawCell>(512)
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
            fun x(c: Int) = ox + l.colX[c] * k
            fun y(r: Int) = oy + l.rowY[r] * k
            fun area(a: DrawCell) = Rect(x(a.c0), y(a.r0), x(a.c1 + 1), y(a.r1 + 1))
            // grid lines
            if (l.info.gridlines) {
                for (c in c0..c1) drawLine(gridLine, Offset(x(c), clip.top), Offset(x(c), clip.bottom), 1f)
                for (r in r0..r1) drawLine(gridLine, Offset(clip.left, y(r)), Offset(clip.right, y(r)), 1f)
                // a merged area is one cell: no grid lines run through it
                if (l.merges.size <= 4000) for (m in l.merges) {
                    val mr0 = m.row0.toInt()
                    val mc0 = m.col0.toInt()
                    val mr1 = min(m.row1.toInt(), l.rows - 1)
                    val mc1 = min(m.col1.toInt(), l.cols - 1)
                    if (mr1 < r0 || mr0 >= r1 || mc1 < c0 || mc0 >= c1) continue
                    val a = Rect(x(mc0), y(mr0), x(mc1 + 1), y(mr1 + 1))
                    if (a.width > 2f && a.height > 2f) drawRect(sheetSurface, Offset(a.left + 0.5f, a.top + 0.5f), Size(a.width - 1f, a.height - 1f))
                }
            }
            // the cells in view, from the blocks in view and the ones before them (a merge that
            // begins above or left of the screen still shows its fill and text)
            shown.clear()
            val br0 = max(0, r0 / BLOCK_ROWS - 1)
            val bc0 = max(0, c0 / BLOCK_COLS - 1)
            for (br in br0..(r1 - 1) / BLOCK_ROWS) for (bc in bc0..(c1 - 1) / BLOCK_COLS) {
                val cells = blocks[Triple(sheet, br, bc)] ?: continue
                for (cell in cells) {
                    if (cell.covered) {
                        if (cell.row in r0 until r1 && cell.col in c0 until c1) shown += cell
                    } else if (cell.r1 >= r0 && cell.r0 < r1 && cell.c1 >= c0 && cell.c0 < c1) shown += cell
                }
            }
            // fills, then data bars
            for (cell in shown) {
                if (cell.covered) continue
                val a = area(cell)
                cell.fill?.let { drawRect(it, a.topLeft, a.size) }
                if (cell.v.bar > 0f) drawRect(rgb(cell.v.barColor).copy(alpha = 0.55f), Offset(a.left + k, a.top + k), Size(((a.width - 2 * k) * cell.v.bar).coerceAtLeast(0f), (a.height - 2 * k).coerceAtLeast(0f)))
            }
            // borders: each cell's own, on its own box (inside a merge only those on the merge's rim)
            for (cell in shown) {
                if (cell.edges == 0) continue
                val left = x(cell.col)
                val top = y(cell.row)
                val right = x(cell.col + 1)
                val bottom = y(cell.row + 1)
                fun edge(e: com.chethan616.clearpdf.core.CellEdge, bit: Int, a: Offset, b: Offset, nx: Float, ny: Float) {
                    val weight = e.weight.toInt()
                    if (weight == 0 || cell.edges and bit == 0) return
                    // automatic (black) lines take the sheet's ink in the dark theme, where black would vanish
                    val color = if (e.color == 0u && dark && cell.fill == null) ink.copy(alpha = 0.85f) else rgb(e.color)
                    val line = e.line.toInt()
                    val width = if (line == 4) 1f else max(1f, weight * 0.5f * k)
                    when (line) {
                        // double: two thin lines either side of the edge
                        3 -> {
                            val d = max(1f, 0.6f * k)
                            val w = max(1f, 0.35f * k)
                            drawLine(color, Offset(a.x + nx * d, a.y + ny * d), Offset(b.x + nx * d, b.y + ny * d), w)
                            drawLine(color, Offset(a.x - nx * d, a.y - ny * d), Offset(b.x - nx * d, b.y - ny * d), w)
                        }
                        1 -> drawLine(color, a, b, width, pathEffect = dashes)
                        2 -> drawLine(color, a, b, width, pathEffect = dots)
                        else -> drawLine(color, a, b, width)
                    }
                }
                edge(cell.v.left, DrawCell.LEFT, Offset(left, top), Offset(left, bottom), 1f, 0f)
                edge(cell.v.top, DrawCell.TOP, Offset(left, top), Offset(right, top), 0f, 1f)
                edge(cell.v.right, DrawCell.RIGHT, Offset(right, top), Offset(right, bottom), 1f, 0f)
                edge(cell.v.bottom, DrawCell.BOTTOM, Offset(left, bottom), Offset(right, bottom), 0f, 1f)
            }
            // pictures float over the cells (in the part that scrolls both ways)
            if (scrollX && scrollY) for (p in state.pictures) {
                val w = (p.width * k).toInt()
                val h = (p.height * k).toInt()
                val left = ox + p.x * k
                val top = oy + p.y * k
                if (w <= 0 || h <= 0 || left > clip.right || top > clip.bottom || left + w < clip.left || top + h < clip.top) continue
                drawImage(p.image, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()), dstSize = androidx.compose.ui.unit.IntSize(w, h))
            }
            // the chosen cell (or its merged area): a ring in the accent, as a spreadsheet marks it
            selected?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                val rEnd = minOf(f.row1.toInt(), l.rows - 1)
                val cEnd = minOf(f.col1.toInt(), l.cols - 1)
                if (rEnd >= r0 && r < r1 && cEnd >= c0 && col < c1) {
                    val box = Rect(x(col), y(r), x(cEnd + 1), y(rEnd + 1))
                    drawRect(accent.copy(alpha = 0.08f), box.topLeft, box.size)
                    drawRect(accent, box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
            }
            // the search hit in this region: an accent ring round its cell
            found?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                if (r in r0 until r1 && col in c0 until c1) {
                    val box = Rect(x(col), y(r), x(col + 1), y(r + 1))
                    drawRect(accent.copy(alpha = 0.2f), box.topLeft, box.size)
                    drawRect(accent, box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
            }
            // text
            for (cell in shown) {
                val v = cell.v
                if (cell.covered || v.text.isEmpty()) continue
                val a = area(cell)
                if (a.width < 2f || a.height < 2f) continue
                val turned = v.rotation.toInt() != 0 && v.rotation.toInt() in -90..90
                // the room the text has, at zoom 1 (pixels): the cell less its padding and indent
                val pad0 = 2f * density
                val indent0 = v.indent.toInt() * 7f * density
                val cellW0 = a.width / zoom
                val cellH0 = a.height / zoom
                val wrapWidth = if (v.wrap && !turned) (cellW0 - 2 * pad0 - indent0).toInt().coerceAtLeast(1) else 0
                val auto = v.ink == 0u
                val surface = cell.fill ?: sheetSurface
                val color = when {
                    auto -> if (surface.luminance() > 0.5f) Color(0xFF15171C) else ink
                    // a dark colour chosen for a white sheet, on the dark theme's ground: lifted so it reads
                    cell.fill == null && dark && rgb(v.ink).luminance() < 0.22f -> androidx.compose.ui.graphics.lerp(rgb(v.ink), Color.White, 0.62f)
                    else -> rgb(v.ink)
                }
                val styleKey = (color.hashCode() * 31 + (v.size * 4).toInt()) * 31 +
                    ((if (v.bold) 1 else 0) or (if (v.italic) 2 else 0) or (if (v.underline) 4 else 0) or (if (v.strike) 8 else 0) or (v.face.toInt() shl 4))
                val style = TextStyle(
                    color = color,
                    fontSize = (v.size / fontScale).sp,
                    fontWeight = if (v.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (v.italic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = when (v.face.toInt()) {
                        1 -> androidx.compose.ui.text.font.FontFamily.Serif
                        2 -> androidx.compose.ui.text.font.FontFamily.Monospace
                        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                    },
                    textDecoration = when {
                        v.underline && v.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        v.underline -> TextDecoration.Underline
                        v.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                )
                val t = texts.cell(cell, style, styleKey, wrapWidth)
                val tw = t.size.width.toFloat()
                val th = t.size.height.toFloat()
                if (turned) {
                    // turned text: about the middle of its cell, kept inside it
                    clipRect(a.left, a.top, a.right, a.bottom) {
                        withTransform({
                            translate(a.center.x, a.center.y)
                            rotate(-v.rotation.toFloat(), Offset.Zero)
                            scale(zoom, zoom, Offset.Zero)
                        }) { drawText(t, topLeft = Offset(-tw / 2f, -th / 2f)) }
                    }
                    continue
                }
                val x0 = when (v.align) {
                    "c" -> (cellW0 - tw) / 2f
                    "r" -> cellW0 - pad0 - indent0 - tw
                    else -> pad0 + indent0
                }
                val y0 = when (v.valign) {
                    "t" -> pad0 / 2
                    "c" -> (cellH0 - th) / 2f
                    else -> cellH0 - pad0 / 2 - th
                }
                // One line too long for its cell runs on over the empty cells beside it, as Excel
                // shows it (a title typed in A1 reads across the row), and stops at the first cell
                // that holds something. Wrapped and merged text keeps to its own area.
                var clipLeft = a.left
                var clipRight = a.right
                if (wrapWidth == 0 && !cell.merged && tw > cellW0 - 2 * pad0) {
                    val need = tw * zoom
                    if (v.align != "r") {
                        var cEnd = cell.col
                        while (cEnd + 1 < c1 + 8 && cEnd + 1 < l.cols && x(cEnd + 1) - a.left < need + pad0 * zoom * 2 &&
                            cellKey(cell.row, cEnd + 1) !in state.texted && l.spanAt(cell.row, cEnd + 1) == null
                        ) cEnd++
                        clipRight = x(cEnd + 1)
                    }
                    if (v.align != "l") {
                        var cStart = cell.col
                        while (cStart - 1 >= 0 && a.right - x(cStart) < need + pad0 * zoom * 2 &&
                            cellKey(cell.row, cStart - 1) !in state.texted && l.spanAt(cell.row, cStart - 1) == null
                        ) cStart--
                        clipLeft = x(cStart)
                    }
                }
                clipRect(clipLeft, a.top, clipRight, a.bottom) {
                    withTransform({
                        translate(a.left, a.top)
                        scale(zoom, zoom, Offset.Zero)
                    }) { drawText(t, topLeft = Offset(x0, y0)) }
                }
            }
        }
    }
    // body, then the frozen strips, then the corner where both are frozen
    region(body, head, fr, l.rows, fc, l.cols, scrollX = true, scrollY = true)
    region(Rect(head.x + frozenW, head.y, size.width, head.y + frozenH), head, 0, fr, fc, l.cols, scrollX = true, scrollY = false)
    region(Rect(head.x, head.y + frozenH, head.x + frozenW, size.height), head, fr, l.rows, 0, fc, scrollX = false, scrollY = true)
    region(Rect(head.x, head.y, head.x + frozenW, head.y + frozenH), head, 0, fr, 0, fc, scrollX = false, scrollY = false)
    val frozenDivider = if (dark) Color(0xFF777D9A) else Color(0xFF9AA3B2)
    if (fr > 0) drawLine(frozenDivider, Offset(head.x, head.y + frozenH), Offset(size.width, head.y + frozenH), density)
    if (fc > 0) drawLine(frozenDivider, Offset(head.x + frozenW, head.y), Offset(head.x + frozenW, size.height), density)
    // the heading strips: only the letters and numbers in view, each measured once
    val strip = if (dark) Color(0xFF20213B) else Color(0xFFF2F4F8)
    val chosen = accent.copy(alpha = if (dark) 0.30f else 0.16f)
    val labelZoom = min(zoom, 1.5f)
    val label = TextStyle(color = ink.copy(alpha = 0.6f), fontSize = (11 / fontScale).sp)
    drawRect(strip, Offset(0f, 0f), Size(size.width, head.y))
    drawRect(strip, Offset(0f, 0f), Size(head.x, size.height))
    val lineC = if (dark) Color(0xFF343650) else Color(0xFFD5D9E0)
    clipRect(head.x, 0f, size.width, head.y) {
        fun columns(from: Int, to: Int, shift: Float, clipFrom: Float) {
            if (from >= to) return
            val first = max(from, l.indexAt(l.colX, (clipFrom - head.x - shift) / k))
            val last = min(to - 1, l.indexAt(l.colX, (size.width - head.x - shift) / k))
            for (c in first..last) {
                val x0 = head.x + l.colX[c] * k + shift
                val x1 = head.x + l.colX[c + 1] * k + shift
                if (x1 - x0 < 1f) continue
                if (selected != null && c.toUInt() in selected.col0..selected.col1) drawRect(chosen, Offset(x0, 0f), Size(x1 - x0, head.y))
                drawLine(lineC, Offset(x1, 0f), Offset(x1, head.y), 1f)
                val t = texts.label(columnName(c), label)
                val w = t.size.width * labelZoom
                if (w < x1 - x0) withTransform({
                    translate((x0 + x1 - w) / 2f, (head.y - t.size.height * labelZoom) / 2f)
                    scale(labelZoom, labelZoom, Offset.Zero)
                }) { drawText(t) }
            }
        }
        clipRect(head.x + frozenW, 0f, size.width, head.y) { columns(fc, l.cols, pan.x, head.x + frozenW) }
        columns(0, fc, 0f, head.x)
    }
    clipRect(0f, head.y, head.x, size.height) {
        fun rows(from: Int, to: Int, shift: Float, clipFrom: Float) {
            if (from >= to) return
            val first = max(from, l.indexAt(l.rowY, (clipFrom - head.y - shift) / k))
            val last = min(to - 1, l.indexAt(l.rowY, (size.height - head.y - shift) / k))
            for (r in first..last) {
                val y0 = head.y + l.rowY[r] * k + shift
                val y1 = head.y + l.rowY[r + 1] * k + shift
                if (y1 - y0 < 1f) continue
                if (selected != null && r.toUInt() in selected.row0..selected.row1) drawRect(chosen, Offset(0f, y0), Size(head.x, y1 - y0))
                drawLine(lineC, Offset(0f, y1), Offset(head.x, y1), 1f)
                val t = texts.label((r + 1).toString(), label)
                val h = t.size.height * labelZoom
                if (h < y1 - y0 + 2) withTransform({
                    translate((head.x - t.size.width * labelZoom) / 2f, (y0 + y1 - h) / 2f)
                    scale(labelZoom, labelZoom, Offset.Zero)
                }) { drawText(t) }
            }
        }
        clipRect(0f, head.y + frozenH, head.x, size.height) { rows(fr, l.rows, pan.y, head.y + frozenH) }
        rows(0, fr, 0f, head.y)
    }
    drawLine(lineC, Offset(0f, head.y), Offset(size.width, head.y), 1f)
    drawLine(lineC, Offset(head.x, 0f), Offset(head.x, size.height), 1f)
}

/**
 * Pan and pinch-zoom the grid with a finger or two, and let it glide on when let go, slowing as a
 * list does (the platform's spline decay), exactly matching the fluid physics of the PDF & doc viewers.
 */
private fun Modifier.panZoomFling(
    state: SheetGridState,
    l: SheetLayout,
    px: Float,
    flinger: Array<kotlinx.coroutines.Job?>,
): Modifier = pointerInput(l) {
    val touchSlop = viewConfiguration.touchSlop
    fun clamp(p: Offset, s: Float): Offset {
        val k = px * s
        val maxX = (l.colX.last() * k - size.width + 120.dp.toPx()).coerceAtLeast(0f)
        val maxY = (l.rowY.last() * k - size.height + 160.dp.toPx()).coerceAtLeast(0f)
        return Offset(p.x.coerceIn(-maxX, 0f), p.y.coerceIn(-maxY, 0f))
    }

    kotlinx.coroutines.coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            flinger[0]?.cancel()
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var moved = Offset.Zero
            var zoomed = 1f
            var started = false
            var pinched = false
            var centroid = down.position

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()

                if (!started) {
                    moved += pan
                    zoomed *= zoomChange
                    if (moved.getDistance() > touchSlop || (pressed.size > 1 && kotlin.math.abs(1f - zoomed) * size.width / 2f > touchSlop)) {
                        started = true
                    }
                }

                if (started) {
                    if (pressed.size > 1) {
                        pinched = true
                        val c = event.calculateCentroid()
                        centroid = c
                        if (zoomChange != 1f) {
                            val newScale = (state.scale * zoomChange).coerceIn(0.4f, 6f)
                            val factor = newScale / state.scale
                            val origin = if (c.isSpecified) c else Offset.Zero
                            val target = (state.pan - origin) * factor + origin + pan
                            state.pan = clamp(target, newScale)
                            state.scale = newScale
                        }
                    } else {
                        if (pan != Offset.Zero) {
                            state.pan = clamp(state.pan + pan, state.scale)
                        }
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }

                val lastChange = pressed.first()
                tracker.addPosition(lastChange.uptimeMillis, lastChange.position)
            }

            if (!started) return@awaitEachGesture
            val v = tracker.calculateVelocity()
            val velocity = if (pinched) Offset.Zero else Offset(v.x, v.y)
            if (velocity.getDistance() < 50f) return@awaitEachGesture

            flinger[0] = launch {
                val anim = androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter)
                var last = Offset.Zero
                val decay = androidx.compose.animation.splineBasedDecay<Offset>(androidx.compose.ui.unit.Density(density))
                anim.animateDecay(velocity, decay) {
                    val delta = value - last
                    last = value
                    val target = state.pan + delta
                    val clamped = clamp(target, state.scale)
                    val consumed = clamped - state.pan
                    val left = delta - consumed
                    state.pan = clamped
                    if (kotlin.math.abs(left.x) > 0.5f && kotlin.math.abs(left.y) > 0.5f) {
                        throw kotlinx.coroutines.CancellationException("at the edge")
                    }
                }
            }
        }
    }
}

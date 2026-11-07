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
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
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

/** One sheet's layout: where every row and column starts (points from A1), its merges and freeze. */
internal class SheetLayout(val info: SheetInfo, val colX: FloatArray, val rowY: FloatArray, val merges: List<CellSpan>) {
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
 * One workbook's grid as the screen holds it: the sheets, the one shown, its layout and the cells
 * fetched so far (in blocks), the zoom and scroll, the chosen cell and the search hits.
 */
@androidx.compose.runtime.Stable
class SheetGridState internal constructor(private val eng: Engine, private val docId: ULong) {
    var sheets by mutableStateOf<List<SheetInfo>>(emptyList())
        internal set
    var current by mutableIntStateOf(0)
    internal var layout by mutableStateOf<SheetLayout?>(null)
    internal val blocks = mutableStateMapOf<Triple<Int, Int, Int>, List<CellView>>()
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
        if (sheet == current) layout = loaded
    }

    internal suspend fun loadBlock(br: Int, bc: Int) {
        val key = Triple(current, br, bc)
        if (key in blocks) return
        blocks[key] = emptyList()
        blocks[key] = runCatching {
            eng.command { sheetCells(docId, current.toUInt(), (br * BLOCK_ROWS).toUInt(), BLOCK_ROWS.toUInt(), (bc * BLOCK_COLS).toUInt(), BLOCK_COLS.toUInt()) }
        }.getOrDefault(emptyList())
    }

    /** After an edit: everything shown is fetched again (sizes and values may all have moved). */
    fun changed() {
        blocks.clear()
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
 * The grid itself: pans and pinch-zooms, a tap chooses a cell ([onCell]; [onDoubleTapCell] for
 * the editor), the heading strips and frozen panes held in place. [top] and [bottom] are the
 * room the bars over it take (pixels): the grid runs on beneath them for their glass to refract.
 */
@Composable
fun SheetGrid(
    state: SheetGridState,
    modifier: Modifier = Modifier,
    top: Float = 0f,
    onCell: ((CellSpan?) -> Unit)? = null,
    onDoubleTapCell: ((CellSpan) -> Unit)? = null,
) {
    val c = clear
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val l = state.layout
    Box(modifier.fillMaxSize().background(Color.White)) {
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(top = with(density) { top.toDp() } + 40.dp), contentAlignment = Alignment.TopCenter) { GlassLoader(size = 64.dp) }
            return@Box
        }
        val px = density.density
        // the blocks in view, asked for as the grid moves
        LaunchedEffect(l) {
            snapshotFlow { Triple(state.pan, state.scale, l) }.distinctUntilChanged().collect { (p, s, lay) ->
                val k = px * s
                val c0 = lay.indexAt(lay.colX, (-p.x / k).coerceAtLeast(0f)) / BLOCK_COLS
                val r0 = lay.indexAt(lay.rowY, (-p.y / k).coerceAtLeast(0f)) / BLOCK_ROWS
                for (br in r0..r0 + 1) for (bc in c0..c0 + 1) {
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
        // the cell under a point of the canvas (null: a heading strip)
        fun cellAt(p: Offset): CellSpan? {
            val k = px * state.scale
            val head = Offset(HEAD_W * k.coerceAtMost(px * 1.5f), HEAD_H * k.coerceAtMost(px * 1.5f))
            val y = p.y - top
            if (p.x < head.x || y < head.y) return null
            val fr = minOf(l.info.frozenRows.toInt(), l.rows)
            val fc = minOf(l.info.frozenCols.toInt(), l.cols)
            val gx = p.x - head.x
            val gy = y - head.y
            val col = if (gx < l.colX[fc] * k) l.indexAt(l.colX, gx / k) else l.indexAt(l.colX, (gx - state.pan.x) / k)
            val row = if (gy < l.rowY[fr] * k) l.indexAt(l.rowY, gy / k) else l.indexAt(l.rowY, (gy - state.pan.y) / k)
            val m = l.merges.firstOrNull { row.toUInt() in it.row0..it.row1 && col.toUInt() in it.col0..it.col1 }
            return m ?: CellSpan(row.toUInt(), col.toUInt(), row.toUInt(), col.toUInt())
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .panZoomFling(state, l, px)
                .pointerInput(l, onCell, onDoubleTapCell) {
                    detectTapGestures(
                        onTap = { p -> onCell?.invoke(cellAt(p)) },
                        onDoubleTap = { p ->
                            val cell = cellAt(p)
                            if (cell != null && onDoubleTapCell != null) onDoubleTapCell(cell)
                            else {
                                state.scale = if (state.scale > 1.2f) 1f else 2f
                                state.pan = Offset.Zero
                            }
                        },
                    )
                },
        ) {
            translate(top = top) {
                drawSheet(l, state.blocks, state.current, px * state.scale, state.pan, measurer, c.ink, state.hits.getOrNull(state.hit), state.selected)
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
    val grid = rememberSheetGrid(eng, doc.id)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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
                    if (cell != null && cell == grid.selected) editing = true
                    else {
                        grid.selected = cell
                        editing = false
                    }
                },
                onDoubleTapCell = { cell -> grid.selected = cell; editing = true },
            )
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = { vm.closeDocument() }, label = stringResource(R.string.common_back)) { GlassIcon(LucideIcons.ChevronLeft, null, Modifier.size(22.dp)) }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f).glassBar().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(doc.title, style = ClearType.BodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        grid.sheets.getOrNull(grid.current)?.let { Text(it.name + if (doc.info.dirty) " · " + stringResource(R.string.sheet_edited) else "", style = ClearType.Caption, color = c.inkSoft, maxLines = 1) }
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
                        GlassIcon(if (searching) LucideIcons.X else LucideIcons.Search, null, Modifier.size(20.dp), tint = if (searching) Candy.Sky else c.ink)
                    }
                    Spacer(Modifier.size(10.dp))
                    var moreRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                    GlassCircle(
                        onClick = { menuAt = moreRect }, label = stringResource(R.string.common_more_options),
                        modifier = Modifier.onGloballyPositioned { moreRect = it.boundsInWindow() },
                    ) { GlassIcon(LucideIcons.MoreHorizontal, null, Modifier.size(20.dp)) }
                }
                if (searching) {
                    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
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
                            androidx.compose.material3.Icon(
                                LucideIcons.Search,
                                null,
                                tint = if (notFound) Candy.Tomato else c.inkSoft,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = ClearType.Body.copy(color = if (notFound) Candy.Tomato else c.ink),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(if (notFound) Candy.Tomato else Candy.Sky),
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
                                    .androidx.compose.ui.focus.focusRequester(searchFocus)
                                    .androidx.compose.ui.semantics.semantics {
                                        androidx.compose.ui.semantics.contentDescription = stringResource(R.string.viewer_find_in_document)
                                    },
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) {
                                            Text(
                                                stringResource(R.string.viewer_find_in_document),
                                                style = ClearType.Body,
                                                color = c.inkFaint,
                                                maxLines = 1,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (notFound) {
                                Text(
                                    "Not found",
                                    style = ClearType.Label.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                    color = Candy.Tomato,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                                )
                            } else if (grid.hits.isNotEmpty()) {
                                Text(
                                    "${grid.hit + 1}/${grid.hits.size}",
                                    style = ClearType.Label.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                    color = c.inkSoft,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                                )
                            }
                            if (query.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .androidx.compose.ui.draw.clip(androidx.compose.foundation.shape.CircleShape)
                                        .androidx.compose.foundation.clickable {
                                            query = ""
                                            grid.hits = emptyList()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        LucideIcons.X,
                                        stringResource(R.string.common_clear),
                                        tint = if (notFound) Candy.Tomato else c.inkSoft,
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(48.dp).background(c.frost, com.chethan616.clearpdf.ui.glass.Capsule).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(4.dp).glassBar(surface = Candy.Sky.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(columnName(cell.col0.toInt()) + (cell.row0.toInt() + 1), style = ClearType.Label, color = c.ink)
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
                                input.ifEmpty { stringResource(R.string.sheet_tap_to_type) }, style = ClearType.Body, color = if (input.isEmpty()) c.inkFaint else c.ink,
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
                            Text(grid.sheets.getOrNull(grid.current)?.name.orEmpty(), style = ClearType.Label.copy(fontSize = 14.sp), color = c.ink, maxLines = 1)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.sheet_of, visible.indexOfFirst { it.index == grid.current } + 1, visible.size), style = ClearType.Caption, color = c.inkSoft)
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
    found: CellSpan?,
    selected: CellSpan?,
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
            // the chosen cell (or its merged area): a ring in the accent, as a spreadsheet marks it
            selected?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                if (r in r0 until r1 && col in c0 until c1) {
                    val a = cell(r, col)
                    val b = cell(minOf(f.row1.toInt(), l.rows - 1), minOf(f.col1.toInt(), l.cols - 1))
                    val box = Rect(a.left, a.top, b.right, b.bottom)
                    drawRect(Color(0xFF0A84FF), box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
            }
            // the search hit in this region: an accent ring round its cell
            found?.let { f ->
                val r = f.row0.toInt()
                val col = f.col0.toInt()
                if (r in r0 until r1 && col in c0 until c1) {
                    val box = cell(r, col)
                    drawRect(Color(0x330A84FF), box.topLeft, box.size)
                    drawRect(Color(0xFF0A84FF), box.topLeft, box.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density))
                }
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

/**
 * Pan and pinch-zoom the grid with a finger or two, and let it glide on when let go, slowing as a
 * list does (the platform's spline decay), held within the sheet.
 */
private fun Modifier.panZoomFling(state: SheetGridState, l: SheetLayout, px: Float): Modifier = pointerInput(l) {
    val decay = androidx.compose.animation.splineBasedDecay<Offset>(this)
    fun clamp(p: Offset, s: Float): Offset {
        val k = px * s
        val maxX = (l.colX.last() * k - size.width / 2f).coerceAtLeast(0f)
        val maxY = (l.rowY.last() * k - size.height / 2f).coerceAtLeast(0f)
        return Offset(p.x.coerceIn(-maxX, 0f), p.y.coerceIn(-maxY, 0f))
    }
    kotlinx.coroutines.coroutineScope {
        var glide: kotlinx.coroutines.Job? = null
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            glide?.cancel()
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            var pinched = false
            do {
                val event = awaitPointerEvent()
                val zoom = event.calculateZoom()
                val move = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (zoom != 1f) pinched = true
                if (zoom != 1f || move != Offset.Zero) {
                    val s = (state.scale * zoom).coerceIn(0.4f, 4f)
                    val f = s / state.scale
                    // zoom about the fingers, then follow them
                    val origin = if (centroid.isSpecified) centroid else Offset.Zero
                    state.pan = clamp((state.pan - origin) * f + origin + move, s)
                    state.scale = s
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
            } while (event.changes.any { it.pressed })
            if (!pinched) {
                val v = tracker.calculateVelocity()
                glide = launch {
                    androidx.compose.animation.core.AnimationState(Offset.VectorConverter, state.pan, Offset(v.x, v.y))
                        .animateDecay(decay) {
                            val next = clamp(value, state.scale)
                            state.pan = next
                            if (next != value) cancelAnimation()
                        }
                }
            }
        }
    }
}

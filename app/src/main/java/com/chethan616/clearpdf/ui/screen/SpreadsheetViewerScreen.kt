package com.chethan616.clearpdf.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.ShareMorphButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.SpreadsheetViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

private val CELL_W = 132.dp
private val CELL_H = 38.dp

/** The cell a tap selected, carried into the value/edit popup. */
private data class CellRef(val row: Int, val col: Int, val value: String)

/** Icon + label, so Copy / Edit / Save all sit the same inside a [LiquidButton]. */
@Composable
private fun CellActionLabel(icon: ImageVector, label: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint)
        BasicText(label, style = TextStyle(tint, 14.sp, FontWeight.Medium))
    }
}

/** Interactive spreadsheet viewer: a real scrollable grid with sticky column letters, sheet
 *  navigation, and tap-a-cell-to-see-its-full-value (so long values are never lost to "…"). */
@Composable
fun SpreadsheetViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: SpreadsheetViewModel,
    onBack: () -> Unit,
    onOpenPdf: (android.net.Uri) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val isDark = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val accent = Color(0xFF1E8E5A)   // spreadsheet green
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var sheetIndex by remember { mutableIntStateOf(0) }
    // The tapped cell, plus whether its popup is in read or edit mode. `draft` holds the in-progress
    // text so cancelling leaves the sheet untouched.
    var selectedCell by remember { mutableStateOf<CellRef?>(null) }
    var editingCell by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val editFocus = remember { FocusRequester() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var showSheetPicker by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val gridListState = rememberLazyListState()
    // Theme-adaptive glass surface for the top-bar pill / buttons → true liquid-glass refraction.
    val chromeGlass = if (isDark) Color(0xFF20242C).copy(0.7f) else Color.White.copy(0.55f)

    val sheets = state.sheets
    val idx = sheetIndex.coerceIn(0, sheets.lastIndex.coerceAtLeast(0))
    val currentSheet = sheets.getOrNull(idx)

    // Cell search across the current sheet → list of (row, col) matches.
    val matches = remember(currentSheet, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank() || currentSheet == null) emptyList()
        else buildList {
            currentSheet.rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, v -> if (v.contains(q, ignoreCase = true)) add(r to c) }
            }
        }
    }
    val matchSet = remember(matches) { matches.mapTo(HashSet()) { it.first.toLong() * 1_000_000L + it.second } }
    val currentCell = matches.getOrNull(currentMatch)
    LaunchedEffect(matches) {
        currentMatch = 0
        if (matches.isNotEmpty()) gridListState.animateScrollToItem(matches[0].first)
    }
    fun goToMatch(delta: Int) {
        if (matches.isEmpty()) return
        currentMatch = ((currentMatch + delta) % matches.size + matches.size) % matches.size
        scope.launch { gridListState.animateScrollToItem(matches[currentMatch].first) }
    }

    Box(Modifier.fillMaxSize()) {
        GlassScreenScaffold(
            backdrop = backdrop,
            contentHorizontalPadding = 12.dp,
            headerHorizontalPadding = 12.dp,
            // Header — the EXACT PDF-viewer top bar: back · centered liquid-glass pill · search.
            // The pill shows "Sheet X / Y" and (for multi-sheet files) opens a sheet picker on tap.
            // It is pinned over the grid and samples the content layer, so rows scroll *under* the
            // chrome and refract through it rather than pushing it down the screen.
            header = { headerBackdrop ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidIconButton(onClick = onBack, backdrop = headerBackdrop, surfaceColor = chromeGlass) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidButton(
                        onClick = { if (sheets.size > 1) showSheetPicker = true },
                        backdrop = headerBackdrop,
                        surfaceColor = chromeGlass
                    ) {
                        BasicText(
                            if (currentSheet != null) stringResource(R.string.viewer_sheet_of, idx + 1, sheets.size)
                            else state.fileName.ifBlank { stringResource(R.string.viewer_title) },
                            style = TextStyle(text, 13.sp, FontWeight.SemiBold)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidIconButton(onClick = { showSearch = true }, backdrop = headerBackdrop, surfaceColor = chromeGlass) {
                        Icon(Icons.Rounded.Search, stringResource(R.string.viewer_find), Modifier.size(20.dp), text)
                    }
                }
            }
        ) { contentPadding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
                }
                currentSheet == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(state.error ?: "Empty spreadsheet", style = TextStyle(sub, 14.sp))
                }
                else -> {
                    SheetGrid(
                        sheet = currentSheet, isDark = isDark, text = text, sub = sub, accent = accent, zoom = zoom,
                        listState = gridListState, matchSet = matchSet, currentCell = currentCell,
                        onZoom = { z -> zoom = (zoom * z).coerceIn(0.7f, 2f) },
                        onCellTap = { r, c, value ->
                            selectedCell = CellRef(r, c, value)
                            editingCell = false
                            draft = value
                        },
                        modifier = Modifier.fillMaxSize().padding(contentPadding)
                    )
                }
            }
        }

        // Tap-a-cell → full value popup (fixes truncated "…" cells), and the entry point for editing.
        AnimatedVisibility(
            visible = selectedCell != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.94f, animationSpec = tween(160)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.28f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedCell = null }
                    // The edit field lives in here, so the card has to ride above the keyboard.
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                selectedCell?.let { cell ->
                    Column(
                        Modifier
                            .fillMaxWidth().padding(28.dp)
                            .liquidGlassPanel(backdrop, uiSensor)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // "B7 · Cell value" — the ref matters once you can change what's in it.
                        BasicText(
                            "${colLetter(cell.col)}${cell.row + 1}  ·  ${stringResource(R.string.sheet_cell_value)}",
                            style = TextStyle(sub, 11.sp, FontWeight.Bold, letterSpacing = 0.8.sp)
                        )

                        if (editingCell) {
                            LaunchedEffect(Unit) { runCatching { editFocus.requestFocus() } }
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 320.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                BasicTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    textStyle = TextStyle(text, 16.sp),
                                    cursorBrush = SolidColor(accent),
                                    modifier = Modifier.fillMaxWidth().focusRequester(editFocus)
                                )
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                            ) {
                                BasicText(
                                    cell.value.ifBlank { stringResource(R.string.sheet_cell_empty) },
                                    style = TextStyle(if (cell.value.isBlank()) sub else text, 16.sp)
                                )
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                            if (editingCell) {
                                LiquidButton(
                                    onClick = { editingCell = false; draft = cell.value },
                                    backdrop = backdrop,
                                    surfaceColor = chromeGlass
                                ) {
                                    BasicText(stringResource(R.string.cancel), style = TextStyle(text, 14.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = {
                                        viewModel.updateCell(idx, cell.row, cell.col, draft)
                                        selectedCell = null
                                        editingCell = false
                                    },
                                    backdrop = backdrop,
                                    surfaceColor = accent.copy(0.85f)
                                ) {
                                    CellActionLabel(Icons.Rounded.Check, stringResource(R.string.save), Color.White)
                                }
                            } else {
                                LiquidButton(
                                    onClick = { clipboard.setText(AnnotatedString(cell.value)); selectedCell = null },
                                    backdrop = backdrop,
                                    surfaceColor = chromeGlass
                                ) {
                                    CellActionLabel(Icons.Rounded.ContentCopy, stringResource(R.string.copy), text)
                                }
                                LiquidButton(
                                    onClick = { draft = cell.value; editingCell = true },
                                    backdrop = backdrop,
                                    surfaceColor = accent.copy(0.28f)
                                ) {
                                    CellActionLabel(Icons.Rounded.Edit, stringResource(R.string.edit), accent)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Share-morph (identical to the PDF viewer): tap = open/export as PDF, long-press +
        // swipe-up = share. Bottom-right, growing strictly upward.
        if (currentSheet != null && !showSearch) {
            ShareMorphButton(
                backdrop = backdrop,
                uiSensor = uiSensor,
                glass = chromeGlass,
                fg = text,
                onOpen = { viewModel.exportToPdf(context) { u -> u?.let(onOpenPdf) } },
                onShare = { state.fileUri?.let { shareFile(context, it) } },
                idleIcon = Icons.Rounded.PictureAsPdf,
                idleContentDesc = stringResource(R.string.sheet_export_pdf),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp)
            )
        }

        // ── Search bar (reused from the PDF viewer): searches cells, highlights matches, and
        // Prev/Next scrolls to them. iOS-style, slides up from the bottom.
        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(180)) + slideInVertically { it },
            exit = fadeOut(tween(140)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            PdfSearchBar(
                query = searchQuery,
                matchCount = matches.size,
                currentMatchIndex = currentMatch,
                focusRequester = searchFocus,
                backdrop = backdrop,
                uiSensor = uiSensor,
                fg = text,
                fgSoft = sub,
                // Translucent, not the old opaque slab colour — the search pill is glass now, and
                // an opaque surface would kill its refraction.
                surface = chromeGlass,
                field = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                onQueryChange = { searchQuery = it },
                onPrevMatch = { goToMatch(-1) },
                onNextMatch = { goToMatch(1) },
                onClose = { showSearch = false; searchQuery = "" }
            )
        }

        // Sheet picker — tap the "Sheet X / Y" pill to jump to any sheet (mirrors PDF page-jump).
        SheetPickerPopup(
            visible = showSheetPicker,
            sheets = sheets,
            currentIndex = idx,
            backdrop = backdrop,
            uiSensor = uiSensor,
            isDark = isDark,
            onPick = { i -> sheetIndex = i; showSheetPicker = false },
            onDismiss = { showSheetPicker = false }
        )
    }
}

/** In-window liquid-glass sheet picker (scrim + scale-in panel), modelled on LiquidPageJumpPopup. */
@Composable
private fun SheetPickerPopup(
    visible: Boolean,
    sheets: List<com.chethan616.clearpdf.utils.SpreadsheetParser.Sheet>,
    currentIndex: Int,
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val accent = Color(0xFF1E8E5A)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)).pointerInput(Unit) { detectTapGestures { onDismiss() } })
        }
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center).padding(28.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 360.dp)
                    .liquidGlassPanel(backdrop, uiSensor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BasicText(
                    stringResource(R.string.sheet_picker_title),
                    style = TextStyle(sub, 11.sp, FontWeight.Bold, letterSpacing = 0.8.sp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sheets.forEachIndexed { i, s ->
                        val selected = i == currentIndex
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accent.copy(0.16f) else Color.Transparent)
                                .clickable { onPick(i) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.GridOn, null, Modifier.size(18.dp), if (selected) accent else sub)
                            BasicText(s.name, style = TextStyle(if (selected) accent else text, 15.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(
    sheet: com.chethan616.clearpdf.utils.SpreadsheetParser.Sheet,
    isDark: Boolean,
    text: Color,
    sub: Color,
    accent: Color,
    zoom: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    matchSet: Set<Long>,
    currentCell: Pair<Int, Int>?,
    onZoom: (Float) -> Unit,
    onCellTap: (row: Int, col: Int, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colCount = sheet.columnCount.coerceAtLeast(1)
    val hScroll = rememberScrollState()   // shared → header + all rows scroll horizontally in sync
    val gridLine = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.10f)
    val headerBg = if (isDark) Color(0xFF23262E) else Color(0xFFF1F3F5)
    val rowAlt = if (isDark) Color.White.copy(0.03f) else Color.Black.copy(0.02f)
    val matchBg = accent.copy(0.20f)
    val currentBg = accent.copy(0.48f)
    val cellH = CELL_H * zoom
    val headerSize = (11f * zoom).sp
    val cellSize = (13f * zoom).sp

    // Per-column base widths sized to their content (sample up to 200 rows) so a short ID column
    // stays narrow while a long text column gets room — no more uniform 132dp waste.
    val baseWidths = remember(sheet) {
        val sample = sheet.rows.take(200)
        List(colCount) { c ->
            var maxLen = colLetter(c).length
            for (row in sample) {
                val len = row.getOrNull(c)?.length ?: 0
                if (len > maxLen) maxLen = len
            }
            (maxLen.coerceAtMost(42) * 8 + 24).dp.coerceIn(64.dp, 260.dp)
        }
    }

    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).border(1.dp, gridLine, RoundedCornerShape(12.dp))
            // Pinch-to-zoom (Apple-HIG). Only two-finger gestures are consumed, so single-finger
            // scrolling still passes through to the row/column scroll.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.count { it.pressed } >= 2) {
                            val z = e.calculateZoom()
                            if (z != 1f) { onZoom(z); e.changes.forEach { it.consume() } }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // If the content is narrower than the viewport, stretch every column proportionally so
            // the grid fills the width (kills the empty right gap); wider sheets keep scrolling.
            val available = maxWidth
            val baseSum = baseWidths.fold(0.dp) { acc, w -> acc + w }
            val fill = if (baseSum > 0.dp && baseSum < available) available / baseSum else 1f
            val colWidths = baseWidths.map { it * fill * zoom }

            Column(Modifier.fillMaxSize()) {
                // Sticky column-letter header.
                Row(Modifier.fillMaxWidth().background(headerBg).horizontalScroll(hScroll)) {
                    for (c in 0 until colCount) {
                        Box(
                            Modifier.width(colWidths[c]).heightIn(min = cellH).border(0.5.dp, gridLine).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicText(colLetter(c), style = TextStyle(sub, headerSize, FontWeight.Bold))
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                    itemsIndexed(sheet.rows) { rIdx, row ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                            for (c in 0 until colCount) {
                                val v = row.getOrElse(c) { "" }
                                val isCurrent = currentCell?.first == rIdx && currentCell.second == c
                                val cellBg = when {
                                    isCurrent -> currentBg
                                    (rIdx.toLong() * 1_000_000L + c) in matchSet -> matchBg
                                    rIdx % 2 == 1 -> rowAlt
                                    else -> Color.Transparent
                                }
                                Box(
                                    // Blank cells are tappable too — you have to be able to select an
                                    // empty cell to type into it.
                                    Modifier.width(colWidths[c]).heightIn(min = cellH).background(cellBg).border(0.5.dp, gridLine)
                                        .clickable { onCellTap(rIdx, c, v) }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicText(v, style = TextStyle(text, cellSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 0→A, 25→Z, 26→AA … spreadsheet column labels. */
private fun colLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) { sb.insert(0, 'A' + (i % 26)); i = i / 26 - 1 }
    return sb.toString()
}

/** Share the (mirrored) file. file:// → FileProvider content:// so it isn't exposed → no crash. */
private fun shareFile(context: android.content.Context, uri: android.net.Uri) {
    val shareUri = if (uri.scheme == "file") {
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(uri.path!!))
        }.getOrNull() ?: uri
    } else uri
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(shareUri) ?: "application/octet-stream"
        putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
}

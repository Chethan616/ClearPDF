package com.chethan616.clearpdf.ui.screen

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.ShareMorphButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.viewerChromeGlass
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.SpreadsheetViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

private val CELL_W = 132.dp
private val CELL_H = 38.dp

/**
 * `liquidGlassPanel`'s own corner curve, restated so the scrolling grid can be clipped to it.
 *
 * The panel is read-only and its radius lives in a default argument, so this is a copy rather than
 * a reference. Keep the two in step — a clip that disagrees with the paint reads as a chipped edge.
 */
private val GlassPanelShape: Shape = RoundedRectangle(28f.dp)

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
    val chromeGlass = viewerChromeGlass(isDark)

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
            // Header — Home and Tools' trio, verbatim: back circle · centred [GlassTitlePill] ·
            // search circle, 10 dp apart. The pill is the same widget carrying "ClearPDF" on Home,
            // so the two can't drift apart; it shows "Sheet X / Y" and (for multi-sheet files) opens
            // a sheet picker on tap. It is pinned over the grid and samples the content layer, so
            // rows scroll *under* the chrome and refract through it rather than pushing it down.
            header = { headerBackdrop ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // No `surfaceColor` — Home's circles paint nothing of their own and are pure
                    // refraction. The heavier `chromeGlass` tint that used to be here is still right
                    // for the grid container below, but on floating chrome it read as a grey slab.
                    LiquidIconButton(onClick = onBack, backdrop = headerBackdrop) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
                    }
                    // Weighted Box, not two weighted spacers — the two circles are both 40 dp, so
                    // this is what actually centres the pill on the row, the way Home does it.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(
                            text = if (currentSheet != null) stringResource(R.string.viewer_sheet_of, idx + 1, sheets.size)
                            else state.fileName.ifBlank { stringResource(R.string.viewer_title) },
                            backdrop = headerBackdrop,
                            // No overrides at all, exactly as Home calls it. Both defaults already
                            // resolve to what this screen was passing by hand — the pill's own tint
                            // is the theme's, and its ink is `LiquidGlassColors.text(isDark)`, which
                            // is what `text` is here.
                            onClick = if (sheets.size > 1) ({ showSheetPicker = true }) else null
                        )
                    }
                    LiquidIconButton(onClick = { showSearch = true }, backdrop = headerBackdrop) {
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
                    // The grid used to sit straight on the wallpaper: cells are mostly transparent,
                    // so whatever photo was behind the app showed through the data. It now rides on
                    // the same heavy glass the sheet picker and the page-jump dialog use — blur 8,
                    // a 20x40 depth lens, highlight and inner shadow — which is the one place in
                    // this app that stack is right outside a dialog, because this *is* a reading
                    // surface that has to hold small text over an arbitrary backdrop.
                    //
                    // The tint is pushed well past `liquidGlassPanel`'s 40% default. At 40% a busy
                    // wallpaper still reads through 11 sp cell text. 72% keeps the refraction and
                    // the depth lens plainly visible while giving the type something to sit on.
                    val sheetSurface =
                        if (isDark) Color(0xFF15181E).copy(0.72f) else Color(0xFFF7F8FA).copy(0.72f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .liquidGlassPanel(backdrop, uiSensor, containerColorOverride = sheetSurface)
                            // `liquidGlassPanel` paints its shape but does not clip, and the grid
                            // scrolls — without this the rows run out past the rounded corners.
                            .clip(GlassPanelShape)
                    ) {
                        SheetGrid(
                            sheet = currentSheet, isDark = isDark, text = text, sub = sub, accent = accent, zoom = zoom,
                            listState = gridListState, matchSet = matchSet, currentCell = currentCell,
                            onZoom = { z -> zoom = (zoom * z).coerceIn(0.7f, 2f) },
                            onCellTap = { r, c, value ->
                                selectedCell = CellRef(r, c, value)
                                editingCell = false
                                draft = value
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Jump-scrubber for long sheets — the fast-flick fling on the grid itself
                        // covers distance quickly, but a WPS/Excel-style rail is still the more
                        // precise way to land on a specific far-off row without counting flicks.
                        SheetRowScrubber(
                            listState = gridListState,
                            rowCount = currentSheet.rows.size,
                            backdrop = backdrop,
                            chromeGlass = chromeGlass,
                            accent = accent,
                            text = text,
                            isDark = isDark,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                        )
                    }
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
                            .viewerGlass(backdrop, chromeGlass)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // "B7 · Cell value" — the ref matters once you can change what's in it.
                        BasicText(
                            "${currentSheet?.labelAt(cell.col) ?: ""}${currentSheet?.rowNumberAt(cell.row) ?: (cell.row + 1)}" +
                                "  ·  ${stringResource(R.string.sheet_cell_value)}",
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
                    .viewerGlass(backdrop, viewerChromeGlass(isDark))
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
    // Translucent now that the grid sits on glass. An opaque header read as a solid slab pasted on
    // top of the panel and cut the refraction dead across the first row.
    val headerBg = if (isDark) Color.White.copy(0.07f) else Color.Black.copy(0.05f)
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
            var maxLen = sheet.labelAt(c).length
            for (row in sample) {
                val len = row.getOrNull(c)?.length ?: 0
                if (len > maxLen) maxLen = len
            }
            (maxLen.coerceAtMost(42) * 8 + 24).dp.coerceIn(64.dp, 260.dp)
        }
    }

    // Row-number gutter, sized to the widest number it will ever show so the grid never shifts
    // sideways mid-scroll. It is pinned outside the horizontal scroll, so the row you are reading
    // keeps its number no matter how far right the sheet is scrolled — the same thing Excel does,
    // and the reason a spreadsheet is navigable at all once it is wider than the screen.
    val gutterWidth = remember(sheet, zoom) {
        val digits = (sheet.rowNumbers.lastOrNull() ?: sheet.rows.size).toString().length
        ((digits * 8 + 22).dp * zoom).coerceIn(30.dp, 76.dp)
    }

    Column(
        // No clip/border of its own any more — the glass panel it now sits inside is the container,
        // and a 12 dp rounded outline inside a 28 dp glass capsule read as a box within a box.
        modifier.fillMaxSize()
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
            // The gutter is subtracted first — it sits outside the scroll, so the columns only ever
            // get what is left of the viewport.
            val available = (maxWidth - gutterWidth).coerceAtLeast(80.dp)
            val baseSum = baseWidths.fold(0.dp) { acc, w -> acc + w }
            val fill = if (baseSum > 0.dp && baseSum < available) available / baseSum else 1f
            val colWidths = baseWidths.map { it * fill * zoom }

            // Left edge of every column in dp, with the grid's total width parked at [colCount].
            val starts = remember(colWidths) {
                val out = ArrayList<Float>(colCount + 1)
                var acc = 0f
                for (w in colWidths) { out.add(acc); acc += w.value }
                out.add(acc)
                out
            }
            // Horizontal windowing. `LazyColumn` keeps the row count in check, but each row was an
            // eager `Row` over *every* column — a 150-column workbook is ~3000 cell nodes on screen,
            // each with its own background, border, click handler and text layout. That does not
            // throw; it just wedges the frame loop long enough to look like the app has died, and
            // on a big enough sheet it is an ANR. Only the columns intersecting the viewport are
            // composed now; the skipped ones on either side become a single spacer each, so the
            // scroll range and every column's x-position are unchanged.
            val scrolledDp = with(androidx.compose.ui.platform.LocalDensity.current) { hScroll.value.toDp().value }
            val window = remember(starts, scrolledDp, available) {
                val right = scrolledDp + available.value
                var first = 0
                while (first < colCount - 1 && starts[first + 1] <= scrolledDp) first++
                var last = first
                while (last < colCount - 1 && starts[last + 1] < right) last++
                first..last
            }
            val leadWidth = starts[window.first].dp
            val tailWidth = (starts[colCount] - starts[window.last + 1]).dp
            // For the grid-line draw pass below — converts the dp-unit `starts`/`colWidths` numbers
            // straight to px without a `LocalDensity.current` lookup inside every row.
            val pxPerDp = with(LocalDensity.current) { 1.dp.toPx() }

            Column(Modifier.fillMaxSize()) {
                // Sticky column-letter header, with the gutter's blank corner cell to its left.
                Row(Modifier.fillMaxWidth().background(headerBg)) {
                    Box(Modifier.width(gutterWidth).heightIn(min = cellH).border(0.5.dp, gridLine))
                    Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        Spacer(Modifier.width(leadWidth))
                        for (c in window) {
                            Box(
                                Modifier.width(colWidths[c]).heightIn(min = cellH).border(0.5.dp, gridLine).padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // The sheet's own label, not the position — a sheet that hides a
                                // column still reads "… G, I …" here, exactly as it does in Excel.
                                BasicText(sheet.labelAt(c), style = TextStyle(sub, headerSize, FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.width(tailWidth))
                    }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    // Rows only. The column axis is at most a few screens wide, so there is nothing
                    // there that repeated swipes are a tiring way to cross.
                    flingBehavior = rememberStackingFlingBehavior()
                ) {
                    itemsIndexed(sheet.rows) { rIdx, row ->
                        val rowMatched = currentCell?.first == rIdx
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.width(gutterWidth).heightIn(min = cellH)
                                    .background(if (rowMatched) accent.copy(0.22f) else headerBg)
                                    .border(0.5.dp, gridLine),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    sheet.rowNumberAt(rIdx).toString(),
                                    style = TextStyle(
                                        if (rowMatched) accent else sub,
                                        headerSize,
                                        if (rowMatched) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1
                                )
                            }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .horizontalScroll(hScroll)
                                    // One draw pass for the whole row's grid lines, instead of a
                                    // `border()` modifier on every cell. A `border` is a real layout
                                    // + draw node, and with 9–16 visible columns that meant up to
                                    // ~16 extra nodes composing for every row a fast fling scrolled
                                    // into view — on a 500–1000 row sheet under the boosted fling
                                    // below, that per-row node churn was more than composition could
                                    // keep up with, which showed as the list visibly pausing to
                                    // "catch up" every screenful.
                                    .drawBehind {
                                        val stroke = 0.5.dp.toPx()
                                        val bottom = size.height
                                        for (c in window) {
                                            val right = (starts[c] + colWidths[c].value) * pxPerDp
                                            drawLine(gridLine, Offset(right, 0f), Offset(right, bottom), stroke)
                                        }
                                        drawLine(gridLine, Offset(0f, bottom), Offset(size.width, bottom), stroke)
                                    }
                            ) {
                                Spacer(Modifier.width(leadWidth).heightIn(min = cellH))
                                for (c in window) {
                                    val v = row.getOrElse(c) { "" }
                                    val isCurrent = rowMatched && currentCell.second == c
                                    val cellBg = when {
                                        isCurrent -> currentBg
                                        (rIdx.toLong() * 1_000_000L + c) in matchSet -> matchBg
                                        rIdx % 2 == 1 -> rowAlt
                                        else -> Color.Transparent
                                    }
                                    val cellInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        // Blank cells are tappable too — you have to be able to select
                                        // an empty cell to type into it. No ripple: a spreadsheet cell
                                        // gives its own feedback (the value popup opens instantly), and
                                        // skipping the indication drops one more subsystem — attaching
                                        // and tearing down a ripple instance — from every cell's cost.
                                        Modifier.width(colWidths[c]).heightIn(min = cellH).background(cellBg)
                                            .clickable(interactionSource = cellInteraction, indication = null) {
                                                onCellTap(rIdx, c, v)
                                            }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicText(v, style = TextStyle(text, cellSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(tailWidth))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SheetScrubberTrackHeight = 208.dp

/**
 * A WPS/Excel-style vertical jump rail for the row axis — drag to scroll to any row in one motion,
 * with a small floating "N/Total" badge that tracks the finger, instead of counting flicks to get
 * from row 12 to row 940.
 *
 * Modelled directly on [PageScrubber] (the PDF viewer's page rail): same track/thumb sizing, same
 * tap-to-jump + drag-to-scrub gesture, same haptic tick per step. The one real difference is that a
 * spreadsheet has no per-row render cost the way a PDF page does, so this scrolls the list live on
 * every drag tick instead of only on release — the sheet content itself becomes the "preview",
 * which is what the WPS reference screenshot actually shows (the grid moving under the thumb, not a
 * separate popup).
 *
 * Only shown for sheets long enough that the rail is a shortcut rather than clutter — a 20-row sheet
 * scrolls in one swipe already.
 */
@Composable
private fun SheetRowScrubber(
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowCount: Int,
    backdrop: LayerBackdrop,
    chromeGlass: Color,
    accent: Color,
    text: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (rowCount < 60) return

    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragRow by remember { mutableIntStateOf(0) }
    val lastSpan = (rowCount - 1).coerceAtLeast(1)

    // Follow normal (non-rail) scrolling when the rail itself isn't being touched.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> if (!isDragging) dragRow = idx.coerceIn(0, lastSpan) }
    }

    // Haptic tick + live scroll on every row the drag crosses.
    LaunchedEffect(dragRow, isDragging) {
        if (isDragging) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            runCatching { listState.scrollToItem(dragRow) }
        }
    }

    val fraction by animateFloatAsState(
        targetValue = (dragRow.toFloat() / lastSpan).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sheetScrubFraction"
    )
    val trackWidth by animateDpAsState(if (isDragging) 8.dp else 4.dp, spring(stiffness = Spring.StiffnessMedium), label = "sheetTrackWidth")
    val thumbHeight by animateDpAsState(if (isDragging) 40.dp else 30.dp, spring(stiffness = Spring.StiffnessMedium), label = "sheetThumbHeight")

    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .height(SheetScrubberTrackHeight)
                .width(28.dp)
                .pointerInput(rowCount) {
                    detectTapGestures { offset ->
                        val target = ((offset.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        dragRow = target
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                    }
                }
                .pointerInput(rowCount) {
                    detectDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            dragRow = ((start.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragRow = ((change.position.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(trackWidth)
                    .height(SheetScrubberTrackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.10f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier
                        .padding(top = ((SheetScrubberTrackHeight - thumbHeight) * fraction).coerceAtLeast(0.dp))
                        .width(if (isDragging) 8.dp else 4.dp)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (isDragging) accent else accent.copy(0.55f))
                )
            }
        }

        // A small pill badge — "12/940", not "Row 12 / 940" in a wide card. Sized to match the 40 dp
        // search-icon circle it sits beside: a plain `CircleShape` can't hold a 4-digit fraction
        // without clipping, so this starts as a near-circle for short numbers and only widens as far
        // as the digits actually need, via `defaultMinSize` rather than a fixed wide padding.
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f, animationSpec = tween(160)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val yOffset = (SheetScrubberTrackHeight * fraction - SheetScrubberTrackHeight / 2f).coerceIn(-90.dp, 90.dp)
            Box(
                Modifier
                    .offset(x = (-32).dp, y = yOffset)
                    .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                    .viewerGlass(backdrop, chromeGlass, shape = { Capsule })
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    stringResource(R.string.sheet_row_of, dragRow + 1, rowCount),
                    style = TextStyle(text, 10.sp, FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A fling behaviour that stacks: flick fast in the same direction again before the previous fling
 * has settled and the next one travels further, up to [MaxFlingMultiplier] times a normal one.
 *
 * A spreadsheet is the one screen in this app that is routinely thousands of rows long, and the
 * platform fling is tuned for lists you read rather than lists you traverse — reaching row 4000
 * takes a tiring number of identical swipes. Repeated fast swipes are already the gesture people
 * reach for there, so this reads them as one intent and gives them distance.
 *
 * It boosts the *initial velocity* and then hands off to the platform's own decay curve, so the
 * motion is the standard one throughout — faster, never jumpier. Nothing snaps or teleports.
 *
 * Only deliberate flicks count toward a streak: a swipe under [MinStreakVelocityDp] per second is
 * someone positioning carefully, and stacking those would make precise scrolling impossible.
 * Changing direction, or pausing past [StreakWindowMillis], resets it.
 */
@Composable
private fun rememberStackingFlingBehavior(): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    val minVelocity = with(LocalDensity.current) { MinStreakVelocityDp.dp.toPx() }
    return remember(base, minVelocity) { StackingFlingBehavior(base, minVelocity) }
}

/** dp per second below which a swipe is treated as positioning, not as a fast flick. */
private const val MinStreakVelocityDp = 1200f

/** How long after a fling a follow-up still counts as part of the same burst. */
private const val StreakWindowMillis = 320L

/** Each consecutive fast flick adds this much of a normal fling's velocity. */
private const val FlingBoostPerSwipe = 0.9f

/** Ceiling, so a long burst can't launch the sheet somewhere unrecoverable. */
private const val MaxFlingMultiplier = 4f

private class StackingFlingBehavior(
    private val base: FlingBehavior,
    private val minVelocity: Float
) : FlingBehavior {

    private var lastDirection = 0
    private var lastFlingAtMillis = 0L
    private var streak = 0

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val now = SystemClock.uptimeMillis()
        val direction = when {
            initialVelocity > 0f -> 1
            initialVelocity < 0f -> -1
            else -> 0
        }
        val isFastFlick = abs(initialVelocity) >= minVelocity
        val continuesBurst = direction != 0 &&
            direction == lastDirection &&
            now - lastFlingAtMillis <= StreakWindowMillis

        streak = if (isFastFlick && continuesBurst) streak + 1 else 0
        lastDirection = direction
        lastFlingAtMillis = now

        val multiplier = (1f + streak * FlingBoostPerSwipe).coerceAtMost(MaxFlingMultiplier)
        // Delegating rather than animating here is the point: the decay curve, the over-scroll
        // handover and the "velocity left over" contract all stay exactly the platform's.
        return with(base) { performFling(initialVelocity * multiplier) }
    }
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

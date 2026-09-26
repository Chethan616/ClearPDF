package com.chethan616.clearpdf.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BorderAll
import androidx.compose.material.icons.rounded.BorderBottom
import androidx.compose.material.icons.rounded.BorderClear
import androidx.compose.material.icons.rounded.BorderInner
import androidx.compose.material.icons.rounded.BorderLeft
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.BorderRight
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.BorderTop
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassBottomSheet
import com.chethan616.clearpdf.ui.components.GlassColorPicker
import com.chethan616.clearpdf.ui.components.GlassDialog
import com.chethan616.clearpdf.ui.components.GlassDialogAction
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassSegmentedControl
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.GlassToolButton
import com.chethan616.clearpdf.ui.components.GlassToolbar
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.OfficeStandardColors
import com.chethan616.clearpdf.ui.components.ShareMorphButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.viewerChromeGlass
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.BorderPreset
import com.chethan616.clearpdf.ui.viewmodel.SpreadsheetViewModel
import com.chethan616.clearpdf.utils.xlsx.Axis
import com.chethan616.clearpdf.utils.xlsx.CellRange
import com.chethan616.clearpdf.utils.xlsx.ColorSpec
import com.chethan616.clearpdf.utils.xlsx.PaintTheme
import com.chethan616.clearpdf.utils.xlsx.SheetLayout
import com.chethan616.clearpdf.utils.xlsx.StylePatch
import com.chethan616.clearpdf.utils.xlsx.ValidationKind
import com.chethan616.clearpdf.utils.xlsx.XlsxColors
import com.chethan616.clearpdf.utils.xlsx.XlsxPainter
import com.chethan616.clearpdf.utils.xlsx.XlsxRefs
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** `liquidGlassPanel`'s corner curve, restated so the scrolling grid can be clipped to it. */
private val GlassPanelShape = RoundedRectangle(28f.dp)

/** Which formatting panel is open. */
private enum class Panel { NONE, FONT_COLOR, FILL_COLOR, ALIGN, BORDERS, NUMBER, ROWS_COLS }

/** An open list-validation dropdown. */
private class DropdownState(val r: Int, val c: Int, val anchor: Rect, val items: List<String>, val strict: Boolean)

private val NumberFormats = listOf(
    "General" to "General",
    "Number" to "#,##0.00",
    "Integer" to "0",
    "Currency" to "\"$\"#,##0.00",
    "Accounting" to "_(\"$\"* #,##0.00_);_(\"$\"* (#,##0.00);_(\"$\"* \"-\"??_);_(@_)",
    "Percent" to "0%",
    "Percent (2dp)" to "0.00%",
    "Scientific" to "0.00E+00",
    "Short date" to "dd-mm-yyyy",
    "Long date" to "d mmmm yyyy",
    "Time" to "h:mm:ss",
    "Text" to "@"
)

/**
 * Spreadsheet viewer and editor.
 *
 * Reading: the file's own look — fills, fonts, borders, merges, column widths, row heights, frozen
 * panes — on a solid document surface inside the glass panel, with sticky column/row headers and a
 * sheet tab strip. Editing (the pencil): a formula bar, a glass formatting toolbar, range handles,
 * list-validation dropdowns, insert/delete rows and columns, undo/redo, and Save / Save as, which
 * patch the original .xlsx in place of regenerating it.
 */
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
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val chromeGlass = viewerChromeGlass(isDark)

    val wb = state.workbook
    val visibleSheets = remember(wb) { wb?.sheets?.indices?.filter { !wb.sheets[it].hidden } ?: emptyList() }
    var sheetIndex by remember { mutableIntStateOf(-1) }
    val idx = if (sheetIndex in visibleSheets) sheetIndex else visibleSheets.firstOrNull() ?: 0
    val sheet = wb?.sheets?.getOrNull(idx)

    var selection by remember { mutableStateOf<GridSelection?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val formulaFocus = remember { FocusRequester() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var dropdown by remember { mutableStateOf<DropdownState?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scrollX = remember { mutableFloatStateOf(0f) }

    // Reset view state when switching sheets.
    LaunchedEffect(idx) {
        selection = null; editing = false; dropdown = null
        scrollX.floatValue = 0f
        runCatching { listState.scrollToItem(0) }
    }

    val surface = if (isDark) Color(0xFF1C1C1E) else Color.White
    val gridColors = remember(isDark) {
        GridColors(
            surface = surface,
            headerBand = if (isDark) Color(0xFF26272B) else Color(0xFFF3F5F8),
            headerText = if (isDark) Color(0xFFB5B6BF) else Color(0xFF5E6068),
            headerDivider = if (isDark) Color(0xFF3A3B40) else Color(0xFFDADDE3),
            accent = accent
        )
    }
    val version = state.version
    val layout = remember(sheet, version) { sheet?.let { SheetLayout(it) } }
    val painter = remember(wb, isDark) {
        wb?.let {
            XlsxPainter(it, PaintTheme(isDark, surface.toArgb(), text.toArgb(), if (isDark) 0xFF34353A.toInt() else 0xFFE2E4E9.toInt()))
        }
    }
    remember(version) { painter?.invalidate(); version }

    val anchorCell = selection?.let { sheet?.cell(it.anchorR, it.anchorC) }
    val anchorStyle = remember(anchorCell, version) { wb?.styles?.resolve(anchorCell?.style ?: 0) }

    // Reads `selection` at call time (not the composition's snapshot), so it is right straight
    // after a selection change inside the same event handler.
    fun anchorEditText(): String =
        selection?.let { s -> sheet?.cell(s.anchorR, s.anchorC) }?.let { wb?.editText(it) } ?: ""

    fun commitDraft() {
        val sel = selection ?: return
        if (!editing) return
        editing = false
        val validation = sheet?.validationAt(sel.anchorR, sel.anchorC)
        if (validation != null && validation.kind == ValidationKind.LIST && validation.strict && draft.isNotEmpty()) {
            val items = wb!!.listItems(validation, sheet)
            if (items.isNotEmpty() && items.none { it.equals(draft, ignoreCase = true) }) {
                Toast.makeText(context, R.string.sheet_invalid_value, Toast.LENGTH_SHORT).show()
                return
            }
        }
        viewModel.setCellText(idx, sel.anchorR, sel.anchorC, draft)
    }

    fun select(sel: GridSelection) {
        if (editing) { commitDraft(); focusManager.clearFocus() }
        dropdown = null
        selection = sel
        draft = sel.let { s -> sheet?.cell(s.anchorR, s.anchorC)?.let { wb?.editText(it) } } ?: ""
    }

    fun startEditing() {
        if (!state.editable || selection == null) return
        if (!editMode) editMode = true
        draft = anchorEditText()
        editing = true
    }

    fun moveSelection(dr: Int, dc: Int) {
        val s = selection ?: return
        val lay = layout ?: return
        var r = s.anchorR
        var c = s.anchorC
        val m = sheet?.mergeAt(r, c)
        if (dr > 0) r = (m?.r2 ?: r) + 1
        if (dc > 0) c = (m?.c2 ?: c) + 1
        r = r.coerceIn(0, lay.nRows - 1); c = c.coerceIn(0, lay.nCols - 1)
        while (r < lay.nRows - 1 && sheet?.isRowHidden(r) == true) r++
        select(GridSelection.cell(r, c, sheet!!))
        if (r >= sheet.frozenRows) {
            val item = lay.itemIndexOf(r)
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == item } || visible.lastOrNull()?.index == item) scope.launch { listState.animateScrollToItem((item - 3).coerceAtLeast(0)) }
        }
    }

    fun openDropdown(r: Int, c: Int, anchor: Rect) {
        val v = sheet?.validationAt(r, c) ?: return
        val items = wb?.listItems(v, sheet) ?: return
        if (items.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        dropdown = DropdownState(r, c, anchor, items, v.strict)
    }

    // Search across the current sheet.
    // Find runs off the main thread and is debounced, so typing never waits on a full-sheet scan
    // (each cell is number-formatted for matching, which is far too slow per keystroke on big sheets).
    val matches by produceState(emptyList<Pair<Int, Int>>(), sheet, searchQuery, version) {
        val q = searchQuery.trim()
        if (q.isBlank() || sheet == null || painter == null) { value = emptyList(); return@produceState }
        kotlinx.coroutines.delay(180L)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val out = ArrayList<Pair<Int, Int>>()
            scan@ for ((r, row) in sheet.rows) for ((c, cell) in row.cells) {
                if (!isActive) return@withContext out
                if (painter.display(cell).contains(q, ignoreCase = true)) {
                    out.add(r to c)
                    if (out.size >= 5000) break@scan
                }
            }
            out
        }
    }
    val currentMatchCell = matches.getOrNull(currentMatch)
    fun scrollToCell(r: Int, c: Int) {
        val lay = layout ?: return
        if (r >= (sheet?.frozenRows ?: 0)) scope.launch { listState.animateScrollToItem((lay.itemIndexOf(r) - 2).coerceAtLeast(0)) }
        val density = context.resources.displayMetrics.density
        val target = ((lay.colX[c] - lay.frozenWidth) * density * zoom - 24 * density).coerceAtLeast(0f)
        if (c >= (sheet?.frozenCols ?: 0)) scrollX.floatValue = target
    }
    LaunchedEffect(matches) {
        currentMatch = 0
        matches.firstOrNull()?.let { (r, c) -> scrollToCell(r, c) }
    }
    fun goToMatch(delta: Int) {
        if (matches.isEmpty()) return
        currentMatch = ((currentMatch + delta) % matches.size + matches.size) % matches.size
        matches[currentMatch].let { (r, c) -> scrollToCell(r, c) }
    }

    // Save / Save as.
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) viewModel.save(context, uri) { res ->
            when (res) {
                is SpreadsheetViewModel.SaveResult.Saved -> {
                    Toast.makeText(context, R.string.sheet_saved, Toast.LENGTH_SHORT).show()
                    if (exitAfterSave) onBack()
                }
                else -> Toast.makeText(context, R.string.sheet_save_failed, Toast.LENGTH_SHORT).show()
            }
        } else exitAfterSave = false
    }
    fun save(asCopy: Boolean) {
        if (editing) commitDraft()
        if (asCopy) { saveAsLauncher.launch(viewModel.suggestedSaveAsName()); return }
        viewModel.save(context, null) { res ->
            when (res) {
                is SpreadsheetViewModel.SaveResult.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    Toast.makeText(context, R.string.sheet_saved, Toast.LENGTH_SHORT).show()
                    if (exitAfterSave) onBack()
                }
                is SpreadsheetViewModel.SaveResult.NeedsSaveAs -> saveAsLauncher.launch(viewModel.suggestedSaveAsName())
                is SpreadsheetViewModel.SaveResult.Failed -> Toast.makeText(context, R.string.sheet_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestBack() {
        when {
            dropdown != null -> dropdown = null
            panel != Panel.NONE -> panel = Panel.NONE
            editing -> { editing = false; draft = anchorEditText(); focusManager.clearFocus() }
            state.dirty -> confirmExit = true
            else -> onBack()
        }
    }
    BackHandler(enabled = dropdown != null || panel != Panel.NONE || editing || state.dirty) { requestBack() }

    fun styleSel(patch: StylePatch) {
        val sel = selection ?: return
        if (editing) commitDraft()
        viewModel.applyStyle(idx, sel.range, patch)
    }

    Box(Modifier.fillMaxSize()) {
        GlassScreenScaffold(
            backdrop = backdrop,
            contentHorizontalPadding = 12.dp,
            headerHorizontalPadding = 12.dp,
            header = { headerBackdrop ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidIconButton(onClick = { requestBack() }, backdrop = headerBackdrop) {
                        Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(
                            text = (if (state.dirty) "• " else "") + state.fileName.ifBlank { stringResource(R.string.viewer_title) },
                            backdrop = headerBackdrop
                        )
                    }
                    if (state.dirty && state.editable) {
                        LiquidIconButton(onClick = { save(false) }, backdrop = headerBackdrop, surfaceColor = accent.copy(0.85f)) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Check, stringResource(R.string.sheet_save), Modifier.size(20.dp), Color.White)
                        }
                    }
                    if (!editMode) {
                        LiquidIconButton(onClick = { showSearch = true }, backdrop = headerBackdrop) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.viewer_find), Modifier.size(20.dp), text)
                        }
                    }
                    if (state.editable) {
                        LiquidIconButton(
                            onClick = {
                                if (editMode) { if (editing) commitDraft(); editMode = false; panel = Panel.NONE; focusManager.clearFocus() }
                                else { editMode = true; showSearch = false }
                            },
                            backdrop = headerBackdrop,
                            surfaceColor = if (editMode) accent.copy(0.22f) else Color.Unspecified
                        ) {
                            if (editMode) BasicText(stringResource(R.string.sheet_done), style = TextStyle(accent, 13.sp, FontWeight.SemiBold))
                            else Icon(Icons.Rounded.Edit, stringResource(R.string.sheet_edit), Modifier.size(19.dp), text)
                        }
                    }
                }
            }
        ) { contentPadding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
                }
                sheet == null || layout == null || painter == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(state.error ?: "Empty spreadsheet", style = TextStyle(sub, 14.sp))
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        // Only while a cell is being typed into (formula bar) does the grid lift above
                        // the keyboard. Keying this on "search closed" made the tabs jump up when ✕
                        // was tapped: search closes a frame before the keyboard finishes hiding.
                        .then(
                            if (editing) Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                            else Modifier
                        )
                ) {
                    // Formula bar: the selected cell's reference and its content.
                    AnimatedVisibility(
                        visible = selection != null,
                        enter = fadeIn(GlassMotion.fade()),
                        exit = fadeOut(GlassMotion.fade())
                    ) {
                        FormulaBar(
                            ref = selection?.let { s ->
                                if (s.range.isSingle || sheet.mergeAt(s.anchorR, s.anchorC) == s.range) XlsxRefs.cellRef(s.anchorR, s.anchorC)
                                else if (s.isWholeColumn) XlsxRefs.colLetter(s.range.c1) + ":" + XlsxRefs.colLetter(s.range.c2)
                                else if (s.isWholeRow) "${s.range.r1 + 1}:${s.range.r2 + 1}"
                                else s.range.toRef()
                            } ?: "",
                            value = if (editing) draft else anchorEditText(),
                            editable = state.editable && editMode,
                            editing = editing,
                            focusRequester = formulaFocus,
                            backdrop = backdrop,
                            glass = chromeGlass,
                            text = text, sub = sub, accent = accent,
                            onValueChange = { draft = it },
                            onStartEdit = { startEditing() },
                            // Enter commits and moves down, staying in typing mode — the keyboard
                            // doesn't bounce between cells when filling a column.
                            onCommit = { commitDraft(); moveSelection(1, 0); startEditing() },
                            onCancel = { editing = false; draft = anchorEditText(); focusManager.clearFocus() }
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = if (selection != null) 8.dp else 0.dp)
                            .liquidGlassPanel(backdrop, uiSensor, containerColorOverride = surface)
                            .clip(GlassPanelShape)
                    ) {
                        SpreadsheetGrid(
                            sheet = sheet,
                            layout = layout,
                            painter = painter,
                            version = version,
                            zoom = zoom,
                            listState = listState,
                            scrollX = scrollX,
                            selection = selection,
                            matches = matches,
                            currentMatch = currentMatchCell,
                            colors = gridColors,
                            editMode = editMode,
                            flingBehavior = rememberStackingFlingBehavior(),
                            onSelect = { select(it) },
                            onTapSelected = { r, c ->
                                val v = sheet.validationAt(r, c)
                                if (editMode && v != null && v.kind == ValidationKind.LIST) {
                                    val sel = selection
                                    if (sel != null) openDropdown(r, c, Rect.Zero)
                                } else startEditing()
                            },
                            onDropdown = { r, c, anchor -> if (state.editable) openDropdown(r, c, anchor) },
                            onZoom = { z -> zoom = (zoom * z).coerceIn(0.5f, 2.5f) },
                            onColumnResize = { c, w -> viewModel.setColWidth(idx, c, w) },
                            // Own layer: the glass panel around the grid redraws on every motion-sensor
                            // tick (its highlight follows gravity). Without isolation each of those
                            // re-ran drawGridRow for every visible row (~48 ms/frame, continuously).
                            // Now the panel reuses the grid's recorded display list.
                            modifier = Modifier.fillMaxSize().graphicsLayer()
                        )
                        SheetRowScrubber(
                            listState = listState,
                            rowCount = layout.scrollRows.size,
                            backdrop = backdrop,
                            chromeGlass = chromeGlass,
                            accent = accent,
                            text = text,
                            isDark = isDark,
                            labelFor = { i -> (layout.scrollRows.getOrElse(i) { i } + 1) },
                            totalLabel = (sheet.maxRow + 1).coerceAtLeast(1),
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                        )
                    }
                    SheetTabs(
                        wb = wb!!,
                        indices = visibleSheets,
                        selected = idx,
                        backdrop = backdrop,
                        glass = chromeGlass,
                        text = text, sub = sub, accent = accent,
                        onSelect = { i -> if (editing) commitDraft(); sheetIndex = i },
                        modifier = Modifier.padding(top = 8.dp, end = if (!editMode && !showSearch) 64.dp else 0.dp)
                    )
                    AnimatedVisibility(
                        visible = editMode,
                        enter = fadeIn(GlassMotion.fade()),
                        exit = fadeOut(GlassMotion.fade())
                    ) {
                        EditToolbar(
                            backdrop = backdrop,
                            accent = accent,
                            canUndo = state.canUndo,
                            canRedo = state.canRedo,
                            hasSelection = selection != null,
                            bold = anchorStyle?.bold == true,
                            italic = anchorStyle?.italic == true,
                            underline = anchorStyle?.underline == true,
                            strike = anchorStyle?.strike == true,
                            panel = panel,
                            onUndo = { if (editing) commitDraft(); viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onBold = { styleSel(StylePatch(bold = !(anchorStyle?.bold ?: false))) },
                            onItalic = { styleSel(StylePatch(italic = !(anchorStyle?.italic ?: false))) },
                            onUnderline = { styleSel(StylePatch(underline = !(anchorStyle?.underline ?: false))) },
                            onStrike = { styleSel(StylePatch(strike = !(anchorStyle?.strike ?: false))) },
                            onPanel = { p -> focusManager.clearFocus(); if (editing) commitDraft(); panel = if (panel == p) Panel.NONE else p },
                            onClear = { selection?.let { viewModel.clearRange(idx, it.range) } },
                            onSaveAs = { save(true) },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Share / export (reading mode): tap = open as PDF, long-press + swipe up = share.
        if (sheet != null && !showSearch && !editMode) {
            ShareMorphButton(
                backdrop = backdrop,
                glass = chromeGlass,
                fg = text,
                onOpen = { viewModel.exportToPdf(context) { u -> u?.let(onOpenPdf) } },
                onShare = { viewModel.shareableUri(context) { u -> u?.let { shareFile(context, it) } } },
                idleIcon = Icons.Rounded.PictureAsPdf,
                idleContentDesc = stringResource(R.string.sheet_export_pdf),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp)
            )
        }

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
                surface = chromeGlass,
                onQueryChange = { searchQuery = it },
                onPrevMatch = { goToMatch(-1) },
                onNextMatch = { goToMatch(1) },
                onClose = { showSearch = false; searchQuery = "" }
            )
        }

        // List-validation dropdown, anchored under (or above) the cell.
        dropdown?.let { dd ->
            Box(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { dropdown = null }
            )
            ValidationDropdown(
                state = dd,
                current = sheet?.cell(dd.r, dd.c)?.let { painter?.display(it) } ?: "",
                backdrop = backdrop,
                glass = chromeGlass,
                text = text, accent = accent,
                onPick = { value ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    viewModel.setCellText(idx, dd.r, dd.c, value)
                    dropdown = null
                    editing = false
                }
            )
        }

        // Colour picker popover (text or fill).
        val colorPanel = panel == Panel.FONT_COLOR || panel == Panel.FILL_COLOR
        AnimatedVisibility(
            visible = colorPanel,
            enter = fadeIn(GlassMotion.fade()) + scaleIn(GlassMotion.pop(), initialScale = 0.94f),
            exit = fadeOut(GlassMotion.fade()) + scaleOut(targetScale = 0.96f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { panel = Panel.NONE }) {
                val isFill = panel == Panel.FILL_COLOR
                val theme = wb?.styles?.themeColors ?: XlsxColors.DefaultTheme
                val themeColors = remember(theme) { (0 until 10).map { Color(theme.getOrElse(it) { XlsxColors.DefaultTheme[it] }) } }
                val current = if (isFill) anchorStyle?.fillArgb else anchorStyle?.fontArgb
                GlassColorPicker(
                    color = current?.let { Color(it) } ?: if (isFill) Color.Transparent else Color.Black,
                    onColorChange = { c ->
                        val spec = ColorSpec.ofArgb(c.toArgb())
                        styleSel(if (isFill) StylePatch(fill = spec) else StylePatch(fontColor = spec))
                    },
                    backdrop = backdrop,
                    themeColors = themeColors,
                    standardColors = OfficeStandardColors,
                    themeVariants = { base -> XlsxColors.themeGridTints(base.toArgb()).map { t -> Color(XlsxColors.applyTint(base.toArgb(), t)) } },
                    onThemePick = { col, row, _ ->
                        val base = theme.getOrElse(col) { 0 }
                        val tint = if (row == 0) 0.0 else XlsxColors.themeGridTints(base)[row - 1]
                        val spec = ColorSpec(theme = col, tint = tint)
                        styleSel(if (isFill) StylePatch(fill = spec) else StylePatch(fontColor = spec))
                    },
                    onClear = {
                        styleSel(if (isFill) StylePatch(clearFill = true) else StylePatch(fontColor = ColorSpec(theme = 1)))
                        panel = Panel.NONE
                    },
                    clearLabel = stringResource(if (isFill) R.string.sheet_no_fill else R.string.sheet_automatic),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
                        .widthIn(max = 420.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                )
            }
        }

        // Other formatting panels.
        val sheetPanel = panel == Panel.ALIGN || panel == Panel.BORDERS || panel == Panel.NUMBER || panel == Panel.ROWS_COLS
        var lastSheetPanel by remember { mutableStateOf(Panel.ALIGN) }
        if (sheetPanel) lastSheetPanel = panel
        GlassBottomSheet(
            visible = sheetPanel,
            onDismiss = { panel = Panel.NONE },
            backdrop = backdrop
        ) {
            val sel = selection
            when (lastSheetPanel) {
                Panel.ALIGN -> AlignPanel(
                    backdrop = backdrop, text = text,
                    h = anchorStyle?.hAlign ?: "general", v = anchorStyle?.vAlign ?: "bottom", wrap = anchorStyle?.wrap == true,
                    onH = { styleSel(StylePatch(hAlign = it)) },
                    onV = { styleSel(StylePatch(vAlign = it)) },
                    onWrap = { styleSel(StylePatch(wrap = it)) }
                )
                Panel.BORDERS -> BordersPanel(text = text, accent = accent) { preset ->
                    if (sel != null) { if (editing) commitDraft(); viewModel.applyBorders(idx, sel.range, preset) }
                    panel = Panel.NONE
                }
                Panel.NUMBER -> NumberPanel(text = text, sub = sub, accent = accent, current = anchorStyle?.numFmtCode ?: "General") { code ->
                    styleSel(StylePatch(numFmtCode = code)); panel = Panel.NONE
                }
                Panel.ROWS_COLS -> RowsColsPanel(text = text, accent = accent, enabled = sel != null) { action ->
                    val s = sel ?: return@RowsColsPanel
                    if (editing) commitDraft()
                    val rows = if (s.isWholeColumn) 1 else s.range.r2 - s.range.r1 + 1
                    val cols = if (s.isWholeRow) 1 else s.range.c2 - s.range.c1 + 1
                    when (action) {
                        0 -> viewModel.insertLines(idx, Axis.ROW, s.range.r1, rows)
                        1 -> viewModel.insertLines(idx, Axis.ROW, s.range.r2 + 1, rows)
                        2 -> viewModel.deleteLines(idx, Axis.ROW, s.range.r1, rows)
                        3 -> viewModel.insertLines(idx, Axis.COL, s.range.c1, cols)
                        4 -> viewModel.insertLines(idx, Axis.COL, s.range.c2 + 1, cols)
                        5 -> viewModel.deleteLines(idx, Axis.COL, s.range.c1, cols)
                    }
                    selection = GridSelection.cell(s.anchorR, s.anchorC, sheet!!)
                    panel = Panel.NONE
                }
                else -> Unit
            }
        }

        // Unsaved changes.
        GlassDialog(
            visible = confirmExit,
            onDismiss = { confirmExit = false },
            backdrop = backdrop,
            title = stringResource(R.string.sheet_unsaved_title),
            actions = {
                GlassDialogAction(stringResource(R.string.sheet_discard), onClick = { confirmExit = false; onBack() }, destructive = true)
                GlassDialogAction(stringResource(R.string.cancel), onClick = { confirmExit = false })
                GlassDialogAction(stringResource(R.string.sheet_save), onClick = { confirmExit = false; exitAfterSave = true; save(false) }, primary = true)
            }
        ) {
            BasicText(stringResource(R.string.sheet_unsaved_body), style = TextStyle(sub, 15.sp))
        }
    }
}

@Composable
private fun FormulaBar(
    ref: String,
    value: String,
    editable: Boolean,
    editing: Boolean,
    focusRequester: FocusRequester,
    backdrop: LayerBackdrop,
    glass: Color,
    text: Color,
    sub: Color,
    accent: Color,
    onValueChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(editing) { if (editing) runCatching { focusRequester.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .viewerGlass(backdrop, glass, shape = { RoundedRectangle(20f.dp) })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(ref, style = TextStyle(accent, 13.sp, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.widthIn(min = 36.dp, max = 110.dp))
        Box(Modifier.width(1.dp).height(22.dp).background(sub.copy(0.35f)))
        BasicText("fx", style = TextStyle(sub, 13.sp, FontWeight.Medium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = !editing,
                singleLine = false,
                maxLines = 4,
                textStyle = TextStyle(text, 15.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused && !editing && editable) onStartEdit() },
                decorationBox = { inner ->
                    if (value.isEmpty() && editing) BasicText(stringResource(R.string.sheet_formula_hint), style = TextStyle(sub, 15.sp))
                    inner()
                }
            )
        }
        if (editing) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(accent).clickable { onCommit() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Check, stringResource(R.string.save), Modifier.size(18.dp), Color.White) }
        }
    }
}

@Composable
private fun SheetTabs(
    wb: com.chethan616.clearpdf.utils.xlsx.XlsxWorkbook,
    indices: List<Int>,
    selected: Int,
    backdrop: LayerBackdrop,
    glass: Color,
    text: Color,
    sub: Color,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .viewerGlass(backdrop, glass, shape = { Capsule })
            .padding(4.dp)
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in indices) {
            val s = wb.sheets[i]
            val sel = i == selected
            val tab = s.tabColor?.resolve(wb.styles.themeColors, wb.styles.indexedColors)?.let { Color(it) }
            Row(
                Modifier
                    .height(44.dp)
                    .clip(Capsule)
                    .background(if (sel) accent.copy(0.18f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (tab != null) Box(Modifier.size(8.dp).clip(CircleShape).background(tab))
                BasicText(
                    s.name,
                    style = TextStyle(if (sel) accent else text, 14.sp, if (sel) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
        }
    }
}

@Composable
private fun EditToolbar(
    backdrop: LayerBackdrop,
    accent: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    panel: Panel,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onPanel: (Panel) -> Unit,
    onClear: () -> Unit,
    onSaveAs: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassToolbar(backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        GlassToolButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.sheet_undo), false, onUndo, enabled = canUndo, accent = accent)
        GlassToolButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.sheet_redo), false, onRedo, enabled = canRedo, accent = accent)
        GlassToolButton(Icons.Rounded.FormatBold, stringResource(R.string.sheet_bold), bold, onBold, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatItalic, stringResource(R.string.sheet_italic), italic, onItalic, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatUnderlined, stringResource(R.string.sheet_underline), underline, onUnderline, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatStrikethrough, stringResource(R.string.sheet_strike), strike, onStrike, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatColorText, stringResource(R.string.sheet_text_color), panel == Panel.FONT_COLOR, { onPanel(Panel.FONT_COLOR) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatColorFill, stringResource(R.string.sheet_fill_color), panel == Panel.FILL_COLOR, { onPanel(Panel.FILL_COLOR) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatAlignLeft, stringResource(R.string.sheet_align), panel == Panel.ALIGN, { onPanel(Panel.ALIGN) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.BorderAll, stringResource(R.string.sheet_borders), panel == Panel.BORDERS, { onPanel(Panel.BORDERS) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.Numbers, stringResource(R.string.sheet_number_format), panel == Panel.NUMBER, { onPanel(Panel.NUMBER) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.TableRows, stringResource(R.string.sheet_rows_cols), panel == Panel.ROWS_COLS, { onPanel(Panel.ROWS_COLS) }, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.FormatClear, stringResource(R.string.sheet_clear), false, onClear, enabled = hasSelection, accent = accent)
        GlassToolButton(Icons.Rounded.SaveAs, stringResource(R.string.sheet_save_as), false, onSaveAs, accent = accent)
    }
}

@Composable
private fun AlignPanel(
    backdrop: LayerBackdrop,
    text: Color,
    h: String,
    v: String,
    wrap: Boolean,
    onH: (String) -> Unit,
    onV: (String) -> Unit,
    onWrap: (Boolean) -> Unit
) {
    val hOptions = listOf("left", "center", "right")
    val vOptions = listOf("top", "center", "bottom")
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PanelLabel(stringResource(R.string.sheet_horizontal), text)
        GlassSegmentedControl(
            options = listOf("Left", "Center", "Right"),
            selectedIndex = hOptions.indexOf(h).coerceAtLeast(0).let { if (h == "general") -1 else it },
            onSelect = { onH(hOptions[it]) },
            backdrop = backdrop,
            icons = listOf(Icons.Rounded.FormatAlignLeft, Icons.Rounded.FormatAlignCenter, Icons.Rounded.FormatAlignRight),
            modifier = Modifier.fillMaxWidth()
        )
        PanelLabel(stringResource(R.string.sheet_vertical), text)
        GlassSegmentedControl(
            options = listOf("Top", "Middle", "Bottom"),
            selectedIndex = vOptions.indexOf(v).coerceAtLeast(0),
            onSelect = { onV(vOptions[it]) },
            backdrop = backdrop,
            icons = listOf(Icons.Rounded.VerticalAlignTop, Icons.Rounded.VerticalAlignCenter, Icons.Rounded.VerticalAlignBottom),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.sheet_wrap), Modifier.weight(1f), style = TextStyle(text, 16.sp, FontWeight.Medium))
            LiquidToggle(selected = { wrap }, onSelect = onWrap, backdrop = backdrop)
        }
    }
}

@Composable
private fun PanelLabel(label: String, color: Color) {
    BasicText(label.uppercase(), style = TextStyle(color.copy(0.6f), 12.sp, FontWeight.SemiBold, letterSpacing = 0.6.sp))
}

@Composable
private fun PanelRow(icon: ImageVector?, label: String, color: Color, tint: Color, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) tint.copy(0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), if (enabled) tint else color.copy(0.3f))
        BasicText(label, Modifier.weight(1f), style = TextStyle(if (enabled) color else color.copy(0.4f), 16.sp, FontWeight.Medium))
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint)
    }
}

@Composable
private fun BordersPanel(text: Color, accent: Color, onPick: (BorderPreset) -> Unit) {
    val items = listOf(
        Triple(BorderPreset.ALL, Icons.Rounded.BorderAll, "All borders"),
        Triple(BorderPreset.OUTER, Icons.Rounded.BorderOuter, "Outside borders"),
        Triple(BorderPreset.THICK_OUTER, Icons.Rounded.BorderStyle, "Thick outside borders"),
        Triple(BorderPreset.INNER, Icons.Rounded.BorderInner, "Inside borders"),
        Triple(BorderPreset.TOP, Icons.Rounded.BorderTop, "Top border"),
        Triple(BorderPreset.BOTTOM, Icons.Rounded.BorderBottom, "Bottom border"),
        Triple(BorderPreset.LEFT, Icons.Rounded.BorderLeft, "Left border"),
        Triple(BorderPreset.RIGHT, Icons.Rounded.BorderRight, "Right border"),
        Triple(BorderPreset.NONE, Icons.Rounded.BorderClear, "No borders")
    )
    Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        for ((p, icon, label) in items) PanelRow(icon, label, text, accent) { onPick(p) }
    }
}

@Composable
private fun NumberPanel(text: Color, sub: Color, accent: Color, current: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        for ((label, code) in NumberFormats) {
            val sample = com.chethan616.clearpdf.utils.ExcelCellFormat.apply(if (code.contains("d") && code.contains("y")) "45566" else "1234.5", code)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (current == code) accent.copy(0.16f) else Color.Transparent)
                    .clickable { onPick(code) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(label, Modifier.weight(1f), style = TextStyle(if (current == code) accent else text, 16.sp, FontWeight.Medium))
                BasicText(sample, style = TextStyle(sub, 14.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RowsColsPanel(text: Color, accent: Color, enabled: Boolean, onAction: (Int) -> Unit) {
    val labels = listOf(
        R.string.sheet_insert_row_above, R.string.sheet_insert_row_below, R.string.sheet_delete_rows,
        R.string.sheet_insert_col_left, R.string.sheet_insert_col_right, R.string.sheet_delete_cols
    )
    Column(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, id ->
            val destructive = i == 2 || i == 5
            PanelRow(null, stringResource(id), if (destructive) LiquidGlassColors.Red else text, if (destructive) LiquidGlassColors.Red else accent, enabled = enabled) { onAction(i) }
        }
    }
}

@Composable
private fun ValidationDropdown(
    state: DropdownState,
    current: String,
    backdrop: LayerBackdrop,
    glass: Color,
    text: Color,
    accent: Color,
    onPick: (String) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = with(density) { maxHeight.toPx() }
        val screenW = with(density) { maxWidth.toPx() }
        val anchor = if (state.anchor == Rect.Zero) Rect(screenW * 0.2f, screenH * 0.35f, screenW * 0.8f, screenH * 0.35f) else state.anchor
        val itemH = with(density) { 46.dp.toPx() }
        val listH = (state.items.size * itemH + with(density) { 16.dp.toPx() }).coerceAtMost(screenH * 0.45f)
        val width = maxOf(anchor.width, with(density) { 220.dp.toPx() }).coerceAtMost(screenW - with(density) { 24.dp.toPx() })
        val below = anchor.bottom + listH + 24f < screenH
        val y = if (below) anchor.bottom + 6f else (anchor.top - listH - 6f).coerceAtLeast(24f)
        val x = anchor.left.coerceIn(with(density) { 12.dp.toPx() }, screenW - width - with(density) { 12.dp.toPx() })
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(GlassMotion.fade()) + scaleIn(GlassMotion.pop(), initialScale = 0.92f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (below) 0f else 1f)),
            modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
        ) {
            Column(
                Modifier
                    .width(with(density) { width.toDp() })
                    .heightIn(max = with(density) { listH.toDp() })
                    .viewerGlass(backdrop, glass, shape = { RoundedRectangle(18f.dp) })
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (item in state.items) {
                    val sel = item.equals(current, ignoreCase = true)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) accent.copy(0.16f) else Color.Transparent)
                            .clickable { onPick(item) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(item, Modifier.weight(1f), style = TextStyle(if (sel) accent else text, 15.sp, if (sel) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), accent)
                    }
                }
            }
        }
    }
}

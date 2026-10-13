package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatIndentDecrease
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Superscript
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.docx.CharFmt
import com.chethan616.clearpdf.docx.DocBlock
import com.chethan616.clearpdf.docx.DocPara
import com.chethan616.clearpdf.docx.DocRaw
import com.chethan616.clearpdf.docx.DocRow
import com.chethan616.clearpdf.docx.DocTable
import com.chethan616.clearpdf.docx.DocxEdits
import com.chethan616.clearpdf.docx.DocxObjects
import com.chethan616.clearpdf.docx.DocxPackage
import com.chethan616.clearpdf.docx.DocxStyles
import com.chethan616.clearpdf.docx.HighlightColors
import com.chethan616.clearpdf.docx.OBJ_CHAR
import com.chethan616.clearpdf.docx.ObjInfo
import com.chethan616.clearpdf.docx.Props
import com.chethan616.clearpdf.docx.RProps
import com.chethan616.clearpdf.docx.XNode
import com.chethan616.clearpdf.docx.XmlScan
import com.chethan616.clearpdf.docx.findPara
import com.chethan616.clearpdf.docx.forEachPara
import com.chethan616.clearpdf.ui.components.GlassBackButton
import com.chethan616.clearpdf.ui.components.GlassColorPicker
import com.chethan616.clearpdf.ui.components.GlassDialog
import com.chethan616.clearpdf.ui.components.GlassDialogAction
import com.chethan616.clearpdf.ui.components.GlassDropdownOption
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassTitlePill
import com.chethan616.clearpdf.ui.components.GlassToolButton
import com.chethan616.clearpdf.ui.components.GlassToolbar
import com.chethan616.clearpdf.ui.components.LiquidGlassDropdown
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LocalBackToLibraryAction
import com.chethan616.clearpdf.ui.components.NightPaper
import com.chethan616.clearpdf.ui.components.OfficeStandardColors
import com.chethan616.clearpdf.ui.components.OfficeThemeColors
import com.chethan616.clearpdf.ui.components.UnsavedChangesDialog
import com.chethan616.clearpdf.ui.components.liquidRowClick
import com.chethan616.clearpdf.ui.components.rememberScreenBackdrop
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.DocxEditorViewModel
import com.chethan616.clearpdf.utils.PdfPrint
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Stands before every paragraph's text in its field, so Backspace at the start is observable. */
private const val ZWSP = '\u200B'

private val PaperCorner = 6.dp
/** Desk showing around the pages, as in LibreOffice Writer. */
private val DeskGutter = 8.dp
private val PageGap = 14.dp

private enum class DocPanel { NONE, TEXT_COLOR, HIGHLIGHT }

/** Formatting chosen with nothing selected: it applies to what is typed next at [at]. */
private class Pending(val paraId: Long, val at: Int, val fmt: CharFmt)

internal sealed class DocItem(val key: Any, val page: Int) {
    /** A paragraph, or (reading) the part of one that falls on [page], when Word broke it across pages. */
    class Para(
        val para: DocPara,
        val readOnly: Boolean,
        page: Int,
        val range: IntRange? = null
    ) : DocItem(if (range == null) para.id else "${para.id}:${range.first}", page) {
        var spaceTop = true
        var spaceBottom = true
        val first: Boolean get() = range == null || range.first == 0
        val last: Boolean get() = range == null || range.last >= para.text.length - 1
    }
    class TRow(val table: DocTable, val row: DocRow, val rowIndex: Int, val readOnly: Boolean, page: Int) : DocItem("r" + row.id, page)
    /** The end of one page and the start of the next ([page]). */
    class Gap(key: String, page: Int) : DocItem(key, page)
    class Top : DocItem("top", 0)
    class Bottom(page: Int) : DocItem("bottom", page)
}

internal class Flat(val items: List<DocItem>, val indexOf: Map<Long, Int>, val pageStarts: List<Int>) {
    val pages: Int get() = pageStarts.size
}

/**
 * The document as one list for the LazyColumn, in pages. A page ends where Word ended it when it
 * last laid the file out (its `lastRenderedPageBreak` marks, after Gander), at page and section
 * breaks, and before paragraphs set to start a page. While reading, a paragraph Word broke across
 * two pages is shown in two parts; while editing it stays whole (one field) and the page ends after it.
 */
internal fun flatten(blocks: List<DocBlock>, styles: DocxStyles, splitParas: Boolean): Flat {
    val items = ArrayList<DocItem>()
    val index = HashMap<Long, Int>()
    val pageStarts = arrayListOf(0)
    items.add(DocItem.Top())
    var page = 0
    var pending = false
    var inked = false
    var lastPara: DocItem.Para? = null
    var lastStyle: String? = null
    var lastContextual = false

    fun breakHere() { if (inked) pending = true }
    fun beforeContent() {
        if (!pending) return
        page++
        pageStarts.add(items.size)
        items.add(DocItem.Gap("g$page", page))
        pending = false
        inked = false
        lastPara = null
    }

    fun add(list: List<DocBlock>, readOnly: Boolean) {
        for (b in list) when (b) {
            is DocPara -> {
                val direct = Props.p(b.pPr)
                val pp = direct.over(styles.paragraphStyle(direct.styleId).first)
                if (pp.pageBreakBefore == true) breakHere()
                val len = b.text.length
                fun shows(i: Int): Boolean {
                    val c = b.text[i]
                    return !(c.isWhitespace() || (c == OBJ_CHAR && DocxObjects.isPageBreak(b.fmts.getOrNull(i)?.obj)))
                }
                val firstInk = (0 until len).firstOrNull { shows(it) } ?: len
                val lastInkEnd = ((len - 1) downTo 0).firstOrNull { shows(it) }?.plus(1) ?: 0
                val cuts = sortedSetOf<Int>()
                cuts.addAll(b.marks)
                b.fmts.forEachIndexed { i, f -> if (DocxObjects.isPageBreak(f.obj)) cuts.add(i + 1) }
                if (cuts.any { it <= firstInk } && firstInk < len) breakHere()
                val trailing = cuts.any { it >= lastInkEnd && lastInkEnd > 0 }
                // Cuts inside the text, each leaving visible text on both sides.
                val inner = ArrayList<Int>()
                var prev = firstInk
                for (c in cuts) {
                    if (c <= firstInk || c >= lastInkEnd) continue
                    if ((prev until c).any { shows(it) }) { inner.add(c); prev = c }
                }
                val style = direct.styleId ?: styles.defaultParagraphId
                val contextual = pp.contextual == true
                val ro = readOnly || !b.editable
                val starts = index.containsKey(b.id).not()
                if (inner.isEmpty() || !splitParas) {
                    beforeContent()
                    val item = DocItem.Para(b, ro, page)
                    lastPara?.let { prevItem ->
                        if (lastStyle == style) {
                            if (lastContextual) prevItem.spaceBottom = false
                            if (contextual) item.spaceTop = false
                        }
                    }
                    if (starts) index[b.id] = items.size
                    items.add(item)
                    inked = true
                    lastPara = item; lastStyle = style; lastContextual = contextual
                    if (inner.isNotEmpty()) breakHere()
                } else {
                    var from = 0
                    for ((k, c) in (inner + len).withIndex()) {
                        if (k > 0) breakHere()
                        beforeContent()
                        val item = DocItem.Para(b, ro, page, from until c)
                        if (k == 0 && starts) index[b.id] = items.size
                        items.add(item)
                        inked = true
                        from = c
                    }
                    lastPara = null
                }
                if (trailing) breakHere()
                val sect = Props.child(b.pPr, "sectPr")
                if (sect != null && !sect.contains("continuous")) breakHere()
            }
            is DocTable -> {
                lastPara = null
                for ((ri, r) in b.rows.withIndex()) {
                    var marked = false
                    for (c in r.cells) forEachPara(c.blocks) { if (it.marks.isNotEmpty()) marked = true }
                    if (marked) breakHere()
                    beforeContent()
                    val at = items.size
                    for (c in r.cells) forEachPara(c.blocks) { index[it.id] = at }
                    items.add(DocItem.TRow(b, r, ri, readOnly, page))
                    inked = true
                }
            }
            is DocRaw -> if (b.preview.isNotEmpty()) add(b.preview, true)
        }
    }
    add(blocks, false)
    items.add(DocItem.Bottom(page))
    return Flat(items, index, pageStarts)
}

/** Shared state and callbacks every paragraph needs; one object so items take one parameter. */
private class DocEnv(
    val pkg: DocxPackage,
    val editMode: Boolean,
    val activeId: Long?,
    val activeValue: TextFieldValue,
    val focusTarget: Long?,
    val labels: Map<Long, String>,
    val hits: Map<Long, List<IntRange>>,
    val currentHit: Pair<Long, IntRange>?,
    val accent: Color,
    /** Width between the page margins. */
    val textWidth: Dp,
    val onChange: (DocPara, TextFieldValue) -> Unit,
    val onFocused: (DocPara) -> Unit,
    val onBlurred: (DocPara) -> Unit,
    val onFocusConsumed: () -> Unit,
    val onReadOnlyTap: () -> Unit
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The Word editor (#73): a .docx opens as a document, not as a converted PDF. Its pages are shown
 * as on a desktop: true proportions and margins, Word's own page breaks, pinch to zoom. The pencil
 * makes every paragraph and table cell editable in place, with a Writer-style formatting bar and a
 * tool dock; saving patches the original .docx.
 *
 * Its light or dark look follows the document's reader mode, not the app theme: the desk, every
 * glass control, menu and dialog take their ink from the paper they sit on (DESIGN.md: ink adapts
 * to what is behind the glass).
 */
@Composable
fun DocxEditorScreen(
    backdrop: LayerBackdrop,
    viewModel: DocxEditorViewModel,
    onBack: () -> Unit,
    onOpenPdf: (android.net.Uri) -> Unit
) {
    val appDark = LocalIsDarkMode.current
    val context = LocalContext.current
    // The moon button's choice is saved; until then the paper follows the theme and #74's setting.
    val autoDarkPaper = appDark && remember { AppSettingsManager.getAutoDarkPages(context) }
    var paperDarkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val paperDark = paperDarkOverride ?: autoDarkPaper

    // The status bar sits over the page too: dark icons on white paper, light ones on dark.
    val view = LocalView.current
    DisposableEffect(view, paperDark) {
        val window = view.context.findActivity()?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.isAppearanceLightStatusBars = !paperDark
        onDispose { ctrl?.isAppearanceLightStatusBars = !appDark }
    }

    CompositionLocalProvider(LocalIsDarkMode provides paperDark) {
        DocxEditorContent(backdrop, viewModel, onBack, onOpenPdf, paperDark) { paperDarkOverride = !paperDark }
    }
}

@Composable
private fun DocxEditorContent(
    backdrop: LayerBackdrop,
    viewModel: DocxEditorViewModel,
    onBack: () -> Unit,
    onOpenPdf: (android.net.Uri) -> Unit,
    paperDark: Boolean,
    onTogglePaper: () -> Unit
) {
    val text = LiquidGlassColors.text(paperDark)
    val sub = LiquidGlassColors.secondary(paperDark)
    val accent = LiquidGlassColors.Blue
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val uiSensor = rememberUISensor()
    val density = LocalDensity.current
    val desk = if (paperDark) Color(0xFF0D0D0F) else Color(0xFFE3E4E9)

    val meta = viewModel.meta
    val pkg = viewModel.pkg
    val doc = viewModel.doc
    val editable = viewModel.editable

    var editMode by rememberSaveable { mutableStateOf(false) }
    /** The paragraph field that has focus; Enter is swallowed while none has (see the root Box). */
    var focusedField by remember { mutableStateOf<Long?>(null) }
    // 1 = the page fits the screen's width, as a desktop shows a whole page.
    var zoomTarget by rememberSaveable { mutableFloatStateOf(1f) }
    val zoom = (zoomTarget * 20f).roundToInt() / 20f
    val pinchFocus = remember { floatArrayOf(0f, 0f) }
    var activeId by remember { mutableStateOf<Long?>(null) }
    var activeValue by remember { mutableStateOf(TextFieldValue()) }
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var panel by remember { mutableStateOf(DocPanel.NONE) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }
    var readOnlyHinted by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()

    val carlito = remember { DocxLook.carlito() }
    // Paragraph properties only (indents, lists, styles): independent of size and colour.
    val propsLook = remember(pkg) { pkg?.let { DocxLook(it, false, 1f, 1f, carlito) } }
    val flat = remember(doc, pkg, editMode) { if (doc != null && pkg != null) flatten(doc.blocks, pkg.styles, !editMode) else null }
    val labels = remember(doc, pkg) { if (doc != null && pkg != null) numberingLabels(pkg, doc.blocks) else emptyMap() }

    // Find, off the main thread and debounced.
    val matches by produceState(emptyList<Pair<Long, IntRange>>(), doc, searchQuery) {
        val q = searchQuery.trim()
        val d = doc
        if (q.isEmpty() || d == null) { value = emptyList(); return@produceState }
        delay(160)
        value = withContext(Dispatchers.Default) {
            val out = ArrayList<Pair<Long, IntRange>>()
            forEachPara(d.blocks) { p ->
                var i = p.text.indexOf(q, ignoreCase = true)
                while (i >= 0 && out.size < 5000) {
                    out.add(p.id to (i until i + q.length))
                    i = p.text.indexOf(q, i + q.length, ignoreCase = true)
                }
            }
            out
        }
    }
    val hitsByPara = remember(matches) { matches.groupBy({ it.first }, { it.second }) }
    fun itemOfHit(id: Long, at: Int): Int? {
        val f = flat ?: return null
        val start = f.indexOf[id] ?: return null
        // A paragraph shown in parts: the part holding the match.
        var best = start
        for (i in start until f.items.size) {
            val it = f.items[i] as? DocItem.Para ?: break
            if (it.para.id != id) break
            if (it.range == null || at in it.range) { best = i; break }
        }
        return best
    }
    fun scrollToItem(idx: Int) {
        scope.launch { listState.animateScrollToItem(idx, -with(density) { 160.dp.roundToPx() }) }
    }
    LaunchedEffect(matches) {
        currentMatch = 0
        matches.firstOrNull()?.let { (id, r) -> itemOfHit(id, r.first)?.let(::scrollToItem) }
    }
    fun goToMatch(delta: Int) {
        if (matches.isEmpty()) return
        currentMatch = ((currentMatch + delta) % matches.size + matches.size) % matches.size
        matches[currentMatch].let { (id, r) -> itemOfHit(id, r.first)?.let(::scrollToItem) }
    }

    val counts by produceState(0 to 0, doc) {
        val d = doc ?: return@produceState
        delay(300)
        value = withContext(Dispatchers.Default) { countWords(d.blocks) }
    }
    val currentPage by remember(flat) {
        derivedStateOf { flat?.items?.getOrNull(listState.firstVisibleItemIndex + 1)?.page ?: 0 }
    }

    // Picks up where you left off: the page last read, then remembers it (after Gander).
    var resumed by remember { mutableStateOf(false) }
    LaunchedEffect(flat) {
        val f = flat ?: return@LaunchedEffect
        if (!resumed) {
            resumed = true
            val p = viewModel.resumePage
            if (p in 1 until f.pages) listState.scrollToItem(f.pageStarts[p])
        }
    }
    LaunchedEffect(flat, meta.sourceUri) {
        val uri = meta.sourceUri ?: return@LaunchedEffect
        val pages = flat?.pages ?: return@LaunchedEffect
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        snapshotFlow { currentPage }.distinctUntilChanged().debounce(600).collect { p ->
            withContext(Dispatchers.IO) { runCatching { RecentFilesManager.updateProgress(context, uri, p, pages) } }
        }
    }

    // Pinch zoom keeps the point under the fingers in place.
    var lastZoom by remember { mutableFloatStateOf(zoom) }
    LaunchedEffect(zoom) {
        val r = zoom / lastZoom
        lastZoom = zoom
        if (r == 1f) return@LaunchedEffect
        withFrameNanos { }
        val fx = pinchFocus[0]
        val fy = pinchFocus[1]
        hScroll.scrollTo(((hScroll.value + fx) * r - fx).roundToInt().coerceAtLeast(0))
        listState.scrollBy((listState.firstVisibleItemScrollOffset + fy) * (r - 1f))
    }

    // Undo / redo replaced the document: the open field re-reads its paragraph.
    LaunchedEffect(viewModel.restoreEpoch) {
        val id = activeId ?: return@LaunchedEffect
        val p = viewModel.para(id)
        if (p == null) { activeId = null; return@LaunchedEffect }
        val s = activeValue.selection
        activeValue = TextFieldValue(ZWSP + p.text, TextRange(s.start.coerceIn(1, p.text.length + 1), s.end.coerceIn(1, p.text.length + 1)))
        pending = null
    }

    // The formatting bar pushes the page down; at the top of the document, show its first line.
    LaunchedEffect(editMode) {
        if (listState.firstVisibleItemIndex == 0) listState.scrollToItem(0)
    }

    // A paragraph about to take focus may be just off screen (Enter on the last visible line).
    LaunchedEffect(focusTarget) {
        val id = focusTarget ?: return@LaunchedEffect
        val idx = flat?.indexOf?.get(id) ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == idx } || visible.lastOrNull()?.index == idx) {
            listState.animateScrollToItem(idx, -with(density) { 220.dp.roundToPx() })
        }
    }

    fun activate(id: Long, caret: Int) {
        val p = viewModel.para(id) ?: return
        activeId = id
        activeValue = TextFieldValue(ZWSP + p.text, TextRange(1 + caret.coerceIn(0, p.text.length)))
        pending = null
        focusTarget = id
    }

    fun onChange(para: DocPara, v: TextFieldValue) {
        val cur = viewModel.para(para.id) ?: return
        var raw = v.text
        var sel = v.selection
        var composition = v.composition
        if (raw.isEmpty() || raw[0] != ZWSP) {
            if (raw == cur.text) {
                // Backspace at the very start: join this paragraph to the one above.
                val res = viewModel.mergeWithPrevious(cur.id)
                when {
                    res == null -> { activeId = cur.id; activeValue = TextFieldValue(ZWSP + cur.text, TextRange(1)) }
                    res.first < 0 -> { activeId = null; focusManager.clearFocus() }
                    else -> activate(res.first, res.second)
                }
                return
            }
            // A selection that included the marker was replaced: put the marker back.
            raw = ZWSP + raw
            sel = TextRange(sel.start + 1, sel.end + 1)
            composition = null
        }
        val newText = raw.substring(1)
        sel = TextRange(sel.start.coerceAtLeast(1), sel.end.coerceAtLeast(1))
        val old = cur.text
        var pre = 0
        val max = minOf(old.length, newText.length)
        while (pre < max && old[pre] == newText[pre]) pre++
        var suf = 0
        while (suf < max - pre && old[old.length - 1 - suf] == newText[newText.length - 1 - suf]) suf++
        val insEnd = newText.length - suf
        val typing = pending?.takeIf { it.paraId == cur.id && it.at == pre }?.fmt ?: DocxEdits.fmtAt(cur, pre)
        val nl = (pre until insEnd).firstOrNull { newText[it] == '\n' }
        if (nl != null) {
            // Enter (or pasted lines): each new line break typed starts a new paragraph.
            var id = cur.id
            var t = newText
            var k: Int = nl
            var end = insEnd
            while (true) {
                val joined = t.removeRange(k, k + 1)
                val newId = viewModel.split(id, joined, k, typing) ?: break
                id = newId
                t = joined.substring(k)
                end -= k + 1
                val next = (0 until end.coerceAtMost(t.length)).firstOrNull { t[it] == '\n' }
                if (next == null) break
                k = next
            }
            activate(id, end.coerceAtLeast(0))
            return
        }
        if (newText != old) {
            viewModel.typed(cur.id, newText, typing)
            pending = null
        } else if (pending != null && (pending?.paraId != cur.id || sel.start - 1 != pending?.at)) {
            pending = null
        }
        if (activeId != cur.id) viewModel.breakUndoGroup()
        activeId = cur.id
        activeValue = TextFieldValue(raw, sel, composition)
    }

    fun onFocused(para: DocPara) {
        focusedField = para.id
        if (activeId != para.id) {
            viewModel.breakUndoGroup()
            activeId = para.id
            activeValue = TextFieldValue(ZWSP + para.text, TextRange(1))
            pending = null
        }
    }

    fun exitEdit() {
        focusManager.clearFocus()
        activeId = null
        pending = null
        panel = DocPanel.NONE
        editMode = false
    }

    // ── formatting ─────────────────────────────────────────────────────────────

    val styles = pkg?.styles
    val activePara = activeId?.let { id -> doc?.let { findPara(it.blocks, id) } }?.takeIf { it.editable }
    val selModel = activePara?.let { p ->
        val s = activeValue.selection
        (s.min - 1).coerceIn(0, p.text.length) to (s.max - 1).coerceIn(0, p.text.length)
    }
    val curFmt: CharFmt? = if (activePara != null && selModel != null) {
        pending?.takeIf { it.paraId == activePara.id && it.at == selModel.first }?.fmt
            ?: if (selModel.first == selModel.second) DocxEdits.fmtAt(activePara, selModel.first)
            else activePara.fmts.getOrNull(selModel.first) ?: CharFmt.Plain
    } else null
    val curProps: RProps? = if (activePara != null && curFmt != null && styles != null) DocxEdits.effective(activePara, curFmt, styles) else null
    val curPara = if (activePara != null && propsLook != null) propsLook.para(activePara).props else null

    fun applyChar(transform: (DocPara, CharFmt) -> CharFmt) {
        val p = activePara ?: return
        val (s0, e0) = selModel ?: return
        var a = s0
        var b = e0
        if (a == b) {
            val w = wordAround(p.text, a)
            if (w != null) { a = w.first; b = w.second } else {
                val base = pending?.takeIf { it.paraId == p.id && it.at == a }?.fmt ?: DocxEdits.fmtAt(p, a)
                pending = Pending(p.id, a, transform(p, base))
                return
            }
        }
        val np = DocxEdits.format(p, a, b) { transform(p, it) }
        if (np !== p) viewModel.replace(np)
    }

    fun applyPara(f: (DocPara) -> DocPara) {
        val p = activePara ?: return
        viewModel.mapParas(listOf(p.id), f)
    }

    fun toggle(t: DocxEdits.Toggle, current: Boolean) {
        val s = styles ?: return
        applyChar { p, f -> DocxEdits.toggle(p, f, s, t, !current) }
    }

    // ── saving, sharing, printing ──────────────────────────────────────────────

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        if (uri != null) viewModel.save(context, uri) { res ->
            if (res is DocxEditorViewModel.SaveResult.Saved) {
                Toast.makeText(context, R.string.docx_saved, Toast.LENGTH_SHORT).show()
                if (exitAfterSave) onBack()
            } else Toast.makeText(context, R.string.docx_save_failed, Toast.LENGTH_SHORT).show()
        } else exitAfterSave = false
    }
    fun save(asCopy: Boolean) {
        if (asCopy) { saveAsLauncher.launch(viewModel.suggestedSaveAsName()); return }
        viewModel.save(context, null) { res ->
            when (res) {
                is DocxEditorViewModel.SaveResult.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    Toast.makeText(context, R.string.docx_saved, Toast.LENGTH_SHORT).show()
                    if (exitAfterSave) onBack()
                }
                is DocxEditorViewModel.SaveResult.NeedsSaveAs -> saveAsLauncher.launch(viewModel.suggestedSaveAsName())
                is DocxEditorViewModel.SaveResult.Failed -> Toast.makeText(context, R.string.docx_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // The exact page layout comes from the PDF path, which uses the Office engine (LibreOffice)
    // whenever it is installed.
    fun openAsPdf() {
        // Always this document's own pages as a PDF (see exportToPdf). Handing the untouched .docx
        // to the PDF route made that route convert it with the old web preview instead, which
        // opened the previous viewer's look rather than the page being read.
        viewModel.exportToPdf(context) { pdf ->
            if (pdf != null) onOpenPdf(pdf) else Toast.makeText(context, R.string.docx_read_failed, Toast.LENGTH_SHORT).show()
        }
    }
    fun print() {
        Toast.makeText(context, R.string.docx_preparing_print, Toast.LENGTH_SHORT).show()
        viewModel.exportToPdf(context) { pdf ->
            if (pdf == null || !PdfPrint.print(context, pdf, meta.fileName.substringBeforeLast('.')))
                Toast.makeText(context, R.string.docx_print_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBack() {
        when {
            showGoTo -> showGoTo = false
            panel != DocPanel.NONE -> panel = DocPanel.NONE
            showSearch -> { showSearch = false; searchQuery = "" }
            editMode -> exitEdit()
            viewModel.dirty -> confirmExit = true
            else -> onBack()
        }
    }
    BackHandler(enabled = showGoTo || panel != DocPanel.NONE || showSearch || editMode || viewModel.dirty) { requestBack() }

    val screenBackdrop = rememberScreenBackdrop(backdrop)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val ribbonHeight = 52.dp
    // Clear glass, exactly like the header and reading circles beside it and the PDF viewer's page
    // pill: no frost, so the title pill, the ribbon's style/font/size controls and the page capsule
    // refract the page the way the buttons do, and every Liquid Glass setting reads the same on all
    // of them. A frost here made them grey slabs over pictures. The ink follows the paper (see above).
    val chipTint = Color.Transparent

    Box(
        Modifier
            .fillMaxSize()
            // While editing, Enter belongs to the paragraph being typed in. A hardware Enter (a
            // keyboard, a Chromebook) that arrives while focus sits elsewhere would otherwise "click"
            // whichever glass button holds focus: Save, Done, Share.
            .onPreviewKeyEvent { e ->
                editMode && focusedField == null && (e.key == Key.Enter || e.key == Key.NumPadEnter)
            }
    ) {
        GlassScreenScaffold(
            backdrop = backdrop,
            screenBackdrop = screenBackdrop,
            contentHorizontalPadding = 0.dp,
            headerHorizontalPadding = 12.dp,
            header = { headerBackdrop ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassBackButton(
                        onBack = { requestBack() },
                        backdrop = headerBackdrop,
                        foreground = text,
                        onLongPressBack = if (viewModel.dirty || editMode) ({ requestBack() }) else LocalBackToLibraryAction.current,
                        longPressLabel = if (viewModel.dirty || editMode) stringResource(R.string.back) else null
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        GlassTitlePill(
                            text = (if (viewModel.dirty) "• " else "") + meta.fileName.ifBlank { stringResource(R.string.viewer_title) },
                            backdrop = headerBackdrop,
                            surfaceColor = chipTint,
                            contentColor = text
                        )
                    }
                    if (viewModel.dirty && editable) {
                        val savePulse = if (meta.saving) {
                            val t = rememberInfiniteTransition(label = "docSavePulse")
                            t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "docSavePulseA").value
                        } else 1f
                        LiquidIconButton(onClick = { if (!meta.saving) save(false) }, backdrop = headerBackdrop) {
                            Icon(Icons.Rounded.Check, stringResource(R.string.sheet_save), Modifier.size(22.dp).graphicsLayer { alpha = savePulse }, accent)
                        }
                    }
                    if (!editMode) {
                        LiquidIconButton(onClick = { showSearch = true }, backdrop = headerBackdrop) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.viewer_find), Modifier.size(20.dp), text)
                        }
                        LiquidIconButton(onClick = onTogglePaper, backdrop = headerBackdrop) {
                            Icon(
                                if (paperDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                stringResource(if (paperDark) R.string.viewer_page_colors_original else R.string.viewer_page_dark_mode),
                                Modifier.size(19.dp), text
                            )
                        }
                    }
                    if (editable && doc != null) {
                        LiquidIconButton(
                            onClick = { if (editMode) exitEdit() else { editMode = true; showSearch = false; searchQuery = "" } },
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
                meta.isLoading -> Box(Modifier.fillMaxSize().background(desk), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.5.dp)
                }
                pkg == null || doc == null || flat == null -> Column(
                    Modifier.fillMaxSize().background(desk).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicText(stringResource(R.string.docx_read_failed), style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center))
                    if (meta.sourceUri != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).liquidRowClick(corner = 20.dp) { openAsPdf() }
                                .background(accent.copy(0.14f)).padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp), accent)
                            BasicText(stringResource(R.string.docx_open_as_pdf), style = TextStyle(accent, 15.sp, FontWeight.SemiBold))
                        }
                    }
                }
                else -> BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(desk)
                        // While editing, the page ends above the keyboard and the tool dock, so the
                        // paragraph being typed into is never under either.
                        .then(if (editMode) Modifier.imePadding().padding(bottom = 76.dp) else Modifier)
                        // Two fingers zoom the page (around the point between them); one scrolls.
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val z = event.calculateZoom()
                                        if (z != 1f) {
                                            val c = event.calculateCentroid(useCurrent = true)
                                            pinchFocus[0] = c.x
                                            pinchFocus[1] = c.y
                                            zoomTarget = (zoomTarget * z).coerceIn(1f, 5f)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val page = pkg.page
                    val pageWidthPt = (page.width / 20f).coerceAtLeast(72f)
                    val fit = ((maxWidth - DeskGutter * 2).value / pageWidthPt).coerceAtLeast(0.1f)
                    val dpPerPt = fit * zoom
                    val look = remember(pkg, paperDark, dpPerPt, density.fontScale) {
                        DocxLook(pkg, paperDark, dpPerPt, density.fontScale, carlito)
                    }
                    fun tw(v: Int): Dp = (v.coerceAtLeast(0) / 20f * dpPerPt).dp
                    val pageW = (pageWidthPt * dpPerPt).dp
                    val marginL = tw(page.left)
                    val marginR = tw(page.right)
                    val marginT = tw(page.top)
                    val marginB = tw(page.bottom)
                    val env = DocEnv(
                        pkg = pkg,
                        editMode = editMode,
                        activeId = activeId,
                        activeValue = activeValue,
                        focusTarget = focusTarget,
                        labels = labels,
                        hits = hitsByPara,
                        currentHit = matches.getOrNull(currentMatch),
                        accent = accent,
                        textWidth = pageW - marginL - marginR,
                        onChange = ::onChange,
                        onFocused = ::onFocused,
                        onBlurred = { p -> if (focusedField == p.id) focusedField = null },
                        onFocusConsumed = { focusTarget = null },
                        onReadOnlyTap = {
                            if (!readOnlyHinted) {
                                readOnlyHinted = true
                                Toast.makeText(context, R.string.docx_readonly_part, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val topRound = RoundedCornerShape(topStart = PaperCorner, topEnd = PaperCorner)
                    val bottomRound = RoundedCornerShape(bottomStart = PaperCorner, bottomEnd = PaperCorner)
                    // One list in one place for both modes: swapping it in and out of the selection
                    // container left the shared LazyListState holding a prefetch handle of the
                    // disposed list, which crashed the next measure.
                    SelectionContainer {
                        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = DeskGutter, end = DeskGutter,
                                    top = contentPadding.calculateTopPadding() + if (editMode) ribbonHeight + 8.dp else 0.dp,
                                    bottom = if (editMode) 16.dp else navBottom + 76.dp
                                ),
                                modifier = Modifier.width(pageW + DeskGutter * 2).fillMaxHeight()
                            ) {
                                items(flat.items, key = { it.key }, contentType = { it::class }) { item ->
                                    when (item) {
                                        is DocItem.Top -> Box(Modifier.width(pageW).height(marginT).background(look.paper, topRound))
                                        is DocItem.Bottom -> Box(Modifier.width(pageW).height(marginB).background(look.paper, bottomRound))
                                        is DocItem.Gap -> Column(Modifier.width(pageW)) {
                                            Box(Modifier.fillMaxWidth().height(marginB).background(look.paper, bottomRound))
                                            Spacer(Modifier.height(PageGap))
                                            Box(Modifier.fillMaxWidth().height(marginT).background(look.paper, topRound))
                                        }
                                        is DocItem.Para -> Box(Modifier.width(pageW).background(look.paper).padding(start = marginL, end = marginR)) {
                                            ParagraphBlock(
                                                item.para, look, env, item.readOnly,
                                                spaceTop = item.spaceTop && item.first, spaceBottom = item.spaceBottom && item.last,
                                                range = item.range
                                            )
                                        }
                                        is DocItem.TRow -> Box(Modifier.width(pageW).background(look.paper).padding(start = marginL, end = marginR)) {
                                            TableRowBlock(item.table, item.row, item.rowIndex, look, env, item.readOnly, env.textWidth)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── edit-mode chrome: formatting bar on top, tool dock above the keyboard ──
        if (pkg != null && styles != null) {
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(GlassMotion.fade()),
                exit = fadeOut(GlassMotion.fade()),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = statusTop + 48.dp + 10.dp)
            ) {
                FormatRibbon(
                    backdrop = screenBackdrop.glass,
                    enabled = activePara != null,
                    styleName = curPara?.styleId?.let { styles.byId[it]?.name } ?: styles.defaultParagraphId?.let { styles.byId[it]?.name } ?: "Normal",
                    fontName = curProps?.let { it.font ?: pkg.theme.resolve(it.fontTheme) } ?: pkg.theme.minor ?: "Calibri",
                    sizeHalfPts = curProps?.size ?: 22,
                    text = text,
                    accent = accent,
                    tint = chipTint,
                    onStyle = { name ->
                        val id = if (name.equals("Normal", true)) styles.defaultParagraphId else DocxEdits.ensureStyle(styles, name)
                        applyPara { DocxEdits.withStyle(it, id, styles) }
                    },
                    onFont = { f -> applyChar { _, fmt -> DocxEdits.withFont(fmt, f) } },
                    onSize = { hp -> applyChar { _, fmt -> DocxEdits.withSize(fmt, hp) } },
                    modifier = Modifier.height(ribbonHeight)
                )
            }
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(GlassMotion.fade()) + slideInVertically { it / 3 },
                exit = fadeOut(GlassMotion.fade()) + slideOutVertically { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            ) {
                val bold = curProps?.bold == true
                val italic = curProps?.italic == true
                val underline = curProps?.underline == true
                val strike = curProps?.strike == true
                val sup = curProps?.vertAlign == "superscript"
                val align = curPara?.align ?: "left"
                val numId = curPara?.numId?.takeIf { it != 0 }
                val bullet = numId != null && pkg.numbering.isBullet(numId)
                val has = activePara != null
                GlassToolbar(backdrop = screenBackdrop.glass, modifier = Modifier.fillMaxWidth()) {
                    GlassToolButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.sheet_undo), false, { viewModel.undo() }, enabled = viewModel.canUndo, accent = accent)
                    GlassToolButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.sheet_redo), false, { viewModel.redo() }, enabled = viewModel.canRedo, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatBold, stringResource(R.string.sheet_bold), bold, { toggle(DocxEdits.Toggle.BOLD, bold) }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatItalic, stringResource(R.string.sheet_italic), italic, { toggle(DocxEdits.Toggle.ITALIC, italic) }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatUnderlined, stringResource(R.string.sheet_underline), underline, { toggle(DocxEdits.Toggle.UNDERLINE, underline) }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatStrikethrough, stringResource(R.string.sheet_strike), strike, { toggle(DocxEdits.Toggle.STRIKE, strike) }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatColorText, stringResource(R.string.sheet_text_color), panel == DocPanel.TEXT_COLOR, { panel = if (panel == DocPanel.TEXT_COLOR) DocPanel.NONE else DocPanel.TEXT_COLOR }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.BorderColor, stringResource(R.string.docx_highlight), panel == DocPanel.HIGHLIGHT, { panel = if (panel == DocPanel.HIGHLIGHT) DocPanel.NONE else DocPanel.HIGHLIGHT }, enabled = has, accent = accent)
                    GlassToolButton(
                        when (align) {
                            "center" -> Icons.Rounded.FormatAlignCenter
                            "right", "end" -> Icons.Rounded.FormatAlignRight
                            "both", "distribute" -> Icons.Rounded.FormatAlignJustify
                            else -> Icons.Rounded.FormatAlignLeft
                        },
                        stringResource(R.string.docx_align), false,
                        {
                            val next = when (align) { "center" -> "right"; "right", "end" -> "both"; "both", "distribute" -> "left"; else -> "center" }
                            applyPara { DocxEdits.withAlign(it, next) }
                        },
                        enabled = has, accent = accent
                    )
                    GlassToolButton(Icons.Rounded.FormatListBulleted, stringResource(R.string.docx_bullets), bullet, {
                        applyPara { p -> DocxEdits.withList(p, if (bullet) null else pkg.numbering.ensureList(true), styles) }
                    }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatListNumbered, stringResource(R.string.docx_numbering), numId != null && !bullet, {
                        applyPara { p -> DocxEdits.withList(p, if (numId != null && !bullet) null else pkg.numbering.ensureList(false), styles) }
                    }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatIndentDecrease, stringResource(R.string.docx_indent_less), false, {
                        propsLook?.let { l -> applyPara { p -> indent(p, -1, l, styles) } }
                    }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatIndentIncrease, stringResource(R.string.docx_indent_more), false, {
                        propsLook?.let { l -> applyPara { p -> indent(p, 1, l, styles) } }
                    }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.Superscript, stringResource(R.string.docx_superscript), sup, { toggle(DocxEdits.Toggle.SUPERSCRIPT, sup) }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.FormatClear, stringResource(R.string.docx_clear), false, { applyChar { _, f -> DocxEdits.cleared(f) } }, enabled = has, accent = accent)
                    GlassToolButton(Icons.Rounded.SaveAs, stringResource(R.string.sheet_save_as), false, { save(true) }, accent = accent)
                }
            }
        }

        // ── reading chrome: page counter, print, PDF layout and share ──
        if (doc != null && flat != null && !editMode && !showSearch) {
            Row(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 10.dp)
                    .height(44.dp)
                    .viewerGlass(screenBackdrop.glass, chipTint, shape = { Capsule })
                    .clip(Capsule)
                    .liquidRowClick(corner = 22.dp) { showGoTo = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    stringResource(R.string.docx_page_of, currentPage + 1, flat.pages) +
                        if (zoom != 1f) "  ·  ${(zoom * 100).roundToInt()}%" else "",
                    style = TextStyle(text, 12.sp, FontWeight.SemiBold),
                    maxLines = 1
                )
            }
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidIconButton(onClick = { print() }, backdrop = screenBackdrop.glass, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Print, stringResource(R.string.docx_print), Modifier.size(20.dp), text)
                }
                LiquidIconButton(onClick = { openAsPdf() }, backdrop = screenBackdrop.glass, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, stringResource(R.string.docx_open_as_pdf), Modifier.size(20.dp), text)
                }
                LiquidIconButton(
                    onClick = { viewModel.currentFile(context) { u -> u?.let { shareFile(context, it) } } },
                    backdrop = screenBackdrop.glass,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.IosShare, stringResource(R.string.viewer_share_document), Modifier.size(20.dp), text)
                }
            }
        }

        // Find: the same glass bar as the PDF viewer's, refracting the live page behind it.
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
                backdrop = screenBackdrop.content,
                uiSensor = uiSensor,
                fg = text,
                fgSoft = sub,
                surface = Color.Transparent,
                onQueryChange = { searchQuery = it },
                onPrevMatch = { goToMatch(-1) },
                onNextMatch = { goToMatch(1) },
                onClose = { showSearch = false; searchQuery = "" }
            )
        }

        // Text / highlight colour.
        AnimatedVisibility(
            visible = panel != DocPanel.NONE,
            enter = fadeIn(GlassMotion.fade()) + scaleIn(GlassMotion.pop(), initialScale = 0.94f),
            exit = fadeOut(GlassMotion.fade()) + scaleOut(targetScale = 0.96f),
            modifier = Modifier.fillMaxSize()
        ) {
            var lastPanel by remember { mutableStateOf(DocPanel.TEXT_COLOR) }
            if (panel != DocPanel.NONE) lastPanel = panel
            val highlight = lastPanel == DocPanel.HIGHLIGHT
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { panel = DocPanel.NONE }) {
                val current = if (highlight) curProps?.highlight else curProps?.color
                GlassColorPicker(
                    color = current?.takeIf { it != 0 }?.let { Color(it) } ?: if (highlight) Color.Transparent else Color.Black,
                    onColorChange = { c ->
                        val argb = c.toArgb()
                        applyChar { _, f -> if (highlight) DocxEdits.withHighlight(f, argb) else DocxEdits.withColor(f, argb) }
                    },
                    backdrop = screenBackdrop.glass,
                    themeColors = if (highlight) HighlightColors.values.take(10).map { Color(it) } else OfficeThemeColors,
                    standardColors = if (highlight) HighlightColors.values.drop(6).take(10).map { Color(it) } else OfficeStandardColors,
                    onClear = {
                        applyChar { _, f -> if (highlight) DocxEdits.withHighlight(f, null) else DocxEdits.withColor(f, null) }
                        panel = DocPanel.NONE
                    },
                    clearLabel = stringResource(if (highlight) R.string.docx_no_highlight else R.string.sheet_automatic),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
                        .widthIn(max = 420.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                )
            }
        }

        // Go to page, the page counter's dialog.
        if (flat != null) {
            var pageInput by remember(showGoTo) { mutableStateOf("") }
            fun go() {
                val n = pageInput.trim().toIntOrNull() ?: return
                val p = (n - 1).coerceIn(0, flat.pages - 1)
                showGoTo = false
                scope.launch { listState.scrollToItem(flat.pageStarts[p]) }
            }
            GlassDialog(
                visible = showGoTo,
                onDismiss = { showGoTo = false },
                backdrop = screenBackdrop.glass,
                title = stringResource(R.string.docx_go_to_page),
                actions = {
                    GlassDialogAction(stringResource(R.string.docx_fit_width), onClick = { zoomTarget = 1f; showGoTo = false })
                    GlassDialogAction(stringResource(R.string.docx_go), onClick = { go() }, primary = true, enabled = pageInput.toIntOrNull() != null)
                }
            ) {
                val nf = remember { java.text.NumberFormat.getIntegerInstance() }
                BasicText(
                    stringResource(R.string.docx_status, nf.format(counts.first), nf.format(counts.second)),
                    style = TextStyle(sub, 13.sp)
                )
                Spacer(Modifier.height(12.dp))
                val fieldFocus = remember { FocusRequester() }
                LaunchedEffect(showGoTo) { if (showGoTo) runCatching { delay(120); fieldFocus.requestFocus() } }
                BasicTextField(
                    value = pageInput,
                    onValueChange = { v -> pageInput = v.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    textStyle = TextStyle(text, 17.sp, FontWeight.SemiBold),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(text.copy(0.07f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .focusRequester(fieldFocus),
                    decorationBox = { inner ->
                        if (pageInput.isEmpty()) BasicText(
                            stringResource(R.string.docx_page_hint, flat.pages),
                            style = TextStyle(sub, 17.sp)
                        )
                        inner()
                    }
                )
            }
        }

        UnsavedChangesDialog(
            visible = confirmExit,
            onDiscard = { confirmExit = false; onBack() },
            onCancel = { confirmExit = false },
            onSave = { confirmExit = false; exitAfterSave = true; save(false) },
            backdrop = screenBackdrop.glass,
            title = stringResource(R.string.unsaved_title),
            body = stringResource(R.string.docx_unsaved_body),
            saveLabel = stringResource(R.string.sheet_save),
            accent = accent
        )
    }
}

private fun indent(p: DocPara, dir: Int, look: DocxLook, styles: DocxStyles): DocPara {
    val pp = look.para(p).props
    val numId = pp.numId?.takeIf { it != 0 }
    return if (numId != null) DocxEdits.withList(p, numId, styles, ((pp.ilvl ?: 0) + dir).coerceIn(0, 8))
    else DocxEdits.withLeftIndent(p, (Props.p(p.pPr).left ?: 0) + 720 * dir)
}

/** The word the caret is strictly inside (Word formats the whole word then), or null. */
private fun wordAround(text: String, at: Int): Pair<Int, Int>? {
    fun w(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'
    if (at <= 0 || at >= text.length || !w(text[at - 1]) || !w(text[at])) return null
    var a = at
    var b = at
    while (a > 0 && w(text[a - 1])) a--
    while (b < text.length && w(text[b])) b++
    return a to b
}

// ── formatting bar ─────────────────────────────────────────────────────────────

private val GalleryStyles = listOf("Normal", "Title", "Subtitle", "heading 1", "heading 2", "heading 3", "Quote")
private val Fonts = listOf("Calibri", "Aptos", "Arial", "Times New Roman", "Georgia", "Cambria", "Verdana", "Courier New")
private val SizesHalfPts = listOf(16, 18, 20, 21, 22, 24, 28, 32, 36, 40, 44, 48, 52, 56, 72, 96, 144)

@Composable
private fun styleLabel(name: String): String = when (name.lowercase()) {
    "normal" -> stringResource(R.string.docx_style_normal)
    "title" -> stringResource(R.string.docx_style_title)
    "subtitle" -> stringResource(R.string.docx_style_subtitle)
    "heading 1" -> stringResource(R.string.docx_style_h1)
    "heading 2" -> stringResource(R.string.docx_style_h2)
    "heading 3" -> stringResource(R.string.docx_style_h3)
    "quote" -> stringResource(R.string.docx_style_quote)
    else -> name.replaceFirstChar { it.uppercase() }
}

/** LibreOffice Writer's "Formatting" bar, in glass: paragraph style · font · size. */
@Composable
private fun FormatRibbon(
    backdrop: Backdrop,
    enabled: Boolean,
    styleName: String,
    fontName: String,
    sizeHalfPts: Int,
    text: Color,
    accent: Color,
    tint: Color,
    onStyle: (String) -> Unit,
    onFont: (String) -> Unit,
    onSize: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val styleOptions = (if (GalleryStyles.none { it.equals(styleName, true) }) listOf(styleName) else emptyList()) + GalleryStyles
    val fontOptions = (if (Fonts.none { it.equals(fontName, true) }) listOf(fontName) else emptyList()) + Fonts
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LiquidGlassDropdown(
            options = styleOptions.map { GlassDropdownOption(it.lowercase(), styleLabel(it)) },
            selected = styleName.lowercase(),
            onSelect = { v -> if (enabled) onStyle(styleOptions.first { it.lowercase() == v }) },
            backdrop = backdrop,
            leadingIcon = Icons.Rounded.TextFields,
            accent = accent,
            triggerSurface = tint,
            contentColor = text,
            modifier = Modifier.width(150.dp)
        )
        LiquidGlassDropdown(
            options = fontOptions.map { GlassDropdownOption(it, it) },
            selected = fontOptions.firstOrNull { it.equals(fontName, true) } ?: fontName,
            onSelect = { if (enabled) onFont(it) },
            backdrop = backdrop,
            leadingIcon = Icons.Rounded.FontDownload,
            accent = accent,
            triggerSurface = tint,
            contentColor = text,
            modifier = Modifier.width(158.dp)
        )
        Row(
            Modifier.height(44.dp).viewerGlass(backdrop, tint, shape = { Capsule }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val down = SizesHalfPts.lastOrNull { it < sizeHalfPts } ?: SizesHalfPts.first()
            val up = SizesHalfPts.firstOrNull { it > sizeHalfPts } ?: SizesHalfPts.last()
            Box(Modifier.size(44.dp).clip(Capsule).liquidRowClick(corner = 22.dp, enabled = enabled) { onSize(down) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.docx_size_down), Modifier.size(18.dp), text)
            }
            val pt = sizeHalfPts / 2f
            BasicText(
                if (pt % 1f == 0f) pt.toInt().toString() else pt.toString(),
                Modifier.widthIn(min = 26.dp),
                style = TextStyle(text, 15.sp, FontWeight.SemiBold, textAlign = TextAlign.Center)
            )
            Box(Modifier.size(44.dp).clip(Capsule).liquidRowClick(corner = 22.dp, enabled = enabled) { onSize(up) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, stringResource(R.string.docx_size_up), Modifier.size(18.dp), text)
            }
        }
    }
}

// ── paragraphs ────────────────────────────────────────────────────────────────

/** Shows the paragraph's runs in the field; the leading marker and objects stay invisible. */
private class DocTransformation(private val annotated: AnnotatedString) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val out = if (t.length == annotated.length + 1) AnnotatedString(ZWSP.toString()) + annotated
        else AnnotatedString(t.replace(OBJ_CHAR, ZWSP))
        return TransformedText(out, OffsetMapping.Identity)
    }
}

@Composable
private fun ParagraphBlock(
    para: DocPara,
    look: DocxLook,
    env: DocEnv,
    readOnly: Boolean,
    spaceTop: Boolean = true,
    spaceBottom: Boolean = true,
    cell: RProps? = null,
    /** The part of the paragraph shown here, when Word broke it across pages (reading only). */
    range: IntRange? = null,
    maxWidth: Dp = env.textWidth
) {
    val pl = remember(para.pPr, look, cell) { look.para(para, cell) }
    val editable = env.editMode && !readOnly && para.editable && range == null
    val hits = env.hits[para.id].orEmpty()
    val current = env.currentHit?.takeIf { it.first == para.id }?.second
    val full = remember(para, look, cell, hits, current, editable) {
        look.annotate(para, pl, cell, hits, current, withLinks = !editable)
    }
    val annotated = remember(full, range) { if (range == null) full else full.subSequence(range.first, (range.last + 1).coerceAtMost(full.length)) }
    val objects = remember(para, range) {
        para.fmts.withIndex().filter { (i, f) -> f.obj != null && (range == null || i in range) }.map { DocxObjects.info(it.value.obj!!) }
    }
    val pictures = objects.filter { it.imageRid != null }
    val boxes = objects.filter { it.textBox.isNotEmpty() }
    val blank = remember(annotated) { annotated.text.all { it == ZWSP || it.isWhitespace() } }
    val first = range == null || range.first == 0
    val label = if (first) env.labels[para.id] else null
    val dir = if (pl.rtl) LayoutDirection.Rtl else LocalLayoutDirection.current

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (spaceTop) pl.before else 0.dp, bottom = if (spaceBottom) pl.after else 0.dp)
                .then(if (pl.shading != null) Modifier.background(pl.shading) else Modifier)
                .padding(start = pl.padStart, end = pl.padEnd)
        ) {
            val paraAlign = when (pl.style.textAlign) {
                TextAlign.Center -> Alignment.CenterHorizontally
                TextAlign.End, TextAlign.Right -> Alignment.End
                else -> Alignment.Start
            }
            for (pic in pictures) {
                val align = when (pic.hAlign) {
                    "center" -> Alignment.CenterHorizontally
                    "right", "outside" -> Alignment.End
                    "left", "inside" -> Alignment.Start
                    else -> paraAlign
                }
                DocPicture(pic, env.pkg, look, maxWidth - pl.padStart - pl.padEnd, align)
            }
            for (box in boxes) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .border(0.5.dp, look.ink.copy(0.22f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    for (b in box.textBox) StaticBlock(b, look, env)
                }
            }
            if (!(blank && (pictures.isNotEmpty() || boxes.isNotEmpty()) && !editable)) {
                Row(Modifier.fillMaxWidth()) {
                    if (label != null) {
                        BasicText(
                            label,
                            Modifier.width(pl.hanging).alignByBaseline(),
                            style = pl.style.copy(textAlign = TextAlign.Start, textIndent = null),
                            maxLines = 1
                        )
                    }
                    Box(Modifier.weight(1f).alignByBaseline()) {
                        if (editable) ParaField(para, pl, full, env)
                        // Full width, like the edit field: centred and right-aligned titles were
                        // drawn at the left while reading because the text only spanned itself.
                        else BasicText(
                            annotated,
                            style = if (first) pl.style else pl.style.copy(textIndent = null),
                            modifier = Modifier.fillMaxWidth().then(
                                if (env.editMode && !readOnly) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { env.onReadOnlyTap() } else Modifier
                            )
                        )
                    }
                }
            }
            if (pl.borderBottom && (range == null || range.last >= para.text.length - 1)) {
                Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(1.dp).background(look.ink.copy(0.55f)))
            }
        }
    }
}

@Composable
private fun ParaField(para: DocPara, pl: ParaLook, annotated: AnnotatedString, env: DocEnv) {
    val active = env.activeId == para.id
    val value = if (active) {
        val v = env.activeValue
        if (v.text.length == para.text.length + 1 && v.text.endsWith(para.text)) v
        else TextFieldValue(ZWSP + para.text, TextRange(v.selection.start.coerceIn(1, para.text.length + 1), v.selection.end.coerceIn(1, para.text.length + 1)))
    } else TextFieldValue(ZWSP + para.text, TextRange(1))
    val focus = remember { FocusRequester() }
    LaunchedEffect(env.focusTarget) {
        if (env.focusTarget == para.id) {
            runCatching { focus.requestFocus() }
            env.onFocusConsumed()
        }
    }
    val transformation = remember(annotated) { DocTransformation(annotated) }
    BasicTextField(
        value = value,
        onValueChange = { env.onChange(para, it) },
        textStyle = pl.style,
        cursorBrush = SolidColor(env.accent),
        visualTransformation = transformation,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) env.onFocused(para) else env.onBlurred(para) }
    )
}

/** Content shown but not edited: text boxes, content controls, nested previews. */
@Composable
private fun StaticBlock(block: DocBlock, look: DocxLook, env: DocEnv) {
    when (block) {
        is DocPara -> ParagraphBlock(block, look, env, readOnly = true)
        is DocTable -> Column(Modifier.fillMaxWidth()) {
            for ((i, r) in block.rows.withIndex()) TableRowBlock(block, r, i, look, env, readOnly = true, availWidth = env.textWidth)
        }
        is DocRaw -> for (b in block.preview) StaticBlock(b, look, env)
    }
}

// ── pictures ──────────────────────────────────────────────────────────────────

internal class DecodedPicture(val image: ImageBitmap, val paperLike: Boolean)

internal object DocImages {
    private val cache = object : LruCache<String, Pair<Bitmap, Boolean>>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Pair<Bitmap, Boolean>) = value.first.byteCount
    }

    fun decode(key: String, bytes: ByteArray, targetW: Int): DecodedPicture? {
        cache.get(key)?.let { return DecodedPicture(it.first.asImageBitmap(), it.second) }
        val bmp = decodeScaled(bytes, targetW.coerceAtLeast(1))
        if (bmp == null) {
            android.util.Log.w("DocxImages", "Couldn't decode picture $key (${bytes.size} bytes)")
            return null
        }
        // A scan or a chart on white turns over with dark paper; a photograph never does.
        val paper = runCatching { NightPaper.looksLikePaper(bmp) }.getOrDefault(false)
        cache.put(key, bmp to paper)
        return DecodedPicture(bmp.asImageBitmap(), paper)
    }

    /**
     * Big camera photos (a 3800 px progressive JPEG is common in Word files) decode through
     * ImageDecoder at the size they're shown, in software so the night probe can read pixels;
     * older Android, or a format ImageDecoder refuses, falls back to a sampled BitmapFactory.
     */
    private fun decodeScaled(bytes: ByteArray, targetW: Int): Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val src = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                return android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    if (w > targetW) {
                        val h = (info.size.height.toLong() * targetW / w).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, h)
                    }
                }
            }.onFailure { android.util.Log.w("DocxImages", "ImageDecoder failed: $it") }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
private fun DocPicture(info: ObjInfo, pkg: DocxPackage, look: DocxLook, maxWidth: Dp, align: Alignment.Horizontal) {
    val rid = info.imageRid ?: return
    val bytes = remember(pkg, rid) { pkg.image(rid) }
    val want = if (info.widthPt > 0f) (info.widthPt * look.scale).dp else maxWidth
    val w = want.coerceAtMost(maxWidth.coerceAtLeast(24.dp)).coerceAtLeast(12.dp)
    val px = with(LocalDensity.current) { w.roundToPx() }
    val bucket = (px / 128 + 1) * 128
    val pic by produceState<DecodedPicture?>(null, bytes, bucket) {
        val b = bytes ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { DocImages.decode("$rid@${System.identityHashCode(pkg)}@$bucket", b, bucket) }
                .onFailure { android.util.Log.w("DocxImages", "Picture $rid failed", it) }
                .getOrNull()
        }
    }
    val ratio = if (info.widthPt > 0f && info.heightPt > 0f) info.heightPt / info.widthPt
    else pic?.image?.let { it.height.toFloat() / it.width.coerceAtLeast(1) } ?: 0.6f
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = align) {
        Box(
            Modifier.width(w).aspectRatio((1f / ratio.coerceIn(0.05f, 20f)))
                .background(if (pic == null) look.ink.copy(0.06f) else Color.Transparent)
        ) {
            pic?.let {
                Image(
                    it.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    colorFilter = if (look.dark && it.paperLike) NightPaper.filter else null
                )
            }
        }
    }
}

// ── tables ────────────────────────────────────────────────────────────────────

internal class TableLook(
    val fractions: FloatArray,
    val width: Dp,
    val indent: Dp,
    val jc: String?,
    val borders: Boolean,
    val headerFill: Int?,
    val headerR: RProps?,
    val bandFill: Int?,
    val firstRow: Boolean,
    val bands: Boolean,
    val scale: Float
)

internal class CellProps(val span: Int, val vContinue: Boolean, val fill: Int?, val vAlign: String?)

private val cellPropsCache = HashMap<String, CellProps>()
private val tableLookCache = java.util.WeakHashMap<DocTable, Pair<Float, TableLook>>()

internal fun cellProps(tcPr: String): CellProps = synchronized(cellPropsCache) {
    cellPropsCache.getOrPut(tcPr) {
        val root = if (tcPr.isEmpty()) null else XmlScan.scan(tcPr).children.firstOrNull()
        fun child(n: String): XNode? = root?.child(n)
        val vm = child("vMerge")
        CellProps(
            span = child("gridSpan")?.attr(tcPr, "val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            vContinue = vm != null && (vm.attr(tcPr, "val") ?: "continue") == "continue",
            fill = Props.hex(child("shd")?.attr(tcPr, "fill")),
            vAlign = child("vAlign")?.attr(tcPr, "val")
        )
    }
}

/**
 * A table at its real size on the page: Word's column grid, alignment and indent. Only a table
 * wider than the page's text area (plus a little of the margin, which Word allows) is shrunk.
 */
internal fun tableLookOf(t: DocTable, pkg: DocxPackage, look: DocxLook, availWidth: Dp): TableLook {
    val cacheKey = look.scale * 100_000f + availWidth.value
    synchronized(tableLookCache) { tableLookCache[t]?.let { if (it.first == cacheKey) return it.second } }
    val tblPr = if (t.tblPr.isEmpty()) null else XmlScan.scan(t.tblPr).children.firstOrNull()
    val styleId = tblPr?.child("tblStyle")?.attr(t.tblPr, "val")
    val chain = pkg.styles.chain(styleId)
    fun bordersIn(xml: String): Boolean? {
        val n = (if (xml.isEmpty()) null else XmlScan.scan(xml).children.firstOrNull())?.child("tblBorders") ?: return null
        return n.children.any { b -> (b.attr(xml, "val") ?: "none").let { it != "none" && it != "nil" } }
    }
    var borders = bordersIn(t.tblPr)
    if (borders == null) for (s in chain.asReversed()) { borders = bordersIn(s.tblPr); if (borders != null) break }
    val lookEl = tblPr?.child("tblLook")
    val lookVal = lookEl?.attr(t.tblPr, "val")?.toIntOrNull(16) ?: 0x04A0
    val firstRow = lookEl?.attr(t.tblPr, "firstRow")?.let { it == "1" || it == "true" } ?: (lookVal and 0x20 != 0)
    val noHBand = lookEl?.attr(t.tblPr, "noHBand")?.let { it == "1" || it == "true" } ?: (lookVal and 0x200 != 0)
    var headFill: Int? = null
    var headR: RProps? = null
    var bandFill: Int? = null
    for (s in chain) {
        s.conditional["firstRow"]?.let { (tc, r) ->
            cellProps(tc).fill?.let { headFill = it }
            if (r.isNotEmpty()) headR = Props.r(r).over(headR ?: RProps())
        }
        s.conditional["band1Horz"]?.let { (tc, _) -> cellProps(tc).fill?.let { bandFill = it } }
    }
    val cols = if (t.grid.isNotEmpty() && t.grid.sum() > 0) t.grid else {
        val n = t.rows.maxOfOrNull { r -> r.cells.sumOf { cellProps(it.tcPr).span } } ?: 1
        List(n.coerceAtLeast(1)) { 1 }
    }
    val total = cols.sum().toFloat().coerceAtLeast(1f)
    val natural = if (t.grid.isNotEmpty()) (total / 20f * look.scale).dp else availWidth
    val room = availWidth * 1.12f
    val scale = if (natural > room) (room.value / natural.value).coerceIn(0.45f, 1f) else 1f
    val jc = tblPr?.child("jc")?.attr(t.tblPr, "val")
    val ind = tblPr?.child("tblInd")?.let { n ->
        if (n.attr(t.tblPr, "type") == "dxa" || n.attr(t.tblPr, "type") == null) (n.attr(t.tblPr, "w")?.toFloatOrNull() ?: 0f) / 20f * look.scale else 0f
    } ?: 0f
    val tl = TableLook(
        fractions = FloatArray(cols.size) { cols[it] / total },
        width = natural * scale,
        indent = ind.dp,
        jc = jc,
        borders = borders == true,
        headerFill = headFill,
        headerR = headR,
        bandFill = bandFill,
        firstRow = firstRow,
        bands = !noHBand,
        scale = (scale * 20f).roundToInt() / 20f
    )
    synchronized(tableLookCache) { tableLookCache[t] = cacheKey to tl }
    return tl
}

@Composable
private fun TableRowBlock(table: DocTable, row: DocRow, rowIndex: Int, look: DocxLook, env: DocEnv, readOnly: Boolean, availWidth: Dp) {
    val tl = remember(table, look, availWidth) { tableLookOf(table, env.pkg, look, availWidth) }
    val cellLook = look.scaled(tl.scale)
    val lastRow = rowIndex == table.rows.lastIndex
    val gridBefore = remember(row.trPr) {
        if (row.trPr.isEmpty()) 0 else XmlScan.scan(row.trPr).children.firstOrNull()?.child("gridBefore")?.attr(row.trPr, "val")?.toIntOrNull() ?: 0
    }
    val header = tl.firstRow && rowIndex == 0
    val banded = tl.bands && tl.bandFill != null && !header && (rowIndex - if (tl.firstRow) 1 else 0) % 2 == 0
    val line = if (tl.borders) look.ink.copy(0.55f) else if (env.editMode) look.ink.copy(0.14f) else Color.Transparent
    fun frac(from: Int, span: Int): Float {
        var f = 0f
        for (i in from until (from + span).coerceAtMost(tl.fractions.size)) f += tl.fractions[i]
        return f.coerceAtLeast(0.0001f)
    }
    val boxAlign = when (tl.jc) {
        "center" -> Alignment.TopCenter
        "right", "end" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = boxAlign) {
        Row(
            Modifier
                .then(if (boxAlign == Alignment.TopStart && tl.indent != 0.dp) Modifier.offset(x = tl.indent) else Modifier)
                .width(tl.width)
                .height(IntrinsicSize.Min)
        ) {
            var col = gridBefore
            if (gridBefore > 0) Spacer(Modifier.weight(frac(0, gridBefore)))
            val cells = row.cells
            for ((ci, cell) in cells.withIndex()) {
                val cp = cellProps(cell.tcPr)
                val fill = cp.fill ?: if (header) tl.headerFill else if (banded) tl.bandFill else null
                val lastCell = ci == cells.lastIndex
                val f = frac(col, cp.span)
                val cellWidth = tl.width * f
                Box(
                    Modifier
                        .weight(f)
                        .fillMaxHeight()
                        .then(look.fillOf(fill)?.let { Modifier.background(it) } ?: Modifier)
                        .drawBehind {
                            if (line.alpha == 0f) return@drawBehind
                            val sw = 1.dp.toPx().coerceAtMost(2f)
                            if (!cp.vContinue) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), sw)
                            drawLine(line, Offset(0f, 0f), Offset(0f, size.height), sw)
                            if (lastCell) drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), sw)
                            if (lastRow) drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), sw)
                        }
                        .padding(horizontal = (5.4f * cellLook.scale).dp, vertical = 1.dp),
                    contentAlignment = when (cp.vAlign) {
                        "center" -> Alignment.CenterStart
                        "bottom" -> Alignment.BottomStart
                        else -> Alignment.TopStart
                    }
                ) {
                    if (!cp.vContinue) Column(Modifier.fillMaxWidth()) {
                        val inner = cellWidth - (10.8f * cellLook.scale).dp
                        for (b in cell.blocks) when (b) {
                            is DocPara -> ParagraphBlock(b, cellLook, env, readOnly || !b.editable, cell = if (header) tl.headerR else null, maxWidth = inner)
                            is DocTable -> Column(Modifier.fillMaxWidth()) {
                                for ((i, r) in b.rows.withIndex()) TableRowBlock(b, r, i, cellLook, env, readOnly, inner)
                            }
                            is DocRaw -> for (x in b.preview) StaticBlock(x, cellLook, env)
                        }
                    }
                }
                col += cp.span
            }
            val used = col
            if (used < tl.fractions.size) Spacer(Modifier.weight(frac(used, tl.fractions.size - used)))
        }
    }
}

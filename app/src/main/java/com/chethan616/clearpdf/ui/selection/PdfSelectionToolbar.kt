package com.chethan616.clearpdf.ui.selection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.BiasAlignment
import com.chethan616.clearpdf.ui.components.liquidStretchOnDrag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.chipGlass
import com.chethan616.clearpdf.ui.components.chipInk
import com.chethan616.clearpdf.ui.components.glassMenu
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** An installed app offering `ACTION_PROCESS_TEXT` (Translate, dictionary, …). */
data class ProcessTextAction(val label: String, val icon: ImageBitmap?, val component: ComponentName)

/** What the floating selection toolbar can do. The viewer implements these against its models. */
class PdfSelectionActions(
    val onCopy: () -> Unit,
    val onSelectAll: () -> Unit,
    val onShare: () -> Unit,
    val onHighlight: (colorArgb: Long) -> Unit,
    val onUnderline: () -> Unit,
    val onStrike: () -> Unit,
    val onRemoveHighlight: () -> Unit,
    val onSearch: () -> Unit,
    val onProcessText: (ComponentName) -> Unit,
    /** The (page, line) the selection sits on when that line is editable digital text, else null. */
    val editableLine: () -> Pair<Int, com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock>? = { null },
    val onEditText: (Int, com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock) -> Unit = { _, _ -> }
)

/** Highlight palette: classic marker yellow first, then the design system's accents. */
val SelectionHighlightColors: List<Color> = listOf(
    Color(0xFFFFD60A), LiquidGlassColors.Green, LiquidGlassColors.Teal, LiquidGlassColors.Blue,
    LiquidGlassColors.Indigo, LiquidGlassColors.Purple, Color(0xFFFF375F), LiquidGlassColors.Orange
)

private enum class ToolbarPage { Main, Overflow, Colors }

private class ToolbarItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val bitmap: ImageBitmap? = null,
    val badge: Color? = null,
    val tint: Color? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit
)

fun queryProcessTextActions(context: Context): List<ProcessTextAction> = runCatching {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
    val sizePx = (24 * context.resources.displayMetrics.density).roundToInt()
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
        .map { ri ->
            ProcessTextAction(
                label = ri.loadLabel(pm).toString(),
                icon = runCatching { ri.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull(),
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
            )
        }
        .sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())

/**
 * The floating selection toolbar, in liquid glass, with the platform floating ActionMode's
 * BEHAVIOUR: anchored centred above the selection, flipping below it when there is no room, clamped
 * 8 dp from the screen edges, hidden while a handle is dragged or the document scrolls / flings /
 * zooms and popping back ~200 ms after it settles, with an overflow "⋯" that morphs the capsule into
 * a vertical menu (process-text apps such as Translate live there).
 *
 * Fills the viewer; only the capsule itself takes touches.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfSelectionToolbar(
    state: PdfTextSelectionState,
    listState: LazyListState,
    backdrop: Backdrop,
    actions: PdfSelectionActions,
    highlightColor: Color,
    hasHighlightOverlap: () -> Boolean,
    modifier: Modifier = Modifier,
    /**
     * Luminance (0..1) of the document inside a screen-space band. With it the capsule wears the
     * title-chip glass ([chipGlass]) — nearly clear — and picks its ink from the page behind it, like
     * the viewer's top bar; without it, the theme's dense platter.
     */
    luminanceAt: ((top: Float, bottom: Float) -> Float)? = null,
    /** Anything that changes what the page looks like (dark reader): re-picks the ink live. */
    appearanceKey: Any? = null
) {
    val isDark = LocalIsDarkMode.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val processActions by produceState(emptyList<ProcessTextAction>(), context) {
        value = withContext(Dispatchers.IO) { queryProcessTextActions(context) }
    }

    // ── Visibility: hide while anything moves, re-appear once it has settled ──────────────
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(listState, state) {
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, state.transform)
        }.collectLatest {
            moving = true
            delay(240)
            moving = false
        }
    }
    val want = state.hasSelection && !state.gestureActive && !moving && !listState.isScrollInProgress
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(want) {
        if (want) { delay(120); shown = true } else shown = false
    }
    var page by remember { mutableStateOf(ToolbarPage.Main) }
    // The sub-page most recently opened. It decides which edge the glass morphs from: the overflow
    // menu grows out of the trailing "⋯" button, the colour row out of the leading Highlight button,
    // and collapsing back keeps that same edge pinned so the morph visibly reverses.
    var lastSub by remember { mutableStateOf(ToolbarPage.Overflow) }
    fun go(p: ToolbarPage) {
        if (p != ToolbarPage.Main) lastSub = p
        page = p
    }
    // Written from the layout pass (only when it flips), read by the morph's content alignment so
    // the capsule grows away from the selection instead of over it.
    var placedAbove by remember { mutableStateOf(true) }
    LaunchedEffect(shown) { if (!shown) page = ToolbarPage.Main }
    // A new selection (not a toolbar action) resets the menu back to the capsule.
    LaunchedEffect(state.start, state.end) { page = ToolbarPage.Main }

    // Ink follows the page under the capsule (it may sit above or below the selection, so sample a
    // band spanning both), decided once per appearance — the capsule is hidden while anything moves.
    var onLight by remember { mutableStateOf(!isDark) }
    val currentLuminance by rememberUpdatedState(luminanceAt)
    LaunchedEffect(shown, appearanceKey) {
        val lum = currentLuminance ?: return@LaunchedEffect
        if (!shown) return@LaunchedEffect
        val rects = state.selectionScreenRects()
        if (rects.isEmpty()) return@LaunchedEffect
        val top = rects.minOf { it.top } - with(density) { 72.dp.toPx() }
        val bottom = rects.maxOf { it.bottom } + with(density) { 96.dp.toPx() }
        onLight = lum(top, bottom) > 0.6f
    }
    val fg by animateColorAsState(
        if (luminanceAt != null) chipInk(onLight) else LiquidGlassColors.text(isDark),
        GlassMotion.fade(),
        label = "selToolbarInk"
    )
    val glass by animateColorAsState(
        if (luminanceAt != null) chipGlass(onLight)
        else if (isDark) Color(0xFF1C1D22).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.74f),
        GlassMotion.fade(),
        label = "selToolbarGlass"
    )
    val menuDark = if (luminanceAt != null) !onLight else isDark

    val alpha by animateFloatAsState(if (shown) 1f else 0f, GlassMotion.fade(), label = "selToolbarAlpha")
    val scale by animateFloatAsState(
        if (shown) 1f else 0.9f,
        if (shown) GlassMotion.pop() else GlassMotion.settle(),
        label = "selToolbarScale"
    )

    // Accessibility: announce the selection size politely whenever it changes.
    // Computed only once a gesture settles, so a drag doesn't rebuild the text every frame.
    val settled = state.hasSelection && !state.gestureActive
    val selStart = state.start
    val selEnd = state.end
    val count = remember(settled, selStart, selEnd) { if (settled) state.selectedText().length else 0 }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (count > 0) {
            val announce = stringResource(R.string.selection_chars_selected, count)
            Box(Modifier.size(1.dp).semantics { contentDescription = announce; liveRegion = LiveRegionMode.Polite })
        }
        if (alpha <= 0.01f && !shown) return@BoxWithConstraints

        val marginPx = with(density) { 8.dp.toPx() }
        val gapPx = with(density) { 10.dp.toPx() }
        val handleDropPx = with(density) { 26.dp.toPx() }
        val topSafe = WindowInsets.statusBars.getTop(density) + marginPx
        val bottomInset = WindowInsets.navigationBars.getBottom(density) + marginPx

        val itemSize = 44.dp
        val availableDp = maxWidth - 16.dp - 8.dp // screen margins + capsule padding
        val lastBounds = remember { arrayOfNulls<Rect>(1) }

        val copyL = stringResource(R.string.copy)
        val selectAllL = stringResource(R.string.viewer_select_all)
        val shareL = stringResource(R.string.share)
        val highlightL = stringResource(R.string.viewer_highlight)
        val underlineL = stringResource(R.string.viewer_underline)
        val strikeL = stringResource(R.string.selection_strikethrough)
        val removeL = stringResource(R.string.selection_remove_highlight)
        val searchL = stringResource(R.string.selection_search)
        val moreL = stringResource(R.string.selection_more)
        val backL = stringResource(R.string.back)
        val colorL = stringResource(R.string.selection_highlight_color)
        val editTextL = stringResource(R.string.text_edit_title)

        fun act(block: () -> Unit): () -> Unit = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            block()
        }

        val editLine = if (shown) actions.editableLine() else null
        val items = buildList {
            add(ToolbarItem("copy", copyL, Icons.Rounded.ContentCopy, onClick = act(actions.onCopy)))
            if (editLine != null) add(ToolbarItem("edit", editTextL, Icons.Rounded.EditNote, onClick = act { actions.onEditText(editLine.first, editLine.second) }))
            add(ToolbarItem("all", selectAllL, Icons.Rounded.SelectAll, onClick = act(actions.onSelectAll)))
            add(ToolbarItem(
                "hl", highlightL, Icons.Rounded.Highlight, badge = highlightColor,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); go(ToolbarPage.Colors) },
                onClick = act { actions.onHighlight(highlightColor.toArgbLong()) }
            ))
            if (shown && hasHighlightOverlap()) add(ToolbarItem("rm", removeL, Icons.Rounded.FormatColorReset, tint = LiquidGlassColors.Red, onClick = act(actions.onRemoveHighlight)))
            add(ToolbarItem("ul", underlineL, Icons.Rounded.FormatUnderlined, onClick = act(actions.onUnderline)))
            add(ToolbarItem("st", strikeL, Icons.Rounded.StrikethroughS, onClick = act(actions.onStrike)))
            add(ToolbarItem("share", shareL, Icons.Rounded.Share, onClick = act(actions.onShare)))
            add(ToolbarItem("search", searchL, Icons.Rounded.Search, onClick = act(actions.onSearch)))
        }
        val processItems = processActions.map { pa ->
            ToolbarItem("pt:${pa.component.flattenToShortString()}", pa.label, bitmap = pa.icon, onClick = act { actions.onProcessText(pa.component) })
        }
        // Like the platform toolbar: as many primary items as fit, the rest (and every process-text
        // app) behind the overflow button.
        val fitAll = processItems.isEmpty() && itemSize * items.size <= availableDp
        val primaryCount = if (fitAll) items.size
        else ((availableDp - itemSize) / itemSize).toInt().coerceIn(1, items.size)
        val primary = items.take(primaryCount)
        val overflow = items.drop(primaryCount) + processItems

        // The Main capsule's width is fully determined by its item count, so the morph can pin an
        // edge of it without waiting for a measure pass.
        val mainWPx = with(density) { (itemSize * (primary.size + if (overflow.isNotEmpty()) 1 else 0) + 8.dp).toPx() }
        val mainHPx = with(density) { 48.dp.toPx() }
        val anchorEnd = lastSub == ToolbarPage.Overflow
        val alignH = if (anchorEnd) 1f else -1f
        val alignV = if (placedAbove) 1f else -1f

        Layout(
            content = {
                // Each page carries its own glass (a single shared glass node on the animating
                // container went see-through once it morphed into the menu). The pages still grow
                // out of the same edge and cross-fade on the morph spring, so it reads as one
                // surface stretching into the next.
                AnimatedContent(
                    targetState = page,
                    contentAlignment = BiasAlignment(alignH, alignV),
                    transitionSpec = {
                        val origin = TransformOrigin(if (anchorEnd) 1f else 0f, if (placedAbove) 1f else 0f)
                        (fadeIn(GlassMotion.fade()) + scaleIn(GlassMotion.morph(), initialScale = 0.92f, transformOrigin = origin)) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMediumLow * 2f)) + scaleOut(GlassMotion.settle(), targetScale = 0.96f, transformOrigin = origin)) using
                            SizeTransform(clip = false) { _, _ -> GlassMotion.morph() }
                    },
                    label = "selToolbarMorph"
                ) { p ->
                    when (p) {
                        ToolbarPage.Main -> Row(
                            Modifier
                                .viewerGlass(backdrop, glass, shape = { Capsule })
                                .height(48.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            primary.forEach { ToolbarIconButton(it, fg) }
                            if (overflow.isNotEmpty()) {
                                ToolbarIconButton(ToolbarItem("more", moreL, Icons.Rounded.MoreHoriz, onClick = act { go(ToolbarPage.Overflow) }), fg)
                            }
                        }
                        ToolbarPage.Overflow -> Column(
                            Modifier
                                // A menu of text rows: the frosted menu material (same as dropdowns),
                                // so labels stay legible over page text.
                                .glassMenu(backdrop, dark = menuDark, shape = { RoundedRectangle(22.dp) })
                                .widthIn(min = 200.dp, max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp)
                        ) {
                            val rows = listOf(ToolbarItem("back", backL, Icons.AutoMirrored.Rounded.ArrowBack, onClick = act { go(ToolbarPage.Main) })) + overflow
                            rows.forEachIndexed { i, item -> MenuRow(item, fg, index = i, fromBelow = placedAbove) }
                        }
                        ToolbarPage.Colors -> Row(
                            Modifier
                                .viewerGlass(backdrop, glass, shape = { Capsule })
                                .height(48.dp)
                                .padding(horizontal = 4.dp)
                                .semantics { contentDescription = colorL },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolbarIconButton(ToolbarItem("back", backL, Icons.AutoMirrored.Rounded.ArrowBack, onClick = act { go(ToolbarPage.Main) }), fg)
                            SelectionHighlightColors.forEachIndexed { i, c ->
                                ColorDot(backdrop, c, selected = c.toArgbLong() == highlightColor.toArgbLong(), ring = fg, index = i) {
                                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    actions.onHighlight(c.toArgbLong())
                                }
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val maxW = (constraints.maxWidth - 2 * marginPx).roundToInt().coerceAtLeast(0)
            val maxH = (constraints.maxHeight - topSafe - bottomInset).roundToInt().coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(maxWidth = maxW, maxHeight = maxH))
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Read geometry here (layout phase) so the capsule follows the selection.
                listState.firstVisibleItemScrollOffset; state.transform
                val screenH = constraints.maxHeight.toFloat()
                val screenW = constraints.maxWidth.toFloat()
                val rects = state.selectionScreenRects()
                val union = rects.takeIf { it.isNotEmpty() }?.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
                val visible = union?.takeIf { it.bottom > 0f && it.top < screenH }
                    ?.let { Rect(it.left, it.top.coerceAtLeast(0f), it.right, it.bottom.coerceAtMost(screenH)) }
                if (visible != null) lastBounds[0] = visible
                // Selection scrolled out of view: hide (the platform toolbar does too). The last
                // bounds are only reused while the capsule fades out after the selection cleared.
                if (visible == null && state.hasSelection) return@layout
                val b = visible ?: lastBounds[0] ?: return@layout
                val w = placeable.width.toFloat()
                val h = placeable.height.toFloat()
                val bottomSafe = screenH - bottomInset
                // Above/below is decided for the 48 dp capsule, not the (animating) current size, so
                // the menu never flips sides mid-morph: it grows away from the selection instead.
                val aboveTop = b.top - gapPx - mainHPx
                val below = b.bottom + handleDropPx + gapPx
                val side = when {
                    aboveTop >= topSafe -> 1
                    below + mainHPx <= bottomSafe -> -1
                    else -> 0
                }
                val y = when (side) {
                    1 -> (b.top - gapPx - h).coerceAtLeast(topSafe)
                    -1 -> below.coerceAtMost(bottomSafe - h).coerceAtLeast(topSafe)
                    // Selection taller than the screen: float inside it, near the top, like the
                    // platform toolbar does.
                    else -> (b.top + gapPx).coerceIn(topSafe, (bottomSafe - h).coerceAtLeast(topSafe))
                }
                if (placedAbove != (side == 1)) placedAbove = side == 1
                // Pin the Main capsule's leading or trailing edge; at w == mainW both agree, so the
                // capsule never jumps when a morph starts or ends.
                val mainX = (b.center.x - mainWPx / 2f).coerceIn(marginPx, (screenW - mainWPx - marginPx).coerceAtLeast(marginPx))
                val pinned = if (anchorEnd) mainX + mainWPx - w else mainX
                val x = pinned.coerceIn(marginPx, (screenW - w - marginPx).coerceAtLeast(marginPx))
                // Show/hide pops out of the selection: the pivot is the point of the capsule
                // nearest the selection's centre.
                val pivotX = if (w > 0f) ((b.center.x - x) / w).coerceIn(0f, 1f) else 0.5f
                val pivotY = if (side == 1) 1f else 0f
                placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                    this.alpha = alpha
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                }
            }
        }
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * Entrance progress (0..1) for the [index]-th item of a page that just morphed in: items land one
 * after another (~22 ms apart) as the glass opens, like an iOS menu unfolding. Items past the first
 * eight share the last delay so long process-text lists don't trail.
 */
@Composable
private fun rememberStaggerIn(index: Int): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(30L + 22L * index.coerceAtMost(8))
        progress.animateTo(1f, GlassMotion.settle())
    }
    return progress
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarIconButton(item: ToolbarItem, fg: Color) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) GlassMotion.PressedScale else 1f, GlassMotion.press(), label = "tbPress")
    val wash by animateFloatAsState(if (pressed) 0.10f else 0f, GlassMotion.fade(), label = "tbWash")
    Box(
        Modifier
            .size(44.dp)
            // Draw-time only: a finger sliding across the item squashes it along the drag, like
            // a drop of the glass it sits on; the press scale adds the tactile dip.
            .liquidStretchOnDrag(stretchFactor = 0.18f, minScale = 0.88f, maxScale = 1.12f)
            .graphicsLayer { scaleX = s; scaleY = s }
            .drawBehind { if (wash > 0f) drawCircle(fg.copy(alpha = wash)) }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = item.onLongClick,
                onClick = item.onClick
            )
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center
    ) {
        ItemIcon(item, item.tint ?: fg, 21.dp)
        if (item.badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 9.dp)
                    .size(8.dp)
                    .background(item.badge, CircleShape)
                    .border(1.dp, fg.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

@Composable
private fun ItemIcon(item: ToolbarItem, tint: Color, size: androidx.compose.ui.unit.Dp) {
    when {
        item.icon != null -> Icon(item.icon, null, Modifier.size(size), tint)
        item.bitmap != null -> Image(item.bitmap, null, Modifier.size(size))
        else -> Box(Modifier.size(size))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuRow(item: ToolbarItem, fg: Color, index: Int, fromBelow: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, GlassMotion.press(), label = "rowPress")
    val wash by animateFloatAsState(if (pressed) 0.08f else 0f, GlassMotion.fade(), label = "rowWash")
    val enter = rememberStaggerIn(index)
    val shiftPx = with(LocalDensity.current) { 8.dp.toPx() } * (if (fromBelow) 1f else -1f)
    Row(
        Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .height(46.dp)
            .graphicsLayer {
                val e = enter.value
                alpha = e
                translationY = (1f - e) * shiftPx
                scaleX = s; scaleY = s
            }
            .drawBehind { if (wash > 0f) drawRect(fg.copy(alpha = wash)) }
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = item.onLongClick, onClick = item.onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ItemIcon(item, item.tint ?: fg, 20.dp)
        BasicText(
            item.label,
            style = TextStyle(item.tint ?: fg, 15.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColorDot(backdrop: Backdrop, color: Color, selected: Boolean, ring: Color, index: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) GlassMotion.PressedScale else 1f, GlassMotion.press(), label = "dotPress")
    val enter = rememberStaggerIn(index)
    Box(
        Modifier
            .size(width = 34.dp, height = 44.dp)
            .liquidStretchOnDrag(stretchFactor = 0.18f, minScale = 0.88f, maxScale = 1.12f)
            .graphicsLayer {
                val e = enter.value
                alpha = e
                val k = s * (0.6f + 0.4f * e)
                scaleX = k; scaleY = k
            }
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(24.dp)
                .viewerGlass(backdrop, color.copy(alpha = 0.88f), shape = { CircleShape })
                .border(if (selected) 2.dp else 1.dp, ring.copy(alpha = if (selected) 0.9f else 0.25f), CircleShape)
        )
    }
}

/** A brief liquid-glass "Copied" confirmation near the bottom of the viewer. [trigger] > 0 shows it. */
@Composable
fun PdfCopiedToast(
    trigger: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    // Whether the content behind the toast is light; when given, it wears the chip glass with ink
    // that follows the page (like the toolbar above), otherwise the theme platter.
    onLight: Boolean? = null
) {
    val isDark = LocalIsDarkMode.current
    val fg = if (onLight != null) chipInk(onLight) else LiquidGlassColors.text(isDark)
    val glass = if (onLight != null) chipGlass(onLight)
    else if (isDark) Color(0xFF1C1D22).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.74f)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger > 0) { visible = true; delay(1400); visible = false }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, GlassMotion.fade(), label = "copiedAlpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.9f, if (visible) GlassMotion.pop() else GlassMotion.settle(), label = "copiedScale")
    if (alpha <= 0.01f) return
    val label = stringResource(R.string.selection_copied)
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .viewerGlass(backdrop, glass, shape = { Capsule })
            .height(40.dp)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), LiquidGlassColors.Green)
        BasicText(label, style = TextStyle(fg, 14.sp, FontWeight.SemiBold))
    }
}

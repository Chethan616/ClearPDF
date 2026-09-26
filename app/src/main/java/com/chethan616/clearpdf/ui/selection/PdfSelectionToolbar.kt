package com.chethan616.clearpdf.ui.selection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
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
    val onProcessText: (ComponentName) -> Unit
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
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val fg = LiquidGlassColors.text(isDark)
    val glass = if (isDark) Color(0xFF1C1D22).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.74f)
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
    LaunchedEffect(shown) { if (!shown) page = ToolbarPage.Main }
    // A new selection (not a toolbar action) resets the menu back to the capsule.
    LaunchedEffect(state.start, state.end) { page = ToolbarPage.Main }

    val alpha by animateFloatAsState(if (shown) 1f else 0f, GlassMotion.fade(), label = "selToolbarAlpha")
    val scale by animateFloatAsState(
        if (shown) 1f else 0.86f,
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

        fun act(block: () -> Unit): () -> Unit = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            block()
        }

        val items = buildList {
            add(ToolbarItem("copy", copyL, Icons.Rounded.ContentCopy, onClick = act(actions.onCopy)))
            add(ToolbarItem("all", selectAllL, Icons.Rounded.SelectAll, onClick = act(actions.onSelectAll)))
            add(ToolbarItem(
                "hl", highlightL, Icons.Rounded.Highlight, badge = highlightColor,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); page = ToolbarPage.Colors },
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

        Layout(
            content = {
                Box(
                    Modifier.graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    }
                ) {
                    when (page) {
                        ToolbarPage.Main -> Row(
                            Modifier
                                .viewerGlass(backdrop, glass, shape = { RoundedRectangle(24.dp) })
                                .height(48.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            primary.forEach { ToolbarIconButton(it, fg) }
                            if (overflow.isNotEmpty()) {
                                ToolbarIconButton(ToolbarItem("more", moreL, Icons.Rounded.MoreHoriz, onClick = act { page = ToolbarPage.Overflow }), fg)
                            }
                        }
                        ToolbarPage.Overflow -> Column(
                            Modifier
                                .viewerGlass(backdrop, glass, shape = { RoundedRectangle(22.dp) })
                                .widthIn(min = 200.dp, max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 6.dp)
                        ) {
                            MenuRow(ToolbarItem("back", backL, Icons.AutoMirrored.Rounded.ArrowBack, onClick = act { page = ToolbarPage.Main }), fg)
                            overflow.forEach { MenuRow(it, fg) }
                        }
                        ToolbarPage.Colors -> Row(
                            Modifier
                                .viewerGlass(backdrop, glass, shape = { RoundedRectangle(24.dp) })
                                .height(48.dp)
                                .padding(horizontal = 4.dp)
                                .semantics { contentDescription = colorL },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolbarIconButton(ToolbarItem("back", backL, Icons.AutoMirrored.Rounded.ArrowBack, onClick = act { page = ToolbarPage.Main }), fg)
                            SelectionHighlightColors.forEach { c ->
                                ColorDot(c, selected = c.toArgbLong() == highlightColor.toArgbLong(), ring = fg) {
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
            val placeable = measurables.first().measure(Constraints(maxWidth = maxW, maxHeight = constraints.maxHeight))
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
                val above = b.top - gapPx - h
                val below = b.bottom + handleDropPx + gapPx
                val y = when {
                    above >= topSafe -> above
                    below + h <= bottomSafe -> below
                    // Selection taller than the screen: float inside it, near the top, like the
                    // platform toolbar does.
                    else -> (b.top + gapPx).coerceIn(topSafe, (bottomSafe - h).coerceAtLeast(topSafe))
                }
                val x = (b.center.x - w / 2f).coerceIn(marginPx, (screenW - w - marginPx).coerceAtLeast(marginPx))
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarIconButton(item: ToolbarItem, fg: Color) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) GlassMotion.PressedScale else 1f, GlassMotion.press(), label = "tbPress")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .background(if (pressed) fg.copy(alpha = 0.10f) else Color.Transparent, CircleShape)
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
private fun MenuRow(item: ToolbarItem, fg: Color) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .height(46.dp)
            .background(if (pressed) fg.copy(alpha = 0.08f) else Color.Transparent)
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
private fun ColorDot(color: Color, selected: Boolean, ring: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) GlassMotion.PressedScale else 1f, GlassMotion.press(), label = "dotPress")
    Box(
        Modifier
            .size(width = 34.dp, height = 44.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(color, CircleShape)
                .border(if (selected) 2.dp else 1.dp, ring.copy(alpha = if (selected) 0.9f else 0.25f), CircleShape)
        )
    }
}

/** A brief liquid-glass "Copied" confirmation near the bottom of the viewer. [trigger] > 0 shows it. */
@Composable
fun PdfCopiedToast(trigger: Int, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val isDark = LocalIsDarkMode.current
    val fg = LiquidGlassColors.text(isDark)
    val glass = if (isDark) Color(0xFF1C1D22).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.74f)
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
            .viewerGlass(backdrop, glass, shape = { RoundedRectangle(20.dp) })
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

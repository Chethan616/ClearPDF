package com.chethan616.clearpdf.ui.viewer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.iconAccent
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.first

/** The room the floating top row takes: a 48 dp capsule and its margins. */
val TopBarHeight = 64.dp

/** The room the floating bottom dock takes: a 60 dp capsule and its margins. */
val BottomBarHeight = 76.dp

/** The dock has no handle strip of its own. */
val QuickHandleHeight = 0.dp

/** The dock's room. */
val QuickBarHeight = BottomBarHeight

/**
 * The viewer's floating top row: a round back button, a wide glass capsule with the document's
 * name, the page it's on and Find, then Share and More. With a tool out: a candy Done, the tool's
 * name, and Undo / Redo.
 */
@Composable
fun ViewerTopBar(
    title: String,
    subtitle: String?,
    toolTitle: String?,
    toolActive: Boolean,
    doneIsCheck: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTitle: () -> Unit,
    onSearch: (() -> Unit)?,
    onShare: () -> Unit,
    onMore: () -> Unit,
    undoLabel: String? = null,
    redoLabel: String? = null,
) {
    val c = clear
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (toolActive && doneIsCheck) {
            GlassCircle(onClick = onBack, tint = Candy.Sky, label = stringResource(R.string.common_done)) { GlassIcon(LucideIcons.Check, null, Modifier.size(22.dp)) }
        } else {
            GlassCircle(onClick = onBack, label = stringResource(R.string.common_back)) { GlassIcon(LucideIcons.ArrowLeft, null, Modifier.size(21.dp)) }
        }
        GlassPill(onClick = onTitle, modifier = Modifier.weight(1f), height = 48.dp, horizontalPadding = 16.dp) {
            if (toolTitle != null) Box(Modifier.size(9.dp).clip(CircleShape).background(Candy.Sky))
            Column(Modifier.weight(1f)) {
                Text(toolTitle ?: title, style = ClearType.Title.copy(fontSize = 16.sp, lineHeight = 19.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && toolTitle == null) Text(subtitle, style = ClearType.Caption, color = c.inkSoft, maxLines = 1)
            }
            if (onSearch != null && !toolActive) {
                Box(Modifier.size(36.dp).pressable(onSearch).semantics { contentDescription = "" }, contentAlignment = Alignment.Center) {
                    Icon(LucideIcons.Search, stringResource(R.string.viewer_find_in_document), tint = c.ink, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (toolActive) {
            if (canUndo || canRedo) {
                GlassCircle(onClick = { if (canUndo) onUndo() }, label = stringResource(R.string.common_undo)) {
                    GlassIcon(LucideIcons.Undo2, null, Modifier.size(20.dp), tint = if (canUndo) Color.Unspecified else c.inkFaint)
                }
                GlassCircle(onClick = { if (canRedo) onRedo() }, label = stringResource(R.string.common_redo)) {
                    GlassIcon(LucideIcons.Redo2, null, Modifier.size(20.dp), tint = if (canRedo) Color.Unspecified else c.inkFaint)
                }
            }
        } else {
            if (canUndo) UndoMenuButton(canUndo, canRedo, onUndo, onRedo, undoLabel, redoLabel)
            GlassCircle(onClick = onShare, label = stringResource(R.string.common_share)) { GlassIcon(LucideIcons.Share2, null, Modifier.size(20.dp)) }
            GlassCircle(onClick = onMore, label = stringResource(R.string.common_more_options)) { GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(21.dp)) }
        }
    }
}

/** Undo as a glass circle: a tap opens a menu naming what Undo and Redo would do. */
@Composable
fun UndoMenuButton(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, undoLabel: String? = null, redoLabel: String? = null) {
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    GlassCircle(
        onClick = { if (canUndo || canRedo) open = true },
        modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
        label = stringResource(R.string.viewer_undo_or_redo),
    ) { GlassIcon(LucideIcons.Undo2, null, Modifier.size(20.dp)) }
    if (open) {
        val items = buildList {
            if (canUndo) add(GlassMenuItem(undoLabel?.let { stringResource(R.string.viewer_undo_named, it) } ?: stringResource(R.string.common_undo), LucideIcons.Undo2, onClick = onUndo))
            if (canRedo) add(GlassMenuItem(redoLabel?.let { stringResource(R.string.viewer_redo_named, it) } ?: stringResource(R.string.common_redo), LucideIcons.Redo2, onClick = onRedo))
        }
        GlassMenu(anchor, { open = false }, items)
    }
}

/**
 * Find, floating where the top row was: a round close button, then a glass capsule holding the
 * field, the "3 of 12" count (Extra Features), clear, Previous and Next.
 */
@Composable
fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    canStep: Boolean,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    count: String? = null,
) {
    val c = clear
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val findLabel = stringResource(R.string.viewer_find_in_document)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassCircle(onClick = onClose, label = stringResource(R.string.viewer_collapse)) { GlassIcon(LucideIcons.ArrowLeft, null, Modifier.size(21.dp)) }
        Row(
            Modifier.weight(1f).height(48.dp).glass(Capsule, blur = 6.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LucideIcons.Search, null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = ClearType.Body.copy(color = c.ink),
                cursorBrush = SolidColor(Candy.Tomato),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = findLabel },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(findLabel, style = ClearType.Body, color = c.inkFaint, maxLines = 1)
                        inner()
                    }
                },
            )
            if (count != null) Text(count, style = ClearType.Label, color = c.inkSoft, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
            if (query.isNotEmpty()) StepButton(LucideIcons.X, stringResource(R.string.common_clear), true, onClear)
            StepButton(LucideIcons.ChevronUp, stringResource(R.string.viewer_find_previous), canStep, onPrev)
            StepButton(LucideIcons.ChevronDown, stringResource(R.string.viewer_find_next), canStep, onNext)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = clear
    Box(Modifier.size(38.dp).pressable(onClick, enabled = enabled).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (enabled) c.ink else c.inkFaint, modifier = Modifier.size(19.dp))
    }
}

/** How many tools fit in the dock at a screen [widthDp] wide (More tools takes its own bubble). */
private fun dockSlots(widthDp: Int): Int = when {
    widthDp >= 768 -> 8
    widthDp >= 460 -> 6
    else -> 5
}

/**
 * The floating dock: the tools the reader keeps there, each glyph in its own candy, on a glass
 * capsule, and More tools as a grape bubble beside it.
 */
@Composable
fun QuickToolbar(
    tools: List<QuickTool>,
    onTool: (QuickTool) -> Unit,
    onMoreTools: () -> Unit,
    moreToolsOpen: Boolean,
) {
    val slots = dockSlots(LocalConfiguration.current.screenWidthDp)
    val shown = tools.take(slots)
    val turn by androidx.compose.animation.core.animateFloatAsState(if (moreToolsOpen) 45f else 0f, GlassMotion.morph(), label = "more")
    Row(
        Modifier.fillMaxWidth().height(BottomBarHeight).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f).fillMaxHeight().glass(Capsule, blur = 6.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in shown) DockItem(t.icon, stringResource(t.label), iconAccent(t.icon)) { onTool(t) }
        }
        GlassCircle(onClick = onMoreTools, size = 60.dp, tint = Candy.Grape, label = stringResource(R.string.common_tool_more_tools)) {
            GlassIcon(LucideIcons.LayoutGrid, null, Modifier.size(24.dp).androidxRotate(turn))
        }
    }
}

private fun Modifier.androidxRotate(degrees: Float) = this.graphicsLayer { rotationZ = degrees }

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockItem(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    val c = clear
    Column(
        Modifier.weight(1f).fillMaxHeight().pressable(onClick, pressedScale = 0.88f).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = ClearType.Caption.copy(fontSize = 10.5.sp, lineHeight = 12.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** No line along bars any more: glass floats. Kept so the tools' own bars can call it. */
fun Modifier.barTopLine(): Modifier = this

/**
 * A tool's sub-toolbar: its buttons on a floating glass capsule, centred, sliding sideways when they
 * don't all fit (to a tool when it's chosen).
 */
@Composable
fun SubToolbar(content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth().height(BottomBarHeight).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = maxWidth).fillMaxHeight().glass(Capsule, blur = 6.dp).horizontalScroll(scroll).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) { CompositionLocalProvider(LocalSubToolbarScroll provides scroll) { content() } }
    }
}

/** The scrolling bar a sub-toolbar tool sits in (none outside one). */
private val LocalSubToolbarScroll = staticCompositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

/** When the tool becomes the chosen one, the bar scrolls to bring it to the start (as far as it goes). */
@Composable
private fun Modifier.slideToWhenChosen(selected: Boolean): Modifier {
    val scroll = LocalSubToolbarScroll.current ?: return this
    var x by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (!selected) return@LaunchedEffect
        val at = snapshotFlow { x }.first { it != null } ?: return@LaunchedEffect
        scroll.animateScrollTo(at)
    }
    return onPlaced { x = it.positionInParent().x.toInt() }
}

/** A sub-toolbar tool; the chosen one sits on a candy disc. */
@Composable
fun SubTool(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    SubButton(icon, description, selected, onClick, enabled, width = 54.dp)
}

/** Fill & Sign's text tool: an "A" in a dashed box (a field to type in). */
@Composable
fun SubTextField(description: String, selected: Boolean, onClick: () -> Unit) {
    SubFace(description, selected, onClick, 54.dp) { tint ->
        Box(
            Modifier.size(22.dp).drawBehind {
                val w = 1.6.dp.toPx()
                drawRect(
                    tint, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))),
                )
            },
            contentAlignment = Alignment.Center,
        ) { Text("A", color = tint, fontSize = 13.sp, style = ClearType.Tile.copy(fontSize = 13.sp)) }
    }
}

/** A sub-toolbar tool drawn as a filled dot (Fill & Sign's dot mark). */
@Composable
fun SubDot(description: String, selected: Boolean, onClick: () -> Unit) {
    SubFace(description, selected, onClick, 54.dp) { tint -> Box(Modifier.size(9.dp).background(tint, CircleShape)) }
}

/** A sub-toolbar property (line thickness, text size): narrower, a soft disc while its picker is open. */
@Composable
fun SubProperty(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    SubButton(icon, description, selected, onClick, true, width = 46.dp, shaded = true)
}

@Composable
private fun SubButton(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean, width: Dp, shaded: Boolean = false) {
    SubFace(description, selected, onClick, width, enabled, shaded) { tint -> Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** The face every sub-toolbar button shares: a jelly press and, chosen, a candy disc with white ink. */
@Composable
private fun SubFace(description: String, selected: Boolean, onClick: () -> Unit, width: Dp, enabled: Boolean = true, shaded: Boolean = false, glyph: @Composable (Color) -> Unit) {
    val c = clear
    val vivid = selected && !shaded
    val disc by animateColorAsState(
        when {
            vivid -> Candy.Sky
            selected -> c.ink.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        GlassMotion.settle(), label = "sub",
    )
    Box(
        Modifier.width(width).fillMaxHeight().slideToWhenChosen(vivid).pressable(onClick, enabled = enabled, pressedScale = 0.86f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(disc))
        glyph(if (!enabled) c.inkFaint else if (vivid) Color.White else c.ink)
    }
}

/** A sub-toolbar divider. */
@Composable
fun SubDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(clear.line))
}

/** The colour button of a sub-toolbar: a bead of the tool's colour, ringed while its picker is open. */
@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = clear
    val colourLabel = stringResource(R.string.viewer_colour)
    Box(
        Modifier.width(46.dp).fillMaxHeight().pressable(onClick, pressedScale = 0.86f).semantics { contentDescription = colourLabel },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(38.dp).border(2.dp, c.ink.copy(alpha = 0.5f), CircleShape))
        Box(Modifier.size(26.dp).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape))
    }
}

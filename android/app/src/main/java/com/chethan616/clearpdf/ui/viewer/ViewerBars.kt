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
import androidx.compose.ui.layout.layout
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
import com.chethan616.clearpdf.ui.glass.BarGlyphButton
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.iconAccent
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.pdfColors
import com.chethan616.clearpdf.ui.glass.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.kyant.backdrop.floatingLayer

/** The room the floating top row takes: a 48 dp capsule and its margins. */
val TopBarHeight = 64.dp

/** The room the floating bottom dock takes: a 60 dp capsule and its margins. */
val BottomBarHeight = 76.dp

/** The dock has no handle strip of its own. */
val QuickHandleHeight = 0.dp

/** The dock's room. */
val QuickBarHeight = BottomBarHeight

/**
 * The viewer's floating top row, iOS 26 style: a round glass Back (the same plain glass as every
 * glass button), a wide glass capsule with the document's name, the page it's on and Find, then
 * Share and More together in one glass capsule (a group, as iOS groups its bar buttons: one glass
 * instead of a cluster of stacked ones). With a tool out: a candy Done, the tool's name, and Undo /
 * Redo grouped the same way. All of it refracts the pages under it.
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
    onTitle: (Rect) -> Unit,
    onSearch: (() -> Unit)?,
    onShare: () -> Unit,
    onMore: (Rect) -> Unit,
    undoLabel: String? = null,
    redoLabel: String? = null,
) {
    val c = pdfColors
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (toolActive && doneIsCheck) {
            GlassCircle(onClick = onBack, tint = Candy.Sky, label = stringResource(R.string.common_done)) { GlassIcon(LucideIcons.Check, null, Modifier.size(18.dp)) }
        } else {
            com.chethan616.clearpdf.ui.glass.GlassBackButton(onBack)
        }
        var titleAnchor by remember { mutableStateOf(Rect.Zero) }
        GlassPill(
            onClick = { onTitle(titleAnchor) },
            modifier = Modifier.weight(1f).onGloballyPositioned { titleAnchor = it.boundsInWindow() },
            height = 48.dp,
            horizontalPadding = 16.dp,
            surface = c.surfaceRaised.copy(alpha = if (c.dark) 0.90f else 0.84f),
        ) {
            CompositionLocalProvider(LocalGlassInk provides c.icon) {
            if (toolTitle != null) Box(Modifier.size(9.dp).clip(CircleShape).background(Candy.Sky))
            Column(Modifier.weight(1f)) {
                Text(toolTitle ?: title, style = ClearType.Title.copy(fontSize = 15.5.sp, lineHeight = 19.sp), color = c.icon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && toolTitle == null) Text(subtitle, style = ClearType.Caption, color = c.icon.copy(alpha = 0.72f), maxLines = 1)
            }
            if (onSearch != null && !toolActive) {
                BarGlyphButton(onSearch, stringResource(R.string.viewer_find_in_document), size = 36.dp) {
                    Icon(LucideIcons.Search, null, tint = c.icon, modifier = Modifier.size(19.dp))
                }
            }
            }
        }
        if (toolActive) {
            if (canUndo || canRedo) {
                BarGroup {
                    BarGlyphButton({ if (canUndo) onUndo() }, stringResource(R.string.common_undo), enabled = canUndo) {
                        Icon(LucideIcons.Undo2, null, tint = if (canUndo) com.chethan616.clearpdf.ui.glass.LocalGlassInk.current else com.chethan616.clearpdf.ui.glass.LocalGlassInk.current.copy(alpha = 0.38f), modifier = Modifier.size(20.dp))
                    }
                    BarGlyphButton({ if (canRedo) onRedo() }, stringResource(R.string.common_redo), enabled = canRedo) {
                        Icon(LucideIcons.Redo2, null, tint = if (canRedo) com.chethan616.clearpdf.ui.glass.LocalGlassInk.current else com.chethan616.clearpdf.ui.glass.LocalGlassInk.current.copy(alpha = 0.38f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            BarGroup {
                if (canUndo) UndoMenuButton(canUndo, canRedo, onUndo, onRedo, undoLabel, redoLabel)
                BarGlyphButton(onShare, stringResource(R.string.common_share)) { Icon(LucideIcons.Share2, null, tint = c.icon, modifier = Modifier.size(19.dp)) }
                var moreAnchor by remember { mutableStateOf(Rect.Zero) }
                BarGlyphButton({ onMore(moreAnchor) }, stringResource(R.string.common_more_options), modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() }) { Icon(LucideIcons.Ellipsis, null, tint = c.icon, modifier = Modifier.size(21.dp)) }
            }
        }
    }
}

/**
 * The viewer's bars coming and going: they spring in from their edge on the glass spring (Settings ▸
 * Motion), swelling a touch past rest, and ease away. They move as finished textures, so the glass is
 * not re-blurred on every frame of the slide.
 */
@Composable
fun GlassBars(visible: Boolean, fromTop: Boolean, content: @Composable () -> Unit) {
    val p = remember { androidx.compose.animation.core.Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        p.animateTo(if (visible) 1f else 0f, if (visible) com.chethan616.clearpdf.ui.glass.GlassMotion.unfold() else com.chethan616.clearpdf.ui.glass.Fluid.dismiss())
    }
    val shown by remember { androidx.compose.runtime.derivedStateOf { visible || p.value > 0.002f } }
    if (!shown) return
    Box(
        Modifier.floatingLayer {
            val v = p.value
            val away = (1f - v).coerceAtLeast(0f)
            // past rest only a little, and only toward the middle of the screen
            val over = (v - 1f).coerceAtLeast(0f) * 10.dp.toPx()
            translationY = (if (fromTop) -1f else 1f) * (away * size.height * 0.8f - over)
            val s = 0.94f + 0.06f * v.coerceIn(0f, 1f)
            scaleX = s
            scaleY = s
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (fromTop) 0f else 1f)
            alpha = v.coerceIn(0f, 1f)
        },
    ) { content() }
}

/** Bar buttons grouped on one glass capsule, as iOS groups them. */
@Composable
private fun BarGroup(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = pdfColors
    Row(
        Modifier.height(48.dp).glassBar(surface = c.surfaceRaised.copy(alpha = if (c.dark) 0.90f else 0.84f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { CompositionLocalProvider(com.chethan616.clearpdf.ui.glass.LocalGlassInk provides c.icon) { content() } }
}

/** Undo in a bar group: a tap opens a menu naming what Undo and Redo would do. */
@Composable
fun UndoMenuButton(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, undoLabel: String? = null, redoLabel: String? = null) {
    var open by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    BarGlyphButton(
        { if (canUndo || canRedo) open = true },
        stringResource(R.string.viewer_undo_or_redo),
        modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
    ) { Icon(LucideIcons.Undo2, null, tint = com.chethan616.clearpdf.ui.glass.LocalGlassInk.current, modifier = Modifier.size(20.dp)) }
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
    progress: () -> Float,
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
    val c = pdfColors
    val ink = com.chethan616.clearpdf.ui.glass.InkProbe(Modifier, c.icon)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val findLabel = stringResource(R.string.viewer_find_in_document)
    LaunchedEffect(Unit) {
        // Let the pill morph first; opening the IME immediately resizes the whole document viewport.
        delay(180)
        focus.requestFocus()
    }
    // the field is the title pill stretched over Share and More (their group and its gap, 96 dp), on
    // a damped glass spring; its words come in once it has room and leave first
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.graphicsLayer { alpha = progress().coerceIn(0f, 1f) }) {
            com.chethan616.clearpdf.ui.glass.GlassBackButton(onClose, label = stringResource(R.string.viewer_collapse), immediate = true)
        }
        Row(
            Modifier.weight(1f).height(48.dp)
                .layout { m, cs ->
                    val full = cs.maxWidth
                    val start = (full - 96.dp.roundToPx()).coerceAtLeast(full / 2)
                    val p = progress().coerceIn(0f, 1f)
                    val w = (start + (full - start) * p).toInt().coerceIn(1, full)
                    val placeable = m.measure(cs.copy(minWidth = w, maxWidth = w))
                    layout(full, placeable.height) { placeable.place(0, 0) }
                }
                .then(ink.modifier)
                .glassBar(surface = c.surfaceRaised.copy(alpha = if (c.dark) 0.92f else 0.86f)).padding(start = 16.dp, end = 4.dp)
                .graphicsLayer { alpha = ((progress().coerceIn(0f, 1f) - 0.35f) / 0.45f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalGlassInk provides ink.color) {
                Icon(LucideIcons.Search, null, tint = ink.color.copy(alpha = 0.70f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = ClearType.Body.copy(color = ink.color),
                    cursorBrush = SolidColor(Candy.Tomato),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        onSubmit()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = findLabel },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(findLabel, style = ClearType.Body, color = ink.color.copy(alpha = 0.48f), maxLines = 1)
                            inner()
                        }
                    },
                )
                if (count != null) Text(count, style = ClearType.Label, color = ink.color.copy(alpha = 0.68f), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                if (query.isNotEmpty()) StepButton(LucideIcons.X, stringResource(R.string.common_clear), true, onClear)
                StepButton(LucideIcons.ChevronUp, stringResource(R.string.viewer_find_previous), canStep, onPrev)
                StepButton(LucideIcons.ChevronDown, stringResource(R.string.viewer_find_next), canStep, onNext)
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).pressable(onClick, enabled = enabled).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        val ink = LocalGlassInk.current.takeIf { it != Color.Unspecified } ?: clear.ink
        Icon(icon, null, tint = ink.copy(alpha = if (enabled) 0.82f else 0.30f), modifier = Modifier.size(19.dp))
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
    moreTools: List<QuickTool> = emptyList(),
    onCustomize: ((Rect) -> Unit)? = null,
) {
    val slots = dockSlots(LocalConfiguration.current.screenWidthDp)
    val shown = tools.take(slots)
    // The four-dot control is an anchored action menu, not another bottom sheet.  Keep the
    // clipped-off toolbar actions in the same menu so every action remains reachable on phones.
    val overflow = remember(tools, moreTools, slots) { (tools.drop(slots) + moreTools).distinct() }
    var menuAt by remember { mutableStateOf<Rect?>(null) }
    var moreRect by remember { mutableStateOf(Rect.Zero) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (menuAt != null) 45f else 0f, com.chethan616.clearpdf.ui.glass.Fluid.quick(), label = "more")
    Row(
        Modifier.fillMaxWidth().height(BottomBarHeight).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // iOS 26's toolbar: one glass capsule of plain glyphs in the glass's ink (no rainbow, no captions:
        // each glyph names itself to TalkBack), More tools in a glass bubble of its own beside it
        val c = pdfColors
        Row(Modifier.weight(1f).fillMaxHeight().glassBar(surface = c.surfaceRaised.copy(alpha = if (c.dark) 0.90f else 0.84f)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(com.chethan616.clearpdf.ui.glass.LocalGlassInk provides c.icon) {
                for (t in shown) DockItem(t.icon, stringResource(t.label)) { onTool(t) }
            }
        }
        // Office documents have their own concise toolbar. PDFs use the menu for the remaining
        // tools rather than opening the old grid sheet.
        if (overflow.isNotEmpty() || onCustomize != null) GlassCircle(
            onClick = { menuAt = moreRect },
            size = 60.dp,
            label = stringResource(R.string.common_tool_more_tools),
            modifier = Modifier.onGloballyPositioned { moreRect = it.boundsInWindow() },
        ) {
            GlassIcon(LucideIcons.LayoutGrid, null, Modifier.size(23.dp).androidxRotate(turn))
        }
    }
    menuAt?.let { anchor ->
        GlassMenu(anchor, { menuAt = null }, buildList {
            overflow.forEach { tool -> add(GlassMenuItem(stringResource(tool.title), tool.icon) { onTool(tool) }) }
            if (onCustomize != null) add(GlassMenuItem(stringResource(R.string.toolbar_customize), LucideIcons.SlidersHorizontal) { onCustomize(anchor) })
        })
    }
}

private fun Modifier.androidxRotate(degrees: Float) = this.graphicsLayer { rotationZ = degrees }

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight().pressable(onClick, pressedScale = 0.86f).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlassIcon(icon, null, Modifier.size(24.dp)) }
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
        val c = pdfColors
        Row(
            Modifier.widthIn(max = maxWidth).fillMaxHeight().glassBar(surface = c.surfaceRaised.copy(alpha = if (c.dark) 0.90f else 0.84f)).horizontalScroll(scroll).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) { CompositionLocalProvider(LocalSubToolbarScroll provides scroll, com.chethan616.clearpdf.ui.glass.LocalGlassInk provides c.icon) { content() } }
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
            selected -> (com.chethan616.clearpdf.ui.glass.LocalGlassInk.current.takeIf { it != Color.Unspecified } ?: c.ink).copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        GlassMotion.settle(), label = "sub",
    )
    Box(
        Modifier.width(width).fillMaxHeight().slideToWhenChosen(vivid).pressable(onClick, enabled = enabled, pressedScale = 0.86f).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(disc))
        val ink = com.chethan616.clearpdf.ui.glass.LocalGlassInk.current.takeIf { it != Color.Unspecified } ?: c.ink
        glyph(if (!enabled) ink.copy(alpha = 0.38f) else if (vivid) Color.White else ink)
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

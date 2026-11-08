package com.chethan616.clearpdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.glass.GlassCard
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassOverlay
import com.chethan616.clearpdf.ui.glass.GlassToggle
import com.chethan616.clearpdf.ui.glass.Fluid
import com.chethan616.clearpdf.ui.glass.glass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.glass.Capsule
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Motion for sheets and bars. */
object PdfMotion {
    const val SHEET_IN_MS = 250
    const val SHEET_OUT_MS = 200
    const val BARS_MS = 300
}

/**
 * The candy an icon wears in rows and menus: chosen from the icon itself, so one action keeps one
 * colour everywhere it appears. (Lemon and lime are left out: too pale behind a glyph.)
 */
fun iconAccent(icon: ImageVector): Color {
    val palette = listOf(Candy.Tomato, Candy.Tangerine, Candy.Mint, Candy.Sky, Candy.Grape, Candy.Bubblegum)
    return palette[(icon.name.hashCode() and 0x7fffffff) % palette.size]
}

/** An icon button: a jelly press, and a soft candy disc behind it when [selected]. */
@Composable
fun BarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = pdfColors.icon,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 46.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier
            .size(size)
            .pressable(onClick, enabled = enabled)
            .clip(CircleShape)
            .background(if (selected) Candy.Sky.copy(alpha = 0.18f) else Color.Transparent)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (!enabled) tint.copy(alpha = 0.32f) else if (selected) Candy.Sky else tint, modifier = Modifier.size(iconSize))
    }
}

/** A thin upright line between groups of a bar. */
@Composable
fun BarDivider(height: Dp = 26.dp) {
    Box(Modifier.width(1.dp).height(height).background(clear.line))
}

/** Whether the sheet being shown is coming in or going ([SheetHost] sets it). */
private val LocalSheetVisible = staticCompositionLocalOf<MutableTransitionState<Boolean>?> { null }

private class Last<T>(var value: T? = null)

/**
 * Shows [key]'s sheet ([content]; nothing while [key] is null), keeping the last one composed
 * while it animates out, so a sheet closed from anywhere leaves smoothly.
 */
@Composable
fun <T : Any> SheetHost(key: T?, content: @Composable (T) -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    val last = remember { Last<T>() }
    if (key != null) last.value = key
    SideEffect { visible.targetState = key != null }
    val shown = last.value
    if (shown != null && (visible.currentState || visible.targetState)) {
        CompositionLocalProvider(LocalSheetVisible provides visible) { content(shown) }
    }
}

/** The sheet being composed's way to close (its header's × uses it). */
private val LocalSheetDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The viewer's sheet, iOS-style: a frosted glass panel floating a little inside the screen's edges
 * with big continuous corners and a grabber, rising on the eased curve and sliding back down when
 * it closes (never springing past its place). It lives in the app's overlay portal, so it refracts
 * whatever is really behind it. A tap outside, Back, its header's ×, or pulling it down closes it.
 * [maxHeightFraction] caps its height.
 */
@Composable
fun PdfSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    maxHeightFraction: Float = 0.9f,
    /** Room kept free above the sheet however tall it grows. */
    topReserve: Dp = 0.dp,
    /** False when the sheet sits on a bar of its own (it then keeps clear of nothing below). */
    bottomBand: Boolean = true,
    /** A wash of its own instead of the usual frost. */
    background: Color? = null,
    handleHeight: Dp = 22.dp,
    aboveNavBar: Boolean = false,
    /** Whether the sheet always takes its full height. */
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val own = remember { MutableTransitionState(false) }
    LaunchedEffect(own) { own.targetState = true }
    val visible = LocalSheetVisible.current ?: own
    GlassOverlay(animatesOut = true) {
        val c = clear
        // Viewer sheets are glass over the actual document. Keep their wash neutral, so a PDF or
        // image supplies the reflected colour instead of the dark theme's old indigo frost.
        val sheetWash = background?.copy(alpha = 0.9f) ?: if (c.dark) Color(0xFF0D0D10).copy(alpha = 0.64f) else Color.White.copy(alpha = 0.76f)
        val presence = com.chethan616.clearpdf.ui.glass.LocalOverlayPresence.current
        val leaving = presence?.leaving == true
        // shown on its own (not through a SheetHost), it slides away once whoever showed it is gone
        LaunchedEffect(leaving) {
            if (!leaving) return@LaunchedEffect
            own.targetState = false
            androidx.compose.runtime.snapshotFlow { visible.isIdle && !visible.currentState }.first { it }
            presence?.done()
        }
        BackHandler(enabled = visible.targetState && !leaving, onBack = onDismiss)
        val scope = rememberCoroutineScope()
        var drag by remember { mutableFloatStateOf(0f) }
        var height by remember { mutableIntStateOf(0) }
        val closeLabel = stringResource(R.string.ui_close_sheet)
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible, enter = fadeIn(Fluid.quick()), exit = fadeOut(Fluid.dismiss())) {
                Box(
                    Modifier.fillMaxSize()
                        .background(if (dim) Color.Black.copy(alpha = if (c.dark) 0.42f else 0.2f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                        .semantics { contentDescription = closeLabel },
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val maxH = minOf(maxHeight * maxHeightFraction, maxHeight - topReserve)
                AnimatedVisibility(
                    visible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(Fluid.rise()) { it + 40 },
                    exit = slideOutVertically(Fluid.dismiss()) { it + 40 },
                ) {
                    GlassCard(
                        modifier
                            .then(if (bottomBand) Modifier.navigationBarsPadding() else Modifier)
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                            .widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .heightIn(max = maxH)
                            .offset { IntOffset(0, drag.roundToInt()) }
                            .onSizeChanged { height = it.height }
                            .pointerInput(Unit) { detectTapGestures() },
                        shape = RoundedRectangle(34.dp),
                        wash = sheetWash,
                        blur = 18.dp,
                    ) {
                        val haptics = com.chethan616.clearpdf.ui.glass.LocalHaptics.current
                        Box(
                            Modifier.fillMaxWidth().height(handleHeight).pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (drag > height / 4f) onDismiss()
                                            animate(drag, 0f, animationSpec = Fluid.follow()) { v, _ -> drag = v }
                                        }
                                    },
                                    onVerticalDrag = { _, dy ->
                                        val before = drag > height / 4f
                                        drag = (drag + dy).coerceAtLeast(0f)
                                        // a tick as the pull passes the point where letting go closes it
                                        if (before != drag > height / 4f) haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Tick)
                                    },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(36.dp, 5.dp).clip(Capsule).background(c.ink.copy(alpha = if (c.dark) 0.28f else 0.18f)))
                        }
                        CompositionLocalProvider(LocalSheetDismiss provides onDismiss) {
                            Column(Modifier.weight(1f, fill = fill)) { content() }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/** The wash of a group of rows on a sheet: white cells on the frost, as iOS groups them. */
@Composable
private fun sectionFill(): Color = if (clear.dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.66f)

/**
 * A group of rows on a sheet, as iOS sets them out: one rounded cell, inset from the sheet's sides,
 * with a hairline between rows that starts where the labels do ([inset]).
 */
@Composable
fun SheetSection(modifier: Modifier = Modifier, inset: Dp = 58.dp, content: @Composable () -> Unit) {
    val line = clear.ink.copy(alpha = if (clear.dark) 0.14f else 0.09f)
    // where each row ends, written while measuring and read while drawing (no state: draw follows layout)
    val bounds = remember { IntArray(64) }
    val count = remember { IntArray(1) }
    androidx.compose.ui.layout.Layout(
        content,
        modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedRectangle(22.dp))
            .background(sectionFill())
            .drawWithContent {
                drawContent()
                val x = inset.toPx()
                val w = 0.7.dp.toPx()
                for (i in 0 until count[0]) {
                    val y = bounds[i] - w / 2f
                    drawLine(line, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(size.width, y), w)
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }.filter { it.height > 0 }
        var y = 0
        count[0] = 0
        placeables.forEachIndexed { i, p ->
            y += p.height
            if (i < placeables.size - 1 && count[0] < bounds.size) bounds[count[0]++] = y
        }
        layout(constraints.maxWidth, y) {
            var top = 0
            placeables.forEach { it.place(0, top); top += it.height }
        }
    }
}

/**
 * A row of a sheet, as iOS Settings draws one: its icon white on a small rounded square of the
 * icon's own candy, the label, then [trailing] (a chevron for drill-in rows, a tick when [checked]).
 */
@Composable
fun SheetRow(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    checked: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    trailingInteractive: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = clear
    val danger = tint != null && tint == pdfColors.red
    val accent = when {
        danger -> Candy.Tomato
        tint != null -> tint
        icon != null -> iconAccent(icon)
        else -> Candy.Sky
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp)
            .then(if (trailingInteractive) Modifier else Modifier.pressable(onClick, enabled = enabled, pressedScale = 0.985f))
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (danger) Candy.Tomato else c.ink
        Row(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (trailingInteractive) Modifier.pressable(onClick, enabled = enabled, pressedScale = 0.985f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(30.dp).clip(RoundedRectangle(9.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(accent.copy(alpha = 0.86f), accent)))
                        .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(14.dp))
            }
            Text(
                label, style = ClearType.Body.copy(fontSize = 16.sp), color = if (enabled) ink else ink.copy(alpha = 0.38f),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 7.dp),
            )
        }
        if (trailing != null) trailing()
        if (checked) Icon(LucideIcons.Check, null, tint = Candy.Sky, modifier = Modifier.size(21.dp))
        if (chevron) Icon(LucideIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.size(19.dp))
    }
}

/**
 * A small round button on a sheet (back, close, a header action): a soft disc with the glyph, no
 * glass of its own (glass inside the sheet's glass would read as one button stacked on another).
 */
@Composable
fun SheetCircleButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, size: Dp = 36.dp) {
    val c = clear
    Box(
        modifier.size(size).clip(CircleShape).background(if (c.dark) Color.White.copy(alpha = 0.10f) else c.ink.copy(alpha = 0.06f))
            .pressable(onClick, pressedScale = 0.88f).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (tint == Color.Unspecified) c.inkSoft else tint, modifier = Modifier.size(size * 0.5f)) }
}

/**
 * A sheet's header, as iOS 26 sets one: the title centred, a round back button on the left when
 * [onBack], and on the right [trailing], else a round × that closes the sheet.
 */
@Composable
fun SheetHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val c = clear
    val dismiss = LocalSheetDismiss.current
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.widthIn(min = 36.dp)) {
            if (onBack != null) SheetCircleButton(LucideIcons.ChevronLeft, stringResource(R.string.common_back), onBack)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = ClearType.Title.copy(fontSize = 17.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (subtitle != null) Text(subtitle, style = ClearType.Caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.widthIn(min = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (trailing != null) trailing()
            else if (dismiss != null) SheetCircleButton(LucideIcons.X, stringResource(R.string.ui_close_sheet), dismiss)
        }
    }
}

/** A sub-sheet's header: back on the left, the title centred. */
@Composable
fun SheetBackHeader(title: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) = SheetHeader(title, onBack, trailing = trailing)

/** A sheet's title, centred, with the round × to close it. */
@Composable
fun SheetTitle(title: String) = SheetHeader(title)

/** A hairline between groups of rows. */
@Composable
fun SheetDivider(inset: Dp = 16.dp, vertical: Dp = 0.dp) {
    Box(Modifier.padding(horizontal = inset + 4.dp, vertical = vertical + 4.dp).fillMaxWidth().height(0.7.dp).background(clear.line))
}

/** Between groups of rows: air, as iOS leaves between its grouped cells. */
@Composable
fun GroupDivider() = Spacer(Modifier.height(14.dp))

/** A hint over the page ("Drag across the text to highlight it"): a frosted glass capsule that fades in. */
@Composable
fun BoxScope.HintToast(text: String?, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center, corner: Dp = 4.dp) {
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier.align(alignment),
        enter = fadeIn(Fluid.quick()) + scaleIn(com.chethan616.clearpdf.ui.glass.GlassMotion.morph(), initialScale = 0.88f),
        exit = fadeOut(Fluid.dismiss()) + scaleOut(Fluid.dismiss(), targetScale = 0.96f),
    ) {
        val last = remember { Last<String>() }
        if (text != null) last.value = text
        Text(
            last.value ?: "",
            style = ClearType.Label,
            color = clear.ink,
            modifier = Modifier.padding(horizontal = 24.dp).glass(Capsule, clear.frost, blur = 12.dp).padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** A switch: the glass toggle. */
@Composable
fun PdfSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    GlassToggle(checked, onChange, modifier, accent = Candy.Sky)
}

/**
 * Top padding for a full screen: the status bar, or the camera cutout while the viewer keeps the
 * status bar hidden.
 */
fun Modifier.safeTopPadding(): Modifier = composed {
    windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
}

/** A dialog: the glass dialog, with the candy confirm (tomato when [destructive]). */
@Composable
fun PdfDialog(
    onDismiss: () -> Unit,
    title: String?,
    confirm: String?,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismiss: String? = stringResource(R.string.common_cancel),
    onDismissButton: () -> Unit = onDismiss,
    destructive: Boolean = false,
    /** A list of choices: no buttons of its own, usually. */
    list: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // the viewer's dialogs are alerts: centred, the buttons side by side across the card
    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        confirm = confirm,
        onConfirm = onConfirm,
        dismiss = dismiss,
        onDismissButton = onDismissButton,
        confirmEnabled = confirmEnabled,
        destructive = destructive,
        centered = !list,
    ) {
        content?.invoke(this)
    }
}

/** A dialog's text field: a soft frosted well, tomato when wrong, with an eye for passwords. */
@Composable
fun PdfField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focus: FocusRequester? = null,
) {
    val c = clear
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedRectangle(16.dp))
            .background(if (error) Candy.Tomato.copy(alpha = 0.14f) else if (c.dark) Color.White.copy(alpha = 0.07f) else c.ink.copy(alpha = 0.05f))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = ClearType.Body, color = if (error) Candy.Tomato else c.inkFaint)
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = ClearType.Body.copy(color = c.ink),
                cursorBrush = SolidColor(Candy.Sky),
                visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).semantics { contentDescription = placeholder },
            )
        }
        if (password) {
            BarIcon(
                if (visible) LucideIcons.Eye else LucideIcons.EyeOff, stringResource(if (visible) R.string.ui_hide_password else R.string.ui_show_password),
                { visible = !visible }, size = 46.dp, iconSize = 20.dp, tint = c.inkSoft,
            )
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

/**
 * A short label under an icon (toolbars, tool tiles), centred, on up to [maxLines] lines that
 * break only between words. A word too wide for the room (a large font size setting) makes the
 * whole label a little smaller, down to [minSize], rather than being split.
 */
@Composable
fun WordWrapLabel(
    text: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    minSize: androidx.compose.ui.unit.TextUnit = 7.sp,
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.material3.LocalTextStyle.current.merge(
        androidx.compose.ui.text.TextStyle(
            color = color, fontSize = size, lineHeight = lineHeight, letterSpacing = letterSpacing,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        ),
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val room = constraints.maxWidth
        val scale = remember(text, style, room) {
            val widest = text.split(' ').filter { it.isNotBlank() }
                .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            if (room == androidx.compose.ui.unit.Constraints.Infinity || widest <= room || widest == 0) 1f
            else (room.toFloat() / widest * 0.97f).coerceAtLeast(minSize.value / size.value)
        }
        Text(text, style = style.copy(fontSize = size * scale, lineHeight = lineHeight * scale), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

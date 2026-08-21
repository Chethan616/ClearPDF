package com.chethan616.clearpdf.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.DestructiveGlassButton
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Page-jump popup rendered **in-window** (not a [Dialog]) so the liquid-glass panel
 * can actually sample the viewer backdrop instead of falling back to a grey box.
 * Enters with a smooth Apple-style glass "pop" (scale + fade, gentle damping) and
 * dims the page behind it with a tap-to-dismiss scrim.
 */
@Composable
internal fun LiquidPageJumpPopup(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette (dark ink on light pages, white on dark pages).
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var targetText by remember(visible, currentPage) { mutableStateOf((currentPage + 1).toString()) }

    Box(Modifier.fillMaxSize()) {
        // Dimming scrim — tap outside to dismiss.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        // Glass popup panel — springy but non-bouncy scale-in.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                    ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center).imePadding()
        ) {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.78f)
                    .liquidGlassPanel(backdrop, uiSensor, surface)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    stringResource(R.string.viewer_jump_to_page),
                    style = TextStyle(fg, 16.sp, fontWeight = FontWeight.Bold)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() } },
                        textStyle = TextStyle(fg, 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        singleLine = true,
                        cursorBrush = SolidColor(fg),
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(field)
                            .border(1.dp, fg.copy(0.22f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )

                    BasicText(
                        "/ $pageCount",
                        style = TextStyle(fgSoft, 16.sp, fontWeight = FontWeight.Medium)
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                        BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp, FontWeight.Medium))
                    }
                    LiquidButton(
                        onClick = {
                            val p = targetText.toIntOrNull()?.minus(1)?.coerceIn(0, pageCount - 1)
                            if (p != null) onJumpToPage(p)
                        },
                        backdrop = backdrop,
                        tint = Color(0xFF1976D2)
                    ) {
                        BasicText(stringResource(R.string.viewer_go), style = TextStyle(Color.White, 13.sp, FontWeight.Bold))
                    }
                }
            }
        }
    }
}

/**
 * Editor for an inserted text box or sticky note. Rendered IN-WINDOW (not a [Dialog])
 * so the glass panel samples the real page content instead of the wallpaper PNG.
 * Adaptive colours keep it readable over any page; fade-only so glass never re-blurs
 * mid-transition. Lifts above the keyboard via [imePadding].
 */
@Composable
internal fun AnnotationEditorDialog(
    isNote: Boolean,
    initialText: String,
    initialColor: Color,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, Color) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; delay(160); runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().imePadding()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 440.dp)
                        .liquidGlassPanel(backdrop, uiSensor, surface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicText(
                        if (isNote) stringResource(R.string.anno_note_title) else stringResource(R.string.anno_text_title),
                        style = TextStyle(fg, 16.sp, fontWeight = FontWeight.Bold)
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(field)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (text.isEmpty()) {
                            BasicText(
                                stringResource(R.string.anno_hint),
                                style = TextStyle(fgSoft, 14.sp)
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(fg, 14.sp),
                            cursorBrush = SolidColor(fg),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    }

                    // Colour picker — recolour the text / sticky note.
                    AnnotationColorRow(selected = color, fgSoft = fgSoft, onPick = { color = it })

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DestructiveGlassButton(stringResource(R.string.delete), onDelete, backdrop)
                        LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                            BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp))
                        }
                        LiquidButton(onClick = { onSave(text, color) }, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                            BasicText(stringResource(R.string.anno_save), style = TextStyle(Color.White, 13.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

/** Which file the viewer's Share action should hand off. */
internal enum class ShareFormat { ORIGINAL, PDF }

/**
 * Share/export chooser, rendered in-window in the same glass family as [LiquidPageJumpPopup]. For a
 * converted document (a .docx opened as a PDF) it offers the original file *or* a PDF via a bouncy
 * [LiquidGlassDropdown]; when PDF is the target it can encrypt with a password. A plain PDF skips the
 * format row and just offers the encrypt toggle.
 *
 * The dialog only collects intent — the actual file work (encrypt, wrap, chooser) happens off the UI
 * thread in the caller.
 */
@Composable
internal fun ExportShareDialog(
    visible: Boolean,
    // Uppercase token for the original file when it isn't a PDF (e.g. "DOCX"); null for a plain PDF.
    originalExt: String?,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onShare: (format: ShareFormat, encrypt: Boolean, password: String) -> Unit
) {
    // Keyed on `visible` so every open starts fresh.
    var formatIndex by remember(visible) { mutableStateOf(0) }
    var encrypt by remember(visible) { mutableStateOf(false) }
    var password by remember(visible) { mutableStateOf("") }
    var dropdownOpen by remember(visible) { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val density = LocalDensity.current

    val pdfOnly = originalExt == null
    val pdfSelected = pdfOnly || formatIndex == 1
    val canShare = !(pdfSelected && encrypt && password.isBlank())
    val options = if (pdfOnly) emptyList() else listOf(originalExt, stringResource(R.string.viewer_share_pdf))

    // Trigger anchor in the dialog Box's coordinate space, so the dropdown menu is an OVERLAY that
    // never grows the glass panel. Growing that panel re-runs its blur+lens every frame — the exact
    // "expand/minimize lag" being fixed here.
    var boxWin by remember { mutableStateOf(Offset.Zero) }
    var trigWin by remember { mutableStateOf(Offset.Zero) }
    var trigSize by remember { mutableStateOf(IntSize.Zero) }

    val chevron by animateFloatAsState(if (dropdownOpen) 180f else 0f, GlassMotion.morph(), label = "shareDropChevron")

    LaunchedEffect(encrypt, pdfSelected) {
        if (encrypt && pdfSelected) { delay(120); runCatching { passwordFocus.requestFocus() } }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center).imePadding()
        ) {
            Box(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.86f)
                    .onGloballyPositioned { boxWin = it.positionInWindow() }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor, surface)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BasicText(
                        stringResource(R.string.viewer_share_title),
                        style = TextStyle(fg, 17.sp, fontWeight = FontWeight.Bold)
                    )

                    if (!pdfOnly) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicText(
                                stringResource(R.string.viewer_share_format),
                                style = TextStyle(fgSoft, 12.sp, fontWeight = FontWeight.Medium)
                            )
                            // Trigger only — the menu is drawn as a dialog overlay below (so the panel
                            // never resizes). Reports its window position + size to anchor that menu.
                            LiquidButton(
                                onClick = { dropdownOpen = !dropdownOpen },
                                backdrop = backdrop,
                                surfaceColor = field,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { trigWin = it.positionInWindow(); trigSize = it.size }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    BasicText(
                                        options.getOrElse(formatIndex) { "" },
                                        style = TextStyle(fg, 14.sp, fontWeight = FontWeight.SemiBold)
                                    )
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        null,
                                        Modifier.size(20.dp).graphicsLayer { rotationZ = chevron },
                                        fgSoft
                                    )
                                }
                            }
                        }
                    }

                    if (pdfSelected) {
                        // Segmented Normal | Encrypted. Deterministic — a plain tap on `LiquidToggle`
                        // fires both its clickable and its drag-stop, cancelling out, so it could get
                        // stuck on and never prompt for a password. Two buttons set a definite value.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(false to R.string.viewer_share_normal, true to R.string.viewer_share_encrypted)
                                .forEach { (enc, labelRes) ->
                                    val sel = encrypt == enc
                                    LiquidButton(
                                        onClick = { encrypt = enc },
                                        backdrop = backdrop,
                                        tint = if (sel) Color(0xFF1976D2) else Color.Unspecified,
                                        surfaceColor = if (sel) Color.Unspecified else field,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        BasicText(
                                            stringResource(labelRes),
                                            style = TextStyle(if (sel) Color.White else fg, 13.sp, fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                        }
                        if (encrypt) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(field)
                                    .border(1.dp, fg.copy(0.18f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                if (password.isEmpty()) {
                                    BasicText(
                                        stringResource(R.string.viewer_share_password_hint),
                                        style = TextStyle(fgSoft, 14.sp)
                                    )
                                }
                                BasicTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fg, 14.sp),
                                    cursorBrush = SolidColor(fg),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus)
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                            BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp, FontWeight.Medium))
                        }
                        LiquidButton(
                            onClick = {
                                if (canShare) onShare(
                                    if (pdfSelected) ShareFormat.PDF else ShareFormat.ORIGINAL,
                                    encrypt && pdfSelected,
                                    password
                                )
                            },
                            backdrop = backdrop,
                            tint = if (canShare) Color(0xFF1976D2) else Color(0xFF1976D2).copy(0.4f)
                        ) {
                            BasicText(stringResource(R.string.viewer_share_button), style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                // Tap-outside catcher for an open dropdown. `matchParentSize` reads the panel's size
                // without contributing to it, so it doesn't grow the dialog.
                if (dropdownOpen) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) { detectTapGestures { dropdownOpen = false } }
                    )
                }

                // Dropdown menu overlay — anchored under the trigger, drawn last (on top), and NOT
                // wrapped by the glass panel, so opening/closing it costs nothing on the panel. The
                // bounce is all draw-time (a `pop()` spring driving alpha + a translationY overshoot +
                // a small scaleY grow), so the menu's own glass never re-blurs while it springs.
                if (!pdfOnly && (dropdownOpen || trigSize != IntSize.Zero)) {
                    val menuOffset = IntOffset(
                        (trigWin.x - boxWin.x).roundToInt(),
                        (trigWin.y - boxWin.y).roundToInt() + trigSize.height + with(density) { 6.dp.roundToPx() }
                    )
                    val menuWidth = with(density) { trigSize.width.toDp() }
                    AnimatedVisibility(
                        visible = dropdownOpen,
                        enter = fadeIn(tween(110)),
                        exit = fadeOut(tween(90)),
                        modifier = Modifier.align(Alignment.TopStart).offset { menuOffset }.zIndex(1f)
                    ) {
                        val reveal by transition.animateFloat(
                            transitionSpec = {
                                if (targetState == EnterExitState.Visible) GlassMotion.pop() else GlassMotion.settle()
                            },
                            label = "shareMenuReveal"
                        ) { if (it == EnterExitState.Visible) 1f else 0f }
                        Column(
                            Modifier
                                .width(menuWidth)
                                .graphicsLayer {
                                    alpha = reveal.coerceIn(0f, 1f)
                                    translationY = (1f - reveal) * (-14.dp.toPx())
                                    scaleY = 0.9f + 0.1f * reveal
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                }
                                .liquidGlassPanel(backdrop, uiSensor, field)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            options.forEachIndexed { i, opt ->
                                val selected = i == formatIndex
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { formatIndex = i; dropdownOpen = false }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    BasicText(
                                        opt,
                                        style = TextStyle(fg, 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                                    )
                                    if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), fg)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Shared annotation / shape colour palette (dark inks read well on white pages; a few
// vivid accents for shapes and notes).
internal val editorPalette: List<Color> = listOf(
    Color(0xFF1A1A1A), // near-black ink
    Color(0xFF1976D2), // blue
    Color(0xFFE53935), // red
    Color(0xFF43A047), // green
    Color(0xFFFB8C00), // orange
    Color(0xFF8E24AA), // purple
    Color(0xFF00ACC1), // teal
    Color(0xFFFFC107)  // amber (notes)
)

/** A horizontal row of tappable colour beads; the selected one gets a ring. */
@Composable
internal fun AnnotationColorRow(
    selected: Color,
    fgSoft: Color,
    onPick: (Color) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(stringResource(R.string.viewer_color), style = TextStyle(fgSoft, 12.sp, FontWeight.Medium))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            editorPalette.forEach { c ->
                val isSel = c.value == selected.value
                Box(
                    Modifier
                        .size(if (isSel) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (isSel) 2.5.dp else 1.dp,
                            color = if (isSel) Color.White else Color.White.copy(0.25f),
                            shape = CircleShape
                        )
                        .clickable { onPick(c) }
                )
            }
        }
    }
}

/**
 * Contextual editor for a placed shape (rect / oval / line / arrow / stroke). Rendered
 * IN-WINDOW so its glass panel samples the real page. Recolours live as the user taps a
 * bead, and offers Delete / Done. Same visual family as [AnnotationEditorDialog].
 */
@Composable
internal fun ShapeEditorPopup(
    initialColor: Color,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onColorChange: (Color) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableStateOf(initialColor) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().imePadding()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 440.dp)
                        .liquidGlassPanel(backdrop, uiSensor, surface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BasicText(
                        stringResource(R.string.viewer_edit_shape),
                        style = TextStyle(fg, 16.sp, fontWeight = FontWeight.Bold)
                    )

                    AnnotationColorRow(selected = color, fgSoft = fgSoft, onPick = { color = it; onColorChange(it) })

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DestructiveGlassButton(stringResource(R.string.delete), onDelete, backdrop)
                        LiquidButton(onClick = onDismiss, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                            BasicText(stringResource(R.string.viewer_done), style = TextStyle(Color.White, 13.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

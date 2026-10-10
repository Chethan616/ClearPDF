package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.components.glassDialogPlatter
import com.chethan616.clearpdf.ui.components.glassDialogInkSoft
import com.chethan616.clearpdf.ui.components.glassDialogInk
import com.chethan616.clearpdf.ui.components.GlassDialogSegmented
import com.chethan616.clearpdf.ui.components.GlassDropdownOption
import com.chethan616.clearpdf.ui.components.LiquidGlassDropdown
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextSnippet
import com.chethan616.clearpdf.ui.components.GlassDialogField
import com.chethan616.clearpdf.ui.components.GlassDialogAction
import com.chethan616.clearpdf.ui.components.GlassDialog
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
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
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
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
    bookmarkedPages: List<Int>,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onToggleCurrentBookmark: () -> Unit,
    onRemoveBookmark: (Int) -> Unit
) {
    // The app's one dialog: GlassDialog (the "Save changes?" card), so every reader dialog shares its
    // entrance, scrim, material and action pills. Its ink follows the app theme, never the page under
    // it — the old popup took its text colour from the page and its button fill from elsewhere, which
    // is how "Cancel" ended up white-on-white.
    var targetText by remember(visible, currentPage) { mutableStateOf((currentPage + 1).toString()) }
    val focus = remember { FocusRequester() }
    val ink = glassDialogInk()
    val soft = glassDialogInkSoft()
    val marked = currentPage in bookmarkedPages
    fun go() {
        targetText.toIntOrNull()?.minus(1)?.coerceIn(0, (pageCount - 1).coerceAtLeast(0))?.let(onJumpToPage)
    }
    LaunchedEffect(visible) {
        if (visible) { delay(260); runCatching { focus.requestFocus() } }
    }
    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.viewer_jump_to_page),
        actions = {
            GlassDialogAction(stringResource(R.string.cancel), onDismiss)
            GlassDialogAction(stringResource(R.string.viewer_go), { go() }, primary = true)
        }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassDialogField(
                value = targetText,
                onValueChange = { targetText = it.filter { c -> c.isDigit() }.take(6) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                minHeight = 54.dp,
                focusRequester = focus
            )
            BasicText("/ $pageCount", style = TextStyle(soft, 18.sp, FontWeight.Medium))
        }
        Spacer(Modifier.height(12.dp))
        DialogRow(
            icon = if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            iconTint = if (marked) LiquidGlassColors.Orange else ink,
            label = stringResource(if (marked) R.string.viewer_bookmark_remove else R.string.viewer_bookmark_add),
            onClick = onToggleCurrentBookmark
        )
        if (bookmarkedPages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bookmarkedPages.forEach { page ->
                    DialogRow(
                        icon = Icons.Rounded.Bookmark,
                        iconTint = LiquidGlassColors.Orange,
                        label = stringResource(R.string.viewer_bookmark_page, page + 1),
                        onClick = { onJumpToPage(page) },
                        trailing = {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemoveBookmark(page) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.viewer_bookmark_remove), Modifier.size(16.dp), soft)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** A tappable row on a solid dialog platter: icon, label, optional trailing control. */
@Composable
private fun DialogRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val isDark = LocalIsDarkMode.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glassDialogPlatter(isDark))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), iconTint)
        BasicText(
            label,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            style = TextStyle(LiquidGlassColors.text(isDark), 15.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

/**
 * Editor for an inserted text box or sticky note, on the shared [GlassDialog] card (in-window, so the
 * glass samples the real page). Theme ink, solid field, vivid Save.
 */
@Composable
internal fun AnnotationEditorDialog(
    isNote: Boolean,
    initialText: String,
    initialColor: Color,
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    /** Text boxes only: the current size, adjustable with a slider (null hides it, e.g. notes). */
    initialFontSize: Float? = null,
    onSave: (String, Color, Float?) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableStateOf(initialColor) }
    var fontSize by remember { mutableStateOf(initialFontSize) }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; delay(260); runCatching { focus.requestFocus() } }

    GlassDialog(
        visible = shown,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = if (isNote) stringResource(R.string.anno_note_title) else stringResource(R.string.anno_text_title),
        actions = {
            GlassDialogAction(stringResource(R.string.delete), onDelete, destructive = true)
            GlassDialogAction(stringResource(R.string.cancel), onDismiss)
            GlassDialogAction(stringResource(R.string.anno_save), { onSave(text, color, fontSize) }, primary = true)
        }
    ) {
        GlassDialogField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.anno_hint),
            singleLine = false,
            minHeight = 96.dp,
            focusRequester = focus,
            textStyle = TextStyle(fontSize = 16.sp)
        )
        fontSize?.let { size ->
            // Exact sizing without dragging the handle — the precise way to resize (#48).
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(stringResource(R.string.anno_text_size), style = TextStyle(glassDialogInkSoft(), 12.sp, FontWeight.Medium))
                BasicText(size.roundToInt().toString(), style = TextStyle(glassDialogInk(), 12.sp, FontWeight.SemiBold))
            }
            com.chethan616.clearpdf.ui.components.LiquidSlider(
                value = { size },
                onValueChange = { fontSize = it },
                valueRange = 10f..200f,
                visibilityThreshold = 0.5f,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(14.dp))
        AnnotationColorRow(selected = color, backdrop = backdrop, fgSoft = glassDialogInkSoft(), onPick = { color = it })
    }
}

/**
 * In-place editor for one line of the page's own text. The new text is written back in the line's
 * original font on save (PdfTextEditor), so this is a single-line field, not a text box.
 */
@Composable
internal fun TextEditDialog(
    initialText: String,
    original: String,
    backdrop: LayerBackdrop,
    canRevert: Boolean,
    onDismiss: () -> Unit,
    onRevert: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; delay(260); runCatching { focus.requestFocus() } }
    GlassDialog(
        visible = shown,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.text_edit_title),
        actions = {
            if (canRevert) GlassDialogAction(stringResource(R.string.text_edit_revert), onRevert, destructive = true)
            GlassDialogAction(stringResource(R.string.cancel), onDismiss)
            GlassDialogAction(stringResource(R.string.viewer_done), { onSave(text) }, primary = true)
        }
    ) {
        BasicText(stringResource(R.string.text_edit_hint), style = TextStyle(glassDialogInkSoft(), 12.sp, lineHeight = 16.sp))
        Spacer(Modifier.height(10.dp))
        GlassDialogField(
            value = text,
            onValueChange = { text = it.replace('\n', ' ') },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            focusRequester = focus,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) })
        )
        if (text != original) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.text_edit_original, original),
                style = TextStyle(glassDialogInkSoft(), 11.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Which file the viewer's Share action should hand off. */
internal enum class ShareFormat { ORIGINAL, PDF, IMAGES, TEXT }

/**
 * Share/export chooser on the shared [GlassDialog] card. For a converted document (a .docx opened as
 * a PDF) it offers the original file or a PDF in a [LiquidGlassDropdown]; when PDF is the target it
 * can encrypt with a password, chosen on a [GlassDialogSegmented]. Both controls sit on the solid
 * dialog platter so every option is legible over any page (the old glass pills went blank on light
 * pages) while their lensed rims still refract the screen.
 *
 * The dialog only collects intent — the file work (encrypt, wrap, chooser) runs off the UI thread in
 * the caller.
 */
@Composable
internal fun ExportShareDialog(
    visible: Boolean,
    // Uppercase token for the original file when it isn't a PDF (e.g. "DOCX"); null for a plain PDF.
    originalExt: String?,
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onShare: (format: ShareFormat, encrypt: Boolean, password: String) -> Unit
) {
    val pdfOnly = originalExt == null
    // Keyed on `visible` so every open starts fresh.
    var format by remember(visible) { mutableStateOf(if (pdfOnly) ShareFormat.PDF else ShareFormat.ORIGINAL) }
    var encrypt by remember(visible) { mutableStateOf(false) }
    var password by remember(visible) { mutableStateOf("") }
    val passwordFocus = remember { FocusRequester() }
    val soft = glassDialogInkSoft()

    val pdfSelected = format == ShareFormat.PDF
    val canShare = !(pdfSelected && encrypt && password.isBlank())

    LaunchedEffect(encrypt, pdfSelected) {
        if (encrypt && pdfSelected) { delay(160); runCatching { passwordFocus.requestFocus() } }
    }

    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.viewer_share_title),
        actions = {
            GlassDialogAction(stringResource(R.string.cancel), onDismiss)
            GlassDialogAction(
                stringResource(R.string.viewer_share_button),
                {
                    if (canShare) onShare(format, encrypt && pdfSelected, password)
                },
                primary = true,
                enabled = canShare
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                stringResource(R.string.viewer_share_format),
                style = TextStyle(soft, 13.sp, FontWeight.Medium)
            )
            // The share sheet's original bouncy glass dropdown (v3), back on the shared component.
            // Edits travel with every format: PDF, page images and text are all made from the
            // edited document.
            val options = buildList {
                if (!pdfOnly) add(GlassDropdownOption(ShareFormat.ORIGINAL, originalExt.orEmpty(), icon = Icons.Rounded.Description))
                add(GlassDropdownOption(ShareFormat.PDF, stringResource(R.string.viewer_share_pdf), icon = Icons.Rounded.PictureAsPdf))
                add(GlassDropdownOption(ShareFormat.IMAGES, stringResource(R.string.viewer_share_images), icon = Icons.Rounded.Image))
                add(GlassDropdownOption(ShareFormat.TEXT, stringResource(R.string.viewer_share_text), icon = Icons.Rounded.TextSnippet))
            }
            LiquidGlassDropdown(
                options = options,
                selected = format,
                onSelect = { format = it },
                backdrop = backdrop,
                leadingIcon = options.firstOrNull { it.value == format }?.icon,
                triggerSurface = glassDialogPlatter(LocalIsDarkMode.current),
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(
                visible = pdfSelected,
                enter = fadeIn(tween(160)) + expandVertically(GlassMotion.settle()),
                exit = fadeOut(tween(120)) + shrinkVertically(GlassMotion.settle())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassDialogSegmented(
                        options = listOf(
                            stringResource(R.string.viewer_share_normal),
                            stringResource(R.string.viewer_share_encrypted)
                        ),
                        selectedIndex = if (encrypt) 1 else 0,
                        onSelect = { encrypt = it == 1 },
                        accent = if (encrypt) LiquidGlassColors.Indigo else LiquidGlassColors.Blue
                    )
                    AnimatedVisibility(
                        visible = encrypt,
                        enter = fadeIn(tween(160)) + expandVertically(GlassMotion.settle()),
                        exit = fadeOut(tween(120)) + shrinkVertically(GlassMotion.settle())
                    ) {
                        GlassDialogField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.viewer_share_password_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation(),
                            focusRequester = passwordFocus
                        )
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
    LiquidGlassColors.Blue,
    LiquidGlassColors.Red,
    LiquidGlassColors.Green,
    LiquidGlassColors.Orange,
    LiquidGlassColors.Purple,
    LiquidGlassColors.Teal,
    Color(0xFFFFCC00) // amber (notes)
)

/** A horizontal row of tappable colour beads; the selected one gets a ring. */
@Composable
internal fun AnnotationColorRow(
    selected: Color,
    backdrop: LayerBackdrop,
    fgSoft: Color,
    onPick: (Color) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(stringResource(R.string.viewer_color), style = TextStyle(fgSoft, 12.sp, FontWeight.Medium))
        // Every swatch fits the row (equal slots), and the selected one springs up with a ring.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(fgSoft.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            editorPalette.forEach { c ->
                val isSel = c.value == selected.value
                val s by animateFloatAsState(if (isSel) 1.12f else 1f, GlassMotion.pop(), label = "swatch")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // Tinted liquid glass bead, same as the draw tools' colour buttons.
                    LiquidIconButton(
                        onClick = { onPick(c) },
                        backdrop = backdrop,
                        tint = c,
                        modifier = Modifier.size(32.dp).graphicsLayer { scaleX = s; scaleY = s }
                    ) {
                        if (isSel) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), Color.White)
                    }
                }
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
    // The app's standard glass dialog: springy entrance, vivid red Delete + blue Done pills.
    GlassDialog(
        visible = shown,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.viewer_edit_shape),
        actions = {
            GlassDialogAction(stringResource(R.string.delete), onDelete, destructive = true)
            GlassDialogAction(stringResource(R.string.viewer_done), onDismiss, primary = true)
        }
    ) {
        AnnotationColorRow(selected = color, backdrop = backdrop, fgSoft = glassDialogInkSoft(), onPick = { color = it; onColorChange(it) })
    }
}

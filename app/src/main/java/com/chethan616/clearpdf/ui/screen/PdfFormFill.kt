package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.GlassOverlay
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.glassMenu
import com.chethan616.clearpdf.ui.components.liquidRowClick
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.pdfcore.form.PdfFormWidget
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The tint form boxes wear: the system's "fillable" blue. */
internal val FormTint = Color(0xFF0A84FF)

/** A widget's box in this page's content px. */
internal fun PdfFormWidget.rectIn(size: Size): Rect =
    Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

/** One PDF point in this page's content px. */
internal fun PdfFormWidget.ptPx(size: Size): Float = size.height / pageHeightPt.coerceAtLeast(1f)

/**
 * The value's text size in px: the field's own (DA) size, or, for an auto-sized field, the size
 * the saved form will use (PDFBox's rule): 12 pt when multi-line, else the largest size whose cap
 * height plus descent fills the box inside its 2 pt padding, and whose [text] still fits across.
 */
internal fun PdfFormWidget.textPx(size: Size, text: String): Float {
    if (fontSizeNorm > 0f) return fontSizeNorm * size.height
    val pt = ptPx(size)
    if (multiline || type == PdfFormWidget.Type.LIST) return 12f * pt
    val r = rectIn(size)
    val byHeight = (r.height - 4f * pt).coerceAtLeast(1f) / 0.925f
    if (text.isEmpty()) return byHeight
    val em = AutoSizePaint.measureText(text) / AutoSizePaint.textSize
    val byWidth = if (em > 0f) (r.width - 4f * pt).coerceAtLeast(1f) / em else byHeight
    return min(byHeight, byWidth)
}

private val AutoSizePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 100f
    typeface = android.graphics.Typeface.SANS_SERIF
}

private fun invert(c: Color) = Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha)

// ── List boxes ──────────────────────────────────────────────────────────────────────────────

/** A list box row's height in content px. */
internal fun PdfFormWidget.listRowPx(size: Size): Float = textPx(size, "") * 1.36f

/** How many list rows fit in the box. */
internal fun PdfFormWidget.listVisibleRows(size: Size): Int =
    max(1, floor((rectIn(size).height - 4f * ptPx(size)) / listRowPx(size)).toInt())

/** The first-row scroll that shows the selected option (in rows), like a PDF viewer opens it. */
internal fun PdfFormWidget.listInitialScroll(size: Size, value: String): Float {
    val visible = listVisibleRows(size)
    val maxScroll = max(0, options.size - visible).toFloat()
    val selected = optionValues.indexOf(value)
    if (selected < 0) return 0f
    return (selected - visible + 1).toFloat().coerceIn(0f, maxScroll)
}

/** The list option at content y [y] for scroll [scroll], or -1. */
internal fun PdfFormWidget.listIndexAt(size: Size, y: Float, scroll: Float): Int {
    val r = rectIn(size)
    val i = floor((y - r.top - 2f * ptPx(size)) / listRowPx(size) + scroll).toInt()
    return if (i in options.indices) i else -1
}

// ── Drawing ─────────────────────────────────────────────────────────────────────────────────

/** Liquid-glass form controls, drawn flat (no backdrop pass) so a dense form stays cheap. */
private class FormPalette(dark: Boolean) {
    val paper = if (dark) Color.Black else Color.White
    val fieldTop = if (dark) Color(0xFF2C323C) else Color(0xFFFCFDFF)
    val fieldBottom = if (dark) Color(0xFF1F242C) else Color(0xFFE8F1FC)
    val rim = if (dark) Color(0xFF56657A) else Color(0xFFA9C1DD)
    val highlight = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.95f)
    val vividTop = Color(0xFF5AAEFF)
    val vividBottom = FormTint
    val subtle = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
}

private fun DrawScope.glassBody(r: Rect, radius: Float, p: FormPalette, focused: Boolean, editing: Boolean, alpha: Float = 1f) {
    val cr = CornerRadius(radius)
    drawRoundRect(Brush.verticalGradient(listOf(p.fieldTop, p.fieldBottom), r.top, r.bottom), r.topLeft, r.size, cr, alpha = alpha)
    // Specular rim along the top edge: what makes a flat fill read as a pane of glass.
    if (r.width > radius * 2f) {
        drawLine(p.highlight, Offset(r.left + radius, r.top + 1.2f), Offset(r.right - radius, r.top + 1.2f), 1.4f, alpha = alpha)
    }
    drawRoundRect(p.rim, r.topLeft, r.size, cr, style = Stroke(1.2f), alpha = alpha)
    if (focused) {
        drawRoundRect(FormTint.copy(alpha = 0.20f), Offset(r.left - 3f, r.top - 3f), Size(r.width + 6f, r.height + 6f), CornerRadius(radius + 3f), style = Stroke(5f))
        drawRoundRect(FormTint, r.topLeft, r.size, cr, style = Stroke(2.2f))
    } else if (editing) {
        drawRoundRect(FormTint.copy(alpha = 0.5f), r.topLeft, r.size, cr, style = Stroke(1.4f))
    }
}

/** The glossy filled pill / bead of a ticked checkbox or chosen radio. */
private fun DrawScope.vividBody(r: Rect, radius: Float, p: FormPalette) {
    val cr = CornerRadius(radius)
    drawRoundRect(Brush.verticalGradient(listOf(p.vividTop, p.vividBottom), r.top, r.bottom), r.topLeft, r.size, cr)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.Transparent), r.top, r.top + r.height * 0.55f),
        Offset(r.left + r.width * 0.08f, r.top + r.height * 0.06f), Size(r.width * 0.84f, r.height * 0.48f), CornerRadius(radius * 0.8f)
    )
    drawRoundRect(Color(0xFF0060D0).copy(alpha = 0.6f), r.topLeft, r.size, cr, style = Stroke(1.1f))
}

/**
 * Draws this page's form fields as liquid-glass controls over the PDF's own boxes: frosted text
 * fields, glossy checkboxes and radios, a dropdown with its chevron, and scrolling list boxes with
 * the chosen row lit. Values are the ones filled in this session ([values]) or the PDF's own, in
 * the field's size, alignment and colour. [dark] is the dark reader, which inverts the page.
 */
internal fun DrawScope.drawFormWidgets(
    widgets: List<PdfFormWidget>,
    values: Map<String, String>,
    editing: Boolean,
    focusedKey: String?,
    dark: Boolean,
    listScroll: Map<String, Float>
) {
    val pal = FormPalette(dark)
    widgets.forEach { w ->
        val r = w.rectIn(size)
        if (r.width < 2f || r.height < 2f) return@forEach
        val focused = w.key == focusedKey
        val ink = Color(w.textArgb).let { if (dark) invert(it) else it }
        val value = values[w.fieldName] ?: w.value
        val alpha = if (w.readOnly) 0.7f else 1f
        // Hide the PDF's own appearance of the box; the glass control stands in for it. A field's
        // border is stroked on its edge, so half of it lies outside the box: cover that too, or
        // its square corners show around the rounded glass.
        val bleed = 1.5f * w.ptPx(size)
        drawRect(pal.paper, Offset(r.left - bleed, r.top - bleed), Size(r.width + bleed * 2f, r.height + bleed * 2f))
        when (w.type) {
            PdfFormWidget.Type.CHECKBOX, PdfFormWidget.Type.RADIO -> {
                val s = r.minDimension * 0.84f
                val box = Rect(r.center.x - s / 2f, r.center.y - s / 2f, r.center.x + s / 2f, r.center.y + s / 2f)
                val radius = if (w.type == PdfFormWidget.Type.RADIO) s / 2f else s * 0.26f
                val on = if (w.type == PdfFormWidget.Type.RADIO) value == w.onValue else value == "true"
                if (on) {
                    vividBody(box, radius, pal)
                    if (w.type == PdfFormWidget.Type.RADIO) {
                        drawCircle(Color.White, s * 0.18f, box.center)
                    } else {
                        val check = Path().apply {
                            moveTo(box.left + s * 0.25f, box.top + s * 0.53f)
                            lineTo(box.left + s * 0.43f, box.top + s * 0.70f)
                            lineTo(box.left + s * 0.76f, box.top + s * 0.31f)
                        }
                        drawPath(check, Color.White, style = Stroke(s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                } else {
                    glassBody(box, radius, pal, focused = false, editing = editing, alpha = alpha)
                }
            }
            PdfFormWidget.Type.TEXT -> {
                glassBody(r, min(r.height * 0.22f, 9f), pal, focused, editing, alpha)
                // The live editor draws the focused field's text itself.
                if (!focused && value.isNotEmpty()) drawFieldText(w, value, r, ink)
            }
            PdfFormWidget.Type.CHOICE -> {
                glassBody(r, min(r.height * 0.3f, 10f), pal, focused, editing, alpha)
                val chevronRoom = r.height * 0.9f
                val textRect = Rect(r.left, r.top, r.right - chevronRoom * 0.7f, r.bottom)
                if (value.isNotEmpty()) drawFieldText(w, w.displayOf(value), textRect, ink)
                val c = r.height * 0.13f
                val cx = r.right - chevronRoom / 2f
                val cy = r.center.y
                val chevron = Path().apply {
                    moveTo(cx - c, cy - c * 0.45f); lineTo(cx, cy + c * 0.55f); lineTo(cx + c, cy - c * 0.45f)
                }
                drawPath(chevron, ink.copy(alpha = 0.7f), style = Stroke(max(1.4f, c * 0.32f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            PdfFormWidget.Type.LIST -> {
                glassBody(r, min(r.height * 0.08f, 10f), pal, focused, editing, alpha)
                drawListRows(w, value, r, ink, pal, listScroll[w.key] ?: w.listInitialScroll(size, value))
            }
        }
    }
}

private fun DrawScope.drawListRows(w: PdfFormWidget, value: String, r: Rect, ink: Color, pal: FormPalette, scroll: Float) {
    val pt = w.ptPx(size)
    val rowH = w.listRowPx(size)
    val textPx = w.textPx(size, "")
    val pad = 2f * pt
    val inner = Rect(r.left + 1.5f, r.top + 1.5f, r.right - 1.5f, r.bottom - 1.5f)
    val selected = w.optionValues.indexOf(value)
    val visible = w.listVisibleRows(size)
    val overflow = w.options.size > visible
    val textRight = if (overflow) inner.right - 6f else inner.right
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawIntoCanvas { cv ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = textPx
                typeface = android.graphics.Typeface.SANS_SERIF
            }
            w.options.forEachIndexed { i, label ->
                val top = r.top + pad + (i - scroll) * rowH
                if (top > r.bottom || top + rowH < r.top) return@forEachIndexed
                if (i == selected) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(pal.vividTop, pal.vividBottom), top, top + rowH),
                        Offset(inner.left + 2f, top + 1f), Size(textRight - inner.left - 4f, rowH - 2f), CornerRadius(rowH * 0.3f)
                    )
                }
                paint.color = (if (i == selected) Color.White else ink).toArgb()
                cv.nativeCanvas.drawText(label, inner.left + pad + 2f, top + rowH / 2f + textPx * 0.35f, paint)
            }
        }
    }
    if (overflow) {
        // A slim glass scroll indicator, like the platform list's.
        val trackTop = inner.top + 3f
        val trackH = inner.height - 6f
        val maxScroll = (w.options.size - visible).toFloat().coerceAtLeast(1f)
        val thumbH = (trackH * visible / w.options.size).coerceAtLeast(10f)
        val thumbTop = trackTop + (trackH - thumbH) * (scroll / maxScroll).coerceIn(0f, 1f)
        drawRoundRect(pal.subtle, Offset(inner.right - 5f, trackTop), Size(3f, trackH), CornerRadius(1.5f))
        drawRoundRect(FormTint.copy(alpha = 0.55f), Offset(inner.right - 5f, thumbTop), Size(3f, thumbH), CornerRadius(1.5f))
    }
}

/** A text / choice value inside its box, in the field's own size, alignment and colour. */
private fun DrawScope.drawFieldText(w: PdfFormWidget, v: String, r: Rect, ink: Color) {
    val shown = if (w.password) "•".repeat(v.length) else v
    val textPx = w.textPx(size, shown)
    val pad = 2f * w.ptPx(size)
    clipRect(r.left, r.top, r.right, r.bottom) {
        drawIntoCanvas { cv ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = textPx
                color = ink.toArgb()
                typeface = android.graphics.Typeface.SANS_SERIF
            }
            val nc = cv.nativeCanvas
            when {
                w.comb && w.maxLength > 0 -> {
                    val cell = r.width / w.maxLength
                    val y = r.center.y + textPx * 0.35f
                    shown.take(w.maxLength).forEachIndexed { i, ch ->
                        val s = ch.toString()
                        nc.drawText(s, r.left + cell * i + (cell - paint.measureText(s)) / 2f, y, paint)
                    }
                }
                w.multiline -> {
                    var y = r.top + pad + textPx
                    for (line in wrap(shown, paint, r.width - pad * 2)) {
                        if (y > r.bottom + textPx) break
                        nc.drawText(line, alignedX(w.alignment, r, pad, paint.measureText(line)), y, paint)
                        y += textPx * 1.16f
                    }
                }
                else -> nc.drawText(shown, alignedX(w.alignment, r, pad, paint.measureText(shown)), r.center.y + textPx * 0.35f, paint)
            }
        }
    }
}

private fun alignedX(alignment: Int, r: Rect, pad: Float, width: Float): Float = when (alignment) {
    1 -> r.center.x - width / 2f
    2 -> r.right - pad - width
    else -> r.left + pad
}

private fun wrap(text: String, paint: android.graphics.Paint, maxW: Float): List<String> {
    val out = ArrayList<String>()
    for (para in text.split('\n')) {
        var line = ""
        for (word in para.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxW || line.isEmpty()) line = candidate
            else { out += line; line = word }
        }
        out += line
    }
    return out
}

// ── Typing in place ─────────────────────────────────────────────────────────────────────────

/**
 * Typing straight into a text field on the page: a real text field laid exactly over the box, in
 * the field's size, alignment and colour (and dot-masked for password fields), so what is typed is
 * what the saved form shows. It lives inside the page's zoom layer, so it zooms with the page; the
 * glass body under it is drawn by [drawFormWidgets].
 */
@Composable
internal fun PdfFormInlineEditor(
    widget: PdfFormWidget,
    value: String,
    canvas: Size,
    dark: Boolean,
    onValueChange: (String) -> Unit,
    onImeNext: () -> Unit
) {
    val density = LocalDensity.current
    val r = widget.rectIn(canvas)
    if (r.width <= 1f || r.height <= 1f) return
    val focus = remember { FocusRequester() }
    var field by remember(widget.key) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Auto-sized fields shrink as the text grows, exactly as the saved form will.
    val textPx = widget.textPx(canvas, if (widget.password) "•".repeat(field.text.length) else field.text)
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    LaunchedEffect(widget.key) { delay(80); runCatching { focus.requestFocus() } }
    val ink = Color(widget.textArgb).let { if (dark) invert(it) else it }
    val pad = with(density) { (2f * widget.ptPx(canvas)).toDp() }
    val comb = widget.comb && widget.maxLength > 0
    val style = TextStyle(
        color = ink,
        fontSize = with(density) { textPx.toSp() },
        textAlign = when { comb -> TextAlign.Start; widget.alignment == 1 -> TextAlign.Center; widget.alignment == 2 -> TextAlign.End; else -> TextAlign.Start },
        letterSpacing = if (comb) with(density) { (r.width / widget.maxLength - textPx * 0.56f).coerceAtLeast(0f).toSp() } else TextStyle.Default.letterSpacing
    )
    BasicTextField(
        value = field,
        onValueChange = { next ->
            val text = if (widget.maxLength > 0) next.text.take(widget.maxLength) else next.text
            field = if (text == next.text) next else next.copy(text = text, selection = TextRange(text.length))
            if (text != value) onValueChange(text)
        },
        singleLine = !widget.multiline,
        textStyle = style,
        cursorBrush = SolidColor(FormTint),
        visualTransformation = if (widget.password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = if (widget.multiline) ImeAction.Default else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onImeNext() }),
        modifier = Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
            .padding(horizontal = pad)
            .focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (widget.multiline) Alignment.TopStart else Alignment.CenterStart
            ) {
                Box(Modifier.fillMaxWidth()) { inner() }
            }
        }
    )
}

/** A field's name as people read it: "applicant.first_name" → "first name". */
internal fun humanFieldName(name: String): String =
    name.substringAfterLast('.')
        .replace('_', ' ')
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("\\[\\d+]"), "")
        .trim()
        .ifEmpty { name }

/**
 * The iOS-style form accessory, floating above the keyboard while a text field is being filled:
 * previous / next field, the field's name, and Done.
 */
@Composable
internal fun PdfFormAccessoryBar(
    widget: PdfFormWidget,
    backdrop: LayerBackdrop,
    ink: Color,
    glass: Color,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .viewerGlass(backdrop, glass, shape = { RoundedCornerShape(26.dp) })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LiquidIconButton(
            onClick = onPrevious, backdrop = backdrop, isInteractive = canPrevious,
            surfaceColor = glass, modifier = Modifier.size(38.dp)
        ) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.form_fill_previous), Modifier.size(22.dp), ink.copy(if (canPrevious) 1f else 0.35f)) }
        LiquidIconButton(
            onClick = onNext, backdrop = backdrop, isInteractive = canNext,
            surfaceColor = glass, modifier = Modifier.size(38.dp)
        ) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.form_fill_next), Modifier.size(22.dp), ink.copy(if (canNext) 1f else 0.35f)) }
        BasicText(
            humanFieldName(widget.fieldName),
            style = TextStyle(ink.copy(0.75f), 13.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        LiquidButton(onClick = onDone, backdrop = backdrop, tint = FormTint, modifier = Modifier.padding(end = 2.dp)) {
            BasicText(stringResource(R.string.viewer_done), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
        }
    }
}

// ── Dropdown menu ───────────────────────────────────────────────────────────────────────────

/**
 * A dropdown field's options, in the app's glass menu, unfolding from the field it belongs to
 * ([anchor] is the field's box in window coordinates). Back or a tap outside closes it.
 */
@Composable
internal fun PdfFormChoiceMenu(
    widget: PdfFormWidget?,
    anchor: Rect,
    value: String,
    dark: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val open = widget != null
    // The last widget shown, so the menu can play its fold animation after it's dismissed.
    var shown by remember { mutableStateOf<PdfFormWidget?>(null) }
    if (widget != null) shown = widget
    BackHandler(enabled = open, onBack = onDismiss)
    val fg = LiquidGlassColors.text(dark)
    GlassOverlay {
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize()) {
            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                onDismiss()
                            }
                        }
                )
            }
            val w = shown ?: return@Box
            val local = windowToLocal(anchor.topLeft)
            val menuWidth = with(density) { max(anchor.width, 200.dp.toPx()).toDp() }
            val gap = with(density) { 6.dp.toPx() }
            val maxMenuH = with(density) { 320.dp.toPx() }
            val below = local.y + anchor.height + gap
            val fitsBelow = below + maxMenuH <= layerSize.height || local.y < maxMenuH
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(110)),
                exit = fadeOut(tween(90)),
                modifier = Modifier.offset {
                    IntOffset(
                        local.x.roundToInt().coerceIn(0, (layerSize.width - with(density) { menuWidth.roundToPx() }).coerceAtLeast(0)),
                        if (fitsBelow) below.roundToInt() else (local.y - gap - maxMenuH).roundToInt().coerceAtLeast(0)
                    )
                }
            ) {
                val reveal by transition.animateFloat(
                    transitionSpec = { if (targetState == EnterExitState.Visible) GlassMotion.unfold() else GlassMotion.fold() },
                    label = "formMenuReveal"
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                val revealY by transition.animateFloat(
                    transitionSpec = { if (targetState == EnterExitState.Visible) GlassMotion.unfoldLag() else GlassMotion.fold() },
                    label = "formMenuRevealY"
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                Column(
                    Modifier
                        .width(menuWidth)
                        .heightIn(max = with(density) { maxMenuH.toDp() })
                        .graphicsLayer {
                            alpha = reveal.coerceIn(0f, 1f)
                            scaleX = 0.9f + 0.1f * reveal
                            scaleY = 0.55f + 0.45f * revealY
                            transformOrigin = TransformOrigin(0.5f, if (fitsBelow) 0f else 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .glassMenu(backdrop, dark, shape = { RoundedRectangle(20.dp) }, surfaceAlpha = 0.78f)
                        .verticalScroll(rememberScrollState())
                        .padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    w.options.forEachIndexed { i, label ->
                        val optionValue = w.optionValues.getOrElse(i) { label }
                        val selected = optionValue == value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .liquidRowClick(corner = 12.dp) { onPick(optionValue) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicText(
                                label,
                                style = TextStyle(fg, 14.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), FormTint)
                        }
                    }
                }
            }
        }
    }
}


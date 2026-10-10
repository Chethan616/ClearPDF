package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
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
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.pdfcore.form.PdfFormWidget
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** The tint form boxes wear in Edit mode: the system's "fillable" blue. */
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
    if (multiline) return 12f * pt
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

/**
 * Draws this page's form boxes: in Edit mode a soft blue wash and rim on every box (stronger on the
 * one being filled), and every value filled in this session inside its box, in the field's own
 * size, alignment and colour, so the page shows exactly what will be saved. [dark] is the dark
 * reader, which inverts the page, so paper and ink invert with it.
 */
internal fun DrawScope.drawFormWidgets(
    widgets: List<PdfFormWidget>,
    values: Map<String, String>,
    editing: Boolean,
    focusedKey: String?,
    dark: Boolean
) {
    val paper = if (dark) Color.Black else Color.White
    widgets.forEach { w ->
        val r = w.rectIn(size)
        val focused = w.key == focusedKey
        val ink = Color(w.textArgb).let { if (dark) invert(it) else it }
        val v = values[w.fieldName]
        // The live editor draws the focused text box itself.
        if (v != null && !(focused && w.type == PdfFormWidget.Type.TEXT)) drawFormValue(w, v, r, paper, ink)
        if (editing || focused) {
            val cr = CornerRadius(min(r.width, r.height) * 0.14f)
            drawRoundRect(FormTint.copy(alpha = if (focused) 0.16f else 0.09f), r.topLeft, r.size, cr)
            drawRoundRect(
                FormTint.copy(alpha = if (focused) 0.95f else 0.5f), r.topLeft, r.size, cr,
                style = Stroke(if (focused) 2.4f else 1.3f)
            )
        }
    }
}

private fun DrawScope.drawFormValue(w: PdfFormWidget, v: String, r: Rect, paper: Color, ink: Color) {
    when (w.type) {
        PdfFormWidget.Type.CHECKBOX -> {
            val inset = r.minDimension * 0.14f
            drawRect(paper, Offset(r.left + inset, r.top + inset), Size(r.width - inset * 2, r.height - inset * 2))
            if (v == "true") {
                val s = r.minDimension
                val ox = r.center.x - s / 2f
                val oy = r.center.y - s / 2f
                val check = Path().apply {
                    moveTo(ox + s * 0.24f, oy + s * 0.53f)
                    lineTo(ox + s * 0.43f, oy + s * 0.71f)
                    lineTo(ox + s * 0.77f, oy + s * 0.30f)
                }
                drawPath(check, ink, style = Stroke(s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        PdfFormWidget.Type.RADIO -> {
            val radius = r.minDimension / 2f
            drawCircle(paper, radius * 0.74f, r.center)
            if (v == w.onValue) drawCircle(ink, radius * 0.38f, r.center)
        }
        PdfFormWidget.Type.TEXT, PdfFormWidget.Type.CHOICE -> {
            val inset = min(r.height * 0.06f, 3f)
            drawRect(paper, Offset(r.left + inset, r.top + inset), Size(r.width - inset * 2, r.height - inset * 2))
            if (v.isEmpty()) return
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
                            val maxW = r.width - pad * 2
                            var y = r.top + pad + textPx
                            for (line in wrap(shown, paint, maxW)) {
                                if (y > r.bottom + textPx) break
                                nc.drawText(line, alignedX(w.alignment, r, pad, paint.measureText(line)), y, paint)
                                y += textPx * 1.16f
                            }
                        }
                        else -> {
                            val y = r.center.y + textPx * 0.35f
                            nc.drawText(shown, alignedX(w.alignment, r, pad, paint.measureText(shown)), y, paint)
                        }
                    }
                }
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

/**
 * Typing straight into a text field on the page: a real text field laid exactly over the box, in
 * the field's size, alignment and colour (and dot-masked for password fields), so what is typed is
 * what the saved form shows. It lives inside the page's zoom layer, so it zooms with the page.
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
    val paper = if (dark) Color.Black else Color.White
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
            .background(paper, RoundedCornerShape(3.dp))
            .border(1.5.dp, FormTint, RoundedCornerShape(3.dp))
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
 * The iOS-style form accessory, floating above the keyboard: previous / next field, the field's
 * name, Done, and, for a choice field (which has no keyboard), its options as glass chips.
 */
@Composable
internal fun PdfFormAccessoryBar(
    widget: PdfFormWidget,
    value: String,
    backdrop: LayerBackdrop,
    ink: Color,
    glass: Color,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .viewerGlass(backdrop, glass, shape = { RoundedCornerShape(26.dp) })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiquidIconButton(
                onClick = onPrevious, backdrop = backdrop, isInteractive = canPrevious,
                surfaceColor = glass, modifier = Modifier.size(38.dp)
            ) { Icon(Icons.Rounded.KeyboardArrowUp, androidx.compose.ui.res.stringResource(R.string.form_fill_previous), Modifier.size(22.dp), ink.copy(if (canPrevious) 1f else 0.35f)) }
            LiquidIconButton(
                onClick = onNext, backdrop = backdrop, isInteractive = canNext,
                surfaceColor = glass, modifier = Modifier.size(38.dp)
            ) { Icon(Icons.Rounded.KeyboardArrowDown, androidx.compose.ui.res.stringResource(R.string.form_fill_next), Modifier.size(22.dp), ink.copy(if (canNext) 1f else 0.35f)) }
            BasicText(
                humanFieldName(widget.fieldName),
                style = TextStyle(ink.copy(0.75f), 13.sp, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            LiquidButton(onClick = onDone, backdrop = backdrop, tint = FormTint, modifier = Modifier.padding(end = 2.dp)) {
                BasicText(androidx.compose.ui.res.stringResource(R.string.viewer_done), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
            }
        }
        if (widget.type == PdfFormWidget.Type.CHOICE && widget.options.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                widget.options.forEach { option ->
                    val selected = option == value
                    LiquidButton(
                        onClick = { onPick(option) },
                        backdrop = backdrop,
                        tint = if (selected) FormTint else Color.Unspecified,
                        surfaceColor = if (selected) Color.Unspecified else glass
                    ) {
                        BasicText(
                            option,
                            style = TextStyle(if (selected) Color.White else ink, 14.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

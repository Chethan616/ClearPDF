package com.chethan616.clearpdf.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/** Office 2013+ default theme colours (Background 1/Text 1/Background 2/Text 2/Accent 1..6). */
val OfficeThemeColors: List<Color> = listOf(
    Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFE7E6E6), Color(0xFF44546A), Color(0xFF4472C4),
    Color(0xFFED7D31), Color(0xFFA5A5A5), Color(0xFFFFC000), Color(0xFF5B9BD5), Color(0xFF70AD47)
)

/** Office "Standard Colors" row. */
val OfficeStandardColors: List<Color> = listOf(
    Color(0xFFC00000), Color(0xFFFF0000), Color(0xFFFFC000), Color(0xFFFFFF00), Color(0xFF92D050),
    Color(0xFF00B050), Color(0xFF00B0F0), Color(0xFF0070C0), Color(0xFF002060), Color(0xFF7030A0)
)

/**
 * Office-style tint/shade ladder for one theme colour (5 variants below the base swatch):
 * very light colours get progressively darker shades, very dark colours progressively lighter
 * tints, everything else 80/60/40% lighter then 25/50% darker — matching Excel's palette.
 */
fun officeVariants(base: Color): List<Color> {
    val lum = base.luminance()
    fun lighter(f: Float) = Color(
        base.red + (1f - base.red) * f, base.green + (1f - base.green) * f, base.blue + (1f - base.blue) * f
    )
    fun darker(f: Float) = Color(base.red * (1f - f), base.green * (1f - f), base.blue * (1f - f))
    return when {
        lum > 0.9f -> listOf(darker(0.05f), darker(0.15f), darker(0.25f), darker(0.35f), darker(0.5f))
        lum < 0.02f -> listOf(lighter(0.5f), lighter(0.35f), lighter(0.25f), lighter(0.15f), lighter(0.05f))
        else -> listOf(lighter(0.8f), lighter(0.6f), lighter(0.4f), darker(0.25f), darker(0.5f))
    }
}

private const val RECENT_PREFS = "glass_color_picker"
private const val RECENT_KEY = "recent_colors"
private const val RECENT_MAX = 8

private fun loadRecent(context: Context): List<Color> =
    context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
        .getString(RECENT_KEY, null)
        ?.split(',')
        ?.mapNotNull { it.toLongOrNull()?.let { v -> Color(v.toInt()) } }
        ?: emptyList()

private fun saveRecent(context: Context, colors: List<Color>) {
    context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE).edit()
        .putString(RECENT_KEY, colors.joinToString(",") { (it.toArgb().toLong() and 0xFFFFFFFFL).toString() })
        .apply()
}

private fun Color.hex(showAlpha: Boolean): String {
    val argb = toArgb()
    return if (showAlpha) String.format("%08X", argb) else String.format("%06X", argb and 0xFFFFFF)
}

private fun parseHex(text: String, keepAlpha: Float): Color? {
    val t = text.trim().removePrefix("#")
    return when (t.length) {
        6 -> t.toLongOrNull(16)?.let { Color((0xFF000000 or it).toInt()).copy(alpha = keepAlpha) }
        8 -> t.toLongOrNull(16)?.let { Color(it.toInt()) }
        3 -> t.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { Color((0xFF000000 or it).toInt()).copy(alpha = keepAlpha) }
        else -> null
    }
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

private fun hsvColor(h: Float, s: Float, v: Float, a: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))).copy(alpha = a)

/**
 * Excel/Office-style colour picker on a liquid-glass panel:
 * theme colour columns with 5 tints/shades each, a standard-colours row, recent colours (last 8,
 * persisted), an optional "No colour"/automatic row ([onClear]) and an expandable custom section
 * with a saturation/value area, hue strip, optional alpha strip and a hex field.
 */
@Composable
fun GlassColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    themeColors: List<Color> = OfficeThemeColors,
    showAlpha: Boolean = false,
    onClear: (() -> Unit)? = null,
    clearLabel: String = "No colour",
    standardColors: List<Color> = OfficeStandardColors,
    /**
     * Optional: the 5 tint/shade variants under each theme swatch. Defaults to [officeVariants];
     * a spreadsheet editor passes the exact HLS-tint ladder its file format uses.
     */
    themeVariants: (Color) -> List<Color> = ::officeVariants,
    /**
     * Optional: called instead of [onColorChange] when a theme-grid swatch is tapped, with its
     * column (theme slot) and row (0 = base, 1–5 = variant), so callers can store the pick as a
     * theme reference + tint rather than a literal colour.
     */
    onThemePick: ((column: Int, row: Int, color: Color) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val isLight = !isDark
    val textColor = LiquidGlassColors.text(isDark)
    val secondary = LiquidGlassColors.secondary(isDark)
    val containerColor = if (isLight) Color(0xFFFAFAFA).copy(0.62f) else Color(0xFF121212).copy(0.45f)
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onColorChange)

    val recent = remember { mutableStateListOf<Color>().apply { addAll(loadRecent(context)) } }
    fun pushRecent(c: Color) {
        val opaqueCompare = c.toArgb()
        recent.removeAll { it.toArgb() == opaqueCompare }
        recent.add(0, c)
        while (recent.size > RECENT_MAX) recent.removeAt(recent.lastIndex)
        saveRecent(context, recent.toList())
    }
    fun pick(c: Color, addToRecent: Boolean = true) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        val out = if (showAlpha) c else c.copy(alpha = 1f)
        currentOnChange(out)
        if (addToRecent) pushRecent(out)
    }

    var customOpen by remember { mutableStateOf(false) }

    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(24f.dp) },
                effects = {
                    colorControls(brightness = if (isLight) 0.2f else 0f, saturation = 1.5f)
                    blur(if (isLight) 16f.dp.toPx() else 10f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = 16f.dp, color = Color.Black.copy(alpha = 0.12f)) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onClear != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClear()
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Block, contentDescription = null, tint = secondary, modifier = Modifier.size(22.dp))
                BasicText(clearLabel, style = TextStyle(textColor, 15.sp, FontWeight.Medium))
            }
        }

        SectionLabel("Theme colours", secondary)
        // Theme grid: base row, gap, then 5 variant rows.
        val columns = themeColors.map { listOf(it) + themeVariants(it) }
        fun pickTheme(col: Int, row: Int) {
            val c = columns[col][row]
            val handler = onThemePick
            if (handler == null) pick(c) else {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                handler(col, row, c.copy(alpha = 1f))
                pushRecent(c.copy(alpha = 1f))
            }
        }
        SwatchRow(themeColors, color, isDark) { c -> pickTheme(themeColors.indexOf(c).coerceAtLeast(0), 0) }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            for (row in 1..5) {
                val rowColors = columns.map { it[row] }
                SwatchRow(rowColors, color, isDark, gap = 4.dp, tight = true) { c -> pickTheme(rowColors.indexOf(c).coerceAtLeast(0), row) }
            }
        }

        SectionLabel("Standard colours", secondary)
        SwatchRow(standardColors, color, isDark) { pick(it) }

        if (recent.isNotEmpty()) {
            SectionLabel("Recent colours", secondary)
            SwatchRow(
                recent.toList() + List((themeColors.size - recent.size).coerceAtLeast(0)) { Color.Unspecified },
                color, isDark
            ) { pick(it) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { customOpen = !customOpen }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
            )
            BasicText("More colours…", Modifier.weight(1f), style = TextStyle(textColor, 15.sp, FontWeight.Medium))
            Icon(
                if (customOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = secondary
            )
        }

        AnimatedVisibility(
            visible = customOpen,
            enter = fadeIn(GlassMotion.fade()) + expandVertically(GlassMotion.settle()),
            exit = fadeOut(GlassMotion.fade()) + shrinkVertically(GlassMotion.settle())
        ) {
            CustomColorSection(
                color = color,
                showAlpha = showAlpha,
                isDark = isDark,
                onChange = { c -> currentOnChange(if (showAlpha) c else c.copy(alpha = 1f)) },
                onCommit = { c -> pushRecent(if (showAlpha) c else c.copy(alpha = 1f)) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    BasicText(text, Modifier.padding(start = 2.dp, top = 2.dp), style = TextStyle(color, 12.sp, FontWeight.SemiBold))
}

@Composable
private fun SwatchRow(
    colors: List<Color>,
    selected: Color,
    isDark: Boolean,
    gap: androidx.compose.ui.unit.Dp = 4.dp,
    tight: Boolean = false,
    onPick: (Color) -> Unit
) {
    val outline = if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.12f)
    val ring = if (isDark) Color.White else LiquidGlassColors.Blue
    val selArgb = selected.copy(alpha = 1f).toArgb()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        colors.forEach { c ->
            if (c == Color.Unspecified) {
                Spacer(Modifier.weight(1f))
                return@forEach
            }
            val isSel = c.copy(alpha = 1f).toArgb() == selArgb
            val swatchShape = RoundedCornerShape(if (tight) 3.dp else 6.dp)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(if (tight) 1.25f else 1f)
                    .clip(swatchShape)
                    .background(c)
                    .border(
                        width = if (isSel) 2.dp else 0.5.dp,
                        color = if (isSel) ring else outline,
                        shape = swatchShape
                    )
                    .semantics { contentDescription = "#" + c.hex(false) }
                    .clickable(role = Role.Button) { onPick(c) },
                contentAlignment = Alignment.Center
            ) {
                // Lightweight specular sheen ties the dense Office palette to ClearPDF's glass
                // controls without putting a separate blur/lens layer on dozens of tiny swatches.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.30f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.10f)
                                )
                            ),
                            swatchShape
                        )
                )
                if (isSel && !tight) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (c.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorSection(
    color: Color,
    showAlpha: Boolean,
    isDark: Boolean,
    onChange: (Color) -> Unit,
    onCommit: (Color) -> Unit
) {
    val textColor = LiquidGlassColors.text(isDark)
    val fieldBg = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f)
    val initial = remember { color.toHsv() }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    var alpha by remember { mutableFloatStateOf(color.alpha) }
    var lastEmitted by remember { mutableStateOf(color) }
    var hexText by remember { mutableStateOf(color.hex(showAlpha)) }

    // Sync from outside changes (e.g. a swatch tap) without disturbing hue for greys.
    LaunchedEffect(color) {
        if (color.toArgb() != lastEmitted.toArgb()) {
            val hsv = color.toHsv()
            if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
            sat = hsv[1]; value = hsv[2]; alpha = color.alpha
            lastEmitted = color
            hexText = color.hex(showAlpha)
        }
    }

    fun emit(commit: Boolean) {
        val c = hsvColor(hue, sat, value, if (showAlpha) alpha else 1f)
        lastEmitted = c
        hexText = c.hex(showAlpha)
        onChange(c)
        if (commit) onCommit(c)
    }

    val pure = hsvColor(hue, 1f, 1f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Saturation / value area
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun apply(p: Offset) {
                            sat = (p.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            emit(false)
                        }
                        apply(down.position)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            apply(ch.position)
                        }
                        emit(true)
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.Black.copy(0.25f), radius = 12.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
        }

        // Hue strip
        StripSlider(
            fraction = hue / 360f,
            brush = Brush.horizontalGradient(
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            ),
            onDrag = { f, end -> hue = (f * 360f).coerceIn(0f, 359.9f); emit(end) }
        )
        if (showAlpha) {
            val opaque = hsvColor(hue, sat, value, 1f)
            StripSlider(
                fraction = alpha,
                brush = Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)),
                checker = true,
                onDrag = { f, end -> alpha = f.coerceIn(0f, 1f); emit(end) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(hsvColor(hue, sat, value, if (showAlpha) alpha else 1f))
                    .border(0.5.dp, textColor.copy(0.2f), RoundedCornerShape(12.dp))
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fieldBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText("#", style = TextStyle(textColor.copy(0.6f), 16.sp, fontFamily = FontFamily.Monospace))
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val filtered = t.removePrefix("#").filter { it.isLetterOrDigit() }.take(if (showAlpha) 8 else 6).uppercase()
                        hexText = filtered
                        val parsed = parseHex(filtered, if (showAlpha) alpha else 1f)
                        if (parsed != null && (filtered.length == 6 || filtered.length == 8)) {
                            val hsv = parsed.toHsv()
                            if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0]
                            sat = hsv[1]; value = hsv[2]; alpha = parsed.alpha
                            lastEmitted = parsed
                            onChange(parsed)
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(textColor, 16.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(LiquidGlassColors.Blue),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        parseHex(hexText, if (showAlpha) alpha else 1f)?.let { onCommit(it) }
                    }),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StripSlider(
    fraction: Float,
    brush: Brush,
    checker: Boolean = false,
    onDrag: (Float, Boolean) -> Unit
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val inset = size.height / 2f
                    fun f(x: Float) = ((x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                    currentOnDrag(f(down.position.x), false)
                    down.consume()
                    var lastX = down.position.x
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        lastX = ch.position.x
                        currentOnDrag(f(lastX), false)
                    }
                    currentOnDrag(f(lastX), true)
                }
            }
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        if (checker) {
            val cell = 7.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = row % 2
                while (x < size.width) {
                    if (col % 2 == 0) drawRect(Color(0xFFCCCCCC), Offset(x, y), Size(cell, cell))
                    else drawRect(Color.White, Offset(x, y), Size(cell, cell))
                    x += cell; col++
                }
                y += cell; row++
            }
        }
        drawRoundRect(brush, cornerRadius = r)
        val inset = size.height / 2f
        val cx = inset + (size.width - inset * 2) * fraction.coerceIn(0f, 1f)
        val c = Offset(cx, size.height / 2f)
        drawCircle(Color.Black.copy(0.25f), radius = size.height / 2f - 1.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
    }
}

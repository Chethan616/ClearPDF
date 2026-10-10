package com.chethan616.clearpdf.ui.screen.imageeditor

import com.chethan616.clearpdf.ui.components.rememberJellyPress
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Hexagon
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ArrowRightAlt
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.imageeditor.engine.Adjust
import com.chethan616.clearpdf.imageeditor.engine.BrushKind
import com.chethan616.clearpdf.imageeditor.engine.FilterPreset
import com.chethan616.clearpdf.imageeditor.engine.ShapeKind
import com.chethan616.clearpdf.imageeditor.engine.TextFont
import com.chethan616.clearpdf.imageeditor.engine.TextLayer
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.GlassSegmentedControl
import com.chethan616.clearpdf.ui.components.GlassToolButton
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel.BrushSettings
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

// ── Shared bits ────────────────────────────────────────────────────────────────────────────

@Composable
internal fun textColor() = LiquidGlassColors.text(LocalIsDarkMode.current)

@Composable
internal fun secondaryColor() = LiquidGlassColors.secondary(LocalIsDarkMode.current)

/** Selectable text pill used inside the glass context panel. */
@Composable
internal fun EditorChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LiquidGlassColors.Blue
) {
    val isDark = LocalIsDarkMode.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val jelly = rememberJellyPress(interaction)
    val bg by animateColorAsState(
        if (selected) accent.copy(if (isDark) 0.30f else 0.18f) else if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f),
        GlassMotion.settle(), label = "chipBg"
    )
    val fg by animateColorAsState(if (selected) accent else LiquidGlassColors.text(isDark), GlassMotion.settle(), label = "chipFg")
    Box(
        modifier
            .graphicsLayer { scaleX = jelly.scale; scaleY = jelly.scale }
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interaction, null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(fg, 13.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
    }
}

/** Label · value row above a [LiquidSlider]; the value resets when tapped. */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    onChange: (Float) -> Unit,
    valueText: (Float) -> String = { it.roundToInt().toString() },
    onReset: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = TextStyle(secondaryColor(), 12.sp, FontWeight.Medium))
            BasicText(
                valueText(value),
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (onReset != null) Modifier.clickable { onReset() } else Modifier)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = TextStyle(textColor(), 12.sp, FontWeight.SemiBold)
            )
        }
        LiquidSlider(
            value = { value },
            onValueChange = onChange,
            valueRange = range,
            visibilityThreshold = (range.endInclusive - range.start) / 1000f,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ColorSwatch(color: Int, onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 36) {
    val isDark = LocalIsDarkMode.current
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(2.dp, if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.2f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    )
}

// ── Crop ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun CropPanel(
    session: ImageEditorViewModel.CropSession?,
    backdrop: Backdrop,
    vm: ImageEditorViewModel
) {
    if (session == null) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LiquidGlassColors.Blue, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlassToolButton(Icons.Rounded.RotateLeft, null, false, { vm.rotateCrop(-1) })
            GlassToolButton(Icons.Rounded.RotateRight, null, false, { vm.rotateCrop(1) })
            GlassToolButton(Icons.Rounded.Flip, null, session.transform.flipH, { vm.flipCrop(true) })
            GlassToolButton(Icons.Rounded.Flip, null, session.transform.flipV, { vm.flipCrop(false) }, Modifier.rotate(90f))
            GlassToolButton(Icons.Rounded.Transform, stringResource(R.string.ie_perspective), session.perspective, { vm.setPerspective(!session.perspective) })
            Spacer(Modifier.weight(1f))
            GlassToolButton(Icons.Rounded.Restore, null, false, { vm.resetCrop() })
        }
        if (!session.perspective) {
            SliderRow(
                stringResource(R.string.ie_straighten), session.transform.straighten, -45f..45f, backdrop,
                onChange = { vm.straighten(it) },
                valueText = { "%.1f°".format(it) },
                onReset = { vm.straighten(0f) }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CropAspects.size) { i ->
                    val a = CropAspects[i]
                    val label = when (a.ratio) {
                        null -> stringResource(R.string.ie_free)
                        -1f -> stringResource(R.string.ie_original)
                        else -> a.label
                    }
                    EditorChip(label, session.aspectIndex == i, { vm.setAspect(i) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CropMasks.size) { i ->
                    EditorChip(CropMasks[i].label, session.outlineIndex == i, { vm.setOutline(i) }, accent = LiquidGlassColors.Purple)
                }
            }
        }
        LiquidButton(onClick = { vm.triggerCrop() }, backdrop = backdrop, tint = LiquidGlassColors.Blue, modifier = Modifier.fillMaxWidth()) {
            BasicText(stringResource(R.string.ie_apply), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold), modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// ── Adjust ─────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun adjustLabel(a: Adjust): String = stringResource(
    when (a) {
        Adjust.Brightness -> R.string.ie_adj_brightness
        Adjust.Contrast -> R.string.ie_adj_contrast
        Adjust.Saturation -> R.string.ie_adj_saturation
        Adjust.Exposure -> R.string.ie_adj_exposure
        Adjust.Highlights -> R.string.ie_adj_highlights
        Adjust.Shadows -> R.string.ie_adj_shadows
        Adjust.Temperature -> R.string.ie_adj_temperature
        Adjust.Tint -> R.string.ie_adj_tint
        Adjust.Vibrance -> R.string.ie_adj_vibrance
        Adjust.Hue -> R.string.ie_adj_hue
        Adjust.Gamma -> R.string.ie_adj_gamma
        Adjust.Sharpen -> R.string.ie_adj_sharpen
        Adjust.Vignette -> R.string.ie_adj_vignette
    }
)

@Composable
fun AdjustPanel(
    values: Map<Adjust, Float>,
    selected: Adjust,
    backdrop: Backdrop,
    vm: ImageEditorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val v = values[selected] ?: selected.default
        SliderRow(
            adjustLabel(selected), v, selected.min..selected.max, backdrop,
            onChange = { vm.setAdjust(selected, it) },
            valueText = { x -> val r = x.roundToInt(); if (r > 0 && selected.min < 0) "+$r" else "$r" },
            onReset = { vm.resetAdjust(selected) }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Adjust.entries.size) { i ->
                val a = Adjust.entries[i]
                val changed = !ImageEditorViewModel.isDefault(a, values[a] ?: a.default)
                EditorChip(
                    adjustLabel(a) + if (changed) " •" else "",
                    a == selected,
                    { vm.selectAdjust(a) }
                )
            }
        }
    }
}

// ── Filters ────────────────────────────────────────────────────────────────────────────────

@Composable
fun FiltersPanel(
    source: Bitmap?,
    thumbs: Map<FilterPreset, Bitmap>,
    selected: FilterPreset?,
    intensity: Float,
    backdrop: Backdrop,
    vm: ImageEditorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selected != null) {
            SliderRow(
                stringResource(R.string.ie_intensity), intensity * 100f, 0f..100f, backdrop,
                onChange = { vm.setFilterIntensity(it / 100f) },
                valueText = { "${it.roundToInt()}%" },
                onReset = { vm.setFilterIntensity(1f) }
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { FilterThumb(stringResource(R.string.ie_filter_none), source, selected == null) { vm.selectFilter(null) } }
            items(FilterPreset.entries.size) { i ->
                val p = FilterPreset.entries[i]
                FilterThumb(p.label, thumbs[p], selected == p) { vm.selectFilter(p) }
            }
        }
    }
}

@Composable
private fun FilterThumb(label: String, bitmap: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val isDark = LocalIsDarkMode.current
    val haptics = LocalHapticFeedback.current
    val border by animateColorAsState(if (selected) LiquidGlassColors.Blue else Color.Transparent, GlassMotion.settle(), label = "thumbBorder")
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, border, RoundedCornerShape(14.dp))
                .background(if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                val img = remember(bitmap) { bitmap.asImageBitmap() }
                Image(img, label, Modifier.size(64.dp), contentScale = ContentScale.Crop)
            } else {
                CircularProgressIndicator(color = LiquidGlassColors.Blue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }
        BasicText(
            label,
            style = TextStyle(if (selected) LiquidGlassColors.Blue else textColor(), 11.sp, FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 1
        )
    }
}

// ── Draw ───────────────────────────────────────────────────────────────────────────────────

private val BrushIcons: Map<BrushKind, ImageVector> = mapOf(
    BrushKind.Pen to Icons.Rounded.Edit,
    BrushKind.Highlighter to Icons.Rounded.BorderColor,
    BrushKind.Neon to Icons.Rounded.Flare,
    BrushKind.Eraser to Icons.Rounded.AutoFixNormal,
    BrushKind.Blur to Icons.Rounded.BlurOn,
    BrushKind.Pixelate to Icons.Rounded.GridOn,
)

@Composable
private fun brushLabel(b: BrushKind) = stringResource(
    when (b) {
        BrushKind.Pen -> R.string.ie_brush_pen
        BrushKind.Highlighter -> R.string.ie_brush_highlighter
        BrushKind.Neon -> R.string.ie_brush_neon
        BrushKind.Eraser -> R.string.ie_brush_eraser
        BrushKind.Blur -> R.string.ie_brush_blur
        BrushKind.Pixelate -> R.string.ie_brush_pixelate
    }
)

/** Shapes offered in the draw panel (outlined + filled variants of each). */
private val ShapeIcons: List<Pair<ShapeKind, ImageVector>> = listOf(
    ShapeKind.Free to Icons.Rounded.Gesture,
    ShapeKind.Line to Icons.Rounded.HorizontalRule,
    ShapeKind.LineArrow to Icons.Rounded.ArrowRightAlt,
    ShapeKind.DoubleLineArrow to Icons.Rounded.SwapHoriz,
    ShapeKind.Arrow to Icons.Rounded.CallMade,
    ShapeKind.DoubleArrow to Icons.Rounded.SyncAlt,
    ShapeKind.OutlinedRect to Icons.Rounded.CropSquare,
    ShapeKind.Rect to Icons.Rounded.Square,
    ShapeKind.OutlinedOval to Icons.Rounded.RadioButtonUnchecked,
    ShapeKind.Oval to Icons.Rounded.Circle,
    ShapeKind.OutlinedTriangle to Icons.Rounded.ChangeHistory,
    ShapeKind.OutlinedPolygon to Icons.Rounded.Hexagon,
    ShapeKind.OutlinedStar to Icons.Rounded.StarOutline,
    ShapeKind.Star to Icons.Rounded.Star,
    ShapeKind.Lasso to Icons.Rounded.Draw,
    ShapeKind.FloodFill to Icons.Rounded.FormatColorFill,
    ShapeKind.Spray to Icons.Rounded.Grain,
)

@Composable
fun DrawPanel(
    b: BrushSettings,
    backdrop: Backdrop,
    onPickColor: () -> Unit,
    vm: ImageEditorViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            items(BrushKind.entries.size) { i ->
                val k = BrushKind.entries[i]
                GlassToolButton(BrushIcons.getValue(k), brushLabel(k), b.brush == k, { vm.updateBrush { it.copy(brush = k) } })
            }
        }
        if (b.brush != BrushKind.Eraser) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items(ShapeIcons.size) { i ->
                    val (shape, icon) = ShapeIcons[i]
                    GlassToolButton(icon, null, b.shape == shape, { vm.updateBrush { it.copy(shape = shape) } }, accent = LiquidGlassColors.Purple)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val colorable = b.brush == BrushKind.Pen || b.brush == BrushKind.Highlighter || b.brush == BrushKind.Neon
            if (colorable) ColorSwatch(b.color, onPickColor)
            Box(Modifier.weight(1f)) {
                when {
                    b.shape == ShapeKind.FloodFill && b.brush != BrushKind.Eraser -> SliderRow(
                        stringResource(R.string.ie_tolerance), b.tolerance * 100f, 0f..100f, backdrop,
                        onChange = { v -> vm.updateBrush { it.copy(tolerance = v / 100f) } }
                    )
                    else -> SliderRow(
                        stringResource(R.string.ie_size), b.width * 1000f, 2f..120f, backdrop,
                        onChange = { v -> vm.updateBrush { it.copy(width = v / 1000f) } }
                    )
                }
            }
        }
        when (b.brush) {
            BrushKind.Blur, BrushKind.Pixelate -> SliderRow(
                stringResource(R.string.ie_strength), b.effectStrength * 1000f, 5f..100f, backdrop,
                onChange = { v -> vm.updateBrush { it.copy(effectStrength = v / 1000f) } }
            )
            BrushKind.Pen, BrushKind.Neon -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    SliderRow(stringResource(R.string.ie_opacity), b.alpha * 100f, 5f..100f, backdrop,
                        onChange = { v -> vm.updateBrush { it.copy(alpha = v / 100f) } }, valueText = { "${it.roundToInt()}%" })
                }
                Box(Modifier.weight(1f)) {
                    SliderRow(stringResource(R.string.ie_softness), b.softness * 100f, 0f..100f, backdrop,
                        onChange = { v -> vm.updateBrush { it.copy(softness = v / 100f) } })
                }
            }
            else -> Unit
        }
    }
}

// ── Text ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun TextPanel(
    t: TextLayer,
    backdrop: Backdrop,
    onPickColor: (target: Int) -> Unit,
    vm: ImageEditorViewModel
) {
    val isDark = LocalIsDarkMode.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (t.text.isEmpty()) BasicText(stringResource(R.string.ie_text_hint), style = TextStyle(secondaryColor(), 15.sp))
                BasicTextField(
                    value = t.text,
                    onValueChange = { s -> vm.updateText { it.copy(text = s) } },
                    textStyle = TextStyle(textColor(), 15.sp),
                    cursorBrush = SolidColor(LiquidGlassColors.Blue),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LiquidButton(onClick = { vm.applyText() }, backdrop = backdrop, tint = LiquidGlassColors.Blue) {
                BasicText(stringResource(R.string.ie_text_add), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(TextFont.entries.size) { i ->
                val f = TextFont.entries[i]
                EditorChip(f.name, t.font == f, { vm.updateText { it.copy(font = f) } })
            }
            item { EditorChip(stringResource(R.string.ie_bold), t.bold, { vm.updateText { it.copy(bold = !it.bold) } }, accent = LiquidGlassColors.Purple) }
            item { EditorChip(stringResource(R.string.ie_italic), t.italic, { vm.updateText { it.copy(italic = !it.italic) } }, accent = LiquidGlassColors.Purple) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LabeledSwatch(stringResource(R.string.ie_color), t.color) { onPickColor(0) }
            LabeledSwatch(stringResource(R.string.ie_outline), t.outlineColor) { onPickColor(1) }
            LabeledSwatch(stringResource(R.string.ie_background), t.backgroundColor) { onPickColor(2) }
            Box(Modifier.weight(1f)) {
                SliderRow(stringResource(R.string.ie_opacity), t.alpha * 100f, 10f..100f, backdrop,
                    onChange = { v -> vm.updateText { it.copy(alpha = v / 100f) } }, valueText = { "${it.roundToInt()}%" })
            }
        }
    }
}

@Composable
private fun LabeledSwatch(label: String, color: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (color == 0) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .border(2.dp, secondaryColor().copy(0.6f), CircleShape)
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center
            ) { BasicText("∅", style = TextStyle(secondaryColor(), 14.sp)) }
        } else ColorSwatch(color, onClick, size = 32)
        BasicText(label, style = TextStyle(secondaryColor(), 10.sp))
    }
}

// ── More ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun MorePanel(
    state: ImageEditorViewModel.UiState,
    backdrop: Backdrop,
    vm: ImageEditorViewModel,
    onOpenSheet: (ImageEditorViewModel.MoreTool) -> Unit
) {
    if (state.more == ImageEditorViewModel.MoreTool.Background) {
        BackgroundPanel(state, backdrop, vm)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        GlassToolButton(Icons.Rounded.AutoFixHigh, stringResource(R.string.ie_more_background), false, { vm.openMore(ImageEditorViewModel.MoreTool.Background) })
        GlassToolButton(Icons.Rounded.BorderColor, stringResource(R.string.ie_more_watermark), false, { onOpenSheet(ImageEditorViewModel.MoreTool.Watermark) })
        GlassToolButton(Icons.Rounded.Straighten, stringResource(R.string.ie_more_resize), false, { onOpenSheet(ImageEditorViewModel.MoreTool.Resize) })
        GlassToolButton(Icons.Rounded.Image, stringResource(R.string.ie_format), false, { onOpenSheet(ImageEditorViewModel.MoreTool.Export) })
        GlassToolButton(Icons.Rounded.Info, stringResource(R.string.ie_more_exif), false, { onOpenSheet(ImageEditorViewModel.MoreTool.Exif) })
    }
}

@Composable
private fun BackgroundPanel(state: ImageEditorViewModel.UiState, backdrop: Backdrop, vm: ImageEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.bgAvailable) {
            LiquidButton(
                onClick = { vm.autoRemoveBackground() },
                backdrop = backdrop,
                tint = LiquidGlassColors.Purple,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.bgBusy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicText(stringResource(R.string.ie_bg_working), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                } else {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp), Color.White)
                    Spacer(Modifier.width(8.dp))
                    BasicText(stringResource(R.string.ie_bg_auto), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                }
            }
        } else {
            BasicText(stringResource(R.string.ie_bg_unavailable), style = TextStyle(secondaryColor(), 12.sp))
        }
        GlassSegmentedControl(
            options = listOf(stringResource(R.string.ie_bg_erase), stringResource(R.string.ie_bg_restore)),
            selectedIndex = if (state.bgRestore) 1 else 0,
            onSelect = { vm.setBgRestore(it == 1) },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
        SliderRow(stringResource(R.string.ie_size), state.bgBrushWidth * 1000f, 10f..200f, backdrop,
            onChange = { v -> vm.setBgBrushWidth(v / 1000f) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LiquidButton(onClick = { vm.closeMore() }, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                BasicText(stringResource(R.string.ie_done), style = TextStyle(LiquidGlassColors.Blue, 14.sp, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
    }
}

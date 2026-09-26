package com.chethan616.clearpdf.ui.screen.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.imageeditor.engine.ExportFormat
import com.chethan616.clearpdf.imageeditor.engine.MetadataMode
import com.chethan616.clearpdf.imageeditor.engine.WatermarkPosition
import com.chethan616.clearpdf.ui.components.GlassSegmentedControl
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

@Composable
private fun SheetTitle(text: String) {
    BasicText(text, style = TextStyle(textColor(), 18.sp, FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun PrimaryAction(text: String, backdrop: Backdrop, onClick: () -> Unit) {
    LiquidButton(onClick = onClick, backdrop = backdrop, tint = LiquidGlassColors.Blue, modifier = Modifier.fillMaxWidth()) {
        BasicText(text, style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold), modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun GlassField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false, hint: String = "") {
    val isDark = LocalIsDarkMode.current
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) BasicText(hint, style = TextStyle(secondaryColor(), 15.sp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(textColor(), 15.sp),
            cursorBrush = SolidColor(LiquidGlassColors.Blue),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WatermarkSheetContent(
    state: ImageEditorViewModel.UiState,
    backdrop: Backdrop,
    vm: ImageEditorViewModel,
    onPickImage: () -> Unit,
    onPickColor: () -> Unit
) {
    val p = state.watermark
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTitle(stringResource(R.string.ie_more_watermark))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassField(p.text, { s -> vm.updateWatermark { it.copy(text = s) } }, Modifier.weight(1f), hint = stringResource(R.string.ie_wm_text))
            ColorSwatch(p.color, onPickColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorChip(stringResource(R.string.ie_wm_image), p.image != null, onPickImage)
            if (p.image != null) EditorChip(stringResource(R.string.ie_wm_clear_image), false, { vm.updateWatermark { it.copy(image = null) } }, accent = LiquidGlassColors.Red)
        }
        BasicText(stringResource(R.string.ie_wm_position), style = TextStyle(secondaryColor(), 12.sp, FontWeight.Medium))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WatermarkPosition.entries) { pos ->
                val label = if (pos == WatermarkPosition.Tiled) stringResource(R.string.ie_wm_tiled)
                else pos.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                EditorChip(label, p.position == pos, { vm.updateWatermark { it.copy(position = pos) } })
            }
        }
        SliderRow(stringResource(R.string.ie_size), p.size * 1000f, 15f..200f, backdrop, onChange = { v -> vm.updateWatermark { it.copy(size = v / 1000f) } })
        SliderRow(stringResource(R.string.ie_opacity), p.alpha * 100f, 5f..100f, backdrop,
            onChange = { v -> vm.updateWatermark { it.copy(alpha = v / 100f) } }, valueText = { "${it.roundToInt()}%" })
        SliderRow(stringResource(R.string.ie_rotation), p.rotation, -90f..90f, backdrop,
            onChange = { v -> vm.updateWatermark { it.copy(rotation = v) } }, valueText = { "${it.roundToInt()}°" },
            onReset = { vm.updateWatermark { it.copy(rotation = 0f) } })
        PrimaryAction(stringResource(R.string.ie_apply), backdrop) { vm.applyWatermark() }
    }
}

@Composable
fun ResizeSheetContent(state: ImageEditorViewModel.UiState, backdrop: Backdrop, vm: ImageEditorViewModel) {
    var w by remember(state.fullWidth) { mutableStateOf(state.fullWidth.toString()) }
    var h by remember(state.fullHeight) { mutableStateOf(state.fullHeight.toString()) }
    var keep by remember { mutableStateOf(true) }
    val ratio = state.fullWidth.toFloat() / state.fullHeight.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTitle(stringResource(R.string.ie_more_resize))
        BasicText(stringResource(R.string.ie_current_size, state.fullWidth, state.fullHeight), style = TextStyle(secondaryColor(), 13.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.ie_width), style = TextStyle(secondaryColor(), 12.sp))
                GlassField(w, { s ->
                    w = s.filter(Char::isDigit).take(5)
                    if (keep) w.toIntOrNull()?.let { h = (it / ratio).roundToInt().coerceAtLeast(1).toString() }
                }, numeric = true)
            }
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.ie_height), style = TextStyle(secondaryColor(), 12.sp))
                GlassField(h, { s ->
                    h = s.filter(Char::isDigit).take(5)
                    if (keep) h.toIntOrNull()?.let { w = (it * ratio).roundToInt().coerceAtLeast(1).toString() }
                }, numeric = true)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.ie_keep_ratio), Modifier.weight(1f), style = TextStyle(textColor(), 14.sp))
            LiquidToggle(selected = { keep }, onSelect = { keep = it }, backdrop = backdrop)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(25, 50, 75)) { pct ->
                EditorChip("$pct%", false, {
                    w = (state.fullWidth * pct / 100).coerceAtLeast(1).toString()
                    h = (state.fullHeight * pct / 100).coerceAtLeast(1).toString()
                })
            }
        }
        PrimaryAction(stringResource(R.string.ie_apply), backdrop) {
            vm.applyResize(w.toIntOrNull() ?: 0, h.toIntOrNull() ?: 0)
        }
    }
}

@Composable
fun ExportSheetContent(state: ImageEditorViewModel.UiState, backdrop: Backdrop, vm: ImageEditorViewModel) {
    val e = state.export
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTitle(stringResource(R.string.ie_more_export))
        BasicText(stringResource(R.string.ie_format), style = TextStyle(secondaryColor(), 12.sp, FontWeight.Medium))
        GlassSegmentedControl(
            options = ExportFormat.entries.map { it.label },
            selectedIndex = e.format.ordinal,
            onSelect = { i -> vm.updateExport { it.copy(format = ExportFormat.entries[i]) } },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
        if (e.format != ExportFormat.Png) {
            SliderRow(stringResource(R.string.ie_quality), e.quality.toFloat(), 30f..100f, backdrop,
                onChange = { v -> vm.updateExport { it.copy(quality = v.roundToInt()) } })
        }
        BasicText(stringResource(R.string.ie_metadata), style = TextStyle(secondaryColor(), 12.sp, FontWeight.Medium))
        GlassSegmentedControl(
            options = listOf(stringResource(R.string.ie_meta_keep), stringResource(R.string.ie_meta_no_location), stringResource(R.string.ie_meta_none)),
            selectedIndex = e.metadata.ordinal,
            onSelect = { i -> vm.updateExport { it.copy(metadata = MetadataMode.entries[i]) } },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExifSheetContent(state: ImageEditorViewModel.UiState, backdrop: Backdrop, vm: ImageEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetTitle(stringResource(R.string.ie_more_exif))
        if (state.exif.isEmpty()) {
            BasicText(stringResource(R.string.ie_exif_empty), style = TextStyle(secondaryColor(), 14.sp))
        } else {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.exif.forEach { entry ->
                    Row(Modifier.fillMaxWidth()) {
                        BasicText(entry.label, Modifier.weight(0.4f), style = TextStyle(secondaryColor(), 13.sp))
                        BasicText(entry.value, Modifier.weight(0.6f), style = TextStyle(textColor(), 13.sp, FontWeight.Medium))
                    }
                }
            }
        }
        BasicText(stringResource(R.string.ie_metadata), style = TextStyle(secondaryColor(), 12.sp, FontWeight.Medium))
        GlassSegmentedControl(
            options = listOf(stringResource(R.string.ie_meta_keep), stringResource(R.string.ie_meta_no_location), stringResource(R.string.ie_meta_none)),
            selectedIndex = state.export.metadata.ordinal,
            onSelect = { i -> vm.updateExport { it.copy(metadata = MetadataMode.entries[i]) } },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

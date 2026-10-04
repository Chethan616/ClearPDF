package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassBottomSheet
import com.chethan616.clearpdf.ui.components.GlassColorPicker
import com.chethan616.clearpdf.ui.components.UnsavedChangesDialog
import com.chethan616.clearpdf.ui.components.GlassHeaderHeight
import com.chethan616.clearpdf.ui.components.GlassProgressBar
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.LocalBackToLibraryAction
import com.chethan616.clearpdf.ui.components.GlassToolButton
import com.chethan616.clearpdf.ui.components.GlassToolbar
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.AdjustPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.CropPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.DrawPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.EditorCropStage
import com.chethan616.clearpdf.ui.screen.imageeditor.EditorStage
import com.chethan616.clearpdf.ui.screen.imageeditor.ExifSheetContent
import com.chethan616.clearpdf.ui.screen.imageeditor.ExportSheetContent
import com.chethan616.clearpdf.ui.screen.imageeditor.FiltersPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.MorePanel
import com.chethan616.clearpdf.ui.screen.imageeditor.ResizeSheetContent
import com.chethan616.clearpdf.ui.screen.imageeditor.StageInput
import com.chethan616.clearpdf.ui.screen.imageeditor.TextPanel
import com.chethan616.clearpdf.ui.screen.imageeditor.WatermarkSheetContent
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel.MoreTool
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel.Tool
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Which colour the shared colour-picker sheet is editing. */
private enum class ColorTarget { Brush, TextFill, TextOutline, TextBackground, Watermark }

/**
 * Full image editor: canvas with a liquid-glass header (undo / redo / export), a context panel for
 * the active tool and a glass tool dock (Crop · Adjust · Filters · Draw · Text · More). All edits
 * are non-destructive (see [ImageEditorViewModel]); export renders at full resolution.
 */
@Composable
fun ImageEditorScreen(
    backdrop: LayerBackdrop,
    viewModel: ImageEditorViewModel,
    onBack: () -> Unit,
    onOpenPdf: (android.net.Uri) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val uiSensor = rememberUISensor()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val contentBackdrop = rememberLayerBackdrop()
    val glass = rememberCombinedBackdrop(backdrop, contentBackdrop)

    var sheet by remember { mutableStateOf<MoreTool?>(null) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var bottomHeight by remember { mutableIntStateOf(0) }

    val requestBack = { if (state.hasEdits) showDiscard = true else onBack() }
    BackHandler(enabled = sheet == null && colorTarget == null && !showDiscard && state.hasEdits) { showDiscard = true }
    BackHandler(enabled = sheet != null) { sheet = null }
    BackHandler(enabled = colorTarget != null) { colorTarget = null }

    val pickWatermarkImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setWatermarkImage(context, it) }
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2400); viewModel.dismissMessage() }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val stagePadding = PaddingValues(
        start = 12.dp,
        end = 12.dp,
        top = statusTop + GlassHeaderHeight + 16.dp,
        bottom = with(density) { bottomHeight.toDp() } + 8.dp
    )

    Box(Modifier.fillMaxSize()) {
        // ── Document layer (solid surface; the glass above refracts it) ──
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LiquidGlassColors.Blue, strokeWidth = 2.5.dp)
                }
                state.error != null || state.preview == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(state.error ?: stringResource(R.string.open_image_failed), style = TextStyle(sub, 14.sp))
                }
                state.tool == Tool.Crop && state.crop != null -> EditorCropStage(
                    session = state.crop!!,
                    contentPadding = stagePadding,
                    onCropped = { r, o -> viewModel.onCropped(r, o) },
                    onPerspective = { viewModel.onPerspectiveCropped(it) }
                )
                else -> {
                    val input: StageInput = when {
                        comparing -> StageInput.None
                        state.tool == Tool.Draw -> StageInput.Brush(state.brush) { viewModel.addStroke(it) }
                        state.tool == Tool.Text -> StageInput.Text(state.text) { f -> viewModel.updateText(f) }
                        state.tool == Tool.More && state.more == MoreTool.Background ->
                            StageInput.Mask(state.bgBrushWidth, state.bgRestore) { viewModel.addMaskStroke(it) }
                        else -> StageInput.None
                    }
                    EditorStage(
                        bitmap = if (comparing) state.original!! else state.preview!!,
                        contentPadding = stagePadding,
                        input = input
                    )
                }
            }
        }

        // ── Header ──
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .height(GlassHeaderHeight),
            contentAlignment = Alignment.Center
        ) {
            GlassScreenHeaderRow(
                title = state.fileName.ifBlank { stringResource(R.string.image_editor_title) },
                backdrop = glass,
                onBack = requestBack,
                onBackLongPress = if (state.hasEdits) requestBack else LocalBackToLibraryAction.current,
                backLongPressLabel = if (state.hasEdits) stringResource(R.string.back) else null,
                trailing = {
                    HeaderIcon(Icons.Rounded.Undo, stringResource(R.string.ie_undo), state.canUndo, glass) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick); viewModel.undo()
                    }
                    HeaderIcon(Icons.Rounded.Redo, stringResource(R.string.ie_redo), state.canRedo, glass) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick); viewModel.redo()
                    }
                    LiquidIconButton(onClick = { sheet = MoreTool.Export }, backdrop = glass, tint = LiquidGlassColors.Blue) {
                        Icon(Icons.Rounded.SaveAlt, stringResource(R.string.ie_save), Modifier.size(18.dp), Color.White)
                    }
                }
            )
        }

        // ── Compare (press & hold) + export progress ──
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = GlassHeaderHeight + 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(state.hasEdits && state.tool != Tool.Crop && !state.isLoading, enter = fadeIn(), exit = fadeOut()) {
                Box {
                    LiquidIconButton(onClick = {}, backdrop = glass, isInteractive = false, surfaceColor = Color.White.copy(0.10f)) {
                        Icon(Icons.Rounded.Compare, stringResource(R.string.ie_compare), Modifier.size(18.dp), if (comparing) LiquidGlassColors.Blue else text)
                    }
                    // Touch overlay on top of the button so press-and-hold isn't eaten by its clickable.
                    Box(
                        Modifier.matchParentSize().pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                comparing = true
                                tryAwaitRelease()
                                comparing = false
                            })
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.exporting || state.isRendering && state.tool != Tool.Crop,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = GlassHeaderHeight + 6.dp)
        ) {
            GlassProgressBar(
                progress = if (state.exporting) state.exportProgress else null,
                backdrop = glass,
                modifier = Modifier.width(140.dp)
            )
        }

        // ── Context panel + tool dock ──
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { bottomHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!state.isLoading && state.error == null) {
                AnimatedContent(
                    targetState = state.tool to state.more,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                    label = "toolPanel"
                ) { (tool, _) ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlassPanel(glass, uiSensor)
                            .padding(14.dp)
                    ) {
                        when (tool) {
                            Tool.Crop -> CropPanel(state.crop, glass, viewModel)
                            Tool.Adjust -> AdjustPanel(state.adjustValues, state.selectedAdjust, glass, viewModel)
                            Tool.Filters -> FiltersPanel(state.filterSource, state.filterThumbs, state.selectedFilter, state.filterIntensity, glass, viewModel)
                            Tool.Draw -> DrawPanel(state.brush, glass, { colorTarget = ColorTarget.Brush }, viewModel)
                            Tool.Text -> TextPanel(state.text, glass, { target ->
                                colorTarget = when (target) { 0 -> ColorTarget.TextFill; 1 -> ColorTarget.TextOutline; else -> ColorTarget.TextBackground }
                            }, viewModel)
                            Tool.More -> MorePanel(state, glass, viewModel) { sheet = it }
                        }
                    }
                }
                GlassToolbar(backdrop = glass, modifier = Modifier.fillMaxWidth()) {
                    DockItem(Icons.Rounded.Crop, R.string.ie_tool_crop, Tool.Crop, state.tool, viewModel)
                    DockItem(Icons.Rounded.Tune, R.string.ie_tool_adjust, Tool.Adjust, state.tool, viewModel)
                    DockItem(Icons.Rounded.AutoAwesome, R.string.ie_tool_filters, Tool.Filters, state.tool, viewModel)
                    DockItem(Icons.Rounded.Brush, R.string.ie_tool_draw, Tool.Draw, state.tool, viewModel)
                    DockItem(Icons.Rounded.TextFields, R.string.ie_tool_text, Tool.Text, state.tool, viewModel)
                    DockItem(Icons.Rounded.MoreHoriz, R.string.ie_tool_more, Tool.More, state.tool, viewModel)
                }
            }
        }

        // ── Transient message ──
        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            BasicText(
                state.message.orEmpty(),
                style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.62f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        // ── Sheets & dialogs (must be the last children of the full-screen box) ──
        GlassBottomSheet(visible = sheet != null, onDismiss = { sheet = null }, backdrop = glass) {
            when (sheet) {
                MoreTool.Watermark -> WatermarkSheetContent(state, glass, viewModel,
                    onPickImage = { pickWatermarkImage.launch("image/*") },
                    onPickColor = { colorTarget = ColorTarget.Watermark })
                MoreTool.Resize -> ResizeSheetContent(state, glass, viewModel)
                MoreTool.Exif -> ExifSheetContent(state, glass, viewModel)
                MoreTool.Export -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ExportSheetContent(state, glass, viewModel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetButton(Icons.Rounded.PictureAsPdf, stringResource(R.string.ie_export_pdf), glass, Modifier.weight(1f), primary = false) {
                            sheet = null
                            viewModel.exportToPdf { uri -> uri?.let(onOpenPdf) }
                        }
                        SheetButton(Icons.Rounded.IosShare, stringResource(R.string.ie_share), glass, Modifier.weight(1f), primary = false) {
                            sheet = null
                            viewModel.share { uri, mime ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        }
                    }
                    SheetButton(Icons.Rounded.SaveAlt, stringResource(R.string.ie_save), glass, Modifier.fillMaxWidth(), primary = true) {
                        sheet = null
                        viewModel.saveToGallery()
                    }
                }
                else -> Spacer(Modifier.height(1.dp))
            }
        }

        GlassBottomSheet(visible = colorTarget != null, onDismiss = { colorTarget = null }, backdrop = glass) {
            val target = colorTarget
            val current = when (target) {
                ColorTarget.Brush -> state.brush.color
                ColorTarget.TextFill -> state.text.color
                ColorTarget.TextOutline -> state.text.outlineColor
                ColorTarget.TextBackground -> state.text.backgroundColor
                ColorTarget.Watermark -> state.watermark.color
                null -> 0
            }
            val clearable = target == ColorTarget.TextOutline || target == ColorTarget.TextBackground
            GlassColorPicker(
                color = Color(if (current == 0) 0xFFFFFFFF.toInt() else current),
                onColorChange = { c ->
                    val argb = c.toArgb()
                    when (target) {
                        ColorTarget.Brush -> viewModel.updateBrush { it.copy(color = argb) }
                        ColorTarget.TextFill -> viewModel.updateText { it.copy(color = argb) }
                        ColorTarget.TextOutline -> viewModel.updateText { it.copy(outlineColor = argb) }
                        ColorTarget.TextBackground -> viewModel.updateText { it.copy(backgroundColor = argb) }
                        ColorTarget.Watermark -> viewModel.updateWatermark { it.copy(color = argb) }
                        null -> Unit
                    }
                },
                backdrop = glass,
                showAlpha = target == ColorTarget.TextBackground,
                onClear = if (clearable) {
                    {
                        when (target) {
                            ColorTarget.TextOutline -> viewModel.updateText { it.copy(outlineColor = 0) }
                            ColorTarget.TextBackground -> viewModel.updateText { it.copy(backgroundColor = 0) }
                            else -> Unit
                        }
                        colorTarget = null
                    }
                } else null
            )
        }

        // Same "Save changes?" card as every editor; Save publishes to the gallery, then leaves.
        UnsavedChangesDialog(
            visible = showDiscard,
            onDiscard = { showDiscard = false; onBack() },
            onCancel = { showDiscard = false },
            onSave = { showDiscard = false; viewModel.saveToGallery(onSaved = onBack) },
            backdrop = glass,
            body = stringResource(R.string.unsaved_body_image)
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    backdrop: com.kyant.backdrop.Backdrop,
    onClick: () -> Unit
) {
    val text = LiquidGlassColors.text(LocalIsDarkMode.current)
    LiquidIconButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        isInteractive = enabled,
        surfaceColor = Color.White.copy(0.08f),
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }
    ) {
        Icon(icon, description, Modifier.size(18.dp), text)
    }
}

@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    tool: Tool,
    current: Tool,
    vm: ImageEditorViewModel
) {
    GlassToolButton(icon, stringResource(label), current == tool, { if (current != tool) vm.selectTool(tool) })
}

@Composable
private fun SheetButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier,
    primary: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val fg = if (primary) Color.White else LiquidGlassColors.text(isDark)
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = if (primary) LiquidGlassColors.Blue else Color.Unspecified,
        surfaceColor = if (primary) Color.Unspecified else if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f),
        modifier = modifier
    ) {
        Icon(icon, null, Modifier.size(18.dp), fg)
        Spacer(Modifier.width(8.dp))
        BasicText(label, style = TextStyle(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
    }
}

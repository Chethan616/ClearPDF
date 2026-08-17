package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
internal fun PdfViewerBottomToolbar(
    // display state
    activeTool: PdfEditTool,
    drawingToolActive: Boolean,
    showFindBar: Boolean,
    showSignaturePad: Boolean,
    activeImageId: Long?,
    currentColor: Color,
    currentColorLong: Long,
    currentStrokeWidth: Float,
    zoomScale: Float,
    hasEdits: Boolean,
    isExporting: Boolean,
    exportError: String?,
    exportMessage: String?,
    lastExportedUri: Uri?,
    selectedTextCount: Int,
    currentSelectedIds: Set<String>,
    activeIsSignature: Boolean,
    // marks for undo/clear (current page)
    currentPageMarks: MutableList<PdfMarkup>,
    // callbacks
    onSetActiveTool: (PdfEditTool) -> Unit,
    onToggleFindBar: () -> Unit,
    onShowSignaturePad: () -> Unit,
    onPickImage: () -> Unit,
    onResetZoom: () -> Unit,
    onShowSaveDialog: () -> Unit,
    onImageDone: () -> Unit,
    onReplaceImage: () -> Unit,
    onDeleteImage: () -> Unit,
    onSelectAllText: () -> Unit,
    onCopyText: () -> Unit,
    onHighlightSelected: () -> Unit,
    onUnderlineSelected: () -> Unit,
    onStrikeSelected: () -> Unit,
    onClearTextSelection: () -> Unit,
    onSetColorLong: (Long) -> Unit,
    onSetStrokeWidth: (Float) -> Unit,
    onDismissExportFeedback: () -> Unit,
    onOpenExportedFile: () -> Unit,
    onOpenAnotherPdf: () -> Unit,
    onShareDocument: () -> Unit,
    onRecolorSignature: (Long) -> Unit,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette (dark ink on light pages, white on dark pages).
    fg: Color,
    fgSoft: Color,
    glass: Color,
    chip: Color
) {
    val accent = Color(0xFF1976D2)

    val showDrawTools  = drawingToolActive
    val showOcrTools   = activeTool == PdfEditTool.SelectText || selectedTextCount > 0
    val showImageTools = activeTool == PdfEditTool.Image && activeImageId != null

    // Collapsed by default (just the "Editor Tools" pill + "Open PDF" circle). Tapping
    // the pill expands the tool set above it. Image selection auto-expands so its tools show.
    var editorOpen by remember { mutableStateOf(false) }
    // Long-press the "Open another PDF" circle to morph it into a Share action; auto-reverts.
    var shareMode by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(shareMode) { if (shareMode) { delay(3500); shareMode = false } }
    // Any active tool implies the editor is open (survives the chrome auto-hiding/returning).
    LaunchedEffect(activeTool, activeImageId) {
        if (activeTool != PdfEditTool.None || activeImageId != null) editorOpen = true
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // ── Draw / OCR / Image sub-toolbar ────────────────────────────────
        // Rises ABOVE the pinned main row with an Apple-style spring slide + fade.
        // Only this panel moves, so re-blur is confined to one surface.
        AnimatedVisibility(
            visible = editorOpen && (showDrawTools || showOcrTools || showImageTools) && !showFindBar && !showSignaturePad,
            enter   = fadeIn(tween(220)) +
                      slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit    = fadeOut(tween(150)) +
                      slideOutVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) { it }
        ) {
            Column(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor, glass).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back to the tool menu (this sub-toolbar is a focused mode).
                    LiquidIconButton(
                        onClick = { onSetActiveTool(PdfEditTool.None) },
                        backdrop = backdrop,
                        surfaceColor = Color(0xFFFF6B81).copy(0.9f),
                        modifier = Modifier.size(40.dp)
                    ) { CloseCrossIcon(Modifier.size(13.dp), Color.White) }
                    Box(Modifier.width(1.dp).height(26.dp).background(fg.copy(0.14f)))
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            showDrawTools -> {
                                // Icon toolbar; the active tool fills with the current ink
                                // colour via a smooth colour cross-fade.
                                val drawTools = listOf(
                                    Triple(PdfEditTool.Draw,      Icons.Rounded.Edit,                 R.string.viewer_pen),
                                    Triple(PdfEditTool.Highlight, Icons.Rounded.Brush,                R.string.viewer_highlight),
                                    Triple(PdfEditTool.Rect,      Icons.Rounded.CropSquare,           R.string.viewer_rect),
                                    Triple(PdfEditTool.Ellipse,   Icons.Rounded.RadioButtonUnchecked, R.string.viewer_oval),
                                    Triple(PdfEditTool.Line,      Icons.Rounded.Remove,               R.string.viewer_line),
                                    Triple(PdfEditTool.Arrow,     Icons.AutoMirrored.Rounded.ArrowForward, R.string.viewer_arrow)
                                )
                                drawTools.forEach { (tool, icon, labelRes) ->
                                    val active = activeTool == tool
                                    val surf by animateColorAsState(
                                        if (active) currentColor.copy(0.95f) else chip,
                                        tween(150), label = "toolSurface"
                                    )
                                    val ink by animateColorAsState(if (active) Color.White else fg, tween(150), label = "toolInk")
                                    LiquidIconButton(
                                        onClick = { onSetActiveTool(tool) },
                                        backdrop = backdrop,
                                        surfaceColor = surf,
                                        modifier = Modifier.size(40.dp)
                                    ) { Icon(icon, stringResource(labelRes), Modifier.size(19.dp), ink) }
                                }
                                Box(Modifier.width(1.dp).height(26.dp).background(fg.copy(0.14f)))
                                LiquidIconButton(
                                    onClick  = { if (currentPageMarks.isNotEmpty()) currentPageMarks.removeAt(currentPageMarks.lastIndex) },
                                    backdrop = backdrop,
                                    surfaceColor = chip,
                                    modifier = Modifier.size(40.dp)
                                ) { Icon(Icons.Rounded.Undo, stringResource(R.string.viewer_undo), Modifier.size(19.dp), fg) }
                                LiquidIconButton(
                                    onClick  = { currentPageMarks.clear() },
                                    backdrop = backdrop,
                                    surfaceColor = Color(0xFFC62828).copy(0.85f),
                                    modifier = Modifier.size(40.dp)
                                ) { Icon(Icons.Rounded.Delete, stringResource(R.string.viewer_clear), Modifier.size(19.dp), Color.White) }
                            }

                            showOcrTools -> {
                                LiquidButton(onClick = onSelectAllText, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_select_all), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onCopyText, backdrop = backdrop, tint = Color(0xFF7E57C2)) {
                                    BasicText(stringResource(R.string.copy), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onHighlightSelected, backdrop = backdrop, tint = Color(0xFFFFB300)) {
                                    BasicText(stringResource(R.string.viewer_highlight), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onUnderlineSelected, backdrop = backdrop, tint = Color(0xFF4CAF50)) {
                                    BasicText(stringResource(R.string.viewer_underline), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onStrikeSelected, backdrop = backdrop, tint = Color(0xFFEF5350)) {
                                    BasicText(stringResource(R.string.viewer_strike), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onClearTextSelection, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_clear), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                            }

                            showImageTools -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidButton(onClick = onImageDone, backdrop = backdrop, tint = Color(0xFF00C853)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), Color.White)
                                            BasicText(stringResource(R.string.viewer_done), style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
                                        }
                                    }

                                    LiquidButton(onClick = onReplaceImage, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp), Color.White)
                                            BasicText(
                                                if (activeIsSignature) stringResource(R.string.viewer_new_sign) else stringResource(R.string.viewer_replace),
                                                style = TextStyle(Color.White, 13.sp, FontWeight.Medium)
                                            )
                                        }
                                    }

                                    LiquidButton(onClick = onDeleteImage, backdrop = backdrop, tint = Color(0xFFEF5350)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), Color.White)
                                            BasicText(stringResource(R.string.delete), style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Attributes — stroke sizes (draw only) + colour beads on ONE line,
                // so the toolbar stays compact instead of stacking rows.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (showDrawTools) {
                        listOf("S" to 3f, "M" to 6f, "L" to 11f, "XL" to 18f).forEach { (label, w) ->
                            val sel = currentStrokeWidth == w
                            val surf by animateColorAsState(if (sel) currentColor.copy(0.85f) else chip, tween(150), label = "sizeSurface")
                            val ink by animateColorAsState(if (sel) Color.White else fg, tween(150), label = "sizeInk")
                            LiquidButton(onClick = { onSetStrokeWidth(w) }, backdrop = backdrop, surfaceColor = surf) {
                                BasicText(label, style = TextStyle(ink, 12.sp, FontWeight.Medium))
                            }
                        }
                        Box(Modifier.width(1.dp).height(24.dp).background(fg.copy(0.14f)))
                    }
                    listOf(
                        0xFF00BCD4L, 0xFFFFB300L, 0xFF4CAF50L, 0xFFEF5350L,
                        0xFF42A5F5L, 0xFFAB47BCL, 0xFF26A69AL, 0xFFE0E0E0L
                    ).forEach { cl ->
                        val sel = currentColorLong == cl
                        LiquidIconButton(
                            onClick      = { onSetColorLong(cl); if (showImageTools) onRecolorSignature(cl) },
                            backdrop     = backdrop,
                            surfaceColor = Color(cl),
                            modifier     = Modifier.size(if (sel) 34.dp else 28.dp)
                        ) {
                            if (sel) Icon(
                                Icons.Rounded.Check, null, Modifier.size(15.dp),
                                if (Color(cl).luminance() > 0.6f) Color.Black.copy(0.7f) else Color.White
                            )
                        }
                    }
                }
            }
        }

        // ── Export feedback row ────────────────────────────────────────────
        if (exportError != null || exportMessage != null || isExporting) {
            Row(
                Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor, glass).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                when {
                    isExporting -> BasicText(stringResource(R.string.viewer_saving), style = TextStyle(fgSoft, 12.sp))
                    exportError != null -> {
                        BasicText(exportError, style = TextStyle(Color(0xFFE53935), 12.sp))
                        LiquidButton(onClick = onDismissExportFeedback, backdrop = backdrop, surfaceColor = chip) {
                            BasicText(stringResource(R.string.dismiss), style = TextStyle(fg, 11.sp, FontWeight.Medium))
                        }
                    }
                    exportMessage != null -> {
                        BasicText(exportMessage, style = TextStyle(Color(0xFFB9F6CA), 12.sp))
                        if (lastExportedUri != null) {
                            LiquidButton(onClick = onOpenExportedFile, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                                BasicText(stringResource(R.string.open), style = TextStyle(Color.White, 11.sp, FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }


        // ── Tool selector row — revealed above the fixed "Editor Tools" bar when
        // the editor is open. Hidden once a sub-tool is active (focused mode) so the
        // screen isn't stacked with panels — the sub-toolbar's ✕ returns here.
        AnimatedVisibility(
            visible = editorOpen && !showDrawTools && !showOcrTools && !showFindBar && !showSignaturePad && activeImageId == null,
            enter   = fadeIn(tween(200)) +
                      slideInVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it },
            // Instant exit → releases its layout space immediately so the sub-toolbar
            // below never gets pushed up and then dropped ("stays top, then comes down").
            exit    = ExitTransition.None
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassPanel(backdrop, uiSensor, glass)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Each tool wears its own professional colour (like the Sign button):
                // a solid colour chip, brighter when active. White ink reads on all of them.
                fun toolSurface(color: Color, active: Boolean) = color.copy(if (active) 0.96f else 0.80f)

                val drawOn = drawingToolActive
                LiquidButton(
                    onClick  = { onSetActiveTool(if (drawOn) PdfEditTool.None else PdfEditTool.Draw) },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFF0097A7), drawOn)
                ) { BasicText(stringResource(R.string.viewer_draw_tools), style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                val selOn = activeTool == PdfEditTool.SelectText
                LiquidButton(
                    onClick  = { onSetActiveTool(if (selOn) PdfEditTool.None else PdfEditTool.SelectText) },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFF7B1FA2), selOn)
                ) {
                    BasicText(
                        if (selectedTextCount > 0) stringResource(R.string.viewer_ocr, selectedTextCount) else stringResource(R.string.viewer_select_text),
                        style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                    )
                }

                val imgOn = activeTool == PdfEditTool.Image
                LiquidButton(
                    onClick  = { onPickImage() },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFF1565C0), imgOn)
                ) { BasicText(stringResource(R.string.viewer_add_image), style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                val textOn = activeTool == PdfEditTool.Text
                LiquidButton(
                    onClick  = { onSetActiveTool(if (textOn) PdfEditTool.None else PdfEditTool.Text) },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFF00796B), textOn)
                ) { BasicText(stringResource(R.string.anno_text_title), style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                val noteOn = activeTool == PdfEditTool.Note
                LiquidButton(
                    onClick  = { onSetActiveTool(if (noteOn) PdfEditTool.None else PdfEditTool.Note) },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFFEF6C00), noteOn)
                ) { BasicText(stringResource(R.string.anno_note_title), style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                val eraseOn = activeTool == PdfEditTool.Eraser
                LiquidButton(
                    onClick  = { onSetActiveTool(if (eraseOn) PdfEditTool.None else PdfEditTool.Eraser) },
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFFC62828), eraseOn)
                ) { BasicText(stringResource(R.string.viewer_eraser), style = TextStyle(Color.White, 12.sp, FontWeight.Medium)) }

                LiquidButton(onClick = onShowSignaturePad, backdrop = backdrop, surfaceColor = toolSurface(Color(0xFF5E35B1), false)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Gesture, null, Modifier.size(14.dp), Color.White)
                        BasicText(stringResource(R.string.viewer_sign), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                    }
                }

                LiquidButton(
                    onClick = onToggleFindBar,
                    backdrop = backdrop,
                    surfaceColor = toolSurface(Color(0xFF0277BD), showFindBar)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(14.dp), Color.White)
                        BasicText(stringResource(R.string.viewer_find), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                    }
                }

                if (drawingToolActive || activeTool == PdfEditTool.SelectText || activeTool == PdfEditTool.Image || activeTool == PdfEditTool.Eraser || activeTool == PdfEditTool.Text || activeTool == PdfEditTool.Note) {
                    LiquidIconButton(
                        onClick  = { onSetActiveTool(PdfEditTool.None) },
                        backdrop = backdrop,
                        tint     = Color(0xFFEF5350),
                        modifier = Modifier.size(32.dp)
                    ) { CloseCrossIcon(Modifier.size(14.dp), Color.White) }
                }

                if (zoomScale > 1.01f) {
                    LiquidButton(onClick = onResetZoom, backdrop = backdrop, surfaceColor = chip) {
                        BasicText(
                            stringResource(R.string.viewer_reset_zoom, (zoomScale * 100 + 0.5f).toInt()),
                            style = TextStyle(fg, 12.sp, FontWeight.Medium)
                        )
                    }
                }

                if (hasEdits && !isExporting) {
                    LiquidButton(onClick = onShowSaveDialog, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                        BasicText(stringResource(R.string.viewer_save_edits), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                    }
                }
            }
        }

        // ── Collapsed home bar: one "Editor Tools" pill (expands the tools above it)
        // + a compact circular "Open another PDF" button. Hidden while a sub-tool is
        // active so that focused mode shows ONLY the sub-toolbar (one panel).
        AnimatedVisibility(
            visible = !showDrawTools && !showOcrTools && !showImageTools && !showFindBar && !showSignaturePad,
            enter   = fadeIn(tween(180)),
            exit    = ExitTransition.None
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = {
                        editorOpen = !editorOpen
                        if (!editorOpen) onSetActiveTool(PdfEditTool.None)
                    },
                    backdrop = backdrop,
                    tint = if (editorOpen) accent else Color.Unspecified,
                    surfaceColor = if (editorOpen) Color.Unspecified else glass,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), if (editorOpen) Color.White else fg)
                        BasicText(
                            stringResource(R.string.viewer_editor_tools),
                            style = TextStyle(if (editorOpen) Color.White else fg, 14.sp, FontWeight.SemiBold),
                            maxLines = 1
                        )
                    }
                }
                // Tap = open another PDF. Long-press morphs it into a Share button (icon
                // cross-fades, surface eases to the share-green), then a tap shares the doc.
                val shareGreen = Color(0xFF34C759)
                val morphSurface by animateColorAsState(
                    if (shareMode) shareGreen else glass, tween(220), label = "shareMorphSurface"
                )
                LiquidIconButton(
                    onClick = {
                        if (shareMode) { onShareDocument(); shareMode = false } else onOpenAnotherPdf()
                    },
                    onLongClick = {
                        shareMode = !shareMode
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    backdrop = backdrop,
                    surfaceColor = morphSurface,
                    modifier = Modifier.size(52.dp)
                ) {
                    Crossfade(targetState = shareMode, animationSpec = tween(200), label = "shareMorphIcon") { sharing ->
                        if (sharing) {
                            Icon(Icons.Rounded.IosShare, stringResource(R.string.viewer_share_document), Modifier.size(20.dp), Color.White)
                        } else {
                            Icon(Icons.Rounded.UploadFile, stringResource(R.string.viewer_open_another), Modifier.size(20.dp), fg)
                        }
                    }
                }
            }
        }
    }
}

package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Slideshow
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
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.ShareMorphButton
import com.chethan616.clearpdf.ui.components.carouselEdges
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.utils.UISensor
import com.chethan616.clearpdf.utils.DocKind
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * How long one face (selector or sub-toolbar) takes to fade out before the other fades in. The
 * fade-out tweens use the same value, so the incoming face starts exactly as the outgoing one lands
 * at alpha 0 — a clean sequential cross-fade with no overlap.
 */
private const val FaceHandoffMillis = 150

/**
 * A glass surface and its controls are siblings on purpose. [viewerGlass] owns a shaped offscreen
 * layer, which is exactly what gives the panel its clean rounded material; placing animated buttons
 * inside that layer clips their press-deformation at the panel edge. This surface keeps the glass
 * clipped and lets the interactive content paint in an overflow-safe layer above it.
 */
@Composable
private fun ViewerGlassOverflowSurface(
    modifier: Modifier,
    backdrop: LayerBackdrop,
    color: Color,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        Box(Modifier.matchParentSize().viewerGlass(backdrop, color))
        Box(Modifier.fillMaxWidth().zIndex(1f), content = content)
    }
}

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
    // Undo/clear are driven by the viewer's own history rather than by a list handed down here: the
    // page the user is drawing on is NOT `firstVisibleItemIndex`, so a list picked by the toolbar
    // was routinely the wrong one.
    canUndo: Boolean,
    // callbacks
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
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
    onEditorOpenChanged: (Boolean) -> Unit = {},
    // Same purpose as [onEditorOpenChanged]: a long-press on the share capsule is a gesture the
    // viewer cannot see, so without this the 5s chrome auto-hide fires mid-hold and takes the button
    // out from under the finger.
    onShareHoldChanged: (Boolean) -> Unit = {},
    onRecolorSignature: (Long) -> Unit,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette (dark ink on light pages, white on dark pages).
    fg: Color,
    fgSoft: Color,
    glass: Color,
    chip: Color,
    // Original document family (derived from the file name) so tools can adapt — e.g. PPT shows a
    // "coming soon" placeholder instead of the annotation tools.
    docKind: DocKind = DocKind.Pdf
) {
    val accent = Color(0xFF1976D2)

    // The floating pills paint nothing at all, exactly as Home's controls do: `LiquidIconButton` is
    // called there with no `surfaceColor`, so its `onDrawSurface` is a no-op and the button is pure
    // refraction. `drawRect(Color.Transparent)` is the same no-op for the `viewerGlass` surfaces
    // here. The header's back and search circles already work this way; without this the Editor
    // Tools pill, the tool row and the share capsule were the only chrome left carrying a tint, and
    // sitting beside clear circles they read as slabs.
    //
    // `glass` is deliberately still used for the *panels* (the draw/OCR/image sub-toolbar and the
    // export-feedback strip): those are dense rows of controls that need a plate to sit on, and they
    // cover the document rather than floating over it.
    val pillGlass = Color.Transparent

    val showDrawTools  = drawingToolActive
    val showOcrTools   = activeTool == PdfEditTool.SelectText || selectedTextCount > 0
    val showImageTools = activeTool == PdfEditTool.Image && activeImageId != null

    // The two faces of the toolbar never share the screen: the SELECTOR face (the tool chips + the
    // blue "Editor Tools" pill) and the SUB-TOOLBAR face (draw / OCR / image). `subActive` is the
    // dimension that swaps them.
    val subActive = showDrawTools || showOcrTools || showImageTools
    // Apple-style hand-off. The old code removed the selector face INSTANTLY (ExitTransition.None)
    // while the sub-toolbar faded in, so the two vanished/appeared on top of each other. Instead we
    // run a tiny two-phase gate: the outgoing face fades fully out, and only THEN does the incoming
    // face fade in — they never overlap in layout, which is also what used to make the sub-toolbar
    // open on top and then visibly drop as the pill collapsed.
    var selectorGate by remember { mutableStateOf(!subActive) }
    var subGate by remember { mutableStateOf(subActive) }
    LaunchedEffect(subActive) {
        if (subActive) {
            selectorGate = false                       // chips + Editor-Tools pill begin fading out
            delay(FaceHandoffMillis.toLong())          // wait for them to clear
            subGate = true                             // sub-toolbar fades in
        } else {
            subGate = false                            // sub-toolbar begins fading out
            delay(FaceHandoffMillis.toLong())
            selectorGate = true                        // chips + pill fade back in
        }
    }

    // Collapsed by default (just the "Editor Tools" pill + "Open PDF" circle). Tapping
    // the pill expands the tool set above it. Image selection auto-expands so its tools show.
    var editorOpen by remember { mutableStateOf(false) }
    // Idle: the tool panels cover the FULL width (scale 1). While the share capsule is morphed up,
    // the panels above the Editor-Tools pill COMPRESS horizontally toward the left (scaleX), freeing
    // room for the cylinder — the whole pill + its buttons shrink together, so nothing is cut.
    var shareActive by remember { mutableStateOf(false) }
    val toolCompress by animateFloatAsState(
        if (shareActive) 0.84f else 1f,
        // Bounce, matching the share capsule's own morph — both now run on GlassMotion.morph(), so the
        // pill springs shut (and back open) with the same weight the cylinder has instead of deflating
        // limply beside it. Safe to overshoot because this drives `scaleX`, a DRAW-time property: no
        // per-frame re-measure of the glass, unlike a width/height spring (see GlassMotion's KDoc).
        GlassMotion.morph(),
        label = "toolCompress"
    )
    // Tell the viewer when the Editor Tools panel is open so it won't auto-hide the chrome.
    LaunchedEffect(editorOpen) { onEditorOpenChanged(editorOpen) }
    // Any active tool implies the editor is open (survives the chrome auto-hiding/returning).
    LaunchedEffect(activeTool, activeImageId) {
        if (activeTool != PdfEditTool.None || activeImageId != null) editorOpen = true
    }

    // The share capsule lives as an OVERLAY sibling of the toolbar column inside this wrapper Box.
    // Both are bottom-anchored: when the capsule morphs taller than the column, the WRAPPER grows
    // (real layout height = strictly upward, no overflow/clip), while the column stays pinned to the
    // bottom — so the tool panels and the "Editor Tools" pill never move.
    Box(Modifier.fillMaxWidth()) {
    Column(
        Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(2f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ── Draw / OCR / Image sub-toolbar ────────────────────────────────
        // Rises ABOVE the pinned main row with an Apple-style spring slide + fade.
        // Only this panel moves, so re-blur is confined to one surface.
        AnimatedVisibility(
            // `subGate` is delayed by [FaceHandoffMillis] after a sub-tool becomes active, so the
            // selector face has already faded out before this fades in — the two never coexist.
            visible = subGate && editorOpen && !showFindBar && !showSignaturePad,
            // Fade only for the LAYOUT (one measure), and a draw-time bottom-anchored `scaleY` unfurl
            // for the motion — the same technique as the Editor-Tools reveal. `expandVertically` here
            // re-measured this `viewerGlass` panel every frame and re-ran its blur + lens with it,
            // which is why the reveal read as rigid/instant rather than liquid.
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(FaceHandoffMillis))
        ) {
            val reveal by transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) spring(dampingRatio = 0.72f, stiffness = 300f)
                    else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
                },
                label = "drawToolsReveal"
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            ViewerGlassOverflowSurface(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        scaleY = 0.9f + 0.1f * reveal
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (1f - reveal) * 8.dp.toPx()
                    }
                    .graphicsLayer { scaleX = toolCompress; transformOrigin = TransformOrigin(0f, 0.5f) },
                backdrop = backdrop,
                color = glass
            ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
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
                    // Same curved-edge treatment as the main Editor-Tools carousel: clip to the
                    // rounded shape (not the scroll's straight rectangular edge) and fade the ends so
                    // the pen / shapes / OCR buttons slide away behind the capsule curve instead of
                    // being chopped by a hard vertical line. Content padding keeps the first/last
                    // button spaced like the rest.
                    val toolRowScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .carouselEdges(toolRowScroll, clipContent = false)
                            .horizontalScroll(toolRowScroll)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            showDrawTools -> {
                                // Icon toolbar; the active tool fills with the current ink
                                // colour via a smooth colour cross-fade.
                                // Word docs only get the Highlight tool here (no pen/shapes) — a
                                // curated markup experience; other doc kinds get the full shape set.
                                val drawTools = if (docKind == DocKind.Word) listOf(
                                    Triple(PdfEditTool.Highlight, Icons.Rounded.Brush, R.string.viewer_highlight)
                                ) else listOf(
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
                                    onClick  = onUndo,
                                    backdrop = backdrop,
                                    surfaceColor = chip,
                                    modifier = Modifier.size(40.dp)
                                ) { Icon(Icons.Rounded.Undo, stringResource(R.string.viewer_undo), Modifier.size(19.dp), fg.copy(if (canUndo) 1f else 0.35f)) }
                                LiquidIconButton(
                                    onClick  = onClearPage,
                                    backdrop = backdrop,
                                    surfaceColor = Color(0xFFC62828).copy(0.85f),
                                    modifier = Modifier.size(40.dp)
                                ) { Icon(Icons.Rounded.Delete, stringResource(R.string.viewer_clear), Modifier.size(19.dp), Color.White) }
                            }

                            showOcrTools -> {
                                // Plain, native-menu-style neutral buttons — no per-action rainbow
                                // tinting — matching the in-context selection toolbar's look.
                                LiquidButton(onClick = onSelectAllText, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_select_all), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onCopyText, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.copy), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onHighlightSelected, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_highlight), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onUnderlineSelected, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_underline), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(onClick = onStrikeSelected, backdrop = backdrop, surfaceColor = chip) {
                                    BasicText(stringResource(R.string.viewer_strike), style = TextStyle(fg, 12.sp, FontWeight.Medium))
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
                // so the toolbar stays compact instead of stacking rows. Same curved-edge fade as the
                // tool row above, so the colour beads disappear behind the capsule curve rather than a
                // straight cut.
                val attrRowScroll = rememberScrollState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .carouselEdges(attrRowScroll, clipContent = false)
                        .horizontalScroll(attrRowScroll)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
        }

        // ── Export feedback row ────────────────────────────────────────────
        if (exportError != null || exportMessage != null || isExporting) {
            Row(
                Modifier.fillMaxWidth().viewerGlass(backdrop, glass).padding(12.dp),
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
            // Gated by `selectorGate`, which drops the instant a sub-tool becomes active — so the
            // chips fade out FIRST and the sub-toolbar (held back by `subGate`) only fades in once
            // this space is clear. The two faces no longer overlap, so a real fading exit is finally
            // safe here: it can hold its layout space while it fades because nothing is fading in on
            // top of it yet.
            visible = selectorGate && editorOpen && !showFindBar && !showSignaturePad && activeImageId == null,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(FaceHandoffMillis))
        ) {
            val reveal by transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) spring(dampingRatio = 0.72f, stiffness = 300f)
                    else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
                },
                label = "editorToolsReveal"
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            Box(
                Modifier
                    .graphicsLayer {
                        scaleY = 0.9f + 0.1f * reveal
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (1f - reveal) * 8.dp.toPx()
                    }
                    .zIndex(1f)
            ) {
            if (docKind == DocKind.Ppt) {
                // PowerPoint editing isn't available yet — a friendly placeholder instead of the
                // annotation tools (which don't map cleanly onto slides).
                ViewerGlassOverflowSurface(
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = toolCompress; transformOrigin = TransformOrigin(0f, 0.5f) },
                    backdrop = backdrop,
                    color = pillGlass
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Slideshow, null, Modifier.size(20.dp), fg)
                        BasicText(stringResource(R.string.viewer_tools_coming_soon), style = TextStyle(fg, 14.sp, FontWeight.SemiBold))
                    }
                }
            } else if (docKind == DocKind.Word) {
                // Curated Word reading/markup set — Select Text, Highlight, Find. No PDF-centric
                // shapes / add-image / text-box / note / eraser / sign.
                val wordScroll = rememberScrollState()
                ViewerGlassOverflowSurface(
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = toolCompress; transformOrigin = TransformOrigin(0f, 0.5f) },
                    backdrop = backdrop,
                    color = pillGlass
                ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        // Keep the existing fade mask and scroll behavior; the extra vertical
                        // breathing room is what lets a pressed chip deform without touching the
                        // viewport's top or bottom edge.
                        .carouselEdges(wordScroll, clipContent = false)
                        .horizontalScroll(wordScroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fun wSurface(color: Color, active: Boolean) = color.copy(if (active) 0.96f else 0.80f)
                    val selOn = activeTool == PdfEditTool.SelectText
                    LiquidButton(onClick = { onSetActiveTool(if (selOn) PdfEditTool.None else PdfEditTool.SelectText) }, backdrop = backdrop, surfaceColor = wSurface(Color(0xFF7B1FA2), selOn)) {
                        BasicText(
                            if (selectedTextCount > 0) stringResource(R.string.viewer_ocr, selectedTextCount) else stringResource(R.string.viewer_select_text),
                            style = TextStyle(Color.White, 12.sp, FontWeight.Medium)
                        )
                    }
                    val hlOn = activeTool == PdfEditTool.Highlight
                    LiquidButton(onClick = { onSetActiveTool(if (hlOn) PdfEditTool.None else PdfEditTool.Highlight) }, backdrop = backdrop, surfaceColor = wSurface(Color(0xFFF9A825), hlOn)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brush, null, Modifier.size(14.dp), Color.White)
                            BasicText(stringResource(R.string.viewer_highlight), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                        }
                    }
                    LiquidButton(onClick = onToggleFindBar, backdrop = backdrop, surfaceColor = wSurface(Color(0xFF0277BD), showFindBar)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(14.dp), Color.White)
                            BasicText(stringResource(R.string.viewer_find), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                        }
                    }
                }
                }
            } else {
            val toolScroll = rememberScrollState()
            ViewerGlassOverflowSurface(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer { scaleX = toolCompress; transformOrigin = TransformOrigin(0f, 0.5f) },
                backdrop = backdrop,
                color = pillGlass
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // The existing carouselEdges mask remains unchanged; this row is simply
                    // rendered above the separate glass sibling so its chips can overflow cleanly.
                    .carouselEdges(toolScroll, clipContent = false)
                    .horizontalScroll(toolScroll)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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

                // Undo used to live only inside the draw strip, so placing a text box, a note, an
                // image or a signature left nothing to undo with. The draw strip has its own copy,
                // hence the exclusion here.
                if (canUndo && !drawingToolActive) {
                    LiquidButton(onClick = onUndo, backdrop = backdrop, surfaceColor = chip) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Undo, null, Modifier.size(14.dp), fg)
                            BasicText(stringResource(R.string.viewer_undo), style = TextStyle(fg, 12.sp, FontWeight.Medium))
                        }
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
            }
            }
        }

        // ── Collapsed home bar: one "Editor Tools" pill (expands the tools above it)
        // + a compact circular "Open another PDF" button. Hidden while a sub-tool is
        // active so that focused mode shows ONLY the sub-toolbar (one panel).
        AnimatedVisibility(
            // Same `selectorGate` as the tool chips above, so the blue "Editor Tools" pill fades out
            // in lockstep with them — "both pills at the same time" — before the sub-toolbar arrives.
            visible = selectorGate && !showFindBar && !showSignaturePad,
            enter   = fadeIn(tween(180)),
            exit    = fadeOut(tween(FaceHandoffMillis))
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Bottom-align so the "Editor Tools" pill stays pinned to the toolbar's base while
                // the share capsule (whose real layout height grows) extends UPWARD only — the
                // toolbar column is bottom-anchored on screen, so added height goes up.
                verticalAlignment = Alignment.Bottom
            ) {
                LiquidButton(
                    onClick = {
                        editorOpen = !editorOpen
                        if (!editorOpen) onSetActiveTool(PdfEditTool.None)
                    },
                    backdrop = backdrop,
                    tint = if (editorOpen) accent else Color.Unspecified,
                    surfaceColor = if (editorOpen) Color.Unspecified else pillGlass,
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
                // Reserve the circle's slot at the home-bar level so the pill never sits under the
                // share button. (The tool panels above cover full width and only FADE on morph.)
                Spacer(Modifier.size(52.dp))
            }
        }
        }

        // ── Share capsule OVERLAY ──────────────────────────────────────────
        // Sits over the reserved slot at the bottom-right. Because it's a sibling of the column
        // (not inside it) and bottom-anchored, morphing it taller grows only this wrapper Box
        // upward — the column (tool panels + pill) stays pinned to the base and never moves.
        AnimatedVisibility(
            // Sits in the pill row's reserved slot, so it rides the same `selectorGate` and fades out
            // alongside the pill instead of popping away on its own.
            visible = selectorGate && !showFindBar && !showSignaturePad,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(FaceHandoffMillis)),
            modifier = Modifier.align(Alignment.BottomEnd).zIndex(3f)
        ) {
            ShareMorphButton(
                backdrop = backdrop,
                glass = pillGlass,
                fg = fg,
                onOpen = onOpenAnotherPdf,
                onShare = onShareDocument,
                onShareModeChanged = { shareActive = it; onShareHoldChanged(it) }
            )
        }
    }
}

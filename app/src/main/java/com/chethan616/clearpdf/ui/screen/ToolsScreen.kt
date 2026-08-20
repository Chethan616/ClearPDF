package com.chethan616.clearpdf.ui.screen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassSearchHeader
import com.chethan616.clearpdf.ui.components.GlassSectionLabel
import com.chethan616.clearpdf.ui.components.ToolTile
import com.chethan616.clearpdf.ui.components.ToolTileWide
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

private data class ToolSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

private data class ToolSection(val label: String, val tools: List<ToolSpec>)

@Composable
fun ToolsScreen(
    backdrop: LayerBackdrop,
    onNavigateToOpenPdf: () -> Unit,
    onNavigateToMergePdf: () -> Unit,
    onNavigateToSplitPdf: () -> Unit,
    onNavigateToCompressPdf: () -> Unit,
    onNavigateToCreatePdf: () -> Unit,
    onNavigateToOrganizePdf: () -> Unit = {},
    onNavigateToExtractText: () -> Unit = {},
    onNavigateToImagesToPdf: () -> Unit = {},
    onNavigateToDecryptPdf: () -> Unit = {},
    onNavigateToEncryptPdf: () -> Unit = {},
    onNavigateToPdfToImages: () -> Unit = {},
    onNavigateToWatermark: () -> Unit = {},
    onNavigateToExtractPages: () -> Unit = {},
    onNavigateToPageNumbers: () -> Unit = {},
    onNavigateToFlatten: () -> Unit = {},
    onNavigateToImageTools: () -> Unit = {},
    // Web/HTML to PDF is hidden in the privacy-focused release — its only networked feature is URL
    // capture. The route and screen are kept; the entry point is simply not listed.
    @Suppress("UNUSED_PARAMETER") onNavigateToHtmlToPdf: () -> Unit = {},
    onNavigateToFillForm: () -> Unit = {}
) {
    val uiSensor = rememberUISensor()
    val isDarkMode = LocalIsDarkMode.current
    val density = LocalDensity.current.density

    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val openPdf = ToolSpec(
        "open", stringResource(R.string.tool_open_pdf), stringResource(R.string.tool_open_pdf_sub),
        LiquidGlassColors.Blue, Icons.Rounded.FileOpen, onNavigateToOpenPdf
    )

    // Built fresh each composition on purpose. The previous version memoised this behind a
    // remember() with 34 dependency keys, which cost more to compare than these 17 small objects
    // cost to allocate — and invalidated wholesale whenever any single callback changed identity.
    val sections = listOf(
        ToolSection(
            stringResource(R.string.tools_section_organize),
            listOf(
                ToolSpec("merge", stringResource(R.string.tool_merge), stringResource(R.string.tool_merge_sub), LiquidGlassColors.Red, Icons.AutoMirrored.Rounded.CallMerge, onNavigateToMergePdf),
                ToolSpec("split", stringResource(R.string.tool_split), stringResource(R.string.tool_split_sub), LiquidGlassColors.Purple, Icons.AutoMirrored.Rounded.CallSplit, onNavigateToSplitPdf),
                ToolSpec("organize", stringResource(R.string.tool_organize), stringResource(R.string.tool_organize_sub), LiquidGlassColors.Teal, Icons.Rounded.Reorder, onNavigateToOrganizePdf),
                ToolSpec("extract_pages", stringResource(R.string.tool_extract_pages), stringResource(R.string.tool_extract_pages_sub), Color(0xFF00897B), Icons.Rounded.ContentCut, onNavigateToExtractPages)
            )
        ),
        ToolSection(
            stringResource(R.string.tools_section_convert),
            listOf(
                ToolSpec("images", stringResource(R.string.tool_images), stringResource(R.string.tool_images_sub), LiquidGlassColors.Indigo, Icons.Rounded.Image, onNavigateToImagesToPdf),
                ToolSpec("pdf_to_images", stringResource(R.string.tool_pdf_to_images), stringResource(R.string.tool_pdf_to_images_sub), Color(0xFF00ACC1), Icons.Rounded.Collections, onNavigateToPdfToImages),
                ToolSpec("extract", stringResource(R.string.tool_extract), stringResource(R.string.tool_extract_sub), Color(0xFF5AC8FA), Icons.Rounded.TextSnippet, onNavigateToExtractText),
                ToolSpec("create", stringResource(R.string.tool_create), stringResource(R.string.tool_create_sub), LiquidGlassColors.Orange, Icons.AutoMirrored.Rounded.NoteAdd, onNavigateToCreatePdf)
            )
        ),
        ToolSection(
            stringResource(R.string.tools_section_edit),
            listOf(
                ToolSpec("watermark", stringResource(R.string.tool_watermark), stringResource(R.string.tool_watermark_sub), Color(0xFFAD1457), Icons.Rounded.BrandingWatermark, onNavigateToWatermark),
                ToolSpec("page_numbers", stringResource(R.string.tool_page_numbers), stringResource(R.string.tool_page_numbers_sub), Color(0xFF3949AB), Icons.Rounded.Numbers, onNavigateToPageNumbers),
                ToolSpec("fill_form", stringResource(R.string.tool_fill_form), stringResource(R.string.tool_fill_form_sub), Color(0xFF00695C), Icons.Rounded.EditNote, onNavigateToFillForm),
                ToolSpec("image_tools", stringResource(R.string.tool_image_tools), stringResource(R.string.tool_image_tools_sub), Color(0xFFF4511E), Icons.Rounded.PhotoSizeSelectLarge, onNavigateToImageTools)
            )
        ),
        ToolSection(
            stringResource(R.string.tools_section_optimize),
            listOf(
                ToolSpec("compress", stringResource(R.string.tool_compress), stringResource(R.string.tool_compress_sub), LiquidGlassColors.Green, Icons.Rounded.Compress, onNavigateToCompressPdf),
                ToolSpec("flatten", stringResource(R.string.tool_flatten), stringResource(R.string.tool_flatten_sub), Color(0xFF6D4C41), Icons.Rounded.Layers, onNavigateToFlatten),
                ToolSpec("encrypt", stringResource(R.string.tool_encrypt_pdf), stringResource(R.string.tool_encrypt_pdf_sub), LiquidGlassColors.Indigo, Icons.Rounded.Lock, onNavigateToEncryptPdf),
                ToolSpec("decrypt", stringResource(R.string.tool_decrypt_pdf), stringResource(R.string.tool_decrypt_pdf_sub), LiquidGlassColors.Purple, Icons.Rounded.LockOpen, onNavigateToDecryptPdf)
            )
        )
    )

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    // One transition, one frame clock. Sections stagger via delayMillis instead of each running its
    // own animateFloatAsState.
    val entrance = updateTransition(isVisible, label = "toolsEntrance")

    val trimmed = query.trim()
    val searching = trimmed.isNotBlank()
    val results = if (!searching) emptyList() else {
        (listOf(openPdf) + sections.flatMap { it.tools }).filter {
            it.title.contains(trimmed, ignoreCase = true) || it.subtitle.contains(trimmed, ignoreCase = true)
        }
    }

    GlassScreenScaffold(
        backdrop = backdrop,
        contentHorizontalPadding = 16.dp,
        headerHorizontalPadding = 16.dp,
        contentBottomPadding = 84.dp,
        header = { headerBackdrop ->
            // Holds a glass title pill and a glass circle, so it fades in place. Pinned above
            // the list, sampling the content layer so the tiles refract through it as they scroll.
            Box(entrance.glassFadeModifier(0)) {
                GlassSearchHeader(
                    title = stringResource(R.string.tools_title),
                    backdrop = headerBackdrop,
                    uiSensor = uiSensor,
                    query = query,
                    onQueryChange = { query = it },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    searchHint = stringResource(R.string.tools_search_hint)
                )
            }
        }
    ) { contentPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (searching) {
            item(key = "results") {
                if (results.isEmpty()) {
                    BasicText(
                        stringResource(R.string.tools_no_matches),
                        style = TextStyle(LiquidGlassColors.secondary(isDarkMode), 14.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        results.forEach { tool ->
                            ToolTileWide(tool.title, tool.subtitle, tool.accent, tool.icon, tool.onClick)
                        }
                    }
                }
            }
        } else {
            item(key = "primary") {
                Box(entrance.tileEntranceModifier(0, density)) {
                    ToolTileWide(openPdf.title, openPdf.subtitle, openPdf.accent, openPdf.icon, openPdf.onClick)
                }
            }

            sections.forEachIndexed { index, section ->
                item(key = section.label) {
                    // Five stagger slots per section — the label, then its four tiles — so the whole
                    // screen cascades top-to-bottom instead of four sections restarting in place.
                    val base = 1 + index * 5
                    Column {
                        Box(entrance.tileEntranceModifier(base, density)) {
                            GlassSectionLabel(section.label)
                        }
                        ToolSectionPanel(section, backdrop, uiSensor, entrance, base, density)
                    }
                }
            }
        }
    }
    }
}

/**
 * One glass surface per section. The tiles inside are flat, so a section of four tools costs a
 * single blur+lens pass rather than four.
 */
@Composable
private fun ToolSectionPanel(
    section: ToolSection,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    entrance: Transition<Boolean>,
    base: Int,
    density: Float
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(entrance.glassFadeModifier(base))
            .liquidGlassPanel(backdrop, uiSensor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        section.tools.chunked(2).forEachIndexed { rowIdx, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { colIdx, tool ->
                    // Flat index across both rows: the label took slot `base`, so the four tiles
                    // occupy base+1..base+4 and the cascade keeps running top-to-bottom.
                    ToolTile(
                        title = tool.title,
                        subtitle = tool.subtitle,
                        accent = tool.accent,
                        icon = tool.icon,
                        onClick = tool.onClick,
                        modifier = Modifier
                            .weight(1f)
                            .then(entrance.tileEntranceModifier(base + 1 + rowIdx * 2 + colIdx, density))
                    )
                }
                // Keep a lone trailing tile at half width instead of letting it stretch.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One stagger step. Everything on the screen is placed on this grid so the cascade reads evenly. */
private const val StaggerStepMs = 35

/**
 * Overshoots past 1.0 and settles back — the "bounce". It is only ever applied to scale and
 * translation, which are draw-time properties, so the overshoot costs nothing beyond the frames it
 * already takes. Alpha deliberately never gets this curve: an overshooting alpha clips at 1.0 and
 * reads as a flicker rather than a bounce.
 */
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Entrance for surfaces that contain liquid glass — the header pill and the section panels.
 *
 * **Alpha only, never translation.** A `drawBackdrop` surface samples the backdrop for the region it
 * currently covers, so moving one re-runs blur+lens every single frame. Four section panels plus the
 * header's glass pill and circle all sliding at once is what made this screen stutter. Holding them
 * still keeps their sample region fixed for the whole entrance.
 */
@Composable
private fun Transition<Boolean>.glassFadeModifier(index: Int): Modifier {
    val alpha by animateFloat(
        transitionSpec = { tween(durationMillis = 320, delayMillis = StaggerStepMs * index, easing = FastOutSlowInEasing) },
        label = "glassFade$index"
    ) { if (it) 1f else 0f }
    return Modifier.graphicsLayer { this.alpha = alpha }
}

/**
 * Entrance for flat content — the tool tiles and the section labels. These have no `drawBackdrop`,
 * so they are free to spring around: this is where the bounce lives.
 *
 * All three values are read inside the `graphicsLayer` lambda, which defers them to the draw phase,
 * so the whole cascade invalidates draw without ever recomposing the screen.
 */
@Composable
private fun Transition<Boolean>.tileEntranceModifier(index: Int, density: Float): Modifier {
    val scale by animateFloat(
        transitionSpec = { tween(durationMillis = 420, delayMillis = StaggerStepMs * index, easing = EaseOutBack) },
        label = "tileScale$index"
    ) { if (it) 1f else 0.86f }
    val offsetY by animateFloat(
        transitionSpec = { tween(durationMillis = 420, delayMillis = StaggerStepMs * index, easing = EaseOutBack) },
        label = "tileOffset$index"
    ) { if (it) 0f else 18f }
    val alpha by animateFloat(
        transitionSpec = { tween(durationMillis = 260, delayMillis = StaggerStepMs * index, easing = FastOutSlowInEasing) },
        label = "tileAlpha$index"
    ) { if (it) 1f else 0f }
    return Modifier.graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationY = offsetY * density
    }
}

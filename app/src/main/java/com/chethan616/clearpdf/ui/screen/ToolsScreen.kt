package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidGlassCard
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

private data class ToolSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val icon: ImageVector,
    val onClick: () -> Unit
)

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
    onNavigateToHtmlToPdf: () -> Unit = {}
) {
    val isDarkMode = LocalIsDarkMode.current
    val uiSensor = rememberUISensor()
    val secondary = LiquidGlassColors.secondary(isDarkMode)

    val toolOpenPdfTitle = stringResource(R.string.tool_open_pdf)
    val toolOpenPdfSub = stringResource(R.string.tool_open_pdf_sub)
    val toolMergeTitle = stringResource(R.string.tool_merge)
    val toolMergeSub = stringResource(R.string.tool_merge_sub)
    val toolSplitTitle = stringResource(R.string.tool_split)
    val toolSplitSub = stringResource(R.string.tool_split_sub)
    val toolCompressTitle = stringResource(R.string.tool_compress)
    val toolCompressSub = stringResource(R.string.tool_compress_sub)
    val toolOrganizeTitle = stringResource(R.string.tool_organize)
    val toolOrganizeSub = stringResource(R.string.tool_organize_sub)
    val toolImagesTitle = stringResource(R.string.tool_images)
    val toolImagesSub = stringResource(R.string.tool_images_sub)
    val toolExtractTitle = stringResource(R.string.tool_extract)
    val toolExtractSub = stringResource(R.string.tool_extract_sub)
    val toolCreateTitle = stringResource(R.string.tool_create)
    val toolCreateSub = stringResource(R.string.tool_create_sub)
    val toolDecryptTitle = stringResource(R.string.tool_decrypt_pdf)
    val toolDecryptSub = stringResource(R.string.tool_decrypt_pdf_sub)
    val toolEncryptTitle = stringResource(R.string.tool_encrypt_pdf)
    val toolEncryptSub = stringResource(R.string.tool_encrypt_pdf_sub)
    val toolPdfToImagesTitle = stringResource(R.string.tool_pdf_to_images)
    val toolPdfToImagesSub = stringResource(R.string.tool_pdf_to_images_sub)
    val toolWatermarkTitle = stringResource(R.string.tool_watermark)
    val toolWatermarkSub = stringResource(R.string.tool_watermark_sub)
    val toolExtractPagesTitle = stringResource(R.string.tool_extract_pages)
    val toolExtractPagesSub = stringResource(R.string.tool_extract_pages_sub)
    val toolPageNumbersTitle = stringResource(R.string.tool_page_numbers)
    val toolPageNumbersSub = stringResource(R.string.tool_page_numbers_sub)
    val toolFlattenTitle = stringResource(R.string.tool_flatten)
    val toolFlattenSub = stringResource(R.string.tool_flatten_sub)
    val toolImageToolsTitle = stringResource(R.string.tool_image_tools)
    val toolImageToolsSub = stringResource(R.string.tool_image_tools_sub)
    val toolHtmlToPdfTitle = stringResource(R.string.tool_html_to_pdf)
    val toolHtmlToPdfSub = stringResource(R.string.tool_html_to_pdf_sub)

    val toolItems = remember(
        toolOpenPdfTitle, toolOpenPdfSub,
        toolMergeTitle, toolMergeSub,
        toolSplitTitle, toolSplitSub,
        toolCompressTitle, toolCompressSub,
        toolOrganizeTitle, toolOrganizeSub,
        toolImagesTitle, toolImagesSub,
        toolExtractTitle, toolExtractSub,
        toolCreateTitle, toolCreateSub,
        toolDecryptTitle, toolDecryptSub,
        toolEncryptTitle, toolEncryptSub,
        onNavigateToOpenPdf,
        onNavigateToMergePdf,
        onNavigateToSplitPdf,
        onNavigateToCompressPdf,
        onNavigateToOrganizePdf,
        onNavigateToImagesToPdf,
        onNavigateToExtractText,
        onNavigateToCreatePdf,
        onNavigateToDecryptPdf,
        onNavigateToEncryptPdf,
        toolPdfToImagesTitle, toolPdfToImagesSub,
        onNavigateToPdfToImages,
        toolWatermarkTitle, toolWatermarkSub,
        onNavigateToWatermark,
        toolExtractPagesTitle, toolExtractPagesSub,
        onNavigateToExtractPages,
        toolPageNumbersTitle, toolPageNumbersSub,
        onNavigateToPageNumbers,
        toolFlattenTitle, toolFlattenSub,
        onNavigateToFlatten,
        toolImageToolsTitle, toolImageToolsSub,
        onNavigateToImageTools,
        toolHtmlToPdfTitle, toolHtmlToPdfSub,
        onNavigateToHtmlToPdf
    ) {
        listOf(
            ToolSpec("open", toolOpenPdfTitle, toolOpenPdfSub, LiquidGlassColors.Blue, Icons.Rounded.FileOpen, onNavigateToOpenPdf),
            ToolSpec("merge", toolMergeTitle, toolMergeSub, LiquidGlassColors.Red, Icons.AutoMirrored.Rounded.CallMerge, onNavigateToMergePdf),
            ToolSpec("split", toolSplitTitle, toolSplitSub, LiquidGlassColors.Purple, Icons.AutoMirrored.Rounded.CallSplit, onNavigateToSplitPdf),
            ToolSpec("compress", toolCompressTitle, toolCompressSub, LiquidGlassColors.Green, Icons.Rounded.Compress, onNavigateToCompressPdf),
            ToolSpec("organize", toolOrganizeTitle, toolOrganizeSub, LiquidGlassColors.Teal, Icons.Rounded.Reorder, onNavigateToOrganizePdf),
            ToolSpec("images", toolImagesTitle, toolImagesSub, LiquidGlassColors.Indigo, Icons.Rounded.Image, onNavigateToImagesToPdf),
            ToolSpec("pdf_to_images", toolPdfToImagesTitle, toolPdfToImagesSub, Color(0xFF00ACC1), Icons.Rounded.Collections, onNavigateToPdfToImages),
            ToolSpec("extract", toolExtractTitle, toolExtractSub, Color(0xFF5AC8FA), Icons.Rounded.TextSnippet, onNavigateToExtractText),
            ToolSpec("create", toolCreateTitle, toolCreateSub, LiquidGlassColors.Orange, Icons.AutoMirrored.Rounded.NoteAdd, onNavigateToCreatePdf),
            ToolSpec("decrypt", toolDecryptTitle, toolDecryptSub, LiquidGlassColors.Purple, Icons.Rounded.LockOpen, onNavigateToDecryptPdf),
            ToolSpec("encrypt", toolEncryptTitle, toolEncryptSub, LiquidGlassColors.Indigo, Icons.Rounded.Lock, onNavigateToEncryptPdf),
            ToolSpec("watermark", toolWatermarkTitle, toolWatermarkSub, Color(0xFFAD1457), Icons.Rounded.BrandingWatermark, onNavigateToWatermark),
            ToolSpec("extract_pages", toolExtractPagesTitle, toolExtractPagesSub, Color(0xFF00897B), Icons.Rounded.ContentCut, onNavigateToExtractPages),
            ToolSpec("page_numbers", toolPageNumbersTitle, toolPageNumbersSub, Color(0xFF3949AB), Icons.Rounded.Numbers, onNavigateToPageNumbers),
            ToolSpec("flatten", toolFlattenTitle, toolFlattenSub, Color(0xFF6D4C41), Icons.Rounded.Layers, onNavigateToFlatten),
            ToolSpec("image_tools", toolImageToolsTitle, toolImageToolsSub, Color(0xFFF4511E), Icons.Rounded.PhotoSizeSelectLarge, onNavigateToImageTools),
            ToolSpec("html_to_pdf", toolHtmlToPdfTitle, toolHtmlToPdfSub, Color(0xFFE65100), Icons.Rounded.Code, onNavigateToHtmlToPdf)
        )
    }

    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "toolsTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 18f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "toolsTopBarOffsetY"
    )

    val gridAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 650, delayMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "toolsGridAlpha"
    )
    val gridOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 26f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 650, delayMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "toolsGridOffsetY"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier.graphicsLayer {
                    alpha = topBarAlpha
                    translationY = topBarOffsetY * density
                }
            ) {
                LiquidGlassTopBar(title = stringResource(R.string.tools_title), backdrop = backdrop, uiSensor = uiSensor)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier.graphicsLayer {
                    alpha = topBarAlpha
                    translationY = topBarOffsetY * density
                }
            ) {
                BasicText(
                    stringResource(R.string.tools_subtitle),
                    style = TextStyle(
                        color = secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        items(toolItems, key = { it.id }) { tool ->
            Box(
                Modifier.graphicsLayer {
                    alpha = gridAlpha
                    translationY = gridOffsetY * density
                }
            ) {
                LiquidGlassCard(
                    title = tool.title,
                    subtitle = tool.subtitle,
                    accentColor = tool.accent,
                    backdrop = backdrop,
                    uiSensor = uiSensor,
                    onClick = tool.onClick,
                    icon = {
                        Icon(tool.icon, contentDescription = tool.title, modifier = Modifier.size(26.dp), tint = tool.accent)
                    }
                )
            }
        }
    }
}

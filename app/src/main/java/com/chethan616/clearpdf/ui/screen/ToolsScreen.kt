package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
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
    onNavigateToImagesToPdf: () -> Unit = {}
) {
    val isDarkMode = LocalIsDarkMode.current
    val uiSensor = rememberUISensor()
    val secondary = LiquidGlassColors.secondary(isDarkMode)
    val toolItems = remember(
        onNavigateToOpenPdf,
        onNavigateToMergePdf,
        onNavigateToSplitPdf,
        onNavigateToCompressPdf,
        onNavigateToOrganizePdf,
        onNavigateToImagesToPdf,
        onNavigateToExtractText,
        onNavigateToCreatePdf
    ) {
        listOf(
            ToolSpec("open", "Open PDF", "View and read", LiquidGlassColors.Blue, Icons.Rounded.FileOpen, onNavigateToOpenPdf),
            ToolSpec("merge", "Merge PDFs", "Combine files", LiquidGlassColors.Red, Icons.AutoMirrored.Rounded.CallMerge, onNavigateToMergePdf),
            ToolSpec("split", "Split PDF", "Extract pages", LiquidGlassColors.Purple, Icons.AutoMirrored.Rounded.CallSplit, onNavigateToSplitPdf),
            ToolSpec("compress", "Compress", "Reduce file size", LiquidGlassColors.Green, Icons.Rounded.Compress, onNavigateToCompressPdf),
            ToolSpec("organize", "Organize", "Reorder and rotate", LiquidGlassColors.Teal, Icons.Rounded.Reorder, onNavigateToOrganizePdf),
            ToolSpec("images", "Images to PDF", "Photos to PDF", LiquidGlassColors.Indigo, Icons.Rounded.Image, onNavigateToImagesToPdf),
            ToolSpec("extract", "Extract text", "Copy text out", Color(0xFF5AC8FA), Icons.Rounded.TextSnippet, onNavigateToExtractText),
            ToolSpec("create", "Create PDF", "Blank, images, text", LiquidGlassColors.Orange, Icons.AutoMirrored.Rounded.NoteAdd, onNavigateToCreatePdf)
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
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
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
                LiquidGlassTopBar(title = "Tools", backdrop = backdrop, uiSensor = uiSensor)
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
                    "Everything you need to work with PDFs.",
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

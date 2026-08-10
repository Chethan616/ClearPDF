package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ImagesToPdfViewModel
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.kyant.backdrop.backdrops.LayerBackdrop

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ImagesToPdfScreen(
    backdrop: LayerBackdrop,
    viewModel: ImagesToPdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (android.net.Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uiSensor = rememberUISensor()
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = Color(0xFF5E35B1)

    var showSaveDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.onPickImages(uris)
    }

    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "imagesTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "imagesTopBarOffsetY"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "imagesContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "imagesContentOffsetY"
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.graphicsLayer {
                alpha = topBarAlpha
                translationY = topBarOffsetY * density
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(stringResource(R.string.images_to_pdf_title), backdrop, uiSensor, Modifier.weight(1f), titleFontSize = 18.sp)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidButton(onClick = { picker.launch("image/*") }, backdrop = backdrop, tint = accent) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.images_add), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                }
                if (state.imageUris.isNotEmpty()) {
                    LiquidButton(onClick = { viewModel.clearImages() }, backdrop = backdrop) {
                        BasicText(stringResource(R.string.viewer_clear), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                }
            }

            // Page-size toggle
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton(
                    onClick = { viewModel.setFitToA4(true) }, backdrop = backdrop,
                    surfaceColor = if (state.fitToA4) accent.copy(0.30f) else Color.White.copy(0.06f)
                ) { BasicText(stringResource(R.string.images_fit_a4), style = TextStyle(text, 13.sp, FontWeight.Medium)) }
                LiquidButton(
                    onClick = { viewModel.setFitToA4(false) }, backdrop = backdrop,
                    surfaceColor = if (!state.fitToA4) accent.copy(0.30f) else Color.White.copy(0.06f)
                ) { BasicText(stringResource(R.string.images_original_size), style = TextStyle(text, 13.sp, FontWeight.Medium)) }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (state.imageUris.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        BasicText(stringResource(R.string.images_hint), style = TextStyle(sub, 13.sp))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.imageUris) { index, uri ->
                            Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.06f))) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.image_number, index + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                        .clip(CircleShape).background(Color.Black.copy(0.55f))
                                        .clickable { viewModel.removeImage(index) },
                                    Alignment.Center
                                ) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.delete), Modifier.size(14.dp), Color.White)
                                }
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFEF5350), 12.sp)) }
            state.resultMessage?.let { msg ->
                Row(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(msg, style = TextStyle(Color(0xFFB9F6CA), 12.sp), modifier = Modifier.weight(1f))
                    state.lastOutputUri?.let { uri ->
                        LiquidButton(onClick = { onViewOutput(uri) }, backdrop = backdrop, tint = accent) {
                            BasicText(stringResource(R.string.open), style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                        }
                    }
                }
            }

            LiquidButton(
                onClick = { if (!state.isSaving && state.imageUris.isNotEmpty()) showSaveDialog = true },
                backdrop = backdrop, tint = accent, modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    if (state.isSaving) "Creating…" else "Create PDF (${state.imageUris.size})",
                    style = TextStyle(Color.White, 15.sp, FontWeight.Medium),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSaveDialog) {
        LiquidSaveDialog(
            initialFileName = "ClearPDF_Images",
            backdrop = backdrop,
            uiSensor = uiSensor,
            onDismiss = { showSaveDialog = false },
            onSave = { fileName, overrideUri ->
                showSaveDialog = false
                viewModel.save(context, fileName, overrideUri)
            }
        )
    }
}

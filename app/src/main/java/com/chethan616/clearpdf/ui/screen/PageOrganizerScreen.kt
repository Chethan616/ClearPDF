package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PageOrganizerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun PageOrganizerScreen(
    backdrop: LayerBackdrop,
    viewModel: PageOrganizerViewModel,
    onBack: () -> Unit,
    onViewOutput: (android.net.Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uiSensor = rememberUISensor()
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = Color(0xFF1976D2)

    var showSaveDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSelectFile(context, it) }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar("Organize Pages", backdrop, uiSensor, Modifier.weight(1f), titleFontSize = 18.sp)
        }

        if (state.sourceUri == null) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BasicText("Reorder, rotate & delete pages", style = TextStyle(text, 18.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        "Pick a PDF, rearrange its pages, then save a new copy. Original text & quality are preserved.",
                        style = TextStyle(sub, 13.sp)
                    )
                    LiquidButton(onClick = { picker.launch(arrayOf("application/pdf")) }, backdrop = backdrop, tint = accent) {
                        Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                        BasicText("Pick a PDF", style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
                    }
                }
                state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFD32F2F), 13.sp)) }
            }
            return@Column
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = accent, strokeWidth = 2.dp) }
            return@Column
        }

        BasicText(
            "${state.sourceFileName} · ${state.pages.size} pages",
            style = TextStyle(sub, 12.sp), modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(state.pages, key = { _, p -> p.originalIndex }) { index, page ->
                Row(
                    Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(54.dp, 72.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(0.06f)),
                        Alignment.Center
                    ) {
                        page.thumbnail?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().rotate(page.rotation.toFloat())
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText("Page ${index + 1}", style = TextStyle(text, 14.sp, FontWeight.Medium))
                        BasicText("Source #${page.originalIndex + 1}" + if (page.rotation != 0) " · ${page.rotation}°" else "",
                            style = TextStyle(sub, 11.sp))
                    }
                    LiquidIconButton(onClick = { viewModel.movePage(index, index - 1) }, backdrop = backdrop, surfaceColor = Color.White.copy(0.10f)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, "Move up", Modifier.size(18.dp), text)
                    }
                    LiquidIconButton(onClick = { viewModel.movePage(index, index + 1) }, backdrop = backdrop, surfaceColor = Color.White.copy(0.10f)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Move down", Modifier.size(18.dp), text)
                    }
                    LiquidIconButton(onClick = { viewModel.rotatePage(index) }, backdrop = backdrop, surfaceColor = Color.White.copy(0.10f)) {
                        Icon(Icons.Rounded.RotateRight, "Rotate", Modifier.size(18.dp), text)
                    }
                    LiquidIconButton(onClick = { viewModel.deletePage(index) }, backdrop = backdrop, surfaceColor = Color(0xFFD32F2F).copy(0.18f)) {
                        Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp), Color(0xFFEF5350))
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
                        BasicText("Open", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                    }
                }
            }
        }

        LiquidButton(
            onClick = { if (!state.isSaving) showSaveDialog = true },
            backdrop = backdrop, tint = accent, modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                if (state.isSaving) "Saving…" else "Save as new PDF",
                style = TextStyle(Color.White, 15.sp, FontWeight.Medium),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSaveDialog) {
        LiquidSaveDialog(
            initialFileName = state.sourceFileName.substringBeforeLast('.').ifBlank { "Document" } + "_Organized",
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

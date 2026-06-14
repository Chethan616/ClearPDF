package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PageOrganizerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val accent = Color(0xFF00897B)
    val density = LocalDensity.current

    var showSaveDialog by remember { mutableStateOf(false) }

    // Drag-reorder state
    val listState = rememberLazyListState()
    var draggingIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    // Continuous edge auto-scroll while dragging near top/bottom.
    LaunchedEffect(autoScroll) {
        if (autoScroll != 0f) {
            while (true) { listState.scrollBy(autoScroll); delay(8) }
        }
    }

    fun insertionIndexAtY(y: Float): Int {
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return 0
        for (item in items) {
            if (y < item.offset + item.size / 2f) return item.index
        }
        return items.last().index + 1
    }

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
                        "Pick a PDF, drag pages to rearrange, then save a new copy. Original text & quality are preserved.",
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

        val selCount = state.selectedIds.size

        // Header / selection action bar
        Row(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText(
                if (selCount > 0) "$selCount selected" else "${state.pages.size} pages",
                style = TextStyle(text, 13.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            if (selCount > 0) {
                LiquidButton(onClick = { viewModel.rotateSelected() }, backdrop = backdrop) {
                    Icon(Icons.Rounded.RotateRight, null, Modifier.size(16.dp), text)
                }
                LiquidButton(onClick = { viewModel.deleteSelected() }, backdrop = backdrop, tint = Color(0xFFEF5350)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), Color.White)
                }
                LiquidButton(onClick = { viewModel.clearSelection() }, backdrop = backdrop) {
                    BasicText("Clear", style = TextStyle(text, 12.sp, FontWeight.Medium))
                }
            } else {
                LiquidButton(onClick = { viewModel.selectAll() }, backdrop = backdrop) {
                    BasicText("Select all", style = TextStyle(text, 12.sp, FontWeight.Medium))
                }
            }
        }

        BasicText(
            "Long-press a page to drag · tap to select",
            style = TextStyle(sub, 11.sp), modifier = Modifier.fillMaxWidth()
        )

        // ── Reorderable list ─────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val st = viewModel.uiState.value
                                val pressed = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                val idx = pressed?.index ?: return@detectDragGesturesAfterLongPress
                                val pressedId = st.pages.getOrNull(idx)?.originalIndex
                                    ?: return@detectDragGesturesAfterLongPress
                                draggingIds = if (pressedId in st.selectedIds && st.selectedIds.size > 1)
                                    st.pages.filter { it.originalIndex in st.selectedIds }.map { it.originalIndex }
                                else listOf(pressedId)
                                pointerY = offset.y
                                targetIndex = idx
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                pointerY = change.position.y
                                targetIndex = insertionIndexAtY(pointerY)
                                val start = listState.layoutInfo.viewportStartOffset
                                val end = listState.layoutInfo.viewportEndOffset
                                val edge = with(density) { 72.dp.toPx() }
                                autoScroll = when {
                                    pointerY < start + edge -> -14f
                                    pointerY > end - edge -> 14f
                                    else -> 0f
                                }
                            },
                            onDragEnd = {
                                if (draggingIds.isNotEmpty()) viewModel.movePagesTo(draggingIds, targetIndex)
                                draggingIds = emptyList(); autoScroll = 0f
                            },
                            onDragCancel = { draggingIds = emptyList(); autoScroll = 0f }
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.pages, key = { _, p -> p.originalIndex }) { index, page ->
                    val isSelected = page.originalIndex in state.selectedIds
                    val isDragging = page.originalIndex in draggingIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (isDragging) 0.35f else 1f)
                            .liquidGlassPanel(backdrop, uiSensor)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) accent else Color.Transparent,
                                shape = RoundedCornerShape(28.dp)
                            )
                            .clickable { viewModel.toggleSelect(page.originalIndex) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            "Select",
                            Modifier.size(22.dp),
                            if (isSelected) accent else sub
                        )
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
                            BasicText(
                                "Source #${page.originalIndex + 1}" + if (page.rotation != 0) " · ${page.rotation}°" else "",
                                style = TextStyle(sub, 11.sp)
                            )
                        }
                        LiquidIconButton(onClick = { viewModel.rotatePage(index) }, backdrop = backdrop, surfaceColor = Color.White.copy(0.10f)) {
                            Icon(Icons.Rounded.RotateRight, "Rotate", Modifier.size(18.dp), text)
                        }
                        LiquidIconButton(onClick = { viewModel.deletePage(index) }, backdrop = backdrop, surfaceColor = Color(0xFFD32F2F).copy(0.18f)) {
                            Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp), Color(0xFFEF5350))
                        }
                        Icon(Icons.Rounded.DragIndicator, "Drag", Modifier.size(22.dp), sub)
                    }
                }
            }

            // Insertion indicator line
            if (draggingIds.isNotEmpty()) {
                val info = listState.layoutInfo
                val lineY = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.offset?.toFloat()
                    ?: info.visibleItemsInfo.lastOrNull()?.let { (it.offset + it.size).toFloat() }
                    ?: 0f
                Box(
                    Modifier
                        .offset { IntOffset(0, (lineY - 2f).roundToInt()) }
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                        .zIndex(1f)
                )

                // Floating drag preview following the finger
                val firstId = draggingIds.first()
                val previewPage = state.pages.firstOrNull { it.originalIndex == firstId }
                Box(
                    Modifier
                        .offset { IntOffset(with(density) { 12.dp.toPx() }.roundToInt(), (pointerY - with(density) { 36.dp.toPx() }).roundToInt()) }
                        .zIndex(2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(0.92f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewPage?.thumbnail?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(36.dp, 48.dp).clip(RoundedCornerShape(4.dp)).rotate(previewPage.rotation.toFloat())
                            )
                        }
                        BasicText(
                            if (draggingIds.size > 1) "${draggingIds.size} pages" else "Move",
                            style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold)
                        )
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

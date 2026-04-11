package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun PdfViewerScreen(
    backdrop: LayerBackdrop,
    viewModel: PdfViewerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF1976D2)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var activeTool by rememberSaveable { mutableStateOf(PdfEditTool.None) }

    val annotationsByPage = remember { mutableStateMapOf<Int, MutableList<PdfMarkup>>() }

    fun getPageMarks(page: Int): MutableList<PdfMarkup> {
        return annotationsByPage.getOrPut(page) { mutableStateListOf() }
    }

    val immersiveActive = state.document != null && !controlsVisible

    DisposableEffect(activity, view, immersiveActive) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersiveActive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(state.document?.uri) {
        controlsVisible = true
        zoomScale = 1f
        panOffset = Offset.Zero
        activeTool = PdfEditTool.None
        annotationsByPage.clear()
    }

    LaunchedEffect(state.document, controlsVisible, state.currentPage) {
        if (state.document != null && controlsVisible) {
            delay(2300)
            controlsVisible = false
        }
    }

    BackHandler(enabled = state.document != null) {
        if (!controlsVisible) {
            controlsVisible = true
        } else {
            onBack()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openPdf(context, it) }
    }

    if (state.document == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                    Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
                }
                LiquidGlassTopBar(title = "PDF Viewer", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(56.dp), accent)
                    BasicText("Open a PDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        "Select a PDF file from your device to view it",
                        style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                    )

                    LiquidButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        backdrop = backdrop,
                        tint = accent
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                            BasicText("Pick a PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }

                if (state.errorMessage != null) {
                    BasicText(state.errorMessage!!, style = TextStyle(Color(0xFFD32F2F), 14.sp))
                }
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLight) Color(0xFF0E1218).copy(alpha = 0.92f) else Color(0xFF050608).copy(alpha = 0.92f))
            .pointerInput(state.document, controlsVisible) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                }
            }
    ) {
        val renderWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(720)
        val pagerScrollEnabled = activeTool == PdfEditTool.None && zoomScale <= 1.02f

        key(state.document?.uri, state.pageCount) {
            val pagerState = rememberPagerState(initialPage = state.currentPage) { state.pageCount }

            LaunchedEffect(pagerState.currentPage, renderWidthPx) {
                zoomScale = 1f
                panOffset = Offset.Zero
                viewModel.onPageChanged(pagerState.currentPage)
                viewModel.renderPage(context, pagerState.currentPage, renderWidthPx)
                if (pagerState.currentPage - 1 >= 0) {
                    viewModel.renderPage(context, pagerState.currentPage - 1, renderWidthPx)
                }
                if (pagerState.currentPage + 1 < state.pageCount) {
                    viewModel.renderPage(context, pagerState.currentPage + 1, renderWidthPx)
                }
            }

            if (state.pageCount > 0) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerScrollEnabled,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val bitmap = state.pageBitmaps.getOrNull(page)
                    val marks = getPageMarks(page)
                    var draftPoints by remember(page, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
                    var draftRectStart by remember(page, activeTool) { mutableStateOf<Offset?>(null) }
                    var draftRectEnd by remember(page, activeTool) { mutableStateOf<Offset?>(null) }

                    if (bitmap == null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0E1218)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = panOffset.x,
                                        translationY = panOffset.y
                                    )
                                    .pointerInput(page, activeTool, zoomScale) {
                                        detectTransformGestures { _, panChange, zoomChange, _ ->
                                            val nextScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                                            zoomScale = nextScale
                                            panOffset = if (nextScale <= 1.01f) {
                                                Offset.Zero
                                            } else {
                                                panOffset + panChange
                                            }
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${page + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    marks.forEach { markup ->
                                        when (markup) {
                                            is PdfMarkup.StrokeMarkup -> {
                                                val path = Path().apply {
                                                    if (markup.points.isNotEmpty()) {
                                                        moveTo(markup.points.first().x, markup.points.first().y)
                                                        markup.points.drop(1).forEach { lineTo(it.x, it.y) }
                                                    }
                                                }
                                                drawPath(
                                                    path = path,
                                                    color = markup.color.copy(alpha = markup.alpha),
                                                    style = Stroke(width = markup.width)
                                                )
                                            }

                                            is PdfMarkup.RectMarkup -> {
                                                val left = min(markup.start.x, markup.end.x)
                                                val top = min(markup.start.y, markup.end.y)
                                                val right = max(markup.start.x, markup.end.x)
                                                val bottom = max(markup.start.y, markup.end.y)
                                                val rect = Rect(left, top, right, bottom)
                                                if (markup.filled) {
                                                    drawRect(markup.color.copy(alpha = markup.alpha), topLeft = rect.topLeft, size = rect.size)
                                                } else {
                                                    drawRect(
                                                        color = markup.color.copy(alpha = markup.alpha),
                                                        topLeft = rect.topLeft,
                                                        size = rect.size,
                                                        style = Stroke(width = 3f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (draftPoints.isNotEmpty()) {
                                        val draftPath = Path().apply {
                                            moveTo(draftPoints.first().x, draftPoints.first().y)
                                            draftPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                        }
                                        drawPath(draftPath, Color(0xFF00BCD4), style = Stroke(width = 6f))
                                    }

                                    if (draftRectStart != null && draftRectEnd != null) {
                                        val start = draftRectStart!!
                                        val end = draftRectEnd!!
                                        val left = min(start.x, end.x)
                                        val top = min(start.y, end.y)
                                        val right = max(start.x, end.x)
                                        val bottom = max(start.y, end.y)
                                        val previewRect = Rect(left, top, right, bottom)
                                        if (activeTool == PdfEditTool.Highlight) {
                                            drawRect(
                                                color = Color(0xFFFFEB3B).copy(alpha = 0.28f),
                                                topLeft = previewRect.topLeft,
                                                size = previewRect.size
                                            )
                                        } else if (activeTool == PdfEditTool.Shape) {
                                            drawRect(
                                                color = Color(0xFF42A5F5),
                                                topLeft = previewRect.topLeft,
                                                size = previewRect.size,
                                                style = Stroke(width = 3f)
                                            )
                                        }
                                    }
                                }

                                if (activeTool != PdfEditTool.None) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(page, activeTool) {
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        when (activeTool) {
                                                            PdfEditTool.Draw -> draftPoints = listOf(start)
                                                            PdfEditTool.Highlight,
                                                            PdfEditTool.Shape -> {
                                                                draftRectStart = start
                                                                draftRectEnd = start
                                                            }

                                                            PdfEditTool.None -> Unit
                                                        }
                                                    },
                                                    onDrag = { change, _ ->
                                                        val pos = change.position
                                                        change.consume()
                                                        when (activeTool) {
                                                            PdfEditTool.Draw -> {
                                                                draftPoints = draftPoints + pos
                                                            }

                                                            PdfEditTool.Highlight,
                                                            PdfEditTool.Shape -> {
                                                                draftRectEnd = pos
                                                            }

                                                            PdfEditTool.None -> Unit
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draftPoints = emptyList()
                                                        draftRectStart = null
                                                        draftRectEnd = null
                                                    },
                                                    onDragEnd = {
                                                        when (activeTool) {
                                                            PdfEditTool.Draw -> {
                                                                if (draftPoints.size > 1) {
                                                                    marks.add(
                                                                        PdfMarkup.StrokeMarkup(
                                                                            points = draftPoints,
                                                                            color = Color(0xFF00BCD4),
                                                                            width = 6f,
                                                                            alpha = 0.95f
                                                                        )
                                                                    )
                                                                }
                                                            }

                                                            PdfEditTool.Highlight -> {
                                                                if (draftRectStart != null && draftRectEnd != null) {
                                                                    marks.add(
                                                                        PdfMarkup.RectMarkup(
                                                                            start = draftRectStart!!,
                                                                            end = draftRectEnd!!,
                                                                            color = Color(0xFFFFEB3B),
                                                                            alpha = 0.30f,
                                                                            filled = true
                                                                        )
                                                                    )
                                                                }
                                                            }

                                                            PdfEditTool.Shape -> {
                                                                if (draftRectStart != null && draftRectEnd != null) {
                                                                    marks.add(
                                                                        PdfMarkup.RectMarkup(
                                                                            start = draftRectStart!!,
                                                                            end = draftRectEnd!!,
                                                                            color = Color(0xFF42A5F5),
                                                                            alpha = 1f,
                                                                            filled = false
                                                                        )
                                                                    )
                                                                }
                                                            }

                                                            PdfEditTool.None -> Unit
                                                        }
                                                        draftPoints = emptyList()
                                                        draftRectStart = null
                                                        draftRectEnd = null
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LiquidButton(
                            onClick = onBack,
                            backdrop = backdrop,
                            surfaceColor = Color.Black.copy(0.35f)
                        ) {
                            Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), Color.White)
                        }
                        LiquidGlassTopBar(
                            title = "Page ${state.currentPage + 1} / ${state.pageCount}",
                            backdrop = backdrop,
                            uiSensor = uiSensor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassPanel(backdrop, uiSensor)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicText(state.fileName, style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("${state.pageCount} pages", style = TextStyle(Color.White.copy(alpha = 0.72f), 12.sp))
                                BasicText("Zoom ${(zoomScale * 100).toInt()}%", style = TextStyle(Color.White.copy(alpha = 0.72f), 12.sp))
                                if (state.sizeBytes > 0) {
                                    val sizeStr = if (state.sizeBytes > 1_048_576) {
                                        "%.1f MB".format(state.sizeBytes / 1_048_576f)
                                    } else {
                                        "${state.sizeBytes / 1024} KB"
                                    }
                                    BasicText(sizeStr, style = TextStyle(Color.White.copy(alpha = 0.72f), 12.sp))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f); if (zoomScale <= 1f) panOffset = Offset.Zero }, backdrop = backdrop) {
                                    BasicText("-", style = TextStyle(Color.White, 16.sp, FontWeight.Bold))
                                }
                                LiquidButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(5f) }, backdrop = backdrop) {
                                    BasicText("+", style = TextStyle(Color.White, 16.sp, FontWeight.Bold))
                                }
                                LiquidButton(onClick = { zoomScale = 1f; panOffset = Offset.Zero }, backdrop = backdrop) {
                                    BasicText("Reset", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LiquidButton(
                                    onClick = { activeTool = if (activeTool == PdfEditTool.None) PdfEditTool.Draw else PdfEditTool.None },
                                    backdrop = backdrop,
                                    tint = if (activeTool != PdfEditTool.None) Color(0xFF00BCD4) else Color.Transparent,
                                    surfaceColor = if (activeTool != PdfEditTool.None) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.12f)
                                ) {
                                    BasicText("Edit", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = { activeTool = PdfEditTool.Draw },
                                    backdrop = backdrop,
                                    tint = if (activeTool == PdfEditTool.Draw) Color(0xFF00BCD4) else Color.Transparent,
                                    surfaceColor = if (activeTool == PdfEditTool.Draw) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.12f)
                                ) {
                                    BasicText("Draw", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = { activeTool = PdfEditTool.Highlight },
                                    backdrop = backdrop,
                                    tint = if (activeTool == PdfEditTool.Highlight) Color(0xFFFFB300) else Color.Transparent,
                                    surfaceColor = if (activeTool == PdfEditTool.Highlight) Color(0xFFFFB300) else Color.White.copy(alpha = 0.12f)
                                ) {
                                    BasicText("Highlight", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = { activeTool = PdfEditTool.Shape },
                                    backdrop = backdrop,
                                    tint = if (activeTool == PdfEditTool.Shape) Color(0xFF42A5F5) else Color.Transparent,
                                    surfaceColor = if (activeTool == PdfEditTool.Shape) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.12f)
                                ) {
                                    BasicText("Shape", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = {
                                        val marks = getPageMarks(state.currentPage)
                                        if (marks.isNotEmpty()) {
                                            marks.removeAt(marks.lastIndex)
                                        }
                                    },
                                    backdrop = backdrop
                                ) {
                                    BasicText("Undo", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                                LiquidButton(
                                    onClick = {
                                        getPageMarks(state.currentPage).clear()
                                    },
                                    backdrop = backdrop
                                ) {
                                    BasicText("Clear", style = TextStyle(Color.White, 12.sp, FontWeight.Medium))
                                }
                            }
                        }

                        LiquidButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            backdrop = backdrop,
                            tint = accent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.UploadFile, null, Modifier.size(16.dp), Color.White)
                                BasicText("Open Another PDF", style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PdfEditTool {
    None,
    Draw,
    Highlight,
    Shape
}

private sealed class PdfMarkup {
    data class StrokeMarkup(
        val points: List<Offset>,
        val color: Color,
        val width: Float,
        val alpha: Float
    ) : PdfMarkup()

    data class RectMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val alpha: Float,
        val filled: Boolean
    ) : PdfMarkup()
}

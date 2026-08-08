package com.chethan616.clearpdf.ui.screen

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.LiquidSaveDialog
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CreateMode
import com.chethan616.clearpdf.ui.viewmodel.CreatePdfViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun CreatePdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CreatePdfViewModel,
    onBack: () -> Unit,
    onViewOutput: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFFE65100)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val activity = context as? Activity

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onModeSelected(CreateMode.FROM_IMAGES)
            viewModel.onImagesSelected(uris)
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                viewModel.onModeSelected(CreateMode.FROM_IMAGES)
                viewModel.onImagesSelected(uris)
            }
        }
    }

    fun launchScanner() {
        if (activity == null) {
            viewModel.setError("Scanner is unavailable in current context")
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                viewModel.setError("Could not start scanner: ${error.message}")
            }
    }

    LaunchedEffect(state.resultMessage, state.errorMessage) {
        if (!state.resultMessage.isNullOrBlank() || !state.errorMessage.isNullOrBlank()) {
            delay(3500)
            viewModel.clearFeedback()
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "createTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "createTopBarOffsetY"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "createContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "createContentOffsetY"
    )

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "Create PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(52.dp), accent)
            BasicText("Create with scans, images, or blank pages", style = TextStyle(text, 19.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "Image-first creation is now default. Text is available as an advanced mode.",
                style = TextStyle(sub, 13.sp, textAlign = TextAlign.Center)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val templates = listOf(
                Triple(CreateMode.FROM_IMAGES, "Import Images", Icons.Rounded.Image),
                Triple(CreateMode.BLANK, "Blank Pages", Icons.Rounded.Description),
                Triple(CreateMode.ADVANCED_TEXT, "Advanced Text", Icons.Rounded.Edit)
            )

            templates.forEach { (mode, label, icon) ->
                val selected = state.selectedMode == mode
                LiquidButton(
                    onClick = { viewModel.onModeSelected(mode) },
                    backdrop = backdrop,
                    tint = if (selected) accent else Color.Transparent,
                    surfaceColor = if (selected) accent else Color.White.copy(0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), if (selected) Color.White else text)
                        BasicText(label, style = TextStyle(if (selected) Color.White else text, 14.sp, FontWeight.Medium))
                    }
                }
            }
        }



        when (state.selectedMode) {
            CreateMode.FROM_IMAGES -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlassPanel(backdrop, uiSensor)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BasicText(
                            "${state.selectedImageUris.size} page${if (state.selectedImageUris.size == 1) "" else "s"} in draft",
                            style = TextStyle(text, 14.sp, FontWeight.Medium)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidButton(
                                onClick = { launchScanner() },
                                backdrop = backdrop,
                                tint = accent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, null, Modifier.size(17.dp), Color.White)
                                    BasicText("Scan", style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                                }
                            }
                            LiquidButton(
                                onClick = { imagePicker.launch("image/*") },
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(17.dp), text)
                                    BasicText("Add Images", style = TextStyle(text, 14.sp, FontWeight.Medium))
                                }
                            }
                        }
                    }

                    if (state.selectedImageUris.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassPanel(backdrop, uiSensor)
                                .padding(16.dp)
                        ) {
                            BasicText("Scan or add images to start your PDF draft.", style = TextStyle(sub, 14.sp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.selectedImageUris) { index, uri ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .liquidGlassPanel(backdrop, uiSensor)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 62.dp, height = 82.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )

                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        BasicText("Page ${index + 1}", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                                        BasicText("Tap arrows to reorder", style = TextStyle(sub, 12.sp))
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            "Move up",
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (index == 0) sub.copy(alpha = 0.2f) else accent.copy(alpha = 0.14f))
                                                .padding(4.dp)
                                                .clickable(enabled = index > 0) { viewModel.moveImage(index, index - 1) },
                                            if (index == 0) sub.copy(alpha = 0.55f) else accent
                                        )
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            "Move down",
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (index == state.selectedImageUris.lastIndex) sub.copy(alpha = 0.2f) else accent.copy(alpha = 0.14f))
                                                .padding(4.dp)
                                                .clickable(enabled = index < state.selectedImageUris.lastIndex) { viewModel.moveImage(index, index + 1) },
                                            if (index == state.selectedImageUris.lastIndex) sub.copy(alpha = 0.55f) else accent
                                        )
                                        Icon(
                                            Icons.Rounded.Close,
                                            "Remove",
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF5252).copy(alpha = 0.14f))
                                                .padding(4.dp)
                                                .clickable { viewModel.removeImage(index) },
                                            Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CreateMode.BLANK -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 220.dp)
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicText("Create a clean blank PDF", style = TextStyle(text, 16.sp, FontWeight.SemiBold))
                    BasicText("Set the number of pages and generate instantly.", style = TextStyle(sub, 13.sp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidButton(onClick = { viewModel.onBlankPageCountChanged(state.blankPageCount - 1) }, backdrop = backdrop) {
                            BasicText("-", style = TextStyle(text, 18.sp, FontWeight.Bold))
                        }
                        BasicText("${state.blankPageCount} pages", style = TextStyle(text, 18.sp, FontWeight.Bold))
                        LiquidButton(onClick = { viewModel.onBlankPageCountChanged(state.blankPageCount + 1) }, backdrop = backdrop, tint = accent) {
                            BasicText("+", style = TextStyle(Color.White, 18.sp, FontWeight.Bold))
                        }
                    }
                }
            }

            CreateMode.ADVANCED_TEXT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp)
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText("Advanced text to PDF", style = TextStyle(text, 16.sp, FontWeight.SemiBold))
                    BasicText("Use this for quick notes. For polished docs, prefer scan/image mode.", style = TextStyle(sub, 12.sp))
                    BasicTextField(
                        value = state.textContent,
                        onValueChange = { viewModel.onTextChanged(it) },
                        textStyle = TextStyle(text, 14.sp, lineHeight = 21.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLight) Color(0x0A000000) else Color(0x1AFFFFFF))
                            .padding(12.dp),
                        decorationBox = { inner ->
                            if (state.textContent.isEmpty()) {
                                BasicText("Type text content...", style = TextStyle(sub.copy(alpha = 0.55f), 14.sp))
                            }
                            inner()
                        }
                    )
                    BasicText("${state.textContent.length} characters", style = TextStyle(sub, 12.sp))
                }
            }
        }

        val createLabel = when (state.selectedMode) {
            CreateMode.FROM_IMAGES -> "Create PDF from Draft"
            CreateMode.BLANK -> "Create Blank PDF"
            CreateMode.ADVANCED_TEXT -> "Create Text PDF"
        }

        LiquidButton(
            onClick = { showSaveDialog = true },
            backdrop = backdrop,
            tint = accent,
            isInteractive = !state.isCreating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(20.dp), Color.White)
                }
                BasicText(
                    if (state.isCreating) "Creating..." else createLabel,
                    style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }

        if (state.errorMessage != null) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                BasicText(state.errorMessage!!, style = TextStyle(Color(0xFFD32F2F), 14.sp))
            }
        }
        if (!state.resultMessage.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                BasicText(state.resultMessage!!, style = TextStyle(Color(0xFF388E3C), 14.sp))
            }
        }

        state.lastOutputUri?.let { outputUri ->
            LiquidButton(
                onClick = { onViewOutput(outputUri) },
                backdrop = backdrop,
                tint = Color(0xFF1976D2),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText("View PDF", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(4.dp))
        }
    }

    if (showSaveDialog) {
        LiquidSaveDialog(
            initialFileName = "ClearPDF_Document.pdf",
            backdrop = backdrop,
            uiSensor = uiSensor,
            onDismiss = { showSaveDialog = false },
            onSave = { fileName, locationUri ->
                showSaveDialog = false
                viewModel.onCreate(context, fileName, locationUri)
            }
        )
    }
}

package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CreateMode
import com.chethan616.clearpdf.ui.viewmodel.CreatePdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun CreatePdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CreatePdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFFE65100)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onImagesSelected(uris)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "Create PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        // Header
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(56.dp), accent)
            BasicText("Create New PDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Convert images or text into a PDF", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
        }

        // Mode selector
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                CreateMode.FROM_IMAGES to "From Images" to Icons.Rounded.Image,
                CreateMode.FROM_TEXT to "From Text" to Icons.Rounded.Edit
            ).forEach { (modeLabel, icon) ->
                val (mode, label) = modeLabel
                val isSelected = state.selectedMode == mode
                LiquidButton(
                    onClick = { viewModel.onModeSelected(mode) },
                    backdrop = backdrop,
                    tint = if (isSelected) accent else Color.Transparent,
                    surfaceColor = if (isSelected) accent else Color.White.copy(0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), if (isSelected) Color.White else text)
                        BasicText(label, style = TextStyle(if (isSelected) Color.White else text, 14.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        // File name
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText("File Name (optional)", style = TextStyle(sub, 13.sp))
            BasicTextField(
                value = state.pdfFileName,
                onValueChange = { viewModel.onFileNameChanged(it) },
                textStyle = TextStyle(text, 15.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLight) Color(0x0A000000) else Color(0x1AFFFFFF))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (state.pdfFileName.isEmpty()) {
                        BasicText("my_document.pdf", style = TextStyle(sub.copy(0.5f), 15.sp))
                    }
                    inner()
                }
            )
        }

        // Content area - scrollable
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.selectedMode) {
                CreateMode.FROM_IMAGES -> {
                    // Image picker
                    Column(
                        Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                "${state.selectedImageUris.size} image${if (state.selectedImageUris.size != 1) "s" else ""} selected",
                                style = TextStyle(text, 15.sp, fontWeight = FontWeight.Medium)
                            )
                            LiquidButton(
                                onClick = { imagePicker.launch("image/*") },
                                backdrop = backdrop, tint = accent
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), Color.White)
                                    BasicText("Add Images", style = TextStyle(Color.White, 13.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }

                    if (state.selectedImageUris.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(state.selectedImageUris) { index, uri ->
                                Box(
                                    Modifier
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = "Image ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Page number
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText("${index + 1}", style = TextStyle(Color.White, 10.sp, FontWeight.Bold))
                                    }
                                    // Remove button
                                    Icon(
                                        Icons.Rounded.Close, "Remove",
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xCC000000))
                                            .padding(3.dp)
                                            .clickable { viewModel.removeImage(index) },
                                        Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                CreateMode.FROM_TEXT -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .liquidGlassPanel(backdrop, uiSensor)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicText("Enter your text", style = TextStyle(sub, 13.sp))
                        BasicTextField(
                            value = state.textContent,
                            onValueChange = { viewModel.onTextChanged(it) },
                            textStyle = TextStyle(text, 14.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLight) Color(0x0A000000) else Color(0x1AFFFFFF))
                                .padding(12.dp),
                            decorationBox = { inner ->
                                if (state.textContent.isEmpty()) {
                                    BasicText(
                                        "Type or paste your text here...\n\nEach paragraph will be formatted as body text in the PDF.",
                                        style = TextStyle(sub.copy(0.5f), 14.sp, lineHeight = 22.sp)
                                    )
                                }
                                inner()
                            }
                        )
                        if (state.textContent.isNotEmpty()) {
                            BasicText(
                                "${state.textContent.length} characters",
                                style = TextStyle(sub, 12.sp)
                            )
                        }
                    }
                }
            }
        }

        // Create button
        LiquidButton(
            onClick = { viewModel.onCreate(context) },
            backdrop = backdrop,
            tint = accent,
            isInteractive = !state.isCreating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 14.dp)
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(20.dp), Color.White)
                }
                BasicText(
                    if (state.isCreating) "Creating..." else "Create PDF",
                    style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Error / Result messages
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

        Spacer(Modifier.height(8.dp))
    }
}

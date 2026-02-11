package com.chethan616.clearpdf.ui.screen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.rememberAsyncImagePainter
import com.chethan616.clearpdf.data.model.ScanFilter
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ScanViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanDocumentScreen(
    backdrop: LayerBackdrop,
    viewModel: ScanViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF4CAF50)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Camera capture URI
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            viewModel.onScanComplete(listOf(pendingCameraUri!!))
        }
        viewModel.cancelScanning()
    }

    // Gallery picker (multiple images)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onScanComplete(uris)
        }
        viewModel.cancelScanning()
    }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            viewModel.setError("Camera permission is required to scan documents")
            viewModel.cancelScanning()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LiquidGlassTopBar(
            title = "Scan Document",
            backdrop = backdrop,
            uiSensor = uiSensor,
            modifier = Modifier.fillMaxWidth()
        )

        // Error display
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.error?.let { error ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFEBEE))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText(
                        error,
                        style = TextStyle(Color(0xFFD32F2F), 13.sp),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Rounded.Close, null,
                        Modifier
                            .size(20.dp)
                            .clickable { viewModel.setError(null) },
                        Color(0xFFD32F2F)
                    )
                }
            }
        }

        if (state.scannedPages.isEmpty()) {
            // ── Empty state ──
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
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Scanner,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        "Scan Document",
                        style = TextStyle(text, 20.sp, FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    BasicText(
                        "Take photos of documents or pick from gallery\nto create a PDF",
                        style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Camera button
                    LiquidButton(
                        onClick = {
                            viewModel.startScanning()
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        backdrop = backdrop,
                        tint = accent,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, Modifier.size(20.dp), Color.White)
                            BasicText("Scan with Camera", style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Gallery button
                    LiquidButton(
                        onClick = {
                            viewModel.startScanning()
                            galleryLauncher.launch("image/*")
                        },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp), text)
                            BasicText("Import from Gallery", style = TextStyle(text, 16.sp, FontWeight.SemiBold))
                        }
                    }
                }

                // Features
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText("Features", style = TextStyle(text, 16.sp, FontWeight.Bold))

                    val features = listOf(
                        "Multi-page document scanning",
                        "Import from gallery",
                        "Export as PDF",
                        "Automatic page ordering"
                    )

                    features.forEach { feature ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), accent)
                            BasicText(feature, style = TextStyle(text, 14.sp))
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        } else {
            // ── Pages view ──
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Page count header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        "${state.scannedPages.size} page${if (state.scannedPages.size != 1) "s" else ""} scanned",
                        style = TextStyle(text, 16.sp, FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Add more pages
                        Icon(
                            Icons.Rounded.CameraAlt, "Add from camera",
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable {
                                    viewModel.startScanning()
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                            accent
                        )
                        Icon(
                            Icons.Rounded.PhotoLibrary, "Add from gallery",
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable {
                                    viewModel.startScanning()
                                    galleryLauncher.launch("image/*")
                                },
                            accent
                        )
                        Icon(
                            Icons.Rounded.Delete, "Clear all",
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                .padding(4.dp)
                                .clickable { viewModel.clearAllPages() },
                            Color(0xFFFF5252)
                        )
                    }
                }

                // Pages grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(state.scannedPages) { index, page ->
                        Box(
                            Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (isLight) Color(0xFFE0E0E0) else Color(0xFF444444),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(page.uri),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                colorFilter = getComposeColorFilter(page.filter),
                                modifier = Modifier.fillMaxSize()
                            )

                            // Page number badge
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    "${index + 1}",
                                    style = TextStyle(Color.White, 11.sp, FontWeight.Bold)
                                )
                            }

                            // Delete button
                            Icon(
                                Icons.Rounded.Close, "Remove page",
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC000000))
                                    .padding(4.dp)
                                    .clickable { viewModel.deletePage(index) },
                                Color.White
                            )
                        }
                    }
                }

                // Filter bar
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlassPanel(backdrop, uiSensor)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicText("Filter", style = TextStyle(text, 14.sp, FontWeight.SemiBold))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            ScanFilter.ORIGINAL to "Original",
                            ScanFilter.AUTO to "Auto",
                            ScanFilter.GRAYSCALE to "Grayscale",
                            ScanFilter.BLACK_WHITE to "B&W",
                            ScanFilter.COLOR to "Vivid"
                        )
                        filters.forEach { (filter, label) ->
                            val currentFilter = state.scannedPages.firstOrNull()?.filter ?: ScanFilter.AUTO
                            val isSelected = currentFilter == filter
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accent else accent.copy(0.12f))
                                    .clickable {
                                        state.scannedPages.indices.forEach { i ->
                                            viewModel.applyFilter(i, filter)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                BasicText(
                                    label,
                                    style = TextStyle(
                                        if (isSelected) Color.White else text,
                                        13.sp, FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Save as PDF button
            LiquidButton(
                onClick = {
                    scope.launch {
                        viewModel.startScanning() // reuse as "saving" indicator
                        val filter = state.scannedPages.firstOrNull()?.filter ?: ScanFilter.ORIGINAL
                        val pdfUri = savePagesAsPdf(context, state.scannedPages.map { it.uri }, filter)
                        viewModel.cancelScanning()
                        if (pdfUri != null) {
                            viewModel.markAsSaved(pdfUri)
                            Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setError("Failed to save PDF")
                        }
                    }
                },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 14.dp)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(22.dp), Color.White)
                    BasicText(
                        if (state.isScanning) "Saving..." else "Save as PDF",
                        style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Create a temporary image URI for camera capture
 */
private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val imageFile = File(
        context.cacheDir,
        "scan_${timeStamp}.jpg"
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}

/**
 * Convert scanned page images to a PDF file with filter applied
 */
private suspend fun savePagesAsPdf(context: Context, uris: List<Uri>, filter: ScanFilter): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()

            uris.forEachIndexed { index, uri ->
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@forEachIndexed
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    val bitmap = applyBitmapFilter(originalBitmap, filter)
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width, bitmap.height, index + 1
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    if (bitmap !== originalBitmap) bitmap.recycle()
                    originalBitmap.recycle()
                }
            }

            // Save to Downloads
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ClearPDF_Scan_$timeStamp.pdf"

            val pdfUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                }
                uri
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.outputStream().use { pdfDocument.writeTo(it) }
                Uri.fromFile(file)
            }

            pdfDocument.close()
            pdfUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Apply a ScanFilter to a Bitmap using ColorMatrix
 */
private fun applyBitmapFilter(src: Bitmap, filter: ScanFilter): Bitmap {
    if (filter == ScanFilter.ORIGINAL) return src

    val cm = when (filter) {
        ScanFilter.GRAYSCALE -> {
            ColorMatrix().apply { setSaturation(0f) }
        }
        ScanFilter.BLACK_WHITE -> {
            // High-contrast B&W: desaturate + boost contrast
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val contrast = ColorMatrix(floatArrayOf(
                2f, 0f, 0f, 0f, -180f,
                0f, 2f, 0f, 0f, -180f,
                0f, 0f, 2f, 0f, -180f,
                0f, 0f, 0f, 1f, 0f
            ))
            contrast.preConcat(gray)
            contrast
        }
        ScanFilter.AUTO -> {
            // Auto enhance: slight brightness + contrast boost
            ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        ScanFilter.COLOR -> {
            // Vivid: boost saturation and slight contrast
            val sat = ColorMatrix().apply { setSaturation(1.5f) }
            val bright = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            bright.preConcat(sat)
            bright
        }
        else -> return src
    }

    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return output
}

/**
 * Get Compose ColorFilter from ScanFilter
 */
private fun getComposeColorFilter(filter: ScanFilter): ColorFilter? {
    return when (filter) {
        ScanFilter.ORIGINAL -> null
        ScanFilter.GRAYSCALE -> ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
        )
        ScanFilter.BLACK_WHITE -> {
            val m = androidx.compose.ui.graphics.ColorMatrix()
            m.setToSaturation(0f)
            val contrast = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                2f, 0f, 0f, 0f, -180f,
                0f, 2f, 0f, 0f, -180f,
                0f, 0f, 2f, 0f, -180f,
                0f, 0f, 0f, 1f, 0f
            ))
            contrast.timesAssign(m)
            ColorFilter.colorMatrix(contrast)
        }
        ScanFilter.AUTO -> ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.2f, 0f, 0f, 10f,
                0f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
        )
        ScanFilter.COLOR -> {
            val m = androidx.compose.ui.graphics.ColorMatrix()
            m.setToSaturation(1.5f)
            val bright = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 5f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            bright.timesAssign(m)
            ColorFilter.colorMatrix(bright)
        }
    }
}

package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.kyant.pdfcore.watermark.PdfWatermarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WatermarkMode { TEXT, IMAGE }

data class WatermarkUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val mode: WatermarkMode = WatermarkMode.TEXT,
    val text: String = "CONFIDENTIAL",
    val imageUri: Uri? = null,
    val imageName: String = "",
    val opacity: Float = 0.25f,
    val diagonal: Boolean = true,
    val isProcessing: Boolean = false,
    val lastOutputUri: Uri? = null,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class WatermarkPdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WatermarkUiState())
    val uiState: StateFlow<WatermarkUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        _uiState.update {
            it.copy(
                sourceUri = uri,
                sourceName = queryName(context, uri),
                lastOutputUri = null,
                resultMessage = null,
                errorMessage = null
            )
        }
    }

    fun onModeChange(mode: WatermarkMode) = _uiState.update { it.copy(mode = mode, lastOutputUri = null, resultMessage = null, errorMessage = null) }
    fun onTextChange(value: String) = _uiState.update { it.copy(text = value) }
    fun onOpacityChange(value: Float) = _uiState.update { it.copy(opacity = value.coerceIn(0.05f, 1f)) }
    fun onDiagonalChange(value: Boolean) = _uiState.update { it.copy(diagonal = value) }
    fun onPickImage(context: Context, uri: Uri) =
        _uiState.update { it.copy(imageUri = uri, imageName = queryName(context, uri), lastOutputUri = null, resultMessage = null, errorMessage = null) }

    fun apply(context: Context) {
        val src = _uiState.value.sourceUri ?: return
        if (_uiState.value.isProcessing) return
        val s0 = _uiState.value
        if (s0.mode == WatermarkMode.TEXT && s0.text.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter watermark text") }
            return
        }
        if (s0.mode == WatermarkMode.IMAGE && s0.imageUri == null) {
            _uiState.update { it.copy(errorMessage = "Pick a watermark image") }
            return
        }
        _uiState.update { it.copy(isProcessing = true, errorMessage = null, resultMessage = null, lastOutputUri = null) }
        viewModelScope.launch {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ClearPDF_Watermarked_$ts.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = createOutputUri(context, fileName)
                val s = _uiState.value
                withContext(Dispatchers.IO) {
                    if (s.mode == WatermarkMode.IMAGE) {
                        val bmp = s.imageUri?.let { u ->
                            context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        } ?: throw IllegalStateException("Couldn't read the watermark image")
                        PdfWatermarker.applyImage(
                            context = context, sourceUri = src, destinationUri = outUri,
                            bitmap = bmp, opacity = s.opacity, diagonal = s.diagonal
                        )
                    } else {
                        PdfWatermarker.apply(
                            context = context, sourceUri = src, destinationUri = outUri,
                            text = s.text.trim(), opacity = s.opacity, diagonal = s.diagonal
                        )
                    }
                }
                RecentFilesManager.addRecent(context, RecentFile(
                    name = fileName, uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(), sizeBytes = 0
                ))
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        lastOutputUri = outUri,
                        resultMessage = "Watermark applied · saved to $saveLabel"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = t.message ?: "Couldn't apply watermark") }
            }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(errorMessage = null, resultMessage = null) }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "document.pdf"

    private fun createOutputUri(context: Context, fileName: String): Uri {
        val customUri = SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            return try {
                val docUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                docUri?.createFile("application/pdf", fileName)?.uri ?: createDownloadUri(context, fileName)
            } catch (_: Exception) { createDownloadUri(context, fileName) }
        }
        return createDownloadUri(context, fileName)
    }

    private fun createDownloadUri(context: Context, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("Unable to create output in Downloads")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            if (!file.exists()) file.createNewFile()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
    }
}

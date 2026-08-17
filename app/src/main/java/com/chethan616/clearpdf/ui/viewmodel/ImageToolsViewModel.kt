package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.pdfcore.image.ImageProcessor
import com.kyant.pdfcore.raster.PdfRasterizer.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageToolsUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val srcWidth: Int = 0,
    val srcHeight: Int = 0,
    val srcSizeBytes: Long = 0,
    val format: ImageFormat = ImageFormat.JPEG,
    val quality: Int = 85,
    val scalePercent: Int = 100,
    val isProcessing: Boolean = false,
    val result: ImageProcessor.Result? = null,
    val savedToGallery: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class ImageToolsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToolsUiState())
    val uiState: StateFlow<ImageToolsUiState> = _uiState.asStateFlow()

    fun onSelectImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val name = queryName(context, uri)
            val info = withContext(Dispatchers.IO) { runCatching { ImageProcessor.inspect(context, uri) }.getOrNull() }
            _uiState.update {
                it.copy(
                    sourceUri = uri, sourceName = name,
                    srcWidth = info?.width ?: 0, srcHeight = info?.height ?: 0, srcSizeBytes = info?.sizeBytes ?: 0,
                    result = null, savedToGallery = false, resultMessage = null,
                    errorMessage = if (info == null || info.width == 0) "Couldn't read this image" else null
                )
            }
        }
    }

    fun onFormatChange(value: ImageFormat) = _uiState.update { it.copy(format = value, result = null, savedToGallery = false) }
    fun onQualityChange(value: Int) = _uiState.update { it.copy(quality = value.coerceIn(30, 100), result = null, savedToGallery = false) }
    fun onScaleChange(value: Int) = _uiState.update { it.copy(scalePercent = value.coerceIn(10, 100), result = null, savedToGallery = false) }

    fun process(context: Context) {
        val src = _uiState.value.sourceUri ?: return
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true, errorMessage = null, resultMessage = null, result = null, savedToGallery = false) }
        viewModelScope.launch {
            try {
                val s = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    ImageProcessor.process(context, src, s.format, s.quality, s.scalePercent)
                }
                val beforeKb = s.srcSizeBytes / 1024
                val afterKb = result.sizeBytes / 1024
                _uiState.update {
                    it.copy(
                        isProcessing = false, result = result,
                        resultMessage = "${result.width}×${result.height} · $afterKb KB" +
                            if (beforeKb > 0) " (was $beforeKb KB)" else ""
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = t.message ?: "Couldn't process image") }
            }
        }
    }

    fun saveToGallery(context: Context) {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ImageProcessor.saveToGallery(context, result.file, _uiState.value.format) }.getOrDefault(false)
            }
            _uiState.update {
                it.copy(
                    savedToGallery = ok,
                    resultMessage = if (ok) "Saved to Pictures/ClearPDF" else it.resultMessage,
                    errorMessage = if (!ok) "Couldn't save to gallery" else null
                )
            }
        }
    }

    fun share(context: Context) {
        val result = _uiState.value.result ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = _uiState.value.format.mime
            putExtra(Intent.EXTRA_STREAM, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clearFeedback() = _uiState.update { it.copy(errorMessage = null, resultMessage = null) }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "image"
}

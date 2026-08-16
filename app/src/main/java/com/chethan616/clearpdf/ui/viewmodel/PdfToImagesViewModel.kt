package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.pdfcore.raster.PdfRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfToImagesUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val format: PdfRasterizer.ImageFormat = PdfRasterizer.ImageFormat.JPEG,
    val quality: Int = 90,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val resultPages: List<PdfRasterizer.RasterPage> = emptyList(),
    val savedCount: Int = 0,
    val errorMessage: String? = null,
    val resultMessage: String? = null
)

class PdfToImagesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PdfToImagesUiState())
    val uiState: StateFlow<PdfToImagesUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val name = queryName(context, uri)
            val count = withContext(Dispatchers.IO) { runCatching { pageCount(context, uri) }.getOrDefault(0) }
            _uiState.update {
                it.copy(
                    sourceUri = uri,
                    sourceName = name,
                    pageCount = count,
                    resultPages = emptyList(),
                    savedCount = 0,
                    resultMessage = null,
                    errorMessage = if (count == 0) "Couldn't read this PDF" else null
                )
            }
        }
    }

    fun onFormatChange(format: PdfRasterizer.ImageFormat) = _uiState.update { it.copy(format = format) }

    fun onQualityChange(quality: Int) = _uiState.update { it.copy(quality = quality.coerceIn(30, 100)) }

    fun run(context: Context) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(isProcessing = true, progress = 0f, errorMessage = null, resultMessage = null, resultPages = emptyList()) }
        viewModelScope.launch {
            try {
                val format = _uiState.value.format
                val quality = _uiState.value.quality
                val pages = withContext(Dispatchers.IO) {
                    PdfRasterizer.rasterize(context, uri, format, dpi = 150, quality = quality) { done, total ->
                        _uiState.update { it.copy(progress = if (total == 0) 0f else done.toFloat() / total) }
                    }
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progress = 1f,
                        resultPages = pages,
                        resultMessage = "Rendered ${pages.size} image${if (pages.size == 1) "" else "s"}"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = t.message ?: "Failed to convert") }
            }
        }
    }

    fun saveToGallery(context: Context) {
        val pages = _uiState.value.resultPages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching { PdfRasterizer.exportToGallery(context, pages, _uiState.value.format) }.getOrDefault(0)
            }
            _uiState.update {
                it.copy(
                    savedCount = count,
                    resultMessage = if (count > 0) "Saved $count image${if (count == 1) "" else "s"} to Pictures/ClearPDF" else it.resultMessage,
                    errorMessage = if (count == 0) "Couldn't save to gallery" else null
                )
            }
        }
    }

    fun shareAll(context: Context) {
        val pages = _uiState.value.resultPages
        if (pages.isEmpty()) return
        val uris = ArrayList(pages.map { it.uri })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = _uiState.value.format.mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share images").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clearFeedback() = _uiState.update { it.copy(errorMessage = null, resultMessage = null) }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "document.pdf"

    private fun pageCount(context: Context, uri: Uri): Int {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        return pfd.use { PdfRenderer(it).use { r -> r.pageCount } }
    }
}

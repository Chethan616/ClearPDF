package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.domain.usecase.CompressPdfUseCase
import com.kyant.pdfcore.model.CompressionQuality
import com.kyant.pdfcore.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CompressPdfUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val originalSizeBytes: Long = 0,
    val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val qualitySlider: Float = 0.5f,
    val estimatedSizeBytes: Long = -1,
    val isCompressing: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val compressedSizeBytes: Long = -1
)

class CompressPdfViewModel(private val compressPdfUseCase: CompressPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CompressPdfUiState())
    val uiState: StateFlow<CompressPdfUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val name = queryFileName(context, uri) ?: "Unknown.pdf"
                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L

                val source = PdfDocument(uri = uri, name = name, sizeBytes = size)
                val estimate = compressPdfUseCase.estimateSize(source, CompressionQuality.MEDIUM)

                _uiState.value = CompressPdfUiState(
                    sourceFileName = name,
                    sourceUri = uri,
                    originalSizeBytes = size,
                    qualitySlider = 0.5f,
                    estimatedSizeBytes = estimate
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun onQualityChanged(quality: CompressionQuality) {
        val slider = when (quality) {
            CompressionQuality.LOW -> 0.15f
            CompressionQuality.MEDIUM -> 0.5f
            CompressionQuality.HIGH -> 0.85f
        }
        val sourceUri = _uiState.value.sourceUri
        val estimate = if (sourceUri != null) {
            compressPdfUseCase.estimateSize(
                source = PdfDocument(
                    uri = sourceUri,
                    name = _uiState.value.sourceFileName,
                    sizeBytes = _uiState.value.originalSizeBytes
                ),
                quality = quality
            )
        } else {
            -1
        }
        _uiState.value = _uiState.value.copy(
            selectedQuality = quality,
            qualitySlider = slider,
            estimatedSizeBytes = estimate
        )
    }

    fun onQualitySliderChanged(value: Float) {
        val quality = when {
            value < 0.33f -> CompressionQuality.LOW
            value < 0.66f -> CompressionQuality.MEDIUM
            else -> CompressionQuality.HIGH
        }
        val sourceUri = _uiState.value.sourceUri
        val estimate = if (sourceUri != null) {
            compressPdfUseCase.estimateSize(
                source = PdfDocument(
                    uri = sourceUri,
                    name = _uiState.value.sourceFileName,
                    sizeBytes = _uiState.value.originalSizeBytes
                ),
                quality = quality
            )
        } else {
            -1
        }
        _uiState.value = _uiState.value.copy(
            qualitySlider = value,
            selectedQuality = quality,
            estimatedSizeBytes = estimate
        )
    }

    fun onCompress(context: Context) {
        val srcUri = _uiState.value.sourceUri ?: return
        _uiState.value = _uiState.value.copy(isCompressing = true, errorMessage = null, resultMessage = null)
        viewModelScope.launch {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ClearPDF_Compressed_$ts.pdf"
                val outUri = createOutputUri(context, fileName)
                val source = PdfDocument(uri = srcUri, name = _uiState.value.sourceFileName, sizeBytes = _uiState.value.originalSizeBytes)
                val result = withContext(Dispatchers.IO) {
                    compressPdfUseCase.compress(context, source, _uiState.value.selectedQuality, outUri)
                }

                RecentFilesManager.addRecent(context, RecentFile(
                    name = fileName, uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(), sizeBytes = result.sizeBytes
                ))

                val origKb = _uiState.value.originalSizeBytes / 1024
                val compKb = result.sizeBytes / 1024
                val reduction = if (_uiState.value.originalSizeBytes > 0)
                    (100 - (result.sizeBytes * 100 / _uiState.value.originalSizeBytes)).toInt()
                else 0

                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    compressedSizeBytes = result.sizeBytes,
                    resultMessage = "Compressed: ${origKb}KB → ${compKb}KB (${reduction}% smaller)\nSaved to Downloads"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    errorMessage = e.message ?: "Compression failed"
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(resultMessage = null, errorMessage = null)
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

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
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            Uri.fromFile(java.io.File(dir, fileName))
        }
    }
}

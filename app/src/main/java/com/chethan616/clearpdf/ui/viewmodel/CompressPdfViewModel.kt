package com.chethan616.clearpdf.ui.viewmodel

import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.domain.usecase.CompressPdfUseCase
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.kyant.pdfcore.model.CompressionQuality
import com.kyant.pdfcore.model.PdfDocument
import com.chethan616.clearpdf.ui.utils.AppDispatchers
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
    val compressedSizeBytes: Long = -1,
    val lastOutputUri: Uri? = null,
    val saveLocationLabel: String = "Downloads (default)"
)

class CompressPdfViewModel(private val compressPdfUseCase: CompressPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CompressPdfUiState())
    val uiState: StateFlow<CompressPdfUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Honor the user's Default Quality setting instead of always starting on MEDIUM.
                // Mapped with the same thresholds the slider uses (see onQualitySliderChanged), and
                // the slider seeds to the raw stored value so its knob lands where the user set it.
                val defaultQuality = AppSettingsManager.getDefaultQuality(context)
                val quality = when {
                    defaultQuality < 0.33f -> CompressionQuality.LOW
                    defaultQuality < 0.66f -> CompressionQuality.MEDIUM
                    else -> CompressionQuality.HIGH
                }
                val (name, size, estimate) = withContext(AppDispatchers.pdf) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val fileName = queryFileName(context, uri) ?: "Unknown.pdf"
                    val fileSize = context.contentResolver.openFileDescriptor(uri, "r")
                        ?.use { it.statSize } ?: -1L
                    val source = PdfDocument(uri = uri, name = fileName, sizeBytes = fileSize)
                    val est = compressPdfUseCase.estimateSize(source, quality)
                    Triple(fileName, fileSize, est)
                }
                _uiState.value = CompressPdfUiState(
                    sourceFileName = name,
                    sourceUri = uri,
                    originalSizeBytes = size,
                    selectedQuality = quality,
                    qualitySlider = defaultQuality,
                    estimatedSizeBytes = estimate
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.open_pdf_failed))
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
        val current = _uiState.value
        val quality = when {
            value < 0.33f -> CompressionQuality.LOW
            value < 0.66f -> CompressionQuality.MEDIUM
            else -> CompressionQuality.HIGH
        }
        val sourceUri = current.sourceUri
        _uiState.value = current.copy(
            qualitySlider = value,
            selectedQuality = quality
        )
        if (quality != current.selectedQuality && sourceUri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val estimate = compressPdfUseCase.estimateSize(
                        source = PdfDocument(
                            uri = sourceUri,
                            name = current.sourceFileName,
                            sizeBytes = current.originalSizeBytes
                        ),
                        quality = quality
                    )
                    _uiState.value = _uiState.value.copy(estimatedSizeBytes = estimate)
                } catch (_: Exception) {}
            }
        }
    }

    fun onCompress(context: Context) {
        val srcUri = _uiState.value.sourceUri ?: return
        _uiState.value = _uiState.value.copy(isCompressing = true, errorMessage = null, resultMessage = null, lastOutputUri = null)
        viewModelScope.launch {
            try {
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
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
                    lastOutputUri = outUri,
                    saveLocationLabel = saveLabel,
                    resultMessage = context.getString(R.string.compression_success, origKb, compKb, reduction, saveLabel)
                )

                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    errorMessage = context.getString(R.string.compression_failed)
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
                val created = docUri?.createFile("application/pdf", fileName)?.uri
                if (created != null) {
                    created
                } else {
                    createDownloadUri(context, fileName)
                }
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

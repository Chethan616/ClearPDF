package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.domain.usecase.CreatePdfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CreateMode { FROM_IMAGES, BLANK, ADVANCED_TEXT }

data class CreatePdfUiState(
    val selectedMode: CreateMode = CreateMode.FROM_IMAGES,
    val textContent: String = "",
    val selectedImageUris: List<Uri> = emptyList(),
    val blankPageCount: Int = 1,
    val isCreating: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val pdfFileName: String = "",
    val lastOutputUri: Uri? = null,
    val saveLocationLabel: String = "Downloads (default)"
)

class CreatePdfViewModel(private val createPdfUseCase: CreatePdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CreatePdfUiState())
    val uiState: StateFlow<CreatePdfUiState> = _uiState.asStateFlow()

    fun onModeSelected(mode: CreateMode) {
        _uiState.update { it.copy(selectedMode = mode, errorMessage = null, resultMessage = null, lastOutputUri = null) }
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(textContent = text) }
    }

    fun onFileNameChanged(name: String) {
        _uiState.update { it.copy(pdfFileName = name) }
    }

    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris + uris, lastOutputUri = null) }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.selectedImageUris.indices || toIndex !in state.selectedImageUris.indices) {
                return@update state
            }
            val uris = state.selectedImageUris.toMutableList()
            val moved = uris.removeAt(fromIndex)
            uris.add(toIndex, moved)
            state.copy(selectedImageUris = uris)
        }
    }

    fun removeImage(index: Int) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris.filterIndexed { i, _ -> i != index }, lastOutputUri = null) }
    }

    fun onBlankPageCountChanged(pageCount: Int) {
        _uiState.update { it.copy(blankPageCount = pageCount.coerceIn(1, 50)) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(resultMessage = null, errorMessage = null) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun onCreate(context: Context) {
        val state = _uiState.value
        if (state.isCreating) return

        // Validate
        when (state.selectedMode) {
            CreateMode.FROM_IMAGES -> {
                if (state.selectedImageUris.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Please select at least one image") }
                    return
                }
            }
            CreateMode.BLANK -> {
                if (state.blankPageCount < 1) {
                    _uiState.update { it.copy(errorMessage = "Blank PDF needs at least 1 page") }
                    return
                }
            }
            CreateMode.ADVANCED_TEXT -> {
                if (state.textContent.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Please enter some text") }
                    return
                }
            }
        }

        _uiState.update { it.copy(isCreating = true, errorMessage = null, resultMessage = null, lastOutputUri = null) }

        viewModelScope.launch {
            try {
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = state.pdfFileName.ifBlank {
                    when (state.selectedMode) {
                        CreateMode.FROM_IMAGES -> "ClearPDF_Images_$timeStamp"
                        CreateMode.BLANK -> "ClearPDF_Blank_$timeStamp"
                        CreateMode.ADVANCED_TEXT -> "ClearPDF_Text_$timeStamp"
                    }
                }.let { if (!it.endsWith(".pdf", true)) "$it.pdf" else it }

                val outputUri = withContext(Dispatchers.IO) {
                    createOutputUri(context, fileName)
                } ?: throw Exception("Failed to create output file")

                val doc = withContext(Dispatchers.IO) {
                    when (state.selectedMode) {
                        CreateMode.FROM_IMAGES -> {
                            val quality = AppSettingsManager.getDefaultQuality(context)
                            val bitmaps = state.selectedImageUris.mapNotNull { uri ->
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)?.let { optimizeBitmapForQuality(it, quality) }
                                }
                            }
                            if (bitmaps.isEmpty()) throw Exception("Could not decode any images")
                            val result = createPdfUseCase.createFromImages(context, bitmaps, outputUri)
                            bitmaps.forEach { it.recycle() }
                            result
                        }
                        CreateMode.BLANK -> {
                            createPdfUseCase.createBlank(context, state.blankPageCount, outputUri)
                        }
                        CreateMode.ADVANCED_TEXT -> {
                            createPdfUseCase.createFromText(context, state.textContent, outputUri)
                        }
                    }
                }

                // Add to recents
                RecentFilesManager.addRecent(context, RecentFile(
                    name = fileName,
                    uriString = outputUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    sizeBytes = -1
                ))

                _uiState.update {
                    it.copy(
                        isCreating = false,
                        lastOutputUri = outputUri,
                        saveLocationLabel = saveLabel,
                        resultMessage = "Created $fileName (${doc.pageCount} pages)\nSaved to $saveLabel"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCreating = false, errorMessage = e.message ?: "Failed to create PDF")
                }
            }
        }
    }

    private fun createOutputUri(context: Context, fileName: String): Uri? {
        val customUri = SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            return try {
                val docUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                docUri?.createFile("application/pdf", fileName)?.uri ?: createDownloadUri(context, fileName)
            } catch (_: Exception) {
                createDownloadUri(context, fileName)
            }
        }
        return createDownloadUri(context, fileName)
    }

    private fun createDownloadUri(context: Context, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, fileName)
            Uri.fromFile(file)
        }
    }

    private fun optimizeBitmapForQuality(source: Bitmap, quality: Float): Bitmap {
        val clamped = quality.coerceIn(0.2f, 1f)
        val scale = (0.5f + (clamped * 0.5f)).coerceIn(0.5f, 1f)
        if (scale >= 0.99f) {
            return source
        }

        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        if (resized != source && !source.isRecycled) {
            source.recycle()
        }
        return resized
    }
}

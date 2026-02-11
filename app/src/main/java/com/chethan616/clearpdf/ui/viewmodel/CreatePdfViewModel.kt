package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
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

enum class CreateMode { FROM_IMAGES, FROM_TEXT }

data class CreatePdfUiState(
    val selectedMode: CreateMode = CreateMode.FROM_IMAGES,
    val textContent: String = "",
    val selectedImageUris: List<Uri> = emptyList(),
    val isCreating: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val pdfFileName: String = ""
)

class CreatePdfViewModel(private val createPdfUseCase: CreatePdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CreatePdfUiState())
    val uiState: StateFlow<CreatePdfUiState> = _uiState.asStateFlow()

    fun onModeSelected(mode: CreateMode) {
        _uiState.update { it.copy(selectedMode = mode, errorMessage = null, resultMessage = null) }
    }

    fun onTextChanged(text: String) {
        _uiState.update { it.copy(textContent = text) }
    }

    fun onFileNameChanged(name: String) {
        _uiState.update { it.copy(pdfFileName = name) }
    }

    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris + uris) }
    }

    fun removeImage(index: Int) {
        _uiState.update { it.copy(selectedImageUris = it.selectedImageUris.filterIndexed { i, _ -> i != index }) }
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
            CreateMode.FROM_TEXT -> {
                if (state.textContent.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Please enter some text") }
                    return
                }
            }
        }

        _uiState.update { it.copy(isCreating = true, errorMessage = null, resultMessage = null) }

        viewModelScope.launch {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = state.pdfFileName.ifBlank {
                    when (state.selectedMode) {
                        CreateMode.FROM_IMAGES -> "ClearPDF_Images_$timeStamp"
                        CreateMode.FROM_TEXT -> "ClearPDF_Text_$timeStamp"
                    }
                }.let { if (!it.endsWith(".pdf", true)) "$it.pdf" else it }

                val outputUri = withContext(Dispatchers.IO) {
                    createOutputUri(context, fileName)
                } ?: throw Exception("Failed to create output file")

                val doc = withContext(Dispatchers.IO) {
                    when (state.selectedMode) {
                        CreateMode.FROM_IMAGES -> {
                            val bitmaps = state.selectedImageUris.mapNotNull { uri ->
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            }
                            if (bitmaps.isEmpty()) throw Exception("Could not decode any images")
                            val result = createPdfUseCase.createFromImages(context, bitmaps, outputUri)
                            bitmaps.forEach { it.recycle() }
                            result
                        }
                        CreateMode.FROM_TEXT -> {
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
                        resultMessage = "PDF created: $fileName (${doc.pageCount} pages)"
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
}

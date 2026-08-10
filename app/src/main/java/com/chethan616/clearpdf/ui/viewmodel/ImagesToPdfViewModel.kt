package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.kyant.pdfcore.converter.PdfConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImagesToPdfUiState(
    val imageUris: List<Uri> = emptyList(),
    val fitToA4: Boolean = true,
    val isSaving: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val lastOutputUri: Uri? = null,
    val saveLocationLabel: String = "Downloads (default)"
)

class ImagesToPdfViewModel(private val converter: PdfConverter) : ViewModel() {
    private val _uiState = MutableStateFlow(ImagesToPdfUiState())
    val uiState: StateFlow<ImagesToPdfUiState> = _uiState.asStateFlow()

    fun onPickImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            imageUris = _uiState.value.imageUris + uris,
            resultMessage = null,
            errorMessage = null
        )
    }

    fun removeImage(index: Int) {
        val list = _uiState.value.imageUris.toMutableList()
        if (index in list.indices) list.removeAt(index)
        _uiState.value = _uiState.value.copy(imageUris = list)
    }

    fun clearImages() {
        _uiState.value = _uiState.value.copy(imageUris = emptyList(), resultMessage = null, errorMessage = null)
    }

    fun setFitToA4(fit: Boolean) {
        _uiState.value = _uiState.value.copy(fitToA4 = fit)
    }

    fun save(context: Context, fileName: String, overrideUri: Uri? = null) {
        val images = _uiState.value.imageUris
        if (images.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = context.getString(R.string.images_min_one))
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, resultMessage = null, lastOutputUri = null)
        viewModelScope.launch {
            try {
                val target = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = withContext(Dispatchers.IO) { createOutputUri(context, target, overrideUri) }
                withContext(Dispatchers.IO) {
                    converter.imagesToPdf(context, images, outUri, fitToA4 = _uiState.value.fitToA4)
                }
                RecentFilesManager.addRecent(context, RecentFile(
                    name = target,
                    uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = images.size
                ))
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    lastOutputUri = outUri,
                    saveLocationLabel = saveLabel,
                    resultMessage = context.getString(R.string.images_success, images.size, saveLabel)
                )
                if (GitHubStarPromptManager.recordPdfInteraction(context)) StarPromptEventBus.requestPrompt()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = context.getString(R.string.conversion_failed))
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(resultMessage = null, errorMessage = null)
    }

    private fun createOutputUri(context: Context, fileName: String, overrideUri: Uri?): Uri {
        val customUri = overrideUri ?: SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                    ?.createFile("application/pdf", fileName)?.uri
            }.getOrNull()?.let { return it }
        }
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

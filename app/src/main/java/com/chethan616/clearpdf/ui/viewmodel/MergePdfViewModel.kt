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
import com.chethan616.clearpdf.domain.usecase.MergePdfUseCase
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

data class MergePdfUiState(
    val selectedFiles: List<String> = emptyList(),
    val selectedUris: List<Uri> = emptyList(),
    val isMerging: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val progress: Float = 0f
)

class MergePdfViewModel(private val mergePdfUseCase: MergePdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(MergePdfUiState())
    val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()

    fun addFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val newNames = uris.map { uri -> queryFileName(context, uri) ?: "Unknown.pdf" }
            _uiState.value = _uiState.value.copy(
                selectedFiles = _uiState.value.selectedFiles + newNames,
                selectedUris = _uiState.value.selectedUris + uris,
                resultMessage = null
            )
        }
    }

    fun onRemoveFile(index: Int) {
        val files = _uiState.value.selectedFiles.toMutableList()
        val uris = _uiState.value.selectedUris.toMutableList()
        if (index in files.indices) {
            files.removeAt(index)
            uris.removeAt(index)
            _uiState.value = _uiState.value.copy(selectedFiles = files, selectedUris = uris)
        }
    }

    fun onMerge(context: Context) {
        val uris = _uiState.value.selectedUris
        if (uris.size < 2) {
            _uiState.value = _uiState.value.copy(errorMessage = "Select at least 2 PDFs")
            return
        }
        _uiState.value = _uiState.value.copy(isMerging = true, errorMessage = null, resultMessage = null)
        viewModelScope.launch {
            try {
                val sources = uris.map { uri ->
                    PdfDocument(uri = uri, name = queryFileName(context, uri) ?: "file.pdf")
                }
                val outputUri = withContext(Dispatchers.IO) {
                    val outUri = createOutputUri(context, "Merged")
                    mergePdfUseCase.merge(context, sources, outUri)
                    outUri
                }
                val outName = queryFileName(context, outputUri) ?: "Merged.pdf"
                RecentFilesManager.addRecent(context, RecentFile(
                    name = outName, uriString = outputUri.toString(),
                    timestamp = System.currentTimeMillis()
                ))
                _uiState.value = _uiState.value.copy(
                    isMerging = false,
                    resultMessage = "Merged ${uris.size} files → $outName\nSaved to Downloads"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMerging = false,
                    errorMessage = e.message ?: "Merge failed"
                )
            }
        }
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

    private fun createOutputUri(context: Context, prefix: String): Uri {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ClearPDF_${prefix}_$ts.pdf"

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

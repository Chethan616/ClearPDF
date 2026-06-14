package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.pdfcore.converter.PdfConverter
import com.kyant.pdfcore.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExtractTextUiState(
    val sourceFileName: String = "",
    val isExtracting: Boolean = false,
    val text: String = "",
    val hasResult: Boolean = false,
    val errorMessage: String? = null
)

class ExtractTextViewModel(private val converter: PdfConverter) : ViewModel() {
    private val _uiState = MutableStateFlow(ExtractTextUiState())
    val uiState: StateFlow<ExtractTextUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        _uiState.value = ExtractTextUiState(isExtracting = true)
        viewModelScope.launch {
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = queryFileName(context, uri) ?: "Unknown.pdf"
                val source = PdfDocument(uri = uri, name = name)
                val extracted = withContext(Dispatchers.IO) { converter.extractText(context, source) }.trim()
                _uiState.value = ExtractTextUiState(
                    sourceFileName = name,
                    isExtracting = false,
                    text = extracted,
                    hasResult = true,
                    errorMessage = if (extracted.isEmpty())
                        "No selectable text found. This PDF may be a scan/image — try the scanner's OCR instead."
                    else null
                )
            } catch (e: Exception) {
                _uiState.value = ExtractTextUiState(isExtracting = false, errorMessage = e.message ?: "Extraction failed")
            }
        }
    }

    fun reset() {
        _uiState.value = ExtractTextUiState()
    }

    private fun queryFileName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    } catch (_: Exception) { null }
}

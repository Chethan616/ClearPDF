package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.domain.usecase.OpenPdfUseCase
import com.kyant.pdfcore.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfViewerUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pageBitmaps: List<Bitmap?> = emptyList(),
    val document: PdfDocument? = null,
    val sizeBytes: Long = -1
)

class PdfViewerViewModel(private val openPdfUseCase: OpenPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    fun openPdf(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { openPdfUseCase.open(context, uri) }
                _uiState.value = _uiState.value.copy(
                    fileName = doc.name,
                    pageCount = doc.pageCount,
                    currentPage = 0,
                    isLoading = false,
                    document = doc,
                    sizeBytes = doc.sizeBytes,
                    pageBitmaps = List(doc.pageCount) { null }
                )
                // Add to recents
                RecentFilesManager.addRecent(context, RecentFile(
                    name = doc.name,
                    uriString = uri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    sizeBytes = doc.sizeBytes
                ))
                // Render first page
                renderPage(context, 0)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to open PDF"
                )
            }
        }
    }

    fun renderPage(context: Context, pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                openPdfUseCase.renderPage(doc, pageIndex, 1200)
            }
            val bitmaps = _uiState.value.pageBitmaps.toMutableList()
            if (pageIndex in bitmaps.indices) bitmaps[pageIndex] = bitmap
            _uiState.value = _uiState.value.copy(pageBitmaps = bitmaps)
        }
    }

    fun onPageChanged(page: Int) {
        _uiState.value = _uiState.value.copy(currentPage = page)
    }

    override fun onCleared() {
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        super.onCleared()
    }
}

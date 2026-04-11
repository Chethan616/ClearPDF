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
import kotlin.math.abs

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
    private val renderingPages = mutableSetOf<Int>()

    companion object {
        private const val DEFAULT_RENDER_WIDTH = 1200
        private const val MIN_RENDER_WIDTH = 720
        private const val CACHE_RADIUS = 2
    }

    fun openPdf(context: Context, uri: Uri) {
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        recycleBitmaps(_uiState.value.pageBitmaps)
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
                renderPage(context, 0, DEFAULT_RENDER_WIDTH)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to open PDF"
                )
            }
        }
    }

    fun renderPage(context: Context, pageIndex: Int, targetWidthPx: Int = DEFAULT_RENDER_WIDTH) {
        val state = _uiState.value
        val doc = state.document ?: return
        if (pageIndex !in state.pageBitmaps.indices) return
        if (!renderingPages.add(pageIndex)) return

        val renderWidth = targetWidthPx.coerceAtLeast(MIN_RENDER_WIDTH)
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    openPdfUseCase.renderPage(doc, pageIndex, renderWidth)
                }

                val currentState = _uiState.value
                if (pageIndex !in currentState.pageBitmaps.indices) return@launch

                val bitmaps = currentState.pageBitmaps.toMutableList()
                val previous = bitmaps[pageIndex]
                if (previous != null && previous != bitmap && !previous.isRecycled) {
                    previous.recycle()
                }
                bitmaps[pageIndex] = bitmap

                // Keep only nearby pages in memory for smooth swipes without OOMs.
                bitmaps.forEachIndexed { index, existing ->
                    if (existing != null && index != pageIndex && abs(index - currentState.currentPage) > CACHE_RADIUS) {
                        if (!existing.isRecycled) existing.recycle()
                        bitmaps[index] = null
                    }
                }

                _uiState.value = currentState.copy(pageBitmaps = bitmaps)
            } finally {
                renderingPages.remove(pageIndex)
            }
        }
    }

    fun onPageChanged(page: Int) {
        val state = _uiState.value
        if (page !in state.pageBitmaps.indices) return

        val bitmaps = state.pageBitmaps.toMutableList()
        bitmaps.forEachIndexed { index, existing ->
            if (existing != null && abs(index - page) > CACHE_RADIUS) {
                if (!existing.isRecycled) existing.recycle()
                bitmaps[index] = null
            }
        }

        _uiState.value = state.copy(currentPage = page, pageBitmaps = bitmaps)
    }

    override fun onCleared() {
        recycleBitmaps(_uiState.value.pageBitmaps)
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        super.onCleared()
    }

    private fun recycleBitmaps(bitmaps: List<Bitmap?>) {
        bitmaps.forEach { bitmap ->
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

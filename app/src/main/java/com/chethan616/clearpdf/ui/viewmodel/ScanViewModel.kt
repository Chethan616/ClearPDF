package com.chethan616.clearpdf.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.model.ScanFilter
import com.chethan616.clearpdf.data.model.ScanPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for document scanning functionality
 */
class ScanViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /**
     * Handle scan completion from ML Kit or gallery
     */
    fun onScanComplete(uris: List<Uri>, defaultFilter: ScanFilter = ScanFilter.AUTO) {
        viewModelScope.launch {
            val newPages = uris.map { uri ->
                ScanPage(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    filter = defaultFilter
                )
            }
            
            _uiState.update { state ->
                state.copy(
                    scannedPages = state.scannedPages + newPages,
                    isScanning = false
                )
            }
        }
    }

    /**
     * Delete a scanned page
     */
    fun deletePage(index: Int) {
        _uiState.update { state ->
            state.copy(
                scannedPages = state.scannedPages.filterIndexed { i, _ -> i != index }
            )
        }
    }

    /**
     * Reorder pages (drag and drop)
     */
    fun reorderPages(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val pages = state.scannedPages.toMutableList()
            val item = pages.removeAt(fromIndex)
            pages.add(toIndex, item)
            state.copy(scannedPages = pages)
        }
    }

    /**
     * Apply filter to a specific page
     */
    fun applyFilter(pageIndex: Int, filter: ScanFilter) {
        _uiState.update { state ->
            val pages = state.scannedPages.toMutableList()
            pages[pageIndex] = pages[pageIndex].copy(filter = filter)
            state.copy(scannedPages = pages)
        }
    }

    /**
     * Start scanning process
     */
    fun startScanning() {
        _uiState.update { it.copy(isScanning = true) }
    }

    /**
     * Cancel scanning
     */
    fun cancelScanning() {
        _uiState.update { it.copy(isScanning = false) }
    }

    /**
     * Clear all scanned pages
     */
    fun clearAllPages() {
        _uiState.update { ScanUiState() }
    }

    /**
     * Set error message
     */
    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }

    /**
     * Mark document as saved
     */
    fun markAsSaved(pdfUri: Uri) {
        _uiState.update { it.copy(savedPdfUri = pdfUri) }
    }
}

/**
 * UI state for scan screen
 */
data class ScanUiState(
    val isScanning: Boolean = false,
    val scannedPages: List<ScanPage> = emptyList(),
    val selectedPageIndex: Int? = null,
    val error: String? = null,
    val savedPdfUri: Uri? = null,
    val isSaving: Boolean = false
)

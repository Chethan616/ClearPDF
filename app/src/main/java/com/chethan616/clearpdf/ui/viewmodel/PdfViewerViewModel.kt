package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.usecase.OpenPdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PdfViewerUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PdfViewerViewModel(private val openPdfUseCase: OpenPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()
    fun onSelectFile() {}
    fun onPageChanged(page: Int) { _uiState.value = _uiState.value.copy(currentPage = page) }
}

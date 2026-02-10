package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.usecase.SplitPdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SplitPdfUiState(
    val sourceFileName: String = "",
    val pageCount: Int = 0,
    val splitAfterPages: List<Int> = emptyList(),
    val isSplitting: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class SplitPdfViewModel(private val splitPdfUseCase: SplitPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(SplitPdfUiState())
    val uiState: StateFlow<SplitPdfUiState> = _uiState.asStateFlow()
    fun onSelectFile() {}
    fun onToggleSplitAfterPage(pageIndex: Int) {
        val current = _uiState.value.splitAfterPages.toMutableList()
        if (pageIndex in current) current.remove(pageIndex) else current.add(pageIndex)
        _uiState.value = _uiState.value.copy(splitAfterPages = current.sorted())
    }
    fun onSplit() {}
}

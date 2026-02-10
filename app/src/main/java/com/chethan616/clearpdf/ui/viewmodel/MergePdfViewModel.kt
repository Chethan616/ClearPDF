package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.usecase.MergePdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MergePdfUiState(
    val selectedFiles: List<String> = emptyList(),
    val isMerging: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class MergePdfViewModel(private val mergePdfUseCase: MergePdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(MergePdfUiState())
    val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()
    fun onSelectFiles() {}
    fun onMerge() {}
    fun onRemoveFile(index: Int) {
        val current = _uiState.value.selectedFiles.toMutableList()
        if (index in current.indices) { current.removeAt(index); _uiState.value = _uiState.value.copy(selectedFiles = current) }
    }
}

package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.usecase.CreatePdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CreateMode { BLANK, FROM_IMAGES, FROM_TEXT }

data class CreatePdfUiState(
    val selectedMode: CreateMode = CreateMode.BLANK,
    val textContent: String = "",
    val selectedImageCount: Int = 0,
    val isCreating: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class CreatePdfViewModel(private val createPdfUseCase: CreatePdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CreatePdfUiState())
    val uiState: StateFlow<CreatePdfUiState> = _uiState.asStateFlow()
    fun onModeSelected(mode: CreateMode) { _uiState.value = _uiState.value.copy(selectedMode = mode) }
    fun onTextChanged(text: String) { _uiState.value = _uiState.value.copy(textContent = text) }
    fun onCreate() {}
}

package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.usecase.CompressPdfUseCase
import com.kyant.pdfcore.model.CompressionQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompressPdfUiState(
    val sourceFileName: String = "",
    val originalSizeBytes: Long = 0,
    val selectedQuality: CompressionQuality = CompressionQuality.MEDIUM,
    val isCompressing: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class CompressPdfViewModel(private val compressPdfUseCase: CompressPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CompressPdfUiState())
    val uiState: StateFlow<CompressPdfUiState> = _uiState.asStateFlow()
    fun onSelectFile() {}
    fun onQualityChanged(quality: CompressionQuality) { _uiState.value = _uiState.value.copy(selectedQuality = quality) }
    fun onCompress() {}
}

package com.chethan616.clearpdf.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chethan616.clearpdf.domain.model.ToolItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val tools: List<ToolItem> = emptyList(),
    val recentFiles: List<String> = emptyList(),
    val isLoading: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            tools = listOf(
                ToolItem("open", "Open PDF", "View and read PDF documents"),
                ToolItem("merge", "Merge PDFs", "Combine multiple PDFs into one"),
                ToolItem("split", "Split PDF", "Extract pages or split documents"),
                ToolItem("compress", "Compress PDF", "Reduce PDF file size"),
                ToolItem("create", "Create PDF", "Create a new PDF from scratch")
            ),
            recentFiles = listOf("Annual Report 2025.pdf", "Contract Draft.pdf", "Invoice_0042.pdf")
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}

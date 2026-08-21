package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.splitter.PdfSplitterImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractPagesUiState(
    val sourceUri: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val rangeText: String = "",
    val isProcessing: Boolean = false,
    val lastOutputUri: Uri? = null,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class ExtractPagesViewModel : ViewModel() {

    private val splitter = PdfSplitterImpl()
    private val _uiState = MutableStateFlow(ExtractPagesUiState())
    val uiState: StateFlow<ExtractPagesUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        viewModelScope.launch {
            val name = queryName(context, uri)
            val count = withContext(Dispatchers.IO) { runCatching { pageCount(context, uri) }.getOrDefault(0) }
            _uiState.update {
                it.copy(
                    sourceUri = uri, sourceName = name, pageCount = count,
                    rangeText = if (count > 0) "1-$count" else "",
                    lastOutputUri = null, resultMessage = null,
                    errorMessage = if (count == 0) "Couldn't read this PDF" else null
                )
            }
        }
    }

    fun onRangeChange(value: String) = _uiState.update { it.copy(rangeText = value) }

    fun apply(context: Context) {
        val src = _uiState.value.sourceUri ?: return
        if (_uiState.value.isProcessing) return
        val pages = parsePageRanges(_uiState.value.rangeText, _uiState.value.pageCount)
        if (pages.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter valid pages (e.g. 1-3, 5)") }
            return
        }
        _uiState.update { it.copy(isProcessing = true, errorMessage = null, resultMessage = null, lastOutputUri = null) }
        viewModelScope.launch {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ClearPDF_Extracted_$ts.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = createOutputUri(context, fileName)
                val source = PdfDocument(uri = src, name = _uiState.value.sourceName, pageCount = _uiState.value.pageCount)
                withContext(Dispatchers.IO) { splitter.extractPages(context, source, pages, outUri) }
                RecentFilesManager.addRecent(context, RecentFile(
                    name = fileName, uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(), sizeBytes = 0
                ))
                _uiState.update {
                    it.copy(
                        isProcessing = false, lastOutputUri = outUri,
                        resultMessage = "Extracted ${pages.size} page${if (pages.size == 1) "" else "s"} · saved to $saveLabel"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = t.message ?: "Couldn't extract pages") }
            }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(errorMessage = null, resultMessage = null) }

    /** Parse a 1-based range string like "1-3, 5, 8-10" into sorted, unique, 0-based indices. */
    private fun parsePageRanges(input: String, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val out = sortedSetOf<Int>()
        input.split(",").forEach { raw ->
            val part = raw.trim()
            if (part.isEmpty()) return@forEach
            if (part.contains("-")) {
                val bounds = part.split("-")
                val a = bounds.getOrNull(0)?.trim()?.toIntOrNull()
                val b = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                if (a != null && b != null) {
                    for (n in minOf(a, b)..maxOf(a, b)) if (n in 1..pageCount) out.add(n - 1)
                }
            } else {
                part.toIntOrNull()?.let { if (it in 1..pageCount) out.add(it - 1) }
            }
        }
        return out.toList()
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "document.pdf"

    private fun pageCount(context: Context, uri: Uri): Int {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        return pfd.use { PdfRenderer(it).use { r -> r.pageCount } }
    }

    private fun createOutputUri(context: Context, fileName: String): Uri {
        val customUri = SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            return try {
                val docUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                docUri?.createFile("application/pdf", fileName)?.uri ?: createDownloadUri(context, fileName)
            } catch (_: Exception) { createDownloadUri(context, fileName) }
        }
        return createDownloadUri(context, fileName)
    }

    private fun createDownloadUri(context: Context, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("Unable to create output in Downloads")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            if (!file.exists()) file.createNewFile()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
    }
}

package com.chethan616.clearpdf.ui.viewmodel

import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.splitter.PdfSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SplitMode {
    SPLIT_ALL,
    EXTRACT_SELECTED
}

data class SplitPdfUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val pageCount: Int = 0,
    val splitAfterPages: List<Int> = emptyList(),
    val selectedPages: List<Int> = emptyList(),
    val mode: SplitMode = SplitMode.EXTRACT_SELECTED,
    val isSplitting: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val pageThumbnails: List<Bitmap?> = emptyList(),
    val lastOutputUri: Uri? = null,
    val saveLocationLabel: String = "Downloads (default)"
)

class SplitPdfViewModel(private val splitter: PdfSplitter) : ViewModel() {
    private val _uiState = MutableStateFlow(SplitPdfUiState())
    val uiState: StateFlow<SplitPdfUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers do not support persistable grants.
                }

                val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val renderer = withContext(Dispatchers.IO) { android.graphics.pdf.PdfRenderer(fd) }
                val name = queryFileName(context, uri) ?: "Unknown.pdf"
                val count = renderer.pageCount

                // Render small thumbnails for all pages
                val thumbnails = withContext(Dispatchers.IO) {
                    (0 until count).map { i ->
                        try {
                            val page = renderer.openPage(i)
                            val thumbWidth = 300
                            val scale = thumbWidth.toFloat() / page.width
                            val thumbHeight = (page.height * scale).toInt()
                            val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            bmp
                        } catch (_: Exception) { null }
                    }
                }

                renderer.close()
                fd.close()
                _uiState.value = SplitPdfUiState(
                    sourceFileName = name,
                    sourceUri = uri,
                    pageCount = count,
                    pageThumbnails = thumbnails,
                    selectedPages = emptyList(),
                    mode = SplitMode.EXTRACT_SELECTED
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun onModeChanged(mode: SplitMode) {
        _uiState.value = _uiState.value.copy(mode = mode, errorMessage = null, resultMessage = null)
    }

    fun onToggleSelectedPage(pageIndex: Int) {
        val selected = _uiState.value.selectedPages.toMutableList()
        if (pageIndex in selected) selected.remove(pageIndex) else selected.add(pageIndex)
        _uiState.value = _uiState.value.copy(selectedPages = selected.sorted())
    }

    fun onSelectAllPages() {
        _uiState.value = _uiState.value.copy(selectedPages = (0 until _uiState.value.pageCount).toList())
    }

    fun onSelectOddPages() {
        _uiState.value = _uiState.value.copy(selectedPages = (0 until _uiState.value.pageCount).filter { (it + 1) % 2 != 0 })
    }

    fun onSelectEvenPages() {
        _uiState.value = _uiState.value.copy(selectedPages = (0 until _uiState.value.pageCount).filter { (it + 1) % 2 == 0 })
    }

    fun onClearSelection() {
        _uiState.value = _uiState.value.copy(selectedPages = emptyList())
    }

    fun onToggleSplitAfterPage(pageIndex: Int) {
        val current = _uiState.value.splitAfterPages.toMutableList()
        if (pageIndex in current) current.remove(pageIndex) else current.add(pageIndex)
        _uiState.value = _uiState.value.copy(splitAfterPages = current.sorted())
    }

    fun onSplitAll(context: Context) {
        val srcUri = _uiState.value.sourceUri ?: return
        _uiState.value = _uiState.value.copy(isSplitting = true, errorMessage = null, resultMessage = null, lastOutputUri = null)
        viewModelScope.launch {
            try {
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outDir = Uri.fromFile(context.cacheDir)
                val source = PdfDocument(uri = srcUri, name = _uiState.value.sourceFileName)
                val results = withContext(Dispatchers.IO) { splitter.splitAll(context, source, outDir) }
                var firstOutputUri: Uri? = null

                // Copy split pages to Downloads
                withContext(Dispatchers.IO) {
                    for (doc in results) {
                        val outUri = createOutputUri(context, doc.name)
                        if (firstOutputUri == null) firstOutputUri = outUri
                        val inFile = java.io.File(doc.uri.path!!)
                        val out = context.contentResolver.openOutputStream(outUri)
                            ?: throw IllegalStateException("Cannot open split output stream")
                        out.use {
                            inFile.inputStream().use { it.copyTo(out) }
                            out.flush()
                        }
                        RecentFilesManager.addRecent(context, RecentFile(
                            name = doc.name, uriString = outUri.toString(),
                            timestamp = System.currentTimeMillis(), pageCount = 1
                        ))
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    lastOutputUri = firstOutputUri,
                    saveLocationLabel = saveLabel,
                    resultMessage = "Split into ${results.size} pages\nSaved to $saveLabel"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    errorMessage = e.message ?: "Split failed"
                )
            }
        }
    }

    fun onExtractPages(context: Context, pages: List<Int>) {
        val srcUri = _uiState.value.sourceUri ?: return
        _uiState.value = _uiState.value.copy(isSplitting = true, errorMessage = null, resultMessage = null, lastOutputUri = null)
        viewModelScope.launch {
            try {
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = createOutputUri(context, "ClearPDF_Extract_${System.currentTimeMillis()}.pdf")
                val source = PdfDocument(uri = srcUri, name = _uiState.value.sourceFileName)
                withContext(Dispatchers.IO) { splitter.extractPages(context, source, pages, outUri) }
                RecentFilesManager.addRecent(context, RecentFile(
                    name = queryFileName(context, outUri) ?: "Extracted.pdf",
                    uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = pages.size
                ))
                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    lastOutputUri = outUri,
                    saveLocationLabel = saveLabel,
                    resultMessage = "Extracted ${pages.size} pages\nSaved to $saveLabel"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    errorMessage = e.message ?: "Extract failed"
                )
            }
        }
    }

    fun onRunPrimaryAction(context: Context) {
        when (_uiState.value.mode) {
            SplitMode.SPLIT_ALL -> onSplitAll(context)
            SplitMode.EXTRACT_SELECTED -> {
                if (_uiState.value.selectedPages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Select at least one page to extract")
                    return
                }
                onExtractPages(context, _uiState.value.selectedPages)
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(resultMessage = null, errorMessage = null)
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun createOutputUri(context: Context, fileName: String): Uri {
        val customUri = SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            return try {
                val docUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                val created = docUri?.createFile("application/pdf", fileName)?.uri
                if (created != null) {
                    created
                } else {
                    createDownloadUri(context, fileName)
                }
            } catch (_: Exception) {
                createDownloadUri(context, fileName)
            }
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

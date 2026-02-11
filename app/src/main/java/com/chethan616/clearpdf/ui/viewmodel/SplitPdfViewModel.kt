package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
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

data class SplitPdfUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val pageCount: Int = 0,
    val splitAfterPages: List<Int> = emptyList(),
    val isSplitting: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val pageThumbnails: List<Bitmap?> = emptyList()
)

class SplitPdfViewModel(private val splitter: PdfSplitter) : ViewModel() {
    private val _uiState = MutableStateFlow(SplitPdfUiState())
    val uiState: StateFlow<SplitPdfUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
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
                    pageThumbnails = thumbnails
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun onToggleSplitAfterPage(pageIndex: Int) {
        val current = _uiState.value.splitAfterPages.toMutableList()
        if (pageIndex in current) current.remove(pageIndex) else current.add(pageIndex)
        _uiState.value = _uiState.value.copy(splitAfterPages = current.sorted())
    }

    fun onSplitAll(context: Context) {
        val srcUri = _uiState.value.sourceUri ?: return
        _uiState.value = _uiState.value.copy(isSplitting = true, errorMessage = null, resultMessage = null)
        viewModelScope.launch {
            try {
                val outDir = Uri.fromFile(context.cacheDir)
                val source = PdfDocument(uri = srcUri, name = _uiState.value.sourceFileName)
                val results = withContext(Dispatchers.IO) { splitter.splitAll(context, source, outDir) }

                // Copy split pages to Downloads
                withContext(Dispatchers.IO) {
                    for (doc in results) {
                        val outUri = createDownloadUri(context, doc.name)
                        val inFile = java.io.File(doc.uri.path!!)
                        context.contentResolver.openOutputStream(outUri)?.use { out ->
                            inFile.inputStream().use { it.copyTo(out) }
                        }
                        RecentFilesManager.addRecent(context, RecentFile(
                            name = doc.name, uriString = outUri.toString(),
                            timestamp = System.currentTimeMillis(), pageCount = 1
                        ))
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    resultMessage = "Split into ${results.size} pages\nSaved to Downloads"
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
        _uiState.value = _uiState.value.copy(isSplitting = true, errorMessage = null, resultMessage = null)
        viewModelScope.launch {
            try {
                val outUri = createDownloadUri(context, "ClearPDF_Extract_${System.currentTimeMillis()}.pdf")
                val source = PdfDocument(uri = srcUri, name = _uiState.value.sourceFileName)
                withContext(Dispatchers.IO) { splitter.extractPages(context, source, pages, outUri) }
                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    resultMessage = "Extracted ${pages.size} pages\nSaved to Downloads"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSplitting = false,
                    errorMessage = e.message ?: "Extract failed"
                )
            }
        }
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

    private fun createDownloadUri(context: Context, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            Uri.fromFile(java.io.File(dir, fileName))
        }
    }
}

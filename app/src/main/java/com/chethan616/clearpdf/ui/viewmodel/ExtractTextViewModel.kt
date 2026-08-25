package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.kyant.ocrcore.OcrService
import com.kyant.ocrcore.OcrWord
import com.kyant.pdfcore.converter.PdfConverter
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.raster.PdfRasterizer
import com.kyant.pdfcore.searchable.InvisibleWord
import com.kyant.pdfcore.searchable.PdfSearchableStamper
import com.kyant.pdfcore.viewer.PdfViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractTextUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val isExtracting: Boolean = false,
    val text: String = "",
    val hasResult: Boolean = false,
    val errorMessage: String? = null,
    /** True once extraction found no digital text layer — offers the OCR fallback. */
    val canRecognize: Boolean = false,
    val isRecognizing: Boolean = false,
    val recognizeProgress: Pair<Int, Int>? = null,
    /** True once OCR has produced word boxes that can be baked into a searchable PDF. */
    val canMakeSearchable: Boolean = false,
    val isSavingSearchable: Boolean = false,
    val searchableOutputUri: Uri? = null,
    val searchableSavedLabel: String? = null
)

class ExtractTextViewModel(
    private val converter: PdfConverter,
    private val ocrService: OcrService,
    private val pdfViewer: PdfViewer
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExtractTextUiState())
    val uiState: StateFlow<ExtractTextUiState> = _uiState.asStateFlow()

    /** Word boxes from the last OCR pass, kept so "Make Searchable" doesn't need to re-run OCR. */
    private var lastOcrWordsByPage: Map<Int, List<OcrWord>> = emptyMap()

    fun onSelectFile(context: Context, uri: Uri) {
        lastOcrWordsByPage = emptyMap()
        _uiState.value = ExtractTextUiState(isExtracting = true)
        viewModelScope.launch {
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = queryFileName(context, uri) ?: "Unknown.pdf"
                val source = PdfDocument(uri = uri, name = name)
                val extracted = withContext(Dispatchers.IO) { converter.extractText(context, source) }.trim()
                val empty = extracted.isEmpty()
                _uiState.value = ExtractTextUiState(
                    sourceFileName = name,
                    sourceUri = uri,
                    isExtracting = false,
                    text = extracted,
                    hasResult = true,
                    canRecognize = empty,
                    errorMessage = if (empty) context.getString(R.string.extract_no_selectable_text) else null
                )
            } catch (e: Exception) {
                _uiState.value = ExtractTextUiState(isExtracting = false, errorMessage = context.getString(R.string.text_extraction_failed))
            }
        }
    }

    /**
     * Fallback for scanned/image-only PDFs: runs on-device OCR page by page (ML Kit,
     * falling back to Tesseract4Android) and joins the recognized text, closing the
     * "no selectable text" dead end left by pure PdfBox extraction.
     */
    fun recognizeText(context: Context) {
        val uri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isRecognizing) return
        _uiState.value = _uiState.value.copy(isRecognizing = true, recognizeProgress = null)
        viewModelScope.launch {
            val wordsByPage = mutableMapOf<Int, List<OcrWord>>()
            val result = withContext(Dispatchers.IO) {
                val doc = runCatching { pdfViewer.open(context, uri) }.getOrNull()
                val pageCount = doc?.pageCount?.takeIf { it > 0 } ?: 0
                doc?.let { runCatching { pdfViewer.close(it) } }
                if (pageCount == 0) return@withContext ""

                val pages = mutableListOf<String>()
                for (i in 0 until pageCount) {
                    // MutableStateFlow.value is safe to set from any thread.
                    _uiState.value = _uiState.value.copy(recognizeProgress = i to pageCount)
                    val bitmap = runCatching { PdfRasterizer.rasterizePageBitmap(context, uri, i) }.getOrNull()
                    if (bitmap != null) {
                        val words = runCatching { ocrService.recognize(context, bitmap) }.getOrNull()?.words.orEmpty()
                        if (words.isNotEmpty()) {
                            wordsByPage[i] = words
                            pages.add(words.joinToString(" ") { it.text })
                        }
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                pages.joinToString("\n\n")
            }
            lastOcrWordsByPage = wordsByPage
            val trimmed = result.trim()
            _uiState.value = _uiState.value.copy(
                isRecognizing = false,
                recognizeProgress = null,
                text = trimmed,
                hasResult = true,
                canRecognize = false,
                canMakeSearchable = wordsByPage.isNotEmpty(),
                errorMessage = if (trimmed.isEmpty()) context.getString(R.string.extract_no_selectable_text) else null
            )
        }
    }

    /** Bakes the last OCR pass's word boxes into a copy of the source PDF as an invisible,
     *  selectable/searchable text layer — readable in any PDF viewer, not just ClearPDF. */
    fun makeSearchablePdf(context: Context) {
        val uri = _uiState.value.sourceUri ?: return
        val wordsByPage = lastOcrWordsByPage
        if (wordsByPage.isEmpty() || _uiState.value.isSavingSearchable) return
        _uiState.value = _uiState.value.copy(isSavingSearchable = true, errorMessage = null, searchableOutputUri = null)
        viewModelScope.launch {
            try {
                val invisibleWordsByPage = wordsByPage.mapValues { (_, words) ->
                    words.map { InvisibleWord(it.text, it.left, it.top, it.right, it.bottom) }
                }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ClearPDF_Searchable_$ts.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = createOutputUri(context, fileName)
                withContext(Dispatchers.IO) {
                    PdfSearchableStamper.stamp(context, uri, outUri, invisibleWordsByPage)
                }
                RecentFilesManager.addRecent(
                    context,
                    RecentFile(name = fileName, uriString = outUri.toString(), timestamp = System.currentTimeMillis(), sizeBytes = 0)
                )
                _uiState.value = _uiState.value.copy(
                    isSavingSearchable = false,
                    searchableOutputUri = outUri,
                    searchableSavedLabel = saveLabel
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isSavingSearchable = false,
                    errorMessage = t.message ?: "Couldn't create a searchable PDF"
                )
            }
        }
    }

    fun reset() {
        lastOcrWordsByPage = emptyMap()
        _uiState.value = ExtractTextUiState()
    }

    private fun queryFileName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    } catch (_: Exception) { null }

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

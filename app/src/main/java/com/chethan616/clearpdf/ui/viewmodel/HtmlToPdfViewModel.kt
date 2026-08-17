package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
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
import com.chethan616.clearpdf.util.HtmlToPdfConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

enum class WebToPdfMode { URL, HTML }

data class HtmlToPdfUiState(
    val mode: WebToPdfMode = WebToPdfMode.URL,
    val url: String = "",
    val html: String = "",
    val sourceName: String = "",
    val isProcessing: Boolean = false,
    val lastOutputUri: Uri? = null,
    val resultMessage: String? = null,
    val errorMessage: String? = null
)

class HtmlToPdfViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HtmlToPdfUiState())
    val uiState: StateFlow<HtmlToPdfUiState> = _uiState.asStateFlow()

    fun onModeChange(mode: WebToPdfMode) = _uiState.update { it.copy(mode = mode, lastOutputUri = null, resultMessage = null, errorMessage = null) }
    fun onUrlChange(value: String) = _uiState.update { it.copy(url = value) }
    fun onHtmlChange(value: String) = _uiState.update { it.copy(html = value) }

    fun onLoadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val name = queryName(context, uri)
            val content = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (content == null) {
                _uiState.update { it.copy(errorMessage = "Couldn't read that file") }
            } else {
                _uiState.update { it.copy(html = content, sourceName = name, lastOutputUri = null, resultMessage = null, errorMessage = null) }
            }
        }
    }

    fun convert(context: Context) {
        if (_uiState.value.isProcessing) return
        val mode = _uiState.value.mode
        val rawUrl = _uiState.value.url.trim()
        if (mode == WebToPdfMode.URL && rawUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a web address") }
            return
        }
        if (mode == WebToPdfMode.HTML && _uiState.value.html.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter or load some HTML") }
            return
        }
        // Default the scheme so "example.com" works.
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        _uiState.update { it.copy(isProcessing = true, errorMessage = null, resultMessage = null, lastOutputUri = null) }
        viewModelScope.launch {
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ClearPDF_Web_$ts.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = withContext(Dispatchers.IO) { createOutputUri(context, fileName) }
                val html = _uiState.value.html
                // WebView work must run on the main thread.
                val ok = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        val cb: (Boolean) -> Unit = { success -> if (cont.isActive) cont.resume(success) }
                        if (mode == WebToPdfMode.URL) HtmlToPdfConverter.convertUrl(context, url, outUri, cb)
                        else HtmlToPdfConverter.convertHtml(context, html, outUri, cb)
                    }
                }
                if (ok) {
                    RecentFilesManager.addRecent(context, RecentFile(
                        name = fileName, uriString = outUri.toString(),
                        timestamp = System.currentTimeMillis(), sizeBytes = 0
                    ))
                    _uiState.update { it.copy(isProcessing = false, lastOutputUri = outUri, resultMessage = "PDF created · saved to $saveLabel") }
                } else {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Couldn't create the PDF") }
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = t.message ?: "Couldn't convert HTML") }
            }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(errorMessage = null, resultMessage = null) }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "document.html"

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

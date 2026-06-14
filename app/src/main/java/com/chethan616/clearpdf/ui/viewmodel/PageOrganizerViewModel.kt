package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.kyant.pdfcore.editor.PdfEditor
import com.kyant.pdfcore.model.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One page in the organiser: which source page it came from, the extra rotation the
 * user applied, and a cached thumbnail.
 */
data class OrganizerPage(
    val originalIndex: Int,
    val rotation: Int = 0,
    val thumbnail: Bitmap? = null
)

data class PageOrganizerUiState(
    val sourceFileName: String = "",
    val sourceUri: Uri? = null,
    val pages: List<OrganizerPage> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val lastOutputUri: Uri? = null,
    val saveLocationLabel: String = "Downloads (default)"
)

class PageOrganizerViewModel(private val editor: PdfEditor) : ViewModel() {
    private val _uiState = MutableStateFlow(PageOrganizerUiState())
    val uiState: StateFlow<PageOrganizerUiState> = _uiState.asStateFlow()

    fun onSelectFile(context: Context, uri: Uri) {
        _uiState.value = PageOrganizerUiState(isLoading = true)
        viewModelScope.launch {
            try {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val name = queryFileName(context, uri) ?: "Unknown.pdf"
                val pages = withContext(Dispatchers.IO) { renderThumbnails(context, uri) }
                _uiState.value = PageOrganizerUiState(
                    sourceFileName = name,
                    sourceUri = uri,
                    pages = pages.mapIndexed { i, bmp -> OrganizerPage(originalIndex = i, thumbnail = bmp) }
                )
            } catch (e: Exception) {
                _uiState.value = PageOrganizerUiState(errorMessage = e.message ?: "Could not open PDF")
            }
        }
    }

    fun movePage(from: Int, to: Int) {
        val pages = _uiState.value.pages.toMutableList()
        if (from !in pages.indices || to !in pages.indices) return
        pages.add(to, pages.removeAt(from))
        _uiState.value = _uiState.value.copy(pages = pages, resultMessage = null)
    }

    fun rotatePage(index: Int, deltaDegrees: Int = 90) {
        val pages = _uiState.value.pages.toMutableList()
        if (index !in pages.indices) return
        val current = pages[index]
        pages[index] = current.copy(rotation = (((current.rotation + deltaDegrees) % 360) + 360) % 360)
        _uiState.value = _uiState.value.copy(pages = pages, resultMessage = null)
    }

    fun deletePage(index: Int) {
        val pages = _uiState.value.pages.toMutableList()
        if (index !in pages.indices || pages.size <= 1) return
        pages.removeAt(index)
        _uiState.value = _uiState.value.copy(pages = pages, resultMessage = null)
    }

    fun save(context: Context, fileName: String, overrideUri: Uri? = null) {
        val src = _uiState.value.sourceUri ?: return
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, resultMessage = null, lastOutputUri = null)
        viewModelScope.launch {
            try {
                val target = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"
                val saveLabel = SaveLocationManager.getSavePathDisplay(context)
                val outUri = withContext(Dispatchers.IO) { createOutputUri(context, target, overrideUri) }
                val source = PdfDocument(uri = src, name = _uiState.value.sourceFileName)
                val order = pages.map { it.originalIndex }
                val rotations = pages.associate { it.originalIndex to it.rotation }.filterValues { it != 0 }
                withContext(Dispatchers.IO) { editor.applyPageEdits(context, source, order, rotations, outUri) }

                RecentFilesManager.addRecent(context, RecentFile(
                    name = queryFileName(context, outUri) ?: target,
                    uriString = outUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = pages.size
                ))
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    lastOutputUri = outUri,
                    saveLocationLabel = saveLabel,
                    resultMessage = "Saved ${pages.size} pages\nSaved to $saveLabel"
                )
                if (GitHubStarPromptManager.recordPdfInteraction(context)) StarPromptEventBus.requestPrompt()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message ?: "Save failed")
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(resultMessage = null, errorMessage = null)
    }

    private fun renderThumbnails(context: Context, uri: Uri): List<Bitmap?> {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return fd.use {
            val renderer = android.graphics.pdf.PdfRenderer(it)
            try {
                (0 until renderer.pageCount).map { i ->
                    runCatching {
                        val page = renderer.openPage(i)
                        val thumbWidth = 300
                        val scale = thumbWidth.toFloat() / page.width
                        val thumbHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bmp
                    }.getOrNull()
                }
            } finally {
                renderer.close()
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    } catch (_: Exception) { null }

    private fun createOutputUri(context: Context, fileName: String, overrideUri: Uri?): Uri {
        val customUri = overrideUri ?: SaveLocationManager.getSaveUri(context)
        if (customUri != null) {
            runCatching {
                val docUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customUri)
                docUri?.createFile("application/pdf", fileName)?.uri
            }.getOrNull()?.let { return it }
        }
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

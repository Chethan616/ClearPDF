package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.utils.SpreadsheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Loads a spreadsheet (.xlsx/.xls) into structured sheets for the interactive viewer. */
class SpreadsheetViewModel : ViewModel() {

    data class UiState(
        val fileName: String = "",
        val fileUri: Uri? = null,        // local (mirrored) copy, for sharing
        val sheets: List<SpreadsheetParser.Sheet> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        /** True once any cell has been edited in this session. */
        val isEdited: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var started = false

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { queryName(context, uri) }
            val (sheets, localUri) = withContext(Dispatchers.IO) {
                // Mirror to app cache so the file stays readable when reopened from recents (a picked
                // content:// URI may lose permission later), then parse from the local copy.
                val local = runCatching { mirrorToCache(context, uri, name) }.getOrDefault(uri)
                SpreadsheetParser.parse(context, local) to local
            }
            _state.value = if (sheets.isEmpty()) {
                UiState(fileName = name, fileUri = localUri, isLoading = false, error = "Couldn't read this spreadsheet.")
            } else {
                UiState(fileName = name, fileUri = localUri, sheets = sheets, isLoading = false)
            }
            if (sheets.isNotEmpty()) withContext(Dispatchers.IO) {
                RecentFilesManager.addRecent(
                    context,
                    RecentFile(
                        name = name,
                        uriString = localUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        pageCount = sheets.size
                    )
                )
            }
        }
    }

    /**
     * Overwrite one cell. The edit lives in memory for this viewing session — the source .xlsx is
     * not rewritten, because the only POI artifact on the classpath is `poi` 3.17 (HSSF/.xls), with
     * no OOXML writer, so persisting would mean regenerating the workbook from scratch and dropping
     * every formula, style and merge the original carries.
     *
     * Rows are padded out to [col] so a cell past the parsed width of its row can still be filled.
     */
    fun updateCell(sheetIndex: Int, row: Int, col: Int, value: String) {
        val sheets = _state.value.sheets
        val sheet = sheets.getOrNull(sheetIndex) ?: return
        if (row !in sheet.rows.indices) return

        val newRows = sheet.rows.toMutableList()
        val cells = newRows[row].toMutableList()
        while (cells.size <= col) cells.add("")
        cells[col] = value
        newRows[row] = cells

        val newSheets = sheets.toMutableList()
        newSheets[sheetIndex] = sheet.copy(rows = newRows)
        _state.value = _state.value.copy(sheets = newSheets, isEdited = true)
    }

    /** Convert the spreadsheet to a PDF (reuses the doc converter) and hand back its URI. */
    fun exportToPdf(context: Context, onDone: (Uri?) -> Unit) {
        val uri = _state.value.fileUri ?: return onDone(null)
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching { com.chethan616.clearpdf.utils.UniversalDocumentConverter.convertToPdf(context, uri) }.getOrNull()
            }
            onDone(out)
        }
    }

    private fun mirrorToCache(context: Context, uri: Uri, name: String): Uri {
        val dir = File(context.cacheDir, "sheets").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "sheet.xlsx" }
        val file = File(dir, "${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { input.copyTo(it) }
        } ?: return uri
        if (file.length() == 0L) { file.delete(); return uri }
        return Uri.fromFile(file)
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "Spreadsheet"
}

package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.LocalDocumentMirror
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.utils.SpreadsheetParser
import com.chethan616.clearpdf.utils.xlsx.Axis
import com.chethan616.clearpdf.utils.xlsx.BorderSide
import com.chethan616.clearpdf.utils.xlsx.CellChange
import com.chethan616.clearpdf.utils.xlsx.CellData
import com.chethan616.clearpdf.utils.xlsx.CellRange
import com.chethan616.clearpdf.utils.xlsx.ColorSpec
import com.chethan616.clearpdf.utils.xlsx.EditOp
import com.chethan616.clearpdf.utils.xlsx.StylePatch
import com.chethan616.clearpdf.utils.xlsx.StyleTable
import com.chethan616.clearpdf.utils.xlsx.XlsxEdits
import com.chethan616.clearpdf.utils.xlsx.XlsxReader
import com.chethan616.clearpdf.utils.xlsx.XlsxSheet
import com.chethan616.clearpdf.utils.xlsx.XlsxWorkbook
import com.chethan616.clearpdf.utils.xlsx.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Border presets the toolbar offers, applied across a selected range. */
enum class BorderPreset { ALL, OUTER, INNER, TOP, BOTTOM, LEFT, RIGHT, THICK_OUTER, NONE }

/**
 * Loads a spreadsheet into an editable [XlsxWorkbook] and owns the edit log.
 *
 * Every change is an [EditOp] applied to the in-memory model; undo/redo move ops between the two
 * stacks; saving replays the applied stack onto the *pristine* local mirror of the file with
 * [XlsxWriter], so what gets written is exactly what is on screen and nothing else in the package
 * is touched.
 */
class SpreadsheetViewModel : ViewModel() {

    data class UiState(
        val fileName: String = "",
        /** Local mirror of the file as opened (never modified). */
        val fileUri: Uri? = null,
        /** The uri the user opened — the "Save" target. */
        val sourceUri: Uri? = null,
        val workbook: XlsxWorkbook? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Bumped on every model change so the grid redraws. */
        val version: Int = 0,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val dirty: Boolean = false,
        val saving: Boolean = false
    ) {
        val editable: Boolean get() = workbook?.editable == true && fileUri?.scheme == "file"
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var started = false

    private val applied = ArrayList<EditOp>()
    private val redoStack = ArrayList<EditOp>()
    /** `applied.size` at the last save, or -1 when that state is no longer reachable by undo. */
    private var savedAt = 0

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val name = withContext(Dispatchers.IO) {
                runCatching { queryName(context, uri) }.getOrNull()
                    ?: RecentFilesManager.getRecents(context).firstOrNull { it.uriString == uri.toString() }?.name
                    ?: uri.lastPathSegment ?: "Spreadsheet"
            }
            val (wb, localUri) = withContext(Dispatchers.IO) {
                val extension = name.substringAfterLast('.', "xlsx").lowercase()
                val readable = runCatching { LocalDocumentMirror.resolve(context, uri, extension) }.getOrDefault(uri)
                val local = runCatching { mirrorToCache(context, readable, name) }.getOrDefault(readable)
                val book = runCatching {
                    if (name.lowercase().endsWith(".xls")) fromLegacy(SpreadsheetParser.parse(context, local))
                    else context.contentResolver.openInputStream(local)?.use { XlsxReader.read(it) }
                }.getOrNull()
                book to local
            }
            val visible = wb?.sheets?.any { !it.hidden } == true
            _state.value = if (wb == null || !visible) {
                UiState(fileName = name, fileUri = localUri, sourceUri = uri, isLoading = false, error = "Couldn't read this spreadsheet.")
            } else {
                UiState(fileName = name, fileUri = localUri, sourceUri = uri, workbook = wb, isLoading = false)
            }
            if (wb != null) withContext(Dispatchers.IO) {
                runCatching {
                    RecentFilesManager.addRecent(
                        context,
                        RecentFile(name = name, uriString = uri.toString(), timestamp = System.currentTimeMillis(), pageCount = wb.sheets.count { !it.hidden })
                    )
                }
            }
        }
    }

    /** Legacy .xls: values only, read-only (there is no package to patch). */
    private fun fromLegacy(sheets: List<SpreadsheetParser.Sheet>): XlsxWorkbook? {
        if (sheets.isEmpty()) return null
        val out = sheets.mapIndexed { i, s ->
            XlsxSheet(s.name, "legacy$i").also { xs ->
                s.rows.forEachIndexed { r, row ->
                    val rowNo = s.rowNumberAt(r) - 1
                    row.forEachIndexed { c, v ->
                        if (v.isNotEmpty()) {
                            val col = com.chethan616.clearpdf.utils.xlsx.XlsxRefs.colIndex(s.labelAt(c)).coerceAtLeast(0)
                            xs.rows.getOrPut(rowNo) { com.chethan616.clearpdf.utils.xlsx.RowData() }.cells[col] =
                                CellData(if (v.toDoubleOrNull() != null) "" else "inlineStr", v, 0)
                        }
                    }
                }
            }
        }
        return XlsxWorkbook(out, StyleTable.empty(), emptyList(), editable = false)
    }

    // ── edit log ───────────────────────────────────────────────────────────────

    private fun push(op: EditOp) {
        val wb = _state.value.workbook ?: return
        if (!wb.editable) return
        XlsxEdits.apply(wb, op)
        applied.add(op)
        redoStack.clear()
        if (savedAt > applied.size - 1) savedAt = -1
        // No cap: the writer replays this whole list onto the pristine file, so dropping the
        // oldest op would silently drop it from the saved workbook too.
        publish()
    }

    private fun publish() {
        _state.value = _state.value.copy(
            version = _state.value.version + 1,
            canUndo = applied.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            dirty = applied.size != savedAt
        )
    }

    fun undo() {
        val wb = _state.value.workbook ?: return
        val op = applied.removeLastOrNull() ?: return
        XlsxEdits.revert(wb, op)
        redoStack.add(op)
        publish()
    }

    fun redo() {
        val wb = _state.value.workbook ?: return
        val op = redoStack.removeLastOrNull() ?: return
        XlsxEdits.apply(wb, op)
        applied.add(op)
        publish()
    }

    /** Commit typed text into a cell; empty text clears the value but keeps its formatting. */
    fun setCellText(sheet: Int, r: Int, c: Int, text: String) {
        val wb = _state.value.workbook ?: return
        val s = wb.sheets.getOrNull(sheet) ?: return
        val before = s.cell(r, c)
        val style = before?.style ?: 0
        val after = if (text.isEmpty()) (if (style != 0) CellData("", "", style) else null) else wb.parseInput(text, style)
        if (before == after) return
        if (before != null && after != null && wb.editText(before) == text && before.style == after.style) return
        push(EditOp.SetCells(sheet, listOf(CellChange(r, c, before, after))))
    }

    fun clearRange(sheet: Int, range: CellRange) {
        val wb = _state.value.workbook ?: return
        val s = wb.sheets.getOrNull(sheet) ?: return
        val changes = cellsIn(s, range, existingOnly = true).mapNotNull { (r, c) ->
            val before = s.cell(r, c) ?: return@mapNotNull null
            if (before.isBlank) null
            else CellChange(r, c, before, if (before.style != 0) CellData("", "", before.style) else null)
        }
        if (changes.isNotEmpty()) push(EditOp.SetCells(sheet, changes))
    }

    fun applyStyle(sheet: Int, range: CellRange, patch: StylePatch) {
        applyStylePer(sheet, range) { _, _ -> patch }
    }

    fun applyBorders(sheet: Int, range: CellRange, preset: BorderPreset, color: ColorSpec? = null) {
        val thin = BorderSide("thin", color)
        val thick = BorderSide("medium", color)
        val none = BorderSide(null)
        applyStylePer(sheet, range) { r, c ->
            val top = r == range.r1; val bottom = r == range.r2; val left = c == range.c1; val right = c == range.c2
            when (preset) {
                BorderPreset.ALL -> StylePatch(left = thin, right = thin, top = thin, bottom = thin)
                BorderPreset.NONE -> StylePatch(left = none, right = none, top = none, bottom = none)
                BorderPreset.OUTER -> StylePatch(left = if (left) thin else null, right = if (right) thin else null, top = if (top) thin else null, bottom = if (bottom) thin else null)
                BorderPreset.THICK_OUTER -> StylePatch(left = if (left) thick else null, right = if (right) thick else null, top = if (top) thick else null, bottom = if (bottom) thick else null)
                BorderPreset.INNER -> StylePatch(left = if (!left) thin else null, right = if (!right) thin else null, top = if (!top) thin else null, bottom = if (!bottom) thin else null)
                BorderPreset.TOP -> if (top) StylePatch(top = thin) else null
                BorderPreset.BOTTOM -> if (bottom) StylePatch(bottom = thin) else null
                BorderPreset.LEFT -> if (left) StylePatch(left = thin) else null
                BorderPreset.RIGHT -> if (right) StylePatch(right = thin) else null
            }
        }
    }

    private fun applyStylePer(sheet: Int, range: CellRange, patchFor: (Int, Int) -> StylePatch?) {
        val wb = _state.value.workbook ?: return
        val s = wb.sheets.getOrNull(sheet) ?: return
        val changes = ArrayList<CellChange>()
        for ((r, c) in cellsIn(s, range, existingOnly = false)) {
            val p = patchFor(r, c) ?: continue
            val before = s.cell(r, c)
            val base = before?.style ?: 0
            val style = wb.styles.derive(base, p)
            val after = (before ?: CellData("", "", 0)).copy(style = style)
            if (after != before) changes.add(CellChange(r, c, before, after))
        }
        if (changes.isNotEmpty()) push(EditOp.SetCells(sheet, changes))
    }

    /** Cells of [range], capped so a whole-column selection doesn't create a million cells. */
    private fun cellsIn(s: XlsxSheet, range: CellRange, existingOnly: Boolean): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        val rMax = minOf(range.r2, if (existingOnly || range.r2 - range.r1 > 2000) maxOf(s.maxRow, range.r1) else range.r2)
        val cMax = minOf(range.c2, if (existingOnly || range.c2 - range.c1 > 200) maxOf(s.maxCol, range.c1) else range.c2)
        for (r in range.r1..rMax) {
            if (existingOnly) {
                val row = s.rows[r] ?: continue
                for (c in row.cells.subMap(range.c1, true, cMax, true).keys) out.add(r to c)
            } else for (c in range.c1..cMax) out.add(r to c)
            if (out.size > 50_000) break
        }
        return out
    }

    fun insertLines(sheet: Int, axis: Axis, at: Int, count: Int) {
        if (count > 0) push(EditOp.Lines(sheet, axis, at, count))
    }

    fun deleteLines(sheet: Int, axis: Axis, at: Int, count: Int) {
        if (count > 0) push(EditOp.Lines(sheet, axis, at, -count))
    }

    fun setColWidth(sheet: Int, col: Int, widthChars: Float) {
        val s = _state.value.workbook?.sheets?.getOrNull(sheet) ?: return
        push(EditOp.ColWidth(sheet, col, s.cols[col]?.widthChars, widthChars))
    }

    // ── saving ─────────────────────────────────────────────────────────────────

    /** Writes the edited package to a cache file; null on failure. */
    private fun buildPatched(context: Context): File? {
        val st = _state.value
        val wb = st.workbook ?: return null
        val src = st.fileUri?.takeIf { it.scheme == "file" }?.path?.let(::File) ?: return null
        val dir = File(context.cacheDir, "sheets").apply { mkdirs() }
        val safe = st.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "sheet.xlsx" }
        val out = File(dir, "edited_${System.currentTimeMillis()}_$safe")
        return runCatching { XlsxWriter.patch(src, out, wb, applied.toList()); out }.getOrElse { out.delete(); null }
    }

    sealed class SaveResult {
        data object Saved : SaveResult()
        data object NeedsSaveAs : SaveResult()
        data class Failed(val message: String) : SaveResult()
    }

    /** Save over the opened document ([target] null) or into a new one from Save As. */
    fun save(context: Context, target: Uri?, onDone: (SaveResult) -> Unit) {
        val st = _state.value
        if (st.saving || !st.editable) return
        val dest = target ?: st.sourceUri ?: return onDone(SaveResult.NeedsSaveAs)
        _state.value = st.copy(saving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val patched = buildPatched(context) ?: return@withContext SaveResult.Failed("Couldn't write the spreadsheet.")
                try {
                    if (writeWithBackup(context, dest, patched)) SaveResult.Saved
                    else if (target == null) SaveResult.NeedsSaveAs else SaveResult.Failed("Couldn't save to that location.")
                } finally {
                    patched.delete()
                }
            }
            _state.value = _state.value.copy(saving = false)
            if (result is SaveResult.Saved) {
                savedAt = applied.size
                publish()
            }
            onDone(result)
        }
    }

    /**
     * Copy [file] over [uri], keeping a backup of what was there until the new content has landed
     * whole, and putting it back if the write fails midway (same approach as OpenDocument.droid).
     */
    private fun writeWithBackup(context: Context, uri: Uri, file: File): Boolean {
        val cr = context.contentResolver
        val backup = File(context.cacheDir, "sheets/backup_${System.currentTimeMillis()}")
        val hadBackup = runCatching { cr.openInputStream(uri)?.use { i -> backup.outputStream().use { i.copyTo(it) } } != null }.getOrDefault(false)
        return try {
            val out = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull()
                ?: runCatching { cr.openOutputStream(uri, "w") }.getOrNull()
                ?: return false
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            backup.delete()
            true
        } catch (e: Throwable) {
            if (hadBackup) runCatching { cr.openOutputStream(uri, "wt")?.use { o -> backup.inputStream().use { it.copyTo(o) } } }
                .onSuccess { backup.delete() }
            // A failed restore leaves the backup in cache on purpose: it may be the last copy.
            false
        }
    }

    fun suggestedSaveAsName(): String {
        val n = _state.value.fileName.ifBlank { "Spreadsheet.xlsx" }
        val base = n.substringBeforeLast('.')
        return "$base (edited).xlsx"
    }

    /** Convert the (edited) spreadsheet to a PDF and hand back its URI. */
    fun exportToPdf(context: Context, onDone: (Uri?) -> Unit) {
        val uri = _state.value.fileUri ?: return onDone(null)
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val source = if (applied.isNotEmpty()) buildPatched(context)?.let { Uri.fromFile(it) } ?: uri else uri
                    com.chethan616.clearpdf.utils.UniversalDocumentConverter.convertToPdf(context, source)
                }.getOrNull()
            }
            onDone(out)
        }
    }

    /** The file to share: the edited copy when there are edits, otherwise the mirror. */
    fun shareableUri(context: Context, onDone: (Uri?) -> Unit) {
        val uri = _state.value.fileUri ?: return onDone(null)
        if (applied.isEmpty()) return onDone(uri)
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { buildPatched(context)?.let { Uri.fromFile(it) } }
            onDone(out ?: uri)
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

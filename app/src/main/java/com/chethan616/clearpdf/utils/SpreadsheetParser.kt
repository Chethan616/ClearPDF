package com.chethan616.clearpdf.utils

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parses .xlsx / .xls spreadsheets into structured, cell-aligned rows per sheet — for the
 * interactive spreadsheet viewer (as opposed to [UniversalDocumentConverter], which flattens to a
 * static PDF). Cells are placed at their true column index (from the r="C5" ref) so omitted/empty
 * cells don't shift data, and inline strings + shared strings are both handled. Self-contained.
 */
object SpreadsheetParser {

    /** Widest row this parser will materialise. See the note at the `"c"` end-tag. */
    private const val MaxColumns = 1024

    data class Sheet(val name: String, val rows: List<List<String>>) {
        /** Widest row → number of columns to render. */
        val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
    }

    /**
     * Never throws. Every failure — no such file, a revoked URI permission, a corrupt archive, a
     * workbook too large for the heap — comes back as an empty list, which the caller renders as
     * "Couldn't read this spreadsheet."
     *
     * The `runCatching` used to wrap only [parseXlsx]/[parseXls], leaving `openInputStream` and the
     * whole-file read outside it. Those are the two calls most likely to fail (a `SecurityException`
     * when a picked URI's grant has lapsed, an `OutOfMemoryError` on a big workbook), and because
     * the caller invokes this from `viewModelScope.launch`, anything escaping here took the process
     * down rather than showing the error state.
     */
    fun parse(context: Context, uri: Uri): List<Sheet> = runCatching {
        val name = queryName(context, uri).lowercase()
        if (name.endsWith(".xls")) {
            // POI's HSSF reader wants the file in hand; there is no streaming path for it.
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) emptyList() else parseXls(bytes)
        } else {
            context.contentResolver.openInputStream(uri)?.use { parseXlsx(it) } ?: emptyList()
        }
    }.getOrDefault(emptyList())

    // ── XLSX (Office Open XML) ───────────────────────────────────────────────────

    fun parseXlsx(bytes: ByteArray): List<Sheet> = bytes.inputStream().use { parseXlsx(it) }

    fun parseXlsx(source: InputStream): List<Sheet> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Only the four things below are ever read again. Keeping the rest was the single
                // biggest allocation in this parser: `xl/media/*` (embedded images, already the
                // bulk of many workbooks) and `xl/calcChain.xml` (one node per formula cell, often
                // larger than the sheets themselves) were being decompressed into the heap in full
                // and never touched. Skipping them is what keeps a mid-size workbook off the OOM
                // line, since every entry kept here stays reachable until parsing finishes.
                if (isNeeded(entry.name)) entries[entry.name] = zip.readBytes()
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val workbookSheets = parseWorkbookSheets(entries["xl/workbook.xml"])   // (name, rId) in tab order
        val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])            // rId → "worksheets/sheetN.xml"

        val ordered: List<Pair<String, ByteArray>> = if (workbookSheets.isNotEmpty()) {
            workbookSheets.mapNotNull { (name, rId) ->
                val target = rels[rId] ?: return@mapNotNull null
                val path = if (target.startsWith("/")) target.drop(1) else "xl/${target.removePrefix("/")}"
                entries[path]?.let { name to it }
            }
        } else {
            entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { Regex("sheet(\\d+)\\.xml").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                .map { (Regex("sheet(\\d+)").find(it)?.groupValues?.getOrNull(1)?.let { n -> "Sheet $n" } ?: "Sheet") to entries[it]!! }
        }

        return ordered.map { (name, xml) -> Sheet(name, parseWorksheetRows(xml.inputStream(), sharedStrings)) }
            .filter { it.rows.isNotEmpty() }
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): List<Pair<String, String>> {
        if (bytes == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        val parser = newParser(bytes.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val rId = parser.getAttributeValue(null, "r:id") ?: parser.getAttributeValue(null, "id") ?: ""
                out.add(name to rId)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val out = HashMap<String, String>()
        val parser = newParser(bytes.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id") ?: ""
                val target = parser.getAttributeValue(null, "Target") ?: ""
                if (id.isNotEmpty()) out[id] = target
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = newParser(stream)
        var inT = false
        val buf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") buf.clear() else if (parser.name == "t") inT = true
                XmlPullParser.END_TAG -> if (parser.name == "si") { strings.add(buf.toString()); inT = false } else if (parser.name == "t") inT = false
                XmlPullParser.TEXT -> if (inT) buf.append(parser.text)
            }
            event = parser.next()
        }
        return strings
    }

    private fun colIndexFromRef(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        var sawLetter = false
        for (ch in ref) {
            val up = ch.uppercaseChar()
            if (up in 'A'..'Z') { idx = idx * 26 + (up - 'A' + 1); sawLetter = true } else break
        }
        return if (sawLetter) idx - 1 else -1
    }

    private fun parseWorksheetRows(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = newParser(stream)
        var rowCells = sortedMapOf<Int, String>()
        var cellType = ""
        var cellCol = 0
        var nextAutoCol = 0
        var inVal = false
        val cellBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { rowCells = sortedMapOf(); nextAutoCol = 0 }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellCol = colIndexFromRef(parser.getAttributeValue(null, "r")).let { if (it >= 0) it else nextAutoCol }
                        inVal = false; cellBuf.clear()
                    }
                    "v", "t" -> inVal = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "row" -> if (rowCells.isNotEmpty()) {
                        val maxC = rowCells.lastKey()
                        rows.add((0..maxC).map { rowCells[it] ?: "" })
                    } else rows.add(emptyList())
                    "c" -> {
                        val raw = cellBuf.toString()
                        val value = if (cellType == "s") sharedStrings.getOrElse(raw.trim().toIntOrNull() ?: -1) { raw } else raw
                        // The column cap is what stops one malformed `r` ref from sizing the whole
                        // sheet: the row is materialised as a dense `0..maxKey` list, so a single
                        // cell claiming to be at XFD would allocate 16384 strings for every row in
                        // the file. Excel's own classic limit is 256; past this a phone grid is not
                        // a usable way to read the data anyway.
                        if (value.isNotEmpty() && cellCol in 0 until MaxColumns) rowCells[cellCol] = value
                        nextAutoCol = cellCol + 1
                        inVal = false
                    }
                    "v", "t" -> inVal = false
                }
                XmlPullParser.TEXT -> if (inVal) cellBuf.append(parser.text)
            }
            event = parser.next()
            if (rows.size > 20000) break
        }
        // Trim trailing fully-empty rows.
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.lastIndex)
        return rows
    }

    // ── Legacy XLS (POI) ─────────────────────────────────────────────────────────

    private fun parseXls(bytes: ByteArray): List<Sheet> {
        HSSFWorkbook(bytes.inputStream()).use { wb ->
            return (0 until wb.numberOfSheets).map { si ->
                val sheet = wb.getSheetAt(si)
                val rows = sheet.map { row ->
                    val lastCol = row.lastCellNum.toInt().coerceAtLeast(0)
                    (0 until lastCol).map { c -> row.getCell(c)?.toString()?.trim() ?: "" }
                }
                Sheet(wb.getSheetName(si) ?: "Sheet ${si + 1}", rows)
            }.filter { it.rows.isNotEmpty() }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /**
     * The zip entries this parser actually reads. Matched by suffix rather than by exact path
     * because a few producers write the part names with a leading slash or a non-`xl/` package
     * root, and a workbook that opens fine in Excel should not come up blank here.
     */
    private fun isNeeded(entryName: String): Boolean {
        val n = entryName.removePrefix("/")
        return n.endsWith("workbook.xml") ||
            n.endsWith("workbook.xml.rels") ||
            n.endsWith("sharedStrings.xml") ||
            (n.contains("worksheets/") && n.endsWith(".xml"))
    }

    private fun newParser(stream: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(stream, "UTF-8")
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "sheet"
}

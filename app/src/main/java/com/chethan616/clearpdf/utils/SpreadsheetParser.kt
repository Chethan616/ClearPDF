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
 *
 * Two things it deliberately reads beyond the raw cell values, because leaving them out made the
 * viewer disagree with Excel on the same file:
 *  - `xl/styles.xml`, so a date cell renders as a date instead of its serial number (see
 *    [ExcelCellFormat]);
 *  - the `hidden` flags on `<cols>`/`<row>`, so a column the author hid stays hidden here too.
 *    Sheets routinely carry helper columns that are hidden on purpose; showing them made the app
 *    look like it was inventing data that "isn't in the file".
 */
object SpreadsheetParser {

    /** Widest row this parser will materialise. See the note at the `"c"` end-tag. */
    private const val MaxColumns = 1024

    /**
     * @param columnLabels the spreadsheet letter for each rendered column. Not simply `A, B, C…`:
     *   hidden columns are dropped from [rows], so a sheet that hides H renders `… G, I …` exactly
     *   as Excel's own header does.
     * @param rowNumbers the real 1-based sheet row number for each rendered row, for the same
     *   reason — and so the viewer's row gutter agrees with the reference the user reads on desktop.
     */
    data class Sheet(
        val name: String,
        val rows: List<List<String>>,
        val columnLabels: List<String> = emptyList(),
        val rowNumbers: List<Int> = emptyList()
    ) {
        /** Widest row → number of columns to render. */
        val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

        /** Header letter for a rendered column, falling back to positional letters. */
        fun labelAt(index: Int): String = columnLabels.getOrNull(index) ?: colLetter(index)

        /** Sheet row number for a rendered row, falling back to positional numbering. */
        fun rowNumberAt(index: Int): Int = rowNumbers.getOrNull(index) ?: (index + 1)
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

    /** 0→A, 25→Z, 26→AA … spreadsheet column labels. */
    fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) { sb.insert(0, 'A' + (i % 26)); i = i / 26 - 1 }
        return sb.toString()
    }

    // ── XLSX (Office Open XML) ───────────────────────────────────────────────────

    fun parseXlsx(bytes: ByteArray): List<Sheet> = bytes.inputStream().use { parseXlsx(it) }

    fun parseXlsx(source: InputStream): List<Sheet> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Only the parts below are ever read again. Keeping the rest was the single biggest
                // allocation in this parser: `xl/media/*` (embedded images, already the bulk of many
                // workbooks) and `xl/calcChain.xml` (one node per formula cell, often larger than
                // the sheets themselves) were being decompressed into the heap in full and never
                // touched. Skipping them is what keeps a mid-size workbook off the OOM line, since
                // every entry kept here stays reachable until parsing finishes.
                if (isNeeded(entry.name)) entries[entry.name] = zip.readBytes()
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val formatCodes = parseStyles(entries["xl/styles.xml"])                 // cellXfs index → format code
        val workbookSheets = parseWorkbookSheets(entries["xl/workbook.xml"])    // (name, rId) in tab order
        val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])             // rId → "worksheets/sheetN.xml"

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

        return ordered.map { (name, xml) ->
            buildSheet(name, parseWorksheet(xml.inputStream(), sharedStrings, formatCodes))
        }.filter { it.rows.isNotEmpty() }
    }

    /** Raw worksheet contents, before hidden columns are folded out. */
    private class RawSheet {
        val rows = mutableListOf<List<String>>()
        val rowNumbers = mutableListOf<Int>()
        val hiddenCols = HashSet<Int>()
    }

    /**
     * Drops the columns the author hid and derives the header letters / row numbers that survive.
     * The labels come from the *original* indices, which is the whole point: after removing H, the
     * remaining headers must still read G then I, or the viewer silently renames the user's columns.
     */
    private fun buildSheet(name: String, raw: RawSheet): Sheet {
        val width = raw.rows.maxOfOrNull { it.size } ?: 0
        val visible = (0 until width).filter { it !in raw.hiddenCols }
        // A sheet with everything hidden is almost certainly a file we misread; showing it beats
        // showing an empty grid.
        if (visible.isEmpty() || visible.size == width) {
            return Sheet(name, raw.rows, (0 until width).map { colLetter(it) }, raw.rowNumbers)
        }
        val rows = raw.rows.map { row -> visible.map { row.getOrElse(it) { "" } } }
        return Sheet(name, rows, visible.map { colLetter(it) }, raw.rowNumbers)
    }

    /**
     * `xl/styles.xml` → format code per `cellXfs` index, which is what a cell's `s="7"` points at.
     * Custom codes live in `<numFmts>`; everything else is a built-in id resolved by
     * [ExcelCellFormat]. Returns empty when there is no styles part, in which case values render
     * exactly as they are stored.
     */
    private fun parseStyles(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val custom = HashMap<Int, String>()
        val numFmtIds = mutableListOf<Int>()
        return runCatching {
            val parser = newParser(bytes.inputStream())
            var inCellXfs = false
            // `<dxfs>` (conditional-formatting overrides) carries its own `<numFmt>` children with
            // ids that can collide with the workbook's real ones. Only the top-level `<numFmts>`
            // block defines what a cell's `numFmtId` means.
            var inDxfs = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "dxfs" -> inDxfs = true
                        "numFmt" -> if (!inDxfs) {
                            val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                            val code = parser.getAttributeValue(null, "formatCode")
                            if (id != null && code != null) custom[id] = code
                        }
                        // `cellStyleXfs` has the same `<xf>` children and comes first; only the
                        // `cellXfs` block is indexed by a cell's `s` attribute.
                        "cellXfs" -> inCellXfs = true
                        "xf" -> if (inCellXfs) {
                            numFmtIds.add(parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "cellXfs" -> inCellXfs = false
                        "dxfs" -> inDxfs = false
                    }
                }
                event = parser.next()
            }
            ExcelCellFormat.formatCodesFor(numFmtIds, custom)
        }.getOrDefault(emptyList())
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

    private fun parseWorksheet(
        stream: InputStream,
        sharedStrings: List<String>,
        formatCodes: List<String>
    ): RawSheet {
        val out = RawSheet()
        val parser = newParser(stream)
        var rowCells = sortedMapOf<Int, String>()
        var cellType = ""
        var cellStyle = -1
        var cellCol = 0
        var nextAutoCol = 0
        var rowNumber = 0
        var rowHidden = false
        var inVal = false
        val cellBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "col" -> {
                        // `hidden="1"` on a `<col>` spans min..max, so one entry can hide a range.
                        if (parser.getAttributeValue(null, "hidden") == "1") {
                            val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 0
                            val max = parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                            for (c in min..max.coerceAtMost(MaxColumns)) if (c >= 1) out.hiddenCols.add(c - 1)
                        }
                    }
                    "row" -> {
                        rowCells = sortedMapOf()
                        nextAutoCol = 0
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (out.rows.size + 1)
                        rowHidden = parser.getAttributeValue(null, "hidden") == "1"
                    }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        cellCol = colIndexFromRef(parser.getAttributeValue(null, "r")).let { if (it >= 0) it else nextAutoCol }
                        inVal = false; cellBuf.clear()
                    }
                    "v", "t" -> inVal = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "row" -> if (!rowHidden) {
                        if (rowCells.isNotEmpty()) {
                            val maxC = rowCells.lastKey()
                            out.rows.add((0..maxC).map { rowCells[it] ?: "" })
                        } else out.rows.add(emptyList())
                        out.rowNumbers.add(rowNumber)
                    }
                    "c" -> {
                        val raw = cellBuf.toString()
                        val value = when (cellType) {
                            "s" -> sharedStrings.getOrElse(raw.trim().toIntOrNull() ?: -1) { raw }
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "str", "inlineStr", "e" -> raw
                            // Numeric (the default, `t` absent) — this is where a date lives, and
                            // where reading the style is the difference between "02-09-2026" and
                            // the bare serial "46267".
                            else -> ExcelCellFormat.apply(raw, formatCodes.getOrNull(cellStyle))
                        }
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
            if (out.rows.size > 20000) break
        }
        // Trim trailing fully-empty rows.
        while (out.rows.isNotEmpty() && out.rows.last().all { it.isBlank() }) {
            out.rows.removeAt(out.rows.lastIndex)
            if (out.rowNumbers.isNotEmpty()) out.rowNumbers.removeAt(out.rowNumbers.lastIndex)
        }
        return out
    }

    // ── Legacy XLS (POI) ─────────────────────────────────────────────────────────

    private fun parseXls(bytes: ByteArray): List<Sheet> {
        HSSFWorkbook(bytes.inputStream()).use { wb ->
            return (0 until wb.numberOfSheets).map { si ->
                val sheet = wb.getSheetAt(si)
                val width = sheet.maxOfOrNull { it.lastCellNum.toInt().coerceAtLeast(0) } ?: 0
                val visible = (0 until width).filter { !sheet.isColumnHidden(it) }
                    .ifEmpty { (0 until width).toList() }
                val kept = sheet.filter { !it.zeroHeight }
                val rows = kept.map { row -> visible.map { c -> row.getCell(c)?.toString()?.trim() ?: "" } }
                Sheet(
                    name = wb.getSheetName(si) ?: "Sheet ${si + 1}",
                    rows = rows,
                    columnLabels = visible.map { colLetter(it) },
                    rowNumbers = kept.map { it.rowNum + 1 }
                )
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
            n.endsWith("styles.xml") ||
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

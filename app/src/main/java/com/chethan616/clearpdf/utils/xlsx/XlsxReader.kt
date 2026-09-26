package com.chethan616.clearpdf.utils.xlsx

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads an .xlsx package into an [XlsxWorkbook]: values, formulas, styles (fonts, fills, borders,
 * alignment, number formats) with theme/tint/indexed colours resolved, rich-text runs, merges,
 * column widths, row heights, frozen panes, tab colours and list data-validations.
 *
 * Only the parts it needs are inflated — media and calcChain are skipped, which is what keeps a
 * mid-size workbook off the OOM line.
 */
object XlsxReader {

    /** Swappable so plain-JVM unit tests can supply kxml2; Android uses the platform parser. */
    @Volatile
    var parserFactory: () -> XmlPullParser = { android.util.Xml.newPullParser() }

    const val MaxRows = 20_000
    const val MaxColumns = 1024

    fun read(bytes: ByteArray): XlsxWorkbook = bytes.inputStream().use { read(it) }

    fun read(source: InputStream): XlsxWorkbook {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(source).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (isNeeded(n)) entries[n] = zip.readBytes()
            }
        }
        val theme = entries.entries.firstOrNull { it.key.startsWith("xl/theme/") && it.key.endsWith(".xml") }
            ?.let { parseTheme(it.value) } ?: XlsxColors.DefaultTheme
        val styles = entries["xl/styles.xml"]?.let { runCatching { parseStyles(it, theme) }.getOrNull() }
            ?: StyleTable.empty(theme)
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val wbSheets = entries["xl/workbook.xml"]?.let { parseWorkbookSheets(it) } ?: emptyList()
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()

        val ordered: List<Triple<String, String, Boolean>> = if (wbSheets.isNotEmpty()) {
            wbSheets.mapNotNull { (name, rId, hidden) ->
                val target = rels[rId] ?: return@mapNotNull null
                val path = if (target.startsWith("/")) target.drop(1) else "xl/" + target.removePrefix("./")
                if (entries.containsKey(path)) Triple(name, path, hidden) else null
            }
        } else {
            entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { Regex("sheet(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                .map { p -> Triple("Sheet " + (Regex("sheet(\\d+)").find(p)?.groupValues?.get(1) ?: ""), p, false) }
        }
        val sheets = ordered.map { (name, path, hidden) ->
            XlsxSheet(name, path).also { s ->
                s.hidden = hidden
                runCatching { parseWorksheet(entries[path]!!, s) }
            }
        }
        return XlsxWorkbook(sheets, styles, shared, editable = entries.containsKey("xl/workbook.xml"))
    }

    private fun isNeeded(n: String): Boolean =
        n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" ||
            n == "xl/styles.xml" || (n.startsWith("xl/theme/") && n.endsWith(".xml")) ||
            (n.startsWith("xl/worksheets/") && n.endsWith(".xml") && !n.contains("_rels"))

    private fun parser(bytes: ByteArray): XmlPullParser = parserFactory().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(bytes.inputStream(), "UTF-8")
    }

    private val XmlPullParser.local: String get() = name.substringAfter(':')

    private fun XmlPullParser.attr(n: String): String? {
        for (i in 0 until attributeCount) {
            val an = getAttributeName(i)
            if (an == n || an.substringAfter(':') == n && !an.startsWith("xmlns")) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlPullParser.attrMap(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        for (i in 0 until attributeCount) m[getAttributeName(i)] = getAttributeValue(i)
        return m
    }

    /** Reads the element the parser is on (START_TAG) into an [XmlEl], leaving it on its END_TAG. */
    private fun readEl(p: XmlPullParser): XmlEl {
        val el = XmlEl(p.local, p.attrMap())
        val depth = p.depth
        var sb: StringBuilder? = null
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> el.children.add(readEl(p))
                XmlPullParser.TEXT -> (sb ?: StringBuilder().also { sb = it }).append(p.text)
                XmlPullParser.END_TAG -> if (p.depth == depth) break
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        // Whitespace is content in a leaf (`<t xml:space="preserve"> </t>`), indentation elsewhere.
        sb?.toString()?.takeIf { el.children.isEmpty() || it.isNotBlank() }?.let { el.text = it }
        return el
    }

    // ── theme ──────────────────────────────────────────────────────────────────

    fun parseTheme(bytes: ByteArray): IntArray = runCatching {
        val p = parser(bytes)
        val byName = HashMap<String, Int>()
        var inScheme = false
        var current: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (val n = p.local) {
                    "clrScheme" -> inScheme = true
                    "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink" ->
                        if (inScheme) current = n
                    "srgbClr" -> if (inScheme && current != null) {
                        p.attr("val")?.let { XlsxColors.parseHex(it) }?.let { byName[current!!] = it }; current = null
                    }
                    "sysClr" -> if (inScheme && current != null) {
                        (p.attr("lastClr") ?: if (p.attr("val") == "window") "FFFFFF" else "000000")
                            .let { XlsxColors.parseHex(it) }?.let { byName[current!!] = it }; current = null
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.local == "clrScheme") break
            ev = p.next()
        }
        val order = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
        IntArray(order.size) { i -> byName[order[i]] ?: XlsxColors.DefaultTheme[i] }
    }.getOrDefault(XlsxColors.DefaultTheme)

    // ── styles ─────────────────────────────────────────────────────────────────

    fun parseStyles(bytes: ByteArray, theme: IntArray): StyleTable {
        val p = parser(bytes)
        val fonts = mutableListOf<XmlEl>()
        val fills = mutableListOf<XmlEl>()
        val borders = mutableListOf<XmlEl>()
        val xfs = mutableListOf<XmlEl>()
        val numFmts = HashMap<Int, String>()
        var indexed: IntArray? = null
        var styleXfs = 0
        var section = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = p.local
                if (p.depth == 2) section = n
                when {
                    section == "numFmts" && n == "numFmt" -> {
                        val id = p.attr("numFmtId")?.toIntOrNull()
                        val code = p.attr("formatCode")
                        if (id != null && code != null) numFmts[id] = code
                    }
                    section == "fonts" && n == "font" && p.depth == 3 -> fonts.add(readEl(p))
                    section == "fills" && n == "fill" && p.depth == 3 -> fills.add(readEl(p))
                    section == "borders" && n == "border" && p.depth == 3 -> borders.add(readEl(p))
                    section == "cellXfs" && n == "xf" && p.depth == 3 -> xfs.add(readEl(p))
                    section == "cellStyleXfs" && n == "xf" && p.depth == 3 -> styleXfs++
                    section == "colors" && n == "indexedColors" -> {
                        val el = readEl(p)
                        indexed = el.children.filter { it.name == "rgbColor" }
                            .map { XlsxColors.parseHex(it.attrs["rgb"] ?: "") ?: 0xFF000000.toInt() }.toIntArray()
                    }
                }
            }
            ev = p.next()
        }
        return StyleTable(fonts, fills, borders, xfs, numFmts, theme, indexed, styleXfs, true)
    }

    // ── shared strings ─────────────────────────────────────────────────────────

    /**
     * Each `<si>` → [RichText]. Phonetic guides (`<rPh>`) carry their own `<t>` and are *not* part
     * of the displayed string — the old parser appended them, so Japanese sheets showed the kana
     * reading glued onto every value.
     */
    fun parseSharedStrings(bytes: ByteArray): List<RichText> {
        val out = ArrayList<RichText>()
        val p = parser(bytes)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.local == "si") out.add(readStringItem(p))
            ev = p.next()
        }
        return out
    }

    /** Parser on `<si>` or `<is>`: returns its text and runs; leaves the parser on the END_TAG. */
    private fun readStringItem(p: XmlPullParser): RichText {
        val el = readEl(p)
        val sb = StringBuilder()
        val runs = ArrayList<TextRun>()
        var sawRun = false
        for (c in el.children) {
            when (c.name) {
                "t" -> sb.append(c.text ?: "")
                "r" -> {
                    sawRun = true
                    val start = sb.length
                    for (t in c.children) if (t.name == "t") sb.append(t.text ?: "")
                    runs.add(TextRun(start, sb.length, c.child("rPr")))
                }
                // "rPh" / "phoneticPr" deliberately ignored.
            }
        }
        return RichText(sb.toString(), if (sawRun) runs else null)
    }

    // ── workbook ───────────────────────────────────────────────────────────────

    private fun parseWorkbookSheets(bytes: ByteArray): List<Triple<String, String, Boolean>> {
        val out = mutableListOf<Triple<String, String, Boolean>>()
        val p = parser(bytes)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.local == "sheet") {
                val name = p.attr("name") ?: "Sheet"
                var rId = ""
                for (i in 0 until p.attributeCount) if (p.getAttributeName(i).endsWith(":id")) rId = p.getAttributeValue(i)
                val state = p.attr("state")
                out.add(Triple(name, rId, state == "hidden" || state == "veryHidden"))
            }
            ev = p.next()
        }
        return out
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        val p = parser(bytes)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.local == "Relationship") {
                val id = p.attr("Id") ?: ""
                if (id.isNotEmpty()) out[id] = p.attr("Target") ?: ""
            }
            ev = p.next()
        }
        return out
    }

    // ── worksheet ──────────────────────────────────────────────────────────────

    fun parseWorksheet(bytes: ByteArray, sheet: XlsxSheet) {
        val p = parser(bytes)
        var rowIdx = -1
        var row: RowData? = null
        var nextCol = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.local) {
                    "sheetPr" -> Unit
                    "tabColor" -> sheet.tabColor = ColorSpec.fromAttrs(p.attrMap())
                    "sheetView" -> if (p.attr("showGridLines") == "0" || p.attr("showGridLines") == "false") sheet.showGridLines = false
                    "pane" -> {
                        val state = p.attr("state")
                        if (state == "frozen" || state == "frozenSplit") {
                            sheet.frozenCols = p.attr("xSplit")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 20) ?: 0
                            sheet.frozenRows = p.attr("ySplit")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 50) ?: 0
                        }
                    }
                    "sheetFormatPr" -> {
                        p.attr("defaultRowHeight")?.toFloatOrNull()?.let { sheet.defaultRowHeightPt = it }
                        val dcw = p.attr("defaultColWidth")?.toFloatOrNull()
                        val base = p.attr("baseColWidth")?.toFloatOrNull()
                        sheet.defaultColWidthChars = dcw ?: base?.let { it + 0.43f } ?: 8.43f
                    }
                    "col" -> {
                        val min = p.attr("min")?.toIntOrNull() ?: 1
                        val max = (p.attr("max")?.toIntOrNull() ?: min).coerceAtMost(MaxColumns)
                        val info = ColumnInfo(p.attr("width")?.toFloatOrNull(), p.attr("hidden").isTrue(), p.attr("style")?.toIntOrNull() ?: -1)
                        for (c in min..max) if (c >= 1) sheet.cols[c - 1] = info
                    }
                    "row" -> {
                        rowIdx = (p.attr("r")?.toIntOrNull() ?: (rowIdx + 2)) - 1
                        if (rowIdx >= MaxRows) { sheet.truncated = true; return }
                        val rd = RowData(
                            heightPt = if (p.attr("customHeight").isTrue() || p.attr("ht") != null) p.attr("ht")?.toFloatOrNull() else null,
                            hidden = p.attr("hidden").isTrue(),
                            style = if (p.attr("customFormat").isTrue()) p.attr("s")?.toIntOrNull() ?: -1 else -1
                        )
                        sheet.rows[rowIdx] = rd
                        row = rd
                        nextCol = 0
                    }
                    "c" -> {
                        val rd = row
                        if (rd != null) {
                            val ref = p.attr("r")
                            val col = ref?.let { XlsxRefs.parseCell(it)?.second } ?: nextCol
                            nextCol = col + 1
                            val cell = readCell(p)
                            if (col < MaxColumns && cell != null) rd.cells[col] = cell
                        }
                    }
                    "mergeCell" -> p.attr("ref")?.let { XlsxRefs.parseRange(it) }?.let { sheet.merges.add(it) }
                    "dataValidation" -> readValidation(p)?.let { sheet.validations.add(it) }
                }
            }
            ev = p.next()
        }
    }

    /** Parser on `<c>`; returns the cell (or null when it carries nothing but position). */
    private fun readCell(p: XmlPullParser): CellData? {
        val type = p.attr("t") ?: ""
        val style = p.attr("s")?.toIntOrNull() ?: 0
        val el = readEl(p)
        val v = el.child("v")?.text ?: ""
        val f = el.child("f")?.let { FormulaSpec(it.text ?: "", it.attrs["t"], it.attrs["si"], it.attrs["ref"]) }
        var rich: RichText? = null
        var raw = v
        if (type == "inlineStr") {
            el.child("is")?.let { isEl ->
                val sb = StringBuilder()
                val runs = ArrayList<TextRun>()
                var sawRun = false
                for (c in isEl.children) when (c.name) {
                    "t" -> sb.append(c.text ?: "")
                    "r" -> {
                        sawRun = true
                        val s = sb.length
                        for (t in c.children) if (t.name == "t") sb.append(t.text ?: "")
                        runs.add(TextRun(s, sb.length, c.child("rPr")))
                    }
                }
                raw = sb.toString()
                rich = RichText(raw, if (sawRun) runs else null)
            }
        }
        if (raw.isEmpty() && f == null && style == 0) return null
        return CellData(type, raw, style, f, rich)
    }

    private fun readValidation(p: XmlPullParser): DataValidation? {
        val type = p.attr("type")
        val sqrefAttr = p.attr("sqref")
        val allowBlank = p.attr("allowBlank").isTrue()
        val showDropDown = p.attr("showDropDown").isTrue()   // NB: "1" means *hide* the arrow
        val strict = p.attr("showErrorMessage").isTrue() && (p.attr("errorStyle") ?: "stop") == "stop"
        val prompt = p.attr("prompt")
        val el = readEl(p)
        // x14 flavour: <x14:formula1><xm:f>…</xm:f></x14:formula1> and <xm:sqref>…</xm:sqref>
        val f1 = el.child("formula1")?.let { it.text ?: it.child("f")?.text }
        val sqref = sqrefAttr ?: el.child("sqref")?.text ?: return null
        val ranges = XlsxRefs.parseSqref(sqref)
        if (ranges.isEmpty()) return null
        return DataValidation(
            ranges, if (type == "list") ValidationKind.LIST else ValidationKind.OTHER,
            f1?.trim(), allowBlank, showDropDown, strict, prompt
        )
    }
}

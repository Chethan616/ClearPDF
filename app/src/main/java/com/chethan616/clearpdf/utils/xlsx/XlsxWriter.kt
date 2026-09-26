package com.chethan616.clearpdf.utils.xlsx

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Saves edits by patching the original package instead of regenerating it.
 *
 * Every zip entry the edits don't touch is copied through unchanged — drawings, charts, pivot caches,
 * macros, custom XML, anything this app has never heard of. Of the parts that do change, only the
 * affected elements are rewritten: a cell whose value didn't change keeps its exact XML (shared
 * formulas, rich-value metadata and all); styles are *appended* so no existing `s` index moves.
 *
 * Known limits (documented, not silent): shared-formula *masters* that get overwritten orphan
 * their followers; table (`xl/tables`) and pivot ranges are not shifted by row/column inserts.
 */
object XlsxWriter {

    fun patch(source: File, dest: File, wb: XlsxWorkbook, ops: List<EditOp>) {
        val parts = source.inputStream().buffered().use { collectParts(it, wb, ops) }
        source.inputStream().buffered().use { input ->
            dest.outputStream().buffered().use { out -> rewrite(input, out, parts) }
        }
    }

    fun patch(source: ByteArray, wb: XlsxWorkbook, ops: List<EditOp>): ByteArray {
        val parts = collectParts(source.inputStream(), wb, ops)
        val bos = ByteArrayOutputStream(source.size + 4096)
        rewrite(source.inputStream(), bos, parts)
        return bos.toByteArray()
    }

    /** name → new bytes, or null to drop the entry; plus entries to add at the end. */
    private class Parts(val replace: Map<String, ByteArray?>, val add: Map<String, ByteArray>)

    private fun collectParts(input: InputStream, wb: XlsxWorkbook, ops: List<EditOp>): Parts {
        val structural = ops.any { it is EditOp.Lines }
        val valueEdits = ops.any { op -> op is EditOp.SetCells && op.changes.any { !it.styleOnly } } || structural
        val touchedSheets = ops.mapNotNull { wb.sheets.getOrNull(it.sheet)?.partPath }.toSet()
        val wanted: (String) -> Boolean = { n ->
            n in touchedSheets || n == "xl/styles.xml" || n == "xl/workbook.xml" || n == "[Content_Types].xml" ||
                n == "xl/_rels/workbook.xml.rels" || n == "xl/calcChain.xml" ||
                (structural && n.startsWith("xl/worksheets/") && n.endsWith(".xml") && !n.contains("_rels"))
        }
        val texts = HashMap<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (wanted(n)) texts[n] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        val sheets = HashMap<String, SheetXml>()
        fun sheetXml(path: String): SheetXml? = sheets[path] ?: texts[path]?.let { SheetXml(it).also { s -> sheets[path] = s } }

        for (op in ops) {
            val sheet = wb.sheets.getOrNull(op.sheet) ?: continue
            val sx = sheetXml(sheet.partPath) ?: continue
            when (op) {
                is EditOp.SetCells -> for (ch in op.changes) sx.setCell(ch)
                is EditOp.ColWidth -> sx.setColWidth(op.col, op.after)
                is EditOp.Lines -> {
                    sx.shiftLines(sheet.name, op.axis, op.at, op.count)
                    // Other sheets' formulas that name this one move too.
                    for (other in wb.sheets) {
                        if (other === sheet) continue
                        val t = texts[other.partPath] ?: continue
                        if (!t.contains(other.name.let { sheet.name } + "!") && !t.contains("'" + sheet.name.replace("'", "''") + "'!")) continue
                        sheetXml(other.partPath)?.shiftForeignFormulas(sheet.name, op.axis, op.at, op.count)
                    }
                }
            }
        }

        val replace = HashMap<String, ByteArray?>()
        val add = HashMap<String, ByteArray>()
        for ((path, sx) in sheets) if (sx.dirty) replace[path] = sx.serialize().toByteArray(Charsets.UTF_8)

        // Styles: append whatever the edits derived.
        val st = wb.styles
        val stylesGrew = st.cellXfs.size > st.origXfs || st.numFmts.keys.any { it !in st.origNumFmtIds }
        var contentTypes = texts["[Content_Types].xml"]
        var wbRels = texts["xl/_rels/workbook.xml.rels"]
        if (stylesGrew) {
            val existing = texts["xl/styles.xml"]
            val patched = existing?.let { StylesXml.append(it, st) }
            if (patched != null) {
                replace["xl/styles.xml"] = patched.toByteArray(Charsets.UTF_8)
            } else {
                val full = StylesXml.full(st).toByteArray(Charsets.UTF_8)
                if (existing != null) replace["xl/styles.xml"] = full else {
                    add["xl/styles.xml"] = full
                    contentTypes = contentTypes?.let {
                        if (it.contains("/xl/styles.xml")) it else it.replace(
                            "</Types>",
                            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>"
                        )
                    }
                    wbRels = wbRels?.let {
                        if (it.contains("styles.xml")) it else it.replace(
                            "</Relationships>",
                            "<Relationship Id=\"rIdClearPdfStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"
                        )
                    }
                }
            }
        }

        if (valueEdits) {
            // calcChain lists formula cells; once any cell may have changed it is stale, and a stale
            // chain is one of the few things that makes Excel "repair" a file. Excel rebuilds it.
            if (texts.containsKey("xl/calcChain.xml")) {
                replace["xl/calcChain.xml"] = null
                contentTypes = contentTypes?.replace(Regex("<Override[^>]*PartName=\"/xl/calcChain\\.xml\"[^>]*/>"), "")
                wbRels = wbRels?.replace(Regex("<Relationship[^>]*Target=\"[^\"]*calcChain\\.xml\"[^>]*/>"), "")
            }
            texts["xl/workbook.xml"]?.let { w ->
                var out = forceFullCalc(w)
                if (structural) {
                    for (op in ops.filterIsInstance<EditOp.Lines>()) {
                        val name = wb.sheets.getOrNull(op.sheet)?.name ?: continue
                        out = shiftDefinedNames(out, name, op.axis, op.at, op.count)
                    }
                }
                replace["xl/workbook.xml"] = out.toByteArray(Charsets.UTF_8)
            }
        }
        if (contentTypes != null && contentTypes != texts["[Content_Types].xml"]) replace["[Content_Types].xml"] = contentTypes.toByteArray(Charsets.UTF_8)
        if (wbRels != null && wbRels != texts["xl/_rels/workbook.xml.rels"]) replace["xl/_rels/workbook.xml.rels"] = wbRels.toByteArray(Charsets.UTF_8)
        return Parts(replace, add)
    }

    private fun rewrite(input: InputStream, output: OutputStream, parts: Parts) {
        ZipInputStream(input).use { zin ->
            val zout = ZipOutputStream(output)
            val buf = ByteArray(64 * 1024)
            while (true) {
                val e = zin.nextEntry ?: break
                val n = e.name.removePrefix("/")
                val out = ZipEntry(e.name).apply { if (e.time != -1L) time = e.time }
                if (parts.replace.containsKey(n)) {
                    val bytes = parts.replace[n] ?: continue      // null → dropped
                    zout.putNextEntry(out)
                    zout.write(bytes)
                } else {
                    zout.putNextEntry(out)
                    while (true) {
                        val k = zin.read(buf)
                        if (k <= 0) break
                        zout.write(buf, 0, k)
                    }
                }
                zout.closeEntry()
            }
            for ((n, bytes) in parts.add) {
                zout.putNextEntry(ZipEntry(n))
                zout.write(bytes)
                zout.closeEntry()
            }
            zout.finish()
            zout.flush()
        }
    }

    // ── workbook.xml ───────────────────────────────────────────────────────────

    internal fun forceFullCalc(xml: String): String {
        val m = Regex("<((?:\\w+:)?)calcPr\\b([^>]*?)(/?)>").find(xml)
        if (m != null) {
            val attrs = m.groupValues[2]
            val newAttrs = if (attrs.contains("fullCalcOnLoad=")) attrs.replace(Regex("fullCalcOnLoad=\"[^\"]*\""), "fullCalcOnLoad=\"1\"")
            else "$attrs fullCalcOnLoad=\"1\""
            return xml.replaceRange(m.range, "<${m.groupValues[1]}calcPr$newAttrs${m.groupValues[3]}>")
        }
        val p = Regex("<((?:\\w+:)?)workbook\\b").find(xml)?.groupValues?.get(1) ?: ""
        for (after in listOf("definedNames", "externalReferences", "functionGroups", "sheets")) {
            val close = "</$p$after>"
            val i = xml.indexOf(close)
            if (i >= 0) return xml.substring(0, i + close.length) + "<${p}calcPr fullCalcOnLoad=\"1\"/>" + xml.substring(i + close.length)
        }
        return xml
    }

    private fun shiftDefinedNames(xml: String, sheet: String, axis: Axis, at: Int, count: Int): String =
        Regex("(<(?:\\w+:)?definedName\\b[^>]*>)(.*?)(</(?:\\w+:)?definedName>)", RegexOption.DOT_MATCHES_ALL).replace(xml) { m ->
            val text = unescape(m.groupValues[2])
            val shifted = XlsxRefs.shiftFormula(text, sheet, false, axis, at, count)
            m.groupValues[1] + XlsxRefs.escapeXml(shifted) + m.groupValues[3]
        }

    internal fun unescape(s: String): String {
        if (!s.contains('&')) return s
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|quot|apos|amp);").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "amp" -> "&"
                else -> {
                    val code = if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()
                    String(Character.toChars(code))
                }
            }
        }
    }
}

/** Attribute list parsed from a start tag, values kept in their escaped form. */
internal class Attrs(val map: LinkedHashMap<String, String> = LinkedHashMap()) {
    operator fun get(k: String) = map[k]
    operator fun set(k: String, v: String) { map[k] = v }
    fun remove(k: String) { map.remove(k) }
    fun render(sb: StringBuilder) { for ((k, v) in map) sb.append(' ').append(k).append("=\"").append(v).append('"') }

    companion object {
        private val AttrRe = Regex("([\\w:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
        fun parse(s: String): Attrs {
            val a = Attrs()
            for (m in AttrRe.findAll(s)) {
                val v = if (m.groups[2] != null) m.groupValues[2] else m.groupValues[3].replace("\"", "&quot;")
                a.map[m.groupValues[1]] = v
            }
            return a
        }
    }
}

/**
 * One worksheet's XML, split around `<sheetData>` so rows and cells can be edited individually while
 * everything else — views, conditional formats, page setup, extLst — stays as written.
 */
internal class SheetXml(text: String) {
    private val prefix: String
    private val head: String
    private val openTag: String
    private val closeTag: String
    private var tail: String
    private val rows = java.util.TreeMap<Int, RowX>()
    var dirty = false
        private set

    private class CellX(val attrs: Attrs, var inner: String?)
    private class RowX(val attrs: Attrs, val cells: java.util.TreeMap<Int, CellX>, var extra: String)

    init {
        val m = Regex("<((?:\\w+:)?)sheetData\\b([^>]*?)(/?)>").find(text)
            ?: throw IllegalArgumentException("worksheet without sheetData")
        prefix = m.groupValues[1]
        head = text.substring(0, m.range.first)
        openTag = "<${prefix}sheetData${m.groupValues[2]}>"
        closeTag = "</${prefix}sheetData>"
        val body: String
        if (m.groupValues[3] == "/") {
            body = ""
            tail = text.substring(m.range.last + 1)
        } else {
            val end = text.indexOf(closeTag, m.range.last + 1)
            require(end >= 0) { "unterminated sheetData" }
            body = text.substring(m.range.last + 1, end)
            tail = text.substring(end + closeTag.length)
        }
        headText = head
        parseRows(body)
    }

    private var headText: String

    private fun isTagStart(s: String, at: Int, tag: String): Boolean {
        if (!s.startsWith(tag, at)) return false
        val nx = s.getOrNull(at + tag.length) ?: return false
        return nx == ' ' || nx == '>' || nx == '/' || nx == '\t' || nx == '\n' || nx == '\r'
    }

    private fun parseRows(body: String) {
        val rowTag = "<${prefix}row"
        val rowClose = "</${prefix}row>"
        var i = 0
        var nextRow = 0
        while (true) {
            var s = body.indexOf(rowTag, i)
            while (s >= 0 && !isTagStart(body, s, rowTag)) s = body.indexOf(rowTag, s + 1)
            if (s < 0) break
            val gt = body.indexOf('>', s)
            val self = body[gt - 1] == '/'
            val attrs = Attrs.parse(body.substring(s + rowTag.length, if (self) gt - 1 else gt))
            val content: String
            if (self) { content = ""; i = gt + 1 } else {
                val close = body.indexOf(rowClose, gt)
                content = body.substring(gt + 1, close)
                i = close + rowClose.length
            }
            val r = (attrs["r"]?.toIntOrNull() ?: (nextRow + 1)) - 1
            nextRow = r + 1
            attrs["r"] = (r + 1).toString()
            val (cells, extra) = parseCells(content)
            rows[r] = RowX(attrs, cells, extra)
        }
    }

    private fun parseCells(content: String): Pair<java.util.TreeMap<Int, CellX>, String> {
        val cells = java.util.TreeMap<Int, CellX>()
        val tag = "<${prefix}c"
        val close = "</${prefix}c>"
        val extra = StringBuilder()
        var i = 0
        var nextCol = 0
        while (true) {
            var s = content.indexOf(tag, i)
            while (s >= 0 && !isTagStart(content, s, tag)) s = content.indexOf(tag, s + 1)
            if (s < 0) { extra.append(content, i, content.length); break }
            extra.append(content, i, s)
            val gt = content.indexOf('>', s)
            val self = content[gt - 1] == '/'
            val attrs = Attrs.parse(content.substring(s + tag.length, if (self) gt - 1 else gt))
            val inner: String?
            if (self) { inner = null; i = gt + 1 } else {
                val e = content.indexOf(close, gt)
                inner = content.substring(gt + 1, e)
                i = e + close.length
            }
            val col = attrs["r"]?.let { XlsxRefs.parseCell(it)?.second } ?: nextCol
            nextCol = col + 1
            cells[col] = CellX(attrs, inner)
        }
        return cells to extra.toString().trim()
    }

    // ── cell edits ─────────────────────────────────────────────────────────────

    fun setCell(ch: CellChange) {
        dirty = true
        val row = rows.getOrPut(ch.r) { RowX(Attrs().also { it["r"] = (ch.r + 1).toString() }, java.util.TreeMap(), "") }
        val existing = row.cells[ch.c]
        val after = ch.after
        if (after == null) { row.cells.remove(ch.c); return }
        if (ch.styleOnly && existing != null) {
            if (after.style == 0) existing.attrs.remove("s") else existing.attrs["s"] = after.style.toString()
            return
        }
        row.cells[ch.c] = buildCell(ch.r, ch.c, after)
    }

    private fun buildCell(r: Int, c: Int, d: CellData): CellX {
        val a = Attrs()
        a["r"] = XlsxRefs.cellRef(r, c)
        if (d.style != 0) a["s"] = d.style.toString()
        if (d.type.isNotEmpty()) a["t"] = d.type
        val p = prefix
        val inner = StringBuilder()
        d.formula?.let { f ->
            inner.append('<').append(p).append('f')
            f.type?.let { inner.append(" t=\"").append(it).append('"') }
            f.ref?.let { inner.append(" ref=\"").append(it).append('"') }
            f.si?.let { inner.append(" si=\"").append(it).append('"') }
            if (f.text.isEmpty()) inner.append("/>") else
                inner.append('>').append(XlsxRefs.escapeXml(f.text)).append("</").append(p).append("f>")
        }
        if (d.type == "inlineStr") {
            inner.append('<').append(p).append("is>")
            val runs = d.rich?.runs
            if (runs != null && runs.isNotEmpty()) {
                for (run in runs) {
                    inner.append('<').append(p).append("r>")
                    run.props?.toXml(p, inner)
                    appendT(inner, d.raw.substring(run.start.coerceAtMost(d.raw.length), run.end.coerceAtMost(d.raw.length)))
                    inner.append("</").append(p).append("r>")
                }
            } else appendT(inner, d.raw)
            inner.append("</").append(p).append("is>")
        } else if (d.raw.isNotEmpty()) {
            inner.append('<').append(p).append("v>").append(XlsxRefs.escapeXml(d.raw)).append("</").append(p).append("v>")
        }
        return CellX(a, if (inner.isEmpty()) null else inner.toString())
    }

    private fun appendT(sb: StringBuilder, text: String) {
        sb.append('<').append(prefix).append("t xml:space=\"preserve\">").append(XlsxRefs.escapeXml(text))
            .append("</").append(prefix).append("t>")
    }

    // ── structure ──────────────────────────────────────────────────────────────

    fun shiftLines(sheetName: String, axis: Axis, at: Int, count: Int) {
        dirty = true
        if (axis == Axis.ROW) {
            val old = java.util.TreeMap(rows)
            rows.clear()
            for ((r, row) in old) {
                val nr = XlsxEdits.shiftIndex(r, at, count)
                if (nr < 0) continue
                row.attrs["r"] = (nr + 1).toString()
                rows[nr] = row
            }
        } else {
            for (row in rows.values) {
                val old = java.util.TreeMap(row.cells)
                row.cells.clear()
                for ((c, cell) in old) {
                    val nc = XlsxEdits.shiftIndex(c, at, count)
                    if (nc >= 0) row.cells[nc] = cell
                }
            }
        }
        for (row in rows.values) {
            // `spans` is an optional hint; after a structural edit it would be wrong, so drop it.
            row.attrs.remove("spans")
            val r = (row.attrs["r"]?.toIntOrNull() ?: 1) - 1
            for ((c, cell) in row.cells) {
                cell.attrs["r"] = XlsxRefs.cellRef(r, c)
                cell.inner?.let { cell.inner = shiftFormulasIn(it, sheetName, true, axis, at, count) }
            }
        }
        headText = shiftOutside(headText, sheetName, axis, at, count)
        tail = shiftOutside(tail, sheetName, axis, at, count)
        if (axis == Axis.COL) headText = shiftCols(headText, at, count)
    }

    /** Formulas on this (another) sheet that name the sheet being edited. */
    fun shiftForeignFormulas(editedSheet: String, axis: Axis, at: Int, count: Int) {
        var changed = false
        for (row in rows.values) for (cell in row.cells.values) {
            val inner = cell.inner ?: continue
            val n = shiftFormulasIn(inner, editedSheet, false, axis, at, count)
            if (n != inner) { cell.inner = n; changed = true }
        }
        if (changed) dirty = true
    }

    private fun shiftFormulasIn(inner: String, sheet: String, same: Boolean, axis: Axis, at: Int, count: Int): String {
        if (!inner.contains("f")) return inner
        val p = Regex.escape(prefix)
        return Regex("<(${p}f)\\b([^>]*?)(/>|>(.*?)</${p}f>)", RegexOption.DOT_MATCHES_ALL).replace(inner) { m ->
            var attrs = m.groupValues[2]
            if (same) {
                attrs = Regex("\\bref=\"([^\"]*)\"").replace(attrs) { r ->
                    val s = XlsxRefs.shiftSqref(r.groupValues[1], axis, at, count) ?: r.groupValues[1]
                    "ref=\"$s\""
                }
            }
            if (m.groupValues[3] == "/>") "<${m.groupValues[1]}$attrs/>" else {
                val text = XlsxWriter.unescape(m.groupValues[4])
                val shifted = XlsxRefs.shiftFormula(text, sheet, same, axis, at, count)
                "<${m.groupValues[1]}$attrs>${XlsxRefs.escapeXml(shifted)}</${m.groupValues[1]}>"
            }
        }
    }

    /** Merges, validations, conditional formats, hyperlinks, autofilter outside sheetData. */
    private fun shiftOutside(xml: String, sheet: String, axis: Axis, at: Int, count: Int): String {
        var s = xml
        val any = "(?:\\w+:)?"
        // Selections point at cells that may no longer exist; they are optional, so drop them.
        s = Regex("<${any}selection\\b[^>]*/>").replace(s, "")
        s = Regex("<(${any})(mergeCell|hyperlink)\\b([^>]*?)/>").replace(s) { m ->
            val attrs = Attrs.parse(m.groupValues[3])
            val ref = attrs["ref"] ?: return@replace m.value
            val nr = XlsxRefs.parseRange(ref)?.let { XlsxRefs.shiftRange(it, axis, at, count) }
            if (nr == null || (m.groupValues[2] == "mergeCell" && nr.isSingle)) "" else {
                attrs["ref"] = nr.toRef()
                val sb = StringBuilder("<").append(m.groupValues[1]).append(m.groupValues[2]); attrs.render(sb); sb.append("/>").toString()
            }
        }
        s = Regex("<(${any})autoFilter\\b([^>]*?)(/?)>").replace(s) { m ->
            val attrs = Attrs.parse(m.groupValues[2])
            val ref = attrs["ref"] ?: return@replace m.value
            XlsxRefs.shiftSqref(ref, axis, at, count)?.let { attrs["ref"] = it }
            val sb = StringBuilder("<").append(m.groupValues[1]).append("autoFilter"); attrs.render(sb); sb.append(m.groupValues[3]).append('>').toString()
        }
        for (el in listOf("dataValidation", "conditionalFormatting")) {
            s = Regex("<(${any})$el\\b([^>]*?)(?:/>|>(.*?)</\\1$el>)", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
                val attrs = Attrs.parse(m.groupValues[2])
                var body = m.groupValues[3]
                var sqref = attrs["sqref"]
                val inner = Regex("<(\\w+:)?sqref>(.*?)</(\\w+:)?sqref>").find(body)
                if (sqref == null && inner != null) sqref = inner.groupValues[2]
                val ns = sqref?.let { XlsxRefs.shiftSqref(it, axis, at, count) } ?: return@replace if (sqref == null) m.value else ""
                if (attrs["sqref"] != null) attrs["sqref"] = ns
                else if (inner != null) body = body.replaceRange(inner.range, "<${inner.groupValues[1]}sqref>$ns</${inner.groupValues[3]}sqref>")
                body = Regex("<((?:\\w+:)?(?:formula1|formula2|formula|f))>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).replace(body) { f ->
                    val t = XlsxWriter.unescape(f.groupValues[2])
                    "<${f.groupValues[1]}>${XlsxRefs.escapeXml(XlsxRefs.shiftFormula(t, sheet, true, axis, at, count))}</${f.groupValues[1]}>"
                }
                val sb = StringBuilder("<").append(m.groupValues[1]).append(el); attrs.render(sb)
                if (m.value.endsWith("/>") && m.groupValues[3].isEmpty()) sb.append("/>") else sb.append('>').append(body).append("</").append(m.groupValues[1]).append(el).append('>')
                sb.toString()
            }
        }
        s = fixCountsAndEmpties(s)
        return s
    }

    private fun fixCountsAndEmpties(xml: String): String {
        var s = xml
        for ((wrapper, child) in listOf("mergeCells" to "mergeCell", "dataValidations" to "dataValidation", "hyperlinks" to "hyperlink")) {
            s = Regex("<((?:\\w+:)?)$wrapper\\b([^>]*)>(.*?)</\\1$wrapper>", RegexOption.DOT_MATCHES_ALL).replace(s) { m ->
                val n = Regex("<(?:\\w+:)?$child\\b").findAll(m.groupValues[3]).count()
                if (n == 0) "" else {
                    val attrs = Attrs.parse(m.groupValues[2])
                    if (attrs["count"] != null) attrs["count"] = n.toString()
                    val sb = StringBuilder("<").append(m.groupValues[1]).append(wrapper); attrs.render(sb)
                    sb.append('>').append(m.groupValues[3]).append("</").append(m.groupValues[1]).append(wrapper).append('>').toString()
                }
            }
        }
        // An x14 ext that just lost its only child would be an empty <ext>; drop the ext as well.
        s = Regex("<((?:\\w+:)?)ext\\b[^>]*>\\s*</\\1ext>").replace(s, "")
        s = Regex("<((?:\\w+:)?)extLst\\b[^>]*>\\s*</\\1extLst>").replace(s, "")
        return s
    }

    private data class ColX(var min: Int, var max: Int, val attrs: Attrs)

    private fun readCols(xml: String): Triple<IntRange?, List<ColX>, String>? {
        val m = Regex("<((?:\\w+:)?)cols\\b[^>]*>(.*?)</\\1cols>", RegexOption.DOT_MATCHES_ALL).find(xml) ?: return null
        val list = Regex("<(?:\\w+:)?col\\b([^>]*?)/>").findAll(m.groupValues[2]).map { c ->
            val a = Attrs.parse(c.groupValues[1])
            ColX(a["min"]?.toIntOrNull() ?: 1, a["max"]?.toIntOrNull() ?: 1, a)
        }.toList()
        return Triple(m.range, list, m.groupValues[1])
    }

    private fun writeCols(p: String, cols: List<ColX>): String {
        if (cols.isEmpty()) return ""
        val sb = StringBuilder("<").append(p).append("cols>")
        for (c in cols.sortedBy { it.min }) {
            c.attrs["min"] = c.min.toString(); c.attrs["max"] = c.max.toString()
            sb.append('<').append(p).append("col"); c.attrs.render(sb); sb.append("/>")
        }
        return sb.append("</").append(p).append("cols>").toString()
    }

    private fun shiftCols(xml: String, at: Int, count: Int): String {
        val (range, cols, p) = readCols(xml) ?: return xml
        val out = cols.mapNotNull { c ->
            val nr = XlsxRefs.shiftRange(CellRange(0, c.min - 1, 0, c.max - 1), Axis.COL, at, count) ?: return@mapNotNull null
            c.min = nr.c1 + 1; c.max = nr.c2 + 1; c
        }
        return xml.replaceRange(range!!, writeCols(p, out))
    }

    fun setColWidth(col: Int, widthChars: Float?) {
        dirty = true
        val c1 = col + 1
        val existing = readCols(headText)
        val cols = existing?.second?.toMutableList() ?: mutableListOf()
        val p = existing?.third ?: prefix
        val hit = cols.firstOrNull { c1 in it.min..it.max }
        val target: ColX
        if (hit != null) {
            cols.remove(hit)
            if (hit.min < c1) cols.add(ColX(hit.min, c1 - 1, Attrs(LinkedHashMap(hit.attrs.map))))
            if (hit.max > c1) cols.add(ColX(c1 + 1, hit.max, Attrs(LinkedHashMap(hit.attrs.map))))
            target = ColX(c1, c1, Attrs(LinkedHashMap(hit.attrs.map)))
        } else target = ColX(c1, c1, Attrs())
        if (widthChars == null) { target.attrs.remove("width"); target.attrs.remove("customWidth") } else {
            target.attrs["width"] = widthChars.toString(); target.attrs["customWidth"] = "1"
        }
        if (target.attrs["width"] != null || target.attrs.map.size > 2) cols.add(target)
        val newCols = writeCols(p, cols)
        headText = if (existing != null) headText.replaceRange(existing.first!!, newCols) else headText + newCols
    }

    // ── output ─────────────────────────────────────────────────────────────────

    private fun dimensionRef(): String {
        var minR = Int.MAX_VALUE; var maxR = -1; var minC = Int.MAX_VALUE; var maxC = -1
        for ((r, row) in rows) {
            if (row.cells.isEmpty()) continue
            minR = minOf(minR, r); maxR = maxOf(maxR, r)
            minC = minOf(minC, row.cells.firstKey()); maxC = maxOf(maxC, row.cells.lastKey())
        }
        if (maxR < 0) return "A1"
        return CellRange(minR, minC, maxR, maxC).toRef()
    }

    fun serialize(): String {
        val sb = StringBuilder(headText.length + tail.length + rows.size * 64)
        sb.append(Regex("(<(?:\\w+:)?dimension\\b[^>]*?\\bref=\")[^\"]*(\")").replaceFirst(headText, "$1${dimensionRef()}$2"))
        sb.append(openTag)
        val p = prefix
        for (row in rows.values) {
            sb.append('<').append(p).append("row"); row.attrs.render(sb)
            if (row.cells.isEmpty() && row.extra.isEmpty()) { sb.append("/>"); continue }
            sb.append('>')
            for (cell in row.cells.values) {
                sb.append('<').append(p).append('c'); cell.attrs.render(sb)
                if (cell.inner == null) sb.append("/>") else sb.append('>').append(cell.inner).append("</").append(p).append("c>")
            }
            sb.append(row.extra)
            sb.append("</").append(p).append("row>")
        }
        sb.append(closeTag)
        sb.append(tail)
        return sb.toString()
    }
}

/** Appends derived fonts/fills/borders/xfs/numFmts to an existing styles.xml. */
internal object StylesXml {

    /** Null when a section we need to append to is missing (the caller then writes a full part). */
    fun append(xml: String, st: StyleTable): String? {
        val p = Regex("<((?:\\w+:)?)styleSheet\\b").find(xml)?.groupValues?.get(1) ?: return null
        var s = xml
        val newFmts = st.numFmts.filterKeys { it !in st.origNumFmtIds }
        if (newFmts.isNotEmpty()) {
            val items = newFmts.entries.joinToString("") { (id, code) ->
                "<${p}numFmt numFmtId=\"$id\" formatCode=\"${XlsxRefs.escapeXml(code)}\"/>"
            }
            s = appendSection(s, p, "numFmts", items, st.numFmts.size)
                ?: Regex("<${Regex.escape(p)}styleSheet\\b[^>]*>").find(s)?.let { m ->
                    s.substring(0, m.range.last + 1) + "<${p}numFmts count=\"${newFmts.size}\">$items</${p}numFmts>" + s.substring(m.range.last + 1)
                } ?: return null
        }
        fun tail(list: List<XmlEl>, from: Int) = buildString { for (i in from until list.size) list[i].toXml(p, this) }
        if (st.fonts.size > st.origFonts) s = appendSection(s, p, "fonts", tail(st.fonts, st.origFonts), st.fonts.size) ?: return null
        if (st.fills.size > st.origFills) s = appendSection(s, p, "fills", tail(st.fills, st.origFills), st.fills.size) ?: return null
        if (st.borders.size > st.origBorders) s = appendSection(s, p, "borders", tail(st.borders, st.origBorders), st.borders.size) ?: return null
        if (st.cellXfs.size > st.origXfs) s = appendSection(s, p, "cellXfs", tail(st.cellXfs, st.origXfs), st.cellXfs.size) ?: return null
        return s
    }

    private fun appendSection(xml: String, p: String, name: String, items: String, total: Int): String? {
        val m = Regex("<${Regex.escape(p)}$name\\b([^>]*?)(/?)>").find(xml) ?: return null
        val attrs = Attrs.parse(m.groupValues[1])
        attrs["count"] = total.toString()
        val open = StringBuilder("<").append(p).append(name).also { attrs.render(it) }.append('>').toString()
        return if (m.groupValues[2] == "/") {
            xml.replaceRange(m.range, "$open$items</$p$name>")
        } else {
            val close = "</$p$name>"
            val end = xml.indexOf(close, m.range.last)
            if (end < 0) return null
            xml.substring(0, m.range.first) + open + xml.substring(m.range.last + 1, end) + items + xml.substring(end)
        }
    }

    fun full(st: StyleTable): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        if (st.numFmts.isNotEmpty()) {
            append("<numFmts count=\"${st.numFmts.size}\">")
            for ((id, code) in st.numFmts) append("<numFmt numFmtId=\"$id\" formatCode=\"${XlsxRefs.escapeXml(code)}\"/>")
            append("</numFmts>")
        }
        fun section(n: String, l: List<XmlEl>) { append("<$n count=\"${l.size}\">"); l.forEach { it.toXml("", this) }; append("</$n>") }
        section("fonts", st.fonts)
        section("fills", st.fills)
        section("borders", st.borders)
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        section("cellXfs", st.cellXfs)
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }
}

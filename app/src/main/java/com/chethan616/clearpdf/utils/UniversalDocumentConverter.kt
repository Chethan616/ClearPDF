package com.chethan616.clearpdf.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object UniversalDocumentConverter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private val TEXT_W get() = (PAGE_W - 2 * MARGIN).toInt()

    // ── Public API ─────────────────────────────────────────────────────────────

    fun isPdf(context: Context, uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null && type.contains("pdf", ignoreCase = true)) return true
        return getFileName(context, uri).endsWith(".pdf", ignoreCase = true)
    }

    fun convertToPdf(context: Context, sourceUri: Uri): Uri {
        val mimeType = context.contentResolver.getType(sourceUri) ?: ""
        val name = getFileName(context, sourceUri).lowercase()
        return when {
            mimeType.startsWith("image/") || name.endsWithAny(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".heic") ->
                convertImageToPdf(context, sourceUri)
            name.endsWith(".docx") -> convertZipXmlToPdf(context, sourceUri, DocFlavor.DOCX)
            name.endsWith(".xlsx") -> convertZipXmlToPdf(context, sourceUri, DocFlavor.XLSX)
            name.endsWith(".pptx") -> convertZipXmlToPdf(context, sourceUri, DocFlavor.PPTX)
            name.endsWith(".odt")  -> convertZipXmlToPdf(context, sourceUri, DocFlavor.ODT)
            name.endsWith(".doc")  -> convertLegacyWordToPdf(context, sourceUri)
            name.endsWith(".xls")  -> convertLegacyXlsToPdf(context, sourceUri)
            name.endsWith(".ppt")  -> convertLegacyPptToPdf(context, sourceUri)
            mimeType.startsWith("text/") || name.endsWithAny(".txt", ".csv", ".log", ".rtf", ".md") ->
                convertTextToPdf(context, sourceUri)
            else -> convertTextToPdf(context, sourceUri)
        }
    }

    // ── Image ──────────────────────────────────────────────────────────────────

    private fun convertImageToPdf(context: Context, sourceUri: Uri): Uri {
        val bitmap = context.contentResolver.openInputStream(sourceUri).use {
            BitmapFactory.decodeStream(it) ?: throw IllegalStateException("Invalid image")
        }
        val pdfDoc = PdfDocument()
        val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create())
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDoc.finishPage(page)
        bitmap.recycle()
        return writePdf(context, pdfDoc, "Image")
    }

    // ── Plain text / CSV ───────────────────────────────────────────────────────

    private fun convertTextToPdf(context: Context, sourceUri: Uri): Uri {
        val lines = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readLines()
        } ?: emptyList()

        val paint = bodyPaint(Typeface.MONOSPACE, 11f)
        val blocks = lines.map { line ->
            DocBlock.Para(SpannableStringBuilder(line), spaceAfter = 0f, lineSpacingMult = 1.15f)
        }
        return writePdf(context, renderBlocks(blocks, paint), "Text")
    }

    // ── Office Open XML (docx / xlsx / pptx / odt) ────────────────────────────

    private enum class DocFlavor { DOCX, XLSX, PPTX, ODT }

    private fun convertZipXmlToPdf(context: Context, sourceUri: Uri, flavor: DocFlavor): Uri {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
        val paint = bodyPaint()
        val blocks: List<DocBlock> = when (flavor) {
            DocFlavor.DOCX -> parseDocx(bytes)
            DocFlavor.XLSX -> parseXlsx(bytes)
            DocFlavor.PPTX -> parsePptx(bytes)
            DocFlavor.ODT  -> parseOdt(bytes)
        }
        val tag = flavor.name.lowercase().replaceFirstChar { it.uppercase() }
        return writePdf(context, renderBlocks(blocks, paint), tag)
    }

    // ── DOCX ───────────────────────────────────────────────────────────────────

    private fun parseDocx(bytes: ByteArray): List<DocBlock> {
        // Read the whole package so we can resolve inline images (drawing → r:embed → rels → media).
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val docXml = entries["word/document.xml"]
            ?: return listOf(DocBlock.Para(SpannableStringBuilder("(empty document)")))
        val rels = parseRels(entries["word/_rels/document.xml.rels"])   // rId → "media/imageN.png"
        val blocks = parseWordXml(docXml.inputStream(), rels, entries)
        return blocks.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty document)"))) }
    }

    private fun parseWordXml(stream: InputStream, rels: Map<String, String>, entries: Map<String, ByteArray>): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }

        var inBody     = false
        var inPara     = false
        var inParaProps = false
        var inRun      = false
        var inRunProps = false
        var inTbl      = false
        var tblRows    = mutableListOf<List<String>>()
        var tblRow     = mutableListOf<String>()
        var tblCell    = StringBuilder()
        var inTblCell  = false

        var headingLevel = 0
        var align = Layout.Alignment.ALIGN_NORMAL
        var justify = false
        // w:ind / w:spacing are in twips (1/20 pt). -1 = "not specified" so a real 0 still overrides.
        var leftIndentTwips = 0
        var spaceBeforeTwips = -1
        var spaceAfterTwips = -1
        var bold = false
        var italic = false
        var underline = false
        var runColor = 0            // 0 = "no explicit colour" sentinel (a real colour is 0xFF…)
        var fontHalfPt = 0          // w:sz is in half-points; 0 = default
        var paraBuf  = StringBuilder()
        data class Span(val start: Int, val end: Int, val span: Any)
        var spans    = mutableListOf<Span>()

        fun flushPara() {
            // trimEnd only — leading indentation and intentional spacing survive (Word carries some
            // indent as literal runs, and trimming both ends flattened them). Real indent still comes
            // from w:ind below.
            val text = paraBuf.toString().trimEnd()
            val leftIndentPt = leftIndentTwips / 20f
            val spaceBefore = if (spaceBeforeTwips >= 0) spaceBeforeTwips / 20f else 0f
            val spaceAfter = when {
                spaceAfterTwips >= 0 -> spaceAfterTwips / 20f
                headingLevel > 0     -> 10f
                else                 -> 6f
            }
            if (text.isNotEmpty()) {
                val ssb = SpannableStringBuilder(text)
                if (headingLevel > 0) ssb.setSpan(StyleSpan(Typeface.BOLD), 0, ssb.length, 0)
                spans.forEach { s -> ssb.setSpan(s.span, s.start.coerceAtMost(ssb.length), s.end.coerceAtMost(ssb.length), 0) }
                result.add(DocBlock.Para(
                    ssb, headingLevel = headingLevel, spaceAfter = spaceAfter, spaceBefore = spaceBefore,
                    alignment = align, leftIndent = leftIndentPt, justify = justify
                ))
            } else {
                // Blank paragraph → a blank line, so vertical spacing between blocks is preserved.
                result.add(DocBlock.Para(SpannableStringBuilder(""), spaceAfter = 0f))
            }
            paraBuf.clear(); spans.clear(); headingLevel = 0; align = Layout.Alignment.ALIGN_NORMAL
            justify = false; leftIndentTwips = 0; spaceBeforeTwips = -1; spaceAfterTwips = -1
            bold = false; italic = false; underline = false; runColor = 0; fontHalfPt = 0
        }

        fun addImage(embedId: String?) {
            val target = rels[embedId ?: return] ?: return
            val path = if (target.startsWith("/")) target.drop(1) else "word/${target.removePrefix("/")}"
            val imgBytes = entries[path] ?: return
            val bmp = runCatching { BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) }.getOrNull() ?: return
            flushPara()
            result.add(DocBlock.ImageBlock(bmp))
        }

        // A boolean toggle prop (w:b / w:i / w:u) is ON unless it carries w:val="false"/"0"/"none".
        fun toggleOn(): Boolean = parser.getAttributeValue(null, "w:val").let { it == null || it !in setOf("false", "0", "none") }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = if (event == XmlPullParser.START_TAG || event == XmlPullParser.END_TAG) parser.name else null
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "w:body"  -> inBody = true
                    "w:p"     -> if (inBody && !inTbl) { inPara = true }
                    "w:pPr"   -> if (inPara) inParaProps = true
                    "w:pStyle"-> if (inParaProps) {
                        headingLevel = when (parser.getAttributeValue(null, "w:val")?.lowercase()) {
                            "heading1", "title", "toc heading" -> 1
                            "heading2", "subtitle"             -> 2
                            "heading3"                         -> 3
                            "heading4", "heading5", "heading6" -> 4
                            else                               -> 0
                        }
                    }
                    // Paragraph alignment. "both"/"distribute" = justified — kept left-aligned with the
                    // justify flag so the renderer can space the words out (setJustificationMode).
                    "w:jc"    -> if (inParaProps) when (parser.getAttributeValue(null, "w:val")?.lowercase()) {
                        "center"             -> { align = Layout.Alignment.ALIGN_CENTER;   justify = false }
                        "right", "end"       -> { align = Layout.Alignment.ALIGN_OPPOSITE; justify = false }
                        "both", "distribute" -> { align = Layout.Alignment.ALIGN_NORMAL;   justify = true  }
                        else                 -> { align = Layout.Alignment.ALIGN_NORMAL;   justify = false }
                    }
                    // Paragraph indent (twips). w:start is the LTR alias for w:left in newer files.
                    "w:ind"   -> if (inParaProps) {
                        (parser.getAttributeValue(null, "w:left") ?: parser.getAttributeValue(null, "w:start"))
                            ?.toIntOrNull()?.let { leftIndentTwips = it.coerceAtLeast(0) }
                    }
                    // Explicit paragraph spacing before/after (twips) overrides the defaults.
                    "w:spacing" -> if (inParaProps) {
                        parser.getAttributeValue(null, "w:before")?.toIntOrNull()?.let { spaceBeforeTwips = it }
                        parser.getAttributeValue(null, "w:after")?.toIntOrNull()?.let { spaceAfterTwips = it }
                    }
                    "w:r"     -> if (inPara) { inRun = true; bold = false; italic = false; underline = false; runColor = 0; fontHalfPt = 0 }
                    "w:rPr"   -> if (inRun) inRunProps = true
                    "w:b"     -> if (inRunProps) bold = toggleOn()
                    "w:i"     -> if (inRunProps) italic = toggleOn()
                    "w:u"     -> if (inRunProps) underline = toggleOn()
                    "w:sz"    -> if (inRunProps) fontHalfPt = parser.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0
                    "w:color" -> if (inRunProps) runColor = parseHexColor(parser.getAttributeValue(null, "w:val"))
                    "w:t"     -> if (inRun && inPara) { /* text event follows */ }
                    // Preserve tabs and in-paragraph line breaks so words don't run together.
                    "w:tab"   -> if (inPara && !inTbl) paraBuf.append("    ")
                    "w:br", "w:cr" -> if (inPara && !inTbl) paraBuf.append("\n")
                    // Inline image: <a:blip r:embed="rIdN"/> → resolve via rels → word/media/… bytes.
                    "a:blip"  -> if (inPara && !inTbl) addImage(parser.getAttributeValue(null, "r:embed") ?: parser.getAttributeValue(null, "r:link"))
                    "w:tbl"   -> if (inBody) { inTbl = true; tblRows = mutableListOf() }
                    "w:tr"    -> if (inTbl) { tblRow = mutableListOf() }
                    "w:tc"    -> if (inTbl) { inTblCell = true; tblCell.clear() }
                }
                XmlPullParser.END_TAG -> when (name) {
                    "w:body"  -> inBody = false
                    "w:pPr"   -> inParaProps = false
                    "w:rPr"   -> inRunProps = false
                    "w:r"     -> inRun = false
                    "w:p"     -> if (inPara) { flushPara(); inPara = false }
                    "w:tbl"   -> {
                        if (tblRows.isNotEmpty()) result.add(DocBlock.Table(tblRows))
                        inTbl = false
                    }
                    "w:tr"    -> if (inTbl) { tblRows.add(tblRow.toList()) }
                    "w:tc"    -> if (inTbl) {
                        tblRow.add(tblCell.toString().trim()); inTblCell = false
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (inRun && inPara && text.isNotEmpty()) {
                        val start = paraBuf.length
                        paraBuf.append(text)
                        val end = paraBuf.length
                        if (bold) spans.add(Span(start, end, StyleSpan(Typeface.BOLD)))
                        if (italic) spans.add(Span(start, end, StyleSpan(Typeface.ITALIC)))
                        if (underline) spans.add(Span(start, end, UnderlineSpan()))
                        if (runColor != 0) spans.add(Span(start, end, ForegroundColorSpan(runColor)))
                        if (fontHalfPt > 0) spans.add(Span(start, end, AbsoluteSizeSpan((fontHalfPt / 2f).toInt().coerceIn(6, 96))))
                    }
                    if (inTblCell && text.isNotEmpty()) tblCell.append(text)
                }
            }
            event = parser.next()
        }
        return result
    }

    // ── XLSX ───────────────────────────────────────────────────────────────────

    private fun parseXlsx(bytes: ByteArray): List<DocBlock> {
        // Read the whole package once so we can resolve workbook order + real sheet names via the
        // rels — instead of guessing from the zip's arbitrary entry order.
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()

        // Workbook defines sheets in TAB order with their names + an r:id → the rels map that r:id
        // to the actual sheetN.xml file. This gives correct order AND the human sheet name.
        val workbookSheets = parseWorkbookSheets(entries["xl/workbook.xml"])          // (name, rId) in tab order
        val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])                   // rId → "worksheets/sheetN.xml"
        val ordered: List<Pair<String, ByteArray>> = if (workbookSheets.isNotEmpty()) {
            workbookSheets.mapNotNull { (name, rId) ->
                val target = rels[rId] ?: return@mapNotNull null
                val path = if (target.startsWith("/")) target.drop(1) else "xl/${target.removePrefix("/")}"
                entries[path]?.let { name to it }
            }
        } else {
            // Fallback: every worksheet by numeric index, generic "Sheet N" names.
            entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { Regex("sheet(\\d+)\\.xml").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                .map { (Regex("sheet(\\d+)").find(it)?.groupValues?.getOrNull(1)?.let { n -> "Sheet $n" } ?: "Sheet") to entries[it]!! }
        }

        val result = mutableListOf<DocBlock>()
        for ((name, xml) in ordered) {
            val rows = parseWorksheetRows(xml.inputStream(), sharedStrings)
            if (rows.isEmpty()) continue
            // A labelled header for each sheet so a multi-sheet workbook reads as clearly separated
            // sections instead of one anonymous run of tables.
            val header = SpannableStringBuilder(name)
            header.setSpan(StyleSpan(Typeface.BOLD), 0, header.length, 0)
            result.add(DocBlock.Para(header, headingLevel = 1, spaceAfter = 6f))
            result.add(DocBlock.Table(rows))
        }
        return result.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty spreadsheet)"))) }
    }

    /** Sheets in workbook (tab) order as (name, relationshipId). */
    private fun parseWorkbookSheets(bytes: ByteArray?): List<Pair<String, String>> {
        if (bytes == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }
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

    /** "#RRGGBB" / "RRGGBB" / "auto" → ARGB int, or 0 (sentinel = none) when absent/invalid. */
    private fun parseHexColor(hex: String?): Int {
        if (hex.isNullOrBlank() || hex.equals("auto", ignoreCase = true)) return 0
        return runCatching { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrDefault(0)
    }

    /** Relationship id → target path from a .rels part. */
    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val out = HashMap<String, String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }
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
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        var inT = false
        val buf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") buf.clear()
                                           else if (parser.name == "t") inT = true
                XmlPullParser.END_TAG   -> if (parser.name == "si") { strings.add(buf.toString()); inT = false }
                                           else if (parser.name == "t") inT = false
                XmlPullParser.TEXT      -> if (inT) buf.append(parser.text)
            }
            event = parser.next()
        }
        return strings
    }

    /** Converts a spreadsheet column reference ("A", "B", … "AA") from a cell ref like "AB12" to a
     *  0-based column index. Returns -1 if there are no leading letters. */
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
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        // Place every cell at its REAL column index (from the r="C5" ref) so omitted/empty cells —
        // which OOXML simply leaves out — don't shift the rest of the row left. That left-shift was
        // the main reason spreadsheet values looked "missing" or landed under the wrong header here.
        var rowCells = sortedMapOf<Int, String>()
        var cellType = ""
        var cellCol = 0
        var nextAutoCol = 0
        var inVal = false           // inside <v> (value) or inline <t> (inline string text)
        val cellBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { rowCells = sortedMapOf(); nextAutoCol = 0 }
                    "c"   -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellCol = colIndexFromRef(parser.getAttributeValue(null, "r")).let { if (it >= 0) it else nextAutoCol }
                        inVal = false; cellBuf.clear()
                    }
                    "v", "t" -> inVal = true    // <t> here = an inline-string cell (<is><t>…)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "row" -> if (rowCells.isNotEmpty()) {
                        val maxC = rowCells.lastKey()
                        rows.add((0..maxC).map { rowCells[it] ?: "" })
                    }
                    "c"   -> {
                        val raw = cellBuf.toString()
                        val value = if (cellType == "s") sharedStrings.getOrElse(raw.trim().toIntOrNull() ?: -1) { raw } else raw
                        if (value.isNotEmpty()) rowCells[cellCol] = value
                        nextAutoCol = cellCol + 1
                        inVal = false
                    }
                    "v", "t" -> inVal = false
                }
                XmlPullParser.TEXT -> if (inVal) cellBuf.append(parser.text)
            }
            event = parser.next()
            if (rows.size > 5000) break
        }
        return rows
    }

    // ── PPTX ───────────────────────────────────────────────────────────────────

    private fun parsePptx(bytes: ByteArray): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        // Collect slides keyed by their numeric index so they render in order — ZipInputStream
        // yields entries in arbitrary order, which previously jumbled the slide sequence.
        val slideXml = sortedMapOf<Int, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val n = entry.name
                if (n.startsWith("ppt/slides/slide") && n.endsWith(".xml")) {
                    val num = Regex("slide(\\d+)\\.xml").find(n)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                    slideXml[num] = zip.readBytes()
                }
            }
        }
        var slideNum = 0
        for ((_, xml) in slideXml) {
            slideNum++
            result.addAll(parseSlideXml(xml.inputStream(), slideNum))
        }
        return result.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty presentation)"))) }
    }

    private fun parseSlideXml(stream: InputStream, slideNum: Int): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        val header = SpannableStringBuilder("Slide $slideNum")
        header.setSpan(StyleSpan(Typeface.BOLD), 0, header.length, 0)
        result.add(DocBlock.Para(header, headingLevel = 2, spaceAfter = 4f))

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        var inT = false
        val buf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "a:t") { inT = true; buf.clear() }
                XmlPullParser.END_TAG   -> if (parser.name == "a:t") {
                    val text = buf.toString().trim()
                    if (text.isNotEmpty()) result.add(DocBlock.Para(SpannableStringBuilder(text), spaceAfter = 4f))
                    inT = false
                }
                XmlPullParser.TEXT -> if (inT) buf.append(parser.text)
            }
            event = parser.next()
        }
        return result
    }

    // ── ODT ────────────────────────────────────────────────────────────────────

    private fun parseOdt(bytes: ByteArray): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "content.xml") {
                    result.addAll(parseOdtContent(zip))
                    break
                }
                zip.closeEntry()
            }
        }
        return result.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty document)"))) }
    }

    private fun parseOdtContent(stream: InputStream): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        var inPara = false
        val buf = StringBuilder()
        var styleName = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "text:p", "text:h" -> {
                        inPara = true; buf.clear()
                        styleName = parser.getAttributeValue(null, "text:style-name") ?: ""
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "text:p", "text:h" -> {
                        val text = buf.toString().trim()
                        if (text.isNotEmpty()) {
                            val heading = styleName.lowercase().contains("heading")
                            val ssb = SpannableStringBuilder(text)
                            if (heading) ssb.setSpan(StyleSpan(Typeface.BOLD), 0, ssb.length, 0)
                            result.add(DocBlock.Para(ssb, headingLevel = if (heading) 1 else 0))
                        }
                        inPara = false
                    }
                }
                XmlPullParser.TEXT -> if (inPara) buf.append(parser.text)
            }
            event = parser.next()
        }
        return result
    }

    // ── Legacy POI formats ─────────────────────────────────────────────────────

    private fun convertLegacyWordToPdf(context: Context, uri: Uri): Uri {
        val paras = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching { WordExtractor(stream).use { it.paragraphText.toList() } }.getOrDefault(emptyList())
        } ?: emptyList()
        val paint = bodyPaint()
        val blocks = paras.filter { it.isNotBlank() }.map { DocBlock.Para(SpannableStringBuilder(it.trim())) }
        return writePdf(context, renderBlocks(blocks, paint), "Doc")
    }

    private fun convertLegacyXlsToPdf(context: Context, uri: Uri): Uri {
        val blocks = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                HSSFWorkbook(stream).use { wb ->
                    // Every sheet, and address cells by COLUMN INDEX (getCell) so blank cells keep
                    // the columns aligned instead of collapsing (row.map skips missing cells).
                    (0 until wb.numberOfSheets).flatMap { si ->
                        val sheet = wb.getSheetAt(si)
                        val rows = sheet.mapNotNull { row ->
                            val lastCol = row.lastCellNum.toInt()
                            if (lastCol <= 0) null
                            else (0 until lastCol).map { c -> row.getCell(c)?.toString()?.trim() ?: "" }
                        }
                        if (rows.isEmpty()) emptyList()
                        else {
                            val header = SpannableStringBuilder(wb.getSheetName(si) ?: "Sheet ${si + 1}")
                            header.setSpan(StyleSpan(Typeface.BOLD), 0, header.length, 0)
                            listOf(DocBlock.Para(header, headingLevel = 1, spaceAfter = 6f), DocBlock.Table(rows))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val paint = bodyPaint()
        return writePdf(context, renderBlocks(blocks.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty)"))) }, paint), "Xls")
    }

    private fun convertLegacyPptToPdf(context: Context, uri: Uri): Uri {
        val texts = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                HSLFSlideShow(stream).use { ss ->
                    ss.slides.flatMap { slide ->
                        slide.shapes.mapNotNull { shape ->
                            (shape as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text?.trim()
                        }.filter { it.isNotBlank() }
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val paint = bodyPaint()
        val blocks = texts.map { DocBlock.Para(SpannableStringBuilder(it)) }
        return writePdf(context, renderBlocks(blocks.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty)"))) }, paint), "Ppt")
    }

    // ── Rendering engine ───────────────────────────────────────────────────────

    private sealed class DocBlock {
        data class Para(
            val text: SpannableStringBuilder,
            val headingLevel: Int = 0,
            val spaceAfter: Float = 8f,
            val spaceBefore: Float = 0f,
            val lineSpacingMult: Float = 1.25f,
            val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            // Left indent in points (from w:ind) and whether the paragraph is justified (w:jc="both").
            // These are what let indented / justified Word text keep its shape instead of collapsing
            // flush-left.
            val leftIndent: Float = 0f,
            val justify: Boolean = false
        ) : DocBlock()
        data class Table(val rows: List<List<String>>) : DocBlock()
        data class ImageBlock(val bitmap: android.graphics.Bitmap) : DocBlock()
    }

    private fun renderBlocks(blocks: List<DocBlock>, defaultPaint: TextPaint): PdfDocument {
        val pdfDoc = PdfDocument()
        var pageNum = 1
        var y = MARGIN
        var page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas

        fun newPage() {
            pdfDoc.finishPage(page)
            pageNum++
            page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureRoom(needed: Float) {
            if (y + needed > PAGE_H - MARGIN && y > MARGIN) newPage()
        }

        for (block in blocks) {
            when (block) {
                is DocBlock.Para -> {
                    // An empty paragraph is a blank line the author put there on purpose — keep it as
                    // one line of vertical space instead of dropping it, so the document's spacing
                    // survives the conversion.
                    if (block.text.isEmpty()) { y += 13f; continue }
                    y += block.spaceBefore
                    val paint = if (block.headingLevel > 0) headingPaint(block.headingLevel) else defaultPaint
                    val indent = block.leftIndent.coerceIn(0f, TEXT_W - 40f)
                    val width = (TEXT_W - indent).toInt().coerceAtLeast(40)
                    val builder = StaticLayout.Builder
                        .obtain(block.text, 0, block.text.length, paint, width)
                        .setAlignment(block.alignment)
                        .setLineSpacing(2f, block.lineSpacingMult)
                        .setIncludePad(false)
                    // Justified text (Word's "both") — only left-aligned runs can be justified.
                    if (block.justify && block.alignment == Layout.Alignment.ALIGN_NORMAL &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    ) {
                        builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
                    }
                    val layout = builder.build()
                    ensureRoom(layout.height.toFloat())
                    canvas.save(); canvas.translate(MARGIN + indent, y); layout.draw(canvas); canvas.restore()
                    y += layout.height + block.spaceAfter
                }
                is DocBlock.ImageBlock -> {
                    val bmp = block.bitmap
                    if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) continue
                    // Fit to the text column width (never upscaled), and cap to the page height.
                    var drawW = TEXT_W.toFloat().coerceAtMost(bmp.width.toFloat())
                    var drawH = drawW * bmp.height / bmp.width
                    val maxH = PAGE_H - 2 * MARGIN
                    if (drawH > maxH) { drawH = maxH; drawW = drawH * bmp.width / bmp.height }
                    ensureRoom(drawH)
                    val left = MARGIN + (TEXT_W - drawW) / 2f   // centre images like Word usually does
                    val dst = android.graphics.RectF(left, y, left + drawW, y + drawH)
                    canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    y += drawH + 10f
                    bmp.recycle()
                }
                is DocBlock.Table -> {
                    if (block.rows.isEmpty()) continue
                    // Up to 16 columns (was 8, which silently dropped wider sheets). Font size and
                    // the per-cell character budget scale to the actual column width, so text no
                    // longer overflows into the next column and long values are trimmed to fit — not
                    // to a fixed 24 chars that spilled over narrow columns.
                    val maxCols = block.rows.maxOf { it.size }.coerceIn(1, 16)
                    val colW = TEXT_W.toFloat() / maxCols
                    val cellPaint = cellPaint().apply {
                        textSize = when { maxCols > 10 -> 7f; maxCols > 6 -> 8f; else -> 9f }
                    }
                    val charW = cellPaint.measureText("0").coerceAtLeast(1f)
                    val maxChars = ((colW - 6f) / charW).toInt().coerceAtLeast(2)
                    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(80, 0, 0, 0); strokeWidth = 0.5f
                    }
                    val rowH = 16f

                    for (row in block.rows.take(2000)) {
                        ensureRoom(rowH + 2f)
                        // horizontal top rule
                        canvas.drawLine(MARGIN, y, MARGIN + TEXT_W, y, linePaint)
                        row.take(maxCols).forEachIndexed { colIdx, cell ->
                            val x = MARGIN + colIdx * colW
                            val clipped = if (cell.length > maxChars) cell.take((maxChars - 1).coerceAtLeast(1)) + "…" else cell
                            canvas.drawText(clipped, x + 3f, y + rowH - 5f, cellPaint)
                            // vertical divider
                            if (colIdx > 0) canvas.drawLine(x, y, x, y + rowH, linePaint)
                        }
                        y += rowH
                    }
                    // bottom rule + spacing
                    canvas.drawLine(MARGIN, y, MARGIN + TEXT_W, y, linePaint)
                    y += 14f
                }
            }
        }
        pdfDoc.finishPage(page)
        return pdfDoc
    }

    // ── Paint helpers ──────────────────────────────────────────────────────────

    private fun bodyPaint(typeface: Typeface = Typeface.SERIF, sizeSp: Float = 11f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp
        color = Color.BLACK
        this.typeface = typeface
    }

    private fun headingPaint(level: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = when (level) { 1 -> 18f; 2 -> 15f; 3 -> 13f; else -> 12f }
        color = Color.BLACK
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private fun cellPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = Color.DKGRAY
        typeface = Typeface.MONOSPACE
    }

    // ── Output helpers ─────────────────────────────────────────────────────────

    private fun writePdf(context: Context, pdfDoc: PdfDocument, prefix: String): Uri {
        val dir = File(context.cacheDir, "converted_pdfs").also { it.mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return android.net.Uri.fromFile(file)
    }

    private fun getFileName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (col != -1 && cursor.moveToFirst()) cursor.getString(col) else null
        } ?: uri.lastPathSegment ?: "document"

    private fun String.endsWithAny(vararg suffixes: String) = suffixes.any { this.endsWith(it, ignoreCase = true) }
}

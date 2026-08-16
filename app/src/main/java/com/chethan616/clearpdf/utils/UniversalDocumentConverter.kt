package com.chethan616.clearpdf.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
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
        var blocks: List<DocBlock> = emptyList()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    blocks = parseWordXml(zip)
                    break
                }
                zip.closeEntry()
            }
        }
        return blocks.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty document)"))) }
    }

    private fun parseWordXml(stream: InputStream): List<DocBlock> {
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
        var bold = false
        var italic = false
        var paraBuf  = StringBuilder()
        data class Span(val start: Int, val end: Int, val span: Any)
        var spans    = mutableListOf<Span>()

        fun flushPara() {
            val text = paraBuf.toString().trim()
            if (text.isNotEmpty()) {
                val ssb = SpannableStringBuilder(text)
                if (headingLevel > 0) ssb.setSpan(StyleSpan(Typeface.BOLD), 0, ssb.length, 0)
                spans.forEach { s -> ssb.setSpan(s.span, s.start.coerceAtMost(ssb.length), s.end.coerceAtMost(ssb.length), 0) }
                val spaceAfter = if (headingLevel > 0) 10f else 6f
                result.add(DocBlock.Para(ssb, headingLevel = headingLevel, spaceAfter = spaceAfter))
            }
            paraBuf.clear(); spans.clear(); headingLevel = 0; bold = false; italic = false
        }

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
                    "w:r"     -> if (inPara) { inRun = true; bold = false; italic = false }
                    "w:rPr"   -> if (inRun) inRunProps = true
                    "w:b"     -> if (inRunProps) bold = true
                    "w:i"     -> if (inRunProps) italic = true
                    "w:t"     -> if (inRun && inPara) { /* text event follows */ }
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
        val sharedStrings = mutableListOf<String>()
        val result = mutableListOf<DocBlock>()

        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/sharedStrings.xml") {
                    sharedStrings.addAll(parseSharedStrings(zip))
                }
                zip.closeEntry()
            }
        }
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                    val rows = parseWorksheetRows(zip, sharedStrings)
                    if (rows.isNotEmpty()) result.add(DocBlock.Table(rows))
                    if (result.size >= 3) { zip.closeEntry(); break }
                }
                zip.closeEntry()
            }
        }
        return result.ifEmpty { listOf(DocBlock.Para(SpannableStringBuilder("(empty spreadsheet)"))) }
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

    private fun parseWorksheetRows(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(stream, "UTF-8")
        }
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var inV = false
        val cellBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c"   -> { cellType = parser.getAttributeValue(null, "t") ?: ""; inV = false; cellBuf.clear() }
                    "v", "t" -> inV = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "row" -> if (currentRow.isNotEmpty()) rows.add(currentRow.toList())
                    "c"   -> {
                        val raw = cellBuf.toString()
                        val value = if (cellType == "s") sharedStrings.getOrElse(raw.toIntOrNull() ?: -1) { raw } else raw
                        currentRow.add(value)
                        inV = false
                    }
                    "v", "t" -> inV = false
                }
                XmlPullParser.TEXT -> if (inV) cellBuf.append(parser.text)
            }
            event = parser.next()
            if (rows.size > 500) break
        }
        return rows.take(500)
    }

    // ── PPTX ───────────────────────────────────────────────────────────────────

    private fun parsePptx(bytes: ByteArray): List<DocBlock> {
        val result = mutableListOf<DocBlock>()
        var slideNum = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    slideNum++
                    val slide = parseSlideXml(zip, slideNum)
                    result.addAll(slide)
                }
                zip.closeEntry()
            }
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
        val rows = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                HSSFWorkbook(stream).use { wb ->
                    wb.getSheetAt(0).map { row ->
                        row.map { cell -> cell.toString() }
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()
        val paint = bodyPaint()
        val blocks = if (rows.isNotEmpty()) listOf(DocBlock.Table(rows)) else listOf(DocBlock.Para(SpannableStringBuilder("(empty)")))
        return writePdf(context, renderBlocks(blocks, paint), "Xls")
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
            val lineSpacingMult: Float = 1.25f
        ) : DocBlock()
        data class Table(val rows: List<List<String>>) : DocBlock()
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
                    if (block.text.isEmpty()) { y += 8f; continue }
                    val paint = if (block.headingLevel > 0) headingPaint(block.headingLevel) else defaultPaint
                    val layout = StaticLayout.Builder
                        .obtain(block.text, 0, block.text.length, paint, TEXT_W)
                        .setLineSpacing(2f, block.lineSpacingMult)
                        .setIncludePad(false)
                        .build()
                    ensureRoom(layout.height.toFloat())
                    canvas.save(); canvas.translate(MARGIN, y); layout.draw(canvas); canvas.restore()
                    y += layout.height + block.spaceAfter
                }
                is DocBlock.Table -> {
                    if (block.rows.isEmpty()) continue
                    val maxCols = block.rows.maxOf { it.size }.coerceAtLeast(1).coerceAtMost(8)
                    val colW = TEXT_W / maxCols
                    val cellPaint = cellPaint()
                    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(80, 0, 0, 0); strokeWidth = 0.5f
                    }
                    val rowH = 18f

                    for (row in block.rows.take(300)) {
                        ensureRoom(rowH + 2f)
                        // horizontal top rule
                        canvas.drawLine(MARGIN, y, MARGIN + TEXT_W, y, linePaint)
                        row.take(maxCols).forEachIndexed { colIdx, cell ->
                            val x = MARGIN + colIdx * colW
                            val clipped = if (cell.length > 24) cell.take(22) + "…" else cell
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

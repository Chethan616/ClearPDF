package com.chethan616.clearpdf.ui.screen

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.docx.DocBlock
import com.chethan616.clearpdf.docx.DocPara
import com.chethan616.clearpdf.docx.DocRaw
import com.chethan616.clearpdf.docx.DocRow
import com.chethan616.clearpdf.docx.DocTable
import com.chethan616.clearpdf.docx.DocxObjects
import com.chethan616.clearpdf.docx.DocxPackage
import com.chethan616.clearpdf.docx.ObjInfo
import com.chethan616.clearpdf.docx.RProps
import com.chethan616.clearpdf.docx.XmlScan
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * A Word document as a PDF, drawn from the Word editor's own page layout: the same [DocxLook]
 * styles, list labels, pictures, tables and Word's page breaks ([flatten]) the editor shows, laid
 * out with Compose's text engine at one unit per point and drawn straight onto [PdfDocument] pages,
 * so the text stays real, selectable text.
 *
 * This replaced the old web-preview conversion for the editor's PDF and Print buttons, which
 * produced the previous viewer's look rather than the page the person was reading. Where our fonts
 * run longer than Word's and a page overflows, the content carries on to a new page, breaking
 * between lines, pictures and table rows.
 */
internal object DocxPdfWriter {
    fun write(context: Context, pkg: DocxPackage, blocks: List<DocBlock>, out: File): Boolean =
        runCatching { Exporter(context, pkg, blocks).run(out); true }
            .onFailure { Log.w("DocxPdf", "Couldn't draw the document as a PDF", it) }
            .getOrDefault(false)
}

/** Something laid out: its height in points, where a page may end inside it, and how to draw it at (0, 0). */
private class Laid(val height: Float, val breaks: FloatArray, val draw: DrawScope.() -> Unit)

private class Exporter(context: Context, private val pkg: DocxPackage, private val blocks: List<DocBlock>) {
    private val density = Density(1f, 1f)
    private val look = DocxLook(pkg, false, 1f, 1f, DocxLook.carlito())
    private val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 0)
    private val labels = numberingLabels(pkg, blocks)
    private val setup = pkg.page
    private val pageW = (setup.width / 20f).coerceAtLeast(72f)
    private val pageH = (setup.height / 20f).coerceAtLeast(72f)
    private val left = setup.left.coerceAtLeast(0) / 20f
    private val right = setup.right.coerceAtLeast(0) / 20f
    private val top = setup.top.coerceAtLeast(0) / 20f
    private val bottom = setup.bottom.coerceAtLeast(0) / 20f
    private val textW = (pageW - left - right).coerceAtLeast(72f)
    private val scope = CanvasDrawScope()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun run(out: File) {
        val flat = flatten(blocks, pkg.styles, splitParas = true)
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        var number = 0
        var y = 0f
        var fresh = true
        val limit = pageH - bottom

        fun newPage() {
            page?.let(doc::finishPage)
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.roundToInt(), pageH.roundToInt(), number).create())
            y = top
            fresh = true
        }

        fun slice(l: Laid, from: Float, to: Float) {
            val p = page ?: return
            val at = y
            scope.draw(density, LayoutDirection.Ltr, Canvas(p.canvas), Size(pageW, pageH)) {
                clipRect(0f, at, pageW, at + (to - from)) {
                    translate(left, at - from) { l.draw(this) }
                }
            }
            y += to - from
            fresh = false
        }

        // Whole when it fits; otherwise up to the last line (or picture, or row) that does, and the
        // rest on the next page. Something taller than a whole page is cut where the page ends.
        fun place(l: Laid) {
            var from = 0f
            while (l.height - from > 0.01f) {
                val avail = limit - y
                if (l.height - from <= avail + 0.5f) { slice(l, from, l.height); return }
                var cut = -1f
                for (b in l.breaks) if (b > from + 0.5f && b - from <= avail && b > cut) cut = b
                when {
                    cut > 0f -> { slice(l, from, cut); from = cut; newPage() }
                    !fresh -> newPage()
                    else -> {
                        val to = from + avail.coerceAtLeast(1f)
                        slice(l, from, to); from = to; newPage()
                    }
                }
            }
        }

        for (item in flat.items) when (item) {
            is DocItem.Top -> newPage()
            // Word's own page break; skipped when an overflow has just started a page anyway.
            is DocItem.Gap -> if (!fresh) newPage()
            is DocItem.Bottom -> Unit
            is DocItem.Para -> place(
                para(item.para, look, textW, null, item.range, item.spaceTop && item.first, item.spaceBottom && item.last)
            )
            is DocItem.TRow -> place(row(item.table, item.row, item.rowIndex, look, textW))
        }
        if (page == null) newPage()
        page?.let(doc::finishPage)
        out.parentFile?.mkdirs()
        try {
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** One paragraph, as [ParagraphBlock] lays it out: pictures, text boxes, label + text, rule. */
    private fun para(
        p: DocPara,
        lk: DocxLook,
        width: Float,
        cell: RProps?,
        range: IntRange?,
        spaceTop: Boolean,
        spaceBottom: Boolean
    ): Laid {
        val pl = lk.para(p, cell)
        val full = lk.annotate(p, pl, cell)
        val annotated = if (range == null) full else full.subSequence(range.first, (range.last + 1).coerceAtMost(full.length))
        val objects = p.fmts.withIndex()
            .filter { (i, f) -> f.obj != null && (range == null || i in range) }
            .map { DocxObjects.info(it.value.obj!!) }
        val pictures = objects.filter { it.imageRid != null }
        val boxes = objects.filter { it.textBox.isNotEmpty() }
        val blank = annotated.text.all { it == '​' || it.isWhitespace() }
        val first = range == null || range.first == 0
        val label = if (first) labels[p.id] else null
        val rtl = pl.rtl
        val dir = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        val padStart = pl.padStart.value
        val padEnd = pl.padEnd.value
        val contentW = (width - padStart - padEnd).coerceAtLeast(8f)
        val contentX = if (rtl) padEnd else padStart
        val before = if (spaceTop) pl.before.value else 0f
        val after = if (spaceBottom) pl.after.value else 0f

        class Part(val x: Float, val y: Float, val l: Laid)
        val parts = ArrayList<Part>()
        val breaks = ArrayList<Float>()
        var y = before

        for (pic in pictures) {
            val l = picture(pic, lk, contentW, pl.style.textAlign, rtl) ?: continue
            parts += Part(contentX, y, l); y += l.height; breaks += y
        }
        for (box in boxes) {
            val inner = stack(box.textBox, lk, (contentW - 16f).coerceAtLeast(8f))
            val h = inner.height + 16f
            val ink = lk.ink.copy(0.22f)
            val l = Laid(h + 8f, floatArrayOf()) {
                drawRoundRect(ink, Offset(0f, 4f), Size(contentW, h), CornerRadius(4f), style = Stroke(0.5f))
                translate(8f, 12f) { inner.draw(this) }
            }
            parts += Part(contentX, y, l); y += l.height; breaks += y
        }
        if (!(blank && (pictures.isNotEmpty() || boxes.isNotEmpty()))) {
            val labelW = if (label != null) pl.hanging.value else 0f
            val bodyW = (contentW - labelW).coerceAtLeast(8f)
            val text = measurer.measure(
                annotated,
                if (first) pl.style else pl.style.copy(textIndent = null),
                constraints = Constraints.fixedWidth(bodyW.roundToInt().coerceAtLeast(1)),
                layoutDirection = dir
            )
            val lab = if (label != null && labelW >= 1f) measurer.measure(
                AnnotatedString(label),
                pl.style.copy(textAlign = TextAlign.Start, textIndent = null),
                overflow = TextOverflow.Clip,
                maxLines = 1,
                constraints = Constraints.fixedWidth(labelW.roundToInt().coerceAtLeast(1)),
                layoutDirection = dir
            ) else null
            // Label and text share their first baseline, like the editor's alignByBaseline row.
            val base = maxOf(text.firstBaseline, lab?.firstBaseline ?: 0f)
            val ty = base - text.firstBaseline
            val ly = base - (lab?.firstBaseline ?: 0f)
            val rowH = maxOf(ty + text.size.height, if (lab != null) ly + lab.size.height else 0f)
            val textX = if (rtl) contentX else contentX + labelW
            val labelX = if (rtl) contentX + bodyW else contentX
            val rowY = y
            for (i in 0 until text.lineCount) breaks += rowY + ty + text.getLineBottom(i)
            parts += Part(0f, y, Laid(rowH, floatArrayOf()) {
                drawText(text, topLeft = Offset(textX, ty))
                lab?.let { drawText(it, topLeft = Offset(labelX, ly)) }
            })
            y += rowH
        }
        if (pl.borderBottom && (range == null || range.last >= p.text.length - 1)) {
            val ink = lk.ink.copy(0.55f)
            parts += Part(contentX, y, Laid(4f, floatArrayOf()) {
                drawLine(ink, Offset(0f, 3.5f), Offset(contentW, 3.5f), 1f)
            })
            y += 4f
        }
        val contentBottom = y
        y += after
        val shading = pl.shading
        return Laid(y, breaks.toFloatArray()) {
            if (shading != null && contentBottom > before) {
                drawRect(shading, Offset(0f, before), Size(width, contentBottom - before))
            }
            for (part in parts) translate(part.x, part.y) { part.l.draw(this) }
        }
    }

    /** A picture at its size in the document, aligned like [DocPicture], with 2 pt above and below. */
    private fun picture(info: ObjInfo, lk: DocxLook, maxWidth: Float, paraAlign: TextAlign?, rtl: Boolean): Laid? {
        val rid = info.imageRid ?: return null
        val bytes = pkg.image(rid) ?: return null
        val want = if (info.widthPt > 0f) info.widthPt * lk.scale else maxWidth
        val w = want.coerceAtMost(maxWidth.coerceAtLeast(24f)).coerceAtLeast(12f)
        // About 150 dpi: sharp in print without a camera photo's full weight.
        val target = (w * 2.1f).roundToInt().coerceIn(64, 1800)
        val pic = DocImages.decode("$rid@${System.identityHashCode(pkg)}@pdf$target", bytes, target)
        val ratio = if (info.widthPt > 0f && info.heightPt > 0f) info.heightPt / info.widthPt
        else pic?.image?.let { it.height.toFloat() / it.width.coerceAtLeast(1) } ?: 0.6f
        val h = w * ratio.coerceIn(0.05f, 20f)
        val start = when (info.hAlign) {
            "center" -> 0.5f
            "right", "outside" -> 1f
            "left", "inside" -> 0f
            else -> when (paraAlign) {
                TextAlign.Center -> 0.5f
                TextAlign.End, TextAlign.Right -> 1f
                else -> 0f
            }
        }
        val x = (maxWidth - w) * (if (rtl) 1f - start else start)
        val bmp = pic?.image?.asAndroidBitmap()
        return Laid(h + 4f, floatArrayOf()) {
            if (bmp != null) drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, null, RectF(x, 2f, x + w, 2f + h), bitmapPaint) }
        }
    }

    /** Blocks one under another (table cells, text boxes, content controls). */
    private fun stack(list: List<DocBlock>, lk: DocxLook, width: Float, cell: RProps? = null): Laid {
        val parts = ArrayList<Pair<Float, Laid>>()
        val breaks = ArrayList<Float>()
        var y = 0f
        fun add(l: Laid) {
            parts += y to l
            for (b in l.breaks) breaks += y + b
            y += l.height
            breaks += y
        }
        for (b in list) when (b) {
            is DocPara -> add(para(b, lk, width, cell, null, spaceTop = true, spaceBottom = true))
            is DocTable -> for ((i, r) in b.rows.withIndex()) add(row(b, r, i, lk, width))
            is DocRaw -> if (b.preview.isNotEmpty()) add(stack(b.preview, lk, width, cell))
        }
        return Laid(y, breaks.toFloatArray()) { for ((py, l) in parts) translate(0f, py) { l.draw(this) } }
    }

    /** One table row, as [TableRowBlock] lays it out: Word's grid, fills, borders, cell alignment. */
    private fun row(table: DocTable, row: DocRow, rowIndex: Int, lk: DocxLook, availWidth: Float): Laid {
        val tl = tableLookOf(table, pkg, lk, availWidth.dp)
        val cellLook = lk.scaled(tl.scale)
        val lastRow = rowIndex == table.rows.lastIndex
        val gridBefore = if (row.trPr.isEmpty()) 0
        else XmlScan.scan(row.trPr).children.firstOrNull()?.child("gridBefore")?.attr(row.trPr, "val")?.toIntOrNull() ?: 0
        val header = tl.firstRow && rowIndex == 0
        val banded = tl.bands && tl.bandFill != null && !header && (rowIndex - if (tl.firstRow) 1 else 0) % 2 == 0
        val line = lk.ink.copy(0.55f)
        val tw = tl.width.value
        fun frac(from: Int, span: Int): Float {
            var f = 0f
            for (i in from until (from + span).coerceAtMost(tl.fractions.size)) f += tl.fractions[i]
            return f.coerceAtLeast(0.0001f)
        }
        val x0 = when (tl.jc) {
            "center" -> (availWidth - tw) / 2f
            "right", "end" -> availWidth - tw
            else -> tl.indent.value
        }
        val padH = 5.4f * cellLook.scale

        class Cell(val x: Float, val w: Float, val fill: Color?, val content: Laid?, val vAlign: String?, val vContinue: Boolean, val last: Boolean)
        val cells = ArrayList<Cell>()
        var col = gridBefore
        var x = x0 + if (gridBefore > 0) tw * frac(0, gridBefore) else 0f
        for ((ci, c) in row.cells.withIndex()) {
            val cp = cellProps(c.tcPr)
            val fill = cp.fill ?: if (header) tl.headerFill else if (banded) tl.bandFill else null
            val cw = tw * frac(col, cp.span)
            val content = if (cp.vContinue) null
            else stack(c.blocks, cellLook, (cw - 2f * padH).coerceAtLeast(4f), if (header) tl.headerR else null)
            cells += Cell(x, cw, lk.fillOf(fill), content, cp.vAlign, cp.vContinue, ci == row.cells.lastIndex)
            x += cw
            col += cp.span
        }
        val rowH = (cells.maxOfOrNull { (it.content?.height ?: 0f) + 2f } ?: 2f).coerceAtLeast(2f)
        return Laid(rowH, floatArrayOf()) {
            for (c in cells) {
                c.fill?.let { drawRect(it, Offset(c.x, 0f), Size(c.w, rowH)) }
                c.content?.let { content ->
                    val free = rowH - 2f - content.height
                    val dy = 1f + when (c.vAlign) {
                        "center" -> free / 2f
                        "bottom" -> free
                        else -> 0f
                    }
                    translate(c.x + padH, dy) { content.draw(this) }
                }
                if (tl.borders) {
                    val sw = 0.5f
                    if (!c.vContinue) drawLine(line, Offset(c.x, 0f), Offset(c.x + c.w, 0f), sw)
                    drawLine(line, Offset(c.x, 0f), Offset(c.x, rowH), sw)
                    if (c.last) drawLine(line, Offset(c.x + c.w, 0f), Offset(c.x + c.w, rowH), sw)
                    if (lastRow) drawLine(line, Offset(c.x, rowH), Offset(c.x + c.w, rowH), sw)
                }
            }
        }
    }
}

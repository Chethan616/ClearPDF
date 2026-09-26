package com.chethan616.clearpdf.utils.xlsx

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/**
 * Prints a workbook to PDF with the same [XlsxPainter] the viewer uses, so fills, fonts, borders,
 * merges and alignment come out exactly as on screen. Each visible sheet is laid out on landscape
 * A4 pages: scaled down (to a floor) to fit its used width, then tiled into column bands if still
 * wider, and paginated by row height.
 */
object XlsxPdfRenderer {
    private const val PageW = 842
    private const val PageH = 595
    private const val Margin = 28f
    private const val MinScale = 0.5f
    private const val TitleH = 22f

    fun render(wb: XlsxWorkbook): PdfDocument? {
        val sheets = wb.sheets.filter { !it.hidden && it.maxRow >= 0 }
        if (sheets.isEmpty()) return null
        val doc = PdfDocument()
        val painter = XlsxPainter(wb, PaintTheme(false, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFD9D9D9.toInt()))
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f; color = 0xFF5E6068.toInt(); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        var pageNo = 1
        for (sheet in sheets) {
            val layout = SheetLayout(sheet, extraRows = 0, extraCols = 0)
            val usedCols = (sheet.maxCol + 1).coerceIn(1, layout.nCols)
            val usedRows = (sheet.maxRow + 1).coerceIn(1, layout.nRows)
            // Pixels at 96 dpi → PDF points at 72.
            val base = 0.75f
            val usedW = layout.colX[usedCols] * base
            val availW = PageW - 2 * Margin
            val scale = base * (availW / usedW).coerceIn(MinScale, 1f)
            // Column bands that each fit the page width.
            val bands = ArrayList<IntRange>()
            var start = 0
            while (start < usedCols) {
                var end = start
                while (end + 1 < usedCols && (layout.colX[end + 2] - layout.colX[start]) * scale <= availW) end++
                bands.add(start..end)
                start = end + 1
            }
            for (band in bands) {
                var r = 0
                while (r < usedRows) {
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(PageW, PageH, pageNo++).create())
                    val canvas = page.canvas
                    canvas.drawColor(0xFFFFFFFF.toInt())
                    canvas.drawText(sheet.name, Margin, Margin + 10f, titlePaint)
                    var y = Margin + TitleH
                    val bandW = (layout.colX[band.last + 1] - layout.colX[band.first]) * scale
                    val firstOnPage = r
                    while (r < usedRows) {
                        val h = layout.rowH(r) * scale
                        if (y + h > PageH - Margin && r > firstOnPage) break
                        if (h > 0f) {
                            canvas.save()
                            canvas.translate(Margin, y)
                            canvas.clipRect(0f, Margin + TitleH - y, bandW, PageH - Margin - y)
                            painter.drawRow(canvas, layout, r, scale, layout.colX[band.first] * scale, bandW, freeze = false) { m ->
                                r == maxOf(m.r1, firstOnPage)
                            }
                            canvas.restore()
                        }
                        y += h
                        r++
                    }
                    doc.finishPage(page)
                }
            }
        }
        return doc
    }
}

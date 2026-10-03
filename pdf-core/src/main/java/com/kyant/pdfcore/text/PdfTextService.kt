package com.kyant.pdfcore.text

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.min

data class PdfTextBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    // Per-text-character normalized x bounds, parallel to [text]. Enable word-precise
    // highlighting of a matched substring instead of the whole line.
    val charLefts: FloatArray = FloatArray(0),
    val charRights: FloatArray = FloatArray(0)
)

/** A search hit as a normalized rect around the exact matched word(s), not the whole line. */
data class PdfSearchMatch(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

interface PdfTextService {
    fun extractPage(context: Context, uri: Uri, pageIndex: Int): List<PdfTextBlock>
    fun searchAll(context: Context, uri: Uri, query: String, pageCount: Int): List<PdfSearchMatch>
}

class PdfTextServiceImpl : PdfTextService {

    override fun extractPage(context: Context, uri: Uri, pageIndex: Int): List<PdfTextBlock> {
        PdfBox.ensureInitialized(context)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@runCatching emptyList()
                    extractPageBlocks(doc, pageIndex)
                }
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    override fun searchAll(
        context: Context,
        uri: Uri,
        query: String,
        pageCount: Int
    ): List<PdfSearchMatch> {
        if (query.isBlank()) return emptyList()
        PdfBox.ensureInitialized(context)
        val lower = query.trim().lowercase()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    buildList {
                        for (pageIdx in 0 until min(doc.numberOfPages, pageCount)) {
                            extractPageBlocks(doc, pageIdx).forEach { block ->
                                addAll(block.matchRects(lower, pageIdx))
                            }
                        }
                    }
                }
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun extractPageBlocks(doc: PDDocument, pageIndex: Int): List<PdfTextBlock> {
        val page = doc.getPage(pageIndex)
        val rotation = page.rotation
        val box = page.cropBox ?: page.mediaBox ?: return emptyList()
        val pageW: Float
        val pageH: Float
        if (rotation == 90 || rotation == 270) {
            pageW = box.height
            pageH = box.width
        } else {
            pageW = box.width
            pageH = box.height
        }
        if (pageW <= 0f || pageH <= 0f) return emptyList()

        val stripper = PositionCapturingStripper().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        runCatching { stripper.getText(doc) }

        return groupPositionsIntoLines(stripper.positions, pageW, pageH, pageIndex)
    }

    private fun groupPositionsIntoLines(
        positions: List<TextPosition>,
        pageWidth: Float,
        pageHeight: Float,
        pageIndex: Int
    ): List<PdfTextBlock> {
        if (positions.isEmpty()) return emptyList()

        val avgH = positions.map { it.height }.average().toFloat().coerceAtLeast(2f)
        val lineGap = avgH * 0.6f

        // Group by similar Y (baseline) into lines
        val sorted = positions.sortedBy { it.y }
        val lines = mutableListOf<MutableList<TextPosition>>()
        for (pos in sorted) {
            val last = lines.lastOrNull()
            if (last == null || abs(pos.y - last.first().y) > lineGap) {
                lines.add(mutableListOf(pos))
            } else {
                last.add(pos)
            }
        }

        // A "line" above is every glyph sharing a baseline across the FULL page width, so on a
        // two-column page the left and right columns' lines merged into one block ("...end of left
        // sentence start of right sentence..."). Split each line wherever the horizontal gap between
        // consecutive glyphs is far wider than any word space (> ~3x the line's average glyph
        // width): column gutters and wide table gaps become separate blocks, ordinary word spacing
        // (even justified) never reaches that.
        val segments = lines.flatMap { line ->
            val byX = line.sortedBy { it.x }
            val avgW = byX.map { it.width }.filter { it > 0f }.average().toFloat()
                .takeIf { !it.isNaN() && it > 0f } ?: avgH * 0.5f
            val splitGap = avgW * 3f
            val out = mutableListOf<MutableList<TextPosition>>()
            var lastRight = -Float.MAX_VALUE
            for (tp in byX) {
                if (out.isEmpty() || (lastRight > -Float.MAX_VALUE && tp.x - lastRight > splitGap)) {
                    out.add(mutableListOf(tp))
                } else {
                    out.last().add(tp)
                }
                lastRight = maxOf(lastRight, tp.x + tp.width)
            }
            out
        }

        return segments.mapIndexedNotNull { lineIdx, linePositions ->
            val byX = linePositions.sortedBy { it.x }
            val avgGlyphWidth = byX.map { it.width }.filter { it > 0f }.average().toFloat()
                .takeIf { it.isFinite() && it > 0f } ?: 1f
            val naturalGaps = byX.zipWithNext { previous, next -> next.x - (previous.x + previous.width) }
                // Large outliers are word spaces, not a useful estimate of glyph tracking.
                .filter { it.isFinite() && it > 0f && it < avgGlyphWidth * 0.55f }
                .sorted()
            val typicalTracking = naturalGaps.getOrNull(naturalGaps.size / 2) ?: 0f
            // A gap is a word break only when it clearly exceeds both the line's ordinary
            // tracking and a fraction of the average glyph width. Using just the current glyph's
            // width split tracked/justified words (especially after narrow letters) into pieces.
            val inferredWordGap = maxOf(
                avgGlyphWidth * 0.42f,
                (typicalTracking * 1.6f + avgGlyphWidth * 0.08f).coerceAtMost(avgGlyphWidth * 0.85f)
            )
            // Build the line text AND per-character x bounds together so indices stay aligned.
            val sb = StringBuilder()
            val cl = ArrayList<Float>()
            val cr = ArrayList<Float>()
            var lastRight = -Float.MAX_VALUE
            byX.forEach { tp ->
                val u = tp.unicode ?: ""
                if (u.isEmpty()) return@forEach
                val gap = tp.x - lastRight
                if (lastRight > -Float.MAX_VALUE && gap > inferredWordGap) {
                    sb.append(' '); cl.add(lastRight); cr.add(tp.x)
                }
                val x0 = tp.x; val x1 = tp.x + tp.width; val n = u.length
                for (i in u.indices) {
                    sb.append(u[i])
                    cl.add(x0 + (x1 - x0) * i / n)
                    cr.add(x0 + (x1 - x0) * (i + 1) / n)
                }
                lastRight = x1
            }
            val raw = sb.toString()
            val startI = raw.indexOfFirst { !it.isWhitespace() }
            val endI = raw.indexOfLast { !it.isWhitespace() }
            if (startI < 0) return@mapIndexedNotNull null
            val text = raw.substring(startI, endI + 1)
            val charLefts = FloatArray(endI - startI + 1) { (cl[startI + it] / pageWidth).coerceIn(0f, 1f) }
            val charRights = FloatArray(endI - startI + 1) { (cr[startI + it] / pageWidth).coerceIn(0f, 1f) }

            val minX = byX.minOf { it.x }
            val maxX = byX.maxOf { it.x + it.width }
            val minY = byX.minOf { it.y - it.height }.coerceAtLeast(0f)
            val maxY = byX.maxOf { it.y }

            PdfTextBlock(
                id     = "$pageIndex-$lineIdx",
                text   = text,
                left   = (minX / pageWidth).coerceIn(0f, 1f),
                top    = (minY / pageHeight).coerceIn(0f, 1f),
                right  = (maxX / pageWidth).coerceIn(0f, 1f),
                bottom = (maxY / pageHeight).coerceIn(0f, 1f),
                charLefts  = charLefts,
                charRights = charRights
            )
        }
    }
}

/** All occurrences of [lower] (already lower-cased) in this line, as tight word rects. */
fun PdfTextBlock.matchRects(lower: String, pageIndex: Int): List<PdfSearchMatch> {
    if (lower.isEmpty() || charLefts.isEmpty()) return emptyList()
    val bt = text.lowercase()
    val out = ArrayList<PdfSearchMatch>()
    var from = 0
    while (true) {
        val idx = bt.indexOf(lower, from)
        if (idx < 0) break
        val endC = idx + lower.length - 1
        if (idx < charLefts.size && endC < charRights.size) {
            out.add(PdfSearchMatch(pageIndex, charLefts[idx], top, charRights[endC], bottom))
        } else {
            out.add(PdfSearchMatch(pageIndex, left, top, right, bottom))
        }
        from = idx + lower.length
    }
    return out
}

private class PositionCapturingStripper : PDFTextStripper() {
    val positions = mutableListOf<TextPosition>()

    override fun processTextPosition(text: TextPosition) {
        if (!text.unicode.isNullOrBlank()) positions.add(text)
    }
}

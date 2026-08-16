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
    val bottom: Float
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val blockId: String
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
                                if (block.text.lowercase().contains(lower)) {
                                    add(PdfSearchMatch(pageIdx, block.id))
                                }
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

        return lines.mapIndexedNotNull { lineIdx, linePositions ->
            val byX = linePositions.sortedBy { it.x }
            val text = buildString {
                var lastRight = -Float.MAX_VALUE
                byX.forEach { tp ->
                    val gap = tp.x - lastRight
                    if (lastRight > -Float.MAX_VALUE && gap > tp.width * 0.4f) append(' ')
                    append(tp.unicode ?: "")
                    lastRight = tp.x + tp.width
                }
            }.trim()

            if (text.isBlank()) return@mapIndexedNotNull null

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
                bottom = (maxY / pageHeight).coerceIn(0f, 1f)
            )
        }
    }
}

private class PositionCapturingStripper : PDFTextStripper() {
    val positions = mutableListOf<TextPosition>()

    override fun processTextPosition(text: TextPosition) {
        if (!text.unicode.isNullOrBlank()) positions.add(text)
    }
}

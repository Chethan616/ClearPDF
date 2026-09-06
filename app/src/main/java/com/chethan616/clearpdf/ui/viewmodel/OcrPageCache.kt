package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import com.kyant.ocrcore.OcrWord
import com.kyant.pdfcore.model.PdfDocument
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Disk cache for on-device OCR results, keyed by document identity + page index.
 * OCR (especially the Tesseract fallback) is too slow to re-run every time a scanned
 * PDF is reopened, so a successful recognition is persisted once under the app cache dir.
 */
internal object OcrPageCache {
    private val json = Json { ignoreUnknownKeys = true }

    private fun docKey(doc: PdfDocument): String {
        val raw = "${doc.uri}|${doc.sizeBytes}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(context: Context, doc: PdfDocument, pageIndex: Int): File {
        val dir = File(context.cacheDir, "ocr_cache/${docKey(doc)}").apply { mkdirs() }
        return File(dir, "page_$pageIndex.json")
    }

    fun read(context: Context, doc: PdfDocument, pageIndex: Int): List<OcrTextBlock>? {
        val file = cacheFile(context, doc, pageIndex)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CachedOcrPage>(file.readText()).blocks.map { it.toOcrBlock() }
        }.getOrNull()
    }

    fun write(context: Context, doc: PdfDocument, pageIndex: Int, blocks: List<OcrTextBlock>) {
        runCatching {
            val page = CachedOcrPage(blocks.map { it.toCached() })
            cacheFile(context, doc, pageIndex).writeText(json.encodeToString(CachedOcrPage.serializer(), page))
        }
    }
}

@Serializable
private data class CachedOcrPage(val blocks: List<CachedOcrBlock>)

@Serializable
private data class CachedOcrBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val charLefts: List<Float>,
    val charRights: List<Float>
)

private fun OcrTextBlock.toCached() = CachedOcrBlock(
    id = id, text = text, left = left, top = top, right = right, bottom = bottom,
    charLefts = charLefts.toList(), charRights = charRights.toList()
)

private fun CachedOcrBlock.toOcrBlock() = OcrTextBlock(
    id = id, text = text, left = left, top = top, right = right, bottom = bottom,
    charLefts = charLefts.toFloatArray(), charRights = charRights.toFloatArray()
)

/**
 * Groups flat OCR word boxes (from [com.kyant.ocrcore.OcrService]) back into line-shaped
 * [OcrTextBlock]s, mirroring [com.kyant.pdfcore.text.PdfTextService]'s character-stream
 * grouping so the rest of the viewer (selection sweep, search, highlight) is fully generic
 * over the two text sources and needs no OCR-specific branching.
 */
internal fun groupOcrWordsIntoBlocks(words: List<OcrWord>, pageIndex: Int): List<OcrTextBlock> {
    if (words.isEmpty()) return emptyList()

    val avgH = words.map { it.bottom - it.top }.average().toFloat().coerceAtLeast(0.005f)
    val lineGap = avgH * 0.6f

    val sorted = words.sortedBy { (it.top + it.bottom) / 2f }
    val lines = mutableListOf<MutableList<OcrWord>>()
    for (w in sorted) {
        val cy = (w.top + w.bottom) / 2f
        val last = lines.lastOrNull()
        val lastCy = last?.let { l -> l.map { (it.top + it.bottom) / 2f }.average().toFloat() }
        if (last == null || lastCy == null || abs(cy - lastCy) > lineGap) {
            lines.add(mutableListOf(w))
        } else {
            last.add(w)
        }
    }

    return lines.mapIndexedNotNull { lineIdx, lineWords ->
        val byX = lineWords.sortedBy { it.left }
        val sb = StringBuilder()
        val cl = ArrayList<Float>()
        val cr = ArrayList<Float>()
        var lastRight = -1f
        byX.forEach { w ->
            if (lastRight >= 0f) {
                // Words are already tokenized by the OCR engine — always separate them with
                // a space, unlike the PDFBox char-stream path which must infer word breaks.
                sb.append(' '); cl.add(lastRight); cr.add(w.left)
            }
            val n = w.text.length.coerceAtLeast(1)
            for (i in w.text.indices) {
                sb.append(w.text[i])
                cl.add(w.left + (w.right - w.left) * i / n)
                cr.add(w.left + (w.right - w.left) * (i + 1) / n)
            }
            lastRight = w.right
        }
        if (sb.isEmpty()) return@mapIndexedNotNull null
        OcrTextBlock(
            id = "$pageIndex-ocr-$lineIdx",
            text = sb.toString(),
            left = byX.minOf { it.left },
            top = byX.minOf { it.top },
            right = byX.maxOf { it.right },
            bottom = byX.maxOf { it.bottom },
            charLefts = cl.toFloatArray(),
            charRights = cr.toFloatArray()
        )
    }
}

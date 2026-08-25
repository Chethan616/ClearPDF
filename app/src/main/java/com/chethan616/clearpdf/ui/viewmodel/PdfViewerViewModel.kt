package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.PdfServiceLocator
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.domain.usecase.OpenPdfUseCase
import com.chethan616.clearpdf.ui.utils.AppDispatchers
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.chethan616.clearpdf.utils.UniversalDocumentConverter
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.raster.PdfRasterizer
import com.kyant.pdfcore.security.PdfSecurityService
import com.kyant.pdfcore.text.PdfTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── UI model — kept identical to old OcrTextBlock so screen code compiles unchanged ──
data class OcrTextBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val charLefts: FloatArray = FloatArray(0),
    val charRights: FloatArray = FloatArray(0)
)

/** A word- or line-precise selection inside one extracted text block. [end] is exclusive. */
data class OcrTextRange(
    val blockId: String,
    val start: Int,
    val end: Int
)

/** A search hit as a normalized rect around the EXACT matched word(s), not the whole line. */
data class FindMatch(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class NormalizedPoint(val x: Float, val y: Float)

sealed class ExportOverlay {
    data class Stroke(
        val points: List<NormalizedPoint>,
        val colorArgb: Int,
        val widthNorm: Float,
        val alpha: Float
    ) : ExportOverlay()

    data class RectShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val alpha: Float,
        val filled: Boolean
    ) : ExportOverlay()

    data class OvalShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val alpha: Float,
        val filled: Boolean
    ) : ExportOverlay()

    data class LineShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val widthNorm: Float,
        val alpha: Float,
        val arrowHead: Boolean
    ) : ExportOverlay()

    data class ImageStamp(
        val bitmap: Bitmap,
        val start: NormalizedPoint,
        val end: NormalizedPoint
    ) : ExportOverlay()

    /** Inserted vector text. [position] is the top-left; baseline is derived on export. */
    data class TextStamp(
        val position: NormalizedPoint,
        val text: String,
        val colorArgb: Int,
        val fontSizeNorm: Float
    ) : ExportOverlay()

    /** A real PDF sticky-note annotation anchored at [position] (top-left of icon). */
    data class NoteStamp(
        val position: NormalizedPoint,
        val text: String,
        val colorArgb: Int
    ) : ExportOverlay()
}

data class PdfViewerUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    // True while a password-protected PDF is being unlocked + loaded, so the viewer can show the
    // padlock "decrypting" animation instead of the plain opening fill. Cleared on every terminal
    // outcome (opened / wrong password / error).
    val decrypting: Boolean = false,
    val errorMessage: String? = null,
    val passwordRequired: Boolean = false,
    val passwordAttemptFailed: Boolean = false,
    val passwordUri: Uri? = null,
    val pageBitmaps: List<Bitmap?> = emptyList(),
    val document: PdfDocument? = null,
    // The uri the user actually opened — a plain PDF's own uri, or the ORIGINAL .docx/.pptx for a
    // converted document (unlike [document].uri, which is the converted temp PDF). Lets Share offer
    // "export as the original file" vs "export as PDF".
    val originalUri: Uri? = null,
    val sizeBytes: Long = -1,
    val ocrBlocksByPage: Map<Int, List<OcrTextBlock>> = emptyMap(),
    val ocrPagesInProgress: Set<Int> = emptySet(),
    val selectedOcrBlockIdsByPage: Map<Int, Set<String>> = emptyMap(),
    val selectedOcrRangesByPage: Map<Int, List<OcrTextRange>> = emptyMap(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val exportError: String? = null,
    val lastExportedUri: Uri? = null,
    val findQuery: String = "",
    val findMatches: List<FindMatch> = emptyList(),
    val currentMatchIndex: Int = -1
)

class PdfViewerViewModel(private val openPdfUseCase: OpenPdfUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val renderingPages = mutableSetOf<Pair<Uri, Int>>()
    private val renderedPageWidths = mutableMapOf<Int, Int>()
    private val textLoadingPages = mutableSetOf<Int>()

    // Text extraction loads the ENTIRE document with PdfBox per page (PdfTextService.extractPage), so
    // several pages extracting at once — the eager first-pages pass, or a fast scroll — stack multiple
    // full-document copies in RAM and can OOM a large / decrypted PDF. This serializes them to one at a
    // time, capping the peak to a single in-flight copy. Pages still extract lazily; they just queue.
    private val textExtractionMutex = Mutex()

    private val textService = PdfServiceLocator.pdfTextService

    companion object {
        private const val DEFAULT_RENDER_WIDTH = 1200
        private const val MIN_RENDER_WIDTH = 720
        private const val CACHE_RADIUS = 2
    }

    fun openPdf(context: Context, uri: Uri, password: String? = null) {
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        recycleBitmaps(_uiState.value.pageBitmaps)
        renderingPages.clear()
        renderedPageWidths.clear()
        textLoadingPages.clear()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            // A supplied password means this call is the actual unlock → drive the decrypt animation.
            decrypting = password != null,
            errorMessage = null,
            document = null,
            pageBitmaps = emptyList(),
            passwordRequired = false,
            passwordAttemptFailed = false,
            passwordUri = null,
            ocrBlocksByPage = emptyMap(),
            ocrPagesInProgress = emptySet()
        )
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val sourceUri = withContext(Dispatchers.IO) {
                    when {
                        password != null -> {
                            val decrypted = PdfSecurityService.decryptToCache(context, uri, password)
                            FileProvider.getUriForFile(context, "${context.packageName}.provider", decrypted)
                        }
                        UniversalDocumentConverter.isPdf(context, uri) &&
                            PdfSecurityService.isPasswordProtected(context, uri) -> {
                            throw PdfSecurityService.PasswordRequiredException()
                        }
                        else -> uri
                    }
                }
                val (doc, _) = withContext(Dispatchers.IO) {
                    openDocumentWithFallback(context, sourceUri)
                }
                val displayName = queryFileName(context, uri) ?: doc.name
                _uiState.value = _uiState.value.copy(
                    fileName = displayName,
                    pageCount = doc.pageCount,
                    currentPage = 0,
                    isLoading = false,
                    decrypting = false,
                    passwordRequired = false,
                    passwordAttemptFailed = false,
                    passwordUri = null,
                    document = doc,
                    // The original selection, so Share can offer the source file itself. For a
                    // password-opened PDF this is still the encrypted original the user picked.
                    originalUri = uri,
                    sizeBytes = doc.sizeBytes,
                    pageBitmaps = List(doc.pageCount) { null },
                    ocrBlocksByPage = emptyMap(),
                    ocrPagesInProgress = emptySet(),
                    selectedOcrBlockIdsByPage = emptyMap(),
                    selectedOcrRangesByPage = emptyMap(),
                    isExporting = false,
                    exportMessage = null,
                    exportError = null,
                    lastExportedUri = null
                )
                // The ORIGINAL uri, deliberately — not `openedUri`.
                //
                // `openedUri` is whatever we ended up rendering: for a plain PDF that is the same
                // file, but for a .docx/.pptx it is the converted temp PDF, and for an unreadable
                // descriptor it is a timestamped mirror in app storage. Storing that broke recents
                // three ways at once. The entry carried the original *name* ("report.docx") beside a
                // converted *uri*, so tapping it re-queried DISPLAY_NAME off the temp file and got
                // the converter's own name back — the "unknown.pdf" bug. `docKindOf` then read that
                // name and routed a Word document to the plain PDF path. And because every open
                // minted a fresh temp file with a fresh uri, `addRecent`'s dedupe never matched, so
                // opening one file five times left five rows.
                //
                // The original uri is the identity of the thing the user opened. Re-opening from
                // recents re-runs the conversion, which is correct: the converted file is a cache
                // artifact, not the document.
                RecentFilesManager.addRecent(context, RecentFile(
                    name = displayName,
                    uriString = uri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    sizeBytes = doc.sizeBytes
                ))
                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }
                renderPage(context, 0, DEFAULT_RENDER_WIDTH)
                // Eagerly load text for first 5 pages without waiting for bitmaps
                val eager = minOf(5, doc.pageCount)
                for (p in 0 until eager) loadTextPage(context, doc, p)
            } catch (e: PdfSecurityService.PasswordRequiredException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    decrypting = false,
                    passwordRequired = true,
                    passwordAttemptFailed = password != null,
                    passwordUri = uri,
                    errorMessage = null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    decrypting = false,
                    errorMessage = context.getString(R.string.viewer_open_failed)
                )
            }
        }
    }

    private fun openDocumentWithFallback(context: Context, sourceUri: Uri): Pair<PdfDocument, Uri> {
        val targetUri = if (!UniversalDocumentConverter.isPdf(context, sourceUri)) {
            UniversalDocumentConverter.convertToPdf(context, sourceUri)
        } else {
            sourceUri
        }
        val sourceDescriptorSize = tryReadDescriptorSize(context, targetUri)
        if (sourceDescriptorSize == 0L) throw IllegalStateException("Selected document is empty")

        val primaryUri = if (sourceDescriptorSize != null) targetUri
        else mirrorPdfToAppStorage(context, targetUri)

        return try {
            openPdfUseCase.open(context, primaryUri) to primaryUri
        } catch (primaryError: Exception) {
            if (primaryUri != targetUri) throw primaryError
            val mirroredUri = mirrorPdfToAppStorage(context, targetUri)
            val mirroredSize = tryReadDescriptorSize(context, mirroredUri)
            if (mirroredSize == 0L) throw IllegalStateException("Selected document is empty")
            if (mirroredSize == null) throw IllegalStateException("Unable to access selected document")
            openPdfUseCase.open(context, mirroredUri) to mirroredUri
        }
    }

    private fun tryReadDescriptorSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrElse { null }

    private fun mirrorPdfToAppStorage(context: Context, sourceUri: Uri): Uri {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Unable to access selected document")
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
        val mirrorDir = File(baseDir, "imported_pdfs")
        if (!mirrorDir.exists() && !mirrorDir.mkdirs()) {
            throw IllegalStateException("Unable to prepare local document storage")
        }
        val sourceName = queryFileName(context, sourceUri)?.ifBlank { null }
            ?: "Imported_${System.currentTimeMillis()}.pdf"
        val sanitized = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetName = if (sanitized.lowercase(Locale.ROOT).endsWith(".pdf")) sanitized
        else "$sanitized.pdf"
        val targetFile = File(mirrorDir, "${System.currentTimeMillis()}_$targetName")
        input.use { it.copyTo(FileOutputStream(targetFile)) }
        if (targetFile.length() == 0L) {
            targetFile.delete()
            throw IllegalStateException("Selected document is empty")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
    }

    fun renderPage(context: Context, pageIndex: Int, targetWidthPx: Int = DEFAULT_RENDER_WIDTH) {
        val state = _uiState.value
        val doc = state.document ?: return
        if (pageIndex !in state.pageBitmaps.indices) return

        val renderWidth = targetWidthPx.coerceAtLeast(MIN_RENDER_WIDTH)
        val cachedBitmap = state.pageBitmaps[pageIndex]
        val cachedWidth = renderedPageWidths[pageIndex] ?: 0
        if (cachedBitmap != null && !cachedBitmap.isRecycled && cachedWidth >= renderWidth) return

        val documentUri = doc.uri
        val renderKey = documentUri to pageIndex
        if (!renderingPages.add(renderKey)) return

        viewModelScope.launch {
            try {
                val bitmap = withContext(AppDispatchers.pdf) {
                    // Guard the whole render — a large page can OOM (an Error, not an Exception), and
                    // this coroutine has only a finally, so an uncaught throwable here crashed the app
                    // mid-scroll. Returning null instead just leaves the page as a spinner placeholder.
                    runCatching { openPdfUseCase.renderPage(doc, pageIndex, renderWidth) }.getOrNull()
                }
                val currentState = _uiState.value
                if (currentState.document?.uri != documentUri) return@launch
                if (pageIndex !in currentState.pageBitmaps.indices) return@launch

                val bitmaps = currentState.pageBitmaps.toMutableList()
                val previous = bitmaps[pageIndex]
                if (previous != null && previous != bitmap && !previous.isRecycled) previous.recycle()
                bitmaps[pageIndex] = bitmap
                if (bitmap == null) renderedPageWidths.remove(pageIndex)
                else renderedPageWidths[pageIndex] = renderWidth

                bitmaps.forEachIndexed { index, existing ->
                    if (existing != null && index != pageIndex && abs(index - currentState.currentPage) > CACHE_RADIUS) {
                        if (!existing.isRecycled) existing.recycle()
                        bitmaps[index] = null
                        renderedPageWidths.remove(index)
                    }
                }
                _uiState.value = currentState.copy(pageBitmaps = bitmaps)

                // Load text for page if not already done (async, IO thread)
                loadTextPage(context, doc, pageIndex)
            } finally {
                renderingPages.remove(renderKey)
            }
        }
    }

    private fun loadTextPage(context: Context, doc: PdfDocument, pageIndex: Int) {
        val state = _uiState.value
        if (state.document?.uri != doc.uri) return
        if (state.ocrBlocksByPage.containsKey(pageIndex)) return
        if (!textLoadingPages.add(pageIndex)) return

        _uiState.value = _uiState.value.copy(
            ocrPagesInProgress = textLoadingPages.toSet()
        )

        viewModelScope.launch {
            val blocks = withContext(AppDispatchers.pdf) {
                // One full-document PdfBox parse at a time — see [textExtractionMutex].
                val pdfBlocks = textExtractionMutex.withLock {
                    runCatching {
                        textService.extractPage(context, doc.uri, pageIndex)
                    }.getOrElse { emptyList() }
                }
                if (pdfBlocks.isNotEmpty()) {
                    pdfBlocks.map { it.toOcrBlock() }
                } else {
                    // No digital text layer (scanned/image-only page) — fall back to
                    // on-device OCR, cached to disk so re-opening the doc is instant.
                    loadOcrFallbackBlocks(context, doc, pageIndex)
                }
            }
            val current = _uiState.value
            if (current.document?.uri != doc.uri) {
                textLoadingPages.remove(pageIndex)
                return@launch
            }
            val updated = current.ocrBlocksByPage.toMutableMap()
            updated[pageIndex] = blocks
            textLoadingPages.remove(pageIndex)
            _uiState.value = current.copy(
                ocrBlocksByPage = updated,
                ocrPagesInProgress = textLoadingPages.toSet()
            )
        }
    }

    /** Rasterizes [pageIndex] and runs it through [PdfServiceLocator.ocrService], reading/writing the disk cache. */
    private suspend fun loadOcrFallbackBlocks(context: Context, doc: PdfDocument, pageIndex: Int): List<OcrTextBlock> {
        OcrPageCache.read(context, doc, pageIndex)?.let { return it }
        val bitmap = runCatching {
            PdfRasterizer.rasterizePageBitmap(context, doc.uri, pageIndex)
        }.getOrNull() ?: return emptyList()
        return try {
            val result = runCatching { PdfServiceLocator.ocrService.recognize(context, bitmap) }.getOrNull()
                ?: return emptyList()
            val blocks = groupOcrWordsIntoBlocks(result.words, pageIndex)
            OcrPageCache.write(context, doc, pageIndex, blocks)
            blocks
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun onPageChanged(page: Int) {
        val state = _uiState.value
        if (page !in state.pageBitmaps.indices) return

        val bitmaps = state.pageBitmaps.toMutableList()
        bitmaps.forEachIndexed { index, existing ->
            if (existing != null && abs(index - page) > CACHE_RADIUS) {
                if (!existing.isRecycled) existing.recycle()
                bitmaps[index] = null
                renderedPageWidths.remove(index)
            }
        }
        _uiState.value = state.copy(currentPage = page, pageBitmaps = bitmaps)
    }

    fun toggleOcrSelection(pageIndex: Int, blockId: String) {
        val current = _uiState.value
        val selectedByPage = current.selectedOcrBlockIdsByPage.toMutableMap()
        val rangesByPage = current.selectedOcrRangesByPage.toMutableMap()
        val selected = (selectedByPage[pageIndex] ?: emptySet()).toMutableSet()
        val ranges = (rangesByPage[pageIndex] ?: emptyList()).toMutableList()
        if (!selected.add(blockId)) {
            selected.remove(blockId)
            ranges.removeAll { it.blockId == blockId }
        } else {
            val block = current.ocrBlocksByPage[pageIndex].orEmpty().firstOrNull { it.id == blockId }
            if (block != null) ranges.add(block.fullTextRange())
        }
        selectedByPage[pageIndex] = selected
        rangesByPage[pageIndex] = ranges
        _uiState.value = current.copy(
            selectedOcrBlockIdsByPage = selectedByPage,
            selectedOcrRangesByPage = rangesByPage
        )
    }

    fun selectOcrBlocks(pageIndex: Int, blockIds: Set<String>, append: Boolean) {
        if (blockIds.isEmpty()) return
        val current = _uiState.value
        val ranges = current.ocrBlocksByPage[pageIndex].orEmpty()
            .filter { it.id in blockIds }
            .map { it.fullTextRange() }
        selectOcrRanges(pageIndex, ranges, append)
    }

    fun selectOcrRanges(pageIndex: Int, ranges: List<OcrTextRange>, append: Boolean) {
        val current = _uiState.value
        val blocks = current.ocrBlocksByPage[pageIndex].orEmpty().associateBy { it.id }
        val clean = ranges.mapNotNull { range ->
            val block = blocks[range.blockId] ?: return@mapNotNull null
            val start = range.start.coerceIn(0, block.text.length)
            val end = range.end.coerceIn(start, block.text.length)
            if (end <= start) null else OcrTextRange(block.id, start, end)
        }
        if (clean.isEmpty()) return

        val existing = if (append) current.selectedOcrRangesByPage[pageIndex].orEmpty() else emptyList()
        val merged = (existing + clean)
            .groupBy { it.blockId }
            .values
            .flatMap { blockRanges ->
                val sorted = blockRanges.sortedBy { it.start }
                buildList<OcrTextRange> {
                    sorted.forEach { range ->
                        val previous = lastOrNull()
                        if (previous != null && range.start <= previous.end) {
                            removeAt(lastIndex)
                            add(previous.copy(end = maxOf(previous.end, range.end)))
                        } else add(range)
                    }
                }
            }
        val selectedByPage = current.selectedOcrBlockIdsByPage.toMutableMap()
        selectedByPage[pageIndex] = merged.map { it.blockId }.toSet()
        val rangesByPage = current.selectedOcrRangesByPage.toMutableMap()
        rangesByPage[pageIndex] = merged
        _uiState.value = current.copy(
            selectedOcrBlockIdsByPage = selectedByPage,
            selectedOcrRangesByPage = rangesByPage
        )
    }

    fun clearOcrSelection(pageIndex: Int) {
        val current = _uiState.value
        val selectedByPage = current.selectedOcrBlockIdsByPage.toMutableMap()
        val rangesByPage = current.selectedOcrRangesByPage.toMutableMap()
        selectedByPage.remove(pageIndex)
        rangesByPage.remove(pageIndex)
        _uiState.value = current.copy(
            selectedOcrBlockIdsByPage = selectedByPage,
            selectedOcrRangesByPage = rangesByPage
        )
    }

    fun selectLine(pageIndex: Int, blockId: String) {
        val state = _uiState.value
        val blocks = state.ocrBlocksByPage[pageIndex] ?: return
        val anchor = blocks.firstOrNull { it.id == blockId } ?: return
        val lineBlocks = blocks.filter { b ->
            val cY = (b.top + b.bottom) / 2f
            cY >= anchor.top && cY <= anchor.bottom
        }
        selectOcrBlocks(pageIndex, lineBlocks.map { it.id }.toSet(), append = false)
    }

    fun selectParagraph(pageIndex: Int, blockId: String) {
        val state = _uiState.value
        val blocks = state.ocrBlocksByPage[pageIndex]?.sortedBy { it.top } ?: return
        val anchor = blocks.firstOrNull { it.id == blockId } ?: return
        val avgLineHeight = blocks.map { it.bottom - it.top }.average().toFloat().coerceAtLeast(0.01f)
        val lineGapThreshold = avgLineHeight * 1.5f

        val paragraphBlocks = mutableListOf<OcrTextBlock>()
        var lastTop = anchor.top
        for (b in blocks.sortedByDescending { it.top }) {
            if (b.top > anchor.bottom + lineGapThreshold) continue
            if (lastTop - b.bottom > lineGapThreshold) break
            paragraphBlocks.add(b)
            lastTop = b.top
        }
        var lastBottom = anchor.bottom
        for (b in blocks.sortedBy { it.top }) {
            if (b.bottom < anchor.top - lineGapThreshold) continue
            if (b.top - lastBottom > lineGapThreshold) break
            if (!paragraphBlocks.contains(b)) paragraphBlocks.add(b)
            lastBottom = b.bottom
        }
        if (paragraphBlocks.isNotEmpty()) {
            selectOcrBlocks(pageIndex, paragraphBlocks.map { it.id }.toSet(), append = false)
        }
    }

    fun getSelectedOcrText(pageIndex: Int): String {
        val state = _uiState.value
        val blocks = state.ocrBlocksByPage[pageIndex].orEmpty()
        val selected = state.selectedOcrRangesByPage[pageIndex].orEmpty()
        if (selected.isEmpty()) return ""
        val byId = blocks.associateBy { it.id }
        return selected
            .sortedWith(compareBy({ byId[it.blockId]?.top ?: Float.MAX_VALUE }, { byId[it.blockId]?.left ?: Float.MAX_VALUE }, { it.start }))
            .mapNotNull { range ->
                val block = byId[range.blockId] ?: return@mapNotNull null
                block.text.substring(range.start.coerceIn(0, block.text.length), range.end.coerceIn(0, block.text.length))
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
            .joinToString(" ")
            .trim()
    }

    fun clearExportFeedback() {
        _uiState.value = _uiState.value.copy(exportMessage = null, exportError = null)
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    fun searchText(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                findQuery = "",
                findMatches = emptyList(),
                currentMatchIndex = -1
            )
            return
        }
        val lower = query.trim().lowercase()
        // Fast path: search already-extracted blocks in memory, highlighting the exact word.
        val inMemoryMatches = _uiState.value.ocrBlocksByPage
            .entries
            .sortedBy { it.key }
            .flatMap { (page, blocks) ->
                blocks.sortedBy { it.top }.flatMap { it.findMatches(lower, page) }
            }

        _uiState.value = _uiState.value.copy(
            findQuery = query,
            findMatches = inMemoryMatches,
            currentMatchIndex = if (inMemoryMatches.isEmpty()) -1 else 0
        )

        // Trigger full-document text extraction for pages not yet loaded, then re-search
        val doc = _uiState.value.document ?: return
        val pageCount = _uiState.value.pageCount
        viewModelScope.launch {
            val allMatches = withContext(Dispatchers.IO) {
                runCatching {
                    // Use current in-memory blocks; do a PdfBox full search for completeness
                    val context = _uiState.value.document?.let { return@runCatching null } ?: return@runCatching null
                    null
                }.getOrElse { null }
            }
            // Keep in-memory results; trigger lazy text load for unloaded pages
        }
    }

    fun searchTextInDocument(context: Context, query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                findQuery = "",
                findMatches = emptyList(),
                currentMatchIndex = -1
            )
            return
        }
        val doc = _uiState.value.document ?: return
        val lower = query.trim().lowercase()
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) {
                runCatching {
                    textService.searchAll(context, doc.uri, query, doc.pageCount)
                        .map { FindMatch(it.pageIndex, it.left, it.top, it.right, it.bottom) }
                }.getOrElse {
                    // Fallback: search in-memory extracted blocks (word-precise).
                    _uiState.value.ocrBlocksByPage.entries.sortedBy { it.key }.flatMap { (page, blocks) ->
                        blocks.sortedBy { it.top }.flatMap { it.findMatches(lower, page) }
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                findQuery = query,
                findMatches = matches,
                currentMatchIndex = if (matches.isEmpty()) -1 else 0
            )
        }
    }

    fun nextMatch() {
        val state = _uiState.value
        if (state.findMatches.isEmpty()) return
        val next = (state.currentMatchIndex + 1) % state.findMatches.size
        _uiState.value = state.copy(currentMatchIndex = next)
    }

    fun prevMatch() {
        val state = _uiState.value
        if (state.findMatches.isEmpty()) return
        val prev = (state.currentMatchIndex - 1 + state.findMatches.size) % state.findMatches.size
        _uiState.value = state.copy(currentMatchIndex = prev)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            findQuery = "",
            findMatches = emptyList(),
            currentMatchIndex = -1
        )
    }

    fun triggerOcrForAllPages(context: Context) {
        val state = _uiState.value
        val doc = state.document ?: return
        for (page in 0 until state.pageCount) {
            if (!state.ocrBlocksByPage.containsKey(page) && !textLoadingPages.contains(page)) {
                loadTextPage(context, doc, page)
            }
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────────

    fun exportEditedPdf(
        context: Context,
        overlaysByPage: Map<Int, List<ExportOverlay>>,
        fileName: String,
        overrideUri: Uri? = null
    ) {
        val doc = _uiState.value.document ?: return
        _uiState.value = _uiState.value.copy(
            isExporting = true, exportMessage = null, exportError = null, lastExportedUri = null
        )

        viewModelScope.launch {
            try {
                val outputUri = withContext(Dispatchers.IO) {
                    createEditedOutputUri(context, fileName, overrideUri)
                }

                withContext(Dispatchers.IO) {
                    exportWithPdfBox(context, doc, overlaysByPage, outputUri)
                }

                val outputName = queryFileName(context, outputUri) ?: "Edited.pdf"
                val outputSize = context.contentResolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: -1L
                RecentFilesManager.addRecent(context, RecentFile(
                    name = outputName,
                    uriString = outputUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    sizeBytes = outputSize
                ))
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = context.getString(R.string.viewer_save_success, outputName),
                    lastExportedUri = outputUri
                )
                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportError = context.getString(R.string.viewer_save_failed)
                )
            }
        }
    }

    private fun exportWithPdfBox(
        context: Context,
        doc: PdfDocument,
        overlaysByPage: Map<Int, List<ExportOverlay>>,
        outputUri: Uri
    ) {
        com.kyant.pdfcore.internal.PdfBox.ensureInitialized(context)
        val inputStream = context.contentResolver.openInputStream(doc.uri)
            ?: throw IllegalStateException("Cannot open source PDF")

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { pdDoc ->
            overlaysByPage.forEach { (pageIdx, overlays) ->
                if (pageIdx !in 0 until pdDoc.numberOfPages) return@forEach
                val page = pdDoc.getPage(pageIdx)
                val pageW = (page.cropBox ?: page.mediaBox)?.width ?: return@forEach
                val pageH = (page.cropBox ?: page.mediaBox)?.height ?: return@forEach

                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    pdDoc, page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true, true
                ).use { cs ->
                    for (overlay in overlays) {
                        drawOverlayOnPage(cs, overlay, pageW, pageH, pdDoc, page)
                    }
                }
            }

            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot open output stream")
            outputStream.use { pdDoc.save(it) }
        }
    }

    private fun drawOverlayOnPage(
        cs: com.tom_roush.pdfbox.pdmodel.PDPageContentStream,
        overlay: ExportOverlay,
        pageW: Float,
        pageH: Float,
        pdDoc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        page: com.tom_roush.pdfbox.pdmodel.PDPage
    ) {
        fun nx(x: Float) = x * pageW
        fun ny(y: Float) = (1f - y) * pageH  // PDF Y=0 is bottom

        try {
            when (overlay) {
                is ExportOverlay.Stroke -> {
                    if (overlay.points.size < 2) return
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    cs.saveGraphicsState()
                    val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = overlay.alpha
                    cs.setGraphicsStateParameters(gs)
                    cs.setStrokingColor(c.red(), c.green(), c.blue())
                    cs.setLineWidth((overlay.widthNorm * min(pageW, pageH)).coerceAtLeast(0.5f))
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    overlay.points.forEachIndexed { idx, pt ->
                        if (idx == 0) cs.moveTo(nx(pt.x), ny(pt.y))
                        else cs.lineTo(nx(pt.x), ny(pt.y))
                    }
                    cs.stroke()
                    cs.restoreGraphicsState()
                }

                is ExportOverlay.RectShape -> {
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    val x1 = nx(minOf(overlay.start.x, overlay.end.x))
                    val y1 = ny(maxOf(overlay.start.y, overlay.end.y))
                    val w = abs(nx(overlay.end.x) - nx(overlay.start.x))
                    val h = abs(ny(overlay.start.y) - ny(overlay.end.y))
                    cs.saveGraphicsState()
                    val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = overlay.alpha
                    gs.nonStrokingAlphaConstant = overlay.alpha
                    cs.setGraphicsStateParameters(gs)
                    if (overlay.filled) {
                        cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                        cs.addRect(x1, y1, w, h)
                        cs.fill()
                    } else {
                        cs.setStrokingColor(c.red(), c.green(), c.blue())
                        cs.setLineWidth(1.5f)
                        cs.addRect(x1, y1, w, h)
                        cs.stroke()
                    }
                    cs.restoreGraphicsState()
                }

                is ExportOverlay.OvalShape -> {
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    val cx = nx((overlay.start.x + overlay.end.x) / 2f)
                    val cy = ny((overlay.start.y + overlay.end.y) / 2f)
                    val rx = abs(nx(overlay.end.x) - nx(overlay.start.x)) / 2f
                    val ry = abs(ny(overlay.start.y) - ny(overlay.end.y)) / 2f
                    cs.saveGraphicsState()
                    val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = overlay.alpha
                    gs.nonStrokingAlphaConstant = overlay.alpha
                    cs.setGraphicsStateParameters(gs)
                    // Approximate ellipse with Bezier curves
                    val k = 0.5523f
                    if (overlay.filled) {
                        cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                        cs.moveTo(cx - rx, cy)
                        cs.curveTo(cx - rx, cy + ry * k, cx - rx * k, cy + ry, cx, cy + ry)
                        cs.curveTo(cx + rx * k, cy + ry, cx + rx, cy + ry * k, cx + rx, cy)
                        cs.curveTo(cx + rx, cy - ry * k, cx + rx * k, cy - ry, cx, cy - ry)
                        cs.curveTo(cx - rx * k, cy - ry, cx - rx, cy - ry * k, cx - rx, cy)
                        cs.fill()
                    } else {
                        cs.setStrokingColor(c.red(), c.green(), c.blue())
                        cs.setLineWidth(1.5f)
                        cs.moveTo(cx - rx, cy)
                        cs.curveTo(cx - rx, cy + ry * k, cx - rx * k, cy + ry, cx, cy + ry)
                        cs.curveTo(cx + rx * k, cy + ry, cx + rx, cy + ry * k, cx + rx, cy)
                        cs.curveTo(cx + rx, cy - ry * k, cx + rx * k, cy - ry, cx, cy - ry)
                        cs.curveTo(cx - rx * k, cy - ry, cx - rx, cy - ry * k, cx - rx, cy)
                        cs.stroke()
                    }
                    cs.restoreGraphicsState()
                }

                is ExportOverlay.LineShape -> {
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    val x1 = nx(overlay.start.x); val y1 = ny(overlay.start.y)
                    val x2 = nx(overlay.end.x);   val y2 = ny(overlay.end.y)
                    cs.saveGraphicsState()
                    val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = overlay.alpha
                    cs.setGraphicsStateParameters(gs)
                    cs.setStrokingColor(c.red(), c.green(), c.blue())
                    cs.setLineWidth((overlay.widthNorm * min(pageW, pageH)).coerceAtLeast(0.5f))
                    cs.setLineCapStyle(1)
                    cs.moveTo(x1, y1)
                    cs.lineTo(x2, y2)
                    if (overlay.arrowHead) {
                        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                        val headLen = (overlay.widthNorm * min(pageW, pageH) * 4f).coerceAtLeast(8f).toDouble()
                        val a1 = angle + PI - PI / 6
                        val a2 = angle + PI + PI / 6
                        cs.moveTo(x2, y2)
                        cs.lineTo((x2 + headLen * cos(a1)).toFloat(), (y2 + headLen * sin(a1)).toFloat())
                        cs.moveTo(x2, y2)
                        cs.lineTo((x2 + headLen * cos(a2)).toFloat(), (y2 + headLen * sin(a2)).toFloat())
                    }
                    cs.stroke()
                    cs.restoreGraphicsState()
                }

                is ExportOverlay.ImageStamp -> {
                    if (overlay.bitmap.isRecycled) return
                    val pdImage = runCatching {
                        com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
                            .createFromImage(pdDoc, overlay.bitmap)
                    }.getOrElse { return }
                    val x = nx(minOf(overlay.start.x, overlay.end.x))
                    val y = ny(maxOf(overlay.start.y, overlay.end.y))
                    val w = abs(nx(overlay.end.x) - nx(overlay.start.x))
                    val h = abs(ny(overlay.start.y) - ny(overlay.end.y))
                    cs.saveGraphicsState()
                    cs.drawImage(pdImage, x, y, w, h)
                    cs.restoreGraphicsState()
                }

                is ExportOverlay.TextStamp -> {
                    if (overlay.text.isBlank()) return
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                    val size = (overlay.fontSizeNorm * pageH).coerceIn(4f, pageH)
                    val leading = size * 1.2f
                    // PDFBox's WinAnsi encoding rejects unsupported glyphs; sanitize to Latin-1.
                    val lines = overlay.text.split("\n").map { line ->
                        buildString { line.forEach { ch -> append(if (ch.code in 32..255) ch else '?') } }
                    }
                    val startX = nx(overlay.position.x)
                    val startY = ny(overlay.position.y) - size  // baseline for first line
                    cs.beginText()
                    cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                    cs.setFont(font, size)
                    cs.newLineAtOffset(startX, startY)
                    lines.forEachIndexed { idx, line ->
                        if (idx > 0) cs.newLineAtOffset(0f, -leading)
                        runCatching { cs.showText(line) }
                    }
                    cs.endText()
                }

                is ExportOverlay.NoteStamp -> {
                    // A real, clickable PDF sticky-note annotation.
                    val c = android.graphics.Color.valueOf(overlay.colorArgb)
                    val note = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
                    note.setContents(overlay.text)
                    note.name = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText.NAME_NOTE
                    note.color = com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                        floatArrayOf(c.red(), c.green(), c.blue()),
                        com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
                    )
                    val ax = nx(overlay.position.x)
                    val ay = ny(overlay.position.y)
                    val iconSize = (min(pageW, pageH) * 0.03f).coerceIn(14f, 28f)
                    note.rectangle = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(ax, ay - iconSize, iconSize, iconSize)
                    page.annotations.add(note)
                }
            }
        } catch (_: Exception) {
            // Silently skip overlay that fails to render
        }
    }

    override fun onCleared() {
        recycleBitmaps(_uiState.value.pageBitmaps)
        renderingPages.clear()
        renderedPageWidths.clear()
        textLoadingPages.clear()
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        super.onCleared()
    }

    private fun createEditedOutputUri(context: Context, targetFileName: String, overrideUri: Uri?): Uri {
        val targetPath = overrideUri ?: SaveLocationManager.getSaveUri(context)
        if (targetPath != null) {
            runCatching {
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, targetPath)
                val created = tree?.createFile("application/pdf", targetFileName)?.uri
                if (created != null) return created
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create edited output in Downloads")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, targetFileName)
            if (!file.exists()) file.createNewFile()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrElse { null }

    private fun recycleBitmaps(bitmaps: List<Bitmap?>) {
        bitmaps.forEach { if (it != null && !it.isRecycled) it.recycle() }
    }
}

private fun OcrTextBlock.fullTextRange(): OcrTextRange =
    OcrTextRange(id, 0, text.length)

private fun PdfTextBlock.toOcrBlock() = OcrTextBlock(
    id = id, text = text, left = left, top = top, right = right, bottom = bottom,
    charLefts = charLefts, charRights = charRights
)

/** All occurrences of [lower] in this block as tight normalized word rects (fallback: block rect). */
private fun OcrTextBlock.findMatches(lower: String, page: Int): List<FindMatch> {
    if (lower.isEmpty()) return emptyList()
    val bt = text.lowercase()
    val out = ArrayList<FindMatch>()
    var from = 0
    while (true) {
        val idx = bt.indexOf(lower, from)
        if (idx < 0) break
        val endC = idx + lower.length - 1
        if (charLefts.isNotEmpty() && idx < charLefts.size && endC < charRights.size) {
            out.add(FindMatch(page, charLefts[idx], top, charRights[endC], bottom))
        } else {
            out.add(FindMatch(page, left, top, right, bottom))
        }
        from = idx + lower.length
    }
    return out
}

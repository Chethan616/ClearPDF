package com.chethan616.clearpdf.ui.viewmodel

import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.domain.usecase.OpenPdfUseCase
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kyant.pdfcore.model.PdfDocument
import com.chethan616.clearpdf.ui.utils.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfViewerUiState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pageBitmaps: List<Bitmap?> = emptyList(),
    val document: PdfDocument? = null,
    val sizeBytes: Long = -1,
    val ocrBlocksByPage: Map<Int, List<OcrTextBlock>> = emptyMap(),
    val ocrPagesInProgress: Set<Int> = emptySet(),
    val selectedOcrBlockIdsByPage: Map<Int, Set<String>> = emptyMap(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val exportError: String? = null,
    val lastExportedUri: Uri? = null,
    // ── Find / Search ─────────────────────────────────────────────────────────
    val findQuery: String = "",
    val findMatches: List<FindMatch> = emptyList(),
    val currentMatchIndex: Int = -1
)

data class OcrTextBlock(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class FindMatch(
    val pageIndex: Int,
    val blockId: String
)

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

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
}

class PdfViewerViewModel(private val openPdfUseCase: OpenPdfUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()
    private val renderingPages = mutableSetOf<Pair<Uri, Int>>()
    private val renderedPageWidths = mutableMapOf<Int, Int>()
    private val ocrProcessingPages = mutableSetOf<Int>()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val DEFAULT_RENDER_WIDTH = 1200
        private const val MIN_RENDER_WIDTH = 720
        private const val CACHE_RADIUS = 2
    }

    fun openPdf(context: Context, uri: Uri) {
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        recycleBitmaps(_uiState.value.pageBitmaps)
        renderingPages.clear()
        renderedPageWidths.clear()
        ocrProcessingPages.clear()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            ocrPagesInProgress = emptySet()
        )
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Not all URI sources support persistable grants.
                }

                val (doc, openedUri) = withContext(Dispatchers.IO) {
                    openDocumentWithFallback(context, uri)
                }
                val displayName = queryFileName(context, uri) ?: doc.name
                _uiState.value = _uiState.value.copy(
                    fileName = displayName,
                    pageCount = doc.pageCount,
                    currentPage = 0,
                    isLoading = false,
                    document = doc,
                    sizeBytes = doc.sizeBytes,
                    pageBitmaps = List(doc.pageCount) { null },
                    ocrBlocksByPage = emptyMap(),
                    ocrPagesInProgress = emptySet(),
                    selectedOcrBlockIdsByPage = emptyMap(),
                    isExporting = false,
                    exportMessage = null,
                    exportError = null,
                    lastExportedUri = null
                )
                // Add to recents
                RecentFilesManager.addRecent(context, RecentFile(
                    name = displayName,
                    uriString = openedUri.toString(),
                    timestamp = System.currentTimeMillis(),
                    pageCount = doc.pageCount,
                    sizeBytes = doc.sizeBytes
                ))

                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }

                // Render first page
                renderPage(context, 0, DEFAULT_RENDER_WIDTH)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to open PDF"
                )
            }
        }
    }

    private fun openDocumentWithFallback(context: Context, sourceUri: Uri): Pair<PdfDocument, Uri> {
        val targetUri = if (!com.chethan616.clearpdf.utils.UniversalDocumentConverter.isPdf(context, sourceUri)) {
            com.chethan616.clearpdf.utils.UniversalDocumentConverter.convertToPdf(context, sourceUri)
        } else {
            sourceUri
        }

        val sourceDescriptorSize = tryReadDescriptorSize(context, targetUri)
        if (sourceDescriptorSize == 0L) {
            throw IllegalStateException("Selected document is empty")
        }

        val primaryUri = if (sourceDescriptorSize != null) {
            targetUri
        } else {
            mirrorPdfToAppStorage(context, targetUri)
        }

        return try {
            openPdfUseCase.open(context, primaryUri) to primaryUri
        } catch (primaryError: Exception) {
            if (primaryUri != targetUri) {
                throw primaryError
            }

            val mirroredUri = mirrorPdfToAppStorage(context, targetUri)
            val mirroredSize = tryReadDescriptorSize(context, mirroredUri)
            if (mirroredSize == 0L) {
                throw IllegalStateException("Selected document is empty")
            }
            if (mirroredSize == null) {
                throw IllegalStateException("Unable to access selected document")
            }

            openPdfUseCase.open(context, mirroredUri) to mirroredUri
        }
    }

    private fun tryReadDescriptorSize(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (_: Exception) {
            null
        }
    }

    private fun mirrorPdfToAppStorage(context: Context, sourceUri: Uri): Uri {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Unable to access selected document")

        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
        val mirrorDir = File(baseDir, "imported_pdfs")
        if (!mirrorDir.exists() && !mirrorDir.mkdirs()) {
            throw IllegalStateException("Unable to prepare local document storage")
        }

        val sourceName = queryFileName(context, sourceUri)
            ?.ifBlank { null }
            ?: "Imported_${System.currentTimeMillis()}.pdf"
        val sanitized = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetName = if (sanitized.lowercase(Locale.ROOT).endsWith(".pdf")) {
            sanitized
        } else {
            "$sanitized.pdf"
        }
        val targetFile = File(mirrorDir, "${System.currentTimeMillis()}_$targetName")

        input.use { inputStream ->
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
                output.flush()
            }
        }

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
                    openPdfUseCase.renderPage(doc, pageIndex, renderWidth)
                }

                val currentState = _uiState.value
                if (currentState.document?.uri != documentUri) return@launch
                if (pageIndex !in currentState.pageBitmaps.indices) return@launch

                val bitmaps = currentState.pageBitmaps.toMutableList()
                val previous = bitmaps[pageIndex]
                if (previous != null && previous != bitmap && !previous.isRecycled) {
                    previous.recycle()
                }
                bitmaps[pageIndex] = bitmap
                if (bitmap == null) {
                    renderedPageWidths.remove(pageIndex)
                } else {
                    renderedPageWidths[pageIndex] = renderWidth
                }

                // Keep only nearby pages in memory for smooth swipes without OOMs.
                bitmaps.forEachIndexed { index, existing ->
                    if (existing != null && index != pageIndex && abs(index - currentState.currentPage) > CACHE_RADIUS) {
                        if (!existing.isRecycled) existing.recycle()
                        bitmaps[index] = null
                        renderedPageWidths.remove(index)
                    }
                }

                _uiState.value = currentState.copy(pageBitmaps = bitmaps)

                if (bitmap != null && !bitmap.isRecycled) {
                    runOcrForPage(documentUri, pageIndex, bitmap)
                }
            } finally {
                renderingPages.remove(renderKey)
            }
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
        val selected = (selectedByPage[pageIndex] ?: emptySet()).toMutableSet()
        if (!selected.add(blockId)) {
            selected.remove(blockId)
        }
        selectedByPage[pageIndex] = selected
        _uiState.value = current.copy(selectedOcrBlockIdsByPage = selectedByPage)
    }

    fun selectOcrBlocks(pageIndex: Int, blockIds: Set<String>, append: Boolean) {
        if (blockIds.isEmpty()) return
        val current = _uiState.value
        val selectedByPage = current.selectedOcrBlockIdsByPage.toMutableMap()
        val base = if (append) {
            (selectedByPage[pageIndex] ?: emptySet()).toMutableSet()
        } else {
            mutableSetOf()
        }
        base.addAll(blockIds)
        selectedByPage[pageIndex] = base
        _uiState.value = current.copy(selectedOcrBlockIdsByPage = selectedByPage)
    }

    fun clearOcrSelection(pageIndex: Int) {
        val current = _uiState.value
        val selectedByPage = current.selectedOcrBlockIdsByPage.toMutableMap()
        selectedByPage.remove(pageIndex)
        _uiState.value = current.copy(selectedOcrBlockIdsByPage = selectedByPage)
    }

    /**
     * Selects all OCR blocks on the same horizontal line as [blockId].
     * "Same line" = blocks whose vertical centre overlaps the target block's bounding box.
     */
    fun selectLine(pageIndex: Int, blockId: String) {
        val state = _uiState.value
        val blocks = state.ocrBlocksByPage[pageIndex] ?: return
        val anchor = blocks.firstOrNull { it.id == blockId } ?: return
        val anchorCenterY = (anchor.top + anchor.bottom) / 2f
        val lineBlocks = blocks.filter { b ->
            val cY = (b.top + b.bottom) / 2f
            cY >= anchor.top && cY <= anchor.bottom
        }
        selectOcrBlocks(pageIndex, lineBlocks.map { it.id }.toSet(), append = false)
    }

    /**
     * Selects all OCR blocks in the same paragraph as [blockId].
     * "Same paragraph" = a vertically contiguous run of lines with gaps < line height.
     */
    fun selectParagraph(pageIndex: Int, blockId: String) {
        val state = _uiState.value
        val blocks = state.ocrBlocksByPage[pageIndex]?.sortedBy { it.top } ?: return
        val anchor = blocks.firstOrNull { it.id == blockId } ?: return
        val avgLineHeight = blocks.map { it.bottom - it.top }.average().toFloat().coerceAtLeast(0.01f)
        val lineGapThreshold = avgLineHeight * 1.5f

        // Group blocks into lines first, then find contiguous paragraph
        fun centerY(b: OcrTextBlock) = (b.top + b.bottom) / 2f

        // Walk upward from anchor
        val paragraphBlocks = mutableListOf<OcrTextBlock>()
        var lastTop = anchor.top
        for (b in blocks.sortedByDescending { it.top }) {
            if (b.top > anchor.bottom + lineGapThreshold) continue
            if (lastTop - b.bottom > lineGapThreshold) break
            paragraphBlocks.add(b)
            lastTop = b.top
        }
        // Walk downward from anchor
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
        val selected = state.selectedOcrBlockIdsByPage[pageIndex] ?: return ""
        if (selected.isEmpty()) return ""
        return state.ocrBlocksByPage[pageIndex]
            .orEmpty()
            .filter { block -> selected.contains(block.id) }
            .joinToString(" ") { it.text }
            .trim()
    }

    fun clearExportFeedback() {
        val current = _uiState.value
        _uiState.value = current.copy(exportMessage = null, exportError = null)
    }

    // ── Find / Search ────────────────────────────────────────────────────────

    /**
     * Searches all OCR-indexed pages for blocks containing [query] (case-insensitive).
     * Results are sorted by page, then by the block's top-to-bottom position.
     */
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
        val matches = _uiState.value.ocrBlocksByPage
            .entries
            .sortedBy { it.key }
            .flatMap { (page, blocks) ->
                blocks
                    .filter { it.text.lowercase().contains(lower) }
                    .sortedBy { it.top }
                    .map { FindMatch(page, it.id) }
            }
        _uiState.value = _uiState.value.copy(
            findQuery = query,
            findMatches = matches,
            currentMatchIndex = if (matches.isEmpty()) -1 else 0
        )
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

    /** Expands OCR search to pages not yet processed. Call when find mode is activated. */
    fun triggerOcrForAllPages(context: Context) {
        val state = _uiState.value
        val doc = state.document ?: return
        val needed = (0 until state.pageCount).filter {
            !state.ocrBlocksByPage.containsKey(it) && !state.ocrPagesInProgress.contains(it)
        }
        needed.forEach { page ->
            val bitmap = state.pageBitmaps[page]
            if (bitmap != null && !bitmap.isRecycled) {
                runOcrForPage(doc.uri, page, bitmap)
            } else {
                renderPage(context, page)
            }
        }
    }

    fun exportEditedPdf(context: Context, overlaysByPage: Map<Int, List<ExportOverlay>>, fileName: String, overrideUri: Uri? = null) {
        val doc = _uiState.value.document ?: return
        _uiState.value = _uiState.value.copy(
            isExporting = true,
            exportMessage = null,
            exportError = null,
            lastExportedUri = null
        )

        viewModelScope.launch {
            try {
                val outputUri = withContext(Dispatchers.IO) {
                    createEditedOutputUri(context, fileName, overrideUri)
                }

                withContext(Dispatchers.IO) {
                    val editedPdf = AndroidPdfDocument()
                    for (pageIndex in 0 until doc.pageCount) {
                        val bitmap = openPdfUseCase.renderPage(doc, pageIndex, DEFAULT_RENDER_WIDTH)
                            ?: throw IllegalStateException("Unable to render page ${pageIndex + 1}")

                        applyOverlays(bitmap, overlaysByPage[pageIndex].orEmpty())

                        val pageInfo = AndroidPdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create()
                        val page = editedPdf.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        editedPdf.finishPage(page)
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }

                    val output = context.contentResolver.openOutputStream(outputUri)
                        ?: throw IllegalStateException("Unable to write edited PDF")
                    output.use {
                        editedPdf.writeTo(it)
                        it.flush()
                    }
                    editedPdf.close()
                }

                val outputName = queryFileName(context, outputUri) ?: "Edited.pdf"
                val outputSize = context.contentResolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: -1L
                RecentFilesManager.addRecent(
                    context,
                    RecentFile(
                        name = outputName,
                        uriString = outputUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        pageCount = doc.pageCount,
                        sizeBytes = outputSize
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = "Saved edited copy as $outputName",
                    lastExportedUri = outputUri
                )

                if (GitHubStarPromptManager.recordPdfInteraction(context)) {
                    StarPromptEventBus.requestPrompt()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportError = e.message ?: "Failed to save edited PDF"
                )
            }
        }
    }

    override fun onCleared() {
        recycleBitmaps(_uiState.value.pageBitmaps)
        renderingPages.clear()
        renderedPageWidths.clear()
        ocrProcessingPages.clear()
        _uiState.value.document?.let { openPdfUseCase.close(it) }
        textRecognizer.close()
        super.onCleared()
    }

    private fun runOcrForPage(documentUri: Uri, pageIndex: Int, bitmap: Bitmap) {
        val state = _uiState.value
        if (state.document?.uri != documentUri) return
        if (state.ocrBlocksByPage.containsKey(pageIndex)) return
        if (!ocrProcessingPages.add(pageIndex)) return
        _uiState.value = _uiState.value.copy(ocrPagesInProgress = ocrProcessingPages.toSet())

        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { text ->
                val current = _uiState.value
                if (current.document?.uri != documentUri) {
                    ocrProcessingPages.remove(pageIndex)
                    _uiState.value = current.copy(ocrPagesInProgress = ocrProcessingPages.toSet())
                    return@addOnSuccessListener
                }

                val width = bitmap.width.toFloat().coerceAtLeast(1f)
                val height = bitmap.height.toFloat().coerceAtLeast(1f)

                val blocks = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapIndexedNotNull { idx, block ->
                    val bounds = block.boundingBox ?: return@mapIndexedNotNull null
                    OcrTextBlock(
                        id = "$pageIndex-$idx-${bounds.left}-${bounds.top}",
                        text = block.text,
                        left = (bounds.left / width).coerceIn(0f, 1f),
                        top = (bounds.top / height).coerceIn(0f, 1f),
                        right = (bounds.right / width).coerceIn(0f, 1f),
                        bottom = (bounds.bottom / height).coerceIn(0f, 1f)
                    )
                }

                val updated = current.ocrBlocksByPage.toMutableMap()
                updated[pageIndex] = blocks
                ocrProcessingPages.remove(pageIndex)
                _uiState.value = current.copy(
                    ocrBlocksByPage = updated,
                    ocrPagesInProgress = ocrProcessingPages.toSet()
                )
            }
            .addOnFailureListener {
                ocrProcessingPages.remove(pageIndex)
                val current = _uiState.value
                _uiState.value = current.copy(ocrPagesInProgress = ocrProcessingPages.toSet())
            }
    }

    private fun createEditedOutputUri(context: Context, targetFileName: String, overrideUri: Uri?): Uri {
        val targetPath = overrideUri ?: SaveLocationManager.getSaveUri(context)
        if (targetPath != null) {
            try {
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, targetPath)
                val created = tree?.createFile("application/pdf", targetFileName)?.uri
                if (created != null) return created
            } catch (_: Exception) {
                // Fallback to default location below.
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

    private fun applyOverlays(bitmap: Bitmap, overlays: List<ExportOverlay>) {
        if (overlays.isEmpty()) return

        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat().coerceAtLeast(1f)
        val height = bitmap.height.toFloat().coerceAtLeast(1f)
        val minDim = min(width, height)

        overlays.forEach { overlay ->
            when (overlay) {
                is ExportOverlay.Stroke -> {
                    if (overlay.points.size < 2) return@forEach
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = applyAlpha(overlay.colorArgb, overlay.alpha)
                        style = Paint.Style.STROKE
                        strokeWidth = (overlay.widthNorm * minDim).coerceAtLeast(1f)
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val path = Path()
                    overlay.points.forEachIndexed { idx, point ->
                        val x = point.x.coerceIn(0f, 1f) * width
                        val y = point.y.coerceIn(0f, 1f) * height
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }

                is ExportOverlay.RectShape -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = applyAlpha(overlay.colorArgb, overlay.alpha)
                        style = if (overlay.filled) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = (0.0045f * minDim).coerceAtLeast(1f)
                    }
                    val rect = normalizedRect(
                        overlay.start.x,
                        overlay.start.y,
                        overlay.end.x,
                        overlay.end.y,
                        width,
                        height
                    )
                    canvas.drawRect(rect, paint)
                }

                is ExportOverlay.OvalShape -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = applyAlpha(overlay.colorArgb, overlay.alpha)
                        style = if (overlay.filled) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = (0.0045f * minDim).coerceAtLeast(1f)
                    }
                    val rect = normalizedRect(
                        overlay.start.x,
                        overlay.start.y,
                        overlay.end.x,
                        overlay.end.y,
                        width,
                        height
                    )
                    canvas.drawOval(rect, paint)
                }

                is ExportOverlay.LineShape -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = applyAlpha(overlay.colorArgb, overlay.alpha)
                        style = Paint.Style.STROKE
                        strokeWidth = (overlay.widthNorm * minDim).coerceAtLeast(1f)
                        strokeCap = Paint.Cap.ROUND
                    }
                    val startX = overlay.start.x.coerceIn(0f, 1f) * width
                    val startY = overlay.start.y.coerceIn(0f, 1f) * height
                    val endX = overlay.end.x.coerceIn(0f, 1f) * width
                    val endY = overlay.end.y.coerceIn(0f, 1f) * height
                    canvas.drawLine(startX, startY, endX, endY, paint)

                    if (overlay.arrowHead) {
                        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                        val headLength = (0.03f * minDim).coerceAtLeast(10f).toDouble()
                        val theta = 30.0 * PI / 180.0
                        val x1 = endX - (headLength * cos(angle - theta)).toFloat()
                        val y1 = endY - (headLength * sin(angle - theta)).toFloat()
                        val x2 = endX - (headLength * cos(angle + theta)).toFloat()
                        val y2 = endY - (headLength * sin(angle + theta)).toFloat()
                        canvas.drawLine(endX, endY, x1, y1, paint)
                        canvas.drawLine(endX, endY, x2, y2, paint)
                    }
                }

                is ExportOverlay.ImageStamp -> {
                    val dst = normalizedRect(
                        overlay.start.x, overlay.start.y, overlay.end.x, overlay.end.y, width, height
                    )
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                    if (!overlay.bitmap.isRecycled) {
                        canvas.drawBitmap(overlay.bitmap, null, dst, paint)
                    }
                }
            }
        }
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val baseAlpha = android.graphics.Color.alpha(color)
        val scaled = (baseAlpha * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }

    private fun normalizedRect(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        width: Float,
        height: Float
    ): android.graphics.RectF {
        val x1 = startX.coerceIn(0f, 1f) * width
        val y1 = startY.coerceIn(0f, 1f) * height
        val x2 = endX.coerceIn(0f, 1f) * width
        val y2 = endY.coerceIn(0f, 1f) * height
        return android.graphics.RectF(
            min(x1, x2),
            min(y1, y2),
            kotlin.math.max(x1, x2),
            kotlin.math.max(y1, y2)
        )
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recycleBitmaps(bitmaps: List<Bitmap?>) {
        bitmaps.forEach { bitmap ->
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

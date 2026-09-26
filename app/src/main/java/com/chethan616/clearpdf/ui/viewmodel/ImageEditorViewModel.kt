package com.chethan616.clearpdf.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.imageeditor.engine.Adjust
import com.chethan616.clearpdf.imageeditor.engine.AdjustOp
import com.chethan616.clearpdf.imageeditor.engine.BackgroundOp
import com.chethan616.clearpdf.imageeditor.engine.BackgroundRemover
import com.chethan616.clearpdf.imageeditor.engine.BrushKind
import com.chethan616.clearpdf.imageeditor.engine.CropOp
import com.chethan616.clearpdf.imageeditor.engine.DrawOp
import com.chethan616.clearpdf.imageeditor.engine.DrawStroke
import com.chethan616.clearpdf.imageeditor.engine.EditOp
import com.chethan616.clearpdf.imageeditor.engine.EditPipeline
import com.chethan616.clearpdf.imageeditor.engine.ExportSettings
import com.chethan616.clearpdf.imageeditor.engine.FilterOp
import com.chethan616.clearpdf.imageeditor.engine.FilterPreset
import com.chethan616.clearpdf.imageeditor.engine.ImageIo
import com.chethan616.clearpdf.imageeditor.engine.MaskStroke
import com.chethan616.clearpdf.imageeditor.engine.PerspectiveOp
import com.chethan616.clearpdf.imageeditor.engine.ResizeOp
import com.chethan616.clearpdf.imageeditor.engine.ShapeKind
import com.chethan616.clearpdf.imageeditor.engine.TextLayer
import com.chethan616.clearpdf.imageeditor.engine.TextOp
import com.chethan616.clearpdf.imageeditor.engine.TransformOp
import com.chethan616.clearpdf.imageeditor.engine.WatermarkOp
import com.chethan616.clearpdf.imageeditor.engine.WatermarkParams
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropOutline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Non-destructive image editor.
 *
 * * History is a list of immutable op-list snapshots; undo / redo just move [historyIndex].
 * * Slider-type edits ("sessions") update a *live* op list that is rendered immediately and
 *   folded into history once the value settles, so one drag = one undo step.
 * * The preview is a ≤ [PREVIEW_MAX] px decode replayed through [EditPipeline]'s per-op cache;
 *   export replays the same ops on a full-resolution decode on the render thread.
 */
class ImageEditorViewModel(app: Application) : AndroidViewModel(app) {

    enum class Tool { Crop, Adjust, Filters, Draw, Text, More }
    enum class MoreTool { None, Background, Watermark, Resize, Export, Exif }

    data class CropSession(
        val transform: TransformOp = TransformOp(),
        val working: Bitmap,
        val aspectIndex: Int = 0,
        val outlineIndex: Int = 0,
        val perspective: Boolean = false,
        val trigger: Boolean = false,
        val version: Int = 0
    )

    data class BrushSettings(
        val brush: BrushKind = BrushKind.Pen,
        val shape: ShapeKind = ShapeKind.Free,
        val color: Int = 0xFFFF453A.toInt(),
        val width: Float = 0.012f,
        val softness: Float = 0f,
        val alpha: Float = 1f,
        val effectStrength: Float = 0.03f,
        val tolerance: Float = 0.25f,
        val vertices: Int = 5
    )

    data class UiState(
        val fileName: String = "",
        val isLoading: Boolean = true,
        val error: String? = null,
        val preview: Bitmap? = null,
        val original: Bitmap? = null,
        val isRendering: Boolean = false,
        val tool: Tool = Tool.Adjust,
        val more: MoreTool = MoreTool.None,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val hasEdits: Boolean = false,
        val fullWidth: Int = 0,
        val fullHeight: Int = 0,
        // crop
        val crop: CropSession? = null,
        // adjust
        val adjustValues: Map<Adjust, Float> = emptyMap(),
        val selectedAdjust: Adjust = Adjust.Brightness,
        // filters
        val filterThumbs: Map<FilterPreset, Bitmap> = emptyMap(),
        val filterSource: Bitmap? = null,
        val selectedFilter: FilterPreset? = null,
        val filterIntensity: Float = 1f,
        // draw
        val brush: BrushSettings = BrushSettings(),
        // text
        val text: TextLayer = TextLayer(text = ""),
        // background
        val bgAvailable: Boolean = false,
        val bgBusy: Boolean = false,
        val bgRestore: Boolean = false,
        val bgBrushWidth: Float = 0.05f,
        // watermark / export
        val watermark: WatermarkParams = WatermarkParams(text = "ClearPDF"),
        val export: ExportSettings = ExportSettings(),
        val exif: List<ImageIo.ExifEntry> = emptyList(),
        val exporting: Boolean = false,
        val exportProgress: Float = 0f,
        val message: String? = null
    )

    private val context: Context get() = getApplication()
    private val pipeline = EditPipeline(app)
    private val bgRemover = BackgroundRemover.create(app)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var sourceUri: Uri? = null
    private var started = false

    private val history = mutableListOf<List<EditOp>>(emptyList())
    private var historyIndex = 0
    private val committed: List<EditOp> get() = history[historyIndex]

    /** Ops currently shown (may be ahead of history during a live session). */
    private var live: List<EditOp> = emptyList()
    /** Index in [live] of the op the current session is editing, or -1. */
    private var sessionIndex = -1
    private var sessionTool: Tool? = null
    private var commitJob: Job? = null
    private var thumbsJob: Job? = null

    private val renderRequests = MutableStateFlow<List<EditOp>?>(null)

    init {
        viewModelScope.launch {
            renderRequests.collectLatest { ops ->
                if (ops == null) return@collectLatest
                _state.update { it.copy(isRendering = true) }
                val bmp = runCatching { pipeline.render(ops) }.getOrNull()
                _state.update { s ->
                    s.copy(
                        preview = bmp ?: s.preview,
                        isRendering = false,
                        fullWidth = bmp?.let { (it.width / pipeline.resolutionScale).roundToInt() } ?: s.fullWidth,
                        fullHeight = bmp?.let { (it.height / pipeline.resolutionScale).roundToInt() } ?: s.fullHeight
                    )
                }
            }
        }
    }

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        sourceUri = uri
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { ImageIo.displayName(context, uri) }
            val bounds = withContext(Dispatchers.IO) { ImageIo.bounds(context, uri) }
            val bmp = withContext(Dispatchers.IO) { ImageIo.decode(context, uri, PREVIEW_MAX) }
            val exif = withContext(Dispatchers.IO) { ImageIo.readExif(context, uri) }
            if (bmp == null || bounds == null) {
                _state.value = UiState(fileName = name, isLoading = false, error = "Couldn't open this image.")
                return@launch
            }
            pipeline.setBase(bmp, bounds.width)
            _state.value = UiState(
                fileName = name,
                isLoading = false,
                preview = bmp,
                original = bmp,
                fullWidth = bounds.width,
                fullHeight = bounds.height,
                exif = exif,
                bgAvailable = bgRemover.isAvailable
            )
            selectTool(Tool.Adjust)
        }
    }

    // ── History ──────────────────────────────────────────────────────────────────────────────

    private fun showLive(ops: List<EditOp>) {
        live = ops
        renderRequests.value = ops
        _state.update { it.copy(canUndo = historyIndex > 0 || ops != committed, hasEdits = ops.isNotEmpty() || historyIndex > 0) }
    }

    private fun commit(ops: List<EditOp>) {
        commitJob?.cancel()
        if (ops == committed) { showLive(ops); return }
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history.add(ops)
        if (history.size > MAX_HISTORY) history.removeAt(0)
        historyIndex = history.lastIndex
        showLive(ops)
        _state.update { it.copy(canUndo = historyIndex > 0, canRedo = false, hasEdits = ops.isNotEmpty()) }
    }

    /** Folds a pending live session into history after the value settles. */
    private fun scheduleCommit() {
        commitJob?.cancel()
        commitJob = viewModelScope.launch {
            delay(450)
            commit(live)
        }
    }

    private fun flushSession() {
        if (commitJob?.isActive == true) commit(live)
        sessionIndex = -1
        sessionTool = null
    }

    fun undo() {
        flushSession()
        if (historyIndex == 0) return
        historyIndex--
        afterHistoryMove()
    }

    fun redo() {
        flushSession()
        if (historyIndex >= history.lastIndex) return
        historyIndex++
        afterHistoryMove()
    }

    private fun afterHistoryMove() {
        showLive(committed)
        _state.update {
            it.copy(canUndo = historyIndex > 0, canRedo = historyIndex < history.lastIndex, hasEdits = committed.isNotEmpty())
        }
        refreshToolState()
    }

    fun resetAll() {
        flushSession()
        commit(emptyList())
        refreshToolState()
    }

    /** Replace-or-append the op for the current session. */
    private fun sessionEdit(tool: Tool, op: EditOp?, commitNow: Boolean) {
        val base = live.toMutableList()
        if (sessionTool == tool && sessionIndex in base.indices) {
            if (op == null) { base.removeAt(sessionIndex); sessionIndex = -1 } else base[sessionIndex] = op
        } else if (op != null) {
            flushSession()
            base.clear(); base.addAll(live)
            base.add(op)
            sessionIndex = base.lastIndex
            sessionTool = tool
        }
        if (commitNow) commit(base) else { showLive(base); scheduleCommit() }
    }

    // ── Tools ────────────────────────────────────────────────────────────────────────────────

    fun selectTool(tool: Tool) {
        flushSession()
        _state.update { it.copy(tool = tool, more = MoreTool.None, crop = null) }
        refreshToolState()
        if (tool == Tool.Crop) startCrop()
    }

    fun openMore(more: MoreTool) {
        flushSession()
        // Keep refining the last background op so "restore" can bring back pixels it removed.
        if (more == MoreTool.Background && live.lastOrNull() is BackgroundOp) {
            sessionIndex = live.lastIndex
            sessionTool = Tool.More
        }
        _state.update { it.copy(tool = Tool.More, more = more, crop = null) }
    }

    fun closeMore() = _state.update { it.copy(more = MoreTool.None) }

    /** Re-derives the slider / chip state for the active tool from the op list. */
    private fun refreshToolState() {
        val top = live.lastOrNull()
        when (_state.value.tool) {
            Tool.Adjust -> {
                // Continue editing the top adjust op if it is the last thing done.
                if (top is AdjustOp) { sessionIndex = live.lastIndex; sessionTool = Tool.Adjust }
                _state.update { it.copy(adjustValues = (top as? AdjustOp)?.values ?: emptyMap()) }
            }
            Tool.Filters -> {
                if (top is FilterOp) { sessionIndex = live.lastIndex; sessionTool = Tool.Filters }
                _state.update { it.copy(selectedFilter = (top as? FilterOp)?.preset, filterIntensity = (top as? FilterOp)?.intensity ?: 1f) }
                refreshThumbnails(if (top is FilterOp) live.dropLast(1) else live)
            }
            Tool.Draw -> if (top is DrawOp) { sessionIndex = live.lastIndex; sessionTool = Tool.Draw }
            else -> Unit
        }
    }

    // Adjust
    fun selectAdjust(a: Adjust) = _state.update { it.copy(selectedAdjust = a) }

    fun setAdjust(a: Adjust, value: Float) {
        val values = _state.value.adjustValues.toMutableMap().apply { put(a, value.coerceIn(a.min, a.max)) }
        _state.update { it.copy(adjustValues = values) }
        val op = AdjustOp(values)
        sessionEdit(Tool.Adjust, if (op.isIdentity) null else op, commitNow = false)
    }

    fun resetAdjust(a: Adjust) = setAdjust(a, a.default)

    // Filters
    private fun refreshThumbnails(input: List<EditOp>) {
        thumbsJob?.cancel()
        thumbsJob = viewModelScope.launch {
            val src = runCatching { pipeline.render(input) }.getOrNull() ?: return@launch
            val thumb = pipeline.onRenderThread {
                val s = THUMB / maxOf(src.width, src.height).toFloat()
                Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true)
            }
            _state.update { it.copy(filterSource = thumb, filterThumbs = emptyMap()) }
            val map = LinkedHashMap<FilterPreset, Bitmap>()
            for (p in FilterPreset.entries) {
                val t = pipeline.onRenderThread { FilterPreset.apply(context, thumb, p, 1f) }
                map[p] = t
                _state.update { it.copy(filterThumbs = LinkedHashMap(map)) }
            }
        }
    }

    fun selectFilter(p: FilterPreset?) {
        val intensity = if (p == _state.value.selectedFilter) _state.value.filterIntensity else 1f
        _state.update { it.copy(selectedFilter = p, filterIntensity = intensity) }
        sessionEdit(Tool.Filters, p?.let { FilterOp(it, intensity) }, commitNow = true)
    }

    fun setFilterIntensity(v: Float) {
        val p = _state.value.selectedFilter ?: return
        _state.update { it.copy(filterIntensity = v) }
        sessionEdit(Tool.Filters, FilterOp(p, v.coerceIn(0f, 1f)), commitNow = false)
    }

    // Crop
    private fun startCrop() {
        viewModelScope.launch {
            val current = runCatching { pipeline.render(live) }.getOrNull() ?: return@launch
            _state.update { it.copy(crop = CropSession(working = current)) }
            baseForCrop = current
        }
    }

    private var baseForCrop: Bitmap? = null

    private fun updateCrop(transform: (CropSession) -> CropSession, reRender: Boolean) {
        val s = _state.value.crop ?: return
        val next = transform(s)
        _state.update { it.copy(crop = next) }
        if (reRender) {
            val base = baseForCrop ?: return
            viewModelScope.launch {
                val working = pipeline.onRenderThread { r -> if (next.transform.isIdentity) base else r.transform(base, next.transform) }
                _state.update { st -> st.crop?.let { st.copy(crop = it.copy(working = working, version = it.version + 1)) } ?: st }
            }
        }
    }

    fun rotateCrop(delta: Int) = updateCrop({ it.copy(transform = it.transform.copy(quarterTurns = it.transform.quarterTurns + delta)) }, true)
    fun flipCrop(horizontal: Boolean) = updateCrop({
        it.copy(transform = if (horizontal) it.transform.copy(flipH = !it.transform.flipH) else it.transform.copy(flipV = !it.transform.flipV))
    }, true)
    fun straighten(angle: Float) = updateCrop({ it.copy(transform = it.transform.copy(straighten = (angle * 10).roundToInt() / 10f)) }, true)
    fun setAspect(index: Int) = updateCrop({ it.copy(aspectIndex = index, perspective = false) }, false)
    fun setOutline(index: Int) = updateCrop({ it.copy(outlineIndex = index, perspective = false) }, false)
    fun setPerspective(on: Boolean) = updateCrop({ it.copy(perspective = on) }, false)
    fun resetCrop() = updateCrop({ it.copy(transform = TransformOp(), aspectIndex = 0, outlineIndex = 0, perspective = false) }, true)
    fun triggerCrop() = updateCrop({ it.copy(trigger = true) }, false)

    fun onCropped(rect: Rect, outline: CropOutline?) {
        val s = _state.value.crop ?: return
        val ops = live.toMutableList()
        if (!s.transform.isIdentity) ops += s.transform
        val full = rect.left < 0.002f && rect.top < 0.002f && rect.right > 0.998f && rect.bottom > 0.998f
        if (!full || outline != null) ops += CropOp(rect, outline)
        finishCrop(ops)
    }

    fun onPerspectiveCropped(corners: List<Offset>) {
        val s = _state.value.crop ?: return
        val ops = live.toMutableList()
        if (!s.transform.isIdentity) ops += s.transform
        if (corners.size == 4) ops += PerspectiveOp(corners)
        finishCrop(ops)
    }

    private fun finishCrop(ops: List<EditOp>) {
        flushSession()
        commit(ops)
        _state.update { it.copy(crop = null) }
        startCrop()
    }

    // Draw
    fun updateBrush(transform: (BrushSettings) -> BrushSettings) = _state.update { it.copy(brush = transform(it.brush)) }

    fun addStroke(points: List<Offset>) {
        if (points.isEmpty()) return
        val b = _state.value.brush
        val colorWithAlpha = (b.color and 0x00FFFFFF) or ((b.alpha * 255).roundToInt().coerceIn(0, 255) shl 24)
        val stroke = DrawStroke(
            brush = b.brush,
            shape = if (b.brush == BrushKind.Eraser) ShapeKind.Free else b.shape,
            color = colorWithAlpha,
            width = b.width,
            softness = b.softness,
            points = points,
            effectStrength = b.effectStrength,
            floodTolerance = b.tolerance,
            polygonVertices = b.vertices,
            seed = (System.nanoTime() and 0x7FFFFFFF).toInt()
        )
        val top = if (sessionTool == Tool.Draw && sessionIndex in live.indices) live[sessionIndex] as? DrawOp else null
        sessionEdit(Tool.Draw, DrawOp((top?.strokes ?: emptyList()) + stroke), commitNow = true)
    }

    // Text
    fun updateText(transform: (TextLayer) -> TextLayer) = _state.update { it.copy(text = transform(it.text)) }

    fun applyText() {
        val t = _state.value.text
        if (t.text.isBlank()) return
        flushSession()
        commit(live + TextOp(t))
        _state.update { it.copy(text = t.copy(text = "", center = Offset(0.5f, 0.5f), rotation = 0f)) }
    }

    // Background
    fun autoRemoveBackground() {
        if (_state.value.bgBusy) return
        _state.update { it.copy(bgBusy = true) }
        viewModelScope.launch {
            val input = runCatching { pipeline.render(live) }.getOrNull()
            val result = input?.let { bgRemover.segment(it) } ?: Result.failure(IllegalStateException("Couldn't read the image."))
            result.onSuccess { mask ->
                flushSession()
                sessionEdit(Tool.More, BackgroundOp(mask), commitNow = true)
                _state.update { it.copy(bgBusy = false, message = "Background removed") }
            }.onFailure { e ->
                _state.update { it.copy(bgBusy = false, message = e.message ?: "Couldn't remove the background.") }
            }
        }
    }

    fun setBgRestore(restore: Boolean) = _state.update { it.copy(bgRestore = restore) }
    fun setBgBrushWidth(w: Float) = _state.update { it.copy(bgBrushWidth = w) }

    fun addMaskStroke(points: List<Offset>) {
        if (points.isEmpty()) return
        val s = _state.value
        val stroke = MaskStroke(points, s.bgBrushWidth, s.bgRestore)
        val top = if (sessionTool == Tool.More && sessionIndex in live.indices) live[sessionIndex] as? BackgroundOp else null
        val op = top?.copy(strokes = top.strokes + stroke) ?: BackgroundOp(null, listOf(stroke))
        sessionEdit(Tool.More, op, commitNow = true)
    }

    // Watermark / resize / export settings
    fun updateWatermark(transform: (WatermarkParams) -> WatermarkParams) = _state.update { it.copy(watermark = transform(it.watermark)) }

    fun setWatermarkImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageIo.decode(context, uri, 1024) }
            if (bmp != null) updateWatermark { it.copy(image = bmp) }
        }
    }

    fun applyWatermark() {
        val p = _state.value.watermark
        if (p.text.isBlank() && p.image == null) return
        flushSession()
        commit(live + WatermarkOp(p))
        closeMore()
    }

    fun applyResize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val s = _state.value
        if (width == s.fullWidth && height == s.fullHeight) { closeMore(); return }
        flushSession()
        commit(live + ResizeOp(width.coerceAtMost(12000), height.coerceAtMost(12000)))
        closeMore()
    }

    fun updateExport(transform: (ExportSettings) -> ExportSettings) = _state.update { it.copy(export = transform(it.export)) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ── Export ───────────────────────────────────────────────────────────────────────────────

    private suspend fun renderFullResolution(): Bitmap? {
        val uri = sourceUri ?: return null
        val ops = live
        val maxMem = Runtime.getRuntime().maxMemory()
        // ~5 full-size ARGB bitmaps may coexist while replaying (input, output, layers).
        val maxPixels = (maxMem / (4L * 5)).coerceAtLeast(1_000_000L)
        val maxDim = if (EditPipeline.usesGpu(ops)) GPU_EXPORT_MAX else CPU_EXPORT_MAX
        val src = withContext(Dispatchers.IO) { ImageIo.decode(context, uri, maxDim, maxPixels) } ?: return null
        return pipeline.renderFull(src, ops) { p -> _state.update { it.copy(exportProgress = p) } }
    }

    private fun export(block: suspend (File) -> String?) {
        if (_state.value.exporting) return
        flushSession()
        _state.update { it.copy(exporting = true, exportProgress = 0f) }
        viewModelScope.launch {
            val msg = runCatching {
                val bmp = renderFullResolution() ?: return@runCatching "Couldn't render the image."
                val file = withContext(Dispatchers.IO) {
                    ImageIo.encodeToCache(context, bmp, _state.value.fileName, _state.value.export, sourceUri)
                }
                bmp.recycle()
                block(file)
            }.getOrElse { e -> if (e is OutOfMemoryError) "Not enough memory to export at full size." else "Couldn't export the image." }
            _state.update { it.copy(exporting = false, exportProgress = 0f, message = msg) }
        }
    }

    /** [onSaved] runs (main thread) only when the image actually landed in the gallery. */
    fun saveToGallery(onSaved: (() -> Unit)? = null) = export { file ->
        val uri = withContext(Dispatchers.IO) { ImageIo.publishToGallery(context, file, _state.value.export.format) }
        if (uri != null && onSaved != null) withContext(Dispatchers.Main) { onSaved() }
        if (uri != null) "Saved to Pictures/ClearPDF" else "Couldn't save image"
    }

    fun share(onShare: (Uri, String) -> Unit) = export { file ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        withContext(Dispatchers.Main) { onShare(uri, _state.value.export.format.mime) }
        null
    }

    /** Full-resolution edited image as a one-page PDF. */
    fun exportToPdf(onDone: (Uri?) -> Unit) {
        if (_state.value.exporting) return
        flushSession()
        _state.update { it.copy(exporting = true, exportProgress = 0f) }
        viewModelScope.launch {
            val out = runCatching {
                val bmp = renderFullResolution() ?: return@runCatching null
                withContext(Dispatchers.IO) {
                    val pdf = android.graphics.pdf.PdfDocument()
                    val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create())
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdf.finishPage(page)
                    val dir = File(context.cacheDir, "converted_pdfs").apply { mkdirs() }
                    val file = File(dir, "Image_${System.currentTimeMillis()}.pdf")
                    file.outputStream().use { pdf.writeTo(it) }
                    pdf.close()
                    bmp.recycle()
                    Uri.fromFile(file)
                }
            }.getOrNull()
            _state.update { it.copy(exporting = false, exportProgress = 0f, message = if (out == null) "Couldn't export PDF" else null) }
            onDone(out)
        }
    }

    override fun onCleared() {
        pipeline.release()
        super.onCleared()
    }

    companion object {
        const val PREVIEW_MAX = 1600
        private const val THUMB = 160f
        private const val MAX_HISTORY = 60
        private const val GPU_EXPORT_MAX = 4096
        private const val CPU_EXPORT_MAX = 8192

        /** Handy for the UI: whether [value] is effectively the default. */
        fun isDefault(a: Adjust, value: Float) = abs(value - a.default) < 0.5f
    }
}

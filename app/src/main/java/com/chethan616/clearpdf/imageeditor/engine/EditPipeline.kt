package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Replays an op list on the downscaled preview with a per-op bitmap cache: when only the last
 * op changes (slider drags, a new stroke) just that op is re-rendered on top of the cached
 * prefix. All rendering happens on one dedicated thread, which also keeps GPUImage's per-thread
 * EGL usage safe.
 *
 * Cached bitmaps are never recycled explicitly: any of them may be on screen right now, so they
 * are simply dropped and left to the GC.
 */
class EditPipeline(context: Context) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "image-editor-render") }
    val dispatcher = executor.asCoroutineDispatcher()
    private val renderer = EditRenderer(appContext)

    private var base: Bitmap? = null
    /** Preview-to-full-resolution pixel ratio (≤ 1). */
    var resolutionScale: Float = 1f
        private set

    private val cacheOps = ArrayList<EditOp>()
    private val cacheBitmaps = ArrayList<Bitmap?>()

    fun setBase(bitmap: Bitmap, fullWidth: Int) {
        base = bitmap
        resolutionScale = (bitmap.width.toFloat() / fullWidth.coerceAtLeast(1)).coerceAtMost(1f)
        cacheOps.clear()
        cacheBitmaps.clear()
    }

    suspend fun render(ops: List<EditOp>): Bitmap = withContext(dispatcher) {
        val src = base ?: error("No image")
        // Longest prefix that is still valid and has a bitmap to resume from.
        var same = 0
        while (same < ops.size && same < cacheOps.size && cacheOps[same] == ops[same]) same++
        var resume = same
        while (resume > 0 && cacheBitmaps[resume - 1] == null) resume--
        while (cacheOps.size > resume) {
            cacheOps.removeAt(cacheOps.lastIndex)
            cacheBitmaps.removeAt(cacheBitmaps.lastIndex)
        }
        var cur = if (resume == 0) src else cacheBitmaps[resume - 1]!!
        for (i in resume until ops.size) {
            coroutineContext.ensureActive()
            cur = renderer.apply(cur, ops[i], resolutionScale)
            cacheOps.add(ops[i])
            cacheBitmaps.add(cur)
        }
        // Memory cap: keep bitmaps only for the most recent steps (older ones re-render on demand).
        for (i in 0 until (cacheBitmaps.size - KEEP_CACHED).coerceAtLeast(0)) cacheBitmaps[i] = null
        cur
    }

    /** Renders on the render thread without touching the cache (thumbnails, crop working copies). */
    suspend fun <T> onRenderThread(block: (EditRenderer) -> T): T = withContext(dispatcher) { block(renderer) }

    /**
     * Full-resolution export: decodes the source under a memory budget, replays every op and
     * reports progress (0..1). Intermediate bitmaps are recycled as soon as they are consumed.
     */
    suspend fun renderFull(
        source: Bitmap,
        ops: List<EditOp>,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(dispatcher) {
        var cur = source
        ops.forEachIndexed { i, op ->
            coroutineContext.ensureActive()
            val next = renderer.apply(cur, op, 1f)
            if (cur !== source && cur !== next) cur.recycle()
            cur = next
            onProgress((i + 1f) / ops.size)
        }
        cur
    }

    fun release() {
        cacheOps.clear()
        cacheBitmaps.clear()
        base = null
        dispatcher.close()
    }

    companion object {
        private const val KEEP_CACHED = 6

        /** True when an op list would send full-resolution work through GL (texture-size cap). */
        fun usesGpu(ops: List<EditOp>): Boolean = ops.any { op ->
            when (op) {
                is AdjustOp -> op.values.any { (k, v) -> k.gpu && v != k.default }
                is FilterOp -> op.preset.gpu() != null
                else -> false
            }
        }
    }
}

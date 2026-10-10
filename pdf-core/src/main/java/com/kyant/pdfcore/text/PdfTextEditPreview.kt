package com.kyant.pdfcore.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlin.math.roundToInt

/**
 * What an in-place text edit will really look like: the page is edited with [PdfTextEditor] (the
 * same code the save path runs) and rendered by the same engine as the viewer's pages, then cut
 * into horizontal bands around the edited lines. The viewer lays those bands over its page bitmap,
 * so the preview is the saved result, pixel for pixel, not an approximation in a system font.
 */
object PdfTextEditPreview {

    /**
     * Renders [bands] (normalized display-space rects) of page [pageIndex] after [edits], at a page
     * width of [pageWidthPx]. Returns one bitmap per band, null where rendering failed (an
     * encrypted file, an out-of-memory page); callers keep their quick preview for those.
     */
    fun render(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        edits: List<PdfTextEdit>,
        bands: List<RectF>,
        pageWidthPx: Int
    ): List<Bitmap?> {
        if (bands.isEmpty() || pageWidthPx <= 0) return emptyList()
        PdfBox.ensureInitialized(context)
        val tmp = runCatching { File.createTempFile("text_edit_preview", ".pdf", context.cacheDir) }.getOrNull()
            ?: return bands.map { null }
        try {
            val saved = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { doc ->
                        if (pageIndex !in 0 until doc.numberOfPages) return@use false
                        PdfTextEditor.applyEdits(doc, pageIndex, edits)
                        // Only the edited page: a smaller file to write and for the renderer to open.
                        for (i in doc.numberOfPages - 1 downTo 0) if (i != pageIndex) doc.removePage(i)
                        doc.save(tmp)
                        true
                    }
                } ?: false
            }.getOrDefault(false)
            if (!saved) return bands.map { null }

            return ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val page = renderer.openPage(0)
                    try {
                        val srcW = page.width.toFloat().coerceAtLeast(1f)
                        val srcH = page.height.toFloat().coerceAtLeast(1f)
                        val pageHeightPx = pageWidthPx * (srcH / srcW)
                        bands.map { b ->
                            runCatching {
                                val w = ((b.right - b.left) * pageWidthPx).roundToInt().coerceAtLeast(1)
                                val h = ((b.bottom - b.top) * pageHeightPx).roundToInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                val m = Matrix().apply {
                                    postScale(pageWidthPx / srcW, pageHeightPx / srcH)
                                    postTranslate(-b.left * pageWidthPx, -b.top * pageHeightPx)
                                }
                                page.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }.getOrNull()
                        }
                    } finally {
                        runCatching { page.close() }
                    }
                }
            }
        } catch (_: Throwable) {
            return bands.map { null }
        } finally {
            tmp.delete()
        }
    }
}

package com.kyant.pdfcore.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import com.kyant.pdfcore.model.PdfDocument
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provides read-only access to a PDF file — opening, page rendering, and metadata.
 *
 * Implementations use Android's [android.graphics.pdf.PdfRenderer].
 */
interface PdfViewer {

    /**
     * Open a PDF from the given [uri] and return its metadata.
     */
    fun open(context: Context, uri: Uri): PdfDocument

    /**
     * Render a single page to a [Bitmap].
     *
     * @param document  The document previously returned by [open].
     * @param pageIndex Zero-based page index.
     * @param width     Desired bitmap width in pixels (the rendered output respects
     *                  the page aspect ratio; [rotationDegrees] is applied first).
     * @param rotationDegrees Clockwise rotation to bake into the bitmap (0/90/180/270).
     * @return A [Bitmap] of the rendered page, or `null` on failure.
     */
    fun renderPage(document: PdfDocument, pageIndex: Int, width: Int, rotationDegrees: Int = 0): Bitmap?

    /**
     * Return the natural size (in PDF points) of [pageIndex], or `null` on failure.
     */
    fun pageSize(document: PdfDocument, pageIndex: Int): Size?

    /**
     * Release all resources held for [document].
     */
    fun close(document: PdfDocument)
}

/**
 * Default implementation backed by [android.graphics.pdf.PdfRenderer].
 *
 * `PdfRenderer` allows only a **single open page per renderer at a time** and is not
 * thread-safe, so every renderer interaction is funnelled through [lock]. This is what
 * makes it safe for the viewer to pre-render neighbouring pages from parallel coroutines.
 */
class PdfViewerImpl : PdfViewer {

    private val lock = ReentrantLock()
    private val renderers = HashMap<Uri, android.graphics.pdf.PdfRenderer>()
    private val fds = HashMap<Uri, android.os.ParcelFileDescriptor>()

    override fun open(context: Context, uri: Uri): PdfDocument = lock.withLock {
        // Re-opening the same uri reuses nothing stale.
        renderers.remove(uri)?.runCatching { close() }
        fds.remove(uri)?.runCatching { close() }

        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file: $uri")
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        fds[uri] = fd
        renderers[uri] = renderer

        PdfDocument(
            uri = uri,
            name = queryFileName(context, uri) ?: "Unknown.pdf",
            pageCount = renderer.pageCount,
            sizeBytes = fd.statSize
        )
    }

    override fun renderPage(document: PdfDocument, pageIndex: Int, width: Int, rotationDegrees: Int): Bitmap? =
        lock.withLock {
            val renderer = renderers[document.uri] ?: return null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

            // `openPage` is a native call that can fail on a page some producers (e.g. a PdfBox-
            // decrypted copy) emit in a form pdfium dislikes; treat any failure as "no bitmap".
            val page = try {
                renderer.openPage(pageIndex)
            } catch (t: Throwable) {
                return@withLock null
            }
            var bitmap: Bitmap? = null
            try {
                val rotation = ((rotationDegrees % 360) + 360) % 360
                val swap = rotation == 90 || rotation == 270
                val srcW = page.width.toFloat().coerceAtLeast(1f)
                val srcH = page.height.toFloat().coerceAtLeast(1f)

                // Width/height of the *displayed* (post-rotation) page.
                val dispW = if (swap) srcH else srcW
                val dispH = if (swap) srcW else srcH

                val targetW = width.coerceAtLeast(1)
                val targetH = (targetW * (dispH / dispW)).toInt().coerceAtLeast(1)

                bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                // Map the source page into the (possibly rotated) target bitmap.
                val matrix = Matrix().apply {
                    if (rotation != 0) {
                        postRotate(rotation.toFloat())
                        // After rotation the content may be in negative space; translate back.
                        when (rotation) {
                            90 -> postTranslate(srcH, 0f)
                            180 -> postTranslate(srcW, srcH)
                            270 -> postTranslate(0f, srcW)
                        }
                    }
                    // Scale rotated content (dispW x dispH) to the target bitmap.
                    postScale(targetW / dispW, targetH / dispH)
                }

                page.render(bitmap, null, matrix, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (t: Throwable) {
                // Catch Throwable, not just Exception: a large page can throw OutOfMemoryError from
                // Bitmap.createBitmap, which is an Error — the old `catch (Exception)` let it escape and
                // crash the app mid-scroll (worst on big / decrypted PDFs). Free any partial bitmap so a
                // failed render doesn't itself leak the memory that caused the failure.
                bitmap?.recycle()
                null
            } finally {
                runCatching { page.close() }
            }
        }

    override fun pageSize(document: PdfDocument, pageIndex: Int): Size? = lock.withLock {
        val renderer = renderers[document.uri] ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        val page = renderer.openPage(pageIndex)
        try {
            Size(page.width, page.height)
        } finally {
            runCatching { page.close() }
        }
    }

    override fun close(document: PdfDocument) = lock.withLock {
        renderers.remove(document.uri)?.runCatching { close() }
        fds.remove(document.uri)?.runCatching { close() }
        Unit
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
}

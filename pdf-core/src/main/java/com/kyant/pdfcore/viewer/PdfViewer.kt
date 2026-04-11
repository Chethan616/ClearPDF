package com.kyant.pdfcore.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument

/**
 * Provides read-only access to a PDF file — opening, page rendering, and metadata.
 *
 * Implementations will use Android's PdfRenderer or a third-party library.
 */
interface PdfViewer {

    /**
     * Open a PDF from the given [uri] and return its metadata.
     *
     * @param context  Application or activity context for content resolution.
     * @param uri      Content URI pointing to the PDF file.
     * @return A [PdfDocument] describing the opened file.
     */
    fun open(context: Context, uri: Uri): PdfDocument

    /**
     * Render a single page to a [Bitmap].
     *
     * @param document  The document previously returned by [open].
     * @param pageIndex Zero-based page index.
     * @param width     Desired bitmap width in pixels.
     * @return A [Bitmap] of the rendered page, or `null` on failure.
     */
    fun renderPage(document: PdfDocument, pageIndex: Int, width: Int): Bitmap?

    /**
     * Release all resources held for [document].
     */
    fun close(document: PdfDocument)
}

/**
 * Default stub implementation.  Every method throws [NotImplementedError].
 */
class PdfViewerImpl : PdfViewer {

    private val renderers = mutableMapOf<Uri, android.graphics.pdf.PdfRenderer>()
    private val fds = mutableMapOf<Uri, android.os.ParcelFileDescriptor>()

    override fun open(context: Context, uri: Uri): PdfDocument {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file: $uri")
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        fds[uri] = fd
        renderers[uri] = renderer

        val name = queryFileName(context, uri) ?: "Unknown.pdf"
        val size = fd.statSize

        return PdfDocument(
            uri = uri,
            name = name,
            pageCount = renderer.pageCount,
            sizeBytes = size
        )
    }

    override fun renderPage(document: PdfDocument, pageIndex: Int, width: Int): Bitmap? {
        val renderer = renderers[document.uri] ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        val page = renderer.openPage(pageIndex)
        val ratio = page.height.toFloat() / page.width.toFloat()
        val height = (width * ratio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    override fun close(document: PdfDocument) {
        renderers.remove(document.uri)?.close()
        fds.remove(document.uri)?.close()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }
}

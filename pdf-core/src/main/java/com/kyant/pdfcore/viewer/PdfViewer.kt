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

    override fun open(context: Context, uri: Uri): PdfDocument {
        TODO("Open PDF using PdfRenderer or third-party library")
    }

    override fun renderPage(document: PdfDocument, pageIndex: Int, width: Int): Bitmap? {
        TODO("Render page bitmap — PdfRenderer.Page.render()")
    }

    override fun close(document: PdfDocument) {
        TODO("Close PdfRenderer and release file descriptor")
    }
}

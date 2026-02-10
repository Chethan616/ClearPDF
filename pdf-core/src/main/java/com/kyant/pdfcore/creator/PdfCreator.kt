package com.kyant.pdfcore.creator

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument

/**
 * Creates new PDF documents from text, images, or blank pages.
 *
 * Implementations will use Android's PdfDocument API or a third-party library.
 */
interface PdfCreator {

    /**
     * Create a blank PDF with the given number of pages.
     *
     * @param context    Application context.
     * @param pageCount  Number of blank pages to create.
     * @param outputUri  Destination URI for the new file.
     * @return A [PdfDocument] describing the created file.
     */
    fun createBlank(
        context: Context,
        pageCount: Int,
        outputUri: Uri
    ): PdfDocument

    /**
     * Create a PDF from a list of images (one image per page).
     *
     * @param context    Application context.
     * @param images     Ordered list of bitmaps to convert to pages.
     * @param outputUri  Destination URI.
     * @return A [PdfDocument] describing the created file.
     */
    fun createFromImages(
        context: Context,
        images: List<Bitmap>,
        outputUri: Uri
    ): PdfDocument

    /**
     * Create a single-page PDF containing the given [text].
     *
     * @param context    Application context.
     * @param text       Text content to render.
     * @param outputUri  Destination URI.
     * @return A [PdfDocument] describing the created file.
     */
    fun createFromText(
        context: Context,
        text: String,
        outputUri: Uri
    ): PdfDocument
}

/**
 * Default stub implementation.  Every method throws [NotImplementedError].
 */
class PdfCreatorImpl : PdfCreator {

    override fun createBlank(
        context: Context,
        pageCount: Int,
        outputUri: Uri
    ): PdfDocument {
        TODO("Create blank PDF using android.graphics.pdf.PdfDocument")
    }

    override fun createFromImages(
        context: Context,
        images: List<Bitmap>,
        outputUri: Uri
    ): PdfDocument {
        TODO("Draw each Bitmap onto a PdfDocument.Page canvas and save")
    }

    override fun createFromText(
        context: Context,
        text: String,
        outputUri: Uri
    ): PdfDocument {
        TODO("Layout text with StaticLayout, draw onto PdfDocument.Page canvas")
    }
}

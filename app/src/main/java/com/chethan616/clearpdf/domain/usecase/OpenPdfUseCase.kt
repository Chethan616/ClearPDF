package com.chethan616.clearpdf.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.viewer.PdfViewer

class OpenPdfUseCase(private val viewer: PdfViewer) {
    fun open(context: Context, uri: Uri): PdfDocument = viewer.open(context, uri)

    fun renderPage(document: PdfDocument, pageIndex: Int, width: Int, rotationDegrees: Int = 0): Bitmap? =
        viewer.renderPage(document, pageIndex, width, rotationDegrees)

    fun pageSize(document: PdfDocument, pageIndex: Int): Size? = viewer.pageSize(document, pageIndex)

    fun close(document: PdfDocument) = viewer.close(document)
}

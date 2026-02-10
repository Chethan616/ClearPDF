package com.chethan616.clearpdf.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kyant.pdfcore.model.PdfDocument
import com.kyant.pdfcore.viewer.PdfViewer

class OpenPdfUseCase(private val viewer: PdfViewer) {
    fun open(context: Context, uri: Uri): PdfDocument = viewer.open(context, uri)
    fun renderPage(document: PdfDocument, pageIndex: Int, width: Int): Bitmap? = viewer.renderPage(document, pageIndex, width)
    fun close(document: PdfDocument) = viewer.close(document)
}

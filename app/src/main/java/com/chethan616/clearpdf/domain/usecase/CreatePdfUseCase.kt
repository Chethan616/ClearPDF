package com.chethan616.clearpdf.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kyant.pdfcore.creator.PdfCreator
import com.kyant.pdfcore.model.PdfDocument

class CreatePdfUseCase(private val creator: PdfCreator) {
    fun createBlank(context: Context, pageCount: Int, outputUri: Uri): PdfDocument =
        creator.createBlank(context, pageCount, outputUri)
    fun createFromImages(context: Context, images: List<Bitmap>, outputUri: Uri): PdfDocument =
        creator.createFromImages(context, images, outputUri)
    fun createFromText(context: Context, text: String, outputUri: Uri): PdfDocument =
        creator.createFromText(context, text, outputUri)
}

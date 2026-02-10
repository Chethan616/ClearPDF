package com.chethan616.clearpdf.domain.usecase

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.merger.PdfMerger
import com.kyant.pdfcore.model.PdfDocument

class MergePdfUseCase(private val merger: PdfMerger) {
    fun merge(context: Context, sources: List<PdfDocument>, outputUri: Uri): PdfDocument =
        merger.merge(context, sources, outputUri)
}

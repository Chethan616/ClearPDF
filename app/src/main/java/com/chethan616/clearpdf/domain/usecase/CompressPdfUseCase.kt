package com.chethan616.clearpdf.domain.usecase

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.compressor.PdfCompressor
import com.kyant.pdfcore.model.CompressionQuality
import com.kyant.pdfcore.model.PdfDocument

class CompressPdfUseCase(private val compressor: PdfCompressor) {
    fun compress(context: Context, source: PdfDocument, quality: CompressionQuality, outputUri: Uri): PdfDocument =
        compressor.compress(context, source, quality, outputUri)
    fun estimateSize(source: PdfDocument, quality: CompressionQuality): Long =
        compressor.estimateSize(source, quality)
}

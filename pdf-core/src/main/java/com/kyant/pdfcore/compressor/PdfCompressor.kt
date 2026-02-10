package com.kyant.pdfcore.compressor

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.model.CompressionQuality
import com.kyant.pdfcore.model.PdfDocument

/**
 * Reduces the file size of a PDF document.
 *
 * Implementations may re-encode images, strip metadata, or use a PDF
 * optimization library.
 */
interface PdfCompressor {

    /**
     * Compress [source] and write the result to [outputUri].
     *
     * @param context    Application context.
     * @param source     The PDF to compress.
     * @param quality    Desired compression level.
     * @param outputUri  Destination URI for the compressed file.
     * @return A [PdfDocument] describing the compressed output.
     */
    fun compress(
        context: Context,
        source: PdfDocument,
        quality: CompressionQuality,
        outputUri: Uri
    ): PdfDocument

    /**
     * Estimate the compressed size without writing a file.
     *
     * @param source   The PDF to analyse.
     * @param quality  Desired compression level.
     * @return Estimated output size in bytes, or -1 if unknown.
     */
    fun estimateSize(
        source: PdfDocument,
        quality: CompressionQuality
    ): Long
}

/**
 * Default stub implementation.  Every method throws [NotImplementedError].
 */
class PdfCompressorImpl : PdfCompressor {

    override fun compress(
        context: Context,
        source: PdfDocument,
        quality: CompressionQuality,
        outputUri: Uri
    ): PdfDocument {
        TODO("Re-encode images at reduced quality, strip unused objects, write to outputUri")
    }

    override fun estimateSize(
        source: PdfDocument,
        quality: CompressionQuality
    ): Long {
        TODO("Analyse source structure and estimate compressed size")
    }
}

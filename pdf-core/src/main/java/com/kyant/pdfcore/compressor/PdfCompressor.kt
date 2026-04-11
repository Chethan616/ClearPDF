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
        val jpegQuality = when (quality) {
            CompressionQuality.LOW -> 30
            CompressionQuality.MEDIUM -> 55
            CompressionQuality.HIGH -> 80
        }
        val scaleFactor = when (quality) {
            CompressionQuality.LOW -> 0.5f
            CompressionQuality.MEDIUM -> 0.7f
            CompressionQuality.HIGH -> 0.85f
        }

        val sourceFd = context.contentResolver.openFileDescriptor(source.uri, "r")
            ?: throw IllegalArgumentException("Cannot open source PDF")

        sourceFd.use { fd ->
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val outDoc = android.graphics.pdf.PdfDocument()
            var reusableBitmap: android.graphics.Bitmap? = null
            val matrix = android.graphics.Matrix()
            val stream = java.io.ByteArrayOutputStream(256 * 1024)
            try {
                for (i in 0 until renderer.pageCount) {
                    val srcPage = renderer.openPage(i)
                    try {
                        val origW = srcPage.width
                        val origH = srcPage.height
                        val w = (origW * scaleFactor).toInt().coerceAtLeast(1)
                        val h = (origH * scaleFactor).toInt().coerceAtLeast(1)

                        val workingBitmap = obtainReusableBitmap(reusableBitmap, w, h)
                        if (workingBitmap !== reusableBitmap) {
                            reusableBitmap?.recycle()
                            reusableBitmap = workingBitmap
                        }

                        workingBitmap.eraseColor(android.graphics.Color.WHITE)
                        matrix.reset()
                        matrix.setScale(scaleFactor, scaleFactor)
                        srcPage.render(
                            workingBitmap,
                            null,
                            matrix,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )

                        // Re-encode as JPEG to compress.
                        stream.reset()
                        workingBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                        val compressed = android.graphics.BitmapFactory.decodeByteArray(
                            stream.toByteArray(),
                            0,
                            stream.size()
                        ) ?: throw IllegalStateException("Failed to compress page ${i + 1}")

                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(origW, origH, i).create()
                        val page = outDoc.startPage(pageInfo)
                        val destRect = android.graphics.RectF(0f, 0f, origW.toFloat(), origH.toFloat())
                        page.canvas.drawBitmap(compressed, null, destRect, null)
                        outDoc.finishPage(page)
                        compressed.recycle()
                    } finally {
                        srcPage.close()
                    }
                }

                val compressOutput = context.contentResolver.openOutputStream(outputUri)
                    ?: throw IllegalStateException("Cannot write compressed PDF output")
                compressOutput.use {
                    outDoc.writeTo(it)
                    it.flush()
                }
            } finally {
                reusableBitmap?.recycle()
                outDoc.close()
                renderer.close()
            }
        }

        val outSize = try {
            context.contentResolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) { -1L }

        return PdfDocument(
            uri = outputUri,
            name = "Compressed.pdf",
            pageCount = source.pageCount,
            sizeBytes = outSize
        )
    }

    override fun estimateSize(
        source: PdfDocument,
        quality: CompressionQuality
    ): Long {
        if (source.sizeBytes <= 0) return -1
        val ratio = when (quality) {
            CompressionQuality.LOW -> 0.25
            CompressionQuality.MEDIUM -> 0.5
            CompressionQuality.HIGH -> 0.75
        }
        return (source.sizeBytes * ratio).toLong()
    }

    private fun obtainReusableBitmap(
        existing: android.graphics.Bitmap?,
        width: Int,
        height: Int
    ): android.graphics.Bitmap {
        if (existing != null && !existing.isRecycled && existing.width == width && existing.height == height) {
            return existing
        }
        return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    }
}

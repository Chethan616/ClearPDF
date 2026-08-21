package com.kyant.pdfcore.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kyant.pdfcore.raster.PdfRasterizer.ImageFormat
import java.io.File
import java.io.FileOutputStream

/**
 * On-device image processing: resize, re-encode (JPG/PNG/WebP) and compress. Re-encoding a
 * decoded [Bitmap] inherently drops all source metadata (EXIF/GPS), so the output is stripped
 * of tracking data by construction. Nothing leaves the device.
 */
object ImageProcessor {

    data class Result(val uri: Uri, val file: File, val width: Int, val height: Int, val sizeBytes: Long)
    data class SourceInfo(val width: Int, val height: Int, val sizeBytes: Long)

    /** Read dimensions + byte size without decoding the full bitmap. */
    fun inspect(context: Context, source: Uri): SourceInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val size = context.contentResolver.openFileDescriptor(source, "r")?.use { it.statSize } ?: -1L
        return SourceInfo(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), size)
    }

    /**
     * @param format       output encoding.
     * @param quality      0..100 (ignored for lossless PNG).
     * @param scalePercent 10..100 of the source dimensions.
     */
    fun process(
        context: Context,
        source: Uri,
        format: ImageFormat,
        quality: Int,
        scalePercent: Int
    ): Result {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalStateException("Unable to read image")
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) throw IllegalStateException("Unsupported image")

        val scale = scalePercent.coerceIn(10, 100) / 100f
        val targetW = (srcW * scale).toInt().coerceAtLeast(1)
        val targetH = (srcH * scale).toInt().coerceAtLeast(1)

        // Downsample while decoding to keep memory bounded for large photos.
        var sample = 1
        while (srcW / (sample * 2) >= targetW && srcH / (sample * 2) >= targetH) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: throw IllegalStateException("Unable to decode image")

        val scaled = if (decoded.width != targetW || decoded.height != targetH) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { if (it != decoded) decoded.recycle() }
        } else decoded

        // JPEG has no alpha channel; flatten transparency onto white so it doesn't render black.
        val output = if (format == ImageFormat.JPEG && scaled.hasAlpha()) {
            Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also { bmp ->
                Canvas(bmp).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
                scaled.recycle()
            }
        } else scaled

        val runDir = File(File(context.cacheDir, "image_tools"), "run_${System.currentTimeMillis()}").apply { mkdirs() }
        val file = File(runDir, "image_${System.currentTimeMillis()}.${format.extension}")
        FileOutputStream(file).use { out ->
            val cf = when (format) {
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            }
            output.compress(cf, quality.coerceIn(0, 100), out)
        }
        val w = output.width
        val h = output.height
        output.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Result(uri, file, w, h, file.length())
    }

    /** Save a processed file into the shared Pictures/[albumName] collection. */
    fun saveToGallery(context: Context, file: File, format: ImageFormat, albumName: String = "ClearPDF"): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$albumName")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val item = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(item)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }
}

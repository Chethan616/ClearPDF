package com.chethan616.clearpdf.imageeditor.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    Jpeg("JPEG", "image/jpeg", "jpg"),
    Png("PNG", "image/png", "png"),
    Webp("WEBP", "image/webp", "webp");

    @Suppress("DEPRECATION")
    val compress: Bitmap.CompressFormat
        get() = when (this) {
            Jpeg -> Bitmap.CompressFormat.JPEG
            Png -> Bitmap.CompressFormat.PNG
            Webp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        }
}

enum class MetadataMode { Keep, RemoveLocation, RemoveAll }

data class ExportSettings(
    val format: ExportFormat = ExportFormat.Jpeg,
    val quality: Int = 92,
    val metadata: MetadataMode = MetadataMode.RemoveLocation
)

object ImageIo {

    data class Bounds(val width: Int, val height: Int)

    /** Pixel size after EXIF orientation is applied. */
    fun bounds(context: Context, uri: Uri): Bounds? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val rot = orientationDegrees(context, uri)
        return if (rot % 180 != 0) Bounds(o.outHeight, o.outWidth) else Bounds(o.outWidth, o.outHeight)
    }

    /**
     * Decodes [uri] so its long edge is at most [maxDim] and its pixel count fits [maxPixels],
     * with EXIF orientation baked in.
     */
    fun decode(context: Context, uri: Uri, maxDim: Int, maxPixels: Long = Long.MAX_VALUE): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        val w = o.outWidth
        val h = o.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (max(w, h) / sample > maxDim * 2 || (w.toLong() / sample) * (h.toLong() / sample) > maxPixels * 4) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        // Exact downscale to the budget.
        val scale = minOf(1f, maxDim.toFloat() / max(bmp.width, bmp.height),
            kotlin.math.sqrt(maxPixels.toDouble() / (bmp.width.toLong() * bmp.height)).toFloat())
        val m = Matrix()
        if (scale < 0.999f) m.postScale(scale, scale)
        applyOrientation(m, exifOrientation(context, uri))
        if (!m.isIdentity) {
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t !== bmp) bmp.recycle()
            bmp = t
        }
        return if (bmp.config != Bitmap.Config.ARGB_8888) bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() } else bmp
    }

    private fun exifOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun orientationDegrees(context: Context, uri: Uri): Int = when (exifOrientation(context, uri)) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    private fun applyOrientation(m: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
    }

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i != -1 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Image"

    // ── EXIF ────────────────────────────────────────────────────────────────────────────────

    private val ViewTags = listOf(
        "Camera make" to ExifInterface.TAG_MAKE,
        "Camera model" to ExifInterface.TAG_MODEL,
        "Date taken" to ExifInterface.TAG_DATETIME_ORIGINAL,
        "Lens" to ExifInterface.TAG_LENS_MODEL,
        "Focal length" to ExifInterface.TAG_FOCAL_LENGTH,
        "Aperture" to ExifInterface.TAG_F_NUMBER,
        "Exposure time" to ExifInterface.TAG_EXPOSURE_TIME,
        "ISO" to ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        "Flash" to ExifInterface.TAG_FLASH,
        "White balance" to ExifInterface.TAG_WHITE_BALANCE,
        "Software" to ExifInterface.TAG_SOFTWARE,
        "Artist" to ExifInterface.TAG_ARTIST,
        "Copyright" to ExifInterface.TAG_COPYRIGHT,
        "Description" to ExifInterface.TAG_IMAGE_DESCRIPTION,
    )

    /** Tags carried over on export (orientation is reset because edits bake it in). */
    private val CopyTags = ViewTags.map { it.second } + listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_LENS_MAKE,
    )

    private val GpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
    )

    data class ExifEntry(val label: String, val value: String)

    fun readExif(context: Context, uri: Uri): List<ExifEntry> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val list = ViewTags.mapNotNull { (label, tag) ->
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { ExifEntry(label, it) }
            }.toMutableList()
            exif.latLong?.let { (lat, lon) -> list += ExifEntry("Location", "%.5f, %.5f".format(lat, lon)) }
            list
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun copyExif(context: Context, source: Uri, target: File, mode: MetadataMode) {
        if (mode == MetadataMode.RemoveAll) return
        runCatching {
            val src = context.contentResolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
            val dst = ExifInterface(target.absolutePath)
            val tags = if (mode == MetadataMode.Keep) CopyTags + GpsTags else CopyTags
            tags.forEach { tag -> src.getAttribute(tag)?.let { dst.setAttribute(tag, it) } }
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dst.setAttribute(ExifInterface.TAG_SOFTWARE, "ClearPDF")
            dst.saveAttributes()
        }
    }

    // ── Saving ──────────────────────────────────────────────────────────────────────────────

    /** Encodes to a cache file (with EXIF policy applied). */
    fun encodeToCache(context: Context, bmp: Bitmap, baseName: String, settings: ExportSettings, source: Uri?): File {
        val dir = File(context.cacheDir, "edited_images").apply { mkdirs() }
        val safe = baseName.substringBeforeLast('.').ifBlank { "image" }.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val file = File(dir, "${safe}_edited_${System.currentTimeMillis()}.${settings.format.ext}")
        file.outputStream().use { bmp.compress(settings.format.compress, settings.quality.coerceIn(1, 100), it) }
        // androidx ExifInterface can write JPEG, PNG and WebP containers.
        if (source != null) copyExif(context, source, file, settings.metadata)
        return file
    }

    /** Copies an encoded file into Pictures/ClearPDF. Returns the new content Uri. */
    fun publishToGallery(context: Context, file: File, format: ExportFormat): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ClearPDF")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val item = context.contentResolver.insert(collection, values) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(item)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            item
        }.getOrElse {
            context.contentResolver.delete(item, null, null)
            null
        }
    }
}

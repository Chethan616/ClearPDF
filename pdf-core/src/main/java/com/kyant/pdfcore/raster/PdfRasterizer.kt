package com.kyant.pdfcore.raster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Renders PDF pages to raster image files (JPEG / PNG / WebP) fully on-device using
 * the platform [PdfRenderer]. Output is written to the app cache and exposed through
 * a [FileProvider] content Uri so it can be shared or opened.
 */
object PdfRasterizer {

    enum class ImageFormat(val extension: String, val mime: String) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp")
    }

    data class RasterPage(val pageIndex: Int, val uri: Uri, val file: File)

    /**
     * Render every page of [source] to an image.
     *
     * @param dpi target render density; 150 is a good screen/print compromise.
     * @param quality 0..100 (ignored for lossless PNG).
     * @param onProgress invoked as pages complete: (done, total).
     */
    fun rasterize(
        context: Context,
        source: Uri,
        format: ImageFormat,
        dpi: Int = 150,
        quality: Int = 90,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): List<RasterPage> {
        val outDir = File(context.cacheDir, "pdf_images").apply { mkdirs() }
        // A fresh sub-folder per run keeps exports from previous runs from piling up in shares.
        val runDir = File(outDir, "run_${System.currentTimeMillis()}").apply { mkdirs() }
        val results = mutableListOf<RasterPage>()

        val pfd = context.contentResolver.openFileDescriptor(source, "r")
            ?: throw IllegalStateException("Cannot open PDF")

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val scale = dpi / 72f
                for (i in 0 until total) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        // Paint a white backing so transparent PDF areas don't render black in JPEG.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val file = File(runDir, "page_${i + 1}.${format.extension}")
                        FileOutputStream(file).use { out ->
                            val compressFormat = when (format) {
                                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                                ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
                            }
                            bitmap.compress(compressFormat, quality.coerceIn(0, 100), out)
                        }
                        bitmap.recycle()

                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        results.add(RasterPage(i, uri, file))
                    }
                    onProgress?.invoke(i + 1, total)
                }
            }
        }
        return results
    }

    /** Persist all rendered pages to the shared Pictures collection via MediaStore. */
    fun exportToGallery(context: Context, pages: List<RasterPage>, format: ImageFormat, albumName: String = "ClearPDF"): Int {
        var saved = 0
        val resolver = context.contentResolver
        pages.forEach { page ->
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, page.file.name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, format.mime)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${android.os.Environment.DIRECTORY_PICTURES}/$albumName"
                    )
                }
            }
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val item = resolver.insert(collection, values) ?: return@forEach
            runCatching {
                resolver.openOutputStream(item)?.use { out ->
                    page.file.inputStream().use { it.copyTo(out) }
                }
                saved++
            }
        }
        return saved
    }
}

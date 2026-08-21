package com.chethan616.clearpdf.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real image editing on the ORIGINAL image: rotate, colour filters, brightness/contrast → save. */
class ImageEditorViewModel : ViewModel() {

    enum class ImgFilter { None, Mono, Sepia, Vivid, Cool, Warm }

    data class UiState(
        val fileName: String = "",
        val preview: Bitmap? = null,     // downsampled, rotation already baked in
        val rotation: Int = 0,           // 0 / 90 / 180 / 270 (relative to source)
        val filter: ImgFilter = ImgFilter.None,
        val brightness: Float = 0f,      // -100..100
        val contrast: Float = 1f,        // 0.5..2.0 (1 = none)
        val isLoading: Boolean = true,
        val error: String? = null,
        val savedMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var sourceUri: Uri? = null
    private var basePreview: Bitmap? = null   // unrotated, downsampled source
    private var started = false

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        sourceUri = uri
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { queryName(context, uri) }
            val bmp = withContext(Dispatchers.IO) { decodeDownsampled(context, uri, 1600) }
            basePreview = bmp
            _state.value = if (bmp == null) {
                UiState(fileName = name, isLoading = false, error = "Couldn't open this image.")
            } else {
                UiState(fileName = name, preview = bmp, isLoading = false)
            }
        }
    }

    fun rotate(deltaDeg: Int) {
        val s = _state.value
        val base = basePreview ?: return
        val newRot = ((s.rotation + deltaDeg) % 360 + 360) % 360
        val rotated = if (newRot == 0) base else rotateBitmap(base, newRot.toFloat())
        _state.value = s.copy(rotation = newRot, preview = rotated)
    }

    fun setFilter(f: ImgFilter) { _state.value = _state.value.copy(filter = f) }
    fun setBrightness(v: Float) { _state.value = _state.value.copy(brightness = v.coerceIn(-100f, 100f)) }
    fun setContrast(v: Float) { _state.value = _state.value.copy(contrast = v.coerceIn(0.5f, 2f)) }
    fun reset() { _state.value = _state.value.copy(filter = ImgFilter.None, brightness = 0f, contrast = 1f) }

    fun dismissMessage() { _state.value = _state.value.copy(savedMessage = null) }

    /** Render the FULL-resolution edited image and save it to the gallery. */
    fun saveToGallery(context: Context) {
        val uri = sourceUri ?: return
        val s = _state.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val full = decodeDownsampled(context, uri, 4096) ?: return@runCatching false
                    val edited = renderEdited(full, s)
                    val saved = saveBitmap(context, edited, s.fileName)
                    edited.recycle(); if (edited != full) full.recycle()
                    saved
                }.getOrDefault(false)
            }
            _state.value = _state.value.copy(savedMessage = if (ok) "Saved to gallery" else "Couldn't save image")
        }
    }

    /** Render the FULL-resolution edited image into a single-page PDF and hand back its URI. */
    fun exportToPdf(context: Context, onDone: (Uri?) -> Unit) {
        val uri = sourceUri ?: return onDone(null)
        val s = _state.value
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val full = decodeDownsampled(context, uri, 4096) ?: return@runCatching null
                    val edited = renderEdited(full, s)
                    val pdf = android.graphics.pdf.PdfDocument()
                    val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(edited.width, edited.height, 1).create())
                    page.canvas.drawBitmap(edited, 0f, 0f, null)
                    pdf.finishPage(page)
                    val dir = java.io.File(context.cacheDir, "converted_pdfs").apply { mkdirs() }
                    val file = java.io.File(dir, "Image_${System.currentTimeMillis()}.pdf")
                    java.io.FileOutputStream(file).use { pdf.writeTo(it) }
                    pdf.close(); edited.recycle(); if (edited != full) full.recycle()
                    Uri.fromFile(file)
                }.getOrNull()
            }
            onDone(out)
        }
    }

    // ── rendering ────────────────────────────────────────────────────────────────

    /** Apply rotation + colour matrix to a bitmap. Compose preview applies the colour matrix live,
     *  so this is only used for the exported/saved output. */
    private fun renderEdited(src: Bitmap, s: UiState): Bitmap {
        val rotated = if (s.rotation == 0) src else rotateBitmap(src, s.rotation.toFloat())
        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(colorMatrixFor(s.filter, s.brightness, s.contrast)))
        }
        Canvas(out).drawBitmap(rotated, 0f, 0f, paint)
        if (rotated != src) rotated.recycle()
        return out
    }

    private fun rotateBitmap(src: Bitmap, deg: Float): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(deg) }, true)

    private fun saveBitmap(context: Context, bmp: Bitmap, name: String): Boolean {
        val base = name.substringBeforeLast('.').ifBlank { "image" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_edited_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ClearPDF")
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val item = context.contentResolver.insert(collection, values) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(item)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            true
        }.getOrDefault(false)
    }

    private fun decodeDownsampled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (w / (sample * 2) >= maxDim || h / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "Image"

    companion object {
        /** Android [ColorMatrix] float[20] for a filter + brightness (-100..100) + contrast (0.5..2). */
        fun colorMatrixFor(filter: ImgFilter, brightness: Float, contrast: Float): FloatArray {
            val m = ColorMatrix()
            when (filter) {
                ImgFilter.None -> {}
                ImgFilter.Mono -> m.setSaturation(0f)
                ImgFilter.Sepia -> {
                    m.setSaturation(0f)
                    m.postConcat(ColorMatrix(floatArrayOf(
                        1.0f, 0f, 0f, 0f, 40f,
                        0f, 0.95f, 0f, 0f, 20f,
                        0f, 0f, 0.82f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )))
                }
                ImgFilter.Vivid -> m.setSaturation(1.6f)
                ImgFilter.Cool -> m.postConcat(ColorMatrix(floatArrayOf(
                    0.95f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )))
                ImgFilter.Warm -> m.postConcat(ColorMatrix(floatArrayOf(
                    1.12f, 0f, 0f, 0f, 12f,
                    0f, 1.0f, 0f, 0f, 4f,
                    0f, 0f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            // Contrast: scale around mid-grey (128). translate = 128*(1-contrast).
            val c = contrast
            val t = (-.5f * c + .5f) * 255f
            m.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
            // Brightness: additive.
            val b = brightness
            m.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )))
            return m.array
        }
    }
}

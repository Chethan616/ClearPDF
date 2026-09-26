package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Produces a subject mask for automatic background removal. Kept behind an interface so a FOSS
 * build flavour can provide a different (or no) implementation without touching the editor.
 */
interface BackgroundRemover {
    /** False when the device / build can't run automatic removal at all. */
    val isAvailable: Boolean

    /**
     * Returns an [Bitmap.Config.ALPHA_8] mask the size of [image] (opaque = keep), or a failure
     * whose message is user-presentable.
     */
    suspend fun segment(image: Bitmap): Result<Bitmap>

    companion object {
        fun create(context: Context): BackgroundRemover = MlKitBackgroundRemover(context.applicationContext)
    }
}

/** Google Play services ML Kit subject segmentation (model is fetched by Play services). */
private class MlKitBackgroundRemover(private val context: Context) : BackgroundRemover {

    override val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hasPlayServices()

    private fun hasPlayServices(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0); true
    }.getOrDefault(false)

    override suspend fun segment(image: Bitmap): Result<Bitmap> {
        if (!isAvailable) return Result.failure(IllegalStateException("Background removal isn't available on this device."))
        return runCatching { segmentImpl(image) }
    }

    private suspend fun segmentImpl(image: Bitmap): Bitmap {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val client = SubjectSegmentation.getClient(options)
        try {
            val input = InputImage.fromBitmap(image, 0)
            val result = suspendCancellableCoroutine { cont ->
                client.process(input)
                    .addOnSuccessListener { cont.resume(Result.success(it)) }
                    .addOnFailureListener { cont.resume(Result.failure(it)) }
            }.getOrElse { e ->
                val msg = e.message.orEmpty()
                throw IllegalStateException(
                    if (msg.contains("download", true) || msg.contains("module", true))
                        "The background-removal model is still downloading. Try again in a moment."
                    else "Couldn't detect a subject in this image."
                )
            }
            val buffer = result.foregroundConfidenceMask
                ?: throw IllegalStateException("Couldn't detect a subject in this image.")
            val w = image.width
            val h = image.height
            buffer.rewind()
            val pixels = IntArray(w * h)
            for (i in 0 until w * h) {
                val c = buffer.get().coerceIn(0f, 1f)
                pixels[i] = ((c * 255f).toInt() shl 24)
            }
            val argb = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            val mask = argb.extractAlpha()
            argb.recycle()
            return mask
        } finally {
            client.close()
        }
    }
}

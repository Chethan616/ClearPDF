package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap

/**
 * F-Droid build: automatic background removal needs Google's ML Kit, which isn't free software,
 * so it's simply not offered (the editor hides it when [BackgroundRemover.isAvailable] is false).
 */
internal object BackgroundRemoverFlavor {
    fun create(context: Context): BackgroundRemover = object : BackgroundRemover {
        override val isAvailable: Boolean = false
        override suspend fun segment(image: Bitmap): Result<Bitmap> =
            Result.failure(UnsupportedOperationException("Background removal isn't available in this build."))
    }
}

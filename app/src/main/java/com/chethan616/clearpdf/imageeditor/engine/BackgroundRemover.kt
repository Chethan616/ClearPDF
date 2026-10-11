package com.chethan616.clearpdf.imageeditor.engine

import android.content.Context
import android.graphics.Bitmap

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
        fun create(context: Context): BackgroundRemover = BackgroundRemoverFlavor.create(context.applicationContext)
    }
}

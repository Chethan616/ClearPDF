package com.chethan616.clearpdf.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Manages saved signature bitmaps in app-private storage.
 * Signatures are stored as PNGs under filesDir/signatures/.
 */
object SignatureManager {
    private const val DIR_NAME = "signatures"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Returns all saved signature files, newest first. */
    fun listSignatures(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Saves a transparent-background signature bitmap using a human-readable name. */
    fun saveSignature(context: Context, bitmap: Bitmap, name: String = "Signature"): File {
        val safeName = name
            .trim()
            .ifBlank { "Signature" }
            .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "Signature" }
        val file = File(dir(context), "${safeName}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    /** Returns the display name stored in a signature filename. */
    fun displayName(file: File): String {
        val base = file.nameWithoutExtension
        val withoutTimestamp = base.substringBeforeLast('_').takeIf {
            base.substringAfterLast('_').toLongOrNull() != null
        } ?: base
        return withoutTimestamp
            .removePrefix("sig")
            .replace('_', ' ')
            .trim()
            .ifBlank { "Signature" }
    }

    /** Loads a signature from file. Returns null if the file is missing or corrupt. */
    fun loadSignature(file: File): Bitmap? = try {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
        if (bmp != null && (bmp.config == Bitmap.Config.HARDWARE || !bmp.isMutable)) {
            bmp.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bmp
        }
    } catch (_: Exception) {
        null
    }

    /** Deletes a signature file. */
    fun deleteSignature(file: File) {
        file.delete()
    }
}

package com.chethan616.clearpdf.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Keeps a durable, private copy of every externally-picked document the app opens, keyed by the
 * URI the user picked — so a document opened via "Open with ClearPDF" from another app is still
 * openable from Recents days later, not just in the minutes right after.
 *
 * Why this is needed at all: a URI handed to the app in a share/view intent from a foreign app
 * (WhatsApp, Gmail, a file manager, ...) carries only a *temporary* read grant. `takePersistableUriPermission`
 * is called on every such URI throughout this app, but it only succeeds when the sender actually
 * attached `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` — many senders don't, and the call then throws
 * and is (correctly) swallowed. Without a persistable grant, Android revokes read access once the
 * sending app's task finishes, which on many devices is within minutes, not "after some time" as it
 * might appear from inside this app. The document then opened once and can never be read again —
 * which is exactly "Failed to open PDF." / "Couldn't read this spreadsheet." from Recents.
 *
 * SAF picks (this app's own document picker) mostly don't need this — `takePersistableUriPermission`
 * on those genuinely persists. This mirror is a no-op safety net for them: [resolve] sees the
 * original is still readable and returns it unchanged, only refreshing the mirror file in the
 * background for the case where that ever stops being true (a doc-provider revoking access, an SD
 * card removed, ...).
 */
object LocalDocumentMirror {
    private const val PREFS_NAME = "clearpdf_doc_mirror"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** App-private and durable — survives a system cache clear, unlike `cacheDir`. */
    private fun mirrorDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(base, "mirrored").apply { mkdirs() }
    }

    /**
     * The best URI to read [original] from right now.
     *
     * - If [original] is still readable, this returns it as-is (no detour through a copy for the
     *   common case) and opportunistically writes/refreshes the mirror for later.
     * - If [original] is no longer readable, this returns a previously-saved mirror instead, if one
     *   exists.
     * - If neither is readable — the very first open of a URI whose grant is already gone, which
     *   this app never had a chance to save — [original] is returned unchanged and the caller's
     *   normal "couldn't open" handling applies. There is nothing this mirror can do about a file
     *   it was never able to read even once.
     *
     * @param extensionHint the source file's real extension (`"pdf"`, `"xlsx"`, `"docx"`, …),
     *   without a dot — so the mirror's filename still sniffs as the right format downstream.
     */
    fun resolve(context: Context, original: Uri, extensionHint: String): Uri {
        val originalSize = runCatching {
            context.contentResolver.openFileDescriptor(original, "r")?.use { it.statSize }
        }.getOrNull()

        if (originalSize != null && originalSize > 0L) {
            runCatching { mirror(context, original, extensionHint, originalSize) }
            return original
        }

        val savedPath = prefs(context).getString(key(original), null)
        val mirrored = savedPath?.let { File(it) }
        if (mirrored != null && mirrored.exists() && mirrored.length() > 0L) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", mirrored)
        }
        return original
    }

    /** Copies [original] into durable storage, skipping the copy if an up-to-date one already exists. */
    private fun mirror(context: Context, original: Uri, extensionHint: String, currentSize: Long) {
        val cleanExt = extensionHint.trimStart('.').ifBlank { "pdf" }
        val file = File(mirrorDir(context), "${key(original)}.$cleanExt")

        // A document that hasn't changed size since it was last mirrored is treated as unchanged.
        // This mirror exists purely as an access-durability net, not a sync mechanism, so a cheap
        // heuristic that avoids re-copying on every single open is the right trade-off.
        if (file.exists() && file.length() == currentSize) {
            prefs(context).edit().putString(key(original), file.absolutePath).apply()
            return
        }

        val tmp = File(file.parentFile, "${file.name}.tmp")
        context.contentResolver.openInputStream(original)?.use { input ->
            FileOutputStream(tmp).use { input.copyTo(it) }
        } ?: return

        if (tmp.length() <= 0L) {
            tmp.delete()
            return
        }
        file.delete()
        if (tmp.renameTo(file)) {
            prefs(context).edit().putString(key(original), file.absolutePath).apply()
        } else {
            tmp.delete()
        }
    }

    /** Forgets and deletes the mirror for one URI — called when its Recents entry is removed. */
    fun forget(context: Context, original: Uri) {
        val savedPath = prefs(context).getString(key(original), null) ?: return
        runCatching { File(savedPath).delete() }
        prefs(context).edit().remove(key(original)).apply()
    }
}

package com.chethan616.clearpdf.office

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * On-disk home of the Office engine. Layout (under noBackupFilesDir, never backed up):
 *
 *   office-engine/
 *     current                  name of the active engine directory
 *     <version>-<abi>/         engine directory = LibreOfficeKit's $APP_DATA_DIR
 *       .ready                 written last; the directory is valid only if present
 *       lib/  program/  share/  etc/  user/  cache/ (created by LibreOffice)
 *     staging/                 an install in progress; renamed into place atomically
 *     download/                resumable download parts (foss)
 *
 * Installs never touch the active engine until the new one is complete: [commit] renames the
 * finished staging directory into place, flips `current`, and only then deletes old versions.
 */
object OfficeEngineStore {

    private const val READY = ".ready"
    private const val CURRENT = "current"

    fun root(context: Context): File = File(context.applicationContext.noBackupFilesDir, "office-engine")

    fun stagingDir(context: Context): File = File(root(context), "staging")

    fun downloadDir(context: Context): File = File(root(context), "download")

    /** The active, complete engine directory, or null when no engine is installed. */
    fun installedDir(context: Context): File? {
        val root = root(context)
        val name = runCatching { File(root, CURRENT).readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() && !it.contains('/') } ?: return null
        val dir = File(root, name)
        val ok = File(dir, READY).isFile &&
            File(dir, "lib/liblo-native-code.so").isFile &&
            File(dir, "lib/libc++_shared.so").isFile &&
            File(dir, "program/fundamentalrc").isFile
        return if (ok) dir else null
    }

    fun isInstalled(context: Context): Boolean = installedDir(context) != null

    /** Engine version of the installed engine (see [OfficeEngineManifest.VERSION]). */
    fun installedVersion(context: Context): String? =
        installedDir(context)?.let { runCatching { File(it, READY).readText().trim() }.getOrNull() }

    fun sizeOnDisk(context: Context): Long = root(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Fresh, empty staging directory. */
    fun resetStaging(context: Context): File {
        val staging = stagingDir(context)
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create $staging")
        return staging
    }

    /**
     * Finalises a fully extracted staging directory as the active engine: relocates hard-coded
     * paths, validates, marks ready, swaps it in atomically and removes every older version.
     */
    @Synchronized
    fun commit(context: Context, staging: File, abi: String) {
        val lib = File(staging, "lib")
        for (required in listOf("liblo-native-code.so", "libc++_shared.so")) {
            if (!File(lib, required).isFile) throw IOException("Engine bundle is missing $required")
        }
        if (!File(staging, "program/fundamentalrc").isFile) throw IOException("Engine bundle is missing its runtime")
        OfficeEngineRelocation.patchLibraries(lib, OfficeEngineManifest.RELOCATE_FROM)
        File(staging, READY).writeText(OfficeEngineManifest.VERSION)

        val root = root(context)
        val name = "${OfficeEngineManifest.VERSION}-$abi"
        val target = File(root, name)
        if (target.exists()) {
            // Reinstalling the same version: move the old copy aside first so `current` never
            // points at a half-deleted directory.
            val trash = File(root, "trash-${System.nanoTime()}")
            if (!target.renameTo(trash)) throw IOException("Cannot replace $target")
        }
        if (!staging.renameTo(target)) throw IOException("Cannot activate $target")
        val tmp = File(root, "$CURRENT.tmp")
        tmp.writeText(name)
        if (!tmp.renameTo(File(root, CURRENT))) throw IOException("Cannot switch engine version")

        root.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != name && f.name != "download") f.deleteRecursively()
        }
    }

    /** Removes every trace of the engine, including partial downloads. */
    @Synchronized
    fun delete(context: Context) {
        root(context).deleteRecursively()
    }
}

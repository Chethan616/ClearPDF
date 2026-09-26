package com.chethan616.clearpdf.office

import android.content.Context
import android.net.Uri
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File

/**
 * LibreOfficeKit inside the `:office` process. Never touch this from the main process: a native
 * crash or runaway allocation here must only take down `:office` (see [OfficeEngineService]).
 *
 * LibreOfficeKit is a process-wide singleton that cannot be re-initialised, so this object is
 * too; all calls are made from the service's single worker thread.
 */
internal object OfficeEngineRuntime {

    private var office: Office? = null

    class ConversionException(message: String, val needsPassword: Boolean = false) : Exception(message)

    private fun ensureStarted(context: Context): Office {
        office?.let { return it }
        val app = context.applicationContext
        val engineDir = OfficeEngineStore.installedDir(app)
            ?: throw ConversionException("Office engine is not installed")
        val lib = File(engineDir, "lib")

        OfficeEngineRelocation.pinAlias(engineDir)
        // Order matters: the STL first, then the engine that links against it.
        System.load(File(lib, "libc++_shared.so").absolutePath)
        System.load(File(lib, "liblo-native-code.so").absolutePath)

        val tmp = File(app.cacheDir, "office-tmp").apply { mkdirs() }
        configureFonts(app, engineDir)
        LibreOfficeKit.putenv("SAL_LOG=-WARN-INFO")
        LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts")
        LibreOfficeKit.putenv("TMPDIR=${tmp.absolutePath}")
        LibreOfficeKit.redirectStdio(true)
        val ok = LibreOfficeKit.initializeNative(
            engineDir.absolutePath,
            tmp.absolutePath,
            app.packageResourcePath,
            app.assets
        )
        if (!ok) throw ConversionException("Office engine failed to initialise")
        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            ?: throw ConversionException("Office engine failed to initialise")
        return Office(handle).also { office = it }
    }

    /**
     * The bundle ships its fontconfig file as `etc/fonts/fonts.conf.txt` (the name the upstream
     * Android bootstrap renames on unpack), so fontconfig found no config at all, had no writable
     * cache, and never saw the bundle's metric-compatible Office fonts — every document fell back
     * to arbitrary system faces and looked no better than the built-in renderer. Write a real
     * config that lists the system fonts *and* the engine's own fonts, maps the Microsoft core
     * families to their metric twins, and gives fontconfig a cache dir; then point the engine at it.
     */
    private fun configureFonts(app: Context, engineDir: File) {
        val fontsDir = File(engineDir, "etc/fonts").apply { mkdirs() }
        val cache = File(app.cacheDir, "fontconfig").apply { mkdirs() }
        val home = File(app.filesDir, "office-home").apply { mkdirs() }
        val dirs = listOf("/system/fonts", "/product/fonts", "/system/product/fonts") +
            listOf("user/fonts", "share/fonts", "share/fonts/truetype").map { File(engineDir, it) }
                .filter { it.isDirectory }.map { it.absolutePath }
        fun alias(from: String, to: String) =
            "  <alias binding=\"same\"><family>$from</family><prefer><family>$to</family></prefer></alias>\n"
        val conf = buildString {
            append("<?xml version=\"1.0\"?>\n<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">\n<fontconfig>\n")
            dirs.forEach { append("  <dir>").append(it).append("</dir>\n") }
            append("  <cachedir>").append(cache.absolutePath).append("</cachedir>\n")
            append(alias("Calibri", "Carlito"))
            append(alias("Cambria", "Caladea"))
            append(alias("Arial", "Liberation Sans"))
            append(alias("Helvetica", "Liberation Sans"))
            append(alias("Arial Narrow", "Liberation Sans Narrow"))
            append(alias("Times New Roman", "Liberation Serif"))
            append(alias("Times", "Liberation Serif"))
            append(alias("Courier New", "Liberation Mono"))
            append(alias("Courier", "Liberation Mono"))
            append(alias("sans-serif", "Roboto"))
            append(alias("serif", "Noto Serif"))
            append(alias("monospace", "Droid Sans Mono"))
            append("</fontconfig>\n")
        }
        val confFile = File(fontsDir, "fonts.conf")
        if (!confFile.isFile || confFile.readText() != conf) confFile.writeText(conf)
        val env = listOf(
            "FONTCONFIG_FILE" to confFile.absolutePath,
            "FONTCONFIG_PATH" to fontsDir.absolutePath,
            "XDG_CACHE_HOME" to app.cacheDir.absolutePath,
            "HOME" to home.absolutePath
        )
        for ((k, v) in env) {
            runCatching { android.system.Os.setenv(k, v, true) }
            LibreOfficeKit.putenv("$k=$v")
        }
    }

    /** Converts [input] to PDF at [output]. The input's extension drives format detection. */
    fun convertToPdf(context: Context, input: File, output: File, password: String?) {
        val office = ensureStarted(context)
        val inputUrl = Uri.fromFile(input).toString()
        var passwordRequests = 0
        office.callback = { type, _ ->
            if (type == Office.LOK_CALLBACK_DOCUMENT_PASSWORD) {
                passwordRequests++
                // Offer the password once; a second request means it was wrong, so abort.
                office.setDocumentPassword(inputUrl, if (passwordRequests == 1) password else null)
            }
        }
        // Always ask for password callbacks: answering null aborts the load cleanly and tells us
        // the file is protected (instead of LibreOffice failing with a generic error).
        office.setOptionalFeatures(Office.FEATURE_DOCUMENT_PASSWORD)
        try {
            val document = office.documentLoad(inputUrl)
                ?: throw ConversionException(
                    office.getError()?.takeIf { it.isNotBlank() } ?: "The Office engine could not open this file",
                    needsPassword = passwordRequests > 0
                )
            try {
                output.delete()
                document.saveAs(Uri.fromFile(output).toString(), "pdf", null)
            } finally {
                document.destroy()
            }
        } finally {
            office.callback = null
        }
        if (!output.isFile || output.length() == 0L) throw ConversionException("The Office engine produced no PDF")
    }
}

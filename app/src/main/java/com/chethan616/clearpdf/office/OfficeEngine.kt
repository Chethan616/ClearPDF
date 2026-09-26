package com.chethan616.clearpdf.office

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Entry point for the optional Office engine (powered by LibreOffice): preferences, the
 * per-flavor installer, and the conversion hook used by UniversalDocumentConverter.
 */
object OfficeEngine {
    /**
     * One-shot request from the viewer's "Get it" hint: Settings consumes it on entry and scrolls
     * its Office engine section into view, so the user watches the install they just started.
     */
    val focusSettingsSection = androidx.compose.runtime.mutableStateOf(false)


    private const val PREFS = "office_engine"
    private const val KEY_USE_FOR_OFFICE = "use_for_office_files"
    private const val KEY_HINT_DISMISSED = "hint_dismissed"

    /** Formats LibreOffice converts better than the built-in renderers. */
    private val OFFICE_EXTENSIONS = setOf(
        "doc", "docx", "docm", "dot", "dotx", "odt", "ott", "rtf",
        "xls", "xlsx", "xlsm", "ods", "ots",
        "ppt", "pptx", "pptm", "pps", "ppsx", "odp", "otp", "odg"
    )

    @Volatile
    private var installerInstance: OfficeEngineInstaller? = null

    fun installer(context: Context): OfficeEngineInstaller =
        installerInstance ?: synchronized(this) {
            installerInstance ?: OfficeEngineFlavor.createInstaller(context.applicationContext).also { installerInstance = it }
        }

    fun isSupported(): Boolean = OfficeEngineManifest.bundleForDevice() != null

    fun isOfficeFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in OFFICE_EXTENSIONS

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun useForOfficeFiles(context: Context): Boolean = prefs(context).getBoolean(KEY_USE_FOR_OFFICE, true)

    fun setUseForOfficeFiles(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_FOR_OFFICE, enabled).apply()
    }

    fun isActive(context: Context): Boolean = useForOfficeFiles(context) && OfficeEngineStore.isInstalled(context)

    /**
     * Converts through the engine when it is installed and enabled; null means "use the built-in
     * renderers" (engine absent, disabled, or it failed — failures are silent by design).
     */
    fun tryConvert(context: Context, source: Uri, fileName: String): File? {
        if (!isOfficeFile(fileName) || !isActive(context)) return null
        return OfficeEngineClient.convertBlocking(context, source, fileName).pdf
    }

    /** Whether to show the one-time "Improve fidelity with the Office engine" hint. */
    fun shouldOfferHint(context: Context, fileName: String): Boolean =
        isOfficeFile(fileName) && isSupported() && !OfficeEngineStore.isInstalled(context) &&
            !prefs(context).getBoolean(KEY_HINT_DISMISSED, false)

    fun dismissHint(context: Context) {
        prefs(context).edit().putBoolean(KEY_HINT_DISMISSED, true).apply()
    }

    /** Uninstall helper shared by both installers: stop the engine process, then drop files. */
    internal fun removeInstalledEngine(context: Context) {
        OfficeEngineClient.stopEngineProcess(context)
        OfficeEngineStore.delete(context)
        OfficeEngineClient.clearCache(context)
    }

    internal fun installedState(context: Context): OfficeEngineState {
        if (!isSupported()) return OfficeEngineState.Unsupported
        OfficeEngineStore.installedDir(context) ?: return OfficeEngineState.NotInstalled
        val installed = OfficeEngineStore.installedVersion(context)
        val current = installed == OfficeEngineManifest.VERSION
        return OfficeEngineState.Installed(
            version = if (current) OfficeEngineManifest.DISPLAY_VERSION else installed.orEmpty().substringAfterLast('-'),
            sizeBytes = OfficeEngineStore.sizeOnDisk(context),
            updateAvailable = !current
        )
    }
}

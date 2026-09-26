package com.chethan616.clearpdf.office

import android.app.Activity
import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * play flavor: the engine is the on-demand dynamic feature `:office_engine`, delivered by Google
 * Play (no INTERNET permission in this app). Once Play has installed the split, its libraries
 * and runtime tree are copied into [OfficeEngineStore] — the engine must sit next to its runtime
 * files and have a few paths relocated — and the split is then deferred-uninstalled so the
 * ~190 MB library is not kept twice.
 */
internal object OfficeEngineFlavor {
    fun createInstaller(context: Context): OfficeEngineInstaller = PlayOfficeEngineInstaller(context)
}

private class PlayOfficeEngineInstaller(private val context: Context) : OfficeEngineInstaller {

    companion object {
        const val MODULE = "office_engine"
        private const val CONFIRM_REQUEST_CODE = 0x0FF1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager: SplitInstallManager = SplitInstallManagerFactory.create(context)
    private val _state = MutableStateFlow(OfficeEngine.installedState(context))
    override val state: StateFlow<OfficeEngineState> = _state.asStateFlow()
    override val usesNotification: Boolean = false

    @Volatile private var sessionId = 0
    @Volatile private var pendingConfirmation: SplitInstallSessionState? = null
    @Volatile private var extractJob: Job? = null

    private val listener = SplitInstallStateUpdatedListener { s ->
        if (s.moduleNames().contains(MODULE).not()) return@SplitInstallStateUpdatedListener
        sessionId = s.sessionId()
        when (s.status()) {
            SplitInstallSessionStatus.PENDING, SplitInstallSessionStatus.DOWNLOADING ->
                _state.value = OfficeEngineState.Downloading(s.bytesDownloaded(), s.totalBytesToDownload())
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                pendingConfirmation = s
                _state.value = OfficeEngineState.NeedsConfirmation
            }
            SplitInstallSessionStatus.DOWNLOADED, SplitInstallSessionStatus.INSTALLING ->
                _state.value = OfficeEngineState.Installing
            SplitInstallSessionStatus.INSTALLED -> extractFromSplit()
            SplitInstallSessionStatus.FAILED ->
                _state.value = OfficeEngineState.Failed("Google Play could not install the engine (error ${s.errorCode()})")
            SplitInstallSessionStatus.CANCELED -> _state.value = OfficeEngine.installedState(context)
            else -> Unit
        }
    }

    init {
        manager.registerListener(listener)
    }

    override fun install() {
        if (!OfficeEngine.isSupported()) return
        if (MODULE in manager.installedModules) {
            extractFromSplit()
            return
        }
        _state.value = OfficeEngineState.Downloading(0, 0)
        val request = SplitInstallRequest.newBuilder().addModule(MODULE).build()
        manager.startInstall(request)
            .addOnSuccessListener { id -> sessionId = id }
            .addOnFailureListener { e ->
                _state.value = OfficeEngineState.Failed(e.message ?: "Google Play could not install the engine")
            }
    }

    override fun confirm(activity: Activity) {
        val pending = pendingConfirmation ?: return
        pendingConfirmation = null
        @Suppress("DEPRECATION")
        manager.startConfirmationDialogForResult(pending, activity, CONFIRM_REQUEST_CODE)
    }

    override fun cancel() {
        extractJob?.cancel()
        if (sessionId != 0) manager.cancelInstall(sessionId)
        _state.value = OfficeEngine.installedState(context)
    }

    override fun uninstall() {
        extractJob?.cancel()
        scope.launch {
            OfficeEngine.removeInstalledEngine(context)
            runCatching { manager.deferredUninstall(listOf(MODULE)) }
            _state.value = OfficeEngine.installedState(context)
        }
    }

    override fun refresh() {
        scope.launch {
            if (extractJob?.isActive != true && sessionId == 0) _state.value = OfficeEngine.installedState(context)
        }
    }

    private fun extractFromSplit() {
        if (extractJob?.isActive == true) return
        _state.value = OfficeEngineState.Installing
        extractJob = scope.launch {
            try {
                val abi = OfficeEngineManifest.bundleForDevice()?.abi ?: throw IOException("Unsupported device")
                val staging = OfficeEngineStore.resetStaging(context)
                val splits = splitApks()
                var files = 0
                for (apk in splits) {
                    ZipFile(apk).use { zip ->
                        for (entry in zip.entries()) {
                            if (!isActive()) throw kotlinx.coroutines.CancellationException()
                            if (entry.isDirectory) continue
                            val name = when {
                                entry.name.startsWith("lib/$abi/") -> abi + "/" + entry.name.removePrefix("lib/$abi/")
                                entry.name.startsWith("assets/lo/") -> entry.name.removePrefix("assets/lo/")
                                else -> continue
                            }
                            val target = EngineArchiveLayout.destinationFor(staging, abi, name) ?: continue
                            target.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it, 1 shl 16) } }
                            files++
                        }
                    }
                }
                if (files == 0) throw IOException("The engine module is empty in this build")
                OfficeEngineClient.stopEngineProcess(context)
                OfficeEngineStore.commit(context, staging, abi)
                OfficeEngineClient.clearCache(context)
                // Everything needed now lives in app storage; let Play reclaim the split.
                runCatching { manager.deferredUninstall(listOf(MODULE)) }
                sessionId = 0
                _state.value = OfficeEngine.installedState(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                OfficeEngineStore.stagingDir(context).deleteRecursively()
                throw e
            } catch (e: Exception) {
                OfficeEngineStore.stagingDir(context).deleteRecursively()
                sessionId = 0
                _state.value = OfficeEngineState.Failed(e.message ?: "Could not install the engine")
            }
        }
    }

    private fun isActive(): Boolean = extractJob?.isActive != false

    /** Installed split APKs (fresh from PackageManager) plus SplitCompat-emulated ones (local testing). */
    private fun splitApks(): List<File> {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        val installed = info.splitSourceDirs.orEmpty().map(::File)
        val emulated = File(context.filesDir, "splitcompat").walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        return (installed + emulated).filter { it.name.contains(MODULE) || it.name.contains("office") }
            .ifEmpty { installed + emulated }
    }
}

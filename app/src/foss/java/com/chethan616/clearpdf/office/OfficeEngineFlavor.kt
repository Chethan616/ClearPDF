package com.chethan616.clearpdf.office

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** foss flavor: the engine is downloaded from the pinned release by [OfficeEngineDownloadWorker]. */
internal object OfficeEngineFlavor {
    fun createInstaller(context: Context): OfficeEngineInstaller = DownloadOfficeEngineInstaller(context)
}

private class DownloadOfficeEngineInstaller(private val context: Context) : OfficeEngineInstaller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)
    private val _state = MutableStateFlow(OfficeEngine.installedState(context))
    override val state: StateFlow<OfficeEngineState> = _state.asStateFlow()
    override val usesNotification: Boolean = true

    /** Only surface a FAILED work result for installs started in this process's lifetime. */
    @Volatile private var startedHere = false

    init {
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(OfficeEngineDownloadWorker.UNIQUE_NAME).collect { infos ->
                _state.value = map(infos.firstOrNull())
            }
        }
    }

    private fun map(info: WorkInfo?): OfficeEngineState {
        if (!OfficeEngine.isSupported()) return OfficeEngineState.Unsupported
        return when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                OfficeEngineState.Downloading(0, OfficeEngineManifest.APPROX_DOWNLOAD_BYTES, waitingForNetwork = true)
            WorkInfo.State.RUNNING -> {
                val p = info.progress
                if (p.getString(OfficeEngineDownloadWorker.KEY_PHASE) == OfficeEngineDownloadWorker.PHASE_INSTALL) {
                    OfficeEngineState.Installing
                } else {
                    OfficeEngineState.Downloading(
                        p.getLong(OfficeEngineDownloadWorker.KEY_DONE, 0),
                        p.getLong(OfficeEngineDownloadWorker.KEY_TOTAL, OfficeEngineManifest.APPROX_DOWNLOAD_BYTES)
                    )
                }
            }
            WorkInfo.State.FAILED -> if (startedHere) {
                OfficeEngineState.Failed(
                    info.outputData.getString(OfficeEngineDownloadWorker.KEY_ERROR) ?: "Download failed"
                )
            } else {
                OfficeEngine.installedState(context)
            }
            else -> OfficeEngine.installedState(context)
        }
    }

    override fun install() {
        if (!OfficeEngine.isSupported()) return
        startedHere = true
        val request = OneTimeWorkRequestBuilder<OfficeEngineDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME)
    }

    override fun uninstall() {
        workManager.cancelUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME)
        scope.launch {
            OfficeEngine.removeInstalledEngine(context)
            workManager.pruneWork()
            _state.value = OfficeEngine.installedState(context)
        }
    }

    override fun refresh() {
        scope.launch {
            val info = workManager.getWorkInfosForUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME).get().firstOrNull()
            _state.value = map(info)
        }
    }
}

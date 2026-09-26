package com.chethan616.clearpdf.office

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** What the Settings "Office engine" section shows. */
sealed interface OfficeEngineState {
    /** No bundle for this device's ABI (e.g. x86) — the section is hidden. */
    data object Unsupported : OfficeEngineState

    data object NotInstalled : OfficeEngineState

    /** [total] <= 0 means the size is not known yet (indeterminate progress). */
    data class Downloading(val downloaded: Long, val total: Long, val waitingForNetwork: Boolean = false) : OfficeEngineState

    /** Verifying and unpacking. */
    data object Installing : OfficeEngineState

    /** Play asks the user to approve a large download ([OfficeEngineInstaller.confirm]). */
    data object NeedsConfirmation : OfficeEngineState

    data class Installed(val version: String, val sizeBytes: Long, val updateAvailable: Boolean) : OfficeEngineState

    data class Failed(val message: String) : OfficeEngineState
}

/**
 * Gets the engine onto the device. One implementation per distribution flavor, created by
 * `OfficeEngineFlavor.createInstaller` (src/play, src/foss); both finish through
 * [OfficeEngineStore.commit] so the runtime side is identical.
 */
interface OfficeEngineInstaller {
    val state: StateFlow<OfficeEngineState>

    /** Whether starting an install should first ask for POST_NOTIFICATIONS (progress notification). */
    val usesNotification: Boolean

    /** Starts (or resumes) installing or updating. */
    fun install()

    fun cancel()

    /** Removes the engine and frees its storage. */
    fun uninstall()

    /** Re-reads on-disk state (e.g. when Settings opens). */
    fun refresh()

    /** Answers [OfficeEngineState.NeedsConfirmation]; no-op where not applicable. */
    fun confirm(activity: Activity) {}
}

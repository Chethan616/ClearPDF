package com.chethan616.clearpdf.office

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-Droid build: the optional Office engine is a downloaded native binary, which F-Droid's
 * inclusion policy doesn't allow, so it is never offered here and the built-in Office renderers
 * are used instead.
 */
internal object OfficeEngineFlavor {
    const val available: Boolean = false
    fun createInstaller(context: Context): OfficeEngineInstaller = UnavailableOfficeEngineInstaller
}

private object UnavailableOfficeEngineInstaller : OfficeEngineInstaller {
    private val _state = MutableStateFlow<OfficeEngineState>(OfficeEngineState.Unsupported)
    override val state: StateFlow<OfficeEngineState> = _state.asStateFlow()
    override val usesNotification: Boolean = false
    override fun install() = Unit
    override fun cancel() = Unit
    override fun uninstall() = Unit
    override fun refresh() = Unit
}

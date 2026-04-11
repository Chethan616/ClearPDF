package com.chethan616.clearpdf.ui.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object StarPromptEventBus {
    private val _promptRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val promptRequests = _promptRequests.asSharedFlow()

    fun requestPrompt() {
        _promptRequests.tryEmit(Unit)
    }
}

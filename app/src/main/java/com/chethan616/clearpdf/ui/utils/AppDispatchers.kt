package com.chethan616.clearpdf.ui.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Dedicated thread-pool dispatcher for heavy PDF I/O and rendering tasks.
 *
 * Using a separate executor pool instead of the shared [Dispatchers.IO] ensures that
 * PDF rendering / compression / thumbnail generation never blocks coroutines that other
 * parts of the app (e.g. ViewModels) are waiting on — preventing UI-thread starvation.
 *
 * Pool size = min(4, available CPU cores).  PDF tasks are mostly I/O-bound so we cap
 * the pool to avoid excessive context-switching on mid-range devices.
 */
object AppDispatchers {
    /** Use for PDF rendering, compression, split, merge, and thumbnail tasks. */
    val pdf: CoroutineDispatcher = Executors.newFixedThreadPool(
        minOf(4, Runtime.getRuntime().availableProcessors())
    ) { runnable ->
        Thread(runnable, "clearpdf-io").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    /** Standard IO — used for lightweight file-system queries (file names, sizes, permissions). */
    val io: CoroutineDispatcher get() = Dispatchers.IO
}

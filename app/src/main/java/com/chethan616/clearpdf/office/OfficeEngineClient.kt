package com.chethan616.clearpdf.office

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Main-process side of the Office engine: converts a document to PDF through
 * [OfficeEngineService] in `:office`, with a timeout, cancellation and a result cache.
 *
 * Every failure (engine missing, timeout, native crash, unreadable file, wrong password) returns a
 * [Result] without a file — callers fall back to the built-in renderers. Conversions are
 * serialised: LibreOfficeKit handles one document at a time.
 */
object OfficeEngineClient {

    private const val TAG = "OfficeEngineClient"
    const val DEFAULT_TIMEOUT_MS = 90_000L
    private const val BIND_TIMEOUT_MS = 10_000L
    private const val CACHE_LIMIT_BYTES = 150L * 1024 * 1024

    data class Result(val pdf: File?, val error: String? = null, val needsPassword: Boolean = false)

    private val lock = Any()

    /** Coroutine-friendly: cancelling the caller interrupts the wait and kills `:office`. */
    suspend fun convert(
        context: Context,
        source: Uri,
        fileName: String,
        password: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result = runInterruptible(Dispatchers.IO) { convertBlocking(context, source, fileName, password, timeoutMs) }

    /**
     * Blocking conversion; call from a background thread. [fileName] supplies the extension that
     * LibreOffice uses for format detection. Thread interruption cancels the conversion.
     */
    fun convertBlocking(
        context: Context,
        source: Uri,
        fileName: String,
        password: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result {
        if (Looper.myLooper() == Looper.getMainLooper()) return Result(null, "Called on the main thread")
        val app = context.applicationContext
        if (!OfficeEngineStore.isInstalled(app)) return Result(null, "Office engine is not installed")
        val ext = fileName.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        if (ext.isEmpty()) return Result(null, "Unknown file type")

        val inDir = File(app.cacheDir, "office-in").apply { mkdirs() }
        val cacheDir = File(app.cacheDir, "office-pdf").apply { mkdirs() }
        val input = File(inDir, "in-${System.nanoTime()}.$ext")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            app.contentResolver.openInputStream(source)?.use { stream ->
                input.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            } ?: return Result(null, "Cannot open file")
            digest.update("$ext|${OfficeEngineStore.installedVersion(app)}".toByteArray())
            val key = digest.digest().joinToString("") { "%02x".format(it) }.take(40)
            // Password-protected output is never cached: the PDF is unencrypted.
            val cached = File(cacheDir, "$key.pdf")
            if (password == null && cached.isFile && cached.length() > 0) {
                cached.setLastModified(System.currentTimeMillis())
                return Result(cached)
            }
            val output = if (password == null) cached else File(cacheDir, "pw-${System.nanoTime()}.pdf")
            val partial = File(cacheDir, "${output.name}.part")
            val result = synchronized(lock) { runInService(app, input, partial, password, timeoutMs) }
            if (result.pdf == null) {
                partial.delete()
                return result
            }
            if (!partial.renameTo(output)) return Result(null, "Cannot store converted PDF")
            trimCache(cacheDir)
            return Result(output)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Result(null, "Cancelled")
        } catch (t: Throwable) {
            Log.w(TAG, "Office engine conversion failed", t)
            return Result(null, t.message)
        } finally {
            input.delete()
        }
    }

    private fun runInService(app: Context, input: File, output: File, password: String?, timeoutMs: Long): Result {
        val connected = CountDownLatch(1)
        val done = CountDownLatch(1)
        val service = AtomicReference<Messenger?>()
        val reply = AtomicReference<Bundle?>()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service.set(Messenger(binder))
                connected.countDown()
            }

            // `:office` died (native crash, OOM, idle kill mid-job): fail fast.
            override fun onServiceDisconnected(name: ComponentName) {
                connected.countDown()
                done.countDown()
            }

            override fun onBindingDied(name: ComponentName) {
                connected.countDown()
                done.countDown()
            }
        }
        val replyTo = Messenger(Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == OfficeEngineService.MSG_RESULT) {
                reply.set(msg.data)
                done.countDown()
            }
            true
        })

        val intent = Intent(app, OfficeEngineService::class.java)
        if (!app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            return Result(null, "Office engine service unavailable")
        }
        var killProcess = false
        try {
            if (!connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                killProcess = true
                return Result(null, "Office engine did not start")
            }
            val messenger = service.get() ?: return Result(null, "Office engine stopped")
            messenger.send(Message.obtain(null, OfficeEngineService.MSG_CONVERT).apply {
                data = Bundle().apply {
                    putString(OfficeEngineService.KEY_INPUT, input.absolutePath)
                    putString(OfficeEngineService.KEY_OUTPUT, output.absolutePath)
                    password?.let { putString(OfficeEngineService.KEY_PASSWORD, it) }
                }
                this.replyTo = replyTo
            })
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                killProcess = true
                return Result(null, "Office engine timed out")
            }
            val data = reply.get() ?: return Result(null, "Office engine stopped unexpectedly")
            return if (data.getBoolean(OfficeEngineService.KEY_OK)) {
                Result(output)
            } else {
                Result(
                    null,
                    data.getString(OfficeEngineService.KEY_ERROR),
                    data.getBoolean(OfficeEngineService.KEY_NEEDS_PASSWORD)
                )
            }
        } catch (e: InterruptedException) {
            killProcess = true
            throw e
        } finally {
            runCatching { app.unbindService(connection) }
            if (killProcess) stopEngineProcess(app)
        }
    }

    /** Kills `:office` (cancel/timeout/uninstall). Safe to call when it is not running. */
    fun stopEngineProcess(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val name = "${app.packageName}:office"
        am.runningAppProcesses?.filter { it.processName == name }?.forEach { Process.killProcess(it.pid) }
    }

    fun clearCache(context: Context) {
        File(context.applicationContext.cacheDir, "office-pdf").deleteRecursively()
    }

    private fun trimCache(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEachIndexed { index, file ->
            total += file.length()
            // Always keep the newest file: it is the one being opened right now.
            if (index > 0 && total > CACHE_LIMIT_BYTES) file.delete()
        }
    }
}

package com.chethan616.clearpdf.office

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import java.io.File

/**
 * Hosts LibreOfficeKit in the isolated `:office` process (android:process=":office").
 *
 * Why a separate process: the engine is ~190 MB of native code that can crash, leak or spin on a
 * hostile document. Here it can only take down `:office`; the client sees the binder die and
 * falls back to the built-in renderers. Memory is reclaimed by killing the whole process once it
 * has been idle for [IDLE_KILL_MS] — LibreOfficeKit cannot be torn down and re-initialised.
 *
 * Protocol (Messenger): [MSG_CONVERT] with [KEY_INPUT], [KEY_OUTPUT], optional [KEY_PASSWORD];
 * reply [MSG_RESULT] with [KEY_OK], [KEY_ERROR], [KEY_NEEDS_PASSWORD]. Paths live in the app's
 * own cache directory, shared by both processes of this uid.
 */
class OfficeEngineService : Service() {

    companion object {
        const val MSG_CONVERT = 1
        const val MSG_RESULT = 2
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
        const val KEY_PASSWORD = "password"
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val KEY_NEEDS_PASSWORD = "needsPassword"
        private const val IDLE_KILL_MS = 60_000L

        private val mainHandler = Handler(Looper.getMainLooper())
        private val idleKill = Runnable { Process.killProcess(Process.myPid()) }
    }

    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        mainHandler.removeCallbacks(idleKill)
        worker = HandlerThread("office-engine").apply { start() }
        messenger = Messenger(Handler(worker.looper) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(msg: Message) {
        if (msg.what != MSG_CONVERT) return
        mainHandler.removeCallbacks(idleKill)
        val data = msg.data
        val reply = Bundle()
        try {
            val input = File(requireNotNull(data.getString(KEY_INPUT)))
            val output = File(requireNotNull(data.getString(KEY_OUTPUT)))
            OfficeEngineRuntime.convertToPdf(this, input, output, data.getString(KEY_PASSWORD))
            reply.putBoolean(KEY_OK, true)
        } catch (e: OfficeEngineRuntime.ConversionException) {
            reply.putString(KEY_ERROR, e.message)
            reply.putBoolean(KEY_NEEDS_PASSWORD, e.needsPassword)
        } catch (t: Throwable) {
            reply.putString(KEY_ERROR, t.message ?: t.javaClass.simpleName)
        }
        try {
            msg.replyTo?.send(Message.obtain(null, MSG_RESULT).apply { this.data = reply })
        } catch (_: RemoteException) {
            // Client gave up (timeout/cancel); nothing to do.
        }
        mainHandler.postDelayed(idleKill, IDLE_KILL_MS)
    }

    override fun onDestroy() {
        // Unbound: keep the warm engine briefly for the next document, then free its memory.
        mainHandler.removeCallbacks(idleKill)
        mainHandler.postDelayed(idleKill, IDLE_KILL_MS)
        worker.quitSafely()
        super.onDestroy()
    }
}

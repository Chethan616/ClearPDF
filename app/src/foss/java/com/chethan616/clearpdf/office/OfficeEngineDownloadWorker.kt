package com.chethan616.clearpdf.office

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chethan616.clearpdf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * foss flavor: downloads, verifies and installs the Office engine.
 *
 *  1. Resumable download (HTTP Range) into office-engine/download/<sha256>.part. The bytes already
 *     on disk are re-hashed first so the streaming SHA-256 covers the whole file.
 *  2. The download is accepted only if its size and SHA-256 match [OfficeEngineManifest].
 *  3. tar.xz is unpacked into office-engine/staging (zip-slip checked), then
 *     [OfficeEngineStore.commit] swaps it in atomically. The previous engine is removed only
 *     after that succeeds.
 *
 * Runs as a foreground dataSync worker with a Cancel action. The partial download survives
 * cancellation and failure, so retrying resumes where it stopped.
 */
class OfficeEngineDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "office-engine-install"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_INSTALL = "install"

        private const val CHANNEL_ID = "office_engine"
        private const val NOTIFICATION_ID = 0x0FF1CE
        private const val BUFFER = 1 shl 16

        /** xz dictionary of these bundles is 256 MiB; decoding needs about that much heap. */
        private const val XZ_MEMORY_LIMIT_KIB = 300 * 1024
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bundle = OfficeEngineManifest.bundleForDevice()
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "This device is not supported"))
        try {
            setForeground(foregroundInfo(0, bundle.sizeBytes, indeterminate = true, installing = false))
            val archive = download(bundle)
            setProgress(workDataOf(KEY_PHASE to PHASE_INSTALL))
            setForeground(foregroundInfo(0, 0, indeterminate = true, installing = true))
            install(archive, bundle.abi)
            archive.delete()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            OfficeEngineStore.stagingDir(applicationContext).deleteRecursively()
            Result.failure(workDataOf(KEY_ERROR to "Not enough memory to unpack the engine on this device"))
        } catch (e: Exception) {
            OfficeEngineStore.stagingDir(applicationContext).deleteRecursively()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private suspend fun download(bundle: OfficeEngineManifest.Bundle): File {
        val dir = OfficeEngineStore.downloadDir(applicationContext).apply { mkdirs() }
        // Drop parts of other versions/ABIs.
        dir.listFiles()?.filter { !it.name.startsWith(bundle.sha256) }?.forEach { it.delete() }
        val part = File(dir, "${bundle.sha256}.part")
        val digest = MessageDigest.getInstance("SHA-256")

        if (part.length() > bundle.sizeBytes) part.delete()
        var have = part.length()
        if (have > 0) part.inputStream().use { input -> hashInto(digest, input) }

        if (have < bundle.sizeBytes) {
            val conn = URL(bundle.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            try {
                val code = conn.responseCode
                val append = when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false
                    else -> throw IOException("Download failed (HTTP $code)")
                }
                if (!append) {
                    // Server ignored the range: start over.
                    have = 0
                    digest.reset()
                }
                BufferedInputStream(conn.inputStream, BUFFER).use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(BUFFER)
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            have += n
                            if (have > bundle.sizeBytes) throw IOException("Download is larger than expected")
                            if (have - lastReport >= 512 * 1024 || have == bundle.sizeBytes) {
                                lastReport = have
                                setProgress(workDataOf(KEY_PHASE to PHASE_DOWNLOAD, KEY_DONE to have, KEY_TOTAL to bundle.sizeBytes))
                                notificationManager.notify(
                                    NOTIFICATION_ID,
                                    notification(have, bundle.sizeBytes, indeterminate = false, installing = false)
                                )
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (have != bundle.sizeBytes || sha != bundle.sha256) {
            part.delete()
            throw IOException("Download verification failed (checksum mismatch)")
        }
        return part
    }

    private suspend fun install(archive: File, abi: String) {
        val context = applicationContext
        if (Runtime.getRuntime().maxMemory() < 290L * 1024 * 1024) {
            throw IOException("Not enough memory to unpack the engine on this device")
        }
        val staging = OfficeEngineStore.resetStaging(context)
        val job = coroutineContext
        val files = XZInputStream(BufferedInputStream(archive.inputStream(), BUFFER), XZ_MEMORY_LIMIT_KIB).use { xz ->
            EngineArchiveLayout.extractTar(xz, staging, abi) { !job.isActive }
        }
        if (files == 0) throw IOException("Engine archive is empty")
        OfficeEngineClient.stopEngineProcess(context)
        OfficeEngineStore.commit(context, staging, abi)
        OfficeEngineClient.clearCache(context)
    }

    private fun hashInto(digest: MessageDigest, input: java.io.InputStream) {
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }

    private fun foregroundInfo(done: Long, total: Long, indeterminate: Boolean, installing: Boolean): ForegroundInfo {
        val notification = notification(done, total, indeterminate, installing)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(done: Long, total: Long, indeterminate: Boolean, installing: Boolean): android.app.Notification {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.office_engine_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val max = 1000
        val progress = if (total > 0) ((done * max) / total).toInt() else 0
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.office_engine_title))
            .setContentText(
                context.getString(
                    if (installing) R.string.office_engine_installing else R.string.office_engine_downloading
                )
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(max, progress, indeterminate)
            .addAction(0, context.getString(R.string.office_engine_cancel), cancel)
            .build()
    }
}

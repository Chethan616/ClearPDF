package com.chethan616.clearpdf.preview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.chethan616.clearpdf.MainActivity
import com.chethan616.clearpdf.PdfcraftApplication
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.DocumentInfo
import com.chethan616.clearpdf.core.MobileException
import com.chethan616.clearpdf.engine.DocumentFiles
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.engine.userMessage
import com.chethan616.clearpdf.ui.glass.GlassSettings
import com.chethan616.clearpdf.ui.viewer.PageImages
import com.chethan616.clearpdf.ui.viewer.PageSize
import com.chethan616.clearpdf.ui.viewer.ViewerState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Preview: a document another app hands to Artefact ("Open with", a chat's attachment)
 * opens in a floating card over that app instead of the full viewer.
 *
 * The window is translucent (Theme.Artefact.Preview) and joins the caller's task, so the app that
 * opened the file stays drawn behind it, exactly where it was; Android dims and, where the phone
 * allows cross-window blur (Android 12+), softly blurs it. The card itself is compact: it never
 * covers the whole screen. Expand hands the file to the full viewer; closing just finishes, which
 * reveals the caller again. Nothing here draws over other apps: no overlay permission, no PiP.
 */
class QuickPreviewActivity : ComponentActivity() {
    private val app get() = application as PdfcraftApplication
    private val files by lazy { DocumentFiles(this) }

    /** What the card shows: loading, a password to ask for, the document, or what went wrong. */
    internal var state by mutableStateOf<PreviewState>(PreviewState.Loading)
        private set

    private var uri: Uri? = null
    private var mime: String? = null
    private var engine: Engine? = null
    private var copy: File? = null

    /** Engine work that must outlive the activity (closing the document). */
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        GlassSettings.load(this)
        uri = sourceOf(intent)
        mime = intent?.type ?: uri?.let { contentResolver.getType(it) }
        if (uri == null) {
            finish()
            return
        }
        // the user turned Quick Preview off: straight to the full viewer
        if (!getSharedPreferences("app", MODE_PRIVATE).getBoolean("quickPreview", true)) {
            expand()
            return
        }
        setContent {
            QuickPreviewScreen(
                activity = this,
                state = state,
                onClose = ::closeNow,
                onExpand = ::expand,
                onShare = ::share,
                onOpenWith = ::openWith,
                onSaveCopy = ::saveCopy,
                onPassword = ::unlock,
                setBlur = ::setBlur,
            )
        }
        load(null)
    }

    /** The document's address from a View or a Send intent. */
    private fun sourceOf(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }

    /**
     * Copy the file in (the caller's grant lasts only as long as this visit), then open it. The
     * copy is the full viewer's working copy too, so Expand opens instantly and keeps working even
     * after the caller's grant is gone.
     */
    private fun load(password: String?) {
        val u = uri ?: return
        lifecycleScope.launch {
            state = if (password == null) PreviewState.Loading else state
            val eng = app.engine.await().getOrNull() ?: run {
                state = PreviewState.Failed(getString(R.string.msg_engine_not_started))
                return@launch
            }
            engine = eng
            try {
                val (file, name, size) = withContext(Dispatchers.IO) {
                    val f = copy ?: files.workingCopy(u).also { copy = it }
                    Triple(f, files.displayName(u), files.size(u) ?: f.length())
                }
                val info: DocumentInfo = eng.command { open(file.absolutePath, password) }
                val images = PageImages(eng, info.id, lifecycleScope, 48 * 1024 * 1024)
                val view = ViewerState(info.pages.map { PageSize(it.width, it.height) })
                (state as? PreviewState.Ready)?.close(eng)
                state = PreviewState.Ready(info, images, view, name, size)
            } catch (e: MobileException.NeedsPassword) {
                state = PreviewState.Password(wrong = false)
            } catch (e: MobileException.WrongPassword) {
                state = PreviewState.Password(wrong = true)
            } catch (e: SecurityException) {
                state = PreviewState.Failed(getString(R.string.msg_file_access_lost))
            } catch (e: Exception) {
                state = PreviewState.Failed(e.userMessage)
            }
        }
    }

    private fun unlock(password: String) = load(password)

    /**
     * Hand the document to the full viewer, then step aside. The viewer gets the original address
     * (so it can save back where the caller allows it) and the copy made here: Android may refuse
     * to pass the caller's one-visit grant on, and then the copy is what opens.
     */
    private fun expand() {
        val u = uri ?: return finish()
        val base = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_FROM_PREVIEW, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        copy?.let { base.putExtra(MainActivity.EXTRA_LOCAL_COPY, it.absolutePath) }
        val withSource = Intent(base).setDataAndType(u, mime ?: "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // the card has already grown to fill the screen: the viewer just fades in over it
        val fade = android.app.ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        if (runCatching { startActivity(withSource, fade) }.isFailure) runCatching { startActivity(base, fade) }
        finishQuietly()
    }

    private fun share() {
        val f = copy ?: return
        val shared = runCatching { files.shareable(f, (state as? PreviewState.Ready)?.name ?: f.name) }.getOrNull() ?: return
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, shared).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(Intent.createChooser(send, null))
    }

    private fun openWith() {
        val f = copy ?: return
        val shared = runCatching { files.shareable(f, (state as? PreviewState.Ready)?.name ?: f.name) }.getOrNull() ?: return
        launch(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(shared, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }

    private fun saveCopy(done: (String) -> Unit) {
        val f = copy ?: return
        val name = (state as? PreviewState.Ready)?.name ?: f.name
        lifecycleScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { files.saveToDownloads(f, name) } }.isSuccess
            done(getString(if (ok) R.string.preview_saved_downloads else R.string.files_error_write))
        }
    }

    private fun launch(i: Intent) {
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            // nothing to share with: nothing to do
        }
    }

    /** The card has animated away: finish with no window animation of the system's. */
    private fun closeNow() = finishQuietly()

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    /**
     * Blur what's behind the window (the caller), Android 12 and later where the phone allows it:
     * [radius] in pixels, 0 for none. The card animates it in and out with itself.
     */
    private fun setBlur(radius: Int) {
        if (Build.VERSION.SDK_INT < 31) return
        val wm = getSystemService(WindowManager::class.java) ?: return
        if (!wm.isCrossWindowBlurEnabled) return
        val lp = window.attributes
        if (radius > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        if (lp.blurBehindRadius != radius) {
            lp.blurBehindRadius = radius
            window.attributes = lp
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val eng = engine
        val s = state
        if (isFinishing && eng != null && s is PreviewState.Ready) cleanup.launch { s.close(eng) }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        (state as? PreviewState.Ready)?.images?.trim(emptySet())
    }
}

/** What Quick Preview is showing. */
internal sealed interface PreviewState {
    data object Loading : PreviewState

    class Password(val wrong: Boolean) : PreviewState

    class Failed(val message: String) : PreviewState

    class Ready(val info: DocumentInfo, val images: PageImages, val view: ViewerState, val name: String, val size: Long) : PreviewState {
        suspend fun close(eng: Engine) {
            images.close()
            runCatching { eng.tool("doc_close", mapOf("doc" to info.id, "discard_changes" to true)) }
        }
    }
}

package com.chethan616.clearpdf.preview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
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
import com.chethan616.clearpdf.ArtefactApplication
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.DocumentInfo
import com.chethan616.clearpdf.core.MobileException
import com.chethan616.clearpdf.engine.DocumentFiles
import com.chethan616.clearpdf.engine.DocumentTypes
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
 * covers the whole screen. Open hands the file to the full viewer at once; closing just finishes, which
 * reveals the caller again. Nothing here draws over other apps: no overlay permission, no PiP.
 */
class QuickPreviewActivity : ComponentActivity() {
    private val app get() = application as ArtefactApplication
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

    /** The picture at [u] (copied in as documents are), decoded no larger than the card needs and turned as shot. */
    private fun picture(u: Uri, name: String): PreviewState.Picture {
        val f = copy ?: files.workingCopy(u).also { copy = it }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
        var bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error(getString(R.string.common_file_unreadable))
        val exif = androidx.exifinterface.media.ExifInterface(f.absolutePath)
        val turn = when (exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f
            3 -> 180f
            8 -> 270f
            else -> 0f
        }
        if (turn != 0f) bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(turn) }, true)
        val sideways = turn == 90f || turn == 270f
        return PreviewState.Picture(
            bmp.asImageBitmap(), name, files.size(u) ?: f.length(),
            if (sideways) bounds.outHeight else bounds.outWidth, if (sideways) bounds.outWidth else bounds.outHeight,
        )
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
            // a picture is shown as it is (no engine needed); Edit opens it in the photo editor
            val pictureName = withContext(Dispatchers.IO) { runCatching { files.displayNameRaw(u) }.getOrNull() }
            // a mind map (OPML) is drawn as a map
            if (pictureName != null && (com.chethan616.clearpdf.engine.DocumentTypes.isMindMapName(pictureName) || mime in com.chethan616.clearpdf.engine.DocumentTypes.MIND_MAPS)) {
                state = runCatching {
                    withContext(Dispatchers.IO) {
                        val f = copy ?: files.workingCopy(u).also { copy = it }
                        require(f.length() < 8L shl 20)
                        val doc = com.chethan616.clearpdf.ui.notes.mindFromOpml(f.readText()) ?: error(getString(R.string.msg_not_opml))
                        PreviewState.Map(doc, pictureName, files.size(u) ?: f.length())
                    }
                }.getOrElse { PreviewState.Failed(getString(R.string.msg_not_opml)) }
                return@launch
            }
            if (pictureName != null && (com.chethan616.clearpdf.engine.DocumentTypes.isImageName(pictureName) || mime in com.chethan616.clearpdf.engine.DocumentTypes.IMAGES)) {
                state = runCatching { withContext(Dispatchers.IO) { picture(u, pictureName) } }.getOrElse { PreviewState.Failed(it.userMessage) }
                return@launch
            }
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
                state = PreviewState.Ready(info, images, view, name, size, eng)
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
        // straight into the app, no stop at a full-screen preview: the viewer rises over the card
        // (which holds still under it) on the sheet curve
        val rise = android.app.ActivityOptions.makeCustomAnimation(this, R.anim.preview_open_enter, R.anim.preview_open_exit).toBundle()
        if (runCatching { startActivity(withSource, rise) }.isFailure) runCatching { startActivity(base, rise) }
        // no override here: a (0, 0) override would replace the animation above
        finish()
    }

    private fun share() {
        val f = copy ?: return
        val shared = runCatching { files.shareable(f, (state as? PreviewState.Ready)?.name ?: f.name) }.getOrNull() ?: return
        val send = Intent(Intent.ACTION_SEND).setType(DocumentTypes.mimeOf(f.name)).putExtra(Intent.EXTRA_STREAM, shared).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(Intent.createChooser(send, null))
    }

    private fun openWith() {
        val f = copy ?: return
        val shared = runCatching { files.shareable(f, (state as? PreviewState.Ready)?.name ?: f.name) }.getOrNull() ?: return
        launch(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(shared, DocumentTypes.mimeOf(f.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
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

    /** A mind map (.opml): drawn as a map. */
    class Map(val doc: com.chethan616.clearpdf.ui.notes.MindDoc, val name: String, val size: Long) : PreviewState

    /** A picture (.jpg, .png): shown as it is, with its size in pixels. */
    class Picture(val image: androidx.compose.ui.graphics.ImageBitmap, val name: String, val size: Long, val width: Int, val height: Int) : PreviewState

    class Ready(val info: DocumentInfo, val images: PageImages, val view: ViewerState, val name: String, val size: Long, val engine: Engine) : PreviewState {
        suspend fun close(eng: Engine) {
            images.close()
            runCatching { eng.tool("doc_close", mapOf("doc" to info.id, "discard_changes" to true)) }
        }
    }
}

package com.chethan616.clearpdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.ui.DocsApp

open class MainActivity : ComponentActivity() {

    /** True for [DocumentActivity]: a document opened from another app, in its own window. */
    protected open val isDocumentWindow: Boolean = false

    override fun attachBaseContext(newBase: android.content.Context) {
        val savedLang = com.chethan616.clearpdf.data.repository.OnboardingManager.getSelectedLocale(newBase)
        val context = com.chethan616.clearpdf.ui.utils.LocaleHelper.getLocalizedContext(newBase, savedLang)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedLang = com.chethan616.clearpdf.data.repository.OnboardingManager.getSelectedLocale(this)
        com.chethan616.clearpdf.ui.utils.LocaleHelper.applyLocale(this, savedLang, recreate = false)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A file opened from another app (a file manager, a chat) gets its own window, like a
        // document in any desktop-class app (#29), instead of running inside the caller's task.
        // The launcher entry and shortcuts stay here.
        if (!isDocumentWindow && savedInstanceState == null && isExternalDocument(intent)) {
            runCatching {
                startActivity(
                    Intent(intent)
                        .setClass(this, DocumentActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
                finish()
                @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                return
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Belt-and-suspenders against the translucent scrim Android can draw behind the nav bar
        // (API 29-34; reported as a "grey band" around it on some OEM skins/Android versions even
        // with enableEdgeToEdge()). The app paints its own full-bleed background in every screen,
        // so that scrim is never needed and only ever shows as a seam.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.isNavigationBarContrastEnforced = false
        }

        // A locale change restarts this Activity. DocsApp animates both halves of that restart, so
        // the system's own cross-fade would just stack on top of ours.
        com.chethan616.clearpdf.ui.utils.LocaleHelper.suppressActivityTransition(this)

        // Map deep-link URIs from app shortcuts to nav routes
        val shortcutRoute: String? = intent?.data?.host?.let { host ->
            when (host) {
                "open" -> "pdf_viewer"
                "merge" -> "merge_pdf"
                "compress" -> "compress_pdf"
                "create" -> "create_pdf"
                else -> null
            }
        }

        // Handle supported documents opened from other apps (VIEW/SEND).
        // The viewer converts office/text/image sources into a local PDF preview.
        val incomingPdfUri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }?.takeIf { uri -> isSupportedDocumentIntent(intent?.type, uri.toString()) }

        // Route an incoming document by its kind: spreadsheets → interactive grid, images → image
        // editor, everything else → the PDF viewer.
        val effectiveRoute = if (incomingPdfUri != null) {
            when (com.chethan616.clearpdf.utils.docKindOf(queryDisplayName(incomingPdfUri))) {
                com.chethan616.clearpdf.utils.DocKind.Excel -> "spreadsheet"
                com.chethan616.clearpdf.utils.DocKind.Image -> "image_editor"
                else -> "pdf_viewer"
            }
        } else shortcutRoute

        if (isDocumentWindow && incomingPdfUri != null) {
            // Recents shows the document's name for its window.
            val label = queryDisplayName(incomingPdfUri)
            if (label != null) runCatching {
                @Suppress("DEPRECATION")
                setTaskDescription(android.app.ActivityManager.TaskDescription(label))
            }
        }

        setContent {
            DocsApp(shortcutRoute = effectiveRoute, incomingPdfUri = incomingPdfUri, documentWindow = isDocumentWindow)
        }
    }

    /** A VIEW / SEND of a file (not our own `clearpdf://` shortcuts). */
    private fun isExternalDocument(intent: Intent?): Boolean {
        intent ?: return false
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.scheme.let { it == "content" || it == "file" } &&
                isSupportedDocumentIntent(intent.type, intent.data.toString())
            Intent.ACTION_SEND -> true
            else -> false
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun isSupportedDocumentIntent(mimeType: String?, uriString: String): Boolean {
        val lower = uriString.lowercase()
        val supportedExtension = listOf(
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".csv", ".txt", ".rtf",
            ".odt", ".ods", ".odp", ".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".heic"
        ).any(lower::contains)
        return mimeType == null ||
            mimeType == "application/pdf" ||
            mimeType?.startsWith("image/") == true ||
            mimeType?.startsWith("text/") == true ||
            mimeType?.contains("word", ignoreCase = true) == true ||
            mimeType?.contains("excel", ignoreCase = true) == true ||
            mimeType?.contains("spreadsheet", ignoreCase = true) == true ||
            mimeType?.contains("presentation", ignoreCase = true) == true ||
            supportedExtension
    }

}

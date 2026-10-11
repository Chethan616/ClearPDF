package com.chethan616.clearpdf.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * play / foss builds: Google's document scanner (edge detection, perspective fix, multi-page).
 * Returns the function that starts a scan; [onPages] gets the scanned page images, [onFinished]
 * runs once the scanner closes (scanned or not), [onError] if it couldn't start.
 */
@Composable
internal fun rememberDocumentScanner(
    onPages: (List<Uri>) -> Unit,
    onFinished: () -> Unit,
    onError: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val pages by rememberUpdatedState(onPages)
    val finished by rememberUpdatedState(onFinished)
    val error by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.mapNotNull { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) pages(uris)
        }
        finished()
    }
    return start@{
        val activity = context.findActivity() ?: run { error(); finished(); return@start }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { error(); finished() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

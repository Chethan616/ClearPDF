package com.chethan616.clearpdf.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * F-Droid build: Google's scanner isn't free software, so a scan is a photo from the phone's own
 * camera app, one page per shot (each shot is added to the scan, and the app's own filters clean
 * it up). Same contract as the play / foss scanner.
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
    val pending = remember { arrayOfNulls<Uri>(1) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending[0]
        if (ok && uri != null) pages(listOf(uri))
        finished()
    }
    fun shoot() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        pending[0] = uri
        runCatching { camera.launch(uri) }.onFailure { error(); finished() }
    }
    // The app declares CAMERA, so Android requires it granted before handing off to a camera app.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shoot() else finished()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) shoot()
        else permission.launch(Manifest.permission.CAMERA)
    }
}

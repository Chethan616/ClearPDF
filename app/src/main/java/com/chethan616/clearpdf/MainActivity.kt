package com.chethan616.clearpdf

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.ui.DocsApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request the highest available refresh rate (90Hz/120Hz)
        requestHighRefreshRate()

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

        setContent {
            DocsApp(shortcutRoute = shortcutRoute)
        }
    }

    private fun requestHighRefreshRate() {
        // Prefer the display mode with the highest refresh rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bestMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
            if (bestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val bestMode = windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }
            if (bestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
            }
        }

        // Reduce post-processing latency (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferMinimalPostProcessing = true
            }
        }

        // Keep the screen rendering at high performance while the app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

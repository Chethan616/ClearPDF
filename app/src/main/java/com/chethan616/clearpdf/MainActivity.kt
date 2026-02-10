package com.chethan616.clearpdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.chethan616.clearpdf.ui.DocsApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
            val isLightTheme = !isSystemInDarkTheme()

            CompositionLocalProvider(
                LocalIndication provides ripple(color = if (isLightTheme) Color.Black else Color.White)
            ) {
                DocsApp(shortcutRoute = shortcutRoute)
            }
        }
    }
}

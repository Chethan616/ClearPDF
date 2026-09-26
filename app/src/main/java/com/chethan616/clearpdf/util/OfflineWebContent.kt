package com.chethan616.clearpdf.util

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

/**
 * Keeps a WebView that renders local content from reaching the network, independently of the
 * app's permissions (the foss flavor holds INTERNET for the optional Office engine download).
 *
 * Two layers: [WebView.getSettings] `blockNetworkLoads`, plus [intercept] from
 * `shouldInterceptRequest`, which answers every request that is not bundled/inline content with
 * an empty 403 so it never leaves the device.
 */
internal object OfflineWebContent {

    private val allowedPrefixes = listOf("file:///android_asset/", "data:", "blob:", "about:")

    fun harden(view: WebView) {
        view.settings.blockNetworkLoads = true
        view.settings.allowContentAccess = false
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        if (allowedPrefixes.any { url.startsWith(it) }) return null
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}

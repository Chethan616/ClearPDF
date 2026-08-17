package com.chethan616.clearpdf.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.min

/**
 * Converts a local HTML string to a paginated PDF entirely on-device: an offscreen [WebView]
 * lays the HTML out at A4 width, then each page-height slice is drawn onto a [PdfDocument] page.
 * No network is used (the app holds no INTERNET permission) — JavaScript is disabled and the
 * base URL is null, so only self-contained HTML/CSS renders.
 *
 * MUST be called on the main thread (WebView requirement). [onDone] is invoked on the main
 * thread with true on success.
 */
object HtmlToPdfConverter {

    // A4 at ~96 dpi.
    private const val PAGE_W = 794
    private const val PAGE_H = 1123

    fun convert(context: Context, html: String, outputUri: Uri, onDone: (Boolean) -> Unit) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        @Suppress("DEPRECATION")
        webView.setInitialScale(100)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // Give layout a beat to settle before measuring.
                view.postDelayed({
                    val ok = runCatching { render(view, context, outputUri) }.getOrDefault(false)
                    runCatching { view.destroy() }
                    onDone(ok)
                }, 250)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun render(view: WebView, context: Context, outputUri: Uri): Boolean {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(PAGE_W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentHeight = view.measuredHeight.coerceAtLeast(1)
        view.layout(0, 0, PAGE_W, contentHeight)

        val doc = PdfDocument()
        var y = 0
        var pageNum = 1
        try {
            while (y < contentHeight) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.save()
                canvas.translate(0f, -y.toFloat())
                view.draw(canvas)
                canvas.restore()
                doc.finishPage(page)
                y += PAGE_H
                pageNum++
                if (pageNum > 500) break // safety cap
            }
            context.contentResolver.openOutputStream(outputUri)?.use { doc.writeTo(it) } ?: return false
        } finally {
            doc.close()
        }
        return true
    }
}

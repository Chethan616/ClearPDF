package com.chethan616.clearpdf.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Converts a web page (URL) or a local HTML string to a paginated PDF on-device: an offscreen
 * [WebView] lays the content out at A4 width, then each page-height slice is drawn onto a
 * [PdfDocument] page.
 *
 * - [convertUrl] fetches a user-entered URL (JavaScript enabled) — the only place the app uses
 *   the network.
 * - [convertHtml] renders self-contained HTML with JS disabled and no base URL (fully offline).
 *
 * MUST be called on the main thread (WebView requirement). [onDone] is invoked on the main
 * thread with true on success.
 */
object HtmlToPdfConverter {

    // A4 at ~96 dpi.
    private const val PAGE_W = 794
    private const val PAGE_H = 1123

    fun convertHtml(context: Context, html: String, outputUri: Uri, onDone: (Boolean) -> Unit) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        finishOnLoad(webView, context, outputUri, settleMs = 250, onDone)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun convertUrl(context: Context, url: String, outputUri: Uri, onDone: (Boolean) -> Unit) {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        // JS-heavy pages need a longer settle before the layout is stable enough to snapshot.
        finishOnLoad(webView, context, outputUri, settleMs = 900, onDone)
        webView.loadUrl(url)
    }

    private fun finishOnLoad(webView: WebView, context: Context, outputUri: Uri, settleMs: Long, onDone: (Boolean) -> Unit) {
        var handled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (handled) return
                view.postDelayed({
                    if (handled) return@postDelayed
                    handled = true
                    val ok = runCatching { render(view, context, outputUri) }.getOrDefault(false)
                    runCatching { view.destroy() }
                    onDone(ok)
                }, settleMs)
            }
        }
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

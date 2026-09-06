package com.chethan616.clearpdf.utils

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.OpenLayoutResultCallback
import android.print.OpenWriteResultCallback
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Base64
import android.util.Xml
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Renders a .docx to PDF by laying it out with `docx-preview` in an offscreen WebView and driving
 * the print framework over the result.
 *
 * Why this and not the hand-written reflow in [UniversalDocumentConverter]: that reflow re-lays
 * Word content onto a fixed A4 page with its own margins and font, so it can only ever approximate
 * the document. Real Word layout needs an engine. The engines that do it properly are enormous —
 * `app.opendocument:odr-core-android` is a 100 MB AAR, and Apache POI's OOXML half is ~17 MB of
 * jars that still only *parses*, leaving you to write the renderer anyway. `docx-preview` is
 * Apache-2.0 and adds about 48 KB to the APK once compressed, because it delegates layout to a
 * thing the phone already has: the browser engine.
 *
 * Offline: the page is loaded from `file:///android_asset`, network loads are blocked on the
 * WebView, and the app declares no `INTERNET` permission at all — so the OS would refuse a network
 * request even if a script attempted one.
 *
 * There are two ways out of the WebView, tried in order:
 *  1. **The print framework.** It honours the CSS page breaks the host page puts on each section,
 *     so the author's own page breaks land on real PDF pages. Requires [OpenLayoutResultCallback],
 *     which relies on a package trick that could fail on some device.
 *  2. **Slice rendering** — measure the content and draw page-height bands straight onto a
 *     `PdfDocument`, the way [com.chethan616.clearpdf.util.HtmlToPdfConverter] already does for the
 *     HTML tool. Pure public API, so it always works, but it cuts every N pixels regardless of
 *     where a break belongs. Still carries all of docx-preview's layout — fonts, tables, headers,
 *     real margins — so it is far closer to Word than the fallback below it.
 *
 * Every failure path returns false and leaves the caller to fall back to the hand-written reflow.
 * Nothing here is allowed to make a document that previously opened stop opening.
 */
internal object DocxWebRenderer {

    /** Generous: the WebView has to cold-start, parse the package, and lay out every page. */
    private const val TimeoutSeconds = 90L

    /** CSS pixels per inch, which is what a WebView lays `in`/`pt` lengths out against. */
    private const val CssDpi = 96

    private const val MaxSlicedPages = 500

    /**
     * Whether the print route can be used at all, decided once by actually constructing one of the
     * callbacks. If the package trick does not hold on this device the constructor throws an
     * access/verification error here, at a point where the only consequence is choosing the other
     * route — rather than half-way through writing a file.
     */
    private val printRouteAvailable: Boolean by lazy {
        runCatching {
            object : OpenLayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) = Unit
                override fun onLayoutFailed(error: CharSequence?) = Unit
                override fun onLayoutCancelled() = Unit
            }
            true
        }.getOrDefault(false)
    }

    /** A4 in mils (thousandths of an inch), for a document that declares no page size. */
    private const val A4WidthMils = 8268
    private const val A4HeightMils = 11693

    /**
     * @return true only if [outFile] now holds a non-empty PDF. False means "use the fallback" —
     *   it is never an error the user should see.
     */
    fun render(context: Context, docxBytes: ByteArray, outFile: File): Boolean {
        // The whole flow blocks on a latch that only the main thread can release, so being called
        // *from* the main thread would deadlock outright. The converter runs on IO today; this is
        // here so that stays true by construction rather than by memory.
        if (Looper.myLooper() == Looper.getMainLooper()) return false

        val (widthMils, heightMils) = readPageSizeMils(docxBytes)
        val latch = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null

        val finish = { ok: Boolean ->
            succeeded.set(ok)
            latch.countDown()
        }

        main.post {
            runCatching {
                val view = WebView(context)
                webView = view
                view.settings.javaScriptEnabled = true
                // Redundant with the missing INTERNET permission, and kept anyway: two independent
                // guarantees that a document can never phone home.
                view.settings.blockNetworkLoads = true
                view.settings.allowFileAccess = true

                view.addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onRendered() {
                            // JavascriptInterface callbacks arrive on the WebView's JS bridge
                            // thread; everything below touches the view and must be on main.
                            main.post {
                                // Slicing is the fallback for the print route failing *and* for it
                                // being unavailable, so both paths converge on the same retry.
                                val slice = {
                                    finish(
                                        runCatching { sliceToPdf(view, widthMils, heightMils, outFile) }
                                            .getOrDefault(false)
                                    )
                                }
                                if (!printRouteAvailable) {
                                    slice()
                                } else {
                                    runCatching {
                                        printToPdf(view, widthMils, heightMils, outFile) { ok ->
                                            if (ok) finish(true) else slice()
                                        }
                                    }.onFailure { slice() }
                                }
                            }
                        }

                        @android.webkit.JavascriptInterface
                        @Suppress("UNUSED_PARAMETER")
                        fun onFailed(reason: String) = finish(false)
                    },
                    "AndroidDocx"
                )

                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val base64 = Base64.encodeToString(docxBytes, Base64.NO_WRAP)
                        view.evaluateJavascript("renderDocx('$base64')", null)
                    }
                }
                view.loadUrl("file:///android_asset/docx/index.html")
            }.onFailure { finish(false) }
        }

        val completed = runCatching { latch.await(TimeoutSeconds, TimeUnit.SECONDS) }.getOrDefault(false)
        main.post { runCatching { webView?.destroy() } }

        if (!completed || !succeeded.get()) {
            runCatching { if (outFile.exists()) outFile.delete() }
            return false
        }
        if (outFile.length() <= 0L) {
            runCatching { outFile.delete() }
            return false
        }
        return true
    }

    /**
     * Drives the adapter's `onLayout` → `onWrite` → `onFinish` sequence by hand, which is what the
     * system print dialog would otherwise do for us. Main thread only.
     */
    private fun printToPdf(
        view: WebView,
        widthMils: Int,
        heightMils: Int,
        outFile: File,
        done: (Boolean) -> Unit
    ) {
        outFile.parentFile?.mkdirs()
        val adapter: PrintDocumentAdapter = view.createPrintDocumentAdapter(outFile.nameWithoutExtension)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize("docx", "docx", widthMils, heightMils))
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            // The document's own margins are already page padding in the HTML, so any margin here
            // would be applied a second time and shrink every page's content.
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        var descriptor: ParcelFileDescriptor? = null
        val cleanUp = { ok: Boolean ->
            runCatching { descriptor?.close() }
            runCatching { adapter.onFinish() }
            done(ok)
        }

        adapter.onStart()
        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : OpenLayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    runCatching {
                        descriptor = ParcelFileDescriptor.open(
                            outFile,
                            ParcelFileDescriptor.MODE_READ_WRITE or
                                ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE
                        )
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            descriptor,
                            CancellationSignal(),
                            object : OpenWriteResultCallback() {
                                override fun onWriteFinished(pages: Array<out PageRange>?) = cleanUp(true)
                                override fun onWriteFailed(error: CharSequence?) = cleanUp(false)
                                override fun onWriteCancelled() = cleanUp(false)
                            }
                        )
                    }.onFailure { cleanUp(false) }
                }

                override fun onLayoutFailed(error: CharSequence?) = cleanUp(false)
                override fun onLayoutCancelled() = cleanUp(false)
            },
            Bundle()
        )
    }

    /**
     * Draws the laid-out page as page-height bands onto a [PdfDocument], the same technique
     * [com.chethan616.clearpdf.util.HtmlToPdfConverter] uses for the HTML tool.
     *
     * Public API throughout, so this always works — but it cuts strictly every page height, with no
     * notion of where a break belongs, so a band boundary can fall through a line of text. That is
     * why it is second choice rather than the only implementation.
     */
    private fun sliceToPdf(view: WebView, widthMils: Int, heightMils: Int, outFile: File): Boolean {
        val pageWidth = (widthMils.toLong() * CssDpi / 1000L).toInt().coerceIn(80, 5000)
        val pageHeight = (heightMils.toLong() * CssDpi / 1000L).toInt().coerceIn(80, 5000)

        view.measure(
            View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentHeight = view.measuredHeight.coerceAtLeast(1)
        view.layout(0, 0, pageWidth, contentHeight)

        outFile.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            var y = 0
            var pageNumber = 1
            while (y < contentHeight && pageNumber <= MaxSlicedPages) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                page.canvas.save()
                page.canvas.translate(0f, -y.toFloat())
                view.draw(page.canvas)
                page.canvas.restore()
                document.finishPage(page)
                y += pageHeight
                pageNumber++
            }
            FileOutputStream(outFile).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return outFile.length() > 0
    }

    /**
     * The document's own page size from `<w:sectPr><w:pgSz>`, so the PDF page matches what the
     * author set rather than defaulting everything to A4 — a US Letter document rendered onto A4
     * reflows every line.
     *
     * Word stores these in twips (1/1440 inch); the print framework wants mils (1/1000 inch).
     */
    private fun readPageSizeMils(docxBytes: ByteArray): Pair<Int, Int> {
        val documentXml = runCatching {
            ZipInputStream(docxBytes.inputStream()).use { zip ->
                var found: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.removePrefix("/") == "word/document.xml") {
                        found = zip.readBytes()
                        break
                    }
                }
                found
            }
        }.getOrNull() ?: return A4WidthMils to A4HeightMils

        return runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(documentXml.inputStream(), "UTF-8")
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "w:pgSz") {
                    val w = parser.getAttributeValue(null, "w:w")?.toIntOrNull()
                    val h = parser.getAttributeValue(null, "w:h")?.toIntOrNull()
                    if (w != null && h != null && w > 0 && h > 0) {
                        // `w:orient` is advisory — Word already swaps w/h for landscape sections,
                        // so trusting the numbers is both simpler and more reliable.
                        return@runCatching twipsToMils(w) to twipsToMils(h)
                    }
                }
                event = parser.next()
            }
            A4WidthMils to A4HeightMils
        }.getOrDefault(A4WidthMils to A4HeightMils)
    }

    private fun twipsToMils(twips: Int): Int =
        (twips * 1000L / 1440L).toInt().coerceIn(1000, 40000)
}

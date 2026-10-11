package com.chethan616.clearpdf.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream

/**
 * Prints a PDF exactly as it is through Android's own print screen (after Gander's Print). Word and
 * other Office files are converted first — by the Office engine (LibreOffice) when it is installed,
 * so the printout matches the document's real pages.
 */
object PdfPrint {

    fun print(context: Context, pdf: Uri, jobName: String): Boolean {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val name = jobName.ifBlank { "Document" }
        return runCatching {
            manager.print(name, Adapter(context.applicationContext, pdf, name), PrintAttributes.Builder().build())
        }.isSuccess
    }

    private class Adapter(private val context: Context, private val pdf: Uri, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder(if (name.endsWith(".pdf", true)) name else "$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                context.contentResolver.openInputStream(pdf)?.use { input ->
                    FileOutputStream(destination.fileDescriptor).use { out -> input.copyTo(out) }
                } ?: run { callback.onWriteFailed(null); return }
                if (cancellationSignal?.isCanceled == true) callback.onWriteCancelled()
                else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}

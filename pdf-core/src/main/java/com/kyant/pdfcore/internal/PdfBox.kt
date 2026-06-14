package com.kyant.pdfcore.internal

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Centralised, idempotent initialisation for PdfBox-Android.
 *
 * PdfBox needs its resource loader primed with an Android [Context] before any document
 * is loaded (it resolves bundled fonts/CMaps through assets). Call [ensureInitialized]
 * at the top of every entry point that touches PdfBox.
 */
internal object PdfBox {

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }
}

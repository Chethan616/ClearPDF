package com.kyant.pdfcore.flatten

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * Flattens a PDF's interactive AcroForm fields into static page content so the document can no
 * longer be edited as a form (values become permanent). The source URI is never modified; the
 * result is written to [destinationUri]. Runs on the caller's worker thread.
 *
 * Returns the number of form fields that were flattened (0 = the PDF had no form; a static copy
 * is still written).
 */
object PdfFlattener {

    fun flatten(context: Context, sourceUri: Uri, destinationUri: Uri): Int {
        PdfBox.ensureInitialized(context)
        var fieldCount = 0
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val acroForm = doc.documentCatalog?.acroForm
                if (acroForm != null) {
                    fieldCount = acroForm.fields?.size ?: 0
                    if (fieldCount > 0) {
                        runCatching { acroForm.flatten() }
                    }
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
        return fieldCount
    }
}

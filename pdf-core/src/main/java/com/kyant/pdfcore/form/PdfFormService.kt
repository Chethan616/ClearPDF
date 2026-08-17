package com.kyant.pdfcore.form

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField

/**
 * Reads and fills interactive AcroForm fields via PDFBox. Handles the common terminal field
 * types (text, checkbox, choice); non-terminal container fields are skipped. The source URI is
 * never modified — filling writes to a destination URI. Runs on the caller's worker thread.
 */
object PdfFormService {

    enum class FieldType { TEXT, CHECKBOX, CHOICE }

    data class FormField(
        val name: String,
        val type: FieldType,
        val value: String,
        val options: List<String> = emptyList()
    )

    /** Enumerate the editable terminal fields of a PDF's form (empty if it has none). */
    fun readFields(context: Context, uri: Uri): List<FormField> {
        PdfBox.ensureInitialized(context)
        val fields = mutableListOf<FormField>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val acro = doc.documentCatalog?.acroForm ?: return emptyList()
                acro.fields?.forEach { collect(it, fields) }
            }
        }
        return fields
    }

    private fun collect(field: PDField, out: MutableList<FormField>) {
        when (field) {
            is PDNonTerminalField -> field.children?.forEach { collect(it, out) }
            is PDTextField -> out.add(FormField(field.fullyQualifiedName, FieldType.TEXT, field.valueAsString ?: ""))
            is PDCheckBox -> out.add(FormField(field.fullyQualifiedName, FieldType.CHECKBOX, if (field.isChecked) "true" else "false"))
            is PDChoice -> out.add(
                FormField(
                    field.fullyQualifiedName, FieldType.CHOICE, field.valueAsString ?: "",
                    runCatching { field.options ?: emptyList() }.getOrDefault(emptyList())
                )
            )
            else -> { /* buttons / signatures / unsupported — skip */ }
        }
    }

    /**
     * Apply [values] (keyed by fully-qualified field name) and write to [destinationUri].
     * Returns the number of fields written. When [flatten] is true the filled fields are baked
     * into static page content so they can no longer be edited.
     */
    fun fill(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        values: Map<String, String>,
        flatten: Boolean
    ): Int {
        PdfBox.ensureInitialized(context)
        var written = 0
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val acro = doc.documentCatalog?.acroForm ?: throw IllegalStateException("This PDF has no form fields")
                acro.fields?.forEach { written += applyValue(it, values) }
                if (flatten && written > 0) runCatching { acro.flatten() }
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    doc.save(output)
                } ?: throw IllegalStateException("Unable to write PDF")
            }
        } ?: throw IllegalStateException("Unable to read PDF")
        return written
    }

    private fun applyValue(field: PDField, values: Map<String, String>): Int {
        var count = 0
        when (field) {
            is PDNonTerminalField -> field.children?.forEach { count += applyValue(it, values) }
            is PDTextField -> values[field.fullyQualifiedName]?.let {
                runCatching { field.setValue(it) }.onSuccess { count++ }
            }
            is PDCheckBox -> values[field.fullyQualifiedName]?.let {
                runCatching { if (it == "true") field.check() else field.unCheck() }.onSuccess { count++ }
            }
            is PDChoice -> values[field.fullyQualifiedName]?.let {
                runCatching { field.setValue(it) }.onSuccess { count++ }
            }
            else -> { /* skip */ }
        }
        return count
    }
}

package com.kyant.pdfcore.form

import android.content.Context
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText

/**
 * One on-page box of an AcroForm field, so a viewer can let people fill the form where it is
 * printed instead of in a list of field names. Geometry is normalized (0..1) in display space:
 * top-left origin, page rotation applied, like the viewer's text blocks.
 */
data class PdfFormWidget(
    val fieldName: String,
    val type: Type,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val value: String,
    /** Choice fields: what the list shows. */
    val options: List<String> = emptyList(),
    /** Checkbox / radio: the value this box sets when on. */
    val onValue: String = "",
    /** From the field's default appearance: points (0 = auto-size) and as a fraction of page height. */
    val fontSize: Float = 0f,
    val fontSizeNorm: Float = 0f,
    val textArgb: Int = 0xFF000000.toInt(),
    /** 0 left, 1 centred, 2 right (the field's /Q). */
    val alignment: Int = 0,
    val multiline: Boolean = false,
    val maxLength: Int = -1,
    val comb: Boolean = false,
    val password: Boolean = false,
    val readOnly: Boolean = false,
    /** The page's display height in points, to turn point sizes and paddings into pixels. */
    val pageHeightPt: Float = 792f
) {
    enum class Type { TEXT, CHECKBOX, RADIO, CHOICE }

    /** Unique per box: radio groups and repeated fields share a name. */
    val key: String get() = "$fieldName#$pageIndex#${(left * 10000).toInt()}#${(top * 10000).toInt()}"
}

object PdfFormWidgets {

    /** Every fillable box in the document, by page (empty when it has no form). */
    fun read(context: Context, uri: Uri): Map<Int, List<PdfFormWidget>> {
        PdfBox.ensureInitialized(context)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc -> read(doc) }
            } ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    fun read(doc: PDDocument): Map<Int, List<PdfFormWidget>> {
        val acro = doc.documentCatalog?.acroForm ?: return emptyMap()
        // Widgets don't always name their page (/P), so map annotation objects to pages once.
        val pageOf = HashMap<COSBase, Int>()
        for (i in 0 until doc.numberOfPages) {
            runCatching { doc.getPage(i).annotations }.getOrNull()?.forEach { pageOf[it.cosObject] = i }
        }
        val out = ArrayList<PdfFormWidget>()
        fun visit(field: PDField) {
            when (field) {
                is PDNonTerminalField -> field.children?.forEach { visit(it) }
                is PDTextField -> field.widgets.forEach { w ->
                    widget(doc, w, pageOf, field.fullyQualifiedName, PdfFormWidget.Type.TEXT, field.valueAsString.orEmpty(), field)?.let {
                        out += it.copy(
                            multiline = field.isMultiline,
                            maxLength = field.maxLen,
                            comb = field.isComb,
                            password = field.isPassword
                        )
                    }
                }
                is PDCheckBox -> field.widgets.forEach { w ->
                    val on = onValueOf(w) ?: runCatching { field.onValue }.getOrNull().orEmpty()
                    widget(doc, w, pageOf, field.fullyQualifiedName, PdfFormWidget.Type.CHECKBOX, if (field.isChecked) "true" else "false", field)
                        ?.let { out += it.copy(onValue = on) }
                }
                is PDRadioButton -> field.widgets.forEach { w ->
                    val on = onValueOf(w) ?: return@forEach
                    widget(doc, w, pageOf, field.fullyQualifiedName, PdfFormWidget.Type.RADIO, field.valueAsString.orEmpty(), field)
                        ?.let { out += it.copy(onValue = on) }
                }
                is PDChoice -> field.widgets.forEach { w ->
                    val options = runCatching { field.optionsDisplayValues }.getOrNull().orEmpty()
                        .ifEmpty { runCatching { field.optionsExportValues }.getOrNull().orEmpty() }
                    widget(doc, w, pageOf, field.fullyQualifiedName, PdfFormWidget.Type.CHOICE, field.valueAsString.orEmpty().trim('[', ']'), field)
                        ?.let { out += it.copy(options = options) }
                }
                else -> Unit // push buttons, signatures
            }
        }
        runCatching { acro.fields }.getOrNull()?.forEach { runCatching { visit(it) } }
        return out.groupBy { it.pageIndex }
    }

    /**
     * Writes [values] (field name → value; "true"/"false" for checkboxes, the on-value for radio
     * groups) into the document's form, regenerating each field's appearance in its own font
     * settings. Returns how many fields were set.
     */
    fun apply(doc: PDDocument, values: Map<String, String>): Int {
        if (values.isEmpty()) return 0
        val acro = doc.documentCatalog?.acroForm ?: return 0
        var count = 0
        for ((name, value) in values) {
            val field = runCatching { acro.getField(name) }.getOrNull() ?: continue
            val ok = runCatching {
                when (field) {
                    is PDCheckBox -> if (value == "true") field.check() else field.unCheck()
                    is PDRadioButton -> field.setValue(value)
                    is PDTextField -> field.setValue(value)
                    is PDChoice -> field.setValue(value)
                    else -> return@runCatching false
                }
                true
            }.getOrDefault(false)
            if (ok) count++
        }
        return count
    }

    private fun widget(
        doc: PDDocument,
        w: PDAnnotationWidget,
        pageOf: Map<COSBase, Int>,
        name: String,
        type: PdfFormWidget.Type,
        value: String,
        field: PDField
    ): PdfFormWidget? {
        val text = field as? PDVariableText
        if (w.isHidden || w.isNoView) return null
        val pageIndex = pageOf[w.cosObject] ?: w.page?.let { p -> doc.pages.indexOf(p).takeIf { it >= 0 } } ?: return null
        val page = doc.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox ?: return null
        val r = w.rectangle ?: return null
        val rotation = ((page.rotation % 360) + 360) % 360
        val norm = normalize(r, box, rotation) ?: return null
        val dispH = if (rotation == 90 || rotation == 270) box.width else box.height
        val da = text?.let { runCatching { it.defaultAppearance }.getOrNull() }.orEmpty()
        val size = Regex("""([0-9.]+)\s+Tf""").find(da)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return PdfFormWidget(
            fieldName = name,
            type = type,
            pageIndex = pageIndex,
            left = norm[0], top = norm[1], right = norm[2], bottom = norm[3],
            value = value,
            fontSize = size,
            fontSizeNorm = if (dispH > 0f) size / dispH else 0f,
            pageHeightPt = dispH,
            textArgb = colorOf(da),
            alignment = text?.let { runCatching { it.q }.getOrDefault(0) } ?: 0,
            readOnly = runCatching { field.isReadOnly }.getOrDefault(false)
        )
    }

    /** The appearance state that means "on" for a checkbox / radio box (anything but /Off). */
    private fun onValueOf(w: PDAnnotationWidget): String? = runCatching {
        w.appearance?.normalAppearance?.takeIf { it.isSubDictionary }?.subDictionary?.keys
            ?.firstOrNull { it != COSName.Off }?.name
    }.getOrNull()

    /** PDF user space → normalized display rect (left, top, right, bottom). */
    private fun normalize(r: PDRectangle, box: PDRectangle, rotation: Int): FloatArray? {
        val w = box.width
        val h = box.height
        if (w <= 0f || h <= 0f) return null
        fun map(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
            90 -> (y - box.lowerLeftY) / h to (x - box.lowerLeftX) / w
            180 -> (box.upperRightX - x) / w to (y - box.lowerLeftY) / h
            270 -> (box.upperRightY - y) / h to (box.upperRightX - x) / w
            else -> (x - box.lowerLeftX) / w to (box.upperRightY - y) / h
        }
        val a = map(r.lowerLeftX, r.lowerLeftY)
        val b = map(r.upperRightX, r.upperRightY)
        val l = minOf(a.first, b.first).coerceIn(0f, 1f)
        val t = minOf(a.second, b.second).coerceIn(0f, 1f)
        val rr = maxOf(a.first, b.first).coerceIn(0f, 1f)
        val bb = maxOf(a.second, b.second).coerceIn(0f, 1f)
        return if (rr - l <= 0f || bb - t <= 0f) null else floatArrayOf(l, t, rr, bb)
    }

    /** The text colour in a default appearance string ("0 g", "1 0 0 rg"); black otherwise. */
    private fun colorOf(da: String): Int {
        fun c(v: String) = ((v.toFloatOrNull() ?: 0f).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        Regex("""([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)\s+rg""").find(da)?.groupValues?.let {
            return (0xFF shl 24) or (c(it[1]) shl 16) or (c(it[2]) shl 8) or c(it[3])
        }
        Regex("""([0-9.]+)\s+g(?:\s|$)""").find(da)?.groupValues?.let {
            val g = c(it[1])
            return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return 0xFF000000.toInt()
    }
}

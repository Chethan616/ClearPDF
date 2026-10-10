package com.kyant.pdfcore.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Replace the text inside one line's box with [newText]. The box is normalized (0..1) in the same
 * display space [PdfTextService] reports blocks in: top-left origin, page rotation applied.
 * [backgroundArgb] paints behind the new text only when the original glyphs cannot be removed.
 */
data class PdfTextEdit(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val newText: String,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt()
)

/**
 * In-place editing of a PDF's existing text — a real edit, not a sticker over the page.
 *
 * For each edit:
 * 1. The page's content stream is replayed with operator bookkeeping, so every glyph is tied to
 *    the exact text-showing operator (`Tj`, `TJ`, `'`, `"`) that drew it.
 * 2. The glyphs inside the edited box are **removed from the content stream**. Each removed glyph
 *    becomes a `TJ` spacing adjustment of exactly its own advance, so every other glyph in the run
 *    stays exactly where it was. The old text is gone (not hidden): it no longer renders, selects,
 *    searches or extracts.
 * 3. The new text is drawn with **the original font** at the original text rendering matrix (size,
 *    position, rotation, horizontal scale, colour), so it matches the line it replaces. If that font
 *    cannot draw the new characters (embedded subsets usually hold only the glyphs the document
 *    used), the closest standard face (serif / sans / mono, bold, italic) is used, then an
 *    embedded system TrueType font, and as a last resort the text is rendered as an image, which
 *    handles any script.
 *
 * Text it cannot surgically remove — inside form XObjects, Type 3 or vertical fonts, or the
 * invisible OCR layer of a scanned page — falls back to painting [PdfTextEdit.backgroundArgb]
 * over the old line before drawing the new text.
 */
object PdfTextEditor {

    fun applyEdits(doc: PDDocument, pageIndex: Int, edits: List<PdfTextEdit>) {
        if (edits.isEmpty() || pageIndex !in 0 until doc.numberOfPages) return
        val page = doc.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox ?: return
        val rotation = ((page.rotation % 360) + 360) % 360
        val (dispW, dispH) = if (rotation == 90 || rotation == 270) box.height to box.width else box.width to box.height
        if (dispW <= 0f || dispH <= 0f) return

        val recorder = GlyphRecorder().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        runCatching { recorder.getText(doc) }

        val tokens = runCatching { parseTokens(page) }.getOrNull()
        val operatorIndex = tokens?.indices?.filter { tokens[it] is Operator }.orEmpty()

        // Per edit: which recorded glyphs it covers, and whether they can be removed surgically.
        class Plan(val edit: PdfTextEdit, val glyphs: List<Glyph>, val removable: Boolean)
        val plans = edits.map { e ->
            val hit = recorder.glyphs.filter { g ->
                val tp = g.tp
                val cx = (tp.x + tp.width / 2f) / dispW
                val by = tp.y / dispH
                val slack = (e.bottom - e.top) * 0.25f
                cx >= e.left && cx <= e.right && by >= e.top - slack && by <= e.bottom + slack
            }
            val removable = tokens != null && hit.isNotEmpty() && hit.all { g ->
                g.depth == 0 && g.renderMode != RenderingMode.NEITHER && g.font !is PDType3Font &&
                    !g.font.isVertical && g.tfs != 0f &&
                    operatorIndex.getOrNull(g.op)?.let { (tokens[it] as Operator).name in TextShowOps } == true
            }
            Plan(e, hit, removable)
        }

        // 1) Remove the old glyphs from the content stream (all edits on the page in one rewrite).
        val toRemove = plans.filter { it.removable }.flatMap { it.glyphs }
        var removedOk = false
        if (toRemove.isNotEmpty() && tokens != null) {
            removedOk = runCatching {
                val out = removeGlyphs(tokens, operatorIndex, toRemove)
                val stream = PDStream(doc)
                stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(out) }
                page.setContents(stream)
                true
            }.getOrDefault(false)
        }

        // 2) Draw the replacements on top, in a fresh, state-isolated stream.
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (plan in plans) {
                val e = plan.edit
                val removed = plan.removable && removedOk
                if (!removed) coverOldLine(cs, e, box.lowerLeftX, box.lowerLeftY, dispW, dispH, rotation)
                val text = e.newText.replace('\n', ' ').trimEnd()
                if (text.isEmpty()) continue
                val first = plan.glyphs.minByOrNull { it.tp.x } ?: run {
                    drawUnanchored(doc, cs, e, text, box.lowerLeftX, box.lowerLeftY, dispW, dispH, rotation)
                    continue
                }
                drawLike(doc, cs, first, text, box.lowerLeftX, box.lowerLeftY)
            }
        }
    }

    // ── Glyph bookkeeping ─────────────────────────────────────────────────────────────────

    private class Glyph(
        val op: Int,          // ordinal of the text-showing operator in the page's operator list
        val str: Int,         // which string operand of that operator (TJ arrays hold several)
        val idx: Int,         // glyph index inside that string
        val depth: Int,       // form XObject nesting (0 = page content)
        val tp: TextPosition,
        val font: PDFont,
        val tfs: Float, val tc: Float, val tw: Float, val th: Float,
        val rgb: Int,
        val renderMode: RenderingMode
    )

    private class GlyphRecorder : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()
        private var depth = 0
        private var op = -1
        private var str = -1
        private var idx = 0

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth == 0) { op++; str = -1 }
            super.processOperator(operator, operands)
        }

        override fun showForm(form: PDFormXObject) {
            depth++
            try { super.showForm(form) } finally { depth-- }
        }

        override fun showText(string: ByteArray) {
            if (depth == 0) { str++; idx = 0 }
            super.showText(string)
        }

        override fun processTextPosition(text: TextPosition) {
            val gs = graphicsState
            val ts = gs.textState
            val font = text.font ?: ts.font ?: return
            glyphs += Glyph(
                op = op, str = str, idx = idx++, depth = depth, tp = text, font = font,
                tfs = ts.fontSize, tc = ts.characterSpacing, tw = ts.wordSpacing,
                th = ts.horizontalScaling / 100f,
                rgb = runCatching { gs.nonStrokingColor.toRGB() }.getOrDefault(0),
                renderMode = ts.renderingMode
            )
        }
    }

    private val TextShowOps = setOf("Tj", "TJ", "'", "\"")

    private fun parseTokens(page: PDPage): MutableList<Any> {
        val parser = PDFStreamParser(page)
        val out = ArrayList<Any>()
        while (true) out += parser.parseNextToken() ?: break
        return out
    }

    /**
     * Rewrites every affected text-showing operator as a `TJ` whose removed glyphs are replaced by
     * spacing numbers equal to their advance, so the rest of the run doesn't move.
     */
    private fun removeGlyphs(tokens: MutableList<Any>, operatorIndex: List<Int>, glyphs: List<Glyph>): MutableList<Any> {
        val out = ArrayList(tokens)
        val byOp = glyphs.groupBy { it.op }
        // Right to left, so earlier token positions stay valid while later slices are replaced.
        for (op in byOp.keys.sortedDescending()) {
            val opTok = operatorIndex[op]
            val operator = out[opTok] as Operator
            var firstOperand = opTok
            while (firstOperand > 0 && out[firstOperand - 1] !is Operator) firstOperand--
            val operands = out.subList(firstOperand, opTok).toList()
            val g0 = byOp.getValue(op).first()
            val removed = byOp.getValue(op).map { it.str to it.idx }.toHashSet()

            fun rebuild(s: COSString, strIdx: Int, into: MutableList<COSBase>) {
                val input = ByteArrayInputStream(s.bytes)
                val run = ByteArrayOutputStream()
                var gi = 0
                fun flush() {
                    if (run.size() > 0) { into += COSString(run.toByteArray()); run.reset() }
                }
                while (input.available() > 0) {
                    val before = input.available()
                    val code = g0.font.readCode(input)
                    val len = before - input.available()
                    val bytes = s.bytes.copyOfRange(s.bytes.size - before, s.bytes.size - before + len)
                    if ((strIdx to gi) in removed) {
                        flush()
                        val w0 = g0.font.getWidth(code)
                        val tw = if (len == 1 && code == 32) g0.tw else 0f
                        val adj = -(w0 + (g0.tc + tw) * 1000f / g0.tfs)
                        val last = into.lastOrNull()
                        if (last is COSNumber) into[into.lastIndex] = COSFloat(last.floatValue() + adj)
                        else into += COSFloat(adj)
                    } else {
                        run.write(bytes)
                    }
                    gi++
                }
                flush()
            }

            val array = COSArray()
            val replacement = ArrayList<Any>()
            when (operator.name) {
                "TJ" -> {
                    val src = operands.lastOrNull() as? COSArray ?: continue
                    val items = ArrayList<COSBase>()
                    var strIdx = -1
                    for (i in 0 until src.size()) {
                        when (val it = src.getObject(i)) {
                            is COSString -> { strIdx++; rebuild(it, strIdx, items) }
                            is COSNumber -> {
                                val last = items.lastOrNull()
                                if (last is COSNumber) items[items.lastIndex] = COSFloat(last.floatValue() + it.floatValue())
                                else items += it
                            }
                            else -> items += it
                        }
                    }
                    items.forEach { array.add(it) }
                    replacement.add(array); replacement.add(Operator.getOperator("TJ"))
                }
                "Tj", "'" -> {
                    val s = operands.lastOrNull() as? COSString ?: continue
                    val items = ArrayList<COSBase>(); rebuild(s, 0, items); items.forEach { array.add(it) }
                    if (operator.name == "'") replacement.add(Operator.getOperator("T*"))
                    replacement.add(array); replacement.add(Operator.getOperator("TJ"))
                }
                "\"" -> {
                    if (operands.size < 3) continue
                    val s = operands[2] as? COSString ?: continue
                    val items = ArrayList<COSBase>(); rebuild(s, 0, items); items.forEach { array.add(it) }
                    replacement.add(operands[0]); replacement.add(Operator.getOperator("Tw"))
                    replacement.add(operands[1]); replacement.add(Operator.getOperator("Tc"))
                    replacement.add(Operator.getOperator("T*"))
                    replacement.add(array); replacement.add(Operator.getOperator("TJ"))
                }
                else -> continue
            }
            val slice = out.subList(firstOperand, opTok + 1)
            slice.clear()
            slice.addAll(replacement)
        }
        return out
    }

    // ── Drawing the replacement ───────────────────────────────────────────────────────────

    /** Draws [text] exactly where and how [first] (the old line's first glyph) was drawn. */
    private fun drawLike(doc: PDDocument, cs: PDPageContentStream, first: Glyph, text: String, llx: Float, lly: Float) {
        // TextPosition's matrix is the text rendering matrix shifted by the crop box origin; undo it.
        val m = first.tp.textMatrix
        val trm = Matrix(
            m.getValue(0, 0), m.getValue(0, 1), m.getValue(1, 0), m.getValue(1, 1),
            m.translateX + llx, m.translateY + lly
        )
        val rgb = first.rgb
        val font = if (canShow(first.font, text)) first.font else fallbackFont(doc, first.font, text)
        if (font != null) {
            cs.beginText()
            cs.setNonStrokingColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            // Size 1 at the full rendering matrix reproduces size, scale, skew and rotation exactly.
            cs.setFont(font, 1f)
            cs.setTextMatrix(trm)
            cs.showText(text)
            cs.endText()
        } else {
            drawAsImage(doc, cs, first.font, text, trm, rgb)
        }
    }

    /** No glyph to copy (e.g. an empty OCR box): draw in a matching face sized to the box. */
    private fun drawUnanchored(
        doc: PDDocument, cs: PDPageContentStream, e: PdfTextEdit, text: String,
        llx: Float, lly: Float, dispW: Float, dispH: Float, rotation: Int
    ) {
        if (rotation != 0) return
        val size = (e.bottom - e.top) * dispH * 0.78f
        if (size <= 0f) return
        val trm = Matrix(size, 0f, 0f, size, llx + e.left * dispW, lly + (1f - e.bottom) * dispH + size * 0.22f)
        val font = fallbackFont(doc, null, text)
        if (font != null) {
            cs.beginText(); cs.setNonStrokingColor(0, 0, 0); cs.setFont(font, 1f); cs.setTextMatrix(trm); cs.showText(text); cs.endText()
        } else drawAsImage(doc, cs, null, text, trm, 0)
    }

    private fun coverOldLine(
        cs: PDPageContentStream, e: PdfTextEdit, llx: Float, lly: Float, dispW: Float, dispH: Float, rotation: Int
    ) {
        if (rotation != 0) return
        val bg = e.backgroundArgb
        cs.saveGraphicsState()
        cs.setNonStrokingColor((bg shr 16) and 0xFF, (bg shr 8) and 0xFF, bg and 0xFF)
        val pad = (e.bottom - e.top) * dispH * 0.08f
        cs.addRect(
            llx + e.left * dispW - pad,
            lly + (1f - e.bottom) * dispH - pad,
            (e.right - e.left) * dispW + pad * 2,
            (e.bottom - e.top) * dispH + pad * 2
        )
        cs.fill()
        cs.restoreGraphicsState()
    }

    /**
     * Last resort, for scripts no available PDF font can draw (or that need shaping, which PDF
     * text output doesn't do — Devanagari, Arabic…): Android lays the text out with full shaping
     * and font fallback, and the result is placed on the baseline as an image.
     */
    private fun drawAsImage(doc: PDDocument, cs: PDPageContentStream, like: PDFont?, text: String, trm: Matrix, rgb: Int) {
        val pt = kotlin.math.hypot(trm.getValue(1, 0), trm.getValue(1, 1))
        if (pt <= 0f) return
        val scale = 4f // px per pt: sharp at print zoom
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = pt * scale
            color = 0xFF000000.toInt() or rgb
            typeface = typefaceLike(like)
        }
        val fm = paint.fontMetrics
        val w = paint.measureText(text).coerceAtLeast(1f)
        val h = (fm.descent - fm.ascent).coerceAtLeast(1f)
        val bmp = Bitmap.createBitmap(w.toInt() + 2, h.toInt() + 2, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 1f, -fm.ascent + 1f, paint)
        val image = runCatching { LosslessFactory.createFromImage(doc, bmp) }.getOrNull() ?: return
        bmp.recycle()
        val x = trm.translateX
        val y = trm.translateY - (fm.descent / scale)
        cs.drawImage(image, x, y, (w + 2f) / scale, (h + 2f) / scale)
    }

    // ── Fonts ─────────────────────────────────────────────────────────────────────────────

    /** Whether [font] can draw every character of [text] (embedded subsets often can't). */
    private fun canShow(font: PDFont, text: String): Boolean = try {
        if (font is PDType3Font || font.isVertical) false
        else {
            var ok = true
            var i = 0
            while (i < text.length && ok) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                val bytes = font.encode(String(Character.toChars(cp)))
                if (cp != ' '.code) {
                    var code = 0
                    for (b in bytes) code = (code shl 8) or (b.toInt() and 0xFF)
                    if (font is PDVectorFont && !font.hasGlyph(code)) ok = false
                    else if (font.getWidth(code) <= 0f) ok = false
                }
            }
            ok
        }
    } catch (_: Exception) {
        false
    }

    private class Traits(val bold: Boolean, val italic: Boolean, val serif: Boolean, val mono: Boolean)

    private fun traitsOf(font: PDFont?): Traits {
        val name = (font?.name ?: "").substringAfter('+').lowercase()
        val d = font?.fontDescriptor
        val bold = listOf("bold", "black", "heavy", "semibold", "demi").any { name.contains(it) } ||
            d?.isForceBold == true || (d?.fontWeight ?: 400f) >= 600f
        val italic = name.contains("italic") || name.contains("oblique") || d?.isItalic == true
        val mono = listOf("courier", "mono", "consol", "menlo").any { name.contains(it) } || d?.isFixedPitch == true
        val serif = !mono && (d?.isSerif == true ||
            listOf("times", "serif", "georgia", "garamond", "cambria", "book", "minion", "palatino", "baskerville").any { name.contains(it) } &&
            !name.contains("sans"))
        return Traits(bold, italic, serif, mono)
    }

    private fun fallbackFont(doc: PDDocument, like: PDFont?, text: String): PDFont? {
        val t = traitsOf(like)
        val std = when {
            t.mono -> when { t.bold && t.italic -> PDType1Font.COURIER_BOLD_OBLIQUE; t.bold -> PDType1Font.COURIER_BOLD; t.italic -> PDType1Font.COURIER_OBLIQUE; else -> PDType1Font.COURIER }
            t.serif -> when { t.bold && t.italic -> PDType1Font.TIMES_BOLD_ITALIC; t.bold -> PDType1Font.TIMES_BOLD; t.italic -> PDType1Font.TIMES_ITALIC; else -> PDType1Font.TIMES_ROMAN }
            else -> when { t.bold && t.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE; t.bold -> PDType1Font.HELVETICA_BOLD; t.italic -> PDType1Font.HELVETICA_OBLIQUE; else -> PDType1Font.HELVETICA }
        }
        if (canShow(std, text)) return std
        // Beyond Latin-1: embed (subset) a system TrueType face of the same style.
        val weight = if (t.bold) "Bold" else "Regular"
        val names = buildList {
            if (t.serif) { add("NotoSerif-$weight.ttf"); add("NotoSerif-Regular.ttf") }
            if (t.mono) { add("DroidSansMono.ttf"); add("CutiveMono.ttf") }
            add("Roboto-$weight.ttf"); add("RobotoStatic-$weight.ttf"); add("Roboto-Regular.ttf")
            add("NotoSans-$weight.ttf"); add("NotoSans-Regular.ttf"); add("DroidSans.ttf")
        }
        for (n in names.distinct()) {
            val f = File("/system/fonts/$n")
            if (!f.exists()) continue
            val font = runCatching { PDType0Font.load(doc, f) }.getOrNull() ?: continue
            if (canShow(font, text)) return font
        }
        return null
    }

    private fun typefaceLike(font: PDFont?): Typeface {
        val t = traitsOf(font)
        val family = when { t.mono -> Typeface.MONOSPACE; t.serif -> Typeface.SERIF; else -> Typeface.SANS_SERIF }
        val style = when { t.bold && t.italic -> Typeface.BOLD_ITALIC; t.bold -> Typeface.BOLD; t.italic -> Typeface.ITALIC; else -> Typeface.NORMAL }
        return Typeface.create(family, style)
    }
}

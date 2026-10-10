package com.kyant.pdfcore.text

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
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

/**
 * Replace the text inside a box with [newText]. Boxes are normalized (0..1) in the same display
 * space [PdfTextService] reports blocks in: top-left origin, page rotation applied.
 *
 * The edit box ([left]..[bottom]) holds the glyphs being replaced: a selection inside a line, or the
 * whole line. The line box ([lineLeft]..[lineBottom]) is the full line. When the edit box is only
 * part of it, the glyphs after the selection are moved by exactly the change in width, so a longer
 * or shorter replacement neither overlaps nor leaves a gap. [backgroundArgb] paints behind the new
 * text only when the original glyphs cannot be removed.
 */
data class PdfTextEdit(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val newText: String,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
    val lineLeft: Float = left,
    val lineTop: Float = top,
    val lineRight: Float = right,
    val lineBottom: Float = bottom
)

/**
 * In-place editing of a PDF's existing text: a real edit, not a sticker over the page.
 *
 * For each edit:
 * 1. The page's content stream is replayed with operator bookkeeping, so every glyph is tied to
 *    the exact text-showing operator (`Tj`, `TJ`, `'`, `"`) and the exact bytes that drew it.
 * 2. The glyphs inside the edit box are **removed from the content stream**. Each removed glyph
 *    becomes a `TJ` spacing adjustment of exactly its own advance, so every other glyph in the run
 *    stays put. The old text is gone (not hidden): it no longer renders, selects, searches or
 *    extracts.
 * 3. The new text is drawn at the first removed glyph's text rendering matrix (size, position,
 *    rotation, horizontal scale, character and word spacing, colour), **character by character in
 *    the original font wherever possible**:
 *    - a character the document already draws in that font reuses those exact bytes, so even an
 *      embedded subset (which PDF libraries can't encode new text into) draws its own glyph;
 *    - otherwise the font's own encoding, if it has the glyph;
 *    - otherwise the closest face (same serif / sans / mono, weight and slant) for that character.
 *    Scripts no PDF font here can draw are laid out by Android and placed as an image.
 * 4. On a partial edit, the rest of the line is re-drawn from its original bytes and fonts, shifted
 *    by the change in width, so it looks exactly as before.
 *
 * Text it cannot surgically remove (inside form XObjects, Type 3 or vertical fonts, or the
 * invisible OCR layer of a scanned page) falls back to painting [PdfTextEdit.backgroundArgb]
 * over the old text before drawing the new text.
 */
object PdfTextEditor {

    /** How many other pages may be scanned for a character the edited page never drew. */
    private const val HarvestPageLimit = 80

    fun applyEdits(doc: PDDocument, pageIndex: Int, edits: List<PdfTextEdit>) {
        if (edits.isEmpty() || pageIndex !in 0 until doc.numberOfPages) return
        val page = doc.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox ?: return
        val rotation = ((page.rotation % 360) + 360) % 360
        val (dispW, dispH) = if (rotation == 90 || rotation == 270) box.height to box.width else box.width to box.height
        if (dispW <= 0f || dispH <= 0f) return
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY

        val recorder = GlyphRecorder(collectGlyphs = true).apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        runCatching { recorder.getText(doc) }

        val tokens = runCatching { parseTokens(page) }.getOrNull()
        val operatorIndex = tokens?.indices?.filter { tokens[it] is Operator }.orEmpty()

        fun inBox(g: Glyph, l: Float, t: Float, r: Float, b: Float): Boolean {
            val cx = (g.tp.x + g.tp.width / 2f) / dispW
            val by = g.tp.y / dispH
            val slack = (b - t) * 0.25f
            return cx >= l && cx <= r && by >= t - slack && by <= b + slack
        }
        fun removable(g: Glyph): Boolean = tokens != null && g.depth == 0 &&
            g.renderMode != RenderingMode.NEITHER && g.font !is PDType3Font && !g.font.isVertical &&
            g.tfs != 0f && g.bytes.isNotEmpty() &&
            operatorIndex.getOrNull(g.op)?.let { (tokens[it] as Operator).name in TextShowOps } == true

        // Per edit: the glyphs it replaces, the rest of its line that has to move, and whether
        // both can be removed surgically.
        class Plan(val edit: PdfTextEdit, val hit: List<Glyph>, val suffix: List<Glyph>, val removable: Boolean, val partial: Boolean)
        val plans = edits.map { e ->
            val hit = recorder.glyphs.filter { inBox(it, e.left, e.top, e.right, e.bottom) }.sortedBy { it.tp.x }
            val partial = e.lineLeft < e.left - 1e-4f || e.lineRight > e.right + 1e-4f
            val last = hit.lastOrNull()
            val hitSet = hit.toHashSet()
            val lineH = (e.lineBottom - e.lineTop) * dispH
            val suffix = if (!partial || last == null) emptyList() else recorder.glyphs.filter { g ->
                g !in hitSet && g.tp.x > last.tp.x &&
                    inBox(g, e.lineLeft, e.lineTop, e.lineRight, e.lineBottom) &&
                    abs(g.tp.y - last.tp.y) <= lineH * 0.5f
            }.sortedBy { it.tp.x }
            val hitOk = hit.isNotEmpty() && hit.all(::removable)
            Plan(e, hit, if (hitOk && suffix.all(::removable)) suffix else emptyList(), hitOk, partial)
        }

        // 1) Remove the old glyphs from the content stream (all edits on the page in one rewrite).
        val toRemove = plans.filter { it.removable }.flatMap { it.hit + it.suffix }
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

        // 2) Draw the replacements (and the moved rest of each line) in a fresh, state-isolated stream.
        val fonts = FontSource(doc, recorder.codes, pageIndex)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (plan in plans) {
                val e = plan.edit
                val removed = plan.removable && removedOk
                if (!removed) coverOldText(cs, e, llx, lly, dispW, dispH, rotation)
                val text = e.newText.replace('\n', ' ').let { if (plan.partial) it else it.trimEnd() }
                val first = plan.hit.firstOrNull() ?: run {
                    if (text.isNotBlank()) drawUnanchored(doc, cs, e, text, fonts, llx, lly, dispW, dispH, rotation)
                    continue
                }
                val advance = drawLike(doc, cs, first, text, fonts, llx, lly)
                if (removed && plan.suffix.isNotEmpty()) {
                    val t0 = trmOf(first, llx, lly)
                    val lastHit = plan.hit.last()
                    val tl = trmOf(lastHit, llx, lly)
                    val aL = advanceOf(lastHit)
                    val dx = (t0.translateX + advance * t0.getValue(0, 0)) - (tl.translateX + aL * tl.getValue(0, 0))
                    val dy = (t0.translateY + advance * t0.getValue(0, 1)) - (tl.translateY + aL * tl.getValue(0, 1))
                    redrawShifted(cs, plan.suffix, dx, dy, llx, lly)
                }
            }
        }
    }

    // ── Glyph bookkeeping ─────────────────────────────────────────────────────────────────

    private class Glyph(
        val op: Int,          // ordinal of the text-showing operator in the page's operator list
        val str: Int,         // which string operand of that operator (TJ arrays hold several)
        val idx: Int,         // code index inside that string (counts every code, drawn or not)
        val depth: Int,       // form XObject nesting (0 = page content)
        val tp: TextPosition,
        val font: PDFont,
        val code: Int,
        val bytes: ByteArray, // the exact bytes that drew this glyph
        val tfs: Float, val tc: Float, val tw: Float, val th: Float,
        val rgb: Int,
        val renderMode: RenderingMode
    )

    /**
     * Replays a page (or pages) through the text extractor, recording each glyph and, per font,
     * the bytes that drew each character, so new text can reuse them.
     */
    private class GlyphRecorder(private val collectGlyphs: Boolean) : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()
        val codes = CodeBook()
        private var depth = 0
        private var op = -1
        private var str = -1
        private var codeIdx = -1
        private var slices: List<ByteArray> = emptyList()
        private var curBytes = ByteArray(0)
        private var curCode = 0

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth == 0) { op++; str = -1 }
            super.processOperator(operator, operands)
        }

        override fun showForm(form: PDFormXObject) {
            depth++
            try { super.showForm(form) } finally { depth-- }
        }

        override fun showText(string: ByteArray) {
            if (depth == 0) str++
            codeIdx = -1
            slices = graphicsState.textState.font?.let { splitCodes(it, string) }.orEmpty()
            super.showText(string)
        }

        override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String?, displacement: Vector) {
            codeIdx++
            curCode = code
            curBytes = slices.getOrNull(codeIdx) ?: ByteArray(0)
            if (!unicode.isNullOrEmpty() && curBytes.isNotEmpty()) codes.put(font, unicode, curBytes)
            super.showGlyph(textRenderingMatrix, font, code, unicode, displacement)
        }

        override fun processTextPosition(text: TextPosition) {
            if (!collectGlyphs) return
            val gs = graphicsState
            val ts = gs.textState
            val font = text.font ?: ts.font ?: return
            glyphs += Glyph(
                op = op, str = str, idx = codeIdx, depth = depth, tp = text, font = font,
                code = curCode, bytes = curBytes,
                tfs = ts.fontSize, tc = ts.characterSpacing, tw = ts.wordSpacing,
                th = ts.horizontalScaling / 100f,
                rgb = runCatching { gs.nonStrokingColor.toRGB() }.getOrDefault(0),
                renderMode = ts.renderingMode
            )
        }
    }

    /** Character → drawing bytes, per font program. */
    private class CodeBook {
        private val byFont = HashMap<COSBase, HashMap<String, ByteArray>>()
        // An embedded subset is identified by its tag ("ABCDEF+Arial"), so the same subset loaded
        // as separate objects on different pages still shares its codes.
        private val bySubsetName = HashMap<String, HashMap<String, ByteArray>>()

        fun put(font: PDFont, unicode: String, bytes: ByteArray) {
            byFont.getOrPut(font.cosObject) { HashMap() }.putIfAbsent(unicode, bytes)
            subsetName(font)?.let { bySubsetName.getOrPut(it) { HashMap() }.putIfAbsent(unicode, bytes) }
        }

        fun get(font: PDFont, unicode: String): ByteArray? =
            byFont[font.cosObject]?.get(unicode) ?: subsetName(font)?.let { bySubsetName[it]?.get(unicode) }

        fun addAll(other: CodeBook) {
            other.byFont.forEach { (k, v) -> val m = byFont.getOrPut(k) { HashMap() }; v.forEach { (u, b) -> m.putIfAbsent(u, b) } }
            other.bySubsetName.forEach { (k, v) -> val m = bySubsetName.getOrPut(k) { HashMap() }; v.forEach { (u, b) -> m.putIfAbsent(u, b) } }
        }

        private fun subsetName(font: PDFont): String? =
            font.name?.takeIf { it.length > 7 && it[6] == '+' }
    }

    private fun splitCodes(font: PDFont, string: ByteArray): List<ByteArray> = runCatching {
        val input = ByteArrayInputStream(string)
        val out = ArrayList<ByteArray>()
        while (input.available() > 0) {
            val start = string.size - input.available()
            font.readCode(input)
            val end = string.size - input.available()
            if (end <= start) break
            out += string.copyOfRange(start, end)
        }
        out
    }.getOrDefault(emptyList())

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

    // ── Drawing ──────────────────────────────────────────────────────────────────────────

    /** TextPosition's matrix is the text rendering matrix shifted by the crop box origin; undo it. */
    private fun trmOf(g: Glyph, llx: Float, lly: Float): Matrix {
        val m = g.tp.textMatrix
        return Matrix(m.getValue(0, 0), m.getValue(0, 1), m.getValue(1, 0), m.getValue(1, 1), m.translateX + llx, m.translateY + lly)
    }

    /** A glyph's advance in units of its own text rendering matrix (font size and scale included). */
    private fun advanceOf(g: Glyph): Float {
        val w0 = runCatching { g.font.getWidth(g.code) }.getOrDefault(0f) / 1000f
        val space = if (g.bytes.size == 1 && g.code == 32) g.tw else 0f
        return w0 + (g.tc + space) / g.tfs
    }

    private sealed class Piece(val font: PDFont, val advance: Float) {
        class Raw(font: PDFont, val bytes: ByteArray, advance: Float) : Piece(font, advance)
        class Text(font: PDFont, val text: String, advance: Float) : Piece(font, advance)
        class Gap(font: PDFont, val units: Float) : Piece(font, units / 1000f)
    }

    /**
     * Draws [text] where and how [first] was drawn; returns its advance in [first]'s text rendering
     * matrix units, so the rest of the line can follow it.
     */
    private fun drawLike(doc: PDDocument, cs: PDPageContentStream, first: Glyph, text: String, fonts: FontSource, llx: Float, lly: Float): Float {
        if (text.isEmpty()) return 0f
        val trm = trmOf(first, llx, lly)
        val tcU = first.tc / first.tfs
        val twU = first.tw / first.tfs
        val pieces = encode(first.font, text, fonts, tcU, twU)
        if (pieces == null) {
            val t = traitsOf(first.font)
            return PdfTextImageFallback.draw(doc, cs, text, trm, first.rgb, t.bold, t.italic, t.serif, t.mono)
        }
        val rgb = first.rgb
        cs.beginText()
        cs.setNonStrokingColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        // Size 1 at the full rendering matrix reproduces size, scale, skew and rotation exactly;
        // spacing is scaled into the same units.
        cs.setFont(pieces.first().font, 1f)
        cs.setCharacterSpacing(tcU)
        cs.setWordSpacing(twU)
        cs.setTextMatrix(trm)
        var font = pieces.first().font
        var advance = 0f
        for (p in pieces) {
            if (p.font !== font && p !is Piece.Gap) { cs.setFont(p.font, 1f); font = p.font }
            when (p) {
                is Piece.Raw -> cs.appendRawCommands(hexShow(p.bytes))
                is Piece.Text -> cs.showText(p.text)
                is Piece.Gap -> cs.appendRawCommands("[" + num(-p.units) + "] TJ\n")
            }
            advance += p.advance
        }
        cs.endText()
        return advance
    }

    /**
     * Splits [text] into runs: the original font's own bytes first, then its encoding, then the
     * closest face, per character. Null when some character can't be drawn by any PDF font here.
     */
    private fun encode(font: PDFont, text: String, fonts: FontSource, tcU: Float, twU: Float): List<Piece>? {
        val out = ArrayList<Piece>()
        fun add(p: Piece) {
            val last = out.lastOrNull()
            out += when {
                last is Piece.Raw && p is Piece.Raw && last.font === p.font -> { out.removeAt(out.lastIndex); Piece.Raw(p.font, last.bytes + p.bytes, last.advance + p.advance) }
                last is Piece.Text && p is Piece.Text && last.font === p.font -> { out.removeAt(out.lastIndex); Piece.Text(p.font, last.text + p.text, last.advance + p.advance) }
                else -> p
            }
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            val raw = fonts.bytesFor(font, ch)
            if (raw != null) {
                val code = codeOf(font, raw)
                val w = runCatching { font.getWidth(code) }.getOrDefault(0f) / 1000f
                add(Piece.Raw(font, raw, w + tcU + if (raw.size == 1 && code == 32) twU else 0f))
                continue
            }
            if (canShow(font, ch)) { add(Piece.Text(font, ch, textAdvance(font, ch, tcU, twU))); continue }
            if (cp == ' '.code) {
                val sw = runCatching { font.spaceWidth }.getOrDefault(0f).takeIf { it > 0f } ?: 250f
                add(Piece.Gap(font, sw + tcU * 1000f))
                continue
            }
            val fb = fonts.fallback(font, ch) ?: return null
            add(Piece.Text(fb, ch, textAdvance(fb, ch, tcU, twU)))
        }
        return out
    }

    private fun textAdvance(font: PDFont, ch: String, tcU: Float, twU: Float): Float = runCatching {
        val bytes = font.encode(ch)
        val code = codeOf(font, bytes)
        font.getWidth(code) / 1000f + tcU + if (bytes.size == 1 && code == 32) twU else 0f
    }.getOrDefault(0f)

    private fun codeOf(font: PDFont, bytes: ByteArray): Int =
        runCatching { font.readCode(ByteArrayInputStream(bytes)) }.getOrElse {
            var c = 0
            for (b in bytes) c = (c shl 8) or (b.toInt() and 0xFF)
            c
        }

    /** The rest of an edited line, re-drawn from its original bytes and fonts, moved by (dx, dy). */
    private fun redrawShifted(cs: PDPageContentStream, glyphs: List<Glyph>, dx: Float, dy: Float, llx: Float, lly: Float) {
        cs.beginText()
        var font: PDFont? = null
        var rgb = Int.MIN_VALUE
        for (g in glyphs) {
            if (g.rgb != rgb) { cs.setNonStrokingColor((g.rgb shr 16) and 0xFF, (g.rgb shr 8) and 0xFF, g.rgb and 0xFF); rgb = g.rgb }
            if (g.font !== font) { cs.setFont(g.font, 1f); font = g.font }
            val t = trmOf(g, llx, lly)
            cs.setTextMatrix(Matrix(t.getValue(0, 0), t.getValue(0, 1), t.getValue(1, 0), t.getValue(1, 1), t.translateX + dx, t.translateY + dy))
            cs.appendRawCommands(hexShow(g.bytes))
        }
        cs.endText()
    }

    private fun hexShow(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2 + 6).append('<')
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v shr 4]).append(HEX[v and 0xF])
        }
        return sb.append("> Tj\n").toString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun num(v: Float): String = String.format(java.util.Locale.ROOT, "%.3f", v)

    /** No glyph to copy (e.g. an empty OCR box): draw in a matching face sized to the box. */
    private fun drawUnanchored(
        doc: PDDocument, cs: PDPageContentStream, e: PdfTextEdit, text: String, fonts: FontSource,
        llx: Float, lly: Float, dispW: Float, dispH: Float, rotation: Int
    ) {
        if (rotation != 0) return
        val size = (e.bottom - e.top) * dispH * 0.78f
        if (size <= 0f) return
        val trm = Matrix(size, 0f, 0f, size, llx + e.left * dispW, lly + (1f - e.bottom) * dispH + size * 0.22f)
        val font = fonts.fallbackFor(null, text)
        if (font != null) {
            cs.beginText(); cs.setNonStrokingColor(0, 0, 0); cs.setFont(font, 1f); cs.setTextMatrix(trm); cs.showText(text); cs.endText()
        } else PdfTextImageFallback.draw(doc, cs, text, trm, 0, false, false, false, false)
    }

    private fun coverOldText(
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

    // ── Fonts ─────────────────────────────────────────────────────────────────────────────

    /** Codes the document already uses, and fallback faces, for one document's edits. */
    private class FontSource(private val doc: PDDocument, private val codes: CodeBook, private val pageIndex: Int) {
        private var harvested = false
        private val loaded = HashMap<String, PDFont?>()

        fun bytesFor(font: PDFont, ch: String): ByteArray? {
            codes.get(font, ch)?.let { return it }
            // Many PDFs never draw a space (they position words instead): not worth a scan.
            if (!harvested && ch.isNotBlank()) {
                harvested = true
                // Not drawn on this page: look through the rest of the document once.
                val n = doc.numberOfPages
                if (n > 1) {
                    val r = GlyphRecorder(collectGlyphs = false).apply {
                        startPage = 1
                        endPage = minOf(n, HarvestPageLimit)
                    }
                    runCatching { r.getText(doc) }
                    codes.addAll(r.codes)
                }
            }
            return codes.get(font, ch)
        }

        /** The closest face to [like] that can draw [ch]. */
        fun fallback(like: PDFont, ch: String): PDFont? = fallbackFor(like, ch)

        fun fallbackFor(like: PDFont?, text: String): PDFont? {
            val t = traitsOf(like)
            val std = when {
                t.mono -> when { t.bold && t.italic -> PDType1Font.COURIER_BOLD_OBLIQUE; t.bold -> PDType1Font.COURIER_BOLD; t.italic -> PDType1Font.COURIER_OBLIQUE; else -> PDType1Font.COURIER }
                t.serif -> when { t.bold && t.italic -> PDType1Font.TIMES_BOLD_ITALIC; t.bold -> PDType1Font.TIMES_BOLD; t.italic -> PDType1Font.TIMES_ITALIC; else -> PDType1Font.TIMES_ROMAN }
                else -> when { t.bold && t.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE; t.bold -> PDType1Font.HELVETICA_BOLD; t.italic -> PDType1Font.HELVETICA_OBLIQUE; else -> PDType1Font.HELVETICA }
            }
            if (canShow(std, text)) return std
            // Beyond Latin-1: embed (subset) a system TrueType face of the same style.
            val weight = when { t.bold && t.italic -> "BoldItalic"; t.bold -> "Bold"; t.italic -> "Italic"; else -> "Regular" }
            val names = buildList {
                if (t.serif) { add("NotoSerif-$weight.ttf"); add("NotoSerif-Regular.ttf") }
                if (t.mono) { add("DroidSansMono.ttf"); add("CutiveMono.ttf") }
                add("Roboto-$weight.ttf"); add("RobotoStatic-$weight.ttf"); add("Roboto-Regular.ttf")
                add("NotoSans-$weight.ttf"); add("NotoSans-Regular.ttf"); add("DroidSans.ttf")
            }
            for (n in names.distinct()) {
                val font = loaded.getOrPut(n) {
                    val f = File("/system/fonts/$n")
                    if (f.exists()) runCatching { PDType0Font.load(doc, f) }.getOrNull() else null
                } ?: continue
                if (canShow(font, text)) return font
            }
            return null
        }
    }

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
        val italic = name.contains("italic") || name.contains("oblique") || d?.isItalic == true || (d?.italicAngle ?: 0f) != 0f
        val mono = listOf("courier", "mono", "consol", "menlo").any { name.contains(it) } || d?.isFixedPitch == true
        val serif = !mono && (d?.isSerif == true ||
            listOf("times", "serif", "georgia", "garamond", "cambria", "book", "minion", "palatino", "baskerville").any { name.contains(it) } &&
            !name.contains("sans"))
        return Traits(bold, italic, serif, mono)
    }
}

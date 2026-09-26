package com.chethan616.clearpdf.utils.xlsx

import com.chethan616.clearpdf.utils.ExcelCellFormat

/**
 * A tiny lossless element tree for the pieces of `styles.xml` we derive new entries from. Keeping
 * every attribute and child (not just the ones we understand) means a font we clone to make bold
 * still carries its charset, family, scheme and anything else the author's Excel put there.
 * Names are stored without their namespace prefix.
 */
class XmlEl(
    val name: String,
    val attrs: LinkedHashMap<String, String> = LinkedHashMap(),
    val children: MutableList<XmlEl> = mutableListOf(),
    var text: String? = null
) {
    fun child(n: String): XmlEl? = children.firstOrNull { it.name == n }
    fun copy(): XmlEl = XmlEl(name, LinkedHashMap(attrs), children.mapTo(mutableListOf()) { it.copy() }, text)

    fun setChild(el: XmlEl) {
        val i = children.indexOfFirst { it.name == el.name }
        if (i >= 0) children[i] = el else children.add(el)
    }

    fun removeChild(n: String) { children.removeAll { it.name == n } }

    fun toXml(prefix: String, sb: StringBuilder = StringBuilder()): StringBuilder {
        sb.append('<').append(prefix).append(name)
        for ((k, v) in attrs) sb.append(' ').append(k).append("=\"").append(XlsxRefs.escapeXml(v)).append('"')
        if (children.isEmpty() && text == null) return sb.append("/>")
        sb.append('>')
        text?.let { sb.append(XlsxRefs.escapeXml(it)) }
        for (c in children) c.toXml(prefix, sb)
        return sb.append("</").append(prefix).append(name).append('>')
    }
}

/** One border edge as the renderer needs it. `weight` 0 = none. */
data class EdgeStyle(val weight: Float, val dashed: Boolean, val double: Boolean, val argb: Int?)

/** Everything the renderer needs from one `cellXfs` entry, with colours already resolved. */
class ResolvedStyle(
    val fillArgb: Int?,
    val fontArgb: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val doubleUnderline: Boolean,
    val strike: Boolean,
    val sizePt: Float,
    val fontName: String?,
    val left: EdgeStyle?,
    val right: EdgeStyle?,
    val top: EdgeStyle?,
    val bottom: EdgeStyle?,
    /** general, left, center, right, fill, justify, centerContinuous, distributed */
    val hAlign: String,
    /** top, center, bottom (default), justify, distributed */
    val vAlign: String,
    val wrap: Boolean,
    val indent: Int,
    val rotation: Int,
    val numFmtId: Int,
    val numFmtCode: String,
    val superscript: Boolean = false,
    val subscript: Boolean = false
) {
    companion object {
        val Default = ResolvedStyle(
            null, null, false, false, false, false, false, 11f, null,
            null, null, null, null, "general", "bottom", false, 0, 0, 0, "General"
        )
    }
}

/** A formatting change requested from the toolbar. Null fields mean "leave as is". */
data class StylePatch(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val fontSize: Double? = null,
    val fontColor: ColorSpec? = null,
    /** A solid fill colour, or [clearFill] to remove any fill. */
    val fill: ColorSpec? = null,
    val clearFill: Boolean = false,
    val hAlign: String? = null,
    val vAlign: String? = null,
    val wrap: Boolean? = null,
    val numFmtCode: String? = null,
    /** Edge updates: null = untouched; a side with `style == null` clears it. */
    val left: BorderSide? = null,
    val right: BorderSide? = null,
    val top: BorderSide? = null,
    val bottom: BorderSide? = null
) {
    val touchesFont get() = bold != null || italic != null || underline != null || strike != null || fontSize != null || fontColor != null
    val touchesFill get() = fill != null || clearFill
    val touchesBorder get() = left != null || right != null || top != null || bottom != null
    val touchesAlignment get() = hAlign != null || vAlign != null || wrap != null
}

/** `style` is a SpreadsheetML border style name ("thin", "medium", "dashed", "double" …) or null for none. */
data class BorderSide(val style: String?, val color: ColorSpec? = null)

/**
 * The workbook's style sheet: the original entries (by index, exactly as read) plus any the user's
 * edits appended. The writer emits only the appended tail, so the original indices never move and
 * every untouched cell keeps pointing at the same `xf` it always did.
 */
class StyleTable(
    val fonts: MutableList<XmlEl>,
    val fills: MutableList<XmlEl>,
    val borders: MutableList<XmlEl>,
    val cellXfs: MutableList<XmlEl>,
    val numFmts: MutableMap<Int, String>,
    val themeColors: IntArray,
    val indexedColors: IntArray?,
    /** `cellStyleXfs` count, only so a derived `xf` can keep a valid `xfId`. */
    val cellStyleXfCount: Int,
    val hasStylesPart: Boolean
) {
    val origFonts = fonts.size
    val origFills = fills.size
    val origBorders = borders.size
    val origXfs = cellXfs.size
    val origNumFmtIds: Set<Int> = numFmts.keys.toSet()

    private var resolved = arrayOfNulls<ResolvedStyle>(cellXfs.size)
    private val derived = HashMap<Pair<Int, StylePatch>, Int>()

    val xfCount get() = cellXfs.size

    fun resolve(xf: Int): ResolvedStyle {
        if (xf < 0 || xf >= cellXfs.size) return if (cellXfs.isNotEmpty() && xf != 0) resolve(0) else ResolvedStyle.Default
        if (resolved.size < cellXfs.size) resolved = resolved.copyOf(cellXfs.size)
        resolved[xf]?.let { return it }
        return buildResolved(cellXfs[xf]).also { resolved[xf] = it }
    }

    fun numFmtCode(id: Int): String = ExcelCellFormat.codeFor(id, numFmts)

    fun color(el: XmlEl?): Int? = el?.let { ColorSpec.fromAttrs(it.attrs)?.resolve(themeColors, indexedColors) }

    private fun buildResolved(xf: XmlEl): ResolvedStyle {
        val font = fonts.getOrNull(xf.attrs["fontId"]?.toIntOrNull() ?: 0)
        val fill = fills.getOrNull(xf.attrs["fillId"]?.toIntOrNull() ?: 0)
        val border = borders.getOrNull(xf.attrs["borderId"]?.toIntOrNull() ?: 0)
        val align = xf.child("alignment")
        val numFmtId = xf.attrs["numFmtId"]?.toIntOrNull() ?: 0
        val f = FontFacts.of(font, this)
        return ResolvedStyle(
            fillArgb = fillColor(fill),
            fontArgb = f.argb,
            bold = f.bold, italic = f.italic, underline = f.underline, doubleUnderline = f.doubleUnderline,
            strike = f.strike, sizePt = f.size, fontName = f.name,
            left = edge(border?.child("left") ?: border?.child("start")),
            right = edge(border?.child("right") ?: border?.child("end")),
            top = edge(border?.child("top")),
            bottom = edge(border?.child("bottom")),
            hAlign = align?.attrs?.get("horizontal") ?: "general",
            vAlign = align?.attrs?.get("vertical") ?: "bottom",
            wrap = align?.attrs?.get("wrapText").isTrue(),
            indent = align?.attrs?.get("indent")?.toIntOrNull() ?: 0,
            rotation = align?.attrs?.get("textRotation")?.toIntOrNull() ?: 0,
            numFmtId = numFmtId,
            numFmtCode = numFmtCode(numFmtId),
            superscript = f.vertAlign == "superscript",
            subscript = f.vertAlign == "subscript"
        )
    }

    private fun fillColor(fill: XmlEl?): Int? {
        fill ?: return null
        fill.child("patternFill")?.let { p ->
            val type = p.attrs["patternType"] ?: if (p.child("fgColor") != null) "solid" else "none"
            if (type == "none") return null
            val fg = color(p.child("fgColor"))
            val bg = color(p.child("bgColor"))
            return when (type) {
                "solid" -> fg ?: bg
                "gray125", "gray0625" -> 0xFFEFEFEF.toInt()
                // Patterned fills are approximated by their foreground, softened — a hatch at cell
                // scale on a phone reads as a flat tone anyway.
                else -> fg?.let { blend(it, bg ?: 0xFFFFFFFF.toInt(), 0.5f) } ?: bg
            }
        }
        fill.child("gradientFill")?.let { g ->
            return color(g.children.firstOrNull { it.name == "stop" }?.child("color"))
        }
        return null
    }

    private fun edge(side: XmlEl?): EdgeStyle? {
        val style = side?.attrs?.get("style") ?: return null
        if (style == "none") return null
        val w = when (style) {
            "medium", "mediumDashed", "mediumDashDot", "mediumDashDotDot", "slantDashDot" -> 2f
            "thick" -> 3f
            "double" -> 3f
            "hair" -> 0.5f
            else -> 1f
        }
        return EdgeStyle(w, style.contains("ash") || style.contains("otted") || style == "hair", style == "double", color(side.child("color")))
    }

    // ── Deriving new styles ────────────────────────────────────────────────────

    /** The `cellXfs` index for [base] with [patch] applied — appended once, then reused. */
    fun derive(base: Int, patch: StylePatch): Int {
        val b = if (base in 0 until cellXfs.size) base else 0
        derived[b to patch]?.let { return it }
        if (cellXfs.isEmpty()) {
            // A styles part with no cellXfs (or none at all): seed Excel's defaults.
            if (fonts.isEmpty()) fonts.add(defaultFont())
            if (fills.isEmpty()) { fills.add(patternFill("none")); fills.add(patternFill("gray125")) }
            if (borders.isEmpty()) borders.add(emptyBorder())
            cellXfs.add(XmlEl("xf", linkedMapOf("numFmtId" to "0", "fontId" to "0", "fillId" to "0", "borderId" to "0", "xfId" to "0")))
        }
        val xf = cellXfs[b].copy()
        if (patch.touchesFont) {
            val font = (fonts.getOrNull(xf.attrs["fontId"]?.toIntOrNull() ?: 0) ?: defaultFont()).copy()
            patch.bold?.let { setFlag(font, "b", it) }
            patch.italic?.let { setFlag(font, "i", it) }
            patch.strike?.let { setFlag(font, "strike", it) }
            patch.underline?.let { if (it) font.setChild(XmlEl("u")) else font.removeChild("u") }
            patch.fontSize?.let { font.setChild(XmlEl("sz", linkedMapOf("val" to trimNum(it)))) }
            patch.fontColor?.let { font.setChild(colorEl("color", it)) }
            fonts.add(font)
            xf.attrs["fontId"] = (fonts.size - 1).toString()
            xf.attrs["applyFont"] = "1"
        }
        if (patch.touchesFill) {
            val fill = if (patch.clearFill || patch.fill == null) patternFill("none") else XmlEl(
                "fill", children = mutableListOf(
                    XmlEl("patternFill", linkedMapOf("patternType" to "solid"), mutableListOf(colorEl("fgColor", patch.fill), XmlEl("bgColor", linkedMapOf("indexed" to "64"))))
                )
            )
            fills.add(fill)
            xf.attrs["fillId"] = (fills.size - 1).toString()
            xf.attrs["applyFill"] = "1"
        }
        if (patch.touchesBorder) {
            val src = borders.getOrNull(xf.attrs["borderId"]?.toIntOrNull() ?: 0) ?: emptyBorder()
            val border = XmlEl("border", LinkedHashMap(src.attrs))
            // CT_Border is an ordered sequence: left, right, top, bottom, diagonal, vertical, horizontal.
            fun sideEl(name: String, alt: String?, p: BorderSide?): XmlEl {
                if (p == null) return (src.child(name) ?: alt?.let { src.child(it) }?.let { XmlEl(name, it.attrs, it.children) } ?: XmlEl(name)).copy()
                if (p.style == null) return XmlEl(name)
                return XmlEl(name, linkedMapOf("style" to p.style), mutableListOf(colorEl("color", p.color ?: ColorSpec(indexed = 64))))
            }
            border.children.add(sideEl("left", "start", patch.left))
            border.children.add(sideEl("right", "end", patch.right))
            border.children.add(sideEl("top", null, patch.top))
            border.children.add(sideEl("bottom", null, patch.bottom))
            border.children.add(src.child("diagonal")?.copy() ?: XmlEl("diagonal"))
            src.child("vertical")?.let { border.children.add(it.copy()) }
            src.child("horizontal")?.let { border.children.add(it.copy()) }
            borders.add(border)
            xf.attrs["borderId"] = (borders.size - 1).toString()
            xf.attrs["applyBorder"] = "1"
        }
        if (patch.touchesAlignment) {
            val al = xf.child("alignment")?.copy() ?: XmlEl("alignment")
            patch.hAlign?.let { if (it == "general") al.attrs.remove("horizontal") else al.attrs["horizontal"] = it }
            patch.vAlign?.let { if (it == "bottom") al.attrs.remove("vertical") else al.attrs["vertical"] = it }
            patch.wrap?.let { if (it) al.attrs["wrapText"] = "1" else al.attrs.remove("wrapText") }
            // `alignment` must precede `protection` inside an xf.
            xf.removeChild("alignment")
            if (al.attrs.isNotEmpty()) xf.children.add(0, al)
            xf.attrs["applyAlignment"] = "1"
        }
        patch.numFmtCode?.let { code ->
            val id = ExcelCellFormat.builtInIdFor(code)
                ?: numFmts.entries.firstOrNull { it.value == code }?.key
                ?: ((numFmts.keys.maxOrNull() ?: 163).coerceAtLeast(163) + 1).also { numFmts[it] = code }
            xf.attrs["numFmtId"] = id.toString()
            xf.attrs["applyNumberFormat"] = "1"
        }
        if ((xf.attrs["xfId"]?.toIntOrNull() ?: 0) >= cellStyleXfCount.coerceAtLeast(1)) xf.attrs["xfId"] = "0"
        cellXfs.add(xf)
        val idx = cellXfs.size - 1
        derived[b to patch] = idx
        return idx
    }

    private fun setFlag(font: XmlEl, n: String, on: Boolean) {
        if (on) font.setChild(XmlEl(n)) else font.removeChild(n)
    }

    private fun colorEl(tag: String, c: ColorSpec): XmlEl {
        val a = LinkedHashMap<String, String>()
        when {
            c.auto -> a["auto"] = "1"
            c.rgb != null -> a["rgb"] = XlsxColors.hex8(c.rgb)
            c.theme >= 0 -> a["theme"] = c.theme.toString()
            c.indexed >= 0 -> a["indexed"] = c.indexed.toString()
        }
        if (c.tint != 0.0 && !c.auto) a["tint"] = c.tint.toString()
        return XmlEl(tag, a)
    }

    private fun defaultFont() = XmlEl(
        "font", children = mutableListOf(
            XmlEl("sz", linkedMapOf("val" to "11")), XmlEl("color", linkedMapOf("theme" to "1")),
            XmlEl("name", linkedMapOf("val" to "Calibri")), XmlEl("family", linkedMapOf("val" to "2")),
            XmlEl("scheme", linkedMapOf("val" to "minor"))
        )
    )

    private fun patternFill(type: String) = XmlEl("fill", children = mutableListOf(XmlEl("patternFill", linkedMapOf("patternType" to type))))
    private fun emptyBorder() = XmlEl("border", children = mutableListOf(XmlEl("left"), XmlEl("right"), XmlEl("top"), XmlEl("bottom"), XmlEl("diagonal")))

    companion object {
        fun empty(theme: IntArray = XlsxColors.DefaultTheme) =
            StyleTable(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), HashMap(), theme, null, 0, false)

        private fun trimNum(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

        fun blend(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, s: Int) = (x shr s) and 0xFF
            val r = (ch(a, 16) * t + ch(b, 16) * (1 - t)).toInt()
            val g = (ch(a, 8) * t + ch(b, 8) * (1 - t)).toInt()
            val bl = (ch(a, 0) * t + ch(b, 0) * (1 - t)).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
        }
    }
}

internal fun String?.isTrue() = this == "1" || this == "true"

/** Font properties read off a `<font>` (styles) or `<rPr>` (rich-text run) element. */
internal class FontFacts(
    val bold: Boolean, val italic: Boolean, val underline: Boolean, val doubleUnderline: Boolean,
    val strike: Boolean, val size: Float, val name: String?, val argb: Int?, val vertAlign: String?
) {
    companion object {
        fun of(el: XmlEl?, styles: StyleTable): FontFacts {
            if (el == null) return FontFacts(false, false, false, false, false, 11f, null, null, null)
            fun flag(n: String): Boolean {
                val c = el.child(n) ?: return false
                val v = c.attrs["val"] ?: return true
                return v.isTrue()
            }
            val u = el.child("u")
            val uVal = u?.attrs?.get("val") ?: if (u != null) "single" else "none"
            return FontFacts(
                bold = flag("b"), italic = flag("i"),
                underline = uVal != "none", doubleUnderline = uVal.startsWith("double"),
                strike = flag("strike"),
                size = el.child("sz")?.attrs?.get("val")?.toFloatOrNull() ?: 11f,
                name = (el.child("name") ?: el.child("rFont"))?.attrs?.get("val"),
                argb = styles.color(el.child("color")),
                vertAlign = el.child("vertAlign")?.attrs?.get("val")
            )
        }
    }
}

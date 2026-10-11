package com.chethan616.clearpdf.docx

/** Character properties of an `<w:rPr>`; null fields are unset (inherit). Sizes in half-points. */
data class RProps(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val size: Int? = null,
    /** ARGB, or 0 for "auto". */
    val color: Int? = null,
    /** ARGB of a highlight or run shading. */
    val highlight: Int? = null,
    val font: String? = null,
    val fontTheme: String? = null,
    val caps: Boolean? = null,
    val smallCaps: Boolean? = null,
    val vertAlign: String? = null,
    val vanish: Boolean? = null,
    val rStyle: String? = null
) {
    fun over(base: RProps): RProps = RProps(
        bold ?: base.bold, italic ?: base.italic, underline ?: base.underline, strike ?: base.strike,
        size ?: base.size, color ?: base.color, highlight ?: base.highlight,
        if (font != null || fontTheme != null) font else base.font,
        if (font != null || fontTheme != null) fontTheme else base.fontTheme,
        caps ?: base.caps, smallCaps ?: base.smallCaps, vertAlign ?: base.vertAlign, vanish ?: base.vanish,
        rStyle ?: base.rStyle
    )
}

/** Paragraph properties of a `<w:pPr>`; lengths in twips. */
data class PProps(
    val styleId: String? = null,
    val align: String? = null,
    val before: Int? = null,
    val after: Int? = null,
    val line: Int? = null,
    val lineRule: String? = null,
    val left: Int? = null,
    val right: Int? = null,
    val firstLine: Int? = null,
    val hanging: Int? = null,
    val numId: Int? = null,
    val ilvl: Int? = null,
    val bidi: Boolean? = null,
    val shading: Int? = null,
    val pageBreakBefore: Boolean? = null,
    val contextual: Boolean? = null,
    val borderBottom: Boolean? = null
) {
    fun over(base: PProps): PProps = PProps(
        styleId ?: base.styleId, align ?: base.align, before ?: base.before, after ?: base.after,
        line ?: base.line, lineRule ?: base.lineRule, left ?: base.left, right ?: base.right,
        if (firstLine != null || hanging != null) firstLine else base.firstLine,
        if (firstLine != null || hanging != null) hanging else base.hanging,
        numId ?: base.numId, ilvl ?: base.ilvl, bidi ?: base.bidi, shading ?: base.shading,
        pageBreakBefore ?: base.pageBreakBefore, contextual ?: base.contextual, borderBottom ?: base.borderBottom
    )
}

/** Word's named highlight colours. */
val HighlightColors: Map<String, Int> = linkedMapOf(
    "yellow" to 0xFFFFFF00.toInt(), "green" to 0xFF00FF00.toInt(), "cyan" to 0xFF00FFFF.toInt(),
    "magenta" to 0xFFFF00FF.toInt(), "blue" to 0xFF0000FF.toInt(), "red" to 0xFFFF0000.toInt(),
    "darkBlue" to 0xFF000080.toInt(), "darkCyan" to 0xFF008080.toInt(), "darkGreen" to 0xFF008000.toInt(),
    "darkMagenta" to 0xFF800080.toInt(), "darkRed" to 0xFF800000.toInt(), "darkYellow" to 0xFF808000.toInt(),
    "darkGray" to 0xFF808080.toInt(), "lightGray" to 0xFFC0C0C0.toInt(), "black" to 0xFF000000.toInt(),
    "white" to 0xFFFFFFFF.toInt()
)

internal object Props {

    private val rCache = HashMap<String, RProps>()
    private val pCache = HashMap<String, PProps>()

    fun hex(v: String?): Int? {
        if (v == null || v.equals("auto", true)) return null
        val n = v.toLongOrNull(16) ?: return null
        return (0xFF000000 or (n and 0xFFFFFF)).toInt()
    }

    private fun onOff(xml: String, n: XNode): Boolean {
        val v = n.attr(xml, "val") ?: return true
        return !(v == "0" || v.equals("false", true) || v.equals("off", true))
    }

    /** Parses an `<w:rPr>…</w:rPr>` (or the empty string). */
    fun r(rPr: String): RProps {
        if (rPr.isEmpty()) return RProps()
        synchronized(rCache) { rCache[rPr]?.let { return it } }
        val root = XmlScan.scan(rPr).children.firstOrNull() ?: return RProps()
        var p = RProps()
        for (c in root.children) {
            p = when (c.local) {
                "b" -> p.copy(bold = onOff(rPr, c))
                "i" -> p.copy(italic = onOff(rPr, c))
                "u" -> p.copy(underline = (c.attr(rPr, "val") ?: "single") != "none")
                "strike", "dstrike" -> if (onOff(rPr, c)) p.copy(strike = true) else p.copy(strike = p.strike ?: false)
                "sz" -> c.attr(rPr, "val")?.toFloatOrNull()?.let { p.copy(size = it.toInt()) } ?: p
                "color" -> {
                    val v = c.attr(rPr, "val")
                    p.copy(color = if (v == null || v.equals("auto", true)) 0 else hex(v) ?: 0)
                }
                "highlight" -> {
                    val v = c.attr(rPr, "val")
                    if (v == null || v == "none") p.copy(highlight = 0) else p.copy(highlight = HighlightColors[v] ?: p.highlight)
                }
                "shd" -> {
                    val fill = hex(c.attr(rPr, "fill"))
                    if (fill != null && p.highlight == null) p.copy(highlight = fill) else p
                }
                "rFonts" -> {
                    val f = c.attr(rPr, "ascii") ?: c.attr(rPr, "hAnsi") ?: c.attr(rPr, "cs")
                    val t = c.attr(rPr, "asciiTheme") ?: c.attr(rPr, "hAnsiTheme")
                    if (f != null || t != null) p.copy(font = f, fontTheme = if (f == null) t else null) else p
                }
                "caps" -> p.copy(caps = onOff(rPr, c))
                "smallCaps" -> p.copy(smallCaps = onOff(rPr, c))
                "vertAlign" -> p.copy(vertAlign = c.attr(rPr, "val"))
                "vanish" -> p.copy(vanish = onOff(rPr, c))
                "rStyle" -> p.copy(rStyle = c.attr(rPr, "val"))
                else -> p
            }
        }
        synchronized(rCache) { if (rCache.size > 4000) rCache.clear(); rCache[rPr] = p }
        return p
    }

    /** Parses an `<w:pPr>…</w:pPr>` (or the empty string). */
    fun p(pPr: String): PProps {
        if (pPr.isEmpty()) return PProps()
        synchronized(pCache) { pCache[pPr]?.let { return it } }
        val root = XmlScan.scan(pPr).children.firstOrNull() ?: return PProps()
        var p = PProps()
        fun tw(n: XNode, a: String): Int? = n.attr(pPr, a)?.toFloatOrNull()?.toInt()
        for (c in root.children) {
            p = when (c.local) {
                "pStyle" -> p.copy(styleId = c.attr(pPr, "val"))
                "jc" -> p.copy(align = c.attr(pPr, "val"))
                "spacing" -> p.copy(
                    before = tw(c, "before") ?: p.before,
                    after = tw(c, "after") ?: p.after,
                    line = tw(c, "line") ?: p.line,
                    lineRule = c.attr(pPr, "lineRule") ?: p.lineRule
                )
                "ind" -> p.copy(
                    left = tw(c, "left") ?: tw(c, "start") ?: p.left,
                    right = tw(c, "right") ?: tw(c, "end") ?: p.right,
                    firstLine = tw(c, "firstLine") ?: if (c.attr(pPr, "hanging") != null) 0 else p.firstLine,
                    hanging = tw(c, "hanging") ?: if (c.attr(pPr, "firstLine") != null) 0 else p.hanging
                )
                "numPr" -> p.copy(
                    numId = c.child("numId")?.attr(pPr, "val")?.toIntOrNull() ?: p.numId,
                    ilvl = c.child("ilvl")?.attr(pPr, "val")?.toIntOrNull() ?: p.ilvl
                )
                "bidi" -> p.copy(bidi = onOff(pPr, c))
                "shd" -> p.copy(shading = hex(c.attr(pPr, "fill")))
                "pageBreakBefore" -> p.copy(pageBreakBefore = onOff(pPr, c))
                "contextualSpacing" -> p.copy(contextual = onOff(pPr, c))
                "pBdr" -> p.copy(borderBottom = c.child("bottom")?.attr(pPr, "val")?.let { it != "none" && it != "nil" })
                else -> p
            }
        }
        synchronized(pCache) { if (pCache.size > 4000) pCache.clear(); pCache[pPr] = p }
        return p
    }

    // ── editing property elements ──────────────────────────────────────────────

    val RPR_ORDER = listOf(
        "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow",
        "emboss", "imprint", "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern",
        "position", "sz", "szCs", "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs",
        "em", "lang", "eastAsianLayout", "specVanish", "oMath"
    )

    val PPR_ORDER = listOf(
        "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr",
        "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap",
        "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi", "adjustRightInd", "snapToGrid",
        "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection",
        "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr", "pPrChange"
    )

    /**
     * Returns [props] (an `<w:rPr>` or `<w:pPr>`, possibly empty) with every child named [local]
     * removed and [element] (when non-null) inserted where the schema's sequence puts it. Word
     * rejects property elements out of order, so this never appends blindly.
     */
    fun setChild(props: String, wrapper: String, order: List<String>, local: String, element: String?): String {
        val root = if (props.isEmpty()) null else XmlScan.scan(props).children.firstOrNull()
        val children = ArrayList<Pair<String, String>>()
        if (root != null) for (c in root.children) if (c.local != local) children.add(c.local to c.text(props))
        if (element != null) {
            val idx = order.indexOf(local)
            var at = children.size
            for ((k, ch) in children.withIndex()) {
                val o = order.indexOf(ch.first)
                if (o < 0 || o > idx) { at = k; break }
            }
            children.add(at, local to element)
        }
        if (children.isEmpty()) return ""
        val open = root?.startTag(props)?.let { if (it.endsWith("/>")) it.dropLast(2) + ">" else it } ?: "<$wrapper>"
        val name = root?.name ?: wrapper
        return buildString {
            append(open)
            for (c in children) append(c.second)
            append("</").append(name).append('>')
        }
    }

    fun setR(rPr: String, local: String, element: String?) = setChild(rPr, "w:rPr", RPR_ORDER, local, element)
    fun setP(pPr: String, local: String, element: String?) = setChild(pPr, "w:pPr", PPR_ORDER, local, element)

    /** The raw XML of the child [local] of a props element, or null. */
    fun child(props: String, local: String): String? {
        if (props.isEmpty()) return null
        val root = XmlScan.scan(props).children.firstOrNull() ?: return null
        return root.child(local)?.text(props)
    }

    fun hex6(argb: Int): String = String.format("%06X", argb and 0xFFFFFF)
}

class StyleDef(
    val id: String,
    val name: String,
    val type: String,
    val basedOn: String?,
    val next: String?,
    val pPr: String,
    val rPr: String,
    val tblPr: String,
    val isDefault: Boolean,
    val hidden: Boolean,
    /** Table styles: conditional formats (firstRow, band1Horz…) → (tcPr, rPr). */
    val conditional: Map<String, Pair<String, String>> = emptyMap()
)

/** styles.xml: the style table and document defaults. Styles added while editing go in [added]. */
class DocxStyles(
    val defaultRPr: String,
    val defaultPPr: String,
    val byId: MutableMap<String, StyleDef>,
    val xml: String?
) {
    val added = ArrayList<String>()

    val defaultParagraphId: String? by lazy {
        byId.values.firstOrNull { it.type == "paragraph" && it.isDefault }?.id ?: byId["Normal"]?.id
    }

    fun byName(name: String): StyleDef? = byId.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The style and its `basedOn` ancestors, root first. */
    fun chain(id: String?): List<StyleDef> {
        val out = ArrayList<StyleDef>()
        var cur = id?.let { byId[it] }
        var guard = 0
        while (cur != null && guard++ < 16) {
            out.add(0, cur)
            cur = cur.basedOn?.let { byId[it] }
        }
        return out
    }

    private val pCache = HashMap<String?, Pair<PProps, RProps>>()

    /** Paragraph and character properties a paragraph style gives (defaults included). */
    fun paragraphStyle(id: String?): Pair<PProps, RProps> = synchronized(pCache) {
        pCache.getOrPut(id) {
            var pp = Props.p(defaultPPr)
            var rp = Props.r(defaultRPr)
            val chain = chain(id ?: defaultParagraphId).ifEmpty { chain(defaultParagraphId) }
            for (s in chain) {
                pp = Props.p(s.pPr).over(pp)
                rp = Props.r(s.rPr).over(rp)
            }
            pp to rp
        }
    }

    fun characterStyle(id: String?): RProps {
        if (id == null) return RProps()
        var rp = RProps()
        for (s in chain(id)) rp = Props.r(s.rPr).over(rp)
        return rp
    }

    fun invalidate() = synchronized(pCache) { pCache.clear() }

    companion object {
        fun parse(xml: String?): DocxStyles {
            if (xml == null) return DocxStyles("", "", HashMap(), null)
            val root = XmlScan.scan(xml).children.firstOrNull { it.local == "styles" }
                ?: return DocxStyles("", "", HashMap(), xml)
            val defaults = root.child("docDefaults")
            val dr = defaults?.child("rPrDefault")?.child("rPr")?.text(xml) ?: ""
            val dp = defaults?.child("pPrDefault")?.child("pPr")?.text(xml) ?: ""
            val map = LinkedHashMap<String, StyleDef>()
            for (s in root.childrenNamed("style")) {
                val id = s.attr(xml, "styleId") ?: continue
                map[id] = StyleDef(
                    id = id,
                    name = s.child("name")?.attr(xml, "val") ?: id,
                    type = s.attr(xml, "type") ?: "paragraph",
                    basedOn = s.child("basedOn")?.attr(xml, "val"),
                    next = s.child("next")?.attr(xml, "val"),
                    pPr = s.child("pPr")?.text(xml) ?: "",
                    rPr = s.child("rPr")?.text(xml) ?: "",
                    tblPr = s.child("tblPr")?.text(xml) ?: "",
                    isDefault = s.attr(xml, "default").let { it == "1" || it == "true" },
                    hidden = s.child("semiHidden") != null,
                    conditional = s.childrenNamed("tblStylePr").mapNotNull { c ->
                        val type = c.attr(xml, "type") ?: return@mapNotNull null
                        type to ((c.child("tcPr")?.text(xml) ?: "") to (c.child("rPr")?.text(xml) ?: ""))
                    }.toMap()
                )
            }
            return DocxStyles(dr, dp, map, xml)
        }
    }
}

class NumLevel(val start: Int, val fmt: String, val text: String, val pPr: String, val rPr: String)

/** numbering.xml: list definitions. Lists created while editing go in [addedAbstract] / [addedNums]. */
class DocxNumbering(
    val abstracts: MutableMap<Int, Map<Int, NumLevel>>,
    /** numId → abstractNumId. */
    val nums: MutableMap<Int, Int>,
    /** numId → (ilvl → start override). */
    val overrides: MutableMap<Int, Map<Int, Int>>,
    val xml: String?
) {
    val addedAbstract = ArrayList<String>()
    val addedNums = ArrayList<String>()

    fun level(numId: Int, ilvl: Int): NumLevel? = nums[numId]?.let { abstracts[it] }?.get(ilvl)

    /** A list of the given kind made for this document while editing (created once). */
    fun ensureList(bullet: Boolean): Int {
        val key = if (bullet) "bullet" else "decimal"
        created[key]?.let { return it }
        val absId = ((abstracts.keys.maxOrNull() ?: 0) + 1).coerceAtLeast(9000)
        val numId = (nums.keys.maxOrNull() ?: 0) + 1
        val levels = HashMap<Int, NumLevel>()
        val sb = StringBuilder("<w:abstractNum w:abstractNumId=\"$absId\"><w:multiLevelType w:val=\"hybridMultilevel\"/>")
        val bullets = listOf("•", "◦", "▪")
        val decimals = listOf("decimal", "lowerLetter", "lowerRoman")
        for (l in 0..8) {
            val left = 720 * (l + 1)
            val fmt = if (bullet) "bullet" else decimals[l % 3]
            val text = if (bullet) bullets[l % 3] else "%${l + 1}."
            val pPr = "<w:pPr><w:ind w:left=\"$left\" w:hanging=\"360\"/></w:pPr>"
            val rPr = if (bullet) "<w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:hint=\"default\"/></w:rPr>" else ""
            levels[l] = NumLevel(1, fmt, text, pPr, rPr)
            sb.append("<w:lvl w:ilvl=\"$l\"><w:start w:val=\"1\"/><w:numFmt w:val=\"$fmt\"/>")
                .append("<w:lvlText w:val=\"").append(XmlScan.escape(text)).append("\"/><w:lvlJc w:val=\"left\"/>")
                .append(pPr).append(rPr).append("</w:lvl>")
        }
        sb.append("</w:abstractNum>")
        abstracts[absId] = levels
        nums[numId] = absId
        addedAbstract.add(sb.toString())
        addedNums.add("<w:num w:numId=\"$numId\"><w:abstractNumId w:val=\"$absId\"/></w:num>")
        created[key] = numId
        return numId
    }

    private val created = HashMap<String, Int>()

    val modified: Boolean get() = addedNums.isNotEmpty()

    fun isBullet(numId: Int): Boolean = level(numId, 0)?.fmt == "bullet"

    companion object {
        fun parse(xml: String?): DocxNumbering {
            val abstracts = HashMap<Int, Map<Int, NumLevel>>()
            val nums = HashMap<Int, Int>()
            val overrides = HashMap<Int, Map<Int, Int>>()
            if (xml == null) return DocxNumbering(abstracts, nums, overrides, null)
            val root = XmlScan.scan(xml).children.firstOrNull { it.local == "numbering" }
                ?: return DocxNumbering(abstracts, nums, overrides, xml)
            for (a in root.childrenNamed("abstractNum")) {
                val id = a.attr(xml, "abstractNumId")?.toIntOrNull() ?: continue
                val levels = HashMap<Int, NumLevel>()
                for (l in a.childrenNamed("lvl")) {
                    val ilvl = l.attr(xml, "ilvl")?.toIntOrNull() ?: continue
                    levels[ilvl] = NumLevel(
                        start = l.child("start")?.attr(xml, "val")?.toIntOrNull() ?: 1,
                        fmt = l.child("numFmt")?.attr(xml, "val") ?: "decimal",
                        text = l.child("lvlText")?.attr(xml, "val") ?: "",
                        pPr = l.child("pPr")?.text(xml) ?: "",
                        rPr = l.child("rPr")?.text(xml) ?: ""
                    )
                }
                abstracts[id] = levels
            }
            for (n in root.childrenNamed("num")) {
                val id = n.attr(xml, "numId")?.toIntOrNull() ?: continue
                val abs = n.child("abstractNumId")?.attr(xml, "val")?.toIntOrNull() ?: continue
                nums[id] = abs
                val ov = HashMap<Int, Int>()
                for (o in n.childrenNamed("lvlOverride")) {
                    val ilvl = o.attr(xml, "ilvl")?.toIntOrNull() ?: continue
                    o.child("startOverride")?.attr(xml, "val")?.toIntOrNull()?.let { ov[ilvl] = it }
                }
                if (ov.isNotEmpty()) overrides[id] = ov
            }
            return DocxNumbering(abstracts, nums, overrides, xml)
        }

        fun format(n: Int, fmt: String): String = when (fmt) {
            "lowerLetter" -> letters(n).lowercase()
            "upperLetter" -> letters(n)
            "lowerRoman" -> roman(n).lowercase()
            "upperRoman" -> roman(n)
            "decimalZero" -> if (n < 10) "0$n" else "$n"
            "none" -> ""
            else -> n.toString()
        }

        private fun letters(n: Int): String {
            if (n <= 0) return ""
            val c = ('A' + (n - 1) % 26)
            return c.toString().repeat((n - 1) / 26 + 1)
        }

        private fun roman(n: Int): String {
            if (n <= 0 || n > 3999) return n.toString()
            val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
            val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
            var x = n
            return buildString { for (i in v.indices) while (x >= v[i]) { append(s[i]); x -= v[i] } }
        }

        /** Symbol-font bullets come through as private-use characters. */
        fun bulletGlyph(text: String): String {
            val c = text.firstOrNull() ?: return "•"
            return when (c.code) {
                0xF0B7, 0xF06C, 0x00B7, 0xF0A8 -> "•"
                0xF0A7, 0xF0FC -> "▪"
                0xF0D8, 0xF0E0 -> "➢"
                0x006F -> "◦"
                0xF076 -> "❖"
                0xF0FB -> "✓"
                else -> if (c.code in 0xF000..0xF0FF) "•" else text
            }
        }
    }
}

/** The theme's heading and body fonts. */
class DocxTheme(val major: String?, val minor: String?) {
    fun resolve(themeRef: String?): String? = when {
        themeRef == null -> null
        themeRef.startsWith("major") -> major
        else -> minor
    }

    companion object {
        fun parse(xml: String?): DocxTheme {
            if (xml == null) return DocxTheme(null, null)
            fun latin(tag: String): String? {
                val i = xml.indexOf(tag)
                if (i < 0) return null
                val j = xml.indexOf("latin", i)
                if (j < 0) return null
                val end = xml.indexOf('>', j)
                return XmlScan.attr("<" + xml.substring(j, end + 1), "typeface")?.ifBlank { null }
            }
            return DocxTheme(latin("majorFont"), latin("minorFont"))
        }
    }
}

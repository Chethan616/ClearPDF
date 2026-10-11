package com.chethan616.clearpdf.docx

/**
 * Pure editing operations on [DocPara]s. Each returns new paragraphs (dropping their original XML,
 * so the writer serializes them again) and never touches anything else.
 */
object DocxEdits {

    /** The paragraph mark's own formatting, which Word gives to text typed into an empty paragraph. */
    fun markFmt(p: DocPara): CharFmt {
        var r = Props.child(p.pPr, "rPr") ?: return CharFmt.Plain
        for (k in listOf("ins", "del", "rPrChange", "moveFrom", "moveTo")) r = Props.setR(r, k, null)
        return CharFmt(r)
    }

    /** The format new text typed at [at] picks up: the character before it, as in Word. */
    fun fmtAt(p: DocPara, at: Int): CharFmt {
        fun plain(f: CharFmt, keepLink: Boolean) = if (f.obj != null || (!keepLink && f.link != null)) CharFmt(f.rPr, if (keepLink) f.link else null) else f
        val before = (at - 1 downTo 0).firstOrNull { p.fmts[it].obj == null && p.text[it] != OBJ_CHAR }
        val after = (at until p.text.length).firstOrNull { p.fmts[it].obj == null }
        if (before != null) {
            val f = p.fmts[before]
            // Typing inside a link extends it; typing right after one doesn't.
            val inside = f.link != null && after != null && p.fmts[after].link == f.link
            return plain(f, inside)
        }
        if (after != null) return plain(p.fmts[after], false)
        return markFmt(p)
    }

    /** Applies the field's new text to [p]; inserted characters take [typing]. */
    fun typed(p: DocPara, newText: String, typing: CharFmt): DocPara {
        val old = p.text
        if (old == newText) return p
        var pre = 0
        val max = minOf(old.length, newText.length)
        while (pre < max && old[pre] == newText[pre]) pre++
        var suf = 0
        while (suf < max - pre && old[old.length - 1 - suf] == newText[newText.length - 1 - suf]) suf++
        val fmts = ArrayList<CharFmt>(newText.length)
        for (i in 0 until pre) fmts.add(p.fmts[i])
        for (i in pre until newText.length - suf) fmts.add(typing)
        for (i in old.length - suf until old.length) fmts.add(p.fmts[i])
        // Word's page marks move with the text after the edit.
        val delta = newText.length - old.length
        val marks = p.marks.map { m -> if (m <= pre) m else if (m >= old.length - suf) m + delta else pre }.distinct()
        return p.edited(text = newText, fmts = fmts, marks = marks)
    }

    /** Splits at [at]; the section break (if this paragraph ends a section) moves to the second half. */
    fun split(p: DocPara, at: Int, styles: DocxStyles): Pair<DocPara, DocPara> {
        val a = p.edited(
            text = p.text.substring(0, at), fmts = p.fmts.subList(0, at).toList(),
            pPr = Props.setP(p.pPr, "sectPr", null), marks = p.marks.filter { it < at }
        )
        var bPpr = p.pPr
        if (at >= p.text.length) {
            // Enter at the end of a heading starts its "next" style (Normal), as in Word.
            val sid = Props.p(p.pPr).styleId
            val next = sid?.let { styles.byId[it]?.next }
            if (next != null && next != sid) {
                bPpr = if (next == styles.defaultParagraphId) Props.setP(bPpr, "pStyle", null)
                else Props.setP(bPpr, "pStyle", "<w:pStyle w:val=\"${XmlScan.escape(next)}\"/>")
            }
        }
        val b = DocPara(
            id = DocIds.next(), origin = null, openTag = "<w:p>", pPr = bPpr, extras = "",
            text = p.text.substring(at), fmts = p.fmts.subList(at, p.text.length).toList(), editable = true,
            marks = p.marks.filter { it >= at }.map { it - at }
        )
        return a to b
    }

    fun merge(prev: DocPara, cur: DocPara): DocPara {
        var pPr = prev.pPr
        Props.child(cur.pPr, "sectPr")?.let { pPr = Props.setP(pPr, "sectPr", it) }
        return prev.edited(
            text = prev.text + cur.text, fmts = prev.fmts + cur.fmts, pPr = pPr, extras = prev.extras + cur.extras,
            marks = prev.marks + cur.marks.map { it + prev.text.length }
        )
    }

    /** Rewrites the formatting of [start, end) through [f] (pictures and other objects excepted). */
    fun format(p: DocPara, start: Int, end: Int, f: (CharFmt) -> CharFmt): DocPara {
        if (start >= end) return p
        val cache = HashMap<CharFmt, CharFmt>()
        val fmts = p.fmts.toMutableList()
        var changed = false
        for (i in start.coerceAtLeast(0) until end.coerceAtMost(fmts.size)) {
            val old = fmts[i]
            if (old.obj != null) continue
            val n = cache.getOrPut(old) { f(old) }
            if (n != old) { fmts[i] = n; changed = true }
        }
        return if (changed) p.edited(fmts = fmts) else p
    }

    // ── run properties ─────────────────────────────────────────────────────────

    enum class Toggle { BOLD, ITALIC, UNDERLINE, STRIKE, SUPERSCRIPT, SUBSCRIPT }

    /** Character properties the paragraph and character styles give, before direct formatting. */
    fun styleProps(p: DocPara, f: CharFmt, styles: DocxStyles): RProps {
        val base = styles.paragraphStyle(Props.p(p.pPr).styleId).second
        val rs = Props.r(f.rPr).rStyle
        return if (rs != null) styles.characterStyle(rs).over(base) else base
    }

    fun effective(p: DocPara, f: CharFmt, styles: DocxStyles): RProps = Props.r(f.rPr).over(styleProps(p, f, styles))

    fun toggle(p: DocPara, f: CharFmt, styles: DocxStyles, t: Toggle, on: Boolean): CharFmt {
        val inherited = styleProps(p, f, styles)
        var r = f.rPr
        fun set(local: String, onXml: String, offXml: String, styleOn: Boolean) {
            r = Props.setR(r, local, if (on == styleOn) null else if (on) onXml else offXml)
        }
        when (t) {
            Toggle.BOLD -> {
                set("b", "<w:b/>", "<w:b w:val=\"0\"/>", inherited.bold == true)
                set("bCs", "<w:bCs/>", "<w:bCs w:val=\"0\"/>", inherited.bold == true)
            }
            Toggle.ITALIC -> {
                set("i", "<w:i/>", "<w:i w:val=\"0\"/>", inherited.italic == true)
                set("iCs", "<w:iCs/>", "<w:iCs w:val=\"0\"/>", inherited.italic == true)
            }
            Toggle.UNDERLINE -> set("u", "<w:u w:val=\"single\"/>", "<w:u w:val=\"none\"/>", inherited.underline == true)
            Toggle.STRIKE -> {
                r = Props.setR(r, "dstrike", null)
                set("strike", "<w:strike/>", "<w:strike w:val=\"0\"/>", inherited.strike == true)
            }
            Toggle.SUPERSCRIPT -> r = Props.setR(r, "vertAlign", if (on) "<w:vertAlign w:val=\"superscript\"/>" else null)
            Toggle.SUBSCRIPT -> r = Props.setR(r, "vertAlign", if (on) "<w:vertAlign w:val=\"subscript\"/>" else null)
        }
        return f.copy(rPr = r)
    }

    fun withSize(f: CharFmt, halfPoints: Int): CharFmt {
        var r = Props.setR(f.rPr, "sz", "<w:sz w:val=\"$halfPoints\"/>")
        r = Props.setR(r, "szCs", "<w:szCs w:val=\"$halfPoints\"/>")
        return f.copy(rPr = r)
    }

    /** [argb] null → automatic colour. */
    fun withColor(f: CharFmt, argb: Int?): CharFmt =
        f.copy(rPr = Props.setR(f.rPr, "color", argb?.let { "<w:color w:val=\"${Props.hex6(it)}\"/>" }))

    /** A named Word highlight when the colour is one, run shading otherwise; null clears both. */
    fun withHighlight(f: CharFmt, argb: Int?): CharFmt {
        var r = Props.setR(f.rPr, "highlight", null)
        r = Props.setR(r, "shd", null)
        if (argb != null) {
            val name = HighlightColors.entries.firstOrNull { (it.value and 0xFFFFFF) == (argb and 0xFFFFFF) }?.key
            r = if (name != null) Props.setR(r, "highlight", "<w:highlight w:val=\"$name\"/>")
            else Props.setR(r, "shd", "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${Props.hex6(argb)}\"/>")
        }
        return f.copy(rPr = r)
    }

    fun withFont(f: CharFmt, font: String): CharFmt {
        val e = XmlScan.escape(font)
        return f.copy(rPr = Props.setR(f.rPr, "rFonts", "<w:rFonts w:ascii=\"$e\" w:hAnsi=\"$e\" w:cs=\"$e\"/>"))
    }

    fun cleared(f: CharFmt): CharFmt = f.copy(rPr = "")

    // ── paragraph properties ───────────────────────────────────────────────────

    fun withStyle(p: DocPara, styleId: String?, styles: DocxStyles): DocPara {
        val sid = styleId?.takeIf { it != styles.defaultParagraphId }
        var pPr = Props.setP(p.pPr, "pStyle", sid?.let { "<w:pStyle w:val=\"${XmlScan.escape(it)}\"/>" })
        // A heading's own size/colour shows only once the text's direct size and colour step aside,
        // the way Word's style gallery behaves for runs formatted by hand.
        val fmts = p.fmts.map { f ->
            if (f.obj != null) f else {
                var r = Props.setR(f.rPr, "sz", null)
                r = Props.setR(r, "szCs", null)
                r = Props.setR(r, "color", null)
                r = Props.setR(r, "rFonts", null)
                if (r == f.rPr) f else f.copy(rPr = r)
            }
        }
        // List paragraphs keep their list; others drop a stale indent left by the old style.
        if (Props.p(pPr).numId == null) pPr = Props.setP(pPr, "ind", null)
        return p.edited(pPr = pPr, fmts = fmts)
    }

    /** "left", "center", "right" or "both". */
    fun withAlign(p: DocPara, jc: String): DocPara =
        p.edited(pPr = Props.setP(p.pPr, "jc", if (jc == "left") null else "<w:jc w:val=\"$jc\"/>"))

    /** Sets (or with [numId] null, removes) list membership. */
    fun withList(p: DocPara, numId: Int?, styles: DocxStyles, ilvl: Int = 0): DocPara {
        val styleList = styles.paragraphStyle(Props.p(p.pPr).styleId).first.numId.let { it != null && it != 0 }
        val numPr = when {
            numId != null -> "<w:numPr><w:ilvl w:val=\"$ilvl\"/><w:numId w:val=\"$numId\"/></w:numPr>"
            // The style itself makes a list: only an explicit "no list" (numId 0) takes it out.
            styleList -> "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"0\"/></w:numPr>"
            else -> null
        }
        var pPr = Props.setP(p.pPr, "numPr", numPr)
        pPr = Props.setP(pPr, "ind", null)
        return p.edited(pPr = pPr)
    }

    fun withLeftIndent(p: DocPara, left: Int): DocPara {
        val old = Props.child(p.pPr, "ind")
        val keep = old?.let { XmlScan.scan(it).children.firstOrNull() }
        val sb = StringBuilder("<w:ind w:left=\"").append(left.coerceAtLeast(0)).append('"')
        if (keep != null) for (a in listOf("right", "firstLine", "hanging")) keep.attr(old, a)?.let { sb.append(" w:").append(a).append("=\"").append(it).append('"') }
        sb.append("/>")
        return p.edited(pPr = Props.setP(p.pPr, "ind", if (left <= 0 && keep == null) null else sb.toString()))
    }

    /** Adds Word's built-in definition of a gallery style the document doesn't have yet. */
    fun ensureStyle(styles: DocxStyles, name: String): String? {
        styles.byName(name)?.let { return it.id }
        val def = BuiltInStyles[name] ?: return null
        var id = def.first
        var n = 1
        while (styles.byId.containsKey(id)) id = def.first + (n++)
        val base = styles.defaultParagraphId
        val basedOn = if (base != null) "<w:basedOn w:val=\"${XmlScan.escape(base)}\"/><w:next w:val=\"${XmlScan.escape(base)}\"/>" else ""
        val xml = "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"${name.lowercase().let { if (it.startsWith("heading")) it else name }}\"/>$basedOn" +
            "<w:qFormat/>${def.second}${def.third}</w:style>"
        styles.added.add(xml)
        styles.byId[id] = StyleDef(id, name, "paragraph", base, base, def.second, def.third, "", false, false)
        styles.invalidate()
        return id
    }

    private val major = "<w:rFonts w:asciiTheme=\"majorHAnsi\" w:eastAsiaTheme=\"majorEastAsia\" w:hAnsiTheme=\"majorHAnsi\" w:cstheme=\"majorBidi\"/>"

    /** name → (style id, pPr, rPr), Word 2016's defaults. */
    val BuiltInStyles: Map<String, Triple<String, String, String>> = linkedMapOf(
        "Title" to Triple("Title",
            "<w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/><w:contextualSpacing/></w:pPr>",
            "<w:rPr>$major<w:spacing w:val=\"-10\"/><w:kern w:val=\"28\"/><w:sz w:val=\"56\"/><w:szCs w:val=\"56\"/></w:rPr>"),
        "Subtitle" to Triple("Subtitle",
            "<w:pPr><w:spacing w:after=\"160\"/></w:pPr>",
            "<w:rPr><w:color w:val=\"5A5A5A\"/><w:spacing w:val=\"15\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/></w:rPr>"),
        "heading 1" to Triple("Heading1",
            "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"240\" w:after=\"0\"/><w:outlineLvl w:val=\"0\"/></w:pPr>",
            "<w:rPr>$major<w:color w:val=\"2F5496\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/></w:rPr>"),
        "heading 2" to Triple("Heading2",
            "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"40\" w:after=\"0\"/><w:outlineLvl w:val=\"1\"/></w:pPr>",
            "<w:rPr>$major<w:color w:val=\"2F5496\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr>"),
        "heading 3" to Triple("Heading3",
            "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"40\" w:after=\"0\"/><w:outlineLvl w:val=\"2\"/></w:pPr>",
            "<w:rPr>$major<w:color w:val=\"1F3763\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/></w:rPr>"),
        "Quote" to Triple("Quote",
            "<w:pPr><w:spacing w:before=\"200\" w:after=\"160\"/><w:ind w:left=\"864\" w:right=\"864\"/><w:jc w:val=\"center\"/></w:pPr>",
            "<w:rPr><w:i/><w:iCs/><w:color w:val=\"404040\"/></w:rPr>")
    )
}

package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.NightPaper
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.docx.CharFmt
import com.chethan616.clearpdf.docx.DocBlock
import com.chethan616.clearpdf.docx.DocPara
import com.chethan616.clearpdf.docx.DocxNumbering
import com.chethan616.clearpdf.docx.DocxPackage
import com.chethan616.clearpdf.docx.OBJ_CHAR
import com.chethan616.clearpdf.docx.PProps
import com.chethan616.clearpdf.docx.Props
import com.chethan616.clearpdf.docx.RProps
import com.chethan616.clearpdf.docx.XmlScan
import com.chethan616.clearpdf.docx.forEachPara

/** How one paragraph sits on the page. Lengths already scaled to the screen. */
internal class ParaLook(
    val style: TextStyle,
    val padStart: Dp,
    val padEnd: Dp,
    val before: Dp,
    val after: Dp,
    /** List label column (hanging indent); 0 when none. */
    val hanging: Dp,
    val shading: Color?,
    val rtl: Boolean,
    val borderBottom: Boolean,
    /** Character properties the paragraph style gives its text. */
    val rBase: RProps,
    val props: PProps
)

/**
 * Turns document formatting into Compose text styles for one paper colour and zoom. Points become
 * `scale` sp, so a page reflows to the phone's width at a readable size (Word's "mobile view")
 * instead of shrinking a whole A4 line onto it.
 */
internal class DocxLook(
    val pkg: DocxPackage,
    val dark: Boolean,
    /** Screen dp per point: the page is laid out at its true proportions, scaled to the screen. */
    val scale: Float,
    /** The system font scale; text sizes are divided by it so the page keeps Word's line breaks. */
    private val fontScale: Float,
    private val carlito: FontFamily
) {
    /** sp per point for text, so a 12 pt run is exactly as wide (in dp) as the page expects. */
    private val sp = scale / fontScale.coerceAtLeast(0.5f)
    val paper = if (dark) Color(0xFF1C1C1E) else Color.White
    val ink = if (dark) Color(0xFFE6E6EA) else Color(0xFF111111)
    val link = if (dark) Color(0xFF6CB4FF) else Color(0xFF0563C1)
    private val styles = pkg.styles
    private val paraCache = HashMap<String, ParaLook>()
    private val scaledCache = HashMap<Float, DocxLook>()

    /** The same look at a fraction of the size, for tables too wide for the screen. */
    fun scaled(f: Float): DocxLook =
        if (f >= 0.999f) this else scaledCache.getOrPut(f) { DocxLook(pkg, dark, scale * f, fontScale, carlito) }

    fun family(name: String?, themeRef: String?): FontFamily {
        val n = (name ?: pkg.theme.resolve(themeRef) ?: pkg.theme.minor ?: "Calibri").lowercase()
        return when {
            n.startsWith("calibri") || n.startsWith("carlito") || n.startsWith("aptos") || n.startsWith("candara") ||
                n.startsWith("corbel") -> carlito
            n.contains("times") || n.contains("georgia") || n.contains("cambria") || n.contains("garamond") ||
                n.contains("book antiqua") || n.contains("palatino") || n.contains("constantia") || n.contains("serif") &&
                !n.contains("sans") || n.contains("baskerville") || n.contains("century") || n.contains("bookman") -> FontFamily.Serif
            n.contains("courier") || n.contains("consolas") || n.contains("mono") || n.contains("lucida console") -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        }
    }

    /** Text colour; on dark paper turned over like the PDF viewer's dark reader (hue kept). */
    fun inkOf(argb: Int?): Color {
        if (argb == null || argb == 0) return ink
        return if (dark) NightPaper.color(Color(argb)) else Color(argb)
    }

    /** Highlight / shading, turned over the same way on dark paper. */
    fun fillOf(argb: Int?): Color? {
        if (argb == null || argb == 0) return null
        if ((argb and 0xFFFFFF) == 0xFFFFFF) return if (dark) null else Color.White
        return if (dark) NightPaper.color(Color(argb)) else Color(argb)
    }

    fun para(p: DocPara, cell: RProps? = null): ParaLook {
        val key = p.pPr + (cell?.hashCode() ?: 0)
        paraCache[key]?.let { return it }
        val direct = Props.p(p.pPr)
        val (styleP, styleR) = styles.paragraphStyle(direct.styleId)
        var pp = direct.over(styleP)
        val numId = pp.numId?.takeIf { it != 0 }
        if (numId != null) {
            val lvl = pkg.numbering.level(numId, (pp.ilvl ?: 0).coerceIn(0, 8))
            if (lvl != null) pp = direct.over(Props.p(lvl.pPr).over(styleP))
        }
        val r = cell?.over(styleR) ?: styleR
        val sizePt = (r.size ?: 22) / 2f
        val fontSize = (sizePt * sp).sp
        val mult = if (pp.lineRule == null || pp.lineRule == "auto") (pp.line ?: 240) / 240f else 1f
        val lineHeight = when {
            pp.lineRule == "exact" && pp.line != null -> (pp.line!! / 20f * sp).sp
            pp.lineRule == "atLeast" && pp.line != null -> maxOf(pp.line!! / 20f * sp, sizePt * sp * 1.2f).sp
            mult in 0.98f..1.02f -> androidx.compose.ui.unit.TextUnit.Unspecified
            else -> (1.2f * mult).em
        }
        val left = (pp.left ?: 0).coerceAtLeast(0)
        val hangingTw = if (numId != null) (pp.hanging ?: 360) else 0
        val firstLine = if (numId == null) (pp.firstLine ?: 0) - (pp.hanging ?: 0) else 0
        val startTw = if (numId != null) (left - hangingTw).coerceAtLeast(0) else left + minOf(firstLine, 0)
        val align = when (pp.align) {
            "center" -> TextAlign.Center
            "right", "end" -> if (pp.bidi == true) TextAlign.Left else TextAlign.End
            "both", "distribute", "lowKashida", "mediumKashida", "highKashida" -> TextAlign.Justify
            else -> TextAlign.Start
        }
        val style = TextStyle(
            color = inkOf(r.color),
            fontSize = fontSize,
            fontWeight = if (r.bold == true) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (r.italic == true) FontStyle.Italic else FontStyle.Normal,
            fontFamily = family(r.font, r.fontTheme),
            textAlign = align,
            textDirection = if (pp.bidi == true) TextDirection.Rtl else TextDirection.Content,
            lineHeight = lineHeight,
            lineHeightStyle = if (lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified)
                LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None) else null,
            textIndent = if (firstLine > 0) TextIndent(firstLine = (firstLine / 20f * sp).sp) else null
        )
        val look = ParaLook(
            style = style,
            padStart = tw(startTw),
            padEnd = tw((pp.right ?: 0).coerceAtLeast(0)),
            before = tw(pp.before ?: 0),
            after = tw(pp.after ?: 0),
            hanging = if (numId != null) tw(hangingTw).coerceAtLeast((sizePt * scale * 1.1f).dp) else 0.dp,
            shading = fillOf(pp.shading),
            rtl = pp.bidi == true,
            borderBottom = pp.borderBottom == true,
            rBase = r,
            props = pp
        )
        if (paraCache.size > 3000) paraCache.clear()
        paraCache[key] = look
        return look
    }

    private fun tw(twips: Int): Dp = (twips / 20f * scale).dp

    /**
     * The paragraph's text with its runs' formatting. Objects show as zero-width spaces (pictures
     * are drawn above the text), so offsets stay one-to-one with the model and the text field.
     */
    fun annotate(
        p: DocPara,
        look: ParaLook,
        cell: RProps? = null,
        hits: List<IntRange> = emptyList(),
        current: IntRange? = null,
        withLinks: Boolean = false
    ): AnnotatedString {
        val text = p.text
        val display = CharArray(text.length)
        val b = AnnotatedString.Builder(text.length)
        var i = 0
        val styleCache = HashMap<CharFmt, Pair<SpanStyle, RProps>>()
        while (i < text.length) {
            val f = p.fmts.getOrElse(i) { CharFmt.Plain }
            var j = i + 1
            while (j < text.length && p.fmts.getOrNull(j) === f) j++
            val (span, eff) = styleCache.getOrPut(f) { spanFor(f, look, cell) }
            for (k in i until j) {
                val c = text[k]
                display[k] = when {
                    c == OBJ_CHAR -> '\u200B'
                    eff.caps == true || eff.smallCaps == true -> c.uppercaseChar()
                    else -> c
                }
            }
            b.append(String(display, i, j - i))
            b.addStyle(span, i, j)
            if (withLinks && f.link != null) {
                val rid = XmlScan.attr(f.link, "id")
                val url = rid?.let { pkg.links[it] }
                if (url != null) b.addLink(LinkAnnotation.Url(url, TextLinkStyles(span)), i, j)
            }
            i = j
        }
        val hitBg = if (dark) Color(0x66FFD60A) else Color(0x80FFD60A)
        val curBg = Color(0xFFFF9F0A).copy(alpha = if (dark) 0.75f else 0.6f)
        for (h in hits) {
            val s = h.first.coerceIn(0, text.length)
            val e = (h.last + 1).coerceIn(0, text.length)
            if (s < e) b.addStyle(SpanStyle(background = if (h == current) curBg else hitBg), s, e)
        }
        return b.toAnnotatedString()
    }

    private fun spanFor(f: CharFmt, look: ParaLook, cell: RProps?): Pair<SpanStyle, RProps> {
        val direct = Props.r(f.rPr)
        val charStyle = direct.rStyle?.let { styles.characterStyle(it) }
        val base = charStyle?.over(look.rBase) ?: look.rBase
        val eff = direct.over(cell?.over(base) ?: base)
        var size = (eff.size ?: 22) / 2f * sp
        val vert = eff.vertAlign
        if (vert == "superscript" || vert == "subscript") size *= 0.65f
        if (eff.smallCaps == true && eff.caps != true) size *= 0.8f
        val isLink = f.link != null && (eff.color == null || eff.color == 0)
        val deco = buildList {
            if (eff.underline == true || isLink) add(TextDecoration.Underline)
            if (eff.strike == true) add(TextDecoration.LineThrough)
        }
        val span = SpanStyle(
            color = if (eff.vanish == true) Color.Transparent else if (isLink) link else inkOf(eff.color),
            fontSize = if (eff.vanish == true) 1.sp else size.sp,
            fontWeight = if (eff.bold == true) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (eff.italic == true) FontStyle.Italic else FontStyle.Normal,
            fontFamily = family(eff.font, eff.fontTheme),
            textDecoration = if (deco.isEmpty()) TextDecoration.None else TextDecoration.combine(deco),
            background = fillOf(eff.highlight) ?: Color.Unspecified,
            baselineShift = when (vert) {
                "superscript" -> BaselineShift.Superscript
                "subscript" -> BaselineShift.Subscript
                else -> null
            }
        )
        return span to eff
    }

    companion object {
        fun carlito(): FontFamily = FontFamily(
            Font(R.font.carlito_regular, FontWeight.Normal),
            Font(R.font.carlito_bold, FontWeight.Bold)
        )
    }
}

/** List labels ("1.", "a)", "•") for every numbered paragraph, counted in reading order. */
internal fun numberingLabels(pkg: DocxPackage, blocks: List<DocBlock>): Map<Long, String> {
    val out = HashMap<Long, String>()
    val num = pkg.numbering
    if (num.nums.isEmpty()) return out
    val counters = HashMap<Int, IntArray>()
    val started = HashSet<Int>()
    val unset = Int.MIN_VALUE
    forEachPara(blocks) { p ->
        val direct = Props.p(p.pPr)
        val pp = direct.over(pkg.styles.paragraphStyle(direct.styleId).first)
        val numId = pp.numId?.takeIf { it != 0 } ?: return@forEachPara
        val ilvl = (pp.ilvl ?: 0).coerceIn(0, 8)
        val abs = num.nums[numId] ?: return@forEachPara
        val lvl = num.level(numId, ilvl) ?: return@forEachPara
        val arr = counters.getOrPut(abs) { IntArray(9) { unset } }
        if (started.add(numId)) num.overrides[numId]?.forEach { (l, start) -> if (l in 0..8) arr[l] = start - 1 }
        arr[ilvl] = if (arr[ilvl] == unset) lvl.start else arr[ilvl] + 1
        for (k in ilvl + 1..8) arr[k] = unset
        out[p.id] = if (lvl.fmt == "bullet") DocxNumbering.bulletGlyph(lvl.text)
        else Regex("%([1-9])").replace(lvl.text) { m ->
            val k = m.groupValues[1].toInt() - 1
            val l = num.level(numId, k)
            val v = if (arr[k] == unset) (l?.start ?: 1) else arr[k]
            DocxNumbering.format(v, l?.fmt ?: "decimal")
        }
    }
    return out
}

/** Words and characters (no spaces), the way a word processor's status bar counts them. */
internal fun countWords(blocks: List<DocBlock>): Pair<Int, Int> {
    var words = 0
    var chars = 0
    forEachPara(blocks, includePreview = true) { p ->
        var inWord = false
        for (c in p.text) {
            if (c == OBJ_CHAR) continue
            if (!c.isWhitespace()) chars++
            val w = !c.isWhitespace()
            if (w && !inWord) words++
            inWord = w
        }
    }
    return words to chars
}

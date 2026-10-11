package com.chethan616.clearpdf.docx

/**
 * A light element tree over an XML string that remembers where every element starts and ends.
 *
 * The editor rewrites only what changed: an edited paragraph is serialized again and spliced into
 * the original text at its exact range, and everything else (unknown elements, tracked changes,
 * bookmarks, section properties…) is copied through character for character. A DOM would
 * re-serialize the whole part and lose that fidelity; this keeps offsets instead.
 */
internal class XNode(val name: String, val start: Int, val parent: XNode?) {
    /** Offset just past the start tag's `>`. */
    var startTagEnd: Int = start
    /** Offset just past the end tag's `>` (or the self-closing tag). */
    var end: Int = start
    val children = ArrayList<XNode>(2)

    val local: String get() = name.substringAfter(':')

    fun child(local: String): XNode? = children.firstOrNull { it.local == local }
    fun childrenNamed(local: String): List<XNode> = children.filter { it.local == local }

    fun descendant(local: String): XNode? {
        for (c in children) {
            if (c.local == local) return c
            c.descendant(local)?.let { return it }
        }
        return null
    }

    fun text(xml: String): String = xml.substring(start, end)
    fun inner(xml: String): String = if (end > startTagEnd) xml.substring(startTagEnd, (end - name.length - 3).coerceAtLeast(startTagEnd)) else ""
    fun startTag(xml: String): String = xml.substring(start, startTagEnd)

    /** An attribute of the start tag, matched by local name (`w:val` and `val` both match `val`). */
    fun attr(xml: String, localName: String): String? = XmlScan.attr(startTag(xml), localName)
}

internal object XmlScan {

    /** Scans [xml] into a tree; the returned root is a pseudo-node spanning the whole string. */
    fun scan(xml: String): XNode {
        val root = XNode("#root", 0, null).also { it.end = xml.length; it.startTagEnd = 0 }
        var cur = root
        var i = xml.indexOf('<')
        val n = xml.length
        while (i in 0 until n) {
            when {
                xml.startsWith("<?", i) -> { i = xml.indexOf("?>", i).let { if (it < 0) n else it + 2 } }
                xml.startsWith("<!--", i) -> { i = xml.indexOf("-->", i).let { if (it < 0) n else it + 3 } }
                xml.startsWith("<![CDATA[", i) -> { i = xml.indexOf("]]>", i).let { if (it < 0) n else it + 3 } }
                xml.startsWith("<!", i) -> { i = xml.indexOf('>', i).let { if (it < 0) n else it + 1 } }
                xml.startsWith("</", i) -> {
                    val close = xml.indexOf('>', i)
                    if (close < 0) break
                    val name = xml.substring(i + 2, close).trim()
                    // Pop to the matching element (tolerant of the odd unclosed tag).
                    var node: XNode? = cur
                    while (node != null && node !== root && node.name != name) node = node.parent
                    if (node != null && node !== root) {
                        node.end = close + 1
                        cur = node.parent ?: root
                    }
                    i = close + 1
                }
                else -> {
                    val tagEnd = tagEnd(xml, i)
                    if (tagEnd < 0) break
                    var nameEnd = i + 1
                    while (nameEnd < n && !xml[nameEnd].isWhitespace() && xml[nameEnd] != '>' && xml[nameEnd] != '/') nameEnd++
                    val node = XNode(xml.substring(i + 1, nameEnd), i, cur)
                    node.startTagEnd = tagEnd + 1
                    cur.children.add(node)
                    if (xml[tagEnd - 1] == '/') {
                        node.end = tagEnd + 1
                    } else {
                        cur = node
                    }
                    i = tagEnd + 1
                }
            }
            if (i < n) i = xml.indexOf('<', i).let { if (it < 0) n else it }
        }
        return root
    }

    /** Index of the `>` closing the tag that starts at [from], skipping quoted attribute values. */
    private fun tagEnd(xml: String, from: Int): Int {
        var q: Char? = null
        var i = from + 1
        while (i < xml.length) {
            val c = xml[i]
            if (q != null) { if (c == q) q = null }
            else if (c == '"' || c == '\'') q = c
            else if (c == '>') return i
            i++
        }
        return -1
    }

    private val attrRegex = Regex("""([A-Za-z_][\w.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")

    fun attr(startTag: String, localName: String): String? {
        for (m in attrRegex.findAll(startTag)) {
            val key = m.groupValues[1]
            if (key == localName || key.substringAfter(':') == localName && !key.startsWith("xmlns")) {
                return unescape(m.groupValues[3].ifEmpty { m.groupValues[4] })
            }
        }
        return null
    }

    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i)
                if (semi > i) {
                    val ent = s.substring(i + 1, semi)
                    val rep: String? = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") -> ent.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (rep != null) { sb.append(rep); i = semi + 1; continue }
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}

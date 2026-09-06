package com.chethan616.clearpdf.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * A minimal in-memory tree for one OOXML part.
 *
 * The rest of this package parses with a streaming pull parser, which is right when the shape of
 * the document matches the shape of the output — a .docx is a flat run of paragraphs, so it streams
 * cleanly. A .pptx is not: a shape's position may live in a *different part* (its layout, or the
 * master behind that), a group transform has to be applied to children parsed later, and a run's
 * colour depends on a theme part read before any slide. Expressing that as pull-parser state is
 * where the old presentation converter gave up and just concatenated every text node it saw.
 *
 * Slide parts are tens of kilobytes, so materialising them is cheap; the streaming reader stays in
 * use for the parts that are genuinely large (worksheets, shared strings).
 */
internal class OoxmlNode(val name: String, val attrs: Map<String, String>) {

    val children = mutableListOf<OoxmlNode>()
    var text: String = ""

    fun attr(key: String): String? = attrs[key]

    /** Direct child by tag name. */
    fun child(tag: String): OoxmlNode? = children.firstOrNull { it.name == tag }

    /** Direct children by tag name. */
    fun childrenNamed(tag: String): List<OoxmlNode> = children.filter { it.name == tag }

    /** First descendant by tag name, depth-first, this node included. */
    fun find(tag: String): OoxmlNode? {
        if (name == tag) return this
        for (c in children) c.find(tag)?.let { return it }
        return null
    }

    /** All text under this node, in document order — the flattened value of a `<a:t>` run group. */
    fun textContent(): String {
        if (children.isEmpty()) return text
        val sb = StringBuilder(text)
        for (c in children) sb.append(c.textContent())
        return sb.toString()
    }

    companion object {
        /**
         * Namespace prefixes are kept as written (`a:off`, `p:sp`), matching the rest of this
         * package: OOXML producers are consistent about them in practice, and turning namespace
         * processing on costs a URI lookup per element for no gain here.
         */
        fun parse(stream: InputStream): OoxmlNode? = runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, "UTF-8")
            }
            var root: OoxmlNode? = null
            val stack = ArrayDeque<OoxmlNode>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val attrs = HashMap<String, String>(parser.attributeCount)
                        for (i in 0 until parser.attributeCount) {
                            attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        }
                        val node = OoxmlNode(parser.name, attrs)
                        stack.lastOrNull()?.children?.add(node)
                        if (root == null) root = node
                        stack.addLast(node)
                    }
                    XmlPullParser.TEXT -> stack.lastOrNull()?.let { it.text += parser.text }
                    XmlPullParser.END_TAG -> stack.removeLastOrNull()
                }
                event = parser.next()
            }
            root
        }.getOrNull()
    }
}

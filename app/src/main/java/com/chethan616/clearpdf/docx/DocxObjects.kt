package com.chethan616.clearpdf.docx

/** What an object run is, for drawing it: a picture, a text box's content, or a page break. */
class ObjInfo(
    /** Relationship id of the picture. */
    val imageRid: String?,
    /** Size in points (0 when unknown). */
    val widthPt: Float,
    val heightPt: Float,
    val textBox: List<DocBlock>,
    val pageBreak: Boolean,
    /** A floating picture's horizontal alignment ("left", "center", "right"), when it gives one. */
    val hAlign: String? = null
)

object DocxObjects {
    private val cache = HashMap<String, ObjInfo>()

    fun info(obj: String): ObjInfo = synchronized(cache) { cache[obj] } ?: parse(obj).also {
        synchronized(cache) { if (cache.size > 2000) cache.clear(); cache[obj] = it }
    }

    fun isPageBreak(obj: String?): Boolean =
        obj != null && obj.contains("br") && (obj.contains("type=\"page\"") || obj.contains("type='page'"))

    private fun parse(obj: String): ObjInfo {
        val root = XmlScan.scan(obj)
        val blip = root.descendant("blip")
        var rid = blip?.attr(obj, "embed")
        var w = 0f
        var h = 0f
        root.descendant("extent")?.let { e ->
            w = (e.attr(obj, "cx")?.toFloatOrNull() ?: 0f) / 12700f
            h = (e.attr(obj, "cy")?.toFloatOrNull() ?: 0f) / 12700f
        }
        if (rid == null) {
            // Legacy VML picture: <v:imagedata r:id> inside a shape sized by its CSS style.
            root.descendant("imagedata")?.let { img ->
                rid = img.attr(obj, "id")
                val style = generateSequence(img.parent) { it.parent }.firstNotNullOfOrNull { it.attr(obj, "style") } ?: ""
                w = cssPt(style, "width")
                h = cssPt(style, "height")
            }
        }
        val txbx = root.descendant("txbxContent")
        val text = if (txbx != null) DocxReader.Ctx(obj).container(txbx.children, readOnly = true) else emptyList()
        val hAlign = root.descendant("positionH")?.child("align")?.let { XmlScan.unescape(it.inner(obj)).trim() }
        return ObjInfo(rid, w, h, text, isPageBreak(obj), hAlign)
    }

    private fun cssPt(style: String, key: String): Float {
        val m = Regex("(?:^|;)\\s*$key\\s*:\\s*([0-9.]+)\\s*(pt|px|in|cm|mm)?").find(style) ?: return 0f
        val v = m.groupValues[1].toFloatOrNull() ?: return 0f
        return when (m.groupValues[2]) {
            "px" -> v * 0.75f
            "in" -> v * 72f
            "cm" -> v * 28.3465f
            "mm" -> v * 2.83465f
            else -> v
        }
    }
}

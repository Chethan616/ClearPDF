package com.chethan616.clearpdf.docx

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes an edited document back into its package. Untouched paragraphs and tables are copied from
 * the original XML verbatim; only edited ones are serialized again. Every other zip entry is
 * streamed through unchanged, except styles / numbering when the edits needed new definitions.
 */
object DocxWriter {

    private const val NUMBERING_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering"
    private const val STYLES_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    fun write(pkg: DocxPackage, state: DocState, source: File, dest: File) {
        val parts = parts(pkg, state)
        source.inputStream().buffered().use { input ->
            dest.outputStream().buffered().use { out -> rewrite(input, out, parts.first, parts.second) }
        }
    }

    fun documentXml(pkg: DocxPackage, state: DocState): String {
        val sb = StringBuilder(pkg.xml.length + 1024)
        sb.append(pkg.xml, 0, pkg.bodyOpenEnd)
        blocks(state.blocks, sb)
        sb.append(pkg.xml, pkg.bodyCloseStart, pkg.xml.length)
        return sb.toString()
    }

    /** (entry → new bytes, new entries). */
    private fun parts(pkg: DocxPackage, state: DocState): Pair<Map<String, ByteArray>, Map<String, ByteArray>> {
        val replace = HashMap<String, ByteArray>()
        val add = LinkedHashMap<String, ByteArray>()
        replace[pkg.mainPath] = documentXml(pkg, state).toByteArray(Charsets.UTF_8)
        var rels = pkg.relsXml
        var types = pkg.contentTypes
        val dir = pkg.mainPath.substringBeforeLast('/', "")

        val num = pkg.numbering
        if (num.modified) {
            val abstracts = num.addedAbstract.joinToString("")
            val nums = num.addedNums.joinToString("")
            val existing = num.xml
            if (existing != null) {
                replace[pkg.numberingPath] = insertNumbering(existing, abstracts, nums).toByteArray(Charsets.UTF_8)
            } else {
                add[pkg.numberingPath] = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<w:numbering xmlns:w=\"$W_NS\">$abstracts$nums</w:numbering>").toByteArray(Charsets.UTF_8)
                rels = addRel(rels, NUMBERING_TYPE, relTarget(dir, pkg.numberingPath))
                types = addOverride(types, "/" + pkg.numberingPath, "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml")
            }
        }
        val st = pkg.styles
        if (st.added.isNotEmpty()) {
            val extra = st.added.joinToString("")
            val existing = st.xml
            if (existing != null) {
                val close = existing.lastIndexOf("</")
                replace[pkg.stylesPath] = (existing.substring(0, close) + extra + existing.substring(close)).toByteArray(Charsets.UTF_8)
            } else {
                add[pkg.stylesPath] = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<w:styles xmlns:w=\"$W_NS\">$extra</w:styles>").toByteArray(Charsets.UTF_8)
                rels = addRel(rels, STYLES_TYPE, relTarget(dir, pkg.stylesPath))
                types = addOverride(types, "/" + pkg.stylesPath, "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml")
            }
        }
        if (rels != pkg.relsXml && rels != null) {
            if (pkg.relsXml != null) replace[pkg.relsPath] = rels.toByteArray(Charsets.UTF_8) else add[pkg.relsPath] = rels.toByteArray(Charsets.UTF_8)
        }
        if (types != pkg.contentTypes && types != null) replace["[Content_Types].xml"] = types.toByteArray(Charsets.UTF_8)
        return replace to add
    }

    private fun relTarget(dir: String, path: String): String =
        if (dir.isNotEmpty() && path.startsWith("$dir/")) path.removePrefix("$dir/") else "/$path"

    /** `abstractNum`s go after the existing ones, `num`s after theirs — the schema's sequence. */
    internal fun insertNumbering(xml: String, abstracts: String, nums: String): String {
        val root = XmlScan.scan(xml).children.firstOrNull { it.local == "numbering" } ?: return xml
        val absAt = (root.children.lastOrNull { it.local == "abstractNum" } ?: root.children.lastOrNull { it.local == "numPicBullet" })?.end
            ?: root.startTagEnd
        val numAt = root.children.firstOrNull { it.local == "numIdMacAtCleanup" }?.start ?: xml.lastIndexOf("</")
        return xml.substring(0, absAt) + abstracts + xml.substring(absAt, numAt) + nums + xml.substring(numAt)
    }

    private fun addRel(rels: String?, type: String, target: String): String {
        val base = rels ?: "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>"
        var n = 1
        while (base.contains("Id=\"rIdCp$n\"")) n++
        val close = base.lastIndexOf("</")
        return base.substring(0, close) + "<Relationship Id=\"rIdCp$n\" Type=\"$type\" Target=\"$target\"/>" + base.substring(close)
    }

    private fun addOverride(types: String?, partName: String, contentType: String): String? {
        types ?: return null
        if (types.contains("PartName=\"$partName\"")) return types
        val close = types.lastIndexOf("</")
        return types.substring(0, close) + "<Override PartName=\"$partName\" ContentType=\"$contentType\"/>" + types.substring(close)
    }

    private fun rewrite(input: InputStream, output: OutputStream, replace: Map<String, ByteArray>, add: Map<String, ByteArray>) {
        ZipInputStream(input).use { zin ->
            val zout = ZipOutputStream(output)
            val buf = ByteArray(64 * 1024)
            val seen = HashSet<String>()
            while (true) {
                val e = zin.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (!seen.add(n)) continue
                val out = ZipEntry(e.name).apply { if (e.time != -1L) time = e.time }
                zout.putNextEntry(out)
                val bytes = replace[n]
                if (bytes != null) zout.write(bytes)
                else while (true) {
                    val k = zin.read(buf)
                    if (k <= 0) break
                    zout.write(buf, 0, k)
                }
                zout.closeEntry()
            }
            for ((n, bytes) in add) {
                if (n in seen) continue
                zout.putNextEntry(ZipEntry(n))
                zout.write(bytes)
                zout.closeEntry()
            }
            zout.finish()
            zout.flush()
        }
    }

    // ── serialization ──────────────────────────────────────────────────────────

    fun blocks(list: List<DocBlock>, sb: StringBuilder) {
        for (b in list) when (b) {
            is DocPara -> para(b, sb)
            is DocTable -> table(b, sb)
            is DocRaw -> sb.append(b.xml)
        }
    }

    private fun table(t: DocTable, sb: StringBuilder) {
        if (t.origin != null) { sb.append(t.origin); return }
        sb.append(t.head)
        for (item in t.items) when (item) {
            is DocRow -> {
                sb.append(item.head)
                for (ri in item.items) when (ri) {
                    is DocCell -> {
                        sb.append(ri.head)
                        blocks(ri.blocks, sb)
                        sb.append(ri.tail)
                    }
                    is DocRaw -> sb.append(ri.xml)
                }
                sb.append(item.tail)
            }
            is DocRaw -> sb.append(item.xml)
        }
        sb.append(t.tail)
    }

    private fun tagName(open: String): String =
        open.substring(1).takeWhile { !it.isWhitespace() && it != '>' && it != '/' }

    fun para(p: DocPara, sb: StringBuilder) {
        if (p.origin != null) { sb.append(p.origin); return }
        sb.append(p.openTag).append(p.pPr).append(p.extras)
        val n = p.text.length
        var i = 0
        while (i < n) {
            val link = p.fmts[i].link
            var j = i
            while (j < n && p.fmts[j].link == link) j++
            if (link != null) {
                sb.append(link)
                runs(p, i, j, sb)
                sb.append("</").append(tagName(link)).append('>')
            } else runs(p, i, j, sb)
            i = j
        }
        sb.append("</").append(tagName(p.openTag)).append('>')
    }

    private fun runs(p: DocPara, from: Int, to: Int, sb: StringBuilder) {
        var k = from
        while (k < to) {
            val f = p.fmts[k]
            if (f.obj != null) {
                sb.append(f.obj)
                k++
                continue
            }
            var m = k
            while (m < to && p.fmts[m].obj == null && p.fmts[m].rPr == f.rPr) m++
            run(f.rPr, p.text, k, m, sb)
            k = m
        }
    }

    private fun run(rPr: String, text: String, from: Int, to: Int, sb: StringBuilder) {
        sb.append("<w:r>").append(rPr)
        val buf = StringBuilder()
        fun flush() {
            if (buf.isEmpty()) return
            sb.append("<w:t xml:space=\"preserve\">").append(XmlScan.escape(buf.toString())).append("</w:t>")
            buf.setLength(0)
        }
        var i = from
        while (i < to) {
            val c = text[i]
            when {
                c == '\t' -> { flush(); sb.append("<w:tab/>") }
                c == '\n' || c == '\r' -> { flush(); sb.append("<w:br/>") }
                c == '\u2011' -> { flush(); sb.append("<w:noBreakHyphen/>") }
                c == '\u00AD' -> { flush(); sb.append("<w:softHyphen/>") }
                c == OBJ_CHAR -> {}
                c.isHighSurrogate() -> {
                    if (i + 1 < to && text[i + 1].isLowSurrogate()) { buf.append(c).append(text[i + 1]); i++ }
                }
                c.isLowSurrogate() -> {}
                c < ' ' || c == '\uFFFE' || c == '\uFFFF' -> {}
                else -> buf.append(c)
            }
            i++
        }
        flush()
        sb.append("</w:r>")
    }
}

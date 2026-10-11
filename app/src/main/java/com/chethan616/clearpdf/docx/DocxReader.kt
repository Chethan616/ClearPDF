package com.chethan616.clearpdf.docx

import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

internal object DocIds {
    private val n = AtomicLong(1)
    fun next(): Long = n.getAndIncrement()
}

/** Everything about the opened file the editor needs besides its blocks. */
class DocxPackage(
    val mainPath: String,
    val xml: String,
    /** Offset just past `<w:body>`. */
    val bodyOpenEnd: Int,
    /** Offset of `</w:body>`. */
    val bodyCloseStart: Int,
    val styles: DocxStyles,
    val stylesPath: String,
    val numbering: DocxNumbering,
    val numberingPath: String,
    val theme: DocxTheme,
    /** Relationship id → zip entry path (internal targets only). */
    val rels: Map<String, String>,
    /** Relationship id → URL, for hyperlinks. */
    val links: Map<String, String>,
    val relsPath: String,
    val relsXml: String?,
    val contentTypes: String?,
    /** Zip entry path → bytes, for pictures. */
    val media: Map<String, ByteArray>,
    val page: PageSetup,
    /** False for documents this editor can show but can't safely write (Strict OOXML, odd prefixes). */
    val writable: Boolean
) {
    fun image(rid: String): ByteArray? = rels[rid]?.let { media[it] }
}

/**
 * Reads a .docx into [DocBlock]s. Paragraphs and tables keep their original XML, so whatever the
 * editor doesn't model survives a save byte for byte.
 */
object DocxReader {

    private const val MEDIA_BUDGET = 60L * 1024 * 1024

    fun read(input: InputStream): Pair<DocxPackage, DocState>? {
        val parts = HashMap<String, String>()
        val media = HashMap<String, ByteArray>()
        var mediaBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.removePrefix("/")
                val lower = n.lowercase()
                if (lower.endsWith(".xml") || lower.endsWith(".rels")) {
                    parts[n] = zip.readBytes().toString(Charsets.UTF_8)
                } else if (!e.isDirectory && mediaBytes < MEDIA_BUDGET && isPicture(lower)) {
                    val b = zip.readBytes()
                    mediaBytes += b.size
                    media[n] = b
                }
            }
        }
        val rootRels = parts["_rels/.rels"]
        val mainPath = rootRels?.let { relsOf(it, "") }?.firstOrNull { it.type.endsWith("/officeDocument") }?.target
            ?.takeIf { parts.containsKey(it) } ?: "word/document.xml"
        val xml = parts[mainPath] ?: return null
        val dir = mainPath.substringBeforeLast('/', "")
        val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + mainPath.substringAfterLast('/') + ".rels"
        val relsXml = parts[relsPath]
        val rels = relsXml?.let { relsOf(it, dir) } ?: emptyList()
        val relMap = rels.filter { !it.external }.associate { it.id to it.target }
        val stylesPath = rels.firstOrNull { it.type.endsWith("/styles") }?.target ?: join(dir, "styles.xml")
        val numberingPath = rels.firstOrNull { it.type.endsWith("/numbering") }?.target ?: join(dir, "numbering.xml")
        val themePath = rels.firstOrNull { it.type.endsWith("/theme") }?.target

        val root = XmlScan.scan(xml)
        val document = root.children.firstOrNull { it.local == "document" } ?: return null
        val body = document.child("body") ?: return null
        val strict = xml.contains("purl.oclc.org/ooxml/wordprocessingml")
        val writable = !strict && body.name == "w:body" && body.end > body.startTagEnd

        val ctx = Ctx(xml)
        val blocks = ctx.container(body.children, readOnly = !writable)
        val bodyClose = xml.lastIndexOf("</", body.end - 1).coerceAtLeast(body.startTagEnd)

        val page = body.children.lastOrNull { it.local == "sectPr" }?.let { s ->
            val sz = s.child("pgSz")
            val mar = s.child("pgMar")
            fun tw(n: XNode?, a: String, d: Int) = n?.attr(xml, a)?.toFloatOrNull()?.toInt() ?: d
            PageSetup(
                width = tw(sz, "w", 11906), height = tw(sz, "h", 16838),
                left = tw(mar, "left", 1440), right = tw(mar, "right", 1440),
                top = tw(mar, "top", 1440), bottom = tw(mar, "bottom", 1440)
            )
        } ?: PageSetup()

        val pkg = DocxPackage(
            mainPath = mainPath,
            xml = xml,
            bodyOpenEnd = body.startTagEnd,
            bodyCloseStart = bodyClose,
            styles = DocxStyles.parse(parts[stylesPath]),
            stylesPath = stylesPath,
            numbering = DocxNumbering.parse(parts[numberingPath]),
            numberingPath = numberingPath,
            theme = DocxTheme.parse(themePath?.let { parts[it] }),
            rels = relMap,
            links = rels.filter { it.external }.associate { it.id to it.target },
            relsPath = relsPath,
            relsXml = relsXml,
            contentTypes = parts["[Content_Types].xml"],
            media = media,
            page = page,
            writable = writable
        )
        return pkg to DocState(blocks)
    }

    private fun isPicture(n: String) =
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") ||
            n.endsWith(".bmp") || n.endsWith(".webp") || n.endsWith(".jfif")

    class Rel(val id: String, val type: String, val target: String, val external: Boolean)

    fun relsOf(xml: String, baseDir: String): List<Rel> {
        val root = XmlScan.scan(xml).children.firstOrNull() ?: return emptyList()
        return root.childrenNamed("Relationship").mapNotNull { r ->
            val id = r.attr(xml, "Id") ?: return@mapNotNull null
            val target = r.attr(xml, "Target") ?: return@mapNotNull null
            val external = r.attr(xml, "TargetMode").equals("External", true)
            Rel(id, r.attr(xml, "Type") ?: "", if (external) target else resolve(baseDir, target), external)
        }
    }

    private fun join(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    fun resolve(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/'))
        for (seg in target.split('/')) when (seg) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            else -> parts.add(seg)
        }
        return parts.joinToString("/")
    }

    /** Parses one XML string's blocks; also used for text boxes inside a picture run. */
    internal class Ctx(val xml: String) {
        private val interned = HashMap<CharFmt, CharFmt>()
        private fun intern(f: CharFmt) = interned.getOrPut(f) { f }

        fun container(children: List<XNode>, readOnly: Boolean): List<DocBlock> {
            val out = ArrayList<DocBlock>(children.size)
            for (c in children) out.add(
                when (c.local) {
                    "p" -> para(c, readOnly)
                    "tbl" -> table(c, readOnly)
                    "sdt" -> DocRaw(DocIds.next(), c.text(xml), c.child("sdtContent")?.let { container(it.children, true) } ?: emptyList())
                    "customXml" -> DocRaw(DocIds.next(), c.text(xml), container(c.children, true))
                    else -> DocRaw(DocIds.next(), c.text(xml))
                }
            )
            return out
        }

        private val markers = setOf(
            "bookmarkStart", "bookmarkEnd", "permStart", "permEnd", "commentRangeStart", "commentRangeEnd"
        )

        fun para(p: XNode, readOnly: Boolean): DocPara {
            val text = StringBuilder()
            val fmts = ArrayList<CharFmt>()
            val extras = StringBuilder()
            val marks = ArrayList<Int>()
            var editable = !readOnly

            fun add(s: String, f: CharFmt) {
                for (ch in s) { text.append(ch); fmts.add(f) }
            }

            fun run(r: XNode, link: String?) {
                val rPr = r.child("rPr")?.text(xml) ?: ""
                val fmt = intern(CharFmt(rPr, link))
                for (c in r.children) when (c.local) {
                    "rPr" -> {}
                    "lastRenderedPageBreak" -> if (marks.lastOrNull() != text.length) marks.add(text.length)
                    "t" -> add(XmlScan.unescape(c.inner(xml)), fmt)
                    "tab" -> add("\t", fmt)
                    "br", "cr" -> {
                        val type = c.attr(xml, "type")
                        if (type == null || type == "textWrapping") add("\n", fmt)
                        else add(OBJ_CHAR.toString(), intern(CharFmt(rPr, link, objXml(r, rPr, c))))
                    }
                    "noBreakHyphen" -> add("\u2011", fmt)
                    "softHyphen" -> add("\u00AD", fmt)
                    "delText", "instrText", "delInstrText", "fldChar" -> editable = false
                    else -> add(OBJ_CHAR.toString(), intern(CharFmt(rPr, link, objXml(r, rPr, c))))
                }
            }

            fun deep(n: XNode) {
                for (c in n.children) when (c.local) {
                    "r" -> run(c, null)
                    "rPr", "pPr", "sdtPr", "sdtEndPr", "del", "moveFrom" -> {}
                    else -> deep(c)
                }
            }

            for (c in p.children) when (c.local) {
                "pPr", "proofErr" -> {}
                "r" -> run(c, null)
                "hyperlink" -> {
                    val tag = c.startTag(xml)
                    if (tag.endsWith("/>")) continue
                    for (h in c.children) when {
                        h.local == "r" -> run(h, tag)
                        h.local in markers -> extras.append(h.text(xml))
                        h.local == "proofErr" -> {}
                        else -> { editable = false; deep(h) }
                    }
                }
                in markers -> extras.append(c.text(xml))
                else -> { editable = false; deep(c) }
            }
            // Symbols set in fonts we can't draw, maths, fields… still show their text, but such
            // paragraphs aren't offered for editing (their structure can't be re-flowed safely).
            val open = p.startTag(xml).let { if (it.endsWith("/>")) it.dropLast(2).trimEnd() + ">" else it }
            return DocPara(
                id = DocIds.next(),
                origin = p.text(xml),
                openTag = open,
                pPr = p.child("pPr")?.text(xml) ?: "",
                extras = extras.toString(),
                text = text.toString(),
                fmts = fmts,
                editable = editable,
                marks = marks
            )
        }

        private fun objXml(r: XNode, rPr: String, child: XNode): String {
            val open = r.startTag(xml).let { if (it.endsWith("/>")) it.dropLast(2).trimEnd() + ">" else it }
            return open + rPr + child.text(xml) + "</" + r.name + ">"
        }

        private fun closeStart(n: XNode): Int = xml.lastIndexOf("</", n.end - 1).coerceAtLeast(n.startTagEnd)

        fun table(t: XNode, readOnly: Boolean): DocBlock {
            val items = t.children.filter { it.local != "tblPr" && it.local != "tblGrid" }
            if (items.isEmpty() || t.end <= t.startTagEnd) return DocRaw(DocIds.next(), t.text(xml))
            val grid = t.child("tblGrid")?.childrenNamed("gridCol")?.map { it.attr(xml, "w")?.toFloatOrNull()?.toInt() ?: 0 }
                ?: emptyList()
            return DocTable(
                id = DocIds.next(),
                origin = t.text(xml),
                head = xml.substring(t.start, items.first().start),
                items = items.map { if (it.local == "tr") row(it, readOnly) else DocRaw(DocIds.next(), it.text(xml)) },
                tail = xml.substring(items.last().end, t.end),
                grid = grid,
                tblPr = t.child("tblPr")?.text(xml) ?: ""
            )
        }

        private fun row(tr: XNode, readOnly: Boolean): DocRow {
            val items = tr.children.filter { it.local != "tblPrEx" && it.local != "trPr" }
            val trPr = tr.child("trPr")?.text(xml) ?: ""
            if (items.isEmpty() || tr.end <= tr.startTagEnd) {
                val cs = closeStart(tr)
                return DocRow(DocIds.next(), xml.substring(tr.start, cs).let { if (it.endsWith("/>")) it.dropLast(2) + ">" else it },
                    emptyList(), "</${tr.name}>", trPr)
            }
            return DocRow(
                id = DocIds.next(),
                head = xml.substring(tr.start, items.first().start),
                items = items.map { if (it.local == "tc") cell(it, readOnly) else DocRaw(DocIds.next(), it.text(xml)) },
                tail = xml.substring(items.last().end, tr.end),
                trPr = trPr
            )
        }

        private fun cell(tc: XNode, readOnly: Boolean): DocCell {
            val blocks = tc.children.filter { it.local != "tcPr" }
            val tcPr = tc.child("tcPr")?.text(xml) ?: ""
            if (blocks.isEmpty() || tc.end <= tc.startTagEnd) {
                val cs = closeStart(tc)
                return DocCell(DocIds.next(), xml.substring(tc.start, cs).let { if (it.endsWith("/>")) it.dropLast(2) + ">" else it },
                    emptyList(), "</${tc.name}>", tcPr)
            }
            return DocCell(
                id = DocIds.next(),
                head = xml.substring(tc.start, blocks.first().start),
                blocks = container(blocks, readOnly),
                tail = xml.substring(blocks.last().end, tc.end),
                tcPr = tcPr
            )
        }
    }
}

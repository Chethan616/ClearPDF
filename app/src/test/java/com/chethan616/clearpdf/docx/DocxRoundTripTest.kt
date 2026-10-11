package com.chethan616.clearpdf.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocxRoundTripTest {

    private val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

    private fun docx(body: String, extra: Map<String, String> = emptyMap()): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
            "word/_rels/document.xml.rels" to "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId9\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>",
            "word/document.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $ns><w:body>$body" +
                "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/></w:sectPr></w:body></w:document>"
        )
        parts.putAll(extra)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, c) in parts) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return bos.toByteArray()
    }

    private fun read(bytes: ByteArray) = DocxReader.read(bytes.inputStream())!!

    private fun paras(state: DocState): List<DocPara> {
        val out = ArrayList<DocPara>()
        forEachPara(state.blocks, includePreview = false) { out.add(it) }
        return out
    }

    private fun save(bytes: ByteArray, pkg: DocxPackage, state: DocState): ByteArray {
        val src = File.createTempFile("src", ".docx").apply { writeBytes(bytes); deleteOnExit() }
        val out = File.createTempFile("out", ".docx").apply { deleteOnExit() }
        DocxWriter.write(pkg, state, src, out)
        return out.readBytes()
    }

    private fun entry(bytes: ByteArray, name: String): String? {
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: return null
                if (e.name == name) return z.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    private val body = "<w:p w14:paraId=\"1\" xmlns:w14=\"x\"><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>" +
        "<w:r><w:rPr><w:sz w:val=\"28\"/></w:rPr><w:t>Hello </w:t></w:r><w:proofErr w:type=\"spellStart\"/>" +
        "<w:r><w:rPr><w:i/></w:rPr><w:t>world</w:t></w:r><w:bookmarkStart w:id=\"0\" w:name=\"b\"/><w:bookmarkEnd w:id=\"0\"/></w:p>" +
        "<w:p><w:r><w:t xml:space=\"preserve\">Second &amp; line</w:t></w:r><w:r><w:tab/><w:t>tabbed</w:t></w:r></w:p>" +
        "<w:p><w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink><w:r><w:t> after</w:t></w:r></w:p>" +
        "<w:p><w:ins w:id=\"5\" w:author=\"a\"><w:r><w:t>tracked</w:t></w:r></w:ins></w:p>" +
        "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/></w:tblPr><w:tblGrid><w:gridCol w:w=\"4000\"/><w:gridCol w:w=\"4000\"/></w:tblGrid>" +
        "<w:tr><w:tc><w:tcPr><w:tcW w:w=\"4000\" w:type=\"dxa\"/></w:tcPr><w:p><w:r><w:t>A1</w:t></w:r></w:p></w:tc>" +
        "<w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"

    @Test
    fun readsTextRunsAndStructure() {
        val (pkg, state) = read(docx(body))
        val ps = paras(state)
        assertEquals("Hello world", ps[0].text)
        assertEquals("Second & line\ttabbed", ps[1].text)
        assertEquals("link after", ps[2].text)
        assertEquals("tracked", ps[3].text)
        assertFalse("tracked changes are not editable", ps[3].editable)
        assertTrue(ps[0].editable)
        assertEquals("A1", ps[4].text)
        assertEquals("B1", ps[5].text)
        assertEquals("https://example.com", pkg.links["rId9"])
        assertEquals(12240, pkg.page.width)
        assertTrue(ps[0].extras.contains("bookmarkStart"))
    }

    @Test
    fun untouchedDocumentIsWrittenVerbatim() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        assertEquals(pkg.xml, DocxWriter.documentXml(pkg, state))
    }

    @Test
    fun typingKeepsFormattingAndOtherParagraphs() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val p = paras(state)[0]
        val typed = DocxEdits.typed(p, "Hello big world", DocxEdits.fmtAt(p, 6))
        val next = DocState(replacePara(state.blocks, typed)!!)
        val out = save(bytes, pkg, next)
        val xml = entry(out, "word/document.xml")!!
        // The untouched paragraphs, table included, are byte-identical.
        assertTrue(xml.contains("<w:p><w:r><w:t xml:space=\"preserve\">Second &amp; line</w:t></w:r><w:r><w:tab/><w:t>tabbed</w:t></w:r></w:p>"))
        assertTrue(xml.contains("<w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc>"))
        val (_, again) = read(out)
        val q = paras(again)[0]
        assertEquals("Hello big world", q.text)
        // "big " was typed after "Hello " (size 14pt): it carries that run's formatting.
        assertEquals(q.fmts[0].rPr, q.fmts[6].rPr)
        assertTrue(q.fmts[10].rPr.contains("<w:i/>"))
        assertTrue(q.pPr.contains("Heading1"))
        assertTrue(q.extras.contains("bookmarkStart"))
        assertEquals("link after", paras(again)[2].text)
    }

    @Test
    fun boldGoesInSchemaOrder() {
        val (pkg, state) = read(docx(body))
        val p = paras(state)[0]
        val bold = DocxEdits.format(p, 0, 5) { DocxEdits.toggle(p, it, pkg.styles, DocxEdits.Toggle.BOLD, true) }
        val rPr = bold.fmts[0].rPr
        assertTrue(rPr, rPr.indexOf("<w:b/>") in 0 until rPr.indexOf("<w:sz "))
        assertEquals(rPr, bold.fmts[4].rPr)
        // The space after "Hello" was not in the range.
        assertFalse(bold.fmts[5].rPr.contains("<w:b/>"))
    }

    @Test
    fun splitAndMergeRoundTrip() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val p = paras(state)[1]
        val (a, b) = DocxEdits.split(p, 6, pkg.styles)
        assertEquals("Second", a.text)
        assertEquals(" & line\ttabbed", b.text)
        val blocks = mapContainerOf(state.blocks, p.id) { list ->
            list.flatMap { if (it is DocPara && it.id == p.id) listOf(a, b) else listOf(it) }
        }!!
        val out = save(bytes, pkg, DocState(blocks))
        val ps = paras(read(out).second)
        assertEquals("Second", ps[1].text)
        assertEquals(" & line\ttabbed", ps[2].text)
        assertEquals("Second & line\ttabbed", DocxEdits.merge(a, b).text)
    }

    @Test
    fun hyperlinkSurvivesEditing() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val p = paras(state)[2]
        val typed = DocxEdits.typed(p, "link after it", DocxEdits.fmtAt(p, 10))
        val out = save(bytes, pkg, DocState(replacePara(state.blocks, typed)!!))
        val xml = entry(out, "word/document.xml")!!
        assertTrue(xml, xml.contains("<w:hyperlink r:id=\"rId9\"><w:r><w:t xml:space=\"preserve\">link</w:t></w:r></w:hyperlink>"))
        assertEquals("link after it", paras(read(out).second)[2].text)
    }

    @Test
    fun editingATableCellRebuildsOnlyThatTable() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val cell = paras(state)[5]
        val typed = DocxEdits.typed(cell, "B1!", DocxEdits.fmtAt(cell, 2))
        val out = save(bytes, pkg, DocState(replacePara(state.blocks, typed)!!))
        val xml = entry(out, "word/document.xml")!!
        assertTrue(xml.contains("<w:tblStyle w:val=\"TableGrid\"/>"))
        assertTrue(xml.contains("<w:tcW w:w=\"4000\" w:type=\"dxa\"/>"))
        assertEquals(listOf("A1", "B1!"), paras(read(out).second).drop(4).map { it.text })
    }

    @Test
    fun newListCreatesNumberingPart() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val p = paras(state)[1]
        val numId = pkg.numbering.ensureList(bullet = true)
        val listed = DocxEdits.withList(p, numId, pkg.styles)
        val out = save(bytes, pkg, DocState(replacePara(state.blocks, listed)!!))
        val numbering = entry(out, "word/numbering.xml")
        assertNotNull(numbering)
        assertTrue(entry(out, "word/_rels/document.xml.rels")!!.contains("relationships/numbering"))
        assertTrue(entry(out, "[Content_Types].xml")!!.contains("/word/numbering.xml"))
        val (pkg2, state2) = read(out)
        assertTrue(pkg2.numbering.isBullet(numId))
        assertEquals(numId, Props.p(paras(state2)[1].pPr).numId)
    }

    @Test
    fun numberingInsertKeepsSchemaOrder() {
        val xml = "<w:numbering $ns><w:abstractNum w:abstractNumId=\"0\"/><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>"
        val out = DocxWriter.insertNumbering(xml, "<w:abstractNum w:abstractNumId=\"9\"/>", "<w:num w:numId=\"2\"/>")
        assertTrue(out.indexOf("abstractNumId=\"9\"") < out.indexOf("<w:num w:numId=\"1\""))
        assertTrue(out.indexOf("<w:num w:numId=\"2\"/>") > out.indexOf("<w:num w:numId=\"1\""))
    }

    @Test
    fun keepsWordPageMarksThroughEdits() {
        val body = "<w:p><w:r><w:t xml:space=\"preserve\">end of page one </w:t></w:r><w:r><w:lastRenderedPageBreak/><w:t>start of two</w:t></w:r></w:p>"
        val (_, state) = read(docx(body))
        val p = paras(state)[0]
        assertEquals(listOf(16), p.marks)
        // Typing before the mark moves it; typing after leaves it.
        val typed = DocxEdits.typed(p, "the end of page one start of two", CharFmt.Plain)
        assertEquals(listOf(20), typed.marks)
        val (a, b) = DocxEdits.split(typed, 4, read(docx(body)).first.styles)
        assertEquals(emptyList<Int>(), a.marks)
        assertEquals(listOf(16), b.marks)
        assertEquals(listOf(20), DocxEdits.merge(a, b).marks)
    }

    @Test
    fun escapesAndDropsInvalidCharacters() {
        val bytes = docx(body)
        val (pkg, state) = read(bytes)
        val p = paras(state)[1]
        val typed = DocxEdits.typed(p, "a<b>&\u0001c", CharFmt.Plain)
        val out = save(bytes, pkg, DocState(replacePara(state.blocks, typed)!!))
        assertEquals("a<b>&c", paras(read(out).second)[1].text)
    }
}

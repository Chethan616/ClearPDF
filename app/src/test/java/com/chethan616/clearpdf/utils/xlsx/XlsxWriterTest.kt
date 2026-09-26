package com.chethan616.clearpdf.utils.xlsx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class XlsxWriterTest {

    @Before fun useKxml() {
        XlsxReader.parserFactory = { KXmlParser() }
    }

    private val media = ByteArray(3000) { (it * 31 % 251).toByte() }

    private val sheet1 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<dimension ref="A1:C4"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="B3" sqref="B3"/></sheetView></sheetViews>
<sheetFormatPr defaultRowHeight="15"/><cols><col min="1" max="1" width="20" customWidth="1"/><col min="2" max="3" width="12" customWidth="1"/></cols>
<sheetData>
<row r="1" spans="1:3" ht="20" customHeight="1"><c r="A1" s="1" t="s"><v>0</v></c><c r="B1" s="1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
<row r="2" spans="1:3"><c r="A2" t="s"><v>3</v></c><c r="B2" s="2"><v>12.5</v></c><c r="C2"><f>B2*2</f><v>25</v></c></row>
<row r="3" spans="1:3"><c r="A3"><v>7</v></c><c r="B3" s="2"><v>3</v></c></row>
<row r="4" spans="1:3"><c r="A4" t="inlineStr"><is><t>inline &amp; text</t></is></c><c r="B4"><f>SUM(B2:B3)</f><v>15.5</v></c></row>
</sheetData>
<mergeCells count="1"><mergeCell ref="A3:A4"/></mergeCells>
<dataValidations count="1"><dataValidation type="list" allowBlank="1" showErrorMessage="1" sqref="C2:C4"><formula1>"Yes,No,Maybe"</formula1></dataValidation></dataValidations>
<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>
</worksheet>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="0.0"/></numFmts>
<fonts count="2"><font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/><scheme val="minor"/></font><font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/><scheme val="minor"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor theme="4" tint="-0.249977111117893"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color indexed="64"/></left><right style="thin"><color indexed="64"/></right><top style="thin"><color indexed="64"/></top><bottom style="medium"><color rgb="FFFF0000"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
<dxfs count="0"/>
</styleSheet>"""

    private val shared = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
<si><t>Name</t></si><si><t>Amount</t></si><si><r><rPr><b/><color rgb="FFFF0000"/></rPr><t>Rich</t></r><r><t xml:space="preserve"> text</t></r></si>
<si><t>東京</t><rPh sb="0" eb="2"><t>トウキョウ</t></rPh><phoneticPr fontId="1"/></si>
</sst>"""

    private val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Data" sheetId="1" r:id="rId1"/><sheet name="Other" sheetId="2" r:id="rId2"/></sheets>
<definedNames><definedName name="Total">Data!${'$'}B${'$'}4</definedName></definedNames>
<calcPr calcId="191029"/></workbook>"""

    private val sheet2 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1"><f>Data!B3*10</f><v>30</v></c></row></sheetData></worksheet>"""

    private val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/><Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/calcChain" Target="calcChain.xml"/></Relationships>"""

    private val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/calcChain.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml"/></Types>"""

    private fun build(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            put("[Content_Types].xml", contentTypes.toByteArray())
            put("xl/workbook.xml", workbook.toByteArray())
            put("xl/_rels/workbook.xml.rels", rels.toByteArray())
            put("xl/worksheets/sheet1.xml", sheet1.toByteArray())
            put("xl/worksheets/sheet2.xml", sheet2.toByteArray())
            put("xl/styles.xml", styles.toByteArray())
            put("xl/sharedStrings.xml", shared.toByteArray())
            put("xl/calcChain.xml", "<calcChain/>".toByteArray())
            put("xl/media/image1.png", media)
        }
        return bos.toByteArray()
    }

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes() }
        }
        return out
    }

    @Test fun readsStylesColoursRichTextAndStructure() {
        val wb = XlsxReader.read(build())
        assertEquals(listOf("Data", "Other"), wb.sheets.map { it.name })
        val s = wb.sheets[0]
        assertEquals(1, s.frozenRows)
        assertEquals(20f, s.colWidthChars(0))
        assertEquals(20f, s.rowHeightPt(0))
        assertEquals(listOf(CellRange(2, 0, 3, 0)), s.merges)
        val dv = s.validationAt(2, 2)!!
        assertEquals(listOf("Yes", "No", "Maybe"), wb.listItems(dv, s))

        val header = wb.styles.resolve(s.cell(0, 0)!!.style)
        assertTrue(header.bold)
        assertEquals(0xFFFFFFFF.toInt(), header.fontArgb)
        assertTrue(XlsxColors.near(header.fillArgb!!, 0xFF2F5597.toInt(), 2))
        assertEquals("center", header.hAlign)
        assertTrue(header.wrap)
        assertEquals(2f, header.bottom!!.weight)
        assertEquals(0xFFFF0000.toInt(), header.bottom!!.argb)

        assertEquals("12.5", wb.display(s.cell(1, 1)!!))
        assertEquals("3.0", wb.display(s.cell(2, 1)!!))
        // rPh must not leak into the text; rich runs survive.
        assertEquals("東京", wb.display(s.cell(1, 0)!!))
        val rich = wb.textOf(s.cell(0, 2)!!)!!
        assertEquals("Rich text", rich.text)
        assertEquals(2, rich.runs!!.size)
        assertEquals("inline & text", wb.display(s.cell(3, 0)!!))
        assertEquals("=SUM(B2:B3)", wb.editText(s.cell(3, 1)!!))
    }

    @Test fun untouchedPackageRoundTripsEntriesByteIdentical() {
        val src = build()
        val wb = XlsxReader.read(src)
        val out = XlsxWriter.patch(src, wb, emptyList())
        val a = entries(src); val b = entries(out)
        assertEquals(a.keys, b.keys)
        for (k in a.keys) assertArrayEquals(k, a[k], b[k])
    }

    @Test fun valueAndStyleEditPatchOnlyWhatChanged() {
        val src = build()
        val wb = XlsxReader.read(src)
        val s = wb.sheets[0]
        val bold = wb.styles.derive(s.cell(1, 1)!!.style, StylePatch(bold = true, fill = ColorSpec(theme = 5, tint = 0.4)))
        val before = s.cell(1, 1)!!
        val ops = listOf(
            EditOp.SetCells(0, listOf(CellChange(1, 1, before, before.copy(style = bold)))),
            EditOp.SetCells(0, listOf(CellChange(2, 0, s.cell(2, 0), wb.parseInput("Hello <world>", 0)))),
            EditOp.SetCells(0, listOf(CellChange(5, 3, null, wb.parseInput("42", 0))))
        )
        ops.forEach { XlsxEdits.apply(wb, it) }
        val out = XlsxWriter.patch(src, wb, ops)
        val e = entries(out)
        assertArrayEquals(media, e["xl/media/image1.png"])
        assertArrayEquals(shared.toByteArray(), e["xl/sharedStrings.xml"])
        assertNull("calcChain dropped", e["xl/calcChain.xml"])
        assertFalse(String(e["[Content_Types].xml"]!!).contains("calcChain"))
        assertTrue(String(e["xl/workbook.xml"]!!).contains("fullCalcOnLoad=\"1\""))
        val xml = String(e["xl/worksheets/sheet1.xml"]!!)
        // Untouched formula cell kept verbatim.
        assertTrue(xml.contains("<c r=\"C2\"><f>B2*2</f><v>25</v></c>"))
        assertTrue(xml.contains("<c r=\"B2\" s=\"$bold\"><v>12.5</v></c>"))
        assertTrue(xml.contains("Hello &lt;world&gt;"))
        assertTrue(xml.contains("<row r=\"6\"><c r=\"D6\"><v>42</v></c></row>"))
        assertTrue(xml.contains("<dimension ref=\"A1:D6\"/>"))

        val re = XlsxReader.read(out)
        val rs = re.sheets[0]
        val st = re.styles.resolve(rs.cell(1, 1)!!.style)
        assertTrue(st.bold)
        assertEquals("0.0", st.numFmtCode)       // kept the base xf's number format
        assertNotNull(st.fillArgb)
        assertEquals("Hello <world>", re.display(rs.cell(2, 0)!!))
        assertEquals("42", re.display(rs.cell(5, 3)!!))
        val styleXml = String(e["xl/styles.xml"]!!)
        assertTrue(styleXml.contains("<cellXfs count=\"4\">"))
        assertTrue(styleXml.contains("<fonts count=\"3\">"))
        assertTrue(styleXml.contains("<dxfs count=\"0\"/>"))
    }

    @Test fun insertAndDeleteRowsShiftEverything() {
        val src = build()
        val wb = XlsxReader.read(src)
        val ins = EditOp.Lines(0, Axis.ROW, 1, 2)    // two rows before row 2
        XlsxEdits.apply(wb, ins)
        assertEquals(listOf(CellRange(4, 0, 5, 0)), wb.sheets[0].merges)
        val out = XlsxWriter.patch(src, wb, listOf(ins))
        val e = entries(out)
        val xml = String(e["xl/worksheets/sheet1.xml"]!!)
        assertTrue(xml, xml.contains("<c r=\"B6\"><f>SUM(B4:B5)</f><v>15.5</v></c>"))
        assertTrue(xml.contains("<mergeCell ref=\"A5:A6\"/>"))
        assertTrue(xml.contains("sqref=\"C4:C6\""))
        assertFalse(xml.contains("<selection"))
        assertFalse(xml.contains("spans="))
        assertTrue(String(e["xl/worksheets/sheet2.xml"]!!).contains("<f>Data!B5*10</f>"))
        assertTrue(String(e["xl/workbook.xml"]!!).contains("Data!\$B\$6"))

        val re = XlsxReader.read(out).sheets[0]
        assertEquals(listOf(CellRange(4, 0, 5, 0)), re.merges)
        assertEquals("Name", XlsxReader.read(out).display(re.cell(0, 0)!!))

        // Undo restores the model exactly.
        XlsxEdits.revert(wb, ins)
        assertEquals(listOf(CellRange(2, 0, 3, 0)), wb.sheets[0].merges)
        assertEquals("7", wb.display(wb.sheets[0].cell(2, 0)!!))

        val del = EditOp.Lines(0, Axis.ROW, 2, -2)   // delete rows 3–4: removes the merge + validation shrink
        XlsxEdits.apply(wb, del)
        val e2 = entries(XlsxWriter.patch(src, wb, listOf(del)))
        val out2 = String(e2["xl/worksheets/sheet1.xml"]!!)
        assertFalse(out2.contains("<mergeCells"))
        assertTrue(out2.contains("sqref=\"C2\""))
        assertFalse(out2.contains("SUM("))
        assertTrue(String(e2["xl/worksheets/sheet2.xml"]!!).contains("<f>Data!#REF!*10</f>"))
    }

    @Test fun deleteColumnShiftsColsAndCells() {
        val src = build()
        val wb = XlsxReader.read(src)
        val del = EditOp.Lines(0, Axis.COL, 0, -1)
        XlsxEdits.apply(wb, del)
        val xml = String(entries(XlsxWriter.patch(src, wb, listOf(del)))["xl/worksheets/sheet1.xml"]!!)
        assertTrue(xml, xml.contains("<cols><col min=\"1\" max=\"2\" width=\"12\" customWidth=\"1\"/></cols>"))
        assertTrue(xml.contains("<c r=\"B2\"><f>A2*2</f><v>25</v></c>"))
        assertTrue(xml.contains("sqref=\"B2:B4\""))
    }

    @Test fun columnWidthSplitsRange() {
        val src = build()
        val wb = XlsxReader.read(src)
        val op = EditOp.ColWidth(0, 2, null, 30f)
        XlsxEdits.apply(wb, op)
        val xml = String(entries(XlsxWriter.patch(src, wb, listOf(op)))["xl/worksheets/sheet1.xml"]!!)
        assertTrue(xml, xml.contains("<col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"30.0\" customWidth=\"1\"/>"))
    }
}

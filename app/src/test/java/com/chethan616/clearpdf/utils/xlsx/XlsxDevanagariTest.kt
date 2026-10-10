package com.chethan616.clearpdf.utils.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Hindi / Devanagari in .xlsx (#28): read from shared and inline strings exactly (combining vowel
 * signs, conjuncts, nukta), keep the workbook's Devanagari font name, edit a cell to Hindi, save,
 * and read it back unchanged.
 */
class XlsxDevanagariTest {

    @Before fun useKxml() {
        XlsxReader.parserFactory = { KXmlParser() }
    }

    private val header = listOf("क्रमांक", "नाम", "पता", "विवरण")
    private val row1 = listOf("सुरेश", "छत्तीसगढ़")

    private val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1"><c r="A1" s="1" t="s"><v>0</v></c><c r="B1" s="1" t="s"><v>1</v></c><c r="C1" s="1" t="s"><v>2</v></c><c r="D1" s="1" t="s"><v>3</v></c></row>
<row r="2"><c r="A2"><v>1</v></c><c r="B2" t="s"><v>4</v></c><c r="C2" t="s"><v>5</v></c><c r="D2" t="inlineStr"><is><t>जानकारी</t></is></c></row>
</sheetData></worksheet>"""

    private val shared = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
${(header + row1).joinToString("") { "<si><t>$it</t></si>" }}
</sst>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="12"/><name val="Mangal"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
</styleSheet>"""

    private val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="सूची" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>"""

    private fun build(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""")
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", rels)
            put("xl/worksheets/sheet1.xml", sheet)
            put("xl/styles.xml", styles)
            put("xl/sharedStrings.xml", shared)
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

    @Test fun readsDevanagariExactly() {
        val wb = XlsxReader.read(build())
        val s = wb.sheets[0]
        assertEquals("सूची", s.name)
        header.forEachIndexed { c, h -> assertEquals(h, wb.display(s.cell(0, c)!!)) }
        assertEquals("सुरेश", wb.display(s.cell(1, 1)!!))
        assertEquals("छत्तीसगढ़", wb.display(s.cell(1, 2)!!))
        assertEquals("जानकारी", wb.display(s.cell(1, 3)!!))
        // The workbook's Devanagari font is kept, so the painter can ask for it by name.
        assertEquals("Mangal", wb.styles.resolve(s.cell(0, 0)!!.style).fontName)
    }

    @Test fun editsAndSavesDevanagariRoundTrip() {
        val src = build()
        val wb = XlsxReader.read(src)
        val s = wb.sheets[0]
        val newCity = "बिलासपुर"
        val ops = listOf(
            EditOp.SetCells(0, listOf(CellChange(1, 2, s.cell(1, 2), wb.parseInput(newCity, 0)))),
            EditOp.SetCells(0, listOf(CellChange(2, 1, null, wb.parseInput("महेश", 0))))
        )
        ops.forEach { XlsxEdits.apply(wb, it) }
        val out = XlsxWriter.patch(src, wb, ops)

        // Written as real UTF-8 Devanagari, not numeric entities or mojibake.
        val xml = String(entries(out)["xl/worksheets/sheet1.xml"]!!, Charsets.UTF_8)
        assertTrue(xml.contains(newCity))

        val re = XlsxReader.read(out)
        val rs = re.sheets[0]
        assertEquals(newCity, re.display(rs.cell(1, 2)!!))
        assertEquals("महेश", re.display(rs.cell(2, 1)!!))
        // Untouched Hindi cells survive unchanged.
        assertEquals("सुरेश", re.display(rs.cell(1, 1)!!))
        assertEquals("जानकारी", re.display(rs.cell(1, 3)!!))
    }
}

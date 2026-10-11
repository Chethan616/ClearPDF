package com.chethan616.clearpdf.office

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * #29: a title placeholder the slide's layout doesn't have (a title on a Blank layout) must carry
 * the master's anchor and title style before LibreOffice sees it, or LibreOffice pins it to the top
 * of a box that starts above the slide and the title is cut off.
 */
class PptxPlaceholderFixTest {

    private val ns = """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships""""

    private val master = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster $ns><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="684212" y="4487332"/><a:ext cx="8534400" cy="1507067"/></a:xfrm></p:spPr>
<p:txBody><a:bodyPr lIns="91440" tIns="45720" anchor="ctr"/><a:lstStyle/><a:p><a:r><a:t>Title</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld>
<p:txStyles><p:titleStyle><a:lvl1pPr algn="l"><a:defRPr sz="3600" cap="all"/></a:lvl1pPr></p:titleStyle><p:bodyStyle/><p:otherStyle/></p:txStyles>
</p:sldMaster>"""

    private val blankLayout = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout $ns><p:cSld name="Blank"><p:spTree/></p:cSld></p:sldLayout>"""

    private fun slide(withLayoutTitle: Boolean) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld $ns><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="3" name="Rectangle 2"/><p:cNvSpPr/><p:nvPr><p:ph type="title" idx="4294967295"/></p:nvPr></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="1809130" y="-339928"/><a:ext cx="7766050" cy="1177926"/></a:xfrm></p:spPr>
<p:txBody><a:bodyPr><a:normAutofit/></a:bodyPr><a:lstStyle/><a:p><a:r><a:rPr sz="3200"/><a:t>Pregled relevantnih standarda</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld></p:sld>""".let { if (withLayoutTitle) it else it }

    private fun rels(target: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="$target"/></Relationships>"""

    private fun deck(layout: String): File {
        val f = File.createTempFile("deck", ".pptx")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("ppt/slides/slide1.xml", slide(false))
            put("ppt/slides/_rels/slide1.xml.rels", rels("../slideLayouts/slideLayout1.xml"))
            put("ppt/slideLayouts/slideLayout1.xml", layout)
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", rels("../slideMasters/slideMaster1.xml"))
            put("ppt/slideMasters/slideMaster1.xml", master)
            put("ppt/media/image1.png", "not really a png")
        }
        return f
    }

    private fun slideXml(f: File) = ZipFile(f).use { z -> z.getInputStream(z.getEntry("ppt/slides/slide1.xml")).readBytes().decodeToString() }

    @Test fun orphanTitleInheritsTheMastersAnchorAndTitleStyle() {
        val f = deck(blankLayout)
        assertTrue(PptxPlaceholderFix.apply(f))
        val xml = slideXml(f)
        assertTrue(xml, Regex("""<a:bodyPr[^>]*anchor="ctr"""").containsMatchIn(xml))
        assertTrue(xml, xml.contains("tIns=\"45720\""))
        assertTrue(xml, Regex("""<a:lvl1pPr[^>]*><a:defRPr[^>]*cap="all"""").containsMatchIn(xml))
        // The slide's own size and box still win.
        assertTrue(xml.contains("sz=\"3200\""))
        assertTrue(xml.contains("y=\"-339928\""))
        // Untouched parts are copied through byte for byte.
        ZipFile(f).use { z -> assertTrue(z.getInputStream(z.getEntry("ppt/media/image1.png")).readBytes().decodeToString() == "not really a png") }
        f.delete()
    }

    @Test fun placeholderTheLayoutHasIsLeftAlone() {
        val layoutWithTitle = blankLayout.replace(
            "<p:spTree/>",
            """<p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="T"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p/></p:txBody></p:sp></p:spTree>"""
        )
        val f = deck(layoutWithTitle)
        assertFalse(PptxPlaceholderFix.apply(f))
        f.delete()
    }
}

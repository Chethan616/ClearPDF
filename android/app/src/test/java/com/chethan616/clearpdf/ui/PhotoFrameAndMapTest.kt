package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.notes.CropBox
import com.chethan616.clearpdf.ui.notes.MindDoc
import com.chethan616.clearpdf.ui.notes.PhotoFrame
import com.chethan616.clearpdf.ui.notes.constrain
import com.chethan616.clearpdf.ui.notes.fitInside
import com.chethan616.clearpdf.ui.notes.fits
import com.chethan616.clearpdf.ui.notes.largest
import com.chethan616.clearpdf.ui.notes.mindFromOpml
import com.chethan616.clearpdf.ui.notes.opml
import com.chethan616.clearpdf.ui.notes.outline
import com.chethan616.clearpdf.ui.notes.parseOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFrameAndMapTest {
    private val w = 4000f
    private val h = 3000f

    private fun near(a: CropBox, b: CropBox) {
        assertEquals(a.x0, b.x0, 1e-4f)
        assertEquals(a.y0, b.y0, 1e-4f)
        assertEquals(a.x1, b.x1, 1e-4f)
        assertEquals(a.y1, b.y1, 1e-4f)
    }

    @Test
    fun aCropStaysOnAStraightenedPicture() {
        assertTrue(fits(CropBox(), 0f, w, h))
        assertFalse(fits(CropBox(), 10f, w, h))
        // straightened, the whole frame shrinks about the middle and keeps its shape
        val inside = fitInside(CropBox(), 10f, w, h)
        assertTrue(fits(inside, 10f, w, h))
        assertEquals(0.5f, inside.cx, 1e-3f)
        assertEquals(4f / 3f, inside.w * w / (inside.h * h), 1e-2f)
        assertTrue(inside.w > 0.7f)
    }

    @Test
    fun aShapeIsTheLargestThatFits() {
        val square = largest(1f, CropBox(), 0f, w, h)
        assertEquals(1f, square.w * w / (square.h * h), 1e-3f)
        assertEquals(1f, square.h, 1e-3f)
        val tall = largest(9f / 16f, CropBox(), 0f, w, h)
        assertEquals(9f / 16f, tall.w * w / (tall.h * h), 1e-3f)
    }

    @Test
    fun aDraggedCropStopsAtTheEdge() {
        val moved = constrain(CropBox(0.1f, 0.1f, 0.5f, 0.5f), CropBox(0.7f, 0.1f, 1.1f, 0.5f), 0f, w, h)
        assertEquals(1f, moved.x1, 1e-3f)
        assertEquals(0.4f, moved.w, 1e-3f)
    }

    @Test
    fun turnsAndMirrorsComeRoundAgain() {
        val c = CropBox(0.1f, 0.2f, 0.5f, 0.6f)
        near(c, c.turned().turned().turned().turned())
        near(c, c.mirrored().mirrored())
        // a quarter turn puts the left edge at the top
        near(CropBox(0.4f, 0.1f, 0.8f, 0.5f), c.turned())
        assertEquals("Normal", PhotoFrame().orientation)
        assertEquals("Rotate90", PhotoFrame(turns = 1).orientation)
        assertEquals("Transverse", PhotoFrame(turns = 1, mirror = true).orientation)
        assertEquals("Transpose", PhotoFrame(turns = 3, mirror = true).orientation)
    }

    @Test
    fun aMapKeepsItsOutline() {
        val doc = MindDoc(parseOutline("Trip", "Before\n  Tickets\n  Hotel\nThere"))
        assertEquals("Trip", doc.root.text)
        assertEquals("Before\n  Tickets\n  Hotel\nThere", doc.outline())
    }

    @Test
    fun anOpmlFileIsReadAsAMap() {
        val xml = """<?xml version="1.0"?><opml version="2.0"><head><title>Trip</title></head><body>""" +
            """<outline text="Trip"><outline text="Tokyo &amp; Kyoto"><outline text="Temples"/></outline><outline title="Budget"/></outline></body></opml>"""
        val doc = mindFromOpml(xml)!!
        assertEquals("Trip", doc.root.text)
        assertEquals(listOf("Tokyo & Kyoto", "Budget"), doc.root.children.map { it.text })
        assertEquals(listOf("Temples"), doc.root.children[0].children.map { it.text })
        // several top outlines hang from the head's title
        val many = mindFromOpml("<opml><head><title>Plan</title></head><body><outline text='A'/><outline text='B'/></body></opml>")!!
        assertEquals("Plan", many.root.text)
        assertEquals(listOf("A", "B"), many.root.children.map { it.text })
        assertNull(mindFromOpml("<opml><body></body></opml>"))
        // what Artefact exports, it reads back the same
        val back = mindFromOpml(doc.opml())!!
        assertEquals(doc.outline(), back.outline())
        assertEquals(doc.root.text, back.root.text)
    }
}

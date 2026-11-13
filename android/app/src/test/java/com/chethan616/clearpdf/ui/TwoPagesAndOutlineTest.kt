package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.notes.parseOutline
import com.chethan616.clearpdf.ui.viewer.DocLayout
import com.chethan616.clearpdf.ui.viewer.PageMode
import com.chethan616.clearpdf.ui.viewer.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPagesAndOutlineTest {
    private val pages = List(5) { PageSize(600f, 800f) }

    @Test
    fun twoPagesShareTheWidthInRows() {
        val l = DocLayout(pages, PageMode.Continuous, 1000f, 1600f, gap = 20f, margin = 10f, padTop = 0f, padBottom = 0f, columns = 2)
        // (1000 less the margins and the gap between the two) halved
        assertEquals(480f, l.rects[0].width, 0.5f)
        assertEquals(l.rects[0].top, l.rects[1].top, 0f)
        assertEquals(20f, l.rects[1].left - l.rects[0].right, 0.5f)
        assertTrue(l.rects[2].top > l.rects[0].bottom)
        // the odd page out stands alone in the last row, centred
        assertEquals(500f, (l.rects[4].left + l.rects[4].right) / 2f, 0.5f)
    }

    @Test
    fun aPointFindsThePageOfItsColumn() {
        val l = DocLayout(pages, PageMode.Continuous, 1000f, 1600f, gap = 20f, margin = 10f, padTop = 0f, padBottom = 0f, columns = 2)
        assertEquals(0, l.nearestPage(100f, 100f))
        assertEquals(1, l.nearestPage(900f, 100f))
        assertEquals(1, l.pageAt(l.rects[1].left + 5f, l.rects[1].top + 5f))
        assertEquals(3, l.nearestPage(900f, l.rects[2].top + 10f))
        assertEquals(4, l.nearestPage(500f, l.rects[4].top + 10f))
        assertEquals(0..1, l.visible(0f, 0f, 1000f, 100f))
        // one column lays out as it always did
        val one = DocLayout(pages, PageMode.Continuous, 1000f, 1600f, gap = 20f, margin = 10f, padTop = 0f, padBottom = 0f)
        assertEquals(980f, one.rects[0].width, 0.5f)
        assertEquals(1, one.nearestPage(500f, one.rects[1].top + 10f))
    }

    @Test
    fun anOutlineBecomesATree() {
        val root = parseOutline("Trip", "# Before\n- Tickets\n  - Train\n  - Hotel\n- Pack\n# There\n1. Museum\n\n**Food**")
        assertEquals("Trip", root.text)
        assertEquals(listOf("Before", "There"), root.children.map { it.text })
        val before = root.children[0]
        assertEquals(listOf("Tickets", "Pack"), before.children.map { it.text })
        assertEquals(listOf("Train", "Hotel"), before.children[0].children.map { it.text })
        assertEquals(listOf("Museum", "Food"), root.children[1].children.map { it.text })
        // no headings: indentation alone nests
        val plain = parseOutline("", "Fruit\n  Apples\n  Pears\nBread")
        assertEquals(listOf("Fruit", "Bread"), plain.children.map { it.text })
        assertEquals(listOf("Apples", "Pears"), plain.children[0].children.map { it.text })
    }
}

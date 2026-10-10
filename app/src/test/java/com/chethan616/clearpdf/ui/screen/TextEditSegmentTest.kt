package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * In-place text editing rewrites only what changed: a selection edits just its words, and the
 * save path gets a box around the changed words plus the line box (so the rest of the line can
 * move by the change in width), never the whole line.
 */
class TextEditSegmentTest {

    private val line = "Invoice number 1042 due on Friday"

    /** One normalized unit per character, so a box's edges name its character range. */
    private fun markup(text: String) = PdfMarkup.TextEditMarkup(
        id = 1L, blockId = "b", rect = Rect(0f, 10f, line.length.toFloat(), 20f), baseline = 18f, fontSize = 10f,
        original = line, text = text, color = Color.Black, background = Color.White,
        bold = false, italic = false, serif = true, mono = false,
        charLefts = FloatArray(line.length) { it.toFloat() / line.length },
        charRights = FloatArray(line.length) { (it + 1f) / line.length }
    )

    private fun charIndex(x: Float) = Math.round(x * line.length)

    @Test fun diffFindsTheChangedSpan() {
        val (p, oEnd, cEnd) = textDiff(line, "Invoice number 2077-B due on Friday")
        assertEquals(15, p)
        assertEquals(19, oEnd)
        assertEquals(21, cEnd)
    }

    @Test fun selectionMapsIntoAnEditedLine() {
        val edited = "Invoice number 2077-B due on Friday"
        // "Friday" sits after the earlier change, so it shifts by the change in length.
        val fri = line.indexOf("Friday")
        val r = selectionInCurrent(line, edited, fri, fri + 6)
        assertEquals("Friday", edited.substring(r.first, r.last + 1))
        // Touching the earlier change grows the selection to include all of it.
        val num = line.indexOf("1042")
        val g = selectionInCurrent(line, edited, num, num + 2)
        assertEquals("2077-B", edited.substring(g.first, g.last + 1))
        // Unedited line: the selection is unchanged.
        val same = selectionInCurrent(line, line, 0, 7)
        assertEquals("Invoice", line.substring(same.first, same.last + 1))
    }

    @Test fun onlyTheChangedWordIsRewritten() {
        val r = markup("Invoice number 2077-B due on Friday").toTextReplace(line.length.toFloat(), 30f)!!
        assertEquals("2077-B", r.text)
        assertEquals(line.indexOf("1042"), charIndex(r.left))
        assertEquals(line.indexOf("1042") + 4, charIndex(r.right))
        // The line box stays the whole line, so the words after it can move.
        assertEquals(0f, r.lineLeft, 1e-6f)
        assertEquals(1f, r.lineRight, 1e-6f)
    }

    @Test fun partOfAWordGrowsToTheWord() {
        val r = markup("Invoice number 1042 due on Monday").toTextReplace(line.length.toFloat(), 30f)!!
        assertEquals("Monday", r.text)
        assertEquals(line.indexOf("Friday"), charIndex(r.left))
    }

    @Test fun insertingBetweenWordsAnchorsOnANeighbour() {
        val r = markup("Invoice number 1042 is due on Friday").toTextReplace(line.length.toFloat(), 30f)!!
        // The inserted word rides on the word it was typed before, so there is a glyph to copy.
        assertEquals("is due", r.text)
        assertEquals(line.indexOf("due"), charIndex(r.left))
    }

    @Test fun unchangedTextHasNoEdit() {
        assertNull(markup(line).toTextReplace(line.length.toFloat(), 30f))
    }

    @Test fun rewritingEverythingIsAWholeLineEdit() {
        val r = markup("Completely different").toTextReplace(line.length.toFloat(), 30f)!!
        assertEquals("Completely different", r.text)
        assertEquals(r.lineLeft, r.left, 1e-6f)
        assertEquals(r.lineRight, r.right, 1e-6f)
    }
}

package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.geometry.Rect
import com.chethan616.clearpdf.ui.viewmodel.OcrTextBlock
import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerInternalsTest {

    @Test
    fun partialCharacterGeometryKeepsHighlightWithinSelectedWord() {
        val text = "A full word remains selected"
        val block = OcrTextBlock(
            id = "line-1",
            text = text,
            left = 0.1f,
            top = 0.2f,
            right = 0.9f,
            bottom = 0.3f,
            // A malformed/incomplete OCR map used to make the range fall back to the entire line.
            charLefts = floatArrayOf(0.10f, 0.13f, 0.16f),
            charRights = floatArrayOf(0.12f, 0.15f, 0.18f)
        )

        val wordStart = text.indexOf("word")
        val rect = ocrTextRangeToRect(
            block,
            OcrTextRange(block.id, wordStart, wordStart + "word".length),
            Rect(0f, 0f, 1000f, 1000f)
        )

        assertTrue("highlight should start inside the text line", rect.left > 100f)
        assertTrue("highlight should end before the line ends", rect.right < 900f)
        assertEquals(4f * 800f / text.length, rect.width, 0.01f)
        assertEquals(200f, rect.top, 0.01f)
        assertEquals(300f, rect.bottom, 0.01f)
    }

    @Test
    fun completeCharacterGeometryUsesExclusiveRangeEnd() {
        val block = OcrTextBlock(
            id = "line-2",
            text = "hello world",
            left = 0.1f,
            top = 0.2f,
            right = 0.9f,
            bottom = 0.3f,
            charLefts = FloatArray(11) { 0.1f + it * 0.05f },
            charRights = FloatArray(11) { 0.15f + it * 0.05f }
        )

        val rect = ocrTextRangeToRect(
            block,
            OcrTextRange(block.id, 6, 11),
            Rect(0f, 0f, 1000f, 1000f)
        )

        assertEquals(400f, rect.left, 0.01f)
        assertEquals(650f, rect.right, 0.01f)
    }
}

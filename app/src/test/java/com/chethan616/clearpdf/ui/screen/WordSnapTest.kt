package com.chethan616.clearpdf.ui.screen

import com.chethan616.clearpdf.ui.viewmodel.OcrTextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class WordSnapTest {
    private val line = "Job Description Data Engineering"

    private fun snap(start: Int, end: Int): String {
        val r = OcrTextRange("b", start, end).snappedToWords(line)
        return line.substring(r.start, r.end)
    }

    @Test fun partialWordGrowsToWholeWord() {
        // "Engin" selected → whole "Engineering"
        val s = line.indexOf("Engin")
        assertEquals("Engineering", snap(s, s + 5))
    }

    @Test fun midWordStartAndEndGrowAcrossWords() {
        // "cription Da" → "Description Data"
        val s = line.indexOf("cription")
        assertEquals("Description Data", snap(s, s + "cription Da".length))
    }

    @Test fun surroundingSpacesAreTrimmed() {
        val s = line.indexOf(" Data ")
        assertEquals("Data", snap(s, s + 6))
    }

    @Test fun wholeWordIsUnchanged() {
        val s = line.indexOf("Job")
        assertEquals("Job", snap(s, s + 3))
    }

    @Test fun apostropheAndHyphenStayInWord() {
        val text = "it's a well-known fact"
        val s = text.indexOf("know")
        val r = OcrTextRange("b", s, s + 2).snappedToWords(text)
        assertEquals("well-known", text.substring(r.start, r.end))
    }
}

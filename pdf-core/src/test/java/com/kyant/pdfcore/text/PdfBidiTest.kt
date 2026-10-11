package com.kyant.pdfcore.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** #72: Arabic copied from a PDF must paste in reading order, not reversed. */
class PdfBidiTest {

    @Test fun pureArabicLineReadsInOrder() {
        val logical = "هي أصل تمتلكه المنشأة"
        assertEquals(logical, PdfBidi.visualToLogical(logical.reversed()))
    }

    @Test fun numbersInsideArabicKeepTheirOrder() {
        // Painted right to left: the words reversed, the number left as is.
        val visual = "هينج 120 رعس"
        assertEquals("سعر 120 جنيه", PdfBidi.visualToLogical(visual))
    }

    @Test fun arabicInsideEnglishIsReversedInPlace() {
        assertEquals("Price: سعر", PdfBidi.visualToLogical("Price: رعس"))
    }

    @Test fun bracketsMirror() {
        val logical = "البضاعة (المشتريات)"
        // Visual: whole line reversed, brackets as painted (mirrored glyphs read as the swapped char).
        val visual = logical.reversed().map { when (it) { '(' -> ')'; ')' -> '('; else -> it } }.joinToString("")
        assertEquals(logical, PdfBidi.visualToLogical(visual))
    }

    @Test fun latinTextIsUntouched() {
        assertEquals("Hello world (test)", PdfBidi.visualToLogical("Hello world (test)"))
    }

    @Test fun presentationFormsBecomePlainLetters() {
        // U+FE8E ARABIC LETTER ALEF FINAL FORM, U+FEE3 MEEM INITIAL FORM
        assertEquals("ما", PdfBidi.visualToLogical("\uFE8E\uFEE3"))
    }

    @Test fun searchFindsArabicInVisualText() {
        val visualLine = "هي أصل تمتلكه".reversed()
        val forms = PdfBidi.searchForms("أصل")
        assert(forms.any { visualLine.contains(it) })
    }
}

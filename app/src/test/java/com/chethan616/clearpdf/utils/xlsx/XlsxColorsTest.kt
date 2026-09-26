package com.chethan616.clearpdf.utils.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxColorsTest {

    private fun rgb(argb: Int) = XlsxColors.hex6(argb)

    @Test fun tintLightensAccent1LikeExcel() {
        // Excel's "Blue, Accent 1, Lighter 40%" for 4472C4 is 8FAADC.
        val c = XlsxColors.applyTint(0xFF4472C4.toInt(), 0.3999755851924192)
        assertTrue(rgb(c), XlsxColors.near(c, 0xFF8FAADC.toInt(), 2))
    }

    @Test fun tintDarkensAccent1LikeExcel() {
        // "Darker 25%" for 4472C4 is 2F5597.
        val c = XlsxColors.applyTint(0xFF4472C4.toInt(), -0.249977111117893)
        assertTrue(rgb(c), XlsxColors.near(c, 0xFF2F5597.toInt(), 2))
    }

    @Test fun whiteDarker5Percent() {
        val c = XlsxColors.applyTint(0xFFFFFFFF.toInt(), -0.0499893185216834)
        assertTrue(rgb(c), XlsxColors.near(c, 0xFFF2F2F2.toInt(), 1))
    }

    @Test fun themeIndexMapsLightDarkSwapped() {
        // theme="1" is dk1 (black text), theme="0" is lt1 (white background).
        assertEquals(0xFF000000.toInt(), ColorSpec(theme = 1).resolve(XlsxColors.DefaultTheme, null))
        assertEquals(0xFFFFFFFF.toInt(), ColorSpec(theme = 0).resolve(XlsxColors.DefaultTheme, null))
        assertEquals(0xFF4472C4.toInt(), ColorSpec(theme = 4).resolve(XlsxColors.DefaultTheme, null))
    }

    @Test fun indexedPaletteAndSystemColours() {
        assertEquals(0xFFFF0000.toInt(), ColorSpec(indexed = 10).resolve(XlsxColors.DefaultTheme, null))
        assertEquals(0xFFC0C0C0.toInt(), ColorSpec(indexed = 22).resolve(XlsxColors.DefaultTheme, null))
        assertNull(ColorSpec(indexed = 64).resolve(XlsxColors.DefaultTheme, null))
        val custom = IntArray(64) { 0xFF123456.toInt() }
        assertEquals(0xFF123456.toInt(), ColorSpec(indexed = 5).resolve(XlsxColors.DefaultTheme, custom))
    }

    @Test fun rgbAttributeParsesArgbAndRgb() {
        assertEquals(0xFF00B050.toInt(), ColorSpec.fromAttrs(mapOf("rgb" to "FF00B050"))!!.resolve(XlsxColors.DefaultTheme, null))
        // Some producers write a zero alpha; Excel treats it as opaque.
        assertEquals(0xFF00B050.toInt(), ColorSpec.fromAttrs(mapOf("rgb" to "0000B050"))!!.resolve(XlsxColors.DefaultTheme, null))
    }

    @Test fun colorSpecXmlKeepsThemeAndTint() {
        assertEquals("<color theme=\"4\" tint=\"0.4\"/>", ColorSpec(theme = 4, tint = 0.4).toXml("color"))
        assertEquals("<fgColor rgb=\"FFFF0000\"/>", ColorSpec.ofArgb(0xFFFF0000.toInt()).toXml("fgColor"))
    }
}

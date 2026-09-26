package com.chethan616.clearpdf.ui.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportTransformTest {

    private fun assertOffset(expected: Offset, actual: Offset, eps: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test
    fun identityAtScaleOne() {
        val t = PdfViewportTransform(containerWidth = 1000f, scale = 1f, offsetX = 0f)
        assertOffset(Offset(120f, 340f), t.pageToScreen(Offset(20f, 40f), pageOrigin = Offset(100f, 300f)))
    }

    @Test
    fun scalesAboutTopCentre() {
        val t = PdfViewportTransform(containerWidth = 1000f, scale = 2f, offsetX = 0f)
        // The horizontal centre stays put, the top edge stays put.
        assertOffset(Offset(500f, 0f), t.layerToScreen(Offset(500f, 0f)))
        // Left edge moves out by half the container; y doubles.
        assertOffset(Offset(-500f, 200f), t.layerToScreen(Offset(0f, 100f)))
    }

    @Test
    fun appliesTranslationAfterScale() {
        val t = PdfViewportTransform(containerWidth = 800f, scale = 2.5f, offsetX = 120f)
        // (x - 400) * 2.5 + 400 + 120
        assertOffset(Offset((100f - 400f) * 2.5f + 520f, 50f * 2.5f), t.layerToScreen(Offset(100f, 50f)))
    }

    @Test
    fun screenToPageIsExactInverse() {
        val t = PdfViewportTransform(containerWidth = 1080f, scale = 3.2f, offsetX = -410f)
        val origin = Offset(0f, 2231.5f)
        listOf(Offset(0f, 0f), Offset(537.25f, 811f), Offset(1080f, 1500f)).forEach { p ->
            assertOffset(p, t.screenToPage(t.pageToScreen(p, origin), origin), eps = 1e-2f)
        }
    }

    @Test
    fun rectMapsCorners() {
        val t = PdfViewportTransform(containerWidth = 1000f, scale = 2f, offsetX = 10f)
        val r = t.pageRectToScreen(Rect(100f, 10f, 200f, 30f), pageOrigin = Offset(0f, 100f))
        assertEquals((100f - 500f) * 2f + 510f, r.left, 1e-3f)
        assertEquals(220f, r.top, 1e-3f)
        assertEquals((200f - 500f) * 2f + 510f, r.right, 1e-3f)
        assertEquals(260f, r.bottom, 1e-3f)
    }
}

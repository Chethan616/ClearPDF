package com.chethan616.clearpdf.imageeditor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening any image used to crash: the default pixel budget overflowed and the sample hit 0. */
class ImageIoSampleSizeTest {

    @Test fun defaultPixelBudgetDoesNotOverflow() {
        // The image editor's open path: only a dimension cap, pixel budget left at its default.
        val s = ImageIo.sampleSize(4000, 3000, maxDim = 2048, maxPixels = Long.MAX_VALUE)
        assertEquals(1, s)
    }

    @Test fun downsamplesHugeImagesToTheDimensionBudget() {
        val s = ImageIo.sampleSize(16000, 12000, maxDim = 2048, maxPixels = Long.MAX_VALUE)
        assertTrue(s > 1)
        assertTrue(16000 / s <= 4096)
    }

    @Test fun respectsAPixelBudget() {
        val s = ImageIo.sampleSize(8000, 8000, maxDim = Int.MAX_VALUE, maxPixels = 4_000_000)
        assertTrue((8000L / s) * (8000L / s) <= 16_000_000)
    }

    @Test fun neverReturnsZeroEvenForAbsurdInputs() {
        assertTrue(ImageIo.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1) > 0)
        assertEquals(1, ImageIo.sampleSize(0, 100, 10, 10))
    }
}

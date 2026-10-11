package com.kyant.ocrcore

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Uses [primary] when the build supplies one (the play / foss builds pass the bundled ML Kit
 * engine: higher real-world accuracy, small footprint) and transparently falls back to the fully
 * open-source Tesseract4Android engine if it fails to initialize or throws during recognition
 * (e.g. on a device whose OEM image is missing pieces ML Kit's TFLite runtime needs). The F-Droid
 * build passes none and uses Tesseract only. Every engine is 100% on-device.
 */
class OcrServiceImpl(
    private val primary: (suspend (Bitmap) -> OcrPageResult)? = null
) : OcrService {

    /** Sticky per-process: once the primary engine is confirmed broken on this device, stop retrying it. */
    @Volatile private var primaryKnownBroken = false

    override suspend fun recognize(context: Context, bitmap: Bitmap): OcrPageResult {
        val engine = primary
        if (engine != null && !primaryKnownBroken) {
            runCatching { engine(bitmap) }
                .onSuccess { return it }
                .onFailure { e ->
                    Log.w(TAG, "Primary OCR engine unavailable, falling back to Tesseract4Android", e)
                    primaryKnownBroken = true
                }
        }
        return TesseractOcrEngine.recognize(context, bitmap)
    }

    private companion object {
        const val TAG = "OcrService"
    }
}

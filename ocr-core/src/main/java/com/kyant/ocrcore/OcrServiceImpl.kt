package com.kyant.ocrcore

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Prefers the bundled ML Kit engine (higher real-world accuracy, small footprint);
 * transparently falls back to the fully open-source Tesseract4Android engine if ML Kit
 * fails to initialize or throws during recognition (e.g. on a device whose OEM image is
 * missing pieces ML Kit's TFLite runtime needs). Both engines are 100% on-device.
 */
class OcrServiceImpl : OcrService {

    /** Sticky per-process: once ML Kit is confirmed broken on this device, stop retrying it. */
    @Volatile private var mlKitKnownBroken = false

    override suspend fun recognize(context: Context, bitmap: Bitmap): OcrPageResult {
        if (!mlKitKnownBroken) {
            runCatching { MlKitOcrEngine.recognize(bitmap) }
                .onSuccess { return it }
                .onFailure { e ->
                    Log.w(TAG, "ML Kit OCR unavailable, falling back to Tesseract4Android", e)
                    mlKitKnownBroken = true
                }
        }
        return TesseractOcrEngine.recognize(context, bitmap)
    }

    private companion object {
        const val TAG = "OcrService"
    }
}

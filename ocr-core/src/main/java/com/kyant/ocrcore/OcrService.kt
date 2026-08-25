package com.kyant.ocrcore

import android.content.Context
import android.graphics.Bitmap

/** A single recognized word, in bitmap-normalized (0..1) coordinates. */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrPageResult(
    val words: List<OcrWord>,
    val engineUsed: String
)

/**
 * Fully on-device text recognition. No implementation ever makes a network call —
 * the ML Kit model ships inside the app (not the Play-Services-downloaded variant)
 * and Tesseract's language data is bundled as an asset.
 */
interface OcrService {
    /** Recognizes text in [bitmap]. Word boxes are normalized to the bitmap's own size (0..1). */
    suspend fun recognize(context: Context, bitmap: Bitmap): OcrPageResult
}

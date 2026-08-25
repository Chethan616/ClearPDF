package com.kyant.ocrcore

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bundled ML Kit text recognizer (`com.google.mlkit:text-recognition`, NOT the
 * Play-Services-backed variant) — the model ships inside the APK, so recognition
 * never needs Play Services or a network call, and works even on devices without GMS.
 */
internal object MlKitOcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): OcrPageResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result: Text -> cont.resume(result) }
                .addOnFailureListener { error -> cont.resumeWithException(error) }
        }

        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)
        val words = buildList {
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val box = element.boundingBox ?: continue
                        if (element.text.isEmpty()) continue
                        add(
                            OcrWord(
                                text = element.text,
                                left = (box.left / w).coerceIn(0f, 1f),
                                top = (box.top / h).coerceIn(0f, 1f),
                                right = (box.right / w).coerceIn(0f, 1f),
                                bottom = (box.bottom / h).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        }
        return OcrPageResult(words, engineUsed = "mlkit")
    }
}

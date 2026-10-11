package com.chethan616.clearpdf.ocr

import android.graphics.Bitmap
import com.kyant.ocrcore.OcrPageResult

/** play / foss builds: the bundled ML Kit recognizer first, Tesseract as the fallback. */
internal object OcrFlavor {
    val primaryEngine: (suspend (Bitmap) -> OcrPageResult)? = { MlKitOcrEngine.recognize(it) }
}

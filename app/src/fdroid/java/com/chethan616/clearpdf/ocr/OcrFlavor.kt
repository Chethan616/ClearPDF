package com.chethan616.clearpdf.ocr

import android.graphics.Bitmap
import com.kyant.ocrcore.OcrPageResult

/** F-Droid build: free software only, so OCR is Tesseract4Android alone. */
internal object OcrFlavor {
    val primaryEngine: (suspend (Bitmap) -> OcrPageResult)? = null
}

package com.chethan616.clearpdf.data.repository

import com.kyant.pdfcore.compressor.PdfCompressor
import com.kyant.pdfcore.compressor.PdfCompressorImpl
import com.kyant.pdfcore.creator.PdfCreator
import com.kyant.pdfcore.creator.PdfCreatorImpl
import com.kyant.pdfcore.merger.PdfMerger
import com.kyant.pdfcore.merger.PdfMergerImpl
import com.kyant.pdfcore.viewer.PdfViewer
import com.kyant.pdfcore.viewer.PdfViewerImpl

object PdfServiceLocator {
    val pdfViewer: PdfViewer by lazy { PdfViewerImpl() }
    val pdfMerger: PdfMerger by lazy { PdfMergerImpl() }
    val pdfCreator: PdfCreator by lazy { PdfCreatorImpl() }
    val pdfCompressor: PdfCompressor by lazy { PdfCompressorImpl() }
}

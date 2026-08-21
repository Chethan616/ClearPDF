package com.chethan616.clearpdf.data.repository

import com.kyant.pdfcore.compressor.PdfCompressor
import com.kyant.pdfcore.compressor.PdfCompressorImpl
import com.kyant.pdfcore.converter.PdfConverter
import com.kyant.pdfcore.converter.PdfConverterImpl
import com.kyant.pdfcore.creator.PdfCreator
import com.kyant.pdfcore.creator.PdfCreatorImpl
import com.kyant.pdfcore.editor.PdfEditor
import com.kyant.pdfcore.editor.PdfEditorImpl
import com.kyant.pdfcore.merger.PdfMerger
import com.kyant.pdfcore.merger.PdfMergerImpl
import com.kyant.pdfcore.splitter.PdfSplitter
import com.kyant.pdfcore.splitter.PdfSplitterImpl
import com.kyant.pdfcore.viewer.PdfViewer
import com.kyant.pdfcore.viewer.PdfViewerImpl
import com.kyant.pdfcore.text.PdfTextService
import com.kyant.pdfcore.text.PdfTextServiceImpl

object PdfServiceLocator {
    val pdfViewer: PdfViewer by lazy { PdfViewerImpl() }
    val pdfMerger: PdfMerger by lazy { PdfMergerImpl() }
    val pdfCreator: PdfCreator by lazy { PdfCreatorImpl() }
    val pdfCompressor: PdfCompressor by lazy { PdfCompressorImpl() }
    val pdfSplitter: PdfSplitter by lazy { PdfSplitterImpl() }
    val pdfEditor: PdfEditor by lazy { PdfEditorImpl() }
    val pdfConverter: PdfConverter by lazy { PdfConverterImpl() }
    val pdfTextService: PdfTextService by lazy { PdfTextServiceImpl() }
}

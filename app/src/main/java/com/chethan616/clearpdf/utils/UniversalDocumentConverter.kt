package com.chethan616.clearpdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.hslf.usermodel.HSLFSlideShow

object UniversalDocumentConverter {

    fun isPdf(context: Context, uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null && type.contains("pdf", ignoreCase = true)) return true
        val name = getFileName(context, uri)
        return name.endsWith(".pdf", ignoreCase = true)
    }

    fun convertToPdf(context: Context, sourceUri: Uri): Uri {
        val mimeType = context.contentResolver.getType(sourceUri) ?: ""
        val name = getFileName(context, sourceUri).lowercase()

        return when {
            mimeType.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp") -> {
                convertImageToPdf(context, sourceUri)
            }
            mimeType.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".log") -> {
                convertTextToPdf(context, sourceUri)
            }
            else -> {
                // Fallback for docx/xlsx/other formats: extract readable text or convert bitmap snapshot
                convertGenericToPdf(context, sourceUri)
            }
        }
    }

    private fun convertImageToPdf(context: Context, sourceUri: Uri): Uri {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Unable to open image stream")
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalStateException("Invalid image file")

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDoc.finishPage(page)

        val outputFile = createTempPdfFile(context, "Image_Converted")
        FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        bitmap.recycle()

        return Uri.fromFile(outputFile)
    }

    private fun convertTextToPdf(context: Context, sourceUri: Uri): Uri {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Unable to open text file")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()

        val pdfDoc = PdfDocument()
        val pageWidth = 595 // A4 width
        val pageHeight = 842 // A4 height
        val margin = 40f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
        }

        var pageNumber = 1
        var y = margin + 20f
        var currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = currentPage.canvas

        for (line in lines) {
            if (y > pageHeight - margin) {
                pdfDoc.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = currentPage.canvas
                y = margin + 20f
            }
            canvas.drawText(line.take(90), margin, y, paint)
            y += 18f
        }
        pdfDoc.finishPage(currentPage)

        val outputFile = createTempPdfFile(context, "Text_Converted")
        FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()

        return Uri.fromFile(outputFile)
    }

    private fun convertGenericToPdf(context: Context, sourceUri: Uri): Uri {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Unable to open file")
        val bytes = inputStream.use { it.readBytes() }
        val fileName = getFileName(context, sourceUri).lowercase()
        val structuredText = when {
            fileName.endsWith(".docx") || fileName.endsWith(".pptx") || fileName.endsWith(".xlsx") || fileName.endsWith(".odt") ->
                extractOfficeText(bytes.inputStream(), fileName)
            fileName.endsWith(".doc") || fileName.endsWith(".xls") || fileName.endsWith(".ppt") ->
                extractLegacyOfficeText(bytes.inputStream(), fileName)
            else -> emptyList()
        }
        val contentStr = if (structuredText.isNotEmpty()) {
            structuredText.joinToString("\n")
        } else {
            String(bytes, Charsets.UTF_8).filter { it.isISOControl().not() || it == '\n' || it == '\r' || it == '\t' }
        }

        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.DKGRAY
            typeface = Typeface.DEFAULT
        }

        val lines = contentStr.lines().take(500)
        var pageNumber = 1
        var y = margin + 20f
        var currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = currentPage.canvas

        for (line in lines) {
            if (y > pageHeight - margin) {
                pdfDoc.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = currentPage.canvas
                y = margin + 20f
            }
            canvas.drawText(line.take(85), margin, y, paint)
            y += 16f
        }
        pdfDoc.finishPage(currentPage)

        val outputFile = createTempPdfFile(context, "Document_Converted")
        FileOutputStream(outputFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()

        return Uri.fromFile(outputFile)
    }

    /**
     * Office Open XML files are ZIP containers. Reading their text nodes keeps the app
     * dependency-light while still producing a useful, searchable PDF preview for Word,
     * PowerPoint and Excel files.
     */
    private fun extractOfficeText(input: java.io.InputStream, fileName: String): List<String> {
        val lines = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !isRelevantOfficeEntry(entry.name, fileName)) continue
                runCatching {
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        setInput(zip, "UTF-8")
                    }
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.TEXT) {
                            parser.text.trim().takeIf { it.isNotEmpty() }?.let { lines += it }
                        }
                        event = parser.next()
                    }
                }
                zip.closeEntry()
                if (lines.size >= 2000) break
            }
        }
        return lines.take(2000)
    }

    private fun isRelevantOfficeEntry(entryName: String, fileName: String): Boolean {
        return when {
            fileName.endsWith(".docx") -> entryName.startsWith("word/") && entryName.endsWith(".xml") &&
                !entryName.contains("styles") && !entryName.contains("settings") && !entryName.contains("rels")
            fileName.endsWith(".pptx") -> entryName.startsWith("ppt/slides/") && entryName.endsWith(".xml")
            fileName.endsWith(".xlsx") -> (entryName == "xl/sharedStrings.xml" || entryName.startsWith("xl/worksheets/")) && entryName.endsWith(".xml")
            else -> entryName.endsWith(".xml")
        }
    }

    private fun extractLegacyOfficeText(input: java.io.InputStream, fileName: String): List<String> {
        return try {
            when {
                fileName.endsWith(".doc") -> WordExtractor(input).use { it.paragraphText.toList() }
                fileName.endsWith(".xls") -> HSSFWorkbook(input).use { workbook ->
                    workbook.iterator().asSequence().flatMap { sheet ->
                        sheet.iterator().asSequence().map { row ->
                            row.iterator().asSequence().joinToString("\t") { cell -> cell.toString() }
                        }
                    }.toList()
                }
                fileName.endsWith(".ppt") -> HSLFSlideShow(input).use { slideshow ->
                    slideshow.slides.flatMap { slide ->
                        slide.shapes.mapNotNull { shape ->
                            (shape as? org.apache.poi.sl.usermodel.TextShape<*, *>)?.text
                        }
                    }
                }
                else -> emptyList()
            }.filter { it.isNotBlank() }.take(2000)
        } catch (_: Throwable) {
            // Some malformed legacy files are still better served by the readable-byte fallback.
            emptyList()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment ?: "document"
    }

    private fun createTempPdfFile(context: Context, prefix: String): File {
        val dir = File(context.cacheDir, "converted_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${prefix}_${System.currentTimeMillis()}.pdf")
    }
}

package com.kyant.pdfcore.image

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import com.kyant.pdfcore.internal.PdfBox
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage

/**
 * Where a page draws its pictures, as rectangles in the page's displayed space (rotation and crop
 * box applied) normalised to 0..1, so they line up with a rendered page bitmap of any size.
 *
 * Night mode turns a page's colours over; the viewer uses these to put photographs back the way
 * they were printed, the way Gander (MIT) does it for pdf.js.
 */
object PdfImageRegions {

    fun pages(context: Context, uri: Uri, pages: Collection<Int>): Map<Int, List<RectF>> {
        PdfBox.ensureInitialized(context)
        val out = HashMap<Int, List<RectF>>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                for (i in pages) {
                    if (i !in 0 until doc.numberOfPages) continue
                    out[i] = runCatching { page(doc.getPage(i)) }.getOrDefault(emptyList())
                }
            }
        }
        return out
    }

    fun page(page: PDPage): List<RectF> {
        val box = page.cropBox
        val x0 = box.lowerLeftX
        val y0 = box.lowerLeftY
        val w = box.width.takeIf { it > 0f } ?: return emptyList()
        val h = box.height.takeIf { it > 0f } ?: return emptyList()
        val rotation = ((page.rotation % 360) + 360) % 360
        val found = ArrayList<RectF>()

        val engine = object : PDFGraphicsStreamEngine(page) {
            override fun drawImage(pdImage: PDImage) {
                val m = graphicsState.currentTransformationMatrix
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                // The image fills the unit square of its transformation matrix.
                for ((ux, uy) in arrayOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                    val px = m.scaleX * ux + m.shearX * uy + m.translateX
                    val py = m.shearY * ux + m.scaleY * uy + m.translateY
                    // Normalised display coordinates, top-left origin.
                    val nx = (px - x0) / w
                    val ny = (py - y0) / h
                    val (dx, dy) = when (rotation) {
                        90 -> ny to nx
                        180 -> (1f - nx) to ny
                        270 -> (1f - ny) to (1f - nx)
                        else -> nx to (1f - ny)
                    }
                    minX = minOf(minX, dx); maxX = maxOf(maxX, dx)
                    minY = minOf(minY, dy); maxY = maxOf(maxY, dy)
                }
                val r = RectF(minX.coerceIn(0f, 1f), minY.coerceIn(0f, 1f), maxX.coerceIn(0f, 1f), maxY.coerceIn(0f, 1f))
                // Tiny pictures (bullets, rules) and slivers aren't worth a pass of their own.
                if (r.width() * r.height() > 0.0004f && r.width() > 0.01f && r.height() > 0.01f) found.add(r)
            }

            override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {}
            override fun clip(windingRule: Path.FillType) {}
            override fun moveTo(x: Float, y: Float) {}
            override fun lineTo(x: Float, y: Float) {}
            override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {}
            override fun getCurrentPoint(): PointF = PointF(0f, 0f)
            override fun closePath() {}
            override fun endPath() {}
            override fun strokePath() {}
            override fun fillPath(windingRule: Path.FillType) {}
            override fun fillAndStrokePath(windingRule: Path.FillType) {}
            override fun shadingFill(shadingName: COSName) {}
        }
        engine.processPage(page)
        return found
    }
}

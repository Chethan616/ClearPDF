package com.kyant.pdfcore.link

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination

/**
 * A tappable link on a page: a web / mail address ([uri]) or a place in the same document
 * ([targetPage], and [targetTop] as a 0..1 fraction of that page's height when the link names
 * one). Geometry is normalized (0..1) in display space, like [com.kyant.pdfcore.form.PdfFormWidget].
 */
data class PdfLink(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uri: String? = null,
    val targetPage: Int? = null,
    val targetTop: Float? = null
)

object PdfLinks {

    /** Every link in [doc], by page (empty when it has none). */
    fun read(doc: PDDocument): Map<Int, List<PdfLink>> {
        val out = HashMap<Int, MutableList<PdfLink>>()
        for (i in 0 until doc.numberOfPages) {
            val page = runCatching { doc.getPage(i) }.getOrNull() ?: continue
            val annotations = runCatching { page.annotations }.getOrNull() ?: continue
            val box = page.cropBox ?: page.mediaBox ?: continue
            val rotation = ((page.rotation % 360) + 360) % 360
            for (a in annotations) {
                val link = a as? PDAnnotationLink ?: continue
                runCatching {
                    val rect = normalize(link.rectangle ?: return@runCatching, box, rotation) ?: return@runCatching
                    var uri: String? = null
                    var dest: PDDestination? = null
                    when (val action = link.action) {
                        is PDActionURI -> uri = action.uri
                        is PDActionGoTo -> dest = action.destination
                        null -> dest = link.destination
                        else -> Unit
                    }
                    val target = dest?.let { pageDestination(doc, it) }
                    val targetPage = target?.retrievePageNumber()?.takeIf { it in 0 until doc.numberOfPages }
                    if (uri.isNullOrBlank() && targetPage == null) return@runCatching
                    out.getOrPut(i) { ArrayList() } += PdfLink(
                        pageIndex = i,
                        left = rect[0], top = rect[1], right = rect[2], bottom = rect[3],
                        uri = uri?.trim()?.takeIf { it.isNotEmpty() },
                        targetPage = targetPage,
                        targetTop = targetPage?.let { topOf(doc, it, target) }
                    )
                }
            }
        }
        return out
    }

    private fun pageDestination(doc: PDDocument, dest: PDDestination): PDPageDestination? = when (dest) {
        is PDPageDestination -> dest
        is PDNamedDestination -> runCatching { doc.documentCatalog.findNamedDestinationPage(dest) }.getOrNull()
        else -> null
    }

    /** Where an "XYZ" destination puts the top of the view, as a fraction of the page (unrotated pages). */
    private fun topOf(doc: PDDocument, pageIndex: Int, dest: PDPageDestination?): Float? {
        val xyz = dest as? PDPageXYZDestination ?: return null
        val y = xyz.top.takeIf { it >= 0 }?.toFloat() ?: return null
        val page = runCatching { doc.getPage(pageIndex) }.getOrNull() ?: return null
        if (((page.rotation % 360) + 360) % 360 != 0) return null
        val box = page.cropBox ?: page.mediaBox ?: return null
        if (box.height <= 0f) return null
        return ((box.upperRightY - y) / box.height).coerceIn(0f, 1f)
    }

    /** PDF user space → normalized display rect (left, top, right, bottom). */
    private fun normalize(r: PDRectangle, box: PDRectangle, rotation: Int): FloatArray? {
        val w = box.width
        val h = box.height
        if (w <= 0f || h <= 0f) return null
        fun map(x: Float, y: Float): Pair<Float, Float> = when (rotation) {
            90 -> (y - box.lowerLeftY) / h to (x - box.lowerLeftX) / w
            180 -> (box.upperRightX - x) / w to (y - box.lowerLeftY) / h
            270 -> (box.upperRightY - y) / h to (box.upperRightX - x) / w
            else -> (x - box.lowerLeftX) / w to (box.upperRightY - y) / h
        }
        val a = map(r.lowerLeftX, r.lowerLeftY)
        val b = map(r.upperRightX, r.upperRightY)
        val l = minOf(a.first, b.first).coerceIn(0f, 1f)
        val t = minOf(a.second, b.second).coerceIn(0f, 1f)
        val rr = maxOf(a.first, b.first).coerceIn(0f, 1f)
        val bb = maxOf(a.second, b.second).coerceIn(0f, 1f)
        return if (rr - l <= 0f || bb - t <= 0f) null else floatArrayOf(l, t, rr, bb)
    }
}

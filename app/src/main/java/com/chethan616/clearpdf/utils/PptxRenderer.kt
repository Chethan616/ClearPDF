package com.chethan616.clearpdf.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import java.util.zip.ZipInputStream

/**
 * Renders a .pptx to one PDF page per slide, at the presentation's real slide size, with shapes
 * drawn where the author put them.
 *
 * The previous converter walked every `<a:t>` in the package and emitted each one as a paragraph on
 * a portrait A4 page. That is not a presentation viewer — a slide's meaning is largely carried by
 * its geometry (what is a title, what sits beside what, what is a picture), and flattening it to a
 * column of strings destroys all of it. Text also arrived in document order, which for a slide with
 * overlapping placeholders is not reading order.
 *
 * This reads what a slide actually is: the slide size from `ppt/presentation.xml`, the shape tree
 * from each slide part, positions inherited from the slide layout and master when a placeholder
 * doesn't carry its own, colours resolved through the theme, and pictures from `ppt/media`.
 *
 * Deliberately dependency-free. Every library that renders PowerPoint properly on Android (POI's
 * OOXML half plus XmlBeans, or a bundled LibreOffice/UNO core) is measured in tens of megabytes,
 * and this app's whole install is smaller than that. The trade-off is stated in the class: this is
 * a faithful-enough static render, not PowerPoint. Charts, SmartArt, 3-D effects, gradients and
 * animations are not reproduced.
 */
internal object PptxRenderer {

    /** English Metric Units per point: 914400 EMU per inch ÷ 72 points per inch. */
    private const val EmuPerPoint = 12700f

    /** 4:3 at 720×540 pt — what PowerPoint used before 16:9, and a safe size for a broken header. */
    private const val DefaultSlideW = 720f
    private const val DefaultSlideH = 540f

    private const val MaxSlides = 500

    /**
     * @return a rendered document, or null if this isn't a presentation we can read — the caller
     *   falls back to its text-dump path rather than showing an empty file.
     */
    fun render(bytes: ByteArray): PdfDocument? {
        val pkg = readPackage(bytes) ?: return null
        val slides = pkg.slidePaths()
        if (slides.isEmpty()) return null

        val doc = PdfDocument()
        val (slideW, slideH) = pkg.slideSize()
        var pageNumber = 1
        for (path in slides.take(MaxSlides)) {
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(Math.round(slideW), Math.round(slideH), pageNumber).create()
            )
            runCatching { pkg.drawSlide(page.canvas, path, pageNumber) }
            doc.finishPage(page)
            pageNumber++
        }
        return doc
    }

    // ── Package ─────────────────────────────────────────────────────────────────

    private fun readPackage(bytes: ByteArray): Package? = runCatching {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val n = entry.name.removePrefix("/")
                // Everything a slide can reach. Notes and thumbnails are skipped: they are not on
                // the rendered page and a notes-heavy deck can carry more of them than slides.
                val keep = n == "ppt/presentation.xml" ||
                    n == "ppt/_rels/presentation.xml.rels" ||
                    n.startsWith("ppt/slides/") ||
                    n.startsWith("ppt/slideLayouts/") ||
                    n.startsWith("ppt/slideMasters/") ||
                    n.startsWith("ppt/theme/") ||
                    n.startsWith("ppt/media/")
                if (keep && !n.startsWith("ppt/notesSlides/")) entries[n] = zip.readBytes()
            }
        }
        if (entries["ppt/presentation.xml"] == null) null else Package(entries)
    }.getOrNull()

    private class Package(val entries: Map<String, ByteArray>) {

        private val parsed = HashMap<String, OoxmlNode?>()
        private val relsCache = HashMap<String, Map<String, String>>()

        fun part(path: String): OoxmlNode? = parsed.getOrPut(path) {
            entries[path]?.let { OoxmlNode.parse(it.inputStream()) }
        }

        /** `rId` → absolute package path, for the `.rels` sidecar of [path]. */
        fun rels(path: String): Map<String, String> = relsCache.getOrPut(path) {
            val dir = path.substringBeforeLast('/', "")
            val relPath = "$dir/_rels/${path.substringAfterLast('/')}.rels"
            val root = entries[relPath]?.let { OoxmlNode.parse(it.inputStream()) } ?: return@getOrPut emptyMap()
            root.childrenNamed("Relationship").mapNotNull { r ->
                val id = r.attr("Id") ?: return@mapNotNull null
                val target = r.attr("Target") ?: return@mapNotNull null
                id to resolve(dir, target)
            }.toMap()
        }

        /** Resolve a relationship target (usually `../slideLayouts/slideLayout3.xml`) to a part path. */
        private fun resolve(baseDir: String, target: String): String {
            if (target.startsWith("/")) return target.removePrefix("/")
            val stack = ArrayDeque(baseDir.split('/').filter { it.isNotEmpty() })
            for (segment in target.split('/')) {
                when (segment) {
                    "", "." -> {}
                    ".." -> stack.removeLastOrNull()
                    else -> stack.addLast(segment)
                }
            }
            return stack.joinToString("/")
        }

        fun slideSize(): Pair<Float, Float> {
            val sz = part("ppt/presentation.xml")?.find("p:sldSz")
            val w = sz?.attr("cx")?.toFloatOrNull()?.div(EmuPerPoint) ?: DefaultSlideW
            val h = sz?.attr("cy")?.toFloatOrNull()?.div(EmuPerPoint) ?: DefaultSlideH
            return w.coerceIn(120f, 4000f) to h.coerceIn(120f, 4000f)
        }

        /**
         * Slides in presentation order. `<p:sldIdLst>` is the authoritative order — the zip's entry
         * order is arbitrary, and `slide10.xml` sorts before `slide2.xml` as a string, so both of
         * the obvious shortcuts get a real deck wrong.
         */
        fun slidePaths(): List<String> {
            val presentation = part("ppt/presentation.xml")
            val rels = rels("ppt/presentation.xml")
            val listed = presentation?.find("p:sldIdLst")?.childrenNamed("p:sldId")
                ?.mapNotNull { rels[it.attr("r:id")] }
                ?.filter { entries.containsKey(it) }
                .orEmpty()
            if (listed.isNotEmpty()) return listed
            return entries.keys
                .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedBy { Regex("slide(\\d+)\\.xml").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
        }

        // ── Theme colours ───────────────────────────────────────────────────────

        private fun themePath(masterPath: String?): String? =
            masterPath?.let { m -> rels(m).values.firstOrNull { it.startsWith("ppt/theme/") } }
                ?: entries.keys.firstOrNull { it.startsWith("ppt/theme/theme") }

        /**
         * Scheme name (`accent1`, `tx1`, …) → ARGB, from the master's theme. The slide-level names
         * (`tx1`, `bg1`, …) go through the master's colour map (a layout may override it): a dark
         * template maps text to `lt1`, which is why its text is white, not the theme's `dk1`.
         */
        private fun themeColors(masterPath: String?, layoutPath: String?): Map<String, Int> {
            val path = themePath(masterPath) ?: return emptyMap()
            val scheme = part(path)?.find("a:clrScheme") ?: return emptyMap()
            val raw = HashMap<String, Int>()
            for (entry in scheme.children) {
                // `<a:dk1><a:sysClr lastClr="000000"/></a:dk1>` or `<a:srgbClr val="4472C4"/>`
                val key = entry.name.substringAfter(':')
                val srgb = entry.child("a:srgbClr")?.attr("val")
                    ?: entry.child("a:sysClr")?.attr("lastClr")
                val color = parseSrgb(srgb) ?: continue
                raw[key] = color
            }
            val map = layoutPath?.let { part(it) }?.find("a:overrideClrMapping")
                ?: masterPath?.let { part(it) }?.find("p:clrMap")
            val out = HashMap<String, Int>(raw)
            val defaults = mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")
            for (name in listOf("bg1", "tx1", "bg2", "tx2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")) {
                val target = map?.attr(name) ?: defaults[name] ?: name
                raw[target]?.let { out[name] = it }
            }
            return out
        }

        // ── Slide rendering ─────────────────────────────────────────────────────

        fun drawSlide(canvas: Canvas, slidePath: String, slideNumber: Int) {
            val slide = part(slidePath) ?: return
            val layoutPath = rels(slidePath).values.firstOrNull { it.startsWith("ppt/slideLayouts/") }
            val masterPath = layoutPath?.let { rels(it).values.firstOrNull { p -> p.startsWith("ppt/slideMasters/") } }
            val theme = themeColors(masterPath, layoutPath)

            val ctx = SlideContext(
                theme = theme,
                slidePath = slidePath,
                slideNumber = slideNumber,
                layout = layoutPath?.let { part(it) },
                master = masterPath?.let { part(it) },
                // A placeholder without its own `<a:xfrm>` inherits the layout's box, and the layout
                // may in turn inherit the master's. Looking that up is what keeps titles and body
                // text on the slide instead of stacked in the top-left corner.
                placeholders = placeholderBoxes(masterPath) + placeholderBoxes(layoutPath)
            )

            canvas.drawColor(Color.WHITE)
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            drawBackground(canvas, slide, theme, masterPath, w, h) ||
                (layoutPath != null && drawBackground(canvas, part(layoutPath), theme, masterPath, w, h)) ||
                (masterPath != null && drawBackground(canvas, part(masterPath), theme, masterPath, w, h))

            val tree = slide.find("p:spTree") ?: return
            drawShapeTree(canvas, tree, ctx, identityTransform())
        }

        /**
         * Paints a part's `<p:bg>`: its own fill (`p:bgPr`: solid or gradient), or a reference into
         * the theme's background fills (`p:bgRef`), whose placeholder colour is the referenced
         * scheme colour. False when the part doesn't set a background.
         */
        private fun drawBackground(
            canvas: Canvas, part: OoxmlNode?, theme: Map<String, Int>, masterPath: String?, w: Float, h: Float
        ): Boolean {
            val bg = part?.find("p:bg") ?: return false
            bg.child("p:bgPr")?.let { return paintFill(canvas, it, theme, null, w, h) }
            val ref = bg.child("p:bgRef") ?: return false
            val idx = ref.attr("idx")?.toIntOrNull() ?: return false
            val color = solidFillColor(ref, theme)
            val list = themePath(masterPath)?.let { part(it) }?.find("a:bgFillStyleLst")?.children.orEmpty()
            val chosen = (if (idx >= 1001) list.getOrNull(idx - 1001) else null)
            if (chosen == null) {
                color?.let { canvas.drawColor(it); return true }
                return false
            }
            return paintFill(canvas, chosen, theme, color, w, h)
        }

        /** A solid or linear-gradient fill over the whole canvas; [phClr] stands in for `phClr`. */
        private fun paintFill(canvas: Canvas, fill: OoxmlNode, theme: Map<String, Int>, phClr: Int?, w: Float, h: Float): Boolean {
            val node = if (fill.name == "a:solidFill" || fill.name == "a:gradFill") fill
                else fill.child("a:solidFill") ?: fill.child("a:gradFill") ?: return false
            fun colorOf(holder: OoxmlNode): Int? {
                holder.child("a:schemeClr")?.let { sc -> if (sc.attr("val") == "phClr" && phClr != null) return withMods(phClr, sc) }
                return solidFillColor(holder, theme)
            }
            when (node.name) {
                "a:solidFill" -> { canvas.drawColor(colorOf(node) ?: return false); return true }
                "a:gradFill" -> {
                    val stops = node.find("a:gsLst")?.childrenNamed("a:gs").orEmpty().mapNotNull { gs ->
                        val c = colorOf(gs) ?: return@mapNotNull null
                        ((gs.attr("pos")?.toFloatOrNull() ?: 0f) / 100000f).coerceIn(0f, 1f) to c
                    }.sortedBy { it.first }
                    if (stops.isEmpty()) return false
                    if (stops.size == 1) { canvas.drawColor(stops[0].second); return true }
                    // `<a:lin ang>` is in 60000ths of a degree, clockwise from left → right.
                    val angle = Math.toRadians(((node.child("a:lin")?.attr("ang")?.toFloatOrNull() ?: 5400000f) / 60000f).toDouble())
                    val half = (kotlin.math.abs(w * kotlin.math.cos(angle)) + kotlin.math.abs(h * kotlin.math.sin(angle))).toFloat() / 2f
                    val dx = (kotlin.math.cos(angle) * half).toFloat()
                    val dy = (kotlin.math.sin(angle) * half).toFloat()
                    val shader = android.graphics.LinearGradient(
                        w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy,
                        stops.map { it.second }.toIntArray(), stops.map { it.first }.toFloatArray(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, Paint().apply { this.shader = shader })
                    return true
                }
            }
            return false
        }

        /** `"type|idx"` → box, from a layout or master part. */
        private fun placeholderBoxes(path: String?): Map<String, Rect> {
            val tree = path?.let { part(it) }?.find("p:spTree") ?: return emptyMap()
            val out = HashMap<String, Rect>()
            for (shape in tree.childrenNamed("p:sp")) {
                val ph = shape.find("p:ph") ?: continue
                val box = shapeBox(shape) ?: continue
                val type = ph.attr("type") ?: "body"
                val idx = ph.attr("idx") ?: ""
                out["$type|$idx"] = box
                // `putIfAbsent` is API 24 and this app ships to 23; the exact-match key above wins
                // over these looser fallbacks, so first-writer-wins is the behaviour we want anyway.
                if (!out.containsKey("$type|")) out["$type|"] = box
                if (idx.isNotEmpty() && !out.containsKey("|$idx")) out["|$idx"] = box
            }
            return out
        }

        private fun drawShapeTree(canvas: Canvas, tree: OoxmlNode, ctx: SlideContext, transform: Transform) {
            for (node in tree.children) {
                runCatching {
                    when (node.name) {
                        "p:sp" -> drawShape(canvas, node, ctx, transform)
                        "p:pic" -> drawPicture(canvas, node, ctx, transform)
                        "p:graphicFrame" -> drawGraphicFrame(canvas, node, ctx, transform)
                        "p:grpSp" -> {
                            // A group re-maps its children's coordinate space: `chOff`/`chExt` is the
                            // space the children were authored in, `off`/`ext` is where the group
                            // actually sits. Without composing that, every grouped shape lands at
                            // its raw authoring offset, which is usually off-slide entirely.
                            val child = groupTransform(node)?.let { compose(transform, it) } ?: transform
                            drawShapeTree(canvas, node, ctx, child)
                        }
                    }
                }
            }
        }

        // ── Shapes ──────────────────────────────────────────────────────────────

        private fun drawShape(canvas: Canvas, shape: OoxmlNode, ctx: SlideContext, transform: Transform) {
            val box = resolveBox(shape, ctx)?.let { transform.apply(it) } ?: return
            val spPr = shape.child("p:spPr")
            val rotation = spPr?.child("a:xfrm")?.attr("rot")?.toFloatOrNull()?.div(60000f) ?: 0f

            canvas.save()
            if (rotation != 0f) canvas.rotate(rotation, box.centerX(), box.centerY())

            val fill = spPr?.child("a:solidFill")?.let { solidFillColor(it, ctx.theme) }
            if (fill != null && spPr.child("a:noFill") == null) {
                canvas.drawRect(box.toRectF(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            }
            // An outline, when the author asked for one — this is what makes boxed callouts and
            // table-like arrangements of rectangles still read as boxes.
            spPr?.child("a:ln")?.child("a:solidFill")?.let { solidFillColor(it, ctx.theme) }?.let { stroke ->
                val width = (spPr.child("a:ln")?.attr("w")?.toFloatOrNull()?.div(EmuPerPoint) ?: 1f).coerceIn(0.5f, 8f)
                canvas.drawRect(box.toRectF(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke; style = Paint.Style.STROKE; strokeWidth = width
                })
            }

            shape.child("p:txBody")?.let { drawTextBody(canvas, it, box, ctx, placeholderType(shape), fill, inheritanceOf(shape, ctx)) }
            canvas.restore()
        }

        private fun drawPicture(canvas: Canvas, pic: OoxmlNode, ctx: SlideContext, transform: Transform) {
            val box = resolveBox(pic, ctx)?.let { transform.apply(it) } ?: return
            val embed = pic.find("a:blip")?.attr("r:embed") ?: return
            val path = rels(ctx.slidePath)[embed] ?: return
            val bitmap = decodeMedia(path, box.width(), box.height()) ?: return
            canvas.drawBitmap(bitmap, null, box.toRectF(), Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
        }

        /** Tables arrive wrapped in a graphic frame; charts and SmartArt also do, and are skipped. */
        private fun drawGraphicFrame(canvas: Canvas, frame: OoxmlNode, ctx: SlideContext, transform: Transform) {
            // Legacy Equation.3 objects carry a picture inside mc:Fallback. Android cannot open
            // the embedded OLE object, but its fallback is the exact equation artwork Office
            // displays; draw it through the same relationship/geometry path as ordinary pictures.
            if (frame.find("p:oleObj") != null) {
                frame.find("p:pic")?.let { drawPicture(canvas, it, ctx, transform) }
                return
            }

            val box = frame.find("p:xfrm")?.let { boxOf(it) }?.let { transform.apply(it) } ?: return
            val table = frame.find("a:tbl") ?: return
            val grid = table.find("a:tblGrid")?.childrenNamed("a:gridCol").orEmpty()
                .map { (it.attr("w")?.toFloatOrNull() ?: 0f) / EmuPerPoint }
            val rows = table.childrenNamed("a:tr")
            val totalW = grid.sum().takeIf { it > 1f } ?: box.width()
            val scale = box.width() / totalW
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 0.6f
            }

            var y = box.top
            for (row in rows) {
                val rowH = ((row.attr("h")?.toFloatOrNull() ?: 0f) / EmuPerPoint).takeIf { it > 1f } ?: 22f
                var x = box.left
                row.childrenNamed("a:tc").forEachIndexed { i, cell ->
                    val w = (grid.getOrNull(i) ?: (totalW / grid.size.coerceAtLeast(1))) * scale
                    val cellBox = Rect(x, y, x + w, y + rowH)
                    canvas.drawRect(cellBox.toRectF(), linePaint)
                    cell.child("a:txBody")?.let { drawTextBody(canvas, it, cellBox.inset(3f), ctx, null, null) }
                    x += w
                }
                y += rowH
                if (y > box.bottom + rowH) break
            }
        }

        private fun placeholderType(shape: OoxmlNode): String? = shape.find("p:ph")?.attr("type")

        /**
         * What a placeholder's text inherits, lowest priority first: the master's text style for
         * its kind, then the master placeholder, then the layout placeholder. Orphan placeholders
         * (no counterpart on the layout) inherit from the master by type, as PowerPoint does.
         */
        private fun inheritanceOf(shape: OoxmlNode, ctx: SlideContext): TextInheritance {
            val ph = shape.find("p:ph") ?: return TextInheritance(emptyList(), emptyList())
            val type = ph.attr("type") ?: "obj"
            val layoutSp = inheritedPlaceholder(ctx.layout, shape, byTypeOnly = false)
            val masterSp = inheritedPlaceholder(ctx.master, shape, byTypeOnly = true)
            val txStyles = ctx.master?.find("p:txStyles")
            val style = when (type) {
                "title", "ctrTitle" -> txStyles?.child("p:titleStyle")
                "dt", "ftr", "sldNum", "hdr" -> txStyles?.child("p:otherStyle")
                else -> txStyles?.child("p:bodyStyle")
            }
            return TextInheritance(
                bodies = listOfNotNull(masterSp?.find("a:bodyPr"), layoutSp?.find("a:bodyPr")),
                lists = listOfNotNull(style, masterSp?.find("a:lstStyle"), layoutSp?.find("a:lstStyle"))
            )
        }

        /** The layout/master placeholder shape [shape] inherits from, by idx then by type. */
        private fun inheritedPlaceholder(part: OoxmlNode?, shape: OoxmlNode, byTypeOnly: Boolean): OoxmlNode? {
            val ph = shape.find("p:ph") ?: return null
            val tree = part?.find("p:spTree") ?: return null
            val type = ph.attr("type") ?: "obj"
            val idx = ph.attr("idx")?.takeIf { it != "4294967295" }
            val shapes = tree.childrenNamed("p:sp")
            if (!byTypeOnly && idx != null) shapes.firstOrNull { it.find("p:ph")?.attr("idx") == idx }?.let { return it }
            val wanted = when (type) {
                "title", "ctrTitle" -> setOf("title", "ctrTitle")
                "body", "obj", "subTitle" -> if (byTypeOnly) setOf("body") else setOf(type)
                else -> setOf(type)
            }
            if (!byTypeOnly && idx != null) return null
            return shapes.firstOrNull { (it.find("p:ph")?.attr("type") ?: "obj") in wanted }
        }

        /** A shape's own `<a:xfrm>`, else the box its placeholder inherits from layout/master. */
        private fun resolveBox(shape: OoxmlNode, ctx: SlideContext): Rect? {
            shapeBox(shape)?.let { return it }
            val ph = shape.find("p:ph") ?: return null
            val type = ph.attr("type") ?: "body"
            val idx = ph.attr("idx") ?: ""
            return ctx.placeholders["$type|$idx"]
                ?: ctx.placeholders["$type|"]
                ?: ctx.placeholders["|$idx"]
                ?: ctx.placeholders["body|"]
        }

        private fun decodeMedia(path: String, targetW: Float, targetH: Float): Bitmap? {
            val data = entries[path] ?: return null
            return runCatching {
                val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, probe)
                // Decoding a 12-megapixel photo to fill a 300 pt box is how a deck of screenshots
                // turns into an OutOfMemoryError. Sample down to roughly twice the drawn size.
                val wanted = maxOf(targetW, targetH, 1f) * 2f
                var sample = 1
                while (maxOf(probe.outWidth, probe.outHeight) / sample > wanted) sample *= 2
                BitmapFactory.decodeByteArray(
                    data, 0, data.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
        }
    }

    // ── Text ────────────────────────────────────────────────────────────────────

    private class SlideContext(
        val theme: Map<String, Int>,
        val slidePath: String,
        val slideNumber: Int,
        val layout: OoxmlNode?,
        val master: OoxmlNode?,
        val placeholders: Map<String, Rect>
    )

    /** Inherited text-box properties ([bodies]) and list styles ([lists]), lowest priority first. */
    private class TextInheritance(val bodies: List<OoxmlNode>, val lists: List<OoxmlNode>) {
        fun body(attr: String): String? = bodies.asReversed().firstNotNullOfOrNull { it.attr(attr) }
        fun runAttr(level: Int, attr: String): String? = lists.asReversed().firstNotNullOfOrNull {
            it.child("a:lvl${level + 1}pPr")?.child("a:defRPr")?.attr(attr)
        }
    }

    /** Default point size when neither the run nor the placeholder says. */
    private fun defaultSize(placeholder: String?): Float = when (placeholder) {
        "title", "ctrTitle" -> 36f
        "subTitle" -> 22f
        "ftr", "sldNum", "dt" -> 11f
        else -> 17f
    }

    private fun drawTextBody(
        canvas: Canvas,
        txBody: OoxmlNode,
        box: Rect,
        ctx: SlideContext,
        placeholder: String?,
        behindColor: Int?,
        inherited: TextInheritance = TextInheritance(emptyList(), emptyList())
    ) {
        val paragraphs = txBody.childrenNamed("a:p")
        if (paragraphs.isEmpty()) return
        val bodyPr = txBody.child("a:bodyPr")
        fun bodyAttr(name: String) = bodyPr?.attr(name) ?: inherited.body(name)
        fun insetPt(name: String, defaultEmu: Float) = (bodyAttr(name)?.toFloatOrNull() ?: defaultEmu) / EmuPerPoint
        val lIns = insetPt("lIns", 91440f)
        val rIns = insetPt("rIns", 91440f)
        val tIns = insetPt("tIns", 45720f)
        val bIns = insetPt("bIns", 45720f)

        // Text over a filled shape has to stay legible against that fill, not against the slide.
        val defaultInk = behindColor?.let { if (isLight(it)) Color.BLACK else Color.WHITE }
            ?: ctx.theme["tx1"] ?: Color.BLACK

        val width = (box.width() - lIns - rIns).toInt().coerceAtLeast(24)
        val layouts = mutableListOf<Pair<StaticLayout, Float>>()   // layout + its left indent
        var totalHeight = 0f

        for (paragraph in paragraphs) {
            val level = paragraph.child("a:pPr")?.attr("lvl")?.toIntOrNull() ?: 0
            val indent = level * 16f
            val (spanned, size) = buildParagraph(paragraph, ctx, defaultInk, placeholder, inherited.runAttr(level, "cap"))
            if (spanned.isEmpty()) { totalHeight += size * 0.6f; continue }

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                color = defaultInk
                typeface = Typeface.SANS_SERIF
            }
            val layout = StaticLayout.Builder
                .obtain(spanned, 0, spanned.length, paint, (width - indent).toInt().coerceAtLeast(24))
                .setAlignment(alignmentOf(paragraph, placeholder))
                .setLineSpacing(1f, 1.12f)
                .setIncludePad(false)
                .build()
            layouts.add(layout to indent)
            totalHeight += layout.height + size * 0.25f
        }
        if (layouts.isEmpty()) return

        // Vertical anchor, inherited from the layout/master placeholder when the slide doesn't say
        // (a master's title is usually centred). Text that outgrows its box overflows it, as in
        // PowerPoint, instead of being clipped: a box starting above the slide still shows its text.
        val anchor = bodyAttr("anchor")
            ?: if (placeholder == "title" || placeholder == "ctrTitle") "ctr" else "t"
        val innerTop = box.top + tIns
        val innerHeight = box.height() - tIns - bIns
        var y = when (anchor) {
            "ctr" -> innerTop + (innerHeight - totalHeight) / 2f
            "b" -> box.bottom - bIns - totalHeight
            else -> innerTop
        }

        for ((layout, indent) in layouts) {
            canvas.save()
            canvas.translate(box.left + lIns + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + layout.paint.textSize * 0.25f
        }
    }

    /** One `<a:p>` → styled text plus the size its first run asked for (used for spacing). */
    private fun buildParagraph(
        paragraph: OoxmlNode,
        ctx: SlideContext,
        defaultInk: Int,
        placeholder: String?,
        inheritedCap: String? = null
    ): Pair<SpannableStringBuilder, Float> {
        val out = SpannableStringBuilder()
        var firstSize = 0f

        val bullet = when {
            paragraph.child("a:pPr")?.child("a:buNone") != null -> null
            paragraph.child("a:pPr")?.child("a:buChar") != null ->
                paragraph.child("a:pPr")?.child("a:buChar")?.attr("char") ?: "•"
            paragraph.child("a:pPr")?.child("a:buAutoNum") != null -> "•"
            else -> null
        }
        if (bullet != null) out.append(bullet).append("  ")

        for (node in paragraph.children) {
            when (node.name) {
                "a:br" -> out.append("\n")
                // `<a:fld>` is a live field — slide number, date. It carries a cached `<a:t>`, but
                // the number in it is whatever it was when the file was last saved, so the slide
                // number is regenerated and everything else uses the cached text.
                "a:fld", "a:r" -> {
                    val rPr = node.child("a:rPr")
                    val raw = node.child("a:t")?.textContent().orEmpty()
                    val value = if (node.name == "a:fld" && node.attr("type")?.startsWith("slidenum") == true) {
                        ctx.slideNumber.toString()
                    } else raw
                    if (value.isEmpty()) continue
                    // `cap="all"` (often inherited from the master's title style) shows capitals.
                    val cap = node.child("a:rPr")?.attr("cap") ?: inheritedCap
                    val shown = if (cap == "all" || cap == "small") value.uppercase() else value

                    val start = out.length
                    out.append(shown)
                    val end = out.length

                    val size = rPr?.attr("sz")?.toFloatOrNull()?.div(100f)?.coerceIn(4f, 200f)
                        ?: defaultSize(placeholder)
                    if (firstSize == 0f) firstSize = size
                    out.setSpan(AbsoluteSizeSpan(Math.round(size)), start, end, 0)

                    val bold = rPr?.attr("b") == "1"
                    val italic = rPr?.attr("i") == "1"
                    if (bold && italic) out.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, 0)
                    else if (bold) out.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                    else if (italic) out.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
                    if (rPr?.attr("u") != null && rPr.attr("u") != "none") out.setSpan(UnderlineSpan(), start, end, 0)

                    val ink = rPr?.child("a:solidFill")?.let { solidFillColor(it, ctx.theme) }
                    if (ink != null && ink != defaultInk) out.setSpan(ForegroundColorSpan(ink), start, end, 0)

                    rPr?.child("a:latin")?.attr("typeface")?.let { face ->
                        val family = when {
                            face.contains("Courier", true) || face.contains("Mono", true) -> "monospace"
                            face.contains("Times", true) || face.contains("Georgia", true) ||
                                face.contains("Serif", true) -> "serif"
                            else -> null
                        }
                        if (family != null) out.setSpan(TypefaceSpan(family), start, end, 0)
                    }
                }
            }
        }
        if (firstSize == 0f) firstSize = defaultSize(placeholder)
        return out to firstSize
    }

    private fun alignmentOf(paragraph: OoxmlNode, placeholder: String?): Layout.Alignment {
        val centredByDefault = placeholder == "ctrTitle" || placeholder == "subTitle"
        return when (paragraph.child("a:pPr")?.attr("algn")) {
            "ctr" -> Layout.Alignment.ALIGN_CENTER
            "r" -> Layout.Alignment.ALIGN_OPPOSITE
            null -> if (centredByDefault) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    // ── Geometry ────────────────────────────────────────────────────────────────

    private class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun width() = right - left
        fun height() = bottom - top
        fun centerX() = (left + right) / 2f
        fun centerY() = (top + bottom) / 2f
        fun toRectF() = RectF(left, top, right, bottom)
        fun inset(by: Float) = Rect(left + by, top + by, right - by, bottom - by)
    }

    /** Scale + translate. A group's child space maps into slide space by exactly this much. */
    private class Transform(val scaleX: Float, val scaleY: Float, val dx: Float, val dy: Float) {
        fun apply(r: Rect) = Rect(
            r.left * scaleX + dx, r.top * scaleY + dy,
            r.right * scaleX + dx, r.bottom * scaleY + dy
        )
    }

    private fun identityTransform() = Transform(1f, 1f, 0f, 0f)

    private fun compose(outer: Transform, inner: Transform) = Transform(
        outer.scaleX * inner.scaleX,
        outer.scaleY * inner.scaleY,
        outer.scaleX * inner.dx + outer.dx,
        outer.scaleY * inner.dy + outer.dy
    )

    private fun groupTransform(group: OoxmlNode): Transform? {
        val xfrm = group.find("a:xfrm") ?: return null
        val off = xfrm.child("a:off") ?: return null
        val ext = xfrm.child("a:ext") ?: return null
        val chOff = xfrm.child("a:chOff") ?: return null
        val chExt = xfrm.child("a:chExt") ?: return null

        val x = (off.attr("x")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val y = (off.attr("y")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val w = (ext.attr("cx")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val h = (ext.attr("cy")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val cx = (chOff.attr("x")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val cy = (chOff.attr("y")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val cw = (chExt.attr("cx")?.toFloatOrNull() ?: 0f) / EmuPerPoint
        val ch = (chExt.attr("cy")?.toFloatOrNull() ?: 0f) / EmuPerPoint

        val sx = if (cw > 0.01f) w / cw else 1f
        val sy = if (ch > 0.01f) h / ch else 1f
        return Transform(sx, sy, x - cx * sx, y - cy * sy)
    }

    private fun shapeBox(shape: OoxmlNode): Rect? = shape.child("p:spPr")?.child("a:xfrm")?.let { boxOf(it) }

    private fun boxOf(xfrm: OoxmlNode): Rect? {
        val off = xfrm.child("a:off") ?: return null
        val ext = xfrm.child("a:ext") ?: return null
        val x = (off.attr("x")?.toFloatOrNull() ?: return null) / EmuPerPoint
        val y = (off.attr("y")?.toFloatOrNull() ?: return null) / EmuPerPoint
        val w = (ext.attr("cx")?.toFloatOrNull() ?: return null) / EmuPerPoint
        val h = (ext.attr("cy")?.toFloatOrNull() ?: return null) / EmuPerPoint
        if (w <= 0f || h <= 0f) return null
        return Rect(x, y, x + w, y + h)
    }

    // ── Colour ──────────────────────────────────────────────────────────────────

    private fun solidFillColor(fill: OoxmlNode, theme: Map<String, Int>): Int? {
        val node = fill.child("a:solidFill") ?: fill
        node.child("a:srgbClr")?.attr("val")?.let { hex -> parseSrgb(hex)?.let { return withMods(it, node.child("a:srgbClr")) } }
        node.child("a:schemeClr")?.let { scheme ->
            val name = scheme.attr("val") ?: return@let
            theme[name]?.let { return withMods(it, scheme) }
            // A deck whose theme part is missing still has to render; these are the defaults
            // PowerPoint itself falls back to.
            return when (name) {
                "tx1", "dk1" -> Color.BLACK
                "bg1", "lt1" -> Color.WHITE
                "tx2", "dk2" -> Color.rgb(0x44, 0x44, 0x44)
                "bg2", "lt2" -> Color.rgb(0xEE, 0xEE, 0xEE)
                else -> null
            }
        }
        return null
    }

    /** `<a:lumMod>` / `<a:lumOff>` / `<a:alpha>` — the tints that make a theme's palette. */
    private fun withMods(base: Int, node: OoxmlNode?): Int {
        if (node == null) return base
        var r = Color.red(base) / 255f
        var g = Color.green(base) / 255f
        var b = Color.blue(base) / 255f

        node.child("a:tint")?.attr("val")?.toFloatOrNull()?.let { v ->
            val t = v / 100000f
            r = r * t + (1f - t); g = g * t + (1f - t); b = b * t + (1f - t)
        }
        node.child("a:shade")?.attr("val")?.toFloatOrNull()?.let { v ->
            val f = v / 100000f
            r *= f; g *= f; b *= f
        }
        node.child("a:satMod")?.attr("val")?.toFloatOrNull()?.let { v ->
            val hsv = FloatArray(3)
            Color.RGBToHSV(Math.round(r.coerceIn(0f, 1f) * 255), Math.round(g.coerceIn(0f, 1f) * 255), Math.round(b.coerceIn(0f, 1f) * 255), hsv)
            hsv[1] = (hsv[1] * v / 100000f).coerceIn(0f, 1f)
            val c = Color.HSVToColor(hsv)
            r = Color.red(c) / 255f; g = Color.green(c) / 255f; b = Color.blue(c) / 255f
        }
        node.child("a:lumMod")?.attr("val")?.toFloatOrNull()?.let { v ->
            val f = v / 100000f
            r *= f; g *= f; b *= f
        }
        node.child("a:lumOff")?.attr("val")?.toFloatOrNull()?.let { v ->
            val f = v / 100000f
            r += f; g += f; b += f
        }
        val alpha = node.child("a:alpha")?.attr("val")?.toFloatOrNull()?.div(100000f) ?: 1f
        return Color.argb(
            Math.round(alpha.coerceIn(0f, 1f) * 255),
            Math.round(r.coerceIn(0f, 1f) * 255),
            Math.round(g.coerceIn(0f, 1f) * 255),
            Math.round(b.coerceIn(0f, 1f) * 255)
        )
    }

    private fun parseSrgb(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrNull()
    }

    private fun isLight(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 150
}

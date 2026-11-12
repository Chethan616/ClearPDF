package com.chethan616.clearpdf.ui.notes

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassBackButton
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One idea of a mind map: its words, how deep it sits, and (once laid out) its box. */
internal class MapNode(val text: String, val level: Int) {
    val children = ArrayList<MapNode>()
    var label: String = text
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    /** Which of the root's branches it belongs to (its colour). */
    var branch = 0
    val centerY: Float get() = top + height / 2f
}

/** The most ideas a map draws (a note is not a database). */
private const val MAX_NODES = 400

/**
 * A note's outline as a tree. One idea a line: headings (`#`, `##`…) open branches, and under
 * them, or with no headings at all, indentation (two spaces or a tab a step) and bullets (`-`,
 * `*`, `•`, `1.`) nest ideas under the line above. [title] is the map's centre.
 */
internal fun parseOutline(title: String, body: String): MapNode {
    val root = MapNode(title.trim(), -1)
    val path = ArrayList<MapNode>().apply { add(root) }
    // where plain lines start counting from: one deeper than the heading they are under
    var base = 0
    var count = 0
    for (raw in body.lines()) {
        if (raw.isBlank()) continue
        var indent = 0
        for (ch in raw) {
            if (ch == ' ') indent++ else if (ch == '\t') indent += 2 else break
        }
        var s = raw.trim()
        val hashes = s.takeWhile { it == '#' }.length
        val level: Int
        if (hashes in 1..6 && s.getOrNull(hashes) == ' ') {
            level = hashes - 1
            base = hashes
            s = s.drop(hashes)
        } else {
            Regex("""^([-*•+]|\d{1,3}[.)])\s+""").find(s)?.let { s = s.substring(it.range.last + 1) }
            level = base + indent / 2
        }
        s = s.replace("**", "").replace("__", "").trim()
        if (s.isEmpty()) continue
        if (++count > MAX_NODES) break
        // the line above that is less deep is its parent
        while (path.size > 1 && path.last().level >= level) path.removeAt(path.lastIndex)
        val node = MapNode(s, path.last().level + 1)
        path.last().children.add(node)
        path.add(node)
    }
    return root
}

/** A map set out: its tree with every box placed, and the room it takes (pixels at [density]). */
internal class MapLayout(val root: MapNode, val width: Float, val height: Float, val density: Float)

/**
 * Sets the tree out left to right: the centre at the left, each generation a column further right,
 * leaves stacked down the page and every parent level with the middle of its children.
 */
internal fun layoutMap(root: MapNode, density: Float): MapLayout {
    val d = density
    val paint = mapTextPaint(d, bold = false)
    val bold = mapTextPaint(d, bold = true)
    val maxText = 230f * d
    val pad = 24f * d
    val columns = ArrayList<Float>()
    fun measure(n: MapNode, depth: Int) {
        val p = if (depth <= 1) bold else paint
        n.label = TextUtils.ellipsize(n.text.ifEmpty { " " }, p, maxText, TextUtils.TruncateAt.END).toString()
        n.width = p.measureText(n.label) + (if (depth == 0) 40f else 28f) * d
        n.height = (if (depth == 0) 48f else 36f) * d
        while (columns.size <= depth) columns.add(0f)
        columns[depth] = maxOf(columns[depth], n.width)
        n.children.forEach { measure(it, depth + 1) }
    }
    measure(root, 0)
    val x = FloatArray(columns.size)
    var at = pad
    for (i in columns.indices) {
        x[i] = at
        at += columns[i] + 46f * d
    }
    var cursor = pad
    fun place(n: MapNode, depth: Int, branch: Int) {
        n.left = x[depth]
        n.branch = branch
        if (n.children.isEmpty()) {
            n.top = cursor
            cursor += n.height + 12f * d
        } else {
            n.children.forEachIndexed { i, c -> place(c, depth + 1, if (depth == 0) i else branch) }
            n.top = (n.children.first().centerY + n.children.last().centerY) / 2f - n.height / 2f
        }
    }
    place(root, 0, 0)
    return MapLayout(root, at - 46f * d + pad, cursor - 12f * d + pad, d)
}

private fun mapTextPaint(density: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 15f * density
    typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
}

private val BranchColors = listOf(Candy.Sky, Candy.Bubblegum, Candy.Mint, Candy.Tangerine, Candy.Grape, Candy.Lemon, Candy.Tomato)

/**
 * Draws [layout] on [canvas] (the screen, a picture or a PDF page, all alike): soft curves from each
 * idea to its children in the branch's colour, the centre as a dark capsule, the first ideas as
 * capsules of colour, the rest as tinted ones.
 */
internal fun drawMap(canvas: android.graphics.Canvas, layout: MapLayout, dark: Boolean) {
    val d = layout.density
    val ink = if (dark) Color.White else Color(0xFF15171C)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        strokeCap = Paint.Cap.ROUND
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val plain = mapTextPaint(d, bold = false)
    val bold = mapTextPaint(d, bold = true)
    val box = RectF()
    val path = Path()
    fun color(n: MapNode) = BranchColors[n.branch % BranchColors.size]
    fun links(n: MapNode) {
        for (c in n.children) {
            val x0 = n.left + n.width
            val x1 = c.left
            val mid = (x0 + x1) / 2f
            path.reset()
            path.moveTo(x0, n.centerY)
            path.cubicTo(mid, n.centerY, mid, c.centerY, x1, c.centerY)
            line.color = color(c).copy(alpha = 0.75f).toArgb()
            canvas.drawPath(path, line)
            links(c)
        }
    }
    fun boxes(n: MapNode, depth: Int) {
        box.set(n.left, n.top, n.left + n.width, n.top + n.height)
        val text = if (depth <= 1) bold else plain
        when (depth) {
            0 -> {
                fill.color = (if (dark) Color(0xFFF2F4FA) else Color(0xFF1E2233)).toArgb()
                text.color = (if (dark) Color(0xFF15171C) else Color.White).toArgb()
            }
            1 -> {
                fill.color = color(n).toArgb()
                text.color = Color.White.toArgb()
            }
            else -> {
                fill.color = color(n).copy(alpha = if (dark) 0.26f else 0.16f).toArgb()
                text.color = ink.toArgb()
            }
        }
        canvas.drawRoundRect(box, n.height / 2f, n.height / 2f, fill)
        val inset = (if (depth == 0) 20f else 14f) * d
        canvas.drawText(n.label, n.left + inset, n.centerY - (text.descent() + text.ascent()) / 2f, text)
        n.children.forEach { boxes(it, depth + 1) }
    }
    links(layout.root)
    boxes(layout.root, 0)
}

/** [layout] as a picture file (PNG, at most 4096 pixels on its long side) or a one-page PDF. */
private fun writeMap(layout: MapLayout, dark: Boolean, pdf: Boolean, out: File) {
    val ground = (if (dark) Color(0xFF14152B) else Color.White).toArgb()
    if (pdf) {
        // a page of the map's own size: a density-independent pixel to a point
        val w = (layout.width / layout.density).toInt().coerceAtLeast(1)
        val h = (layout.height / layout.density).toInt().coerceAtLeast(1)
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
            page.canvas.drawColor(ground)
            page.canvas.scale(1f / layout.density, 1f / layout.density)
            drawMap(page.canvas, layout, dark)
            doc.finishPage(page)
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    } else {
        val k = minOf(1f, 4096f / maxOf(layout.width, layout.height))
        val bitmap = Bitmap.createBitmap((layout.width * k).toInt().coerceAtLeast(1), (layout.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(ground)
        canvas.scale(k, k)
        drawMap(canvas, layout, dark)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

/**
 * A note seen as a mind map: its title in the middle, its outline branching out to the right. Drag
 * to move it, pinch to zoom; the two buttons save it to Downloads as a picture or a PDF. The map is
 * drawn from the note as it is written (one idea a line, indented under the idea it belongs to), so
 * it is edited by editing the note, and nothing leaves the phone.
 */
@Composable
fun MindMapScreen(vm: AppViewModel, title: String, body: String, onClose: () -> Unit) {
    val c = clear
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.mindmap_title)
    val layout = remember(title, body, density) { layoutMap(parseOutline(title.ifBlank { fallback }, body), density) }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(layout) { mutableStateOf(false) }
    val empty = layout.root.children.isEmpty()
    BackHandler(onBack = onClose)

    fun save(pdf: Boolean) {
        val name = title.ifBlank { fallback }.replace(Regex("""[\\/:*?"<>|\n\r]"""), " ").trim().take(60).ifEmpty { fallback }
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching {
                    File(context.cacheDir, "map-${System.nanoTime()}.${if (pdf) "pdf" else "png"}").also { writeMap(layout, c.dark, pdf, it) }
                }.getOrNull()
            }
            if (file == null) vm.tell(context.getString(R.string.common_file_unreadable))
            else vm.deliverFile(file, "$name.${if (pdf) "pdf" else "png"}", if (pdf) "application/pdf" else "image/png")
        }
    }

    Box(
        Modifier.fillMaxSize().background(if (c.dark) Color(0xFF14152B) else Color(0xFFF6F7FB)).onSizeChanged { size: IntSize ->
            // the whole map in view to begin with, never larger than life
            if (!fitted && size.width > 0 && layout.width > 0f) {
                val s = minOf(1f, size.width / layout.width, size.height * 0.8f / layout.height).coerceAtLeast(0.3f)
                scale = s
                pan = Offset((size.width - layout.width * s) / 2f, (size.height - layout.height * s) / 2f)
                fitted = true
            }
        },
    ) {
        if (empty) {
            Text(
                stringResource(R.string.mindmap_empty), style = ClearType.Body, color = c.inkSoft, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
        } else {
            Canvas(
                Modifier.fillMaxSize().pointerInput(layout) {
                    detectTransformGestures { centroid, move, zoom, _ ->
                        val next = (scale * zoom).coerceIn(0.2f, 4f)
                        pan = (pan - centroid) * (next / scale) + centroid + move
                        scale = next
                    }
                },
            ) {
                drawIntoCanvas { cv ->
                    val native = cv.nativeCanvas
                    native.save()
                    native.translate(pan.x, pan.y)
                    native.scale(scale, scale)
                    drawMap(native, layout, c.dark)
                    native.restore()
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassBackButton(onClose, immediate = true)
            Text(stringResource(R.string.mindmap_title), style = ClearType.Title, color = c.ink, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (!empty) {
                GlassCircle(onClick = { save(pdf = false) }, label = stringResource(R.string.mindmap_save_image)) {
                    GlassIcon(LucideIcons.ImagePlus, null, Modifier.size(20.dp))
                }
                GlassCircle(onClick = { save(pdf = true) }, label = stringResource(R.string.mindmap_save_pdf)) {
                    GlassIcon(LucideIcons.FilePdf, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

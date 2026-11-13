package com.chethan616.clearpdf.ui.notes

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassBackButton
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.rememberFeel
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ---- the map ---------------------------------------------------------------------------------------

/** The most ideas a map holds (a note is not a database). */
private const val MAX_NODES = 400

/** The branch colours, in the order a map's first ideas take them. */
private val BranchColors = listOf(Candy.Sky, Candy.Bubblegum, Candy.Mint, Candy.Tangerine, Candy.Grape, Candy.Lemon, Candy.Tomato, Candy.Lime)

internal fun branchColor(i: Int): Color = if (i < 0) Candy.Sky else BranchColors[i % BranchColors.size]

/**
 * One idea of a mind map: its words, the colour picked for it (-1: its branch's), whether its
 * branch is folded away, and how far it was dragged from where the map would put it (dp). The rest
 * is set by [layoutMind] each time the map changes.
 */
internal class MindNode(var text: String, val id: String = UUID.randomUUID().toString().take(12)) {
    val children = ArrayList<MindNode>()
    var color = -1
    var collapsed = false
    var dx = 0f
    var dy = 0f

    // set out by layoutMind, in pixels
    var parent: MindNode? = null
    var depth = 0
    var tint = 0
    var side = 1
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    var span = 0f
    var hidden = 0
    var words: StaticLayout? = null
    var wordsKey = ""

    val cx: Float get() = left + width / 2f
    val cy: Float get() = top + height / 2f
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun find(id: String): MindNode? {
        if (this.id == id) return this
        for (c in children) c.find(id)?.let { return it }
        return null
    }

    fun count(): Int = 1 + children.sumOf { it.count() }

    /** Whether [n] is this idea or one under it. */
    fun holds(n: MindNode): Boolean = this === n || children.any { it.holds(n) }
}

/** A mind map: its centre idea and whether its branches all go to the right. */
internal class MindDoc(val root: MindNode, var oneSided: Boolean = false) {
    /** A new mind-map note holding this map. */
    fun asNote() = com.chethan616.clearpdf.notes.Note(
        UUID.randomUUID().toString(), com.chethan616.clearpdf.notes.NoteKind.Map,
        title = root.text.trim(), body = outline(), map = toJson(),
    )
}

private fun MindNode.json(): JSONObject = JSONObject().apply {
    put("id", id)
    put("t", text)
    if (color >= 0) put("c", color)
    if (collapsed) put("f", true)
    if (dx != 0f) put("dx", dx.toDouble())
    if (dy != 0f) put("dy", dy.toDouble())
    if (children.isNotEmpty()) put("k", JSONArray().apply { children.forEach { put(it.json()) } })
}

private fun JSONObject.node(budget: IntArray): MindNode = MindNode(optString("t"), optString("id").ifEmpty { UUID.randomUUID().toString().take(12) }).also { n ->
    n.color = optInt("c", -1)
    n.collapsed = optBoolean("f")
    n.dx = optDouble("dx", 0.0).toFloat()
    n.dy = optDouble("dy", 0.0).toFloat()
    optJSONArray("k")?.let { a ->
        for (i in 0 until a.length()) {
            if (budget[0]-- <= 0) break
            n.children.add(a.getJSONObject(i).node(budget))
        }
    }
}

/** The map as it is kept in its note. */
internal fun MindDoc.toJson(): String =
    JSONObject().put("v", 1).put("layout", if (oneSided) "right" else "both").put("root", root.json()).toString()

internal fun mindFromJson(s: String): MindDoc? {
    if (s.isBlank()) return null
    return runCatching {
        val o = JSONObject(s)
        MindDoc(o.getJSONObject("root").node(intArrayOf(MAX_NODES)), o.optString("layout") == "right")
    }.getOrNull()
}

/** The map's ideas as an indented outline (two spaces a level): the note's body, for search and sharing. */
internal fun MindDoc.outline(): String = buildString {
    fun walk(n: MindNode, depth: Int) {
        for (c in n.children) {
            if (c.text.isNotBlank()) {
                repeat(depth) { append("  ") }
                append(c.text.replace('\n', ' ').trim()).append('\n')
            }
            walk(c, depth + 1)
        }
    }
    walk(root, 0)
}.trimEnd()

/** The map as OPML 2.0, which desktop outliners and mind-map apps open. */
internal fun MindDoc.opml(): String = buildString {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "&#10;")
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head><title>")
    append(esc(root.text)).append("</title></head>\n  <body>\n")
    fun walk(n: MindNode, depth: Int) {
        val indent = "  ".repeat(depth + 2)
        append(indent).append("<outline text=\"").append(esc(n.text)).append('"')
        if (n.children.isEmpty()) {
            append("/>\n")
        } else {
            append(">\n")
            n.children.forEach { walk(it, depth + 1) }
            append(indent).append("</outline>\n")
        }
    }
    walk(root, 0)
    append("  </body>\n</opml>\n")
}

/**
 * An OPML outline (what desktop outliners and mind-map apps export) as a map: its `outline`
 * elements nested as ideas, each named by its `text` (or `title`) attribute. One top outline is
 * the centre; several hang from a centre named by the head's `title`. Null when it has no outline.
 * Read with a small tokenizer rather than an XML parser, so a damaged file still gives what it can.
 */
internal fun mindFromOpml(xml: String): MindDoc? {
    fun unescape(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e == "amp" -> "&"
            e == "lt" -> "<"
            e == "gt" -> ">"
            e == "quot" -> "\""
            e == "apos" -> "'"
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            else -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
    }
    val attr = Regex("""([A-Za-z_:][-\w:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    fun attrs(s: String): Map<String, String> = attr.findAll(s).associate { it.groupValues[1].lowercase() to unescape(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
    val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.let { unescape(it).trim() }.orEmpty()
    val holder = MindNode(title)
    val path = ArrayList<MindNode>().apply { add(holder) }
    var count = 0
    for (m in Regex("""<(/?)outline\b([^>]*?)(/?)>""", RegexOption.IGNORE_CASE).findAll(xml)) {
        val closing = m.groupValues[1] == "/"
        if (closing) {
            if (path.size > 1) path.removeAt(path.lastIndex)
            continue
        }
        if (++count > MAX_NODES) break
        val a = attrs(m.groupValues[2])
        val node = MindNode((a["text"] ?: a["title"] ?: "").replace('\n', ' ').trim())
        path.last().children.add(node)
        if (m.groupValues[3] != "/") path.add(node)
    }
    if (holder.children.isEmpty()) return null
    // one top outline is the centre itself
    return MindDoc(holder.children.singleOrNull() ?: holder)
}

/**
 * A note's outline as a tree. One idea a line: headings (`#`, `##`…) open branches, and under
 * them, or with no headings at all, indentation (two spaces or a tab a step) and bullets (`-`,
 * `*`, `•`, `1.`) nest ideas under the line above. [title] is the map's centre.
 */
internal fun parseOutline(title: String, body: String): MindNode {
    val root = MindNode(title.trim())
    // each open idea with how deep it sits (the centre at -1)
    val path = ArrayList<Pair<MindNode, Int>>().apply { add(root to -1) }
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
        while (path.size > 1 && path.last().second >= level) path.removeAt(path.lastIndex)
        val node = MindNode(s)
        val (parent, depth) = path.last()
        parent.children.add(node)
        path.add(node to depth + 1)
    }
    return root
}

// ---- laid out --------------------------------------------------------------------------------------

/** A map set out: every idea shown (in drawing order) with its box placed, and the room they take. */
internal class MindLayout(val doc: MindDoc, val density: Float, val nodes: List<MindNode>, val bounds: RectF) {
    /** The idea at ([x], [y]), the one drawn on top; [skip]'s branch is looked through (an idea being dragged). */
    fun hit(x: Float, y: Float, slop: Float, skip: MindNode? = null): MindNode? =
        nodes.lastOrNull { (skip == null || !skip.holds(it)) && x >= it.left - slop && x <= it.right + slop && y >= it.top - slop && y <= it.bottom + slop }

    /** The folded idea whose count badge is at ([x], [y]). */
    fun badgeAt(x: Float, y: Float): MindNode? =
        nodes.firstOrNull { it.hidden > 0 && hypot(x - badgeX(it), y - it.cy) <= 17f * density }
}

private fun badgeX(n: MindNode) = if (n.side >= 0) n.right else n.left

/** The app's own face (Plus Jakarta Sans) for the map's words, once a screen has loaded it. */
internal object MapType {
    @Volatile
    private var base: Typeface? = null

    val loaded: Boolean get() = base != null

    fun load(context: android.content.Context) {
        if (base == null) base = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.jakarta) }.getOrNull()
    }

    fun face(weight: Int): Typeface = Typeface.create(base ?: Typeface.DEFAULT, weight, false)
}

private fun wordsPaint(d: Float, depth: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = when (depth) {
        0 -> 19f
        1 -> 16f
        else -> 15f
    } * d
    typeface = MapType.face(if (depth == 0) 800 else if (depth == 1) 700 else 500)
}

/**
 * Sets [doc] out the way mind maps are drawn: the centre in the middle, its ideas branching out to
 * the right and the left in turn (or all to the right), each idea's own ideas stacked beside it and
 * centred on it. A dragged idea keeps its offset, and its branch moves with it. Words wrap past a
 * width and are measured once until they change.
 */
internal fun layoutMind(doc: MindDoc, d: Float, rootHint: String, hint: String): MindLayout {
    val paints = Array(3) { wordsPaint(d, it) }
    val shown = ArrayList<MindNode>()
    fun measure(n: MindNode, parent: MindNode?, depth: Int, branch: Int) {
        n.parent = parent
        n.depth = depth
        n.tint = when {
            depth == 0 -> -1
            n.color >= 0 -> n.color
            depth == 1 -> branch
            else -> parent!!.tint
        }
        val words = n.text.ifBlank { if (depth == 0) rootHint else hint }
        val key = "$d|$depth|${MapType.loaded}|${n.text.isBlank()}|$words"
        if (n.words == null || n.wordsKey != key) {
            val p = paints[depth.coerceAtMost(2)]
            val max = (if (depth == 0) 240f else 210f) * d
            val w = ceil(min(Layout.getDesiredWidth(words, p), max)).toInt().coerceAtLeast(1)
            n.words = StaticLayout.Builder.obtain(words, 0, words.length, p, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(6)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            n.wordsKey = key
        }
        val padX = when (depth) {
            0 -> 26f
            1 -> 18f
            else -> 14f
        } * d
        val padY = when (depth) {
            0 -> 16f
            1 -> 11f
            else -> 9f
        } * d
        val words2 = n.words!!
        n.width = max(words2.width + padX * 2f, (if (depth == 0) 96f else 56f) * d)
        n.height = words2.height + padY * 2f
        n.hidden = if (n.collapsed) n.count() - 1 else 0
        shown.add(n)
        if (!n.collapsed) n.children.forEachIndexed { i, c -> measure(c, n, depth + 1, if (depth == 0) i else branch) }
    }
    measure(doc.root, null, 0, 0)

    val gapRoot = 24f * d
    val gap = 12f * d
    fun spans(n: MindNode): Float {
        val kids = if (n.collapsed) emptyList() else n.children
        val inner = kids.sumOf { spans(it).toDouble() }.toFloat() + (if (n.depth == 0) gapRoot else gap) * (kids.size - 1).coerceAtLeast(0)
        n.span = max(n.height, inner)
        return n.span
    }
    spans(doc.root)

    fun place(n: MindNode, side: Int, edge: Float, centreY: Float) {
        n.side = side
        val reach = (if (n.depth == 1) 64f else 40f) * d
        n.left = if (side > 0) edge + reach else edge - reach - n.width
        n.top = centreY - n.height / 2f
        if (n.collapsed || n.children.isEmpty()) return
        val total = n.children.sumOf { it.span.toDouble() }.toFloat() + gap * (n.children.size - 1)
        var y = centreY - total / 2f
        for (c in n.children) {
            place(c, side, if (side > 0) n.right else n.left, y + c.span / 2f)
            y += c.span + gap
        }
    }
    val root = doc.root
    root.side = 0
    root.left = -root.width / 2f
    root.top = -root.height / 2f
    if (!root.collapsed) {
        for (side in intArrayOf(1, -1)) {
            val kids = root.children.filterIndexed { i, _ -> if (side > 0) doc.oneSided || i % 2 == 0 else !doc.oneSided && i % 2 == 1 }
            if (kids.isEmpty()) continue
            val total = kids.sumOf { it.span.toDouble() }.toFloat() + gapRoot * (kids.size - 1)
            var y = -total / 2f
            for (c in kids) {
                place(c, side, if (side > 0) root.right else root.left, y + c.span / 2f)
                y += c.span + gapRoot
            }
        }
    }
    // dragged ideas, with their branches
    fun shift(n: MindNode, ox: Float, oy: Float) {
        val x = ox + n.dx * d
        val y = oy + n.dy * d
        n.left += x
        n.top += y
        if (!n.collapsed) n.children.forEach { shift(it, x, y) }
    }
    shift(root, 0f, 0f)

    val bounds = RectF(root.left, root.top, root.right, root.bottom)
    for (n in shown) {
        bounds.union(n.left, n.top, n.right, n.bottom)
        if (n.hidden > 0) bounds.union(badgeX(n) - 12f * d, n.cy - 12f * d, badgeX(n) + 12f * d, n.cy + 12f * d)
    }
    return MindLayout(doc, d, shown, bounds)
}

// ---- drawn -----------------------------------------------------------------------------------------

/** A branch from ([x0], [y0]) to ([x1], [y1]): a soft S-curve that thins from [w0] to [w1]. */
private fun taper(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float) {
    path.reset()
    val steps = 22
    val mx = (x0 + x1) / 2f
    val xs = FloatArray(steps + 1)
    val ys = FloatArray(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val u = 1f - t
        xs[i] = u * u * u * x0 + 3f * u * u * t * mx + 3f * u * t * t * mx + t * t * t * x1
        ys[i] = u * u * u * y0 + 3f * u * u * t * y0 + 3f * u * t * t * y1 + t * t * t * y1
    }
    val lx = FloatArray(steps + 1)
    val ly = FloatArray(steps + 1)
    val rx = FloatArray(steps + 1)
    val ry = FloatArray(steps + 1)
    for (i in 0..steps) {
        val a = max(i - 1, 0)
        val b = min(i + 1, steps)
        var tx = xs[b] - xs[a]
        var ty = ys[b] - ys[a]
        val len = hypot(tx, ty).coerceAtLeast(0.001f)
        tx /= len
        ty /= len
        val half = lerp(w0, w1, i / steps.toFloat()) / 2f
        lx[i] = xs[i] - ty * half
        ly[i] = ys[i] + tx * half
        rx[i] = xs[i] + ty * half
        ry[i] = ys[i] - tx * half
    }
    path.moveTo(lx[0], ly[0])
    for (i in 1..steps) path.lineTo(lx[i], ly[i])
    for (i in steps downTo 0) path.lineTo(rx[i], ry[i])
    path.close()
    path.addCircle(x1, y1, w1 / 2f, Path.Direction.CW)
}

/**
 * Draws [layout] on [canvas] (the screen, a picture or a PDF page alike): branches that thin as
 * they go out, in their branch's colour; the centre as a dark capsule, its first ideas filled with
 * their colour, the rest as cards edged with it. A folded idea carries the count of what it hides;
 * [selected] wears a ring, and [target] (where a dragged idea would go) a green one.
 */
internal fun drawMind(canvas: android.graphics.Canvas, layout: MindLayout, dark: Boolean, selected: String? = null, target: String? = null) {
    val d = layout.density
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * d
        typeface = MapType.face(800)
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.WHITE
    }
    val path = Path()
    val box = RectF()
    for (n in layout.nodes) {
        val p = n.parent ?: continue
        val toRight = n.cx >= p.cx
        val x0 = if (p.depth == 0) p.cx + (if (toRight) 1f else -1f) * p.width * 0.3f else if (toRight) p.right else p.left
        val x1 = if (toRight) n.left else n.right
        val (w0, w1) = when (n.depth) {
            1 -> 8f to 3f
            2 -> 3.8f to 2f
            else -> 2.6f to 1.5f
        }
        taper(path, x0, p.cy, x1, n.cy, w0 * d, w1 * d)
        fill.color = branchColor(n.tint).copy(alpha = if (dark) 0.9f else 0.82f).toArgb()
        canvas.drawPath(path, fill)
    }
    val ink = if (dark) Color(0xFFF1F3F9) else Color(0xFF181B26)
    for (n in layout.nodes) {
        val colour = branchColor(n.tint)
        box.set(n.left, n.top, n.right, n.bottom)
        val radius = min(n.height / 2f, (if (n.depth == 0) 26f else if (n.depth == 1) 18f else 13f) * d)
        fill.color = Color.Black.copy(alpha = if (dark) 0.3f else 0.08f).toArgb()
        box.offset(0f, 2.5f * d)
        canvas.drawRoundRect(box, radius, radius, fill)
        box.offset(0f, -2.5f * d)
        val words: Color
        when (n.depth) {
            0 -> {
                fill.color = (if (dark) Color(0xFFF2F4FA) else Color(0xFF1E2233)).toArgb()
                words = if (dark) Color(0xFF14152B) else Color.White
            }
            1 -> {
                fill.color = colour.toArgb()
                words = if (colour.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
            }
            else -> {
                fill.color = (if (dark) Color(0xFF242842) else Color.White).toArgb()
                words = ink
            }
        }
        canvas.drawRoundRect(box, radius, radius, fill)
        if (n.depth >= 2) {
            stroke.strokeWidth = 1.6f * d
            stroke.color = colour.copy(alpha = 0.8f).toArgb()
            canvas.drawRoundRect(box, radius, radius, stroke)
        }
        n.words?.let { w ->
            w.paint.color = (if (n.text.isBlank()) words.copy(alpha = 0.5f) else words).toArgb()
            canvas.save()
            canvas.translate(n.cx - w.width / 2f, n.cy - w.height / 2f)
            w.draw(canvas)
            canvas.restore()
        }
        if (n.hidden > 0) {
            val bx = badgeX(n)
            fill.color = (if (n.depth == 0) Candy.Sky else colour).toArgb()
            canvas.drawCircle(bx, n.cy, 11f * d, fill)
            stroke.strokeWidth = 2f * d
            stroke.color = (if (dark) Color(0xFF14152B) else Color.White).toArgb()
            canvas.drawCircle(bx, n.cy, 11f * d, stroke)
            val label = if (n.hidden > 99) "99+" else n.hidden.toString()
            canvas.drawText(label, bx, n.cy - (badge.descent() + badge.ascent()) / 2f, badge)
        }
        if (n.id == selected || n.id == target) {
            val ring = 5f * d
            stroke.strokeWidth = 2.5f * d
            stroke.color = (if (n.id == target) Candy.Mint else Candy.Sky).toArgb()
            box.inset(-ring, -ring)
            canvas.drawRoundRect(box, radius + ring, radius + ring, stroke)
        }
    }
}

/** [doc] as a picture (PNG, at most 4096 pixels on its long side), a one-page PDF, or OPML. */
private fun writeMind(doc: MindDoc, d: Float, dark: Boolean, kind: String, out: File, rootHint: String, hint: String) {
    if (kind == "opml") {
        out.writeText(doc.opml())
        return
    }
    val layout = layoutMind(doc, d, rootHint, hint)
    val pad = 36f * d
    val b = RectF(layout.bounds).apply { inset(-pad, -pad) }
    if (kind == "pdf") {
        // a page of the map's own size, a dp to a point, on white (it is for printing)
        val doc2 = PdfDocument()
        try {
            val page = doc2.startPage(PdfDocument.PageInfo.Builder((b.width() / d).toInt().coerceAtLeast(1), (b.height() / d).toInt().coerceAtLeast(1), 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.scale(1f / d, 1f / d)
            page.canvas.translate(-b.left, -b.top)
            drawMind(page.canvas, layout, dark = false)
            doc2.finishPage(page)
            out.outputStream().use { doc2.writeTo(it) }
        } finally {
            doc2.close()
        }
    } else {
        val k = min(1f, 4096f / max(b.width(), b.height()))
        val bitmap = Bitmap.createBitmap((b.width() * k).toInt().coerceAtLeast(1), (b.height() * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor((if (dark) Color(0xFF14152B) else Color(0xFFF8F9FC)).toArgb())
        canvas.scale(k, k)
        canvas.translate(-b.left, -b.top)
        drawMind(canvas, layout, dark)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

private fun safeName(s: String, fallback: String) =
    s.replace(Regex("""[\\/:*?"<>|\n\r]"""), " ").trim().take(60).ifEmpty { fallback }

/** The faint dot grid a map lies on, moving and scaling with it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dots(pan: Offset, scale: Float, d: Float, colour: Color) {
    var step = 26f * d * scale
    while (step < 14f * d) step *= 2f
    val points = ArrayList<Offset>()
    var x = ((pan.x % step) + step) % step
    while (x < size.width) {
        var y = ((pan.y % step) + step) % step
        while (y < size.height) {
            points.add(Offset(x, y))
            y += step
        }
        x += step
    }
    drawPoints(points, androidx.compose.ui.graphics.PointMode.Points, colour, strokeWidth = 2.4f * d, cap = StrokeCap.Round)
}

// ---- the editor ------------------------------------------------------------------------------------

/**
 * A mind-map note. Tap an idea to pick it and tap it again to type; the bar under it adds an idea
 * under it or beside it, colours its branch, folds it away or deletes it. Drag an idea to move it
 * (its branch comes along), or drop it on another idea to put it there. Drag the paper to look
 * around and pinch to zoom. Every change is kept with the note as it is made, and can be undone.
 */
@Composable
fun MindMapEditor(vm: AppViewModel, id: String) {
    val note = vm.notes.get(id) ?: run {
        LaunchedEffect(id) { vm.openNote = null }
        return
    }
    val c = clear
    val d = LocalDensity.current.density
    val context = LocalContext.current
    remember(context) { MapType.load(context) }
    val scope = rememberCoroutineScope()
    val feel = rememberFeel()
    val keyboard = LocalSoftwareKeyboardController.current
    val rootHint = stringResource(R.string.mindmap_root_hint)
    val hint = stringResource(R.string.mindmap_node_hint)
    val fallback = stringResource(R.string.mindmap_title)

    var doc by remember(id) { mutableStateOf(mindFromJson(note.map) ?: MindDoc(parseOutline(note.title, note.body))) }
    var version by remember { mutableIntStateOf(0) }
    val undo = remember(id) { ArrayList<String>() }
    val redo = remember(id) { ArrayList<String>() }
    var history by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue()) }
    var palette by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var menuAt by remember { mutableStateOf<Rect?>(null) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    val layout = remember(doc, version, d, rootHint, hint) { layoutMind(doc, d, rootHint, hint) }
    val current by rememberUpdatedState(layout)
    val focus = remember { FocusRequester() }
    var glide by remember { mutableStateOf<Job?>(null) }

    fun save() {
        val n = vm.notes.get(id) ?: return
        val json = doc.toJson()
        if (n.map != json) vm.notes.put(n.copy(title = doc.root.text.trim(), body = doc.outline(), map = json))
    }
    fun changed() {
        version++
        save()
    }
    // the map as it was, kept before each change for Undo
    fun keep() {
        undo.add(doc.toJson())
        if (undo.size > 80) undo.removeAt(0)
        redo.clear()
        history++
    }
    fun glideTo(s: Float, p: Offset) {
        glide?.cancel()
        val s0 = scale
        val p0 = pan
        glide = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 240f)) { v, _ ->
                scale = lerp(s0, s, v)
                pan = androidx.compose.ui.geometry.lerp(p0, p, v)
            }
        }
    }
    fun fit(animated: Boolean = true) {
        if (view.width == 0) return
        val b = current.bounds
        val top = 92f * d
        val bottom = 130f * d
        val w = view.width - 40f * d
        val h = (view.height - top - bottom).coerceAtLeast(100f)
        val s = min(w / b.width().coerceAtLeast(1f), h / b.height().coerceAtLeast(1f)).coerceIn(0.25f, 1.25f)
        val p = Offset(view.width / 2f - b.centerX() * s, top + h / 2f - b.centerY() * s)
        if (animated) glideTo(s, p) else {
            scale = s
            pan = p
        }
    }
    fun show(n: MindNode) {
        val s = scale.coerceIn(0.85f, 1.6f)
        glideTo(s, Offset(view.width / 2f, view.height * 0.3f) - Offset(n.cx, n.cy) * s)
    }
    fun startEdit(n: MindNode, isNew: Boolean) {
        if (!isNew) keep()
        fresh = isNew
        palette = false
        selected = n.id
        editing = n.id
        field = TextFieldValue(n.text, TextRange(0, n.text.length))
    }
    fun finishEdit() {
        val n = editing?.let { doc.root.find(it) }
        editing = null
        if (n != null) {
            n.text = n.text.trim()
            // an idea left without words goes again
            if (n.text.isEmpty() && n !== doc.root) {
                n.parent?.children?.remove(n)
                selected = n.parent?.id
            }
        }
        changed()
        // nothing came of it: no step to undo
        if (undo.isNotEmpty() && undo.last() == doc.toJson()) {
            undo.removeAt(undo.lastIndex)
            history++
        }
    }
    fun addChild(p: MindNode) {
        keep()
        p.collapsed = false
        val n = MindNode("")
        p.children.add(n)
        version++
        startEdit(n, isNew = true)
        feel(Feel.Rise)
    }
    fun addSibling(n: MindNode) {
        val p = n.parent ?: return addChild(n)
        keep()
        val s = MindNode("")
        p.children.add(p.children.indexOf(n) + 1, s)
        version++
        startEdit(s, isNew = true)
        feel(Feel.Rise)
    }
    fun delete(n: MindNode) {
        val p = n.parent ?: return
        keep()
        p.children.remove(n)
        selected = p.id
        changed()
        feel(Feel.Fall)
    }
    fun fold(n: MindNode) {
        if (n.children.isEmpty()) return
        keep()
        n.collapsed = !n.collapsed
        changed()
        feel(if (n.collapsed) Feel.ToggleOff else Feel.ToggleOn)
    }
    fun colour(n: MindNode, i: Int) {
        if (n.color == i) return
        keep()
        n.color = i
        changed()
        feel(Feel.Tick)
    }
    fun step(from: ArrayList<String>, to: ArrayList<String>) {
        if (editing != null) finishEdit()
        val s = from.removeLastOrNull() ?: return
        to.add(doc.toJson())
        mindFromJson(s)?.let { doc = it }
        if (selected?.let { doc.root.find(it) } == null) selected = null
        history++
        save()
        feel(Feel.Tick)
    }
    fun export(kind: String) {
        val copy = mindFromJson(doc.toJson()) ?: return
        val name = safeName(doc.root.text, fallback)
        val dark = c.dark
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching { File(context.cacheDir, "map-${System.nanoTime()}.$kind").also { writeMind(copy, d, dark, kind, it, rootHint, hint) } }.getOrNull()
            }
            val mime = when (kind) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                else -> "text/x-opml"
            }
            if (file == null) vm.tell(context.getString(R.string.common_file_unreadable)) else vm.deliverFile(file, "$name.$kind", mime)
        }
    }

    BackHandler {
        when {
            editing != null -> finishEdit()
            selected != null -> {
                selected = null
                palette = false
            }
            else -> vm.closeNote()
        }
    }
    LaunchedEffect(view) {
        if (!fitted && view.width > 0) {
            fit(animated = false)
            fitted = true
        }
    }
    // a new map starts by naming its centre
    LaunchedEffect(id) {
        if (doc.root.text.isBlank() && doc.root.children.isEmpty()) startEdit(doc.root, isNew = true)
    }
    // the keyboard put away ends the typing, so no cursor is left blinking in the bar
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeOpen) {
        if (imeOpen) imeWasOpen = true
        else if (imeWasOpen) {
            // a moment's grace: going on to the next idea hands the keyboard from one field to the next
            delay(350)
            imeWasOpen = false
            if (editing != null) finishEdit()
        }
    }
    LaunchedEffect(editing) {
        val n = editing?.let { current.doc.root.find(it) } ?: return@LaunchedEffect
        // the new idea's field takes the keyboard at once, then the map glides to it
        runCatching { focus.requestFocus() }
        keyboard?.show()
        show(n)
    }

    val chosen = selected?.let { doc.root.find(it) }?.takeIf { s -> layout.nodes.any { it === s } }
    val ground = if (c.dark) Color(0xFF14152B) else Color(0xFFF6F7FB)
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(ground)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { view = it }
                    .pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            glide?.cancel()
                            val lay = current
                            val start = (down.position - pan) / scale
                            val badge = lay.badgeAt(start.x, start.y)
                            val hit = if (badge == null) lay.hit(start.x, start.y, 4f * d) else null
                            // the centre doesn't move: dragging it looks around, as the paper does
                            val moving = hit?.takeIf { it !== lay.doc.root }
                            var dragged = false
                            var pinched = false
                            var travel = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    pinched = true
                                    if (target != null) target = null
                                    val zoom = event.calculateZoom()
                                    val move = event.calculatePan()
                                    val centre = event.calculateCentroid()
                                    val next = (scale * zoom).coerceIn(0.2f, 3f)
                                    pan = (pan - centre) * (next / scale) + centre + move
                                    scale = next
                                    event.changes.forEach { it.consume() }
                                } else if (!pinched) {
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                                    val delta = ch.positionChange()
                                    travel += delta.getDistance()
                                    if (!dragged && travel > viewConfiguration.touchSlop) {
                                        dragged = true
                                        if (moving != null) {
                                            if (editing != null) finishEdit()
                                            keep()
                                            selected = moving.id
                                            palette = false
                                            feel(Feel.Press)
                                        }
                                    }
                                    if (dragged) {
                                        if (moving != null) {
                                            moving.dx += delta.x / scale / d
                                            moving.dy += delta.y / scale / d
                                            version++
                                            val at = (ch.position - pan) / scale
                                            val over = current.hit(at.x, at.y, 0f, skip = moving)?.takeIf { it !== moving.parent }
                                            if (over?.id != target) {
                                                target = over?.id
                                                if (over != null) feel(Feel.Tick)
                                            }
                                        } else {
                                            pan += delta
                                        }
                                        ch.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            when {
                                moving != null && dragged -> {
                                    val into = target?.let { doc.root.find(it) }
                                    if (into != null && !pinched) {
                                        moving.parent?.children?.remove(moving)
                                        moving.dx = 0f
                                        moving.dy = 0f
                                        into.collapsed = false
                                        into.children.add(moving)
                                        feel(Feel.Confirm)
                                    }
                                    target = null
                                    changed()
                                }
                                dragged || pinched -> Unit
                                badge != null -> fold(badge)
                                hit != null -> {
                                    palette = false
                                    when {
                                        editing == hit.id -> Unit
                                        editing != null -> {
                                            finishEdit()
                                            selected = hit.id
                                        }
                                        // a second tap on the picked idea types in it
                                        selected == hit.id -> startEdit(hit, isNew = false)
                                        else -> {
                                            selected = hit.id
                                            feel(Feel.Tick)
                                        }
                                    }
                                }
                                else -> {
                                    if (editing != null) finishEdit()
                                    selected = null
                                    palette = false
                                }
                            }
                        }
                    },
            ) {
                dots(pan, scale, d, (if (c.dark) Color.White else Color(0xFF1E2233)).copy(alpha = if (c.dark) 0.07f else 0.09f))
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    nc.save()
                    nc.translate(pan.x, pan.y)
                    nc.scale(scale, scale)
                    drawMind(nc, layout, c.dark, selected, target)
                    nc.restore()
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val ink = rememberGlassInk(c.ink)
            // ---- top: back, the map's name, undo, redo, more
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassBackButton({
                    if (editing != null) finishEdit()
                    vm.closeNote()
                }, immediate = true)
                Row(Modifier.weight(1f).height(44.dp).then(ink.modifier).glassBar().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(LucideIcons.Network, null, tint = Candy.Grape, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        doc.root.text.ifBlank { fallback }, style = ClearType.Title.copy(fontSize = 15.sp), color = ink.color,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val canUndo = history >= 0 && undo.isNotEmpty()
                val canRedo = history >= 0 && redo.isNotEmpty()
                GlassCircle(onClick = { step(undo, redo) }, modifier = Modifier.alpha(if (canUndo) 1f else 0.4f), size = 44.dp, label = stringResource(R.string.common_undo)) {
                    GlassIcon(LucideIcons.Undo2, null, Modifier.size(19.dp))
                }
                GlassCircle(onClick = { step(redo, undo) }, modifier = Modifier.alpha(if (canRedo) 1f else 0.4f), size = 44.dp, label = stringResource(R.string.common_redo)) {
                    GlassIcon(LucideIcons.Redo2, null, Modifier.size(19.dp))
                }
                GlassCircle(
                    onClick = { menuAt = moreAnchor },
                    modifier = Modifier.onGloballyPositioned { moreAnchor = it.boundsInWindow() },
                    size = 44.dp,
                    label = stringResource(R.string.common_more_options),
                ) {
                    GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(19.dp))
                }
            }

            // ---- bottom: what can be done with the picked idea, or typing in it
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val typing = editing?.let { doc.root.find(it) }
                when {
                    typing != null -> {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 58.dp).then(ink.modifier).glassBar(RoundedCornerShape29).padding(start = 18.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                // Each idea is typed into a field of its own, with its own text and its own
                                // keyboard session: going on to the next idea starts the keyboard afresh, so
                                // nothing typed into the last one carries over into this one.
                                androidx.compose.runtime.key(typing.id) {
                                    val ideaId = typing.id
                                    val words = remember {
                                        androidx.compose.foundation.text.input.TextFieldState(typing.text, TextRange(0, typing.text.length))
                                    }
                                    LaunchedEffect(words) {
                                        androidx.compose.runtime.snapshotFlow { words.text.toString() }.collect { t ->
                                            val n = doc.root.find(ideaId) ?: return@collect
                                            val clean = t.replace('\n', ' ')
                                            if (n.text != clean) {
                                                n.text = clean
                                                version++
                                                save()
                                            }
                                        }
                                    }
                                    if (words.text.isEmpty()) {
                                        Text(if (typing === doc.root) rootHint else hint, style = ClearType.Body, color = ink.color.copy(alpha = 0.45f))
                                    }
                                    BasicTextField(
                                        state = words,
                                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                        textStyle = ClearType.Body.copy(color = ink.color),
                                        cursorBrush = SolidColor(Candy.Sky),
                                        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                        onKeyboardAction = { finishEdit() },
                                    )
                                }
                            }
                            // straight on to the next idea: under this one, or beside it
                            GlassCircle(onClick = {
                                val at = typing.id
                                finishEdit()
                                doc.root.find(at)?.let { addChild(it) }
                            }, size = 44.dp, label = stringResource(R.string.mindmap_child)) {
                                GlassIcon(LucideIcons.Plus, null, Modifier.size(19.dp))
                            }
                            if (typing !== doc.root) {
                                GlassCircle(onClick = {
                                    val at = typing.id
                                    finishEdit()
                                    doc.root.find(at)?.let { addSibling(it) }
                                }, size = 44.dp, label = stringResource(R.string.mindmap_sibling)) {
                                    GlassIcon(LucideIcons.RowsAdd, null, Modifier.size(19.dp))
                                }
                            }
                            GlassCircle(onClick = { finishEdit() }, size = 44.dp, tint = Candy.Sky, label = stringResource(R.string.common_done)) {
                                GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp))
                            }
                        }
                    }
                    chosen != null -> {
                        AnimatedVisibility(palette && chosen !== doc.root, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Row(
                                Modifier.then(ink.modifier).glassBar().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Swatch(Brush.sweepGradient(BranchColors + BranchColors.first()), chosen.color < 0, ink.color, stringResource(R.string.mindmap_branch_colour)) { colour(chosen, -1) }
                                BranchColors.forEachIndexed { i, col ->
                                    Swatch(SolidColor(col), chosen.color == i, ink.color, null) { colour(chosen, i) }
                                }
                            }
                        }
                        Row(
                            Modifier.then(ink.modifier).glassBar().padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(LocalGlassInk provides ink.color) {
                                MapTool(LucideIcons.Plus, stringResource(R.string.mindmap_child)) { addChild(chosen) }
                                if (chosen !== doc.root) MapTool(LucideIcons.RowsAdd, stringResource(R.string.mindmap_sibling)) { addSibling(chosen) }
                                MapTool(LucideIcons.Pencil, stringResource(R.string.mindmap_edit)) { startEdit(chosen, isNew = false) }
                                if (chosen !== doc.root) MapTool(LucideIcons.Palette, stringResource(R.string.mindmap_colour), lit = palette) { palette = !palette }
                                if (chosen.children.isNotEmpty()) {
                                    MapTool(if (chosen.collapsed) LucideIcons.Eye else LucideIcons.EyeOff, stringResource(if (chosen.collapsed) R.string.mindmap_unfold else R.string.mindmap_fold)) { fold(chosen) }
                                }
                                if (chosen !== doc.root) MapTool(LucideIcons.Trash2, stringResource(R.string.common_delete), tint = Candy.Tomato) { delete(chosen) }
                            }
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.mindmap_hint), style = ClearType.Caption, color = c.inkSoft, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassPill(onClick = { addChild(doc.root) }, tint = Candy.Sky, height = 48.dp, label = stringResource(R.string.mindmap_add_idea)) {
                                Icon(LucideIcons.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.mindmap_add_idea), style = ClearType.BodyStrong, color = Color.White)
                            }
                            GlassCircle(onClick = { fit() }, size = 48.dp, label = stringResource(R.string.mindmap_fit)) {
                                GlassIcon(LucideIcons.Maximize, null, Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }

        menuAt?.let { anchor ->
            GlassMenu(
                anchor = anchor,
                onDismiss = { menuAt = null },
                items = listOf(
                    GlassMenuItem(stringResource(R.string.mindmap_fit), LucideIcons.Maximize) { fit() },
                    GlassMenuItem(stringResource(if (doc.oneSided) R.string.mindmap_both_sides else R.string.mindmap_one_side), LucideIcons.Network) {
                        keep()
                        doc.oneSided = !doc.oneSided
                        changed()
                        scope.launch {
                            delay(30)
                            fit()
                        }
                    },
                    GlassMenuItem(stringResource(R.string.mindmap_save_image), LucideIcons.ImagePlus) { export("png") },
                    GlassMenuItem(stringResource(R.string.mindmap_save_pdf), LucideIcons.FilePdf) { export("pdf") },
                    GlassMenuItem(stringResource(R.string.mindmap_save_opml), LucideIcons.FileOutput) { export("opml") },
                ),
            )
        }
    }
}

private val RoundedCornerShape29 = androidx.compose.foundation.shape.RoundedCornerShape(29.dp)

/** One of the picked idea's tools: an icon over its name. */
@Composable
private fun MapTool(icon: ImageVector, label: String, tint: Color = LocalGlassInk.current, lit: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.width(58.dp).height(58.dp).pressable(onClick, pressedScale = 0.88f).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (lit) Candy.Sky else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (lit) Color.White else tint, modifier = Modifier.size(19.dp))
        }
        Text(label, style = ClearType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = tint.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A colour to pick: a disc, ringed when it is the one in use. */
@Composable
private fun Swatch(fill: Brush, on: Boolean, ink: Color, label: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .border(if (on) 2.dp else 0.dp, if (on) ink else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .pressable(onClick, pressedScale = 0.85f)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}

// ---- a written note, seen as a map -----------------------------------------------------------------

/**
 * A text note seen as a mind map: its title in the middle, its outline branching out. Drag to look
 * around and pinch to zoom; save it as a picture or a PDF, or make it a mind-map note to work on.
 */
@Composable
fun MindMapScreen(vm: AppViewModel, title: String, body: String, onClose: () -> Unit) {
    val fallback = stringResource(R.string.mindmap_title)
    val doc = remember(title, body) { MindDoc(parseOutline(title.ifBlank { fallback }, body)) }
    MindMapPreview(vm, doc, title, onClose = onClose)
}

/**
 * A mind map to look at: a note's outline drawn as a map, or an OPML file opened from Files or
 * another app. Drag to look around and pinch to zoom; save it as a picture or a PDF, or make it a
 * mind-map note to work on.
 */
@Composable
internal fun MindMapPreview(
    vm: AppViewModel,
    doc: MindDoc,
    title: String,
    /** Make it a mind-map note; by default the preview closes and the new note opens. */
    onMake: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val c = clear
    val d = LocalDensity.current.density
    val context = LocalContext.current
    remember(context) { MapType.load(context) }
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.mindmap_title)
    val rootHint = stringResource(R.string.mindmap_root_hint)
    val hint = stringResource(R.string.mindmap_node_hint)
    val layout = remember(doc, d) { layoutMind(doc, d, rootHint, hint) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember(layout) { mutableStateOf(false) }
    val empty = doc.root.children.isEmpty()
    BackHandler(onBack = onClose)

    fun save(kind: String) {
        val copy = mindFromJson(doc.toJson()) ?: return
        val name = safeName(title, fallback)
        val dark = c.dark
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching { File(context.cacheDir, "map-${System.nanoTime()}.$kind").also { writeMind(copy, d, dark, kind, it, rootHint, hint) } }.getOrNull()
            }
            if (file == null) vm.tell(context.getString(R.string.common_file_unreadable))
            else vm.deliverFile(file, "$name.$kind", if (kind == "pdf") "application/pdf" else "image/png")
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(if (c.dark) Color(0xFF14152B) else Color(0xFFF6F7FB))) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).onSizeChanged { size ->
                if (!fitted && size.width > 0) {
                    val b = layout.bounds
                    val s = min(size.width * 0.9f / b.width().coerceAtLeast(1f), size.height * 0.7f / b.height().coerceAtLeast(1f)).coerceIn(0.25f, 1.2f)
                    scale = s
                    pan = Offset(size.width / 2f - b.centerX() * s, size.height / 2f - b.centerY() * s)
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
                            val next = (scale * zoom).coerceIn(0.2f, 3f)
                            pan = (pan - centroid) * (next / scale) + centroid + move
                            scale = next
                        }
                    },
                ) {
                    dots(pan, scale, d, (if (c.dark) Color.White else Color(0xFF1E2233)).copy(alpha = if (c.dark) 0.07f else 0.09f))
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.translate(pan.x, pan.y)
                        nc.scale(scale, scale)
                        drawMind(nc, layout, c.dark)
                        nc.restore()
                    }
                }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassBackButton(onClose, immediate = true)
                Text(
                    title.ifBlank { fallback }, style = ClearType.Title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                if (!empty) {
                    GlassCircle(onClick = { save("png") }, label = stringResource(R.string.mindmap_save_image)) {
                        GlassIcon(LucideIcons.ImagePlus, null, Modifier.size(20.dp))
                    }
                    GlassCircle(onClick = { save("pdf") }, label = stringResource(R.string.mindmap_save_pdf)) {
                        GlassIcon(LucideIcons.FilePdf, null, Modifier.size(20.dp))
                    }
                }
            }
            if (!empty) {
                GlassPill(
                    onClick = {
                        if (onMake != null) {
                            onMake()
                        } else {
                            onClose()
                            vm.newMindMap(doc)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
                    tint = Candy.Grape,
                    height = 50.dp,
                    label = stringResource(R.string.mindmap_make),
                ) {
                    Icon(LucideIcons.Network, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mindmap_make), style = ClearType.BodyStrong, color = Color.White)
                }
            }
        }
    }
}

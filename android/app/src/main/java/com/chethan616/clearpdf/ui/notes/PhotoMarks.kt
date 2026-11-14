package com.chethan616.clearpdf.ui.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- the frame: turns, mirror, straighten and crop -------------------------------------------------

/**
 * A crop, as LightCraft keeps it: a rectangle normalized to the turned picture (x / width,
 * y / height), axis-aligned in the frame the straighten angle turns the picture in.
 */
internal data class CropBox(val x0: Float = 0f, val y0: Float = 0f, val x1: Float = 1f, val y1: Float = 1f) {
    val w: Float get() = x1 - x0
    val h: Float get() = y1 - y0
    val cx: Float get() = (x0 + x1) / 2f
    val cy: Float get() = (y0 + y1) / 2f
    val isWhole: Boolean get() = x0 <= 1e-4f && y0 <= 1e-4f && x1 >= 1f - 1e-4f && y1 >= 1f - 1e-4f

    fun moved(dx: Float, dy: Float) = CropBox(x0 + dx, y0 + dy, x1 + dx, y1 + dy)

    /** This crop on the picture turned a quarter clockwise: (x, y) goes to (1 - y, x). */
    fun turned() = CropBox(1f - y1, x0, 1f - y0, x1)

    /** This crop on the picture mirrored left to right. */
    fun mirrored() = CropBox(1f - x1, y0, 1f - x0, y1)
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mix(a: CropBox, b: CropBox, t: Float) = CropBox(mix(a.x0, b.x0, t), mix(a.y0, b.y0, t), mix(a.x1, b.x1, t), mix(a.y1, b.y1, t))

/**
 * How the picture is framed: [turns] quarter turns clockwise after an optional [mirror], then
 * straightened by [angle] degrees (clockwise) and cropped to [crop].
 */
internal data class PhotoFrame(val turns: Int = 0, val mirror: Boolean = false, val angle: Float = 0f, val crop: CropBox = CropBox()) {
    /** LightCraft's orientation for the turns and the mirror (its `Orientation::from_parts`). */
    val orientation: String
        get() = when (turns.mod(4)) {
            0 -> if (mirror) "FlipH" else "Normal"
            1 -> if (mirror) "Transverse" else "Rotate90"
            2 -> if (mirror) "FlipV" else "Rotate180"
            else -> if (mirror) "Transpose" else "Rotate270"
        }
    val isPlain: Boolean get() = turns.mod(4) == 0 && !mirror && angle == 0f && crop.isWhole
    val hasCrop: Boolean get() = angle != 0f || !crop.isWhole
}

/** Whether every corner of [r] lies on the picture ([w] × [h]) once it is straightened by [angle]. */
internal fun fits(r: CropBox, angle: Float, w: Float, h: Float): Boolean {
    if (r.x0 > r.x1 || r.y0 > r.y1) return false
    val a = Math.toRadians(-angle.toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val eps = 1e-3f
    for ((nx, ny) in arrayOf(r.x0 to r.y0, r.x1 to r.y0, r.x1 to r.y1, r.x0 to r.y1)) {
        val dx = nx * w - w / 2f
        val dy = ny * h - h / 2f
        val qx = (w / 2f + dx * c - dy * s) / w
        val qy = (h / 2f + dx * s + dy * c) / h
        if (qx < -eps || qy < -eps || qx > 1f + eps || qy > 1f + eps) return false
    }
    return true
}

/** As far from [from] towards [to] as the crop can go and stay on the picture. */
internal fun constrain(from: CropBox, to: CropBox, angle: Float, w: Float, h: Float): CropBox {
    if (fits(to, angle, w, h)) return to
    if (!fits(from, angle, w, h)) return fitInside(from, angle, w, h)
    var lo = 0f
    var hi = 1f
    repeat(18) {
        val m = (lo + hi) / 2f
        if (fits(mix(from, to, m), angle, w, h)) lo = m else hi = m
    }
    return mix(from, to, lo)
}

/** [r], shrunk (and drawn towards the middle) until it lies on the picture straightened by [angle]. */
internal fun fitInside(r: CropBox, angle: Float, w: Float, h: Float): CropBox {
    if (fits(r, angle, w, h)) return r
    fun at(t: Float): CropBox {
        val cx = mix(0.5f, r.cx, t)
        val cy = mix(0.5f, r.cy, t)
        return CropBox(cx - r.w * t / 2f, cy - r.h * t / 2f, cx + r.w * t / 2f, cy + r.h * t / 2f)
    }
    var lo = 0f
    var hi = 1f
    repeat(20) {
        val m = (lo + hi) / 2f
        if (fits(at(m), angle, w, h)) lo = m else hi = m
    }
    return at(lo)
}

/** The largest crop of [aspect] (width over height, in pixels) about the middle of [around]. */
internal fun largest(aspect: Float, around: CropBox, angle: Float, w: Float, h: Float): CropBox {
    val pic = w / h
    val nw = min(1f, aspect / pic)
    val nh = min(1f, pic / aspect)
    val whole = CropBox(around.cx - nw / 2f, around.cy - nh / 2f, around.cx + nw / 2f, around.cy + nh / 2f)
    // kept on the picture's own square first, then on the straightened picture
    val dx = when {
        whole.x0 < 0f -> -whole.x0
        whole.x1 > 1f -> 1f - whole.x1
        else -> 0f
    }
    val dy = when {
        whole.y0 < 0f -> -whole.y0
        whole.y1 > 1f -> 1f - whole.y1
        else -> 0f
    }
    return fitInside(whole.moved(dx, dy), angle, w, h)
}

// ---- what is drawn on the picture ------------------------------------------------------------------

internal const val SMUDGE_BLUR = 0
internal const val SMUDGE_PIXELS = 1
internal const val SMUDGE_ERASE = 2

/** Something laid on the picture, placed in the turned (not yet straightened or cropped) picture. */
internal sealed class Mark

/** A brush stroke that blurs or pixelates what is under it, or takes that back ([SMUDGE_ERASE]). */
internal class Smudge(val kind: Int, val width: Float) : Mark() {
    /** Normalized to the turned picture; [width] is a share of its long side. */
    val points = ArrayList<PointF>()
}

/**
 * Words on the picture: centred at ([x], [y]) of the turned picture, [size] a share of its long
 * side, turned [rotation] degrees, in [color] and one of the fonts and styles ([style]: 0 plain
 * with a soft shadow, 1 on a filled tag, 2 outlined).
 */
internal class Label(
    var text: String,
    var x: Float,
    var y: Float,
    var size: Float = 0.06f,
    var rotation: Float = 0f,
    var color: Int = android.graphics.Color.WHITE,
    var style: Int = 0,
    var font: Int = 0,
) : Mark()

internal const val LABEL_FONTS = 4
internal const val LABEL_STYLES = 3

/** The marks follow the picture as it turns a quarter clockwise. */
internal fun Mark.turn() {
    when (this) {
        is Smudge -> points.forEach { val x = it.x; it.x = 1f - it.y; it.y = x }
        is Label -> {
            val x0 = x
            x = 1f - y
            y = x0
            rotation += 90f
        }
    }
}

/** The marks follow the picture as it is mirrored. */
internal fun Mark.mirror() {
    when (this) {
        is Smudge -> points.forEach { it.x = 1f - it.x }
        is Label -> {
            x = 1f - x
            rotation = -rotation
        }
    }
}

/**
 * Where the turned picture ([w] × [h], any unit) lands in a developed output of [outW] × [outH]
 * pixels: straightened by [angle] about its middle, then cropped to [crop] (LightCraft's
 * `output_to_source`, the other way round).
 */
internal class Placement(val w: Float, val h: Float, val angle: Float, val crop: CropBox, val outW: Float, val outH: Float) {
    private val c = cos(Math.toRadians(angle.toDouble())).toFloat()
    private val s = sin(Math.toRadians(angle.toDouble())).toFloat()

    /** Output pixels to a unit of the turned picture. */
    val scale = outW / (crop.w * w).coerceAtLeast(1e-6f)

    /** The turned picture's long side, in output pixels: what mark sizes are shares of. */
    val long = max(w, h) * scale

    fun out(nx: Float, ny: Float, into: PointF = PointF()): PointF {
        val dx = nx * w - w / 2f
        val dy = ny * h - h / 2f
        val rx = w / 2f + dx * c - dy * s
        val ry = h / 2f + dx * s + dy * c
        into.set((rx - crop.x0 * w) * scale, (ry - crop.y0 * h) * scale)
        return into
    }

    fun back(ox: Float, oy: Float): PointF {
        val rx = ox / scale + crop.x0 * w
        val ry = oy / scale + crop.y0 * h
        val dx = rx - w / 2f
        val dy = ry - h / 2f
        return PointF((w / 2f + dx * c + dy * s) / w, (h / 2f - dx * s + dy * c) / h)
    }
}

/** The picture blurred and pixelated, small: drawn stretched under the brush strokes. */
internal class Fx(val blur: Bitmap, val pixels: Bitmap)

/**
 * [picture] blurred and pixelated. Both are made at a fixed share of the picture's long side, so
 * the small preview and the full-size save look the same.
 */
internal fun fxOf(picture: Bitmap): Fx {
    val long = max(picture.width, picture.height).toFloat()
    val k = 320f / long
    val small = Bitmap.createScaledBitmap(picture, max(1, (picture.width * k).roundToInt()), max(1, (picture.height * k).roundToInt()), true)
    val px = IntArray(small.width * small.height)
    small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
    boxBlur(px, small.width, small.height, 7)
    val blur = Bitmap.createBitmap(px, small.width, small.height, Bitmap.Config.ARGB_8888)
    if (small !== picture) small.recycle()
    val q = 44f / long
    val pixels = Bitmap.createScaledBitmap(picture, max(1, (picture.width * q).roundToInt()), max(1, (picture.height * q).roundToInt()), true)
    return Fx(blur, pixels)
}

/** Three box blurs across and down: close to a Gaussian of about [r] pixels. */
private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(px.size)
    repeat(3) {
        boxPass(px, tmp, w, h, r, across = true)
        boxPass(tmp, px, w, h, r, across = false)
    }
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, across: Boolean) {
    val lines = if (across) h else w
    val len = if (across) w else h
    val div = 2 * r + 1
    for (line in 0 until lines) {
        val base = if (across) line * w else line
        val stride = if (across) 1 else w
        var a = 0
        var rr = 0
        var g = 0
        var b = 0
        for (i in -r..r) {
            val c = src[base + i.coerceIn(0, len - 1) * stride]
            a += c ushr 24
            rr += (c shr 16) and 255
            g += (c shr 8) and 255
            b += c and 255
        }
        for (i in 0 until len) {
            dst[base + i * stride] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
            val add = src[base + (i + r + 1).coerceAtMost(len - 1) * stride]
            val rem = src[base + (i - r).coerceAtLeast(0) * stride]
            a += (add ushr 24) - (rem ushr 24)
            rr += ((add shr 16) and 255) - ((rem shr 16) and 255)
            g += ((add shr 8) and 255) - ((rem shr 8) and 255)
            b += (add and 255) - (rem and 255)
        }
    }
}

internal fun labelFont(i: Int): Typeface = when (i) {
    1 -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
    2 -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    3 -> Typeface.create("cursive", Typeface.BOLD)
    else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}

private fun light(color: Int): Boolean {
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    return 0.299 * r + 0.587 * g + 0.114 * b > 165
}

private fun labelLayout(l: Label, size: Float): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size.coerceAtLeast(1f)
        typeface = labelFont(l.font)
    }
    val text = l.text.ifEmpty { " " }
    val width = ceil(text.lines().maxOf { Layout.getDesiredWidth(it, paint) }).toInt().coerceAtLeast(1)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()
}

/** A label's box: its middle in output pixels, half its width and height, and how far it is turned. */
internal class LabelBox(val cx: Float, val cy: Float, val halfW: Float, val halfH: Float, val degrees: Float) {
    fun holds(x: Float, y: Float, slop: Float): Boolean {
        val a = Math.toRadians(-degrees.toDouble())
        val dx = x - cx
        val dy = y - cy
        val lx = dx * cos(a) - dy * sin(a)
        val ly = dx * sin(a) + dy * cos(a)
        return abs(lx) <= halfW + slop && abs(ly) <= halfH + slop
    }
}

internal fun labelBox(l: Label, place: Placement): LabelBox {
    val p = place.out(l.x, l.y)
    val size = l.size * place.long
    val layout = labelLayout(l, size)
    val pad = size * 0.34f
    return LabelBox(p.x, p.y, layout.width / 2f + pad, layout.height / 2f + pad * 0.6f, l.rotation + place.angle)
}

private fun drawLabel(canvas: Canvas, place: Placement, l: Label, chosen: Boolean) {
    val p = place.out(l.x, l.y)
    val size = l.size * place.long
    if (size < 1f) return
    val layout = labelLayout(l, size)
    val paint = layout.paint
    val pad = size * 0.34f
    val box = RectF(-pad, -pad * 0.6f, layout.width + pad, layout.height + pad * 0.6f)
    canvas.save()
    canvas.translate(p.x, p.y)
    canvas.rotate(l.rotation + place.angle)
    canvas.translate(-layout.width / 2f, -layout.height / 2f)
    val onLight = light(l.color)
    when (l.style) {
        1 -> {
            val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.color }
            canvas.drawRoundRect(box, size * 0.36f, size * 0.36f, tag)
            paint.color = if (onLight) 0xFF141414.toInt() else android.graphics.Color.WHITE
            layout.draw(canvas)
        }
        2 -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.14f
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = if (onLight) 0xFF141414.toInt() else android.graphics.Color.WHITE
            layout.draw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = l.color
            layout.draw(canvas)
        }
        else -> {
            paint.color = l.color
            paint.setShadowLayer(size * 0.12f, 0f, size * 0.04f, if (onLight) 0x80000000.toInt() else 0x59FFFFFF)
            layout.draw(canvas)
        }
    }
    if (chosen) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (place.long * 0.003f).coerceAtLeast(1.5f)
            color = android.graphics.Color.WHITE
            pathEffect = DashPathEffect(floatArrayOf(place.long * 0.012f, place.long * 0.008f), 0f)
            setShadowLayer(place.long * 0.004f, 0f, 0f, 0x99000000.toInt())
        }
        box.inset(-pad * 0.4f, -pad * 0.4f)
        canvas.drawRoundRect(box, size * 0.4f, size * 0.4f, frame)
    }
    canvas.restore()
}

/**
 * Lays [marks] on a developed picture drawn at (0, 0) to ([Placement.outW], [Placement.outH]) on
 * [canvas]: the brush strokes, in order, as masks over the blurred and pixelated picture in [fx]
 * (an eraser stroke takes back what the strokes before it laid), then the words. The same for the
 * editor's preview and for the picture saved.
 */
internal fun paintMarks(canvas: Canvas, place: Placement, marks: List<Mark>, fx: Fx?, chosen: Label? = null) {
    val smudges = marks.filterIsInstance<Smudge>()
    if (fx != null && smudges.isNotEmpty()) {
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.BLACK
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val path = Path()
        val pt = PointF()
        // the layer covers only where strokes are, so a full-size save doesn't need a second picture
        val reach = RectF()
        var first = true
        for (sm in smudges) {
            val half = sm.width * place.long / 2f + 2f
            for (p in sm.points) {
                place.out(p.x, p.y, pt)
                if (first) {
                    reach.set(pt.x - half, pt.y - half, pt.x + half, pt.y + half)
                    first = false
                } else {
                    reach.union(pt.x - half, pt.y - half, pt.x + half, pt.y + half)
                }
            }
        }
        val whole = RectF(0f, 0f, place.outW, place.outH)
        if (!first && reach.intersect(whole)) {
            for (kind in intArrayOf(SMUDGE_BLUR, SMUDGE_PIXELS)) {
                if (smudges.none { it.kind == kind }) continue
                val layer = canvas.saveLayer(reach, null)
                for (sm in smudges) {
                    if (sm.kind != kind && sm.kind != SMUDGE_ERASE) continue
                    val width = sm.width * place.long
                    val mode = if (sm.kind == SMUDGE_ERASE) clear else null
                    brush.xfermode = mode
                    dot.xfermode = mode
                    brush.strokeWidth = width
                    if (sm.points.size == 1) {
                        place.out(sm.points[0].x, sm.points[0].y, pt)
                        canvas.drawCircle(pt.x, pt.y, width / 2f, dot)
                        continue
                    }
                    path.reset()
                    sm.points.forEachIndexed { i, p ->
                        place.out(p.x, p.y, pt)
                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    canvas.drawPath(path, brush)
                }
                val over = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    isFilterBitmap = kind == SMUDGE_BLUR
                }
                canvas.drawBitmap(if (kind == SMUDGE_BLUR) fx.blur else fx.pixels, null, whole, over)
                canvas.restoreToCount(layer)
            }
        }
    }
    for (l in marks) if (l is Label) drawLabel(canvas, place, l, l === chosen)
}

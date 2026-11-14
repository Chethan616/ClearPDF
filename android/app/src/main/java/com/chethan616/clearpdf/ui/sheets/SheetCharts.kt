package com.chethan616.clearpdf.ui.sheets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * A chart of a sheet as a picture: GridCraft lays the chart out (bars, lines, wedges, axes and
 * every label) as drawing steps in points, and they are painted here with the phone's own text,
 * at [scale] pixels a point (at most [maxPx] on the long side), so it reads as sharply as the cells.
 */
internal fun paintChart(prims: String, width: Float, height: Float, scale: Float, maxPx: Int = 2400): Bitmap? {
    if (width <= 0f || height <= 0f) return null
    val k = min(scale, maxPx / max(width, height))
    val bitmap = Bitmap.createBitmap(max(1, (width * k).toInt()), max(1, (height * k).toInt()), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(k, k)
    val steps = runCatching { JSONArray(prims) }.getOrNull() ?: return null
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val path = Path()
    val box = RectF()

    fun color(a: JSONArray?): Int? {
        if (a == null || a.length() < 4) return null
        return android.graphics.Color.argb(a.optInt(3), a.optInt(0), a.optInt(1), a.optInt(2))
    }
    fun stroke(o: JSONObject): Boolean {
        val s = o.optJSONArray("stroke") ?: return false
        line.color = color(s.optJSONArray(0)) ?: return false
        line.strokeWidth = s.optDouble(1, 1.0).toFloat().coerceAtLeast(0.5f)
        line.pathEffect = null
        return true
    }
    fun points(a: JSONArray?, close: Boolean): Boolean {
        path.reset()
        if (a == null || a.length() == 0) return false
        for (i in 0 until a.length()) {
            val p = a.optJSONArray(i) ?: continue
            val x = p.optDouble(0).toFloat()
            val y = p.optDouble(1).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (close) path.close()
        return true
    }

    for (i in 0 until steps.length()) {
        val step = steps.optJSONObject(i) ?: continue
        val kind = step.keys().asSequence().firstOrNull() ?: continue
        val o = step.optJSONObject(kind) ?: continue
        when (kind) {
            "Rect" -> {
                val x = o.optDouble("x").toFloat()
                val y = o.optDouble("y").toFloat()
                box.set(x, y, x + o.optDouble("w").toFloat(), y + o.optDouble("h").toFloat())
                val r = o.optDouble("radius").toFloat()
                color(o.optJSONArray("fill"))?.let {
                    fill.color = it
                    canvas.drawRoundRect(box, r, r, fill)
                }
                if (stroke(o)) canvas.drawRoundRect(box, r, r, line)
            }
            "Line" -> {
                if (!points(o.optJSONArray("pts"), close = false)) continue
                line.color = color(o.optJSONArray("color")) ?: continue
                val w = o.optDouble("width", 1.0).toFloat().coerceAtLeast(0.5f)
                line.strokeWidth = w
                line.pathEffect = if (o.optBoolean("dash")) DashPathEffect(floatArrayOf(w * 4f, w * 3f), 0f) else null
                canvas.drawPath(path, line)
            }
            "Polygon" -> {
                if (!points(o.optJSONArray("pts"), close = true)) continue
                color(o.optJSONArray("fill"))?.let {
                    fill.color = it
                    canvas.drawPath(path, fill)
                }
                if (stroke(o)) canvas.drawPath(path, line)
            }
            "Wedge" -> {
                // angles from 12 o'clock, clockwise, in radians; Android's arcs start at 3 o'clock
                val cx = o.optDouble("cx").toFloat()
                val cy = o.optDouble("cy").toFloat()
                val ro = o.optDouble("r_outer").toFloat()
                val ri = o.optDouble("r_inner").toFloat()
                val a0 = Math.toDegrees(o.optDouble("a0")).toFloat() - 90f
                val sweep = Math.toDegrees(o.optDouble("a1") - o.optDouble("a0")).toFloat()
                path.reset()
                if (sweep >= 359.99f) {
                    path.addCircle(cx, cy, ro, Path.Direction.CW)
                    if (ri > 0f) path.addCircle(cx, cy, ri, Path.Direction.CCW)
                } else {
                    box.set(cx - ro, cy - ro, cx + ro, cy + ro)
                    path.arcTo(box, a0, sweep, true)
                    if (ri > 0f) {
                        box.set(cx - ri, cy - ri, cx + ri, cy + ri)
                        path.arcTo(box, a0 + sweep, -sweep)
                    } else {
                        path.lineTo(cx, cy)
                    }
                    path.close()
                }
                fill.color = color(o.optJSONArray("fill")) ?: continue
                canvas.drawPath(path, fill)
                if (stroke(o)) canvas.drawPath(path, line)
            }
            "Circle" -> {
                val cx = o.optDouble("cx").toFloat()
                val cy = o.optDouble("cy").toFloat()
                val r = o.optDouble("r").toFloat()
                color(o.optJSONArray("fill"))?.let {
                    fill.color = it
                    canvas.drawCircle(cx, cy, r, fill)
                }
                if (stroke(o)) canvas.drawCircle(cx, cy, r, line)
            }
            "Text" -> {
                val words = o.optString("text")
                if (words.isEmpty()) continue
                text.color = color(o.optJSONArray("color")) ?: android.graphics.Color.BLACK
                text.textSize = o.optDouble("size", 10.0).toFloat()
                text.typeface = if (o.optBoolean("bold")) bold else regular
                text.textAlign = when (o.optString("align")) {
                    "Center" -> Paint.Align.CENTER
                    "Right" -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }
                val x = o.optDouble("x").toFloat()
                val y = o.optDouble("y").toFloat()
                val fm = text.fontMetrics
                val baseline = when (o.optString("valign")) {
                    "Middle" -> y - (fm.ascent + fm.descent) / 2f
                    "Bottom" -> y - fm.descent
                    else -> y - fm.ascent
                }
                val turn = Math.toDegrees(o.optDouble("rotation")).toFloat()
                if (turn != 0f) {
                    canvas.save()
                    canvas.rotate(turn, x, y)
                    canvas.drawText(words, x, baseline, text)
                    canvas.restore()
                } else {
                    canvas.drawText(words, x, baseline, text)
                }
            }
        }
    }
    return bitmap
}

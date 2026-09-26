/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 * Modified by ClearPDF (2026): shape/arrow geometry extracted from
 * feature/draw/presentation/components/utils/PathHelper.kt, rewritten against
 * android.graphics.Path so the same code serves the live preview and full-resolution replay.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.chethan616.clearpdf.imageeditor.engine.draw

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import com.chethan616.clearpdf.imageeditor.engine.ShapeKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ShapePaths {

    /**
     * Builds the path for [shape] from pixel-space [points]. Freehand shapes use every point;
     * geometric shapes use the first (down) and last (current) point as the bounding box.
     */
    fun build(
        shape: ShapeKind,
        points: List<PointF>,
        strokeWidthPx: Float,
        polygonVertices: Int = 5,
        seed: Int = 0
    ): Path {
        if (points.isEmpty()) return Path()
        val down = points.first()
        val cur = points.last()
        return when (shape) {
            ShapeKind.Free, ShapeKind.Lasso -> smooth(points, close = shape == ShapeKind.Lasso)
            ShapeKind.Arrow -> smooth(points, false).also { endArrow(it, strokeWidthPx) }
            ShapeKind.DoubleArrow -> smooth(points, false).also {
                endArrow(it, strokeWidthPx); startArrow(it, strokeWidthPx)
            }
            ShapeKind.Line -> line(down, cur)
            ShapeKind.LineArrow -> line(down, cur).also { endArrow(it, strokeWidthPx) }
            ShapeKind.DoubleLineArrow -> line(down, cur).also {
                endArrow(it, strokeWidthPx); startArrow(it, strokeWidthPx)
            }
            ShapeKind.Rect, ShapeKind.OutlinedRect -> Path().apply {
                addRect(box(down, cur), Path.Direction.CW)
            }
            ShapeKind.Oval, ShapeKind.OutlinedOval -> Path().apply {
                addOval(box(down, cur), Path.Direction.CW)
            }
            ShapeKind.Triangle, ShapeKind.OutlinedTriangle -> triangle(down, cur)
            ShapeKind.Polygon, ShapeKind.OutlinedPolygon -> polygon(down, cur, polygonVertices, 0, false)
            ShapeKind.Star, ShapeKind.OutlinedStar -> star(down, cur, polygonVertices, 0.5f, 0, false)
            ShapeKind.Spray -> spray(points, strokeWidthPx, seed)
            ShapeKind.FloodFill -> Path() // resolved against the bitmap by the renderer
        }
    }

    private fun box(a: PointF, b: PointF) = RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

    private fun line(a: PointF, b: PointF) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }

    /** Quadratic smoothing through the midpoints of consecutive samples. */
    fun smooth(points: List<PointF>, close: Boolean): Path = Path().apply {
        val first = points.first()
        moveTo(first.x, first.y)
        if (points.size == 1) {
            lineTo(first.x + 0.01f, first.y + 0.01f)
            return@apply
        }
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        }
        val last = points.last()
        lineTo(last.x, last.y)
        if (close) close()
    }

    private fun triangle(down: PointF, cur: PointF) = Path().apply {
        moveTo(down.x, down.y)
        lineTo(cur.x, down.y)
        lineTo((down.x + cur.x) / 2, cur.y)
        lineTo(down.x, down.y)
        close()
    }

    private fun polygon(down: PointF, cur: PointF, vertices: Int, rotationDegrees: Int, isRegular: Boolean): Path {
        val left = min(down.x, cur.x); val right = max(down.x, cur.x)
        val top = min(down.y, cur.y); val bottom = max(down.y, cur.y)
        val width = right - left; val height = bottom - top
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        return Path().apply {
            if (isRegular) {
                val radius = min(width, height) / 2f
                val step = 360f / vertices
                val start = rotationDegrees - 270.0
                for (i in 0 until vertices) {
                    val a = Math.toRadians(start + i * step)
                    val x = cx + radius * cos(a).toFloat(); val y = cy + radius * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            } else {
                for (i in 0 until vertices) {
                    val a = Math.toRadians(i * (360.0 / vertices) + rotationDegrees - 270.0)
                    val x = cx + width / 2f * cos(a).toFloat(); val y = cy + height / 2f * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            close()
        }
    }

    private fun star(down: PointF, cur: PointF, vertices: Int, innerRatio: Float, rotationDegrees: Int, isRegular: Boolean): Path {
        val left = min(down.x, cur.x); val right = max(down.x, cur.x)
        val top = min(down.y, cur.y); val bottom = max(down.y, cur.y)
        val width = right - left; val height = bottom - top
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        return Path().apply {
            for (i in 0 until 2 * vertices) {
                val a = Math.toRadians(i * (360.0 / (2 * vertices)) + rotationDegrees - 270.0)
                val (rx, ry) = if (isRegular) {
                    val outer = min(width, height) / 2f
                    val r = if (i % 2 == 0) outer else outer * innerRatio
                    r to r
                } else {
                    (if (i % 2 == 0) width else width * innerRatio) / 2f to
                        (if (i % 2 == 0) height else height * innerRatio) / 2f
                }
                val x = cx + rx * cos(a).toFloat(); val y = cy + ry * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /** Deterministic spray: the same seed yields the same dots at any resolution. */
    private fun spray(points: List<PointF>, radius: Float, seed: Int): Path = Path().apply {
        val rnd = Random(seed)
        val dot = max(1f, radius / 18f)
        points.forEach { p ->
            repeat(40) {
                val angle = rnd.nextFloat() * PI_2
                val r = sqrt(rnd.nextFloat()) * radius
                val x = p.x + r * cos(angle)
                val y = p.y + r * sin(angle)
                addOval(RectF(x, y, x + dot, y + dot), Path.Direction.CW)
            }
        }
    }

    private fun endArrow(path: Path, strokeWidth: Float, sizeScale: Float = 3f, angle: Double = 150.0) {
        val pm = PathMeasure(path, false)
        val len = pm.length
        if (len <= 0f) return
        val last = FloatArray(2); val pre = FloatArray(2)
        pm.getPosTan(len, last, null)
        pm.getPosTan(max(0f, len - strokeWidth * sizeScale), pre, null)
        addArrowHead(path, last[0], last[1], last[0] - pre[0], last[1] - pre[1], strokeWidth * sizeScale, angle)
    }

    private fun startArrow(path: Path, strokeWidth: Float, sizeScale: Float = 3f, angle: Double = 150.0) {
        val pm = PathMeasure(path, false)
        val len = pm.length
        if (len <= 0f) return
        val first = FloatArray(2); val second = FloatArray(2)
        pm.getPosTan(0f, first, null)
        pm.getPosTan(minOf(len, strokeWidth * sizeScale), second, null)
        addArrowHead(path, first[0], first[1], first[0] - second[0], first[1] - second[1], strokeWidth * sizeScale, angle)
    }

    private fun addArrowHead(path: Path, tipX: Float, tipY: Float, vx0: Float, vy0: Float, size: Float, angle: Double) {
        val n = sqrt(vx0 * vx0 + vy0 * vy0)
        if (n < 0.0001f || abs(size) < 0.0001f) return
        val vx = vx0 / n * size; val vy = vy0 / n * size
        fun rot(deg: Double): Pair<Float, Float> {
            val r = Math.toRadians(deg)
            return (vx * cos(r) - vy * sin(r)).toFloat() to (vx * sin(r) + vy * cos(r)).toFloat()
        }
        val (x1, y1) = rot(angle)
        val (x2, y2) = rot(360 - angle)
        path.moveTo(tipX, tipY); path.rLineTo(x1, y1)
        path.moveTo(tipX, tipY); path.rLineTo(x2, y2)
    }

    private const val PI_2 = (Math.PI * 2).toFloat()
}

/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 * Modified by ClearPDF (2026): paint set-up for pen / highlighter / neon / eraser and the
 * path-effect (blur, pixelate) brushes adapted from feature/draw DrawUtils.createDrawPaint and
 * data/AndroidImageDrawApplier, working on normalised strokes with android.graphics.
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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.chethan616.clearpdf.imageeditor.engine.BlurUtils
import com.chethan616.clearpdf.imageeditor.engine.BrushKind
import com.chethan616.clearpdf.imageeditor.engine.DrawStroke
import com.chethan616.clearpdf.imageeditor.engine.ShapeKind
import kotlin.math.min

object StrokeRenderer {

    /**
     * Renders [strokes] onto a copy of [base]. Strokes go onto a transparent layer first so the
     * eraser removes drawing only, then the layer is composited over the image.
     */
    fun render(base: Bitmap, strokes: List<DrawStroke>): Bitmap {
        val w = base.width
        val h = base.height
        val short = min(w, h).toFloat()
        val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        var blurred: Bitmap? = null
        val pixelated = HashMap<Int, Bitmap>()
        for (s in strokes) {
            val pts = s.points.map { PointF(it.x * w, it.y * h) }
            val widthPx = (s.width * short).coerceAtLeast(1f)
            if (s.shape == ShapeKind.FloodFill && s.brush != BrushKind.Eraser) {
                val p = pts.firstOrNull() ?: continue
                // Flood fill samples the image as it currently looks (base + drawing so far).
                val probe = composite(base, layer)
                probe.floodFillPath(p.x.toInt(), p.y.toInt(), s.floodTolerance)?.let { path ->
                    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.color; style = Paint.Style.FILL })
                }
                probe.recycle()
                continue
            }
            val path = ShapePaths.build(s.shape, pts, widthPx, s.polygonVertices, s.seed)
            val paint = paintFor(s, widthPx, short)
            when (s.brush) {
                BrushKind.Blur -> {
                    val src = blurred ?: BlurUtils.blur(base, s.effectStrength * short).also { blurred = it }
                    paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    paint.color = Color.BLACK
                }
                BrushKind.Pixelate -> {
                    val block = (s.effectStrength * short).toInt().coerceAtLeast(2)
                    val src = pixelated.getOrPut(block) { BlurUtils.pixelate(base, block.toFloat()) }
                    paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    paint.color = Color.BLACK
                }
                else -> Unit
            }
            canvas.drawPath(path, paint)
        }
        val out = composite(base, layer)
        layer.recycle()
        blurred?.recycle()
        pixelated.values.forEach { it.recycle() }
        return out
    }

    fun paintFor(s: DrawStroke, widthPx: Float, short: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        val filled = s.shape.filled && s.brush != BrushKind.Eraser
        if (filled) {
            style = Paint.Style.FILL
        } else {
            style = Paint.Style.STROKE
            strokeWidth = widthPx
            if (s.brush == BrushKind.Highlighter || s.shape.sharp) {
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.MITER
            } else {
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        color = s.color
        when (s.brush) {
            BrushKind.Eraser -> {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            BrushKind.Highlighter -> {
                // Highlighter: translucent flat ink regardless of chosen alpha.
                alpha = min(Color.alpha(s.color), 110)
            }
            BrushKind.Neon -> {
                color = Color.WHITE
                val glow = (s.softness.coerceAtLeast(0.2f) * widthPx * 2f).coerceAtLeast(2f)
                setShadowLayer(glow, 0f, 0f, (s.color and 0x00FFFFFF) or (0xCC shl 24))
            }
            else -> if (s.softness > 0f) {
                maskFilter = BlurMaskFilter((s.softness * widthPx).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    private fun composite(base: Bitmap, layer: Bitmap): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawBitmap(layer, 0f, 0f, null)
        return out
    }
}

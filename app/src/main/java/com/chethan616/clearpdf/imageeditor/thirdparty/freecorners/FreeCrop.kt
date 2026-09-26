/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 * Modified by ClearPDF (2026): OpenCV warpPerspective replaced with android.graphics.Matrix
 * setPolyToPoly so the perspective crop needs no native library.
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

package com.chethan616.clearpdf.imageeditor.thirdparty.freecorners

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import com.chethan616.clearpdf.imageeditor.thirdparty.freecorners.model.Quad
import com.chethan616.clearpdf.imageeditor.thirdparty.freecorners.model.distance
import kotlin.math.max
import kotlin.math.roundToInt

object FreeCrop {

    /**
     * Warps the quadrilateral [points] (TL, TR, BR, BL in bitmap pixels) of [bitmap] into an
     * upright rectangle. Output size follows the original OpenCV implementation: the shorter of
     * each pair of opposite edges.
     */
    fun crop(
        bitmap: Bitmap,
        points: List<Offset>
    ): Bitmap {
        val corners = Quad(
            topLeftCorner = PointF(points[0].x, points[0].y),
            topRightCorner = PointF(points[1].x, points[1].y),
            bottomRightCorner = PointF(points[2].x, points[2].y),
            bottomLeftCorner = PointF(points[3].x, points[3].y)
        )
        val tl = corners.topLeftCorner
        val tr = corners.topRightCorner
        val br = corners.bottomRightCorner
        val bl = corners.bottomLeftCorner

        val width = max(1, minOf(tl.distance(tr), bl.distance(br)).roundToInt())
        val height = max(1, minOf(tl.distance(bl), tr.distance(br)).roundToInt())

        val src = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dst = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return bitmap

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            bitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        )
        return out
    }
}

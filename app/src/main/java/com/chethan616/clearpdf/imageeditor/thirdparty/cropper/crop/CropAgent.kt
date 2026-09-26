/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 * Modified by ClearPDF (2026): repackaged and adapted for the ClearPDF image editor.
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

package com.chethan616.clearpdf.imageeditor.thirdparty.cropper.crop

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.scale
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropImageMask
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropOutline
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropPath
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropShape
import kotlin.math.roundToInt


/**
 * Crops imageBitmap based on path that is passed in [crop] function
 */
/** ClearPDF: made public and stripped of the URI/cache path — the editor replays crops itself. */
class CropAgent {

    private val imagePaint = Paint().apply {
        blendMode = BlendMode.SrcIn
    }

    private val paint = Paint()


    fun crop(
        imageBitmap: ImageBitmap,
        cropRect: Rect,
        cropOutline: CropOutline,
        layoutDirection: LayoutDirection,
        density: Density,
    ): ImageBitmap {
        return runCatching {
            val roundedCropRect = cropRect.roundedInBounds(
                width = imageBitmap.width,
                height = imageBitmap.height
            )
            val croppedBitmap: Bitmap = Bitmap.createBitmap(
                imageBitmap.asAndroidBitmap(),
                roundedCropRect.left,
                roundedCropRect.top,
                roundedCropRect.width,
                roundedCropRect.height,
            )

            val imageToCrop = croppedBitmap
                .copy(Bitmap.Config.ARGB_8888, true)
                .apply { setHasAlpha(true) }
                .asImageBitmap()

            drawCroppedImage(
                cropOutline = cropOutline,
                cropSize = roundedCropRect.size,
                layoutDirection = layoutDirection,
                density = density,
                imageToCrop = imageToCrop
            )

            imageToCrop
        }.getOrNull() ?: imageBitmap
    }

    private fun drawCroppedImage(
        cropOutline: CropOutline,
        cropSize: IntSize,
        layoutDirection: LayoutDirection,
        density: Density,
        imageToCrop: ImageBitmap,
    ) {

        when (cropOutline) {
            is CropShape -> {

                val path = Path().apply {
                    val outline =
                        cropOutline.shape.createOutline(cropSize.toSize(), layoutDirection, density)
                    addOutline(outline)
                }

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawPath(path, paint)

                    // Source
                    drawImage(
                        image = imageToCrop,
                        topLeftOffset = Offset.Zero,
                        paint = imagePaint
                    )
                    restore()
                }
            }

            is CropPath -> {

                val path = Path().apply {

                    addPath(cropOutline.path)

                    val pathSize = getBounds().size
                    val rectSize = cropSize.toSize()

                    val matrix = android.graphics.Matrix()
                    matrix.postScale(
                        rectSize.width / pathSize.width,
                        rectSize.height / pathSize.height
                    )
                    this.asAndroidPath().transform(matrix)

                    val left = getBounds().left
                    val top = getBounds().top

                    translate(Offset(-left, -top))
                }

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawPath(path, paint)

                    // Source
                    drawImage(image = imageToCrop, topLeftOffset = Offset.Zero, imagePaint)
                    restore()
                }
            }

            is CropImageMask -> {

                val imageMask = cropOutline.image.asAndroidBitmap()
                    .scale(cropSize.width, cropSize.height).asImageBitmap()

                Canvas(image = imageToCrop).run {
                    saveLayer(nativeCanvas.clipBounds.toComposeRect(), imagePaint)

                    // Destination
                    drawImage(imageMask, topLeftOffset = Offset.Zero, paint)

                    // Source
                    drawImage(image = imageToCrop, topLeftOffset = Offset.Zero, imagePaint)

                    restore()
                }
            }
        }
    }
}

internal fun Rect.roundedInBounds(
    width: Int,
    height: Int
): IntRect {
    val roundedWidth = this.width.roundToInt().coerceIn(1, width)
    val roundedHeight = this.height.roundToInt().coerceIn(1, height)
    val roundedLeft = left.roundToInt().coerceIn(0, width - roundedWidth)
    val roundedTop = top.roundToInt().coerceIn(0, height - roundedHeight)

    return IntRect(
        left = roundedLeft,
        top = roundedTop,
        right = roundedLeft + roundedWidth,
        bottom = roundedTop + roundedHeight
    )
}

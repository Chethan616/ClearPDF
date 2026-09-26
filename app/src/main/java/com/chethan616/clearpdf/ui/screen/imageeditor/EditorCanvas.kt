package com.chethan616.clearpdf.ui.screen.imageeditor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.chethan616.clearpdf.imageeditor.engine.BrushKind
import com.chethan616.clearpdf.imageeditor.engine.DrawStroke
import com.chethan616.clearpdf.imageeditor.engine.OverlayRenderer
import com.chethan616.clearpdf.imageeditor.engine.ShapeKind
import com.chethan616.clearpdf.imageeditor.engine.TextLayer
import com.chethan616.clearpdf.imageeditor.engine.draw.ShapePaths
import com.chethan616.clearpdf.imageeditor.engine.draw.StrokeRenderer
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel.BrushSettings
import kotlin.math.min
import kotlin.math.roundToInt

/** What the canvas does with touches. */
sealed interface StageInput {
    data object None : StageInput
    data class Brush(val settings: BrushSettings, val onStroke: (List<Offset>) -> Unit) : StageInput
    data class Mask(val width: Float, val restore: Boolean, val onStroke: (List<Offset>) -> Unit) : StageInput
    data class Text(val layer: TextLayer, val onChange: ((TextLayer) -> TextLayer) -> Unit) : StageInput
}

/** Fits [bitmap] inside [container] (ContentScale.Fit), centred. */
internal fun fitRect(container: IntSize, bw: Int, bh: Int): Rect {
    if (container.width <= 0 || container.height <= 0 || bw <= 0 || bh <= 0) return Rect.Zero
    val s = min(container.width / bw.toFloat(), container.height / bh.toFloat())
    val w = bw * s
    val h = bh * s
    val l = (container.width - w) / 2f
    val t = (container.height - h) / 2f
    return Rect(l, t, l + w, t + h)
}

/**
 * The document surface: the (edited) image fitted in the free area between the header and the
 * bottom dock, with live stroke / text previews drawn in image space so they match the export.
 */
@Composable
fun EditorStage(
    bitmap: Bitmap,
    contentPadding: PaddingValues,
    input: StageInput,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val rect = fitRect(size, bitmap.width, bitmap.height)
    val currentInput by rememberUpdatedState(input)
    val livePoints = remember { mutableStateListOf<Offset>() }

    fun toNorm(p: Offset): Offset = Offset(
        ((p.x - rect.left) / rect.width.coerceAtLeast(1f)),
        ((p.y - rect.top) / rect.height.coerceAtLeast(1f))
    )

    val gestureKey = when (input) {
        is StageInput.Brush -> input.settings
        is StageInput.Mask -> input.restore to input.width
        is StageInput.Text -> "text"
        StageInput.None -> "none"
    }

    Box(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .onSizeChanged { size = it }
            .pointerInput(gestureKey, rect) {
                when (val i = currentInput) {
                    is StageInput.Brush, is StageInput.Mask -> {
                        val geometric = i is StageInput.Brush && !i.settings.shape.isFreehand &&
                            i.settings.brush != BrushKind.Eraser
                        if (i is StageInput.Brush && i.settings.shape == ShapeKind.FloodFill && i.settings.brush != BrushKind.Eraser) {
                            detectTapGestures { p -> i.onStroke(listOf(toNorm(p))) }
                        } else {
                            detectDragGestures(
                                onDragStart = { p -> livePoints.clear(); livePoints.add(toNorm(p)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val n = toNorm(change.position)
                                    if (geometric && livePoints.size > 1) livePoints[1] = n else livePoints.add(n)
                                },
                                onDragEnd = {
                                    val pts = livePoints.toList()
                                    when (val c = currentInput) {
                                        is StageInput.Brush -> c.onStroke(pts)
                                        is StageInput.Mask -> c.onStroke(pts)
                                        else -> Unit
                                    }
                                    livePoints.clear()
                                },
                                onDragCancel = { livePoints.clear() }
                            )
                        }
                    }
                    is StageInput.Text -> detectTransformGestures { _, pan, zoom, rotation ->
                        val t = currentInput as? StageInput.Text ?: return@detectTransformGestures
                        t.onChange { l ->
                            l.copy(
                                center = Offset(
                                    (l.center.x + pan.x / rect.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                    (l.center.y + pan.y / rect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                ),
                                size = (l.size * zoom).coerceIn(0.015f, 0.6f),
                                rotation = l.rotation + rotation
                            )
                        }
                    }
                    StageInput.None -> Unit
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (rect.isEmpty) return@Canvas
            if (bitmap.hasAlpha()) drawChecker(rect)
            drawImage(
                image = image,
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                filterQuality = FilterQuality.Medium
            )
            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                when (val i = input) {
                    is StageInput.Brush -> if (livePoints.isNotEmpty()) drawLiveStroke(rect, i.settings, livePoints)
                    is StageInput.Mask -> if (livePoints.isNotEmpty()) drawLiveMask(rect, i, livePoints)
                    is StageInput.Text -> drawIntoCanvas { c ->
                        c.nativeCanvas.save()
                        c.nativeCanvas.translate(rect.left, rect.top)
                        OverlayRenderer.drawTextOn(c.nativeCanvas, rect.width.roundToInt(), rect.height.roundToInt(), i.layer)
                        c.nativeCanvas.restore()
                    }
                    StageInput.None -> Unit
                }
            }
        }
    }
}

private fun DrawScope.drawChecker(rect: Rect) {
    val cell = 12f * density
    val light = Color(0xFFF2F2F4)
    val dark = Color(0xFFD9DADF)
    drawRect(light, rect.topLeft, rect.size)
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        var y = rect.top
        var row = 0
        while (y < rect.bottom) {
            var x = rect.left + if (row % 2 == 0) 0f else cell
            while (x < rect.right) {
                drawRect(dark, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

private fun DrawScope.drawLiveStroke(rect: Rect, b: BrushSettings, pts: List<Offset>) {
    val short = min(rect.width, rect.height)
    val px = pts.map { PointF(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    val widthPx = (b.width * short).coerceAtLeast(1f)
    val shape = if (b.brush == BrushKind.Eraser) ShapeKind.Free else b.shape
    val argb = (b.color and 0x00FFFFFF) or ((b.alpha * 255).roundToInt().coerceIn(0, 255) shl 24)
    val stroke = DrawStroke(b.brush, shape, argb, b.width, b.softness, emptyList(), polygonVertices = b.vertices, seed = 7)
    val paint = StrokeRenderer.paintFor(stroke, widthPx, short).apply {
        when (b.brush) {
            // Hints for effects that only exist once rendered on the bitmap.
            BrushKind.Eraser -> { xfermode = null; color = android.graphics.Color.argb(150, 255, 255, 255) }
            BrushKind.Blur, BrushKind.Pixelate -> { color = android.graphics.Color.argb(110, 128, 128, 140) }
            else -> Unit
        }
    }
    val path = ShapePaths.build(shape, px, widthPx, b.vertices, 7)
    drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
}

private fun DrawScope.drawLiveMask(rect: Rect, m: StageInput.Mask, pts: List<Offset>) {
    val short = min(rect.width, rect.height)
    val px = pts.map { PointF(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (m.width * short).coerceAtLeast(1f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = if (m.restore) android.graphics.Color.argb(120, 48, 209, 88) else android.graphics.Color.argb(120, 255, 69, 58)
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(ShapePaths.smooth(px, false), paint) }
}

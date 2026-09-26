package com.chethan616.clearpdf.imageeditor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.roundToInt

/** Blur / pixelate helpers used by the blur & pixelate brushes (no native libraries). */
object BlurUtils {

    /** Gaussian-looking blur. API 31+: RenderEffect via HardwareRenderer; otherwise stack blur. */
    fun blur(src: Bitmap, radiusPx: Float): Bitmap {
        val r = radiusPx.coerceAtLeast(1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { renderEffectBlur(src, r) }.getOrNull()?.let { return it }
        }
        // Downscale first so large radii stay cheap, then stack blur and scale back.
        val factor = max(1f, r / 8f)
        val sw = max(1, (src.width / factor).roundToInt())
        val sh = max(1, (src.height / factor).roundToInt())
        val small = if (factor > 1f) src.scale(sw, sh, true) else src.copy(Bitmap.Config.ARGB_8888, true)
        stackBlur(small, (r / factor).roundToInt().coerceIn(1, 254))
        return if (factor > 1f) small.scale(src.width, src.height, true).also { small.recycle() } else small
    }

    fun pixelate(src: Bitmap, blockPx: Float): Bitmap {
        val b = blockPx.coerceAtLeast(2f)
        val sw = max(1, (src.width / b).roundToInt())
        val sh = max(1, (src.height / b).roundToInt())
        val small = src.scale(sw, sh, true)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(small, null, android.graphics.Rect(0, 0, out.width, out.height), Paint())
        small.recycle()
        return out
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun renderEffectBlur(src: Bitmap, radius: Float): Bitmap? {
        val w = src.width
        val h = src.height
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("editorBlur")
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            node.setPosition(0, 0, w, h)
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            val canvas = node.beginRecording()
            canvas.drawBitmap(src, 0f, 0f, null)
            node.endRecording()
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: return null
            image.use {
                val hb = it.hardwareBuffer ?: return null
                hb.use { buffer ->
                    val hw = Bitmap.wrapHardwareBuffer(buffer, null) ?: return null
                    val out = hw.copy(Bitmap.Config.ARGB_8888, true)
                    hw.recycle()
                    return out
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }

    /**
     * Stack Blur v1.0 by Mario Klingemann (public-domain algorithm), in-place on [bitmap].
     */
    fun stackBlur(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh); val a = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int; var asum: Int
        var x: Int; var y: Int; var i: Int; var p: Int; var yp: Int; var yi: Int
        val vmin = IntArray(max(w, h))
        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum) { it / divsum }
        yi = 0
        var yw = 0
        val stack = Array(div) { IntArray(4) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int; var aoutsum: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int; var ainsum: Int
        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0; ainsum = 0
            routsum = 0; goutsum = 0; boutsum = 0; aoutsum = 0
            rsum = 0; gsum = 0; bsum = 0; asum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + minOf(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16; sir[1] = p and 0x00ff00 shr 8; sir[2] = p and 0x0000ff; sir[3] = p ushr 24
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs; asum += sir[3] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3] }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]; a[yi] = dv[asum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3]
                if (y == 0) vmin[x] = minOf(x + radius + 1, wm)
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16; sir[1] = p and 0x00ff00 shr 8; sir[2] = p and 0x0000ff; sir[3] = p ushr 24
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]
                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0; ainsum = 0
            routsum = 0; goutsum = 0; boutsum = 0; aoutsum = 0
            rsum = 0; gsum = 0; bsum = 0; asum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]; sir[3] = a[yi]
                rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs; asum += a[yi] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3] }
                if (i < hm) yp += w
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (dv[asum] shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3]
                if (x == 0) vmin[y] = minOf(y + r1, hm) * w
                p = x + vmin[y]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]; sir[3] = a[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]
                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}

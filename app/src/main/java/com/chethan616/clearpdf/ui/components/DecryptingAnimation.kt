package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay

/**
 * The "decrypting" animation: a **blue liquid-glass** document whose encrypted lines resolve as a
 * scan beam sweeps down it, in the flat self-drawing style of the onboarding page-5 demos
 * (`DemoAnnotate`'s signature stroking itself) — NOT the page-6 glass-disc medallion.
 *
 * The panel is a real [viewerGlass] surface tinted blue, so it refracts the wallpaper [backdrop] like
 * the rest of the app's chrome; on top of it each line is faint until the beam reaches it, then it
 * fills in solid **left-to-right** (drawn, not faded). Runs the demos' rise → hold → reset → gap loop
 * so it reads as a repeating demonstration for as long as the decrypt is in flight.
 */
@Composable
fun DecryptingAnimation(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    // The blue glass tint and the two ink strengths the lines resolve between.
    val blueGlass = Color(0xFF1E6BFF).copy(alpha = 0.34f)
    val encrypted = Color.White.copy(alpha = 0.22f)
    val decrypted = Color(0xFFEAF3FF)
    val beam = Color(0xFF9AD0FF)

    // Same cadence as rememberDemoLoop: lead-in, decrypt (rise) → hold on the finished page → reset
    // (re-lock) → short gap → repeat. Reset-and-replay is exactly how DemoAnnotate loops.
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(220)
        while (true) {
            target = 1f
            delay(1500)          // scan down + dwell on the decrypted page
            target = 0f
            delay(520)           // re-lock gap
        }
    }
    val p by animateFloatAsState(target, tween(1050, easing = FastOutSlowInEasing), label = "decryptScan")

    Box(
        modifier
            .size(width = 176.dp, height = 132.dp)
            .viewerGlass(backdrop, blueGlass, shape = { RoundedRectangle(20f.dp) })
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 22.dp)) {
            val w = size.width
            val h = size.height
            val barH = 6.dp.toPx()
            val radius = CornerRadius(barH / 2f, barH / 2f)

            // The document's lines: (vertical fraction, width fraction), varied like real text.
            val lines = listOf(
                0.02f to 0.55f,   // a short "heading"
                0.22f to 1.00f,
                0.40f to 0.82f,
                0.58f to 0.95f,
                0.76f to 0.66f,
                0.94f to 0.88f
            )

            // The beam sweeps from just above the first line to just past the last, so every line is
            // fully resolved before the dwell.
            val scanY = lerp(-0.06f * h, 1.10f * h, p)
            val band = 0.18f * h   // how far ahead of the beam a line begins resolving

            lines.forEach { (fy, fw) ->
                val y = fy * h
                val full = w * fw
                // Encrypted (base) bar — always present, faint.
                drawRoundRect(
                    color = encrypted,
                    topLeft = Offset(0f, y),
                    size = Size(full, barH),
                    cornerRadius = radius
                )
                // Decrypted fill: grows to full width as the beam passes — a left-to-right "draw".
                val reveal = ((scanY - y + band) / band).coerceIn(0f, 1f)
                if (reveal > 0f) {
                    drawRoundRect(
                        color = decrypted,
                        topLeft = Offset(0f, y),
                        size = Size(full * reveal, barH),
                        cornerRadius = radius
                    )
                }
            }

            // The scan beam — a bright rule with a soft trail above it (where it has just decrypted).
            // Hidden during the dwell/reset, when it's off the page.
            if (scanY in 0f..h) {
                val trail = 0.16f * h
                val top = (scanY - trail).coerceAtLeast(0f)
                drawRect(
                    color = beam.copy(alpha = 0.16f),
                    topLeft = Offset(0f, top),
                    size = Size(w, scanY - top)
                )
                drawLine(
                    color = beam,
                    start = Offset(0f, scanY),
                    end = Offset(w, scanY),
                    strokeWidth = 2f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

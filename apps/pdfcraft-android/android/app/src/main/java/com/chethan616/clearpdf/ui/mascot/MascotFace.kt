package com.chethan616.clearpdf.ui.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The bots' expressive face, in a 100-unit body box:
 *
 *   eyes     glossy ovals with two catchlights, shut by the blink
 *   lids     a top and a bottom lid that cut the eye: smiling crescents,
 *            sleepy, focused (inner ends down), worried (inner ends up)
 *   brows    short soft arcs that lift and tilt
 *   mouths   smile, open grin, flat, frown, "o", tongue-out, cross-faded
 *   extras   blush, a thinking swirl in the eyes, a tear, puppy-eye shine
 *            with a double eyebrow-raise (needs you), a sleep bubble that
 *            breathes (asleep)
 *
 * A mood is a set of numbers ([Face]); Mascot eases between them, so a
 * change of mood reads as the face moving, not a swap. Needing you and
 * sleeping are said by the face itself — no "!" badge, no z's.
 */
internal data class Face(
  val lidTop: Float = 0f,
  val lidBot: Float = 0f,
  val lidTopR: Float = 0f,
  /** > 0 lifts the lids' inner ends (worried), < 0 drops them (focused) */
  val slant: Float = 0f,
  val scale: Float = 1f,
  /** ^ ^ arcs, and closed ‿ arcs */
  val happy: Float = 0f,
  val closed: Float = 0f,
  val swirl: Float = 0f,
  val brow: Float = 0f,
  val browLift: Float = 0f,
  val browLiftL: Float = 0f,
  /** > 0 worried (inner ends up), < 0 focused */
  val browTilt: Float = 0f,
  val smile: Float = 1f,
  val grin: Float = 0f,
  val flat: Float = 0f,
  val frown: Float = 0f,
  val oh: Float = 0f,
  val tongue: Float = 0f,
  val mouthW: Float = 1f,
  val mouthX: Float = 0f,
  val mouthTilt: Float = 0f,
  val blush: Float = 0.4f,
  val tear: Float = 0f,
  /** big twinkling catchlights (puppy eyes) */
  val shine: Float = 0f,
  /** a double eyebrow-raise every 2.6 s: "hey, over here" */
  val browBounce: Float = 0f,
  /** a sleep bubble at the mouth, breathing in and out */
  val bubble: Float = 0f,
)

/** The face each mood wears. */
internal fun faceFor(mood: MascotMood): Face = when (mood) {
  // At rest: smiling eyes, a soft smile, a little blush.
  MascotMood.Idle -> Face(lidBot = 0.2f, blush = 0.6f)
  // Reasoning: one eye squinting, a swirl in the eyes, one brow up, a sideways "hmm".
  MascotMood.Thinking -> Face(
    lidTop = 0.04f, lidTopR = 0.32f, swirl = 1f, brow = 1f, browLiftL = 3.5f, browTilt = -0.12f,
    smile = 0f, flat = 1f, mouthW = 0.8f, mouthX = 2.5f, mouthTilt = -0.18f,
  )
  // Working: lids down and angled in, focused brows, tongue out.
  MascotMood.Working -> Face(lidTop = 0.3f, slant = -0.22f, brow = 0.95f, browTilt = -0.24f, browLift = -1f, smile = 0f, tongue = 1f, blush = 0.25f)
  // Needs you: hopeful puppy eyes, brows gently pleading, a double eyebrow-raise now and then.
  MascotMood.NeedsYou -> Face(scale = 1.12f, shine = 1f, brow = 0.9f, browLift = 2.4f, browTilt = 0.2f, browBounce = 1f, lidBot = 0.08f, mouthW = 0.72f, blush = 0.75f)
  // Done: ^ ^ eyes, an open grin, full blush.
  MascotMood.Happy -> Face(happy = 1f, smile = 0f, grin = 1f, blush = 0.9f)
  // Failed: heavy worried lids, sad brows, a frown, a tear.
  MascotMood.Sad -> Face(lidTop = 0.32f, slant = 0.36f, brow = 1f, browTilt = 0.38f, smile = 0f, frown = 1f, blush = 0.15f, tear = 1f)
  // Asleep: eyes shut, a small "o", a sleep bubble.
  MascotMood.Sleeping -> Face(closed = 1f, smile = 0f, oh = 0.75f, mouthW = 0.7f, blush = 0.3f, bubble = 1f)
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun lerpFace(a: Face, b: Face, t: Float): Face = Face(
  lidTop = mix(a.lidTop, b.lidTop, t), lidBot = mix(a.lidBot, b.lidBot, t), lidTopR = mix(a.lidTopR, b.lidTopR, t),
  slant = mix(a.slant, b.slant, t), scale = mix(a.scale, b.scale, t),
  happy = mix(a.happy, b.happy, t), closed = mix(a.closed, b.closed, t), swirl = mix(a.swirl, b.swirl, t),
  brow = mix(a.brow, b.brow, t), browLift = mix(a.browLift, b.browLift, t), browLiftL = mix(a.browLiftL, b.browLiftL, t),
  browTilt = mix(a.browTilt, b.browTilt, t),
  smile = mix(a.smile, b.smile, t), grin = mix(a.grin, b.grin, t), flat = mix(a.flat, b.flat, t),
  frown = mix(a.frown, b.frown, t), oh = mix(a.oh, b.oh, t), tongue = mix(a.tongue, b.tongue, t),
  mouthW = mix(a.mouthW, b.mouthW, t), mouthX = mix(a.mouthX, b.mouthX, t), mouthTilt = mix(a.mouthTilt, b.mouthTilt, t),
  blush = mix(a.blush, b.blush, t), tear = mix(a.tear, b.tear, t), shine = mix(a.shine, b.shine, t),
  browBounce = mix(a.browBounce, b.browBounce, t), bubble = mix(a.bubble, b.bubble, t),
)

/**
 * The face's ink: dark eyes with white catchlights on every bot (the look of
 * the ghost, star, droid and cloud), light only on a near-black custom colour
 * where dark eyes would vanish.
 */
internal fun inkFor(body: Color): Color = if (body.luminance() < 0.06f) Color(0xFFF8F8FA) else Color(0xFF15161A)

private val BLUSH = Color(0xFFFF7096)
private val TONGUE = Color(0xFFFF7D93)
private val TEAR = Color(0xFF7CCBFF)
private const val DEG = 180f / PI.toFloat()

private fun c01(v: Float) = v.coerceIn(0f, 1f)

/**
 * The face, in the 100-unit body box.
 *
 * [breath] −1…1 (the breathing cycle, for the bubble), [nudge] 0…1 over
 * 2.6 s (the eyebrow-raise, the swirl, the twinkle). [large] adds the mouth,
 * brows and lid lines — below ~36 dp they'd be smudges.
 */
internal fun DrawScope.drawFace(
  face: Face,
  shape: BotShape,
  ink: Color,
  lookX: Float,
  lookY: Float,
  eyeOpen: Float,
  large: Boolean,
  breath: Float = 0f,
  nudge: Float = 0.5f,
) {
  // The rig is drawn in face units ~1.7x the body's; S maps them.
  val s = 0.58f * shape.faceScale
  val cx = shape.faceX + lookX
  val cy = shape.faceY - 3f + lookY
  val darkInk = ink.luminance() < 0.5f
  val hi = if (darkInk) Color.White else Color(0xFF1E1A33).copy(alpha = 0.55f)
  val mouthFill = if (darkInk) Color(0xFF4A1A33) else ink

  // "Hey, over here": two quick raises at the start of each 2.6 s cycle.
  fun bump(p: Float) = if (p >= 0f && p < 0.077f) sin(PI.toFloat() * p / 0.077f) else 0f
  val raise = face.browBounce * (bump(nudge) + bump(nudge - 0.123f))

  // Blush, under everything.
  if (face.blush > 0.02f) {
    for (side in floatArrayOf(-1f, 1f)) {
      val c = Offset(cx + side * 19f * s, cy + 9f * s)
      drawOval(BLUSH.copy(alpha = 0.85f * c01(face.blush)), topLeft = Offset(c.x - 4.4f * s, c.y - 2.5f * s), size = Size(8.8f * s, 5f * s))
    }
  }

  // Mouth.
  val mouth = Offset(cx + face.mouthX * s, cy + 13.5f * s)
  if (large) {
    translate(mouth.x, mouth.y) {
      rotate(face.mouthTilt * DEG, pivot = Offset.Zero) {
        scale(s, s, pivot = Offset.Zero) { drawMouth(face, ink, mouthFill) }
      }
    }
  }

  // Sleep bubble: swells on the in-breath, shrinks on the out-breath.
  if (face.bubble > 0.02f) {
    val r = (2.4f + 5.6f * (0.5f + 0.5f * breath)) * s * c01(face.bubble)
    val at = if (large) Offset(mouth.x + 3.4f * s + r * 0.75f, mouth.y + 1.1f * s - r * 0.2f)
    else Offset(cx + 4.5f * s + r * 0.75f, cy + 10f * s)
    val a = c01(face.bubble)
    drawCircle(
      Brush.radialGradient(
        listOf(Color(0x40E1F5FF), Color(0x80A0D2FF)),
        center = at + Offset(-r * 0.3f, -r * 0.3f),
        radius = r * 1.2f,
      ),
      radius = r, center = at, alpha = a,
    )
    drawCircle(Color.White.copy(alpha = 0.85f * a), radius = r, center = at, style = Stroke(width = 0.9f * s))
    drawOval(Color.White.copy(alpha = 0.9f * a), topLeft = at + Offset(-r * 0.62f, -r * 0.55f), size = Size(r * 0.5f, r * 0.32f))
  }

  // Eyes.
  val arcUp = face.happy * (1f - face.closed)
  val openA = c01(1f - max(arcUp, face.closed))
  for (side in floatArrayOf(-1f, 1f)) {
    val left = side < 0f
    val k = face.scale * (1f + 0.07f * raise)
    val rx = 6.3f * s * k
    val ry = 8.6f * s * k
    val c = Offset(cx + side * 12.5f * s, cy)
    val lidTop = c01(face.lidTop + if (left) 0f else face.lidTopR)
    val lidBot = c01(face.lidBot)
    val lidT = lidTop + (1f - lidTop - lidBot) * (1f - c01(eyeOpen))
    if (openA > 0.02f) drawEye(c, rx, ry, lidT, lidBot, face.slant, -side, ink, hi, face.swirl, face.shine, large, nudge, s, openA)
    if (arcUp > 0.02f) drawArcEye(c, rx, ry, up = true, ink, s, arcUp)
    if (face.closed > 0.02f) drawArcEye(c, rx, ry, up = false, ink, s, face.closed)

    // Brow.
    if (large && face.brow > 0.02f) {
      val lift = face.browLift + (if (left) face.browLiftL else 0f) + 2.8f * raise
      val b = Offset(c.x, c.y - ry - (3.6f + lift) * s)
      rotate((if (left) -face.browTilt else face.browTilt) * DEG, pivot = b) {
        val p = Path().apply {
          moveTo(b.x - 4.6f * s, b.y + 1.1f * s)
          quadraticTo(b.x, b.y - 1.8f * s, b.x + 4.6f * s, b.y + 1.1f * s)
        }
        drawPath(p, ink.copy(alpha = 0.62f * c01(face.brow)), style = Stroke(width = 2.5f * s, cap = StrokeCap.Round))
      }
    }
  }

  // A tear by the right eye, sliding down.
  if (face.tear > 0.02f) {
    val fall = (nudge * 2.6f / 1.6f % 1f) * 3f
    val t = Offset(cx + 19f * s, cy + (6f + fall) * s)
    val drop = Path().apply {
      moveTo(t.x, t.y - 3.2f * s)
      quadraticTo(t.x + 2.4f * s, t.y + 0.6f * s, t.x, t.y + 2.2f * s)
      quadraticTo(t.x - 2.4f * s, t.y + 0.6f * s, t.x, t.y - 3.2f * s)
    }
    drawPath(drop, TEAR.copy(alpha = c01(face.tear) * (1f - fall / 4f)))
  }
}

/** One eye: lids cut a glossy oval; collapses into a line when shut. */
private fun DrawScope.drawEye(
  c: Offset, rx: Float, ry: Float, lidT: Float, lidB: Float, slant: Float, inner: Float,
  ink: Color, hi: Color, swirl: Float, shine: Float, detail: Boolean, nudge: Float, s: Float, alpha: Float,
) {
  val yT = -ry + 2f * ry * lidT
  val yB = ry - 2f * ry * lidB
  fun top(x: Float): Float {
    val q = x / rx
    return yT - slant * ry * 0.55f * q * inner - (if (lidT > 0.02f) 0.1f * ry * (1f - q * q) else 0f)
  }
  fun bot(x: Float): Float {
    val q = x / rx
    return yB - min(1f, lidB * 4f) * 0.5f * ry * (1f - min(1f, q * q))
  }
  if (yB - yT < ry * 0.14f) {
    val m = (yT + yB) / 2f
    val p = Path().apply {
      moveTo(c.x - rx, c.y + m)
      quadraticTo(c.x, c.y + m + ry * 0.3f, c.x + rx, c.y + m)
    }
    drawPath(p, ink.copy(alpha = alpha), style = Stroke(width = 3.4f * s, cap = StrokeCap.Round))
    return
  }
  val eye = Path().apply { addOval(Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)) }
  val w = rx * 1.5f
  val open = Path().apply {
    for (i in 0..10) {
      val x = -w + 2f * w * i / 10f
      if (i == 0) moveTo(c.x + x, c.y + top(x)) else lineTo(c.x + x, c.y + top(x))
    }
    for (i in 10 downTo 0) {
      val x = -w + 2f * w * i / 10f
      lineTo(c.x + x, c.y + bot(x))
    }
    close()
  }
  clipPath(eye) {
    clipPath(open) {
      drawPath(eye, ink.copy(alpha = alpha))
      drawPath(
        eye,
        Brush.radialGradient(
          listOf(if (darkGloss(hi)) Color(0x735A6390) else Color(0x1F000000), Color.Transparent),
          center = c + Offset(-rx * 0.35f, -ry * 0.45f),
          radius = ry * 1.1f,
        ),
        alpha = alpha,
      )
      drawCircle(hi.copy(alpha = hi.alpha * alpha), radius = max(1.6f * s, rx * 0.36f) * (1f + 0.45f * shine), center = c + Offset(-rx * 0.3f, -ry * 0.36f))
      if (detail || shine > 0.3f) {
        drawCircle(hi.copy(alpha = hi.alpha * alpha * 0.85f), radius = rx * 0.17f * (1f + 0.7f * shine), center = c + Offset(rx * 0.32f, ry * 0.36f))
      }
      // Puppy-eye twinkle: a small four-point star that pulses.
      if (shine > 0.05f) {
        val k = rx * 0.24f * shine * (0.75f + 0.25f * sin(nudge * 6f * PI.toFloat()))
        val t = c + Offset(rx * 0.16f, -ry * 0.6f)
        val star = Path().apply {
          moveTo(t.x, t.y - k)
          quadraticTo(t.x, t.y, t.x + k, t.y)
          quadraticTo(t.x, t.y, t.x, t.y + k)
          quadraticTo(t.x, t.y, t.x - k, t.y)
          quadraticTo(t.x, t.y, t.x, t.y - k)
          close()
        }
        drawPath(star, hi.copy(alpha = hi.alpha * alpha))
      }
      if (swirl > 0.02f && detail) {
        rotate(-nudge * 720f, pivot = c) {
          val r = rx * 0.5f
          drawArc(
            hi.copy(alpha = hi.alpha * alpha * swirl), startAngle = 0f, sweepAngle = 261f, useCenter = false,
            topLeft = Offset(c.x - r, c.y + ry * 0.12f - r), size = Size(2 * r, 2 * r),
            style = Stroke(width = 1.4f * s, cap = StrokeCap.Round),
          )
        }
      }
    }
  }
  // Lid lines where a lid crosses the eye.
  if (detail && (lidT > 0.05f || lidB > 0.05f)) {
    val rim = Path().apply { addOval(Rect(c.x - rx * 1.12f, c.y - ry * 1.06f, c.x + rx * 1.12f, c.y + ry * 1.06f)) }
    clipPath(rim) {
      fun edge(fn: (Float) -> Float, width: Float, a: Float) {
        val p = Path().apply {
          for (i in 0..10) {
            val x = -rx * 1.1f + 2.2f * rx * i / 10f
            if (i == 0) moveTo(c.x + x, c.y + fn(x)) else lineTo(c.x + x, c.y + fn(x))
          }
        }
        drawPath(p, ink.copy(alpha = alpha * a), style = Stroke(width = width * s, cap = StrokeCap.Round))
      }
      if (lidT > 0.05f) edge(::top, 2f, 1f)
      if (lidB > 0.05f) edge(::bot, 1.4f, 0.55f)
    }
  }
}

private fun darkGloss(hi: Color) = hi == Color.White

/** Arc eyes: ^ (happy) or ‿ (asleep). */
private fun DrawScope.drawArcEye(c: Offset, rx: Float, ry: Float, up: Boolean, ink: Color, s: Float, alpha: Float) {
  val p = Path().apply {
    if (up) {
      moveTo(c.x - rx * 0.95f, c.y + ry * 0.25f)
      quadraticTo(c.x, c.y - ry * 1.05f, c.x + rx * 0.95f, c.y + ry * 0.25f)
    } else {
      moveTo(c.x - rx, c.y - ry * 0.05f)
      quadraticTo(c.x, c.y + ry * 0.6f, c.x + rx, c.y - ry * 0.05f)
    }
  }
  drawPath(p, ink.copy(alpha = alpha), style = Stroke(width = 4.2f * s, cap = StrokeCap.Round))
}

/** The mouths, in face units round the mouth's centre, cross-faded by weight. */
private fun DrawScope.drawMouth(f: Face, ink: Color, mouthFill: Color) {
  val w = f.mouthW
  val line = Stroke(width = 3.2f, cap = StrokeCap.Round)
  if (f.smile > 0.02f) {
    val p = Path().apply { moveTo(-5f * w, -1f); quadraticTo(0f, 4.6f, 5f * w, -1f) }
    drawPath(p, ink.copy(alpha = c01(f.smile)), style = line)
  }
  if (f.grin > 0.02f) {
    val a = c01(f.grin)
    val p = Path().apply {
      moveTo(-6.2f * w, -1.8f)
      lineTo(6.2f * w, -1.8f)
      quadraticTo(0f, 10.5f, -6.2f * w, -1.8f)
      close()
    }
    drawPath(p, mouthFill.copy(alpha = a))
    clipPath(p) {
      drawOval(TONGUE.copy(alpha = a), topLeft = Offset(-3.8f * w, 2.8f), size = Size(7.6f * w, 5.2f))
    }
    drawPath(p, ink.copy(alpha = a), style = Stroke(width = 1.6f))
  }
  if (f.flat > 0.02f) {
    drawLine(ink.copy(alpha = c01(f.flat)), Offset(-3.8f * w, 0.4f), Offset(3.8f * w, 0.4f), strokeWidth = 3.2f, cap = StrokeCap.Round)
  }
  if (f.frown > 0.02f) {
    val p = Path().apply { moveTo(-4.6f * w, 2.4f); quadraticTo(0f, -2.8f, 4.6f * w, 2.4f) }
    drawPath(p, ink.copy(alpha = c01(f.frown)), style = line)
  }
  if (f.oh > 0.02f) {
    drawOval(mouthFill.copy(alpha = c01(f.oh)), topLeft = Offset(-2.6f * w, -2f), size = Size(5.2f * w, 6.4f))
  }
  if (f.tongue > 0.02f) {
    val a = c01(f.tongue)
    drawOval(TONGUE.copy(alpha = a), topLeft = Offset(0.5f, -0.1f), size = Size(3.8f, 4.6f))
    drawLine(ink.copy(alpha = a), Offset(-3.6f, 0.3f), Offset(3f, 0.3f), strokeWidth = 3f, cap = StrokeCap.Round)
  }
}


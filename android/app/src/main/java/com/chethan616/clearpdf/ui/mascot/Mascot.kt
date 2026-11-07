package com.chethan616.clearpdf.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * What a mascot is feeling:
 *
 *  - Idle       awake, blinking, glancing round, the odd happy hop
 *  - Thinking   one eye squinting, a swirl in the eyes, a sideways "hmm", thought dots
 *  - Working    focused lids, tongue out, hopping (a long job)
 *  - NeedsYou   hopeful puppy eyes, a double eyebrow-raise, eager hops
 *  - Happy      ^ ^ eyes, a big open smile, a jump, sparkles (done!)
 *  - Sad        worried lids and brows, a frown, a tear, a little grey (something failed)
 *  - Sleeping   eyes shut, slow breaths, a sleep bubble (nothing here yet)
 *
 * The face is MascotFace.kt.
 */
enum class MascotMood { Idle, Thinking, Working, NeedsYou, Happy, Sad, Sleeping }

/** What a mascot does to the phone when it hops (the app plays a haptic). */
val LocalMascotHop = staticCompositionLocalOf<() -> Unit> { {} }

private val SPARKLE = Color(0xFFFFD24A)
private val SPARKLE_COOL = Color(0xFF7FD8FF)
private val SWEAT = Color(0xFF7CC4F5)

/**
 * A living bot: breathes, blinks, glances around, tilts its head toward where
 * it's looking, and wears its mood — each with its own face (lids, brows,
 * mouth, blush; BotFace.kt), its own way of moving, and for some a little
 * accent (thought dots, sparkles, a sweat drop). It jumps when tapped. Drawn from the bot-avatars outlines with a soft
 * lit-plastic shading — no bitmaps, crisp at any size.
 */
@Composable
fun Mascot(
  type: String,
  mood: MascotMood,
  size: Dp,
  modifier: Modifier = Modifier,
  interactive: Boolean = true,
  label: String? = null,
  color: Color? = null,
  /**
   * A still portrait: the mood's face and accent, drawn once — for lists,
   * where a dozen breathing, blinking bots would each redraw every frame.
   * A tap still makes it jump.
   */
  still: Boolean = false,
  /** Changing this makes the bot hop (a reaction, e.g. to a key press); 0 does nothing. */
  hopSignal: Int = 0,
  /** Off screen (a pager's far page): stops redrawing with the breath, so it costs no frames. */
  paused: Boolean = false,
) {
  val clock = remember { floatArrayOf(0f, 0.4f, 0.5f) }
  val shape = BOT_SHAPES[type] ?: BOT_SHAPES.getValue("flower")
  val body = remember(type) { PathParser().parsePathString(shape.path).toPath() }
  val parts = remember(type) { shape.parts?.let { PathParser().parsePathString(it).toPath() } }
  // A sad bot loses a little colour.
  val grey by animateFloatAsState(if (mood == MascotMood.Sad) 0.32f else 0f, tween(900), label = "grey")
  val base = color ?: Color(shape.color)
  val bodyColor = lerp(base, Color(0xFF8C9199), grey)
  val ink = inkFor(base)
  val hopFeedback = LocalMascotHop.current
  val haptics = com.chethan616.clearpdf.ui.glass.LocalHaptics.current
  val scope = rememberCoroutineScope()
  val seed = remember(type) { Random(type.hashCode()) }

  val hop = remember { Animatable(0f) }        // units above rest (body is 100 tall)
  val squash = remember { Animatable(1f) }     // 1 = rest; <1 squashed, >1 stretched
  val eyeOpen = remember { Animatable(1f) }
  val lookX = remember { Animatable(0f) }
  val lookY = remember { Animatable(0f) }
  val spin = remember { Animatable(0f) }       // a happy twirl, degrees
  val accent = remember { Animatable(0f) }     // the accent popping in, 0…1

  val slow = mood == MascotMood.Sleeping || mood == MascotMood.Sad
  // Both phases are read only while drawing (a running bot redraws; it never
  // recomposes), and a still one has none at all.
  val breathPhase = if (still) null else rememberInfiniteTransition(label = "breath").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(if (slow) 4200 else 3000, easing = LinearEasing), RepeatMode.Restart),
    label = "breathPhase",
  )
  val beatPhase = if (still) null else rememberInfiniteTransition(label = "beat").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
    label = "beatPhase",
  )
  // The face's own clock: the eyebrow-raise, the thinking swirl, the twinkle.
  val nudgePhase = if (still) null else rememberInfiniteTransition(label = "nudge").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
    label = "nudgePhase",
  )

  // The face eases from mood to mood: the lids, brows and mouth move, they don't swap.
  val faceFrom = remember { mutableStateOf(faceFor(mood)) }
  val faceTo = remember { mutableStateOf(faceFor(mood)) }
  val faceMix = remember { Animatable(1f) }
  LaunchedEffect(mood) {
    val target = faceFor(mood)
    if (target == faceTo.value) return@LaunchedEffect
    faceFrom.value = lerpFace(faceFrom.value, faceTo.value, faceMix.value)
    faceTo.value = target
    if (still) faceMix.snapTo(1f) else {
      faceMix.snapTo(0f)
      faceMix.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
  }

  suspend fun jump(height: Float) {
    squash.animateTo(0.86f, tween(90, easing = FastOutSlowInEasing))
    scope.launch { squash.animateTo(1.1f, tween(160)) }
    hop.animateTo(height, tween(220, easing = FastOutSlowInEasing))
    scope.launch { squash.animateTo(1f, tween(180)) }
    hop.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
    squash.animateTo(0.9f, tween(70))
    squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
  }

  LaunchedEffect(hopSignal) {
    if (hopSignal != 0) jump(14f)
  }

  if (still) {
    // The mood's resting pose, set once.
    LaunchedEffect(mood) {
      accent.snapTo(1f)
      eyeOpen.snapTo(if (mood == MascotMood.Sleeping) 0f else 1f)
      val (x, y) = when (mood) {
        MascotMood.Sleeping -> 0f to 2.5f
        MascotMood.Sad -> -1f to 2.2f
        MascotMood.NeedsYou, MascotMood.Happy -> 0f to -0.5f
        MascotMood.Thinking -> 2.6f to -2.4f
        else -> 0f to 0f
      }
      lookX.snapTo(x)
      lookY.snapTo(y)
    }
  } else {
  // The accent pops in on every change of mood.
  LaunchedEffect(mood) {
    accent.snapTo(0f)
    accent.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
  }

  // Blinking — never while asleep (lids are already shut). Thinking's squint
  // is the face's own lid, not a half-open eye.
  LaunchedEffect(mood) {
    when (mood) {
      MascotMood.Sleeping -> { eyeOpen.animateTo(0f, tween(400)); return@LaunchedEffect }
      else -> eyeOpen.animateTo(1f, tween(250))
    }
    val open = eyeOpen.value
    while (true) {
      delay(2200L + seed.nextLong(3200))
      eyeOpen.animateTo(0.08f, tween(70))
      eyeOpen.animateTo(open, tween(120))
      if (seed.nextInt(5) == 0) { // the odd double blink
        delay(120)
        eyeOpen.animateTo(0.08f, tween(60))
        eyeOpen.animateTo(open, tween(110))
      }
    }
  }

  // Where it looks.
  LaunchedEffect(mood) {
    val soft = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
    when (mood) {
      MascotMood.Sleeping -> { launch { lookX.animateTo(0f) }; lookY.animateTo(2.5f) }
      MascotMood.Sad -> { launch { lookX.animateTo(-1f, soft) }; lookY.animateTo(2.2f, soft) }
      // Straight at you.
      MascotMood.NeedsYou, MascotMood.Happy -> { launch { lookX.animateTo(0f, soft) }; lookY.animateTo(-0.5f, soft) }
      // Up and to the side, drifting a little.
      MascotMood.Thinking -> while (true) {
        launch { lookX.animateTo(2.4f + seed.nextFloat() * 0.8f, soft) }
        lookY.animateTo(-2.6f + seed.nextFloat() * 0.6f, soft)
        delay(1800L + seed.nextLong(1400))
      }
      else -> while (true) {
        delay(1400L + seed.nextLong(2600))
        val x = (seed.nextFloat() * 2 - 1) * 3.2f
        val y = (seed.nextFloat() * 2 - 1) * 1.8f
        launch { lookX.animateTo(x, soft) }
        lookY.animateTo(y, soft)
      }
    }
  }

  // How it moves.
  LaunchedEffect(mood) {
    when (mood) {
      MascotMood.Working -> while (true) { jump(12f); delay(260) }
      MascotMood.NeedsYou -> while (true) { jump(7f); jump(5f); delay(1500) }
      MascotMood.Happy -> {
        launch { spin.snapTo(0f); spin.animateTo(360f, tween(620, easing = FastOutSlowInEasing)); spin.snapTo(0f) }
        jump(22f)
        while (true) { delay(2600); jump(10f) }
      }
      MascotMood.Idle -> while (true) { delay(7000L + seed.nextLong(6000)); jump(9f) }
      MascotMood.Thinking, MascotMood.Sad, MascotMood.Sleeping -> Unit
    }
  }
  }

  val large = size >= 36.dp
  val showAccent = size >= 24.dp
  val tapModifier = if (interactive) {
    Modifier.pointerInput(type) {
      detectTapGestures {
        hopFeedback()
        haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Rise)
        scope.launch {
          jump(18f)
          haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Thud)
        }
      }
    }
  } else Modifier

  Canvas(
    modifier
      .size(size)
      // its own layer: the breathing and blinking redraw only this, not the card or page around it
      .graphicsLayer {}
      .then(tapModifier)
      .semantics { contentDescription = label ?: "Artefact mascot, ${moodLabel(mood)}" },
  ) {
    val unit = this.size.minDimension / 100f
    // paused (off screen) it reads no clock, so it is never redrawn, and keeps its last pose
    if (!paused) {
      clock[0] = breathPhase?.value ?: 0f
      clock[1] = beatPhase?.value ?: 0.4f
      clock[2] = nudgePhase?.value ?: 0.5f
    }
    val breath = clock[0]
    val beat = clock[1]
    val phase = breath * 2f * PI.toFloat()
    val breathe = if (slow) 1f + 0.025f * sin(phase) else 1f + 0.012f * sin(phase)
    val bob = if (mood == MascotMood.Idle) 1.2f * sin(phase) else 0f
    // Thinking sways gently side to side.
    val sway = if (mood == MascotMood.Thinking) 3.5f * sin(phase) else 0f
    val lift = hop.value + bob

    // Contact shadow: shrinks and fades as the bot rises.
    val shadowW = 48f * unit * (1f - (hop.value / 40f).coerceIn(0f, 0.5f))
    drawOval(
      color = Color.Black.copy(alpha = 0.16f * (1f - (hop.value / 30f).coerceIn(0f, 0.6f))),
      topLeft = Offset(50f * unit - shadowW / 2, 90f * unit),
      size = Size(shadowW, 6f * unit),
    )

    translate(top = -lift * unit) {
      val sx = (2f - squash.value) * breathe
      val sy = squash.value * breathe
      scale(scaleX = sx, scaleY = sy, pivot = Offset(50f * unit, 88f * unit)) {
        rotate(degrees = lookX.value * 1.4f + sway + spin.value, pivot = Offset(50f * unit, 60f * unit)) {
          scale(unit, unit, pivot = Offset.Zero) {
            parts?.let { drawPath(it, lerp(bodyColor, Color.Black, 0.18f)) }
            drawPath(body, bodyColor)
            // Lit plastic: a soft key light from the upper left, a darker belly.
            drawPath(
              body,
              Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)),
                center = Offset(34f + lookX.value, 26f),
                radius = 52f,
              ),
            )
            drawPath(
              body,
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f)),
                startY = 48f,
                endY = 96f,
              ),
            )
            drawPath(body, Color.White.copy(alpha = 0.18f), style = Stroke(width = 0.9f))
            drawFace(
              face = lerpFace(faceFrom.value, faceTo.value, faceMix.value),
              shape = shape, ink = ink, lookX = lookX.value, lookY = lookY.value,
              eyeOpen = eyeOpen.value, large = large,
              breath = sin(phase), nudge = clock[2],
            )
          }
        }
      }
      if (showAccent) {
        scale(unit, unit, pivot = Offset.Zero) {
          drawAccent(mood, ink, phase, beat, accent.value)
        }
      }
    }
  }
}

private fun moodLabel(mood: MascotMood): String = when (mood) {
  MascotMood.Idle -> "ready"
  MascotMood.Thinking -> "thinking"
  MascotMood.Working -> "working"
  MascotMood.NeedsYou -> "needs you"
  MascotMood.Happy -> "done"
  MascotMood.Sad -> "hit a problem"
  MascotMood.Sleeping -> "asleep"
}

/** The accent beside the head, in the 100-unit box: pops in with the mood. */
internal fun DrawScope.drawAccent(mood: MascotMood, ink: Color, phase: Float, beat: Float, pop: Float) {
  if (pop <= 0.01f) return
  when (mood) {
    MascotMood.Thinking -> scale(pop, pivot = Offset(80f, 16f)) {
      // A little thought cloud with three dots taking turns.
      drawCircle(Color.White.copy(alpha = 0.92f), radius = 3f, center = Offset(66f, 28f))
      drawRoundRect(Color.White.copy(alpha = 0.95f), topLeft = Offset(69f, 6f), size = Size(24f, 13f), cornerRadius = CornerRadius(7f, 7f))
      for (i in 0 until 3) {
        val t = ((beat * 3f - i) % 3f + 3f) % 3f
        val up = if (t < 1f) sin(t * PI.toFloat()) * 2.2f else 0f
        drawCircle(Color(0xFF55595F).copy(alpha = 0.55f + up / 5f), radius = 2f, center = Offset(75f + i * 6f, 12.5f - up))
      }
    }
    MascotMood.Happy -> {
      val stars = listOf(Triple(Offset(84f, 12f), 7f, SPARKLE), Triple(Offset(14f, 22f), 5f, SPARKLE), Triple(Offset(72f, 2f), 4f, SPARKLE_COOL))
      stars.forEachIndexed { i, (c, r, tone) ->
        val t = (sin((beat + i * 0.33f) * 2f * PI.toFloat()) + 1f) / 2f
        val s = pop * (0.45f + 0.7f * t)
        rotate(45f * t, pivot = c) { drawSparkle(c, r * s, tone.copy(alpha = 0.55f + 0.45f * t)) }
      }
    }
    MascotMood.Sad -> {
      // A sweat drop sliding down the temple.
      val t = (beat * 0.6f) % 1f
      val y = 18f + 12f * t
      val a = (if (t < 0.15f) t / 0.15f else if (t > 0.8f) (1f - t) / 0.2f else 1f) * pop
      val drop = Path().apply {
        moveTo(86f, y - 6f)
        cubicTo(90f, y - 1f, 91f, y + 2f, 86f, y + 4f)
        cubicTo(81f, y + 2f, 82f, y - 1f, 86f, y - 6f)
        close()
      }
      drawPath(drop, SWEAT.copy(alpha = a))
      drawCircle(Color.White.copy(alpha = 0.7f * a), radius = 1f, center = Offset(84.6f, y))
    }
    else -> Unit
  }
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
  val path = Path().apply {
    moveTo(c.x, c.y - r)
    quadraticTo(c.x + r * 0.18f, c.y - r * 0.18f, c.x + r, c.y)
    quadraticTo(c.x + r * 0.18f, c.y + r * 0.18f, c.x, c.y + r)
    quadraticTo(c.x - r * 0.18f, c.y + r * 0.18f, c.x - r, c.y)
    quadraticTo(c.x - r * 0.18f, c.y - r * 0.18f, c.x, c.y - r)
    close()
  }
  drawPath(path, color)
}


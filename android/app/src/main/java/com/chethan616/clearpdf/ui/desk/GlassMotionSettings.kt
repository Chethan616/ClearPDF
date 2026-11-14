package com.chethan616.clearpdf.ui.desk

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.chethan616.clearpdf.ui.glass.pressable
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.home.chipRowFade
import com.chethan616.clearpdf.ui.glass.EaseCurve
import com.chethan616.clearpdf.ui.glass.GlassCard
import com.chethan616.clearpdf.ui.glass.GlassChip
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassSettings
import com.chethan616.clearpdf.ui.glass.GlassSlider
import com.chethan616.clearpdf.ui.glass.GlassStyle
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.GlassToggle
import com.chethan616.clearpdf.ui.glass.MotionSettings
import com.chethan616.clearpdf.ui.glass.MotionStyle
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Settings ▸ Liquid glass & motion: a live stage of glass controls, then everything about the glass
 * (style and each of its values), the glass spring (a graph of how it moves, and its six values) and
 * the screens' curve (an editor with two handles, presets, and the pace), each graph playing on a loop.
 */
@Composable
fun GlassMotionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val g = GlassSettings.style
    val m = MotionSettings.style
    fun glass(s: GlassStyle) = GlassSettings.update(context, s)
    fun motion(s: MotionStyle) = MotionSettings.update(context, s)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "title") {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(null, stringResource(R.string.settings_glass_motion), onBack = onBack)
        }
        item(key = "stage") { PreviewStage() }

        item(key = "glass") { Section(stringResource(R.string.gm_glass_look), custom = g !in GlassStyle.Presets) }
        item(key = "glasstabs") {
            // with Adaptive luminance on, frost, colour, brightness and tint are the glass's own to
            // set: the presets are shown at rest
            Box(Modifier.graphicsLayer { alpha = if (GlassSettings.adaptive) 0.4f else 1f }) {
                PresetTiles(glassPresetNames(), GlassStyle.Presets.indexOf(g), { if (!GlassSettings.adaptive) glass(GlassStyle.Presets[it]) }) { i -> drawGlassSwatch(i) }
            }
        }
        item(key = "glassvalues") {
            FineTune {
                SliderRow(stringResource(R.string.gm_blur), g.blur, 0f..3f, Candy.Sky) { glass(g.copy(blur = it)) }
                SliderRow(stringResource(R.string.gm_lens_height), g.refractionHeight, 0f..2.5f, Candy.Grape) { glass(g.copy(refractionHeight = it)) }
                SliderRow(stringResource(R.string.gm_lens_amount), g.refractionAmount, 0f..2.5f, Candy.Grape) { glass(g.copy(refractionAmount = it)) }
                SliderRow(stringResource(R.string.gm_tint), g.tint, 0f..2.5f, Candy.Mint) { glass(g.copy(tint = it)) }
                SliderRow(stringResource(R.string.gm_saturation), g.saturation, 0.5f..2.5f, Candy.Bubblegum) { glass(g.copy(saturation = it)) }
                SliderRow(stringResource(R.string.gm_brightness), g.brightness, -0.3f..0.3f, Candy.Lemon) { glass(g.copy(brightness = it)) }
                SliderRow(stringResource(R.string.gm_highlight), g.highlight, 0f..2f, Candy.Tangerine) { glass(g.copy(highlight = it)) }
                ToggleRow(LucideIcons.Layers, Candy.Sky, stringResource(R.string.gm_depth), null, g.depthEffect) { glass(g.copy(depthEffect = it)) }
                ToggleRow(LucideIcons.Sparkles, Candy.Grape, stringResource(R.string.gm_chromatic), null, g.chromaticAberration) { glass(g.copy(chromaticAberration = it)) }
            }
        }

        // above the presets it rests (they are dimmed while it is on)
        item(key = "adaptive") {
            Spacer(Modifier.height(8.dp))
            Panel {
                ToggleRow(
                    LucideIcons.Sparkles, Candy.Lemon, stringResource(R.string.gm_adaptive), stringResource(R.string.gm_adaptive_body),
                    GlassSettings.adaptive, first = true,
                ) { GlassSettings.updateAdaptive(context, it) }
            }
        }

        item(key = "motion") { Section(stringResource(R.string.settings_motion), custom = m !in MotionStyle.Presets) }
        item(key = "motiontabs") {
            PresetTiles(motionPresetNames(), MotionStyle.Presets.indexOf(m), { motion(MotionStyle.Presets[it]) }) { i -> drawSpringSwatch(MotionStyle.Presets[i].bounce) }
        }
        item(key = "spring") {
            GraphCard(stringResource(R.string.gm_spring), stringResource(R.string.gm_spring_body)) { SpringGraph() }
        }
        item(key = "motionvalues") {
            FineTune {
                SliderRow(stringResource(R.string.gm_bounce), m.bounce, 0f..1f, Candy.Bubblegum) { motion(m.copy(bounce = it)) }
                SliderRow(stringResource(R.string.gm_speed), m.speed, 0.4f..2.5f, Candy.Sky, times) { motion(m.copy(speed = it)) }
                SliderRow(stringResource(R.string.gm_press), m.press, 0f..2.5f, Candy.Grape, times) { motion(m.copy(press = it)) }
                SliderRow(stringResource(R.string.gm_wobble), m.wobble, 0f..3f, Candy.Tomato, times) { motion(m.copy(wobble = it)) }
                SliderRow(stringResource(R.string.gm_stretch), m.stretch, 0f..1.6f, Candy.Mint, times) { motion(m.copy(stretch = it)) }
                SliderRow(stringResource(R.string.gm_bloom), m.bloom, 0f..2.5f, Candy.Tangerine, times) { motion(m.copy(bloom = it)) }
            }
        }

        item(key = "transitions") { Section(stringResource(R.string.gm_transitions), custom = MotionSettings.curve !in EaseCurve.Presets) }
        item(key = "curve") {
            GraphCard(null, stringResource(R.string.gm_curve_body)) {
                BezierEditor(MotionSettings.curve) { c, save -> MotionSettings.updateCurve(context, c, save) }
            }
        }
        item(key = "curves") {
            val names = curvePresetNames()
            LazyRow(
                Modifier.padding(vertical = 4.dp).chipRowFade(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(EaseCurve.Presets) { i, curve ->
                    com.chethan616.clearpdf.ui.home.DeskChip(names[i], MotionSettings.curve == curve) { MotionSettings.updateCurve(context, curve) }
                }
            }
        }
        item(key = "duration") {
            Panel {
                SliderRow(stringResource(R.string.gm_duration), MotionSettings.duration, 0.5f..2f, Candy.Sky, times) { MotionSettings.updateDuration(context, it) }
            }
        }
        item(key = "reset") {
            Spacer(Modifier.height(18.dp))
            Panel {
                SettingRow(LucideIcons.RotateCcw, Candy.Tomato, stringResource(R.string.gm_reset), {
                    glass(GlassStyle.Default)
                    GlassSettings.updateAdaptive(context, false)
                    motion(MotionStyle.Default)
                    MotionSettings.updateCurve(context, EaseCurve.Drawer)
                    MotionSettings.updateDuration(context, 1f)
                }, first = true)
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

private val times: (Float) -> String = { String.format(Locale.ROOT, "×%.2f", it) }

@Composable
internal fun glassPresetNames() = listOf(
    stringResource(R.string.settings_glass_clear), stringResource(R.string.settings_glass_frosted),
    stringResource(R.string.settings_glass_crystal), stringResource(R.string.settings_glass_lite),
)

@Composable
internal fun motionPresetNames() = listOf(
    stringResource(R.string.settings_motion_calm), stringResource(R.string.settings_motion_normal),
    stringResource(R.string.settings_motion_bouncy), stringResource(R.string.settings_motion_jelly),
)

@Composable
private fun curvePresetNames() = listOf(
    stringResource(R.string.gm_curve_drawer), stringResource(R.string.gm_curve_standard), stringResource(R.string.gm_curve_emphasized),
    stringResource(R.string.gm_curve_ease), stringResource(R.string.gm_curve_linear),
)

/** Glass controls to try the settings on: they press, swell, jiggle and bloom as set. */
@Composable
private fun PreviewStage() {
    val c = clear
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), shape = RoundedRectangle(30.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPill(onClick = {}, tint = Candy.Sky, onLongClick = {}) { Text(stringResource(R.string.gm_press_me), style = ClearType.BodyStrong, color = Color.White) }
                GlassCircle(onClick = {}, size = 48.dp, onLongClick = {}) { GlassIcon(LucideIcons.Sparkles, null, Modifier.size(22.dp)) }
                GlassChip(onClick = {}, height = 44.dp, bloom = true) { Text(stringResource(R.string.gm_chip), style = ClearType.Body, color = c.ink) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gm_preview_body), style = ClearType.Caption, color = c.inkSoft, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(12.dp))
                var on by remember { mutableStateOf(true) }
                GlassToggle(on, { on = it })
            }
        }
    }
}

/**
 * Four presets as tiles, each with a little picture of itself ([swatch]): the chosen one ringed and
 * ticked. A hand-tuned style chooses none.
 */
@Composable
private fun PresetTiles(names: List<String>, selected: Int, onSelect: (Int) -> Unit, swatch: androidx.compose.ui.graphics.drawscope.DrawScope.(Int) -> Unit) {
    val c = clear
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        names.forEachIndexed { i, name ->
            val on = i == selected
            Column(
                Modifier.weight(1f).pressable({ onSelect(i) }, pressedScale = 0.94f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .border(if (on) 2.5.dp else 1.dp, if (on) Candy.Sky else c.ink.copy(alpha = 0.12f), RoundedRectangle(18.dp))
                        .padding(4.dp)
                        .clip(RoundedRectangle(14.dp))
                        .drawBehind { swatch(i) },
                ) {
                    if (on) Box(
                        Modifier.align(Alignment.BottomEnd).padding(5.dp).size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Candy.Sky),
                        contentAlignment = Alignment.Center,
                    ) { Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(name, style = if (on) ClearType.BodyStrong else ClearType.Label, color = if (on) c.ink else c.inkSoft, maxLines = 1)
            }
        }
    }
}

/** A glass preset in miniature: candy stripes behind a disc of that glass. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassSwatch(i: Int) {
    val stripes = listOf(Candy.Sky, Candy.Bubblegum, Candy.Lemon, Candy.Mint)
    val band = size.width / 6f
    for (k in -2..8) {
        val x = k * band
        drawPath(
            androidx.compose.ui.graphics.Path().apply { moveTo(x, 0f); lineTo(x + band, 0f); lineTo(x + band - size.height, size.height); lineTo(x - size.height, size.height); close() },
            stripes[(k + 8) % stripes.size],
        )
    }
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.34f
    val (fill, rim) = when (i) {
        1 -> Color.White.copy(alpha = 0.62f) to 0.9f   // frosted
        2 -> Color.White.copy(alpha = 0.10f) to 1f     // crystal
        3 -> Color(0xFFEDEAF7).copy(alpha = 0.85f) to 0f // lite
        else -> Color.White.copy(alpha = 0.28f) to 0.8f  // clear
    }
    drawCircle(fill, r, center)
    if (i == 2) {
        drawCircle(
            androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color(0xFFFF6B9A), Color(0xFFFFD84D), Color(0xFF5BE3B0), Color(0xFF5AA9FF), Color(0xFFB36BFF), Color(0xFFFF6B9A)), center),
            r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()),
        )
    } else if (rim > 0f) {
        drawCircle(Color.White.copy(alpha = rim), r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}

/** A motion preset in miniature: its spring's swing from start to rest. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpringSwatch(bounce: Float) {
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2A2350), Color(0xFF1A1636))))
    val damping = (1f - bounce).coerceIn(0.12f, 1f)
    val w = size.width
    val h = size.height
    val rest = h * 0.32f
    val start = h * 0.82f
    drawLine(Color.White.copy(alpha = 0.18f), androidx.compose.ui.geometry.Offset(w * 0.08f, rest), androidx.compose.ui.geometry.Offset(w * 0.92f, rest), 1.dp.toPx())
    val path = androidx.compose.ui.graphics.Path()
    val omega = 14f
    for (k in 0..60) {
        val t = k / 60f
        val x = w * 0.08f + t * w * 0.84f
        val d = damping
        val v = if (d >= 1f) 1f - (1f + omega * t) * kotlin.math.exp(-omega * t)
        else {
            val wd = omega * kotlin.math.sqrt(1f - d * d)
            1f - kotlin.math.exp(-d * omega * t) * (kotlin.math.cos(wd * t) + d * omega / wd * kotlin.math.sin(wd * t))
        }
        val y = start + (rest - start) * v
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Candy.Bubblegum, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** Fine-tuning folded away under one row, so the page leads with the presets. */
@Composable
private fun FineTune(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = clear
    var open by remember { mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, com.chethan616.clearpdf.ui.glass.GlassMotion.settle(), label = "fine")
    Spacer(Modifier.height(8.dp))
    Panel {
        Row(
            Modifier.fillMaxWidth().pressable({ open = !open }).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).clip(RoundedRectangle(8.dp)).background(Candy.Grape), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.SlidersHorizontal, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.gm_fine_tune), style = ClearType.Body, color = c.ink, modifier = Modifier.weight(1f))
            Icon(LucideIcons.ChevronDown, null, tint = c.inkFaint, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn })
        }
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.expandVertically(com.chethan616.clearpdf.ui.glass.GlassMotion.settle()) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(com.chethan616.clearpdf.ui.glass.GlassMotion.settle()) + androidx.compose.animation.fadeOut(),
        ) { Column { content() } }
    }
}

/** A setting's name and value above its glass slider. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    format: (Float) -> String = { String.format(Locale.ROOT, "%.2f", it) },
    onChange: (Float) -> Unit,
) {
    val c = clear
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ClearType.Body, color = c.ink, modifier = Modifier.weight(1f))
            Text(format(value), style = ClearType.Label.copy(fontFamily = FontFamily.Monospace), color = c.inkSoft)
        }
        Spacer(Modifier.height(6.dp))
        GlassSlider(value, onChange, range = range, accent = accent)
    }
}

/** A graph on a frosted card: a title, a line on how to use it, the graph. */
@Composable
private fun GraphCard(title: String?, body: String, graph: @Composable () -> Unit) {
    val c = clear
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), shape = RoundedRectangle(28.dp), wash = c.frost, blur = 12.dp) {
        Column(Modifier.padding(18.dp)) {
            if (title != null) Text(title, style = ClearType.Title, color = c.ink)
            Text(body, style = ClearType.Caption, color = c.inkSoft, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            graph()
        }
    }
}

/** A loop from 0 to 1 over [millis], paused while [playing] is false, resting a beat at the end. */
@Composable
private fun rememberLoop(playing: Boolean, millis: Int): Animatable<Float, *> {
    val t = remember { Animatable(0f) }
    LaunchedEffect(playing, millis) {
        while (playing) {
            if (t.value >= 1f) t.snapTo(0f)
            t.animateTo(1f, tween(((1f - t.value) * millis).toInt().coerceAtLeast(1), easing = LinearEasing))
            delay(500)
        }
    }
    return t
}

/**
 * The glass spring (the one glass morphs on: menus, dialogs, the + glyph) as a graph of where it is
 * over time: it rises, swings past rest and settles. The pink dot plays it; tap to pause.
 */
@Composable
private fun SpringGraph() {
    val c = clear
    val accent = Candy.Bubblegum
    val zeta = MotionSettings.damping(0.58f)
    val k = MotionSettings.stiffness(420f)
    val w0 = sqrt(k)
    val wd = w0 * sqrt(1f - zeta * zeta)
    // to where the spring is let go (0.05 % from rest, GlassMotion's threshold), as menus now run it
    val settle = (ln(2000f) / (zeta * w0)).coerceIn(0.3f, 4f)
    fun x(t: Float): Float = 1f - exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
    val hi = remember(zeta, w0) { (0..160).maxOf { x(settle * it / 160f) }.coerceAtLeast(1f) + 0.08f }
    val lo = -0.08f
    var playing by remember { mutableStateOf(true) }
    val loop = rememberLoop(playing, (settle * 1000).toInt())
    Canvas(Modifier.fillMaxWidth().height(170.dp).pointerInput(Unit) { detectTapGestures { playing = !playing } }) {
        val pad = 10.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun px(t: Float) = pad + t / settle * w
        fun py(v: Float) = pad + (hi - v) / (hi - lo) * h
        val grid = c.inkFaint.copy(alpha = 0.5f)
        drawLine(grid, Offset(pad, py(1f)), Offset(pad + w, py(1f)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        drawLine(grid, Offset(pad, py(0f)), Offset(pad + w, py(0f)), 1.dp.toPx())
        val full = Path()
        val done = Path()
        val p = loop.value
        for (i in 0..160) {
            val t = settle * i / 160f
            val o = Offset(px(t), py(x(t)))
            if (i == 0) full.moveTo(o.x, o.y) else full.lineTo(o.x, o.y)
            if (i / 160f <= p) { if (i == 0) done.moveTo(o.x, o.y) else done.lineTo(o.x, o.y) }
        }
        drawPath(full, c.ink.copy(alpha = 0.8f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(done, accent, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
        val t = settle * p
        drawCircle(accent, 9.dp.toPx(), Offset(px(t), py(x(t))))
    }
    Text(
        stringResource(
            R.string.gm_spring_stats,
            String.format(Locale.ROOT, "%.2f", zeta), String.format(Locale.ROOT, "%.0f", k), String.format(Locale.ROOT, "%.1f", settle),
        ),
        style = ClearType.Caption.copy(fontFamily = FontFamily.Monospace), color = c.inkSoft, modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * The screens' curve, drawn as a cubic-bezier: a box from (0, 0) to (1, 1), the curve through it, the
 * two handles on dotted arms (drag them; the curve may swing past the box up and down), a pink dot
 * running along it at the curve's own pace, and the four numbers that name it. Tap to pause.
 */
@Composable
private fun BezierEditor(curve: EaseCurve, onChange: (EaseCurve, Boolean) -> Unit) {
    val c = clear
    val accent = Candy.Bubblegum
    val latest by rememberUpdatedState(curve)
    val change by rememberUpdatedState(onChange)
    var playing by remember { mutableStateOf(true) }
    val loop = rememberLoop(playing, (900 * MotionSettings.duration).toInt())
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, 320f).dp
        Canvas(
            Modifier.size(side)
                .pointerInput(Unit) { detectTapGestures { playing = !playing } }
                .pointerInput(Unit) {
                    var which = 0
                    val pad = 22.dp.toPx()
                    val span = { size.width - 2 * pad }
                    fun toPx(x: Float, y: Float) = Offset(pad + x * span(), size.height - pad - (y + 0.25f) / 1.5f * span())
                    detectDragGestures(
                        onDragStart = { at ->
                            val a = latest.let { toPx(it.x1, it.y1) }
                            val b = latest.let { toPx(it.x2, it.y2) }
                            val da = hypot(at.x - a.x, at.y - a.y)
                            val db = hypot(at.x - b.x, at.y - b.y)
                            val reach = 56.dp.toPx()
                            which = when {
                                da <= db && da < reach -> 1
                                db < da && db < reach -> 2
                                else -> 0
                            }
                        },
                        onDragEnd = { if (which != 0) change(latest, true); which = 0 },
                        onDragCancel = { if (which != 0) change(latest, true); which = 0 },
                        onDrag = { ch, _ ->
                            if (which == 0) return@detectDragGestures
                            ch.consume()
                            val x = ((ch.position.x - pad) / span()).coerceIn(0f, 1f)
                            val y = (((size.height - pad - ch.position.y) / span()) * 1.5f - 0.25f).coerceIn(-0.25f, 1.25f)
                            val r = { v: Float -> Math.round(v * 100f) / 100f }
                            change(if (which == 1) latest.copy(x1 = r(x), y1 = r(y)) else latest.copy(x2 = r(x), y2 = r(y)), false)
                        },
                    )
                },
        ) {
            val pad = 22.dp.toPx()
            val span = size.width - 2 * pad
            fun at(x: Float, y: Float) = Offset(pad + x * span, size.height - pad - (y + 0.25f) / 1.5f * span)
            val box0 = at(0f, 1f)
            val box1 = at(1f, 0f)
            drawRect(c.ink.copy(alpha = 0.55f), box0, Size(box1.x - box0.x, box1.y - box0.y), style = Stroke(1.5.dp.toPx()))
            val p0 = at(0f, 0f)
            val p3 = at(1f, 1f)
            val h1 = at(curve.x1, curve.y1)
            val h2 = at(curve.x2, curve.y2)
            val dots = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawLine(c.inkSoft, p0, h1, 1.5.dp.toPx(), pathEffect = dots)
            drawLine(c.inkSoft, p3, h2, 1.5.dp.toPx(), pathEffect = dots)
            val path = Path().apply { moveTo(p0.x, p0.y); cubicTo(h1.x, h1.y, h2.x, h2.y, p3.x, p3.y) }
            drawPath(path, c.ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // the part the dot has run, in pink
            val easing = curve.easing
            val p = loop.value
            val done = Path()
            val steps = 80
            for (i in 0..steps) {
                val x = p * i / steps
                val o = at(x, easing.transform(x))
                if (i == 0) done.moveTo(o.x, o.y) else done.lineTo(o.x, o.y)
            }
            drawPath(done, accent, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent, 10.dp.toPx(), at(p, easing.transform(p)))
            // the handles: hollow squares, as the motion specs draw them
            for (hnd in listOf(h1, h2)) {
                val s = 16.dp.toPx()
                drawRoundRect(c.ink, Offset(hnd.x - s / 2, hnd.y - s / 2), Size(s, s), CornerRadius(3.dp.toPx()))
                drawRoundRect(if (c.dark) Color(0xFF1C2433) else Color.White, Offset(hnd.x - s / 4, hnd.y - s / 4), Size(s / 2, s / 2), CornerRadius(1.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (v in listOf(curve.x1, curve.y1, curve.x2, curve.y2)) {
            Text(
                String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" },
                style = ClearType.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 17.sp), color = c.inkSoft,
            )
        }
    }
}

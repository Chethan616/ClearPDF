package com.chethan616.clearpdf.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.PhotoEditor
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.LocalHaptics
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** One adjustment of LightCraft's develop settings: where it lives, its name, glyph and range. */
private enum class Adjust(val section: String, val field: String, val label: Int, val icon: ImageVector, val min: Float, val max: Float, val decimals: Int) {
    Exposure("light", "exposure", R.string.photo_exposure, LucideIcons.Sun, -3f, 3f, 2),
    Contrast("light", "contrast", R.string.photo_contrast, LucideIcons.Contrast, -100f, 100f, 0),
    Highlights("light", "highlights", R.string.photo_highlights, LucideIcons.Highlights, -100f, 100f, 0),
    Shadows("light", "shadows", R.string.photo_shadows, LucideIcons.Shadows, -100f, 100f, 0),
    Vibrance("color", "vibrance", R.string.photo_vibrance, LucideIcons.Palette, -100f, 100f, 0),
    Saturation("color", "saturation", R.string.photo_saturation, LucideIcons.Droplet, -100f, 100f, 0),
}

/** The quarter turns LightCraft's orientation names, clockwise from as shot. */
private val TURNS = listOf("Normal", "Rotate90", "Rotate180", "Rotate270")

/** "+0.40", "−12", "0": signed, as Photos shows an adjustment. */
private fun shown(a: Adjust, v: Float): String {
    val zero = if (a.decimals > 0) 0.005f else 0.5f
    if (abs(v) < zero) return "0"
    val n = if (a.decimals > 0) "%.${a.decimals}f".format(abs(v)) else abs(v).toInt().toString()
    return (if (v > 0) "+" else "−") + n
}

/**
 * A picture developed with LightCraft, the way Photos edits one: the picture large (hold it to see
 * the original), the chosen adjustment's name and value, a ruler to drag it along (it catches at
 * zero), and the adjustments as a row of glass dials, each ringed by how far it has moved. Rotate
 * and Reset at the top; Done saves the developed picture in place of the note's.
 */
@Composable
fun PhotoEditScreen(vm: AppViewModel, noteId: String, name: String, onClose: () -> Unit) {
    val source = remember(name) { vm.notes.file(name) }
    val editor = remember { PhotoEditor() }
    DisposableEffect(editor) { onDispose { editor.release(); editor.close() } }
    val values = remember { mutableStateMapOf<Adjust, Float>() }
    var turn by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(Adjust.Exposure) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var original by remember { mutableStateOf<ImageBitmap?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val edited = values.values.any { it != 0f } || turn != 0

    fun settings(withValues: Boolean = true): String {
        val o = JSONObject()
        if (withValues) for ((a, v) in values) if (v != 0f) (o.optJSONObject(a.section) ?: JSONObject().also { o.put(a.section, it) }).put(a.field, v.toDouble())
        if (turn != 0) o.put("orientation", TURNS[turn])
        return o.toString()
    }

    // re-develop as the ruler moves; a newer value cancels the render still running
    LaunchedEffect(Unit) {
        snapshotFlow { values.toMap() to turn }.collectLatest {
            val json = settings()
            shown = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1600u).toBitmap().asImageBitmap() }.getOrNull() } ?: shown
        }
    }
    // the picture as shot (turned as the edit is), for holding to compare
    LaunchedEffect(turn) {
        val json = settings(withValues = false)
        original = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1600u).toBitmap().asImageBitmap() }.getOrNull() }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        comparing = true
                        tryAwaitRelease()
                        comparing = false
                    })
                },
                contentAlignment = Alignment.Center,
            ) {
                (if (comparing) original ?: shown else shown)?.let {
                    Image(it, null, Modifier.clip(RoundedRectangle(14.dp)), contentScale = ContentScale.Fit)
                }
                if (comparing) Text(
                    stringResource(R.string.photo_original), style = ClearType.Label, color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).background(Color.Black.copy(alpha = 0.45f), RoundedRectangle(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(250.dp))
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // the top row: cancel, reset (once something changed), rotate, done
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = onClose, label = stringResource(R.string.common_cancel), ink = Color.White) { GlassIcon(LucideIcons.X, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.weight(1f))
                    if (edited) GlassPill(onClick = { values.clear(); turn = 0 }, height = 40.dp, horizontalPadding = 16.dp) {
                        Text(stringResource(R.string.photo_reset), style = ClearType.Label.copy(fontSize = 14.sp), color = Candy.Lemon)
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassCircle(onClick = { turn = (turn + 1) % 4 }, label = stringResource(R.string.photo_rotate), ink = Color.White) { GlassIcon(LucideIcons.RotateCw, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.width(10.dp))
                    GlassCircle(
                        onClick = {
                            if (saving) return@GlassCircle
                            if (!edited) { onClose(); return@GlassCircle }
                            saving = true
                            scope.launch {
                                val out = File(source.parentFile, "edited-${System.currentTimeMillis()}.jpg")
                                val ok = withContext(Dispatchers.Default) { runCatching { editor.exportJpeg(source.absolutePath, settings(), out.absolutePath, 92u) }.isSuccess }
                                if (ok) vm.notes.get(noteId)?.let { n -> vm.notes.put(n.copy(images = n.images.map { if (it == name) out.name else it })) }
                                else vm.tell(com.chethan616.clearpdf.text(R.string.photo_not_saved))
                                saving = false
                                onClose()
                            }
                        },
                        tint = Candy.Sky, label = stringResource(R.string.common_done),
                    ) { GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp)) }
                }
            }

            // the controls: the chosen adjustment's value, its ruler, the dials
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                val v = values[chosen] ?: 0f
                Text(stringResource(chosen.label).uppercase(), style = ClearType.Label.copy(fontSize = 12.sp, letterSpacing = 1.4.sp), color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(4.dp))
                Text(shown(chosen, v), style = ClearType.Title.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = if (v != 0f) Candy.Lemon else Color.White)
                Spacer(Modifier.height(10.dp))
                Ruler(chosen, v) { values[chosen] = it }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    for (a in Adjust.entries) Dial(a, values[a] ?: 0f, a == chosen) { chosen = a }
                }
                Spacer(Modifier.height(18.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * An adjustment as a round glass button: its glyph, a ring that fills by how far it has moved (the
 * chosen one lit in lemon), as Photos draws its adjustment dials.
 */
@Composable
private fun Dial(a: Adjust, value: Float, chosen: Boolean, onClick: () -> Unit) {
    val span = maxOf(abs(a.min), abs(a.max))
    val part = (value / span).coerceIn(-1f, 1f)
    Box(Modifier.size(60.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(58.dp)) {
            val stroke = 2.6.dp.toPx()
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)
            drawCircle(Color.White.copy(alpha = if (chosen) 0.16f else 0.08f), radius = size.minDimension / 2)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            if (part != 0f) drawArc(if (chosen) Candy.Lemon else Color.White, -90f, 360f * part, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        GlassIcon(a.icon, stringResource(a.label), Modifier.size(22.dp), tint = if (chosen) Candy.Lemon else Color.White)
    }
}

/**
 * The value's ruler: ticks across the whole range, the value under the fixed centre line; dragging
 * moves the ruler, and it catches at zero with a tick of the haptics.
 */
@Composable
private fun Ruler(a: Adjust, value: Float, onChange: (Float) -> Unit) {
    val haptics = LocalHaptics.current
    val range = a.max - a.min
    var drag by remember(a) { mutableStateOf(value) }
    LaunchedEffect(value) { drag = value }
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).pointerInput(a) {
            // the whole range spans three screen widths
            val perPx = range / (size.width * 3f)
            detectHorizontalDragGestures { change, dx ->
                change.consume()
                val before = drag
                var next = (drag - dx * perPx).coerceIn(a.min, a.max)
                // a catch at zero
                if (abs(next) < range * 0.012f) next = 0f
                if ((before != 0f && next == 0f)) haptics?.play(Feel.Tick)
                drag = next
                onChange(next)
            }
        },
    ) {
        val w = size.width * 3f
        val mid = size.width / 2
        val t = (drag - a.min) / range
        val ticks = 60
        for (i in 0..ticks) {
            val x = mid + (i.toFloat() / ticks - t) * w
            if (x < -4 || x > size.width + 4) continue
            val major = i % 10 == 0
            val h = if (major) size.height * 0.55f else size.height * 0.32f
            val fade = (1f - abs(x - mid) / mid).coerceIn(0.15f, 1f)
            drawLine(Color.White.copy(alpha = (if (major) 0.8f else 0.45f) * fade), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 1.6.dp.toPx(), StrokeCap.Round)
        }
        // zero's mark, and the centre line the value sits under
        val zx = mid + ((0f - a.min) / range - t) * w
        if (zx in 0f..size.width) drawCircle(Candy.Lemon, 2.4.dp.toPx(), Offset(zx, size.height - 2.dp.toPx()))
        drawLine(Candy.Lemon, Offset(mid, 0f), Offset(mid, size.height), 2.4.dp.toPx(), StrokeCap.Round)
    }
}

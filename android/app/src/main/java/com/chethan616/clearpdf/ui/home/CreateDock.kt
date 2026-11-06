package com.chethan616.clearpdf.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.floatingLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.notes.NoteKind
import com.chethan616.clearpdf.notes.waveOf
import com.chethan616.clearpdf.text
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.FilePickKind
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.desk.ToolCatalog
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.Fluid
import com.chethan616.clearpdf.ui.glass.GlassChip
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.LocalHaptics
import com.chethan616.clearpdf.ui.glass.MotionSettings
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.VoiceRecorder
import com.chethan616.clearpdf.ui.viewer.clock
import com.kyant.shapes.Capsule
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The ink of the + and the microphone: near black, as the mock's +. */
private val DockInk = Color(0xFF1C2433)

/**
 * The create capsule's state: the fan of ways to make something, a voice memo being recorded, and
 * the memo being named.
 */
@Stable
class DeskState(private val scope: CoroutineScope) {
    /** The app's haptics, set by the dock. */
    internal var haptics: com.chethan616.clearpdf.ui.glass.Haptics? = null

    var fanOpen by mutableStateOf(false)
    var recorder by mutableStateOf<VoiceRecorder?>(null)
        private set
    private var file: File? = null

    /** The voice memo whose name is being typed (its id), or null. */
    var naming by mutableStateOf<String?>(null)

    /**
     * Turns the capsule into the recorder at once (the tap answers in the same frame) while the
     * microphone opens off the main thread, which can take a moment on some phones.
     */
    fun startRecording(vm: AppViewModel) {
        fanOpen = false
        if (recorder != null) return
        vm.withMic {
            val f = vm.notes.newMedia("wav")
            val r = VoiceRecorder(f, scope)
            file = f
            recorder = r
            haptics?.play(Feel.Rise)
            scope.launch {
                if (!r.startAsync()) {
                    if (recorder === r) {
                        recorder = null
                        file = null
                        haptics?.play(Feel.Reject)
                        vm.tell(text(R.string.msg_voice_no_mic))
                    }
                    f.delete()
                }
            }
        }
    }

    fun finish(vm: AppViewModel, keep: Boolean) {
        val r = recorder ?: return
        val f = file
        recorder = null
        file = null
        scope.launch {
            r.stop()
            if (f == null) return@launch
            if (!keep || r.elapsedMs < 600) {
                f.delete()
                haptics?.play(Feel.Reject)
                if (keep) vm.tell(text(R.string.voice_too_short))
                return@launch
            }
            haptics?.play(Feel.Confirm)
            val note = Note(
                java.util.UUID.randomUUID().toString(), NoteKind.Voice,
                audio = f.name, durationMs = r.elapsedMs, wave = waveOf(r.levels.toList()),
            )
            vm.notes.add(note)
            // saved straight away; a name is offered, never asked for
            vm.tell(text(R.string.voice_saved), text(R.string.voice_name_it)) { naming = note.id }
        }
    }
}

/**
 * The floating capsule at the desk's foot, as the mock has it: a white + and a glass microphone on
 * dark glass. The + fans out the ways to make something; the microphone turns the capsule into a
 * recorder (time, live waveform, stop). The capsule is liquid glass (the Back button's) and changes
 * shape on a glass spring ([GlassMotion], tuned by Settings ▸ Motion): it swells past the recorder's
 * size and rebounds when it shrinks back. Its size is animated in the layout pass only, so nothing
 * recomposes while it moves.
 */
@Composable
fun BoxScope.CreateDock(
    vm: AppViewModel,
    desk: DeskState,
    onBrowse: () -> Unit,
    intoNote: Boolean = false,
    fromNote: Boolean = false,
    onNoteMorphed: () -> Unit = {},
) {
    val c = clear
    desk.haptics = LocalHaptics.current
    BackHandler(enabled = desk.fanOpen && !intoNote) { desk.fanOpen = false }
    BackHandler(enabled = desk.recorder != null && !intoNote) { desk.finish(vm, keep = false) }
    // a note opened from the desk: the capsule grows into the note's toolbar (0 = dock, 1 = toolbar) on
    // the glass spring and hands over the moment it is that size; back on the desk, the toolbar shrinks
    // back into the dock the same way
    val into = remember { Animatable(if (fromNote) 1f else 0f) }
    val done by androidx.compose.runtime.rememberUpdatedState(onNoteMorphed)
    LaunchedEffect(intoNote, fromNote) {
        when {
            intoNote -> {
                val reached = kotlinx.coroutines.CompletableDeferred<Unit>()
                launch {
                    into.animateTo(1f, GlassMotion.unfold()) { if (value >= 0.96f) reached.complete(Unit) }
                    reached.complete(Unit)
                }
                kotlinx.coroutines.withTimeoutOrNull(600L) { reached.await() }
                done()
            }
            fromNote -> {
                into.animateTo(0f, GlassMotion.unfold())
                done()
            }
            else -> into.snapTo(0f)
        }
    }
    // leaving the desk or the app ends a recording, kept: the microphone never runs unseen
    DisposableEffect(Unit) { onDispose { desk.fanOpen = false; desk.finish(vm, keep = true) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { desk.finish(vm, keep = true) }
    // The scrim is read in draw and through derived flags, so the fan opening recomposes this dock
    // once, not on every frame of the fade (which rebuilt every chip's glass sixty times a second).
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(desk.fanOpen) { scrim.animateTo(if (desk.fanOpen) 1f else 0f, Fluid.quick()) }
    val scrimShown by remember { derivedStateOf { scrim.value > 0.01f } }
    val fanShown by remember { derivedStateOf { desk.fanOpen || scrim.value > 0.01f } }
    if (scrimShown) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    alpha = scrim.value
                    // one flat rectangle: no need for the offscreen pass a fractional alpha would ask for
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .background(Color.Black.copy(alpha = if (c.dark) 0.45f else 0.24f))
                .clickable(remember { MutableInteractionSource() }, null) { desk.fanOpen = false },
        )
    }
    // composed while the scrim is still fading, so the items can fold back into the +
    if (fanShown) Fan(vm, desk, desk.fanOpen, onBrowse)

    // the recorder stays drawn while the capsule shrinks back
    val rec = desk.recorder
    var lingering by remember { mutableStateOf<VoiceRecorder?>(null) }
    val shown = rec ?: lingering
    // the desk under the fan or the recorder is still: kept as one texture while they move over it
    val host = com.chethan616.clearpdf.ui.glass.LocalGlassOverlayHost.current
    val still = fanShown || shown != null
    if (host != null && still) DisposableEffect(host) { host.stillHolds++; onDispose { host.stillHolds-- } }
    // 0 = the + and mic capsule, 1 = the recorder. A slow, soft glass spring (Settings ▸ Motion, with a
    // floor on its damping so Jelly can't ring on): it swells past the recorder's size on the way out
    // and pinches at the rebound on the way back; only draw/layout read it.
    val morph = remember { Animatable(0f) }
    LaunchedEffect(rec) {
        if (rec != null) {
            lingering = rec
            morph.animateTo(1f, GlassMotion.shape())
        } else {
            morph.animateTo(0f, GlassMotion.shapeBack())
            lingering = null
        }
    }

    Layout(
        content = {
            Row(
                Modifier.graphicsLayer {
                    alpha = (1f - morph.value * 2.2f).coerceIn(0f, 1f) * (1f - into.value * 3f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlusButton(desk.fanOpen) {
                    desk.fanOpen = !desk.fanOpen
                    desk.haptics?.play(if (desk.fanOpen) Feel.Rise else Feel.Fall)
                }
                GlassCircle(
                    onClick = { if (desk.recorder == null) desk.startRecording(vm) }, size = 54.dp, surface = Color.White.copy(alpha = 0.30f),
                    label = stringResource(R.string.create_voice),
                ) { GlassIcon(LucideIcons.Mic, null, Modifier.size(22.dp), tint = DockInk) }
            }
            shown?.let { r ->
                Box(
                    Modifier.graphicsLayer {
                        alpha = ((morph.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                ) { Recording(r, onCancel = { desk.finish(vm, keep = false) }, onStop = { desk.finish(vm, keep = true) }) }
            }
        },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
            // the jelly of the swell: a squash mid-flight and a pinch at the rebound, anchored at the foot
            .graphicsLayer {
                val m = morph.value
                val k = MotionSettings.style.stretch.coerceIn(0f, 1.6f)
                val mid = 4f * m.coerceIn(0f, 1f) * (1f - m.coerceIn(0f, 1f))
                val swell = (m - 1f).coerceAtLeast(0f)
                val pinch = (-m).coerceAtLeast(0f)
                // small and bounded: the width's own swell is limited by the screen (see the measure
                // block), so the jelly is carried by a gentle squash, never an extreme one
                scaleX = (1f + swell * 0.06f * k - pinch * 0.2f * k).coerceIn(0.92f, 1.03f)
                scaleY = (1f - mid * 0.03f * k - swell * 0.22f * k + pinch * 0.15f * k).coerceIn(0.93f, 1.06f)
                transformOrigin = TransformOrigin(0.5f, 1f)
                // the note's toolbar sits 4 dp lower than the dock
                translationY = into.value.coerceIn(0f, 1f) * 4.dp.toPx()
            }
            // the very glass of the Back and menu buttons; the recorder darkens it so its white reads
            .glass(Capsule, c.glass, blur = 3.dp, lensHeight = 14.dp, lensAmount = 26.dp)
            .graphicsLayer { clip = true; shape = Capsule }
            .drawBehind { drawRect(c.capsule, alpha = (lerp(0.5f, 1.9f, morph.value.coerceIn(0f, 1f)) + 0.5f * into.value.coerceIn(0f, 1f)).coerceAtMost(1f)) }
            .padding(6.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val idle = measurables[0].measure(loose)
        val recording = measurables.getOrNull(1)?.measure(loose)
        // past 1 the capsule swells beyond the recorder, but only by what the screen has room for (a
        // swing wider than that was cut off at the screen's edge and read as a stall), eased in so it
        // never hits a wall; past 0 it pinches in a little (the rest of the rebound is the squeeze
        // above), and it can never collapse to nothing
        val raw = morph.value
        val m = when {
            raw < 0f -> raw * 0.25f
            raw > 1f && recording != null -> {
                val room = (constraints.maxWidth - recording.width).coerceAtLeast(0).toFloat()
                val cap = (room / (recording.width - idle.width).coerceAtLeast(1)).coerceIn(0f, 0.12f)
                if (cap <= 0.001f) 1f else 1f + cap * kotlin.math.tanh((raw - 1f) / cap)
            }
            else -> raw
        }
        var w = if (recording == null) idle.width else lerp(idle.width.toFloat(), recording.width.toFloat(), m).roundToInt().coerceAtLeast(1)
        var h = if (recording == null) idle.height else lerp(idle.height.toFloat(), recording.height.toFloat(), m).roundToInt().coerceAtLeast(1)
        // becoming the note's toolbar: its glass, less the 6 dp this capsule pads on every side
        val bar = vm.noteBarSize
        val n = into.value
        if (n != 0f && bar.width > 0) {
            val pad = 12.dp.roundToPx()
            w = lerp(w.toFloat(), (bar.width - pad).toFloat(), n).roundToInt().coerceAtLeast(1)
            h = lerp(h.toFloat(), (bar.height - pad).toFloat(), n).roundToInt().coerceAtLeast(1)
        }
        layout(w, h) {
            idle.place((w - idle.width) / 2, (h - idle.height) / 2)
            recording?.place((w - recording.width) / 2, (h - recording.height) / 2)
        }
    }
}

/** The white + (it turns to an × while the fan is open). */
@Composable
private fun PlusButton(open: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, if (open) GlassMotion.morph() else Fluid.dismiss(), label = "plus")
    val label = stringResource(R.string.dock_create)
    Box(
        Modifier.size(58.dp)
            .pressable(onClick, pressedScale = 0.9f, feel = Feel.Press)
            .graphicsLayer { shadowElevation = 4.dp.toPx(); shape = CircleShape; clip = true }
            .background(Color.White)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { PlusGlyph(DockInk, Modifier.size(22.dp).graphicsLayer { rotationZ = turn }) }
}

/** A way to make something, in the fan. */
private class FanItem(val icon: ImageVector, val label: String, val accent: Color, val go: () -> Unit)

/** The fan: the ways to make something, rising from the + one after another. */
@Composable
private fun BoxScope.Fan(vm: AppViewModel, desk: DeskState, open: Boolean, onBrowse: () -> Unit) {
    val c = clear
    // glass bodies: the Back button's glass, with enough body that the label reads over the desk
    val body = c.frost.copy(alpha = c.frost.alpha * if (c.dark) 0.40f else 0.32f)
    fun go(f: () -> Unit): () -> Unit = {
        desk.fanOpen = false
        f()
    }
    val items = listOf(
        FanItem(LucideIcons.SquarePen, stringResource(R.string.create_note), Candy.Sky, go { vm.newNote(NoteKind.Text) }),
        FanItem(LucideIcons.ListChecks, stringResource(R.string.create_list), Candy.Mint, go { vm.newNote(NoteKind.List) }),
        FanItem(ToolCatalog.Scan.icon, stringResource(ToolCatalog.Scan.label), Candy.Tomato, go { ToolCatalog.Scan.go(vm) }),
        FanItem(LucideIcons.FolderOpen, stringResource(R.string.home_open_file), Candy.Grape, go(onBrowse)),
        FanItem(LucideIcons.Images, stringResource(R.string.desk_photos_to_pdf), Candy.Tangerine, go { vm.pickPhotosToPdf() }),
        FanItem(LucideIcons.Combine, stringResource(R.string.common_tool_combine), Candy.Bubblegum, go { vm.pickFiles(FilePickKind.Combine) }),
    )
    val haptics = LocalHaptics.current
    LaunchedEffect(open) { if (open) { delay(60); haptics?.cascade(items.size) } }
    Column(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { i, item -> FanChip(item, i, items.size, open, body) }
    }
}

/**
 * One item of the fan. The nearest (last) comes first, as if they spill out of the +; each is
 * composed only when its turn comes, so the tap builds one chip's glass, not six at once (the column
 * grows upward from the +, so nothing shifts). They spring out on the menu's glass spring (the position
 * and size may go past rest, the fade never does) and slide straight back in. Each slides as a finished
 * texture ([floatingLayer]): its glass is drawn once, where it comes to rest, not on every frame.
 */
@Composable
private fun FanChip(item: FanItem, index: Int, count: Int, open: Boolean, body: Color) {
    val c = clear
    val p = remember { Animatable(0f) }
    val a = remember { Animatable(0f) }
    var live by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            delay(26L * (count - 1 - index))
            live = true
            launch { a.animateTo(1f, Fluid.fade()) }
            p.animateTo(1f, GlassMotion.unfold())
        } else {
            launch { a.animateTo(0f, Fluid.fade()) }
            p.animateTo(0f, Fluid.dismiss())
        }
    }
    if (!live) return
    GlassChip(
        onClick = { if (open) item.go() },
        modifier = Modifier.floatingLayer {
            translationY = (1f - p.value) * (28 + 10 * (count - index)).dp.toPx()
            val s = 0.86f + 0.14f * p.value
            scaleX = s
            scaleY = s
            alpha = a.value.coerceIn(0f, 1f)
        },
        height = 54.dp,
        fill = { body },
        contentPadding = PaddingValues(start = 8.dp, end = 22.dp),
        horizontalArrangement = Arrangement.Start,
        blur = 6.dp,
        tap = Feel.Tap,
        label = item.label,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(item.accent), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(item.label, style = ClearType.BodyStrong, color = c.ink)
    }
}

/** The capsule as a recorder: cancel, a pulsing dot with the time, the live waveform, stop. */
@Composable
private fun Recording(rec: VoiceRecorder, onCancel: () -> Unit, onStop: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rec").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // the + turns to an x in the dock's near-black; the recorder's x wears the same ink, on a light
        // glass so it reads against the dark capsule
        GlassCircle(onClick = onCancel, size = 50.dp, surface = Color.White.copy(alpha = 0.82f), label = stringResource(R.string.common_cancel)) {
            GlassIcon(LucideIcons.X, null, Modifier.size(20.dp), tint = DockInk)
        }
        // the pulse is read in draw: it never recomposes the row
        Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(Candy.Tomato))
        Elapsed(rec)
        LiveWave(rec.levels, Modifier.width(112.dp).height(34.dp))
        Box(
            Modifier.size(54.dp).pressable(onStop, pressedScale = 0.9f, feel = Feel.Confirm).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(18.dp).clip(com.kyant.shapes.RoundedRectangle(5.dp)).background(Candy.Tomato)) }
    }
}

/** The time recorded, on its own so only it recomposes as the time runs. */
@Composable
private fun Elapsed(rec: VoiceRecorder) {
    Text(clock(rec.elapsedMs), style = ClearType.Number.copy(fontSize = 17.sp), color = Color.White)
}

/** The last moments' loudness, newest on the right (read in draw: no recomposition per sample). */
@Composable
private fun LiveWave(levels: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val n = 22
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val recent = levels.takeLast(n)
        val peak = (recent.maxOrNull() ?: 0f).coerceAtLeast(0.08f)
        for (i in 0 until n) {
            val v = recent.getOrNull(recent.size - n + i) ?: 0f
            val h = (size.height * (0.12f + 0.88f * (v / peak))).coerceAtLeast(w)
            drawRoundRect(Color.White, Offset(i * (w + gap), (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2f))
        }
    }
}

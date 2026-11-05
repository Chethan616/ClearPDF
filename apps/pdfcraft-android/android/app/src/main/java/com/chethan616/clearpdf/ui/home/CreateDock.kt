package com.chethan616.clearpdf.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.notes.NoteKind
import com.chethan616.clearpdf.notes.waveOf
import com.chethan616.clearpdf.text
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.FilePickKind
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.desk.ToolCatalog
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.VoiceRecorder
import com.chethan616.clearpdf.ui.viewer.clock
import com.kyant.shapes.Capsule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The create capsule's state: the fan of ways to make something, and a voice memo being recorded. */
@Stable
class DeskState(private val scope: CoroutineScope) {
    var fanOpen by mutableStateOf(false)
    var recorder by mutableStateOf<VoiceRecorder?>(null)
        private set
    private var file: File? = null

    fun startRecording(vm: AppViewModel) {
        fanOpen = false
        vm.withMic {
            val f = vm.notes.newMedia("wav")
            val r = VoiceRecorder(f, scope)
            if (r.start()) {
                file = f
                recorder = r
            } else {
                f.delete()
                vm.tell(text(R.string.msg_voice_no_mic))
            }
        }
    }

    fun finish(vm: AppViewModel, keep: Boolean) {
        val r = recorder ?: return
        val f = file
        scope.launch {
            r.stop()
            recorder = null
            file = null
            if (f == null) return@launch
            if (!keep || r.elapsedMs < 600) {
                f.delete()
                if (keep) vm.tell(text(R.string.voice_too_short))
                return@launch
            }
            vm.notes.add(
                Note(
                    java.util.UUID.randomUUID().toString(), NoteKind.Voice,
                    audio = f.name, durationMs = r.elapsedMs, wave = waveOf(r.levels.toList()),
                ),
            )
            vm.tell(text(R.string.voice_saved))
        }
    }
}

/**
 * The floating capsule at the desk's foot, as the mock has it: a white + and a glass microphone on
 * dark glass. The + fans out the ways to make something; the microphone turns the capsule into a
 * recorder (live waveform, time, stop).
 */
@Composable
fun BoxScope.CreateDock(vm: AppViewModel, desk: DeskState, onBrowse: () -> Unit) {
    val c = clear
    BackHandler(enabled = desk.fanOpen) { desk.fanOpen = false }
    BackHandler(enabled = desk.recorder != null) { desk.finish(vm, keep = false) }
    val scrim by animateFloatAsState(if (desk.fanOpen) 1f else 0f, GlassMotion.settle(), label = "fan scrim")
    if (scrim > 0.01f) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = scrim }
                .background(Color.Black.copy(alpha = if (c.dark) 0.45f else 0.24f))
                .clickable(remember { MutableInteractionSource() }, null) { desk.fanOpen = false },
        )
    }
    if (desk.fanOpen) Fan(vm, desk, onBrowse)

    Box(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
            .glass(Capsule, c.capsule, blur = 10.dp)
            .animateContentSize(GlassMotion.morph())
            .padding(6.dp),
    ) {
        AnimatedContent(
            desk.recorder,
            transitionSpec = { (fadeIn(GlassMotion.settle()) + scaleIn(GlassMotion.pop(), 0.8f)) togetherWith (fadeOut(GlassMotion.fold()) + scaleOut(GlassMotion.fold(), 0.8f)) },
            label = "dock",
        ) { rec ->
            if (rec == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlusButton(desk.fanOpen) { desk.fanOpen = !desk.fanOpen }
                    GlassCircle(
                        onClick = { desk.startRecording(vm) }, size = 54.dp, surface = Color.White.copy(alpha = 0.16f),
                        label = stringResource(R.string.create_voice),
                    ) { GlassIcon(LucideIcons.Mic, null, Modifier.size(22.dp), tint = Color.White) }
                }
            } else {
                Recording(rec, onCancel = { desk.finish(vm, keep = false) }, onStop = { desk.finish(vm, keep = true) })
            }
        }
    }
}

/** The white + (it turns to an × while the fan is open). */
@Composable
private fun PlusButton(open: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, GlassMotion.morph(), label = "plus")
    val label = stringResource(R.string.dock_create)
    Box(
        Modifier.size(58.dp)
            .pressable(onClick, pressedScale = 0.86f)
            .graphicsLayer { shadowElevation = 8.dp.toPx(); shape = CircleShape; clip = true }
            .background(Color.White)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { PlusGlyph(Color(0xFF1C2433), Modifier.size(22.dp).graphicsLayer { rotationZ = turn }) }
}

/** A way to make something, in the fan. */
private class FanItem(val icon: ImageVector, val label: String, val accent: Color, val go: () -> Unit)

/** The fan: the ways to make something, rising from the + one after another. */
@Composable
private fun BoxScope.Fan(vm: AppViewModel, desk: DeskState, onBrowse: () -> Unit) {
    val c = clear
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
    Column(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { i, it ->
            // the nearest (last) first, as if they spill out of the +
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(28L * (items.size - 1 - i))
                p.animateTo(1f, GlassMotion.pop())
            }
            Row(
                Modifier
                    .graphicsLayer {
                        translationY = (1f - p.value) * (40 + 14 * (items.size - i)).dp.toPx()
                        val s = 0.5f + 0.5f * p.value
                        scaleX = s
                        scaleY = s
                        alpha = p.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .pressable(it.go, pressedScale = 0.92f)
                    .glass(Capsule, c.frost, blur = 14.dp)
                    .height(54.dp)
                    .padding(start = 8.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(it.accent), contentAlignment = Alignment.Center) {
                    Icon(it.icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(it.label, style = ClearType.BodyStrong, color = c.ink)
            }
        }
    }
}

/** The capsule as a recorder: cancel, a pulsing dot with the time, the live waveform, stop. */
@Composable
private fun Recording(rec: VoiceRecorder, onCancel: () -> Unit, onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCircle(onClick = onCancel, size = 50.dp, surface = Color.White.copy(alpha = 0.14f), label = stringResource(R.string.common_cancel)) {
            GlassIcon(LucideIcons.X, null, Modifier.size(20.dp), tint = Color.White)
        }
        Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Candy.Tomato))
        Text(clock(rec.elapsedMs), style = ClearType.Number.copy(fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White)
        LiveWave(rec.levels, Modifier.width(112.dp).height(34.dp))
        Box(
            Modifier.size(54.dp).pressable(onStop, pressedScale = 0.86f).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(18.dp).clip(com.kyant.shapes.RoundedRectangle(5.dp)).background(Candy.Tomato)) }
    }
}

/** The last moments' loudness, newest on the right. */
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

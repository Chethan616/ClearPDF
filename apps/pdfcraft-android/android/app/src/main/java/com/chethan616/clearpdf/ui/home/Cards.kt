package com.chethan616.clearpdf.ui.home

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.CheckItem
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.desk.PaperThumb
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.CardTone
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.Tone
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The corner every desk card shares: big and continuous, like the mock's. */
val CardShape = RoundedRectangle(30.dp)

/** A desk card's paint: its tone's gradient, a soft light in one corner, a lit rim. */
@Composable
fun Modifier.cardPaint(tone: CardTone, glow: Offset = Offset(0.85f, 0.05f)): Modifier {
    val dark = clear.dark
    return clip(CardShape)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(tone.top, tone.bottom)))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.32f), Color.Transparent),
                    Offset(glow.x * size.width, glow.y * size.height), size.maxDimension * 0.75f,
                ),
                radius = size.maxDimension * 0.75f, center = Offset(glow.x * size.width, glow.y * size.height),
            )
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.6f), Color.White.copy(alpha = 0.05f))), CardShape)
}

/** Cards rise into place one after another when the desk first shows. */
@Composable
fun Modifier.riseIn(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(8))
        p.animateTo(1f, GlassMotion.rise())
    }
    return graphicsLayer {
        translationY = (1f - p.value) * 46.dp.toPx()
        val s = 0.94f + 0.06f * p.value
        scaleX = s
        scaleY = s
        alpha = p.value.coerceIn(0f, 1f)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** "1 Jun 2026". */
fun shortDate(t: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))

// ---- To-do list ----------------------------------------------------------------------------------

/** A list: its name in dot-matrix type, then its first items as glass pills you can tick right here. */
@Composable
fun ListCard(note: Note, onOpen: () -> Unit, onLong: () -> Unit, onToggle: (CheckItem) -> Unit, modifier: Modifier = Modifier) {
    val tone = clear.tone(Tone.Sage)
    val shown = note.items.filter { it.text.isNotBlank() }.take(4)
    val more = note.items.count { it.text.isNotBlank() } - shown.size
    Column(
        modifier.pressable(onOpen, onLong, pressedScale = 0.96f).cardPaint(tone, Offset(0.15f, 0.0f)).padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
    ) {
        WordFitText(
            note.title.ifBlank { stringResource(R.string.notes_untitled_list) },
            ClearType.DotTitle.copy(color = tone.ink.copy(alpha = 0.92f), textAlign = TextAlign.Center),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { item -> CheckPill(item, tone) { onToggle(item) } }
        }
        if (more > 0) {
            Spacer(Modifier.height(8.dp))
            Text(pluralStringResource(R.plurals.notes_more_items, more, more), style = ClearType.Caption, color = tone.ink.copy(alpha = 0.6f), modifier = Modifier.padding(start = 10.dp))
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.notes_list_empty), style = ClearType.Label, color = tone.ink.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * [text] at [style]'s size, or smaller when its longest word wouldn't fit the width: a word is
 * never split across lines (lines break between words only).
 */
@Composable
fun WordFitText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, maxLines: Int = 2, minScale: Float = 0.5f) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val scale = remember(text, style, room) {
            val widest = text.split(' ', '\n').filter { it.isNotBlank() }
                .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            if (widest <= room || widest == 0) 1f else (room.toFloat() / widest * 0.98f).coerceAtLeast(minScale)
        }
        Text(
            text, style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A list item on a card: a glass capsule with a round tick; ticked, it darkens and the tick fills. */
@Composable
fun CheckPill(item: CheckItem, tone: CardTone, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val done = item.done
    val fill by animateColorAsState(if (done) tone.ink.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.30f), GlassMotion.settle(), label = "pill")
    val ink by animateColorAsState(if (done) Color.White else tone.ink, GlassMotion.settle(), label = "pill ink")
    val tick by animateFloatAsState(if (done) 1f else 0f, GlassMotion.pop(), label = "tick")
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .pressable({ haptics.performHapticFeedback(HapticFeedbackType.ToggleOn); onToggle() }, pressedScale = 0.94f)
            .clip(Capsule).background(fill)
            .border(1.dp, Color.White.copy(alpha = if (done) 0.12f else 0.55f), Capsule)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).border(1.6.dp, ink.copy(alpha = 0.75f * (1f - tick)), CircleShape))
            Box(
                Modifier.size(24.dp).graphicsLayer { scaleX = tick; scaleY = tick }.clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(LucideIcons.Check, null, tint = tone.ink, modifier = Modifier.size(14.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Text(item.text, style = ClearType.Body.copy(fontSize = 14.5.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Text note -----------------------------------------------------------------------------------

/** A note: frosted white paper, its title big and bold in the note's blue, the date in the corner. */
@Composable
fun NoteCard(note: Note, onOpen: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier) {
    val tone = clear.tone(Tone.Paper)
    val title = note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("# ").orEmpty() }
    val body = if (note.title.isBlank()) "" else note.body.lineSequence().filter { it.isNotBlank() }.joinToString(" ") { it.trim().removePrefix("# ").replace("**", "") }
    Column(
        modifier.heightIn(min = 156.dp).pressable(onOpen, onLong, pressedScale = 0.96f).cardPaint(tone, Offset(0.1f, 0.0f)).padding(18.dp),
    ) {
        WordFitText(title.ifBlank { stringResource(R.string.notes_untitled_note) }, ClearType.CardTitle.copy(color = tone.accent), maxLines = 4)
        if (body.isNotBlank() && title.length < 40) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = ClearType.Label, color = tone.ink.copy(alpha = 0.62f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        note.images.firstOrNull()?.let { name ->
            Spacer(Modifier.height(12.dp))
            NoteImage(name, Modifier.fillMaxWidth().height(86.dp).clip(RoundedRectangle(16.dp)))
        }
        Spacer(Modifier.height(12.dp).weight(1f, fill = false))
        Text(shortDate(note.updated), style = ClearType.Caption.copy(fontSize = 10.5.sp), color = tone.ink.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.End))
    }
}

/** A note's picture, decoded small off the main thread. */
@Composable
fun NoteImage(name: String, modifier: Modifier, maxPx: Int = 720) {
    val vm = LocalHomeVm.current
    var img by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name) { img = withContext(Dispatchers.IO) { decodeScaled(vm?.notes?.file(name), maxPx) } }
    Box(modifier.background(Color.White.copy(alpha = 0.2f))) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** A picture at no more than [maxPx] on its long side. */
fun decodeScaled(file: File?, maxPx: Int): ImageBitmap? {
    if (file == null || !file.isFile) return null
    return runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }.getOrNull()
}

/** The view model, for cards deep in the grid that load pictures. */
val LocalHomeVm = androidx.compose.runtime.staticCompositionLocalOf<AppViewModel?> { null }

// ---- Voice note ----------------------------------------------------------------------------------

/** A voice memo: lavender, a play button, its waveform in white (the played part brighter). */
@Composable
fun VoiceCard(note: Note, playing: Boolean, progress: Float, onPlay: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier) {
    val tone = clear.tone(Tone.Lavender)
    Box(
        modifier.height(96.dp).pressable(onPlay, onLong, pressedScale = 0.95f).cardPaint(tone, Offset(0.5f, 0.0f)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(if (playing) LucideIcons.Pause else LucideIcons.Play, null, tint = Color.White, modifier = Modifier.size(20.dp).align(Alignment.TopStart))
        Waveform(note.wave, progress, Modifier.align(Alignment.Center).padding(horizontal = 18.dp).fillMaxWidth().height(52.dp))
        Text(
            if (note.title.isNotBlank()) note.title else com.chethan616.clearpdf.ui.viewer.clock(note.durationMs),
            style = ClearType.Caption.copy(fontSize = 10.5.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Mirrored rounded bars, as the mock's waveform: [levels] 0..1, the first [progress] of them lit. */
@Composable
fun Waveform(levels: List<Float>, progress: Float, modifier: Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val bars = levels.ifEmpty { List(24) { 0.3f } }
        val gap = 3.dp.toPx()
        val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1.5f)
        // the middle bars swell a little more, like the mock's
        bars.forEachIndexed { i, v ->
            val bell = 0.55f + 0.45f * kotlin.math.sin(Math.PI.toFloat() * (i + 0.5f) / bars.size)
            val h = (size.height * v * bell).coerceAtLeast(w)
            val x = i * (w + gap)
            val lit = (i + 0.5f) / bars.size <= progress
            drawRoundRect(
                color.copy(alpha = if (progress <= 0f || lit) 1f else 0.5f),
                topLeft = Offset(x, (size.height - h) / 2f), size = Size(w, h), cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

// ---- Collections ---------------------------------------------------------------------------------

/** A collection ("Files", "Pdf"): its word set big in serif, and two papers fanned in the corner. */
@Composable
fun CollectionCard(word: String, tone: CardTone, onOpen: () -> Unit, modifier: Modifier = Modifier, height: Dp = 150.dp, count: Int? = null) {
    Box(modifier.height(height).pressable(onOpen, pressedScale = 0.95f).cardPaint(tone, Offset(0.2f, 1.0f))) {
        Papers(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp))
        Text(
            word, style = ClearType.SerifWord, color = tone.accent, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp),
        )
        if (count != null && count > 0) {
            Text(
                count.toString(), style = ClearType.Label, color = tone.ink.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
        }
    }
}

/** Two sheets of paper with grey lines, one turned each way. */
@Composable
private fun Papers(modifier: Modifier) {
    Box(modifier.size(84.dp, 64.dp)) {
        Paper(Modifier.align(Alignment.CenterStart).rotate(-10f))
        Paper(Modifier.align(Alignment.CenterEnd).offset(y = 4.dp).rotate(7f))
    }
}

@Composable
private fun Paper(modifier: Modifier) {
    Column(
        modifier.size(46.dp, 56.dp)
            .graphicsLayer { shadowElevation = 6.dp.toPx(); shape = RoundedRectangle(8.dp); clip = true }
            .background(Color.White).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(5) { k -> Box(Modifier.fillMaxWidth(if (k == 4) 0.6f else 1f).height(2.5.dp).clip(Capsule).background(Color(0xFF17141F).copy(alpha = 0.16f))) }
    }
}

// ---- Stacks of papers or pictures ------------------------------------------------------------------

/**
 * A stack: the newest document's first page (or a picture) filling the card, two tinted cards
 * peeking out behind it at angles, a title over the top, and glass chips along the bottom.
 */
@Composable
fun StackCard(
    title: String,
    subtitle: String?,
    behind: List<CardTone>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 214.dp,
    onLong: (() -> Unit)? = null,
    chips: @Composable BoxScope.() -> Unit = {},
    picture: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.height(height).pressable(onOpen, onLong, pressedScale = 0.95f)) {
        behind.forEachIndexed { i, t ->
            Box(
                Modifier.fillMaxSize().padding(vertical = 6.dp)
                    .graphicsLayer {
                        rotationZ = if (i == 0) 5f else -4f
                        translationX = (if (i == 0) 10 else -6).dp.toPx()
                    }
                    .clip(CardShape).background(Brush.verticalGradient(listOf(t.top, t.bottom))),
            )
        }
        Box(Modifier.fillMaxSize().clip(CardShape).background(Color.White)) {
            picture()
            // a shade over the top so the title reads on a white page or a bright photo
            Box(Modifier.fillMaxWidth().height(86.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent))))
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)) {
                Text(title, style = ClearType.Title.copy(fontSize = 17.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = ClearType.Caption.copy(fontSize = 10.5.sp), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            chips()
            Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.35f), CardShape))
        }
    }
}

/** A frosted chip on a picture: "+5", or an icon and a word. */
@Composable
fun PhotoChip(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 0.9f) else Modifier)
            .height(34.dp).clip(Capsule).background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), Capsule)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = ClearType.Label, color = Color.White, maxLines = 1)
    }
}

/** A document's first page filling a card. */
@Composable
fun BoxScope.PageFill(vm: AppViewModel, path: String?) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        PaperThumb(vm, path, maxWidth, maxHeight, corner = 0.dp)
    }
}

/**
 * The documents read lately, as the mock's stacked card: the newest three first pages fanned on a
 * sky card (the newest in front, opening it), "Recent" and when, and "+N" / PDFs chips for the rest.
 */
@Composable
fun RecentCard(vm: AppViewModel, rows: List<FileRow>, onAll: () -> Unit, modifier: Modifier = Modifier) {
    val tone = clear.tone(Tone.Sky)
    val top = rows.first()
    Box(modifier.height(226.dp).pressable(top.open, { vm.fileSheet = top to true }, pressedScale = 0.95f)) {
        // a blush card peeking out behind, turned
        Box(Modifier.fillMaxSize().padding(vertical = 8.dp).graphicsLayer { rotationZ = 5f; translationX = 8.dp.toPx() }.clip(CardShape).background(Brush.verticalGradient(listOf(clear.tone(Tone.Blush).top, clear.tone(Tone.Blush).bottom))))
        Box(Modifier.fillMaxSize().cardPaint(tone, Offset(0.9f, 0.0f))) {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp)) {
                Text(stringResource(R.string.desk_recent), style = ClearType.Title.copy(fontSize = 17.sp), color = tone.ink, maxLines = 1)
                Text(whenLabel(top.date), style = ClearType.Caption.copy(fontSize = 10.5.sp), color = tone.ink.copy(alpha = 0.6f), maxLines = 1)
            }
            // up to three first pages, fanned; the newest on top
            val shown = rows.take(3)
            Box(Modifier.align(Alignment.Center).padding(top = 18.dp).size(140.dp, 132.dp)) {
                shown.indices.reversed().forEach { i ->
                    val r = shown[i]
                    val tilt = listOf(-4f, 7f, -12f)[i]
                    val dx = listOf(0, 26, -22)[i]
                    PaperThumb(
                        vm, r.thumbPath, 84.dp, 110.dp, tilt = tilt, corner = 7.dp,
                        modifier = Modifier.align(Alignment.Center).offset(x = dx.dp, y = (i * 4).dp),
                    )
                }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.size > 1) PhotoChip("+${rows.size - 1}", onClick = onAll)
                PhotoChip(stringResource(R.string.desk_chip_pdfs), LucideIcons.FileText, onClick = onAll)
            }
        }
    }
}

// ---- A single document ---------------------------------------------------------------------------

/** One PDF on the desk: its first page tilted on a tinted card, its name under it. */
@Composable
fun DocCard(vm: AppViewModel, r: FileRow, tone: CardTone, tilt: Float, onLong: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.pressable(r.open, onLong, pressedScale = 0.95f).cardPaint(tone, Offset(0.8f, 0.0f)).padding(14.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            PaperThumb(vm, r.thumbPath, 104.dp, 136.dp, tilt = tilt, corner = 8.dp, modifier = Modifier.graphicsLayer { shadowElevation = 10.dp.toPx(); shape = RoundedRectangle(8.dp) })
            if (r.starred) {
                Box(Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(LucideIcons.Star, null, tint = com.chethan616.clearpdf.ui.theme.Candy.Lemon, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(displayName(r.name), style = ClearType.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp), color = tone.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(whenLabel(r.date), style = ClearType.Caption, color = tone.ink.copy(alpha = 0.6f), maxLines = 1)
    }
}

// ---- Bits ----------------------------------------------------------------------------------------

/** The mock's menu glyph: four dots in a square. */
@Composable
fun FourDots(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val r = 2.4.dp.toPx()
        val d = 5.2.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        for (dx in listOf(-d, d)) for (dy in listOf(-d, d)) drawCircle(color, r, c + Offset(dx, dy))
    }
}

/** A plus drawn with round ends (the create button's). */
@Composable
fun PlusGlyph(color: Color, modifier: Modifier = Modifier, stroke: Dp = 2.6.dp) {
    Canvas(modifier) {
        val s = stroke.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val h = size.minDimension / 2f
        drawLine(color, Offset(c.x - h, c.y), Offset(c.x + h, c.y), s, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - h), Offset(c.x, c.y + h), s, StrokeCap.Round)
    }
}

/** A tick drawn as a path (for places that animate it). */
fun tickPath(size: Size): Path = Path().apply {
    moveTo(size.width * 0.22f, size.height * 0.52f)
    lineTo(size.width * 0.42f, size.height * 0.72f)
    lineTo(size.width * 0.78f, size.height * 0.3f)
}


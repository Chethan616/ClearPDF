package com.chethan616.clearpdf.ui.viewer

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.ReadingBlock
import com.chethan616.clearpdf.core.TextRange
import com.chethan616.clearpdf.engine.Engine
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.text
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.PdfSheet
import com.chethan616.clearpdf.ui.pdfColors
import com.chethan616.clearpdf.ui.glass.GlassCard
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassOverlay
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassStyle
import com.chethan616.clearpdf.ui.glass.InkOnLight
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.LocalGlassOverlayHost
import com.chethan616.clearpdf.ui.glass.LocalScreenInk
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.clear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Read Aloud: the document read out by the phone's own text-to-speech voice, paragraph by
 * paragraph from [startPage] on. The paragraph being read and its current word are marked on the
 * page, which scrolls to follow them; back and forward move about 15 seconds of reading.
 */
@Stable
class ReadAloud(
    context: Context,
    private val engine: Engine,
    private val doc: OpenDocument,
    private val scope: CoroutineScope,
    startPage: Int,
    private val onEnd: () -> Unit,
    /** Where this document's reading position is kept between sessions. */
    private val key: String = "",
    /** Start inside [startPage] at this glyph (the selection's first word), not its top. */
    private val startGlyph: UInt? = null,
    /** Pick up a stopped reading: paragraph and character within [startPage]. */
    private val resumeAt: Pair<Int, Int>? = null,
) {
    var page by mutableIntStateOf(startPage)
        private set

    /** The document's title, for the reading notification. */
    val title: String get() = doc.title
    var playing by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(prefs(context).getFloat("speed", 1f))
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    /** The language the voice reads in. */
    var language by mutableStateOf<Locale>(Locale.getDefault())
        private set

    /** The languages the phone's voice can read, by name. */
    fun languages(): List<Locale> = runCatching { tts.availableLanguages.orEmpty().sortedBy { it.getDisplayName(Locale.getDefault()) } }.getOrDefault(emptyList())

    /** Read in [locale] from now on (remembered), going on from where the voice is. */
    fun chooseLanguage(locale: Locale) {
        language = locale
        tts.language = locale
        prefs(prefsCtx).edit().putString("lang", locale.toLanguageTag()).apply()
        if (playing) speakCurrent()
    }

    /** The lines of the paragraph being read and of its current word (points, flattened x0, y0, x1, y1). */
    var blockRects by mutableStateOf<List<Float>>(emptyList())
        private set
    var wordRects by mutableStateOf<List<Float>>(emptyList())
        private set

    private val prefsCtx = context.applicationContext
    private var blocks: List<ReadingBlock> = emptyList()
    private var index by mutableIntStateOf(0)
    /** Where in the current paragraph the voice is (characters), and where its utterance began. */
    private var offset by mutableIntStateOf(0)
    private var base = 0
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        scope.launch {
            if (status != TextToSpeech.SUCCESS) {
                problem = text(R.string.read_aloud_no_voice)
                return@launch
            }
            ready = true
            // the language chosen before (Voice), else the phone's
            tts.language = prefs(prefsCtx).getString("lang", null)?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            language = tts.language ?: Locale.getDefault()
            tts.setSpeechRate(speed)
            loadPage(page)
            placeStart()
            play()
        }
    }

    /** The document's words (CJK characters count one each), once counted; 250 a page until then. */
    private var totalWords by mutableIntStateOf(0)

    init {
        // count the words of every page for the time estimate, as the reference does
        scope.launch {
            totalWords = withContext(Dispatchers.Default) {
                (0 until doc.pageCount).sumOf { p -> countWords(runCatching { engine.session.pagePlainText(doc.id, p.toUInt()) }.getOrDefault("")) }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                scope.launch {
                    if (utteranceId != utteranceFor()) return@launch
                    offset = base + start
                    markWord(base + start, base + end)
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (!playing || utteranceId != utteranceFor()) return@launch
                    index++
                    offset = 0
                    if (index < blocks.size) speakCurrent() else nextPage()
                }
            }
        })
    }

    private fun utteranceFor() = "p$page-$index-$base"

    private suspend fun loadPage(p: Int) {
        page = p
        index = 0
        offset = 0
        blocks = withContext(Dispatchers.Default) { runCatching { engine.session.readingBlocks(doc.id, p.toUInt()) }.getOrDefault(emptyList()) }
        if (doc.viewer.currentPage != p) doc.viewer.goToPage(p)
    }

    /** Move to the selection's word, or to where a stopped reading was, within the loaded page. */
    private fun placeStart() {
        val g = startGlyph
        if (g != null) {
            val i = blocks.indexOfFirst { g >= it.range.start && g < it.range.end }
            if (i >= 0) {
                index = i
                offset = blocks[i].glyphOfChar.indexOfFirst { it >= g }.coerceAtLeast(0)
            }
            return
        }
        resumeAt?.let { (i, o) ->
            if (i in blocks.indices) {
                index = i
                offset = o.coerceIn(0, blocks[i].text.length)
            }
        }
    }

    private suspend fun nextPage() {
        var p = page + 1
        while (p < doc.pageCount) {
            loadPage(p)
            if (blocks.isNotEmpty()) {
                speakCurrent()
                return
            }
            p++
        }
        playing = false
        finished = true
        blockRects = emptyList()
        wordRects = emptyList()
    }

    private fun speakCurrent() {
        val block = blocks.getOrNull(index) ?: return
        base = offset.coerceIn(0, block.text.length)
        scope.launch {
            val rects = withContext(Dispatchers.Default) { runCatching { engine.session.textRects(doc.id, block.range) }.getOrDefault(emptyList()) }
            blockRects = rects
            wordRects = emptyList()
            follow(rects)
        }
        tts.speak(block.text.substring(base), TextToSpeech.QUEUE_FLUSH, null, utteranceFor())
    }

    /** The word the voice is on, from characters [start] to [end] of the paragraph. */
    private suspend fun markWord(start: Int, end: Int) {
        val block = blocks.getOrNull(index) ?: return
        val first = block.glyphOfChar.getOrNull(start) ?: return
        val last = block.glyphOfChar.getOrNull((end - 1).coerceAtLeast(start)) ?: first
        val range = TextRange(page.toUInt(), first, last + 1u)
        wordRects = withContext(Dispatchers.Default) { runCatching { engine.session.textRects(doc.id, range) }.getOrDefault(emptyList()) }
    }

    /** Scroll so a paragraph that starts out of view sits just under the top bar. */
    private fun follow(rects: List<Float>) {
        if (rects.size < 4) return
        val v = doc.viewer
        val top = v.pageToScreen(page, rects[0], rects[1]).y
        if (top < v.padTop || top > v.viewH - v.padBottom - 120f) v.goToPage(page, (rects[1] - 24f).coerceAtLeast(0f))
    }

    fun play() {
        if (!ready) return
        if (finished) {
            finished = false
            scope.launch {
                loadPage(0)
                playing = true
                if (blocks.isEmpty()) nextPage() else speakCurrent()
            }
            return
        }
        playing = true
        if (blocks.isEmpty()) scope.launch { nextPage() } else speakCurrent()
    }

    fun pause() {
        playing = false
        tts.stop()
    }

    /**
     * About 15 seconds of reading back or forward: the words a voice says in that time at the
     * current speed (some 32 at 1.0x), counted across paragraphs and pages.
     */
    fun skip(forward: Boolean) {
        scope.launch {
            tts.stop()
            var words = (15f * WORDS_PER_MINUTE / 60f * speed).roundToInt().coerceAtLeast(1)
            if (forward) {
                while (words > 0) {
                    val text = blocks.getOrNull(index)?.text
                    if (text == null) {
                        if (page + 1 >= doc.pageCount) break
                        loadPage(page + 1)
                        continue
                    }
                    val next = text.indexOf(' ', offset + 1)
                    if (next < 0) {
                        index++
                        offset = 0
                        if (index >= blocks.size) {
                            if (page + 1 >= doc.pageCount) {
                                pause()
                                finished = true
                                return@launch
                            }
                            loadPage(page + 1)
                        }
                    } else {
                        offset = next + 1
                    }
                    words--
                }
            } else {
                while (words > 0) {
                    val text = blocks.getOrNull(index)?.text.orEmpty()
                    val prev = if (offset > 1) text.lastIndexOf(' ', offset - 2) else -1
                    if (prev < 0 && offset > 0) {
                        offset = 0
                    } else if (prev < 0) {
                        if (index > 0) {
                            index--
                            offset = blocks.getOrNull(index)?.text?.length ?: 0
                        } else if (page > 0) {
                            loadPage(page - 1)
                            index = (blocks.size - 1).coerceAtLeast(0)
                            offset = blocks.getOrNull(index)?.text?.length ?: 0
                        } else {
                            break
                        }
                    } else {
                        offset = prev + 1
                    }
                    words--
                }
            }
            if (playing) speakCurrent()
        }
    }

    fun setRate(r: Float) {
        speed = r
        tts.setSpeechRate(r)
        prefs(prefsCtx).edit().putFloat("speed", r).apply()
        if (playing) speakCurrent()
    }

    fun stop() {
        playing = false
        // keep the place for next time (the reference offers to resume); a finished reading starts
        // over at the beginning next time, as the reference's does
        if (key.isNotEmpty()) {
            val (p, i, o) = if (finished) START_OVER else Triple(page, index, offset)
            prefs(prefsCtx).edit().putString("pos:$key", "$p,$i,$o").apply()
        }
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        blockRects = emptyList()
        wordRects = emptyList()
        onEnd()
    }

    /** How far through the document reading is, 0..1. */
    val progress: Float
        get() {
            if (doc.pageCount == 0) return 0f
            val within = if (blocks.isEmpty()) 0f else (index + offset.toFloat() / (blocks.getOrNull(index)?.text?.length ?: 1).coerceAtLeast(1)) / blocks.size
            return ((page + within) / doc.pageCount).coerceIn(0f, 1f)
        }

    /** Reading time in all, estimated from the document's words (about 250 a page until they are counted). */
    val estimatedTotalMs: Long get() {
        val words = if (totalWords > 0) totalWords.toLong() else doc.pageCount * 250L
        return (words * 60_000L / (WORDS_PER_MINUTE * speed)).toLong()
    }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        /** The saved position of a reading that went to the end: the top of the first page. */
        val START_OVER = Triple(0, 0, 0)
        /** A voice's pace at 1.0x, for turning 15 seconds into words. */
        private const val WORDS_PER_MINUTE = 128f
        private fun prefs(c: Context) = c.getSharedPreferences("readAloud", Context.MODE_PRIVATE)

        /** Words in [text]: runs of letters or digits, with each CJK character a word of its own. */
        fun countWords(text: String): Int {
            var n = 0
            var inWord = false
            for (ch in text) {
                val cjk = Character.UnicodeScript.of(ch.code) in setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
                when {
                    cjk -> { n++; inWord = false }
                    ch.isLetterOrDigit() -> { if (!inWord) n++; inWord = true }
                    else -> inWord = false
                }
            }
            return n
        }

        /** Where reading of a document stopped: page, paragraph and character, if it did. */
        fun savedPosition(c: Context, key: String): Triple<Int, Int, Int>? {
            val v = prefs(c).getString("pos:$key", null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
            return if (v.size == 3) Triple(v[0], v[1], v[2]) else null
        }

        fun forgetPosition(c: Context, key: String) = prefs(c).edit().remove("pos:$key").apply()
    }
}

/** "1.0x", "1.25x": the speed as the player shows it. */
internal fun speedLabel(s: Float): String = if (s * 10f % 1f == 0f) "%.1fx".format(Locale.US, s) else "%.2fx".format(Locale.US, s)

/** The paragraph being read, pale yellow, and its current word, stronger, laid over the page. */
fun DrawScope.drawReadAloud(doc: OpenDocument, r: ReadAloud) {
    fun mark(rects: List<Float>, color: Color, pad: Float) {
        var i = 0
        while (i + 3 < rects.size) {
            val a = doc.viewer.pageToScreen(r.page, rects[i], rects[i + 1])
            val b = doc.viewer.pageToScreen(r.page, rects[i + 2], rects[i + 3])
            drawRoundRect(color, Offset(a.x - pad, a.y - pad), Size(b.x - a.x + 2 * pad, b.y - a.y + 2 * pad), CornerRadius(2f * density), blendMode = BlendMode.Multiply)
            i += 4
        }
    }
    mark(r.blockRects, Color(0xFFFFFFBB), 1f * density)
    mark(r.wordRects, Color(0xFFF8D904), 1.5f * density)
}

/**
 * The read-aloud player, docked over the page: the app's glass bar (as the viewer's toolbar), the
 * voice's sign in the accent beside the document's name and where it has got to, the speed, the
 * live wave of progress, and the transport (back 15, play or pause, forward 15, stop).
 */
@Composable
fun BoxScope.ReadAloudBar(doc: OpenDocument, r: ReadAloud) {
    var speeds by remember { mutableStateOf(false) }
    var speedAnchor by remember { mutableStateOf(Rect.Zero) }
    val ink = rememberGlassInk(InkOnLight)
    Box(
        Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(ink.modifier)
            .glassBar(RoundedRectangle(30.dp))
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        CompositionLocalProvider(LocalGlassInk provides ink.color) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Candy.Sky), contentAlignment = Alignment.Center) {
                        Icon(LucideIcons.AudioLines, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(doc.title, style = ClearType.BodyStrong, color = LocalGlassInk.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.common_tool_read_aloud) + "  ·  " + stringResource(R.string.viewer_page_of, r.page + 1, doc.info.pages.size),
                            style = ClearType.Caption, color = LocalGlassInk.current.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        GlassPill(
                            onClick = { speeds = true },
                            modifier = Modifier.onGloballyPositioned { speedAnchor = it.boundsInWindow() },
                            height = 36.dp,
                            horizontalPadding = 12.dp,
                            label = stringResource(R.string.read_aloud_speed),
                        ) {
                            Text(speedLabel(r.speed), style = ClearType.Label.copy(fontWeight = FontWeight.SemiBold), color = LocalGlassInk.current)
                        }
                        if (speeds) SpeedMenu(speedAnchor, r) { speeds = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                WavyProgressTrack(r.progress, Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(9.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                    SkipButton(back = true, size = 46.dp) { r.skip(false) }
                    GlassCircle(
                        onClick = { if (r.playing) r.pause() else r.play() },
                        size = 56.dp,
                        tint = Candy.Sky,
                        label = stringResource(if (r.playing) R.string.common_pause else if (r.finished) R.string.read_aloud_read_again else R.string.common_play),
                    ) { GlassIcon(if (r.playing) LucideIcons.Pause else LucideIcons.Play, null, Modifier.size(25.dp), tint = Color.White) }
                    SkipButton(back = false, size = 46.dp) { r.skip(true) }
                }
                r.problem?.let { Text(it, style = ClearType.Caption, color = Candy.Tomato, modifier = Modifier.padding(top = 6.dp)) }
            }
            // stop sits in the corner, away from play
            GlassCircle(onClick = r::stop, size = 34.dp, label = stringResource(R.string.read_aloud_stop_reading), modifier = Modifier.align(Alignment.BottomEnd)) {
                GlassIcon(LucideIcons.Square, null, Modifier.size(13.dp))
            }
        }
    }
}

/** Material 3 Expressive's animated wave, tinted to the player rather than the app theme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavyProgressTrack(progress: Float, modifier: Modifier, accent: Color = Candy.Sky) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(360), label = "readAloudProgress")
    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
        color = accent,
        trackColor = LocalGlassInk.current.copy(alpha = 0.18f),
    )
}

@Composable
private fun PlayerButton(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp, onClick: () -> Unit) {
    GlassCircle(onClick = onClick, size = size, label = description) { GlassIcon(icon, null, Modifier.size(iconSize)) }
}

/** Back or forward 15 seconds: a turning arrow with "15" in it. */
@Composable
private fun SkipButton(
    back: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    ink: Color = Color.Unspecified,
    surface: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val label = stringResource(if (back) R.string.read_aloud_back_15 else R.string.read_aloud_forward_15)
    GlassCircle(onClick = onClick, size = size, ink = ink, surface = surface, label = label) {
        Icon(if (back) LucideIcons.RotateCcw else LucideIcons.RotateCw, null, tint = LocalGlassInk.current, modifier = Modifier.size(iconSize))
        Text("15", color = LocalGlassInk.current, fontSize = (iconSize.value * 0.3f).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(y = 0.5.dp))
    }
}

/** Playback speeds use the shared anchored menu, not another theme-bound pop-up card. */
@Composable
private fun SpeedMenu(anchor: Rect, r: ReadAloud, onDismiss: () -> Unit) {
    GlassMenu(anchor, onDismiss, ReadAloud.SPEEDS.map { speed ->
        GlassMenuItem(speedLabel(speed), if (speed == r.speed) LucideIcons.Check else null) { r.setRate(speed) }
    })
}

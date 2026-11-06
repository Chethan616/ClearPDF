package com.chethan616.clearpdf.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.checkItem
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.Fluid
import androidx.compose.ui.draw.drawBehind
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.GlassChip
import com.chethan616.clearpdf.ui.glass.GlassHost
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.home.CardShape
import com.chethan616.clearpdf.ui.home.CheckPill
import com.chethan616.clearpdf.ui.home.CollectionCard
import com.chethan616.clearpdf.ui.home.Waveform
import com.chethan616.clearpdf.ui.home.cardPaint
import com.chethan616.clearpdf.ui.mascot.Mascot
import com.chethan616.clearpdf.ui.mascot.MascotMood
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.Tone
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.theme.glowing
import com.kyant.backdrop.throttleBackdrops
import com.chethan616.clearpdf.ui.glass.Capsule
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How many welcome pages there are. */
const val WelcomePages = 4

/** The strip under the status bar kept for Skip. */
private val SkipBand = 44.dp

/** The Continue button's colour on each page; between two pages it blends with the swipe. */
private val PageAccents = listOf(Candy.Grape, Candy.Bubblegum, Candy.Tangerine, Candy.Mint)

private fun accentAt(position: Float): Color {
    val i = position.toInt().coerceIn(0, PageAccents.size - 2)
    return androidx.compose.ui.graphics.lerp(PageAccents[i], PageAccents[i + 1], (position - i).coerceIn(0f, 1f))
}

/**
 * The welcome: four pages on the sky, each with its own playful stack of cards (the mock's tilted
 * cards), a big white title, and a glass pager with a jelly dot. Skip or Get started lands on the desk.
 */
@Composable
fun Onboarding(vm: AppViewModel, pager: PagerState = rememberPagerState { WelcomePages }) {
    val c = clear
    val scope = rememberCoroutineScope()
    val haptics = com.chethan616.clearpdf.ui.glass.LocalHaptics.current
    LaunchedEffect(pager) {
        var first = true
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect {
            if (!first) haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Tick)
            first = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            // how far this page is from the middle, read only while drawing: a swipe moves the
            // pages and their cards without recomposing a thing
            val offset = remember(pager, page) { { (pager.currentPage - page) + pager.currentPageOffsetFraction } }
            // a page that is out of sight and not about to be: its mascot stops redrawing
            val idle = remember(pager, page) { derivedStateOf { !pager.isScrollInProgress && pager.currentPage != page } }
            Column(
                Modifier.fillMaxSize().graphicsLayer {
                    // pages slide exactly with the finger (no drift of their own: that pulled the
                    // neighbours into view at rest); depth comes from the cards' own sway
                    alpha = 1f - offset().absoluteValue.coerceIn(0f, 1f) * 0.6f
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                // the band Skip lives in: nothing of a page is ever laid under it
                Spacer(Modifier.height(SkipBand))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (page) {
                        0 -> Hello(offset, idle)
                        1 -> OneDesk(offset)
                        2 -> PdfTools(offset)
                        else -> Promise(offset, idle)
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }

        // skip, top right, in its own band, a glass chip like Back and More; it fades out as the last
        // page arrives (read while drawing)
        GlassChip(
            onClick = { if (pager.currentPage < WelcomePages - 1) vm.finishOnboarding() },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 14.dp)
                .graphicsLayer {
                    val at = pager.currentPage + pager.currentPageOffsetFraction
                    alpha = (1f - (at - (WelcomePages - 2)).coerceIn(0f, 1f))
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            height = 36.dp,
            ink = c.hero,
            tap = Feel.Fall,
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            Text(stringResource(R.string.welcome_skip), style = ClearType.BodyStrong, color = c.hero)
        }

        // the dots and the button
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dots(pager)
            Spacer(Modifier.height(22.dp))
            val last = pager.currentPage == WelcomePages - 1
            // the button takes each page's colour and blends into the next with the swipe (read while drawing)
            val accent = remember(pager) { { accentAt(pager.currentPage + pager.currentPageOffsetFraction) } }
            GlassPill(
                onClick = { if (last) { haptics?.play(Feel.Confirm); vm.finishOnboarding() } else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = Fluid.push()) } },
                tintAt = accent,
                tap = if (last) null else Feel.Tap,
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (last) R.string.welcome_start else R.string.welcome_next), style = ClearType.BodyStrong.copy(fontSize = 17.sp), color = Color.White)
                Icon(LucideIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The page dots on a frosted capsule; the current one is a pill. Drawn straight from the pager's
 * position, so the pill stretches and slides with the finger (as iOS's page control) and nothing
 * is laid out or recomposed while it moves.
 */
@Composable
private fun Dots(pager: PagerState) {
    val c = clear
    val dot = 8.dp
    val pill = 26.dp
    val gap = 7.dp
    Box(
        // its own layer, so a swipe redraws just the dots, not everything they sit in
        Modifier.graphicsLayer {}.frost(Capsule).padding(horizontal = 12.dp, vertical = 9.dp)
            .size(width = pill + (dot + gap) * (WelcomePages - 1), height = dot)
            .drawBehind {
                val d = dot.toPx()
                val p = pill.toPx()
                val g = gap.toPx()
                val at = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (WelcomePages - 1).toFloat())
                var x = 0f
                for (i in 0 until WelcomePages) {
                    // how much of the pill this dot holds: 1 when it's the page, easing off as the next takes over
                    val share = (1f - kotlin.math.abs(at - i)).coerceIn(0f, 1f)
                    val w = d + (p - d) * share
                    drawRoundRect(
                        c.hero.copy(alpha = 0.45f + 0.55f * share),
                        topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w, d),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(d / 2f),
                    )
                    x += w + g
                }
            },
    )
}

/** A title block: white, glowing; [accent] words come out in a pastel gradient. */
@Composable
private fun Title(eyebrow: String, title: String, accent: String, body: String, top: Dp = 0.dp) {
    val c = clear
    // painted once into a texture: the glow is a 22 px blur, too dear to redo on every frame of a swipe
    // (the layer spans the full width, so the glow isn't cut at the text)
    Column(Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.padding(start = 26.dp, end = 26.dp, top = top)) {
        Text(eyebrow.uppercase(), style = ClearType.Eyebrow, color = c.hero.copy(alpha = 0.85f))
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                append(title)
                append(" ")
                withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFD6EE), Color(0xFFD7E4FF))))) { append(accent) }
            },
            style = ClearType.Hero.copy(fontSize = 44.sp, lineHeight = 46.sp, color = c.hero).glowing(c),
        )
        Spacer(Modifier.height(10.dp))
        Text(body, style = ClearType.Body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = if (c.dark) c.inkSoft else Color(0xFF1C2433).copy(alpha = 0.72f))
    }
}

/**
 * A card flying in: from [from], turned [tilt], gliding into place on the eased curve a little after
 * [delayMs], and swaying with the swipe ([swayWith], read only while drawing). [cache] is for cards
 * dear to redraw (live glass, shadows): they sit in a texture while they move. Every layer is a render
 * pass on the phone's GPU, so the cheap cards are left to draw directly.
 */
@Composable
private fun Modifier.flyIn(tilt: Float, from: Offset, delayMs: Long, swayWith: () -> Float, cache: Boolean = false): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // wait until the page is mostly on screen: the cards of the pages beside it stay put, so
        // each page's entrance plays when you arrive, not while it is still out of sight
        androidx.compose.runtime.snapshotFlow { kotlin.math.abs(swayWith()) < 0.6f }.first { it }
        kotlinx.coroutines.delay(delayMs)
        p.animateTo(1f, androidx.compose.animation.core.tween(620, easing = Fluid.Ease))
    }
    // true from the first frame of the glide to the last, and while a swipe holds the card between pages:
    // the glass inside it re-records its backdrop ten times a second then, not on every frame
    val moving = remember(swayWith) { derivedStateOf { p.isRunning || kotlin.math.abs(swayWith()).let { it > 0.001f && it < 1f } } }
    return graphicsLayer {
        val v = p.value
        // zero at rest and on the page beside it, peaking mid-swipe: the cards lean into the swipe
        // and settle back, never leaving a neighbour's cards bleeding in at rest
        val o = swayWith()
        val a = kotlin.math.abs(o)
        val sway = if (a < 1f) 2f * o * (1f - a) else 0f
        translationX = lerp(from.x, 0f, v) * density + sway * size.width * 0.18f
        translationY = lerp(from.y, 0f, v) * density
        rotationZ = lerp(tilt * 2f, tilt, v) + sway * 6f
        alpha = v.coerceIn(0f, 1f)
        // no offscreen pass for the fade: it multiplies into each draw instead
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }.then(
        if (!cache) Modifier else Modifier.graphicsLayer {
            // while the card is flying or swaying, its paint (gradients, clips, glowing text) sits in a
            // texture the outer layer only moves; at rest it is drawn live, so tilted text stays sharp
            val o = kotlin.math.abs(swayWith())
            compositingStrategy = if (p.isRunning || (o > 0.001f && o < 1f)) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        },
    ).throttleBackdrops { moving.value }
}

/** A mascot that stops redrawing while its page is out of sight ([idle] is read here, so only this recomposes). */
@Composable
private fun PagedMascot(type: String, mood: MascotMood, size: Dp, idle: State<Boolean>, modifier: Modifier = Modifier) {
    Mascot(type, mood, size, modifier, paused = idle.value)
}

/** Today, as the welcome's cards write it: made once, not on every frame. */
@Composable
private fun rememberDate(pattern: String): String = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date()) }

// ---- page 1: hello ----------------------------------------------------------------------------------

@Composable
private fun Hello(sway: () -> Float, idle: State<Boolean>) {
    val c = clear
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.artefact_logo), null, Modifier.padding(top = 4.dp).size(72.dp).flyIn(0f, Offset(0f, -40f), 0, sway))
        Spacer(Modifier.height(8.dp))
        // the big glowing titles painted once into a texture (the glow is a 22 px blur); the layer is
        // taller than its text, so the glow under the last line isn't cut
        Column(
            Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.welcome_hello_eyebrow).uppercase(), style = ClearType.Eyebrow, color = c.hero.copy(alpha = 0.9f))
            Text(stringResource(R.string.welcome_hello_title), style = ClearType.Hero.copy(fontSize = 58.sp, lineHeight = 60.sp, color = c.hero).glowing(c), textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.app_name),
                style = ClearType.Hero.copy(
                    fontSize = 58.sp, lineHeight = 60.sp,
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFCFEA), Color(0xFFCFE0FF))),
                ).glowing(c),
            )
        }
        // the mock's three tilted cards: a note, a picture with a mascot, today's date
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            NotePaper(Modifier.align(Alignment.TopStart).padding(start = 20.dp).width(186.dp).flyIn(-7f, Offset(-200f, 60f), 120, sway))
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 46.dp).size(148.dp, 184.dp).flyIn(6f, Offset(220f, 40f), 220, sway)
                    .clip(CardShape).background(Brush.linearGradient(listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC), Color(0xFFF9C6D3)))),
            ) {
                PagedMascot("flower", MascotMood.Happy, 92.dp, idle, Modifier.align(Alignment.Center))
                Text(stringResource(R.string.welcome_card_highlights), style = ClearType.Title.copy(fontSize = 15.sp), color = Color.White, modifier = Modifier.padding(14.dp))
            }
            DateCard(Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 168.dp).width(176.dp).flyIn(-2f, Offset(0f, 200f), 320, sway))
        }
    }
}

@Composable
private fun NotePaper(modifier: Modifier) {
    val tone = clear.tone(Tone.Paper)
    Column(modifier.cardPaint(tone).padding(16.dp)) {
        Text(stringResource(R.string.seed_note_title), style = ClearType.Title.copy(fontSize = 15.sp), color = tone.accent)
        Text(rememberDate("d MMM yyyy"), style = ClearType.Caption.copy(fontSize = 10.sp), color = tone.ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.welcome_note_body), style = ClearType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = tone.ink.copy(alpha = 0.75f), maxLines = 4)
    }
}

@Composable
private fun DateCard(modifier: Modifier) {
    val tone = clear.tone(Tone.Paper)
    Column(modifier.cardPaint(tone).padding(18.dp)) {
        Text(rememberDate("EEEE"), style = ClearType.Headline.copy(fontSize = 26.sp), color = tone.ink)
        Text(rememberDate("MMM d"), style = ClearType.Headline.copy(fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = tone.ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.welcome_card_overview), style = ClearType.Caption, color = tone.ink.copy(alpha = 0.5f))
    }
}

// ---- page 2: one desk --------------------------------------------------------------------------------

@Composable
private fun OneDesk(sway: () -> Float) {
    val c = clear
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_desk_eyebrow), stringResource(R.string.welcome_desk_title), stringResource(R.string.welcome_desk_accent), stringResource(R.string.welcome_desk_body), top = 4.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val tone = c.tone(Tone.Sage)
                // the pills are glass, so the card is painted on a sibling layer for them to refract
                GlassHost(
                    Modifier.fillMaxWidth().flyIn(-3f, Offset(-120f, 120f), 80, sway, cache = true),
                    paint = { Box(Modifier.fillMaxSize().cardPaint(tone)) },
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.seed_list_title), style = ClearType.DotTitle.copy(fontSize = 22.sp, lineHeight = 24.sp), color = tone.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CheckPill(checkItem(stringResource(R.string.seed_list_1), true), tone) {}
                            CheckPill(checkItem(stringResource(R.string.seed_list_2)), tone) {}
                        }
                    }
                }
                CollectionCard(stringResource(R.string.desk_word_files), c.tone(Tone.Butter), {}, Modifier.fillMaxWidth().flyIn(2f, Offset(-80f, 200f), 180, sway, cache = true), height = 104.dp)
            }
            Column(Modifier.weight(1f).padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the desk's own voice card, as the mock draws it
                com.chethan616.clearpdf.ui.home.VoiceCard(
                    remember { com.chethan616.clearpdf.notes.Note("welcome-voice", com.chethan616.clearpdf.notes.NoteKind.Voice) },
                    playing = false, progress = { 0f }, onPlay = {}, onLong = {},
                    modifier = Modifier.fillMaxWidth().flyIn(4f, Offset(120f, 100f), 140, sway, cache = true),
                )
                CollectionCard(stringResource(R.string.desk_word_pdf), c.tone(Tone.Orchid), {}, Modifier.fillMaxWidth().flyIn(-4f, Offset(140f, 220f), 240, sway, cache = true), height = 104.dp)
            }
        }
        }
    }
}

// ---- page 3: PDF tools -------------------------------------------------------------------------------

@Composable
private fun PdfTools(sway: () -> Float) {
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_tools_eyebrow), stringResource(R.string.welcome_tools_title), stringResource(R.string.welcome_tools_accent), stringResource(R.string.welcome_tools_body), top = 4.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp)) {
            // a page with a signature being written on it
            // the margin round it is where the shadow falls, so the texture it moves as holds the shadow whole
            Column(
                Modifier.align(Alignment.Center).flyIn(-4f, Offset(0f, 220f), 60, sway, cache = true).padding(28.dp).width(210.dp).height(280.dp)
                    .graphicsLayer { shadowElevation = 18.dp.toPx(); shape = RoundedRectangle(16.dp); clip = true }
                    .background(Color.White).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(Capsule).background(Color(0xFF17141F).copy(alpha = 0.75f)))
                Spacer(Modifier.height(4.dp))
                repeat(7) { k -> Box(Modifier.fillMaxWidth(if (k % 3 == 2) 0.7f else 1f).height(6.dp).clip(Capsule).background(Color(0xFF17141F).copy(alpha = 0.12f))) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth(0.75f).height(36.dp).clip(RoundedRectangle(8.dp)).background(Candy.Chip.copy(alpha = 0.6f)).border(1.dp, Candy.Lemon, RoundedRectangle(8.dp))) {
                    Text("A. Lovelace", style = ClearType.Title.copy(fontSize = 18.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Candy.InkBlue, modifier = Modifier.align(Alignment.Center))
                }
            }
            ToolBubble(LucideIcons.Signature, Candy.Grape, Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 30.dp).flyIn(-8f, Offset(-160f, 0f), 200, sway))
            ToolBubble(LucideIcons.Highlighter, Candy.Tangerine, Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 10.dp).flyIn(10f, Offset(160f, -30f), 260, sway))
            ToolBubble(LucideIcons.ScanText, Candy.Mint, Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 30.dp).flyIn(-6f, Offset(160f, 80f), 320, sway))
            ToolBubble(LucideIcons.FilePenLine, Candy.Bubblegum, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 60.dp).flyIn(7f, Offset(-160f, 60f), 380, sway))
        }
        }
    }
}

/**
 * A frosted fill with a lit rim: the look of glass without its blur pass, for decorations that
 * move with the pager (a real backdrop would re-blur on every frame of a swipe).
 */
@Composable
private fun Modifier.frost(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val c = clear
    return clip(shape)
        .background(if (c.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) 0.25f else 0.85f), Color.White.copy(alpha = 0.1f))), shape)
}

/** A tool as a frosted bubble with its candy glyph. */
@Composable
private fun ToolBubble(icon: ImageVector, accent: Color, modifier: Modifier) {
    Box(modifier.size(70.dp).frost(CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ---- page 4: the promise -----------------------------------------------------------------------------

@Composable
private fun Promise(sway: () -> Float, idle: State<Boolean>) {
    val c = clear
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_promise_eyebrow), stringResource(R.string.welcome_promise_title), stringResource(R.string.welcome_promise_accent), stringResource(R.string.welcome_promise_body), top = 4.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // the star alone: the lumpy cloud bot is gone from the welcome
            PagedMascot("star", MascotMood.Happy, 108.dp, idle, Modifier.flyIn(0f, Offset(0f, 160f), 100, sway))
            Spacer(Modifier.height(4.dp))
            listOf(
                Triple(LucideIcons.ShieldCheck, R.string.welcome_promise_1, Candy.Mint),
                Triple(LucideIcons.UserRound, R.string.welcome_promise_2, Candy.Sky),
                Triple(LucideIcons.Sparkles, R.string.welcome_promise_3, Candy.Bubblegum),
            ).forEachIndexed { i, (icon, label, accent) ->
                Row(
                    Modifier.fillMaxWidth().flyIn(0f, Offset(0f, 80f), 260L + 70 * i, sway).frost(RoundedRectangle(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(label), style = ClearType.BodyStrong, color = c.ink)
                }
            }
        }
        }
    }
}

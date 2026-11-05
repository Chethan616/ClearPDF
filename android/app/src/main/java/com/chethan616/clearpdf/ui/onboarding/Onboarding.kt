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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
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
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/** How many welcome pages there are. */
const val WelcomePages = 4

/**
 * The welcome: four pages on the sky, each with its own playful stack of cards (the mock's tilted
 * cards), a big white title, and a glass pager with a jelly dot. Skip or Get started lands on the desk.
 */
@Composable
fun Onboarding(vm: AppViewModel, pager: PagerState = rememberPagerState { WelcomePages }) {
    val c = clear
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Column(
                Modifier.fillMaxSize().graphicsLayer {
                    // the page behind drifts slower than the finger: depth
                    translationX = offset * size.width * 0.35f
                    alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.6f
                },
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (page) {
                        0 -> Hello(offset)
                        1 -> OneDesk(offset)
                        2 -> PdfTools(offset)
                        else -> Promise(offset)
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }

        // skip, top right
        if (pager.currentPage < WelcomePages - 1) {
            Text(
                stringResource(R.string.welcome_skip), style = ClearType.BodyStrong, color = c.hero,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 22.dp)
                    .pressable({ vm.finishOnboarding() }).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // the dots and the button
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dots(pager)
            Spacer(Modifier.height(22.dp))
            val last = pager.currentPage == WelcomePages - 1
            GlassPill(
                onClick = { if (last) vm.finishOnboarding() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = GlassMotion.morph()) } },
                tint = if (last) Candy.Sky else Color(0xFF1C2433),
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (last) R.string.welcome_start else R.string.welcome_next), style = ClearType.BodyStrong.copy(fontSize = 17.sp), color = Color.White)
                Icon(LucideIcons.ChevronRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The page dots on a glass capsule; the current one stretches into a pill. */
@Composable
private fun Dots(pager: PagerState) {
    val c = clear
    Row(
        Modifier.glass(Capsule, Color.White.copy(alpha = 0.18f), blur = 8.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(WelcomePages) { i ->
            val on = pager.currentPage == i
            val w by animateDpAsState(if (on) 26.dp else 8.dp, GlassMotion.morph(), label = "dot")
            val col by animateColorAsState(if (on) c.hero else c.hero.copy(alpha = 0.45f), GlassMotion.settle(), label = "dot colour")
            Box(Modifier.size(w, 8.dp).clip(Capsule).background(col))
        }
    }
}

/** A title block: white, glowing; [accent] words come out in a pastel gradient. */
@Composable
private fun Title(eyebrow: String, title: String, accent: String, body: String, top: Dp = 0.dp) {
    val c = clear
    Column(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = top)) {
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

/** A card flying in: from [from], turned [tilt], landing on a spring a little after [delayMs]. */
@Composable
private fun Modifier.flyIn(tilt: Float, from: Offset, delayMs: Long, swayWith: Float): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        p.animateTo(1f, GlassMotion.rise())
    }
    return graphicsLayer {
        val v = p.value
        translationX = lerp(from.x, 0f, v) * density + swayWith * size.width * 0.18f
        translationY = lerp(from.y, 0f, v) * density
        rotationZ = lerp(tilt * 3f, tilt, v) + swayWith * 6f
        alpha = v.coerceIn(0f, 1f)
    }
}

// ---- page 1: hello ----------------------------------------------------------------------------------

@Composable
private fun Hello(sway: Float) {
    val c = clear
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.artefact_logo), null, Modifier.padding(top = 28.dp).size(72.dp).flyIn(0f, Offset(0f, -40f), 0, sway))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.welcome_hello_eyebrow).uppercase(), style = ClearType.Eyebrow, color = c.hero.copy(alpha = 0.9f))
        Text(stringResource(R.string.welcome_hello_title), style = ClearType.Hero.copy(fontSize = 58.sp, lineHeight = 60.sp, color = c.hero).glowing(c), textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.app_name),
            style = ClearType.Hero.copy(
                fontSize = 58.sp, lineHeight = 60.sp,
                brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFCFEA), Color(0xFFCFE0FF))),
            ).glowing(c),
        )
        // the mock's three tilted cards: a note, a picture with a mascot, today's date
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 22.dp)) {
            NotePaper(Modifier.align(Alignment.TopStart).padding(start = 20.dp).width(186.dp).flyIn(-7f, Offset(-200f, 60f), 120, sway))
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 46.dp).size(148.dp, 184.dp).flyIn(6f, Offset(220f, 40f), 220, sway)
                    .clip(CardShape).background(Brush.linearGradient(listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC), Color(0xFFF9C6D3)))),
            ) {
                Mascot("flower", MascotMood.Happy, 92.dp, Modifier.align(Alignment.Center))
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
        Text(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()), style = ClearType.Caption.copy(fontSize = 10.sp), color = tone.ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.welcome_note_body), style = ClearType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = tone.ink.copy(alpha = 0.75f), maxLines = 4)
    }
}

@Composable
private fun DateCard(modifier: Modifier) {
    val tone = clear.tone(Tone.Paper)
    val now = Date()
    Column(modifier.cardPaint(tone).padding(18.dp)) {
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(now), style = ClearType.Headline.copy(fontSize = 26.sp), color = tone.ink)
        Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(now), style = ClearType.Headline.copy(fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = tone.ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.welcome_card_overview), style = ClearType.Caption, color = tone.ink.copy(alpha = 0.5f))
    }
}

// ---- page 2: one desk --------------------------------------------------------------------------------

@Composable
private fun OneDesk(sway: Float) {
    val c = clear
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_desk_eyebrow), stringResource(R.string.welcome_desk_title), stringResource(R.string.welcome_desk_accent), stringResource(R.string.welcome_desk_body), top = 24.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val tone = c.tone(Tone.Sage)
                Column(Modifier.fillMaxWidth().flyIn(-3f, Offset(-120f, 120f), 80, sway).cardPaint(tone).padding(12.dp)) {
                    Text(stringResource(R.string.seed_list_title), style = ClearType.DotTitle.copy(fontSize = 22.sp, lineHeight = 24.sp), color = tone.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CheckPill(checkItem(stringResource(R.string.seed_list_1), true), tone) {}
                        CheckPill(checkItem(stringResource(R.string.seed_list_2)), tone) {}
                    }
                }
                CollectionCard(stringResource(R.string.desk_word_files), c.tone(Tone.Butter), {}, Modifier.fillMaxWidth().flyIn(2f, Offset(-80f, 200f), 180, sway), height = 104.dp)
            }
            Column(Modifier.weight(1f).padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val lav = c.tone(Tone.Lavender)
                Box(Modifier.fillMaxWidth().height(76.dp).flyIn(4f, Offset(120f, 100f), 140, sway).cardPaint(lav).padding(14.dp)) {
                    Icon(LucideIcons.Play, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Waveform(List(22) { i -> 0.25f + 0.75f * ((i * 37 % 11) / 10f) }, 0f, Modifier.align(Alignment.Center).fillMaxWidth().height(40.dp).padding(horizontal = 14.dp))
                }
                CollectionCard(stringResource(R.string.desk_word_pdf), c.tone(Tone.Orchid), {}, Modifier.fillMaxWidth().flyIn(-4f, Offset(140f, 220f), 240, sway), height = 104.dp)
            }
        }
        }
    }
}

// ---- page 3: PDF tools -------------------------------------------------------------------------------

@Composable
private fun PdfTools(sway: Float) {
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_tools_eyebrow), stringResource(R.string.welcome_tools_title), stringResource(R.string.welcome_tools_accent), stringResource(R.string.welcome_tools_body), top = 24.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp)) {
            // a page with a signature being written on it
            Column(
                Modifier.align(Alignment.Center).width(210.dp).height(280.dp).flyIn(-4f, Offset(0f, 220f), 60, sway)
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

/** A tool as a glass bubble with its candy glyph. */
@Composable
private fun ToolBubble(icon: ImageVector, accent: Color, modifier: Modifier) {
    Box(modifier.size(70.dp).glass(CircleShape, clear.frost, blur = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ---- page 4: the promise -----------------------------------------------------------------------------

@Composable
private fun Promise(sway: Float) {
    val c = clear
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.welcome_promise_eyebrow), stringResource(R.string.welcome_promise_title), stringResource(R.string.welcome_promise_accent), stringResource(R.string.welcome_promise_body), top = 24.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp), verticalAlignment = Alignment.Bottom) {
                Mascot("blob", MascotMood.Happy, 78.dp, Modifier.flyIn(0f, Offset(-80f, 120f), 60, sway))
                Mascot("star", MascotMood.Happy, 100.dp, Modifier.offset(y = (-10).dp).flyIn(0f, Offset(0f, 160f), 140, sway))
                Mascot("cloud", MascotMood.Happy, 84.dp, Modifier.flyIn(0f, Offset(80f, 120f), 220, sway))
            }
            Spacer(Modifier.height(4.dp))
            listOf(
                Triple(LucideIcons.ShieldCheck, R.string.welcome_promise_1, Candy.Mint),
                Triple(LucideIcons.UserRound, R.string.welcome_promise_2, Candy.Sky),
                Triple(LucideIcons.Sparkles, R.string.welcome_promise_3, Candy.Bubblegum),
            ).forEachIndexed { i, (icon, label, accent) ->
                Row(
                    Modifier.fillMaxWidth().flyIn(0f, Offset(0f, 80f), 260L + 70 * i, sway).glass(RoundedRectangle(22.dp), c.frost, blur = 12.dp).padding(horizontal = 14.dp, vertical = 12.dp),
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

package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The live animation replays that sit on the onboarding pages.
 *
 * Every one of these is a **sibling** of a real component, never a modification of it. The app's
 * production animations are gesture-driven — `ShareMorphButton` morphs off a long-press,
 * `GlassSearchHeader` off a tap — and bolting a "demo mode" parameter onto a shipping component so
 * onboarding can puppet it is how those components rot. Instead each replay re-uses the *same*
 * [GlassMotion] spec and the same easing curves as the thing it is teaching, so the motion is
 * genuinely identical even though the code is separate. The one exception is [DemoToolsMenu], which
 * drives the real [GlassCapsuleMenu] unmodified because that component already exposes a
 * caller-owned `progress`.
 *
 * **Every replay takes `isActive`.** `HorizontalPager` composes the pages either side of the current
 * one, so without gating each loop on visibility all five demos run at once, off-screen, forever.
 */

/** Matches ToolsScreen's tile entrance — a real overshoot, on scale only. */
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** One file kind: the label page 3 prints on its chips, and the colour both pages carry. */
private data class DemoKind(val label: String, val tint: Color)

/**
 * The file-kind palette, shared by page 1's orbiting sheets and page 3's chip grid so the two pages
 * teach the same colour language rather than each inventing its own. Order follows `DocKind`'s
 * (`utils/DocKind.kt`), and the length is deliberately [OrbitPages] — page 1 maps one kind per sheet
 * by index, so the two must stay the same size.
 */
private val DemoKinds = listOf(
    DemoKind("PDF", LiquidGlassColors.Red),
    DemoKind("DOC", LiquidGlassColors.Blue),
    DemoKind("XLS", LiquidGlassColors.Green),
    DemoKind("PPT", LiquidGlassColors.Orange),
    DemoKind("IMG", LiquidGlassColors.Purple),
    DemoKind("TXT", LiquidGlassColors.Teal)
)

/**
 * Drives a demo loop: returns a 0..1 float that ramps over [riseMs], holds, resets, and repeats,
 * but only while [isActive].
 *
 * Uses a keyed `LaunchedEffect` + `animateFloatAsState` rather than `rememberInfiniteTransition`
 * because these replays need a **hold** at the end of each cycle — the user has to actually see the
 * finished state before it rewinds. An infinite transition with `RepeatMode.Restart` snaps back with
 * no dwell, which reads as a stutter rather than a demonstration.
 */
@Composable
private fun rememberDemoLoop(
    isActive: Boolean,
    riseMs: Int = 620,
    holdMs: Long = 1500L,
    gapMs: Long = 420L
): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isActive) {
        if (!isActive) { target = 0f; return@LaunchedEffect }
        // Small lead-in so the page has settled before the demo starts playing.
        delay(280)
        while (true) {
            target = 1f
            delay(riseMs + holdMs)
            target = 0f
            delay(gapMs)
        }
    }
    val v by animateFloatAsState(target, tween(riseMs, easing = FastOutSlowInEasing), label = "demoLoop")
    return v
}

// ── Page 1 · Welcome ────────────────────────────────────────────────────────────────────────────

/** Sheets in the orbit. Six reads as "a document's worth" without the centre turning to mush. */
private const val OrbitPages = 6

/** Radius of the loose orbit, in dp, before the vortex takes over. */
private const val OrbitRadius = 74f

private const val TwoPi = 6.2831855f
private const val Pi = 3.1415927f

/** Footprint of a single sheet, and therefore of the assembled book. */
private val SheetW = 122.dp
private val SheetH = 158.dp

/**
 * Smoothstep.
 *
 * Used in place of an `Easing` object because every phase below is a *slice* of one linear clock
 * that each sheet re-maps for itself — an `Easing` would have to be re-transformed per sheet anyway,
 * and as a plain function the whole trajectory stays one readable expression.
 */
private fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** How far the assembled book has taken over from the last sheet to land. */
private fun bookIn(p: Float): Float = ((p - 0.82f) / 0.15f).coerceIn(0f, 1f)

/**
 * Loose pages orbiting the centre, drawn into a vortex, folding as they fall, and settling into a
 * book.
 *
 * **The spiral is emergent, not scripted.** There is not a single control point or keyframed path
 * here: each sheet's radius collapses while its angular speed *climbs* (`pull²`), and those two
 * together are what bends the trajectory inward. A constant sweep against a shrinking radius reads
 * as a straight run at the centre; accelerating the sweep is what makes it curve. It also means the
 * paths never cross the same way twice as the stagger shifts them, which is why the motion reads as
 * organic rather than as six objects on rails.
 *
 * **Each sheet runs the same shape on its own delayed clock.** Without the per-index `head` offset
 * all six arrive as one clump; with it the deck assembles a sheet at a time, and `depth` doubles as
 * both the landing order and the fan offset in the settled stack, so the first to arrive ends up
 * furthest back.
 *
 * The fold is `rotationY`, not a `scaleX` squeeze — the near edge has to actually foreshorten or it
 * reads as a card being squashed rather than paper being creased. `cameraDistance` is pulled in
 * tight to exaggerate that at this size.
 *
 * **Only the book is glass.** Six orbiting glass surfaces would mean six blur-and-lens passes per
 * frame; the flying sheets are flat fills moved by `graphicsLayer`, which costs nothing, and the one
 * glass surface arrives at the end, stationary, cross-faded over the top sheet it replaces.
 */
@Composable
fun DemoDocumentOpen(isActive: Boolean, backdrop: Backdrop, glass: Color, ink: Color) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    // Hand-rolled rather than [rememberDemoLoop]: this demo needs a LINEAR clock, because every
    // curve above is carved out of it by hand and a second easing on top would double-ease them.
    //
    // It plays ONCE and holds on the finished book — no rewind loop. Looping it would play the
    // assembly in reverse (the book exploding back into an orbit), and the ask is a single, settled
    // demonstration. It re-arms only when the page is left and re-entered, which this effect's
    // `isActive` key already gives for free.
    LaunchedEffect(isActive) {
        if (!isActive) { fade.snapTo(0f); progress.snapTo(0f); return@LaunchedEffect }
        delay(240)
        fade.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        progress.animateTo(1f, tween(2200, easing = LinearEasing))
    }

    // The assembled book stays neutral glass so the CONTENT can carry the colour: each of its lines
    // is tinted by one of the six formats that flew in, in the same order they landed, so the book
    // visibly reads as "made of" the PDF/DOC/XLS/PPT/IMG/TXT sheets that assembled it rather than
    // collapsing to a single red.
    val bookGlass = glass

    Box(
        Modifier
            .size(224.dp)
            .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center
    ) {
        repeat(OrbitPages) { i ->
            val depth = (OrbitPages - 1 - i).toFloat()
            val base = (i.toFloat() / OrbitPages) * TwoPi
            // Alternating fold direction, so the deck does not crease as one slab.
            val dir = if (i % 2 == 0) 1f else -1f

            OrbitSheet(
                // One kind per sheet, in `DemoKinds` order — so the six formats the app opens fly in
                // as themselves and assemble into the one reader, and page 3's chip grid later
                // repeats the same colours for the same formats.
                tint = DemoKinds[i].tint,
                modifier = Modifier.graphicsLayer {
                    val p = progress.value
                    val head = i * 0.045f
                    val local = ((p - head) / (1f - head)).coerceIn(0f, 1f)
                    // Orbit through the opening stretch, in-fall through the middle, settled after.
                    val pull = smooth(((local - 0.18f) / 0.58f).coerceIn(0f, 1f))

                    val angle = base + (0.40f * local + 1.15f * pull * pull) * TwoPi
                    val radius = OrbitRadius * (1f - pull) * density

                    translationX = cos(angle) * radius + (depth * 2.4f * density) * pull
                    translationY = sin(angle) * radius + (-depth * 2.8f * density) * pull

                    // Peaks mid-flight and relaxes: creased on the way in, flat on landing.
                    val fold = sin(pull * Pi)
                    rotationY = 64f * fold * dir
                    rotationZ = lerp(sin(angle) * 17f, depth * 1.7f, pull) + 22f * fold * dir
                    cameraDistance = 14f

                    val s = lerp(0.32f, 1f, pull)
                    scaleX = s
                    scaleY = s
                    // The top sheet is the one the glass book replaces, so it hands over rather
                    // than sitting underneath and darkening it.
                    alpha = smooth(local * 4f) *
                        if (i == OrbitPages - 1) 1f - bookIn(p) else 1f
                }
            )
        }

        Box(
            Modifier
                .size(width = SheetW, height = SheetH)
                .graphicsLayer {
                    val b = bookIn(progress.value)
                    alpha = b
                    val s = lerp(0.97f, 1f, b)
                    scaleX = s
                    scaleY = s
                }
                // withShadow = false: this book is the one surface on page 1, and its drop shadow used
                // to snap in the instant the assembled book reached full opacity. Page 1 shows no
                // shadow at all now — see [viewerGlass]'s `withShadow`.
                .viewerGlass(backdrop, bookGlass, shape = { RoundedRectangle(16f.dp) }, withShadow = false)
                .padding(horizontal = 15.dp, vertical = 17.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Content arrives once the book has: the last beat, so the sequence ends on something
            // legible rather than on the stack merely stopping. Each line wears the colour of the
            // format sheet at its position — in [DemoKinds] order — so the finished book carries all
            // six colours that formed it, laid down one after another.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val widths = listOf(0.85f, 1f, 0.72f, 0.94f, 0.6f, 0.88f, 0.45f)
                widths.forEachIndexed { idx, w ->
                    val lineTint = DemoKinds[idx % DemoKinds.size].tint
                    Box(
                        Modifier
                            .fillMaxWidth(w)
                            .height(if (idx == 0) 8.dp else 5.dp)
                            .graphicsLayer {
                                val head = idx * 0.10f
                                val t = ((bookIn(progress.value) - head) / (1f - head))
                                    .coerceIn(0f, 1f)
                                alpha = t
                                // Grows from the left edge, like a line of text being laid down.
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                scaleX = t
                            }
                            .clip(RoundedCornerShape(3.dp))
                            // Was 0.85 for the first line and a flat 0.62 for every line after it —
                            // a steep drop that read as "the first sheet landed bright, the rest are
                            // washed out," which is exactly backwards for a book meant to show all
                            // six formats having equally assembled it. Close enough now that the
                            // first line still reads as a heading without the rest going dim.
                            .background(lineTint.copy(if (idx == 0) 0.90f else 0.80f))
                    )
                }
            }
        }
    }
}

/**
 * One flying page. Flat by design — see [DemoDocumentOpen]; the bars are what make a rounded
 * rectangle read as a document once it is down at a third of its size.
 *
 * [tint] is the sheet's file kind, from [DemoKinds]. Card and bars share it so a sheet reads as one
 * coloured object rather than a coloured card with grey lines on it. Both alphas sit a little above
 * the neutral ones they replace (0.16 / 0.24): a hue needs more weight than grey to register at the
 * 0.32 flight scale, under a 64° fold.
 */
@Composable
private fun OrbitSheet(tint: Color, modifier: Modifier) {
    Box(
        modifier
            .size(width = SheetW, height = SheetH)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(0.20f))
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf(0.80f, 1f, 0.66f, 0.92f, 0.50f).forEach { w ->
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tint.copy(0.34f))
                )
            }
        }
    }
}

// ── Page 3 · Open anything ──────────────────────────────────────────────────────────────────────

/**
 * "Open anything" as ONE liquid-glass document that becomes each format in turn, rather than a static
 * list of rows. A single [viewerGlass] card holds a big format icon, the format name and its
 * extensions, and a few tinted "content" lines — and it cross-fades from PDF → Word → Excel → PPT →
 * Image on a loop, the whole card re-tinting toward each format's colour as it goes. It reads as the
 * same reader opening anything you hand it, which is the promise the page is making, and it does it
 * with the app's real glass instead of a generic chip grid.
 *
 * One glass surface (not five), so it costs a single blur+lens pass; only the flat inner content
 * cross-fades. Names reuse the localized recents category strings; extensions are literal.
 */
@Composable
fun DemoFileKinds(isActive: Boolean, backdrop: Backdrop, glass: Color, ink: Color, inkSoft: Color) {
    data class KindCard(val icon: ImageVector, val tint: Color, val name: String, val ext: String)
    val cards = listOf(
        KindCard(Icons.Rounded.PictureAsPdf, LiquidGlassColors.Red,    stringResource(R.string.recents_filter_pdf),   ".pdf"),
        KindCard(Icons.Rounded.Description,  LiquidGlassColors.Blue,   stringResource(R.string.recents_filter_word),  ".doc · .docx"),
        KindCard(Icons.Rounded.GridOn,       LiquidGlassColors.Green,  stringResource(R.string.recents_filter_excel), ".xls · .xlsx"),
        KindCard(Icons.Rounded.Slideshow,    LiquidGlassColors.Orange, stringResource(R.string.recents_filter_ppt),   ".ppt · .pptx"),
        KindCard(Icons.Rounded.Image,        LiquidGlassColors.Purple, stringResource(R.string.recents_filter_image), ".jpg · .png")
    )

    // Advance the format on a dwell loop; reset to the first when the page is left so it always opens
    // on PDF. Gated on `isActive` like every other demo — see the file header.
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) {
        if (!isActive) { index = 0; return@LaunchedEffect }
        delay(500)
        while (true) {
            delay(1500)
            index = (index + 1) % cards.size
        }
    }
    val current = cards[index]

    // The card tint eases toward the active format's colour, so the glass itself carries the change
    // even between the content cross-fades. A soft blend, so it stays glass rather than a colour swatch.
    val cardTint by animateColorAsState(
        lerpColor(glass, current.tint, 0.16f),
        tween(560, easing = FastOutSlowInEasing),
        label = "fileCardTint"
    )

    Column(
        Modifier
            .width(212.dp)
            .viewerGlass(backdrop, cardTint, shape = { RoundedRectangle(26f.dp) })
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The whole inner content cross-fades as one, so the icon, the name and the tinted lines all
        // turn over together. The glass card behind is outside the fade and never re-measures.
        Crossfade(
            targetState = index,
            animationSpec = tween(460, easing = FastOutSlowInEasing),
            label = "fileKind"
        ) { i ->
            val k = cards[i]
            // The badge pops on every turnover instead of just cross-fading flat — a small overshoot
            // that lands, so "now it's a Word doc" reads as an arrival rather than a still image being
            // swapped out from under itself.
            val badgePop = remember(i) { Animatable(0.72f) }
            LaunchedEffect(i) { badgePop.animateTo(1f, GlassMotion.pop()) }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .graphicsLayer { scaleX = badgePop.value; scaleY = badgePop.value }
                        .clip(RoundedCornerShape(22.dp))
                        .background(k.tint.copy(0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(k.icon, null, Modifier.size(40.dp), Color.White)
                }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    BasicText(
                        k.name,
                        style = TextStyle(ink, 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    BasicText(
                        k.ext,
                        style = TextStyle(inkSoft, 13.sp, textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // A few "content" lines in the format's own colour, so the card reads as a document of
                // that kind rather than just an icon.
                Column(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(1f, 0.82f, 0.92f, 0.6f).forEach { w ->
                        Box(
                            Modifier
                                .fillMaxWidth(w)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(k.tint.copy(0.45f))
                        )
                    }
                }
            }
        }

        // Which format is cycling now, out of how many — without this the card just silently
        // relabels itself every 1.5s with no sense of a sequence being shown. Same pill-dot language
        // as the pager's own [PageDots], tinted to the active format instead of a flat ink colour.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEachIndexed { i, k ->
                val active = i == index
                val w by animateFloatAsState(if (active) 16f else 5f, GlassMotion.settle(), label = "kindDotW$i")
                val dotColor by animateColorAsState(
                    if (active) k.tint.copy(0.9f) else ink.copy(0.22f),
                    tween(320),
                    label = "kindDotColor$i"
                )
                Box(
                    Modifier
                        .width(w.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor)
                )
            }
        }
    }
}

// ── Page 5 · The feature slots ──────────────────────────────────────────────────────────────────

/**
 * Height of a feature row's leading demo slot.
 *
 * Kept modest on purpose. The features page is the tallest in the flow, and the demo area is a
 * `weight(1f)` box between fixed top and bottom padding — on a 640 dp-tall phone that box is only
 * around 280 dp, and the panel has to fit inside it or it overlaps the copy underneath.
 */
private val SlotHeight = 50.dp

/**
 * A pen stroke drawing itself across a mini page, then a highlighter sweeping under it.
 *
 * The reveal is a [PathMeasure] trim rather than an animated point list: sampling `getSegment` gives
 * a stroke that grows at a constant *arc-length* rate, so it draws at an even speed through the
 * curve. Interpolating the control points instead makes it visibly rush the straight sections.
 */
@Composable
fun DemoAnnotate(isActive: Boolean, ink: Color) {
    val p = rememberDemoLoop(isActive, riseMs = 900, holdMs = 1100L)

    Box(
        Modifier
            .size(width = 72.dp, height = SlotHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(ink.copy(0.10f))
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height

            // Two text lines for the stroke to sit against.
            listOf(0.24f, 0.52f).forEach { fy ->
                drawLine(
                    ink.copy(0.30f),
                    Offset(0f, h * fy),
                    Offset(w * 0.92f, h * fy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // The highlighter sweeps first, under the signature — same order a user works in.
            val hl = (p / 0.45f).coerceIn(0f, 1f)
            if (hl > 0f) {
                drawLine(
                    Color(0xFFFFD60A).copy(0.55f),
                    Offset(0f, h * 0.24f),
                    Offset(w * 0.92f * hl, h * 0.24f),
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }

            // A signature-ish squiggle, trimmed by arc length.
            val sig = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (sig > 0f) {
                val path = Path().apply {
                    moveTo(w * 0.05f, h * 0.86f)
                    cubicTo(w * 0.20f, h * 0.60f, w * 0.28f, h * 1.02f, w * 0.44f, h * 0.80f)
                    cubicTo(w * 0.58f, h * 0.62f, w * 0.62f, h * 0.98f, w * 0.78f, h * 0.76f)
                    cubicTo(w * 0.86f, h * 0.66f, w * 0.90f, h * 0.80f, w * 0.95f, h * 0.72f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * sig, drawn, true)
                drawPath(
                    drawn,
                    Color(0xFF0A84FF),
                    style = Stroke(width = 2.4f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * The search capsule unfurling out of its circle, replaying `GlassSearchHeader`'s open.
 *
 * Keeps that component's split exactly: the **width** is critically damped ([GlassMotion.settle]'s
 * shape, via a linear-ish ease here since it drives real layout) while the glyph's quarter-turn and
 * the field's scale ride an overshooting curve. A bouncing width would re-measure every frame and
 * re-run the blur underneath.
 */
@Composable
fun DemoSearch(isActive: Boolean, backdrop: Backdrop, glass: Color, ink: Color) {
    val p = rememberDemoLoop(isActive, riseMs = 620, holdMs = 1400L)
    val eased = FastOutSlowInEasing.transform(p)
    val overshoot = EaseOutBack.transform(p)

    Box(Modifier.size(width = 72.dp, height = SlotHeight), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                // Layout width: eased, never overshooting. A capsule that sprang past its target
                // would drive this negative-adjacent and re-measure the glass every frame.
                .width(lerp(34f, 72f, eased).dp)
                .height(34.dp)
                .viewerGlass(backdrop, glass, shape = { Capsule }),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                Icons.Rounded.Search,
                null,
                Modifier
                    .padding(start = 9.dp)
                    .size(16.dp)
                    // Draw-time property, so this is where the bounce is allowed to live.
                    .graphicsLayer { rotationZ = lerp(0f, 90f, overshoot) },
                ink.copy(0.75f)
            )
            // A caret and a "typed" bar appear once the capsule has room for them.
            Box(
                Modifier
                    .padding(start = 32.dp)
                    .graphicsLayer {
                        alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        scaleX = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    }
                    .width(26.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ink.copy(0.45f))
            )
        }
    }
}

/**
 * The real [GlassCapsuleMenu], driven by a looping progress float.
 *
 * No replay needed and no new drawing code: the component already takes `progress` from its caller
 * precisely so whatever opened it can own the clock, which makes it the one production animation
 * onboarding can show *literally* rather than by imitation.
 *
 * The rise is deliberately slower than the real menu's. Its `StaggerFraction` of 0.07 is a fraction
 * of `progress`, not a duration, so at the gesture's ~560 ms the three circles land about 39 ms
 * apart — legible when your own finger caused it, indistinguishable from simultaneous when you are
 * watching. Stretching the clock is the only knob that widens the cascade without touching the
 * component, and it costs nothing here because nothing is waiting on this animation.
 *
 * Must be given its natural width. See `OnboardingScreen.FeatureRows` — this is a `Row` of 40 dp
 * circles, and a constraint narrower than its content silently drops actions rather than shrinking.
 */
@Composable
fun DemoToolsMenu(
    isActive: Boolean,
    backdrop: Backdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    glass: Color,
    actions: List<GlassMenuAction>
) {
    val p = rememberDemoLoop(isActive, riseMs = 820, holdMs = 1600L)
    GlassCapsuleMenu(
        actions = actions,
        backdrop = backdrop,
        uiSensor = uiSensor,
        progress = p,
        surfaceColor = glass
    )
}

// ── Page 5 · Ready ──────────────────────────────────────────────────────────────────────────────

/**
 * A glass disc springs in, then a checkmark draws itself inside it, ringed by the six format dots
 * that opened the tour on page 1.
 *
 * A checkmark alone in a circle is the one generic "success" cliché every templated app onboarding
 * reaches for, with nothing about it specific to this app. The ring ties it back to the actual
 * promise being confirmed — closing the loop the flow opened with the same six colours from
 * [DemoKinds]/page 1's flying sheets: "that's all six formats, sorted." It reuses the format's own
 * language rather than inventing a new decorative element.
 *
 * Same [PathMeasure] trim as [DemoAnnotate] — the tick is *drawn*, not faded in, which is what makes
 * it read as a confirmation rather than an icon appearing. Plays once per visit rather than looping:
 * a checkmark that keeps un-checking itself undermines the "you're all set" it is illustrating.
 */
@Composable
fun DemoReady(isActive: Boolean, backdrop: Backdrop, glass: Color) {
    var play by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) { if (isActive) { delay(220); play = 1 } else play = 0 }

    val disc by animateFloatAsState(
        if (play == 1) 1f else 0f,
        GlassMotion.morph(),
        label = "readyDisc"
    )
    val tick by animateFloatAsState(
        if (play == 1) 1f else 0f,
        tween(520, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "readyTick"
    )
    // A settle, not the disc's own bouncy morph() — six dots overshooting independently around a
    // ring reads as jitter, not as a landing.
    val ring by animateFloatAsState(
        if (play == 1) 1f else 0f,
        tween(640, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "readyRing"
    )

    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        // Six small dots settling into a ring around the disc — each on its own staggered slice of
        // `ring`, same head-offset idiom page 1 uses for its book's lines, so an early dot is
        // fully landed while a later one is still arriving.
        DemoKinds.forEachIndexed { i, kind ->
            val angle = -Pi / 2f + (i.toFloat() / DemoKinds.size) * TwoPi
            val head = i * 0.08f
            val t = ((ring - head) / (1f - head)).coerceIn(0f, 1f)
            val eased = smooth(t)
            Box(
                Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        // Drifts in from a touch further out, so it reads as arriving into the ring
                        // rather than just fading up in place.
                        val radius = (72f - 10f * (1f - eased)) * density
                        translationX = cos(angle) * radius
                        translationY = sin(angle) * radius
                        val s = lerp(0.4f, 1f, eased)
                        scaleX = s; scaleY = s
                        alpha = eased
                    }
                    .clip(CircleShape)
                    .background(kind.tint)
            )
        }
        // A soft accent halo behind the glass, flat, so it can pulse without costing a re-blur.
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer {
                    val s = lerp(0.7f, 1f, disc.coerceIn(0f, 1f))
                    scaleX = s; scaleY = s
                    alpha = 0.22f * disc.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(LiquidGlassColors.Green)
        )
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    val s = lerp(0.82f, 1f, disc)
                    scaleX = s; scaleY = s
                    alpha = disc.coerceIn(0f, 1f)
                }
                .viewerGlass(backdrop, glass, shape = { Capsule }),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
                if (tick <= 0f) return@Canvas
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.16f, h * 0.53f)
                    lineTo(w * 0.41f, h * 0.76f)
                    lineTo(w * 0.85f, h * 0.26f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * tick, drawn, true)
                drawPath(
                    drawn,
                    LiquidGlassColors.Green,
                    style = Stroke(width = 4.5f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

package com.chethan616.clearpdf.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.PdfField
import com.chethan616.clearpdf.ui.PdfcraftTheme
import com.chethan616.clearpdf.ui.ThemeChoice
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassLayer
import com.chethan616.clearpdf.ui.glass.GlassLoader
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassOverlayHost
import com.chethan616.clearpdf.ui.glass.GlassOverlayLayer
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassToast
import com.chethan616.clearpdf.ui.glass.LocalGlassOverlayHost
import com.chethan616.clearpdf.ui.glass.ProvideGlassHaptics
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.mascot.Mascot
import com.chethan616.clearpdf.ui.mascot.MascotMood
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearTheme
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.CanvasLook
import com.chethan616.clearpdf.ui.viewer.PageCanvas
import com.chethan616.clearpdf.ui.viewer.formatSize
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much of the screen's height the card takes: the caller stays visible above it. */
private const val CardHeight = 0.84f

/**
 * Quick Preview's window content: a dim (and on newer phones a blur) over the app that opened the
 * file, and the card rising from the bottom on a spring. The card's header is glass over the
 * pages: Close, the name, Expand, Share and More. Drag the header down to close, up to expand.
 */
@Composable
internal fun QuickPreviewScreen(
    activity: QuickPreviewActivity,
    state: PreviewState,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: ((String) -> Unit) -> Unit,
    onPassword: (String) -> Unit,
    setBlur: (Int) -> Unit,
) {
    val overlays = remember { GlassOverlayHost() }
    ClearTheme(ThemeChoice.System) {
        PdfcraftTheme(ThemeChoice.System) {
            ProvideGlassHaptics {
                CompositionLocalProvider(LocalGlassOverlayHost provides overlays) {
                    Box(Modifier.fillMaxSize()) {
                        Preview(state, onClose, onExpand, onShare, onOpenWith, onSaveCopy, onPassword, setBlur)
                        GlassOverlayLayer(overlays)
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(
    state: PreviewState,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: ((String) -> Unit) -> Unit,
    onPassword: (String) -> Unit,
    setBlur: (Int) -> Unit,
) {
    val c = clear
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { appear.animateTo(1f, GlassMotion.morph()) }
    // the blur behind follows the card in and out
    LaunchedEffect(Unit) {
        snapshotFlow { (appear.value.coerceIn(0f, 1f) * (1f - grow.value) * 36f).toInt() }.collect { setBlur(it) }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            appear.animateTo(0f, GlassMotion.fold())
            onClose()
        }
    }

    fun expand() {
        if (leaving) return
        leaving = true
        scope.launch {
            // layout grows, so no bounce (a spring past 1 would make the margins negative)
            grow.animateTo(1f, GlassMotion.settle())
            onExpand()
        }
    }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        // the dim over the caller: tap it to close
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) * (1f - grow.value * 0.3f) }
                .background(Color.Black.copy(alpha = if (c.dark) 0.5f else 0.32f))
                .clickable(remember { MutableInteractionSource() }, null) { close() },
        )
        val margin = with(density) { 10.dp.toPx() }
        val cardH = fullH * CardHeight - navPx
        val g = grow.value.coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val p = appear.value
                    // rising in, following a drag, growing to the whole screen for Expand
                    translationY = (1f - p) * cardH * 0.9f + drag.coerceAtLeast(0f) * (1f - g)
                    val pull = (-drag).coerceAtLeast(0f) / fullH
                    scaleX = lerp(0.94f, 1f, p.coerceIn(0f, 1.2f)) + pull * 0.06f
                    scaleY = scaleX
                }
                .padding(
                    start = with(density) { lerp(margin, 0f, g).toDp() },
                    end = with(density) { lerp(margin, 0f, g).toDp() },
                    bottom = with(density) { lerp(margin + navPx, 0f, g).toDp() },
                )
                .fillMaxWidth()
                .height(with(density) { lerp(cardH, fullH, g).toDp() })
                .clip(RoundedRectangle(lerp(34f, 0f, g).dp))
                .background(if (c.dark) Color(0xFF111A1F) else Color(0xFFE6ECF4))
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            val headerH = 112.dp
            GlassLayer(
                Modifier.fillMaxSize(),
                content = {
                    AnimatedContent(
                        state, contentKey = { it::class }, label = "preview",
                        transitionSpec = { fadeIn(GlassMotion.settle()) togetherWith fadeOut(GlassMotion.fold()) },
                    ) { s ->
                        when (s) {
                            is PreviewState.Ready -> Pages(s, headerH)
                            is PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GlassLoader(size = 72.dp) }
                            is PreviewState.Password -> PasswordCard(s, onPassword)
                            is PreviewState.Failed -> FailedCard(s.message) { close() }
                        }
                    }
                },
            ) {
                Header(
                    state, Modifier.align(Alignment.TopCenter).padding(top = with(density) { lerp(0f, statusPx, g).toDp() }),
                    onDrag = { d -> drag += d },
                    onDragEnd = {
                        val threshold = with(density) { 110.dp.toPx() }
                        when {
                            drag > threshold -> close()
                            drag < -threshold * 0.8f -> expand()
                            else -> scope.launch { Animatable(drag).animateTo(0f, GlassMotion.pressOut()) { drag = value } }
                        }
                    },
                    onClose = ::close, onExpand = ::expand, onShare = onShare, onOpenWith = onOpenWith,
                    onSaveCopy = { onSaveCopy { toast = it } },
                )
                (state as? PreviewState.Ready)?.let { BottomBar(it, onShare, ::expand, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) }
                toast?.let { t -> GlassToast(t, null, {}, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 84.dp)) }
            }
        }
    }
}

/** The pages, scrolling under the glass header; pinch and double tap zoom as in the viewer. */
@Composable
private fun Pages(s: PreviewState.Ready, headerTop: androidx.compose.ui.unit.Dp) {
    val c = clear
    val density = LocalDensity.current
    LaunchedEffect(s) {
        with(density) { s.view.setPadding(headerTop.toPx(), 86.dp.toPx()) }
    }
    PageCanvas(
        state = s.view,
        images = s.images,
        generation = s.info.generation.toLong(),
        look = CanvasLook(background = if (c.dark) Color(0xFF111A1F) else Color(0xFFE6ECF4), pageShadow = Color(0x26000000)),
        gap = 10.dp,
        margin = 12.dp,
    )
}

/** The glass header: a handle, Close, the document's name and size, and More. Drag it to close or expand. */
@Composable
private fun Header(
    state: PreviewState,
    modifier: Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: () -> Unit,
) {
    val c = clear
    var menu by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val ready = state as? PreviewState.Ready
    Column(
        modifier.fillMaxWidth().pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { ch, d ->
                ch.consume()
                onDrag(d)
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp, 5.dp).clip(Capsule).background(c.ink.copy(alpha = 0.22f)))
        }
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().glass(Capsule, c.frost, blur = 14.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlassCircle(onClick = onClose, size = 44.dp, label = stringResource(R.string.preview_close)) { GlassIcon(LucideIcons.X, null, Modifier.size(20.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    ready?.name?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: stringResource(R.string.preview_title),
                    style = ClearType.BodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ready != null) {
                    Text(
                        listOf("PDF", androidx.compose.ui.res.pluralStringResource(R.plurals.preview_pages, ready.info.pages.size, ready.info.pages.size), formatSize(ready.size)).joinToString("  ·  "),
                        style = ClearType.Caption, color = c.inkSoft, maxLines = 1,
                    )
                }
            }
            if (ready != null) {
                GlassCircle(
                    onClick = { menu = true }, size = 44.dp, label = stringResource(R.string.common_more_options),
                    modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
                ) { GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(20.dp)) }
            }
        }
    }
    if (menu) {
        GlassMenu(
            anchor, { menu = false },
            listOf(
                GlassMenuItem(stringResource(R.string.preview_open_in_app), LucideIcons.Maximize2, onClick = onExpand),
                GlassMenuItem(stringResource(R.string.preview_save_downloads), LucideIcons.FileDown, onClick = onSaveCopy),
                GlassMenuItem(stringResource(R.string.home_open_with), LucideIcons.ExternalLink, onClick = onOpenWith),
            ),
        )
    }
}

/** The card's foot, on glass: Share, the page it's on ("3 / 12"), and Open (Expand) in the app's blue. */
@Composable
private fun BottomBar(s: PreviewState.Ready, onShare: () -> Unit, onExpand: () -> Unit, modifier: Modifier) {
    val c = clear
    Row(
        modifier.fillMaxWidth().glass(Capsule, c.frost, blur = 14.dp).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircle(onClick = onShare, size = 46.dp, label = stringResource(R.string.common_share)) { GlassIcon(LucideIcons.Share2, null, Modifier.size(20.dp)) }
        Text(
            "${s.view.currentPage + 1} / ${s.info.pages.size}", style = ClearType.Label, color = c.inkSoft,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        GlassPill(onClick = onExpand, tint = Candy.Sky, height = 46.dp, horizontalPadding = 18.dp, label = stringResource(R.string.preview_expand)) {
            Icon(LucideIcons.Maximize2, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Text(stringResource(R.string.preview_open), style = ClearType.BodyStrong, color = Color.White)
        }
    }
}

/** A locked document: its password, asked for right in the card. */
@Composable
private fun PasswordCard(s: PreviewState.Password, onPassword: (String) -> Unit) {
    val c = clear
    var pw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mascot("ghost", if (s.wrong) MascotMood.Sad else MascotMood.NeedsYou, 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.root_enter_password), style = ClearType.Headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(if (s.wrong) R.string.common_wrong_password else R.string.preview_locked_body), style = ClearType.Body, color = if (s.wrong) Candy.Tomato else c.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        PdfField(
            pw, { pw = it }, stringResource(R.string.common_password), password = true, error = s.wrong && pw.isEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (pw.isNotEmpty()) onPassword(pw) }),
        )
        Spacer(Modifier.height(14.dp))
        GlassPill(onClick = { if (pw.isNotEmpty()) onPassword(pw) }, tint = Candy.Sky, modifier = Modifier.fillMaxWidth()) {
            Icon(LucideIcons.LockOpen, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.preview_unlock), style = ClearType.BodyStrong, color = Color.White)
        }
    }
}

/** Something went wrong: a sad mascot, the reason, and Close. */
@Composable
private fun FailedCard(message: String, onClose: () -> Unit) {
    val c = clear
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mascot("puddle", MascotMood.Sad, 100.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.preview_failed_title), style = ClearType.Headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = ClearType.Body, color = c.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        GlassPill(onClick = onClose) { Text(stringResource(R.string.preview_close), style = ClearType.BodyStrong, color = c.ink) }
    }
}


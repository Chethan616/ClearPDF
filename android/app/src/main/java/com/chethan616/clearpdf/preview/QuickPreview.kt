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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.PdfField
import com.chethan616.clearpdf.ui.PdfcraftTheme
import com.chethan616.clearpdf.ui.ThemeChoice
import com.chethan616.clearpdf.ui.glass.GlassLayer
import com.chethan616.clearpdf.ui.glass.GlassLoader
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.Fluid
import com.chethan616.clearpdf.ui.glass.GlassOverlayHost
import com.chethan616.clearpdf.ui.glass.GlassOverlayLayer
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassToast
import com.chethan616.clearpdf.ui.glass.LocalGlassOverlayHost
import com.chethan616.clearpdf.ui.glass.ProvideGlassHaptics
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.mascot.Mascot
import com.chethan616.clearpdf.ui.mascot.MascotMood
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearTheme
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.CanvasLook
import com.chethan616.clearpdf.ui.viewer.PageCanvas
import com.chethan616.clearpdf.ui.viewer.formatSize
import com.chethan616.clearpdf.ui.glass.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much of the screen's height the card takes: the caller stays visible above it. */
private const val CardHeight = 0.84f

/**
 * Quick Preview's window content: a dim (and on newer phones a blur) over the app that opened the
 * file, and a card rising from the bottom on the eased curve. The card is as plain as it can be:
 * the pages, the name over them with More and Close, and one Open button. Drag the header down to
 * close, or up to open the file in Artefact. Floating glass (the More menu) lives inside the card's
 * own glass layer, so it refracts the pages.
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
    // the theme chosen in the app's Settings (the same "app" preference the app reads), not the system's
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = remember {
        runCatching {
            ThemeChoice.valueOf(context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE).getString("theme", "System") ?: "System")
        }.getOrDefault(ThemeChoice.System)
    }
    ClearTheme(theme) {
        PdfcraftTheme(theme) {
            ProvideGlassHaptics {
                CompositionLocalProvider(LocalGlassOverlayHost provides overlays) {
                    Preview(state, onClose, onExpand, onShare, onOpenWith, onSaveCopy, onPassword, setBlur)
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
    val overlays = LocalGlassOverlayHost.current
    val appear = remember { Animatable(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var leaving by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val paper = if (c.dark) Color(0xFF111A1F) else Color(0xFFE6ECF4)

    // the card slides up as an iOS sheet does: one eased curve, never past its place
    LaunchedEffect(Unit) { appear.animateTo(1f, Fluid.rise()) }
    // the blur behind follows the card in and out
    LaunchedEffect(Unit) {
        snapshotFlow { (appear.value.coerceIn(0f, 1f) * 36f).toInt() }.collect { setBlur(it) }
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
            appear.animateTo(0f, Fluid.dismiss())
            onClose()
        }
    }

    // straight into the app: the viewer's own window animation takes over from here, so the card
    // never goes full screen first
    fun open() {
        if (leaving) return
        leaving = true
        scope.launch { Animatable(drag).animateTo(0f, Fluid.follow()) { drag = value } }
        onExpand()
    }
    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat()
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        // the dim over the caller: tap it to close
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (c.dark) 0.5f else 0.32f))
                .clickable(remember { MutableInteractionSource() }, null) { close() },
        )
        val margin = with(density) { 10.dp.toPx() }
        val cardH = fullH * CardHeight - navPx
        // Apple's way: continuous corners, concentric with the screen's own (its corner radius less
        // the card's inset), the same on all four corners; 34 dp where the screen doesn't say
        val view = androidx.compose.ui.platform.LocalView.current
        val corner = remember(view, density) {
            val screen = if (android.os.Build.VERSION.SDK_INT >= 31) {
                view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
            } else 0
            with(density) { if (screen > 0) (screen.toDp() - 10.dp).coerceIn(24.dp, 48.dp) else 34.dp }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    // rising in from below the screen's edge, following a drag
                    translationY = (1f - appear.value) * (cardH + margin + navPx) + drag.coerceAtLeast(0f)
                    val pull = (-drag).coerceAtLeast(0f) / fullH
                    scaleX = 1f + pull * 0.04f
                    scaleY = scaleX
                }
                .padding(start = 10.dp, end = 10.dp, bottom = with(density) { (margin + navPx).toDp() })
                .fillMaxWidth()
                .height(with(density) { cardH.toDp() })
                .clip(com.chethan616.clearpdf.ui.glass.Squircle(corner))
                .background(paper)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            GlassLayer(
                Modifier.fillMaxSize(),
                content = {
                    AnimatedContent(
                        state, contentKey = { it::class }, label = "preview",
                        transitionSpec = { fadeIn(Fluid.fade()) togetherWith fadeOut(Fluid.fade()) },
                    ) { s ->
                        when (s) {
                            is PreviewState.Ready -> Pages(s, paper)
                            is PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GlassLoader(size = 72.dp) }
                            is PreviewState.Password -> PasswordCard(s, onPassword)
                            is PreviewState.Failed -> FailedCard(s.message) { close() }
                        }
                    }
                },
            ) {
                val haptics = com.chethan616.clearpdf.ui.glass.LocalHaptics.current
                val threshold = with(density) { 110.dp.toPx() }
                Header(
                    state, paper, Modifier.align(Alignment.TopCenter),
                    onDrag = { d ->
                        val before = drag > threshold || drag < -threshold * 0.8f
                        drag += d
                        // a tick where letting go would close (down) or open (up)
                        if (before != (drag > threshold || drag < -threshold * 0.8f)) haptics?.play(com.chethan616.clearpdf.ui.glass.Feel.Tick)
                    },
                    onDragEnd = {
                        when {
                            drag > threshold -> close()
                            drag < -threshold * 0.8f -> open()
                            else -> scope.launch { Animatable(drag).animateTo(0f, Fluid.follow()) { drag = value } }
                        }
                    },
                    onClose = ::close, onShare = onShare, onOpenWith = onOpenWith,
                    onSaveCopy = { onSaveCopy { toast = it } },
                )
                if (state is PreviewState.Ready) {
                    GlassPill(
                        onClick = ::open, tint = Candy.Sky, height = 50.dp, horizontalPadding = 30.dp,
                        label = stringResource(R.string.preview_expand),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    ) {
                        Text(stringResource(R.string.preview_open), style = ClearType.BodyStrong, color = Color.White)
                    }
                }
                toast?.let { t -> GlassToast(t, null, {}, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 84.dp)) }
                // floating glass (the More menu) is composed here, over the pages, so it refracts them
                overlays?.let { GlassOverlayLayer(it) }
            }
        }
    }
}

/** The header's height: the pages start below it and fade up into it as they scroll. */
private val HeaderHeight = 84.dp

/** The pages, scrolling under the header; pinch and double tap zoom as in the viewer. */
@Composable
private fun Pages(s: PreviewState.Ready, paper: Color) {
    val density = LocalDensity.current
    LaunchedEffect(s) {
        with(density) { s.view.setPadding(HeaderHeight.toPx(), 92.dp.toPx()) }
    }
    PageCanvas(
        state = s.view,
        images = s.images,
        generation = s.info.generation.toLong(),
        look = CanvasLook(background = paper, pageShadow = Color(0x26000000)),
        gap = 10.dp,
        margin = 12.dp,
    )
}

/**
 * The header, plain: a handle, the document's name and size, More and Close as quiet round buttons.
 * It sits on the card's own colour and fades into the pages below it. Drag it to close or to open.
 */
@Composable
private fun Header(
    state: PreviewState,
    paper: Color,
    modifier: Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onSaveCopy: () -> Unit,
) {
    val c = clear
    var menu by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val ready = state as? PreviewState.Ready
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to paper, 0.74f to paper, 1f to paper.copy(alpha = 0f)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { ch, d ->
                    ch.consume()
                    onDrag(d)
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(Capsule).background(c.ink.copy(alpha = 0.2f)))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ready?.name?.let(com.chethan616.clearpdf.engine.DocumentTypes::title) ?: stringResource(R.string.preview_title),
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
                QuietButton(
                    { menu = true }, stringResource(R.string.common_more_options),
                    Modifier.onGloballyPositioned { anchor = it.boundsInWindow() },
                ) { Icon(LucideIcons.Ellipsis, null, tint = c.ink, modifier = Modifier.size(20.dp)) }
            }
            QuietButton(onClose, stringResource(R.string.preview_close)) { Icon(LucideIcons.X, null, tint = c.ink, modifier = Modifier.size(18.dp)) }
        }
    }
    if (menu) {
        GlassMenu(
            anchor, { menu = false },
            listOf(
                GlassMenuItem(stringResource(R.string.common_share), LucideIcons.Share2, onClick = onShare),
                GlassMenuItem(stringResource(R.string.preview_save_downloads), LucideIcons.FileDown, onClick = onSaveCopy),
                GlassMenuItem(stringResource(R.string.home_open_with), LucideIcons.ExternalLink, onClick = onOpenWith),
            ),
        )
    }
}

/** A round button with no glass: a faint disc of ink and a glyph, for a plain surface. */
@Composable
private fun QuietButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(38.dp).pressable(onClick, pressedScale = 0.9f).clip(CircleShape).background(clear.ink.copy(alpha = 0.08f)).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
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


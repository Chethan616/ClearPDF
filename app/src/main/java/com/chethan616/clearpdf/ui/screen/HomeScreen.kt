package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.GlassCapsuleMenu
import com.chethan616.clearpdf.ui.components.GlassMenuAction
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.GlassSearchHeader
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.utils.DocKind
import com.chethan616.clearpdf.utils.docKindOf
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SourGummyFontFamily = FontFamily(
    Font(R.font.sour_gummy_regular, FontWeight.Normal),
    Font(R.font.sour_gummy_bold, FontWeight.Bold)
)

@Composable
fun HomeScreen(
    backdrop: LayerBackdrop,
    onNavigateToOpenPdf: () -> Unit,
    onNavigateToScan: () -> Unit,
    // The stored display name travels with the uri. Re-querying DISPLAY_NAME at tap time is a
    // guess that fails exactly when it matters: a lapsed permission or a provider that answers with
    // its own internal name sends a .docx down the plain-PDF route. Recents already knows what the
    // file is called, so routing should ask recents.
    onRecentFileSelected: (Uri, String) -> Unit
) {
    val homeRecentLimit = 5
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = LiquidGlassColors.text(isDarkMode)
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Blue
    val redAccent = LiquidGlassColors.Red
    val uiSensor = rememberUISensor()
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()

    var recents by remember { mutableStateOf(RecentFilesManager.getRecents(context)) }
    var showAllRecents by remember { mutableStateOf(false) }
    var recentQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedRecent by remember { mutableStateOf<com.chethan616.clearpdf.data.repository.RecentFile?>(null) }
    // The row currently playing its exit animation because "Remove" was tapped in the long-press
    // menu. Swipe-to-delete drives its own exit from inside the row; this lets the menu path run the
    // exact same fade + container spring instead of snapping the row out of existence.
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }
    // Root-space vertical center of the long-pressed row, so the popup can rise
    // from the item instead of floating dead-center.
    var selectedRecentAnchorY by remember { mutableStateOf(0f) }   // root-space TOP of the pressed row
    var selectedRecentRowHeight by remember { mutableStateOf(0f) }
    var recentPopupHeightPx by remember { mutableStateOf(0) }
    var infoRecent by remember { mutableStateOf<com.chethan616.clearpdf.data.repository.RecentFile?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val popupTransition = updateTransition(
        targetState = selectedRecent != null,
        label = "recentPopupTransition"
    )
    val morphProgress by popupTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.82f, stiffness = 220f) },
        label = "morphProgress"
    ) { if (it) 1f else 0f }
    // `morphProgress` alone now drives the menu: GlassCapsuleMenu derives each circle's stagger
    // from it, so the four per-button springs this used to run are gone.

    // Category filter for the recents list. Null = show everything.
    var recentFilter by remember { mutableStateOf<DocKind?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterAnchorY by remember { mutableStateOf(0f) }   // root-space BOTTOM of the filter glyph
    val filterTransition = updateTransition(filterMenuOpen, label = "recentsFilterMenu")
    val filterProgress by filterTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.82f, stiffness = 220f) },
        label = "filterProgress"
    ) { if (it) 1f else 0f }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recents = RecentFilesManager.getRecents(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val density = LocalDensity.current.density
    // One transition for the whole entrance. Previously six independent
    // animateFloatAsState calls ran on three frame clocks; this is one clock with a
    // per-section delay, so the sections can't drift apart on a busy frame.
    val entrance = updateTransition(isVisible, label = "homeEntrance")

    val query = recentQuery.trim()
    val byKind = recentFilter?.let { kind -> recents.filter { docKindOf(it.name) == kind } } ?: recents
    val filtered = if (query.isBlank()) byKind
                   else byKind.filter { it.name.contains(query, ignoreCase = true) }
    val searching = query.isNotBlank()
    val visibleRecents = if (showAllRecents || searching) filtered else filtered.take(homeRecentLimit)

    Box(Modifier.fillMaxSize()) {
        GlassScreenScaffold(
            backdrop = backdrop,
            contentHorizontalPadding = 16.dp,
            headerHorizontalPadding = 16.dp,
            header = { headerBackdrop ->
                // Pinned above the list: the header samples the content layer, so cards scroll
                // *under* it and its glass refracts them instead of only the wallpaper.
                GlassSearchHeader(
                    title = "ClearPDF",
                    backdrop = headerBackdrop,
                    uiSensor = uiSensor,
                    query = recentQuery,
                    onQueryChange = { recentQuery = it },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    searchHint = stringResource(R.string.recents_search_hint),
                    modifier = entrance.entranceModifier(0, density),
                    titleFontFamily = SourGummyFontFamily
                )
            }
        ) { contentPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome card. It collapses while searching so results own the fold.
            item(key = "hero") {
                AnimatedVisibility(
                    visible = !searchActive,
                    // Desync the fade from the height so the glass is invisible whenever its size is
                    // moving — an alpha-0 liquidGlassPanel draws nothing, so the collapse can't show
                    // its per-frame re-blur, and the recents panel's rise reads as a clean fade rather
                    // than a shimmer. Open the height first, then fade in; fade out fast, then finish
                    // shrinking unseen.
                    enter = expandVertically(tween(240)) + fadeIn(tween(200, delayMillis = 110)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(240))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(entrance.entranceModifier(1, density))
                            .liquidGlassPanel(backdrop, uiSensor)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Description, contentDescription = null,
                            tint = accent, modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        // The ON-DEVICE badge moved here from the header — the header is now the
                        // viewer's compact pill, which has no room for an action chip.
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = if (isLight) 0.12f else 0.18f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                stringResource(R.string.home_on_device),
                                style = TextStyle(accent, 10.sp, FontWeight.Bold)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        BasicText(
                            stringResource(R.string.home_tagline),
                            style = TextStyle(
                                color = text,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SourGummyFontFamily,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            stringResource(R.string.home_subtitle),
                            style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                        )
                        Spacer(Modifier.height(18.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidButton(
                                onClick = onNavigateToOpenPdf,
                                backdrop = backdrop,
                                tint = accent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp), Color.White)
                                    BasicText(stringResource(R.string.home_open_pdf), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                                }
                            }
                            LiquidButton(
                                onClick = onNavigateToScan,
                                backdrop = backdrop,
                                tint = LiquidGlassColors.Green,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Scanner, null, Modifier.size(18.dp), Color.White)
                                    BasicText(stringResource(R.string.home_scan), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }

            // Recent files. The rows stay inside a single glass panel — one refraction
            // pass for the whole list rather than one per row — and the list is capped at
            // MAX_RECENTS (20), so composing them together is cheap.
            item(key = "recents") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(entrance.entranceModifier(2, density))
                        .liquidGlassPanel(backdrop, uiSensor)
                        // The container's own minimise animation. `animateContentSize` sits INSIDE the
                        // glass (after `liquidGlassPanel`, which is a pure draw modifier that paints at
                        // whatever size it measures), so the glass tracks the animated height frame by
                        // frame — the whole panel springs shut when a row leaves, rather than the row
                        // collapsing on its own while the panel snaps. A lightly-underdamped spring
                        // gives the elastic "settle" bounce; because the size delta of a single removed
                        // row is small, the re-blur this costs is a short, snappy window, not the long
                        // spring tail the old per-row collapse used to burn.
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f)
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(stringResource(R.string.home_recents), style = TextStyle(text, 18.sp, FontWeight.Bold))
                            // Category selector. One glyph, no chip row — the filter is a
                            // secondary control and shouldn't compete with the list.
                            if (recents.isNotEmpty()) {
                                val filterActive = recentFilter != null
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .onGloballyPositioned {
                                            val o = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                                            filterAnchorY = o.y + it.size.height
                                        }
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (filterActive) accent.copy(0.20f)
                                            else if (isLight) Color.Black.copy(0.05f) else Color.White.copy(0.08f)
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { filterMenuOpen = !filterMenuOpen },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.FilterList,
                                        stringResource(R.string.recents_filter),
                                        Modifier.size(15.dp),
                                        if (filterActive) accent else sub
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (filtered.size > homeRecentLimit) {
                                BasicText(
                                    if (showAllRecents) stringResource(R.string.home_see_less) else "${stringResource(R.string.home_see_all)} (${filtered.size})",
                                    style = TextStyle(accent, 12.sp, FontWeight.SemiBold),
                                    modifier = Modifier.clickable { showAllRecents = !showAllRecents }
                                )
                            }
                            if (recents.isNotEmpty()) {
                                LiquidIconButton(
                                    onClick = {
                                        RecentFilesManager.clearRecents(context)
                                        recents = emptyList()
                                        showAllRecents = false
                                    },
                                    backdrop = backdrop,
                                    tint = redAccent,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    CloseCrossIcon(Modifier.size(16.dp), Color.White)
                                }
                            }
                        }
                    }

                    if (recents.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(36.dp), sub.copy(0.5f))
                            BasicText(
                                stringResource(R.string.home_no_recents),
                                style = TextStyle(sub, 14.sp, FontWeight.Medium, textAlign = TextAlign.Center)
                            )
                            BasicText(
                                stringResource(R.string.home_no_recents_subtitle),
                                style = TextStyle(sub.copy(0.7f), 12.sp, textAlign = TextAlign.Center)
                            )
                        }
                    } else {
                        if (filtered.isEmpty()) {
                            BasicText(
                                stringResource(R.string.recents_no_matches),
                                style = TextStyle(sub, 13.sp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            )
                        }
                        visibleRecents.forEach { recent ->
                            // `key` on the URI, not the index: a swipe-removed row must take its
                            // offset animation state with it instead of handing it to its successor.
                            key(recent.uriString) {
                                RecentRow(
                                    recent = recent,
                                    isLight = isLight,
                                    textColor = text,
                                    secondaryColor = sub,
                                    // Set by the long-press menu's "Remove". When it matches this row,
                                    // the row plays the same exit as a swipe before it is dropped.
                                    pendingDelete = pendingDeleteUri == recent.uriString,
                                    onClick = { onRecentFileSelected(recent.uri, recent.name) },
                                    onLongClick = { top, height ->
                                        selectedRecent = recent
                                        selectedRecentAnchorY = top
                                        selectedRecentRowHeight = height
                                    },
                                    onDelete = {
                                        // Called once the row has finished fading. Drop it from the
                                        // list (cheap, keyed rows) — the container's animateContentSize
                                        // springs the gap shut — and persist off the main thread so the
                                        // terminal relayout never blocks a frame during the exit.
                                        pendingDeleteUri = null
                                        recents = recents.filterNot { it.uriString == recent.uriString }
                                        homeScope.launch(Dispatchers.IO) { RecentFilesManager.removeRecent(context, recent.uri) }
                                    }
                                )
                            }
                        }
                        if (!searching && filtered.size > homeRecentLimit && !showAllRecents) {
                            BasicText(
                                stringResource(R.string.recents_long_press_hint),
                                style = TextStyle(sub.copy(0.6f), 11.sp, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Tab bar (64dp) + breathing room (20dp). The nav-bar inset is already in
            // `contentPadding`, supplied by the scaffold.
            item(key = "bottomSpacer") {
                Spacer(Modifier.height(84.dp))
            }
        }
        }

        // ── Floating Liquid Glass Chat Bubble Reaction Bar ──
        AnimatedVisibility(
            visible = selectedRecent != null,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { selectedRecent = null },
                contentAlignment = Alignment.TopCenter
            ) {
                val maxH = constraints.maxHeight
                val padPx = 16f * density
                selectedRecent?.let { recent ->
                    // Float the pill ABOVE the pressed row (WhatsApp-style); flip below only if
                    // there isn't enough room above.
                    val gap = 10f * density
                    val minY = padPx * 3f
                    val maxY = (maxH - recentPopupHeightPx - padPx * 3f).coerceAtLeast(minY)
                    val aboveY = selectedRecentAnchorY - recentPopupHeightPx - gap
                    val belowY = selectedRecentAnchorY + selectedRecentRowHeight + gap
                    val targetY = (if (aboveY >= minY) aboveY else belowY).coerceIn(minY, maxY)
                    // One glass capsule with the actions inside it, not five glass buttons floating
                    // in the air — and one blur pass instead of five. The capsule fades; the
                    // circles inside carry the morph (see GlassCapsuleMenu).
                    GlassCapsuleMenu(
                        actions = listOf(
                            GlassMenuAction(
                                Icons.Rounded.FileOpen, stringResource(R.string.recents_open), Color(0xFF0088FF)
                            ) { selectedRecent = null; onRecentFileSelected(recent.uri, recent.name) },
                            GlassMenuAction(
                                if (recent.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                stringResource(if (recent.pinned) R.string.recents_unpin else R.string.recents_pin),
                                Color(0xFFFF9500)
                            ) {
                                RecentFilesManager.togglePin(context, recent.uri)
                                recents = RecentFilesManager.getRecents(context)
                                selectedRecent = null
                            },
                            GlassMenuAction(
                                Icons.Rounded.IosShare, stringResource(R.string.recents_share), Color(0xFF34C759)
                            ) {
                                selectedRecent = null
                                val raw = recent.uri
                                // A file:// URI can't be shared to other apps (FileUriExposedException →
                                // crash). Convert to a FileProvider content:// URI first, and use the
                                // file's real MIME type so non-PDF recents (xlsx/images) share correctly.
                                val shareUri = if (raw.scheme == "file") {
                                    runCatching {
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.provider", java.io.File(raw.path!!)
                                        )
                                    }.getOrNull() ?: raw
                                } else raw
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = context.contentResolver.getType(shareUri) ?: "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.recents_share_pdf)))
                                }
                            },
                            GlassMenuAction(
                                Icons.Rounded.Info, stringResource(R.string.recents_details), Color(0xFF8E8E93)
                            ) { selectedRecent = null; infoRecent = recent },
                            GlassMenuAction(
                                Icons.Rounded.DeleteOutline, stringResource(R.string.recents_remove), Color(0xFFE53935)
                            ) {
                                // Hand the removal to the row so it fades out and the container springs
                                // shut, exactly like a swipe — no instant snap. The row calls back to
                                // `onDelete` when its fade completes.
                                selectedRecent = null
                                pendingDeleteUri = recent.uriString
                            }
                        ),
                        backdrop = backdrop,
                        uiSensor = uiSensor,
                        progress = morphProgress,
                        modifier = Modifier
                            .offset { IntOffset(0, targetY.toInt()) }
                            .onGloballyPositioned { recentPopupHeightPx = it.size.height }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Consume inner taps */ }
                    )
                }
            }
        }

        // ── Category filter menu ──
        // Same capsule as the long-press menu, anchored just under the filter glyph.
        AnimatedVisibility(
            visible = filterMenuOpen,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { filterMenuOpen = false },
                contentAlignment = Alignment.TopCenter
            ) {
                val neutral = if (isLight) Color(0xFF8E8E93) else Color(0xFF636366)
                // Labels resolved up front: `stringResource` is @Composable, so it can't be called
                // from inside a plain local helper.
                val categories = listOf(
                    Triple(null as DocKind?, Icons.Rounded.Apps, stringResource(R.string.recents_filter_all)) to accent,
                    Triple(DocKind.Pdf, Icons.Rounded.PictureAsPdf, stringResource(R.string.recents_filter_pdf)) to Color(0xFFE53935),
                    Triple(DocKind.Word, Icons.Rounded.Description, stringResource(R.string.recents_filter_word)) to Color(0xFF2B579A),
                    Triple(DocKind.Excel, Icons.Rounded.GridOn, stringResource(R.string.recents_filter_excel)) to Color(0xFF217346),
                    Triple(DocKind.Ppt, Icons.Rounded.Slideshow, stringResource(R.string.recents_filter_ppt)) to Color(0xFFD24726),
                    Triple(DocKind.Image, Icons.Rounded.Image, stringResource(R.string.recents_filter_image)) to Color(0xFF7E57C2)
                )

                GlassCapsuleMenu(
                    actions = categories.map { (spec, tint) ->
                        val (kind, icon, label) = spec
                        GlassMenuAction(
                            icon,
                            label,
                            // The active category is the only one that carries its colour —
                            // selection reads at a glance without a chip row.
                            if (recentFilter == kind) tint else neutral
                        ) {
                            recentFilter = kind
                            filterMenuOpen = false
                            showAllRecents = false
                        }
                    },
                    backdrop = backdrop,
                    uiSensor = uiSensor,
                    progress = filterProgress,
                    modifier = Modifier
                        .offset { IntOffset(0, (filterAnchorY + 8f * density).toInt()) }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume inner taps */ }
                )
            }
        }

        // File information dialog. Keep it in the same glass layer as the action sheet
        // so the action has an immediate, readable result instead of silently closing.
        AnimatedVisibility(
            visible = infoRecent != null,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Very light dim + NO ripple, so the screen stays in focus (not greyed out).
                    .background(Color.Black.copy(alpha = 0.14f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { infoRecent = null },
                contentAlignment = Alignment.Center
            ) {
                infoRecent?.let { recent ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .liquidGlassPanel(backdrop, uiSensor)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* Consume inner taps */ }
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE53935).copy(alpha = if (isLight) 0.14f else 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(24.dp), Color(0xFFE53935))
                            }
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    stringResource(R.string.recents_file_info),
                                    style = TextStyle(text, 18.sp, FontWeight.SemiBold)
                                )
                                BasicText(
                                    recent.name,
                                    style = TextStyle(sub, 12.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                        )

                        InfoRow(
                            stringResource(
                                if (docKindOf(recent.name) == DocKind.Excel) R.string.recents_sheets
                                else R.string.recents_pages
                            ),
                            if (recent.pageCount > 0) recent.pageCount.toString() else stringResource(R.string.recents_unknown),
                            text, sub
                        )
                        InfoRow(stringResource(R.string.recents_size_label), if (recent.sizeBytes > 0) formatFileSize(recent.sizeBytes) else stringResource(R.string.recents_unknown), text, sub)
                        InfoRow(stringResource(R.string.recents_added), formatTimestamp(recent.timestamp), text, sub)
                        InfoRow(stringResource(R.string.recents_location), recent.uri.toString(), text, sub, maxLines = 3)

                        LiquidButton(
                            onClick = { infoRecent = null },
                            backdrop = backdrop,
                            tint = accent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicText(stringResource(R.string.done), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Staggered entrance for one top-level section. All sections read from the same
 * [Transition], so they share a frame clock and only the delay differs.
 */
@Composable
private fun Transition<Boolean>.entranceModifier(index: Int, density: Float): Modifier {
    val alpha by animateFloat(
        transitionSpec = { tween(560, delayMillis = 90 * index, easing = FastOutSlowInEasing) },
        label = "entranceAlpha"
    ) { if (it) 1f else 0f }
    val offsetY by animateFloat(
        transitionSpec = { tween(560, delayMillis = 90 * index, easing = FastOutSlowInEasing) },
        label = "entranceOffsetY"
    ) { if (it) 0f else 22f }
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY * density
        // Modulate alpha per draw-op instead of compositing to an offscreen buffer. The glass panels
        // carry a soft `drawBackdrop` shadow that extends BEYOND their bounds; an offscreen layer
        // (the default when alpha < 1) clips that overspill, so the shadow stayed invisible through
        // the whole fade and then snapped in at full strength the instant alpha reached 1 and the
        // offscreen switched off. Modulating avoids the buffer entirely, so the shadow fades in with
        // its container — the way the Tools screen's panels already come in.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
    }
}

/** Fraction of the row width a swipe must cross to commit the delete. */
private const val SwipeCommitFraction = 0.40f

/**
 * How "armed" a swipe of [travel] px looks on a row [width] px wide: 0 until the swipe is well
 * underway, ramping to 1 right at [SwipeCommitFraction]. Drives the delete icon's scale.
 */
private fun armProgress(travel: Float, width: Float): Float {
    if (width <= 0f) return 0f
    val p = (travel / width).coerceIn(0f, 1f)
    return ((p - (SwipeCommitFraction - 0.22f)) / 0.22f).coerceIn(0f, 1f)
}

/** Fraction of the row width over which the slab reaches its full red. */
private const val RedRampFraction = 0.25f

/** The pale red the slab starts on, so the first quarter of the swipe reads as a hint. */
private val SwipeRedLight = Color(0xFFEF7B7B).copy(alpha = 0.55f)

/** The committed red. Reached at [RedRampFraction] and held for the rest of the travel. */
private val SwipeRedFull = Color(0xFFE53935)

/**
 * The slab colour for a swipe of [travel] px on a row [width] px wide.
 *
 * Separate from [armProgress] on purpose. The icon is allowed to hold back and then pop right at
 * the commit point, but the colour is the thing you read first, and ramping it against a 0.40
 * threshold meant the row stayed almost neutral through most of the gesture and then went red in a
 * rush. Full red by a quarter of the width instead: pale while you are still deciding, unambiguous
 * well before you have to commit, and flat afterwards so the last half of the travel does not keep
 * shifting under the finger.
 */
private fun swipeRed(travel: Float, width: Float): Color {
    if (width <= 0f) return SwipeRedLight
    val t = (travel / (width * RedRampFraction)).coerceIn(0f, 1f)
    return lerp(SwipeRedLight, SwipeRedFull, t)
}

/**
 * Row geometry, deliberately **not** snapshot state.
 *
 * `onGloballyPositioned` fires for a row whenever it is re-placed, and a delete re-places every row
 * below the one collapsing on every frame of the animation. Writing these into `mutableStateOf`
 * therefore recomposed most of the list ~60 times a second for the length of the delete — each of
 * those recompositions re-running `docKindOf`, the size/date formatting and the badge lookup for a
 * row whose contents had not changed at all. Nothing observes these reactively: the long-press and
 * the drag both read them at gesture time, long after they are set.
 */
private class RowMetrics {
    var topY = 0f
    var height = 0f
    var width = 1f
}

/**
 * One recents entry. Liquid-glass press: no grey ripple; the row eases down with a soft
 * spring while held (like an Apple button), then springs back on release. It reports its
 * root-space position on long-press so the reaction pill can anchor to it.
 */
@Composable
private fun RecentRow(
    recent: com.chethan616.clearpdf.data.repository.RecentFile,
    isLight: Boolean,
    textColor: Color,
    secondaryColor: Color,
    // True once the long-press menu's "Remove" targets this row: it plays the same exit a swipe does.
    pendingDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: (top: Float, height: Float) -> Unit,
    onDelete: () -> Unit
) {
    val metrics = remember { RowMetrics() }
    val rowInteraction = remember { MutableInteractionSource() }
    val rowPressed by rowInteraction.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        if (rowPressed) 0.96f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "recentPress"
    )
    // Swipe-to-delete. Left only: a rightward drag has no meaning here, and allowing it would
    // just expose empty space behind the row.
    val swipeX = remember { Animatable(0f) }
    // Opacity of the row AND its red slab together. The card and the slab fade as ONE object so the
    // red can never outlive the card it belongs to (the old "bare red band" overlap glitch). The gap
    // the row leaves behind is now closed by the container's `animateContentSize` spring, not by the
    // row collapsing its own height — so the exit here is a single, clean fade with no layout phase
    // fighting the drag, and no per-row re-blur tail.
    val exitAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // The single exit animation, shared by the swipe commit and the menu's "Remove". Guarded so the
    // two paths can't both fire it. A quick fade, then hand back to the parent to drop the row —
    // whereupon the container springs the gap shut.
    val exiting = remember { mutableStateOf(false) }
    suspend fun runExit() {
        if (exiting.value) return
        exiting.value = true
        exitAlpha.animateTo(0f, tween(140, easing = FastOutLinearInEasing))
        onDelete()
    }
    LaunchedEffect(pendingDelete) { if (pendingDelete) runExit() }

    Box(
        Modifier
            .fillMaxWidth()
            // Read inside the lambda, so the fade invalidates draw only. Wraps both the slab and
            // the card, which is the whole point — see [exitAlpha]. The gap-close is the container's
            // job now (its `animateContentSize` spring), so this row never animates its own height.
            .graphicsLayer { alpha = exitAlpha.value }
            .clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned {
                metrics.topY = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
                metrics.height = it.size.height.toFloat()
                metrics.width = it.size.width.toFloat().coerceAtLeast(1f)
            }
    ) {
        // The delete slab behind the row. Every read of `swipeX` here happens inside a draw
        // lambda, so a drag invalidates draw only — it never recomposes the row.
        Row(
            Modifier
                .matchParentSize()
                // Ease the slab in over the first sliver of travel instead of snapping it fully
                // opaque the instant the finger moves (the old `if (swipeX < 0) 1 else 0` toggle —
                // that hard flip, plus the pale red appearing at full strength, was the "red
                // glitching" pop). Now it fades up with the drag and fades back out cleanly on a
                // spring-back, so a half-swipe that is released leaves no red flash behind.
                .graphicsLayer {
                    val travel = -swipeX.value
                    alpha = (travel / (metrics.width * 0.10f)).coerceIn(0f, 1f)
                }
                .drawBehind {
                    // Pale for the first quarter of the travel, solid red from there on. See
                    // [swipeRed] — the ramp is deliberately much earlier than the commit point.
                    drawRect(swipeRed(-swipeX.value, size.width))
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                stringResource(R.string.recents_swipe_delete),
                Modifier
                    .padding(end = 20.dp)
                    .size(22.dp)
                    // Reads `swipeX` inside the layer block, so the drag invalidates draw only.
                    .graphicsLayer {
                        val travel = -swipeX.value
                        val w = metrics.width
                        val t = armProgress(travel, w)
                        val s = 0.82f + 0.33f * t
                        scaleX = s; scaleY = s
                        // Without this the icon is fully formed after one pixel of drag.
                        alpha = (travel / (w * 0.12f)).coerceIn(0f, 1f)
                    },
                Color.White
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeX.value
                    scaleX = rowScale
                    scaleY = rowScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(if (isLight) Color.White.copy(0.18f) else Color.White.copy(0.06f))
                .combinedClickable(
                    interactionSource = rowInteraction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { onLongClick(metrics.topY, metrics.height) }
                )
                // A pinned row is an explicit "keep this" — it doesn't swipe away.
                .then(
                    if (recent.pinned) Modifier else Modifier.pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        // Both live in the gesture scope, not in composition, so neither the arming
                        // tick nor the drag itself recomposes the row.
                        var wasArmed = false
                        // The authoritative drag offset. Reading it back off `swipeX` instead was a
                        // lag bug: `snapTo` is suspending, so it lands on the next dispatch, and
                        // every pointer event that arrived in the same frame computed its target
                        // from the same stale value — the row fell behind a fast finger.
                        var offset = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                wasArmed = false
                                offset = swipeX.value
                            },
                            onDragEnd = {
                                val vx = velocityTracker.calculateVelocity().x
                                val commitAt = metrics.width * SwipeCommitFraction
                                // Commit on distance OR on a fast flick. Requiring 40% of the row
                                // every time made a quick, confident swipe spring back.
                                if (-offset > commitAt || vx < -900f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    scope.launch {
                                        // Carry the swipe's momentum: keep sliding the card off to the
                                        // left while it dissolves, so the gesture never stalls into a
                                        // "stop, then fade" hitch. The slide and the fade run together;
                                        // the fade (140ms, inside runExit) finishes first and hands the
                                        // removal back to the parent, at which point the container's
                                        // animateContentSize spring closes the gap with its bounce. The
                                        // slab fades as one object with the card (exitAlpha), so no bare
                                        // red is ever left shrinking on its own.
                                        launch { swipeX.animateTo(-metrics.width, tween(190, easing = FastOutLinearInEasing)) }
                                        runExit()
                                    }
                                } else {
                                    scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = 420f)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = 420f)) }
                            }
                        ) { change, dragAmount ->
                            // Consume horizontal only, so the LazyColumn keeps its vertical scroll.
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val width = metrics.width
                            val commitAt = width * SwipeCommitFraction
                            // Past the commit point the row gets heavier — further travel moves it
                            // at a third speed. The threshold becomes something you feel through the
                            // resistance rather than a number you cross blind.
                            val resisted =
                                if (-offset > commitAt && dragAmount < 0f) dragAmount * 0.32f else dragAmount
                            offset = (offset + resisted).coerceIn(-width, 0f)
                            val nowArmed = -offset > commitAt
                            if (nowArmed != wasArmed) {
                                wasArmed = nowArmed
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            val target = offset
                            scope.launch { swipeX.snapTo(target) }
                        }
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon + badge reflect the ORIGINAL file type (from its name), so a
            // recents list of mixed docx/xlsx/pptx/images reads at a glance.
            val kind = docKindOf(recent.name)
            val (kIcon, kColor, kLabel) = kindVisual(kind)
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(kColor.copy(alpha = if (isLight) 0.14f else 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kIcon, null, Modifier.size(23.dp), kColor)
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    recent.name,
                    style = TextStyle(textColor, 14.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val timeStr = formatTimestamp(recent.timestamp)
                val sizeStr = if (recent.sizeBytes > 0) " · ${formatFileSize(recent.sizeBytes)}" else ""
                // A spreadsheet has sheets, not pages — the count is the same number, the noun isn't.
                val countStr = when {
                    recent.pageCount <= 0 -> ""
                    kind == DocKind.Excel -> " · ${stringResource(R.string.recents_sheet_count, recent.pageCount)}"
                    else -> " · ${stringResource(R.string.recents_page_count, recent.pageCount)}"
                }
                BasicText("$timeStr$sizeStr$countStr", style = TextStyle(secondaryColor, 11.sp))
            }

            // Pinned marker. Contained in the same kind of translucent tinted chip the rest of the
            // app uses so it reads as an intentional status badge rather than a stray glyph. The mark
            // itself is a shield-with-check ("kept") — cleaner and more legible at this size than the
            // old leaning pushpin, and it no longer needs a rotation to look deliberate.
            if (recent.pinned) {
                val pinOrange = Color(0xFFFF9500)
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(pinOrange.copy(alpha = if (isLight) 0.16f else 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pinned_badge),
                        stringResource(R.string.recents_unpin),
                        Modifier.size(15.dp),
                        pinOrange
                    )
                }
            }

            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(kColor.copy(0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                BasicText(kLabel, style = TextStyle(kColor, 9.sp, FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    textColor: Color,
    secondaryColor: Color,
    maxLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(label.uppercase(), style = TextStyle(secondaryColor, 10.sp, FontWeight.Bold, letterSpacing = 0.8.sp))
        BasicText(
            value,
            style = TextStyle(textColor, 13.sp, FontWeight.Medium),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> stringResource(R.string.recents_just_now)
        diff < 3_600_000 -> stringResource(R.string.recents_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> stringResource(R.string.recents_hours_ago, diff / 3_600_000)
        diff < 172_800_000 -> stringResource(R.string.recents_yesterday)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

@Composable
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.recents_size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.recents_size_kb, bytes / 1024)
    else -> stringResource(R.string.recents_size_mb, bytes / (1024.0 * 1024.0))
}

/** Icon, accent colour, and short badge for a document kind (used by the recents rows). */
private fun kindVisual(kind: DocKind): Triple<ImageVector, Color, String> = when (kind) {
    DocKind.Pdf   -> Triple(Icons.Rounded.PictureAsPdf, Color(0xFFE53935), "PDF")
    DocKind.Word  -> Triple(Icons.Rounded.Description,  Color(0xFF2B77E5), "DOC")
    DocKind.Excel -> Triple(Icons.Rounded.GridOn,       Color(0xFF1E8E5A), "XLS")
    DocKind.Ppt   -> Triple(Icons.Rounded.Slideshow,    Color(0xFFE8722B), "PPT")
    DocKind.Image -> Triple(Icons.Rounded.Image,        Color(0xFF8E5AF2), "IMG")
    DocKind.Other -> Triple(Icons.Rounded.Description,  Color(0xFF8E8E93), "FILE")
}

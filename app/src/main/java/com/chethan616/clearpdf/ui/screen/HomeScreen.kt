package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
    onRecentFileSelected: (Uri) -> Unit
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

    var recents by remember { mutableStateOf(RecentFilesManager.getRecents(context)) }
    var showAllRecents by remember { mutableStateOf(false) }
    var recentQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedRecent by remember { mutableStateOf<com.chethan616.clearpdf.data.repository.RecentFile?>(null) }
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
                    enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
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
                                    onClick = { onRecentFileSelected(recent.uri) },
                                    onLongClick = { top, height ->
                                        selectedRecent = recent
                                        selectedRecentAnchorY = top
                                        selectedRecentRowHeight = height
                                    },
                                    onSwipeDelete = {
                                        RecentFilesManager.removeRecent(context, recent.uri)
                                        recents = recents.filterNot { it.uriString == recent.uriString }
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
                            ) { selectedRecent = null; onRecentFileSelected(recent.uri) },
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
                                RecentFilesManager.removeRecent(context, recent.uri)
                                recents = recents.filterNot { it.uriString == recent.uri.toString() }
                                selectedRecent = null
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
    }
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
    onClick: () -> Unit,
    onLongClick: (top: Float, height: Float) -> Unit,
    onSwipeDelete: () -> Unit
) {
    var rowTopY by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0f) }
    var rowWidthPx by remember { mutableStateOf(1f) }
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
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned {
                rowTopY = it.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
                rowHeightPx = it.size.height.toFloat()
                rowWidthPx = it.size.width.toFloat().coerceAtLeast(1f)
            }
    ) {
        // The delete slab behind the row. Every read of `swipeX` here happens inside a draw
        // lambda, so a drag invalidates draw only — it never recomposes the row.
        Row(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (swipeX.value < 0f) 1f else 0f }
                .drawBehind {
                    // Deepens once the swipe is past the commit threshold, so the row tells you it
                    // will delete before you let go.
                    val armed = -swipeX.value > size.width * 0.40f
                    drawRect(Color(0xFFE53935).copy(if (armed) 0.92f else 0.55f))
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                Icons.Rounded.DeleteOutline,
                stringResource(R.string.recents_swipe_delete),
                Modifier.padding(end = 20.dp).size(22.dp),
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
                    onLongClick = { onLongClick(rowTopY, rowHeightPx) }
                )
                // A pinned row is an explicit "keep this" — it doesn't swipe away.
                .then(
                    if (recent.pinned) Modifier else Modifier.pointerInput(rowWidthPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (-swipeX.value > rowWidthPx * 0.40f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    scope.launch {
                                        swipeX.animateTo(-rowWidthPx, tween(160, easing = FastOutSlowInEasing))
                                        onSwipeDelete()
                                    }
                                } else {
                                    scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                            }
                        ) { change, dragAmount ->
                            // Consume horizontal only, so the LazyColumn keeps its vertical scroll.
                            change.consume()
                            scope.launch { swipeX.snapTo((swipeX.value + dragAmount).coerceIn(-rowWidthPx, 0f)) }
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

            if (recent.pinned) {
                Icon(Icons.Rounded.PushPin, stringResource(R.string.recents_unpin), Modifier.size(13.dp), Color(0xFFFF9500))
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

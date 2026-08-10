package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.chethan616.clearpdf.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.chethan616.clearpdf.ui.components.CloseCrossIcon

import androidx.compose.ui.graphics.graphicsLayer

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
    var selectedRecent by remember { mutableStateOf<com.chethan616.clearpdf.data.repository.RecentFile?>(null) }
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
    // One transition coordinates the buttons with the sheet. This removes four
    // independent state machines and keeps the glass surface on a single frame clock.
    val b1Scale by popupTransition.animateFloat(
        transitionSpec = { tween(240, if (targetState) 110 else 0) },
        label = "popupOpenScale"
    ) { if (it) 1f else 0f }
    val b2Scale by popupTransition.animateFloat(
        transitionSpec = { tween(240, if (targetState) 145 else 0) },
        label = "popupShareScale"
    ) { if (it) 1f else 0f }
    val b3Scale by popupTransition.animateFloat(
        transitionSpec = { tween(240, if (targetState) 180 else 0) },
        label = "popupDetailsScale"
    ) { if (it) 1f else 0f }
    val b4Scale by popupTransition.animateFloat(
        transitionSpec = { tween(240, if (targetState) 215 else 0) },
        label = "popupRemoveScale"
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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isVisible = true
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // Progressive staggered component animations (slow fade-in)
    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "topBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 18f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "topBarOffsetY"
    )

    val cardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 620, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "cardAlpha"
    )
    val cardOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 620, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "cardOffsetY"
    )

    val recentsAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700, delayMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "recentsAlpha"
    )
    val recentsOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 30f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 700, delayMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "recentsOffsetY"
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = topBarAlpha
                    translationY = topBarOffsetY * density
                }
            ) {
                LiquidGlassTopBar(
                    title = "ClearPDF",
                    backdrop = backdrop,
                    uiSensor = uiSensor,
                    fontFamily = SourGummyFontFamily,
                    titleFontSize = 24.sp,
                    actions = {
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
                    }
                )
            }

            // Welcome card
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = cardAlpha
                        translationY = cardOffsetY * density
                    }
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.Description, contentDescription = null,
                    tint = accent, modifier = Modifier.size(50.dp)
                )
                Spacer(Modifier.height(14.dp))
                BasicText(
                    stringResource(R.string.home_workspace_label),
                    style = TextStyle(accent, 11.sp, FontWeight.Bold, letterSpacing = 1.4.sp)
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.home_tagline),
                    style = TextStyle(
                        color = text,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SourGummyFontFamily,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.home_subtitle),
                    style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center)
                )
                Spacer(Modifier.height(22.dp))

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

            // Recent files
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = recentsAlpha
                        translationY = recentsOffsetY * density
                    }
                    .liquidGlassPanel(backdrop, uiSensor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicText(stringResource(R.string.home_recents), style = TextStyle(text, 18.sp, FontWeight.Bold))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (recents.isNotEmpty() && recents.size > homeRecentLimit) {
                            BasicText(
                                if (showAllRecents) stringResource(R.string.home_see_less) else "${stringResource(R.string.home_see_all)} (${recents.size})",
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
                    val visibleRecents = if (showAllRecents) recents else recents.take(homeRecentLimit)
                    visibleRecents.forEach { recent ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isLight) Color.White.copy(0.18f) else Color.White.copy(0.06f))
                                .combinedClickable(
                                    onClick = { onRecentFileSelected(recent.uri) },
                                    onLongClick = { selectedRecent = recent }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE53935).copy(alpha = if (isLight) 0.14f else 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(24.dp), Color(0xFFE53935))
                            }
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    recent.name,
                                    style = TextStyle(text, 14.sp, FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val timeStr = formatTimestamp(recent.timestamp)
                                val sizeStr = if (recent.sizeBytes > 0) " · ${formatFileSize(recent.sizeBytes)}" else ""
                                val pageStr = if (recent.pageCount > 0) " · ${stringResource(R.string.recents_page_count, recent.pageCount)}" else ""
                                BasicText("$timeStr$sizeStr$pageStr", style = TextStyle(sub, 11.sp))
                            }
                            
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                BasicText(stringResource(R.string.recents_pdf_type), style = TextStyle(accent, 9.sp, FontWeight.Bold))
                            }
                        }
                    }
                    if (recents.size > homeRecentLimit && !showAllRecents) {
                        BasicText(
                            stringResource(R.string.recents_long_press_hint),
                            style = TextStyle(sub.copy(0.6f), 11.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }

            // Dynamic bottom spacer: tab bar (64dp) + actual nav bar inset + breathing room (20dp)
            Spacer(Modifier.height(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp
            ))
        }

        // ── Floating Liquid Glass Chat Bubble Reaction Bar ──
        AnimatedVisibility(
            visible = selectedRecent != null,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { selectedRecent = null },
                contentAlignment = Alignment.Center
            ) {
                selectedRecent?.let { recent ->
                    Column(
                        Modifier
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                val scaleXValue = lerp(0.20f, 1.0f, morphProgress)
                                val scaleYValue = lerp(0.15f, 1.0f, morphProgress)
                                val translateYValue = lerp(100f, 0f, morphProgress) * density
                                scaleX = scaleXValue
                                scaleY = scaleYValue
                                translationY = translateYValue
                                alpha = morphProgress.coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(0.5f, 0.85f)
                                shadowElevation = (16f * morphProgress).dp.toPx()
                            }
                            .liquidGlassPanel(backdrop, uiSensor)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* Consume inner taps */ }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Chat bubble reaction header (filename & close)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935).copy(alpha = if (isLight) 0.14f else 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp), Color(0xFFE53935))
                                }
                                BasicText(
                                    recent.name,
                                    style = TextStyle(text, 14.sp, FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            LiquidIconButton(
                                onClick = { selectedRecent = null },
                                backdrop = backdrop,
                                surfaceColor = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.1f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                CloseCrossIcon(Modifier.size(14.dp), sub)
                            }
                        }

                        // Horizontal Staggered Reaction Buttons
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Open Button (Blue)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = b1Scale; scaleY = b1Scale
                                        alpha = b1Scale.coerceIn(0f, 1f)
                                    }
                            ) {
                                LiquidIconButton(
                                    onClick = {
                                        selectedRecent = null
                                        onRecentFileSelected(recent.uri)
                                    },
                                    backdrop = backdrop,
                                    tint = Color(0xFF0088FF),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(22.dp), Color.White)
                                }
                                BasicText(stringResource(R.string.recents_open), style = TextStyle(text, 11.sp, FontWeight.Medium))
                            }

                            // 2. Share Button (Green)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = b2Scale; scaleY = b2Scale
                                        alpha = b2Scale.coerceIn(0f, 1f)
                                    }
                            ) {
                                LiquidIconButton(
                                    onClick = {
                                        selectedRecent = null
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, recent.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.recents_share_pdf)))
                                    },
                                    backdrop = backdrop,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, null, Modifier.size(22.dp), Color.White)
                                }
                                BasicText(stringResource(R.string.recents_share), style = TextStyle(text, 11.sp, FontWeight.Medium))
                            }

                            // 3. Info Button (Purple)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = b3Scale; scaleY = b3Scale
                                        alpha = b3Scale.coerceIn(0f, 1f)
                                    }
                            ) {
                                LiquidIconButton(
                                    onClick = {
                                        selectedRecent = null
                                        infoRecent = recent
                                    },
                                    backdrop = backdrop,
                                    tint = Color(0xFF9C27B0),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Rounded.Info, null, Modifier.size(22.dp), Color.White)
                                }
                                BasicText(stringResource(R.string.recents_details), style = TextStyle(text, 11.sp, FontWeight.Medium))
                            }

                            // 4. Remove Button (Red)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = b4Scale; scaleY = b4Scale
                                        alpha = b4Scale.coerceIn(0f, 1f)
                                    }
                            ) {
                                LiquidIconButton(
                                    onClick = {
                                        RecentFilesManager.removeRecent(context, recent.uri)
                                        recents = recents.filterNot { it.uriString == recent.uri.toString() }
                                        selectedRecent = null
                                    },
                                    backdrop = backdrop,
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    CloseCrossIcon(Modifier.size(18.dp), Color.White)
                                }
                                BasicText(stringResource(R.string.recents_remove), style = TextStyle(redAccent, 11.sp, FontWeight.Medium))
                            }
                        }
                    }
                }
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
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { infoRecent = null },
                contentAlignment = Alignment.Center
            ) {
                infoRecent?.let { recent ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .liquidGlassPanel(backdrop, uiSensor)
                            .clickable { /* Consume inner taps */ }
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

                        InfoRow(stringResource(R.string.recents_pages), if (recent.pageCount > 0) recent.pageCount.toString() else stringResource(R.string.recents_unknown), text, sub)
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
private fun ActionSheetItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint)
        BasicText(label, style = TextStyle(textColor, 15.sp, FontWeight.Medium))
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

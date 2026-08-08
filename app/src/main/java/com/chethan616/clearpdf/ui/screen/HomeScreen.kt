package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                    actions = {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = if (isLight) 0.12f else 0.18f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                "ON DEVICE",
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
                    "PDF WORKSPACE",
                    style = TextStyle(accent, 11.sp, FontWeight.Bold, letterSpacing = 1.4.sp)
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "PDF work, beautifully simple.",
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
                    "Open, scan, and organize your files—privately on your device.",
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
                            BasicText("Open PDF", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
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
                            BasicText("Scan", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
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
                    BasicText("Recent files", style = TextStyle(text, 18.sp, FontWeight.Bold))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (recents.isNotEmpty() && recents.size > homeRecentLimit) {
                            BasicText(
                                if (showAllRecents) "Show less" else "See all (${recents.size})",
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
                                "Your workspace is ready.",
                                style = TextStyle(sub, 14.sp, FontWeight.Medium, textAlign = TextAlign.Center)
                            )
                            BasicText(
                                "Opened and exported PDFs will appear here.",
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
                                val pageStr = if (recent.pageCount > 0) " · ${recent.pageCount} pg" else ""
                                BasicText("$timeStr$sizeStr$pageStr", style = TextStyle(sub, 11.sp))
                            }
                            
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                BasicText("PDF", style = TextStyle(accent, 9.sp, FontWeight.Bold))
                            }
                        }
                    }
                    if (recents.size > homeRecentLimit && !showAllRecents) {
                        BasicText(
                            "Long press for quick actions",
                            style = TextStyle(sub.copy(0.6f), 11.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // ── Long-press Bouncy Jelly Glass Pop Menu ──
        AnimatedVisibility(
            visible = selectedRecent != null,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { selectedRecent = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                selectedRecent?.let { recent ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetY = { it / 2 }
                        ) + scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialScale = 0.7f
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + scaleOut(targetScale = 0.8f) + fadeOut()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .liquidGlassPanel(backdrop, uiSensor)
                                .clickable { /* Consume inner taps */ }
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header with Close Cross
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    Modifier.weight(1f),
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
                                            recent.name,
                                            style = TextStyle(text, 15.sp, FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val details = buildString {
                                            if (recent.sizeBytes > 0) append(formatFileSize(recent.sizeBytes))
                                            if (recent.pageCount > 0) {
                                                if (isNotEmpty()) append(" · ")
                                                append("${recent.pageCount} pages")
                                            }
                                            if (isNotEmpty()) append(" · ")
                                            append(formatTimestamp(recent.timestamp))
                                        }
                                        BasicText(details, style = TextStyle(sub, 12.sp))
                                    }
                                }

                                LiquidIconButton(
                                    onClick = { selectedRecent = null },
                                    backdrop = backdrop,
                                    surfaceColor = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.1f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    CloseCrossIcon(Modifier.size(16.dp), sub)
                                }
                            }

                            // Divider
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.08f))
                            )

                            // Horizontal Circular Action Buttons
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Open Button (Blue)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        selectedRecent = null
                                        onRecentFileSelected(recent.uri)
                                    }
                                ) {
                                    LiquidIconButton(
                                        onClick = {
                                            selectedRecent = null
                                            onRecentFileSelected(recent.uri)
                                        },
                                        backdrop = backdrop,
                                        tint = Color(0xFF0088FF),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(24.dp), Color.White)
                                    }
                                    BasicText("Open", style = TextStyle(text, 12.sp, FontWeight.Medium))
                                }

                                // 2. Share Button (Green)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        selectedRecent = null
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, recent.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
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
                                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                        },
                                        backdrop = backdrop,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(Icons.Rounded.Share, null, Modifier.size(24.dp), Color.White)
                                    }
                                    BasicText("Share", style = TextStyle(text, 12.sp, FontWeight.Medium))
                                }

                                // 3. Info Button (Purple)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        selectedRecent = null
                                        infoRecent = recent
                                    }
                                ) {
                                    LiquidIconButton(
                                        onClick = {
                                            selectedRecent = null
                                            infoRecent = recent
                                        },
                                        backdrop = backdrop,
                                        tint = Color(0xFF9C27B0),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), Color.White)
                                    }
                                    BasicText("Details", style = TextStyle(text, 12.sp, FontWeight.Medium))
                                }

                                // 4. Remove Button (Red with Close Cross style)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable {
                                        RecentFilesManager.removeRecent(context, recent.uri)
                                        recents = recents.filterNot { it.uriString == recent.uri.toString() }
                                        selectedRecent = null
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
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        CloseCrossIcon(Modifier.size(20.dp), Color.White)
                                    }
                                    BasicText("Remove", style = TextStyle(redAccent, 12.sp, FontWeight.Medium))
                                }
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
                                    "File Info",
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

                        InfoRow("Pages", if (recent.pageCount > 0) recent.pageCount.toString() else "Unknown", text, sub)
                        InfoRow("Size", if (recent.sizeBytes > 0) formatFileSize(recent.sizeBytes) else "Unknown", text, sub)
                        InfoRow("Added", formatTimestamp(recent.timestamp), text, sub)
                        InfoRow("Location", recent.uri.toString(), text, sub, maxLines = 3)

                        LiquidButton(
                            onClick = { infoRecent = null },
                            backdrop = backdrop,
                            tint = accent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicText("Done", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
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

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

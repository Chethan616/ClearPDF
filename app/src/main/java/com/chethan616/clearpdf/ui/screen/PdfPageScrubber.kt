package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.ViewerChromeGlass
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlin.math.roundToInt

/**
 * Adobe Acrobat-style vertical scrub bar pinned to the right margin.
 *
 * A thin, unobtrusive track that thickens while dragging. Dragging snaps to page
 * steps with a haptic tick per boundary and surfaces a floating "Page X of Y"
 * bubble with a live thumbnail preview. Visibility is owned by the parent (via
 * [AnimatedVisibility]); this composable only animates its own drag affordances.
 */
private val TRACK_HEIGHT = 208.dp

@Composable
internal fun PageScrubber(
    currentPage: Int,
    pageCount: Int,
    pageBitmaps: List<Bitmap?>,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    onPageChange: (Int) -> Unit,
    onPageScrubbing: (Int) -> Unit,
    /**
     * Reported on every rail grab/release. The parent fades the whole scrubber down when the list
     * is at rest, and dragging the rail does not scroll the list (that only happens on release), so
     * without this the preview bubble fades to 40% opacity in the middle of a drag.
     */
    onDraggingChange: (Boolean) -> Unit = {},
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val view = LocalView.current
    val accent = Color(0xFF0A84FF)
    // Idle thumb: soft light blue — visible on both themes, not harsh like pure white.
    val idleThumb = Color(0xFF8FBFFF)

    var isDragging by remember { mutableStateOf(false) }
    var dragPage by remember { mutableIntStateOf(currentPage) }
    val lastSpan = (pageCount - 1).coerceAtLeast(1)

    // Follow the pager when not actively scrubbing.
    LaunchedEffect(currentPage) { if (!isDragging) dragPage = currentPage }

    // Haptic tick each time the resolved page changes during a drag.
    LaunchedEffect(dragPage, isDragging) {
        if (isDragging) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            onPageScrubbing(dragPage)
        }
    }

    val fraction by animateFloatAsState(
        targetValue = (dragPage.toFloat() / lastSpan).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "scrubFraction"
    )
    val trackWidth by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "trackWidth"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 40.dp else 30.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbHeight"
    )

    Box(modifier = modifier) {
        // ── The scrub track (drag target) ───────────────────────────────────
        Box(
            Modifier
                // Pin the rail to the right edge so it doesn't jump left when the wider preview
                // bubble appears (which grows the parent Box's width during a drag).
                .align(Alignment.CenterEnd)
                .height(TRACK_HEIGHT)
                .width(28.dp)
                .pointerInput(pageCount) {
                    detectTapGestures { offset ->
                        val target = ((offset.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        dragPage = target
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                        onPageChange(target)
                    }
                }
                .pointerInput(pageCount) {
                    detectDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            onDraggingChange(true)
                            dragPage = ((start.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPage = ((change.position.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDragEnd = { isDragging = false; onDraggingChange(false); onPageChange(dragPage) },
                        onDragCancel = { isDragging = false; onDraggingChange(false); onPageChange(dragPage) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background rail
            Box(
                Modifier
                    .width(trackWidth)
                    .height(TRACK_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.10f)),
                contentAlignment = Alignment.TopCenter
            ) {
                // Draggable thumb
                Box(
                    Modifier
                        .padding(top = ((TRACK_HEIGHT - thumbHeight) * fraction).coerceAtLeast(0.dp))
                        .width(if (isDragging) 8.dp else 4.dp)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (isDragging) accent else idleThumb)
                )
            }
        }

        // ── Floating page thumbnail (a compact scroll navigator, NOT a second document) ──────
        // A clean miniature page card that follows the scroll/drag position on the right. It's an
        // overlay (no glass container behind it, no grey pill), has no gesture handler so it never
        // blocks document scrolling, preserves the real page aspect ratio, and stays inside the
        // track. Shown ONLY while dragging the rail (never on normal scroll/fling).
        val screenW = LocalConfiguration.current.screenWidthDp
        val thumbW = when {
            screenW < 340 -> 0.dp        // too little room → rail only, hide the thumbnail
            screenW < 400 -> 84.dp
            else          -> 104.dp
        }
        AnimatedVisibility(
            visible = isDragging && thumbW > 0.dp,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
            exit  = fadeOut(tween(200)) + scaleOut(targetScale = 0.94f),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // Keep the LAST GOOD bitmap + aspect. The scrubbed page's high-res bitmap renders
            // asynchronously, so during a fast drag it's often momentarily null (or a low-res cache
            // being upgraded). Rather than crossfade null↔bitmap or low-res↔high-res (which read as
            // a half/ghost render that stutters), we swap the image IN PLACE and, when the target is
            // not ready yet, keep showing the last good one — the preview simply "catches up".
            var lastShown by remember { mutableStateOf<Bitmap?>(null) }
            var lastAspect by remember { mutableFloatStateOf(1f / 1.414f) }
            val live = pageBitmaps.getOrNull(dragPage)?.takeIf { !it.isRecycled && it.height > 0 }
            if (live != null) {
                lastShown = live
                lastAspect = live.width.toFloat() / live.height.toFloat()
            }
            val shown = live ?: lastShown?.takeIf { !it.isRecycled }
            // Follow the scroll position, clamped to the track slack so it's never clipped at the ends.
            val yOffset = (TRACK_HEIGHT * fraction - TRACK_HEIGHT / 2f).coerceIn(-22.dp, 22.dp)

            Column(
                modifier = Modifier
                    .offset(x = (-34).dp, y = yOffset)
                    .width(thumbW)
                    // No elevation shadow: an offset elevation shadow with clip=false bleeds past
                    // the scrubber's offscreen alpha layer and reads as a torn edge. A clean clipped
                    // card with a slightly stronger border gives the same lift without the artifact.
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isDark) Color(0xFF2A2E37) else Color.White)
                    .border(1.dp, if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.14f), RoundedCornerShape(11.dp))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(lastAspect)          // stable aspect → no layout jolt between pages
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDark) Color(0xFF1B1E24) else Color(0xFFF2F3F5)),
                    contentAlignment = Alignment.Center
                ) {
                    if (shown != null) {
                        Image(
                            bitmap = shown.asImageBitmap(),
                            contentDescription = stringResource(R.string.preview_page, dragPage + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Nothing rendered yet at all → a faint spinner (only on the very first preview).
                        CircularProgressIndicator(color = accent.copy(0.7f), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                }
                BasicText(
                    "${dragPage + 1} / $pageCount",
                    style = TextStyle(if (isDark) Color.White.copy(0.85f) else Color(0xFF333333), 10.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

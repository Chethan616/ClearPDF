package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
                            dragPage = ((start.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPage = ((change.position.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDragEnd = { isDragging = false; onPageChange(dragPage) },
                        onDragCancel = { isDragging = false; onPageChange(dragPage) }
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

        // ── Floating page bubble + thumbnail preview ────────────────────────
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val preview = pageBitmaps.getOrNull(dragPage)
            val yOffset = (TRACK_HEIGHT * fraction - TRACK_HEIGHT / 2f).coerceIn(-84.dp, 84.dp)

            Column(
                modifier = Modifier
                    .offset(x = (-44).dp, y = yOffset)
                    .width(132.dp)
                    .liquidGlassPanel(backdrop, uiSensor, ViewerChromeGlass)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(0.24f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicText(
                        stringResource(R.string.viewer_page_label),
                        style = TextStyle(accent, 8.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText(
                        "${dragPage + 1} / $pageCount",
                        style = TextStyle(Color.White, 12.sp, fontWeight = FontWeight.ExtraBold)
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(0.45f))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null && !preview.isRecycled) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.preview_page, dragPage + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    } else {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

package com.chethan616.clearpdf.ui.screen

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SheetScrubberTrackHeight = 208.dp

/**
 * A WPS/Excel-style vertical jump rail for the row axis — drag to scroll to any row in one motion,
 * with a small floating "N/Total" badge that tracks the finger, instead of counting flicks to get
 * from row 12 to row 940.
 *
 * Modelled directly on [PageScrubber] (the PDF viewer's page rail): same track/thumb sizing, same
 * tap-to-jump + drag-to-scrub gesture, same haptic tick per step. The one real difference is that a
 * spreadsheet has no per-row render cost the way a PDF page does, so this scrolls the list live on
 * every drag tick instead of only on release — the sheet content itself becomes the "preview",
 * which is what the WPS reference screenshot actually shows (the grid moving under the thumb, not a
 * separate popup).
 *
 * Only shown for sheets long enough that the rail is a shortcut rather than clutter — a 20-row sheet
 * scrolls in one swipe already.
 */
@Composable
internal fun SheetRowScrubber(
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowCount: Int,
    backdrop: LayerBackdrop,
    chromeGlass: Color,
    accent: Color,
    text: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    /** Sheet row number (1-based) shown in the badge for list index `i`. */
    labelFor: (Int) -> Int = { it + 1 },
    totalLabel: Int = rowCount
) {
    if (rowCount < 60) return

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragRow by remember { mutableIntStateOf(0) }
    val lastSpan = (rowCount - 1).coerceAtLeast(1)

    // The thumb's position, as a fraction of the track. Driven from a snapshotFlow into an
    // Animatable and read only in draw/layout lambdas below. Before, `firstVisibleItemIndex` was
    // copied into a state the scrubber read in composition and fed through `animateFloatAsState`
    // into a `padding(top = …)`: every row scrolled past recomposed and re-measured the scrubber,
    // and every frame of the follow-spring did it again — continuous recomposition for as long as
    // the sheet was moving.
    val thumb = remember { Animatable(0f) }
    LaunchedEffect(listState, lastSpan) {
        snapshotFlow { if (isDragging) dragRow else listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { idx ->
                thumb.animateTo(
                    (idx.toFloat() / lastSpan).coerceIn(0f, 1f),
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                )
            }
    }

    // Haptic tick + live scroll on every row the drag crosses.
    LaunchedEffect(listState) {
        snapshotFlow { if (isDragging) dragRow else -1 }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { row ->
                runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                runCatching { listState.scrollToItem(row) }
            }
    }

    // 0 idle → 1 while dragging; widens the track and lengthens the thumb. A State read in draw.
    val active = animateFloatAsState(
        if (isDragging) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "sheetScrubActive"
    )
    val trackColor = if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.10f)

    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .height(SheetScrubberTrackHeight)
                .width(28.dp)
                .pointerInput(rowCount) {
                    detectTapGestures { offset ->
                        val target = ((offset.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                        // Tap-to-jump used to only move `dragRow`, which nothing scrolled to outside
                        // a drag, so a tap on the rail did nothing.
                        scope.launch { runCatching { listState.scrollToItem(target) } }
                    }
                }
                .pointerInput(rowCount) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragRow = ((start.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                            isDragging = true
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragRow = ((change.position.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
                .drawBehind {
                    val p = active.value
                    val trackW = (4.dp + 4.dp * p).toPx()
                    val thumbH = (30.dp + 10.dp * p).toPx()
                    val x = (size.width - trackW) / 2f
                    val radius = CornerRadius(trackW / 2f)
                    drawRoundRect(trackColor, Offset(x, 0f), Size(trackW, size.height), radius)
                    val y = (size.height - thumbH) * thumb.value
                    drawRoundRect(lerp(accent.copy(alpha = 0.55f), accent, p), Offset(x, y), Size(trackW, thumbH), radius)
                }
        )

        // A small pill badge — "12/940", not "Row 12 / 940" in a wide card. Sized to match the 40 dp
        // search-icon circle it sits beside: a plain `CircleShape` can't hold a 4-digit fraction
        // without clipping, so this starts as a near-circle for short numbers and only widens as far
        // as the digits actually need, via `defaultMinSize` rather than a fixed wide padding.
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                Modifier
                    // Follows the thumb in the layout phase only; no recomposition per frame.
                    .offset {
                        val track = SheetScrubberTrackHeight.toPx()
                        val y = (track * thumb.value - track / 2f).coerceIn(-90.dp.toPx(), 90.dp.toPx())
                        IntOffset((-32).dp.roundToPx(), y.roundToInt())
                    }
                    .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
                    .viewerGlass(backdrop, chromeGlass, shape = { Capsule })
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    stringResource(R.string.sheet_row_of, labelFor(dragRow), totalLabel),
                    style = TextStyle(text, 10.sp, FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A fling behaviour that stacks: flick fast in the same direction again before the previous fling
 * has settled and the next one travels further, up to [MaxFlingMultiplier] times a normal one.
 *
 * A spreadsheet is the one screen in this app that is routinely thousands of rows long, and the
 * platform fling is tuned for lists you read rather than lists you traverse — reaching row 4000
 * takes a tiring number of identical swipes. Repeated fast swipes are already the gesture people
 * reach for there, so this reads them as one intent and gives them distance.
 *
 * It boosts the *initial velocity* and then hands off to the platform's own decay curve, so the
 * motion is the standard one throughout — faster, never jumpier. Nothing snaps or teleports.
 *
 * Only deliberate flicks count toward a streak: a swipe under [MinStreakVelocityDp] per second is
 * someone positioning carefully, and stacking those would make precise scrolling impossible.
 * Changing direction, or pausing past [StreakWindowMillis], resets it.
 */
@Composable
internal fun rememberStackingFlingBehavior(): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    val minVelocity = with(LocalDensity.current) { MinStreakVelocityDp.dp.toPx() }
    return remember(base, minVelocity) { StackingFlingBehavior(base, minVelocity) }
}

/** dp per second below which a swipe is treated as positioning, not as a fast flick. */
private const val MinStreakVelocityDp = 1200f

/** How long after a fling a follow-up still counts as part of the same burst. */
private const val StreakWindowMillis = 320L

/** Each consecutive fast flick adds this much of a normal fling's velocity. */
private const val FlingBoostPerSwipe = 0.9f

/** Ceiling, so a long burst can't launch the sheet somewhere unrecoverable. */
private const val MaxFlingMultiplier = 4f

private class StackingFlingBehavior(
    private val base: FlingBehavior,
    private val minVelocity: Float
) : FlingBehavior {

    private var lastDirection = 0
    private var lastFlingAtMillis = 0L
    private var streak = 0

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val now = SystemClock.uptimeMillis()
        val direction = when {
            initialVelocity > 0f -> 1
            initialVelocity < 0f -> -1
            else -> 0
        }
        val isFastFlick = abs(initialVelocity) >= minVelocity
        val continuesBurst = direction != 0 &&
            direction == lastDirection &&
            now - lastFlingAtMillis <= StreakWindowMillis

        streak = if (isFastFlick && continuesBurst) streak + 1 else 0
        lastDirection = direction
        lastFlingAtMillis = now

        val multiplier = (1f + streak * FlingBoostPerSwipe).coerceAtMost(MaxFlingMultiplier)
        // Delegating rather than animating here is the point: the decay curve, the over-scroll
        // handover and the "velocity left over" contract all stay exactly the platform's.
        return with(base) { performFling(initialVelocity * multiplier) }
    }
}

/** Share the (mirrored) file. file:// → FileProvider content:// so it isn't exposed → no crash. */
internal fun shareFile(context: android.content.Context, uri: android.net.Uri) {
    val shareUri = if (uri.scheme == "file") {
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(uri.path!!))
        }.getOrNull() ?: uri
    } else uri
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(shareUri) ?: "application/octet-stream"
        putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
}

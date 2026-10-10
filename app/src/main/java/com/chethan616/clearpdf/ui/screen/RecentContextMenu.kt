package com.chethan616.clearpdf.ui.screen

import com.chethan616.clearpdf.ui.components.rememberJellyPress
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.chethan616.clearpdf.ui.components.liquidRowClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.components.glassMenu
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One entry of the Recents context menu. [confirmLabel] makes it a two-step destructive action:
 * the first tap arms it (the row turns red and says [confirmLabel]), the second performs it.
 */
internal class RecentMenuAction(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val destructive: Boolean = false,
    val confirmLabel: String? = null,
    val onClick: () -> Unit
)

private val MenuWidth = 256.dp

/** How much of the entrance each successive item is delayed by, as a fraction of `progress`. */
private const val Stagger = 0.06f

/**
 * iOS-style context menu for a long-pressed Recents row.
 *
 * - A blur-free dim scrim with a rounded hole punched where the pressed row sits, so the real row
 *   (which lifts itself; see RecentRow's `lifted`) stays bright above the dimmed screen.
 * - ONE glass panel. Opening scales it 0.6 -> 1 around the edge nearest the row, so it visibly
 *   grows out of it; the vivid primary buttons and rows cascade in from the same shared progress
 *   (no per-item springs). Closing plays it backwards.
 * - Placed below the row when it fits, above otherwise, clamped inside the status bar and the
 *   floating tab bar. Dismisses on outside tap, any drag on the scrim (scroll), or back.
 */
@Composable
internal fun RecentContextMenu(
    visible: Boolean,
    anchor: Rect,
    title: String,
    backdrop: Backdrop,
    primary: List<RecentMenuAction>,
    actions: List<RecentMenuAction>,
    onDismiss: () -> Unit
) {
    // Unfolds from the pressed row like iOS 27: width on [GlassMotion.unfold], height a beat behind
    // on the softer [GlassMotion.unfoldLag], so it lands as one gel-like overshoot. Folds back fast.
    val progress = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        coroutineScope {
            launch { fade.animateTo(target, GlassMotion.fade()) }
            launch { stretch.animateTo(target, if (visible) GlassMotion.unfoldLag() else GlassMotion.settle()) }
            progress.animateTo(target, if (visible) GlassMotion.unfold() else GlassMotion.settle())
        }
    }
    BackHandler(enabled = visible, onBack = onDismiss)
    // derivedStateOf: composition only hears about the 0 crossing, not every animation frame.
    val gone by remember { androidx.compose.runtime.derivedStateOf { fade.value <= 0.001f && progress.value <= 0.001f && stretch.value <= 0.001f } }
    if (!visible && gone) return

    val isDark = LocalIsDarkMode.current
    val fg = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val glass = if (isDark) Color(0xFF1C1D22).copy(alpha = 0.74f) else Color.White.copy(alpha = 0.76f)
    val scrim = Color.Black.copy(alpha = if (isDark) 0.40f else 0.22f)
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val currentVisible by androidx.compose.runtime.rememberUpdatedState(visible)

    val topSafe = WindowInsets.statusBars.getTop(density) + with(density) { 12.dp.toPx() }
    // The floating tab bar (64 dp + margins) sits over the bottom of Home.
    val bottomSafe = WindowInsets.navigationBars.getBottom(density) + with(density) { 88.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }
    val liftPad = with(density) { 6.dp.toPx() }
    val holeRadius = with(density) { 18.dp.toPx() }

    Layout(
        content = {
            // Scrim. Reads `fade` in draw only; any pointer-down dismisses, so a tap OR the start of
            // a scroll both close the menu (the list underneath never sees the gesture).
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val a = anchor.translate(-origin)
                        val hole = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    a.left - liftPad, a.top - liftPad, a.right + liftPad, a.bottom + liftPad,
                                    CornerRadius(holeRadius)
                                )
                            )
                        }
                        clipPath(hole, ClipOp.Difference) {
                            drawRect(scrim.copy(alpha = scrim.alpha * fade.value))
                        }
                    }
                    .pointerInputDismiss { if (currentVisible) currentDismiss() }
            )
            MenuPanel(
                title = title, fg = fg, sub = sub, glass = glass, backdrop = backdrop,
                primary = primary, actions = actions, progress = { progress.value }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { origin = it.positionInRoot() }
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val scrimP = measurables[0].measure(Constraints.fixed(w, h))
        val panelW = MenuWidth.roundToPx().coerceAtMost((w - 2 * marginPx).roundToInt().coerceAtLeast(0))
        val maxPanelH = (h - topSafe - bottomSafe).roundToInt().coerceAtLeast(0)
        val panel = measurables[1].measure(Constraints(minWidth = panelW, maxWidth = panelW, maxHeight = maxPanelH))
        layout(w, h) {
            scrimP.place(0, 0)
            val a = anchor.translate(-origin)
            val ph = panel.height.toFloat()
            val below = a.bottom + gapPx
            val above = a.top - gapPx - ph
            val placeBelow = below + ph <= h - bottomSafe || above < topSafe
            val y = (if (placeBelow) below else above)
                .coerceIn(topSafe, (h - bottomSafe - ph).coerceAtLeast(topSafe))
            // Trailing-aligned with the row, like iOS aligns the menu to the pressed cell.
            val x = (a.right - panelW).coerceIn(marginPx, (w - panelW - marginPx).coerceAtLeast(marginPx))
            val pivotX = ((a.center.x - x) / panelW).coerceIn(0f, 1f)
            val pivotY = if (placeBelow) 0f else 1f
            panel.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                scaleX = lerp(0.6f, 1f, progress.value)
                scaleY = lerp(0.5f, 1f, stretch.value)
                alpha = fade.value
                transformOrigin = TransformOrigin(pivotX, pivotY)
            }
        }
    }
}

private fun Modifier.pointerInputDismiss(onDown: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onDown()
        }
    }

@Composable
private fun MenuPanel(
    title: String,
    fg: Color,
    sub: Color,
    glass: Color,
    backdrop: Backdrop,
    primary: List<RecentMenuAction>,
    actions: List<RecentMenuAction>,
    progress: () -> Float
) {
    fun local(index: Int): Float {
        val head = index * Stagger
        return ((progress() - head) / (1f - head).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    }
    // iOS-compact: the everyday actions as one row of small icon tiles (Open, Share, Pin, Open
    // with); everything else as slim rows. No title line — the lifted row already says which file.
    val quick = (primary + actions.filter { !it.destructive && it.confirmLabel == null }).take(4)
    val rows = actions.filter { it !in quick }
    Column(
        Modifier
            .width(MenuWidth)
            .glassMenu(backdrop, dark = com.chethan616.clearpdf.ui.theme.LocalIsDarkMode.current, shape = { RoundedRectangle(22.dp * com.chethan616.clearpdf.ui.components.GlassSettings.style.corners) })
            // Swallow taps on the panel's padding so they don't fall through to the scrim.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { contentDescription = title }
            .padding(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEachIndexed { i, action ->
                QuickTile(action, fg, Modifier.weight(1f), enter = { local(i) })
            }
        }
        if (rows.isNotEmpty()) {
            Box(
                Modifier
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .drawBehind { drawRect(fg.copy(alpha = 0.12f)) }
            )
            rows.forEachIndexed { i, action -> MenuRow(action, fg, enter = { local(quick.size + i) }) }
        }
    }
}

/** One compact quick action: tinted icon over a short label, springing under the finger. */
@Composable
private fun QuickTile(action: RecentMenuAction, fg: Color, modifier: Modifier, enter: () -> Float) {
    Column(
        modifier
            .height(54.dp)
            .graphicsLayer {
                val e = enter()
                alpha = e
                val s = lerp(0.85f, 1f, e)
                scaleX = s; scaleY = s
            }
            .liquidRowClick(corner = 14.dp, onClick = action.onClick)
            .drawBehind {
                // Tile wash follows the Liquid Glass Tint, like every glass surface.
                val t = com.chethan616.clearpdf.ui.components.GlassSettings.style.tint
                drawRoundRect(
                    action.tint.copy(alpha = (0.14f * t).coerceIn(0f, 0.5f)),
                    cornerRadius = CornerRadius(14.dp.toPx() * com.chethan616.clearpdf.ui.components.GlassSettings.style.corners)
                )
            }
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Icon(action.icon, null, Modifier.size(19.dp), action.tint)
        BasicText(
            action.label,
            style = TextStyle(fg, 10.5.sp, FontWeight.Medium, textAlign = TextAlign.Center, letterSpacing = (-0.1).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuRow(action: RecentMenuAction, fg: Color, enter: () -> Float) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var armed by remember { mutableStateOf(false) }
    val jelly = rememberJellyPress(interaction, pressedScale = 0.97f)
    val wash by animateFloatAsState(
        when {
            armed -> 1f
            pressed -> 0.5f
            else -> 0f
        },
        GlassMotion.fade(), label = "recentMenuWash"
    )
    val ink = if (action.destructive) action.tint else fg
    val label = if (armed && action.confirmLabel != null) action.confirmLabel else action.label
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer {
                val e = enter()
                alpha = e
                translationY = (1f - e) * 10.dp.toPx()
                scaleX = jelly.scale; scaleY = jelly.scale
            }
            .drawBehind {
                if (wash > 0f) {
                    val c = if (armed || action.destructive) action.tint.copy(alpha = 0.16f * wash)
                    else fg.copy(alpha = 0.08f * wash)
                    drawRoundRect(c, cornerRadius = CornerRadius(12.dp.toPx()))
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                if (action.confirmLabel != null && !armed) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = true
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    action.onClick()
                }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(action.icon, null, Modifier.size(16.dp), action.tint)
        BasicText(
            label,
            style = TextStyle(ink, 13.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** True when [uri]'s provider lets us delete the underlying file (not just the recents entry). */
internal fun canDeleteDocument(context: Context, uri: Uri): Boolean = runCatching {
    when (uri.scheme) {
        "file" -> java.io.File(uri.path ?: return false).let { it.isFile && it.parentFile?.canWrite() == true }
        "content" -> {
            if (!DocumentsContract.isDocumentUri(context, uri)) return false
            context.contentResolver.query(
                uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null
            )?.use { c ->
                c.moveToFirst() && (c.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
            } ?: false
        }
        else -> false
    }
}.getOrDefault(false)

/** Deletes the file behind [uri]. Returns false (never throws) when the provider refuses. */
internal fun deleteDocument(context: Context, uri: Uri): Boolean = runCatching {
    when (uri.scheme) {
        "file" -> java.io.File(uri.path!!).delete()
        "content" -> DocumentsContract.deleteDocument(context.contentResolver, uri)
        else -> false
    }
}.getOrDefault(false)

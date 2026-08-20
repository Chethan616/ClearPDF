package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule

/**
 * A liquid-glass circle with an Apple-style share gesture: **tap** = [onOpen]; **press-and-hold**
 * morphs the circle into a vertical glass capsule, and **swiping up** past a threshold then releasing
 * fires [onShare] (an iOS power-slider). Idle capsule is plain glass; it tints blue as the finger
 * travels up. Fixed 52dp width; height animates 52→104. Meant to be placed as a bottom-anchored
 * overlay so the capsule grows strictly UPWARD.
 *
 * Animation notes: height and both icon layers run off a **single** [updateTransition], so the icon
 * swap can't drift away from the morph the way a separate `Crossfade` clock did. The spring is
 * deliberately well damped — the height drives *layout*, so every overshoot frame re-measures the box
 * and forces `drawBackdrop` to re-run its blur and lens at a new size. Damping here is a performance
 * fix as much as a visual one.
 */
@Composable
fun ShareMorphButton(
    backdrop: LayerBackdrop,
    glass: Color,
    fg: Color,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onShareModeChanged: (Boolean) -> Unit = {},
    idleIcon: ImageVector = Icons.Rounded.UploadFile,
    idleContentDesc: String? = null,
    modifier: Modifier = Modifier
) {
    var shareMode by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var dragUp by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 48.dp.toPx() }
    val blue = Color(0xFF0A84FF)

    val morph = updateTransition(shareMode, label = "shareMorph")
    // Underdamped: the capsule springs past 104dp and settles back, so the morph has real weight.
    val height by morph.animateDp(
        transitionSpec = { GlassMotion.morph() },
        label = "shareMorphHeight"
    ) { if (it) 104.dp else 52.dp }
    // 0 while idle, 1 while morphed. Both icon layers read this, so they cross-fade against the
    // same curve the height is travelling on. The fade itself stays critically damped — a bouncing
    // alpha reads as a flicker — while `pop` carries the bounce on scale instead.
    val morphed by morph.animateFloat(
        transitionSpec = { GlassMotion.fade() },
        label = "shareMorphContent"
    ) { if (it) 1f else 0f }
    val pop by morph.animateFloat(
        transitionSpec = { GlassMotion.pop() },
        label = "shareMorphPop"
    ) { if (it) 1f else 0f }

    // A slow bob on the chevron hints "keep going up" while the capsule is open. Only runs while
    // morphed, and only touches translation, so it never re-measures the glass.
    val bobTransition = rememberInfiniteTransition(label = "shareMorphBob")
    val bob by bobTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "shareMorphBobOffset"
    )

    val progress = (-dragUp / thresholdPx).coerceIn(0f, 1f)
    // Raw drag distance tracks the finger 1:1, which makes the blue arrive abruptly. Easing it lets
    // the tint build gently and settle right as the gesture arms.
    val eased = FastOutSlowInEasing.transform(progress)
    val armed = progress >= 1f
    val surface = lerp(glass, blue, eased)

    // Fire exactly once on the false→true edge. Keying the effect on `armed` alone also fired on
    // first composition and again on disarm.
    var wasArmed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed && !wasArmed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasArmed = armed
    }

    // Presses down firmly and bounces back on release, like LiquidButton's deformation.
    val pressScale by animateFloatAsState(
        if (pressed && !shareMode) GlassMotion.PressedScale else 1f,
        GlassMotion.press(),
        label = "shareMorphPress"
    )

    Box(
        modifier
            .width(52.dp)
            .height(height)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            // The viewer's own chrome material, so this sits beside the back and search circles as one
            // family rather than as a frostier slab. Capsule, not a 28 dp rounded rectangle, so the
            // 52 -> 104 dp morph is an actual circle-to-capsule the whole way up.
            .viewerGlass(backdrop, surface, shape = { Capsule })
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { if (!shareMode) onOpen() }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        shareMode = true; onShareModeChanged(true); dragUp = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount -> change.consume(); dragUp += amount.y },
                    onDragEnd = {
                        if (dragUp < -thresholdPx) onShare()
                        shareMode = false; onShareModeChanged(false); dragUp = 0f
                    },
                    onDragCancel = { shareMode = false; onShareModeChanged(false); dragUp = 0f }
                )
            }
    ) {
        // Both layers always exist and cross-fade on the shared clock; only the visible one is
        // drawn, since a fully transparent graphicsLayer is skipped.
        if (morphed < 0.999f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morphed },
                contentAlignment = Alignment.Center
            ) {
                Icon(idleIcon, idleContentDesc, Modifier.size(20.dp), fg)
            }
        }
        if (morphed > 0.001f) {
            val ink = lerp(fg, Color.White, eased)
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morphed }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.IosShare,
                    stringResource(R.string.viewer_share_document),
                    Modifier
                        .size(20.dp)
                        // Overshoots to ~1.08 before settling — the icon lands with the capsule.
                        .graphicsLayer { scaleX = pop; scaleY = pop },
                    ink
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    null,
                    Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            // Bob fades out as the gesture arms: once the user is committed, the
                            // hint stops nagging and the chevron just rides up with the drag.
                            translationY = (-3f.dp.toPx() * bob) * (1f - eased) - 4f.dp.toPx() * eased
                        },
                    ink.copy(alpha = 0.4f + 0.6f * eased)
                )
            }
        }
    }
}

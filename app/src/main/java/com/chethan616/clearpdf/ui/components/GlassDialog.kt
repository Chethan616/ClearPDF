package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.Capsule
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import kotlin.math.abs

/** Backdrop the dialog panel samples, handed to [GlassDialogAction]s so the pills refract the same scene. */
private val LocalDialogBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Blur of the dialog panel, so action pills frost the scene exactly like the sheet they sit on. */
private val LocalDialogBlur = staticCompositionLocalOf { 16.dp }

/** Panel surface wash, reused by neutral action pills so they read as the same glass. */
private val LocalDialogSurface = staticCompositionLocalOf { Color.Unspecified }

/**
 * Liquid-glass alert dialog (catalog DialogContent recipe) rendered **in-hierarchy** so it can
 * refract [backdrop]. Place it as the last child of a full-screen Box, OUTSIDE the layer that records
 * into [backdrop] (a platform Dialog window could not sample the app's backdrop layer).
 *
 * Pass the screen's LIVE backdrop — [ScreenBackdrop.glass] for a [GlassScreenScaffold] screen, the
 * viewer's `contentBackdrop` for the PDF viewer / image editor. With the wallpaper off by default a
 * wallpaper-only backdrop has nothing to bend and the panel reads as a flat card.
 *
 * Entrance: scrim fades in, panel scales 0.88 -> 1 with a spring while the lens strength ramps with
 * the same progress (ControlCenter-style progressive lens). Back press and scrim tap call [onDismiss].
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    title: String?,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f, visibilityThreshold = 0.001f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.001f))
            composed = false
        }
    }
    if (!composed) return

    BackHandler(enabled = visible) { onDismiss() }

    val isLight = !LocalIsDarkMode.current
    // Theme-owned wash, dense enough that the dialog's text holds contrast over ANY page behind it
    // (a 34-50% wash let a black PDF page turn the light-theme panel charcoal under dark text, and a
    // white page turn the dark panel pale under white text). The lens rim and blur still refract the
    // live screen, so it keeps reading as glass rather than a card.
    val containerColor = glassDialogSurface(!isLight)
    val dimColor = if (isLight) Color(0xFF29293A).copy(0.16f) else Color(0xFF000000).copy(0.32f)
    val blurDp = if (isLight) 16.dp else 8.dp
    val contentColor = LiquidGlassColors.text(!isLight)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.value.fastCoerceIn(0f, 1f)
                drawRect(dimColor.copy(alpha = dimColor.alpha * p))
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible && dismissOnScrimTap,
                onClick = onDismiss
            )
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progress.value
                    val s = 0.88f + 0.12f * p
                    scaleX = s
                    scaleY = s
                    alpha = p.fastCoerceIn(0f, 1f)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        val p = progress.value.fastCoerceIn(0f, 1f)
                        colorControls(
                            brightness = if (isLight) 0.2f else 0f,
                            saturation = 1.5f
                        )
                        blur(blurDp.toPx())
                        lens(24f.dp.toPx() * p, 48f.dp.toPx() * p, depthEffect = true, chromaticAberration = true)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 24f.dp, color = Color.Black.copy(alpha = 0.12f)) },
                    onDrawSurface = { drawRect(containerColor) }
                )
                // Swallow taps so they don't reach the scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .semantics { if (title != null) paneTitle = title }
        ) {
            // Content controls (segmented choices, dropdown triggers) get the same scene as the
            // action pills, so every control in the sheet is the same liquid glass.
            CompositionLocalProvider(
                LocalDialogBackdrop provides backdrop,
                LocalDialogBlur provides blurDp,
                LocalDialogSurface provides containerColor
            ) {
                if (title != null) {
                    BasicText(
                        title,
                        Modifier.padding(28f.dp, 24f.dp, 28f.dp, 8f.dp),
                        style = TextStyle(contentColor, 22f.sp, FontWeight.SemiBold)
                    )
                } else {
                    Box(Modifier.height(16.dp))
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24f.dp, vertical = 8f.dp)) {
                    content()
                }
                Row(
                    Modifier
                        .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

/**
 * Liquid-glass capsule action for [GlassDialog]'s `actions` slot, in the vivid "Get it"
 * [LiquidButton] style: primary = [tint] (screen accent), destructive = Red, neutral = clear glass
 * frosted like the panel. Each pill refracts the same live backdrop as the dialog.
 */
@Composable
fun RowScope.GlassDialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    tint: Color = LiquidGlassColors.Blue
) {
    val isDark = LocalIsDarkMode.current
    val haptics = LocalHapticFeedback.current
    val backdrop = LocalDialogBackdrop.current ?: error("GlassDialogAction must be used inside GlassDialog")
    val pillTint = when {
        destructive -> LiquidGlassColors.Red
        primary -> tint
        else -> Color.Unspecified
    }
    val fg = if (pillTint != Color.Unspecified) Color.White else LiquidGlassColors.text(isDark)
    LiquidButton(
        onClick = {
            if (enabled) {
                haptics.performHapticFeedback(if (destructive) HapticFeedbackType.LongPress else HapticFeedbackType.ContextClick)
                onClick()
            }
        },
        backdrop = backdrop,
        modifier = modifier
            .weight(1f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        tint = pillTint,
        // Neutral pills get a SOLID theme platter, not the panel's wash: they refract the screen
        // behind the dialog (not the panel), so a translucent fill let a white page show through
        // under white ink — the blank "Cancel" pills. Primary/destructive pills are vivid tints.
        surfaceColor = if (pillTint == Color.Unspecified) glassDialogPlatter(isDark) else Color.Unspecified,
        blurRadius = LocalDialogBlur.current,
        // Three equal actions must still leave room for translated labels such as “Discard”.
        horizontalContentPadding = 8.dp
    ) {
        BasicText(
            text,
            style = TextStyle(fg, 16.sp, if (primary || destructive) FontWeight.SemiBold else FontWeight.Medium),
            maxLines = 1
        )
    }
}


// ── Dialog material, shared by every in-window glass dialog ─────────────────────────────────────

/** Panel wash for in-window dialogs: translucent, but dense enough to own its text contrast. */
fun glassDialogSurface(isDark: Boolean): Color =
    if (isDark) Color(0xFF1C1D22).copy(0.68f) else Color(0xFFFAFAFC).copy(0.70f)

/** Solid platter for neutral controls inside a dialog (buttons, fields, segment tracks). */
fun glassDialogPlatter(isDark: Boolean): Color =
    if (isDark) Color(0xFF2E3038).copy(0.94f) else Color.White.copy(0.94f)

/** Ink for content inside a dialog. Follows the app theme, never the page under the dialog. */
@Composable
fun glassDialogInk(): Color = LiquidGlassColors.text(LocalIsDarkMode.current)

@Composable
fun glassDialogInkSoft(): Color = LiquidGlassColors.secondary(LocalIsDarkMode.current)

/**
 * Text field for [GlassDialog] content: a solid rounded platter (no glass-on-glass), theme ink,
 * placeholder, optional focus requester.
 */
@Composable
fun GlassDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    minHeight: Dp = 48.dp,
    focusRequester: FocusRequester? = null
) {
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(glassDialogPlatter(isDark))
            .border(1.dp, ink.copy(alpha = if (isDark) 0.10f else 0.08f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            BasicText(
                placeholder,
                style = textStyle.merge(TextStyle(color = LiquidGlassColors.secondary(isDark))),
                maxLines = if (singleLine) 1 else Int.MAX_VALUE
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.merge(TextStyle(color = ink)),
            cursorBrush = SolidColor(LiquidGlassColors.Blue),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        )
    }
}

/**
 * Segmented choice for [GlassDialog] content, in the same liquid glass as the dialog's action pills:
 * a lensed capsule track on the solid dialog platter (so labels stay legible over any page) with a
 * vivid "Get it" glass thumb.
 *
 * The thumb is liquid: it slides on the morph spring and stretches along its travel in proportion to
 * its speed, squashing slightly in height, then wobbles back into shape as it lands — the iOS 26
 * segmented-control feel. A finger can also slide across the track; the selection follows it with a
 * tick per segment. The pressed segment's label dips like a pressed button.
 *
 * Outside a [GlassDialog] (no dialog backdrop to refract) it falls back to the solid track.
 */
@Composable
fun GlassDialogSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LiquidGlassColors.Blue
) {
    if (options.isEmpty()) return
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val haptics = LocalHapticFeedback.current
    val backdrop = LocalDialogBackdrop.current
    val blurDp = LocalDialogBlur.current
    val platter = glassDialogPlatter(isDark)
    val index = selectedIndex.coerceIn(0, options.lastIndex)
    val currentIndex by rememberUpdatedState(index)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val thumb = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) { thumb.animateTo(index.toFloat(), GlassMotion.morph()) }
    val thumbAccent by animateColorAsState(accent, GlassMotion.fade(), label = "segAccent")
    var pressed by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule },
                        effects = {
                            vibrancy()
                            blur(blurDp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(platter) }
                    )
                } else {
                    Modifier.clip(RoundedCornerShape(50)).background(platter)
                }
            )
            .pointerInput(options.size) {
                // Tap a segment, or slide across them: the selection tracks the finger.
                val count = options.size
                fun segmentAt(x: Float): Int = ((x / size.width) * count).toInt().coerceIn(0, count - 1)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var current = segmentAt(down.position.x)
                    pressed = current
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val i = segmentAt(change.position.x)
                        if (change.changedToUp()) {
                            if (i != currentIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentOnSelect(i)
                            }
                            break
                        }
                        if (i != current) {
                            current = i
                            pressed = i
                            if (i != currentIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                currentOnSelect(i)
                            }
                        }
                    }
                    pressed = -1
                }
            }
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val thumbModifier = Modifier
            .width(segment)
            .fillMaxHeight()
            .graphicsLayer {
                translationX = thumb.value * segment.toPx()
                // Stretch with speed (segments/s), capped so a fast flick reads as liquid, not a bug.
                val stretch = (abs(thumb.velocity) * 0.02f).fastCoerceIn(0f, 0.2f)
                scaleX = 1f + stretch
                scaleY = 1f - stretch * 0.35f
            }
        if (backdrop != null) {
            Box(
                thumbModifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule },
                    effects = {
                        vibrancy()
                        blur(blurDp.toPx())
                        lens(10f.dp.toPx(), 20f.dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = {
                        // LiquidButton's vivid tint recipe, so the thumb matches the primary pill.
                        drawRect(Color.White.copy(alpha = 0.42f))
                        drawRect(thumbAccent, blendMode = BlendMode.Hue)
                        drawRect(thumbAccent.copy(alpha = 0.8f))
                    }
                )
            )
        } else {
            Box(
                thumbModifier
                    .clip(RoundedCornerShape(50))
                    .background(thumbAccent)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.Transparent)))
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val sel = i == index
                val labelColor by animateColorAsState(if (sel) Color.White else ink, label = "segInk$i")
                val labelScale by animateFloatAsState(
                    if (pressed == i) GlassMotion.PressedScale else 1f,
                    GlassMotion.press(),
                    label = "segPress$i"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = sel
                            onClick(label = label) {
                                if (i != currentIndex) currentOnSelect(i)
                                true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        label,
                        modifier = Modifier.graphicsLayer {
                            scaleX = labelScale
                            scaleY = labelScale
                        },
                        style = TextStyle(labelColor, 15.sp, if (sel) FontWeight.SemiBold else FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

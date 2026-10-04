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
            CompositionLocalProvider(
                LocalDialogBackdrop provides backdrop,
                LocalDialogBlur provides blurDp,
                LocalDialogSurface provides containerColor
            ) {
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
 * Segmented choice for [GlassDialog] content: a solid capsule track with a vivid sliding thumb
 * (the "Get it" tint), springing between segments. Solid on purpose — a glass thumb inside a glass
 * panel would refract the page behind the dialog, not the panel.
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
    val index = selectedIndex.coerceIn(0, options.lastIndex)
    val thumb by animateFloatAsState(index.toFloat(), GlassMotion.morph(), label = "dialogSegment")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(glassDialogPlatter(isDark))
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        Box(
            Modifier
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { translationX = thumb * segment.toPx() }
                .clip(RoundedCornerShape(50))
                .background(accent)
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.22f), Color.Transparent)))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val sel = i == index
                val labelColor by animateColorAsState(if (sel) Color.White else ink, label = "segInk$i")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (i != index) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        label,
                        style = TextStyle(labelColor, 15.sp, if (sel) FontWeight.SemiBold else FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

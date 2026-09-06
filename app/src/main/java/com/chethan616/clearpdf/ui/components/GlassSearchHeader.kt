package com.chethan616.clearpdf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** Matches [LiquidButton]'s 48 dp capsule, so the pill and the circle share one baseline. */
private val HeaderHeight = 48.dp

/** Deliberately smaller than the 48 dp row — the search circle is a secondary affordance. */
private val HeaderCircleSize = 40.dp

/** Gap between the row's three slots. */
private val HeaderGap = 10.dp

/**
 * The search capsule is deliberately slimmer than the 48 dp row it sits in — a search field is a
 * transient input, not chrome, so it shouldn't carry the same visual weight as the title pill.
 * Shared by every search surface in the app (Home, Tools, both viewers' find bars) so there is one
 * search shape rather than three.
 */
val GlassSearchPillHeight = 36.dp

/**
 * The screen header for Home and Tools. Same trio as the PDF viewer's top bar — a leading slot, a
 * centered [GlassTitlePill], and a circular [LiquidIconButton] — so the top-level screens and the
 * viewers read as one family.
 *
 * Search is a *mode*, per Apple HIG: tapping the circle collapses the pill and opens the search
 * field, and the circle becomes a cancel affordance. Back exits. While searching, the leading slot
 * collapses to zero width and hands its space to the field, so the field runs the full row width up
 * to the cancel circle.
 *
 * A single [updateTransition] drives the pill collapse, the leading slot's width, the field's scale
 * and both icon cross-fades — one frame clock, so nothing can drift. It forks into three floats by
 * what each one is allowed to do: `progress` owns the layout width and stays critically damped (an
 * overshooting width re-measures the glass and re-runs its blur+lens at a new size, and a negative
 * one throws), `fade` owns alpha and stays critically damped (overshoot clips at 1.0 and reads as a
 * flicker), and `bounce` owns the scales and the icon spin, where overshoot is the whole point.
 */
@Composable
fun GlassSearchHeader(
    title: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    searchHint: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    titleFontFamily: FontFamily? = null,
    leading: @Composable RowScope.() -> Unit = { Box(Modifier.size(HeaderCircleSize)) }
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val transition = updateTransition(active, label = "searchHeader")
    // Owns the leading slot's real WIDTH, so it must never overshoot — a negative width throws.
    val progress by transition.animateFloat(
        transitionSpec = { GlassMotion.settle() },
        label = "searchProgress"
    ) { if (it) 1f else 0f }
    val fade by transition.animateFloat(
        transitionSpec = { GlassMotion.fade() },
        label = "searchFade"
    ) { if (it) 1f else 0f }
    // The bounce rides its own clock so it can overshoot without dragging `progress` — and therefore
    // the layout width — past 1. Everything it drives is a draw-time property, so the overshoot costs
    // a layer-matrix update, not a re-measure: `drawBackdrop` never re-runs its blur or lens.
    // Symmetric on purpose: this used to spring open with `morph()` but close with the fully
    // rigid `settle()` (no overshoot at all), on the theory that overshooting past the field's
    // floor scale on the way in would look broken. It doesn't — every use of `bounce` below either
    // clamps to [0,1] (the pill's shrink) or tolerates a few percent past its floor for a couple of
    // frames (the field's grow-in, the icon's spin) — so there was nothing actually protecting
    // against, just an animation that sprang open and then went dead on the way back, closing every
    // single time compared with opening.
    val bounce by transition.animateFloat(
        transitionSpec = { GlassMotion.morph() },
        label = "searchBounce"
    ) { if (it) 1f else 0f }

    val dismiss = {
        onQueryChange("")
        onActiveChange(false)
    }

    BackHandler(enabled = active) { dismiss() }

    LaunchedEffect(active) {
        if (active) runCatching { focusRequester.requestFocus() } else keyboard?.hide()
    }

    // Only compose the side that is at least partly visible. Once the transition settles, exactly
    // one of the two exists, so the hidden text field can never hold focus or swallow taps.
    val showTitle = fade < 0.999f
    val showField = fade > 0.001f

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Idle, this balances the trailing circle so the title pill sits dead-centre. As search
        // opens it collapses — width and its trailing gap both go to zero — so the field gets the
        // whole row. `progress` is critically damped, so the width only ever shrinks monotonically;
        // the clamp is belt-and-braces, because Modifier.width() throws on a negative value.
        val leadingWidth = (HeaderCircleSize + HeaderGap) * (1f - progress).coerceAtLeast(0f)
        Box(
            Modifier
                .width(leadingWidth)
                .graphicsLayer { alpha = 1f - fade },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, content = leading)
        }

        Box(Modifier.weight(1f).height(HeaderHeight), contentAlignment = Alignment.Center) {
            if (showTitle) {
                // The pill shrinks toward the circle as search takes over.
                Box(
                    Modifier.graphicsLayer {
                        alpha = 1f - fade
                        // Clamped: the pill only needs the eased shape, not the overshoot — it is on
                        // its way out and a rebound would fight the fade.
                        val s = lerp(1f, 0.86f, bounce.coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    GlassTitlePill(
                        text = title,
                        backdrop = backdrop,
                        onClick = onTitleClick,
                        fontFamily = titleFontFamily
                    )
                }
            }
            if (showField) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = fade
                            // Grow out of the circle: the capsule's right edge is pinned, so it
                            // unfurls leftward, overshoots past full width and springs back. The
                            // overshoot runs into the leading slot, which has collapsed by then.
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            scaleX = lerp(0.32f, 1f, bounce)
                            scaleY = lerp(0.72f, 1f, bounce)
                        }
                ) {
                    GlassSearchPill(
                        query = query,
                        onQueryChange = onQueryChange,
                        hint = searchHint,
                        backdrop = backdrop,
                        uiSensor = uiSensor,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = focusRequester,
                        onSubmit = { keyboard?.hide() },
                        // The transition above already unfurls the pill out of the search circle;
                        // its own spring-in would stack on top of that.
                        animateIn = false
                    )
                }
            }
        }

        Spacer(Modifier.width(HeaderGap))

        LiquidIconButton(
            onClick = { if (active) dismiss() else onActiveChange(true) },
            backdrop = backdrop,
            modifier = Modifier.size(HeaderCircleSize)
        ) {
            val tint = LiquidGlassColors.text(LocalIsDarkMode.current)
            // The icon spins a quarter turn as it swaps, so the circle feels like it re-purposes
            // rather than just blinking to a new glyph. On `bounce`, so it overshoots the turn in
            // step with the capsule instead of arriving ahead of it.
            Box(
                Modifier.graphicsLayer { rotationZ = lerp(0f, 90f, bounce) },
                contentAlignment = Alignment.Center
            ) {
                if (showTitle) {
                    Icon(
                        Icons.Rounded.Search,
                        stringResource(R.string.search_action),
                        Modifier.size(18.dp).graphicsLayer { alpha = 1f - fade },
                        tint
                    )
                }
                if (showField) {
                    CloseCrossIcon(Modifier.size(14.dp).graphicsLayer { alpha = fade }, tint)
                }
            }
        }
    }
}

/**
 * The app's one and only search capsule — a 36 dp glass pill carrying [GlassTitlePill]'s typography
 * (13 sp SemiBold), surface tint and spring-in, so a search field reads as the same material as the
 * title pill, just slimmer. Mirrors [LiquidGlassTopBar]'s effect stack; it exists as a separate
 * composable rather than a slot on [LiquidGlassTopBar] because the LiquidGlass* components are not
 * to be modified.
 *
 * Used by [GlassSearchHeader] (Home, Tools) and by the viewers' find bar, so all four search
 * surfaces are literally the same widget.
 *
 * @param animateIn set `false` when a caller already animates the pill in (e.g. [GlassSearchHeader]
 *   grows it out of the search circle) — otherwise the two entrances stack.
 * @param viewerChrome set `true` inside the viewers, where the pill wears [viewerGlass]'s lighter
 *   stack so it matches the top bar's title pill instead of the app-chrome one Home and Tools use.
 * @param trailing an optional slot before the clear button, e.g. the find bar's "3 / 12" counter.
 */
@Composable
fun GlassSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
    animateIn: Boolean = true,
    viewerChrome: Boolean = false,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    hintColor: Color = Color.Unspecified,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val isDarkMode = LocalIsDarkMode.current
    val isLightTheme = !isDarkMode
    // Same expression GlassTitlePill resolves, so the pill and the field are one material.
    val containerColor = if (surfaceColor.isSpecified) surfaceColor
        else if (isLightTheme) Color(0xFFFAFAFA).copy(0.35f) else Color(0xFF1E1E1E).copy(0.35f)
    val text = if (contentColor.isSpecified) contentColor else LiquidGlassColors.text(isDarkMode)
    val sub = if (hintColor.isSpecified) hintColor else LiquidGlassColors.secondary(isDarkMode)

    // The title pill's entrance, verbatim — a soft, critically-damped settle. Read inside a
    // graphicsLayer so it only invalidates draw, never composition.
    var shown by remember { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) { shown = true }
    val enterScale by animateFloatAsState(
        if (shown) 1f else 0.9f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "searchPillScale"
    )
    val enterAlpha by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "searchPillAlpha"
    )

    Row(
        modifier
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
                alpha = enterAlpha
            }
            // Two stacks, one shape. In the viewers the pill has to be the same material as the top
            // bar's title pill, which is a plain LiquidButton — lighter blur, shallower lens, and the
            // library's default highlight/shadow rather than the gravity-angled app-chrome ones.
            .then(
                if (viewerChrome) {
                    Modifier.viewerGlass(backdrop, containerColor, shape = { Capsule })
                } else {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(16f.dp.toPx(), 32f.dp.toPx())
                        },
                        highlight = {
                            Highlight(style = HighlightStyle.Default(angle = uiSensor.gravityAngle, falloff = 2f))
                        },
                        shadow = { Shadow(radius = 6f.dp, color = Color.Black.copy(alpha = 0.08f)) },
                        innerShadow = { InnerShadow(radius = 2f.dp, alpha = 0.25f) },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                }
            )
            .height(GlassSearchPillHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(15.dp), sub.copy(0.9f))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                // Portuguese hints run ~1.3x longer than English — clip rather than wrap out of a
                // 36 dp capsule.
                BasicText(
                    hint,
                    style = TextStyle(sub.copy(0.75f), 13.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(text, 13.sp, FontWeight.SemiBold),
                cursorBrush = SolidColor(LiquidGlassColors.Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
        trailing?.invoke(this)
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(sub.copy(0.30f))
                    .clickable(interactionSource = null, indication = null) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                CloseCrossIcon(Modifier.size(10.dp), if (isLightTheme) Color.White else Color.Black.copy(0.75f))
            }
        }
    }
}

/**
 * An uppercase section label, iOS grouped-list style. Sits above a glass panel rather than inside
 * it, so the panel stays a single uninterrupted glass surface.
 */
@Composable
fun GlassSectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text.uppercase(),
        style = TextStyle(
            // Full-weight ink (black in light, white in dark) rather than the grouped-list grey —
            // the Tools section headers (Organize / Convert / Edit / Optimize / Secure) read as
            // proper titles this way instead of fading into the wallpaper.
            color = LiquidGlassColors.text(LocalIsDarkMode.current),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

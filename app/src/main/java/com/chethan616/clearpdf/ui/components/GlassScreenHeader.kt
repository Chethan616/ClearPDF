package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** The chrome row's height — [LiquidButton]'s hardcoded 48 dp capsule, so the pill fills it exactly. */
val GlassHeaderHeight = 48.dp

/** Breathing room between the pinned header and the first thing that scrolls under it. */
private val DefaultHeaderGap = 12.dp

/**
 * The app-wide screen shell: content in a captured layer, chrome floating **above** it.
 *
 * Every screen used to stack its header and its body in a [androidx.compose.foundation.layout.Column],
 * which meant the two occupied disjoint bounds — the header could never be overlapped, but content
 * could never flow under it either, and on Home/Tools the header lived inside the `LazyColumn` and
 * simply scrolled away. Only the PDF viewer got it right. This extracts that structure:
 *
 * 1. [content] is composed first, inside a `Box` captured into its own [rememberLayerBackdrop].
 * 2. [header] is composed last, so it draws on top of everything the content puts on screen.
 * 3. The header is handed `wallpaper + live content` as its backdrop, so its glass refracts the cards
 *    actually sliding under it rather than a frozen wallpaper. The header sits OUTSIDE the captured
 *    Box, so there is no glass-on-glass feedback loop; panels *inside* [content] keep sampling the
 *    wallpaper [backdrop] as before.
 *
 * [content] receives a [PaddingValues] that already clears the status bar, the header and the
 * navigation bar — feed it straight into a `LazyColumn`'s `contentPadding`, or into
 * `Modifier.padding(...)` for a `verticalScroll` body.
 */
@Composable
fun GlassScreenScaffold(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: Dp = GlassDimens.ScreenPadding,
    contentBottomPadding: Dp = 0.dp,
    headerHorizontalPadding: Dp = GlassDimens.ScreenPadding,
    headerGap: Dp = DefaultHeaderGap,
    header: @Composable (headerBackdrop: Backdrop) -> Unit,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit
) {
    val contentBackdrop = rememberLayerBackdrop()
    // Wallpaper first, live content composited over it — the content layer is transparent wherever
    // no card is drawn, so the header still refracts the wallpaper in the gaps.
    val headerBackdrop = rememberCombinedBackdrop(backdrop, contentBackdrop)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val contentPadding = PaddingValues(
        start = contentHorizontalPadding,
        end = contentHorizontalPadding,
        top = statusTop + GlassHeaderHeight + headerGap,
        bottom = navBottom + contentBottomPadding
    )

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
            content(contentPadding)
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = headerHorizontalPadding)
                .height(GlassHeaderHeight),
            contentAlignment = Alignment.Center
        ) {
            header(headerBackdrop)
        }
    }
}

/**
 * The header trio every screen hand-rolled: back circle · centred [GlassTitlePill] · trailing action.
 *
 * Pass `onBack = null` for a root screen (Settings, Scan) — the slot becomes a spacer so the pill
 * stays optically centred. Same for [trailing].
 */
@Composable
fun GlassScreenHeaderRow(
    title: String,
    backdrop: Backdrop,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleFontFamily: FontFamily? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val text = LiquidGlassColors.text(LocalIsDarkMode.current)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            LiquidIconButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            GlassTitlePill(text = title, backdrop = backdrop, fontFamily = titleFontFamily)
        }
        if (trailing != null) trailing() else Spacer(Modifier.size(40.dp))
    }
}

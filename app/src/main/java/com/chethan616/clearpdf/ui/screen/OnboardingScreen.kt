package com.chethan616.clearpdf.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.ui.components.DemoAnnotate
import com.chethan616.clearpdf.ui.components.DemoDocumentOpen
import com.chethan616.clearpdf.ui.components.DemoFileKinds
import com.chethan616.clearpdf.ui.components.DemoReady
import com.chethan616.clearpdf.ui.components.DemoSearch
import com.chethan616.clearpdf.ui.components.DemoToolsMenu
import com.chethan616.clearpdf.ui.components.GlassMenuAction
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.components.viewerChromeGlass
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

private const val PageCount = 6

/**
 * First-run tour. Six pages, each explaining a feature by **replaying the app's own animation** for
 * it rather than showing a picture of it — see `OnboardingDemos.kt`.
 *
 * Two structural decisions drive the layout:
 *
 * **The chrome lives outside the pager.** `HorizontalPager` positions pages by layout offset, so a
 * glass surface inside a page moves relative to its backdrop on every frame of a swipe, and
 * `drawBackdrop` re-runs its blur and lens each time. Keeping the CTA, the dots and Skip stationary
 * means a swipe only ever drags the one glass surface a page genuinely needs.
 *
 * **Only the current page animates.** The pager composes its neighbours ahead of time, so each demo
 * is gated on `isActive` — otherwise all five loops run at once, off-screen, for the whole flow.
 *
 * The language page changes [selectedLocale] *in place*: the caller re-provides `LocalResources`, so
 * every `stringResource` below re-resolves and the remaining pages translate without an Activity
 * restart. That only holds while this file avoids `context.getString`, which it does.
 *
 * The appearance page is the same idea one step further — [themeMode] and [showWallpaper] are the
 * app's real hoisted state, so changing them here re-tints the tour itself as you tap. That is the
 * whole point of putting the page here rather than in Settings: the glass is the product, and the
 * fastest way to teach it is to let the user watch it react.
 */
@Composable
fun OnboardingScreen(
    backdrop: LayerBackdrop,
    selectedLocale: String,
    onLocaleSelected: (String) -> Unit,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    showWallpaper: Boolean,
    onShowWallpaperChanged: (Boolean) -> Unit,
    hasCustomWallpaper: Boolean,
    onCustomWallpaperChanged: (String?) -> Unit,
    onFinish: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val uiSensor = rememberUISensor()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PageCount })

    val ink = LiquidGlassColors.text(isDark)
    val inkSoft = LiquidGlassColors.secondary(isDark)
    // Higher alpha than the viewers' chrome tint: onboarding floats over the wallpaper rather than
    // over a document, and at this recipe's 2 dp blur the wallpaper comes through nearly sharp.
    val glass = if (isDark) Color(0xFF20242C).copy(0.80f) else Color.White.copy(0.72f)

    val last = pagerState.currentPage == PageCount - 1

    // Back steps through the flow. Deliberately DISABLED on page one rather than consumed there: on
    // a first run that leaves the system default (exit the app, tour returns next launch), and on a
    // replay from Settings it falls through to the nav host and returns to Settings. Consuming it
    // would make back silently dead on the first page in both cases.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    val advance: () -> Unit = {
        if (last) {
            onFinish()
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isActive = pagerState.currentPage == page
            OnboardingPage(
                page = page,
                isActive = isActive,
                backdrop = backdrop,
                uiSensor = uiSensor,
                glass = glass,
                ink = ink,
                inkSoft = inkSoft,
                selectedLocale = selectedLocale,
                onLocaleSelected = onLocaleSelected,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                showWallpaper = showWallpaper,
                onShowWallpaperChanged = onShowWallpaperChanged,
                hasCustomWallpaper = hasCustomWallpaper,
                onCustomWallpaperChanged = onCustomWallpaperChanged,
                isDark = isDark
            )
        }

        // ── Stationary chrome ───────────────────────────────────────────────────────────────────
        // Skip fades out on the last page instead of being removed, so the row above the pager does
        // not reflow underneath the swipe.
        val skipAlpha by animateFloatAsState(
            if (last) 0f else 1f,
            tween(220, easing = FastOutSlowInEasing),
            label = "onboardingSkipAlpha"
        )
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .graphicsLayer { alpha = skipAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!last) {
                LiquidButton(
                    onClick = onFinish,
                    backdrop = backdrop,
                    // The viewers' chrome tint, not the page's `glass`. Both are the same minimal
                    // recipe, so the difference is purely opacity: at 0.80/0.72 the panels behind
                    // the demos need to hold small text, but a bare Skip capsule at that weight
                    // reads as a painted pill — the lens has nothing to refract. Dropping to the
                    // viewers' 0.70/0.55 is what makes it look like the buttons it is quoting.
                    surfaceColor = viewerChromeGlass(isDark)
                ) {
                    BasicText(
                        stringResource(R.string.onboarding_skip),
                        style = TextStyle(ink, 14.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            PageDots(current = pagerState.currentPage, count = PageCount, ink = ink)

            // `ink`, not `inkSoft`. Everything in this Column sits directly on the wallpaper with no
            // glass under it, and the secondary token is a mid grey that has to survive on whatever
            // photo is behind it. Full-strength ink (near-white on dark, near-black on light) is the
            // only value that holds in both, so the hierarchy is carried by size and weight instead.
            BasicText(
                stringResource(R.string.onboarding_step, pagerState.currentPage + 1, PageCount),
                style = TextStyle(ink.copy(0.8f), 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )

            LiquidButton(
                onClick = advance,
                backdrop = backdrop,
                tint = Color(0xFF0088FF),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Cross-faded rather than swapped: the label changes on the last page and a hard
                // swap inside a capsule that is not resizing reads as a glitch.
                val ctaProgress by animateFloatAsState(
                    if (last) 1f else 0f,
                    GlassMotion.fade(),
                    label = "onboardingCta"
                )
                Box(contentAlignment = Alignment.Center) {
                    if (ctaProgress < 0.999f) {
                        BasicText(
                            stringResource(R.string.onboarding_cta_continue),
                            style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = 1f - ctaProgress }
                        )
                    }
                    if (ctaProgress > 0.001f) {
                        BasicText(
                            stringResource(R.string.onboarding_cta_get_started),
                            style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = ctaProgress }
                        )
                    }
                }
            }
        }
    }
}

/** Flat pills — no glass — so the active one can stretch without costing a blur pass. */
@Composable
private fun PageDots(current: Int, count: Int, ink: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { i ->
            val active = i == current
            val w by animateFloatAsState(
                if (active) 22f else 7f,
                GlassMotion.settle(),
                label = "dotWidth$i"
            )
            val a by animateFloatAsState(
                if (active) 0.9f else 0.28f,
                GlassMotion.fade(),
                label = "dotAlpha$i"
            )
            Box(
                Modifier
                    .width(w.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(ink.copy(a))
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    isActive: Boolean,
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    glass: Color,
    ink: Color,
    inkSoft: Color,
    selectedLocale: String,
    onLocaleSelected: (String) -> Unit,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    showWallpaper: Boolean,
    onShowWallpaperChanged: (Boolean) -> Unit,
    hasCustomWallpaper: Boolean,
    onCustomWallpaperChanged: (String?) -> Unit,
    isDark: Boolean
) {
    val title: String
    val subtitle: String
    when (page) {
        0 -> { title = stringResource(R.string.onboarding_welcome_title); subtitle = stringResource(R.string.onboarding_welcome_subtitle) }
        1 -> { title = stringResource(R.string.onboarding_language_title); subtitle = stringResource(R.string.onboarding_language_subtitle) }
        2 -> { title = stringResource(R.string.onboarding_appearance_title); subtitle = stringResource(R.string.onboarding_appearance_subtitle) }
        3 -> { title = stringResource(R.string.onboarding_files_title); subtitle = stringResource(R.string.onboarding_files_subtitle) }
        4 -> { title = stringResource(R.string.onboarding_features_title); subtitle = stringResource(R.string.onboarding_features_subtitle) }
        else -> { title = stringResource(R.string.onboarding_ready_title); subtitle = stringResource(R.string.onboarding_ready_subtitle) }
    }

    // Copy rises in behind the demo on every visit, so paging back and forth feels alive rather than
    // landing on a static slab. Draw-time properties only.
    val enter by animateFloatAsState(
        if (isActive) 1f else 0f,
        tween(360, easing = FastOutSlowInEasing),
        label = "pageEnter"
    )
    val density = LocalDensity.current.density

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // Top clears the Skip capsule; bottom clears the dots + step counter + CTA.
            .padding(horizontal = 32.dp)
            .padding(top = 56.dp, bottom = 170.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (page) {
                0 -> DemoDocumentOpen(isActive, backdrop, glass, ink)
                1 -> LanguageChooser(backdrop, glass, ink, inkSoft, selectedLocale, onLocaleSelected, isDark)
                2 -> AppearanceChooser(
                    backdrop, glass, ink, inkSoft, isDark,
                    themeMode, onThemeModeChanged, showWallpaper, onShowWallpaperChanged,
                    hasCustomWallpaper, onCustomWallpaperChanged
                )
                3 -> DemoFileKinds(isActive, backdrop, glass, ink, inkSoft)
                4 -> FeatureRows(isActive, backdrop, uiSensor, glass, ink, inkSoft)
                else -> DemoReady(isActive, backdrop, glass)
            }
        }

        Spacer(Modifier.height(28.dp))

        Column(
            Modifier.graphicsLayer {
                alpha = enter
                translationY = lerp(16f, 0f, enter) * density
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BasicText(
                title,
                style = TextStyle(ink, 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            )
            // Also `ink` — see the step counter's note. This block is the largest run of text in the
            // app with nothing but wallpaper behind it.
            BasicText(
                subtitle,
                style = TextStyle(ink.copy(0.86f), 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
            )
            if (page == PageCount - 1) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .viewerGlass(backdrop, glass, shape = { Capsule })
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        stringResource(R.string.onboarding_privacy_badge),
                        style = TextStyle(inkSoft, 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

/**
 * Page 2 is the only interactive one — the real segmented control from Settings, not a replay.
 *
 * Selecting here does **not** restart the Activity; the caller updates the hoisted locale state and
 * re-provides `LocalResources`, so this whole screen re-composes translated. The restart, if the
 * choice actually changed anything, is deferred to "Get Started".
 */
@Composable
private fun LanguageChooser(
    backdrop: LayerBackdrop,
    glass: Color,
    ink: Color,
    inkSoft: Color,
    selectedLocale: String,
    onLocaleSelected: (String) -> Unit,
    isDark: Boolean
) {
    val accent = Color(0xFF0088FF)
    Column(
        Modifier
            .fillMaxWidth()
            .viewerGlass(backdrop, glass)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BasicText(
            stringResource(R.string.onboarding_language_label),
            style = TextStyle(inkSoft, 13.sp, fontWeight = FontWeight.SemiBold)
        )
        listOf(
            "en" to R.string.language_english,
            "pt-BR" to R.string.language_portuguese,
            "es" to R.string.language_spanish
        ).forEach { (code, res) ->
            val selected = selectedLocale == code
            LiquidButton(
                onClick = { onLocaleSelected(code) },
                backdrop = backdrop,
                tint = if (selected) accent else Color.Unspecified,
                surfaceColor = if (selected) Color.Unspecified else (if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    stringResource(res),
                    style = TextStyle(
                        if (selected) Color.White else ink,
                        15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Page 3 — theme and background, the second interactive page.
 *
 * Both controls write straight through to the app's real hoisted state, so the tour re-themes under
 * the user's finger: the panel they just tapped is itself glass over the wallpaper, which makes this
 * the one place in the app where the setting and its preview are the same pixels. Turning the
 * background off here is also the honest way to show what that does — the demos on the later pages
 * are the exact surfaces affected.
 *
 * Mirrors `SettingsScreen`'s appearance section (`:279-320`, `:578-587`) rather than sharing code
 * with it: that section is embedded in a scrolling settings layout with its own panel chrome and
 * cascade offsets, and lifting it out would mean parameterising it for two very different hosts.
 */
@Composable
private fun AppearanceChooser(
    backdrop: LayerBackdrop,
    glass: Color,
    ink: Color,
    inkSoft: Color,
    isDark: Boolean,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    showWallpaper: Boolean,
    onShowWallpaperChanged: (Boolean) -> Unit,
    hasCustomWallpaper: Boolean,
    onCustomWallpaperChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    // Same contract Settings uses: `OpenDocument` plus a persisted read grant, because the chosen
    // image has to survive the restart that finishing the tour may trigger.
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            AppSettingsManager.setCustomWallpaper(context, uri.toString())
            onCustomWallpaperChanged(uri.toString())
        }
    }

    data class ThemeOption(val idx: Int, val label: String, val icon: ImageVector, val accent: Color)
    val options = listOf(
        ThemeOption(0, stringResource(R.string.settings_theme_auto), Icons.Rounded.PhoneAndroid, Color(0xFF0088FF)),
        ThemeOption(1, stringResource(R.string.settings_theme_light), Icons.Rounded.LightMode, Color(0xFFFFA726)),
        ThemeOption(2, stringResource(R.string.settings_theme_dark), Icons.Rounded.DarkMode, Color(0xFF7C4DFF))
    )

    // Tighter than the language page's 20/14 on purpose. This panel now carries six rows, and the
    // demo area it sits in is a `weight(1f)` box between fixed top and bottom padding — around
    // 275 dp on a 640 dp-tall phone. At 20/14 the gallery row pushed it past that and the panel
    // clipped rather than the page scrolling.
    Column(
        Modifier
            .fillMaxWidth()
            .viewerGlass(backdrop, glass)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            stringResource(R.string.settings_appearance),
            style = TextStyle(inkSoft, 13.sp, fontWeight = FontWeight.SemiBold)
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val selected = themeMode == option.idx
                val cc = if (selected) Color.White else ink
                LiquidButton(
                    onClick = { onThemeModeChanged(option.idx) },
                    backdrop = backdrop,
                    tint = if (selected) option.accent else Color.Unspecified,
                    surfaceColor = if (selected) Color.Unspecified else (if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f)),
                    modifier = Modifier.weight(1f)
                ) {
                    // Icon above label rather than beside it: three segments across a 32dp-inset page
                    // leaves ~80dp each, and "Escuro"/"Claro" next to a 16dp icon clips in pt-BR.
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(option.icon, null, Modifier.size(18.dp), cc)
                        BasicText(
                            option.label,
                            style = TextStyle(cc, 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        BasicText(
            when (themeMode) {
                1 -> stringResource(R.string.settings_theme_light_desc)
                2 -> stringResource(R.string.settings_theme_dark_desc)
                else -> stringResource(R.string.settings_theme_auto_desc)
            },
            style = TextStyle(inkSoft.copy(0.8f), 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(0.10f)))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Wallpaper, null, Modifier.size(20.dp), inkSoft)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    stringResource(R.string.settings_background),
                    style = TextStyle(ink, 14.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    stringResource(R.string.settings_background_desc),
                    style = TextStyle(inkSoft, 12.sp, lineHeight = 16.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LiquidToggle(
                selected = { showWallpaper },
                onSelect = onShowWallpaperChanged,
                backdrop = backdrop
            )
        }

        // Only while the background is actually on, and only one control wide — the brief was "don't
        // make it too cluttered", and a picker for a background that is switched off is a row the
        // user has to read past to find out it does nothing. Reset joins it only once there is
        // something to reset, so the common case stays a single button.
        if (showWallpaper) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                    backdrop = backdrop,
                    surfaceColor = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(15.dp), ink)
                        BasicText(
                            stringResource(R.string.settings_bg_gallery),
                            style = TextStyle(ink, 13.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (hasCustomWallpaper) {
                    LiquidIconButton(
                        onClick = {
                            AppSettingsManager.clearCustomWallpaper(context)
                            onCustomWallpaperChanged(null)
                        },
                        backdrop = backdrop,
                        surfaceColor = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.settings_reset), Modifier.size(17.dp), inkSoft)
                    }
                }
            }
        }
    }
}

/**
 * Page 5: the three headline features. The first two are compact rows with a micro-demo in their
 * leading slot; the third is a full-width showcase.
 *
 * **Why the third one breaks the row shape.** `GlassCapsuleMenu` is a horizontal `Row` of 40dp
 * circles — 152dp wide with three actions. A 72dp leading slot constrains it to 72dp, and a `Row`
 * that runs out of width does not wrap or shrink, it measures the overflow at zero: the demo
 * rendered as a lone Merge button with the other two actions silently gone. Scaling it down with a
 * `graphicsLayer` would not have fixed the measurement either, and would have composited the glass
 * at less than half size so its blur and lens read at the wrong scale. Giving it the panel's full
 * width instead lets the real component measure at its real size, which is the only way this demo is
 * worth showing at all — it is the one production animation onboarding plays literally.
 *
 * All of it shares one glass panel: four separate glass surfaces would cost four blur passes for no
 * visual gain, the same reasoning `GlassCapsuleMenu` and `ToolSectionPanel` already follow.
 */
@Composable
private fun FeatureRows(
    isActive: Boolean,
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    glass: Color,
    ink: Color,
    inkSoft: Color
) {
    val toolActions = remember {
        listOf(
            GlassMenuAction(Icons.AutoMirrored.Rounded.MergeType, "Merge", LiquidGlassColors.Red) {},
            GlassMenuAction(Icons.AutoMirrored.Rounded.CallSplit, "Split", LiquidGlassColors.Purple) {},
            GlassMenuAction(Icons.Rounded.Compress, "Compress", LiquidGlassColors.Green) {}
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .viewerGlass(backdrop, glass)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeatureRow(
            title = stringResource(R.string.feature_annotate_title),
            desc = stringResource(R.string.feature_annotate_desc),
            ink = ink, inkSoft = inkSoft
        ) { DemoAnnotate(isActive, ink) }

        FeatureRow(
            title = stringResource(R.string.feature_search_title),
            desc = stringResource(R.string.feature_search_desc),
            ink = ink, inkSoft = inkSoft
        ) { DemoSearch(isActive, backdrop, glass, ink) }

        // A hairline before the shape changes, so the wider third entry reads as deliberate emphasis
        // rather than a row that failed to line up with the two above it.
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(0.10f)))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                stringResource(R.string.feature_tools_title),
                style = TextStyle(ink, 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                stringResource(R.string.feature_tools_desc),
                style = TextStyle(inkSoft, 12.5.sp, lineHeight = 17.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            DemoToolsMenu(isActive, backdrop, uiSensor, glass, toolActions)
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    desc: String,
    ink: Color,
    inkSoft: Color,
    demo: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        demo()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                title,
                style = TextStyle(ink, 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                desc,
                style = TextStyle(inkSoft, 12.5.sp, lineHeight = 17.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

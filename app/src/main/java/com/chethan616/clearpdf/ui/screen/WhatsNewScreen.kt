package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.AccentIconTile
import com.chethan616.clearpdf.ui.components.GlassMotion
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class WhatsNewItem(val icon: ImageVector, val accent: Color, val title: Int, val desc: Int)

private val WhatsNewItems = listOf(
    WhatsNewItem(Icons.Rounded.EditNote, LiquidGlassColors.Blue, R.string.whats_new_edit_title, R.string.whats_new_edit_desc),
    WhatsNewItem(Icons.Rounded.AutoAwesome, LiquidGlassColors.Teal, R.string.whats_new_glass_title, R.string.whats_new_glass_desc),
    WhatsNewItem(Icons.Rounded.IosShare, LiquidGlassColors.Green, R.string.whats_new_share_title, R.string.whats_new_share_desc),
    WhatsNewItem(Icons.Rounded.Vibration, LiquidGlassColors.Purple, R.string.whats_new_feel_title, R.string.whats_new_feel_desc),
    WhatsNewItem(Icons.Rounded.TableChart, LiquidGlassColors.Orange, R.string.whats_new_sheets_title, R.string.whats_new_sheets_desc),
    WhatsNewItem(Icons.Rounded.BugReport, LiquidGlassColors.Red, R.string.whats_new_fixes_title, R.string.whats_new_fixes_desc)
)

/**
 * The once-per-update "What's new" page, iOS style: a hero badge, a big title and the version, then
 * one glass panel listing the changes and a vivid Continue. It sits on the wallpaper like onboarding,
 * so it uses the same ink and glass recipe. Back counts as Continue, so the page never comes back
 * for an update the user has already dismissed.
 */
@Composable
fun WhatsNewScreen(
    backdrop: LayerBackdrop,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    // Full-strength ink: the header sits straight on the wallpaper (see OnboardingScreen).
    val ink = LiquidGlassColors.text(isDark)
    val inkSoft = LiquidGlassColors.secondary(isDark)
    val glass = if (isDark) Color(0xFF20242C).copy(0.80f) else Color.White.copy(0.72f)
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val density = LocalDensity.current.density

    BackHandler(onBack = onContinue)

    // One progress per element; scale, rise and alpha all derive from it.
    val hero = remember { Animatable(0f) }
    val header = remember { Animatable(0f) }
    val panel = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60)
        coroutineScope {
            launch { hero.animateTo(1f, GlassMotion.pop()) }
            delay(70)
            launch { header.animateTo(1f, GlassMotion.morph()) }
            delay(70)
            launch { panel.animateTo(1f, GlassMotion.settle()) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                // Bottom clears the Continue button.
                .padding(top = 48.dp, bottom = 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.graphicsLayer {
                    val e = hero.value
                    alpha = e.coerceIn(0f, 1f)
                    val s = 0.6f + 0.4f * e
                    scaleX = s; scaleY = s
                }
            ) {
                AccentIconTile(Icons.Rounded.AutoAwesome, LiquidGlassColors.Blue, size = 72, iconSize = 38)
            }

            Spacer(Modifier.height(20.dp))

            Column(
                Modifier.graphicsLayer {
                    val e = header.value
                    alpha = e.coerceIn(0f, 1f)
                    translationY = (1f - e) * 16f * density
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BasicText(
                    stringResource(R.string.whats_new_title),
                    style = TextStyle(ink, 30.sp, FontWeight.Bold, textAlign = TextAlign.Center)
                )
                // Flat text, not a glass chip: this whole block rises in, and glass must not move.
                if (versionName.isNotEmpty()) {
                    BasicText(
                        stringResource(R.string.settings_version_label) + " " + versionName,
                        style = TextStyle(ink.copy(0.8f), 13.sp, FontWeight.SemiBold)
                    )
                }
                BasicText(
                    stringResource(R.string.whats_new_subtitle),
                    style = TextStyle(ink.copy(0.86f), 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Glass fades in place (never translates); the rows inside it are flat and rise in turn.
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = panel.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .viewerGlass(backdrop, glass)
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                WhatsNewItems.forEachIndexed { i, item -> WhatsNewRow(item, i, ink, inkSoft) }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp)
        ) {
            LiquidButton(
                onClick = onContinue,
                backdrop = backdrop,
                tint = LiquidGlassColors.Blue,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    stringResource(R.string.whats_new_continue),
                    style = TextStyle(Color.White, 16.sp, FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WhatsNewRow(item: WhatsNewItem, index: Int, ink: Color, inkSoft: Color) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(260L + 30L * index)
        enter.animateTo(1f, GlassMotion.pop())
    }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * lift
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AccentIconTile(item.icon, item.accent, size = 44, iconSize = 22)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(stringResource(item.title), style = TextStyle(ink, 15.sp, FontWeight.SemiBold))
            BasicText(stringResource(item.desc), style = TextStyle(inkSoft, 12.5.sp, lineHeight = 17.sp))
        }
    }
}

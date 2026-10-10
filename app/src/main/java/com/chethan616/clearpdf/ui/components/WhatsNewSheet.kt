package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay

/** Whether the What's New sheet is up — first-use tutorials wait until it has been dismissed. */
object WhatsNewGate {
    var showing by mutableStateOf(false)
}

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
 * The once-per-update "What's new" sheet, on the app's glass dialog: a springy entrance, then the
 * feature rows land one after another (~45 ms apart, a soft overshoot each).
 */
@Composable
fun WhatsNewSheet(
    visible: Boolean,
    versionName: String,
    backdrop: Backdrop,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val ink = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    GlassDialog(
        visible = visible,
        onDismiss = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.whats_new_title, versionName),
        actions = {
            GlassDialogAction(stringResource(R.string.whats_new_continue), onDismiss, primary = true)
        }
    ) {
        BasicText(stringResource(R.string.whats_new_subtitle), style = TextStyle(sub, 13.sp))
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            WhatsNewItems.forEachIndexed { i, item -> WhatsNewRow(item, i, ink, sub) }
        }
    }
}

@Composable
private fun WhatsNewRow(item: WhatsNewItem, index: Int, ink: Color, sub: Color) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(180L + 45L * index)
        enter.animateTo(1f, GlassMotion.pop())
    }
    val lift = with(LocalDensity.current) { 12.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val e = enter.value
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * lift
                val s = 0.92f + 0.08f * e
                scaleX = s; scaleY = s
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccentIconTile(item.icon, item.accent, size = 38, iconSize = 20)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(stringResource(item.title), style = TextStyle(ink, 15.sp, FontWeight.SemiBold))
            BasicText(stringResource(item.desc), style = TextStyle(sub, 12.5.sp, lineHeight = 16.sp))
        }
    }
}

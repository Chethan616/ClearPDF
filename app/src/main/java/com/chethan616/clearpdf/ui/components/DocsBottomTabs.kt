package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.R
import com.kyant.backdrop.Backdrop

/**
 * Icon + label, the same richness the xlsx sheet-tab dock has (a name, not just a glyph) instead of
 * three bare icons — the tab a visitor is actually ON used to be legible only by which capsule the
 * slider sat under, not by anything the tab itself said.
 */
@Composable
fun DocsBottomTabs(
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isDarkMode = LocalIsDarkMode.current
    val sub = LiquidGlassColors.secondary(isDarkMode)
    val accent = LiquidGlassColors.Blue

    // The selected pill is a light sheen + a whisper of accent (see LiquidBottomTabs'
    // onDrawSurface) in BOTH themes, not a solid fill, so its ink is the accent colour itself —
    // the same convention the xlsx sheet tabs use for their own selected label.
    @Composable
    fun tabColor(index: Int): Color = if (selectedTab() == index) accent else sub

    LiquidBottomTabs(
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
        backdrop = backdrop,
        tabsCount = 3,
        modifier = modifier
    ) {
        val tabs = listOf(
            Triple(Icons.Rounded.Home, R.string.nav_home, 0),
            Triple(Icons.Rounded.GridView, R.string.nav_tools, 1),
            Triple(Icons.Rounded.Tune, R.string.nav_settings, 2)
        )
        tabs.forEach { (icon, labelRes, index) ->
            val label = stringResource(labelRes)
            LiquidBottomTab(onClick = { onTabSelected(index) }) {
                // Ink stays close to the selected pill's own light-sheen + accent-wash surface
                // (see LiquidBottomTabs' onDrawSurface), so it reads against either theme without
                // needing a per-tab recomposition when the selection moves.
                Icon(icon, contentDescription = null, tint = tabColor(index), modifier = Modifier.size(20.dp))
                BasicText(
                    label,
                    style = TextStyle(
                        tabColor(index),
                        10.sp,
                        if (selectedTab() == index) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

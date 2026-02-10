package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
fun DocsBottomTabs(
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLight = !isSystemInDarkTheme()
    val tint = if (isLight) Color(0xFF444444) else Color(0xFFCCCCCC)

    LiquidBottomTabs(
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
        backdrop = backdrop,
        tabsCount = 3,
        modifier = modifier
    ) {
        LiquidBottomTab(onClick = { onTabSelected(0) }) {
            Icon(Icons.Rounded.Home, contentDescription = "Home", tint = tint,
                modifier = Modifier.size(22.dp))
        }

        LiquidBottomTab(onClick = { onTabSelected(1) }) {
            Icon(Icons.Rounded.Build, contentDescription = "Tools", tint = tint,
                modifier = Modifier.size(22.dp))
        }

        LiquidBottomTab(onClick = { onTabSelected(2) }) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = tint,
                modifier = Modifier.size(22.dp))
        }
    }
}

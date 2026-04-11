package com.chethan616.clearpdf.ui

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.ui.components.DocsBottomTabs
import com.chethan616.clearpdf.ui.navigation.DocsNavGraph
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * Root composable for the Docs app.
 * Provides the wallpaper backdrop, bottom tabs, and navigation host.
 */
@Composable
fun DocsApp(shortcutRoute: String? = null, incomingPdfUri: android.net.Uri? = null) {
    val context = LocalContext.current
    var themeMode by rememberSaveable { mutableIntStateOf(AppSettingsManager.getThemeMode(context)) }
    var showStarPrompt by rememberSaveable { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val isDarkMode = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    LaunchedEffect(context) {
        showStarPrompt = GitHubStarPromptManager.shouldShowPrompt(context)
        StarPromptEventBus.promptRequests.collectLatest {
            if (GitHubStarPromptManager.shouldShowPrompt(context)) {
                showStarPrompt = true
            }
        }
    }

    val openRepo = remember(context) {
        {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GitHubStarPromptManager.REPO_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        var painter: Painter? by remember { mutableStateOf(null) }

        val backdrop = rememberLayerBackdrop()
        val navController = rememberNavController()
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val showBottomTabs = currentRoute == "home" || currentRoute == "tools" || currentRoute == "settings"
        val onBottomTabSelected: (Int) -> Unit = remember(navController) {
            { index ->
                selectedTab = index
                when (index) {
                    0 -> navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                        launchSingleTop = true
                    }

                    1 -> navController.navigate("tools") {
                        popUpTo("home")
                        launchSingleTop = true
                    }

                    2 -> navController.navigate("settings") {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                }
            }
        }

        // Handle app shortcut deep links
        LaunchedEffect(shortcutRoute) {
            if (shortcutRoute != null) {
                navController.navigate(shortcutRoute) { launchSingleTop = true }
            }
        }

        // Keep tab highlight synced even when navigation occurs from cards/deep links.
        LaunchedEffect(currentRoute) {
            selectedTab = when (currentRoute) {
                "home" -> 0
                "tools" -> 1
                "settings" -> 2
                else -> selectedTab
            }
        }

        Image(
            painter ?: painterResource(if (!isDarkMode) R.drawable.wallpaper_light else R.drawable.wallpaper_dark),
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
            Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                DocsNavGraph(
                    navController = navController,
                    backdrop = backdrop,
                    selectedTab = selectedTab,
                    onTabChanged = { selectedTab = it },
                    isDarkMode = isDarkMode,
                    onDarkModeChanged = { /* unused */ },
                    themeMode = themeMode,
                    onThemeModeChanged = { 
                        themeMode = it
                        AppSettingsManager.setThemeMode(context, it)
                    },
                    incomingPdfUri = incomingPdfUri
                )
            }


            if (showStarPrompt) {
                AlertDialog(
                    onDismissRequest = {
                        GitHubStarPromptManager.onPromptDismissed(context)
                        showStarPrompt = false
                    },
                    title = { Text("Support ClearPDF") },
                    text = {
                        Text("ClearPDF is open source on GitHub. Would you like to star the project?")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            GitHubStarPromptManager.onPromptAccepted(context)
                            showStarPrompt = false
                            openRepo()
                        }) {
                            Text("Yes, Star It")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            GitHubStarPromptManager.onPromptDismissed(context)
                            showStarPrompt = false
                        }) {
                            Text("Not Now")
                        }
                    }
                )
            }
            if (showBottomTabs) {
                DocsBottomTabs(
                    selectedTabIndex = selectedTab,
                    onTabSelected = onBottomTabSelected,
                    backdrop = backdrop,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                )
            }
        }
        }
    }
}

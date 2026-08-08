package com.chethan616.clearpdf.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.AppSettingsManager
import com.chethan616.clearpdf.data.repository.GitHubStarPromptManager
import com.chethan616.clearpdf.ui.components.DocsBottomTabs
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.navigation.DocsNavGraph
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.StarPromptEventBus
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * Root composable for the Docs app.
 * Provides the wallpaper backdrop, floating bottom tabs, and navigation host.
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
            painterResource(if (!isDarkMode) R.drawable.wallpaper_light else R.drawable.wallpaper_dark),
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        CompositionLocalProvider(LocalIsDarkMode provides isDarkMode) {
            Box(Modifier.fillMaxSize()) {
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = showBottomTabs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    enter = androidx.compose.animation.fadeIn(
                        // EaseInOut tween: opacity arrives slightly after the slide,
                        // giving a sequential layered feel.
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 320,
                            delayMillis = 40,
                            easing = androidx.compose.animation.core.EaseInOut
                        )
                    ) + androidx.compose.animation.slideInVertically(
                        // Critically-overdamped spring = no bounce, pure fluid glide.
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        initialOffsetY = { it }
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 200,
                            easing = androidx.compose.animation.core.EaseInOut
                        )
                    ) + androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        ),
                        targetOffsetY = { it }
                    )
                ) {
                    DocsBottomTabs(
                        selectedTab = { selectedTab },
                        onTabSelected = onBottomTabSelected,
                        backdrop = backdrop,
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                if (showStarPrompt) {
                    val uiSensor = rememberUISensor()
                    val starText = if (!isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
                    val starSub = if (!isDarkMode) Color(0xFF666666) else Color(0xFFAAAAAA)
                    val starAccent = Color(0xFFFFC107)
                    Dialog(
                        onDismissRequest = {
                            GitHubStarPromptManager.onPromptDismissed(context)
                            showStarPrompt = false
                        },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth(0.88f)
                                .liquidGlassPanel(backdrop, uiSensor)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Star, null,
                                Modifier.size(52.dp), starAccent
                            )
                            BasicText(
                                "Support ClearPDF",
                                style = TextStyle(starText, 20.sp, FontWeight.Bold, textAlign = TextAlign.Center)
                            )
                            BasicText(
                                "ClearPDF is open source on GitHub.\nWould you like to star the project?",
                                style = TextStyle(starSub, 14.sp, textAlign = TextAlign.Center)
                            )
                            Spacer(Modifier.height(4.dp))
                            LiquidButton(
                                onClick = {
                                    GitHubStarPromptManager.onPromptAccepted(context)
                                    showStarPrompt = false
                                    openRepo()
                                },
                                backdrop = backdrop,
                                tint = starAccent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Star, null, Modifier.size(18.dp), Color.White)
                                    BasicText("Yes, Star It", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    "Not Now",
                                    style = TextStyle(starSub, 13.sp, FontWeight.Medium, textAlign = TextAlign.Center),
                                    modifier = Modifier
                                        .clickable {
                                            GitHubStarPromptManager.onPromptDismissed(context)
                                            showStarPrompt = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                BasicText(
                                    "•",
                                    style = TextStyle(starSub.copy(0.4f), 13.sp)
                                )
                                BasicText(
                                    "Don't Ask Again",
                                    style = TextStyle(starSub.copy(0.7f), 13.sp, FontWeight.Medium, textAlign = TextAlign.Center),
                                    modifier = Modifier
                                        .clickable {
                                            GitHubStarPromptManager.setNeverShowAgain(context)
                                            showStarPrompt = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

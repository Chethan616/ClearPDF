package com.chethan616.clearpdf.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.DocsBottomTabs
import com.chethan616.clearpdf.ui.navigation.DocsNavGraph
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Root composable for the Docs app.
 * Provides the wallpaper backdrop, bottom tabs, and navigation host.
 */
@Composable
fun DocsApp(shortcutRoute: String? = null) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        var painter: Painter? by remember { mutableStateOf(null) }
        val context = LocalContext.current
        val pickMedia = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val imageBitmap = BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                        if (imageBitmap != null) {
                            painter = BitmapPainter(imageBitmap)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        val backdrop = rememberLayerBackdrop()
        val navController = rememberNavController()
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }

        // Handle app shortcut deep links
        LaunchedEffect(shortcutRoute) {
            if (shortcutRoute != null) {
                navController.navigate(shortcutRoute) { launchSingleTop = true }
            }
        }

        Image(
            painter ?: painterResource(R.drawable.wallpaper_light),
            contentDescription = null,
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                DocsNavGraph(
                    navController = navController,
                    backdrop = backdrop,
                    selectedTab = selectedTab,
                    onTabChanged = { selectedTab = it },
                    onPickWallpaper = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            DocsBottomTabs(
                selectedTab = { selectedTab },
                onTabSelected = { index ->
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
                },
                backdrop = backdrop,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

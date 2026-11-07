package com.chethan616.clearpdf.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.theme.ClearType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A presentation shown slide by slide, full screen on black: swipe, or tap the right or left of the
 * screen, to go on or back; the system bars hide while it shows. Each slide is drawn by DeckCraft at
 * the screen's size.
 */
@Composable
fun PresentMode(vm: AppViewModel, doc: OpenDocument, onClose: () -> Unit) {
    val eng = vm.engine ?: return
    val count = doc.pageCount
    val pager = rememberPagerState(initialPage = doc.viewer.currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    // full screen: the status and navigation bars hide while the show runs
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val maxPx = constraints.maxWidth.toFloat()
            val maxPy = constraints.maxHeight.toFloat()
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                var slide by remember(i) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(i, maxPx, maxPy) {
                    val p = doc.info.pages.getOrNull(i) ?: return@LaunchedEffect
                    // the scale that fits the slide to the screen
                    val scale = minOf(maxPx / p.width, maxPy / p.height)
                    slide = withContext(Dispatchers.Default) { runCatching { eng.command { renderPage(doc.id, i.toUInt(), scale, null) }.toBitmap().asImageBitmap() }.getOrNull() }
                }
                Box(
                    Modifier.fillMaxSize().pointerInput(i) {
                        detectTapGestures { at ->
                            val next = if (at.x > size.width * 0.35f) i + 1 else i - 1
                            if (next in 0 until count) scope.launch { pager.animateScrollToPage(next) } else if (next >= count) onClose()
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { slide?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            GlassCircle(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(14.dp), label = stringResource(R.string.viewer_close), ink = Color.White) {
                GlassIcon(LucideIcons.X, null, Modifier.size(20.dp))
            }
            Text(
                stringResource(R.string.office_slide_of, pager.currentPage + 1, count),
                style = ClearType.Caption.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
    }
}

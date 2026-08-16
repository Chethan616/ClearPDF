package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.ExtractTextViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ExtractTextScreen(
    backdrop: LayerBackdrop,
    viewModel: ExtractTextViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiSensor = rememberUISensor()
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = Color(0xFF00838F)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSelectFile(context, it) }
    }

    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }
    val density = LocalDensity.current.density

    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractTopBarAlpha"
    )
    val topBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 16f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractTopBarOffsetY"
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractContentAlpha"
    )
    val contentOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "extractContentOffsetY"
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.graphicsLayer {
                alpha = topBarAlpha
                translationY = topBarOffsetY * density
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidIconButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(16.dp), text)
            }
                LiquidGlassTopBar(stringResource(R.string.tool_extract), backdrop, uiSensor, Modifier.weight(1f), titleFontSize = 18.sp)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffsetY * density
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.viewer_pick_pdf), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
                }
                if (state.text.isNotEmpty()) {
                    LiquidButton(onClick = { clipboard.setText(AnnotatedString(state.text)) }, backdrop = backdrop) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), text)
                        BasicText(stringResource(R.string.copy), style = TextStyle(text, 14.sp, FontWeight.Medium))
                    }
                    com.chethan616.clearpdf.ui.components.LiquidIconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.text)
                            }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.extract_share_text)))
                        },
                        backdrop = backdrop,
                        tint = Color(0xFF0088FF),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.share), Modifier.size(20.dp), Color.White)
                    }
                }
            }

            state.sourceFileName.takeIf { it.isNotBlank() }?.let {
                BasicText(it, style = TextStyle(sub, 12.sp))
            }
            state.errorMessage?.let { BasicText(it, style = TextStyle(Color(0xFFFFB300), 12.sp)) }

            Box(Modifier.fillMaxWidth().weight(1f).liquidGlassPanel(backdrop, uiSensor).padding(14.dp)) {
                when {
                    state.isExtracting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                    }
                    state.text.isNotEmpty() -> SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        BasicText(state.text, style = TextStyle(text, 14.sp))
                    }
                    state.hasResult -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        BasicText(stringResource(R.string.extract_no_text), style = TextStyle(sub, 13.sp))
                    }
                    else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        BasicText(stringResource(R.string.extract_pick_hint), style = TextStyle(sub, 13.sp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

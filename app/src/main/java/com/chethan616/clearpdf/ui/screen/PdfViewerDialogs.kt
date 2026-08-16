package com.chethan616.clearpdf.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

/**
 * Page-jump popup rendered **in-window** (not a [Dialog]) so the liquid-glass panel
 * can actually sample the viewer backdrop instead of falling back to a grey box.
 * Enters with a smooth Apple-style glass "pop" (scale + fade, gentle damping) and
 * dims the page behind it with a tap-to-dismiss scrim.
 */
@Composable
internal fun LiquidPageJumpPopup(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette (dark ink on light pages, white on dark pages).
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var targetText by remember(visible, currentPage) { mutableStateOf((currentPage + 1).toString()) }

    Box(Modifier.fillMaxSize()) {
        // Dimming scrim — tap outside to dismiss.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        // Glass popup panel — springy but non-bouncy scale-in.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                    ),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center).imePadding()
        ) {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.78f)
                    .liquidGlassPanel(backdrop, uiSensor, surface)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    stringResource(R.string.viewer_jump_to_page),
                    style = TextStyle(fg, 16.sp, fontWeight = FontWeight.Bold)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() } },
                        textStyle = TextStyle(fg, 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        singleLine = true,
                        cursorBrush = SolidColor(fg),
                        modifier = Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(field)
                            .border(1.dp, fg.copy(0.22f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )

                    BasicText(
                        "/ $pageCount",
                        style = TextStyle(fgSoft, 16.sp, fontWeight = FontWeight.Medium)
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                        BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp, FontWeight.Medium))
                    }
                    LiquidButton(
                        onClick = {
                            val p = targetText.toIntOrNull()?.minus(1)?.coerceIn(0, pageCount - 1)
                            if (p != null) onJumpToPage(p)
                        },
                        backdrop = backdrop,
                        tint = Color(0xFF1976D2)
                    ) {
                        BasicText(stringResource(R.string.viewer_go), style = TextStyle(Color.White, 13.sp, FontWeight.Bold))
                    }
                }
            }
        }
    }
}

/**
 * Editor for an inserted text box or sticky note. Rendered IN-WINDOW (not a [Dialog])
 * so the glass panel samples the real page content instead of the wallpaper PNG.
 * Adaptive colours keep it readable over any page; fade-only so glass never re-blurs
 * mid-transition. Lifts above the keyboard via [imePadding].
 */
@Composable
internal fun AnnotationEditorDialog(
    isNote: Boolean,
    initialText: String,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true; delay(160); runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().imePadding()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 440.dp)
                        .liquidGlassPanel(backdrop, uiSensor, surface)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicText(
                        if (isNote) stringResource(R.string.anno_note_title) else stringResource(R.string.anno_text_title),
                        style = TextStyle(fg, 16.sp, fontWeight = FontWeight.Bold)
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(field)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (text.isEmpty()) {
                            BasicText(
                                stringResource(R.string.anno_hint),
                                style = TextStyle(fgSoft, 14.sp)
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(fg, 14.sp),
                            cursorBrush = SolidColor(fg),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidButton(onClick = onDelete, backdrop = backdrop, surfaceColor = Color(0xFFEF5350).copy(0.22f)) {
                            BasicText(stringResource(R.string.delete), style = TextStyle(Color.White, 13.sp))
                        }
                        LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                            BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp))
                        }
                        LiquidButton(onClick = { onSave(text) }, backdrop = backdrop, tint = Color(0xFF1976D2)) {
                            BasicText(stringResource(R.string.anno_save), style = TextStyle(Color.White, 13.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

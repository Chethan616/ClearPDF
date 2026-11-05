package com.chethan616.clearpdf.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A 0 → 1 entrance on [spec], read in draw. */
@Composable
private fun rememberAppear(): Animatable<Float, AnimationVector1D> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, GlassMotion.morph()) }
    return a
}

/** The dim behind a dialog or sheet; a tap on it dismisses. */
@Composable
private fun BoxScope.Scrim(appear: Animatable<Float, AnimationVector1D>, onDismiss: () -> Unit) {
    val c = clear
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = if (c.dark) 0.42f else 0.22f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    )
}

/**
 * A dialog: a frosted card that springs up from 82 %, an optional candy [icon] blob, a big title,
 * then [content] and the buttons. [destructive] paints the confirm button tomato.
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String?,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    dismiss: String? = null,
    onDismissButton: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    accent: Color = Candy.Sky,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = clear
    val tint = if (destructive) Candy.Tomato else accent
    GlassOverlay {
        BackHandler(enabled = dismissible, onBack = onDismiss)
        val appear = rememberAppear()
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Scrim(appear) { if (dismissible) onDismiss() }
            GlassCard(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = lerp(0.82f, 1f, appear.value)
                        scaleX = s
                        scaleY = s
                        alpha = appear.value.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .clickable(remember { MutableInteractionSource() }, null) {},
                shape = RoundedRectangle(34.dp),
                wash = c.frost,
                blur = 16.dp,
            ) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)) {
                    if (icon != null) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (title != null) {
                        Text(title, style = ClearType.Headline, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                    }
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { content() }
                    if (confirm != null || dismiss != null) {
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                            if (dismiss != null) {
                                GlassPill(onClick = { (onDismissButton ?: onDismiss)() }, height = 46.dp) {
                                    Text(dismiss, style = ClearType.BodyStrong, color = c.ink)
                                }
                            }
                            if (confirm != null) {
                                GlassPill(
                                    onClick = { if (confirmEnabled) onConfirm() },
                                    tint = if (confirmEnabled) tint else c.inkFaint,
                                    height = 46.dp,
                                ) { Text(confirm, style = ClearType.BodyStrong, color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A sheet: a floating frosted card along the bottom, inset from the edges, that rises on a spring.
 * [title] in the headline face, an optional [subtitle], then [content].
 */
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    maxHeightFraction: Float = 0.86f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = clear
    GlassOverlay {
        BackHandler(onBack = onDismiss)
        val appear = rememberAppear()
        val density = LocalDensity.current
        val overlayHeight = LocalGlassOverlayHost.current?.size?.height ?: 0
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Scrim(appear, onDismiss)
            val maxH = with(density) { if (overlayHeight > 0) (overlayHeight * maxHeightFraction).toDp() else 640.dp }
            GlassCard(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .graphicsLayer {
                        val p = appear.value
                        translationY = (1f - p) * 96.dp.toPx()
                        val s = lerp(0.94f, 1f, p)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        alpha = p.coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .clickable(remember { MutableInteractionSource() }, null) {},
                shape = RoundedRectangle(32.dp),
                wash = c.frost,
                blur = 16.dp,
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 5.dp).clip(Capsule).background(c.inkFaint.copy(alpha = 0.35f)))
                }
                if (title != null) {
                    Text(title, style = ClearType.Headline, color = c.ink, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp))
                    if (subtitle != null) Text(subtitle, style = ClearType.Label, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A row of a sheet or menu: a candy icon blob, a label, an optional value. */
@Composable
fun GlassRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Candy.Sky,
    value: String? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = clear
    Row(
        modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val blob = if (destructive) Candy.Tomato else accent
            Box(Modifier.size(34.dp).clip(RoundedRectangle(11.dp)).background(blob.copy(alpha = if (c.dark) 0.26f else 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = blob, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = ClearType.BodyStrong, color = if (destructive) Candy.Tomato else c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, style = ClearType.Label, color = c.inkSoft, modifier = Modifier.padding(start = 8.dp))
        trailing?.invoke()
    }
}

/** An item of a [GlassMenu]. */
data class GlassMenuItem(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A popover menu anchored to [anchor] (window bounds of its trigger): unfolds from the trigger's
 * corner, width first then height, and folds away on a tap outside or Back.
 */
@Composable
fun GlassMenu(anchor: Rect, onDismiss: () -> Unit, items: List<GlassMenuItem>) {
    val c = clear
    GlassOverlay {
        BackHandler(onBack = onDismiss)
        val host = LocalGlassOverlayHost.current ?: return@GlassOverlay
        val w = remember { Animatable(0f) }
        val h = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { w.animateTo(1f, GlassMotion.unfold()) }
            launch { h.animateTo(1f, GlassMotion.unfoldLag()) }
        }
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        val local = Rect(host.windowToLocal(anchor.topLeft), host.windowToLocal(anchor.bottomRight))
        val margin = with(LocalDensity.current) { 12.dp.toPx() }
        var fromRight by remember { mutableStateOf(true) }
        var fromBelow by remember { mutableStateOf(true) }
        Layout(
            content = {
                GlassCard(
                    Modifier
                        .width(236.dp)
                        .graphicsLayer {
                            scaleX = lerp(0.4f, 1f, w.value)
                            scaleY = lerp(0.2f, 1f, h.value)
                            transformOrigin = TransformOrigin(if (fromRight) 1f else 0f, if (fromBelow) 0f else 1f)
                            alpha = (h.value * 1.6f).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    shape = RoundedRectangle(24.dp),
                    wash = c.frost,
                    blur = 16.dp,
                ) {
                    Spacer(Modifier.height(6.dp))
                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().pressable({ onDismiss(); item.onClick() }).padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.label, style = ClearType.BodyStrong, color = if (item.destructive) Candy.Tomato else c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.icon != null) Icon(item.icon, null, tint = if (item.destructive) Candy.Tomato else c.inkSoft, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            },
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) { measurables, constraints ->
            val p = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val right = local.center.x > constraints.maxWidth / 2f
                fromRight = right
                val x = if (right) (local.right - p.width) else local.left
                val below = local.bottom + 6f + p.height < constraints.maxHeight - margin
                fromBelow = below
                val y = if (below) local.bottom + 6f else local.top - 6f - p.height
                p.place(IntOffset(x.coerceIn(margin, constraints.maxWidth - p.width - margin).roundToInt(), y.coerceAtLeast(margin).roundToInt()))
            }
        }
    }
}

/** A text field on glass: a rounded frosted well, a quiet [placeholder], the theme's ink. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    error: Boolean = false,
    focus: FocusRequester? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    val c = clear
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = ClearType.Body.copy(color = c.ink),
        cursorBrush = SolidColor(Candy.Sky),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedRectangle(16.dp))
                    .background(if (c.dark) Color.White.copy(alpha = 0.07f) else c.ink.copy(alpha = 0.05f))
                    .then(if (error) Modifier.background(Candy.Tomato.copy(alpha = 0.12f)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = ClearType.Body, color = if (error) Candy.Tomato else c.inkFaint)
                inner()
            }
        },
    )
}

/** A toast: a glass capsule that pops in above the dock, with an optional candy action. */
@Composable
fun GlassToast(text: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = clear
    val appear = rememberAppear()
    Row(
        modifier
            .graphicsLayer {
                val p = appear.value
                translationY = (1f - p) * 24.dp.toPx()
                alpha = p.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .widthIn(max = 520.dp)
            .glass(Capsule, c.frost, blur = 12.dp)
            .padding(start = 20.dp, end = if (action != null) 6.dp else 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = ClearType.Label, color = c.ink, modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp), maxLines = 3)
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            GlassPill(onClick = onAction, tint = Candy.Sky, height = 38.dp, horizontalPadding = 14.dp) {
                Text(action, style = ClearType.Label, color = Color.White)
            }
        }
    }
}

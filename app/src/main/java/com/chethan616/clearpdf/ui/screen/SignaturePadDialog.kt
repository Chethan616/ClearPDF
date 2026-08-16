package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.LiquidSlider
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen signature capture dialog. Presents a dark canvas for the user to draw
 * their signature with white ink. On "Done", exports a transparent-background bitmap.
 */
@Composable
fun SignaturePadDialog(
    backdrop: LayerBackdrop,
    onDismiss: () -> Unit,
    onSignatureCaptured: (Bitmap) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiSensor = rememberUISensor()
    val scope = rememberCoroutineScope()

    data class StrokeItem(val points: List<Offset>, val color: Color, val width: Float)
    val strokes = remember { mutableStateListOf<StrokeItem>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Dark inks for a white "paper" pad — natural to sign with, and (unlike white ink)
    // the resulting signature is actually visible when stamped onto a white PDF page.
    val signatureColors = listOf(
        Color(0xFF141414), // Black
        Color(0xFF1565C0), // Ink Blue
        Color(0xFF0D3B66), // Navy
        Color(0xFFB3261E), // Crimson
        Color(0xFF1B5E20), // Green
        Color(0xFF6A1B9A)  // Purple
    )
    var selectedColor by remember { mutableStateOf(signatureColors[0]) }

    var selectedWidth by remember { androidx.compose.runtime.mutableFloatStateOf(6.5f) }
    var contentVisible by remember { mutableStateOf(false) }
    var showNamePrompt by remember { mutableStateOf(false) }
    var signatureName by rememberSaveable { mutableStateOf("") }
    var pendingSignature by remember { mutableStateOf<Bitmap?>(null) }
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    // Play the exit animation FULLY, THEN actually dismiss (Dialogs otherwise snap shut).
    // Delay must be >= the exit duration below (fade 200 / slide 300) so it never cuts off.
    val requestClose: () -> Unit = {
        contentVisible = false
        scope.launch { delay(310); onDismiss() }
        Unit
    }

    LaunchedEffect(showNamePrompt) {
        if (showNamePrompt) {
            delay(120)
            runCatching { nameFocusRequester.requestFocus() }
        }
    }

    data class SavedSignature(val name: String, val bitmap: Bitmap, val file: java.io.File)
    val savedSignatures = remember(context) {
        mutableStateListOf<SavedSignature>().apply {
            try {
                com.chethan616.clearpdf.data.repository.SignatureManager
                    .listSignatures(context)
                    .mapNotNull { file ->
                        com.chethan616.clearpdf.data.repository.SignatureManager
                            .loadSignature(file)
                            ?.let { SavedSignature(
                                com.chethan616.clearpdf.data.repository.SignatureManager.displayName(file),
                                it,
                                file
                            ) }
                    }
                    .forEach(::add)
            } catch (_: Throwable) {}
        }
    }
    // Long-pressed saved signature awaiting a delete confirmation.
    var signatureToDelete by remember { mutableStateOf<SavedSignature?>(null) }

    val confirmSignatureName = {
        val bitmap = pendingSignature
        val trimmedName = signatureName.trim()
        if (bitmap != null && trimmedName.isNotEmpty()) {
            runCatching {
                com.chethan616.clearpdf.data.repository.SignatureManager
                    .saveSignature(context, bitmap, trimmedName)
            }
            showNamePrompt = false
            pendingSignature = null
            onSignatureCaptured(bitmap)
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        // decorFitsSystemWindows = false → the dialog draws edge-to-edge (behind the
        // status/nav bars) so there are no gaps above/below. dismissOnBackPress = false
        // so the back gesture routes through requestClose and plays the exit animation.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler { requestClose() }
        AnimatedVisibility(
            visible = contentVisible,
            modifier = Modifier.fillMaxSize(),
            // Slide in/out like a pushed screen (not a modal pop).
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it / 3 },
            exit  = fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { it / 3 }
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidIconButton(
                    onClick = requestClose,
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew, stringResource(R.string.back), Modifier.size(18.dp), Color.White)
                }

                BasicText(
                    stringResource(R.string.sig_draw_title),
                    style = TextStyle(Color.White, 18.sp, FontWeight.Bold)
                )

                LiquidIconButton(
                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    // Subtle when there's nothing to undo.
                    Icon(
                        Icons.Rounded.Undo, stringResource(R.string.undo), Modifier.size(20.dp),
                        Color.White.copy(if (strokes.isEmpty()) 0.32f else 1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Light control tray: ink colours (no outline rings — they read cleanly on
            // the light surface) and a liquid-glass thickness slider.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF2F2EE))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ink colour beads — plain, no outline circle.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    signatureColors.forEach { color ->
                        val isSelected = selectedColor == color
                        LiquidIconButton(
                            onClick = {
                                selectedColor = color
                                if (strokes.isNotEmpty()) {
                                    strokes.indices.forEach { idx ->
                                        strokes[idx] = strokes[idx].copy(color = color)
                                    }
                                }
                            },
                            backdrop = backdrop,
                            surfaceColor = color,
                            modifier = Modifier.size(if (isSelected) 38.dp else 30.dp)
                        ) {
                            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), Color.White)
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.Black.copy(0.07f))
                )

                // Thickness: liquid-glass slider with a live ink-dot preview.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size((selectedWidth * 1.1f).dp.coerceIn(4.dp, 20.dp))
                                .clip(CircleShape)
                                .background(selectedColor)
                        )
                    }
                    LiquidSlider(
                        value = { selectedWidth },
                        onValueChange = { selectedWidth = it.coerceIn(2f, 20f) },
                        valueRange = 2f..20f,
                        visibilityThreshold = 0.1f,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Signature canvas
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF7F7F3))
                    .border(1.dp, Color.Black.copy(0.10f), RoundedCornerShape(20.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(selectedColor, selectedWidth) {
                        detectDragGestures(
                            onDragStart = { start ->
                                currentStroke = listOf(start)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                             onDragEnd = {
                                if (currentStroke.size > 1) {
                                    strokes.add(StrokeItem(currentStroke, selectedColor, selectedWidth))
                                }
                                currentStroke = emptyList()
                            },
                            onDragCancel = { currentStroke = emptyList() }
                        )
                    }
            ) {
                // Empty-canvas guides: a subtle centered "Sign here", plus a faint
                // signature baseline with an × marker near the lower third.
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    BasicText(
                        stringResource(R.string.sig_hint),
                        style = TextStyle(Color.Black.copy(0.20f), 14.sp, FontWeight.Medium),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 44.dp)
                            .fillMaxWidth(0.84f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicText("✕", style = TextStyle(Color.Black.copy(0.22f), 16.sp, FontWeight.Bold))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(0.14f))
                        )
                    }
                }

                Canvas(Modifier.fillMaxSize()) {
                    // Draw completed strokes
                    for (item in strokes) {
                        val stroke = item.points
                        if (stroke.size < 2) continue
                        val strokeStyle = Stroke(
                            width = item.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val path = Path()
                        path.moveTo(stroke[0].x, stroke[0].y)
                        for (i in 1 until stroke.size) {
                            if (i == 1 || i == stroke.size - 1) {
                                path.lineTo(stroke[i].x, stroke[i].y)
                            } else {
                                val mid = Offset(
                                    (stroke[i].x + stroke[i + 1].x) / 2f,
                                    (stroke[i].y + stroke[i + 1].y) / 2f
                                )
                                path.quadraticTo(stroke[i].x, stroke[i].y, mid.x, mid.y)
                            }
                        }
                        drawPath(path, item.color, style = strokeStyle)
                    }
                    // Draw live stroke
                    if (currentStroke.size >= 2) {
                        val strokeStyle = Stroke(
                            width = selectedWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val path = Path()
                        path.moveTo(currentStroke[0].x, currentStroke[0].y)
                        currentStroke.drop(1).forEach { path.lineTo(it.x, it.y) }
                        drawPath(path, selectedColor, style = strokeStyle)
                    }
                }
            }

            // Saved Signatures row if available
            if (savedSignatures.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                BasicText(
                    stringResource(R.string.sig_saved_title),
                    style = TextStyle(Color.White.copy(0.7f), 12.sp, FontWeight.SemiBold)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    savedSignatures.forEach { savedBmp ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(savedBmp.file) {
                                    detectTapGestures(
                                        onTap = {
                                            val safeBmp = if (savedBmp.bitmap.config == Bitmap.Config.HARDWARE || !savedBmp.bitmap.isMutable) {
                                                savedBmp.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                            } else {
                                                savedBmp.bitmap
                                            }
                                            onSignatureCaptured(safeBmp)
                                        },
                                        // Long-press → ask to delete this saved signature.
                                        onLongPress = { signatureToDelete = savedBmp }
                                    )
                                }
                                .padding(2.dp)
                        ) {
                            // Preview the actual signature ink on a light "paper" tile so
                            // the dark ink reads (matches the signing surface).
                            Box(
                                Modifier
                                    .size(84.dp, 50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF2F2EE))
                                    .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = savedBmp.bitmap.asImageBitmap(),
                                    contentDescription = savedBmp.name,
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            BasicText(
                                savedBmp.name,
                                style = TextStyle(Color.White.copy(0.7f), 10.sp, FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bottom draw actions. Name entry is a floating overlay (below) so the
            // keyboard never displaces this layout.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasInk = strokes.isNotEmpty()
                // Secondary, subdued when there's nothing to clear.
                LiquidButton(
                    onClick = { if (hasInk) { strokes.clear(); currentStroke = emptyList() } },
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(if (hasInk) 0.10f else 0.04f),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        stringResource(R.string.sig_clear),
                        style = TextStyle(Color.White.copy(if (hasInk) 0.85f else 0.32f), 15.sp, FontWeight.Medium),
                        modifier = Modifier.padding(vertical = 9.dp)
                    )
                }

                LiquidButton(
                    onClick = {
                        if (strokes.isEmpty()) return@LiquidButton
                        val rawW = canvasSize.width.coerceAtLeast(200)
                        val rawH = canvasSize.height.coerceAtLeast(200)
                        val maxDim = 800f
                        val scale = kotlin.math.min(1f, maxDim / kotlin.math.max(rawW, rawH).toFloat())
                        val w = (rawW * scale).toInt().coerceIn(200, 1000)
                        val h = (rawH * scale).toInt().coerceIn(200, 1000)
                        try {
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.scale(scale, scale)
                            for (item in strokes) {
                                val stroke = item.points
                                if (stroke.size < 2) continue
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = item.color.toArgb()
                                    style = Paint.Style.STROKE
                                    strokeWidth = item.width
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                }
                                val path = android.graphics.Path()
                                path.moveTo(stroke[0].x, stroke[0].y)
                                stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
                                canvas.drawPath(path, paint)
                            }
                            pendingSignature = bmp
                            signatureName = ""
                            showNamePrompt = true
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    },
                    backdrop = backdrop,
                    tint = if (hasInk) Color(0xFF00C853) else Color(0xFF2E7D32).copy(0.55f),
                    modifier = Modifier.weight(1.7f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 9.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), Color.White)
                        BasicText(
                            stringResource(R.string.sig_use),
                            style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold)
                        )
                    }
                }
            }
            }

        }
    }
    }

    // Naming uses its own modal window so the platform pans it cleanly above the
    // keyboard and the signature canvas behind it never reflows.
    if (showNamePrompt) {
        SignatureNameDialog(
            backdrop = backdrop,
            uiSensor = uiSensor,
            name = signatureName,
            focusRequester = nameFocusRequester,
            onNameChange = { signatureName = it },
            onDismiss = { showNamePrompt = false; pendingSignature = null },
            onSave = confirmSignatureName
        )
    }

    // Delete a saved signature (from a long-press on its thumbnail).
    signatureToDelete?.let { sig ->
        Dialog(onDismissRequest = { signatureToDelete = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1B1E25))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BasicText(
                    stringResource(R.string.sig_delete_title),
                    style = TextStyle(Color.White, 17.sp, FontWeight.Bold)
                )
                BasicText(
                    stringResource(R.string.sig_delete_msg, sig.name),
                    style = TextStyle(Color.White.copy(0.72f), 14.sp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    LiquidButton(
                        onClick = { signatureToDelete = null },
                        backdrop = backdrop,
                        surfaceColor = Color.White.copy(0.10f)
                    ) {
                        BasicText(stringResource(R.string.cancel), style = TextStyle(Color.White, 13.sp), modifier = Modifier.padding(vertical = 4.dp))
                    }
                    LiquidButton(
                        onClick = {
                            runCatching {
                                com.chethan616.clearpdf.data.repository.SignatureManager.deleteSignature(sig.file)
                            }
                            savedSignatures.remove(sig)
                            signatureToDelete = null
                        },
                        backdrop = backdrop,
                        tint = Color(0xFFEF5350)
                    ) {
                        BasicText(stringResource(R.string.delete), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureNameDialog(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    name: String,
    focusRequester: FocusRequester,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val canSave = name.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(26.dp))
                // Solid dark card (matches the signature screen) instead of sampling
                // the wallpaper PNG through glass in a separate Dialog window.
                .background(Color(0xFF1B1E25))
                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(26.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BasicText(
                stringResource(R.string.sig_name_title),
                style = TextStyle(Color.White, 16.sp, FontWeight.Bold)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.12f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Gesture, null, Modifier.size(18.dp), Color.White.copy(0.6f))
                Box(Modifier.weight(1f)) {
                    if (name.isEmpty()) {
                        BasicText(
                            stringResource(R.string.sig_name_hint),
                            style = TextStyle(Color.White.copy(0.45f), 14.sp)
                        )
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = onNameChange,
                        textStyle = TextStyle(Color.White, 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.width(100.dp)
                ) {
                    BasicText(
                        stringResource(R.string.cancel),
                        style = TextStyle(Color.White.copy(0.78f), 13.sp, FontWeight.Medium),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                LiquidButton(
                    onClick = { if (canSave) onSave() },
                    backdrop = backdrop,
                    tint = if (canSave) Color(0xFF00C853) else Color.White.copy(0.08f),
                    modifier = Modifier.width(110.dp)
                ) {
                    BasicText(
                        stringResource(R.string.sig_name_save),
                        style = TextStyle(Color.White.copy(if (canSave) 1f else 0.45f), 13.sp, FontWeight.SemiBold),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

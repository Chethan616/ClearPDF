package com.chethan616.clearpdf.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

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

    data class StrokeItem(val points: List<Offset>, val color: Color, val width: Float)
    val strokes = remember { mutableStateListOf<StrokeItem>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val signatureColors = listOf(
        Color.White,
        Color(0xFF2196F3), // Ink Blue
        Color(0xFF4CAF50), // Emerald
        Color(0xFFE91E63), // Crimson
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF9800)  // Gold
    )
    var selectedColor by remember { mutableStateOf(signatureColors[0]) }

    val strokeWidths = listOf(
        3.5f to stringResource(R.string.sig_thin),
        6.5f to stringResource(R.string.sig_medium),
        11f to stringResource(R.string.sig_thick),
        16f to stringResource(R.string.sig_heavy)
    )
    var selectedWidth by remember { androidx.compose.runtime.mutableFloatStateOf(6.5f) }

    val savedSignatures = remember {
        mutableStateListOf<Bitmap>().apply {
            try {
                addAll(com.chethan616.clearpdf.data.repository.SignatureManager.listSignatures(context).mapNotNull { com.chethan616.clearpdf.data.repository.SignatureManager.loadSignature(it) })
            } catch (_: Throwable) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
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
                    onClick = onDismiss,
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.Close, "Close", Modifier.size(20.dp), Color.White)
                }

                BasicText(
                    stringResource(R.string.sig_draw_title),
                    style = TextStyle(Color.White.copy(0.85f), 17.sp, FontWeight.SemiBold)
                )

                LiquidIconButton(
                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.Undo, "Undo", Modifier.size(20.dp), Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Color & Line Width Controls
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Color palette
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(0.08f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    signatureColors.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) Color.White else Color.White.copy(0.3f),
                                    CircleShape
                                )
                                .clickable {
                                    selectedColor = color
                                    if (strokes.isNotEmpty()) {
                                        strokes.indices.forEach { idx ->
                                            strokes[idx] = strokes[idx].copy(color = color)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (color == Color.White) Color.Black else Color.White)
                                )
                            }
                        }
                    }
                }

                // Stroke width pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(0.08f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    strokeWidths.forEach { (w, label) ->
                        val isSelected = selectedWidth == w
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White.copy(0.25f) else Color.Transparent)
                                .clickable { selectedWidth = w }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                label,
                                style = TextStyle(
                                    if (isSelected) Color.White else Color.White.copy(0.6f),
                                    12.sp,
                                    if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Signature canvas
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF111111))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(20.dp))
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
                // Baseline hint
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                            .fillMaxWidth(0.75f)
                            .height(1.dp)
                            .background(Color.White.copy(0.15f))
                    )
                    BasicText(
                        "Sign here",
                        style = TextStyle(Color.White.copy(0.2f), 14.sp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
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
                    "Saved Signatures",
                    style = TextStyle(Color.White.copy(0.7f), 12.sp, FontWeight.SemiBold)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    savedSignatures.take(4).forEach { savedBmp ->
                        Box(
                            Modifier
                                .size(70.dp, 44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(0.10f))
                                .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val safeBmp = if (savedBmp.config == Bitmap.Config.HARDWARE || !savedBmp.isMutable) {
                                        savedBmp.copy(Bitmap.Config.ARGB_8888, true)
                                    } else {
                                        savedBmp
                                    }
                                    onSignatureCaptured(safeBmp)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Gesture, null, Modifier.size(12.dp), Color.White)
                                BasicText(stringResource(R.string.sig_saved), style = TextStyle(Color.White, 11.sp, FontWeight.Medium))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bottom actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidButton(
                    onClick = { strokes.clear(); currentStroke = emptyList() },
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        stringResource(R.string.sig_clear),
                        style = TextStyle(Color.White.copy(0.75f), 15.sp, FontWeight.Medium),
                        modifier = Modifier.padding(vertical = 6.dp)
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
                            runCatching { com.chethan616.clearpdf.data.repository.SignatureManager.saveSignature(context, bmp) }
                            onSignatureCaptured(bmp)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    },
                    backdrop = backdrop,
                    tint = Color(0xFF00C853),
                    modifier = Modifier.weight(2f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
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

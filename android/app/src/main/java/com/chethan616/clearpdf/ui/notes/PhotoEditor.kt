package com.chethan616.clearpdf.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.PhotoEditor
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.ChromeCapsule
import com.chethan616.clearpdf.ui.glass.Feel
import com.chethan616.clearpdf.ui.glass.Fluid
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.LocalHaptics
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.rememberBeat
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** One adjustment of LightCraft's develop settings: where it lives, its name, glyph and range. */
private enum class Adjust(
    val section: String,
    val field: String,
    val label: Int,
    val icon: ImageVector,
    val min: Float,
    val max: Float,
    val decimals: Int,
) {
    Exposure("light", "exposure", R.string.photo_exposure, LucideIcons.Sun, -3f, 3f, 2),
    Contrast("light", "contrast", R.string.photo_contrast, LucideIcons.Contrast, -100f, 100f, 0),
    Highlights("light", "highlights", R.string.photo_highlights, LucideIcons.Highlights, -100f, 100f, 0),
    Shadows("light", "shadows", R.string.photo_shadows, LucideIcons.Shadows, -100f, 100f, 0),
    Vibrance("color", "vibrance", R.string.photo_vibrance, LucideIcons.Palette, -100f, 100f, 0),
    Saturation("color", "saturation", R.string.photo_saturation, LucideIcons.Droplet, -100f, 100f, 0),
}

/** The quarter turns LightCraft's orientation names, clockwise from as shot. */
private val TURNS = listOf("Normal", "Rotate90", "Rotate180", "Rotate270")

/** "+0.40", "−12", "0": signed, as Photos shows an adjustment. */
private fun shown(a: Adjust, v: Float): String {
    val zero = if (a.decimals > 0) 0.005f else 0.5f
    if (abs(v) < zero) return "0"
    val n = if (a.decimals > 0) "%.${a.decimals}f".format(abs(v)) else abs(v).toInt().toString()
    return (if (v > 0) "+" else "−") + n
}

/**
 * A photo editor built completely on Artefact's liquid-glass design system:
 * - Edge-to-edge photo canvas with touch-and-hold to view original
 * - Floating liquid-glass header with beat-delayed Cancel (X) and Save (Tick)
 * - Pure liquid glass controls with NO canvas circle marks
 * - Zero-snap precision ruler with haptic feedback
 * - Deep integration with GlassSettings & MotionSettings
 * - Proper BackHandler ensuring swipe-back never closes the app
 */
@Composable
fun PhotoEditScreen(vm: AppViewModel, noteId: String, name: String, onClose: () -> Unit) {
    val source = remember(name) { vm.notes.file(name) }
    val editor = remember { PhotoEditor() }
    DisposableEffect(editor) { onDispose { editor.release(); editor.close() } }
    val values = remember { mutableStateMapOf<Adjust, Float>() }
    var turn by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf(Adjust.Exposure) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var original by remember { mutableStateOf<ImageBitmap?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val edited = values.values.any { it != 0f } || turn != 0

    fun settings(withValues: Boolean = true): String {
        val o = JSONObject()
        if (withValues) for ((a, v) in values) if (v != 0f) {
            (o.optJSONObject(a.section) ?: JSONObject().also { o.put(a.section, it) }).put(a.field, v.toDouble())
        }
        if (turn != 0) o.put("orientation", TURNS[turn])
        return o.toString()
    }

    // Beat delay for Cancel (X): plays the liquid glass spring press before dismissing
    val beatClose = rememberBeat(onClose)

    // Beat delay for Done (Tick): plays the spring press, exports the image, and closes cleanly
    val beatDone = rememberBeat({
        if (saving) return@rememberBeat
        if (!edited) {
            onClose()
            return@rememberBeat
        }
        saving = true
        scope.launch {
            val out = File(source.parentFile, "edited-${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    editor.exportJpeg(source.absolutePath, settings(), out.absolutePath, 92u)
                }.isSuccess
            }
            if (ok) {
                vm.notes.get(noteId)?.let { n ->
                    vm.notes.put(n.copy(images = n.images.map { if (it == name) out.name else it }))
                }
            } else {
                vm.tell(com.chethan616.clearpdf.text(R.string.photo_not_saved))
            }
            saving = false
            onClose()
        }
    })

    // Intercept back gesture / button: closes photo editor safely instead of closing the app!
    BackHandler(enabled = true) {
        beatClose()
    }

    // Re-develop as the ruler moves; a newer value cancels the render still running
    LaunchedEffect(Unit) {
        snapshotFlow { values.toMap() to turn }.collectLatest {
            val json = settings()
            shown = withContext(Dispatchers.Default) {
                runCatching {
                    editor.render(source.absolutePath, json, 1600u).toBitmap().asImageBitmap()
                }.getOrNull()
            } ?: shown
        }
    }

    // The picture as shot (turned as the edit is), for holding to compare
    LaunchedEffect(turn) {
        val json = settings(withValues = false)
        original = withContext(Dispatchers.Default) {
            runCatching {
                editor.render(source.absolutePath, json, 1600u).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)),
    ) {
        // ── Photo Canvas Layer ───────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                (if (comparing) original ?: shown else shown)?.let {
                    Image(
                        it,
                        null,
                        Modifier
                            .clip(RoundedRectangle(20.dp))
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                // "ORIGINAL" indicator when user holds down on the photo
                androidx.compose.animation.AnimatedVisibility(
                    visible = comparing,
                    enter = fadeIn(Fluid.fade()),
                    exit = fadeOut(Fluid.fade()),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), ChromeCapsule)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            stringResource(R.string.photo_original).uppercase(),
                            style = ClearType.Label.copy(
                                fontSize = 11.sp,
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
            // Room for the bottom liquid glass deck
            Spacer(Modifier.height(240.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        // ── Liquid Glass Chrome Layer ─────────────────────────────────────────
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // ── Top Navigation Bar ────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Close / Cancel (X) button with beat delay
                    GlassCircle(
                        onClick = beatClose,
                        label = stringResource(R.string.common_cancel),
                        size = 42.dp,
                        surface = Color.White.copy(alpha = 0.12f),
                        ink = Color.White,
                    ) {
                        GlassIcon(LucideIcons.X, null, Modifier.size(18.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    // Reset button — animates in only when edits exist
                    AnimatedVisibility(
                        visible = edited,
                        enter = fadeIn(Fluid.quick()) + slideInVertically(Fluid.quick()) { -it / 2 },
                        exit = fadeOut(Fluid.quick()) + slideOutVertically(Fluid.quick()) { -it / 2 },
                    ) {
                        GlassPill(
                            onClick = {
                                values.clear()
                                turn = 0
                            },
                            height = 42.dp,
                            horizontalPadding = 16.dp,
                            surface = Color.White.copy(alpha = 0.12f),
                            tap = Feel.Tap,
                        ) {
                            Text(
                                stringResource(R.string.photo_reset),
                                style = ClearType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = Candy.Lemon,
                            )
                        }
                    }

                    if (edited) Spacer(Modifier.width(10.dp))

                    // Rotate CW
                    GlassCircle(
                        onClick = { turn = (turn + 1) % 4 },
                        label = stringResource(R.string.photo_rotate),
                        size = 42.dp,
                        surface = Color.White.copy(alpha = 0.12f),
                        ink = Color.White,
                    ) {
                        GlassIcon(LucideIcons.RotateCw, null, Modifier.size(18.dp))
                    }

                    Spacer(Modifier.width(10.dp))

                    // Done / Save (Tick) button with beat delay
                    GlassCircle(
                        onClick = beatDone,
                        label = stringResource(R.string.common_done),
                        size = 42.dp,
                        tint = if (edited) Candy.Sky else Color.Unspecified,
                        surface = if (edited) Candy.Sky.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.12f),
                        ink = if (edited) Color.White else Color.White.copy(alpha = 0.7f),
                    ) {
                        GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp))
                    }
                }
            }

            // ── Bottom Liquid Glass Control Center ────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val v = values[chosen] ?: 0f

                // Floating glass control deck enclosing readout, ruler, and dials
                Box(
                    Modifier
                        .fillMaxWidth()
                        .glass(
                            shape = RoundedRectangle(28.dp),
                            wash = Color(0x33101524),
                            blur = 18.dp,
                            lensHeight = 14.dp,
                            lensAmount = 22.dp,
                        )
                        .padding(top = 16.dp, bottom = 14.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Title + Value display
                        Text(
                            stringResource(chosen.label).uppercase(),
                            style = ClearType.Label.copy(fontSize = 11.sp, letterSpacing = 1.8.sp),
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            shown(chosen, v),
                            style = ClearType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = if (v != 0f) Candy.Lemon else Color.White,
                        )

                        Spacer(Modifier.height(10.dp))

                        // Precision Ruler
                        Ruler(chosen, v) { values[chosen] = it }

                        Spacer(Modifier.height(16.dp))

                        // Liquid Glass Dials Row (no circle marks!)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (a in Adjust.entries) {
                                Dial(
                                    a = a,
                                    value = values[a] ?: 0f,
                                    chosen = a == chosen,
                                    onClick = { chosen = a },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * An adjustment dial: pure liquid glass component with responsive fluid press,
 * squish and bloom. NO canvas circle marks or outer rings.
 */
@Composable
private fun Dial(
    a: Adjust,
    value: Float,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val nonZero = value != 0f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp),
    ) {
        GlassCircle(
            onClick = onClick,
            size = 50.dp,
            label = stringResource(a.label),
            ink = if (chosen) Candy.Lemon else Color.White,
            tint = if (chosen) Candy.Lemon.copy(alpha = 0.28f) else Color.Unspecified,
            surface = if (chosen) Candy.Lemon.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
        ) {
            GlassIcon(
                a.icon,
                null,
                Modifier.size(22.dp),
                tint = if (chosen) Candy.Lemon else Color.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (nonZero) shown(a, value) else stringResource(a.label),
            style = ClearType.Label.copy(
                fontSize = 11.sp,
                fontWeight = if (chosen || nonZero) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (chosen || nonZero) Candy.Lemon else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The value's ruler: ticks across the range with edge-fade, zero catch with haptics,
 * and a center indicator in Lemon.
 */
@Composable
private fun Ruler(a: Adjust, value: Float, onChange: (Float) -> Unit) {
    val haptics = LocalHaptics.current
    val range = a.max - a.min
    var drag by remember(a) { mutableStateOf(value) }
    LaunchedEffect(value) { drag = value }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(a) {
                val perPx = range / (size.width * 3f)
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val before = drag
                    var next = (drag - dx * perPx).coerceIn(a.min, a.max)
                    // catch at zero
                    if (abs(next) < range * 0.012f) next = 0f
                    if (before != 0f && next == 0f) haptics?.play(Feel.Tick)
                    drag = next
                    onChange(next)
                }
            },
    ) {
        val w = size.width * 3f
        val mid = size.width / 2
        val t = (drag - a.min) / range
        val ticks = 60

        for (i in 0..ticks) {
            val x = mid + (i.toFloat() / ticks - t) * w
            if (x < -4 || x > size.width + 4) continue
            val major = i % 10 == 0
            val h = if (major) size.height * 0.55f else size.height * 0.32f
            val fade = (1f - abs(x - mid) / mid).coerceIn(0.12f, 1f)
            drawLine(
                Color.White.copy(alpha = (if (major) 0.8f else 0.4f) * fade),
                Offset(x, (size.height - h) / 2),
                Offset(x, (size.height + h) / 2),
                1.5.dp.toPx(),
                StrokeCap.Round,
            )
        }

        // zero indicator mark & center needle
        val zx = mid + ((0f - a.min) / range - t) * w
        if (zx in 0f..size.width) {
            drawCircle(Candy.Lemon, 2.5.dp.toPx(), Offset(zx, size.height - 2.dp.toPx()))
        }
        drawLine(Candy.Lemon, Offset(mid, 0f), Offset(mid, size.height), 2.dp.toPx(), StrokeCap.Round)
    }
}

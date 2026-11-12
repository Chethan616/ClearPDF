package com.chethan616.clearpdf.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.WbSunny
import com.chethan616.clearpdf.ui.glass.ChromeCapsule
import com.chethan616.clearpdf.ui.glass.Fluid
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.rememberBeat
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** One adjustment LightCraft can develop. */
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
    // LightCraft's geometry, tone, presence and detail (names as its settings spell them)
    Straighten("geometry", "rotate", R.string.photo_straighten, androidx.compose.material.icons.Icons.Rounded.Straighten, -10f, 10f, 1),
    Whites("light", "whites", R.string.photo_whites, androidx.compose.material.icons.Icons.Rounded.WbSunny, -100f, 100f, 0),
    Blacks("light", "blacks", R.string.photo_blacks, LucideIcons.Moon, -100f, 100f, 0),
    Clarity("effects", "clarity", R.string.photo_clarity, LucideIcons.Sparkles, -100f, 100f, 0),
    Sharpen("detail", "sharpen_amount", R.string.photo_sharpen, androidx.compose.material.icons.Icons.Rounded.Details, 0f, 150f, 0),
    Vignette("vignette", "amount", R.string.photo_vignette, androidx.compose.material.icons.Icons.Rounded.Vignette, -100f, 100f, 0),
}

private val TURNS = listOf("Normal", "Rotate90", "Rotate180", "Rotate270")

/** The shapes Crop steps through (width to height), after "as it is". */
private val CROPS = listOf(1f to 1f, 4f to 3f, 3f to 4f, 16f to 9f, 9f to 16f)

private fun shown(a: Adjust, value: Float): String {
    val zero = if (a.decimals > 0) 0.005f else 0.5f
    if (abs(value) < zero) return "0"
    val number = if (a.decimals > 0) "%1$.${a.decimals}f".format(abs(value)) else abs(value).toInt().toString()
    return (if (value > 0f) "+" else "−") + number
}

/**
 * A direct-touch editor: the picture remains the focus, while two compact glass cylinders own the
 * adjustment value and adjustment selection. The slider is immediate; rendering happens behind it.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun PhotoEditScreen(vm: AppViewModel, noteId: String, name: String, onClose: () -> Unit) {
    val source = remember(noteId, name) { vm.notes.file(name) }
    val editor = remember(name) { PhotoEditor() }
    DisposableEffect(editor) { onDispose { editor.release(); editor.close() } }

    val values = remember(name) { mutableStateMapOf<Adjust, Float>() }
    var turn by remember(name) { mutableStateOf(0) }
    var selected by remember(name) { mutableStateOf(Adjust.Exposure) }
    var preview by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    var original by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 0: the whole picture; else the shape of CROPS[crop - 1], cut from the middle
    var crop by remember(name) { mutableStateOf(0) }
    val changed = values.values.any { it != 0f } || turn != 0 || crop != 0

    fun settings(includeAdjustments: Boolean = true): String {
        val root = JSONObject()
        if (includeAdjustments) values.forEach { (adjustment, value) ->
            if (value != 0f) {
                val section = root.optJSONObject(adjustment.section) ?: JSONObject().also { root.put(adjustment.section, it) }
                section.put(adjustment.field, value.toDouble())
            }
        }
        if (turn != 0) root.put("orientation", TURNS[turn])
        val whole = original
        if (includeAdjustments && crop != 0 && whole != null && whole.width > 0 && whole.height > 0) {
            // the largest box of the chosen shape, from the middle of the picture as it stands
            val (cw, ch) = CROPS[crop - 1]
            val shape = cw / ch
            val picture = whole.width.toFloat() / whole.height
            val w = minOf(1f, shape / picture).toDouble()
            val h = minOf(1f, picture / shape).toDouble()
            val rect = JSONObject().put("x0", (1 - w) / 2).put("y0", (1 - h) / 2).put("x1", (1 + w) / 2).put("y1", (1 + h) / 2)
            root.put("crop", JSONObject().put("geometry", JSONObject().put("rect", rect).put("angle", 0.0)))
        }
        return root.toString()
    }

    val close = rememberBeat(onClose)
    val done = rememberBeat(action = {
        if (saving) return@rememberBeat
        if (!changed) {
            onClose()
            return@rememberBeat
        }
        saving = true
        scope.launch {
            val output = File(source.parentFile, "edited-${System.currentTimeMillis()}.jpg")
            val saved = withContext(Dispatchers.Default) {
                runCatching { editor.exportJpeg(source.absolutePath, settings(), output.absolutePath, 92u) }.isSuccess
            }
            if (saved) {
                vm.notes.get(noteId)?.let { note ->
                    vm.notes.put(note.copy(images = note.images.map { if (it == name) output.name else it }))
                }
            } else {
                vm.tell(com.chethan616.clearpdf.text(R.string.photo_not_saved))
            }
            saving = false
            onClose()
        }
    })

    BackHandler(enabled = true) { close() }

    // A smaller working render prevents native image development from competing with slider input.
    LaunchedEffect(source) {
        snapshotFlow { settings() }
            .distinctUntilChanged()
            .debounce(80)
            .collectLatest { json ->
                preview = withContext(Dispatchers.Default) {
                    runCatching { editor.render(source.absolutePath, json, 1280u).toBitmap().asImageBitmap() }.getOrNull()
                } ?: preview
            }
    }
    LaunchedEffect(source, turn) {
        original = withContext(Dispatchers.Default) {
            runCatching { editor.render(source.absolutePath, settings(includeAdjustments = false), 1280u).toBitmap().asImageBitmap() }.getOrNull()
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color(0xFF090A0F))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 76.dp, bottom = 176.dp, start = 12.dp, end = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                (if (comparing) original ?: preview else preview)?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedRectangle(24.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                AnimatedVisibility(
                    visible = comparing,
                    enter = fadeIn(Fluid.fade()),
                    exit = fadeOut(Fluid.fade()),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Box(
                        Modifier.padding(top = 12.dp).background(Color.Black.copy(alpha = 0.58f), ChromeCapsule).padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(stringResource(R.string.photo_original), style = ClearType.Label, color = Color.White)
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize()) {
                PhotoHeader(
                    changed = changed,
                    onClose = close,
                    onDone = done,
                    onReset = { values.clear(); turn = 0; crop = 0 },
                    onRotate = { turn = (turn + 1) % 4 },
                    cropLabel = if (crop == 0) null else CROPS[crop - 1].let { "${it.first.toInt()}:${it.second.toInt()}" },
                    onCrop = { crop = (crop + 1) % (CROPS.size + 1) },
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    PhotoControls(
                        selected = selected,
                        value = values[selected] ?: 0f,
                        onSelect = { selected = it },
                        onValueChange = { next ->
                            val caught = if (abs(next) < (selected.max - selected.min) * 0.012f) 0f else next
                            values[selected] = caught
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoHeader(
    changed: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
    onRotate: () -> Unit,
    cropLabel: String?,
    onCrop: () -> Unit,
) {
    val ink = rememberGlassInk(Color.White)
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCircle(onClick = onClose, size = 42.dp, ink = Color.White, label = stringResource(R.string.common_cancel)) {
                GlassIcon(LucideIcons.X, null, Modifier.size(19.dp))
            }
            Row(Modifier.weight(1f).height(42.dp).then(ink.modifier).glassBar().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.photo_edit), style = ClearType.Title.copy(fontSize = 15.sp), color = ink.color)
            }
            AnimatedVisibility(visible = changed, enter = fadeIn(Fluid.quick()), exit = fadeOut(Fluid.quick())) {
                GlassPill(onClick = onReset, height = 42.dp, horizontalPadding = 13.dp, label = stringResource(R.string.photo_reset)) {
                    Text(stringResource(R.string.photo_reset), style = ClearType.Label, color = Candy.Lemon)
                }
            }
            // Crop steps through the shapes (1:1, 4:3, 3:4, 16:9, 9:16) and back to the whole picture
            GlassCircle(onClick = onCrop, size = 42.dp, ink = Color.White, label = stringResource(R.string.photo_crop)) {
                if (cropLabel == null) GlassIcon(LucideIcons.Crop, null, Modifier.size(19.dp))
                else Text(cropLabel, style = ClearType.Label.copy(fontSize = 11.sp), color = Candy.Lemon, maxLines = 1)
            }
            GlassCircle(onClick = onRotate, size = 42.dp, ink = Color.White, label = stringResource(R.string.photo_rotate)) {
                GlassIcon(LucideIcons.RotateCw, null, Modifier.size(19.dp))
            }
            GlassCircle(
                onClick = onDone,
                size = 42.dp,
                tint = if (changed) Candy.Sky else Color.Unspecified,
                ink = Color.White,
                label = stringResource(R.string.common_done),
            ) {
                GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PhotoControls(
    selected: Adjust,
    value: Float,
    onSelect: (Adjust) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    val ink = rememberGlassInk(Color.White)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).then(ink.modifier).glassBar().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.width(72.dp)) {
                Text(stringResource(selected.label), style = ClearType.Label, color = ink.color.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown(selected, value), style = ClearType.Title.copy(fontSize = 22.sp), color = if (value == 0f) ink.color else Candy.Lemon)
            }
            // felt under the thumb: a light tick every fortieth of the way, a firm one through zero
            val feel = com.chethan616.clearpdf.ui.glass.rememberFeel()
            val notch = remember(selected) { intArrayOf(Int.MIN_VALUE) }
            Slider(
                value = value,
                onValueChange = { v ->
                    val at = Math.round((v - selected.min) / (selected.max - selected.min).coerceAtLeast(0.0001f) * 40f)
                    if (at != notch[0]) {
                        if (notch[0] != Int.MIN_VALUE) {
                            val throughZero = selected.min < 0f && ((value < 0f) != (v < 0f) || v == 0f)
                            feel(if (throughZero) com.chethan616.clearpdf.ui.glass.Feel.Tap else com.chethan616.clearpdf.ui.glass.Feel.Tick)
                        }
                        notch[0] = at
                    }
                    onValueChange(v)
                },
                valueRange = selected.min..selected.max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Candy.Sky,
                    activeTrackColor = Candy.Sky,
                    inactiveTrackColor = ink.color.copy(alpha = 0.22f),
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(70.dp).then(ink.modifier).glassBar(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(Adjust.entries, key = { it.name }) { adjustment ->
                CompositionLocalProvider(LocalGlassInk provides ink.color) {
                    AdjustmentTab(adjustment, adjustment == selected, onClick = { onSelect(adjustment) })
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun AdjustmentTab(adjustment: Adjust, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalGlassInk.current
    Column(
        Modifier.width(68.dp).height(62.dp).pressable(onClick, pressedScale = 0.9f).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(ChromeCapsule).background(if (selected) Candy.Sky else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            GlassIcon(adjustment.icon, null, Modifier.size(19.dp), tint = if (selected) Color.White else ink)
        }
        Text(
            stringResource(adjustment.label),
            style = ClearType.Label.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) ink else ink.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

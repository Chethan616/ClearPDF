package com.chethan616.clearpdf.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.chethan616.clearpdf.ui.glass.LocalGlassInk
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.rememberBeat
import com.chethan616.clearpdf.ui.glass.rememberFeel
import com.chethan616.clearpdf.ui.glass.rememberGlassInk
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    Whites("light", "whites", R.string.photo_whites, Icons.Rounded.WbSunny, -100f, 100f, 0),
    Blacks("light", "blacks", R.string.photo_blacks, LucideIcons.Moon, -100f, 100f, 0),
    Vibrance("color", "vibrance", R.string.photo_vibrance, LucideIcons.Palette, -100f, 100f, 0),
    Saturation("color", "saturation", R.string.photo_saturation, LucideIcons.Droplet, -100f, 100f, 0),
    Clarity("effects", "clarity", R.string.photo_clarity, LucideIcons.Sparkles, -100f, 100f, 0),
    Sharpen("detail", "sharpen_amount", R.string.photo_sharpen, Icons.Rounded.Details, 0f, 150f, 0),
    Vignette("vignette", "amount", R.string.photo_vignette, Icons.Rounded.Vignette, -100f, 100f, 0),
    // below zero it softens the picture, above it brings detail out
    Texture("effects", "texture", R.string.photo_texture, LucideIcons.Droplet, -100f, 100f, 0),
}

/** What the editor is doing: developing, framing, hiding, or writing on the picture. */
private enum class Mode(val label: Int, val icon: ImageVector) {
    Adjust(R.string.photo_adjust, LucideIcons.SlidersHorizontal),
    Crop(R.string.photo_crop, LucideIcons.Crop),
    Blur(R.string.photo_blur, Icons.Rounded.BlurOn),
    Text(R.string.photo_text, LucideIcons.Type),
}

/** The crop shapes (width to height); 0: free, -1: the picture's own. */
private val SHAPES = listOf(0f to 0f, -1f to -1f, 1f to 1f, 4f to 5f, 3f to 4f, 4f to 3f, 16f to 9f, 9f to 16f)

/** The colours words can take. */
private val INKS = listOf(
    Color.White, Color(0xFF141414), Candy.Lemon, Candy.Tangerine, Candy.Tomato, Candy.Bubblegum, Candy.Grape, Candy.Sky, Candy.Mint, Candy.Lime,
)

private fun shown(a: Adjust, value: Float): String {
    val zero = if (a.decimals > 0) 0.005f else 0.5f
    if (abs(value) < zero) return "0"
    val number = if (a.decimals > 0) "%1$.${a.decimals}f".format(abs(value)) else abs(value).toInt().toString()
    return (if (value > 0f) "+" else "−") + number
}

private fun degrees(a: Float): String {
    if (abs(a) < 0.05f) return "0°"
    return (if (a > 0f) "+" else "−") + "%.1f°".format(abs(a))
}

/** [w] × [h] fitted in [area] (less [margin] all round), centred. */
private fun fit(area: IntSize, w: Float, h: Float, margin: Float = 0f): RectF? {
    if (area.width <= 0 || area.height <= 0 || w <= 0f || h <= 0f) return null
    val aw = area.width - margin * 2f
    val ah = area.height - margin * 2f
    val k = min(aw / w, ah / h)
    val left = (area.width - w * k) / 2f
    val top = (area.height - h * k) / 2f
    return RectF(left, top, left + w * k, top + h * k)
}

private fun fontFamily(i: Int) = when (i) {
    1 -> FontFamily.Serif
    2 -> FontFamily.Monospace
    3 -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

private fun decodeMutable(file: File): Bitmap {
    val opts = BitmapFactory.Options().apply { inMutable = true }
    return try {
        BitmapFactory.decodeFile(file.absolutePath, opts) ?: error("not decoded")
    } catch (_: OutOfMemoryError) {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inMutable = true; inSampleSize = 2 }) ?: error("not decoded")
    }
}

/**
 * The picture editor. The picture stays the focus; a glass switch at the top picks what the bar
 * under it does:
 *  - Adjust: LightCraft's light and colour, one slider at a time (hold the picture to see it as it was);
 *  - Crop: a frame with corner and edge handles to drag, a dial to straighten it (±45°), quarter
 *    turns, a mirror, and the usual shapes;
 *  - Blur: paint over what should not be seen, blurred or pixelated (an eraser takes it back);
 *  - Text: tap to write on it; drag words to move them, pinch to size and turn them.
 * Developing is LightCraft's (its own crop model, so the frame shown is the frame saved); the
 * brush strokes and words are laid on its output, the same way for the preview and for the save.
 */
@Composable
fun PhotoEditScreen(vm: AppViewModel, noteId: String, name: String, onClose: () -> Unit) {
    val source = remember(noteId, name) { vm.notes.file(name) }
    PhotoEditScreen(vm, source, name, onSaved = { output ->
        vm.notes.get(noteId)?.let { note ->
            vm.notes.put(note.copy(images = note.images.map { if (it == name) output.name else it }))
        }
        // opened as a tool: the edited picture is also put in Downloads
        vm.savePhotoCopy(output)
    }, onClose = onClose)
}

/** A picture file opened from Files, Recent or another app: the edited copy goes to Downloads. */
@Composable
fun PhotoEditScreen(vm: AppViewModel, image: com.chethan616.clearpdf.ui.OpenedImage, onClose: () -> Unit) {
    PhotoEditScreen(vm, image.file, image.uri.toString(), outputDir = vm.getApplication<android.app.Application>().cacheDir, onSaved = { output ->
        vm.saveEditedImage(image, output)
        output.delete()
    }, onClose = onClose)
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun PhotoEditScreen(
    vm: AppViewModel,
    source: File,
    name: String,
    outputDir: File = source.parentFile ?: source,
    onSaved: suspend (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val editor = remember(name) { PhotoEditor() }
    DisposableEffect(editor) { onDispose { editor.release(); editor.close() } }
    val feel = rememberFeel()
    val scope = rememberCoroutineScope()

    val values = remember(name) { mutableStateMapOf<Adjust, Float>() }
    var selected by remember(name) { mutableStateOf(Adjust.Exposure) }
    var mode by remember(name) { mutableStateOf(Mode.Adjust) }
    // the picture as its camera meant it to stand (its EXIF orientation, which LightCraft leaves to us)
    val upright = remember(source) { exifFrame(source) }
    var frame by remember(name) { mutableStateOf(upright) }
    // the crop as last set by hand: straightening shrinks it to stay on the picture, and gives it back
    var baseCrop by remember(name) { mutableStateOf(CropBox()) }
    var shape by remember(name) { mutableIntStateOf(0) }
    var aspect by remember(name) { mutableStateOf<Float?>(null) }
    val marks = remember(name) { mutableStateListOf<Mark>() }
    var marksVersion by remember { mutableIntStateOf(0) }
    var brush by remember { mutableFloatStateOf(0.06f) }
    var smudgeKind by remember { mutableIntStateOf(SMUDGE_BLUR) }
    var brushAt by remember { mutableStateOf<Offset?>(null) }
    var chosen by remember { mutableStateOf<Label?>(null) }
    var typing by remember { mutableStateOf<Label?>(null) }
    var typingNew by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var guide by remember { mutableStateOf(false) }
    var lastInk by remember { mutableIntStateOf(Color.White.toArgb()) }
    var lastStyle by remember { mutableIntStateOf(0) }
    var lastFont by remember { mutableIntStateOf(0) }
    var cropping by remember { mutableStateOf(false) }

    var preview by remember(name) { mutableStateOf<Bitmap?>(null) }
    var previewImage by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    var fx by remember(name) { mutableStateOf<Fx?>(null) }
    var original by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    var framePic by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    // the turned picture's own size (its shape is what crops and marks are measured against)
    var frameSize by remember(name) { mutableStateOf<Size?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf(IntSize.Zero) }

    val changed = values.values.any { it != 0f } || frame != upright || marks.isNotEmpty()

    fun settings(adjust: Boolean = true, crop: Boolean = true): String {
        val root = JSONObject()
        if (adjust) values.forEach { (adjustment, value) ->
            if (value != 0f) {
                val section = root.optJSONObject(adjustment.section) ?: JSONObject().also { root.put(adjustment.section, it) }
                section.put(adjustment.field, value.toDouble())
            }
        }
        if (frame.orientation != "Normal") root.put("orientation", frame.orientation)
        if (crop && frame.hasCrop) {
            val c = frame.crop
            val rect = JSONObject().put("x0", c.x0.toDouble()).put("y0", c.y0.toDouble()).put("x1", c.x1.toDouble()).put("y1", c.y1.toDouble())
            root.put("crop", JSONObject().put("geometry", JSONObject().put("rect", rect).put("angle", frame.angle.toDouble())))
        }
        return root.toString()
    }

    // ---- developing: a working preview, the frame to crop in, and the picture as it was
    LaunchedEffect(source) {
        snapshotFlow { settings() }
            .distinctUntilChanged()
            .debounce(70)
            .collectLatest { json ->
                val bmp = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1280u).toBitmap() }.getOrNull() } ?: return@collectLatest
                preview = bmp
                previewImage = bmp.asImageBitmap()
                fx = withContext(Dispatchers.Default) { runCatching { fxOf(bmp) }.getOrNull() }
            }
    }
    LaunchedEffect(source) {
        snapshotFlow { if (mode == Mode.Crop) settings(crop = false) else null }
            .distinctUntilChanged()
            .debounce(40)
            .collectLatest { json ->
                if (json == null) return@collectLatest
                framePic = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1280u).toBitmap().asImageBitmap() }.getOrNull() } ?: framePic
            }
    }
    LaunchedEffect(source, frame.turns, frame.mirror) {
        val json = settings(adjust = false, crop = false)
        withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1536u).toBitmap() }.getOrNull() }?.let {
            frameSize = Size(it.width.toFloat(), it.height.toFloat())
        }
    }
    LaunchedEffect(source, frame) {
        delay(120)
        val json = settings(adjust = false)
        original = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1280u).toBitmap().asImageBitmap() }.getOrNull() } ?: original
    }

    // ---- framing
    fun setAngle(a: Float) {
        val fs = frameSize ?: return
        frame = frame.copy(angle = a, crop = fitInside(baseCrop, a, fs.width, fs.height))
    }
    fun pickShape(i: Int) {
        val fs = frameSize ?: return
        shape = i
        val (w, h) = SHAPES[i]
        aspect = when {
            w == 0f -> null
            w < 0f -> fs.width / fs.height
            else -> w / h
        }
        aspect?.let {
            val c = largest(it, frame.crop, frame.angle, fs.width, fs.height)
            frame = frame.copy(crop = c)
            baseCrop = c
        }
        feel(Feel.Tick)
    }
    fun turn() {
        frame = frame.copy(turns = (frame.turns + 1).mod(4), crop = frame.crop.turned())
        baseCrop = baseCrop.turned()
        aspect = aspect?.let { 1f / it }
        frameSize = frameSize?.let { Size(it.height, it.width) }
        marks.forEach { it.turn() }
        marksVersion++
        feel(Feel.Spin)
    }
    fun mirror() {
        frame = frame.copy(turns = (4 - frame.turns).mod(4), mirror = !frame.mirror, angle = -frame.angle, crop = frame.crop.mirrored())
        baseCrop = baseCrop.mirrored()
        marks.forEach { it.mirror() }
        marksVersion++
        feel(Feel.Tap)
    }
    fun reset() {
        val sideways = (frame.turns - upright.turns) % 2 != 0
        values.clear()
        frame = upright
        frameSize = frameSize?.let { if (sideways) Size(it.height, it.width) else it }
        baseCrop = CropBox()
        shape = 0
        aspect = null
        marks.clear()
        chosen = null
        marksVersion++
        feel(Feel.Fall)
    }

    // ---- words
    fun startTyping(l: Label, isNew: Boolean) {
        typing = l
        typingNew = isNew
        typed = l.text
    }
    fun finishTyping() {
        val l = typing ?: return
        typing = null
        if (typed.isBlank()) {
            marks.remove(l)
            if (chosen === l) chosen = null
        } else {
            l.text = typed.trimEnd()
            chosen = l
        }
        marksVersion++
    }

    val close = rememberBeat(onClose)
    val done = rememberBeat(action = {
        if (saving) return@rememberBeat
        if (!changed) {
            onClose()
            return@rememberBeat
        }
        saving = true
        val json = settings()
        val laid = marks.toList()
        val fs = frameSize
        val geometry = frame
        scope.launch {
            val stamp = System.currentTimeMillis()
            val output = File(outputDir, "edited-$stamp.jpg")
            val saved = withContext(Dispatchers.Default) {
                runCatching {
                    if (laid.isEmpty() || fs == null) {
                        editor.exportJpeg(source.absolutePath, json, output.absolutePath, 92u)
                    } else {
                        val developed = File(context.cacheDir, "developed-$stamp.jpg")
                        try {
                            editor.exportJpeg(source.absolutePath, json, developed.absolutePath, 100u)
                            val bmp = decodeMutable(developed)
                            val place = Placement(fs.width, fs.height, geometry.angle, geometry.crop, bmp.width.toFloat(), bmp.height.toFloat())
                            paintMarks(android.graphics.Canvas(bmp), place, laid, if (laid.any { it is Smudge }) fxOf(bmp) else null)
                            output.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                            bmp.recycle()
                        } finally {
                            developed.delete()
                        }
                    }
                }.isSuccess
            }
            if (saved) {
                onSaved(output)
            } else {
                vm.tell(com.chethan616.clearpdf.text(R.string.photo_not_saved))
            }
            saving = false
            onClose()
        }
    })

    BackHandler(enabled = true) {
        when {
            typing != null -> finishTyping()
            chosen != null -> chosen = null
            else -> close()
        }
    }

    // ---- where things are on screen
    val pic = preview
    val shownRect = if (pic != null) fit(area, pic.width.toFloat(), pic.height.toFloat()) else null
    val place = if (pic != null && frameSize != null) Placement(frameSize!!.width, frameSize!!.height, frame.angle, frame.crop, pic.width.toFloat(), pic.height.toFloat()) else null
    val frameBox = frameSize?.let { fit(area, it.width, it.height, margin = 22f * density) }
    val now by rememberUpdatedState(Triple(shownRect, place, frameBox))
    val k = if (shownRect != null && pic != null) shownRect.width() / pic.width else 1f

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color(0xFF090A0F))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 66.dp, bottom = 168.dp, start = 12.dp, end = 12.dp)
                    .onSizeChanged { area = it }
                    .pointerInput(mode) {
                        when (mode) {
                            Mode.Adjust -> detectTapGestures(onPress = {
                                comparing = true
                                tryAwaitRelease()
                                comparing = false
                            })
                            Mode.Crop -> awaitEachGesture {
                                val down = awaitFirstDown()
                                val box = now.third ?: return@awaitEachGesture
                                val fs = frameSize ?: return@awaitEachGesture
                                val r0 = frame.crop
                                val cr = RectF(box.left + r0.x0 * box.width(), box.top + r0.y0 * box.height(), box.left + r0.x1 * box.width(), box.top + r0.y1 * box.height())
                                val p = down.position
                                val reach = 30f * density
                                val corners = arrayOf(Offset(cr.left, cr.top), Offset(cr.right, cr.top), Offset(cr.right, cr.bottom), Offset(cr.left, cr.bottom))
                                var handle = corners.indexOfFirst { (it - p).getDistance() <= reach }
                                if (handle < 0 && aspect == null) {
                                    val near = 22f * density
                                    handle = when {
                                        abs(p.x - cr.left) <= near && p.y in cr.top..cr.bottom -> 4
                                        abs(p.y - cr.top) <= near && p.x in cr.left..cr.right -> 5
                                        abs(p.x - cr.right) <= near && p.y in cr.top..cr.bottom -> 6
                                        abs(p.y - cr.bottom) <= near && p.x in cr.left..cr.right -> 7
                                        else -> -1
                                    }
                                }
                                if (handle < 0 && cr.contains(p.x, p.y)) handle = 8
                                if (handle < 0) return@awaitEachGesture
                                cropping = true
                                val minW = 56f * density / box.width()
                                val minH = 56f * density / box.height()
                                var acc = Offset.Zero
                                var stopped = false
                                do {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val delta = ch.positionChange()
                                    acc += delta
                                    val dx = acc.x / box.width()
                                    val dy = acc.y / box.height()
                                    val cur = frame.crop
                                    val wanted = when (handle) {
                                        // moved a step at a time, across then down, so it slides along an edge it meets
                                        8 -> {
                                            val a = constrain(cur, cur.moved(delta.x / box.width(), 0f), frame.angle, fs.width, fs.height)
                                            a.moved(0f, delta.y / box.height())
                                        }
                                        in 0..3 -> {
                                            val ax = if (handle == 0 || handle == 3) r0.x1 else r0.x0
                                            val ay = if (handle == 0 || handle == 1) r0.y1 else r0.y0
                                            val mx = (if (handle == 0 || handle == 3) r0.x0 else r0.x1) + dx
                                            val my = (if (handle == 0 || handle == 1) r0.y0 else r0.y1) + dy
                                            var w = max(abs(mx - ax), minW)
                                            var h = max(abs(my - ay), minH)
                                            aspect?.let { a ->
                                                if (w * fs.width / (h * fs.height) > a) w = h * fs.height * a / fs.width else h = w * fs.width / a / fs.height
                                            }
                                            val x0 = if (handle == 0 || handle == 3) ax - w else ax
                                            val y0 = if (handle == 0 || handle == 1) ay - h else ay
                                            CropBox(x0, y0, x0 + w, y0 + h)
                                        }
                                        4 -> cur.copy(x0 = min(r0.x0 + dx, r0.x1 - minW))
                                        5 -> cur.copy(y0 = min(r0.y0 + dy, r0.y1 - minH))
                                        6 -> cur.copy(x1 = max(r0.x1 + dx, r0.x0 + minW))
                                        else -> cur.copy(y1 = max(r0.y1 + dy, r0.y0 + minH))
                                    }
                                    val next = constrain(cur, wanted, frame.angle, fs.width, fs.height)
                                    // a light tick the moment the frame meets the picture's edge
                                    val blocked = handle != 8 && abs(next.w - wanted.w) + abs(next.h - wanted.h) + abs(next.x0 - wanted.x0) + abs(next.y0 - wanted.y0) > 1e-4f
                                    if (blocked && !stopped) feel(Feel.Tick)
                                    stopped = blocked
                                    frame = frame.copy(crop = next)
                                    baseCrop = next
                                    ch.consume()
                                } while (event.changes.any { it.pressed })
                                cropping = false
                            }
                            Mode.Blur -> awaitEachGesture {
                                val down = awaitFirstDown()
                                val (rect, pl, _) = now
                                if (rect == null || pl == null || !rect.contains(down.position.x, down.position.y)) return@awaitEachGesture
                                val scaleK = rect.width() / pl.outW
                                fun at(o: Offset): PointF = pl.back((o.x - rect.left) / scaleK, (o.y - rect.top) / scaleK)
                                val sm = Smudge(smudgeKind, brush)
                                sm.points.add(at(down.position))
                                marks.add(sm)
                                chosen = null
                                marksVersion++
                                brushAt = down.position
                                var last = down.position
                                do {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((ch.position - last).getDistance() > 2.5f * density) {
                                        sm.points.add(at(ch.position))
                                        last = ch.position
                                        marksVersion++
                                    }
                                    brushAt = ch.position
                                    ch.consume()
                                } while (event.changes.any { it.pressed })
                                brushAt = null
                            }
                            Mode.Text -> awaitEachGesture {
                                val down = awaitFirstDown()
                                val (rect, pl, _) = now
                                if (rect == null || pl == null) return@awaitEachGesture
                                val scaleK = rect.width() / pl.outW
                                fun out(o: Offset) = Offset((o.x - rect.left) / scaleK, (o.y - rect.top) / scaleK)
                                val o0 = out(down.position)
                                val hit = marks.filterIsInstance<Label>().lastOrNull { labelBox(it, pl).holds(o0.x, o0.y, 10f * density / scaleK) }
                                val wasChosen = hit != null && hit === chosen
                                if (hit != null && hit !== chosen) {
                                    chosen = hit
                                    feel(Feel.Tick)
                                }
                                val grab = hit?.let { val b = pl.back(o0.x, o0.y); PointF(it.x - b.x, it.y - b.y) }
                                var moved = false
                                var many = false
                                var travel = 0f
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    val l = chosen
                                    if (pressed >= 2 && l != null) {
                                        many = true
                                        l.size = (l.size * event.calculateZoom()).coerceIn(0.015f, 0.45f)
                                        l.rotation += event.calculateRotation()
                                        val move = event.calculatePan()
                                        val c0 = pl.out(l.x, l.y)
                                        val b = pl.back(c0.x + move.x / scaleK, c0.y + move.y / scaleK)
                                        l.x = b.x
                                        l.y = b.y
                                        marksVersion++
                                        event.changes.forEach { it.consume() }
                                    } else if (pressed == 1 && hit != null && !many) {
                                        val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                                        travel += ch.positionChange().getDistance()
                                        if (travel > viewConfiguration.touchSlop) moved = true
                                        if (moved && grab != null) {
                                            val o = out(ch.position)
                                            val b = pl.back(o.x, o.y)
                                            hit.x = b.x + grab.x
                                            hit.y = b.y + grab.y
                                            // held on the middle line while near it
                                            val c = pl.out(hit.x, hit.y)
                                            val snap = abs(c.x - pl.outW / 2f) < 9f * density / scaleK
                                            if (snap) {
                                                val s = pl.back(pl.outW / 2f, c.y)
                                                hit.x = s.x
                                                hit.y = s.y
                                            }
                                            if (snap && !guide) feel(Feel.Tick)
                                            guide = snap
                                            marksVersion++
                                            ch.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                guide = false
                                if (!moved && !many) {
                                    when {
                                        hit != null -> if (wasChosen) startTyping(hit, isNew = false)
                                        chosen != null -> chosen = null
                                        rect.contains(down.position.x, down.position.y) -> {
                                            val b = pl.back(o0.x, o0.y)
                                            val l = Label("", b.x, b.y, rotation = -frame.angle, color = lastInk, style = lastStyle, font = lastFont)
                                            marks.add(l)
                                            chosen = l
                                            startTyping(l, isNew = true)
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                val gridAlpha by animateFloatAsState(if (cropping) 1f else 0f, Fluid.quick(), label = "grid")
                Canvas(Modifier.fillMaxSize()) {
                    marksVersion.let { }
                    if (mode == Mode.Crop) {
                        val box = frameBox
                        val img = framePic
                        if (box != null && img != null) {
                            rotate(frame.angle, pivot = Offset(box.centerX(), box.centerY())) {
                                drawImage(
                                    img,
                                    dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
                                    dstSize = IntSize(box.width().roundToInt(), box.height().roundToInt()),
                                )
                            }
                            val r = frame.crop
                            val cr = Rect(box.left + r.x0 * box.width(), box.top + r.y0 * box.height(), box.left + r.x1 * box.width(), box.top + r.y1 * box.height())
                            val outside = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addRect(cr)
                            }
                            drawPath(outside, Color(0xFF090A0F).copy(alpha = 0.72f))
                            if (gridAlpha > 0f) {
                                for (i in 1..2) {
                                    val x = cr.left + cr.width * i / 3f
                                    val y = cr.top + cr.height * i / 3f
                                    drawLine(Color.White.copy(alpha = 0.55f * gridAlpha), Offset(x, cr.top), Offset(x, cr.bottom), 1f * density)
                                    drawLine(Color.White.copy(alpha = 0.55f * gridAlpha), Offset(cr.left, y), Offset(cr.right, y), 1f * density)
                                }
                            }
                            drawRect(Color.White.copy(alpha = 0.9f), cr.topLeft, cr.size, style = Stroke(1.2f * density))
                            val arm = min(22f * density, min(cr.width, cr.height) / 3f)
                            val t = 3.5f * density
                            val o = t / 2f
                            for ((cx, cy, sx, sy) in listOf(
                                Corner(cr.left, cr.top, 1f, 1f), Corner(cr.right, cr.top, -1f, 1f),
                                Corner(cr.right, cr.bottom, -1f, -1f), Corner(cr.left, cr.bottom, 1f, -1f),
                            )) {
                                drawLine(Color.White, Offset(cx - sx * o, cy - sy * o), Offset(cx + sx * arm, cy - sy * o), t, StrokeCap.Round)
                                drawLine(Color.White, Offset(cx - sx * o, cy - sy * o), Offset(cx - sx * o, cy + sy * arm), t, StrokeCap.Round)
                            }
                            if (aspect == null) {
                                val half = min(13f * density, min(cr.width, cr.height) / 6f)
                                drawLine(Color.White, Offset(cr.left, cr.center.y - half), Offset(cr.left, cr.center.y + half), t, StrokeCap.Round)
                                drawLine(Color.White, Offset(cr.right, cr.center.y - half), Offset(cr.right, cr.center.y + half), t, StrokeCap.Round)
                                drawLine(Color.White, Offset(cr.center.x - half, cr.top), Offset(cr.center.x + half, cr.top), t, StrokeCap.Round)
                                drawLine(Color.White, Offset(cr.center.x - half, cr.bottom), Offset(cr.center.x + half, cr.bottom), t, StrokeCap.Round)
                            }
                        }
                    } else {
                        val rect = shownRect
                        val img = if (comparing) original ?: previewImage else previewImage
                        if (rect != null && img != null) {
                            val clip = Path().apply { addRoundRect(RoundRect(Rect(rect.left, rect.top, rect.right, rect.bottom), CornerRadius(20f * density))) }
                            clipPath(clip) {
                                drawImage(
                                    img,
                                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                                    dstSize = IntSize(rect.width().roundToInt(), rect.height().roundToInt()),
                                )
                                val pl = place
                                if (!comparing && pl != null && marks.isNotEmpty()) {
                                    drawIntoCanvas { cv ->
                                        val nc = cv.nativeCanvas
                                        nc.save()
                                        nc.translate(rect.left, rect.top)
                                        nc.scale(k, k)
                                        paintMarks(nc, pl, if (typing != null) marks.filter { it !== typing } else marks, fx, if (mode == Mode.Text && typing == null) chosen else null)
                                        nc.restore()
                                    }
                                }
                            }
                            if (guide) {
                                drawLine(Candy.Lemon.copy(alpha = 0.9f), Offset(rect.centerX(), rect.top), Offset(rect.centerX(), rect.bottom), 1.5f * density)
                            }
                            brushAt?.let { at ->
                                val pl = place ?: return@let
                                val radius = brush * pl.long * k / 2f
                                drawCircle(Color.White.copy(alpha = 0.9f), radius, at, style = Stroke(1.5f * density))
                                drawCircle(Color.Black.copy(alpha = 0.25f), radius + 1.5f * density, at, style = Stroke(1f * density))
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = comparing,
                    enter = fadeIn(Fluid.fade()),
                    exit = fadeOut(Fluid.fade()),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Box(Modifier.padding(top = 12.dp).background(Color.Black.copy(alpha = 0.58f), ChromeCapsule).padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(stringResource(R.string.photo_original), style = ClearType.Label, color = Color.White)
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val ink = rememberGlassInk(Color.White)
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassCircle(onClick = close, size = 44.dp, ink = Color.White, label = stringResource(R.string.common_cancel)) {
                            GlassIcon(LucideIcons.X, null, Modifier.size(19.dp))
                        }
                        ModeSwitch(mode, Modifier.weight(1f)) {
                            if (typing != null) finishTyping()
                            if (it != mode) feel(Feel.Tick)
                            mode = it
                            if (it != Mode.Text) chosen = null
                        }
                        GlassCircle(
                            onClick = done,
                            size = 44.dp,
                            tint = if (changed) Candy.Sky else Color.Unspecified,
                            ink = Color.White,
                            label = stringResource(R.string.common_done),
                        ) {
                            GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp))
                        }
                    }
                    AnimatedVisibility(
                        visible = changed,
                        enter = fadeIn(Fluid.quick()),
                        exit = fadeOut(Fluid.quick()),
                        modifier = Modifier.align(Alignment.End).padding(end = 14.dp),
                    ) {
                        GlassPill(onClick = { reset() }, height = 32.dp, horizontalPadding = 12.dp, label = stringResource(R.string.photo_reset)) {
                            Text(stringResource(R.string.photo_reset), style = ClearType.Label.copy(fontSize = 12.sp), color = Candy.Lemon)
                        }
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalGlassInk provides ink.color) {
                        // each bar its own glass (and its own press light), so only the one touched moves
                        fun bar() = Modifier.fillMaxWidth().then(ink.modifier)
                        when (mode) {
                            Mode.Adjust -> {
                                Row(bar().height(72.dp).glassBar().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val value = values[selected] ?: 0f
                                    ValueReadout(stringResource(selected.label), shown(selected, value), value != 0f, ink.color)
                                    FeltSlider(value, selected.min..selected.max, ink.color, Modifier.weight(1f), key = selected) { next ->
                                        values[selected] = if (abs(next) < (selected.max - selected.min) * 0.012f) 0f else next
                                    }
                                }
                                LazyRow(
                                    modifier = bar().height(70.dp).glassBar(),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    items(Adjust.entries, key = { it.name }) { adjustment ->
                                        ToolTab(adjustment.icon, stringResource(adjustment.label), adjustment == selected, dot = (values[adjustment] ?: 0f) != 0f) { selected = adjustment }
                                    }
                                }
                            }
                            Mode.Crop -> {
                                Row(bar().height(72.dp).glassBar().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ValueReadout(stringResource(R.string.photo_straighten), degrees(frame.angle), frame.angle != 0f, ink.color)
                                    StraightenDial(frame.angle, ink.color, Modifier.weight(1f)) { setAngle(it) }
                                }
                                Row(bar().height(62.dp).glassBar().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RoundTool(LucideIcons.RotateCw, stringResource(R.string.photo_rotate)) { turn() }
                                    RoundTool(Icons.Rounded.Flip, stringResource(R.string.photo_mirror)) { mirror() }
                                    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(ink.color.copy(alpha = 0.2f)))
                                    LazyRow(
                                        Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        itemsIndexed(SHAPES) { i, (w, h) ->
                                            val label = when {
                                                w == 0f -> stringResource(R.string.photo_free)
                                                w < 0f -> stringResource(R.string.photo_shape_original)
                                                else -> "${w.toInt()}:${h.toInt()}"
                                            }
                                            Chip(label, on = shape == i) { pickShape(i) }
                                        }
                                    }
                                }
                            }
                            Mode.Blur -> {
                                Row(bar().height(72.dp).glassBar().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ValueReadout(stringResource(R.string.photo_brush), "${(brush * 100).roundToInt()}", false, ink.color)
                                    FeltSlider(brush, 0.02f..0.16f, ink.color, Modifier.weight(1f), key = "brush") { brush = it }
                                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                        val dot by animateDpAsState((10f + (brush - 0.02f) / 0.14f * 24f).dp, Fluid.quick(), label = "brushDot")
                                        Box(Modifier.size(dot).clip(CircleShape).background(ink.color.copy(alpha = 0.85f)))
                                    }
                                }
                                Row(bar().height(62.dp).glassBar().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Chip(stringResource(R.string.photo_blur), smudgeKind == SMUDGE_BLUR, Icons.Rounded.BlurOn) { smudgeKind = SMUDGE_BLUR }
                                    Chip(stringResource(R.string.photo_pixelate), smudgeKind == SMUDGE_PIXELS, Icons.Rounded.GridOn) { smudgeKind = SMUDGE_PIXELS }
                                    Chip(stringResource(R.string.photo_erase), smudgeKind == SMUDGE_ERASE, LucideIcons.BrushCleaning) { smudgeKind = SMUDGE_ERASE }
                                    Spacer(Modifier.weight(1f))
                                    val strokes = marksVersion >= 0 && marks.any { it is Smudge }
                                    RoundTool(LucideIcons.Undo2, stringResource(R.string.common_undo), enabled = strokes) {
                                        marks.lastOrNull { it is Smudge }?.let { marks.remove(it) }
                                        marksVersion++
                                        feel(Feel.Tick)
                                    }
                                }
                            }
                            Mode.Text -> {
                                val l = chosen
                                Row(bar().height(72.dp).glassBar().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (l == null) {
                                        Text(
                                            stringResource(R.string.photo_text_help), style = ClearType.Label, color = ink.color.copy(alpha = 0.72f),
                                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        )
                                    } else {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            items(INKS) { col ->
                                                val on = marksVersion >= 0 && l.color == col.toArgb()
                                                Box(
                                                    Modifier.size(34.dp).border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                                                        .padding(4.dp).clip(CircleShape).background(col)
                                                        .pressable({
                                                            l.color = col.toArgb()
                                                            lastInk = l.color
                                                            marksVersion++
                                                            feel(Feel.Tick)
                                                        }, pressedScale = 0.85f),
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(bar().height(62.dp).glassBar().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GlassPill(
                                        onClick = add@{
                                            val pl = place ?: return@add
                                            val b = pl.back(pl.outW / 2f, pl.outH / 2f)
                                            val n = Label("", b.x, b.y, rotation = -frame.angle, color = lastInk, style = lastStyle, font = lastFont)
                                            marks.add(n)
                                            chosen = n
                                            startTyping(n, isNew = true)
                                        },
                                        tint = Candy.Sky,
                                        height = 42.dp,
                                        horizontalPadding = 14.dp,
                                        label = stringResource(R.string.photo_add_text),
                                    ) {
                                        Icon(LucideIcons.Plus, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.photo_add_text), style = ClearType.Label, color = Color.White)
                                    }
                                    if (l != null) {
                                        Spacer(Modifier.weight(1f))
                                        // the font, shown in itself
                                        Box(
                                            Modifier.size(42.dp).clip(CircleShape).pressable({
                                                l.font = (l.font + 1) % LABEL_FONTS
                                                lastFont = l.font
                                                marksVersion++
                                                feel(Feel.Tick)
                                            }, pressedScale = 0.88f),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(stringResource(R.string.photo_font_sample), style = TextStyle(fontFamily = fontFamily(if (marksVersion >= 0) l.font else 0), fontWeight = FontWeight.Bold, fontSize = 17.sp), color = ink.color)
                                        }
                                        // plain, on a tag, outlined
                                        Box(
                                            Modifier.size(42.dp).clip(CircleShape).pressable({
                                                l.style = (l.style + 1) % LABEL_STYLES
                                                lastStyle = l.style
                                                marksVersion++
                                                feel(Feel.Tick)
                                            }, pressedScale = 0.88f),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            StyleGlyph(if (marksVersion >= 0) l.style else 0, ink.color)
                                        }
                                        RoundTool(LucideIcons.Pencil, stringResource(R.string.mindmap_edit)) { startTyping(l, isNew = false) }
                                        RoundTool(LucideIcons.Trash2, stringResource(R.string.common_delete), tint = Candy.Tomato) {
                                            marks.remove(l)
                                            chosen = null
                                            marksVersion++
                                            feel(Feel.Fall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }

            // ---- writing: the words big over the dimmed picture, the keyboard up
            typing?.let { l ->
                val focus = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                // the keyboard put away ends the typing, so no cursor is left blinking over the picture
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                val imeOpen = WindowInsets.isImeVisible
                var imeWasOpen by remember { mutableStateOf(false) }
                LaunchedEffect(imeOpen) {
                    if (imeOpen) imeWasOpen = true
                    else if (imeWasOpen) {
                        imeWasOpen = false
                        finishTyping()
                    }
                }
                LaunchedEffect(l) {
                    delay(60)
                    runCatching { focus.requestFocus() }
                    keyboard?.show()
                }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.64f)).pointerInput(l) { detectTapGestures { finishTyping() } }.navigationBarsPadding().imePadding(),
                ) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                        GlassPill(onClick = { finishTyping() }, tint = Candy.Sky, height = 42.dp, label = stringResource(R.string.common_done)) {
                            Text(stringResource(R.string.common_done), style = ClearType.BodyStrong, color = Color.White)
                        }
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp).focusRequester(focus),
                        textStyle = TextStyle(
                            color = Color(if (marksVersion >= 0) l.color else 0),
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontFamily = fontFamily(l.font),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(Candy.Sky),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (typed.isEmpty()) {
                                    Text(
                                        stringResource(R.string.photo_text_hint),
                                        style = TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = 30.sp, fontFamily = fontFamily(l.font), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    LazyRow(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(INKS) { col ->
                            val on = l.color == col.toArgb()
                            Box(
                                Modifier.size(32.dp).border(if (on) 2.5.dp else 1.5.dp, if (on) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                                    .padding(3.dp).clip(CircleShape).background(col)
                                    .pressable({
                                        l.color = col.toArgb()
                                        lastInk = l.color
                                        marksVersion++
                                    }, pressedScale = 0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Corner(val x: Float, val y: Float, val sx: Float, val sy: Float)

/** The frame a picture starts in: turned and mirrored as its EXIF orientation says (1 to 8). */
private fun exifFrame(file: File): PhotoFrame {
    val o = runCatching {
        androidx.exifinterface.media.ExifInterface(file.absolutePath).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
    }.getOrDefault(1)
    // EXIF's eight orientations as (mirrored, quarter turns clockwise), LightCraft's `to_parts`
    val (mirror, turns) = when (o) {
        2 -> true to 0
        3 -> false to 2
        4 -> true to 2
        5 -> true to 3
        6 -> false to 1
        7 -> true to 1
        8 -> false to 3
        else -> false to 0
    }
    return PhotoFrame(turns = turns, mirror = mirror)
}

/** The switch between Adjust, Crop, Blur and Text: a lit capsule slides to the one in use. */
@Composable
private fun ModeSwitch(mode: Mode, modifier: Modifier, onPick: (Mode) -> Unit) {
    val ink = rememberGlassInk(Color.White)
    BoxWithConstraints(modifier.height(48.dp).then(ink.modifier).glassBar().padding(4.dp)) {
        val each = maxWidth / Mode.entries.size
        val x by animateDpAsState(each * mode.ordinal, spring(dampingRatio = 0.78f, stiffness = 520f), label = "modeLight")
        Box(Modifier.offset(x = x).width(each).fillMaxHeight().clip(ChromeCapsule).background(Candy.Sky))
        Row(Modifier.fillMaxSize()) {
            Mode.entries.forEach { m ->
                val on = m == mode
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(ChromeCapsule).pressable({ onPick(m) }, pressedScale = 0.92f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(m.icon, null, tint = if (on) Color.White else ink.color.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                    Text(
                        stringResource(m.label), style = ClearType.Label.copy(fontSize = 9.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) Color.White else ink.color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueReadout(label: String, value: String, lit: Boolean, ink: Color) {
    Column(Modifier.width(76.dp)) {
        Text(label, style = ClearType.Label, color = ink.copy(alpha = 0.66f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = ClearType.Title.copy(fontSize = 21.sp), color = if (lit) Candy.Lemon else ink, maxLines = 1)
    }
}

/** A slider felt under the thumb: a light tick every fortieth of the way, a firm one through zero. */
@Composable
private fun FeltSlider(value: Float, range: ClosedFloatingPointRange<Float>, ink: Color, modifier: Modifier, key: Any, onChange: (Float) -> Unit) {
    val feel = rememberFeel()
    val notch = remember(key) { intArrayOf(Int.MIN_VALUE) }
    Slider(
        value = value,
        onValueChange = { v ->
            val at = Math.round((v - range.start) / (range.endInclusive - range.start).coerceAtLeast(0.0001f) * 40f)
            if (at != notch[0]) {
                if (notch[0] != Int.MIN_VALUE) {
                    val throughZero = range.start < 0f && ((value < 0f) != (v < 0f) || v == 0f)
                    feel(if (throughZero) Feel.Tap else Feel.Tick)
                }
                notch[0] = at
            }
            onChange(v)
        },
        valueRange = range,
        modifier = modifier,
        colors = SliderDefaults.colors(thumbColor = Candy.Sky, activeTrackColor = Candy.Sky, inactiveTrackColor = ink.copy(alpha = 0.22f)),
    )
}

/**
 * The straighten dial: a ruler of degrees that slides under a fixed blue needle. A tick is felt at
 * every degree and a firmer one at level; a double tap levels it again.
 */
@Composable
private fun StraightenDial(angle: Float, ink: Color, modifier: Modifier, onChange: (Float) -> Unit) {
    val feel = rememberFeel()
    val density = LocalDensity.current.density
    val perDegree = 9f * density
    val current by rememberUpdatedState(angle)
    Box(
        modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (current != 0f) feel(Feel.Tap)
                    onChange(0f)
                })
            }
            .pointerInput(Unit) {
                var raw = 0f
                detectHorizontalDragGestures(
                    onDragStart = { raw = current },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        raw = (raw - dx / perDegree).coerceIn(-45f, 45f)
                        val next = if (abs(raw) < 0.6f) 0f else (raw * 10f).roundToInt() / 10f
                        val before = current
                        if (next != before) {
                            if (floor(next) != floor(before)) feel(if (next == 0f || before == 0f) Feel.Tap else Feel.Tick)
                            onChange(next)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val mid = size.height / 2f
            for (deg in -45..45) {
                val x = cx + (deg - angle) * perDegree
                if (x < 0f || x > size.width) continue
                val fade = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
                val major = deg % 5 == 0
                val h = (if (deg == 0) 22f else if (major) 15f else 8f) * density
                drawLine(
                    (if (deg == 0) Candy.Lemon else ink).copy(alpha = (if (major) 0.85f else 0.45f) * fade),
                    Offset(x, mid - h / 2f), Offset(x, mid + h / 2f), (if (major) 1.6f else 1.1f) * density, StrokeCap.Round,
                )
            }
            drawLine(Candy.Sky, Offset(cx, mid - 15f * density), Offset(cx, mid + 15f * density), 3f * density, StrokeCap.Round)
            drawCircle(Candy.Sky, 3f * density, Offset(cx, mid - 18f * density))
        }
    }
}

/** An icon over its name, lit when in use; a dot when it changes the picture. */
@Composable
private fun ToolTab(icon: ImageVector, label: String, selected: Boolean, dot: Boolean = false, onClick: () -> Unit) {
    val ink = LocalGlassInk.current
    Column(
        Modifier.width(68.dp).height(62.dp).pressable(onClick, pressedScale = 0.9f).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(34.dp).clip(ChromeCapsule).background(if (selected) Candy.Sky else Color.Transparent), contentAlignment = Alignment.Center) {
                GlassIcon(icon, null, Modifier.size(19.dp), tint = if (selected) Color.White else ink)
            }
            if (dot && !selected) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(Candy.Lemon))
        }
        Text(
            label,
            style = ClearType.Label.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) ink else ink.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A capsule to pick, lit when picked. */
@Composable
private fun Chip(label: String, on: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val ink = LocalGlassInk.current
    Row(
        Modifier.height(38.dp).clip(ChromeCapsule).background(if (on) Candy.Sky else ink.copy(alpha = 0.08f))
            .pressable(onClick, pressedScale = 0.92f).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (on) Color.White else ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = ClearType.Label.copy(fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) Color.White else ink, maxLines = 1)
    }
}

/** A round icon button inside a bar. */
@Composable
private fun RowScope.RoundTool(icon: ImageVector, label: String, tint: Color = LocalGlassInk.current, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).pressable({ if (enabled) onClick() }, pressedScale = 0.88f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(19.dp))
    }
}

/** How words sit: plain, on a filled tag, or outlined. */
@Composable
private fun StyleGlyph(style: Int, ink: Color) {
    when (style) {
        1 -> Box(Modifier.size(24.dp, 20.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(ink), contentAlignment = Alignment.Center) {
            Text("A", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 13.sp), color = Color(0xFF141414))
        }
        2 -> Box(Modifier.size(24.dp, 20.dp).border(1.5.dp, ink, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("A", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 13.sp), color = ink)
        }
        else -> Text("A", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 18.sp), color = ink)
    }
}

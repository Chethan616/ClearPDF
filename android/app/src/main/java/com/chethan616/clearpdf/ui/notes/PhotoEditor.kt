package com.chethan616.clearpdf.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.PhotoEditor
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassChip
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassSlider
import com.chethan616.clearpdf.ui.glass.LocalBackdrop
import com.chethan616.clearpdf.ui.glass.glassBar
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

/** One adjustment of LightCraft's develop settings: its section, field, label and range. */
private enum class Adjust(val section: String, val field: String, val label: Int, val min: Float, val max: Float) {
    Exposure("light", "exposure", R.string.photo_exposure, -3f, 3f),
    Contrast("light", "contrast", R.string.photo_contrast, -100f, 100f),
    Highlights("light", "highlights", R.string.photo_highlights, -100f, 100f),
    Shadows("light", "shadows", R.string.photo_shadows, -100f, 100f),
    Vibrance("color", "vibrance", R.string.photo_vibrance, -100f, 100f),
    Saturation("color", "saturation", R.string.photo_saturation, -100f, 100f),
}

/** The quarter turns LightCraft's orientation names, clockwise from as shot. */
private val TURNS = listOf("Normal", "Rotate90", "Rotate180", "Rotate270")

/**
 * A note's picture developed with LightCraft: the picture large, glass chips to pick an adjustment
 * and a glass slider for it, a quarter turn, and Done, which saves the developed picture in place of
 * the note's (a new file, so the desk's thumbnails see the change).
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
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun settings(): String {
        val o = JSONObject()
        for ((a, v) in values) if (v != 0f) (o.optJSONObject(a.section) ?: JSONObject().also { o.put(a.section, it) }).put(a.field, v.toDouble())
        if (turn != 0) o.put("orientation", TURNS[turn])
        return o.toString()
    }

    // re-develop as the sliders move; a newer value cancels the render still running
    LaunchedEffect(Unit) {
        snapshotFlow { values.toMap() to turn }.collectLatest {
            val json = settings()
            shown = withContext(Dispatchers.Default) { runCatching { editor.render(source.absolutePath, json, 1600u).toBitmap().asImageBitmap() }.getOrNull() } ?: shown
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop), contentAlignment = Alignment.Center) {
            shown?.let { Image(it, null, Modifier.fillMaxSize().padding(vertical = 120.dp), contentScale = ContentScale.Fit) }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = onClose, label = stringResource(R.string.common_cancel), ink = Color.White) { GlassIcon(LucideIcons.X, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.weight(1f))
                    GlassCircle(onClick = { turn = (turn + 1) % 4 }, label = stringResource(R.string.photo_rotate), ink = Color.White) { GlassIcon(LucideIcons.RotateCw, null, Modifier.size(20.dp)) }
                    Spacer(Modifier.size(10.dp))
                    GlassCircle(
                        onClick = {
                            if (saving) return@GlassCircle
                            saving = true
                            scope.launch {
                                val out = File(source.parentFile, "edited-${System.currentTimeMillis()}.jpg")
                                val ok = withContext(Dispatchers.Default) { runCatching { editor.exportJpeg(source.absolutePath, settings(), out.absolutePath, 92u) }.isSuccess }
                                if (ok) vm.notes.get(noteId)?.let { n -> vm.notes.put(n.copy(images = n.images.map { if (it == name) out.name else it })) }
                                saving = false
                                onClose()
                            }
                        },
                        tint = Candy.Sky, label = stringResource(R.string.common_done),
                    ) { GlassIcon(LucideIcons.Check, null, Modifier.size(20.dp)) }
                }
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().glassBar(com.chethan616.clearpdf.ui.glass.RoundedRectangle(28.dp)).padding(16.dp)) {
                    Text(
                        stringResource(chosen.label) + "  " + "%.2f".format(values[chosen] ?: 0f).trimEnd('0').trimEnd('.'),
                        style = ClearType.Caption.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.size(8.dp))
                    GlassSlider(values[chosen] ?: 0f, { values[chosen] = it }, range = chosen.min..chosen.max, accent = Candy.Sky)
                    Spacer(Modifier.size(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in Adjust.entries) {
                            GlassChip(
                                onClick = { chosen = a },
                                fill = if (a == chosen) ({ Candy.Lemon }) else null,
                                ink = if (a == chosen) Color(0xFF172033) else Color.White,
                                onLongClick = { values.remove(a) },
                            ) { Text(stringResource(a.label), style = ClearType.Body.copy(fontSize = 14.sp)) }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

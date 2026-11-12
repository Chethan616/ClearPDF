package com.chethan616.clearpdf.ui.desk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.chethan616.clearpdf.ui.home.chipRowFade
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.HomeTab
import com.chethan616.clearpdf.ui.home.cardPaint
import com.chethan616.clearpdf.ui.home.riseIn
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.Tone
import com.chethan616.clearpdf.ui.theme.clear

private val Categories = listOf(
    "All" to R.string.home_category_all,
    "Read" to R.string.home_category_read,
    "Create" to R.string.home_category_create,
    "Edit" to R.string.home_category_edit,
    "Convert" to R.string.home_category_convert,
)

/** Tile heights, tallest first: the two columns read this at opposite phases so the bento never lines up. */
private val Rhythm = listOf(196.dp, 144.dp, 168.dp, 144.dp, 196.dp, 144.dp)

private val TileGap = 12.dp
private val TilePadding = 16.dp

/** The tile is tall enough to also say what the tool does. */
private val RoomForAbout = 160.dp

/** One tone per tool, chosen so neighbours in the bento never share a colour. */
private fun toneOf(t: ToolDef): Tone = when (t.key) {
    "edit" -> Tone.Blush
    "sign" -> Tone.Lavender
    "scan" -> Tone.Peach
    "combine" -> Tone.Sky
    "comment" -> Tone.Butter
    "read" -> Tone.Sage
    "organize" -> Tone.Lavender
    "compress" -> Tone.Peach
    "export" -> Tone.Orchid
    "crop" -> Tone.Sky
    "protect" -> Tone.Sage
    "create" -> Tone.Butter
    "pdf2text" -> Tone.Blush
    "photo" -> Tone.Lavender
    "text2pdf" -> Tone.Sky
    "fields" -> Tone.Peach
    else -> Tone.Paper
}

/**
 * Tools: the desk's own language. A soft serif title, the category chips, then every tool as a pastel
 * tile in a two-column bento of uneven heights: its name set big in serif like "Files" and "Pdf", its
 * glyph turned and cropped at the corner, and (on the tall tiles) a line on what it does.
 */
@Composable
fun ToolsScreen(vm: AppViewModel) {
    var cat by rememberSaveable { mutableStateOf("All") }
    val tools = ToolCatalog.All.filter { cat == "All" || cat in it.categories }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "header") {
            Spacer(Modifier.height(16.dp))
            ScreenHeader(null, stringResource(R.string.home_tab_tools), titleStyle = ClearType.ToolsTitle, onBack = { vm.homeTab = HomeTab.Home })
        }
        item(key = "chips") {
            LazyRow(Modifier.chipRowFade(),contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categories, key = { it.first }) { (k, label) -> com.chethan616.clearpdf.ui.home.DeskChip(stringResource(label), k == cat) { cat = k } }
            }
        }
        item(key = "bento") {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val names = ToolCatalog.All.map { stringResource(it.label) }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val room = (maxWidth - TileGap) / 2 - TilePadding * 2
                // one size for every tile (whatever the filter): the largest at which the longest word of any name fits
                val wordSize = remember(names, room, density.density, density.fontScale) {
                    val roomPx = with(density) { room.toPx() }
                    var s = ClearType.ToolWord.fontSize.value
                    while (s > 18f && names.any { n ->
                            n.split(' ').any { w -> measurer.measure(w, ClearType.ToolWord.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > roomPx }
                        }
                    ) s -= 1f
                    s.sp
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    for (col in 0..1) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TileGap)) {
                            tools.forEachIndexed { i, t ->
                                if (i % 2 != col) return@forEachIndexed
                                key(t.key) { ToolTile(t, Rhythm[(i / 2 + col * 3) % Rhythm.size], wordSize, i) { t.go(vm) } }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun ToolTile(t: ToolDef, height: Dp, wordSize: TextUnit, index: Int, onClick: () -> Unit) {
    val tone = clear.tone(toneOf(t))
    val label = stringResource(t.label)
    val about = stringResource(t.about)
    Box(
        Modifier.fillMaxWidth().height(height).riseIn(index)
            .semantics { contentDescription = "$label. $about" }
            .pressable(onClick, pressedScale = 0.95f)
            .cardPaint(tone, Offset(0.15f, 1f)),
    ) {
        Icon(
            t.icon, null,
            tint = tone.accent.copy(alpha = if (clear.dark) 0.34f else 0.26f),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = (-4).dp).size(88.dp).rotate(-12f),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = TilePadding, vertical = 14.dp)) {
            Text(label, style = ClearType.ToolWord.copy(fontSize = wordSize), color = tone.accent, maxLines = 2)
            if (height >= RoomForAbout) {
                Spacer(Modifier.height(4.dp))
                Text(about, style = ClearType.Caption, color = tone.ink.copy(alpha = 0.66f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

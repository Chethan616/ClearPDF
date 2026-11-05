package com.chethan616.clearpdf.ui.desk

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.glass.wash
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

private val Categories = listOf(
    "All" to R.string.home_category_all,
    "Read" to R.string.home_category_read,
    "Create" to R.string.home_category_create,
    "Edit" to R.string.home_category_edit,
    "Convert" to R.string.home_category_convert,
)

/**
 * Tools: a big title, candy category chips, the featured tool as a wide hero tile, then every tool
 * as a card with its candy blob, name and one line about it.
 */
@Composable
fun ToolsScreen(vm: AppViewModel) {
    val c = clear
    var cat by rememberSaveable { mutableStateOf("All") }
    val tools = ToolCatalog.All.filter { cat == "All" || cat in it.categories }
    val featured = if (cat == "All") ToolCatalog.Edit else null
    val grid = tools.filter { it !== featured }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "header") {
            Spacer(Modifier.height(16.dp))
            ScreenHeader(stringResource(R.string.tools_subtitle), stringResource(R.string.home_tab_tools), onBack = { vm.homeTab = com.chethan616.clearpdf.ui.home.HomeTab.Home })
        }
        item(key = "chips") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categories, key = { it.first }) { (k, label) -> Chip(stringResource(label), k == cat, chipAccent(k)) { cat = k } }
            }
        }
        if (featured != null) item(key = "featured") { FeaturedTile(featured) { featured.go(vm) } }
        items(grid.chunked(2), key = { row -> row.joinToString { it.key } }) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { t -> ToolCard(t, Modifier.weight(1f)) { t.go(vm) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

private fun chipAccent(k: String) = when (k) {
    "Read" -> Candy.Sky
    "Create" -> Candy.Tomato
    "Edit" -> Candy.Bubblegum
    "Convert" -> Candy.Mint
    else -> Candy.Grape
}

/** A category chip: a wash capsule, filled with its candy once chosen. */
@Composable
private fun Chip(label: String, chosen: Boolean, accent: Color, onClick: () -> Unit) {
    val c = clear
    val fill by animateColorAsState(if (chosen) accent else Color.Transparent, GlassMotion.settle(), label = "chip")
    Box(
        Modifier.pressable(onClick).wash(Capsule).background(fill, Capsule).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ClearType.BodyStrong, color = if (chosen) Color.White else c.ink) }
}

/** The featured tool: a wide candy tile with a bubble of light and a big headline. */
@Composable
private fun FeaturedTile(t: ToolDef, onClick: () -> Unit) {
    val shape = RoundedRectangle(32.dp)
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(168.dp)
            .pressable(onClick, pressedScale = 0.95f).clip(shape)
            .background(Brush.linearGradient(listOf(t.accent, Candy.Grape)))
            .padding(22.dp),
    ) {
        Box(Modifier.align(Alignment.TopEnd).size(150.dp).graphicsLayer { translationX = 50.dp.toPx(); translationY = (-60).dp.toPx() }.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
        Box(Modifier.align(Alignment.BottomEnd).size(70.dp).graphicsLayer { translationX = 10.dp.toPx(); translationY = 24.dp.toPx() }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
        Column {
            Text(stringResource(R.string.tools_featured).uppercase(), style = ClearType.Eyebrow, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(t.label), style = ClearType.Display, color = Color.White)
        }
        Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(t.about), style = ClearType.Label, color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(end = 64.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = t.accent, modifier = Modifier.size(26.dp))
        }
    }
}

/** A tool's card: a candy blob with its glyph, its name in display type, and one line about it. */
@Composable
private fun ToolCard(t: ToolDef, modifier: Modifier, onClick: () -> Unit) {
    val c = clear
    Column(modifier.heightIn(min = 168.dp).pressable(onClick).wash(RoundedRectangle(28.dp)).padding(16.dp)) {
        Box(
            Modifier.size(52.dp).clip(RoundedRectangle(17.dp)).background(Brush.linearGradient(listOf(t.accent.copy(alpha = 0.85f), t.accent))),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(t.label), style = ClearType.Tile, color = c.ink, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(t.about), style = ClearType.Caption, color = c.inkSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

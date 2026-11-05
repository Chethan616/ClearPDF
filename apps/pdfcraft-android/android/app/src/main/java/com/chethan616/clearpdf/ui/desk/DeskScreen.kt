package com.chethan616.clearpdf.ui.desk

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.engine.RecentFile
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCard
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.FileRow
import com.chethan616.clearpdf.ui.home.HomeTab
import com.chethan616.clearpdf.ui.home.displayName
import com.chethan616.clearpdf.ui.home.whenLabel
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How much room the floating dock takes at the bottom of every tab. */
val DockClearance = 112.dp

/**
 * The desk: a big greeting, the document you were reading, a bento of candy tools, and a shelf of
 * recent papers dropped at playful angles.
 */
@Composable
fun DeskScreen(vm: AppViewModel, onBrowse: () -> Unit) {
    val recents = vm.recentRows()
    val last = vm.recentList.firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dateLine().uppercase(), style = ClearType.Eyebrow, color = clear.inkSoft, modifier = Modifier.weight(1f))
                SettingsButton(vm)
            }
        }
        item(key = "hero") { Greeting(hasDocument = last != null) }
        if (last != null && recents.isNotEmpty()) {
            item(key = "continue") { ContinueCard(vm, last, recents.first()) }
        }
        item(key = "make") {
            SectionTitle(stringResource(R.string.desk_make_something), action = stringResource(R.string.desk_all_tools)) { vm.homeTab = HomeTab.Tools }
            Bento(vm)
        }
        item(key = "shelf") {
            SectionTitle(stringResource(R.string.desk_on_your_desk), action = if (recents.isNotEmpty()) stringResource(R.string.desk_see_all) else null) { vm.homeTab = HomeTab.Files }
            if (recents.isEmpty()) {
                EmptyHero(
                    stringResource(R.string.desk_empty_title), stringResource(R.string.desk_empty_body),
                    stringResource(R.string.home_open_file), Candy.Tomato, onBrowse,
                )
            } else {
                Shelf(vm, recents.drop(if (last != null) 1 else 0).take(12).ifEmpty { recents.take(1) })
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

private fun dateLine(): String = SimpleDateFormat("EEEE  ·  d MMMM", Locale.getDefault()).format(Date())

/** The hero: "Good evening," in ink, the tagline in a candy gradient, rising in on a soft spring. */
@Composable
private fun Greeting(hasDocument: Boolean) {
    val c = clear
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val hello = stringResource(
        when (hour) {
            in 5..11 -> R.string.desk_greeting_morning
            in 12..16 -> R.string.desk_greeting_afternoon
            in 17..22 -> R.string.desk_greeting_evening
            else -> R.string.desk_greeting_night
        },
    )
    val tagline = stringResource(if (hasDocument) R.string.desk_tagline_reading else R.string.desk_tagline_empty)
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, GlassMotion.rise()) }
    val gradient = Brush.linearGradient(listOf(Candy.Tomato, Candy.Bubblegum, Candy.Grape))
    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp)
            .graphicsLayer {
                translationY = (1f - rise.value) * 28.dp.toPx()
                alpha = rise.value.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Text(hello, style = ClearType.Hero, color = c.ink)
        Text(
            buildAnnotatedString { withStyle(SpanStyle(brush = gradient)) { append(tagline) } },
            style = ClearType.Hero,
        )
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    val c = clear
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ClearType.Headline, color = c.ink, modifier = Modifier.weight(1f))
        if (action != null) {
            GlassPill(onClick = onAction, height = 36.dp, horizontalPadding = 14.dp) {
                Text(action, style = ClearType.Label, color = c.ink)
                GlassIcon(LucideIcons.ChevronRight, null, Modifier.size(16.dp))
            }
        }
    }
}

/**
 * The document you were reading: its tilted first page, its name, where you are in it, and a
 * chunky candy progress bar.
 */
@Composable
private fun ContinueCard(vm: AppViewModel, last: RecentFile, row: FileRow) {
    val c = clear
    val pages = last.pages.coerceAtLeast(1)
    val page = (last.lastPage + 1).coerceIn(1, pages)
    val progress = page / pages.toFloat()
    GlassCard(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().pressable(row.open),
        shape = RoundedRectangle(32.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp, 132.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(92.dp, 120.dp).graphicsLayer { rotationZ = 6f; translationX = 8.dp.toPx() }.clip(RoundedRectangle(8.dp)).background(Candy.Lemon.copy(alpha = 0.7f)))
                PaperThumb(vm, row.thumbPath, 92.dp, 120.dp, tilt = -4f)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.desk_continue).uppercase(), style = ClearType.Eyebrow, color = Candy.Tomato)
                Spacer(Modifier.height(6.dp))
                Text(displayName(row.name), style = ClearType.Title, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.desk_page_of, page, pages), style = ClearType.Label, color = c.inkSoft)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(Capsule).background(c.ink.copy(alpha = 0.08f))) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0.06f, 1f)).fillMaxHeight().clip(Capsule)
                            .background(Brush.horizontalGradient(listOf(Candy.Tangerine, Candy.Tomato))),
                    )
                }
            }
        }
    }
}

/** A candy tile of the bento: a big glyph up top, the tool's name at the bottom, a lighter corner. */
@Composable
private fun CandyTile(icon: ImageVector, label: String, accent: Color, modifier: Modifier, big: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedRectangle(if (big) 30.dp else 26.dp)
    Box(
        modifier.pressable(onClick, pressedScale = 0.93f).clip(shape)
            .background(Brush.linearGradient(listOf(lighten(accent, 0.22f), accent, darken(accent, 0.08f))))
            .padding(if (big) 18.dp else 14.dp),
    ) {
        // a soft bubble of light in the corner, the playful bit
        Box(Modifier.align(Alignment.TopEnd).size(if (big) 90.dp else 56.dp).graphicsLayer { translationX = 26.dp.toPx(); translationY = (-26).dp.toPx() }.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 34.dp else 26.dp))
        Text(label, style = if (big) ClearType.Headline else ClearType.Tile, color = Color.White, modifier = Modifier.align(Alignment.BottomStart), maxLines = 2)
    }
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f, c.alpha)
private fun darken(c: Color, f: Float) = Color(c.red * (1f - f), c.green * (1f - f), c.blue * (1f - f), c.alpha)

/**
 * The bento: Scan as a tall hero tile, Combine and Fill & Sign beside it, then a row of three.
 */
@Composable
private fun Bento(vm: AppViewModel) {
    val gap = 12.dp
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().height(232.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val scan = ToolCatalog.Scan
            CandyTile(scan.icon, stringResource(scan.label), scan.accent, Modifier.weight(1.08f).fillMaxHeight(), big = true) { scan.go(vm) }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (t in listOf(ToolCatalog.Combine, ToolCatalog.FillSign)) {
                    CandyTile(t.icon, stringResource(t.label), t.accent, Modifier.weight(1f).fillMaxWidth()) { t.go(vm) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (t in listOf(ToolCatalog.Photos, ToolCatalog.Edit, ToolCatalog.Compress)) {
                CandyTile(t.icon, stringResource(t.label), t.accent, Modifier.weight(1f).fillMaxHeight()) { t.go(vm) }
            }
        }
    }
}

private val Tilts = floatArrayOf(-4f, 3f, -2f, 4.5f, -3.5f, 2f)

/** Recent papers on a shelf, each dropped at its own angle; a press straightens it. */
@Composable
private fun Shelf(vm: AppViewModel, rows: List<FileRow>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(rows, key = { _, r -> r.key }) { i, r -> ShelfPaper(vm, r, Tilts[i % Tilts.size]) }
    }
}

@Composable
private fun ShelfPaper(vm: AppViewModel, r: FileRow, tilt: Float) {
    val c = clear
    Column(Modifier.width(124.dp).pressable(r.open, onLongClick = { vm.fileSheet = r to true }, pressedScale = 1.04f).padding(vertical = 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f), contentAlignment = Alignment.Center) {
            PaperThumb(vm, r.thumbPath, 112.dp, 144.dp, tilt = tilt)
            if (r.starred) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Candy.Lemon),
                    contentAlignment = Alignment.Center,
                ) { Icon(LucideIcons.Star, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(displayName(r.name), style = ClearType.Label, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(whenLabel(r.date), style = ClearType.Caption, color = c.inkSoft, maxLines = 1)
    }
}

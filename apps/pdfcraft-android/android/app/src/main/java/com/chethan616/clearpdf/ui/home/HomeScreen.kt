package com.chethan616.clearpdf.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.notes.NoteKind
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassRow
import com.chethan616.clearpdf.ui.glass.GlassSheet
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.mascot.Mascot
import com.chethan616.clearpdf.ui.mascot.MascotMood
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.Tone
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.theme.glowing
import com.chethan616.clearpdf.ui.viewer.VoicePlayer
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

/** What the desk shows: everything, or one kind. */
enum class DeskFilter(@StringRes val chip: Int, @StringRes val title: Int) {
    All(R.string.desk_filter_all, R.string.desk_title_all),
    Notes(R.string.desk_filter_notes, R.string.desk_title_notes),
    Todo(R.string.desk_filter_todo, R.string.desk_title_todo),
    Pdfs(R.string.desk_filter_pdfs, R.string.desk_title_pdfs),
    Images(R.string.desk_filter_images, R.string.desk_title_images),
    Audio(R.string.desk_filter_audio, R.string.desk_title_audio),
}

/** One card on the desk. */
sealed interface DeskItem {
    val key: String

    data class Of(val note: Note) : DeskItem {
        override val key get() = "n-" + note.id
    }

    data class Picture(val note: Note) : DeskItem {
        override val key get() = "p-" + note.id
    }

    data object Files : DeskItem {
        override val key get() = "files"
    }

    data class Pdfs(val count: Int) : DeskItem {
        override val key get() = "pdfs"
    }

    data class Recent(val rows: List<FileRow>) : DeskItem {
        override val key get() = "recent"
    }

    data class Doc(val row: FileRow) : DeskItem {
        override val key get() = "d-" + row.key
    }
}

/** The desk's cards for [filter]: notes newest first, with the collections set in among them. */
fun deskItems(filter: DeskFilter, notes: List<Note>, recents: List<FileRow>): List<DeskItem> {
    val sorted = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    return when (filter) {
        DeskFilter.All -> {
            val out = sorted.map<Note, DeskItem> { DeskItem.Of(it) }.toMutableList()
            if (recents.isNotEmpty()) out.add(minOf(1, out.size), DeskItem.Recent(recents))
            out.add(minOf(3, out.size), DeskItem.Files)
            out.add(minOf(6, out.size), DeskItem.Pdfs(recents.size))
            out
        }
        DeskFilter.Notes -> sorted.filter { it.kind == NoteKind.Text }.map { DeskItem.Of(it) }
        DeskFilter.Todo -> sorted.filter { it.kind == NoteKind.List }.map { DeskItem.Of(it) }
        DeskFilter.Audio -> sorted.filter { it.kind == NoteKind.Voice }.map { DeskItem.Of(it) }
        DeskFilter.Images -> sorted.filter { it.images.isNotEmpty() }.map { DeskItem.Picture(it) }
        DeskFilter.Pdfs -> if (recents.isEmpty()) emptyList() else listOf(DeskItem.Files, DeskItem.Pdfs(recents.size)) + recents.map { DeskItem.Doc(it) }
    }
}

/** How much room the floating create capsule takes at the bottom. */
val DeskDockClearance = 128.dp

/**
 * The desk (the home screen): the Artefact mark and a menu, a big title that changes with the
 * chosen chip, the chips, then every note, list, voice memo and document as a bento of pastel
 * cards. The create capsule floats over it (see [CreateDock]).
 */
@Composable
fun HomeScreen(vm: AppViewModel, desk: DeskState) {
    var filter by rememberSaveable { mutableStateOf(DeskFilter.All) }
    val scope = rememberCoroutineScope()
    val player = remember { VoicePlayer(scope) }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var actionsFor by remember { mutableStateOf<Note?>(null) }
    val notes = vm.notes.all
    val recents = vm.recentRows()
    val items = remember(filter, notes, vm.recentList) { deskItems(filter, notes, recents) }
    val grid = rememberLazyStaggeredGridState()
    LaunchedEffect(filter) { grid.animateScrollToItem(0) }

    fun play(n: Note) {
        val f = n.audio?.let(vm.notes::file) ?: return
        if (playing == n.id) {
            player.toggle()
        } else if (player.load(f)) {
            playing = n.id
            player.toggle()
        }
    }

    CompositionLocalProvider(LocalHomeVm provides vm) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DeskDockClearance + 40.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top", span = StaggeredGridItemSpan.FullLine) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    DeskHeader(vm)
                }
            }
            item(key = "title", span = StaggeredGridItemSpan.FullLine) { DeskTitle(filter) }
            item(key = "chips", span = StaggeredGridItemSpan.FullLine) {
                FilterChips(filter) { filter = it }
            }
            itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                val m = Modifier.fillMaxWidth().riseIn(i)
                when (item) {
                    is DeskItem.Of -> when (item.note.kind) {
                        NoteKind.List -> ListCard(item.note, { vm.openNote = item.note.id }, { actionsFor = item.note }, { vm.notes.toggle(item.note.id, it.id) }, m)
                        NoteKind.Voice -> VoiceCard(
                            item.note, playing == item.note.id && player.playing,
                            if (playing == item.note.id && player.durationMs > 0) player.positionMs / player.durationMs.toFloat() else 0f,
                            { play(item.note) }, { actionsFor = item.note }, m,
                        )
                        NoteKind.Text -> NoteCard(item.note, { vm.openNote = item.note.id }, { actionsFor = item.note }, m)
                    }
                    is DeskItem.Picture -> {
                        val n = item.note
                        StackCard(
                            n.title.ifBlank { stringResource(R.string.desk_filter_images) }, shortDate(n.updated),
                            listOf(clear.tone(Tone.Blush), clear.tone(Tone.Peach)), { vm.openNote = n.id }, m, onLong = { actionsFor = n },
                            chips = {
                                if (n.images.size > 1) PhotoChip("+${n.images.size - 1}", modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                            },
                        ) { NoteImage(n.images.first(), Modifier.matchParentSize(), maxPx = 900) }
                    }
                    DeskItem.Files -> CollectionCard(stringResource(R.string.desk_word_files), clear.tone(Tone.Butter), { vm.filesShowLocal = true; vm.homeTab = HomeTab.Files }, m)
                    is DeskItem.Pdfs -> CollectionCard(stringResource(R.string.desk_word_pdf), clear.tone(Tone.Orchid), { vm.homeTab = HomeTab.Files }, m, height = 136.dp)
                    is DeskItem.Recent -> {
                        val top = item.rows.first()
                        StackCard(
                            stringResource(R.string.desk_recent), whenLabel(top.date), listOf(clear.tone(Tone.Blush), clear.tone(Tone.Sky)), top.open, m,
                            onLong = { vm.fileSheet = top to true },
                            chips = {
                                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.rows.size > 1) PhotoChip("+${item.rows.size - 1}") { vm.homeTab = HomeTab.Files }
                                    PhotoChip(stringResource(R.string.desk_chip_pdfs), LucideIcons.FileText) { vm.homeTab = HomeTab.Files }
                                }
                            },
                        ) { PageFill(vm, top.thumbPath) }
                    }
                    is DeskItem.Doc -> DocCard(
                        vm, item.row, clear.tone(listOf(Tone.Sky, Tone.Peach, Tone.Sage, Tone.Butter)[i % 4]), listOf(-3f, 4f, -2f, 3f)[i % 4],
                        { vm.fileSheet = item.row to true }, m,
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty-$filter", span = StaggeredGridItemSpan.FullLine) { DeskEmpty(vm, filter, desk) }
            }
        }
    }

    actionsFor?.let { n -> NoteActions(vm, n) { actionsFor = null } }
}

/** The mark on the left (it spins when tapped), the four-dot menu on the right. */
@Composable
private fun DeskHeader(vm: AppViewModel) {
    val c = clear
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.artefact_mark), stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(c.hero),
            modifier = Modifier.height(50.dp).width(56.dp)
                .graphicsLayer { rotationZ = spin.value; val s = 1f + 0.12f * kotlin.math.sin(Math.toRadians(spin.value.toDouble() / 2).toFloat()); scaleX = s; scaleY = s }
                .pressable({ scope.launch { spin.snapTo(0f); spin.animateTo(360f, GlassMotion.morph()) } }, pressedScale = 0.85f),
        )
        Spacer(Modifier.weight(1f))
        GlassCircle(
            onClick = { menu = true }, size = 54.dp, surface = Color.White.copy(alpha = if (c.dark) 0.08f else 0.18f),
            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }.border(1.dp, c.rim, androidx.compose.foundation.shape.CircleShape),
            label = stringResource(R.string.common_more_options),
        ) { FourDots(c.hero) }
    }
    if (menu) {
        GlassMenu(
            anchor, { menu = false },
            listOf(
                GlassMenuItem(stringResource(R.string.home_tab_files), LucideIcons.FolderOpen) { vm.homeTab = HomeTab.Files },
                GlassMenuItem(stringResource(R.string.home_tab_tools), LucideIcons.LayoutGrid) { vm.homeTab = HomeTab.Tools },
                GlassMenuItem(stringResource(R.string.home_settings), LucideIcons.Settings) { vm.settingsOpen = true },
            ),
        )
    }
}

/** The big white title; a new chip slides a new one up in its place. */
@Composable
private fun DeskTitle(filter: DeskFilter) {
    val c = clear
    AnimatedContent(
        filter,
        transitionSpec = {
            (slideInVertically(GlassMotion.morph()) { it / 2 } + fadeIn(GlassMotion.settle())) togetherWith
                (slideOutVertically(GlassMotion.fold()) { -it / 3 } + fadeOut(GlassMotion.fold()))
        },
        label = "desk title",
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 4.dp),
    ) { f ->
        BasicText(
            stringResource(f.title),
            style = ClearType.Display.copy(color = c.hero).glowing(c),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 44.sp, stepSize = 1.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The chips: glass capsules with a white rim; the chosen one is filled lemon. */
@Composable
private fun FilterChips(selected: DeskFilter, onSelect: (DeskFilter) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp).graphicsLayer { translationX = 0f },
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(DeskFilter.entries, key = { it.name }) { f -> DeskChip(stringResource(f.chip), f == selected) { onSelect(f) } }
    }
}

@Composable
fun DeskChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = clear
    val fill by animateColorAsState(if (chosen) Candy.Chip else Color.White.copy(alpha = if (c.dark) 0.06f else 0.16f), GlassMotion.settle(), label = "chip")
    val lift by animateFloatAsState(if (chosen) 1f else 0f, GlassMotion.pop(), label = "chip lift")
    Box(
        Modifier
            .graphicsLayer { val s = 1f + 0.04f * lift; scaleX = s; scaleY = s }
            .pressable(onClick, pressedScale = 0.9f)
            .height(46.dp).clip(Capsule).background(fill)
            .border(1.2.dp, if (chosen) Color.Transparent else c.rim, Capsule)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ClearType.Body.copy(fontSize = 16.sp), color = if (chosen) Color(0xFF1C2433) else if (c.dark) c.ink else Color(0xFF1C2433))
    }
}

/** Nothing of this kind yet: a mascot, a line, and the way to make one. */
@Composable
private fun DeskEmpty(vm: AppViewModel, filter: DeskFilter, desk: DeskState) {
    val c = clear
    data class E(val bot: String, val mood: MascotMood, @StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int, val go: () -> Unit)
    val e = when (filter) {
        DeskFilter.Notes -> E("flower", MascotMood.NeedsYou, R.string.empty_notes_title, R.string.empty_notes_body, R.string.create_note) { vm.newNote(NoteKind.Text) }
        DeskFilter.Todo -> E("clover", MascotMood.Idle, R.string.empty_lists_title, R.string.empty_lists_body, R.string.create_list) { vm.newNote(NoteKind.List) }
        DeskFilter.Pdfs -> E("ghost", MascotMood.Thinking, R.string.empty_pdfs_title, R.string.empty_pdfs_body, R.string.home_open_file) { vm.pickFiles(com.chethan616.clearpdf.ui.FilePickKind.Open) }
        DeskFilter.Images -> E("cloud", MascotMood.Sleeping, R.string.empty_images_title, R.string.empty_images_body, R.string.create_photo_note) { vm.newNote(NoteKind.Text); vm.pickNotePhoto() }
        DeskFilter.Audio -> E("pill", MascotMood.Happy, R.string.empty_audio_title, R.string.empty_audio_body, R.string.create_voice) { desk.startRecording(vm) }
        DeskFilter.All -> E("star", MascotMood.NeedsYou, R.string.empty_notes_title, R.string.empty_notes_body, R.string.create_note) { vm.newNote(NoteKind.Text) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(e.bot, e.mood, 110.dp)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(e.title), style = ClearType.Headline.copy(color = c.hero).glowing(c), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(e.body), style = ClearType.Body, color = if (c.dark) c.inkSoft else Color(0xFF1C2433).copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        GlassPill(onClick = e.go, tint = Candy.Sky) { Text(stringResource(e.action), style = ClearType.BodyStrong, color = Color.White) }
    }
}

/** A long press on a note: pin it, or delete it. */
@Composable
private fun NoteActions(vm: AppViewModel, n: Note, onDismiss: () -> Unit) {
    GlassSheet(onDismiss, n.title.ifBlank { stringResource(R.string.notes_untitled_note) }, shortDate(n.updated)) {
        GlassRow(stringResource(if (n.pinned) R.string.notes_unpin else R.string.notes_pin), { vm.notes.put(n.copy(pinned = !n.pinned)); onDismiss() }, icon = LucideIcons.Pin, accent = Candy.Tangerine)
        if (n.kind != NoteKind.Voice) GlassRow(stringResource(R.string.notes_open), { vm.openNote = n.id; onDismiss() }, icon = LucideIcons.SquarePen, accent = Candy.Sky)
        GlassRow(stringResource(R.string.common_delete), { vm.notes.delete(n.id); onDismiss() }, icon = LucideIcons.Trash2, destructive = true)
    }
}


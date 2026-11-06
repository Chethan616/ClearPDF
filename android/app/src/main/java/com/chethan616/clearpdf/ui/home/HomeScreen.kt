package com.chethan616.clearpdf.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
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
import com.chethan616.clearpdf.ui.glass.HeaderGlassButton
import androidx.compose.ui.focus.focusRequester
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

    data object Thought : DeskItem {
        override val key get() = "thought"
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

/**
 * The desk's cards for [filter]: notes newest first, with the collections set in among them, and
 * [day]'s "Thought of the day" second (its note, once written on, is that card, not a second one).
 */
fun deskItems(filter: DeskFilter, notes: List<Note>, recents: List<FileRow>, day: Long): List<DeskItem> {
    val sorted = notes.filter { it.thought != day }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    return when (filter) {
        DeskFilter.All -> {
            val out = sorted.map<Note, DeskItem> { DeskItem.Of(it) }.toMutableList()
            if (recents.isNotEmpty()) out.add(minOf(1, out.size), DeskItem.Recent(recents))
            out.add(minOf(3, out.size), DeskItem.Files)
            out.add(minOf(6, out.size), DeskItem.Pdfs(recents.size))
            out.add(minOf(1, out.size), DeskItem.Thought)
            out
        }
        DeskFilter.Notes -> sorted.filter { it.kind == NoteKind.Text }.map<Note, DeskItem> { DeskItem.Of(it) }.toMutableList().apply { add(minOf(1, size), DeskItem.Thought) }
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
    val items = remember(filter, notes, vm.recentList) { deskItems(filter, notes, recents, com.chethan616.clearpdf.notes.today()) }
    val grid = rememberLazyStaggeredGridState()
    LaunchedEffect(filter) { grid.animateScrollToItem(0) }
    // the cards rise in when the desk (or a chip) first shows them, never while scrolling
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(filter) {
        intro = true
        kotlinx.coroutines.delay(700)
        intro = false
    }

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
                val m = Modifier.fillMaxWidth().riseIn(i, active = intro)
                when (item) {
                    is DeskItem.Of -> when (item.note.kind) {
                        NoteKind.List -> ListCard(item.note, { vm.openNote = item.note.id }, { actionsFor = item.note }, { vm.notes.toggle(item.note.id, it.id) }, m)
                        NoteKind.Voice -> VoiceCard(
                            item.note, playing == item.note.id && player.playing,
                            // read while drawing: the position ticking never recomposes the desk
                            { if (playing == item.note.id && player.durationMs > 0) player.positionMs / player.durationMs.toFloat() else 0f },
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
                                if (n.images.size > 1) PhotoChip("+${n.images.size - 1}", { vm.openNote = n.id }, Modifier.align(Alignment.BottomEnd).padding(12.dp))
                            },
                        ) { NoteImage(n.images.first(), Modifier.matchParentSize(), maxPx = 900) }
                    }
                    DeskItem.Thought -> ThoughtCard({ vm.openThought() }, m)
                    DeskItem.Files -> CollectionCard(stringResource(R.string.desk_word_files), clear.tone(Tone.Butter), { vm.filesShowLocal = true; vm.homeTab = HomeTab.Files }, m)
                    is DeskItem.Pdfs -> CollectionCard(stringResource(R.string.desk_word_pdf), clear.tone(Tone.Orchid), { vm.homeTab = HomeTab.Files }, m, height = 136.dp)
                    is DeskItem.Recent -> RecentCard(vm, item.rows, { vm.homeTab = HomeTab.Files }, m)
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

    actionsFor?.let { n -> NoteActions(vm, n, onRename = { desk.naming = n.id }) { actionsFor = null } }
    desk.naming?.let { id -> vm.notes.get(id)?.let { n -> NameSheet(vm, n) { desk.naming = null } } }
}

/** The mark on the left, the four-dot menu on the right. */
@Composable
private fun DeskHeader(vm: AppViewModel) {
    val c = clear
    var menu by remember { mutableStateOf(false) }
    var morphing by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.artefact_mark), stringResource(R.string.app_name),
            colorFilter = ColorFilter.tint(c.hero),
            modifier = Modifier.height(50.dp).width(56.dp),
        )
        Spacer(Modifier.weight(1f))
        // one glass button, as every other: its own lit rim, no second ring drawn around it
        // the button becomes the menu: hidden while its glass is out in the overlay
        HeaderGlassButton(
            onClick = { menu = true; morphing = true },
            label = stringResource(R.string.common_more_options),
            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }.graphicsLayer { alpha = if (morphing) 0f else 1f },
        ) { FourDots(c.hero) }
    }
    if (morphing) {
        com.chethan616.clearpdf.ui.glass.GlassMorphMenu(
            open = menu,
            anchor = anchor,
            onDismiss = { menu = false },
            onGone = { morphing = false },
            items = listOf(
                GlassMenuItem(stringResource(R.string.home_tab_files), LucideIcons.FolderOpen) { vm.homeTab = HomeTab.Files },
                GlassMenuItem(stringResource(R.string.home_tab_tools), LucideIcons.LayoutGrid) { vm.homeTab = HomeTab.Tools },
                GlassMenuItem(stringResource(R.string.home_settings), LucideIcons.Settings) { vm.settingsOpen = true },
            ),
        ) { FourDots(c.hero) }
    }
}

private fun deskFace(f: DeskFilter): TextStyle = when (f) {
    DeskFilter.All -> ClearType.DeskAll
    DeskFilter.Notes -> ClearType.DeskNotes
    DeskFilter.Todo -> ClearType.DeskTodo
    DeskFilter.Pdfs -> ClearType.DeskPdfs
    DeskFilter.Images -> ClearType.DeskImages
    DeskFilter.Audio -> ClearType.DeskAudio
}

/**
 * The big white title; a new chip slides a new one up in its place. Every title is set at the same
 * size and in a box of the same height, so nothing below it moves when the chip changes. The size is
 * the largest (up to [ClearType.DeskTitleSize]) at which the widest of all six titles fits the width.
 */
@Composable
private fun DeskTitle(filter: DeskFilter) {
    val c = clear
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titles = DeskFilter.entries.map { it to stringResource(it.title) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 6.dp, top = 20.dp)) {
        val widthPx = constraints.maxWidth
        val size = remember(titles, widthPx, density.density, density.fontScale) {
            var s = ClearType.DeskTitleSize.value
            while (s > 24f && titles.any { (f, text) ->
                    measurer.measure(text, deskFace(f).copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > widthPx
                }
            ) s -= 1f
            s.sp
        }
        // a little more than the line: AnimatedContent clips, and an italic g's tail runs below it
        val height = with(density) { ClearType.DeskTitleLine.toDp() } + 10.dp
        AnimatedContent(
            filter,
            transitionSpec = {
                (slideInVertically(com.chethan616.clearpdf.ui.glass.Fluid.push()) { it / 3 } + fadeIn(com.chethan616.clearpdf.ui.glass.Fluid.quick())) togetherWith
                    (slideOutVertically(com.chethan616.clearpdf.ui.glass.Fluid.quick()) { -it / 4 } + fadeOut(com.chethan616.clearpdf.ui.glass.Fluid.fade()))
            },
            label = "desk title",
            modifier = Modifier.fillMaxWidth().height(height),
        ) { f ->
            BasicText(
                stringResource(f.title),
                style = deskFace(f).copy(color = c.hero, fontSize = size).glowing(c),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The chips: glass capsules with a white rim; the chosen one is filled lemon. The row runs to the
 * screen's edges and fades out at both, so a chip scrolled past slips away instead of being cut.
 */
@Composable
private fun FilterChips(selected: DeskFilter, onSelect: (DeskFilter) -> Unit) {
    LazyRow(
        Modifier
            // out to the screen's edges, past the grid's 16 dp margins
            .layout { m, c ->
                val extra = 16.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.maxWidth + extra * 2, maxWidth = c.maxWidth + extra * 2))
                layout(c.maxWidth, p.height) { p.place(-extra, 0) }
            }
            .padding(top = 16.dp, bottom = 10.dp)
            .chipRowFade(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(DeskFilter.entries, key = { it.name }) { f -> DeskChip(stringResource(f.chip), f == selected) { onSelect(f) } }
    }
}

/** A long, eased fade at both edges of a chip row (fade.png): chips dissolve into the sky there. */
fun Modifier.chipRowFade(): Modifier = graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = 44.dp.toPx() / size.width
        drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                0f to Color.Transparent, f * 0.35f to Color.Black.copy(alpha = 0.35f), f to Color.Black,
                1f - f to Color.Black, 1f - f * 0.35f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/**
 * A filter chip: real liquid glass (the Back and menu buttons' glass, by Settings ▸ liquid glass and
 * motion); the chosen one is washed lemon, the colour animating while drawing.
 */
@Composable
fun DeskChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = clear
    val lit by animateFloatAsState(if (chosen) 1f else 0f, com.chethan616.clearpdf.ui.glass.Fluid.quick(), label = "chip")
    com.chethan616.clearpdf.ui.glass.GlassChip(
        onClick = onClick,
        height = 46.dp,
        fill = { Candy.Chip.copy(alpha = 0.94f * lit) },
        contentPadding = PaddingValues(horizontal = 22.dp),
        tap = com.chethan616.clearpdf.ui.glass.Feel.Tick,
        ink = if (chosen) Color(0xFF1C2433) else c.ink,
    ) {
        Text(label, style = ClearType.Body.copy(fontSize = 16.sp), color = if (chosen) Color(0xFF1C2433) else c.ink)
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

/** A long press on a note: name it (a voice memo), pin it, open it, or delete it. */
@Composable
private fun NoteActions(vm: AppViewModel, n: Note, onRename: () -> Unit, onDismiss: () -> Unit) {
    val voice = n.kind == NoteKind.Voice
    val title = n.title.ifBlank { stringResource(if (voice) R.string.voice_note else R.string.notes_untitled_note) }
    val subtitle = if (voice && n.durationMs > 0) shortDate(n.updated) + "  ·  " + com.chethan616.clearpdf.ui.viewer.clock(n.durationMs) else shortDate(n.updated)
    GlassSheet(onDismiss, title, subtitle) {
        if (voice) GlassRow(stringResource(R.string.notes_rename), { onDismiss(); onRename() }, icon = LucideIcons.PenLine, accent = Candy.Grape)
        GlassRow(stringResource(if (n.pinned) R.string.notes_unpin else R.string.notes_pin), { vm.notes.put(n.copy(pinned = !n.pinned)); onDismiss() }, icon = LucideIcons.Pin, accent = Candy.Tangerine)
        if (!voice) GlassRow(stringResource(R.string.notes_open), { vm.openNote = n.id; onDismiss() }, icon = LucideIcons.SquarePen, accent = Candy.Sky)
        val feel = com.chethan616.clearpdf.ui.glass.rememberFeel()
        GlassRow(stringResource(R.string.common_delete), { feel(com.chethan616.clearpdf.ui.glass.Feel.Reject); vm.notes.delete(n.id); onDismiss() }, icon = LucideIcons.Trash2, destructive = true)
    }
}

/**
 * Naming a voice memo: one field, already focused with the keyboard up and any name there
 * selected, so typing replaces it; Done or Save keeps it, an empty name clears it.
 */
@Composable
private fun NameSheet(vm: AppViewModel, n: Note, onDismiss: () -> Unit) {
    var name by remember(n.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(n.title, androidx.compose.ui.text.TextRange(0, n.title.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val feel = com.chethan616.clearpdf.ui.glass.rememberFeel()
    fun save() {
        val t = name.text.trim()
        if (t != n.title) vm.notes.put(n.copy(title = t))
        feel(com.chethan616.clearpdf.ui.glass.Feel.Confirm)
        onDismiss()
    }
    LaunchedEffect(Unit) {
        // once the sheet has risen far enough for the keyboard not to fight it
        kotlinx.coroutines.delay(120)
        runCatching { focus.requestFocus() }
    }
    GlassSheet(onDismiss, stringResource(R.string.voice_name_title), if (n.durationMs > 0) shortDate(n.created) + "  ·  " + com.chethan616.clearpdf.ui.viewer.clock(n.durationMs) else null) {
        val c = clear
        // the keyboard goes down with the sheet, not after it
        val leaving = com.chethan616.clearpdf.ui.glass.LocalOverlayPresence.current?.leaving == true
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(leaving) { if (leaving) focusManager.clearFocus(force = true) }
        Column(Modifier.padding(horizontal = 18.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = ClearType.Body.copy(color = c.ink, fontSize = 17.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Candy.Grape),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).clip(com.kyant.shapes.RoundedRectangle(18.dp))
                            .background(if (c.dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.7f))
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LucideIcons.Mic, null, tint = Candy.Grape, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (name.text.isEmpty()) Text(stringResource(R.string.voice_name_hint), style = ClearType.Body.copy(fontSize = 17.sp), color = c.inkFaint)
                            inner()
                        }
                        if (name.text.isNotEmpty()) {
                            Box(
                                Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).pressable({ name = androidx.compose.ui.text.input.TextFieldValue("") }, pressedScale = 0.9f),
                                contentAlignment = Alignment.Center,
                            ) { Icon(LucideIcons.CircleX, stringResource(R.string.common_clear), tint = c.inkFaint, modifier = Modifier.size(18.dp)) }
                        }
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPill(onClick = onDismiss, modifier = Modifier.weight(1f), height = 50.dp) {
                    Text(stringResource(R.string.common_cancel), style = ClearType.BodyStrong, color = c.ink, maxLines = 1)
                }
                GlassPill(onClick = { save() }, tint = Candy.Grape, modifier = Modifier.weight(1f), height = 50.dp) {
                    Text(stringResource(R.string.common_save), style = ClearType.BodyStrong, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}


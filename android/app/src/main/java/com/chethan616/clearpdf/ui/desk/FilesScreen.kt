package com.chethan616.clearpdf.ui.desk

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassRow
import com.chethan616.clearpdf.ui.glass.GlassTab
import com.chethan616.clearpdf.ui.glass.GlassTabs
import com.chethan616.clearpdf.ui.glass.WashPanel
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.FileRow
import com.chethan616.clearpdf.ui.home.HomeTab
import com.chethan616.clearpdf.ui.home.dayBucket
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.glass.Capsule
import kotlinx.coroutines.delay

/** At most this many rows share one panel, so a long folder isn't one giant item. */
private const val PanelRows = 20

/**
 * Files: a big title with the count, a glass search capsule, Recent / Starred / On device, and the
 * files grouped (by day, or by folder) in wash panels. A long press starts selecting.
 */
@Composable
fun FilesScreen(vm: AppViewModel, onBrowse: () -> Unit) {
    val c = clear
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    // narrowed to one kind of file from the ⋯ menu
    var kind by rememberSaveable { mutableStateOf(com.chethan616.clearpdf.engine.DocumentTypes.Kind.All) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.filesShowLocal) {
        if (vm.filesShowLocal) {
            tab = 2
            vm.filesShowLocal = false
        }
    }
    LaunchedEffect(tab) { if (tab == 2) vm.scanLocal() }
    BackHandler(enabled = vm.selectedFiles.isNotEmpty()) { vm.selectedFiles.clear() }
    BackHandler(enabled = vm.selectedFiles.isEmpty() && query.isNotEmpty()) { query = "" }

    val rows: List<FileRow> = when {
        query.isNotBlank() -> vm.searchFiles(query).sortedWith(compareBy<FileRow> { !it.name.lowercase().startsWith(query.trim().lowercase()) }.thenByDescending { it.date })
        tab == 0 -> vm.recentRows()
        tab == 1 -> vm.starredRows()
        else -> vm.localRows()
    }.let { if (sortByName && query.isBlank()) it.sortedBy { r -> r.name.lowercase() } else it }
        .let { if (kind == com.chethan616.clearpdf.engine.DocumentTypes.Kind.All) it else it.filter { r -> com.chethan616.clearpdf.engine.DocumentTypes.kindOf(r.name) == kind } }

    // a search left alone for a moment counts as one made
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(2000)
            if (rows.isNotEmpty()) vm.addRecentSearch(query)
        }
    }

    val resultsTitle = pluralStringResource(R.plurals.home_search_results, rows.size, rows.size)
    val bucketLabels = listOf(
        stringResource(R.string.files_today), stringResource(R.string.common_yesterday),
        stringResource(R.string.files_this_week), stringResource(R.string.files_earlier),
    )
    val internal = stringResource(R.string.home_internal_storage)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = DockClearance)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item(key = "header") {
            Spacer(Modifier.height(16.dp))
            if (vm.selectedFiles.isNotEmpty()) {
                SelectionHeader(vm, recent = tab == 0) { confirmDelete = true }
            } else {
                ScreenHeader(pluralStringResource(R.plurals.files_count, rows.size, rows.size), stringResource(R.string.home_tab_files), onBack = { vm.homeTab = HomeTab.Home }) {
                    com.chethan616.clearpdf.ui.glass.HeaderGlassButton(
                        onClick = { menuOpen = true }, label = stringResource(R.string.common_more_options),
                        modifier = Modifier.onGloballyPositioned { menuAnchor = it.boundsInWindow() },
                    ) { GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(20.dp)) }
                }
            }
        }
        item(key = "search") { SearchCapsule(query, { query = it }, onSearch = { vm.addRecentSearch(query) }) }
        if (query.isBlank()) {
            item(key = "tabs") {
                GlassTabs(
                    tabs = listOf(
                        GlassTab(stringResource(R.string.home_list_recent), accent = Candy.Tomato),
                        GlassTab(stringResource(R.string.home_list_starred), accent = Candy.Tangerine),
                        GlassTab(stringResource(R.string.home_list_on_device), accent = Candy.Sky),
                    ),
                    selected = tab, onSelect = { tab = it; vm.selectedFiles.clear() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                    height = 48.dp,
                )
            }
        }
        when {
            query.isNotBlank() -> {
                if (rows.isEmpty()) item(key = "noresults") { EmptyHero(stringResource(R.string.home_no_results), stringResource(R.string.home_no_results_body), accent = Candy.Sky) }
                else panels(vm, "results", resultsTitle, rows, recent = false)
            }
            tab == 0 -> {
                if (rows.isEmpty()) {
                    item(key = "empty0") { EmptyHero(stringResource(R.string.home_no_recent_title), stringResource(R.string.home_no_recent_body), stringResource(R.string.home_open_file), Candy.Tomato, onBrowse) }
                } else {
                    val groups = if (sortByName) mapOf(-1 to rows) else rows.groupBy { dayBucket(it.date) }.toSortedMap()
                    for ((bucket, list) in groups) {
                        panels(vm, "d$bucket", if (bucket < 0) null else bucketLabels[bucket], list, recent = true)
                    }
                }
                item(key = "places") { Places(vm, onBrowse) { tab = 2 } }
            }
            tab == 1 -> {
                if (rows.isEmpty()) item(key = "empty1") { EmptyHero(stringResource(R.string.files_starred_empty_title), stringResource(R.string.files_starred_empty_body), accent = Candy.Tangerine) }
                else panels(vm, "starred", null, rows, recent = true)
            }
            else -> {
                when {
                    !vm.hasFileAccess && rows.isEmpty() -> item(key = "noaccess") {
                        EmptyHero(stringResource(R.string.home_device_files_title), stringResource(R.string.home_device_files_body_long), stringResource(R.string.home_add_folder), Candy.Sky) { vm.requestFileAccess() }
                    }
                    rows.isEmpty() -> item(key = "nopdfs") {
                        if (vm.scanning) EmptyHero(stringResource(R.string.home_looking_for_pdfs), "", accent = Candy.Sky)
                        else EmptyHero(stringResource(R.string.home_no_pdfs_title), stringResource(R.string.home_no_pdfs_body), stringResource(R.string.home_add_folder), Candy.Sky) { vm.requestFileAccess() }
                    }
                    else -> {
                        for ((folder, list) in rows.groupBy { it.location }) panels(vm, "f$folder", folder.ifEmpty { internal }, list, recent = false)
                    }
                }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }

    if (menuOpen) {
        val anchor = menuAnchor ?: Rect.Zero
        val items = buildList {
            add(GlassMenuItem(stringResource(if (sortByName) R.string.home_sort_by_date else R.string.home_sort_by_name), LucideIcons.ArrowUpDown) { sortByName = !sortByName })
            // show one kind of file: the one shown has a tick
            for (k in com.chethan616.clearpdf.engine.DocumentTypes.Kind.entries) {
                add(GlassMenuItem(stringResource(k.label), if (k == kind) LucideIcons.Check else k.icon) { kind = k })
            }
            if (tab == 0 && vm.recentList.isNotEmpty()) add(GlassMenuItem(stringResource(R.string.home_clear_recent), LucideIcons.BrushCleaning) { confirmClear = true })
            if (tab == 2) {
                add(GlassMenuItem(stringResource(R.string.home_refresh), LucideIcons.RotateCw) { vm.scanLocal(force = true) })
                add(GlassMenuItem(stringResource(R.string.home_add_folder), LucideIcons.FolderPlus) { vm.requestFileAccess() })
                for ((tree, name) in vm.localFolders()) add(GlassMenuItem(stringResource(R.string.home_stop_listing, name), LucideIcons.FolderOpen) { vm.removeLocalFolder(tree) })
            }
        }
        GlassMenu(anchor, { menuOpen = false }, items)
    }
    if (confirmClear) {
        GlassDialog(
            onDismiss = { confirmClear = false }, title = stringResource(R.string.home_clear_recent_title), icon = LucideIcons.BrushCleaning, destructive = true,
            confirm = stringResource(R.string.common_clear), onConfirm = { confirmClear = false; vm.clearRecents() }, dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.home_clear_recent_body), style = ClearType.Body, color = c.inkSoft) }
    }
    if (confirmDelete) {
        val sel = vm.selectedFiles.values.toList()
        GlassDialog(
            onDismiss = { confirmDelete = false }, title = pluralStringResource(R.plurals.home_delete_files_title, sel.size, sel.size), icon = LucideIcons.Trash2, destructive = true,
            confirm = stringResource(R.string.common_delete), onConfirm = { confirmDelete = false; sel.forEach(vm::deleteFile); vm.selectedFiles.clear() },
            dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.home_delete_files_body), style = ClearType.Body, color = c.inkSoft) }
    }
}

/** [rows] under an optional [title], in wash panels of at most [PanelRows]. */
private fun LazyListScope.panels(vm: AppViewModel, key: String, title: String?, rows: List<FileRow>, recent: Boolean) {
    if (title != null) {
        item(key = "t-$key") {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(title, style = ClearType.Title, color = clear.ink, modifier = Modifier.weight(1f))
                Text(rows.size.toString(), style = ClearType.Number, color = clear.inkFaint)
            }
        }
    } else {
        item(key = "s-$key") { Spacer(Modifier.height(12.dp)) }
    }
    rows.chunked(PanelRows).forEachIndexed { i, chunk ->
        item(key = "p-$key-$i") {
            WashPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                Spacer(Modifier.height(4.dp))
                chunk.forEach { r -> FileListItem(vm, r, recent) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** The search capsule: glass, a candy cursor, a clear button. */
@Composable
private fun SearchCapsule(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    val c = clear
    val hint = stringResource(R.string.home_search_your_files)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(52.dp).glass(Capsule, blur = 4.dp).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LucideIcons.Search, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = ClearType.Body.copy(color = c.ink), cursorBrush = SolidColor(Candy.Tomato),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text(hint, style = ClearType.Body, color = c.inkFaint); inner() } },
        )
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).pressable({ onQuery("") }), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.CircleX, stringResource(R.string.common_clear), tint = c.inkSoft, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** While files are ticked: how many, and what to do with them. */
@Composable
private fun SelectionHeader(vm: AppViewModel, recent: Boolean, onDelete: () -> Unit) {
    val c = clear
    val rows = vm.selectedFiles.values.toList()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassCircle(onClick = { vm.selectedFiles.clear() }, label = stringResource(R.string.home_clear_selection)) { GlassIcon(LucideIcons.X, null, Modifier.size(20.dp)) }
        Text(pluralStringResource(R.plurals.home_files_selected, rows.size, rows.size), style = ClearType.Headline, color = c.ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
        GlassCircle(onClick = { vm.shareFiles(rows); vm.selectedFiles.clear() }, tint = Candy.Sky, label = stringResource(R.string.common_share)) { GlassIcon(LucideIcons.Share2, null, Modifier.size(20.dp)) }
        if (rows.size >= 2) GlassCircle(onClick = { vm.combineSelected(rows); vm.selectedFiles.clear() }, tint = Candy.Grape, label = stringResource(R.string.common_tool_combine)) { GlassIcon(LucideIcons.Combine, null, Modifier.size(20.dp)) }
        if (recent) GlassCircle(onClick = { vm.removeRecents(rows); vm.selectedFiles.clear() }, label = stringResource(R.string.home_remove_from_recent)) { GlassIcon(LucideIcons.CircleMinus, null, Modifier.size(20.dp)) }
        GlassCircle(onClick = onDelete, tint = Candy.Tomato, label = stringResource(R.string.common_delete)) { GlassIcon(LucideIcons.Trash2, null, Modifier.size(20.dp)) }
    }
}

/** Where else files come from. */
@Composable
private fun Places(vm: AppViewModel, onBrowse: () -> Unit, onDevice: () -> Unit) {
    Column {
        Text(stringResource(R.string.files_places), style = ClearType.Title, color = clear.ink, modifier = Modifier.padding(start = 24.dp, top = 26.dp, bottom = 8.dp))
        WashPanel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Spacer(Modifier.height(4.dp))
            PlaceRow(LucideIcons.Smartphone, stringResource(R.string.home_on_this_device), Candy.Sky, onDevice)
            PlaceRow(LucideIcons.Images, stringResource(R.string.home_photos), Candy.Lemon) { vm.pickPhotosToPdf() }
            PlaceRow(LucideIcons.FolderOpen, stringResource(R.string.home_look_in_other_folders), Candy.Mint, onBrowse)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    GlassRow(label, onClick, icon = icon, accent = accent, trailing = { Icon(LucideIcons.ChevronRight, null, tint = clear.inkFaint, modifier = Modifier.size(18.dp)) })
}

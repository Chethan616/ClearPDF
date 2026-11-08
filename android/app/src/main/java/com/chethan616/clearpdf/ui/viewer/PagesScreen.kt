package com.chethan616.clearpdf.ui.viewer

import com.chethan616.clearpdf.ui.safeTopPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.BarIcon
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.PdfSheet
import com.chethan616.clearpdf.ui.SheetRow
import com.chethan616.clearpdf.ui.SheetTitle
import com.chethan616.clearpdf.ui.pdfColors
import kotlinx.coroutines.withContext

/**
 * Pages: the document's pages as a grid (two across upright, four sideways). Viewing, a tap goes
 * to the page. Organizing, pages are picked with their check boxes and turned, deleted,
 * extracted or added to, and moved by holding and dragging.
 */
@Composable
fun PagesScreen(vm: AppViewModel, doc: OpenDocument, onClose: () -> Unit) {
    val c = pdfColors
    val organizing = vm.organizing
    val selected = remember { mutableStateMapOf<Int, Boolean>() }
    val thumbs = remember(doc.info.generation) { mutableStateMapOf<Int, ImageBitmap>() }
    var insertMenu by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val columns = if (landscape) 4 else 2
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = doc.viewer.currentPage)
    // dragging a page: its index and where it would drop
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    val exporting = vm.imageExport
    fun close() {
        vm.organizing = false
        vm.imageExport = null
        selected.clear()
        onClose()
    }
    // opened straight into organizing (a tool), Back returns to the document; organizing begun
    // from the grid returns to the grid
    val openedOrganizing = remember { vm.organizing }
    fun back() {
        if (exporting != null || openedOrganizing) {
            close()
        } else if (organizing) {
            vm.organizing = false
            selected.clear()
        } else {
            close()
        }
    }
    BackHandler(onBack = ::back)
    // organizing starts with the page being read ticked, as the reference's does
    LaunchedEffect(organizing) { if (organizing && selected.isEmpty()) selected[doc.viewer.currentPage] = true }
    val picked = selected.filterValues { it }.keys.sorted()
    val canDrag = doc.info.allowsAssembly && doc.pageCount > 1
    // Artefact's own pages: the app's wallpaper, a glass Back, frosted page cards and a glass bar
    com.chethan616.clearpdf.ui.glass.AuroraScreen {
    Column(Modifier.fillMaxSize().safeTopPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (organizing) {
                com.chethan616.clearpdf.ui.glass.GlassCircle(com.chethan616.clearpdf.ui.glass.rememberBeat(::back), tint = com.chethan616.clearpdf.ui.theme.Candy.Sky, label = stringResource(R.string.common_done)) {
                    com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Check, null, Modifier.size(18.dp))
                }
            } else {
                com.chethan616.clearpdf.ui.glass.GlassBackButton(::back)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (organizing && picked.isNotEmpty()) pluralStringResource(R.plurals.pages_selected_count, picked.size, picked.size) else if (organizing) stringResource(R.string.pages_select) else stringResource(R.string.pages_title),
                style = com.chethan616.clearpdf.ui.theme.ClearType.Title, color = com.chethan616.clearpdf.ui.theme.clear.ink, maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (organizing && exporting == null) {
                UndoMenuButton(doc.info.undo != null, doc.info.redo != null, { vm.undo(doc) }, { vm.redo(doc) }, doc.info.undo, doc.info.redo)
                Spacer(Modifier.width(6.dp))
            }
        }
        // the reference's grid: two 136 x 162 dp cards across (four sideways), 24 dp apart, each
        // holding its page fitted into 100 x 116 dp, with the page number under it
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val side = if (landscape) 54.dp else 24.dp
            val gap = if (landscape) 22.dp else 24.dp
            val cardW = minOf(136.dp, (maxWidth - side * 2 - gap * (columns - 1)) / columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(start = side, end = side, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(doc.info.pages, key = { i, _ -> "${doc.info.generation}-$i" }) { i, p ->
                    LaunchedEffect(i, doc.info.generation) {
                        if (thumbs[i] == null) {
                            val eng = vm.engine ?: return@LaunchedEffect
                            val scale = 300f / maxOf(p.width, p.height).coerceAtLeast(1f)
                            val img = runCatching { withContext(eng.thumbnails) { eng.session.renderPage(doc.id, i.toUInt(), scale, null).toBitmap().asImageBitmap() } }.getOrNull()
                            if (img != null) thumbs[i] = img
                        }
                    }
                    val isSel = selected[i] == true
                    val isCurrent = !organizing && i == doc.viewer.currentPage
                    val highlighted = isSel || isCurrent || dragTo == i && dragFrom != i
                    val pageLabel = stringResource(R.string.pages_page, i + 1)
                    Column(
                        Modifier
                            .graphicsLayer { alpha = if (dragFrom == i) 0.4f else 1f }
                            .then(
                                // holding a page and dragging it moves it, in either mode
                                if (canDrag) {
                                    Modifier.pointerInput(i, doc.info.generation) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragFrom = i
                                                dragTo = i
                                            },
                                            onDragEnd = {
                                                val from = dragFrom
                                                val to = dragTo
                                                dragFrom = null
                                                dragTo = null
                                                if (from != null && to != null && to != from) {
                                                    // engine positions count before the move: dropping after a later page lands one further on
                                                    val target = if (to > from) to + 2 else to + 1
                                                    vm.runTool(doc, "page_move", mapOf("pages" to listOf(from + 1), "to" to target))
                                                }
                                            },
                                            onDragCancel = {
                                                dragFrom = null
                                                dragTo = null
                                            },
                                        ) { change, _ ->
                                            change.consume()
                                            val pos = change.position
                                            val info = grid.layoutInfo
                                            val me = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return@detectDragGesturesAfterLongPress
                                            val x = me.offset.x + pos.x
                                            val y = me.offset.y + pos.y
                                            info.visibleItemsInfo.firstOrNull { it.offset.x <= x && x <= it.offset.x + it.size.width && it.offset.y <= y && y <= it.offset.y + it.size.height }
                                                ?.let { dragTo = it.index }
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                if (organizing) {
                                    selected[i] = !isSel
                                } else {
                                    doc.viewer.goToPage(i)
                                    close()
                                }
                            }
                            .semantics { contentDescription = pageLabel },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(cardW, 162.dp).then(com.chethan616.clearpdf.ui.glass.washPanel())
                                .then(if (highlighted) Modifier.border(2.dp, com.chethan616.clearpdf.ui.theme.Candy.Sky, com.chethan616.clearpdf.ui.glass.RoundedRectangle(20.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val ratio = (p.width / p.height.coerceAtLeast(1f)).coerceIn(0.2f, 5f)
                            // fitted into 100 x 116 dp, keeping the page's shape
                            val w = if (ratio > 100f / 116f) 100.dp else 116.dp * ratio
                            Box(Modifier.size(w, w / ratio).background(Color.White)) {
                                thumbs[i]?.let { Image(it, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            }
                            if (organizing) com.chethan616.clearpdf.ui.glass.GlassTick(isSel, Modifier.align(Alignment.TopStart).padding(8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${i + 1}", style = com.chethan616.clearpdf.ui.theme.ClearType.Label, color = com.chethan616.clearpdf.ui.theme.clear.inkSoft)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        val enabled = picked.isNotEmpty()
        // the actions on one floating glass capsule, as the viewer's toolbar
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(60.dp)
                .glassBar().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (exporting != null) {
                // picking the pages to export: save them to the phone's pictures, or share them
                OrganizeItem(LucideIcons.Save, stringResource(R.string.common_save), enabled) { vm.writeImages(doc, exporting, picked, share = false) }
                OrganizeItem(LucideIcons.Share2, stringResource(R.string.common_share), enabled) { vm.writeImages(doc, exporting, picked, share = true) }
            } else if (organizing) {
                OrganizeItem(LucideIcons.FilePlus, stringResource(R.string.pages_insert_page), true) { insertMenu = true }
                OrganizeItem(LucideIcons.FileOutput, stringResource(R.string.pages_extract), enabled) { vm.extractPages(doc, picked) }
                OrganizeItem(LucideIcons.Trash2, stringResource(R.string.common_delete), enabled && picked.size < doc.pageCount) {
                    vm.runTool(doc, "page_delete", mapOf("pages" to picked.map { it + 1 }))
                    selected.clear()
                }
                OrganizeItem(LucideIcons.RotateCcw, stringResource(R.string.pages_rotate_left), enabled) { vm.runTool(doc, "page_rotate", mapOf("pages" to picked.map { it + 1 }, "degrees" to -90)) }
                OrganizeItem(LucideIcons.RotateCw, stringResource(R.string.pages_rotate_right), enabled) { vm.runTool(doc, "page_rotate", mapOf("pages" to picked.map { it + 1 }, "degrees" to 90)) }
            } else {
                OrganizeItem(LucideIcons.FilePenLine, stringResource(R.string.common_tool_edit_pdf), true) {
                    close()
                    vm.enterTool(doc, ViewerTool.Edit)
                }
                OrganizeItem(LucideIcons.Crop, stringResource(R.string.common_tool_crop), true) {
                    close()
                    vm.runQuickTool(doc, QuickTool.Crop)
                }
            }
        }
    }
    }
    if (insertMenu) {
        // where the new pages go, as the reference asks: around the selection, or at either end
        var after by remember { mutableStateOf(true) }
        val first = picked.firstOrNull()
        val last = picked.lastOrNull()
        val at = when {
            first == null || last == null -> if (after) doc.pageCount + 1 else 1
            after -> last + 2
            else -> first + 1
        }
        PdfSheet(onDismiss = { insertMenu = false }, dim = true) {
            SheetTitle(stringResource(R.string.pages_insert))
            val c = pdfColors
            for (option in listOf(false, true)) {
                val label = when {
                    first == null || last == null -> stringResource(if (option) R.string.pages_at_end else R.string.pages_at_start)
                    option -> stringResource(R.string.pages_after_page, last + 1)
                    else -> stringResource(R.string.pages_before_page, first + 1)
                }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { after = option }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.chethan616.clearpdf.ui.glass.GlassRadio(after == option, { after = option })
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = c.text, fontSize = 16.sp)
                }
            }
            com.chethan616.clearpdf.ui.SheetDivider()
            SheetRow(LucideIcons.File, stringResource(R.string.pages_blank_page), { insertMenu = false; vm.runTool(doc, "page_insert_blank", mapOf("at" to at)) })
            SheetRow(LucideIcons.FileStack, stringResource(R.string.pages_from_file), { insertMenu = false; vm.pickInsertFile(doc, at) })
            SheetRow(LucideIcons.Camera, stringResource(R.string.pages_from_scan), { insertMenu = false; vm.insertScanAt = doc to at; vm.startScan() })
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** An action of the pages' glass bar: its glyph in the glass's ink (it names itself to TalkBack), faint while it can't be used. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.OrganizeItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight().pressable(onClick, enabled = enabled, pressedScale = 0.86f).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        com.chethan616.clearpdf.ui.glass.GlassIcon(icon, null, Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else 0.35f })
    }
}

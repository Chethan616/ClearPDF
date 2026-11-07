package com.chethan616.clearpdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.core.AttachmentInfo
import com.chethan616.clearpdf.core.CommentInfo
import com.chethan616.clearpdf.core.OutlineEntry
import com.chethan616.clearpdf.plural
import com.chethan616.clearpdf.text
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.BarIcon
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.PdfSheet
import com.chethan616.clearpdf.ui.PdfSwitch
import com.chethan616.clearpdf.ui.SheetBackHeader
import com.chethan616.clearpdf.ui.GroupDivider
import com.chethan616.clearpdf.ui.SheetDivider
import com.chethan616.clearpdf.ui.SheetRow
import com.chethan616.clearpdf.ui.SheetTitle
import com.chethan616.clearpdf.ui.pdfColors
import com.chethan616.clearpdf.ui.SheetCircleButton
import com.chethan616.clearpdf.ui.SheetSection
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.glass.Capsule
import com.chethan616.clearpdf.ui.glass.RoundedRectangle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import java.text.DateFormat
import java.util.Date

/**
 * More: a header (name, "PDF · modified", Print, Star) over the viewer's commands in the
 * reference's order. Drill-in rows (with a chevron) open their own sheet.
 */
@Composable
fun MoreSheet(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    val ctl = doc.controller
    fun close() {
        ctl.sheet = ViewerSheet.None
    }
    val bookmarked = vm.bookmarks.isBookmarked(doc, doc.viewer.currentPage)
    val starred = vm.isStarred(doc)
    // like the reference, the sheet opens part way and rises to full height once its list scrolls
    val scroll = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(scroll.value > 0) { if (scroll.value > 0) expanded = true }
    val fraction by androidx.compose.animation.core.animateFloatAsState(if (expanded) 1f else 0.69f, label = "sheet")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val top = with(density) {
        maxOf(
            androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density),
            androidx.compose.foundation.layout.WindowInsets.displayCutout.getTop(density),
        ).toDp()
    } + TopBarHeight
    PdfSheet(onDismiss = ::close, maxHeightFraction = fraction, topReserve = top) {
        // the document: a small paper, its name and what it is, then Star and Print
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DocGlyph()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = ClearType.Title.copy(fontSize = 17.sp), color = clear.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    (if (doc.isSlides) stringResource(R.string.sheet_kind_slides) else if (doc.isOffice) stringResource(R.string.sheet_kind_word) else "PDF") + " · ${modifiedLabel(doc.file.lastModified(), stringResource(R.string.common_yesterday))}" + if (!doc.writable) " · " + stringResource(R.string.sheet_read_only) else "",
                    style = ClearType.Caption.copy(fontSize = 12.sp), color = clear.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            SheetCircleButton(
                LucideIcons.Star, stringResource(if (starred) R.string.sheet_remove_star else R.string.sheet_add_star), { vm.toggleStar(doc) },
                tint = if (starred) Candy.Lemon else Color.Unspecified,
            )
            Spacer(Modifier.width(8.dp))
            SheetCircleButton(LucideIcons.X, stringResource(R.string.ui_close_sheet), ::close)
        }
        Column(Modifier.verticalScroll(scroll)) {
            // the four things done most, as big tiles
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    if (bookmarked) LucideIcons.BookmarkMinus else LucideIcons.BookmarkPlus,
                    stringResource(if (bookmarked) R.string.sheet_remove_bookmark else R.string.sheet_add_bookmark), Candy.Tangerine,
                ) { close(); vm.toggleBookmark(doc) }
                ActionTile(LucideIcons.Volume2, stringResource(R.string.common_tool_read_aloud), Candy.Sky) { close(); vm.startReadAloud(doc) }
                if (doc.isSlides) ActionTile(LucideIcons.Presentation, stringResource(R.string.office_present), Candy.Bubblegum) { close(); vm.presenting = doc }
                else if (!doc.isOffice) ActionTile(LucideIcons.FilePenLine, stringResource(R.string.common_tool_edit_pdf), Candy.Bubblegum) { close(); vm.enterTool(doc, ViewerTool.Edit) }
                ActionTile(LucideIcons.Printer, stringResource(R.string.sheet_print), Candy.Grape) { close(); vm.print(doc) }
            }
            Spacer(Modifier.height(8.dp))
            SheetSection {
                SheetRow(LucideIcons.Glasses, stringResource(R.string.sheet_view_settings), { ctl.sheet = ViewerSheet.ViewSettings }, chevron = true, tint = Candy.Sky)
                SheetRow(LucideIcons.LayoutGrid, stringResource(R.string.sheet_pages), { close(); ctl.pagesOpen = true }, chevron = true, tint = Candy.Mint)
                SheetRow(LucideIcons.Bookmark, stringResource(R.string.sheet_bookmarks_contents), { ctl.sheet = ViewerSheet.Bookmarks }, chevron = true, tint = Candy.Tangerine)
                if (doc.info.attachments > 0u) SheetRow(LucideIcons.Paperclip, stringResource(R.string.sheet_attachments), { ctl.sheet = ViewerSheet.Attachments }, chevron = true, tint = Candy.Grape)
                if (!doc.isOffice) {
                    SheetRow(LucideIcons.MessagesSquare, stringResource(R.string.sheet_comment_list), { ctl.sheet = ViewerSheet.Comments }, chevron = true, tint = Candy.Bubblegum)
                    SheetRow(LucideIcons.ShieldCheck, stringResource(R.string.sign_signatures), { ctl.sheet = ViewerSheet.Signatures }, chevron = true, tint = Candy.Lime)
                }
            }
            // an office document: written by its own engine as PDF or another format
            if (doc.isOffice) SheetSection {
                SheetRow(LucideIcons.FileOutput, stringResource(R.string.office_export_pdf), { close(); vm.exportOffice(doc, "pdf") }, tint = Candy.Tangerine)
                SheetRow(LucideIcons.Copy, stringResource(R.string.office_save_as), { ctl.sheet = ViewerSheet.SaveAs }, chevron = true, tint = Candy.Lime)
            }
            if (!doc.isOffice) SheetSection {
                SheetRow(LucideIcons.FileOutput, stringResource(R.string.sheet_export_as), { close(); vm.openExport(doc) }, tint = Candy.Tangerine)
                SheetRow(LucideIcons.FileArchive, stringResource(R.string.common_tool_compress), { close(); vm.openCompress(doc) }, tint = Candy.Mint)
                SheetRow(LucideIcons.Combine, stringResource(R.string.common_tool_combine), { close(); vm.openCombine(doc) }, tint = Candy.Grape)
                SheetRow(LucideIcons.FileLock2, stringResource(R.string.common_tool_set_password), { close(); vm.openProtect(doc) }, tint = Candy.Sky)
                SheetRow(LucideIcons.FilePlus2, stringResource(R.string.common_tool_create_pdf), { close(); vm.openCreate() }, tint = Candy.Bubblegum)
                SheetRow(LucideIcons.Copy, stringResource(R.string.common_save_copy), { close(); vm.saveCopyAs(doc) }, tint = Candy.Lime)
            }
            if (doc.info.undo != null || doc.info.redo != null) {
                SheetSection {
                    doc.info.undo?.let { SheetRow(LucideIcons.Undo2, stringResource(R.string.sheet_undo_action, it), { close(); vm.undo(doc) }, tint = Candy.Grape) }
                    doc.info.redo?.let { SheetRow(LucideIcons.Redo2, stringResource(R.string.sheet_redo_action, it), { close(); vm.redo(doc) }, tint = Candy.Grape) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** A PDF as a little paper in a sheet's header: grey lines and the red PDF corner. */
@Composable
private fun DocGlyph() {
    Box(
        Modifier.size(38.dp, 46.dp).graphicsLayer { shadowElevation = 3.dp.toPx(); shape = RoundedRectangle(7.dp); clip = true }.background(Color.White)
            .padding(horizontal = 7.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { k -> Box(Modifier.fillMaxWidth(if (k == 3) 0.6f else 1f).height(2.5.dp).clip(Capsule).background(Color(0xFF17141F).copy(alpha = 0.14f))) }
        }
        Box(Modifier.align(Alignment.BottomEnd).clip(RoundedRectangle(3.dp)).background(Candy.Tomato).padding(horizontal = 3.dp, vertical = 1.dp)) {
            Text("PDF", color = Color.White, fontSize = 7.sp, style = ClearType.Eyebrow.copy(letterSpacing = 0.3.sp, fontSize = 7.sp, lineHeight = 8.sp))
        }
    }
}

/** One of More's big tiles: a candy glyph over its name, on a white cell. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionTile(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    val c = clear
    Column(
        Modifier.weight(1f).height(78.dp).clip(RoundedRectangle(20.dp))
            .background(if (c.dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.66f))
            .pressable(onClick, pressedScale = 0.95f).padding(horizontal = 4.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = ClearType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp), color = c.ink, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** When the file was modified: the time today, [yesterday] ("Yesterday"), else the date. */
internal fun modifiedLabel(t: Long, yesterday: String, now: Long = System.currentTimeMillis()): String {
    fun day(ms: Long) = java.util.Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(java.util.Calendar.YEAR) * 400 + it.get(java.util.Calendar.DAY_OF_YEAR) }
    val d = Date(t)
    val today = day(now)
    val dayBefore = day(now - 24L * 3600 * 1000)
    return when (day(t)) {
        today -> DateFormat.getTimeInstance(DateFormat.SHORT).format(d)
        dayBefore -> yesterday
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(d)
    }
}

/** View settings: Continuous, Single page, Reading Mode, then the Night Mode switch. */
@Composable
fun ViewSettingsSheet(vm: AppViewModel, doc: OpenDocument) {
    val ctl = doc.controller
    PdfSheet(onDismiss = { ctl.sheet = ViewerSheet.None }) {
        SheetBackHeader(stringResource(R.string.sheet_view_settings), onBack = { ctl.sheet = ViewerSheet.More })
        // how the pages run, as three cards to choose from (the chosen one lit in blue)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutChoice(LucideIcons.GalleryVerticalEnd, stringResource(R.string.sheet_continuous), doc.viewer.mode == PageMode.Continuous && !vm.readingMode) {
                vm.setPageMode(doc, PageMode.Continuous)
                ctl.sheet = ViewerSheet.None
            }
            LayoutChoice(LucideIcons.File, stringResource(R.string.sheet_single_page), doc.viewer.mode == PageMode.SinglePage && !vm.readingMode) {
                vm.setPageMode(doc, PageMode.SinglePage)
                ctl.sheet = ViewerSheet.None
            }
            LayoutChoice(LucideIcons.BookOpenText, stringResource(R.string.sheet_reading_mode), vm.readingMode) {
                ctl.sheet = ViewerSheet.None
                vm.openReadingMode(doc)
            }
        }
        Spacer(Modifier.height(6.dp))
        SheetSection {
            SheetRow(LucideIcons.Moon, stringResource(R.string.sheet_night_mode), { vm.setNightMode(doc, !ctl.nightMode); ctl.sheet = ViewerSheet.None }, tint = Candy.Grape) {
                PdfSwitch(ctl.nightMode, { vm.setNightMode(doc, it); ctl.sheet = ViewerSheet.None })
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** A way for the pages to run: a card with its glyph and name; the chosen one is lit in blue. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.LayoutChoice(icon: ImageVector, label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = clear
    val fill by androidx.compose.animation.animateColorAsState(
        if (chosen) Candy.Sky.copy(alpha = if (c.dark) 0.30f else 0.16f) else if (c.dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.66f),
        com.chethan616.clearpdf.ui.glass.Fluid.quick(), label = "layout choice",
    )
    Column(
        Modifier.weight(1f).height(96.dp).clip(RoundedRectangle(22.dp)).background(fill)
            .border(1.5.dp, if (chosen) Candy.Sky else Color.Transparent, RoundedRectangle(22.dp))
            .pressable(onClick, pressedScale = 0.95f).padding(8.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (chosen) Candy.Sky else c.inkSoft, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = ClearType.Label.copy(fontSize = 12.5.sp), color = if (chosen) Candy.Sky else c.ink, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** Bookmarks & Table of Contents: the outline, one level at a time, and the reader's own bookmarks. */
@Composable
fun BookmarksSheet(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    val ctl = doc.controller
    var outline by remember { mutableStateOf<List<OutlineEntry>?>(null) }
    LaunchedEffect(doc.info.generation) { outline = vm.outline(doc) }
    val hasOutline = doc.info.hasOutline
    var tab by remember { mutableStateOf(if (hasOutline) 0 else 1) }
    // the outline level shown: the index of its parent entry (-1: the top level)
    var parent by remember { mutableStateOf(-1) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    PdfSheet(onDismiss = { ctl.sheet = ViewerSheet.None }, maxHeightFraction = 0.69f, fill = true) {
        SheetBackHeader(stringResource(R.string.sheet_bookmarks_contents), onBack = { ctl.sheet = ViewerSheet.More })
        // Contents | Bookmarks: the glass segmented control
        com.chethan616.clearpdf.ui.glass.GlassTabs(
            listOf(
                com.chethan616.clearpdf.ui.glass.GlassTab(stringResource(R.string.sheet_tab_contents), accent = Candy.Sky),
                com.chethan616.clearpdf.ui.glass.GlassTab(stringResource(R.string.sheet_tab_bookmarks), accent = Candy.Tangerine),
            ),
            selected = tab, onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            height = 46.dp,
        )
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            val all = outline
            when {
                all == null -> Text("…", color = c.textSecondary, modifier = Modifier.padding(20.dp))
                all.isEmpty() -> Text(stringResource(R.string.sheet_no_contents), color = c.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
                else -> {
                    val level = if (parent < 0) 0u else all[parent].level + 1u
                    val start = parent + 1
                    // the entries of this level under `parent`: until the level rises back above it
                    val entries = buildList {
                        var i = start
                        while (i < all.size) {
                            val e = all[i]
                            if (e.level < level) break
                            if (e.level == level) add(i)
                            i++
                        }
                    }
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp).clip(RoundedRectangle(22.dp)).background(cellFill())) {
                        if (parent >= 0) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().pressable({ parent = parentOf(all, parent) }, pressedScale = 0.985f).padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SheetCircleButton(LucideIcons.ChevronLeft, stringResource(R.string.sheet_up_level), { parent = parentOf(all, parent) }, size = 32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(all[parent].title, style = ClearType.BodyStrong.copy(fontSize = 16.sp), color = clear.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        items(entries) { i ->
                            val e = all[i]
                            Row(
                                Modifier.fillMaxWidth().pressable({
                                    e.page?.let { p ->
                                        ctl.rememberJump(doc.viewer.currentPage)
                                        doc.viewer.goToPage(p.toInt())
                                        ctl.sheet = ViewerSheet.None
                                    }
                                }, pressedScale = 0.985f).padding(start = 18.dp, end = 10.dp).heightIn(min = 50.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(e.title, style = ClearType.Body.copy(fontSize = 16.sp), color = clear.ink, modifier = Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                e.page?.let { Text((it.toInt() + 1).toString(), style = ClearType.Label, color = clear.inkFaint, modifier = Modifier.padding(horizontal = 6.dp)) }
                                if (e.children > 0u) SheetCircleButton(LucideIcons.ChevronRight, stringResource(R.string.sheet_expand_entry, e.title), { parent = i }, size = 32.dp)
                            }
                        }
                    }
                }
            }
        } else {
            val list = vm.bookmarks.list(doc)
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedRectangle(18.dp)).background(Candy.Tangerine), contentAlignment = Alignment.Center) {
                        Icon(LucideIcons.Bookmark, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.sheet_bookmarks_empty), style = ClearType.Body, color = clear.inkSoft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    com.chethan616.clearpdf.ui.glass.GlassPill(onClick = { vm.toggleBookmark(doc) }, tint = Candy.Tangerine, height = 44.dp) {
                        Text(stringResource(R.string.sheet_add_a_bookmark), style = ClearType.BodyStrong, color = Color.White)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp).clip(RoundedRectangle(22.dp)).background(cellFill())) {
                    items(list.size) { i ->
                        val b = list[i]
                        UserBookmarkRow(
                            name = b.name,
                            page = b.page,
                            renaming = renaming == i,
                            onOpen = {
                                ctl.rememberJump(doc.viewer.currentPage)
                                doc.viewer.goToPage(b.page)
                                ctl.sheet = ViewerSheet.None
                            },
                            onRename = { renaming = i },
                            onRenamed = { name ->
                                renaming = null
                                if (name.isBlank()) vm.tell(text(R.string.sheet_bookmark_name_invalid)) else vm.bookmarks.rename(doc, b, name.trim())
                            },
                            onDelete = { vm.bookmarks.remove(doc, b); vm.tell(text(R.string.sheet_bookmark_removed, b.name)) },
                            onDeleteAll = { vm.confirmDeleteAllBookmarks = doc },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun parentOf(all: List<OutlineEntry>, i: Int): Int {
    val level = all[i].level
    if (level == 0u) return -1
    var k = i - 1
    while (k >= 0 && all[k].level >= level) k--
    return k
}

/** The white cell rows sit in on a sheet. */
@Composable
private fun cellFill(): Color = if (clear.dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.66f)

@Composable
private fun UserBookmarkRow(name: String, page: Int, renaming: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onRenamed: (String) -> Unit, onDelete: () -> Unit, onDeleteAll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var text by remember(renaming) { mutableStateOf(name) }
    // a long press opens Rename / Delete / Delete all, as the reference's list does
    Row(
        Modifier.fillMaxWidth().onGloballyPositioned { anchor = it.boundsInWindow() }
            .pressable(onOpen, onLongClick = { menu = true }, enabled = !renaming, pressedScale = 0.985f)
            .padding(start = 14.dp, end = 14.dp).heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedRectangle(9.dp)).background(Candy.Tangerine), contentAlignment = Alignment.Center) {
            Icon(LucideIcons.Bookmark, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        if (renaming) {
            com.chethan616.clearpdf.ui.PdfField(
                text, { text = it }, stringResource(R.string.common_rename), modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRenamed(text) }),
            )
        } else {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(name, style = ClearType.Body.copy(fontSize = 16.sp), color = clear.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.sheet_page, page + 1), style = ClearType.Caption.copy(fontSize = 12.sp), color = clear.inkSoft)
            }
        }
    }
    if (menu) {
        com.chethan616.clearpdf.ui.glass.GlassMenu(
            anchor, { menu = false },
            listOf(
                com.chethan616.clearpdf.ui.glass.GlassMenuItem(stringResource(R.string.common_rename), LucideIcons.PenLine, onClick = onRename),
                com.chethan616.clearpdf.ui.glass.GlassMenuItem(stringResource(R.string.common_delete), LucideIcons.Trash2, destructive = true, onClick = onDelete),
                com.chethan616.clearpdf.ui.glass.GlassMenuItem(stringResource(R.string.sheet_delete_all), LucideIcons.BookmarkMinus, destructive = true, onClick = onDeleteAll),
            ),
        )
    }
}

/** Attachments: the embedded files; a PDF opens in the viewer, anything else in another app. */
@Composable
fun AttachmentsSheet(vm: AppViewModel, doc: OpenDocument) {
    val ctl = doc.controller
    var list by remember { mutableStateOf<List<AttachmentInfo>>(emptyList()) }
    LaunchedEffect(doc.info.generation) { list = vm.attachments(doc) }
    // the document's own files, as the reference lists them (a comment's file is opened from the comment)
    val files = list.filter { !it.onPage }
    PdfSheet(onDismiss = { ctl.sheet = ViewerSheet.None }, maxHeightFraction = 0.7f) {
        SheetBackHeader(stringResource(R.string.sheet_attachments), onBack = { ctl.sheet = ViewerSheet.More })
        LazyColumn(Modifier.padding(horizontal = 12.dp).clip(RoundedRectangle(22.dp)).background(cellFill())) {
            items(files) { a ->
                SheetRow(LucideIcons.FileText, a.name, { ctl.sheet = ViewerSheet.None; vm.openAttachment(doc, a.name) }, tint = Candy.Grape, chevron = true)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> plural(R.plurals.sheet_size_bytes, bytes.toInt(), bytes)
    bytes < 1024 * 1024 -> text(R.string.sheet_size_kb, bytes / 1024.0)
    else -> text(R.string.sheet_size_mb, bytes / 1024.0 / 1024.0)
}

/** `D:20261011143000+02'00'` (or plain text) as a short local date. */
fun pdfDateLabel(s: String?): String? {
    if (s == null) return null
    val t = pdfDateMillis(s) ?: return s.takeIf { it.isNotBlank() }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))
}

/**
 * The moment a PDF date names (`D:20261011171900Z`, `D:20261011171900-06'00'`, the engine's
 * `2026-10-11 17:19`, or a certificate's `2031.10.19 13:59:58 UTC`).
 */
fun pdfDateMillis(s: String?): Long? {
    if (s == null) return null
    val m = Regex("""(\d{4})[-/.]?(\d{2})?[-/.]?(\d{2})?[ T]?(\d{2})?:?(\d{2})?:?(\d{2})? ?(Z|UTC|[+-]\d{2}'?\d{2}'?)?""").find(s) ?: return null
    val g = m.groupValues
    // with a time zone the moment is exact and shows in the phone's local time; without one, as written
    val tz = g[7]
    val zone = when {
        tz.isEmpty() -> java.util.TimeZone.getDefault()
        tz == "Z" || tz == "UTC" -> java.util.TimeZone.getTimeZone("UTC")
        else -> java.util.TimeZone.getTimeZone("GMT" + tz.substring(0, 3) + ":" + tz.filter { it.isDigit() }.drop(2).padEnd(2, '0').take(2))
    }
    val cal = java.util.Calendar.getInstance(zone)
    cal.clear()
    cal.set(g[1].toInt(), (g[2].ifEmpty { "1" }.toInt() - 1), g[3].ifEmpty { "1" }.toInt(), g[4].ifEmpty { "0" }.toInt(), g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt())
    return cal.timeInMillis
}

/**
 * A tool tile of More tools, as an app icon on a home screen: the tool's glyph white on a rounded
 * square of its own colour (lighter at the top), its name under it.
 */
@Composable
private fun ToolTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(onClick, pressedScale = 0.92f).padding(vertical = 6.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedRectangle(16.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(tint, Color.White, 0.18f), tint))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = ClearType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp), color = clear.ink, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

internal fun tileBackground(tint: Color, dark: Boolean): Color =
    if (dark) Color(tint.red * 0.22f + 0.07f, tint.green * 0.22f + 0.07f, tint.blue * 0.22f + 0.09f) else Color(tint.red * 0.12f + 0.88f, tint.green * 0.12f + 0.88f, tint.blue * 0.12f + 0.88f)

/** The tool colours of More tools and the Tools tab (each tool its own hue). */
object ToolHues {
    val combine = Color(0xFF8F8CF5)
    val compress = Color(0xFF3CC8C2)
    val protect = Color(0xFF8F8CF5)
    val organize = Color(0xFF9FD848)
    val crop = Color(0xFF6E9BF2)
    val ocr = Color(0xFF5BCB6B)
    val create = Color(0xFFF2615C)
    val text = Color(0xFFF2A93B)
    val scan = Color(0xFF3CC8C2)
    val read = Color(0xFF6E9BF2)
    val edit = Color(0xFFEC5BA6)
    val export = Color(0xFFF2615C)
    val sign = Color(0xFF9B7CF2)
    val comment = Color(0xFFF2A93B)
    val redact = Color(0xFFF2615C)
}

/** More tools: the tools that aren't on the bar, as a grid of tiles, and Customize toolbar. */
@Composable
fun MoreToolsSheet(vm: AppViewModel, doc: OpenDocument) {
    val ctl = doc.controller
    var customizing by remember { mutableStateOf(false) }
    PdfSheet(onDismiss = { if (customizing) customizing = false else ctl.sheet = ViewerSheet.None }) {
        if (customizing) {
            CustomizeToolbar(vm) { customizing = false }
            return@PdfSheet
        }
        SheetTitle(stringResource(R.string.common_tool_more_tools))
        val slots = toolbarSlots(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
        val prefs = vm.quickToolbar
        val tools = if (doc.isOffice) emptyList() else prefs.bar.drop(slots) + prefs.grid
        // a grid of app-icon tiles: four to a row on a phone, more on wider screens
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
            val perRow = if (maxWidth >= 560.dp) 6 else if (maxWidth >= 400.dp) 5 else 4
            val cell = maxWidth / perRow
            Column {
                tools.chunked(perRow).forEach { row ->
                    Row(verticalAlignment = Alignment.Top) {
                        row.forEach { t ->
                            ToolTile(t.icon, stringResource(t.title), t.hue, Modifier.width(cell)) {
                                ctl.sheet = ViewerSheet.None
                                vm.runQuickTool(doc, t)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        // Customize toolbar: a plain glass capsule across the sheet
        com.chethan616.clearpdf.ui.glass.GlassPill(
            onClick = { customizing = true }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), height = 48.dp,
        ) {
            Icon(LucideIcons.SlidersHorizontal, null, tint = clear.ink, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.sheet_customize_toolbar), style = ClearType.BodyStrong, color = clear.ink)
        }
        Spacer(Modifier.height(12.dp))
    }
}


/** Save as: the formats the document's engine writes, each saved to Downloads. */
@Composable
fun SaveAsSheet(vm: AppViewModel, doc: OpenDocument) {
    val ctl = doc.controller
    var formats by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(doc.id) { formats = vm.engine?.let { e -> runCatching { e.command { exportFormats(doc.id) } }.getOrNull() }.orEmpty() }
    PdfSheet(onDismiss = { ctl.sheet = ViewerSheet.None }) {
        SheetTitle(stringResource(R.string.office_save_as))
        SheetSection {
            for (f in formats) SheetRow(LucideIcons.FileOutput, stringResource(R.string.office_format, formatName(f), f.uppercase()), {
                ctl.sheet = ViewerSheet.None
                vm.exportOffice(doc, f)
            }, tint = Candy.Sky)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** A format's name as people know it. */
@Composable
private fun formatName(f: String): String = stringResource(
    when (f) {
        "pdf" -> R.string.format_pdf
        "docx" -> R.string.format_docx
        "odt" -> R.string.format_odt
        "rtf" -> R.string.format_rtf
        "html" -> R.string.format_html
        "md" -> R.string.format_md
        "txt" -> R.string.format_txt
        "pptx" -> R.string.format_pptx
        "xlsx" -> R.string.format_xlsx
        "csv" -> R.string.format_csv
        else -> R.string.format_other
    },
)

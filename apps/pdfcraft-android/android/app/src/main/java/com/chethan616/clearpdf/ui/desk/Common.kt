package com.chethan616.clearpdf.ui.desk

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.FilePickKind
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassDialog
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassPill
import com.chethan616.clearpdf.ui.glass.GlassRow
import com.chethan616.clearpdf.ui.glass.GlassSheet
import com.chethan616.clearpdf.ui.glass.GlassTextField
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.FileRow
import com.chethan616.clearpdf.ui.home.FileTool
import com.chethan616.clearpdf.ui.home.displayName
import com.chethan616.clearpdf.ui.home.whenLabel
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.chethan616.clearpdf.ui.viewer.formatSize
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tool as the desk and the Tools tab show it: one candy colour everywhere it appears. */
class ToolDef(
    val key: String,
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val about: Int,
    val accent: Color,
    val categories: Set<String>,
    val go: (AppViewModel) -> Unit,
)

object ToolCatalog {
    val Scan = ToolDef("scan", LucideIcons.Camera, R.string.home_new_scan, R.string.home_about_scan, Candy.Tomato, setOf("Create")) { it.startScan() }
    val Edit = ToolDef("edit", LucideIcons.FilePenLine, R.string.common_tool_edit_pdf, R.string.home_about_edit, Candy.Bubblegum, setOf("Edit")) { it.toolForFile(FileTool.Edit) }
    val FillSign = ToolDef("sign", LucideIcons.Signature, R.string.common_tool_fill_sign, R.string.home_about_fill_sign, Candy.Grape, emptySet()) { it.toolForFile(FileTool.FillSign) }
    val Comment = ToolDef("comment", LucideIcons.MessageSquareText, R.string.home_tool_add_comment, R.string.home_about_comment, Candy.Tangerine, emptySet()) { it.toolForFile(FileTool.Comment) }
    val ReadAloud = ToolDef("read", LucideIcons.Volume2, R.string.common_tool_read_aloud, R.string.home_about_read_aloud, Candy.Sky, setOf("Read")) { it.toolForFile(FileTool.ReadAloud) }
    val Create = ToolDef("create", LucideIcons.FilePlus2, R.string.common_tool_create_pdf, R.string.home_about_create, Candy.Tomato, setOf("Create")) { it.openCreate() }
    val Combine = ToolDef("combine", LucideIcons.Combine, R.string.common_tool_combine, R.string.home_about_combine, Candy.Sky, setOf("Create", "Edit")) { it.pickFiles(FilePickKind.Combine) }
    val Organize = ToolDef("organize", LucideIcons.LayoutGrid, R.string.common_tool_organize, R.string.home_about_organize, Candy.Lime, setOf("Edit")) { it.toolForFile(FileTool.Organize) }
    val Crop = ToolDef("crop", LucideIcons.Crop, R.string.common_tool_crop, R.string.home_about_crop, Candy.Mint, setOf("Edit")) { it.toolForFile(FileTool.Crop) }
    val Compress = ToolDef("compress", LucideIcons.FileArchive, R.string.common_tool_compress, R.string.home_about_compress, Candy.Mint, setOf("Convert")) { it.toolForFile(FileTool.Compress) }
    val Export = ToolDef("export", LucideIcons.FileOutput, R.string.common_tool_export, R.string.home_about_export, Candy.Tangerine, setOf("Convert")) { it.toolForFile(FileTool.Export) }
    val Protect = ToolDef("protect", LucideIcons.FileLock2, R.string.common_tool_set_password, R.string.home_about_protect, Candy.Grape, setOf("Edit")) { it.toolForFile(FileTool.Protect) }
    val Photos = ToolDef("photos", LucideIcons.Images, R.string.desk_photos_to_pdf, R.string.home_about_create, Candy.Lemon, setOf("Create")) { it.pickPhotosToPdf() }

    val All = listOf(Edit, FillSign, Scan, Combine, Comment, ReadAloud, Organize, Compress, Export, Crop, Protect, Create)
}

/**
 * A document's first page on white paper (the render, or a candy placeholder while it draws),
 * optionally tilted like a sheet dropped on a desk.
 */
@Composable
fun PaperThumb(vm: AppViewModel, path: String?, width: Dp, height: Dp, modifier: Modifier = Modifier, tilt: Float = 0f, corner: Dp = 8.dp) {
    var img by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path != null) img = withContext(Dispatchers.IO) { vm.thumbnails.of(path)?.asImageBitmap() }
    }
    val c = clear
    val shape = RoundedRectangle(corner)
    Box(
        modifier.size(width, height).rotate(tilt).clip(shape).background(Color.White)
            .border(1.dp, if (c.dark) Color.White.copy(alpha = 0.10f) else c.ink.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val i = img
        if (i != null) {
            Image(i, null, modifier = Modifier.size(width, height), contentScale = ContentScale.Crop)
        } else {
            // a blank page: a few grey "lines" and a red PDF corner, drawn instead of an icon
            Column(Modifier.fillMaxWidth().padding(horizontal = width * 0.16f), verticalArrangement = Arrangement.spacedBy(height * 0.06f)) {
                Box(Modifier.fillMaxWidth(0.62f).height(height * 0.05f).clip(CircleShape).background(Color(0xFF17141F).copy(alpha = 0.20f)))
                repeat(4) { k -> Box(Modifier.fillMaxWidth(if (k == 3) 0.5f else 1f).height(height * 0.03f).clip(CircleShape).background(Color(0xFF17141F).copy(alpha = 0.09f))) }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(width * 0.08f).clip(RoundedRectangle(4.dp)).background(Candy.Tomato).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("PDF", color = Color.White, fontSize = (width.value * 0.11f).coerceIn(7f, 13f).sp, style = ClearType.Eyebrow.copy(letterSpacing = 0.4.sp))
            }
        }
    }
}

/** The big title every tab opens with: a small caps [eyebrow], the [title] in display type, and [trailing] controls. */
@Composable
fun ScreenHeader(eyebrow: String?, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val c = clear
    Row(modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow.uppercase(), style = ClearType.Eyebrow, color = c.inkSoft)
                Spacer(Modifier.height(6.dp))
            }
            Text(title, style = ClearType.Display, color = c.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** The round glass Settings button that sits in each tab's header. */
@Composable
fun SettingsButton(vm: AppViewModel) {
    GlassCircle(onClick = { vm.settingsOpen = true }, label = stringResource(R.string.home_settings)) {
        GlassIcon(LucideIcons.Settings, null, Modifier.size(21.dp))
    }
}

/** A file in a list: its paper, its name, when and how big, a star, and its menu. */
@Composable
fun FileListItem(vm: AppViewModel, r: FileRow, recent: Boolean, modifier: Modifier = Modifier) {
    val c = clear
    val selecting = vm.selectedFiles.isNotEmpty()
    val ticked = r.key in vm.selectedFiles
    Row(
        modifier.fillMaxWidth()
            .pressable(onClick = { if (selecting) vm.toggleSelected(r) else r.open() }, onLongClick = { vm.toggleSelected(r) })
            .background(if (ticked) Candy.Sky.copy(alpha = 0.14f) else Color.Transparent)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperThumb(vm, r.thumbPath, 40.dp, 52.dp, corner = 6.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName(r.name), style = ClearType.BodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(whenLabel(r.date), r.size.takeIf { it > 0 }?.let { formatSize(it) }).joinToString("  ·  "),
                style = ClearType.Caption, color = c.inkSoft, maxLines = 1,
            )
        }
        if (r.starred) Icon(LucideIcons.Star, stringResource(R.string.home_starred_description), tint = Candy.Lemon, modifier = Modifier.padding(horizontal = 4.dp).size(16.dp))
        if (selecting) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (ticked) Candy.Sky else Color.Transparent)
                        .border(2.dp, if (ticked) Candy.Sky else c.inkFaint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (ticked) Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        } else {
            Box(Modifier.size(44.dp).pressable({ vm.fileSheet = r to recent }), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.EllipsisVertical, stringResource(R.string.home_more_options_for, r.name), tint = c.inkSoft, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A file's sheet: four big candy actions up top, then its tools and the rest. */
@Composable
fun FileActionsSheet(vm: AppViewModel, r: FileRow, recent: Boolean, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    fun act(f: () -> Unit) {
        onDismiss()
        f()
    }
    GlassSheet(onDismiss = onDismiss, title = displayName(r.name), subtitle = listOfNotNull(whenLabel(r.date), r.size.takeIf { it > 0 }?.let { formatSize(it) }).joinToString("  ·  ")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigAction(LucideIcons.Share2, stringResource(R.string.common_share), Candy.Sky) { act { vm.shareFile(r) } }
            BigAction(LucideIcons.Star, stringResource(if (r.starred) R.string.home_unstar else R.string.home_star), Candy.Lemon) { act { vm.setStarred(r.key, !r.starred) } }
            BigAction(LucideIcons.Pencil, stringResource(R.string.common_rename), Candy.Grape) { renaming = true }
            BigAction(LucideIcons.Printer, stringResource(R.string.home_print), Candy.Mint) { act { vm.printFile(r) } }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            GlassRow(stringResource(R.string.common_tool_edit_pdf), { act { vm.openFor(r, FileTool.Edit) } }, icon = LucideIcons.FilePenLine, accent = ToolCatalog.Edit.accent)
            GlassRow(stringResource(R.string.common_tool_read_aloud), { act { vm.openFor(r, FileTool.ReadAloud) } }, icon = LucideIcons.Volume2, accent = ToolCatalog.ReadAloud.accent)
            GlassRow(stringResource(R.string.home_export_as), { act { vm.openFor(r, FileTool.Export) } }, icon = LucideIcons.FileOutput, accent = ToolCatalog.Export.accent)
            GlassRow(stringResource(R.string.common_tool_compress), { act { vm.openFor(r, FileTool.Compress) } }, icon = LucideIcons.FileArchive, accent = ToolCatalog.Compress.accent)
            GlassRow(stringResource(R.string.common_tool_set_password), { act { vm.openFor(r, FileTool.Protect) } }, icon = LucideIcons.FileLock2, accent = ToolCatalog.Protect.accent)
            GlassRow(stringResource(R.string.home_open_with), { act { vm.openWith(r) } }, icon = LucideIcons.ExternalLink, accent = Candy.Sky)
            GlassRow(stringResource(R.string.home_duplicate), { act { vm.duplicateFile(r) } }, icon = LucideIcons.Copy, accent = Candy.Tangerine)
            GlassRow(stringResource(R.string.common_save_copy), { act { vm.saveFileCopy(r) } }, icon = LucideIcons.FileDown, accent = Candy.Mint)
            if (recent) GlassRow(stringResource(R.string.home_remove_from_recent), { act { vm.removeRecent(r.key) } }, icon = LucideIcons.X, accent = Candy.Grape)
            GlassRow(stringResource(R.string.common_delete), { deleting = true }, icon = LucideIcons.Trash2, destructive = true)
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(r.name.removeSuffix(".pdf")) }
        GlassDialog(
            onDismiss = { renaming = false }, title = stringResource(R.string.common_rename), icon = LucideIcons.Pencil, accent = Candy.Grape,
            confirm = stringResource(R.string.common_done), confirmEnabled = name.isNotBlank() && !name.startsWith("."),
            onConfirm = { renaming = false; onDismiss(); vm.renameFile(r, name) },
            dismiss = stringResource(R.string.common_cancel),
        ) { GlassTextField(name, { name = it }, stringResource(R.string.home_enter_name)) }
    }
    if (deleting) {
        GlassDialog(
            onDismiss = { deleting = false }, title = stringResource(R.string.home_delete_file_title), icon = LucideIcons.Trash2, destructive = true,
            confirm = stringResource(R.string.common_delete), onConfirm = { deleting = false; onDismiss(); vm.deleteFile(r) },
            dismiss = stringResource(R.string.common_cancel),
        ) { Text(stringResource(R.string.home_delete_file_body, r.name), style = ClearType.Body, color = clear.inkSoft) }
    }
}

/** A big round candy action with its name under it. */
@Composable
private fun BigAction(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    val c = clear
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(onClick = onClick, size = 56.dp, tint = accent, label = label) { GlassIcon(icon, null, Modifier.size(24.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = ClearType.Caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** An empty list, playfully: three tilted papers, a big line and a candy action. */
@Composable
fun EmptyHero(title: String, body: String, action: String? = null, accent: Color = Candy.Tomato, onAction: (() -> Unit)? = null) {
    val c = clear
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(150.dp, 120.dp), contentAlignment = Alignment.Center) {
            Paper(Modifier.offset(x = (-30).dp, y = 6.dp).rotate(-14f), Candy.Sky)
            Paper(Modifier.offset(x = 30.dp, y = 4.dp).rotate(12f), Candy.Lemon)
            Paper(Modifier.rotate(-2f), accent)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = ClearType.Headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = ClearType.Body, color = c.inkSoft, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            GlassPill(onClick = onAction, tint = accent) { Text(action, style = ClearType.BodyStrong, color = Color.White) }
        }
    }
}

@Composable
private fun Paper(modifier: Modifier, accent: Color) {
    Box(
        modifier.size(64.dp, 84.dp).clip(RoundedRectangle(10.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F0EA))))
            .border(1.dp, Color(0xFF17141F).copy(alpha = 0.08f), RoundedRectangle(10.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape).background(accent))
            repeat(3) { Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF17141F).copy(alpha = 0.10f))) }
        }
    }
}

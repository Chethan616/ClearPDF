package com.chethan616.clearpdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import com.chethan616.clearpdf.ui.home.chipRowFade
import com.chethan616.clearpdf.ui.glass.glassBar
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.engine.toBitmap
import com.chethan616.clearpdf.ui.viewer.formatSize
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/** A tool's full screen: a top bar with back and title, the content, and the action button at the bottom. */
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    action: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    /** Black under the camera and the viewer's band behind the navigation buttons, as the viewer's own screens have. */
    viewerBands: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    GlassToolBackground {
        Column(Modifier.fillMaxSize().safeTopPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(com.chethan616.clearpdf.ui.viewer.TopBarHeight).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassBack(onBack)
                Spacer(Modifier.width(14.dp))
                Text(title, style = com.chethan616.clearpdf.ui.theme.ClearType.Title, color = com.chethan616.clearpdf.ui.theme.clear.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            if (action != null) ActionPill(action, actionEnabled, onAction, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/**
 * A tool's screen stands on the app's own wallpaper (a glass layer of its own), so its glass Back,
 * action and panels refract it and follow Settings ▸ liquid glass and motion like the rest of the app.
 */
@Composable
private fun GlassToolBackground(content: @Composable () -> Unit) {
    com.chethan616.clearpdf.ui.glass.GlassHost(
        Modifier.fillMaxSize(),
        paint = { com.chethan616.clearpdf.ui.glass.Aurora(com.chethan616.clearpdf.ui.glass.AuroraMoods.Tools) },
    ) { content() }
}

/** A tool's Back: the round glass Back of every screen, leaving a beat after the tap. */
@Composable
private fun GlassBack(onBack: () -> Unit) {
    com.chethan616.clearpdf.ui.glass.GlassBackButton(onBack)
}

/** A file as the tools show it: an icon, its name and size. */
@Composable
fun FileCard(name: String, size: Long?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = pdfColors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).then(com.chethan616.clearpdf.ui.glass.washPanel()).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfBadge()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name.substringBeforeLast('.'), color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(name.substringAfterLast('.', "").uppercase().ifEmpty { null }, size?.let { formatSize(it) }).joinToString("  •  "), color = c.textSecondary, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

/** The red document badge with "PDF" on it. */
@Composable
fun PdfBadge(size: Int = 36) {
    val c = pdfColors
    Box(Modifier.size(size.dp, (size * 1.2f).dp).clip(RoundedCornerShape(4.dp)).background(c.red), contentAlignment = Alignment.BottomCenter) {
        Text("PDF", color = Color.White, fontSize = (size / 3.6f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/**
 * A tool page as the reference lays its own out: a bar with only back (and [trailing]), a large
 * title, the content, then the action as a blue pill at the lower right.
 */
@Composable
fun ToolPage(
    title: String,
    onBack: () -> Unit,
    action: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    // the keyboard covers the pill rather than lifting it, as the reference's does
    GlassToolBackground {
        Column(Modifier.fillMaxSize().safeTopPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(com.chethan616.clearpdf.ui.viewer.TopBarHeight).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassBack(onBack)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Text(title, style = com.chethan616.clearpdf.ui.theme.ClearType.Headline, color = com.chethan616.clearpdf.ui.theme.clear.hero, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 14.dp))
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            Row(Modifier.fillMaxWidth().padding(end = 24.dp, bottom = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.End) {
                ActionPill(action, actionEnabled, onAction)
            }
        }
    }
}

/** A small action on a tool's screen: a glass chip, faint while it can't be used. */
@Composable
private fun GlassTextButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    com.chethan616.clearpdf.ui.glass.GlassChip(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        height = 42.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) { content() }
}

/** The candy glass pill a tool ends with (plain glass, faint, while it can't be used). */
@Composable
fun ActionPill(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = com.chethan616.clearpdf.ui.theme.clear
    com.chethan616.clearpdf.ui.glass.GlassPill(
        onClick = { if (enabled) onClick() },
        modifier = modifier.widthIn(min = 110.dp),
        tint = if (enabled) com.chethan616.clearpdf.ui.theme.Candy.Sky else Color.Unspecified,
        height = 50.dp,
        horizontalPadding = 24.dp,
        label = label,
    ) { Text(label, style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = if (enabled) Color.White else c.inkFaint, maxLines = 1) }
}

/** The file a tool works on, in an outlined card: a picture of its first page, its name, type and size. */
@Composable
fun OutlinedFileCard(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    var thumb by remember(doc.info.generation) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc.info.generation) {
        val eng = vm.engine ?: return@LaunchedEffect
        val p = doc.info.pages.firstOrNull() ?: return@LaunchedEffect
        val scale = 120f / maxOf(p.width, p.height).coerceAtLeast(1f)
        thumb = runCatching { withContext(eng.thumbnails) { eng.session.renderPage(doc.id, 0u, scale, null).toBitmap().asImageBitmap() } }.getOrNull()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(com.chethan616.clearpdf.ui.glass.washPanel()).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp, 40.dp).background(Color.White)) {
            thumb?.let { Image(it, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.name.substringBeforeLast('.'), color = c.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("PDF • ${formatSize(doc.info.fileSize.toLong())}", color = c.text, fontSize = 13.sp)
        }
    }
}

/** A radio choice of a tool page: the circle, a bold name and a line under it. */
@Composable
private fun ToolChoice(selected: Boolean, name: String, description: String?, onClick: () -> Unit) {
    val c = pdfColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 16.dp, top = if (description == null) 0.dp else 6.dp, bottom = if (description == null) 0.dp else 8.dp),
        verticalAlignment = if (description == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        com.chethan616.clearpdf.ui.glass.GlassRadio(selected, onClick, Modifier.padding(top = if (description == null) 0.dp else 4.dp))
        Column(Modifier.padding(start = 6.dp, top = if (description == null) 0.dp else 10.dp)) {
            Text(name, color = c.label, fontSize = 16.sp, fontWeight = if (description == null) FontWeight.Normal else FontWeight.SemiBold)
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(description, color = c.text, fontSize = 14.sp)
            }
        }
    }
}

/** Compress PDF: one of three levels (medium by default), then a smaller copy in Downloads. */
@Composable
fun CompressScreen(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    var level by remember { mutableStateOf(1) }
    val levels = listOf(
        stringResource(R.string.tools_compress_high) to stringResource(R.string.tools_compress_high_desc),
        stringResource(R.string.tools_compress_medium) to stringResource(R.string.tools_compress_medium_desc),
        stringResource(R.string.tools_compress_low) to stringResource(R.string.tools_compress_low_desc),
    )
    ToolPage(stringResource(R.string.common_tool_compress), { vm.toolScreen = null }, stringResource(R.string.tools_compress_action), true, { vm.compress(doc, level) }) {
        OutlinedFileCard(vm, doc)
        Text(stringResource(R.string.tools_compression_level), color = c.label, fontSize = 15.sp, modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 4.dp))
        levels.forEachIndexed { i, (name, desc) -> ToolChoice(level == i, name, desc) { level = i } }
    }
}

/** A password field of Set password: its label above an outlined box, with the eye to show it. */
@Composable
private fun PasswordField(label: String, value: String, onValue: (String) -> Unit, visible: Boolean, onToggle: (() -> Unit)?, error: Boolean, focus: androidx.compose.ui.focus.FocusRequester? = null) {
    val c = pdfColors
    var focused by remember { mutableStateOf(false) }
    Text(label, color = c.label, fontSize = 16.sp, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(44.dp).clip(RoundedCornerShape(4.dp)).background(c.menu)
            .border(if (focused) 2.dp else 1.dp, if (error) c.red else if (focused) c.toolAccent else c.divider, RoundedCornerShape(4.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 17.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.toolAccent),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.weight(1f).then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        )
        BarIcon(if (visible) LucideIcons.Eye else LucideIcons.EyeOff, stringResource(if (visible) R.string.ui_hide_password else R.string.ui_show_password), { onToggle?.invoke() }, size = 44.dp)
    }
}

/** Set password: a password twice (6 to 128 printable characters), then a protected copy in Downloads. */
@Composable
fun ProtectScreen(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    val ink = com.chethan616.clearpdf.ui.theme.clear
    val locked = doc.info.encrypted
    // the way that makes sense for this file is open first: a protected one to remove, else to set
    var removing by remember { mutableStateOf(locked) }
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val bad = pw.isNotEmpty() && (pw.length !in 6..128 || pw.any { it < '!' || it > '~' })
    val mismatch = confirm.isNotEmpty() && confirm != pw
    val canSet = pw.isNotEmpty() && confirm.isNotEmpty() && !bad && !mismatch
    ToolScaffold(
        stringResource(R.string.tool_encryption), { vm.toolScreen = null },
        action = stringResource(if (removing) R.string.tools_unlock_action else R.string.common_tool_set_password),
        actionEnabled = if (removing) locked else canSet,
        onAction = { if (removing) vm.removePassword(doc) else vm.protect(doc, pw) },
        trailing = {
            com.chethan616.clearpdf.ui.glass.GlassCircle({ info = true }, label = stringResource(R.string.tools_about_passwords)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Info, null, Modifier.size(20.dp))
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            // how the file stands now
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (locked) LucideIcons.Lock else LucideIcons.LockOpen, null, tint = ink.inkSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (locked) R.string.tools_unlock_title else R.string.msg_no_password_yet),
                    style = com.chethan616.clearpdf.ui.theme.ClearType.Label, color = ink.inkSoft,
                )
            }
            EncryptionChoice(
                LucideIcons.Lock, com.chethan616.clearpdf.ui.theme.Candy.Grape,
                stringResource(R.string.common_tool_set_password), stringResource(R.string.tools_set_password_body),
                chosen = !removing, enabled = true, onClick = { removing = false },
            ) {
                PasswordField(stringResource(R.string.tools_new_password), pw, { pw = it }, visible, { visible = !visible }, bad)
                if (bad) Text(stringResource(R.string.tools_password_rule), color = c.red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                PasswordField(stringResource(R.string.tools_repeat_password), confirm, { confirm = it }, visible, { visible = !visible }, mismatch)
                if (mismatch) Text(stringResource(R.string.tools_password_mismatch), color = c.red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            EncryptionChoice(
                LucideIcons.LockOpen, com.chethan616.clearpdf.ui.theme.Candy.Mint,
                stringResource(R.string.tools_unlock_action), stringResource(if (locked) R.string.tools_unlock_body else R.string.msg_no_password),
                chosen = removing, enabled = locked, onClick = { removing = true },
            ) {}
        }
    }
    if (info) {
        PdfDialog(onDismiss = { info = false }, title = stringResource(R.string.tools_about_passwords), confirm = stringResource(R.string.common_ok), onConfirm = { info = false }, dismiss = null) {
            Text(stringResource(R.string.tools_about_passwords_body), color = c.text, fontSize = 14.sp)
        }
    }
}

/**
 * One of Encryption's two ways, as a card: its sign in its colour, what it is called and what it
 * does, a tick when it is the chosen one (the card is then ringed, and [more] opens under it). The
 * button at the foot of the screen does the chosen one. A way that can't apply (removing a password
 * from a file that has none) is shown faint and can't be chosen.
 */
@Composable
private fun EncryptionChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    body: String,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    more: @Composable ColumnScope.() -> Unit,
) {
    val ink = com.chethan616.clearpdf.ui.theme.clear
    val shape = com.chethan616.clearpdf.ui.glass.RoundedRectangle(24.dp)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(ink.glass)
            .border(if (chosen && enabled) 2.dp else 1.dp, if (chosen && enabled) com.chethan616.clearpdf.ui.theme.Candy.Sky else ink.ink.copy(alpha = 0.10f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(com.chethan616.clearpdf.ui.glass.RoundedRectangle(14.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = ink.ink)
                Text(body, style = com.chethan616.clearpdf.ui.theme.ClearType.Caption, color = ink.inkSoft, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (chosen && enabled) com.chethan616.clearpdf.ui.theme.Candy.Sky else Color.Transparent)
                    .border(2.dp, if (chosen && enabled) com.chethan616.clearpdf.ui.theme.Candy.Sky else ink.inkFaint, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (chosen && enabled) Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        androidx.compose.animation.AnimatedVisibility(chosen && enabled) { Column(content = more) }
    }
}

/** Export PDF: Document (Word, RTF, Excel, PowerPoint) or Image (JPEG, PNG) of its pages. */
@Composable
fun ExportScreen(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    var tab by remember { mutableStateOf(0) }
    // the image format used last time comes first chosen
    var format by remember(tab) { mutableStateOf(if (tab == 1 && vm.lastImageFormat == "png") 1 else 0) }
    val formats = if (tab == 0) {
        listOf(
            stringResource(R.string.tools_format_word) to "docx", stringResource(R.string.tools_format_rtf) to "rtf",
            stringResource(R.string.tools_format_excel) to "xlsx", stringResource(R.string.tools_format_powerpoint) to "pptx",
            stringResource(R.string.tools_format_text) to "txt",
        )
    } else {
        listOf(stringResource(R.string.tools_format_jpeg) to "jpg", stringResource(R.string.tools_format_png) to "png")
    }
    ToolPage(stringResource(R.string.common_tool_export), { vm.toolScreen = null }, stringResource(R.string.tools_export_action), true, {
        val ext = formats[format].second
        if (tab == 0) vm.exportDocument(doc, ext) else vm.exportImages(doc, ext)
    }) {
        // the tabs sit at the left, each underlined across its own label
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.divider))
            Row {
                listOf(R.string.tools_export_document, R.string.tools_export_image).forEachIndexed { i, label ->
                    Column(Modifier.clickable { tab = i }.padding(horizontal = 8.dp).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                        Text(stringResource(label), color = if (tab == i) c.label else c.text, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (tab == i) c.label else Color.Transparent))
                    }
                    if (i == 0) Spacer(Modifier.width(14.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0 && doc.info.encrypted && !doc.info.allowsCopying) {
            Text(stringResource(R.string.tools_export_not_allowed), color = c.red, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
        }
        formats.forEachIndexed { i, (label, _) -> ToolChoice(format == i, label, null) { format = i } }
    }
}

/**
 * Create PDF's first page: where the files come from (the photos, or any file through the
 * system's picker), and a scan to make a new one. Choosing closes this page.
 */
@Composable
fun CreateSourcesScreen(vm: AppViewModel) {
    val back = { vm.toolScreen = null }
    var typing by remember { mutableStateOf(vm.createTyping.also { vm.createTyping = false }) }
    ToolScaffold(stringResource(R.string.tools_create_title), back, action = null, actionEnabled = false, onAction = {}) {
        Spacer(Modifier.height(8.dp))
        com.chethan616.clearpdf.ui.glass.WashPanel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            SourceRow(LucideIcons.Images, stringResource(R.string.home_photos), com.chethan616.clearpdf.ui.theme.Candy.Tangerine) { vm.toolScreen = null; vm.pickPhotosToPdf() }
            SourceRow(LucideIcons.FolderOpen, stringResource(R.string.tools_browse_files), com.chethan616.clearpdf.ui.theme.Candy.Grape) { vm.toolScreen = null; vm.pickFiles(FilePickKind.Create) }
        }
        Text(
            stringResource(R.string.tools_or_make_new).uppercase(), style = com.chethan616.clearpdf.ui.theme.ClearType.Eyebrow,
            color = com.chethan616.clearpdf.ui.theme.clear.inkSoft, modifier = Modifier.padding(start = 26.dp, top = 24.dp, bottom = 8.dp),
        )
        com.chethan616.clearpdf.ui.glass.WashPanel(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            SourceRow(LucideIcons.Type, stringResource(R.string.tools_create_from_text), com.chethan616.clearpdf.ui.theme.Candy.Sky) { typing = true }
            SourceRow(LucideIcons.Camera, stringResource(R.string.tools_scan_document), com.chethan616.clearpdf.ui.theme.Candy.Tomato) { vm.toolScreen = null; vm.startScan() }
        }
    }
    // Text to PDF: a name and the words, typed or pasted, set out as pages by the Word engine
    if (typing) {
        var name by remember { mutableStateOf("") }
        var words by remember { mutableStateOf("") }
        com.chethan616.clearpdf.ui.glass.GlassDialog(
            onDismiss = { typing = false },
            title = stringResource(R.string.tools_create_from_text),
            confirm = stringResource(R.string.tools_create_action),
            confirmEnabled = words.isNotBlank(),
            onConfirm = {
                typing = false
                vm.toolScreen = null
                vm.createFromText(name, words)
            },
            dismiss = stringResource(R.string.common_cancel),
        ) {
            com.chethan616.clearpdf.ui.glass.GlassTextField(name, { name = it }, stringResource(R.string.tools_text_name))
            Spacer(Modifier.height(10.dp))
            com.chethan616.clearpdf.ui.glass.GlassTextField(
                words, { words = it }, stringResource(R.string.tools_text_hint),
                modifier = Modifier.heightIn(min = 160.dp), singleLine = false,
            )
        }
    }
}

@Composable
private fun SourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    com.chethan616.clearpdf.ui.glass.GlassRow(label, onClick, icon = icon, accent = accent)
}

/** The tool screen that is open. */
@Composable
fun ToolScreenHost(vm: AppViewModel, screen: ToolScreen) {
    when (screen) {
        is ToolScreen.Compress -> CompressScreen(vm, screen.doc)
        is ToolScreen.Combine -> CombineScreen(vm)
        is ToolScreen.Protect -> ProtectScreen(vm, screen.doc)
        is ToolScreen.Crop -> CropScreen(vm, screen.doc)
        is ToolScreen.Export -> ExportScreen(vm, screen.doc)
        ToolScreen.Scan -> ScanScreen(vm)
        ToolScreen.Create -> CreateSourcesScreen(vm)
    }
}


/**
 * Combine files, as the reference's: "Combine files" with an outlined Add files at the top, the
 * files in order (the open document first when there is one; hold the grip to reorder, the minus to
 * remove), and Combine across the bottom once there are two to twelve of them.
 */
@Composable
fun CombineScreen(vm: AppViewModel) {
    var naming by remember { mutableStateOf(false) }
    val c = pdfColors
    val files = vm.combineFiles
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 58.dp.toPx() }
    val ok = files.size in 2..12
    val back = { vm.toolScreen = null; vm.combineFiles.clear() }
    val dragLabel = stringResource(R.string.tools_drag_to_reorder)
    val removedMessage = stringResource(R.string.tools_file_removed)
    BackHandler(onBack = back)
    val ink = com.chethan616.clearpdf.ui.theme.clear
    GlassToolBackground {
    Column(Modifier.fillMaxSize().safeTopPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(com.chethan616.clearpdf.ui.viewer.TopBarHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBack(back)
            Spacer(Modifier.width(14.dp))
            Text(
                if (files.size > 1) pluralStringResource(R.plurals.tools_file_count, files.size, files.size) else stringResource(R.string.common_tool_combine),
                style = com.chethan616.clearpdf.ui.theme.ClearType.Title, color = ink.ink, maxLines = 1, modifier = Modifier.weight(1f),
            )
            com.chethan616.clearpdf.ui.glass.GlassPill(onClick = { vm.pickFiles(FilePickKind.Combine) }, height = 44.dp) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Plus, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tools_add_files), style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = ink.ink, maxLines = 1)
            }
        }
        if (files.size > 12) Text(stringResource(R.string.tools_combine_too_many), color = c.red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            itemsIndexed(files, key = { _, f -> f.uri.toString() }) { i, f ->
                Row(
                    Modifier.fillMaxWidth().height(62.dp)
                        .offset { IntOffset(0, if (dragIndex == i) dragOffset.roundToInt() else 0) }
                        .then(com.chethan616.clearpdf.ui.glass.washPanel())
                        .then(if (dragIndex == i) Modifier.border(2.dp, com.chethan616.clearpdf.ui.theme.Candy.Sky, com.chethan616.clearpdf.ui.glass.RoundedRectangle(20.dp)) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).pointerInput(f.uri) {
                            detectDragGestures(
                                onDragStart = { dragIndex = files.indexOf(f); dragOffset = 0f },
                                onDragEnd = { dragIndex = null; dragOffset = 0f },
                                onDragCancel = { dragIndex = null; dragOffset = 0f },
                            ) { change, d ->
                                change.consume()
                                val from = dragIndex ?: return@detectDragGestures
                                dragOffset += d.y
                                val steps = (dragOffset / rowPx).toInt()
                                val to = (from + steps).coerceIn(0, files.lastIndex)
                                if (to != from) {
                                    files.add(to, files.removeAt(from))
                                    dragIndex = to
                                    dragOffset -= (to - from) * rowPx
                                }
                            }
                        }.semantics { contentDescription = dragLabel },
                        contentAlignment = Alignment.Center,
                    ) { Icon(LucideIcons.GripVertical, null, tint = ink.inkSoft, modifier = Modifier.size(20.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(f.name.substringBeforeLast('.'), style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(f.name.substringAfterLast('.', "").uppercase(), f.size?.let { formatSize(it) }).joinToString(" • "), style = com.chethan616.clearpdf.ui.theme.ClearType.Caption, color = ink.inkSoft)
                    }
                    com.chethan616.clearpdf.ui.glass.GlassCircle(onClick = { files.remove(f); vm.tell(removedMessage) }, size = 38.dp, label = stringResource(R.string.tools_remove_file, f.name)) {
                        com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Minus, null, Modifier.size(18.dp), tint = com.chethan616.clearpdf.ui.theme.Candy.Tomato)
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
        ActionPill(stringResource(R.string.tools_combine_as_pdf), ok, { naming = true }, Modifier.fillMaxWidth().padding(16.dp))
        if (naming) {
            var name by remember { mutableStateOf("Combine " + java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date())) }
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            PdfDialog(
                onDismiss = { naming = false }, title = stringResource(R.string.tools_combine_name_title), confirm = stringResource(R.string.common_save),
                onConfirm = { naming = false; vm.combine(name.trim().ifEmpty { "Combine" }) },
                confirmEnabled = name.isNotBlank(),
            ) { PdfField(name, { name = it }, stringResource(R.string.common_file_name), focus = focus) }
        }
    }
    }
}


/** A file picked for a tool: where it is, its name and size. */
data class PickedFile(val uri: android.net.Uri, val name: String, val size: Long?)

/** What a file picker was opened for. */
enum class FilePickKind { Combine, Create, Insert, Open, DigitalId }


/**
 * Crop pages, laid out as the reference's: the page inset 32 dp on black, a crop frame with ringed
 * corner handles and pill handles mid-edge, the page number in a grey pill under it, then the All
 * pages switch and Reset. Save and Reset stay grey until the frame moves.
 */
@Composable
fun CropScreen(vm: AppViewModel, doc: OpenDocument) {
    val c = pdfColors
    var page by remember { mutableStateOf(doc.viewer.currentPage) }
    var allPages by remember { mutableStateOf(false) }
    // the crop as fractions of the page: left, top, right, bottom
    var rect by remember { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }
    var bitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    val p = doc.info.pages.getOrNull(page)
    LaunchedEffect(page) {
        val eng = vm.engine ?: return@LaunchedEffect
        val info = p ?: return@LaunchedEffect
        val scale = 1200f / maxOf(info.width, info.height).coerceAtLeast(1f)
        bitmap = runCatching { withContext(eng.thumbnails) { eng.session.renderPage(doc.id, page.toUInt(), scale, null).toBitmap().asImageBitmap() } }.getOrNull()
    }
    val changed = !(rect[0] == 0f && rect[1] == 0f && rect[2] == 1f && rect[3] == 1f)
    ToolScaffold(stringResource(if (allPages) R.string.tools_all_pages else R.string.tools_current_page), { vm.toolScreen = null }, null, false, {},
        viewerBands = true,
        trailing = {
            ActionPill(stringResource(R.string.common_save), changed, {
                val info = p ?: return@ActionPill
                vm.crop(doc, if (allPages) null else page, floatArrayOf(rect[0] * info.width, rect[1] * info.height, rect[2] * info.width, rect[3] * info.height))
            })
        }) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(com.chethan616.clearpdf.ui.glass.washPanel()), contentAlignment = Alignment.Center) {
            val info = p ?: return@BoxWithConstraints
            val ratio = info.width / info.height.coerceAtLeast(1f)
            // the page sits 32 dp in from the sides; touches are taken a margin beyond its edges
            val margin = 32f
            val boxW = minOf(maxWidth.value - 2 * margin, (maxHeight.value - 2 * margin) * ratio).coerceAtLeast(1f)
            val boxH = boxW / ratio
            val density = LocalDensity.current
            val wPx = with(density) { boxW.dp.toPx() }
            val hPx = with(density) { boxH.dp.toPx() }
            val mPx = with(density) { margin.dp.toPx() }
            Box(
                Modifier.size((boxW + 2 * margin).dp, (boxH + 2 * margin).dp).pointerInput(page) {
                    var mode = -1
                    detectDragGestures(
                        onDragStart = { o ->
                            val x = (o.x - mPx) / wPx
                            val y = (o.y - mPx) / hPx
                            val near = 0.08f
                            val l = kotlin.math.abs(x - rect[0]) < near
                            val r = kotlin.math.abs(x - rect[2]) < near
                            val t = kotlin.math.abs(y - rect[1]) < near
                            val b = kotlin.math.abs(y - rect[3]) < near
                            mode = when {
                                l && t -> 0; r && t -> 1; r && b -> 2; l && b -> 3
                                l -> 4; t -> 5; r -> 6; b -> 7
                                x in rect[0]..rect[2] && y in rect[1]..rect[3] -> 8
                                else -> -1
                            }
                        },
                    ) { change, d ->
                        change.consume()
                        val dx = d.x / wPx
                        val dy = d.y / hPx
                        val n = rect.copyOf()
                        val min = 0.1f
                        when (mode) {
                            0 -> { n[0] += dx; n[1] += dy }
                            1 -> { n[2] += dx; n[1] += dy }
                            2 -> { n[2] += dx; n[3] += dy }
                            3 -> { n[0] += dx; n[3] += dy }
                            4 -> n[0] += dx
                            5 -> n[1] += dy
                            6 -> n[2] += dx
                            7 -> n[3] += dy
                            8 -> {
                                val mx = dx.coerceIn(-n[0], 1f - n[2])
                                val my = dy.coerceIn(-n[1], 1f - n[3])
                                n[0] += mx; n[2] += mx; n[1] += my; n[3] += my
                            }
                            else -> return@detectDragGestures
                        }
                        n[0] = n[0].coerceIn(0f, n[2] - min); n[1] = n[1].coerceIn(0f, n[3] - min)
                        n[2] = n[2].coerceIn(n[0] + min, 1f); n[3] = n[3].coerceIn(n[1] + min, 1f)
                        rect = n
                    }
                },
            ) {
                Box(Modifier.padding(margin.dp).fillMaxSize().background(Color.White)) {
                    bitmap?.let { Image(it, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                }
                val ring = c.toolAccent
                val fill = c.menu
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val l = mPx + rect[0] * wPx
                    val t = mPx + rect[1] * hPx
                    val r = mPx + rect[2] * wPx
                    val b = mPx + rect[3] * hPx
                    val dim = Color.Black.copy(alpha = 0.45f)
                    drawRect(dim, Offset(mPx, mPx), Size(wPx, t - mPx))
                    drawRect(dim, Offset(mPx, b), Size(wPx, mPx + hPx - b))
                    drawRect(dim, Offset(mPx, t), Size(l - mPx, b - t))
                    drawRect(dim, Offset(r, t), Size(mPx + wPx - r, b - t))
                    val line = 2.dp.toPx()
                    drawRect(ring, Offset(l, t), Size(r - l, b - t), style = Stroke(line))
                    // corners: 18 dp rings; edges: 44 x 18 dp pills along the edge
                    val corner = 9.dp.toPx()
                    for ((hx, hy) in listOf(l to t, r to t, r to b, l to b)) {
                        drawCircle(fill, corner, Offset(hx, hy))
                        drawCircle(ring, corner - line / 2, Offset(hx, hy), style = Stroke(line))
                    }
                    val long = 44.dp.toPx()
                    val short = 18.dp.toPx()
                    for ((hx, hy, across) in listOf(Triple((l + r) / 2, t, true), Triple((l + r) / 2, b, true), Triple(l, (t + b) / 2, false), Triple(r, (t + b) / 2, false))) {
                        val w = if (across) long else short
                        val h = if (across) short else long
                        val tl = Offset(hx - w / 2, hy - h / 2)
                        val cr = androidx.compose.ui.geometry.CornerRadius(short / 2)
                        drawRoundRect(fill, tl, Size(w, h), cr)
                        drawRoundRect(ring, Offset(tl.x + line / 2, tl.y + line / 2), Size(w - line, h - line), cr, style = Stroke(line))
                    }
                }
            }
        }
        // the page in a grey pill (with arrows to step through the pages when cropping them all)
        // the page in a glass pill, glass arrows either side when cropping them all
        val ink = com.chethan616.clearpdf.ui.theme.clear
        Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            if (allPages) com.chethan616.clearpdf.ui.glass.GlassCircle({ page = (page - 1).coerceAtLeast(0) }, label = stringResource(R.string.tools_previous_page)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.ChevronLeft, null, Modifier.size(20.dp).alpha(if (page > 0) 1f else 0.35f))
            }
            Box(
                Modifier.widthIn(min = 110.dp).height(40.dp).glassBar().padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.tools_page_number, page + 1), style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = ink.ink) }
            if (allPages) com.chethan616.clearpdf.ui.glass.GlassCircle({ page = (page + 1).coerceAtMost(doc.pageCount - 1) }, label = stringResource(R.string.tools_next_page)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.ChevronRight, null, Modifier.size(20.dp).alpha(if (page < doc.pageCount - 1) 1f else 0.35f))
            }
        }
        com.chethan616.clearpdf.ui.glass.WashPanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(start = 18.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tools_all_pages), style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = ink.ink, modifier = Modifier.weight(1f))
                com.chethan616.clearpdf.ui.glass.GlassToggle(allPages, { allPages = it })
                Spacer(Modifier.width(10.dp))
                com.chethan616.clearpdf.ui.glass.GlassPill(onClick = { if (changed) rect = floatArrayOf(0f, 0f, 1f, 1f) }, height = 40.dp, horizontalPadding = 14.dp) {
                    Text(stringResource(R.string.tools_reset), style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = if (changed) ink.ink else ink.inkFaint)
                }
            }
        }
    }
}


/**
 * A scanned page: the photo's file, its thumbnail (as taken) and the part of it kept: [corners]
 * as shares of the photo's width and height (x1, y1 … x4, y4 from the top left, clockwise), the
 * whole photo when No crop was chosen or no edges were found. [found] is what Auto-detect found.
 */
data class ScanPage(val file: java.io.File, val thumb: ImageBitmap, val corners: FloatArray = WHOLE, val found: FloatArray? = null) {
    companion object {
        val WHOLE = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    }
}

/** The looks a scan can have, and how the engine's camera clean-up gives each. */
enum class ScanFilter(@StringRes val label: Int, val mode: String, val level: Int) {
    Original(R.string.tools_filter_original, "original", 0),
    AutoColor(R.string.tools_filter_auto_colour, "colour", 50),
    Grayscale(R.string.tools_filter_grayscale, "greyscale", 40),
    Whiteboard(R.string.tools_filter_whiteboard, "colour", 85),
    LightText(R.string.tools_filter_light_text, "bw", 60),
}

/** Scan: pages taken with the camera, reviewed (name, retake, crop, delete, look), then saved as a PDF. */
@Composable
fun ScanScreen(vm: AppViewModel) {
    val c = pdfColors
    val pages = vm.scanPages
    var filter by remember { mutableStateOf(ScanFilter.AutoColor) }
    var current by remember { mutableStateOf(0) }
    var cropping by remember { mutableStateOf<java.io.File?>(null) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (pages.isEmpty()) vm.takeScanPhoto() }
    // a page just taken is the one shown
    var seen by remember { mutableStateOf(pages.size) }
    LaunchedEffect(pages.size) {
        if (pages.size > seen) current = pages.lastIndex
        seen = pages.size
    }
    val croppingAt = pages.indexOfFirst { it.file == cropping }
    if (croppingAt >= 0) {
        ScanCrop(pages[croppingAt], onDone = { pages[croppingAt] = pages[croppingAt].copy(corners = it); cropping = null }, onCancel = { cropping = null })
        return
    }
    val addPageLabel = stringResource(R.string.tools_add_page)
    ToolScaffold(
        if (pages.isEmpty()) stringResource(R.string.common_tool_scan) else stringResource(R.string.tools_page_of, current.coerceAtMost(pages.lastIndex) + 1, pages.size),
        { vm.discardScan() },
        stringResource(R.string.tools_save_pdf), pages.isNotEmpty(), { vm.saveScan(filter) },
    ) {
        // the new file's name, changeable before it's saved (not when the pages go into an open document)
        if (vm.insertScanAt == null && vm.scanName.isNotBlank()) {
            val nameLabel = stringResource(R.string.tools_scan_name_description, vm.scanName)
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().then(com.chethan616.clearpdf.ui.glass.washPanel())
                    .clickable { naming = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp).semantics { contentDescription = nameLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.FileText, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(vm.scanName, style = com.chethan616.clearpdf.ui.theme.ClearType.BodyStrong, color = com.chethan616.clearpdf.ui.theme.clear.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Pencil, null, Modifier.size(18.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(com.chethan616.clearpdf.ui.glass.washPanel()), contentAlignment = Alignment.Center) {
            val page = pages.getOrNull(current.coerceAtMost(pages.lastIndex))
            if (page != null) {
                ScanPhoto(page.thumb, page.corners, Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.tools_scan_empty), color = c.textSecondary)
            }
        }
        val ink = com.chethan616.clearpdf.ui.theme.clear.ink
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { i, p ->
                Image(
                    p.thumb, stringResource(R.string.tools_page_number, i + 1),
                    modifier = Modifier.size(56.dp, 72.dp).clip(com.chethan616.clearpdf.ui.glass.RoundedRectangle(12.dp))
                        .border(2.5.dp, if (i == current) com.chethan616.clearpdf.ui.theme.Candy.Sky else Color.Transparent, com.chethan616.clearpdf.ui.glass.RoundedRectangle(12.dp))
                        .clickable { current = i },
                    contentScale = ContentScale.Crop,
                )
            }
            com.chethan616.clearpdf.ui.glass.GlassCircle(onClick = { vm.takeScanPhoto() }, size = 48.dp, label = addPageLabel) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Plus, null, Modifier.size(24.dp))
            }
        }
        // the looks, as the desk's filter chips
        Row(
            Modifier.fillMaxWidth().chipRowFade().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScanFilter.entries.forEach { f -> com.chethan616.clearpdf.ui.home.DeskChip(stringResource(f.label), f == filter) { filter = f } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            GlassTextButton(onClick = { vm.retakeScan(current) }, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Camera, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tools_retake), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            GlassTextButton(onClick = { cropping = pages.getOrNull(current.coerceAtMost(pages.lastIndex))?.file }, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Crop, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tools_crop), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            GlassTextButton(onClick = { if (pages.isNotEmpty()) { pages.removeAt(current.coerceAtMost(pages.lastIndex)); current = (current - 1).coerceAtLeast(0) } }, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Trash2, null, Modifier.size(18.dp), tint = com.chethan616.clearpdf.ui.theme.Candy.Tomato)
                Text(stringResource(R.string.common_delete), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (naming) {
        var name by remember { mutableStateOf(vm.scanName) }
        PdfDialog(
            onDismiss = { naming = false }, title = stringResource(R.string.common_file_name),
            confirm = stringResource(R.string.common_ok), confirmEnabled = name.isNotBlank(), onConfirm = { vm.scanName = name.trim(); naming = false },
        ) {
            PdfField(name, { name = it }, stringResource(R.string.common_file_name))
        }
    }
}

/**
 * Crop one scanned page: drag the corners onto the document's, or take what Auto-detect found,
 * or the whole photo (No crop).
 */
@Composable
private fun ScanCrop(page: ScanPage, onDone: (FloatArray) -> Unit, onCancel: () -> Unit) {
    val c = pdfColors
    var corners by remember(page.file) { mutableStateOf(page.corners) }
    ToolScaffold(stringResource(R.string.tools_crop), onCancel, stringResource(R.string.common_done), true, { onDone(corners) }) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(com.chethan616.clearpdf.ui.glass.washPanel())) {
            ScanPhoto(page.thumb, corners, Modifier.fillMaxSize(), onMove = { corners = it })
        }
        val ink = com.chethan616.clearpdf.ui.theme.clear.ink
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            GlassTextButton(onClick = { page.found?.let { corners = it } }, enabled = page.found != null) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Sparkles, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tools_auto_detect), color = ink)
            }
            GlassTextButton(onClick = { corners = ScanPage.WHOLE }) {
                com.chethan616.clearpdf.ui.glass.GlassIcon(LucideIcons.Maximize, null, Modifier.size(18.dp))
                Text(stringResource(R.string.tools_no_crop), color = ink)
            }
        }
    }
}

/** Whether [q] (x1, y1 … x4, y4) is a convex quadrilateral, its corners going round one way. */
private fun convexQuad(q: FloatArray): Boolean {
    var sign = 0
    for (i in 0 until 4) {
        val (ax, ay) = q[2 * i] to q[2 * i + 1]
        val (bx, by) = q[2 * ((i + 1) % 4)] to q[2 * ((i + 1) % 4) + 1]
        val (cx, cy) = q[2 * ((i + 2) % 4)] to q[2 * ((i + 2) % 4) + 1]
        val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
        val s = if (cross > 1e-6f) 1 else if (cross < -1e-6f) -1 else return false
        if (sign != 0 && s != sign) return false
        sign = s
    }
    return true
}

/**
 * A scanned page's photo, fitted, with the part kept outlined and the rest dimmed: [corners] as
 * shares of the photo. With [onMove], each corner has a handle to drag (the shape stays a convex
 * four-sided one, as straightening needs).
 */
@Composable
private fun ScanPhoto(thumb: ImageBitmap, corners: FloatArray, modifier: Modifier = Modifier, onMove: ((FloatArray) -> Unit)? = null) {
    val c = pdfColors
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        // room around the photo for the handles
        val pad = with(density) { (if (onMove != null) 24.dp else 16.dp).toPx() }
        val k = minOf((constraints.maxWidth - 2 * pad) / thumb.width, (constraints.maxHeight - 2 * pad) / thumb.height).coerceAtLeast(0.01f)
        val (iw, ih) = thumb.width * k to thumb.height * k
        val origin = Offset((constraints.maxWidth - iw) / 2f, (constraints.maxHeight - ih) / 2f)
        val latest = androidx.compose.runtime.rememberUpdatedState(corners)
        val point = { q: FloatArray, i: Int -> Offset(origin.x + q[2 * i] * iw, origin.y + q[2 * i + 1] * ih) }
        val drag = if (onMove == null) Modifier else Modifier.pointerInput(thumb, iw, ih) {
            var held = -1
            detectDragGestures(
                onDragStart = { p ->
                    val q = latest.value
                    held = (0..3).minByOrNull { (point(q, it) - p).getDistance() }?.takeIf { (point(q, it) - p).getDistance() < 48.dp.toPx() } ?: -1
                },
                onDragEnd = { held = -1 },
                onDragCancel = { held = -1 },
            ) { change, d ->
                if (held < 0) return@detectDragGestures
                change.consume()
                val q = latest.value.copyOf()
                q[2 * held] = (q[2 * held] + d.x / iw).coerceIn(0f, 1f)
                q[2 * held + 1] = (q[2 * held + 1] + d.y / ih).coerceIn(0f, 1f)
                if (convexQuad(q)) onMove(q)
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().then(drag)) {
            drawImage(thumb, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(iw.roundToInt().coerceAtLeast(1), ih.roundToInt().coerceAtLeast(1)))
            val quad = androidx.compose.ui.graphics.Path().apply {
                (0..3).forEach { i -> point(corners, i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                close()
            }
            clipPath(quad, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.5f), origin, Size(iw, ih))
            }
            drawPath(quad, c.accent, style = Stroke(2.dp.toPx()))
            if (onMove != null) {
                for (i in 0..3) {
                    drawCircle(Color.White, 11.dp.toPx(), point(corners, i))
                    drawCircle(c.accent, 11.dp.toPx(), point(corners, i), style = Stroke(2.5.dp.toPx()))
                }
            }
        }
    }
}

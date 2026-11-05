package com.chethan616.clearpdf.ui.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.notes.CheckItem
import com.chethan616.clearpdf.notes.Note
import com.chethan616.clearpdf.notes.NoteKind
import com.chethan616.clearpdf.notes.checkItem
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.GlassMenu
import com.chethan616.clearpdf.ui.glass.GlassMenuItem
import com.chethan616.clearpdf.ui.glass.GlassMotion
import com.chethan616.clearpdf.ui.glass.glass
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.home.NoteImage
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.ClearType
import com.chethan616.clearpdf.ui.theme.clear
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.text.DateFormat
import java.util.Date

/**
 * A note open for writing, as the mock's editor: round glass Back and More, the title big and
 * bold, the words under it (light Markdown: `**bold**` shows bold), the list's items with round
 * ticks, its pictures, and a dark glass toolbar riding above the keyboard.
 */
@Composable
fun NoteEditor(vm: AppViewModel, id: String) {
    val note = vm.notes.get(id) ?: run {
        LaunchedEffect(id) { vm.openNote = null }
        return
    }
    BackHandler { vm.closeNote() }
    val c = clear
    var title by remember(id) { mutableStateOf(TextFieldValue(note.title)) }
    var body by remember(id) { mutableStateOf(TextFieldValue(note.body)) }
    var items by remember(id) { mutableStateOf(note.items) }
    var focusItem by remember { mutableStateOf<String?>(null) }
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val isList = note.kind == NoteKind.List

    fun commit() {
        val n = vm.notes.get(id) ?: return
        if (n.title != title.text || n.body != body.text || n.items != items) vm.notes.put(n.copy(title = title.text, body = body.text, items = items))
    }
    LaunchedEffect(title.text, body.text, items) { commit() }
    LaunchedEffect(id) { if (note.isEmpty) runCatching { titleFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 140.dp),
        ) {
            item(key = "top") {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassCircle(onClick = { vm.closeNote() }, size = 56.dp, surface = c.capsule, label = stringResource(R.string.common_back)) {
                            GlassIcon(LucideIcons.ChevronLeft, null, Modifier.size(24.dp), tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        GlassCircle(
                            onClick = { menu = true }, size = 56.dp, surface = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }.border(1.dp, c.rim, CircleShape),
                            label = stringResource(R.string.common_more_options),
                        ) { GlassIcon(LucideIcons.Ellipsis, null, Modifier.size(24.dp), tint = c.hero) }
                    }
                }
            }
            item(key = "title") {
                BasicTextField(
                    title, { title = it },
                    textStyle = ClearType.Headline.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = c.ink),
                    cursorBrush = SolidColor(Candy.Sky),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        if (isList) {
                            val first = items.firstOrNull() ?: checkItem("").also { items = items + it }
                            focusItem = first.id
                        } else {
                            runCatching { bodyFocus.requestFocus() }
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(top = 34.dp).focusRequester(titleFocus),
                    decorationBox = { inner ->
                        Box {
                            if (title.text.isEmpty()) Text(stringResource(if (isList) R.string.notes_list_title_hint else R.string.notes_title_hint), style = ClearType.Headline.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold), color = c.ink.copy(alpha = 0.32f))
                            inner()
                        }
                    },
                )
            }
            item(key = "date") {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updated)),
                    style = ClearType.Caption, color = c.ink.copy(alpha = 0.5f), modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                )
            }
            if (isList) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                    ItemRow(
                        item, focus = focusItem == item.id,
                        onChange = { t -> items = items.map { if (it.id == item.id) it.copy(text = t) else it } },
                        onToggle = { items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it } },
                        onNext = {
                            val n = checkItem("")
                            items = items.toMutableList().apply { add(i + 1, n) }
                            focusItem = n.id
                        },
                        onRemove = {
                            if (items.size > 1) {
                                val prev = items.getOrNull(i - 1)
                                items = items.filter { it.id != item.id }
                                focusItem = prev?.id
                            }
                        },
                    )
                }
                item(key = "add") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).pressable({ val n = checkItem(""); items = items + n; focusItem = n.id }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LucideIcons.Plus, null, tint = c.ink.copy(alpha = 0.55f), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.notes_add_item), style = ClearType.Body.copy(fontSize = 17.sp), color = c.ink.copy(alpha = 0.55f))
                    }
                }
            } else {
                item(key = "body") {
                    BasicTextField(
                        body, { body = it },
                        textStyle = ClearType.Body.copy(fontSize = 18.sp, lineHeight = 30.sp, color = c.ink.copy(alpha = 0.86f)),
                        cursorBrush = SolidColor(Candy.Sky),
                        visualTransformation = MarkdownLite(c.ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).focusRequester(bodyFocus),
                        decorationBox = { inner ->
                            Box {
                                if (body.text.isEmpty()) Text(stringResource(R.string.notes_body_hint), style = ClearType.Body.copy(fontSize = 18.sp, lineHeight = 30.sp), color = c.ink.copy(alpha = 0.38f))
                                inner()
                            }
                        },
                    )
                }
            }
            if (note.images.isNotEmpty()) {
                item(key = "images") {
                    LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(note.images, key = { it }) { name ->
                            Box {
                                NoteImage(name, Modifier.size(150.dp, 120.dp).clip(RoundedRectangle(22.dp)))
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                                        .pressable({ vm.notes.get(id)?.let { n -> vm.notes.put(n.copy(images = n.images - name)) } })
                                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(LucideIcons.X, stringResource(R.string.notes_remove_picture), tint = Color.White, modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                }
            }
        }

        // the toolbar, above the keyboard
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 14.dp)
                .glass(Capsule, c.capsule, blur = 10.dp).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isList) {
                ToolButton(LucideIcons.Plus, stringResource(R.string.notes_add_item), primary = true) { val n = checkItem(""); items = items + n; focusItem = n.id }
                ToolButton(LucideIcons.BrushCleaning, stringResource(R.string.notes_clear_done)) { items = items.filter { !it.done }.ifEmpty { listOf(checkItem("")) } }
            } else {
                ToolButton(LucideIcons.Bold, stringResource(R.string.notes_bold), primary = true) { body = toggleBold(body) }
                ToolButton(LucideIcons.Heading, stringResource(R.string.notes_heading)) { body = toggleHeading(body) }
            }
            ToolButton(LucideIcons.Camera, stringResource(R.string.notes_take_photo)) { commit(); vm.takeNotePhoto() }
            ToolButton(LucideIcons.ImagePlus, stringResource(R.string.notes_add_photo)) { commit(); vm.pickNotePhoto() }
        }
    }

    if (menu) {
        GlassMenu(
            anchor, { menu = false },
            listOf(
                GlassMenuItem(stringResource(if (note.pinned) R.string.notes_unpin else R.string.notes_pin), LucideIcons.Pin) { vm.notes.put(note.copy(pinned = !note.pinned)) },
                GlassMenuItem(stringResource(R.string.common_share), LucideIcons.Share2) { commit(); share(vm, vm.notes.get(id) ?: note) },
                GlassMenuItem(stringResource(R.string.common_delete), LucideIcons.Trash2, destructive = true) { vm.openNote = null; vm.notes.delete(id) },
            ),
        )
    }
}

/** A list item being written: its round tick and its words; Enter makes the next, Backspace on nothing removes it. */
@Composable
private fun ItemRow(item: CheckItem, focus: Boolean, onChange: (String) -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onRemove: () -> Unit) {
    val c = clear
    val fr = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { fr.requestFocus() } }
    var value by remember(item.id) { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
    val tick by animateFloatAsState(if (item.done) 1f else 0f, GlassMotion.pop(), label = "tick")
    val ink by animateColorAsState(if (item.done) c.ink.copy(alpha = 0.45f) else c.ink, GlassMotion.settle(), label = "ink")
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).pressable(onToggle, pressedScale = 0.8f).semantics { contentDescription = item.text }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(26.dp).border(2.dp, c.ink.copy(alpha = 0.5f * (1f - tick)), CircleShape))
            Box(Modifier.size(26.dp).graphicsLayer { scaleX = tick; scaleY = tick }.clip(CircleShape).background(Candy.Mint), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value,
            { v ->
                if (v.text.contains('\n')) {
                    onNext()
                } else {
                    value = v
                    onChange(v.text)
                }
            },
            textStyle = ClearType.Body.copy(fontSize = 18.sp, lineHeight = 26.sp, color = ink, textDecoration = if (item.done) TextDecoration.LineThrough else null),
            cursorBrush = SolidColor(Candy.Mint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(fr).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && value.text.isEmpty()) {
                    onRemove()
                    true
                } else false
            },
        )
    }
}

/** A toolbar circle; the first is white, as the mock's. */
@Composable
private fun ToolButton(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Box(
            Modifier.size(56.dp).pressable(onClick, pressedScale = 0.86f).clip(CircleShape).background(Color.White).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color(0xFF1C2433), modifier = Modifier.size(22.dp)) }
    } else {
        GlassCircle(onClick = onClick, size = 52.dp, surface = Color.White.copy(alpha = 0.14f), label = label) {
            GlassIcon(icon, null, Modifier.size(21.dp), tint = Color.White)
        }
    }
}

/** `**word**` drawn bold (its stars faint), a `# ` line drawn large. The text itself is unchanged. */
private class MarkdownLite(private val ink: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            Regex("""\*\*(.+?)\*\*""").findAll(s).forEach { m ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink), m.range.first + 2, m.range.last - 1)
                addStyle(SpanStyle(color = ink.copy(alpha = 0.22f)), m.range.first, m.range.first + 2)
                addStyle(SpanStyle(color = ink.copy(alpha = 0.22f)), m.range.last - 1, m.range.last + 1)
            }
            var at = 0
            s.split('\n').forEach { line ->
                if (line.startsWith("# ")) {
                    addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = ink), at, at + line.length)
                    addStyle(SpanStyle(color = ink.copy(alpha = 0.22f)), at, at + 2)
                }
                at += line.length + 1
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** Wrap the selection in `**` (or unwrap it); with nothing selected, start bold at the cursor. */
private fun toggleBold(v: TextFieldValue): TextFieldValue {
    val s = v.text
    val a = v.selection.min
    val b = v.selection.max
    if (a >= 2 && b + 2 <= s.length && s.substring(a - 2, a) == "**" && s.substring(b, b + 2) == "**") {
        val t = s.removeRange(b, b + 2).removeRange(a - 2, a)
        return TextFieldValue(t, TextRange(a - 2, b - 2))
    }
    val t = s.substring(0, a) + "**" + s.substring(a, b) + "**" + s.substring(b)
    return TextFieldValue(t, TextRange(a + 2, b + 2))
}

/** Make the cursor's line a heading, or a heading plain again. */
private fun toggleHeading(v: TextFieldValue): TextFieldValue {
    val s = v.text
    val start = s.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(0)).let { if (it < 0 || v.selection.min == 0) 0 else it + 1 }
    return if (s.startsWith("# ", start)) {
        TextFieldValue(s.removeRange(start, start + 2), TextRange((v.selection.start - 2).coerceAtLeast(start), (v.selection.end - 2).coerceAtLeast(start)))
    } else {
        TextFieldValue(s.substring(0, start) + "# " + s.substring(start), TextRange(v.selection.start + 2, v.selection.end + 2))
    }
}

/** A note to the share sheet as plain text. */
private fun share(vm: AppViewModel, n: Note) {
    val text = buildString {
        if (n.title.isNotBlank()) appendLine(n.title).appendLine()
        if (n.kind == NoteKind.List) n.items.filter { it.text.isNotBlank() }.forEach { appendLine((if (it.done) "☑ " else "☐ ") + it.text) }
        else append(n.body.replace("**", ""))
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, n.title)
    vm.intents.trySend(Intent.createChooser(send, null))
}


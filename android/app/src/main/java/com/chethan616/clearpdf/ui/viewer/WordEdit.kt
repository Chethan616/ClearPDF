package com.chethan616.clearpdf.ui.viewer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.glass.GlassCircle
import com.chethan616.clearpdf.ui.glass.GlassIcon
import com.chethan616.clearpdf.ui.glass.glassBar
import com.chethan616.clearpdf.ui.glass.pressable
import com.chethan616.clearpdf.ui.theme.Candy
import com.chethan616.clearpdf.ui.theme.clear

/**
 * What the keyboard types into: a field that always holds one sentinel character. Text committed
 * after it goes into the document; the sentinel gone is a backspace. A word still being composed
 * (the keyboard's suggestions) waits until it is committed.
 */
private const val SENTINEL = "​"

/**
 * Word editing over the pages: the selection's rectangles and a blinking caret, drawn where
 * WordCraft's layout puts them, and the keyboard's field.
 */
@Composable
fun WordEditLayer(vm: AppViewModel, doc: OpenDocument) {
    val caret = vm.wordCaret
    val accent = Candy.Sky
    val blink = rememberInfiniteTransition(label = "caret").animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    Canvas(Modifier.fillMaxSize()) {
        caret ?: return@Canvas
        val v = doc.viewer
        val sel = caret.selection
        var i = 0
        while (i + 4 < sel.size) {
            val page = sel[i].toInt()
            val a = v.pageToScreen(page, sel[i + 1], sel[i + 2])
            val b = v.pageToScreen(page, sel[i + 3], sel[i + 4])
            drawRect(accent.copy(alpha = 0.25f), a, Size(b.x - a.x, b.y - a.y))
            i += 5
        }
        if (sel.isEmpty()) {
            val p = caret.page.toInt()
            val top = v.pageToScreen(p, caret.x, caret.top)
            val bottom = v.pageToScreen(p, caret.x, caret.top + caret.height)
            drawLine(accent.copy(alpha = blink.value.coerceIn(0f, 1f)), top, bottom, 2.2f * density)
        }
    }

    // the keyboard's field, out of sight
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BasicTextField(
        value = field,
        onValueChange = { v ->
            if (v.composition != null) {
                field = v
                return@BasicTextField
            }
            val text = v.text
            when {
                !text.startsWith(SENTINEL) -> vm.wordRun(doc, "text.backspace")
                text.length > SENTINEL.length -> {
                    // each line typed is a paragraph of its own
                    val typed = text.substring(SENTINEL.length)
                    typed.split('\n').forEachIndexed { n, part ->
                        if (n > 0) vm.wordRun(doc, "text.newParagraph")
                        if (part.isNotEmpty()) vm.wordRun(doc, "text.insert", mapOf("text" to part))
                    }
                }
            }
            field = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
        },
        modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0f }.focusRequester(focus).onPreviewKeyEvent { e ->
            // a hardware keyboard's Delete and Enter
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Backspace -> { vm.wordRun(doc, "text.backspace"); true }
                Key.Enter -> { vm.wordRun(doc, "text.newParagraph"); true }
                else -> false
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true, imeAction = ImeAction.None),
        keyboardActions = KeyboardActions.Default,
    )
}

/**
 * The formatting dock while a Word document is edited: Done, undo and redo, then WordCraft's
 * Home tab: bold, italic, underline, bullets, numbering, alignment, heading, in one glass capsule.
 */
@Composable
fun WordEditDock(vm: AppViewModel, doc: OpenDocument) {
    val f = vm.wordFormat
    Row(
        Modifier.fillMaxWidth().height(BottomBarHeight).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCircle(onClick = { vm.stopWordEdit() }, size = 60.dp, tint = Candy.Sky, label = stringResource(R.string.common_done)) {
            GlassIcon(LucideIcons.Check, null, Modifier.size(22.dp))
        }
        Row(
            Modifier.weight(1f).fillMaxHeight().glassBar().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EditKey(LucideIcons.Undo2, stringResource(R.string.word_undo), doc.info.undo != null) { vm.wordRun(doc, "edit.undo") }
            EditKey(LucideIcons.Redo2, stringResource(R.string.word_redo), doc.info.redo != null) { vm.wordRun(doc, "edit.redo") }
            EditKey(LucideIcons.Bold, stringResource(R.string.word_bold), on = f.optBoolean("bold")) { vm.wordRun(doc, "format.bold") }
            EditKey(LucideIcons.Italic, stringResource(R.string.word_italic), on = f.optBoolean("italic")) { vm.wordRun(doc, "format.italic") }
            EditKey(LucideIcons.Underline, stringResource(R.string.common_tool_underline), on = f.opt("underline").let { it != null && it != false && it != "none" && it != org.json.JSONObject.NULL }) { vm.wordRun(doc, "format.underline") }
            EditKey(LucideIcons.List, stringResource(R.string.word_bullets)) { vm.wordRun(doc, "para.bullets") }
            EditKey(LucideIcons.ListOrdered, stringResource(R.string.word_numbering)) { vm.wordRun(doc, "para.numbering") }
            EditKey(LucideIcons.AlignLeft, stringResource(R.string.word_align_left)) { vm.wordRun(doc, "para.alignLeft") }
            EditKey(LucideIcons.AlignCenter, stringResource(R.string.word_align_center)) { vm.wordRun(doc, "para.alignCenter") }
            EditKey(LucideIcons.AlignRight, stringResource(R.string.word_align_right)) { vm.wordRun(doc, "para.alignRight") }
            EditKey(LucideIcons.Heading, stringResource(R.string.word_heading)) { vm.wordRun(doc, "para.style", mapOf("style" to "Heading1")) }
            EditKey(LucideIcons.Type, stringResource(R.string.word_body_text)) { vm.wordRun(doc, "para.normal") }
        }
    }
}

/** One glyph of the dock: in the glass's ink, lit by a soft accent while its formatting is on. */
@Composable
private fun EditKey(icon: ImageVector, label: String, enabled: Boolean = true, on: Boolean = false, onClick: () -> Unit) {
    val c = clear
    Box(
        Modifier.size(44.dp).pressable({ if (enabled) onClick() }).then(
            if (on) Modifier.padding(4.dp).glassBar(surface = Candy.Sky.copy(alpha = 0.28f)) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(icon, label, Modifier.size(21.dp).graphicsLayer { alpha = if (enabled) 1f else 0.35f }, tint = if (on) Candy.Sky else c.ink)
    }
}

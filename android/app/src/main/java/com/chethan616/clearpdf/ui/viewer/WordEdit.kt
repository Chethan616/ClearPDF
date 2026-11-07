package com.chethan616.clearpdf.ui.viewer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
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
 * Word editing as a PDF's text is edited: the paragraph tapped opens in a box over the page, at the
 * document's size and weight, outlined in the selection blue; typing happens right there, and the
 * text goes into the document when another paragraph is tapped or Done is.
 */
@Composable
fun WordEditLayer(vm: AppViewModel, doc: OpenDocument) {
    val para = vm.wordPara ?: return
    val v = doc.viewer
    val density = androidx.compose.ui.platform.LocalDensity.current
    val a = v.pageToScreen(para.page.toInt(), para.x0, para.y0)
    val b = v.pageToScreen(para.page.toInt(), para.x1, para.y1)
    val f = vm.wordFormat
    val points = f.optDouble("size", 11.0).toFloat().takeIf { it > 0f } ?: 11f
    val px = points * v.pixelsPerPoint(para.page.toInt())
    val sp = with(density) { (px / fontScale).toSp() }
    val focus = remember(para) { FocusRequester() }
    var value by remember(para) { mutableStateOf(TextFieldValue(vm.wordDraft, TextRange(vm.wordDraft.length))) }
    LaunchedEffect(para) { focus.requestFocus() }
    // the box kept in sight above the keyboard and the dock
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density)
    LaunchedEffect(para, imeBottom) {
        val room = v.viewH - imeBottom - with(density) { (BottomBarHeight + 24.dp).toPx() }
        if (b.y > room) v.setScroll(v.scrollX, v.scrollY + (b.y - room))
    }
    with(density) {
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(a.x.toInt(), a.y.toInt()) }
                .width((b.x - a.x).toDp() + 6.dp).heightIn(min = (b.y - a.y).toDp() + 6.dp)
                .background(Color.White).drawBehind { drawRect(Candy.Sky, size = size, style = Stroke(2.dp.toPx())) }
                .padding(3.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it; vm.wordDraft = it.text },
                textStyle = TextStyle(
                    color = Color.Black, fontSize = sp, lineHeight = sp * 1.2f,
                    fontWeight = if (f.optBoolean("bold")) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (f.optBoolean("italic")) FontStyle.Italic else FontStyle.Normal,
                ),
                cursorBrush = SolidColor(Candy.Sky),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
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
        GlassCircle(onClick = { vm.stopWordEdit() }, size = 48.dp, tint = Candy.Sky, label = stringResource(R.string.common_done)) {
            GlassIcon(LucideIcons.Check, null, Modifier.size(19.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .glassBar(
                    surface = if (clear.dark) Color(0xFF1B1E2E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.90f)
                )
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
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
    val inkColor = if (on) Candy.Sky else if (clear.dark) Color(0xFFF0F4FC) else Color(0xFF1E222D)
    Box(
        Modifier
            .size(40.dp)
            .pressable({ if (enabled) onClick() })
            .then(
                if (on) Modifier.padding(4.dp).glassBar(surface = Candy.Sky.copy(alpha = 0.28f)) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(
            icon,
            label,
            Modifier.size(21.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f },
            tint = inkColor,
        )
    }
}

package com.chethan616.clearpdf.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import com.kyant.backdrop.floatingLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.AppViewModel
import com.chethan616.clearpdf.ui.LucideIcons
import com.chethan616.clearpdf.ui.OpenDocument
import com.chethan616.clearpdf.ui.pdfColors
import kotlin.math.roundToInt

/** A long press on an empty part of a page: the menu of what can start there. */
class PageMenu(val doc: OpenDocument, val at: PagePoint, val screen: Offset, val pageHasText: Boolean)

/**
 * The reference's page menu: a row of the commenting tools that start at the pressed point
 * (comment, draw, text, sign), then a list of page tools. Tapping outside closes it.
 */
@Composable
fun PageMenuView(vm: AppViewModel, menu: PageMenu) {
    val c = pdfColors
    val doc = menu.doc
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val close = { vm.pageMenu = null }
    val annotate = doc.info.allowsAnnotation
    val fill = doc.info.allowsFormFilling || annotate
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } }) {
        val margin = with(density) { 8.dp.toPx() }
        val x = (menu.screen.x - size.width / 2f).coerceIn(margin, (doc.viewer.viewW - size.width - margin).coerceAtLeast(margin))
        // below the finger when it fits, else above it
        val below = menu.screen.y + with(density) { 24.dp.toPx() }
        val y = if (below + size.height < doc.viewer.viewH - doc.viewer.padBottom) below else (menu.screen.y - size.height - with(density) { 24.dp.toPx() }).coerceAtLeast(doc.viewer.padTop)
        // the selection menu's frosted glass and unfold (ViewerOverlays), out of the finger
        val appear = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, com.chethan616.clearpdf.ui.glass.GlassMotion.unfold()) }
        val opensDown = y > menu.screen.y
        com.chethan616.clearpdf.ui.glass.GlassCard(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .onSizeChanged { size = it }
                .width(236.dp)
                .floatingLayer {
                    val p = appear.value
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.4f + 0.6f * p
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (opensDown) 0f else 1f)
                    alpha = (p * 1.6f).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
            shape = com.chethan616.clearpdf.ui.glass.RoundedRectangle(24.dp),
            wash = com.chethan616.clearpdf.ui.theme.clear.frost,
            blur = 16.dp,
        ) {
            Spacer(Modifier.height(6.dp))
            if (doc.isOffice) {
                // a Word document, slides or a sheet: its own tools, not the PDF ones
                if (!doc.isSlides && !doc.isSheets) MenuRow(LucideIcons.FilePenLine, stringResource(R.string.word_edit)) { close(); vm.startWordEdit(doc) }
                MenuRow(LucideIcons.Volume2, stringResource(R.string.common_tool_read_aloud)) { close(); vm.startReadAloud(doc) }
                MenuRow(LucideIcons.FilePdf, stringResource(R.string.office_export_pdf)) { close(); vm.exportOffice(doc, "pdf") }
            } else {
                Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    MenuIcon(LucideIcons.MessageSquarePlus, stringResource(R.string.page_menu_add_comment), annotate) { close(); vm.notePrompt = NotePrompt(doc, menu.at) }
                    MenuIcon(LucideIcons.Pencil, stringResource(R.string.common_tool_draw), annotate) { close(); vm.enterTool(doc, ViewerTool.Draw) }
                    MenuIcon(LucideIcons.Type, stringResource(R.string.page_menu_add_text), annotate) { close(); vm.textPrompt = TextPrompt(doc, menu.at) }
                    if (fill) MenuIcon(LucideIcons.Signature, stringResource(R.string.common_sign), true) { close(); vm.enterTool(doc, ViewerTool.FillSign) }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(com.chethan616.clearpdf.ui.theme.clear.ink.copy(alpha = 0.12f)))
                Spacer(Modifier.height(4.dp))
                if (!menu.pageHasText) MenuRow(LucideIcons.ScanText, stringResource(R.string.common_tool_recognize_text)) { close(); vm.recognizeText(doc) }
                MenuRow(LucideIcons.Crop, stringResource(R.string.common_tool_crop)) { close(); vm.openCrop(doc) }
                MenuRow(LucideIcons.FilePenLine, stringResource(R.string.common_tool_edit_pdf)) { close(); vm.enterTool(doc, ViewerTool.Edit) }
                MenuRow(LucideIcons.ImagePlus, stringResource(R.string.page_menu_add_image)) { close(); vm.addImageAt(doc, menu.at) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

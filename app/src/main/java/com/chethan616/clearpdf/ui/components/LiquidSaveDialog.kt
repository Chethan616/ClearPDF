package com.chethan616.clearpdf.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop

/**
 * Solid modal presentation — used on the tool screens (they sit over the wallpaper, and a
 * Dialog is a separate window that cannot sample the page anyway, so a clean themed card
 * beats a wallpaper-PNG "glass" look).
 */
@Composable
fun LiquidSaveDialog(
    initialFileName: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    onDismiss: () -> Unit,
    onSave: (fileName: String, locationUri: Uri?) -> Unit
) {
    val isLight = !LocalIsDarkMode.current
    // Same adaptive palette the in-window sheet uses, derived from the theme (tool screens
    // sit over the wallpaper, so we can't sample a live page — a solid card matches the theme).
    val fg     = if (isLight) Color(0xFF15171C) else Color.White
    val fgSoft = if (isLight) Color(0xFF15171C).copy(0.62f) else Color.White.copy(0.62f)
    val field  = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.10f)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SaveDocumentBody(
            surface = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isLight) Color(0xFFF5F6F8) else Color(0xFF1B1E25))
                .border(1.dp, if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.12f), RoundedCornerShape(28.dp)),
            initialFileName = initialFileName,
            backdrop = backdrop,
            fg = fg,
            fgSoft = fgSoft,
            field = field,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

/**
 * Real, in-window Liquid-Glass sheet. Rendered inside the screen (NOT a Dialog window) so the
 * glass panel samples the actual content behind it (e.g. the live PDF page) — no PNG, no solid
 * slab. Enters with a scrim + gentle glass "pop".
 */
@Composable
fun LiquidSaveSheet(
    visible: Boolean,
    initialFileName: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette from the viewer (matches the Insert Text dialog exactly).
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onDismiss: () -> Unit,
    onSave: (fileName: String, locationUri: Uri?) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Fade-only (like AnnotationEditorDialog) — glass never re-blurs mid-transition.
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(160)), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center).imePadding()
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SaveDocumentBody(
                    surface = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 440.dp)
                        .liquidGlassPanel(backdrop, uiSensor, surface),
                    initialFileName = initialFileName,
                    backdrop = backdrop,
                    fg = fg,
                    fgSoft = fgSoft,
                    field = field,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

/**
 * Shared save UI — visually a member of the same dialog family as the Insert Text editor
 * ([AnnotationEditorDialog]): same 18dp padding, 14dp spacing, 16sp Bold title, field boxes
 * (heightIn 54 / radius 12 / [field] bg / 12×10 padding), and LiquidButton footer. The
 * presentation (solid card vs glass panel) is passed in via [surface]; colours via [fg]/[fgSoft]/[field].
 */
@Composable
private fun SaveDocumentBody(
    surface: Modifier,
    initialFileName: String,
    backdrop: Backdrop,
    fg: Color,
    fgSoft: Color,
    field: Color,
    onDismiss: () -> Unit,
    onSave: (fileName: String, locationUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    val accent = Color(0xFF1976D2)
    // Soft violet accent for the "choose location" affordance — a contextual, important action.
    val folderAccent = Color(0xFF7C5CFF)

    var fileName by remember { mutableStateOf(initialFileName) }
    var locationUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }
    var locationDisplay by remember { mutableStateOf(SaveLocationManager.getSavePathDisplay(context)) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            locationUri = uri
            locationDisplay = uri.lastPathSegment?.replace("primary:", "") ?: context.getString(R.string.selected_folder)
        }
    }

    Column(surface.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(stringResource(R.string.save_document), style = TextStyle(fg, 16.sp, FontWeight.Bold))

        // File name — same field treatment as the Insert Text dialog's text box.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(stringResource(R.string.file_name), style = TextStyle(fgSoft, 12.sp, FontWeight.Medium))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(field)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (fileName.isEmpty()) BasicText(stringResource(R.string.document_pdf), style = TextStyle(fgSoft, 14.sp))
                BasicTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    textStyle = TextStyle(fg, 14.sp),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Save location — a stronger, violet-tinted contextual action card.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(stringResource(R.string.settings_save_location), style = TextStyle(fgSoft, 12.sp, FontWeight.Medium))
            LiquidButton(onClick = { folderPicker.launch(null) }, backdrop = backdrop, surfaceColor = folderAccent.copy(0.16f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(folderAccent.copy(0.22f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp), folderAccent) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BasicText(stringResource(R.string.change_folder), style = TextStyle(fgSoft, 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(locationDisplay, style = TextStyle(fg, 14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Actions — same footer as the Insert Text dialog (field Cancel + blue Save).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            LiquidButton(onClick = onDismiss, backdrop = backdrop, surfaceColor = field) {
                BasicText(stringResource(R.string.cancel), style = TextStyle(fg, 13.sp, FontWeight.Medium))
            }
            LiquidButton(
                onClick = {
                    val finalName = fileName.ifBlank { "Document" }.let { if (!it.lowercase().endsWith(".pdf")) "$it.pdf" else it }
                    onSave(finalName, locationUri)
                },
                backdrop = backdrop,
                tint = accent
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(15.dp), Color.White)
                    BasicText(stringResource(R.string.save), style = TextStyle(Color.White, 13.sp, FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

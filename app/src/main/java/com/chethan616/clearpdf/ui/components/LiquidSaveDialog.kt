package com.chethan616.clearpdf.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chethan616.clearpdf.data.repository.SaveLocationManager
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.Backdrop

@Composable
fun LiquidSaveDialog(
    initialFileName: String,
    backdrop: Backdrop,
    uiSensor: UISensor,
    onDismiss: () -> Unit,
    onSave: (fileName: String, locationUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFF1976D2)

    var fileName by remember { mutableStateOf(initialFileName) }
    var locationUri by remember { mutableStateOf(SaveLocationManager.getSaveUri(context)) }
    var locationDisplay by remember { mutableStateOf(SaveLocationManager.getSavePathDisplay(context)) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            locationUri = uri
            locationDisplay = uri.lastPathSegment?.replace("primary:", "") ?: context.getString(R.string.selected_folder)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                // Solid modal card — a Dialog is a separate window and can't sample the
                // page, so a themed surface reads far cleaner than the wallpaper PNG.
                .background(if (isLight) Color(0xFFF5F6F8) else Color(0xFF1B1E25))
                .border(1.dp, if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.12f), RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            BasicText(
                stringResource(R.string.save_document),
                style = TextStyle(color = text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            )

            // File Name Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(stringResource(R.string.file_name), style = TextStyle(color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                BasicTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    textStyle = TextStyle(color = text, fontSize = 16.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.08f))
                        .padding(14.dp),
                    decorationBox = { inner ->
                        if (fileName.isEmpty()) {
                            BasicText(stringResource(R.string.document_pdf), style = TextStyle(color = sub.copy(0.5f), fontSize = 16.sp))
                        }
                        inner()
                    }
                )
            }

            // Save Location Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(stringResource(R.string.settings_save_location), style = TextStyle(color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                LiquidButton(
                    onClick = { folderPicker.launch(null) },
                    backdrop = backdrop,
                    surfaceColor = if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(locationDisplay, style = TextStyle(color = text, fontSize = 14.sp), maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.FolderOpen, stringResource(R.string.change_folder), Modifier.size(20.dp), accent)
                    }
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    surfaceColor = Color.Transparent
                ) {
                    BasicText(stringResource(R.string.cancel), style = TextStyle(color = sub, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(Modifier.width(8.dp))
                LiquidButton(
                    onClick = {
                        val finalName = fileName.ifBlank { "Document" }.let { if (!it.lowercase().endsWith(".pdf")) "$it.pdf" else it }
                        onSave(finalName, locationUri)
                    },
                    backdrop = backdrop,
                    tint = accent
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Save, stringResource(R.string.save), Modifier.size(16.dp), Color.White)
                        BasicText(stringResource(R.string.save), style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

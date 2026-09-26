package com.chethan616.clearpdf.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.Backdrop

/**
 * The one "Save changes?" card for every screen that can lose edits (xlsx, PDF / Office viewer,
 * image editor). Layout is the spreadsheet's original: title, one line of body, then
 * Discard (red) · Cancel (clear glass) · Save (accent) liquid pills.
 *
 * [backdrop] must be the screen's LIVE backdrop ([ScreenBackdrop.glass] or a viewer's
 * `contentBackdrop`) so the card refracts what is actually behind it. [onSave] must perform the
 * screen's real save and leave once it succeeds; [onCancel] also handles back / scrim tap.
 */
@Composable
fun UnsavedChangesDialog(
    visible: Boolean,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    backdrop: Backdrop,
    title: String = stringResource(R.string.unsaved_title),
    body: String = stringResource(R.string.unsaved_body),
    saveLabel: String = stringResource(R.string.save),
    accent: Color = LiquidGlassColors.Blue
) {
    val isDark = LocalIsDarkMode.current
    GlassDialog(
        visible = visible,
        onDismiss = onCancel,
        backdrop = backdrop,
        title = title,
        actions = {
            GlassDialogAction(stringResource(R.string.unsaved_discard), onClick = onDiscard, destructive = true)
            GlassDialogAction(stringResource(R.string.cancel), onClick = onCancel)
            GlassDialogAction(saveLabel, onClick = onSave, primary = true, tint = accent)
        }
    ) {
        BasicText(body, style = TextStyle(LiquidGlassColors.secondary(isDark), 15.sp))
    }
}

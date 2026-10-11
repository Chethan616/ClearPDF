package com.chethan616.clearpdf.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDownCircle
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.AccentIconTile
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassErrorCard
import com.chethan616.clearpdf.ui.components.ToolScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.FillFormViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

private val FormAccent = Color(0xFF00695C)

/**
 * The Fill Form tool. Forms are filled on the page now (#68): pick a PDF, and if it has fields it
 * opens in the viewer, where every field is a Liquid Glass control you fill where it's printed —
 * instead of the old list of field names, where it was impossible to tell which field was which.
 */
@Composable
fun FillFormScreen(
    backdrop: LayerBackdrop,
    viewModel: FillFormViewModel,
    onBack: () -> Unit,
    onFillOnPage: (Uri) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDark = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDark)
    val sub = LiquidGlassColors.secondary(isDark)
    val uiSensor = rememberUISensor()
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) { delay(4000); viewModel.clearFeedback() }
    }
    // A form with fields goes straight to the page. Reset first, so coming back here doesn't
    // reopen it.
    LaunchedEffect(state.loaded, state.fields.size, state.sourceUri) {
        val uri = state.sourceUri ?: return@LaunchedEffect
        if (state.loaded && state.fields.isNotEmpty()) {
            viewModel.reset()
            onFillOnPage(uri)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onSelectFile(context, uri) }

    ToolScaffold(
        title = stringResource(R.string.tool_fill_form),
        backdrop = backdrop,
        onBack = onBack
    ) {
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccentIconTile(Icons.Rounded.EditNote, FormAccent, size = 64, iconSize = 34)
            BasicText(stringResource(R.string.fill_form_hero_title), style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
            BasicText(stringResource(R.string.fill_form_hero_desc), style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp))
            LiquidButton(
                onClick = { if (state.sourceUri == null || state.loaded) filePicker.launch(arrayOf("application/pdf")) },
                backdrop = backdrop,
                tint = FormAccent
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.sourceUri != null && !state.loaded) CircularProgressIndicator(Modifier.size(18.dp), Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                    BasicText(stringResource(R.string.fill_form_pick), style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FillFormStep(Icons.Rounded.Keyboard, LiquidGlassColors.Blue, R.string.fill_form_step_type_title, R.string.fill_form_step_type_desc, text, sub)
            FillFormStep(Icons.Rounded.CheckBox, LiquidGlassColors.Indigo, R.string.fill_form_step_tick_title, R.string.fill_form_step_tick_desc, text, sub)
            FillFormStep(Icons.Rounded.ArrowDropDownCircle, LiquidGlassColors.Teal, R.string.fill_form_step_pick_title, R.string.fill_form_step_pick_desc, text, sub)
            FillFormStep(Icons.Rounded.Save, FormAccent, R.string.fill_form_step_save_title, R.string.fill_form_step_save_desc, text, sub)
        }

        if (state.errorMessage != null) {
            LiquidGlassErrorCard(message = state.errorMessage!!, backdrop = backdrop, uiSensor = uiSensor, onDismiss = { viewModel.clearFeedback() })
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FillFormStep(icon: ImageVector, accent: Color, title: Int, desc: Int, text: Color, sub: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AccentIconTile(icon, accent, size = 40, iconSize = 21)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(stringResource(title), style = TextStyle(text, 15.sp, FontWeight.SemiBold))
            BasicText(stringResource(desc), style = TextStyle(sub, 12.5.sp, lineHeight = 17.sp))
        }
    }
}

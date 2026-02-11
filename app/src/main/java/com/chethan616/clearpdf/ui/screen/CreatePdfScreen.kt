package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidGlassCard
import com.chethan616.clearpdf.ui.components.LiquidGlassTopBar
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.chethan616.clearpdf.ui.viewmodel.CreateMode
import com.chethan616.clearpdf.ui.viewmodel.CreatePdfViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun CreatePdfScreen(
    backdrop: LayerBackdrop,
    viewModel: CreatePdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkMode = LocalIsDarkMode.current
    val isLight = !isDarkMode
    val text = if (isLight) Color(0xFF222222) else Color(0xFFF0F0F0)
    val sub = if (isLight) Color(0xFF888888) else Color(0xFFAAAAAA)
    val accent = Color(0xFFE65100)
    val uiSensor = rememberUISensor()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, surfaceColor = Color.White.copy(0.08f)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", Modifier.size(18.dp), text)
            }
            LiquidGlassTopBar(title = "Create PDF", backdrop = backdrop, uiSensor = uiSensor, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(56.dp), accent)
            BasicText("Create New PDF", style = TextStyle(text, 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Choose how you want to create your PDF", style = TextStyle(sub, 14.sp, textAlign = TextAlign.Center))
        }

        CreateModeCard(
            icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
            title = "Blank Document",
            description = "Start with an empty PDF page",
            accent = accent,
            textColor = text,
            subColor = sub,
            backdrop = backdrop,
            onClick = { viewModel.onModeSelected(CreateMode.BLANK) }
        )

        CreateModeCard(
            icon = Icons.Rounded.Image,
            title = "From Images",
            description = "Convert images to a PDF document",
            accent = accent,
            textColor = text,
            subColor = sub,
            backdrop = backdrop,
            onClick = { viewModel.onModeSelected(CreateMode.FROM_IMAGES) }
        )

        CreateModeCard(
            icon = Icons.Rounded.Edit,
            title = "From Text",
            description = "Type or paste text to create a PDF",
            accent = accent,
            textColor = text,
            subColor = sub,
            backdrop = backdrop,
            onClick = { viewModel.onModeSelected(CreateMode.FROM_TEXT) }
        )

        if (!state.resultMessage.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(16.dp)) {
                BasicText(state.resultMessage!!, style = TextStyle(sub, 14.sp))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun CreateModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
    textColor: Color,
    subColor: Color,
    backdrop: LayerBackdrop,
    onClick: () -> Unit
) {
    val uiSensor = rememberUISensor()
    LiquidGlassCard(
        title = title,
        subtitle = description,
        accentColor = accent,
        backdrop = backdrop,
        uiSensor = uiSensor,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null, Modifier.size(26.dp), accent)
    }
}

package com.chethan616.clearpdf.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.GlassScreenHeaderRow
import com.chethan616.clearpdf.ui.components.GlassScreenScaffold
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.pdfcore.security.PdfSecurityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DecryptPdfScreen(
    backdrop: LayerBackdrop,
    onBack: () -> Unit,
    onViewOutput: (android.net.Uri) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF222222)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF777777)
    val accent = Color(0xFF7C4DFF)
    val uiSensor = rememberUISensor()
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var outputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val source = sourceUri ?: return@rememberLauncherForActivityResult
        if (destination == null) return@rememberLauncherForActivityResult
        isBusy = true
        message = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PdfSecurityService.decryptToUri(context, source, destination, password)
                }
                isError = false
                outputUri = destination
                message = context.getString(R.string.decrypt_pdf_success)
            } catch (_: PdfSecurityService.PasswordRequiredException) {
                isError = true
                message = context.getString(R.string.decrypt_pdf_invalid)
            } catch (_: Exception) {
                isError = true
                message = context.getString(R.string.file_preview_unsupported)
            } finally {
                isBusy = false
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        sourceUri = uri
        outputUri = null
        message = null
        password = ""
    }

    GlassScreenScaffold(
        backdrop = backdrop,
        header = { headerBackdrop ->
            GlassScreenHeaderRow(
                title = stringResource(R.string.decrypt_pdf_title),
                backdrop = headerBackdrop,
                onBack = onBack
            )
        }
    ) { contentPadding ->
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().liquidGlassPanel(backdrop, uiSensor).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.LockOpen, null, Modifier.size(52.dp), accent)
            BasicText(stringResource(R.string.decrypt_pdf_title), style = TextStyle(text, 21.sp, FontWeight.SemiBold))
            BasicText(
                stringResource(R.string.decrypt_pdf_subtitle),
                style = TextStyle(sub, 14.sp),
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            LiquidButton(
                onClick = { openLauncher.launch(arrayOf("application/pdf")) },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp), Color.White)
                BasicText(stringResource(R.string.decrypt_pdf_select), style = TextStyle(Color.White, 14.sp, FontWeight.Medium))
            }

            sourceUri?.let { source ->
                BasicText(
                    source.lastPathSegment ?: stringResource(R.string.viewer_title),
                    style = TextStyle(text, 14.sp, FontWeight.Medium),
                    maxLines = 1
                )
                LiquidButton(
                    onClick = { onViewOutput(source) },
                    backdrop = backdrop,
                    surfaceColor = Color.White.copy(0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(17.dp), accent)
                    BasicText(stringResource(R.string.view_pdf), style = TextStyle(text, 14.sp, FontWeight.Medium))
                }
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    textStyle = TextStyle(text, 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (password.isEmpty()) BasicText(stringResource(R.string.decrypt_pdf_password), style = TextStyle(sub, 14.sp))
                        inner()
                    }
                )
                LiquidButton(
                    onClick = { saveLauncher.launch("unlocked.pdf") },
                    backdrop = backdrop,
                    tint = accent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else BasicText(stringResource(R.string.decrypt_pdf_output), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                }
            }

            AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                message?.let { BasicText(it, style = TextStyle(if (isError) Color(0xFFD32F2F) else Color(0xFF2E7D32), 13.sp)) }
            }
            outputUri?.let { result ->
                LiquidButton(
                    onClick = { onViewOutput(result) },
                    backdrop = backdrop,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(17.dp), Color.White)
                    BasicText(stringResource(R.string.view_unlocked_pdf), style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(
            Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 68.dp)
        )
    }
    }
}

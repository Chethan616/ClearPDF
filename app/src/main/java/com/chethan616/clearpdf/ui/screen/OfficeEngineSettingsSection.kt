package com.chethan616.clearpdf.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.office.OfficeEngine
import com.chethan616.clearpdf.office.OfficeEngineManifest
import com.chethan616.clearpdf.office.OfficeEngineState
import com.chethan616.clearpdf.ui.components.DestructiveGlassButton
import com.chethan616.clearpdf.ui.components.GlassProgressBar
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidToggle
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.kyant.backdrop.backdrops.LayerBackdrop

private const val LIBREOFFICE_LICENSES_URL = "https://www.libreoffice.org/about-us/licenses/"

/**
 * Settings section for the optional Office engine (powered by LibreOffice). Hidden entirely on
 * devices the engine does not support (e.g. x86). Delete is confirmed by the caller's GlassDialog
 * ([onRequestDelete]) because that dialog must be the last child of the screen's full-size Box.
 */
@Composable
fun OfficeEngineSettingsSection(
    backdrop: LayerBackdrop,
    isLight: Boolean,
    textColor: Color,
    labelColor: Color,
    subColor: Color,
    onRequestDelete: (sizeBytes: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val installer = remember(context) { OfficeEngine.installer(context) }
    val state by installer.state.collectAsState()
    var useForOffice by remember { mutableStateOf(OfficeEngine.useForOfficeFiles(context)) }

    LaunchedEffect(installer) { installer.refresh() }
    if (state is OfficeEngineState.Unsupported) return

    // The progress notification needs POST_NOTIFICATIONS on 13+; the download proceeds either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        installer.install()
    }
    val startInstall = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val needsPermission = installer.usesNotification && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else installer.install()
    }

    val divider = if (isLight) Color.Black.copy(0.04f) else Color.White.copy(0.06f)
    val neutralSurface = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.10f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(LiquidGlassColors.Blue.copy(0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Article, null, Modifier.size(18.dp), LiquidGlassColors.Blue)
            }
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.office_engine_title), style = TextStyle(textColor, 17.sp, fontWeight = FontWeight.SemiBold))
                BasicText(stringResource(R.string.office_engine_powered_by), style = TextStyle(subColor, 12.sp))
            }
        }

        BasicText(
            stringResource(R.string.office_engine_description),
            style = TextStyle(subColor, 12.sp, lineHeight = 17.sp)
        )

        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(spring(stiffness = 400f)) togetherWith fadeOut(spring(stiffness = 400f)) },
            label = "officeEngineState"
        ) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s) {
                    is OfficeEngineState.NotInstalled -> StatusWithAction(
                        status = stringResource(
                            R.string.office_engine_not_installed,
                            Formatter.formatShortFileSize(context, OfficeEngineManifest.APPROX_DOWNLOAD_BYTES)
                        ),
                        statusColor = labelColor
                    ) {
                        PrimaryPill(stringResource(R.string.office_engine_download), backdrop, onClick = startInstall)
                    }

                    is OfficeEngineState.Downloading -> {
                        val status = when {
                            s.waitingForNetwork -> stringResource(R.string.office_engine_waiting_network)
                            s.total > 0 && s.downloaded > 0 -> stringResource(
                                R.string.office_engine_downloading_progress,
                                Formatter.formatShortFileSize(context, s.downloaded),
                                Formatter.formatShortFileSize(context, s.total)
                            )
                            else -> stringResource(R.string.office_engine_downloading)
                        }
                        StatusWithAction(status = status, statusColor = labelColor) {
                            SecondaryPill(stringResource(R.string.office_engine_cancel), backdrop, neutralSurface, textColor) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                installer.cancel()
                            }
                        }
                        GlassProgressBar(
                            progress = if (s.total > 0 && s.downloaded > 0) s.downloaded.toFloat() / s.total else null,
                            backdrop = backdrop
                        )
                    }

                    is OfficeEngineState.Installing -> {
                        StatusWithAction(stringResource(R.string.office_engine_installing), labelColor) {
                            SecondaryPill(stringResource(R.string.office_engine_cancel), backdrop, neutralSurface, textColor) {
                                installer.cancel()
                            }
                        }
                        GlassProgressBar(progress = null, backdrop = backdrop)
                    }

                    is OfficeEngineState.NeedsConfirmation -> StatusWithAction(
                        stringResource(R.string.office_engine_needs_confirmation), labelColor
                    ) {
                        PrimaryPill(stringResource(R.string.office_engine_approve), backdrop) {
                            context.findActivity()?.let(installer::confirm)
                        }
                    }

                    is OfficeEngineState.Installed -> {
                        val size = Formatter.formatShortFileSize(context, s.sizeBytes)
                        StatusWithAction(
                            status = if (s.updateAvailable) stringResource(R.string.office_engine_update_available, size)
                            else stringResource(R.string.office_engine_installed, size, s.version),
                            statusColor = if (s.updateAvailable) LiquidGlassColors.Orange else LiquidGlassColors.Green
                        ) {
                            if (s.updateAvailable) {
                                PrimaryPill(stringResource(R.string.office_engine_update), backdrop, onClick = startInstall)
                            }
                            DestructiveGlassButton(
                                text = stringResource(R.string.office_engine_delete),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRequestDelete(s.sizeBytes)
                                },
                                backdrop = backdrop,
                                icon = Icons.Rounded.Delete,
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }

                    is OfficeEngineState.Failed -> StatusWithAction(
                        stringResource(R.string.office_engine_failed, s.message), LiquidGlassColors.Red
                    ) {
                        PrimaryPill(stringResource(R.string.office_engine_retry), backdrop, onClick = startInstall)
                    }

                    OfficeEngineState.Unsupported -> Unit
                }
            }
        }

        if (state is OfficeEngineState.Installed) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        useForOffice = !useForOffice
                        OfficeEngine.setUseForOfficeFiles(context, useForOffice)
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(labelColor.copy(0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), labelColor)
                }
                Column(Modifier.weight(1f)) {
                    BasicText(stringResource(R.string.office_engine_use_for_office), style = TextStyle(labelColor, 15.sp, fontWeight = FontWeight.Medium))
                    BasicText(stringResource(R.string.office_engine_use_for_office_sub), style = TextStyle(subColor, 12.sp))
                }
                LiquidToggle(
                    selected = { useForOffice },
                    onSelect = {
                        useForOffice = it
                        OfficeEngine.setUseForOfficeFiles(context, it)
                    },
                    backdrop = backdrop
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { openUrl(context, LIBREOFFICE_LICENSES_URL) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Gavel, null, Modifier.size(16.dp), LiquidGlassColors.Blue)
            BasicText(
                stringResource(R.string.office_engine_licences),
                style = TextStyle(LiquidGlassColors.Blue, 13.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun StatusWithAction(
    status: String,
    statusColor: Color,
    actions: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(
            status,
            style = TextStyle(statusColor, 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
private fun PrimaryPill(text: String, backdrop: LayerBackdrop, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        tint = LiquidGlassColors.Blue,
        modifier = Modifier.heightIn(min = 44.dp)
    ) {
        BasicText(text, style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun SecondaryPill(text: String, backdrop: LayerBackdrop, surface: Color, textColor: Color, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        surfaceColor = surface,
        modifier = Modifier.heightIn(min = 44.dp)
    ) {
        BasicText(text, style = TextStyle(textColor, 14.sp, fontWeight = FontWeight.Medium))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

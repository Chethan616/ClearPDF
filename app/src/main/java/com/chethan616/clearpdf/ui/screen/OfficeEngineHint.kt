package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.viewerChromeGlass
import com.chethan616.clearpdf.ui.components.viewerGlass
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.kyant.backdrop.Backdrop

/**
 * One-time viewer suggestion shown after an Office file was rendered by the built-in renderers:
 * "Improve fidelity with the Office engine". Floating viewer chrome, so it is viewerGlass over
 * the live page backdrop, with white text like the rest of the viewer controls.
 */
@Composable
fun OfficeEngineHintCard(
    backdrop: Backdrop,
    isDark: Boolean,
    onGet: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .viewerGlass(backdrop, viewerChromeGlass(isDark))
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(LiquidGlassColors.Blue.copy(0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), LiquidGlassColors.Teal)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                stringResource(R.string.office_engine_hint),
                style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                stringResource(R.string.office_engine_hint_sub),
                style = TextStyle(Color.White.copy(0.72f), 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LiquidButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onGet()
            },
            backdrop = backdrop,
            tint = LiquidGlassColors.Blue,
            modifier = Modifier.heightIn(min = 44.dp)
        ) {
            BasicText(stringResource(R.string.office_engine_hint_action), style = TextStyle(Color.White, 14.sp, fontWeight = FontWeight.SemiBold))
        }
        LiquidIconButton(onClick = onDismiss, backdrop = backdrop) {
            CloseCrossIcon(Modifier.size(14.dp))
        }
    }
}

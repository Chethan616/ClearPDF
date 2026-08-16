package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

@Composable
internal fun PdfSearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    focusRequester: FocusRequester,
    backdrop: LayerBackdrop,
    uiSensor: UISensor,
    // Adaptive chrome palette (dark ink on light pages, white on dark pages).
    fg: Color,
    fgSoft: Color,
    surface: Color,
    field: Color,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlassPanel(backdrop, uiSensor, surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(field)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), fgSoft)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    BasicText(
                        stringResource(R.string.viewer_find_hint),
                        style = TextStyle(fgSoft, 13.sp)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(fg, 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(fg),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }

        if (matchCount > 0) {
            BasicText(
                "${currentMatchIndex + 1}/$matchCount",
                style = TextStyle(fg, 11.sp, FontWeight.SemiBold)
            )
        } else if (query.isNotBlank()) {
            BasicText(
                stringResource(R.string.viewer_find_no_results),
                style = TextStyle(Color(0xFFE53935), 11.sp, FontWeight.Medium)
            )
        }

        LiquidIconButton(
            onClick = onPrevMatch,
            backdrop = backdrop,
            surfaceColor = field,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.previous), Modifier.size(18.dp), fg)
        }
        LiquidIconButton(
            onClick = onNextMatch,
            backdrop = backdrop,
            surfaceColor = field,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.next), Modifier.size(18.dp), fg)
        }

        LiquidIconButton(
            onClick = onClose,
            backdrop = backdrop,
            surfaceColor = Color(0xFFEF5350).copy(0.22f),
            modifier = Modifier.size(32.dp)
        ) {
            CloseCrossIcon(Modifier.size(12.dp), fg)
        }
    }
}

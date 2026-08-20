package com.chethan616.clearpdf.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.components.CloseCrossIcon
import com.chethan616.clearpdf.ui.components.GlassSearchPill
import com.chethan616.clearpdf.ui.components.LiquidIconButton
import com.chethan616.clearpdf.ui.utils.UISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay

/**
 * The find bar for both viewers. It is the same 36 dp [GlassSearchPill] Home and Tools use — one
 * search shape across the app — with the nav circles moved *outside* the capsule so the pill stays a
 * pure input rather than a slab with controls buried in it.
 *
 * The adaptive chrome palette ([fg]/[fgSoft]/[surface]/[field]) still threads through: on a light
 * page the viewer inverts its chrome, and the find bar has to follow or it goes unreadable.
 */
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
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassSearchPill(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.viewer_find_hint),
            backdrop = backdrop,
            uiSensor = uiSensor,
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            // The bar's own slide-up is the entrance; a spring-in on the pill would stack on it.
            animateIn = false,
            surfaceColor = surface,
            contentColor = fg,
            hintColor = fgSoft,
            // The match count belongs with the query it counts, not out beside the buttons.
            trailing = {
                if (matchCount > 0) {
                    BasicText(
                        "${currentMatchIndex + 1}/$matchCount",
                        style = TextStyle(fgSoft, 11.sp, FontWeight.SemiBold)
                    )
                } else if (query.isNotBlank()) {
                    BasicText(
                        stringResource(R.string.viewer_find_no_results),
                        style = TextStyle(Color(0xFFE53935), 11.sp, FontWeight.Medium)
                    )
                }
            }
        )

        LiquidIconButton(
            onClick = onPrevMatch,
            backdrop = backdrop,
            surfaceColor = field,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.previous), Modifier.size(17.dp), fg)
        }
        LiquidIconButton(
            onClick = onNextMatch,
            backdrop = backdrop,
            surfaceColor = field,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.next), Modifier.size(17.dp), fg)
        }
        LiquidIconButton(
            onClick = onClose,
            backdrop = backdrop,
            surfaceColor = Color(0xFFEF5350).copy(0.22f),
            modifier = Modifier.size(30.dp)
        ) {
            CloseCrossIcon(Modifier.size(11.dp), fg)
        }
    }
}

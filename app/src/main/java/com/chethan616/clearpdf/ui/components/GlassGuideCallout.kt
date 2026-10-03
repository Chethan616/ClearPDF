package com.chethan616.clearpdf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.kyant.backdrop.backdrops.LayerBackdrop

/** Short, anchored first-use guidance that points directly at the control it describes. */
@Composable
fun GlassGuideCallout(
    visible: Boolean,
    title: String,
    message: String,
    backdrop: LayerBackdrop,
    isLastStep: Boolean,
    pointsUp: Boolean = false,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val text = LiquidGlassColors.text(isDark)
    val secondary = LiquidGlassColors.secondary(isDark)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(GlassMotion.fade()) + scaleIn(GlassMotion.pop(), initialScale = 0.94f),
        exit = fadeOut(GlassMotion.fade()) + scaleOut(GlassMotion.settle(), targetScale = 0.97f),
        modifier = modifier
    ) {
        Column(
            Modifier
                .widthIn(min = 220.dp, max = 312.dp)
                .viewerGlass(
                    backdrop = backdrop,
                    color = if (isDark) Color(0xDF171A21) else Color(0xEFFFFFFF),
                    shape = { RoundedCornerShape(22.dp) }
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (pointsUp) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    null,
                    Modifier.size(20.dp),
                    LiquidGlassColors.Blue
                )
                BasicText(title, style = TextStyle(text, 14.sp, FontWeight.SemiBold))
            }
            BasicText(message, style = TextStyle(secondary, 13.sp, lineHeight = 18.sp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(
                    stringResource(R.string.tour_skip),
                    Modifier.clickable { onSkip() }.padding(horizontal = 6.dp, vertical = 10.dp),
                    style = TextStyle(secondary, 12.sp, FontWeight.Medium)
                )
                Spacer(Modifier.weight(1f))
                LiquidButton(onClick = onNext, backdrop = backdrop, tint = LiquidGlassColors.Blue) {
                    BasicText(
                        stringResource(if (isLastStep) R.string.tour_done else R.string.tour_next),
                        style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

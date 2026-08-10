package com.chethan616.clearpdf.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.data.repository.OnboardingManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop

private data class LanguageOption(
    val code: String,
    val badge: String,
    val labelRes: Int
)

private val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("en", "EN", R.string.language_english),
    LanguageOption("pt-BR", "BR", R.string.language_portuguese)
)

private data class FormatOption(
    val labelRes: Int,
    val drawableRes: Int
)

private val FORMAT_OPTIONS = listOf(
    FormatOption(R.string.file_type_pdf, R.drawable.ic_format_pdf),
    FormatOption(R.string.file_type_word, R.drawable.ic_format_word),
    FormatOption(R.string.file_type_excel, R.drawable.ic_format_excel),
    FormatOption(R.string.file_type_powerpoint, R.drawable.ic_format_ppt),
    FormatOption(R.string.file_type_images, R.drawable.ic_format_image),
    FormatOption(R.string.file_type_txt, R.drawable.ic_format_txt)
)

private data class FeatureOption(
    val icon: ImageVector,
    val accent: Color,
    val titleRes: Int,
    val descriptionRes: Int
)

private val FEATURE_OPTIONS = listOf(
    FeatureOption(Icons.Rounded.Draw, Color(0xFF7D5CFF), R.string.feature_annotate_title, R.string.feature_annotate_desc),
    FeatureOption(Icons.Rounded.Search, Color(0xFF4F9BFF), R.string.feature_search_title, R.string.feature_search_desc),
    FeatureOption(Icons.Rounded.Tune, Color(0xFF33C88A), R.string.feature_tools_title, R.string.feature_tools_desc)
)

/** A quiet, editorial onboarding flow for a private document workspace. */
@Composable
fun OnboardingScreen(
    backdrop: LayerBackdrop,
    onComplete: () -> Unit,
    selectedLocale: String = "en",
    onLanguageChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    val uiSensor = rememberUISensor()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = 4
    val accents = listOf(
        Color(0xFF4F7CFF),
        Color(0xFFFF5F6D),
        Color(0xFF7D5CFF),
        Color(0xFF33C88A)
    )
    val accent = accents[page]
    val text = if (isDark) Color(0xFFF4F7FF) else Color(0xFF182033)
    val secondary = if (isDark) Color(0xFFB3BED2) else Color(0xFF62708A)
    val background = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF101728), Color(0xFF080B12)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF5F8FF), Color(0xFFE9EEFA)))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .size(250.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer { alpha = 0.22f }
                .background(accent.copy(alpha = 0.28f), CircleShape)
        )
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = 0.18f }
                .background(LiquidGlassColors.Blue.copy(alpha = 0.28f), CircleShape)
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(21.dp), accent)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BasicText(
                            stringResource(R.string.app_name),
                            style = TextStyle(text, 16.sp, FontWeight.Bold)
                        )
                        BasicText(
                            stringResource(R.string.onboarding_welcome_subtitle),
                            style = TextStyle(secondary.copy(alpha = 0.72f), 10.sp, FontWeight.Medium)
                        )
                    }
                }
                BasicText(
                    stringResource(R.string.onboarding_step, page + 1, pageCount),
                    style = TextStyle(secondary, 12.sp, FontWeight.SemiBold)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pageCount) { index ->
                    val selected = index == page
                    val width by animateFloatAsState(
                        if (selected) 1f else 0.32f,
                        tween(260, easing = FastOutSlowInEasing),
                        label = "onboardingProgress$index"
                    )
                    Box(
                        Modifier
                            .weight(if (selected) width else 0.32f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else secondary.copy(alpha = 0.18f))
                    )
                }
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    if (forward) {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(260)) + scaleIn(initialScale = 0.98f)) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut(tween(170)) + scaleOut(targetScale = 0.98f))
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn(tween(260)) + scaleIn(initialScale = 0.98f)) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut(tween(170)) + scaleOut(targetScale = 0.98f))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboardingPage"
            ) { currentPage ->
                when (currentPage) {
                    0 -> LanguagePage(
                        backdrop = backdrop,
                        uiSensor = uiSensor,
                        isDark = isDark,
                        text = text,
                        secondary = secondary,
                        selectedLocale = selectedLocale,
                        onLanguageSelected = onLanguageChanged
                    )
                    1 -> FormatsPage(backdrop, uiSensor, isDark, text, secondary)
                    2 -> FeaturesPage(backdrop, uiSensor, isDark, text, secondary)
                    else -> ReadyPage(backdrop, uiSensor, isDark, text, secondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Keep the proven Backdrop button as the only primary action.
            LiquidButton(
                onClick = {
                    if (page < pageCount - 1) page++
                    else {
                        OnboardingManager.setOnboardingComplete(context)
                        onComplete()
                    }
                },
                backdrop = backdrop,
                tint = accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    stringResource(
                        if (page == pageCount - 1) R.string.onboarding_cta_get_started
                        else R.string.onboarding_cta_continue
                    ),
                    style = TextStyle(Color.White, 16.sp, FontWeight.SemiBold),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguagePage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean,
    text: Color,
    secondary: Color,
    selectedLocale: String,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingArtworkCard(backdrop, uiSensor, R.drawable.ic_onboarding_language, Color(0xFF4F7CFF), isDark)
        Spacer(Modifier.height(22.dp))
        BasicText(
            stringResource(R.string.onboarding_welcome_title),
            style = TextStyle(text, 29.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.onboarding_welcome_subtitle),
            style = TextStyle(secondary, 15.sp, FontWeight.Medium, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(22.dp))
        BasicText(
            stringResource(R.string.onboarding_language_label),
            style = TextStyle(secondary.copy(alpha = 0.86f), 12.sp, FontWeight.SemiBold)
        )
        Spacer(Modifier.height(9.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SUPPORTED_LANGUAGES.forEach { language ->
                val selected = selectedLocale == language.code
                val rowColor = if (selected) Color(0xFF4F7CFF).copy(alpha = 0.20f) else Color.White.copy(if (isDark) 0.06f else 0.48f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(rowColor)
                        .border(
                            1.dp,
                            if (selected) Color(0xFF4F7CFF) else secondary.copy(alpha = 0.16f),
                            RoundedCornerShape(16.dp)
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onLanguageSelected(language.code) }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFF4F7CFF).copy(alpha = if (selected) 0.28f else 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(language.badge, style = TextStyle(Color(0xFF4F7CFF), 12.sp, FontWeight.Bold))
                    }
                    BasicText(
                        stringResource(language.labelRes),
                        style = TextStyle(text, 15.sp, if (selected) FontWeight.Bold else FontWeight.Medium),
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .size(23.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F7CFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatsPage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean,
    text: Color,
    secondary: Color
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingArtworkCard(backdrop, uiSensor, R.drawable.ic_onboarding_formats, Color(0xFFFF5F6D), isDark)
        Spacer(Modifier.height(22.dp))
        BasicText(
            stringResource(R.string.onboarding_files_title),
            style = TextStyle(text, 29.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.onboarding_files_subtitle),
            style = TextStyle(secondary, 15.sp, FontWeight.Medium, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            FORMAT_OPTIONS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { option ->
                        FormatChip(option, isDark, text, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    option: FormatOption,
    isDark: Boolean,
    text: Color,
    modifier: Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.62f))
            .border(1.dp, if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.07f), RoundedCornerShape(15.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painterResource(option.drawableRes),
            contentDescription = stringResource(option.labelRes),
            modifier = Modifier.size(35.dp)
        )
        BasicText(
            stringResource(option.labelRes),
            style = TextStyle(text, 11.sp, FontWeight.SemiBold, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun FeaturesPage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean,
    text: Color,
    secondary: Color
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingArtworkCard(backdrop, uiSensor, R.drawable.ic_onboarding_tools, Color(0xFF7D5CFF), isDark)
        Spacer(Modifier.height(22.dp))
        BasicText(
            stringResource(R.string.onboarding_features_title),
            style = TextStyle(text, 29.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.onboarding_features_subtitle),
            style = TextStyle(secondary, 15.sp, FontWeight.Medium, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            FEATURE_OPTIONS.forEach { feature ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(feature.accent.copy(alpha = if (isDark) 0.12f else 0.08f))
                        .border(1.dp, feature.accent.copy(alpha = 0.22f), RoundedCornerShape(17.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(feature.accent.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(feature.icon, null, Modifier.size(21.dp), feature.accent)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(stringResource(feature.titleRes), style = TextStyle(text, 14.sp, FontWeight.Bold))
                        BasicText(stringResource(feature.descriptionRes), style = TextStyle(secondary, 11.sp, FontWeight.Medium))
                    }
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), feature.accent)
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean,
    text: Color,
    secondary: Color
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingArtworkCard(backdrop, uiSensor, R.drawable.ic_onboarding_ready, Color(0xFF33C88A), isDark)
        Spacer(Modifier.height(26.dp))
        BasicText(
            stringResource(R.string.onboarding_ready_title),
            style = TextStyle(text, 30.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.onboarding_ready_subtitle),
            style = TextStyle(secondary, 15.sp, FontWeight.Medium, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF33C88A).copy(alpha = if (isDark) 0.12f else 0.09f))
                .border(1.dp, Color(0xFF33C88A).copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF33C88A).copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), Color(0xFF33C88A))
            }
            BasicText(
                stringResource(R.string.onboarding_privacy_badge),
                style = TextStyle(text, 12.sp, FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun OnboardingArtworkCard(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    drawableRes: Int,
    accent: Color,
    isDark: Boolean
) {
    val transition = rememberInfiniteTransition(label = "onboardingArtwork")
    val drift by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artworkDrift"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(208.dp)
            .liquidGlassPanel(backdrop, uiSensor)
            .clip(RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(165.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (isDark) 0.16f else 0.10f))
        )
        Box(
            Modifier
                .size(116.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isDark) 0.05f else 0.42f))
        )
        Image(
            painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier
                .size(230.dp)
                .graphicsLayer { translationY = drift }
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(15.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

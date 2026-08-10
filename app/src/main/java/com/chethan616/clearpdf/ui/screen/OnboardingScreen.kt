package com.chethan616.clearpdf.ui.screen

import androidx.compose.ui.res.stringResource
import com.chethan616.clearpdf.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.chethan616.clearpdf.data.repository.OnboardingManager
import com.chethan616.clearpdf.ui.components.LiquidButton
import com.chethan616.clearpdf.ui.components.liquidGlassPanel
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.theme.LocalIsDarkMode
import com.chethan616.clearpdf.ui.utils.rememberUISensor
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val flag: String
)

private val SUPPORTED_LANGUAGES = listOf(
    Language("English", "English", "en", "🇬🇧"),
    Language("Portuguese (Brazil)", "Português (Brasil)", "pt-BR", "🇧🇷")
)

private data class FileTypeItem(
    val label: String,
    val resId: Int
)

private val FILE_TYPES = listOf(
    FileTypeItem("PDF", R.drawable.ic_format_pdf),
    FileTypeItem("Word", R.drawable.ic_format_word),
    FileTypeItem("Excel", R.drawable.ic_format_excel),
    FileTypeItem("PowerPoint", R.drawable.ic_format_ppt),
    FileTypeItem("Images", R.drawable.ic_format_image),
    FileTypeItem("TXT", R.drawable.ic_format_txt)
)

private data class FeatureItem(
    val icon: ImageVector,
    val color: Color,
    val titleRes: Int,
    val descRes: Int
)

private val FEATURES = listOf(
    FeatureItem(Icons.Rounded.Draw, Color(0xFF7E57C2), com.chethan616.clearpdf.R.string.feature_annotate_title, com.chethan616.clearpdf.R.string.feature_annotate_desc),
    FeatureItem(Icons.Rounded.Search, Color(0xFF0288D1), com.chethan616.clearpdf.R.string.feature_search_title, com.chethan616.clearpdf.R.string.feature_search_desc),
    FeatureItem(Icons.Rounded.Tune, Color(0xFF00897B), com.chethan616.clearpdf.R.string.feature_tools_title, com.chethan616.clearpdf.R.string.feature_tools_desc)
)

/**
 * Apple HIG-inspired, 4-page onboarding experience.
 * Page 0: Language selector
 * Page 1: File compatibility showcase
 * Page 2: Feature highlights
 * Page 3: "Let's Go" CTA
 */
@Composable
fun OnboardingScreen(
    backdrop: LayerBackdrop,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode = LocalIsDarkMode.current
    val uiSensor = rememberUISensor()
    val scope = rememberCoroutineScope()

    var currentPage by remember { mutableIntStateOf(0) }
    var selectedLocale by remember {
        mutableStateOf(OnboardingManager.getSelectedLocale(context))
    }

    val totalPages = 4

    // Page entry animation
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(currentPage) {
        contentVisible = false
        delay(80)
        contentVisible = true
    }

    val bgBrush = if (isDarkMode) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF0D1117), Color(0xFF0A0A0F)),
            radius = 2000f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFF5F7FF), Color(0xFFECEFF5)),
            radius = 2000f
        )
    }

    val pageColor = when (currentPage) {
        0 -> Color(0xFF6366F1) // Indigo
        1 -> Color(0xFF00B0FF) // Bright Cyan
        2 -> Color(0xFF8B5CF6) // Purple
        else -> Color(0xFF00C853) // Vivid Emerald
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                repeat(totalPages) { page ->
                    val isActive = page == currentPage
                    val dotWidth by animateDpAsState(
                        if (isActive) 28.dp else 8.dp,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "dotWidth$page"
                    )
                    val dotAlpha by animateFloatAsState(
                        if (isActive) 1f else 0.3f,
                        tween(200), label = "dotAlpha$page"
                    )
                    Box(
                        Modifier
                            .width(dotWidth)
                            .height(8.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(pageColor)
                    )
                }
            }

            // Animated page content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 2 } + fadeIn(tween(280))).togetherWith(
                            slideOutHorizontally { -it / 2 } + fadeOut(tween(180))
                        )
                    } else {
                        (slideInHorizontally { -it / 2 } + fadeIn(tween(280))).togetherWith(
                            slideOutHorizontally { it / 2 } + fadeOut(tween(180))
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                label = "pageContent"
            ) { page ->
                when (page) {
                    0 -> LanguagePage(
                        backdrop = backdrop,
                        uiSensor = uiSensor,
                        selectedLocale = selectedLocale,
                        isDark = isDarkMode,
                        onLanguageSelected = { code ->
                            selectedLocale = code
                            OnboardingManager.setSelectedLocale(context, code)
                            com.chethan616.clearpdf.ui.utils.LocaleHelper.applyLocale(context, code, recreate = false)
                        }
                    )
                    1 -> FilesPage(isDark = isDarkMode)
                    2 -> FeaturesPage(backdrop = backdrop, uiSensor = uiSensor, isDark = isDarkMode)
                    3 -> ReadyPage(isDark = isDarkMode)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Navigation button
            LiquidButton(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        OnboardingManager.setOnboardingComplete(context)
                        onComplete()
                    }
                },
                backdrop = backdrop,
                tint = pageColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    if (currentPage == totalPages - 1) stringResource(R.string.onboarding_cta_get_started) else stringResource(R.string.onboarding_cta_continue),
                    style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

// ─── Page 0: Language Selector ────────────────────────────────────────────────

@Composable
private fun LanguagePage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    selectedLocale: String,
    isDark: Boolean,
    onLanguageSelected: (String) -> Unit
) {
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A2E)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666699)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Hero icon
        Box(
            Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(LiquidGlassColors.Blue.copy(alpha = 0.15f))
                .border(1.dp, LiquidGlassColors.Blue.copy(alpha = 0.3f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicText("🌐", style = TextStyle(fontSize = 44.sp))
        }

        Spacer(Modifier.height(28.dp))

        BasicText(
            stringResource(R.string.onboarding_language_title),
            style = TextStyle(text, 26.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.onboarding_language_subtitle),
            style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(36.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassPanel(backdrop, uiSensor)
                .padding(16.dp)
        ) {
            SUPPORTED_LANGUAGES.forEach { lang ->
                val isSelected = selectedLocale == lang.code
                val borderColor = if (isSelected) LiquidGlassColors.Blue else (if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.10f))
                val bgColor = if (isSelected) LiquidGlassColors.Blue.copy(0.18f) else (if (isDark) Color.White.copy(0.06f) else Color.White.copy(0.50f))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { onLanguageSelected(lang.code) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicText(lang.flag, style = TextStyle(fontSize = 28.sp))
                    Column(Modifier.weight(1f)) {
                        BasicText(lang.nativeName, style = TextStyle(text, 16.sp, FontWeight.SemiBold))
                        if (lang.nativeName != lang.name) {
                            BasicText(lang.name, style = TextStyle(sub, 13.sp))
                        }
                    }
                    if (isSelected) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(LiquidGlassColors.Blue),
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

// ─── Page 1: File Types ───────────────────────────────────────────────────────

@Composable
private fun FilesPage(isDark: Boolean) {
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A2E)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666699)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF2E7D32).copy(0.15f))
                .border(1.dp, Color(0xFF2E7D32).copy(0.3f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(52.dp), Color(0xFF4CAF50))
        }

        Spacer(Modifier.height(28.dp))

        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_files_title),
            style = TextStyle(text, 26.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_files_subtitle),
            style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(36.dp))

        // File type grid
        val rows = FILE_TYPES.chunked(3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { item ->
                        FileTypePill(item, isDark, modifier = Modifier.weight(1f))
                    }
                    // Fill empty cells if last row has < 3 items
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FileTypePill(item: FileTypeItem, isDark: Boolean, modifier: Modifier) {
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A2E)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f))
            .border(1.dp, if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f), RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(item.resId),
            contentDescription = item.label,
            modifier = Modifier.size(42.dp)
        )
        BasicText(item.label, style = TextStyle(text, 12.sp, FontWeight.SemiBold, textAlign = TextAlign.Center))
    }
}

// ─── Page 2: Features ─────────────────────────────────────────────────────────

@Composable
private fun FeaturesPage(
    backdrop: LayerBackdrop,
    uiSensor: com.chethan616.clearpdf.ui.utils.UISensor,
    isDark: Boolean
) {
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A2E)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666699)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xFF7E57C2).copy(0.15f))
                .border(1.dp, Color(0xFF7E57C2).copy(0.3f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Draw, null, Modifier.size(52.dp), Color(0xFF7E57C2))
        }

        Spacer(Modifier.height(28.dp))

        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_features_title),
            style = TextStyle(text, 26.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_features_subtitle),
            style = TextStyle(sub, 15.sp, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(32.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FEATURES.forEach { feature ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(feature.color.copy(if (isDark) 0.1f else 0.07f))
                        .border(1.dp, feature.color.copy(0.22f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(feature.color.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(feature.icon, null, Modifier.size(24.dp), feature.color)
                    }
                    Column {
                        BasicText(stringResource(feature.titleRes), style = TextStyle(text, 15.sp, FontWeight.SemiBold))
                        BasicText(stringResource(feature.descRes), style = TextStyle(sub, 12.sp))
                    }
                }
            }
        }
    }
}

// ─── Page 3: Ready CTA ────────────────────────────────────────────────────────

@Composable
private fun ReadyPage(isDark: Boolean) {
    val text = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A2E)
    val sub = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666699)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated check mark
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(300); visible = true }
        val iconScale by animateFloatAsState(
            if (visible) 1f else 0f,
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "iconScale"
        )

        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                .clip(CircleShape)
                .background(Color(0xFF00C853).copy(0.15f))
                .border(2.dp, Color(0xFF00C853).copy(0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(60.dp), Color(0xFF00C853))
        }

        Spacer(Modifier.height(36.dp))

        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_ready_title),
            style = TextStyle(text, 28.sp, FontWeight.Bold, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(12.dp))

        BasicText(
            stringResource(com.chethan616.clearpdf.R.string.onboarding_ready_subtitle),
            style = TextStyle(sub, 16.sp, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(48.dp))

        // Privacy badge
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(LiquidGlassColors.Blue.copy(0.1f))
                .border(1.dp, LiquidGlassColors.Blue.copy(0.25f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicText("🔒", style = TextStyle(fontSize = 14.sp))
            BasicText(stringResource(com.chethan616.clearpdf.R.string.onboarding_privacy_badge),
                style = TextStyle(sub, 12.sp, FontWeight.Medium))
        }
    }
}

package com.chethan616.clearpdf.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.chethan616.clearpdf.data.repository.PdfServiceLocator
import com.chethan616.clearpdf.domain.usecase.CompressPdfUseCase
import com.chethan616.clearpdf.domain.usecase.CreatePdfUseCase
import com.chethan616.clearpdf.domain.usecase.MergePdfUseCase
import com.chethan616.clearpdf.domain.usecase.OpenPdfUseCase
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.chethan616.clearpdf.ui.screen.CompressPdfScreen
import com.chethan616.clearpdf.ui.screen.CreatePdfScreen
import com.chethan616.clearpdf.ui.screen.DecryptPdfScreen
import com.chethan616.clearpdf.ui.screen.EncryptPdfScreen
import com.chethan616.clearpdf.ui.screen.ExtractTextScreen
import com.chethan616.clearpdf.ui.screen.ImagesToPdfScreen
import com.chethan616.clearpdf.ui.screen.HomeScreen
import com.chethan616.clearpdf.ui.screen.MergePdfScreen
import com.chethan616.clearpdf.ui.screen.PageOrganizerScreen
import com.chethan616.clearpdf.ui.screen.PdfToImagesScreen
import com.chethan616.clearpdf.ui.screen.WatermarkPdfScreen
import com.chethan616.clearpdf.ui.screen.ExtractPagesScreen
import com.chethan616.clearpdf.ui.screen.PageNumbersScreen
import com.chethan616.clearpdf.ui.screen.PdfViewerScreen
import com.chethan616.clearpdf.ui.screen.SettingsScreen
import com.chethan616.clearpdf.ui.screen.SplitPdfScreen
import com.chethan616.clearpdf.ui.screen.ScanDocumentScreen
import com.chethan616.clearpdf.ui.screen.ToolsScreen
import com.chethan616.clearpdf.ui.viewmodel.CompressPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.CreatePdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.ExtractTextViewModel
import com.chethan616.clearpdf.ui.viewmodel.ImagesToPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.MergePdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.PageOrganizerViewModel
import com.chethan616.clearpdf.ui.viewmodel.PdfToImagesViewModel
import com.chethan616.clearpdf.ui.viewmodel.WatermarkPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.ExtractPagesViewModel
import com.chethan616.clearpdf.ui.viewmodel.PageNumbersViewModel
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.chethan616.clearpdf.ui.viewmodel.SplitPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.ScanViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

private const val ROUTE_HOME = "home"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SCAN = "scan_document"
private const val ROUTE_VIEWER_BASE = "pdf_viewer"
private const val ROUTE_MERGE = "merge_pdf"
private const val ROUTE_SPLIT = "split_pdf"
private const val ROUTE_COMPRESS = "compress_pdf"
private const val ROUTE_CREATE = "create_pdf"
private const val ROUTE_ORGANIZE = "organize_pdf"
private const val ROUTE_EXTRACT_TEXT = "extract_text"
private const val ROUTE_IMAGES_TO_PDF = "images_to_pdf"
private const val ROUTE_DECRYPT_PDF = "decrypt_pdf"
private const val ROUTE_ENCRYPT_PDF = "encrypt_pdf"
private const val ROUTE_PDF_TO_IMAGES = "pdf_to_images"
private const val ROUTE_WATERMARK = "watermark_pdf"
private const val ROUTE_EXTRACT_PAGES = "extract_pages"
private const val ROUTE_PAGE_NUMBERS = "page_numbers"
private const val ARG_PDF_URI = "uri"
private const val ROUTE_VIEWER = "$ROUTE_VIEWER_BASE?$ARG_PDF_URI={$ARG_PDF_URI}"

private fun parseViewerUriArg(rawArg: String?): Uri? {
    if (rawArg.isNullOrBlank()) return null
    val parsed = Uri.parse(rawArg)
    // Navigation arguments are typically decoded already, but tolerate encoded leftovers.
    return if (parsed.scheme.isNullOrEmpty() && rawArg.contains('%')) {
        Uri.parse(Uri.decode(rawArg))
    } else {
        parsed
    }
}

private fun NavHostController.navigateToPdfViewer(uri: Uri? = null) {
    val route = if (uri == null) {
        ROUTE_VIEWER_BASE
    } else {
        "$ROUTE_VIEWER_BASE?$ARG_PDF_URI=${Uri.encode(uri.toString())}"
    }
    navigate(route) { launchSingleTop = true }
}

private val MAIN_TAB_ROUTES = setOf(ROUTE_HOME, ROUTE_TOOLS, ROUTE_SETTINGS)

private fun routeToTabIdx(route: String?): Int = when (route) {
    ROUTE_HOME -> 0
    ROUTE_TOOLS -> 1
    ROUTE_SETTINGS -> 2
    else -> 0
}

@Composable
fun DocsNavGraph(
    navController: NavHostController,
    backdrop: LayerBackdrop,
    selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    showWallpaper: Boolean = true,
    onShowWallpaperChanged: (Boolean) -> Unit = {},
    selectedLocale: String,
    onLocaleChanged: (String) -> Unit,
    incomingPdfUri: Uri? = null
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        enterTransition = {
            val isMainTabSwitch = initialState.destination.route in MAIN_TAB_ROUTES && targetState.destination.route in MAIN_TAB_ROUTES
            if (isMainTabSwitch) {
                val dist = kotlin.math.abs(routeToTabIdx(targetState.destination.route) - routeToTabIdx(initialState.destination.route))
                val fadeDuration = if (dist >= 2) 520 else 350
                androidx.compose.animation.fadeIn(
                    animationSpec = tween(durationMillis = fadeDuration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            } else {
                androidx.compose.animation.fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = EaseInOut)
                ) + androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    initialOffsetX = { (it * 0.22f).toInt() }
                )
            }
        },
        exitTransition = {
            val isMainTabSwitch = initialState.destination.route in MAIN_TAB_ROUTES && targetState.destination.route in MAIN_TAB_ROUTES
            if (isMainTabSwitch) {
                val dist = kotlin.math.abs(routeToTabIdx(targetState.destination.route) - routeToTabIdx(initialState.destination.route))
                val fadeDuration = if (dist >= 2) 520 else 350
                androidx.compose.animation.fadeOut(
                    animationSpec = tween(durationMillis = fadeDuration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            } else {
                androidx.compose.animation.fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = EaseInOut)
                ) + androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    targetOffsetX = { (-it * 0.10f).toInt() }
                )
            }
        },
        popEnterTransition = {
            val isMainTabSwitch = initialState.destination.route in MAIN_TAB_ROUTES && targetState.destination.route in MAIN_TAB_ROUTES
            if (isMainTabSwitch) {
                val dist = kotlin.math.abs(routeToTabIdx(targetState.destination.route) - routeToTabIdx(initialState.destination.route))
                val fadeDuration = if (dist >= 2) 520 else 350
                androidx.compose.animation.fadeIn(
                    animationSpec = tween(durationMillis = fadeDuration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            } else {
                androidx.compose.animation.fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = EaseInOut)
                ) + androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    initialOffsetX = { (-it * 0.10f).toInt() }
                )
            }
        },
        popExitTransition = {
            val isMainTabSwitch = initialState.destination.route in MAIN_TAB_ROUTES && targetState.destination.route in MAIN_TAB_ROUTES
            if (isMainTabSwitch) {
                val dist = kotlin.math.abs(routeToTabIdx(targetState.destination.route) - routeToTabIdx(initialState.destination.route))
                val fadeDuration = if (dist >= 2) 520 else 350
                androidx.compose.animation.fadeOut(
                    animationSpec = tween(durationMillis = fadeDuration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            } else {
                androidx.compose.animation.fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = EaseInOut)
                ) + androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    targetOffsetX = { (it * 0.22f).toInt() }
                )
            }
        }
    ) {

        // ── Main tabs ──

        composable(ROUTE_HOME) {
            HomeScreen(
                backdrop = backdrop,
                onNavigateToOpenPdf = {
                    navController.navigateToPdfViewer()
                },
                onNavigateToScan = {
                    navController.navigate(ROUTE_SCAN) { launchSingleTop = true }
                },
                onRecentFileSelected = { uri ->
                    navController.navigateToPdfViewer(uri)
                }
            )
        }

        composable(ROUTE_TOOLS) {
            ToolsScreen(
                backdrop = backdrop,
                onNavigateToOpenPdf = { navController.navigateToPdfViewer() },
                onNavigateToMergePdf = { navController.navigate(ROUTE_MERGE) { launchSingleTop = true } },
                onNavigateToSplitPdf = { navController.navigate(ROUTE_SPLIT) { launchSingleTop = true } },
                onNavigateToCompressPdf = { navController.navigate(ROUTE_COMPRESS) { launchSingleTop = true } },
                onNavigateToCreatePdf = { navController.navigate(ROUTE_CREATE) { launchSingleTop = true } },
                onNavigateToOrganizePdf = { navController.navigate(ROUTE_ORGANIZE) { launchSingleTop = true } },
                onNavigateToExtractText = { navController.navigate(ROUTE_EXTRACT_TEXT) { launchSingleTop = true } },
                onNavigateToImagesToPdf = { navController.navigate(ROUTE_IMAGES_TO_PDF) { launchSingleTop = true } },
                onNavigateToDecryptPdf = { navController.navigate(ROUTE_DECRYPT_PDF) { launchSingleTop = true } },
                onNavigateToEncryptPdf = { navController.navigate(ROUTE_ENCRYPT_PDF) { launchSingleTop = true } },
                onNavigateToPdfToImages = { navController.navigate(ROUTE_PDF_TO_IMAGES) { launchSingleTop = true } },
                onNavigateToWatermark = { navController.navigate(ROUTE_WATERMARK) { launchSingleTop = true } },
                onNavigateToExtractPages = { navController.navigate(ROUTE_EXTRACT_PAGES) { launchSingleTop = true } },
                onNavigateToPageNumbers = { navController.navigate(ROUTE_PAGE_NUMBERS) { launchSingleTop = true } }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                backdrop = backdrop,
                isDarkMode = isDarkMode,
                onDarkModeChanged = onDarkModeChanged,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                showWallpaper = showWallpaper,
                onShowWallpaperChanged = onShowWallpaperChanged,
                selectedLocale = selectedLocale,
                onLocaleChanged = onLocaleChanged
            )
        }

        // ── Tool detail screens ──

        composable(ROUTE_SCAN) {
            val vm: ScanViewModel = viewModel()
            ScanDocumentScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_DECRYPT_PDF) {
            DecryptPdfScreen(
                backdrop = backdrop,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_ENCRYPT_PDF) {
            EncryptPdfScreen(
                backdrop = backdrop,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(
            route = ROUTE_VIEWER,
            arguments = listOf(
                navArgument(ARG_PDF_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val vm: PdfViewerViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        PdfViewerViewModel(OpenPdfUseCase(PdfServiceLocator.pdfViewer)) as T
                }
            )
            val routeUri = backStackEntry.arguments
                ?.getString(ARG_PDF_URI)
                ?.let { parseViewerUriArg(it) }
            // Auto-load PDF if opened from external app
            LaunchedEffect(routeUri, incomingPdfUri) {
                val targetUri = routeUri ?: incomingPdfUri
                if (targetUri != null) {
                    vm.openPdf(context, targetUri)
                }
            }
            PdfViewerScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(ROUTE_MERGE) {
            val vm: MergePdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        MergePdfViewModel(MergePdfUseCase(PdfServiceLocator.pdfMerger)) as T
                }
            )
            MergePdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_SPLIT) {
            val vm: SplitPdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        SplitPdfViewModel(PdfServiceLocator.pdfSplitter) as T
                }
            )
            SplitPdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_COMPRESS) {
            val vm: CompressPdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        CompressPdfViewModel(CompressPdfUseCase(PdfServiceLocator.pdfCompressor)) as T
                }
            )
            CompressPdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_CREATE) {
            val vm: CreatePdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        CreatePdfViewModel(CreatePdfUseCase(PdfServiceLocator.pdfCreator)) as T
                }
            )
            CreatePdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_ORGANIZE) {
            val vm: PageOrganizerViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        PageOrganizerViewModel(PdfServiceLocator.pdfEditor) as T
                }
            )
            PageOrganizerScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_EXTRACT_TEXT) {
            val vm: ExtractTextViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        ExtractTextViewModel(PdfServiceLocator.pdfConverter) as T
                }
            )
            ExtractTextScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_IMAGES_TO_PDF) {
            val vm: ImagesToPdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        ImagesToPdfViewModel(PdfServiceLocator.pdfConverter) as T
                }
            )
            ImagesToPdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_PDF_TO_IMAGES) {
            val vm: PdfToImagesViewModel = viewModel()
            PdfToImagesScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_WATERMARK) {
            val vm: WatermarkPdfViewModel = viewModel()
            WatermarkPdfScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_EXTRACT_PAGES) {
            val vm: ExtractPagesViewModel = viewModel()
            ExtractPagesScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }

        composable(ROUTE_PAGE_NUMBERS) {
            val vm: PageNumbersViewModel = viewModel()
            PageNumbersScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onViewOutput = { uri -> navController.navigateToPdfViewer(uri) }
            )
        }
    }
}

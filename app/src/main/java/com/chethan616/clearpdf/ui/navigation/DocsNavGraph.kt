package com.chethan616.clearpdf.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
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
import com.chethan616.clearpdf.ui.screen.HomeScreen
import com.chethan616.clearpdf.ui.screen.MergePdfScreen
import com.chethan616.clearpdf.ui.screen.PdfViewerScreen
import com.chethan616.clearpdf.ui.screen.SettingsScreen
import com.chethan616.clearpdf.ui.screen.SplitPdfScreen
import com.chethan616.clearpdf.ui.screen.ScanDocumentScreen
import com.chethan616.clearpdf.ui.screen.ToolsScreen
import com.chethan616.clearpdf.ui.viewmodel.CompressPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.CreatePdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.MergePdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.PdfViewerViewModel
import com.chethan616.clearpdf.ui.viewmodel.SplitPdfViewModel
import com.chethan616.clearpdf.ui.viewmodel.ScanViewModel
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
fun DocsNavGraph(
    navController: NavHostController,
    backdrop: LayerBackdrop,
    selectedTab: Int,
    onTabChanged: (Int) -> Unit,
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    incomingPdfUri: Uri? = null
) {
    // Disable NavHost transition animations - the LiquidBottomTabs already
    // handles its own spring-based animation. Removing crossfade transitions
    // eliminates GPU work that competes with the backdrop rendering pipeline.
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {

        // ── Main tabs ──

        composable("home") {
            HomeScreen(
                backdrop = backdrop,
                onNavigateToOpenPdf = {
                    navController.navigate("pdf_viewer") { launchSingleTop = true }
                },
                onNavigateToScan = {
                    navController.navigate("scan_document") { launchSingleTop = true }
                }
            )
        }

        composable("tools") {
            ToolsScreen(
                backdrop = backdrop,
                onNavigateToOpenPdf = { navController.navigate("pdf_viewer") { launchSingleTop = true } },
                onNavigateToMergePdf = { navController.navigate("merge_pdf") { launchSingleTop = true } },
                onNavigateToSplitPdf = { navController.navigate("split_pdf") { launchSingleTop = true } },
                onNavigateToCompressPdf = { navController.navigate("compress_pdf") { launchSingleTop = true } },
                onNavigateToCreatePdf = { navController.navigate("create_pdf") { launchSingleTop = true } }
            )
        }

        composable("settings") {
            SettingsScreen(
                backdrop = backdrop,
                isDarkMode = isDarkMode,
                onDarkModeChanged = onDarkModeChanged
            )
        }

        // ── Tool detail screens ──

        composable("scan_document") {
            val vm: ScanViewModel = viewModel()
            ScanDocumentScreen(
                backdrop = backdrop,
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable("pdf_viewer") {
            val context = LocalContext.current
            val vm: PdfViewerViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        PdfViewerViewModel(OpenPdfUseCase(PdfServiceLocator.pdfViewer)) as T
                }
            )
            // Auto-load PDF if opened from external app
            LaunchedEffect(incomingPdfUri) {
                if (incomingPdfUri != null) {
                    vm.openPdf(context, incomingPdfUri)
                }
            }
            PdfViewerScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable("merge_pdf") {
            val vm: MergePdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        MergePdfViewModel(MergePdfUseCase(PdfServiceLocator.pdfMerger)) as T
                }
            )
            MergePdfScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable("split_pdf") {
            val vm: SplitPdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        SplitPdfViewModel(PdfServiceLocator.pdfSplitter) as T
                }
            )
            SplitPdfScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable("compress_pdf") {
            val vm: CompressPdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        CompressPdfViewModel(CompressPdfUseCase(PdfServiceLocator.pdfCompressor)) as T
                }
            )
            CompressPdfScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable("create_pdf") {
            val vm: CreatePdfViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        CreatePdfViewModel(CreatePdfUseCase(PdfServiceLocator.pdfCreator)) as T
                }
            )
            CreatePdfScreen(backdrop = backdrop, viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}

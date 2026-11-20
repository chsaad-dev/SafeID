package com.saaddev.safeid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.saaddev.safeid.ui.screens.CropAuditScreen
import com.saaddev.safeid.ui.screens.ExportShareScreen
import com.saaddev.safeid.ui.screens.HomeScreen
import com.saaddev.safeid.ui.screens.MultiPageStudioScreen
import com.saaddev.safeid.ui.screens.WatermarkStudioScreen
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel

@Composable
fun SafeIdNavGraph(
    navController: NavHostController,
    viewModel: SafeIdViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onSingleDocumentSelected = { uri ->
                    viewModel.setSingleDocument(uri)
                    navController.navigate(Screen.CropAudit.route)
                },
                onFrontBackDocumentsSelected = { frontUri, backUri ->
                    viewModel.setFrontBackDocuments(frontUri, backUri)
                    navController.navigate(Screen.CropAudit.route)
                },
                onMultiPageBundleSelected = { uris ->
                    viewModel.setMultiPageBundle(uris)
                    navController.navigate(Screen.MultiPageStudio.route)
                }
            )
        }

        composable(Screen.MultiPageStudio.route) {
            MultiPageStudioScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExportShare = { navController.navigate(Screen.ExportShare.route) }
            )
        }

        composable(Screen.CropAudit.route) {
            CropAuditScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWatermarkStudio = { navController.navigate(Screen.WatermarkStudio.route) }
            )
        }

        composable(Screen.WatermarkStudio.route) {
            WatermarkStudioScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExportShare = { navController.navigate(Screen.ExportShare.route) }
            )
        }

        composable(Screen.ExportShare.route) {
            ExportShareScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onResetToHome = {
                    viewModel.resetSession()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

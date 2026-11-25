package com.saaddev.safeid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.saaddev.safeid.ui.screens.CropAuditScreen
import com.saaddev.safeid.ui.screens.ExportShareScreen
import com.saaddev.safeid.ui.screens.HomeScreen
import com.saaddev.safeid.ui.screens.MultiPageStudioScreen
import com.saaddev.safeid.ui.screens.PassportGridScreen
import com.saaddev.safeid.ui.screens.WatermarkStudioScreen
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel

@Composable
fun SafeIdNavGraph(
    navController: NavHostController,
    viewModel: SafeIdViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customPresets by viewModel.customPresets.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCustomPresets(context)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                customPresets = customPresets,
                onAddCustomPreset = { label, text ->
                    viewModel.addCustomPreset(context, label, text)
                },
                onDeleteCustomPreset = { id ->
                    viewModel.deleteCustomPreset(context, id)
                },
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
                },
                onPassportPhotoSelected = { uri ->
                    viewModel.setPassportPhoto(uri)
                    navController.navigate(Screen.PassportGrid.route)
                },
                onQuickPresetSelected = { uri, presetLabel, presetText ->
                    viewModel.setSingleDocument(uri, presetLabel, presetText)
                    navController.navigate(Screen.CropAudit.route)
                }
            )
        }

        composable(Screen.PassportGrid.route) {
            PassportGridScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExportShare = { navController.navigate(Screen.ExportShare.route) }
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

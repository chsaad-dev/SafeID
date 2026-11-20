package com.saaddev.safeid.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object CropAudit : Screen("crop_audit")
    object WatermarkStudio : Screen("watermark_studio")
    object MultiPageStudio : Screen("multi_page_studio")
    object PassportGrid : Screen("passport_grid")
    object ExportShare : Screen("export_share")
}


package com.saaddev.safeid.data.model

import android.net.Uri

enum class ExportFormat(val displayName: String, val extension: String, val mimeType: String) {
    JPG("JPG Image", "jpg", "image/jpeg"),
    PDF("PDF Document", "pdf", "application/pdf")
}

enum class TargetSize(val label: String, val maxKb: Int?) {
    ORIGINAL("Original", null),
    KB_200("< 200 KB", 200),
    KB_100("< 100 KB", 100),
    KB_50("< 50 KB", 50)
}

enum class BundleTargetSize(val label: String, val maxKb: Int?) {
    ORIGINAL("Original", null),
    MB_2("< 2 MB", 2000),
    MB_1("< 1 MB", 1000),
    KB_500("< 500 KB", 500)
}

enum class WatermarkDensity(val label: String, val spacingMultiplier: Float) {
    COMPACT("Compact", 0.8f),
    NORMAL("Normal", 1.0f),
    WIDE("Wide", 1.4f)
}

enum class PassportStandard(val label: String, val widthMm: Float, val heightMm: Float) {
    STANDARD_35_45("35×45 mm", 35f, 45f),
    US_2_2("2×2 inch", 50.8f, 50.8f),
    STANDARD_30_40("3×4 cm", 30f, 40f)
}

enum class PaperSheetSize(val label: String, val widthPx: Int, val heightPx: Int) {
    PHOTO_4X6("4×6 Photo Paper", 1800, 1200),
    A4("A4 Full Sheet", 2480, 3508)
}

enum class GridCopies(val label: String, val count: Int) {
    FOUR("4 Photos", 4),
    SIX("6 Photos", 6),
    EIGHT("8 Photos", 8)
}

data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPG,
    val targetSize: TargetSize = TargetSize.ORIGINAL,
    val watermarkText: String = "",
    val watermarkOpacity: Float = 0.25f,
    val watermarkDensity: WatermarkDensity = WatermarkDensity.NORMAL,
    val watermarkColor: Long = 0xFF64748B, // Slate Grey
    val blurSensitiveAreasEnabled: Boolean = false,
    val autoDeleteCacheOnExit: Boolean = true,
    val exportedFileUri: Uri? = null,
    val exportedFileSizeBytes: Long = 0L,
    val exportedFileName: String? = null,
    val exportBaseFileName: String? = null
)


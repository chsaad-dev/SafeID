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

enum class WatermarkDensity(val label: String, val spacingMultiplier: Float) {
    COMPACT("Compact", 0.8f),
    NORMAL("Normal", 1.0f),
    WIDE("Wide", 1.4f)
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
    val exportedFileName: String? = null
)


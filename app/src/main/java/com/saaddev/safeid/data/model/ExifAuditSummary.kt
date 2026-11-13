package com.saaddev.safeid.data.model

data class ExifAuditSummary(
    val hasExifData: Boolean = false,
    val gpsCoordinates: String? = null,
    val deviceModel: String? = null,
    val captureDateTime: String? = null,
    val detectedTags: List<String> = emptyList()
) {
    val isClean: Boolean get() = !hasExifData || detectedTags.isEmpty()
}


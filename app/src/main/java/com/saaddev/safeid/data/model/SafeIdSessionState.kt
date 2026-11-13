package com.saaddev.safeid.data.model

import android.graphics.Bitmap
import android.net.Uri

data class SafeIdSessionState(
    val primaryImageUri: Uri? = null,
    val secondaryImageUri: Uri? = null,
    val isFrontBackMode: Boolean = false,
    val exifSummary: ExifAuditSummary? = null,
    val processedBitmap: Bitmap? = null,
    val exportConfig: ExportConfig = ExportConfig()
) {
    val selectedImageUris: List<Uri>
        get() = listOfNotNull(primaryImageUri, secondaryImageUri)

    val hasImages: Boolean
        get() = primaryImageUri != null
}


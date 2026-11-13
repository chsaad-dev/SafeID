package com.saaddev.safeid.data.model

import android.graphics.Bitmap
import android.net.Uri
import com.saaddev.safeid.engine.PrivacyAuditReport

data class SafeIdSessionState(
    val primaryImageUri: Uri? = null,
    val secondaryImageUri: Uri? = null,
    val isFrontBackMode: Boolean = false,
    val primaryPrivacyReport: PrivacyAuditReport? = null,
    val secondaryPrivacyReport: PrivacyAuditReport? = null,
    val processedBitmap: Bitmap? = null,
    val secondaryProcessedBitmap: Bitmap? = null,
    val exportConfig: ExportConfig = ExportConfig()
) {
    val selectedImageUris: List<Uri>
        get() = listOfNotNull(primaryImageUri, secondaryImageUri)

    val hasImages: Boolean
        get() = primaryImageUri != null
}

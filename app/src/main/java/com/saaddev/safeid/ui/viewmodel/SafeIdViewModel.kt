package com.saaddev.safeid.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.saaddev.safeid.data.model.ExifAuditSummary
import com.saaddev.safeid.data.model.ExportConfig
import com.saaddev.safeid.data.model.SafeIdSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SafeIdViewModel : ViewModel() {

    private val _sessionState = MutableStateFlow(SafeIdSessionState())
    val sessionState: StateFlow<SafeIdSessionState> = _sessionState.asStateFlow()

    fun setSingleDocument(uri: Uri) {
        _sessionState.update { current ->
            current.copy(
                primaryImageUri = uri,
                secondaryImageUri = null,
                isFrontBackMode = false,
                processedBitmap = null
            )
        }
    }

    fun setFrontBackMode(enabled: Boolean) {
        _sessionState.update { current ->
            current.copy(isFrontBackMode = enabled)
        }
    }

    fun setFrontImage(uri: Uri) {
        _sessionState.update { current ->
            current.copy(primaryImageUri = uri)
        }
    }

    fun setBackImage(uri: Uri) {
        _sessionState.update { current ->
            current.copy(secondaryImageUri = uri)
        }
    }

    fun setFrontBackDocuments(frontUri: Uri, backUri: Uri) {
        _sessionState.update { current ->
            current.copy(
                primaryImageUri = frontUri,
                secondaryImageUri = backUri,
                isFrontBackMode = true,
                processedBitmap = null
            )
        }
    }

    fun setExifSummary(summary: ExifAuditSummary) {
        _sessionState.update { current ->
            current.copy(exifSummary = summary)
        }
    }

    fun setProcessedBitmap(bitmap: Bitmap?) {
        _sessionState.update { current ->
            current.copy(processedBitmap = bitmap)
        }
    }

    fun updateExportConfig(transform: (ExportConfig) -> ExportConfig) {
        _sessionState.update { current ->
            current.copy(exportConfig = transform(current.exportConfig))
        }
    }

    fun resetSession() {
        _sessionState.value = SafeIdSessionState()
    }
}


package com.saaddev.safeid.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.saaddev.safeid.data.model.ExportConfig
import com.saaddev.safeid.data.model.SafeIdSessionState
import com.saaddev.safeid.engine.PrivacyAuditReport
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
                primaryPrivacyReport = null,
                secondaryPrivacyReport = null,
                processedBitmap = null,
                secondaryProcessedBitmap = null
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
                primaryPrivacyReport = null,
                secondaryPrivacyReport = null,
                processedBitmap = null,
                secondaryProcessedBitmap = null
            )
        }
    }

    fun setPrimaryPrivacyReport(report: PrivacyAuditReport) {
        _sessionState.update { current ->
            current.copy(primaryPrivacyReport = report)
        }
    }

    fun setSecondaryPrivacyReport(report: PrivacyAuditReport) {
        _sessionState.update { current ->
            current.copy(secondaryPrivacyReport = report)
        }
    }

    fun setProcessedBitmaps(primary: Bitmap?, secondary: Bitmap? = null) {
        _sessionState.update { current ->
            current.copy(
                processedBitmap = primary,
                secondaryProcessedBitmap = secondary
            )
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

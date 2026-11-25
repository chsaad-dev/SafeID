package com.saaddev.safeid.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.saaddev.safeid.data.CustomPresetRepository
import com.saaddev.safeid.data.CustomWatermarkPreset
import com.saaddev.safeid.data.model.ExportConfig
import com.saaddev.safeid.data.model.SafeIdSessionState
import com.saaddev.safeid.engine.PrivacyAuditReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeIdViewModel : ViewModel() {

    private val _sessionState = MutableStateFlow(SafeIdSessionState())
    val sessionState: StateFlow<SafeIdSessionState> = _sessionState.asStateFlow()

    private val _customPresets = MutableStateFlow<List<CustomWatermarkPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomWatermarkPreset>> = _customPresets.asStateFlow()

    fun loadCustomPresets(context: Context) {
        _customPresets.value = CustomPresetRepository.loadPresets(context)
    }

    fun addCustomPreset(context: Context, label: String, templateText: String) {
        val cleanLabel = label.trim()
        val cleanText = templateText.trim()
        if (cleanLabel.isBlank() || cleanText.isBlank()) return
        val updated = _customPresets.value + CustomWatermarkPreset(
            label = cleanLabel,
            templateText = cleanText
        )
        _customPresets.value = updated
        CustomPresetRepository.savePresets(context, updated)
    }

    fun deleteCustomPreset(context: Context, id: String) {
        val updated = _customPresets.value.filterNot { it.id == id }
        _customPresets.value = updated
        CustomPresetRepository.savePresets(context, updated)
    }

    fun setSingleDocument(uri: Uri, quickPreset: String? = null, quickPresetText: String? = null) {
        _sessionState.update { current ->
            current.copy(
                primaryImageUri = uri,
                secondaryImageUri = null,
                isFrontBackMode = false,
                bundleImageUris = emptyList(),
                passportPhotoUri = null,
                quickPreset = quickPreset,
                quickPresetText = quickPresetText,
                primaryPrivacyReport = null,
                secondaryPrivacyReport = null,
                processedBitmap = null,
                secondaryProcessedBitmap = null
            )
        }
    }

    fun setPassportPhoto(uri: Uri) {
        _sessionState.update { current ->
            current.copy(
                passportPhotoUri = uri,
                primaryImageUri = uri,
                secondaryImageUri = null,
                isFrontBackMode = false,
                bundleImageUris = emptyList(),
                quickPreset = null,
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
                bundleImageUris = emptyList(),
                primaryPrivacyReport = null,
                secondaryPrivacyReport = null,
                processedBitmap = null,
                secondaryProcessedBitmap = null
            )
        }
    }

    fun setMultiPageBundle(uris: List<Uri>) {
        _sessionState.update { current ->
            current.copy(
                bundleImageUris = uris,
                primaryImageUri = uris.firstOrNull(),
                secondaryImageUri = null,
                isFrontBackMode = false,
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

    /**
     * Generates a clean, human-readable default base filename (without extension).
     * Example: SafeID_Bank_KYC_21Nov2026 or SafeID_Passport_Grid_21Nov2026.
     */
    fun generateDefaultBaseFileName(
        isPassportGrid: Boolean = false,
        isBundle: Boolean = false,
        preset: String? = null
    ): String {
        val dateStr = SimpleDateFormat("ddMMMyyyy", Locale.US).format(Date())
        return when {
            isPassportGrid -> "SafeID_Passport_Grid_$dateStr"
            isBundle -> {
                if (!preset.isNullOrBlank() && preset != "Custom") {
                    val cleanPreset = preset.replace("/", "_").replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "").trim('_')
                    "SafeID_${cleanPreset}_$dateStr"
                } else {
                    "SafeID_Bundle_$dateStr"
                }
            }
            !preset.isNullOrBlank() && preset != "Custom" -> {
                val cleanPreset = when (preset) {
                    "Bank / KYC", "Bank KYC", "KYC" -> "Bank_KYC"
                    "SIM Verification", "SIM Card", "SIM" -> "SIM_Card"
                    "Job Application", "Job Portal", "JOB" -> "Job_Portal"
                    "Visa / Travel", "VISA" -> "Visa_Travel"
                    "Rental", "RENTAL" -> "Rental"
                    else -> preset.replace("/", "_").replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "").trim('_')
                }
                "SafeID_${cleanPreset}_$dateStr"
            }
            _sessionState.value.isFrontBackMode -> "SafeID_ID_Card_$dateStr"
            else -> "SafeID_Document_$dateStr"
        }
    }

    /**
     * Sanitizes user input automatically by replacing spaces with underscores (or keeping clean underscores)
     * and stripping invalid filesystem characters ([\\\\/:*?"<>|]), falling back to "SafeID_Document" if blank.
     */
    fun sanitizeFileName(input: String): String {
        val cleaned = input.replace(Regex("[\\\\/:*?\"<>|]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
        return if (cleaned.isBlank()) "SafeID_Document" else cleaned
    }

    /**
     * Updates the in-memory exportBaseFileName and synchronized exportedFileName.
     */
    fun updateExportFileName(newName: String) {
        _sessionState.update { current ->
            val sanitized = sanitizeFileName(newName)
            val ext = current.exportConfig.format.extension
            current.copy(
                exportConfig = current.exportConfig.copy(
                    exportBaseFileName = newName,
                    exportedFileName = "$sanitized.$ext"
                )
            )
        }
    }

    /**
     * Renames the cached export file inside cacheDir/safeid_exports/ to match the latest user input.
     * Updates FileProvider URI and session state accordingly.
     */
    fun commitExportFileRename(context: Context, newBaseName: String): File? {
        val sanitized = sanitizeFileName(newBaseName)
        val ext = _sessionState.value.exportConfig.format.extension
        val newFullName = "$sanitized.$ext"
        val currentFullName = _sessionState.value.exportConfig.exportedFileName

        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val currentFile = if (currentFullName != null) File(exportDir, currentFullName) else null
        val targetFile = File(exportDir, newFullName)

        if (currentFile != null && currentFile.exists()) {
            if (currentFile.absolutePath != targetFile.absolutePath) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = currentFile.renameTo(targetFile)
                if (!renamed) {
                    try {
                        currentFile.copyTo(targetFile, overwrite = true)
                        currentFile.delete()
                    } catch (e: Exception) {
                        android.util.Log.e("SafeID", "commitExportFileRename copy fallback failed", e)
                    }
                }
            }
        }

        val finalFile = if (targetFile.exists()) targetFile else currentFile
        if (finalFile != null && finalFile.exists()) {
            val newUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finalFile)
            } catch (_: Exception) {
                finalFile.toUri()
            }
            _sessionState.update { current ->
                current.copy(
                    exportConfig = current.exportConfig.copy(
                        exportBaseFileName = sanitized,
                        exportedFileName = finalFile.name,
                        exportedFileUri = newUri,
                        exportedFileSizeBytes = finalFile.length()
                    )
                )
            }
            return finalFile
        }
        return null
    }

    fun resetSession() {
        _sessionState.value = SafeIdSessionState()
    }
}

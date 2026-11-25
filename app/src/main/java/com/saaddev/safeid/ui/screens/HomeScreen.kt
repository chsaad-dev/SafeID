package com.saaddev.safeid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.saaddev.safeid.data.CustomWatermarkPreset
import com.saaddev.safeid.ui.components.AdBannerComposable
import com.saaddev.safeid.ui.theme.LocalThemeMode
import com.saaddev.safeid.ui.theme.LocalThemeModeChanger
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private enum class SheetMode {
    NONE,
    SINGLE_PICKER,
    FRONT_BACK_GUIDED,
    MULTI_PAGE_PICKER,
    PASSPORT_PICKER
}

private enum class TargetSide {
    FRONT,
    BACK
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    customPresets: List<CustomWatermarkPreset> = emptyList(),
    onAddCustomPreset: (label: String, templateText: String) -> Unit = { _, _ -> },
    onDeleteCustomPreset: (id: String) -> Unit = {},
    onSingleDocumentSelected: (Uri) -> Unit,
    onFrontBackDocumentsSelected: (frontUri: Uri, backUri: Uri) -> Unit,
    onMultiPageBundleSelected: (List<Uri>) -> Unit = {},
    onPassportPhotoSelected: (Uri) -> Unit = {},
    onQuickPresetSelected: (Uri, String, String?) -> Unit = { uri, _, _ -> onSingleDocumentSelected(uri) }
) {
    val context = LocalContext.current
    val colors = SafeIdTheme.colors
    val themeMode = LocalThemeMode.current
    val onThemeModeChanged = LocalThemeModeChanger.current

    var sheetMode by remember { mutableStateOf(SheetMode.NONE) }
    var activeTargetSide by remember { mutableStateOf(TargetSide.FRONT) }
    var pendingQuickPreset by remember { mutableStateOf<String?>(null) }
    var pendingQuickPresetText by remember { mutableStateOf<String?>(null) }
    var showAddPresetDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<CustomWatermarkPreset?>(null) }

    var frontUri by remember { mutableStateOf<Uri?>(null) }
    var backUri by remember { mutableStateOf<Uri?>(null) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Helper: Create temporary camera destination Uri
    fun prepareCameraUri(): Uri {
        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val tempFile = File(exportDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    // Helper: Copy gallery URI stream into internal app cache for persistent read & thumbnail decoding
    fun copyUriToTemp(uri: Uri, name: String): Uri {
        return try {
            val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
            val tempFile = File(exportDir, "${name}_${System.currentTimeMillis()}.jpg")
            val stream = if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path
                if (path != null) FileInputStream(File(path)) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            android.util.Log.e("SafeID", "copyUriToTemp failed", e)
            uri
        }
    }

    // Camera Result Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val capturedUri = pendingCameraUri!!
            when (sheetMode) {
                SheetMode.SINGLE_PICKER -> {
                    sheetMode = SheetMode.NONE
                    val preset = pendingQuickPreset
                    val presetText = pendingQuickPresetText
                    pendingQuickPreset = null
                    pendingQuickPresetText = null
                    if (preset != null) {
                        onQuickPresetSelected(capturedUri, preset, presetText)
                    } else {
                        onSingleDocumentSelected(capturedUri)
                    }
                }
                SheetMode.PASSPORT_PICKER -> {
                    sheetMode = SheetMode.NONE
                    onPassportPhotoSelected(capturedUri)
                }
                SheetMode.MULTI_PAGE_PICKER -> {
                    sheetMode = SheetMode.NONE
                    onMultiPageBundleSelected(listOf(capturedUri))
                }
                SheetMode.FRONT_BACK_GUIDED -> {
                    if (activeTargetSide == TargetSide.FRONT) {
                        frontUri = capturedUri
                        if (backUri == null) {
                            activeTargetSide = TargetSide.BACK
                        }
                    } else {
                        backUri = capturedUri
                    }
                }
                SheetMode.NONE -> {}
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val newUri = prepareCameraUri()
            pendingCameraUri = newUri
            takePictureLauncher.launch(newUri)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Launch camera with permission check
    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val newUri = prepareCameraUri()
            pendingCameraUri = newUri
            takePictureLauncher.launch(newUri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Visual Media Photo Picker (zero storage permissions)
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            when (sheetMode) {
                SheetMode.SINGLE_PICKER -> {
                    sheetMode = SheetMode.NONE
                    val safeUri = copyUriToTemp(uri, "single")
                    val preset = pendingQuickPreset
                    val presetText = pendingQuickPresetText
                    pendingQuickPreset = null
                    pendingQuickPresetText = null
                    if (preset != null) {
                        onQuickPresetSelected(safeUri, preset, presetText)
                    } else {
                        onSingleDocumentSelected(safeUri)
                    }
                }
                SheetMode.PASSPORT_PICKER -> {
                    sheetMode = SheetMode.NONE
                    val safeUri = copyUriToTemp(uri, "passport")
                    onPassportPhotoSelected(safeUri)
                }
                SheetMode.FRONT_BACK_GUIDED -> {
                    val safeUri = copyUriToTemp(uri, if (activeTargetSide == TargetSide.FRONT) "front" else "back")
                    if (activeTargetSide == TargetSide.FRONT) {
                        frontUri = safeUri
                        if (backUri == null) {
                            activeTargetSide = TargetSide.BACK
                        }
                    } else {
                        backUri = safeUri
                    }
                }
                SheetMode.MULTI_PAGE_PICKER -> {
                    sheetMode = SheetMode.NONE
                    val safeUri = copyUriToTemp(uri, "bundle_page_0")
                    onMultiPageBundleSelected(listOf(safeUri))
                }
                SheetMode.NONE -> {}
            }
        }
    }

    fun launchGallery() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Multiple Visual Media Picker for Multi-Page Bundle (up to 10 items)
    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            sheetMode = SheetMode.NONE
            val safeUris = uris.mapIndexed { index, uri ->
                copyUriToTemp(uri, "bundle_page_$index")
            }
            onMultiPageBundleSelected(safeUris)
        }
    }

    Scaffold(
        containerColor = colors.bgPrimary,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: SafeID Title and Quiet Offline Subtitle Row
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "SafeID",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusSafeDot)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Offline • Processed on-device",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Right: Theme Mode Menu Toggle Button
                    Box {
                        var showThemeMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.bgSubtle)
                                .border(1.dp, colors.borderHairline, CircleShape)
                                .clickable { showThemeMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (themeMode) {
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Appearance",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false },
                            modifier = Modifier
                                .background(colors.bgElevated)
                                .border(1.dp, colors.borderHairline, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "System",
                                        color = if (themeMode == ThemeMode.SYSTEM) colors.textPrimary else colors.textSecondary,
                                        fontWeight = if (themeMode == ThemeMode.SYSTEM) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.BrightnessAuto,
                                        contentDescription = null,
                                        tint = if (themeMode == ThemeMode.SYSTEM) colors.textPrimary else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onThemeModeChanged(ThemeMode.SYSTEM)
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Light",
                                        color = if (themeMode == ThemeMode.LIGHT) colors.textPrimary else colors.textSecondary,
                                        fontWeight = if (themeMode == ThemeMode.LIGHT) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = if (themeMode == ThemeMode.LIGHT) colors.textPrimary else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onThemeModeChanged(ThemeMode.LIGHT)
                                    showThemeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Dark",
                                        color = if (themeMode == ThemeMode.DARK) colors.textPrimary else colors.textSecondary,
                                        fontWeight = if (themeMode == ThemeMode.DARK) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = if (themeMode == ThemeMode.DARK) colors.textPrimary else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    onThemeModeChanged(ThemeMode.DARK)
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                AdBannerComposable()
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action Card 1: Single Document
            ActionCard(
                title = "Single Document",
                description = "ID card, passport page, bill, or receipt.",
                icon = Icons.Default.Description,
                onClick = {
                    pendingQuickPreset = null
                    sheetMode = SheetMode.SINGLE_PICKER
                }
            )

            // Action Card 2: Front & Back ID Card (A4 Layout)
            ActionCard(
                title = "Front & Back ID Card",
                description = "Places both sides onto one printable A4 page.",
                icon = Icons.Default.CreditCard,
                onClick = {
                    frontUri = null
                    backUri = null
                    activeTargetSide = TargetSide.FRONT
                    sheetMode = SheetMode.FRONT_BACK_GUIDED
                }
            )

            // Action Card 3: Multi-Page PDF Bundle
            ActionCard(
                title = "Multi-Page PDF Bundle",
                description = "Combine 2 to 10 documents into one watermarked PDF.",
                icon = Icons.Default.Layers,
                onClick = {
                    sheetMode = SheetMode.MULTI_PAGE_PICKER
                }
            )

            // Action Card 4: Passport & ID Photo Print Grid
            ActionCard(
                title = "Passport Photo Print Grid",
                description = "Tile 4, 6, or 8 passport photos onto a printable 4x6 or A4 sheet.",
                icon = Icons.Default.GridView,
                onClick = {
                    sheetMode = SheetMode.PASSPORT_PICKER
                }
            )

            // Quick Start Watermark Section
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "QUICK PURPOSE PRESET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("Bank KYC", "SIM Card", "Job Portal", "Visa / Travel")
                    items(presets) { preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.bgElevated,
                            border = BorderStroke(1.dp, colors.borderHairline),
                            modifier = Modifier.clickable {
                                pendingQuickPreset = preset
                                pendingQuickPresetText = null
                                sheetMode = SheetMode.SINGLE_PICKER
                            }
                        ) {
                            Text(
                                text = preset,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }

                    items(customPresets, key = { it.id }) { custom ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.bgElevated,
                            border = BorderStroke(1.dp, colors.borderHairline),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    pendingQuickPreset = custom.label
                                    pendingQuickPresetText = custom.templateText
                                    sheetMode = SheetMode.SINGLE_PICKER
                                },
                                onLongClick = {
                                    presetToDelete = custom
                                }
                            )
                        ) {
                            Text(
                                text = custom.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.bgSubtle,
                            border = BorderStroke(1.dp, colors.borderHairline),
                            modifier = Modifier.clickable {
                                showAddPresetDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Custom Preset",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Add Custom",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Quiet, Unboxed Footer Hint
            Text(
                text = "Tip: You can also share any photo directly from your Gallery to SafeID.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textTertiary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }

    // Modal Bottom Sheet for Document Source Selection
    if (sheetMode != SheetMode.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { sheetMode = SheetMode.NONE },
            sheetState = sheetState,
            containerColor = colors.bgElevated,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.borderHairline)
                )
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            when (sheetMode) {
                SheetMode.SINGLE_PICKER -> {
                    SinglePickerSheetContent(
                        onCameraClick = { launchCamera() },
                        onGalleryClick = { launchGallery() }
                    )
                }
                SheetMode.MULTI_PAGE_PICKER -> {
                    MultiPagePickerSheetContent(
                        onCameraClick = { launchCamera() },
                        onGalleryClick = {
                            pickMultipleMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
                SheetMode.PASSPORT_PICKER -> {
                    PassportPhotoPickerSheetContent(
                        onCameraClick = { launchCamera() },
                        onGalleryClick = { launchGallery() }
                    )
                }
                SheetMode.FRONT_BACK_GUIDED -> {
                    FrontBackGuidedSheetContent(
                        activeSide = activeTargetSide,
                        frontUri = frontUri,
                        backUri = backUri,
                        onSelectFront = { activeTargetSide = TargetSide.FRONT },
                        onSelectBack = { activeTargetSide = TargetSide.BACK },
                        onCameraClick = { launchCamera() },
                        onGalleryClick = { launchGallery() },
                        onContinue = {
                            val f = frontUri
                            val b = backUri
                            if (f != null && b != null) {
                                sheetMode = SheetMode.NONE
                                onFrontBackDocumentsSelected(f, b)
                            }
                        }
                    )
                }
                SheetMode.NONE -> {}
            }
        }
    }

    if (showAddPresetDialog) {
        var newLabel by remember { mutableStateOf("") }
        var newTemplate by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            containerColor = colors.bgElevated,
            title = {
                Text(
                    text = "New Watermark Preset",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { if (it.length <= 24) newLabel = it },
                        label = { Text("Short Label (e.g. Acme HR)", fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.textSecondary,
                            unfocusedLabelColor = colors.textTertiary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = newTemplate,
                        onValueChange = { if (it.length <= 80) newTemplate = it },
                        label = { Text("Watermark Text (e.g. FOR ACME HR ONLY)", fontSize = 13.sp) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.textSecondary,
                            unfocusedLabelColor = colors.textTertiary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomPreset(newLabel, newTemplate)
                        showAddPresetDialog = false
                    },
                    enabled = newLabel.isNotBlank() && newTemplate.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.onAccentPrimary
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    presetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            containerColor = colors.bgElevated,
            title = {
                Text(
                    text = "Remove Custom Preset",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Delete \"${target.label}\" from your quick watermark presets?",
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomPreset(target.id)
                        presetToDelete = null
                    }
                ) {
                    Text("Delete", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

/**
 * Minimalist Whole-Card Interactive Action Card with 0.dp elevation and 1.dp hairline border.
 */
@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SafeIdTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgElevated,
        border = BorderStroke(1.dp, colors.borderHairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top row: 40.dp rounded square icon container + subtle chevron on the far right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Title & Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun SinglePickerSheetContent(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val colors = SafeIdTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Select Document Source",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "Camera photos never leave your device. All metadata is stripped locally.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOptionRow(
                title = "Take Photo with Camera",
                subtitle = "Capture a clean document photo",
                icon = Icons.Default.CameraAlt,
                onClick = onCameraClick
            )

            SourceOptionRow(
                title = "Choose from Gallery",
                subtitle = "Pick an image from your device library",
                icon = Icons.Default.PhotoLibrary,
                onClick = onGalleryClick
            )
        }
    }
}

@Composable
private fun MultiPagePickerSheetContent(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val colors = SafeIdTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Multi-Page PDF Bundle",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "Combine 2 to 10 document pages into a single watermarked, size-capped PDF.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOptionRow(
                title = "Choose Multiple from Gallery",
                subtitle = "Select up to 10 photos or documents",
                icon = Icons.Default.PhotoLibrary,
                onClick = onGalleryClick
            )

            SourceOptionRow(
                title = "Capture Pages with Camera",
                subtitle = "Take photos and append pages one by one",
                icon = Icons.Default.CameraAlt,
                onClick = onCameraClick
            )
        }
    }
}

@Composable
private fun PassportPhotoPickerSheetContent(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val colors = SafeIdTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Passport Photo Print Grid",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "Select or capture a portrait photo to tile on a printable 4x6 or A4 sheet.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOptionRow(
                title = "Choose from Gallery",
                subtitle = "Pick a portrait photo from your library",
                icon = Icons.Default.PhotoLibrary,
                onClick = onGalleryClick
            )

            SourceOptionRow(
                title = "Take Photo with Camera",
                subtitle = "Capture a clean portrait photo",
                icon = Icons.Default.CameraAlt,
                onClick = onCameraClick
            )
        }
    }
}

@Composable
private fun FrontBackGuidedSheetContent(
    activeSide: TargetSide,
    frontUri: Uri?,
    backUri: Uri?,
    onSelectFront: () -> Unit,
    onSelectBack: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val colors = SafeIdTheme.colors

    var frontThumb by remember { mutableStateOf<Bitmap?>(null) }
    var backThumb by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(frontUri) {
        frontThumb = if (frontUri != null) {
            withContext(Dispatchers.IO) {
                loadThumbnailSafely(context, frontUri)
            }
        } else null
    }

    LaunchedEffect(backUri) {
        backThumb = if (backUri != null) {
            withContext(Dispatchers.IO) {
                loadThumbnailSafely(context, backUri)
            }
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Front & Back ID Card",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = "2-step guided capture aligned onto a single printable A4 page.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = colors.textSecondary
            )
        }

        // Side selector status pills (Equal height: 52.dp with mini thumbnails)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SidePill(
                label = "1. Front Side",
                isReady = frontUri != null,
                isActive = activeSide == TargetSide.FRONT,
                thumbnailBitmap = frontThumb,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                onClick = onSelectFront
            )
            SidePill(
                label = "2. Back Side",
                isReady = backUri != null,
                isActive = activeSide == TargetSide.BACK,
                thumbnailBitmap = backThumb,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                onClick = onSelectBack
            )
        }

        val isBothReady = frontUri != null && backUri != null
        val isFrontMissing = frontUri == null && backUri != null
        val isBackMissing = frontUri != null && backUri == null

        val promptText = when {
            isBothReady -> "Both sides ready. Tap a tab above to replace, or continue."
            isBackMissing -> "Step 2: Select or capture the BACK side"
            isFrontMissing -> "Step 1: Select or capture the FRONT side"
            activeSide == TargetSide.FRONT -> "Step 1: Select or capture the FRONT side"
            else -> "Step 2: Select or capture the BACK side"
        }

        Text(
            text = promptText,
            fontSize = if (isBothReady) 13.sp else 14.sp,
            fontWeight = if (isBothReady) FontWeight.Normal else FontWeight.Medium,
            color = if (isBothReady) colors.textSecondary else colors.textPrimary,
            lineHeight = 18.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOptionRow(
                title = "Take Photo with Camera",
                subtitle = "Capture using your camera",
                icon = Icons.Default.CameraAlt,
                onClick = onCameraClick
            )

            SourceOptionRow(
                title = "Choose from Gallery",
                subtitle = "Select from photo library",
                icon = Icons.Default.PhotoLibrary,
                onClick = onGalleryClick
            )
        }

        if (isBothReady) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Continue to Crop",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SourceOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = SafeIdTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgSubtle,
        border = BorderStroke(1.dp, colors.borderHairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgElevated)
                    .border(1.dp, colors.borderHairline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SidePill(
    label: String,
    isReady: Boolean,
    isActive: Boolean,
    thumbnailBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = SafeIdTheme.colors
    val borderColor = if (isActive) colors.accentPrimary else colors.borderHairline
    val bgColor = if (isActive) colors.bgElevated else colors.bgSubtle

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isReady) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = colors.statusSafeDot,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Fast, lightweight thumbnail decoder with EXIF orientation correction.
 */
private fun loadThumbnailSafely(context: Context, uri: Uri): Bitmap? {
    return try {
        val stream = if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: return null
            FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(uri)
        } ?: return null

        val bytes = stream.use { it.readBytes() }
        if (bytes.isEmpty()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // Downsample to ~120px for high density 28dp thumbnail
        var inSampleSize = 1
        val targetSize = 120
        while (options.outWidth / (inSampleSize * 2) >= targetSize &&
            options.outHeight / (inSampleSize * 2) >= targetSize
        ) {
            inSampleSize *= 2
        }

        options.inSampleSize = inSampleSize
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    } catch (e: Exception) {
        android.util.Log.e("SafeID", "loadThumbnailSafely failed", e)
        null
    }
}

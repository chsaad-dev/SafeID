package com.saaddev.safeid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.saaddev.safeid.ui.components.AdBannerComposable
import com.saaddev.safeid.ui.theme.LocalThemeMode
import com.saaddev.safeid.ui.theme.LocalThemeModeChanger
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.theme.ThemeMode
import java.io.File

private enum class SheetMode {
    NONE,
    SINGLE_PICKER,
    FRONT_BACK_GUIDED
}

private enum class TargetSide {
    FRONT,
    BACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSingleDocumentSelected: (Uri) -> Unit,
    onFrontBackDocumentsSelected: (frontUri: Uri, backUri: Uri) -> Unit
) {
    val context = LocalContext.current
    val colors = SafeIdTheme.colors
    val themeMode = LocalThemeMode.current
    val onThemeModeChanged = LocalThemeModeChanger.current

    var sheetMode by remember { mutableStateOf(SheetMode.NONE) }
    var activeTargetSide by remember { mutableStateOf(TargetSide.FRONT) }

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

    // Camera Result Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val capturedUri = pendingCameraUri!!
            when (sheetMode) {
                SheetMode.SINGLE_PICKER -> {
                    sheetMode = SheetMode.NONE
                    onSingleDocumentSelected(capturedUri)
                }
                SheetMode.FRONT_BACK_GUIDED -> {
                    if (activeTargetSide == TargetSide.FRONT) {
                        frontUri = capturedUri
                        activeTargetSide = TargetSide.BACK
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
                    onSingleDocumentSelected(uri)
                }
                SheetMode.FRONT_BACK_GUIDED -> {
                    if (activeTargetSide == TargetSide.FRONT) {
                        frontUri = uri
                        activeTargetSide = TargetSide.BACK
                    } else {
                        backUri = uri
                    }
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
            .padding(bottom = 32.dp),
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
    val colors = SafeIdTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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

        // Side selector status pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SidePill(
                label = "1. Front Side",
                isReady = frontUri != null,
                isActive = activeSide == TargetSide.FRONT,
                modifier = Modifier.weight(1f),
                onClick = onSelectFront
            )
            SidePill(
                label = "2. Back Side",
                isReady = backUri != null,
                isActive = activeSide == TargetSide.BACK,
                modifier = Modifier.weight(1f),
                onClick = onSelectBack
            )
        }

        val promptText = if (activeSide == TargetSide.FRONT) {
            "Capture or select FRONT side:"
        } else {
            "Capture or select BACK side:"
        }

        Text(
            text = promptText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
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

        val bothReady = frontUri != null && backUri != null
        if (bothReady) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.statusSafeDot,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Both Sides Selected • Continue to Crop",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) colors.textPrimary else colors.textSecondary
            )
            if (isReady) {
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

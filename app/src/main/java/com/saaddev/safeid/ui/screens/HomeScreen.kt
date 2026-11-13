package com.saaddev.safeid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.saaddev.safeid.ui.components.AdBannerComposable
import com.saaddev.safeid.ui.theme.SafeGreen
import com.saaddev.safeid.ui.theme.Teal500
import com.saaddev.safeid.ui.theme.Teal600
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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SafeID",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // 100% Offline • On-Device Only badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SafeGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.4f)),
                            contentColor = SafeGreen
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "100% Offline • On-Device Only",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AdBannerComposable()
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Tip Strip
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Teal500,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Tip: Tap Share on any photo in your Gallery and pick SafeID directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Action Card 1: Single Document / Photo
            ActionCard(
                title = "Single Document / Photo",
                subtitle = "One-sided ID card, passport page, certificate, utility bill, or receipt.",
                icon = Icons.Default.Description,
                badgeText = "Fast 1-Step",
                onClick = {
                    sheetMode = SheetMode.SINGLE_PICKER
                }
            )

            // Primary Action Card 2: Front + Back ID Card (A4 Layout)
            ActionCard(
                title = "Front + Back ID Card (A4 Layout)",
                subtitle = "Guided 2-step capture (Front → Back) aligned onto a single printable A4 page at 300 DPI equivalent.",
                icon = Icons.Default.CreditCard,
                badgeText = "2-Sided Guided",
                onClick = {
                    frontUri = null
                    backUri = null
                    activeTargetSide = TargetSide.FRONT
                    sheetMode = SheetMode.FRONT_BACK_GUIDED
                }
            )
        }
    }

    // Modal Bottom Sheet for Image Sources
    if (sheetMode != SheetMode.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { sheetMode = SheetMode.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
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

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Teal500.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Teal500,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal600
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Select / Capture",
                    fontWeight = FontWeight.SemiBold
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Document Source",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Camera photos never leave your device. All GPS and device metadata are stripped locally.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Teal500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera")
                }
            }

            Button(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Front + Back ID Card (A4 Layout)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Status Tabs for Front / Back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SideStatusBadge(
                label = "1. Front Side",
                isReady = frontUri != null,
                isActive = activeSide == TargetSide.FRONT,
                modifier = Modifier.weight(1f),
                onClick = onSelectFront
            )

            SideStatusBadge(
                label = "2. Back Side",
                isReady = backUri != null,
                isActive = activeSide == TargetSide.BACK,
                modifier = Modifier.weight(1f),
                onClick = onSelectBack
            )
        }

        HorizontalDivider()

        val promptText = if (activeSide == TargetSide.FRONT) {
            "Capture or select the FRONT of your ID card:"
        } else {
            "Capture or select the BACK of your ID card:"
        }

        Text(
            text = promptText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Camera and Gallery buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Teal500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera")
                }
            }

            Button(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
        }

        // Continue Button when both sides are ready
        val bothReady = frontUri != null && backUri != null
        if (bothReady) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Both Sides Selected • Continue to Crop", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SideStatusBadge(
    label: String,
    isReady: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) Teal500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) BorderStroke(1.5.dp, Teal500) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Teal500 else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isReady) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = SafeGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

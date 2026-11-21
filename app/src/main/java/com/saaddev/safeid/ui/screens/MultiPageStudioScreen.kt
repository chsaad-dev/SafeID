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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.saaddev.safeid.data.model.BundleTargetSize
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.engine.ExifPrivacyEngine
import com.saaddev.safeid.engine.ExportAndCompressionEngine
import com.saaddev.safeid.engine.ScanFilter
import com.saaddev.safeid.engine.WatermarkColorPreset
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class BundleWatermarkPreset(val chipLabel: String, val textTemplate: String) {
    SIM("SIM Verification", "FOR SIM VERIFICATION ONLY"),
    KYC("Bank / KYC", "FOR BANK & KYC VERIFICATION ONLY"),
    JOB("Job Application", "FOR JOB APPLICATION ONLY"),
    VISA("Visa / Travel", "FOR VISA & TRAVEL APPLICATION ONLY"),
    RENTAL("Rental", "FOR RENTAL AGREEMENT ONLY"),
    CUSTOM("Custom", "CONFIDENTIAL & PRIVACY PROTECTED")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPageStudioScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportShare: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by viewModel.sessionState.collectAsState()
    val colors = SafeIdTheme.colors

    var pages by remember { mutableStateOf(sessionState.bundleImageUris) }
    val pageThumbnails = remember { mutableStateMapOf<Uri, Bitmap?>() }
    var totalStrippedTags by remember { mutableIntStateOf(0) }

    // Customization state
    var selectedFilter by remember { mutableStateOf(ScanFilter.ORIGINAL) }
    var selectedPreset by remember { mutableStateOf(BundleWatermarkPreset.KYC) }
    var customWatermarkText by remember { mutableStateOf(BundleWatermarkPreset.KYC.textTemplate) }
    var includeDate by remember { mutableStateOf(true) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.25f) }
    var selectedColorInt by remember { mutableIntStateOf(WatermarkColorPreset.GREY.colorInt) }
    var targetSize by remember { mutableStateOf(BundleTargetSize.ORIGINAL) }

    var isGenerating by remember { mutableStateOf(false) }
    var showAddPageSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Today's date string
    val todayDateString = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    val fullWatermarkText = remember(customWatermarkText, includeDate, todayDateString) {
        val trimmed = customWatermarkText.trim()
        if (includeDate) {
            if (trimmed.isEmpty()) todayDateString
            else if (trimmed.contains(todayDateString, ignoreCase = true)) trimmed
            else "$trimmed • $todayDateString"
        } else {
            trimmed
        }
    }

    // Helper: Copy URI to cache
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

    // Preload thumbnails and audit stripped tags
    LaunchedEffect(pages) {
        withContext(Dispatchers.IO) {
            var strippedSum = 0
            for (uri in pages) {
                if (!pageThumbnails.containsKey(uri)) {
                    val bmp = loadThumbnailSafely(context, uri)
                    pageThumbnails[uri] = bmp
                }
                val (report, _) = ExifPrivacyEngine.loadAndAudit(context, uri)
                if (report != null) {
                    strippedSum += report.strippedTagCount
                }
            }
            withContext(Dispatchers.Main) {
                totalStrippedTags = strippedSum
            }
        }
    }

    // Camera setup for adding pages
    fun prepareCameraUri(): Uri {
        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val tempFile = File(exportDir, "bundle_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            val captured = pendingCameraUri!!
            if (pages.size < 10) {
                pages = pages + captured
            }
            showAddPageSheet = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val newUri = prepareCameraUri()
            pendingCameraUri = newUri
            takePictureLauncher.launch(newUri)
        } else {
            Toast.makeText(context, "Camera permission is required to capture pages", Toast.LENGTH_SHORT).show()
        }
    }

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

    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = (10 - pages.size).coerceAtLeast(1))
    ) { uris ->
        if (uris.isNotEmpty()) {
            val safeUris = uris.mapIndexed { index, uri ->
                copyUriToTemp(uri, "bundle_add_$index")
            }
            pages = (pages + safeUris).take(10)
            showAddPageSheet = false
        }
    }

    fun launchGallery() {
        pickMultipleMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Reorder & Remove actions
    fun movePageLeft(index: Int) {
        if (index > 0) {
            val mutable = pages.toMutableList()
            val temp = mutable[index]
            mutable[index] = mutable[index - 1]
            mutable[index - 1] = temp
            pages = mutable
        }
    }

    fun movePageRight(index: Int) {
        if (index < pages.size - 1) {
            val mutable = pages.toMutableList()
            val temp = mutable[index]
            mutable[index] = mutable[index + 1]
            mutable[index + 1] = temp
            pages = mutable
        }
    }

    fun removePage(index: Int) {
        if (pages.size > 1) {
            val mutable = pages.toMutableList()
            val removed = mutable.removeAt(index)
            pages = mutable
            pageThumbnails.remove(removed)
        }
    }

    // Generate Bundle PDF
    fun generateBundle() {
        if (pages.isEmpty()) return
        isGenerating = true

        coroutineScope.launch(Dispatchers.Default) {
            val defaultBaseName = viewModel.generateDefaultBaseFileName(
                isPassportGrid = false,
                isBundle = true,
                preset = null
            )

            val result = ExportAndCompressionEngine.generateMultiPagePdf(
                context = context,
                pageUris = pages,
                filter = selectedFilter,
                watermarkText = fullWatermarkText,
                watermarkColorInt = selectedColorInt,
                watermarkOpacity = watermarkOpacity,
                targetSizeKb = targetSize.maxKb,
                baseFileName = defaultBaseName
            )

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                result.pdfFile
            )

            withContext(Dispatchers.Main) {
                viewModel.setProcessedBitmaps(result.firstPagePreview, null)
                viewModel.updateExportConfig { current ->
                    current.copy(
                        format = ExportFormat.PDF,
                        exportedFileUri = contentUri,
                        exportedFileName = result.pdfFile.name,
                        exportBaseFileName = defaultBaseName,
                        exportedFileSizeBytes = result.fileSizeBytes,
                        watermarkText = fullWatermarkText,
                        watermarkOpacity = watermarkOpacity,
                        watermarkColor = selectedColorInt.toLong()
                    )
                }
                isGenerating = false
                onNavigateToExportShare()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PDF Bundle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Page count pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.bgSubtle,
                border = BorderStroke(1.dp, colors.borderHairline)
            ) {
                Text(
                    text = "${pages.size} / 10 Pages",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // 2. Privacy Summary Strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.bgElevated,
            border = BorderStroke(1.dp, colors.borderHairline),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.statusSafeDot)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (totalStrippedTags > 0) {
                        "EXIF & GPS Stripped • $totalStrippedTags tags removed across ${pages.size} pages"
                    } else {
                        "Clean Bundle • No EXIF metadata found"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. Interactive Page Strip (Thumbnails, Reorder, Remove, Add)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(pages) { index, uri ->
                    val bmp = pageThumbnails[uri]
                    Surface(
                        modifier = Modifier
                            .size(width = 96.dp, height = 124.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.bgElevated,
                        border = BorderStroke(1.dp, colors.borderHairline),
                        shadowElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Thumbnail Image
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.bgSubtle),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colors.accentSurgical,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            // Top gradient shadow / scrim for badges
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )

                            // Page number badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.bgPrimary.copy(alpha = 0.90f),
                                border = BorderStroke(1.dp, colors.borderHairline),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }

                            // Remove button
                            if (pages.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                    .background(colors.bgPrimary.copy(alpha = 0.90f))
                                    .clickable { removePage(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Page",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Reorder arrows bar at bottom
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .align(Alignment.BottomCenter),
                                color = colors.bgElevated.copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { movePageLeft(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Move Left",
                                            tint = if (index > 0) colors.textPrimary else colors.textTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(12.dp)
                                            .background(colors.borderHairline)
                                    )

                                    IconButton(
                                        onClick = { movePageRight(index) },
                                        enabled = index < pages.size - 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Move Right",
                                            tint = if (index < pages.size - 1) colors.textPrimary else colors.textTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Page Tile (if < 10 pages)
                if (pages.size < 10) {
                    item {
                        Surface(
                            modifier = Modifier
                                .size(width = 96.dp, height = 124.dp)
                                .clickable { showAddPageSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bgSubtle,
                            border = BorderStroke(1.dp, colors.borderHairline),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.bgElevated)
                                        .border(1.dp, colors.borderHairline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Page",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add Page",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Scrollable Controls Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Document Filter Segmented Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = colors.bgSubtle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterOptions = listOf(
                        ScanFilter.ORIGINAL to "Original",
                        ScanFilter.CLEAN_SCAN to "Clean Scan",
                        ScanFilter.COLOR_BOOST to "Color Boost"
                    )
                    filterOptions.forEach { (filter, label) ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .background(colors.bgElevated)
                                            .border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier.background(Color.Transparent)
                                    }
                                )
                                .clickable { selectedFilter = filter },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Card A: Watermark Purpose & Text
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgElevated,
                border = BorderStroke(1.dp, colors.borderHairline),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Watermark Purpose",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    // Presets LazyRow
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(BundleWatermarkPreset.entries.toTypedArray()) { preset ->
                            val isSelected = selectedPreset == preset
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) colors.accentPrimary else colors.bgSubtle,
                                border = if (isSelected) null else BorderStroke(1.dp, colors.borderHairline),
                                modifier = Modifier.clickable {
                                    selectedPreset = preset
                                    customWatermarkText = preset.textTemplate
                                }
                            ) {
                                Text(
                                    text = preset.chipLabel,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) colors.onAccentPrimary else colors.textSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Watermark Text input
                    OutlinedTextField(
                        value = customWatermarkText,
                        onValueChange = {
                            customWatermarkText = it
                            selectedPreset = BundleWatermarkPreset.CUSTOM
                        },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedLabelColor = colors.accentPrimary,
                            unfocusedLabelColor = colors.textSecondary,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentPrimary,
                            focusedContainerColor = colors.bgElevated,
                            unfocusedContainerColor = colors.bgElevated
                        )
                    )

                    // Date toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Include Today's Date",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Appends $todayDateString",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }

                        Switch(
                            checked = includeDate,
                            onCheckedChange = { includeDate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccentPrimary,
                                checkedTrackColor = colors.accentPrimary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.bgSubtle,
                                uncheckedBorderColor = colors.borderHairline
                            )
                        )
                    }
                }
            }

            // Card B: Style & Appearance (Opacity & Ink Color)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgElevated,
                border = BorderStroke(1.dp, colors.borderHairline),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${(watermarkOpacity * 100).roundToInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }

                    Slider(
                        value = watermarkOpacity,
                        onValueChange = { watermarkOpacity = it },
                        valueRange = 0.10f..0.60f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accentPrimary,
                            activeTrackColor = colors.accentPrimary,
                            inactiveTrackColor = colors.borderHairline
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.borderHairline)
                    )

                    Text(
                        text = "Watermark Ink Color",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colorOptions = listOf(
                            Triple("Slate", Color(0xFF64748B), WatermarkColorPreset.GREY.colorInt),
                            Triple("Red", Color(0xFFDC2626), WatermarkColorPreset.RED.colorInt),
                            Triple("Blue", Color(0xFF2563EB), WatermarkColorPreset.BLUE.colorInt)
                        )

                        colorOptions.forEach { (label, dotColor, presetInt) ->
                            val isSelected = selectedColorInt == presetInt
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable { selectedColorInt = presetInt },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.bgSubtle,
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, colors.accentPrimary)
                                } else {
                                    BorderStroke(1.dp, colors.borderHairline)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Card C: Target PDF Size Limit
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgElevated,
                border = BorderStroke(1.dp, colors.borderHairline),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Target PDF Size Limit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BundleTargetSize.entries.forEach { sizeOption ->
                            val isSelected = targetSize == sizeOption
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { targetSize = sizeOption },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.bgElevated else colors.bgSubtle,
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, colors.accentPrimary)
                                } else {
                                    BorderStroke(1.dp, colors.borderHairline)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sizeOption.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 5. Fixed Bottom Primary Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            color = Color.Transparent
        ) {
            Button(
                onClick = { generateBundle() },
                enabled = !isGenerating && pages.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary,
                    disabledContainerColor = colors.bgSubtle,
                    disabledContentColor = colors.textTertiary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.onAccentPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Generate PDF Bundle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    // Modal Sheet for adding pages
    if (showAddPageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddPageSheet = false },
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Pages to Bundle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { launchGallery() },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.bgSubtle,
                    border = BorderStroke(1.dp, colors.borderHairline)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Choose from Gallery", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("Select up to ${10 - pages.size} more photos", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { launchCamera() },
                    shape = RoundedCornerShape(14.dp),
                    color = colors.bgSubtle,
                    border = BorderStroke(1.dp, colors.borderHairline)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Take Photo with Camera", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("Capture next page using your camera", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
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

        var inSampleSize = 1
        val targetSize = 160
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

package com.saaddev.safeid.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.data.model.TargetSize
import com.saaddev.safeid.engine.BlurStroke
import com.saaddev.safeid.engine.ExportAndCompressionEngine
import com.saaddev.safeid.engine.NormalizedPoint
import com.saaddev.safeid.engine.WatermarkAndBlurEngine
import com.saaddev.safeid.engine.WatermarkColorPreset
import com.saaddev.safeid.ui.theme.AlertRed
import com.saaddev.safeid.ui.theme.SafeGreen
import com.saaddev.safeid.ui.theme.Slate400
import com.saaddev.safeid.ui.theme.Slate700
import com.saaddev.safeid.ui.theme.Teal500
import com.saaddev.safeid.ui.theme.Teal600
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class StudioInteractionMode(val label: String) {
    WATERMARK("Watermark Mode"),
    BLUR_BRUSH("Blur Redact Brush")
}

private enum class WatermarkPreset(val chipLabel: String, val textTemplate: String) {
    SIM("SIM Verification", "FOR SIM VERIFICATION ONLY"),
    KYC("Bank / KYC", "FOR BANK & KYC VERIFICATION ONLY"),
    JOB("Job Application", "FOR JOB APPLICATION ONLY"),
    VISA("Visa / Travel", "FOR VISA & TRAVEL APPLICATION ONLY"),
    RENTAL("Rental Agreement", "FOR RENTAL AGREEMENT ONLY"),
    CUSTOM("Custom Text", "CONFIDENTIAL & PRIVACY PROTECTED")
}

private enum class ActiveStudioSide {
    FRONT,
    BACK
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatermarkStudioScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportShare: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by viewModel.sessionState.collectAsState()

    var activeSide by remember { mutableStateOf(ActiveStudioSide.FRONT) }
    var interactionMode by remember { mutableStateOf(StudioInteractionMode.WATERMARK) }

    // Preset & Text state
    var selectedPreset by remember { mutableStateOf(WatermarkPreset.KYC) }
    var customWatermarkText by remember { mutableStateOf(WatermarkPreset.KYC.textTemplate) }
    var includeDate by remember { mutableStateOf(true) }

    // Styling state
    var watermarkOpacity by remember { mutableFloatStateOf(0.25f) }
    var selectedColorInt by remember { mutableIntStateOf(WatermarkColorPreset.GREY.colorInt) }

    // Redaction blur strokes
    var frontBlurStrokes by remember { mutableStateOf<List<BlurStroke>>(emptyList()) }
    var backBlurStrokes by remember { mutableStateOf<List<BlurStroke>>(emptyList()) }
    var liveDragPoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

    // Output & Compression config
    var selectedFormat by remember {
        mutableStateOf(if (sessionState.isFrontBackMode) ExportFormat.PDF else ExportFormat.JPG)
    }
    var selectedTargetSize by remember { mutableStateOf(TargetSize.ORIGINAL) }

    var isExporting by remember { mutableStateOf(false) }

    // Current side references
    val currentBaseBitmap = if (activeSide == ActiveStudioSide.FRONT) {
        sessionState.processedBitmap
    } else {
        sessionState.secondaryProcessedBitmap
    }

    val currentBlurStrokes = if (activeSide == ActiveStudioSide.FRONT) frontBlurStrokes else backBlurStrokes

    fun updateCurrentBlurStrokes(newStrokes: List<BlurStroke>) {
        if (activeSide == ActiveStudioSide.FRONT) {
            frontBlurStrokes = newStrokes
        } else {
            backBlurStrokes = newStrokes
        }
    }

    fun addBlurStroke(newStroke: BlurStroke) {
        if (activeSide == ActiveStudioSide.FRONT) {
            frontBlurStrokes = frontBlurStrokes + newStroke
        } else {
            backBlurStrokes = backBlurStrokes + newStroke
        }
    }

    // Today's date string
    val todayDateString = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    }

    // Combined full watermark string
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

    // Cached preview with blur applied
    var blurredPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentBaseBitmap, currentBlurStrokes) {
        val base = currentBaseBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val blurred = if (currentBlurStrokes.isNotEmpty()) {
                WatermarkAndBlurEngine.applyBlurStrokes(base, currentBlurStrokes, pixelationBlockSize = 16)
            } else {
                base
            }
            withContext(Dispatchers.Main) {
                blurredPreviewBitmap = blurred
            }
        }
    }

    // Execution: Generate Safe File
    fun executeSafeExport() {
        val frontSource = sessionState.processedBitmap ?: return
        isExporting = true

        coroutineScope.launch(Dispatchers.Default) {
            // Step 1: Bake watermark and blur into front
            val finalFront = WatermarkAndBlurEngine.applyWatermarkAndBlur(
                source = frontSource,
                watermarkText = fullWatermarkText,
                watermarkColorInt = selectedColorInt,
                watermarkOpacity = watermarkOpacity,
                autoAppendDate = false, // already appended in fullWatermarkText
                spacingMultiplier = 1.0f,
                blurStrokes = frontBlurStrokes
            )

            // Step 2: Bake watermark and blur into back (if Front + Back mode)
            val finalBack = if (sessionState.isFrontBackMode && sessionState.secondaryProcessedBitmap != null) {
                WatermarkAndBlurEngine.applyWatermarkAndBlur(
                    source = sessionState.secondaryProcessedBitmap!!,
                    watermarkText = fullWatermarkText,
                    watermarkColorInt = selectedColorInt,
                    watermarkOpacity = watermarkOpacity,
                    autoAppendDate = false,
                    spacingMultiplier = 1.0f,
                    blurStrokes = backBlurStrokes
                )
            } else null

            // Step 3: Handle export formats
            val timestamp = System.currentTimeMillis()
            val exportedFile = if (selectedFormat == ExportFormat.PDF) {
                val pdfDoc = if (sessionState.isFrontBackMode && finalBack != null) {
                    ExportAndCompressionEngine.createFrontBackIdCardPdf(finalFront, finalBack)
                } else {
                    ExportAndCompressionEngine.createSinglePagePdf(finalFront)
                }
                ExportAndCompressionEngine.savePdfToExportCache(
                    context,
                    pdfDoc,
                    "SafeID_Export_$timestamp.pdf"
                )
            } else {
                // JPG image compression
                val compressionResult = ExportAndCompressionEngine.compressBitmapToTargetKb(
                    source = finalFront,
                    targetKb = selectedTargetSize.maxKb
                )
                ExportAndCompressionEngine.saveBytesToExportCache(
                    context,
                    compressionResult.data,
                    "SafeID_Export_$timestamp.jpg"
                )
            }

            val fileUri = exportedFile.toUri()
            val fileSize = exportedFile.length()
            val fileName = exportedFile.name

            withContext(Dispatchers.Main) {
                viewModel.updateExportConfig { current ->
                    current.copy(
                        format = selectedFormat,
                        targetSize = selectedTargetSize,
                        watermarkText = fullWatermarkText,
                        watermarkOpacity = watermarkOpacity,
                        watermarkColor = selectedColorInt.toLong(),
                        exportedFileUri = fileUri,
                        exportedFileSizeBytes = fileSize,
                        exportedFileName = fileName
                    )
                }
                isExporting = false
                onNavigateToExportShare()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Watermark & Redact Studio", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Front + Back Tab Row (when in Front+Back mode)
            if (sessionState.isFrontBackMode) {
                PrimaryTabRow(
                    selectedTabIndex = if (activeSide == ActiveStudioSide.FRONT) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = activeSide == ActiveStudioSide.FRONT,
                        onClick = { activeSide = ActiveStudioSide.FRONT },
                        text = {
                            Text(
                                "Preview: Front Side",
                                fontWeight = if (activeSide == ActiveStudioSide.FRONT) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = activeSide == ActiveStudioSide.BACK,
                        onClick = { activeSide = ActiveStudioSide.BACK },
                        text = {
                            Text(
                                "Preview: Back Side",
                                fontWeight = if (activeSide == ActiveStudioSide.BACK) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 1. Live Interactive Preview Canvas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Interaction Mode Segmented Switch + Undo Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                            StudioInteractionMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = interactionMode == mode,
                                    onClick = { interactionMode = mode },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = interactionMode == mode)
                                    }
                                ) {
                                    Text(mode.label, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }

                        // Undo Blur Button
                        AnimatedVisibility(visible = interactionMode == StudioInteractionMode.BLUR_BRUSH) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (currentBlurStrokes.isNotEmpty()) {
                                            updateCurrentBlurStrokes(currentBlurStrokes.dropLast(1))
                                        }
                                    },
                                    enabled = currentBlurStrokes.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo Blur",
                                        tint = if (currentBlurStrokes.isNotEmpty()) Teal500 else Slate400
                                    )
                                }
                            }
                        }
                    }

                    if (interactionMode == StudioInteractionMode.BLUR_BRUSH) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Drag your finger across signatures, barcodes, or IDs to pixelate and redact.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Teal500,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Canvas View
                    val displayBitmap = blurredPreviewBitmap ?: currentBaseBitmap
                    if (displayBitmap != null) {
                        LiveStudioCanvas(
                            bitmap = displayBitmap,
                            watermarkText = fullWatermarkText,
                            colorInt = selectedColorInt,
                            opacity = watermarkOpacity,
                            isBlurMode = interactionMode == StudioInteractionMode.BLUR_BRUSH,
                            onNewStrokeCompleted = { newStroke ->
                                addBlurStroke(newStroke)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Teal500)
                        }
                    }
                }
            }

            // 2. 1-Tap Purpose Presets & Customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Watermark Purpose Preset",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Horizontal Scrollable Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkPreset.entries.forEach { preset ->
                            val isSelected = selectedPreset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPreset = preset
                                    customWatermarkText = preset.textTemplate
                                },
                                label = { Text(preset.chipLabel, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                    selectedLabelColor = Teal500,
                                    selectedLeadingIconColor = Teal500
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Editable Watermark Text Field
                    OutlinedTextField(
                        value = customWatermarkText,
                        onValueChange = {
                            customWatermarkText = it
                            selectedPreset = WatermarkPreset.CUSTOM
                        },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Include Today's Date Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Teal500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Include Today's Date", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Appends • $todayDateString", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = includeDate,
                            onCheckedChange = { includeDate = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Teal500)
                        )
                    }

                    HorizontalDivider()

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Opacity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${(watermarkOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Teal500
                        )
                    }
                    Slider(
                        value = watermarkOpacity,
                        onValueChange = { watermarkOpacity = it },
                        valueRange = 0.10f..0.60f,
                        colors = SliderDefaults.colors(thumbColor = Teal500, activeTrackColor = Teal600)
                    )

                    // Watermark Color Swatches
                    Text("Watermark Ink Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ColorSwatchItem(
                            label = "Slate Grey",
                            color = Color(0xFF64748B),
                            isSelected = selectedColorInt == WatermarkColorPreset.GREY.colorInt,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedColorInt = WatermarkColorPreset.GREY.colorInt }
                        )

                        ColorSwatchItem(
                            label = "Crimson Red",
                            color = Color(0xFFDC2626),
                            isSelected = selectedColorInt == WatermarkColorPreset.RED.colorInt,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedColorInt = WatermarkColorPreset.RED.colorInt }
                        )

                        ColorSwatchItem(
                            label = "Navy Blue",
                            color = Color(0xFF2563EB),
                            isSelected = selectedColorInt == WatermarkColorPreset.BLUE.colorInt,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedColorInt = WatermarkColorPreset.BLUE.colorInt }
                        )
                    }
                }
            }

            // 3. Output Format & Target KB Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Output Format & Compression",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Format Segmented Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Format", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                        if (sessionState.isFrontBackMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Teal500)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF (A4 2-Sided Layout)", fontSize = 12.sp, color = Teal500)
                                }
                            }
                        } else {
                            SingleChoiceSegmentedButtonRow {
                                ExportFormat.entries.forEachIndexed { index, format ->
                                    SegmentedButton(
                                        selected = selectedFormat == format,
                                        onClick = { selectedFormat = format },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                    ) {
                                        Text(format.displayName, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Target KB Limit Pills
                    Text("Target File Size Limit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TargetSize.entries.forEach { sizeOption ->
                            val isSelected = selectedTargetSize == sizeOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTargetSize = sizeOption },
                                label = { Text(sizeOption.label, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                    selectedLabelColor = Teal500,
                                    selectedLeadingIconColor = Teal500
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Primary CTA: Generate Safe File
            Button(
                onClick = { executeSafeExport() },
                enabled = !isExporting && currentBaseBitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Baking Watermark & Compressing...")
                } else {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate Safe File",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchItem(
    label: String,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Teal500) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Teal500 else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Live interactive canvas preview rendering the cropped bitmap with real-time watermark
 * and handling touch drag strokes when in Blur Brush mode.
 */
@Composable
private fun LiveStudioCanvas(
    bitmap: Bitmap,
    watermarkText: String,
    colorInt: Int,
    opacity: Float,
    isBlurMode: Boolean,
    onNewStrokeCompleted: (BlurStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val onNewStrokeCompletedState by rememberUpdatedState(onNewStrokeCompleted)
    var currentDragStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isBlurMode, bitmap) {
                    if (!isBlurMode) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = imageDisplayRect
                            if (rect.width > 0 && rect.height > 0) {
                                val normX = ((offset.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val normY = ((offset.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                currentDragStroke = listOf(NormalizedPoint(normX, normY))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            val rect = imageDisplayRect
                            if (rect.width > 0 && rect.height > 0) {
                                val normX = ((pos.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val normY = ((pos.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                currentDragStroke = currentDragStroke + NormalizedPoint(normX, normY)
                            }
                        },
                        onDragEnd = {
                            if (currentDragStroke.isNotEmpty()) {
                                onNewStrokeCompletedState(BlurStroke(currentDragStroke, 0.045f))
                                currentDragStroke = emptyList()
                            }
                        },
                        onDragCancel = {
                            currentDragStroke = emptyList()
                        }
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Calculate aspect fit destination rectangle
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val scale = minOf(canvasW / imgW, canvasH / imgH)
            val displayW = imgW * scale
            val displayH = imgH * scale
            val left = (canvasW - displayW) / 2f
            val top = (canvasH - displayH) / 2f
            val computedRect = Rect(left, top, left + displayW, top + displayH)
            imageDisplayRect = computedRect

            // 2. Draw base (or blurred) image
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(computedRect.left.roundToInt(), computedRect.top.roundToInt()),
                dstSize = IntSize(computedRect.width.roundToInt(), computedRect.height.roundToInt())
            )

            // 3. Draw live drag stroke indicator if dragging
            if (currentDragStroke.isNotEmpty()) {
                currentDragStroke.forEach { pt ->
                    val screenX = computedRect.left + pt.x * computedRect.width
                    val screenY = computedRect.top + pt.y * computedRect.height
                    drawCircle(
                        color = Color.Red.copy(alpha = 0.5f),
                        radius = 12.dp.toPx(),
                        center = Offset(screenX, screenY)
                    )
                }
            }

            // 4. Render live real-time diagonal watermark directly on canvas
            if (watermarkText.isNotBlank()) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.clipRect(
                    computedRect.left,
                    computedRect.top,
                    computedRect.right,
                    computedRect.bottom
                )
                WatermarkAndBlurEngine.drawDiagonalWatermark(
                    canvas = nativeCanvas,
                    text = watermarkText,
                    colorInt = colorInt,
                    opacityFloat = opacity,
                    spacingMultiplier = 1.0f
                )
                nativeCanvas.restore()
            }
        }
    }
}

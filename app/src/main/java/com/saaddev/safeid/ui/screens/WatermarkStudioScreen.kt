package com.saaddev.safeid.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class StudioInteractionMode(val label: String) {
    WATERMARK("Watermark"),
    BLUR_BRUSH("Blur Brush")
}

private enum class WatermarkPreset(val chipLabel: String, val textTemplate: String) {
    SIM("SIM Verification", "FOR SIM VERIFICATION ONLY"),
    KYC("Bank / KYC", "FOR BANK & KYC VERIFICATION ONLY"),
    JOB("Job Application", "FOR JOB APPLICATION ONLY"),
    VISA("Visa / Travel", "FOR VISA & TRAVEL APPLICATION ONLY"),
    RENTAL("Rental", "FOR RENTAL AGREEMENT ONLY"),
    CUSTOM("Custom", "CONFIDENTIAL & PRIVACY PROTECTED")
}

private enum class ActiveStudioSide {
    FRONT,
    BACK
}

@Composable
fun WatermarkStudioScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportShare: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by viewModel.sessionState.collectAsState()
    val colors = SafeIdTheme.colors

    var activeSide by remember { mutableStateOf(ActiveStudioSide.FRONT) }
    var interactionMode by remember { mutableStateOf(StudioInteractionMode.WATERMARK) }

    // Preset & Text state
    val initialPreset = remember(sessionState.quickPreset) {
        when (sessionState.quickPreset) {
            "Bank KYC" -> WatermarkPreset.KYC
            "SIM Card" -> WatermarkPreset.SIM
            "Job Portal" -> WatermarkPreset.JOB
            "Visa / Travel" -> WatermarkPreset.VISA
            else -> WatermarkPreset.KYC
        }
    }

    var selectedPreset by remember(initialPreset) { mutableStateOf(initialPreset) }
    var customWatermarkText by remember(initialPreset) { mutableStateOf(initialPreset.textTemplate) }
    var includeDate by remember { mutableStateOf(true) }

    // Styling state
    var watermarkOpacity by remember { mutableFloatStateOf(0.25f) }
    var selectedColorInt by remember { mutableIntStateOf(WatermarkColorPreset.GREY.colorInt) }

    // Redaction blur strokes
    var frontBlurStrokes by remember { mutableStateOf<List<BlurStroke>>(emptyList()) }
    var backBlurStrokes by remember { mutableStateOf<List<BlurStroke>>(emptyList()) }

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
                autoAppendDate = false,
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

            // Step 3: Handle export formats with smart default filename
            val defaultBaseName = viewModel.generateDefaultBaseFileName(
                isPassportGrid = false,
                isBundle = false,
                preset = selectedPreset.chipLabel
            )
            val fullFileName = "$defaultBaseName.${selectedFormat.extension}"

            val exportedFile = if (selectedFormat == ExportFormat.PDF) {
                val pdfDoc = if (sessionState.isFrontBackMode && finalBack != null) {
                    ExportAndCompressionEngine.createFrontBackIdCardPdf(finalFront, finalBack)
                } else {
                    ExportAndCompressionEngine.createSinglePagePdf(finalFront)
                }
                ExportAndCompressionEngine.savePdfToExportCache(
                    context,
                    pdfDoc,
                    fullFileName
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
                    fullFileName
                )
            }

            val fileUri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportedFile
                )
            } catch (_: Exception) {
                exportedFile.toUri()
            }
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
                        exportedFileName = fileName,
                        exportBaseFileName = defaultBaseName
                    )
                }
                isExporting = false
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
        // 1. Fixed Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
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
                text = "Watermark & Size",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Fixed Front/Back Switcher (in Front + Back mode)
        if (sessionState.isFrontBackMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 20.dp),
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
                    val isFront = activeSide == ActiveStudioSide.FRONT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isFront) {
                                    Modifier
                                        .background(colors.bgElevated)
                                        .border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .clickable { activeSide = ActiveStudioSide.FRONT },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Front Side",
                            fontSize = 13.sp,
                            fontWeight = if (isFront) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isFront) colors.textPrimary else colors.textSecondary,
                            maxLines = 1
                        )
                    }

                    val isBack = activeSide == ActiveStudioSide.BACK
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isBack) {
                                    Modifier
                                        .background(colors.bgElevated)
                                        .border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .clickable { activeSide = ActiveStudioSide.BACK },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Back Side",
                            fontSize = 13.sp,
                            fontWeight = if (isBack) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isBack) colors.textPrimary else colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 2. Pinned Live Preview Card (Fixed height ~230dp, Zero Scroll Blindness)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.bgElevated,
            border = BorderStroke(1.dp, colors.borderHairline),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Top row: Segmented mode toggle + Undo button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.bgSubtle,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isWatermark = interactionMode == StudioInteractionMode.WATERMARK
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (isWatermark) {
                                            Modifier
                                                .background(colors.bgElevated)
                                                .border(1.dp, colors.borderHairline, RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier.background(Color.Transparent)
                                        }
                                    )
                                    .clickable { interactionMode = StudioInteractionMode.WATERMARK }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Watermark",
                                    fontSize = 12.sp,
                                    fontWeight = if (isWatermark) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isWatermark) colors.textPrimary else colors.textSecondary
                                )
                            }

                            val isBlur = interactionMode == StudioInteractionMode.BLUR_BRUSH
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (isBlur) {
                                            Modifier
                                                .background(colors.bgElevated)
                                                .border(1.dp, colors.borderHairline, RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier.background(Color.Transparent)
                                        }
                                    )
                                    .clickable { interactionMode = StudioInteractionMode.BLUR_BRUSH }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Blur Brush",
                                    fontSize = 12.sp,
                                    fontWeight = if (isBlur) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isBlur) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }

                    if (interactionMode == StudioInteractionMode.BLUR_BRUSH) {
                        TextButton(
                            onClick = {
                                if (currentBlurStrokes.isNotEmpty()) {
                                    updateCurrentBlurStrokes(currentBlurStrokes.dropLast(1))
                                }
                            },
                            enabled = currentBlurStrokes.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Undo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentBlurStrokes.isNotEmpty()) colors.textPrimary else colors.textTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Frame with bgSubtle background - NO harsh black letterbox bars
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle),
                    contentAlignment = Alignment.Center
                ) {
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
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.accentSurgical,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Scrollable Customization Controls Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

                    // LazyRow for presets
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(WatermarkPreset.entries.toTypedArray()) { preset ->
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

                    // Watermark Text Field
                    OutlinedTextField(
                        value = customWatermarkText,
                        onValueChange = {
                            customWatermarkText = it
                            selectedPreset = WatermarkPreset.CUSTOM
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

                    // Include Today's Date Toggle
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
                    // Opacity Row & Slider
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

                    // Watermark Ink Color: 3 equal-width single-line pills
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

            // Card C: Output Format & Target Size Limit
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
                        text = "Output Format & Compression",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )

                    // Format Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Format",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )

                        if (sessionState.isFrontBackMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.bgSubtle,
                                border = BorderStroke(1.dp, colors.borderHairline)
                            ) {
                                Text(
                                    text = "PDF • A4 2-Sided",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.bgSubtle,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(ExportFormat.JPG to "JPG", ExportFormat.PDF to "PDF").forEach { (fmt, label) ->
                                        val isSelected = selectedFormat == fmt
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (isSelected) {
                                                        Modifier
                                                            .background(colors.bgElevated)
                                                            .border(1.dp, colors.borderHairline, RoundedCornerShape(6.dp))
                                                    } else {
                                                        Modifier.background(Color.Transparent)
                                                    }
                                                )
                                                .clickable { selectedFormat = fmt }
                                                .padding(horizontal = 12.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) colors.textPrimary else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Target File Size Limit: 4 equal-width single-line pills
                    Text(
                        text = "Target File Size Limit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TargetSize.entries.forEach { sizeOption ->
                            val isSelected = selectedTargetSize == sizeOption
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable { selectedTargetSize = sizeOption },
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

        // 4. Pinned Bottom CTA Bar (Full-width 52dp, accentPrimary, no extra icons)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            color = Color.Transparent
        ) {
            Button(
                onClick = { executeSafeExport() },
                enabled = !isExporting && currentBaseBitmap != null,
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
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.onAccentPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Generate Safe File",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
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

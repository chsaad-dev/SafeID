package com.saaddev.safeid.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.data.model.GridCopies
import com.saaddev.safeid.data.model.PaperSheetSize
import com.saaddev.safeid.data.model.PassportStandard
import com.saaddev.safeid.engine.ExifPrivacyEngine
import com.saaddev.safeid.engine.PassportGridEngine
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PassportGridScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportShare: () -> Unit
) {
    val context = LocalContext.current
    val colors = SafeIdTheme.colors
    val sessionState by viewModel.sessionState.collectAsState()
    val scope = rememberCoroutineScope()

    val sourceUri: Uri? = sessionState.passportPhotoUri ?: sessionState.primaryImageUri

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingSource by remember { mutableStateOf(true) }
    var isGeneratingSheet by remember { mutableStateOf(false) }

    // Grid Options
    var selectedStandard by remember { mutableStateOf(PassportStandard.STANDARD_35_45) }
    var selectedPaper by remember { mutableStateOf(PaperSheetSize.PHOTO_4X6) }
    var selectedCopies by remember { mutableStateOf(GridCopies.SIX) }
    var showCutGuides by remember { mutableStateOf(true) }
    var exportFormat by remember { mutableStateOf(ExportFormat.JPG) }

    // Face Framing & Zoom
    var zoomFactor by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Load and strip EXIF from source bitmap
    LaunchedEffect(sourceUri) {
        if (sourceUri != null) {
            isLoadingSource = true
            withContext(Dispatchers.IO) {
                val (_, decoded) = ExifPrivacyEngine.loadAndAudit(context, sourceUri)
                sourceBitmap = decoded
            }
            isLoadingSource = false
        } else {
            isLoadingSource = false
        }
    }

    // Reactively compute cropped portrait
    val croppedPortrait = remember(sourceBitmap, selectedStandard, zoomFactor, panX, panY) {
        sourceBitmap?.let { bmp ->
            PassportGridEngine.cropAndFramePortrait(
                source = bmp,
                standard = selectedStandard,
                zoom = zoomFactor,
                panXNormalized = panX,
                panYNormalized = panY
            )
        }
    }

    // Reactively compute preview sheet
    val previewSheet = remember(croppedPortrait, selectedStandard, selectedPaper, selectedCopies, showCutGuides) {
        croppedPortrait?.let { cropped ->
            PassportGridEngine.generatePreviewSheet(
                croppedPortrait = cropped,
                standard = selectedStandard,
                paperSize = selectedPaper,
                copies = selectedCopies,
                showCutGuides = showCutGuides
            )
        }
    }

    fun handleGenerateSheet() {
        val src = sourceBitmap
        if (src == null) {
            Toast.makeText(context, "No photo loaded", Toast.LENGTH_SHORT).show()
            return
        }

        isGeneratingSheet = true
        scope.launch(Dispatchers.IO) {
            try {
                val croppedFull = PassportGridEngine.cropAndFramePortrait(
                    source = src,
                    standard = selectedStandard,
                    zoom = zoomFactor,
                    panXNormalized = panX,
                    panYNormalized = panY
                )

                val result = PassportGridEngine.generatePrintSheet(
                    context = context,
                    croppedPortrait = croppedFull,
                    standard = selectedStandard,
                    paperSize = selectedPaper,
                    copies = selectedCopies,
                    showCutGuides = showCutGuides,
                    exportAsPdf = (exportFormat == ExportFormat.PDF)
                )

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    result.file
                )

                withContext(Dispatchers.Main) {
                    viewModel.setProcessedBitmaps(result.previewBitmap, null)
                    viewModel.updateExportConfig { current ->
                        current.copy(
                            format = exportFormat,
                            exportedFileUri = contentUri,
                            exportedFileName = result.file.name,
                            exportedFileSizeBytes = result.fileSizeBytes
                        )
                    }
                    isGeneratingSheet = false
                    onNavigateToExportShare()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isGeneratingSheet = false
                    Toast.makeText(context, "Generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Passport Photo Grid",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1
            )
        }

        // Live Sheet Preview Card (Pinned at Top, ~240.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.bgElevated,
            border = BorderStroke(1.dp, colors.borderHairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgSubtle)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Smooth pan adjustments
                            panX = (panX + dragAmount.x / 400f).coerceIn(-1f, 1f)
                            panY = (panY + dragAmount.y / 400f).coerceIn(-1f, 1f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingSource) {
                    CircularProgressIndicator(
                        color = colors.accentPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (previewSheet != null) {
                    Image(
                        bitmap = previewSheet.asImageBitmap(),
                        contentDescription = "Passport Sheet Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "Unable to load preview",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Scrollable Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: Interactive Framing & Zoom
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FACE FRAMING & ZOOM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textTertiary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1fx", zoomFactor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (zoomFactor != 1.0f || panX != 0f || panY != 0f) {
                            Text(
                                text = "Reset",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accentSurgical,
                                modifier = Modifier.clickable {
                                    zoomFactor = 1.0f
                                    panX = 0f
                                    panY = 0f
                                }
                            )
                        }
                    }
                }

                Slider(
                    value = zoomFactor,
                    onValueChange = { zoomFactor = it },
                    valueRange = 1.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentPrimary,
                        activeTrackColor = colors.accentPrimary,
                        inactiveTrackColor = colors.borderHairline
                    )
                )

                Text(
                    text = "Drag the sheet preview above to pan and center the face.",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
            }

            // Section 2: Photo Standard (3-Column Equal-Width Segmented Control)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PHOTO STANDARD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val standards = listOf(
                        PassportStandard.STANDARD_35_45,
                        PassportStandard.US_2_2,
                        PassportStandard.STANDARD_30_40
                    )
                    standards.forEach { standard ->
                        val isSelected = selectedStandard == standard
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.bgElevated else colors.bgSubtle)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedStandard = standard }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = standard.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Section 3: Paper Sheet Size (2-Column Equal-Width Segmented Control)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PAPER SHEET SIZE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val papers = listOf(
                        PaperSheetSize.PHOTO_4X6,
                        PaperSheetSize.A4
                    )
                    papers.forEach { paper ->
                        val isSelected = selectedPaper == paper
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.bgElevated else colors.bgSubtle)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedPaper = paper }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = paper.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Section 4: Copies on Sheet (3-Column Equal-Width Segmented Control)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "COPIES ON SHEET",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val copiesList = listOf(
                        GridCopies.FOUR,
                        GridCopies.SIX,
                        GridCopies.EIGHT
                    )
                    copiesList.forEach { copy ->
                        val isSelected = selectedCopies == copy
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.bgElevated else colors.bgSubtle)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedCopies = copy }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = copy.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Section 5: Cut Guides & Export Format
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cut Guides Toggle Switch
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgElevated,
                    border = BorderStroke(1.dp, colors.borderHairline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Scissor Cut Lines",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Subtle 1px guidelines for easy cutting.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = showCutGuides,
                            onCheckedChange = { showCutGuides = it },
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

                // Output Format Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "OUTPUT FORMAT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textTertiary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bgSubtle)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val formats = listOf(
                            ExportFormat.JPG to "JPG (Photo Print)",
                            ExportFormat.PDF to "PDF (Printable Doc)"
                        )
                        formats.forEach { (fmt, label) ->
                            val isSelected = exportFormat == fmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.bgElevated else colors.bgSubtle)
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, colors.borderHairline, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable { exportFormat = fmt }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Fixed Primary CTA Bottom Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgElevated)
                .border(BorderStroke(1.dp, colors.borderHairline))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Button(
                onClick = { handleGenerateSheet() },
                enabled = !isGeneratingSheet && sourceBitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary,
                    disabledContainerColor = colors.bgSubtle,
                    disabledContentColor = colors.textTertiary
                )
            ) {
                if (isGeneratingSheet) {
                    CircularProgressIndicator(
                        color = colors.onAccentPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Generating Sheet...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Generate Print Sheet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

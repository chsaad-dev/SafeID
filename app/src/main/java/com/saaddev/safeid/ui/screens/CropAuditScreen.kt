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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saaddev.safeid.engine.AutoEdgeDetector
import com.saaddev.safeid.engine.ExifPrivacyEngine
import com.saaddev.safeid.engine.NormalizedCornerPoints
import com.saaddev.safeid.engine.NormalizedPoint
import com.saaddev.safeid.engine.PerspectiveAndFilterEngine
import com.saaddev.safeid.engine.PrivacyAuditReport
import com.saaddev.safeid.engine.ScanFilter
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class ActiveSide {
    FRONT,
    BACK
}

private enum class CornerId {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropAuditScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWatermarkStudio: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by viewModel.sessionState.collectAsState()
    val colors = SafeIdTheme.colors

    var activeSide by remember { mutableStateOf(ActiveSide.FRONT) }
    var isProcessing by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }

    // Front Side State
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frontReport by remember { mutableStateOf<PrivacyAuditReport?>(null) }
    var frontCorners by remember { mutableStateOf(NormalizedCornerPoints()) }
    var frontFilter by remember { mutableStateOf(ScanFilter.ORIGINAL) }
    var frontRotation by remember { mutableFloatStateOf(0f) }

    // Back Side State
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backReport by remember { mutableStateOf<PrivacyAuditReport?>(null) }
    var backCorners by remember { mutableStateOf(NormalizedCornerPoints()) }
    var backFilter by remember { mutableStateOf(ScanFilter.ORIGINAL) }
    var backRotation by remember { mutableFloatStateOf(0f) }

    // Load Front Bitmap, Privacy Audit & Auto-Edge Detection
    LaunchedEffect(sessionState.primaryImageUri) {
        val uri = sessionState.primaryImageUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val (report, decoded) = ExifPrivacyEngine.loadAndAudit(context, uri)
            val detectedCorners = if (decoded != null) {
                withContext(Dispatchers.Default) { AutoEdgeDetector.detectDocumentCorners(decoded) }
            } else NormalizedCornerPoints()
            withContext(Dispatchers.Main) {
                frontReport = report
                frontBitmap = decoded
                frontCorners = detectedCorners
                viewModel.setPrimaryPrivacyReport(report)
            }
        }
    }

    // Load Back Bitmap, Privacy Audit & Auto-Edge Detection (if Front + Back mode)
    LaunchedEffect(sessionState.secondaryImageUri) {
        val uri = sessionState.secondaryImageUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val (report, decoded) = ExifPrivacyEngine.loadAndAudit(context, uri)
            val detectedCorners = if (decoded != null) {
                withContext(Dispatchers.Default) { AutoEdgeDetector.detectDocumentCorners(decoded) }
            } else NormalizedCornerPoints()
            withContext(Dispatchers.Main) {
                backReport = report
                backBitmap = decoded
                backCorners = detectedCorners
                viewModel.setSecondaryPrivacyReport(report)
            }
        }
    }

    // Determine current active values based on selected tab
    val currentRawBitmap = if (activeSide == ActiveSide.FRONT) frontBitmap else backBitmap
    val currentReport = if (activeSide == ActiveSide.FRONT) frontReport else backReport
    val currentCorners = if (activeSide == ActiveSide.FRONT) frontCorners else backCorners
    val currentFilter = if (activeSide == ActiveSide.FRONT) frontFilter else backFilter
    val currentRotation = if (activeSide == ActiveSide.FRONT) frontRotation else backRotation

    fun updateCurrentCorners(newCorners: NormalizedCornerPoints) {
        if (activeSide == ActiveSide.FRONT) {
            frontCorners = newCorners
        } else {
            backCorners = newCorners
        }
    }

    fun updateCurrentFilter(filter: ScanFilter) {
        if (activeSide == ActiveSide.FRONT) {
            frontFilter = filter
        } else {
            backFilter = filter
        }
    }

    fun rotateCurrent(clockwise: Boolean) {
        val delta = if (clockwise) 90f else -90f
        if (activeSide == ActiveSide.FRONT) {
            frontRotation = (frontRotation + delta) % 360f
        } else {
            backRotation = (backRotation + delta) % 360f
        }
    }

    fun resetCurrent() {
        if (activeSide == ActiveSide.FRONT) {
            frontCorners = NormalizedCornerPoints()
            frontRotation = 0f
        } else {
            backCorners = NormalizedCornerPoints()
            backRotation = 0f
        }
    }

    fun autoDetectCurrent() {
        val bmp = currentRawBitmap ?: return
        coroutineScope.launch(Dispatchers.Default) {
            val rotated = if (currentRotation != 0f) {
                PerspectiveAndFilterEngine.rotate(bmp, currentRotation)
            } else bmp
            val detected = AutoEdgeDetector.detectDocumentCorners(rotated)
            if (rotated != bmp) rotated.recycle()
            withContext(Dispatchers.Main) {
                updateCurrentCorners(detected)
            }
        }
    }

    // Processing Next: apply perspective warp + filter
    fun processAndAdvance() {
        isProcessing = true
        coroutineScope.launch(Dispatchers.Default) {
            val fRaw = frontBitmap
            val fRotated = if (frontRotation != 0f && fRaw != null) {
                PerspectiveAndFilterEngine.rotate(fRaw, frontRotation)
            } else fRaw

            val fWarped = if (fRotated != null) {
                PerspectiveAndFilterEngine.warpPerspective(fRotated, frontCorners)
            } else null

            val fFinal = if (fWarped != null) {
                PerspectiveAndFilterEngine.applyFilter(fWarped, frontFilter)
            } else null

            var bFinal: Bitmap? = null
            if (sessionState.isFrontBackMode) {
                val bRaw = backBitmap
                val bRotated = if (backRotation != 0f && bRaw != null) {
                    PerspectiveAndFilterEngine.rotate(bRaw, backRotation)
                } else bRaw

                val bWarped = if (bRotated != null) {
                    PerspectiveAndFilterEngine.warpPerspective(bRotated, backCorners)
                } else null

                bFinal = if (bWarped != null) {
                    PerspectiveAndFilterEngine.applyFilter(bWarped, backFilter)
                } else null
            }

            withContext(Dispatchers.Main) {
                viewModel.setProcessedBitmaps(fFinal, bFinal)
                isProcessing = false
                onNavigateToWatermarkStudio()
            }
        }
    }

    // Single-Viewport Non-Scrolling Root
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Minimal Single-Line Top Bar
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
                    text = "Crop & Privacy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { autoDetectCurrent() },
                    enabled = currentRawBitmap != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Auto-Detect",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accentSurgical
                    )
                }

                TextButton(
                    onClick = { resetCurrent() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // 2. Front/Back Segmented Switcher (in Front + Back mode)
        if (sessionState.isFrontBackMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
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
                    // Front Tab
                    val isFront = activeSide == ActiveSide.FRONT
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
                            .clickable { activeSide = ActiveSide.FRONT },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusSafeDot)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Front Side",
                                fontSize = 13.sp,
                                fontWeight = if (isFront) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isFront) colors.textPrimary else colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    // Back Tab
                    val isBack = activeSide == ActiveSide.BACK
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
                            .clickable { activeSide = ActiveSide.BACK },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusSafeDot)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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
            }
        }

        // 3. Compact Privacy Audit Strip (No Loud Green Text)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(enabled = currentReport?.isClean == false) {
                    showAuditDialog = true
                },
            shape = RoundedCornerShape(12.dp),
            color = colors.bgElevated,
            border = BorderStroke(1.dp, colors.borderHairline),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.statusSafeDot)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            currentReport == null -> "Inspecting metadata..."
                            currentReport.isClean -> "Clean File • No EXIF metadata found"
                            else -> "EXIF & GPS Stripped • ${currentReport.strippedTagCount} tags removed"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentReport != null && !currentReport.isClean) {
                    Text(
                        text = "View",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // 4. Crop Canvas Container (Weight 1f, No Black Bars, No Clipped Handles)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgSubtle)
                .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (currentRawBitmap != null) {
                val displayBitmap = remember(currentRawBitmap, currentRotation) {
                    if (currentRotation != 0f) {
                        PerspectiveAndFilterEngine.rotate(currentRawBitmap, currentRotation)
                    } else currentRawBitmap
                }

                PerspectiveCropCanvas(
                    bitmap = displayBitmap,
                    corners = currentCorners,
                    onCornersChanged = { updateCurrentCorners(it) },
                    accentSurgical = colors.accentSurgical,
                    modifier = Modifier.fillMaxSize()
                )

                // Distinct Monochrome Rotation Buttons in Floating Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.bgElevated.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, colors.borderHairline),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { rotateCurrent(clockwise = false) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                                contentDescription = "Rotate 90° Left",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(colors.borderHairline)
                        )
                        IconButton(
                            onClick = { rotateCurrent(clockwise = true) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = "Rotate 90° Right",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.accentSurgical,
                    strokeWidth = 2.5.dp
                )
            }
        }

        // 5. Equal-Width 3-Segment Filter Bar (No Text Truncation)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
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
                val filterOptions = listOf(
                    ScanFilter.ORIGINAL to "Original",
                    ScanFilter.CLEAN_SCAN to "Clean Scan",
                    ScanFilter.COLOR_BOOST to "Color Boost"
                )
                filterOptions.forEach { (filter, label) ->
                    val isSelected = currentFilter == filter
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
                            .clickable { updateCurrentFilter(filter) },
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

        // 6. Full-Width 52dp Primary Action Button
        Button(
            onClick = { processAndAdvance() },
            enabled = !isProcessing && currentRawBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentPrimary,
                contentColor = colors.onAccentPrimary,
                disabledContainerColor = colors.bgSubtle,
                disabledContentColor = colors.textTertiary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.onAccentPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Continue to Watermark",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }

    // Optional Stripped Tags Details Dialog
    if (showAuditDialog && currentReport != null && !currentReport.isClean) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = {
                Text(
                    text = "Stripped Privacy Tags",
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The following metadata was embedded in your photo and stripped on-device:",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentReport.toSummaryChipList().forEach { chip ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.bgSubtle,
                                border = BorderStroke(1.dp, colors.borderHairline)
                            ) {
                                Text(
                                    text = chip,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditDialog = false }) {
                    Text("Done", fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                }
            },
            containerColor = colors.bgElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Interactive 4-Corner Draggable Perspective Canvas with Inset Safe-Zone Padding & Corner Loupe
 */
@Composable
private fun PerspectiveCropCanvas(
    bitmap: Bitmap,
    corners: NormalizedCornerPoints,
    onCornersChanged: (NormalizedCornerPoints) -> Unit,
    accentSurgical: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val internalPaddingPx = with(density) { 16.dp.toPx() }
    val touchHitboxPx = with(density) { 44.dp.toPx() }
    val loupeRadiusPx = with(density) { 48.dp.toPx() }
    val loupeOffsetY = with(density) { 76.dp.toPx() }

    val handleRadiusPx = with(density) { 12.dp.toPx() }
    val handleInnerDotPx = with(density) { 4.5f.dp.toPx() }
    val handleCenterAccentPx = with(density) { 2.dp.toPx() }
    val strokeWidthPx = with(density) { 1.5f.dp.toPx() }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    val currentCorners by rememberUpdatedState(corners)
    val onCornersChangedState by rememberUpdatedState(onCornersChanged)

    var activeCorner by remember { mutableStateOf<CornerId?>(null) }
    var currentTouch by remember { mutableStateOf(Offset.Zero) }

    // Displayed image rectangle computed inside canvas with safe padding
    var imageRect by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    var activeCorners = currentCorners
                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = imageRect
                            val liveCorners = currentCorners
                            activeCorners = liveCorners
                            if (rect.width > 0 && rect.height > 0) {
                                val tl = Offset(rect.left + liveCorners.topLeft.x * rect.width, rect.top + liveCorners.topLeft.y * rect.height)
                                val tr = Offset(rect.left + liveCorners.topRight.x * rect.width, rect.top + liveCorners.topRight.y * rect.height)
                                val br = Offset(rect.left + liveCorners.bottomRight.x * rect.width, rect.top + liveCorners.bottomRight.y * rect.height)
                                val bl = Offset(rect.left + liveCorners.bottomLeft.x * rect.width, rect.top + liveCorners.bottomLeft.y * rect.height)

                                val dTl = hypot(offset.x - tl.x, offset.y - tl.y)
                                val dTr = hypot(offset.x - tr.x, offset.y - tr.y)
                                val dBr = hypot(offset.x - br.x, offset.y - br.y)
                                val dBl = hypot(offset.x - bl.x, offset.y - bl.y)

                                val minDistance = minOf(dTl, dTr, dBr, dBl)
                                if (minDistance <= touchHitboxPx) {
                                    val matchedCorner = when (minDistance) {
                                        dTl -> CornerId.TOP_LEFT
                                        dTr -> CornerId.TOP_RIGHT
                                        dBr -> CornerId.BOTTOM_RIGHT
                                        else -> CornerId.BOTTOM_LEFT
                                    }
                                    activeCorner = matchedCorner
                                    currentTouch = when (matchedCorner) {
                                        CornerId.TOP_LEFT -> tl
                                        CornerId.TOP_RIGHT -> tr
                                        CornerId.BOTTOM_RIGHT -> br
                                        CornerId.BOTTOM_LEFT -> bl
                                    }
                                } else {
                                    activeCorner = null
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val corner = activeCorner
                            val rect = imageRect
                            if (rect.width > 0 && rect.height > 0 && corner != null) {
                                currentTouch += dragAmount
                                val normX = ((currentTouch.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val normY = ((currentTouch.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                val updatedPoint = NormalizedPoint(normX, normY)

                                val newCorners = when (corner) {
                                    CornerId.TOP_LEFT -> activeCorners.copy(topLeft = updatedPoint)
                                    CornerId.TOP_RIGHT -> activeCorners.copy(topRight = updatedPoint)
                                    CornerId.BOTTOM_RIGHT -> activeCorners.copy(bottomRight = updatedPoint)
                                    CornerId.BOTTOM_LEFT -> activeCorners.copy(bottomLeft = updatedPoint)
                                }
                                activeCorners = newCorners
                                onCornersChangedState(newCorners)
                            }
                        },
                        onDragEnd = { activeCorner = null },
                        onDragCancel = { activeCorner = null }
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Calculate aspect-fit image rectangle inside internal padding bounds
            val availW = (canvasW - 2 * internalPaddingPx).coerceAtLeast(1f)
            val availH = (canvasH - 2 * internalPaddingPx).coerceAtLeast(1f)
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val scale = minOf(availW / imgW, availH / imgH)
            val displayW = imgW * scale
            val displayH = imgH * scale
            val left = internalPaddingPx + (availW - displayW) / 2f
            val top = internalPaddingPx + (availH - displayH) / 2f
            val computedRect = Rect(left, top, left + displayW, top + displayH)
            imageRect = computedRect

            // 2. Draw base bitmap
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(computedRect.left.roundToInt(), computedRect.top.roundToInt()),
                dstSize = IntSize(computedRect.width.roundToInt(), computedRect.height.roundToInt())
            )

            // 3. Compute screen corner positions
            val tl = Offset(computedRect.left + corners.topLeft.x * computedRect.width, computedRect.top + corners.topLeft.y * computedRect.height)
            val tr = Offset(computedRect.left + corners.topRight.x * computedRect.width, computedRect.top + corners.topRight.y * computedRect.height)
            val br = Offset(computedRect.left + corners.bottomRight.x * computedRect.width, computedRect.top + corners.bottomRight.y * computedRect.height)
            val bl = Offset(computedRect.left + corners.bottomLeft.x * computedRect.width, computedRect.top + corners.bottomLeft.y * computedRect.height)

            // 4. Construct crop quadrilateral
            val quadPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            // 5. Scrim outside quadrilateral (dim non-selected region)
            clipPath(quadPath, clipOp = ClipOp.Difference) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(computedRect.left, computedRect.top),
                    size = Size(computedRect.width, computedRect.height)
                )
            }

            // 6. Subtle rule-of-thirds grid lines inside quad
            clipPath(quadPath) {
                val dashedStroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
                val gridColor = Color.White.copy(alpha = 0.35f)

                val top1 = Offset(tl.x + (tr.x - tl.x) / 3f, tl.y + (tr.y - tl.y) / 3f)
                val top2 = Offset(tl.x + (tr.x - tl.x) * 2f / 3f, tl.y + (tr.y - tl.y) * 2f / 3f)
                val bot1 = Offset(bl.x + (br.x - bl.x) / 3f, bl.y + (br.y - bl.y) / 3f)
                val bot2 = Offset(bl.x + (br.x - bl.x) * 2f / 3f, bl.y + (br.y - bl.y) * 2f / 3f)
                val left1 = Offset(tl.x + (bl.x - tl.x) / 3f, tl.y + (bl.y - tl.y) / 3f)
                val left2 = Offset(tl.x + (bl.x - tl.x) * 2f / 3f, tl.y + (bl.y - tl.y) * 2f / 3f)
                val right1 = Offset(tr.x + (br.x - tr.x) / 3f, tr.y + (br.y - tr.y) / 3f)
                val right2 = Offset(tr.x + (br.x - tr.x) * 2f / 3f, tr.y + (br.y - tr.y) * 2f / 3f)

                drawLine(gridColor, top1, bot1, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)
                drawLine(gridColor, top2, bot2, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)
                drawLine(gridColor, left1, right1, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)
                drawLine(gridColor, left2, right2, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)
            }

            // 7. Quad boundary stroke in surgical accent
            drawPath(
                path = quadPath,
                color = accentSurgical,
                style = Stroke(width = strokeWidthPx)
            )

            // 8. 4 corner circular handles (24dp outer ring in accentSurgical with white center dot)
            listOf(tl, tr, br, bl).forEach { pt ->
                drawCircle(color = accentSurgical, radius = handleRadiusPx, center = pt)
                drawCircle(color = Color.White, radius = handleInnerDotPx, center = pt)
                drawCircle(color = accentSurgical, radius = handleCenterAccentPx, center = pt)
            }

            // 9. Precision Magnifier Loupe while dragging
            val corner = activeCorner
            if (corner != null) {
                val activeCornerPt = when (corner) {
                    CornerId.TOP_LEFT -> tl
                    CornerId.TOP_RIGHT -> tr
                    CornerId.BOTTOM_RIGHT -> br
                    CornerId.BOTTOM_LEFT -> bl
                }

                var loupeX = currentTouch.x
                var loupeY = currentTouch.y - loupeOffsetY
                if (loupeY - loupeRadiusPx < 0f) {
                    loupeY = currentTouch.y + loupeOffsetY
                }
                loupeX = loupeX.coerceIn(loupeRadiusPx + 8f, canvasW - loupeRadiusPx - 8f)
                val loupeCenter = Offset(loupeX, loupeY)

                val loupePath = Path().apply {
                    addOval(Rect(loupeCenter, loupeRadiusPx))
                }

                // Zoomed preview inside loupe
                clipPath(loupePath) {
                    drawRect(color = Color.Black)
                    withTransform({
                        scale(2.2f, 2.2f, pivot = activeCornerPt)
                        translate(loupeCenter.x - activeCornerPt.x, loupeCenter.y - activeCornerPt.y)
                    }) {
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(computedRect.left.roundToInt(), computedRect.top.roundToInt()),
                            dstSize = IntSize(computedRect.width.roundToInt(), computedRect.height.roundToInt())
                        )
                    }
                }

                // Loupe border rings: crisp white and surgical accent
                drawCircle(
                    color = Color.White,
                    radius = loupeRadiusPx,
                    center = loupeCenter,
                    style = Stroke(width = 2.5f.dp.toPx())
                )
                drawCircle(
                    color = accentSurgical,
                    radius = loupeRadiusPx + 1.dp.toPx(),
                    center = loupeCenter,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Precision crosshairs
                val crosshairLen = 12.dp.toPx()
                drawLine(
                    color = accentSurgical,
                    start = Offset(loupeCenter.x - crosshairLen, loupeCenter.y),
                    end = Offset(loupeCenter.x + crosshairLen, loupeCenter.y),
                    strokeWidth = 1.5f.dp.toPx()
                )
                drawLine(
                    color = accentSurgical,
                    start = Offset(loupeCenter.x, loupeCenter.y - crosshairLen),
                    end = Offset(loupeCenter.x, loupeCenter.y + crosshairLen),
                    strokeWidth = 1.5f.dp.toPx()
                )
            }
        }
    }
}

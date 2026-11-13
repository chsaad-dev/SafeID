package com.saaddev.safeid.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.saaddev.safeid.engine.ExifPrivacyEngine
import com.saaddev.safeid.engine.NormalizedCornerPoints
import com.saaddev.safeid.engine.NormalizedPoint
import com.saaddev.safeid.engine.PerspectiveAndFilterEngine
import com.saaddev.safeid.engine.PrivacyAuditReport
import com.saaddev.safeid.engine.ScanFilter
import com.saaddev.safeid.ui.theme.AlertRed
import com.saaddev.safeid.ui.theme.SafeGreen
import com.saaddev.safeid.ui.theme.Slate700
import com.saaddev.safeid.ui.theme.Teal500
import com.saaddev.safeid.ui.theme.Teal600
import com.saaddev.safeid.ui.theme.Teal700
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CropAuditScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWatermarkStudio: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionState by viewModel.sessionState.collectAsState()

    var activeSide by remember { mutableStateOf(ActiveSide.FRONT) }
    var isProcessing by remember { mutableStateOf(false) }

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

    // Load Front Bitmap & Privacy Audit
    LaunchedEffect(sessionState.primaryImageUri) {
        val uri = sessionState.primaryImageUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val report = ExifPrivacyEngine.inspectPrivacyAudit(context, uri)
            val decoded = ExifPrivacyEngine.decodeSafely(context, uri)
            withContext(Dispatchers.Main) {
                frontReport = report
                frontBitmap = decoded
                viewModel.setPrimaryPrivacyReport(report)
            }
        }
    }

    // Load Back Bitmap & Privacy Audit (if Front + Back mode)
    LaunchedEffect(sessionState.secondaryImageUri) {
        val uri = sessionState.secondaryImageUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val report = ExifPrivacyEngine.inspectPrivacyAudit(context, uri)
            val decoded = ExifPrivacyEngine.decodeSafely(context, uri)
            withContext(Dispatchers.Main) {
                backReport = report
                backBitmap = decoded
                viewModel.setSecondaryPrivacyReport(report)
            }
        }
    }

    // Determine current active values based on selected tab
    val currentUri = if (activeSide == ActiveSide.FRONT) sessionState.primaryImageUri else sessionState.secondaryImageUri
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

    fun resetCurrentCorners() {
        updateCurrentCorners(NormalizedCornerPoints())
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (sessionState.isFrontBackMode) "Crop & Audit (Front + Back)" else "Crop & Privacy Audit",
                        fontWeight = FontWeight.Bold
                    )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Front + Back Tab Row (when in Front+Back mode)
            if (sessionState.isFrontBackMode) {
                PrimaryTabRow(
                    selectedTabIndex = if (activeSide == ActiveSide.FRONT) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = activeSide == ActiveSide.FRONT,
                        onClick = { activeSide = ActiveSide.FRONT },
                        text = {
                            Text(
                                "1. Front Side",
                                fontWeight = if (activeSide == ActiveSide.FRONT) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = activeSide == ActiveSide.BACK,
                        onClick = { activeSide = ActiveSide.BACK },
                        text = {
                            Text(
                                "2. Back Side",
                                fontWeight = if (activeSide == ActiveSide.BACK) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 1. Prominent Privacy Audit Card
            PrivacyAuditCard(report = currentReport)

            // 2. Interactive 4-Corner Cropper Canvas
            if (currentRawBitmap != null) {
                val displayBitmap = remember(currentRawBitmap, currentRotation) {
                    if (currentRotation != 0f) {
                        PerspectiveAndFilterEngine.rotate(currentRawBitmap, currentRotation)
                    } else currentRawBitmap
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Adjust 4 Corner Handles",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row {
                                IconButton(onClick = { rotateCurrent(clockwise = false) }) {
                                    Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate Left", tint = Teal500)
                                }
                                IconButton(onClick = { rotateCurrent(clockwise = true) }) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Right", tint = Teal500)
                                }
                                IconButton(onClick = { resetCurrentCorners() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset Corners", tint = Teal500)
                                }
                            }
                        }

                        // Draggable 4-Corner Perspective Canvas
                        PerspectiveCropCanvas(
                            bitmap = displayBitmap,
                            corners = currentCorners,
                            onCornersChanged = { updateCurrentCorners(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal500)
                }
            }

            // 3. Quick Scan Filter Chips
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Document Enhancement Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanFilter.entries.forEach { filter ->
                            val isSelected = currentFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { updateCurrentFilter(filter) },
                                label = {
                                    Text(
                                        text = when (filter) {
                                            ScanFilter.ORIGINAL -> "Original"
                                            ScanFilter.CLEAN_SCAN -> "Clean Scan (B&W)"
                                            ScanFilter.COLOR_BOOST -> "Color Boost"
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Bottom Button
            Button(
                onClick = { processAndAdvance() },
                enabled = !isProcessing && currentRawBitmap != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal600)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Applying Perspective & Filters...")
                } else {
                    Text(
                        text = "Next: Add Watermark & Size",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

/**
 * Prominent Privacy Audit Card displaying stripped tags
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyAuditCard(report: PrivacyAuditReport?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (report?.isClean == false) AlertRed else SafeGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        report == null -> "Inspecting EXIF & GPS Metadata..."
                        report.isClean -> "Clean File • No EXIF tags found"
                        else -> "Stripped ${report.strippedTagCount} hidden tags"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (report?.isClean == false) AlertRed else SafeGreen
                )
            }

            if (report != null && !report.isClean) {
                Text(
                    text = "GPS locations, device identifiers, and timestamps will never leave your phone:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    report.toSummaryChipList().forEach { chipText ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = chipText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (report != null && report.isClean) {
                Text(
                    text = "No GPS coordinates, device serials, or capture metadata were embedded in this file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Interactive 4-Corner Draggable Perspective Canvas with Corner Magnifier Loupe
 */
@Composable
private fun PerspectiveCropCanvas(
    bitmap: Bitmap,
    corners: NormalizedCornerPoints,
    onCornersChanged: (NormalizedCornerPoints) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val touchHitboxPx = with(density) { 44.dp.toPx() }
    val loupeRadiusPx = with(density) { 48.dp.toPx() }
    val loupeOffsetY = with(density) { 76.dp.toPx() }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    var activeCorner by remember { mutableStateOf<CornerId?>(null) }
    var currentTouch by remember { mutableStateOf(Offset.Zero) }

    // Canvas layout bounds of the displayed image
    var imageRect by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentTouch = offset
                            val rect = imageRect
                            if (rect.width > 0 && rect.height > 0) {
                                val tl = Offset(rect.left + corners.topLeft.x * rect.width, rect.top + corners.topLeft.y * rect.height)
                                val tr = Offset(rect.left + corners.topRight.x * rect.width, rect.top + corners.topRight.y * rect.height)
                                val br = Offset(rect.left + corners.bottomRight.x * rect.width, rect.top + corners.bottomRight.y * rect.height)
                                val bl = Offset(rect.left + corners.bottomLeft.x * rect.width, rect.top + corners.bottomLeft.y * rect.height)

                                val dTl = hypot(offset.x - tl.x, offset.y - tl.y)
                                val dTr = hypot(offset.x - tr.x, offset.y - tr.y)
                                val dBr = hypot(offset.x - br.x, offset.y - br.y)
                                val dBl = hypot(offset.x - bl.x, offset.y - bl.y)

                                val minDistance = minOf(dTl, dTr, dBr, dBl)
                                if (minDistance <= touchHitboxPx) {
                                    activeCorner = when (minDistance) {
                                        dTl -> CornerId.TOP_LEFT
                                        dTr -> CornerId.TOP_RIGHT
                                        dBr -> CornerId.BOTTOM_RIGHT
                                        else -> CornerId.BOTTOM_LEFT
                                    }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentTouch += dragAmount
                            val rect = imageRect
                            val corner = activeCorner
                            if (rect.width > 0 && rect.height > 0 && corner != null) {
                                val normX = ((currentTouch.x - rect.left) / rect.width).coerceIn(0f, 1f)
                                val normY = ((currentTouch.y - rect.top) / rect.height).coerceIn(0f, 1f)
                                val updatedPoint = NormalizedPoint(normX, normY)

                                val newCorners = when (corner) {
                                    CornerId.TOP_LEFT -> corners.copy(topLeft = updatedPoint)
                                    CornerId.TOP_RIGHT -> corners.copy(topRight = updatedPoint)
                                    CornerId.BOTTOM_RIGHT -> corners.copy(bottomRight = updatedPoint)
                                    CornerId.BOTTOM_LEFT -> corners.copy(bottomLeft = updatedPoint)
                                }
                                onCornersChanged(newCorners)
                            }
                        },
                        onDragEnd = { activeCorner = null },
                        onDragCancel = { activeCorner = null }
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Calculate aspect-fit image rectangle
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val scale = minOf(canvasW / imgW, canvasH / imgH)
            val displayW = imgW * scale
            val displayH = imgH * scale
            val left = (canvasW - displayW) / 2f
            val top = (canvasH - displayH) / 2f
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

            // 4. Draw crop polygon
            val quadPath = Path().apply {
                moveTo(tl.x, tl.y)
                lineTo(tr.x, tr.y)
                lineTo(br.x, br.y)
                lineTo(bl.x, bl.y)
                close()
            }

            // Interior tint
            drawPath(path = quadPath, color = Teal500.copy(alpha = 0.15f))

            // Boundary stroke
            drawPath(
                path = quadPath,
                color = Teal500,
                style = Stroke(width = 2.5f.dp.toPx())
            )

            // Subtle rule-of-thirds grid inside quad
            val midTop = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
            val midBottom = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
            val midLeft = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)
            val midRight = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
            val dashedStroke = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            drawLine(Teal500.copy(alpha = 0.4f), midTop, midBottom, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)
            drawLine(Teal500.copy(alpha = 0.4f), midLeft, midRight, strokeWidth = dashedStroke.width, pathEffect = dashedStroke.pathEffect)

            // 5. Draw 4 corner handles
            listOf(tl, tr, br, bl).forEach { pt ->
                drawCircle(color = Teal700, radius = 13.dp.toPx(), center = pt)
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = pt)
                drawCircle(color = Teal500, radius = 4.dp.toPx(), center = pt)
            }

            // 6. Corner Magnifier Loupe while dragging
            val corner = activeCorner
            if (corner != null) {
                val activeCornerPt = when (corner) {
                    CornerId.TOP_LEFT -> tl
                    CornerId.TOP_RIGHT -> tr
                    CornerId.BOTTOM_RIGHT -> br
                    CornerId.BOTTOM_LEFT -> bl
                }

                // Place loupe above touch, clamped so it stays inside canvas
                var loupeX = currentTouch.x
                var loupeY = currentTouch.y - loupeOffsetY
                if (loupeY - loupeRadiusPx < 0f) {
                    loupeY = currentTouch.y + loupeOffsetY // Flip below if too high
                }
                loupeX = loupeX.coerceIn(loupeRadiusPx + 8f, canvasW - loupeRadiusPx - 8f)
                val loupeCenter = Offset(loupeX, loupeY)

                val loupePath = Path().apply {
                    addOval(Rect(loupeCenter, loupeRadiusPx))
                }

                // Draw zoomed image within circular loupe
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

                // Loupe border ring
                drawCircle(
                    color = Color.White,
                    radius = loupeRadiusPx,
                    center = loupeCenter,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Teal500,
                    radius = loupeRadiusPx + 1.dp.toPx(),
                    center = loupeCenter,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Precision crosshair at loupe center
                val crosshairLen = 14.dp.toPx()
                drawLine(
                    color = AlertRed,
                    start = Offset(loupeCenter.x - crosshairLen, loupeCenter.y),
                    end = Offset(loupeCenter.x + crosshairLen, loupeCenter.y),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = AlertRed,
                    start = Offset(loupeCenter.x, loupeCenter.y - crosshairLen),
                    end = Offset(loupeCenter.x, loupeCenter.y + crosshairLen),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

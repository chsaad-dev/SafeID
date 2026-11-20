package com.saaddev.safeid.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saaddev.safeid.ads.InterstitialAdManager
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.engine.ExportAndCompressionEngine
import com.saaddev.safeid.ui.theme.SafeIdTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ExportShareScreen(
    viewModel: SafeIdViewModel,
    onNavigateBack: () -> Unit,
    onResetToHome: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionState by viewModel.sessionState.collectAsState()
    val exportConfig = sessionState.exportConfig
    val colors = SafeIdTheme.colors

    var autoDeleteOnExit by remember { mutableStateOf(exportConfig.autoDeleteCacheOnExit) }
    var isSavingToDownloads by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    // Preload interstitial ad when reaching Export screen
    LaunchedEffect(Unit) {
        InterstitialAdManager.load(context)
    }

    // Try loading preview bitmap from exported file if JPG, or fallback to processedBitmap
    var previewBitmap by remember { mutableStateOf<Bitmap?>(sessionState.processedBitmap) }
    LaunchedEffect(exportConfig.exportedFileUri) {
        val uri = exportConfig.exportedFileUri ?: return@LaunchedEffect
        if (exportConfig.format == ExportFormat.JPG) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        withContext(Dispatchers.Main) {
                            if (bmp != null) previewBitmap = bmp
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Clean exit handler: deletes cache if auto-delete toggle is ON
    fun handleExit(onComplete: () -> Unit) {
        if (autoDeleteOnExit) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val exportDir = File(context.cacheDir, "safeid_exports")
                    if (exportDir.exists()) {
                        exportDir.deleteRecursively()
                    }
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    viewModel.resetSession()
                    onComplete()
                }
            }
        } else {
            viewModel.resetSession()
            onComplete()
        }
    }

    // Share via WhatsApp / Email action
    fun shareExportedFile() {
        val uri = exportConfig.exportedFileUri
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = exportConfig.format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Safe Document"))

            // Trigger interstitial ad only AFTER user taps Share
            (context as? Activity)?.let { activity ->
                InterstitialAdManager.showIfReady(activity)
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Export file is not ready to share.")
            }
        }
    }

    // Save to public Downloads/SafeID/ folder via MediaStore
    fun saveToPublicDownloads() {
        val fileName = exportConfig.exportedFileName ?: return
        val sourceFile = File(context.cacheDir, "safeid_exports/$fileName")
        if (!sourceFile.exists()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Source export file not found.")
            }
            return
        }

        isSavingToDownloads = true
        coroutineScope.launch(Dispatchers.IO) {
            val savedUri = ExportAndCompressionEngine.saveToPublicDownloads(
                context = context,
                sourceFile = sourceFile,
                mimeType = exportConfig.format.mimeType
            )

            withContext(Dispatchers.Main) {
                isSavingToDownloads = false
                if (savedUri != null) {
                    isSaved = true
                    snackbarHostState.showSnackbar("Saved to Downloads/SafeID/$fileName")

                    // Trigger interstitial ad only AFTER user taps Save
                    (context as? Activity)?.let { activity ->
                        InterstitialAdManager.showIfReady(activity)
                    }

                    // Reset checkmark state after 3.5 seconds
                    coroutineScope.launch {
                        delay(3500)
                        isSaved = false
                    }
                } else {
                    snackbarHostState.showSnackbar("Failed to save to Downloads.")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Clean Top Bar with "Done" Exit Action
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
                        onClick = { handleExit { onNavigateBack() } },
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
                        text = "Export & Share",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = { handleExit { onResetToHome() } },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Done",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
            }

            // 2. Scrollable Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Unclipped Summary & Preview Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bgElevated,
                    border = BorderStroke(1.dp, colors.borderHairline),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header: Title with safe icon, filename, and metrics
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.statusSafeDot,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to Share",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = exportConfig.exportedFileName ?: "safeid_export",
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val sizeKb = (exportConfig.exportedFileSizeBytes / 1024f).roundToInt()
                            val formatLabel = if (exportConfig.format == ExportFormat.PDF) "PDF Document" else "JPG Image"
                            val layoutExtra = if (sessionState.isFrontBackMode) "  •  A4 2-Sided" else ""
                            Text(
                                text = "$sizeKb KB  •  $formatLabel$layoutExtra",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                style = TextStyle(fontFeatureSettings = "tnum")
                            )
                        }

                        // Document Preview Frame (bgSubtle background - NO black/navy bars)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bgSubtle)
                                .border(1.dp, colors.borderHairline, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = previewBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Export Document Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PDF Document Ready",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Quiet Verification Footer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderHairline)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusSafeDot)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXIF & GPS stripped  •  Watermark baked",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // Balanced Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Primary Share Button
                    Button(
                        onClick = { shareExportedFile() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = colors.onAccentPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Document",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Secondary Save Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(enabled = !isSavingToDownloads) { saveToPublicDownloads() },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.bgElevated,
                        border = BorderStroke(1.dp, colors.borderHairline),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSavingToDownloads) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.textPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saving to Downloads...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            } else if (isSaved) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.statusSafeDot,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved to Downloads/SafeID",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save to Downloads",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Minimal Auto-Delete Privacy Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.bgElevated,
                    border = BorderStroke(1.dp, colors.borderHairline),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-delete temporary file",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Clears app cache when you leave",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Switch(
                            checked = autoDeleteOnExit,
                            onCheckedChange = { autoDeleteOnExit = it },
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

package com.saaddev.safeid.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saaddev.safeid.ads.InterstitialAdManager
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.engine.ExportAndCompressionEngine
import com.saaddev.safeid.ui.theme.AlertRed
import com.saaddev.safeid.ui.theme.SafeGreen
import com.saaddev.safeid.ui.theme.Slate700
import com.saaddev.safeid.ui.theme.Teal500
import com.saaddev.safeid.ui.theme.Teal600
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    var autoDeleteOnExit by remember { mutableStateOf(exportConfig.autoDeleteCacheOnExit) }
    var isSavingToDownloads by remember { mutableStateOf(false) }

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
                    snackbarHostState.showSnackbar(
                        message = "Saved to Downloads/SafeID/$fileName"
                    )

                    // Trigger interstitial ad only AFTER user taps Save
                    (context as? Activity)?.let { activity ->
                        InterstitialAdManager.showIfReady(activity)
                    }
                } else {
                    snackbarHostState.showSnackbar("Failed to save to Downloads.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Export & Share", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { handleExit { onNavigateBack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Confirmation Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SafeGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to Share Safely",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = exportConfig.exportedFileName ?: "safeid_export",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // Exact Output File Size Badge
                        val sizeKb = (exportConfig.exportedFileSizeBytes / 1024f).roundToInt()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Teal500.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$sizeKb KB • ${exportConfig.format.displayName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Teal500,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Verification Badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SafeGreen.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EXIF & GPS Stripped", fontSize = 11.sp, color = SafeGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Teal500.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Teal500, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Baked Diagonal Watermark", fontSize = 11.sp, color = Teal500, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (sessionState.isFrontBackMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Front + Back A4 Aligned",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Document Preview Card
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Export Document Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (exportConfig.format == ExportFormat.PDF) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("A4 PDF Document", fontWeight = FontWeight.Bold)
                                    Text("Centered at 300 DPI equivalent print proportions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Primary Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Primary Filled Button: Share via WhatsApp / Email
                Button(
                    onClick = { shareExportedFile() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal600)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share via WhatsApp / Email",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary Outlined Button: Save to Downloads
                OutlinedButton(
                    onClick = { saveToPublicDownloads() },
                    enabled = !isSavingToDownloads,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSavingToDownloads) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving to Downloads...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Teal500)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save to Downloads/SafeID/",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider()

            // 3. Privacy Auto-Wipe Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            imageVector = Icons.Default.AutoDelete,
                            contentDescription = null,
                            tint = if (autoDeleteOnExit) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Auto-delete temporary file on exit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Zero residual ID copies left behind in app cache.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = autoDeleteOnExit,
                        onCheckedChange = { autoDeleteOnExit = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SafeGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Done / New Document Button
            Button(
                onClick = { handleExit { onResetToHome() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Done / New Document",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

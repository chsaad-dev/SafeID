package com.saaddev.safeid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.saaddev.safeid.ui.navigation.SafeIdNavGraph
import com.saaddev.safeid.ui.navigation.Screen
import com.saaddev.safeid.ui.theme.SafeIDTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SafeIdViewModel
    private var pendingShareUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SafeIdViewModel::class.java]

        extractIncomingImageUri(intent)?.let { uri ->
            viewModel.setSingleDocument(uri)
            pendingShareUri = uri
        }

        setContent {
            SafeIDTheme {
                val navController = rememberNavController()

                // If launched via system share intent, navigate immediately to CropAudit
                LaunchedEffect(pendingShareUri) {
                    val uri = pendingShareUri
                    if (uri != null) {
                        navController.navigate(Screen.CropAudit.route) {
                            launchSingleTop = true
                        }
                        pendingShareUri = null
                    }
                }

                SafeIdNavGraph(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingImageUri(intent)?.let { uri ->
            viewModel.setSingleDocument(uri)
            pendingShareUri = uri
        }
    }

    private fun extractIncomingImageUri(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val sourceUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: intent.data

            if (sourceUri != null) {
                return try {
                    val exportDir = File(cacheDir, "safeid_exports").apply { mkdirs() }
                    val tempFile = File(exportDir, "incoming_share_temp.jpg")
                    val inputStream = if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
                        val path = sourceUri.path
                        if (path != null) FileInputStream(File(path)) else null
                    } else {
                        contentResolver.openInputStream(sourceUri)
                    }
                    inputStream?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Uri.fromFile(tempFile)
                } catch (e: Exception) {
                    android.util.Log.e("SafeID", "copyIncomingShareStream error", e)
                    sourceUri
                }
            }
        }
        return null
    }
}

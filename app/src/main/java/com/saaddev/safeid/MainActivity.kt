package com.saaddev.safeid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.saaddev.safeid.ui.navigation.SafeIdNavGraph
import com.saaddev.safeid.ui.navigation.Screen
import com.saaddev.safeid.ui.theme.SafeIDTheme
import com.saaddev.safeid.ui.viewmodel.SafeIdViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SafeIdViewModel
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SafeIdViewModel::class.java]

        setContent {
            SafeIDTheme {
                val controller = rememberNavController().also {
                    navController = it
                }

                SafeIdNavGraph(
                    navController = controller,
                    viewModel = viewModel
                )
            }
        }

        handleIncomingSendIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSendIntent(intent)
    }

    private fun handleIncomingSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: intent.data

            imageUri?.let { uri ->
                viewModel.setSingleDocument(uri)
                navController?.navigate(Screen.CropAudit.route) {
                    launchSingleTop = true
                }
            }
        }
    }
}

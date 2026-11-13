package com.saaddev.safeid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SafeIdDarkColorScheme = darkColorScheme(
    primary = Teal500,
    onPrimary = Slate900,
    primaryContainer = Teal700,
    onPrimaryContainer = Slate100,
    secondary = Teal600,
    onSecondary = Slate100,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate800,
    onSurface = Slate100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    outline = Slate600
)

private val SafeIdLightColorScheme = lightColorScheme(
    primary = Teal600,
    onPrimary = Slate100,
    primaryContainer = Teal500,
    onPrimaryContainer = Slate900,
    secondary = Teal700,
    onSecondary = Slate100,
    background = Slate100,
    onBackground = Slate900,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Slate900,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
    onSurfaceVariant = Slate600,
    outline = Slate400
)

@Composable
fun SafeIDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SafeIdDarkColorScheme else SafeIdLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

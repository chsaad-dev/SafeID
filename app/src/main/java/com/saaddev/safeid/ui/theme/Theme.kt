package com.saaddev.safeid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object SafeIdTheme {
    val colors: SafeIdColors
        @Composable
        get() = LocalSafeIdColors.current
}

private val SafeIdLightColorScheme = lightColorScheme(
    primary = LightSafeIdColors.accentPrimary,
    onPrimary = LightSafeIdColors.onAccentPrimary,
    primaryContainer = LightSafeIdColors.bgSubtle,
    onPrimaryContainer = LightSafeIdColors.textPrimary,
    secondary = LightSafeIdColors.accentSurgical,
    onSecondary = LightSafeIdColors.onAccentPrimary,
    background = LightSafeIdColors.bgPrimary,
    onBackground = LightSafeIdColors.textPrimary,
    surface = LightSafeIdColors.bgElevated,
    onSurface = LightSafeIdColors.textPrimary,
    surfaceVariant = LightSafeIdColors.bgSubtle,
    onSurfaceVariant = LightSafeIdColors.textSecondary,
    outline = LightSafeIdColors.borderHairline,
    outlineVariant = LightSafeIdColors.borderHairline
)

private val SafeIdDarkColorScheme = darkColorScheme(
    primary = DarkSafeIdColors.accentPrimary,
    onPrimary = DarkSafeIdColors.onAccentPrimary,
    primaryContainer = DarkSafeIdColors.bgSubtle,
    onPrimaryContainer = DarkSafeIdColors.textPrimary,
    secondary = DarkSafeIdColors.accentSurgical,
    onSecondary = DarkSafeIdColors.onAccentPrimary,
    background = DarkSafeIdColors.bgPrimary,
    onBackground = DarkSafeIdColors.textPrimary,
    surface = DarkSafeIdColors.bgElevated,
    onSurface = DarkSafeIdColors.textPrimary,
    surfaceVariant = DarkSafeIdColors.bgSubtle,
    onSurfaceVariant = DarkSafeIdColors.textSecondary,
    outline = DarkSafeIdColors.borderHairline,
    outlineVariant = DarkSafeIdColors.borderHairline
)

@Composable
fun SafeIDTheme(
    themeMode: ThemeMode = LocalThemeMode.current,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val safeIdColors = if (isDark) DarkSafeIdColors else LightSafeIdColors
    val materialColorScheme = if (isDark) SafeIdDarkColorScheme else SafeIdLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalSafeIdColors provides safeIdColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}

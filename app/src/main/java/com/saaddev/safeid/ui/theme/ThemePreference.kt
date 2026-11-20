package com.saaddev.safeid.ui.theme

import android.content.Context
import androidx.compose.runtime.compositionLocalOf

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemePreference {
    private const val PREFS_NAME = "safeid_theme_preferences"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalThemeModeChanger = compositionLocalOf<(ThemeMode) -> Unit> { {} }

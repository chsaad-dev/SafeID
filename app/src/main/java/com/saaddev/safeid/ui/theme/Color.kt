package com.saaddev.safeid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SafeIdColors(
    val bgPrimary: Color,
    val bgElevated: Color,
    val bgSubtle: Color,
    val borderHairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentPrimary: Color,
    val accentSurgical: Color,
    val onAccentPrimary: Color,
    val statusSafeDot: Color,
    val isDark: Boolean
)

// Light Theme Colors (90% Neutral Canvas / 8% Surface & Hairline Border / 2% Accent)
val LightSafeIdColors = SafeIdColors(
    bgPrimary = Color(0xFFF8F9FA),
    bgElevated = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFF1F3F5),
    borderHairline = Color(0xFFE5E7EB),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    textTertiary = Color(0xFF9CA3AF),
    accentPrimary = Color(0xFF111827),
    accentSurgical = Color(0xFF0D9488),
    onAccentPrimary = Color(0xFFFFFFFF),
    statusSafeDot = Color(0xFF16A34A),
    isDark = false
)

// Dark Theme Colors
val DarkSafeIdColors = SafeIdColors(
    bgPrimary = Color(0xFF0B0D10),
    bgElevated = Color(0xFF14171D),
    bgSubtle = Color(0xFF1C2028),
    borderHairline = Color(0xFF242933),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textTertiary = Color(0xFF6B7280),
    accentPrimary = Color(0xFFF3F4F6),
    accentSurgical = Color(0xFF14B8A6),
    onAccentPrimary = Color(0xFF0B0D10),
    statusSafeDot = Color(0xFF4ADE80),
    isDark = true
)

val LocalSafeIdColors = staticCompositionLocalOf { LightSafeIdColors }

// Backward compatibility colors for existing screens before they are redesigned
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate400 = Color(0xFF94A3B8)
val Slate100 = Color(0xFFF8FAFC)

val Teal600 = Color(0xFF0D9488)
val Teal500 = Color(0xFF14B8A6)
val Teal700 = Color(0xFF0F766E)
val SafeGreen = Color(0xFF10B981)
val AlertRed = Color(0xFFEF4444)

package com.example.sonotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sonotes.data.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF334155),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = Color(0xFF1E293B),
    outlineVariant = Color(0xFF64748B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF0F172A),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF222228),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainer = Color(0xFF1E1E24),
    surfaceContainerLow = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF282830),
    outline = Color(0xFFF1F5F9),
    outlineVariant = Color(0xFF94A3B8)
)

private val BurgundyColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF4A0021),
    primaryContainer = Color(0xFF880E4F),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = Color(0xFFF06292),
    onSecondary = Color(0xFF4A0021),
    secondaryContainer = Color(0xFF5C002B),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = Color(0xFFFF80AB),
    onTertiary = Color(0xFF4A0021),
    background = Color(0xFF1F1019),
    onBackground = Color(0xFFF6EEF2),
    surface = Color(0xFF1F1019),
    onSurface = Color(0xFFF6EEF2),
    surfaceVariant = Color(0xFF331D2A),
    onSurfaceVariant = Color(0xFFD7C2CD),
    surfaceContainer = Color(0xFF2B1823),
    surfaceContainerLow = Color(0xFF25141E),
    surfaceContainerHigh = Color(0xFF38202F),
    outline = Color(0xFFFFB2DD),
    outlineVariant = Color(0xFFF48FB1)
)

@Composable
fun SoNotesTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemDark) AppThemeMode.BLACK_CIRCLE else AppThemeMode.WHITE_CIRCLE
        else -> themeMode
    }

    val colorScheme = when (resolvedTheme) {
        AppThemeMode.WHITE_CIRCLE -> LightColorScheme
        AppThemeMode.BLACK_CIRCLE -> DarkColorScheme
        AppThemeMode.BURGUNDY_CIRCLE -> BurgundyColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

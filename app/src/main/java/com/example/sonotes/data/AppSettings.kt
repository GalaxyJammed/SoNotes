package com.example.sonotes.data

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val displayName: String) {
    SYSTEM("💻 System"),
    LIGHT("☀️ Light"),
    DARK("🌙 Dark")
}

object AppSettings {
    private const val PREFS_NAME = "sonotes_settings"
    private const val KEY_DYSLEXIA_MODE = "dyslexia_mode"
    private const val KEY_STYLUS_COLOR = "stylus_color"
    private const val KEY_THEME_MODE = "theme_mode"

    val DEFAULT_STYLUS_COLORS = listOf(
        Color(0xFF000000), // Classic Black
        Color(0xFF1A237E), // Navy Blue
        Color(0xFFB71C1C), // Crimson Red
        Color(0xFF1B5E20), // Emerald Green
        Color(0xFF4A148C), // Dark Purple
        Color(0xFFFFFFFF)  // White / Light Gray
    )

    fun isDyslexiaModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DYSLEXIA_MODE, true)
    }

    fun setDyslexiaModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DYSLEXIA_MODE, enabled).apply()
    }

    fun getStylusColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_STYLUS_COLOR, android.graphics.Color.BLACK)
    }

    fun setStylusColor(context: Context, colorArgb: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_STYLUS_COLOR, colorArgb).apply()
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try { AppThemeMode.valueOf(name) } catch (_: Exception) { AppThemeMode.SYSTEM }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}

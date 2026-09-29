package com.example.sonotes.data

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val displayName: String) {
    SYSTEM("💻 System"),
    LIGHT("☀️ Light"),
    DARK("🌙 Dark")
}

enum class SortOption(val displayName: String) {
    DATE_CREATED_NEWEST("Date Created (Newest to Oldest)"),
    DATE_CREATED_OLDEST("Date Created (Oldest to Newest)"),
    ALPHABETICAL_AZ("Alphabetical (A to Z)"),
    ALPHABETICAL_ZA("Alphabetical (Z to A)"),
    CONTENT_SIZE_BIGGEST("Content Size (Biggest to Smallest)"),
    CONTENT_SIZE_SMALLEST("Content Size (Smallest to Biggest)"),
    CUSTOM_ORDER("Custom Order")
}

object AppSettings {
    private const val PREFS_NAME = "sonotes_settings"
    private const val KEY_DYSLEXIA_MODE = "dyslexia_mode"
    private const val KEY_STYLUS_COLOR = "stylus_color"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_STYLUS_WIDTH = "stylus_width"
    private const val KEY_SORT_OPTION = "sort_option"
    const val DEFAULT_STYLUS_WIDTH = 1.0f

    val DEFAULT_STYLUS_COLORS = listOf(
        Color(0xFF000000),
        Color(0xFF1A237E),
        Color(0xFFB71C1C),
        Color(0xFF1B5E20),
        Color(0xFF4A148C),
        Color(0xFFFFFFFF)
    )

    fun getSortOption(context: Context): SortOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SORT_OPTION, SortOption.DATE_CREATED_NEWEST.name) ?: SortOption.DATE_CREATED_NEWEST.name
        return try { SortOption.valueOf(name) } catch (_: Exception) { SortOption.DATE_CREATED_NEWEST }
    }

    fun setSortOption(context: Context, option: SortOption) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SORT_OPTION, option.name).apply()
    }

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

    fun getStylusWidth(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_STYLUS_WIDTH, DEFAULT_STYLUS_WIDTH)
    }

    fun setStylusWidth(context: Context, width: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_STYLUS_WIDTH, width).apply()
    }
}

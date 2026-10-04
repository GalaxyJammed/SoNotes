package com.example.sonotes.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System"),
    WHITE_CIRCLE("White Mode"),
    BLACK_CIRCLE("Dark Mode"),
    BURGUNDY_CIRCLE("Burgundy Mode")
}

enum class AppFontOption(val displayName: String, val fontFamily: FontFamily) {
    DEFAULT("Default System", FontFamily.Default),
    SERIF("Classic Serif", FontFamily.Serif),
    CURSIVE("Aesthetic Cursive", FontFamily.Cursive),
    MONOSPACE("Retro Monospace", FontFamily.Monospace),
    SANS_SERIF("Modern Sans", FontFamily.SansSerif)
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

enum class UnlockMethod(val displayName: String) {
    FINGERPRINT("Fingerprint"),
    PIN("PIN")
}

object AppSettings {
    private const val PREFS_NAME = "sonotes_settings"
    private const val KEY_DYSLEXIA_MODE = "dyslexia_mode"
    private const val KEY_STYLUS_COLOR = "stylus_color"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_APP_FONT = "app_font"
    private const val KEY_NOTE_LINES_ENABLED = "note_lines_enabled"
    private const val KEY_STYLUS_WIDTH = "stylus_width"
    private const val KEY_SORT_OPTION = "sort_option"
    private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
    private const val KEY_PIN_ENABLED = "pin_enabled"
    private const val KEY_PIN_CODE = "pin_code"
    private const val KEY_PREFERRED_UNLOCK = "preferred_unlock"
    private const val KEY_AUTO_EXPORT_ENABLED = "auto_export_enabled"
    private const val KEY_AUTO_EXPORT_DAYS = "auto_export_days"

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
        return try {
            AppThemeMode.valueOf(name)
        } catch (_: Exception) {
            when (name) {
                "LIGHT" -> AppThemeMode.WHITE_CIRCLE
                "DARK" -> AppThemeMode.BLACK_CIRCLE
                else -> AppThemeMode.SYSTEM
            }
        }
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

    fun isFingerprintEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
    }

    fun setFingerprintEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    fun isPinEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PIN_ENABLED, false)
    }

    fun setPinEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PIN_ENABLED, enabled).apply()
    }

    fun getPinCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN_CODE, "") ?: ""
    }

    fun setPinCode(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN_CODE, pin).apply()
    }

    fun getPreferredUnlockMethod(context: Context): UnlockMethod {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_PREFERRED_UNLOCK, UnlockMethod.FINGERPRINT.name) ?: UnlockMethod.FINGERPRINT.name
        return try { UnlockMethod.valueOf(name) } catch (_: Exception) { UnlockMethod.FINGERPRINT }
    }

    fun setPreferredUnlockMethod(context: Context, method: UnlockMethod) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFERRED_UNLOCK, method.name).apply()
    }

    fun isAutoExportEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_EXPORT_ENABLED, false)
    }

    fun setAutoExportEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_EXPORT_ENABLED, enabled).apply()
    }

    fun getAutoExportDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AUTO_EXPORT_DAYS, 7)
    }

    fun setAutoExportDays(context: Context, days: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AUTO_EXPORT_DAYS, days).apply()
    }

    fun getAppFont(context: Context): AppFontOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_APP_FONT, AppFontOption.DEFAULT.name) ?: AppFontOption.DEFAULT.name
        return try { AppFontOption.valueOf(name) } catch (_: Exception) { AppFontOption.DEFAULT }
    }

    fun setAppFont(context: Context, font: AppFontOption) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_FONT, font.name).apply()
    }

    fun isNoteLinesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTE_LINES_ENABLED, false)
    }

    fun setNoteLinesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTE_LINES_ENABLED, enabled).apply()
    }
}


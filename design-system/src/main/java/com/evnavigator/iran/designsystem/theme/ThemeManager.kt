package com.evnavigator.iran.designsystem.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supported UI theme modes for EV Navigator Iran.
 * Supports ultra-luxury Dark Glassmorphism, Crystal Light Glassmorphism, and System Auto.
 */
enum class AppThemeMode(val code: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromCode(code: String): AppThemeMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DARK
        }
    }
}

/**
 * Manages runtime and persistent application theme selection (Dark Glass / Light Crystal / System Auto).
 */
object ThemeManager {
    private const val PREFS_NAME = "ev_navigator_theme_prefs"
    private const val KEY_THEME = "selected_theme_mode"

    private val _currentThemeMode = MutableStateFlow(AppThemeMode.DARK)
    val currentThemeMode: StateFlow<AppThemeMode> = _currentThemeMode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        val saved = prefs.getString(KEY_THEME, AppThemeMode.DARK.code) ?: AppThemeMode.DARK.code
        _currentThemeMode.value = AppThemeMode.fromCode(saved)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        if (_currentThemeMode.value == mode) return
        _currentThemeMode.value = mode
        getPrefs(context).edit().putString(KEY_THEME, mode.code).apply()
    }

    fun isDark(mode: AppThemeMode, systemIsDark: Boolean): Boolean {
        return when (mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> systemIsDark
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

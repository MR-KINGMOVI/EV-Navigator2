package com.evnavigator.iran.designsystem.locale

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages runtime and persistent application language selection (Persian / English).
 */
object LocaleManager {
    private const val PREFS_NAME = "ev_navigator_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    private val _currentLanguage = MutableStateFlow(AppLanguage.PERSIAN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        val savedCode = prefs.getString(KEY_LANGUAGE, AppLanguage.PERSIAN.code) ?: AppLanguage.PERSIAN.code
        val language = AppLanguage.fromCode(savedCode)
        _currentLanguage.value = language
        applyLocaleToConfiguration(context, language)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        if (_currentLanguage.value == language) return
        _currentLanguage.value = language
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
        applyLocaleToConfiguration(context, language)
    }

    private fun applyLocaleToConfiguration(context: Context, language: AppLanguage) {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

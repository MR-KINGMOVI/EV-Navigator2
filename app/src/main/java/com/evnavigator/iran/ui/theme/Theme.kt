package com.evnavigator.iran.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.evnavigator.iran.designsystem.locale.AppLanguage
import com.evnavigator.iran.designsystem.locale.AppStrings
import com.evnavigator.iran.designsystem.locale.LocalAppLanguage
import com.evnavigator.iran.designsystem.locale.LocalAppStrings
import com.evnavigator.iran.designsystem.locale.LocaleManager
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.designsystem.tokens.ColorTokens

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = ColorTokens.NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = ColorTokens.CockpitSurfaceElevated,
    onPrimaryContainer = ColorTokens.NeonCyan,
    secondary = ColorTokens.ElectricGreen,
    onSecondary = Color.Black,
    secondaryContainer = ColorTokens.CockpitSurface,
    onSecondaryContainer = ColorTokens.ElectricGreen,
    tertiary = ColorTokens.VoltBlue,
    onTertiary = Color.White,
    background = ColorTokens.CockpitBackground,
    onBackground = ColorTokens.TextPrimaryOnDark,
    surface = ColorTokens.CockpitSurface,
    onSurface = ColorTokens.TextPrimaryOnDark,
    surfaceVariant = ColorTokens.CockpitSurfaceElevated,
    onSurfaceVariant = ColorTokens.TextSecondaryOnDark,
    outline = ColorTokens.CockpitCardBorder,
    outlineVariant = ColorTokens.CockpitCardBorder,
    error = ColorTokens.CrimsonAlert,
    onError = Color.White
)

private val AutomotiveLightColorScheme = lightColorScheme(
    primary = ColorTokens.CyanAccentLight,
    onPrimary = Color.White,
    primaryContainer = ColorTokens.CrystalSurfaceElevated,
    onPrimaryContainer = ColorTokens.CyanAccentLight,
    secondary = Color(0xFF00A854),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF007E33),
    tertiary = Color(0xFF1976D2),
    onTertiary = Color.White,
    background = ColorTokens.CrystalBackground,
    onBackground = ColorTokens.TextPrimaryOnLight,
    surface = ColorTokens.CrystalSurface,
    onSurface = ColorTokens.TextPrimaryOnLight,
    surfaceVariant = ColorTokens.CrystalSurfaceElevated,
    onSurfaceVariant = ColorTokens.TextSecondaryOnLight,
    outline = ColorTokens.CrystalCardBorder,
    outlineVariant = ColorTokens.CrystalCardBorder,
    error = ColorTokens.CrimsonAlert,
    onError = Color.White
)

@Composable
fun EVNavigatorIranTheme(
    content: @Composable () -> Unit
) {
    val currentLanguage by LocaleManager.currentLanguage.collectAsState()
    val currentThemeMode by ThemeManager.currentThemeMode.collectAsState()
    val systemIsDark = isSystemInDarkTheme()
    val isDark = ThemeManager.isDark(currentThemeMode, systemIsDark)

    val colorScheme = if (isDark) AutomotiveDarkColorScheme else AutomotiveLightColorScheme
    val appStrings = AppStrings.forLanguage(currentLanguage)
    val layoutDirection = if (currentLanguage == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides currentLanguage,
        LocalAppStrings provides appStrings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

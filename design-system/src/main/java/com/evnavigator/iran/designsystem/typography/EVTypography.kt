package com.evnavigator.iran.designsystem.typography

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.designsystem.tokens.TypographyTokens

/**
 * EV Navigator specific typography that combines Material3 with EV-specific styles
 */
object EVTypography {
    
    // Base typography using Material3 tokens
    val typography = Typography(
        displayLarge = TypographyTokens.DisplayLarge,
        displayMedium = TypographyTokens.DisplayMedium,
        displaySmall = TypographyTokens.DisplaySmall,
        headlineLarge = TypographyTokens.HeadlineLarge,
        headlineMedium = TypographyTokens.HeadlineMedium,
        headlineSmall = TypographyTokens.HeadlineSmall,
        titleLarge = TypographyTokens.TitleLarge,
        titleMedium = TypographyTokens.TitleMedium,
        titleSmall = TypographyTokens.TitleSmall,
        bodyLarge = TypographyTokens.BodyLarge,
        bodyMedium = TypographyTokens.BodyMedium,
        bodySmall = TypographyTokens.BodySmall,
        labelLarge = TypographyTokens.LabelLarge,
        labelMedium = TypographyTokens.LabelMedium,
        labelSmall = TypographyTokens.LabelSmall
    )
    
    // EV-specific typography styles for special use cases
    val energyValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
    
    val energyUnit = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = ColorTokens.TextSecondary
    )
    
    val temperatureValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
    
    val speedValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
    
    val socValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
    
    val socUnit = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = ColorTokens.TextSecondary
    )
    
    val routeDistance = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
    
    val timeValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = ColorTokens.TextPrimary
    )
}
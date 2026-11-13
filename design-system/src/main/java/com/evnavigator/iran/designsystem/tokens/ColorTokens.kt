package com.evnavigator.iran.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for EV Navigator Iran
 */
object ColorTokens {
    
    // Primary Palette (Automotive Cyberpunk Neon Cyan)
    val Primary = Color(0xFF00E5FF)
    val PrimaryVariant = Color(0xFF2979FF)
    val OnPrimary = Color(0xFF0B0F19)
    
    // Secondary Palette (Electric Emerald)
    val Secondary = Color(0xFF00E676)
    val SecondaryVariant = Color(0xFF00B0FF)
    val OnSecondary = Color(0xFF0B0F19)
    
    // Error Palette
    val Error = Color(0xFFFF1744)
    val ErrorVariant = Color(0xFFD50000)
    val OnError = Color(0xFFFFFFFF)
    
    // Success Palette
    val Success = Color(0xFF00E676)
    val SuccessVariant = Color(0xFF00C853)
    val OnSuccess = Color(0xFF0B0F19)
    
    // Warning Palette
    val Warning = Color(0xFFFFAB00)
    val WarningVariant = Color(0xFFFF8F00)
    val OnWarning = Color(0xFF0B0F19)
    
    // Info Palette
    val Info = Color(0xFF00B0FF)
    val InfoVariant = Color(0xFF0091EA)
    val OnInfo = Color(0xFF0B0F19)
    
    // Background Palette (Deep Obsidian & Graphite Cockpit)
    val BackgroundPrimary = Color(0xFF0B0F19)
    val BackgroundSecondary = Color(0xFF0F172A)
    val OnBackground = Color(0xFFF8FAFC)
    
    // Surface Palette (Frosted Acrylic Dark Glass)
    val Surface = Color(0xFF131B2E)
    val SurfaceElevated = Color(0xFF1C2742)
    val SurfaceVariant = Color(0xFF1E293B)
    val OnSurface = Color(0xFFF8FAFC)
    val OnSurfaceVariant = Color(0xFF94A3B8)
    
    // Text Palette (High Contrast White & Slate)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextDisabled = Color(0xFF475569)
    val TextMuted = Color(0xFF64748B)
    
    // Divider
    val Divider = Color(0x3338BDF8)
    
    // EV-Specific Colors
    val BatteryCharging = Color(0xFF4CAF50) 
    val BatteryFull = Color(0xFF4CAF50)
    val BatteryMedium = Color(0xFFFFC107)
    val BatteryLow = Color(0xFFFF5722)
    val BatteryCritical = Color(0xFFF44336)
    
    // Map Colors
    val RouteLine = Color(0xFF2196F3)
    val RouteLineVariant = Color(0xFF1976D2)
    val ChargerMarker = Color(0xFFFF5722)
    val ChargerMarkerSelected = Color(0xFFF44336)
    
    // Weather Colors
    val WeatherClear = Color(0xFFB3E5FC)
    val WeatherCloudy = Color(0xFFCFD8DC)
    val WeatherRain = Color(0xFF90CAF9)
    val WeatherSnow = Color(0xFFE1F5FE)
    val WeatherStorm = Color(0xFF78909C)
    
    // Traffic Colors
    val TrafficFreeFlow = Color(0xFF4CAF50)
    val TrafficLight = Color(0xFFFFC107)
    val TrafficModerate = Color(0xFFFF9800)
    val TrafficHeavy = Color(0xFFF44336)
    val TrafficSevere = Color(0xFFE91E63)
    
    // Glass/Translucent
    val GlassSurfaceLight = Color(0x80FFFFFF) // 50% opaque white
    val GlassSurfaceDark = Color(0x80000000) // 50% opaque black

    // Automotive Cockpit High-Contrast Dark Palette
    val CockpitBackground = Color(0xFF0B0F19)
    val CockpitSurface = Color(0xFF131B2E)
    val CockpitSurfaceElevated = Color(0xFF1C2742)
    val CockpitCardBorder = Color(0x3338BDF8)
    val NeonCyan = Color(0xFF00E5FF)
    val ElectricGreen = Color(0xFF00E676)
    val VoltBlue = Color(0xFF2979FF)
    val AmberAlert = Color(0xFFFFAB00)
    val CrimsonAlert = Color(0xFFFF1744)
    val EnergyAmber = AmberAlert
    val HighVoltageRed = CrimsonAlert
    val TextPrimaryOnDark = Color(0xFFF8FAFC)
    val TextSecondaryOnDark = Color(0xFF94A3B8)
    val TextMutedOnDark = Color(0xFF64748B)

    // Automotive Daylight High-Contrast Light Crystal Palette
    val CrystalBackground = Color(0xFFF1F5F9) // Slate 100
    val CrystalSurface = Color(0xFFFFFFFF) // Pure white card
    val CrystalSurfaceElevated = Color(0xFFF8FAFC) // Slate 50
    val CrystalGlassCard = Color(0xE6FFFFFF) // 90% frosted white glass
    val CrystalCardBorder = Color(0x330091EA) // Specular sky cyan glass border
    val TextPrimaryOnLight = Color(0xFF0F172A) // Slate 900
    val TextSecondaryOnLight = Color(0xFF475569) // Slate 600
    val TextMutedOnLight = Color(0xFF94A3B8) // Slate 400
    val CyanAccentLight = Color(0xFF0091EA) // High-contrast daylight cyan
}

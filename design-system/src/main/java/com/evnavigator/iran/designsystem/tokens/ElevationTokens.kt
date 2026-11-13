package com.evnavigator.iran.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Elevation tokens for consistent shadow and depth
 */
object ElevationTokens {
    
    // Float levels
    val Level0 = 0.0f
    val Level1 = 1.0f
    val Level2 = 3.0f
    val Level3 = 6.0f
    val Level4 = 8.0f
    val Level5 = 12.0f

    // Elevation levels for different components
    val Elevation0 = 0.dp
    val Elevation1 = 1.dp
    val Elevation2 = 3.dp
    val Elevation3 = 6.dp
    val Elevation4 = 8.dp
    val Elevation5 = 12.dp
    val Elevation6 = 16.dp
    val Elevation8 = 24.dp
    val Elevation24 = 24.dp

    // Component elevations
    val CardElevation = Elevation2
    val SurfaceElevation = Elevation0
    val ElevatedSurfaceElevation = Elevation4
    val BottomSheetElevation = Elevation8
    val DialogElevation = Elevation24
    val TooltipElevation = Elevation1
    val SnackbarElevation = Elevation6
    val FloatingActionButtonElevation = Elevation6
    
    // Navigation bar elevation
    val NavigationBarElevation = Elevation0
    
    // App bar elevation  
    val AppBarElevation = Elevation4
    
    // Glass surface elevations (for depth effect)
    val GlassSurfaceElevation = Elevation1
}

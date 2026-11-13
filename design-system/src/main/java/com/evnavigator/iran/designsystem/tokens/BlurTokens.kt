package com.evnavigator.iran.designsystem.tokens

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.dp

/**
 * Blur and glass effect tokens for frosted glass design
 */
object BlurTokens {
    
    // Blur radius values
    val BlurSmall = 4.dp
    val BlurMedium = 8.dp
    val BlurLarge = 12.dp
    val BlurExtraLarge = 16.dp
    
    // Glass surface blur settings
    val GlassSurfaceBlur = BlurMedium
    val GlassCardBlur = BlurSmall
    val GlassDialogBlur = BlurMedium
    val GlassBottomSheetBlur = BlurMedium
    
    // Compositing strategy for glass effects
    val GlassCompositingStrategy = CompositingStrategy.Offscreen
    
    // Transparency settings for glass
    val GlassSurfaceOpacity = 0.5f
    val GlassCardOpacity = 0.7f
    val GlassDialogOpacity = 0.8f
    val GlassBottomSheetOpacity = 0.8f
    
    // Backdrop blur settings
    val BackdropBlur = BlurMedium
    val BackdropOpacity = 0.4f
}

package com.evnavigator.iran.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens for consistent component corners
 */
object ShapeTokens {
    
    // Extra Small radius
    val ExtraSmall = RoundedCornerShape(2.dp)
    
    // Small radius
    val Small = RoundedCornerShape(4.dp)
    
    // Medium radius
    val Medium = RoundedCornerShape(8.dp)
    
    // Large radius
    val Large = RoundedCornerShape(12.dp)
    
    // Extra Large radius
    val ExtraLarge = RoundedCornerShape(16.dp)
    
    // Full radius (circular)
    val Full = RoundedCornerShape(50)

    // Corner Aliases
    val CornerNone = RoundedCornerShape(0.dp)
    val CornerExtraSmall = ExtraSmall
    val CornerSmall = Small
    val CornerMedium = Medium
    val CornerLarge = Large
    val CornerExtraLarge = ExtraLarge
    val CornerFull = Full
    
    // Custom shapes for specific components
    val Card = Medium
    val Button = Medium
    val Chip = Small
    val TextField = Small
    val Dialog = Large
    val BottomSheet = Large
    val GlassSurface = Medium
    val MapOverlay = Large
    
    // Material3 Shapes equivalent
    val shapes = Shapes(
        extraSmall = ExtraSmall,
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = ExtraLarge
    )
}

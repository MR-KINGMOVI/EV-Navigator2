package com.evnavigator.iran.designsystem.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * EV Icon wrapper for consistent icon usage throughout the app
 */
sealed class EVIcon {
    data class Vector(val imageVector: ImageVector) : EVIcon()
    data class Resource(val resourceId: Int) : EVIcon()
    
    @Composable
    fun asImageVector(): ImageVector? = when (this) {
        is Vector -> imageVector
        is Resource -> null // Will need to be handled differently for resources
    }
}

/**
 * Helper functions for creating EV icons
 */
object EVIconFactory {
    
    /**
     * Create a vector icon from an ImageVector
     */
    fun vector(imageVector: ImageVector): EVIcon = EVIcon.Vector(imageVector)
    
    /**
     * Create a resource icon (placeholder for future implementation)
     */
    fun resource(resourceId: Int): EVIcon = EVIcon.Resource(resourceId)
}
package com.evnavigator.iran.designsystem.tokens

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Animation tokens for consistent motion design
 */
object AnimationTokens {
    
    // Duration constants
    val ShortDuration = 150
    val MediumDuration = 250
    val LongDuration = 300
    val ExtraLongDuration = 500
    
    // Animation specs
    val StandardEasing: Easing = LinearOutSlowInEasing
    val FastOutLinearInEasingSpec: Easing = FastOutLinearInEasing
    
    // Common animations
    val FadeIn = tween<Float>(durationMillis = MediumDuration, easing = StandardEasing)
    val FadeOut = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
    
    // Component transitions
    val CardEnterTransition = tween<Float>(durationMillis = MediumDuration, easing = StandardEasing)
    val CardExitTransition = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
    
    // Navigation transitions
    val ScreenTransitionIn = tween<Float>(durationMillis = MediumDuration, easing = StandardEasing)
    val ScreenTransitionOut = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
    
    // Loading animations
    val CircularProgressIndicator = tween<Float>(durationMillis = 1000)
    
    // Toast/Snackbar animations
    val SnackbarEnter = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
    val SnackbarExit = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
    
    // Glass surface animations
    val GlassSurfaceFadeIn = tween<Float>(durationMillis = MediumDuration, easing = StandardEasing)
    val GlassSurfaceFadeOut = tween<Float>(durationMillis = ShortDuration, easing = StandardEasing)
}

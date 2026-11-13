package com.evnavigator.iran.domain.model

/**
 * Preferences for route calculation.
 *
 * @param avoidTolls Whether to avoid toll roads
 * @param avoidHighways Whether to avoid highways
 * @param avoidFerries Whether to avoid ferries
 * @param preferPrimaryRoads Whether to prefer primary roads
 * @param maxSlope Maximum slope percentage allowed (e.g., 10 for 10%)
 * @param preferredSpeed Preferred speed in kilometers per hour
 */
data class RoutePreferences(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val preferPrimaryRoads: Boolean = false,
    val maxSlope: Double = 15.0, // 15% maximum slope
    val preferredSpeed: Double = 80.0 // 80 km/h preferred speed
)
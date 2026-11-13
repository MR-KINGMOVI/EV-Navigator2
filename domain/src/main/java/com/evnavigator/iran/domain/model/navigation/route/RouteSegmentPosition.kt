package com.evnavigator.iran.domain.model.navigation.route

import kotlinx.serialization.Serializable

/**
 * Position on a specific route segment.
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class RouteSegmentPosition(
    val fraction: Double = 0.0,
    val distanceMeters: Double = 0.0
)

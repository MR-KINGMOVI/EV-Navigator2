package com.evnavigator.iran.core.tripplanner.model

import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Segment within an EV planned trip.
 */
data class TripSegment(
    val id: String,
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val energyWh: Double = 0.0
)

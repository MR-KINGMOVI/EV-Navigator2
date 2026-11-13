package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain-level representation of a route calculation result.
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class RouteResult(
    val id: String,
    val origin: GeoCoordinate,
    val destination: GeoCoordinate,
    val distance: Double,
    val estimatedTime: Double,
    val calculatedAt: Long = System.currentTimeMillis(),
    val waypoints: List<GeoCoordinate> = emptyList()
)

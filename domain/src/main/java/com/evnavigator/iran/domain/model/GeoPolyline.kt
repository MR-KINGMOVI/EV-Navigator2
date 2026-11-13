package com.evnavigator.iran.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a sequence of geographic coordinates forming a polyline path.
 * Pure Kotlin/JVM domain model (independent of Android framework).
 */
@Serializable
data class GeoPolyline(
    val coordinates: List<GeoCoordinate>
) {
    init {
        require(coordinates.isNotEmpty()) { "Coordinates list cannot be empty" }
    }

    constructor(vararg coords: GeoCoordinate) : this(coords.toList())

    val size: Int get() = coordinates.size
    val startCoordinate: GeoCoordinate get() = coordinates.first()
    val endCoordinate: GeoCoordinate get() = coordinates.last()

    companion object {
        fun of(vararg coords: GeoCoordinate): GeoPolyline = GeoPolyline(coords.toList())
    }
}

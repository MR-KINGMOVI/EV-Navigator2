package com.evnavigator.iran.core.routing.model

import com.evnavigator.iran.domain.model.GeoCoordinate

/**
 * Represents a node in the road graph - a point where roads connect
 */
data class RoadNode(
    val id: String,
    val coordinate: GeoCoordinate,
    val elevationMeters: Double? = null
) {
    init {
        require(id.isNotEmpty()) { "RoadNode ID cannot be empty" }
    }
}
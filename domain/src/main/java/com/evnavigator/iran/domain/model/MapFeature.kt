package com.evnavigator.iran.domain.model

/**
 * Represents a map feature.
 *
 * @param id Unique identifier for the feature
 * @param type Type of the feature (e.g., road, building, park)
 * @param geometry Geometry of the feature (point, line, or polygon)
 * @param properties Additional properties of the feature
 */
data class MapFeature(
    val id: String,
    val type: String,
    val geometry: GeoGeometry,
    val properties: Map<String, Any>
)

/**
 * Represents geographic geometry.
 */
sealed class GeoGeometry {
    data class Point(val coordinate: GeoCoordinate) : GeoGeometry()
    data class LineString(val coordinates: List<GeoCoordinate>) : GeoGeometry()
    data class Polygon(val coordinates: List<List<GeoCoordinate>>) : GeoGeometry()
}
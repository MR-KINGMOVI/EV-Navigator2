package com.evnavigator.iran.domain.model

/**
 * Represents a segment of a road.
 */
data class RoadSegment(
    val id: String,
    val start: GeoCoordinate,
    val end: GeoCoordinate,
    val road: Road,
    val distance: Distance,
    val bearing: Bearing,
    val elevationProfile: List<ElevationPoint> = emptyList(),
    val grade: Double, // in percentage, or Double.NaN when unknown
    val isGradeUnknown: Boolean = grade.isNaN(),
    val edgeId: String = id,
    val direction: String = "FORWARD",
    val startOffsetMeters: Double = 0.0,
    val endOffsetMeters: Double = distance.meters
)
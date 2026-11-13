package com.evnavigator.iran.domain.model.navigation

import com.evnavigator.iran.domain.model.Location

/**
 * Represents a location that has been matched to the road network,
 * preserving longitudinal offset along the matched road segment.
 */
data class MatchedLocation(
    /**
     * The original location data.
     */
    val location: Location,

    /**
     * The matched point on the road network.
     */
    val matchedPoint: com.evnavigator.iran.domain.model.Point,

    /**
     * Confidence level of the match (0.0 to 1.0).
     */
    val confidence: Double,

    /**
     * The road segment this location is matched to.
     */
    val roadSegmentId: String,

    /**
     * Longitudinal distance from the start of the matched segment in meters.
     */
    val distanceAlongSegmentMeters: Double = 0.0,

    /**
     * 0-based index of the matched segment along the active route, or -1 if unknown.
     */
    val segmentIndex: Int = -1,

    /**
     * Directed traversal orientation ("FORWARD" or "BACKWARD").
     */
    val direction: String = "FORWARD",

    /**
     * Confidence in GPS/IMU heading (0.0 when stationary/low-speed < 1.5 m/s, 1.0 at highway speed).
     */
    val bearingConfidence: Double = 1.0
)
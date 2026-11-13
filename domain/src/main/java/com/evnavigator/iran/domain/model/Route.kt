package com.evnavigator.iran.domain.model

/**
 * Represents a route between two points.
 *
 * @param id Unique identifier for the route
 * @param start Starting point of the route
 * @param end Ending point of the route
 * @param segments List of route segments
 * @param distance Total distance of the route
 * @param estimatedTime Estimated travel time in seconds
 * @param elevationProfile Elevation profile along the route
 * @param waypoints Ordered polyline/waypoint coordinates along the route
 */
data class Route(
    val id: String,
    val start: GeoCoordinate,
    val end: GeoCoordinate,
    val segments: List<RouteSegment> = emptyList(),
    val distance: Distance,
    val estimatedTime: Long, // in seconds
    val elevationProfile: List<ElevationPoint> = emptyList(),
    val waypoints: List<GeoCoordinate> = emptyList()
) {
    /**
     * Secondary constructor supporting named `startLocation` and `endLocation` arguments.
     */
    constructor(
        id: String,
        startLocation: GeoCoordinate,
        endLocation: GeoCoordinate,
        waypoints: List<GeoCoordinate> = emptyList(),
        distance: Distance,
        estimatedTime: Long,
        segments: List<RouteSegment> = emptyList(),
        elevationProfile: List<ElevationPoint> = emptyList()
    ) : this(
        id = id,
        start = startLocation,
        end = endLocation,
        segments = segments,
        distance = distance,
        estimatedTime = estimatedTime,
        elevationProfile = elevationProfile,
        waypoints = waypoints
    )

    val startLocation: GeoCoordinate
        get() = start

    val endLocation: GeoCoordinate
        get() = end

    /**
     * Gets the total climb of the route.
     */
    val totalClimb: Double
        get() {
            if (elevationProfile.isEmpty()) {
                return segments.sumOf { it.totalClimb }
            }
            var climb = 0.0
            for (i in 1 until elevationProfile.size) {
                val prevElevation = elevationProfile[i - 1].elevation.altitude.value()
                val currentElevation = elevationProfile[i].elevation.altitude.value()
                if (currentElevation > prevElevation) {
                    climb += currentElevation - prevElevation
                }
            }
            return climb
        }

    /**
     * Gets the total descent of the route.
     */
    val totalDescent: Double
        get() {
            if (elevationProfile.isEmpty()) {
                return segments.sumOf { it.totalDescent }
            }
            var descent = 0.0
            for (i in 1 until elevationProfile.size) {
                val prevElevation = elevationProfile[i - 1].elevation.altitude.value()
                val currentElevation = elevationProfile[i].elevation.altitude.value()
                if (currentElevation < prevElevation) {
                    descent += prevElevation - currentElevation
                }
            }
            return descent
        }

    /**
     * Gets the average grade of the route.
     */
    val averageGrade: Double
        get() = if (distance.value > 0) {
            (totalClimb - totalDescent) / distance.value * 100
        } else {
            0.0
        }
}
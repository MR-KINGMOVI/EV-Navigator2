package com.evnavigator.iran.domain.model

/**
 * Summary information about a route.
 *
 * @param distance Total distance of the route
 * @param estimatedTime Estimated travel time in seconds
 * @param totalClimb Total climb along the route in meters
 * @param totalDescent Total descent along the route in meters
 * @param averageGrade Average grade of the route in percentage
 * @param roadTypes Road types encountered on the route
 * @param numberOfSegments Number of segments in the route
 */
data class RouteSummary(
    val distance: Distance,
    val estimatedTime: Long, // in seconds
    val totalClimb: Double, // in meters
    val totalDescent: Double, // in meters
    val averageGrade: Double, // in percentage
    val roadTypes: Set<String>,
    val numberOfSegments: Int
)
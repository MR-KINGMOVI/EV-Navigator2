package com.evnavigator.iran.domain.model

/**
 * Represents a segment of a route with preserved directed edge identity and longitudinal offsets.
 */
data class RouteSegment(
    val id: String,
    val start: GeoCoordinate,
    val end: GeoCoordinate,
    val distance: Distance,
    val bearing: Bearing,
    val elevationProfile: List<ElevationPoint> = emptyList(),
    val roadName: String? = null,
    val roadType: String? = null,
    val edgeId: String = id,
    val direction: String = "FORWARD",
    val startOffsetMeters: Double = 0.0,
    val endOffsetMeters: Double = distance.meters,
    val speedLimitKmh: Int = 0,
    val estimatedAverageSpeedKmh: Double? = null,
    val gradePercent: Double = Double.NaN,
    val isGradeUnknown: Boolean = elevationProfile.size < 2 && gradePercent.isNaN()
) {
    val hasKnownSpeedLimit: Boolean
        get() = speedLimitKmh in 5..140

    val knownSpeedLimitKmhOrNull: Int?
        get() = speedLimitKmh.takeIf { it in 5..140 }
    /**
     * Gets the total climb of this segment.
     */
    val totalClimb: Double
        get() {
            if (elevationProfile.isEmpty()) return 0.0

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
     * Gets the total descent of this segment.
     */
    val totalDescent: Double
        get() {
            if (elevationProfile.isEmpty()) return 0.0

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
     * Gets the average grade of this segment.
     * Returns Double.NaN if elevation data is absent (never silently assumes 0.0).
     */
    val averageGrade: Double
        get() = when {
            !gradePercent.isNaN() -> gradePercent
            elevationProfile.size >= 2 && distance.value > 0 -> (totalClimb - totalDescent) / distance.value * 100.0
            else -> Double.NaN
        }
}
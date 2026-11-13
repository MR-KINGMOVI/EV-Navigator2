package com.evnavigator.iran.core.elevation

import com.evnavigator.iran.core.domain.GeoCoordinate

/**
 * Represents a geographic point with coordinates and elevation
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Represents elevation gain and loss values
 */
data class ElevationGainLoss(
    val totalGain: Float,
    val totalLoss: Float,
    val netElevationChange: Float = totalGain - totalLoss
)

/**
 * Represents elevation data for an area
 */
data class ElevationData(
    val geoPoint: GeoPoint,
    val elevation: Float,
    val slope: Float = 0f,
    val terrainType: TerrainType = TerrainType.UNKNOWN,
    val qualityScore: Float = 1.0f,
    val uncertainty: Float = 0.0f
)

/**
 * Represents a bounding box for geographic area
 */
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * Represents route elevation characteristics
 */
data class RouteElevationCharacteristics(
    val averageElevation: Float,
    val minElevation: Float,
    val maxElevation: Float,
    val totalElevationGain: Float,
    val totalElevationLoss: Float,
    val averageGrade: Double,
    val maximumGrade: Double,
    val minimumGrade: Double,
    val climbSections: List<ClimbDescentSection>,
    val descentSections: List<ClimbDescentSection>
)

/**
 * Represents a climb or descent section
 */
data class ClimbDescentSection(
    val startCoordinate: GeoCoordinate,
    val endCoordinate: GeoCoordinate,
    val lengthMeters: Float,
    val elevationGain: Float,
    val gradePercent: Double,
    val isClimb: Boolean,
    val sectionType: SectionType = SectionType.NORMAL
)

/**
 * Represents minimum and maximum elevation values
 */
data class MinMaxElevation(
    val minElevation: Float,
    val maxElevation: Float,
    val minCoordinate: GeoCoordinate,
    val maxCoordinate: GeoCoordinate
)

/**
 * Enum representing different types of terrain
 */
enum class TerrainType {
    UNKNOWN,
    PLAIN,
    HILL,
    MOUNTAIN,
    VALLEY,
    WATER,
    URBAN,
    FOREST
}

/**
 * Enum representing elevation provenance sources
 */
enum class ElevationProvenance {
    UNKNOWN,
    SRTM30M,      // Shuttle Radar Topography Mission 30m
    SRTM90M,      // Shuttle Radar Topography Mission 90m
    ASTER_GDEM,   // ASTER Global Digital Elevation Model
    MAPBOX,       // Mapbox elevation data
    CUSTOM_API,   // Custom elevation API
    LOCAL_CACHE,  // Cached local data
    SYNTHETIC     // Generated synthetic elevation
}

/**
 * Enum representing vertical datums
 */
enum class VerticalDatum {
    WGS84,
    EGM96,
    EGM2008,
    LOCAL
}

/**
 * Enum representing elevation quality states
 */
enum class ElevationQuality {
    UNKNOWN,      // No elevation data available
    INVALID,      // Invalid elevation data (e.g., NaN)
    KNOWN,        // Known measured elevation
    INTERPOLATED, // Interpolated elevation value
    APPROXIMATED  // Approximated elevation value
}

/**
 * Enum representing section types for climbs/descents
 */
enum class SectionType {
    NORMAL,
    STEEP,
    VERTICAL,
    TUNNEL,
    BRIDGE,
    FERRY
}
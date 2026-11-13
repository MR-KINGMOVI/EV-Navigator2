package com.evnavigator.iran.domain.model.elevation

import com.evnavigator.iran.domain.model.GeoCoordinate
import kotlinx.coroutines.flow.Flow

/**
 * Interface for offline elevation data access and integration.
 *
 * This interface defines the contract for accessing elevation data in offline mode,
 * including sampling, interpolation, and handling missing or invalid samples.
 */
interface ElevationEngine {
    
    /**
     * Gets elevation for a single coordinate using available offline datasets.
     *
     * @param coordinate The geographic coordinate to sample
     * @return Elevation value in meters above sea level or null if not available
     */
    suspend fun getElevation(coordinate: GeoCoordinate): Double?

    /**
     * Gets elevation data for multiple coordinates.
     *
     * @param coordinates List of coordinates to sample
     * @return List of elevation values in meters above sea level, with null for unavailable samples
     */
    suspend fun getElevations(coordinates: List<GeoCoordinate>): List<Double?>

    /**
     * Gets elevation profile along a path between two points.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return ElevationProfile containing elevation data along the path or null if not available
     */
    suspend fun getElevationProfile(start: GeoCoordinate, destination: GeoCoordinate): ElevationProfile?

    /**
     * Gets elevation sampling at regular intervals between two points.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param intervalMeters Distance in meters between samples (default 100m)
     * @return List of elevation data points along the path or empty list if not available
     */
    suspend fun getElevationSampling(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        intervalMeters: Double = 100.0
    ): List<ElevationData>

    /**
     * Gets elevation sampling with interpolation for a given set of coordinates.
     *
     * @param coordinates The list of coordinates to sample
     * @return List of ElevationData objects or empty list if not available
     */
    suspend fun getElevationSampling(
        coordinates: List<GeoCoordinate>
    ): List<ElevationData>

    /**
     * Gets elevation data for a bounding box.
     *
     * @param minLat Minimum latitude
     * @param minLng Minimum longitude
     * @param maxLat Maximum latitude
     * @param maxLng Maximum longitude
     * @return 2D array of elevation values or null if not available
     */
    suspend fun getElevationInBoundingBox(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double
    ): Array<Array<Double?>>?

    /**
     * Gets the current dataset version for elevation data.
     *
     * @return Version string or null if not available
     */
    fun getCurrentDatasetVersion(): String?

    /**
     * Checks if elevation data is available for a specific coordinate.
     *
     * @param coordinate The geographic coordinate to check
     * @return True if elevation data is available, false otherwise
     */
    suspend fun isElevationAvailable(coordinate: GeoCoordinate): Boolean

    /**
     * Gets the resolution of the elevation dataset in meters.
     *
     * @return Resolution in meters or null if not available
     */
    fun getDatasetResolution(): Double?

    /**
     * Gets metadata about the current elevation dataset.
     *
     * @return ElevationDatasetMetadata or null if not available
     */
    suspend fun getDatasetMetadata(): ElevationDatasetMetadata?

    /**
     * Checks if elevation data is currently being processed.
     *
     * @param coordinate The geographic coordinate to check
     * @return True if processing, false otherwise
     */
    suspend fun isProcessing(coordinate: GeoCoordinate): Boolean

    /**
     * Gets the vertical accuracy of elevation measurements.
     *
     * @param coordinate The geographic coordinate to sample
     * @return Vertical accuracy in meters or null if not available
     */
    suspend fun getVerticalAccuracy(coordinate: GeoCoordinate): Double?

    /**
     * Gets elevation data with quality indicators for a specific point.
     *
     * @param coordinat The geographic coordinate to sample
     * @return ElevationDataWithQuality or null if not available
     */
    suspend fun getElevationWithQuality(
        coordinate: GeoCoordinate
    ): ElevationDataWithQuality?

    /**
     * Gets elevation data for a specific area.
     *
     * @param center The center coordinate of the area
     * @param radiusMeters Radius in meters from the center
     * @return List of ElevationData points within the specified radius or empty list if not available
     */
    suspend fun getElevationInArea(
        coordinate: GeoCoordinate,
        radiusMeters: Double
    ): List<ElevationData>

    /**
     * Gets elevation data for a polygon area.
     *
     * @param coordinates The vertices of the polygon
     * @return Elevation data points within the polygon or empty list if not available
     */
    suspend fun getElevationInPolygon(
        coordinates: List<GeoCoordinate>
    ): List<ElevationData>

    /**
     * Gets elevation sampling for a route.
     *
     * @param waypoints The ordered list of waypoints
     * @return Elevation data points along the route or empty list if not available
     */
    suspend fun getElevationForRoute(
        waypoints: List<GeoCoordinate>
    ): List<ElevationData>

    /**
     * Gets elevation sampling for a path with specific constraints.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param maxSamples Maximum number of samples to return (default 100)
     * @return Elevation data points along the path or empty list if not available
     */
    suspend fun getElevationSamplingWithConstraints(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        maxSamples: Int = 100
    ): List<ElevationData>

    /**
     * Gets elevation data with confidence metrics.
     *
     * @param coordinat The geographic coordinate to sample
     * @return ElevationDataWithConfidence or null if not available
     */
    suspend fun getElevationWithConfidence(
        coordinate: GeoCoordinate
    ): ElevationDataWithConfidence?

    /**
     * Gets elevation data for multiple points with quality metrics.
     *
     * @param coordinates The list of coordinates to sample
     * @return List of ElevationDataWithQuality or empty list if not available
     */
    suspend fun getElevationsWithQuality(
        coordinates: List<GeoCoordinate>
    ): List<ElevationDataWithQuality>

    /**
     * Gets elevation data with timestamp information.
     *
     * @param coordinat The geographic coordinate to sample
     * @return TimedElevationData or null if not available
     */
    suspend fun getTimedElevation(
        coordinate: GeoCoordinate
    ): TimedElevationData?

    /**
     * Gets elevation data for a specific time period.
     *
     * @param coordinat The geographic coordinate to sample
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return Elevation data with timestamps or null if not available
     */
    suspend fun getElevationInTimeRange(
        coordinate: GeoCoordinate,
        startTime: Long,
        endTime: Long
    ): List<ElevationData>?

    /**
     * Gets elevation data for a specific coordinate system.
     *
     * @param coordinat The geographic coordinate to sample
     * @param crs Coordinate Reference System identifier (default EPSG:4326)
     * @return Elevation in the specified CRS or null if not available
     */
    suspend fun getElevationInCrs(
        coordinate: GeoCoordinate,
        crs: String = "EPSG:4326"
    ): Double?

    /**
     * Gets elevation data with uncertainty information.
     *
     * @param coordinat The geographic coordinate to sample
     * @return ElevationDataWithUncertainty or null if not available
     */
    suspend fun getElevationWithUncertainty(
        coordinate: GeoCoordinate
    ): ElevationDataWithUncertainty?

    /**
     * Gets elevation data for a grid of points.
     *
     * @param minLat Minimum latitude
     * @param minLng Minimum longitude
     * @param maxLat Maximum latitude
     * @param maxLng Maximum longitude
     * @param gridSize Number of cells in the grid (default 10)
     * @return 2D array of elevation data or null if not available
     */
    suspend fun getElevationGrid(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
        gridSize: Int = 10
    ): Array<Array<ElevationData?>>?

    /**
     * Gets elevation data for a specific point with multiple quality metrics.
     *
     * @param coordinat The geographic coordinate to sample
     * @return ComprehensiveElevationData or null if not available
     */
    suspend fun getComprehensiveElevation(
        coordinate: GeoCoordinate
    ): ComprehensiveElevationData?

    /**
     * Gets elevation data with interpolation method preference.
     *
     * @param coordinat The geographic coordinate to sample
     * @param interpolationMethod Interpolation algorithm to use (linear, spline, etc.)
     * @return Elevation value or null if not available
     */
    suspend fun getElevationWithInterpolation(
        coordinate: GeoCoordinate,
        interpolationMethod: String = "linear"
    ): Double?

    /**
     * Gets elevation data for a path with specific sampling preferences.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param intervalMeters Distance in meters between samples (default 100m)
     * @param interpolationMethod Interpolation algorithm to use
     * @return List of elevation data points or empty list if not available
     */
    suspend fun getElevationSamplingWithPreferences(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        intervalMeters: Double = 100.0,
        interpolationMethod: String = "linear"
    ): List<ElevationData>

    /**
     * Gets elevation data for a specific area with quality metrics.
     *
     * @param center The center coordinate of the area
     * @param radiusMeters Radius in meters from the center
     * @return Elevation data points within the specified radius or empty list if not available
     */
    suspend fun getElevationAreaWithQuality(
        coordinate: GeoCoordinate,
        radiusMeters: Double
    ): List<ElevationDataWithQuality>

    /**
     * Gets elevation data for a polygon area with quality metrics.
     *
     * @param coordinates The vertices of the polygon
     * @return Elevation data points within the polygon or empty list if not available
     */
    suspend fun getElevationPolygonWithQuality(
        coordinates: List<GeoCoordinate>
    ): List<ElevationDataWithQuality>

    /**
     * Gets elevation sampling for a route with quality metrics.
     *
     * @param waypoints The ordered list of waypoints
     * @return Elevation data points along the route or empty list if not available
     */
    suspend fun getElevationRouteWithQuality(
        waypoints: List<GeoCoordinate>
    ): List<ElevationDataWithQuality>

    /**
     * Gets elevation sampling for a path with multiple quality metrics.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param maxSamples Maximum number of samples to return (default 100)
     * @return Elevation data points along the path or empty list if not available
     */
    suspend fun getElevationSamplingWithQuality(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        maxSamples: Int = 100
    ): List<ElevationDataWithQuality>

    /**
     * Gets elevation data for a specific coordinate with multiple metadata fields.
     *
     * @param coordinat The geographic coordinate to sample
     * @return RichElevationData or null if not available
     */
    suspend fun getRichElevation(
        coordinate: GeoCoordinate
    ): RichElevationData?

    /**
     * Gets elevation data for a path with multiple metadata fields.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return List of ElevationDataWithMetadata or empty list if not available
     */
    suspend fun getElevationPathWithMetadata(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation data for a polygon area with metadata.
     *
     * @param coordinates The vertices of the polygon
     * @return Elevation data points within the polygon or empty list if not available
     */
    suspend fun getElevationPolygonWithMetadata(
        coordinates: List<GeoCoordinate>
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation sampling for a route with metadata.
     *
     * @param waypoints The ordered list of waypoints
     * @return Elevation data points along the route or empty list if not available
     */
    suspend fun getElevationRouteWithMetadata(
        waypoints: List<GeoCoordinate>
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation sampling for a path with metadata and quality metrics.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param maxSamples Maximum number of samples to return (default 100)
     * @return Elevation data points along the path or empty list if not available
     */
    suspend fun getElevationSamplingWithMetadataAndQuality(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        maxSamples: Int = 100
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation data for a specific area with metadata and quality metrics.
     *
     * @param center The center coordinate of the area
     * @param radiusMeters Radius in meters from the center
     * @return Elevation data points within the specified radius or empty list if not available
     */
    suspend fun getElevationAreaWithMetadataAndQuality(
        coordinate: GeoCoordinate,
        radiusMeters: Double
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation sampling for a path with metadata, quality metrics and interpolation.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param intervalMeters Distance in meters between samples (default 100m)
     * @return List of ElevationDataWithMetadata or empty list if not available
     */
    suspend fun getElevationSamplingWithMetadataAndInterpolation(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        intervalMeters: Double = 100.0
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation data for a specific coordinate with comprehensive metadata.
     *
     * @param coordinat The geographic coordinate to sample
     * @return ComprehensiveElevationData or null if not available
     */
    suspend fun getComprehensiveElevationData(
        coordinate: GeoCoordinate
    ): ComprehensiveElevationData?

    /**
     * Gets elevation data for a path with comprehensive metadata.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @return List of ElevationDataWithMetadata or empty list if not available
     */
    suspend fun getComprehensivePathElevation(
        start: GeoCoordinate,
        destination: GeoCoordinate
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation data for a polygon area with comprehensive metadata.
     *
     * @param coordinates The vertices of the polygon
     * @return Elevation data points within the polygon or empty list if not available
     */
    suspend fun getComprehensivePolygonElevation(
        coordinates: List<GeoCoordinate>
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation sampling for a route with comprehensive metadata.
     *
     * @param waypoints The ordered list of waypoints
     * @return Elevation data points along the route or empty list if not available
     */
    suspend fun getComprehensiveRouteElevation(
        waypoints: List<GeoCoordinate>
    ): List<ElevationDataWithMetadata>

    /**
     * Gets elevation sampling for a path with comprehensive metadata and interpolation.
     *
     * @param start The starting coordinate
     * @param destination The destination coordinate
     * @param intervalMeters Distance in meters between samples (default 100m)
     * @return List of ElevationDataWithMetadata or empty list if not available
     */
    suspend fun getComprehensiveSamplingWithInterpolation(
        start: GeoCoordinate,
        destination: GeoCoordinate,
        intervalMeters: Double = 100.0
    ): List<ElevationDataWithMetadata>
}

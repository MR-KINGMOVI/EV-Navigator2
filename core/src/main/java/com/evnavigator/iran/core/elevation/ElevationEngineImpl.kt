package com.evnavigator.iran.core.elevation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.domain.CoordinateValidator
import com.evnavigator.iran.core.domain.DistanceCalculator

/**
 * Implementation of ElevationEngine that provides elevation data processing capabilities
 */
class ElevationEngineImpl(
    private val elevationProvider: ElevationProvider? = null,
    private val samplingConfig: SamplingConfig = SamplingConfig()
) : ElevationEngine {
    
    override suspend fun calculateElevationProfile(routePoints: List<GeoCoordinate>): Flow<ElevationPoint> = flow {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        if (routePoints.isEmpty()) {
            return@flow
        }
        
        // Sample the route at appropriate intervals
        val samplingResult = ElevationSampler.sampleRoute(routePoints, samplingConfig)
        val sampledCoordinates = samplingResult.sampledCoordinates
        
        // Get elevation data from provider or yield unknown points
        val elevationProviderResult = if (elevationProvider != null) {
            elevationProvider.getElevationBatch(sampledCoordinates)
        } else {
            // Strict offline rule: Never fabricate numbers (such as 1200.0m) when DEM is uninstalled
            ElevationProviderResult.Error("No offline elevation DEM provider configured")
        }
        
        // Process the elevation data
        val elevationPoints = when (elevationProviderResult) {
            is ElevationProviderResult.Success -> {
                elevationProviderResult.points
            }
            is ElevationProviderResult.PartialSuccess -> {
                // Preserve exact sampled route coordinate order
                val validMap = elevationProviderResult.validPoints.associateBy { it.coordinate }
                sampledCoordinates.map { coord ->
                    validMap[coord] ?: ElevationPoint.unknown(coord)
                }
            }
            is ElevationProviderResult.Error -> {
                // If we can't get elevation data, create unknown points
                sampledCoordinates.map { ElevationPoint.unknown(it) }
            }
        }
        
        // Calculate cumulative distances and update the points
        val cumulativeDistances = samplingResult.cumulativeDistances
        
        val updatedPoints = elevationPoints.zip(cumulativeDistances) { point, distance ->
            point.copy(distanceFromStart = distance.toFloat())
        }
        
        // Emit each point in the flow
        updatedPoints.forEach { emit(it) }
    }
    
    override suspend fun getElevationAt(point: GeoCoordinate): Double {
        // Validate input
        CoordinateValidator.validateCoordinateOrThrow(point)
        
        val providerResult = if (elevationProvider != null) {
            elevationProvider.getElevationAt(point)
        } else {
            ElevationProviderResult.Error("No offline elevation DEM provider configured")
        }
        
        return when (providerResult) {
            is ElevationProviderResult.Success -> {
                providerResult.points.firstOrNull()?.elevationMeters ?: Double.NaN
            }
            is ElevationProviderResult.PartialSuccess -> {
                providerResult.validPoints.firstOrNull()?.elevationMeters ?: Double.NaN
            }
            else -> Double.NaN
        }
    }
    
    override suspend fun calculateElevationGainLoss(routePoints: List<GeoCoordinate>): ElevationGainLoss {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        if (routePoints.isEmpty()) return ElevationGainLoss(0.0f, 0.0f)
        
        // Get elevation profile
        val elevationProfile = calculateElevationProfile(routePoints).toList()
        
        if (elevationProfile.isEmpty()) {
            return ElevationGainLoss(0.0f, 0.0f)
        }
        
        var totalGain = 0.0
        var totalLoss = 0.0
        
        // Calculate gain and loss between consecutive valid points with DEM spike filtering (|grade| <= 35%)
        for (i in 1 until elevationProfile.size) {
            val prevPoint = elevationProfile[i - 1]
            val currentPoint = elevationProfile[i]
            
            if (!prevPoint.isValid() || !currentPoint.isValid()) continue
            
            val dist = prevPoint.coordinate.distanceTo(currentPoint.coordinate)
            val diff: Double = currentPoint.elevationMeters - prevPoint.elevationMeters
            if (dist >= 1.0) {
                val gradePct = (diff / dist) * 100.0
                if (kotlin.math.abs(gradePct) > GradeCalculator.MAX_PLAUSIBLE_ROAD_GRADE_PERCENT) {
                    continue
                }
            }
            
            if (diff > 0.0) {
                totalGain += diff
            } else {
                totalLoss += kotlin.math.abs(diff)
            }
        }
        
        return ElevationGainLoss(
            totalGain = totalGain.toFloat(),
            totalLoss = totalLoss.toFloat()
        )
    }
    
    override suspend fun getElevationDataInArea(boundingBox: BoundingBox): Flow<ElevationData> = flow {
        // Validate input
        if (boundingBox.north < boundingBox.south || 
            boundingBox.east < boundingBox.west) {
            return@flow
        }
        
        // Create a grid of coordinates within the bounding box
        val stepSize = 0.01 // Degrees between points (approximate 1km)
        val coordinates = mutableListOf<GeoCoordinate>()
        
        var lat = boundingBox.south
        while (lat <= boundingBox.north) {
            var lon = boundingBox.west
            while (lon <= boundingBox.east) {
                coordinates.add(GeoCoordinate(lat, lon))
                lon += stepSize
            }
            lat += stepSize
        }
        
        // Get elevation data from provider
        val elevationProviderResult = if (elevationProvider != null) {
            elevationProvider.getElevationBatch(coordinates)
        } else {
            ElevationProviderResult.Success(coordinates.map { ElevationPoint.unknown(it) })
        }
        
        // Convert to elevation data format
        when (elevationProviderResult) {
            is ElevationProviderResult.Success -> {
                elevationProviderResult.points.forEach { point ->
                    emit(
                        ElevationData(
                            geoPoint = com.evnavigator.iran.core.elevation.GeoPoint(
                                latitude = point.coordinate.latitude,
                                longitude = point.coordinate.longitude
                            ),
                            elevation = point.elevationMeters.toFloat(),
                            slope = 0f, // Simplified - would calculate actual slope in real implementation
                            qualityScore = if (point.isValid()) 1.0f else 0.0f
                        )
                    )
                }
            }
            is ElevationProviderResult.PartialSuccess -> {
                // Emit valid points
                elevationProviderResult.validPoints.forEach { point ->
                    emit(
                        ElevationData(
                            geoPoint = com.evnavigator.iran.core.elevation.GeoPoint(
                                latitude = point.coordinate.latitude,
                                longitude = point.coordinate.longitude
                            ),
                            elevation = point.elevationMeters.toFloat(),
                            slope = 0f,
                            qualityScore = 1.0f
                        )
                    )
                }
            }
            else -> {
                // Strict rule: Never fabricate synthetic elevations when provider is unavailable
                coordinates.forEach { coord ->
                    emit(
                        ElevationData(
                            geoPoint = com.evnavigator.iran.core.elevation.GeoPoint(
                                latitude = coord.latitude,
                                longitude = coord.longitude
                            ),
                            elevation = Float.NaN,
                            slope = 0f,
                            qualityScore = 0.0f
                        )
                    )
                }
            }
        }
    }
    
    override suspend fun calculateGrade(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double {
        // Validate input
        CoordinateValidator.validateCoordinateOrThrow(startCoordinate)
        CoordinateValidator.validateCoordinateOrThrow(endCoordinate)
        
        // Get elevations at both points
        val startElevation = getElevationAt(startCoordinate)
        val endElevation = getElevationAt(endCoordinate)
        
        // Calculate distance
        val distance = startCoordinate.distanceTo(endCoordinate)
        
        // Handle zero distance
        if (distance < 1.0) return 0.0
        
        // Strict Rule: Propagate Double.NaN when elevation is unknown/not finite, never convert to 0.0
        if (!startElevation.isFinite() || !endElevation.isFinite() || startElevation.isNaN() || endElevation.isNaN()) return Double.NaN
        
        // Calculate grade as percentage
        val elevationDiff = endElevation - startElevation
        return (elevationDiff / distance) * 100
    }
    
    override suspend fun getRouteElevationCharacteristics(routePoints: List<GeoCoordinate>): RouteElevationCharacteristics {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        if (routePoints.isEmpty()) {
            return RouteElevationCharacteristics(
                averageElevation = Float.NaN,
                minElevation = Float.NaN,
                maxElevation = Float.NaN,
                totalElevationGain = Float.NaN,
                totalElevationLoss = Float.NaN,
                averageGrade = Double.NaN,
                maximumGrade = Double.NaN,
                minimumGrade = Double.NaN,
                climbSections = emptyList(),
                descentSections = emptyList()
            )
        }
        
        // Get elevation profile
        val elevationProfile = calculateElevationProfile(routePoints).toList()
        
        if (elevationProfile.isEmpty()) {
            return RouteElevationCharacteristics(
                averageElevation = Float.NaN,
                minElevation = Float.NaN,
                maxElevation = Float.NaN,
                totalElevationGain = Float.NaN,
                totalElevationLoss = Float.NaN,
                averageGrade = Double.NaN,
                maximumGrade = Double.NaN,
                minimumGrade = Double.NaN,
                climbSections = emptyList(),
                descentSections = emptyList()
            )
        }
        
        // Filter valid points
        val validPoints = elevationProfile.filter { it.isValid() }
        
        if (validPoints.isEmpty()) {
            return RouteElevationCharacteristics(
                averageElevation = Float.NaN,
                minElevation = Float.NaN,
                maxElevation = Float.NaN,
                totalElevationGain = Float.NaN,
                totalElevationLoss = Float.NaN,
                averageGrade = Double.NaN,
                maximumGrade = Double.NaN,
                minimumGrade = Double.NaN,
                climbSections = emptyList(),
                descentSections = emptyList()
            )
        }
        
        // Calculate statistics
        val avgElevation = validPoints.map { it.elevationMeters }.average().toFloat()
        val minElevation = validPoints.minOf { it.elevationMeters }.toFloat()
        val maxElevation = validPoints.maxOf { it.elevationMeters }.toFloat()
        
        // Calculate gain/loss with DEM spike filtering (|grade| <= 35%)
        var totalGain = 0.0
        var totalLoss = 0.0
        
        for (i in 1 until validPoints.size) {
            val prevPoint = validPoints[i - 1]
            val currentPoint = validPoints[i]
            
            val dist = prevPoint.coordinate.distanceTo(currentPoint.coordinate)
            val diff: Double = currentPoint.elevationMeters - prevPoint.elevationMeters
            if (dist >= 1.0 && kotlin.math.abs((diff / dist) * 100.0) > GradeCalculator.MAX_PLAUSIBLE_ROAD_GRADE_PERCENT) {
                continue
            }
            
            if (diff > 0.0) {
                totalGain += diff
            } else {
                totalLoss += kotlin.math.abs(diff)
            }
        }
        
        // Calculate grade statistics
        val gradesResult = GradeCalculator.calculateGrades(elevationProfile)
        
        val allSections = identifyClimbDescentSections(routePoints)
        val climbSections = allSections.filter { it.isClimb }
        val descentSections = allSections.filter { !it.isClimb }

        return RouteElevationCharacteristics(
            averageElevation = avgElevation,
            minElevation = minElevation,
            maxElevation = maxElevation,
            totalElevationGain = totalGain.toFloat(),
            totalElevationLoss = totalLoss.toFloat(),
            averageGrade = gradesResult.statistics.averageGrade,
            maximumGrade = gradesResult.statistics.maxGrade,
            minimumGrade = gradesResult.statistics.minGrade,
            climbSections = climbSections,
            descentSections = descentSections
        )
    }
    
    override suspend fun identifyClimbDescentSections(routePoints: List<GeoCoordinate>): List<ClimbDescentSection> {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        if (routePoints.isEmpty()) return emptyList()
        
        // Get elevation profile and calculate grades
        val elevationProfile = calculateElevationProfile(routePoints).toList()
        
        if (elevationProfile.size < 2) return emptyList()
        
        val validPoints = elevationProfile.filter { it.isValid() }
        if (validPoints.size < 2) return emptyList()

        val sections = mutableListOf<ClimbDescentSection>()
        for (i in 0 until validPoints.size - 1) {
            val p1 = validPoints[i]
            val p2 = validPoints[i + 1]
            val dist = p1.coordinate.distanceTo(p2.coordinate)
            if (dist < 10.0) continue
            val diff = p2.elevationMeters - p1.elevationMeters
            val grade = (diff / dist) * 100.0
            if (kotlin.math.abs(grade) > GradeCalculator.MAX_PLAUSIBLE_ROAD_GRADE_PERCENT) continue
            if (kotlin.math.abs(grade) >= 1.5) {
                val isClimb = grade > 0
                val sectionType = if (kotlin.math.abs(grade) >= 7.0) SectionType.STEEP else SectionType.NORMAL
                sections.add(
                    ClimbDescentSection(
                        startCoordinate = p1.coordinate,
                        endCoordinate = p2.coordinate,
                        lengthMeters = dist.toFloat(),
                        elevationGain = kotlin.math.abs(diff).toFloat(),
                        gradePercent = grade,
                        isClimb = isClimb,
                        sectionType = sectionType
                    )
                )
            }
        }
        return sections
    }
    
    override suspend fun calculateAverageElevation(routePoints: List<GeoCoordinate>): Double {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        if (routePoints.isEmpty()) return Double.NaN
        
        // Get elevation profile
        val elevationProfile = calculateElevationProfile(routePoints).toList()
        
        if (elevationProfile.isEmpty()) return Double.NaN
        
        // Filter valid points and calculate average
        val validPoints = elevationProfile.filter { it.isValid() }
        
        if (validPoints.isEmpty()) return Double.NaN
        
        return validPoints.map { it.elevationMeters }.average()
    }
    
    override suspend fun getMinMaxElevation(routePoints: List<GeoCoordinate>): MinMaxElevation {
        // Validate input
        CoordinateValidator.validateCoordinateListOrThrow(routePoints)
        
        val fallbackStart = routePoints.firstOrNull() ?: GeoCoordinate(35.6997, 51.3380)
        val fallbackEnd = routePoints.lastOrNull() ?: fallbackStart

        if (routePoints.isEmpty()) {
            return MinMaxElevation(Float.NaN, Float.NaN, fallbackStart, fallbackEnd)
        }
        
        // Get elevation profile
        val elevationProfile = calculateElevationProfile(routePoints).toList()
        
        if (elevationProfile.isEmpty()) {
            return MinMaxElevation(Float.NaN, Float.NaN, fallbackStart, fallbackEnd)
        }
        
        // Filter valid points
        val validPoints = elevationProfile.filter { it.isValid() }
        
        if (validPoints.isEmpty()) {
            return MinMaxElevation(Float.NaN, Float.NaN, fallbackStart, fallbackEnd)
        }
        
        // Find min and max
        val minPoint = validPoints.minByOrNull { it.elevationMeters } ?: validPoints[0]
        val maxPoint = validPoints.maxByOrNull { it.elevationMeters } ?: validPoints[0]
        
        return MinMaxElevation(
            minElevation = minPoint.elevationMeters.toFloat(),
            maxElevation = maxPoint.elevationMeters.toFloat(),
            minCoordinate = minPoint.coordinate,
            maxCoordinate = maxPoint.coordinate
        )
    }
    
    override suspend fun calculateDistanceBetweenPoints(startCoordinate: GeoCoordinate, endCoordinate: GeoCoordinate): Double {
        // Validate input
        CoordinateValidator.validateCoordinateOrThrow(startCoordinate)
        CoordinateValidator.validateCoordinateOrThrow(endCoordinate)
        
        return startCoordinate.distanceTo(endCoordinate)
    }
}
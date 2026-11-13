package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.WeatherObservation
import com.evnavigator.iran.core.weather.model.ForecastWeatherPoint
import com.evnavigator.iran.core.weather.model.TrafficIncident
import com.evnavigator.iran.core.weather.model.TrafficObservation
import com.evnavigator.iran.core.weather.model.TrafficClass
import java.time.Instant

data class WeatherEnvironmentContext(
    val observation: WeatherObservation? = null,
    val provenance: Any? = null
)

data class TrafficEnvironmentContext(
    val observation: TrafficObservation? = null,
    val provenance: Any? = null
)

/**
 * Represents the environmental context for a route, combining weather and traffic data
 * for each segment along the route.
 *
 * This component provides a unified view of route environment conditions that can be
 * consumed by downstream systems like energy calculation, trip planning, and navigation.
 */
data class RouteEnvironmentProfile(
    val segments: List<RouteSegmentEnvironment> = emptyList(),
    val startTime: Instant = Instant.now(),
    val endTime: Instant = Instant.now(),
    val totalDistance: Double = 0.0,
    val totalTimeEstimate: java.time.Duration = java.time.Duration.ZERO,
    val confidence: Double = 0.5,
    val uncertainty: Double = 0.5,
    val provenance: String? = null
)

data class RouteEnvironmentContext(
    val segments: List<RouteSegmentEnvironment>,
    val confidence: Double, // Overall confidence in the environmental context (0.0 to 1.0)
    val uncertainty: Double, // Overall uncertainty in the environmental context (0.0 to 1.0)
    val freshness: Freshness, // Freshness of the overall environmental data
    val provenance: String // Source or method used to create this context
) {
    constructor(
        segmentStartCoordinate: GeoCoordinate,
        segmentEndCoordinate: GeoCoordinate,
        weatherContext: WeatherEnvironmentContext?,
        trafficContext: TrafficEnvironmentContext?
    ) : this(
        segments = listOf(
            RouteSegmentEnvironment(
                segmentIndex = 0,
                segmentStartCoordinate = segmentStartCoordinate,
                segmentEndCoordinate = segmentEndCoordinate,
                weatherContext = weatherContext,
                trafficContext = trafficContext
            )
        ),
        confidence = 0.9,
        uncertainty = 0.1,
        freshness = Freshness.FRESH,
        provenance = "test"
    )

    companion object {
        /**
         * Creates an empty route environment context with no segments
         */
        fun empty(): RouteEnvironmentContext {
            return RouteEnvironmentContext(
                segments = emptyList(),
                confidence = 0.0,
                uncertainty = 1.0,
                freshness = Freshness.UNKNOWN,
                provenance = "Empty"
            )
        }
    }
}

val RouteEnvironmentContext.weatherContext: WeatherContext?
    get() = segments.firstOrNull()?.weatherContext

val RouteEnvironmentContext.trafficContext: TrafficDataForSegment?
    get() = segments.firstOrNull()?.trafficContext

/**
 * Represents the environmental context for a specific route segment
 */
data class RouteSegmentEnvironment(
    val segment: RouteSegment,
    val weatherContext: WeatherContext?,
    val trafficContext: TrafficDataForSegment?,
    val timestamp: Instant, // When this environment was calculated/observed
    val confidence: Double, // Confidence in this segment's environmental data (0.0 to 1.0)
    val uncertainty: Double, // Uncertainty in this segment's environmental data (0.0 to 1.0)
    val freshness: Freshness, // Freshness of the data for this segment
    val provenance: String // Source or method used to create this context
) {
    constructor(
        segmentIndex: Int,
        segmentStartCoordinate: GeoCoordinate,
        segmentEndCoordinate: GeoCoordinate,
        weatherContext: WeatherEnvironmentContext?,
        trafficContext: TrafficEnvironmentContext?
    ) : this(
        segment = RouteSegment(id = "seg_$segmentIndex", distance = 1000.0, duration = 120.0),
        weatherContext = WeatherContext(observation = weatherContext?.observation),
        trafficContext = TrafficDataForSegment(
            trafficClass = trafficContext?.observation?.trafficClass ?: TrafficClass.FREE_FLOW,
            speed = null,
            freeFlowSpeed = null,
            congestionRatio = null,
            travelTimeMultiplier = null,
            incidents = emptyList(),
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = Freshness.FRESH,
            observation = trafficContext?.observation
        ),
        timestamp = Instant.now(),
        confidence = 0.9,
        uncertainty = 0.1,
        freshness = Freshness.FRESH,
        provenance = "test"
    )
    companion object {
        /**
         * Creates an unknown segment environment with no weather or traffic data
         */
        fun unknown(segment: RouteSegment): RouteSegmentEnvironment {
            return RouteSegmentEnvironment(
                segment = segment,
                weatherContext = null,
                trafficContext = null,
                timestamp = Instant.now(),
                confidence = 0.0,
                uncertainty = 1.0,
                freshness = Freshness.UNKNOWN,
                provenance = "Unknown"
            )
        }
    }
}

/**
 * Weather context for a specific location and time
 */
data class WeatherContext(
    val observation: WeatherObservation? = null,
    val forecast: List<ForecastWeatherPoint>? = null, // Forecast points that might apply to this segment
    val confidence: Double = 0.5, // Confidence in the weather data (0.0 to 1.0)
    val uncertainty: Double = 0.5, // Uncertainty in the weather data (0.0 to 1.0)
    val freshness: Freshness = Freshness.FRESH, // Freshness of the weather data
    val provenance: String = "unknown" // Source or method used to create this context
) {
    companion object {
        /**
         * Creates an unknown weather context
         */
        fun unknown(): WeatherContext {
            return WeatherContext(
                observation = null,
                forecast = null,
                confidence = 0.0,
                uncertainty = 1.0,
                freshness = Freshness.UNKNOWN,
                provenance = "Unknown"
            )
        }
    }
}

/**
 * Creates a unified route environment profile from separate weather and traffic data
 *
 * @param routeSegments List of route segments to create environment context for
 * @param weatherData Map of segment to weather context (may be null)
 * @param trafficData Map of segment to traffic data
 * @return Unified route environment context
 */
fun createRouteEnvironmentContext(
    routeSegments: List<RouteSegment>,
    weatherData: Map<RouteSegment, WeatherContext>?,
    trafficData: Map<RouteSegment, TrafficDataForSegment>
): RouteEnvironmentContext {
    
    val segments = routeSegments.map { segment ->
        val weatherContext = weatherData?.get(segment)
        val trafficContext = trafficData[segment]
        
        RouteSegmentEnvironment(
            segment = segment,
            weatherContext = weatherContext,
            trafficContext = trafficContext,
            timestamp = Instant.now(),
            confidence = calculateSegmentConfidence(weatherContext, trafficContext),
            uncertainty = calculateSegmentUncertainty(weatherContext, trafficContext),
            freshness = determineSegmentFreshness(weatherContext, trafficContext),
            provenance = "Combined from Weather and Traffic Data"
        )
    }
    
    // Calculate overall confidence and uncertainty for the entire route
    val overallConfidence = if (segments.isNotEmpty()) {
        segments.map { it.confidence }.average()
    } else {
        0.0
    }
    
    val overallUncertainty = if (segments.isNotEmpty()) {
        segments.map { it.uncertainty }.average()
    } else {
        1.0
    }
    
    // Determine overall freshness based on the freshest segment
    val overallFreshness = if (segments.isNotEmpty()) {
        determineOverallFreshness(segments)
    } else {
        Freshness.UNKNOWN
    }
    
    return RouteEnvironmentContext(
        segments = segments,
        confidence = overallConfidence,
        uncertainty = overallUncertainty,
        freshness = overallFreshness,
        provenance = "RouteEnvironmentContextBuilder"
    )
}

/**
 * Calculates the confidence for a segment based on its weather and traffic data
 */
private fun calculateSegmentConfidence(
    weatherContext: WeatherContext?,
    trafficContext: TrafficDataForSegment?
): Double {
    val weatherConfidence = weatherContext?.confidence ?: 0.0
    val trafficConfidence = trafficContext?.confidence ?: 0.0
    
    // Return the average confidence or 0 if both are missing
    return when {
        weatherContext == null && trafficContext == null -> 0.0
        weatherContext == null -> trafficConfidence
        trafficContext == null -> weatherConfidence
        else -> (weatherConfidence + trafficConfidence) / 2.0
    }
}

/**
 * Calculates the uncertainty for a segment based on its weather and traffic data
 */
private fun calculateSegmentUncertainty(
    weatherContext: WeatherContext?,
    trafficContext: TrafficDataForSegment?
): Double {
    val weatherUncertainty = weatherContext?.uncertainty ?: 1.0
    val trafficUncertainty = trafficContext?.uncertainty ?: 1.0
    
    // Return the average uncertainty or 1.0 if both are missing
    return when {
        weatherContext == null && trafficContext == null -> 1.0
        weatherContext == null -> trafficUncertainty
        trafficContext == null -> weatherUncertainty
        else -> (weatherUncertainty + trafficUncertainty) / 2.0
    }
}

/**
 * Determines the freshness for a segment based on its weather and traffic data
 */
private fun determineSegmentFreshness(
    weatherContext: WeatherContext?,
    trafficContext: TrafficDataForSegment?
): Freshness {
    val weatherFreshness = weatherContext?.freshness ?: Freshness.UNKNOWN
    val trafficFreshness = trafficContext?.freshness ?: Freshness.UNKNOWN
    
    // Determine the freshest state - FRESH > STALE_BUT_USABLE > EXPIRED > INVALID > UNKNOWN
    return when {
        weatherFreshness == Freshness.FRESH || trafficFreshness == Freshness.FRESH -> Freshness.FRESH
        weatherFreshness == Freshness.STALE_BUT_USABLE || trafficFreshness == Freshness.STALE_BUT_USABLE -> 
            Freshness.STALE_BUT_USABLE
        weatherFreshness == Freshness.EXPIRED || trafficFreshness == Freshness.EXPIRED -> Freshness.EXPIRED
        weatherFreshness == Freshness.INVALID || trafficFreshness == Freshness.INVALID -> Freshness.INVALID
        else -> Freshness.UNKNOWN
    }
}

/**
 * Determines the overall freshness of a route based on segment freshness states
 */
private fun determineOverallFreshness(segments: List<RouteSegmentEnvironment>): Freshness {
    // Return the freshest state among all segments
    val freshnessStates = segments.map { it.freshness }
    
    return when {
        freshnessStates.contains(Freshness.FRESH) -> Freshness.FRESH
        freshnessStates.contains(Freshness.STALE_BUT_USABLE) -> Freshness.STALE_BUT_USABLE
        freshnessStates.contains(Freshness.EXPIRED) -> Freshness.EXPIRED
        freshnessStates.contains(Freshness.INVALID) -> Freshness.INVALID
        else -> Freshness.UNKNOWN
    }
}

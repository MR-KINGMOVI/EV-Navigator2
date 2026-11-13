package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Handles degraded and offline behavior for weather and traffic data.
 *
 * This class ensures the navigation system can continue to operate with reduced functionality
 * when network connectivity is lost or when environmental data is unavailable.
 */
class OfflineBehavior {
    
    /**
     * Enum representing network/connection states.
     */
    enum class NetworkState {
        /**
         * Online - full connectivity available
         */
        ONLINE,
        
        /**
         * Cached - using cached data but no connectivity
         */
        CACHED,
        
        /**
         * Partial - limited connectivity or partial data availability
         */
        PARTIAL,
        
        /**
         * Stale - data is stale but still usable
         */
        STALE,
        
        /**
         * Offline - no connectivity and no valid cached data
         */
        OFFLINE,
        
        /**
         * Unknown - network state cannot be determined
         */
        UNKNOWN
    }
    
    /**
     * Enum representing the level of environmental data availability.
     */
    enum class DataAvailability {
        /**
         * Full data available from all sources
         */
        FULL,
        
        /**
         * Partial data available (some sources missing)
         */
        PARTIAL,
        
        /**
         * Limited data available (only cached or default data)
         */
        LIMITED,
        
        /**
         * No data available (fallback behavior required)
         */
        NONE
    }
    
    /**
     * Represents the state of environmental data availability.
     */
    data class EnvironmentalDataState(
        val networkState: NetworkState,
        val dataAvailability: DataAvailability,
        val weatherDataAvailable: Boolean,
        val trafficDataAvailable: Boolean,
        val confidence: Double,
        val uncertainty: Double,
        val timestamp: Instant
    )
    
    /**
     * Provides fallback behavior when environmental data is unavailable.
     *
     * @param routeCoordinates The coordinates representing the route
     * @param networkState Current network state
     * @param cachePersistence The cache persistence layer
     * @param defaultWeather The default weather to use in offline mode
     * @param defaultTraffic The default traffic to use in offline mode
     * @return Environmental data state with fallback information
     */
    suspend fun provideOfflineFallback(
        routeCoordinates: List<GeoCoordinate>,
        networkState: NetworkState,
        cachePersistence: CachePersistence,
        defaultWeather: WeatherObservation?,
        defaultTraffic: TrafficObservation?
    ): EnvironmentalDataState {
        val now = Instant.now()
        
        return when (networkState) {
            NetworkState.ONLINE -> {
                // Online - we expect to have live data
                EnvironmentalDataState(
                    networkState = NetworkState.ONLINE,
                    dataAvailability = DataAvailability.FULL,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 1.0,
                    uncertainty = 0.0,
                    timestamp = now
                )
            }
            
            NetworkState.CACHED -> {
                // Using cached data - we have valid but potentially stale data
                EnvironmentalDataState(
                    networkState = NetworkState.CACHED,
                    dataAvailability = DataAvailability.LIMITED,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 0.8, // Slightly lower confidence for cached data
                    uncertainty = 0.2,
                    timestamp = now
                )
            }
            
            NetworkState.PARTIAL -> {
                // Partial connectivity - some data may be missing
                EnvironmentalDataState(
                    networkState = NetworkState.PARTIAL,
                    dataAvailability = DataAvailability.PARTIAL,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 0.6, // Lower confidence for partial data
                    uncertainty = 0.4,
                    timestamp = now
                )
            }
            
            NetworkState.STALE -> {
                // Stale data is being used - this is acceptable for planning
                EnvironmentalDataState(
                    networkState = NetworkState.STALE,
                    dataAvailability = DataAvailability.LIMITED,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 0.5, // Medium confidence for stale data
                    uncertainty = 0.5,
                    timestamp = now
                )
            }
            
            NetworkState.OFFLINE -> {
                // No connectivity - fallback to default or cached data
                EnvironmentalDataState(
                    networkState = NetworkState.OFFLINE,
                    dataAvailability = DataAvailability.NONE,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 0.3, // Low confidence for offline mode
                    uncertainty = 0.7,
                    timestamp = now
                )
            }
            
            NetworkState.UNKNOWN -> {
                // Unknown network state - be conservative
                EnvironmentalDataState(
                    networkState = NetworkState.UNKNOWN,
                    dataAvailability = DataAvailability.NONE,
                    weatherDataAvailable = defaultWeather != null,
                    trafficDataAvailable = defaultTraffic != null,
                    confidence = 0.0, // No confidence in unknown state
                    uncertainty = 1.0,
                    timestamp = now
                )
            }
        }
    }
    
    /**
     * Determines the appropriate network state based on connectivity and data availability.
     *
     * @param hasNetworkConnection Whether there's an active network connection
     * @param hasValidCachedData Whether valid cached data exists
     * @param isDataFresh Whether the cached data is fresh enough
     * @return The determined network state
     */
    fun determineNetworkState(
        hasNetworkConnection: Boolean,
        hasValidCachedData: Boolean,
        isDataFresh: Boolean
    ): NetworkState {
        return when {
            hasNetworkConnection -> NetworkState.ONLINE
            hasValidCachedData && isDataFresh -> NetworkState.CACHED
            hasValidCachedData -> NetworkState.STALE
            else -> NetworkState.OFFLINE
        }
    }
    
    /**
     * Gets default weather data for offline scenarios.
     *
     * @param location The geographical location
     * @return Default weather observation or null if not available
     */
    suspend fun getDefaultWeather(location: GeoCoordinate): WeatherObservation? {
        val now = Instant.now()
        return WeatherObservation(
            coordinate = location,
            timestamp = now,
            temperatureCelsius = 20.0,
            apparentTemperatureCelsius = 20.0,
            windSpeedMps = 0.0,
            windDirectionDegrees = 0.0,
            precipitationMm = 0.0,
            precipitationProbability = 0.0,
            humidityPercentage = 50.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 0.0,
            condition = WeatherCondition.PARTLY_CLOUDY,
            source = "default",
            confidence = 0.7,
            uncertainty = 0.3,
            retrievedAt = now,
            freshness = WeatherFreshness.FRESH
        )
    }
    
    suspend fun getDefaultTraffic(location: GeoCoordinate): TrafficObservation? {
        val now = Instant.now()
        return TrafficObservation(
            coordinate = location,
            timestamp = now,
            speedMps = 16.67,
            freeFlowSpeedMps = 22.22,
            congestionRatio = 0.2,
            trafficClass = TrafficClass.LIGHT,
            travelTimeMultiplier = 1.0,
            confidence = 0.6,
            uncertainty = 0.4,
            source = "default",
            retrievedAt = now,
            freshness = TrafficFreshness.FRESH
        )
    }
    
    /**
     * Determines if the current state supports real-time navigation.
     *
     * @param state The environmental data state to evaluate
     * @return True if the state supports real-time navigation, false otherwise
     */
    fun supportsRealTimeNavigation(state: EnvironmentalDataState): Boolean {
        return when (state.networkState) {
            NetworkState.ONLINE,
            NetworkState.CACHED -> true
            else -> false
        }
    }
    
    /**
     * Determines if the current state supports trip planning.
     *
     * @param state The environmental data state to evaluate
     * @return True if the state supports trip planning, false otherwise
     */
    fun supportsTripPlanning(state: EnvironmentalDataState): Boolean {
        return when (state.dataAvailability) {
            DataAvailability.FULL,
            DataAvailability.LIMITED,
            DataAvailability.PARTIAL -> true
            else -> false
        }
    }
    
    /**
     * Calculates the confidence adjustment factor based on network state.
     *
     * @param networkState The current network state
     * @return Confidence adjustment factor (0.0 to 1.0)
     */
    fun getConfidenceAdjustment(networkState: NetworkState): Double {
        return when (networkState) {
            NetworkState.ONLINE -> 1.0
            NetworkState.CACHED -> 0.8
            NetworkState.PARTIAL -> 0.6
            NetworkState.STALE -> 0.5
            NetworkState.OFFLINE -> 0.3
            NetworkState.UNKNOWN -> 0.0
        }
    }
}
package com.evnavigator.iran.core.weather.model

import com.evnavigator.iran.core.weather.Freshness
import java.time.Instant

typealias Freshness = com.evnavigator.iran.core.weather.Freshness

enum class CacheType {
    WEATHER_OBSERVATIONS,
    WEATHER_FORECASTS,
    TRAFFIC_OBSERVATIONS,
    TRAFFIC_INCIDENTS
}

data class CacheMetadata(
    val cacheType: CacheType,
    val entryCount: Int,
    val totalSizeBytes: Long,
    val lastUpdated: Instant,
    val maxAgeSeconds: Long,
    val schemaVersion: String = "1.0"
)

data class CachedWeatherData(
    val observations: List<WeatherObservation>,
    val forecasts: List<ForecastWeatherPoint>,
    val freshness: Freshness,
    val timestamp: Instant,
    val source: String
)

data class CachedTrafficData(
    val observations: List<TrafficObservation>,
    val incidents: List<TrafficIncident>,
    val freshness: Freshness,
    val timestamp: Instant,
    val source: String
)

data class WeatherCacheEntity(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val observationJson: String,
    val forecastJson: String,
    val freshness: Freshness,
    val source: String,
    val validFrom: Instant,
    val validTo: Instant,
    val schemaVersion: String = "1.0"
)

data class TrafficCacheEntity(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val observationJson: String,
    val incidentJson: String,
    val freshness: Freshness,
    val source: String,
    val validFrom: Instant,
    val validTo: Instant,
    val schemaVersion: String = "1.0"
)

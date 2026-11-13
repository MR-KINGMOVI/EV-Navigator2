package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.EnvironmentalEnergyFactors
import com.evnavigator.iran.core.weather.model.EnvironmentalRangeAdjustment
import com.evnavigator.iran.core.weather.model.EnvironmentalStopDurationAdjustment
import com.evnavigator.iran.core.weather.model.RouteEnvironmentContext
import com.evnavigator.iran.core.weather.model.SegmentTrafficData
import com.evnavigator.iran.core.weather.model.SegmentWeatherData
import com.evnavigator.iran.core.weather.model.TrafficClass
import com.evnavigator.iran.core.weather.model.TrafficObservation
import com.evnavigator.iran.core.weather.model.WeatherObservation
import com.evnavigator.iran.core.weather.provider.TrafficProvider
import com.evnavigator.iran.core.weather.provider.WeatherProvider
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Explicit tri-state representation for environmental telemetry (V13.0 Audit — Items 15, 55, 82).
 *
 * Replaces silent fallback defaults for missing temperature and precipitation observations
 * with an explicit [Known], [Stale], or [Unknown] wrapper.
 * Also decouples `:core:weather` from `:core:tripplanner` so `:core:weather` has zero upward dependency.
 */
sealed class WeatherValue<out T> {
    abstract val valueOrNull: T?
    abstract val isKnownAndFresh: Boolean

    data class Known<out T>(
        val value: T,
        val observedAt: Instant,
        val confidence: Double = 1.0
    ) : WeatherValue<T>() {
        override val valueOrNull: T = value
        override val isKnownAndFresh: Boolean = true
    }

    data class Stale<out T>(
        val value: T,
        val observedAt: Instant,
        val ageSeconds: Long,
        val confidence: Double = 0.45
    ) : WeatherValue<T>() {
        override val valueOrNull: T = value
        override val isKnownAndFresh: Boolean = false
    }

    data class Unknown(val reason: String) : WeatherValue<Nothing>() {
        override val valueOrNull: Nothing? = null
        override val isKnownAndFresh: Boolean = false
    }
}

/**
 * Evaluates weather and traffic context along route segments without silent fallback assumptions.
 */
class TripPlannerIntegration(
    private val weatherProvider: WeatherProvider,
    private val trafficProvider: TrafficProvider,
    private val environmentEnergyAdapter: EnvironmentEnergyAdapter = EnvironmentEnergyAdapter(),
    private val trafficRouteIntegration: TrafficRouteIntegration = TrafficRouteIntegration(),
    private val weatherFreshnessTtlSeconds: Long = 3_600L // 60 minutes TTL
) {

    fun classifyTemperature(obs: WeatherObservation?, now: Instant = Instant.now()): WeatherValue<Double> {
        val temp = obs?.temperatureCelsius?.takeIf { it.isFinite() }
            ?: return WeatherValue.Unknown("Temperature observation is missing")
        val ageSec = Duration.between(obs.timestamp, now).seconds
        if (ageSec < -300L) {
            return WeatherValue.Unknown("Weather observation timestamp is in the future")
        }
        return if (ageSec <= weatherFreshnessTtlSeconds && obs.freshness != Freshness.STALE && obs.freshness != Freshness.EXPIRED) {
            WeatherValue.Known(temp, obs.timestamp, obs.confidence.coerceIn(0.0, 1.0))
        } else {
            WeatherValue.Stale(temp, obs.timestamp, max(0L, ageSec))
        }
    }

    fun classifyPrecipitation(obs: WeatherObservation?, now: Instant = Instant.now()): WeatherValue<Double> {
        val precip = obs?.precipitationMm?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return WeatherValue.Unknown("Precipitation observation is missing")
        val ageSec = Duration.between(obs.timestamp, now).seconds
        if (ageSec < -300L) {
            return WeatherValue.Unknown("Weather observation timestamp is in the future")
        }
        return if (ageSec <= weatherFreshnessTtlSeconds && obs.freshness != Freshness.STALE && obs.freshness != Freshness.EXPIRED) {
            WeatherValue.Known(precip, obs.timestamp, obs.confidence.coerceIn(0.0, 1.0))
        } else {
            WeatherValue.Stale(precip, obs.timestamp, max(0L, ageSec))
        }
    }

    suspend fun buildRouteEnvironmentContext(
        segments: List<RouteSegment>,
        startTime: Instant = Instant.now(),
        estimatedDurationSeconds: Double = 3_600.0
    ): RouteEnvironmentContext {
        val coordinates = segments.map { it.startCoordinate }
        val endTime = startTime.plusSeconds(estimatedDurationSeconds.toLong().coerceAtLeast(60L))

        val weatherObservations = try {
            weatherProvider.getRouteWeather(coordinates, startTime, endTime)
        } catch (_: Exception) {
            emptyList()
        }

        val trafficResult = try {
            trafficProvider.getTrafficForSegments(segments)
        } catch (_: Exception) {
            null
        }
        val trafficObservations = trafficResult?.observations.orEmpty()

        val segmentWeatherMap = mapWeatherToSegments(segments, weatherObservations, startTime)
        val segmentTrafficMap = trafficRouteIntegration.mapTrafficToRouteSegments(segments, trafficObservations)

        val overallConfidence = calculateOverallConfidence(weatherObservations, trafficObservations)
        val overallFreshness = determineOverallFreshness(weatherObservations, trafficObservations)

        return RouteEnvironmentContext(
            routeSegments = segments,
            weatherData = segmentWeatherMap,
            trafficData = segmentTrafficMap,
            overallConfidence = overallConfidence,
            overallUncertainty = (1.0 - overallConfidence).coerceIn(0.0, 1.0),
            timestamp = Instant.now(),
            freshness = overallFreshness,
            provenance = "TripPlannerIntegration-V13"
        )
    }

    fun calculateEnergyAdjustment(
        baseEnergyWh: Double,
        environmentContext: RouteEnvironmentContext
    ): EnvironmentalEnergyFactors {
        return environmentEnergyAdapter.adaptRouteEnergy(baseEnergyWh, environmentContext)
    }

    fun calculateRangeAdjustment(
        baseRangeKm: Double,
        environmentContext: RouteEnvironmentContext
    ): EnvironmentalRangeAdjustment {
        return environmentEnergyAdapter.adaptRangeEstimate(baseRangeKm, environmentContext)
    }

    fun calculateStopDurationAdjustment(
        baseDurationMinutes: Double,
        coordinate: GeoCoordinate,
        environmentContext: RouteEnvironmentContext
    ): EnvironmentalStopDurationAdjustment {
        return environmentEnergyAdapter.adaptChargingStopDuration(
            baseDurationMinutes,
            coordinate,
            environmentContext
        )
    }

    internal fun mapWeatherToSegments(
        segments: List<RouteSegment>,
        weatherObservations: List<WeatherObservation>,
        now: Instant = Instant.now()
    ): Map<RouteSegment, SegmentWeatherData> {
        return segments.associateWith { segment ->
            val relevantObs = weatherObservations.filter { obs ->
                val ageSec = Duration.between(obs.timestamp, now).seconds
                ageSec in -300L..weatherFreshnessTtlSeconds &&
                    obs.freshness != Freshness.EXPIRED
            }

            val knownTemps = relevantObs.mapNotNull { obs ->
                (classifyTemperature(obs, now) as? WeatherValue.Known)?.value
            }
            val knownWinds = relevantObs.mapNotNull { it.windSpeedMps?.takeIf { v -> v.isFinite() && v >= 0.0 } }
            val knownPrecips = relevantObs.mapNotNull { obs ->
                (classifyPrecipitation(obs, now) as? WeatherValue.Known)?.value
            }

            if (relevantObs.isEmpty() || (knownTemps.isEmpty() && knownWinds.isEmpty() && knownPrecips.isEmpty())) {
                SegmentWeatherData(
                    segment = segment,
                    temperatureCelsius = null,
                    windSpeedMps = null,
                    windDirectionDegrees = null,
                    precipitationMm = null,
                    roadCondition = null,
                    confidence = 0.0,
                    uncertainty = 1.0,
                    freshness = Freshness.UNKNOWN
                )
            } else {
                val avgConf = relevantObs.map { it.confidence }.average().takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
                SegmentWeatherData(
                    segment = segment,
                    temperatureCelsius = knownTemps.takeIf { it.isNotEmpty() }?.average(),
                    windSpeedMps = knownWinds.takeIf { it.isNotEmpty() }?.average(),
                    windDirectionDegrees = relevantObs.firstNotNullOfOrNull { it.windDirectionDegrees },
                    precipitationMm = knownPrecips.takeIf { it.isNotEmpty() }?.average(),
                    roadCondition = relevantObs.firstNotNullOfOrNull { it.roadCondition },
                    confidence = avgConf,
                    uncertainty = (1.0 - avgConf).coerceIn(0.0, 1.0),
                    freshness = determineWeatherFreshness(relevantObs)
                )
            }
        }
    }

    private fun calculateOverallConfidence(
        weatherObservations: List<WeatherObservation>,
        trafficObservations: List<TrafficObservation>
    ): Double {
        val validWeather = weatherObservations.filter { it.confidence > 0.0 && it.freshness != Freshness.UNKNOWN }
        val validTraffic = trafficObservations.filter {
            it.confidence > 0.0 && it.trafficClass != TrafficClass.UNKNOWN && it.freshness != Freshness.UNKNOWN
        }
        if (validWeather.isEmpty() && validTraffic.isEmpty()) return 0.0
        val weatherConf = validWeather.map { it.confidence }.average().takeIf { it.isFinite() } ?: 0.0
        val trafficConf = validTraffic.map { it.confidence }.average().takeIf { it.isFinite() } ?: 0.0
        return when {
            validWeather.isNotEmpty() && validTraffic.isNotEmpty() -> min(weatherConf, trafficConf)
            validWeather.isNotEmpty() -> weatherConf * 0.75
            else -> trafficConf * 0.75
        }.coerceIn(0.0, 1.0)
    }

    private fun determineOverallFreshness(
        weatherObservations: List<WeatherObservation>,
        trafficObservations: List<TrafficObservation>
    ): Freshness {
        if (weatherObservations.isEmpty() && trafficObservations.isEmpty()) return Freshness.UNKNOWN
        val wFresh = determineWeatherFreshness(weatherObservations)
        val tFresh = determineTrafficFreshness(trafficObservations)
        return when {
            wFresh == Freshness.EXPIRED || tFresh == Freshness.EXPIRED -> Freshness.EXPIRED
            wFresh == Freshness.STALE || tFresh == Freshness.STALE -> Freshness.STALE
            wFresh == Freshness.FRESH || tFresh == Freshness.FRESH -> Freshness.FRESH
            wFresh == Freshness.CACHED || tFresh == Freshness.CACHED -> Freshness.CACHED
            else -> Freshness.UNKNOWN
        }
    }

    private fun determineWeatherFreshness(observations: List<WeatherObservation>): Freshness {
        if (observations.isEmpty()) return Freshness.UNKNOWN
        return when {
            observations.any { it.freshness == Freshness.EXPIRED } -> Freshness.EXPIRED
            observations.any { it.freshness == Freshness.STALE } -> Freshness.STALE
            observations.all { it.freshness == Freshness.FRESH } -> Freshness.FRESH
            else -> Freshness.CACHED
        }
    }

    private fun determineTrafficFreshness(observations: List<TrafficObservation>): Freshness {
        if (observations.isEmpty()) return Freshness.UNKNOWN
        return when {
            observations.any { it.freshness == Freshness.EXPIRED } -> Freshness.EXPIRED
            observations.any { it.freshness == Freshness.STALE } -> Freshness.STALE
            observations.all { it.freshness == Freshness.FRESH } -> Freshness.FRESH
            else -> Freshness.CACHED
        }
    }
}
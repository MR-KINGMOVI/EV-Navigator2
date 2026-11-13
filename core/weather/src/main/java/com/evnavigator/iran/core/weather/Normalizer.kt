package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import com.evnavigator.iran.core.weather.validation.WeatherValidator
import com.evnavigator.iran.core.weather.validation.TrafficValidator
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * Normalizer for weather and traffic data to ensure consistent representation.
 */
class Normalizer {
    
    private val weatherValidator = WeatherValidator()
    private val trafficValidator = TrafficValidator()

    private fun getDoubleFromAny(obj: Any?, key: String, altKey: String? = null): Double? {
        return when (obj) {
            is Map<*, *> -> {
                val v = obj[key] ?: altKey?.let { obj[it] }
                when (v) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                    else -> null
                }
            }
            is JSONObject -> {
                val targetKey = if (obj.has(key)) key else altKey?.takeIf { obj.has(it) }
                targetKey?.let {
                    try { obj.getDouble(it) } catch (e: Exception) { null }
                }
            }
            else -> null
        }
    }

    private fun getLongFromAny(obj: Any?, key: String, altKey: String? = null): Long? {
        return when (obj) {
            is Map<*, *> -> {
                val v = obj[key] ?: altKey?.let { obj[it] }
                when (v) {
                    is Number -> v.toLong()
                    is String -> v.toLongOrNull()
                    is Instant -> v.toEpochMilli()
                    else -> null
                }
            }
            is JSONObject -> {
                val targetKey = if (obj.has(key)) key else altKey?.takeIf { obj.has(it) }
                targetKey?.let {
                    try { obj.getLong(it) } catch (e: Exception) { null }
                }
            }
            else -> null
        }
    }

    private fun getStringFromAny(obj: Any?, key: String, altKey: String? = null): String? {
        return when (obj) {
            is Map<*, *> -> {
                val v = obj[key] ?: altKey?.let { obj[it] }
                v?.toString()
            }
            is JSONObject -> {
                val targetKey = if (obj.has(key)) key else altKey?.takeIf { obj.has(it) }
                targetKey?.let {
                    try { obj.getString(it) } catch (e: Exception) { null }
                }
            }
            else -> null
        }
    }
    
    /**
     * Normalize a raw weather observation into a canonical domain model.
     *
     * @param rawObservation The raw observation from a provider (WeatherObservation, Map, or JSONObject)
     * @param source The source identifier for this data
     * @return Normalized WeatherObservation or null if invalid
     */
    fun normalizeWeatherObservation(
        rawObservation: Any?, 
        source: String
    ): WeatherObservation? {
        if (rawObservation is WeatherObservation) {
            val validationResult = weatherValidator.validateWeatherObservation(rawObservation)
            return if (!validationResult.hasErrors) {
                rawObservation.copy(source = source)
            } else {
                null
            }
        }

        if (rawObservation is Map<*, *> || rawObservation is JSONObject) {
            val lat = getDoubleFromAny(rawObservation, "latitude", "lat")
            val lon = getDoubleFromAny(rawObservation, "longitude", "lon") ?: getDoubleFromAny(rawObservation, "lng")
            if (lat == null || lon == null) return null

            val timeMs = getLongFromAny(rawObservation, "timestamp", "time")
            val timestamp = if (timeMs != null) Instant.ofEpochMilli(timeMs) else Instant.now()
            val temp = getDoubleFromAny(rawObservation, "temperatureCelsius", "temp") ?: getDoubleFromAny(rawObservation, "temperature")
            val apparent = getDoubleFromAny(rawObservation, "apparentTemperatureCelsius", "apparentTemp") ?: getDoubleFromAny(rawObservation, "feelsLike")
            val windSpeed = getDoubleFromAny(rawObservation, "windSpeedMps", "windSpeed")
            val windDir = getDoubleFromAny(rawObservation, "windDirectionDegrees", "windDir") ?: getDoubleFromAny(rawObservation, "windBearing")
            val precip = getDoubleFromAny(rawObservation, "precipitationMm", "precip") ?: getDoubleFromAny(rawObservation, "precipitation")
            val humidity = getDoubleFromAny(rawObservation, "humidityPercentage", "humidity")
            val pressure = getDoubleFromAny(rawObservation, "pressureHpa", "pressure")
            val visibility = getDoubleFromAny(rawObservation, "visibilityMeters", "visibility")
            val cloudCover = getDoubleFromAny(rawObservation, "cloudCoverPercentage", "cloudCover")
            val condStr = getStringFromAny(rawObservation, "condition", "cond")
            val cond = condStr?.let {
                try { WeatherCondition.valueOf(it.uppercase()) } catch (e: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN

            val obs = WeatherObservation(
                coordinate = GeoCoordinate(lat, lon),
                timestamp = timestamp,
                temperatureCelsius = temp,
                apparentTemperatureCelsius = apparent,
                windSpeedMps = windSpeed,
                windDirectionDegrees = windDir,
                precipitationMm = precip,
                humidityPercentage = humidity,
                pressureHpa = pressure,
                visibilityMeters = visibility,
                cloudCoverPercentage = cloudCover,
                condition = cond,
                source = source,
                retrievedAt = Instant.now()
            )
            val validation = weatherValidator.validateWeatherObservation(obs)
            return if (!validation.hasErrors) obs else null
        }
        
        return null
    }
    
    /**
     * Normalize a raw forecast point into a canonical domain model.
     *
     * @param rawForecast The raw forecast from a provider (ForecastWeatherPoint, Map, or JSONObject)
     * @param source The source identifier for this data
     * @return Normalized ForecastWeatherPoint or null if invalid
     */
    fun normalizeForecast(
        rawForecast: Any?, 
        source: String
    ): ForecastWeatherPoint? {
        if (rawForecast is ForecastWeatherPoint) {
            val validationResult = weatherValidator.validateForecastWeatherPoint(rawForecast)
            return if (!validationResult.hasErrors) {
                rawForecast.copy(source = source)
            } else {
                null
            }
        }

        if (rawForecast is Map<*, *> || rawForecast is JSONObject) {
            val lat = getDoubleFromAny(rawForecast, "latitude", "lat")
            val lon = getDoubleFromAny(rawForecast, "longitude", "lon") ?: getDoubleFromAny(rawForecast, "lng")
            if (lat == null || lon == null) return null

            val validTimeMs = getLongFromAny(rawForecast, "validTime", "time")
            val validTime = if (validTimeMs != null) Instant.ofEpochMilli(validTimeMs) else Instant.now().plusSeconds(3600)
            val genTimeMs = getLongFromAny(rawForecast, "forecastGeneratedAt", "genTime")
            val genTime = if (genTimeMs != null) Instant.ofEpochMilli(genTimeMs) else Instant.now()

            val temp = getDoubleFromAny(rawForecast, "temperatureCelsius", "temp")
            val apparent = getDoubleFromAny(rawForecast, "apparentTemperatureCelsius", "apparentTemp")
            val windSpeed = getDoubleFromAny(rawForecast, "windSpeedMps", "windSpeed")
            val windDir = getDoubleFromAny(rawForecast, "windDirectionDegrees", "windDir")
            val precip = getDoubleFromAny(rawForecast, "precipitationMm", "precip")
            val precipProb = getDoubleFromAny(rawForecast, "precipitationProbability", "precipProb")
            val humidity = getDoubleFromAny(rawForecast, "humidityPercentage", "humidity")
            val pressure = getDoubleFromAny(rawForecast, "pressureHpa", "pressure")
            val visibility = getDoubleFromAny(rawForecast, "visibilityMeters", "visibility")
            val cloudCover = getDoubleFromAny(rawForecast, "cloudCoverPercentage", "cloudCover")
            val condStr = getStringFromAny(rawForecast, "condition", "cond")
            val cond = condStr?.let {
                try { WeatherCondition.valueOf(it.uppercase()) } catch (e: Exception) { WeatherCondition.UNKNOWN }
            } ?: WeatherCondition.UNKNOWN
            val horizon = getDoubleFromAny(rawForecast, "forecastHorizonHours", "horizon") ?: 1.0
            val conf = getDoubleFromAny(rawForecast, "confidence", "conf") ?: 0.9
            val uncert = getDoubleFromAny(rawForecast, "uncertainty", "uncert") ?: 0.1

            val fc = ForecastWeatherPoint(
                coordinate = GeoCoordinate(lat, lon),
                validTime = validTime,
                forecastGeneratedAt = genTime,
                temperatureCelsius = temp,
                apparentTemperatureCelsius = apparent,
                windSpeedMps = windSpeed,
                windDirectionDegrees = windDir,
                precipitationMm = precip,
                precipitationProbability = precipProb,
                humidityPercentage = humidity,
                pressureHpa = pressure,
                visibilityMeters = visibility,
                cloudCoverPercentage = cloudCover,
                condition = cond,
                source = source,
                forecastHorizonHours = horizon,
                confidence = conf,
                uncertainty = uncert
            )
            val validation = weatherValidator.validateForecastWeatherPoint(fc)
            return if (!validation.hasErrors) fc else null
        }
        
        return null
    }
    
    /**
     * Normalize a raw traffic observation into a canonical domain model.
     *
     * @param rawObservation The raw traffic observation from a provider (TrafficObservation, Map, or JSONObject)
     * @param source The source identifier for this data
     * @return Normalized TrafficObservation or null if invalid
     */
    fun normalizeTrafficObservation(
        rawObservation: Any?, 
        source: String
    ): TrafficObservation? {
        if (rawObservation is TrafficObservation) {
            val validationResult = trafficValidator.validateTrafficObservation(rawObservation)
            return if (!validationResult.hasErrors) {
                rawObservation.copy(source = source)
            } else {
                null
            }
        }

        if (rawObservation is Map<*, *> || rawObservation is JSONObject) {
            val lat = getDoubleFromAny(rawObservation, "latitude", "lat")
            val lon = getDoubleFromAny(rawObservation, "longitude", "lon") ?: getDoubleFromAny(rawObservation, "lng")
            if (lat == null || lon == null) return null

            val timeMs = getLongFromAny(rawObservation, "timestamp", "time")
            val timestamp = if (timeMs != null) Instant.ofEpochMilli(timeMs) else Instant.now()
            val speed = getDoubleFromAny(rawObservation, "speedMps", "speed")
            val freeFlow = getDoubleFromAny(rawObservation, "freeFlowSpeedMps", "freeFlow")
            val congestion = getDoubleFromAny(rawObservation, "congestionRatio", "congestion")
            val trafficClassStr = getStringFromAny(rawObservation, "trafficClass")
            val trafficClass = trafficClassStr?.let {
                try { TrafficClass.valueOf(it.uppercase()) } catch (e: Exception) { TrafficClass.UNKNOWN }
            } ?: TrafficClass.UNKNOWN
            val multiplier = getDoubleFromAny(rawObservation, "travelTimeMultiplier", "multiplier")
            val conf = getDoubleFromAny(rawObservation, "confidence", "conf") ?: 1.0

            val obs = TrafficObservation(
                coordinate = GeoCoordinate(lat, lon),
                timestamp = timestamp,
                speedMps = speed,
                freeFlowSpeedMps = freeFlow,
                congestionRatio = congestion,
                trafficClass = trafficClass,
                travelTimeMultiplier = multiplier,
                confidence = conf,
                source = source,
                retrievedAt = Instant.now()
            )
            val validation = trafficValidator.validateTrafficObservation(obs)
            return if (!validation.hasErrors) obs else null
        }
        
        return null
    }
    
    /**
     * Normalize a raw traffic incident into a canonical domain model.
     *
     * @param rawIncident The raw traffic incident from a provider (TrafficIncident, Map, or JSONObject)
     * @param source The source identifier for this data
     * @return Normalized TrafficIncident or null if invalid
     */
    fun normalizeTrafficIncident(
        rawIncident: Any?, 
        source: String
    ): TrafficIncident? {
        if (rawIncident is TrafficIncident) {
            val validationResult = trafficValidator.validateTrafficIncident(rawIncident)
            return if (!validationResult.hasErrors) {
                rawIncident.copy(source = source)
            } else {
                null
            }
        }

        if (rawIncident is Map<*, *> || rawIncident is JSONObject) {
            val id = getStringFromAny(rawIncident, "id") ?: "incident_${System.currentTimeMillis()}"
            val lat = getDoubleFromAny(rawIncident, "latitude", "lat")
            val lon = getDoubleFromAny(rawIncident, "longitude", "lon") ?: getDoubleFromAny(rawIncident, "lng")
            if (lat == null || lon == null) return null

            val typeStr = getStringFromAny(rawIncident, "type")
            val type = typeStr?.let {
                try { IncidentType.valueOf(it.uppercase()) } catch (e: Exception) { IncidentType.UNKNOWN }
            } ?: IncidentType.UNKNOWN

            val severity = getLongFromAny(rawIncident, "severity")?.toInt()
            val startMs = getLongFromAny(rawIncident, "startTime") ?: System.currentTimeMillis()
            val endExpectedMs = getLongFromAny(rawIncident, "expectedEndTime", "expectedEnd")
            val endActualMs = getLongFromAny(rawIncident, "actualEndTime", "actualEnd")
            val statusStr = getStringFromAny(rawIncident, "status")
            val status = statusStr?.let {
                try { IncidentStatus.valueOf(it.uppercase()) } catch (e: Exception) { IncidentStatus.ACTIVE }
            } ?: IncidentStatus.ACTIVE
            val desc = getStringFromAny(rawIncident, "description", "desc")
            val conf = getDoubleFromAny(rawIncident, "confidence", "conf") ?: 0.9
            val uncert = getDoubleFromAny(rawIncident, "uncertainty", "uncert") ?: 0.1
            val freshnessStr = getStringFromAny(rawIncident, "freshness")
            val freshness = freshnessStr?.let {
                try { TrafficFreshness.valueOf(it.uppercase()) } catch (e: Exception) { TrafficFreshness.FRESH }
            } ?: TrafficFreshness.FRESH

            val inc = TrafficIncident(
                id = id,
                coordinate = GeoCoordinate(lat, lon),
                type = type,
                severity = severity,
                startTime = Instant.ofEpochMilli(startMs),
                expectedEndTime = endExpectedMs?.let { Instant.ofEpochMilli(it) },
                actualEndTime = endActualMs?.let { Instant.ofEpochMilli(it) },
                status = status,
                description = desc,
                source = source,
                retrievedAt = Instant.now(),
                confidence = conf,
                uncertainty = uncert,
                freshness = freshness
            )
            val validation = trafficValidator.validateTrafficIncident(inc)
            return if (!validation.hasErrors) inc else null
        }
        
        return null
    }
    
    /**
     * Normalize travel time data into a canonical model.
     *
     * @param rawTravelTime The raw travel time data from a provider
     * @param source The source identifier for this data
     * @return Normalized TravelTimeEstimate or null if invalid
     */
    fun normalizeTravelTime(
        rawTravelTime: Any?, 
        source: String
    ): TravelTimeEstimate? {
        if (rawTravelTime is TravelTimeEstimate) {
            return rawTravelTime.copy(source = source)
        }
        return null
    }
    
    /**
     * Calculate and update confidence and uncertainty based on data freshness.
     *
     * @param observation The weather or traffic observation
     * @param freshness The freshness level of the data
     * @return Updated observation with adjusted confidence/uncertainty
     */
    fun adjustConfidenceAndUncertainty(
        observation: WeatherObservation,
        freshness: WeatherFreshness
    ): WeatherObservation {
        return observation
    }
    
    /**
     * Calculate and update confidence and uncertainty based on data freshness.
     *
     * @param observation The traffic observation
     * @param freshness The freshness level of the data
     * @return Updated observation with adjusted confidence/uncertainty
     */
    fun adjustConfidenceAndUncertainty(
        observation: TrafficObservation,
        freshness: TrafficFreshness
    ): TrafficObservation {
        return observation
    }
    
    /**
     * Apply unit conversions and standardization.
     *
     * @param weather The weather observation to convert
     * @return Weather observation with standardized units
     */
    fun standardizeUnits(weather: WeatherObservation): WeatherObservation {
        return weather
    }
    
    /**
     * Apply unit conversions and standardization.
     *
     * @param forecast The forecast point to convert
     * @return Forecast point with standardized units
     */
    fun standardizeUnits(forecast: ForecastWeatherPoint): ForecastWeatherPoint {
        return forecast
    }
    
    /**
     * Apply unit conversions and standardization.
     *
     * @param traffic The traffic observation to convert
     * @return Traffic observation with standardized units
     */
    fun standardizeUnits(traffic: TrafficObservation): TrafficObservation {
        return traffic
    }
}

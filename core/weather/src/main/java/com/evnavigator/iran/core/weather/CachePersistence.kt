package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord
import com.evnavigator.iran.domain.repository.EnvironmentCacheStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

typealias EnvironmentCacheDao = EnvironmentCacheStore
typealias EnvironmentCacheEntity = DomainEnvironmentCacheRecord

/**
 * Handles caching and persistence of weather and traffic data.
 *
 * This component manages thread-safe caching of environmental observations,
 * forecasts, and incidents with configurable time-to-live and distance checks,
 * backed by persistent storage via the Domain [EnvironmentCacheStore] contract.
 */
class CachePersistence(
    private val cacheDao: EnvironmentCacheStore? = null,
    private val defaultTtlSeconds: Long = 3600L,
    private val isProductionEnvironment: Boolean = false
) {

    init {
        if (isProductionEnvironment && cacheDao == null) {
            throw IllegalStateException("EnvironmentCacheDao injection is strictly mandatory in production environment for CachePersistence.")
        }
    }

    private val weatherObservationsCache = CopyOnWriteArrayList<WeatherObservation>()
    private val forecastPointsCache = CopyOnWriteArrayList<ForecastWeatherPoint>()
    private val trafficObservationsCache = CopyOnWriteArrayList<TrafficObservation>()
    private val trafficIncidentsCache = CopyOnWriteArrayList<TrafficIncident>()

    private val lastUpdateMap = ConcurrentHashMap<CacheType, Instant>()

    /**
     * Checks data freshness based on timestamp and configured threshold.
     */
    fun checkFreshness(timestamp: Instant, freshnessThresholdSeconds: Long): Freshness {
        val ageSeconds = Duration.between(timestamp, Instant.now()).seconds
        return when {
            ageSeconds <= freshnessThresholdSeconds -> Freshness.FRESH
            ageSeconds <= freshnessThresholdSeconds * 2.5 -> Freshness.STALE_BUT_USABLE
            else -> Freshness.EXPIRED
        }
    }

    /**
     * Determines whether cached data can be used based on freshness state and policy.
     */
    fun shouldUseCachedData(freshness: Freshness, allowStale: Boolean): Boolean {
        return when (freshness) {
            Freshness.FRESH -> true
            Freshness.STALE_BUT_USABLE -> allowStale
            Freshness.EXPIRED -> false
            Freshness.INVALID -> false
            Freshness.CACHED -> allowStale
            Freshness.UNKNOWN -> false
        }
    }

    /**
     * Cold-start restoration: Loads valid persistent entries from Room database into memory L1 cache.
     * Restores weather observations, forecasts, traffic observations, and incidents.
     */
    suspend fun warmUpFromStorage(
        minLat: Double = 24.0,
        maxLat: Double = 40.5,
        minLon: Double = 43.5,
        maxLon: Double = 64.5
    ): Int {
        val dao = cacheDao ?: return 0
        dao.pruneExpired(System.currentTimeMillis())
        var totalRestored = 0

        // 1. Weather Observations
        val weatherEntities = dao.getValidEntriesInBounds("WEATHER_OBSERVATION", minLat, maxLat, minLon, maxLon)
        val restoredWeather = weatherEntities.mapNotNull { parseWeatherObservation(it) }
        saveWeatherObservations(restoredWeather)
        totalRestored += restoredWeather.size

        // 2. Weather Forecasts
        val forecastEntities = dao.getValidEntriesInBounds("WEATHER_FORECAST", minLat, maxLat, minLon, maxLon)
        val restoredForecasts = forecastEntities.mapNotNull { parseForecastPoint(it) }
        saveForecastWeatherPoints(restoredForecasts)
        totalRestored += restoredForecasts.size

        // 3. Traffic Observations
        val trafficEntities = dao.getValidEntriesInBounds("TRAFFIC_OBSERVATION", minLat, maxLat, minLon, maxLon)
        val restoredTraffic = trafficEntities.mapNotNull { parseTrafficObservation(it) }
        saveTrafficObservations(restoredTraffic)
        totalRestored += restoredTraffic.size

        // 4. Traffic Incidents
        val incidentEntities = dao.getValidEntriesInBounds("TRAFFIC_INCIDENT", minLat, maxLat, minLon, maxLon)
        val restoredIncidents = incidentEntities.mapNotNull { parseTrafficIncident(it) }
        saveTrafficIncidents(restoredIncidents)
        totalRestored += restoredIncidents.size

        return totalRestored
    }

    /**
     * Saves weather observations to cache and Room database with TTL.
     * Enforces conflict policy: newer data never overwritten by stale data.
     */
    suspend fun saveWeatherObservations(observations: List<WeatherObservation>): Boolean {
        if (observations.isEmpty()) return false

        // L1 Cache with conflict resolution
        for (newObs in observations) {
            val existing = weatherObservationsCache.firstOrNull {
                abs(it.coordinate.latitude - newObs.coordinate.latitude) < 0.005 &&
                    abs(it.coordinate.longitude - newObs.coordinate.longitude) < 0.005
            }
            if (existing == null) {
                weatherObservationsCache.add(newObs)
            } else if (!existing.timestamp.isAfter(newObs.timestamp)) {
                weatherObservationsCache.remove(existing)
                weatherObservationsCache.add(newObs)
            }
        }
        lastUpdateMap[CacheType.WEATHER_OBSERVATIONS] = Instant.now()

        cacheDao?.let { dao ->
            val now = System.currentTimeMillis()
            val entities = observations.mapNotNull { obs ->
                val key = generateWeatherCacheKey(obs.coordinate.latitude, obs.coordinate.longitude, obs.timestamp)
                val json = org.json.JSONObject().apply {
                    put("lat", obs.coordinate.latitude)
                    put("lon", obs.coordinate.longitude)
                    put("time", obs.timestamp.toEpochMilli())
                    obs.temperatureCelsius?.let { put("temp", it) }
                    obs.apparentTemperatureCelsius?.let { put("apparentTemp", it) }
                    obs.windSpeedMps?.let { put("windSpeed", it) }
                    obs.windDirectionDegrees?.let { put("windDir", it) }
                    obs.precipitationMm?.let { put("precip", it) }
                    obs.humidityPercentage?.let { put("humidity", it) }
                    obs.pressureHpa?.let { put("pressure", it) }
                    obs.visibilityMeters?.let { put("visibility", it) }
                    obs.cloudCoverPercentage?.let { put("cloudCover", it) }
                    put("cond", obs.condition.name)
                }
                EnvironmentCacheEntity(
                    key = key,
                    cacheType = "WEATHER_OBSERVATION",
                    latitude = obs.coordinate.latitude,
                    longitude = obs.coordinate.longitude,
                    payloadJson = json.toString(),
                    cachedAt = now,
                    expiresAt = now + (defaultTtlSeconds * 1000L)
                )
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }
        return true
    }

    /**
     * Saves forecast weather points to cache and Room database with TTL.
     * Enforces conflict policy: newer forecast generation never overwritten by older.
     */
    suspend fun saveForecastWeatherPoints(forecasts: List<ForecastWeatherPoint>): Boolean {
        if (forecasts.isEmpty()) return false

        for (newFc in forecasts) {
            val existing = forecastPointsCache.firstOrNull {
                abs(it.coordinate.latitude - newFc.coordinate.latitude) < 0.005 &&
                    abs(it.coordinate.longitude - newFc.coordinate.longitude) < 0.005 &&
                    it.validTime == newFc.validTime
            }
            if (existing == null) {
                forecastPointsCache.add(newFc)
            } else if (!existing.forecastGeneratedAt.isAfter(newFc.forecastGeneratedAt)) {
                forecastPointsCache.remove(existing)
                forecastPointsCache.add(newFc)
            }
        }
        lastUpdateMap[CacheType.WEATHER_FORECASTS] = Instant.now()

        cacheDao?.let { dao ->
            val now = System.currentTimeMillis()
            val entities = forecasts.mapNotNull { fc ->
                val json = org.json.JSONObject().apply {
                    put("lat", fc.coordinate.latitude)
                    put("lon", fc.coordinate.longitude)
                    put("validTime", fc.validTime.toEpochMilli())
                    put("genTime", fc.forecastGeneratedAt.toEpochMilli())
                    fc.temperatureCelsius?.let { put("temp", it) }
                    fc.apparentTemperatureCelsius?.let { put("apparentTemp", it) }
                    fc.windSpeedMps?.let { put("windSpeed", it) }
                    fc.windDirectionDegrees?.let { put("windDir", it) }
                    fc.precipitationMm?.let { put("precip", it) }
                    fc.precipitationProbability?.let { put("precipProb", it) }
                    fc.humidityPercentage?.let { put("humidity", it) }
                    fc.pressureHpa?.let { put("pressure", it) }
                    fc.visibilityMeters?.let { put("visibility", it) }
                    fc.cloudCoverPercentage?.let { put("cloudCover", it) }
                    put("cond", fc.condition.name)
                    put("source", fc.source)
                    put("horizon", fc.forecastHorizonHours)
                    put("conf", fc.confidence)
                    put("uncert", fc.uncertainty)
                }
                EnvironmentCacheEntity(
                    key = "forecast_${fc.coordinate.latitude}_${fc.coordinate.longitude}_${fc.validTime.toEpochMilli()}",
                    cacheType = "WEATHER_FORECAST",
                    latitude = fc.coordinate.latitude,
                    longitude = fc.coordinate.longitude,
                    payloadJson = json.toString(),
                    cachedAt = now,
                    expiresAt = now + (defaultTtlSeconds * 1000L)
                )
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }
        return true
    }

    /**
     * Saves traffic observations to cache and Room database with TTL.
     * Enforces conflict policy: newer timestamps never overwritten by older.
     */
    suspend fun saveTrafficObservations(observations: List<TrafficObservation>): Boolean {
        if (observations.isEmpty()) return false

        for (newObs in observations) {
            val existing = trafficObservationsCache.firstOrNull {
                abs(it.coordinate.latitude - newObs.coordinate.latitude) < 0.005 &&
                    abs(it.coordinate.longitude - newObs.coordinate.longitude) < 0.005
            }
            if (existing == null) {
                trafficObservationsCache.add(newObs)
            } else if (!existing.timestamp.isAfter(newObs.timestamp)) {
                trafficObservationsCache.remove(existing)
                trafficObservationsCache.add(newObs)
            }
        }
        lastUpdateMap[CacheType.TRAFFIC_OBSERVATIONS] = Instant.now()

        cacheDao?.let { dao ->
            val now = System.currentTimeMillis()
            val entities = observations.mapNotNull { obs ->
                val json = org.json.JSONObject().apply {
                    put("lat", obs.coordinate.latitude)
                    put("lon", obs.coordinate.longitude)
                    put("time", obs.timestamp.toEpochMilli())
                    obs.speedMps?.let { put("speed", it) }
                    obs.freeFlowSpeedMps?.let { put("freeFlow", it) }
                    obs.congestionRatio?.let { put("congestion", it) }
                    obs.trafficClass?.let { put("trafficClass", it.name) }
                    obs.travelTimeMultiplier?.let { put("multiplier", it) }
                    put("conf", obs.confidence)
                }
                EnvironmentCacheEntity(
                    key = generateTrafficCacheKey(obs.coordinate.latitude, obs.coordinate.longitude, obs.timestamp),
                    cacheType = "TRAFFIC_OBSERVATION",
                    latitude = obs.coordinate.latitude,
                    longitude = obs.coordinate.longitude,
                    payloadJson = json.toString(),
                    cachedAt = now,
                    expiresAt = now + (defaultTtlSeconds * 1000L)
                )
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }
        return true
    }

    /**
     * Saves traffic incidents to cache and Room database with TTL.
     * Enforces conflict policy: newer incident retrieval never overwritten by older.
     */
    suspend fun saveTrafficIncidents(incidents: List<TrafficIncident>): Boolean {
        if (incidents.isEmpty()) return false

        for (newInc in incidents) {
            val existing = trafficIncidentsCache.firstOrNull { it.id == newInc.id }
            if (existing == null) {
                trafficIncidentsCache.add(newInc)
            } else if (!existing.retrievedAt.isAfter(newInc.retrievedAt)) {
                trafficIncidentsCache.remove(existing)
                trafficIncidentsCache.add(newInc)
            }
        }
        lastUpdateMap[CacheType.TRAFFIC_INCIDENTS] = Instant.now()

        cacheDao?.let { dao ->
            val now = System.currentTimeMillis()
            val entities = incidents.mapNotNull { inc ->
                val json = org.json.JSONObject().apply {
                    put("id", inc.id)
                    put("lat", inc.coordinate.latitude)
                    put("lon", inc.coordinate.longitude)
                    put("type", inc.type.name)
                    inc.severity?.let { put("severity", it) }
                    put("startTime", inc.startTime.toEpochMilli())
                    inc.expectedEndTime?.let { put("expectedEnd", it.toEpochMilli()) }
                    inc.actualEndTime?.let { put("actualEnd", it.toEpochMilli()) }
                    put("status", inc.status.name)
                    inc.description?.let { put("desc", it) }
                    put("source", inc.source)
                    put("retrievedAt", inc.retrievedAt.toEpochMilli())
                    put("confidence", inc.confidence)
                    put("uncertainty", inc.uncertainty)
                    put("freshness", inc.freshness.name)
                }
                EnvironmentCacheEntity(
                    key = "incident_${inc.id}_${inc.startTime.toEpochMilli()}",
                    cacheType = "TRAFFIC_INCIDENT",
                    latitude = inc.coordinate.latitude,
                    longitude = inc.coordinate.longitude,
                    payloadJson = json.toString(),
                    cachedAt = now,
                    expiresAt = now + (defaultTtlSeconds * 1000L)
                )
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }
        return true
    }

    internal fun parseWeatherObservation(entity: EnvironmentCacheEntity): WeatherObservation? {
        return try {
            val json = org.json.JSONObject(entity.payloadJson)
            val lat = json.optDouble("lat", entity.latitude)
            val lon = json.optDouble("lon", entity.longitude)
            val timeMs = json.optLong("time", entity.cachedAt)
            val temp = if (json.has("temp")) json.getDouble("temp") else null
            val apparent = if (json.has("apparentTemp")) json.getDouble("apparentTemp") else null
            val windSpeed = if (json.has("windSpeed")) json.getDouble("windSpeed") else null
            val windDir = if (json.has("windDir")) json.getDouble("windDir") else null
            val precip = if (json.has("precip")) json.getDouble("precip") else null
            val humidity = if (json.has("humidity")) json.getDouble("humidity") else null
            val pressure = if (json.has("pressure")) json.getDouble("pressure") else null
            val visibility = if (json.has("visibility")) json.getDouble("visibility") else null
            val cloudCover = if (json.has("cloudCover")) json.getDouble("cloudCover") else null
            val condStr = json.optString("cond", WeatherCondition.UNKNOWN.name)
            val cond = try { WeatherCondition.valueOf(condStr) } catch (e: Exception) { WeatherCondition.UNKNOWN }

            WeatherObservation(
                coordinate = GeoCoordinate(lat, lon),
                timestamp = Instant.ofEpochMilli(timeMs),
                temperatureCelsius = temp,
                apparentTemperatureCelsius = apparent,
                windSpeedMps = windSpeed,
                windDirectionDegrees = windDir,
                precipitationMm = precip,
                humidityPercentage = humidity,
                pressureHpa = pressure,
                visibilityMeters = visibility,
                cloudCoverPercentage = cloudCover,
                condition = cond
            )
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseForecastPoint(entity: EnvironmentCacheEntity): ForecastWeatherPoint? {
        return try {
            val json = org.json.JSONObject(entity.payloadJson)
            val lat = json.optDouble("lat", entity.latitude)
            val lon = json.optDouble("lon", entity.longitude)
            val validTime = json.optLong("validTime", entity.cachedAt)
            val genTime = json.optLong("genTime", entity.cachedAt)
            val temp = if (json.has("temp")) json.getDouble("temp") else null
            val apparent = if (json.has("apparentTemp")) json.getDouble("apparentTemp") else null
            val windSpeed = if (json.has("windSpeed")) json.getDouble("windSpeed") else null
            val windDir = if (json.has("windDir")) json.getDouble("windDir") else null
            val precip = if (json.has("precip")) json.getDouble("precip") else null
            val precipProb = if (json.has("precipProb")) json.getDouble("precipProb") else null
            val humidity = if (json.has("humidity")) json.getDouble("humidity") else null
            val pressure = if (json.has("pressure")) json.getDouble("pressure") else null
            val visibility = if (json.has("visibility")) json.getDouble("visibility") else null
            val cloudCover = if (json.has("cloudCover")) json.getDouble("cloudCover") else null
            val condStr = json.optString("cond", WeatherCondition.UNKNOWN.name)
            val cond = try { WeatherCondition.valueOf(condStr) } catch (e: Exception) { WeatherCondition.UNKNOWN }
            val source = json.optString("source", "PERSISTED_CACHE")
            val horizon = json.optDouble("horizon", 1.0)
            val conf = json.optDouble("conf", 0.9)
            val uncert = json.optDouble("uncert", 0.1)

            ForecastWeatherPoint(
                coordinate = GeoCoordinate(lat, lon),
                validTime = Instant.ofEpochMilli(validTime),
                forecastGeneratedAt = Instant.ofEpochMilli(genTime),
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
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseTrafficObservation(entity: EnvironmentCacheEntity): TrafficObservation? {
        return try {
            val json = org.json.JSONObject(entity.payloadJson)
            val lat = json.optDouble("lat", entity.latitude)
            val lon = json.optDouble("lon", entity.longitude)
            val time = json.optLong("time", entity.cachedAt)
            val speed = if (json.has("speed")) json.getDouble("speed") else null
            val freeFlow = if (json.has("freeFlow")) json.getDouble("freeFlow") else null
            val congestion = if (json.has("congestion")) json.getDouble("congestion") else null
            val trafficClass = if (json.has("trafficClass")) {
                try { TrafficClass.valueOf(json.getString("trafficClass")) } catch (e: Exception) { TrafficClass.UNKNOWN }
            } else null
            val multiplier = if (json.has("multiplier")) json.getDouble("multiplier") else null
            val conf = json.optDouble("conf", 1.0)

            TrafficObservation(
                coordinate = GeoCoordinate(lat, lon),
                timestamp = Instant.ofEpochMilli(time),
                speedMps = speed,
                freeFlowSpeedMps = freeFlow,
                congestionRatio = congestion,
                trafficClass = trafficClass,
                travelTimeMultiplier = multiplier,
                confidence = conf
            )
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseTrafficIncident(entity: EnvironmentCacheEntity): TrafficIncident? {
        return try {
            val json = org.json.JSONObject(entity.payloadJson)
            val id = json.optString("id", entity.key)
            val lat = json.optDouble("lat", entity.latitude)
            val lon = json.optDouble("lon", entity.longitude)
            val typeStr = json.optString("type", IncidentType.UNKNOWN.name)
            val type = try { IncidentType.valueOf(typeStr) } catch (e: Exception) { IncidentType.UNKNOWN }
            val severity = if (json.has("severity")) json.getInt("severity") else null
            val startTimeMs = json.optLong("startTime", entity.cachedAt)
            val expectedEnd = if (json.has("expectedEnd")) Instant.ofEpochMilli(json.getLong("expectedEnd")) else null
            val actualEnd = if (json.has("actualEnd")) Instant.ofEpochMilli(json.getLong("actualEnd")) else null
            val statusStr = json.optString("status", IncidentStatus.ACTIVE.name)
            val status = try { IncidentStatus.valueOf(statusStr) } catch (e: Exception) { IncidentStatus.ACTIVE }
            val desc = if (json.has("desc")) json.getString("desc") else null
            val source = json.optString("source", "PERSISTED_CACHE")
            val retrievedAtMs = json.optLong("retrievedAt", entity.cachedAt)
            val conf = json.optDouble("confidence", 0.9)
            val uncert = json.optDouble("uncertainty", 0.1)
            val freshnessStr = json.optString("freshness", TrafficFreshness.REASONABLY_FRESH.name)
            val freshness = try { TrafficFreshness.valueOf(freshnessStr) } catch (e: Exception) { TrafficFreshness.REASONABLY_FRESH }

            TrafficIncident(
                id = id,
                coordinate = GeoCoordinate(lat, lon),
                type = type,
                severity = severity,
                startTime = Instant.ofEpochMilli(startTimeMs),
                expectedEndTime = expectedEnd,
                actualEndTime = actualEnd,
                status = status,
                description = desc,
                source = source,
                retrievedAt = Instant.ofEpochMilli(retrievedAtMs),
                confidence = conf,
                uncertainty = uncert,
                freshness = freshness
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieves cached weather observations for a specific location and time range
     */
    suspend fun getCachedWeatherObservations(
        latitude: Double,
        longitude: Double,
        startTime: Instant,
        endTime: Instant
    ): List<WeatherObservation> {
        cacheDao?.pruneExpired(System.currentTimeMillis())

        val inMemory = weatherObservationsCache.filter { obs ->
            abs(obs.coordinate.latitude - latitude) < 0.1 &&
                abs(obs.coordinate.longitude - longitude) < 0.1 &&
                !obs.timestamp.isBefore(startTime) &&
                !obs.timestamp.isAfter(endTime) &&
                isCacheValid(obs, defaultTtlSeconds)
        }
        if (inMemory.isNotEmpty()) return inMemory

        // If in-memory is empty (e.g. after process restart), query Room cache
        val dao = cacheDao ?: return emptyList()
        val entities = dao.getValidEntriesInBounds(
            cacheType = "WEATHER_OBSERVATION",
            minLat = latitude - 0.1,
            maxLat = latitude + 0.1,
            minLon = longitude - 0.1,
            maxLon = longitude + 0.1
        )
        val restored = entities.mapNotNull { parseWeatherObservation(it) }.filter { obs ->
            !obs.timestamp.isBefore(startTime) &&
                !obs.timestamp.isAfter(endTime) &&
                isCacheValid(obs, defaultTtlSeconds)
        }
        weatherObservationsCache.addAll(restored)
        return restored
    }

    /**
     * Retrieves cached forecast weather points for a specific location and time range
     */
    suspend fun getCachedForecastWeatherPoints(
        latitude: Double,
        longitude: Double,
        startTime: Instant,
        endTime: Instant
    ): List<ForecastWeatherPoint> {
        cacheDao?.pruneExpired(System.currentTimeMillis())

        val inMemory = forecastPointsCache.filter { fc ->
            abs(fc.coordinate.latitude - latitude) < 0.1 &&
                abs(fc.coordinate.longitude - longitude) < 0.1 &&
                !fc.validTime.isBefore(startTime) &&
                !fc.validTime.isAfter(endTime) &&
                isCacheValid(fc, defaultTtlSeconds)
        }
        if (inMemory.isNotEmpty()) return inMemory

        val dao = cacheDao ?: return emptyList()
        val entities = dao.getValidEntriesInBounds(
            cacheType = "WEATHER_FORECAST",
            minLat = latitude - 0.1,
            maxLat = latitude + 0.1,
            minLon = longitude - 0.1,
            maxLon = longitude + 0.1
        )
        val restored = entities.mapNotNull { parseForecastPoint(it) }.filter { fc ->
            !fc.validTime.isBefore(startTime) &&
                !fc.validTime.isAfter(endTime) &&
                isCacheValid(fc, defaultTtlSeconds)
        }
        forecastPointsCache.addAll(restored)
        return restored
    }

    /**
     * Retrieves cached traffic observations for a specific location and time range
     */
    suspend fun getCachedTrafficObservations(
        latitude: Double,
        longitude: Double,
        startTime: Instant,
        endTime: Instant
    ): List<TrafficObservation> {
        cacheDao?.pruneExpired(System.currentTimeMillis())

        val inMemory = trafficObservationsCache.filter { obs ->
            abs(obs.coordinate.latitude - latitude) < 0.1 &&
                abs(obs.coordinate.longitude - longitude) < 0.1 &&
                !obs.timestamp.isBefore(startTime) &&
                !obs.timestamp.isAfter(endTime) &&
                isCacheValid(obs, defaultTtlSeconds)
        }
        if (inMemory.isNotEmpty()) return inMemory

        val dao = cacheDao ?: return emptyList()
        val entities = dao.getValidEntriesInBounds(
            cacheType = "TRAFFIC_OBSERVATION",
            minLat = latitude - 0.1,
            maxLat = latitude + 0.1,
            minLon = longitude - 0.1,
            maxLon = longitude + 0.1
        )
        val restored = entities.mapNotNull { parseTrafficObservation(it) }.filter { obs ->
            !obs.timestamp.isBefore(startTime) &&
                !obs.timestamp.isAfter(endTime) &&
                isCacheValid(obs, defaultTtlSeconds)
        }
        trafficObservationsCache.addAll(restored)
        return restored
    }

    /**
     * Retrieves cached traffic incidents for a specific location and time range
     */
    suspend fun getCachedTrafficIncidents(
        latitude: Double,
        longitude: Double,
        startTime: Instant,
        endTime: Instant
    ): List<TrafficIncident> {
        cacheDao?.pruneExpired(System.currentTimeMillis())

        val inMemory = trafficIncidentsCache.filter { inc ->
            abs(inc.coordinate.latitude - latitude) < 0.15 &&
                abs(inc.coordinate.longitude - longitude) < 0.15 &&
                !inc.startTime.isAfter(endTime) &&
                (inc.actualEndTime == null || !inc.actualEndTime.isBefore(startTime)) &&
                isCacheValid(inc, defaultTtlSeconds)
        }
        if (inMemory.isNotEmpty()) return inMemory

        val dao = cacheDao ?: return emptyList()
        val entities = dao.getValidEntriesInBounds(
            cacheType = "TRAFFIC_INCIDENT",
            minLat = latitude - 0.15,
            maxLat = latitude + 0.15,
            minLon = longitude - 0.15,
            maxLon = longitude + 0.15
        )
        val restored = entities.mapNotNull { parseTrafficIncident(it) }.filter { inc ->
            !inc.startTime.isAfter(endTime) &&
                (inc.actualEndTime == null || !inc.actualEndTime.isBefore(startTime)) &&
                isCacheValid(inc, defaultTtlSeconds)
        }
        trafficIncidentsCache.addAll(restored)
        return restored
    }

    /**
     * Invalidates cached data for a specific location and time range across all 4 L1 memory caches and Room L2 storage.
     */
    suspend fun invalidateCache(
        latitude: Double,
        longitude: Double,
        startTime: Instant,
        endTime: Instant
    ): Boolean {
        weatherObservationsCache.removeIf {
            abs(it.coordinate.latitude - latitude) < 0.1 &&
                abs(it.coordinate.longitude - longitude) < 0.1 &&
                !it.timestamp.isBefore(startTime) &&
                !it.timestamp.isAfter(endTime)
        }
        forecastPointsCache.removeIf {
            abs(it.coordinate.latitude - latitude) < 0.1 &&
                abs(it.coordinate.longitude - longitude) < 0.1 &&
                !it.validTime.isBefore(startTime) &&
                !it.validTime.isAfter(endTime)
        }
        trafficObservationsCache.removeIf {
            abs(it.coordinate.latitude - latitude) < 0.1 &&
                abs(it.coordinate.longitude - longitude) < 0.1 &&
                !it.timestamp.isBefore(startTime) &&
                !it.timestamp.isAfter(endTime)
        }
        trafficIncidentsCache.removeIf {
            abs(it.coordinate.latitude - latitude) < 0.15 &&
                abs(it.coordinate.longitude - longitude) < 0.15 &&
                !it.startTime.isAfter(endTime) &&
                (it.actualEndTime == null || !it.actualEndTime.isBefore(startTime))
        }
        cacheDao?.deleteInBounds(
            minLat = latitude - 0.15,
            maxLat = latitude + 0.15,
            minLon = longitude - 0.15,
            maxLon = longitude + 0.15
        )
        return true
    }

    /**
     * Determines if cached data is still valid based on freshness and expiration criteria.
     * - Tolerates minor clock skew up to 60 seconds (`-60L..maxAgeSeconds`), rejecting far-future corrupted timestamps.
     * - ForecastWeatherPoint: evaluates cache age against forecastGeneratedAt and ensures validTime has not expired (>1h ago).
     * - TrafficIncident: evaluates cache age against retrievedAt and ensures incident has not ended.
     */
    fun isCacheValid(cacheEntry: Any, maxAgeSeconds: Long): Boolean {
        if (maxAgeSeconds <= 0L) return false
        val now = Instant.now()
        val clockSkewToleranceSeconds = -60L
        return when (cacheEntry) {
            is WeatherObservation -> {
                val ageSeconds = Duration.between(cacheEntry.timestamp, now).seconds
                ageSeconds in clockSkewToleranceSeconds..maxAgeSeconds
            }
            is ForecastWeatherPoint -> {
                val generatedAgeSeconds = Duration.between(cacheEntry.forecastGeneratedAt, now).seconds
                val notExpiredSlot = !cacheEntry.validTime.isBefore(now.minusSeconds(3600))
                generatedAgeSeconds in clockSkewToleranceSeconds..maxAgeSeconds && notExpiredSlot
            }
            is TrafficObservation -> {
                val ageSeconds = Duration.between(cacheEntry.timestamp, now).seconds
                ageSeconds in clockSkewToleranceSeconds..maxAgeSeconds
            }
            is TrafficIncident -> {
                val retrievedAgeSeconds = Duration.between(cacheEntry.retrievedAt, now).seconds
                val hasEnded = (cacheEntry.actualEndTime != null && !cacheEntry.actualEndTime.isAfter(now)) ||
                    (cacheEntry.expectedEndTime != null && !cacheEntry.expectedEndTime.isAfter(now))
                retrievedAgeSeconds in clockSkewToleranceSeconds..maxAgeSeconds && !hasEnded
            }
            else -> false
        }
    }

    /**
     * Generates cache key for weather data
     */
    fun generateWeatherCacheKey(
        latitude: Double,
        longitude: Double,
        timestamp: Instant
    ): String {
        return "weather_${latitude}_${longitude}_${timestamp.toEpochMilli()}"
    }

    /**
     * Generates cache key for traffic data
     */
    fun generateTrafficCacheKey(
        latitude: Double,
        longitude: Double,
        timestamp: Instant
    ): String {
        return "traffic_${latitude}_${longitude}_${timestamp.toEpochMilli()}"
    }

    /**
     * Determines cache state based on data freshness and validity
     */
    fun determineCacheState(
        freshnessState: FreshnessStateMachine.DataFreshnessState,
        isValid: Boolean
    ): CacheState {
        return when {
            !isValid -> CacheState.INVALID
            freshnessState == FreshnessStateMachine.DataFreshnessState.FRESH -> CacheState.LIVE
            freshnessState == FreshnessStateMachine.DataFreshnessState.STALE_BUT_USABLE -> CacheState.CACHED
            freshnessState == FreshnessStateMachine.DataFreshnessState.EXPIRED -> CacheState.STALE
            freshnessState == FreshnessStateMachine.DataFreshnessState.UNKNOWN -> CacheState.UNKNOWN
            else -> CacheState.INVALID
        }
    }

    enum class CacheState {
        LIVE,
        CACHED,
        STALE,
        EXPIRED,
        INVALID,
        UNKNOWN
    }
}

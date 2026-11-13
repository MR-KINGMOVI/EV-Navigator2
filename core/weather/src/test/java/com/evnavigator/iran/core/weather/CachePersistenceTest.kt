package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class CachePersistenceTest {
    
    private val cachePersistence = CachePersistence()
    
    @Test
    fun `test checkFreshness with fresh data`() {
        // Given
        val now = Instant.now()
        val freshnessThreshold = 3600L // 1 hour in seconds
        
        // When
        val result = cachePersistence.checkFreshness(now.minusSeconds(300), freshnessThreshold)
        
        // Then
        assertEquals(Freshness.FRESH, result)
    }
    
    @Test
    fun `test checkFreshness with stale data`() {
        // Given
        val now = Instant.now()
        val freshnessThreshold = 3600L // 1 hour in seconds
        
        // When
        val result = cachePersistence.checkFreshness(now.minusSeconds(7200), freshnessThreshold)
        
        // Then
        assertEquals(Freshness.STALE_BUT_USABLE, result)
    }
    
    @Test
    fun `test checkFreshness with expired data`() {
        // Given
        val now = Instant.now()
        val freshnessThreshold = 3600L // 1 hour in seconds
        
        // When
        val result = cachePersistence.checkFreshness(now.minusSeconds(10000), freshnessThreshold)
        
        // Then
        assertEquals(Freshness.EXPIRED, result)
    }
    
    @Test
    fun `test shouldUseCachedData with fresh data`() {
        // When
        val result = cachePersistence.shouldUseCachedData(Freshness.FRESH, true)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `test shouldUseCachedData with stale but usable data and allow stale`() {
        // When
        val result = cachePersistence.shouldUseCachedData(Freshness.STALE_BUT_USABLE, true)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `test shouldUseCachedData with stale but usable data and no allow stale`() {
        // When
        val result = cachePersistence.shouldUseCachedData(Freshness.STALE_BUT_USABLE, false)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test shouldUseCachedData with expired data`() {
        // When
        val result = cachePersistence.shouldUseCachedData(Freshness.EXPIRED, true)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `test CacheType enum values`() {
        val expectedValues = setOf(
            CacheType.WEATHER_OBSERVATIONS,
            CacheType.WEATHER_FORECASTS,
            CacheType.TRAFFIC_OBSERVATIONS,
            CacheType.TRAFFIC_INCIDENTS
        )
        
        assertEquals(expectedValues, CacheType.values().toSet())
    }
    
    @Test
    fun `test CacheMetadata data class`() {
        // When
        val metadata = CacheMetadata(
            cacheType = CacheType.WEATHER_OBSERVATIONS,
            entryCount = 100,
            totalSizeBytes = 102400,
            lastUpdated = Instant.now(),
            maxAgeSeconds = 3600,
            schemaVersion = "1.0"
        )
        
        // Then
        assertEquals(CacheType.WEATHER_OBSERVATIONS, metadata.cacheType)
        assertEquals(100, metadata.entryCount)
        assertEquals(102400, metadata.totalSizeBytes)
        assertEquals("1.0", metadata.schemaVersion)
    }
    
    @Test
    fun `test CachedWeatherData data class`() {
        // When
        val cachedData = CachedWeatherData(
            observations = emptyList(),
            forecasts = emptyList(),
            freshness = Freshness.FRESH,
            timestamp = Instant.now(),
            source = "TestSource"
        )
        
        // Then
        assertTrue(cachedData.observations.isEmpty())
        assertTrue(cachedData.forecasts.isEmpty())
        assertEquals(Freshness.FRESH, cachedData.freshness)
        assertEquals("TestSource", cachedData.source)
    }
    
    @Test
    fun `test CachedTrafficData data class`() {
        // When
        val cachedData = CachedTrafficData(
            observations = emptyList(),
            incidents = emptyList(),
            freshness = Freshness.FRESH,
            timestamp = Instant.now(),
            source = "TestSource"
        )
        
        // Then
        assertTrue(cachedData.observations.isEmpty())
        assertTrue(cachedData.incidents.isEmpty())
        assertEquals(Freshness.FRESH, cachedData.freshness)
        assertEquals("TestSource", cachedData.source)
    }
    
    @Test
    fun `test WeatherCacheEntity data class`() {
        // When
        val entity = WeatherCacheEntity(
            id = "123",
            latitude = 35.6895,
            longitude = 51.3890,
            timestamp = Instant.now(),
            observationJson = "{}",
            forecastJson = "{}",
            freshness = Freshness.FRESH,
            source = "TestSource",
            validFrom = Instant.now(),
            validTo = Instant.now().plusSeconds(3600),
            schemaVersion = "1.0"
        )
        
        // Then
        assertEquals("123", entity.id)
        assertEquals(35.6895, entity.latitude)
        assertEquals(51.3890, entity.longitude)
        assertEquals("TestSource", entity.source)
        assertEquals("1.0", entity.schemaVersion)
    }
    
    @Test
    fun `test TrafficCacheEntity data class`() {
        // When
        val entity = TrafficCacheEntity(
            id = "456",
            latitude = 35.6895,
            longitude = 51.3890,
            timestamp = Instant.now(),
            observationJson = "{}",
            incidentJson = "{}",
            freshness = Freshness.FRESH,
            source = "TestSource",
            validFrom = Instant.now(),
            validTo = Instant.now().plusSeconds(3600),
            schemaVersion = "1.0"
        )
        
        // Then
        assertEquals("456", entity.id)
        assertEquals(35.6895, entity.latitude)
        assertEquals(51.3890, entity.longitude)
        assertEquals("TestSource", entity.source)
        assertEquals("1.0", entity.schemaVersion)
    }

    private class FakeEnvironmentCacheDao : com.evnavigator.iran.domain.repository.EnvironmentCacheStore {
        val storage = java.util.concurrent.ConcurrentHashMap<String, com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord>()

        override suspend fun insertOrUpdate(entry: com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord) {
            storage[entry.key] = entry
        }

        override suspend fun insertAll(entries: List<com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord>) {
            entries.forEach { storage[it.key] = it }
        }

        override suspend fun getValidEntry(key: String, now: Long): com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord? {
            val entry = storage[key] ?: return null
            return if (entry.expiresAt > now) entry else null
        }

        override suspend fun getValidEntriesInBounds(
            cacheType: String,
            minLat: Double,
            maxLat: Double,
            minLon: Double,
            maxLon: Double,
            now: Long
        ): List<com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord> {
            return storage.values.filter {
                it.cacheType == cacheType &&
                    it.expiresAt > now &&
                    it.latitude in minLat..maxLat &&
                    it.longitude in minLon..maxLon
            }
        }

        override suspend fun pruneExpired(now: Long) {
            val expired = storage.values.filter { it.expiresAt <= now }
            expired.forEach { storage.remove(it.key) }
        }

        override suspend fun deleteInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
            val inBounds = storage.values.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
            inBounds.forEach { storage.remove(it.key) }
        }

        override suspend fun clearAll() {
            storage.clear()
        }
    }

    @Test
    fun `test production environment throws if dao is missing`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            CachePersistence(cacheDao = null, isProductionEnvironment = true)
        }
        assertTrue(ex.message?.contains("strictly mandatory in production") == true)
    }

    @Test
    fun `test conflict resolution newer data never overwritten by stale data`() = kotlinx.coroutines.test.runTest {
        val dao = FakeEnvironmentCacheDao()
        val cache = CachePersistence(cacheDao = dao)
        val coord = com.evnavigator.iran.core.domain.GeoCoordinate(35.70, 51.40)
        val tNewer = Instant.now()
        val tOlder = tNewer.minusSeconds(600)

        val obsNewer = WeatherObservation(
            coordinate = coord,
            timestamp = tNewer,
            temperatureCelsius = 25.0,
            apparentTemperatureCelsius = 25.0,
            windSpeedMps = 3.0,
            windDirectionDegrees = 180.0,
            precipitationMm = 0.0,
            humidityPercentage = 30.0,
            pressureHpa = 1013.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 10.0,
            condition = WeatherCondition.CLEAR
        )

        val obsOlder = WeatherObservation(
            coordinate = coord,
            timestamp = tOlder,
            temperatureCelsius = 15.0,
            apparentTemperatureCelsius = 15.0,
            windSpeedMps = 5.0,
            windDirectionDegrees = 90.0,
            precipitationMm = 2.0,
            humidityPercentage = 60.0,
            pressureHpa = 1010.0,
            visibilityMeters = 5000.0,
            cloudCoverPercentage = 80.0,
            condition = WeatherCondition.RAIN
        )

        // Save newer observation first
        cache.saveWeatherObservations(listOf(obsNewer))

        // Attempt to save older observation at same coordinate
        cache.saveWeatherObservations(listOf(obsOlder))

        val results = cache.getCachedWeatherObservations(
            latitude = 35.70,
            longitude = 51.40,
            startTime = tOlder.minusSeconds(60),
            endTime = tNewer.plusSeconds(60)
        )

        assertEquals(1, results.size)
        assertEquals(25.0, results[0].temperatureCelsius)
        assertEquals(tNewer, results[0].timestamp)
    }

    @Test
    fun `test cold start restoration from room for all four data types`() = kotlinx.coroutines.test.runTest {
        val sharedDao = FakeEnvironmentCacheDao()
        val firstSessionCache = CachePersistence(cacheDao = sharedDao)
        val coord = com.evnavigator.iran.core.domain.GeoCoordinate(35.6997, 51.3380)
        val now = Instant.now()

        // 1. Weather Observation
        val obs = WeatherObservation(
            coordinate = coord,
            timestamp = now,
            temperatureCelsius = 28.5,
            apparentTemperatureCelsius = 28.0,
            windSpeedMps = 4.0,
            windDirectionDegrees = 120.0,
            precipitationMm = 0.0,
            humidityPercentage = 25.0,
            pressureHpa = 1012.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 5.0,
            condition = WeatherCondition.CLEAR
        )
        firstSessionCache.saveWeatherObservations(listOf(obs))

        // 2. Weather Forecast
        val forecast = ForecastWeatherPoint(
            coordinate = coord,
            validTime = now.plusSeconds(1800),
            forecastGeneratedAt = now,
            temperatureCelsius = 30.0,
            apparentTemperatureCelsius = 29.5,
            windSpeedMps = 4.5,
            windDirectionDegrees = 130.0,
            precipitationMm = 0.0,
            precipitationProbability = 0.0,
            humidityPercentage = 22.0,
            pressureHpa = 1011.0,
            visibilityMeters = 10000.0,
            cloudCoverPercentage = 10.0,
            condition = WeatherCondition.CLEAR,
            source = "IRAN_METEOROLOGY",
            forecastHorizonHours = 0.5,
            confidence = 0.95,
            uncertainty = 0.05
        )
        firstSessionCache.saveForecastWeatherPoints(listOf(forecast))

        // 3. Traffic Observation
        val trafficObs = TrafficObservation(
            coordinate = coord,
            timestamp = now,
            speedMps = 25.0,
            freeFlowSpeedMps = 30.0,
            congestionRatio = 0.15,
            trafficClass = TrafficClass.FREE_FLOW,
            travelTimeMultiplier = 1.05,
            confidence = 0.95
        )
        firstSessionCache.saveTrafficObservations(listOf(trafficObs))

        // 4. Traffic Incident
        val incident = TrafficIncident(
            id = "incident_tehran_01",
            coordinate = coord,
            type = IncidentType.ACCIDENT,
            severity = 2,
            startTime = now,
            expectedEndTime = now.plusSeconds(3600),
            actualEndTime = null,
            status = IncidentStatus.ACTIVE,
            description = "تصادف در تقاطع آزادی",
            source = "TEHRAN_TRAFFIC_POLICE",
            retrievedAt = now,
            confidence = 0.9,
            uncertainty = 0.1,
            freshness = TrafficFreshness.VERY_FRESH
        )
        firstSessionCache.saveTrafficIncidents(listOf(incident))

        // Simulate application restart: instantiate a NEW CachePersistence instance with empty in-memory L1
        val restartedSessionCache = CachePersistence(cacheDao = sharedDao)

        // Query weather observation on cold restart
        val restoredObs = restartedSessionCache.getCachedWeatherObservations(
            latitude = 35.6997,
            longitude = 51.3380,
            startTime = now.minusSeconds(60),
            endTime = now.plusSeconds(60)
        )
        assertEquals(1, restoredObs.size)
        assertEquals(28.5, restoredObs[0].temperatureCelsius)

        // Query forecast on cold restart
        val restoredForecasts = restartedSessionCache.getCachedForecastWeatherPoints(
            latitude = 35.6997,
            longitude = 51.3380,
            startTime = now,
            endTime = now.plusSeconds(3600)
        )
        assertEquals(1, restoredForecasts.size)
        assertEquals(30.0, restoredForecasts[0].temperatureCelsius)

        // Query traffic observation on cold restart
        val restoredTraffic = restartedSessionCache.getCachedTrafficObservations(
            latitude = 35.6997,
            longitude = 51.3380,
            startTime = now.minusSeconds(60),
            endTime = now.plusSeconds(60)
        )
        assertEquals(1, restoredTraffic.size)
        assertEquals(25.0, restoredTraffic[0].speedMps)

        // Query traffic incident on cold restart
        val restoredIncidents = restartedSessionCache.getCachedTrafficIncidents(
            latitude = 35.6997,
            longitude = 51.3380,
            startTime = now.minusSeconds(60),
            endTime = now.plusSeconds(3600)
        )
        assertEquals(1, restoredIncidents.size)
        assertEquals("incident_tehran_01", restoredIncidents[0].id)
    }

    @Test
    fun `test forecast cache rejects expired validTime even if forecastGeneratedAt is recent`() = kotlinx.coroutines.test.runTest {
        val dao = FakeEnvironmentCacheDao()
        val cache = CachePersistence(cacheDao = dao)
        val coord = com.evnavigator.iran.core.domain.GeoCoordinate(35.72, 51.42)
        val now = Instant.now()

        // Forecast generated 10 minutes ago, but validTime was 5 minutes ago (already passed)
        val pastForecast = ForecastWeatherPoint(
            coordinate = coord,
            validTime = now.minusSeconds(300),
            forecastGeneratedAt = now.minusSeconds(600),
            temperatureCelsius = 22.0
        )
        cache.saveForecastWeatherPoints(listOf(pastForecast))

        val validResults = cache.getCachedForecastWeatherPoints(
            latitude = 35.72,
            longitude = 51.42,
            startTime = now.minusSeconds(1800),
            endTime = now.plusSeconds(1800)
        )
        assertTrue(validResults.isEmpty(), "Forecast whose validTime has already passed must not be returned as valid cache")
    }

    @Test
    fun `test traffic incident cache rejects resolved or expired incident`() = kotlinx.coroutines.test.runTest {
        val dao = FakeEnvironmentCacheDao()
        val cache = CachePersistence(cacheDao = dao)
        val coord = com.evnavigator.iran.core.domain.GeoCoordinate(35.75, 51.35)
        val now = Instant.now()

        val expiredIncident = TrafficIncident(
            id = "inc_expired_01",
            coordinate = coord,
            type = IncidentType.ROAD_CLOSURE,
            severity = 3,
            startTime = now.minusSeconds(3600),
            expectedEndTime = now.minusSeconds(60), // Ended 1 minute ago
            actualEndTime = null,
            status = IncidentStatus.ACTIVE,
            retrievedAt = now.minusSeconds(120)
        )
        cache.saveTrafficIncidents(listOf(expiredIncident))

        val activeIncidents = cache.getCachedTrafficIncidents(
            latitude = 35.75,
            longitude = 51.35,
            startTime = now.minusSeconds(7200),
            endTime = now.plusSeconds(3600)
        )
        assertTrue(activeIncidents.isEmpty(), "Incident whose expectedEndTime has passed must be rejected by cache validity check")
    }
}
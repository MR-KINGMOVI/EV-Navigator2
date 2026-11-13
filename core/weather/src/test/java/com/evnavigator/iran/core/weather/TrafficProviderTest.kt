package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.RouteSegment
import com.evnavigator.iran.core.weather.model.TrafficClass
import com.evnavigator.iran.core.weather.model.TrafficObservation
import com.evnavigator.iran.core.weather.provider.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class TrafficProviderTest {

    @Test
    fun testOfflineTrafficProviderGeneratesReferenceSpeedsWithoutFakeLiveCongestion() = runTest {
        val provider = OfflineTrafficEstimator()
        assertTrue(provider.isAvailable())

        val segment = RouteSegment(
            id = "seg1",
            roadId = "road_freeway5",
            startCoordinate = GeoCoordinate(35.6997, 51.3380),
            endCoordinate = GeoCoordinate(34.6401, 50.8764),
            lengthMeters = 148_000.0,
            gradientPercent = 0.0,
            speedLimitKmh = 120
        )

        val result = provider.getTrafficForSegments(listOf(segment))
        assertEquals(TrafficProviderType.OFFLINE_ESTIMATION, result.providerType)
        assertTrue(result.isOfflineFallback)
        assertEquals(1, result.observations.size)

        val obs = result.observations.first()
        assertEquals(120.0 / 3.6, obs.speedMps ?: 0.0, 0.1)
        // P0-04: Road speed limit is NOT live traffic. When offline, status is honest UNKNOWN with 0.0 live confidence.
        assertEquals(TrafficClass.UNKNOWN, obs.trafficClass)
        assertNull(obs.congestionRatio)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun testRealTrafficProviderUnavailableReturnsGracefulEmptyResult() = runTest {
        val realProvider = RealTrafficProvider(isServiceReachable = false)
        assertFalse(realProvider.isAvailable())

        val segment = RouteSegment(
            id = "seg1",
            roadId = "road_freeway5",
            startCoordinate = GeoCoordinate(35.6997, 51.3380),
            endCoordinate = GeoCoordinate(34.6401, 50.8764),
            lengthMeters = 148_000.0,
            gradientPercent = 0.0,
            speedLimitKmh = 120
        )

        val result = realProvider.getTrafficForSegments(listOf(segment))
        assertEquals(TrafficProviderStatus.UNAVAILABLE, result.providerStatus)
        assertEquals(0.0, result.confidence)
        assertTrue(result.observations.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun testExpiredTrafficObservationIsDiscardedAndSetToUnknown() {
        val integration = TrafficRouteIntegration()
        val segment = RouteSegment(
            id = "seg_expired",
            distance = 2000.0,
            duration = 120.0
        )

        // Observation from 2 hours ago (expired > 15 minutes)
        val expiredObs = TrafficObservation(
            coordinate = GeoCoordinate(35.7500, 51.3600),
            timestamp = Instant.now().minus(Duration.ofHours(2)),
            speed = 5.0,
            freeFlowSpeed = 25.0,
            trafficClass = TrafficClass.SEVERE
        )

        val result = integration.mapTrafficToRouteSegments(listOf(segment), listOf(expiredObs))
        val data = result[segment]
        assertNotNull(data)
        // Stale data older than 15 mins must not be used as active traffic
        assertEquals(TrafficClass.UNKNOWN, data!!.trafficClass)
        assertEquals(0.0, data.confidence)
    }

    @Test
    fun testMockTrafficProviderIsolationForTests() = runTest {
        val mockObs = listOf(
            TrafficObservation(
                coordinate = GeoCoordinate(35.70, 51.30),
                speed = 20.0,
                freeFlowSpeed = 25.0,
                trafficClass = TrafficClass.LIGHT
            )
        )
        val mockProvider = MockTrafficProvider(mockObservations = mockObs, isSimulatedAvailable = true)
        assertEquals(TrafficProviderType.MOCK_TEST, mockProvider.providerType)
        assertTrue(mockProvider.isAvailable())

        val result = mockProvider.getTrafficForSegments(emptyList())
        assertEquals(TrafficDataSource.TEST_MOCK, result.dataSource)
        assertEquals(1, result.observations.size)
    }

    @Test
    fun testCompositeTrafficProviderFallsBackToOfflineWhenRealFails() = runTest {
        val failingRealProvider = MockTrafficProvider(isSimulatedAvailable = false)
        val composite = CompositeTrafficProvider(realProvider = failingRealProvider)

        val segment = RouteSegment(
            id = "seg1",
            roadId = "road_1",
            startCoordinate = GeoCoordinate(35.6997, 51.3380),
            endCoordinate = GeoCoordinate(35.7000, 51.3400),
            lengthMeters = 500.0,
            gradientPercent = 0.0,
            speedLimitKmh = 80
        )

        val result = composite.getTrafficForSegments(listOf(segment))
        assertTrue(result.isOfflineFallback)
        assertEquals(1, result.observations.size)
    }

    @Test
    fun testSpatialRelevanceFiltersDistantObservations() {
        val integration = TrafficRouteIntegration()

        // Segment in Tehran
        val tehranSegment = RouteSegment(
            id = "tehran_seg",
            roadId = "hemmat",
            startCoordinate = GeoCoordinate(35.7500, 51.3500),
            endCoordinate = GeoCoordinate(35.7500, 51.3700),
            lengthMeters = 1800.0,
            gradientPercent = 0.0
        )

        // Observation 1: On the segment in Tehran
        val nearObs = TrafficObservation(
            coordinate = GeoCoordinate(35.7500, 51.3600),
            timestamp = Instant.now(),
            speed = 10.0,
            freeFlowSpeed = 25.0,
            trafficClass = TrafficClass.HEAVY
        )

        // Observation 2: Far away in Isfahan (> 300km away)
        val farObs = TrafficObservation(
            coordinate = GeoCoordinate(32.6370, 51.6830),
            timestamp = Instant.now(),
            speed = 5.0,
            freeFlowSpeed = 25.0,
            trafficClass = TrafficClass.SEVERE
        )

        val result = integration.mapTrafficToRouteSegments(listOf(tehranSegment), listOf(farObs))
        val data = result[tehranSegment]
        assertNotNull(data)
        assertEquals(TrafficClass.UNKNOWN, data!!.trafficClass, "Distant Isfahan traffic must NOT affect Tehran segment")

        val resultWithNear = integration.mapTrafficToRouteSegments(listOf(tehranSegment), listOf(nearObs, farObs))
        val dataWithNear = resultWithNear[tehranSegment]
        assertNotNull(dataWithNear)
        assertEquals(TrafficClass.HEAVY, dataWithNear!!.trafficClass, "Near Tehran observation must be applied")
    }

    @Test
    fun testRealTrafficProviderWithEmptyObservationsReturnsUnavailableAndZeroConfidence() = runTest {
        val realProvider = RealTrafficProvider(isServiceReachable = true)
        assertTrue(realProvider.isAvailable())

        val segment = RouteSegment(
            id = "seg1",
            roadId = "road_freeway5",
            startCoordinate = GeoCoordinate(35.6997, 51.3380),
            endCoordinate = GeoCoordinate(34.6401, 50.8764),
            lengthMeters = 148_000.0,
            gradientPercent = 0.0,
            speedLimitKmh = 120
        )

        val result = realProvider.getTrafficForSegments(listOf(segment))
        assertEquals(TrafficProviderStatus.UNAVAILABLE, result.providerStatus)
        assertEquals(0.0, result.confidence)
        assertTrue(result.observations.isEmpty())
        assertEquals(TrafficDataSource.UNKNOWN_NO_DATA, result.dataSource)
    }

    @Test
    fun testFutureTrafficObservationIsRejectedAndSetToUnknown() {
        val integration = TrafficRouteIntegration()
        val segment = RouteSegment(
            id = "seg_future",
            distance = 2000.0,
            duration = 120.0
        )

        // Invalid observation with future timestamp (1 hour ahead)
        val futureObs = TrafficObservation(
            coordinate = GeoCoordinate(35.7500, 51.3600),
            timestamp = Instant.now().plus(Duration.ofHours(1)),
            speed = 5.0,
            freeFlowSpeed = 25.0,
            trafficClass = TrafficClass.SEVERE,
            confidence = 0.95
        )

        val result = integration.mapTrafficToRouteSegments(listOf(segment), listOf(futureObs))
        val data = result[segment]
        assertNotNull(data)
        assertEquals(TrafficClass.UNKNOWN, data!!.trafficClass, "Future observation must be rejected")
        assertEquals(0.0, data.confidence, "Future observation must yield 0.0 confidence")
    }
}

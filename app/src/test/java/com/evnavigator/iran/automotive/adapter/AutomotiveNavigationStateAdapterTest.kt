package com.evnavigator.iran.automotive.adapter

import com.evnavigator.iran.domain.model.*
import com.evnavigator.iran.domain.model.navigation.*
import com.evnavigator.iran.domain.model.navigation.maneuver.ManeuverType
import com.evnavigator.iran.domain.model.navigation.maneuver.UpcomingManeuver
import org.junit.Test
import org.junit.Assert.*

class AutomotiveNavigationStateAdapterTest {

    private val adapter = AutomotiveNavigationStateAdapter()

    @Test
    fun `convertToAutomotiveState with null navigation state returns default state`() {
        val result = adapter.convertToAutomotiveState(null)
        
        assertEquals(AutomotiveNavigationStatus.IDLE, result.navigationStatus)
        assertNull(result.currentRoad)
        assertNull(result.currentManeuver)
        assertNull(result.nextManeuver)
        assertNull(result.distanceToManeuver)
        assertNull(result.remainingDistance)
        assertNull(result.ETA)
        assertNull(result.remainingTime)
        assertNull(result.SOC)
        assertNull(result.predictedArrivalSOC)
        assertNull(result.nextChargingStop)
        assertEquals(AutomotiveDataQuality.UNKNOWN, result.dataQuality)
        assertEquals(AutomotiveGPSQuality.UNKNOWN, result.GPSQuality)
        assertEquals(AutomotiveTrafficStatus.UNKNOWN, result.trafficStatus)
        assertEquals(AutomotiveWeatherStatus.UNKNOWN, result.weatherStatus)
    }

    @Test
    fun `convertToAutomotiveState converts basic navigation status and road`() {
        val domainState = createTestNavigationState()
        val result = adapter.convertToAutomotiveState(domainState)
        
        assertEquals(AutomotiveNavigationStatus.NAVIGATING, result.navigationStatus)
        assertEquals(85.0, result.SOC)
        assertEquals(65.0, result.predictedArrivalSOC)
        assertEquals("Test Road", result.currentRoad)
        assertNotNull(result.remainingDistance)
        assertEquals(5000.0, result.remainingDistance?.meters ?: 0.0, 0.001)
    }

    @Test
    fun `convertToAutomotiveState handles maneuver conversion`() {
        val domainState = createTestNavigationState()
        val result = adapter.convertToAutomotiveState(domainState)
        
        assertNotNull(result.currentManeuver)
        assertEquals(AutomotiveManeuverType.TURN_LEFT, result.currentManeuver?.type)
        assertEquals("Test Road", result.currentManeuver?.roadName)
        assertEquals("1A", result.currentManeuver?.exitNumber)
    }

    @Test
    fun `convertToAutomotiveState determines data quality correctly`() {
        val domainState = createTestNavigationState()
        val result = adapter.convertToAutomotiveState(domainState)
        
        // Since route and progress are present, should be LIVE quality
        assertEquals(AutomotiveDataQuality.LIVE, result.dataQuality)
        assertEquals(AutomotiveGPSQuality.GOOD, result.GPSQuality)
        assertEquals(AutomotiveTrafficStatus.LIVE, result.trafficStatus)
        assertEquals(AutomotiveWeatherStatus.LIVE, result.weatherStatus)
    }

    @Test
    fun `convertToAutomotiveState preserves semantic degradation statuses`() {
        val baseState = createTestNavigationState()
        
        val degraded = adapter.convertToAutomotiveState(baseState.copy(status = NavigationStatus.GPS_DEGRADED))
        assertEquals(AutomotiveNavigationStatus.GPS_DEGRADED, degraded.navigationStatus)

        val offline = adapter.convertToAutomotiveState(baseState.copy(status = NavigationStatus.OFFLINE))
        assertEquals(AutomotiveNavigationStatus.OFFLINE, offline.navigationStatus)

        val paused = adapter.convertToAutomotiveState(baseState.copy(status = NavigationStatus.PAUSED))
        assertEquals(AutomotiveNavigationStatus.PAUSED, paused.navigationStatus)

        val stopped = adapter.convertToAutomotiveState(baseState.copy(status = NavigationStatus.STOPPED))
        assertEquals(AutomotiveNavigationStatus.STOPPED, stopped.navigationStatus)
    }

    private fun createTestNavigationState(): NavigationState {
        val maneuver = UpcomingManeuver(
            type = ManeuverType.TURN_LEFT,
            roadName = "Test Road",
            exitNumber = "1A",
            distanceToManeuver = Distance(100.0),
            timeToManeuver = Duration(10L),
            direction = "left"
        )
        val nextManeuver = UpcomingManeuver(
            type = ManeuverType.TURN_RIGHT,
            roadName = "Next Road",
            exitNumber = "2B",
            distanceToManeuver = Distance(200.0),
            timeToManeuver = Duration(20L),
            direction = "right"
        )
        val route = Route(
            id = "test-route",
            start = GeoCoordinate(35.6895, 51.3890),
            end = GeoCoordinate(35.7704, 51.4540),
            segments = emptyList(),
            distance = Distance(10000.0),
            estimatedTime = 3600L
        )
        val progress = NavigationProgress(
            distanceTraveled = Distance(5000.0),
            totalDistance = Distance(10000.0),
            routePercentage = 0.5,
            elapsedTime = Duration(1800L),
            currentSegmentIndex = 0,
            totalSegments = 1
        )
        val etaEstimate = EtaEstimate(
            eta = 3600000L,
            travelTime = Duration(1800L),
            confidence = 0.95,
            isBasedOnLiveTraffic = true,
            adjustmentReason = null
        )

        return NavigationState(
            sessionId = NavigationSessionId("test-session"),
            status = NavigationStatus.NAVIGATING,
            route = route,
            routePosition = null,
            matchedLocation = null,
            progress = progress,
            upcomingManeuver = maneuver,
            currentManeuver = maneuver,
            nextManeuver = nextManeuver,
            eta = etaEstimate,
            remainingDistance = Distance(5000.0),
            remainingTime = Duration(1800L),
            currentSoc = 85.0,
            predictedArrivalSoc = 65.0,
            nextChargingStop = null,
            dataQuality = NavigationDataQuality.LIVE,
            gpsQuality = GpsQuality.GOOD,
            trafficStatus = TrafficStatus.NORMAL,
            weatherStatus = WeatherStatus.CLEAR
        )
    }
}

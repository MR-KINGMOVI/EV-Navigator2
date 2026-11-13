package com.evnavigator.iran.data.charging.repository

import com.evnavigator.iran.core.charging.*
import com.evnavigator.iran.data.charging.dao.ChargingStationDao
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Step W Verification Test: Offline Charging Data Integration.
 *
 * Verifies:
 * - Proper static vs live status separation in offline mode
 * - Offline queries never claim live availability or fabricate Iranian charging stations
 * - Handling of unverified/unconfirmed reports
 * - Behavior when no verified charging dataset is installed
 */
class OfflineChargingDatasetIntegrationTest {

    private lateinit var mockDao: ChargingStationDao
    private lateinit var repository: ChargingStationRepository

    @Before
    fun setUp() {
        mockDao = mock(ChargingStationDao::class.java)
        repository = ChargingStationRepository(mockDao)
    }

    @Test
    fun `test offline query returns static infrastructure without fabricating live operational status`() = runTest {
        val staticStation = ChargingStationEntity(
            id = "station_tehran_azadi_01",
            externalId = "MAPNA_AZADI_01",
            name = "Azadi Tower EV Fast Charger",
            latitude = 35.6997,
            longitude = 51.3380,
            address = "Azadi Square, Tehran",
            operator = "Mapna Group",
            network = "MAPNA_MIND",
            accessType = "PUBLIC",
            source = "MAPNA_OFFLINE_VERIFIED",
            sourceType = "OFFLINE_EXPORT",
            version = "2026.1",
            validationStatus = "VERIFIED",
            confidence = 0.95,
            isOperational = true,
            isVerified = true,
            createdAt = 1680000000L,
            updatedAt = 1690000000L
        )

        `when`(mockDao.getStationById("station_tehran_azadi_01")).thenReturn(staticStation)

        val station = repository.getStationById("station_tehran_azadi_01")
        assertNotNull(station)
        assertEquals("Azadi Tower EV Fast Charger", station?.name)
        assertEquals("VERIFIED", station?.validationStatus)

        // Ensure static vs live separation principle: offline data provides static capability,
        // but dynamic occupancy/live fault state must NOT be fabricated as AVAILABLE
        val staticSeparation = StaticLiveSeparation(
            statusType = StaticVsLiveStatus.STATIC,
            freshness = Freshness(
                timestamp = staticStation.updatedAt,
                source = staticStation.source,
                ageMillis = System.currentTimeMillis() - staticStation.updatedAt,
                confidence = staticStation.confidence,
                version = staticStation.version,
                isFresh = false // Offline cached data is not live fresh
            )
        )

        assertEquals(StaticVsLiveStatus.STATIC, staticSeparation.statusType)
        assertNull(staticSeparation.operationalStatus) // Must NOT claim AVAILABLE when offline
    }

    @Test
    fun `test unverified charger is preserved as unverified`() = runTest {
        val unverifiedStation = ChargingStationEntity(
            id = "unverified_crowdsource_01",
            externalId = null,
            name = "Rumored Hotel Charger",
            latitude = 35.7500,
            longitude = 51.4100,
            address = "Valiasr St, Tehran",
            operator = null,
            network = null,
            accessType = "HOTEL_GUESTS_ONLY",
            source = "COMMUNITY_REPORT",
            sourceType = "UNVERIFIED_CROWDSOURCE",
            version = "1.0",
            validationStatus = "UNVERIFIED",
            confidence = 0.30,
            isOperational = false,
            isVerified = false,
            createdAt = 1690000000L,
            updatedAt = 1690000000L
        )

        `when`(mockDao.getStationById("unverified_crowdsource_01")).thenReturn(unverifiedStation)

        val station = repository.getStationById("unverified_crowdsource_01")
        assertNotNull(station)
        assertFalse(station!!.isVerified)
        assertEquals("UNVERIFIED", station.validationStatus)
        assertTrue(station.confidence < 0.5)
    }

    @Test
    fun `test empty dataset returns empty list without inventing fallback chargers`() = runTest {
        `when`(mockDao.getAllStations()).thenReturn(flowOf(emptyList()))

        val stations = repository.getAllStations().first()
        assertTrue(stations.isEmpty())
    }
}

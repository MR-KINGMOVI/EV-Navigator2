package com.evnavigator.iran.data.search

import com.evnavigator.iran.core.energy.model.Energy
import com.evnavigator.iran.core.energy.model.Mass
import com.evnavigator.iran.core.vehicle.*
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.repository.ChargingStationRepository
import com.evnavigator.iran.data.vehicle.repository.VehicleRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for OfflineSearchService.
 * Verifies Persian/RTL normalization, deterministic ranking, pagination, and multi-domain search.
 */
class OfflineSearchServiceTest {

    private lateinit var mockChargingRepo: ChargingStationRepository
    private lateinit var mockVehicleRepo: VehicleRepository
    private lateinit var searchService: OfflineSearchService

    @Before
    fun setUp() {
        mockChargingRepo = mock(ChargingStationRepository::class.java)
        mockVehicleRepo = mock(VehicleRepository::class.java)
        searchService = OfflineSearchService(mockChargingRepo, mockVehicleRepo)
    }

    @Test
    fun `test Persian normalizer unifies Arabic Yeh Kaf and Persian digits`() {
        // Arabic Yeh (ي) vs Persian Yeh (ی), Arabic Kaf (ك) vs Persian Kehe (ک)
        val arabicInput = "مركز شارژ آزادي ۱۲۳"
        val expected = "مرکز شارژ آزادی 123"

        val normalized = PersianTextNormalizer.normalize(arabicInput)
        assertEquals(expected, normalized)
    }

    @Test
    fun `test exact match ranks higher than substring match`() = runTest {
        val stationExact = ChargingStationEntity(
            id = "s1",
            externalId = null,
            name = "Azadi Charger",
            latitude = 35.7,
            longitude = 51.3,
            address = "Tehran",
            operator = "Mapna",
            network = null,
            accessType = "PUBLIC",
            source = "SRC",
            sourceType = "TYPE",
            version = "1",
            validationStatus = "VERIFIED",
            confidence = 1.0,
            isOperational = true,
            isVerified = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val stationSubstring = ChargingStationEntity(
            id = "s2",
            externalId = null,
            name = "North Terminal Azadi Charger Substation",
            latitude = 35.8,
            longitude = 51.4,
            address = "Tehran",
            operator = "Mapna",
            network = null,
            accessType = "PUBLIC",
            source = "SRC",
            sourceType = "TYPE",
            version = "1",
            validationStatus = "VERIFIED",
            confidence = 1.0,
            isOperational = true,
            isVerified = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        `when`(mockChargingRepo.getAllStations()).thenReturn(flowOf(listOf(stationSubstring, stationExact)))
        `when`(mockVehicleRepo.getAllVehicles()).thenReturn(emptyList())

        val results = searchService.search("Azadi Charger")

        assertEquals(2, results.size)
        // Exact match should rank first
        assertEquals("s1", (results[0] as OfflineSearchResult.ChargingStationItem).id)
        assertEquals(1.0, results[0].rankScore, 0.001)
        assertEquals("s2", (results[1] as OfflineSearchResult.ChargingStationItem).id)
        assertEquals(0.6, results[1].rankScore, 0.001)
    }

    @Test
    fun `test pagination with limit and offset`() = runTest {
        val s1 = ChargingStationEntity(id = "s1", canonicalName = "Charger 1", latitude = 35.0, longitude = 51.0, source = "S", sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN, retrievedDate = 0L, validationStatus = "VERIFIED")
        val s2 = ChargingStationEntity(id = "s2", canonicalName = "Charger 2", latitude = 35.0, longitude = 51.0, source = "S", sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN, retrievedDate = 0L, validationStatus = "VERIFIED")
        val s3 = ChargingStationEntity(id = "s3", canonicalName = "Charger 3", latitude = 35.0, longitude = 51.0, source = "S", sourceType = com.evnavigator.iran.core.charging.SourceType.UNKNOWN, retrievedDate = 0L, validationStatus = "VERIFIED")

        `when`(mockChargingRepo.getAllStations()).thenReturn(flowOf(listOf(s1, s2, s3)))
        `when`(mockVehicleRepo.getAllVehicles()).thenReturn(emptyList())

        val paged = searchService.search("Charger", limit = 2, offset = 1)

        assertEquals(2, paged.size)
    }
}

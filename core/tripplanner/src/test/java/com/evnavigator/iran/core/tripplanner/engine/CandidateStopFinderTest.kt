package com.evnavigator.iran.core.tripplanner.engine

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.tripplanner.model.TripConstraints
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import com.evnavigator.iran.core.vehicle.ConnectorType
import com.evnavigator.iran.domain.model.catalog.*
import com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandidateStopFinderTest {

    @Test
    fun testFindCandidates() = runBlocking {
        val mockRepository = mock(ChargingStationCatalogRepository::class.java)

        val station1 = DomainChargingStation(
            id = "station1",
            canonicalName = "Station 1",
            latitude = 35.6892,
            longitude = 51.3890,
            address = "Tehran",
            province = "Tehran",
            city = "Tehran",
            accessType = DomainAccessType.PUBLIC,
            is247 = true,
            operatorName = "Mapna",
            source = "test",
            sourceType = DomainSourceType.NETWORK_PROVIDER,
            validationStatus = "VERIFIED",
            retrievedDate = System.currentTimeMillis()
        )
        val station2 = station1.copy(
            id = "station2",
            canonicalName = "Station 2",
            latitude = 35.6900,
            longitude = 51.3895
        )
        val stationWithoutPorts = station1.copy(
            id = "station_empty",
            canonicalName = "Empty Station",
            latitude = 35.6891,
            longitude = 51.3891
        )

        val port1 = DomainChargingPort(
            id = "port1",
            stationId = "station1",
            connectorType = DomainConnectorType.CCS_COMBO_2,
            connectorCount = 1,
            voltage = 400.0,
            current = 250.0,
            powerKw = 100.0,
            isAc = false,
            isDc = true,
            phaseCount = 3,
            status = "AVAILABLE",
            validationStatus = "VERIFIED",
            pricePerKwh = 1500.0
        )
        val port2 = port1.copy(id = "port2", stationId = "station2", powerKw = 150.0, current = 375.0)

        `when`(mockRepository.observeStationsNearby(anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(flowOf(listOf(station1, station2, stationWithoutPorts)))

        `when`(mockRepository.getStationWithPorts("station1"))
            .thenReturn(DomainChargingStationWithPorts(station = station1, ports = listOf(port1)))

        `when`(mockRepository.getStationWithPorts("station2"))
            .thenReturn(DomainChargingStationWithPorts(station = station2, ports = listOf(port2)))

        `when`(mockRepository.getStationWithPorts("station_empty"))
            .thenReturn(DomainChargingStationWithPorts(station = stationWithoutPorts, ports = emptyList()))

        val finder = CandidateStopFinder(mockRepository)

        val constraints = TripConstraints(
            maxDetourKm = 5.0,
            preferredConnectorTypes = setOf(com.evnavigator.iran.core.charging.ConnectorType.CCS_COMBO_2),
            verifiedStationsOnly = true
        )

        val vehicleSpec = ChargingSpecification(
            acChargingMaxPowerKw = null,
            dcChargingMaxPowerKw = com.evnavigator.iran.core.energy.model.Power.fromKilowatts(100.0),
            chargingVoltageRangeV = null,
            chargingCurrentRangeA = null,
            connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
            chargePortCount = 1,
            chargingEfficiency = null,
            chargingTimeEstimate = null,
            isFastChargingCapable = true,
            isV2GCompatible = false
        )

        val candidates = finder.findCandidates(
            nearPoint = GeoCoordinate(35.6890, 51.3890),
            vehicleChargingSpec = vehicleSpec,
            constraints = constraints
        )

        // Station without ports is rejected (never fabricated with a fake 22 kW port)
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.id.id == "station1" })
        assertTrue(candidates.any { it.id.id == "station2" })
        assertTrue(candidates.none { it.id.id == "station_empty" })
    }
}

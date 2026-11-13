package com.evnavigator.iran.core.tripplanner.model

import com.evnavigator.iran.core.charging.*
import com.evnavigator.iran.core.charging.GeoCoordinate
import org.junit.Test
import kotlin.test.assertEquals

class TripPlanModelTest {
    
    @Test
    fun testTripConstraints() {
        val constraints = TripConstraints(
            minSocBufferPercent = 15.0,
            maxSocChargeTargetPercent = 85.0,
            maxDetourKm = 3.0,
            preferredConnectorTypes = setOf(),
            minStationPowerKw = 50.0,
            verifiedStationsOnly = true
        )
        
        assertEquals(15.0, constraints.minSocBufferPercent)
        assertEquals(85.0, constraints.maxSocChargeTargetPercent)
        assertEquals(3.0, constraints.maxDetourKm)
        assertEquals(setOf(), constraints.preferredConnectorTypes)
        assertEquals(50.0, constraints.minStationPowerKw)
        assertEquals(true, constraints.verifiedStationsOnly)
    }
    
    @Test
    fun testTripPlanningStrategy() {
        assertEquals(TripPlanningStrategy.FASTEST_ARRIVAL, TripPlanningStrategy.FASTEST_ARRIVAL)
        assertEquals(TripPlanningStrategy.FEWEST_STOPS, TripPlanningStrategy.FEWEST_STOPS)
        assertEquals(TripPlanningStrategy.LOWEST_COST, TripPlanningStrategy.LOWEST_COST)
    }
    
    @Test
    fun testPlannedChargingStop() {
        val port = ChargingPort(
            id = "test_port",
            connectorType = ConnectorType.CCS_COMBO_2,
            power = 50.0,
            isAc = false,
            isDc = true,
            status = PortStatus.AVAILABLE
        )
        val station = ChargingStation(
            id = ChargingStationId.fromString("test_station"),
            location = StationLocation(coordinate = GeoCoordinate(35.6892, 51.3890)),
            metadata = StationMetadata(
                canonicalName = "Test Station",
                operatorId = null,
                operatorName = "Test Operator",
                networkId = null,
                networkName = null,
                accessType = AccessType.PUBLIC,
                accessRestrictions = null,
                openingHours = null,
                is247 = true,
                paymentMethods = null,
                amenities = null,
                contactPhone = null,
                contactEmail = null,
                website = null,
                notes = null,
                source = "test"
            ),
            ports = listOf(port),
            powerCapabilities = listOf(PowerCapability(powerKw = 50.0, voltage = 400.0, current = 125.0, isAc = false, isDc = true)),
            status = StationStatus.OPERATIONAL,
            isOperational = true,
            provenance = Provenance(source = "test")
        )
        
        val stop = PlannedChargingStop(
            station = station,
            arrivalDistanceMeters = 1000.0,
            detourDistanceMeters = 200.0,
            predictedArrivalSocPercent = 30.0,
            targetDepartureSocPercent = 80.0,
            estimatedChargingDurationMinutes = 45.0,
            estimatedEnergyAddedWh = 20000.0,
            estimatedCost = 1500.0
        )
        
        assertEquals(station, stop.station)
        assertEquals(1000.0, stop.arrivalDistanceMeters)
        assertEquals(200.0, stop.detourDistanceMeters)
        assertEquals(30.0, stop.predictedArrivalSocPercent)
        assertEquals(80.0, stop.targetDepartureSocPercent)
        assertEquals(45.0, stop.estimatedChargingDurationMinutes)
        assertEquals(20000.0, stop.estimatedEnergyAddedWh)
        assertEquals(1500.0, stop.estimatedCost)
    }
}

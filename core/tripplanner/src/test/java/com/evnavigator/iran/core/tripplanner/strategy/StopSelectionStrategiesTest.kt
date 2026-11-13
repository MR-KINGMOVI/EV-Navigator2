package com.evnavigator.iran.core.tripplanner.strategy

import com.evnavigator.iran.core.charging.*
import com.evnavigator.iran.core.charging.GeoCoordinate
import com.evnavigator.iran.core.energy.model.Power
import com.evnavigator.iran.core.tripplanner.engine.ChargingCurveModel
import com.evnavigator.iran.core.tripplanner.model.TripConstraints
import com.evnavigator.iran.core.vehicle.ChargingSpecification
import org.junit.Test
import kotlin.test.assertEquals

class StopSelectionStrategiesTest {

    private val String.id: String get() = this

    private fun createTestStation(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        powerKw: Double,
        pricePerKwh: Double? = 1500.0
    ): ChargingStation {
        val port = ChargingPort(
            id = "${id}_port",
            connectorType = ConnectorType.CCS_COMBO_2,
            power = powerKw,
            isAc = false,
            isDc = true,
            pricePerKwh = pricePerKwh,
            status = PortStatus.AVAILABLE
        )
        return ChargingStation(
            id = ChargingStationId.fromString(id),
            location = StationLocation(coordinate = GeoCoordinate(lat, lon)),
            metadata = StationMetadata(
                canonicalName = name,
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
            powerCapabilities = listOf(PowerCapability(powerKw = powerKw, isAc = false, isDc = true)),
            status = StationStatus.OPERATIONAL,
            isOperational = true,
            provenance = Provenance(source = "test")
        )
    }

    private val vehicleSpec = ChargingSpecification(
        acChargingMaxPowerKw = null,
        dcChargingMaxPowerKw = Power(150.0),
        chargingVoltageRangeV = null,
        chargingCurrentRangeA = null,
        connectorTypes = setOf(com.evnavigator.iran.core.vehicle.ConnectorType.CCS_COMBO_2),
        chargePortCount = 1,
        chargingEfficiency = null,
        chargingTimeEstimate = null,
        isFastChargingCapable = true,
        isV2GCompatible = false
    )

    @Test
    fun testSelectForFewestStops() {
        val station1 = createTestStation("station1", "Station 1", 35.6890, 51.3890, 100.0)
        val station2 = createTestStation("station2", "Station 2", 35.6895, 51.3895, 150.0)

        val candidates = listOf(station1, station2)
        val selected = StopSelectionStrategies.selectForFewestStops(candidates, vehicleSpec)
        assertEquals("station2", selected.id.id)
    }

    @Test
    fun testSelectForFastestArrival() {
        val station1 = createTestStation("station1", "Station 1", 35.6890, 51.3890, 100.0)
        val station2 = createTestStation("station2", "Station 2", 35.6895, 51.3895, 100.0)

        val candidates = listOf(station1, station2)
        val constraints = TripConstraints()
        val chargingCurveModel = ChargingCurveModel

        val selected = StopSelectionStrategies.selectForFastestArrival(
            candidates,
            vehicleSpec,
            500.0,
            constraints,
            chargingCurveModel
        )
        assertEquals("station1", selected.id.id)
    }

    @Test
    fun testSelectForLowestCost() {
        val station1 = createTestStation("station1", "Station 1", 35.6890, 51.3890, 100.0, pricePerKwh = 2000.0)
        val station2 = createTestStation("station2", "Station 2", 35.6895, 51.3895, 100.0, pricePerKwh = 1000.0)

        val candidates = listOf(station1, station2)
        val selected = StopSelectionStrategies.selectForLowestCost(candidates, vehicleSpec)
        assertEquals("station2", selected.id.id)
    }
}

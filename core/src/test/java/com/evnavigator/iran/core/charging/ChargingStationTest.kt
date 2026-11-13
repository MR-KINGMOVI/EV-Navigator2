package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertFailsWith as assertThrows
import java.util.*

class ChargingStationTest {

    @Test
    fun `test charging station creation with valid data`() {
        // Create a simple charging station for testing
        val stationId = ChargingStationId.createNew()
        val geoCoordinate = GeoCoordinate(35.6895, 51.3890) // Tehran coordinates
        val stationLocation = StationLocation(coordinate = geoCoordinate)
        val stationMetadata = StationMetadata(
            canonicalName = "Test Charging Station",
            operatorId = "test-operator",
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
            source = "test-source"
        )
        
        val chargingPort = ChargingPort(
            id = UUID.randomUUID().toString(),
            connectorType = ConnectorType.CCS_COMBO_2,
            connectorCount = 1,
            voltage = 400.0,
            current = 125.0,
            power = 50.0,
            isAc = false,
            isDc = true,
            phaseCount = 3,
            status = PortStatus.AVAILABLE
        )
        
        val powerCapability = PowerCapability(
            powerKw = 50.0,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true,
            confidence = 0.95
        )
        
        val provenanceModel = ProvenanceModel(
            source = "test-source",
            retrievedAt = System.currentTimeMillis(),
            version = "1.0"
        )

        // Create the station
        val station = ChargingStation(
            id = stationId,
            location = stationLocation,
            metadata = stationMetadata,
            ports = listOf(chargingPort),
            powerCapabilities = listOf(powerCapability),
            status = StationStatus.OPERATIONAL,
            provenance = provenanceModel
        )

        // Verify the station properties
        assert(station.id == stationId.id)
        assert(station.location == stationLocation)
        assert(station.metadata == stationMetadata)
        assert(station.ports == listOf(chargingPort))
        assert(station.powerCapabilities == listOf(powerCapability))
        assert(station.status == StationStatus.OPERATIONAL)
        assert(station.totalConnectorCount == 1)
        assert(station.connectorTypes.contains(ConnectorType.CCS_COMBO_2))
        assert(station.supportsDc)
        assert(!station.supportsAc)
        assert(station.isPublic)
    }

    @Test
    fun `test charging station with multiple ports`() {
        val stationId = ChargingStationId.createNew()
        val geoCoordinate = GeoCoordinate(35.6895, 51.3890)
        val stationLocation = StationLocation(coordinate = geoCoordinate)
        val stationMetadata = StationMetadata(
            canonicalName = "Multi-port Test Station",
            operatorId = "multi-operator",
            operatorName = "Multi Operator",
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
            source = "test-source"
        )
        
        val port1 = ChargingPort(
            id = "port-1",
            connectorType = ConnectorType.CCS_COMBO_2,
            connectorCount = 2,
            voltage = 400.0,
            current = 125.0,
            power = 50.0,
            isAc = false,
            isDc = true,
            phaseCount = 3,
            status = PortStatus.AVAILABLE
        )
        
        val port2 = ChargingPort(
            id = "port-2",
            connectorType = ConnectorType.TYPE_2,
            connectorCount = 1,
            voltage = 230.0,
            current = 32.0,
            power = 7.4,
            isAc = true,
            isDc = false,
            phaseCount = 1,
            status = PortStatus.AVAILABLE
        )
        
        val powerCapability = PowerCapability(
            powerKw = 50.0,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true,
            confidence = 0.95
        )
        
        val provenanceModel = ProvenanceModel(
            source = "test-source",
            retrievedAt = System.currentTimeMillis(),
            version = "1.0"
        )

        // Create the station with multiple ports
        val station = ChargingStation(
            id = stationId,
            location = stationLocation,
            metadata = stationMetadata,
            ports = listOf(port1, port2),
            powerCapabilities = listOf(powerCapability),
            status = StationStatus.OPERATIONAL,
            provenance = provenanceModel
        )

        // Verify properties for multiple ports
        assert(station.totalConnectorCount == 3) // 2 + 1 = 3
        assert(station.connectorTypes.contains(ConnectorType.CCS_COMBO_2))
        assert(station.connectorTypes.contains(ConnectorType.TYPE_2))
        assert(station.supportsDc)
        assert(station.supportsAc)
    }

    @Test
    fun `test charging station validation with no ports`() {
        val stationId = ChargingStationId.createNew()
        val geoCoordinate = GeoCoordinate(35.6895, 51.3890)
        val stationLocation = StationLocation(coordinate = geoCoordinate)
        val stationMetadata = StationMetadata(
            canonicalName = "Test Station",
            operatorId = "test-operator",
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
            source = "test-source"
        )
        
        val powerCapability = PowerCapability(
            powerKw = 50.0,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true,
            confidence = 0.95
        )
        
        val provenanceModel = ProvenanceModel(
            source = "test-source",
            retrievedAt = System.currentTimeMillis(),
            version = "1.0"
        )

        // This should throw an exception because no ports are provided
        assertThrows<IllegalArgumentException> {
            ChargingStation(
                id = stationId,
                location = stationLocation,
                metadata = stationMetadata,
                ports = emptyList(), // No ports - this should fail validation
                powerCapabilities = listOf(powerCapability),
                status = StationStatus.OPERATIONAL,
                provenance = provenanceModel
            )
        }
    }

    @Test
    fun `test charging station with duplicate port IDs`() {
        val stationId = ChargingStationId.createNew()
        val geoCoordinate = GeoCoordinate(35.6895, 51.3890)
        val stationLocation = StationLocation(coordinate = geoCoordinate)
        val stationMetadata = StationMetadata(
            canonicalName = "Test Station",
            operatorId = "test-operator",
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
            source = "test-source"
        )
        
        val port1 = ChargingPort(
            id = "duplicate-id",
            connectorType = ConnectorType.CCS_COMBO_2,
            connectorCount = 1,
            voltage = 400.0,
            current = 125.0,
            power = 50.0,
            isAc = false,
            isDc = true,
            phaseCount = 3,
            status = PortStatus.AVAILABLE
        )
        
        val port2 = ChargingPort(
            id = "duplicate-id", // Same ID as port1 - this should fail validation
            connectorType = ConnectorType.TYPE_2,
            connectorCount = 1,
            voltage = 230.0,
            current = 32.0,
            power = 7.4,
            isAc = true,
            isDc = false,
            phaseCount = 1,
            status = PortStatus.AVAILABLE
        )
        
        val powerCapability = PowerCapability(
            powerKw = 50.0,
            voltage = 400.0,
            current = 125.0,
            isAc = false,
            isDc = true,
            confidence = 0.95
        )
        
        val provenanceModel = ProvenanceModel(
            source = "test-source",
            retrievedAt = System.currentTimeMillis(),
            version = "1.0"
        )

        // This should throw an exception due to duplicate port IDs
        assertThrows<IllegalArgumentException> {
            ChargingStation(
                id = stationId,
                location = stationLocation,
                metadata = stationMetadata,
                ports = listOf(port1, port2),
                powerCapabilities = listOf(powerCapability),
                status = StationStatus.OPERATIONAL,
                provenance = provenanceModel
            )
        }
    }
}
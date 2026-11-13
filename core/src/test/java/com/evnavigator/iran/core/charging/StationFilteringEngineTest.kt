package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.vehicle.ConnectorType
import org.junit.Test
import kotlin.test.assertEquals

class StationFilteringEngineTest {

    @Test
    fun `test filter by connector type`() {
        // Create test stations with different connectors
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by CCS2 connector
        val criteria = StationFilterCriteria(
            connectorTypes = setOf(ConnectorType.CCS_COMBO_2)
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by minimum power`() {
        // Create test stations with different power capabilities
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by minimum power of 40kW
        val criteria = StationFilterCriteria(
            minPowerKw = 40.0
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by AC only`() {
        // Create test stations with different power capabilities
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by AC-only
        val criteria = StationFilterCriteria(
            isAcOnly = true
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by DC only`() {
        // Create test stations with different power capabilities
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by DC-only
        val criteria = StationFilterCriteria(
            isDcOnly = true
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station2", result[0].id)
    }

    @Test
    fun `test filter by operator`() {
        // Create test stations with different operators
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by operator A
        val criteria = StationFilterCriteria(
            operator = "Operator A"
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by network`() {
        // Create test stations with different networks
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by network X
        val criteria = StationFilterCriteria(
            network = "Network X"
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by access restriction`() {
        // Create test stations with different access types
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by public access
        val criteria = StationFilterCriteria(
            accessRestriction = AccessPaymentModel.AccessType.PUBLIC
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by province`() {
        // Create test stations with different provinces
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Isfahan",
                city = "Isfahan", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by Tehran province
        val criteria = StationFilterCriteria(
            province = "Tehran"
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by city`() {
        // Create test stations with different cities
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Isfahan",
                city = "Isfahan", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by Tehran city
        val criteria = StationFilterCriteria(
            city = "Tehran"
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by verified only`() {
        // Create test stations with different verification states
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.UNVERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by verified only
        val criteria = StationFilterCriteria(
            verifiedOnly = true
        )
        
        val result = StationFilteringEngine.filterStations(stations, criteria)
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }

    @Test
    fun `test filter by radius`() {
        // Create test stations within and outside a radius
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.7895,  // Farther away
                longitude = 51.4890,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Filter by radius around Tehran coordinates
        val result = StationFilteringEngine.filterStationsByRadius(
            stations, 
            latitude = 35.6895,
            longitude = 51.3890,
            radiusKm = 10.0
        )
        
        // Both stations should be within the bounding box
        assertEquals(2, result.size)
    }

    @Test
    fun `test combined filtering`() {
        // Create test stations with different characteristics
        val station1 = ChargingStation(
            id = "station1",
            externalId = "ext1",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address 1"
            ),
            metadata = StationMetadata(
                name = "Station 1",
                operator = "Operator A",
                network = "Network X",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port1",
                    stationId = "station1",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = 50.0,
                    isAc = false,
                    isDc = true,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src1",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val station2 = ChargingStation(
            id = "station2",
            externalId = "ext2",
            location = StationLocation(
                latitude = 35.6896,
                longitude = 51.3891,
                province = "Tehran",
                city = "Tehran", 
                address = "Test Address 2"
            ),
            metadata = StationMetadata(
                name = "Station 2",
                operator = "Operator B",
                network = "Network Y",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PRIVATE
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port2",
                    stationId = "station2",
                    connectorTypes = setOf(ConnectorType.TYPE_2),
                    acPowerKw = 7.0,
                    dcPowerKw = null,
                    isAc = true,
                    isDc = false,
                    voltageV = null,
                    currentA = null,
                    phaseCount = null,
                    protocol = null
                )
            ),
            provenance = ProvenanceModel(
                source = "TestSource",
                sourceId = "src2",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:01:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.8,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )

        val stations = listOf(station1, station2)
        
        // Combine multiple filters
        val criteria = StationFilterCriteria(
            connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
            minPowerKw = 40.0,
            operator = "Operator A",
            verifiedOnly = true
        )
        
        val result = StationFilteringEngine.filterStationsWithGeographic(
            stations, 
            criteria, 
            latitude = 35.6895,
            longitude = 51.3890,
            radiusKm = 10.0
        )
        
        assertEquals(1, result.size)
        assertEquals("station1", result[0].id)
    }
}
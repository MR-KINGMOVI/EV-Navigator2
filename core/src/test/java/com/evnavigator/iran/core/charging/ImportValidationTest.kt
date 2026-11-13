package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.vehicle.ConnectorType
import org.junit.Test
import kotlin.test.assertEquals

class ImportValidationTest {

    @Test
    fun `test valid record validation`() {
        // Create a valid normalized charging station record
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify successful validation
        assertEquals(true, result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun `test invalid station ID`() {
        // Create a record with invalid station ID
        val record = NormalizedChargingStationRecord(
            id = "",  // Empty ID - should cause validation error
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Station ID is required", result.errors[0])
    }

    @Test
    fun `test invalid latitude`() {
        // Create a record with invalid latitude
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 100.0,  // Invalid latitude - outside range
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Latitude must be between -90 and 90 degrees", result.errors[0])
    }

    @Test
    fun `test invalid longitude`() {
        // Create a record with invalid longitude
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 200.0,  // Invalid longitude - outside range
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Longitude must be between -180 and 180 degrees", result.errors[0])
    }

    @Test
    fun `test missing province warning`() {
        // Create a record with missing province
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "",  // Empty province - should cause warning
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation passed but with warning
        assertEquals(true, result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(1, result.warnings.size)
        assertEquals("Province information is missing", result.warnings[0])
    }

    @Test
    fun `test missing connector types warning`() {
        // Create a record with port having no connector types
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
                    connectorTypes = emptySet(),  // Empty connector types - should cause warning
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation passed but with warning
        assertEquals(true, result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(1, result.warnings.size)
        assertEquals("No connector types specified for port - may affect compatibility", result.warnings[0])
    }

    @Test
    fun `test negative power value error`() {
        // Create a record with negative DC power
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2),
                    acPowerKw = null,
                    dcPowerKw = -10.0,  // Negative power - should cause error
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("DC power cannot be negative", result.errors[0])
    }

    @Test
    fun `test missing source error`() {
        // Create a record with missing source in provenance
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                source = "",  // Empty source - should cause error
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Source is required", result.errors[0])
    }

    @Test
    fun `test invalid confidence range`() {
        // Create a record with invalid confidence value
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 1.5,  // Invalid confidence - outside range
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation failed with correct error
        assertEquals(false, result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Confidence must be between 0.0 and 1.0", result.errors[0])
    }

    @Test
    fun `test valid port with multiple connectors`() {
        // Create a record with port having multiple connector types
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
                    connectorTypes = setOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2),
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify successful validation
        assertEquals(true, result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun `test warning for unknown connector type`() {
        // Create a record with UNKNOWN connector type
        val record = NormalizedChargingStationRecord(
            id = "station-123",
            externalId = "ext-123",
            location = StationLocation(
                latitude = 35.6895,
                longitude = 51.3890,
                province = "Tehran",
                city = "Tehran",
                address = "Test Address"
            ),
            metadata = StationMetadata(
                name = "Test Station",
                operator = "Test Operator",
                network = "Test Network",
                accessPayment = AccessPaymentModel(
                    accessType = AccessPaymentModel.AccessType.PUBLIC
                )
            ),
            ports = listOf(
                ChargingPort(
                    id = "port-1",
                    stationId = "station-123",
                    connectorTypes = setOf(ConnectorType.OTHER),  // UNKNOWN connector type - should cause warning
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
                sourceId = "src-123",
                sourceType = "TestType",
                retrievalTimestamp = "2023-01-01T00:00:00Z",
                datasetVersion = "1.0",
                recordVersion = "1.0",
                confidence = 0.9,
                verificationState = ProvenanceModel.VerificationState.VERIFIED
            )
        )
        
        // Validate the record
        val result = ImportValidation.validateRecord(record)
        
        // Verify validation passed but with warning about UNKNOWN connector
        assertEquals(true, result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(1, result.warnings.size)
        assertEquals("Contains 'OTHER' connector type - should be replaced with specific type", result.warnings[0])
    }
}
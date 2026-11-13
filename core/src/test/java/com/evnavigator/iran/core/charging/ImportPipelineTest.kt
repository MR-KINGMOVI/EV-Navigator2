package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals

class ImportPipelineTest {

    @Test
    fun `test successful import pipeline`() {
        // Create a raw charging station record
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-123",
            sourceType = "OpenChargeMap",
            rawData = mapOf(
                "name" to "Test Station",
                "latitude" to 35.6895,
                "longitude" to 51.3890
            ),
            retrievalTimestamp = 1672531200000L,
            sourceVersion = "1.0"
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify successful import
        assertEquals(true, result.success)
        assertEquals("temp-station-123", result.stationId)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.warnings.size)
        assertEquals(true, result.importedRecord != null)
    }

    @Test
    fun `test import with missing required fields`() {
        // Create a raw charging station record with missing required fields
        val rawRecord = RawChargingStationRecord(
            sourceId = "",  // Empty source ID - should cause validation error
            sourceType = "OpenChargeMap",
            rawData = mapOf(
                "name" to "Test Station",
                "latitude" to 35.6895,
                "longitude" to 51.3890
            ),
            retrievalTimestamp = 1672531200000L
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify failed import with appropriate error
        assertEquals(false, result.success)
        assertEquals(1, result.errors.size)
        assertEquals("Source ID is required", result.errors[0])
    }

    @Test
    fun `test import with empty raw data`() {
        // Create a raw charging station record with empty raw data
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-123",
            sourceType = "OpenChargeMap",
            rawData = emptyMap(),  // Empty raw data
            retrievalTimestamp = 1672531200000L
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify successful import (empty data should still be processed, but with warnings)
        assertEquals(true, result.success)
        assertEquals(0, result.errors.size)
        // Should have a warning about empty raw data
    }

    @Test
    fun `test import with valid data`() {
        // Create a valid raw charging station record
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-456",
            sourceType = "Tesla",
            rawData = mapOf(
                "name" to "Tesla Supercharger",
                "latitude" to 35.7895,
                "longitude" to 51.4890
            ),
            retrievalTimestamp = "2023-01-01T01:00:00Z",
            sourceVersion = "2.0"
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify successful import
        assertEquals(true, result.success)
        assertEquals("temp-station-456", result.stationId)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.warnings.size)
        assertEquals(true, result.importedRecord != null)
    }

    @Test
    fun `test import with null source version`() {
        // Create a raw charging station record with null source version
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-789",
            sourceType = "GenericProvider",
            rawData = mapOf(
                "name" to "Generic Station",
                "latitude" to 35.8895,
                "longitude" to 51.5890
            ),
            retrievalTimestamp = "2023-01-01T02:00:00Z",
            sourceVersion = null  // Null version
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify successful import
        assertEquals(true, result.success)
        assertEquals("temp-station-789", result.stationId)
        assertEquals(0, result.errors.size)
        assertEquals(0, result.warnings.size)
        assertEquals(true, result.importedRecord != null)
    }

    @Test
    fun `test validation with multiple errors`() {
        // Create a raw charging station record with multiple missing fields
        val rawRecord = RawChargingStationRecord(
            sourceId = "",  // Empty source ID
            sourceType = "",  // Empty source type
            rawData = mapOf(),
            retrievalTimestamp = ""  // Empty timestamp
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify failed import with multiple errors
        assertEquals(false, result.success)
        assertEquals(3, result.errors.size)
    }

    @Test
    fun `test normalized record structure`() {
        // Create a raw charging station record
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-101",
            sourceType = "OpenChargeMap",
            rawData = mapOf("name" to "Test Station"),
            retrievalTimestamp = 1672531200000L
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify the structure of the normalized record
        assertEquals(true, result.success)
        val importedRecord = result.importedRecord!!
        
        // Check basic fields
        assertEquals("temp-station-101", importedRecord.id)
        assertEquals("station-101", importedRecord.externalId)
        assertEquals(true, importedRecord.location != null)
        assertEquals(true, importedRecord.metadata != null)
        assertEquals(true, importedRecord.ports.isNotEmpty())
        assertEquals(true, importedRecord.provenance != null)
        
        // Check provenance fields
        assertEquals("OpenChargeMap", importedRecord.provenance.source)
        assertEquals("station-101", importedRecord.provenance.sourceId)
        assertEquals("2023-01-01T00:00:00Z", importedRecord.provenance.retrievalTimestamp)
        assertEquals("1.0", importedRecord.provenance.datasetVersion)
    }

    @Test
    fun `test provenance processing`() {
        // Create a raw charging station record
        val rawRecord = RawChargingStationRecord(
            sourceId = "station-202",
            sourceType = "TestProvider",
            rawData = mapOf("name" to "Test Station"),
            retrievalTimestamp = 1672531200000L,
            sourceVersion = "3.0"
        )
        
        // Process through the import pipeline
        val result = ImportPipeline.processImport(rawRecord)
        
        // Verify provenance processing
        assertEquals(true, result.success)
        val importedRecord = result.importedRecord!!
        
        // Check that provenance was properly processed
        assertEquals("TestProvider", importedRecord.provenance.source)
        assertEquals("station-202", importedRecord.provenance.sourceId)
        assertEquals("TestProvider", importedRecord.provenance.sourceType)
        assertEquals("2023-01-01T00:00:00Z", importedRecord.provenance.retrievalTimestamp)
        assertEquals("3.0", importedRecord.provenance.datasetVersion)
    }
}
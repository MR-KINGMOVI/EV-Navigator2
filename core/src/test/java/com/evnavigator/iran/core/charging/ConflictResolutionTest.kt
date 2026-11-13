package com.evnavigator.iran.core.charging

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConflictResolutionTest {

    @Test
    fun `test resolveFieldConflict with no values`() {
        val result = ConflictResolutionEngine.resolveFieldConflict(
            emptyMap(),
            "test_field"
        )
        
        assertEquals(ResolutionStatus.UNKNOWN, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveFieldConflict with single value`() {
        val result = ConflictResolutionEngine.resolveFieldConflict(
            mapOf("source1" to "value1"),
            "test_field"
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals("value1", result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveFieldConflict with identical values`() {
        val result = ConflictResolutionEngine.resolveFieldConflict(
            mapOf("source1" to "value1", "source2" to "value1"),
            "test_field"
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals("value1", result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveFieldConflict with conflicting values`() {
        val result = ConflictResolutionEngine.resolveFieldConflict(
            mapOf("source1" to "value1", "source2" to "value2"),
            "test_field"
        )
        
        assertEquals(ResolutionStatus.CONFLICT, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertTrue(result.conflictingSources.contains("source1"))
        assertTrue(result.conflictingSources.contains("source2"))
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveFieldConflict with mixed null and values`() {
        val result = ConflictResolutionEngine.resolveFieldConflict(
            mapOf("source1" to "value1", "source2" to null),
            "test_field"
        )
        
        assertEquals(ResolutionStatus.RESOLVED, result.resolutionStatus)
        assertEquals("value1", result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.8, result.confidence, 0.01)
    }

    @Test
    fun `test resolvePowerConflict with no values`() {
        val result = ConflictResolutionEngine.resolvePowerConflict(
            emptyMap()
        )
        
        assertEquals(ResolutionStatus.UNKNOWN, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolvePowerConflict with single value`() {
        val result = ConflictResolutionEngine.resolvePowerConflict(
            mapOf("source1" to 50.0)
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals(50.0, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolvePowerConflict with identical values`() {
        val result = ConflictResolutionEngine.resolvePowerConflict(
            mapOf("source1" to 50.0, "source2" to 50.0)
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals(50.0, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolvePowerConflict with small tolerance`() {
        val result = ConflictResolutionEngine.resolvePowerConflict(
            mapOf("source1" to 50.0, "source2" to 55.0) // 10% difference
        )
        
        assertEquals(ResolutionStatus.RESOLVED, result.resolutionStatus)
        assertEquals(52.5, result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.8, result.confidence, 0.01)
    }

    @Test
    fun `test resolvePowerConflict with large tolerance`() {
        val result = ConflictResolutionEngine.resolvePowerConflict(
            mapOf("source1" to 50.0, "source2" to 70.0) // 40% difference
        )
        
        assertEquals(ResolutionStatus.CONFLICT, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveConnectorConflict with no values`() {
        val result = ConflictResolutionEngine.resolveConnectorConflict(
            emptyMap()
        )
        
        assertEquals(ResolutionStatus.UNKNOWN, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveConnectorConflict with single list`() {
        val connectors = listOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2)
        val result = ConflictResolutionEngine.resolveConnectorConflict(
            mapOf("source1" to connectors)
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals(connectors, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveConnectorConflict with identical lists`() {
        val connectors = listOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2)
        val result = ConflictResolutionEngine.resolveConnectorConflict(
            mapOf("source1" to connectors, "source2" to connectors)
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals(connectors, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveConnectorConflict with common connectors`() {
        val connectors1 = listOf(ConnectorType.CCS_COMBO_2, ConnectorType.TYPE_2)
        val connectors2 = listOf(ConnectorType.CCS_COMBO_2, ConnectorType.CHAdeMO)
        val result = ConflictResolutionEngine.resolveConnectorConflict(
            mapOf("source1" to connectors1, "source2" to connectors2)
        )
        
        assertEquals(ResolutionStatus.RESOLVED, result.resolutionStatus)
        @Suppress("UNCHECKED_CAST")
        val resolvedConnectors = result.resolvedValue as? List<ConnectorType>
        assertTrue(resolvedConnectors?.contains(ConnectorType.CCS_COMBO_2) == true)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.7, result.confidence, 0.01)
    }

    @Test
    fun `test resolveConnectorConflict with no common connectors`() {
        val connectors1 = listOf(ConnectorType.CCS_COMBO_2)
        val connectors2 = listOf(ConnectorType.TYPE_2)
        val result = ConflictResolutionEngine.resolveConnectorConflict(
            mapOf("source1" to connectors1, "source2" to connectors2)
        )
        
        assertEquals(ResolutionStatus.CONFLICT, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveMetadataConflict with no values`() {
        val result = ConflictResolutionEngine.resolveMetadataConflict(
            emptyMap()
        )
        
        assertEquals(ResolutionStatus.UNKNOWN, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `test resolveMetadataConflict with single value`() {
        val result = ConflictResolutionEngine.resolveMetadataConflict(
            mapOf("source1" to "metadata1")
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals("metadata1", result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveMetadataConflict with identical values`() {
        val result = ConflictResolutionEngine.resolveMetadataConflict(
            mapOf("source1" to "metadata1", "source2" to "metadata1")
        )
        
        assertEquals(ResolutionStatus.NO_CONFLICT, result.resolutionStatus)
        assertEquals("metadata1", result.resolvedValue)
        assertTrue(result.conflictingSources.isEmpty())
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun `test resolveMetadataConflict with conflicting values`() {
        val result = ConflictResolutionEngine.resolveMetadataConflict(
            mapOf("source1" to "metadata1", "source2" to "metadata2")
        )
        
        assertEquals(ResolutionStatus.CONFLICT, result.resolutionStatus)
        assertEquals(null, result.resolvedValue)
        assertEquals(2, result.conflictingSources.size)
        assertEquals(0.0, result.confidence)
    }
}
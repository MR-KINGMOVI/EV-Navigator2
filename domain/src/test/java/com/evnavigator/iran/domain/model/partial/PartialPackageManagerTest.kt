package com.evnavigator.iran.domain.model.partial

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for PartialPackageManager.
 */
class PartialPackageManagerTest {

    private lateinit var partialPackageManager: PartialPackageManager
    private lateinit var tempDir: Path

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("partial_package_test_")
        partialPackageManager = PartialPackageManager(partialPackageFile = tempDir.resolve("partial_packages.json"))
        runTest { partialPackageManager.initialize() }
    }

    @After
    fun teardown() {
        runTest {
            partialPackageManager.clearAll()
        }
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `registerPartialPackage returns true for valid partial package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val success = partialPackageManager.registerPartialPackage(partialPackage)
        assertTrue(success)
        assertTrue(partialPackageManager.isPartial("test_dataset"))
    }

    @Test
    fun `registerPartialPackage returns false for complete package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Complete routing graph",
            partialPercentage = 100,
            isComplete = true,
            isOffline = false,
            isStale = false,
            isInvalid = false
        )

        val success = partialPackageManager.registerPartialPackage(partialPackage)
        assertFalse(success)
        assertFalse(partialPackageManager.isPartial("test_dataset"))
    }

    @Test
    fun `getPartialPackage returns correct package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        val retrieved = partialPackageManager.getPartialPackage("test_dataset")
        assertNotNull(retrieved)
        assertEquals("test_dataset", retrieved?.datasetId)
        assertEquals("1.0.0", retrieved?.version)
    }

    @Test
    fun `getPartialPackage returns null for non-existent dataset`() = runTest {
        val retrieved = partialPackageManager.getPartialPackage("nonexistent")
        assertNull(retrieved)
    }

    @Test
    fun `getAllPartialPackages returns all registered packages`() = runTest {
        val partialPackage1 = PartialPackage(
            datasetId = "dataset1",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val partialPackage2 = PartialPackage(
            datasetId = "dataset2",
            version = "1.0.0",
            type = PartialPackageType.ELEVATION,
            coverageType = CoverageType.PARTIAL_ELEVATION,
            missingComponents = emptyList(),
            degradedReason = "Partial elevation",
            partialPercentage = 80,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage1)
        partialPackageManager.registerPartialPackage(partialPackage2)

        val allPackages = partialPackageManager.getAllPartialPackages()
        assertEquals(2, allPackages.size)
        assertTrue(allPackages.any { it.datasetId == "dataset1" })
        assertTrue(allPackages.any { it.datasetId == "dataset2" })
    }

    @Test
    fun `removePartialPackage returns true for existing package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        val success = partialPackageManager.removePartialPackage("test_dataset")
        assertTrue(success)
        assertFalse(partialPackageManager.isPartial("test_dataset"))
    }

    @Test
    fun `removePartialPackage returns false for non-existent package`() = runTest {
        val success = partialPackageManager.removePartialPackage("nonexistent")
        assertFalse(success)
    }

    @Test
    fun `isPartial returns true for registered partial package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)
        assertTrue(partialPackageManager.isPartial("test_dataset"))
    }

    @Test
    fun `isPartial returns false for non-existent dataset`() = runTest {
        assertFalse(partialPackageManager.isPartial("nonexistent"))
    }

    @Test
    fun `getDegradationLevel returns FULL for complete package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Complete routing graph",
            partialPercentage = 100,
            isComplete = true,
            isOffline = false,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals(DegradationLevel.FULL, partialPackageManager.getDegradationLevel("test_dataset", OperationType.ROUTING))
    }

    @Test
    fun `getDegradationLevel returns PARTIAL for partial package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals(DegradationLevel.PARTIAL, partialPackageManager.getDegradationLevel("test_dataset", OperationType.ROUTING))
    }

    @Test
    fun `getDegradationLevel returns UNSUPPORTED for unsupported operation`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph"),
            degradedReason = "Missing routing graph",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals(DegradationLevel.UNSUPPORTED, partialPackageManager.getDegradationLevel("test_dataset", OperationType.ROUTING))
    }

    @Test
    fun `getMissingComponentsForOperation returns correct components`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("road_network", "elevation", "charging_infrastructure"),
            degradedReason = "Partial geographic coverage",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        val missing = partialPackageManager.getMissingComponents("test_dataset", OperationType.TRIP_PLANNING)
        assertTrue(missing.contains("road_network"))
        assertTrue(missing.contains("elevation"))
        assertFalse(missing.contains("charging_infrastructure"))
    }

    @Test
    fun `getDegradedReason returns correct reason for partial package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals("Partial routing graph", partialPackageManager.getDegradedReason("test_dataset"))
    }

    @Test
    fun `getDegradedReason returns null for complete package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Complete routing graph",
            partialPercentage = 100,
            isComplete = true,
            isOffline = false,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertNull(partialPackageManager.getDegradedReason("test_dataset"))
    }

    @Test
    fun `getPartialPercentage returns correct percentage`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals(75, partialPackageManager.getPartialPercentage("test_dataset"))
    }

    @Test
    fun `getPartialPercentage returns 100 for complete package`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Complete routing graph",
            partialPercentage = 100,
            isComplete = true,
            isOffline = false,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertEquals(100, partialPackageManager.getPartialPercentage("test_dataset"))
    }

    @Test
    fun `canSupportOperation returns true for supported operation`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertTrue(partialPackageManager.canSupportOperation("test_dataset", OperationType.ROUTING))
    }

    @Test
    fun `canSupportOperation returns false for unsupported operation`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph"),
            degradedReason = "Missing routing graph",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        assertFalse(partialPackageManager.canSupportOperation("test_dataset", OperationType.ROUTING))
    }

    @Test
    fun `getDatasetsForOperation returns correct datasets`() = runTest {
        val partialPackage1 = PartialPackage(
            datasetId = "dataset1",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val partialPackage2 = PartialPackage(
            datasetId = "dataset2",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph"),
            degradedReason = "Missing routing graph",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage1)
        partialPackageManager.registerPartialPackage(partialPackage2)

        val datasets = partialPackageManager.getDatasetsForOperation(OperationType.ROUTING)
        assertEquals(1, datasets.size)
        assertTrue(datasets.contains("dataset1"))
        assertFalse(datasets.contains("dataset2"))
    }

    @Test
    fun `getDatasetsForOperationUnsupported returns correct datasets`() = runTest {
        val partialPackage1 = PartialPackage(
            datasetId = "dataset1",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val partialPackage2 = PartialPackage(
            datasetId = "dataset2",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph"),
            degradedReason = "Missing routing graph",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage1)
        partialPackageManager.registerPartialPackage(partialPackage2)

        val datasets = partialPackageManager.getDatasetsForOperationUnsupported(OperationType.ROUTING)
        assertEquals(1, datasets.size)
        assertTrue(datasets.contains("dataset2"))
        assertFalse(datasets.contains("dataset1"))
    }

    @Test
    fun `getPartialPackageStatistics returns correct statistics`() = runTest {
        val partialPackage1 = PartialPackage(
            datasetId = "dataset1",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val partialPackage2 = PartialPackage(
            datasetId = "dataset2",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph"),
            degradedReason = "Missing routing graph",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage1)
        partialPackageManager.registerPartialPackage(partialPackage2)

        val stats = partialPackageManager.getPartialPackageStatistics()

        assertEquals(2, stats.totalPartialPackages)
        assertEquals(2, stats.partialCoveragePackages)
        assertEquals(1, stats.unsupportedOperations)
    }

    @Test
    fun `partial package state is persisted to disk`() = runTest {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)

        // Verify state is persisted
        val stateFile = partialPackageManager.partialPackageFile.toFile()
        assertTrue(stateFile.exists())

        // Create new manager and verify state is loaded
        val newManager = PartialPackageManager(partialPackageFile = tempDir.resolve("partial_packages.json"))
        newManager.initialize()

        val retrieved = newManager.getPartialPackage("test_dataset")
        assertNotNull(retrieved)
        assertEquals("test_dataset", retrieved?.datasetId)
    }

    @Test
    fun `clearAll clears all partial packages`() = runTest {
        val partialPackage1 = PartialPackage(
            datasetId = "dataset1",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        val partialPackage2 = PartialPackage(
            datasetId = "dataset2",
            version = "1.0.0",
            type = PartialPackageType.ELEVATION,
            coverageType = CoverageType.PARTIAL_ELEVATION,
            missingComponents = emptyList(),
            degradedReason = "Partial elevation",
            partialPercentage = 80,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage1)
        partialPackageManager.registerPartialPackage(partialPackage2)

        partialPackageManager.clearAll()

        assertEquals(0, partialPackageManager.getPartialPackageCount())
        assertFalse(partialPackageManager.isPartial("dataset1"))
        assertFalse(partialPackageManager.isPartial("dataset2"))
    }

    @Test
    fun `getPartialPackageCount returns correct count`() = runTest {
        assertEquals(0, partialPackageManager.getPartialPackageCount())

        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        partialPackageManager.registerPartialPackage(partialPackage)
        assertEquals(1, partialPackageManager.getPartialPackageCount())
    }
}

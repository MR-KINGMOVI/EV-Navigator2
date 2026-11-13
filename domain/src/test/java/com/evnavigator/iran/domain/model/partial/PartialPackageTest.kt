package com.evnavigator.iran.domain.model.partial

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PartialPackage sealed class.
 */
class PartialPackageTest {

    @Test
    fun `PartialPackage is never complete`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("road_network"),
            degradedReason = "Partial geographic coverage",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertFalse(partialPackage.isComplete)
    }

    @Test
    fun `canSupportRouting returns true for partial routing graph`() {
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

        assertTrue(partialPackage.canSupportOperation(OperationType.ROUTING))
    }

    @Test
    fun `canSupportRouting returns false for missing routing graph`() {
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

        assertFalse(partialPackage.canSupportOperation(OperationType.ROUTING))
    }

    @Test
    fun `canSupportElevation returns true for partial elevation`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
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

        assertTrue(partialPackage.canSupportOperation(OperationType.ELEVATION))
    }

    @Test
    fun `canSupportCharging returns true for partial charging`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.CHARGING_INFRASTRUCTURE,
            coverageType = CoverageType.PARTIAL_CHARGING,
            missingComponents = emptyList(),
            degradedReason = "Partial charging infrastructure",
            partialPercentage = 70,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.CHARGING))
    }

    @Test
    fun `canSupportVehicle returns true for partial vehicle database`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.VEHICLE_DATABASE,
            coverageType = CoverageType.PARTIAL_VEHICLE,
            missingComponents = emptyList(),
            degradedReason = "Partial vehicle database",
            partialPercentage = 90,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.VEHICLE))
    }

    @Test
    fun `canSupportWeather returns true for partial weather cache`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.WEATHER_CACHE,
            coverageType = CoverageType.PARTIAL_WEATHER,
            missingComponents = emptyList(),
            degradedReason = "Partial weather cache",
            partialPercentage = 85,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.WEATHER))
    }

    @Test
    fun `canSupportTraffic returns true for partial traffic cache`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.TRAFFIC_CACHE,
            coverageType = CoverageType.PARTIAL_TRAFFIC,
            missingComponents = emptyList(),
            degradedReason = "Partial traffic cache",
            partialPercentage = 75,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.TRAFFIC))
    }

    @Test
    fun `canSupportSearch returns true for partial places`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.PLACES,
            coverageType = CoverageType.PARTIAL_PLACES,
            missingComponents = emptyList(),
            degradedReason = "Partial places",
            partialPercentage = 80,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.SEARCH))
    }

    @Test
    fun `canSupportTripPlanning returns true for partial routing graph with elevation`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = listOf("elevation"),
            degradedReason = "Missing elevation data",
            partialPercentage = 90,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.TRIP_PLANNING))
    }

    @Test
    fun `canSupportTripPlanning returns false for missing routing graph`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("routing_graph", "elevation"),
            degradedReason = "Missing routing graph and elevation",
            partialPercentage = 30,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertFalse(partialPackage.canSupportOperation(OperationType.TRIP_PLANNING))
    }

    @Test
    fun `canSupportChargingPlanning returns true for partial charging with road network`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.CHARGING_INFRASTRUCTURE,
            coverageType = CoverageType.PARTIAL_CHARGING,
            missingComponents = listOf("road_network"),
            degradedReason = "Missing road network",
            partialPercentage = 80,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.CHARGING_PLANNING))
    }

    @Test
    fun `canSupportNavigation returns true for partial routing graph with elevation`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = listOf("elevation"),
            degradedReason = "Missing elevation data",
            partialPercentage = 90,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertTrue(partialPackage.canSupportOperation(OperationType.NAVIGATION))
    }

    @Test
    fun `getDegradationLevel returns PARTIAL for partial packages`() {
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

        assertEquals(DegradationLevel.PARTIAL, partialPackage.getDegradationLevel(OperationType.ROUTING))
    }

    @Test
    fun `getDegradationLevel returns DEGRADED for partial packages with some missing components`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.MAP_DATA,
            coverageType = CoverageType.PARTIAL_GEOGRAPHIC,
            missingComponents = listOf("road_network"),
            degradedReason = "Partial geographic coverage",
            partialPercentage = 50,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        assertEquals(DegradationLevel.DEGRADED, partialPackage.getDegradationLevel(OperationType.ROUTING))
    }

    @Test
    fun `getDegradationLevel returns UNSUPPORTED when operation cannot be supported`() {
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

        assertEquals(DegradationLevel.UNSUPPORTED, partialPackage.getDegradationLevel(OperationType.ROUTING))
    }

    @Test
    fun `getDegradationLevel returns FULL for complete packages`() {
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

        assertEquals(DegradationLevel.FULL, partialPackage.getDegradationLevel(OperationType.ROUTING))
    }

    @Test
    fun `getMissingComponentsForOperation returns correct missing components`() {
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

        val missing = partialPackage.getMissingComponentsForOperation(OperationType.TRIP_PLANNING)
        assertTrue(missing.contains("road_network"))
        assertTrue(missing.contains("elevation"))
        assertFalse(missing.contains("charging_infrastructure"))
    }

    @Test
    fun `toMap serializes partial package correctly`() {
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

        val map = partialPackage.toMap()

        assertEquals("test_dataset", map["datasetId"])
        assertEquals("1.0.0", map["version"])
        assertEquals("PARTIAL_ROUTING_GRAPH", map["coverageType"])
        assertEquals(75, map["partialPercentage"])
        assertFalse(map["isComplete"] as Boolean)
    }

    @Test
    fun `fromMap deserializes partial package correctly`() {
        val map = mapOf(
            "datasetId" to "test_dataset",
            "version" to "1.0.0",
            "type" to "ROUTING_GRAPH",
            "coverageType" to "PARTIAL_ROUTING_GRAPH",
            "missingComponents" to emptyList<String>(),
            "degradedReason" to "Partial routing graph",
            "partialPercentage" to 75,
            "isComplete" to false,
            "isOffline" to true,
            "isStale" to false,
            "isInvalid" to false
        )

        val partialPackage = PartialPackage.fromMap(map)

        assertEquals("test_dataset", partialPackage.datasetId)
        assertEquals("1.0.0", partialPackage.version)
        assertEquals(PartialPackageType.ROUTING_GRAPH, partialPackage.type)
        assertEquals(CoverageType.PARTIAL_ROUTING_GRAPH, partialPackage.coverageType)
        assertEquals(75, partialPackage.partialPercentage)
        assertFalse(partialPackage.isComplete)
    }

    @Test
    fun `partial package with 100% coverage is still marked as partial`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = emptyList(),
            degradedReason = "Partial routing graph",
            partialPercentage = 100,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = false
        )

        // Even with 100% coverage, it's still a partial package
        assertEquals(100, partialPackage.partialPercentage)
        assertFalse(partialPackage.isComplete)
    }

    @Test
    fun `partial package with 0% coverage is marked as invalid`() {
        val partialPackage = PartialPackage(
            datasetId = "test_dataset",
            version = "1.0.0",
            type = PartialPackageType.ROUTING_GRAPH,
            coverageType = CoverageType.PARTIAL_ROUTING_GRAPH,
            missingComponents = listOf("routing_graph"),
            degradedReason = "No routing graph data",
            partialPercentage = 0,
            isComplete = false,
            isOffline = true,
            isStale = false,
            isInvalid = true
        )

        assertEquals(0, partialPackage.partialPercentage)
        assertTrue(partialPackage.isInvalid)
    }
}

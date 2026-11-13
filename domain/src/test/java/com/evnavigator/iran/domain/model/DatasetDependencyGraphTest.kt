package com.evnavigator.iran.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class DatasetDependencyGraphTest {

    @Test
    fun `test addDataset and getDependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val routingId = DatasetId("routing")
        val mapId = DatasetId("map")
        val elevationId = DatasetId("elevation")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        
        // Verify dependencies are stored correctly
        val deps = graph.getDependencies(routingId)
        assertEquals(1, deps.size)
        assertEquals(mapId, deps[0].datasetId)
        assertEquals(DatasetVersion("1.0.0"), deps[0].requiredVersion)
    }
    
    @Test
    fun `test validateDependencies with satisfied dependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        
        // Validate dependencies - should be satisfied
        assertTrue(graph.validateDependencies(routingId))
    }
    
    @Test
    fun `test validateDependencies with unsatisfied dependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        
        // Create metadata
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add only the routing dataset (no map dependency)
        graph.addDataset(routingId, routingMetadata)
        
        // Validate dependencies - should not be satisfied since map is missing
        assertFalse(graph.validateDependencies(routingId))
    }
    
    @Test
    fun `test hasCircularDependencies with no cycles`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        val elevationId = DatasetId("elevation")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val elevationMetadata = DatasetMetadata(
            id = elevationId,
            type = DatasetType.ELEVATION,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with no circular dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        val routingDependency = DatasetDependency(routingId, DatasetVersion("1.0.0"))
        
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        graph.addDataset(elevationId, elevationMetadata, listOf(routingDependency))
        
        // Should not have circular dependencies
        assertFalse(graph.hasCircularDependencies())
    }
    
    @Test
    fun `test hasCircularDependencies with cycles`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets that create a cycle: A -> B -> A
        val datasetAId = DatasetId("datasetA")
        val datasetBId = DatasetId("datasetB")
        
        // Create metadata
        val datasetAMetadata = DatasetMetadata(
            id = datasetAId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val datasetBMetadata = DatasetMetadata(
            id = datasetBId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Create circular dependency: A depends on B, B depends on A
        val dependencyA = DatasetDependency(datasetBId, DatasetVersion("1.0.0"))
        val dependencyB = DatasetDependency(datasetAId, DatasetVersion("1.0.0"))
        
        graph.addDataset(datasetAId, datasetAMetadata, listOf(dependencyA))
        graph.addDataset(datasetBId, datasetBMetadata, listOf(dependencyB))
        
        // Should detect circular dependencies
        assertTrue(graph.hasCircularDependencies())
    }
    
    @Test
    fun `test canInstallDataset with satisfied dependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        
        // Should be able to install routing dataset
        assertTrue(graph.canInstallDataset(routingId))
    }
    
    @Test
    fun `test canInstallDataset with unsatisfied dependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        
        // Create metadata
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add only routing dataset (no map dependency)
        graph.addDataset(routingId, routingMetadata)
        
        // Should not be able to install routing dataset
        assertFalse(graph.canInstallDataset(routingId))
    }
    
    @Test
    fun `test getDependentDatasets`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        val elevationId = DatasetId("elevation")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val elevationMetadata = DatasetMetadata(
            id = elevationId,
            type = DatasetType.ELEVATION,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        val routingDependency = DatasetDependency(routingId, DatasetVersion("1.0.0"))
        
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        graph.addDataset(elevationId, elevationMetadata, listOf(routingDependency))
        
        // Check dependents of map dataset
        val dependents = graph.getDependentDatasets(mapId)
        assertEquals(1, dependents.size)
        assertTrue(dependents.contains(routingId))
        
        // Check dependents of routing dataset  
        val routingDependents = graph.getDependentDatasets(routingId)
        assertEquals(1, routingDependents.size)
        assertTrue(routingDependents.contains(elevationId))
    }
    
    @Test
    fun `test getMissingDependencies`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        
        // Create metadata
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add only routing dataset (no map dependency)
        graph.addDataset(routingId, routingMetadata)
        
        // Should find missing dependencies
        val missing = graph.getMissingDependencies(routingId)
        assertEquals(1, missing.size)
        assertTrue(missing.contains(mapId))
    }
    
    @Test
    fun `test validateCompleteDependencyChain`() {
        val graph = DatasetDependencyGraph()
        
        // Create test datasets
        val mapId = DatasetId("map")
        val routingId = DatasetId("routing")
        val elevationId = DatasetId("elevation")
        
        // Create metadata
        val mapMetadata = DatasetMetadata(
            id = mapId,
            type = DatasetType.MAP,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val routingMetadata = DatasetMetadata(
            id = routingId,
            type = DatasetType.ROUTING,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        val elevationMetadata = DatasetMetadata(
            id = elevationId,
            type = DatasetType.ELEVATION,
            version = DatasetVersion("1.0.0"),
            schemaVersion = DatasetVersion("1.0.0"),
            packageVersion = DatasetVersion("1.0.0"),
            source = DatasetSource("test-source", "http://example.com"),
            creationTimestamp = Instant.now()
        )
        
        // Add datasets with dependencies
        val mapDependency = DatasetDependency(mapId, DatasetVersion("1.0.0"))
        val routingDependency = DatasetDependency(routingId, DatasetVersion("1.0.0"))
        
        graph.addDataset(mapId, mapMetadata)
        graph.addDataset(routingId, routingMetadata, listOf(mapDependency))
        graph.addDataset(elevationId, elevationMetadata, listOf(routingDependency))
        
        // Should validate complete chain successfully
        val datasetIds = listOf(mapId, routingId, elevationId)
        assertTrue(graph.validateCompleteDependencyChain(datasetIds))
    }
}
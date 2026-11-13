package com.evnavigator.iran.domain.model

import java.util.*

/**
 * Represents a dataset dependency graph that manages relationships between datasets.
 * This class provides functionality for validating dependencies, detecting cycles,
 * and ensuring dataset compatibility.
 */
class DatasetDependencyGraph {
    
    /**
     * Map of dataset IDs to their dependencies
     */
    private val dependencies: MutableMap<DatasetId, List<DatasetDependency>> = mutableMapOf()
    
    /**
     * Map of dataset IDs to their metadata for validation purposes
     */
    private val datasetMetadata: MutableMap<DatasetId, DatasetMetadata> = mutableMapOf()
    
    /**
     * Adds a dataset and its dependencies to the graph.
     *
     * @param datasetId The ID of the dataset being added
     * @param metadata The metadata of the dataset (for validation)
     * @param datasetDependencies The list of dependencies for this dataset
     */
    fun addDataset(
        datasetId: DatasetId,
        metadata: DatasetMetadata,
        datasetDependencies: List<DatasetDependency> = emptyList()
    ) {
        dependencies[datasetId] = datasetDependencies
        datasetMetadata[datasetId] = metadata
    }
    
    /**
     * Validates that all dependencies for a given dataset are satisfied.
     *
     * @param datasetId The ID of the dataset to validate dependencies for
     * @return True if all dependencies are satisfied, false otherwise
     */
    fun validateDependencies(datasetId: DatasetId): Boolean {
        val datasetDependencies = dependencies[datasetId] ?: return true
        
        // Check each dependency
        for (dependency in datasetDependencies) {
            if (!isDependencySatisfied(dependency)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Checks if a specific dependency is satisfied.
     *
     * @param dependency The dependency to check
     * @return True if the dependency is satisfied, false otherwise
     */
    private fun isDependencySatisfied(dependency: DatasetDependency): Boolean {
        // Check if a dataset with this ID exists in our graph
        val hasDataset = datasetMetadata.containsKey(dependency.datasetId)
        
        // If it's not required (optional), it's satisfied regardless of existence
        if (dependency.optional) {
            return true
        }
        
        // For required dependencies, check that the dataset exists and meets version requirements
        if (!hasDataset) {
            return false
        }
        
        val targetMetadata = datasetMetadata[dependency.datasetId]!!
        val targetVersion = targetMetadata.version
        
        // Check if compatible versions are specified and match
        if (dependency.compatibleVersions.isNotEmpty()) {
            return dependency.compatibleVersions.contains(targetVersion)
        }
        
        // Check if required version matches exactly or is compatible (simplified check)
        return dependency.requiredVersion == targetVersion
    }
    
    /**
     * Detects circular dependencies in the graph.
     *
     * @return True if circular dependencies exist, false otherwise
     */
    fun hasCircularDependencies(): Boolean {
        val visited = mutableSetOf<DatasetId>()
        val recursionStack = mutableSetOf<DatasetId>()
        
        for (datasetId in dependencies.keys) {
            if (!visited.contains(datasetId)) {
                if (detectCycle(datasetId, visited, recursionStack)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Helper method for detecting cycles using DFS.
     */
    private fun detectCycle(
        datasetId: DatasetId,
        visited: MutableSet<DatasetId>,
        recursionStack: MutableSet<DatasetId>
    ): Boolean {
        visited.add(datasetId)
        recursionStack.add(datasetId)
        
        val datasetDependencies = dependencies[datasetId] ?: emptyList()
        
        for (dependency in datasetDependencies) {
            val depId = dependency.datasetId
            
            // If we haven't visited this dependency yet, visit it
            if (!visited.contains(depId)) {
                if (detectCycle(depId, visited, recursionStack)) {
                    return true
                }
            } 
            // If this dependency is in the current recursion stack, we found a cycle
            else if (recursionStack.contains(depId)) {
                return true
            }
        }
        
        // Remove from recursion stack
        recursionStack.remove(datasetId)
        return false
    }
    
    /**
     * Gets all dependencies for a dataset.
     *
     * @param datasetId The ID of the dataset
     * @return List of dependencies, or empty list if none exist
     */
    fun getDependencies(datasetId: DatasetId): List<DatasetDependency> {
        return dependencies[datasetId] ?: emptyList()
    }
    
    /**
     * Checks if a dataset exists in the graph.
     *
     * @param datasetId The ID of the dataset to check
     * @return True if the dataset exists, false otherwise
     */
    fun hasDataset(datasetId: DatasetId): Boolean {
        return datasetMetadata.containsKey(datasetId)
    }
    
    /**
     * Gets metadata for a specific dataset.
     *
     * @param datasetId The ID of the dataset
     * @return Metadata if found, null otherwise
     */
    fun getDatasetMetadata(datasetId: DatasetId): DatasetMetadata? {
        return datasetMetadata[datasetId]
    }
    
    /**
     * Removes a dataset and all its dependencies from the graph.
     *
     * @param datasetId The ID of the dataset to remove
     */
    fun removeDataset(datasetId: DatasetId) {
        dependencies.remove(datasetId)
        datasetMetadata.remove(datasetId)
    }
    
    /**
     * Gets all datasets in the graph.
     *
     * @return Set of all dataset IDs
     */
    fun getAllDatasets(): Set<DatasetId> {
        return datasetMetadata.keys.toSet()
    }
    
    /**
     * Validates that a set of datasets forms a valid dependency graph.
     *
     * @param datasetIds The list of dataset IDs to validate
     * @return True if the entire set is valid, false otherwise
     */
    fun validateDatasetSet(datasetIds: List<DatasetId>): Boolean {
        // Check for circular dependencies in the subset
        val tempGraph = DatasetDependencyGraph()
        
        // Add only the specified datasets and their dependencies
        for (datasetId in datasetIds) {
            if (datasetMetadata.containsKey(datasetId)) {
                val metadata = datasetMetadata[datasetId]!!
                val deps = dependencies[datasetId] ?: emptyList()
                tempGraph.addDataset(datasetId, metadata, deps)
            }
        }
        
        return !tempGraph.hasCircularDependencies()
    }
    
    /**
     * Gets the dependency depth for a dataset (how many levels of dependencies it has).
     *
     * @param datasetId The ID of the dataset
     * @return Depth of dependencies (0 if no dependencies)
     */
    fun getDependencyDepth(datasetId: DatasetId): Int {
        val datasetDependencies = dependencies[datasetId] ?: return 0
        
        var maxDepth = 0
        for (dependency in datasetDependencies) {
            val depId = dependency.datasetId
            val depDepth = getDependencyDepth(depId)
            if (depDepth > maxDepth) {
                maxDepth = depDepth
            }
        }
        
        return maxDepth + 1
    }
    
    /**
     * Checks if a dataset can be safely installed given the current dependency graph.
     * This validates that all dependencies are satisfied before installation.
     *
     * @param datasetId The ID of the dataset to check for installation readiness
     * @return True if the dataset can be installed, false otherwise
     */
    fun canInstallDataset(datasetId: DatasetId): Boolean {
        // Check if this dataset exists in the graph
        if (!hasDataset(datasetId)) {
            return false
        }
        
        // Validate dependencies are satisfied
        return validateDependencies(datasetId)
    }
    
    /**
     * Validates a complete dependency chain for installation.
     * Ensures that all required datasets and their dependencies are available and compatible.
     *
     * @param datasetIds The list of dataset IDs to validate as a complete set
     * @return True if the entire dependency chain is valid, false otherwise
     */
    fun validateCompleteDependencyChain(datasetIds: List<DatasetId>): Boolean {
        // First check for circular dependencies
        if (!validateDatasetSet(datasetIds)) {
            return false
        }
        
        // Then check that each dataset can be installed
        for (datasetId in datasetIds) {
            if (!canInstallDataset(datasetId)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Gets all datasets that depend on a specific dataset.
     * This is useful for determining which datasets would be affected by removing or updating a dependency.
     *
     * @param datasetId The ID of the dataset to find dependencies for
     * @return List of dataset IDs that depend on the specified dataset
     */
    fun getDependentDatasets(datasetId: DatasetId): List<DatasetId> {
        val dependents = mutableListOf<DatasetId>()
        
        for ((id, deps) in dependencies) {
            if (deps.any { it.datasetId == datasetId }) {
                dependents.add(id)
            }
        }
        
        return dependents
    }
    
    /**
     * Gets a list of missing dependencies for a dataset.
     * Useful for diagnostic purposes when installation fails.
     *
     * @param datasetId The ID of the dataset to check
     * @return List of missing dependency dataset IDs
     */
    fun getMissingDependencies(datasetId: DatasetId): List<DatasetId> {
        val missing = mutableListOf<DatasetId>()
        val datasetDependencies = dependencies[datasetId] ?: return emptyList()
        
        for (dependency in datasetDependencies) {
            if (!isDependencySatisfied(dependency)) {
                missing.add(dependency.datasetId)
            }
        }
        
        return missing
    }
    
    /**
     * Gets a list of incompatible dependencies for a dataset.
     * Useful for detecting version conflicts or incompatible schemas.
     *
     * @param datasetId The ID of the dataset to check
     * @return List of incompatible dependency dataset IDs
     */
    fun getIncompatibleDependencies(datasetId: DatasetId): List<DatasetId> {
        val incompatible = mutableListOf<DatasetId>()
        val datasetDependencies = dependencies[datasetId] ?: return emptyList()
        
        for (dependency in datasetDependencies) {
            // Check if the dependency exists but has incompatible version
            if (datasetMetadata.containsKey(dependency.datasetId)) {
                val targetMetadata = datasetMetadata[dependency.datasetId]!!
                val targetVersion = targetMetadata.version
                
                // Check version compatibility
                val compatible = if (dependency.compatibleVersions.isNotEmpty()) {
                    dependency.compatibleVersions.contains(targetVersion)
                } else {
                    dependency.requiredVersion == targetVersion
                }
                
                if (!compatible) {
                    incompatible.add(dependency.datasetId)
                }
            }
        }
        
        return incompatible
    }
}
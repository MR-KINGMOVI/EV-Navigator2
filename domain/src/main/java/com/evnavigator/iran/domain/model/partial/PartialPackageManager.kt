package com.evnavigator.iran.domain.model.partial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Partial package manager for managing partial dataset packages.
 * 
 * This manager handles partial packages with explicit degradation semantics.
 * Partial packages are never treated as complete and must be explicitly
 * handled by consumers with degradation awareness.
 * 
 * @property partialPackages List of partial packages
 * @property partialPackageStateFlow State flow for partial package state changes
 * @property partialPackageFile Path to partial package state file
 * @property json JSON serializer
 */
class PartialPackageManager(
    private val partialPackages: MutableMap<String, PartialPackage> = mutableMapOf(),
    private val partialPackageStateFlow: MutableStateFlow<Map<String, PartialPackage>> = MutableStateFlow(emptyMap()),
    internal val partialPackageFile: Path = Paths.get("data", "partial_packages.json"),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Current partial packages.
     */
    val partialPackagesFlow: StateFlow<Map<String, PartialPackage>> = partialPackageStateFlow.asStateFlow()

    /**
     * Initializes partial package manager and loads previous state.
     */
    suspend fun initialize() {
        loadPartialPackages()
    }

    /**
     * Registers a partial package.
     * 
     * @param partialPackage The partial package to register
     * @return True if registration was successful
     */
    suspend fun registerPartialPackage(partialPackage: PartialPackage): Boolean {
        if (partialPackage.isComplete) {
            return false
        }

        partialPackages[partialPackage.datasetId] = partialPackage
        partialPackageStateFlow.value = partialPackages.toMap()
        savePartialPackages()

        return true
    }

    /**
     * Gets a partial package by dataset ID.
     * 
     * @param datasetId Dataset identifier
     * @return Partial package, or null if not found
     */
    fun getPartialPackage(datasetId: String): PartialPackage? {
        return partialPackages[datasetId]
    }

    /**
     * Gets all partial packages.
     * 
     * @return List of all partial packages
     */
    fun getAllPartialPackages(): List<PartialPackage> {
        return partialPackages.values.toList()
    }

    /**
     * Removes a partial package.
     * 
     * @param datasetId Dataset identifier
     * @return True if removal was successful
     */
    suspend fun removePartialPackage(datasetId: String): Boolean {
        if (!partialPackages.containsKey(datasetId)) {
            return false
        }

        partialPackages.remove(datasetId)
        partialPackageStateFlow.value = partialPackages.toMap()
        savePartialPackages()

        return true
    }

    /**
     * Checks if a dataset is partial.
     * 
     * @param datasetId Dataset identifier
     * @return True if the dataset is partial
     */
    fun isPartial(datasetId: String): Boolean {
        return partialPackages.containsKey(datasetId)
    }

    /**
     * Gets the degradation level for a dataset.
     * 
     * @param datasetId Dataset identifier
     * @param operationType Type of operation
     * @return Degradation level
     */
    fun getDegradationLevel(datasetId: String, operationType: OperationType): DegradationLevel {
        val partialPackage = partialPackages[datasetId]
        return if (partialPackage != null) {
            partialPackage.getDegradationLevel(operationType)
        } else {
            DegradationLevel.FULL
        }
    }

    /**
     * Gets the missing components for a dataset.
     * 
     * @param datasetId Dataset identifier
     * @param operationType Type of operation
     * @return List of missing components
     */
    fun getMissingComponents(datasetId: String, operationType: OperationType): List<String> {
        val partialPackage = partialPackages[datasetId]
        return if (partialPackage != null) {
            partialPackage.getMissingComponentsForOperation(operationType)
        } else {
            emptyList()
        }
    }

    /**
     * Gets the degraded reason for a dataset.
     * 
     * @param datasetId Dataset identifier
     * @return Degraded reason, or null if not degraded
     */
    fun getDegradedReason(datasetId: String): String? {
        val partialPackage = partialPackages[datasetId]
        return if (partialPackage != null && partialPackage.partialPercentage < 100) {
            partialPackage.degradedReason
        } else {
            null
        }
    }

    /**
     * Gets the partial percentage for a dataset.
     * 
     * @param datasetId Dataset identifier
     * @return Partial percentage (0-100), or 100 if not partial
     */
    fun getPartialPercentage(datasetId: String): Int {
        val partialPackage = partialPackages[datasetId]
        return if (partialPackage != null) {
            partialPackage.partialPercentage
        } else {
            100
        }
    }

    /**
     * Checks if a dataset can support an operation.
     * 
     * @param datasetId Dataset identifier
     * @param operationType Type of operation
     * @return True if the operation can be supported
     */
    fun canSupportOperation(datasetId: String, operationType: OperationType): Boolean {
        val partialPackage = partialPackages[datasetId]
        return if (partialPackage != null) {
            partialPackage.canSupportOperation(operationType)
        } else {
            true
        }
    }

    /**
     * Gets all datasets that can support an operation.
     * 
     * @param operationType Type of operation
     * @return List of dataset IDs that can support the operation
     */
    fun getDatasetsForOperation(operationType: OperationType): List<String> {
        return partialPackages.filter { (_, partialPackage) ->
            partialPackage.canSupportOperation(operationType)
        }.keys.toList()
    }

    /**
     * Gets all datasets that cannot support an operation.
     * 
     * @param operationType Type of operation
     * @return List of dataset IDs that cannot support the operation
     */
    fun getDatasetsForOperationUnsupported(operationType: OperationType): List<String> {
        return partialPackages.filter { (_, partialPackage) ->
            !partialPackage.canSupportOperation(operationType)
        }.keys.toList()
    }

    /**
     * Gets statistics about partial packages.
     */
    fun getPartialPackageStatistics(): PartialPackageStatistics {
        val total = partialPackages.size
        val partialCoverage = partialPackages.values.count { it.partialPercentage < 100 }
        val unsupportedOperations = partialPackages.values.sumOf { pkg ->
            OperationType.values().count { op ->
                pkg.getMissingComponentsForOperation(op).isNotEmpty() && !pkg.canSupportOperation(op)
            }
        }

        return PartialPackageStatistics(
            totalPartialPackages = total,
            partialCoveragePackages = partialCoverage,
            unsupportedOperations = unsupportedOperations
        )
    }

    /**
     * Loads partial packages from disk.
     */
    private fun loadPartialPackages() {
        try {
            if (Files.exists(partialPackageFile)) {
                val content = String(Files.readAllBytes(partialPackageFile), Charsets.UTF_8)
                val rawMap = json.decodeFromString<Map<String, Map<String, String>>>(content)
                val packages = rawMap.mapValues { (_, map) ->
                    PartialPackage(
                        datasetId = map["datasetId"] ?: "",
                        version = map["version"] ?: "",
                        type = PartialPackageType.valueOf(map["type"] ?: "MAP_DATA"),
                        coverageType = CoverageType.valueOf(map["coverageType"] ?: "UNKNOWN"),
                        missingComponents = map["missingComponents"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                        degradedReason = map["degradedReason"] ?: "",
                        partialPercentage = map["partialPercentage"]?.toIntOrNull() ?: 0,
                        isComplete = map["isComplete"]?.toBoolean() ?: false,
                        isOffline = map["isOffline"]?.toBoolean() ?: true,
                        isStale = map["isStale"]?.toBoolean() ?: false,
                        isInvalid = map["isInvalid"]?.toBoolean() ?: false
                    )
                }
                partialPackages.clear()
                partialPackages.putAll(packages)
                partialPackageStateFlow.value = partialPackages.toMap()
            }
        } catch (e: Exception) {
            // Failed to load partial packages, start with empty state
            partialPackages.clear()
            partialPackageStateFlow.value = emptyMap()
        }
    }

    /**
     * Saves partial packages to disk.
     */
    private suspend fun savePartialPackages() {
        try {
            val maps = partialPackages.mapValues { (_, pkg) ->
                mapOf(
                    "datasetId" to pkg.datasetId,
                    "version" to pkg.version,
                    "type" to pkg.type.name,
                    "coverageType" to pkg.coverageType.name,
                    "missingComponents" to pkg.missingComponents.joinToString(","),
                    "degradedReason" to pkg.degradedReason,
                    "partialPercentage" to pkg.partialPercentage.toString(),
                    "isComplete" to pkg.isComplete.toString(),
                    "isOffline" to pkg.isOffline.toString(),
                    "isStale" to pkg.isStale.toString(),
                    "isInvalid" to pkg.isInvalid.toString()
                )
            }
            val content = json.encodeToString(maps)
            partialPackageFile.parent?.let { Files.createDirectories(it) }
            Files.write(partialPackageFile, content.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // Failed to save partial packages, this is not critical
        }
    }

    /**
     * Clears all partial packages (for testing or manual reset).
     */
    suspend fun clearAll() {
        partialPackages.clear()
        partialPackageStateFlow.value = emptyMap()
        savePartialPackages()
    }

    /**
     * Gets the number of partial packages.
     */
    fun getPartialPackageCount(): Int {
        return partialPackages.size
    }
}

/**
 * Partial package statistics.
 */
data class PartialPackageStatistics(
    val totalPartialPackages: Int,
    val partialCoveragePackages: Int,
    val unsupportedOperations: Int
)

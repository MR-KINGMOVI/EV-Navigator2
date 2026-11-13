package com.evnavigator.iran.core.vehicle

import java.util.*

/**
 * Versioning system for vehicle specifications.
 */
data class VehicleVersion(
    val schemaVersion: String,        // Database schema version
    val dataVersion: String,           // Data source version  
    val vehicleRecordVersion: String,  // Individual vehicle record version
    val lastUpdated: Long = System.currentTimeMillis()
) {
    init {
        require(schemaVersion.isNotBlank()) { "Schema version must not be blank" }
        require(dataVersion.isNotBlank()) { "Data version must not be blank" }
        require(vehicleRecordVersion.isNotBlank()) { "Vehicle record version must not be blank" }
    }
    
    /**
     * Creates a new version with incremented vehicle record version
     */
    fun incrementVehicleRecordVersion(): VehicleVersion {
        return copy(
            vehicleRecordVersion = incrementVersion(vehicleRecordVersion)
        )
    }
    
    /**
     * Creates a new version with incremented data version
     */
    fun incrementDataVersion(): VehicleVersion {
        return copy(
            dataVersion = incrementVersion(dataVersion)
        )
    }
    
    /**
     * Creates a new version with incremented schema version  
     */
    fun incrementSchemaVersion(): VehicleVersion {
        return copy(
            schemaVersion = incrementVersion(schemaVersion)
        )
    }
    
    private fun incrementVersion(version: String): String {
        // Simple version increment logic - in practice this would be more sophisticated
        return try {
            val parts = version.split(".").map { it.toInt() }
            val newParts = parts.toMutableList()
            
            // Increment the last part
            if (newParts.isNotEmpty()) {
                newParts[newParts.size - 1] = newParts[newParts.size - 1] + 1
            } else {
                "1"
            }
            
            newParts.joinToString(".")
        } catch (e: NumberFormatException) {
            // If version isn't numeric, just append .1
            "$version.1"
        }
    }
    
    /**
     * Checks if this version is compatible with another version
     */
    fun isCompatibleWith(other: VehicleVersion): Boolean {
        // For simplicity, we'll consider versions compatible if they have the same schema version
        return schemaVersion == other.schemaVersion
    }
}

/**
 * Version control for vehicle data operations
 */
object VersionControl {
    
    /**
     * Gets the current database schema version
     */
    fun getCurrentSchemaVersion(): String = "1.0"
    
    /**
     * Gets the current data source version
     */
    fun getCurrentDataSourceVersion(): String = "1.0"
    
    /**
     * Gets the current vehicle record version
     */
    fun getCurrentVehicleRecordVersion(): String = "1.0"
    
    /**
     * Creates a new version for vehicle records
     */
    fun createNewVehicleVersion(): VehicleVersion {
        return VehicleVersion(
            schemaVersion = getCurrentSchemaVersion(),
            dataVersion = getCurrentDataSourceVersion(),
            vehicleRecordVersion = getCurrentVehicleRecordVersion()
        )
    }
    
    private val vehicleRecordVersionMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Associates or updates the version string for a specific vehicle record.
     */
    fun setVehicleRecordVersion(vehicleId: VehicleId, version: String) {
        require(version.isNotBlank()) { "Version must not be blank" }
        vehicleRecordVersionMap[vehicleId.id] = version
    }

    /**
     * Validates that the database schema is compatible with the expected version.
     * Major version must match, minor version must be supported.
     */
    fun validateDatabaseVersion(expectedVersion: String): Boolean {
        if (expectedVersion.isBlank()) return false
        val current = getCurrentSchemaVersion()
        if (expectedVersion == current) return true

        val expectedParts = expectedVersion.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        if (expectedParts.isEmpty() || currentParts.isEmpty()) return false

        // Major version compatibility check
        return expectedParts[0] == currentParts[0] && expectedParts.getOrElse(1) { 0 } <= currentParts.getOrElse(1) { 0 }
    }
    
    /**
     * Gets the version information for a specific vehicle record.
     */
    fun getVehicleRecordVersion(vehicleId: VehicleId): String {
        return vehicleRecordVersionMap[vehicleId.id] ?: getCurrentVehicleRecordVersion()
    }
}
package com.evnavigator.iran.domain.model

import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Represents the staging area for offline data packages.
 * This provides isolated storage for packages before they are activated.
 */
data class StagingArea(
    /**
     * Unique identifier for this staging area
     */
    val id: String,
    
    /**
     * Path to the staging directory
     */
    val path: Path,
    
    /**
     * Maximum size allowed for this staging area in bytes
     */
    val maxSizeBytes: Long = 1024 * 1024 * 1024, // Default 1GB
    
    /**
     * Current usage in bytes
     */
    val currentUsageBytes: Long = 0,
    
    /**
     * Timestamp when this staging area was created
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * Timestamp when this staging area was last modified
     */
    val lastModified: Instant = Instant.now()
) {
    private val stagedDatasets = mutableMapOf<String, Dataset>()

    constructor(pathString: String) : this(id = "staging", path = java.nio.file.Paths.get(pathString))

    fun stageDataset(dataset: Dataset): Boolean {
        stagedDatasets[dataset.version] = dataset
        return true
    }

    fun getDataset(version: String): Dataset? {
        return stagedDatasets[version]
    }

    fun activateDataset(version: String): Boolean {
        val dataset = stagedDatasets[version] ?: return false
        val integrity = dataset.integrity ?: return true
        return integrity.isValid
    }

    fun cleanup() {
        stagedDatasets.clear()
    }

    init {
        require(id.isNotBlank()) { "Staging area ID cannot be blank" }
        require(path != null) { "Staging area path cannot be null" }
        require(maxSizeBytes > 0) { "Maximum size must be positive" }
    }
}

/**
 * Represents a staged package in the staging area.
 */
data class StagedPackage(
    /**
     * Unique identifier for this staged package
     */
    val id: String,
    
    /**
     * Reference to the dataset manifest
     */
    val manifest: DatasetManifest,
    
    /**
     * Path to the staged package file
     */
    val packagePath: Path,
    
    /**
     * Size of the package in bytes
     */
    val sizeBytes: Long,
    
    /**
     * Timestamp when this package was staged
     */
    val stagedAt: Instant = Instant.now(),
    
    /**
     * Status of the staging operation
     */
    val status: StagingStatus = StagingStatus.STAGED,
    
    /**
     * Integrity information for this package
     */
    val integrity: PackageIntegrity? = null,
    
    /**
     * Whether this is a partial package (download was interrupted)
     */
    val isPartial: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Staged package ID cannot be blank" }
        require(manifest != null) { "Manifest cannot be null" }
        require(packagePath != null) { "Package path cannot be null" }
        require(sizeBytes >= 0) { "Package size must be non-negative" }
    }
}

/**
 * Represents the status of a staging operation.
 */
enum class StagingStatus {
    /**
     * Package is staged and ready for validation
     */
    STAGED,
    
    /**
     * Package is being validated
     */
    VALIDATING,
    
    /**
     * Package validation has failed
     */
    VALIDATION_FAILED,
    
    /**
     * Package is being prepared for installation
     */
    PREPARING,
    
    /**
     * Package preparation has failed
     */
    PREPARATION_FAILED,
    
    /**
     * Package is ready to be installed
     */
    READY_FOR_INSTALL,
    
    /**
     * Package is being installed
     */
    INSTALLING,
    
    /**
     * Package installation has failed
     */
    INSTALLATION_FAILED,
    
    /**
     * Package has been successfully installed
     */
    INSTALLED,
    
    /**
     * Package was deleted or removed from staging
     */
    DELETED
}

/**
 * Represents the result of a staging operation.
 */
sealed class StagingResult {
    /**
     * Success result with staged package information.
     */
    data class Success(val stagedPackage: StagedPackage) : StagingResult()
    
    /**
     * Error result with error message.
     */
    data class Error(val message: String, val exception: Exception? = null) : StagingResult()
}

/**
 * Represents a staging manager for handling package staging operations.
 */
class StagingManager {
    
    private val stagingArea: StagingArea
    private val stagedPackages: MutableMap<String, StagedPackage> = mutableMapOf()
    
    constructor(stagingArea: StagingArea) {
        this.stagingArea = stagingArea
    }
    
    /**
     * Stages a package for installation.
     *
     * @param manifest The dataset manifest for the package
     * @param packageData The raw package data
     * @return The staging result
     */
    fun stagePackage(manifest: DatasetManifest, packageData: ByteArray): StagingResult {
        try {
            // Validate that we have enough space
            val packageSize = packageData.size.toLong()
            if (stagingArea.currentUsageBytes + packageSize > stagingArea.maxSizeBytes) {
                return StagingResult.Error("Insufficient storage in staging area")
            }
            
            // Generate unique ID for this package
            val packageId = UUID.randomUUID().toString()
            
            // Create package file path
            val packagePath = stagingArea.path.resolve("$packageId.dat")
            
            // Write package data to staging area
            packagePath.toFile().writeBytes(packageData)
            
            // Create staged package object
            val stagedPackage = StagedPackage(
                id = packageId,
                manifest = manifest,
                packagePath = packagePath,
                sizeBytes = packageSize,
                integrity = null, // Will be set after validation
                isPartial = false
            )
            
            // Add to staging registry
            stagedPackages[packageId] = stagedPackage
            
            return StagingResult.Success(stagedPackage)
        } catch (e: Exception) {
            return StagingResult.Error("Failed to stage package", e)
        }
    }
    
    /**
     * Gets a staged package by ID.
     *
     * @param packageId The ID of the staged package
     * @return The staged package or null if not found
     */
    fun getStagedPackage(packageId: String): StagedPackage? {
        return stagedPackages[packageId]
    }
    
    /**
     * Removes a staged package.
     *
     * @param packageId The ID of the package to remove
     * @return True if removed, false otherwise
     */
    fun removeStagedPackage(packageId: String): Boolean {
        val packageToRemove = stagedPackages[packageId] ?: return false
        
        // Delete the actual file
        try {
            packageToRemove.packagePath.toFile().delete()
        } catch (e: Exception) {
            // Log error but continue with removal from registry
        }
        
        // Remove from registry
        stagedPackages.remove(packageId)
        return true
    }
    
    /**
     * Lists all staged packages.
     *
     * @return List of all staged packages
     */
    fun listStagedPackages(): List<StagedPackage> {
        return stagedPackages.values.toList()
    }
    
    /**
     * Gets staging area information.
     *
     * @return The staging area information
     */
    fun getStagingArea(): StagingArea {
        return stagingArea
    }
    
    /**
     * Updates the usage statistics of the staging area.
     */
    fun updateUsage() {
        var totalSize = 0L
        for (stagedPackage in stagedPackages.values) {
            if (stagedPackage.packagePath.toFile().exists()) {
                totalSize += stagedPackage.packagePath.toFile().length()
            }
        }
        // In a real implementation, this would update the staging area's currentUsageBytes
    }
}
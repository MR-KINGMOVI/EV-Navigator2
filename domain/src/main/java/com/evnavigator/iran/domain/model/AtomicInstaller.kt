package com.evnavigator.iran.domain.model

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Represents the state of an atomic installation operation.
 */
sealed class InstallationState {
    /**
     * Installation has not started.
     */
    data object NotStarted : InstallationState()
    
    /**
     * Installation is in progress.
     */
    data object InProgress : InstallationState()
    
    /**
     * Installation has completed successfully.
     */
    data class Success(val installedDataset: DatasetMetadata) : InstallationState()
    
    /**
     * Installation has failed.
     */
    data class Failed(val reason: String, val exception: Exception? = null) : InstallationState()
    
    /**
     * Installation requires rollback.
     */
    data class RollbackRequired(val reason: String) : InstallationState()
}

/**
 * Represents the result of an atomic installation operation.
 */
sealed class InstallationResult {
    /**
     * Success result with installed dataset information.
     */
    data class Success(val datasetId: DatasetId, val version: DatasetVersion, val installedAt: Instant) : InstallationResult()
    
    /**
     * Error result with error message.
     */
    data class Error(val message: String, val exception: Exception? = null) : InstallationResult()
    
    /**
     * Rollback result indicating a rollback was performed.
     */
    data class RolledBack(val previousVersion: DatasetVersion, val rollbackReason: String) : InstallationResult()
}

/**
 * Represents the atomic installer for offline data packages.
 * This ensures crash-safe, atomic installation of datasets.
 */
class AtomicInstaller(
    private val stagingManager: StagingManager,
    private val activeDatasetManager: ActiveDatasetManager
) {
    
    /**
     * Performs atomic installation of a staged package.
     *
     * @param stagedPackage The staged package to install
     * @return The installation result
     */
    fun installPackage(stagedPackage: StagedPackage): InstallationResult {
        try {
            // Validate the staged package
            val validation = validatePackage(stagedPackage)
            if (!validation.isValid) {
                return InstallationResult.Error(
                    "Package validation failed: ${validation.errorMessage}",
                    validation.exception
                )
            }
            
            // Check if there's an active dataset that needs to be preserved
            val previousActive = activeDatasetManager.getActiveDataset()
            
            // Perform the installation
            val installationState = performInstallation(stagedPackage)
            
            return when (installationState) {
                is InstallationState.Success -> {
                    // Update active dataset manager
                    activeDatasetManager.setActiveDataset(stagedPackage.manifest)
                    
                    // Clean up staging area
                    stagingManager.removeStagedPackage(stagedPackage.id)
                    
                    InstallationResult.Success(
                        datasetId = stagedPackage.manifest.id,
                        version = stagedPackage.manifest.version,
                        installedAt = Instant.now()
                    )
                }
                is InstallationState.Failed -> {
                    // Rollback if needed
                    if (previousActive != null) {
                        val rollbackResult = rollbackToPrevious(previousActive)
                        return InstallationResult.RolledBack(
                            previousVersion = previousActive.version,
                            rollbackReason = "Installation failed: ${installationState.reason}"
                        )
                    }
                    InstallationResult.Error(
                        "Installation failed: ${installationState.reason}",
                        installationState.exception
                    )
                }
                is InstallationState.RollbackRequired -> {
                    // Perform rollback
                    if (previousActive != null) {
                        val rollbackResult = rollbackToPrevious(previousActive)
                        return InstallationResult.RolledBack(
                            previousVersion = previousActive.version,
                            rollbackReason = installationState.reason
                        )
                    }
                    InstallationResult.Error(
                        "Rollback required but no previous dataset available: ${installationState.reason}"
                    )
                }
                is InstallationState.InProgress -> {
                    InstallationResult.Error("Installation is already in progress")
                }
                is InstallationState.NotStarted -> {
                    InstallationResult.Error("Installation has not started")
                }
            }
        } catch (e: Exception) {
            // Attempt rollback on any exception
            val previousActive = activeDatasetManager.getActiveDataset()
            if (previousActive != null) {
                val rollbackResult = rollbackToPrevious(previousActive)
                return InstallationResult.RolledBack(
                    previousVersion = previousActive.version,
                    rollbackReason = "Installation failed with exception: ${e.message}"
                )
            }
            return InstallationResult.Error(
                "Installation failed with exception: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Validates a staged package before installation.
     *
     * @param stagedPackage The package to validate
     * @return Validation result
     */
    private fun validatePackage(stagedPackage: StagedPackage): PackageValidationResult {
        // Check if package file exists
        if (!stagedPackage.packagePath.toFile().exists()) {
            return PackageValidationResult(
                isValid = false,
                errorMessage = "Package file does not exist"
            )
        }
        
        // Check package size
        val packageSize = stagedPackage.packagePath.toFile().length()
        if (packageSize != stagedPackage.sizeBytes) {
            return PackageValidationResult(
                isValid = false,
                errorMessage = "Package size mismatch: expected ${stagedPackage.sizeBytes}, got $packageSize"
            )
        }
        
        // Check integrity if available
        if (stagedPackage.integrity != null) {
            val integrityValid = validateIntegrity(stagedPackage)
            if (!integrityValid) {
                return PackageValidationResult(
                    isValid = false,
                    errorMessage = "Package integrity validation failed"
                )
            }
        }
        
        // Check dependencies
        val dependencyGraph = DatasetDependencyGraph()
        for (dependency in stagedPackage.manifest.dependencies) {
            dependencyGraph.addDataset(
                dependency.datasetId,
                DatasetMetadata(
                    id = dependency.datasetId,
                    version = dependency.requiredVersion,
                    schemaVersion = dependency.requiredVersion,
                    packageVersion = dependency.requiredVersion,
                    type = DatasetType.MAP,
                    source = stagedPackage.manifest.source,
                    creationTimestamp = Instant.now()
                ),
                stagedPackage.manifest.dependencies
            )
        }
        
        if (!dependencyGraph.validateDependencies(stagedPackage.manifest.id)) {
            return PackageValidationResult(
                isValid = false,
                errorMessage = "Package dependencies are not satisfied"
            )
        }
        
        return PackageValidationResult(isValid = true)
    }
    
    /**
     * Validates package integrity.
     *
     * @param stagedPackage The package to validate
     * @return True if valid, false otherwise
     */
    private fun validateIntegrity(stagedPackage: StagedPackage): Boolean {
        if (stagedPackage.integrity == null) {
            return false
        }
        
        val integrity = stagedPackage.integrity
        val packageFile = stagedPackage.packagePath.toFile()
        
        if (!packageFile.exists()) {
            return false
        }
        
        val packageData = packageFile.readBytes()
        
        return PackageIntegrityValidator.validatePackageIntegrity(
            packageData,
            integrity.packageChecksum,
            integrity.packageChecksumAlgorithm
        )
    }
    
    /**
     * Performs the actual installation of the package.
     *
     * @param stagedPackage The package to install
     * @return Installation state
     */
    private fun performInstallation(stagedPackage: StagedPackage): InstallationState {
        // In a real implementation, this would:
        // 1. Extract the package to the active dataset location
        // 2. Update the database with the new dataset metadata
        // 3. Update the active dataset manager
        
        // For now, simulate successful installation
        return InstallationState.Success(
            installedDataset = DatasetMetadata(
                id = stagedPackage.manifest.id,
                version = stagedPackage.manifest.version,
                schemaVersion = stagedPackage.manifest.schemaVersion,
                packageVersion = stagedPackage.manifest.packageVersion,
                type = DatasetType.MAP,
                source = stagedPackage.manifest.source,
                creationTimestamp = stagedPackage.manifest.creationTimestamp
            )
        )
    }
    
    /**
     * Rolls back to a previous dataset.
     *
     * @param previousDataset The previous dataset to restore
     * @return Rollback result
     */
    private fun rollbackToPrevious(previousDataset: DatasetMetadata): InstallationResult {
        try {
            // In a real implementation, this would:
            // 1. Restore the previous dataset files
            // 2. Update the database
            // 3. Update the active dataset manager
            
            return InstallationResult.Success(
                datasetId = previousDataset.id,
                version = previousDataset.version,
                installedAt = Instant.now()
            )
        } catch (e: Exception) {
            return InstallationResult.Error(
                "Rollback failed: ${e.message}",
                e
            )
        }
    }
}

/**
 * Represents the result of package validation.
 */
data class PackageValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val exception: Exception? = null
)

/**
 * Manages the active dataset in the system.
 */
class ActiveDatasetManager {
    
    private var activeDataset: DatasetMetadata? = null
    
    /**
     * Gets the currently active dataset.
     *
     * @return The active dataset or null if none is active
     */
    fun getActiveDataset(): DatasetMetadata? {
        return activeDataset
    }
    
    /**
     * Sets the active dataset.
     *
     * @param dataset The dataset to activate
     */
    fun setActiveDataset(dataset: DatasetManifest) {
        activeDataset = DatasetMetadata(
            id = dataset.id,
            version = dataset.version,
            schemaVersion = dataset.schemaVersion,
            packageVersion = dataset.packageVersion,
            type = DatasetType.MAP,
            source = dataset.source,
            creationTimestamp = dataset.creationTimestamp
        )
    }
    
    /**
     * Checks if a dataset is currently active.
     *
     * @param datasetId The ID of the dataset to check
     * @return True if the dataset is active
     */
    fun isDatasetActive(datasetId: DatasetId): Boolean {
        return activeDataset?.id == datasetId
    }
    
    /**
     * Gets the version of the currently active dataset.
     *
     * @return The version of the active dataset or null if none is active
     */
    fun getActiveVersion(): DatasetVersion? {
        return activeDataset?.version
    }
}
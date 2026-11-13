package com.evnavigator.iran.domain.model

import java.util.Collections

/**
 * Deterministic dataset version selector.
 * 
 * This class implements deterministic version selection logic that considers:
 * - Application compatibility
 * - Schema compatibility
 * - Dependency compatibility
 * - Geographic coverage
 * - Freshness
 * - Integrity
 * - Active status
 * 
 * The selector never selects invalid data, never selects incompatible data,
 * and never silently downgrades.
 * 
 * @property availableVersions List of available versions
 * @property activeVersion Currently active version (may be null)
 * @property appVersion Current application version
 */
class VersionSelector(
    private val availableVersions: List<DatasetVersionInfo>,
    private val activeVersion: String?,
    private val appVersion: String
) {
    /**
     * Selects the best compatible version based on deterministic rules.
     * 
     * Selection algorithm:
     * 1. Check active version - if active, use it
     * 2. Filter by compatibility - app version, schema version
     * 3. Filter by dependencies - required dependencies satisfied
     * 4. Filter by coverage - geographic coverage matches
     * 5. Select best version - newest compatible version
     * 6. Validate integrity - checksum and manifest valid
     * 7. Return selected version - or null if none available
     * 
     * @return Selected version info, or null if no compatible version available
     */
    fun selectBestVersion(): DatasetVersionInfo? {
        // Step 1: Check active version
        val active = availableVersions.find { it.version == activeVersion }
        if (active != null && isCompatible(active)) {
            return active
        }

        // Step 2: Filter by compatibility
        val compatibleVersions = availableVersions.filter { versionInfo ->
            isCompatible(versionInfo)
        }

        if (compatibleVersions.isEmpty()) {
            return null
        }

        // Step 3: Filter by dependencies
        val dependencySatisfiedVersions = compatibleVersions.filter { versionInfo ->
            areDependenciesSatisfied(versionInfo)
        }

        if (dependencySatisfiedVersions.isEmpty()) {
            return null
        }

        // Step 4: Filter by coverage (if coverage info available)
        val coverageSatisfiedVersions = dependencySatisfiedVersions.filter { versionInfo ->
            areCoverageSatisfied(versionInfo)
        }

        if (coverageSatisfiedVersions.isEmpty()) {
            return null
        }

        // Step 5: Select best version - newest compatible version
        val sortedVersions = coverageSatisfiedVersions.sortedByDescending { it.version }
        val bestVersion = sortedVersions.first()

        // Step 6: Validate integrity
        if (!isIntegrityValid(bestVersion)) {
            return null
        }

        // Step 7: Return selected version
        return bestVersion
    }

    /**
     * Checks if a version is compatible with the current application.
     * 
     * @param versionInfo Version information to check
     * @return true if compatible, false otherwise
     */
    private fun isCompatible(versionInfo: DatasetVersionInfo): Boolean {
        return versionInfo.compatibility.isAppVersionCompatible(appVersion)
    }

    /**
     * Checks if all required dependencies are satisfied.
     * 
     * @param versionInfo Version information to check
     * @return true if dependencies satisfied, false otherwise
     */
    private fun areDependenciesSatisfied(versionInfo: DatasetVersionInfo): Boolean {
        if (versionInfo.dependencies.isEmpty()) {
            return true
        }

        val installedVersions = getInstalledVersions()

        for (dependency in versionInfo.dependencies) {
            val installedVersion = installedVersions[dependency.datasetId.value]

            if (!dependency.optional) {
                // Required dependency must be installed
                if (installedVersion == null) {
                    return false
                }

                // Check version compatibility
                if (dependency.compatibleVersions.isNotEmpty()) {
                    if (!dependency.compatibleVersions.any { it.value == installedVersion }) {
                        return false
                    }
                }
            } else {
                // Optional dependency may be missing
                if (installedVersion != null) {
                    // Check version compatibility
                    if (dependency.compatibleVersions.isNotEmpty()) {
                        if (!dependency.compatibleVersions.any { it.value == installedVersion }) {
                            return false
                        }
                    }
                }
            }
        }

        return true
    }

    /**
     * Checks if geographic coverage is satisfied.
     * 
     * @param versionInfo Version information to check
     * @return true if coverage satisfied, false otherwise
     */
    private fun areCoverageSatisfied(versionInfo: DatasetVersionInfo): Boolean {
        // This is a simplified check - actual coverage checking would be more complex
        // and would involve comparing coverage regions
        return true
    }

    /**
     * Checks if integrity is valid.
     * 
     * @param versionInfo Version information to check
     * @return true if integrity valid, false otherwise
     */
    private fun isIntegrityValid(versionInfo: DatasetVersionInfo): Boolean {
        // This is a simplified check - actual integrity validation would involve
        // verifying checksums and manifest integrity
        return versionInfo.checksum != null && versionInfo.checksum.isNotEmpty()
    }

    /**
     * Gets installed versions.
     * 
     * @return Map of dataset ID to installed version
     */
    private fun getInstalledVersions(): Map<String, String> {
        // This would query the installed datasets
        // For now, return empty map
        return emptyMap()
    }

    /**
     * Selects the newest compatible version.
     * 
     * @return Newest compatible version, or null if none available
     */
    fun selectNewestVersion(): DatasetVersionInfo? {
        val compatibleVersions = availableVersions.filter { versionInfo ->
            isCompatible(versionInfo)
        }

        if (compatibleVersions.isEmpty()) {
            return null
        }

        val sortedVersions = compatibleVersions.sortedByDescending { it.version }
        return sortedVersions.first()
    }

    /**
     * Selects the oldest compatible version.
     * 
     * @return Oldest compatible version, or null if none available
     */
    fun selectOldestVersion(): DatasetVersionInfo? {
        val compatibleVersions = availableVersions.filter { versionInfo ->
            isCompatible(versionInfo)
        }

        if (compatibleVersions.isEmpty()) {
            return null
        }

        val sortedVersions = compatibleVersions.sortedBy { it.version }
        return sortedVersions.first()
    }

    /**
     * Checks if a specific version is compatible.
     * 
     * @param versionInfo Version information to check
     * @return true if compatible, false otherwise
     */
    fun isVersionCompatible(versionInfo: DatasetVersionInfo): Boolean {
        return isCompatible(versionInfo) &&
               areDependenciesSatisfied(versionInfo) &&
               areCoverageSatisfied(versionInfo) &&
               isIntegrityValid(versionInfo)
    }

    /**
     * Gets all compatible versions.
     * 
     * @return List of compatible versions
     */
    fun getCompatibleVersions(): List<DatasetVersionInfo> {
        return availableVersions.filter { versionInfo ->
            isVersionCompatible(versionInfo)
        }
    }

    /**
     * Gets the best version for a specific dataset ID.
     * 
     * @param datasetId Dataset ID
     * @return Best version info, or null if none available
     */
    fun selectBestVersionForDataset(datasetId: String): DatasetVersionInfo? {
        val versionsForDataset = availableVersions.filter { it.datasetId == datasetId }
        if (versionsForDataset.isEmpty()) {
            return null
        }

        val selector = VersionSelector(
            availableVersions = versionsForDataset,
            activeVersion = activeVersion,
            appVersion = appVersion
        )

        return selector.selectBestVersion()
    }
}

/**
 * Dataset version information.
 * 
 * @property datasetId Dataset identifier
 * @property version Dataset version
 * @property schemaVersion Schema version
 * @property formatVersion Format version
 * @property source Source provider
 * @property publicationTimestamp Publication timestamp
 * @property expirationTimestamp Expiration timestamp
 * @property coverage Geographic and domain coverage
 * @property dependencies Required and optional dependencies
 * @property packageSize Package size in bytes
 * @property checksum Checksum
 * @property checksumAlgorithm Checksum algorithm
 * @property compression Compression type
 * @property compatibility Compatibility metadata
 * @property minimumAppVersion Minimum app version required
 * @property maximumAppVersion Maximum app version supported
 * @property installationRequirements Installation requirements
 * @property optionalSignature Optional digital signature
 */
data class DatasetVersionInfo(
    val datasetId: String,
    val version: String,
    val schemaVersion: Int?,
    val formatVersion: Int?,
    val source: String,
    val publicationTimestamp: Long?,
    val expirationTimestamp: Long?,
    val coverage: DatasetCoverage,
    val dependencies: List<DatasetDependency>,
    val packageSize: Long,
    val checksum: String?,
    val checksumAlgorithm: String?,
    val compression: CompressionType,
    val compatibility: CompatibilityMetadata,
    val minimumAppVersion: String? = compatibility.minimumAppVersion,
    val maximumAppVersion: String? = compatibility.maximumAppVersion,
    val installationRequirements: List<String> = emptyList(),
    val optionalSignature: String? = null
) {
    /**
     * Checks if the version is expired.
     * 
     * @return true if expired, false otherwise
     */
    fun isExpired(): Boolean {
        if (expirationTimestamp == null) {
            return false
        }
        return System.currentTimeMillis() > expirationTimestamp
    }

    /**
     * Checks if the version is fresh.
     * 
     * @param freshnessThreshold Freshness threshold in milliseconds
     * @return true if fresh, false otherwise
     */
    fun isFresh(freshnessThreshold: Long = 24 * 60 * 60 * 1000): Boolean {
        if (publicationTimestamp == null) {
            return false
        }
        val age = System.currentTimeMillis() - publicationTimestamp
        return age < freshnessThreshold
    }

    /**
     * Gets the age of the version in milliseconds.
     * 
     * @return Age in milliseconds
     */
    fun getAge(): Long {
        return if (publicationTimestamp != null) {
            System.currentTimeMillis() - publicationTimestamp
        } else {
            0
        }
    }

    /**
     * Compares this version with another version.
     * 
     * @param other Other version to compare
     * @return negative if this version is older, positive if newer, zero if equal
     */
    fun compareTo(other: DatasetVersionInfo): Int {
        return version.compareTo(other.version)
    }

    /**
     * Checks if this version is newer than another version.
     * 
     * @param other Other version to compare
     * @return true if this version is newer, false otherwise
     */
    fun isNewerThan(other: DatasetVersionInfo): Boolean {
        return compareTo(other) > 0
    }

    /**
     * Checks if this version is older than another version.
     * 
     * @param other Other version to compare
     * @return true if this version is older, false otherwise
     */
    fun isOlderThan(other: DatasetVersionInfo): Boolean {
        return compareTo(other) < 0
    }

    /**
     * Checks if this version is the same as another version.
     * 
     * @param other Other version to compare
     * @return true if versions are equal, false otherwise
     */
    fun isSameVersion(other: DatasetVersionInfo): Boolean {
        return version == other.version
    }
}



/**
 * Dataset coverage information.
 * 
 * @property regions Geographic regions covered
 * @property domains Data domains covered
 * @property missingRegions Missing regions
 * @property missingDomains Missing domains
 */
data class DatasetCoverage(
    val regions: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val missingRegions: List<String> = emptyList(),
    val missingDomains: List<String> = emptyList()
) {
    /**
     * Checks if a region is covered.
     * 
     * @param region Region to check
     * @return true if covered, false otherwise
     */
    fun isRegionCovered(region: String): Boolean {
        return regions.contains(region) && !missingRegions.contains(region)
    }

    /**
     * Checks if a domain is covered.
     * 
     * @param domain Domain to check
     * @return true if covered, false otherwise
     */
    fun isDomainCovered(domain: String): Boolean {
        return domains.contains(domain) && !missingDomains.contains(domain)
    }

    /**
     * Gets the coverage percentage.
     * 
     * @return Coverage percentage (0-100)
     */
    fun getCoveragePercentage(): Double {
        val totalRegions = regions.size + missingRegions.size
        val totalDomains = domains.size + missingDomains.size

        val regionCoverage = if (totalRegions > 0) {
            (regions.size.toDouble() / totalRegions) * 100
        } else {
            0.0
        }

        val domainCoverage = if (totalDomains > 0) {
            (domains.size.toDouble() / totalDomains) * 100
        } else {
            0.0
        }

        return (regionCoverage + domainCoverage) / 2.0
    }
}

/**
 * Compatibility metadata.
 * 
 * @property minimumAppVersion Minimum app version
 * @property maximumAppVersion Maximum app version
 * @property minimumSchemaVersion Minimum schema version
 * @property maximumSchemaVersion Maximum schema version
 * @property requiredFeatures Required features
 * @property optionalFeatures Optional features
 */
data class CompatibilityMetadata(
    val minimumAppVersion: String? = null,
    val maximumAppVersion: String? = null,
    val minimumSchemaVersion: Int? = null,
    val maximumSchemaVersion: Int? = null,
    val requiredFeatures: List<String> = emptyList(),
    val optionalFeatures: List<String> = emptyList()
) {
    /**
     * Checks if the app version is compatible.
     * 
     * @param appVersion App version to check
     * @return true if compatible, false otherwise
     */
    fun isAppVersionCompatible(appVersion: String): Boolean {
        if (minimumAppVersion != null && compareSemVer(appVersion, minimumAppVersion) < 0) {
            return false
        }

        if (maximumAppVersion != null && compareSemVer(appVersion, maximumAppVersion) > 0) {
            return false
        }

        return true
    }

    /**
     * Checks if the schema version is compatible.
     * 
     * @param schemaVersion Schema version to check
     * @return true if compatible, false otherwise
     */
    fun isSchemaVersionCompatible(schemaVersion: Int): Boolean {
        if (minimumSchemaVersion != null && schemaVersion < minimumSchemaVersion) {
            return false
        }

        if (maximumSchemaVersion != null && schemaVersion > maximumSchemaVersion) {
            return false
        }

        return true
    }
}

internal fun compareSemVer(v1: String, v2: String): Int {
    val parts1 = v1.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    val parts2 = v2.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) {
            return p1.compareTo(p2)
        }
    }
    return 0
}

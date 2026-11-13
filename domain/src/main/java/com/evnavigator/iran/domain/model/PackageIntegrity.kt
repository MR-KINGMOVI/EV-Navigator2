package com.evnavigator.iran.domain.model

import java.security.MessageDigest
import java.util.*
import java.time.Instant

/**
 * Represents package integrity information for offline data packages.
 * This class handles cryptographic checksum validation and integrity verification.
 */
data class PackageIntegrity(
    /**
     * The main package checksum
     */
    val packageChecksum: String,
    
    /**
     * Algorithm used for package checksum
     */
    val packageChecksumAlgorithm: String,
    
    /**
     * Checksum of the manifest section
     */
    val manifestChecksum: String? = null,
    
    /**
     * Checksum of the payload section  
     */
    val payloadChecksum: String? = null,
    
    /**
     * Checksum of the metadata section
     */
    val metadataChecksum: String? = null,
    
    /**
     * Timestamp when integrity was last verified
     */
    val verificationTimestamp: Instant? = null,
    
    /**
     * Status of the integrity check
     */
    val integrityStatus: IntegrityStatus = IntegrityStatus.UNKNOWN
) {
    constructor(checksum: String, algorithm: String, isValid: Boolean) : this(
        packageChecksum = checksum,
        packageChecksumAlgorithm = algorithm,
        integrityStatus = if (isValid) IntegrityStatus.VALID else IntegrityStatus.INVALID
    )

    val isValid: Boolean
        get() = integrityStatus == IntegrityStatus.VALID

    init {
        require(packageChecksum.isNotBlank()) { "Package checksum cannot be blank" }
        require(packageChecksumAlgorithm.isNotBlank()) { "Package checksum algorithm cannot be blank" }
    }
}

/**
 * Represents the status of package integrity verification.
 */
enum class IntegrityStatus {
    /**
     * Integrity has not been verified yet
     */
    UNKNOWN,
    
    /**
     * Package integrity is valid and verified
     */
    VALID,
    
    /**
     * Package integrity check failed - data may be corrupted
     */
    INVALID,
    
    /**
     * Package integrity check was skipped or not performed
     */
    SKIPPED,
    
    /**
     * Package integrity verification in progress
     */
    VERIFYING
}

/**
 * Utility class for package integrity validation and checksum calculation.
 */
object PackageIntegrityValidator {
    
    /**
     * Calculates a checksum for the given data using the specified algorithm.
     *
     * @param data The data to calculate checksum for
     * @param algorithm The checksum algorithm to use (e.g., "SHA-256")
     * @return The hexadecimal representation of the checksum
     */
    fun calculateChecksum(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val hash = digest.digest(data)
        return bytesToHex(hash)
    }
    
    /**
     * Validates package integrity by comparing computed and expected checksums.
     *
     * @param data The complete package data
     * @param expectedChecksum The expected checksum value
     * @param algorithm The algorithm used for the expected checksum
     * @return True if the checksum matches, false otherwise
     */
    fun validatePackageIntegrity(data: ByteArray, expectedChecksum: String, algorithm: String): Boolean {
        val computedChecksum = calculateChecksum(data, algorithm)
        return computedChecksum.equals(expectedChecksum, ignoreCase = true)
    }
    
    /**
     * Validates integrity of individual package components.
     *
     * @param manifestData The manifest data
     * @param payloadData The payload data  
     * @param metadataData The metadata data
     * @param expectedManifestChecksum Expected manifest checksum
     * @param expectedPayloadChecksum Expected payload checksum
     * @param expectedMetadataChecksum Expected metadata checksum
     * @param algorithm The checksum algorithm to use
     * @return True if all components are valid, false otherwise
     */
    fun validateComponentIntegrity(
        manifestData: ByteArray,
        payloadData: ByteArray,
        metadataData: ByteArray,
        expectedManifestChecksum: String?,
        expectedPayloadChecksum: String?,
        expectedMetadataChecksum: String?,
        algorithm: String
    ): Boolean {
        var isValid = true
        
        // Validate manifest if checksum is provided
        if (expectedManifestChecksum != null) {
            val computedManifestChecksum = calculateChecksum(manifestData, algorithm)
            if (!computedManifestChecksum.equals(expectedManifestChecksum, ignoreCase = true)) {
                isValid = false
            }
        }
        
        // Validate payload if checksum is provided
        if (expectedPayloadChecksum != null) {
            val computedPayloadChecksum = calculateChecksum(payloadData, algorithm)
            if (!computedPayloadChecksum.equals(expectedPayloadChecksum, ignoreCase = true)) {
                isValid = false
            }
        }
        
        // Validate metadata if checksum is provided
        if (expectedMetadataChecksum != null) {
            val computedMetadataChecksum = calculateChecksum(metadataData, algorithm)
            if (!computedMetadataChecksum.equals(expectedMetadataChecksum, ignoreCase = true)) {
                isValid = false
            }
        }
        
        return isValid
    }
    
    /**
     * Converts a byte array to hexadecimal string.
     *
     * @param bytes The byte array to convert
     * @return Hexadecimal representation of the bytes
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val result = StringBuilder()
        for (b in bytes) {
            result.append(String.format("%02x", b))
        }
        return result.toString()
    }
    
    /**
     * Validates a package against its manifest's checksum information.
     *
     * @param packageData The complete offline package data
     * @param manifest The dataset manifest containing integrity information
     * @return True if package integrity is valid, false otherwise
     */
    fun validatePackageAgainstManifest(packageData: ByteArray, manifest: DatasetManifest): Boolean {
        // Check if manifest has checksum information
        val expectedChecksum = manifest.checksum
        val algorithm = manifest.integrityAlgorithm
        
        if (expectedChecksum == null || algorithm == null) {
            return false
        }
        
        return validatePackageIntegrity(packageData, expectedChecksum, algorithm)
    }
}
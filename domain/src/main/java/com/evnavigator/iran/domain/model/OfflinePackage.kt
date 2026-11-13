package com.evnavigator.iran.domain.model

import java.time.Instant
import java.util.*

/**
 * Represents a complete offline package structure for EV Navigator Iran.
 * This class defines the format and structure of all offline data packages.
 */
data class OfflinePackage(
    /**
     * The manifest describing this package's contents and metadata
     */
    val manifest: DatasetManifest,
    
    /**
     * The actual payload/data contained in this package
     */
    val payload: PackagePayload,
    
    /**
     * Metadata about the package structure itself
     */
    val packageMetadata: PackageMetadata,
    
    /**
     * Optional signature for package authenticity verification
     */
    val signature: PackageSignature? = null
) {
    init {
        require(manifest != null) { "Package manifest cannot be null" }
        require(payload != null) { "Package payload cannot be null" }
        require(packageMetadata != null) { "Package metadata cannot be null" }
    }
}

/**
 * Represents the actual data payload contained within an offline package.
 */
data class PackagePayload(
    /**
     * The raw binary data of the package payload
     */
    val data: ByteArray,
    
    /**
     * Size of the payload in bytes
     */
    val size: Long = data.size.toLong(),
    
    /**
     * Compression method used for the payload (if any)
     */
    val compression: String? = null,
    
    /**
     * Encryption method used for the payload (if any)
     */
    val encryption: String? = null
) {
    init {
        require(data.isNotEmpty()) { "Package payload data cannot be empty" }
    }
}

/**
 * Represents metadata about an offline package structure.
 */
data class PackageMetadata(
    /**
     * Version of the package format
     */
    val packageVersion: DatasetVersion,
    
    /**
     * Format identifier for this package type
     */
    val formatIdentifier: String,
    
    /**
     * Creation timestamp for this package
     */
    val creationTimestamp: Instant,
    
    /**
     * Last modification timestamp
     */
    val lastModified: Instant,
    
    /**
     * Size of the entire package in bytes (including manifest, payload, and metadata)
     */
    val totalSize: Long,
    
    /**
     * Compression method used for the entire package (if any)
     */
    val packageCompression: String? = null,
    
    /**
     * Encryption method used for the entire package (if any)
     */
    val packageEncryption: String? = null,
    
    /**
     * Checksum of the entire package
     */
    val packageChecksum: String? = null,
    
    /**
     * Algorithm used for package checksum
     */
    val packageChecksumAlgorithm: String? = null
) {
    init {
        require(packageVersion != null) { "Package version cannot be null" }
        require(formatIdentifier.isNotBlank()) { "Format identifier cannot be blank" }
        require(totalSize >= 0) { "Total size cannot be negative" }
    }
}

/**
 * Represents a digital signature for package authenticity verification.
 */
data class PackageSignature(
    /**
     * The signature value
     */
    val value: String,
    
    /**
     * Algorithm used to generate the signature
     */
    val algorithm: String,
    
    /**
     * Key identifier used for signing
     */
    val keyId: String? = null,
    
    /**
     * Certificate chain (if applicable)
     */
    val certificateChain: List<String>? = null,
    
    /**
     * Signature timestamp
     */
    val signatureTimestamp: Instant
) {
    init {
        require(value.isNotBlank()) { "Signature value cannot be blank" }
        require(algorithm.isNotBlank()) { "Signature algorithm cannot be blank" }
        require(signatureTimestamp != null) { "Signature timestamp cannot be null" }
    }
}

/**
 * Represents the structure of a package file when serialized to disk.
 */
data class PackageFileStructure(
    /**
     * The manifest as a JSON string
     */
    val manifestJson: String,
    
    /**
     * The payload data
     */
    val payloadData: ByteArray,
    
    /**
     * Metadata about the package structure
     */
    val metadata: PackageMetadata,
    
    /**
     * Optional signature
     */
    val signatureJson: String? = null
)

/**
 * Builder class for creating OfflinePackage instances.
 * This provides a fluent API for constructing packages with proper validation.
 */
class OfflinePackageBuilder {
    private var manifest: DatasetManifest? = null
    private var payload: PackagePayload? = null
    private var packageMetadata: PackageMetadata? = null
    private var signature: PackageSignature? = null
    
    fun setManifest(manifest: DatasetManifest): OfflinePackageBuilder {
        this.manifest = manifest
        return this
    }
    
    fun setPayload(payload: PackagePayload): OfflinePackageBuilder {
        this.payload = payload
        return this
    }
    
    fun setPackageMetadata(metadata: PackageMetadata): OfflinePackageBuilder {
        this.packageMetadata = metadata
        return this
    }
    
    fun setSignature(signature: PackageSignature?): OfflinePackageBuilder {
        this.signature = signature
        return this
    }
    
    fun build(): OfflinePackage {
        return OfflinePackage(
            manifest = requireNotNull(manifest) { "Manifest is required" },
            payload = requireNotNull(payload) { "Payload is required" },
            packageMetadata = requireNotNull(packageMetadata) { "Package metadata is required" },
            signature = signature
        )
    }
}

/**
 * Package format version constants for semantic versioning.
 */
object PackageFormatVersions {
    const val V1_0 = "1.0"
    const val V1_1 = "1.1"
    const val V2_0 = "2.0"
}

/**
 * Supported package formats and compression methods.
 */
object PackageFormats {
    const val STANDARD_FORMAT = "EVN-STD"
    const val COMPRESSED_FORMAT = "EVN-CMP"
    const val ENCRYPTED_FORMAT = "EVN-ENC"
    const val SECURE_FORMAT = "EVN-SEC"
}

/**
 * Supported compression algorithms for packages.
 */
object PackageCompression {
    const val NONE = "none"
    const val GZIP = "gzip"
    const val ZSTD = "zstd"
    const val BZIP2 = "bzip2"
}

/**
 * Supported encryption algorithms for packages.
 */
object PackageEncryption {
    const val NONE = "none"
    const val AES_256_GCM = "AES-256-GCM"
    const val RSA_OAEP = "RSA-OAEP"
}

/**
 * Supported checksum algorithms for package integrity verification.
 */
object PackageChecksumAlgorithms {
    const val SHA_256 = "SHA-256"
    const val SHA_384 = "SHA-384"
    const val SHA_512 = "SHA-512"
    const val BLAKE2B_256 = "BLAKE2B-256"
    const val BLAKE2B_512 = "BLAKE2B-512"
}
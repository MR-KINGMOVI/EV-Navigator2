package com.evnavigator.iran.core.vehicle

import java.util.*

/**
 * Data quality and provenance information for vehicle specifications.
 */
data class DataQualityAndProvenance(
    // Data quality indicators
    val dataQuality: DataQuality,
    
    // Source information
    val source: String,              // Source of the specification (e.g., manufacturer, database)
    val sourceType: SourceType,      // Type of source (official, database, etc.)
    val sourceUrl: String?,          // URL to source documentation if available
    val sourceReference: String?,    // Reference identifier for the source
    
    // Metadata about data retrieval
    val retrievedDate: Long,         // Unix timestamp when data was retrieved
    val sourceVersion: String?,      // Version of the data source (if applicable)
    
    // Confidence information  
    val confidence: Double,          // Confidence level 0.0 to 1.0 (1.0 = highest confidence)
    val confidenceExplanation: String?, // Explanation for the confidence rating
    
    // Verification status
    val verificationStatus: VerificationStatus,
    val verifiedBy: String?,         // Person or system that verified the data
    val verificationDate: Long?,     // When verification was performed
    
    // Data completeness information
    val fieldsWithData: Set<String>,  // Fields that have actual data (not null/unknown)
    val fieldsWithUnknown: Set<String>, // Fields that are explicitly marked as unknown
    val fieldsWithMissing: Set<String>  // Fields that are missing entirely
    
) {
    init {
        require(confidence >= 0.0 && confidence <= 1.0) { "Confidence must be between 0.0 and 1.0" }
        require(source.isNotBlank()) { "Source must not be blank" }
    }
    
    /**
     * Gets a human-readable summary of the data quality
     */
    val qualitySummary: String
        get() = when (dataQuality) {
            DataQuality.HIGH -> "High quality - verified and complete"
            DataQuality.MEDIUM -> "Medium quality - partially verified or estimated" 
            DataQuality.LOW -> "Low quality - incomplete or questionable data"
            DataQuality.UNKNOWN -> "Unknown quality - no data available"
        }
    
    /**
     * Determines if the data is considered trustworthy
     */
    val isTrustworthy: Boolean
        get() = confidence >= 0.7 && verificationStatus == VerificationStatus.VERIFIED
    
    /**
     * Gets a formatted timestamp of when the data was retrieved
     */
    val retrievedDateString: String
        get() {
            val date = Date(retrievedDate)
            return date.toString()
        }
}

/**
 * Represents different types of data sources
 */
enum class SourceType {
    MANUFACTURER,      // Official manufacturer specifications
    OFFICIAL_DATABASE, // Trusted official database (e.g., homologation documents)
    REGULATORY,        // Regulatory or certification documents  
    TRUSTED_TECHNICAL, // Trusted technical database
    SECONDARY_SOURCE,  // Secondary source like automotive publications
    USER_PROVIDED,     // Data provided by users (with caveats)
    UNKNOWN            // Source type not specified
}

/**
 * Represents verification status of vehicle data
 */
enum class VerificationStatus {
    VERIFIED,          // Data has been verified as accurate
    PARTIALLY_VERIFIED, // Some aspects have been verified  
    UNVERIFIED,        // No verification performed yet
    INCOMPLETE,        // Verification was incomplete
    DISPUTED           // Data is disputed or conflicting
}

/**
 * Helper class for building data quality and provenance information
 */
class DataQualityBuilder {
    private var dataQuality: DataQuality = DataQuality.UNKNOWN
    private var source: String = ""
    private var sourceType: SourceType = SourceType.UNKNOWN
    private var sourceUrl: String? = null
    private var sourceReference: String? = null
    private var retrievedDate: Long = System.currentTimeMillis()
    private var sourceVersion: String? = null
    private var confidence: Double = 0.0
    private var confidenceExplanation: String? = null
    private var verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED
    private var verifiedBy: String? = null
    private var verificationDate: Long? = null
    private var fieldsWithData: Set<String> = emptySet()
    private var fieldsWithUnknown: Set<String> = emptySet()
    private var fieldsWithMissing: Set<String> = emptySet()
    
    fun dataQuality(dataQuality: DataQuality) = apply { this.dataQuality = dataQuality }
    fun source(source: String) = apply { this.source = source }
    fun sourceType(sourceType: SourceType) = apply { this.sourceType = sourceType }
    fun sourceUrl(sourceUrl: String?) = apply { this.sourceUrl = sourceUrl }
    fun sourceReference(sourceReference: String?) = apply { this.sourceReference = sourceReference }
    fun retrievedDate(retrievedDate: Long) = apply { this.retrievedDate = retrievedDate }
    fun sourceVersion(sourceVersion: String?) = apply { this.sourceVersion = sourceVersion }
    fun confidence(confidence: Double) = apply { this.confidence = confidence }
    fun confidenceExplanation(confidenceExplanation: String?) = apply { this.confidenceExplanation = confidenceExplanation }
    fun verificationStatus(verificationStatus: VerificationStatus) = apply { this.verificationStatus = verificationStatus }
    fun verifiedBy(verifiedBy: String?) = apply { this.verifiedBy = verifiedBy }
    fun verificationDate(verificationDate: Long?) = apply { this.verificationDate = verificationDate }
    fun fieldsWithData(fieldsWithData: Set<String>) = apply { this.fieldsWithData = fieldsWithData }
    fun fieldsWithUnknown(fieldsWithUnknown: Set<String>) = apply { this.fieldsWithUnknown = fieldsWithUnknown }
    fun fieldsWithMissing(fieldsWithMissing: Set<String>) = apply { this.fieldsWithMissing = fieldsWithMissing }
    
    fun build(): DataQualityAndProvenance {
        return DataQualityAndProvenance(
            dataQuality = dataQuality,
            source = source,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            sourceReference = sourceReference,
            retrievedDate = retrievedDate,
            sourceVersion = sourceVersion,
            confidence = confidence,
            confidenceExplanation = confidenceExplanation,
            verificationStatus = verificationStatus,
            verifiedBy = verifiedBy,
            verificationDate = verificationDate,
            fieldsWithData = fieldsWithData,
            fieldsWithUnknown = fieldsWithUnknown,
            fieldsWithMissing = fieldsWithMissing
        )
    }
}
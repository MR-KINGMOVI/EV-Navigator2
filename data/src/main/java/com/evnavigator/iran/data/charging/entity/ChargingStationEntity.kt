package com.evnavigator.iran.data.charging.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.Index
import com.evnavigator.iran.core.charging.AccessType
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.DayOfWeek
import com.evnavigator.iran.core.charging.SourceType
import com.evnavigator.iran.data.charging.converters.*

/**
 * Room database entity for charging stations.
 *
 * This entity represents a complete charging station record in the local database,
 * including all static infrastructure information that doesn't change over time.
 *
 * Optimized with appropriate indexes for fast queries.
 */
@Entity(
    tableName = "charging_stations",
    indices = [
        Index(value = ["externalId"]),
        Index(value = ["latitude"]),
        Index(value = ["longitude"]),
        Index(value = ["province"]),
        Index(value = ["city"]),
        Index(value = ["operatorName"]),
        Index(value = ["source"]),
        Index(value = ["sourceType"]),
        Index(value = ["validationStatus"]),
        Index(value = ["datasetVersion"]),
        Index(value = ["confidence"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["province", "city"]),
        Index(value = ["source", "sourceType"]),
        Index(value = ["datasetVersion", "confidence"])
    ]
)
@TypeConverters(
    ChargingStationIdConverter::class,
    ConnectorTypeConverter::class,
    AccessTypeConverter::class,
    SourceTypeConverter::class,
    DayOfWeekConverter::class,
    ValidationStatusConverter::class,
    com.evnavigator.iran.data.vehicle.converters.VehicleTypeConverters::class
)
data class ChargingStationEntity(
    /**
     * Primary key for the station (stable unique identifier)
     */
    @PrimaryKey
    val id: String,

    /**
     * External ID from source system (if available)
     */
    val externalId: String? = null,

    /**
     * Canonical name of the station
     */
    val canonicalName: String,

    /**
     * Operator ID from source system
     */
    val operatorId: String? = null,

    /**
     * Operator name
     */
    val operatorName: String? = null,

    /**
     * Network ID if part of a network
     */
    val networkId: String? = null,

    /**
     * Network name if applicable
     */
    val networkName: String? = null,

    /**
     * Access type for the station
     */
    val accessType: AccessType = AccessType.PUBLIC,

    /**
     * Set of access restrictions (if any)
     */
    val accessRestrictions: Set<String>? = null,

    /**
     * Whether the station is open 24/7
     */
    val is247: Boolean = false,

    /**
     * Opening hours (daily) - stored as JSON or separate fields
     */
    val openingHoursJson: String? = null,

    /**
     * Payment methods accepted (if any)
     */
    val paymentMethods: Set<String>? = null,

    /**
     * Amenities available (if any)
     */
    val amenities: Set<String>? = null,

    /**
     * Contact phone number
     */
    val contactPhone: String? = null,

    /**
     * Contact email address
     */
    val contactEmail: String? = null,

    /**
     * Station website
     */
    val website: String? = null,

    /**
     * Additional notes about the station
     */
    val notes: String? = null,

    /**
     * Latitude coordinate (WGS84)
     */
    val latitude: Double,

    /**
     * Longitude coordinate (WGS84)
     */
    val longitude: Double,

    /**
     * Province where the station is located
     */
    val province: String? = null,

    /**
     * City where the station is located
     */
    val city: String? = null,

    /**
     * Locality/neighborhood information
     */
    val locality: String? = null,

    /**
     * Road/highway context
     */
    val roadContext: String? = null,

    /**
     * Address details (where available)
     */
    val address: String? = null,

    /**
     * Source of this station data
     */
    val source: String,

    /**
     * Type of source (NETWORK_PROVIDER, GOVERNMENT, etc.)
     */
    val sourceType: SourceType,

    /**
     * Reference ID from source system
     */
    val sourceReference: String? = null,

    /**
     * Version of the dataset or record
     */
    val version: String? = null,

    /**
     * Timestamp when this data was retrieved or last updated
     */
    val retrievedDate: Long,

    /**
     * Timestamp when this data was originally created or first reported
     */
    val createdDate: Long? = null,

    /**
     * Timestamp when this data was last validated or verified
     */
    val validatedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: String, // Stored as string for Room compatibility

    /**
     * Indicates whether this is a primary or secondary source
     */
    val isPrimarySource: Boolean = false,

    /**
     * Dataset version (if applicable)
     */
    val datasetVersion: String? = null,

    /**
     * Geographic coverage for this station
     */
    val coverage: String? = null,

    /**
     * Type of coverage (POINT, AREA, ROUTE, etc.)
     */
    val coverageType: String? = null,

    /**
     * Source URL / reference link
     */
    val sourceUrl: String? = null,

    /**
     * Verification timestamp
     */
    val verifiedAt: Long? = null,

    /**
     * Level of coverage (FULL, PARTIAL, NONE)
     */
    val coverageLevel: String? = null,

    /**
     * Timestamp when this station was last accessed
     */
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this station is expired based on confidence threshold.
     *
     * @param confidenceThreshold Minimum confidence required (default 0.5)
     * @return true if confidence is below threshold
     */
    
    @androidx.room.Ignore
    constructor(
        id: String,
        externalId: String? = null,
        name: String,
        latitude: Double,
        longitude: Double,
        address: String? = null,
        operator: String? = null,
        network: String? = null,
        accessType: Any? = null,
        source: String = "MAPNA",
        sourceType: Any? = null,
        version: String? = null,
        validationStatus: String = "VALIDATED",
        confidence: Double = 0.5,
        isOperational: Boolean = true,
        isVerified: Boolean = false,
        createdAt: Long = 0L,
        updatedAt: Long = 0L
    ) : this(
        id = id,
        externalId = externalId,
        canonicalName = name,
        latitude = latitude,
        longitude = longitude,
        address = address,
        operatorName = operator,
        networkName = network,
        accessType = when(accessType) {
            is com.evnavigator.iran.core.charging.AccessType -> accessType
            is String -> try { com.evnavigator.iran.core.charging.AccessType.valueOf(accessType) } catch (e: Exception) { com.evnavigator.iran.core.charging.AccessType.PUBLIC }
            else -> com.evnavigator.iran.core.charging.AccessType.PUBLIC
        },
        source = source,
        sourceType = when(sourceType) {
            is com.evnavigator.iran.core.charging.SourceType -> sourceType
            is String -> try { com.evnavigator.iran.core.charging.SourceType.valueOf(sourceType) } catch (e: Exception) { com.evnavigator.iran.core.charging.SourceType.UNKNOWN }
            else -> com.evnavigator.iran.core.charging.SourceType.UNKNOWN
        },
        version = version,
        validationStatus = validationStatus,
        confidence = confidence,
        retrievedDate = createdAt,
        createdDate = createdAt
    )

    
    val name: String get() = canonicalName
    val updatedAt: Long get() = retrievedDate
    val isVerified: Boolean get() = validationStatus == "VERIFIED"

    fun isExpired(confidenceThreshold: Double = 0.5): Boolean {
        return confidence < confidenceThreshold
    }

    /**
     * Check if this station is valid based on validation status.
     *
     * @return true if validation status is VALID
     */
    fun isValid(): Boolean {
        return validationStatus == "VALID" || validationStatus == "VALIDATED" || validationStatus == "VERIFIED"
    }

    /**
     * Check if this station is stale based on age.
     *
     * @param maxAgeMs Maximum age in milliseconds (default 30 days)
     * @return true if station is older than max age
     */
    fun isStale(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000): Boolean {
        val age = System.currentTimeMillis() - retrievedDate
        return age > maxAgeMs
    }

    /**
     * Get the age of this station in milliseconds.
     *
     * @return Age in milliseconds
     */
    fun getAge(): Long {
        return System.currentTimeMillis() - retrievedDate
    }
}

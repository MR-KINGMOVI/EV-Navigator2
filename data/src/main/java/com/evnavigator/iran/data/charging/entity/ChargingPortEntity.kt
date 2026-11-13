package com.evnavigator.iran.data.charging.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.SourceType
import com.evnavigator.iran.data.charging.converters.*

/**
 * Room database entity for charging ports (EVSEs).
 *
 * This entity represents a single charging port at a station, including its
 * physical specifications and electrical capabilities.
 * Optimized with appropriate indexes for fast queries.
 */
@Entity(
    tableName = "charging_ports",
    indices = [
        Index(value = ["stationId"]),
        Index(value = ["connectorType"]),
        Index(value = ["powerKw"]),
        Index(value = ["status"]),
        Index(value = ["datasetVersion"]),
        Index(value = ["schemaVersion"]),
        Index(value = ["stationId", "connectorType"]),
        Index(value = ["stationId", "powerKw"]),
        Index(value = ["stationId", "status"]),
        Index(value = ["connectorType", "powerKw"]),
        Index(value = ["connectorType", "status"]),
        Index(value = ["powerKw", "status"]),
        Index(value = ["datasetVersion", "schemaVersion"]),
        Index(value = ["source"]),
        Index(value = ["sourceReference"]),
        Index(value = ["retrievedDate"]),
        Index(value = ["confidence"]),
        Index(value = ["validationStatus"])
    ]
)
@TypeConverters(
    ConnectorTypeConverter::class,
    SourceTypeConverter::class,
    ValidationStatusConverter::class
)
data class ChargingPortEntity(
    /**
     * Primary key for the charging port (stable unique identifier)
     */
    @PrimaryKey
    val id: String,

    /**
     * Foreign key to the station this port belongs to
     */
    val stationId: String,

    /**
     * Connector type for this port
     */
    val connectorType: ConnectorType,

    /**
     * Number of connectors of this type at this EVSE
     */
    val connectorCount: Int = 1,

    /**
     * Nominal voltage (V)
     */
    val voltage: Double? = null,

    /**
     * Nominal current (A)
     */
    val current: Double? = null,

    /**
     * Nominal power (kW)
     */
    val powerKw: Double? = null,

    /**
     * Whether this is AC charging
     */
    val isAc: Boolean,

    /**
     * Whether this is DC charging
     */
    val isDc: Boolean,

    /**
     * Number of phases (1 or 3 for AC)
     */
    val phaseCount: Int? = null,

    /**
     * Charging protocol if known (CCS, CHAdeMO, etc.)
     */
    val protocol: String? = null,

    /**
     * Current operational status
     */
    val status: String, // Stored as string for Room compatibility
    val pricePerKwh: Double? = null,

    /**
     * Source of this port data
     */
    val source: String? = null,

    /**
     * Reference ID from source system
     */
    val sourceReference: String? = null,

    /**
     * Timestamp when this information was retrieved
     */
    val retrievedDate: Long? = null,

    /**
     * Confidence level in the data quality (0.0 to 1.0)
     */
    val confidence: Double = 0.5,

    /**
     * Validation status of this information
     */
    val validationStatus: String, // Stored as string for Room compatibility

    /**
     * Dataset version (if applicable)
     */
    val datasetVersion: String? = null,

    /**
     * Schema version (if applicable)
     */
    val schemaVersion: String? = null,

    /**
     * Application version that processed this data (if applicable)
     */
    val applicationVersion: String? = null
) {
    /**
     * Check if this port is expired based on confidence threshold.
     *
     * @param confidenceThreshold Minimum confidence required (default 0.5)
     * @return true if confidence is below threshold
     */
    fun isExpired(confidenceThreshold: Double = 0.5): Boolean {
        return confidence < confidenceThreshold
    }

    /**
     * Check if this port is valid based on validation status.
     *
     * @return true if validation status is VALID
     */
    fun isValid(): Boolean {
        return validationStatus == "VALID" || validationStatus == "VALIDATED" || validationStatus == "VERIFIED"
    }

    /**
     * Check if this port is stale based on age.
     *
     * @param maxAgeMs Maximum age in milliseconds (default 30 days)
     * @return true if port is older than max age
     */
    fun isStale(maxAgeMs: Long = 30L * 24 * 60 * 60 * 1000): Boolean {
        val date = retrievedDate ?: return false
        val age = System.currentTimeMillis() - date
        return age > maxAgeMs
    }

    /**
     * Get the age of this port in milliseconds.
     *
     * @return Age in milliseconds
     */
    fun getAge(): Long {
        val date = retrievedDate ?: return 0L
        return System.currentTimeMillis() - date
    }
}

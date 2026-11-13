package com.evnavigator.iran.core.weather

import java.time.Instant
import com.evnavigator.iran.core.weather.model.*

/**
 * Manages data provenance for weather and traffic information.
 *
 * This class tracks where data came from, when it was retrieved, and how long it's valid,
 * enabling proper caching, freshness assessment, and error handling.
 */
class DataProvenance {
    
    /**
     * Creates a provenance record for a weather observation.
     *
     * @param observation The weather observation
     * @param source The data source identifier
     * @param retrievedAt When the data was retrieved
     * @param validFrom When the data becomes valid
     * @param validUntil When the data expires
     * @return WeatherProvenance record
     */
    fun createWeatherProvenance(
        observation: WeatherObservation,
        source: String,
        retrievedAt: Instant,
        validFrom: Instant,
        validUntil: Instant
    ): WeatherProvenance {
        return WeatherProvenance(
            source = source,
            retrievedAt = retrievedAt,
            validFrom = validFrom,
            validUntil = validUntil,
            schemaVersion = "1.0",
            confidence = observation.confidence,
            uncertainty = observation.uncertainty
        )
    }
    
    /**
     * Creates a provenance record for a traffic observation.
     *
     * @param observation The traffic observation
     * @param source The data source identifier
     * @param retrievedAt When the data was retrieved
     * @param validFrom When the data becomes valid
     * @param validUntil When the data expires
     * @return TrafficProvenance record
     */
    fun createTrafficProvenance(
        observation: TrafficObservation,
        source: String,
        retrievedAt: Instant,
        validFrom: Instant,
        validUntil: Instant
    ): TrafficProvenance {
        return TrafficProvenance(
            source = source,
            retrievedAt = retrievedAt,
            validFrom = validFrom,
            validUntil = validUntil,
            schemaVersion = "1.0",
            confidence = observation.confidence,
            uncertainty = observation.uncertainty
        )
    }
    
    /**
     * Creates a provenance record for a forecast.
     *
     * @param forecastPoint The forecast point
     * @param source The data source identifier
     * @param retrievedAt When the data was retrieved
     * @param validFrom When the data becomes valid
     * @param validUntil When the data expires
     * @return ForecastProvenance record
     */
    fun createForecastProvenance(
        forecastPoint: ForecastWeatherPoint,
        source: String,
        retrievedAt: Instant,
        validFrom: Instant,
        validUntil: Instant
    ): ForecastProvenance {
        return ForecastProvenance(
            source = source,
            retrievedAt = retrievedAt,
            validFrom = validFrom,
            validUntil = validUntil,
            schemaVersion = "1.0",
            confidence = forecastPoint.confidence,
            uncertainty = forecastPoint.uncertainty
        )
    }
    
    /**
     * Updates provenance for a data item when it's refreshed.
     *
     * @param originalProvenance The existing provenance record
     * @param newRetrievedAt When the data was newly retrieved
     * @param newValidFrom When the new data becomes valid
     * @param newValidUntil When the new data expires
     * @return Updated provenance record
     */
    fun updateProvenance(
        originalProvenance: DataProvenanceRecord,
        newRetrievedAt: Instant,
        newValidFrom: Instant,
        newValidUntil: Instant
    ): DataProvenanceRecord {
        return when (originalProvenance) {
            is WeatherProvenance -> {
                originalProvenance.copy(
                    retrievedAt = newRetrievedAt,
                    validFrom = newValidFrom,
                    validUntil = newValidUntil
                )
            }
            is TrafficProvenance -> {
                originalProvenance.copy(
                    retrievedAt = newRetrievedAt,
                    validFrom = newValidFrom,
                    validUntil = newValidUntil
                )
            }
            is ForecastProvenance -> {
                originalProvenance.copy(
                    retrievedAt = newRetrievedAt,
                    validFrom = newValidFrom,
                    validUntil = newValidUntil
                )
            }
        }
    }
    
    /**
     * Determines if a data item is still valid based on its provenance.
     *
     * @param provenance The provenance record
     * @param currentInstant The current time for comparison
     * @return True if the data is still valid, false otherwise
     */
    fun isValid(provenance: DataProvenanceRecord, currentInstant: Instant): Boolean {
        return currentInstant >= provenance.validFrom && currentInstant <= provenance.validUntil
    }
    
    /**
     * Gets the freshness state of a data item based on its provenance.
     *
     * @param provenance The provenance record
     * @param currentInstant The current time for comparison
     * @return Freshness state
     */
    fun getFreshnessState(provenance: DataProvenanceRecord, currentInstant: Instant): WeatherFreshness {
        // Check if data is valid
        if (!isValid(provenance, currentInstant)) {
            return WeatherFreshness.EXPIRED
        }
        
        // Calculate time difference from retrieval
        val timeSinceRetrieval = currentInstant.toEpochMilli() - provenance.retrievedAt.toEpochMilli()
        
        // Define freshness thresholds in milliseconds
        val freshThreshold = 30 * 60 * 1000L // 30 minutes
        val staleThreshold = 2 * 60 * 60 * 1000L // 2 hours
        
        return when {
            timeSinceRetrieval <= freshThreshold -> WeatherFreshness.FRESH
            timeSinceRetrieval <= staleThreshold -> WeatherFreshness.STALE_BUT_USABLE
            else -> WeatherFreshness.STALE_BUT_USABLE
        }
    }
}

/**
 * Base interface for all provenance records.
 */
sealed class DataProvenanceRecord {
    /**
     * Source identifier for where the data came from
     */
    abstract val source: String
    
    /**
     * When the data was retrieved or generated
     */
    abstract val retrievedAt: Instant
    
    /**
     * When the data becomes valid (start time)
     */
    abstract val validFrom: Instant
    
    /**
     * When the data expires (end time)
     */
    abstract val validUntil: Instant
    
    /**
     * Schema version of the data format
     */
    abstract val schemaVersion: String
    
    /**
     * Confidence level in the data (0.0 to 1.0)
     */
    abstract val confidence: Double
    
    /**
     * Uncertainty measure for the data (0.0 to 1.0)
     */
    abstract val uncertainty: Double
}

/**
 * Provenance record for weather observations.
 */
data class WeatherProvenance(
    override val source: String,
    override val retrievedAt: Instant,
    override val validFrom: Instant,
    override val validUntil: Instant,
    override val schemaVersion: String,
    override val confidence: Double,
    override val uncertainty: Double
) : DataProvenanceRecord()

/**
 * Provenance record for traffic observations.
 */
data class TrafficProvenance(
    override val source: String,
    override val retrievedAt: Instant,
    override val validFrom: Instant,
    override val validUntil: Instant,
    override val schemaVersion: String,
    override val confidence: Double,
    override val uncertainty: Double
) : DataProvenanceRecord()

/**
 * Provenance record for weather forecasts.
 */
data class ForecastProvenance(
    override val source: String,
    override val retrievedAt: Instant,
    override val validFrom: Instant,
    override val validUntil: Instant,
    override val schemaVersion: String,
    override val confidence: Double,
    override val uncertainty: Double
) : DataProvenanceRecord()
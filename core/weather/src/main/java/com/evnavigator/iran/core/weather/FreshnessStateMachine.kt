package com.evnavigator.iran.core.weather

import com.evnavigator.iran.core.weather.model.*
import java.time.Duration
import java.time.Instant

/**
 * Implements a deterministic freshness state machine for weather and traffic data.
 *
 * This class provides explicit semantic distinctions between different freshness levels
 * and defines clear transition rules for data quality states.
 */
class FreshnessStateMachine {
    
    /**
     * Enum representing the freshness level of environmental data.
     */
    enum class DataFreshnessState {
        /**
         * Data is fresh and valid, suitable for real-time use
         */
        FRESH,
        
        /**
         * Data is stale but still usable for planning purposes
         */
        STALE_BUT_USABLE,
        
        /**
         * Data has expired and should not be used for real-time decisions
         */
        EXPIRED,
        
        /**
         * Data is invalid or corrupted, cannot be used
         */
        INVALID,
        
        /**
         * Freshness state is unknown due to insufficient information
         */
        UNKNOWN
    }
    
    /**
     * Enum representing the validity status of data.
     */
    enum class ValidityStatus {
        /**
         * Data is valid and can be trusted
         */
        VALID,
        
        /**
         * Data has been validated but may be stale
         */
        VALID_STALE,
        
        /**
         * Data has been invalidated due to errors or corruption
         */
        INVALIDATED,
        
        /**
         * Data validity is unknown
         */
        UNKNOWN
    }
    
    /**
     * Represents the complete state of data freshness and validity.
     */
    data class FreshnessContext(
        val freshness: DataFreshnessState,
        val validity: ValidityStatus,
        val timestamp: Instant,
        val source: String,
        val ttl: Duration?,
        val ageSeconds: Long
    )
    
    /**
     * Calculates the freshness state based on timestamp and TTL.
     *
     * @param timestamp The timestamp of the data
     * @param retrievedAt When the data was retrieved
     * @param ttl Time-to-live for the data
     * @return Freshness context with determined state
     */
    fun calculateFreshnessState(
        timestamp: Instant,
        retrievedAt: Instant,
        ttl: Duration?
    ): FreshnessContext {
        val now = Instant.now()
        val ageSeconds = Duration.between(timestamp, now).seconds
        
        // Determine data validity based on retrieval time vs current time
        val validity = when {
            retrievedAt.isAfter(now) -> ValidityStatus.INVALIDATED  // Future timestamp
            retrievedAt.isBefore(now.minus(Duration.ofDays(30))) -> ValidityStatus.INVALIDATED  // Too old
            else -> ValidityStatus.VALID
        }
        
        // Determine freshness based on TTL and age
        val freshness = when {
            validity == ValidityStatus.INVALIDATED -> DataFreshnessState.INVALID
            ttl == null -> DataFreshnessState.UNKNOWN
            ageSeconds < 0 -> DataFreshnessState.UNKNOWN
            else -> {
                // Calculate relative freshness
                val ttlSeconds = ttl.seconds
                
                when {
                    ageSeconds <= ttlSeconds * 0.5 -> DataFreshnessState.FRESH
                    ageSeconds <= ttlSeconds * 0.8 -> DataFreshnessState.STALE_BUT_USABLE
                    ageSeconds <= ttlSeconds * 1.2 -> DataFreshnessState.EXPIRED
                    else -> DataFreshnessState.EXPIRED
                }
            }
        }
        
        return FreshnessContext(
            freshness = freshness,
            validity = validity,
            timestamp = timestamp,
            source = "unknown", // This would be set by the provider
            ttl = ttl,
            ageSeconds = ageSeconds
        )
    }
    
    /**
     * Updates the freshness state based on a new timestamp and TTL.
     *
     * @param currentContext Current freshness context
     * @param newTimestamp New timestamp for the data
     * @param newTtl New TTL for the data
     * @return Updated freshness context
     */
    fun updateFreshnessState(
        currentContext: FreshnessContext,
        newTimestamp: Instant,
        newTtl: Duration?
    ): FreshnessContext {
        return calculateFreshnessState(newTimestamp, currentContext.timestamp, newTtl)
    }
    
    /**
     * Determines if data is considered fresh enough for real-time navigation use.
     *
     * @param context The freshness context to evaluate
     * @param maxFreshAge Maximum acceptable age in seconds for fresh data
     * @return True if data is fresh enough, false otherwise
     */
    fun isFreshEnoughForNavigation(
        context: FreshnessContext,
        maxFreshAge: Long = 60 // Default: 1 minute
    ): Boolean {
        return when (context.freshness) {
            DataFreshnessState.FRESH -> true
            DataFreshnessState.STALE_BUT_USABLE -> context.ageSeconds <= maxFreshAge
            else -> false
        }
    }
    
    /**
     * Determines if data should be considered usable despite being stale.
     *
     * @param context The freshness context to evaluate
     * @return True if data can be used for planning purposes, false otherwise
     */
    fun isUsableForPlanning(
        context: FreshnessContext
    ): Boolean {
        return when (context.freshness) {
            DataFreshnessState.FRESH,
            DataFreshnessState.STALE_BUT_USABLE,
            DataFreshnessState.EXPIRED -> true
            else -> false
        }
    }
    
    /**
     * Determines if data is valid and can be trusted.
     *
     * @param context The freshness context to evaluate
     * @return True if data is valid, false otherwise
     */
    fun isValid(context: FreshnessContext): Boolean {
        return context.validity == ValidityStatus.VALID || 
               context.validity == ValidityStatus.VALID_STALE
    }
    
    /**
     * Checks if the data has expired based on TTL.
     *
     * @param context The freshness context to evaluate
     * @return True if data is expired, false otherwise
     */
    fun isExpired(context: FreshnessContext): Boolean {
        return context.freshness == DataFreshnessState.EXPIRED ||
               context.freshness == DataFreshnessState.INVALID
    }
    
    /**
     * Converts a freshness state to a human-readable string.
     *
     * @param state The freshness state to convert
     * @return Human-readable description of the freshness state
     */
    fun freshnessStateToString(state: DataFreshnessState): String {
        return when (state) {
            DataFreshnessState.FRESH -> "Fresh"
            DataFreshnessState.STALE_BUT_USABLE -> "Stale but usable"
            DataFreshnessState.EXPIRED -> "Expired"
            DataFreshnessState.INVALID -> "Invalid"
            DataFreshnessState.UNKNOWN -> "Unknown"
        }
    }
}
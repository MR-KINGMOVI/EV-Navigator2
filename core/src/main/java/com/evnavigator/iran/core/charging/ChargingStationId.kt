package com.evnavigator.iran.core.charging

import java.util.*

/**
 * Canonical charging station identifier that uniquely distinguishes charging stations.
 *
 * A charging station must be distinguishable by:
 * - Stable internal ID (UUID or hash-based)
 * - External source ID(s) when available
 * - Operator/network identifier  
 * - Market/region
 * - Canonical name (for display purposes only)
 *
 * This prevents confusion between duplicate records from different providers.
 */
data class ChargingStationId(
    val id: String, // Stable internal unique identifier
    val externalIds: Map<String, String> = emptyMap(), // External source IDs by provider
    val operatorId: String? = null, // Operator/network identifier
    val market: String = "IR", // Default to Iran market
    val canonicalName: String? = null // For display purposes only, not for identification
) : CharSequence by id {
    
    init {
        require(id.isNotBlank()) { "Internal ID must not be blank" }
        if (operatorId != null) {
            require(operatorId.isNotBlank()) { "Operator ID must not be blank if provided" }
        }
    }
    
    /**
     * Creates a stable identifier from the key components
     */
    val stableIdentifier: String by lazy {
        val parts = mutableListOf<String>()
        parts.add(id)
        operatorId?.let { parts.add("op:$it") }
        parts.add("mkt:$market")
        if (canonicalName != null) {
            parts.add("name:${canonicalName.lowercase()}")
        }
        parts.joinToString("_")
    }
    
    /**
     * Checks if this station ID is from the same source as another
     */
    fun isFromSameSource(other: ChargingStationId): Boolean {
        // Check if they share any external IDs from the same provider
        return externalIds.keys.intersect(other.externalIds.keys).isNotEmpty()
    }
    
    /**
     * Gets the external ID for a specific source/provider
     */
    fun getExternalId(source: String): String? {
        return externalIds[source]
    }
    
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is CharSequence) return id == other.toString()
        if (other is ChargingStationId) return id == other.id
        return false
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    companion object {
        /**
         * Creates a new charging station ID with auto-generated UUID
         */
        fun fromString(id: String): ChargingStationId {
            return ChargingStationId(id = id)
        }

        fun createNew(): ChargingStationId {
            val uuid = UUID.randomUUID().toString()
            return ChargingStationId(id = uuid)
        }
        
        /**
         * Creates a charging station ID from external identifiers
         */
        fun fromExternalIds(
            externalIds: Map<String, String>,
            operatorId: String? = null,
            market: String = "IR"
        ): ChargingStationId {
            // Create a stable hash-based ID from external IDs
            val id = externalIds.entries.joinToString("_") { "${it.key}:${it.value}" }.hashCode().toString()
            return ChargingStationId(
                id = id,
                externalIds = externalIds,
                operatorId = operatorId,
                market = market
            )
        }
    }
}
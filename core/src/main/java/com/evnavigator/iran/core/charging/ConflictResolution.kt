package com.evnavigator.iran.core.charging

/**
 * Represents the result of resolving conflicts between different data sources for the same charging station.
 */
data class ConflictResolutionResult(
    val resolutionStatus: ResolutionStatus,
    val resolvedValue: Any?,
    val conflictingSources: List<String>,
    val confidence: Double = 0.0,
    val resolutionNotes: String = ""
)

/**
 * Enum representing the status of conflict resolution.
 */
enum class ResolutionStatus {
    /**
     * The values are consistent across all sources and no conflict exists.
     */
    NO_CONFLICT,

    /**
     * The conflict has been resolved with a determined value.
     */
    RESOLVED,

    /**
     * The conflict cannot be resolved due to insufficient information or contradictory data.
     * The system should treat this as UNKNOWN rather than fabricating a value.
     */
    CONFLICT,

    /**
     * The value is unknown and cannot be determined from available sources.
     */
    UNKNOWN
}

/**
 * Utility class for resolving conflicts between different data sources for charging station records.
 *
 * This engine provides deterministic logic to handle cases where multiple sources provide
 * conflicting information about the same station, such as different power ratings or connector types.
 */
object ConflictResolutionEngine {
    
    /**
     * Resolves conflicts in a specific field across multiple data sources.
     *
     * @param fieldValues A map of source identifiers to their values for this field
     * @param fieldName The name of the field being resolved (for logging purposes)
     * @return Conflict resolution result with the resolved value or indication of conflict/unknown status
     */
    fun resolveFieldConflict(
        fieldValues: Map<String, Any?>,
        fieldName: String
    ): ConflictResolutionResult {
        // If no values provided, return unknown
        if (fieldValues.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = emptyList(),
                confidence = 0.0,
                resolutionNotes = "No source values provided for $fieldName"
            )
        }
        
        // If only one value, no conflict
        if (fieldValues.size == 1) {
            val singleValue = fieldValues.values.first()
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = singleValue,
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "Single source value for $fieldName"
            )
        }
        
        // Check if all values are the same
        val uniqueValues = fieldValues.values.toSet()
        if (uniqueValues.size == 1) {
            val singleValue = uniqueValues.first()
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = singleValue,
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "All sources agree on $fieldName"
            )
        }
        
        // Check if all values are null/unknown
        val nullValues = fieldValues.values.filter { it == null || it.toString().isBlank() }
        if (nullValues.size == fieldValues.size) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = fieldValues.keys.toList(),
                confidence = 0.0,
                resolutionNotes = "All sources provide null/empty values for $fieldName"
            )
        }
        
        // Handle mixed null and non-null values
        val nonNullValues = fieldValues.values.filter { it != null && !it.toString().isBlank() }
        if (nonNullValues.isNotEmpty() && nonNullValues.size < fieldValues.size) {
            // Some sources provide data, others don't - this might be acceptable
            // But we still want to see if there's a clear resolution strategy
            
            // For now, let's check if all non-null values are the same
            val uniqueNonNullValues = nonNullValues.toSet()
            if (uniqueNonNullValues.size == 1) {
                return ConflictResolutionResult(
                    resolutionStatus = ResolutionStatus.RESOLVED,
                    resolvedValue = uniqueNonNullValues.first(),
                    conflictingSources = fieldValues.keys.toList(),
                    confidence = 0.8, // High confidence since all non-null values agree
                    resolutionNotes = "Mixed null/non-null values, but all non-null values agree for $fieldName"
                )
            }
        }
        
        // At this point, we have conflicting values that can't be resolved automatically
        return ConflictResolutionResult(
            resolutionStatus = ResolutionStatus.CONFLICT,
            resolvedValue = null,
            conflictingSources = fieldValues.keys.toList(),
            confidence = 0.0,
            resolutionNotes = "Conflicting values for $fieldName: ${fieldValues.values.joinToString(", ")}"
        )
    }
    
    /**
     * Resolves conflicts in power capability across multiple sources.
     *
     * @param sourcePowers A map of source identifiers to their power values
     * @return Conflict resolution result with resolved power value or indication of conflict
     */
    fun resolvePowerConflict(
        sourcePowers: Map<String, Double?>
    ): ConflictResolutionResult {
        // If no powers provided, return unknown
        if (sourcePowers.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = emptyList(),
                confidence = 0.0,
                resolutionNotes = "No source power values provided"
            )
        }
        
        // Filter out null values
        val validPowers = sourcePowers.mapNotNull { (source, power) ->
            if (power != null && power > 0.0) source to power else null
        }.toMap()
        
        if (validPowers.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = sourcePowers.keys.toList(),
                confidence = 0.0,
                resolutionNotes = "All power values are null or invalid for conflict resolution"
            )
        }
        
        // If only one valid power value, no conflict
        if (validPowers.size == 1) {
            val singlePower = validPowers.values.first()
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = singlePower,
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "Single valid power value"
            )
        }
        
        // Check if all valid values are the same (within reasonable tolerance)
        val uniquePowers = validPowers.values.toSet()
        if (uniquePowers.size == 1) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = uniquePowers.first(),
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "All valid power values are identical"
            )
        }
        
        // Check if all values are within a reasonable tolerance of each other
        val sortedPowers = uniquePowers.sorted()
        val minPower = sortedPowers.first()
        val maxPower = sortedPowers.last()
        
        // If maximum difference is within 10% of the maximum value, consider it resolved
        val difference = maxPower - minPower
        val relativeDifference = if (maxPower > 0) difference / maxPower else 0.0
        
        if (relativeDifference <= 0.1) { // 10% tolerance
            // Return the average as a compromise
            val averagePower = uniquePowers.average()
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.RESOLVED,
                resolvedValue = averagePower,
                conflictingSources = sourcePowers.keys.toList(),
                confidence = 0.8, // High confidence in this resolution approach
                resolutionNotes = "All power values within 10% tolerance of each other"
            )
        }
        
        // Conflicting power values that cannot be resolved automatically
        return ConflictResolutionResult(
            resolutionStatus = ResolutionStatus.CONFLICT,
            resolvedValue = null,
            conflictingSources = sourcePowers.keys.toList(),
            confidence = 0.0,
            resolutionNotes = "Conflicting power values: ${validPowers.values.joinToString(", ")}"
        )
    }
    
    /**
     * Resolves conflicts in connector types across multiple sources.
     *
     * @param sourceConnectors A map of source identifiers to their connector type lists
     * @return Conflict resolution result with resolved connector information or indication of conflict
     */
    fun resolveConnectorConflict(
        sourceConnectors: Map<String, List<ConnectorType>>
    ): ConflictResolutionResult {
        // If no connectors provided, return unknown
        if (sourceConnectors.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = emptyList(),
                confidence = 0.0,
                resolutionNotes = "No source connector information provided"
            )
        }
        
        // Filter out empty lists
        val validConnectors = sourceConnectors.filter { it.value.isNotEmpty() }
        
        if (validConnectors.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = sourceConnectors.keys.toList(),
                confidence = 0.0,
                resolutionNotes = "All connector lists are empty"
            )
        }
        
        // If only one valid list, no conflict
        if (validConnectors.size == 1) {
            val singleList = validConnectors.values.first()
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = singleList,
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "Single connector type list"
            )
        }
        
        // Check if all lists are identical
        val uniqueLists = validConnectors.values.map { it.toSet() }.toSet()
        if (uniqueLists.size == 1) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = uniqueLists.first().toList(),
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "All connector lists are identical"
            )
        }
        
        // Check for common connectors between all sources
        val allConnectors = validConnectors.values.flatten()
        val connectorCounts = allConnectors.groupingBy { it }.eachCount()
        
        // Find the most common connectors (those appearing in more than one source)
        val commonConnectors = connectorCounts.filter { it.value > 1 }.keys.toList()
        
        if (commonConnectors.isNotEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.RESOLVED,
                resolvedValue = commonConnectors,
                conflictingSources = sourceConnectors.keys.toList(),
                confidence = 0.7, // Medium-high confidence for finding common ground
                resolutionNotes = "Found common connectors: ${commonConnectors.joinToString(", ")}"
            )
        }
        
        // No clear resolution path - conflicting connector information
        return ConflictResolutionResult(
            resolutionStatus = ResolutionStatus.CONFLICT,
            resolvedValue = null,
            conflictingSources = sourceConnectors.keys.toList(),
            confidence = 0.0,
            resolutionNotes = "No common connectors found across sources"
        )
    }
    
    /**
     * Resolves conflicts in metadata fields that might have multiple valid representations.
     *
     * @param sourceMetadata A map of source identifiers to their metadata values
     * @return Conflict resolution result with resolved metadata or indication of conflict
     */
    fun resolveMetadataConflict(
        sourceMetadata: Map<String, String?>
    ): ConflictResolutionResult {
        // If no metadata provided, return unknown
        if (sourceMetadata.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = emptyList(),
                confidence = 0.0,
                resolutionNotes = "No source metadata values provided"
            )
        }
        
        // Filter out null/empty values
        val validMetadata = sourceMetadata.mapNotNull { (k, v) ->
            if (!v.isNullOrBlank()) k to v else null
        }.toMap()
        
        if (validMetadata.isEmpty()) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.UNKNOWN,
                resolvedValue = null,
                conflictingSources = sourceMetadata.keys.toList(),
                confidence = 0.0,
                resolutionNotes = "All metadata values are null or empty"
            )
        }
        
        // If only one valid value, no conflict
        if (validMetadata.size == 1) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = validMetadata.values.first(),
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "Single metadata value"
            )
        }
        
        // Check if all valid values are identical
        val uniqueValues = validMetadata.values.toSet()
        if (uniqueValues.size == 1) {
            return ConflictResolutionResult(
                resolutionStatus = ResolutionStatus.NO_CONFLICT,
                resolvedValue = uniqueValues.first(),
                conflictingSources = emptyList(),
                confidence = 1.0,
                resolutionNotes = "All metadata values are identical"
            )
        }
        
        // For metadata, we'll prefer the most recent (assuming timestamps in keys or sorted order)
        // For now, return a conflict since we can't automatically resolve metadata differences
        return ConflictResolutionResult(
            resolutionStatus = ResolutionStatus.CONFLICT,
            resolvedValue = null,
            conflictingSources = sourceMetadata.keys.toList(),
            confidence = 0.0,
            resolutionNotes = "Conflicting metadata values: ${validMetadata.values.joinToString(", ")}"
        )
    }
}
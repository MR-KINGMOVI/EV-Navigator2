package com.evnavigator.iran.core.charging

import com.evnavigator.iran.core.vehicle.ConnectorType

/**
 * Represents the result of deduplicating charging station records.
 */
data class DeduplicationResult(
    val isDuplicate: Boolean,
    val duplicateStationId: String? = null,
    val confidence: Double = 0.0,
    val matchReasons: List<String> = emptyList()
)

/**
 * Utility class for detecting and handling duplicate charging station records.
 *
 * This engine provides deterministic logic to identify potentially duplicate stations
 * based on various signals like source IDs, coordinates, names, etc.
 */
object DeduplicationEngine {
    
    /**
     * Determines if a normalized charging station record is a duplicate of existing records.
     *
     * @param newRecord The new record to check for duplicates
     * @param existingRecords The list of existing records to compare against
     * @return Deduplication result indicating if it's a duplicate and how confident we are
     */
    fun detectDuplicate(
        newRecord: NormalizedChargingStationRecord,
        existingRecords: List<NormalizedChargingStationRecord>
    ): DeduplicationResult {
        // Check for exact matches based on external ID
        val externalIdMatch = existingRecords.find { it.externalId == newRecord.externalId }
        if (externalIdMatch != null) {
            return DeduplicationResult(
                isDuplicate = true,
                duplicateStationId = externalIdMatch.id,
                confidence = 1.0,
                matchReasons = listOf("Exact external ID match")
            )
        }
        
        // Check for exact coordinate matches
        val coordinateMatch = existingRecords.find { 
            it.location.coordinate.latitude == newRecord.location.coordinate.latitude && 
            it.location.coordinate.longitude == newRecord.location.coordinate.longitude
        }
        if (coordinateMatch != null) {
            return DeduplicationResult(
                isDuplicate = true,
                duplicateStationId = coordinateMatch.id,
                confidence = 0.9, // High confidence for exact coordinates
                matchReasons = listOf("Exact coordinate match")
            )
        }
        
        // Check for geographic proximity (within 100 meters)
        val proximityMatch = findNearbyStation(newRecord, existingRecords, 0.1) // 100 meters
        if (proximityMatch != null) {
            return DeduplicationResult(
                isDuplicate = true,
                duplicateStationId = proximityMatch.id,
                confidence = 0.7, // Medium-high confidence for proximity
                matchReasons = listOf("Geographic proximity match")
            )
        }
        
        // Check for matching connector configurations
        val connectorMatch = findMatchingConnectorStation(newRecord, existingRecords)
        if (connectorMatch != null) {
            return DeduplicationResult(
                isDuplicate = true,
                duplicateStationId = connectorMatch.id,
                confidence = 0.6, // Medium confidence for connector match
                matchReasons = listOf("Matching connector configuration")
            )
        }
        
        // Check for matching names (normalized)
        val nameMatch = findByName(newRecord, existingRecords)
        if (nameMatch != null) {
            return DeduplicationResult(
                isDuplicate = true,
                duplicateStationId = nameMatch.id,
                confidence = 0.5, // Medium confidence for name match
                matchReasons = listOf("Similar name match")
            )
        }
        
        // No clear duplicates found
        return DeduplicationResult(
            isDuplicate = false,
            confidence = 0.0,
            matchReasons = emptyList()
        )
    }
    
    /**
     * Finds a nearby station within a given radius.
     *
     * @param newRecord The new record to check
     * @param existingRecords The list of existing records to compare against
     * @param radiusKm The maximum distance in kilometers
     * @return Matching station or null if none found
     */
    private fun findNearbyStation(
        newRecord: NormalizedChargingStationRecord,
        existingRecords: List<NormalizedChargingStationRecord>,
        radiusKm: Double
    ): NormalizedChargingStationRecord? {
        // For simplicity, we'll use a basic distance calculation here
        // In production, this would use a more robust geospatial library
        
        val newLat = newRecord.location.coordinate.latitude
        val newLng = newRecord.location.coordinate.longitude
        
        for (existing in existingRecords) {
            val existingLat = existing.location.coordinate.latitude
            val existingLng = existing.location.coordinate.longitude
            
            // Simple distance approximation (not accurate but sufficient for this implementation)
            val latDiff = Math.abs(newLat - existingLat)
            val lngDiff = Math.abs(newLng - existingLng)
            
            // Convert to kilometers (approximate)
            val distanceKm = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111.0
            
            if (distanceKm <= radiusKm) {
                return existing
            }
        }
        
        return null
    }
    
    /**
     * Finds a station with matching connector configuration.
     *
     * @param newRecord The new record to check
     * @param existingRecords The list of existing records to compare against
     * @return Matching station or null if none found
     */
    private fun findMatchingConnectorStation(
        newRecord: NormalizedChargingStationRecord,
        existingRecords: List<NormalizedChargingStationRecord>
    ): NormalizedChargingStationRecord? {
        // Check if any existing station has similar connector types and power capabilities
        
        val newPort = newRecord.ports.firstOrNull() ?: return null
        val newConnectorType = newPort.connectorType
        val newAcPower = if (newPort.isAc) newPort.power else null
        val newDcPower = if (newPort.isDc) newPort.power else null
        
        for (existing in existingRecords) {
            for (existingPort in existing.ports) {
                val existingConnectorType = existingPort.connectorType
                val existingAcPower = if (existingPort.isAc) existingPort.power else null
                val existingDcPower = if (existingPort.isDc) existingPort.power else null
                
                val hasConnectorOverlap = newConnectorType == existingConnectorType
                if (hasConnectorOverlap) {
                    val acPowerSimilar = arePowersSimilar(newAcPower, existingAcPower)
                    val dcPowerSimilar = arePowersSimilar(newDcPower, existingDcPower)
                    
                    if (acPowerSimilar || dcPowerSimilar) {
                        return existing
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Checks if two power values are similar within a threshold.
     *
     * @param power1 First power value (nullable)
     * @param power2 Second power value (nullable)
     * @return True if powers are similar, false otherwise
     */
    private fun arePowersSimilar(power1: Double?, power2: Double?): Boolean {
        // If either is null, we can't compare
        if (power1 == null || power2 == null) return false
        
        // If both are zero, they're similar
        if (power1 == 0.0 && power2 == 0.0) return true
        
        // Calculate relative difference
        val max = maxOf(power1, power2)
        if (max == 0.0) return true  // Both zero
        
        val diff = Math.abs(power1 - power2)
        val relativeDiff = diff / max
        
        // Consider similar if within 20%
        return relativeDiff <= 0.2
    }
    
    /**
     * Finds a station with a similar name (normalized).
     *
     * @param newRecord The new record to check
     * @param existingRecords The list of existing records to compare against
     * @return Matching station or null if none found
     */
    private fun findByName(
        newRecord: NormalizedChargingStationRecord,
        existingRecords: List<NormalizedChargingStationRecord>
    ): NormalizedChargingStationRecord? {
        val newName = normalizeString(newRecord.metadata.canonicalName)
        
        for (existing in existingRecords) {
            val existingName = normalizeString(existing.metadata.canonicalName)
            
            // Simple similarity check - strings must be at least 80% similar
            if (calculateSimilarity(newName, existingName) >= 0.8) {
                return existing
            }
        }
        
        return null
    }
    
    /**
     * Normalizes a string for comparison by removing extra spaces and converting to lowercase.
     *
     * @param input The input string to normalize
     * @return The normalized string
     */
    private fun normalizeString(input: String): String {
        return input.trim().lowercase().replace(Regex("\\s+"), " ")
    }
    
    /**
     * Calculates similarity between two strings using a basic approach.
     *
     * @param str1 First string
     * @param str2 Second string
     * @return Similarity score between 0.0 and 1.0
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        if (str1.isEmpty() && str2.isEmpty()) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        if (longer.isEmpty()) return 1.0
        
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance).toDouble() / longer.length.toDouble()
    }
    
    /**
     * Calculates the Levenshtein distance between two strings.
     *
     * @param str1 First string
     * @param str2 Second string
     * @return The edit distance between the strings
     */
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val matrix = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) {
            matrix[i][0] = i
        }
        
        for (j in 0..str2.length) {
            matrix[0][j] = j
        }
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1,      // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return matrix[str1.length][str2.length]
    }
    
    /**
     * Merges two charging station records when a duplicate is detected.
     *
     * @param existingRecord The existing record
     * @param newRecord The new record to merge with
     * @return A merged record with combined information
     */
    fun mergeRecords(
        existingRecord: NormalizedChargingStationRecord,
        newRecord: NormalizedChargingStationRecord
    ): NormalizedChargingStationRecord {
        // For now, we'll take the newer record but preserve some key information from the existing
        
        // Preserve the station ID and external ID from the existing record
        val mergedId = existingRecord.id
        val mergedExternalId = existingRecord.externalId
        
        // Merge location data (prefer existing if same)
        val mergedLocation = existingRecord.location
        
        // Merge metadata - prefer newer where available, but merge information
        val mergedMetadata = mergeMetadata(existingRecord.metadata, newRecord.metadata)
        
        // Merge ports - combine both sets of ports
        val mergedPorts = mergePorts(existingRecord.ports, newRecord.ports)
        
        // Merge provenance with updated timestamps and confidence
        val mergedProvenance = mergeProvenance(existingRecord.provenance, newRecord.provenance)
        
        return NormalizedChargingStationRecord(
            id = mergedId,
            externalId = mergedExternalId,
            location = mergedLocation,
            metadata = mergedMetadata,
            ports = mergedPorts,
            provenance = mergedProvenance
        )
    }
    
    /**
     * Merges station metadata.
     */
    private fun mergeMetadata(
        existing: StationMetadata,
        new: StationMetadata
    ): StationMetadata {
        return existing.copy(
            canonicalName = if (new.canonicalName.isNotBlank()) new.canonicalName else existing.canonicalName,
            operatorName = new.operatorName ?: existing.operatorName,
            networkName = new.networkName ?: existing.networkName
        )
    }
    
    /**
     * Merges charging ports.
     */
    private fun mergePorts(
        existingPorts: List<ChargingPort>,
        newPorts: List<ChargingPort>
    ): List<ChargingPort> {
        return existingPorts + newPorts
    }
    
    /**
     * Merges provenance information.
     */
    private fun mergeProvenance(
        existing: Provenance,
        new: Provenance
    ): Provenance {
        return existing.copy(
            version = if (!new.version.isNullOrBlank()) new.version else existing.version,
            confidence = maxOf(existing.confidence, new.confidence),
            retrievedDate = maxOf(existing.retrievedDate ?: 0L, new.retrievedDate ?: 0L)
        )
    }
}
package com.evnavigator.iran.core.weather.validation

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.weather.model.*
import java.time.Instant

/**
 * Validator for traffic data to ensure it meets quality standards and physical constraints.
 */
class TrafficValidator {
    
    /**
     * Validate a traffic observation for correctness and plausibility.
     *
     * @param observation The traffic observation to validate
     * @return ValidationResult with any errors or warnings
     */
    fun validateTrafficObservation(observation: TrafficObservation): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate coordinate
        if (observation.coordinate == null) {
            errors.add("Coordinate is required")
        } else {
            if (!isValidCoordinate(observation.coordinate)) {
                errors.add("Invalid coordinate values")
            }
        }
        
        // Validate timestamp
        if (observation.timestamp == null) {
            errors.add("Timestamp is required")
        } else {
            if (!isValidTimestamp(observation.timestamp)) {
                errors.add("Invalid timestamp")
            }
        }
        
        // Validate retrievedAt timestamp
        if (observation.retrievedAt == null) {
            errors.add("RetrievedAt timestamp is required")
        } else {
            if (!isValidTimestamp(observation.retrievedAt)) {
                errors.add("Invalid retrievedAt timestamp")
            }
        }
        
        // Validate speed (non-negative, reasonable values)
        observation.speedMps?.let { speed ->
            if (speed < 0.0) {
                errors.add("Speed cannot be negative: $speed m/s")
            } else if (speed > 100.0) {
                warnings.add("Speed seems unusually high: $speed m/s")
            }
            Unit
        }
        
        // Validate free-flow speed (positive values)
        observation.freeFlowSpeedMps?.let { freeFlow ->
            if (freeFlow <= 0.0) {
                errors.add("Free-flow speed must be positive: $freeFlow m/s")
            } else if (freeFlow > 100.0) {
                warnings.add("Free-flow speed seems unusually high: $freeFlow m/s")
            }
            Unit
        }
        
        // Validate congestion ratio bounds
        observation.congestionRatio?.let { ratio ->
            if (ratio < 0.0 || ratio > 2.0) { // Allow some flexibility for very heavy traffic
                errors.add("Congestion ratio must be between 0 and 2: $ratio")
            }
        }
        
        // Validate travel-time multiplier bounds
        observation.travelTimeMultiplier?.let { multiplier ->
            if (multiplier <= 0.0) {
                errors.add("Travel-time multiplier must be positive: $multiplier")
            } else if (multiplier > 10.0) {
                warnings.add("Travel-time multiplier seems unusually high: $multiplier")
            }
            Unit
        }
        
        // Validate traffic class
        if (observation.trafficClass == null) {
            errors.add("Traffic class is required")
        }
        
        // Validate freshness
        if (observation.freshness == null) {
            warnings.add("Freshness is unknown")
        }
        
        // Validate confidence bounds
        if (observation.confidence < 0.0 || observation.confidence > 1.0) {
            errors.add("Confidence must be between 0 and 1: ${observation.confidence}")
        }
        
        // Validate uncertainty bounds
        if (observation.uncertainty < 0.0 || observation.uncertainty > 1.0) {
            errors.add("Uncertainty must be between 0 and 1: ${observation.uncertainty}")
        }
        
        return ValidationResult(
            hasErrors = errors.isNotEmpty(),
            hasWarnings = warnings.isNotEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a traffic incident for correctness and plausibility.
     *
     * @param incident The traffic incident to validate
     * @return ValidationResult with any errors or warnings
     */
    fun validateTrafficIncident(incident: TrafficIncident): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate ID
        if (incident.id.isNullOrBlank()) {
            errors.add("Incident ID is required")
        }
        
        // Validate coordinate
        if (incident.coordinate == null) {
            errors.add("Coordinate is required")
        } else {
            if (!isValidCoordinate(incident.coordinate)) {
                errors.add("Invalid coordinate values")
            }
        }
        
        // Validate type
        if (incident.type == null) {
            errors.add("Incident type is required")
        }
        
        // Validate start time
        if (incident.startTime == null) {
            errors.add("Start time is required")
        } else {
            if (!isValidTimestamp(incident.startTime)) {
                errors.add("Invalid start time")
            }
        }
        
        // Validate expected end time if provided
        incident.expectedEndTime?.let { endTime ->
            if (!isValidTimestamp(endTime)) {
                errors.add("Invalid expected end time")
            }
            if (endTime.isBefore(incident.startTime)) {
                errors.add("Expected end time cannot be before start time")
            }
        }
        
        // Validate actual end time if provided
        incident.actualEndTime?.let { endTime ->
            if (!isValidTimestamp(endTime)) {
                errors.add("Invalid actual end time")
            }
            if (endTime.isBefore(incident.startTime)) {
                errors.add("Actual end time cannot be before start time")
            }
        }
        
        // Validate severity bounds
        incident.severity?.let { severity ->
            if (severity < 1 || severity > 5) {
                errors.add("Severity must be between 1 and 5: $severity")
            }
        }
        
        // Validate status
        if (incident.status == null) {
            errors.add("Incident status is required")
        }
        
        // Validate source
        if (incident.source.isNullOrBlank()) {
            errors.add("Source is required")
        }
        
        // Validate retrievedAt timestamp
        if (incident.retrievedAt == null) {
            errors.add("RetrievedAt timestamp is required")
        } else {
            if (!isValidTimestamp(incident.retrievedAt)) {
                errors.add("Invalid retrievedAt timestamp")
            }
        }
        
        // Validate confidence bounds
        if (incident.confidence < 0.0 || incident.confidence > 1.0) {
            errors.add("Confidence must be between 0 and 1: ${incident.confidence}")
        }
        
        // Validate uncertainty bounds
        if (incident.uncertainty < 0.0 || incident.uncertainty > 1.0) {
            errors.add("Uncertainty must be between 0 and 1: ${incident.uncertainty}")
        }
        
        // Validate freshness
        if (incident.freshness == null) {
            warnings.add("Freshness is unknown")
        }
        
        return ValidationResult(
            hasErrors = errors.isNotEmpty(),
            hasWarnings = warnings.isNotEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate a coordinate for geographic validity.
     */
    private fun isValidCoordinate(coordinate: GeoCoordinate): Boolean {
        return coordinate.latitude >= -90.0 && coordinate.latitude <= 90.0 &&
               coordinate.longitude >= -180.0 && coordinate.longitude <= 180.0
    }
    
    /**
     * Validate a timestamp for logical validity.
     */
    private fun isValidTimestamp(timestamp: Instant): Boolean {
        // Allow timestamps within reasonable range (approx. 100 years from Unix epoch)
        return timestamp.epochSecond >= -3153600000L && timestamp.epochSecond <= 3153600000L
    }
}

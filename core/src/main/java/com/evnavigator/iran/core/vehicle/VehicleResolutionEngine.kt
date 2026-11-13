package com.evnavigator.iran.core.vehicle

/**
 * Vehicle specification resolution engine.
 *
 * Handles conflicts and prioritization when multiple vehicle specifications exist for the same vehicle.
 */
object VehicleResolutionEngine {
private val DataQuality.qualityRank: Int
    get() = when (this) {
        DataQuality.HIGH -> 3
        DataQuality.MEDIUM -> 2
        DataQuality.LOW -> 1
        DataQuality.UNKNOWN -> 0
    }

    
    /**
     * Resolves conflicting vehicle specifications for the same vehicle identity.
     *
     * This is a deterministic process that determines:
     * - Canonical record
     * - Preferred specification  
     * - Conflicting specifications
     * - Confidence levels
     * - Provenance information
     *
     * @param vehicles List of potentially conflicting vehicle specifications
     * @return Resolved vehicle specification or null if resolution is impossible
     */
    fun resolveVehicleSpecifications(vehicles: List<Vehicle>): Vehicle? {
        if (vehicles.isEmpty()) return null
        
        // If only one specification, return it directly
        if (vehicles.size == 1) return vehicles[0]
        
        // Group by vehicle identity to handle variants properly
        val groupedByIdentity = vehicles.groupBy { it.id }
        
        // For each group, determine the best resolution
        val resolvedVehicles = mutableListOf<Vehicle>()
        
        for ((identity, groupVehicles) in groupedByIdentity) {
            val resolved = resolveGroup(groupVehicles)
            resolved?.let { resolvedVehicles.add(it) }
        }
        
        // If we have multiple identities, return the first one (or handle as needed)
        return resolvedVehicles.maxWithOrNull(compareBy<Vehicle> { it.dataQuality.qualityRank }.thenBy { it.confidence })
    }
    
    /**
     * Resolves a group of vehicles with the same identity
     */
    fun resolveGroup(vehicles: List<Vehicle>): Vehicle? {
        if (vehicles.isEmpty()) return null
        if (vehicles.size == 1) return vehicles[0]
        
        // Sort by data quality first (highest first)
        val sortedByQuality = vehicles.sortedByDescending { it.dataQuality.qualityRank }
        
        // Find the highest quality vehicle that passes validation
        for (vehicle in sortedByQuality) {
            val validationResult = ValidationEngine.validateVehicle(vehicle)
            if (validationResult.status != ValidationStatus.INVALID) {
                return vehicle
            }
        }
        
        // If no valid vehicle found, return the highest quality one anyway
        return sortedByQuality.firstOrNull()
    }
    
    /**
     * Determines preferred specification based on multiple criteria
     */
    fun determinePreferredSpecification(vehicles: List<Vehicle>): Vehicle? {
        if (vehicles.isEmpty()) return null
        
        // Criteria for preference (in order of importance):
        // 1. Highest data quality
        // 2. Highest confidence level  
        // 3. Most recent source
        // 4. Most complete specification
        
        val sortedVehicles = vehicles.sortedWith(
            compareByDescending<Vehicle> { it.dataQuality.qualityRank }
                .thenByDescending { it.confidence }
                .thenByDescending { it.retrievedDate }
                .thenByDescending { getCompletenessScore(it) }
        )
        
        return sortedVehicles.firstOrNull()
    }
    
    /**
     * Calculates a completeness score for a vehicle specification
     */
    private fun getCompletenessScore(vehicle: Vehicle): Double {
        val totalFields = 20.0 // Approximate number of important fields
        var filledFields = 0.0
        
        // Count filled fields
        if (vehicle.name.isNotBlank()) filledFields++
        if (vehicle.curbMassKg.kilograms > 0) filledFields++
        if (vehicle.nominalBatteryCapacityWh.wattHours > 0) filledFields++
        if (vehicle.usableBatteryCapacityWh.wattHours > 0) filledFields++
        if (vehicle.motorCount >= 0) filledFields++
        if (vehicle.connectorTypes.isNotEmpty()) filledFields++
        
        // Add more specific fields
        vehicle.grossVehicleMassKg?.let { if (it.kilograms > 0) filledFields++ }
        vehicle.lengthMm?.let { if (it > 0) filledFields++ }
        vehicle.widthMm?.let { if (it > 0) filledFields++ }
        vehicle.heightMm?.let { if (it > 0) filledFields++ }
        vehicle.wheelbaseMm?.let { if (it > 0) filledFields++ }
        vehicle.dragCoefficient?.let { if (it >= 0) filledFields++ }
        vehicle.frontalAreaM2?.let { if (it >= 0) filledFields++ }
        vehicle.rollingResistanceCoefficient?.let { if (it >= 0) filledFields++ }
        vehicle.acChargingMaxPowerKw?.let { if (it.watts >= 0) filledFields++ }
        vehicle.dcChargingMaxPowerKw?.let { if (it.watts >= 0) filledFields++ }
        
        return filledFields / totalFields
    }
    
    /**
     * Checks if two vehicles are compatible (same basic identity but different variants)
     */
    fun areCompatible(vehicle1: Vehicle, vehicle2: Vehicle): Boolean {
        return vehicle1.id.manufacturer == vehicle2.id.manufacturer &&
               vehicle1.id.model == vehicle2.id.model &&
               vehicle1.id.generation == vehicle2.id.generation &&
               vehicle1.id.modelYear == vehicle2.id.modelYear
    }
    
    /**
     * Determines if a vehicle has sufficient data for energy calculations
     */
    fun isEnergyReady(vehicle: Vehicle): Boolean {
        // For Phase 5 energy calculations, we need:
        return vehicle.curbMassKg.kilograms > 0 &&
               vehicle.usableBatteryCapacityWh.wattHours > 0 &&
               vehicle.motorCount >= 0 &&
               vehicle.connectorTypes.isNotEmpty()
    }
    
    /**
     * Merges multiple vehicle specifications into one (with conflict resolution)
     */
    fun mergeVehicleSpecifications(vehicles: List<Vehicle>): Vehicle? {
        if (vehicles.isEmpty()) return null
        if (vehicles.size == 1) return vehicles[0]
        
        // For now, just return the first valid one with best confidence
        val sortedByConfidence = vehicles.sortedByDescending { it.confidence }
        
        for (vehicle in sortedByConfidence) {
            val validationResult = ValidationEngine.validateVehicle(vehicle)
            if (validationResult.status != ValidationStatus.INVALID) {
                return vehicle
            }
        }
        
        // Return the highest confidence one if validation fails
        return sortedByConfidence.firstOrNull()
    }
    
    /**
     * Resolves a specific vehicle identity to a canonical specification
     * 
     * This handles cases where there might be multiple records for the same vehicle
     * but with slight differences (different markets, years, etc.)
     */
    fun resolveCanonicalVehicle(vehicles: List<Vehicle>): Vehicle? {
        if (vehicles.isEmpty()) return null
        
        // First, group by identity components to understand what we're working with
        val groupedByBaseIdentity = vehicles.groupBy { 
            "${it.id.manufacturer}_${it.id.model}_${it.id.generation}_${it.id.modelYear}" 
        }
        
        // If we have only one base identity, just resolve that group
        if (groupedByBaseIdentity.size == 1) {
            return resolveGroup(vehicles)
        }
        
        // For multiple identities, try to select the most appropriate one
        // Prefer the highest quality and confidence version
        val sortedVehicles = vehicles.sortedWith(
            compareByDescending<Vehicle> { it.dataQuality.qualityRank }
                .thenByDescending { it.confidence }
                .thenByDescending { it.retrievedDate }
        )
        
        return sortedVehicles.firstOrNull()
    }
}
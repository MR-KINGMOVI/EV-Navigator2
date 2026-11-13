package com.evnavigator.iran.core.vehicle

/**
 * Rolling resistance specification for electric vehicles.
 */
data class RollingResistanceSpecification(
    // Core rolling resistance parameter
    val rollingResistanceCoefficient: Double?, // Crr - dimensionless
    
    // Tire and wheel specifications (if known)
    val tireType: String?,        // Type of tire (e.g., "Summer", "Winter", "All-Season")
    val tireWidthMm: Int?,        // Tire width in millimeters
    val tireProfile: Int?,        // Aspect ratio (e.g., 50 for 50% profile)
    val wheelDiameterInch: Double?, // Wheel diameter in inches
    
    // Road surface conditions (if known)
    val typicalRoadSurface: RoadSurfaceType?, // Type of road surface
    val tirePressureBar: Double?,   // Tire pressure in bar
    
    // Additional rolling resistance factors (if known)
    val tireMaterial: String?,     // Tire material composition
    val loadFactor: Double?        // Load factor affecting rolling resistance
) {
    init {
        rollingResistanceCoefficient?.let { require(it >= 0.0) { "Rolling resistance coefficient cannot be negative" } }
        tireWidthMm?.let { require(it > 0) { "Tire width must be positive" } }
        tireProfile?.let { require(it > 0) { "Tire profile must be positive" } }
        wheelDiameterInch?.let { require(it > 0) { "Wheel diameter must be positive" } }
        tirePressureBar?.let { require(it >= 0.0) { "Tire pressure cannot be negative" } }
        loadFactor?.let { require(it >= 0.0) { "Load factor cannot be negative" } }
    }
    
    /**
     * Determines if basic rolling resistance information is available
     */
    val hasBasicRollingResistance: Boolean
        get() = rollingResistanceCoefficient != null
    
    /**
     * Gets the typical road surface information
     */
    val roadSurfaceInfo: String
        get() = when {
            typicalRoadSurface != null -> "Road surface: ${typicalRoadSurface.name}"
            else -> "No road surface specified"
        }
}

/**
 * Represents different types of road surfaces that affect rolling resistance
 */
enum class RoadSurfaceType {
    ASPHALT,      // Standard asphalt pavement  
    CONCRETE,     // Concrete pavement
    GRAVEL,       // Gravel road
    DIRT,         // Dirt or unpaved road
    ICE,          // Icy road surface
    SNOW,         // Snow-covered road
    MIXED         // Mixed surface conditions
}